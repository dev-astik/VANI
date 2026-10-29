package com.example.vani.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
//import androidx.compose.ui.platform.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.vani.ui.commands.CommandProcessor
import com.example.vani.ui.commands.CommandResult
import com.example.vani.ui.theme.VaniTheme

object OverlayManager {

    private const val TAG = "VaniOverlay"

    private val handler = Handler(Looper.getMainLooper())

    var onDismissed: (() -> Unit)? = null

    val isShowing: Boolean
        get() = composeView != null

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    @SuppressLint("StaticFieldLeak")
    private var voiceListener: VoiceListener? = null

    private var uiState by mutableStateOf(ListeningUiState())

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun show(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post {
                show(context.applicationContext)
            }
            return
        }

        // If already visible, start listening again.
        if (composeView != null) {
            startListening()
            return
        }

        val appContext = context.applicationContext

        if (!canDrawOverlays(appContext)) {
            Log.w(TAG, "Overlay permission has not been granted")
            return
        }

        val wm = appContext.getSystemService(
            Context.WINDOW_SERVICE
        ) as WindowManager

        val owner = OverlayLifecycleOwner()
        owner.start()

        val view = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)

            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )

            setContent {
                VaniTheme(
                    darkTheme = isSystemInDarkTheme()
                ) {
                    ListeningOverlay(
                        state = uiState,
                        onClose = {
                            hide()
                        },
                        onRetry = {
                            startListening()
                        }
                    )
                }
            }
        }

        try {
            wm.addView(view, layoutParams())
        } catch (error: Exception) {
            Log.e(TAG, "Could not add the overlay window", error)
            owner.destroy()
            return
        }

        windowManager = wm
        composeView = view
        lifecycleOwner = owner

        voiceListener = VoiceListener(
            context = appContext,

            onPartial = { text ->
                handler.post {
                    if (composeView != null) {
                        uiState = ListeningUiState(
                            phase = ListeningPhase.Listening,
                            transcript = text
                        )
                    }
                }
            },

            onFinal = { text ->
                handler.post {
                    if (composeView != null) {
                        handleFinal(appContext, text)
                    }
                }
            },

            onFailure = {
                handler.post {
                    if (composeView != null) {
                        fail()
                    }
                }
            }
        )

        startListening()
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post {
                hide()
            }
            return
        }

        val wasShowing = composeView != null

        // Cancel delayed listening, result and dismissal callbacks.
        handler.removeCallbacksAndMessages(null)

        voiceListener?.stop()
        voiceListener = null

        val view = composeView
        val wm = windowManager
        val owner = lifecycleOwner

        // Clear references before removing the window.
        composeView = null
        windowManager = null
        lifecycleOwner = null

        if (view != null && wm != null) {
            try {
                wm.removeView(view)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Overlay was already removed", error)
            } catch (error: Exception) {
                Log.e(TAG, "Could not remove the overlay", error)
            }
        }

        owner?.destroy()

        uiState = ListeningUiState()

        if (wasShowing) {
            onDismissed?.invoke()
        }
    }

    private fun startListening() {
        handler.removeCallbacksAndMessages(null)

        if (composeView == null || voiceListener == null) {
            return
        }

        uiState = ListeningUiState(
            phase = ListeningPhase.Listening
        )

        try {
            voiceListener?.start()
        } catch (error: Exception) {
            Log.e(TAG, "Could not start voice recognition", error)
            fail()
        }
    }

    private fun handleFinal(context: Context, text: String) {
        if (text.isBlank()) {
            fail()
            return
        }

        // Show the recognized command while it is being processed.
        uiState = ListeningUiState(
            phase = ListeningPhase.Processing,
            transcript = text
        )

        handler.postDelayed({
            if (composeView == null) {
                return@postDelayed
            }

            // Run the command on CommandProcessor's background executor.
            CommandProcessor.handleAsync(context, text) { result ->
                if (composeView == null) {
                    return@handleAsync
                }

                showResult(text, result)
            }
        }, 300L)
    }

    private fun showResult(
        text: String,
        result: CommandResult
    ) {
        if (composeView == null) {
            return
        }

        uiState = ListeningUiState(
            phase = if (result.success) {
                ListeningPhase.Success
            } else {
                ListeningPhase.Error
            },
            transcript = text,
            message = result.message
        )

        /*
         * CommandProcessor handles follow-up answers through
         * PendingAction.answer(). Keep the overlay open and listen
         * again when WhatsApp requires another spoken response.
         */
        val waitingForWhatsApp = result.success && (
                result.message.contains(
                    "I'll prepare the message",
                    ignoreCase = true
                ) ||
                        result.message.contains(
                            "I'll ask before calling",
                            ignoreCase = true
                        )
                )

        if (waitingForWhatsApp) {
            handler.postDelayed({
                if (composeView != null) {
                    startListening()
                }
            }, 1200L)

            return
        }

        // Ordinary successful commands disappear sooner than errors.
        handler.postDelayed(
            {
                hide()
            },
            if (result.success) 1800L else 4000L
        )
    }

    private fun fail() {
        if (composeView == null) {
            return
        }

        uiState = ListeningUiState(
            phase = ListeningPhase.Error,
            message = "Sorry, I couldn't hear you clearly. Please try again."
        )

        handler.postDelayed({
            hide()
        }, 4000L)
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val type = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
    }
}

private class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    private val savedStateController =
        SavedStateRegistryController.create(this)

    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)

        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_CREATE
        )
    }

    fun start() {
        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_START
        )

        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_RESUME
        )
    }

    fun destroy() {
        if (
            lifecycleRegistry.currentState.isAtLeast(
                Lifecycle.State.RESUMED
            )
        ) {
            lifecycleRegistry.handleLifecycleEvent(
                Lifecycle.Event.ON_PAUSE
            )
        }

        if (
            lifecycleRegistry.currentState.isAtLeast(
                Lifecycle.State.STARTED
            )
        ) {
            lifecycleRegistry.handleLifecycleEvent(
                Lifecycle.Event.ON_STOP
            )
        }

        if (
            lifecycleRegistry.currentState !=
            Lifecycle.State.DESTROYED
        ) {
            lifecycleRegistry.handleLifecycleEvent(
                Lifecycle.Event.ON_DESTROY
            )
        }

        store.clear()
    }
}
