package com.husarp.ankigo

import java.time.LocalDateTime
import java.time.ZoneId
import com.husarp.ankigo.update.UpdateLogic
import com.husarp.ankigo.update.UpdateLogic.Asset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    @Test fun comparesAsNumbersNotText() {
        assertTrue(UpdateLogic.isNewer("0.5.11", "0.5.10"))
        assertTrue(UpdateLogic.isNewer("0.5.10", "0.5.9"))
        assertTrue(UpdateLogic.isNewer("3.10.0", "3.9.0"))
        assertFalse(UpdateLogic.isNewer("0.5.9", "0.5.10"))
        assertFalse(UpdateLogic.isNewer("0.5.11", "0.5.11"))
        assertEquals(0, UpdateLogic.compare("1.0", "1.0.0"))
        assertTrue(UpdateLogic.isNewer("1.0.1", "1.0"))
        assertTrue(UpdateLogic.isNewer("v1.2.0", "1.1.9"))
        assertEquals(0, UpdateLogic.compare("0.6.0-beta", "0.6.0"))
    }

    @Test fun readsTheVersionFromTheApkName() {
        assertEquals("0.5.11", UpdateLogic.apkVersion("AnkiGo-0.5.11.apk"))
        assertEquals("0.5.11", UpdateLogic.apkVersion("ankigo-v0.5.11.APK"))
        assertNull(UpdateLogic.apkVersion("AnkiGoSetup-0.84.7.exe"))
        assertNull(UpdateLogic.apkVersion("LockdownMobile.apk"))
    }

    @Test fun picksTheApkNotTheExeNorTheTag() {
        val assets = listOf(
            Asset("AnkiGoSetup-0.84.7.exe", "u1", 1),
            Asset("AnkiGo-0.5.11.apk", "u2", 2),
        )
        val (a, v) = UpdateLogic.pickApk(assets)!!
        assertEquals("u2", a.url); assertEquals("0.5.11", v)
        // two APKs: the higher version wins (compared as numbers)
        val two = assets + Asset("AnkiGo-0.5.9.apk", "u3", 3)
        assertEquals("0.5.11", UpdateLogic.pickApk(two)!!.second)
        // no APK in the release (a PC-only release) -> no Android update
        assertNull(UpdateLogic.pickApk(listOf(Asset("AnkiGoSetup-0.84.7.exe", "u1", 1))))
    }

    @Test fun aPcOnlyReleaseOnTopDoesNotHideThePhoneUpdate() {
        val pc10 = UpdateLogic.Release("p10", listOf(Asset("AnkiGoSetup-0.84.10.exe", "e10", 1)))
        val pc9 = UpdateLogic.Release("p9", listOf(Asset("AnkiGoSetup-0.84.9.exe", "e9", 1), Asset("AnkiGo-0.5.12.apk", "a12", 2)))
        val pc8 = UpdateLogic.Release("p8", listOf(Asset("AnkiGo-0.5.11.apk", "a11", 2)))
        val (rel, apk, v) = UpdateLogic.newestApk(listOf(pc10, pc9, pc8))!!
        assertEquals("p9", rel.page); assertEquals("a12", apk.url); assertEquals("0.5.12", v)
        // drafts and pre-releases don't count; no APK anywhere -> no Android update
        val draft = UpdateLogic.Release("d", listOf(Asset("AnkiGo-0.6.0.apk", "d", 2)), draft = true)
        val pre = UpdateLogic.Release("b", listOf(Asset("AnkiGo-0.6.1.apk", "b", 2)), prerelease = true)
        assertEquals("0.5.12", UpdateLogic.newestApk(listOf(draft, pre, pc10, pc9))!!.third)
        assertNull(UpdateLogic.newestApk(listOf(pc10)))
    }

    @Test fun asksGithubAtMostEveryFiveMinutes() {
        val t = 1_000_000_000L
        assertTrue(UpdateLogic.mayCheck(t, 0))                       // never asked yet
        assertFalse(UpdateLogic.mayCheck(t + 60_000, t))              // 1 min later
        assertFalse(UpdateLogic.mayCheck(t + 299_999, t))
        assertTrue(UpdateLogic.mayCheck(t + 300_000, t))              // 5 min later
        assertTrue(UpdateLogic.mayCheck(t - 1, t))                    // clock went back: don't get stuck
    }

    private val warsaw = ZoneId.of("Europe/Warsaw")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(warsaw).toInstant().toEpochMilli()
    private fun day(y: Int, mo: Int, d: Int, h: Int, mi: Int) = UpdateLogic.dayOf(ms(y, mo, d, h, mi), warsaw)

    @Test fun neverClosedBannerIsVisibleWhenNewer() {
        val today = day(2026, 10, 14, 12, 0)
        assertTrue(UpdateLogic.bannerVisible("0.5.12", "0.5.11", UpdateLogic.NO_DAY, today))
    }

    @Test fun noNewerVersionMeansNoBanner() {
        val today = day(2026, 10, 14, 12, 0)
        assertFalse(UpdateLogic.bannerVisible("0.5.11", "0.5.11", UpdateLogic.NO_DAY, today))
        assertFalse(UpdateLogic.bannerVisible("0.5.10", "0.5.11", UpdateLogic.NO_DAY, today))
        assertFalse(UpdateLogic.bannerVisible(null, "0.5.11", UpdateLogic.NO_DAY, today))
    }

    @Test fun closedTodayStaysHiddenAllDayAndAcrossRestarts() {
        val closed = day(2026, 10, 14, 9, 0)
        // the rule takes no app-start input: a restart or a return from another app later that day is the same call
        assertTrue(UpdateLogic.bannerHidden(closed, day(2026, 10, 14, 9, 1)))
        assertFalse(UpdateLogic.bannerVisible("0.5.12", "0.5.11", closed, day(2026, 10, 14, 23, 59)))
        assertFalse(UpdateLogic.bannerVisible("0.5.13", "0.5.11", closed, day(2026, 10, 14, 0, 0)))
    }

    @Test fun closedYesterdayIsVisibleAgain() {
        val closed = day(2026, 10, 13, 12, 0)
        assertTrue(UpdateLogic.bannerVisible("0.5.12", "0.5.11", closed, day(2026, 10, 14, 8, 0)))
    }

    @Test fun closedAt2359IsVisibleAtMidnight() {
        val closed = day(2026, 10, 14, 23, 59)
        assertFalse(UpdateLogic.bannerVisible("0.5.12", "0.5.11", closed, day(2026, 10, 14, 23, 59)))
        assertTrue(UpdateLogic.bannerVisible("0.5.12", "0.5.11", closed, day(2026, 10, 15, 0, 0)))
    }

    @Test fun dstChangeNightsStillRollOverAtLocalMidnight() {
        // Warsaw: clocks go forward 2026-03-29 (02:00 -> 03:00) and back 2026-10-25 (03:00 -> 02:00)
        val springClosed = day(2026, 3, 28, 23, 59)
        assertFalse(UpdateLogic.bannerVisible("0.5.12", "0.5.11", springClosed, day(2026, 3, 28, 23, 59)))
        assertTrue(UpdateLogic.bannerVisible("0.5.12", "0.5.11", springClosed, day(2026, 3, 29, 0, 0)))
        val springSunday = day(2026, 3, 29, 1, 59)
        assertFalse(UpdateLogic.bannerVisible("0.5.12", "0.5.11", springSunday, day(2026, 3, 29, 3, 0)))   // same day across the gap
        val autumnClosed = day(2026, 10, 25, 2, 30)   // the repeated hour: still the 25th
        assertFalse(UpdateLogic.bannerVisible("0.5.12", "0.5.11", autumnClosed, UpdateLogic.dayOf(ms(2026, 10, 25, 23, 0), warsaw)))
        assertTrue(UpdateLogic.bannerVisible("0.5.12", "0.5.11", autumnClosed, day(2026, 10, 26, 0, 0)))
    }
}
