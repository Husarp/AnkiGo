package com.husarp.ankigo.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.R
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.gfx.SpriteView
import java.io.File
import java.util.concurrent.Executors

/**
 * Loads battle sprites into a [SpriteView] for the panel: file from the cache (or downloaded), decoded on a background
 * thread, shown on the main thread. Until the image is there the view shows a type-coloured placeholder.
 * Only the newest request per view counts (a tag on the view), so a slow download never overwrites a newer sprite.
 */
internal object PanelSprites {
    /** Decodes off the main thread; a var only so the design screenshots can decode in place. */
    internal var pool: java.util.concurrent.Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "panel-sprites").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    /**
     * Shows [sp] in [view]. [kind] FRONT for the wild one, BACK for our main; with [mirrorFrontIfNoBack] a species without
     * a back sprite shows its front sprite mirrored instead. [flipFront]: [kind] FRONT is shown mirrored (our Pokémon from the front).
     */
    fun show(ctx: Context, view: SpriteView, sp: Species, kind: SpriteKind, shiny: Boolean, mirrorFrontIfNoBack: Boolean, flipFront: Boolean = false) {
        val token = Any()
        view.setTag(R.id.ag_tag_sprite, token)
        fun current() = view.getTag(R.id.ag_tag_sprite) === token

        fun load(file: File, mirrored: Boolean) {
            pool.execute {
                val img = Sprites.decode(file)
                main.post {
                    if (!current()) return@post
                    if (img != null) {
                        view.mirror = mirrored
                        view.setImage(img)
                    } else {
                        // A damaged file must not leave the previous Pokémon's picture under the new one's name.
                        view.setImage(null)
                        view.setPlaceholder(sp.name.take(2), Ui.typeColor(sp.types.firstOrNull() ?: com.husarp.ankigo.game.Type.NORMAL))
                    }
                }
            }
        }

        fun fallbackFront() {
            val cached = Sprites.cached(ctx, sp, SpriteKind.FRONT, shiny)
            if (cached != null) { load(cached, true); return }
            Sprites.fetch(ctx, sp, SpriteKind.FRONT, shiny) { f -> if (current() && f != null) load(f, true) }
        }

        val static = Sprites.staticFile(ctx, sp.num, shiny)
        val source = com.husarp.ankigo.study.StudyRules.spriteSource(Sprites.cached(ctx, sp, kind, shiny) != null, static.isFile)
        if (source == com.husarp.ankigo.study.StudyRules.SpriteSource.STATIC) load(static, kind == SpriteKind.BACK || flipFront)
        val cached = Sprites.cached(ctx, sp, kind, shiny)
        if (cached != null) {
            // Cached: keep the old picture for the few ms the decode takes instead of flashing the placeholder.
            load(cached, flipFront)
            return
        }
        view.mirror = false
        if (source != com.husarp.ankigo.study.StudyRules.SpriteSource.STATIC) view.setImage(null)
        view.setPlaceholder(sp.name.take(2), Ui.typeColor(sp.types.firstOrNull() ?: com.husarp.ankigo.game.Type.NORMAL))
        Sprites.fetch(ctx, sp, kind, shiny) { f ->
            if (!current()) return@fetch
            if (f != null) load(f, flipFront)
            else if (kind == SpriteKind.BACK && mirrorFrontIfNoBack) fallbackFront()
        }
    }

    /** Our main's picture: the back sprite, or with [AppPrefs.playerFront] the front sprite mirrored to face the wild one. */
    fun showMain(ctx: Context, view: SpriteView, sp: Species, shiny: Boolean) {
        if (AppPrefs.playerFront(ctx)) show(ctx, view, sp, SpriteKind.FRONT, shiny, false, flipFront = true)
        else show(ctx, view, sp, SpriteKind.BACK, shiny, true)
    }

    /** Downloads (and decodes into the memory cache) a sprite ahead of time. [back] = the main's view (BACK sprite, FRONT as the fallback; just FRONT with [AppPrefs.playerFront]). */
    fun prefetch(ctx: Context, sp: Species, shiny: Boolean, back: Boolean) {
        fun warm(f: File?) { if (f != null) pool.execute { Sprites.decode(f) } }
        if (!back || AppPrefs.playerFront(ctx)) {
            Sprites.fetch(ctx, sp, SpriteKind.FRONT, shiny) { warm(it) }
            return
        }
        Sprites.fetch(ctx, sp, SpriteKind.BACK, shiny) { f ->
            if (f != null) warm(f) else Sprites.fetch(ctx, sp, SpriteKind.FRONT, shiny) { warm(it) }
        }
    }
}
