package com.husarp.ankigo.ui

import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R9-B10: "Fully evolved" shows an owned Pokémon at the level its final form is first reached. */
class CompareEvolvedLevelTest {
    private val world = TestData.world()
    private val extra = listOf(
        // a stone line, then a level step: stone adds nothing, 20 does
        TestData.species(920, "Stonea", evolutions = listOf(Evolution(921, EvoKind.ITEM, item = "fire-stone"))),
        TestData.species(921, "Stoneb", prevo = 920, evolutions = listOf(Evolution(922, EvoKind.LEVEL, level = 20))),
        TestData.species(922, "Stonec", prevo = 921),
        // a pure stone line
        TestData.species(930, "Gema", evolutions = listOf(Evolution(931, EvoKind.ITEM, item = "water-stone"))),
        TestData.species(931, "Gemb", prevo = 930),
        // a branching line: the first branch is a level 25 step, the second a level 50 step
        TestData.species(940, "Forka", evolutions = listOf(Evolution(941, EvoKind.LEVEL, level = 25), Evolution(942, EvoKind.LEVEL, level = 50))),
        TestData.species(941, "Forkb", prevo = 940), TestData.species(942, "Forkc", prevo = 940),
        // a non-level kind that still carries a level
        TestData.species(950, "Lvlfriend", evolutions = listOf(Evolution(951, EvoKind.FRIENDSHIP, level = 30, minFriendship = 160))),
        TestData.species(951, "Lvlfriend2", prevo = 950),
    )
    private val data = GameData(world.species + extra.associateBy { it.num }, world.moves, world.growth, world.itemPrices)

    private fun state(vararg mons: Mon) = GameState(createdAt = 1L, starterChosen = true).also { s -> mons.forEach { s.mons += it } }
    private fun mon(sp: Int, lvl: Int, uid: String = "a") = TestData.mon(data, sp, lvl, uid = uid)
    private fun levelCell(t: CompareData, i: Int = 0) = t.row("level").cells[i] as Cell.Text
    private fun build(m: Mon, evolved: Boolean, vararg more: ComparePick) =
        CompareTable.build(data, state(m), listOf<ComparePick>(ComparePick.Owned(m.uid)) + more, evolved)

    @Test fun threeStageLineLv5GoesToTheLastLevelStep() {
        // Leafling -> Leafbud (Lv 16) -> Leafking (Lv 32)
        val m = mon(1, 5)
        assertEquals(32, CompareTable.evolvedLevel(data, data.sp(1), m))
        val t = build(m, true)
        val c = levelCell(t)
        assertEquals("Lv 32", c.text)
        assertEquals(32, c.value)
        assertEquals(32, c.level)
        assertEquals(listOf("evolves"), c.sub)
        assertEquals(3, t.columns[0].shown.num)
    }

    @Test fun midLineGoesOnlyToTheRemainingStep() {
        val m = mon(2, 20)
        assertEquals(32, CompareTable.evolvedLevel(data, data.sp(2), m))
        assertEquals("Lv 32", levelCell(build(m, true)).text)
    }

    @Test fun alreadyPastTheLevelsKeepsItsLevelWithoutTheSuffix() {
        val m = mon(1, 40)
        assertEquals(40, CompareTable.evolvedLevel(data, data.sp(1), m))
        val c = levelCell(build(m, true))
        assertEquals("Lv 40", c.text)
        assertEquals(40, c.value)
        assertTrue(c.sub.isEmpty())
    }

    @Test fun aLevelBetweenTheStepsCountsOnlyTheHigherOne() {
        val m = mon(1, 20) // past the Lv 16 step, short of Lv 32
        assertEquals(32, CompareTable.evolvedLevel(data, data.sp(1), m))
    }

    @Test fun alreadyFinalIsPlain() {
        val m = mon(3, 5)
        assertEquals(5, CompareTable.evolvedLevel(data, data.sp(3), m))
        val c = levelCell(build(m, true))
        assertEquals("Lv 5", c.text)
        assertTrue(c.sub.isEmpty())
    }

    @Test fun aStoneStepAddsNothing() {
        val pure = mon(930, 7)
        assertEquals(7, CompareTable.evolvedLevel(data, data.sp(930), pure))
        val c = levelCell(build(pure, true))
        assertEquals("Lv 7", c.text)
        assertTrue(c.sub.isEmpty()) // the form changed but the level did not
        // stone first, then a Lv 20 step: only the 20 counts
        assertEquals(20, CompareTable.evolvedLevel(data, data.sp(920), mon(920, 3)))
        assertEquals(30, CompareTable.evolvedLevel(data, data.sp(920), mon(920, 30)))
    }

    @Test fun aBranchFollowsTheFirstBranch() {
        val m = mon(940, 10)
        assertEquals(25, CompareTable.evolvedLevel(data, data.sp(940), m)) // not 50
        val t = build(m, true)
        assertEquals(941, t.columns[0].shown.num)
        assertEquals("Lv 25", levelCell(t).text)
    }

    @Test fun anotherKindWithALevelCounts() {
        assertEquals(30, CompareTable.evolvedLevel(data, data.sp(950), mon(950, 4)))
    }

    @Test fun statsUseTheEffectiveLevel() {
        val m = mon(1, 5)
        val t = build(m, true)
        val at32 = StatCalc.stats(data.sp(3), m, 32)
        assertEquals(at32.hp.toString(), (t.row("hp").cells[0] as Cell.Text).text)
        assertEquals(at32.total.toString(), (t.row("total").cells[0] as Cell.Text).text)
        assertEquals(StatCalc.stats(data.sp(3), m, m.level).total < at32.total, true)
        // the plain overload is the current level
        assertEquals(StatCalc.stats(data.sp(3), m), StatCalc.stats(data.sp(3), m, 5))
    }

    @Test fun notFullyEvolvedIsUnchanged() {
        val m = mon(1, 5)
        val t = build(m, false)
        val c = levelCell(t)
        assertEquals("Lv 5", c.text)
        assertEquals(5, c.value)
        assertTrue(c.sub.isEmpty())
        assertEquals(StatCalc.stats(data.sp(1), m).total.toString(), (t.row("total").cells[0] as Cell.Text).text)
    }

    @Test fun aDexColumnIsUnchangedAndTheOwnedOneStillRaised() {
        val m = mon(1, 5)
        val t = build(m, true, ComparePick.Dex(1))
        assertEquals(3, t.columns[1].shown.num)
        assertEquals(null, t.columns[1].mon)
        val cells = t.row("level").cells
        assertEquals("Lv 32", (cells[0] as Cell.Text).text)
        assertTrue((cells[1] as Cell.Text).empty) // a Dex entry has no level
        assertTrue(t.rows.first { it.key == "total" }.cells[1].let { (it as Cell.Text).empty })
    }

    @Test fun twoOwnedColumnsCompareOnTheEffectiveLevel() {
        val a = mon(1, 5, "a"); val b = mon(3, 31, "b")
        val t = CompareTable.build(data, state(a, b), listOf(ComparePick.Owned("a"), ComparePick.Owned("b")), true)
        assertEquals(listOf(32, 31), t.row("level").cells.map { (it as Cell.Text).value })
        assertEquals(listOf(true, false), t.row("level").cells.map { (it as Cell.Text).best })
    }
}
