package com.example.vani.ui.commands

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlin.math.roundToInt

data class CommandResult(val success: Boolean, val message: String)

/**
 * Turns recognized speech ("turn on the torch") into a real action.
 * Add new commands by adding a branch in [handle].
 */
object CommandProcessor {

    private val FILLERS = setOf("please", "hey", "vani", "the", "a", "an", "my", "now", "app", "application", "to", "for", "me")
    private val TORCH_WORDS = setOf("torch", "flashlight", "flash")
    private val ON_WORDS = setOf("on", "enable", "start")
    private val OFF_WORDS = setOf("off", "disable", "stop")
    private val UP_WORDS = setOf("up", "increase", "raise", "louder", "higher", "more")
    private val DOWN_WORDS = setOf("down", "decrease", "lower", "reduce", "quieter", "softer", "less")
    private val OPEN_WORDS = setOf("open", "launch", "run", "start")

    fun handle(context: Context, rawText: String): CommandResult {
        val app = context.applicationContext
        val words = normalize(rawText).split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return CommandResult(false, "Didn't catch that")

        return when {
            words.any { it in TORCH_WORDS } -> torch(app, words)
            "wifi" in words -> wifi(app)
            "volume" in words || "mute" in words || "unmute" in words ||
                    "louder" in words || "quieter" in words -> volume(app, words)
            "home" in words -> launch(app, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), "Going home")
            "back" in words -> CommandResult(false, "“Go back” needs accessibility access (coming next)")
            "scroll" in words -> CommandResult(false, "Scrolling needs accessibility access (coming next)")
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
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
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
        } catch (e: Exception) {
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

    // ---------- Open an app ----------

    private fun openApp(context: Context, words: List<String>): CommandResult {
        val index = words.indexOfFirst { it in OPEN_WORDS }
        val query = words.drop(index + 1).joinToString("") // "you tube" -> "youtube"
        if (query.isBlank()) return CommandResult(false, "Which app should I open?")

        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = pm.queryIntentActivities(launcher, 0).map {
            it.loadLabel(pm).toString() to it.activityInfo.packageName
        }

        fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

        val match = apps.firstOrNull { key(it.first) == query }
            ?: apps.firstOrNull { key(it.first).startsWith(query) }
            ?: apps.firstOrNull { key(it.first).contains(query) }
            ?: return CommandResult(false, "Couldn't find an app named “${words.drop(index + 1).joinToString(" ")}”")

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
        } catch (e: Exception) {
            CommandResult(false, "Couldn't do that")
        }
    }
}