package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvoLineTest {
    private fun sp(num: Int, name: String, prevo: Int? = null, vararg evo: Evolution) =
        TestData.species(num, name, prevo = prevo, evolutions = evo.toList())

    private fun names(l: List<EvoNode>) = l.map { it.species.name }

    // Sparklet -> Sparkmouse (Lv 16) -> Sparkrat (stone) | Sparkcat (friendship, night)
    private val branched = TestData.data(listOf(
        sp(1001, "Sparklet", null, Evolution(1002, EvoKind.LEVEL, level = 16)),
        sp(1002, "Sparkmouse", 1001, Evolution(1003, EvoKind.ITEM, item = "thunder-stone"), Evolution(1004, EvoKind.FRIENDSHIP, minFriendship = 160, time = "night")),
        sp(1003, "Sparkrat", 1002),
        sp(1004, "Sparkcat", 1002),
    ))

    @Test fun theWholeLineIsTheSameFromAnyMember() {
        for (num in 1001..1004) {
            val line = EvoLine.of(branched, branched.sp(num))
            assertEquals("Sparklet", line.root.species.name)
            assertEquals(branched.sp(num), line.current)
            assertEquals(listOf("Sparklet", "Sparkmouse", "Sparkrat", "Sparkcat"), names(line.nodes))
        }
    }

    @Test fun stagesBranchesAndFinals() {
        val line = EvoLine.of(branched, branched.sp(1002))
        assertEquals(listOf(listOf("Sparklet"), listOf("Sparkmouse"), listOf("Sparkrat", "Sparkcat")), line.stages.map(::names))
        assertTrue(line.branched)
        assertFalse(line.standalone)
        assertEquals(listOf("Sparkrat", "Sparkcat"), names(line.finals))
        assertEquals(listOf("Sparklet", "Sparkmouse"), names(line.pathToCurrent))
        assertEquals(listOf("Sparklet", "Sparkmouse", "Sparkcat"), names(EvoLine.of(branched, branched.sp(1004)).pathToCurrent))
    }

    @Test fun everyStepKeepsItsMethod() {
        val line = EvoLine.of(branched, branched.sp(1001))
        assertEquals(null, line.root.via)
        val mouse = line.root.children.single()
        assertEquals("Lv 16", EvoLine.method(branched, mouse.via!!))
        assertEquals(listOf("Thunder Stone", "Friendship, night"), mouse.children.map { EvoLine.method(branched, it.via!!) })
    }

    @Test fun aSpeciesWithoutALineIsStandalone() {
        val d = TestData.data(listOf(sp(1010, "Loner")))
        val line = EvoLine.of(d, d.sp(1010))
        assertTrue(line.standalone)
        assertFalse(line.branched)
        assertEquals(listOf("Loner"), names(line.nodes))
        assertEquals(listOf("Loner"), names(line.finals))
    }

    // Regional line: Plainling -> Plainrat (Kanto-like) and Plainling-Rocky (num 12001) -> Rockyrat (12002, prevo = the form)
    private val regional = TestData.data(listOf(
        sp(2001, "Plainling", null, Evolution(2002, EvoKind.LEVEL, level = 20)),
        sp(2002, "Plainrat", 2001),
        sp(12001, "Rocky Plainling", null, Evolution(12002, EvoKind.LEVEL, level = 28)),
        sp(12002, "Rockyrat", 12001),
    ))

    @Test fun aRegionalLineStaysRegional() {
        val rocky = EvoLine.of(regional, regional.sp(12002))
        assertEquals(listOf("Rocky Plainling", "Rockyrat"), names(rocky.nodes))
        assertEquals(listOf("Plainling", "Plainrat"), names(EvoLine.of(regional, regional.sp(2001)).nodes))
        assertEquals(listOf("Rocky Plainling", "Rockyrat"), names(EvoLine.of(regional, regional.sp(12001)).nodes))
    }

    @Test fun aBrokenCycleDoesNotHang() {
        val d = TestData.data(listOf(
            sp(3001, "Ping", 3002, Evolution(3002, EvoKind.LEVEL, level = 10)),
            sp(3002, "Pong", 3001, Evolution(3001, EvoKind.LEVEL, level = 10)),
        ))
        val line = EvoLine.of(d, d.sp(3001))
        assertTrue(line.nodes.size <= 3)
    }

    @Test fun evolutionsMissingFromTheDataAreLeftOut() {
        val d = TestData.data(listOf(sp(4001, "Halfline", null, Evolution(4999, EvoKind.LEVEL, level = 5))))
        assertTrue(EvoLine.of(d, d.sp(4001)).standalone)
    }

    @Test fun methodTexts() {
        val d = TestData.data(emptyList())
        assertEquals("Level up", EvoLine.method(d, Evolution(1, EvoKind.LEVEL)))
        assertEquals("Lv 30, day", EvoLine.method(d, Evolution(1, EvoKind.LEVEL, level = 30, time = "day")))
        assertEquals("Linking Cord", EvoLine.method(d, Evolution(1, EvoKind.TRADE)))
        assertEquals("knows Mimic", EvoLine.method(d, Evolution(1, EvoKind.LEVEL_MOVE, move = "mimic")))
    }

    @Test fun unseenFormsShowQuestionMarks() {
        val seen = setOf(1002)
        assertEquals("???", EvoLine.shownName(branched.sp(1001), seen))
        assertEquals("Sparkmouse", EvoLine.shownName(branched.sp(1002), seen))
        assertTrue(EvoLine.isSeen(branched.sp(1002), seen))
        assertFalse(EvoLine.isSeen(branched.sp(1003), seen))
        assertEquals("Sparkrat", EvoLine.shownName(branched.sp(1003), setOf(1003)))
    }
}
