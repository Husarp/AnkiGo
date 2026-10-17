package com.husarp.ankigo.ui

import com.husarp.ankigo.sound.SoundGroup
import com.husarp.ankigo.sound.SoundId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundUiRulesTest {
    @Test fun fourButtonCardMapsOneToOne() {
        assertEquals(listOf(SoundId.AGAIN, SoundId.HARD, SoundId.GOOD, SoundId.EASY), (1..4).map { SoundUiRules.answerSound(4, it) })
    }

    @Test fun threeButtonCardIsAgainGoodEasy() {
        assertEquals(listOf(SoundId.AGAIN, SoundId.GOOD, SoundId.EASY), (1..3).map { SoundUiRules.answerSound(3, it) })
    }

    @Test fun twoButtonCardIsAgainGood() {
        assertEquals(listOf(SoundId.AGAIN, SoundId.GOOD), (1..2).map { SoundUiRules.answerSound(2, it) })
    }

    @Test fun answerClicksBelongToTheAnswerGroup() {
        for (b in 2..4) for (e in 1..b) assertEquals(SoundGroup.ANSWER_BUTTONS, SoundUiRules.answerSound(b, e).group)
    }

    @Test fun volumeSnapsToTens() {
        assertEquals(0, SoundUiRules.snapVolume(-5))
        assertEquals(0, SoundUiRules.snapVolume(4))
        assertEquals(10, SoundUiRules.snapVolume(5))
        assertEquals(40, SoundUiRules.snapVolume(44))
        assertEquals(50, SoundUiRules.snapVolume(45))
        assertEquals(70, SoundUiRules.snapVolume(70))
        assertEquals(100, SoundUiRules.snapVolume(96))
        assertEquals(100, SoundUiRules.snapVolume(250))
    }

    /** Every row of the Sound screen must be findable in Settings search: its title is in the catalog (Sounds section). */
    @Test fun everySoundRowIsInTheCatalog() {
        val f = File("src/main/java/com/husarp/ankigo/ui/SettingsSound.kt")
        assertTrue("cannot find ${f.absolutePath}", f.exists())
        val re = Regex("""(?:group|volume)\(\s*"((?:[^"\\]|\\.)*)"""")
        val titles = re.findAll(f.readText()).map { it.groupValues[1] }.toList()
        assertTrue("regex found too few rows (${titles.size})", titles.size >= 10)
        val missing = titles.filter { t -> SettingsCatalog.byTitle(t)?.section != CatalogSection.SOUNDS }
        assertEquals("Sound rows missing from the catalog", emptyList<String>(), missing)
    }

    @Test fun catalogSoundRowsAreTheNewOnesAndKeepOldIds() {
        val ids = SettingsCatalog.bySection(CatalogSection.SOUNDS).map { it.id }
        for (id in listOf("battleSounds", "cries", "quietOnCardAudio", "testSound", "volumeButtons", "volumeBattle", "volumeCries"))
            assertTrue(id, id in ids)
        assertTrue("volume" !in ids)
    }
}
