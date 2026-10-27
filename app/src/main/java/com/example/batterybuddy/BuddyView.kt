package com.example.batterybuddy

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.os.SystemClock
import android.view.View
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Shared prefs + saved character image loader. */
object Buddy {
    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("buddy", Context.MODE_PRIVATE)
    fun loadImage(c: Context): Bitmap? =
        if (prefs(c).getBoolean("custom", false)) BitmapFactory.decodeFile(File(c.filesDir, "buddy.png").path) else null
}

/**
 * Three looks, all sitting on a ledge at 70% of the view height with swinging legs:
 *  - no image:        the built-in original neko girl
 *  - image:           the image becomes a round head on a little body (body colour sampled from the image)
 *  - image + sprite:  the image is drawn as-is (for full-body transparent PNGs)
 * Moods: happy heart while charging, sweat drop at <=15% or when overheating (face/ring turns red),
 * and she rests (stops animating) at <=15% to save power.
 * Coordinates are a 100 x 125 box scaled to the view.
 */
class BuddyView(context: Context) : View(context) {
    var image: Bitmap? = null
        set(value) { field = value; rebuild(); invalidate() }
    var sprite = false
        set(value) { field = value; invalidate() }

    private var level = 100
    private var charging = false
    private var hot = false
    private var shader: BitmapShader? = null
    private var bodyColor = DRESS

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val resting: Boolean get() = level <= 15 && !charging
    private val happy: Boolean get() = charging && !hot
    private val worried: Boolean get() = hot || resting

    fun setBattery(level: Int, charging: Boolean, hot: Boolean) {
        this.level = level
        this.charging = charging
        this.hot = hot
        invalidate()
    }

    private fun fill(color: Int) { p.style = Paint.Style.FILL; p.color = color }
    private fun stroke(color: Int, w: Float) {
        p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = w; p.strokeCap = Paint.Cap.ROUND
    }
    private fun darker(c: Int, f: Float = 0.6f) =
        Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    private fun rebuild() {
        val b = image
        if (b == null) { shader = null; bodyColor = DRESS; return }
        val k = max(2 * HEAD_R / b.width, 2 * HEAD_R / b.height)
        shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(k, k)
                postTranslate(HEAD_X - b.width * k / 2, HEAD_Y - HEAD_R) // centred, top-aligned (faces sit high)
            })
        }
        val y = (b.height * 0.8f).toInt().coerceIn(0, b.height - 1)
        val strip = Bitmap.createBitmap(b, 0, y, b.width, b.height - y)
        val px = Bitmap.createScaledBitmap(strip, 1, 1, true).getPixel(0, 0)
        bodyColor = if (Color.alpha(px) < 128) DRESS else px
    }

    override fun onDraw(c: Canvas) {
        c.scale(width / 100f, height / 125f)
        // rest mode: frozen pose, redrawn once a second -> almost no power use
        val t = if (resting) 0f else SystemClock.uptimeMillis() / 1000f
        val bmp = image
        when {
            bmp != null && sprite -> drawSprite(c, bmp, t)
            bmp != null -> drawPortrait(c, t)
            else -> drawGirl(c, t)
        }
        postInvalidateDelayed(if (resting) 1000L else 80L) // ~12 fps; stops by itself when the view is hidden
    }

    private fun drawSprite(c: Canvas, bmp: Bitmap, t: Float) {
        val s = min(100f / bmp.width, 100f / bmp.height)
        val w = bmp.width * s
        val h = bmp.height * s
        c.rotate(sin(t * 2.5f) * 3f, 50f, 100f)
        c.drawBitmap(bmp, null, RectF(50f - w / 2, 100f - h, 50f + w / 2, 100f), null)
    }

    private fun drawPortrait(c: Canvas, t: Float) {
        val sh = shader ?: return
        drawLegs(c, t, darker(bodyColor), 0xFF222222.toInt())
        c.save()
        c.translate(0f, sin(t * 3f) * 1.2f)
        drawBody(c, bodyColor)
        fill(Color.WHITE); p.shader = sh
        c.drawCircle(HEAD_X, HEAD_Y, HEAD_R, p)
        p.shader = null
        stroke(if (hot) RED else Color.WHITE, 2.5f)
        c.drawCircle(HEAD_X, HEAD_Y, HEAD_R, p)
        drawMood(c, t)
        c.restore()
    }

    private fun drawLegs(c: Canvas, t: Float, leg: Int, shoe: Int) {
        val swing = sin(t * 3f) * 14f
        for (side in intArrayOf(-1, 1)) {
            val hx = 50f + side * 12f
            c.save()
            c.rotate(swing * side, hx, 80f)
            fill(leg); c.drawRoundRect(hx - 5f, 78f, hx + 5f, 108f, 5f, 5f, p)
            fill(shoe); c.drawRoundRect(hx - 7f, 103f, hx + 7f, 114f, 5f, 5f, p)
            c.restore()
        }
    }

    private fun drawBody(c: Canvas, color: Int) {
        fill(color)
        c.drawRoundRect(RectF(28f, 54f, 72f, 88f), 12f, 12f, p)
        c.drawRoundRect(RectF(20f, 58f, 31f, 80f), 5f, 5f, p)
        c.drawRoundRect(RectF(69f, 58f, 80f, 80f), 5f, 5f, p)
        fill(SKIN)
        c.drawCircle(25.5f, 82f, 5f, p)
        c.drawCircle(74.5f, 82f, 5f, p)
    }

    private fun drawMood(c: Canvas, t: Float) {
        if (happy) {
            c.save()
            c.translate(88f, 14f + sin(t * 4f) * 2f); c.scale(1.3f, 1.3f)
            fill(PINK); path.reset()
            path.moveTo(0f, 2.5f); path.cubicTo(-7f, -2f, -3.5f, -8f, 0f, -4f)
            path.cubicTo(3.5f, -8f, 7f, -2f, 0f, 2.5f)
            c.drawPath(path, p)
            c.restore()
        } else if (worried) {
            c.save()
            c.translate(87f, 22f)
            fill(0xFF74C0FC.toInt()); path.reset()
            path.moveTo(0f, 0f); path.cubicTo(-6f, 8f, -5f, 14f, 0f, 14f)
            path.cubicTo(5f, 14f, 6f, 8f, 0f, 0f)
            c.drawPath(path, p)
            c.restore()
        }
    }

    private fun drawGirl(c: Canvas, t: Float) {
        val blink = SystemClock.uptimeMillis() % 4000 < 140
        drawLegs(c, t, SKIN, PINK)
        c.save()
        c.translate(0f, sin(t * 3f) * 1.2f)

        // twin tails + body
        fill(HAIR)
        c.drawOval(RectF(9f, 34f, 31f, 78f), p)
        c.drawOval(RectF(69f, 34f, 91f, 78f), p)
        drawBody(c, DRESS)

        // hair back + cat ears
        fill(HAIR)
        c.drawCircle(50f, 38f, 29f, p)
        for (m in 0..1) {
            c.save()
            if (m == 1) c.scale(-1f, 1f, 50f, 0f)
            fill(HAIR)
            path.reset(); path.moveTo(22f, 26f); path.lineTo(24f, 2f); path.lineTo(44f, 14f); path.close()
            c.drawPath(path, p)
            fill(PINK)
            path.reset(); path.moveTo(27f, 22f); path.lineTo(28f, 9f); path.lineTo(38f, 15f); path.close()
            c.drawPath(path, p)
            c.restore()
        }

        // face + blush (+ red flush when overheating)
        fill(SKIN); c.drawOval(RectF(26f, 16f, 74f, 60f), p)
        if (hot) { fill(0x55FF3B30); c.drawOval(RectF(26f, 16f, 74f, 60f), p) }
        fill(0x66FF6F91)
        c.drawOval(RectF(27f, 47f, 37f, 53f), p)
        c.drawOval(RectF(63f, 47f, 73f, 53f), p)

        // eyes
        for (ex in floatArrayOf(38f, 62f)) {
            if (happy) {
                stroke(EYE, 2.6f)
                path.reset(); path.moveTo(ex - 5f, 45f); path.quadTo(ex, 37f, ex + 5f, 45f)
                c.drawPath(path, p)
            } else if (blink) {
                stroke(EYE, 2.4f); c.drawLine(ex - 5f, 44f, ex + 5f, 44f, p)
            } else {
                fill(EYE); c.drawOval(RectF(ex - 5f, 36f, ex + 5f, 51f), p)
                fill(Color.WHITE)
                c.drawCircle(ex + 1.5f, 40f, 2.3f, p)
                c.drawCircle(ex - 1.5f, 47f, 1.1f, p)
            }
        }

        // mouth
        path.reset()
        when {
            happy -> {
                fill(0xFFD6336C.toInt())
                path.moveTo(45f, 53f); path.quadTo(50f, 61f, 55f, 53f); path.close()
                c.drawPath(path, p)
            }
            worried -> {
                stroke(EYE, 2f)
                path.moveTo(45f, 56f); path.quadTo(50f, 51f, 55f, 56f)
                c.drawPath(path, p)
            }
            else -> {
                stroke(EYE, 2f)
                path.moveTo(45f, 53f); path.quadTo(50f, 57f, 55f, 53f)
                c.drawPath(path, p)
            }
        }

        // bangs
        fill(HAIR); path.reset()
        path.moveTo(25f, 40f); path.cubicTo(25f, 8f, 75f, 8f, 75f, 40f)
        path.lineTo(68f, 26f); path.lineTo(61f, 35f); path.lineTo(50f, 24f)
        path.lineTo(39f, 35f); path.lineTo(32f, 26f); path.close()
        c.drawPath(path, p)

        // bow
        fill(RED); path.reset()
        path.moveTo(50f, 63f); path.lineTo(41f, 58f); path.lineTo(41f, 68f); path.close()
        path.moveTo(50f, 63f); path.lineTo(59f, 58f); path.lineTo(59f, 68f); path.close()
        c.drawPath(path, p)
        c.drawCircle(50f, 63f, 2.6f, p)

        drawMood(c, t)
        c.restore()
    }

    private companion object {
        const val HEAD_X = 50f
        const val HEAD_Y = 36f
        const val HEAD_R = 27f
        val HAIR = 0xFF5B4B8A.toInt()
        val SKIN = 0xFFFFE3D1.toInt()
        val DRESS = 0xFFFF8FAB.toInt()
        val EYE = 0xFF2B2D42.toInt()
        val PINK = 0xFFFF6F91.toInt()
        val RED = 0xFFE63946.toInt()
    }
}
