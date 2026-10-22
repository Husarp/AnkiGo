package com.husarp.ankigo.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AbsListView
import android.widget.ImageView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.ByteLru
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.data.SpriteUrls
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.gfx.GifDecoder
import java.io.File

/**
 * PLAN 156: the sprite of a Pokédex / Box grid cell. [bind] shows today's static picture ([Ui.staticSprite]) and, for a caught Pokémon
 * whose animated front sprite is ALREADY on the phone, swaps in a [GridAnimDrawable] once it is decoded (off the main thread, newest
 * request per view wins). It never downloads an animation. The setting "Animate Pokémon in lists" (default on), "Remove battle
 * animations" and Android's animator switch keep the grids still.
 *
 * Only cells on screen move: the drawable runs while ImageView says it is visible (attached, shown, window visible), so a recycled,
 * scrolled-away or hidden cell stops by itself. While a list flings ([ScrollGate]) everything stands still and new cells bind the
 * static picture; when it settles the visible cells start. All of them share one clock ([GridClock]) that slows down, never stops,
 * when the phone cannot keep up.
 *
 * Memory: grid decodes have their own frame cap and their own LRU, both from one budget ([budgetBytes]); see [maxPixels] and [plan].
 * A picture bigger than the room its tile draws it in is first shrunk (nearest neighbour) to that room; the drawable keeps the
 * GIF's own size, so the tile scale does not change. Every cached animation moves, however big (a huge one on fewer frames).
 */
internal object GridSprites {
    /** The most memory the grids' decoded animations may use (on a phone with a big enough heap). */
    const val BUDGET_MAX_BYTES = 32L * 1024 * 1024

    /** Cells of a grid whose shape is not known (a phone with 4 columns: 9 rows). */
    const val DEFAULT_CELLS = 36

    /** A decoded animation keeps room for at least this many frames of the final size, so it always has two to play. */
    const val MIN_FRAMES = 3

    /** The decoder works on the full-size canvas before the frames are shrunk: most pixels it may hold for one animation (4 MB). */
    const val TRANSIENT_MAX_PIXELS = 1_000_000L

    /** The budget on a phone whose app heap is [maxHeapBytes]: 32 MB, or an eighth of the heap on a small one. */
    fun budgetBytes(maxHeapBytes: Long): Long = minOf(BUDGET_MAX_BYTES, maxHeapBytes / 8)

    /**
     * The pixels (frames x width x height) one animation may keep: [cells] animations of ARGB_8888 frames (4 bytes a pixel) at this
     * size fill [budgetBytes] exactly. A longer animation keeps every 2nd / 3rd frame and plays at the same speed.
     */
    fun maxPixels(budgetBytes: Long, cells: Int): Long = (budgetBytes / 4 / cells.coerceAtLeast(1)).coerceAtLeast(1)

    /** Bytes of the decoded frames of [frames] frames of [w] x [h] pixels. */
    fun frameBytes(frames: Int, w: Int, h: Int): Long = frames.toLong() * w * h * 4

    /** How a GIF of [w] x [h] is decoded for a grid: the size its frames are kept at, and the cap to hand [GifDecoder.decode]. */
    class Plan(val outW: Int, val outH: Int, val decoderCap: Long)

    /**
     * Plans the decode of a [w] x [h] GIF for a grid of [shape] with [share] pixels per animation ([maxPixels]).
     * 1. The frames are kept at the room the tile draws them in ([GridShape.roomW] x roomH) when the picture is bigger than that,
     *    and smaller still when even that leaves room for fewer than [MIN_FRAMES] frames in the share.
     * 2. The decoder counts full-size pixels, so its cap is the share (less one frame: it can keep one more than cap / frame) in
     *    full-size terms; a longer animation keeps every 2nd / 3rd frame. It is never under two full frames (so two frames always
     *    play) and never over [TRANSIENT_MAX_PIXELS], which bounds the memory held before the frames are shrunk.
     */
    fun plan(w: Int, h: Int, shape: GridShape, share: Long): Plan {
        val full = w.toLong() * h
        var f = minOf(1.0, shape.roomW.toDouble() / w, shape.roomH.toDouble() / h)
        val most = share / MIN_FRAMES
        if (full * f * f > most) f = Math.sqrt(most.toDouble() / full)
        val outW = Math.floor(w * f).toInt().coerceIn(1, w)
        val outH = Math.floor(h * f).toInt().coerceIn(1, h)
        val out = outW.toLong() * outH
        val ideal = (share - out).coerceAtLeast(2 * out) * full / out
        return Plan(outW, outH, minOf(ideal, TRANSIENT_MAX_PIXELS).coerceAtLeast(2 * full))
    }

    /** Width and height of the first screen of a GIF file ([bytes] starts with its header), or null when it is not a GIF header. */
    fun gifSize(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 10 || bytes[0] != 'G'.code.toByte() || bytes[1] != 'I'.code.toByte() || bytes[2] != 'F'.code.toByte()) return null
        val w = (bytes[6].toInt() and 0xFF) or ((bytes[7].toInt() and 0xFF) shl 8)
        val h = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
        return if (w > 0 && h > 0) w to h else null
    }

    /** [src] ([w] x [h] ARGB pixels) shrunk to [outW] x [outH], nearest neighbour (the pixel at the centre of each target pixel): hard edges, no blending. */
    fun shrink(src: IntArray, w: Int, h: Int, outW: Int, outH: Int): IntArray {
        val out = IntArray(outW * outH)
        for (y in 0 until outH) {
            val row = ((y * 2L + 1) * h / (outH * 2L)).toInt() * w
            for (x in 0 until outW) out[y * outW + x] = src[row + ((x * 2L + 1) * w / (outW * 2L)).toInt()]
        }
        return out
    }

    /**
     * Pure rule: does a cell animate? Needs the setting on, the cell asking for it ([wanted]: a caught Pokémon, not a silhouette),
     * no reduced motion (study setting or Android's animator switch) and an animated front sprite already cached ([cachedName] is a GIF).
     */
    fun animates(setting: Boolean, wanted: Boolean, reduceMotion: Boolean, animatorsOn: Boolean, cachedName: String?): Boolean =
        setting && wanted && !reduceMotion && animatorsOn && cachedName?.endsWith(".gif", ignoreCase = true) == true

    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Runs [block] on the main thread; a var so tests can run it in place. */
    internal var uiPost: (Runnable) -> Unit = { main.post(it) }

    private val heapBudget by lazy { budgetBytes(Runtime.getRuntime().maxMemory()) }
    private val lru by lazy { ByteLru<GridPic>(heapBudget) { pic -> pic.img.frames.sumOf { it.byteCount.toLong() } } }

    /** One request to animate the picture in a view; it sits in the view's sprite tag until the view is bound again. */
    private class Req(val ctx: Context, val num: Int, val shiny: Boolean, val file: File, val gate: ScrollGate?) {
        /** False once the view was bound for something else: a decode that has not started yet is skipped. */
        @Volatile var live = true
        /** True once the decode was asked for (or the animation is on); a request bound during a fling waits with false. */
        var started = false
    }

    /**
     * Shows species [num] in [view]: the static picture now, and, when [animate] (the cell is a caught Pokémon) and its animated front
     * sprite is cached, the animation as soon as it is decoded. [silhouette] cells (never met) and seen-only cells stay still. While
     * [gate]'s list flings the animation waits ([ScrollGate] starts it when the list settles).
     */
    fun bind(ctx: Context, view: ImageView, num: Int, shiny: Boolean, animate: Boolean, gate: ScrollGate?, silhouette: Boolean = false) {
        (view.getTag(R.id.ag_tag_sprite) as? Req)?.live = false // the previous request for this view is stale now
        val gif = if (animate && !silhouette) cachedGif(ctx, num, shiny) else null
        if (gif == null) {
            Ui.staticSprite(ctx, num, shiny, view, silhouette)
            return
        }
        Ui.staticSprite(ctx, num, shiny, view, download = false)
        val req = Req(ctx, num, shiny, gif, gate)
        view.setTag(R.id.ag_tag_sprite, req)
        if (gate?.flinging != true) start(view, req)
    }

    /**
     * The list settled: a view that already moves goes back on the clock (it left it when the fling began), and a view that was bound
     * while the list flew starts its decode.
     */
    fun resume(view: ImageView) {
        (view.drawable as? GridAnimDrawable)?.let { it.resumeOnClock(); return }
        val req = view.getTag(R.id.ag_tag_sprite) as? Req ?: return
        if (req.live && !req.started) start(view, req)
    }

    private fun cachedGif(ctx: Context, num: Int, shiny: Boolean): File? {
        val setting = AppPrefs.animateLists(ctx)
        val reduce = ctx.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)
        val animatorsOn = ValueAnimator.areAnimatorsEnabled()
        if (!setting || reduce || !animatorsOn) return null // nothing to look up on disk
        val sp = GameRepo.data?.species?.get(num) ?: return null
        val file = Sprites.cached(ctx, sp, SpriteKind.FRONT, shiny) ?: return null
        return file.takeIf { animates(setting, true, reduce, animatorsOn, it.name) }
    }

    private fun start(view: ImageView, req: Req) {
        req.started = true
        val shape = req.gate?.shape ?: GridShape.DEFAULT
        val key = "${req.file.path}|${req.file.lastModified()}|${shape.cells}|${shape.roomW}x${shape.roomH}"
        lru.get(key)?.let { show(view, req, it); return }
        PanelSprites.pool.execute {
            if (!req.live) return@execute
            val pic = decode(req.file, shape)
            if (pic != null) lru.put(key, pic) // kept for the next time even if this view moved on
            uiPost(Runnable {
                if (!req.live || view.getTag(R.id.ag_tag_sprite) !== req) return@Runnable // the view moved on
                if (pic != null) show(view, req, pic)
                // no animation after all (unreadable, one frame only, out of memory): the cell is a plain one again, so its still picture is fetched if it is missing
                else if (!Sprites.staticFile(req.ctx, req.num, req.shiny).isFile) Ui.staticSprite(req.ctx, req.num, req.shiny, view)
            })
        }
    }

    /**
     * Decodes [file] for a grid of [shape] as [plan] says (off the main thread): the kept frames are shrunk to the planned size and
     * made into bitmaps one at a time. Null for a PNG, an unreadable file, a one-frame GIF, or when memory runs out.
     */
    private fun decode(file: File, shape: GridShape): GridPic? {
        try {
            val bytes = file.readBytes()
            if (!SpriteUrls.isGif(bytes)) return null
            val (w, h) = gifSize(bytes) ?: return null
            val plan = plan(w, h, shape, maxPixels(heapBudget, shape.cells))
            val gif = GifDecoder.decode(bytes, plan.decoderCap)
            if (gif.frames.size < 2) return null
            val bitmaps = gif.frames.map {
                val px = if (plan.outW == gif.width && plan.outH == gif.height) it.pixels else shrink(it.pixels, gif.width, gif.height, plan.outW, plan.outH)
                Bitmap.createBitmap(px, plan.outW, plan.outH, Bitmap.Config.ARGB_8888)
            }
            return GridPic(SpriteImage(bitmaps, IntArray(gif.frames.size) { gif.frames[it].delayMs }), gif.width, gif.height)
        } catch (_: Exception) {
            return null
        } catch (_: OutOfMemoryError) {
            return null
        }
    }

    private fun show(view: ImageView, req: Req, pic: GridPic) {
        if (req.gate?.flinging == true) { req.started = false; return } // a fling began meanwhile: the picture waits for the settle
        view.setImageDrawable(GridAnimDrawable(pic, req.gate))
    }
}

/**
 * The shape of one grid: how many cells it shows at once ([cells], worked out once per grid) and the room in pixels one tile draws a
 * sprite in ([roomW] x [roomH]). [GridSprites.plan] shrinks bigger pictures to the room and splits the memory budget by [cells].
 */
internal class GridShape(val cells: Int, val roomW: Int, val roomH: Int) {
    companion object {
        /** A grid whose size is not known (a view bound without a list around it): a 4-column phone, no room limit. */
        val DEFAULT = GridShape(GridSprites.DEFAULT_CELLS, Int.MAX_VALUE, Int.MAX_VALUE)

        /**
         * Pure: a list [widthPx] x [heightPx] of rows of [columns] cells (height = width x [ratio]) with [gapPx] between cells and
         * [sidePx] at the sides, each cell keeping [topPx] / [stripPx] free above and below its sprite and [insetPx] at both sides
         * of it (the layout of CellRow). The cells it shows at once are the columns times the rows that fit the height, plus one
         * (a row half scrolled in); the room is what a tile's sprite view gets.
         */
        fun layout(widthPx: Int, heightPx: Int, columns: Int, ratio: Float, gapPx: Int, sidePx: Int, topPx: Int, stripPx: Int, insetPx: Int): GridShape {
            if (widthPx <= 0 || heightPx <= 0 || columns <= 0) return DEFAULT
            val cellW = (widthPx - 2 * sidePx + gapPx) / columns - gapPx
            if (cellW <= 0) return DEFAULT
            val cellH = (cellW * ratio).toInt()
            val rows = heightPx / (cellH + gapPx).coerceAtLeast(1) + 1
            return GridShape(columns * rows, (cellW - 2 * insetPx).coerceAtLeast(1), (cellH - topPx - stripPx).coerceAtLeast(1))
        }

        /** The shape of a Pokédex / Box list in [ctx]'s window (the sizes are the CellRow and cell layout values in dp). */
        fun of(ctx: Context, columns: Int, ratio: Float, gapDp: Int, sidePadDp: Int, topDp: Int, stripDp: Int, insetDp: Int = 2): GridShape {
            val cfg = ctx.resources.configuration
            val d = ctx.resources.displayMetrics.density
            return layout(
                (cfg.screenWidthDp * d).toInt(), (cfg.screenHeightDp * d).toInt(), columns, ratio,
                Ui.dp(ctx, gapDp), Ui.dp(ctx, sidePadDp), Ui.dp(ctx, topDp), Ui.dp(ctx, stripDp), Ui.dp(ctx, insetDp),
            )
        }
    }
}

/**
 * The scroll gate of one list: [flinging] is true from the moment the list is thrown until it settles. While it is true the
 * list's animations leave the shared clock (it posts no frame callbacks for them, and none at all when nothing else is left), and
 * new cells bind the static picture; when it ends the visible sprites go back on the clock / start their decode ([visibleSprites]
 * are the picture views of the cells on screen). [shape] is the grid's size for the memory budget. Plug it in with [attach].
 */
internal class ScrollGate(
    val shape: GridShape = GridShape.DEFAULT,
    private val visibleSprites: () -> List<ImageView>,
) : AbsListView.OnScrollListener, View.OnAttachStateChangeListener {
    var flinging = false
        private set

    fun attach(list: AbsListView) {
        list.setOnScrollListener(this)
        list.addOnAttachStateChangeListener(this)
    }

    /** The list is thrown ([on]) or has settled / is being dragged (!on). */
    fun setFlinging(on: Boolean) {
        if (on == flinging) return
        flinging = on
        if (on) GridClock.suspend(this) else visibleSprites().forEach { GridSprites.resume(it) }
    }

    override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) =
        setFlinging(scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING)

    override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) = Unit

    override fun onViewAttachedToWindow(v: View) = Unit

    /** A list that leaves the window mid-fling never reports that it settled. */
    override fun onViewDetachedFromWindow(v: View) { flinging = false }
}
