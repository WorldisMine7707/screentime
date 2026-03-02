package com.dajiraj.screentime

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class ScreenTimeService : Service() {

    companion object {
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "screentime_persist"
        const val CHANNEL_NAME = "Screen Time Tracker"
        const val UPDATE_INTERVAL = 60_000L // every 60 seconds
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var handler: Handler
    private lateinit var notifManager: NotificationManager

    private val updateRunnable = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, UPDATE_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("dst_v3", Context.MODE_PRIVATE)
        handler = Handler(Looper.getMainLooper())
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification(0, 0))
        handler.post(updateRunnable)
        return START_STICKY // Restarts if killed
    }

    override fun onBind(intent: IBinder?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    private fun tick() {
        val totalMins = UsageHelper.getTodayTotalMinutes(this)
        val goalMins = (prefs.getFloat("goal_hours", 4f) * 60).toInt()
        val pct = ((totalMins.toFloat() / goalMins) * 100).coerceIn(0f, 100f).toInt()
        val hourlyBreakdown = UsageHelper.getHourlyBreakdownToday(this)

        // Save today's total
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayKey = "day_${sdf.format(Date())}"
        prefs.edit().putInt(todayKey, totalMins).apply()

        // Save hourly data
        val todayDate = sdf.format(Date())
        hourlyBreakdown.forEachIndexed { hour, mins ->
            if (mins > 0) {
                prefs.edit().putInt("hour_${todayDate}_$hour", mins).apply()
            }
        }

        // Update notification
        notifManager.notify(NOTIF_ID, buildNotification(totalMins, goalMins))

        // Check reward / alert
        checkAlerts(totalMins, goalMins)
    }

    private fun buildNotification(totalMins: Int, goalMins: Int): Notification {
        val h = totalMins / 60
        val m = totalMins % 60
        val pct = if (goalMins > 0) ((totalMins.toFloat() / goalMins) * 100).coerceIn(0f, 100f).toInt() else 0
        val isHindi = prefs.getBoolean("lang_hindi", false)
        val goalH = (prefs.getFloat("goal_hours", 4f)).toInt()

        // Sandclock emoji changes with usage
        val clockEmoji = when {
            pct <= 25 -> "⏳"
            pct <= 50 -> "⌛"
            pct <= 75 -> "⏰"
            pct <= 100 -> "🔔"
            else -> "🚨"
        }

        val title = "$clockEmoji  $h:${m.toString().padStart(2,'0')}  ·  $pct%"
        val subtext = if (isHindi)
            "लक्ष्य: ${goalH}घं · बाकी: ${maxOf(0, goalMins - totalMins)}मिनट"
        else
            "Goal: ${goalH}h · Remaining: ${maxOf(0, goalMins - totalMins)}min"

        // Color changes with overuse
        val color = when {
            pct <= 75 -> Color.parseColor("#22C55E")
            pct <= 100 -> Color.parseColor("#FF9933")
            pct <= 130 -> Color.parseColor("#F97316")
            pct <= 160 -> Color.parseColor("#EF4444")
            else -> Color.parseColor("#DC2626")
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtext)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setColor(color)
            .setColorized(true)
            .setOngoing(true) // Cannot be swiped away
            .setProgress(100, pct.coerceIn(0, 100), false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun checkAlerts(totalMins: Int, goalMins: Int) {
        val isHindi = prefs.getBoolean("lang_hindi", false)
        val pct = if (goalMins > 0) (totalMins.toFloat() / goalMins * 100).toInt() else 0

        // Alert at 80%, 100%, 150%
        val alerted80 = prefs.getBoolean("alerted_80_today", false)
        val alerted100 = prefs.getBoolean("alerted_100_today", false)
        val alerted150 = prefs.getBoolean("alerted_150_today", false)

        if (pct >= 80 && !alerted80) {
            sendAlertNotification(2001,
                if(isHindi) "⚠️ लक्ष्य का 80% पूरा" else "⚠️ 80% of daily goal reached",
                if(isHindi) "सिर्फ ${goalMins - totalMins} मिनट बाकी" else "${goalMins - totalMins} minutes remaining")
            prefs.edit().putBoolean("alerted_80_today", true).apply()
        }
        if (pct >= 100 && !alerted100) {
            sendAlertNotification(2002,
                if(isHindi) "🚨 आज का लक्ष्य पूरा हो गया!" else "🚨 Daily goal reached!",
                if(isHindi) "अब फोन रखने का वक्त है।" else "Time to put the phone down.")
            prefs.edit().putBoolean("alerted_100_today", true).apply()
        }
        if (pct >= 150 && !alerted150) {
            sendAlertNotification(2003,
                if(isHindi) "🔴 लक्ष्य से 50% ज़्यादा!" else "🔴 50% over your daily goal!",
                if(isHindi) "फोन बंद करें — अभी!" else "Seriously — put it down now.")
            prefs.edit().putBoolean("alerted_150_today", true).apply()
        }

        // Reset alerts at midnight
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour == 0) {
            prefs.edit()
                .putBoolean("alerted_80_today", false)
                .putBoolean("alerted_100_today", false)
                .putBoolean("alerted_150_today", false)
                .apply()
        }
    }

    private fun sendAlertNotification(id: Int, title: String, text: String) {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notifManager.notify(id, notif)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows your real-time screen time in the notification bar"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        notifManager.createNotificationChannel(channel)
    }
}
