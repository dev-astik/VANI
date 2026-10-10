package com.example.vani.ui.wakeword
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts / stops the wake word service and remembers whether the user turned it on. */
object WakeWordController {
    private const val PREFS = "vani_prefs"
    private const val KEY_ENABLED = "wake_word_enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun start(context: Context) {
        save(context, true)
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, WakeWordService::class.java),
        )
    }

    fun stop(context: Context) {
        save(context, false)
        context.applicationContext.stopService(
            Intent(context.applicationContext, WakeWordService::class.java),
        )
    }

     fun save(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}