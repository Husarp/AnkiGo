package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaFxView
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import com.husarp.ankigo.ui.HomeView
import com.husarp.ankigo.ui.movefx.FxKit
import com.husarp.ankigo.ui.movefx.FxPalette
import com.husarp.ankigo.ui.movefx.MoveFxEffect
import com.husarp.ankigo.ui.movefx.MoveFxPlan
import com.husarp.ankigo.ui.movefx.MoveFxStage
import com.husarp.ankigo.ui.movefx.MoveLook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * R9-B5: the attacker stays visible ON TOP while it is over the target (sprite or info card), in every host and both directions.
 * The two sprites are made-up solid discs of two colours that nothing else on screen has (magenta = the attacker, lime = the target),
 * so a pixel count says how much of the attacker's disc is still there where the target and the cards are drawn: the attacker alone
 * (everything else hidden) against the whole arena. Mid-approach stills of the real Tackle look (no burst yet) plus the plain lunge.
 */
class R9AttackOnTopShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    private val plan = MoveFxPlan(MoveLook.TACKLE, 1f, FxKit.NORMAL, FxPalette(0xFF6D6B4E.toInt(), 0xFFE8E4C8.toInt(), 0xFFA8A77A.toInt(), 0xFFFFFFFF.toInt()))

    /** What one still measured: pixels of the attacker's disc alone / in the whole arena, and the same inside the target sprite's disc. */
    private class Probe(val label: String, val alone: Int, val whole: Int, val overTarget: Int, val overTargetShown: Int) {
        val fraction get() = if (alone == 0) 0.0 else whole.toDouble() / alone
        override fun toString() = "%s: attacker disc %d px alone, %d px in the arena (%.1f %%); %d px over the target disc, %d of them shown".format(label, alone, whole, fraction * 100, overTarget, overTargetShown)
    }

    private val probes = ArrayList<Probe>()

    @After fun report() {
        if (probes.isEmpty()) return
        val f = File(Shots.outDir, "r9-attack-on-top-report.txt")
        f.appendText(probes.joinToString("\n", postfix = "\n"))
    }

    companion object {
        private const val ATTACKER = 0xFFE000E0.toInt()
        private const val TARGET = 0xFF00E000.toInt()

        /** A solid disc picture of [colour], [size] sprite pixels wide (round, so no square corners hide in a card's rounded edge). */
        fun disc(colour: Int, size: Int = 64): SpriteImage {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val p = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL; this.color = colour }
            Canvas(bmp).drawCircle(size / 2f, size / 2f, size / 2f - 1f, p)
            return SpriteImage(listOf(bmp), intArrayOf(100))
        }

        fun snap(v: View): Bitmap {
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(b))
            return b
        }

        fun mask(b: Bitmap, colour: Int): BooleanArray {
            val px = IntArray(b.width * b.height)
            b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            return BooleanArray(px.size) { px[it] == colour }
        }

        fun holderOf(arena: ArenaView, sprite: SpriteView): View = sprite.parent as View
    }

    // ---------------------------------------------------------------- the stills

    private fun measureWith(arena: ArenaView, byPlayer: Boolean, label: String) {
        val attacker = if (byPlayer) arena.playerSprite else arena.enemySprite
        val target = if (byPlayer) arena.enemySprite else arena.playerSprite
        val aHolder = holderOf(arena, attacker)
        val tHolder = holderOf(arena, target)
        // the frame: one draw so the look poses the sprites (it does that while it draws), then the pictures
        snap(arena)
        val whole = mask(snap(arena), ATTACKER)
        val alone = onlyShown(arena, aHolder) { mask(snap(arena), ATTACKER) }
        val targetAlone = onlyShown(arena, tHolder) { mask(snap(arena), TARGET) }
        var a = 0; var w = 0; var o = 0; var os = 0
        for (i in whole.indices) {
            if (alone[i]) a++
            if (whole[i]) w++
            if (alone[i] && targetAlone[i]) { o++; if (whole[i]) os++ }
        }
        probes += Probe(label, a, w, o, os)
    }

    /** Draws with every child of the arena hidden except [keep] and the canvas effect layers (they sit on top of everything). */
    private fun <T> onlyShown(arena: ArenaView, keep: View, draw: () -> T): T {
        val hidden = ArrayList<View>()
        val clipped = arena.clipChildren
        arena.clipChildren = false   // the reference is the disc as it would look with nothing in its way, not even a box edge
        for (i in 0 until arena.childCount) {
            val c = arena.getChildAt(i)
            if (c !== keep && c !is ArenaFxView && c.visibility == View.VISIBLE) { c.visibility = View.INVISIBLE; hidden += c }
        }
        try { return draw() } finally { hidden.forEach { it.visibility = View.VISIBLE }; arena.clipChildren = clipped }
    }

    private fun stillAfterLayout(arena: ArenaView, byPlayer: Boolean, label: String, atMs: Float = 290f, lunge: Boolean = false) {
        var done = false
        arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!done && v.width > 0 && v.height > 0) {
                done = true
                arena.setEnemyShown(true)
                arena.playerSprite.setImage(disc(if (byPlayer) ATTACKER else TARGET))
                arena.enemySprite.setImage(disc(if (byPlayer) TARGET else ATTACKER))
                relayout(arena)
                if (lunge) {   // the plain lunge (reduced motion / move animations off), frozen half way
                    arena.lunge(byPlayer, 1000)
                    for (a in running(arena)) (a as android.animation.ValueAnimator).currentPlayTime = 500
                } else arena.moveStill(plan, byPlayer, atMs)
                relayout(arena)
                measureWith(arena, byPlayer, label)
            }
        }
    }

    private fun relayout(arena: ArenaView) {
        arena.measure(View.MeasureSpec.makeMeasureSpec(arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(arena.height, View.MeasureSpec.EXACTLY))
        arena.layout(arena.left, arena.top, arena.right, arena.bottom)
    }

    private fun full(ctx: Context, byPlayer: Boolean, view: HomeView, label: String, lunge: Boolean = false): View {
        val b = BattleShotKit.bound(ctx, view)
        stillAfterLayout(b.arena!!, byPlayer, label, lunge = lunge)
        return b.root
    }

    private fun small(ctx: Context, byPlayer: Boolean, label: String): View {
        val p = BattlePanelView(ctx)
        p.setCompact(true); p.renderAll()
        stillAfterLayout(p.arena, byPlayer, label)
        return p
    }

    private fun pip(ctx: Context, byPlayer: Boolean, label: String, lunge: Boolean = false): View {
        val b = BattleShotKit.bound(ctx)
        BattleShotKit.pip(b)
        stillAfterLayout(b.arena!!, byPlayer, label, lunge = lunge)
        return b.root
    }

    @Suppress("UNCHECKED_CAST")
    private fun running(arena: ArenaView): Set<android.animation.Animator> =
        ArenaView::class.java.getDeclaredField("live").apply { isAccessible = true }.get(arena) as Set<android.animation.Animator>

    private fun shoot(name: String, size: Size, build: (Context) -> View) = paparazzi.shootWith(name, listOf(size), build = build)

    private fun check() {
        assertTrue("no still was measured", probes.isNotEmpty())
        for (p in probes) {
            println(p)
            assertTrue("$p: the attacker's disc must be there (alone)", p.alone > 0)
            assertTrue("$p: the attacker must stay visible", p.fraction >= 0.97)
        }
    }

    @Test fun phoneOurs() { shoot("r9-b5-phone-ours", Size.PHONE) { full(it, true, HomeView.BATTLE, "phone ours") }; check() }
    @Test fun phoneWild() { shoot("r9-b5-phone-wild", Size.PHONE) { full(it, false, HomeView.BATTLE, "phone wild") }; check() }
    @Test fun studyOurs() { shoot("r9-b5-study-ours", Size.SPLIT) { full(it, true, HomeView.STUDY, "study ours") }; check() }
    @Test fun studyWild() { shoot("r9-b5-study-wild", Size.SPLIT) { full(it, false, HomeView.STUDY, "study wild") }; check() }
    @Test fun floatOurs() { shoot("r9-b5-float-ours", Size.FLOATBATTLE) { small(it, true, "float ours") }; check() }
    @Test fun floatWild() { shoot("r9-b5-float-wild", Size.FLOATBATTLE) { small(it, false, "float wild") }; check() }
    @Test fun pipOurs() { shoot("r9-b5-pip-ours", Size.PIP) { pip(it, true, "pip ours") }; check() }
    @Test fun pipWild() { shoot("r9-b5-pip-wild", Size.PIP) { pip(it, false, "pip wild") }; check() }

    // the plain lunge, half way
    @Test fun lungePhoneOurs() { shoot("r9-b5-lunge-phone-ours", Size.PHONE) { full(it, true, HomeView.BATTLE, "lunge phone ours", lunge = true) }; check() }
    @Test fun lungePhoneWild() { shoot("r9-b5-lunge-phone-wild", Size.PHONE) { full(it, false, HomeView.BATTLE, "lunge phone wild", lunge = true) }; check() }
    @Test fun lungePipWild() { shoot("r9-b5-lunge-pip-wild", Size.PIP) { pip(it, false, "lunge pip wild", lunge = true) }; check() }

    // ---------------------------------------------------------------- the lift is only for the attack, and it always ends

    private class Look(val stage: MoveFxStage) : MoveFxEffect {
        override val impactMs = 300L
        override val doneMs = 900L
        override fun draw(canvas: Canvas, t: Float) = true
        override fun end() { stage.restore() }
    }

    private fun ctx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        AppPrefs.setAttackAnimations(c, true)
        return c
    }

    private fun measure(v: View, wDp: Int, hDp: Int) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private fun rig(look: Boolean = true): ArenaView {
        val panel = BattlePanelView(ctx())
        measure(panel, 412, 869)
        val arena = panel.arena
        arena.setEnemyShown(true)
        arena.enemySprite.setPlaceholder("W", 0xFFB05050.toInt())
        arena.playerSprite.setPlaceholder("P", 0xFF5070B0.toInt())
        measure(panel, 412, 869)
        arena.moveOnScreen = { true }
        arena.moveMaker = { stage, _, _, _ -> if (look) Look(stage) else null }
        return arena
    }

    private fun holder(arena: ArenaView, player: Boolean) = (if (player) arena.playerSprite else arena.enemySprite).parent as View
    private fun moveLayer(arena: ArenaView) = (0 until arena.childCount).map { arena.getChildAt(it) }.filterIsInstance<ArenaFxView>().first { it !== arena.fx }
    private fun dp(arena: ArenaView, v: Float) = v * arena.resources.displayMetrics.density

    private fun assertAtRest(arena: ArenaView) {
        assertEquals(0f, holder(arena, true).translationZ, 0f)
        assertEquals(0f, holder(arena, false).translationZ, 0f)
        assertEquals(0f, moveLayer(arena).translationZ, 0f)
        assertTrue("the arena clips its children again", arena.clipChildren)
    }

    @Test fun aMoveLiftsTheAttackerOverTheCardsAndReleaseGivesTheOrderBack() {
        for (byPlayer in listOf(true, false)) {
            val arena = rig()
            assertAtRest(arena)
            assertNotNull(arena.playMove(plan, byPlayer, true, 1f))
            val up = holder(arena, byPlayer)
            assertTrue("over the cards' 2 dp elevation", up.translationZ > dp(arena, 2f))
            assertTrue("under the status bubble's 8 dp", up.translationZ < dp(arena, 8f))
            assertEquals("the target's box stays where it was", 0f, holder(arena, !byPlayer).translationZ, 0f)
            assertTrue("the particles stay over the attacker", moveLayer(arena).translationZ > up.translationZ)
            assertTrue("nothing is cut at its own box while it attacks", !arena.clipChildren)
            arena.releaseMove()
            assertAtRest(arena)
        }
    }

    @Test fun theLookEndingByItselfOrBeingClearedPutsTheOrderBack() {
        val arena = rig()
        arena.playMove(plan, true, true, 1f)
        arena.cancelAnimations()   // a battle reset, the panel going away
        assertAtRest(arena)
        arena.playMove(plan, false, true, 1f)
        arena.resetFx()
        assertAtRest(arena)
    }

    @Test fun aMoveWithNoLookLeavesNothingLifted() {
        val arena = rig(look = false)
        assertEquals(null, arena.playMove(plan, true, true, 1f))
        assertAtRest(arena)
    }

    @Test fun theNextAttackFromTheOtherSideTakesTheLiftOver() {
        val arena = rig()
        arena.playMove(plan, true, true, 1f)
        arena.playMove(plan, false, true, 1f)
        assertEquals(0f, holder(arena, true).translationZ, 0f)
        assertTrue(holder(arena, false).translationZ > dp(arena, 2f))
        assertTrue(!arena.clipChildren)
        arena.releaseMove()
        assertAtRest(arena)
    }

    @Test fun thePlainLungeLiftsToo() {
        for (byPlayer in listOf(true, false)) {
            val arena = rig()
            arena.lunge(byPlayer, 400)
            assertTrue(holder(arena, byPlayer).translationZ > dp(arena, 2f))
            assertEquals("no particles in a plain lunge", 0f, moveLayer(arena).translationZ, 0f)
            assertTrue(!arena.clipChildren)
            arena.cancelAnimations()   // the end action of the animator runs on a cancel too
            assertAtRest(arena)
        }
    }

    @Test fun resetPutsTheOrderBackToo() {
        val arena = rig()
        arena.lunge(true, 400)
        arena.reset(true)
        assertAtRest(arena)
    }

    @Test fun anOlderLungeEndingLateDoesNotDropANewerMove() {
        val arena = rig()
        arena.lunge(true, 400)
        arena.playMove(plan, false, true, 1f)
        arena.cancelAnimations().also { assertAtRest(arena) }   // both over
        arena.lunge(true, 400)
        arena.playMove(plan, false, true, 1f)                    // the lunge's end action fires only on its cancel, below
        for (a in running(arena).toList()) a.cancel()
        assertTrue("the move still holds the lift after the lunge ended", holder(arena, false).translationZ > dp(arena, 2f))
        arena.releaseMove()
        assertAtRest(arena)
    }

    /** Codex 1: the same side's lunge, then its move before the lunge ends: the lunge's end must leave the move's lift (Z + no clip). */
    @Test fun aLungeEndingLateLeavesALaterMoveOfTheSameSideLifted() {
        for (byPlayer in listOf(true, false)) {
            val arena = rig()
            arena.lunge(byPlayer, 400)
            assertNotNull(arena.playMove(plan, byPlayer, true, 1f))
            for (a in running(arena).toList()) a.cancel()   // the lunge's end action runs now
            assertTrue("the move keeps its Z", holder(arena, byPlayer).translationZ > dp(arena, 2f))
            assertTrue("the move keeps the clip off", !arena.clipChildren)
            assertTrue("the move keeps its particles above", moveLayer(arena).translationZ > holder(arena, byPlayer).translationZ)
            arena.releaseMove()   // the move's own release drops it
            assertAtRest(arena)
        }
    }

    /** The other way round: an older move's stage let go late must not drop a later lunge of the same side either. */
    @Test fun anOlderMoveReleasedLateLeavesALaterLungeLifted() {
        val arena = rig()
        arena.playMove(plan, true, true, 1f)
        val first = arena.playMove(plan, true, true, 1f)   // takes over, the first one is still "live" in its effect
        arena.lunge(true, 400)
        (first as Look).stage.restore()                    // a stage of an attack that was replaced ends late
        assertTrue("the lunge keeps the lift", holder(arena, true).translationZ > dp(arena, 2f))
        assertTrue(!arena.clipChildren)
        arena.cancelAnimations()
        assertAtRest(arena)
    }

    /** Codex 5: only a travelling look lifts anything; the others keep the R7 order (particles under the cards), the clip is off. */
    @Test fun aLookThatDoesNotTravelKeepsTheParticlesUnderTheCards() {
        val others = MoveLook.values().filter { it != MoveLook.TACKLE && it != MoveLook.HIT_TEMPLATE }
        assertTrue(others.size >= 8)
        for (look in others) for (byPlayer in listOf(true, false)) {
            val arena = rig()
            assertNotNull(arena.playMove(plan.copy(look = look), byPlayer, true, 1f))
            assertEquals("$look: holder at 0", 0f, holder(arena, byPlayer).translationZ, 0f)
            assertEquals("$look: target at 0", 0f, holder(arena, !byPlayer).translationZ, 0f)
            assertEquals("$look: particles below the cards", 0f, moveLayer(arena).translationZ, 0f)
            assertTrue("$look: the poses may leave the box, the clip is off", !arena.clipChildren)
            arena.releaseMove()
            assertAtRest(arena)
        }
    }

    @Test fun theTravellingLooksAreTackleAndTheHitTemplate() {
        for (look in MoveLook.values()) {
            val expect = look == MoveLook.TACKLE || look == MoveLook.HIT_TEMPLATE
            assertEquals("$look", expect, ArenaView.travelsOnto(look))
        }
        val arena = rig()
        arena.playMove(plan.copy(look = MoveLook.HIT_TEMPLATE), true, true, 1f)
        assertTrue(holder(arena, true).translationZ > dp(arena, 2f))
        assertTrue(moveLayer(arena).translationZ > holder(arena, true).translationZ)
        arena.releaseMove()
        assertAtRest(arena)
    }

    @Test fun theFloatingNumberOfAHitIsOverTheAttacker() {
        val arena = rig()
        arena.floatNumber(false, "-4", com.husarp.ankigo.ui.FloatStyle.DAMAGE)
        val before = (0 until arena.childCount).map { arena.getChildAt(it) }.filter { it.javaClass.simpleName == "FloatTextView" }
        assertEquals(1, before.size)
        assertEquals("at rest it stays an ordinary sibling", 0f, before[0].translationZ, 0f)
        arena.playMove(plan, true, true, 1f)
        arena.floatNumber(false, "-9", com.husarp.ankigo.ui.FloatStyle.DAMAGE)
        val now = (0 until arena.childCount).map { arena.getChildAt(it) }.filter { it.javaClass.simpleName == "FloatTextView" }
        assertEquals(2, now.size)
        val up = holder(arena, true).translationZ
        assertTrue("over the attacker and its particles", now.last().translationZ > moveLayer(arena).translationZ && moveLayer(arena).translationZ > up)
    }

    @Test fun aTapOnTheLiftedAttackerStillReachesIt() {
        val arena = rig()
        arena.playMove(plan, true, true, 1f)
        val h = holder(arena, true)
        val x = h.left + h.width / 2f
        val y = h.top + h.height / 2f
        val down = android.view.MotionEvent.obtain(0L, 0L, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        assertTrue("the holder takes the touch (it is clickable)", arena.dispatchTouchEvent(down))
        down.recycle()
        arena.releaseMove()
    }
}
