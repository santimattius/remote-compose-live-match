package example.livematch.android

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Taps Remote Compose targets the way a user does. The player exposes its components as virtual
 * accessibility nodes instead of child views, so targets are located by their visible text.
 */
object RemoteNodeTapper {
    private const val PLAYBACK_TIMEOUT_MS = 10_000L
    private const val POLL_INTERVAL_MS = 25L
    private const val STABILITY_INTERVAL_MS = 250L

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Waits for the asynchronously captured document, then taps the center of [text]. */
    fun tapText(text: String) {
        val bounds = awaitStableBoundsOf(text)
        tap(bounds.centerX(), bounds.centerY())
        instrumentation.waitForIdleSync()
    }

    /** Returns bounds only once two consecutive readings agree, so a window still resizing is not tapped. */
    private fun awaitStableBoundsOf(text: String): Rect {
        val deadline = SystemClock.uptimeMillis() + PLAYBACK_TIMEOUT_MS
        var previous = boundsOf(text)
        while (SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(STABILITY_INTERVAL_MS)
            val current = boundsOf(text)
            if (current == previous) return current
            previous = current
        }
        error("Bounds of '$text' never settled; last seen $previous")
    }

    /** Waits for the asynchronously captured document and returns the on-screen bounds of [text]. */
    fun boundsOf(text: String): Rect =
        checkNotNull(awaitBoundsOf(text)) { "Expected '$text' to be rendered. Accessibility tree:\n${describeTree()}" }

    private fun awaitBoundsOf(text: String): Rect? {
        val automation = instrumentation.uiAutomation
        // Remote Compose nodes are virtual accessibility nodes, which are only reported when
        // not-important views are included.
        automation.serviceInfo =
            automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS }
        val deadline = SystemClock.uptimeMillis() + PLAYBACK_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val node = findNodeWithText(automation.rootInActiveWindow, text)
            if (node != null) return Rect().also(node::getBoundsInScreen)
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return null
    }

    /** Walks the tree by hand: the player's virtual nodes do not support findAccessibilityNodeInfosByText. */
    private fun findNodeWithText(
        node: AccessibilityNodeInfo?,
        text: String,
    ): AccessibilityNodeInfo? = findNode(node) { it.text?.toString() == text }

    /** Waits for the node showing [text] and returns the content description a screen reader would announce. */
    fun descriptionOf(text: String): String {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo =
            automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS }
        val deadline = SystemClock.uptimeMillis() + PLAYBACK_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val node = findNodeWithText(automation.rootInActiveWindow, text)
            if (node != null) return node.contentDescription?.toString().orEmpty()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        error("Expected '$text' to be rendered. Accessibility tree:\n${describeTree()}")
    }

    /** Waits for a node whose text starts with [prefix] and returns its current text. */
    fun awaitTextStartingWith(prefix: String): String {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo =
            automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS }
        val deadline = SystemClock.uptimeMillis() + PLAYBACK_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val node = findNode(automation.rootInActiveWindow) { it.text?.startsWith(prefix) == true }
            if (node != null) return node.text.toString()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        error("Expected text starting with '$prefix'. Accessibility tree:\n${describeTree()}")
    }

    private fun findNode(
        node: AccessibilityNodeInfo?,
        matches: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (matches(node)) return node
        for (i in 0 until node.childCount) {
            findNode(node.getChild(i), matches)?.let { return it }
        }
        return null
    }

    private fun describeTree(): String {
        val automation = instrumentation.uiAutomation
        val lines = mutableListOf<String>()

        fun visit(
            node: AccessibilityNodeInfo?,
            depth: Int,
        ) {
            if (node == null) return
            lines += "${"  ".repeat(depth)}${node.className} text=${node.text} desc=${node.contentDescription}"
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        lines += "root=${automation.rootInActiveWindow?.packageName}"
        visit(automation.rootInActiveWindow, 0)
        return lines.joinToString("\n")
    }

    private fun tap(
        x: Int,
        y: Int,
    ) {
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
    }
}
