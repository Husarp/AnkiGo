package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

class R26MDetailsTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun tap(v: View, text: String) = all(v).filterIsInstance<TextView>().first { it.text.toString() == text }.performClick()

    @Test fun backAndCloseEachRemoveOnlyTheCurrentWindow() {
        val ctx = Shots.files(paparazzi.context)
        val other = android.content.ContextWrapper(ctx)
        fun card(c: android.content.Context) = AppDialog.Builder(c).setTitle("Details").setMessage("Nested details").setDetailsFooter().create()
        val parent = card(ctx); val child = card(ctx); val unrelated = card(other)
        parent.joinStack(); child.joinStack(); unrelated.joinStack()
        var parentClosed = 0; var childClosed = 0; var otherClosed = 0
        // StackWindow spies test the same context-scoped stack without requiring platform windows.
        class Spy(override val stackContext: android.content.Context, val close: () -> Unit): StackWindow {
            override fun closeFromStack() { close(); WindowStack.remove(this) }
            override fun stackChanged(stacked: Boolean) {}
        }
        WindowStack.closeAll(ctx); unrelated.dismiss()
        val p = Spy(ctx) { parentClosed++ }; val q = Spy(ctx) { childClosed++ }; val r = Spy(other) { otherClosed++ }
        WindowStack.add(p); WindowStack.add(q); WindowStack.add(r)
        val top = card(ctx); top.joinStack()
        assertFalse(texts(top.content()).contains("Close all"))
        all(top.content()).first { it.contentDescription == "Back to previous screen" }.performClick()
        assertTrue(WindowStack.stacked(ctx))
        assertEquals(0, parentClosed); assertEquals(0, childClosed)
        val systemBack = card(ctx); systemBack.joinStack(); systemBack.cancel()
        assertTrue(WindowStack.stacked(ctx))
        assertEquals(0, parentClosed); assertEquals(0, childClosed)
        val next = card(ctx); next.joinStack()
        tap(next.content(), "Close")
        assertEquals(0, parentClosed); assertEquals(0, childClosed); assertEquals(0, otherClosed)
        assertTrue(WindowStack.stacked(ctx))
        WindowStack.closeCurrent(ctx)
        assertEquals(0, parentClosed); assertEquals(1, childClosed)
        assertFalse(WindowStack.stacked(ctx))
        WindowStack.closeCurrent(ctx)
        assertEquals(1, parentClosed)
        WindowStack.closeAll(other)
        assertEquals(1, otherClosed)
    }

    @Test fun detailsLightAndDark() {
        paparazzi.shoot("r26m-details", listOf(Size.SMALL), listOf(Theme.LIGHT, Theme.DARK)) { ctx ->
            FakeGame.scenario = FakeGame.Scenario.MID_GAME; FakeGame.install(ctx)
            DetailSections.storeOf = { object : DetailOpenStore {
                override fun get(key: String): Boolean? = true
                override fun set(key: String, open: Boolean) {}
            } }
            try {
                val page = TeamPage(ctx, NoHost)
                val dialog = AppDialogShots.capture { MonDetail.show(page, GameRepo.data!!, GameRepo.state!!.main!!) }!!
                val content = dialog.content()
                assertTrue(texts(content).any { it.startsWith("Level ") })
                assertFalse(texts(content).any { it.contains("tick = normal") })
                val options = all(content).filterIsInstance<TextView>().first { it.text.toString() == "Options" }
                val close = all(content).filterIsInstance<TextView>().first { it.text.toString() == "Close" }
                assertSame(options.parent, close.parent)
                AppDialogShots.scene(ctx, dialog)
            } finally {
                DetailSections.storeOf = { c -> object : DetailOpenStore {
                    override fun get(key: String) = AppPrefs.detailOpen(c, key)
                    override fun set(key: String, open: Boolean) = AppPrefs.setDetailOpen(c, key, open)
                } }
            }
        }
    }

    @Test fun statsStageAlignmentAndFadeEnd() {
        lateinit var renderedSection: View
        paparazzi.shoot("r26m-stats", listOf(Size.SMALL), listOf(Theme.LIGHT, Theme.DARK)) { ctx ->
            val data = TestData.world()
            val mon = TestData.state(data, level = 25).main!!
            val store = object : DetailOpenStore {
                override fun get(key: String): Boolean? = true
                override fun set(key: String, open: Boolean) {}
            }
            val section = DetailSections.section(ctx, DetailOpen(store), DetailSection.STATS, "Total 259",
                titleLevel = 25, boosts = intArrayOf(0, -1, 0, 0, 0, 0, 0)) {
                DetailSections.statsBody(ctx, StatCalc.stats(data.sp(mon.species), mon), 25, mon.nature,
                    data.sp(mon.species), true, intArrayOf(0, -1, 0, 0, 0, 0, 0))
            }
            renderedSection = section
            AppDialogShots.scene(ctx, AppDialog.Builder(ctx).setTitle("Details")
                .setView(DetailsFade.wrap(DetailSections.scroll(ctx, section)), true)
                .setDetailsFooter(DetailParts.secondaryButton(ctx, "Options") {}).create())
        }
        val stage = all(renderedSection).filterIsInstance<StatPillView>().first()
        assertEquals((stage.parent as View).width, stage.right)
        val level = all(renderedSection).filterIsInstance<TextView>().first { it.text.toString() == "Level 25" }
        assertSame(level.parent, (stage.parent as View).parent)
        assertEquals(LevelTier.color(level.context, 25), level.currentTextColor)
        val ctx = paparazzi.context
        val scroll = android.widget.ScrollView(ctx)
        scroll.addView(View(ctx).apply {
            minimumHeight = Ui.dp(ctx, 2000)
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_mauve))
        }, android.widget.FrameLayout.LayoutParams(-1, -2))
        val wrapper = DetailsFade.wrap(scroll) as ViewGroup
        val host = android.widget.FrameLayout(ctx).apply {
            addView(wrapper, android.widget.FrameLayout.LayoutParams(-1, Ui.dp(ctx, 240)))
        }
        paparazzi.snapshot(host, "r26m-fade-start")
        assertEquals(View.VISIBLE, wrapper.getChildAt(1).visibility)
        assertFalse(wrapper.getChildAt(1).isClickable)
        scroll.scrollTo(0, scroll.getChildAt(0).height)
        paparazzi.snapshot(host, "r26m-fade-end")
        assertEquals(View.INVISIBLE, wrapper.getChildAt(1).visibility)
    }

    @Test fun wildHeaderAndAchievementWitness() {
        paparazzi.shoot("r26m-wild", listOf(Size.SMALL), listOf(Theme.LIGHT, Theme.DARK)) { ctx ->
            FakeGame.scenario = FakeGame.Scenario.MID_GAME; FakeGame.install(ctx)
            val d = AppDialogShots.capture { assertTrue(BattleDetails.showWild(ctx)) }!!
            assertTrue(texts(d.content()).containsAll(listOf("Details", "Wild Pokémon", "Close")))
            assertTrue(all(d.content()).any { it.contentDescription == "Back to previous screen" })
            AppDialogShots.scene(ctx, d)
        }
        paparazzi.shoot("r26m-achievement", listOf(Size.SMALL), listOf(Theme.LIGHT, Theme.DARK)) { ctx ->
            FakeGame.install(ctx)
            val st = GameRepo.state!!; val data = GameRepo.data!!
            st.badges.remove(18); st.achPokemon.remove("badge/18")
            val baby = st.mons.first { it.species == 172 }
            Badges.award(st, 18, mutableListOf(), 1793491200000L, baby, data)
            st.achTiers["first-baby"] = mutableListOf(1793491200000L)
            val v = Achievements.view(st, data).first { it.ankimonBadge == 18 }
            val sheet = AchievementDetail.sheet(ctx, v, 0, ZoneId.of("UTC"))
            assertTrue(texts(sheet).contains("Earned with ${baby.shownName}"))
            assertEquals(1, texts(sheet).count { it.contains("1 Nov 2026") })
            assertFalse(texts(sheet).any { it.contains("caught on", true) || it.contains("Ankimon badges") })
            sheet
        }
    }
}
