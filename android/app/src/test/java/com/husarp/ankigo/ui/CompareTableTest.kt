package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareTableTest {
    private val world = TestData.world()
    private val extra = listOf(
        TestData.species(900, "Mudfish", listOf(Type.WATER, Type.GROUND), TestData.stats(70, 60, 60, 50, 50, 40)),
        TestData.species(901, "Bugsteel", listOf(Type.STEEL, Type.BUG)),
    )
    private val data = GameData(world.species + extra.associateBy { it.num }, world.moves, world.growth, world.itemPrices)

    private fun state(vararg mons: Mon): GameState {
        val s = GameState(createdAt = 1L, starterChosen = true)
        s.seen += data.species.keys   // everything met, so evolution names show (R11-D hides unseen ones)
        mons.forEach { s.mons += it }
        return s
    }
    private fun mon(sp: Int, lvl: Int, uid: String, moves: List<String> = listOf("tackle")) = TestData.mon(data, sp, lvl, moves, uid)
    private fun texts(row: CompareRow) = row.cells.map { (it as Cell.Text).text }
    private fun bests(row: CompareRow) = row.cells.map { (it as Cell.Text).best }

    @Test fun bestIndexesHighestTiesAndNothingToCompare() {
        assertEquals(setOf(1), CompareTable.bestIndexes(listOf(3, 9, 5)))
        assertEquals(setOf(0, 2), CompareTable.bestIndexes(listOf(7, 4, 7)))
        assertEquals(setOf(0, 1), CompareTable.bestIndexes(listOf(5, 5)))
        assertEquals(emptySet<Int>(), CompareTable.bestIndexes(listOf(5, null, null)))
        assertEquals(setOf(0, 2), CompareTable.bestIndexes(listOf(8, null, 8, 3)))
        assertEquals(emptySet<Int>(), CompareTable.bestIndexes(emptyList()))
    }

    @Test fun ownedColumnsShowLevelStatsAndMarkBest() {
        val a = mon(25, 26, "a"); val b = mon(2, 24, "b")
        val t = CompareTable.build(data, state(a, b), listOf(ComparePick.Owned("a"), ComparePick.Owned("b")), false)
        assertEquals(listOf("Lv 26", "Lv 24"), texts(t.row("level")))
        assertEquals(listOf(true, false), bests(t.row("level")))
        val sa = StatCalc.stats(data, a); val sb = StatCalc.stats(data, b)
        assertEquals(listOf(sa.hp.toString(), sb.hp.toString()), texts(t.row("hp")))
        assertEquals(sa.spe > sb.spe, bests(t.row("spe"))[0])
        assertEquals(listOf(sa.total.toString(), sb.total.toString()), texts(t.row("total")))
        // base stats: Sparkmouse 35/55/40/50/50/90 (total 320) vs Leafbud 60/62/63/80/80/60 (total 405)
        assertEquals(listOf("320", "405"), texts(t.row("base_total")))
        assertEquals(listOf(false, true), bests(t.row("base_total")))
        assertEquals(listOf("35", "60"), texts(t.row("base_hp")))
        assertEquals(listOf("90", "60"), texts(t.row("base_spe")))
        assertEquals(listOf(true, false), bests(t.row("base_spe")))
    }

    @Test fun tiesMarkEveryone() {
        val a = mon(25, 20, "a"); val b = mon(25, 20, "b")
        val t = CompareTable.build(data, state(a, b), listOf(ComparePick.Owned("a"), ComparePick.Owned("b")), false)
        assertEquals(listOf(true, true), bests(t.row("level")))
        assertEquals(listOf(true, true), bests(t.row("base_total")))
    }

    @Test fun pokedexEntryHasNoLevelStatsIvsOrMoves() {
        val a = mon(25, 26, "a", listOf("tackle", "thunderbolt"))
        val t = CompareTable.build(data, state(a), listOf(ComparePick.Owned("a"), ComparePick.Dex(3)), false)
        assertEquals(listOf("Lv 26", DASH), texts(t.row("level")))
        assertEquals(listOf(false, false), bests(t.row("level"))) // only one value: nothing to compare
        assertEquals(DASH, (t.row("hp").cells[1] as Cell.Text).text)
        assertEquals(DASH, (t.row("total").cells[1] as Cell.Text).text)
        assertEquals(DASH, (t.row("nature").cells[1] as Cell.Text).text)
        assertEquals(DASH, (t.row("ivs").cells[1] as Cell.Text).text)
        assertEquals(DASH, (t.row("moves").cells[1] as Cell.Text).text)
        assertNull(t.columns[1].mon)
        // base stats and matchups still compare
        assertEquals(listOf("320", "525"), texts(t.row("base_total")))
        assertEquals(listOf(false, true), bests(t.row("base_total")))
        assertEquals(Cell.TypeChips(listOf(Type.GRASS, Type.POISON)), t.row("types").cells[1])
    }

    @Test fun onlyPokedexEntriesDropTheOwnedRows() {
        val t = CompareTable.build(data, state(), listOf(ComparePick.Dex(25), ComparePick.Dex(26)), false)
        for (k in listOf("level", "hp", "total", "nature", "ivs", "moves")) assertNull(k, t.rowOrNull(k))
        assertEquals(listOf("320", "485"), texts(t.row("base_total")))
        assertEquals(listOf(false, true), bests(t.row("base_total")))
    }

    @Test fun evolvedStatsUseTheEvolvedSpeciesWithOwnIvsEvsAndNature() {
        val m = mon(25, 26, "a").also {
            it.evs = Stats(0, 100, 0, 0, 0, 0)
        }
        // Mon.nature is a val: build the Pokémon again with Adamant
        val adamant = Mon(m.uid, 25, m.name, 26, 0, m.ivs, m.evs, Nature.ADAMANT, 'M', false, "x", mutableListOf("tackle"), 1)
        val t = CompareTable.build(data, state(adamant), listOf(ComparePick.Owned("a"), ComparePick.Dex(3)), true)
        // Sparkrat base 60/90/55/90/80/110, IV 15, Lv 26, Adamant (+Atk -SpA), EV 100 on Atk
        // hp: 10+26+floor((120+15)*26/100)=10+26+35 = 71
        // atk: floor((5+floor((180+15+25)*26/100=57.2)=57)=62 *1.1=68.2) = 68
        // def: 5+floor((110+15)*.26=32.5)=37
        // spa: floor((5+floor((180+15)*.26=50.7)=50)=55 *0.9=49.5) = 49
        // spd: 5+floor((160+15)*.26=45.5)=50
        // spe: 5+floor((220+15)*.26=61.1)=66
        assertEquals(listOf("71", DASH), texts(t.row("hp")))
        assertEquals("68", texts(t.row("atk"))[0])
        assertEquals("37", texts(t.row("def"))[0])
        assertEquals("49", texts(t.row("spa"))[0])
        assertEquals("50", texts(t.row("spd"))[0])
        assertEquals("66", texts(t.row("spe"))[0])
        assertEquals((71 + 68 + 37 + 49 + 50 + 66).toString(), texts(t.row("total"))[0])
        // same maths as StatCalc on the evolved species
        assertEquals(StatCalc.stats(data.sp(26), adamant).total.toString(), texts(t.row("total"))[0])
        // the column shows the evolved species; the Level row is unchanged
        assertEquals(26, t.columns[0].shown.num)
        assertEquals("Sparkrat", t.columns[0].name)
        assertEquals("from Sparkmouse", t.columns[0].note)
        assertEquals("Lv 26", texts(t.row("level"))[0])
        assertEquals(Cell.TypeChips(listOf(Type.ELECTRIC)), t.row("types").cells[0])
        assertEquals("485", texts(t.row("base_total"))[0]) // Sparkrat 60+90+55+90+80+110
        // the Dex entry (Leafking, already final) is untouched
        assertEquals("already final", t.columns[1].note)
        assertEquals(3, t.columns[1].shown.num)
    }

    @Test fun nowModeKeepsThePickedSpecies() {
        val a = mon(25, 26, "a")
        val t = CompareTable.build(data, state(a), listOf(ComparePick.Owned("a"), ComparePick.Dex(25)), false)
        assertEquals(25, t.columns[0].shown.num)
        assertNull(t.columns[0].note)
        assertEquals(StatCalc.stats(data, a).hp.toString(), texts(t.row("hp"))[0])
    }

    @Test fun branchedLineTakesTheFirstBranch() {
        val a = mon(133, 18, "a")
        val t = CompareTable.build(data, state(a), listOf(ComparePick.Owned("a"), ComparePick.Dex(1)), true)
        assertEquals(134, t.columns[0].shown.num)
        assertEquals("from Fluffy · first of 3", t.columns[0].note)
        assertEquals("Fluffwave", t.columns[0].name)
        assertEquals(Cell.TypeChips(listOf(Type.WATER)), t.row("types").cells[0])
        val evo = t.row("evo").cells[0] as Cell.Evo
        assertEquals(listOf("Fluffy", "Fluffwave"), evo.names)
        assertEquals(0, evo.mark)
        assertEquals("first of 3 branches", evo.note)
        val becomes = t.row("becomes").cells[0] as Cell.Text
        assertEquals("Fluffwave", becomes.text)
        assertEquals(listOf("first of 3"), becomes.sub)
    }

    @Test fun pickedBranchMemberKeepsItsOwnBranchAndLine() {
        val t = CompareTable.build(data, state(), listOf(ComparePick.Dex(135), ComparePick.Dex(134)), false)
        val evo = t.row("evo").cells[0] as Cell.Evo
        assertEquals(listOf("Fluffy", "Fluffbolt"), evo.names)
        assertEquals(1, evo.mark)
        assertNull(evo.note)
        assertEquals("already final", (t.row("becomes").cells[0] as Cell.Text).text)
    }

    @Test fun alreadyFinalStaysTheSameColumn() {
        val a = mon(3, 33, "a")
        val now = CompareTable.build(data, state(a), listOf(ComparePick.Owned("a"), ComparePick.Dex(1)), false)
        val evo = CompareTable.build(data, state(a), listOf(ComparePick.Owned("a"), ComparePick.Dex(1)), true)
        assertEquals(texts(now.row("hp"))[0], texts(evo.row("hp"))[0])
        assertEquals(texts(now.row("total"))[0], texts(evo.row("total"))[0])
        assertEquals("already final", evo.columns[0].note)
        assertEquals(3, evo.columns[0].shown.num)
        assertEquals("already final", (evo.row("becomes").cells[0] as Cell.Text).text)
        // Leafling (Dex) does evolve, all the way to Leafking
        assertEquals("Leafking", (evo.row("becomes").cells[1] as Cell.Text).text)
        assertEquals(3, evo.columns[1].shown.num)
        assertEquals("from Leafling", evo.columns[1].note)
    }

    @Test fun evolutionLines() {
        val t = CompareTable.build(data, state(), listOf(ComparePick.Dex(25), ComparePick.Dex(3), ComparePick.Dex(172)), false)
        val l0 = t.row("evo").cells[0] as Cell.Evo
        assertEquals("Sparklet → Sparkmouse → Sparkrat", l0.line)
        assertEquals(1, l0.mark)
        val l1 = t.row("evo").cells[1] as Cell.Evo
        assertEquals("Leafling → Leafbud → Leafking", l1.line)
        assertEquals(2, l1.mark)
        val l2 = t.row("evo").cells[2] as Cell.Evo
        assertEquals(0, l2.mark)
        assertEquals("Sparklet → Sparkmouse → Sparkrat", l2.line)
        assertEquals("Sparkrat", (t.row("becomes").cells[0] as Cell.Text).text)
        assertEquals("Sparkrat", (t.row("becomes").cells[2] as Cell.Text).text)
    }

    @Test fun matchupsWaterGround() {
        val (weak, resist, immune) = CompareTable.matchups(listOf(Type.WATER, Type.GROUND))
        assertEquals(listOf(Matchup(Type.GRASS, 4.0)), weak)
        assertEquals("×4", weak[0].label)
        assertEquals(listOf(Type.FIRE, Type.POISON, Type.ROCK, Type.STEEL), resist.map { it.type })
        assertTrue(resist.all { it.mult == 0.5 && it.label == "×½" })
        assertEquals(listOf(Matchup(Type.ELECTRIC, 0.0)), immune)
        assertEquals("×0", immune[0].label)
    }

    @Test fun matchupsQuarterResistanceComesFirst() {
        val (weak, resist, immune) = CompareTable.matchups(listOf(Type.STEEL, Type.BUG))
        assertEquals(listOf(Matchup(Type.FIRE, 4.0)), weak.filter { it.mult == 4.0 })
        val grass = resist.first { it.type == Type.GRASS }
        assertEquals(0.25, grass.mult, 0.0)
        assertEquals("×¼", grass.label)
        assertEquals(0.25, resist.first().mult, 0.0)
        assertEquals(listOf(Type.POISON), immune.map { it.type }) // Steel is immune to Poison
        assertTrue(weak.zipWithNext().all { (x, y) -> x.mult >= y.mult })
    }

    @Test fun matchupRowsUseTheShownSpecies() {
        val t = CompareTable.build(data, state(), listOf(ComparePick.Dex(900), ComparePick.Dex(901)), false)
        assertEquals(listOf(Matchup(Type.GRASS, 4.0)), (t.row("weak").cells[0] as Cell.Matchups).entries)
        assertEquals(listOf(Matchup(Type.ELECTRIC, 0.0)), (t.row("immune").cells[0] as Cell.Matchups).entries)
        assertEquals(listOf(Type.POISON), (t.row("immune").cells[1] as Cell.Matchups).entries.map { it.type })
        // "Fully evolved": Flopfish (Water) becomes Water/Flying: Electric x4
        val f = CompareTable.build(data, state(), listOf(ComparePick.Dex(129), ComparePick.Dex(900)), true)
        assertEquals(Matchup(Type.ELECTRIC, 4.0), (f.row("weak").cells[0] as Cell.Matchups).entries.first())
    }

    @Test fun buildSectionHeadingsAreSet() {
        val t = CompareTable.build(data, state(mon(25, 20, "a")), listOf(ComparePick.Owned("a"), ComparePick.Dex(1)), false)
        assertEquals(listOf("Stats", "Base stats", "Build", "Moves", "Matchups", "Evolution"), t.rows.mapNotNull { it.section })
    }

    @Test fun natureIvsAndMovesOfOwnedMons() {
        val m = Mon("a", 25, "Sparkmouse", 20, 0, Stats(31, 20, 10, 5, 0, 31), Stats.ZERO, Nature.TIMID, 'F', false, "x",
            mutableListOf("thunderbolt", "growl", "nope"), 1)
        val n = mon(2, 20, "b", listOf("tackle"))
        val t = CompareTable.build(data, state(m, n), listOf(ComparePick.Owned("a"), ComparePick.Owned("b")), false)
        val nat = t.row("nature").cells
        assertEquals("Timid", (nat[0] as Cell.Text).text)
        assertEquals(listOf("+Spe −Atk"), (nat[0] as Cell.Text).sub)
        assertEquals(listOf("neutral"), (nat[1] as Cell.Text).sub)
        val iv0 = t.row("ivs").cells[0] as Cell.Text
        assertEquals("97 / 186", iv0.text)
        assertEquals(listOf("31 · 20 · 10", "5 · 0 · 31"), iv0.sub)
        assertEquals("90 / 186", (t.row("ivs").cells[1] as Cell.Text).text) // Leafbud: 15 each
        assertEquals(listOf(true, false), t.row("ivs").cells.map { (it as Cell.Text).best })
        val mv = (t.row("moves").cells[0] as Cell.Moves).moves
        assertEquals(MoveCell("Thunderbolt", Type.ELECTRIC, "90", "thunderbolt"), mv[0])
        assertEquals(MoveCell("Growl", Type.NORMAL, DASH, "growl"), mv[1])
        assertEquals(MoveCell("nope", null, DASH, "nope"), mv[2])
    }

    @Test fun nicknameIsKeptAndMissingPicksAreSkipped() {
        val a = mon(25, 20, "a").also { it.nickname = "Zap" }
        val t = CompareTable.build(data, state(a), listOf(ComparePick.Owned("a"), ComparePick.Owned("gone"), ComparePick.Dex(99999), ComparePick.Dex(1)), true)
        assertEquals(2, t.columns.size)
        assertEquals("Zap", t.columns[0].name)
        assertEquals("Leafking", t.columns[1].name)
        assertEquals(2, t.row("types").cells.size)
    }

    @Test fun everyRowHasOneCellPerColumn() {
        val t = CompareTable.build(data, state(mon(25, 20, "a"), mon(133, 10, "b")),
            listOf(ComparePick.Owned("a"), ComparePick.Owned("b"), ComparePick.Dex(150), ComparePick.Dex(1)), true)
        assertTrue(t.rows.all { it.cells.size == 4 })
    }

    @Test fun finalFormCyclesAreSafe() {
        val a = TestData.species(910, "Loopa", evolutions = listOf(com.husarp.ankigo.game.Evolution(911, com.husarp.ankigo.game.EvoKind.LEVEL, level = 5)))
        val b = TestData.species(911, "Loopb", prevo = 910, evolutions = listOf(com.husarp.ankigo.game.Evolution(910, com.husarp.ankigo.game.EvoKind.LEVEL, level = 5)))
        val d = TestData.data(listOf(a, b))
        assertEquals(911, CompareTable.finalForm(d, a).species.num)
        assertEquals(2, CompareTable.evolutionLine(d, a).names.size)
    }

    @Test fun unseenFormsInTheEvolutionLineShowQuestionMarks() {
        val st = GameState(createdAt = 1L, starterChosen = true)
        st.seen += 25   // only Sparkmouse (the middle form) is met
        val t = CompareTable.build(data, st, listOf(ComparePick.Dex(25)), false)
        val l = t.row("evo").cells[0] as Cell.Evo
        assertEquals("??? → Sparkmouse → ???", l.line)
        assertEquals("???", (t.row("becomes").cells[0] as Cell.Text).text)
        st.seen += 172
        assertEquals("Sparklet → Sparkmouse → ???", (CompareTable.build(data, st, listOf(ComparePick.Dex(25)), false).row("evo").cells[0] as Cell.Evo).line)
    }

    @Test fun anUnseenFinalFormStaysHiddenInFullyEvolved() {
        val st = GameState(createdAt = 1L, starterChosen = true)
        st.seen += 25
        val col = CompareTable.build(data, st, listOf(ComparePick.Dex(25)), true).columns[0]
        assertEquals("???", col.name)
        assertTrue(col.hidden)
        st.seen += 26
        val seen = CompareTable.build(data, st, listOf(ComparePick.Dex(25)), true).columns[0]
        assertEquals(seen.shown.name, seen.name)
        assertFalse(seen.hidden)
    }
}
