package com.example.batterybuddy

/** Everything the sprite needs to draw one frame. One instance is reused every frame (no allocations). */
class Pose {
    var eyes = EYES_NORMAL
    var lookX = 0f
    var mouth = MOUTH_SMILE
    var armL = ARM_DOWN
    var armR = ARM_DOWN
    var wave = 0f
    var swing = 1f
    var swingAmp = 14f
    var bob = 1.2f
    var tilt = 0f
    var shiver = 0f
    var jump = 0f
    var scale = 1f
    var flush = false
    var worried = false
    var zzz = false
    var hearts = false
    var sparkles = false
    var cloud = false
    var notes = false
    var speed = false
    var stars = false
    var moon = false
    var shh = false
    var bubble = 0f
    var scarf = false
    var nightcap = false
    var shades = false
    var mug = false
    var phones = false
    var wings = 0.3f
    var hat = HAT_NONE
    var festival = FESTIVAL_NONE

    fun reset(): Pose {
        eyes = EYES_NORMAL; lookX = 0f; mouth = MOUTH_SMILE; armL = ARM_DOWN; armR = ARM_DOWN; wave = 0f
        swing = 1f; swingAmp = 14f; bob = 1.2f; tilt = 0f; shiver = 0f; jump = 0f; scale = 1f
        flush = false; worried = false; zzz = false; hearts = false; sparkles = false; cloud = false
        notes = false; speed = false; stars = false; moon = false; shh = false; bubble = 0f
        scarf = false; nightcap = false; shades = false; mug = false; phones = false
        wings = 0.3f; hat = HAT_NONE; festival = FESTIVAL_NONE
        return this
    }

    companion object {
        const val EYES_NORMAL = 0
        const val EYES_CLOSED = 1
        const val EYES_HAPPY = 2
        const val EYES_BLINK = 3
        const val EYES_X = 4
        const val EYES_BIG = 5
        const val EYES_HALF = 6

        const val MOUTH_SMILE = 0
        const val MOUTH_OPEN = 1
        const val MOUTH_SMALL_O = 2
        const val MOUTH_YAWN = 3
        const val MOUTH_FROWN = 4
        const val MOUTH_FLAT = 5
        const val MOUTH_WAVY = 6

        const val ARM_DOWN = 0
        const val ARM_WAVE = 1
        const val ARM_UP = 2
        const val ARM_POKE = 3
        const val ARM_BEG = 4
        const val ARM_MOUTH = 5
        const val ARM_MUG = 6

        const val HAT_NONE = 0
        const val HAT_PUMPKIN = 1
        const val HAT_SANTA = 2
        const val HAT_PARTY = 3
        const val HAT_TRICOLOR = 4
        const val HAT_FLOWER = 5

        const val FESTIVAL_NONE = 0
        const val FESTIVAL_DIYA = 1
        const val FESTIVAL_PETALS = 2
    }
}
