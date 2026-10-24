package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * R17C FIX-A (coders' notes): Diamond Storm's `self: {chance: 50, boosts}` is a 50 % self boost (a secondary), not an always-on one; an empty
 * `secondary: {}` is no secondary; `selfdestruct: "ifHit"` (Final Gambit) is told apart from `"always"` (Explosion). The cache keeps format 4.
 */
class R17CFixAParserTest {
    private val json = """{
      "diamondstorm": {"num": 591, "accuracy": 95, "basePower": 100, "category": "Physical", "name": "Diamond Storm", "type": "Rock", "target": "allAdjacentFoes",
        "flags": {"protect": 1, "mirror": 1}, "self": {"chance": 50, "boosts": {"def": 2}}, "secondary": {}},
      "flamecharge": {"num": 488, "accuracy": 100, "basePower": 50, "category": "Physical", "name": "Flame Charge", "type": "Fire", "target": "normal",
        "secondary": {"chance": 100, "self": {"boosts": {"spe": 1}}}},
      "swordsdance": {"num": 14, "accuracy": true, "basePower": 0, "category": "Status", "name": "Swords Dance", "type": "Normal", "target": "self", "boosts": {"atk": 2}},
      "closecombat": {"num": 370, "accuracy": 100, "basePower": 120, "category": "Physical", "name": "Close Combat", "type": "Fighting", "target": "normal", "self": {"boosts": {"def": -1, "spd": -1}}},
      "finalgambit": {"num": 515, "accuracy": 100, "basePower": 0, "selfdestruct": "ifHit", "category": "Special", "name": "Final Gambit", "type": "Fighting", "target": "normal"},
      "explosion": {"num": 153, "accuracy": 100, "basePower": 250, "category": "Physical", "name": "Explosion", "type": "Normal", "target": "allAdjacent", "selfdestruct": "always"}
    }"""
    private val moves = Parsers.parseMoves(json)

    @Test fun diamondStormRaisesDefenseHalfOfTheTime() {
        val m = moves.getValue("diamondstorm")
        assertNull("not an always-on boost", m.selfBoosts)
        assertEquals(Secondary(50, selfBoosts = mapOf("def" to 2)), m.secondary)
        assertEquals(1, m.allSecondaries.size) // the empty `secondary: {}` is no second secondary
        assertFalse(m.secondaryOnHit)
        assertTrue(MoveText.describe(m), "May raise your Defense by 2 (50 %)." in MoveText.describe(m))
    }

    @Test fun otherSelfBoostsAreNotTouched() {
        assertEquals(mapOf("def" to -1, "spd" to -1), moves.getValue("closecombat").selfBoosts)
        assertNull(moves.getValue("closecombat").secondary)
        assertEquals(Secondary(100, selfBoosts = mapOf("spe" to 1)), moves.getValue("flamecharge").secondary)
    }

    @Test fun finalGambitNeedsAHitExplosionDoesNot() {
        val gambit = moves.getValue("finalgambit"); val explosion = moves.getValue("explosion")
        assertTrue(gambit.selfDestruct && gambit.selfDestructIfHit)
        assertTrue(explosion.selfDestruct && !explosion.selfDestructIfHit)
        assertFalse(moves.getValue("swordsdance").selfDestruct)
    }

    @Test fun theCacheKeepsTheNewShapeAndStaysFormat4() {
        val d = TestData.data(TestData.world().species.values.toList(), moves)
        val text = Parsers.encodeCache(d)
        assertTrue(text.contains("\"format\":4"))
        val back = Parsers.decodeCache(text)
        assertEquals(moves.getValue("diamondstorm"), back.moves.getValue("diamondstorm"))
        assertTrue(back.moves.getValue("finalgambit").selfDestructIfHit)
        assertFalse(back.moves.getValue("explosion").selfDestructIfHit)
        // a cache written before this fix (no "sh" key) reads the flag as false and still loads
        val old = text.replace(",\"sh\":1", "").replace("\"sh\":1,", "").replace("\"sh\":1", "")
        assertFalse(Parsers.decodeCache(old).moves.getValue("finalgambit").selfDestructIfHit)
    }

    /** The same checks on the real moves.json (run with --real). */
    @Test fun realDataDiamondStormAndTheSelfDestructMoves() {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) }
        assumeTrue("ANKIGO_REAL_DATA not set", dir != null)
        val real = Parsers.parseMoves(File(dir!!, "moves.json").readText())
        val ds = real.getValue("diamondstorm")
        assertNull(ds.selfBoosts); assertEquals(Secondary(50, selfBoosts = mapOf("def" to 2)), ds.secondary); assertFalse(ds.secondaryOnHit)
        assertEquals(setOf("finalgambit", "healingwish", "lunardance", "memento"), real.values.filter { it.selfDestructIfHit }.map { it.id }.toSet())
        assertTrue(listOf("explosion", "selfdestruct", "mistyexplosion").all { real.getValue(it).selfDestruct && !real.getValue(it).selfDestructIfHit })
        // no always-on self boost is left that Showdown gives a chance
        assertTrue(real.values.none { it.selfBoosts != null && it.id == "diamondstorm" })
    }
}
