package com.husarp.ankigo.shots

import android.view.View
import android.widget.FrameLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class R36MFlowTest {
    @get:Rule val paparazzi = Shots.rule()
    @After fun reset() { BattleLog.frontText = BattleLog::plain; BattleShotKit.reset() }

    private fun context(reduced: Boolean = true): android.content.Context {
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        AppPrefs.setBackgroundPictures(c, false)
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", reduced).commit()
        return c
    }
    private fun measure(v: View, height: Int = 869) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((height * d).toInt(), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }
    private fun result() = listOf<GameEvent>(GameEvent.Defeated(1, "Old", 20),
        GameEvent.Encounter(54, "Next", 5, false, Tier.NORMAL, 20, 20, false, false, GameRepo.fightId()!!))

    @Test fun anInitialEncounterAlsoWaitsAndAnswersCannotAdvanceIt() {
        val c = context()
        GameRepo.state!!.day = GameRepo.game!!.dayKey()
        GameRepo.state!!.battle = null
        val answer = com.husarp.ankigo.live.LiveSession.practiceAnswer(c, 123456, 3)
        assertTrue(answer.any { it is GameEvent.Encounter })
        assertTrue(answer.none { it is GameEvent.Attack || it is GameEvent.Charge })
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel)
        measure(panel)
        player.enqueue(answer)
        assertTrue(player.holding)
        assertEquals(0, GameRepo.state!!.battle!!.answers)
        assertTrue(player.continueBattle())
        assertFalse(player.holding)
    }

    @Test fun legacyUntouchedEncountersWaitButAnExplicitlyContinuedSaveStaysActive() {
        context()
        val state = GameRepo.state!!
        state.battle!!.apply { rounds = 0; answers = 0; failedThrows = 0; buttons.clear(); awaitingContinue = false }
        val saved = SaveCodec.encode(state)
        assertFalse(SaveCodec.decode(saved).battle!!.awaitingContinue)
        val legacy = org.json.JSONObject(saved)
        legacy.getJSONObject("battle").remove("awaitingContinue")
        assertTrue("an old prepared encounter cannot auto-start", SaveCodec.decode(legacy.toString()).battle!!.awaitingContinue)
        legacy.getJSONObject("battle").put("rounds", 1)
        assertFalse("an ongoing legacy battle resumes normally", SaveCodec.decode(legacy.toString()).battle!!.awaitingContinue)
    }

    @Test fun shoppingAndSavingNeverReleaseContinueOrAdvanceTheWaitingBattle() {
        val c = context()
        val p = BattlePanelView(c)
        val events = EventPlayer(p)
        measure(p)
        GameRepo.state!!.battle!!.wild.hp = 1
        val won = GameRepo.act(c) { it.onAnswer(Answer(123000, 3, System.currentTimeMillis())) }
        assertTrue("a real round ended the fight", won.any { it is GameEvent.Caught || it is GameEvent.Defeated })
        events.enqueue(won)
        val b = GameRepo.state!!.battle!!
        assertTrue(b.awaitingContinue)
        val hp = b.wild.hp
        val answers = b.answers
        repeat(3) {
            assertTrue(GameRepo.runOffscreen(c) { Shop.buy(it.data, it.state, "potion") }.ok)
            GameRepo.act(c) { it.tick() }
            GameRepo.act(c) { it.onAnswer(Answer(900L + it.state.totalReviews, 3, System.currentTimeMillis())) }
            p.renderAll()
            assertTrue(events.holding)
            assertEquals(hp, b.wild.hp)
            assertEquals(answers, b.answers)
        }
        assertTrue(SaveCodec.decode(SaveCodec.encode(GameRepo.state!!)).battle!!.awaitingContinue)
        assertTrue(events.continueBattle())
        assertFalse(events.holding)
        assertFalse(b.awaitingContinue)
    }

    @Test fun windowChangesTimersAnswersAndRecreationKeepContinuePending() {
        val c = context(false)
        val p = BattlePanelView(c)
        val events = EventPlayer(p)
        measure(p)
        events.enqueue(result())
        events.pauseHeldResult()
        p.setCompact(true)
        measure(p, 180)
        events.enqueue(listOf(GameEvent.Counted(3, 10, 3, 2, false), GameEvent.Charge(10, 1, 2)))
        repeat(3) { paparazzi.snapshot(FrameLayout(c), "m-hold-timer", (it + 1) * 60_000L) }
        assertTrue(events.holding)
        events.clear()
        val fresh = BattlePanelView(c)
        val resumed = EventPlayer(fresh)
        measure(fresh)
        fresh.renderAll()
        assertTrue(resumed.holding)
        assertTrue(fresh.tilesShown)
        assertTrue(resumed.continueBattle())
        assertFalse(resumed.holding)
    }

    @Test fun continuingOnARecreatedHostAlsoReleasesTheOldPanel() {
        val c = context()
        val oldPanel = BattlePanelView(c)
        val old = EventPlayer(oldPanel)
        measure(oldPanel)
        old.enqueue(result())
        assertTrue(old.holding)
        val fresh = BattlePanelView(c)
        val current = EventPlayer(fresh)
        measure(fresh)
        fresh.renderAll()
        assertTrue(current.continueBattle())
        oldPanel.renderAll()
        assertFalse(old.holding)
        assertFalse(current.holding)
    }

    @Test fun heldBattleRejectsBallEscapeAndItemHitsButHealingStillWorks() {
        val c = context()
        val s = GameRepo.state!!
        val b = s.battle!!
        b.awaitingContinue = true
        val hp = s.main!!.hp
        assertTrue(GameRepo.game!!.throwBall("poke-ball").isEmpty())
        assertTrue(GameRepo.game!!.escape("fluffy-tail").isEmpty())
        val healed = GameRepo.game!!.useItem("potion", s.main!!.uid)
        assertTrue(healed.ok)
        assertTrue(s.main!!.hp > hp)
        assertTrue(healed.events.none { it is GameEvent.SwitchHit || it is GameEvent.Attack })
        val switched = GameRepo.game!!.requestMain("t2", cardShowing = false)
        assertTrue(switched.ok)
        assertTrue(switched.events.none { it is GameEvent.SwitchHit || it is GameEvent.Attack || it is GameEvent.Encounter })
        assertTrue(b.awaitingContinue)
    }

    @Test fun flinchPlaybackFinishesWithoutWaitingForTheScheduler() {
        val c = context(false)
        val panel = BattlePanelView(c)
        val player = EventPlayer(panel)
        measure(panel)
        player.enqueue(listOf(GameEvent.RoundStart(1.0, 20, true),
            GameEvent.Effect(true, "Main flinched and couldn't move!", 20, 40, null, EffectKind.CANT_MOVE, "flinch", 0, 0)))
        repeat(3) { paparazzi.snapshot(FrameLayout(c), "m-flinch", (it + 1) * 60_000L) }
        assertTrue("flinch has a bounded visual delay", player.idle)
        assertFalse(player.holding)
    }

    @Test fun slowBattleLogFrontCannotBlockAnAnswerAndKeepsItsCardText() {
        val c = context()
        BattleLog.init(c)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val mainThread = Thread.currentThread()
        var textThread: Thread? = null
        BattleLog.frontText = { html ->
            textThread = Thread.currentThread()
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            BattleLog.plain(html)
        }
        try {
            BattleLog.events(listOf(GameEvent.Encounter(1, "Wild", 5, false, Tier.NORMAL, 20, 20, false, false)))
            BattleLog.front(987654, "<div>Flinch card</div>")
            assertTrue("parsing started on its worker", entered.await(5, TimeUnit.SECONDS))
            assertNotSame(mainThread, textThread)
            BattleLog.answer("study", 987654, 3)
            BattleLog.events(listOf(GameEvent.Counted(3, 10, 3, 2, false), GameEvent.Charge(10, 1, 2)))
        } finally { release.countDown() }
        val log = BattleLog.snapshot()
        assertTrue(log.flatMap { it.rounds }.flatMap { it.answers }.any { it.card == "Flinch card" })
    }
}
