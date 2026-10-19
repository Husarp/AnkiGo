package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The journal as a real file (temp file + rename): what a restart reads back. */
class FileJournalStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val before = mapOf("_id" to "7", "note_id" to "70", "deck_id" to "3", "reps" to "3", "due" to "12", "queue" to "2", "last_review_time_secs" to "100")
    private fun a(seq: Long) = QueuedAnswer(seq, 3, 70, 0, 7, before, 3, 1500, 1_700_000_000_000L + seq, buttons = 3)

    @Test fun aMissingFileReadsAsNoJournalAndNoProblem() {
        val store = FileJournalStore(File(tmp.root, "study-answers.json"))
        assertNull(store.read())
        val d = AnswerJournal.decode(store.read())
        assertTrue(d.answers.isEmpty()); assertNull(d.problem)
    }

    @Test fun whatIsWrittenComesBackAfterARestart() {
        val file = File(tmp.root, "study-answers.json")
        FileJournalStore(file).write(AnswerJournal.encode(listOf(a(1), a(2))))
        val d = AnswerJournal.decode(FileJournalStore(file).read())   // a new store object = a new process
        assertNull(d.problem)
        assertEquals(listOf(1L, 2L), d.answers.map { it.seq })
        assertTrue(d.answers.all { it.restored && it.buttons == 3 && it.cardId == 7L })
        assertEquals(listOf("study-answers.json"), tmp.root.list()!!.toList())   // no temp file is left behind
    }

    @Test fun aSecondWriteReplacesTheFirstWhole() {
        val file = File(tmp.root, "study-answers.json")
        val store = FileJournalStore(file)
        store.write(AnswerJournal.encode(listOf(a(1), a(2), a(3))))
        store.write(AnswerJournal.encode(listOf(a(3))))
        assertEquals(listOf(3L), AnswerJournal.decode(store.read()).answers.map { it.seq })
        store.write(AnswerJournal.encode(emptyList()))
        assertTrue(AnswerJournal.decode(store.read()).answers.isEmpty())
    }

    @Test fun aCorruptOrEmptyFileIsNoAnswersWithAReasonAndNeverACrash() {
        val file = File(tmp.root, "study-answers.json")
        for (junk in listOf("", "   ", "not json at all", "{\"v\":1", "{\"v\":99,\"answers\":[]}", "\u0000\u0000\u0000")) {
            file.writeText(junk)
            val d = AnswerJournal.decode(FileJournalStore(file).read())
            assertTrue(junk, d.answers.isEmpty()); assertNotNull(junk, d.problem)
        }
    }

    @Test fun aHalfWrittenTempFileNeverHidesTheRealJournal() {
        val file = File(tmp.root, "study-answers.json")
        val store = FileJournalStore(file)
        store.write(AnswerJournal.encode(listOf(a(1))))
        File(tmp.root, "study-answers.json.tmp").writeText("{\"v\":1,\"answers\":[{half")   // the process died while writing the next version
        assertEquals(listOf(1L), AnswerJournal.decode(store.read()).answers.map { it.seq })
        store.write(AnswerJournal.encode(listOf(a(2))))                                     // the next write starts from a clean temp file
        assertEquals(listOf(2L), AnswerJournal.decode(store.read()).answers.map { it.seq })
        assertFalse(File(tmp.root, "study-answers.json.tmp").exists())
    }

    @Test fun aWriteThatCannotHappenThrowsAndKeepsTheOldJournal() {
        val file = File(tmp.root, "study-answers.json")
        FileJournalStore(file).write(AnswerJournal.encode(listOf(a(1))))
        val gone = FileJournalStore(File(File(tmp.root, "no-such-dir"), "study-answers.json"))
        assertTrue(runCatching { gone.write("x") }.isFailure)
        assertEquals(listOf(1L), AnswerJournal.decode(FileJournalStore(file).read()).answers.map { it.seq })
    }

    @Test fun theQueueUsesItAsItsJournalAcrossARestart() {
        val file = File(tmp.root, "study-answers.json")
        val fake = FakeAnki().also { f -> for (i in 1..2) f.add(7, 100L + i) }
        val hooks = object : AnswerHooks {
            override fun expect(before: Map<String, String?>) {}
            override fun cancel(before: Map<String, String?>) {}
            override fun outcome(o: AnswerOutcome) {}
        }
        val first = AnswerQueue(fake, FileJournalStore(file), hooks, LockWaiter())
        val c = fake.queueOf(7).first()
        first.enqueue(QueuedAnswer(first.nextSeq(), 7, c.nid, c.ord, c.id, fake.stats(c.key)!!, 3, 400, 1))
        // the process dies here: a new queue over the same file replays it, once
        val second = AnswerQueue(fake, FileJournalStore(file), hooks, LockWaiter())
        assertEquals(1, second.restore().count)
        val out = second.drain().single()
        assertTrue(out is AnswerOutcome.Saved)
        assertEquals(1, fake.revlog.size)
        assertTrue(AnswerJournal.decode(FileJournalStore(file).read()).answers.isEmpty())
        assertFalse("the last answer leaves no file at all", file.exists())
    }

    @Test fun clearDeletesTheFileAndAMissingFileIsFine() {
        val file = File(tmp.root, "study-answers.json")
        val store = FileJournalStore(file)
        store.clear() // nothing there yet
        store.write(AnswerJournal.encode(listOf(a(1))))
        store.clear()
        assertFalse(file.exists()); assertNull(store.read())
        assertEquals(emptyList<String>(), tmp.root.list()!!.toList())
        assertNull(AnswerJournal.decode(FileJournalStore(file).read()).problem) // a restart reads "no journal", not a problem
    }

    @Test fun clearAlsoRemovesALeftoverTempFile() {
        val file = File(tmp.root, "study-answers.json")
        val store = FileJournalStore(file)
        store.write(AnswerJournal.encode(listOf(a(1))))
        File(tmp.root, "study-answers.json.tmp").writeText("{\"v\":1,\"answers\":[{half")
        store.clear()
        assertEquals(emptyList<String>(), tmp.root.list()!!.toList())
    }

    @Test fun aQueueWhoseWritesAlwaysFailStillClearsTheRealFile() {
        val file = File(tmp.root, "study-answers.json")
        val fake = FakeAnki().also { f -> for (i in 1..2) f.add(7, 100L + i) }
        val hooks = object : AnswerHooks {
            override fun expect(before: Map<String, String?>) {}
            override fun cancel(before: Map<String, String?>) {}
            override fun outcome(o: AnswerOutcome) {}
        }
        val real = FileJournalStore(file)
        val q = AnswerQueue(fake, real, hooks, LockWaiter())
        val c = fake.queueOf(7).first()
        q.enqueue(QueuedAnswer(q.nextSeq(), 7, c.nid, c.ord, c.id, fake.stats(c.key)!!, 3, 400, 1))
        assertTrue(file.exists())
        fake.moveToTop(fake.queueOf(7).last()) // not first any more: told, not written
        // from here the disk is full: every write fails, deleting still works
        val full = object : JournalStore by real { override fun write(text: String) { throw java.io.IOException("disk full") } }
        val q2 = AnswerQueue(fake, full, hooks, LockWaiter())
        assertEquals(1, q2.restore().count)
        assertEquals(AnswerOutcome.Reason.NOT_TOP, (q2.drain().single() as AnswerOutcome.NotWritten).reason)
        assertFalse("nothing is left to replay", file.exists())
        assertEquals(0, AnswerQueue(fake, real, hooks, LockWaiter()).restore().count)
    }
}
