package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSidesTest {
    private fun attack(byPlayer: Boolean) = GameEvent.Attack(byPlayer, "A", "B", "Zap", null, Category.PHYSICAL, 5, 1, 1.0, false, false, false, 1, 10, 10, 10)
    private fun encounter() = GameEvent.Encounter(1, "Wildling", 5, false, Tier.NORMAL, 10, 10, false, false)

    @Test fun anEmptyBarHasNothingToFlipTo() {
        val s = LogSlots<String>()
        assertNull(s.current()); assertFalse(s.canFlip); assertEquals(LogArrow.NONE, s.arrow())
        assertNull(s.flip())
        assertEquals(LogSide.YOURS, s.shown)
    }

    @Test fun oneSideOnlyHasNoChevronAndTheFlipDoesNothing() {
        val s = LogSlots<String>()
        s.post(LogSide.YOURS, "mine")
        assertEquals(LogArrow.NONE, s.arrow())
        assertNull(s.flip())
        assertEquals("mine", s.current()); assertEquals(LogSide.YOURS, s.shown)
    }

    @Test fun yourLineShowsTheRightChevronAndTheWildLineTheLeftOne() {
        val s = LogSlots<String>()
        s.post(LogSide.WILD, "wild"); s.post(LogSide.YOURS, "mine")
        assertEquals("mine", s.current()); assertEquals(LogArrow.RIGHT, s.arrow())
        assertEquals("wild", s.flip()); assertEquals(LogArrow.LEFT, s.arrow())
        assertEquals("mine", s.flip()); assertEquals(LogArrow.RIGHT, s.arrow())
    }

    @Test fun aNewLineShowsAtOnceWhicheverSideItIsFromAndForgetsTheFlip() {
        val s = LogSlots<String>()
        s.post(LogSide.YOURS, "mine"); s.post(LogSide.WILD, "wild")
        assertEquals("wild", s.current())
        s.flip()
        assertEquals("mine", s.current())
        s.post(LogSide.WILD, "wild 2")
        assertEquals("wild 2", s.current()); assertEquals(LogSide.WILD, s.shown); assertEquals(LogArrow.LEFT, s.arrow())
        assertEquals("mine", s.flip())
    }

    @Test fun eachSideKeepsOnlyItsLatestLine() {
        val s = LogSlots<String>()
        s.post(LogSide.YOURS, "m1"); s.post(LogSide.WILD, "w1"); s.post(LogSide.YOURS, "m2")
        assertEquals("w1", s.flip())
        assertEquals("m2", s.flip())
    }

    @Test fun aFreshLineForgetsTheOtherSide() {
        val s = LogSlots<String>()
        s.post(LogSide.YOURS, "mine"); s.post(LogSide.WILD, "wild")
        s.post(LogSide.WILD, "A wild one appeared", fresh = true)
        assertEquals("A wild one appeared", s.current()); assertFalse(s.canFlip); assertEquals(LogArrow.NONE, s.arrow())
        s.post(LogSide.YOURS, "Go!")
        assertEquals(LogArrow.RIGHT, s.arrow()); assertEquals("A wild one appeared", s.flip())
    }

    @Test fun resetForgetsBothSides() {
        val s = LogSlots<String>()
        s.post(LogSide.YOURS, "a"); s.post(LogSide.WILD, "b")
        s.reset()
        assertNull(s.current()); assertEquals(LogArrow.NONE, s.arrow())
    }

    @Test fun attacksEffectsAndFaintsBelongToWhoDoesOrSuffersThem() {
        assertEquals(LogSide.YOURS, LogSides.of(attack(true)))
        assertEquals(LogSide.WILD, LogSides.of(attack(false)))
        assertEquals(LogSide.YOURS, LogSides.of(GameEvent.Effect(true, "x", 1, 2)))
        assertEquals(LogSide.WILD, LogSides.of(GameEvent.Effect(false, "x", 1, 2)))
        assertEquals(LogSide.YOURS, LogSides.of(GameEvent.Fainted(true, "A")))
        assertEquals(LogSide.WILD, LogSides.of(GameEvent.Fainted(false, "B")))
    }

    @Test fun lineAboutTheWildOneAreItsSide() {
        assertEquals(LogSide.WILD, LogSides.of(encounter()))
        assertEquals(LogSide.WILD, LogSides.of(GameEvent.Splash("B")))
        assertEquals(LogSide.WILD, LogSides.of(GameEvent.Defeated(1, "B", 10)))
        assertEquals(LogSide.WILD, LogSides.of(GameEvent.Fled("B")))
        assertEquals(LogSide.WILD, LogSides.of(GameEvent.Info("Wildling broke free!")))
    }

    @Test fun systemLinesCountAsYours() {
        assertEquals(LogSide.YOURS, LogSides.of(GameEvent.Info("Daily goal reached")))
        assertEquals(LogSide.YOURS, LogSides.of(GameEvent.Charge(1, 1, 2)))
        assertEquals(LogSide.YOURS, LogSides.of(GameEvent.LevelUp("u", "A", 5, 10, 10)))
        assertEquals(LogSide.YOURS, LogSides.of(GameEvent.ItemFound("potion", 1)))
        assertEquals(LogSide.YOURS, LogSides.of(GameEvent.Caught("u", 1, "B", 5, false, null, false, true)))
        assertEquals(LogSide.YOURS, LogSides.of(GameEvent.BallThrow("poke-ball", 50, true, 3)))
    }

    @Test fun onlyAnEncounterStartsANewFight() {
        assertTrue(LogSides.startsFight(encounter()))
        assertFalse(LogSides.startsFight(attack(false)))
        assertFalse(LogSides.startsFight(GameEvent.Defeated(1, "B", 10)))
    }
}
