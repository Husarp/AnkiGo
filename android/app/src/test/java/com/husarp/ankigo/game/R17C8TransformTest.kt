package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C8.attacks
import com.husarp.ankigo.game.R17C8.lines
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor
import kotlin.random.Random

/**
 * R17C-8: Transform (Ditto's only move): the types, stats except HP, stat changes, moves, ability and weight of the target for the rest of the
 * fight; both sides; the fail cases; the damage formula uses the copied stats. The picture does not change (nothing here touches the sprite).
 * "Blobby" (level 30) is the Ditto, "Torchy" a Fire type with very different stats.
 */
class R17C8TransformTest {
    private val data = R17C8.data
    private fun blobby(lv: Int = 30) = R17C8.mon(807, lv, "transform")
    private fun torchy() = R17C8.mon(802, 100, "ember", "flamethrower")
    private fun rd(main: Mon, b: Battle, rng: Random = Random(1), wildActs: Boolean = false) = R17C8.round(main, b, rng, enemyActs = wildActs)
    private fun <T> forced(id: String, block: () -> T): T = R17C8.withRules(R17C8.forcing(id), block = block)

    private fun hit(m: Mon, b: Battle, move: String, byMain: Boolean): Hit {
        val v = R17C8.view(m, b)
        val mv = data.move(move)!!
        return if (byMain) BattleEngine.newHit(v, true, m, b.player, b.wild, b.enemy, mv) else BattleEngine.newHit(v, false, b.wild, b.enemy, m, b.player, mv)
    }

    @Test fun aDittoWithOnlyTransformUsesItAndBecomesTheTarget() {
        val m = blobby(); val w = torchy(); val b = R17C8.battle(w)
        b.enemy.boosts[0] = 2; b.enemy.boosts[4] = -1
        val e = rd(m, b)
        assertEquals(listOf("Transform"), e.attacks().filter { it.byPlayer }.map { it.move })
        assertTrue("Blobby transformed into Torchy!" in e.lines())
        val v = R17C8.view(m, b)
        assertEquals(listOf(Type.FIRE), v.types(true))
        val ws = v.stats(false); val ms = v.stats(true)
        assertEquals(listOf(ws.atk, ws.def, ws.spa, ws.spd, ws.spe), listOf(ms.atk, ms.def, ms.spa, ms.spd, ms.spe))
        assertEquals("the HP stays its own", StatCalc.stats(data, m).hp, ms.hp)
        assertEquals(StatCalc.maxHp(data, m), v.maxHp(true))
        assertEquals(listOf("ember", "flamethrower"), v.moves(true).map { it.id })
        assertEquals(listOf(2, 0, 0, 0, -1, 0, 0), b.player.boosts.toList())
        assertEquals("the Pokémon's own list is untouched", listOf("transform"), m.moves)
    }

    @Test fun theTransformedPokemonFightsWithTheCopiedMoves() {
        val m = blobby(); val b = R17C8.battle(torchy())
        rd(m, b)
        val used = (1..4).flatMap { rd(m, b, Random(it)).attacks().filter { a -> a.byPlayer }.map { a -> a.moveId } }
        assertTrue(used.toString(), used.isNotEmpty() && used.all { it == "ember" || it == "flamethrower" })
    }

    @Test fun theDamageFormulaUsesTheCopiedStatsAndTheLevelStaysItsOwn() {
        val m = blobby(30); val w = torchy(); val b = R17C8.battle(w)
        val tackle = data.move("tackle")!!
        fun dmg() = BattleEngine.damage(data, m, b.player, w, b.enemy, tackle, Random(1), forceCrit = false, forceRoll = 100, view = R17C8.view(m, b), attackerIsMain = true).damage
        val before = dmg()
        rd(m, b)
        val s = R17C8.view(m, b).stats(false)
        // the games' formula, by hand: level 30, power 40, the copied Attack against Torchy's Defense; Fire type: no STAB for Tackle; Normal vs Fire x1
        val expected = floor(floor(2.0 * 30 / 5 + 2).toInt() * 40.0 * s.atk / s.def).toInt() / 50 + 2
        assertEquals(expected, dmg())
        assertNotEquals("the copied Attack is not the Ditto's own", before, dmg())
        // STAB follows the copied types: a Fire move of a Fire type gets it
        val ember = data.move("ember")!!
        val withStab = BattleEngine.damage(data, m, b.player, w, b.enemy, ember, Random(1), forceCrit = false, forceRoll = 100, view = R17C8.view(m, b), attackerIsMain = true)
        val raw = floor(floor(2.0 * 30 / 5 + 2).toInt() * 40.0 * s.spa / s.spd).toInt() / 50 + 2
        assertEquals(floor(floor(raw * 1.5) * 0.5).toInt(), withStab.damage) // STAB x1.5, Fire against Fire x0.5
    }

    @Test fun aTransformedPokemonWeighsWhatItsTargetWeighs() {
        val m = blobby(); val b = R17C8.battle(torchy())
        assertEquals(20, MovePower.basePower(hit(m, b, "lowkick", byMain = false))) // 4 kg: light
        rd(m, b)
        assertEquals(120, MovePower.basePower(hit(m, b, "lowkick", byMain = false))) // 250 kg
        assertEquals(250.0, MoveControl.transformedWeight(R17C8.view(m, b), m)!!, 0.0)
        assertEquals(null, MoveControl.transformedWeight(R17C8.view(m, b), b.wild))
    }

    @Test fun theWildOneCanTransformToo() {
        val m = R17C8.main("ember"); val w = R17C8.mon(807, 50, "transform"); val b = R17C8.battle(w)
        val e = rd(m, b, wildActs = true)
        assertEquals(listOf("Transform"), e.attacks().filter { !it.byPlayer }.map { it.move })
        val v = R17C8.view(m, b)
        assertEquals(v.stats(true).atk, v.stats(false).atk)
        assertEquals(v.types(true), v.types(false))
        assertEquals(listOf("ember"), v.moves(false).map { it.id })
        assertTrue("Blobby transformed into Fastie!" in e.lines())
    }

    @Test fun transformFailsOnATransformedTargetASubstituteAndAUserThatTransformedAlready() {
        // the target has transformed
        val b = R17C8.battle(R17C8.mon(807, 50, "tackle")); b.enemy.notes["mc.transform"] = "800"
        assertTrue(forced("transform") { rd(blobby(), b) }.attacks().single { it.byPlayer }.failed)
        assertFalse("mc.transform" in b.player.notes)
        // the target hides behind a substitute
        val b2 = R17C8.battle(torchy()); b2.enemy.volatiles["substitute"] = 20
        val e2 = forced("transform") { rd(blobby(), b2) }
        assertTrue(e2.attacks().single { it.byPlayer }.failed); assertTrue("But it failed!" in e2.lines())
        // the user has transformed already
        val m3 = blobby(); val b3 = R17C8.battle(torchy())
        rd(m3, b3)
        assertTrue(forced("transform") { rd(m3, b3) }.attacks().single { it.byPlayer }.failed)
    }

    @Test fun aDittoThatCannotTransformStrugglesInsteadOfLoopingForever() {
        val m = blobby(); val b = R17C8.battle(torchy()); b.enemy.volatiles["substitute"] = 20
        assertEquals(listOf("Struggle"), rd(m, b).attacks().filter { it.byPlayer }.map { it.move })
    }

    @Test fun theMainsStatTricksAndTransformAreNotMixedUp() {
        // a Ditto that used Power Trick before copies the target's numbers as they are: no swap is carried over
        val m = blobby(); val b = R17C8.battle(torchy())
        b.player.volatiles["powertrick"] = 1
        rd(m, b)
        val v = R17C8.view(m, b)
        assertEquals(v.stats(false).atk, v.stats(true).atk); assertEquals(v.stats(false).def, v.stats(true).def)
    }

    @Test fun transformSurvivesASave() {
        val m = blobby(); val b = R17C8.battle(torchy())
        b.enemy.boosts[2] = 3
        rd(m, b)
        val s = GameState(schema = GameState.SCHEMA)
        s.mons += Mon("u1", 19, "Ratty", 10, 0, Stats(15, 15, 15, 15, 15, 15), Stats.ZERO, Nature.HARDY, 'M', false, "x", mutableListOf("tackle"), 20)
        s.team += "u1"; s.battle = b
        val back = SaveCodec.decode(SaveCodec.encode(s)).battle!!
        val v0 = R17C8.view(m, b); val v1 = R17C8.view(m, back)
        assertEquals(v0.types(true), v1.types(true)); assertEquals(v0.stats(true), v1.stats(true))
        assertEquals(v0.moves(true).map { it.id }, v1.moves(true).map { it.id })
        assertEquals(3, back.player.boosts[2])
    }

    @Test fun transformTextIsPlainAndSaysTheSpriteStaysTheSame() {
        val text = MoveText.describe(data.move("transform")!!)
        assertTrue(text, text.startsWith("Turns you into the target until the fight ends: its types, stats (not HP), stat changes, moves and ability."))
        assertTrue("picture" in text && text.endsWith("."))
    }
}
