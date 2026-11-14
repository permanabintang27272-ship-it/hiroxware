package com.hiroxware

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private val tweaks = listOf(
        Triple("global", "window_animation_scale", "0.5"),
        Triple("global", "transition_animation_scale", "0.5"),
        Triple("global", "animator_duration_scale", "0.5"),
        Triple("system", "peak_refresh_rate", "120"),
        Triple("system", "min_refresh_rate", "120")
    )

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("hiroxware", MODE_PRIVATE) }

    private lateinit var ramCard: Card
    private lateinit var batteryCard: Card
    private lateinit var storageCard: Card
    private lateinit var rateCard: Card
    private lateinit var statusText: TextView
    private lateinit var logText: TextView

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        log(if (result == PackageManager.PERMISSION_GRANTED) "Izin Shizuku diberikan." else "Izin Shizuku ditolak.")
        refreshStatus()
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateStats()
            handler.postDelayed(this, 2000)
        }
    }

    // ---------- Kartu angka ----------

    private inner class Card(title: String) {
        val value = TextView(this@MainActivity).apply {
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val sub = TextView(this@MainActivity).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#8F8BC7"))
        }
        val bar = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(Color.parseColor("#FF2D55"))
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A2757"))
        }
        val dotGreen = dot("#34D399")
        val dotRed = dot("#FF2D55")

        val view = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = box("#1C1A44", "#4B47A0", 20)

            val top = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 10f
                    setTextColor(Color.parseColor("#B8B4EC"))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(dotGreen, LinearLayout.LayoutParams(dp(6), dp(6)).apply { setMargins(0, 0, dp(4), 0) })
                addView(dotRed, LinearLayout.LayoutParams(dp(6), dp(6)))
            }
            addView(top)
            addView(value)
            addView(sub)
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply {
                topMargin = dp(10)
            })
        }
    }

    // ---------- Siklus hidup ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        Shizuku.addRequestPermissionResultListener(permListener)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permListener)
    }

    // ---------- Tampilan ----------

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0920"))
            setPadding(dp(14), dp(28), dp(14), dp(24))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "Hiröxware"
            textSize = 24f
            setTextColor(Color.parseColor("#FF2D55"))
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(pill("ENGINE ACTIVE"))
        root.addView(header)

        statusText = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#8F8BC7"))
            setPadding(0, dp(4), 0, 0)
        }
        root.addView(statusText)

        // Kartu utama
        ramCard = Card("RAM")
        batteryCard = Card("BATERAI")
        rateCard = Card("LAYAR")
        storageCard = Card("PENYIMPANAN")
        root.addView(row(ramCard.view, batteryCard.view))
        root.addView(row(rateCard.view, storageCard.view))

        // Tombol utama
        root.addView(bigButton("Aktifkan Tweak", "#FF2D55", "#FF7A93") { applyTweaks() }, topMargin(16))
        root.addView(bigButton("Kembalikan ke Stok", "#3B3880", "#6A64C8") { revertTweaks() }, topMargin(10))
        root.addView(bigButton("Izinkan Shizuku", "#262450", "#3B3880") { requestShizuku() }, topMargin(10))

        logText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#B8B4EC"))
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(logText)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0920"))
            addView(root)
        }
    }

    private fun pill(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 9f
        setTextColor(Color.parseColor("#FF2D55"))
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = box("#1C1A44", "#FF2D55", 12)
    }

    private fun dot(hex: String): View = View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
        }
    }

    private fun row(left: View, right: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, dp(10), dp(5), 0)
            })
            addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(5), dp(10), 0, 0)
            })
        }

    private fun bigButton(label: String, fill: String, stroke: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = box(fill, stroke, 18)
            setOnClickListener { onClick() }
        }

    private fun topMargin(px: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(px) }

    private fun box(fill: String, stroke: String, radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        setStroke(dp(1), Color.parseColor(stroke))
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    private fun log(msg: String) {
        logText.text = msg
    }

    // ---------- Data layar ----------

    private fun updateStats() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val usedPct = ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt()
        ramCard.value.text = "%.1fGB".format((mi.totalMem - mi.availMem) / 1e9)
        ramCard.sub.text = "%.1fGB total".format(mi.totalMem / 1e9)
        ramCard.bar.progress = usedPct

        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        batteryCard.value.text = "$level%"
        batteryCard.sub.text = "Level saat ini"
        batteryCard.bar.progress = level

        @Suppress("DEPRECATION")
        val hz = windowManager.defaultDisplay.refreshRate
        rateCard.value.text = "%.0fHz".format(hz)
        rateCard.sub.text = "Refresh rate saat ini"
        rateCard.bar.progress = (hz / 1.2f).toInt().coerceAtMost(100)

        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        storageCard.value.text = "%.0fGB".format((total - free) / 1e9)
        storageCard.sub.text = "%.0fGB total, %.0fGB kosong".format(total / 1e9, free / 1e9)
        storageCard.bar.progress = ((total - free) * 100 / total).toInt()
    }

    private fun refreshStatus() {
        statusText.text = when {
            !shizukuBinderAlive() -> "Shizuku belum aktif. Buka Shizuku dulu."
            !shizukuGranted() -> "Shizuku aktif, tapi izin belum diberikan."
            else -> "Shizuku siap. Tanpa root."
        }
    }

    // ---------- Shizuku ----------

    private fun shizukuBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    private fun shizukuGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    private fun requestShizuku() {
        if (!shizukuBinderAlive()) {
            log("Shizuku belum aktif. Buka aplikasi Shizuku dan nyalakan dulu.")
            return
        }
        if (shizukuGranted()) {
            log("Izin sudah ada.")
            return
        }
        Shizuku.requestPermission(0)
    }

    private fun runShell(cmd: String): Boolean = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        p.waitFor() == 0
    } catch (e: Exception) {
        false
    }

    // ---------- Terapkan & kembalikan ----------

    private fun saveOriginals() {
        val cr = contentResolver
        val edit = prefs.edit()
        for ((ns, key, _) in tweaks) {
            val name = "orig_${ns}_$key"
            if (prefs.contains(name)) continue
            val value = if (ns == "global") Settings.Global.getString(cr, key) else Settings.System.getString(cr, key)
            edit.putString(name, value ?: "null")
        }
        edit.apply()
    }

    private fun applyTweaks() {
        if (!shizukuBinderAlive() || !shizukuGranted()) {
            log("Aktifkan Shizuku dan izinkan dulu.")
            return
        }
        saveOriginals()
        log("Menerapkan tweak...")
        Thread {
            val ok = tweaks.all { (ns, key, value) -> runShell("settings put $ns $key $value") }
            runOnUiThread {
                log(if (ok) "Tweak aktif. Tekan Kembalikan kalau ada yang tidak cocok." else "Sebagian tweak gagal.")
            }
        }.start()
    }

    private fun revertTweaks() {
        if (!shizukuBinderAlive() || !shizukuGranted()) {
            log("Shizuku tidak aktif. Buka Shizuku dulu.")
            return
        }
        log("Mengembalikan ke stok...")
        Thread {
            var ok = true
            for ((ns, key, _) in tweaks) {
                val stored = prefs.getString("orig_${ns}_$key", null) ?: continue
                val cmd = if (stored == "null") "settings delete $ns $key" else "settings put $ns $key $stored"
                if (!runShell(cmd)) ok = false
            }
            prefs.edit().clear().apply()
            runOnUiThread {
                log(if (ok) "Sudah dikembalikan ke stok." else "Sebagian gagal dikembalikan.")
            }
        }.start()
    }
}
