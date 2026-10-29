package com.example.vani.ui.commands

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/** A button shown on the listening card. Tapping it works like saying [command]. */
data class ReplyOption(val label: String, val command: String)

data class CommandResult(
    val success: Boolean,
    /** Friendly sentence shown on the card and read aloud. */
    val message: String,
    /** True when VANI asked a question and is waiting for the answer. */
    val needsReply: Boolean = false,
    val options: List<ReplyOption> = emptyList(),
)

internal fun ok(message: String) = CommandResult(true, message)

internal fun fail(message: String) = CommandResult(false, message)

internal fun ask(message: String, options: List<ReplyOption>) =
    CommandResult(true, message, needsReply = true, options = options)

/** Starts an activity from anywhere (overlay, service, worker thread). Returns false if it fails. */
internal fun startSafely(context: Context, intent: Intent): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (error: Exception) {
        Log.w("VaniCommands", "Could not start ${intent.action}", error)
        false
    }
}

/** Opens the Accessibility settings and explains why. */
internal fun needAccessibility(context: Context, what: String): CommandResult {
    startSafely(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    return fail(
        "To $what, VANI must be turned on in Accessibility settings. " +
                "I've opened them for you. Turn on VANI, then ask me again."
    )
}