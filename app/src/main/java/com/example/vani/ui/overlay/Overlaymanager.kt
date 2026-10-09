package com.example.vani.ui.overlay
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.example.vani.ui.overlay.ListeningOverlay
import com.example.vani.ui.overlay.ListeningPhase
import com.example.vani.ui.overlay.ListeningUiState
import com.example.vani.ui.theme.VaniTheme
import com.example.vani.voice.VoiceListener

/**
 * Shows / hides the floating "Listening" card on top of any app.
 * Call [show] from the mic button now, and from the wake-word service ("Hey VANI") later.
 */
object OverlayManager {

    /** Plug your real command execution here. Return true if the command worked. */
    var commandHandler: (String) -> Boolean = { true }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var voiceListener: VoiceListener? = null
    private var uiState by mutableStateOf(ListeningUiState())

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context) {
        if (composeView != null) {
            startListening()
            return
        }
        val app = context.applicationContext
        if (!canDrawOverlays(app)) return

        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val owner = OverlayLifecycleOwner().also { it.start() }

        val view = ComposeView(app).apply {
            ViewTreeLifecycleOwner.set(this, owner)
            ViewTreeViewModelStoreOwner.set(this, owner)
            ViewTreeSavedStateRegistryOwner.set(this, owner)
            setContent {
                VaniTheme(darkTheme = isSystemInDarkTheme()) {
                    ListeningOverlay(
                        state = uiState,
                        onClose = { hide() },
                        onRetry = { startListening() },
                    )
                }
            }
        }

        wm.addView(view, layoutParams())
        windowManager = wm
        composeView = view
        lifecycleOwner = owner

        voiceListener = VoiceListener(
            context = app,
            onPartial = { text -> uiState = ListeningUiState(ListeningPhase.Listening, text) },
            onFinal = { text -> handleFinal(text) },
            onFailure = { fail() },
        )
        startListening()
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        voiceListener?.stop()
        voiceListener = null
        composeView?.let { runCatching { windowManager?.removeView(it) } }
        composeView = null
        windowManager = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
    }

    // ---------- internal flow ----------

    private fun startListening() {
        handler.removeCallbacksAndMessages(null)
        uiState = ListeningUiState(ListeningPhase.Listening)
        voiceListener?.start()
    }

    private fun handleFinal(text: String) {
        if (text.isBlank()) {
            fail()
            return
        }
        uiState = ListeningUiState(ListeningPhase.Processing, text)
        handler.postDelayed({
            val ok = commandHandler(text)
            uiState = ListeningUiState(if (ok) ListeningPhase.Success else ListeningPhase.Error, text)
            handler.postDelayed({ hide() }, if (ok) 1500L else 4000L)
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
        ).apply { gravity = Gravity.BOTTOM }
    }
}

/** A ComposeView outside an Activity needs its own lifecycle, saved-state and ViewModel owners. */
private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun start() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}