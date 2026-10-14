package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.study.StudyRules

/**
 * Single-Pokémon previews (team rows, detail sheets, pickers, Dex hero): the looping battle GIF when the FRONT sprite is
 * already cached as a GIF, otherwise exactly [Ui.staticSprite]. Grids (box, Pokédex list) keep the static sprite on purpose
 * (dozens of decoded animations would cost memory and CPU).
 */
internal object MonPreview {
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Pure choice: ANIMATED when the cached front sprite is a GIF, else STATIC when the PNG is on disk, else PLACEHOLDER. */
    fun source(cachedFrontName: String?, staticExists: Boolean): StudyRules.SpriteSource =
        StudyRules.spriteSource(cachedFrontName?.endsWith(".gif", ignoreCase = true) == true, staticExists)

    /** Frame durations for an AnimationDrawable: at least 20 ms (same floor as SpriteView). */
    fun durations(delays: IntArray, frames: Int): IntArray = IntArray(frames) { delays.getOrElse(it) { 100 }.coerceAtLeast(20) }

    /**
     * Shows species [num] in [view]. Never starts a download: a missing sprite stays the static PNG / placeholder (the
     * static path may still fetch its own PNG, as before). The GIF is decoded off the main thread; the newest request per
     * view wins. The animation runs while the view is attached and visible (ImageView toggles Drawable.setVisible).
     */
    fun show(ctx: Context, num: Int, shiny: Boolean, view: ImageView, silhouette: Boolean = false) {
        val sp = GameRepo.data?.species?.get(num)
        val front = if (sp == null || silhouette) null else Sprites.cached(ctx, sp, SpriteKind.FRONT, shiny)
        val static = Sprites.staticFile(ctx, num, shiny).isFile
        if (sp == null || source(front?.name, static) != StudyRules.SpriteSource.ANIMATED) {
            Ui.staticSprite(ctx, num, shiny, view, silhouette)
            return
        }
        // Animated sprite is on disk: static picture (or placeholder, no download) until the decode lands.
        Ui.staticSprite(ctx, num, shiny, view, download = false)
        val token = Any()
        view.setTag(R.id.ag_tag_sprite, token)
        PanelSprites.pool.execute {
            val img = Sprites.decode(front!!)
            main.post {
                if (img == null || !img.animated || view.getTag(R.id.ag_tag_sprite) !== token) return@post
                view.setImageDrawable(animation(ctx, img))
                (view.drawable as? AnimationDrawable)?.start()
            }
        }
    }

    private fun animation(ctx: Context, img: SpriteImage): AnimationDrawable {
        val times = durations(img.delays, img.frames.size)
        return AnimationDrawable().apply {
            img.frames.forEachIndexed { i, bmp -> addFrame(Ui.pixelBitmap(ctx, bmp), times[i]) }
            isOneShot = false
        }
    }
}
