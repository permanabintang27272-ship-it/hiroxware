
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
import android.os.Build
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
private const val GREEN = "#34D399"
private const val SUB = "#B5B3C9"
private const val BG = "#1C1D24"

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
    private val tiltTargets = mutableListOf<View>()
    private var tilt: TiltEffect? = null

    private lateinit var ramCard: Card
    private lateinit var batteryCard: Card
    private lateinit var storageCard: Card
    private lateinit var rateCard: Card
    private lateinit var statusText: TextView
    private lateinit var statusPill: TextView
    private lateinit var activeValue: TextView
    private lateinit var shizukuValue: TextView
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
            background = glossy(20, "#2E2F3A", "#22232B", "#33FFFFFF")
            elevation = dp(8).toFloat()

            val top = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 10f
                    setTextColor(Color.parseColor(SUB))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(dot(GREEN), LinearLayout.LayoutParams(dp(6), dp(6)).apply { setMargins(0, 0, dp(4), 0) })
                addView(dot(ACCENT), LinearLayout.LayoutParams(dp(6), dp(6)))
            }
            addView(top)
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
        setContentView(buildUi())
        tilt = TiltEffect(this, tiltTargets, dp(18).toFloat())
        Shizuku.addRequestPermissionResultListener(permListener)
        refreshToggles()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
        tilt?.start()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        tilt?.stop()
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
            addView(pill(Build.MODEL))
        }
        root.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = glossy(24, "#2E2F3A", "#1B1C24", "#44FFFFFF")
            elevation = dp(10).toFloat()
            addView(TextView(this@MainActivity).apply {
                text = "🐰"
                textSize = 26f
                gravity = Gravity.CENTER
                background = glossy(20, "#3A1F28", "#261720", ACCENT)
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                addView(TextView(this@MainActivity).apply {
                    text = "HIRÖXWARE"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                })
                statusText = TextView(this@MainActivity).apply {
                    textSize = 11f
                    setTextColor(Color.parseColor(SUB))
                }
                addView(statusText)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            statusPill = pill("...")
            addView(statusPill)
        }
        root.addView(hero, topMargin(12))
        tiltTargets.add(hero)

        activeValue = valueText("0")
        shizukuValue = valueText("...")
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = glossy(20, "#2E2F3A", "#1B1C24", "#33FFFFFF")
            addView(column("FITUR AKTIF", activeValue), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(column("SHIZUKU", shizukuValue), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(stats, topMargin(12))
        tiltTargets.add(stats)

        ramCard = Card("RAM")
        batteryCard = Card("BATERAI")
        rateCard = Card("LAYAR")
        storageCard = Card("PENYIMPANAN")
        root.addView(row(ramCard.view, batteryCard.view))
        root.addView(row(rateCard.view, storageCard.view))
        tiltTargets.add(ramCard.view)
        tiltTargets.add(batteryCard.view)
        tiltTargets.add(rateCard.view)
        tiltTargets.add(storageCard.view)

        root.addView(label("FITUR"), topMargin(18))
        features.forEach { root.addView(featureCard(it), topMargin(10)) }

        root.addView(button("Revert ke Stok") { revertAll() }, topMargin(18))
        root.addView(button("Izinkan Shizuku") { requestShizuku() }, topMargin(10))

        logText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor(SUB))
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(logText)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor(BG))
            addView(root)
        }
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
            elevation = dp(10).toFloat()
            isClickable = true
            setOnClickListener { toggle(f.id) }
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(badge)
        }
        cards[f.id] = card
        tiltTargets.add(card)
        return card
    }

    private fun refreshToggles() {
        features.forEach { f ->
            val on = isOn(f.id)
            badges[f.id]?.apply {
                text = if (on) "ON" else "OFF"
                setTextColor(Color.parseColor(if (on) "#FFFFFF" else SUB))
                background = if (on) glossy(14, "#FF2D55", "#D41E44", "#FF7A93")
                else glossy(14, "#33FFFFFF", "#22FFFFFF", "#44FFFFFF")
            }
            cards[f.id]?.background = if (on) glossy(22, "#3A1F28", "#261720", ACCENT)
            else glossy(22, "#2E2F3A", "#22232B", "#33FFFFFF")
        }
        activeValue.text = features.count { isOn(it.id) }.toString()
    }

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = glossy(18, "#3A3B46", "#202129", "#40FFFFFF")
        elevation = dp(12).toFloat()
        isClickable = true
        setOnClickListener { onClick() }
    }.also { tiltTargets.add(it) }

    private fun pill(label: String, color: String = ACCENT): TextView = TextView(this).apply {
        text = label
        textSize = 9f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor(color))
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = glossy(12, "#2A2B35", "#1E1F27", color)
    }

    private fun valueText(v: String): TextView = TextView(this).apply {
        text = v
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
    }

    private fun column(label: String, value: TextView): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 9f
            setTextColor(Color.parseColor(SUB))
        })
        addView(value)
    }

    private fun dot(hex: String): View = View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
        }
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

    private fun glossy(radiusDp: Int, top: String, bottom: String, stroke: String) =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor(top), Color.parseColor(bottom))
        ).apply {
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), Color.parseColor(stroke))
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
        val alive = shizukuBinderAlive()
        val granted = alive && shizukuGranted()
        statusText.text = when {
            !alive -> "Shizuku belum aktif. Buka Shizuku dulu."
            !granted -> "Shizuku aktif, tapi izin belum diberikan."
            else -> "Shizuku siap. Tanpa root."
        }
        statusPill.text = when {
            !alive -> "MATI"
            !granted -> "IZIN"
            else -> "SIAP"
        }
        statusPill.setTextColor(Color.parseColor(if (granted) GREEN else ACCENT))
        shizukuValue.text = when {
            !alive -> "Mati"
            !granted -> "Belum izin"
            else -> "Siap"
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
