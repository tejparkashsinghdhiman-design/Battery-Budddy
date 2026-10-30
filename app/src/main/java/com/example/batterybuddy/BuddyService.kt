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
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.max

/**
 * An accessibility service is the one kind of app allowed to draw ABOVE the status bar
 * (TYPE_ACCESSIBILITY_OVERLAY). It never reads the screen: it hosts the overlay, watches the battery,
 * the screen, Do Not Disturb, music and battery saver, keeps the battery diary and sends the alerts.
 */
class BuddyService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private var buddy: BuddyView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences by lazy { Buddy.prefs(this) }
    private var behave = Behave()
    private var st = BuddyState()
    private var screenOn = true

    private var chargeAlerted = false
    private var heatAlerted = false
    private var fullCounted = false
    private var sessionStart = 0L
    private var sessionMax = 0
    private var lastWidget = 0L

    // NOT_TOUCHABLE: taps always pass straight through, she can never block the screen.
    // The window is as wide as the screen so she can wander along the status bar.
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
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    // must stay in a field: SharedPreferences only holds listeners weakly
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when {
            key == null -> {}
            key in LAYOUT_KEYS -> refresh()
            key.startsWith("b_") -> {
                behave = Behave.load(prefs)
                if (key == "b_eye") scheduleEyeBreak()
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> onBattery(intent)
                Intent.ACTION_SCREEN_ON -> { screenOn = true; refresh(); scheduleEyeBreak() }
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; refresh(); handler.removeCallbacks(eyeBreak) }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                    update { it.copy(saver = getSystemService(PowerManager::class.java).isPowerSaveMode) }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> update { it.copy(dnd = readDnd()) }
            }
        }
    }

    private val audioCb = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            val playing = getSystemService(AudioManager::class.java).isMusicActive
            if (playing != st.music) update { it.copy(music = playing) }
        }
    }

    private val eyeBreak = object : Runnable {
        override fun run() {
            if (screenOn && behave.eyeBreak) buddy?.poke()
            scheduleEyeBreak()
        }
    }

    // ---------------- lifecycle ----------------

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        val v = BuddyView(this)
        v.mode = BuddyView.MODE_OVERLAY
        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            return // crash guard: if Android refuses the overlay, just stay idle
        }
        buddy = v
        behave = Behave.load(prefs)
        screenOn = getSystemService(PowerManager::class.java).isInteractive
        st = BuddyState.snapshot(this)
        v.state = st

        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        registerReceiver(receiver, f)?.let { if (it.action == Intent.ACTION_BATTERY_CHANGED) onBattery(it) }
        getSystemService(AudioManager::class.java).registerAudioPlaybackCallback(audioCb, handler)
        refresh()
        scheduleEyeBreak()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        runCatching { unregisterReceiver(receiver) }
        runCatching { getSystemService(AudioManager::class.java).unregisterAudioPlaybackCallback(audioCb) }
        handler.removeCallbacksAndMessages(null)
        buddy?.let { v -> runCatching { wm.removeView(v) } }
        buddy = null
        super.onDestroy()
    }

    // ---------------- state ----------------

    private fun update(f: (BuddyState) -> BuddyState) {
        st = f(st)
        buddy?.state = st
    }

    private fun readDnd(): Boolean {
        val f = getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun onBattery(i: Intent) {
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, scale) * 100 / scale
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) / 10f
        val watts = if (charging) Power.watts(this, i) else 0f
        val old = st
        st = st.copy(level = level, charging = charging, tempC = tempC, watts = watts)
        track(old, st)
        buddy?.state = st
        pushWidget()
    }

    private fun record(what: String) {
        Stats.record(prefs, what)
        st = st.copy(hearts = Stats.hearts(prefs))
    }

    private fun sayLater(delayMs: Long, text: () -> String?) {
        handler.postDelayed({ text()?.let { buddy?.say(it) } }, delayMs)
    }

    /** Plug and unplug bubbles, the battery diary, XP, and the unplug / overheat alerts. */
    private fun track(old: BuddyState, now: BuddyState) {
        val t = System.currentTimeMillis()

        if (!old.charging && now.charging) {
            sessionStart = t
            sessionMax = now.level
            if (behave.bubbles) sayLater(2500L) { Power.remaining(this, st) }
        } else if (old.charging && !now.charging) {
            val minutes = (t - sessionStart) / 60_000L
            if (sessionStart != 0L && minutes >= 3L) {
                record(if (sessionMax <= prefs.getInt("limit", 80) + 5) "ok" else "over")
            }
            fullCounted = false
            if (behave.bubbles) sayLater(1500L) { Power.remaining(this, st) ?: "Unplugged \uD83D\uDD0B ${st.level}%" }
        }
        if (now.charging) sessionMax = max(sessionMax, now.level)

        if (now.charging && now.level >= 100 && !fullCounted) {
            fullCounted = true
            record("full")
            if (behave.bubbles) buddy?.say("100%! \uD83C\uDF89")
        }
        if (!now.charging && old.level >= 20 && now.level < 20) record("low")
        if (now.hot && !old.hot) record("hot")

        // unplug alert: one per charge session
        val limit = prefs.getInt("limit", 80)
        if (!now.charging || now.level < limit) {
            chargeAlerted = false
        } else if (!chargeAlerted && prefs.getBoolean("alert_charge", true)) {
            chargeAlerted = true
            alert(1, "Unplug me, please", "I\u2019m at ${now.level}%. Stopping around $limit% keeps your battery healthy for longer.")
            if (behave.bubbles) buddy?.say("Unplug me! \uD83D\uDD0C")
        }

        // overheat alert: one until the battery has cooled down again
        if (!now.hot && now.tempC < COOL_C) {
            heatAlerted = false
        } else if (now.hot && !heatAlerted && prefs.getBoolean("alert_hot", true)) {
            heatAlerted = true
            alert(2, "I\u2019m burning up!", "The battery is at ${now.tempC.toInt()} \u00b0C. Stop charging or gaming, take the case off and let me cool down.")
        }

        maybeWeekly(t)
    }

    private fun maybeWeekly(now: Long) {
        val last = prefs.getLong("last_report", 0L)
        if (last == 0L) {
            prefs.edit().putLong("last_report", now).apply()
            return
        }
        if (now - last >= 7 * 86_400_000L && prefs.getBoolean("weekly", true)) {
            prefs.edit().putLong("last_report", now).apply()
            alert(3, "Your week with me \uD83D\uDC96", Stats.summary(Stats.weekly(prefs)))
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

    private fun pushWidget() {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastWidget < 60_000L) return // at most once a minute
        lastWidget = nowMs
        BuddyWidget.updateAll(this, st)
    }

    private fun scheduleEyeBreak() {
        handler.removeCallbacks(eyeBreak)
        if (screenOn && behave.eyeBreak) handler.postDelayed(eyeBreak, 20 * 60_000L)
    }

    // ---------------- window ----------------

    private fun refresh() {
        val v = buddy ?: return
        val d = resources.displayMetrics.density
        val charW = prefs.getInt("size", 26).coerceIn(16, 60) * d // size cap: she can't grow to cover the screen
        lp.width = wm.currentWindowMetrics.bounds.width()
        lp.height = (charW * 1.25f).toInt()
        lp.x = 0
        lp.y = (prefs.getInt("y", 0) * d).toInt()
        v.charW = charW
        v.rightMargin = prefs.getInt("x", 6) * d
        try {
            val cut = wm.currentWindowMetrics.windowInsets.displayCutout
            v.setCutout(cut?.boundingRects?.firstOrNull { it.top <= 2 }?.exactCenterX() ?: -1f)
        } catch (e: Exception) {
            v.setCutout(-1f)
        }
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        v.visibility = if (prefs.getBoolean("show", true) && portrait && screenOn) View.VISIBLE else View.GONE
        try {
            wm.updateViewLayout(v, lp)
        } catch (e: Exception) {
            // crash guard
        }
        v.invalidate()
    }

    private companion object {
        const val COOL_C = 40f  // re-arm the overheat alert once it drops below this
        const val CHANNEL = "buddy_alerts"
        val LAYOUT_KEYS = setOf("show", "size", "x", "y")
    }
}
