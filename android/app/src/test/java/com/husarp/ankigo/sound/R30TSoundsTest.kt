package com.husarp.ankigo.sound

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class R30TSoundsTest {
    @Test fun chosenRecordingsAreBundledUnchangedWithDistinctCacheNamesAndLicences() {
        val expected = mapOf(
            SoundId.AGAIN to "20b325e8ad016a0c3e400515844059bc57c95ef0afbb571c2c60b99a871a7395",
            SoundId.HARD to "139e7e2251d1eb19fda071a5d5a7737bde448fffab6866300e93c8dfa52d2a41",
            SoundId.GOOD to "56cd7a6ab4a4255d5fea83cffc776f607a8603f659f4e5858f8d7a077d4531d1",
            SoundId.EASY to "ebe2db3ee4bb15418ee763d52a7f2df81634fca920249a6197367638aee767b2",
        )
        for ((id, hash) in expected) {
            val bytes = File("src/main/assets/${id.asset}").readBytes()
            assertEquals(hash, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            assertEquals("RIFF", String(bytes, 0, 4)); assertTrue(id.file.endsWith("-r30.wav"))
            assertEquals(SoundGroup.ANSWER_BUTTONS, id.group)
            assertFalse(SoundRules.shouldPlay(Cue.Effect(id), SoundContext(volumes = mapOf(SoundChannel.BUTTONS to 0))))
            assertFalse(SoundRules.shouldPlay(Cue.Effect(id), SoundContext(groupsOff = setOf(SoundGroup.ANSWER_BUTTONS))))
        }
        assertTrue(File("src/main/assets/licenses/answer-sounds.txt").readText().contains("CC0"))
        for (name in listOf("Kenney-Digital-CC0.txt", "Kenney-Interface-CC0.txt")) assertTrue(File("src/main/assets/licenses/$name").isFile)
    }

    @Test fun fallbackIsExactlyTodaysAnswerTickAndRespectsMenuSettings() {
        assertArrayEquals(SoftSfx.samples(SoftSound.SHOW_ANSWER), Sfx.samples(SoundId.DEFAULT_TAP))
        val cue = Cue.Effect(SoundId.DEFAULT_TAP)
        assertEquals(SoundChannel.BUTTONS, SoundId.DEFAULT_TAP.group.channel)
        assertFalse(SoundRules.shouldPlay(cue, SoundContext(groupsOff = setOf(SoundGroup.MENUS))))
        assertFalse(SoundRules.shouldPlay(cue, SoundContext(volumes = mapOf(SoundChannel.BUTTONS to 0))))
        assertFalse(SoundRules.shouldPlay(cue, SoundContext(muted = true)))
        assertFalse(SoundRules.shouldPlay(cue, SoundContext(ringerNormal = false)))
    }
}
