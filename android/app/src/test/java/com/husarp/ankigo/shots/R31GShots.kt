package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.VectorDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R31GShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun tap(d: AppDialog, label: String) = all(d.content()).filterIsInstance<TextView>().first { it.text.toString() == label }.performClick()
    private fun back(d: AppDialog) = all(d.content()).single { it.contentDescription == "Back to previous screen" }.performClick()
    private fun page(): TeamPage {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        return TeamPage(ctx, NoHost).also { AppPrefs.setDetailOpen(it, "evolution", true) }
    }
    private fun show(open: () -> Unit) = DialogShots.capture(open)!!
    private fun noNewWindow(open: () -> Unit) {
        var opened = 0
        Pickers.interceptApp = { opened++ }
        try { open(); assertEquals("Reuse the existing Pokémon window", 0, opened) }
        finally { Pickers.interceptApp = null }
    }
    private fun shot(name: String, build: (Context) -> View) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            paparazzi.snapshot(build(ctx), "g-$name-${theme.name.lowercase()}")
        }
    }

    @Test fun ownedEvolutionLinkAndMyPokemonShareOneWindowAndBackHistory() {
        val page = page()
        val root = show { MonDetail.show(page, page.data, page.state.main!!) }
        try {
            val original = root.content()
            val raichu = all(original).single { it.contentDescription == page.data.sp(26).name }
            noNewWindow { raichu.performClick() }
            val species = root.content()
            assertNotSame(original, species)
            assertFalse(WindowStack.stacked(page))
            noNewWindow { tap(root, "My Pokémon") }
            assertNotSame(species, root.content())
            back(root)
            assertSame(species, root.content())
            root.goBack() // the same handler invoked by the platform Dialog's system Back
            assertSame(original, root.content())
            noNewWindow { raichu.performClick() }
            tap(root, "Close")
            assertNull(WindowStack.pokemonWindow(page))
            val fresh = show { DexDetail.show(page, page.data.sp(26)) }
            try { back(fresh); assertNull(WindowStack.pokemonWindow(page)) }
            finally { fresh.dismiss() }
        } finally { root.dismiss() }
    }

    @Test fun multipleSpeciesEvolutionLinksPopOnePageAtATime() {
        val page = page()
        val root = show { DexDetail.show(page, page.data.sp(1)) }
        try {
            val first = root.content()
            noNewWindow { all(first).single { it.contentDescription == page.data.sp(2).name }.performClick() }
            val second = root.content()
            noNewWindow { all(second).single { it.contentDescription == page.data.sp(3).name }.performClick() }
            assertFalse(WindowStack.stacked(page))
            back(root); assertSame(second, root.content())
            back(root); assertSame(first, root.content())
            back(root); assertNull(WindowStack.pokemonWindow(page))
        } finally { root.dismiss() }
    }

    @Test fun platformSystemBackUsesTheSameHistoryAsTheArrow() {
        val page = page()
        val root = show { DexDetail.show(page, page.data.sp(1)) }
        try {
            val original = root.content()
            root.window // create the actual platform Dialog without showing a renderer window
            noNewWindow { DexDetail.show(page, page.data.sp(2)) }
            val platform = AppDialog::class.java.getDeclaredField("made").apply { isAccessible = true }.get(root) as android.app.Dialog
            @Suppress("DEPRECATION")
            platform.onBackPressed()
            assertSame(original, root.content())
            assertSame(root, WindowStack.pokemonWindow(page))
            @Suppress("DEPRECATION")
            platform.onBackPressed()
            assertNull(WindowStack.pokemonWindow(page))
        } finally { root.dismiss() }
    }

    @Test fun linksThroughAnEvolutionExplanationUncoverAndReuseThePokemonWindow() {
        val page = page()
        val root = show { DexDetail.show(page, page.data.sp(1)) }
        try {
            val original = root.content()
            val explanation = show { all(original).single { it.contentDescription == "Explain Level 16" }.performClick() }
            assertTrue(WindowStack.stacked(page))
            noNewWindow { DexDetail.show(page, page.data.sp(2)) }
            assertFalse(WindowStack.stacked(page))
            back(root); assertSame(original, root.content())
            // A stale explanation cannot remain in the stack after following a Pokémon link.
            explanation.dismiss()
            assertSame(root, WindowStack.pokemonWindow(page))
        } finally { root.dismiss() }
    }

    @Test fun itemEvolutionExplanationAlsoKeepsThePokemonHistory() {
        val page = page()
        val root = show { MonDetail.show(page, page.data, page.state.main!!) }
        try {
            val original = root.content()
            show { all(original).single { it.contentDescription?.startsWith("Explain Use Thunder Stone") == true }.performClick() }
            assertTrue(WindowStack.stacked(page))
            noNewWindow { DexDetail.show(page, page.data.sp(26)) }
            assertFalse(WindowStack.stacked(page))
            back(root); assertSame(original, root.content())
        } finally { root.dismiss() }
    }

    @Test fun compareDetailsUseTheSameNavigationForOwnedAndSpeciesColumns() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val page = ComparePage(ctx, NoHost, listOf(ComparePick.Owned("t1"), ComparePick.Dex(150)))
        pageScreen(ctx, page); page.render()
        fun details(index: Int) {
            val menu = MenuKit.capture {
                all(page.root).filter { it.contentDescription?.contains("Tap for options.") == true }[index].performClick()
            }!!
            menu.pick(menu.describe().single { it.second == "Details" }.first)
        }
        val root = show { details(0) }
        try {
            val original = root.content()
            noNewWindow { details(1) }
            assertFalse(WindowStack.stacked(page))
            back(root); assertSame(original, root.content())
        } finally { root.dismiss() }
    }

    @Test fun itemSheetPokemonLinkCloseReturnsToTheItem() {
        val page = page()
        var closed = 0
        val item = ItemWindow(page) { closed++ }
        ItemWindow.interceptShow = { }
        try {
            item.update(ItemCard.Spec("thunder-stone")); item.show()
            val root = show { all(item.view).single { it.contentDescription == "${page.data.sp(25).name}. Opens Evolution details" }.performClick() }
            val species = root.content()
            noNewWindow { tap(root, "My Pokémon") }
            back(root); assertSame(species, root.content())
            tap(root, "Close")
            assertTrue(item.isShowing); assertEquals(0, closed)
            WindowStack.closeCurrent(page)
            assertFalse(item.isShowing); assertEquals(1, closed)
        } finally { ItemWindow.interceptShow = null; item.dismiss() }
    }

    @Test fun itemInfoDialogIsAlsoAnOriginForANewPokemonWindow() {
        val page = page()
        val underlying = show { DexDetail.show(page, page.data.sp(1)) }
        val info = show { AppDialog.Builder(page).setInfoHeader("Thunder Stone", "thunder-stone").setMessage("Item info").showCard() }
        val root = show { DexDetail.show(page, page.data.sp(25)) }
        try {
            assertNotSame(underlying, root)
            tap(root, "Close")
            assertNull(WindowStack.pokemonWindow(page)) // item info is still above the underlying Pokémon
            assertTrue(WindowStack.stacked(page))
            WindowStack.closeCurrent(page)
            assertSame(underlying, WindowStack.pokemonWindow(page))
        } finally { root.dismiss(); info.dismiss(); underlying.dismiss() }
    }

    @Test fun refreshAfterEvolutionKeepsHistoryWithoutAddingAnExtraPage() {
        val page = page()
        val mon = page.state.main!!
        val root = show { DexDetail.show(page, page.data.sp(1)) }
        try {
            val original = root.content()
            noNewWindow { MonDetail.show(page, page.data, mon) }
            noNewWindow { MonDetail.show(page, page.data, mon, replaceDetails = true) }
            back(root); assertSame(original, root.content())
            back(root); assertNull(WindowStack.pokemonWindow(page))
        } finally { root.dismiss() }
    }

    @Test fun separatePageContextsNeverSharePokemonHistory() {
        val first = page()
        val second = TeamPage(first.baseContext, NoHost)
        val a = show { DexDetail.show(first, first.data.sp(25)) }
        val b = show { DexDetail.show(second, second.data.sp(1)) }
        try {
            assertSame(a, WindowStack.pokemonWindow(first))
            assertSame(b, WindowStack.pokemonWindow(second))
            tap(b, "Close")
            assertSame(a, WindowStack.pokemonWindow(first))
            assertNull(WindowStack.pokemonWindow(second))
        } finally { a.dismiss(); b.dismiss() }
    }

    @Test fun everyDialogCloseControlClosesOnlyItsOwnWindow() {
        val page = page()
        val parent = show { MonDetail.show(page, page.data, page.state.main!!) }
        try {
            for (style in listOf("footer", "top", "info", "button")) {
                val child = show {
                    AppDialog.Builder(page).setTitle("Child").apply {
                        when (style) {
                            "footer" -> setDetailsFooter()
                            "top" -> setCloseAtTop()
                            "info" -> setInfoHeader("Item", "thunder-stone")
                            else -> setNegativeButton("Close", null)
                        }
                    }.showCard()
                }
                assertTrue(WindowStack.stacked(page))
                if (style == "info") all(child.content()).single { it.contentDescription == "Close" }.performClick()
                else tap(child, "Close")
                assertFalse(WindowStack.stacked(page))
                assertSame(parent, WindowStack.pokemonWindow(page))
            }
            tap(parent, "Close")
            assertNull(WindowStack.pokemonWindow(page))
        } finally { parent.dismiss() }
    }

    @Test fun plainHoldActionsStartFillingOnTouchAndCancelWithoutConfirming() {
        val ctx = Shots.files(paparazzi.context)
        for (action in listOf("Evolve", "Refresh", "Buy", "Release", "Teach", "Use", "Revive", "Throw")) {
            var confirmed = 0
            val d = show { Pickers.hold(ctx, action, "This can't be undone.", action) { confirmed++ } }
            try {
                val button = d.button(AppDialog.BUTTON_POSITIVE)!!
                assertEquals(action, button.text.toString())
                assertEquals(action, button.contentDescription)
                button.layout(0, 0, 240, 80)
                fun touch(event: Int) = MotionEvent.obtain(0, 0, event, 10f, 10f, 0).let {
                    button.dispatchTouchEvent(it); it.recycle()
                }
                touch(MotionEvent.ACTION_DOWN)
                assertTrue("$action starts filling on touch", button.background.level > 0)
                assertEquals(0, confirmed)
                val filling = button.background.level
                touch(MotionEvent.ACTION_CANCEL)
                assertEquals("$action retracts from its current fill", filling, button.background.level)
                assertEquals(0, confirmed)
                button.performClick(); assertEquals(0, confirmed)
                assertFalse(all(d.content()).any { "one second" in (it.contentDescription?.toString().orEmpty()) })
                button.performLongClick(); assertEquals(1, confirmed)
            } finally { d.dismiss() }
        }
    }

    @Test fun pokemonHistoryLightAndDark() = shot("529-pokemon-history") { ctx ->
        val page = TeamPage(ctx, NoHost)
        AppPrefs.setDetailOpen(page, "evolution", true)
        val d = show { MonDetail.show(page, page.data, page.state.main!!) }
        noNewWindow { DexDetail.show(page, page.data.sp(26)) }
        DialogShots.scene(ctx, d).also { d.dismiss() }
    }

    @Test fun plainHoldLabelsLightAndDark() = shot("523-hold-labels") { ctx ->
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 20), Ui.dp(ctx, 20), Ui.dp(ctx, 20))
            setBackgroundColor(Ui.c(ctx, R.color.ag_card))
            for (label in listOf("Evolve", "Refresh", "Buy", "Release", "Teach", "Use", "Revive", "Throw")) {
                addView(TextView(ctx).apply {
                    text = label; gravity = android.view.Gravity.CENTER
                    HoldToConfirm.bind(this, label.lowercase()) {}
                    background.level = 4000
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(ctx, 8) })
            }
        }
    }

    @Test fun achievementFlameIsACutOutFrameAndUsesAchievementColours() = shot("512-flame-medals") { ctx ->
        assertEquals(R.drawable.ic_ach_streak_flame, AchStyle.icon("flame"))
        val drawable = ctx.getDrawable(AchStyle.icon("flame"))!!.mutate()
        assertTrue(drawable is VectorDrawable)
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 128, 128); drawable.draw(Canvas(bitmap))
        assertEquals("The frame is hollow", 0, Color.alpha(bitmap.getPixel(68, 35)))
        assertEquals("The inner lick is hollow", 0, Color.alpha(bitmap.getPixel(69, 111)))
        val count = (0 until 128).sumOf { x -> (0 until 128).count { y -> Color.alpha(bitmap.getPixel(x, y)) > 0 } }
        assertTrue("An outline, with a transparent interior", count in 2000..6500)
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, R.color.ag_card))
            addView(StreakFlame(ctx), LinearLayout.LayoutParams(Ui.dp(ctx, 56), Ui.dp(ctx, 56)))
            for (tier in -1..4) addView(AchMedal(ctx, 72).apply { set(AchStyle.icon("flame"), tier) })
        }
    }
}
