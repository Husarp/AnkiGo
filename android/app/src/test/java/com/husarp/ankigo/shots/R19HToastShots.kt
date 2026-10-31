package com.husarp.ankigo.shots

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.DailyGift
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.SwitchQueue
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.study.NewCardOk
import com.husarp.ankigo.ui.ActionBarView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import com.husarp.ankigo.ui.LogKind
import com.husarp.ankigo.ui.ToastKind
import com.husarp.ankigo.ui.ToastQueue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R19-H (PLAN 257, pick A from the left): the old whole-bar lines are card toasts at the arena's top-left, above your Pokémon, and the
 * action bar keeps its segments. Flow tests (a line request leaves the bar in segments, the queue, a tap, reduced motion, TalkBack, the
 * place per host) and the screenshots r19h-* (light + dark): the Study panel, the Battle tab, the Study strip, picture-in-picture and
 * the floating window.
 */
class R19HToastShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    // ---------------------------------------------------------------- helpers

    private fun ctx(size: Size = Size.SPLIT, still: Boolean = true): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", still).commit()
        return c
    }

    private fun bar(p: BattlePanelView): ActionBarView = BattlePanelView::class.java.getDeclaredField("logBox").apply { isAccessible = true }.get(p) as ActionBarView

    private fun studyPanel(ctx: Context, strip: Boolean = false, mini: Boolean = false) =
        BattlePanelView(ctx).also { it.setStudyLayout(strip, mini); it.renderAll(); it.restoreStudyCharge() }

    private fun measure(v: View, wDp: Int, hDp: Int) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private fun atk(byPlayer: Boolean, move: String, type: Type?, cat: Category, damage: Int) =
        GameEvent.Attack(byPlayer, if (byPlayer) "Sparkmouse" else "Gnarlpup", if (byPlayer) "Gnarlpup" else "Sparkmouse", move, type, cat, damage, 1, 1.0, false, false, false, 10, 30, 20, 30)

    private val round = GameEvent.RoundStart(1.0, 1, true)
    private val moves = arrayOf<GameEvent>(round, atk(true, "Thunderbolt", Type.ELECTRIC, Category.SPECIAL, 24), atk(false, "Bite", Type.DARK, Category.PHYSICAL, 9))

    private fun turn(p: BattlePanelView, vararg e: GameEvent) = e.forEach { p.observeStudyEvent(it) }

    /** The card's rectangle in the panel's own px (the layer is a full-size child of the scaled container; scale 1 in these hosts). */
    private fun cardRect(p: BattlePanelView): Rect {
        val c = p.toastLayer.shownCard!!
        val lp = c.layoutParams as FrameLayout.LayoutParams
        return Rect(lp.leftMargin, lp.topMargin, lp.leftMargin + c.measuredWidth, lp.topMargin + c.measuredHeight)
    }

    private fun arenaRect(p: BattlePanelView) = Rect(p.arena.left, p.arena.top, p.arena.right, p.arena.bottom)

    private fun hits(a: Rect, b: com.husarp.ankigo.ui.FloatSpot.Rect) = a.left < b.r && b.l < a.right && a.top < b.b && b.t < a.bottom

    // ---------------------------------------------------------------- flow: the bar keeps its segments

    private val oldLines = listOf(
        Triple(LogKind.SYS, "FAST", "Played fast: 12 answers, 2 won, 1 level-up"),
        Triple(LogKind.NEUTRAL, "OK", NewCardOk.LOG_LINE),
        Triple(LogKind.NEUTRAL, "UNDO", "Redo after undo — not counted"),
        Triple(LogKind.NEUTRAL, "UNDO", "Undo in AnkiDroid: the redo won't count twice"),
        Triple(LogKind.SYS, "CAUGHT UP", "Caught up 5 answers made while AnkiGo was closed: 1 won, 0 caught, +25 coins"),
        Triple(LogKind.SYS, "GOAL", DailyGift.logText("Great Ball", 4)),
        Triple(LogKind.SYS, "ITEM", "Found {{a Super Potion}}!"),
        Triple(LogKind.SYS, "BADGE", "Badge: {{Night Owl}}"),
        Triple(LogKind.SYS, "TRAINER", "Trainer level {{12}}!"),
        Triple(LogKind.SYS, "NEW DAY", "A new day begins: the daily goal starts again."),
        Triple(LogKind.SYS, "GOAL", "Daily goal reached: 100 reviews!"),
        Triple(LogKind.SYS, "INFO", "Your team is full: Puddlet went to the Box."),
        Triple(LogKind.SYS, "SWITCH", SwitchQueue.queuedText("Puddlet")),
    )

    @Test fun everyOldLineLeavesTheBarInSegmentsAndBecomesAToast() {
        val p = studyPanel(ctx())
        measure(p, 412, 400)
        val b = bar(p)
        turn(p, *moves)
        for ((kind, pill, text) in oldLines) {
            p.setLog(kind, pill, text)
            assertFalse("$pill: the bar keeps its segments", b.lineShown())
            assertEquals(listOf("mine", "dots", "theirs"), b.segmentOrder())
            val shown = p.toastLayer.shown
            assertNotNull("$pill: a toast shows", shown)
            assertEquals(ToastKind.of(kind, pill, text), shown!!.kind)
            p.toastLayer.finishNow()
        }
    }

    @Test fun theBarStillShowsTheMovesWhileAToastIsUp() {
        val p = studyPanel(ctx())
        measure(p, 412, 400)
        turn(p, *moves)
        p.setLog(LogKind.SYS, "ITEM", "Found a Super Potion!")
        turn(p, round, atk(true, "Tackle", Type.NORMAL, Category.PHYSICAL, 5))
        assertEquals("a move does not end a toast", ToastKind.ITEM, p.toastLayer.shown?.kind)
        assertEquals(false, bar(p).lineShown())
        assertEquals("Tackle", (bar(p).model.mine as com.husarp.ankigo.ui.HalfState.Moved).move.move)
    }

    @Test fun theSwitchQueuedEventIsACardToastNotABottomMessage() {
        val c = ctx(still = false)   // reduced motion would skip the events (EventPlayer.skipAll), not play them
        val panel = studyPanel(c)
        measure(panel, 412, 400)
        val player = EventPlayer(panel)
        player.enqueue(listOf(GameEvent.SwitchQueued("t2", "Puddlet")))
        assertEquals(ToastKind.SWITCH, panel.toastLayer.shown?.kind)
        assertEquals(SwitchQueue.queuedText("Puddlet"), panel.toastLayer.shown?.text)
        assertFalse(bar(panel).lineShown())
    }

    @Test fun anItemEventPlayedByThePlayerShowsItsToast() {
        val c = ctx(still = false)
        val panel = studyPanel(c)
        measure(panel, 412, 400)
        EventPlayer(panel).enqueue(listOf(GameEvent.ItemFound("super-potion", 1)))
        assertEquals(ToastKind.ITEM, panel.toastLayer.shown?.kind)
        assertTrue(panel.toastLayer.shown!!.text.startsWith("Found "))
        assertFalse(bar(panel).lineShown())
    }

    @Test fun otherCodeCanRaiseAToastWithAKindAndAText() {
        val p = studyPanel(ctx())
        measure(p, 412, 400)
        p.toast(ToastKind.SWITCH, "Switched! Wild {{Gnarlpup}} gets a free move.")
        assertEquals(ToastKind.SWITCH, p.toastLayer.shown?.kind)
        assertEquals("Switched! Wild {{Gnarlpup}} gets a free move.", p.toastLayer.shown?.text)
    }

    @Test fun thePausedLineIsTheOnlyLineLeftAndItStillWorks() {
        val p = studyPanel(ctx(), strip = true)
        measure(p, 412, 180)
        p.setPaused(true)
        assertTrue(bar(p).lineShown())
        p.setPaused(false)
        assertFalse(bar(p).lineShown())
    }

    // ---------------------------------------------------------------- flow: queue, tap, motion, TalkBack

    @Test fun severalInARowShowOneAtATimeAndMergeEqualOnes() {
        val p = studyPanel(ctx())
        measure(p, 412, 400)
        var t = 0L
        p.toastLayer.clock = { t }
        p.setLog(LogKind.SYS, "ITEM", "Found a Potion!")
        p.setLog(LogKind.SYS, "GOAL", "Daily gift: Great Ball")
        p.setLog(LogKind.SYS, "TRAINER", "Trainer level 3!")
        p.setLog(LogKind.SYS, "ITEM", "Found a Potion!")   // equal: merged
        assertEquals("only one card in the layer", ToastKind.ITEM, p.toastLayer.shown?.kind)
        assertEquals(2, p.toastLayer.pendingCount)
        assertEquals(1, (0 until p.toastLayer.childCount).count { p.toastLayer.getChildAt(it) is com.husarp.ankigo.ui.ToastCard })
        t = 2600; p.toastLayer.finishNow()
        assertEquals(ToastKind.GIFT, p.toastLayer.shown?.kind)
        t = 2700; p.toastLayer.finishNow()
        assertEquals(ToastKind.TRAINER, p.toastLayer.shown?.kind)
        t = 2800; p.toastLayer.finishNow()
        assertNull(p.toastLayer.shown)
        t = 2900
        assertEquals("the same news again so soon is merged", ToastQueue.Offer.MERGED, p.toastLayer.show(ToastKind.ITEM, "Found a Potion!"))
    }

    @Test fun aTapClosesTheCardAndTheNextOneComes() {
        val p = studyPanel(ctx())
        measure(p, 412, 400)
        p.setLog(LogKind.SYS, "ITEM", "Found a Potion!")
        p.setLog(LogKind.SYS, "TRAINER", "Trainer level 3!")
        p.toastLayer.shownCard!!.performClick()
        assertEquals(ToastKind.TRAINER, p.toastLayer.shown?.kind)
        p.toastLayer.shownCard!!.performClick()
        assertNull(p.toastLayer.shown)
    }

    @Test fun reducedMotionAppearsWhereItBelongsWithoutSliding() {
        val p = studyPanel(ctx(still = true))
        measure(p, 412, 400)
        p.setLog(LogKind.SYS, "ITEM", "Found a Potion!")
        val c = p.toastLayer.shownCard!!
        assertEquals(0f, c.translationX, 0f); assertEquals(1f, c.alpha, 0f)
    }

    @Test fun withMotionItStartsOffTheRightEdge() {
        val p = studyPanel(ctx(still = false))
        measure(p, 412, 400)
        p.setLog(LogKind.SYS, "ITEM", "Found a Potion!")
        val c = p.toastLayer.shownCard!!
        val animators = android.animation.ValueAnimator.areAnimatorsEnabled()
        if (animators) assertTrue("starts right of the panel", c.translationX > 0f && c.translationX + cardRect(p).left >= p.toastLayer.width)
        p.cancelAnimations()   // a skipped batch: the slide ends where it belongs
        assertEquals(0f, c.translationX, 0f)
        assertEquals("the toast is still there", ToastKind.ITEM, p.toastLayer.shown?.kind)
    }

    @Test fun aLineWithoutPopComesWithoutSliding() {
        val p = studyPanel(ctx(still = false))
        measure(p, 412, 400)
        p.setLog(LogKind.SYS, "ITEM", "Found a Potion!", false)
        assertEquals(0f, p.toastLayer.shownCard!!.translationX, 0f)
    }

    @Test fun theTextIsToldOnceToTalkBackAndNotAgainWhenMerged() {
        val p = studyPanel(ctx())
        measure(p, 412, 400)
        val said = ArrayList<String>()
        p.toastLayer.announce = { said += it }
        p.setLog(LogKind.SYS, "ITEM", "Found {{a Super Potion}}!")
        p.setLog(LogKind.SYS, "ITEM", "Found {{a Super Potion}}!")
        assertEquals(listOf("Found a Super Potion!"), said)
        p.setLog(LogKind.SYS, "GOAL", "Daily gift: Great Ball")
        assertEquals("a queued one is told when it comes in, not before", 1, said.size)
        p.toastLayer.finishNow()
        assertEquals(listOf("Found a Super Potion!", "Daily gift: Great Ball"), said)
        assertEquals("Daily gift: Great Ball", p.toastLayer.shownCard!!.contentDescription)
    }

    @Test fun aToastCameBeforeTheFirstLayoutShowsWhenTheViewIsLaidOut() {
        val p = studyPanel(ctx())
        p.setLog(LogKind.SYS, "ITEM", "Found a Potion!")
        assertNull("nothing to draw in yet", p.toastLayer.shownCard)
        measure(p, 412, 400)
        assertNotNull(p.toastLayer.shownCard)
    }

    // ---------------------------------------------------------------- flow: the place per host

    private fun placeIn(size: Size, strip: Boolean, mini: Boolean = false): Triple<BattlePanelView, Rect, Rect> {
        val p = studyPanel(ctx(size), strip, mini)
        measure(p, size.wDp, size.hDp)
        turn(p, *moves)
        p.setLog(LogKind.SYS, "ITEM", "Found a Super Potion!")
        return Triple(p, cardRect(p), arenaRect(p))
    }

    @Test fun inTheStudyPanelItSitsAtTheArenasTopRightAndKeepsOffYourInfoBox() {
        val (p, card, arena) = placeIn(Size.SPLIT, strip = false)
        assertTrue("inside the arena", arena.contains(card))
        val keep = p.arena.toastKeepOff()
        assertFalse("your info box stays visible", hits(Rect(card.left - arena.left, card.top - arena.top, card.right - arena.left, card.bottom - arena.top), keep.ourCard))
        assertTrue("on the right side", card.right - arena.left > arena.width() * 3 / 4)
        assertTrue("near the top", card.top - arena.top < arena.height() / 3)
    }

    @Test fun inTheStudyStripItStaysInsideTheArenaAndKeepsOffYourInfoBox() {
        val (p, card, arena) = placeIn(Size.PIP_169M, strip = true)
        assertTrue(arena.contains(card))
        val keep = p.arena.toastKeepOff()
        assertFalse(hits(Rect(card.left - arena.left, card.top - arena.top, card.right - arena.left, card.bottom - arena.top), keep.ourCard))
    }

    @Test fun inTheMiniBarItIsOneSmallLineOverThePanel() {
        val p = studyPanel(ctx(Size.SPLIT), strip = true, mini = true)
        measure(p, 412, 40)
        p.setLog(LogKind.SYS, "ITEM", "Found a Super Potion!")
        val c = p.toastLayer.shownCard!!
        assertTrue("one line", c.main.maxLines == 1)
        assertNull("no second line in the smallest form", c.sub)
        assertTrue("inside the panel", Rect(0, 0, p.width, p.height).contains(cardRect(p)))
        assertFalse(bar(p).lineShown())
    }

    @Test fun aLongerTextIsNeverCutWhereTheCardCouldWrapInstead() {
        for ((size, strip) in listOf(Size.SPLIT to false, Size.PIP_169M to true, Size.PHONE to false)) {
            val p = studyPanel(ctx(size), strip)
            measure(p, size.wDp, size.hDp)
            p.setLog(LogKind.SYS, "SWITCH", SwitchQueue.queuedText("Puddlet"))
            assertFalse("$size: the switch text is whole", p.toastLayer.shownCard!!.truncated)
        }
    }

    @Test fun theCardNeverTakesMoreThanTheSideOfTheArena() {
        val (_, card, arena) = placeIn(Size.PHONE, strip = false)
        assertTrue(card.width() <= arena.width() * 0.9)
    }

    // ---------------------------------------------------------------- screenshots

    private fun shoot(name: String, size: Size, build: (Context) -> View) = paparazzi.shootWith(name, listOf(size)) { ctx ->
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        build(ctx)
    }

    private val kinds: List<Pair<String, (BattlePanelView) -> Unit>> = listOf(
        "item" to { p -> p.setLog(LogKind.SYS, "ITEM", "Found {{a Super Potion}}!") },
        "gift" to { p -> p.setLog(LogKind.SYS, "GOAL", DailyGift.logText("Great Ball", 4)) },
        "fast" to { p -> p.setLog(LogKind.SYS, "FAST", "Played fast: 12 answers, 2 won, 1 level-up") },
        "switch" to { p -> p.setLog(LogKind.SYS, "SWITCH", SwitchQueue.queuedText("Puddlet")) },
        "caughtup" to { p -> p.setLog(LogKind.SYS, "CAUGHT UP", "Caught up 5 answers made while AnkiGo was closed: 1 won, 0 caught, +25 coins") },
    )

    /** Option C must remain legible in full, compact and tiny hosts, in both palettes. */
    @Test fun trainerRankShots() {
        assertEquals(com.husarp.ankigo.R.drawable.ic_trainer_rank, ToastKind.TRAINER.icon)
        for (size in listOf(Size.PHONE, Size.SPLIT, Size.PIP_169M)) {
            shoot("r23r-trainer-${size.name.lowercase()}", size) { ctx ->
                studyPanel(ctx, strip = size == Size.PIP_169M).also {
                    measure(it, size.wDp, size.hDp)
                    it.setLog(LogKind.SYS, "TRAINER", "Trainer level {{12}}!")
                    assertEquals(ToastKind.TRAINER, it.toastLayer.shown?.kind)
                }
            }
        }
    }

    @Test fun studyPanelShots() {
        for ((k, raise) in kinds) shoot("r19h-study-$k", Size.SPLIT) { ctx -> studyPanel(ctx).also { turn(it, *moves); raise(it) } }
    }

    @Test fun battleTabShots() {
        for ((k, raise) in kinds.take(4)) shoot("r19h-battle-$k", Size.PHONE) { ctx ->
            val b = BattleShotKit.bound(ctx)
            turn(b.panel!!, *moves); raise(b.panel!!)
            b.root
        }
    }

    @Test fun splitScreenBattleTabShot() = shoot("r19h-split-item", Size.SPLIT) { ctx ->
        val b = BattleShotKit.bound(ctx)
        b.panel!!.setTurnDots(true)   // GameActivity.updateTurnDots with isInMultiWindowMode
        turn(b.panel!!, *moves)
        b.panel!!.setLog(LogKind.SYS, "ITEM", "Found {{a Super Potion}}!")
        b.root
    }

    @Test fun studyStripShots() {
        for ((k, raise) in listOf(kinds[0], kinds[3])) shoot("r19h-strip-$k", Size.PIP_169M) { ctx -> studyPanel(ctx, strip = true).also { turn(it, *moves); raise(it) } }
    }

    @Test fun pipShot() = shoot("r19h-pip-item", Size.PIP_169) { ctx ->
        val b = BattleShotKit.bound(ctx)
        BattleShotKit.pip(b)
        b.panel!!.setLog(LogKind.SYS, "ITEM", "Found {{a Super Potion}}!")
        b.root
    }

    @Test fun miniShot() = shoot("r19h-mini-item", Size.PIP_169M) { ctx ->
        studyPanel(ctx, strip = true, mini = true).also { turn(it, *moves); it.setLog(LogKind.SYS, "ITEM", "Found {{a Super Potion}}!") }
    }

    @Test fun theBarKeepsItsMovesAndAToastShows() = shoot("r19h-study-after-move", Size.SPLIT) { ctx ->
        studyPanel(ctx).also {
            turn(it, *moves)
            it.setLog(LogKind.SYS, "ITEM", "Found {{a Super Potion}}!")
            it.setLog(LogKind.SYS, "TRAINER", "Trainer level {{12}}!")   // waits behind the first: one at a time
        }
    }

    private fun device(w: Int, h: Int, theme: Theme): DeviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = w * 2, screenHeight = h * 2, xdpi = 320, ydpi = 320, density = Density.XHIGH, nightMode = theme.night,
        orientation = if (w > h) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
    )

    private fun <T> field(o: Any, name: String): T {
        val f = o.javaClass.getDeclaredField(name); f.isAccessible = true
        @Suppress("UNCHECKED_CAST") return f.get(o) as T
    }

    private fun floating(ctx: Context, raise: (BattlePanelView) -> Unit): View {
        val self = object : ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        v.javaClass.getDeclaredMethod("bind").apply { isAccessible = true }.invoke(v)
        val panel: BattlePanelView? = field(v, "panel")
        panel?.renderAll()
        panel?.let { raise(it) }
        Thread.sleep(400)
        return field<Any>(field(v, "window"), "root") as View
    }

    private fun shootAt(name: String, w: Int, h: Int, build: (Context) -> View) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = device(w, h, theme))
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            val c = Shots.files(paparazzi.context)
            FakeGame.install(c)
            c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val v = build(c)
            val frame = FrameLayout(c).apply { addView(v, FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "$name-${theme.name.lowercase()}", 1000)
        }
    }

    @Test fun floatDefaultShot() = shootAt("r19h-float-default-item", 240, 190) { floating(it) { p -> p.setLog(LogKind.SYS, "ITEM", "Found {{a Super Potion}}!") } }
    @Test fun floatSmallestShot() = shootAt("r19h-float-smallest-item", 170, 130) { floating(it) { p -> p.setLog(LogKind.SYS, "ITEM", "Found {{a Super Potion}}!") } }
}
