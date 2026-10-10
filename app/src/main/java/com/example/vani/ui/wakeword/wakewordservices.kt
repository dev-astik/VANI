package com.example.vani.ui.wakeword
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/**
 * Listens to the microphone and calls [onDetected] when "Hey VANI" is heard.
 *
 * When the wake word is detected the microphone is RELEASED first, then [onDetected] is called
 * (on the detector thread), so the speech recognizer can use the mic right away.
 * Call [start] again when you want to resume listening.
 */
class WakeWordDetector(
    private val context: Context,
    private val onDetected: () -> Unit,
) {
    private val sampleRate = 16000

    private var spotter: KeywordSpotter? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread({ runLoop() }, "vani-wake-word").also { it.start() }
    }

    /** Stops listening and frees the model. Call when the service is destroyed. */
    fun release() {
        running = false
        thread?.join(1500)
        thread = null
        spotter?.release()
        spotter = null
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested before the service starts
    private fun runLoop() {
        val kws = spotter ?: createSpotter().also { spotter = it }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, sampleRate), // buffer size in bytes
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            running = false
            return
        }

        val stream = kws.createStream()
        val shorts = ShortArray(sampleRate / 10) // 100 ms chunks
        var detected = false

        try {
            record.startRecording()
            while (running && !detected) {
                val n = record.read(shorts, 0, shorts.size)
                if (n < 0) break
                if (n == 0) continue

                val samples = FloatArray(n) { shorts[it] / 32768f }
                stream.acceptWaveform(samples, sampleRate)

                while (kws.isReady(stream)) {
                    kws.decode(stream)
                    if (kws.getResult(stream).keyword.isNotBlank()) {
                        kws.reset(stream)
                        detected = true
                        break
                    }
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            stream.release()
            running = false
        }

        if (detected) onDetected()
    }

    private fun createSpotter(): KeywordSpotter {
        // Folder inside app/src/main/assets/ (see setup steps)
        val dir = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"

        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$dir/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    decoder = "$dir/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    joiner = "$dir/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                ),
                tokens = "$dir/tokens.txt",
                modelType = "zipformer2",
                numThreads = 1,
            ),
            keywordsFile = "$dir/keywords.txt",
            keywordsScore = 1.5f,      // higher = easier to trigger
            keywordsThreshold = 0.25f, // lower = easier to trigger
        )
        return KeywordSpotter(assetManager = context.assets, config = config)
    }
}

