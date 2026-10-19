package com.husarp.ankigo.debug

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class GameLogTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun ringKeepsFiveHundredAndSurvivesReload() {
        val file = File(temp.root, "log")
        val log = RollingGameLog(file)
        log.append((0..599).map { "event=$it" }, Instant.EPOCH)
        val lines = RollingGameLog(file).all().lines()
        assertEquals(500, lines.size)
        assertTrue(lines.first().endsWith("event=100"))
        assertTrue(lines.last().endsWith("event=599"))
        assertFalse(File(file.path + ".tmp").exists())
    }
    @Test fun clearPersistsAndMultilineRecordCannotConsumeTheRing() {
        val file = File(temp.root, "log")
        val log = RollingGameLog(file, 3)
        log.append(listOf("first\nline\rline", "x".repeat(900)))
        assertEquals(2, log.all().lines().size)
        assertTrue(log.all().lines().all { it.length < 512 })
        log.clear()
        assertEquals("", RollingGameLog(file, 3).all())
    }
    @Test fun sourceAndGameFactsAreRecordedWithoutPersonalText() {
        val secret = "private card text and nickname"
        val events = listOf(
            GameEvent.Attack(true, secret, secret, secret, Type.NORMAL, Category.PHYSICAL, 7, 1, 1.0, false, false, false, 13, 20, 18, 21),
            GameEvent.Caught(secret, 16, secret, 5, false, secret, true, true),
            GameEvent.Evolved(secret, secret, secret, 17), GameEvent.Info(secret),
            GameEvent.Ignored(secret), GameEvent.Effect(true, secret, 10, 20),
            GameEvent.BadgeEarned(1, secret), GameEvent.ItemFound(secret, 2)
        )
        val text = events.joinToString("\n", transform = GameEventText::event)
        assertFalse(text.contains(secret))
        assertTrue(text.contains("damage=7")); assertTrue(text.contains("targetHp=13/20"))
        assertTrue(text.contains("species=16")); assertTrue(text.contains("species=17"))
        for (source in listOf("live", "study", "catch-up"))
            assertEquals("Answer source=$source card=123 button=4", GameEventText.answer(source, 123, 4))
        assertFalse(GameEventText.answer(secret, 123, 3).contains(secret))
    }
    @Test fun concurrentWritersLeaveBoundedReadableLog() {
        val file = File(temp.root, "log")
        val log = RollingGameLog(file, 40)
        val threads = List(3) { n -> Thread { repeat(30) { log.append(listOf("thread=$n event=$it")) } } }
        threads.forEach { it.start() }; threads.forEach { it.join() }
        assertEquals(40, RollingGameLog(file, 40).all().lines().size)
    }

    @Test fun theSwitchHitIsLoggedWithoutNames() {
        val secret = "a nickname"
        val text = GameEventText.event(GameEvent.SwitchHit(secret, secret, 13, 20))
        assertFalse(text.contains(secret))
        assertEquals("SwitchHit hp=13/20", text)
    }
}
