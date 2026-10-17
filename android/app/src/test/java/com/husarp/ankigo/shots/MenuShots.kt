package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Takes an [AppMenu] instead of showing it, and puts its card on a scrim (a renderer has no window for a popup). */
object MenuKit {
    fun capture(open: () -> Unit): AppMenu? {
        var got: AppMenu? = null
        AppMenu.intercept = { got = it }
        try { open() } finally { AppMenu.intercept = null }
        return got
    }

    private fun content(ctx: Context, m: AppMenu): View =
        m.content(ctx)

    /** The menu card alone, centred on a 50 % scrim. */
    fun scene(ctx: Context, m: AppMenu?): View {
        requireNotNull(m) { "no menu was shown" }
        return FrameLayout(ctx).apply {
            setBackgroundColor(Color.argb(128, 0, 0, 0))
            addView(content(ctx, m), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
    }

    /** [screen] with the menu card over it, right-aligned at [gravity] and [marginDp] from that edge (where PopupWindow would put it). */
    fun over(ctx: Context, screen: View, m: AppMenu?, gravity: Int, marginDp: Int): View {
        requireNotNull(m) { "no menu was shown" }
        return FrameLayout(ctx).apply {
            addView(screen, FrameLayout.LayoutParams(-1, -1))
            addView(content(ctx, m), FrameLayout.LayoutParams(-2, -2, gravity or Gravity.END).also {
                if (gravity == Gravity.TOP) it.topMargin = Ui.dp(ctx, marginDp) else it.bottomMargin = Ui.dp(ctx, marginDp)
                it.marginEnd = 0
            })
        }
    }
}

/**
 * The app-styled ⋯ menus (B-01). PNGs: menu-battle(-blocked), menu-team(-box), menu-float (cards on a scrim);
 * menu-screen (battle screen with the top bar / footer Stats button); menu-open-battle, menu-open-team (menu over its screen).
 */
class MenuShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val phoneSplit = listOf(Variants.PHONE, Variants.SPLIT)
    private val st get() = GameRepo.state!!

    private fun battleMenu(ctx: Context): AppMenu? {
        val b = BattleShotKit.bound(ctx)
        return MenuKit.capture { b.call("showMenu", b.root) }
    }

    @Test fun battleCard() {
        paparazzi.shootPage("menu-battle", phoneSplit) { ctx -> MenuKit.scene(ctx, battleMenu(ctx)) }
        paparazzi.shootPage("menu-battle-blocked", phoneSplit, scenario = FakeGame.Scenario.EMPTY) { ctx -> MenuKit.scene(ctx, battleMenu(ctx)) }
    }

    @Test fun teamActionCards() {
        paparazzi.shootPage("menu-team", phoneSplit) { ctx ->
            MenuKit.scene(ctx, MenuKit.capture { MonActions(TeamPage(ctx, NoHost)).menu(st.mon("t3")!!, View(ctx)) })
        }
        paparazzi.shootPage("menu-team-box", phoneSplit) { ctx ->
            st.mon("b1")!!.shiny = true
            MenuKit.scene(ctx, MenuKit.capture { MonActions(TeamPage(ctx, NoHost)).menu(st.mon("b1")!!, View(ctx)) })
        }
    }

    @Test fun floatCard() {
        paparazzi.shootPage("menu-float", phoneSplit) { ctx ->
            val m = AppMenu(View(ctx))
            val fv = FloatBattleView(object : android.content.ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }, {}, {}, {})
            fv.fillMenu(m)
            MenuKit.scene(ctx, m)
        }
    }

    /** The battle screen with its top bar (tall) or the Stats tool button in the footer (short). */
    @Test fun battleScreen() = paparazzi.shootWith("menu-screen", listOf(Size.PHONE, Size.SPLIT)) { ctx -> BattleShotKit.bound(ctx).root }

    @Test fun battleMenuOpen() {
        paparazzi.shootPage("menu-open-battle", phoneSplit) { ctx ->
            val b = BattleShotKit.bound(ctx)
            val m = MenuKit.capture { b.call("showMenu", b.root) }
            val tall = ctx.resources.configuration.screenHeightDp >= 560
            if (tall) MenuKit.over(ctx, b.root, m, Gravity.TOP, 50) else MenuKit.over(ctx, b.root, m, Gravity.BOTTOM, 56)
        }
    }

    @Test fun teamMenuOpen() {
        paparazzi.shootPage("menu-open-team", phoneSplit) { ctx ->
            val page = TeamPage(ctx, NoHost)
            val m = MenuKit.capture { MonActions(page).menu(st.mon("t3")!!, View(ctx)) }
            MenuKit.over(ctx, pageScreen(ctx, page), m, Gravity.TOP, 120)
        }
    }

    /** Disabled row: dimmed, not clickable, reason line kept; enabled rows report their id and close the menu. */
    @Test fun rowsBehave() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val m = AppMenu(View(ctx)).header("T", "sub").item(1, "A", R.drawable.ic_team).item(2, "B", R.drawable.ic_book, enabled = false, sub = "why")
        val picked = ArrayList<Int>()
        MenuKit.capture { m.show { picked += it } }
        val frame = m.content(ctx) as android.view.ViewGroup
        val card = (frame.getChildAt(0) as android.view.ViewGroup).getChildAt(0) as android.view.ViewGroup
        // header, divider, then the rows
        val a = card.getChildAt(2); val b = card.getChildAt(3)
        assertTrue(a.isClickable); assertFalse(b.isClickable)
        a.performClick(); b.performClick()
        assertEquals(listOf(1), picked)
        assertEquals("why", m.subOf(2))
        assertEquals(listOf(Triple(1, "A", true), Triple(2, "B", false)), m.describe())
        assertNull(AppMenu.intercept)
    }
}
