package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.game.ActionResult
import com.husarp.ankigo.game.Answer
import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.BattleCard
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.EventPlayer
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * R14-E (PLAN 191): fight events made on a screen that is not a battle (an item on the main from the Bag, a team edit that changes
 * the main) wait in GameRepo and play in the battle when you come back: the Pokémon at its HP before the hit, then the wild one's
 * attack. Once, never twice; dropped when an answer came first or the fight is over. PNGs: r14e-*.
 */
class R14EPendingFightShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun freshNotifier() = forgetQueuedNotify()

    @After fun cleanUp() {
        BattleShotKit.reset()
        forgetQueuedNotify()
    }

    /** GameRepo coalesces its listener calls behind a "queued" flag; a test that ends before the posted call ran leaves it set (as R9StaleMainShots takes care of). */
    private fun forgetQueuedNotify() {
        GameRepo::class.java.getDeclaredField("notifyQueued").apply { isAccessible = true }.setBoolean(GameRepo, false)
    }

    private val st get() = GameRepo.state!!

    private fun ctx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        // a weak wild one: the incoming Pokémon survive its one attack, so the fight goes on (a fight that ends is not kept, see PendingFight.fightPart)
        st.battle!!.wild.level = 5
        // one plain move: the same hit every run (a Water Gun's effect does not finish inside the renderer, see R10SwitchHitShots' reduced-motion test)
        st.battle!!.wild.moves.clear(); st.battle!!.wild.moves += "tackle"
        st.battle!!.wild.hp = com.husarp.ankigo.game.StatCalc.maxHp(FakeGame.data, st.battle!!.wild)
        return c
    }

    private fun page(c: Context) = TeamPage(c, NoHost)

    /** A Potion on the main from the Bag (a page, not a battle host): the wild one attacks once. */
    private fun heal(c: Context): ActionResult = MonActions(page(c)).runUse("potion", "t1")

    private fun settle(host: View, name: String, ms: Long = 4000) {
        val frame = host.parent as? View ?: FrameLayout(host.context).apply { addView(host, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, name, ms)
    }

    private fun hitOf(r: ActionResult) = r.events.filterIsInstance<GameEvent.SwitchHit>().single()

    // ---------------------------------------------------------------- GameRepo: pending, taken once

    @Test fun aBagHealOnTheMainMidFightWaitsForTheBattleAndIsTakenOnce() {
        val c = ctx()
        val fight = GameRepo.fightId()
        val r = heal(c)
        assertTrue(r.ok)
        assertEquals("potion", hitOf(r).item)
        assertEquals("the fight goes on", fight, GameRepo.fightId())
        val waiting = GameRepo.pendingFightEvents()
        assertTrue(waiting.first() is GameEvent.SwitchHit)
        assertTrue("the wild one's attack", waiting.any { it is GameEvent.Attack && !it.byPlayer })
        assertEquals("peeking takes nothing", waiting, GameRepo.pendingFightEvents())
        assertEquals(waiting, GameRepo.takePendingFightEvents(fight))
        assertTrue("never twice", GameRepo.takePendingFightEvents(fight).isEmpty())
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
    }

    @Test fun twoActionsAppendInOrder() {
        val c = ctx()
        val a = heal(c)
        val b = heal(c)
        val waiting = GameRepo.pendingFightEvents()
        assertEquals(listOf(hitOf(a), hitOf(b)), waiting.filterIsInstance<GameEvent.SwitchHit>())
        assertTrue("the first action's events come first", waiting.indexOfFirst { it === hitOf(a) } < waiting.indexOfFirst { it === hitOf(b) })
    }

    @Test fun takingWithAnotherFightIdDropsThem() {
        val c = ctx()
        val fight = GameRepo.fightId()
        heal(c)
        assertTrue(GameRepo.takePendingFightEvents("another-wild").isEmpty())
        assertTrue("and they are gone for this fight too", GameRepo.takePendingFightEvents(fight).isEmpty())
        heal(c)
        assertTrue("no fight at all", GameRepo.takePendingFightEvents(null).isEmpty())
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
    }

    @Test fun anAnswerThatArrivesFirstDropsThem() {
        val c = ctx()
        heal(c)
        assertTrue(GameRepo.pendingFightEvents().isNotEmpty())
        val events = GameRepo.act(c) { it.onAnswer(Answer(91_001L, 3, System.currentTimeMillis())) }
        assertTrue(events.any { it is GameEvent.Counted })
        assertTrue("their end state is in the save already", GameRepo.pendingFightEvents().isEmpty())
        assertTrue(GameRepo.takePendingFightEvents().isEmpty())
    }

    @Test fun aHostsOwnSwitchDropsThem() {
        val c = ctx()
        heal(c)
        GameRepo.run(c) { it.setMain("t3") } // a battle host plays its own events at once
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
    }

    @Test fun noFightOrAFightThatEndedKeepsNothing() {
        val c = ctx()
        val before = GameRepo.fightId()
        GameRepo.keepPendingFight(null, listOf(GameEvent.MainChanged("t2", "Raichu"))) // there was no fight
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
        GameRepo.keepPendingFight("some-other-wild", listOf(GameEvent.MainChanged("t2", "Raichu"))) // the fight is not the one the action started in
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
        GameRepo.keepPendingFight(before, listOf(GameEvent.Info("renamed"))) // nothing of the fight in it
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
        GameRepo.keepPendingFight(before, listOf(GameEvent.MainChanged("t2", "Raichu"), GameEvent.MainHealed("t2", "Raichu", 60))) // the main fainted: the fight is over
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
        GameRepo.keepPendingFight(before, listOf(GameEvent.MainChanged("t2", "Raichu"))) // and a switch of the fight on is kept
        assertEquals(1, GameRepo.pendingFightEvents().size)
    }

    @Test fun aRenameOrAFavouriteLeavesNothingPending() {
        val c = ctx()
        page(c).act { it.rename("b3", "Zippy") }
        page(c).act { it.setFavourite("b4", true) }
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
    }

    @Test fun withoutAFightNothingIsKept() {
        val c = ctx()
        st.battle = null
        page(c).act { it.moveInTeam("t2", -1) }
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
    }

    // ---------------------------------------------------------------- a host: the panel draws the HP before the hit, plays once

    @Test fun thePanelDrawsTheHpBeforeTheHitAndPlaysTheEventsOnce() {
        val c = ctx()
        val b = BattleShotKit.bound(c)
        val arena = b.arena!!
        val player = b.player!!
        val t1 = st.mon("t1")!!
        val full = arena.playerHpShown
        val r = heal(c)
        val hit = hitOf(r)
        assertTrue("the wild one hit it: ${t1.hp} of ${hit.hp}", t1.hp < hit.hp)
        // nothing plays yet: every way the panel draws the player side shows the HP before the hit, not the result
        b.panel!!.renderAll()
        assertEquals(hit.hp, arena.playerHpShown)
        b.panel!!.refreshMain()
        assertEquals(hit.hp, arena.playerHpShown)
        assertNotEquals(t1.hp, arena.playerHpShown)
        assertTrue(hit.hp >= full)
        // the host is on screen and idle: it takes them, once
        assertTrue(player.playPending())
        assertFalse(player.idle)
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
        assertFalse("a second look plays nothing", player.playPending())
        settle(b.root, "r14e-heal-playing", 300)
        settle(b.root, "r14e-heal-done", 4000)
        assertTrue(player.idle)
        assertEquals("the bar ends on the HP the saved game has", t1.hp, arena.playerHpShown)
        assertFalse(player.playPending())
    }

    @Test fun aSwitchedInPokemonComesInAtItsHpBeforeTheHit() {
        val edits: List<Pair<String, (TeamPage) -> ActionResult>> = listOf(
            "move the second member up" to { p -> p.act { it.moveInTeam("t2", -1) } },
            "send the main to the box" to { p -> p.act { it.toBox("t1") } },
            "release the main" to { p -> p.act { it.release("t1") } },
            "swap a box Pokémon in for the main (team full)" to { p -> p.act { it.swapIntoTeam("b8", "t1") } },
            "set another one as main at once" to { p -> p.act { it.setMain("t3") } },
        )
        for (reduce in listOf(false, true)) for ((what0, edit) in edits) {
            val what = if (reduce) "$what0 (reduced motion)" else what0
            val c = ctx()
            if (reduce) c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit() // nothing animates: the end state at once
            val b = BattleShotKit.bound(c)
            val arena = b.arena!!
            val oldMain = st.main!!.uid
            val r = edit(page(c))
            assertTrue(what, r.ok)
            val main = st.main!!
            assertNotEquals("$what: another main", oldMain, main.uid)
            val hit = hitOf(r)
            val waiting = GameRepo.pendingFightEvents()
            assertTrue("$what: the switch comes first", waiting.first() is GameEvent.MainChanged)
            assertTrue("$what: and the marker", waiting[1] is GameEvent.SwitchHit)
            b.panel!!.renderAll()
            assertEquals("$what: the incoming card", main.uid, arena.playerUid)
            assertEquals("$what: at the HP before the hit", hit.hp, arena.playerHpShown)
            assertTrue("$what: the saved game has the hit in it", main.hp < hit.hp)
            assertTrue(what, b.player!!.playPending())
            if (reduce) {
                assertTrue(what, b.player!!.idle)
                assertEquals("$what: the bar is on the saved HP", main.hp, arena.playerHpShown)
                assertEquals(what, main.uid, arena.playerUid)
            } else {
                assertFalse(what, b.player!!.idle)
                assertEquals("$what: the entrance keeps the HP before the hit", hit.hp, arena.playerHpShown)
                settle(b.root, "r14e-switch-playing", 300) // (the wild one's move effect itself does not finish inside the renderer, as in R10SwitchHitShots)
            }
            assertTrue(what, GameRepo.pendingFightEvents().isEmpty())
            assertFalse(what, b.player!!.playPending())
            if (reduce) c.getSharedPreferences("study", 0).edit().remove("reduceMotion").commit()
        }
    }

    @Test fun twoSwitchesMadeElsewherePlayEachPokemonAsItself() {
        // R14 review: each MainChanged drew the save's main, so after t1 -> t2 -> t3 off screen t3 showed during t2's entrance with t2's HP
        val c = ctx()
        val b = BattleShotKit.bound(c)
        val arena = b.arena!!
        val player = b.player!!
        val first = page(c).act { it.setMain("t2") }
        val second = page(c).act { it.setMain("t3") }
        assertTrue(first.ok); assertTrue(second.ok)
        val hit2 = hitOf(first)
        val hit3 = hitOf(second)
        val t3 = st.mon("t3")!!
        assertEquals(listOf("t2", "t3"), GameRepo.pendingFightEvents().filterIsInstance<GameEvent.MainChanged>().map { it.uid })
        assertEquals("t3", st.main!!.uid)
        assertTrue(player.playPending())
        assertFalse(player.idle)
        val seen = mutableListOf<Pair<String?, Int>>(arena.playerUid to arena.playerHpShown) // the first switch played at once
        settle(b.root, "r14e-two-switches-playing", 300)
        // the rest step by step, as the player's own timer does it (a switch's playback does not finish inside the renderer)
        val queue = getField(player, "queue") as ArrayDeque<*>
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 60) {
            val next = runCatching { getField(queue.first()!!, "e") }.getOrNull()
            setField(player, "busy", false); call(player, "pump")
            if (next is GameEvent.MainChanged) seen += arena.playerUid to arena.playerHpShown
        }
        setField(player, "busy", false); call(player, "pump")
        assertTrue(player.idle)
        assertEquals("each one comes in as itself, at its HP before its hit", listOf<Pair<String?, Int>>("t2" to hit2.hp, "t3" to hit3.hp), seen)
        assertEquals("then t3 stays, on the HP the saved game has", "t3", arena.playerUid)
        assertEquals(t3.hp, arena.playerHpShown)
    }

    @Test fun aFightThatEndedMeanwhilePlaysNothing() {
        val c = ctx()
        val b = BattleShotKit.bound(c)
        val fight = GameRepo.fightId()
        heal(c)
        val other = com.husarp.ankigo.game.TestData.mon(FakeGame.data, 54, 5, listOf("tackle"), "wild-2")
        st.battle = Battle(other, Tier.NORMAL, answers = 1, startedAt = 1L) // another wild one came
        assertFalse(b.player!!.playPending())
        assertTrue(b.player!!.idle)
        assertTrue("dropped, not kept for later", GameRepo.takePendingFightEvents(fight).isEmpty())

        heal(c) // in the new fight: kept for that one
        assertTrue(GameRepo.pendingFightEvents().isNotEmpty())
        st.battle = null // no battle now
        assertFalse(b.player!!.playPending())
        assertTrue(GameRepo.takePendingFightEvents().isEmpty())
    }

    @Test fun twoHostsOnScreenTheFirstTakesTheOtherShowsTheEndState() {
        val c = ctx()
        val a = BattleShotKit.bound(c)
        val panelB = BattlePanelView(c).also { it.renderAll() }
        val playerB = EventPlayer(panelB)
        val t1 = st.mon("t1")!!
        heal(c)
        assertTrue(a.player!!.playPending())
        assertFalse("nothing left for the second host", playerB.playPending())
        assertTrue(playerB.idle)
        panelB.refreshMain() // what its gameWatch does when the repo tells everybody the events were taken
        assertEquals(t1.hp, panelB.arena.playerHpShown)
    }

    @Test fun reducedMotionJustDrawsTheEndState() {
        val c = ctx()
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val b = BattleShotKit.bound(c)
        val t1 = st.mon("t1")!!
        heal(c)
        assertTrue(b.player!!.playPending())
        assertTrue(b.player!!.idle)
        assertEquals(t1.hp, b.arena!!.playerHpShown)
        c.getSharedPreferences("study", 0).edit().remove("reduceMotion").commit()
    }

    // ---------------------------------------------------------------- the panel comes on screen by itself

    @Test fun aPanelThatIsAttachedLaterTakesThemOnceWhenItComesOnScreen() {
        val c = ctx()
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit() // the end state at once (the wild one's move effect does not finish inside the renderer)
        val panel = BattlePanelView(c).also { it.renderAll() }
        val player = EventPlayer(panel)
        val t1 = st.mon("t1")!!
        val hit = hitOf(heal(c))
        assertEquals("not on screen yet: still waiting", 1, GameRepo.pendingFightEvents().count { it is GameEvent.SwitchHit })
        panel.renderAll()
        assertEquals("drawn off screen: the HP before the hit", hit.hp, panel.arena.playerHpShown)
        val frame = FrameLayout(c).apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "r14e-attach-done", 300)
        assertTrue("taken by the attach hook", GameRepo.pendingFightEvents().isEmpty())
        assertTrue(player.idle)
        assertEquals("and played: the bar is on the saved HP", t1.hp, panel.arena.playerHpShown)
        c.getSharedPreferences("study", 0).edit().remove("reduceMotion").commit()
    }

    // ---------------------------------------------------------------- the hosts' own hooks

    private class Live : BattleCard.Live {
        override val paused = false
        override fun hold(ctx: Context, owner: Any) {}
        override fun release(ctx: Context, owner: Any) {}
        override fun addSink(s: LiveSession.Sink) {}
        override fun removeSink(s: LiveSession.Sink) {}
        override fun addPauseListener(l: (Boolean) -> Unit) {}
        override fun removePauseListener(l: (Boolean) -> Unit) {}
    }

    private class ActivityHost(val activity: Activity) : PageHost {
        override val hostActivity: Activity? get() = activity
        override fun closePage() {}
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
    }

    @Test fun thePokemonTabsCardPlaysThemRightThereOnce() {
        val c = ctx()
        val p = TeamPage(c, ActivityHost(attach(Activity(), c)))
        p.cardSession = Live()
        p.preset(TeamSection.TEAM)
        pageScreen(c, p)
        call(p, "render")
        val card = getField(p, "battleCard") as BattleCard
        assertTrue(card.isLive)
        val player = getField(card, "player") as EventPlayer
        assertTrue(player.idle)
        val r = p.act { it.toBox("t1") } // a team edit on the very page the card sits on
        assertTrue(r.ok)
        assertTrue(GameRepo.pendingFightEvents().isNotEmpty())
        card.refresh() // what the page's render does after the action
        assertFalse(player.idle)
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
        card.refresh()
        card.refresh()
        assertTrue("still the one run, nothing doubled", GameRepo.pendingFightEvents().isEmpty())
    }

    @Test fun theStudyScreenPlaysThemWhenItComesBack() {
        val c = ctx()
        val act = attach(StudyActivity(), ShotCtx(c))
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        act.buildContent()
        call(act, "layout")
        val panel = getField(act, "panel") as BattlePanelView
        panel.renderAll()
        val events = getField(act, "events") as EventPlayer
        heal(c) // the Bag, opened from its menu
        act.refreshMain() // what onResume calls
        assertFalse(events.idle)
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
        act.refreshMain()
        assertTrue("a second resume plays nothing more", GameRepo.takePendingFightEvents().isEmpty())
    }

    @Test fun theFloatingWindowPlaysThemWhenItIsShownAgain() {
        val c = ctx()
        val self = object : android.content.ContextWrapper(c) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        call(v, "bind")
        val panel = getField(v, "panel") as BattlePanelView
        val player = getField(v, "player") as EventPlayer
        panel.renderAll()
        val t1 = st.mon("t1")!!
        val hp = heal(c).let { hitOf(it).hp }
        panel.renderAll()
        assertEquals("the window shows the HP before the hit", hp, panel.arena.playerHpShown)
        assertTrue(player.idle)
        v.refreshPicture() // the service calls it on every game change while the window is up
        assertFalse(player.idle)
        assertTrue(GameRepo.pendingFightEvents().isEmpty())
        v.refreshPicture()
        assertNotEquals(t1.hp, hp)
    }

    @Test fun aBusyHostLeavesThemWaiting() {
        val c = ctx()
        val b = BattleShotKit.bound(c)
        heal(c)
        b.player!!.enqueue(listOf(GameEvent.Info("one"), GameEvent.Info("two"))) // an animation still plays
        assertFalse(b.player!!.playPending())
        assertTrue("they wait", GameRepo.pendingFightEvents().isNotEmpty())
    }
}
