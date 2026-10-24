package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Stat
import org.junit.Assert.*
import org.junit.Test

class StatInfoTest {
    @Test fun everyStatHasText() {
        Stat.entries.forEach { s ->
            val i = StatInfo.of(s)
            assertTrue(i.title.isNotBlank()); assertTrue(i.text.isNotBlank())
        }
        StatInfo.all().forEach { assertTrue(it.title.isNotBlank() && it.text.isNotBlank()) }
    }

    @Test fun noPercentOfHp() {
        assertFalse(StatInfo.of(Stat.HP).text.contains("%") && !StatInfo.of(Stat.HP).text.contains("never a percent"))
    }

    @Test fun rulesMatchTheGame() {
        assertTrue(StatInfo.IV.text.contains("0 to 31"))
        assertTrue(StatInfo.EV.text.contains("252") && StatInfo.EV.text.contains("510"))
        assertTrue(StatInfo.NATURE.text.contains("10 %"))
        assertTrue(StatInfo.EV.text.contains("every win"))
        assertTrue(StatInfo.FRIENDSHIP.text.contains("255") && StatInfo.FRIENDSHIP.text.contains("160") && StatInfo.FRIENDSHIP.text.contains("5 to 9"))
        assertTrue(StatInfo.FRIENDSHIP.text.contains("Soothe Bell") && StatInfo.FRIENDSHIP.text.contains("Return"))
        assertTrue(StatInfo.FRIENDSHIP in StatInfo.all())
        assertEquals("255 / 255", DetailFormat.friendship(300))
        assertEquals("0 / 255", DetailFormat.friendship(-3))
        assertEquals("142 / 255", DetailFormat.friendship(142))
        assertTrue(StatInfo.of(Stat.SPE).text.contains("first"))
    }

    @Test fun compareKeys() {
        listOf("hp", "atk", "def", "spa", "spd", "spe", "base_hp", "base_spe", "total", "base_total", "ivs", "nature", "level")
            .forEach { assertNotNull(it, StatInfo.forKey(it)) }
        assertNull(StatInfo.forKey("types"))
        assertNull(StatInfo.forKey("moves"))
    }
}
