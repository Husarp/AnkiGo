package com.husarp.ankigo.ui

import com.husarp.ankigo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R19-H (PLAN 257, pick A from the left): the pure parts of the arena toast: the kind of each old whole-bar line, how a line is cut,
 * the queue / merge rules, the card's size per host, and where it sits.
 */
class R19HToastTest {
    // ---------------------------------------------------------------- kind -> icon

    @Test fun everyOldWholeBarLineHasItsOwnKind() {
        val lines = listOf(
            Triple(LogKind.SYS, "FAST", "Played fast: 12 answers, 2 won, 1 level-up") to ToastKind.FAST,
            Triple(LogKind.NEUTRAL, "OK", "New card: OK. No attacks this time.") to ToastKind.NEW_CARD_OK,
            Triple(LogKind.NEUTRAL, "UNDO", "Redo after undo — not counted") to ToastKind.UNDO,
            Triple(LogKind.NEUTRAL, "UNDO", "Undo in AnkiDroid: the redo won't count twice") to ToastKind.UNDO,
            Triple(LogKind.SYS, "CAUGHT UP", "Caught up 5 answers made while AnkiGo was closed: 1 won, 0 caught, +25 coins") to ToastKind.CAUGHT_UP,
            Triple(LogKind.SYS, "GOAL", "Daily gift: Great Ball (4-day streak)") to ToastKind.GIFT,
            Triple(LogKind.SYS, "ITEM", "Found a Super Potion!") to ToastKind.ITEM,
            Triple(LogKind.SYS, "BADGE", "Badge: Night Owl") to ToastKind.BADGE,
            Triple(LogKind.SYS, "TRAINER", "Trainer level 12!") to ToastKind.TRAINER,
            Triple(LogKind.SYS, "NEW DAY", "A new day begins: the daily goal starts again.") to ToastKind.NEW_DAY,
            Triple(LogKind.SYS, "GOAL", "Daily goal reached: 100 reviews!") to ToastKind.GOAL,
            Triple(LogKind.SYS, "INFO", "Your team is full: Puddlet went to the Box.") to ToastKind.NEWS,
            Triple(LogKind.SYS, "SWITCH", "Switching to Puddlet on your next move.") to ToastKind.SWITCH,
        )
        for ((line, kind) in lines) assertEquals(line.second, kind, ToastKind.of(line.first, line.second, line.third))
    }

    @Test fun anUnknownPillFallsBackByTheLogKind() {
        assertEquals(ToastKind.NOTE, ToastKind.of(LogKind.NEUTRAL, "WHATEVER"))
        assertEquals(ToastKind.NEWS, ToastKind.of(LogKind.SYS, "WHATEVER"))
        assertEquals(ToastKind.NEWS, ToastKind.of(LogKind.GOOD, "Good"))
        assertEquals(ToastKind.BADGE, ToastKind.of(LogKind.SYS, "ACHIEVE"))
        assertEquals("a pill is matched without its case", ToastKind.FAST, ToastKind.of(LogKind.SYS, "fast"))
    }

    @Test fun everyKindHasAnIconAndTheNeutralOnesAreTheQuietNotes() {
        for (k in ToastKind.entries) assertNotEquals(k.name, 0, k.icon)
        assertEquals(setOf(ToastKind.NOTE, ToastKind.NEW_CARD_OK, ToastKind.UNDO), ToastKind.entries.filter { it.neutral }.toSet())
        assertEquals(R.drawable.ic_swap, ToastKind.SWITCH.icon)
        assertEquals(R.drawable.ic_gift, ToastKind.ITEM.icon)
        assertEquals(R.drawable.ic_clock, ToastKind.CAUGHT_UP.icon)
    }

    // ---------------------------------------------------------------- the text

    @Test fun onlyTheCaughtUpLineHasASecondLine() {
        val (a, b) = ToastText.split(ToastKind.CAUGHT_UP, "Caught up 5 answers made while AnkiGo was closed: 1 won, 0 caught, +25 coins")
        assertEquals("Caught up 5 answers made while AnkiGo was closed", a)
        assertEquals("1 won, 0 caught, +25 coins", b)
        assertEquals("no colon, no second line", "Caught up 1 answer." to null, ToastText.split(ToastKind.CAUGHT_UP, "Caught up 1 answer."))
        assertEquals("Your team is full: Puddlet went to the Box." to null, ToastText.split(ToastKind.NEWS, "Your team is full: Puddlet went to the Box."))
    }

    @Test fun plainTextLosesTheMarks() {
        assertEquals("Found a Super Potion!", ToastText.plain("Found {{a Super Potion}}!"))
    }

    // ---------------------------------------------------------------- queue / merge

    private fun item(kind: ToastKind, text: String, pop: Boolean = true) = ToastQueue.Item(kind, text, null, pop)

    @Test fun theFirstShowsAtOnceAndTheNextWaitsOneAfterTheOther() {
        val q = ToastQueue()
        val a = item(ToastKind.ITEM, "Found a Potion!"); val b = item(ToastKind.TRAINER, "Trainer level 3!")
        assertEquals(ToastQueue.Offer.SHOW, q.offer(a, 0))
        assertEquals(a, q.showing)
        assertEquals(ToastQueue.Offer.QUEUED, q.offer(b, 100))
        assertEquals(1, q.pendingCount)
        assertEquals("never two at once: a still shows", a, q.showing)
        assertEquals(b, q.finish(2600))
        assertEquals(b, q.showing)
        assertNull(q.finish(5100))
        assertNull(q.showing)
    }

    @Test fun anEqualOneMergesWhileShowingWhileWaitingAndForAFewSecondsAfter() {
        val q = ToastQueue()
        val a = item(ToastKind.ITEM, "Found a Potion!"); val b = item(ToastKind.TRAINER, "Trainer level 3!")
        q.offer(a, 0)
        assertEquals("equal to the one showing", ToastQueue.Offer.MERGED, q.offer(a, 50))
        q.offer(b, 60)
        assertEquals("equal to a waiting one", ToastQueue.Offer.MERGED, q.offer(b, 70))
        assertEquals(1, q.pendingCount)
        q.finish(2600); q.finish(2700)   // both over: nothing shows
        assertEquals("a second after it went (it started 2.7 s ago): still the same news", ToastQueue.Offer.MERGED, q.offer(a, 2800))
        assertEquals("after the few seconds it may show again", ToastQueue.Offer.SHOW, q.offer(a, ToastQueue.DUP_MS + 1))
    }

    @Test fun theSameTextOfAnotherKindIsNotMerged() {
        val q = ToastQueue()
        q.offer(item(ToastKind.ITEM, "x"), 0)
        assertEquals(ToastQueue.Offer.QUEUED, q.offer(item(ToastKind.NEWS, "x"), 10))
    }

    @Test fun aNewerOneOfTheSameKindReplacesTheWaitingOne() {
        val q = ToastQueue()
        q.offer(item(ToastKind.ITEM, "Found a Potion!"), 0)
        q.offer(item(ToastKind.FAST, "Played fast: 3 answers"), 10)
        assertEquals(ToastQueue.Offer.REPLACED, q.offer(item(ToastKind.FAST, "Played fast: 9 answers"), 20))
        assertEquals(listOf("Played fast: 9 answers"), q.pending().map { it.text })
    }

    @Test fun atMostThreeWaitAndTheOldestGoes() {
        val q = ToastQueue()
        q.offer(item(ToastKind.ITEM, "a"), 0)
        q.offer(item(ToastKind.FAST, "b"), 1); q.offer(item(ToastKind.TRAINER, "c"), 2); q.offer(item(ToastKind.GOAL, "d"), 3)
        assertEquals(3, q.pendingCount)
        q.offer(item(ToastKind.BADGE, "e"), 4)
        assertEquals(3, q.pendingCount)
        assertEquals(listOf("c", "d", "e"), q.pending().map { it.text })
    }

    @Test fun clearForgetsEverything() {
        val q = ToastQueue()
        q.offer(item(ToastKind.ITEM, "a"), 0); q.offer(item(ToastKind.FAST, "b"), 1)
        q.clear()
        assertNull(q.showing); assertEquals(0, q.pendingCount)
        assertEquals("nothing is remembered as shown", ToastQueue.Offer.SHOW, q.offer(item(ToastKind.ITEM, "a"), 2))
    }

    @Test fun aToastStaysAboutTwoAndAHalfSecondsAndShorterWhileOthersWait() {
        val short = item(ToastKind.ITEM, "Found a Super Potion!")
        assertEquals(2500L, ToastQueue.holdMs(short, busy = false))
        assertEquals("shorter, but long enough to read", 1666L, ToastQueue.holdMs(short, busy = true))
        val long = ToastQueue.Item(ToastKind.CAUGHT_UP, "x".repeat(300), "y".repeat(100))
        assertEquals("a long one is capped", 4500L, ToastQueue.holdMs(long, busy = false))
        assertTrue(ToastQueue.holdMs(long, busy = true) <= 3000L)
        val q = ToastQueue()
        q.offer(short, 0)
        assertEquals(2500L, q.holdMs(short))
        q.offer(item(ToastKind.FAST, "a"), 1)
        assertEquals(1666L, q.holdMs(short))
    }

    // ---------------------------------------------------------------- the card's size per host

    @Test fun theLookFollowsTheArenaSizeOnScreen() {
        assertEquals("Study panel / Battle tab", ToastLook.Tier.FULL, ToastLook.of(412f, 260f).tier)
        assertEquals(ToastLook.Tier.FULL, ToastLook.of(412f, 420f).tier)
        assertEquals("a short arena (the Study strip, a floating window)", ToastLook.Tier.COMPACT, ToastLook.of(412f, 150f).tier)
        assertEquals(ToastLook.Tier.COMPACT, ToastLook.of(240f, 120f).tier)
        assertEquals("picture-in-picture", ToastLook.Tier.TINY, ToastLook.of(240f, 105f).tier)
        assertEquals(ToastLook.Tier.TINY, ToastLook.of(170f, 130f).tier)
        assertEquals("no arena at all (the Study mini bar)", ToastLook.Tier.TINY, ToastLook.of(412f, 400f, arenaShown = false).tier)
    }

    @Test fun smallerHostsGetSmallerCardsWithoutTheSecondLineInTheSmallest() {
        val f = ToastLook.of(412f, 260f); val c = ToastLook.of(412f, 150f); val t = ToastLook.of(170f, 100f)
        assertTrue(f.textSp > c.textSp && c.textSp > t.textSp)
        assertTrue(f.iconDp > c.iconDp && c.iconDp > t.iconDp)
        assertEquals(2, f.mainLines); assertEquals(3, c.mainLines); assertEquals("the smallest is one line", 1, t.mainLines)
        assertTrue(f.showSub && c.showSub); assertFalse(t.showSub)
        assertTrue("a tiny arena gives the card more of its width", t.maxWidthShare > f.maxWidthShare)
    }

    // ---------------------------------------------------------------- where it sits

    private fun r(l: Int, t: Int, rr: Int, b: Int) = FloatSpot.Rect(l, t, rr, b)
    private val far = r(-100, -100, -90, -90)
    private fun keep(ourCard: FloatSpot.Rect = far, wildCard: FloatSpot.Rect = far, ourArt: FloatSpot.Rect = far, wildArt: FloatSpot.Rect = far, safe: FrameSafe? = null) =
        KeepOff(ourCard, wildCard, ourArt, wildArt, safe)

    @Test fun firstChoiceIsTheTopLeftCornerAMarginIn() {
        val s = ToastSpot.place(150, 30, 412, 260, 10, 4, keep(ourCard = r(100, 200, 260, 255), wildArt = r(280, 20, 380, 120)))
        assertEquals(10, s.left); assertEquals(10, s.top); assertFalse(s.coversOurCard)
        val n = ToastSpot.place(150, 30, 412, 260, 10, 4, null)
        assertEquals(10, n.left); assertEquals(10, n.top)
    }

    @Test fun withABattleFrameItSitsInsideTheSafeArea() {
        val safe = FrameSafe(8f, 14f, 30f)   // band 8, icicles to 14, corner squares 30
        val s = ToastSpot.place(150, 30, 412, 260, 6, 4, keep(safe = safe))
        assertTrue("inside the band", s.left >= 8 && s.top >= 14)
        assertTrue("clear of the corner square", safe.isClear(s.left.toFloat(), s.top.toFloat(), 150f, 30f, 412f, 260f))
    }

    @Test fun anInfoBoxAtTheTopLeftMakesItMoveToTheNearestFreePlace() {
        // the Study strip: the wild card sits top-left
        val wild = r(4, 4, 150, 60)
        val s = ToastSpot.place(130, 26, 412, 120, 6, 4, keep(wildCard = wild, ourCard = r(250, 70, 408, 118)))
        assertFalse("it does not sit on the wild card", FloatSpot.Rect(s.left, s.top, s.left + 130, s.top + 26).hits(wild))
        assertEquals(0.0, s.covered, 0.0)
    }

    @Test fun aLittleOverAPicturesTopBeatsAFarAwaySpot() {
        // the picture's box (its inner part) reaches 15 px into the corner place; every other place covers an info box: the corner stays
        val art = r(30, 10, 330, 260)
        val s = ToastSpot.place(300, 40, 412, 400, 10, 4, keep(ourArt = art, ourCard = r(10, 270, 340, 390), wildCard = r(250, 150, 410, 250)))
        assertEquals(10, s.left); assertEquals(10, s.top)
    }

    @Test fun aBigCoverOfAPictureMovesTheCard() {
        // most of the card would sit on the picture: it goes where nothing is covered
        val art = r(0, 0, 300, 120)
        val s = ToastSpot.place(200, 40, 412, 300, 10, 4, keep(ourArt = art))
        assertEquals("nothing covered", 0.0, s.covered, 0.0)
    }

    @Test fun yourInfoBoxCountsMostWhenNothingIsFree() {
        // an arena where the toast cannot avoid both: it covers the wild box rather than ours
        val ours = r(0, 0, 300, 60); val wild = r(0, 60, 300, 120)
        val s = ToastSpot.place(140, 40, 300, 120, 4, 4, keep(ourCard = ours, wildCard = wild))
        assertFalse("your info box stays visible", s.coversOurCard)
    }

    @Test fun theCardStaysInsideTheArena() {
        val s = ToastSpot.place(100, 30, 120, 60, 10, 4, keep(ourCard = r(0, 0, 120, 60)))
        assertTrue(s.left >= 0 && s.left + 100 <= 120)
        assertTrue(s.top >= 0 && s.top + 30 <= 60)
    }

    @Test fun aCardKeepsItsMarginFromTheRightAndBottomEdge() {
        // the Study strip: the wild card at the top-left, so the card goes along the top to the right of it, as far as the margin allows
        val s = ToastSpot.place(300, 30, 412, 120, 6, 4, keep(wildCard = r(4, 4, 150, 60)))
        assertTrue("room to the right of the wild card is 412 - 154 - 6 = 252 < 300: not there", s.left + 300 + 6 <= 412)
        assertTrue(s.top + 30 + 6 <= 120)
    }

    @Test fun anExtraObstacleLikeThePausedBadgeIsAvoided() {
        val badge = r(6, 6, 60, 22)
        val k = KeepOff(far, far, far, far, null, listOf(badge))
        val s = ToastSpot.place(100, 20, 240, 100, 3, 2, k)
        assertFalse(FloatSpot.Rect(s.left, s.top, s.left + 100, s.top + 20).hits(badge))
    }
}
