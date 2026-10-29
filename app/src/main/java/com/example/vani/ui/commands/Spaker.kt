package com.example.vani.ui.commands

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads VANI's replies aloud (can be switched off in Settings).
 * [speak] ALWAYS calls [onDone] on the main thread, also when voice replies are off,
 * so callers can wait for the speech to finish before listening again.
 */
object Speaker {

    private const val PREFS = "vani_prefs"
    private const val KEY_ENABLED = "voice_replies"

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var initializing = false
    private val waiting = ArrayList<Pair<String, () -> Unit>>()
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) stop()
    }

    fun speak(context: Context, text: String, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        if (!isEnabled(app)) {
            // no voice: keep the card up long enough to read the sentence
            main.postDelayed({ onDone() }, (text.length * 45L).coerceIn(1500L, 6000L))
            return
        }
        main.post {
            val engine = tts
            if (engine != null && ready) {
                say(engine, text, onDone)
            } else {
                waiting.add(text to onDone)
                if (!initializing) startEngine(app)
            }
        }
    }

    fun stop() {
        main.post {
            callbacks.clear()
            waiting.clear()
            tts?.stop()
        }
    }

    private fun startEngine(app: Context) {
        initializing = true
        tts = TextToSpeech(app) { status ->
            main.post {
                initializing = false
                val engine = tts
                val queued = ArrayList(waiting)
                waiting.clear()
                if (status == TextToSpeech.SUCCESS && engine != null) {
                    val indian = Locale("en", "IN")
                    engine.language =
                        if (engine.isLanguageAvailable(indian) >= TextToSpeech.LANG_AVAILABLE) indian
                        else Locale.getDefault()
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) = finish(utteranceId)

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) = finish(utteranceId)
                        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
                    })
                    ready = true
                    queued.forEach { (text, done) -> say(engine, text, done) }
                } else {
                    // no voice available on this phone: carry on silently
                    queued.forEach { (_, done) -> done() }
                }
            }
        }
    }

    private fun say(engine: TextToSpeech, text: String, onDone: () -> Unit) {
        val id = UUID.randomUUID().toString()
        callbacks[id] = onDone
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            callbacks.remove(id)?.let { callback -> main.post { callback() } }
        }
    }

    private fun finish(utteranceId: String?) {
        val callback = utteranceId?.let { callbacks.remove(it) } ?: return
        main.post { callback() }
    }
}