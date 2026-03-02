package com.dajiraj.screentime

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var gson: Gson
    private var isHindi = false
    private var currentPage = "home"

    // Views - Persistent header
    private lateinit var tvHeaderTime: TextView
    private lateinit var tvHeaderGoal: TextView
    private lateinit var progressRing: ProgressBar
    private lateinit var tvStreakChip: TextView
    private lateinit var btnLang: Button

    // Views - Nav
    private lateinit var navHome: LinearLayout
    private lateinit var navStats: LinearLayout
    private lateinit var navCal: LinearLayout
    private lateinit var navReview: LinearLayout

    // Pages
    private lateinit var pageHome: ScrollView
    private lateinit var pageStats: ScrollView
    private lateinit var pageCal: ScrollView
    private lateinit var pageReview: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("dst_v3", Context.MODE_PRIVATE)
        gson = Gson()
        isHindi = prefs.getBoolean("lang_hindi", false)

        // Check if usage access granted
        if (!hasUsageAccess()) {
            showUsagePermissionDialog()
        } else {
            startScreenTimeService()
        }

        setContentView(R.layout.activity_main)
        bindViews()
        setupNav()
        renderHome()
        updateHeader()
    }

    override fun onResume() {
        super.onResume()
        updateHeader()
        if (currentPage == "home") renderHome()
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showUsagePermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(if (isHindi) "अनुमति चाहिए" else "Permission Required")
            .setMessage(
                if (isHindi)
                    "ऐप को स्क्रीन टाइम ऑटोमैटिक पढ़ने के लिए 'Usage Access' की ज़रूरत है।\n\n" +
                    "Settings > Apps > Special App Access > Usage Access > ScreenTime ON करें।"
                else
                    "This app needs 'Usage Access' permission to automatically read your screen time.\n\n" +
                    "Settings → Apps → Special App Access → Usage Access → Enable ScreenTime"
            )
            .setPositiveButton(if (isHindi) "सेटिंग्स खोलें" else "Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton(if (isHindi) "बाद में" else "Later", null)
            .setCancelable(false)
            .show()
    }

    private fun startScreenTimeService() {
        val intent = Intent(this, ScreenTimeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun bindViews() {
        tvHeaderTime = findViewById(R.id.tvHeaderTime)
        tvHeaderGoal = findViewById(R.id.tvHeaderGoal)
        progressRing = findViewById(R.id.progressRing)
        tvStreakChip = findViewById(R.id.tvStreakChip)
        btnLang = findViewById(R.id.btnLang)

        navHome = findViewById(R.id.navHome)
        navStats = findViewById(R.id.navStats)
        navCal = findViewById(R.id.navCal)
        navReview = findViewById(R.id.navReview)

        pageHome = findViewById(R.id.pageHome)
        pageStats = findViewById(R.id.pageStats)
        pageCal = findViewById(R.id.pageCal)
        pageReview = findViewById(R.id.pageReview)

        btnLang.setOnClickListener { toggleLang() }
        btnLang.text = if (isHindi) "EN" else "हिंदी"

        // Long press header to set goal
        val header = findViewById<LinearLayout>(R.id.persistHeader)
        header.setOnLongClickListener { showGoalDialog(); true }
    }

    private fun setupNav() {
        navHome.setOnClickListener { showPage("home") }
        navStats.setOnClickListener { showPage("stats") }
        navCal.setOnClickListener { showPage("cal") }
        navReview.setOnClickListener { showPage("review") }
    }

    private fun showPage(page: String) {
        currentPage = page
        pageHome.visibility = if (page == "home") View.VISIBLE else View.GONE
        pageStats.visibility = if (page == "stats") View.VISIBLE else View.GONE
        pageCal.visibility = if (page == "cal") View.VISIBLE else View.GONE
        pageReview.visibility = if (page == "review") View.VISIBLE else View.GONE

        // Nav highlight
        val navs = listOf(
            Pair(navHome, "home"), Pair(navStats, "stats"),
            Pair(pageCal, "cal"), Pair(pageReview, "review")
        )
        listOf(navHome, navStats, navCal, navReview).forEach { nav ->
            nav.alpha = 0.4f
        }
        when (page) {
            "home" -> navHome.alpha = 1f
            "stats" -> navStats.alpha = 1f
            "cal" -> navCal.alpha = 1f
            "review" -> navReview.alpha = 1f
        }

        when (page) {
            "home" -> renderHome()
            "stats" -> renderStats()
            "cal" -> renderCalendar()
            "review" -> renderReview()
        }
    }

    fun updateHeader() {
        val usage = UsageHelper.getTodayTotalMinutes(this)
        val goal = prefs.getFloat("goal_hours", 4f)
        val goalMins = (goal * 60).toInt()
        val pct = ((usage.toFloat() / goalMins) * 100).coerceIn(0f, 100f)

        val h = usage / 60
        val m = usage % 60
        tvHeaderTime.text = "$h:${m.toString().padStart(2,'0')}"
        tvHeaderTime.setTextColor(getUsageColor(usage, goalMins))

        tvHeaderGoal.text = if (isHindi) "आज · लक्ष्य ${goal.toInt()}घं" else "TODAY · GOAL ${goal.toInt()}H"
        progressRing.progress = pct.toInt()

        val streak = calcStreak()
        if (streak >= 2) {
            tvStreakChip.visibility = View.VISIBLE
            tvStreakChip.text = "🔥 ${streak}${if (isHindi) "दिन" else "d"}"
        } else {
            tvStreakChip.visibility = View.GONE
        }
    }

    private fun renderHome() {
        val container = findViewById<LinearLayout>(R.id.homeContainer)
        container.removeAllViews()

        val usage = UsageHelper.getTodayTotalMinutes(this)
        val goal = prefs.getFloat("goal_hours", 4f)
        val goalMins = (goal * 60).toInt()
        val streak = calcStreak()

        // Reward banner
        val reward = getRewardMessage(usage, goalMins, streak)
        if (reward != null) {
            addRewardBanner(container, reward.first, reward.second)
        }

        // Main hero card
        addHeroCard(container, usage, goalMins, goal)

        // Hourly breakdown
        addHourlyCard(container)

        // Top apps today
        addTopAppsCard(container)

        // 7-day bar
        addWeekCard(container)
    }

    private fun addRewardBanner(container: LinearLayout, emoji: String, message: String) {
        val card = createCard(container)
        card.setBackgroundColor(Color.parseColor("#0F2A1A"))
        card.setPadding(dp(16), dp(14), dp(16), dp(14))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_VERTICAL

        val emojiTv = TextView(this)
        emojiTv.text = emoji
        emojiTv.textSize = 28f
        emojiTv.setPadding(0, 0, dp(12), 0)

        val msgTv = TextView(this)
        msgTv.text = message
        msgTv.setTextColor(Color.parseColor("#22C55E"))
        msgTv.textSize = 13f
        msgTv.setTypeface(null, android.graphics.Typeface.BOLD)

        row.addView(emojiTv)
        row.addView(msgTv)
        card.addView(row)
        container.addView(card)
    }

    private fun addHeroCard(container: LinearLayout, usage: Int, goalMins: Int, goal: Float) {
        val card = createCard(container)
        val color = getUsageColor(usage, goalMins)

        val h = usage / 60
        val m = usage % 60

        // Big time display
        val timeTv = TextView(this)
        timeTv.text = "$h:${m.toString().padStart(2,'0')}"
        timeTv.textSize = 64f
        timeTv.setTextColor(Color.parseColor(colorToHex(color)))
        timeTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        timeTv.gravity = android.view.Gravity.CENTER
        card.addView(timeTv)

        // Sub label
        val subTv = TextView(this)
        subTv.text = if (isHindi) "आज का स्क्रीन टाइम (ऑटो)" else "TODAY'S SCREEN TIME (AUTO-TRACKED)"
        subTv.setTextColor(Color.parseColor("#72708A"))
        subTv.textSize = 10f
        subTv.gravity = android.view.Gravity.CENTER
        subTv.setPadding(0, 0, 0, dp(12))
        card.addView(subTv)

        // Goal bar
        val barLabel = LinearLayout(this)
        barLabel.orientation = LinearLayout.HORIZONTAL

        val barLeft = TextView(this); barLeft.text = "0"; barLeft.textSize = 10f; barLeft.setTextColor(Color.parseColor("#72708A"))
        val barMid = TextView(this); barMid.text = if(isHindi) "लक्ष्य ${goal.toInt()}घं" else "GOAL ${goal.toInt()}H"; barMid.textSize = 10f; barMid.setTextColor(Color.parseColor("#72708A")); barMid.gravity = android.view.Gravity.CENTER
        val barRight = TextView(this); barRight.text = "${goal.toInt()*2}H"; barRight.textSize = 10f; barRight.setTextColor(Color.parseColor("#72708A")); barRight.gravity = android.view.Gravity.END

        barLabel.addView(barLeft, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        barLabel.addView(barMid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        barLabel.addView(barRight, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(barLabel)

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.max = 100
        val pct = ((usage.toFloat() / goalMins) * 100).coerceIn(0f, 100f).toInt()
        progressBar.progress = pct
        progressBar.progressDrawable.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
        val barParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
        barParams.setMargins(0, dp(4), 0, 0)
        card.addView(progressBar, barParams)

        container.addView(card)
    }

    private fun addHourlyCard(container: LinearLayout) {
        val hourlyData = UsageHelper.getHourlyBreakdownToday(this)
        val card = createCard(container)

        addCardTitle(card, if (isHindi) "घंटे के हिसाब से आज" else "HOURLY BREAKDOWN · TODAY")

        val chartContainer = LinearLayout(this)
        chartContainer.orientation = LinearLayout.HORIZONTAL
        chartContainer.gravity = android.view.Gravity.BOTTOM
        val chartParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60))
        chartContainer.layoutParams = chartParams

        val maxVal = hourlyData.maxOrNull()?.coerceAtLeast(1) ?: 1
        val curHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val goalPerHour = ((prefs.getFloat("goal_hours", 4f) * 60) / 24).toInt()

        for (i in 0 until 24) {
            val val_ = hourlyData.getOrElse(i) { 0 }
            val barH = if (val_ > 0) ((val_.toFloat() / maxVal) * 56).toInt().coerceAtLeast(4) else 2

            val bar = View(this)
            val color = if (val_ > 0) getUsageColor(val_, goalPerHour) else Color.parseColor("#252535")
            bar.setBackgroundColor(color)

            val barParams = LinearLayout.LayoutParams(0, barH, 1f)
            barParams.setMargins(1, 0, 1, 0)
            if (i == curHour) {
                // Current hour highlighted
                bar.alpha = 1f
            } else {
                bar.alpha = if (val_ > 0) 0.85f else 0.4f
            }
            chartContainer.addView(bar, barParams)
        }
        card.addView(chartContainer)

        // Hour labels
        val labelRow = LinearLayout(this)
        labelRow.orientation = LinearLayout.HORIZONTAL
        val labelParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        labelParams.setMargins(0, dp(4), 0, 0)
        labelRow.layoutParams = labelParams

        listOf("12A", "6A", "12P", "6P", "11P").forEach { label ->
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 8f
            tv.setTextColor(Color.parseColor("#4A4860"))
            tv.setTypeface(null, android.graphics.Typeface.BOLD)
            tv.gravity = android.view.Gravity.CENTER
            labelRow.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addView(labelRow)
        container.addView(card)
    }

    private fun addTopAppsCard(container: LinearLayout) {
        val topApps = UsageHelper.getTopAppsToday(this, 5)
        val card = createCard(container)
        addCardTitle(card, if (isHindi) "आज के टॉप ऐप्स" else "TOP APPS TODAY")

        if (topApps.isEmpty()) {
            val noData = TextView(this)
            noData.text = if (isHindi) "डेटा लोड हो रहा है..." else "Loading usage data..."
            noData.setTextColor(Color.parseColor("#4A4860"))
            noData.textSize = 12f
            noData.gravity = android.view.Gravity.CENTER
            noData.setPadding(0, dp(12), 0, dp(4))
            card.addView(noData)
        } else {
            val maxTime = topApps.maxOf { it.second }.coerceAtLeast(1)
            topApps.forEach { (appName, mins) ->
                addAppRow(card, appName, mins, maxTime)
            }
        }
        container.addView(card)
    }

    private fun addAppRow(container: LinearLayout, appName: String, mins: Int, maxMins: Int) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        val rowParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rowParams.setMargins(0, dp(6), 0, dp(6))
        row.layoutParams = rowParams

        val nameTv = TextView(this)
        nameTv.text = appName
        nameTv.setTextColor(Color.parseColor("#E4E0F4"))
        nameTv.textSize = 12f
        nameTv.setTypeface(null, android.graphics.Typeface.BOLD)

        val barOuter = FrameLayout(this)
        barOuter.setBackgroundColor(Color.parseColor("#15151E"))
        val barParams = LinearLayout.LayoutParams(0, dp(5), 1f)
        barParams.setMargins(dp(8), 0, dp(8), 0)
        barOuter.layoutParams = barParams
        // Wrap in a layout for proper width
        val barOuterWrap = LinearLayout(this)
        barOuterWrap.orientation = LinearLayout.HORIZONTAL
        val barFill = View(this)
        barFill.setBackgroundColor(Color.parseColor("#FF9933"))
        barOuterWrap.addView(barFill, LinearLayout.LayoutParams((mins.toFloat()/maxMins * 200).toInt().coerceAtLeast(4), dp(5)))
        barOuter.addView(barOuterWrap)

        val timeTv = TextView(this)
        val h = mins / 60
        val m = mins % 60
        timeTv.text = if (h > 0) "${h}h${m}m" else "${m}m"
        timeTv.setTextColor(Color.parseColor("#FF9933"))
        timeTv.textSize = 12f

        row.addView(nameTv, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(barOuter)
        row.addView(timeTv, LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT))
        container.addView(row)
    }

    private fun addWeekCard(container: LinearLayout) {
        val card = createCard(container)
        addCardTitle(card, if (isHindi) "पिछले 7 दिन" else "LAST 7 DAYS")

        val goalMins = (prefs.getFloat("goal_hours", 4f) * 60).toInt()
        val barsRow = LinearLayout(this)
        barsRow.orientation = LinearLayout.HORIZONTAL
        barsRow.gravity = android.view.Gravity.BOTTOM
        val barsParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(80))
        barsRow.layoutParams = barsParams

        val today = Calendar.getInstance()
        val weekData = mutableListOf<Triple<String, Int, Boolean>>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dowLabels = arrayOf("S","M","T","W","T","F","S")
        val dowHi = arrayOf("र","स","मं","बु","गु","शु","श")

        for (i in 6 downTo 0) {
            val d = Calendar.getInstance()
            d.add(Calendar.DAY_OF_YEAR, -i)
            val key = sdf.format(d.time)
            val mins = prefs.getInt("day_$key", -1)
            val label = if (isHindi) dowHi[d.get(Calendar.DAY_OF_WEEK)-1] else dowLabels[d.get(Calendar.DAY_OF_WEEK)-1]
            weekData.add(Triple(label, if(mins == -1) 0 else mins, i == 0))
        }

        val maxV = weekData.maxOf { it.second }.coerceAtLeast(goalMins)

        weekData.forEach { (label, mins, isToday) ->
            val colWrap = LinearLayout(this)
            colWrap.orientation = LinearLayout.VERTICAL
            colWrap.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            val colParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            colWrap.layoutParams = colParams

            val bar = View(this)
            val barH = if (mins > 0) ((mins.toFloat()/maxV)*68).toInt().coerceAtLeast(4) else 3
            val barColor = if (mins > 0) getUsageColor(mins, goalMins) else Color.parseColor("#252535")
            bar.setBackgroundColor(barColor)
            val bParams = LinearLayout.LayoutParams(dp(if(isToday) 20 else 16), barH)
            bParams.setMargins(dp(2), 0, dp(2), 0)
            bar.layoutParams = bParams
            colWrap.addView(bar)

            val lTv = TextView(this)
            lTv.text = label
            lTv.textSize = 9f
            lTv.setTextColor(if(isToday) Color.parseColor("#FF9933") else Color.parseColor("#4A4860"))
            lTv.setTypeface(null, if(isToday) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            lTv.gravity = android.view.Gravity.CENTER
            val ltParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            ltParams.setMargins(0, dp(3), 0, 0)
            lTv.layoutParams = ltParams
            colWrap.addView(lTv)

            if (mins > 0) {
                val vTv = TextView(this)
                val h = mins/60; val m = mins%60
                vTv.text = if(h>0) "${h}h" else "${m}m"
                vTv.textSize = 7f
                vTv.setTextColor(Color.parseColor("#4A4860"))
                vTv.gravity = android.view.Gravity.CENTER
                colWrap.addView(vTv)
            }

            barsRow.addView(colWrap)
        }
        card.addView(barsRow)
        container.addView(card)
    }

    private fun renderStats() {
        val container = findViewById<LinearLayout>(R.id.statsContainer)
        container.removeAllViews()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val allKeys = prefs.all.keys.filter { it.startsWith("day_") }.sortedDescending()
        val goalMins = (prefs.getFloat("goal_hours", 4f) * 60).toInt()

        // Summary row
        val card = createCard(container)
        addCardTitle(card, if (isHindi) "सारांश" else "SUMMARY")

        if (allKeys.isEmpty()) {
            val tv = TextView(this)
            tv.text = if (isHindi) "डेटा लोड हो रहा है..." else "Collecting data... check back soon."
            tv.setTextColor(Color.parseColor("#72708A"))
            tv.textSize = 13f
            tv.gravity = android.view.Gravity.CENTER
            tv.setPadding(0, dp(16), 0, dp(8))
            card.addView(tv)
            container.addView(card)
            return
        }

        val vals = allKeys.map { prefs.getInt(it, 0) }.filter { it > 0 }
        val avg = if (vals.isNotEmpty()) vals.average().toInt() else 0
        val streak = calcStreak()
        val goodDays = vals.count { it <= goalMins }

        addStatRow(card, if(isHindi) "दैनिक औसत" else "Daily Average", fmm(avg))
        addStatRow(card, if(isHindi) "अच्छे दिनों की streak" else "Good Day Streak", "$streak ${if(isHindi)"दिन" else "days"}")
        addStatRow(card, if(isHindi) "लक्ष्य के भीतर दिन" else "Days Under Goal", "$goodDays of ${vals.size}")
        addStatRow(card, if(isHindi) "सबसे ज़्यादा" else "Worst Day", fmm(vals.maxOrNull() ?: 0))
        addStatRow(card, if(isHindi) "सबसे कम" else "Best Day", fmm(vals.minOrNull() ?: 0))
        container.addView(card)

        // Hourly pattern card
        val hourCard = createCard(container)
        addCardTitle(hourCard, if (isHindi) "सबसे ज़्यादा उपयोग के घंटे (30 दिन)" else "PEAK HOURS (30-DAY PATTERN)")
        // Aggregate hourly from last 30 days
        val aggHours = IntArray(24) { 0 }
        val last30 = allKeys.take(30)
        var cnt = 0
        last30.forEach { key ->
            val dateStr = key.removePrefix("day_")
            for (h in 0 until 24) {
                aggHours[h] += prefs.getInt("hour_${dateStr}_$h", 0)
            }
            cnt++
        }
        val avgHours = if (cnt > 0) aggHours.map { it / cnt } else IntArray(24).toList()

        // Draw chart
        val chartRow = LinearLayout(this)
        chartRow.orientation = LinearLayout.HORIZONTAL
        chartRow.gravity = android.view.Gravity.BOTTOM
        val chartParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(70))
        chartRow.layoutParams = chartParams
        val maxH = avgHours.maxOrNull()?.coerceAtLeast(1) ?: 1
        val goalPerH = goalMins / 24
        for (i in 0 until 24) {
            val v = avgHours[i]
            val barH = if (v > 0) ((v.toFloat()/maxH)*66).toInt().coerceAtLeast(3) else 2
            val bar = View(this)
            bar.setBackgroundColor(if(v > 0) getUsageColor(v, goalPerH) else Color.parseColor("#252535"))
            val bp = LinearLayout.LayoutParams(0, barH, 1f)
            bp.setMargins(1,0,1,0)
            chartRow.addView(bar, bp)
        }
        hourCard.addView(chartRow)

        val peakH = avgHours.indexOf(avgHours.max())
        val peakLabel = when {
            peakH == 0 -> "12AM"
            peakH < 12 -> "${peakH}AM"
            peakH == 12 -> "12PM"
            else -> "${peakH-12}PM"
        }
        val peakTv = TextView(this)
        peakTv.text = if(isHindi) "सबसे ज़्यादा उपयोग: $peakLabel के आसपास" else "Peak usage around $peakLabel"
        peakTv.setTextColor(Color.parseColor("#72708A"))
        peakTv.textSize = 11f
        peakTv.gravity = android.view.Gravity.CENTER
        peakTv.setPadding(0, dp(6), 0, 0)
        hourCard.addView(peakTv)
        container.addView(hourCard)
    }

    private fun renderCalendar() {
        // Calendar implementation in CalendarHelper
        val container = findViewById<LinearLayout>(R.id.calContainer)
        container.removeAllViews()

        val calTitle = TextView(this)
        calTitle.text = if(isHindi) "इस महीने की एंट्रीज़" else "THIS MONTH'S ENTRIES"
        calTitle.setTextColor(Color.parseColor("#72708A"))
        calTitle.textSize = 11f
        calTitle.setTypeface(null, android.graphics.Typeface.BOLD)
        calTitle.letterSpacing = 0.15f
        calTitle.setPadding(0, 0, 0, dp(12))
        container.addView(calTitle)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val goalMins = (prefs.getFloat("goal_hours", 4f) * 60).toInt()

        // All days this month
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (day in 1..daysInMonth) {
            val d = Calendar.getInstance()
            d.set(year, month, day)
            if (d.after(Calendar.getInstance())) break
            val key = sdf.format(d.time)
            val mins = prefs.getInt("day_$key", -1)
            if (mins < 0) continue

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            val rParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
            rParams.setMargins(0, 0, 0, dp(8))
            row.layoutParams = rParams
            row.setBackgroundColor(Color.parseColor("#12121A"))
            row.setPadding(dp(14), 0, dp(14), 0)
            // Round corners via background
            val bg = android.graphics.drawable.GradientDrawable()
            bg.cornerRadius = dp(14).toFloat()
            bg.setColor(Color.parseColor("#12121A"))
            row.background = bg
            row.setOnClickListener { showDayDetail(key, mins) }

            val dayTv = TextView(this)
            dayTv.text = "$day"
            dayTv.setTextColor(getUsageColor(mins, goalMins))
            dayTv.textSize = 22f
            dayTv.setTypeface(null, android.graphics.Typeface.BOLD)
            dayTv.setPadding(0, 0, dp(14), 0)
            row.addView(dayTv)

            val timeTv = TextView(this)
            val h = mins/60; val m = mins%60
            timeTv.text = "${h}:${m.toString().padStart(2,'0')}"
            timeTv.setTextColor(getUsageColor(mins, goalMins))
            timeTv.textSize = 22f
            timeTv.setTypeface(null, android.graphics.Typeface.BOLD)
            row.addView(timeTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val pct = ((mins.toFloat()/goalMins)*100).coerceIn(0f,100f).toInt()
            val goalTv = TextView(this)
            goalTv.text = if(mins <= goalMins) "✓" else "$pct%"
            goalTv.setTextColor(if(mins <= goalMins) Color.parseColor("#22C55E") else Color.parseColor("#EF4444"))
            goalTv.textSize = 14f
            goalTv.setTypeface(null, android.graphics.Typeface.BOLD)
            row.addView(goalTv)

            container.addView(row)
        }
    }

    private fun showDayDetail(key: String, totalMins: Int) {
        val goalMins = (prefs.getFloat("goal_hours", 4f) * 60).toInt()
        val h = totalMins/60; val m = totalMins%60
        val pct = ((totalMins.toFloat()/goalMins)*100).toInt()
        val msg = "$key\n\n⏱ Total: ${h}h ${m}m\n📊 Goal: $pct%\n\n" +
            if(isHindi) "Tap करें विस्तार देखने के लिए।" else "Tap outside to close."
        AlertDialog.Builder(this)
            .setTitle("$key — ${h}h ${m}m")
            .setMessage(if(isHindi) "कुल समय: ${h} घंटे ${m} मिनट\nलक्ष्य: $pct%\n${if(totalMins<=goalMins)"✅ लक्ष्य के भीतर" else "⚠️ लक्ष्य से ज़्यादा"}"
                else "Total: ${h}h ${m}m\nGoal: $pct%\n${if(totalMins<=goalMins)"✅ Under goal" else "⚠️ Over goal"}")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun renderReview() {
        val container = findViewById<LinearLayout>(R.id.reviewContainer)
        container.removeAllViews()

        val year = Calendar.getInstance().get(Calendar.YEAR)
        val goalMins = (prefs.getFloat("goal_hours", 4f) * 60).toInt()
        val allKeys = prefs.all.keys.filter { it.startsWith("day_$year") }
        val vals = allKeys.map { prefs.getInt(it, 0) }.filter { it > 0 }

        val heroCard = createCard(container)
        heroCard.setBackgroundColor(Color.parseColor("#0F0F14"))
        val totalMins = vals.sum()
        val totalH = totalMins / 60

        val bigTv = TextView(this)
        bigTv.text = "${totalH}H"
        bigTv.setTextColor(Color.parseColor("#FF9933"))
        bigTv.textSize = 56f
        bigTv.setTypeface(null, android.graphics.Typeface.BOLD)
        bigTv.gravity = android.view.Gravity.CENTER
        heroCard.addView(bigTv)

        val subTv = TextView(this)
        subTv.text = if(isHindi) "${vals.size} दिन रिकॉर्ड · $year" else "${vals.size} DAYS RECORDED · $year"
        subTv.setTextColor(Color.parseColor("#72708A"))
        subTv.textSize = 11f
        subTv.gravity = android.view.Gravity.CENTER
        subTv.letterSpacing = 0.12f
        heroCard.addView(subTv)
        container.addView(heroCard)

        if (vals.isNotEmpty()) {
            val avg = vals.average().toInt()
            val max = vals.max()
            val min = vals.min()
            val good = vals.count { it <= goalMins }

            val statsCard = createCard(container)
            addCardTitle(statsCard, if(isHindi) "साल की जानकारी" else "YEAR AT A GLANCE")
            addStatRow(statsCard, if(isHindi) "दैनिक औसत" else "Daily Average", fmm(avg))
            addStatRow(statsCard, if(isHindi) "सबसे खराब दिन" else "Worst Day", fmm(max))
            addStatRow(statsCard, if(isHindi) "सबसे अच्छा दिन" else "Best Day", fmm(min))
            addStatRow(statsCard, if(isHindi) "लक्ष्य के भीतर" else "Good Days", "$good / ${vals.size}")
            container.addView(statsCard)

            // Dec 31 message
            val dec31Card = createCard(container)
            val pctGood = (good.toFloat()/vals.size*100).toInt()
            val dec31Tv = TextView(this)
            dec31Tv.text = if(isHindi)
                "📅 31 दिसंबर को याद रखें:\n\n$year में $totalH घंटे फोन पर। दैनिक औसत: ${fmm(avg)}।\n\n${pctGood}% दिन लक्ष्य के भीतर — ${when{pctGood>=70 -> "🏆 शानदार!" pctGood>=50 -> "💪 अच्छी प्रगति!" else -> "🌱 अगले साल और बेहतर!"}}"
            else
                "📅 Dec 31 Year-End:\n\nIn $year, you spent $totalH hours on phone. Daily avg: ${fmm(avg)}.\n\n$pctGood% days under goal — ${when{pctGood>=70 -> "🏆 Remarkable!" pctGood>=50 -> "💪 Good progress!" else -> "🌱 Better next year!"}}"
            dec31Tv.setTextColor(Color.parseColor("#E4E0F4"))
            dec31Tv.textSize = 13f
            dec31Tv.lineSpacingMultiplier = 1.5f
            dec31Card.addView(dec31Tv)
            container.addView(dec31Card)
        }
    }

    // ---- Helpers ----

    private fun createCard(parent: LinearLayout): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        val bg = android.graphics.drawable.GradientDrawable()
        bg.cornerRadius = dp(20).toFloat()
        bg.setColor(Color.parseColor("#12121A"))
        card.background = bg
        card.setPadding(dp(16), dp(16), dp(16), dp(16))
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, dp(12))
        card.layoutParams = params
        return card
    }

    private fun addCardTitle(card: LinearLayout, text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor("#72708A"))
        tv.textSize = 11f
        tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.letterSpacing = 0.15f
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, dp(12))
        tv.layoutParams = params
        card.addView(tv)
    }

    private fun addStatRow(card: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, dp(10))
        row.layoutParams = params

        val lTv = TextView(this)
        lTv.text = label
        lTv.setTextColor(Color.parseColor("#72708A"))
        lTv.textSize = 13f

        val vTv = TextView(this)
        vTv.text = value
        vTv.setTextColor(Color.parseColor("#E4E0F4"))
        vTv.textSize = 15f
        vTv.setTypeface(null, android.graphics.Typeface.BOLD)
        vTv.gravity = android.view.Gravity.END

        row.addView(lTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(vTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(row)
    }

    private fun getUsageColor(mins: Int, goalMins: Int): Int {
        if (mins <= 0) return Color.parseColor("#72708A")
        val r = mins.toFloat() / goalMins
        return when {
            r <= 0.5f -> Color.parseColor("#22C55E")
            r <= 0.75f -> Color.parseColor("#84CC16")
            r <= 1.0f -> Color.parseColor("#FF9933")
            r <= 1.3f -> Color.parseColor("#F97316")
            r <= 1.6f -> Color.parseColor("#EF4444")
            r <= 2.0f -> Color.parseColor("#DC2626")
            r <= 2.5f -> Color.parseColor("#991B1B")
            else -> Color.parseColor("#7F1D1D")
        }
    }

    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }

    private fun getRewardMessage(usage: Int, goalMins: Int, streak: Int): Pair<String, String>? {
        return when {
            usage > 0 && usage <= goalMins / 2 -> Pair("🌱",
                if(isHindi) "आज फोन से ज़्यादा ज़िंदगी! सिर्फ ${fmm(usage)}" else "More life, less screen! Only ${fmm(usage)}")
            streak >= 7 -> Pair("🏆",
                if(isHindi) "$streak दिन से लक्ष्य के भीतर! शानदार!" else "$streak days under goal! Remarkable!")
            streak >= 3 -> Pair("🔥",
                if(isHindi) "शाबाश! $streak दिन से लक्ष्य के भीतर" else "Shabash! $streak days under goal")
            else -> null
        }
    }

    private fun calcStreak(): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val goalMins = (prefs.getFloat("goal_hours", 4f) * 60).toInt()
        var streak = 0
        for (i in 0 until 365) {
            val d = Calendar.getInstance()
            d.add(Calendar.DAY_OF_YEAR, -i)
            val key = "day_${sdf.format(d.time)}"
            val mins = prefs.getInt(key, -1)
            if (mins < 0) break
            if (mins <= goalMins) streak++ else break
        }
        return streak
    }

    private fun showGoalDialog() {
        val currentGoal = prefs.getFloat("goal_hours", 4f)
        val options = arrayOf("2 hours / 2 घंटे", "3 hours / 3 घंटे", "4 hours / 4 घंटे",
            "5 hours / 5 घंटे", "6 hours / 6 घंटे", "7 hours / 7 घंटे")
        val goalVals = floatArrayOf(2f, 3f, 4f, 5f, 6f, 7f)
        val selected = goalVals.indexOfFirst { it == currentGoal }.coerceAtLeast(2)

        AlertDialog.Builder(this)
            .setTitle(if(isHindi) "दैनिक लक्ष्य सेट करें" else "Set Daily Goal")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                prefs.edit().putFloat("goal_hours", goalVals[which]).apply()
                updateHeader()
                renderHome()
                dialog.dismiss()
                Toast.makeText(this, "Goal: ${goalVals[which].toInt()}h", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(if(isHindi) "रद्द करें" else "Cancel", null)
            .show()
    }

    private fun toggleLang() {
        isHindi = !isHindi
        prefs.edit().putBoolean("lang_hindi", isHindi).apply()
        btnLang.text = if (isHindi) "EN" else "हिंदी"
        showPage(currentPage)
        updateHeader()
    }

    private fun fmm(mins: Int): String {
        val h = mins / 60; val m = mins % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
