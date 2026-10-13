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
import com.example.vani.ui.overlay.VoiceListener

/**
 * Displays the floating VANI listening overlay and runs the spoken command.
 * Used by the mic button and by the "Hey VANI" wake word service.
 */
object OverlayManager {

    private const val TAG = "VaniOverlay"

    /** Runs the recognized text. Default: CommandProcessor (torch, volume, open app, ...). */
    var commandHandler: (Context, String) -> CommandResult = { context, text ->
        CommandProcessor.handle(context, text)
    }

    /** Called after the overlay has been removed (the wake word service uses it to listen again). */
    var onDismissed: (() -> Unit)? = null

    val isShowing: Boolean get() = composeView != null

    private val handler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    // VoiceListener only keeps the application context, so this is not a real leak
    @SuppressLint("StaticFieldLeak")
    private var voiceListener: VoiceListener? = null

    private var uiState by mutableStateOf(ListeningUiState())

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context) {
        // WindowManager and Compose UI operations belong on the main thread.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { show(context.applicationContext) }
            return
        }

        // If the overlay already exists, restart listening.
        if (composeView != null) {
            startListening()
            return
        }

        val appContext = context.applicationContext
        if (!canDrawOverlays(appContext)) return

        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val owner = OverlayLifecycleOwner()
        owner.start()

        val view = ComposeView(appContext)
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)

        view.setContent {
            VaniTheme(darkTheme = isSystemInDarkTheme()) {
                ListeningOverlay(
                    state = uiState,
                    onClose = { hide() },
                    onRetry = { startListening() },
                )
            }
        }

        try {
            wm.addView(view, layoutParams())
        } catch (error: Exception) {
            Log.w(TAG, "Could not add the overlay window", error)
            owner.destroy()
            return
        }

        // Store references only after the view is attached.
        windowManager = wm
        composeView = view
        lifecycleOwner = owner

        voiceListener = VoiceListener(
            context = appContext,
            onPartial = { text ->
                handler.post {
                    if (composeView != null) {
                        uiState = ListeningUiState(ListeningPhase.Listening, text)
                    }
                }
            },
            onFinal = { text ->
                handler.post {
                    if (composeView != null) handleFinal(appContext, text)
                }
            },
            onFailure = {
                handler.post {
                    if (composeView != null) fail()
                }
            },
        )

        startListening()
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { hide() }
            return
        }

        val wasShowing = composeView != null

        // Cancel pending hide and processing callbacks.
        handler.removeCallbacksAndMessages(null)

        voiceListener?.stop()
        voiceListener = null

        val view = composeView
        val wm = windowManager

        composeView = null
        windowManager = null

        if (view != null && wm != null) {
            try {
                wm.removeView(view)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Overlay was already removed", error)
            } catch (error: Exception) {
                Log.w(TAG, "Could not remove the overlay", error)
            }
        }

        lifecycleOwner?.destroy()
        lifecycleOwner = null

        uiState = ListeningUiState()

        if (wasShowing) onDismissed?.invoke()
    }

    private fun startListening() {
        handler.removeCallbacksAndMessages(null)

        if (composeView == null || voiceListener == null) return

        uiState = ListeningUiState(ListeningPhase.Listening)
        voiceListener?.start()
    }

    private fun handleFinal(context: Context, text: String) {
        if (text.isBlank()) {
            fail()
            return
        }

        uiState = ListeningUiState(ListeningPhase.Processing, text)

        handler.postDelayed({
            if (composeView == null) return@postDelayed

            val result = try {
                commandHandler(context, text)
            } catch (error: Exception) {
                Log.e(TAG, "Command failed", error)
                CommandResult(false, "Something went wrong")
            }

            uiState = ListeningUiState(
                phase = if (result.success) ListeningPhase.Success else ListeningPhase.Error,
                transcript = text,
                message = result.message,
            )

            handler.postDelayed({ hide() }, if (result.success) 1500L else 4000L)
        }, 600L)
    }

    private fun fail() {
        uiState = ListeningUiState(ListeningPhase.Error)
        handler.postDelayed({ hide() }, 4000L)
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
        }
    }
}

/**
 * Provides lifecycle, saved-state and ViewModel owners
 * for a ComposeView displayed outside an Activity.
 */
private class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
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
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun start() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        store.clear()
    }
}