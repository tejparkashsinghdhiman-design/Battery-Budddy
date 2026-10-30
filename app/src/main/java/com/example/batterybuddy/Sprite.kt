package com.example.batterybuddy

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * All the drawing. Draws the built-in character (or a picture as head / full body) from a [Pose].
 * Coordinates are a 100 x 125 box. Nothing is allocated per frame.
 */
class Sprite {
    /** current 3D yaw and which way it moves things, set by the view before every draw */
    var ry = 0f
    var sign = 1f

    private var look = Look()
    private var hairC = 0
    private var skinC = 0
    private var eyeC = 0
    private var outC = 0
    private var faceSh: Shader? = null
    private var hairSh: Shader? = null
    private var bodySh: Shader? = null
    private var image: Bitmap? = null
    private var headSh: BitmapShader? = null
    private var bodyColor = 0

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val r = RectF()
    private val moonPath = Path().apply {
        val a = Path().apply { addCircle(0f, 0f, 6f, Path.Direction.CW) }
        val b = Path().apply { addCircle(3f, -1.5f, 5.2f, Path.Direction.CW) }
        op(a, b, Path.Op.DIFFERENCE)
    }
    private lateinit var po: Pose
    private var tt = 0f

    // ---------------- setup ----------------

    fun setLook(l: Look) {
        look = l
        hairC = Look.HAIR_COLORS[l.hairColor]
        skinC = Look.SKIN_COLORS[l.skin]
        eyeC = Look.EYE_COLORS[l.eyes]
        outC = Look.OUTFIT_COLORS[l.outfit]
        faceSh = RadialGradient(
            42f, 28f, 38f,
            intArrayOf(shade(skinC, 1.08f), skinC, shade(skinC, 0.8f)), floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        hairSh = LinearGradient(0f, 4f, 0f, 74f, shade(hairC, 1.3f), shade(hairC, 0.72f), Shader.TileMode.CLAMP)
        bodySh = LinearGradient(26f, 54f, 74f, 90f, shade(outC, 1.25f), shade(outC, 0.72f), Shader.TileMode.CLAMP)
        rebuildImage()
    }

    fun setImage(b: Bitmap?) {
        image = b
        rebuildImage()
    }

    private fun rebuildImage() {
        val b = image
        if (b == null) { headSh = null; bodyColor = outC; return }
        val k = max(2 * HEAD_R / b.width, 2 * HEAD_R / b.height)
        headSh = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(k, k)
                postTranslate(HEAD_X - b.width * k / 2, HEAD_Y - HEAD_R) // centred, top-aligned (faces sit high)
            })
        }
        val y = (b.height * 0.8f).toInt().coerceIn(0, b.height - 1)
        val strip = Bitmap.createBitmap(b, 0, y, b.width, b.height - y)
        val px = Bitmap.createScaledBitmap(strip, 1, 1, true).getPixel(0, 0)
        bodyColor = if (Color.alpha(px) < 128) outC else px
    }

    // ---------------- paint helpers ----------------

    private fun shade(c: Int, f: Float) = Color.rgb(
        min(255, (Color.red(c) * f).toInt()),
        min(255, (Color.green(c) * f).toInt()),
        min(255, (Color.blue(c) * f).toInt())
    )

    private fun fill(color: Int, sh: Shader? = null) {
        p.style = Paint.Style.FILL; p.color = color
        p.shader = if (look.threeD) sh else null // gradients only in 3D mode
    }

    private fun stroke(color: Int, w: Float) {
        p.shader = null
        p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = w; p.strokeCap = Paint.Cap.ROUND
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, alpha: Int = 255) {
        tp.textSize = size; tp.color = color; tp.alpha = alpha
        c.drawText(s, x, y, tp)
    }

    private fun tri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        c.drawPath(path, p)
    }

    private fun heart(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        c.save(); c.translate(x, y); c.scale(s, s)
        fill(color); path.reset()
        path.moveTo(0f, 2.5f); path.cubicTo(-7f, -2f, -3.5f, -8f, 0f, -4f)
        path.cubicTo(3.5f, -8f, 7f, -2f, 0f, 2.5f)
        c.drawPath(path, p)
        c.restore()
    }

    private fun star4(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y - s); path.lineTo(x + s * 0.3f, y - s * 0.3f); path.lineTo(x + s, y)
        path.lineTo(x + s * 0.3f, y + s * 0.3f); path.lineTo(x, y + s); path.lineTo(x - s * 0.3f, y + s * 0.3f)
        path.lineTo(x - s, y); path.lineTo(x - s * 0.3f, y - s * 0.3f); path.close()
        c.drawPath(path, p)
    }

    private fun star5(c: Canvas, cx: Float, cy: Float, rad: Float) {
        path.reset()
        for (k in 0..9) {
            val a = -1.5708f + k * 0.62832f
            val rr = if (k % 2 == 0) rad else rad * 0.45f
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, p)
    }

    /** One depth layer. In 3D, layers nearer the viewer (dz > 0) shift against the rotation = parallax. */
    private inline fun layer(c: Canvas, dz: Float, dy: Float = 0f, block: () -> Unit) {
        c.save()
        c.translate(if (look.threeD) -sign * dz * sin(ry * DEG) else 0f, dy)
        block()
        c.restore()
    }

    // ---------------- entry points ----------------

    fun draw(c: Canvas, pose: Pose, t: Float, fullBody: Boolean) {
        po = pose
        tt = t
        val bmp = image
        c.save()
        if (po.shiver > 0f) c.translate(sin(t * 45f) * po.shiver, 0f)
        if (po.tilt != 0f) c.rotate(po.tilt, 50f, 100f)
        if (po.scale != 1f) c.scale(po.scale, po.scale, 50f, 100f)
        if (po.jump != 0f) c.translate(0f, -po.jump)
        drawSpeed(c)
        when {
            bmp != null && fullBody -> layer(c, 0f) { drawFull(c, bmp) }
            bmp != null -> drawPortrait(c)
            else -> drawCharacter(c)
        }
        c.restore()
    }

    /** The pocket cat. [dx] is how far it has hopped (box units), [hop] its height. */
    fun drawPet(c: Canvas, dx: Float, hop: Float, t: Float) {
        c.save()
        c.translate(-12f + dx, 76f - hop)
        c.save()
        c.rotate(sin(t * 5f) * 10f, 8f, 8f)
        stroke(0xFFE08A3C.toInt(), 3f)
        path.reset(); path.moveTo(7f, 8f); path.cubicTo(17f, 8f, 17f, -4f, 12f, -8f)
        c.drawPath(path, p)
        c.restore()
        fill(0xFFF4A259.toInt()); r.set(-9f, -4f, 9f, 12f); c.drawOval(r, p)
        fill(0xFFFFE3C8.toInt()); r.set(-4f, 2f, 4f, 11f); c.drawOval(r, p)
        fill(0xFFF4A259.toInt()); c.drawCircle(0f, -8f, 8f, p)
        tri(c, -8f, -11f, -7f, -19f, -1f, -13f)
        tri(c, 8f, -11f, 7f, -19f, 1f, -13f)
        fill(PINK_LIGHT); tri(c, -6.5f, -12f, -6.5f, -16f, -3f, -13f)
        tri(c, 6.5f, -12f, 6.5f, -16f, 3f, -13f)
        fill(DARK); c.drawCircle(-3f, -8f, 1.2f, p); c.drawCircle(3f, -8f, 1.2f, p)
        fill(PINK); c.drawCircle(0f, -5.6f, 0.9f, p)
        c.restore()
    }

    // ---------------- the three looks ----------------

    private fun drawFull(c: Canvas, bmp: Bitmap) {
        val s = min(100f / bmp.width, 100f / bmp.height)
        val w = bmp.width * s
        val h = bmp.height * s
        c.rotate(sin(tt * 2.5f * po.swing) * 3f, 50f, 100f)
        r.set(50f - w / 2, 100f - h, 50f + w / 2, 100f)
        c.drawBitmap(bmp, null, r, null)
        drawMood(c)
    }

    private fun drawPortrait(c: Canvas) {
        val sh = headSh ?: return
        val bob = sin(tt * 3f) * po.bob
        layer(c, -2f) { drawLegs(c, shade(bodyColor, 0.6f), 0xFF222222.toInt()) }
        layer(c, 0f, bob) { drawBody(c, bodyColor, null) }
        layer(c, 6f, bob) {
            fill(Color.WHITE); p.shader = sh
            c.drawCircle(HEAD_X, HEAD_Y, HEAD_R, p)
            p.shader = null
            stroke(if (po.flush) RED else Color.WHITE, 2.5f)
            c.drawCircle(HEAD_X, HEAD_Y, HEAD_R, p)
            drawArms(c, bodyColor)
            drawProps(c)
            drawMood(c)
        }
    }

    private fun drawCharacter(c: Canvas) {
        val bob = sin(tt * 3f) * po.bob
        layer(c, -12f, bob) { drawWings(c) }
        layer(c, -10f) { drawTail(c) }
        layer(c, -6f, bob) { drawHairBack(c) }
        layer(c, -2f) { drawLegs(c, skinC, shade(outC, 0.7f)) }
        layer(c, 0f, bob) { drawBody(c, outC, bodySh) }
        layer(c, 6f, bob) {
            drawHead(c)
            drawArms(c, outC)
            drawProps(c)
            drawMood(c)
        }
    }

    // ---------------- body parts ----------------

    private fun drawSpeed(c: Canvas) {
        if (!po.speed) return
        stroke(0x889B8CFF.toInt(), 2.6f)
        for (i in 0..2) {
            val x = 2f + ((tt * 7f + i * 0.31f) % 1f) * 12f
            c.drawLine(x, 62f + i * 11f, x + 15f, 62f + i * 11f, p)
        }
    }

    private fun drawWings(c: Canvas) {
        if (look.acc != 6) return
        val flap = sin(tt * 7f) * 10f * po.wings
        for (i in 0..1) {
            val left = i == 0
            c.save()
            c.rotate(if (left) -25f - flap else 25f + flap, if (left) 30f else 70f, 64f)
            fill(0xFFF6F7FF.toInt()); r.set(if (left) 0f else 70f, 42f, if (left) 30f else 100f, 82f); c.drawOval(r, p)
            fill(0xFFE3E6FF.toInt()); r.set(if (left) 5f else 74f, 50f, if (left) 27f else 96f, 76f); c.drawOval(r, p)
            c.restore()
        }
    }

    private fun drawTail(c: Canvas) {
        when (look.ears) {
            1 -> { // cat tail
                stroke(hairC, 5f)
                path.reset(); path.moveTo(68f, 82f); path.cubicTo(92f, 86f, 98f, 62f, 88f, 48f)
                c.drawPath(path, p)
            }
            2 -> { // bunny pom
                fill(Color.WHITE); c.drawCircle(77f, 84f, 6.5f, p)
            }
            3 -> { // fluffy fox tail with a white tip
                c.save(); c.rotate(35f, 74f, 84f)
                fill(hairC, hairSh); r.set(68f, 58f, 92f, 98f); c.drawOval(r, p)
                fill(Color.WHITE); r.set(70f, 58f, 90f, 72f); c.drawOval(r, p)
                c.restore()
            }
        }
    }

    private fun drawHairBack(c: Canvas) {
        fill(hairC, hairSh)
        when (look.hair) {
            0 -> { // twin tails
                r.set(9f, 34f, 31f, 78f); c.drawOval(r, p)
                r.set(69f, 34f, 91f, 78f); c.drawOval(r, p)
            }
            3 -> { // long hair behind the shoulders
                r.set(19f, 18f, 81f, 94f); c.drawRoundRect(r, 26f, 26f, p)
            }
        }
    }

    private fun drawLegs(c: Canvas, leg: Int, shoe: Int) {
        val swing = sin(tt * 3f * po.swing) * po.swingAmp
        for (i in 0..1) {
            val side = i * 2 - 1
            val hx = 50f + side * 12f
            c.save()
            c.rotate(swing * side, hx, 80f)
            fill(leg); r.set(hx - 5f, 78f, hx + 5f, 108f); c.drawRoundRect(r, 5f, 5f, p)
            fill(shoe); r.set(hx - 7f, 103f, hx + 7f, 114f); c.drawRoundRect(r, 5f, 5f, p)
            c.restore()
        }
    }

    private fun drawBody(c: Canvas, color: Int, sh: Shader?) {
        fill(color, sh)
        r.set(28f, 54f, 72f, 88f)
        c.drawRoundRect(r, 12f, 12f, p)
    }

    private fun drawArms(c: Canvas, color: Int) {
        arm(c, true, po.armL, color)
        arm(c, false, po.armR, color)
    }

    private fun arm(c: Canvas, left: Boolean, code: Int, color: Int) {
        val o: Float
        val len: Float
        when (code) {
            Pose.ARM_WAVE -> { o = 140f + sin(tt * 14f) * 18f; len = 22f }
            Pose.ARM_UP -> { o = 165f + sin(tt * 10f) * 12f * po.wave; len = 22f }
            Pose.ARM_POKE -> { o = 95f + sin(tt * 12f) * 8f; len = 27f }
            Pose.ARM_BEG -> { o = -55f; len = 18f }
            Pose.ARM_MOUTH -> { o = -104f; len = 21f }
            Pose.ARM_MUG -> { o = -72f; len = 18f }
            else -> { o = 4f; len = 22f }
        }
        val sx = if (left) 25.5f else 74.5f
        c.save()
        c.rotate(if (left) o else -o, sx, 60f)
        fill(color)
        r.set(sx - 5.5f, 57f, sx + 5.5f, 60f + len)
        c.drawRoundRect(r, 5.5f, 5.5f, p)
        fill(skinC)
        c.drawCircle(sx, 60f + len, 5f, p)
        c.restore()
    }

    private fun drawEars(c: Canvas) {
        if (look.ears == 0) return
        for (m in 0..1) {
            c.save()
            if (m == 1) c.scale(-1f, 1f, 50f, 0f)
            when (look.ears) {
                1 -> {
                    fill(hairC, hairSh); tri(c, 22f, 26f, 24f, 2f, 44f, 14f)
                    fill(PINK); tri(c, 27f, 22f, 28f, 9f, 38f, 15f)
                }
                2 -> {
                    c.rotate(-8f, 36f, 24f)
                    fill(hairC, hairSh); r.set(30f, -9f, 43f, 24f); c.drawOval(r, p)
                    fill(PINK_LIGHT); r.set(33f, -4f, 40f, 19f); c.drawOval(r, p)
                }
                3 -> {
                    fill(hairC, hairSh); tri(c, 21f, 32f, 19f, -3f, 47f, 16f)
                    fill(shade(hairC, 0.45f)); tri(c, 25f, 26f, 24f, 6f, 38f, 16f)
                }
            }
            c.restore()
        }
    }

    private fun drawHead(c: Canvas) {
        // hair behind the face
        fill(hairC, hairSh)
        when (look.hair) {
            1 -> { r.set(20f, 12f, 80f, 66f); c.drawRoundRect(r, 26f, 26f, p) }
            2 -> c.drawCircle(50f, 38f, 27f, p)
            else -> c.drawCircle(50f, 38f, 29f, p)
        }
        if (look.hair == 2) for (s in SPIKES) tri(c, s[0], s[1], s[2], s[3], s[4], s[5])
        drawEars(c)

        // face, red flush when overheating, blush
        fill(skinC, faceSh); r.set(26f, 16f, 74f, 60f); c.drawOval(r, p)
        if (po.flush) { fill(0x55FF3B30); c.drawOval(r, p) }
        fill(0x66FF6F91)
        r.set(27f, 47f, 37f, 53f); c.drawOval(r, p)
        r.set(63f, 47f, 73f, 53f); c.drawOval(r, p)

        // eyes
        for (i in 0..1) {
            val ex = if (i == 0) 38f else 62f
            when (po.eyes) {
                Pose.EYES_HAPPY -> {
                    stroke(DARK, 2.6f)
                    path.reset(); path.moveTo(ex - 5f, 45f); path.quadTo(ex, 37f, ex + 5f, 45f)
                    c.drawPath(path, p)
                }
                Pose.EYES_CLOSED -> {
                    stroke(DARK, 2.4f)
                    path.reset(); path.moveTo(ex - 5f, 43f); path.quadTo(ex, 48f, ex + 5f, 43f)
                    c.drawPath(path, p)
                }
                Pose.EYES_BLINK -> { stroke(DARK, 2.4f); c.drawLine(ex - 5f, 44f, ex + 5f, 44f, p) }
                Pose.EYES_X -> {
                    stroke(DARK, 2.4f)
                    c.drawLine(ex - 4f, 40f, ex + 4f, 48f, p)
                    c.drawLine(ex - 4f, 48f, ex + 4f, 40f, p)
                }
                else -> {
                    val big = po.eyes == Pose.EYES_BIG
                    val dx = po.lookX * 2.5f
                    val hw = if (big) 6f else 5f
                    fill(eyeC); r.set(ex - hw, if (big) 34f else 36f, ex + hw, if (big) 52f else 51f); c.drawOval(r, p)
                    fill(shade(eyeC, 0.4f)); r.set(ex - 3f + dx, 39f, ex + 3f + dx, 50f); c.drawOval(r, p)
                    fill(Color.WHITE)
                    c.drawCircle(ex + 1.5f + dx, 40f, if (big) 3f else 2.3f, p)
                    c.drawCircle(ex - 1.5f + dx, 47f, if (big) 1.8f else 1.1f, p)
                    if (big && i == 0) { fill(0xFF74C0FC.toInt()); c.drawCircle(ex - 4f, 54f, 1.6f, p) }
                    if (po.eyes == Pose.EYES_HALF) { // sleepy eyelids
                        fill(skinC, faceSh); r.set(ex - hw - 1f, 34f, ex + hw + 1f, 42.5f); c.drawRect(r, p)
                        stroke(DARK, 2f); c.drawLine(ex - hw, 42.5f, ex + hw, 42.5f, p)
                    }
                }
            }
        }
        if (po.shades || look.acc == 7) drawShades(c, look.acc == 7)

        // mouth
        path.reset()
        when (po.mouth) {
            Pose.MOUTH_OPEN -> {
                fill(0xFFD6336C.toInt())
                path.moveTo(45f, 53f); path.quadTo(50f, 61f, 55f, 53f); path.close()
                c.drawPath(path, p)
            }
            Pose.MOUTH_SMALL_O -> { fill(0xFF7A1F3D.toInt()); c.drawCircle(50f, 55f, 2.4f, p) }
            Pose.MOUTH_YAWN -> {
                fill(0xFF7A1F3D.toInt()); r.set(44f, 49f, 56f, 62f); c.drawOval(r, p)
                fill(0xFFFF8FA3.toInt()); r.set(46.5f, 56f, 53.5f, 61f); c.drawOval(r, p)
            }
            Pose.MOUTH_FROWN -> {
                stroke(DARK, 2f)
                path.moveTo(45f, 56f); path.quadTo(50f, 51f, 55f, 56f)
                c.drawPath(path, p)
            }
            Pose.MOUTH_FLAT -> { stroke(DARK, 2f); c.drawLine(46f, 54f, 54f, 54f, p) }
            Pose.MOUTH_WAVY -> {
                stroke(DARK, 1.8f)
                path.moveTo(44f, 54f); path.lineTo(47f, 52.5f); path.lineTo(50f, 54.5f)
                path.lineTo(53f, 52.5f); path.lineTo(56f, 54f)
                c.drawPath(path, p)
            }
            else -> {
                stroke(DARK, 2f)
                path.moveTo(45f, 53f); path.quadTo(50f, 57f, 55f, 53f)
                c.drawPath(path, p)
            }
        }

        // fringe
        fill(hairC, hairSh); path.reset()
        when (look.hair) {
            1 -> { // short bob: straight fringe + side locks
                path.moveTo(24f, 40f); path.cubicTo(24f, 8f, 76f, 8f, 76f, 40f)
                path.lineTo(76f, 32f); path.quadTo(50f, 42f, 24f, 32f); path.close()
                c.drawPath(path, p)
                r.set(22f, 28f, 31f, 58f); c.drawRoundRect(r, 4f, 4f, p)
                r.set(69f, 28f, 78f, 58f); c.drawRoundRect(r, 4f, 4f, p)
            }
            2 -> { // spiky fringe
                path.moveTo(24f, 40f); path.cubicTo(24f, 10f, 76f, 10f, 76f, 40f)
                path.lineTo(70f, 24f); path.lineTo(64f, 38f); path.lineTo(56f, 20f); path.lineTo(50f, 36f)
                path.lineTo(44f, 20f); path.lineTo(36f, 38f); path.lineTo(30f, 24f); path.close()
                c.drawPath(path, p)
            }
            3 -> { // long hair, centre parting
                path.moveTo(25f, 42f); path.cubicTo(25f, 8f, 75f, 8f, 75f, 42f)
                path.quadTo(62f, 34f, 50f, 20f); path.quadTo(38f, 34f, 25f, 42f); path.close()
                c.drawPath(path, p)
            }
            else -> { // zigzag bangs
                path.moveTo(25f, 40f); path.cubicTo(25f, 8f, 75f, 8f, 75f, 40f)
                path.lineTo(68f, 26f); path.lineTo(61f, 35f); path.lineTo(50f, 24f)
                path.lineTo(39f, 35f); path.lineTo(32f, 26f); path.close()
                c.drawPath(path, p)
            }
        }
        if (look.threeD) { // hair shine
            stroke(0x66FFFFFF, 3f)
            path.reset(); path.moveTo(32f, 24f); path.quadTo(50f, 12f, 68f, 24f)
            c.drawPath(path, p)
        }

        // accessories
        when (look.acc) {
            1 -> { // bow
                fill(RED); path.reset()
                path.moveTo(50f, 63f); path.lineTo(41f, 58f); path.lineTo(41f, 68f); path.close()
                path.moveTo(50f, 63f); path.lineTo(59f, 58f); path.lineTo(59f, 68f); path.close()
                c.drawPath(path, p)
                c.drawCircle(50f, 63f, 2.6f, p)
            }
            2 -> { // glasses
                stroke(DARK, 1.8f)
                c.drawCircle(38f, 43f, 8.5f, p)
                c.drawCircle(62f, 43f, 8.5f, p)
                c.drawLine(46.5f, 43f, 53.5f, 43f, p)
            }
        }
        if (look.acc == 3 || po.phones) drawPhones(c)
    }

    private fun drawShades(c: Canvas, star: Boolean) {
        for (i in 0..1) {
            val ex = if (i == 0) 38f else 62f
            if (star) {
                fill(0xFFFFC145.toInt()); star5(c, ex, 43f, 10.5f)
                fill(0xFFFF6F91.toInt()); star5(c, ex, 43f, 6.5f)
            } else {
                fill(0xFF1B1B2F.toInt()); r.set(ex - 9f, 37f, ex + 9f, 50f); c.drawRoundRect(r, 6f, 6f, p)
                stroke(0x88FFFFFF.toInt(), 1.4f); c.drawLine(ex - 5f, 40f, ex - 1f, 40f, p)
            }
        }
        stroke(0xFF1B1B2F.toInt(), 1.6f)
        c.drawLine(46f, 43f, 54f, 43f, p)
    }

    private fun drawPhones(c: Canvas) {
        stroke(0xFF30343F.toInt(), 4f)
        path.reset(); path.moveTo(23f, 44f); path.cubicTo(20f, -2f, 80f, -2f, 77f, 44f)
        c.drawPath(path, p)
        fill(outC)
        r.set(14f, 36f, 27f, 56f); c.drawOval(r, p)
        r.set(73f, 36f, 86f, 56f); c.drawOval(r, p)
    }

    // ---------------- props: hats, scarf, mug ----------------

    private fun drawProps(c: Canvas) {
        if (po.scarf) {
            fill(RED); r.set(29f, 61f, 71f, 69f); c.drawRoundRect(r, 4f, 4f, p)
            fill(Color.WHITE)
            for (i in 0..2) { r.set(35f + i * 13f, 61f, 38f + i * 13f, 69f); c.drawRect(r, p) }
            fill(RED); r.set(60f, 66f, 68f, 84f); c.drawRoundRect(r, 3f, 3f, p)
        }
        if (po.hat != Pose.HAT_NONE) {
            drawHat(c)
        } else {
            if (po.nightcap) drawNightcap(c)
            else if (look.acc == 5) drawCrown(c)
            if (look.acc == 4) drawHalo(c)
        }
        if (po.mug) drawMug(c)
    }

    private fun drawHat(c: Canvas) {
        when (po.hat) {
            Pose.HAT_PUMPKIN -> {
                fill(0xFFF08A24.toInt()); r.set(27f, -1f, 73f, 22f); c.drawOval(r, p)
                stroke(0xFFC2661A.toInt(), 1.6f)
                for (i in -1..1) {
                    path.reset(); path.moveTo(50f + i * 11f, 0f)
                    path.quadTo(50f + i * 17f, 10f, 50f + i * 11f, 21f)
                    c.drawPath(path, p)
                }
                fill(0xFF3E8E41.toInt()); r.set(47f, -6f, 53f, 2f); c.drawRoundRect(r, 2f, 2f, p)
            }
            Pose.HAT_SANTA -> {
                fill(0xFFE63946.toInt()); path.reset()
                path.moveTo(26f, 22f); path.cubicTo(28f, 2f, 52f, -6f, 82f, 12f); path.lineTo(76f, 22f); path.close()
                c.drawPath(path, p)
                fill(Color.WHITE); r.set(24f, 18f, 76f, 27f); c.drawRoundRect(r, 4f, 4f, p)
                c.drawCircle(83f, 13f, 5f, p)
            }
            Pose.HAT_PARTY -> {
                fill(0xFFFF6FB5.toInt()); tri(c, 38f, 20f, 50f, -8f, 62f, 20f)
                fill(0xFFFFD166.toInt())
                c.drawCircle(50f, -8f, 3f, p); c.drawCircle(46f, 8f, 1.6f, p)
                c.drawCircle(54f, 12f, 1.6f, p); c.drawCircle(50f, 2f, 1.6f, p)
            }
            Pose.HAT_TRICOLOR -> {
                fill(0xFFFF9933.toInt()); r.set(26f, 11f, 74f, 14.6f); c.drawRect(r, p)
                fill(Color.WHITE); r.set(26f, 14.6f, 74f, 18.2f); c.drawRect(r, p)
                fill(0xFF138808.toInt()); r.set(26f, 18.2f, 74f, 21.8f); c.drawRect(r, p)
            }
            Pose.HAT_FLOWER -> {
                for (i in 0..4) {
                    val x = 28f + i * 11f
                    val y = 16f - 9f * sin(i / 4f * 3.1416f)
                    fill(0xFF4CAF50.toInt()); r.set(x - 5f, y - 1.5f, x + 5f, y + 2f); c.drawOval(r, p)
                    fill(0xFFFFB7D5.toInt()); c.drawCircle(x, y, 4.2f, p)
                    fill(0xFFFFD166.toInt()); c.drawCircle(x, y, 1.6f, p)
                }
            }
        }
    }

    private fun drawNightcap(c: Canvas) {
        fill(0xFF4D6BFE.toInt()); path.reset()
        path.moveTo(26f, 22f); path.cubicTo(30f, 0f, 58f, -6f, 80f, 12f); path.lineTo(76f, 24f); path.close()
        c.drawPath(path, p)
        fill(Color.WHITE); r.set(24f, 18f, 76f, 26f); c.drawRoundRect(r, 4f, 4f, p)
        c.drawCircle(81f, 13f, 4.5f, p)
    }

    private fun drawCrown(c: Canvas) {
        fill(0xFFFFC145.toInt()); path.reset()
        path.moveTo(34f, 15f); path.lineTo(35f, 3f); path.lineTo(43f, 10f); path.lineTo(50f, 0f)
        path.lineTo(57f, 10f); path.lineTo(65f, 3f); path.lineTo(66f, 15f); path.close()
        c.drawPath(path, p)
        fill(RED); c.drawCircle(50f, 11f, 2f, p)
    }

    private fun drawHalo(c: Canvas) {
        stroke(0x66FFF1B8, 5f); r.set(34f, -2f, 66f, 6f); c.drawOval(r, p)
        stroke(0xFFFFD166.toInt(), 2.6f); c.drawOval(r, p)
    }

    private fun drawMug(c: Canvas) {
        fill(Color.WHITE); r.set(52f, 58f, 61f, 68f); c.drawRoundRect(r, 2f, 2f, p)
        fill(0xFF6B4226.toInt()); r.set(52.5f, 59f, 60.5f, 61f); c.drawRect(r, p)
        stroke(Color.WHITE, 1.6f); r.set(59f, 60f, 64f, 66f); c.drawOval(r, p)
        stroke(0xAAFFFFFF.toInt(), 1.2f)
        for (i in 0..1) {
            val x = 54f + i * 4f
            path.reset(); path.moveTo(x, 56f); path.quadTo(x + 2f * sin(tt * 4f + i), 52f, x, 48f)
            c.drawPath(path, p)
        }
    }

    // ---------------- mood effects ----------------

    private fun drawMood(c: Canvas) {
        if (po.festival == Pose.FESTIVAL_DIYA) {
            fill(0xFFB5651D.toInt()); path.reset()
            path.moveTo(4f, 96f); path.quadTo(14f, 110f, 24f, 96f); path.close()
            c.drawPath(path, p)
            c.save(); c.translate(14f, 93f); c.scale(1f, 1f + sin(tt * 12f) * 0.12f)
            fill(0xFFFFC145.toInt()); path.reset()
            path.moveTo(0f, -8f); path.cubicTo(-4f, -3f, -3f, 2f, 0f, 2f); path.cubicTo(3f, 2f, 4f, -3f, 0f, -8f)
            c.drawPath(path, p)
            c.restore()
        }
        if (po.festival == Pose.FESTIVAL_PETALS) {
            fill(0xFFFFB7D5.toInt())
            for (i in 0..4) {
                val ph = (tt * 0.25f + i * 0.2f) % 1f
                val x = 10f + i * 18f + sin(tt * 2f + i) * 5f
                val y = -4f + ph * 118f
                c.save(); c.rotate(ph * 360f, x, y)
                r.set(x - 2.2f, y - 1.4f, x + 2.2f, y + 1.4f); c.drawOval(r, p)
                c.restore()
            }
        }
        if (po.hearts) {
            heart(c, 88f, 14f + sin(tt * 4f) * 2f, 1.3f, PINK)
            heart(c, 12f, 24f + sin(tt * 3.3f) * 2f, 1f, PINK_LIGHT)
        }
        if (po.sparkles) {
            fill(0xFFFFE08A.toInt())
            for (i in 0..3) {
                val s = 2.4f + 1.8f * (0.5f + 0.5f * sin(tt * 4f + i * 1.7f))
                star4(c, SPARK_X[i], SPARK_Y[i], s)
            }
        }
        if (po.zzz) {
            for (i in 0..2) {
                val ph = (tt * 0.7f + i * 0.33f) % 1f
                text(c, "z", 78f + i * 5f + ph * 6f, 24f - ph * 20f - i * 4f, 7f + i * 2f + ph * 3f, 0xFF6C7BFF.toInt(), (255 * (1f - ph)).toInt())
            }
        }
        if (po.notes) {
            for (i in 0..1) {
                val ph = (tt * 1.2f + i * 0.5f) % 1f
                text(c, if (i == 0) "♪" else "♫", 86f + sin(tt * 3f + i) * 4f, 32f - ph * 26f, 10f, 0xFFFF6F91.toInt(), (255 * (1f - ph)).toInt())
            }
        }
        if (po.cloud) {
            fill(0xFF9AA5B8.toInt())
            r.set(30f, -8f, 54f, 4f); c.drawOval(r, p)
            r.set(42f, -10f, 70f, 6f); c.drawOval(r, p)
            fill(0xFF74C0FC.toInt())
            for (i in 0..2) {
                val ph = (tt * 1.5f + i * 0.33f) % 1f
                c.drawCircle(38f + i * 11f, 4f + ph * 16f, 1.4f, p)
            }
        }
        if (po.stars) {
            fill(0xFFFFD166.toInt())
            for (i in 0..2) {
                val a = tt * 3f + i * 2.094f
                star4(c, 50f + cos(a) * 28f, 8f + sin(a) * 5f, 3.2f)
            }
        }
        if (po.moon) {
            c.save(); c.translate(14f, 16f); c.scale(1.5f, 1.5f)
            fill(0xFFFFD166.toInt()); c.drawPath(moonPath, p)
            c.restore()
        }
        if (po.shh) text(c, "shh", 68f, 44f, 8f, 0xFF7A6A8C.toInt())
        if (po.bubble > 0f) {
            val cx = 58f + po.bubble * 4f
            val cy = 52f - po.bubble * 2f
            val rr = 2f + po.bubble * 11f
            fill(0x55BEE3FF); c.drawCircle(cx, cy, rr, p)
            stroke(0xAA74C0FC.toInt(), 0.9f); c.drawCircle(cx, cy, rr, p)
            fill(0xAAFFFFFF.toInt()); c.drawCircle(cx - rr * 0.35f, cy - rr * 0.35f, rr * 0.18f, p)
        }
        if (po.worried) { // sweat drop
            c.save(); c.translate(87f, 22f)
            fill(0xFF74C0FC.toInt()); path.reset()
            path.moveTo(0f, 0f); path.cubicTo(-6f, 8f, -5f, 14f, 0f, 14f); path.cubicTo(5f, 14f, 6f, 8f, 0f, 0f)
            c.drawPath(path, p)
            c.restore()
        }
    }

    private companion object {
        const val HEAD_X = 50f
        const val HEAD_Y = 36f
        const val HEAD_R = 27f
        const val DEG = 0.0174533f
        val DARK = 0xFF2B2D42.toInt()
        val PINK = 0xFFFF6F91.toInt()
        val PINK_LIGHT = 0xFFFFB3C7.toInt()
        val RED = 0xFFE63946.toInt()
        val SPIKES = arrayOf(
            floatArrayOf(28f, 18f, 34f, -4f, 46f, 12f),
            floatArrayOf(40f, 12f, 52f, -9f, 62f, 12f),
            floatArrayOf(56f, 12f, 68f, -4f, 74f, 18f)
        )
        val SPARK_X = floatArrayOf(16f, 86f, 76f, 10f)
        val SPARK_Y = floatArrayOf(20f, 40f, 6f, 60f)
    }
}
