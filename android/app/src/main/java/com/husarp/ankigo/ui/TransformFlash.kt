package com.husarp.ankigo.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.husarp.ankigo.game.lookSpecies
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * R19-L (PLAN 271, pick A "a bright flash"): a Pokémon that transforms goes white for a moment, a few sparkles twinkle round it, and it
 * turns into the copy; nothing stays on it afterwards. Pure maths (no android.*): [white] is how white the picture is at a time,
 * [SWAP_MS] when the copy's picture takes over (the picture is all white then), [forEach] the sparkles. [TransformFlashFx] draws them.
 */
object TransformFlash {
    /** The whole flash. */
    const val DURATION_MS = 400L
    /** The picture is swapped here, at the whitest moment. */
    const val SWAP_MS = 200L

    private const val RISE_MS = 160f
    private const val HOLD_END_MS = 240f
    private const val SPARKLES = 6

    private fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v

    /** How white the picture is [tMs] after the start: 0 -> 1 in 160 ms, held to 240 ms, then 1 -> 0 by 400 ms. */
    fun white(tMs: Float): Float = when {
        tMs <= 0f -> 0f
        tMs < RISE_MS -> (tMs / RISE_MS).let { it * it }
        tMs < HOLD_END_MS -> 1f
        tMs < DURATION_MS -> 1f - clamp01((tMs - HOLD_END_MS) / (DURATION_MS - HOLD_END_MS))
        else -> 0f
    }

    /** The flash is over (nothing to draw any more). */
    fun finished(tMs: Float): Boolean = tMs >= DURATION_MS

    /** Mock units to pixels: the sprite box's short side counts as 72 (the same unit as [ShinyBurst.scale]). */
    fun scale(boxW: Float, boxH: Float): Float = ShinyBurst.scale(boxW, boxH)

    /**
     * Hands [emit] every sparkle alive at [tMs]: a small ring of stars round the picture's middle ([cx], [cy]) that grow and fade while the
     * picture is white (none on the picture itself: it is white already). Sizes are star radii in the same pixels as the centre.
     */
    fun forEach(tMs: Float, cx: Float, cy: Float, sc: Float, emit: ShinyBurst.Sink) {
        if (tMs <= 0f || tMs >= DURATION_MS) return
        val q = tMs / DURATION_MS
        val bell = sin(q * PI.toFloat())   // 0 -> 1 -> 0 over the flash
        for (i in 0 until SPARKLES) {
            val a = i.toFloat() / SPARKLES * (2f * PI.toFloat()) + 0.5f
            val reach = (14f + 14f * q) * sc * (if (i % 2 == 0) 1f else 0.75f)
            val twinkle = 0.7f + 0.3f * sin(tMs / 30f + i * 2.1f)
            emit.star(cx + cos(a) * reach, cy + sin(a) * reach * 0.85f, 7f * sc * bell * twinkle, bell, ShinyBurst.COLORS[i % 3], false)
        }
    }
}

/** The sparkles of [TransformFlash] on the arena's effect layer: centre ([cx], [cy]), unit scale [sc], played over [ms] (the player's pace). */
class TransformFlashFx(private val cx: Float, private val cy: Float, private val sc: Float, private val ms: Long = TransformFlash.DURATION_MS) : FxEffect {
    private val star = Path().apply {
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4f
            val r = if (i % 2 == 1) 0.28f else 1f
            if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r)
        }
        close()
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private lateinit var canvas: Canvas
    private val sink = ShinyBurst.Sink { x, y, r, alpha, color, _ -> drawStar(x, y, r, alpha, color) }

    override fun draw(canvas: Canvas, t: Float): Boolean {
        val own = t * TransformFlash.DURATION_MS / ms.coerceAtLeast(1L)   // the flash's own clock
        if (TransformFlash.finished(own)) return false
        this.canvas = canvas
        TransformFlash.forEach(own, cx, cy, sc, sink)
        return true
    }

    private fun drawStar(x: Float, y: Float, r: Float, alpha: Float, color: Int) {
        if (r <= 0f || alpha <= 0f) return
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(r, r)
        paint.color = HALO   // a faint dark edge, so white stars still show on a light arena
        paint.alpha = (alpha * 95).toInt()
        canvas.save(); canvas.scale(1.22f, 1.22f); canvas.drawPath(star, paint); canvas.restore()
        paint.color = color
        paint.alpha = (alpha * 255).toInt()
        canvas.drawPath(star, paint)
        canvas.restore()
    }

    private companion object { const val HALO = 0xFF463C14.toInt() }
}

/**
 * R19-L (PLAN 271): which picture a side of the fight shows. A transformed Pokémon shows its target's picture (its Pokédex number and shininess,
 * kept in the fight's notes, so a redraw, a restart or another screen draws the same); when the transform is over (switch, the fight ended)
 * the notes are gone and the Pokémon's own picture shows again. The info box keeps the Pokémon's own name.
 */
internal object TransformSprite {
    /** The picture of our [m]; [battle] = the fight [m] is the Main of (null: no fight, or [m] is not the Main). */
    fun player(m: com.husarp.ankigo.game.Mon, battle: com.husarp.ankigo.game.Battle?, data: com.husarp.ankigo.game.GameData): Pair<com.husarp.ankigo.game.Species, Boolean> {
        val look = battle?.player?.let { com.husarp.ankigo.game.MoveControl.transformLook(it) }
        val copy = look?.let { data.species[it.first] }
        return if (copy != null) copy to look.second else m.lookSpecies(data) to m.shiny
    }

    /** The picture of the wild Pokémon of [battle]; null when its species is not in the data. */
    fun wild(battle: com.husarp.ankigo.game.Battle, data: com.husarp.ankigo.game.GameData): Pair<com.husarp.ankigo.game.Species, Boolean>? {
        val own = data.species[battle.wild.species] ?: return null
        val look = com.husarp.ankigo.game.MoveControl.transformLook(battle.enemy)
        val copy = look?.let { data.species[it.first] }
        return if (copy != null) copy to look.second else own to battle.wild.shiny
    }
}
