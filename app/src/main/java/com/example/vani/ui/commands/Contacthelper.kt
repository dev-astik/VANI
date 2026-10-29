package com.example.vani.ui.commands

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat

data class Contact(val id: Long, val lookupKey: String, val name: String, val number: String)

object ContactsHelper {

    /**
     * Nicknames: what you SAY  ->  a part of the name saved in your contacts.
     * Example:  "big brother" to "rahul"
     */
    val NICKNAMES: Map<String, String> = mapOf(
        // "big brother" to "rahul",
    )

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED

    private fun key(text: String) =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun score(name: String, query: String, queryWords: List<String>): Int {
        val nameWords = name.split(" ")
        return when {
            name == query -> 5
            name.startsWith("$query ") || query in nameWords -> 4
            name.contains(query) -> 3
            queryWords.all { w -> nameWords.any { it.startsWith(w) } } -> 3
            queryWords.any { w ->
                w.length >= 4 && nameWords.any { n -> n.length >= 4 && TextNormalizer.editDistance(w, n) <= 1 }
            } -> 2
            else -> 0
        }
    }

    /** Best matching people (one entry per person, one phone number each), at most five. */
    fun find(context: Context, spoken: String): List<Contact> {
        if (!hasPermission(context)) return emptyList()
        val said = key(spoken)
        val query = key(NICKNAMES[said] ?: said)
        if (query.isBlank()) return emptyList()
        val queryWords = query.split(" ")

        val best = HashMap<Long, Pair<Int, Contact>>()
        val bestRank = HashMap<Long, Int>()

        val cursor = context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.LOOKUP_KEY, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.IS_PRIMARY, Phone.TYPE),
            null, null, null,
        ) ?: return emptyList()

        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val lookup = c.getString(1) ?: continue
                val name = c.getString(2) ?: continue
                val number = c.getString(3) ?: continue
                val score = score(key(name), query, queryWords)
                if (score == 0) continue

                // when a person has several numbers prefer the primary one, then a mobile
                val rank = (if (c.getInt(4) != 0) 2 else 0) + (if (c.getInt(5) == Phone.TYPE_MOBILE) 1 else 0)
                if (best[id] == null || rank > (bestRank[id] ?: -1)) {
                    best[id] = score to Contact(id, lookup, name, number)
                    bestRank[id] = rank
                }
            }
        }

        val sorted = best.values.sortedWith(
            compareByDescending<Pair<Int, Contact>> { it.first }.thenBy { it.second.name }
        )
        val top = sorted.firstOrNull()?.first ?: return emptyList()
        return sorted.filter { it.first == top }.map { it.second }.take(5)
    }

    fun openCard(context: Context, contact: Contact): Boolean {
        val uri = ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
        return startSafely(context, Intent(Intent.ACTION_VIEW, uri))
    }
}