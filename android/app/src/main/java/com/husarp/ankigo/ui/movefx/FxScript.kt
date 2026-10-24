package com.husarp.ankigo.ui.movefx

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Runs once at its time (the mockup's `tl.at`). */
fun interface FxAt { fun run() }

/** Runs every frame while active (`tl.tween`): [p] = eased progress 0..1, [ms] = mockup ms since the animation began. */
fun interface FxTween { fun run(p: Float, ms: Float) }

/** A colour filter for the target during a hit, by progress 0..1 of the hit; null = none. See [FxColor]. */
fun interface FxFilterAt { fun at(p: Float): FloatArray? }

/**
 * What a look returns (all in mockup ms; the player divides by the speed): [total] = when the last particle is gone (the
 * mockup's return value), [impact] = when the first hit lands on the target, [done] = when sprites and background are back at
 * rest for good (after it only particles fade).
 */
class FxTimes(@JvmField val total: Float, @JvmField val impact: Float, @JvmField val done: Float)

/** A texture to draw as a Showdown-style effect: art key (see [FxArt]), tint (0 = none) and its size in Showdown units (100 for the fx PNGs). */
class FxSprite(@JvmField val key: String, @JvmField val tint: Int = 0, @JvmField val w: Float = 100f, @JvmField val h: Float = 100f)

/** Start state of a [FxScript.psFx] effect: Showdown units (x right, y up, z 0 = attacker ... 200 = target), scale, opacity, time in ms. */
class PsKey(
    @JvmField val x: Float = 0f, @JvmField val y: Float = 0f, @JvmField val z: Float = 0f,
    @JvmField val scale: Float = 1f, @JvmField val opacity: Float = 1f, @JvmField val time: Float = 0f,
)

/** End state of a [FxScript.psFx] effect: every field left null keeps the start value; [time] null = start time + 500 ms. */
class PsEnd(
    @JvmField val x: Float? = null, @JvmField val y: Float? = null, @JvmField val z: Float? = null,
    @JvmField val scale: Float? = null, @JvmField val opacity: Float? = null, @JvmField val time: Float? = null,
)

enum class PsEase(internal val ease: FxEase) { LINEAR(FxEase.LIN), ACCEL(FxEase.IN2), DECEL(FxEase.OUT2) }

/** What a [FxScript.psFx] effect does after its end time: nothing, fade out over 100 ms, or grow x3 while fading over 200 ms. */
enum class PsAfter(internal val tail: Float) { NONE(0f), FADE(100f), EXPLODE(200f) }

/**
 * The look-facing API of the move animation player (R7-03): everything a look needs to script one move, in the units and
 * words of docs/design/attack-anims-v2/v2-fx.js. A look is a function `(FxScript) -> FxTimes` that, called once, fills the
 * timeline and returns its timings; [advance] then runs the timeline (pure, testable without a canvas) and
 * [FxRenderer] draws what it left behind.
 *
 * **Units.** Time is mockup ms (the player maps real time with the speed). Positions you hand in or get back are pixels of the
 * effect layer: get them from [space] ([FxSpace.fromAttacker], [FxSpace.fromTarget], [FxSpace.between]). Sizes, speeds,
 * accelerations and sprite offsets are mockup stage units, like the JS: [particle], [burst] and [Actor.set] multiply with the
 * unit, and flip x with the attack direction, for you. Only drawing code in a [proc] works in pixels (multiply with [u]).
 *
 * **Time and determinism.** All randomness comes from [random] (seeded). A callback that the JS runs every frame with a random
 * test (`tween(..., if (rnd < .3) P(...))`) must use [ticks]: it runs on a fixed 60 fps step of mockup time and catches up, so
 * a slow phone (and a still frame) shows the same amount of particles. Particles are stamped with the time the item ran at,
 * not with the frame that happened to run it.
 *
 * **JS to Kotlin** (v2-fx.js -> here): `tl.at` = [at]; `tl.tween` = [tween], or [ticks] when it spawns randomly; `P({...})` = [particle];
 * `burst(...)` = [burst]; `proc(s, e, fn, layer)` = [proc] (draws with an [FxCanvas]); `att.set({x, y, sx, sy, f})` = `attacker.set(...)`,
 * `def.set` = `target.set(...)`; `hit` / `lunge` = [hit] / [lunge]; `flashOnce` = [flashOnce]; `dim = ...` = [dim]; `psFx` = [psFx];
 * `cubic` = [FxCubic]; `jag` = [jag]; `kf` / `lerp` = [FxMath]; `E.in2` etc. = [FxEase]; `rnd(a, b)` = [rnd]; `Math.random() < p` =
 * [chance]; `[AX + dx, AY + dy]` / `[DX + dx, DY + dy]` = `space.fromAttacker(dx, dy)` / `space.fromTarget(dx, dy)`; `lerp(AX, DX, f)` =
 * `space.between(f, dx, dy)`; `tinted(name, '#rrggbb')` = the `tint` ARGB int of [particle] / [FxCanvas.tex]; `artPx(k)` = [artPx];
 * `scen.style.transform` = [jolt]; the CSS `filter` strings = [FxColor]. Nothing in a look may use `Math.random`, wall-clock time or Android views.
 *
 * **Target reaction.** With `react = false` the target never gets a pose or filter ([target] ignores [Actor.set], [hit] does
 * nothing); everything else plays.
 */
class FxScript(
    val space: FxSpace,
    val plan: MoveFxPlan,
    private val stage: MoveFxStage,
    /** False = the target shows no reaction (no damage). */
    val react: Boolean,
    seed: Long,
) {
    /** The seeded random source of this animation. */
    val random: Random = Random(seed)

    /** Size factor of the plan (sqrt of power / base power, 0.8..1.6). */
    val k: Float get() = plan.k
    /** Pixels per stage unit. */
    val u: Float get() = space.unit
    /** +1 when the target is right of the attacker, -1 when left. */
    val dir: Float get() = space.dir

    /** The last mockup ms [advance] was called with. */
    var clock = 0f
        private set

    /** The mockup ms the running item is "at": its own scheduled time for [at] / [ticks] steps, the frame time for a [tween]. New particles are stamped with it. */
    var now = 0f
        private set

    /** True once [releaseStage] handed the sprites and the background back: every later pose, filter and jolt is dropped. */
    var stageReleased = false
        private set

    /**
     * Puts both sprites and the background back at rest ONCE and lets go of them for good: the look no longer touches them, so the
     * next battle event may animate them (a faint, an entrance) while this one's particles still fade. Called by the player at the
     * look's done time; a second call does nothing.
     */
    fun releaseStage() {
        if (stageReleased) return
        stageReleased = true
        stage.restore()
    }

    // ---- randomness ----
    fun rnd(a: Float, b: Float): Float = a + random.nextFloat() * (b - a)
    /** True with probability [p]. */
    fun chance(p: Float): Boolean = random.nextFloat() < p

    // ---- timeline ----
    private class Item(
        val s: Float, val e: Float, val kind: Int, val ease: FxEase?, val step: Float, val at: FxAt?, val tween: FxTween?,
    ) {
        var done = false
        var next = s
    }

    private val items = ArrayList<Item>()

    /** Runs [fn] once at [ms]. */
    fun at(ms: Float, fn: FxAt) { items += Item(ms, ms, KIND_AT, null, 0f, fn, null) }

    /** Calls [fn] with the (eased) progress on every frame from [ms] for [dur] ms; its last call is at progress 1, even when frames were skipped. */
    fun tween(ms: Float, dur: Float, ease: FxEase? = null, fn: FxTween) { items += Item(ms, ms + dur, KIND_TWEEN, ease, 0f, null, fn) }

    /** Like [tween] but called on a fixed step of [stepMs] mockup ms (default one 60 fps frame), catching up missed steps: for random spawning. */
    fun ticks(ms: Float, dur: Float, stepMs: Float = FRAME_MS, fn: FxTween) { items += Item(ms, ms + dur, KIND_TICKS, null, stepMs, null, fn) }

    // ---- particles, procs, washes ----
    internal val particles = ArrayList<FxParticle>()
    internal val procs = ArrayList<FxProc>()

    /** Alpha of the dark wash (rgb 6, 6, 30) over the whole layer, 0 = none. */
    var dim = 0f
    internal var flashAlpha = 0f
    private var flashUntil = 0f

    /** A white wash over the whole layer of [alpha] for [ms] mockup ms (the JS `flashOnce`). */
    fun flashOnce(alpha: Float, ms: Float = 60f) { flashAlpha = alpha; flashUntil = now + ms }

    /**
     * Spawns a particle (the JS `P()`) at pixel ([x], [y]) at the time the running item is at. Velocity [vx], [vy] (units/s, x
     * positive toward the target), acceleration [ax], [ay] (units/s^2, y down: gravity is positive), sizes [s0] -> [s1] (units, s1 =
     * s0 when null), alpha [a0] -> [a1], [life] in mockup ms. Texture [img] (an [FxArt] key) tinted with [tint], rotated by
     * [rot] + [vrot] (rad, rad/s) or along its velocity ([follow]), [stretch]ed, [add]itive. [pix] = a square pixel bit cycling
     * through those colours (sizes in whole device pixels, never rotated: use [artPx]). [draw] = draw it yourself. Returns null
     * when the particle cap is reached.
     */
    fun particle(
        img: String? = null, tint: Int = 0,
        x: Float, y: Float,
        vx: Float = 0f, vy: Float = 0f, ax: Float = 0f, ay: Float = 0f,
        life: Float = 500f,
        s0: Float = 30f, s1: Float? = null,
        a0: Float = 1f, a1: Float = 0f,
        rot: Float = 0f, vrot: Float = 0f,
        follow: Boolean = false, stretch: Float = 1f, add: Boolean = false,
        pix: IntArray? = null,
        draw: FxParticleDraw? = null,
    ): FxParticle? {
        if (particles.size >= MAX_PARTICLES) return null
        val p = FxParticle(
            img, tint, now, life, x, y,
            space.x(vx), space.y(vy), space.x(ax), space.y(ay),
            space.len(s0), space.len(s1 ?: s0), a0, a1, rot, vrot, follow, stretch, add, pix, draw,
        )
        particles += p
        return p
    }

    /**
     * [n] particles of texture [img] flying out from [at] in random directions at up to [speed] (units/s, at least 40 % of it),
     * the JS `burst()`: life x 0.7..1.2, start size x 0.7..1.2, random spin, [gravity] pulling down (units/s^2).
     */
    fun burst(img: String, tint: Int, at: FxPt, n: Int, speed: Float, life: Float, s0: Float, s1: Float, add: Boolean = false, gravity: Float = 0f) {
        for (i in 0 until n) {
            val a = rnd(0f, 6.283f)
            val v = rnd(speed * .4f, speed)
            particle(
                img = img, tint = tint, x = at.x, y = at.y,
                vx = kotlin.math.cos(a) * v, vy = sin(a) * v, ay = gravity,
                life = life * rnd(.7f, 1.2f), s0 = s0 * rnd(.7f, 1.2f), s1 = s1,
                rot = rnd(0f, 6.28f), vrot = rnd(-3f, 3f), add = add,
            )
        }
    }

    /**
     * Adds a drawing function active from [startMs] to [endMs]; layer 0 draws under the particles, layer 1 over them. [draw] gets
     * the raw drawing helper [FxCanvas] (pixels; use [u] for stage units).
     */
    fun proc(startMs: Float, endMs: Float, layer: Int = 0, draw: FxDraw) { procs += FxProc(startMs, endMs, layer, draw) }

    /** One art pixel of the pixel-art bitmaps in stage units, so that `artPx(k) x u` is a whole number of device pixels (the JS `artPx`). */
    fun artPx(sizeFactor: Float = 1f): Float = space.artPixels(sizeFactor) / space.unit

    // ---- sprites ----
    /** One of the two sprites. [set] puts it into a pose (the JS `mon.set`), absolute: omit a value for its rest value. */
    inner class Actor(private val isAttacker: Boolean, private val enabled: Boolean) {
        /**
         * Offset [x] (stage units, toward the target = positive) and [y] (down = positive), scale [sx] / [sy] about the bottom
         * centre, colour [filter] (a [FxColor] matrix, null = none). Nothing happens for the target when `react` is false, nor after [releaseStage].
         */
        fun set(x: Float = 0f, y: Float = 0f, sx: Float = 1f, sy: Float = sx, filter: FloatArray? = null) {
            lastX = x; lastY = y; lastSx = sx; lastSy = sy; lastFilter = filter
            if (enabled && !stageReleased) stage.pose(isAttacker, space.x(x + if (isAttacker) leanX else 0f), space.y(y), sx, sy, filter)
        }

        fun reset() = set()

        // R18-C (PLAN 228): the pose the look asked for last, so the attacker's lean ([FxScript.lean]) can be added to it.
        private var lastX = 0f
        private var lastY = 0f
        private var lastSx = 1f
        private var lastSy = 1f
        private var lastFilter: FloatArray? = null

        /** Poses again with the look's last pose, so a change of the lean shows even when the look itself poses nothing right now. */
        internal fun repose() = set(lastX, lastY, lastSx, lastSy, lastFilter)
    }

    /** R18-C: the attacker's lean toward the target right now, in stage units; [Actor.set] adds it to the look's own offset. */
    private var leanX = 0f

    /**
     * R18-C (PLAN 228): the user of a special or status move leans a little toward the target and back ([FxLean]), as if casting at
     * it, from [t0]. Added on top of whatever the look poses; ends at rest ([FxLean.UNITS] -> 0 at the end of its last tween).
     */
    fun lean(t0: Float) {
        tween(t0, FxLean.OUT_MS + FxLean.BACK_MS) { _, ms -> leanX = FxLean.at(ms - t0); attacker.repose() }
    }

    val attacker = Actor(true, true)
    val target = Actor(false, react)

    /**
     * The attacker's lunge (the Tackle run-up): winds back 160 ms, shoots at the target 150 ms (stops 20 units short of its centre
     * at 86 % of the way), and returns 300 ms. Uses the real attacker -> target vector, so it also works from the wild one down to us.
     */
    fun lunge(t0: Float) {
        val hx = (space.towardX - 20f) * .86f
        val hy = space.towardY * .86f
        tween(t0, 160f, FxEase.OUT2) { p, _ -> attacker.set(-22f * p, 6f * p) }
        tween(t0 + 160f, 150f, FxEase.IN2) { p, _ -> attacker.set(FxMath.lerp(-22f, hx, p), FxMath.lerp(6f, hy, p)) }
        tween(t0 + 310f, 300f) { p, _ -> val e = FxEase.INOUT.at(p); attacker.set(hx * (1f - e), hy * (1f - e)) }
    }

    /**
     * The target's reaction to a hit (the JS `hit`): shakes sideways (9 units, fading), is pushed [push] units away from the
     * attacker and back, jumps [jump] units up and down, wears the colour [filter] meanwhile. Nothing at all with `react` false.
     */
    fun hit(t0: Float, dur: Float, push: Float = 0f, jump: Float = 0f, filter: FxFilterAt? = null) {
        if (!react) return
        tween(t0, dur) { p, _ ->
            val q = 1f - p
            target.set(
                x = sin(p * 40f) * 9f * q + push * sin(p * PI.toFloat()),
                y = if (jump != 0f) -sin(p * PI.toFloat()) * jump else 0f,
                filter = if (filter != null && p < 1f) filter.at(p) else null,
            )
        }
    }

    /** Jolts the background by [dy] stage units (Earthquake), 0 = at rest. */
    fun jolt(dy: Float) { if (!stageReleased) stage.jolt(space.y(dy)) }

    // ---- paths and shapes ----
    /** A jagged line from (x0, y0) to (x1, y1) in [n] segments, each point off the line by up to [j] horizontally and 0.3 [j] vertically (pixels; the JS `jag`). Returns x, y pairs. */
    fun jag(x0: Float, y0: Float, x1: Float, y1: Float, n: Int, j: Float): FloatArray {
        val out = FloatArray((n + 1) * 2)
        out[0] = x0; out[1] = y0
        for (i in 1 until n) {
            val p = i.toFloat() / n
            out[i * 2] = FxMath.lerp(x0, x1, p) + rnd(-j, j)
            out[i * 2 + 1] = FxMath.lerp(y0, y1, p) + rnd(-j * .3f, j * .3f)
        }
        out[n * 2] = x1; out[n * 2 + 1] = y1
        return out
    }

    // ---- Showdown-style effects (battle-animations.ts showEffect, re-expressed in the JS psFx) ----
    /** The scale of a Showdown-style sprite at depth [z] (0 = attacker ... 200 = target): (2 - z / 200) x 0.6, so things shrink toward the target. */
    fun psScale(z: Float): Float = (2f - z / 200f) * PS_SC

    /**
     * A texture that moves between two states over time (the JS `psFx`): from [a] to [b] with [ease] between their times, then
     * [after]. Showdown units: x right (toward the target), y up, z 0 = attacker ... 200 = target. [rot] rotates it (radians,
     * screen axes: e.g. [FxSpace.pathAngle] for a bitmap that points right); [add] draws it additive.
     */
    fun psFx(spr: FxSprite, a: PsKey, b: PsEnd, ease: PsEase = PsEase.LINEAR, after: PsAfter = PsAfter.NONE, add: Boolean = false, rot: Float = 0f, layer: Int = 0) {
        val bt = b.time ?: (a.time + 500f)
        val bx = b.x ?: a.x; val by = b.y ?: a.y; val bz = b.z ?: a.z
        val bs = b.scale ?: a.scale; val bo = b.opacity ?: a.opacity
        val tail = after.tail
        val e = ease.ease
        val span = bt + tail - a.time
        proc(a.time, bt + tail, layer) { c, p, _ ->
            val ms = a.time + p * span
            val x: Float; val y: Float; val z: Float; val scale: Float; val opacity: Float
            if (ms <= bt) {
                val q = (ms - a.time) / max(1f, bt - a.time)
                val qe = e.at(q)
                x = FxMath.lerp(a.x, bx, qe); y = FxMath.lerp(a.y, by, qe); z = FxMath.lerp(a.z, bz, qe)
                scale = FxMath.lerp(a.scale, bs, qe); opacity = FxMath.lerp(a.opacity, bo, q)
            } else {
                val r = (ms - bt) / tail
                x = bx; y = by; z = bz
                scale = if (after == PsAfter.EXPLODE) bs * (1f + 2f * r) else bs
                opacity = bo * (1f - r)
            }
            val s = psScale(z)
            val w = spr.w * s * scale
            val h = spr.h * s * scale
            if (w > .5f && opacity > 0f) {
                val f = z / 200f
                c.alpha = min(1f, opacity)
                c.additive = add
                c.translate(space.betweenX(f, x * s), space.betweenY(f, -y * s))
                if (rot != 0f) c.rotate(rot)
                c.tex(spr.key, spr.tint, 0f, 0f, w * u, h * u)
            }
        }
    }

    // ---- running ----
    /**
     * Runs the timeline up to [ms] mockup ms: due items in the order they were added (like the JS), new particles, expiry of
     * washes and dead particles / procs. Pure; the renderer draws the state afterwards. Calling it again with the same time is harmless.
     */
    fun advance(ms: Float) {
        clock = ms
        var i = 0
        while (i < items.size) {
            val item = items[i++]
            if (item.done || ms < item.s) continue
            when (item.kind) {
                KIND_AT -> { item.done = true; now = item.s; item.at!!.run() }
                KIND_TWEEN -> {
                    val raw = if (item.e == item.s) 1f else min(1f, (ms - item.s) / (item.e - item.s))
                    now = ms
                    val ease = item.ease
                    item.tween!!.run(if (ease != null) ease.at(raw) else raw, ms)
                    if (raw >= 1f) item.done = true
                }
                else -> while (!item.done && item.next <= ms) {
                    val t = item.next
                    val raw = if (item.e == item.s) 1f else min(1f, (t - item.s) / (item.e - item.s))
                    now = t
                    item.tween!!.run(raw, t)
                    item.next = t + item.step
                    if (item.next > item.e + EPS) item.done = true
                }
            }
        }
        now = ms
        if (ms >= flashUntil) flashAlpha = 0f
        var w = 0
        for (r in 0 until particles.size) { val p = particles[r]; if (p.alive(ms)) particles[w++] = p }
        while (particles.size > w) particles.removeAt(particles.size - 1)
        w = 0
        for (r in 0 until procs.size) { val p = procs[r]; if (ms < p.e) procs[w++] = p }
        while (procs.size > w) procs.removeAt(procs.size - 1)
    }

    companion object {
        /** Mockup milliseconds of one 60 fps frame: the step of [ticks]. */
        const val FRAME_MS = 1000f / 60f
        /** No more particles than this at once (the cap keeps a slow phone smooth). */
        const val MAX_PARTICLES = 240
        private const val PS_SC = 0.6f
        private const val EPS = 1e-3f
        private const val KIND_AT = 0
        private const val KIND_TWEEN = 1
        private const val KIND_TICKS = 2
    }
}
