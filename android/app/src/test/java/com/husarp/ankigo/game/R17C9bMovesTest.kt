package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C9b.attacks
import com.husarp.ankigo.game.R17C9b.effects
import com.husarp.ankigo.game.R17C9b.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17C-9b: the seven ability-changing moves (Entrainment, Gastro Acid, Role Play, Simple Beam, Skill Swap, Worry Seed, Doodle): who gets which ability,
 * which abilities can't be changed or copied (Showdown's flags), that the new ability starts and cures what it stops, and that it lasts for the fight.
 */
class R17C9bMovesTest {
    private fun abilityOf(b: Battle, m: Mon, main: Boolean) = SimpleView.of(R17C9b.data, b, m).ability(main)

    private fun failed(e: List<GameEvent>) = e.attacks().single().failed

    @Test fun entrainmentGivesTheTargetTheUsersAbility() {
        val w = R17C9b.wild("Levitate", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Static", "entrainment")
        val e = R17C9b.round(m, b, "entrainment")
        assertEquals("static", abilityOf(b, m, false))
        assertEquals("static", abilityOf(b, m, true))
        assertFalse(failed(e))
        assertTrue(e.lines().any { "acquired Static!" in it })
        assertEquals("the float label is the new ability", "Static", e.effects().first { it.text.contains("acquired") }.label)
        // the Levitate is gone: Ground moves hit now
        val w2 = R17C9b.wild("Levitate", 801, "tackle"); val b2 = R17C9b.battle(w2); val m2 = R17C9b.main("Static", "entrainment", "earthquake")
        R17C9b.round(m2, b2, "entrainment")
        R17C9b.round(m2, b2, "earthquake")
        assertTrue(w2.hp < R17C9b.maxHp(w2))
    }

    @Test fun entrainmentFailsWhenTheAbilitiesCannotChange() {
        for ((mine, theirs) in listOf("Trace" to "Levitate", "Static" to "Comatose", "Static" to "Static", "" to "Levitate", "Wonder Guard" to "Levitate", "Neutralizing Gas" to "Static")) {
            val w = R17C9b.wild(theirs, 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main(mine, "entrainment")
            val e = R17C9b.round(m, b, "entrainment")
            assertTrue("$mine -> $theirs fails", failed(e))
            assertEquals("$mine -> $theirs", BattleView.normalizeAbility(theirs), AbilityMoves.current(SimpleView.of(R17C9b.data, b, m), false)) // current: Neutralizing Gas puts the effective one out
            assertTrue(e.lines().any { "But it failed!" in it })
        }
    }

    @Test fun theNewAbilityStartsLikeOnEntering() {
        // Entrainment of Intimidate: the wild one acts as if it just came in
        val w = R17C9b.wild("Levitate", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Intimidate", "entrainment")
        R17C9b.round(m, b, "entrainment")
        assertEquals("the Main's own Intimidate on entering", -1, b.enemy.boosts[0])
        assertEquals("the wild one's new Intimidate", -1, b.player.boosts[0])
    }

    @Test fun gastroAcidSwitchesTheTargetsAbilityOffForTheFight() {
        val w = R17C9b.wild("Levitate", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "gastroacid", "earthquake")
        val e = R17C9b.round(m, b, "gastroacid")
        assertEquals("", abilityOf(b, m, false))
        assertTrue(e.lines().any { "Ability was suppressed!" in it })
        assertEquals("Suppressed", e.effects().first { it.text.contains("suppressed") }.label)
        R17C9b.round(m, b, "earthquake")
        assertTrue("Levitate does nothing now", w.hp < R17C9b.maxHp(w))
        // again: nothing left to suppress; an ability that can't be suppressed
        val e2 = R17C9b.round(m, b, "gastroacid")
        assertTrue(failed(e2))
        val w3 = R17C9b.wild("Comatose", 801, "tackle"); val b3 = R17C9b.battle(w3)
        assertTrue(failed(R17C9b.round(R17C9b.main("", "gastroacid"), b3, "gastroacid")))
        assertEquals("comatose", SimpleView.of(R17C9b.data, b3, R17C9b.main("", "tackle")).ability(false))
    }

    @Test fun rolePlayAndDoodleCopyTheTargetsAbility() {
        for (move in listOf("roleplay", "doodle")) {
            val w = R17C9b.wild("Levitate", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Static", move)
            val e = R17C9b.round(m, b, move)
            assertEquals(move, "levitate", abilityOf(b, m, true))
            assertEquals(move, "levitate", abilityOf(b, m, false))
            assertTrue(move, e.lines().any { "copied" in it && "Levitate!" in it })
            // fails: the user's ability can't change, the target's can't be copied, or they are the same
            for ((mine, theirs) in listOf("Comatose" to "Levitate", "Static" to "Wonder Guard", "Static" to "Trace", "Static" to "Static", "Static" to "")) {
                val w2 = R17C9b.wild(theirs, 801, "tackle"); val b2 = R17C9b.battle(w2); val m2 = R17C9b.main(mine, move)
                assertTrue("$move $mine <- $theirs", failed(R17C9b.round(m2, b2, move)))
                assertEquals("$move $mine <- $theirs", BattleView.normalizeAbility(mine), abilityOf(b2, m2, true))
            }
        }
    }

    @Test fun simpleBeamGivesSimpleExceptWhereItCannot() {
        val w = R17C9b.wild("Levitate", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "simplebeam")
        val e = R17C9b.round(m, b, "simplebeam")
        assertEquals("simple", abilityOf(b, m, false))
        assertTrue(e.lines().any { "acquired Simple!" in it })
        for (theirs in listOf("Simple", "Truant", "Comatose", "Disguise")) {
            val w2 = R17C9b.wild(theirs, 801, "tackle"); val b2 = R17C9b.battle(w2)
            assertTrue(theirs, failed(R17C9b.round(R17C9b.main("", "simplebeam"), b2, "simplebeam")))
        }
    }

    @Test fun skillSwapTradesTheAbilities() {
        val w = R17C9b.wild("Levitate", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Static", "skillswap")
        val e = R17C9b.round(m, b, "skillswap")
        assertEquals("levitate", abilityOf(b, m, true))
        assertEquals("static", abilityOf(b, m, false))
        assertTrue(e.lines().any { "swapped Abilities with its target!" in it })
        // either side with an ability that can't be swapped: it fails
        for ((mine, theirs) in listOf("Wonder Guard" to "Levitate", "Static" to "Wonder Guard", "Static" to "Static", "Neutralizing Gas" to "Levitate")) {
            val w2 = R17C9b.wild(theirs, 801, "tackle"); val b2 = R17C9b.battle(w2); val m2 = R17C9b.main(mine, "skillswap")
            assertTrue("$mine <-> $theirs", failed(R17C9b.round(m2, b2, "skillswap")))
        }
        // the abilities that act on entering act for the one that got them
        val w3 = R17C9b.wild("Levitate", 801, "tackle"); val b3 = R17C9b.battle(w3); val m3 = R17C9b.main("Intimidate", "skillswap")
        R17C9b.round(m3, b3, "skillswap")
        assertEquals("the Main's Intimidate on entering", -1, b3.enemy.boosts[0])
        assertEquals("the wild one's new Intimidate", -1, b3.player.boosts[0])
    }

    @Test fun worrySeedGivesInsomniaAndWakesASleeper() {
        val w = R17C9b.wild("Levitate", 801, "tackle"); val b = R17C9b.battle(w); b.enemy.status = "slp"; val m = R17C9b.main("", "worryseed")
        val e = R17C9b.round(m, b, "worryseed", rng = R17C9b.high)
        assertEquals("insomnia", abilityOf(b, m, false))
        assertNull("it woke up", b.enemy.status)
        assertTrue(e.lines().any { "acquired Insomnia!" in it })
        assertTrue(e.lines().any { "Insomnia cured its sleep!" in it })
        assertTrue(e.effects().any { it.kind == EffectKind.STATUS_CURED && it.stat == "slp" })
        // not twice, not on an ability that can't change; a status Insomnia doesn't stop stays
        assertTrue(failed(R17C9b.round(m, b, "worryseed")))
        val w2 = R17C9b.wild("Comatose", 801, "tackle"); val b2 = R17C9b.battle(w2)
        assertTrue(failed(R17C9b.round(R17C9b.main("", "worryseed"), b2, "worryseed")))
        val w3 = R17C9b.wild("Levitate", 801, "tackle"); val b3 = R17C9b.battle(w3); b3.enemy.status = "par"
        R17C9b.round(R17C9b.main("", "worryseed"), b3, "worryseed")
        assertEquals("par", b3.enemy.status)
    }

    @Test fun aNewAbilityCuresTheStatusItStops() {
        // Skill Swap hands the paralyzed Main a Limber ("high" numbers: it is not fully paralyzed, and Skill Swap never misses)
        val w = R17C9b.wild("Limber", 801, "tackle"); val b = R17C9b.battle(w); b.player.status = "par"
        val m = R17C9b.main("Static", "skillswap")
        val e = R17C9b.round(m, b, "skillswap", rng = R17C9b.high)
        assertEquals("limber", abilityOf(b, m, true))
        assertNull(b.player.status)
        assertTrue(e.lines().any { "Limber cured its paralysis!" in it })
        assertTrue(e.effects().any { it.kind == EffectKind.STATUS_CURED && it.stat == "par" })
    }

    @Test fun anAbilityChangeLastsForTheFightAndAMainThatLeavesGetsItsOwnBack() {
        val w = R17C9b.wild("Levitate", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Static", "skillswap")
        R17C9b.round(m, b, "skillswap")
        assertEquals("levitate", abilityOf(b, m, true))
        b.switchOutMain(R17C9b.data, m)
        assertEquals("the fresh side", "static", abilityOf(b, m, true))
        assertEquals("the wild one keeps what it got", "static", abilityOf(b, m, false))
    }

    @Test fun theMovesAreHandledHaveTextsAndTheSmartChoiceKnowsWhenTheyFail() {
        assertEquals(AbilityMoves.IDS, AbilitiesMore.handles)
        assertEquals(7, AbilityMoves.IDS.size)
        for (id in AbilityMoves.IDS) {
            val text = MoveText.describe(R17C9b.data.move(id)!!)
            assertFalse("$id has a text", text.startsWith("Has no effect"))
            assertTrue("$id ends with a full stop: $text", text.endsWith("."))
        }
        val w = R17C9b.wild("Static", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("Static", "entrainment")
        val v = SimpleView.of(R17C9b.data, b, m)
        assertFalse("the same ability: nothing to do", MoveChooser.statusUseful(MoveChoice.SMART, R17C9b.data.move("entrainment")!!, v, true))
        val b2 = R17C9b.battle(R17C9b.wild("Levitate", 801, "tackle"))
        assertTrue(MoveChooser.statusUseful(MoveChoice.SMART, R17C9b.data.move("entrainment")!!, SimpleView.of(R17C9b.data, b2, m), true))
        assertFalse("nothing to suppress", MoveChooser.statusUseful(MoveChoice.SMART, R17C9b.data.move("gastroacid")!!, SimpleView.of(R17C9b.data, R17C9b.battle(R17C9b.wild("", 801, "tackle")), m), true))
    }

    @Test fun theWildOneUsesThemOnTheMainToo() {
        val m = R17C9b.main("Levitate", "tackle"); val w = R17C9b.wild("Static", 801, "skillswap"); val b = R17C9b.battle(w)
        R17C9b.round(m, b, "tackle", "skillswap")
        assertEquals("static", abilityOf(b, m, true))
        assertEquals("levitate", abilityOf(b, m, false))
    }
}
