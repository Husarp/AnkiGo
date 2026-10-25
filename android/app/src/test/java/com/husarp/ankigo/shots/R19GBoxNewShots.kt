package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.GamePage
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.TileMarks
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** PLAN 280 / R19: real Box first/second openings, phone and floating window, light and dark. */
class R19GBoxNewShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val sizes = listOf(Variants.PHONE, Variant("float", Size.FLOAT.wDp, Size.FLOAT.hDp))
    private fun render(page: TeamPage) = GamePage::class.java.getDeclaredMethod("render").apply {
        isAccessible = true
    }.invoke(page)

    private fun visit(second: Boolean) = paparazzi.shootPage(
        if (second) "r19g-box-second" else "r19g-box-first", sizes,
    ) { ctx ->
        val st = GameRepo.state!!
        st.viewedMons.clear()
        st.mons.forEach { it.caughtAt = System.currentTimeMillis() }
        val page = TeamPage(ctx, NoHost, if (second) TeamSection.BOX else TeamSection.TEAM)
        val root = pageScreen(ctx, page)
        render(page)
        if (!second) {
            assertFalse(ctx.getSharedPreferences("ankigo_ui", 0).contains("box_seen_owned"))
            page.showSection(TeamSection.BOX); render(page)
        }
        assertTrue(st.mons.all { TileMarks.isNew(st, it) })
        render(page) // ordinary redraws must not consume the visit
        assertTrue(st.mons.all { TileMarks.isNew(st, it) })
        if (second) {
            page.onHide(); render(page) // a queued redraw of a hidden page is not an opening
            assertTrue(st.mons.all { TileMarks.isNew(st, it) })
            page.onShow(); render(page)
            assertTrue(st.mons.none { TileMarks.isNew(st, it) })
            page.showSection(TeamSection.TEAM); render(page)
            page.showSection(TeamSection.BOX); render(page)
            assertTrue(st.mons.none { TileMarks.isNew(st, it) })
            page.onHide(); page.onShow(); render(page)
            assertTrue(st.mons.none { TileMarks.isNew(st, it) })
        }
        root
    }

    @Test fun firstVisit() = visit(false)
    @Test fun secondVisit() = visit(true)

    @Test fun sectionSwitchStartsNextVisit() = paparazzi.shootPage("r19g-box-switch", listOf(Variants.PHONE)) { ctx ->
        val st = GameRepo.state!!
        st.viewedMons.clear()
        st.mons.forEach { it.caughtAt = System.currentTimeMillis() }
        val page = TeamPage(ctx, NoHost, TeamSection.BOX)
        val root = pageScreen(ctx, page)
        render(page)
        assertTrue(st.mons.all { TileMarks.isNew(st, it) })
        page.showSection(TeamSection.TEAM); render(page)
        page.showSection(TeamSection.BOX); render(page)
        assertTrue(st.mons.none { TileMarks.isNew(st, it) })
        root
    }
}
