package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class BeforeReplaceTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun backupIsDurableAndNeverReplacesCurrentOrAnotherBackup() {
        val store = SaveStore(temp.newFolder("game"))
        val state = TestData.state(TestData.world())
        store.save(state)
        val current = File(store.dir, "save.json").readText()
        val first = store.backupBeforeReplace(state, "ankimon", 123)
        val second = store.backupBeforeReplace(state, "ankimon", 123)
        assertNotEquals(first, second)
        assertEquals(current, first.readText())
        assertEquals(current, second.readText())
        assertEquals(current, File(store.dir, "save.json").readText())
        assertTrue(store.backups().containsAll(listOf(first, second)))
        assertFalse(File(first.path + ".tmp").exists())
    }
    @Test fun failedBackupThrowsWithoutTouchingCurrentGame() {
        val store = SaveStore(temp.newFolder("game"))
        val state = TestData.state(TestData.world())
        store.save(state)
        val current = File(store.dir, "save.json").readText()
        File(store.dir, "save.before-restore-123.json.tmp").mkdir()
        try { store.backupBeforeReplace(state, "restore", 123); fail("backup should fail") }
        catch (_: IOException) {}
        assertEquals(current, File(store.dir, "save.json").readText())
        assertFalse(File(store.dir, "save.before-restore-123.json").exists())
    }
}
