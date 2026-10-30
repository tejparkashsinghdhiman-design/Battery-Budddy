package com.example.batterybuddy

import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * The battery diary behind the Tamagotchi and the weekly coach.
 * Per day: full (reached 100%), low (dropped under 20%), hot (overheated),
 * ok (a charge that stopped near your limit), over (a charge that went well past it).
 */
object Stats {
    class Day(val key: String, var full: Int = 0, var low: Int = 0, var hot: Int = 0, var ok: Int = 0, var over: Int = 0)
    class Report(val full: Int, val low: Int, val hot: Int, val ok: Int, val over: Int, val tips: List<String>)

    private const val KEEP = 30

    private fun keyFor(ms: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

    fun load(p: SharedPreferences): MutableMap<String, Day> {
        val map = LinkedHashMap<String, Day>()
        try {
            val o = JSONObject(p.getString("stats", "{}") ?: "{}")
            for (k in o.keys()) {
                val d = o.getJSONObject(k)
                map[k] = Day(k, d.optInt("full"), d.optInt("low"), d.optInt("hot"), d.optInt("ok"), d.optInt("over"))
            }
        } catch (e: Exception) {
            // start fresh
        }
        return map
    }

    private fun save(p: SharedPreferences, map: Map<String, Day>) {
        val o = JSONObject()
        for (k in map.keys.sorted().takeLast(KEEP)) {
            val d = map[k] ?: continue
            o.put(k, JSONObject().put("full", d.full).put("low", d.low).put("hot", d.hot).put("ok", d.ok).put("over", d.over))
        }
        p.edit().putString("stats", o.toString()).apply()
    }

    /** what: full, low, hot, ok or over. Also moves the XP. */
    fun record(p: SharedPreferences, what: String) {
        val map = load(p)
        val k = keyFor(System.currentTimeMillis())
        val d = map.getOrPut(k) { Day(k) }
        var xp = p.getInt("xp", 0)
        when (what) {
            "full" -> d.full++
            "low" -> d.low++
            "hot" -> { d.hot++; xp = max(0, xp - 5) }
            "ok" -> { d.ok++; xp += 10 }
            "over" -> { d.over++; xp += 2 }
        }
        if (p.getString("xp_day", "") != k) {
            xp += 2 // small bonus for every day she is on duty
            p.edit().putString("xp_day", k).apply()
        }
        p.edit().putInt("xp", xp).apply()
        save(p, map)
    }

    fun level(xp: Int): Int = min(20, xp / 50 + 1)

    fun levelProgress(xp: Int): Float = if (level(xp) >= 20) 1f else (xp % 50) / 50f

    /** Days in a row (up to today) with at least one healthy charge and no overheating. */
    fun streak(p: SharedPreferences): Int {
        val map = load(p)
        val cal = Calendar.getInstance()
        var s = 0
        for (i in 0 until KEEP) {
            val d = map[keyFor(cal.timeInMillis)]
            val good = d != null && d.ok >= 1 && d.hot == 0
            if (good) s++ else if (i > 0) break // today may simply not be done yet
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return s
    }

    /** Her mood, 0 to 5 hearts, from the last three days. */
    fun hearts(p: SharedPreferences): Int {
        val map = load(p)
        val cal = Calendar.getInstance()
        var ok = 0; var hot = 0; var over = 0; var low = 0
        for (i in 0 until 3) {
            map[keyFor(cal.timeInMillis)]?.let { ok += it.ok; hot += it.hot; over += it.over; low += it.low }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return (3 + min(ok, 3) - hot * 2 - min(over, 2) - (if (low >= 3) 1 else 0)).coerceIn(0, 5)
    }

    fun weekly(p: SharedPreferences): Report {
        val map = load(p)
        val cal = Calendar.getInstance()
        var full = 0; var low = 0; var hot = 0; var ok = 0; var over = 0
        for (i in 0 until 7) {
            map[keyFor(cal.timeInMillis)]?.let { full += it.full; low += it.low; hot += it.hot; ok += it.ok; over += it.over }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        val tips = ArrayList<String>()
        if (full + low + hot + ok + over == 0) tips.add("Not enough data yet. Keep me running for a few days and I’ll have tips for you.")
        if (over > ok) tips.add("You often charged well past your limit. Unplugging around 80% is gentler on the battery.")
        if (full >= 5) tips.add("You hit 100% $full times. Staying around 80–90% helps your battery last longer.")
        if (low >= 3) tips.add("The battery dropped under 20% $low times. Topping up earlier avoids deep discharges.")
        if (hot >= 1) tips.add("It got hot $hot time(s). Avoid gaming while charging and take the case off.")
        if (ok >= 5 && hot == 0) tips.add("Great week! Your charging habits are excellent 🌟")
        if (tips.isEmpty()) tips.add("Looking good. Keep charging between about 20% and 80% when you can.")
        return Report(full, low, hot, ok, over, tips)
    }

    fun summary(r: Report): String =
        "✅ ${r.ok} healthy charges · 🔌 ${r.over} over the limit · 🥵 ${r.hot} hot moments. ${r.tips.first()}"
}
