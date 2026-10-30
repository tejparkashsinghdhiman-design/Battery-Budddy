package com.example.batterybuddy

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.View
import java.io.File
import java.util.Calendar
import java.util.Random
import kotlin.math.PI
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
 * The buddy: the brain (what is she doing right now?) plus a Sprite that draws it.
 *
 * Modes: SIMPLE fits the view (mini previews, widget). OVERLAY is the status-bar window: the view is as wide as the
 * screen so she can wander along it, and she sits [rightMargin] px from the right edge. STAGE is the wide app preview.
 *
 * Optimised: no allocations per frame, adaptive frame rate (slow when sleeping, resting, in battery saver),
 * everything stops when the view is hidden or the screen is off, sensors only run when 3D tilt is on and visible.
 */
class BuddyView(context: Context) : View(context), SensorEventListener {
    private val art = Sprite()
    private val pose = Pose()
    private val prefs = Buddy.prefs(context)
    private val density = context.resources.displayMetrics.density
    private val random = Random()

    var mode = MODE_SIMPLE
    var charW = 0f
    var rightMargin = 0f

    var look = Look()
        set(v) {
            if (v != field) {
                field = v
                art.setLook(v)
                syncSensor()
                invalidate()
            }
        }
    var behave = Behave()
    var fullBody = false
    var image: Bitmap? = null
        set(v) {
            field = v
            art.setImage(v)
            invalidate()
        }
    var state = BuddyState()
        set(v) {
            val old = field
            field = v
            onState(old, v)
            invalidate()
        }

    private val bp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val tail = Path()

    // 3D
    private val cam = Camera().apply { setLocation(0f, 0f, -3.5f) }
    private val m3 = Matrix()
    private var ry = 0f
    private var rx = 0f
    private var tiltY = 0f
    private var tiltX = 0f
    private var lastSensor = 0L
    private val sign: Float by lazy { detectSign() }

    // geometry of the current frame
    private var curHx = 0f
    private var curCw = 0f
    private var curCh = 0f

    // timeline
    private var lastFrame = 0L
    private var danceUntil = 0L
    private var act = -1
    private var actStart = 0L
    private var actDur = 0L
    private var nextIdleAt = 0L
    private var hourCached = 12
    private var seasonCached = 0
    private var clockAt = 0L

    // wandering
    private var walk = W_HOME
    private var walkX = 0f
    private var walkTarget = 0f
    private var atStart = 0L
    private var atUntil = 0L
    private var holeVisit = false
    private var nextWanderAt = 0L
    private var cutoutX = -1f

    // pocket pet
    private var petPhase = 0
    private var petT0 = 0L
    private var nextPetAt = 0L
    private var petX = 0f
    private var petHop = 0f

    // speech bubble
    private var bubbleText: String? = null
    private var bubbleUntil = 0L

    // scripted scenes (the "Try her moods" buttons)
    private var sceneState: BuddyState? = null
    private var sceneUntil = 0L
    private var sceneTod = -1
    private var sceneSeason = -1
    private var lastSceneId = 0L

    private var loadedRev = -1L
    private var ready = false
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reload() }

    init {
        reload()
        art.setLook(look)
        val now = SystemClock.uptimeMillis()
        nextIdleAt = now + rnd(30_000L, 60_000L)
        nextWanderAt = now + rnd(90_000L, 180_000L)
        nextPetAt = now + rnd(15_000L, 30_000L)
        ready = true
    }

    private fun rnd(lo: Long, hi: Long): Long = lo + (random.nextFloat() * (hi - lo)).toLong()

    private fun reload() {
        look = Look.load(prefs)
        behave = Behave.load(prefs)
        fullBody = prefs.getBoolean("sprite", false)
        val rev = prefs.getLong("rev", 0L)
        if (rev != loadedRev) {
            loadedRev = rev
            image = Buddy.loadImage(context)
        }
    }

    // ---------------- public controls ----------------

    fun say(text: String, ms: Long = 3500L) {
        bubbleText = text
        bubbleUntil = SystemClock.uptimeMillis() + ms
        invalidate()
    }

    fun poke() {
        startAction(A_POKE)
        say("Look away for 20 s \uD83D\uDC40", 5000L)
    }

    fun setCutout(centerX: Float) {
        cutoutX = centerX
    }

    fun startAction(a: Int) {
        val now = SystemClock.uptimeMillis()
        when (a) {
            A_WALK -> beginWander(false)
            A_HOLE -> beginWander(true)
            A_PET -> { petPhase = 1; petT0 = now }
            A_DANCE -> danceUntil = now + 7000L
            else -> { act = a; actStart = now; actDur = DURATIONS[a] }
        }
        invalidate()
    }

    fun play(s: Scene) {
        if (s.id == lastSceneId) return
        lastSceneId = s.id
        val now = SystemClock.uptimeMillis()
        sceneUntil = now + 9000L
        sceneState = s.state
        sceneTod = s.tod
        sceneSeason = s.season
        val st = s.state
        if (st != null && st.charging && st.level >= 100) danceUntil = now + 7000L
        if (s.action >= 0) startAction(s.action)
        s.bubble?.let { say(it, 5000L) }
        invalidate()
    }

    private fun onState(old: BuddyState, new: BuddyState) {
        if (new.charging && new.level >= 100 && !(old.charging && old.level >= 100)) {
            danceUntil = SystemClock.uptimeMillis() + 6500L
        }
    }

    // ---------------- lifecycle / sensors ----------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        reload()
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
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        tiltY = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
        tiltX = (e.values[2] / 9.81f - 0.35f).coerceIn(-1f, 1f)
        lastSensor = SystemClock.uptimeMillis()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---------------- 3D ----------------

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
        val tilting = look.tilt && SystemClock.uptimeMillis() - lastSensor < 1000L
        val targetY = if (tilting) tiltY * 40f else sin(t * 1.1f) * 28f
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

    // ---------------- clock ----------------

    private fun updateClock(now: Long) {
        if (clockAt != 0L && now - clockAt < 60_000L) return
        clockAt = now
        val cal = Calendar.getInstance()
        hourCached = cal.get(Calendar.HOUR_OF_DAY)
        seasonCached = DayClock.season(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun effective(now: Long): BuddyState {
        val s = sceneState
        return if (now < sceneUntil && s != null) s else state
    }

    // ---------------- wandering and the pet ----------------

    private fun beginWander(hole: Boolean) {
        if (mode == MODE_SIMPLE) return
        holeVisit = hole
        val room = curHx + curCw / 2f // from her centre to the left screen edge
        walkTarget = if (hole) {
            when {
                mode == MODE_STAGE -> -(width * 0.3f)
                cutoutX > 0f -> cutoutX - (curHx + curCw / 2f)
                else -> 0f
            }
        } else {
            -min(max(0f, room - curCw), (60f + random.nextFloat() * 70f) * density)
        }
        if (holeVisit && walkTarget > -curCw * 1.2f) { // too close or no camera hole known: just stroll
            holeVisit = false
            walkTarget = -min(max(0f, room - curCw), 70f * density)
        }
        if (walkTarget > -curCw * 0.5f) {
            nextWanderAt = SystemClock.uptimeMillis() + rnd(120_000L, 240_000L)
            return
        }
        walk = W_OUT
    }

    private fun updateWander(now: Long, dt: Float, canRoam: Boolean) {
        if (mode == MODE_SIMPLE) { walk = W_HOME; walkX = 0f; return }
        val speed = 42f * density
        when (walk) {
            W_HOME -> if (canRoam && now >= nextWanderAt && act < 0) {
                beginWander((cutoutX > 0f || mode == MODE_STAGE) && random.nextFloat() < 0.35f)
            }
            W_OUT -> if (!canRoam) {
                walk = W_BACK
            } else {
                walkX -= speed * dt
                if (walkX <= walkTarget) {
                    walkX = walkTarget
                    walk = W_AT
                    atStart = now
                    atUntil = now + if (holeVisit) 4200L else 2600L
                }
            }
            W_AT -> if (now >= atUntil || !canRoam) walk = W_BACK
            W_BACK -> {
                walkX += speed * dt
                if (walkX >= 0f) {
                    walkX = 0f
                    walk = W_HOME
                    nextWanderAt = now + rnd(120_000L, 240_000L)
                }
            }
        }
    }

    private fun updatePet(now: Long) {
        if (mode == MODE_SIMPLE || !behave.pet) { petPhase = 0; petX = 0f; petHop = 0f; return }
        val dist = 36f * density / (curCw / 100f).coerceAtLeast(0.01f)
        when (petPhase) {
            0 -> if (now >= nextPetAt) { petPhase = 1; petT0 = now }
            1 -> {
                val u = ((now - petT0) / 900f).coerceIn(0f, 1f)
                petX = -dist * u
                petHop = sin(u * PI.toFloat()) * 10f
                if (u >= 1f) { petPhase = 2; petT0 = now; petHop = 0f }
            }
            2 -> if (now - petT0 > 4500L) { petPhase = 3; petT0 = now }
            3 -> {
                val u = ((now - petT0) / 900f).coerceIn(0f, 1f)
                petX = -dist * (1f - u)
                petHop = sin(u * PI.toFloat()) * 10f
                if (u >= 1f) {
                    petPhase = 0; petX = 0f; petHop = 0f
                    nextPetAt = now + rnd(25_000L, 50_000L)
                }
            }
        }
    }

    // ---------------- what is she doing? ----------------

    private fun applyAction(now: Long, po: Pose) {
        val e = now - actStart
        if (e >= actDur) { act = -1; return }
        val u = e / actDur.toFloat()
        when (act) {
            A_WAVE -> { po.armR = Pose.ARM_WAVE; po.eyes = Pose.EYES_HAPPY; po.mouth = Pose.MOUTH_OPEN }
            A_STRETCH -> {
                po.armL = Pose.ARM_UP; po.armR = Pose.ARM_UP; po.wave = 0f
                po.eyes = Pose.EYES_CLOSED; po.mouth = Pose.MOUTH_YAWN
            }
            A_LOOK -> po.lookX = sin(u * 9.4248f)
            A_BUBBLE -> { po.mouth = Pose.MOUTH_SMALL_O; po.bubble = if (u < 0.75f) u / 0.75f else 0f }
            A_KICK -> {
                po.swing = 3f; po.swingAmp = 26f; po.eyes = Pose.EYES_HAPPY; po.mouth = Pose.MOUTH_OPEN
            }
        }
    }

    private fun computePose(now: Long, st: BuddyState) {
        val po = pose.reset()
        val b = behave
        updateClock(now)
        val sceneOn = now < sceneUntil
        val tod = if (sceneOn && sceneTod >= 0) sceneTod else DayClock.tod(hourCached)
        val season = if (sceneOn && sceneSeason >= 0) sceneSeason else seasonCached
        val t = now / 1000f
        val blink = now % 4000L < 140L

        val hot = st.hot
        val dying = b.drama && !st.charging && st.level <= 5
        val begging = b.drama && !st.charging && st.level in 6..10
        val cold = b.cold && st.cold && !hot
        val yawning = b.drama && !st.charging && st.level in 11..30
        val dance = st.charging && st.level >= 100 && now < danceUntil
        val zoom = b.fast && st.fast && !dance
        val sleeping = b.sleep && st.charging && !st.fast && !dance
        val music = b.music && st.music && !dying && !begging && !hot
        val dnd = b.dnd && st.dnd
        val calm = !(dying || begging || hot || cold)
        var neutral = false

        po.flush = hot
        po.worried = hot

        when {
            dying -> {
                po.eyes = Pose.EYES_X; po.mouth = Pose.MOUTH_FROWN
                po.tilt = 12f; po.swing = 0f; po.swingAmp = 4f; po.bob = 0f; po.stars = true
            }
            begging -> {
                po.eyes = Pose.EYES_BIG; po.mouth = Pose.MOUTH_SMALL_O
                po.armL = Pose.ARM_BEG; po.armR = Pose.ARM_BEG
                po.bob = 0.6f; po.swing = 0.3f; po.swingAmp = 8f
            }
            hot -> {
                po.eyes = if (blink) Pose.EYES_BLINK else Pose.EYES_NORMAL
                po.mouth = Pose.MOUTH_FROWN; po.swing = 0.7f; po.swingAmp = 8f
            }
            cold -> {
                po.eyes = if (blink) Pose.EYES_BLINK else Pose.EYES_NORMAL
                po.mouth = Pose.MOUTH_WAVY; po.scarf = true; po.shiver = 0.9f; po.swing = 0.4f; po.swingAmp = 6f
            }
            dance -> {
                po.eyes = Pose.EYES_HAPPY; po.mouth = Pose.MOUTH_OPEN
                po.armL = Pose.ARM_UP; po.armR = Pose.ARM_UP; po.wave = 1f
                po.jump = abs(sin(t * 6f)) * 7f; po.swing = 2.8f; po.swingAmp = 26f
                po.hearts = true; po.sparkles = true; po.wings = 1f
            }
            zoom -> {
                po.eyes = Pose.EYES_HAPPY; po.mouth = Pose.MOUTH_OPEN
                po.swing = 2.4f; po.swingAmp = 22f; po.bob = 2f
                po.speed = true; po.hearts = true; po.wings = 1f
            }
            sleeping -> {
                po.eyes = Pose.EYES_CLOSED; po.mouth = Pose.MOUTH_SMILE
                po.zzz = true; po.bob = 0.5f; po.swing = 0.25f; po.swingAmp = 7f
            }
            yawning -> {
                if ((now / 1000L) % 6L < 2L) {
                    po.eyes = Pose.EYES_CLOSED; po.mouth = Pose.MOUTH_YAWN; po.armR = Pose.ARM_MOUTH
                } else {
                    po.eyes = Pose.EYES_HALF; po.mouth = Pose.MOUTH_FLAT; po.bob = 0.8f; po.swing = 0.5f
                }
            }
            dnd -> {
                po.eyes = Pose.EYES_HALF; po.mouth = Pose.MOUTH_FLAT; po.armR = Pose.ARM_MOUTH
                po.moon = true; po.shh = true; po.bob = 0.5f; po.swing = 0.3f
            }
            else -> neutral = true
        }

        if (music) {
            po.phones = true; po.notes = true
            po.bob = max(po.bob, 2.4f); po.swing = max(po.swing, 1.4f)
            if (po.eyes == Pose.EYES_CLOSED || po.eyes == Pose.EYES_HALF) po.eyes = Pose.EYES_HAPPY
            if (po.mouth == Pose.MOUTH_YAWN || po.mouth == Pose.MOUTH_FLAT) po.mouth = Pose.MOUTH_OPEN
            po.zzz = false; po.moon = false; po.shh = false
            if (po.armR == Pose.ARM_MOUTH) po.armR = Pose.ARM_DOWN
        }

        if (neutral) {
            po.eyes = if (blink) Pose.EYES_BLINK else Pose.EYES_NORMAL
            if (!music) {
                if (act < 0 && walk == W_HOME && b.idle && now >= nextIdleAt) {
                    startAction(random.nextInt(5))
                    nextIdleAt = now + rnd(45_000L, 90_000L)
                }
                if (act in 0..4) applyAction(now, po)
            }
            when (walk) {
                W_OUT, W_BACK -> { po.swing = 3.2f; po.swingAmp = 24f; po.bob = 2.6f; po.mouth = Pose.MOUTH_OPEN }
                W_AT -> if (holeVisit) {
                    val e = now - atStart
                    po.scale = when {
                        e < 400L -> 1f - e / 400f
                        e < 2600L -> 0f
                        e < 3000L -> (e - 2600L) / 400f
                        else -> 1f
                    }
                    if (e >= 3000L) { po.armR = Pose.ARM_WAVE; po.eyes = Pose.EYES_HAPPY; po.mouth = Pose.MOUTH_OPEN }
                } else {
                    po.armR = Pose.ARM_WAVE; po.eyes = Pose.EYES_HAPPY; po.mouth = Pose.MOUTH_OPEN
                }
            }
        }

        if (act == A_POKE) {
            if (calm && now - actStart < actDur) {
                po.armR = Pose.ARM_POKE
                if (po.eyes == Pose.EYES_CLOSED || po.eyes == Pose.EYES_HALF) po.eyes = Pose.EYES_NORMAL
                po.mouth = Pose.MOUTH_OPEN; po.zzz = false
            } else {
                act = -1
            }
        }

        if (calm) {
            if (b.tod) {
                when (tod) {
                    DayClock.NIGHT -> po.nightcap = true
                    DayClock.NOON -> if (po.eyes != Pose.EYES_CLOSED) po.shades = true
                    DayClock.MORNING -> if (neutral && act < 0 && !music && walk == W_HOME) {
                        po.mug = true; po.armR = Pose.ARM_MUG
                    }
                }
            }
            if (b.season) {
                when (season) {
                    DayClock.S_HALLOWEEN -> po.hat = Pose.HAT_PUMPKIN
                    DayClock.S_XMAS -> po.hat = Pose.HAT_SANTA
                    DayClock.S_NEWYEAR -> { po.hat = Pose.HAT_PARTY; po.sparkles = true }
                    DayClock.S_VALENTINE -> po.hearts = true
                    DayClock.S_INDIA -> po.hat = Pose.HAT_TRICOLOR
                    DayClock.S_DIWALI -> { po.festival = Pose.FESTIVAL_DIYA; po.sparkles = true }
                    DayClock.S_SAKURA -> { po.hat = Pose.HAT_FLOWER; po.festival = Pose.FESTIVAL_PETALS }
                }
            }
            if (po.hat != Pose.HAT_NONE) po.nightcap = false
            if (b.mood && neutral) {
                if (st.hearts >= 4) po.sparkles = true else if (st.hearts <= 1) po.cloud = true
            }
        }
        if (walk != W_HOME) po.wings = 1f
    }

    // ---------------- drawing ----------------

    private fun box(c: Canvas, x: Float) {
        c.translate(x, 0f)
        c.scale(curCw / 100f, curCh / 125f)
        c.scale(0.92f, 0.92f, 50f, 125f) // headroom for tall ears, hats and spikes
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceIn(0f, 0.25f)
        lastFrame = now
        val st = effective(now)

        when (mode) {
            MODE_OVERLAY -> { curCw = charW; curCh = charW * 1.25f; curHx = width - rightMargin - curCw }
            MODE_STAGE -> { curCh = height.toFloat(); curCw = curCh * 0.8f; curHx = (width - curCw) / 2f }
            else -> { curCw = width.toFloat(); curCh = height.toFloat(); curHx = 0f }
        }
        if (curCw <= 0f || curCh <= 0f) return

        val frozen = !st.charging && st.level <= 5
        val t = if (frozen) 0f else now / 1000f * look.speed
        val canRoam = mode != MODE_SIMPLE && behave.wander && !st.saver && !st.charging && !st.hot &&
            !st.cold && st.level > 30 && !st.dnd && !(behave.music && st.music)
        updateWander(now, dt, canRoam)
        updatePet(now)
        computePose(now, st)
        if (look.threeD) updateRotation(t)

        if (mode != MODE_SIMPLE && behave.pet) {
            c.save(); box(c, curHx)
            art.drawPet(c, petX, petHop, t)
            c.restore()
        }
        if (pose.scale > 0.02f) {
            c.save(); box(c, curHx + walkX)
            if (look.threeD) c.concat(cameraMatrix())
            art.ry = ry; art.sign = sign
            art.draw(c, pose, t, fullBody)
            c.restore()
        }
        if (mode == MODE_STAGE && holeVisit && walk != W_HOME) drawFakeHole(c)
        drawBubble(c, now)
        postInvalidateDelayed(frameDelay(st))
    }

    private fun drawFakeHole(c: Canvas) {
        bp.style = Paint.Style.FILL; bp.color = 0xFF111111.toInt()
        c.drawCircle(curHx + curCw / 2f + walkTarget, 8f * density, 5f * density, bp)
    }

    private fun drawBubble(c: Canvas, now: Long) {
        val text = bubbleText ?: return
        if (now > bubbleUntil) { bubbleText = null; return }
        if (mode == MODE_SIMPLE) return
        val size = (curCh * 0.26f).coerceIn(9f * density, 14f * density)
        tp.textSize = size; tp.color = 0xFF3B2A4D.toInt()
        val w = tp.measureText(text) + size * 1.2f
        val h = size * 1.7f
        val right = curHx + walkX + curCw * 0.1f
        val top = curCh * 0.1f
        bp.style = Paint.Style.FILL; bp.color = 0xF5FFFFFF.toInt()
        rect.set(right - w, top, right, top + h)
        c.drawRoundRect(rect, h / 2f, h / 2f, bp)
        tail.reset()
        tail.moveTo(right - h * 0.5f, top + h * 0.5f)
        tail.lineTo(right + h * 0.35f, top + h * 0.75f)
        tail.lineTo(right - h * 0.2f, top + h * 0.85f)
        tail.close()
        c.drawPath(tail, bp)
        c.drawText(text, right - w / 2f, top + h / 2f + size * 0.36f, tp)
    }

    /** Slower when nothing much is happening: this is what keeps her cheap on the battery. */
    private fun frameDelay(st: BuddyState): Long {
        val active = walk != W_HOME || act >= 0 || petPhase != 0 || pose.jump > 0f || pose.speed || pose.notes
        return when {
            st.saver -> 250L
            !st.charging && st.level <= 5 -> 600L
            !st.charging && st.level <= 15 -> 200L
            active -> 55L
            pose.zzz -> 120L
            look.threeD -> 66L
            else -> 80L
        }
    }

    companion object {
        const val MODE_SIMPLE = 0
        const val MODE_OVERLAY = 1
        const val MODE_STAGE = 2

        const val A_WAVE = 0
        const val A_STRETCH = 1
        const val A_LOOK = 2
        const val A_BUBBLE = 3
        const val A_KICK = 4
        const val A_POKE = 5
        const val A_DANCE = 6
        const val A_WALK = 7
        const val A_HOLE = 8
        const val A_PET = 9

        private const val W_HOME = 0
        private const val W_OUT = 1
        private const val W_AT = 2
        private const val W_BACK = 3

        private val DURATIONS = longArrayOf(2600L, 2600L, 3000L, 2600L, 2200L, 4000L)
    }
}
