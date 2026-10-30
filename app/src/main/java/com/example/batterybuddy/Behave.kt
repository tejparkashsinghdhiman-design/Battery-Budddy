package com.example.batterybuddy

import android.content.SharedPreferences

/** On/off switches for everything the buddy can do. All on by default. */
data class Behave(
    val sleep: Boolean = true,
    val fast: Boolean = true,
    val drama: Boolean = true,
    val cold: Boolean = true,
    val tod: Boolean = true,
    val music: Boolean = true,
    val dnd: Boolean = true,
    val season: Boolean = true,
    val idle: Boolean = true,
    val wander: Boolean = true,
    val pet: Boolean = true,
    val bubbles: Boolean = true,
    val eyeBreak: Boolean = true,
    val mood: Boolean = true
) {
    companion object {
        /** pref key, title, subtitle: the Life tab builds its switches from this list */
        val ITEMS = listOf(
            Triple("b_sleep", "💤 Sleep while charging", "She curls up with floating zzz and does a happy dance when you hit 100%."),
            Triple("b_fast", "⚡ Fast-charge zoom", "Speed lines and fast legs on a fast charger. She naps on a slow one."),
            Triple("b_drama", "🥱 Dramatic low battery", "Yawns at 30%, begs at 10% and faints at 5%."),
            Triple("b_cold", "🥶 Cold shivers", "A scarf and shivers when the battery is cold."),
            Triple("b_tod", "🕐 Time-of-day looks", "Nightcap after midnight, coffee in the morning, shades at noon."),
            Triple("b_music", "🎧 Music mode", "Headphones, notes and head-bobbing while music plays."),
            Triple("b_dnd", "🤫 Do Not Disturb", "She shushes you and sits quietly."),
            Triple("b_season", "🎃 Seasonal outfits", "Halloween, Christmas, New Year, Valentine’s, Diwali, Republic and Independence Day, sakura season."),
            Triple("b_idle", "👋 Idle actions", "Waves, stretches, looks around, blows bubbles."),
            Triple("b_wander", "🚶 Wandering", "Strolls along the status bar and hides in your camera hole."),
            Triple("b_pet", "🐱 Pocket pet", "A tiny cat that sits beside her and hops along."),
            Triple("b_bubbles", "💬 Time-left bubbles", "A speech bubble with time left, or time to full, when you unplug or plug in."),
            Triple("b_eye", "👀 Eye-break nudges", "She pokes you after every 20 minutes of screen time."),
            Triple("b_mood", "✨ Mood effects", "Sparkles when you treat your battery well, a rain cloud when you don’t.")
        )

        fun load(p: SharedPreferences) = Behave(
            sleep = p.getBoolean("b_sleep", true),
            fast = p.getBoolean("b_fast", true),
            drama = p.getBoolean("b_drama", true),
            cold = p.getBoolean("b_cold", true),
            tod = p.getBoolean("b_tod", true),
            music = p.getBoolean("b_music", true),
            dnd = p.getBoolean("b_dnd", true),
            season = p.getBoolean("b_season", true),
            idle = p.getBoolean("b_idle", true),
            wander = p.getBoolean("b_wander", true),
            pet = p.getBoolean("b_pet", true),
            bubbles = p.getBoolean("b_bubbles", true),
            eyeBreak = p.getBoolean("b_eye", true),
            mood = p.getBoolean("b_mood", true)
        )
    }
}
