package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.LookPicker
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.BoxFilter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R3-V8 (pick 8B): choose which look an owned Pokémon shows. PNGs (phone + split, light + dark): r3v8-menu (options menu of a
 * Pokémon with a line), r3v8-menu-single (same menu, a Pokémon with one look: no entry), r3v8-popup (+ -reset: a look is set,
 * "Use its own look" shows), r3v8-box (Box tiles, two with another look), r3v8-battle (player sprite with a custom look),
 * r3v8-detail (details header). The fake pictures are plain blobs coloured by type, so a changed look shows as a changed colour.
 */
class R3V8Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val both = listOf(Variants.PHONE, Variants.SPLIT)
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private fun labels(ctx: Context, uid: String) =
        MenuKit.capture { MonActions(TeamPage(ctx, NoHost)).menu(st.mon(uid)!!, View(ctx)) }!!.describe().map { it.second }

    /** b10 = Fluffwave (water) shows Fluffy (normal), b11 = Fluffbolt (electric) shows Fluffy too. */
    private fun withLooks() {
        st.mon("b10")!!.look = 133
        st.mon("b11")!!.look = 133
    }

    @Test fun menuWithEntry() {
        paparazzi.shootPage("r3v8-menu", both) { ctx ->
            val m = MenuKit.capture { MonActions(TeamPage(ctx, NoHost)).menu(st.mon("t3")!!, View(ctx)) }
            assertTrue(m!!.describe().any { it.second == "Change look…" && it.third })
            assertEquals("Pick a picture from its line", m.subOf(m.describe().first { it.second == "Change look…" }.first))
            MenuKit.scene(ctx, m)
        }
    }

    @Test fun menuHidesEntryForSingleLook() {
        paparazzi.shootPage("r3v8-menu-single", both) { ctx ->
            assertFalse("Birdie has one look", labels(ctx, "b1").contains("Change look…"))     // 16, no line
            assertFalse("Leafling is the base form", labels(ctx, "b8").contains("Change look…")) // 1
            assertTrue(labels(ctx, "b9").contains("Change look…"))                              // 26 <- 25 <- 172
            MenuKit.scene(ctx, MenuKit.capture { MonActions(TeamPage(ctx, NoHost)).menu(st.mon("b1")!!, View(ctx)) })
        }
    }

    private fun popup(name: String, setLook: Boolean) {
        paparazzi.shootPage(name, both) { ctx ->
            if (setLook) st.mon("t3")!!.look = 1
            val d = DialogShots.capture { LookPicker.show(ctx, gd, st.mon("t3")!!, gd.species.keys) { } } ?: error("no dialog")
            DialogShots.scene(ctx, d, widthDp = 330)
        }
    }

    @Test fun popupCurrent() = popup("r3v8-popup", false)
    @Test fun popupWithLook() = popup("r3v8-popup-reset", true)

    @Test fun boxTiles() {
        paparazzi.shootPage("r3v8-box", both) { ctx ->
            withLooks()
            val p = TeamPage(ctx, NoHost)
            p.preset(TeamSection.BOX, filter = BoxFilter.ALL, group = true)
            pageScreen(ctx, p)
        }
    }

    @Test fun battlePlayer() {
        paparazzi.shootWith("r3v8-battle", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
            st.mon("t1")!!.apply { species = 134; name = "Fluffwave"; look = 133 }
            BattleShotKit.bound(ctx).root
        }
    }

    @Test fun details() {
        paparazzi.shootPage("r3v8-detail", both) { ctx ->
            withLooks()
            val d = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), gd, st.mon("b10")!!) } ?: error("no dialog")
            DialogShots.scene(ctx, d)
        }
    }

    @Test fun pickIsSavedThroughTheGame() {
        paparazzi.shootPage("r3v8-unused", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val g = GameRepo.game!!
            assertTrue(g.setLook("t3", 1).ok)
            assertEquals(1, st.mon("t3")!!.look)
            assertTrue(g.setLook("t3", null).ok)
            assertNotNull(st.mon("t3"))
            assertEquals(null, st.mon("t3")!!.look)
            View(ctx)
        }
    }
}
