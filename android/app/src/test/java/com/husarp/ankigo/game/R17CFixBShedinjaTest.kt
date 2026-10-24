package com.husarp.ankigo.game

import com.husarp.ankigo.ui.DisplayNumbers
import com.husarp.ankigo.ui.WidgetLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-FIX item 1 (HIGH): the mainline Shedinja rule. A species with base HP 1 has 1 max HP at any level, IV and EV (so its Wonder Guard,
 * which only super-effective moves and Struggle get through, can be beaten). Every reader of the max HP still works at 1: the stat range,
 * newly made Pokémon, levelling, the defeat heal, the bench rest, healing items, the faster-start wild HP and the HP bar numbers.
 * (The Ankimon import / export rows are in game.ankimon.R17CFixBShedinjaAnkimonTest.)
 */
class R17CFixBShedinjaTest {
    private val now = 1_790_000_000_000L
    private val shed = TestData.species(292, "Shell", listOf(Type.BUG, Type.GHOST), TestData.stats(1, 90, 45, 30, 30, 40), growthRate = 2)
    private val data = TestData.data(TestData.world().species.values.toList() + shed)

    private fun maxHp(m: Mon) = StatCalc.maxHp(data, m)

    @Test fun baseHpOneIsOneHpAtAnyLevelIvAndEv() {
        for (level in listOf(1, 5, 20, 50, 100)) {
            assertEquals("level $level", 1, StatCalc.hp(1, 0, 0, level))
            assertEquals("level $level, best IV and EV", 1, StatCalc.hp(1, 31, 252, level))
        }
        val m = TestData.mon(data, 292, 60)
        m.evs = Stats.ZERO.copy(hp = 252)
        assertEquals(1, maxHp(m))
        assertEquals(1, StatCalc.stats(data.sp(292), m).hp)
        assertEquals(1, StatCalc.stats(data.sp(292), m, 100).hp)
    }

    @Test fun otherBaseHpsAreUntouched() {
        assertEquals(114, StatCalc.hp(2, 0, 0, 100))      // 10 + 100 + floor(4)
        assertEquals(208, StatCalc.hp(2, 31, 252, 100))
        assertEquals(110, StatCalc.hp(0, 0, 0, 100))      // the rule is exactly "base 1"
        assertEquals(1, StatCalc.SHEDINJA_BASE_HP)
    }

    @Test fun theRangeOfBaseHpOneIsOneToOne() {
        assertEquals(1..1, StatCalc.range(1, Stat.HP))
        assertEquals(1..1, StatCalc.range(1, Stat.HP, 50))
        assertEquals(6..111, StatCalc.range(1, Stat.DEF))   // the other stats of a base 1 are the normal formula
        assertEquals(114..208, StatCalc.range(2, Stat.HP))
    }

    @Test fun aNewShedinjaHasFullHpOfOne() {
        val m = Progression.newMon(data, 292, 25, Random(3), now)
        assertEquals(1, m.hp)
        assertEquals(1, maxHp(m))
    }

    @Test fun healingAtMaxHpOneDoesNothingAndNeverRevives() {
        val m = TestData.mon(data, 292, 30)
        val ev = mutableListOf<GameEvent>()
        Healing.healMain(data, m, HealReason.DEFEAT, ev)
        assertTrue("already full: no heal event", ev.isEmpty())
        assertEquals(1, m.hp)
        m.hp = 0                                            // a fainted main is healed by Game.answer, not here
        Healing.healMain(data, m, HealReason.DEFEAT, ev)
        assertTrue(ev.isEmpty()); assertEquals(0, m.hp)
        assertEquals(1, Healing.share(1, Tuning.DEFEAT_HEAL_PERCENT))   // at least 1 HP, never 0
    }

    @Test fun levelUpHealAtMaxHpOne() {
        assertEquals(1, Healing.levelUpHp(1, 1, 1))
        assertEquals(0, Healing.levelUpHp(0, 1, 1))         // a fainted one stays fainted
        val s = TestData.state(data, 292, 10, listOf("tackle"))
        val m = s.main!!
        val ev = mutableListOf<GameEvent>()
        val gained = Progression.gainXp(data, s, m, 100_000, ev)
        assertTrue(gained > 0)
        assertEquals(1, maxHp(m)); assertEquals(1, m.hp)
        assertTrue(ev.filterIsInstance<GameEvent.LevelUp>().all { it.hp == 1 && it.maxHp == 1 })
        assertTrue("it was full: no heal line", ev.none { it is GameEvent.Healed })
    }

    @Test fun theBenchRestOfAFaintedShedinjaBuildsUpToOneHp() {
        val m = TestData.mon(data, 292, 30)
        m.hp = 0
        repeat(Tuning.BENCH_REST_DIVISOR - 1) { Healing.rest(data, m) }
        assertEquals(0, m.hp)
        assertEquals(Tuning.BENCH_REST_DIVISOR - 1, m.restFrac)
        Healing.rest(data, m)
        assertEquals(1, m.hp); assertEquals(0, m.restFrac)
        Healing.rest(data, m)                               // at max HP nothing builds up
        assertEquals(1, m.hp); assertEquals(0, m.restFrac)
    }

    @Test fun healingItemsAtMaxHpOne() {
        val s = TestData.state(data, 292, 30, listOf("tackle"))
        Economy.give(s, "potion", 2); Economy.give(s, "full-restore", 2)
        val main = s.main!!
        assertFalse("a potion can't be used on a Pokémon at full HP", ItemUse.canUse(data, s, "potion", main))
        val r = ItemUse.use(data, s, "potion", main.uid, TestData.rng(), now)
        assertFalse(r.ok); assertEquals(2, s.count("potion"))
        // Full Restore still cures a status in a fight, and heals nothing
        s.battle = Battle(TestData.mon(data, 19, 12, listOf("tackle"), uid = "wild"), Tier.NORMAL, startedAt = now)
        s.battle!!.player.status = "brn"
        assertTrue(ItemUse.canUse(data, s, "full-restore", main))
        val cure = ItemUse.use(data, s, "full-restore", main.uid, TestData.rng(), now)
        assertTrue(cure.message, cure.ok)
        assertEquals("Full Restore cured Shell", cure.message)
        assertEquals(1, main.hp)
        // a potion on a hurt one (0 HP) heals the 1 HP it is missing, not its 20
        main.hp = 0
        val heal = ItemUse.use(data, s, "potion", main.uid, TestData.rng(), now)
        assertTrue(heal.ok); assertEquals(1, main.hp)
        assertEquals("Potion healed Shell by 1 HP", heal.message)
    }

    @Test fun theFasterStartWildHpKeepsOneHp() {
        assertEquals(1, Tuning.wildHp(1, 5, true))
        assertEquals(1, Tuning.wildHp(1, 5, false))
        val wild = TestData.mon(data, 292, 22, uid = "wild")
        val b = Battle(wild, Tier.NORMAL, startedAt = now, wildMaxHp = null)
        assertEquals(1, b.maxWildHp(data))
        // an encounter-time override of 0 (an old or damaged save) never gives a 0 bar
        assertEquals(1, Battle(wild, Tier.NORMAL, startedAt = now, wildMaxHp = 0).maxWildHp(data))
    }

    @Test fun theHpBarNumbersAtMaxHpOne() {
        assertEquals(1f, WidgetLogic.hpFrac(1, 1), 0f)
        assertEquals(0f, WidgetLogic.hpFrac(0, 1), 0f)
        assertEquals("1 / 1", DisplayNumbers.hp(1, 1))
        assertEquals("0 / 1", DisplayNumbers.hp(0, 1))
    }
}
