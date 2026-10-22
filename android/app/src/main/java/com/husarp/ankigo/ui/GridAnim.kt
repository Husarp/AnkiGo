package com.husarp.ankigo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.Choreographer
import android.view.View
import com.husarp.ankigo.data.SpriteImage

// PLAN 156: animated Pokémon in the Pokédex and Box grids. This file is the moving part: the frame maths, the adaptive tick rule,
// the drawable and the one shared clock. What binds a cell, the memory budget, the setting and the scroll gate are in GridSprites.

/** Pure maths of a looping animation (unit-tested). */
internal object GridFrames {
    /** The time (ms from the start of the loop) at which each frame ends; the frame times are [MonPreview.durations] (20 ms floor, 100 ms when missing). */
    fun ends(delays: IntArray, frames: Int): LongArray {
        val d = MonPreview.durations(delays, frames)
        var t = 0L
        return LongArray(frames) { t += d[it]; t }
    }

    /** The frame to show [elapsedMs] after the start of a loop whose frames end at [ends]: real elapsed time, so a skipped tick never slows the animation. */
    fun frameAt(ends: LongArray, elapsedMs: Long): Int {
        if (ends.isEmpty()) return 0
        val total = ends.last()
        if (total <= 0) return 0
        val t = elapsedMs.coerceAtLeast(0) % total
        var i = 0
        while (i < ends.size - 1 && ends[i] <= t) i++
        return i
    }
}

/**
 * The adaptive tick rule of the shared clock (unit-tested, no Android). The clock acts every [step] vsyncs (1, 2, 3 or 4).
 * [onTick] is told how long each acted tick really took; when the average of the last [WINDOW] ticks is over [SLOW] times the budget
 * (step x vsync) frames are being dropped, so the step goes up one (the animations update less often rather than stop). After
 * [calmNeededMs] without trouble it goes down one again. A step that is raised again soon after it was lowered makes the next wait
 * twice as long (up to [CALM_MAX_MS]), so a phone that can do 30 but not 60 frames a second does not flip between the two every second.
 */
internal class TickRate {
    var step = 1
        private set
    private val ring = LongArray(WINDOW)
    private var filled = 0
    private var next = 0
    private var sum = 0L
    private var calmNs = 0L
    private var calmNeededNs = CALM_MS * NS_PER_MS
    private var sinceLowerNs = RECENT_MS * NS_PER_MS // starts "not recent"

    /** One acted tick took [deltaNs] since the previous one; [vsyncNs] is the screen's frame time. Returns the step to use from now on. */
    fun onTick(deltaNs: Long, vsyncNs: Long): Int {
        calmNs += deltaNs
        sinceLowerNs = (sinceLowerNs + deltaNs).coerceAtMost(RECENT_MS * NS_PER_MS)
        if (filled == WINDOW) sum -= ring[next] else filled++
        ring[next] = deltaNs
        sum += deltaNs
        next = (next + 1) % WINDOW
        if (filled < WINDOW) return step
        if (sum / WINDOW > step * vsyncNs * SLOW) {
            calmNs = 0
            if (step < MAX_STEP) {
                calmNeededNs = if (sinceLowerNs < RECENT_MS * NS_PER_MS) minOf(calmNeededNs * 2, CALM_MAX_MS * NS_PER_MS) else CALM_MS * NS_PER_MS
                step++
                clearWindow()
            }
        } else if (step > 1 && calmNs >= calmNeededNs) {
            step--
            sinceLowerNs = 0
            calmNs = 0
            clearWindow()
        }
        return step
    }

    private fun clearWindow() { filled = 0; next = 0; sum = 0 }

    companion object {
        const val WINDOW = 30
        private const val NS_PER_MS = 1_000_000L
        const val MAX_STEP = 4
        /** The average tick may be this many times its budget before it counts as dropping frames. */
        const val SLOW = 1.5
        const val CALM_MS = 1000L
        const val CALM_MAX_MS = 16_000L
        /** A raise this soon after a lower counts as flipping back and forth. */
        const val RECENT_MS = 8000L
    }
}

/**
 * A decoded grid animation: its [img] frames may be smaller than the GIF ([width] x [height] is the GIF's own size, see
 * [GridSprites.plan]).
 */
internal class GridPic(val img: SpriteImage, val width: Int, val height: Int)

/**
 * The picture of one animated Pokémon in a grid cell: the frame for the real time since it started, drawn without any smoothing
 * (sharp pixels at any scale). It reports the GIF's own size ([GridPic.width] x height) even when its frames were shrunk to the tile
 * (they are drawn up to the bounds), so [TileSprite] picks the tile scale from the sprite's pixels as it always did. It moves only
 * while it is visible ([setVisible], which ImageView calls on attach, detach, scroll-away and when the window or page is hidden) and
 * only on the ticks of [GridClock]; while its list flings ([gate]) it is off the clock and stands still on its frame.
 */
internal class GridAnimDrawable(pic: GridPic, internal val gate: ScrollGate?) : Drawable() {
    private val frames: List<Bitmap> = pic.img.frames
    private val picW = pic.width
    private val picH = pic.height
    private val ends = GridFrames.ends(pic.img.delays, frames.size)
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private var startNs = System.nanoTime()

    /** The size the frames are kept at, which is under [getIntrinsicWidth] x height when the GIF was shrunk to its tile (for tests). */
    internal val keptWidth get() = frames[0].width
    internal val keptHeight get() = frames[0].height

    /** The frame on show (for tests). */
    var shown = 0
        private set

    override fun getIntrinsicWidth() = picW
    override fun getIntrinsicHeight() = picH
    override fun draw(canvas: Canvas) { canvas.drawBitmap(frames[shown], null, bounds, paint) }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible) {
            if (restart) { startNs = System.nanoTime(); shown = 0 }
            if (gate?.flinging != true) GridClock.add(this) // a list in flight starts it on the settle ([resumeOnClock])
        } else {
            GridClock.remove(this)
        }
        return changed
    }

    /** The list settled: back on the clock when still visible (a fling took it off; the frame then follows the real time again). */
    fun resumeOnClock() { if (isVisible && onScreen()) GridClock.add(this) }

    /** Shows the frame for [nowNs] (invalidates only when it changed); nothing while the list flings. */
    fun advance(nowNs: Long) {
        if (gate?.flinging == true) return
        val i = GridFrames.frameAt(ends, (nowNs - startNs) / 1_000_000)
        if (i != shown) { shown = i; invalidateSelf() }
    }

    /** True while the view that shows this is attached, shown and in a visible window (the clock drops it otherwise). */
    fun onScreen(): Boolean {
        val v = callback as? View ?: return false
        return v.isAttachedToWindow && v.isShown && v.windowVisibility == View.VISIBLE
    }
}

/**
 * The one clock of all grid animations. A single frame callback runs only while at least one [GridAnimDrawable] is visible (it
 * stops by itself when the last one is hidden, so nothing is left running behind a hidden page). It acts every [TickRate.step]
 * vsyncs: each visible drawable moves to the frame for the real time and invalidates its view only when the frame changed.
 */
internal object GridClock {
    /** Where frame callbacks come from; a test drives its own. */
    internal fun interface Frames { fun post(callback: (frameTimeNanos: Long) -> Unit) }

    private val choreographer = Frames { cb -> Choreographer.getInstance().postFrameCallback { cb(it) } }
    internal var frames: Frames = choreographer

    private const val DEFAULT_VSYNC_NS = 16_666_667L
    private val active = ArrayList<GridAnimDrawable>()
    private var scheduled = false
    private var rate = TickRate() // kept while the clock sleeps: a phone that could not keep up still cannot
    private var vsyncNs = DEFAULT_VSYNC_NS
    private var sinceActed = 0
    private var lastActedNs = 0L
    private val onFrame: (Long) -> Unit = { frame(it) }

    /** Visible animations being driven (for tests). */
    internal val activeCount get() = active.size

    /** True while a frame callback is pending (for tests). */
    internal val running get() = scheduled

    /** How many vsyncs between two ticks right now (for tests). */
    internal val step get() = rate.step

    fun add(d: GridAnimDrawable) {
        if (d !in active) active.add(d)
        if (!scheduled) {
            (d.callback as? View)?.display?.refreshRate?.takeIf { it >= 20f }?.let { vsyncNs = (1e9 / it).toLong() }
            sinceActed = 0
            lastActedNs = 0
            post()
        }
    }

    fun remove(d: GridAnimDrawable) { active.remove(d) }

    /**
     * A list began to fling: all of its animations leave the clock. The clock then posts no more frame callbacks for them (a callback
     * already pending finds nothing to do and is not renewed) unless another list still has visible ones; [GridAnimDrawable.resumeOnClock] puts them back.
     */
    fun suspend(gate: ScrollGate) { active.removeAll { it.gate === gate } }

    /** Drops everything and stops (tests). */
    internal fun reset() { active.clear(); scheduled = false; sinceActed = 0; lastActedNs = 0; rate = TickRate(); vsyncNs = DEFAULT_VSYNC_NS }

    private fun post() {
        scheduled = true
        // a renderer without a looper (screenshots) has no Choreographer: nothing moves there, the first frame still shows
        try { frames.post(onFrame) } catch (_: RuntimeException) { scheduled = false }
    }

    private fun frame(nowNs: Long) {
        scheduled = false
        if (active.isEmpty()) return
        if (++sinceActed >= rate.step) {
            sinceActed = 0
            if (lastActedNs != 0L) rate.onTick(nowNs - lastActedNs, vsyncNs)
            lastActedNs = nowNs
            var i = active.size - 1
            while (i >= 0) {
                if (i < active.size) {
                    val d = active[i]
                    if (d.onScreen()) d.advance(nowNs) else active.removeAt(i)
                }
                i--
            }
        }
        if (active.isNotEmpty()) post()
    }
}
