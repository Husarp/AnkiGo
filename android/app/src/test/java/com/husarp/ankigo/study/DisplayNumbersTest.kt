package com.husarp.ankigo.study
import com.husarp.ankigo.ui.DisplayNumbers
import org.junit.Assert.*
import org.junit.Test
class DisplayNumbersTest {
    @Test fun numbersInsteadOfPercents() {
        assertEquals("58 / 71", DisplayNumbers.hp(58, 71))
        assertEquals("XP 640 / 1000", DisplayNumbers.xp(640, 1000))
        assertEquals("XP MAX", DisplayNumbers.xp(0, 0))
    }
    @Test fun sizesAndSpeed() {
        assertEquals("2.1 MB", DisplayNumbers.size(2_100_000))
        assertEquals("1.4 / 2.1 MB · 850 KB/s", DisplayNumbers.progress(1_445_000, 2_100_000, 1700))
        assertTrue(DisplayNumbers.progress(0, -1, 0).contains("Size unknown"))
    }
}
