package com.example.batterybuddy

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager

/** A snapshot of everything about the phone that changes how the buddy behaves. */
data class BuddyState(
    val level: Int = 100,
    val charging: Boolean = false,
    val tempC: Float = 25f,
    val watts: Float = 0f,
    val music: Boolean = false,
    val dnd: Boolean = false,
    val saver: Boolean = false,
    val hearts: Int = 3
) {
    val hot: Boolean get() = tempC >= 43f
    val cold: Boolean get() = tempC <= 10f && tempC > -30f
    val fast: Boolean get() = charging && watts >= 12f

    companion object {
        /** Reads the phone right now (used by the in-app preview and the widget). */
        fun snapshot(ctx: Context): BuddyState {
            val c = ctx.applicationContext
            val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val scale = (i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
            val level = (i?.getIntExtra(BatteryManager.EXTRA_LEVEL, scale) ?: scale) * 100 / scale
            val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) ?: 250) / 10f
            val watts = if (charging && i != null) Power.watts(c, i) else 0f
            val music = c.getSystemService(AudioManager::class.java)?.isMusicActive ?: false
            val f = c.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
                ?: NotificationManager.INTERRUPTION_FILTER_ALL
            val dnd = f != NotificationManager.INTERRUPTION_FILTER_ALL &&
                f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            val saver = c.getSystemService(PowerManager::class.java)?.isPowerSaveMode ?: false
            return BuddyState(level, charging, temp, watts, music, dnd, saver, Stats.hearts(Buddy.prefs(c)))
        }
    }
}

/** A scripted situation for the "Try her moods" buttons: it overrides reality for a few seconds. */
data class Scene(
    val state: BuddyState? = null,
    val tod: Int = -1,
    val season: Int = -1,
    val action: Int = -1,
    val bubble: String? = null,
    val id: Long = System.nanoTime()
)

/** Time of day and the festivals she dresses up for. */
object DayClock {
    const val NIGHT = 0
    const val MORNING = 1
    const val NOON = 2
    const val EVENING = 3

    const val S_NONE = 0
    const val S_HALLOWEEN = 1
    const val S_XMAS = 2
    const val S_NEWYEAR = 3
    const val S_VALENTINE = 4
    const val S_INDIA = 5
    const val S_DIWALI = 6
    const val S_SAKURA = 7

    fun tod(hour: Int): Int = when (hour) {
        in 0..4 -> NIGHT
        in 5..10 -> MORNING
        in 11..15 -> NOON
        else -> EVENING
    }

    /** m is 1..12. Diwali is a lunar festival, so its dates are listed per year (2026 and 2027). */
    fun season(y: Int, m: Int, d: Int): Int = when {
        (y == 2026 && m == 11 && d in 6..10) || (y == 2027 && m == 10 && d in 27..31) -> S_DIWALI
        (m == 10 && d >= 24) || (m == 11 && d == 1) -> S_HALLOWEEN
        m == 12 && d in 18..26 -> S_XMAS
        (m == 12 && d >= 29) || (m == 1 && d <= 2) -> S_NEWYEAR
        m == 2 && d in 12..15 -> S_VALENTINE
        (m == 1 && d in 25..26) || (m == 8 && d in 14..15) -> S_INDIA
        (m == 3 && d >= 25) || (m == 4 && d <= 10) -> S_SAKURA
        else -> S_NONE
    }
}

object Moods {
    fun describe(st: BuddyState): String = when {
        !st.charging && st.level <= 5 -> "Fainted 😵 Please charge me!"
        !st.charging && st.level <= 10 -> "Begging for a charge 🥺"
        st.hot -> "Burning up 🥵"
        st.cold -> "Brrr, it’s cold 🥶"
        st.charging && st.level >= 100 -> "Full and happy 🎉"
        st.fast -> "Zooming on fast charge ⚡"
        st.charging -> "Napping while I charge 💤"
        st.level <= 30 -> "Getting sleepy 🥱"
        st.music -> "Vibing to the music 🎧"
        st.dnd -> "Shhh, Do Not Disturb 🤫"
        else -> "Happy and cozy 💖"
    }
}
