package com.example.vani.ui.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * WhatsApp by voice. Messages and calls ALWAYS ask for confirmation first.
 * Main way: find the person in your phone contacts and open their chat with the message already typed.
 * Fallback: use WhatsApp's own search through the accessibility service.
 */
object WhatsAppActions {

    private val PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun open(context: Context): CommandResult {
        for (pkg in PACKAGES) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            if (startSafely(context, intent)) return ok("Okay, opening WhatsApp.")
        }
        return fail("Sorry, I couldn't open WhatsApp. Is it installed?")
    }

    fun search(context: Context, contact: String): CommandResult {
        val service = VaniAccessibilityService.instance ?: return needAccessibility(context, "search WhatsApp")
        if (!service.openWhatsApp()) return fail("Sorry, I couldn't open WhatsApp.")
        if (!service.startWhatsAppSearch()) return fail("I opened WhatsApp, but I couldn't find its search button.")
        if (!service.setWhatsAppSearchText(contact)) return fail("I couldn't type $contact into WhatsApp search.")
        return ok("Okay, showing WhatsApp results for $contact.")
    }

    fun message(context: Context, spoken: String, text: String): CommandResult {
        val service = VaniAccessibilityService.instance
            ?: return needAccessibility(context, "send WhatsApp messages")

        val matches = ContactsHelper.find(context, spoken)
        return when {
            matches.size == 1 -> openChatAndAsk(context, service, matches[0], text, call = false)
            matches.size > 1 -> PendingAction.askWhich("I found ${matches.size} contacts.", matches.map { it.name }) { n ->
                openChatAndAsk(context, service, matches[n - 1], text, call = false)
            }
            else -> viaWhatsAppSearch(service, spoken, text, call = false)
        }
    }

    fun call(context: Context, spoken: String): CommandResult {
        val service = VaniAccessibilityService.instance
            ?: return needAccessibility(context, "call on WhatsApp")

        val matches = ContactsHelper.find(context, spoken)
        return when {
            matches.size == 1 -> openChatAndAsk(context, service, matches[0], null, call = true)
            matches.size > 1 -> PendingAction.askWhich("I found ${matches.size} contacts.", matches.map { it.name }) { n ->
                openChatAndAsk(context, service, matches[n - 1], null, call = true)
            }
            else -> viaWhatsAppSearch(service, spoken, null, call = true)
        }
    }

    // ---------- main way: contacts + wa.me link ----------

    private fun openChatAndAsk(
        context: Context,
        service: VaniAccessibilityService,
        contact: Contact,
        text: String?,
        call: Boolean,
    ): CommandResult {
        if (!openChat(context, contact.number, text)) return fail("Sorry, I couldn't open WhatsApp. Is it installed?")
        if (!service.waitForWhatsApp()) return fail("I tried to open the chat with ${contact.name}, but WhatsApp didn't open.")
        return afterChatOpen(service, contact.name, text, call)
    }

    private fun openChat(context: Context, number: String, text: String?): Boolean {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val country = telephony?.simCountryIso?.takeIf { it.isNotBlank() } ?: Locale.getDefault().country
        val international = PhoneNumberUtils.formatNumberToE164(number, country.uppercase())
        val digits = (international ?: number).filter { it.isDigit() }

        val query = if (text.isNullOrBlank()) "" else "?text=" + Uri.encode(text)
        val uri = Uri.parse("https://wa.me/$digits$query")
        return PACKAGES.any { pkg -> startSafely(context, Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)) }
    }

    // ---------- fallback: WhatsApp's own search ----------

    private fun viaWhatsAppSearch(
        service: VaniAccessibilityService,
        spoken: String,
        text: String?,
        call: Boolean,
    ): CommandResult {
        if (!service.openWhatsApp()) return fail("Sorry, I couldn't open WhatsApp.")
        if (!service.startWhatsAppSearch()) return fail("I opened WhatsApp, but I couldn't find its search button.")
        if (!service.setWhatsAppSearchText(spoken)) return fail("I couldn't type $spoken into WhatsApp search.")

        val names = service.getWhatsAppContactMatches(spoken)
        return when {
            names.isEmpty() -> fail("Sorry, I couldn't find $spoken in WhatsApp.")
            names.size == 1 -> openFound(service, names[0], text, call)
            else -> PendingAction.askWhich("I found ${names.size} matches in WhatsApp.", names) { n ->
                openFound(service, names[n - 1], text, call)
            }
        }
    }

    private fun openFound(service: VaniAccessibilityService, name: String, text: String?, call: Boolean): CommandResult {
        if (!service.openWhatsAppContact(name)) return fail("Sorry, I couldn't open the chat with $name.")
        return afterChatOpen(service, name, text, call)
    }

    // ---------- shared: the chat is open ----------

    private fun afterChatOpen(
        service: VaniAccessibilityService,
        name: String,
        text: String?,
        call: Boolean,
    ): CommandResult {
        if (text != null) {
            if (!service.prepareWhatsAppMessage(text)) {
                return fail("I opened the chat with $name, but I couldn't type the message.")
            }
            return PendingAction.askConfirm("I've typed \"$text\" to $name. Should I send it?", "Send") {
                if (service.confirmAndSendWhatsAppMessage()) ok("Okay, the message to $name is sent.")
                else fail("Sorry, I couldn't press send. Please tap the send button yourself.")
            }
        }
        if (call) {
            return PendingAction.askConfirm("I opened the chat with $name. Should I call them on WhatsApp?", "Call") {
                if (service.clickWhatsAppVoiceCall()) ok("Okay, calling $name on WhatsApp.")
                else fail("Sorry, I couldn't find the call button in the chat with $name.")
            }
        }
        return ok("Okay, I opened the chat with $name.")
    }
}