package com.husarp.ankigo.ui

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/** PLAN 167: the memory behind the green "Everything downloaded" button (per enabled generations; "Clear picture cache" forgets it). */
class DownloadsDoneEverythingTest {
    /** A preferences file in memory: the string getter, and an editor with putString / remove / apply (all this memory uses). */
    private class Fake {
        val map = HashMap<String, String>()
        val bools = HashMap<String, Boolean>()
        val prefs: SharedPreferences = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, m, a ->
            when (m.name) {
                "getString" -> map[a[0] as String] ?: a[1]
                "getBoolean" -> bools[a[0] as String] ?: a[1]
                "contains" -> map.containsKey(a[0] as String)
                "edit" -> editor()
                else -> error("not faked: ${m.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = ArrayList<() -> Unit>()
            lateinit var self: SharedPreferences.Editor
            self = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, m, a ->
                when (m.name) {
                    "putBoolean" -> { pending += { bools[a[0] as String] = a[1] as Boolean }; self }
                    "putString" -> { pending += { map[a[0] as String] = a[1] as String }; self }
                    "remove" -> { pending += { map.remove(a[0] as String) }; self }
                    "apply", "commit" -> { pending.forEach { it() }; null }
                    else -> error("not faked: ${m.name}")
                }
            } as SharedPreferences.Editor
            return self
        }
    }

    private val default = (1..8).toSet()

    @Test fun nothingIsDoneAtFirst() {
        val f = Fake()
        assertFalse(DownloadsDone.isDoneIn(f.prefs, DownloadsDone.EVERYTHING, default))
    }

    @Test fun aCleanRunIsRemembered() {
        val f = Fake()
        DownloadsDone.markIn(f.prefs, DownloadsDone.EVERYTHING, default)
        assertTrue(DownloadsDone.isDoneIn(f.prefs, DownloadsDone.EVERYTHING, default))
        assertTrue("fewer generations are covered too", DownloadsDone.isDoneIn(f.prefs, DownloadsDone.EVERYTHING, setOf(1, 2)))
    }

    @Test fun enablingAnotherGenerationShowsTheButtonAgain() {
        val f = Fake()
        DownloadsDone.markIn(f.prefs, DownloadsDone.EVERYTHING, default)
        assertFalse(DownloadsDone.isDoneIn(f.prefs, DownloadsDone.EVERYTHING, default + 9))
        // a later run for all nine covers both
        DownloadsDone.markIn(f.prefs, DownloadsDone.EVERYTHING, default + 9)
        assertTrue(DownloadsDone.isDoneIn(f.prefs, DownloadsDone.EVERYTHING, default + 9))
    }

    @Test fun theMemoryIsItsOwnKeyNotThePicturesOne() {
        val f = Fake()
        DownloadsDone.markIn(f.prefs, DownloadsDone.PICTURES, default)
        assertFalse("pictures done is not everything done", DownloadsDone.isDoneIn(f.prefs, DownloadsDone.EVERYTHING, default))
        DownloadsDone.markIn(f.prefs, DownloadsDone.EVERYTHING, setOf(1))
        assertFalse(DownloadsDone.isDoneIn(f.prefs, DownloadsDone.PICTURES, default + 9))
    }

    @Test fun clearingThePictureCacheForgetsEverythingAndThePicturesAndTheOldAnimationsKey() {
        val f = Fake()
        f.map["dl_done_animations"] = "1,2,3"   // a phone that had the old "Battle animations: Downloaded" button
        DownloadsDone.markIn(f.prefs, DownloadsDone.PICTURES, default)
        DownloadsDone.markIn(f.prefs, DownloadsDone.EVERYTHING, default)
        DownloadsDone.clearIn(f.prefs)
        assertFalse(DownloadsDone.isDoneIn(f.prefs, DownloadsDone.EVERYTHING, default))
        assertFalse(DownloadsDone.isDoneIn(f.prefs, DownloadsDone.PICTURES, default))
        assertNull(f.map["dl_done_animations"])
        assertEquals(emptyMap<String, String>(), f.map)
    }

    @Test fun nothingMissingTurnsThePicturesOnAndIsRemembered() {
        val f = Fake()
        f.bools["skipSprites"] = true   // "Download pictures as needed" is off
        DownloadsDone.markNothingMissingIn(f.prefs, default)   // the scan found an empty todo
        assertTrue("the button turns the pictures on", com.husarp.ankigo.data.Sprites.enabledIn(f.prefs))
        assertTrue(DownloadsDone.isDoneIn(f.prefs, DownloadsDone.EVERYTHING, default))
    }

    @Test fun theOldAnimationsMemoryDoesNotMakeTheNewButtonGreen() {
        val f = Fake()
        f.map["dl_done_animations"] = default.joinToString(",")
        assertFalse(DownloadsDone.isDoneIn(f.prefs, DownloadsDone.EVERYTHING, default))
    }
}
