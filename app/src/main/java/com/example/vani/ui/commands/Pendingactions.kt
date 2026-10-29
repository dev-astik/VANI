package com.example.vani.ui.commands

import android.content.Context

/**
 * Remembers the question VANI just asked ("Should I send it?" / "Which one?") for 30 seconds,
 * and understands the answer: yes, cancel, or a number.
 */
object PendingAction {

    private const val LIFETIME_MS = 30_000L

    private class Item(
        val expiresAt: Long,
        val options: List<ReplyOption>,
        val choiceCount: Int,
        val onYes: ((Context) -> CommandResult)?,
        val onPick: ((Int) -> CommandResult)?,
    )

    @Volatile
    private var item: Item? = null

    private val YES = Regex(
        "^(?:yes|yeah|yep|yup|sure|ok|okay|confirm|go ahead|do it|send|send it|send message|" +
                "send now|call|call now|call him|call her|call them|" +
                "yes (?:please|send|send it|call|call now|do it|go ahead))$"
    )
    private val NO = Regex(
        "^(?:no|nope|nah|cancel|stop|abort|never mind|nevermind|dont|don t|do not|" +
                "dont send|don t send|do not send|dont call|don t call|cancel call|cancel message|cancel it)$"
    )
    private val PICK = Regex(
        "^(?:number |option |choice )?(one|two|three|four|five|first|second|third|fourth|fifth|1|2|3|4|5)$"
    )

    /** Asks a yes/cancel question. [onYes] runs when the user confirms. */
    fun askConfirm(prompt: String, yesLabel: String, onYes: (Context) -> CommandResult): CommandResult {
        val options = listOf(ReplyOption(yesLabel, "yes"), ReplyOption("Cancel", "cancel"))
        item = Item(System.currentTimeMillis() + LIFETIME_MS, options, 0, onYes, null)
        return ask(prompt, options)
    }

    /** Asks "which one?" with up to five numbered choices. [onPick] gets 1..5. */
    fun askWhich(question: String, labels: List<String>, onPick: (Int) -> CommandResult): CommandResult {
        val shown = labels.take(5)
        val options = shown.mapIndexed { index, label -> ReplyOption(label, "${index + 1}") }
        val spoken = shown.mapIndexed { index, label -> "${index + 1}, $label" }.joinToString(". ")
        item = Item(System.currentTimeMillis() + LIFETIME_MS, options, shown.size, null, onPick)
        return ask("$question $spoken. Say a number.", options)
    }

    fun clear() {
        item = null
    }

    /**
     * Returns a result when [text] answers the pending question.
     * Returns null when nothing is pending or the user said something else (the old question is forgotten).
     */
    fun answer(context: Context, text: String): CommandResult? {
        val current = item ?: return null
        if (System.currentTimeMillis() > current.expiresAt) {
            item = null
            return null
        }

        if (NO.matches(text)) {
            item = null
            return ok("Okay, I've cancelled that.")
        }

        if (YES.matches(text)) {
            val onYes = current.onYes
            if (onYes != null) {
                item = null
                return onYes(context)
            }
            return ask("Please say the number of the one you want.", current.options)
        }

        val picked = PICK.matchEntire(text)?.groupValues?.get(1)?.let { NumberWords.ordinal(it) }
        if (picked != null) {
            val onPick = current.onPick
            if (onPick != null && picked in 1..current.choiceCount) {
                item = null
                return onPick(picked)
            }
            return if (onPick != null) {
                ask("Please choose a number from 1 to ${current.choiceCount}.", current.options)
            } else {
                ask("Please say yes to continue, or cancel.", current.options)
            }
        }

        item = null // something else was said: forget the old question
        return null
    }
}