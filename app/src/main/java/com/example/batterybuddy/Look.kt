package com.example.batterybuddy

import android.content.SharedPreferences

/** Everything about how the built-in character looks and moves. Stored as plain ints in SharedPreferences. */
data class Look(
    val hair: Int = 0,
    val hairColor: Int = 0,
    val skin: Int = 0,
    val eyes: Int = 0,
    val outfit: Int = 0,
    val ears: Int = 1,
    val acc: Int = 1,
    val threeD: Boolean = true,
    val tilt: Boolean = true,
    val speed: Float = 1f
) {
    companion object {
        val HAIR_STYLES = listOf("Twin tails", "Short bob", "Spiky", "Long")
        val HAIR_COLOR_NAMES = listOf("Violet", "Black", "Brown", "Blonde", "Pink", "Blue", "Silver", "Orange")
        val HAIR_COLORS = intArrayOf(
            0xFF5B4B8A.toInt(), 0xFF22223B.toInt(), 0xFF6B4226.toInt(), 0xFFF2C14E.toInt(),
            0xFFF48FB1.toInt(), 0xFF3A7BD5.toInt(), 0xFFC9CED6.toInt(), 0xFFE8731A.toInt()
        )
        val SKIN_NAMES = listOf("Light", "Peach", "Tan", "Deep")
        val SKIN_COLORS = intArrayOf(0xFFFFE3D1.toInt(), 0xFFF6C9A8.toInt(), 0xFFD9A074.toInt(), 0xFF9C6B4A.toInt())
        val EYE_NAMES = listOf("Navy", "Green", "Blue", "Amber", "Red", "Purple")
        val EYE_COLORS = intArrayOf(
            0xFF2B2D42.toInt(), 0xFF2E8B57.toInt(), 0xFF2F80ED.toInt(),
            0xFFC17F1E.toInt(), 0xFFC0392B.toInt(), 0xFF7D3C98.toInt()
        )
        val OUTFIT_NAMES = listOf("Pink", "Red", "Blue", "Green", "Black", "White", "Yellow", "Purple")
        val OUTFIT_COLORS = intArrayOf(
            0xFFFF8FAB.toInt(), 0xFFE63946.toInt(), 0xFF4D96FF.toInt(), 0xFF52B788.toInt(),
            0xFF2B2D42.toInt(), 0xFFF1F3F5.toInt(), 0xFFFFD166.toInt(), 0xFF9D4EDD.toInt()
        )
        val EAR_NAMES = listOf("None", "Cat", "Bunny", "Fox")
        val ACC_NAMES = listOf("None", "Bow", "Glasses", "Headphones")
        val STYLE_NAMES = listOf("2D (flat)", "3D (figurine)")

        /** pref keys, in the same order as the preset arrays below */
        val KEYS = listOf("hair", "hair_color", "skin", "eyes", "outfit", "ears", "acc")
        val PRESETS = listOf(
            "Neko girl" to intArrayOf(0, 0, 0, 0, 0, 1, 1),
            "Bunny girl" to intArrayOf(0, 6, 0, 2, 5, 2, 1),
            "Fox girl" to intArrayOf(3, 7, 1, 3, 1, 3, 0),
            "Spiky hero" to intArrayOf(2, 1, 1, 2, 2, 0, 3),
            "Cool guy" to intArrayOf(1, 2, 0, 1, 4, 0, 2),
            "Silver mage" to intArrayOf(3, 6, 0, 4, 7, 0, 0)
        )

        fun load(p: SharedPreferences) = Look(
            hair = p.getInt("hair", 0).coerceIn(0, HAIR_STYLES.lastIndex),
            hairColor = p.getInt("hair_color", 0).coerceIn(0, HAIR_COLORS.lastIndex),
            skin = p.getInt("skin", 0).coerceIn(0, SKIN_COLORS.lastIndex),
            eyes = p.getInt("eyes", 0).coerceIn(0, EYE_COLORS.lastIndex),
            outfit = p.getInt("outfit", 0).coerceIn(0, OUTFIT_COLORS.lastIndex),
            ears = p.getInt("ears", 1).coerceIn(0, EAR_NAMES.lastIndex),
            acc = p.getInt("acc", 1).coerceIn(0, ACC_NAMES.lastIndex),
            threeD = p.getInt("style", 1) == 1,
            tilt = p.getBoolean("tilt", true),
            speed = p.getInt("speed", 100).coerceIn(50, 200) / 100f
        )
    }
}
