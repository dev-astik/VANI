package com.example.vani.ui.commands

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Finding contacts and calling them (always asks first). */
object PhoneActions {

    private fun needContactsPermission(context: Context): CommandResult {
        startSafely(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
        return fail("I need permission to read your contacts. I've opened VANI's settings. Allow Contacts, then ask me again.")
    }

    fun findContact(context: Context, spoken: String): CommandResult {
        if (!ContactsHelper.hasPermission(context)) return needContactsPermission(context)

        val matches = ContactsHelper.find(context, spoken)
        return when {
            matches.isEmpty() -> fail("Sorry, I couldn't find $spoken in your contacts.")
            matches.size == 1 -> showContact(context, matches[0])
            else -> PendingAction.askWhich("I found ${matches.size} contacts.", matches.map { it.name }) { n ->
                showContact(context, matches[n - 1])
            }
        }
    }

    private fun showContact(context: Context, contact: Contact): CommandResult =
        if (ContactsHelper.openCard(context, contact)) ok("Okay, here is ${contact.name}.")
        else fail("Sorry, I couldn't open ${contact.name}.")

    fun call(context: Context, spoken: String): CommandResult {
        // a spoken phone number: "call 98 76 54 32 10"
        val digits = spoken.filter { it.isDigit() }
        if (digits.length >= 6 && spoken.all { it.isDigit() || it == ' ' }) {
            return confirmCall(spoken.trim(), digits)
        }

        if (!ContactsHelper.hasPermission(context)) return needContactsPermission(context)

        val matches = ContactsHelper.find(context, spoken)
        return when {
            matches.isEmpty() -> fail("Sorry, I couldn't find $spoken in your contacts.")
            matches.size == 1 -> confirmCall(matches[0].name, matches[0].number)
            else -> PendingAction.askWhich("I found ${matches.size} contacts.", matches.map { it.name }) { n ->
                confirmCall(matches[n - 1].name, matches[n - 1].number)
            }
        }
    }

    private fun confirmCall(name: String, number: String): CommandResult =
        PendingAction.askConfirm("Do you want me to call $name?", "Call") { context -> placeCall(context, name, number) }

    private fun placeCall(context: Context, name: String, number: String): CommandResult {
        val uri = Uri.fromParts("tel", number, null)
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED

        return if (canCall && startSafely(context, Intent(Intent.ACTION_CALL, uri))) {
            ok("Okay, calling $name.")
        } else if (startSafely(context, Intent(Intent.ACTION_DIAL, uri))) {
            ok("Okay, I've put $name in the dialer. Tap call to start.")
        } else {
            fail("Sorry, I couldn't start the call.")
        }
    }
}