package com.example.vani.ui.commands

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import kotlin.math.roundToInt

data class CommandResult(val success: Boolean, val message: String)

/**
 * Turns recognized speech ("turn on the torch") into a real action.
 * Add new commands by adding a branch in [handle].
 */
object CommandProcessor {

    private const val TAG = "VaniCommands"

    private val FILLERS = setOf("please", "hey", "vani", "the", "a", "an", "my", "now", "app", "application", "to", "for", "me")
    private val TORCH_WORDS = setOf("torch", "flashlight", "flash")
    private val ON_WORDS = setOf("on", "enable", "start")
    private val OFF_WORDS = setOf("off", "disable", "stop")
    private val UP_WORDS = setOf("up", "increase", "raise", "louder", "higher", "more")
    private val DOWN_WORDS = setOf("down", "decrease", "lower", "reduce", "quieter", "softer", "less")
    private val OPEN_WORDS = setOf("open", "launch", "run", "start")

    // silent mode / do not disturb
    private val SILENT_WORDS = setOf("silent", "silence", "silenced", "dnd", "disturb")
    private val CALL_TARGETS = setOf("notification", "notifications", "call", "calls", "phone", "ringer")
    private val SOUND_BACK_WORDS = setOf("normal", "unsilent", "unsilence", "unmute", "ring", "ringing", "loud", "sound")

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

    fun handle(context: Context, rawText: String): CommandResult {
        val app = context.applicationContext
        val words = normalize(rawText).split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return CommandResult(false, "Didn't catch that")

        return when {
            words.any { it in TORCH_WORDS } -> torch(app, words)
            "wifi" in words -> wifi(app)
            isSilentCommand(words) -> silent(app, words)
            "vibrate" in words || "vibration" in words -> vibrate(app)
            "volume" in words || "mute" in words || "unmute" in words ||
                    "louder" in words || "quieter" in words -> volume(app, words)
            "home" in words -> launch(app, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), "Going home")
            "back" in words -> accessibility(app, "Going back") { it.goBack() }
            "scroll" in words || "swipe" in words -> {
                val down = "up" !in words
                accessibility(app, if (down) "Scrolling down" else "Scrolling up") { it.scroll(down) }
            }
            "recent" in words || "recents" in words || "multitask" in words ->
                accessibility(app, "Recent apps") { it.showRecents() }
            "quick" in words && "settings" in words ->
                accessibility(app, "Quick settings") { it.showQuickSettings() }
            "notification" in words || "notifications" in words ->
                accessibility(app, "Notification panel") { it.showNotifications() }
            words.any { it in OPEN_WORDS } -> openApp(app, words)
            else -> CommandResult(false, "I didn't understand that")
        }
    }

    // ---------- Normalizing speech text ----------

    private fun normalize(raw: String): String {
        val text = raw.lowercase()
            .replace("wi-fi", "wifi")
            .replace("wi fi", "wifi")
            .replace("flash light", "flashlight")
            .replace("what's app", "whatsapp")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .replace("what s app", "whatsapp")
            .replace("whats app", "whatsapp")
            .trim()
        return text.split(" ").filter { it !in FILLERS }.joinToString(" ")
    }

    // ---------- Torch ----------

    private var torchOn = false
    private var torchCallbackRegistered = false

    private fun torch(context: Context, words: List<String>): CommandResult {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        if (!torchCallbackRegistered) {
            torchCallbackRegistered = true
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    torchOn = enabled
                }
            }, Handler(Looper.getMainLooper()))
        }

        val cameraId = runCatching {
            cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull() ?: return CommandResult(false, "No flashlight found on this phone")

        val turnOn = when {
            words.any { it in OFF_WORDS } -> false
            words.any { it in ON_WORDS } -> true
            else -> !torchOn // "torch" alone toggles
        }

        return try {
            cm.setTorchMode(cameraId, turnOn)
            torchOn = turnOn
            CommandResult(true, if (turnOn) "Torch on" else "Torch off")
        } catch (error: Exception) {
            Log.w(TAG, "Torch failed", error)
            CommandResult(false, "Couldn't control the torch")
        }
    }

    // ---------- Wi-Fi ----------

    private fun wifi(context: Context): CommandResult {
        // Since Android 10 apps are not allowed to switch Wi-Fi directly, so we open the Wi-Fi panel.
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_WIFI
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        return launch(context, Intent(action), "Opening Wi-Fi settings")
    }

    // ---------- Silent mode (calls + notifications) ----------

    private fun isSilentCommand(words: List<String>): Boolean =
        words.any { it in SILENT_WORDS } ||
                (words.any { it == "mute" || it == "unmute" } && words.any { it in CALL_TARGETS }) ||
                (("normal" in words || "ring" in words) && "mode" in words)

    private fun silent(context: Context, words: List<String>): CommandResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android only lets an app change Do Not Disturb after the user allows it once.
        if (!nm.isNotificationPolicyAccessGranted) {
            launch(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), "")
            return CommandResult(false, "Allow VANI under “Do Not Disturb access”, then try again")
        }

        val soundBack = words.any { it in OFF_WORDS || it in SOUND_BACK_WORDS }
        return try {
            if (soundBack) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                CommandResult(true, "Sound is back on")
            } else {
                // "alarms only": calls and notifications are silent, alarms still ring
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                CommandResult(true, "Calls and notifications silenced")
            }
        } catch (error: Exception) {
            Log.w(TAG, "Do Not Disturb failed", error)
            CommandResult(false, "Couldn't change silent mode")
        }
    }

    private fun vibrate(context: Context): CommandResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            CommandResult(true, "Vibrate mode")
        } catch (error: Exception) {
            Log.w(TAG, "Vibrate mode failed", error)
            CommandResult(false, "Couldn't switch to vibrate")
        }
    }

    // ---------- Volume ----------

    private fun volume(context: Context, words: List<String>): CommandResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val flags = AudioManager.FLAG_SHOW_UI
        val number = words.firstNotNullOfOrNull { it.toIntOrNull() }

        return when {
            number != null -> {
                val percent = number.coerceIn(0, 100)
                val max = audio.getStreamMaxVolume(stream)
                audio.setStreamVolume(stream, (max * percent / 100.0).roundToInt(), flags)
                CommandResult(true, "Volume set to $percent%")
            }
            "unmute" in words -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, flags)
                CommandResult(true, "Unmuted")
            }
            "mute" in words -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, flags)
                CommandResult(true, "Muted")
            }
            words.any { it in UP_WORDS } -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, flags)
                CommandResult(true, "Volume up")
            }
            words.any { it in DOWN_WORDS } -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, flags)
                CommandResult(true, "Volume down")
            }
            else -> CommandResult(false, "Say “volume up” or “volume down”")
        }
    }

    // ---------- Back / scroll / panels (need the accessibility service) ----------

    private fun accessibility(
        context: Context,
        successMessage: String,
        action: (VaniAccessibilityService) -> Boolean,
    ): CommandResult {
        val service = VaniAccessibilityService.instance
        if (service == null) {
            launch(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "")
            return CommandResult(false, "Turn on “VANI” in Accessibility settings, then try again")
        }
        return if (action(service)) CommandResult(true, successMessage) else CommandResult(false, "Couldn't do that")
    }

    // ---------- Open an app ----------

    private fun openApp(context: Context, words: List<String>): CommandResult {
        val index = words.indexOfFirst { it in OPEN_WORDS }
        val nameWords = words.drop(index + 1)
        val query = nameWords.joinToString("") // "you tube" -> "youtube"
        if (query.isBlank()) return CommandResult(false, "Which app should I open?")

        // built-in screens
        when (query) {
            "settings" -> return launch(context, Intent(Settings.ACTION_SETTINGS), "Opening Settings")
            "camera" -> return launch(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Opening camera")
            "phone", "dialer" -> return launch(context, Intent(Intent.ACTION_DIAL), "Opening phone")
        }

        val pm = context.packageManager

        // well-known apps first (exact package names)
        KNOWN_APPS[query]?.let { known ->
            for (pkg in known.packages) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) return launch(context, intent, "Opening ${known.label}")
            }
        }

        // any other installed app: match by its name
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = pm.queryIntentActivities(launcher, 0).map {
            it.loadLabel(pm).toString() to it.activityInfo.packageName
        }

        fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

        val match = apps.firstOrNull { key(it.first) == query }
            ?: apps.firstOrNull { key(it.first).startsWith(query) }
            ?: apps.firstOrNull { key(it.first).contains(query) }
            ?: return CommandResult(false, "Couldn't find an app named “${nameWords.joinToString(" ")}”")

        val intent = pm.getLaunchIntentForPackage(match.second)
            ?: return CommandResult(false, "Couldn't open ${match.first}")
        return launch(context, intent, "Opening ${match.first}")
    }

    // ---------- helper ----------

    private fun launch(context: Context, intent: Intent, message: String): CommandResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CommandResult(true, message)
        } catch (error: Exception) {
            Log.w(TAG, "Could not start $message", error)
            CommandResult(false, "Couldn't do that")
        }
    }
}