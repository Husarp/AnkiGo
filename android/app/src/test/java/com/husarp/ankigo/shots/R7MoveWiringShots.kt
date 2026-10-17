package com.husarp.ankigo.shots

import android.animation.Animator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.ArenaMoveStage
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import com.husarp.ankigo.ui.FxEffect
import com.husarp.ankigo.ui.HomeView
import com.husarp.ankigo.ui.movefx.FxKit
import com.husarp.ankigo.ui.movefx.FxPalette
import com.husarp.ankigo.ui.movefx.MoveFxEffect
import com.husarp.ankigo.ui.movefx.MoveFxPlan
import com.husarp.ankigo.ui.movefx.MoveFxStage
import com.husarp.ankigo.ui.movefx.MoveLook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R7-05: the attack animation wiring in the REAL [ArenaView] / [EventPlayer]: a fake look draws the stage's geometry (centres, unit)
 * and poses the target through the stage, so the shots show where a look would draw; plus the rules of when the old lunge and hit
 * still play. The real look (MoveFx.create, R7-03) draws its own picture in the shots named `...-real`.
 */
class R7MoveWiringShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    private val plan = MoveFxPlan(MoveLook.TACKLE, 1f, FxKit.NORMAL, FxPalette(0xFF6D6B4E.toInt(), 0xFFE8E4C8.toInt(), 0xFFA8A77A.toInt(), 0xFFFFFFFF.toInt()))

    /** Draws the stage: a cross at each centre, the line between them, a circle of 40 units, and the target squashed and tinted. */
    private class GeometryFx(val stage: MoveFxStage, override val impactMs: Long = 400, override val doneMs: Long = 900) : MoveFxEffect {
        var ended = 0
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f; style = Paint.Style.STROKE; color = Color.MAGENTA }
        init {
            stage.pose(false, dx = 0f, dy = 0f, sx = 1.15f, sy = 0.85f, matrix = floatArrayOf(1f, 0f, 0f, 0f, 60f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        }
        override fun draw(canvas: Canvas, t: Float): Boolean {
            val r = 10f
            for ((x, y) in listOf(stage.attackerX to stage.attackerY, stage.targetX to stage.targetY)) {
                canvas.drawLine(x - r, y, x + r, y, line); canvas.drawLine(x, y - r, x, y + r, line)
                canvas.drawCircle(x, y, 40f * stage.unit, line)
            }
            canvas.drawLine(stage.attackerX, stage.attackerY, stage.targetX, stage.targetY, line)
            return true
        }
        override fun end() { ended++; stage.restore() }
    }

    private fun fake(arena: ArenaView): MutableList<GeometryFx> {
        val made = ArrayList<GeometryFx>()
        arena.moveOnScreen = { true }
        arena.moveMaker = { stage, _, _, _ -> GeometryFx(stage).also { made += it } }
        return made
    }

    private fun placeholders(arena: ArenaView) {
        // sprites with no picture yet: a made-up round placeholder is drawn, which has a size to centre on
        arena.enemySprite.setPlaceholder("W", 0xFFB05050.toInt())
        arena.playerSprite.setPlaceholder("P", 0xFF5070B0.toInt())
    }

    private fun stillAfterLayout(arena: ArenaView, byPlayer: Boolean, real: Boolean) {
        var done = false
        arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!done && v.width > 0 && v.height > 0) {
                done = true
                if (!real) fake(arena)
                arena.setEnemyShown(true)
                arena.moveStill(plan, byPlayer, 450f)
                arena.measure(View.MeasureSpec.makeMeasureSpec(arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(arena.height, View.MeasureSpec.EXACTLY))
                arena.layout(arena.left, arena.top, arena.right, arena.bottom)
            }
        }
    }

    private fun full(ctx: Context, byPlayer: Boolean, real: Boolean, view: HomeView = HomeView.BATTLE): View {
        val b = BattleShotKit.bound(ctx, view)
        stillAfterLayout(b.arena!!, byPlayer, real)
        return b.root
    }

    private fun small(ctx: Context, byPlayer: Boolean, real: Boolean): View {
        val p = BattlePanelView(ctx)
        p.setCompact(true); p.renderAll()
        stillAfterLayout(p.arena, byPlayer, real)
        return p
    }

    private fun pip(ctx: Context, byPlayer: Boolean, real: Boolean): View {
        val b = BattleShotKit.bound(ctx)
        BattleShotKit.pip(b)
        stillAfterLayout(b.arena!!, byPlayer, real)
        return b.root
    }

    @Test fun phoneOurs() = paparazzi.shootWith("r7-05-phone-ours", listOf(Size.PHONE)) { full(it, true, false) }
    @Test fun phoneWild() = paparazzi.shootWith("r7-05-phone-wild", listOf(Size.PHONE)) { full(it, false, false) }
    @Test fun splitOurs() = paparazzi.shootWith("r7-05-split-ours", listOf(Size.SPLIT)) { full(it, true, false, HomeView.STUDY) }
    @Test fun splitWild() = paparazzi.shootWith("r7-05-split-wild", listOf(Size.SPLIT)) { full(it, false, false, HomeView.STUDY) }
    @Test fun floatOurs() = paparazzi.shootWith("r7-05-float-ours", listOf(Size.FLOATBATTLE)) { small(it, true, false) }
    @Test fun floatWild() = paparazzi.shootWith("r7-05-float-wild", listOf(Size.FLOATBATTLE)) { small(it, false, false) }
    @Test fun pipOurs() = paparazzi.shootWith("r7-05-pip-ours", listOf(Size.PIP)) { pip(it, true, false) }
    @Test fun pipWild() = paparazzi.shootWith("r7-05-pip-wild", listOf(Size.PIP)) { pip(it, false, false) }

    // the real look, whatever MoveFx.create draws by now (nothing while it is the stub)
    @Test fun phoneRealOurs() = paparazzi.shootWith("r7-05-phone-real-ours", listOf(Size.PHONE)) { full(it, true, true) }
    @Test fun phoneRealWild() = paparazzi.shootWith("r7-05-phone-real-wild", listOf(Size.PHONE)) { full(it, false, true) }

    // ---------------------------------------------------------------- when the old lunge and hit still play

    private fun ctx(reduced: Boolean = false, setting: Boolean = true): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", reduced).commit()
        AppPrefs.setAttackAnimations(c, setting)
        return c
    }

    private fun measure(v: View, wDp: Int, hDp: Int) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    @Suppress("UNCHECKED_CAST")
    private fun running(arena: ArenaView): Set<Animator> =
        ArenaView::class.java.getDeclaredField("live").apply { isAccessible = true }.get(arena) as Set<Animator>

    private fun attack(missed: Boolean = false, damage: Int = 12, moveId: String = "tackle") = GameEvent.Attack(
        true, "Mainy", "Wildling", "Tackle", Type.NORMAL, Category.PHYSICAL, damage, 1, 1.0, false, missed, false, 20, 30, 40, 40, moveId, 40)

    private class Rig(val c: Context) {
        val panel = BattlePanelView(c)
        val arena get() = panel.arena
        val player = EventPlayer(panel)
        init {
            panel.setLog(com.husarp.ankigo.ui.LogKind.SYS, "X", "x", fresh = true)
        }
    }

    private fun rig(c: Context): Pair<Rig, MutableList<GeometryFx>> {
        val r = Rig(c)
        measure(r.panel, 412, 869)
        r.arena.setEnemyShown(true)
        placeholders(r.arena)
        measure(r.panel, 412, 869)
        return r to fake(r.arena)
    }

    @Test fun withTheSettingOnAHitPlaysTheMoveAndNotTheLunge() {
        val (r, made) = rig(ctx())
        r.player.enqueue(listOf(attack()))
        assertEquals("the move animation was made once", 1, made.size)
        assertTrue("no lunge or hit animator of the plain path", running(r.arena).isEmpty())
    }

    @Test fun withTheSettingOffTheOldLungePlays() {
        val (r, made) = rig(ctx(setting = false))
        r.player.enqueue(listOf(attack()))
        assertEquals(0, made.size)
        assertFalse("the lunge runs", running(r.arena).isEmpty())
    }

    @Test fun aMissedOrFailedMoveKeepsTheOldPath() {
        val (r, made) = rig(ctx())
        r.player.enqueue(listOf(attack(missed = true)))
        assertEquals(0, made.size)
        assertFalse(running(r.arena).isEmpty())
    }

    @Test fun reducedMotionPlaysNothingAndMakesNoLook() {
        val (r, made) = rig(ctx(reduced = true))
        r.player.enqueue(listOf(attack()))
        assertEquals(0, made.size)
        assertNull(r.arena.playMove(plan, true, true, 1f))
    }

    @Test fun noPlaceOnScreenFallsBackToTheOldPath() {
        val (r, made) = rig(ctx())
        r.arena.moveOnScreen = { false }
        r.player.enqueue(listOf(attack()))
        assertEquals(0, made.size)
        assertFalse(running(r.arena).isEmpty())
    }

    @Test fun aHiddenWildSideOrEmptyBoxMakesNoMove() {
        val (r, made) = rig(ctx())
        r.arena.setEnemyShown(false)
        assertNull(r.arena.playMove(plan, true, true, 1f))
        r.arena.setEnemyShown(true)
        assertNotNull(r.arena.playMove(plan, true, true, 1f))
        assertEquals(1, made.size)
    }

    @Test fun aNewMoveEndsTheRunningOneAndClearingRestoresTheSprites() {
        val (r, made) = rig(ctx())
        val first = r.arena.playMove(plan, true, true, 1f)
        assertNotNull(first)
        assertEquals("the look posed the target", 1.15f, r.arena.enemySprite.scaleX, 0f)
        assertNotNull(r.arena.enemySprite.fxMatrix)
        r.arena.playMove(plan, false, true, 1f)
        assertEquals("the first one was ended", 1, made[0].ended)
        r.arena.cancelAnimations()
        assertEquals(1, made[1].ended)
        assertEquals(1f, r.arena.enemySprite.scaleX, 0f)
        assertEquals(1f, r.arena.enemySprite.scaleY, 0f)
        assertEquals(0f, r.arena.enemySprite.translationX, 0f)
        assertNull(r.arena.enemySprite.fxMatrix)
    }

    @Test fun clearingThePlayerEndsTheMoveToo() {
        val (r, made) = rig(ctx())
        r.player.enqueue(listOf(attack()))
        r.player.clear()
        assertEquals(1, made[0].ended)
        assertNull(r.arena.enemySprite.fxMatrix)
    }

    // ---------------------------------------------------------------- R7-FIX 1: the sprites belong to the next event after the hand-back

    @Test fun aReleasedMoveNeverPosesOrResetsTheSpritesAgain() {
        val (r, made) = rig(ctx())
        assertNotNull(r.arena.playMove(plan, true, true, 1f))
        val stage = made[0].stage as ArenaMoveStage
        assertEquals("the look posed the target", 1.15f, r.arena.enemySprite.scaleX, 0f)
        assertFalse(stage.released)
        r.arena.releaseMove()
        assertTrue(stage.released)
        assertEquals("handed back at rest", 1f, r.arena.enemySprite.scaleX, 0f)
        assertNull(r.arena.enemySprite.fxMatrix)
        // the next event's own animation (a faint sinks the sprite) now owns it; late frames and the late end of the effect leave it be
        r.arena.enemySprite.translationY = 55f
        r.arena.enemySprite.alpha = .4f
        stage.pose(false, dx = 3f, dy = 7f, sx = 1.3f, sy = .7f, matrix = FloatArray(20))
        stage.jolt(5f)
        stage.restore()
        made[0].end()
        r.arena.cancelAnimations()
        assertEquals(55f, r.arena.enemySprite.translationY, 0f)
        assertEquals(0f, r.arena.enemySprite.translationX, 0f)
        assertEquals(1f, r.arena.enemySprite.scaleX, 0f)
        assertEquals(1f, r.arena.enemySprite.scaleY, 0f)
        assertNull(r.arena.enemySprite.fxMatrix)
    }

    @Test fun releasingWhenNoMoveRanIsHarmless() {
        val (r, _) = rig(ctx())
        r.arena.enemySprite.translationY = 20f
        r.arena.releaseMove()
        r.arena.releaseMove()
        assertEquals(20f, r.arena.enemySprite.translationY, 0f)
    }

    @Test fun aNewMoveGetsAFreshStageEvenAfterTheLastWasReleased() {
        val (r, made) = rig(ctx())
        r.arena.playMove(plan, true, true, 1f)
        r.arena.releaseMove()
        assertNotNull(r.arena.playMove(plan, true, true, 1f))
        assertFalse((made[1].stage as ArenaMoveStage).released)
        assertEquals("the new look poses the target again", 1.15f, r.arena.enemySprite.scaleX, 0f)
    }

    @Test fun theEventPlayerReleasesTheMoveBeforeItStartsTheNextEvent() {
        val c = ctx()
        val (r, made) = rig(c)
        r.player.enqueue(listOf(attack(), GameEvent.Fainted(false, "Wildling")))
        assertEquals(1, made.size)
        val stage = made[0].stage as ArenaMoveStage
        assertFalse("the move runs: the queue is waiting for it", stage.released)
        // the queue moves on after the wait; the effect itself (this fake never lets go on its own) is still "running"
        paparazzi.snapshot(android.widget.FrameLayout(c), "r7fix-flush", 3000)
        assertTrue("released synchronously when the queue moved on", stage.released)
        // the faint is on its way (it sinks the sprite); the effect's late end must not pull the sprite back up
        r.arena.enemySprite.translationY = 33f
        made[0].end()
        assertEquals(33f, r.arena.enemySprite.translationY, 0f)
    }

    @Test fun theMatrixTintsTheDrawnSprite() {
        val c = ctx()
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.rgb(100, 100, 100)) }
        val sv = SpriteView(c)
        sv.setImage(SpriteImage(listOf(bmp), intArrayOf(100)))
        sv.layout(0, 0, 8, 8)
        fun centre(): Int {
            val out = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            sv.draw(Canvas(out))
            return out.getPixel(4, 4)
        }
        assertEquals(100, Color.red(centre()))
        // red +60, green and blue zeroed
        sv.fxMatrix = floatArrayOf(1f, 0f, 0f, 0f, 60f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        val p = centre()
        assertEquals(160, Color.red(p)); assertEquals(0, Color.green(p)); assertEquals(0, Color.blue(p))
        sv.fxMatrix = null
        assertEquals(100, Color.red(centre()))
    }
}
