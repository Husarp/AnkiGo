package com.husarp.ankigo.ui

import kotlin.math.max
import kotlin.math.min

/**
 * How the battle panel fits a window (pure maths, no Android): one scale factor for the whole panel, which parts show,
 * and how the two stat cards sit in the arena. [BattlePanelView] lays itself out at the "virtual" size [Fit.virtualW] x
 * [Fit.virtualH] and draws that scaled by [Fit.scale], so sprites, texts, bars, pills, log and chips all shrink together.
 *
 * - Scale: `s = min(1, w / REF_W, h / REF_H)`, where REF_* is the smallest window the chosen set of parts looks right in
 *   unscaled (stats bar 384 dp wide, arena 344, a 212 dp arena + log + stats bar). Never above 1.
 * - Text floor: nothing is drawn under [MIN_SP] on screen. A text that would fall under it is drawn at the virtual size
 *   `max(design, MIN_SP / s)` ([Fit.floorSp]); the virtual window grows with 1 / s, so it mostly still fits.
 * - Step-out: only when the raised texts do not fit the window, parts leave in a fixed order: (1) stats bar, (2) log
 *   line, (3) XP numbers. Pokémon and both HP (bar and numbers) always stay; the last resort is a smaller scale.
 * - Cards: [ArenaGeo] puts the stat cards in the corners (wild top-left, ours bottom-right) as long as no card covers a
 *   sprite, otherwise in the middle column between the two sprites.
 */
object PanelFit {
    /** No text is drawn under this many sp on screen. */
    const val MIN_SP = 8f

    /** Smallest unscaled width (virtual dp) of the stats bar: its five chips (calibrated against the real views) + 20 dp padding. */
    const val REF_W_STATS = 385f

    /** The last resort stops shrinking here: a window this small is hopeless anyway. */
    private const val MIN_SCALE = 0.2f

    /** What to show and how, for one window. */
    data class Fit(
        /** Draw scale (at most 1): the panel is laid out at [virtualW] x [virtualH] dp and shown at [scale] times that. */
        val scale: Float,
        val virtualW: Float,
        val virtualH: Float,
        /** Smallest virtual text size in sp (MIN_SP / scale, rounded up to 0.1): smaller texts are raised to it. */
        val floorSp: Float,
        val stats: Boolean,
        val log: Boolean,
        val xp: Boolean,
        /** Menu button and tool buttons (hidden in small windows). */
        val tools: Boolean,
        val logLines: Int,
        val compactCards: Boolean,
        /** True when the cards sit in the middle column between the sprites; false when they sit in the corners. */
        val middle: Boolean,
        /** False only when even the last resort could not make everything fit without a card covering a sprite. */
        val ok: Boolean,
    )

    /**
     * Decides for a window of [w] x [h] dp. [small]: picture-in-picture or floating battle (no menu and tool buttons, one
     * log line, stats bar only with [statsBarSmall]). [strip]: the red status-problem strip shows above the arena.
     * [corner]: dp (on screen) kept free at the bottom-right corner for a resize grip.
     */
    fun decide(w: Float, h: Float, small: Boolean, statsBarSmall: Boolean, compactCards: Boolean, strip: Boolean = false, corner: Float = 0f): Fit {
        val steps = ArrayList<Shown>()
        if (!small || statsBarSmall) steps += Shown(stats = true, log = true, xp = true)
        steps += Shown(stats = false, log = true, xp = true)
        steps += Shown(stats = false, log = false, xp = true)
        steps += Shown(stats = false, log = false, xp = false)
        var f: Fit? = null
        for ((i, p) in steps.withIndex()) {
            f = evaluate(w, h, p, small, compactCards, strip, corner, shrinkToFit = i == steps.lastIndex)
            if (f.ok) break
        }
        return f!!
    }

    /** Which of the parts that can step out are shown. */
    internal class Shown(val stats: Boolean, val log: Boolean, val xp: Boolean)

    /** One step: the scale from the reference size, then the fit test; the last step shrinks further until it fits. */
    private fun evaluate(w: Float, h: Float, p: Shown, small: Boolean, compact: Boolean, strip: Boolean, corner: Float, shrinkToFit: Boolean): Fit {
        var s = min(1f, min(w / refW(p, compact), h / refH(p, small, compact, strip)))
        while (true) {
            val f = at(w, h, s, p, small, compact, strip, corner)
            if (f.ok || !shrinkToFit || s <= MIN_SCALE) return f
            s *= 0.94f
        }
    }

    private fun at(w: Float, h: Float, s: Float, p: Shown, small: Boolean, compact: Boolean, strip: Boolean, corner: Float): Fit {
        val floor = Math.ceil((MIN_SP / s * 10f).toDouble()).toFloat() / 10f
        val lines = if (small) 1 else 2
        val tools = !small
        val vw = w / s
        val vh = h / s
        val arenaH = vh - (if (strip) Heights.strip(floor) else 0f) - (if (p.log) Heights.log(lines, floor) else 0f) -
            (if (p.stats) Heights.footer(floor, tools) else 0f)
        val plan = ArenaGeo.plan(vw, arenaH, ArenaGeo.cards(floor, compact, p.xp), compact)
        val chipsFit = !p.stats || vw - corner / s >= Heights.chips(floor) + 20f
        return Fit(s, vw, vh, floor, p.stats, p.log, p.xp, tools, lines, compact, plan.middle, plan.fits && chipsFit)
    }

    /** The smallest unscaled width for these parts (virtual dp). */
    internal fun refW(p: Shown, compact: Boolean): Float = max(if (p.stats) REF_W_STATS else 0f, ArenaGeo.refW(compact, p.xp))

    /** The smallest unscaled height for these parts (virtual dp): the 212 dp arena (180 with compact cards) + the parts around it. */
    internal fun refH(p: Shown, small: Boolean, compact: Boolean, strip: Boolean): Float =
        ArenaGeo.refH(compact) + (if (strip) Heights.strip(0f) else 0f) + (if (p.log) Heights.log(if (small) 1 else 2, 0f) else 0f) +
            (if (p.stats) Heights.footer(0f, !small) else 0f)

    /** Heights (virtual dp) of the panel's parts at text floor [floor]; the views are built to these numbers. */
    internal object Heights {
        /** One line of text plus the Grant button (the tallest thing in it), 5 dp padding above and below. */
        fun strip(floor: Float) = 10f + max(TextMath.line(TextMath.at(12f, floor)), TextMath.line(TextMath.at(11f, floor)) + 10f)

        /** The log card with its 8 dp top margin. */
        fun log(lines: Int, floor: Float) = 8f + TextMath.logBox(lines, floor)

        /** Goal row (the tool buttons, 30 dp, or the goal text) + 4 dp + chips row, 9 dp padding above and below. */
        fun footer(floor: Float, tools: Boolean) =
            18f + max(TextMath.line(TextMath.at(12f, floor)), if (tools) 30f else 8f * TextMath.bar(floor)) + 4f + chipH(floor)

        fun chipH(floor: Float) = TextMath.line(TextMath.at(11f, floor)) + 8f

        /** Width of the chips row: five chips with 18 dp padding and label margin each, 4 dp gaps, icons and label texts. */
        fun chips(floor: Float) = 5f * 22f + 4f * 5f + 5f * 13f * TextMath.at(11f, floor) / 11f + 190f * TextMath.at(11f, floor) / 11f
    }
}

/** Text maths shared by [PanelFit] and the views. */
internal object TextMath {
    /** Nunito's ascent + descent per em (the views have font padding off). */
    private const val LINE = 1.364f

    /** The virtual size of a text of design size [base] sp under floor [floor]. */
    fun at(base: Float, floor: Float) = max(base, floor)

    fun line(sp: Float) = LINE * sp

    /** Bars and other non-text strokes grow with the raised text so they do not turn into hairlines. */
    fun bar(floor: Float) = max(1f, floor / 12f)

    /** The log card (without its top margin): [lines] lines of 1.2 x line height, 10 dp padding. */
    fun logBox(lines: Int, floor: Float) = lines * 1.2f * line(at(13f, floor)) + 10f
}

/**
 * The arena's geometry (virtual dp): corner layout or middle layout, from the arena's size and the two cards' sizes.
 * The cards' sizes come from [cards] (their widths are set by the arena, their heights follow the text).
 */
internal object ArenaGeo {
    /** Sprite pictures never quite fill their holder (whole-pixel scaling): the overlap test uses this share of it. */
    private const val ART = 0.85f
    private const val A_MIN = 0.6f

    class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
        val right get() = x + w
        val bottom get() = y + h
        fun hits(o: Box) = x < o.right - 0.01f && o.x < right - 0.01f && y < o.bottom - 0.01f && o.y < bottom - 0.01f

        /** The part of a sprite holder the picture can fill: bottom-centre. */
        fun art() = Box(x + w * (1f - ART) / 2f, y + h * (1f - ART), w * ART, h * ART)
    }

    /** Sizes of the two cards: [wildH] without the catch tag, [tagH] 0 when there is none. */
    class Cards(val wildW: Float, val wildH: Float, val tagH: Float, val playerW: Float, val playerH: Float)

    class Plan(
        val middle: Boolean,
        val fits: Boolean,
        val wildCard: Box,
        val playerCard: Box,
        val wildSprite: Box,
        val playerSprite: Box,
        val wildPlat: Box,
        val playerPlat: Box,
        /** Sprite scale: 1 = the mockup's 110 x 100 and 140 x 128 dp holders. */
        val a: Float,
    )

    fun refH(compact: Boolean) = if (compact) 180f else 212f

    /** Cards + the two sprites at A_MIN, in the middle column: 344 dp (full cards), 302 dp (compact cards). */
    fun refW(compact: Boolean, xp: Boolean): Float {
        val c = cards(0f, compact, xp)
        return max(c.wildW, c.playerW) + 250f * A_MIN + 2f * MID_EDGE + 2f * MID_GAP
    }

    private const val MID_EDGE = 6f
    private const val MID_GAP = 4f

    /** The cards at text floor [floor]. Their widths grow with the raised text (floor / 12, at least 1). */
    fun cards(floor: Float, compact: Boolean, xp: Boolean): Cards {
        val k = max(1f, floor / 12f)
        val bar = TextMath.bar(floor)
        if (compact) {
            // name + Lv, then a thin HP bar with the small HP number next to it
            val h = 5f + TextMath.line(TextMath.at(14f, floor)) + 3f + max(5f * bar, TextMath.line(TextMath.at(10f, floor))) + 6f
            return Cards(128f * k, h, 0f, 128f * k, h)
        }
        val wild = 7f + TextMath.line(TextMath.at(14f, floor)) + (4f + TextMath.line(TextMath.at(9f, floor)) + 2f) + (6f + 7f * bar) + TextMath.line(TextMath.at(11f, floor)) + 8f
        val tag = TextMath.line(TextMath.at(10f, floor)) + 12f
        val player = 7f + TextMath.line(TextMath.at(14f, floor)) + (6f + 7f * bar) + (3f + TextMath.line(TextMath.at(11f, floor))) + (4f + 3f * bar) + 8f
        return Cards(166f * k, wild, tag, (if (xp) 170f else 132f) * k, player)
    }

    /**
     * Corner layout when no card covers a sprite (the mockup's look); otherwise the middle layout. [w] x [h] is the arena.
     */
    fun plan(w: Float, h: Float, c: Cards, compact: Boolean): Plan {
        val ref = refH(compact)
        val corners = corners(w, h, c, ref)
        return if (corners.fits) corners else middle(w, h, c, ref)
    }

    private fun corners(w: Float, h: Float, c: Cards, ref: Float): Plan {
        val a = (h / ref).coerceIn(0.66f, 1.5f)
        val down = max(0f, h - ref * a) * 0.75f
        val infoMax = max((w - 30f) / 2f, 120f)
        val ww = min(c.wildW, infoMax)
        val pw = min(c.playerW, infoMax)
        val wild = Box(10f, 10f, ww, c.wildH + c.tagH)
        val player = Box(w - 10f - pw, h - 12f - c.playerH, pw, c.playerH)
        val wildSprite = Box(w - 42f * a - 110f * a, 12f * a + down, 110f * a, 100f * a)
        val playerSprite = Box(22f * a, h - 6f * a - 128f * a, 140f * a, 128f * a)
        val wildPlat = Box(w - 22f * a - 150f * a, 92f * a + down, 150f * a, 30f * a)
        val playerPlat = Box(4f * a, h + 8f * a - 40f * a, 180f * a, 40f * a)
        val clear = !wild.hits(wildSprite.art()) && !wild.hits(playerSprite.art()) && !player.hits(wildSprite.art()) &&
            !player.hits(playerSprite.art()) && !wild.hits(player)
        return Plan(false, clear, wild, player, wildSprite, playerSprite, wildPlat, playerPlat, a)
    }

    /**
     * Our sprite at the left, the wild one at the right, both cards stacked in the middle column between them (wild card
     * on top). The sprites shrink (together) until the column and the sprites fit side by side.
     */
    private fun middle(w: Float, h: Float, c: Cards, ref: Float): Plan {
        val cw = max(c.wildW, c.playerW)
        val side = w - cw - 2f * MID_EDGE - 2f * MID_GAP
        val a = min(min((h / ref).coerceIn(0.66f, 1.5f), side / 250f), h / 134f)
        val down = max(0f, h - ref * a) * 0.75f
        val playerSprite = Box(MID_EDGE, h - 6f * a - 128f * a, 140f * a, 128f * a)
        val wildSprite = Box(w - MID_EDGE - 110f * a, 12f * a + down, 110f * a, 100f * a)
        val stack = c.wildH + c.tagH + 6f + c.playerH
        val left = playerSprite.right + MID_GAP
        val right = wildSprite.x - MID_GAP
        val cx = left + max(0f, (right - left - cw) / 2f)
        val top = max(MID_EDGE, (h - stack) / 2f)
        val wild = Box(cx, top, cw, c.wildH + c.tagH)
        val player = Box(cx, top + c.wildH + c.tagH + 6f, cw, c.playerH)
        val wildPlat = Box(wildSprite.x - 6f * a, wildSprite.y + 80f * a, 122f * a, 30f * a)
        val playerPlat = Box(0f, h + 8f * a - 40f * a, playerSprite.right + 10f * a, 40f * a)
        val fits = a >= A_MIN - EPS / 100f && stack + 2f * MID_EDGE <= h + EPS && right - left >= cw - EPS
        return Plan(true, fits, wild, player, wildSprite, playerSprite, wildPlat, playerPlat, a)
    }
}
