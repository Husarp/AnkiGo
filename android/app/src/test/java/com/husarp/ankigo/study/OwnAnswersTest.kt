package com.husarp.ankigo.study
import com.husarp.ankigo.live.OwnAnswers
import org.junit.Assert.*
import org.junit.Test
class OwnAnswersTest {
    @Test fun consumesOnceAndLeavesOtherReps() {
        val g = OwnAnswers(); g.expect(7, 4, 0); g.expect(7, 4, 0)
        assertEquals(1, g.consume(7, 3, 5, 10))
        assertEquals(0, g.consume(7, 3, 5, 20))
    }
    @Test fun expiredOrDifferentCardsAreNotSuppressed() {
        val g = OwnAnswers(); g.expect(7, 4, 0)
        assertEquals(0, g.consume(8, 3, 4, 10))
        assertEquals(0, g.consume(7, 3, 4, 30_001))
    }
    @Test fun expectingAgainKeepsTheExpectationAliveBeyondTheFirstThirtySeconds() {
        // a write that applied and is read again later (back-off) must still be recognised as ours by a poll at 40 s
        val g = OwnAnswers(); g.expect(7, 4, 0); g.expect(7, 4, 20_000)
        assertEquals(1, g.consume(7, 3, 4, 40_000))
        assertEquals("once", 0, g.consume(7, 3, 4, 40_001))
        val old = OwnAnswers(); old.expect(7, 4, 0)
        assertEquals("without the refresh it would have expired", 0, old.consume(7, 3, 4, 40_000))
    }
    @Test fun anExpectationForAnotherRepIsNotRefreshedByThisOne() {
        val g = OwnAnswers(); g.expect(7, 4, 0); g.expect(7, 5, 20_000)
        assertEquals("rep 4 expired at 30 s, rep 5 lives to 50 s", 1, g.consume(7, 3, 5, 40_000))
    }
    // ---- pinned while an answer is unresolved (R9 wave 6 FIX, Codex 3)

    @Test fun aPinnedExpectationSurvivesTenMinutes() {
        val g = OwnAnswers(); g.expect(7, 4, 0, pinned = true)
        assertEquals("a poll after the screen was left, long after the last attempt, still sees our own answer", 1, g.consume(7, 3, 4, 600_000))
        val plain = OwnAnswers(); plain.expect(7, 4, 0)
        assertEquals("a plain one is gone by then", 0, plain.consume(7, 3, 4, 600_000))
    }

    @Test fun aPollRemovesAPinnedExpectationLikeAnyOther() {
        val g = OwnAnswers(); g.expect(7, 4, 0, pinned = true)
        assertEquals(1, g.consume(7, 3, 4, 100))
        assertEquals("the game step of the saved answer is then the one count", 0, g.consume(7, 3, 4, 200))
    }

    @Test fun aLaterPlainExpectUnpinsItToThirtySeconds() {
        val g = OwnAnswers(); g.expect(7, 4, 0, pinned = true); g.expect(7, 4, 100_000) // the saved answer's handoff
        assertEquals(1, g.consume(7, 3, 4, 129_000))
        val h = OwnAnswers(); h.expect(7, 4, 0, pinned = true); h.expect(7, 4, 100_000)
        assertEquals("it expires thirty seconds after the plain one", 0, h.consume(7, 3, 4, 131_000))
    }

    @Test fun aPinAfterAPlainExpectPinsIt() {
        val g = OwnAnswers(); g.expect(7, 4, 0); g.expect(7, 4, 10_000, pinned = true)
        assertEquals(1, g.consume(7, 3, 4, 900_000))
    }

    @Test fun cancelRemovesAPinnedExpectation() {
        val g = OwnAnswers(); g.expect(7, 4, 0, pinned = true); g.cancel(7, 4)
        assertEquals("an answer that ended not written leaves nothing behind", 0, g.consume(7, 3, 4, 10))
    }

    @Test fun aPinnedExpectationOnlyShieldsItsOwnRep() {
        val g = OwnAnswers(); g.expect(7, 4, 0, pinned = true)
        assertEquals("a poll that saw rep 5 (an answer made elsewhere) is not suppressed by rep 4's pin", 0, g.consume(7, 4, 5, 10))
        assertEquals(1, g.consume(7, 3, 4, 20))
    }
}
