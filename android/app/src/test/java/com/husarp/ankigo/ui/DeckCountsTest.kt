package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckCountsTest {
    @Test fun upToFiveDigitsAreShownWhole() {
        for ((n, s) in listOf(0 to "0", 7 to "7", 1000 to "1000", 9999 to "9999", 12345 to "12345", 99999 to "99999")) assertEquals(s, DeckCounts.format(n))
    }
    @Test fun biggerNumbersAreCompact() {
        assertEquals("100k", DeckCounts.format(100_000)); assertEquals("123k", DeckCounts.format(123_456))
        assertEquals("999k", DeckCounts.format(999_999)); assertEquals("1M", DeckCounts.format(1_000_000)); assertEquals("1.2M", DeckCounts.format(1_234_567))
    }
    @Test fun negativeShowsZero() = assertEquals("0", DeckCounts.format(-3))
}
