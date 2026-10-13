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
}
