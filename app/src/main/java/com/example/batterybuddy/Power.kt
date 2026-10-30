package com.example.batterybuddy

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import kotlin.math.abs

/** Charging power and time-left estimates, using only what Android exposes. Best effort: devices differ. */
object Power {
    private fun currentMicroAmps(bm: BatteryManager): Long {
        var cur = abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toLong())
        if (cur in 1..20000) cur *= 1000 // some phones report milliamps
        return if (cur > 20_000_000L) 0L else cur
    }

    fun watts(ctx: Context, i: Intent): Float {
        val bm = ctx.getSystemService(BatteryManager::class.java) ?: return 0f
        val cur = currentMicroAmps(bm)
        val mv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        return cur / 1_000_000f * mv / 1000f
    }

    private fun dur(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }

    /** Text for the speech bubble, or null when the phone can't tell. */
    fun remaining(ctx: Context, st: BuddyState): String? {
        val bm = ctx.getSystemService(BatteryManager::class.java) ?: return null
        if (st.charging) {
            if (st.level >= 100) return "Fully charged 🎉"
            val ms = bm.computeChargeTimeRemaining()
            return if (ms > 60_000) "Full in ${dur(ms)} ⚡" else null
        }
        val cur = currentMicroAmps(bm)
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (cur <= 0L || counter <= 0) return null
        val ms = (counter.toDouble() / cur * 3_600_000.0).toLong()
        return if (ms in 600_000L..360_000_000L) "About ${dur(ms)} left 🔋" else null
    }
}
