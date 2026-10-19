package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R10-X2: the four "When you beat a wild Pokémon" options <-> autoMode + autoReleaseDuplicates. */
class CatchChoiceTest {
    private fun settings(mode: Int, swap: Boolean) = Settings().also { it.autoMode = mode; it.autoReleaseDuplicates = swap }

    @Test fun eachOptionWritesBothFields() {
        val s = Settings()
        CatchChoice.apply(s, CatchChoice.ALWAYS); assertEquals(1 to false, s.autoMode to s.autoReleaseDuplicates)
        CatchChoice.apply(s, CatchChoice.NEW_ONLY); assertEquals(3 to false, s.autoMode to s.autoReleaseDuplicates)
        CatchChoice.apply(s, CatchChoice.NEW_AND_BETTER); assertEquals(3 to true, s.autoMode to s.autoReleaseDuplicates)
        CatchChoice.apply(s, CatchChoice.DEFEAT); assertEquals(2 to false, s.autoMode to s.autoReleaseDuplicates)
    }

    @Test fun everyOptionReadsBackAsItself() {
        for (c in CatchChoice.entries) {
            val s = Settings(); CatchChoice.apply(s, c)
            assertEquals(c, CatchChoice.of(s))
        }
    }

    @Test fun defaultIsCatchOnlyNewSpecies() {
        assertEquals(CatchChoice.NEW_ONLY, CatchChoice.of(Settings()))
    }

    @Test fun oldSavesMapToTheMatchingOption() {
        assertEquals(CatchChoice.ALWAYS, CatchChoice.of(settings(1, false)))
        assertEquals(CatchChoice.DEFEAT, CatchChoice.of(settings(2, false)))
        assertEquals(CatchChoice.NEW_ONLY, CatchChoice.of(settings(3, false)))
        assertEquals(CatchChoice.NEW_AND_BETTER, CatchChoice.of(settings(3, true)))
        // the switch only counts with mode 3; a decoded old save with mode 1/2 + switch is normalized (switch off, mode kept)
        assertEquals(CatchChoice.ALWAYS, CatchChoice.of(settings(1, true)))
        assertEquals(CatchChoice.DEFEAT, CatchChoice.of(settings(2, true)))
        for (m in listOf(1, 2)) { val s = settings(m, true); CatchChoice.normalize(s); assertEquals(m to false, s.autoMode to s.autoReleaseDuplicates) }
        val keep = settings(3, true); CatchChoice.normalize(keep); assertTrue(keep.autoReleaseDuplicates)
    }

    @Test fun loadedOldSaveShowsItsOptionAndSurvivesAChange() {
        val old = SaveCodec.decode("""{"schema":1,"settings":{"autoMode":2},"mons":[{"uid":"a","species":25,"level":5}]}""")
        assertEquals(CatchChoice.DEFEAT, CatchChoice.of(old.settings))
        val withSwitch = SaveCodec.decode("""{"schema":1,"settings":{"autoMode":1,"autoReleaseDuplicates":true},"mons":[]}""")
        assertEquals(CatchChoice.ALWAYS, CatchChoice.of(withSwitch.settings))
        assertFalse(withSwitch.settings.autoReleaseDuplicates) // what the list shows is what the game does
        CatchChoice.apply(withSwitch.settings, CatchChoice.ALWAYS)
        val back = SaveCodec.decode(SaveCodec.encode(withSwitch))
        assertEquals(1, back.settings.autoMode)
        assertFalse(back.settings.autoReleaseDuplicates)
        CatchChoice.apply(back.settings, CatchChoice.NEW_AND_BETTER)
        assertTrue(SaveCodec.decode(SaveCodec.encode(back)).settings.autoReleaseDuplicates)
    }
}
