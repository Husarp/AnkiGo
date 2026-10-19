package com.husarp.ankigo.shots

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.ui.ActionBarView
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BarSide
import com.husarp.ankigo.ui.BattleCard
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.DotsPlace
import com.husarp.ankigo.ui.HalfState
import com.husarp.ankigo.ui.LogKind
import com.husarp.ankigo.ui.MoveBubbleLook
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R9 D3: the battle action bar (my last move | dots | their last move) with the details bubble, in every host that shows the log
 * slot: the Battle tab (phone, landscape), the Study panel (split, short strip), the Pokémon tab's battle card, the floating battle
 * window and picture-in-picture; dots in the middle and at the right end; nothing played yet; a line about the whole battle; the
 * bubble for a super-effective crit that burns (mine), a super-effective hit (theirs), a miss, an immune target and a status move
 * with a stat change. PNGs r9ab-*, light + dark. Plus the bubble's open / close rules by touch.
 */
class R9ActionBarShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    // ---------------------------------------------------------------- events

    private fun atk(
        byPlayer: Boolean, move: String, type: Type?, cat: Category, damage: Int, eff: Double = 1.0, crit: Boolean = false,
        missed: Boolean = false, failed: Boolean = false, hits: Int = 1,
    ) = GameEvent.Attack(
        byPlayer, if (byPlayer) "Sparkmouse" else "Gnarlpup", if (byPlayer) "Gnarlpup" else "Sparkmouse", move, type, cat, damage, hits, eff, crit,
        missed, failed, 10, 30, 20, 30,
    )

    private fun fx(onPlayer: Boolean, kind: EffectKind, text: String = "x", status: String? = null, stat: String? = null, stages: Int = 0) =
        GameEvent.Effect(onPlayer, text, 10, 30, status, kind, stat, stages)

    private fun encounter() = GameEvent.Encounter(1, "Gnarlpup", 5, false, Tier.NORMAL, 30, 30, false, false)

    private fun turn(p: BattlePanelView, vararg events: GameEvent) = events.forEach { p.observeStudyEvent(it) }

    /** Both sides have moved: mine super effective + crit that burns, theirs a plain hit. */
    private fun moves(p: BattlePanelView) = turn(p,
        atk(true, "Thunderbolt", Type.ELECTRIC, Category.SPECIAL, 24, eff = 2.0, crit = true),
        fx(false, EffectKind.STATUS_APPLIED, "Gnarlpup was burned!", status = "brn"),
        atk(false, "Bite", Type.DARK, Category.PHYSICAL, 9),
    )

    // ---------------------------------------------------------------- hosts

    private fun bar(p: BattlePanelView): ActionBarView = BattlePanelView::class.java.getDeclaredField("logBox").apply { isAccessible = true }.get(p) as ActionBarView

    private fun battleTab(ctx: Context, setup: (BattlePanelView) -> Unit): View {
        val b = BattleShotKit.bound(ctx)
        setup(b.panel!!)
        return b.root
    }

    private fun pip(ctx: Context, setup: (BattlePanelView) -> Unit): View {
        val b = BattleShotKit.bound(ctx)
        BattleShotKit.pip(b)
        setup(b.panel!!)
        return b.root
    }

    private fun small(ctx: Context, setup: (BattlePanelView) -> Unit): View {
        val p = BattlePanelView(ctx)
        p.setCompact(true); p.renderAll()
        setup(p)
        return p
    }

    private fun study(ctx: Context, heightMode: Int, setup: (BattlePanelView) -> Unit): View {
        val act = attach(com.husarp.ankigo.StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        setField(act, "heightMode", heightMode)
        val root = act.buildContent()
        setField(act, "card", com.husarp.ankigo.Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>")
        setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic")
        setField(act, "cardName", "Card 1")
        setField(act, "flipped", false)
        setField(act, "locked", false)
        (getField(act, "subtitle") as android.widget.TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as BattlePanelView
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        setup(panel)
        return root
    }

    /** The Study panel by itself (what StudyActivity puts in its panel slot): tall = the 52 dp bar, strip = the short edge-to-edge one. */
    private fun studyPanel(ctx: Context, strip: Boolean, setup: (BattlePanelView) -> Unit): View {
        val p = BattlePanelView(ctx)
        p.setStudyLayout(strip, false); p.renderAll(); p.restoreStudyCharge()
        setup(p)
        return p
    }

    private class FakeLive : BattleCard.Live {
        override var paused = false
        override fun hold(ctx: Context, owner: Any) {}
        override fun release(ctx: Context, owner: Any) {}
        override fun addSink(s: LiveSession.Sink) {}
        override fun removeSink(s: LiveSession.Sink) {}
        override fun addPauseListener(l: (Boolean) -> Unit) {}
        override fun removePauseListener(l: (Boolean) -> Unit) {}
    }

    private class CardHost : BattleCard.Host {
        override fun expand() {}
        override fun popOut() {}
    }

    private fun card(ctx: Context, setup: (BattlePanelView) -> Unit): View {
        val c = BattleCard(ctx, CardHost(), FakeLive())
        c.start()
        val panel = BattleCard::class.java.getDeclaredField("panel").apply { isAccessible = true }.get(c) as BattlePanelView
        setup(panel)
        return FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), 0)
            addView(c.view, FrameLayout.LayoutParams(-1, -2))
        }
    }

    private fun quiet(ctx: Context) { ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit() }

    private fun shoot(name: String, size: Size, dots: DotsPlace = DotsPlace.MIDDLE, build: (Context) -> View) =
        paparazzi.shootWith(name, listOf(size)) { ctx -> quiet(ctx); AppPrefs.setTurnDots(ctx, dots); build(ctx) }

    // ---------------------------------------------------------------- the bar in each host

    @Test fun battleTabPhone() = shoot("r9ab-battle", Size.PHONE) { battleTab(it, ::moves) }
    @Test fun battleTabLand() = shoot("r9ab-battle", Size.LAND) { battleTab(it, ::moves) }
    @Test fun studySplitDotsMiddle() = shoot("r9ab-study-mid", Size.SPLIT) { study(it, 0) { p -> moves(p); turn(p, GameEvent.Counted(3, 1, 1, 1, false)) } }
    @Test fun studySplitDotsRight() = shoot("r9ab-study-right", Size.SPLIT, DotsPlace.RIGHT_END) { study(it, 0) { p -> moves(p); turn(p, GameEvent.Counted(3, 1, 1, 1, false)) } }
    @Test fun studyPanelTallDotsMiddle() = shoot("r9ab-panel-mid", Size.SPLIT) { studyPanel(it, false) { p -> moves(p); turn(p, GameEvent.Counted(3, 1, 1, 1, false)) } }
    @Test fun studyPanelTallDotsRight() = shoot("r9ab-panel-right", Size.SPLIT, DotsPlace.RIGHT_END) { studyPanel(it, false) { p -> moves(p); turn(p, GameEvent.Counted(3, 1, 1, 1, false)) } }
    @Test fun studyStripDotsMiddle() = shoot("r9ab-strip-mid", Size.PIP_169M) { studyPanel(it, true) { p -> moves(p); turn(p, GameEvent.Counted(2, 1, 1, 1, false)) } }
    @Test fun studyStripDotsRight() = shoot("r9ab-strip-right", Size.PIP_169M, DotsPlace.RIGHT_END) { studyPanel(it, true) { p -> moves(p); turn(p, GameEvent.Counted(2, 1, 1, 1, false)) } }
    @Test fun studyStripNoMoveYet() = shoot("r9ab-strip-nomove", Size.PIP_169M) { studyPanel(it, true) { p -> turn(p, encounter()) } }
    @Test fun teamCard() = shoot("r9ab-card", Size.PHONE) { card(it, ::moves) }
    @Test fun floatBattle() = shoot("r9ab-float", Size.FLOATBATTLE) { small(it, ::moves) }
    @Test fun pipWindow() = shoot("r9ab-pip", Size.PIP) { pip(it, ::moves) }

    @Test fun noMoveYet() {
        shoot("r9ab-nomove-battle", Size.PHONE) { battleTab(it) { p -> turn(p, encounter()) } }
        shoot("r9ab-nomove-study", Size.SPLIT) { study(it, 0) { p -> turn(p, encounter()) } }
    }

    @Test fun oneSideHasMoved() = shoot("r9ab-oneside", Size.PHONE) { battleTab(it) { p ->
        turn(p, encounter(), atk(false, "Bite", Type.DARK, Category.PHYSICAL, 9), fx(true, EffectKind.CANT_MOVE, "Sparkmouse is fully paralyzed!"))
    } }

    @Test fun missedAndLongNames() = shoot("r9ab-missed", Size.PHONE) { battleTab(it) { p ->
        turn(p, atk(true, "Extremely Long Move Name", Type.WATER, Category.SPECIAL, 0, missed = true), atk(false, "Dragon Dance", Type.DRAGON, Category.STATUS, 0))
    } }

    @Test fun aLineAboutTheWholeBattle() {
        shoot("r9ab-line-battle", Size.PHONE) { battleTab(it) { p -> moves(p); p.setLog(LogKind.SYS, "FAST", "Played fast: 12 answers, 2 won, 1 level-up", false) } }
        shoot("r9ab-line-study", Size.SPLIT) { study(it, 0) { p -> moves(p); p.setLog(LogKind.SYS, "FAST", "Played fast: 12 answers, 2 won, 1 level-up", false) } }
        shoot("r9ab-line-strip", Size.PIP_169M) { studyPanel(it, true) { p -> moves(p); p.setLog(LogKind.SYS, "FAST", "Played fast: 12 answers, 2 won, 1 level-up", false) } }
        shoot("r9ab-line-panel", Size.SPLIT) { studyPanel(it, false) { p -> moves(p); p.setLog(LogKind.SYS, "FAST", "Played fast: 12 answers, 2 won, 1 level-up", false) } }
    }

    @Test fun pausedInASmallWindow() = shoot("r9ab-paused", Size.FLOATBATTLE) { small(it) { p -> moves(p); p.setPaused(true) } }

    // ---------------------------------------------------------------- the bubble

    private fun measure(v: View, wDp: Int, hDp: Int) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    /** Lays [root] out at the window's size, then opens the bubble of [side]: its place is computed from the real layout. */
    private fun open(root: View, p: BattlePanelView, side: BarSide, variant: MoveBubbleLook.Variant? = null): View {
        val c = root.resources.configuration
        variant?.let { bar(p).bubbleVariant = it }
        measure(root, c.screenWidthDp, c.screenHeightDp)
        bar(p).openBubble(side)
        measure(root, c.screenWidthDp, c.screenHeightDp)
        return root
    }

    private fun bubbleShot(name: String, side: BarSide, size: Size = Size.PHONE, setup: (BattlePanelView) -> Unit) =
        shoot(name, size) { ctx -> val b = BattleShotKit.bound(ctx); setup(b.panel!!); open(b.root, b.panel!!, side) }

    @Test fun bubbleMineSuperCritBurn() = bubbleShot("r9ab-bub-mine", BarSide.MINE, setup = ::moves)

    @Test fun bubbleTheirsSuperEffective() = bubbleShot("r9ab-bub-theirs", BarSide.THEIRS) { p ->
        turn(p, atk(true, "Ember", Type.FIRE, Category.SPECIAL, 9, eff = 0.5), atk(false, "Surf", Type.WATER, Category.SPECIAL, 22, eff = 2.0))
    }

    @Test fun bubbleTheirsFourTimesAndCrit() = bubbleShot("r9ab-bub-theirs4", BarSide.THEIRS) { p ->
        turn(p, atk(false, "Rock Slide", Type.ROCK, Category.PHYSICAL, 41, eff = 4.0, crit = true, hits = 2))
    }

    @Test fun bubbleMissed() = bubbleShot("r9ab-bub-missed", BarSide.THEIRS) { p ->
        turn(p, atk(true, "Zap", Type.ELECTRIC, Category.SPECIAL, 10), atk(false, "Hydro Pump", Type.WATER, Category.SPECIAL, 0, missed = true))
    }

    @Test fun bubbleImmune() = bubbleShot("r9ab-bub-immune", BarSide.THEIRS) { p ->
        turn(p, atk(true, "Zap", Type.ELECTRIC, Category.SPECIAL, 10), atk(false, "Mud Slap", Type.GROUND, Category.SPECIAL, 0, eff = 0.0))
    }

    @Test fun bubbleStatusMoveWithAStatChange() = bubbleShot("r9ab-bub-statchange", BarSide.THEIRS) { p ->
        turn(p, atk(true, "Zap", Type.ELECTRIC, Category.SPECIAL, 10), atk(false, "Withdraw", Type.WATER, Category.STATUS, 0),
            fx(false, EffectKind.STAT_CHANGE, "Gnarlpup's Defense rose!", stat = "def", stages = 1),
            fx(false, EffectKind.STAT_CHANGE, "Gnarlpup's Attack fell!", stat = "atk", stages = -2))
    }

    @Test fun bubbleNotVeryEffectiveAndParalysis() = bubbleShot("r9ab-bub-notvery", BarSide.MINE) { p ->
        turn(p, atk(true, "Spark", Type.ELECTRIC, Category.PHYSICAL, 6, eff = 0.25), fx(false, EffectKind.STATUS_APPLIED, "Gnarlpup was paralyzed!", status = "par"))
    }

    /** The other look (the dark bubble the ticket described) one value away: same content, same place. */
    @Test fun bubbleDarkLookE2A() = shoot("r9ab-bub-e2a", Size.PHONE) { ctx ->
        val b = BattleShotKit.bound(ctx); moves(b.panel!!)
        open(b.root, b.panel!!, BarSide.MINE, MoveBubbleLook.Variant.E2A)
    }

    @Test fun bubbleInTheStudyPanel() = shoot("r9ab-bub-study", Size.SPLIT) { ctx ->
        var panel: BattlePanelView? = null
        val root = studyPanel(ctx, false) { p -> panel = p; moves(p); turn(p, GameEvent.Counted(3, 1, 1, 1, false)) }
        open(root, panel!!, BarSide.MINE)
    }

    @Test fun bubbleInASmallWindow() = shoot("r9ab-bub-float", Size.FLOATBATTLE) { ctx ->
        var panel: BattlePanelView? = null
        val root = small(ctx) { p -> panel = p; moves(p) }
        open(root, panel!!, BarSide.THEIRS)
    }

    // ---------------------------------------------------------------- rules (no pictures)

    private fun ready(size: Size = Size.PHONE): Pair<BattlePanelView, ActionBarView> {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        quiet(c)
        val p = BattlePanelView(c)
        p.renderAll()
        moves(p)
        measure(p, size.wDp, size.hDp)
        return p to bar(p)
    }

    private fun touch(v: View, action: Int, x: Float, y: Float): Boolean {
        val t = SystemClock.uptimeMillis()
        return v.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))
    }

    /** The point where the half [side] of [bar] is, in [root]'s own space (through every parent's offset and scale). */
    private fun centreOf(root: View, bar: ActionBarView, side: BarSide): FloatArray {
        val half = bar.halfView(side)
        val pt = floatArrayOf(half.width / 2f, half.height / 2f)
        var v: View = half
        while (v !== root) {
            if (!v.matrix.isIdentity) v.matrix.mapPoints(pt)
            pt[0] += v.left; pt[1] += v.top
            v = v.parent as View
        }
        return pt
    }

    @Test fun theBarStartsEmptyAndFillsFromTheEvents() {
        val c = ready().second.model
        assertEquals("Thunderbolt", ((c.mine as HalfState.Moved).move).move)
        assertEquals("brn", ((c.mine as HalfState.Moved).move).status)
        assertEquals("Bite", ((c.theirs as HalfState.Moved).move).move)
    }

    @Test fun aTapOnAHalfOpensTheBubbleAndAnotherTapOnItClosesIt() {
        val (p, b) = ready()
        assertFalse(b.bubbleOpen)
        b.halfView(BarSide.MINE).performClick()
        assertTrue(b.bubbleOpen)
        b.halfView(BarSide.MINE).performClick()
        assertFalse(b.bubbleOpen)
        b.halfView(BarSide.MINE).performClick(); b.halfView(BarSide.THEIRS).performClick()
        assertTrue("the other half takes the bubble over", b.bubbleOpen)
        assertEquals(1, b.bubbleViews())
        turn(p, encounter())
        assertEquals(0, b.bubbleViews())
    }

    @Test fun cancellingThePanelClosesAnOpenBubbleSoTheNextTapOpensOne() {
        val (p, b) = ready()
        b.halfView(BarSide.MINE).performClick()
        assertTrue(b.bubbleOpen)
        p.cancelAnimations()
        assertFalse(b.bubbleOpen)
        assertEquals(0, b.bubbleViews())
        b.halfView(BarSide.MINE).performClick()
        assertTrue("one tap opens a bubble again", b.bubbleOpen)
    }

    @Test fun aTapAnywhereElseInThePanelClosesIt() {
        val (p, b) = ready()
        b.halfView(BarSide.MINE).performClick()
        assertTrue(b.bubbleOpen)
        touch(p, MotionEvent.ACTION_DOWN, 5f, 5f)
        assertFalse(b.bubbleOpen)
    }

    @Test fun aTouchOnAHalfDoesNotCloseItBeforeItsOwnClick() {
        val (p, b) = ready()
        b.halfView(BarSide.MINE).performClick()
        val at = centreOf(p, b, BarSide.THEIRS)
        touch(p, MotionEvent.ACTION_DOWN, at[0], at[1])
        assertTrue("the half's own click decides", b.bubbleOpen)
        touch(p, MotionEvent.ACTION_CANCEL, at[0], at[1])
    }

    @Test fun aNewMoveOnThatSideClosesIt() {
        val (p, b) = ready()
        b.halfView(BarSide.THEIRS).performClick()
        turn(p, atk(true, "Zap", Type.ELECTRIC, Category.SPECIAL, 3))
        assertTrue("mine changed, theirs is still open", b.bubbleOpen)
        turn(p, atk(false, "Bite", Type.DARK, Category.PHYSICAL, 4))
        assertFalse(b.bubbleOpen)
    }

    @Test fun aNewFightClosesIt() {
        val (p, b) = ready()
        b.halfView(BarSide.MINE).performClick()
        turn(p, encounter())
        assertFalse(b.bubbleOpen)
        assertEquals(HalfState.Empty, b.model.mine); assertEquals(HalfState.Empty, b.model.theirs)
    }

    @Test fun theBarBeingHiddenClosesIt() {
        val (_, b) = ready()
        b.halfView(BarSide.MINE).performClick()
        b.visibility = View.GONE
        assertFalse(b.bubbleOpen)
    }

    @Test fun aLineAboutTheWholeBattleClosesItAndTheNextMoveBringsTheBarBack() {
        val (p, b) = ready()
        b.halfView(BarSide.MINE).performClick()
        p.setLog(LogKind.SYS, "INFO", "Something about the whole battle", false)
        assertFalse(b.bubbleOpen)
        assertTrue(b.lineShown())
        turn(p, atk(true, "Zap", Type.ELECTRIC, Category.SPECIAL, 3))
        assertFalse(b.lineShown())
    }

    @Test fun anEmptyOrNoteHalfOpensNothing() {
        val (p, b) = ready()
        turn(p, encounter())
        b.halfView(BarSide.MINE).performClick()
        assertFalse(b.bubbleOpen)
        turn(p, fx(true, EffectKind.CANT_MOVE, "Sparkmouse is fast asleep."))
        b.halfView(BarSide.MINE).performClick()
        assertFalse(b.bubbleOpen)
    }

    @Test fun theDotsSitWhereTheSettingSays() {
        val (p, b) = ready()
        b.dotsOn = true
        b.dotsPlace = DotsPlace.MIDDLE
        assertEquals(listOf("mine", "dots", "theirs"), b.segmentOrder())
        b.dotsPlace = DotsPlace.RIGHT_END
        assertEquals(listOf("mine", "theirs", "dots"), b.segmentOrder())
        b.dotsOn = false
        assertEquals(listOf("mine", "theirs"), b.segmentOrder())
        assertTrue(p.width > 0)
    }
}
