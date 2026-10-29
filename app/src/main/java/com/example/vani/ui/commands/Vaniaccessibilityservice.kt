package com.example.vani.ui.commands

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import java.util.Locale

/** Result of trying to flip the first switch on a Settings screen. */
enum class SwitchOutcome { CHANGED, ALREADY_SET, NOT_FOUND }

class VaniAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // UI inspection is performed only when VANI requests it.
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

    fun goBack(): Boolean =
        performGlobalAction(GLOBAL_ACTION_BACK)

    fun showRecents(): Boolean =
        performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun showNotifications(): Boolean =
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun showQuickSettings(): Boolean =
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

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
            .addStroke(
                GestureDescription.StrokeDescription(path, 0, 350)
            )
            .build()

        return dispatchGesture(gesture, null, null)
    }

    /**
     * Finds visible accessibility nodes whose text or
     * content description contains the requested text.
     */
    fun findNodesByText(
        text: String
    ): List<AccessibilityNodeInfo> {
        if (text.isBlank()) return emptyList()

        return rootInActiveWindow
            ?.findAccessibilityNodeInfosByText(text)
            ?.toList()
            ?: emptyList()
    }

    /**
     * Clicks the first matching node, or its clickable parent.
     */
    fun clickByText(text: String): Boolean {
        val nodes = findNodesByText(text)

        try {
            for (node in nodes) {
                var current: AccessibilityNodeInfo? = node
                var depth = 0

                while (current != null && depth < 8) {
                    if (current.isClickable &&
                        current.isEnabled &&
                        current.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )
                    ) {
                        return true
                    }

                    val parent = current.parent

                    if (current !== node) {
                        current.recycle()
                    }

                    current = parent
                    depth++
                }
            }
        } finally {
            nodes.forEach {
                try {
                    it.recycle()
                } catch (_: Exception) {
                    // A node may already have been recycled.
                }
            }
        }

        return false
    }

    /**
     * Returns a readable snapshot of the active UI tree.
     * Useful for debugging YouTube search results.
     */
    fun getVisibleUiText(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val output = mutableListOf<String>()

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 20) return

            val label = node.text?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()
                    ?.takeIf { it.isNotBlank() }

            if (label != null) {
                output.add(label)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    visit(child, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }

        try {
            visit(root, 0)
        } finally {
            root.recycle()
        }

        return output.distinct()
    }


    fun clickYouTubeVideo(position: Int): Boolean {
        if (position < 1) {
            Log.d("VANI_UI", "Invalid video position: $position")
            return false
        }

        val root = rootInActiveWindow ?: run {
            Log.d("VANI_UI", "No active window")
            return false
        }

        data class VideoCandidate(
            val node: AccessibilityNodeInfo,
            val label: String
        )

        val candidates = mutableListOf<VideoCandidate>()

        try {
            fun collect(node: AccessibilityNodeInfo) {
                val text = node.text?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()
                val label = "$text $desc".trim()

                val isVideoResult =
                    label.contains("play video", ignoreCase = true) ||
                            label.contains("play Short", ignoreCase = true)

                if (isVideoResult && node.isVisibleToUser) {
                    // Keep a copy because the traversal recycles child nodes.
                    candidates.add(
                        VideoCandidate(
                            AccessibilityNodeInfo.obtain(node),
                            label
                        )
                    )
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    try {
                        collect(child)
                    } finally {
                        child.recycle()
                    }
                }
            }

            collect(root)

            // Remove duplicate labels while preserving screen traversal order.
            val uniqueCandidates = candidates.distinctBy { it.label }

            Log.d(
                "VANI_UI",
                "Found ${uniqueCandidates.size} video candidates"
            )

            uniqueCandidates.forEachIndexed { index, candidate ->
                Log.d(
                    "VANI_UI",
                    "Video ${index + 1}: ${candidate.label}"
                )
            }

            if (position > uniqueCandidates.size) {
                Log.d("VANI_UI", "Video $position was not found")
                return false
            }

            val target = uniqueCandidates[position - 1].node
            var current: AccessibilityNodeInfo? =
                AccessibilityNodeInfo.obtain(target)

            var depth = 0

            while (current != null && depth < 10) {
                if (current.isClickable && current.isEnabled) {
                    val clicked = current.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )

                    Log.d(
                        "VANI_UI",
                        "Video $position click attempted: $clicked"
                    )

                    if (clicked) return true
                }

                val parent = current.parent
                current.recycle()
                current = parent
                depth++
            }

            current?.recycle()

            Log.d("VANI_UI", "Could not click video $position")
            return false

        } finally {
            candidates.forEach {
                try {
                    it.node.recycle()
                } catch (_: Exception) {
                    // Ignore already-recycled nodes.
                }
            }
            root.recycle()
        }
    }




    fun verifyYouTubePlayback() {
        val root = rootInActiveWindow

        if (root == null) {
            Log.d("VANI_UI", "Playback check: no active window")
            return
        }

        try {
            val labels = mutableListOf<String>()

            fun visit(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 15) return

                val text = node.text?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()

                if (text.isNotBlank()) labels.add(text)
                if (desc.isNotBlank()) labels.add(desc)

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    try {
                        visit(child, depth + 1)
                    } finally {
                        child.recycle()
                    }
                }
            }

            visit(root, 0)

            Log.d("VANI_UI", "----- PLAYBACK CHECK -----")
            labels.distinct().forEach {
                Log.d("VANI_UI", it)
            }
            Log.d("VANI_UI", "----- CHECK END -----")
        } finally {
            root.recycle()
        }
    }

// ---------- WhatsApp control ----------

    private val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")

    /**
     * Opens WhatsApp (or WhatsApp Business) if installed.
     */
    fun openWhatsApp(): Boolean {
        for (pkg in whatsappPackages) {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
                return true
            } catch (error: Exception) {
                Log.w("VANI_UI", "Could not open $pkg", error)
            }
        }
        return false
    }

    /**
     * Waits (up to about 6 seconds) until WhatsApp is on screen, then gives it a moment to draw.
     * Call from a worker thread, never from the main thread.
     */
    fun waitForWhatsApp(): Boolean {
        val deadline = System.currentTimeMillis() + 6000
        while (System.currentTimeMillis() < deadline) {
            if (isWhatsAppVisible()) {
                Thread.sleep(900)
                return true
            }
            Thread.sleep(250)
        }
        return false
    }

    /**
     * Opens WhatsApp's search interface.
     * Search results may take a moment to appear.
     */
    fun startWhatsAppSearch(): Boolean {
        if (!isWhatsAppVisible()) {
            Log.w("VANI_WHATSAPP", "WhatsApp is not the active screen")
            return false
        }

        val root = rootInActiveWindow ?: return false

        try {
            val searchLabels = listOf(
                "Search",
                "Search chats",
                "Search contacts"
            )

            for (label in searchLabels) {
                val nodes = root.findAccessibilityNodeInfosByText(label)
                    ?: continue

                try {
                    for (node in nodes) {
                        var current: AccessibilityNodeInfo? =
                            AccessibilityNodeInfo.obtain(node)

                        var depth = 0

                        while (current != null && depth < 8) {
                            if (current.isClickable && current.isEnabled) {
                                val clicked = current.performAction(
                                    AccessibilityNodeInfo.ACTION_CLICK
                                )

                                if (clicked) return true
                            }

                            val parent = current.parent
                            current.recycle()
                            current = parent
                            depth++
                        }

                        current?.recycle()
                    }
                } finally {
                    nodes.forEach {
                        runCatching { it.recycle() }
                    }
                }
            }
        } finally {
            root.recycle()
        }

        Log.w("VANI_WHATSAPP", "Could not find the WhatsApp search button")
        return false
    }

    /**
     * Enters text in a visible editable field.
     * Use this after opening WhatsApp search.
     */
    fun setWhatsAppSearchText(query: String): Boolean {
        if (query.isBlank() || !isWhatsAppVisible()) return false

        val root = rootInActiveWindow ?: return false

        try {
            val editFields = mutableListOf<AccessibilityNodeInfo>()

            fun collect(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 20) return

                if (
                    node.isVisibleToUser &&
                    node.isEnabled &&
                    node.className?.toString()?.contains("EditText", true) == true
                ) {
                    editFields.add(AccessibilityNodeInfo.obtain(node))
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    try {
                        collect(child, depth + 1)
                    } finally {
                        child.recycle()
                    }
                }
            }

            collect(root, 0)

            try {
                val target = editFields.firstOrNull() ?: return false

                val arguments = android.os.Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        query
                    )
                }

                return target.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    arguments
                )
            } finally {
                editFields.forEach { it.recycle() }
            }
        } finally {
            root.recycle()
        }
    }

    /**
     * Opens a visible WhatsApp result matching the requested contact.
     * Uses exact matches first to reduce the chance of opening the wrong person.
     */
    /**
     * Returns contact-like labels from the visible WhatsApp screen.
     * Labels are collected from visible nodes; this is diagnostic/matching data,
     * not a guarantee that every label represents a separate contact row.
     */
    fun getWhatsAppContactMatches(query: String): List<String> {
        if (query.isBlank() || !isWhatsAppVisible()) return emptyList()

        val key = normalizeWhatsAppLabel(query)
        if (key.isBlank()) return emptyList()

        val root = rootInActiveWindow ?: return emptyList()
        val labels = linkedSetOf<String>()
        try {
            fun visit(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 20 || !node.isVisibleToUser) return

                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                listOf(text, desc).forEach { label ->
                    val normalized = normalizeWhatsAppLabel(label)
                    if (label.length in 2..100 && normalized.contains(key)) {
                        val lower = label.lowercase()
                        if (!lower.contains("search") &&
                            !lower.contains("message") &&
                            !lower.contains("typing") &&
                            !lower.contains("last seen")) {
                            labels.add(label)
                        }
                    }
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    try { visit(child, depth + 1) } finally { child.recycle() }
                }
            }
            visit(root, 0)
        } finally {
            root.recycle()
        }

        val result = labels.toList().take(8)
        Log.d("VANI_WHATSAPP", "Contact labels matching '$query': $result")
        return result
    }

    /** Normalizes names so punctuation, spaces and case do not prevent a match. */
    private fun normalizeWhatsAppLabel(value: String): String =
        value.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    /**
     * Opens the best visible WhatsApp search-result candidate for a contact.
     * Exact normalized matches are preferred; a longer row label containing the
     * complete requested name is accepted as WhatsApp may append status details.
     * This method only clicks a result; callers should verify the chat before
     * attempting to fill the message field.
     */
    fun openWhatsAppContact(contactName: String): Boolean {
        if (contactName.isBlank()) return false

        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() !in whatsappPackages) {
            Log.w("VANI_WHATSAPP", "WhatsApp is not active; cannot click result")
            return false
        }

        fun clean(value: String): String =
            value.lowercase(Locale.ROOT)
                .replace("\u200B", "")
                .replace("\uFEFF", "")
                .replace(Regex("[^\\p{L}\\p{N}]"), "")

        val query = clean(contactName)
        if (query.isBlank()) {
            root.recycle()
            return false
        }

        data class Candidate(
            val node: AccessibilityNodeInfo,
            val label: String,
            val score: Int
        )

        val candidates = mutableListOf<Candidate>()

        try {
            fun visit(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 20 || !node.isVisibleToUser) return

                val labels = listOf(
                    node.text?.toString().orEmpty(),
                    node.contentDescription?.toString().orEmpty()
                ).filter { it.isNotBlank() }

                for (label in labels.distinct()) {
                    val normalized = clean(label)
                    if (normalized.isBlank()) continue

                    val score = when {
                        normalized == query -> 100
                        normalized.startsWith(query) -> 80
                        normalized.contains(query) -> 60
                        query.contains(normalized) && normalized.length >= 3 -> 40
                        else -> 0
                    }

                    if (score > 0) {
                        candidates.add(
                            Candidate(
                                AccessibilityNodeInfo.obtain(node),
                                label,
                                score
                            )
                        )
                    }
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    try {
                        visit(child, depth + 1)
                    } finally {
                        child.recycle()
                    }
                }
            }

            visit(root, 0)

            val ranked = candidates
                .distinctBy { "${it.label}|${it.node.boundsKey()}" }
                .sortedByDescending { it.score }

            Log.d(
                "VANI_WHATSAPP",
                "Ranked results: ${ranked.map { "${it.label}=${it.score}" }}"
            )

            for (candidate in ranked) {
                var current: AccessibilityNodeInfo? =
                    AccessibilityNodeInfo.obtain(candidate.node)

                try {
                    repeat(9) {
                        val node = current ?: return@repeat

                        if (node.isVisibleToUser && node.isEnabled && node.isClickable) {
                            Log.d(
                                "VANI_WHATSAPP",
                                "Clicking best match: ${candidate.label}, score=${candidate.score}"
                            )

                            if (node.performAction(
                                    AccessibilityNodeInfo.ACTION_CLICK
                                )
                            ) {
                                return true
                            }
                        }

                        val parent = node.parent
                        node.recycle()
                        current = parent
                    }

                    val bounds = android.graphics.Rect()
                    candidate.node.getBoundsInScreen(bounds)

                    // Fallback: tap the center of the matching visible result.
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        val path = android.graphics.Path().apply {
                            moveTo(bounds.exactCenterX(), bounds.exactCenterY())
                        }

                        val gesture = android.accessibilityservice.GestureDescription
                            .Builder()
                            .addStroke(
                                android.accessibilityservice.GestureDescription.StrokeDescription(
                                    path, 0, 100
                                )
                            )
                            .build()

                        if (android.os.Build.VERSION.SDK_INT >= 24 &&
                            dispatchGesture(gesture, null, null)
                        ) {
                            Log.d(
                                "VANI_WHATSAPP",
                                "Tap dispatched for best match: ${candidate.label}"
                            )
                            return true
                        }
                    }
                } finally {
                    current?.recycle()
                }
            }

            Log.w("VANI_WHATSAPP", "No matching contact result could be clicked")
            return false
        } finally {
            candidates.forEach { runCatching { it.node.recycle() } }
            root.recycle()
        }
    }

    private fun AccessibilityNodeInfo.boundsKey(): String {
        val rect = android.graphics.Rect()
        getBoundsInScreen(rect)
        return "${rect.left},${rect.top},${rect.right},${rect.bottom}"
    }
    /**
     * Fills in a message but DOES NOT send it.
     * Review the recipient and message before confirming.
     */
    fun prepareWhatsAppMessage(message: String): Boolean {
        if (message.isBlank() || !isWhatsAppVisible()) {
            Log.w("VANI_WHATSAPP", "Cannot prepare message: blank text or WhatsApp not active")
            return false
        }

        val root = rootInActiveWindow ?: return false
        val editFields = mutableListOf<AccessibilityNodeInfo>()
        try {
            fun collect(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 22 || !node.isVisibleToUser) return

                val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText?.toString().orEmpty() else ""
                val description = node.contentDescription?.toString().orEmpty()
                val className = node.className?.toString().orEmpty()
                val looksEditable = node.isEditable || className.contains("EditText", ignoreCase = true)
                val looksLikeMessageBox = looksEditable && (
                        hint.contains("message", ignoreCase = true) ||
                                description.contains("message", ignoreCase = true) ||
                                node.isEditable
                        )

                if (node.isEnabled && looksLikeMessageBox) {
                    editFields.add(AccessibilityNodeInfo.obtain(node))
                    Log.d("VANI_WHATSAPP", "Editable candidate: class=$className hint='$hint' desc='$description' editable=${node.isEditable}")
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    try { collect(child, depth + 1) } finally { child.recycle() }
                }
            }

            collect(root, 0)
            if (editFields.isEmpty()) {
                Log.w("VANI_WHATSAPP", "No message field found. Visible labels=${getVisibleUiText().take(60)}")
                return false
            }

            // WhatsApp's chat composer is normally the last editable field on the chat screen.
            val target = editFields.last()
            val arguments = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
            }
            val success = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d("VANI_WHATSAPP", "Prepare message action=$success fieldClass=${target.className}")
            return success
        } finally {
            editFields.forEach { runCatching { it.recycle() } }
            root.recycle()
        }
    }

    /**
     * Sends a prepared message only when explicitly requested.
     */
    fun confirmAndSendWhatsAppMessage(): Boolean {
        if (!isWhatsAppVisible()) return false

        val root = rootInActiveWindow ?: return false

        try {
            val sendLabels = listOf("Send", "Send message")

            for (label in sendLabels) {
                val nodes = root.findAccessibilityNodeInfosByText(label)
                    ?: continue

                try {
                    for (node in nodes) {
                        var current: AccessibilityNodeInfo? =
                            AccessibilityNodeInfo.obtain(node)

                        var depth = 0

                        while (current != null && depth < 8) {
                            if (
                                current.isVisibleToUser &&
                                current.isEnabled &&
                                current.isClickable
                            ) {
                                if (
                                    current.performAction(
                                        AccessibilityNodeInfo.ACTION_CLICK
                                    )
                                ) {
                                    current.recycle()
                                    return true
                                }
                            }

                            val parent = current.parent
                            current.recycle()
                            current = parent
                            depth++
                        }

                        current?.recycle()
                    }
                } finally {
                    nodes.forEach { runCatching { it.recycle() } }
                }
            }

            return false
        } finally {
            root.recycle()
        }
    }

    /**
     * Attempts to click WhatsApp's voice-call control.
     * Check the contact name in the open chat before calling.
     */
    fun clickWhatsAppVoiceCall(): Boolean {
        if (!isWhatsAppVisible()) return false

        val root = rootInActiveWindow ?: return false

        try {
            val labels = listOf(
                "Voice call",
                "Audio call",
                "Call"
            )

            for (label in labels) {
                val nodes = root.findAccessibilityNodeInfosByText(label)
                    ?: continue

                try {
                    for (node in nodes) {
                        var current: AccessibilityNodeInfo? =
                            AccessibilityNodeInfo.obtain(node)

                        var depth = 0

                        while (current != null && depth < 8) {
                            if (
                                current.isVisibleToUser &&
                                current.isEnabled &&
                                current.isClickable
                            ) {
                                if (
                                    current.performAction(
                                        AccessibilityNodeInfo.ACTION_CLICK
                                    )
                                ) {
                                    current.recycle()
                                    return true
                                }
                            }

                            val parent = current.parent
                            current.recycle()
                            current = parent
                            depth++
                        }

                        current?.recycle()
                    }
                } finally {
                    nodes.forEach { runCatching { it.recycle() } }
                }
            }

            return false
        } finally {
            root.recycle()
        }
    }

    private fun isWhatsAppVisible(): Boolean {
        val root = rootInActiveWindow ?: return false

        return try {
            root.packageName?.toString() in whatsappPackages
        } finally {
            root.recycle()
        }
    }

    // ---------- Lock screen and screenshot (Android 9+) ----------

    fun lockScreen(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    fun takeScreenshot(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    // ---------- Settings switches (Wi-Fi, Bluetooth, Airplane, Battery saver) ----------

    private fun isSwitchLike(node: AccessibilityNodeInfo): Boolean {
        val name = node.className?.toString().orEmpty()
        return name.contains("Switch", ignoreCase = true) ||
                name.contains("ToggleButton", ignoreCase = true) ||
                name.contains("CompoundButton", ignoreCase = true)
    }

    /** First visible switch on screen. The caller owns (and may recycle) the returned node. */
    private fun findFirstSwitch(node: AccessibilityNodeInfo, strict: Boolean, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 25) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isCheckable && child.isVisibleToUser && (!strict || isSwitchLike(child))) return child
            val found = findFirstSwitch(child, strict, depth + 1)
            @Suppress("DEPRECATION")
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    /** Clicks [node] or the nearest clickable parent. */
    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable && current.isEnabled && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
            depth++
        }
        return false
    }

    /**
     * Flips the first switch on the current Settings screen to [on].
     * Waits up to ~4 seconds for the screen to appear, so call it from a worker thread.
     */
    fun toggleFirstSwitch(on: Boolean): SwitchOutcome {
        val deadline = System.currentTimeMillis() + 4000
        var target: AccessibilityNodeInfo? = null

        while (System.currentTimeMillis() < deadline && target == null) {
            val root = rootInActiveWindow
            if (root != null) {
                try {
                    target = findFirstSwitch(root, strict = true) ?: findFirstSwitch(root, strict = false)
                } finally {
                    @Suppress("DEPRECATION")
                    root.recycle()
                }
            }
            if (target == null) Thread.sleep(300)
        }

        val switch = target ?: return SwitchOutcome.NOT_FOUND
        try {
            if (switch.isChecked == on) return SwitchOutcome.ALREADY_SET
            if (!clickNodeOrParent(switch)) return SwitchOutcome.NOT_FOUND

            Thread.sleep(500)
            switch.refresh()
            return if (switch.isChecked == on) SwitchOutcome.CHANGED else SwitchOutcome.NOT_FOUND
        } finally {
            @Suppress("DEPRECATION")
            switch.recycle()
        }
    }

    // ---------- Spotify ----------

    private val spotifyPackage = "com.spotify.music"

    private fun findNode(
        node: AccessibilityNodeInfo,
        depth: Int = 0,
        test: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (depth > 25) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (test(child)) return child
            val found = findNode(child, depth + 1, test)
            @Suppress("DEPRECATION")
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun label(node: AccessibilityNodeInfo): String =
        (node.contentDescription?.toString() ?: node.text?.toString() ?: "").trim().lowercase(Locale.getDefault())

    /** Spotify shows a "Pause" button while a song is playing. */
    private fun spotifyIsPlaying(): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            if (root.packageName?.toString() != spotifyPackage) return false
            val pause = findNode(root) { label(it) == "pause" }
            pause?.let {
                @Suppress("DEPRECATION")
                it.recycle()
            }
            pause != null
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    /** Taps the best matching row / play button on Spotify's screen. Returns true if something was tapped. */
    private fun tapSpotifyResult(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            if (root.packageName?.toString() != spotifyPackage) return false
            val words = query.lowercase(Locale.getDefault()).split(" ").filter { it.length >= 3 }
            val needed = maxOf(1, (words.size + 1) / 2)

            val row = findNode(root) { node ->
                !node.isEditable && words.isNotEmpty() &&
                        words.count { label(node).contains(it) } >= needed
            }
            if (row != null) {
                val clicked = clickNodeOrParent(row)
                @Suppress("DEPRECATION")
                row.recycle()
                if (clicked) return true
            }

            val play = findNode(root) { label(it) == "play" }
            if (play != null) {
                val clicked = clickNodeOrParent(play)
                @Suppress("DEPRECATION")
                play.recycle()
                return clicked
            }
            return false
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    /**
     * After VANI asked Spotify to play [query], waits for it to start. If it only shows search results,
     * taps the best match. Best effort: returns true only when Spotify shows the "Pause" button.
     * Call from a worker thread.
     */
    fun ensureSpotifyPlaying(query: String): Boolean {
        val deadline = System.currentTimeMillis() + 9000
        var taps = 0
        Thread.sleep(1500)
        while (System.currentTimeMillis() < deadline) {
            if (spotifyIsPlaying()) return true
            if (taps < 3 && tapSpotifyResult(query)) {
                taps++
                Thread.sleep(1200)
            } else {
                Thread.sleep(500)
            }
        }
        return spotifyIsPlaying()
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        var instance: VaniAccessibilityService? = null
    }
}