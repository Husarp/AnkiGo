package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

/**
 * R17C-2 (PLAN 240): every win gives the Main the same EVs and friendship, whether it is a defeat, an auto catch or a ball catch.
 * Before, only a defeat did, and the catch rules turn most wins into catches, so the Main's EVs stayed at 0.
 */
class R17C2RewardsTest {
    private val data = TestData.world()
    private val noon: Long = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 13, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    private fun ev() = mutableListOf<GameEvent>()
    private fun st(species: Int = 1, level: Int = 5): Pair<GameState, Mon> {
        val s = TestData.state(data, species, level, listOf("tackle"))
        return s to s.main!!
    }
    private fun wild16(level: Int = 10) = TestData.mon(data, 16, level, uid = "wild") // yields 1 HP EV
    private inline fun <reified T : GameEvent> List<GameEvent>.of() = filterIsInstance<T>()

    private fun win(how: String, s: GameState, m: Mon, w: Mon, rng: Random = Random(1), e: MutableList<GameEvent> = ev()) {
        if (how == "defeat") Progression.rewardDefeat(data, s, m, w, noon, rng, e) else Progression.rewardCatch(data, s, m, w, noon, rng, e)
    }

    // ---- the same bonus for a defeat and a catch ----

    @Test fun aDefeatAndACatchWinGiveTheSameEvsAndFriendship() {
        for (how in listOf("defeat", "catch")) {
            val (s, m) = st()
            win(how, s, m, wild16(), Random(7))
            assertEquals(how, Stats(1, 0, 0, 0, 0, 0), m.evs)
            assertEquals(how, Tuning.FRIENDSHIP_GAIN.random(Random(7)), m.friendship)
        }
    }

    @Test fun severalWinsAddUp() {
        val (s, m) = st()
        repeat(3) { win("catch", s, m, wild16()) }
        repeat(2) { win("defeat", s, m, wild16()) }
        assertEquals(5, m.evs.hp)
        assertTrue(m.friendship in 25..45)
    }

    @Test fun machoBraceAndPowerItemsApplyToACatchWin() {
        val (s, m) = st()
        m.heldItem = "macho-brace"
        win("catch", s, m, wild16())
        assertEquals(2, m.evs.hp)
        val (s2, m2) = st()
        m2.heldItem = "power-anklet"
        win("catch", s2, m2, wild16())
        assertEquals(Stats(1, 0, 0, 0, 0, 8), m2.evs)
    }

    @Test fun sootheBellApplies() {
        for (how in listOf("defeat", "catch")) {
            val (s, m) = st()
            m.heldItem = "soothe-bell"
            win(how, s, m, wild16(), Random(7))
            assertEquals(how, (Tuning.FRIENDSHIP_GAIN.random(Random(7)) * Tuning.SOOTHE_BELL).toInt(), m.friendship)
        }
    }

    @Test fun theExpShareHolderGetsXpButNoEvsOrFriendship() {
        for (how in listOf("defeat", "catch")) {
            val (s, m) = st()
            val h = TestData.mon(data, 16, 5, uid = "holder")
            s.mons += h; s.team += h.uid
            s.settings.xpShareUid = "holder"
            val e = ev()
            win(how, s, m, wild16(), e = e)
            assertTrue(how, e.of<GameEvent.XpGained>().any { it.uid == "holder" && it.xp > 0 })
            assertEquals(how, Stats.ZERO, h.evs); assertEquals(how, 0, h.friendship)
            assertEquals(how, 1, m.evs.hp)
        }
    }

    // ---- the cap ----

    @Test fun friendshipStopsAt255() {
        for (how in listOf("defeat", "catch")) {
            val (s, m) = st()
            m.friendship = 252
            win(how, s, m, wild16())
            assertEquals(how, 255, m.friendship)
            win(how, s, m, wild16())
            assertEquals(how, 255, m.friendship)
        }
        assertEquals(255, Tuning.FRIENDSHIP_MAX)
    }

    @Test fun aHigherNumberFromAnOlderSaveIsCutWhenItNextChanges() {
        val (s, m) = st()
        m.friendship = 300
        win("catch", s, m, wild16())
        assertEquals(255, m.friendship)
    }

    // ---- evolution after a catch win ----

    @Test fun aCatchWinCanEvolveByFriendship() {
        val (s, m) = st(172, 5)
        m.friendship = 159
        val e = ev()
        Progression.rewardCatch(data, s, m, wild16(1), noon, Random(1), e) // +5..9 -> 164 or more
        assertEquals(25, m.species)
        assertTrue(e.any { it is GameEvent.Evolved })
    }

    @Test fun aCatchWinStillEvolvesByLevelFirst() {
        val (s, m) = st(1, 15)
        val e = ev()
        Progression.rewardCatch(data, s, m, TestData.mon(data, 150, 50, uid = "wild"), noon, Random(1), e)
        assertEquals(2, m.species)
        assertTrue(e.any { it is GameEvent.Evolved })
    }

    @Test fun anEverstoneStopsTheFriendshipEvolutionOfACatchWin() {
        val (s, m) = st(172, 5)
        m.friendship = 159; m.heldItem = "everstone"
        Progression.rewardCatch(data, s, m, wild16(1), noon, Random(1), ev())
        assertEquals(172, m.species)
    }

    // ---- through the game: auto catch, ball catch, defeat ----

    private fun game(state: GameState, seed: Int = 1) = Game(data, state, Random(seed)) { noon }
    private fun Game.fight(species: Int, level: Int = 12) {
        state.battle = Battle(TestData.mon(data, species, level, listOf("tackle"), uid = "wild").also { it.hp = 1 }, Tier.NORMAL, startedAt = noon)
    }
    private fun Game.finish(): List<GameEvent> { onAnswer(Answer(1, 3, noon)); return onAnswer(Answer(2, 3, noon)) }

    @Test fun anAutoCatchRaisesTheMainsEvsAndFriendship() {
        val g = game(TestData.state(data, 25, 20, listOf("tackle")))
        g.fight(54) // a new species: caught
        val ev = g.finish()
        assertEquals(1, ev.of<GameEvent.Caught>().size)
        val m = g.state.main!!
        assertTrue("EVs ${m.evs}", m.evs.total > 0)
        assertTrue("friendship ${m.friendship}", m.friendship in Tuning.FRIENDSHIP_GAIN)
        assertEquals(1, m.defeats)
    }

    @Test fun aDefeatRaisesTheMainsEvsAndFriendship() {
        val g = game(TestData.state(data, 25, 20, listOf("tackle")))
        g.state.caught += 54
        g.fight(54) // known species: the default rule defeats it
        val ev = g.finish()
        assertEquals(1, ev.of<GameEvent.Defeated>().size)
        val m = g.state.main!!
        assertTrue(m.evs.total > 0); assertTrue(m.friendship in Tuning.FRIENDSHIP_GAIN)
    }

    @Test fun aBallCatchRaisesTheMainsEvsAndFriendship() {
        val g = game(TestData.state(data, 25, 20, listOf("tackle")))
        Economy.give(g.state, "master-ball", 1)
        g.fight(54)
        val ev = g.throwBall("master-ball")
        assertEquals(1, ev.of<GameEvent.Caught>().size)
        val m = g.state.main!!
        assertTrue(m.evs.total > 0); assertTrue(m.friendship in Tuning.FRIENDSHIP_GAIN)
    }

    @Test fun aMissedBallGivesNothing() {
        val g = game(TestData.state(data, 25, 20, listOf("tackle")), seed = (0..1000).first { Random(it).nextInt(100) >= 20 })
        Economy.give(g.state, "poke-ball", 1)
        g.fight(54)
        g.throwBall("poke-ball")
        val m = g.state.main!!
        assertEquals(Stats.ZERO, m.evs); assertEquals(0, m.friendship)
    }

    @Test fun theCaughtCopyStartsFreshAsBefore() {
        val g = game(TestData.state(data, 25, 20, listOf("tackle")))
        g.fight(54)
        val caught = g.finish().of<GameEvent.Caught>().single()
        val c = g.state.mon(caught.uid)!!
        assertEquals(Stats.ZERO, c.evs); assertEquals(0, c.friendship)
        assertFalse(c.uid == g.state.main!!.uid)
    }

    // ---- the item ----

    @Test fun sootheBellIsAHeldItemYouCanBuyAndFind() {
        val def = Items.def("soothe-bell")!!
        assertEquals(ItemKind.HELD, def.kind)
        assertEquals("Soothe Bell", Items.name("soothe-bell"))
        assertTrue("soothe-bell" in Items.dropPool())
        assertTrue(def.note.contains("friendship"))
    }

    // ---- the save keeps what the wins gave ----

    @Test fun evsAndFriendshipSurviveASaveRoundTrip() {
        val (s, m) = st()
        m.heldItem = "soothe-bell"
        repeat(4) { win("catch", s, m, wild16()) }
        val back = com.husarp.ankigo.game.save.SaveCodec.decode(com.husarp.ankigo.game.save.SaveCodec.encode(s)).main!!
        assertEquals(Stats(4, 0, 0, 0, 0, 0), back.evs)
        assertEquals(m.friendship, back.friendship)
        assertEquals("soothe-bell", back.heldItem)
    }
}
