package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamXpCardsTest {
    private fun xp(uid: String, xp: Int, level: Int, inLevel: Int, forLevel: Int) = GameEvent.XpGained(uid, uid.uppercase(), xp, level, inLevel, forLevel)
    private fun up(uid: String, level: Int) = GameEvent.LevelUp(uid, uid.uppercase(), level, 10, 20)

    @Test fun mainIsNotACard() {
        assertEquals(emptyList<XpCard>(), TeamXpCards.of(listOf(xp("main", 30, 10, 40, 100)), "main"))
    }

    @Test fun holderGetsOneCardWithTheBarBeforeAndAfter() {
        val cards = TeamXpCards.of(listOf(xp("main", 60, 10, 10, 100), xp("a", 30, 9, 50, 100)), "main")
        assertEquals(1, cards.size)
        val c = cards.single()
        assertEquals("a", c.uid); assertEquals("A", c.name); assertEquals(30, c.xp)
        assertEquals(9, c.levelFrom); assertEquals(9, c.levelTo); assertFalse(c.levelUp); assertNull(c.evolvedTo)
        assertEquals(0.2f, c.barFrom, 0.0001f); assertEquals(0.5f, c.barTo, 0.0001f)
    }

    @Test fun twoMembersInEventOrder() {
        val cards = TeamXpCards.of(listOf(xp("main", 60, 10, 10, 100), xp("b", 20, 5, 20, 50), xp("a", 10, 7, 30, 60)), "main")
        assertEquals(listOf("b", "a"), cards.map { it.uid })
        assertEquals(0f, cards[0].barFrom, 0.0001f); assertEquals(0.4f, cards[0].barTo, 0.0001f)
    }

    @Test fun levelUpStartsTheBarAtTheOldFractionOfTheOldLevel() {
        // PLAN 182: 80 of 90 XP before the gain, two levels, 25 of 100 now
        val ev = listOf(GameEvent.XpGained("a", "A", 500, 12, 25, 100, 80, listOf(90, 95)), up("a", 11), up("a", 12))
        val c = TeamXpCards.of(ev, "main").single()
        assertTrue(c.levelUp); assertEquals(10, c.levelFrom); assertEquals(12, c.levelTo)
        assertEquals(80f / 90f, c.barFrom, 0.0001f); assertEquals(0.25f, c.barTo, 0.0001f)
    }

    @Test fun aPlainGainUsesTheRecordedStartToo() {
        val c = TeamXpCards.of(listOf(GameEvent.XpGained("a", "A", 30, 9, 50, 100, 20)), "main").single()
        assertFalse(c.levelUp); assertEquals(0.2f, c.barFrom, 0.0001f); assertEquals(0.5f, c.barTo, 0.0001f)
    }

    @Test fun aLevelUpWithTheStartRecordedAtZeroStartsEmpty() {
        val ev = listOf(GameEvent.XpGained("a", "A", 100, 10, 0, 100, 0, listOf(100)), up("a", 10))
        assertEquals(0f, TeamXpCards.of(ev, "main").single().barFrom, 0f)
    }

    @Test fun oldStyleLevelUpWithoutARecordedStartStartsTheBarAtZero() {
        val ev = listOf(xp("a", 500, 12, 25, 100), up("a", 11), up("a", 12), up("main", 30))
        val c = TeamXpCards.of(ev, "main").single()
        assertTrue(c.levelUp); assertEquals(10, c.levelFrom); assertEquals(12, c.levelTo)
        assertEquals(0f, c.barFrom, 0.0001f); assertEquals(0.25f, c.barTo, 0.0001f)
    }

    @Test fun levelUpOfAnotherMemberDoesNotLeakIntoTheCard() {
        val ev = listOf(xp("a", 30, 9, 50, 100), xp("b", 500, 12, 25, 100), up("b", 12))
        val cards = TeamXpCards.of(ev, "main")
        assertFalse(cards[0].levelUp); assertTrue(cards[1].levelUp); assertEquals(11, cards[1].levelFrom)
    }

    @Test fun evolutionIsOnTheCard() {
        val ev = listOf(xp("a", 500, 16, 5, 100), up("a", 16), GameEvent.Evolved("a", "A", "Bigger", 99))
        assertEquals("Bigger", TeamXpCards.of(ev, "main").single().evolvedTo)
    }

    @Test fun maxLevelFillsTheBar() {
        val c = TeamXpCards.of(listOf(xp("a", 40, 100, 0, 0), up("a", 100)), "main").single()
        assertTrue(c.levelUp); assertEquals(99, c.levelFrom); assertEquals(100, c.levelTo)
        assertEquals(0f, c.barFrom, 0.0001f); assertEquals(1f, c.barTo, 0.0001f)
    }

    @Test fun zeroXpAndNullMain() {
        assertEquals(emptyList<XpCard>(), TeamXpCards.of(listOf(xp("a", 0, 9, 50, 100)), "main"))
        assertEquals(1, TeamXpCards.of(listOf(xp("a", 5, 9, 50, 100)), null).size)
        assertEquals(emptyList<XpCard>(), TeamXpCards.of(listOf(GameEvent.Info("x")), "main"))
    }

    @Test fun theSameMemberTwiceGetsTwoCards() {
        val cards = TeamXpCards.of(listOf(xp("a", 10, 9, 20, 100), xp("a", 10, 9, 30, 100)), "main")
        assertEquals(2, cards.size)
        assertEquals(0.1f, cards[0].barFrom, 0.0001f); assertEquals(0.2f, cards[1].barFrom, 0.0001f)
    }
}
