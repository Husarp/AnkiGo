package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.game.*
import com.husarp.ankigo.stats.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R41FShots {
    @get:Rule val paparazzi = Shots.rule()

    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    private fun layout(ctx: Context, root: View) {
        val dm = ctx.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun world(): GameData = TestData.data(listOf(
        TestData.species(840, "Applin", evolutions = listOf(Evolution(841, EvoKind.ITEM, item = "tart-apple"), Evolution(841, EvoKind.ITEM, item = "syrupy-apple"))),
        TestData.species(841, "Flapple"),
        TestData.species(1011, "Dipplin", evolutions = listOf(Evolution(1019, EvoKind.ITEM, item = "syrupy-apple"))),
        TestData.species(1019, "Hydrapple"),
    ))

    @Test fun evolutionDescriptionsNameDistinctSources() {
        assertEquals("Evolves Applin", PopupMath.description("tart-apple", world()))
        assertEquals("Evolves Applin and Dipplin", PopupMath.description("syrupy-apple", world()))
        for (id in Items.ALL.keys) assertFalse(id, PopupMath.description(id, world()).contains("evolve with it"))
        assertEquals(ItemInfo.effect("potion"), PopupMath.description("potion", world()))
        val found = PopupMath.finds(listOf(GameEvent.ItemFound("tart-apple", 2))).single()
        assertEquals(2, found.total)
    }

    @Test fun foundItem() {
        paparazzi.shootWith("f-item-found", listOf(Size.PHONE)) { ctx ->
            setField(GameRepo, "loadedData", world())
            AchievementPopup.itemLayer(ctx, listOf(ItemFind("tart-apple", 2))) {}.also { root ->
                val texts = views(root).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(texts.toString(), texts.containsAll(listOf("Evolves Applin", "Now you have 2")))
            }
        }
    }

    @Test fun multipleFoundItems() {
        paparazzi.shootWith("f-items-found", listOf(Size.PHONE)) { ctx ->
            setField(GameRepo, "loadedData", world())
            AchievementPopup.itemLayer(ctx, listOf(ItemFind("syrupy-apple", 3), ItemFind("potion", 8))) {}.also { root ->
                val texts = views(root).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(texts.any { it.contains("Evolves Applin and Dipplin") && it.contains("Now you have 3") })
                assertTrue(texts.any { it.contains("Now you have 8") })
            }
        }
    }

    @Test fun weeklyChartAndGenerationSources() {
        paparazzi.shootPage("f-stats", listOf(Variants.TALL)) { ctx ->
            AnkiStatsStore.enabled = false
            try {
                val state = GameRepo.state!!
                val today = GameRepo.game!!.dayKey()
                state.history.clear()
                for (i in 0..6) state.history[java.time.LocalDate.parse(today).minusDays(i.toLong()).toString()] =
                    DayLog(reviews = 30 + i * 10)
                val page = StatsPage(ctx, NoHost)
                page.preset(metric = ChartMetric.REVIEWS, period = ChartPeriod.D7, bar = 6)
                val root = pageScreen(ctx, page)
                val texts = views(root).filterIsInstance<TextView>().map { it.text.toString() }
                assertFalse(texts.toString(), texts.any { it.contains("Source:") || it.contains("tap a bar for its day", true) })
                root
            } finally { AnkiStatsStore.enabled = true }
        }
    }

    private fun summary(scale: Float) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p360", 360, 1000).device(theme).copy(fontScale = scale))
            FakeGame.install(Shots.files(paparazzi.context))
            val ctx = Shots.files(paparazzi.context)
            val act = attach(StudyActivity(), ShotCtx(ctx))
            setField(act, "deckId", 2L); setField(act, "deckName", "Geography::Capitals")
            val root = act.buildContent()
            val counts = getField(act, "counts") as IntArray
            listOf(1, 3, 20, 5).forEachIndexed { i, n -> counts[i] = n }
            call(act, "summary", StudyDeckKit.decks, 0, 0)
            layout(ctx, root)
            val labels = views(root).filterIsInstance<TextView>()
            val answerLabels = listOf("Again 1", "Hard 3", "Good 20", "Easy 5").map { name -> labels.single { it.text.toString() == name } }
            val tones = listOf(R.color.ag_red, R.color.ag_peach, R.color.ag_green, R.color.ag_blue)
            answerLabels.forEachIndexed { i, label -> assertEquals(Ui.c(ctx, tones[i]), label.currentTextColor) }
            val header = labels.single { it.text.toString() == "BATTLE RESULTS" }
            fun top(view: View): Int {
                var y = 0; var child = view
                while (child !== root) { y += child.top; child = child.parent as View }
                return y
            }
            paparazzi.snapshot(root, "f-summary-x$scale-${theme.name.lowercase()}", 1000)
            answerLabels.forEach { label ->
                assertTrue("legend bottom ${top(label) + label.height}, header top ${top(header)} at font scale $scale", top(label) + label.height <= top(header))
                assertTrue(label.height >= label.layout.height)
                assertTrue("legend row must include its labels", (label.parent as View).height >= label.height)
            }
        }
    }

    @Test fun summaryNormal() = summary(1f)
    @Test fun summaryLargeFonts() = summary(2f)
}
