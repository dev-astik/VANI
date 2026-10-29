package com.example.vani.ui.commands

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
//import com.example.vani.ui.camera.CameraCaptureActivity

enum class MediaAction(val keyCode: Int, val message: String) {
    PLAY(KeyEvent.KEYCODE_MEDIA_PLAY, "Okay, playing."),
    PAUSE(KeyEvent.KEYCODE_MEDIA_PAUSE, "Okay, pausing."),
    NEXT(KeyEvent.KEYCODE_MEDIA_NEXT, "Okay, playing the next one."),
    PREVIOUS(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Okay, going back to the previous one."),
}

/** Opening apps, searching, music/video controls, camera, gallery, calculator. */
object AppActions {

    // ---------- Known apps and searchable apps ----------

    private class KnownApp(val label: String, val packages: List<String>)

    private val KNOWN_APPS: Map<String, KnownApp> = mapOf(
        "youtube" to KnownApp("YouTube", listOf("com.google.android.youtube")),
        "yt" to KnownApp("YouTube", listOf("com.google.android.youtube")),
        "whatsapp" to KnownApp("WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b")),
        "instagram" to KnownApp("Instagram", listOf("com.instagram.android")),
        "insta" to KnownApp("Instagram", listOf("com.instagram.android")),
        "chrome" to KnownApp("Chrome", listOf("com.android.chrome")),
        "gmail" to KnownApp("Gmail", listOf("com.google.android.gm")),
        "maps" to KnownApp("Maps", listOf("com.google.android.apps.maps")),
        "googlemaps" to KnownApp("Maps", listOf("com.google.android.apps.maps")),
        "facebook" to KnownApp("Facebook", listOf("com.facebook.katana")),
        "telegram" to KnownApp("Telegram", listOf("org.telegram.messenger")),
        "spotify" to KnownApp("Spotify", listOf("com.spotify.music")),
        "playstore" to KnownApp("Play Store", listOf("com.android.vending")),
    )

    private class SearchApp(val label: String, val url: (String) -> String, val inChrome: Boolean = false)

    private val SEARCH_APPS: Map<String, SearchApp> = run {
        val youtube = SearchApp("YouTube", { "https://www.youtube.com/results?search_query=$it" })
        val google = SearchApp("Google", { "https://www.google.com/search?q=$it" }, inChrome = true)
        val maps = SearchApp("Google Maps", { "https://www.google.com/maps/search/?api=1&query=$it" })
        mapOf(
            "youtube" to youtube, "yt" to youtube,
            "chrome" to google, "google" to google, "browser" to google,
            "spotify" to SearchApp("Spotify", { "https://open.spotify.com/search/$it" }),
            "maps" to maps, "googlemaps" to maps,
        )
    }

    fun canSearchIn(spoken: String) = SEARCH_APPS.containsKey(appKey(spoken))

    private fun appKey(spoken: String) =
        spoken.lowercase().replace(Regex("\\b(?:the|app|application)\\b"), " ").filter { it.isLetterOrDigit() }

    // ---------- Search ----------

    fun search(context: Context, spokenApp: String, query: String): CommandResult {
        val app = SEARCH_APPS[appKey(spokenApp)]
            ?: return fail("Sorry, I can search on YouTube, Google, Spotify and Maps.")
        val uri = Uri.parse(app.url(Uri.encode(query)))

        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (app.inChrome) intent.setPackage("com.android.chrome")
        val started = startSafely(context, intent) || startSafely(context, Intent(Intent.ACTION_VIEW, uri))
        return if (started) ok("Okay, searching ${app.label} for $query.")
        else fail("Sorry, I couldn't open the search results.")
    }

    // ---------- Open an app ----------

    fun openApp(context: Context, spokenName: String): CommandResult {
        val key = spokenName.lowercase().filter { it.isLetterOrDigit() }
        if (key.isBlank()) return fail("Which app should I open?")

        when (key) {
            "settings" -> return openThing(Intent(Settings.ACTION_SETTINGS), context, "Settings")
            "camera" -> return openCamera(context, null)
            "phone", "dialer", "phoneapp" -> return openPhone(context)
            "contacts", "contact" -> return openContacts(context)
            "calculator" -> return openCalculator(context)
            "gallery", "photos", "photo", "photogallery" -> return openGallery(context)
        }

        val pm = context.packageManager
        KNOWN_APPS[key]?.let { known ->
            for (pkg in known.packages) {
                val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
                if (startSafely(context, intent)) return ok("Okay, opening ${known.label}.")
            }
            return fail("Sorry, ${known.label} doesn't seem to be installed.")
        }

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = pm.queryIntentActivities(launcher, 0).map {
            it.loadLabel(pm).toString() to it.activityInfo.packageName
        }
        fun k(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

        val match = apps.firstOrNull { k(it.first) == key }
            ?: apps.firstOrNull { k(it.first).startsWith(key) }
            ?: apps.firstOrNull { k(it.first).contains(key) }
            ?: apps.firstOrNull { key.length >= 5 && TextNormalizer.editDistance(k(it.first), key) <= 2 }
            ?: return fail("Sorry, I couldn't find an app called $spokenName.")

        val intent = pm.getLaunchIntentForPackage(match.second)
            ?: return fail("Sorry, I couldn't open ${match.first}.")
        return if (startSafely(context, intent)) ok("Okay, opening ${match.first}.")
        else fail("Sorry, I couldn't open ${match.first}.")
    }

    private fun openThing(intent: Intent, context: Context, name: String) =
        if (startSafely(context, intent)) ok("Okay, opening $name.") else fail("Sorry, I couldn't open $name.")

    fun openContacts(context: Context) =
        openThing(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI), context, "your contacts")

    fun openPhone(context: Context) = openThing(Intent(Intent.ACTION_DIAL), context, "the Phone app")

    fun openCalculator(context: Context): CommandResult {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
        return openThing(intent, context, "the Calculator")
    }

    fun openGallery(context: Context): CommandResult {
        val selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
        if (startSafely(context, selector)) return ok("Okay, opening the Gallery.")
        return openThing(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), context, "the Gallery")
    }

    // ---------- Camera ----------

    fun openCamera(context: Context, front: Boolean?): CommandResult {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        if (front != null) {
            // camera apps differ: these are the common ways to ask for a lens
            intent.putExtra("android.intent.extras.CAMERA_FACING", if (front) 1 else 0)
            intent.putExtra("android.intent.extras.LENS_FACING_FRONT", if (front) 1 else 0)
            intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", front)
        }
        val name = when (front) {
            true -> "the front camera"
            false -> "the rear camera"
            null -> "the camera"
        }
        return openThing(intent, context, name)
    }

    /** Takes a photo with VANI's own camera screen and saves it to the gallery. */
//    fun takePhoto(context: Context, front: Boolean): CommandResult {
//        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
//                PackageManager.PERMISSION_GRANTED
//        if (!allowed) {
//            startSafely(
//                context,
//                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
//            )
//            return fail("I need the Camera permission to take pictures. I've opened VANI's settings. Allow Camera, then ask me again.")
//        }
//        val intent = Intent(context, CameraCaptureActivity::class.java)
//            .putExtra(CameraCaptureActivity.EXTRA_FRONT, front)
//        return if (startSafely(context, intent)) {
//            ok(if (front) "Okay, get ready for your selfie. I'll take it in 3 seconds." else "Okay, taking a picture in 2 seconds.")
//        } else {
//            fail("Sorry, I couldn't start the camera.")
//        }
//    }

    // ---------- Music and video controls ----------

    fun media(context: Context, action: MediaAction): CommandResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, action.keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, action.keyCode))
        return ok(action.message)
    }

    fun spotifyPlay(context: Context, query: String): CommandResult {
        val spotify = "com.spotify.music"
        if (context.packageManager.getLaunchIntentForPackage(spotify) == null) {
            return fail("Sorry, Spotify isn't installed on this phone.")
        }

        val playIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(spotify)
            putExtra(SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(MediaStore.EXTRA_MEDIA_TITLE, query)
        }
        val started = startSafely(context, playIntent) ||
                startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")))
        if (!started) return fail("Sorry, I couldn't start Spotify.")

        val service = VaniAccessibilityService.instance
            ?: return ok(
                "Okay, I asked Spotify to play $query. If it only shows results, tap the song. " +
                        "Turn on VANI in Accessibility and I can tap it for you."
            )
        return if (service.ensureSpotifyPlaying(query)) {
            ok("Okay, playing $query on Spotify.")
        } else {
            ok("I opened Spotify for $query, but I couldn't confirm it is playing. If you see the results, tap the song.")
        }
    }

    // ---------- YouTube results ----------

    fun youtubeVideo(context: Context, position: Int): CommandResult {
        val service = VaniAccessibilityService.instance ?: return needAccessibility(context, "pick a YouTube video")
        val name = when (position) {
            1 -> "first"
            2 -> "second"
            3 -> "third"
            else -> "number $position"
        }
        return if (service.clickYouTubeVideo(position)) ok("Okay, playing the $name video.")
        else fail("Sorry, I couldn't find the $name video. Make sure the YouTube results are on the screen.")
    }
}

//annotation class CameraCaptureActivity(val context: Context, val java: Any)
