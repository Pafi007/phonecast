package com.example.phonecast

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import com.google.zxing.integration.android.IntentIntegrator

class MainActivity : Activity() {
    private var pending: List<String>? = null
    private val h = Handler(Looper.getMainLooper())
    private lateinit var dot: View
    private lateinit var st: TextView
    private lateinit var ctl: TextView

    private val tick = object : Runnable {
        override fun run() {
            val s = CastService.state
            st.text = listOf("Gotowy do połączenia", "Łączenie…", "Na żywo — ekran udostępniany")[s]
            (dot.background as GradientDrawable).setColor(intArrayOf(0xFFFFBE46.toInt(), 0xFF5A78FF.toInt(), 0xFF46E68C.toInt())[s])
            ctl.text = if (TouchService.inst != null) "✓  Sterowanie z PC włączone" else "Włącz sterowanie z PC"
            h.postDelayed(this, 400)
        }
    }

    private fun lp(top: Int = 14) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.TRANSPARENT
        requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 0)

        dot = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.GRAY) } }
        ObjectAnimator.ofFloat(dot, "alpha", 1f, .3f).apply { duration = 900; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE }.start()
        st = TextView(this).apply { textSize = 17f; setTextColor(Color.WHITE) }
        val card = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(22), dp(24), dp(22))
            background = GlassDrawable(dp(30).toFloat())
            addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(14) })
            addView(st)
        }
        ctl = glassButton("") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(28), dp(56), dp(28), dp(40)) }
        col.addView(TextView(this).apply { text = "PhoneCast"; textSize = 44f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
        col.addView(TextView(this).apply { text = "Ekran telefonu na PC, bez kabla"; textSize = 16f; setTextColor(0xB3FFFFFF.toInt()) })
        col.addView(card, lp(36))
        col.addView(glassButton("Skanuj kod QR", 0xFF5A78FF.toInt()) {
            IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setBeepEnabled(false)
                .setOrientationLocked(false).setPrompt("Skieruj aparat na kod QR w PhoneCast na PC").initiateScan()
        }, lp(28))
        col.addView(ctl, lp())
        col.addView(glassButton("Rozłącz", 0xFFFF5078.toInt()) { stopService(Intent(this, CastService::class.java)) }, lp())

        setContentView(FrameLayout(this).apply { addView(GlassBg(context)); addView(col) })

        for (i in 0 until col.childCount) col.getChildAt(i).apply {
            alpha = 0f; translationY = dp(40).toFloat()
            animate().alpha(1f).translationY(0f).setStartDelay(i * 90L).setDuration(650).setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    override fun onResume() { super.onResume(); h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }

    override fun onActivityResult(rq: Int, rc: Int, d: Intent?) {
        IntentIntegrator.parseActivityResult(rq, rc, d)?.contents?.let { c ->
            val p = c.split("|")
            if (p.size == 4 && p[0] == "PHC") {
                pending = p
                startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 1)
            }
        }
        if (rq == 1 && rc == RESULT_OK) pending?.let {
            stopService(Intent(this, CastService::class.java))
            startForegroundService(
                Intent(this, CastService::class.java).putExtra("code", rc).putExtra("data", d)
                    .putExtra("host", it[1]).putExtra("port", it[2].toInt()).putExtra("token", it[3])
            )
        }
    }
}
