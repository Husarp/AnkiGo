package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C9.attacks
import com.husarp.ankigo.game.R17C9.effects
import com.husarp.ankigo.game.R17C9.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17C-9a: what the target's (or the user's) ability does when a move meets it: immunities and absorbing, Sturdy, Damp, Prankster, the contact
 * effects, Rough Skin, the reactions to a hit (Weak Armor, Justified, Rattled, Cursed Body), Poison Touch, Stench, Steadfast, Synchronize,
 * Shield Dust, Infiltrator and Mold Breaker. Fixtures in [R17C9]; `low` makes every chance happen, [R17C9.fixed] picks the roll.
 */
class R17C9aReactionTest {
    private fun fightLines(e: List<GameEvent>) = e.lines()

    // ---------------------------------------------------------------- absorbing and immunity

    @Test fun waterAbsorbVoltAbsorbAndDrySkinHealAQuarter() {
        for ((ab, move) in listOf("Water Absorb" to "watergun", "Volt Absorb" to "thundershock", "Dry Skin" to "watergun")) {
            val w = R17C9.wild(ab, 801, "tackle"); val b = R17C9.battle(w); val m = R17C9.main("", move)
            val max = R17C9.maxHp(w)
            w.hp = max - 300
            val e = R17C9.round(m, b, move)
            assertEquals(ab, max - 300 + max / 4, w.hp)
            assertTrue(ab, e.attacks().single().failed)
            assertTrue("$ab names itself", e.lines().any { "$ab restored its HP!" in it })
            assertEquals("the float label is the ability", ab, e.effects().first { it.kind == EffectKind.HEAL }.label)
            assertTrue("a heal shows its amount", e.effects().first { it.kind == EffectKind.HEAL }.amount == max / 4)
        }
    }

    @Test fun anAbsorbingAbilityAtFullHpIsJustUnaffectedAndOtherTypesHurt() {
        val w = R17C9.wild("Volt Absorb", 801, "tackle"); val b = R17C9.battle(w); val m = R17C9.main("", "thundershock", "watergun")
        val max = R17C9.maxHp(w)
        val e = R17C9.round(m, b, "thundershock")
        assertEquals(max, w.hp)
        assertTrue(e.lines().any { "Volt Absorb: it isn't affected." in it })
        R17C9.round(m, b, "watergun")
        assertTrue("another type hurts", w.hp < max)
    }

    @Test fun lightningRodStormDrainMotorDriveAndSapSipperRaiseAStat() {
        // ability, move, stat index, stat key
        for ((ab, move, idx) in listOf(Triple("Lightning Rod", "thundershock", 2), Triple("Storm Drain", "watergun", 2), Triple("Motor Drive", "thundershock", 4), Triple("Sap Sipper", "vinewhip", 0))) {
            val w = R17C9.wild(ab, 801, "tackle"); val b = R17C9.battle(w)
            val e = R17C9.round(R17C9.main("", move), b, move)
            assertEquals("$ab: no damage", R17C9.maxHp(w), w.hp)
            assertEquals("$ab: +1", 1, b.enemy.boosts[idx])
            assertTrue(ab, e.attacks().single().failed)
            assertTrue("$ab absorbed", e.lines().any { "$ab: it absorbed the move!" in it })
        }
    }

    @Test fun absorbersAlsoStopStatusMovesOfTheirType() {
        val w = R17C9.wild("Volt Absorb", 801, "tackle"); val b = R17C9.battle(w)
        w.hp = R17C9.maxHp(w) - 50
        R17C9.round(R17C9.main("", "thunderwave"), b, "thunderwave")
        assertNull("no paralysis", b.enemy.status)
        assertTrue("it healed instead", w.hp > R17C9.maxHp(w) - 50)
        val w2 = R17C9.wild("Sap Sipper", 801, "tackle"); val b2 = R17C9.battle(w2)
        R17C9.round(R17C9.main("", "sleeppowder"), b2, "sleeppowder")
        assertNull(b2.enemy.status)
        assertEquals(1, b2.enemy.boosts[0])
    }

    @Test fun flashFireAbsorbsFireMovesAndFireStatusMoves() {
        val w = R17C9.wild("Flash Fire", 801, "tackle"); val b = R17C9.battle(w); val m = R17C9.main("", "ember", "willowisp")
        val e = R17C9.round(m, b, "ember")
        assertEquals(R17C9.maxHp(w), w.hp)
        assertTrue(e.lines().any { "Flash Fire: its Fire moves are stronger now!" in it })
        assertEquals(1, b.enemy.volatiles["ab.flashfire"])
        val e2 = R17C9.round(m, b, "willowisp")
        assertNull("Will-O-Wisp is absorbed too", b.enemy.status)
        assertTrue(e2.lines().any { "Flash Fire: it isn't affected." in it })
    }

    @Test fun levitateStopsGroundMovesUnlessGravityOrMoldBreaker() {
        val w = R17C9.wild("Levitate", 801, "tackle"); val b = R17C9.battle(w); val m = R17C9.main("", "earthquake", "tackle")
        val e = R17C9.round(m, b, "earthquake")
        assertEquals(R17C9.maxHp(w), w.hp)
        assertTrue(e.attacks().single().failed)
        assertTrue(e.lines().any { "Levitate: Ground moves don't affect it." in it })
        R17C9.round(m, b, "tackle")
        assertTrue("other moves hit", w.hp < R17C9.maxHp(w))
        // Gravity brings it down
        val w2 = R17C9.wild("Levitate", 801, "tackle"); val b2 = R17C9.battle(w2); b2.field.rooms["gravity"] = 5
        R17C9.round(m, b2, "earthquake")
        assertTrue("Gravity", w2.hp < R17C9.maxHp(w2))
        // Mold Breaker
        val w3 = R17C9.wild("Levitate", 801, "tackle"); val b3 = R17C9.battle(w3)
        R17C9.round(R17C9.main("Mold Breaker", "earthquake"), b3, "earthquake")
        assertTrue("Mold Breaker", w3.hp < R17C9.maxHp(w3))
    }

    @Test fun levitateIsNotGroundedButStaysGroundedUnderGravityOrIngrain() {
        val w = R17C9.wild("Levitate", 801, "tackle"); val b = R17C9.battle(w); val m = R17C9.main("", "tackle")
        val v = SimpleView.of(R17C9.data, b, m)
        assertFalse(v.grounded(false))
        assertTrue("the Main is grounded", v.grounded(true))
        b.enemy.volatiles["ingrain"] = 1
        assertTrue(v.grounded(false))
        b.enemy.volatiles.clear(); b.enemy.volatiles["smackdown"] = 1
        assertTrue("Smack Down", v.grounded(false))
        b.enemy.volatiles.clear(); b.field.rooms["gravity"] = 5
        assertTrue(v.grounded(false))
    }

    @Test fun soundproofStopsSoundMoves() {
        val w = R17C9.wild("Soundproof", 801, "tackle"); val b = R17C9.battle(w); val m = R17C9.main("", "hypervoice", "growl", "tackle")
        val e = R17C9.round(m, b, "hypervoice")
        assertEquals(R17C9.maxHp(w), w.hp)
        assertTrue(e.lines().any { "Soundproof: sound moves don't affect it." in it })
        R17C9.round(m, b, "growl")
        assertEquals("Growl is a sound move too", 0, b.enemy.boosts[0])
        R17C9.round(m, b, "tackle")
        assertTrue(w.hp < R17C9.maxHp(w))
    }

    @Test fun overcoatStopsPowderMoves() {
        fun after(ab: String): String? { val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); R17C9.round(R17C9.main("", "sleeppowder"), b, "sleeppowder"); return b.enemy.status }
        assertNull(after("Overcoat"))
        assertEquals("slp", after(""))
    }

    @Test fun sturdyEndursAtFullHpAndStopsOneHitKos() {
        val w = R17C9.wild("Sturdy", 801, "tackle", lv = 5); val b = R17C9.battle(w); val m = R17C9.main("", "bodyslam")
        val e = R17C9.round(m, b, "bodyslam")
        assertEquals(1, w.hp)
        assertTrue(e.lines().any { "Sturdy: it endured the hit!" in it })
        R17C9.round(m, b, "bodyslam")
        assertEquals("not at full HP any more", 0, w.hp)
        // a hit that does not knock it out does not trigger it
        val w2 = R17C9.wild("Sturdy", 801, "tackle"); val b2 = R17C9.battle(w2)
        val e2 = R17C9.round(R17C9.main("", "tackle"), b2, "tackle")
        assertTrue(w2.hp > 1); assertFalse(e2.lines().any { "Sturdy" in it })
        // the one-hit KO does not work
        val w3 = R17C9.wild("Sturdy", 801, "tackle"); val b3 = R17C9.battle(w3)
        val e3 = R17C9.round(R17C9.main("", "fissure"), b3, "fissure")
        assertEquals(R17C9.maxHp(w3), w3.hp)
        assertTrue(e3.lines().any { "Sturdy: one-hit KO moves don't affect it." in it })
        // Mold Breaker
        val w4 = R17C9.wild("Sturdy", 801, "tackle", lv = 5); val b4 = R17C9.battle(w4)
        R17C9.round(R17C9.main("Mold Breaker", "bodyslam"), b4, "bodyslam")
        assertEquals(0, w4.hp)
    }

    @Test fun dampStopsSelfDestruct() {
        for ((mainAb, wildAb) in listOf("" to "Damp", "Damp" to "")) {
            val m = R17C9.main(mainAb, "explosion"); val w = R17C9.wild(wildAb, 801, "tackle"); val b = R17C9.battle(w)
            val e = R17C9.round(m, b, "explosion")
            assertTrue("Main $mainAb wild $wildAb: Explosion failed", e.attacks().single().failed)
            assertTrue(m.hp > 0)
            assertTrue(e.lines().any { "Damp prevents" in it })
        }
        val m = R17C9.main("", "explosion"); R17C9.round(m, R17C9.battle(R17C9.wild("", 801, "tackle")), "explosion")
        assertEquals("without Damp it goes off", 0, m.hp)
        // Mold Breaker ignores the target's Damp
        val m2 = R17C9.main("Mold Breaker", "explosion"); R17C9.round(m2, R17C9.battle(R17C9.wild("Damp", 801, "tackle")), "explosion")
        assertEquals(0, m2.hp)
    }

    @Test fun pranksterGoesFirstAndFailsAgainstDarkTypes() {
        // the wild one is slower, but its Prankster status move goes first
        fun firstIsWild(ab: String): Boolean {
            val m = R17C9.main("", "tackle"); val b = R17C9.battle(R17C9.wild(ab, 801, "growl"))
            return !R17C9.round(m, b, "tackle", "growl").attacks().first().byPlayer
        }
        assertTrue(firstIsWild("Prankster"))
        assertFalse(firstIsWild(""))
        // against a Dark type it fails
        val w = R17C9.wild("", 803, "tackle"); val b = R17C9.battle(w)
        val e = R17C9.round(R17C9.main("Prankster", "thunderwave"), b, "thunderwave")
        assertTrue(e.attacks().single().failed); assertNull(b.enemy.status)
        assertTrue(e.lines().any { "doesn't affect" in it })
        val b2 = R17C9.battle(R17C9.wild("", 803, "tackle"))
        R17C9.round(R17C9.main("", "thunderwave"), b2, "thunderwave")
        assertEquals("par", b2.enemy.status)
    }

    @Test fun aSmartSideDoesNotPickAMoveThatWillBeAbsorbed() {
        val w = R17C9.wild("Volt Absorb", 801, "tackle"); val b = R17C9.battle(w); val m = R17C9.main("", "thundershock", "tackle")
        for (choice in listOf(MoveChoice.SMART, MoveChoice.SMARTEST)) for (seed in 0 until 40) {
            val move = MoveChooser.pick(choice, SimpleView.of(R17C9.data, b, m), true, kotlin.random.Random(seed))
            assertEquals("$choice $seed", "tackle", move.id)
        }
        // the same for a status move that the target's ability stops
        val b2 = R17C9.battle(R17C9.wild("Limber", 801, "tackle")); val m2 = R17C9.main("", "thunderwave", "tackle")
        for (seed in 0 until 40) assertEquals("tackle", MoveChooser.pick(MoveChoice.SMART, SimpleView.of(R17C9.data, b2, m2), true, kotlin.random.Random(seed)).id)
    }

    // ---------------------------------------------------------------- contact effects of the target

    @Test fun staticPoisonPointAndFlameBodyAfflictAContactAttacker() {
        for ((ab, status) in listOf("Static" to "par", "Poison Point" to "psn", "Flame Body" to "brn")) {
            val b = R17C9.battle(R17C9.wild(ab, 801, "tackle")); val m = R17C9.main("", "tackle", "watergun")
            val e = R17C9.round(m, b, "tackle")
            assertEquals(ab, status, b.player.status)
            assertTrue("$ab shouts", e.lines().any { "$ab!" in it })
            assertEquals("a float with the ability's name", ab, e.effects().first { it.label == ab }.label)
            val b2 = R17C9.battle(R17C9.wild(ab, 801, "tackle"))
            R17C9.round(R17C9.main("", "watergun"), b2, "watergun")
            assertNull("$ab: no contact, nothing", b2.player.status)
            val b3 = R17C9.battle(R17C9.wild(ab, 801, "tackle"))
            R17C9.round(R17C9.main("", "tackle"), b3, "tackle", null, R17C9.fixed(99, 0.0))
            assertNull("$ab: the 30 % is not met by a roll of 99", b3.player.status)
        }
    }

    @Test fun theContactStatusRespectsTypes() {
        // an Electric attacker can't be paralyzed by Static, a Poison one not poisoned
        val m = R17C9.mon(805, 100, "", "tackle"); val b = R17C9.battle(R17C9.wild("Static", 801, "tackle"))
        R17C9.round(m, b, "tackle")
        assertNull(b.player.status)
        val m2 = R17C9.mon(811, 100, "", "tackle"); val b2 = R17C9.battle(R17C9.wild("Poison Point", 801, "tackle"))
        R17C9.round(m2, b2, "tackle")
        assertNull(b2.player.status)
    }

    @Test fun effectSporeGivesPoisonParalysisOrSleepButNotToGrassOrOvercoat() {
        val picks = listOf(0, 1, 2).map { i ->
            val b = R17C9.battle(R17C9.wild("Effect Spore", 801, "tackle"))
            val rng = object : kotlin.random.Random() { override fun nextBits(bitCount: Int) = 0; override fun nextInt(until: Int) = if (until == 3) i else 0; override fun nextDouble() = 0.0 }
            R17C9.round(R17C9.main("", "tackle"), b, "tackle", null, rng)
            b.player.status
        }
        assertEquals(listOf("psn", "par", "slp"), picks)
        val grass = R17C9.battle(R17C9.wild("Effect Spore", 801, "tackle")); R17C9.round(R17C9.mon(802, 100, "", "tackle"), grass, "tackle")
        assertNull(grass.player.status)
        val coat = R17C9.battle(R17C9.wild("Effect Spore", 801, "tackle")); R17C9.round(R17C9.main("Overcoat", "tackle"), coat, "tackle")
        assertNull(coat.player.status)
    }

    @Test fun cuteCharmMakesAnOppositeGenderAttackerFallInLove() {
        fun inLove(mg: Char, wg: Char, move: String = "tackle"): Boolean {
            val b = R17C9.battle(R17C9.wild("Cute Charm", 801, "tackle", gender = wg)); val m = R17C9.main("", "tackle", "watergun", gender = mg)
            R17C9.round(m, b, move)
            return "attract" in b.player.volatiles
        }
        assertTrue(inLove('M', 'F'))
        assertTrue(inLove('F', 'M'))
        assertFalse("same gender", inLove('M', 'M'))
        assertFalse("no gender", inLove('M', 'N'))
        assertFalse("no contact", inLove('M', 'F', "watergun"))
    }

    @Test fun roughSkinAndIronBarbsHurtAContactAttackerAnEighth() {
        for (ab in listOf("Rough Skin", "Iron Barbs")) {
            val m = R17C9.main("", "tackle", "watergun"); val b = R17C9.battle(R17C9.wild(ab, 801, "tackle"))
            val max = R17C9.maxHp(m)
            val e = R17C9.round(m, b, "tackle")
            assertEquals(ab, max - max / 8, m.hp)
            assertEquals(ab, e.effects().first { it.label == ab }.label)
            val m2 = R17C9.main("", "watergun"); R17C9.round(m2, R17C9.battle(R17C9.wild(ab, 801, "tackle")), "watergun")
            assertEquals("$ab: no contact", max, m2.hp)
            val m3 = R17C9.main("Magic Guard", "tackle"); R17C9.round(m3, R17C9.battle(R17C9.wild(ab, 801, "tackle")), "tackle")
            assertEquals("$ab: Magic Guard", max, m3.hp)
        }
    }

    @Test fun roughSkinCanKnockTheAttackerOutAndStopsAMultiHitMove() {
        val m = R17C9.main("", "doublekick"); val b = R17C9.battle(R17C9.wild("Rough Skin", 801, "tackle"))
        m.hp = 1
        val e = R17C9.round(m, b, "doublekick")
        assertEquals(0, m.hp)
        assertTrue(e.any { it is GameEvent.Fainted && it.player })
        assertEquals("the second hit never comes", 1, e.attacks().single().hits)
    }

    @Test fun weakArmorJustifiedAndRattledReactToTheirHits() {
        fun boosts(wildAb: String, move: String): List<Int> { // a Rock type: Bug, Ghost and Dark hit it for damage
            val b = R17C9.battle(R17C9.wild(wildAb, 806, "tackle")); R17C9.round(R17C9.main("", move), b, move); return b.enemy.boosts.toList()
        }
        assertEquals("Weak Armor: Defense -1, Speed +2", listOf(0, -1, 0, 0, 2, 0, 0), boosts("Weak Armor", "tackle"))
        assertEquals("a special move: nothing", listOf(0, 0, 0, 0, 0, 0, 0), boosts("Weak Armor", "watergun"))
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0), boosts("Justified", "crunch"))
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0), boosts("Justified", "tackle"))
        for (move in listOf("bugbite", "shadowball", "crunch")) assertEquals("Rattled $move", listOf(0, 0, 0, 0, 1, 0, 0), boosts("Rattled", move))
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0), boosts("Rattled", "tackle"))
    }

    @Test fun cursedBodyDisablesTheMoveThatHitIt() {
        val m = R17C9.main("", "tackle"); val b = R17C9.battle(R17C9.wild("Cursed Body", 801, "tackle"))
        R17C9.round(m, b, "tackle")
        assertEquals("tackle", b.player.notes["disable"])
        assertTrue((b.player.volatiles["disable"] ?: 0) > 0)
        val b2 = R17C9.battle(R17C9.wild("Cursed Body", 801, "tackle"))
        R17C9.round(R17C9.main("", "tackle"), b2, "tackle", null, R17C9.fixed(99))
        assertFalse("the 30 % is not met", "disable" in b2.player.volatiles)
    }

    @Test fun shieldDustStopsTheExtraEffectsOfAMoveButNotItsUsersOwnGain() {
        fun stage(wildAb: String, move: String, idx: Int, main: Mon = R17C9.main("", move)): Int {
            val b = R17C9.battle(R17C9.wild(wildAb, 801, "tackle")); R17C9.round(main, b, move)
            return if (idx >= 100) b.player.boosts[idx - 100] else b.enemy.boosts[idx]
        }
        assertEquals("Psychic lowers Sp. Def", -1, stage("", "psychic", 3))
        assertEquals("not with Shield Dust", 0, stage("Shield Dust", "psychic", 3))
        assertEquals("Flame Charge raises the user's Speed", 1, stage("Shield Dust", "flamecharge", 104))
        val b = R17C9.battle(R17C9.wild("Shield Dust", 801, "tackle")); R17C9.round(R17C9.main("", "firepunch"), b, "firepunch")
        assertNull(b.enemy.status)
        // Mold Breaker
        assertEquals(-1, stage("Shield Dust", "psychic", 3, R17C9.main("Mold Breaker", "psychic")))
    }

    // ---------------------------------------------------------------- effects of the user's own moves

    @Test fun poisonTouchPoisonsOnContactButNotThroughShieldDust() {
        fun status(wildAb: String, move: String): String? {
            val b = R17C9.battle(R17C9.wild(wildAb, 801, "tackle")); R17C9.round(R17C9.main("Poison Touch", move), b, move); return b.enemy.status
        }
        assertEquals("psn", status("", "tackle"))
        assertNull(status("", "watergun"))
        assertNull(status("Shield Dust", "tackle"))
        assertEquals("a Steel/Poison type can't be poisoned: none", null, run { val b = R17C9.battle(R17C9.wild("", 811, "tackle")); R17C9.round(R17C9.main("Poison Touch", "tackle"), b, "tackle"); b.enemy.status })
    }

    @Test fun stenchMakesTheTargetFlinchIfItMovesFirstAndSteadfastRaisesSpeed() {
        fun wildFlinched(ab: String, rng: kotlin.random.Random): Boolean {
            val b = R17C9.battle(R17C9.wild("", 801, "tackle")); val m = R17C9.main(ab, "tackle")
            return R17C9.round(m, b, "tackle", "tackle", rng).lines().any { "flinched and couldn't move!" in it }
        }
        assertTrue(wildFlinched("Stench", R17C9.fixed(0)))
        assertFalse("10 % is not met by 45", wildFlinched("Stench", R17C9.fixed(45)))
        assertFalse(wildFlinched("", R17C9.fixed(0)))
        // Steadfast: Bite's flinch (30 %) raises the Speed of the one that flinches
        val b = R17C9.battle(R17C9.wild("Steadfast", 801, "tackle")); val m = R17C9.main("", "bite")
        val e = R17C9.round(m, b, "bite", "tackle")
        assertEquals(1, b.enemy.boosts[4])
        assertTrue(e.lines().any { "Steadfast!" in it })
        assertEquals("only when it flinches", 0, R17C9.battle(R17C9.wild("Steadfast", 801, "tackle")).also { R17C9.round(m, it, "tackle", "tackle", R17C9.fixed(99)) }.enemy.boosts[4])
        // Inner Focus can't flinch
        val b2 = R17C9.battle(R17C9.wild("Inner Focus", 801, "tackle"))
        val e2 = R17C9.round(R17C9.main("", "bite"), b2, "bite", "tackle")
        assertFalse(e2.lines().any { "flinched" in it })
    }

    @Test fun synchronizePassesBurnPoisonAndParalysisBack() {
        for ((move, status) in listOf("willowisp" to "brn", "toxic" to "tox", "thunderwave" to "par")) {
            val m = R17C9.main("Synchronize", "splash"); val b = R17C9.battle(R17C9.wild("", 801, move))
            val e = R17C9.round(m, b, "splash", move)
            assertEquals(move, status, b.player.status)
            assertEquals("$move: the wild one gets it too", status, b.enemy.status)
            assertTrue(e.lines().any { "Synchronize!" in it })
        }
        // sleep is not passed back
        val m = R17C9.main("Synchronize", "splash"); val b = R17C9.battle(R17C9.wild("", 801, "hypnosis"))
        R17C9.round(m, b, "splash", "hypnosis")
        assertEquals("slp", b.player.status); assertNull(b.enemy.status)
    }

    @Test fun infiltratorGoesThroughSubstituteScreensSafeguardAndMist() {
        // Substitute: the hit goes to the Pokémon, the substitute stays
        fun sub(ab: String): Pair<Int, Int> {
            val w = R17C9.wild("", 801, "tackle"); val b = R17C9.battle(w)
            b.enemy.volatiles["substitute"] = 30_000
            R17C9.round(R17C9.main(ab, "tackle"), b, "tackle")
            return w.hp to (b.enemy.volatiles["substitute"] ?: 0)
        }
        val max = R17C9.maxHp(R17C9.wild("", 801))
        assertEquals("the substitute takes it", max, sub("").first)
        assertTrue(sub("").second < 30_000)
        assertTrue("Infiltrator hits the Pokémon", sub("Infiltrator").first < max)
        assertEquals("and leaves the substitute alone", 30_000, sub("Infiltrator").second)
        // Reflect
        fun dmg(ab: String, reflect: Boolean): Int {
            val b = R17C9.battle(R17C9.wild("", 801, "tackle")); if (reflect) b.field.enemy["reflect"] = 5
            return R17C9.dmg(b, R17C9.main(ab, "tackle"), "tackle")
        }
        assertTrue(dmg("", true) < dmg("", false))
        assertEquals(dmg("", false), dmg("Infiltrator", true))
        // Safeguard, Mist
        fun par(ab: String): String? { val b = R17C9.battle(R17C9.wild("", 801, "tackle")); b.field.enemy["safeguard"] = 5; R17C9.round(R17C9.main(ab, "thunderwave"), b, "thunderwave"); return b.enemy.status }
        assertNull(par("")); assertEquals("par", par("Infiltrator"))
        fun atk(ab: String): Int { val b = R17C9.battle(R17C9.wild("", 801, "tackle")); b.field.enemy["mist"] = 5; R17C9.round(R17C9.main(ab, "growl"), b, "growl"); return b.enemy.boosts[0] }
        assertEquals(0, atk("")); assertEquals(-1, atk("Infiltrator"))
    }

    @Test fun moldBreakerIgnoresTheStopsOfBreakableAbilities() {
        fun stage(wildAb: String, move: String, mainAb: String, idx: Int): Int { val b = R17C9.battle(R17C9.wild(wildAb, 801, "tackle")); R17C9.round(R17C9.main(mainAb, move), b, move); return b.enemy.boosts[idx] }
        assertEquals(0, stage("Clear Body", "growl", "", 0))
        assertEquals(-1, stage("Clear Body", "growl", "Mold Breaker", 0))
        assertEquals(-1, stage("Hyper Cutter", "growl", "Teravolt", 0))
        // an ability that is not breakable still works: Rough Skin
        val m = R17C9.main("Mold Breaker", "tackle"); R17C9.round(m, R17C9.battle(R17C9.wild("Rough Skin", 801, "tackle")), "tackle")
        assertTrue(m.hp < R17C9.maxHp(m))
        // Volt Absorb / Soundproof
        val w = R17C9.wild("Volt Absorb", 801, "tackle"); R17C9.round(R17C9.main("Mold Breaker", "thundershock"), R17C9.battle(w), "thundershock")
        assertTrue(w.hp < R17C9.maxHp(w))
        val w2 = R17C9.wild("Soundproof", 801, "tackle"); R17C9.round(R17C9.main("Turboblaze", "hypervoice"), R17C9.battle(w2), "hypervoice")
        assertTrue(w2.hp < R17C9.maxHp(w2))
    }
}
