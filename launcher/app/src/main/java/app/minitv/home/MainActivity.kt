package app.minitv.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Date

/** One screen: rotating wallpaper, clock, dock. Reads /sdcard/TVHome/{wallpapers,favorites.txt}. */
class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val root = File(Environment.getExternalStorageDirectory(), "TVHome")
    private lateinit var bgA: ImageView
    private lateinit var bgB: ImageView
    private lateinit var clock: TextView
    private lateinit var dock: LinearLayout
    private var walls: List<File> = emptyList()
    private var idx = 0
    private var front = true

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val fl = FrameLayout(this)
        fl.setBackgroundColor(0xFF0B1A14.toInt())
        bgA = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        bgB = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
        val mp = FrameLayout.LayoutParams.MATCH_PARENT
        val wc = FrameLayout.LayoutParams.WRAP_CONTENT
        fl.addView(bgA, FrameLayout.LayoutParams(mp, mp))
        fl.addView(bgB, FrameLayout.LayoutParams(mp, mp))
        clock = TextView(this).apply {
            textSize = 54f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(8f, 0f, 2f, 0x99000000.toInt())
        }
        fl.addView(clock, FrameLayout.LayoutParams(wc, wc, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(48), dp(28), 0, 0)
        })
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply { cornerRadius = dp(30).toFloat(); setColor(0x66000000) }
        }
        fl.addView(dock, FrameLayout.LayoutParams(wc, wc, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(40)
        })
        fl.clipChildren = false
        setContentView(fl)
    }

    override fun onResume() {
        super.onResume()
        walls = File(root, "wallpapers").listFiles { f ->
            f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }?.sortedBy { it.name }?.shuffled() ?: emptyList()
        if (walls.isEmpty()) {
            bgA.setImageDrawable(GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF1B3A4B.toInt(), 0xFF3D5A5B.toInt(), 0xFF0B1A14.toInt())))
        }
        buildDock()
        h.removeCallbacks(rotate); h.removeCallbacks(tick)
        h.post(rotate); h.post(tick)
    }

    override fun onPause() {
        h.removeCallbacks(rotate); h.removeCallbacks(tick)
        super.onPause()
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        dock.getChildAt(0)?.requestFocus()
    }

    override fun onBackPressed() { /* home screen: stay put */ }

    private val tick = object : Runnable {
        override fun run() {
            clock.text = android.text.format.DateFormat.getTimeFormat(this@MainActivity).format(Date())
            h.postDelayed(this, 20_000)
        }
    }

    private val rotate = object : Runnable {
        override fun run() { next(); h.postDelayed(this, 90_000) }
    }

    private fun decode(f: File): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / s > 2000) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
            inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565
        })
    } catch (e: Throwable) { null }

    private fun next() {
        if (walls.isEmpty()) return
        val bmp = decode(walls[idx++ % walls.size]) ?: return
        val inV = if (front) bgB else bgA
        val outV = if (front) bgA else bgB
        inV.setImageBitmap(bmp)
        inV.animate().alpha(1f).setDuration(1500).start()
        outV.animate().alpha(0f).setDuration(1500).withEndAction { outV.setImageDrawable(null) }.start()
        front = !front
    }

    private fun buildDock() {
        dock.removeAllViews()
        val pm = packageManager
        val favs = try {
            File(root, "favorites.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }
        } catch (e: Throwable) { emptyList() }
        for (p in favs) {
            val i = pm.getLeanbackLaunchIntentForPackage(p) ?: pm.getLaunchIntentForPackage(p) ?: continue
            val ic = try { pm.getApplicationIcon(p) } catch (e: Throwable) { continue }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            dock.addView(tile(ic) { startActivity(i) })
        }
        dock.addView(tile(label("All apps")) { showAll() })
        dock.addView(tile(label("Settings")) {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        dock.getChildAt(0)?.requestFocus()
    }

    private fun label(t: String): Drawable {
        val b = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER }
        Canvas(b).drawText(t, 80f, 90f, p)
        return BitmapDrawable(resources, b)
    }

    private fun tile(ic: Drawable, onClick: () -> Unit): View {
        fun bg(c: Int) = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(c) }
        return ImageView(this).apply {
            setImageDrawable(ic)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(dp(88), dp(88)).apply { setMargins(dp(10), 0, dp(10), 0) }
            isFocusable = true
            isClickable = true
            background = bg(0x22FFFFFF)
            setOnFocusChangeListener { v, f ->
                val s = if (f) 1.25f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
                v.background = bg(if (f) 0x55FFFFFF else 0x22FFFFFF)
            }
            setOnClickListener {
                try { onClick() } catch (e: Throwable) {
                    Toast.makeText(this@MainActivity, "Cannot open", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAll() {
        val pm = packageManager
        val a = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val b = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = (pm.queryIntentActivities(a, 0) + pm.queryIntentActivities(b, 0))
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val names = apps.map { it.loadLabel(pm).toString() }.toTypedArray()
        AlertDialog.Builder(this).setTitle("All apps").setItems(names) { _, i ->
            val ai = apps[i].activityInfo
            startActivity(Intent(Intent.ACTION_MAIN).setClassName(ai.packageName, ai.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.show()
    }
}
