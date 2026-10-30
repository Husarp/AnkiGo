package com.husarp.ankigo.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.content.FileProvider
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.DataStore
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.gfx.GifDecoder
import java.io.File
import kotlin.math.roundToInt

/** URI-backed, native-size composed GIF frames; never put the decoded animation into the RemoteViews parcel. */
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
        // Memory budget reflects the phone heap; ordinary 96x96 animations retain ALL frames.
        val pixels = (Runtime.getRuntime().maxMemory() / 16).coerceAtMost(32L*1024*1024) / 4
        val gif = GifDecoder.decode(file.readBytes(), pixels, maxStep=2)
        if (step == 2 && gif.frameStep == 2) return null // already every second frame; the next fallback is still
        if (gif.frames.size < 2) return null
        val folder = File(ctx.cacheDir, "widget-frames/${mon.species}-${mon.shiny}-${file.lastModified()}-${gif.frameStep}").apply { mkdirs() }
        val rv = RemoteViews(ctx.packageName, R.layout.widget_buddy_anim)
        rv.setImageViewBitmap(R.id.b_img_l1, WidgetRender.render(ctx, WidgetRender.Kind.BUDDY, snap, w, h, false, look=look, appearance=appearance, hideSprite=true))
        rv.setImageViewBitmap(R.id.b_img_d1, WidgetRender.render(ctx, WidgetRender.Kind.BUDDY, snap, w, h, true, look=look, appearance=appearance, hideSprite=true))
        val space=BuddyGeometry.space(w,h)
        val g=BuddyGeometry.of(space.width,space.height,look.layout)
        val density=ctx.resources.displayMetrics.density
        val left=space.offsetX+g.spriteLeft*space.scale
        val top=space.offsetY+g.spriteTop*space.scale
        val size=g.spriteSize*space.scale
        val padding=intArrayOf((left*density).roundToInt(),(top*density).roundToInt(),
            ((w-left-size)*density).roundToInt(),((h-top-size)*density).roundToInt())
        val launchers = ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        for (flip in listOf(R.id.b_flip_l, R.id.b_flip_d)) {
            rv.removeAllViews(flip)
            rv.setViewPadding(flip, padding[0], padding[1], padding[2], padding[3])
            rv.setInt(flip, "setFlipInterval", (gif.frames.sumOf { it.delayMs }.toDouble()/gif.frames.size*step).roundToInt().coerceAtLeast(20))
            for (i in gif.frames.indices step step) {
                val png = File(folder, "$i.png")
                if (!png.exists()) {
                    val bitmap = Bitmap.createBitmap(gif.frames[i].pixels, gif.width, gif.height, Bitmap.Config.ARGB_8888)
                    val tmp = File(folder, "$i.tmp")
                    tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                    tmp.renameTo(png)
                }
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName+".files", png)
                for (launcher in launchers) ctx.grantUriPermission(launcher.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val frame = RemoteViews(ctx.packageName, R.layout.widget_buddy_frame)
                frame.setImageViewUri(R.id.b_frame, uri)
                rv.addView(flip, frame)
            }
        }
        return rv
    }
}
