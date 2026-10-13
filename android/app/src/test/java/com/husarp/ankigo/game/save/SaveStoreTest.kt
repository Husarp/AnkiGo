package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class SaveStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dir get() = File(tmp.root, "game")
    private fun store() = SaveStore(dir)

    private fun state(cash: Int = 0, day: String = "2026-10-13"): GameState {
        val s = GameState(createdAt = 1, day = day, starterChosen = true)
        s.mons += Mon("m1", 25, "Pikachu", 5, 0, Stats(1, 2, 3, 4, 5, 6), Stats.ZERO, Nature.HARDY, 'M', false, "a", mutableListOf("tackle"), 20)
        s.team += "m1"
        s.cash = cash
        return s
    }

    private fun file(name: String) = File(dir, name)
    private fun seqOf(f: File) = SaveCodec.decode(f.readText()).seq
    private fun corruptFiles() = dir.listFiles { f -> f.name.startsWith("save.corrupt-") }.orEmpty().toList()

    @Test fun emptyOrMissingDirectoryIsANewPlayer() {
        val l = store().load()
        assertNull(l.state); assertNull(l.source); assertTrue(l.problems.isEmpty())
        dir.mkdirs()
        assertNull(store().load().state)
    }

    @Test fun firstSaveCreatesSaveJsonAndSecondCreatesBakWithPreviousSeq() {
        val st = store(); val s = state(cash = 1)
        st.save(s, now = 1000)
        assertTrue(file("save.json").isFile); assertFalse(file("save.bak").exists()); assertFalse(file("save.json.tmp").exists())
        assertEquals(1, seqOf(file("save.json")))
        s.cash = 2
        st.save(s, now = 2000)
        assertEquals(2, seqOf(file("save.json"))); assertEquals(1, seqOf(file("save.bak")))
        val d = SaveCodec.decode(file("save.json").readText())
        assertEquals(2000, d.savedAt); assertEquals(2, d.cash)
        assertEquals(1000, SaveCodec.decode(file("save.bak").readText()).savedAt)
        assertFalse(file("save.json.tmp").exists())
    }

    @Test fun prepareBumpsSeqAndSavedAt() {
        val s = state()
        val j1 = store().prepare(s, 111)
        assertEquals(1, s.seq); assertEquals(111, s.savedAt)
        val j2 = store().prepare(s, 222)
        assertEquals(2, s.seq); assertEquals(222, s.savedAt)
        assertEquals(2, SaveCodec.decode(j2).seq); assertEquals(1, SaveCodec.decode(j1).seq)
    }

    @Test fun loadReturnsWhatWasSaved() {
        val s = state(cash = 77)
        store().save(s)
        val l = store().load()
        assertEquals("save.json", l.source); assertTrue(l.problems.isEmpty())
        assertEquals(77, l.state!!.cash); assertEquals(1, l.state!!.seq)
    }

    @Test fun corruptSaveJsonFallsBackToBakMovesTheBadFileAndKeepsTheGoodDataInBak() {
        val st = store(); val s = state(cash = 1)
        st.save(s); s.cash = 2; st.save(s) // json seq 2 cash 2, bak seq 1 cash 1
        file("save.json").writeText("{\"schema\":1,\"mons\":[{\"uid\"") // torn write
        val st2 = store()
        val l = st2.load()
        assertEquals(1, l.state!!.cash); assertEquals("save.bak", l.source)
        assertTrue(l.problems.any { it.contains("save.json") })
        assertEquals(1, corruptFiles().size)
        assertEquals("{\"schema\":1,\"mons\":[{\"uid\"", corruptFiles()[0].readText())
        // the good state was written back as save.json, so the next start is clean
        assertEquals(1, SaveCodec.decode(file("save.json").readText()).cash)
        // and the next write must not push a bad file over the backup
        val loaded = l.state!!; loaded.cash = 3
        st2.save(loaded)
        assertEquals(3, SaveCodec.decode(file("save.json").readText()).cash)
        assertEquals(1, SaveCodec.decode(file("save.bak").readText()).cash)
        assertTrue(store().load().problems.isEmpty())
    }

    @Test fun emptyOrGarbageSaveJsonNeverReplacesAGoodBackupEvenWithoutLoad() {
        val st = store(); val s = state(cash = 1)
        st.save(s); s.cash = 2; st.save(s)
        file("save.json").writeText("") // an empty file, written by something else
        val fresh = store() // never loaded
        s.cash = 3
        fresh.save(s)
        assertEquals(3, SaveCodec.decode(file("save.json").readText()).cash)
        assertEquals(1, SaveCodec.decode(file("save.bak").readText()).cash) // the old good bak survived
        assertEquals(1, corruptFiles().size)
    }

    @Test fun aNewerValidTmpBeatsSaveJsonAndIsPromoted() {
        val st = store(); val s = state(cash = 1)
        st.save(s) // seq 1
        s.cash = 2
        val json2 = st.prepare(s) // seq 2, "crashed" before the rename
        file("save.json.tmp").writeText(json2)
        val l = store().load()
        assertEquals(2, l.state!!.cash); assertEquals("save.json.tmp", l.source)
        assertEquals(2, seqOf(file("save.json"))); assertEquals(1, seqOf(file("save.bak")))
        assertFalse(file("save.json.tmp").exists())
        assertTrue(corruptFiles().isEmpty())
    }

    @Test fun crashBetweenTheTwoRenamesRecoversFromTmpAndBak() {
        val st = store(); val s = state(cash = 1)
        st.save(s); s.cash = 2; st.save(s)
        // simulate: save.json renamed to save.bak (seq 2), tmp (seq 3) not yet renamed
        s.cash = 3
        val json3 = st.prepare(s)
        file("save.bak").delete(); file("save.json").renameTo(file("save.bak")); file("save.json.tmp").writeText(json3)
        val l = store().load()
        assertEquals(3, l.state!!.cash)
        assertEquals(3, seqOf(file("save.json"))); assertEquals(2, seqOf(file("save.bak")))
    }

    @Test fun truncatedTmpIsIgnoredAndMovedAside() {
        val st = store(); val s = state(cash = 1)
        st.save(s)
        s.cash = 2
        val json2 = st.prepare(s)
        file("save.json.tmp").writeText(json2.substring(0, json2.length / 2))
        val l = store().load()
        assertEquals(1, l.state!!.cash); assertEquals("save.json", l.source)
        assertTrue(l.problems.any { it.contains("save.json.tmp") })
        assertFalse(file("save.json.tmp").exists()); assertEquals(1, corruptFiles().size)
        assertEquals(1, SaveCodec.decode(file("save.json").readText()).cash)
    }

    @Test fun staleValidTmpWithLowerSeqLoses() {
        val st = store(); val s = state(cash = 1)
        st.save(s)
        val old = file("save.json").readText()
        s.cash = 2; st.save(s)
        file("save.json.tmp").writeText(old) // seq 1 < 2
        val l = store().load()
        assertEquals(2, l.state!!.cash); assertEquals("save.json", l.source)
    }

    @Test fun tieOnSeqPrefersTheEarlierCandidate() {
        val st = store(); val s = state(cash = 1)
        st.save(s)
        val a = SaveCodec.decode(file("save.json").readText()); a.cash = 55 // same seq 1, different content
        file("save.bak").writeText(SaveCodec.encode(a))
        assertEquals(1, store().load().state!!.cash)
    }

    @Test fun onlySnapshotsLeftLoadsTheNewestSnapshot() {
        val st = store(); val s = state(cash = 1, day = "2026-10-11")
        st.save(s); s.day = "2026-10-12"; s.cash = 2; st.save(s); s.day = "2026-10-13"; s.cash = 3; st.save(s)
        file("save.json").delete(); file("save.bak").delete()
        val l = store().load()
        assertEquals(3, l.state!!.cash); assertEquals("snapshots/save-2026-10-13.json", l.source)
        assertTrue(file("save.json").isFile) // promoted
        assertEquals(3, SaveCodec.decode(file("save.json").readText()).cash)
        assertEquals(3, file("snapshots").listFiles()!!.size)
    }

    @Test fun snapshotIsWrittenOncePerDayAndKeepsTheNewestSeven() {
        val st = store(); val s = state(cash = 0)
        for (d in 1..10) {
            s.day = "2026-10-%02d".format(d)
            st.save(s); s.cash++; st.save(s) // two saves a day: the snapshot must come from the first
        }
        val names = file("snapshots").listFiles()!!.map { it.name }.sorted()
        assertEquals((4..10).map { "save-2026-10-%02d.json".format(it) }, names)
        assertEquals(19L, seqOf(File(file("snapshots"), "save-2026-10-10.json")))
        assertTrue(file("snapshots").listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun badDayStringsNeverEscapeTheSnapshotsFolder() {
        val st = store(); val s = state()
        st.write(st.prepare(s), "../evil"); st.write(st.prepare(s), "")
        assertFalse(File(tmp.root, "evil.json").exists())
        assertFalse(file("snapshots").exists() && file("snapshots").listFiles()!!.isNotEmpty())
        assertEquals(2, seqOf(file("save.json")))
    }

    @Test fun allFilesCorruptGivesNullWithProblemsAndKeepsEverythingForInspection() {
        file("snapshots").mkdirs()
        file("save.json").writeText("garbage"); file("save.json.tmp").writeText("{"); file("save.bak").writeText("")
        File(file("snapshots"), "save-2026-10-13.json").writeText("[]")
        val l = store().load()
        assertNull(l.state); assertNull(l.source)
        assertEquals(4, l.problems.size)
        assertEquals(2, corruptFiles().size) // save.json and tmp moved aside, bak and snapshot only listed
        assertTrue(file("save.bak").exists()); assertTrue(File(file("snapshots"), "save-2026-10-13.json").exists())
    }

    @Test fun saveFromANewerAppIsReportedAndNeverMoved() {
        dir.mkdirs()
        file("save.json").writeText("""{"schema":99,"mons":[]}""")
        val l = store().load()
        assertNull(l.state)
        assertTrue(l.problems.single().contains("newer AnkiGo"))
        assertTrue(file("save.json").exists()); assertTrue(corruptFiles().isEmpty())
        // if a write happens anyway, the newer save is kept as the backup
        store().save(state())
        assertTrue(file("save.bak").readText().contains("\"schema\":99"))
    }

    @Test fun backupsListBakThenSnapshotsNewestFirst() {
        val st = store(); val s = state()
        s.day = "2026-10-12"; st.save(s); s.day = "2026-10-13"; st.save(s)
        assertEquals(listOf("save.bak", "save-2026-10-13.json", "save-2026-10-12.json"), st.backups().map { it.name })
        assertEquals(emptyList<File>(), SaveStore(File(tmp.root, "nothing")).backups())
    }

    @Test fun readDecodesWithoutTouchingTheCurrentSaveAndReturnsNullForInvalid() {
        val st = store(); val s = state(cash = 9)
        st.save(s)
        val before = file("save.json").readText()
        assertEquals(9, st.read(file("save.json"))!!.cash)
        val bad = tmp.newFile("bad.json"); bad.writeText("nope")
        assertNull(st.read(bad)); assertNull(st.read(File(tmp.root, "missing.json")))
        assertEquals(before, file("save.json").readText())
    }

    @Test fun restoreLiftsSeqAboveEverythingOnDisk() {
        val st = store(); val s = state(cash = 1)
        repeat(5) { st.save(s) } // seq 5
        val old = tmp.newFile("old.json"); old.writeText(SaveCodec.encode(state(cash = 100).also { it.seq = 2 }))
        val r = st.restore(old)!!
        assertEquals(5, r.seq); assertEquals(100, r.cash)
        st.save(r)
        assertEquals(100, store().load().state!!.cash)
        assertNull(st.restore(File(tmp.root, "missing.json")))
    }

    @Test fun unicodeAndSpecialCharsSurviveTheFile() {
        val s = state(); s.liveSnapshot = "a\"b\nc é ♀ 日本"; s.settings.trainerName = "Flabébé"
        store().save(s)
        val b = store().load().state!!
        assertEquals("a\"b\nc é ♀ 日本", b.liveSnapshot); assertEquals("Flabébé", b.settings.trainerName)
    }

    @Test fun emptyDocumentIsRefusedAndTheSaveIsUntouched() {
        val st = store(); st.save(state(cash = 4))
        val before = file("save.json").readText()
        assertThrows(IllegalArgumentException::class.java) { st.write("", "2026-10-13") }
        assertThrows(IllegalArgumentException::class.java) { st.write("  \n", "2026-10-13") }
        assertEquals(before, file("save.json").readText()); assertFalse(file("save.bak").exists())
    }

    @Test fun failedWriteLeavesTheOldSaveAndBackupIntact() {
        val st = store(); val s = state(cash = 1)
        st.save(s); s.cash = 2; st.save(s)
        val json = file("save.json").readText(); val bak = file("save.bak").readText()
        // a directory squatting on the tmp name makes the tmp write fail
        file("save.json.tmp").mkdirs()
        s.cash = 3
        assertThrows(IOException::class.java) { st.save(s) }
        assertEquals(json, file("save.json").readText()); assertEquals(bak, file("save.bak").readText())
        file("save.json.tmp").deleteRecursively()
        st.save(s) // works again
        assertEquals(3, SaveCodec.decode(file("save.json").readText()).cash)
    }

    @Test fun failedRotationLeavesTheOldSave() {
        val st = store(); val s = state(cash = 1)
        st.save(s)
        val json = file("save.json").readText()
        // save.bak as a non-empty directory: renaming save.json onto it fails
        file("save.bak").mkdirs(); File(file("save.bak"), "x").writeText("x")
        s.cash = 2
        assertThrows(IOException::class.java) { st.save(s) }
        assertEquals(json, file("save.json").readText())
        // the finished tmp (newer, valid) is not lost either: the next load returns it
        assertEquals(2, store().load().state!!.cash)
    }

    @Test fun concurrentWritesFromTwoThreadsLeaveAValidSaveWithTheHighestSeq() {
        val st = store()
        val s = state()
        val lock = Any()
        val start = CountDownLatch(1)
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val threads = (1..2).map { n ->
            thread {
                start.await()
                try {
                    repeat(100) { i ->
                        val json = synchronized(lock) { s.cash = n * 1000 + i; st.prepare(s) }
                        st.write(json, "2026-10-13")
                    }
                } catch (e: Throwable) { errors += e }
            }
        }
        start.countDown(); threads.forEach { it.join() }
        assertTrue(errors.toString(), errors.isEmpty())
        val l = store().load()
        assertEquals(200, l.state!!.seq)
        assertTrue(l.problems.isEmpty())
        assertEquals(200, seqOf(file("save.json"))) // load put the newest back if the last writer was an older one
        assertFalse(file("save.json.tmp").exists())
        assertTrue(corruptFiles().isEmpty())
        assertEquals(1, file("snapshots").listFiles()!!.size)
    }

    @Test fun twoStoreInstancesOnOneFolderDoNotInterleave() {
        val a = SaveStore(dir); val b = SaveStore(dir)
        val sa = state(cash = 1); val sb = state(cash = 2); sb.seq = 1000
        val ts = listOf(thread { repeat(50) { a.save(sa) } }, thread { repeat(50) { b.save(sb) } })
        ts.forEach { it.join() }
        val l = SaveStore(dir).load()
        assertNotNull(l.state); assertTrue(l.problems.toString(), l.problems.isEmpty())
        assertTrue(corruptFiles().isEmpty()); assertFalse(file("save.json.tmp").exists())
        assertNotNull(SaveStore(dir).read(file("save.bak")))
    }
}
