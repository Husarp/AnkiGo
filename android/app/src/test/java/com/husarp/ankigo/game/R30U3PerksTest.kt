package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.DetailMath
import com.husarp.ankigo.ui.CompareTable
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.Cell
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R30U3PerksTest {
    private val data = TestData.world()
    private fun mon(nature: Nature, species: Int = 25, uid: String = "main", level: Int = 40): Mon {
        val m = TestData.mon(data, species, level, uid = uid)
        return Mon(m.uid, m.species, m.name, m.level, 0, m.ivs, m.evs, nature, m.gender, false, "", m.moves, m.hp)
    }
    private fun state(main: Mon, holder: Mon? = null) = GameState(starterChosen = true).also {
        it.mons += main; it.team += main.uid
        if (holder != null) { it.mons += holder; it.settings.xpShareUid = holder.uid }
        it.battle = Battle(TestData.mon(data, 16, 10, uid = "wild"), Tier.NORMAL)
    }
    private fun reward(s: GameState, caught: Boolean): List<GameEvent> = mutableListOf<GameEvent>().also {
        if (caught) Progression.rewardCatch(data, s, s.main!!, s.battle!!.wild, 1, Random(1), it)
        else Progression.rewardDefeat(data, s, s.main!!, s.battle!!.wild, 1, Random(1), it)
    }
    private fun List<GameEvent>.xp(uid: String) = filterIsInstance<GameEvent.XpGained>().singleOrNull { it.uid == uid }?.xp ?: 0

    @Test fun seededRollsCoverExactlyThePickedBoundsForAllNatures() {
        for (n in Nature.entries) {
            val range = when (n) { Nature.SERIOUS -> 90..100; Nature.QUIRKY -> 80..105; else -> 85..100 }
            val rng = Random(468)
            val rolls = List(5000) { NaturePerks.damageRoll(n, rng) }
            assertEquals(n.label, range.toSet(), rolls.toSet())
        }
    }

    @Test fun damageUsesTheAttackersNatureImmediatelyOnEitherSide() {
        for (n in listOf(Nature.SERIOUS, Nature.QUIRKY, Nature.BASHFUL)) for (isMain in listOf(true, false)) {
            val attacker = mon(n, 25)
            val defender = mon(Nature.SERIOUS, 54, "foe")
            val move = data.move("earthquake")!!
            val range = when (n) { Nature.SERIOUS -> 90..100; Nature.QUIRKY -> 80..105; else -> 85..100 }
            val legalDamage = range.map { roll -> BattleEngine.damage(data, attacker, BattleSide(), defender, BattleSide(), move,
                Random(1), forceCrit = false, forceRoll = roll, attackerIsMain = isMain).damage }.toSet()
            val rng = Random(498)
            val actual = (1..1000).map { BattleEngine.damage(data, attacker, BattleSide(), defender, BattleSide(), move,
                rng, forceCrit = false, attackerIsMain = isMain).damage }.toSet()
            assertEquals("$n / $isMain", legalDamage, actual)
        }
    }

    @Test fun fixedDamageAndImmunityDoNotUseNatureRolls() {
        for (n in listOf(Nature.SERIOUS, Nature.QUIRKY)) {
            val a = mon(n); val d = mon(Nature.BASHFUL, 54, "foe")
            for (seed in 1..50) for ((id, expected) in listOf("dragonrage" to 40, "seismictoss" to a.level, "superfang" to d.hp / 2, "fissure" to d.hp)) {
                assertEquals(expected, BattleEngine.damage(data, a, BattleSide(), d, BattleSide(), data.move(id)!!, Random(seed)).damage)
            }
            assertEquals(0, BattleEngine.damage(data, a, BattleSide(), mon(n, 92, "ghost"), BattleSide(), data.move("tackle")!!, Random(1)).damage)
        }
    }

    @Test fun bashfulAppliesAfterSplitEggAndBondOnCatchAndDefeatWithPracticeScaling() {
        for (caught in listOf(false, true)) for (practice in listOf(false, true)) {
            val main = mon(Nature.BASHFUL)
            val holder = mon(Nature.BASHFUL, uid = "holder").apply { heldItem = "lucky-egg"; bondPoints = 250 }
            val s = state(main, holder).apply { practiceRewards = practice }
            val half = (Progression.xpForDefeat(data.sp(16), 10) * Tuning.XP_SHARE_PART).toInt()
            val base = Bond.bonusXp((half * Tuning.LUCKY_EGG).toInt(), holder.bondPoints)
            val events = reward(s, caught)
            assertEquals(Tuning.studyXp(half, practice), events.xp(main.uid))
            assertEquals(Tuning.studyXp(base, practice) + base * (if (practice) 4 else 8) / 200, events.xp(holder.uid))
            assertEquals(base * (if (practice) 4 else 8) % 200, holder.natureXpCarry)
            assertEquals(Stats.ZERO, holder.evs); assertEquals(250, holder.bondPoints)
            assertEquals(0, main.natureXpCarry)
        }
    }

    @Test fun bashfulFractionalCarryPersistsAndBelongsOnlyToTheRecipient() {
        val holder = mon(Nature.BASHFUL, uid = "holder")
        val s = state(mon(Nature.SERIOUS), holder)
        assertEquals(1, NaturePerks.sharedXp(s, holder, 1)); assertEquals(8, holder.natureXpCarry)
        val back = SaveCodec.decode(SaveCodec.encode(s)); val h = back.mon(holder.uid)!!
        assertEquals(8, h.natureXpCarry)
        assertEquals(25, (1..24).sumOf { NaturePerks.sharedXp(back, h, 1) })
        assertEquals(0, h.natureXpCarry)
        back.practiceRewards = true
        assertEquals(52, (1..50).sumOf { NaturePerks.sharedXp(back, h, 2) })
        back.settings.xpShareUid = back.main!!.uid
        assertEquals(100, NaturePerks.sharedXp(back, h, 200))
        h.level = 100; back.settings.xpShareUid = h.uid
        val carry = h.natureXpCarry
        assertEquals(100, NaturePerks.sharedXp(back, h, 200)); assertEquals(carry, h.natureXpCarry)
    }

    @Test fun everstoneEligibilityUsesBranchesRatherThanReadinessOrPriorEvolution() {
        // 3 is fully evolved; 150 is single-stage; 1 and 25 still have branches, even when unready.
        for (species in listOf(3, 150, 1, 25)) for (onMain in listOf(true, false)) for (caught in listOf(true, false)) {
            val main = mon(Nature.SERIOUS, if (onMain) species else 54)
            val holder = mon(Nature.SERIOUS, if (onMain) 54 else species, "holder")
            val target = if (onMain) main else holder
            target.heldItem = "everstone"; target.bondPoints = 250
            val s = state(main, holder)
            val half = (Progression.xpForDefeat(data.sp(16), 10) * Tuning.XP_SHARE_PART).toInt()
            val eligible = species in listOf(3, 150)
            val expected = Bond.bonusXp(if (eligible) half * 115 / 100 else half, 250)
            val events = reward(s, caught)
            assertEquals("$species / $onMain / $caught", expected, events.xp(target.uid))
            assertEquals(half, events.xp(if (onMain) holder.uid else main.uid))
            assertEquals("everstone", target.heldItem)
        }
    }

    @Test fun everstoneWorksWithoutShareAndStopsAtTheLevelCapForEitherRecipient() {
        val m = mon(Nature.SERIOUS, 3).apply { heldItem = "everstone"; bondPoints = 250 }
        val s = state(m)
        val xp = Progression.xpForDefeat(data.sp(16), 10)
        assertEquals(Bond.bonusXp(xp * 115 / 100, 250), reward(s, true).xp(m.uid))
        val holder = mon(Nature.BASHFUL, 150, "holder", level = 100).apply { heldItem = "everstone"; natureXpCarry = 196 }
        s.mons += holder; s.settings.xpShareUid = holder.uid
        m.level = 100
        assertTrue(reward(s, false).none { it is GameEvent.XpGained })
        assertEquals(196, holder.natureXpCarry)
        assertEquals("everstone", holder.heldItem)
    }

    @Test fun everstoneThenBondThenBashfulThenPracticeStackInThatOrder() {
        for (caught in listOf(true, false)) for (practice in listOf(true, false)) {
            val holder = mon(Nature.BASHFUL, 3, "holder").apply { heldItem = "everstone"; bondPoints = 500 }
            val s = state(mon(Nature.SERIOUS), holder).apply { practiceRewards = practice }
            val half = (Progression.xpForDefeat(data.sp(16), 10) * Tuning.XP_SHARE_PART).toInt()
            val base = Bond.bonusXp(half * 115 / 100, holder.bondPoints)
            assertEquals(Tuning.studyXp(base, practice) + base * (if (practice) 4 else 8) / 200, reward(s, caught).xp(holder.uid))
        }
    }

    @Test fun everstoneSuppressionAffectsFighterAndNeverRemovesHeldItems() {
        for (suppression in listOf("embargo", "magicroom")) {
            val main = mon(Nature.SERIOUS, 3).apply { heldItem = "everstone" }
            val holder = mon(Nature.SERIOUS, 150, "holder").apply { heldItem = "everstone" }
            val s = state(main, holder)
            if (suppression == "embargo") s.battle!!.player.volatiles[suppression] = 5
            else s.battle!!.field.rooms[suppression] = 5
            val half = (Progression.xpForDefeat(data.sp(16), 10) * Tuning.XP_SHARE_PART).toInt()
            val events = reward(s, false)
            assertEquals(half, events.xp(main.uid)); assertEquals(half * 115 / 100, events.xp(holder.uid))
            assertEquals("everstone", main.heldItem); assertEquals("everstone", holder.heldItem)
        }
    }

    @Test fun oldSaveRetainsEverstoneAndDirectXpHasNoItemBonus() {
        val m = mon(Nature.SERIOUS, 3).apply { heldItem = "everstone" }
        val s = SaveCodec.decode(SaveCodec.encode(state(m)).replace(Regex("\\\"natureXpCarry\\\":0,?"), ""))
        assertEquals("everstone", s.main!!.heldItem)
        val events = mutableListOf<GameEvent>()
        Progression.gainXp(data, s, s.main!!, 100, events)
        assertEquals(100, events.xp(m.uid))
        s.main!!.level = 100
        assertEquals(0, reward(s, true).xp(m.uid))
    }

    @Test fun pickedNatureAndEverstoneDescriptionsExplainTheNewRules() {
        for (n in listOf(Nature.SERIOUS, Nature.BASHFUL, Nature.QUIRKY)) {
            assertTrue(DetailMath.natureEffect(n).contains(NaturePerks.description(n)!!))
            val m = mon(n); val s = state(m)
            val table = CompareTable.build(data, s, listOf(ComparePick.Owned(m.uid)), false)
            assertTrue((table.row("nature").cells.single() as Cell.Text).sub.contains(NaturePerks.description(n)))
        }
        val facts = ItemInfo.facts(data, "everstone").joinToString { it.text }
        for (limit in listOf("no evolution branch", "15%", "Single-stage", "Exp. Share", "round down", "Bond bonus", "practice", "level-100", "Embargo", "Magic Room", "not consumed", "allowed while holding")) {
            assertTrue(limit, facts.contains(limit))
        }
        assertFalse(facts.contains("never evolves"))
    }
}
