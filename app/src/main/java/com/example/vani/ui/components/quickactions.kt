package com.example.vani.ui.components
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.vani.ui.commands.CommandProcessor
import com.example.vani.ui.commands.CommandResult

/** Every quick action the user can show on the home screen. */
enum class QuickAction(val label: String, val icon: ImageVector) {
    Torch("Torch", Icons.Filled.FlashlightOn),
    WiFi("Wi-Fi", Icons.Filled.Wifi),
    VolumeUp("Volume +", Icons.AutoMirrored.Filled.VolumeUp),
    VolumeDown("Volume –", Icons.AutoMirrored.Filled.VolumeDown),
    Mute("Mute", Icons.AutoMirrored.Filled.VolumeOff),
    Bluetooth("Bluetooth", Icons.Filled.Bluetooth),
    Camera("Camera", Icons.Filled.CameraAlt);

    companion object {
        val defaults = listOf(Torch, WiFi, VolumeUp, VolumeDown)
    }
}

/** Runs a quick action. Reuses [CommandProcessor] so voice and buttons behave the same. */
object QuickActionRunner {
    private const val TAG = "VaniQuickAction"

    fun run(context: Context, action: QuickAction): CommandResult {
        val app = context.applicationContext
        return when (action) {
            QuickAction.Torch -> CommandProcessor.handle(app, "torch") // toggles
            QuickAction.WiFi -> CommandProcessor.handle(app, "wifi")
            QuickAction.VolumeUp -> CommandProcessor.handle(app, "volume up")
            QuickAction.VolumeDown -> CommandProcessor.handle(app, "volume down")
            QuickAction.Mute -> {
                val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
                CommandProcessor.handle(app, if (muted) "unmute" else "mute")
            }
            QuickAction.Bluetooth -> open(app, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Opening Bluetooth settings")
            QuickAction.Camera -> open(app, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Opening camera")
        }
    }

    private fun open(context: Context, intent: Intent, message: String): CommandResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CommandResult(true, message)
        } catch (error: Exception) {
            Log.w(TAG, "Could not open $message", error)
            CommandResult(false, "Couldn't open that")
        }
    }
}

/** Remembers which quick actions the user wants on the home screen. */
object QuickActionPrefs {
    private const val PREFS = "vani_prefs"
    private const val KEY = "quick_actions"

    fun load(context: Context): List<QuickAction> {
        return try {
            val raw = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return QuickAction.defaults
            raw.split(",")
                .filter { it.isNotBlank() }
                .mapNotNull { name -> QuickAction.entries.firstOrNull { it.name == name } }
        } catch (error: Exception) {
            QuickAction.defaults
        }
    }

    fun save(context: Context, actions: List<QuickAction>) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, actions.joinToString(",") { it.name })
            .apply()
    }
}