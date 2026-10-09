package com.example.vani.ui.overlay
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Thin wrapper around Android's SpeechRecognizer.
 * Must be created and used on the main thread.
 */
class VoiceListener(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onFailure: (Int) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        stop()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFailure(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(error: Int) = onFailure(error)

                override fun onPartialResults(partialResults: Bundle?) {
                    firstResult(partialResults)?.let(onPartial)
                }

                override fun onResults(results: Bundle?) {
                    onFinal(firstResult(results).orEmpty())
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            sr.startListening(intent)
        }
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}