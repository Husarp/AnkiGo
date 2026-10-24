package com.husarp.ankigo.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.random.Random

/**
 * R17C-6: Full Restore heals all HP AND cures the Main's battle status in a fight (a STATUS_CURED effect, so the badge goes at once); it
 * works at full HP when the Main has a status; Max Potion and the other potions heal HP only; nothing to cure outside a fight or on the bench.
 */
class R17C6FullRestoreTest {
    private val data = TestData.world()
    private val now = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private lateinit var savedZone: TimeZone
    @Before fun utcZone() { savedZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restoreZone() = TimeZone.setDefault(savedZone)

    /** Main t0 (Lv 20) with [status] in a fight against Ratty; a bench member t1 (hurt). */
    private fun game(status: String?, hurt: Boolean = true, fight: Boolean = true): Game {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.team.clear(); s.mons.clear()
        for ((i, sp) in listOf(25, 16).withIndex()) {
            val m = TestData.mon(data, sp, 20, listOf("tackle"), uid = "t$i")
            if (hurt || i == 1) m.hp = StatCalc.maxHp(data, m) / 2
            s.mons += m; s.team += m.uid
        }
        val g = Game(data, s, Random(7)) { now }
        if (fight) s.battle = Battle(TestData.mon(data, 19, 12, listOf("tackle"), uid = "wild"), Tier.NORMAL, startedAt = now, rounds = 0, answers = 0)
        s.battle?.player?.status = status
        Economy.give(s, "full-restore", 3); Economy.give(s, "max-potion", 3); Economy.give(s, "potion", 3)
        return g
    }

    @Test fun fullRestoreHealsAndCuresEveryStatus() {
        for ((key, text) in listOf(
            "brn" to "Sparkmouse's burn was healed.", "psn" to "Sparkmouse's poison was healed.", "tox" to "Sparkmouse's poison was healed.",
            "par" to "Sparkmouse's paralysis was healed.", "slp" to "Sparkmouse woke up.", "frz" to "Sparkmouse thawed out.",
        )) {
            val g = game(key)
            val main = g.state.main!!
            g.state.battle!!.player.statusTurns = 3
            val r = g.useItem("full-restore", main.uid)
            assertTrue(key + r.message, r.ok)
            assertEquals(StatCalc.maxHp(data, main), main.hp - 0)   // the item healed it fully (it was at half HP)
            assertNull(key, g.state.battle!!.player.status)
            assertEquals(0, g.state.battle!!.player.statusTurns)
            assertEquals(2, g.state.count("full-restore"))
            val fx = r.events.filterIsInstance<GameEvent.Effect>().single { it.kind == EffectKind.STATUS_CURED }
            assertTrue(fx.onPlayer); assertNull("the badge goes at once: the event carries no status", fx.status)
            assertEquals(key, fx.stat)
            assertEquals(text, fx.text)
        }
    }

    @Test fun itHealsAllHpTooAndSaysBoth() {
        val g = game("brn")
        val main = g.state.main!!
        main.hp = 5
        val r = ItemUse.use(data, g.state, "full-restore", main.uid, TestData.rng(), now)
        assertTrue(r.ok)
        assertEquals(StatCalc.maxHp(data, main), main.hp)
        assertTrue(r.message, r.message.startsWith("Full Restore healed Sparkmouse by") && r.message.endsWith("and cured its status"))
        assertEquals(StatCalc.maxHp(data, main), r.events.filterIsInstance<GameEvent.Effect>().single().hp)
    }

    @Test fun atFullHpItStillWorksWithAStatusInAFight() {
        val g = game("par", hurt = false)
        val main = g.state.main!!
        assertEquals(StatCalc.maxHp(data, main), main.hp)
        assertTrue(ItemUse.canUse(data, g.state, "full-restore", main))
        assertTrue(ItemUse.canUse(data, g.state, "full-restore", null))   // no target = the main
        val r = ItemUse.use(data, g.state, "full-restore", main.uid, TestData.rng(), now)
        assertTrue(r.message, r.ok)
        assertEquals("Full Restore cured Sparkmouse", r.message)
        assertNull(g.state.battle!!.player.status)
        assertEquals(2, g.state.count("full-restore"))
    }

    @Test fun atFullHpWithoutAStatusItIsRefused() {
        val g = game(null, hurt = false)
        assertFalse(ItemUse.canUse(data, g.state, "full-restore", g.state.main))
        val r = ItemUse.use(data, g.state, "full-restore", "t0", TestData.rng(), now)
        assertFalse(r.ok); assertEquals("Sparkmouse is already healthy.", r.message)
        assertEquals(3, g.state.count("full-restore"))
    }

    @Test fun maxPotionAndPotionHealOnlyAndNeedMissingHp() {
        val g = game("brn")
        val main = g.state.main!!
        val r = ItemUse.use(data, g.state, "max-potion", main.uid, TestData.rng(), now)
        assertTrue(r.ok); assertEquals(StatCalc.maxHp(data, main), main.hp)
        assertEquals("brn", g.state.battle!!.player.status)
        assertTrue(r.events.none { it is GameEvent.Effect })
        // at full HP with a status, Max Potion and a Potion still can't be used
        assertFalse(ItemUse.canUse(data, g.state, "max-potion", main))
        assertFalse(ItemUse.canUse(data, g.state, "potion", main))
        assertEquals("Sparkmouse is already healthy.", ItemUse.use(data, g.state, "max-potion", main.uid, TestData.rng(), now).message)
    }

    @Test fun onTheBenchOrOutsideAFightThereIsNoStatusToCure() {
        val g = game("brn")
        val bench = g.state.mon("t1")!!
        val r = ItemUse.use(data, g.state, "full-restore", "t1", TestData.rng(), now)
        assertTrue(r.ok); assertEquals(StatCalc.maxHp(data, bench), bench.hp)
        assertEquals("the Main's burn stays", "brn", g.state.battle!!.player.status)
        assertTrue(r.events.none { it is GameEvent.Effect })
        assertFalse(ItemUse.canUse(data, g.state, "full-restore", bench))   // the bench member is full now, and has no status

        val none = game(null, hurt = false, fight = false)
        assertFalse(ItemUse.canUse(data, none.state, "full-restore", none.state.main))
    }

    @Test fun usedOnTheMainInAFightItTakesTheTurnLikeAnyHeal() {
        val g = game("brn")
        g.state.battle!!.rounds = 1
        val main = g.state.main!!
        val r = g.useItem("full-restore", main.uid)
        assertTrue(r.ok)
        assertEquals(1, r.events.filterIsInstance<GameEvent.SwitchHit>().size)
        assertEquals(1, r.events.filterIsInstance<GameEvent.Attack>().size)
    }

    @Test fun theTextsSayWhatEachOneDoes() {
        fun fact(id: String, title: String) = ItemInfo.facts(data, id).first { it.title == title }.text
        val fr = fact("full-restore", "What it does")
        assertTrue(fr, fr.contains("**all HP**") && fr.contains("**cures**") && !fr.contains("no status problems"))
        assertTrue(fact("full-restore", "How to use").contains("except on your main when it has a status in a fight"))
        val mp = fact("max-potion", "What it does")
        assertTrue(mp, mp.contains("**all HP**") && mp.contains("**doesn't** cure a status"))
        assertEquals("All HP + status", ItemInfo.short(data, "full-restore"))
        assertEquals("Heals all HP, cures a status", ItemInfo.line(data, "full-restore"))
        assertEquals("Heals all HP", ItemInfo.short(data, "max-potion"))
        assertEquals("Heals all HP and cures a status", Items.def("full-restore")!!.note)
        assertEquals("Heals all HP", Items.def("max-potion")!!.note)
        for (id in listOf("full-restore", "max-potion", "potion")) assertTrue(id, fact(id, "In a fight").contains("**at once**"))
    }
}
