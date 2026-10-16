package com.husarp.ankigo.ui

import kotlin.math.max
import kotlin.math.min

/**
 * How the battle panel fits a window (pure maths, no Android): one scale factor for the whole panel, which parts show,
 * and how the two stat cards sit in the arena. [BattlePanelView] lays itself out at the "virtual" size [Fit.virtualW] x
 * [Fit.virtualH] and draws that scaled by [Fit.scale], so sprites, texts, bars, pills, log and chips all shrink together.
 *
 * - Scale: `s = min(1, w / REF_W, h / REF_H)`, where REF_* is the smallest window the chosen set of parts looks right in
 *   unscaled: 376 dp wide (the stats bar's five chips; 340 dp for the arena alone) and 212 dp of arena + log card + stats
 *   bar (about 347 dp; the arena is 180 dp with compact cards). Never above 1.
 * - Text floor: nothing is drawn under [MIN_SP] on screen. A text that would fall under it is drawn at the virtual size
 *   `max(design, MIN_SP / s)` ([Fit.floorSp]); the virtual window grows with 1 / s, so it mostly still fits. If it does
 *   not, the scale shrinks a little more (down to [S_MIN], where the 14 sp names reach the floor too).
 * - Step-out: only when it still does not fit, parts leave in a fixed order: (1) stats bar, (2) log line, (3) XP numbers.
 *   Pokémon and both HP (bar and numbers) always stay; the last resort is a smaller scale.
 * - Cards: [ArenaGeo] puts each stat card under (or next to) its own Pokémon, or - with the pref off - in the corners
 *   (wild top-left, ours bottom-right), as long as no card covers a sprite; otherwise in the middle column between the
 *   two sprites. The sprites and cards also grow into free space (sprites up to 40 % of the arena's height).
 * - Small windows (picture-in-picture, floating battle) under 260 dp on their short side always use the compact cards.
 */
object PanelFit {
    /** No text is drawn under this many sp on screen. */
    const val MIN_SP = 8f

    /** Smallest unscaled width (virtual dp) of the stats bar: its chips row (356 dp, measured on the real views) + 20 dp padding. */
    const val REF_W_STATS = 376f

    /**
     * The smallest scale before parts step out: about 8 / 14, where the biggest text (the 14 sp names) reaches the floor.
     * Under it every text sits on the floor, so shrinking further only makes the pictures smaller.
     */
    private const val S_MIN = 0.55f

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
     * [corner]: dp (on screen) kept free at the bottom-right corner for a resize grip. [footerTools]: the menu and tool
     * buttons are wanted in the goal row (never in small windows). [own]: each card under its own Pokémon (else the
     * corners). A small window whose short side is under 260 dp has no room for the full cards: it uses the compact ones
     * whatever the pref says.
     */
    fun decide(
        w: Float, h: Float, small: Boolean, statsBarSmall: Boolean, compactCards: Boolean,
        strip: Boolean = false, corner: Float = 0f, footerTools: Boolean = true, own: Boolean = true,
    ): Fit {
        val cc = compactCards || (small && min(w, h) < 260f)
        if (w < 1f || h < 1f) return Fit(1f, w, h, MIN_SP, stats = false, log = false, xp = false, tools = false, logLines = 1, compactCards = cc, middle = false, ok = false)
        val tools = footerTools && !small
        val steps = ArrayList<Shown>()
        if (!small || statsBarSmall) steps += Shown(stats = true, log = true, xp = true)
        steps += Shown(stats = false, log = true, xp = true)
        steps += Shown(stats = false, log = false, xp = true)
        steps += Shown(stats = false, log = false, xp = false)
        var f: Fit? = null
        for ((i, p) in steps.withIndex()) {
            f = evaluate(w, h, p, small, tools, cc, strip, corner, own, last = i == steps.lastIndex)
            if (f.ok) break
        }
        return f!!
    }

    /** Which of the parts that can step out are shown. */
    internal class Shown(val stats: Boolean, val log: Boolean, val xp: Boolean)

    /**
     * One step: the scale from the reference size; when the raised texts do not fit, a little smaller (0.97 x) while
     * it stays at least [S_MIN]. A step that cannot get there is not ok (the next part steps out); the last step goes on
     * down to [MIN_SCALE], so the cards never cover a sprite.
     */
    private fun evaluate(w: Float, h: Float, p: Shown, small: Boolean, tools: Boolean, compact: Boolean, strip: Boolean, corner: Float, own: Boolean, last: Boolean): Fit {
        var s = max(MIN_SCALE, min(1f, min(w / refW(p, compact), h / refH(p, small, tools, compact, strip))))
        while (true) {
            val f = at(w, h, s, p, small, tools, compact, strip, corner, own)
            if (f.ok && (s >= S_MIN || last)) return f
            val next = s * 0.97f
            if (next < (if (last) MIN_SCALE else S_MIN)) return if (last) f else f.copy(ok = false)
            s = next
        }
    }

    private fun at(w: Float, h: Float, s: Float, p: Shown, small: Boolean, tools: Boolean, compact: Boolean, strip: Boolean, corner: Float, own: Boolean): Fit {
        val floor = Math.ceil((MIN_SP / s * 10f).toDouble()).toFloat() / 10f
        val lines = if (small) 1 else 2
        val vw = w / s
        val vh = h / s
        val arenaH = vh - (if (strip) Heights.strip(floor) else 0f) - (if (p.log) Heights.log(lines, floor) else 0f) -
            (if (p.stats) Heights.footer(floor, tools) else 0f)
        val plan = ArenaGeo.plan(vw, arenaH, ArenaGeo.cards(floor, compact, p.xp), compact, own)
        val chipsFit = !p.stats || vw - corner / s >= Heights.chips(floor) + 20f
        return Fit(s, vw, vh, floor, p.stats, p.log, p.xp, tools, lines, compact, plan.middle, plan.fits && chipsFit)
    }

    /** The smallest unscaled width for these parts (virtual dp). */
    internal fun refW(p: Shown, compact: Boolean): Float = max(if (p.stats) REF_W_STATS else 0f, ArenaGeo.refW(compact, p.xp))

    /** The smallest unscaled height for these parts (virtual dp): the 212 dp arena (180 with compact cards) + the parts around it. */
    internal fun refH(p: Shown, small: Boolean, tools: Boolean, compact: Boolean, strip: Boolean): Float =
        ArenaGeo.refH(compact) + (if (strip) Heights.strip(0f) else 0f) + (if (p.log) Heights.log(if (small) 1 else 2, 0f) else 0f) +
            (if (p.stats) Heights.footer(0f, tools) else 0f)

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

        /** Width of the chips row: 20 dp gaps and 18 dp padding + label margin per chip (110 dp), plus icons and label texts (246 dp at 11 sp). */
        fun chips(floor: Float) = 110f + 246f * TextMath.at(11f, floor) / 11f
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
 * The arena's geometry (virtual dp): each card under (or beside) its own Pokémon, the classic corners, or the middle
 * layout, from the arena's size and the two cards' sizes. The cards' sizes come from [cards] (their widths are set by the
 * arena, their heights follow the text); the plan's card boxes are the on-screen ones (that size times [Plan.k]).
 */
internal object ArenaGeo {
    /** Sprite pictures never quite fill their holder (whole-pixel scaling): the overlap test uses this share of it. */
    private const val ART = 0.85f
    private const val A_MIN = 0.6f

    /** The under, beside and corner layouts keep the sprites at 85 % of the mockup's size at least; under that the middle layout gives them more room. */
    private const val A_CORNERS = 0.85f
    private const val EPS = 0.5f

    class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
        val right get() = x + w
        val bottom get() = y + h
        fun hits(o: Box) = x < o.right - 0.01f && o.x < right - 0.01f && y < o.bottom - 0.01f && o.y < bottom - 0.01f

        /** The part of a sprite holder the picture can fill: bottom-centre. */
        fun art() = Box(x + w * (1f - ART) / 2f, y + h * (1f - ART), w * ART, h * ART)
    }

    /** Sizes of the two cards (the wild card's NEW sticker overhangs its corner and takes no room). */
    class Cards(val wildW: Float, val wildH: Float, val playerW: Float, val playerH: Float)

    /** UNDER: each card under its own sprite (tall arenas). BESIDE: next to it (short ones). CORNERS: the classic opposite corners. MIDDLE: stacked between the sprites. */
    enum class Layout { UNDER, BESIDE, CORNERS, MIDDLE }

    class Plan(
        val layout: Layout,
        val fits: Boolean,
        val wildCard: Box,
        val playerCard: Box,
        val wildSprite: Box,
        val playerSprite: Box,
        val wildPlat: Box,
        val playerPlat: Box,
        /** Sprite scale: 1 = the mockup's 110 x 100 and 140 x 128 dp holders. */
        val a: Float,
        /** Card scale (1 to 1.4): the cards are laid out at their normal size and drawn k times bigger; the card boxes include it. */
        val k: Float = 1f,
    ) {
        val middle get() = layout == Layout.MIDDLE
    }

    fun refH(compact: Boolean) = if (compact) 180f else 212f

    /** Cards + the two sprites at A_MIN, in the middle column: 340 dp (full cards), 310 dp (compact cards). */
    fun refW(compact: Boolean, xp: Boolean): Float {
        val c = cards(0f, compact, xp)
        return max(c.wildW, c.playerW) + 250f * A_MIN + 2f * MID_EDGE + 2f * MID_GAP
    }

    private const val MID_EDGE = 6f
    private const val MID_GAP = 4f
    private const val UNDER_EDGE = 8f

    /**
     * The status badge row under a card's HP bar (item 95), in virtual dp: 14 dp (12 on compact cards), grown with the raised
     * text like the bars. Its height is part of the card whether or not a badge shows, so nothing moves when an effect comes.
     */
    fun statusRow(floor: Float, compact: Boolean) = (if (compact) 12f else 14f) * TextMath.bar(floor)

    /** The cards at text floor [floor]. Their widths grow with the raised text (floor / 12, at least 1). */
    fun cards(floor: Float, compact: Boolean, xp: Boolean): Cards {
        val k = max(1f, floor / 12f)
        val bar = TextMath.bar(floor)
        val row = statusRow(floor, compact)
        if (compact) {
            // name + Lv, then a thin HP bar with the small HP number next to it, then the status row
            val h = 5f + TextMath.line(TextMath.at(14f, floor)) + 3f + max(5f * bar, TextMath.line(TextMath.at(10f, floor))) + row + 6f
            return Cards(140f * k, h, 140f * k, h)
        }
        // the status row sits between the HP bar and its numbers (on our card it takes the place of their 3 dp gap)
        val wild = 7f + TextMath.line(TextMath.at(14f, floor)) + (4f + TextMath.line(TextMath.at(9f, floor)) + 2f) + (6f + 7f * bar) + row + TextMath.line(TextMath.at(11f, floor)) + 8f
        val player = 7f + TextMath.line(TextMath.at(14f, floor)) + (6f + 7f * bar) + row + TextMath.line(TextMath.at(11f, floor)) + (4f + 3f * bar) + 8f
        // the wild card is also as wide as its type chips: the two longest (ELECTRIC + FIGHTING) are 94 dp of text at 9 sp, grown with the floor
        val chips = 49f + 11f * max(9f, floor)
        return Cards(max(166f * k, chips), wild, (if (xp) 170f else 132f) * k, player)
    }

    /**
     * The layout for an arena of [w] x [h]. [own]: each card under its own Pokémon (UNDER, tall arenas), else next to it
     * (BESIDE, short ones); not [own]: the classic corners. What does not fit gives the middle layout. One rule from
     * picture-in-picture to tablet, and it also grows: the sprites' scale `a` goes from the mockup's size up to 40 % of
     * the arena's height (60 % of its width, at most 4), the cards' scale `k` up to 1.4. The first (a, k) that fits wins:
     * biggest sprites first, then biggest cards. [same]: our sprite box is the wild one's size (110 x 100) instead of 140 x 128;
     * the wild one and the search stay as they are, so only our side gets more room.
     */
    fun plan(w: Float, h: Float, c: Cards, compact: Boolean, own: Boolean = true, same: Boolean = false): Plan {
        val ref = refH(compact)
        val base = (h / ref).coerceIn(0.66f, 1.5f)
        val aMax = max(base, min(0.4f * h / 128f, min(0.6f * w / 140f, 4f)))
        val k0 = min(w / 300f, h / 430f).coerceIn(1f, 1.4f)
        val low = min(base, A_CORNERS)
        val found = if (own) search(aMax, low, k0) { a, k -> under(w, h, c, a, k, same) } ?: search(aMax, low, k0) { a, k -> beside(w, h, c, ref, a, k, same) }
        else search(aMax, low, k0) { a, k -> corners(w, h, c, ref, a, k, same) }
        return found ?: middle(w, h, c, ref, same)
    }

    /** The first plan that fits, sprite scale from [aMax] down to [aMin] (steps of 0.05), for each of those the card scale from [k0] down to 1. */
    private inline fun search(aMax: Float, aMin: Float, k0: Float, build: (Float, Float) -> Plan): Plan? {
        var a = aMax
        while (a >= aMin - 0.001f) {
            var k = k0
            while (true) {
                val p = build(a, k)
                if (p.fits) return p
                if (k <= 1f) break
                k = max(1f, k - 0.05f)
            }
            if (a <= aMin) break
            a = max(aMin, a - 0.05f)
        }
        return null
    }

    private fun inside(w: Float, h: Float, b: Box) = b.x >= -0.01f && b.y >= -0.01f && b.right <= w + 0.01f && b.bottom <= h + 0.01f

    /** Every layout's rule: all inside the arena, no card on a sprite's picture or on the other card, the two pictures apart. */
    private fun clear(w: Float, h: Float, wild: Box, player: Box, wildSprite: Box, playerSprite: Box): Boolean {
        val wa = wildSprite.art()
        val pa = playerSprite.art()
        // the wild block wraps its height (the card): 4 dp of slack at the bottom for font differences
        return inside(w, h - 4f, wild) && inside(w, h, player) && inside(w, h, wa) && inside(w, h, pa) &&
            !wild.hits(wa) && !wild.hits(pa) && !player.hits(wa) && !player.hits(pa) && !wild.hits(player) && !wa.hits(pa)
    }

    /** Sprite holders and platforms of the classic look: the wild one at the right, ours bottom-left; the wild one sits a bit lower in tall arenas. */
    private class Sprites(val wild: Box, val player: Box, val wildPlat: Box, val playerPlat: Box)

    private fun classic(w: Float, h: Float, ref: Float, a: Float, same: Boolean): Sprites {
        val down = max(0f, h - ref * a) * 0.35f
        return Sprites(
            Box(w - 42f * a - 110f * a, 12f * a + down, 110f * a, 100f * a),
            Box(22f * a, h - 6f * a - playerH(same) * a, playerW(same) * a, playerH(same) * a),
            Box(w - 22f * a - 150f * a, 92f * a + down, 150f * a, 30f * a),
            Box(4f * a, h + 8f * a - 40f * a, 180f * a * (playerW(same) / 140f), 40f * a),
        )
    }

    /** Our sprite holder in the mockup's dp: enlarged (140 x 128) or, with [same], the wild one's size. */
    private fun playerW(same: Boolean) = if (same) 110f else 140f
    private fun playerH(same: Boolean) = if (same) 100f else 128f

    /** The mockup's look: wild card top-left, ours bottom-right, the cards [k] times bigger. */
    private fun corners(w: Float, h: Float, c: Cards, ref: Float, a: Float, k: Float, same: Boolean): Plan {
        val s = classic(w, h, ref, a, same)
        val wild = Box(10f, 10f, c.wildW * k, c.wildH * k)
        val player = Box(w - 10f - c.playerW * k, h - 12f - c.playerH * k, c.playerW * k, c.playerH * k)
        return Plan(Layout.CORNERS, clear(w, h, wild, player, s.wild, s.player), wild, player, s.wild, s.player, s.wildPlat, s.playerPlat, a, k)
    }

    /** Short arenas: the classic sprites, each card beside its own Pokémon (the wild card in the top row to the left of it, ours in the bottom row to the right of it). */
    private fun beside(w: Float, h: Float, c: Cards, ref: Float, a: Float, k: Float, same: Boolean): Plan {
        val s = classic(w, h, ref, a, same)
        val cw = c.wildW * k
        val pw = c.playerW * k
        val wild = Box(max(10f, s.wild.art().x - 6f - cw), 10f, cw, c.wildH * k)
        val player = Box(min(w - 10f - pw, s.player.art().right + 6f), h - 12f - c.playerH * k, pw, c.playerH * k)
        return Plan(Layout.BESIDE, clear(w, h, wild, player, s.wild, s.player), wild, player, s.wild, s.player, s.wildPlat, s.playerPlat, a, k)
    }

    /**
     * Tall arenas: the wild sprite top-right with its card right-aligned right under it; our card bottom-left with our
     * sprite right above it. Free height between the two groups moves the wild one down by 30 % of it.
     */
    private fun under(w: Float, h: Float, c: Cards, a: Float, k: Float, same: Boolean): Plan {
        val cw = c.wildW * k
        val ch = c.wildH * k
        val player = Box(UNDER_EDGE, h - UNDER_EDGE - c.playerH * k, c.playerW * k, c.playerH * k)
        val playerSprite = Box(UNDER_EDGE, player.y - 4f - playerH(same) * a, playerW(same) * a, playerH(same) * a)
        val extra = playerSprite.art().y - (UNDER_EDGE + 100f * a + 4f + ch) - 6f
        val wildSprite = Box(w - UNDER_EDGE - 110f * a, UNDER_EDGE + max(0f, extra) * 0.3f, 110f * a, 100f * a)
        val wild = Box(w - UNDER_EDGE - cw, wildSprite.bottom + 4f, cw, ch)
        val wildPlat = Box(wildSprite.x - 20f * a, wildSprite.y + 80f * a, 150f * a, 30f * a)
        val playerPlat = Box(playerSprite.x - 20f * a, playerSprite.bottom - 32f * a, 180f * a * (playerW(same) / 140f), 40f * a)
        return Plan(Layout.UNDER, clear(w, h, wild, player, wildSprite, playerSprite), wild, player, wildSprite, playerSprite, wildPlat, playerPlat, a, k)
    }

    /**
     * Our sprite at the left, the wild one at the right, both cards stacked in the middle column between them (wild card
     * on top). The sprites shrink (together) until the column and the sprites fit side by side.
     */
    private fun middle(w: Float, h: Float, c: Cards, ref: Float, same: Boolean = false): Plan {
        val cw = max(c.wildW, c.playerW)
        val side = w - cw - 2f * MID_EDGE - 2f * MID_GAP
        val a = min(min((h / ref).coerceIn(0.66f, 1.5f), side / (playerW(same) + 110f)), h / (playerH(same) + 6f))
        val down = max(0f, h - ref * a) * 0.75f
        val playerSprite = Box(MID_EDGE, h - 6f * a - playerH(same) * a, playerW(same) * a, playerH(same) * a)
        val wildSprite = Box(w - MID_EDGE - 110f * a, 12f * a + down, 110f * a, 100f * a)
        val stack = c.wildH + 6f + c.playerH
        val left = playerSprite.right + MID_GAP
        val right = wildSprite.x - MID_GAP
        val cx = left + max(0f, (right - left - cw) / 2f)
        val top = max(MID_EDGE, (h - stack) / 2f)
        val wild = Box(cx, top, cw, c.wildH)
        val player = Box(cx, top + c.wildH + 6f, cw, c.playerH)
        val wildPlat = Box(min(wildSprite.x - 6f * a, w - 122f * a), wildSprite.y + 80f * a, 122f * a, 30f * a)
        val playerPlat = Box(0f, h + 8f * a - 40f * a, playerSprite.right + 10f * a, 40f * a)
        val fits = a >= A_MIN - 0.005f && stack + 2f * MID_EDGE <= h + EPS && right - left >= cw - EPS
        return Plan(Layout.MIDDLE, fits, wild, player, wildSprite, playerSprite, wildPlat, playerPlat, a)
    }
}
