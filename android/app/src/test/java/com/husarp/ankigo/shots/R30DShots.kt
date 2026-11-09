package com.husarp.ankigo.shots

import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import com.husarp.ankigo.gfx.GifDecoder
import com.husarp.ankigo.stats.*
import com.husarp.ankigo.ui.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

class R30DShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun ctx(theme: Theme = Theme.LIGHT, height: Int = 820): android.content.Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Variant("360", 360, height).device(theme))
        return Shots.files(paparazzi.context).also { FakeGame.install(it) }
    }
    @After fun clean() { Pickers.interceptApp = null; AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    @Test fun nightDealFrame() {
        for (theme in Theme.entries) {
            val c = ctx(theme)
            val offer = ShopViews.dealCard(c, "potion", "Potion", ShopPlan.Price(140, 200, 30), "Ends in 2 h 12 min", "Heal your Pokémon", night = true) {}
            val stage = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24); setBackgroundColor(Ui.c(c, R.color.ag_base)); addView(offer)
            }
            assertTrue(all(stage).filterIsInstance<TextView>().any { it.text == "DEAL OF THE NIGHT" })
            val shape = offer.background as android.graphics.drawable.GradientDrawable
            assertNotNull(shape)
            assertEquals(Ui.c(c, R.color.ag_blue), (offer as ViewGroup).getChildAt(1).background.let { (it as android.graphics.drawable.ColorDrawable).color })
            paparazzi.snapshot(stage, "d-471-night-${theme.name.lowercase()}")
        }
    }

    @Test fun battleBagOnlyKeepsCategoryPicker() {
        for (theme in Theme.entries) {
            val c = ctx(theme)
            val panel = BattlePanelView(c)
            panel.renderAll()
            var dialog: AppDialog? = null
            Pickers.interceptApp = { dialog = it }
            BattleBag.show(c) {}
            val content = dialog!!.content()
            val page = all(content).filterIsInstance<View>().mapNotNull { it.context as? ShopPage }.first()
            ShopPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
            val texts = all(content).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(texts.contains("Bag")); assertTrue(texts.any { it.contains("All items") })
            assertFalse(texts.contains("Shop")); assertFalse(texts.any { it.startsWith("Bag ·") })
            assertNotNull(all(content).firstOrNull { it.contentDescription == "Expand categories" })
            val stage = FrameLayout(c).apply {
                addView(panel, FrameLayout.LayoutParams(-1, -1))
                addView(View(c).apply { setBackgroundColor(0x77000000) }, FrameLayout.LayoutParams(-1, -1))
                addView(content, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER))
            }
            paparazzi.snapshot(stage, "d-477-battle-bag-${theme.name.lowercase()}")
            dialog!!.dismiss()
            page.onHide(); page.onGone()
        }
    }

    @Test fun battleBagActionsDeliverEventsOnceToTheBattleHost() {
        val c = ctx()
        val received = mutableListOf<List<GameEvent>>()
        val page = ShopPage(c, NoHost, bagOnly = true, onBattleEvents = { received += it })
        val main = GameRepo.state!!.main!!
        val result = page.act { it.useItem("potion", main.uid) }
        assertTrue(result.message, result.ok)
        assertTrue(result.events.any { it is GameEvent.SwitchHit })
        assertEquals(listOf(result.events), received)
        assertTrue(GameRepo.takePendingFightEvents().isEmpty())
        page.onHide(); page.onGone()
    }

    @Test fun heldResultBagCannotThrowOrEscapeThePreparedEncounter() {
        val c = ctx()
        val st = GameRepo.state!!
        st.bag["poke-doll"] = 1
        val battle = st.battle
        val page = ShopPage(c, NoHost, bagOnly = true, onBattleEvents = { fail("Held battle acted") }, fightAvailable = { false })
        val bag = BagActions(page, MonActions(page), page, fightAvailable = { false })
        for (id in listOf("poke-ball", "poke-doll")) {
            val count = st.count(id)
            val act = bag.spec(CardKeys.bagItem(id))!!.act!!
            assertFalse(act.enabled)
            assertEquals("Tap next battle first", act.offLine)
            act.onTap() // A stale enabled control must be checked again at dispatch.
            assertEquals(count, st.count(id))
            assertSame(battle, st.battle)
        }
        assertTrue(bag.spec(CardKeys.bagItem("potion"))!!.act!!.enabled)
        page.onGone()
    }

    @Test fun answerButtonPeriods() {
        AnkiStatsStore.enabled = false
        for (theme in Theme.entries) for (p in ChartPrefs.periodsFor(ChartMetric.BUTTONS)) {
            val c = ctx(theme, 1500)
            val today = LocalDate.parse(GameRepo.game!!.dayKey())
            GameRepo.state!!.apply {
                day = today.toString(); history.clear()
                for (i in 0..40) history[today.minusDays(i.toLong()).toString()] = DayLog(reviews = 90, buttons = intArrayOf(8, 5, 10, 60, 15))
            }
            val page = StatsPage(c, NoHost)
            page.preset(metric = ChartMetric.BUTTONS, period = p)
            val stage = pageScreen(c, page)
            StatsPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
            val texts = all(stage).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(texts.contains("Answer buttons"))
            assertTrue(texts.contains(StatsChartMath.periodLabel(ChartMetric.BUTTONS, p)))
            paparazzi.snapshot(stage, "d-484-${p.name.lowercase()}-${theme.name.lowercase()}")
            page.onHide(); page.onGone()
        }
    }

    @Test fun animatedWidgetContainsFramesWithoutCacheUris() {
        for (theme in Theme.entries) {
            val c = ctx(theme)
            val snap = WidgetLogic.summarize(GameRepo.state!!, System.currentTimeMillis()).copy(today = 120, goal = 50, due = 116)
            val gif = GifDecoder.Gif(2, 2, listOf(
                GifDecoder.Frame(IntArray(4) { Color.RED }, 100), GifDecoder.Frame(IntArray(4) { Color.BLUE }, 100)))
            val rv = BuddyFrames.frameViews(c, snap, 286f, 140f, BuddyLook(hop = true), WidgetLook(transparency = 35), gif)
            // No provider or file is available: both light and dark launchers must receive actual bitmap frames.
            repeat(2) {
                val view = rv.apply(c, FrameLayout(c))
                for (id in listOf(R.id.b_flip_l, R.id.b_flip_d)) {
                    val flip = view.findViewById<ViewFlipper>(id)
                    assertEquals(2, flip.childCount)
                    for ((i, color) in listOf(Color.RED, Color.BLUE).withIndex()) {
                        val image = flip.getChildAt(i).findViewById<ImageView>(R.id.b_frame)
                        assertEquals(color, (image.drawable as BitmapDrawable).bitmap.getPixel(0, 0))
                    }
                }
                assertNotNull(view.findViewById<ImageView>(R.id.b_img_l1).drawable)
                assertNotNull(view.findViewById<ImageView>(R.id.b_img_d1).drawable)
            }
        }
    }
    @Test fun widgetWithNoAnimatedAssetKeepsACompleteStillRender() {
        val c = ctx()
        val snap = WidgetLogic.summarize(GameRepo.state!!, System.currentTimeMillis())
        val rv = WidgetRender.buddyHopViews(c, snap.copy(team = listOf(WidgetMon(-1, "Missing animation", 5, false, 1f))),
            286f, 140f, BuddyLook(hop = true))
        val view = rv.apply(c, FrameLayout(c))
        assertNotNull(view.findViewById<ImageView>(R.id.b_img_l).drawable)
        assertNotNull(view.findViewById<ImageView>(R.id.b_img_d).drawable)
    }

}
