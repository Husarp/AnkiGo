package com.husarp.ankigo.shots

import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Duplicates
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.AppDialog
import com.husarp.ankigo.ui.ReleaseDuplicates
import com.husarp.ankigo.ui.ReleaseDuplicatesText
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * TICKET R10-I (B21): "Release duplicates" confirmation. PNGs: r10reldups-<normal|long|single>-<phone|float>-<theme> (the dialog card on its scrim).
 * Flow checks (no window): nothing to release = a toast and no dialog; "Release N" runs ONE game action (page.act = one GameRepo.run = one
 * save scheduled; the save timer coalesces, so there is one write) and toasts "Released N Pokémon"; a state that changed while the dialog
 * was open releases only what was confirmed and is still planned (nothing left = no action, no save scheduled).
 */
class R10ReleaseDupsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val sizes = listOf(Size.PHONE, Size.FLOAT)

    private val st get() = GameRepo.state!!
    private val data get() = GameRepo.data!!

    /** More low copies of [species] in the box (made-up Lv/uids); [edit] marks one of them. */
    private fun addCopies(species: Int, vararg levels: Int, edit: (com.husarp.ankigo.game.Mon) -> Unit = {}) {
        for ((i, lv) in levels.withIndex()) {
            val m = TestData.mon(data, species, lv, listOf("tackle"), uid = "x$species-$i")
            m.caughtAt = 1_760_000_000_000L + i
            if (i == 0) edit(m)
            st.mons += m; st.caught += species
        }
    }

    /** GameRepo's "a save is scheduled" flag (private; set by every action through GameRepo.run, cleared when the timer fires). */
    private var saveQueued: Boolean
        get() = GameRepo::class.java.getDeclaredField("saveQueued").apply { isAccessible = true }.getBoolean(GameRepo)
        set(v) = GameRepo::class.java.getDeclaredField("saveQueued").apply { isAccessible = true }.setBoolean(GameRepo, v)

    private fun dialog(ctx: android.content.Context) = ReleaseDuplicates.dialog(ctx, Duplicates.releasePlan(data, st)) {}

    /** FakeGame's box already holds a second Birdie, Ratty and Duckling; add protected and plain extras so every line of the card shows. */
    @Test fun normal() = paparazzi.shoot("r10reldups-normal", sizes) { ctx ->
        addCopies(16, 5, 6, 7) { it.favourite = true } // the Lv 5 one is a favourite (kept); Lv 6 and 7 go
        addCopies(19, 4) { it.heldItem = "everstone" }
        addCopies(54, 3) { it.shiny = true }
        val plan = Duplicates.releasePlan(data, st)
        assertEquals("Release 5", ReleaseDuplicatesText.button(plan))
        assertEquals(3, plan.keptCount)
        AppDialogShots.scene(ctx, dialog(ctx))
    }

    /** Ten species: the list is cut after six with "and 4 more". */
    @Test fun longList() = paparazzi.shoot("r10reldups-long", sizes) { ctx ->
        for (sp in listOf(129, 63, 64, 172, 134, 135, 700)) addCopies(sp, 30, 4, 5)
        val plan = Duplicates.releasePlan(data, st)
        assertEquals(10, plan.groups.size)
        assertTrue(ReleaseDuplicatesText.species(plan).endsWith(", and 4 more"))
        AppDialogShots.scene(ctx, dialog(ctx))
    }

    /** One Pokémon to release, one protected: the singular wording. */
    @Test fun single() = paparazzi.shoot("r10reldups-single", sizes) { ctx ->
        val first = Duplicates.releasePlan(data, st).release
        st.mons.removeAll { it.uid in first.drop(1) } // leave one duplicate to release
        addCopies(16, 3) { it.favourite = true } // and one protected copy that stays
        val plan = Duplicates.releasePlan(data, st)
        assertEquals(1, plan.count)
        assertEquals(1, plan.keptCount)
        AppDialogShots.scene(ctx, dialog(ctx))
    }

    // ---- the flow ----

    @Test fun nothingToReleaseIsAToastAndNoDialog() {
        paparazzi.shoot("r10reldups-flow-none", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            st.mons.removeAll { it.uid in Duplicates.releasePlan(data, st).release.toSet() }
            assertTrue(Duplicates.releasePlan(data, st).isEmpty)
            val before = st.mons.size
            Ui.lastToast = null
            val dlg = AppDialogShots.capture { ReleaseDuplicates.start(TeamPage(ctx, NoHost)) }
            assertNull("no dialog", dlg)
            assertEquals("No duplicates to release", Ui.lastToast)
            assertEquals(before, st.mons.size)
            View(ctx)
        }
    }

    @Test fun confirmRunsOneActionAndToastsTheNumber() {
        paparazzi.shoot("r10reldups-flow-go", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            val page = TeamPage(ctx, NoHost)
            val plan = Duplicates.releasePlan(data, st)
            assertEquals(3, plan.count)
            val before = st.mons.size
            saveQueued = false
            try {
                Ui.lastToast = null
                val dlg = AppDialogShots.capture { ReleaseDuplicates.start(page) }
                assertNotNull("one dialog", dlg)
                assertEquals("showing the dialog changes nothing", before, st.mons.size)
                assertFalse("no save for showing it", saveQueued)
                assertTrue(dlg!!.button(AppDialog.BUTTON_NEGATIVE) != null)
                dlg.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
                assertTrue("the action went through GameRepo.run: a save is scheduled", saveQueued)
                assertEquals(before - 3, st.mons.size)
                assertEquals("Released 3 Pokémon", Ui.lastToast)
                assertTrue(Duplicates.releasePlan(data, st).isEmpty)
            } finally { saveQueued = false }
            View(ctx)
        }
    }

    @Test fun cancelChangesNothing() {
        paparazzi.shoot("r10reldups-flow-cancel", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            val before = st.mons.map { it.uid }
            val dlg = AppDialogShots.capture { ReleaseDuplicates.start(TeamPage(ctx, NoHost)) }!!
            dlg.button(AppDialog.BUTTON_NEGATIVE)!!.performClick()
            assertEquals(before, st.mons.map { it.uid })
            View(ctx)
        }
    }

    @Test fun copiesThatArrivedWhileTheDialogWasOpenAreNotReleased() {
        paparazzi.shoot("r10reldups-flow-grew", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            val page = TeamPage(ctx, NoHost)
            val dlg = AppDialogShots.capture { ReleaseDuplicates.start(page) }!!
            val shown = Duplicates.releasePlan(data, st).release.toSet()
            assertEquals(3, shown.size) // the dialog says 3 ...
            addCopies(92, 2) // ... then a lower Spooky (the team's one is the keeper) and another Birdie are caught
            addCopies(16, 1)
            val fresh = Duplicates.releasePlan(data, st).release.toSet()
            assertEquals(5, fresh.size)
            val newOnes = fresh - shown
            Ui.lastToast = null
            dlg.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
            assertEquals("Released 3 Pokémon", Ui.lastToast)
            assertTrue("the new ones stay", newOnes.all { st.mon(it) != null })
            assertTrue("the confirmed ones went", shown.none { st.mon(it) != null })
            View(ctx)
        }
    }

    @Test fun aHigherLevelCopyWhileTheDialogWasOpenDoesNotReleaseTheFormerKeeper() {
        paparazzi.shoot("r10reldups-flow-higher", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            val page = TeamPage(ctx, NoHost)
            val dlg = AppDialogShots.capture { ReleaseDuplicates.start(page) }!!
            val shown = Duplicates.releasePlan(data, st).release.toSet()
            val species = st.mon(shown.first())!!.species
            val keeper = st.mons.filter { it.species == species && it.uid !in shown }.maxByOrNull { it.level }!!
            addCopies(species, 99) // a better copy: the keeper would now be released by a fresh plan
            assertTrue(keeper.uid in Duplicates.releasePlan(data, st).release)
            Ui.lastToast = null
            dlg.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
            assertEquals("Released 3 Pokémon", Ui.lastToast)
            assertNotNull("the former keeper stays", st.mon(keeper.uid))
            assertTrue(shown.none { st.mon(it) != null })
            View(ctx)
        }
    }

    @Test fun aConfirmedPokemonThatBecameAFavouriteStays() {
        paparazzi.shoot("r10reldups-flow-fav", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            val page = TeamPage(ctx, NoHost)
            val dlg = AppDialogShots.capture { ReleaseDuplicates.start(page) }!!
            val shown = Duplicates.releasePlan(data, st).release
            st.mon(shown.first())!!.favourite = true
            Ui.lastToast = null
            dlg.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
            assertEquals("Released 2 of 3 — your Pokémon changed", Ui.lastToast)
            assertNotNull(st.mon(shown.first()))
            View(ctx)
        }
    }

    @Test fun aStateThatChangedWhileTheDialogWasOpenReleasesNothingAndSavesNothing() {
        paparazzi.shoot("r10reldups-flow-replan", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            val page = TeamPage(ctx, NoHost)
            val dlg = AppDialogShots.capture { ReleaseDuplicates.start(page) }!!
            val shown = Duplicates.releasePlan(data, st)
            // while it is open: the three planned Pokémon become favourites, and a new low copy appears (releasable, but never confirmed)
            for (uid in shown.release) st.mon(uid)!!.favourite = true
            addCopies(16, 1)
            assertFalse(Duplicates.releasePlan(data, st).isEmpty)
            val before = st.mons.map { it.uid }
            saveQueued = false
            try {
                Ui.lastToast = null
                dlg.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
                assertFalse("no action, no save scheduled", saveQueued)
                assertEquals("Nothing released — your Pokémon changed", Ui.lastToast)
                assertEquals(before, st.mons.map { it.uid })
                assertFalse(before.isEmpty())
            } finally { saveQueued = false }
            View(ctx)
        }
    }
}
