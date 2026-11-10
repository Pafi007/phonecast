package com.example.phonecast

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket

class CastService : Service() {
    companion object { @Volatile var state = 0 } // 0 gotowy, 1 łączenie, 2 na żywo
    @Volatile private var out: DataOutputStream? = null
    @Volatile private var sock: Socket? = null
    private var proj: MediaProjection? = null
    private var ht: HandlerThread? = null

    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { state = 0; runCatching { sock?.close() }; runCatching { proj?.stop() }; ht?.quitSafely() }

    override fun onStartCommand(i: Intent, f: Int, id: Int): Int {
        state = 1
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("c", "Cast", NotificationManager.IMPORTANCE_LOW))
        startForeground(
            1,
            Notification.Builder(this, "c").setContentTitle("PhoneCast").setSmallIcon(android.R.drawable.ic_menu_camera).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
        @Suppress("DEPRECATION")
        val pr = getSystemService(MediaProjectionManager::class.java)
            .getMediaProjection(i.getIntExtra("code", 0), i.getParcelableExtra<Intent>("data")!!)
        pr.registerCallback(object : MediaProjection.Callback() {}, null)
        proj = pr

        val dm = resources.displayMetrics
        val sw = 720
        val sh = dm.heightPixels * sw / dm.widthPixels
        val rd = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2)
        val t = HandlerThread("cap").also { it.start() }
        ht = t
        pr.createVirtualDisplay("pc", sw, sh, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, rd.surface, null, null)

        rd.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val o = out
                if (o != null) {
                    val p = img.planes[0]
                    val full = Bitmap.createBitmap(p.rowStride / p.pixelStride, sh, Bitmap.Config.ARGB_8888)
                    full.copyPixelsFromBuffer(p.buffer)
                    val bos = ByteArrayOutputStream()
                    Bitmap.createBitmap(full, 0, 0, sw, sh).compress(Bitmap.CompressFormat.JPEG, 60, bos)
                    full.recycle()
                    val b = bos.toByteArray()
                    o.writeInt(b.size); o.write(b); o.flush()
                }
            } catch (e: IOException) {
                out = null
            } finally {
                img.close()
            }
        }, Handler(t.looper))

        val host = i.getStringExtra("host")!!
        val port = i.getIntExtra("port", 5555)
        val token = i.getStringExtra("token")!!
        Thread { link(host, port, token) }.start()
        return START_NOT_STICKY
    }

    private fun link(host: String, port: Int, token: String) {
        try {
            val s = Socket(); sock = s
            s.connect(InetSocketAddress(host, port), 5000)
            s.tcpNoDelay = true
            s.getOutputStream().apply { write((token + "\n").toByteArray()); flush() }
            out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
            state = 2
            val inp = BufferedReader(InputStreamReader(s.getInputStream()))
            while (true) {
                val ln = inp.readLine() ?: break
                runCatching { TouchService.inst?.cmd(ln) }
            }
        } catch (e: IOException) { }
        out = null
        stopSelf()
    }
}
