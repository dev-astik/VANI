    package com.example.vani.ui.wakeword

    import android.annotation.SuppressLint
    import android.app.NotificationChannel
    import android.app.NotificationManager
    import android.app.PendingIntent
    import android.app.Service
    import android.content.Intent
    import android.content.pm.ServiceInfo
    import android.os.Build
    import android.os.Handler
    import android.os.IBinder
    import android.os.Looper
    import android.util.Log
    import androidx.core.app.NotificationCompat
    import com.example.vani.MainActivity
    import com.example.vani.ui.overlay.OverlayManager

    /**
     * Foreground service (microphone type) that keeps listening for "Hey VANI".
     * Detected -> mic released -> listening card shown -> when the card closes, listen again.
     */
    class WakeWordService : Service() {

        private val main = Handler(Looper.getMainLooper())
        private var detector: WakeWordDetector? = null

        override fun onCreate() {
            super.onCreate()
            WakeWordStatus.text = "Starting service…"
            Log.d("VaniWake", "Service onCreate")
            startAsForeground()

            val d = WakeWordDetector(applicationContext) { main.post { onWakeWord() } }
            detector = d

            // when the listening card closes, listen for the wake word again
            OverlayManager.onDismissed = { main.postDelayed({ detector?.start() }, 600) }

            d.start()
        }

        private fun onWakeWord() {
            OverlayManager.show(applicationContext)
            // if the card could not be shown (e.g. overlay permission missing), keep listening
            if (!OverlayManager.isShowing) {
                WakeWordStatus.text = "Heard it, but the card can't show: allow “Display over other apps”"
                detector?.start()
            }
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

        override fun onBind(intent: Intent?): IBinder? = null

        override fun onDestroy() {
            main.removeCallbacksAndMessages(null)
            OverlayManager.onDismissed = null
            OverlayManager.hide()
            detector?.release()
            detector = null
            WakeWordStatus.text = "Off"
            WakeWordStatus.micLevel = 0f
            Log.d("VaniWake", "Service onDestroy")
            super.onDestroy()
        }

        @SuppressLint("InlinedApi")
        private fun startAsForeground() {
            val channelId = "vani_wake_word"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId, "Hey VANI listening", NotificationManager.IMPORTANCE_LOW,
                )
                getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }

            val openApp = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("VANI is listening")
                .setContentText("Say “Hey VANI” to start")
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, notification)
            }
        }
    }