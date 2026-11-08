package com.husarp.ankigo.shots

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30ZShots {
    @get:Rule val paparazzi = Shots.rule()

    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    private fun specialWorld() {
        val data = TestData.data(listOf(
            TestData.species(133, "Ordinary"),
            TestData.species(150, "Legendary", isLegendary = true),
            TestData.species(151, "Mythical", isMythical = true),
            TestData.species(793, "Ultra Beast"),
        ))
        val state = GameState(createdAt = 1L, day = "2026-10-14", starterChosen = true)
        for (sp in data.species.values) {
            val mon = TestData.mon(data, sp.num, 35).also { it.tier = Encounters.tierOf(sp) ?: Tier.NORMAL }
            state.mons += mon; state.team += mon.uid; state.caught += sp.num; state.seen += sp.num
        }
        for ((key, value) in listOf("loadedData" to data, "held" to state)) {
            GameRepo::class.java.getDeclaredField(key).apply { isAccessible = true }.set(GameRepo, value)
        }
        GameRepo::class.java.getDeclaredMethod("rebuild").apply { isAccessible = true }.invoke(GameRepo)
    }

    @Test fun actualBoxDexFramesAndPlainTeamCards() {
        for (section in TeamSection.entries) {
            paparazzi.shoot("z-${section.name.lowercase()}", listOf(Size.PHONE)) { ctx ->
                specialWorld()
                val page = TeamPage(ctx, NoHost).also { it.preset(section) }
                val root = pageScreen(ctx, page)
                TeamPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
                root.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 412), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 869), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, Ui.dp(ctx, 412), Ui.dp(ctx, 869))
                val frames = all(root).filter { v -> generateSequence(v) { it.parent as? View }.all { it.visibility == View.VISIBLE } }.mapNotNull { it.foreground as? RarityFrame.Frame }
                assertEquals("$section frames", if (section == TeamSection.TEAM) 0 else 3, frames.size)
                frames.forEach { it.frozenTimeMs = 784L }
                object : FrameLayout(ctx) {
                    override fun dispatchDraw(canvas: Canvas) {
                        // A deferred page render may replace rows after attachment.
                        all(root).mapNotNull { it.foreground as? RarityFrame.Frame }.forEach { it.frozenTimeMs = 784L }
                        super.dispatchDraw(canvas)
                    }
                }.apply { addView(root, FrameLayout.LayoutParams(-1, -1)) }
            }
        }
    }

    @Test fun clippedHiddenDetachedAndReducedMotionStopAndVisibleCardsShareClock() {
        var checked = false
        paparazzi.shoot("z-visibility", listOf(Size.PHONE), listOf(Theme.LIGHT), offsetMillis = 0) { ctx ->
            val first = View(ctx).apply { RarityFrame.bind(this, PokemonRarity.ULTRA) }
            val second = View(ctx).apply { RarityFrame.bind(this, PokemonRarity.MYTHICAL) }
            val a = first.foreground as RarityFrame.Frame
            val b = second.foreground as RarityFrame.Frame
            object : FrameLayout(ctx) {
                override fun dispatchDraw(canvas: Canvas) {
                    if (!checked) {
                        a.refresh(true); b.refresh(true)
                        assertEquals(2, RarityFrame.Clock.active.size)
                        second.translationY = height.toFloat() + 100
                        b.refresh(true)
                        assertEquals(setOf(a), RarityFrame.Clock.active)
                        first.translationY = height.toFloat() + 100
                        a.refresh(true)
                        assertTrue(RarityFrame.Clock.active.isEmpty())
                        assertFalse("no frame callback when every card is off-screen", RarityFrame.Clock.posted)
                        first.translationY = 0f
                        a.refresh(true)
                        second.translationY = 0f
                        b.refresh(true)
                        assertEquals(2, RarityFrame.Clock.active.size)
                        second.visibility = View.GONE
                        b.refresh(true)
                        assertEquals(setOf(a), RarityFrame.Clock.active)
                        a.refresh(false)
                        assertTrue(RarityFrame.Clock.active.isEmpty())
                        assertFalse(RarityFrame.Clock.posted)
                        a.refresh(true)
                        removeView(first)
                        assertTrue(RarityFrame.Clock.active.isEmpty())
                        checked = true
                    }
                    super.dispatchDraw(canvas)
                }
            }.apply {
                addView(first, FrameLayout.LayoutParams(100, 120))
                addView(second, FrameLayout.LayoutParams(100, 120).also { it.leftMargin = 150 })
            }
        }
        assertTrue("attached visibility checks executed", checked)
        assertTrue("no callback clients retained after snapshot", RarityFrame.Clock.active.isEmpty())
    }

    @Test fun recycledCardsLoseTheirOldEffect() {
        paparazzi.shoot("z-recycled", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            View(ctx).apply {
                RarityFrame.bind(this, PokemonRarity.MYTHICAL)
                assertNotNull(foreground)
                RarityFrame.bind(this, PokemonRarity.PLAIN)
                assertNull(foreground)
                RarityFrame.bind(this, PokemonRarity.ULTRA)
                assertEquals(RarityFrame.effect(PokemonRarity.ULTRA, false), (foreground as RarityFrame.Frame).effect)
                RarityFrame.bind(this, null)
                assertNull(foreground)
            }
        }
    }
}
