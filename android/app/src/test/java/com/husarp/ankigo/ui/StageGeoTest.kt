package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

// ROUND 8 R8-02: the stage plan - sprites on the feet, cards where they cover neither Pokémon.
class StageGeoTest {
    private val shapes = listOf(0.5f, 0.6f, 1f, 1.25f, 1.4f, 2.2f, 2.6f, 3.5f)

    // panel sizes in dp (phones in every shape, strips, split halves, tablets)
    private val gridW = listOf(250f, 300f, 360f, 412f, 520f, 640f, 800f, 869f)
    private val gridH = listOf(130f, 160f, 200f, 250f, 300f, 400f, 640f)

    // card sizes in dp (a full wild card, a full player card)
    private val wcw = 166f
    private val wch = 70f
    private val pcw = 170f
    private val pch = 82f

    private fun stage(id: String, w: Float, h: Float, zoom: Float = 1f) = StageFit.fit(BackdropCatalog.byId(id)!!, w, h, zoom)!!

    private fun plan(id: String, w: Float, h: Float, own: Boolean, k0: Float = 1.2f, zoom: Float = 1f, scale: Float = 1f) =
        StageGeo.plan(w, h, stage(id, w, h, zoom), wcw * scale, wch * scale, pcw * scale, pch * scale, k0, own)

    private fun inside(w: Float, h: Float, b: ArenaGeo.Box) = b.x >= -0.01f && b.y >= -0.01f && b.right <= w + 0.01f && b.bottom <= h + 0.01f

    private fun assertClear(tag: String, w: Float, h: Float, p: StagePlan) {
        val wa = p.wildSprite.art()
        val pa = p.playerSprite.art()
        assertTrue("$tag wild inside", inside(w, h - 4f, p.wildCard))
        assertTrue("$tag player inside", inside(w, h, p.playerCard))
        assertFalse("$tag wild on wild", p.wildCard.hits(wa))
        assertFalse("$tag wild on ours", p.wildCard.hits(pa))
        assertFalse("$tag player on wild", p.playerCard.hits(wa))
        assertFalse("$tag player on ours", p.playerCard.hits(pa))
        assertFalse("$tag cards overlap", p.wildCard.hits(p.playerCard))
    }

    // an independent brute-force: is there ANY clear pair at sprite scale f and card scale k (any of the 4 x 4 positions)
    private fun anyClear(w: Float, h: Float, s: Stage, f: Float, k: Float): Boolean {
        val side = { frame: Float -> frame * f }
        val ws = side(s.wildFrame)
        val ps = side(s.playerFrame)
        val wa = ArenaGeo.Box(s.wildFeetX - ws / 2, s.wildFeetY - ws, ws, ws).art()
        val pa = ArenaGeo.Box(s.playerFeetX - ps / 2, s.playerFeetY - ps, ps, ps).art()
        val cw = wcw * k; val ch = wch * k; val qw = pcw * k; val qh = pch * k
        val mid = (pa.right + wa.x) / 2f
        val wx = listOf(wa.x - 6f - cw, 8f, mid - cw / 2f, w - 8f - cw).map { max(8f, min(it, w - 8f - cw)) }
        val px = listOf(pa.right + 6f, w - 8f - qw, mid - qw / 2f, 8f).map { max(8f, min(it, w - 8f - qw)) }
        for (x in wx) for (y in px) {
            val wild = ArenaGeo.Box(x, 8f, cw, ch)
            val pl = ArenaGeo.Box(y, h - 10f - qh, qw, qh)
            if (inside(w, h - 4f, wild) && inside(w, h, pl) && !wild.hits(wa) && !wild.hits(pa) && !pl.hits(wa) && !pl.hits(pa) && !wild.hits(pl)) return true
        }
        return false
    }

    // ---------------------------------------------------------------- geometry

    @Test fun spritesStandOnTheFeet() {
        val w = 800f; val h = 500f
        val s = stage("forest", w, h)
        val p = StageGeo.plan(w, h, s, wcw, wch, pcw, pch, 1f, true)
        assertEquals(1f, p.spriteScale, 0f)
        assertEquals(s.playerFeetX, p.playerSprite.x + p.playerSprite.w / 2f, 0.01f)
        assertEquals(s.playerFeetY, p.playerSprite.bottom, 0.01f)
        assertEquals(s.playerFrame, p.playerSprite.h, 0.01f)
        assertEquals(s.playerFrame, p.playerSprite.w, 0.01f)
        assertEquals(s.wildFeetX, p.wildSprite.x + p.wildSprite.w / 2f, 0.01f)
        assertEquals(s.wildFeetY, p.wildSprite.bottom, 0.01f)
        assertEquals(s.wildFrame, p.wildSprite.h, 0.01f)
    }

    @Test fun smallerSpriteScaleKeepsTheFeet() {
        // a tiny panel forces f below 1: holders shrink around the same feet
        val w = 330f; val h = 250f
        val s = stage("night-alley", w, h)
        val p = StageGeo.plan(w, h, s, wcw, wch, pcw, pch, 1f, true)
        assertTrue(p.spriteScale < 1f)
        assertEquals(s.playerFeetY, p.playerSprite.bottom, 0.01f)
        assertEquals(s.wildFeetX, p.wildSprite.x + p.wildSprite.w / 2f, 0.01f)
        assertEquals(s.playerFrame * p.spriteScale, p.playerSprite.h, 0.01f)
    }

    // ---------------------------------------------------------------- preference

    @Test fun ownPutsEachCardBesideItsPokemon() {
        val w = 1400f; val h = 1000f
        val p = plan("forest", w, h, own = true, k0 = 1f)
        assertTrue(p.clear)
        assertEquals(1f, p.spriteScale, 0f)
        // wild card: BW, 6 dp left of the wild picture, top row; ours: BP, 6 dp right of our picture, bottom row
        assertEquals(p.wildSprite.art().x - 6f, p.wildCard.right, 0.01f)
        assertEquals(8f, p.wildCard.y, 0.01f)
        assertEquals(p.playerSprite.art().right + 6f, p.playerCard.x, 0.01f)
        assertEquals(h - 10f, p.playerCard.bottom, 0.01f)
    }

    @Test fun notOwnPutsTheCardsInTheCorners() {
        val w = 1400f; val h = 1000f
        val p = plan("forest", w, h, own = false, k0 = 1f)
        assertTrue(p.clear)
        assertEquals(8f, p.wildCard.x, 0.01f)
        assertEquals(8f, p.wildCard.y, 0.01f)
        assertEquals(w - 8f, p.playerCard.right, 0.01f)
        assertEquals(h - 10f, p.playerCard.bottom, 0.01f)
    }

    @Test fun cardsGrowToK0WhenThereIsRoom() {
        val p = plan("forest", 1400f, 1000f, own = true, k0 = 1.4f)
        assertEquals(1.4f, p.k, 0.0001f)
        assertEquals(wcw * 1.4f, p.wildCard.w, 0.01f)
        assertEquals(pch * 1.4f, p.playerCard.h, 0.01f)
        val one = plan("forest", 1400f, 1000f, own = true, k0 = 0.5f)
        assertEquals("k0 under 1 is read as 1", 1f, one.k, 0.0001f)
    }

    // ---------------------------------------------------------------- properties over the catalog

    @Test fun clearPlansReallyAreClear() {
        for (b in BackdropCatalog.ALL) for (a in shapes) for (own in listOf(true, false)) for (zoom in listOf(1f, 0.72f)) {
            val h = 420f
            val w = a * h
            val p = plan(b.id, w, h, own, zoom = zoom)
            if (p.clear) assertClear("${b.id} $a own=$own zoom=$zoom", w, h, p)
        }
    }

    @Test fun spriteScaleOnlyBelowOneWhenOneHasNoClearPair() {
        for (b in BackdropCatalog.ALL) for (a in shapes) {
            val h = 420f
            val w = a * h
            val s = stage(b.id, w, h)
            val p = plan(b.id, w, h, own = true)
            val scales = listOf(1f, 0.9f, 0.8f, 0.7f, 0.6f)
            if (p.clear) {
                assertTrue("${b.id} $a", p.spriteScale in scales)
                for (f in scales.filter { it > p.spriteScale + 0.001f }) {
                    var k = 1.2f
                    while (true) {
                        assertFalse("${b.id} $a f=$f k=$k", anyClear(w, h, s, f, k))
                        if (k <= 1f) break
                        k = max(1f, k - 0.05f)
                    }
                }
                // at the chosen f, no bigger card scale was clear
                var k = 1.2f
                while (k > p.k + 0.001f) {
                    assertFalse("${b.id} $a bigger k=$k", anyClear(w, h, s, p.spriteScale, k))
                    k = max(1f, k - 0.05f)
                }
                assertTrue("${b.id} $a", anyClear(w, h, s, p.spriteScale, p.k))
            } else {
                assertEquals(0.7f, p.spriteScale, 0f)
                assertEquals(1f, p.k, 0f)
                for (f in scales) assertFalse("${b.id} $a f=$f", anyClear(w, h, s, f, 1f))
            }
        }
    }

    @Test fun kStaysBetweenOneAndK0() {
        for (b in BackdropCatalog.ALL) for (a in shapes) {
            val h = 420f
            val p = plan(b.id, a * h, h, true, k0 = 1.4f)
            assertTrue("${b.id} $a", p.k >= 1f - 0.0001f && p.k <= 1.4f + 0.0001f)
        }
    }

    @Test fun tallPhoneBattleAndLandscapeAreAlwaysClear() {
        // (the real arenas at f 1 were measured by the manager with the real card sizes; these card sizes are round numbers, so f may dip a notch)
        for (b in BackdropCatalog.ALL) for ((w, h) in listOf(412f to 640f, 869f to 400f)) for (own in listOf(true, false)) {
            val p = plan(b.id, w, h, own)
            assertTrue("${b.id} ${w}x$h own=$own", p.clear)
            assertTrue("${b.id} ${w}x$h own=$own f=${p.spriteScale}", p.spriteScale >= 0.8f)
        }
    }

    // ---------------------------------------------------------------- the fallback

    @Test fun fallbackWhenNothingFitsIsDeterministic() {
        // a panel far too small for the cards
        val w = 200f; val h = 120f
        val s = stage("night-alley", w, h)
        val a = StageGeo.plan(w, h, s, wcw, wch, pcw, pch, 1.4f, true)
        val b = StageGeo.plan(w, h, s, wcw, wch, pcw, pch, 1.4f, true)
        assertFalse(a.clear)
        assertEquals(0.7f, a.spriteScale, 0f)
        assertEquals(1f, a.k, 0f)
        assertEquals(wcw, a.wildCard.w, 0.001f)
        assertEquals(a.wildCard.x, b.wildCard.x, 0f)
        assertEquals(a.wildCard.y, b.wildCard.y, 0f)
        assertEquals(a.playerCard.x, b.playerCard.x, 0f)
        assertEquals(a.playerCard.y, b.playerCard.y, 0f)
        assertEquals(s.playerFrame * 0.7f, a.playerSprite.h, 0.001f)
    }

    @Test fun fallbackPicksTheLeastOverlap() {
        // the chosen pair covers no more of the sprites than the all-corners pair would
        val w = 260f; val h = 200f
        val s = stage("forest", w, h)
        val p = StageGeo.plan(w, h, s, wcw, wch, pcw, pch, 1f, true)
        if (p.clear) return
        fun area(a: ArenaGeo.Box, b: ArenaGeo.Box): Float {
            val dx = min(a.right, b.right) - max(a.x, b.x)
            val dy = min(a.bottom, b.bottom) - max(a.y, b.y)
            return if (dx > 0f && dy > 0f) dx * dy else 0f
        }
        val wa = p.wildSprite.art(); val pa = p.playerSprite.art()
        val chosen = area(p.wildCard, wa) + area(p.wildCard, pa) + area(p.playerCard, wa) + area(p.playerCard, pa) + area(p.wildCard, p.playerCard)
        val corners = ArenaGeo.Box(8f, 8f, wcw, wch).let { c -> area(c, wa) + area(c, pa) } +
            ArenaGeo.Box(w - 8f - pcw, h - 10f - pch, pcw, pch).let { c -> area(c, wa) + area(c, pa) }
        assertTrue("$chosen vs $corners", chosen <= corners + 0.01f)
    }

    // ---------------------------------------------------------------- R8-FIX M2: clearance from what the sprites really draw

    private fun planArt(id: String, w: Float, h: Float, own: Boolean, wild: ArtShare?, ours: ArtShare?, k0: Float = 1.2f): StagePlan =
        StageGeo.plan(w, h, stage(id, w, h), wcw, wch, pcw, pch, k0, own, wild?.let { a -> { _: Float -> a } }, ours?.let { a -> { _: Float -> a } })

    @Test fun theArtLambdasGetEachFramesSideAndNullMeansTheDefault() {
        val w = 330f; val h = 250f
        val s = stage("night-alley", w, h)
        val wildSides = ArrayList<Float>(); val ourSides = ArrayList<Float>()
        val p = StageGeo.plan(w, h, s, wcw, wch, pcw, pch, 1f, true, { wildSides += it; null }, { ourSides += it; null })
        val q = StageGeo.plan(w, h, s, wcw, wch, pcw, pch, 1f, true)
        // no share given = the 85 % square, so the plan is the very same
        assertEquals(q.spriteScale, p.spriteScale, 0f)
        assertEquals(q.wildCard.x, p.wildCard.x, 0f); assertEquals(q.playerCard.y, p.playerCard.y, 0f)
        // asked at the sprite scales it tries, in px of the frame's side (the first = scale 1)
        assertEquals(s.wildFrame, wildSides.first(), 0.001f)
        assertEquals(s.playerFrame, ourSides.first(), 0.001f)
        assertTrue(wildSides.all { it <= s.wildFrame + 0.001f && it >= s.wildFrame * 0.6f - 0.001f })
    }

    @Test fun aSmallerPictureMakesAPreviouslyBlockedPairClear() {
        var better = 0
        var example = ""
        for (b in BackdropCatalog.ALL) for (w in gridW) for (h in gridH) for (own in listOf(true, false)) {
            val flat = planArt(b.id, w, h, own, null, null)
            val small = planArt(b.id, w, h, own, ArtShare(0.4f, 0.45f), ArtShare(0.4f, 0.45f))
            if ((!flat.clear && small.clear) || (flat.spriteScale < 1f - 0.001f && small.spriteScale > flat.spriteScale + 0.001f)) {
                better++
                if (example.isEmpty()) example = "${b.id} ${w}x$h own=$own flat f=${flat.spriteScale} clear=${flat.clear} -> small f=${small.spriteScale} clear=${small.clear}"
            }
            if (small.clear) {
                // clear of the SMALL pictures, as asked: the same check as assertClear, with the shares
                val wa = small.wildSprite.share(0.4f, 0.45f); val pa = small.playerSprite.share(0.4f, 0.45f)
                assertFalse("${b.id} ${w}x$h wild card on wild", small.wildCard.hits(wa)); assertFalse("${b.id} ${w}x$h wild card on ours", small.wildCard.hits(pa))
                assertFalse("${b.id} ${w}x$h our card on wild", small.playerCard.hits(wa)); assertFalse("${b.id} ${w}x$h our card on ours", small.playerCard.hits(pa))
                assertFalse("${b.id} ${w}x$h cards", small.wildCard.hits(small.playerCard))
            }
        }
        assertTrue("a small picture frees room somewhere in the catalog ($better)", better > 0)
        println("M2 example: $example ($better combos better)")
    }

    @Test fun aSpecificBlockedPairClearsWithASmallPicture() {
        // beach, 300 x 250 dp: with the flat 85 % the sprites must shrink to .8 for the cards; a 40 x 45 % picture leaves them whole and the cards bigger
        val flat = planArt("beach", 300f, 250f, true, null, null)
        val small = planArt("beach", 300f, 250f, true, ArtShare(0.4f, 0.45f), ArtShare(0.4f, 0.45f))
        assertTrue(flat.clear && small.clear)
        assertTrue("flat f=${flat.spriteScale}", flat.spriteScale < 1f)
        assertEquals(1f, small.spriteScale, 0f)
        assertTrue("k ${flat.k} -> ${small.k}", small.k > flat.k)
    }

    @Test fun aBiggerPictureNeedsMoreRoomThanTheDefault() {
        // a full-frame sprite (1.0 x 1.0) is bigger than the 85 % default: cards that cleared the default may have to move or shrink
        var worse = 0
        for (b in BackdropCatalog.ALL) for (w in gridW) for (h in gridH) {
            val flat = planArt(b.id, w, h, true, null, null)
            val big = planArt(b.id, w, h, true, ArtShare(1f, 1f), ArtShare(1f, 1f))
            if (big.spriteScale < flat.spriteScale - 0.001f || (flat.clear && !big.clear)) worse++
            if (big.clear) {
                val wa = big.wildSprite; val pa = big.playerSprite   // the whole frame is the picture
                assertFalse("${b.id} ${w}x$h", big.wildCard.hits(wa) || big.wildCard.hits(pa) || big.playerCard.hits(wa) || big.playerCard.hits(pa))
            }
        }
        assertTrue("some arenas need the sprites smaller for a full-frame picture ($worse)", worse > 0)
    }

    private fun ArenaGeo.Box.share(aw: Float, ah: Float) = ArenaGeo.Box(x + w * (1f - aw) / 2f, y + h * (1f - ah), w * aw, h * ah)

    // ---------------------------------------------------------------- R8-FIX M3: both cards on top, and a fallback that protects the wild one

    @Test fun bothOnTopIsTriedOnlyFromScaleDotEightAndNeverMovesTheWildCardDown() {
        var top = 0
        var example = ""
        for (b in BackdropCatalog.ALL) for (w in gridW) for (h in gridH) for (own in listOf(true, false)) for (zoom in listOf(1f, 0.72f)) {
            val p = plan(b.id, w, h, own, zoom = zoom)
            assertEquals("${b.id} ${w}x$h: the wild card keeps the top row", 8f, p.wildCard.y, 0.001f)
            if (p.bothTop) {
                top++
                if (example.isEmpty()) example = "${b.id} ${w}x$h own=$own zoom=$zoom f=${p.spriteScale} clear=${p.clear}"
                assertEquals("${b.id} ${w}x$h: ours on the top row too", 8f, p.playerCard.y, 0.001f)
                if (p.clear) {
                    assertTrue("${b.id} ${w}x$h: both on top only from .8 down (f=${p.spriteScale})", p.spriteScale <= 0.8f + 0.001f)
                    assertClear("${b.id} ${w}x$h own=$own", w, h, p)
                    assertTrue("${b.id} ${w}x$h: the two cards keep a gap in their shared row", p.wildCard.right + 6f <= p.playerCard.x + 0.01f || p.playerCard.right + 6f <= p.wildCard.x + 0.01f)
                }
            } else {
                assertEquals("${b.id} ${w}x$h: ours in the bottom row", h - 10f, p.playerCard.bottom, 0.001f)
            }
        }
        assertTrue("both-on-top is used somewhere in the catalog ($top)", top > 0)
        println("M3 both-on-top example: $example ($top plans)")
    }

    @Test fun bothOnTopBeatsShrinkingTheSpritesFurther() {
        // a clear plan below .8 means neither the usual rows nor the top row had a clear pair at .8 (at any card scale)
        var below = 0
        for (b in BackdropCatalog.ALL) for (w in gridW) for (h in gridH) {
            val s = stage(b.id, w, h)
            val p = plan(b.id, w, h, true)
            if (!p.clear || p.spriteScale >= 0.8f - 0.001f) continue
            below++
            var k = 1.2f
            while (true) {
                assertFalse("${b.id} ${w}x$h f=.8 k=$k usual", anyClear(w, h, s, 0.8f, k))
                assertFalse("${b.id} ${w}x$h f=.8 k=$k top", anyClearTop(w, h, s, 0.8f, k))
                if (k <= 1f) break
                k = max(1f, k - 0.05f)
            }
        }
        println("M3 plans below .8: $below")
    }

    // both cards on the top row: the wild card at any of its 4 positions, ours top-right or top-middle
    private fun anyClearTop(w: Float, h: Float, s: Stage, f: Float, k: Float): Boolean {
        val ws = s.wildFrame * f; val ps = s.playerFrame * f
        val wa = ArenaGeo.Box(s.wildFeetX - ws / 2, s.wildFeetY - ws, ws, ws).art()
        val pa = ArenaGeo.Box(s.playerFeetX - ps / 2, s.playerFeetY - ps, ps, ps).art()
        val cw = wcw * k; val ch = wch * k; val qw = pcw * k; val qh = pch * k
        val mid = (pa.right + wa.x) / 2f
        val wx = listOf(wa.x - 6f - cw, 8f, mid - cw / 2f, w - 8f - cw).map { max(8f, min(it, w - 8f - cw)) }
        val px = listOf(w - 8f - qw, mid - qw / 2f).map { max(8f, min(it, w - 8f - qw)) }
        for (x in wx) for (y in px) {
            val wild = ArenaGeo.Box(x, 8f, cw, ch)
            val pl = ArenaGeo.Box(y, 8f, qw, qh)
            val apart = !wild.hits(ArenaGeo.Box(pl.x - 6f, pl.y, pl.w + 12f, pl.h))
            if (inside(w, h - 4f, wild) && inside(w, h, pl) && !wild.hits(wa) && !wild.hits(pa) && !pl.hits(wa) && !pl.hits(pa) && apart) return true
        }
        return false
    }

    // the weighted score the fallback minimises: wild covered 3x, ours 1x, the cards on each other 3x, off the panel 4x
    private fun ov(a: ArenaGeo.Box, b: ArenaGeo.Box): Float {
        val dx = min(a.right, b.right) - max(a.x, b.x); val dy = min(a.bottom, b.bottom) - max(a.y, b.y)
        return if (dx > 0f && dy > 0f) dx * dy else 0f
    }
    private fun off(w: Float, h: Float, b: ArenaGeo.Box) = b.w * b.h - ov(b, ArenaGeo.Box(0f, 0f, w, h))
    private fun weighted(w: Float, h: Float, c: ArenaGeo.Box, d: ArenaGeo.Box, wa: ArenaGeo.Box, pa: ArenaGeo.Box) =
        3f * (ov(c, wa) + ov(d, wa)) + (ov(c, pa) + ov(d, pa)) + 3f * ov(c, d) + 4f * (off(w, h, c) + off(w, h, d))

    /** Every position pair the fallback considers ([own] decides the orders only through StageGeo's own tables, so all 4 x 6 are listed): (wild covered, ours covered, weighted). */
    private fun candidates(w: Float, h: Float, p: StagePlan): List<Triple<Float, Float, Float>> {
        val wa = p.wildSprite.art(); val pa = p.playerSprite.art()
        val mid = (pa.right + wa.x) / 2f
        fun cx(x: Float, cw: Float) = max(8f, min(x, w - 8f - cw))
        val wxs = listOf(wa.x - 6f - wcw, 8f, mid - wcw / 2f, w - 8f - wcw).map { cx(it, wcw) }
        val bottom = listOf(pa.right + 6f, w - 8f - pcw, mid - pcw / 2f, 8f).map { ArenaGeo.Box(cx(it, pcw), h - 10f - pch, pcw, pch) }
        val top = listOf(w - 8f - pcw, mid - pcw / 2f).map { ArenaGeo.Box(cx(it, pcw), 8f, pcw, pch) }
        val out = ArrayList<Triple<Float, Float, Float>>()
        for (x in wxs) {
            val wild = ArenaGeo.Box(x, 8f, wcw, wch)
            for (pl in bottom + top) out += Triple(ov(wild, wa) + ov(pl, wa), ov(wild, pa) + ov(pl, pa), weighted(w, h, wild, pl, wa, pa))
        }
        return out
    }

    @Test fun theFallbackIsTheLeastWeightedOverlapOfEveryPositionPair() {
        var fallbacks = 0
        var protecting = 0   // fallbacks where the weights chose a pair with less on the wild one than the plain sum would have
        for (b in BackdropCatalog.ALL) for (w in listOf(200f, 240f, 300f)) for (h in listOf(110f, 140f, 170f)) for (own in listOf(true, false)) {
            val s = stage(b.id, w, h)
            val p = StageGeo.plan(w, h, s, wcw, wch, pcw, pch, 1f, own)
            if (p.clear) continue
            fallbacks++
            assertEquals(0.7f, p.spriteScale, 0f)
            assertEquals(8f, p.wildCard.y, 0.001f)
            val wa = p.wildSprite.art(); val pa = p.playerSprite.art()
            val all = candidates(w, h, p)
            val chosen = weighted(w, h, p.wildCard, p.playerCard, wa, pa)
            assertEquals("${b.id} ${w}x$h own=$own", all.minOf { it.third }, chosen, 0.5f)
            val chosenWild = ov(p.wildCard, wa) + ov(p.playerCard, wa)
            val plain = all.minBy { it.first + it.second }
            if (chosenWild < plain.first - 0.5f) protecting++
        }
        assertTrue("some panels fall back ($fallbacks)", fallbacks > 0)
        println("M3 fallbacks: $fallbacks, weights protected the wild one in $protecting")
    }
}
