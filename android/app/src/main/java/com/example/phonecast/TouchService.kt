package com.example.phonecast

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlin.math.hypot

class TouchService : AccessibilityService() {
    companion object { @Volatile var inst: TouchService? = null }
    private var dx = 0f
    private var dy = 0f
    private var t0 = 0L

    override fun onServiceConnected() { inst = this }
    override fun onUnbind(i: Intent?): Boolean { inst = null; return super.onUnbind(i) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val p = Path().apply { moveTo(x1, y1); if (hypot(x2 - x1, y2 - y1) > 10) lineTo(x2, y2) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), null, null)
    }

    // D x y = mysz w dół, U x y = mysz w górę (dotknięcie/przeciągnięcie), W ±1 = scroll, B = wstecz, H = home
    fun cmd(l: String) {
        val a = l.split(" ")
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        when (a[0]) {
            "D" -> { dx = a[1].toFloat() * w; dy = a[2].toFloat() * h; t0 = System.currentTimeMillis() }
            "U" -> swipe(dx, dy, a[1].toFloat() * w, a[2].toFloat() * h, (System.currentTimeMillis() - t0).coerceIn(50L, 5000L))
            "W" -> { val s = if (a[1].toInt() > 0) 1 else -1; swipe(w / 2f, h * (0.5f - 0.15f * s), w / 2f, h * (0.5f + 0.15f * s), 200) }
            "B" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "H" -> performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }
}
