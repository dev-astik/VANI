package com.example.vani.ui.commands

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Lets VANI do things Android only allows through accessibility:
 * go back, scroll, recent apps, notification panel, quick settings.
 * It does NOT read the screen. The user turns it on in Settings > Accessibility.
 */
class VaniAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // not used: VANI does not read the screen
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun showRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun showNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun showQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    /**
     * Swipes the middle of the screen. "Scroll down" moves your finger UP, like a real scroll.
     * The swipe stays in the upper part of the screen so it does not touch the listening card.
     */
    fun scroll(down: Boolean): Boolean {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val height = metrics.heightPixels.toFloat()

        val startY = if (down) height * 0.50f else height * 0.20f
        val endY = if (down) height * 0.18f else height * 0.50f

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        // Set while the service is enabled; cleared when it stops.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        var instance: VaniAccessibilityService? = null
    }
}