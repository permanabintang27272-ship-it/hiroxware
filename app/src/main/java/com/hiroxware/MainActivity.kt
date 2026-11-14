package com.hiroxware

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
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
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku

private const val ACCENT = "#FF2D55"
private const val SUB = "#C4C0F2"
private const val GLASS = "#331B1A3F"
private const val STROKE = "#665B57C9"

class MainActivity : Activity() {

    private data class Setting(val ns: String, val key: String, val value: String)
    private data class Feature(val id: String, val title: String, val sub: String, val settings: List<Setting>)

    private val features = listOf(
        Feature("licin", "Layar Licin", "Refresh rate 120Hz", listOf(
            Setting("system", "peak_refresh_rate", "120"),
            Setting("system", "min_refresh_rate", "120")
        )),
        Feature("ringan", "Layar Ringan", "Animasi jendela dipercepat", listOf(
            Setting("global", "window_animation_scale", "0.5"),
            Setting("global", "transition_animation_scale", "0.5")
        )),
        Feature("responsif", "Responsif Naik", "Durasi animasi lebih singkat", listOf(
            Setting("global", "animator_duration_scale", "0.5")
        ))
    )

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("hiroxware", MODE_PRIVATE) }
    private val badges = mutableMapOf<String, TextView>()
    private val cards = mutableMapOf<String, LinearLayout>()

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
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val sub = TextView(this@MainActivity).apply {
            textSize = 11f
            setTextColor(Color.parseColor(SUB))
        }
        val bar = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(Color.parseColor(ACCENT))
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#22FFFFFF"))
        }
        val view = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = box(GLASS, STROKE, 20)
            elevation = dp(10).toFloat()
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 10f
                setTextColor(Color.parseColor(SUB))
            })
            addView(value)
            addView(sub)
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply {
                topMargin = dp(10)
            })
        }
    }

    // ---------- Siklus hidup ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(buildUi())
        Shizuku.addRequestPermissionResultListener(permListener)
        refreshToggles()
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
            setPadding(dp(14), dp(34), dp(14), dp(24))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "Hiröxware"
                textSize = 22f
                setTextColor(Color.parseColor(ACCENT))
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(pill("ENGINE ACTIVE", ACCENT))
        }
        root.addView(header)

        statusText = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor(SUB))
            setPadding(0, dp(4), 0, 0)
        }
        root.addView(statusText)

        ramCard = Card("RAM")
        batteryCard = Card("BATERAI")
        rateCard = Card("LAYAR")
        storageCard = Card("PENYIMPANAN")
        root.addView(row(ramCard.view, batteryCard.view))
        root.addView(row(rateCard.view, storageCard.view))

        root.addView(label("FITUR"), topMargin(18))
        features.forEach { root.addView(featureCard(it), topMargin(10)) }

        root.addView(floatingButton("Revert ke Stok", "#663B3880", "#66FFFFFF") { revertAll() }, topMargin(18))
        root.addView(floatingButton("Izinkan Shizuku", "#66262450", "#55FFFFFF") { requestShizuku() }, topMargin(10))

        logText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor(SUB))
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(logText)

        return ScrollView(this).apply { addView(root) }
    }

    private fun featureCard(f: Feature): LinearLayout {
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = f.title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@MainActivity).apply {
                text = f.sub
                textSize = 11f
                setTextColor(Color.parseColor(SUB))
            })
        }
        val badge = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        badges[f.id] = badge
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(12).toFloat()
            isClickable = true
            setOnClickListener { toggle(f.id) }
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(badge)
        }
        cards[f.id] = card
        return card
    }

    private fun refreshToggles() {
        features.forEach { f ->
            val on = isOn(f.id)
            badges[f.id]?.apply {
                text = if (on) "ON" else "OFF"
                setTextColor(Color.parseColor(if (on) "#FFFFFF" else SUB))
                background = box(if (on) ACCENT else "#22FFFFFF", if (on) ACCENT else "#44FFFFFF", 14)
            }
            cards[f.id]?.background = box(if (on) "#33FF2D55" else GLASS, if (on) ACCENT else STROKE, 22)
        }
    }

    private fun floatingButton(label: String, fill: String, stroke: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = box(fill, stroke, 22)
            elevation = dp(14).toFloat()
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun pill(label: String, color: String): TextView = TextView(this).apply {
        text = label
        textSize = 9f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor(color))
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = box("#1C1A44", color, 12)
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor(SUB))
    }

    private fun row(left: View, right: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, dp(10), dp(5), 0)
            })
            addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(5), dp(10), 0, 0)
            })
        }

    private fun topMargin(px: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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

    private fun ready(): Boolean = shizukuBinderAlive() && shizukuGranted()

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

    // ---------- Nyala / mati fitur ----------

    private fun isOn(id: String) = prefs.getBoolean("on_$id", false)

    private fun toggle(id: String) {
        if (!ready()) {
            log("Aktifkan Shizuku dan izinkan dulu.")
            return
        }
        prefs.edit().putBoolean("on_$id", !isOn(id)).apply()
        refreshToggles()
        applyAll()
    }

    private fun saveOriginals() {
        val cr = contentResolver
        val edit = prefs.edit()
        for (f in features) for (s in f.settings) {
            val name = "orig_${s.ns}_${s.key}"
            if (prefs.contains(name)) continue
            val value = if (s.ns == "global") Settings.Global.getString(cr, s.key) else Settings.System.getString(cr, s.key)
            edit.putString(name, value ?: "null")
        }
        edit.apply()
    }

    private fun restoreCmd(s: Setting): String? {
        val stored = prefs.getString("orig_${s.ns}_${s.key}", null) ?: return null
        return if (stored == "null") "settings delete ${s.ns} ${s.key}"
        else "settings put ${s.ns} ${s.key} $stored"
    }

    private fun applyAll(resetAfter: Boolean = false) {
        saveOriginals()
        log("Menerapkan...")
        Thread {
            var ok = true
            for (f in features) {
                val on = isOn(f.id)
                for (s in f.settings) {
                    val cmd = if (on) "settings put ${s.ns} ${s.key} ${s.value}" else restoreCmd(s) ?: continue
                    if (!runShell(cmd)) ok = false
                }
            }
            if (resetAfter) prefs.edit().clear().apply()
            runOnUiThread {
                refreshToggles()
                log(if (ok) "Berhasil." else "Sebagian gagal. Pastikan Shizuku masih aktif.")
            }
        }.start()
    }

    private fun revertAll() {
        if (!ready()) {
            log("Shizuku tidak aktif. Buka Shizuku dulu.")
            return
        }
        val edit = prefs.edit()
        features.forEach { edit.putBoolean("on_${it.id}", false) }
        edit.apply()
        applyAll(resetAfter = true)
    }
}
