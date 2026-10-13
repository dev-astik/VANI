package com.example.vani.ui.wakeword

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/** Starts / stops the wake word service and remembers whether the user turned it on. */
object WakeWordController {
    private const val PREFS = "vani_prefs"
    private const val KEY_ENABLED = "wake_word_enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)   // was false

    fun start(context: Context) {
        save(context, true)
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, WakeWordService::class.java),
        )
    }

    fun stop(context: Context) {
        android.util.Log.d("VaniWake", "WakeWordController.start called")
        save(context, false)
        context.applicationContext.stopService(
            Intent(context.applicationContext, WakeWordService::class.java),
        )
    }

    private fun save(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_ENABLED, enabled) }
    }
}