package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Move
import com.husarp.ankigo.game.MoveText
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17C-FIX item 12: the Compare table's move cells show a power number exactly when [MoveText.showsPower] says it means something. The
 * parser stores 60 for the variable-power moves (Low Kick, Grass Knot, Return, Gyro Ball ...); the cell shows a dash for them, like the
 * details screen and the compare popup do.
 */
class R17CFixBCompareTest {
    private val world = TestData.world()
    private val extra = listOf(
        Move("lowkick", "Low Kick", Type.FIGHTING, Category.PHYSICAL, 60, 100),
        Move("grassknot", "Grass Knot", Type.GRASS, Category.SPECIAL, 60, 100),
        Move("returnmove", "Return", Type.NORMAL, Category.PHYSICAL, 102, 100), // not listed: a plain 102 stays a number
        Move("return", "Return", Type.NORMAL, Category.PHYSICAL, 60, 100),
        Move("gyroball", "Gyro Ball", Type.STEEL, Category.PHYSICAL, 60, 100),
        Move("counter", "Counter", Type.FIGHTING, Category.PHYSICAL, 60, 100),
    ).associateBy { it.id }
    private val data = GameData(world.species, world.moves + extra, world.growth, world.itemPrices)

    private fun cells(moves: List<String>): List<MoveCell> {
        val m: Mon = TestData.mon(data, 25, 30, moves, "a")
        val s = GameState(createdAt = 1L, starterChosen = true).also { it.seen += data.species.keys; it.mons += m }
        val t = CompareTable.build(data, s, listOf(ComparePick.Owned("a"), ComparePick.Dex(1)), false)
        return (t.row("moves").cells[0] as Cell.Moves).moves
    }

    @Test fun variablePowerMovesShowADashNotTheStoredSixty() {
        val c = cells(listOf("lowkick", "grassknot", "return", "gyroball"))
        for (cell in c) assertEquals(cell.name, DASH, cell.power)
        for (id in listOf("lowkick", "grassknot", "return", "gyroball", "counter")) assertFalse(id, MoveText.showsPower(data.move(id)!!))
    }

    @Test fun movesWithARealPowerStillShowIt() {
        val c = cells(listOf("tackle", "thunderbolt", "returnmove", "growl"))
        assertEquals(listOf("40", "90", "102", DASH), c.map { it.power })
        for (id in listOf("tackle", "thunderbolt", "returnmove")) assertTrue(id, MoveText.showsPower(data.move(id)!!))
    }

    @Test fun theCellAgreesWithTheRuleForEveryMoveOfTheFixtures() {
        for (mv in data.moves.values) {
            val cell = cells(listOf(mv.id)).single()
            assertEquals(mv.id, if (MoveText.showsPower(mv)) mv.power.toString() else DASH, cell.power)
        }
    }
}
