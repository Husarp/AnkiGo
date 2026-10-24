package com.husarp.ankigo.ui

import com.husarp.ankigo.game.EvoNode
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * R18-G: the pure parts of the Pokémon details round: the Holding lines (239B / 239S1), the evolution grid rule (243B), the arrow's place
 * (260A), the "Stats · Lv N" title (254A), the 48 dp rows (241) and the texts of the new explanations.
 */
class R18GDetailsTest {
    private val data = TestData.world()

    // ---------- 239: the Holding lines ----------

    private fun mon(held: String? = null, uid: String = "m1") = TestData.mon(data, 25, 23, uid = uid).also { it.heldItem = held }

    @Test fun anItemShowsItsNameAndTheBagsRowTextUnderIt() {
        val lines = HoldingPlan.lines(data, mon("amulet-coin"), xpShareUid = null, mainUid = "other")
        assertEquals(1, lines.size)
        assertEquals(HoldLine.Kind.ITEM, lines[0].kind)
        assertEquals("amulet-coin", lines[0].item)
        assertEquals("Amulet Coin", lines[0].name)
        assertEquals("Main holds it: ×2 money", lines[0].sub)
    }

    @Test fun theExpShareHolderGetsATealLineWithTheRule() {
        val lines = HoldingPlan.lines(data, mon(), xpShareUid = "m1", mainUid = "other")
        assertEquals(1, lines.size)
        assertEquals(HoldLine.Kind.EXP_SHARE, lines[0].kind)
        assertNull(lines[0].item)
        assertEquals("Exp. Share", lines[0].name)
        assertEquals("It and your Main each get half the XP", lines[0].sub)
    }

    @Test fun anItemAndTheExpShareShowBothItemFirst() {
        val lines = HoldingPlan.lines(data, mon("lucky-egg"), xpShareUid = "m1", mainUid = "other")
        assertEquals(listOf(HoldLine.Kind.ITEM, HoldLine.Kind.EXP_SHARE), lines.map { it.kind })
        assertEquals("Lucky Egg", lines[0].name)
    }

    @Test fun nothingHeldIsNoLine() {
        assertTrue(HoldingPlan.lines(data, mon(), xpShareUid = null, mainUid = "other").isEmpty())
        // somebody else holds the Exp. Share
        assertTrue(HoldingPlan.lines(data, mon(), xpShareUid = "m2", mainUid = "other").isEmpty())
    }

    @Test fun theExpShareOnTheMainSaysItDoesNothingThere() {
        // Progression shares nothing when the holder is the Main: the line must not promise half the XP
        val lines = HoldingPlan.lines(data, mon(), xpShareUid = "m1", mainUid = "m1")
        assertEquals("Does nothing while it is your Main", lines.single().sub)
    }

    @Test fun giveItemShowsOnlyForNothingHeldAndOnlyWithOptions() {
        val none = emptyList<HoldLine>()
        assertTrue(HoldingPlan.showGive(none, hasOptions = true))
        assertFalse("a window without Options has no Give item", HoldingPlan.showGive(none, hasOptions = false))
        val one = HoldingPlan.lines(data, mon("lucky-egg"), null, null)
        assertFalse(HoldingPlan.showGive(one, hasOptions = true))
        assertFalse(HoldingPlan.showGive(HoldingPlan.lines(data, mon(), "m1", "other"), hasOptions = true))
    }

    @Test fun giveableIsWhatOptionsGiveItemLists() {
        val st = TestData.state(data, 25, 23)
        val m = st.mons[0]
        st.bag["lucky-egg"] = 1; st.bag["amulet-coin"] = 0; st.bag["potion"] = 3; st.bag["everstone"] = 2
        assertEquals(setOf("lucky-egg", "everstone"), HoldingPlan.giveable(st, m).toSet())
        m.heldItem = "everstone"
        assertEquals("the item it holds is not offered again", listOf("lucky-egg"), HoldingPlan.giveable(st, m))
        st.bag.clear()
        assertTrue(HoldingPlan.giveable(st, m).isEmpty())
    }

    // ---------- 243B: the grid ----------

    private fun node(num: Int, vararg kids: EvoNode) =
        EvoNode(TestData.species(num), if (num == 1) null else Evolution(num, EvoKind.ITEM, item = "water-stone"), kids.toList())

    @Test fun moreThanTwoLastFormsWrapIntoAGrid() {
        val eevee = node(1, *(2..9).map { node(it) }.toTypedArray())
        assertTrue(EvoGrid.isGrid(eevee))
        assertTrue("three targets are a grid too", EvoGrid.isGrid(node(1, node(2), node(3), node(4))))
    }

    @Test fun twoBranchesStayStackedRows() {
        assertFalse(EvoGrid.isGrid(node(1, node(2), node(3))))
        assertFalse(EvoGrid.isGrid(node(1, node(2))))
        assertFalse(EvoGrid.isGrid(node(1)))
    }

    @Test fun aTargetThatEvolvesOnKeepsTheStackedRows() {
        // Applin-like: a grid would drop the grandchild
        assertFalse(EvoGrid.isGrid(node(1, node(2), node(3), node(4, node(5)))))
    }

    @Test fun threePerLine() {
        assertEquals(3, EvoGrid.PER_ROW)
        assertEquals(1, EvoGrid.lines(3)); assertEquals(2, EvoGrid.lines(4)); assertEquals(3, EvoGrid.lines(8)); assertEquals(3, EvoGrid.lines(9))
    }

    // ---------- 260A: the arrow's place ----------

    /** Where the arrow's centre ends up, from the top of a row [rowH] tall whose Pokémon box is centred in it (the row centres the arrow column too). */
    private fun arrowCentre(boxH: Int, anchorY: Int, arrowH: Int, labelH: Int, rowH: Int): Pair<Double, Double> {
        val col = ArrowGeometry.column(boxH, anchorY, arrowH, labelH)
        val rowTall = maxOf(rowH, col.height, boxH)
        val colTop = (rowTall - col.height) / 2
        val boxTop = (rowTall - boxH) / 2
        // label inside the column, under the arrow
        if (labelH > 0) assertTrue("label inside the column: ${col.labelTop}+$labelH <= ${col.height}", col.labelTop + labelH <= col.height)
        assertTrue("arrow inside the column", col.arrowTop >= 0)
        assertEquals("the label hangs right under the arrow", col.arrowTop + arrowH, col.labelTop)
        return (colTop + col.arrowTop + arrowH / 2.0) to (boxTop + anchorY).toDouble()
    }

    @Test fun theArrowStandsOnThePicturesCentreWhateverTheLabel() {
        // xhdpi (2 px per dp): a 48 dp picture with 4 dp above it, a 14 dp name line, 4 dp below; the arrow is 14 dp tall; a label line is 12.5 dp
        val boxH = (4 + 48 + 14 + 4) * 2; val anchorY = (4 + 24) * 2; val arrowH = 28
        for (lines in 0..3) for (rowH in listOf(boxH, 300, 520)) {
            val (arrow, picture) = arrowCentre(boxH, anchorY, arrowH, (lines * 12.5 * 2).toInt(), rowH)
            assertTrue("$lines label lines, row $rowH: arrow $arrow, picture $picture", abs(arrow - picture) <= 1.0)
        }
    }

    @Test fun theArrowDoesNotMoveBetweenOneAndThreeLinesOfLabel() {
        val boxH = 140; val anchorY = 56; val arrowH = 28
        // the row grows by itself for a long label, so what must stay put is the arrow against its picture, not against the row's top
        val off = (0..3).map { lines -> arrowCentre(boxH, anchorY, arrowH, lines * 25, boxH).let { (arrow, picture) -> arrow - picture } }
        assertTrue("the arrow's distance from its picture's centre over 0..3 label lines: $off", off.max() - off.min() <= 1.0)
    }

    @Test fun theColumnOnlyGrowsWhenALongLabelNeedsIt() {
        val short = ArrowGeometry.column(140, 56, 28, 25).height
        assertEquals("one or two lines fit beside a Pokémon box", 140, short)
        assertTrue("three lines of label make the column taller", ArrowGeometry.column(140, 56, 28, 75).height > 140)
    }

    @Test fun smallPicturesWork() {
        // a 28 dp picture, no name room difference: still on the centre
        val boxH = (4 + 28 + 13 + 4) * 2; val anchorY = (4 + 14) * 2
        for (lines in 0..3) {
            val (arrow, picture) = arrowCentre(boxH, anchorY, 28, lines * 24, boxH)
            assertTrue("small, $lines lines: $arrow vs $picture", abs(arrow - picture) <= 1.0)
        }
    }

    // ---------- 254A / 241 / 273: texts and sizes ----------

    @Test fun theStatsTitleCarriesTheLevel() {
        assertEquals("Stats · Lv 23", DetailFormat.levelTitle("Stats", 23))
        assertEquals("Stats · Lv 100", DetailFormat.levelTitle("Stats", 100))
    }

    @Test fun theLineUnderTheNameHasNoLevel() {
        assertEquals("#025 · Hasty", DetailFormat.numLine(25, "Hasty"))
        assertEquals("#133 · Calm", DetailFormat.numLine(133, "Calm"))
    }

    @Test fun everyTapRowIs48DpHigh() {
        assertEquals(48, DetailFormat.TAP_MIN_DP)
    }

    @Test fun theNewTitlesHaveTrueShortExplanations() {
        for (i in listOf(StatInfo.BARS, StatInfo.WINS, StatInfo.HOLDING, StatInfo.EVOLUTION)) {
            assertTrue(i.title.isNotBlank() && i.text.isNotBlank())
            assertTrue("${i.title} is in the list the tests check", i in StatInfo.all())
        }
        assertTrue(StatInfo.HOLDING.text.contains("one item") && StatInfo.HOLDING.text.contains("Exp. Share") && StatInfo.HOLDING.text.contains("half"))
        assertTrue(StatInfo.WINS.text.contains("knocked out or caught"))
        assertTrue(StatInfo.BARS.text.contains("HP") && StatInfo.BARS.text.contains("XP"))
        assertTrue(StatInfo.EVOLUTION.text.contains("Pokédex"))
        assertNotNull(StatInfo.of(com.husarp.ankigo.game.Stat.HP))
    }
}
