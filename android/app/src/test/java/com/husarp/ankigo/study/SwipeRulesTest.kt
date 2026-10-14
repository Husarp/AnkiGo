package com.husarp.ankigo.study

import com.husarp.ankigo.study.SwipeRules.Dir
import org.junit.Assert.*
import org.junit.Test

class SwipeRulesTest {
    private val t = 160f // 80 dp at 2x

    @Test fun fourButtonsMapLikeAnkiDroid() {
        assertEquals(1, SwipeRules.ease(Dir.LEFT, 4)); assertEquals(2, SwipeRules.ease(Dir.DOWN, 4))
        assertEquals(3, SwipeRules.ease(Dir.RIGHT, 4)); assertEquals(4, SwipeRules.ease(Dir.UP, 4))
    }
    @Test fun threeButtonsHaveNoHardDown() {
        assertEquals(1, SwipeRules.ease(Dir.LEFT, 3)); assertEquals(2, SwipeRules.ease(Dir.RIGHT, 3)); assertEquals(3, SwipeRules.ease(Dir.UP, 3))
        assertNull(SwipeRules.ease(Dir.DOWN, 3))
    }
    @Test fun twoButtonsOnlyLeftRight() {
        assertEquals(1, SwipeRules.ease(Dir.LEFT, 2)); assertEquals(2, SwipeRules.ease(Dir.RIGHT, 2))
        assertNull(SwipeRules.ease(Dir.UP, 2)); assertNull(SwipeRules.ease(Dir.DOWN, 2))
    }
    @Test fun labelsAndHints() {
        assertEquals("← Again · ↓ Hard · → Good · ↑ Easy", SwipeRules.hint(4))
        assertEquals("← Again · → Good · ↑ Easy", SwipeRules.hint(3))
        assertEquals("← Again · → Good", SwipeRules.hint(2))
        assertEquals("Swipe left for Again, right for Good", SwipeRules.description(2))
        assertEquals("← Again", SwipeRules.pill(Dir.LEFT)); assertEquals("Good →", SwipeRules.pill(Dir.RIGHT))
        assertEquals("↑ Easy", SwipeRules.pill(Dir.UP)); assertEquals("Hard ↓", SwipeRules.pill(Dir.DOWN))
    }
    @Test fun commitsAtThresholdOnADominantAxis() {
        assertEquals(Dir.RIGHT, SwipeRules.commit(160f, 10f, t, 4))
        assertEquals(Dir.LEFT, SwipeRules.commit(-200f, 40f, t, 4))
        assertEquals(Dir.UP, SwipeRules.commit(30f, -170f, t, 4))
        assertEquals(Dir.DOWN, SwipeRules.commit(-20f, 300f, t, 4))
    }
    @Test fun shortDragCommitsNothing() {
        assertNull(SwipeRules.commit(159f, 0f, t, 4)); assertNull(SwipeRules.commit(0f, 0f, t, 4)); assertNull(SwipeRules.commit(0f, -100f, t, 4))
    }
    @Test fun diagonalDragIsNotDominant() {
        assertNull(SwipeRules.commit(200f, 120f, t, 4))   // 200 < 2 * 120
        assertEquals(Dir.RIGHT, SwipeRules.commit(240f, 120f, t, 4)) // exactly 2x
        assertFalse(SwipeRules.dominant(100f, -60f)); assertTrue(SwipeRules.dominant(100f, 50f))
    }
    @Test fun noButtonNoCommit() {
        assertNull(SwipeRules.commit(0f, 300f, t, 3)); assertNull(SwipeRules.commit(0f, -300f, t, 2))
    }
    @Test fun progressGrowsAndIsFullAtTheThreshold() {
        assertEquals(.5f, SwipeRules.progress(Dir.RIGHT, 80f, 0f, t), 1e-6f)
        assertEquals(1f, SwipeRules.progress(Dir.RIGHT, 160f, 0f, t), 0f)
        assertEquals(1f, SwipeRules.progress(Dir.UP, 0f, -400f, t), 0f)
        assertEquals(0f, SwipeRules.progress(Dir.LEFT, 80f, 0f, t), 0f)   // pointing the other way
        assertEquals(0f, SwipeRules.progress(Dir.RIGHT, 100f, 80f, t), 0f) // diagonal
    }
    @Test fun scrollEdgeRule() {
        // finger up = content scrolls down (+1): blocked while the card can still scroll down
        assertFalse(SwipeRules.claims(0f, -40f, 4) { h, d -> !h && d == 1 })
        assertTrue(SwipeRules.claims(0f, -40f, 4) { h, d -> !h && d == -1 })
        // finger down = content scrolls up (-1)
        assertFalse(SwipeRules.claims(0f, 40f, 4) { h, d -> !h && d == -1 })
        assertTrue(SwipeRules.claims(0f, 40f, 4) { _, _ -> false })
        // horizontal: finger left = +1, finger right = -1
        assertFalse(SwipeRules.claims(-40f, 0f, 4) { h, d -> h && d == 1 })
        assertFalse(SwipeRules.claims(40f, 0f, 4) { h, d -> h && d == -1 })
        assertTrue(SwipeRules.claims(40f, 0f, 4) { h, d -> h && d == 1 })
    }
    @Test fun claimsNeedAButtonForTheDirection() {
        assertFalse(SwipeRules.claims(0f, 40f, 3) { _, _ -> false })
        assertFalse(SwipeRules.claims(0f, -40f, 2) { _, _ -> false })
        assertFalse(SwipeRules.claims(0f, 0f, 4) { _, _ -> false })
        assertTrue(SwipeRules.claims(-40f, 3f, 2) { _, _ -> false })
    }
    @Test fun secondFingerCancels() { assertFalse(SwipeRules.cancelledBy(1)); assertTrue(SwipeRules.cancelledBy(2)) }
}
