package com.example.batterybuddy

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * An accessibility service is the one kind of app allowed to draw ABOVE the status bar
 * (TYPE_ACCESSIBILITY_OVERLAY). It never reads the screen; it only hosts the overlay
 * and watches the battery for the safety alerts.
 */
class BuddyService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private var buddy: BuddyView? = null
    private var loadedRev = -1L
    private var chargeAlerted = false
    private var heatAlerted = false

    // NOT_TOUCHABLE: taps always pass straight through, she can never block the screen.
    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    // must stay in a field: SharedPreferences only holds listeners weakly
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = applyBattery(intent)
    }

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        val v = BuddyView(this)
        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            return // crash guard: if Android refuses the overlay, just stay idle
        }
        buddy = v
        Buddy.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { applyBattery(it) }
        refresh()
    }

    private fun applyBattery(i: Intent) {
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, scale) * 100 / scale
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val hot = tempC >= HOT_C ||
            i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) == BatteryManager.BATTERY_HEALTH_OVERHEAT
        buddy?.setBattery(level, charging, hot)
        checkAlerts(level, charging, hot, tempC)
    }

    private fun checkAlerts(level: Int, charging: Boolean, hot: Boolean, tempC: Float) {
        val p = Buddy.prefs(this)

        // Charge limit: one alert per charge session
        val limit = p.getInt("limit", 80)
        if (!charging || level < limit) {
            chargeAlerted = false
        } else if (!chargeAlerted && p.getBoolean("alert_charge", true)) {
            chargeAlerted = true
            alert(1, "Unplug me, please", "I’m at $level%. Stopping around $limit% keeps your battery healthy for longer.")
        }

        // Overheat: one alert until the battery has cooled down again
        if (!hot && tempC < COOL_C) {
            heatAlerted = false
        } else if (hot && !heatAlerted && p.getBoolean("alert_hot", true)) {
            heatAlerted = true
            alert(2, "I’m burning up!", "The battery is at ${tempC.toInt()} °C. Stop charging or gaming, take the case off and let me cool down.")
        }
    }

    private fun alert(id: Int, title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Battery alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            id,
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_buddy)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun refresh() {
        val v = buddy ?: return
        val p = Buddy.prefs(this)
        val d = resources.displayMetrics.density

        val rev = p.getLong("rev", 0L)
        if (rev != loadedRev) { v.image = Buddy.loadImage(this); loadedRev = rev }
        v.sprite = p.getBoolean("sprite", false)

        val size = p.getInt("size", 26).coerceIn(16, 60) // size cap: she can't grow to cover the screen
        lp.width = (size * d).toInt()
        lp.height = (size * 1.25f * d).toInt()
        lp.x = (p.getInt("x", 6) * d).toInt()   // from the right edge
        lp.y = (p.getInt("y", 0) * d).toInt()   // from the top

        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        v.visibility = if (p.getBoolean("show", true) && portrait) View.VISIBLE else View.GONE
        try {
            wm.updateViewLayout(v, lp)
        } catch (e: Exception) {
            // crash guard
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        Buddy.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        runCatching { unregisterReceiver(batteryReceiver) }
        buddy?.let { v -> runCatching { wm.removeView(v) } }
        buddy = null
        super.onDestroy()
    }

    private companion object {
        const val HOT_C = 43f   // warn at or above this battery temperature
        const val COOL_C = 40f  // re-arm the warning once it drops below this
        const val CHANNEL = "buddy_alerts"
    }
}
