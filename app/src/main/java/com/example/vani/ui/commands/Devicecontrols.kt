package com.example.vani.ui.commands

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlin.math.roundToInt

enum class VolumeAction { UP, DOWN, MUTE, UNMUTE, SET }

/** Phone settings: flashlight, Wi-Fi, Bluetooth, volume, brightness, silent mode, battery ... */
object DeviceControls {

    private const val TAG = "VaniCommands"

    private fun word(on: Boolean) = if (on) "on" else "off"

    // ---------- Flashlight ----------

    private var torchOn = false
    private var torchCallbackRegistered = false

    fun torch(context: Context, on: Boolean?): CommandResult {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        if (!torchCallbackRegistered) {
            torchCallbackRegistered = true
            manager.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    torchOn = enabled
                }
            }, Handler(Looper.getMainLooper()))
        }

        val cameraId = runCatching {
            manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull() ?: return fail("Sorry, I couldn't find a flashlight on this phone.")

        val turnOn = on ?: !torchOn // "torch" alone switches it
        return try {
            manager.setTorchMode(cameraId, turnOn)
            torchOn = turnOn
            ok("Okay, the flashlight is turning ${word(turnOn)}.")
        } catch (error: Exception) {
            Log.w(TAG, "Torch failed", error)
            fail("Sorry, I couldn't control the flashlight.")
        }
    }

    // ---------- Wi-Fi, Bluetooth, Airplane mode, Battery saver ----------

    @Suppress("DEPRECATION")
    fun wifi(context: Context, on: Boolean?): CommandResult {
        // Android 9 and older still let apps switch Wi-Fi directly
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && on != null) {
            return try {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifi.isWifiEnabled = on
                ok("Okay, Wi-Fi is turning ${word(on)}.")
            } catch (error: Exception) {
                Log.w(TAG, "Wi-Fi failed", error)
                fail("Sorry, I couldn't change Wi-Fi.")
            }
        }
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_WIFI
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        return viaSettingsSwitch(context, Intent(action), "Wi-Fi", on)
    }

    fun bluetooth(context: Context, on: Boolean?) =
        viaSettingsSwitch(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Bluetooth", on)

    fun airplaneMode(context: Context, on: Boolean?) =
        viaSettingsSwitch(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), "Airplane mode", on)

    fun batterySaver(context: Context, on: Boolean?) =
        viaSettingsSwitch(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), "Battery saver", on)

    /**
     * Android does not let apps flip these switches, so VANI opens the screen and,
     * if Accessibility is on, taps the switch for you. Runs on a worker thread (it waits for the screen).
     */
    private fun viaSettingsSwitch(context: Context, intent: Intent, name: String, on: Boolean?): CommandResult {
        if (!startSafely(context, intent)) return fail("Sorry, I couldn't open the $name settings.")
        if (on == null) return ok("Okay, opening the $name settings.")

        val service = VaniAccessibilityService.instance
            ?: return ok(
                "Okay, I opened the $name settings. Turn on VANI in Accessibility and I can flip " +
                        "the switch for you. For now, please tap it yourself."
            )

        return when (service.toggleFirstSwitch(on)) {
            SwitchOutcome.CHANGED -> {
                Thread.sleep(600)
                service.goBack()
                ok("Okay, $name is now ${word(on)}.")
            }
            SwitchOutcome.ALREADY_SET -> {
                service.goBack()
                ok("$name is already ${word(on)}.")
            }
            SwitchOutcome.NOT_FOUND ->
                fail("I opened the $name settings, but I couldn't find the switch. Please tap it yourself.")
        }
    }

    // ---------- Volume ----------

    fun volume(context: Context, action: VolumeAction, percent: Int? = null): CommandResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val flags = AudioManager.FLAG_SHOW_UI

        return when (action) {
            VolumeAction.SET -> {
                val target = (percent ?: 50).coerceIn(0, 100)
                val max = audio.getStreamMaxVolume(stream)
                audio.setStreamVolume(stream, (max * target / 100.0).roundToInt(), flags)
                ok("Okay, setting the volume to $target percent.")
            }
            VolumeAction.UP -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, flags)
                ok("Okay, turning the volume up.")
            }
            VolumeAction.DOWN -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, flags)
                ok("Okay, turning the volume down.")
            }
            VolumeAction.MUTE -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, flags)
                ok("Okay, the sound is muted.")
            }
            VolumeAction.UNMUTE -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, flags)
                ok("Okay, the sound is back on.")
            }
        }
    }

    // ---------- Brightness and screen rotation (need "Modify system settings") ----------

    private fun needWriteSettings(context: Context): CommandResult? {
        if (Settings.System.canWrite(context)) return null
        startSafely(
            context,
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
        return fail(
            "I need permission to change system settings. I've opened the screen. " +
                    "Allow VANI there, then ask me again."
        )
    }

    /** [direction]: +1 brighter, -1 dimmer. If [percent] is given the brightness is set to it. */
    fun brightness(context: Context, direction: Int, percent: Int?): CommandResult {
        needWriteSettings(context)?.let { return it }
        return try {
            val resolver = context.contentResolver
            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val target = when {
                percent != null -> percent.coerceIn(1, 100) * 255 / 100
                direction > 0 -> current + 40
                else -> current - 40
            }.coerceIn(5, 255)

            Settings.System.putInt(
                resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target)

            val shown = target * 100 / 255
            ok(
                when {
                    percent != null -> "Okay, setting the brightness to $shown percent."
                    direction > 0 -> "Okay, making the screen brighter. It is about $shown percent now."
                    else -> "Okay, making the screen dimmer. It is about $shown percent now."
                }
            )
        } catch (error: Exception) {
            Log.w(TAG, "Brightness failed", error)
            fail("Sorry, I couldn't change the brightness.")
        }
    }

    fun autoRotate(context: Context, on: Boolean): CommandResult {
        needWriteSettings(context)?.let { return it }
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
            ok("Okay, screen rotation is now ${word(on)}.")
        } catch (error: Exception) {
            Log.w(TAG, "Rotation failed", error)
            fail("Sorry, I couldn't change screen rotation.")
        }
    }

    // ---------- Silent mode (calls and notifications) ----------

    fun silentMode(context: Context, on: Boolean): CommandResult {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!manager.isNotificationPolicyAccessGranted) {
            startSafely(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return fail(
                "I need Do Not Disturb access for that. I've opened the screen. " +
                        "Allow VANI there, then ask me again."
            )
        }
        return try {
            if (on) {
                // "alarms only": calls and notifications are silent, alarms still ring
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                ok("Okay, silencing calls and notifications. Alarms will still ring.")
            } else {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                ok("Okay, the sound is back on for calls and notifications.")
            }
        } catch (error: Exception) {
            Log.w(TAG, "Do Not Disturb failed", error)
            fail("Sorry, I couldn't change silent mode.")
        }
    }

    fun vibrate(context: Context): CommandResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            ok("Okay, the phone is on vibrate.")
        } catch (error: Exception) {
            Log.w(TAG, "Vibrate failed", error)
            fail("Sorry, I couldn't switch to vibrate.")
        }
    }

    // ---------- Battery ----------

    fun showBattery(context: Context): CommandResult {
        val status = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return fail("Sorry, I couldn't read the battery level.")
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return fail("Sorry, I couldn't read the battery level.")

        val percent = level * 100 / scale
        val state = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL
        return ok("Your battery is at $percent percent${if (charging) " and charging" else ""}.")
    }

    // ---------- Lock and screenshot (Accessibility, Android 9+) ----------

    fun lockPhone(context: Context): CommandResult {
        val service = VaniAccessibilityService.instance ?: return needAccessibility(context, "lock your phone")
        return if (service.lockScreen()) ok("Okay, locking your phone.")
        else fail("Sorry, locking the screen needs Android 9 or newer.")
    }

    fun screenshot(context: Context): CommandResult {
        val service = VaniAccessibilityService.instance ?: return needAccessibility(context, "take a screenshot")
        return if (service.takeScreenshot()) ok("Okay, taking a screenshot.")
        else fail("Sorry, screenshots by voice need Android 9 or newer.")
    }
}