package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C8.attacks
import com.husarp.ankigo.game.R17C8.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R17C-8: type changes: Soak, Magic Powder, Reflect Type, Conversion, Conversion 2, Forest's Curse, Trick-or-Treat, Camouflage, Burn Up, Double
 * Shock, Roost, and Electrify and Psycho Shift. The Main acts first; the wild one idles unless a test says so.
 */
class R17C8TypesTest {
    private val data = R17C8.data
    private fun main(vararg m: String) = R17C8.main(*m)
    private fun wild(vararg m: String, sp: Int = 801) = R17C8.wild(*m, sp = sp)
    private fun rd(main: Mon, b: Battle, rng: Random = Random(1), wildActs: Boolean = false) = R17C8.round(main, b, rng, enemyActs = wildActs)
    private fun <T> forced(id: String, block: () -> T): T = R17C8.withRules(R17C8.forcing(id), block = block)
    private fun types(m: Mon, b: Battle, mine: Boolean) = R17C8.view(m, b).types(mine)
    private fun List<GameEvent>.failedMain() = attacks().single { it.byPlayer }.failed

    // ---------------------------------------------------------------- the target's type

    @Test fun soakMakesTheTargetAPureWaterTypeAndFailsOnOneThatIsAlready() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle", sp = 802))
        val e = forced("soak") { rd(m, b) }
        assertEquals(listOf(Type.WATER), types(m, b, false))
        assertTrue("Torchy transformed into the Water type!" in e.lines())
        assertTrue("already pure Water", forced("soak") { rd(m, b) }.failedMain())
        assertTrue(forced("soak") { rd(main("tackle"), R17C8.battle(wild("tackle", sp = 809))) }.failedMain())
    }

    @Test fun magicPowderMakesThePsychicTypeButNotOnAGrassType() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle", sp = 802))
        forced("magicpowder") { rd(m, b) }
        assertEquals(listOf(Type.PSYCHIC), types(m, b, false))
        val b2 = R17C8.battle(wild("tackle", sp = 803))
        assertTrue(forced("magicpowder") { rd(main("tackle"), b2) }.failedMain())
        assertEquals(listOf(Type.GRASS), types(m, b2, false))
    }

    @Test fun forestsCurseAndTrickOrTreatAddATypeAndFailWhenItIsThere() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        val e = forced("forestscurse") { rd(m, b) }
        assertEquals(listOf(Type.NORMAL, Type.GRASS), types(m, b, false))
        assertTrue("Grass type was added to Slowpoke!" in e.lines())
        assertTrue(forced("forestscurse") { rd(m, b) }.failedMain())
        forced("trickortreat") { rd(m, b) }
        assertEquals(listOf(Type.NORMAL, Type.GRASS, Type.GHOST), types(m, b, false))
        // the new type counts: a Normal move no longer hurts a Ghost
        val tackle = data.move("tackle")!!
        val r = BattleEngine.damage(data, m, b.player, b.wild, b.enemy, tackle, Random(1), forceCrit = false, forceRoll = 100, view = R17C8.view(m, b), attackerIsMain = true)
        assertEquals(0.0, r.effectiveness, 0.0)
    }

    @Test fun electrifyMakesTheTargetsMoveElectricAndFailsWhenItHasMoved() {
        val m = R17C8.mon(814, 50, "tackle"); val b = R17C8.battle(wild("tackle")) // a fast Ground type: Electric moves do not hurt it
        val plain = rd(m, R17C8.battle(wild("tackle")), wildActs = true).attacks().single { !it.byPlayer }
        assertTrue(plain.damage > 0)
        val e = forced("electrify") { rd(m, b, wildActs = true) }
        assertTrue("Slowpoke's moves have been electrified!" in e.lines())
        val hit = e.attacks().single { !it.byPlayer }
        assertEquals(0, hit.damage); assertEquals(0.0, hit.effectiveness, 0.0)
        assertNull("it lasts one round", b.enemy.volatiles["electrify"])
        // too late: the target moved before
        val slow = R17C8.mon(801, 50, "tackle"); val b2 = R17C8.battle(R17C8.mon(800, 100, "tackle"))
        assertTrue(forced("electrify") { rd(slow, b2, wildActs = true) }.failedMain())
    }

    // ---------------------------------------------------------------- the user's type

    @Test fun reflectTypeCopiesTheTargetsTypes() {
        val m = main("tackle"); val b = R17C8.battle(R17C8.mon(813, 100, "tackle"))
        val e = forced("reflecttype") { rd(m, b) }
        assertEquals(listOf(Type.GRASS, Type.FLYING), types(m, b, true))
        assertTrue("Fastie's type became the same as Leafbird's type!" in e.lines())
    }

    @Test fun conversionTakesTheTypeOfOneOfTheOtherMovesThatIsNew() {
        for (seed in 1..8) {
            val m = main("conversion", "ember", "watergun", "tackle"); val b = R17C8.battle(wild("tackle"))
            forced("conversion") { rd(m, b, Random(seed)) }
            assertTrue("seed $seed", types(m, b, true).single() in listOf(Type.FIRE, Type.WATER))
        }
        val m = main("conversion", "tackle"); val b = R17C8.battle(wild("tackle"))
        assertTrue("every move is Normal: nothing new", forced("conversion") { rd(m, b) }.failedMain())
    }

    @Test fun conversion2TakesATypeThatResistsTheTargetsLastMove() {
        for (seed in 1..8) {
            val m = main("tackle"); val b = R17C8.battle(wild("ember")); b.enemy.lastMove = "ember"
            forced("conversion2") { rd(m, b, Random(seed)) }
            val t = types(m, b, true).single()
            assertTrue("seed $seed: $t", TypeChart.single(Type.FIRE, t) < 1.0)
        }
        val m = main("tackle"); val b = R17C8.battle(wild("ember"))
        assertTrue("no last move yet", forced("conversion2") { rd(m, b) }.failedMain())
    }

    @Test fun camouflageFollowsTheTerrainAndNormalWithoutOne() {
        for ((terrain, type) in listOf("electric" to Type.ELECTRIC, "grassy" to Type.GRASS, "misty" to Type.FAIRY, "psychic" to Type.PSYCHIC)) {
            val m = main("tackle"); val b = R17C8.battle(wild("tackle")); b.field.terrain = terrain; b.field.terrainTurns = 5
            forced("camouflage") { rd(m, b) }
            assertEquals(terrain, listOf(type), types(m, b, true))
        }
        val m = R17C8.mon(808, 50, "tackle"); val b = R17C8.battle(wild("tackle"))
        forced("camouflage") { rd(m, b) }
        assertEquals(listOf(Type.NORMAL), types(m, b, true))
        assertTrue("a Normal type stays Normal", forced("camouflage") { rd(main("tackle"), R17C8.battle(wild("tackle"))) }.failedMain())
    }

    // ---------------------------------------------------------------- Burn Up, Double Shock, Roost

    @Test fun burnUpNeedsAFireTypeAndCostsTheType() {
        val m = R17C8.mon(802, 50, "burnup"); val w = wild("tackle"); val b = R17C8.battle(w)
        val e = rd(m, b)
        assertTrue(w.hp < StatCalc.maxHp(data, w)); assertTrue("Torchy burned itself out!" in e.lines())
        assertTrue("a pure Fire type is typeless afterwards", types(m, b, true).isEmpty())
        // a user without the type fails and hurts nobody
        val m2 = main("tackle"); val w2 = wild("tackle"); val b2 = R17C8.battle(w2)
        val e2 = forced("burnup") { rd(m2, b2) }
        assertTrue(e2.failedMain()); assertEquals(StatCalc.maxHp(data, w2), w2.hp)
    }

    @Test fun doubleShockLosesTheElectricTypeAndNeedsIt() {
        val m = R17C8.mon(808, 50, "doubleshock"); val b = R17C8.battle(wild("tackle"))
        val e = rd(m, b)
        assertTrue("Zappy used up all its electricity!" in e.lines()); assertTrue(types(m, b, true).isEmpty())
        val m2 = main("tackle"); val b2 = R17C8.battle(wild("tackle"))
        assertTrue(forced("doubleshock") { rd(m2, b2) }.failedMain())
    }

    @Test fun burnUpOnADualTypeKeepsTheOtherType() {
        val m = R17C8.mon(802, 50, "burnup"); val b = R17C8.battle(wild("tackle"))
        b.player.notes["mc.types"] = "FIRE,FLYING"
        rd(m, b)
        assertEquals(listOf(Type.FLYING), types(m, b, true))
    }

    @Test fun roostHealsAndTakesAwayFlyingUntilTheEndOfTheRound() {
        val m = R17C8.mon(810, 50, "tackle"); m.hp = m.hp / 2; val before = m.hp; val b = R17C8.battle(wild("earthquake"))
        val e = forced("roost") { rd(m, b, wildActs = true) }
        assertTrue(m.hp > before)
        val quake = e.attacks().single { !it.byPlayer }
        assertTrue("Earthquake hits the roosting Flying type", quake.damage > 0 && quake.effectiveness > 0.0)
        assertTrue(Type.FLYING in types(m, b, true)); assertNull(b.player.volatiles["roost"])
        // without Roost the Flying type is immune
        val again = rd(m, b, wildActs = true).attacks().single { !it.byPlayer }
        assertEquals(0, again.damage); assertEquals(0.0, again.effectiveness, 0.0)
        // a pure Flying type is a Normal type meanwhile
        val bird = R17C8.mon(806, 50, "tackle"); val b2 = R17C8.battle(wild("tackle")); b2.player.volatiles["roost"] = 1
        assertEquals(listOf(Type.NORMAL), types(bird, b2, true))
    }

    @Test fun roostFailsAtFullHealth() {
        val m = R17C8.mon(810, 50, "tackle"); val b = R17C8.battle(wild("tackle"))
        assertTrue(forced("roost") { rd(m, b) }.failedMain())
    }

    // ---------------------------------------------------------------- Psycho Shift

    @Test fun psychoShiftMovesTheStatusToTheTargetAndCuresTheUser() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle")); b.player.status = "brn"
        val e = forced("psychoshift") { rd(m, b) }
        assertEquals("brn", b.enemy.status); assertNull(b.player.status)
        assertTrue("Slowpoke was burned!" in e.lines()); assertTrue("Fastie's burn was cured!" in e.lines())
    }

    @Test fun psychoShiftFailsWithoutAStatusOnATargetThatHasOneOrOneThatCannotGetIt() {
        val m = main("tackle"); val b = R17C8.battle(wild("tackle"))
        assertTrue("nothing to move", forced("psychoshift") { rd(m, b) }.failedMain())
        val m2 = main("tackle"); val b2 = R17C8.battle(wild("tackle")); b2.player.status = "brn"; b2.enemy.status = "par"
        assertTrue(forced("psychoshift") { rd(m2, b2) }.failedMain()); assertEquals("brn", b2.player.status); assertEquals("par", b2.enemy.status)
        val m3 = main("tackle"); val b3 = R17C8.battle(wild("tackle", sp = 802)); b3.player.status = "brn" // a Fire type can't be burned
        assertTrue(forced("psychoshift") { rd(m3, b3) }.failedMain()); assertEquals("brn", b3.player.status); assertNull(b3.enemy.status)
    }

    @Test fun theTypeMovesSayWhatTheyDoInPlainWords() {
        for (id in listOf("soak", "magicpowder", "reflecttype", "conversion", "conversion2", "forestscurse", "trickortreat", "camouflage", "burnup", "doubleshock",
            "roost", "electrify", "psychoshift")) {
            val text = MoveText.describe(data.move(id)!!)
            assertTrue("$id: $text", text.endsWith(".") && "Has no effect" !in text && "→" !in text)
        }
        assertFalse(MoveText.describe(data.move("magicpowder")!!).contains("  "))
    }
}
