package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.shownName
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.TeamPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * PLAN 190B / X6 / X1 / X2 (R16-2-G): the Team rows get one ⋯ button and no hint line. PNGs: r16g-<state>-<w360|phone>-<light|dark>.
 */
class R16GTeamRowShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val st get() = GameRepo.state!!
    private val w360 = Variant("w360", 360, 800)
    private val both = listOf(w360, Variants.PHONE)

    /** The page renders on the next message-loop turn; the checks below need the rows now. */
    private fun draw(page: TeamPage) {
        TeamPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
    }

    private fun views(v: View, out: MutableList<View> = mutableListOf()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out)
        return out
    }

    private fun more(root: View) = views(root).filter { it.contentDescription?.startsWith("Options for ") == true }
    private fun texts(root: View) = views(root).filterIsInstance<TextView>().map { it.text.toString() }

    @Test fun normal() = paparazzi.shootPage("r16g-normal", both) { ctx ->
        val page = TeamPage(ctx, NoHost)
        val root = pageScreen(ctx, page).also { draw(page) }
        assertEquals(st.team.size, more(root).size)
        assertTrue(texts(root).none { it.contains("hold for actions") || it.contains("Tap for details") })
        root
    }

    @Test fun editMode() = paparazzi.shootPage("r16g-edit", both) { ctx ->
        val page = TeamPage(ctx, NoHost).also { it.preset(edit = true) }
        val root = pageScreen(ctx, page).also { draw(page) }
        assertTrue("no ⋯ while editing", more(root).isEmpty())
        assertTrue(texts(root).any { it.startsWith("Use the arrows") })
        root
    }

    @Test fun comparePicking() = paparazzi.shootPage("r16g-pick", both) { ctx ->
        val page = TeamPage(ctx, NoHost).also { it.preset(pick = listOf(ComparePick.Owned(st.team[0]))) }
        val root = pageScreen(ctx, page).also { draw(page) }
        assertTrue("no ⋯ while picking", more(root).isEmpty())
        assertTrue(texts(root).any { it.startsWith("Tap a Pokémon to tick it") })
        root
    }

    @Test fun queuedSwitch() = paparazzi.shootPage("r16g-queued", both) { ctx ->
        st.pendingMain = st.team[1]
        val root = TeamPage(ctx, NoHost).let { p -> pageScreen(ctx, p).also { draw(p) } }
        assertTrue(texts(root).any { it.contains("AFTER THIS CARD", ignoreCase = true) || it.contains("after this card", ignoreCase = true) })
        root
    }

    /** The ⋯ button opens exactly the menu a long-press on the row opens. */
    @Test fun moreOpensTheLongPressMenu() = paparazzi.shootPage("r16g-menu", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
        val page = TeamPage(ctx, NoHost)
        val root = pageScreen(ctx, page).also { draw(page) }
        val buttons = more(root)
        assertEquals(st.team.size, buttons.size)
        for ((i, uid) in st.team.withIndex()) {
            val mon = st.mon(uid)!!
            val b = buttons[i]
            assertEquals("Options for ${mon.shownName}", b.contentDescription)
            assertTrue(b.isClickable)
            val viaButton = MenuKit.capture { b.performClick() }
            val viaLong = MenuKit.capture { (b.parent as View).performLongClick() }
            val direct = MenuKit.capture { MonActions(page).menu(mon, View(ctx)) }
            assertNotNull(viaButton); assertNotNull(viaLong); assertNotNull(direct)
            assertEquals(direct!!.describe(), viaButton!!.describe())
            assertEquals(direct.describe(), viaLong!!.describe())
        }
        MenuKit.scene(ctx, MenuKit.capture { buttons[1].performClick() })
    }
}
