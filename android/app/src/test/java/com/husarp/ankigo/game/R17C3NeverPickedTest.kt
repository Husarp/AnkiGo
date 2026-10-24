package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C3.attacks
import com.husarp.ankigo.game.R17C3.effects
import com.husarp.ankigo.game.R17C3.lines
import com.husarp.ankigo.game.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * R17C-3 (PLAN 229 part 2): moves that mean nothing in a 1-vs-1 wild fight ([CoreRules.NEVER_PICKED]) are never picked by any move choice, say
 * why in their text, and do nothing when they are used anyway. The `--real` tests check the list and the rule data against the real moves.
 */
class R17C3NeverPickedTest {
    private val data = R17C3.data
    private val never = listOf("roar", "helpinghand", "focusenergy", "spite", "perishsong", "celebrate", "fling", "splash")

    @Test fun theListHasTheFiftyEightMovesOfTheAuditInSevenReasons() {
        assertEquals(58, CoreRules.NEVER_PICKED.size)
        val perReason = CoreRules.NEVER_PICKED.values.groupingBy { it }.eachCount()
        assertEquals(mapOf("partner" to 18, "switching" to 18, "items" to 12, "pp" to 2, "crits" to 4, "fight" to 1, "nothing" to 3), perReason)
        assertEquals(CoreRules.reasonText.keys, perReason.keys)
        for (r in CoreRules.reasonText.values) assertTrue(r, r.endsWith("."))
    }

    @Test fun noMoveChoiceEverPicksOneWhileThereIsAnotherMove() {
        for (choice in MoveChoice.entries) for (seed in 1..120) {
            val b = R17C3.battle(R17C3.wild(*never.toTypedArray(), "tackle"))
            val m = R17C3.main(*never.toTypedArray(), "tackle")
            val rng = Random(seed)
            assertEquals("$choice $seed Main", "tackle", MoveChooser.pickMain(choice, data, b, m, rng).id)
            assertEquals("$choice $seed wild", "tackle", MoveChooser.pickWild(choice, data, b, m, rng).id)
        }
    }

    @Test fun aMainWithOnlyNeverPickedMovesUsesStruggle() {
        for (choice in MoveChoice.entries) {
            val b = R17C3.battle(R17C3.wild("tackle")); val m = R17C3.main("splash", "roar", "celebrate")
            assertEquals("$choice", Tuning.STRUGGLE, MoveChooser.pickMain(choice, data, b, m, Random(3)).id)
        }
    }

    @Test fun aWildOneWithOnlyNeverPickedMovesStillUsesOneAndNothingHappens() {
        for (choice in MoveChoice.entries) {
            val b = R17C3.battle(R17C3.wild("splash", "roar")); val m = R17C3.main("tackle")
            val picked = (1..30).map { MoveChooser.pickWild(choice, data, b, m, Random(it)).id }.toSet()
            assertEquals("$choice", setOf("splash", "roar"), picked)
        }
        // in a round: "But nothing happened.", and nobody is hurt (the Magikarp feel)
        val m = R17C3.main("tackle"); val b = R17C3.battle(R17C3.wild("splash"))
        val hp = m.hp
        val e = R17C3.round(m, b, Random(1), enemyActs = true)
        val a = e.attacks().single { !it.byPlayer }
        assertTrue(a.failed); assertEquals(0, a.damage)
        assertTrue("But nothing happened." in e.lines())
        assertEquals(hp, m.hp)
    }

    @Test fun aNeverPickedMoveThatIsUsedAnywayDoesNothing() {
        val force = object : BattleRules {
            override fun forcedMove(v: BattleView, p: Boolean): Move? = if (p) v.data.move("fling") else v.data.move("roar")
        }
        R17C3.withRules(force) {
            val m = R17C3.main("tackle"); val w = R17C3.wild("tackle"); val b = R17C3.battle(w)
            val wildHp = w.hp; val hp = m.hp
            val e = R17C3.round(m, b, Random(1), enemyActs = true)
            assertEquals(wildHp, w.hp); assertEquals(hp, m.hp)
            assertTrue(e.attacks().all { it.failed })
            assertEquals(2, e.lines().count { it == "But nothing happened." })
        }
    }

    @Test fun theMoveTextSaysWhy() {
        assertEquals("Never used in AnkiGo battles: it needs a partner Pokémon.", MoveText.describe(data.move("helpinghand")!!))
        assertEquals("Never used in AnkiGo battles: it only works when Pokémon switch in or out.", MoveText.describe(data.move("roar")!!))
        assertEquals("Never used in AnkiGo battles: it needs held items.", MoveText.describe(data.move("fling")!!))
        assertEquals("Never used in AnkiGo battles: AnkiGo has no PP.", MoveText.describe(data.move("spite")!!))
        assertEquals("Never used in AnkiGo battles: AnkiGo has no critical hits.", MoveText.describe(data.move("focusenergy")!!))
        assertEquals("Never used in AnkiGo battles: it would end every fight on its own.", MoveText.describe(data.move("perishsong")!!))
        assertEquals("It does nothing, so it is never used.", MoveText.describe(data.move("celebrate")!!))
        assertEquals("It does nothing, so it is never used.", MoveText.describe(data.move("splash")!!))
        assertEquals("Deals damage with no extra effect.", MoveText.describe(data.move("tackle")!!))
    }

    @Test fun theConfusionAndLoveMovesAreTheOnesTheSetImplements() {
        assertEquals(setOf("confuseray", "supersonic", "sweetkiss", "teeterdance", "swagger", "flatter", "attract"), CoreRules.handles)
        assertTrue(CoreRules.handles.none { it in CoreRules.NEVER_PICKED })
    }

    // ---------------------------------------------------------------- the real data (--real)

    private fun realMoves(): Map<String, Move>? {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) } ?: return null
        return Parsers.parseMoves(File(dir, "moves.json").readText())
    }

    @Test fun realEveryNeverPickedMoveExistsAndSaysItsReason() {
        val moves = realMoves(); assumeTrue("ANKIGO_REAL_DATA not set", moves != null)
        moves!!
        val missing = CoreRules.NEVER_PICKED.keys.filter { it !in moves }
        assertTrue("not in the real data: $missing", missing.isEmpty())
        for ((id, reason) in CoreRules.NEVER_PICKED) {
            assertEquals(id, CoreRules.reasonText.getValue(reason), MoveText.describe(moves.getValue(id)))
        }
    }

    @Test fun realTheMovesTheRulesNeedAreFlaggedInTheData() {
        val moves = realMoves(); assumeTrue("ANKIGO_REAL_DATA not set", moves != null)
        moves!!
        // charge: the flag and Tuning's fallback list are the same 17 moves
        val charge = moves.values.filter { "charge" in it.flags }.map { it.id }.toSet()
        assertEquals(Tuning.CHARGE_MOVES, charge)
        assertTrue(moves.values.filter { CoreRules.isCharge(it) }.all { it.id in charge })
        // recharge: 10 moves, flag and self condition agree
        val recharge = moves.values.filter { CoreRules.isRecharge(it) }.map { it.id }.toSet()
        assertEquals(10, recharge.size)
        assertEquals(recharge, moves.values.filter { "recharge" in it.flags }.map { it.id }.toSet())
        assertEquals(recharge, moves.values.filter { it.selfVolatile == "mustrecharge" }.map { it.id }.toSet())
        // the locks and binding
        assertEquals(setOf("outrage", "petaldance", "ragingfury", "thrash"), moves.values.filter { it.selfVolatile == "lockedmove" }.map { it.id }.toSet())
        assertEquals(setOf("uproar"), moves.values.filter { it.selfVolatile == "uproar" }.map { it.id }.toSet())
        assertEquals(10, moves.values.count { it.volatileStatus == "partiallytrapped" })
        // flinch: 33 moves roll it, the fangs as a second secondary
        val flinch = moves.values.filter { m -> m.allSecondaries.any { it.flinch || it.volatileStatus == "flinch" } }
        assertEquals(33, flinch.size)
        assertTrue(moves.getValue("firefang").moreSecondaries.any { it.flinch })
        // confusion: the status moves and the 12 secondaries
        assertEquals(setOf("confuseray", "supersonic", "sweetkiss", "teeterdance", "swagger", "flatter"),
            moves.values.filter { it.volatileStatus == "confusion" }.map { it.id }.toSet())
        assertEquals(12, moves.values.count { m -> m.allSecondaries.any { it.volatileStatus == "confusion" } })
        // crash damage and the HP cost (Chloroblast has its own data key, CoreRules knows it by id)
        assertEquals(setOf("axekick", "highjumpkick", "jumpkick", "supercellslam"), moves.values.filter { it.crashDamage }.map { it.id }.toSet())
        assertEquals(setOf("mindblown", "steelbeam"), moves.values.filter { it.mindBlownRecoil }.map { it.id }.toSet())
        assertNotNull(moves["chloroblast"])
        // the code-only secondaries: Tri Attack, Dire Claw, Alluring Voice and Burning Jealousy are built; Throat Chop is R17C-8's; Anchor Shot and
        // Spirit Shackle (trap the target), Eerie Spell (PP), Stone Axe and Ceaseless Edge (hazards) have no meaning in AnkiGo and stay plain damage
        // (R17C FIX: Diamond Storm's Defense rise is now a real 50 % secondary, so it is no longer "on hit")
        val onHit = moves.values.filter { it.secondaryOnHit }.map { it.id }.toSet()
        val built = setOf("alluringvoice", "burningjealousy", "direclaw", "triattack")
        val left = setOf("anchorshot", "spiritshackle", "eeriespell", "throatchop", "stoneaxe", "ceaselessedge")
        assertEquals(built + left, onHit)
    }

    @Test fun realEveryChargeAndRecharge_MoveHasTheTextOfItsRule() {
        val moves = realMoves(); assumeTrue("ANKIGO_REAL_DATA not set", moves != null)
        moves!!
        for (m in moves.values.filter { CoreRules.isCharge(it) }) {
            val t = MoveText.describe(m)
            assertTrue("${m.id}: $t", "Charges on the first turn" in t && t.contains("with your next move."))
        }
        for (m in moves.values.filter { CoreRules.isRecharge(it) }) {
            assertTrue(m.id, "After a hit, your next move is a rest turn to recharge." in MoveText.describe(m))
        }
        for (m in moves.values.filter { m -> m.allSecondaries.any { it.flinch } }) {
            assertTrue(m.id, "flinch" in MoveText.describe(m))
        }
        assertFalse(moves.values.any { MoveText.describe(it).isBlank() })
    }
}
