package com.example.batterybuddy

import android.accessibilityservice.AccessibilityService
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
 * (TYPE_ACCESSIBILITY_OVERLAY). It never reads the screen; it only hosts the overlay.
 */
class BuddyService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private var buddy: BuddyView? = null
    private var loadedRev = -1L

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
        buddy = BuddyView(this).also { wm.addView(it, lp) }
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
        buddy?.setBattery(level, charging)
    }

    private fun refresh() {
        val v = buddy ?: return
        val p = Buddy.prefs(this)
        val d = resources.displayMetrics.density

        val rev = p.getLong("rev", 0L)
        if (rev != loadedRev) { v.image = Buddy.loadImage(this); loadedRev = rev }
        v.sprite = p.getBoolean("sprite", false)

        val size = p.getInt("size", 26)
        lp.width = (size * d).toInt()
        lp.height = (size * 1.25f * d).toInt()
        lp.x = (p.getInt("x", 6) * d).toInt()   // from the right edge
        lp.y = (p.getInt("y", 0) * d).toInt()   // from the top

        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        v.visibility = if (p.getBoolean("show", true) && portrait) View.VISIBLE else View.GONE
        wm.updateViewLayout(v, lp)
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
}
