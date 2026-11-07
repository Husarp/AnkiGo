package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.DataStore
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.gfx.GifDecoder
import kotlin.math.roundToInt

/** Self-contained frames: the launcher can restore the widget after cache eviction or process death. */
object BuddyFrames {
    @Synchronized fun views(ctx: Context, snap: WidgetSnapshot, w: Float, h: Float, look: BuddyLook, appearance: WidgetLook, step: Int = 1): RemoteViews? {
        val mon = snap.buddyMon ?: return null
        val data = GameRepo.data ?: DataStore.load(ctx) ?: return null
        val species = data.species[mon.species] ?: return null
        val file = Sprites.cached(ctx, species, SpriteKind.FRONT, mon.shiny) ?: run {
            // Do not keep an AppWidget broadcast alive through a network download.
            Sprites.fetch(ctx, species, SpriteKind.FRONT, mon.shiny) { downloaded ->
                if (downloaded?.extension == "gif") com.husarp.ankigo.Widgets.refreshAll(ctx,study=false)
            }
            return null
        }
        if (file.extension != "gif") return null
        // Bound parcelled frames as well as heap use; oversized animations use the still fallback.
        val pixels = (Runtime.getRuntime().maxMemory() / 16).coerceAtMost(WidgetLogic.MAX_BITMAP_BYTES.toLong()) / 4
        val gif = GifDecoder.decode(file.readBytes(), pixels, maxStep=2)
        if (step == 2 && gif.frameStep == 2) return null // already every second frame; the next fallback is still
        if (gif.frames.size < 2) return null
        return frameViews(ctx, snap, w, h, look, appearance, gif, step)
    }

    internal fun frameViews(ctx: Context, snap: WidgetSnapshot, w: Float, h: Float, look: BuddyLook,
                            appearance: WidgetLook, gif: GifDecoder.Gif, step: Int = 1): RemoteViews {
        val frames = gif.frames.indices.step(step).map { i ->
            Bitmap.createBitmap(gif.frames[i].pixels, gif.width, gif.height, Bitmap.Config.ARGB_8888)
        }
        val rv = RemoteViews(ctx.packageName, R.layout.widget_buddy_anim)
        rv.setImageViewBitmap(R.id.b_img_l1, WidgetRender.render(ctx, WidgetRender.Kind.BUDDY, snap, w, h, false, look=look, appearance=appearance, hideSprite=true))
        rv.setImageViewBitmap(R.id.b_img_d1, WidgetRender.render(ctx, WidgetRender.Kind.BUDDY, snap, w, h, true, look=look, appearance=appearance, hideSprite=true))
        val space=BuddyGeometry.space(w,h)
        val g=BuddyGeometry.of(space.width,space.height,look.layout).scaled(look.size)
        val density=ctx.resources.displayMetrics.density
        val left=space.offsetX+g.spriteLeft*space.scale
        val top=space.offsetY+g.spriteTop*space.scale
        val size=g.spriteSize*space.scale
        val padding=intArrayOf((left*density).roundToInt(),(top*density).roundToInt(),
            ((w-left-size)*density).roundToInt(),((h-top-size)*density).roundToInt())
        for (flip in listOf(R.id.b_flip_l, R.id.b_flip_d)) {
            rv.removeAllViews(flip)
            rv.setViewPadding(flip, padding[0], padding[1], padding[2], padding[3])
            rv.setInt(flip, "setFlipInterval", (gif.frames.sumOf { it.delayMs }.toDouble()/gif.frames.size*step).roundToInt().coerceAtLeast(20))
            for (bitmap in frames) {
                val frame = RemoteViews(ctx.packageName, R.layout.widget_buddy_frame)
                frame.setImageViewBitmap(R.id.b_frame, bitmap)
                rv.addView(flip, frame)
            }
        }
        return rv
    }
}
