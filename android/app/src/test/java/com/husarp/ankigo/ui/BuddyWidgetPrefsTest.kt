package com.husarp.ankigo.ui

import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.shots.Shots
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BuddyWidgetPrefsTest {
    private val data = TestData.world()
    private val zone = ZoneId.of("Europe/Warsaw")
    private val now = 1_760_000_000_000L

    /** Team = the main only; "boxed" is owned but not on the team. */
    private fun state() = TestData.state(data).also { s ->
        TestData.mon(data, 16, 9, uid = "boxed").also { s.mons += it }
    }

    @Test fun defaultIsTheCurrentMain() {
        val p = Shots.MemPrefs()
        assertEquals(BuddyWidgetPrefs.MAIN, BuddyWidgetPrefs.choice(p, 7))
        val snap = WidgetLogic.summarize(state(), now, zone).forBuddy(BuddyWidgetPrefs.choice(p, 7))
        assertEquals("main", snap.buddyMon!!.uid)
    }

    @Test fun choiceIsPerWidget() {
        val p = Shots.MemPrefs()
        BuddyWidgetPrefs.set(p, 1, "boxed")
        BuddyWidgetPrefs.set(p, 2, "main")
        assertEquals("boxed", BuddyWidgetPrefs.choice(p, 1))
        assertEquals("main", BuddyWidgetPrefs.choice(p, 2))
        assertEquals(BuddyWidgetPrefs.MAIN, BuddyWidgetPrefs.choice(p, 3))
        BuddyWidgetPrefs.setAll(p, intArrayOf(2, 3), "boxed")
        assertEquals("boxed", BuddyWidgetPrefs.choice(p, 2))
        assertEquals("boxed", BuddyWidgetPrefs.choice(p, 3))
    }

    @Test fun aBoxedPokemonCanBeShown() {
        val snap = WidgetLogic.summarize(state(), now, zone, extraUids = setOf("boxed")).forBuddy("boxed")
        assertEquals("boxed", snap.buddyMon!!.uid)
        assertEquals(16, snap.buddyMon!!.species)
        assertEquals(9, snap.buddyMon!!.level)
        assertEquals("main", snap.main!!.uid) // the team part is untouched
    }

    @Test fun aReleasedPokemonFallsBackToTheMain() {
        val s = state()
        val choice = "boxed"
        s.mons.removeAll { it.uid == choice }
        val snap = WidgetLogic.summarize(s, now, zone, extraUids = setOf(choice)).forBuddy(choice)
        assertNull(snap.buddy)
        assertEquals("main", snap.buddyMon!!.uid)
        // the stored choice stays, harmless
        val p = Shots.MemPrefs()
        BuddyWidgetPrefs.set(p, 1, choice)
        assertEquals(choice, BuddyWidgetPrefs.choice(p, 1))
    }

    @Test fun anUnaskedUidIsNotInTheSnapshot() {
        val snap = WidgetLogic.summarize(state(), now, zone)
        assertEquals(emptyMap<String, WidgetMon>(), snap.picked)
        assertEquals("main", snap.forBuddy("boxed").buddyMon!!.uid)
    }

    @Test fun noTeamAndNothingChosenShowsNothing() {
        val s = state().apply { team.clear() }
        assertNull(WidgetLogic.summarize(s, now, zone).forBuddy(BuddyWidgetPrefs.MAIN).buddyMon)
        // a chosen Pokémon still shows when it exists
        assertNotNull(WidgetLogic.summarize(s, now, zone, extraUids = setOf("boxed")).forBuddy("boxed").buddyMon)
    }

    @Test fun deletedWidgetsAreForgotten() {
        val p = Shots.MemPrefs()
        BuddyWidgetPrefs.setAll(p, intArrayOf(1, 2, 3), "boxed")
        BuddyWidgetPrefs.forget(p, intArrayOf(1, 3))
        assertEquals(BuddyWidgetPrefs.MAIN, BuddyWidgetPrefs.choice(p, 1))
        assertEquals("boxed", BuddyWidgetPrefs.choice(p, 2))
        assertEquals(BuddyWidgetPrefs.MAIN, BuddyWidgetPrefs.choice(p, 3))
        assertEquals(setOf(BuddyWidgetPrefs.key(2)), p.all.keys)
    }

    @Test fun anEmptyStoredChoiceCountsAsMain() {
        val p = Shots.MemPrefs()
        p.edit().putString(BuddyWidgetPrefs.key(1), "").apply()
        assertEquals(BuddyWidgetPrefs.MAIN, BuddyWidgetPrefs.choice(p, 1))
    }
}
