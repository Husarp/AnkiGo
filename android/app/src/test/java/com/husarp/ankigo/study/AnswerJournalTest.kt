package com.husarp.ankigo.study

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnswerJournalTest {
    private val full = mapOf(
        "_id" to "7", "note_id" to "70", "deck_id" to "3", "reps" to "3", "due" to "12", "queue" to "2",
        "last_review_time_secs" to null, "lapses" to "1", "sm2_factor" to "2500",
    )

    private fun a(seq: Long, before: Map<String, String?> = full, ease: Int = 3) =
        QueuedAnswer(seq, 3, 70, 0, 7, before, ease, 1234, 1_700_000_000_000L + seq)

    @Test fun roundTripKeepsEverythingTheQueueNeeds() {
        val d = AnswerJournal.decode(AnswerJournal.encode(listOf(a(1), a(2, ease = 1))))
        assertNull(d.problem)
        assertEquals(listOf(1L, 2L), d.answers.map { it.seq })
        val x = d.answers[0]
        assertEquals(3L, x.deckId); assertEquals(70L, x.nid); assertEquals(0, x.ord); assertEquals(7L, x.cardId)
        assertEquals(3, x.ease); assertEquals(1234L, x.tookMs); assertEquals(1_700_000_000_001L, x.madeAt)
        assertTrue("restored entries are marked", d.answers.all { it.restored })
        assertTrue("the compare set and the ids survive, a null stays a null", AnswerState.same(full, x.before))
        assertTrue(x.before.containsKey("last_review_time_secs")); assertNull(x.before["last_review_time_secs"])
        assertEquals("70", x.before["note_id"]); assertEquals("3", x.before["deck_id"])
    }

    @Test fun onlyTheSmallSetIsWritten() {
        val before = JSONObject(AnswerJournal.encode(listOf(a(1)))).getJSONArray("answers").getJSONObject(0).getJSONObject("before")
        assertEquals(AnswerState.JOURNAL.toSet(), before.keys().asSequence().toSet())
        assertEquals(1, JSONObject(AnswerJournal.encode(emptyList())).getInt("v"))
    }

    @Test fun noFileIsNotAProblemEverythingElseBrokenIs() {
        assertNull(AnswerJournal.decode(null).problem); assertEquals(0, AnswerJournal.decode(null).answers.size)
        for (bad in listOf("", "  \n", "garbage", "{", "{\"v\":0,\"answers\":[]}", "{\"v\":2,\"answers\":[]}", "{\"answers\":[]}", "{\"v\":1}", "{\"v\":1,\"answers\":5}", "[]", "null")) {
            val d = AnswerJournal.decode(bad)
            assertEquals(bad, 0, d.answers.size); assertNotNull(bad, d.problem)
        }
    }

    @Test fun unknownFieldsAreIgnoredAndOrderIsBySeq() {
        val text = JSONObject(AnswerJournal.encode(listOf(a(5), a(2)))).put("future", "x").toString()
            .replace("\"seq\":5", "\"seq\":5,\"extra\":{\"a\":1}")
        val d = AnswerJournal.decode(text)
        assertNull(d.problem); assertEquals(listOf(2L, 5L), d.answers.map { it.seq })
    }

    @Test fun unusableEntriesAreSkippedAndReportedTheRestStays() {
        val good = JSONObject(AnswerJournal.encode(listOf(a(1)))).getJSONArray("answers").getJSONObject(0)
        fun bad(edit: (JSONObject) -> Unit) = JSONObject(good.toString()).also(edit)
        val arr = org.json.JSONArray()
            .put(good)
            .put(bad { it.put("ease", 9) })
            .put(bad { it.put("seq", 3).remove("cardId") })
            .put(bad { it.put("seq", 4).getJSONObject("before").remove("reps") })
            .put(bad { it.put("seq", 5).getJSONObject("before").put("_id", "99") }) // card id and before disagree
            .put(bad { it.put("seq", 1) }) // duplicate seq
            .put("not an object")
        val d = AnswerJournal.decode(JSONObject().put("v", 1).put("answers", arr).toString())
        assertEquals(listOf(1L), d.answers.map { it.seq })
        assertTrue(d.problem!!, d.problem!!.startsWith("6 "))
    }

    @Test fun sameIgnoresOtherFieldsAndTreatsMissingAsNull() {
        assertTrue(AnswerState.same(full, full + ("lapses" to "9")))
        assertTrue(AnswerState.same(full, full - "last_review_time_secs"))
        assertFalse(AnswerState.same(full, full + ("due" to "13")))
        assertFalse(AnswerState.same(full, full + ("reps" to "4")))
        assertFalse(AnswerState.same(full, full + ("queue" to "3")))
        assertFalse(AnswerState.same(full, full + ("last_review_time_secs" to "5")))
        assertFalse(AnswerState.same(full, full + ("_id" to "8")))
    }

    @Test fun usableNeedsAnIdAndRepCount() {
        assertTrue(AnswerState.usable(full))
        assertFalse(AnswerState.usable(full - "_id")); assertFalse(AnswerState.usable(full + ("reps" to "x")))
    }

    @Test fun theButtonCountSurvivesAndAnOldOrOddOneBecomesFour() {
        val three = a(1).copy(buttons = 3)
        assertEquals(3, AnswerJournal.decode(AnswerJournal.encode(listOf(three))).answers.single().buttons)
        assertEquals("an old journal without the field", 4, AnswerJournal.decode(AnswerJournal.encode(listOf(a(2))).replace(",\"buttons\":4", "")).answers.single().buttons)
        assertEquals(4, AnswerJournal.decode(AnswerJournal.encode(listOf(a(3).copy(buttons = 9)))).answers.single().buttons)
    }
}
