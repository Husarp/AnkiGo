package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R10-G: the Rows sheet texts (B7) and the tap-to-sort cycle (B12). */
class CompareRowTextTest {
    @Test fun everyRowKeyTheTableMakesHasAText() {
        val data = TestData.world()
        val st = GameState(createdAt = 1L, starterChosen = true)
        st.mons += TestData.mon(data, 25, 20, listOf("tackle"), "a")
        val table = CompareTable.build(data, st, listOf(ComparePick.Owned("a"), ComparePick.Dex(3)), false)
        for (row in table.rows) {
            assertTrue("${row.key} is in KEYS", row.key in CompareRowText.KEYS)
            val (full, sentence) = CompareRowText.of(row.key, row.label)
            assertTrue("${row.key} has a full name", full.isNotBlank())
            assertTrue("${row.key} has a sentence", sentence.isNotBlank())
        }
        // and the list has nothing the table does not make
        assertEquals(table.rows.map { it.key }.toSet(), CompareRowText.KEYS.toSet())
    }

    @Test fun sentencesAreOneShortSentence() {
        for (key in CompareRowText.KEYS) {
            val (full, s) = CompareRowText.of(key, "x")
            assertTrue("$key: ends with one period: $s", s.endsWith(".") && s.count { it == '.' } == 1)
            assertTrue("$key: short (${s.length})", s.length <= 70)
            assertTrue("$key: starts with a capital", s.first().isUpperCase())
            assertTrue("$key: the full name is no sentence", !full.endsWith("."))
        }
    }

    @Test fun speedExampleAndUnknownKey() {
        assertEquals("Speed" to "Who attacks first; higher is faster.", CompareRowText.of("spe", "Spe"))
        assertEquals("Mystery" to "", CompareRowText.of("nope", "Mystery"))
    }

    @Test fun tapCycleDescThenAscThenClear() {
        var cur: Pair<String?, Boolean> = null to true
        cur = CompareSortCycle.next(cur.first, cur.second, "atk"); assertEquals("atk" to true, cur)
        cur = CompareSortCycle.next(cur.first, cur.second, "atk"); assertEquals("atk" to false, cur)
        cur = CompareSortCycle.next(cur.first, cur.second, "atk"); assertEquals(null to true, cur)
        cur = CompareSortCycle.next(cur.first, cur.second, "atk"); assertEquals("atk" to true, cur) // round again
    }

    @Test fun anotherStatStartsHighToLowWhateverTheDirection() {
        assertEquals("def" to true, CompareSortCycle.next("atk", true, "def"))
        assertEquals("def" to true, CompareSortCycle.next("atk", false, "def"))
        assertEquals("spe" to true, CompareSortCycle.next(null, false, "spe"))
        assertNull(CompareSortCycle.next("atk", false, "atk").first)
    }

    @Test fun tapSaysWhatComesNext() {
        assertEquals("Tap to sort high to low.", CompareSortCycle.tapSays(null, true, "atk"))
        assertEquals("Tap to sort high to low.", CompareSortCycle.tapSays("def", false, "atk"))
        assertEquals("Tap to sort low to high.", CompareSortCycle.tapSays("atk", true, "atk"))
        assertEquals("Tap to clear the sort.", CompareSortCycle.tapSays("atk", false, "atk"))
    }
}
