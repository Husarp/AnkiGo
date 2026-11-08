package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/** Phone widths are dp; render at 2x. Exercise production sizing, including overridden app density. */
class R29CWindowShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun reset() = BattleShotKit.reset()

    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    private fun fits(root: View) {
        all(root).filter { it.visibility == View.VISIBLE && it.width > 0 }.forEach { v ->
            val parent = v.parent as? ViewGroup ?: return@forEach
            // Scrollable children can extend vertically, never past a window's right edge.
            assertTrue("${v.javaClass.simpleName} right=${v.right}, parent=${parent.width}", v.right <= parent.width)
            assertTrue("${v.javaClass.simpleName} left=${v.left}", v.left >= 0)
        }
    }

    @Test fun phoneWindowMatrix() {
        for (theme in Theme.entries) for (width in listOf(320, 360, 393, 412))
            for (scale in listOf(1f, 1.1f, 1.2f, 1.3f)) {
                paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme)
                    .copy(screenWidth = width * 2, fontScale = scale))
                val ctx = Shots.files(paparazzi.context)
                ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
                FakeGame.install(ctx)
                val data = GameRepo.data!!
                val state = GameRepo.state!!
                val suffix = "${theme.name.lowercase()}-$width-$scale"
                val species = AppDialogShots.capture {
                    DexDetail.show(TeamPage(ctx, NoHost), data.sp(state.main!!.species))
                }!!
                val scene = AppDialogShots.scene(ctx, species)
                paparazzi.snapshot(scene, "c-species-$suffix")
                fits(scene)
                all(scene).filterIsInstance<TextView>().filter {
                    it.text.toString() in setOf(data.sp(25).name, data.sp(26).name, "Thunder Stone", "Needs Level 3 Bond")
                }.forEach { assertTrue("Evolution names and requirements stay readable", it.layout.lineCount <= 2) }
                val label = all(scene).filterIsInstance<TextView>().single { it.text.toString() == "Species info" }
                for (line in 0 until label.layout.lineCount)
                    assertTrue(label.layout.getLineWidth(line) <= label.width - label.totalPaddingLeft - label.totalPaddingRight + 1)
                species.dismiss()

                val iceEvolution = Evolution(28, EvoKind.ITEM, item = "ice-stone")
                val iceData = TestData.data(listOf(
                    TestData.species(27, "Sandshrew", evolutions = listOf(iceEvolution)),
                    TestData.species(28, "Sandslash")))
                val ice = EvolutionRows.explanationDialog(ctx, iceData,
                    iceEvolution, iceData.sp(27), state.caught, state, state.main) {}
                val iceScene = AppDialogShots.scene(ctx, ice)
                paparazzi.snapshot(iceScene, "c-ice-stone-$suffix")
                fits(iceScene)
                ice.dismiss()

                val use = AppDialogShots.capture {
                    Pickers.pickMon(ctx, "Use on", data, state.mons, { "Ready to use" }) {}
                }!!
                val useScene = AppDialogShots.scene(ctx, use)
                paparazzi.snapshot(useScene, "c-use-on-$suffix")
                fits(useScene)
                use.dismiss()

                val achievement = AchievementDetail.layer(ctx,
                    Achievements.view(state, data).first { it.id == "evolutions" }, state.achSince, ZoneId.of("UTC")) {}
                paparazzi.snapshot(achievement, "c-achievement-$suffix")
                fits(achievement)
                assertFalse(all(achievement).filterIsInstance<TextView>().any {
                    it.text.startsWith("Counted from your save")
                })
            }
    }

    @Test fun densityOverrideCannotGrowCardPastWindow() {
        val ctx = Shots.files(paparazzi.context)
        for (percent in listOf(85, 100, 110, 120, 130)) {
            AppLook.setScale(ctx, percent)
            val wrapped = AppLook.wrap(ctx)
            val dialog = AppDialog.Builder(wrapped).setTitle("Use on").create()
            val dm = wrapped.resources.displayMetrics
            assertEquals(minOf(dm.widthPixels - Ui.dp(wrapped, 32), Ui.dp(wrapped, 560)), dialog.cardWidthPx())
            assertTrue(dialog.cardWidthPx() < dm.widthPixels)
        }
    }

    @Test fun sheetIsHiddenUntilItsFirstLayout() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val layer = AchievementDetail.layer(ctx, Achievements.view(GameRepo.state!!, GameRepo.data!!).first(),
            0, ZoneId.of("UTC")) {} as ViewGroup
        assertEquals(0f, layer.getChildAt(0).alpha)
        assertEquals(0f, layer.getChildAt(1).alpha)
    }

    @Test fun achievementRefreshKeepsExistingSheet() {
        val ctx = Shots.files(paparazzi.context)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        FakeGame.install(ctx)
        val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
        val screen = pageScreen(ctx, page)
        page.openAchievement("evolutions")
        val render = StatsPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }
        render.invoke(page)
        paparazzi.snapshot(screen, "c-achievement-before-return")
        val detail = all(screen).filterIsInstance<AchMedal>().last()
        page.onAppReturned()
        render.invoke(page)
        assertTrue(all(screen).any { it === detail })
        paparazzi.snapshot(screen, "c-achievement-after-return")
    }

    @Test fun returningToPageDoesNotReselectTab() {
        val ctx = Shots.files(paparazzi.context)
        var reselected = false
        val page = object : GamePage(ctx, NoHost) {
            override val tab: BottomNav.Tab? = null
            override fun buildBar(): View = View(this)
            override fun buildBody(): View = View(this)
            override fun render() {}
            override fun onTabReselected() { reselected = true }
        }
        page.onAppReturned()
        assertFalse(reselected)
    }
}
