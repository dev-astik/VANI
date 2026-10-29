package com.example.vani.ui.commands

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/**
 * Understands what the user said and does it. Every command ends with a friendly sentence.
 * Order matters: the more specific commands are checked first.
 */
object CommandProcessor {

    private const val TAG = "VaniCommands"
    private const val UI_TAG = "VANI_UI"

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Runs a command on a background thread and delivers the answer on the main thread. */
    fun handleAsync(context: Context, rawText: String, onResult: (CommandResult) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val result = try {
                handle(app, rawText)
            } catch (error: Throwable) {
                Log.e(TAG, "Command crashed", error)
                fail("Sorry, something went wrong while doing that.")
            }
            main.post { onResult(result) }
        }
    }

    // ---------- Patterns (compiled once) ----------

    private val ON_RE = Regex("""\b(?:on|enable|activate|start)\b""")
    private val OFF_RE = Regex("""\b(?:off|disable|deactivate|stop)\b""")
    private val UP_RE = Regex("""\b(?:increase|raise|up|louder|brighter|higher|more|max|maximum)\b""")
    private val DOWN_RE = Regex("""\b(?:decrease|lower|down|quieter|dim|dimmer|softer|less|reduce|min|minimum)\b""")
    private val NUMBER_RE = Regex("""\b\d{1,3}\b""")

    private val WHATSAPP_ON_RE = Regex("""\s+(?:on|in|using|via|through)\s+whatsapp\b""")
    private val WHATSAPP_OPEN_RE = Regex("""^(?:open|launch|start)\s+whatsapp(?:\s+and)?\s*""")
    private val WA_SAYING = Regex("""^(?:send|text|message)\s+(?:a\s+)?(?:message\s+)?(?:to\s+)?(.+?)\s+(?:saying|that says|says|that)\s+(.+)$""")
    private val WA_SEND_TO = Regex("""^(?:send|write|type|text)\s+(.+?)\s+to\s+(.+)$""")
    private val WA_CALL = Regex("""^(?:voice call|call|phone|ring)\s+(.+)$""")
    private val WA_SEARCH_SEND = Regex("""^(?:search for|search|find|look for)\s+(.+?)\s+and\s+(?:send|say|type|write|message)\s+(.+)$""")
    private val WA_SEARCH = Regex("""^(?:search for|search|find|look for|chat with|open chat with)\s+(.+)$""")

    private val SPOTIFY_PLAY = Regex("""^(?:play|start|put on)\s+(?:the\s+)?(?:song\s+|track\s+|music\s+|playlist\s+|album\s+)?(.+?)\s+(?:on|in|using|with)\s+spotify$""")
    private val SPOTIFY_OPEN_PLAY = Regex("""^(?:open|launch)\s+spotify\s+and\s+play\s+(?:the\s+)?(?:song\s+)?(.+)$""")
    private val GENERIC_MUSIC = setOf("music", "song", "songs", "something", "anything", "some music")

    private val VIDEO_POSITION = Regex("""\b(?:play|watch|open|click|tap|select)\b.*?\b(first|1st|second|2nd|third|3rd|fourth|4th|fifth|5th|top most|topmost|top|1|2|3|4|5)\s+(?:video|result|one)\b""")

    private val MEDIA_NEXT = Regex("""^(?:play |go to |skip to |put on )?(?:the )?next(?: song| track| video| one)?$|^skip(?: this| the)?(?: song| track| video)?$""")
    private val MEDIA_PREVIOUS = Regex("""^(?:play |go to )?(?:the )?(?:previous|last)(?: song| track| video| one)?$""")
    private val MEDIA_PAUSE = Regex("""^(?:pause|stop)(?: the)?(?: music| song| songs| video| playback| playing)?$""")
    private val MEDIA_PLAY = Regex("""^(?:play|resume|continue)(?: the)?(?: music| song| songs| video| playback)?$""")

    private val OPEN_CONTACTS = Regex("""^(?:open|launch|show)\s+(?:the\s+|my\s+)?contacts?(?:\s+app)?$""")
    private val FIND_IN_CONTACTS = Regex("""^(?:find|search(?:\s+for)?|look\s+up|show)\s+(.+?)\s+(?:in|on|from)\s+(?:my\s+|the\s+)?contacts?$""")
    private val SEARCH_CONTACTS_FOR = Regex("""^(?:search|find|look)\s+(?:in\s+)?(?:my\s+|the\s+)?contacts?\s+(?:for\s+)?(.+)$""")
    private val OPEN_PHONE = Regex("""^(?:open|launch)\s+(?:the\s+)?(?:phone|dialer)(?:\s+app)?$""")
    private val CALL = Regex("""^(?:call|phone|dial)\s+(.+)$""")

    private val SEARCH_OPEN_APP = Regex("""^(?:open|launch|start)\s+(.+?)\s+(?:and\s+)?(?:search(?:\s+for)?|find|look\s+up)\s+(.+)$""")
    private val SEARCH_ON_APP = Regex("""^(?:search(?:\s+for)?|find|look\s+up)\s+(.+)\s+(?:on|in|using)\s+(.+?)$""")
    private val SEARCH_PLAIN = Regex("""^(?:search(?:\s+for)?|google|look\s+up)\s+(.+)$""")

    private val SELFIE = Regex("""\b(?:take|capture|click|snap|shoot)\b.*\bselfie\b|^selfie$""")
    private val PHOTO = Regex("""\b(?:take|capture|click|snap)\b.*\b(?:picture|photo|pic)\b""")
    private val OPEN_LENS_CAMERA = Regex("""^(?:open|launch|start)\s+(?:the\s+)?(front|rear|back)\s+camera$""")
    private val OPEN_GALLERY = Regex("""^(?:open|launch|start)\s+(?:the\s+)?(?:gallery|photos|photo gallery)$""")

    private val SILENT = Regex("""\b(?:do not disturb|dnd|silent|silence)\b|\b(?:normal|ring|sound)\s+mode\b|\b(?:mute|unmute)\s+(?:the\s+|my\s+)?(?:phone|ringer|calls?|notifications?|everything)\b""")
    private val SOUND_BACK = Regex("""\b(?:unmute|normal|unsilent|unsilence|(?:ring|sound) mode)\b""")
    private val TORCH_RE = Regex("""\b(?:flashlight|torch|flash)\b""")
    private val WIFI_RE = Regex("""\bwifi\b""")
    private val BLUETOOTH_RE = Regex("""\bbluetooth\b""")
    private val AIRPLANE_RE = Regex("""\bairplane\b|\bflight mode\b""")
    private val SAVER_RE = Regex("""\bbattery saver\b|\bpower saving\b|\bsaver mode\b""")
    private val BATTERY_RE = Regex("""\bbattery\b""")
    private val BRIGHTNESS_RE = Regex("""\bbrightness\b""")
    private val ROTATION_RE = Regex("""\b(?:screen )?rotation\b|\bauto rotate\b""")
    private val VIBRATE_RE = Regex("""\bvibrate\b|\bvibration mode\b""")
    private val LOCK_RE = Regex("""\block\s+(?:my\s+|the\s+)?(?:phone|screen|device)\b|^lock$""")
    private val SCREENSHOT_RE = Regex("""\bscreenshot\b""")
    private val VOLUME_RE = Regex("""\b(?:volume|louder|quieter|mute|unmute)\b""")
    private val MUTE_RE = Regex("""\bmute\b""")

    private val HOME_RE = Regex("""^(?:go\s+)?(?:to\s+)?(?:the\s+)?home(?:\s+screen)?$|\bgo\s+(?:to\s+)?(?:the\s+)?home\b|\bhome\s+screen\b""")
    private val BACK_RE = Regex("""^(?:go\s+|press\s+|navigate\s+)?back$|\bgo\s+back\b""")
    private val RECENTS_RE = Regex("""\brecents?\b|\brecent\s+apps?\b|\bmultitask\b|\bapp\s+switcher\b""")
    private val QUICK_SETTINGS_RE = Regex("""\bquick\s+settings\b""")
    private val NOTIFICATIONS_RE = Regex("""\bnotifications?\b""")
    private val SCROLL_RE = Regex("""\b(?:scroll|swipe)\b""")

    private val OPEN_APP = Regex("""^(?:open|launch|start|run)\s+(?:the\s+)?(.+?)(?:\s+app)?$""")

    private fun stateOf(text: String): Boolean? = when {
        OFF_RE.containsMatchIn(text) -> false
        ON_RE.containsMatchIn(text) -> true
        else -> null
    }

    // ---------- The router ----------

    /** Blocking: call it from a worker thread ([handleAsync]) when the command may take a while. */
    fun handle(context: Context, rawText: String): CommandResult {
        val app = context.applicationContext
        val text = TextNormalizer.clean(rawText)
        Log.d(TAG, "Heard: \"$rawText\"  ->  \"$text\"")

        if (text.isBlank()) return fail("Sorry, I didn't catch that. Please try again.")

        // 0) an answer to a question VANI just asked ("Should I send it?")
        PendingAction.answer(app, text)?.let { return it }

        // 1) diagnostic: logs what is on screen (search "VANI_UI" in Logcat)
        if (text == "test screen" || text == "debug screen" || text == "test youtube screen" || text == "debug youtube screen") {
            return testScreen()
        }

        // 2) maths
        CalculatorParser.tryCalculate(text)?.let { return it }

        // 3) WhatsApp
        if ("whatsapp" in text) return handleWhatsApp(app, text)

        // 4) Spotify
        SPOTIFY_PLAY.matchEntire(text)?.let { return playOnSpotify(app, it.groupValues[1]) }
        SPOTIFY_OPEN_PLAY.matchEntire(text)?.let { return playOnSpotify(app, it.groupValues[1]) }

        // 5) YouTube: "play the first video"
        VIDEO_POSITION.find(text)?.let { match ->
            NumberWords.ordinal(match.groupValues[1])?.let { return AppActions.youtubeVideo(app, it) }
        }

        // 6) play / pause / next / previous
        mediaAction(text)?.let { return AppActions.media(app, it) }

        // 7) contacts and phone
        contactCommand(app, text)?.let { return it }

        // 8) search
        searchCommand(app, text)?.let { return it }

        // 9) camera and gallery
//        cameraCommand(app, text)?.let { return it }

        // 10) phone settings and navigation
        deviceCommand(app, text)?.let { return it }

        // 11) open an app
        OPEN_APP.matchEntire(text)?.let { return AppActions.openApp(app, it.groupValues[1]) }

        return fail("Sorry, I didn't understand “$text”. Try saying “turn on the flashlight” or “open YouTube”.")
    }

    // ---------- WhatsApp ----------

    private fun contactName(value: String) =
        value.trim().removePrefix("my ").removePrefix("the ").trim()

    private fun sentence(value: String) = value.trim().replaceFirstChar { it.uppercase() }

    private fun handleWhatsApp(context: Context, text: String): CommandResult {
        val core = text
            .replace(WHATSAPP_ON_RE, "")
            .replace(WHATSAPP_OPEN_RE, "")
            .replace(Regex("""\bwhatsapp\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (core.isBlank()) return WhatsAppActions.open(context)

        WA_SAYING.matchEntire(core)?.let {
            return WhatsAppActions.message(context, contactName(it.groupValues[1]), sentence(it.groupValues[2]))
        }
        WA_SEND_TO.matchEntire(core)?.let {
            return WhatsAppActions.message(context, contactName(it.groupValues[2]), sentence(it.groupValues[1]))
        }
        WA_SEARCH_SEND.matchEntire(core)?.let {
            return WhatsAppActions.message(context, contactName(it.groupValues[1]), sentence(it.groupValues[2]))
        }
        WA_CALL.matchEntire(core)?.let {
            return WhatsAppActions.call(context, contactName(it.groupValues[1]))
        }
        WA_SEARCH.matchEntire(core)?.let {
            return WhatsAppActions.search(context, contactName(it.groupValues[1]))
        }
        return fail("Sorry, I didn't understand that WhatsApp command. Try “send hi to Mayur on WhatsApp”.")
    }

    // ---------- Spotify ----------

    private fun playOnSpotify(context: Context, query: String): CommandResult {
        val wanted = query.trim()
        if (wanted in GENERIC_MUSIC) {
            val opened = AppActions.openApp(context, "spotify")
            if (!opened.success) return opened
            Thread.sleep(1200)
            AppActions.media(context, MediaAction.PLAY)
            return ok("Okay, playing music on Spotify.")
        }
        return AppActions.spotifyPlay(context, wanted)
    }

    // ---------- Music and video buttons ----------

    private fun mediaAction(text: String): MediaAction? = when {
        MEDIA_NEXT.matches(text) -> MediaAction.NEXT
        MEDIA_PREVIOUS.matches(text) -> MediaAction.PREVIOUS
        MEDIA_PAUSE.matches(text) -> MediaAction.PAUSE
        MEDIA_PLAY.matches(text) -> MediaAction.PLAY
        else -> null
    }

    // ---------- Contacts and phone ----------

    private fun contactCommand(context: Context, text: String): CommandResult? {
        if (OPEN_CONTACTS.matches(text)) return AppActions.openContacts(context)
        if (OPEN_PHONE.matches(text)) return AppActions.openPhone(context)

        FIND_IN_CONTACTS.matchEntire(text)?.let { return PhoneActions.findContact(context, it.groupValues[1]) }
        SEARCH_CONTACTS_FOR.matchEntire(text)?.let { return PhoneActions.findContact(context, it.groupValues[1]) }
        CALL.matchEntire(text)?.let { return PhoneActions.call(context, contactName(it.groupValues[1])) }
        return null
    }

    // ---------- Search ----------

    private fun searchCommand(context: Context, text: String): CommandResult? {
        SEARCH_OPEN_APP.matchEntire(text)?.let { match ->
            if (AppActions.canSearchIn(match.groupValues[1])) {
                return AppActions.search(context, match.groupValues[1], match.groupValues[2])
            }
        }
        SEARCH_ON_APP.matchEntire(text)?.let { match ->
            if (AppActions.canSearchIn(match.groupValues[2])) {
                return AppActions.search(context, match.groupValues[2], match.groupValues[1])
            }
        }
        // "search for pizza near me": a normal Google search in Chrome
        SEARCH_PLAIN.matchEntire(text)?.let { return AppActions.search(context, "chrome", it.groupValues[1]) }
        return null
    }

    // ---------- Camera and gallery ----------

//    private fun cameraCommand(context: Context, text: String): CommandResult? {
//        if (SELFIE.containsMatchIn(text)) return AppActions.takePhoto(context, front = true)
//        if (PHOTO.containsMatchIn(text)) return AppActions.takePhoto(context, front = "front" in text)
//        OPEN_LENS_CAMERA.matchEntire(text)?.let { return AppActions.openCamera(context, front = it.groupValues[1] == "front") }
//        if (OPEN_GALLERY.matches(text)) return AppActions.openGallery(context)
//        return null
//    }

    // ---------- Phone settings and navigation ----------

    private fun deviceCommand(context: Context, text: String): CommandResult? {
        val state = stateOf(text)

        if (SILENT.containsMatchIn(text)) {
            val turnOn = state != false && !SOUND_BACK.containsMatchIn(text)
            return DeviceControls.silentMode(context, turnOn)
        }
        if (TORCH_RE.containsMatchIn(text)) return DeviceControls.torch(context, state)
        if (WIFI_RE.containsMatchIn(text)) return DeviceControls.wifi(context, state)
        if (BLUETOOTH_RE.containsMatchIn(text)) return DeviceControls.bluetooth(context, state)
        if (AIRPLANE_RE.containsMatchIn(text)) return DeviceControls.airplaneMode(context, state)
        if (SAVER_RE.containsMatchIn(text)) return DeviceControls.batterySaver(context, state)
        if (BATTERY_RE.containsMatchIn(text)) return DeviceControls.showBattery(context)

        if (BRIGHTNESS_RE.containsMatchIn(text)) {
            val number = NUMBER_RE.find(NumberWords.toDigits(text))?.value?.toIntOrNull()
            return when {
                number != null -> DeviceControls.brightness(context, 0, number)
                UP_RE.containsMatchIn(text) -> DeviceControls.brightness(context, +1, null)
                DOWN_RE.containsMatchIn(text) -> DeviceControls.brightness(context, -1, null)
                else -> fail("Say “increase the brightness” or “set brightness to 50 percent”.")
            }
        }

        if (ROTATION_RE.containsMatchIn(text)) return DeviceControls.autoRotate(context, state ?: true)
        if (VIBRATE_RE.containsMatchIn(text)) return DeviceControls.vibrate(context)
        if (LOCK_RE.containsMatchIn(text)) return DeviceControls.lockPhone(context)
        if (SCREENSHOT_RE.containsMatchIn(text)) return DeviceControls.screenshot(context)

        if (VOLUME_RE.containsMatchIn(text)) {
            val number = NUMBER_RE.find(NumberWords.toDigits(text))?.value?.toIntOrNull()
            return when {
                "unmute" in text -> DeviceControls.volume(context, VolumeAction.UNMUTE)
                MUTE_RE.containsMatchIn(text) -> DeviceControls.volume(context, VolumeAction.MUTE)
                "volume" in text && number != null -> DeviceControls.volume(context, VolumeAction.SET, number)
                UP_RE.containsMatchIn(text) -> DeviceControls.volume(context, VolumeAction.UP)
                DOWN_RE.containsMatchIn(text) -> DeviceControls.volume(context, VolumeAction.DOWN)
                else -> fail("Say “volume up” or “volume down”.")
            }
        }

        // navigation
        if (HOME_RE.containsMatchIn(text)) {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            return if (startSafely(context, home)) ok("Okay, going to the home screen.")
            else fail("Sorry, I couldn't go to the home screen.")
        }
        if (BACK_RE.containsMatchIn(text)) {
            return viaAccessibility(context, "go back", "Okay, going back.") { it.goBack() }
        }
        if (RECENTS_RE.containsMatchIn(text)) {
            return viaAccessibility(context, "open recent apps", "Okay, showing your recent apps.") { it.showRecents() }
        }
        if (QUICK_SETTINGS_RE.containsMatchIn(text)) {
            return viaAccessibility(context, "open quick settings", "Okay, opening quick settings.") { it.showQuickSettings() }
        }
        if (NOTIFICATIONS_RE.containsMatchIn(text)) {
            return viaAccessibility(context, "open the notification panel", "Okay, opening the notification panel.") { it.showNotifications() }
        }
        if (SCROLL_RE.containsMatchIn(text)) {
            val down = !Regex("""\bup\b""").containsMatchIn(text)
            return viaAccessibility(context, "scroll", if (down) "Okay, scrolling down." else "Okay, scrolling up.") { it.scroll(down) }
        }
        return null
    }

    private fun viaAccessibility(
        context: Context,
        what: String,
        success: String,
        action: (VaniAccessibilityService) -> Boolean,
    ): CommandResult {
        val service = VaniAccessibilityService.instance ?: return needAccessibility(context, what)
        return if (action(service)) ok(success) else fail("Sorry, I couldn't do that.")
    }

    // ---------- Diagnostic ----------

    private fun testScreen(): CommandResult {
        val service = VaniAccessibilityService.instance
            ?: return fail("The accessibility service is not connected. Turn on VANI in Accessibility settings.")

        val labels = service.getVisibleUiText()
        Log.d(UI_TAG, "----- SCREEN START (${labels.size} labels) -----")
        labels.forEach { Log.d(UI_TAG, it) }
        Log.d(UI_TAG, "----- SCREEN END -----")

        return if (labels.isEmpty()) ok("I found no readable text on this screen. Check Logcat for VANI_UI.")
        else ok("Done. I wrote the screen text to Logcat. Search for VANI_UI.")
    }
}