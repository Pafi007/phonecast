package com.example.phonecast

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

class GlassBg(c: Context) : View(c) {
    private var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cols = intArrayOf(0xFFFF5078.toInt(), 0xFF5A78FF.toInt(), 0xFF3CDCC8.toInt(), 0xFFB45AFF.toInt())
    init {
        ValueAnimator.ofFloat(0f, 1000f).apply {
            duration = 1_000_000; interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
        }.start()
    }
    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF0A0C1C.toInt())
        for (i in cols.indices) {
            val a = t * (0.25f + 0.07f * i) + i * 1.7f
            val x = width * (0.5f + 0.4f * sin(a))
            val y = height * (0.5f + 0.4f * cos(a * 0.8f + i))
            val r = width * (0.55f + 0.08f * i)
            p.shader = RadialGradient(x, y, r, cols[i], cols[i] and 0xFFFFFF, Shader.TileMode.CLAMP)
            p.alpha = 190
            c.drawCircle(x, y, r, p)
        }
    }
}

class GlassDrawable(private val rad: Float, private val tint: Int = Color.WHITE) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas) {
        val r = RectF(bounds).apply { inset(2f, 2f) }
        val rgb = tint and 0xFFFFFF
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom, rgb or 0x48000000, rgb or 0x10000000, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rad, rad, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f
        p.shader = LinearGradient(r.left, r.top, r.right, r.bottom, intArrayOf(0xD2FFFFFF.toInt(), 0x1EFFFFFF, 0x78FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rad, rad, p)
        val h = RectF(r.left + rad * .5f, r.top + 5f, r.right - rad * .5f, r.top + r.height() * .32f)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, h.top, 0f, h.bottom, 0x46FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(h, rad * .6f, rad * .6f, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun Context.glassButton(label: String, tint: Int = Color.WHITE, onClick: () -> Unit) = TextView(this).apply {
    text = label; textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    background = GlassDrawable(dp(30).toFloat(), tint)
    setPadding(dp(24), dp(20), dp(24), dp(20))
    setOnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> v.animate().setStartDelay(0).scaleX(.95f).scaleY(.95f).setDuration(120).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().setStartDelay(0).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(OvershootInterpolator(3f)).start()
                if (e.action == MotionEvent.ACTION_UP) onClick()
            }
        }
        true
    }
}
