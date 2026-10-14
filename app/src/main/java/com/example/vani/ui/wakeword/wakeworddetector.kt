package com.example.vani.ui.wakeword

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlin.math.sqrt

private const val TAG = "VaniWake"

/**
 * Listens to the microphone and calls [onDetected] when a keyword from keywords.txt is heard.
 * The mic is RELEASED before [onDetected] is called. Call [start] again to resume.
 * Every step is logged (Logcat tag "VaniWake") and shown in [WakeWordStatus].
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
        thread = Thread({ safeRun() }, "vani-wake-word").also { it.start() }
    }


    @Synchronized
    fun release() {
        running = false

        val worker = thread
        if (worker != null && worker !== Thread.currentThread()) {
            worker.join(3000)
        }

        // Release the model only after the worker exits.
        if (worker == null || !worker.isAlive) {
            thread = null
            runCatching {
                spotter?.release()
            }
            spotter = null
        }
    }


    private fun safeRun() {
        try {
            runLoop()
        } catch (t: Throwable) {
            Log.e(TAG, "Detector crashed", t)
            WakeWordStatus.text = "Error: ${t.javaClass.simpleName}: ${t.message}"
            running = false
        }
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested before the service starts
    private fun runLoop() {
        if (spotter == null) {
            WakeWordStatus.text = "Loading model…"
            Log.d(TAG, "Loading model")
            spotter = createSpotter()
            Log.d(TAG, "Model loaded")
        }
        val kws = spotter!!

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, sampleRate),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            WakeWordStatus.text = "Error: microphone unavailable (permission, or another app is using it)"
            Log.e(TAG, "AudioRecord not initialized")
            running = false
            return
        }

        val stream = kws.createStream()
        val shorts = ShortArray(sampleRate / 10) // 100 ms
        var heard = ""
        var chunks = 0
        var loudest = 0f

        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                WakeWordStatus.text = "Error: microphone could not start recording"
                Log.e(TAG, "AudioRecord did not start")
                return
            }
            WakeWordStatus.text = "Listening…"
            Log.d(TAG, "Listening started")

            while (running && heard.isEmpty()) {
                val n = record.read(shorts, 0, shorts.size)
                if (n < 0) {
                    WakeWordStatus.text = "Error: microphone read failed ($n)"
                    Log.e(TAG, "AudioRecord.read returned $n")
                    break
                }
                if (n == 0) continue

                // mic level (RMS) for the live bar in Settings
                var sum = 0.0
                for (i in 0 until n) {
                    val v = shorts[i].toDouble()
                    sum += v * v
                }
                val level = (sqrt(sum / n) / 32768.0 * 12.0).coerceIn(0.0, 1.0).toFloat()
                WakeWordStatus.micLevel = level
                if (level > loudest) loudest = level

                chunks++
                if (chunks == 30 && loudest == 0f) {
                    WakeWordStatus.text = "Error: microphone returns only silence"
                    Log.e(TAG, "Mic returned silence for 3 seconds")
                }
                if (chunks % 100 == 0) Log.d(TAG, "alive, level=$level")

                val samples = FloatArray(n) { shorts[it] / 32768f }
                stream.acceptWaveform(samples, sampleRate)

                while (kws.isReady(stream)) {
                    kws.decode(stream)
                    val keyword = kws.getResult(stream).keyword
                    if (keyword.isNotBlank()) {
                        heard = keyword
                        kws.reset(stream)
                        break
                    }
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            stream.release()
            WakeWordStatus.micLevel = 0f
            running = false
        }

        if (heard.isNotEmpty()) {
            Log.d(TAG, "Detected keyword: $heard")
            WakeWordStatus.lastHeard = heard
            WakeWordStatus.text = "Heard “$heard”"
            onDetected()
        }
    }

    private fun createSpotter(): KeywordSpotter {
        // Folder inside app/src/main/assets/
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
            keywordsScore = 1.5f,
            keywordsThreshold = 0.20f,
        )
        return KeywordSpotter(assetManager = context.assets, config = config)
    }
}