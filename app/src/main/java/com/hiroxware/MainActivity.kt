        
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

class MainActivity : Activity() {

    private val accent = "#FF2D55"
    private val green = "#34D399"
    private val subText = "#B5B3C9"
    private val background = "#1C1D24"

    private class Setting(val ns: String, val key: String, val value: String)

    private class Feature(
        val id: String,
        val title: String,
        val sub: String,
        val settings: List<Setting>
    )

    private val features: List<Feature> = listOf(
        Feature(
            "licin", "Layar Licin", "Refresh rate 120Hz",
            listOf(
                Setting("system", "peak_refresh_rate", "120"),
                Setting("system", "min_refresh_rate", "120")
            )
        ),
        Feature(
            "ringan", "Layar Ringan", "Animasi jendela dipercepat",
            listOf(
                Setting("global", "window_animation_scale", "0.5"),
                Setting("global", "transition_animation_scale", "0.5")
            )
        ),
        Feature(
            "responsif", "Responsif Naik", "Durasi animasi lebih singkat",
            listOf(
                Setting("global", "animator_duration_scale", "0.5")
            )
        )
    )

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("hiroxware", MODE_PRIVATE) }
    private val badges = HashMap<String, TextView>()
    private val cards = HashMap<String, LinearLayout>()
    private val tiltTargets = ArrayList<View>()
    private var tilt: TiltEffect? = null

    private lateinit var ramCard: StatCard
    private lateinit var batteryCard: StatCard
    private lateinit var storageCard: StatCard
    private lateinit var rateCard: StatCard
    private lateinit var statusText: TextView
    private lateinit var statusPill: TextView
    private lateinit var activeValue: TextView
    private lateinit var shizukuValue: TextView
    private lateinit var logText: TextView

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            log("Izin Shizuku diberikan.")
        } else {
            log("Izin Shizuku ditolak.")
        }
        refreshStatus()
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateStats()
            handler.postDelayed(this, 2000)
        }
    }

    // ---------- Kartu angka ----------

    private inner class StatCard(title: String) {
        val value = TextView(this@MainActivity)
        val sub = TextView(this@MainActivity)
        val bar = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal)
        val view = LinearLayout(this@MainActivity)

        init {
            value.textSize = 24f
            value.setTextColor(Color.WHITE)
            value.typeface = Typeface.DEFAULT_BOLD

            sub.textSize = 11f
            sub.setTextColor(Color.parseColor(subText))

            bar.max = 100
            bar.progressTintList = ColorStateList.valueOf(Color.parseColor(accent))
            bar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#22FFFFFF"))

            val top = LinearLayout(this@MainActivity)
            top.orientation = LinearLayout.HORIZONTAL
            top.gravity = Gravity.CENTER_VERTICAL

            val titleView = TextView(this@MainActivity)
            titleView.text = title
            titleView.textSize = 10f
            titleView.setTextColor(Color.parseColor(subText))
            top.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(dot(green), LinearLayout.LayoutParams(dp(6), dp(6)))
            top.addView(dot(accent), LinearLayout.LayoutParams(dp(6), dp(6)))

            view.orientation = LinearLayout.VERTICAL
            view.setPadding(dp(14), dp(12), dp(14), dp(12))
            view.background = glossy(20, "#2E2F3A", "#22232B", "#33FFFFFF")
            view.elevation = dp(8).toFloat()
            view.addView(top)
            view.addView(value)
            view.addView(sub)

            val barParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4))
            barParams.topMargin = dp(10)
            view.addView(bar, barParams)
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
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(14), dp(34), dp(14), dp(40))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "Hiröxware"
        title.textSize = 22f
        title.setTextColor(Color.parseColor(accent))
        title.typeface = Typeface.DEFAULT_BOLD
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(makePill(Build.MODEL, accent))
        root.addView(header)

        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.HORIZONTAL
        hero.gravity = Gravity.CENTER_VERTICAL
        hero.setPadding(dp(14), dp(14), dp(14), dp(14))
        hero.background = glossy(24, "#2E2F3A", "#1B1C24", "#44FFFFFF")
        hero.elevation = dp(10).toFloat()

        val logo = TextView(this)
        logo.text = "🐰"
        logo.textSize = 26f
        logo.gravity = Gravity.CENTER
        logo.background = glossy(20, "#3A1F28", "#261720", accent)
        hero.addView(logo, LinearLayout.LayoutParams(dp(56), dp(56)))

        val heroText = LinearLayout(this)
        heroText.orientation = LinearLayout.VERTICAL
        heroText.setPadding(dp(12), 0, dp(8), 0)
        val heroName = TextView(this)
        heroName.text = "HIRÖXWARE"
        heroName.textSize = 15f
        heroName.typeface = Typeface.DEFAULT_BOLD
        heroName.setTextColor(Color.WHITE)
        heroText.addView(heroName)
        statusText = TextView(this)
        statusText.textSize = 11f
        statusText.setTextColor(Color.parseColor(subText))
        heroText.addView(statusText)
        hero.addView(heroText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        statusPill = makePill("...", accent)
        hero.addView(statusPill)

        root.addView(hero, topMargin(12))
        tiltTargets.add(hero)

        activeValue = makeValue("0")
        shizukuValue = makeValue("...")
        val stats = LinearLayout(this)
        stats.orientation = LinearLayout.HORIZONTAL
        stats.setPadding(dp(16), dp(12), dp(16), dp(12))
        stats.background = glossy(20, "#2E2F3A", "#1B1C24", "#33FFFFFF")
        stats.addView(makeColumn("FITUR AKTIF", activeValue), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        stats.addView(makeColumn("SHIZUKU", shizukuValue), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(stats, topMargin(12))
        tiltTargets.add(stats)

        ramCard = StatCard("RAM")
        batteryCard = StatCard("BATERAI")
        rateCard = StatCard("LAYAR")
        storageCard = StatCard("PENYIMPANAN")
        root.addView(makeRow(ramCard.view, batteryCard.view))
        root.addView(makeRow(rateCard.view, storageCard.view))
        tiltTargets.add(ramCard.view)
        tiltTargets.add(batteryCard.view)
        tiltTargets.add(rateCard.view)
        tiltTargets.add(storageCard.view)

        root.addView(makeLabel("FITUR"), topMargin(22))
        for (f in features) {
            root.addView(makeFeatureCard(f), topMargin(12))
        }

        root.addView(makeButton("Revert ke Stok") { revertAll() }, topMargin(30))
        root.addView(makeButton("Izinkan Shizuku") { requestShizuku() }, topMargin(22))

        logText = TextView(this)
        logText.textSize = 12f
        logText.setTextColor(Color.parseColor(subText))
        logText.setPadding(0, dp(16), 0, 0)
        root.addView(logText)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor(background))
        scroll.addView(root)
        return scroll
    }

    private fun makeFeatureCard(f: Feature): LinearLayout {
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = f.title
        t.textSize = 15f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setTextColor(Color.WHITE)
        texts.addView(t)
        val s = TextView(this)
        s.text = f.sub
        s.textSize = 11f
        s.setTextColor(Color.parseColor(subText))
        texts.addView(s)

        val badge = TextView(this)
        badge.textSize = 11f
        badge.typeface = Typeface.DEFAULT_BOLD
        badge.gravity = Gravity.CENTER
        badge.setPadding(dp(14), dp(6), dp(14), dp(6))
        badges[f.id] = badge

        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(16), dp(16), dp(16), dp(16))
        card.elevation = dp(12).toFloat()
        card.isClickable = true
        card.setOnClickListener { toggle(f.id) }
        card.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(badge)

        cards[f.id] = card
        tiltTargets.add(card)
        return card
    }

    private fun refreshToggles() {
        for (f in features) {
            val on = isOn(f.id)
            val badge = badges[f.id]
            if (badge != null) {
                badge.text = if (on) "ON" else "OFF"
                badge.setTextColor(Color.parseColor(if (on) "#FFFFFF" else subText))
                badge.background = if (on) {
                    glossy(14, "#FF2D55", "#D41E44", "#FF7A93")
                } else {
                    glossy(14, "#33FFFFFF", "#22FFFFFF", "#44FFFFFF")
                }
            }
            val card = cards[f.id]
            if (card != null) {
                card.background = if (on) {
                    glossy(22, "#3A1F28", "#261720", accent)
                } else {
                    glossy(22, "#2E2F3A", "#22232B", "#33FFFFFF")
                }
            }
        }
        var count = 0
        for (f in features) {
            if (isOn(f.id)) count++
        }
        activeValue.text = count.toString()
    }

    private fun makeButton(label: String, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.textSize = 16f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setTextColor(Color.WHITE)
        tv.minHeight = dp(62)
        tv.setPadding(dp(18), dp(20), dp(18), dp(20))
        tv.background = glossy(20, "#4A4C5C", "#262731", "#80FFFFFF")
        tv.elevation = dp(18).toFloat()
        tv.isClickable = true
        tv.setOnClickListener { onClick() }
        tiltTargets.add(tv)
        return tv
    }

    private fun makePill(label: String, color: String): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = 9f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setTextColor(Color.parseColor(color))
        tv.setPadding(dp(8), dp(4), dp(8), dp(4))
        tv.background = glossy(12, "#2A2B35", "#1E1F27", color)
        return tv
    }

    private fun makeValue(v: String): TextView {
        val tv = TextView(this)
        tv.text = v
        tv.textSize = 16f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setTextColor(Color.WHITE)
        return tv
    }

    private fun makeColumn(label: String, value: TextView): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val l = TextView(this)
        l.text = label
        l.textSize = 9f
        l.setTextColor(Color.parseColor(subText))
        col.addView(l)
        col.addView(value)
        return col
    }

    private fun makeLabel(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 11f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setTextColor(Color.parseColor(subText))
        return tv
    }

    private fun makeRow(left: View, right: View): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val lp1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp1.setMargins(0, dp(10), dp(5), 0)
        row.addView(left, lp1)
        val lp2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp2.setMargins(dp(5), dp(10), 0, 0)
        row.addView(right, lp2)
        return row
    }

    private fun topMargin(px: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(px)
        return lp
    }

    private fun glossy(radiusDp: Int, top: String, bottom: String, stroke: String): GradientDrawable {
        val d = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor(top), Color.parseColor(bottom))
        )
        d.cornerRadius = dp(radiusDp).toFloat()
        d.setStroke(dp(1), Color.parseColor(stroke))
        return d
    }

    private fun dot(hex: String): View {
        val v = View(this)
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(Color.parseColor(hex))
        v.background = d
        return v
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
        ramCard.value.text = String.format("%.1fGB", (mi.totalMem - mi.availMem) / 1e9)
        ramCard.sub.text = String.format("%.1fGB total", mi.totalMem / 1e9)
        ramCard.bar.progress = usedPct

        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        batteryCard.value.text = "$level%"
        batteryCard.sub.text = "Level saat ini"
        batteryCard.bar.progress = level

        val hz = windowManager.defaultDisplay.refreshRate
        rateCard.value.text = String.format("%.0fHz", hz)
        rateCard.sub.text = "Refresh rate saat ini"
        rateCard.bar.progress = Math.min(100, (hz / 1.2f).toInt())

        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        storageCard.value.text = String.format("%.0fGB", (total - free) / 1e9)
        storageCard.sub.text = String.format("%.0fGB total, %.0fGB kosong", total / 1e9, free / 1e9)
        storageCard.bar.progress = ((total - free) * 100 / total).toInt()
    }

    private fun refreshStatus() {
        val alive = shizukuBinderAlive()
        val granted = alive && shizukuGranted()

        if (!alive) {
            statusText.text = "Shizuku belum aktif. Buka Shizuku dulu."
            statusPill.text = "MATI"
            statusPill.setTextColor(Color.parseColor(accent))
            shizukuValue.text = "Mati"
        } else if (!granted) {
            statusText.text = "Shizuku aktif, tapi izin belum diberikan."
            statusPill.text = "IZIN"
            statusPill.setTextColor(Color.parseColor(accent))
            shizukuValue.text = "Belum izin"
        } else {
            statusText.text = "Shizuku siap. Tanpa root."
            statusPill.text = "SIAP"
            statusPill.setTextColor(Color.parseColor(green))
            shizukuValue.text = "Siap"
        }
    }

    // ---------- Shizuku ----------

    private fun shizukuBinderAlive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    private fun shizukuGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
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

    private fun runShell(cmd: String): Boolean {
        return try {
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
    }

    // ---------- Nyala / mati fitur ----------

    private fun isOn(id: String): Boolean = prefs.getBoolean("on_$id", false)

    private fun toggle(id: String) {
        if (!ready()) {
            log("Aktifkan Shizuku dan izinkan dulu.")
            return
        }
        prefs.edit().putBoolean("on_$id", !isOn(id)).apply()
        refreshToggles()
        applyAll(false)
    }

    private fun saveOriginals() {
        val cr = contentResolver
        val edit = prefs.edit()
        for (f in features) {
            for (s in f.settings) {
                val name = "orig_${s.ns}_${s.key}"
                if (prefs.contains(name)) continue
                val value = if (s.ns == "global") {
                    Settings.Global.getString(cr, s.key)
                } else {
                    Settings.System.getString(cr, s.key)
                }
                edit.putString(name, value ?: "null")
            }
        }
        edit.apply()
    }

    private fun restoreCmd(s: Setting): String? {
        val stored = prefs.getString("orig_${s.ns}_${s.key}", null) ?: return null
        
