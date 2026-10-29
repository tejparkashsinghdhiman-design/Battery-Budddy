package com.example.batterybuddy

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.View
import java.io.File
import kotlin.math.abs
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
 * The buddy. Three looks, all sitting on a ledge with swinging legs:
 *  - no image:        the built-in parametric character (see [Look]: hair, ears, outfit, accessories...)
 *  - image:           the image becomes a round head on a little body (body colour sampled from the image)
 *  - image + sprite:  the image is drawn as-is (for full-body transparent PNGs)
 *
 * Two render styles: 2D (flat) and 3D (figurine): real perspective turntable rotation via android.graphics.Camera,
 * parallax between depth layers (tail / hair / legs / body / head), gradient shading and a hair shine.
 * The 3D rotation can follow the phone's tilt. Moods: happy while charging, sweat at <=15% or when overheating
 * (flushed red); she rests (stops animating) at <=15% to save power.
 *
 * The view reads its own settings from SharedPreferences and updates live when they change.
 * Coordinates are a 100 x 125 box scaled to the view.
 */
class BuddyView(context: Context) : View(context), SensorEventListener {
    var look = Look()
        set(value) { if (value != field) { field = value; applyLook() } }
    var image: Bitmap? = null
        set(value) { field = value; rebuild(); invalidate() }
    var sprite = false
        set(value) { field = value; invalidate() }

    private val prefs = Buddy.prefs(context)
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> look = Look.load(prefs) }

    private var level = 100
    private var charging = false
    private var hot = false

    private var hairC = 0
    private var skinC = 0
    private var eyeC = 0
    private var outC = 0
    private var faceSh: Shader? = null
    private var hairSh: Shader? = null
    private var bodySh: Shader? = null
    private var shader: BitmapShader? = null
    private var bodyColor = 0

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val cam = Camera().apply { setLocation(0f, 0f, -3.5f) }
    private val m3 = Matrix()
    private var ry = 0f
    private var rx = 0f
    private var tiltY = 0f
    private var tiltX = 0f
    private var lastSensor = 0L
    private val sign: Float by lazy { detectSign() }
    private var ready = false

    private val resting: Boolean get() = level <= 15 && !charging
    private val happy: Boolean get() = charging && !hot
    private val worried: Boolean get() = hot || resting

    init {
        look = Look.load(prefs)
        applyLook()
        ready = true
    }

    fun setBattery(level: Int, charging: Boolean, hot: Boolean) {
        this.level = level
        this.charging = charging
        this.hot = hot
        invalidate()
    }

    // ---------------- setup ----------------

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

    private fun applyLook() {
        hairC = Look.HAIR_COLORS[look.hairColor]
        skinC = Look.SKIN_COLORS[look.skin]
        eyeC = Look.EYE_COLORS[look.eyes]
        outC = Look.OUTFIT_COLORS[look.outfit]
        faceSh = RadialGradient(
            42f, 28f, 38f,
            intArrayOf(shade(skinC, 1.08f), skinC, shade(skinC, 0.8f)), floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        hairSh = LinearGradient(0f, 4f, 0f, 74f, shade(hairC, 1.3f), shade(hairC, 0.72f), Shader.TileMode.CLAMP)
        bodySh = LinearGradient(26f, 54f, 74f, 90f, shade(outC, 1.25f), shade(outC, 0.72f), Shader.TileMode.CLAMP)
        rebuild()
        syncSensor()
        invalidate()
    }

    private fun rebuild() {
        val b = image
        if (b == null) { shader = null; bodyColor = outC; return }
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
        bodyColor = if (Color.alpha(px) < 128) outC else px
    }

    // ---------------- lifecycle / sensors ----------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        look = Look.load(prefs)
        syncSensor()
    }

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        context.getSystemService(SensorManager::class.java)?.unregisterListener(this)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncSensor()
    }

    private fun syncSensor() {
        if (!ready) return
        val sm = context.getSystemService(SensorManager::class.java) ?: return
        sm.unregisterListener(this)
        if (look.threeD && look.tilt && isAttachedToWindow && isShown) {
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        tiltY = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)           // left/right roll
        tiltX = (e.values[2] / 9.81f - 0.35f).coerceIn(-1f, 1f)    // forward/back pitch
        lastSensor = SystemClock.uptimeMillis()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---------------- 3D helpers ----------------

    /** Which way does a positive rotateY move things? Measured once, so parallax always matches the camera. */
    private fun detectSign(): Float {
        val m = Matrix()
        cam.save(); cam.rotateY(30f); cam.getMatrix(m); cam.restore()
        val r = floatArrayOf(100f, -10f, 100f, 10f)
        val l = floatArrayOf(-100f, -10f, -100f, 10f)
        m.mapPoints(r); m.mapPoints(l)
        return if (abs(r[3] - r[1]) > abs(l[3] - l[1])) 1f else -1f
    }

    private fun updateRotation(t: Float) {
        val tilting = look.tilt && SystemClock.uptimeMillis() - lastSensor < 1000
        val targetY = if (tilting) tiltY * 40f else sin(t * 1.1f) * 28f   // follow the phone, or sway like a turntable
        val targetX = if (tilting) tiltX * 25f else -6f
        ry += (targetY - ry) * 0.25f
        rx += (targetX - rx) * 0.25f
    }

    private fun cameraMatrix(): Matrix {
        cam.save(); cam.rotateY(ry); cam.rotateX(rx); cam.getMatrix(m3); cam.restore()
        m3.preTranslate(-50f, -75f)
        m3.postTranslate(50f, 75f)
        return m3
    }

    /** Draws one depth layer; in 3D, layers nearer the viewer (dz > 0) shift against the rotation = parallax. */
    private inline fun layer(c: Canvas, dz: Float, dy: Float = 0f, block: () -> Unit) {
        c.save()
        c.translate(if (look.threeD) -sign * dz * sin(ry * DEG) else 0f, dy)
        block()
        c.restore()
    }

    // ---------------- drawing ----------------

    override fun onDraw(c: Canvas) {
        c.scale(width / 100f, height / 125f)
        c.scale(0.92f, 0.92f, 50f, 125f) // headroom for tall ears and spikes
        // rest mode: frozen pose, redrawn once a second -> almost no power use
        val t = if (resting) 0f else SystemClock.uptimeMillis() / 1000f * look.speed
        if (look.threeD) {
            updateRotation(t)
            c.concat(cameraMatrix())
        }
        val bmp = image
        when {
            bmp != null && sprite -> layer(c, 0f) { drawSprite(c, bmp, t) }
            bmp != null -> drawPortrait(c, t)
            else -> drawCharacter(c, t)
        }
        postInvalidateDelayed(if (resting) 1000L else if (look.threeD) 50L else 80L) // stops by itself when hidden
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
        val bob = sin(t * 3f) * 1.2f
        layer(c, -2f) { drawLegs(c, t, shade(bodyColor, 0.6f), 0xFF222222.toInt()) }
        layer(c, 0f, bob) { drawBody(c, bodyColor, null) }
        layer(c, 6f, bob) {
            fill(Color.WHITE); p.shader = sh
            c.drawCircle(HEAD_X, HEAD_Y, HEAD_R, p)
            p.shader = null
            stroke(if (hot) RED else Color.WHITE, 2.5f)
            c.drawCircle(HEAD_X, HEAD_Y, HEAD_R, p)
            drawMood(c, t)
        }
    }

    private fun drawCharacter(c: Canvas, t: Float) {
        val bob = sin(t * 3f) * 1.2f
        val blink = SystemClock.uptimeMillis() % 4000 < 140
        layer(c, -10f) { drawTail(c) }
        layer(c, -6f, bob) { drawHairBack(c) }
        layer(c, -2f) { drawLegs(c, t, skinC, shade(outC, 0.7f)) }
        layer(c, 0f, bob) { drawBody(c, outC, bodySh) }
        layer(c, 6f, bob) { drawHead(c, blink); drawMood(c, t) }
    }

    private fun tri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        c.drawPath(path, p)
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
                fill(hairC, hairSh); c.drawOval(RectF(68f, 58f, 92f, 98f), p)
                fill(Color.WHITE); c.drawOval(RectF(70f, 58f, 90f, 72f), p)
                c.restore()
            }
        }
    }

    private fun drawHairBack(c: Canvas) {
        fill(hairC, hairSh)
        when (look.hair) {
            0 -> { // twin tails
                c.drawOval(RectF(9f, 34f, 31f, 78f), p)
                c.drawOval(RectF(69f, 34f, 91f, 78f), p)
            }
            3 -> c.drawRoundRect(RectF(19f, 18f, 81f, 94f), 26f, 26f, p) // long hair behind the shoulders
        }
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

    private fun drawBody(c: Canvas, color: Int, sh: Shader?) {
        fill(color, sh)
        c.drawRoundRect(RectF(28f, 54f, 72f, 88f), 12f, 12f, p)
        c.drawRoundRect(RectF(20f, 58f, 31f, 80f), 5f, 5f, p)
        c.drawRoundRect(RectF(69f, 58f, 80f, 80f), 5f, 5f, p)
        fill(skinC)
        c.drawCircle(25.5f, 82f, 5f, p)
        c.drawCircle(74.5f, 82f, 5f, p)
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
                    fill(hairC, hairSh); c.drawOval(RectF(30f, -9f, 43f, 24f), p)
                    fill(PINK_LIGHT); c.drawOval(RectF(33f, -4f, 40f, 19f), p)
                }
                3 -> {
                    fill(hairC, hairSh); tri(c, 21f, 32f, 19f, -3f, 47f, 16f)
                    fill(shade(hairC, 0.45f)); tri(c, 25f, 26f, 24f, 6f, 38f, 16f)
                }
            }
            c.restore()
        }
    }

    private fun drawHead(c: Canvas, blink: Boolean) {
        // hair behind the face
        fill(hairC, hairSh)
        when (look.hair) {
            1 -> c.drawRoundRect(RectF(20f, 12f, 80f, 66f), 26f, 26f, p)
            2 -> c.drawCircle(50f, 38f, 27f, p)
            else -> c.drawCircle(50f, 38f, 29f, p)
        }
        if (look.hair == 2) { // spiky crown
            for (s in listOf(
                floatArrayOf(28f, 18f, 34f, -4f, 46f, 12f),
                floatArrayOf(40f, 12f, 52f, -9f, 62f, 12f),
                floatArrayOf(56f, 12f, 68f, -4f, 74f, 18f)
            )) tri(c, s[0], s[1], s[2], s[3], s[4], s[5])
        }
        drawEars(c)

        // face, red flush when overheating, blush
        fill(skinC, faceSh); c.drawOval(RectF(26f, 16f, 74f, 60f), p)
        if (hot) { fill(0x55FF3B30); c.drawOval(RectF(26f, 16f, 74f, 60f), p) }
        fill(0x66FF6F91)
        c.drawOval(RectF(27f, 47f, 37f, 53f), p)
        c.drawOval(RectF(63f, 47f, 73f, 53f), p)

        // eyes
        for (ex in floatArrayOf(38f, 62f)) {
            if (happy) {
                stroke(DARK, 2.6f)
                path.reset(); path.moveTo(ex - 5f, 45f); path.quadTo(ex, 37f, ex + 5f, 45f)
                c.drawPath(path, p)
            } else if (blink) {
                stroke(DARK, 2.4f); c.drawLine(ex - 5f, 44f, ex + 5f, 44f, p)
            } else {
                fill(eyeC); c.drawOval(RectF(ex - 5f, 36f, ex + 5f, 51f), p)
                fill(shade(eyeC, 0.4f)); c.drawOval(RectF(ex - 3f, 39f, ex + 3f, 50f), p)
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
                stroke(DARK, 2f)
                path.moveTo(45f, 56f); path.quadTo(50f, 51f, 55f, 56f)
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
                c.drawRoundRect(RectF(22f, 28f, 31f, 58f), 4f, 4f, p)
                c.drawRoundRect(RectF(69f, 28f, 78f, 58f), 4f, 4f, p)
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

        // accessory
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
            3 -> { // headphones
                stroke(0xFF30343F.toInt(), 4f)
                path.reset(); path.moveTo(23f, 44f); path.cubicTo(20f, -2f, 80f, -2f, 77f, 44f)
                c.drawPath(path, p)
                fill(outC)
                c.drawOval(RectF(14f, 36f, 27f, 56f), p)
                c.drawOval(RectF(73f, 36f, 86f, 56f), p)
            }
        }
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

    private companion object {
        const val HEAD_X = 50f
        const val HEAD_Y = 36f
        const val HEAD_R = 27f
        const val DEG = 0.0174533f
        val DARK = 0xFF2B2D42.toInt()
        val PINK = 0xFFFF6F91.toInt()
        val PINK_LIGHT = 0xFFFFB3C7.toInt()
        val RED = 0xFFE63946.toInt()
    }
}
