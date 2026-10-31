package com.husarp.ankigo.data

import com.husarp.ankigo.game.data.DexEntry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class DexTextTest {
    private fun bundle(): JSONObject = GZIPInputStream(File("src/main/assets/dextext.json.gz").inputStream())
        .bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }

    @Test fun bundledSpeciesResolvesWithoutCacheOrNetwork() {
        val entries = bundle()
        val entry = DexText.resolve(
            { DexText.bundledEntry(entries, 1) },
            { error("Disk cache must not be read") },
            { error("Network is unavailable") },
        )!!
        assertEquals("Seed Pokémon", entry.genus)
        assertTrue(entry.text!!.isNotBlank())
    }

    @Test fun everyBundledSpeciesHasEnglishCategoryAndText() {
        val entries = bundle()
        assertTrue(entries.length() >= 1025)
        for (number in 1..entries.length()) {
            val entry = DexText.bundledEntry(entries, number)!!
            assertTrue(entry.genus!!.isNotBlank())
            assertTrue(entry.text!!.isNotBlank())
            assertFalse(entry.text!!.contains('\n'))
            assertFalse(entry.text!!.contains('\u000C'))
        }
    }

    @Test fun missingBundleUsesCacheBeforeNetwork() {
        val cached = DexEntry("Cached category", "Cached text")
        assertEquals(cached, DexText.resolve(
            { DexText.bundledEntry(JSONObject(), 9999) }, { cached }, { error("Network is unavailable") },
        ))
    }

    @Test fun missingLocalEntryUsesOnlineFallback() {
        val online = DexEntry("Online category", "Online text")
        assertEquals(online, DexText.resolve({ null }, { null }, { online }))
        assertNull(DexText.resolve({ null }, { null }, { null }))
        assertNull(DexText.bundledEntry(JSONObject("{\"1\":{}}"), 1))
    }
}
