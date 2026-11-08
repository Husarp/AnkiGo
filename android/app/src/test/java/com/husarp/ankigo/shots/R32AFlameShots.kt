package com.husarp.ankigo.shots

import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class R32AFlameShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    private fun streak() {
        GameRepo.state!!.streakDays = 17
        GameRepo.state!!.bestStreakDays = 17
        GameRepo.state!!.lastGoalDay = GameRepo.game!!.dayKey()
    }

    private fun flames(view: View): List<StreakFlame> = when (view) {
        is StreakFlame -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { flames(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun checkFlame(root: View, size: Int): View {
        val flame = flames(root).single()
        assertTrue(flame.alive)
        assertEquals(Ui.dp(root.context, size), flame.layoutParams.width)
        assertEquals(Ui.dp(root.context, size), flame.layoutParams.height)
        assertEquals(255, flame.imageAlpha)
        assertNull(flame.colorFilter)
        return root
    }

    @Test fun studyBar() {
        val shown = mutableListOf<StreakFlame>()
        paparazzi.shootWith("a-study-bar", listOf(Size.PHONE)) { ctx ->
            streak()
            val activity = attach(StudyActivity(), ShotCtx(ctx))
            setField(activity, "deckId", 2L)
            setField(activity, "deckName", "Geography::Capitals")
            setField(activity, "panelDp", 400)
            val root = activity.buildContent()
            setField(activity, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
            setField(activity, "locked", false)
            call(activity, "layout")
            (getField(activity, "panel") as BattlePanelView).renderAll()
            call(activity, "showButtons")
            shown += flames(root)
            checkFlame(root, 16)
        }
        assertTrue("The full study panel must show the flame in both themes", shown.size == 2 && shown.all { flame ->
            generateSequence<View>(flame) { it.parent as? View }.all { it.visibility == View.VISIBLE }
        })
    }

    @Test fun battleBar() {
        paparazzi.shootWith("a-battle-bar", listOf(Size.PHONE)) { ctx ->
            streak()
            checkFlame(BattleShotKit.bound(ctx).root, 16)
        }
    }

    @Test fun studySummary() {
        paparazzi.shootWith("a-study-summary", listOf(Size.PHONE)) { ctx ->
            streak()
            val activity = attach(StudyActivity(), ShotCtx(ctx))
            setField(activity, "deckId", 2L)
            setField(activity, "deckName", "Geography::Capitals")
            val root = activity.buildContent()
            call(activity, "summary", StudyDeckKit.decks, 0, 3)
            assertTrue(flames(root).isNotEmpty())
            root
        }
    }

    @Test fun stats() {
        paparazzi.shootWith("a-stats", listOf(Size.PHONE)) { ctx ->
            streak()
            val page = StatsPage(ctx, NoHost)
            val root = pageScreen(ctx, page)
            call(page, "render")
            checkFlame(root, 34)
        }
    }

    @Test fun widgetStillMatches131() {
        paparazzi.shootWith("a-widgets", listOf(Size.PHONE)) { ctx ->
            val snapshot = WidgetSnapshot(25, false, 28, 50, 17, false, 100, 4,
                listOf(WidgetMon(25, "Sparkmouse", 26, false, 1f)))
            assertTrue(snapshot.hasStarter)
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                for (kind in WidgetRender.Kind.entries) {
                    addView(ImageView(ctx).apply {
                        setImageBitmap(WidgetRender.render(ctx, kind, snapshot, 360f, 170f,
                            ctx.resources.configuration.uiMode and 0x30 == 0x20))
                    }, LinearLayout.LayoutParams(Ui.dp(ctx, 360), Ui.dp(ctx, 170)))
                }
                assertNotNull(StreakFlame.stillBitmap(ctx, 32))
            }
        }
    }

    @Test fun originalAnimationFrames() {
        paparazzi.shootWith("a-frames-131", listOf(Size.PHONE)) { ctx ->
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                for (frame in listOf(0, 24, 48, 72)) {
                    addView(Ui.text(ctx, 16f, 800, R.color.ag_text).apply { text = "v1.3.1: ${frame * 50} ms" })
                    val bitmap = javaClass.getResourceAsStream("/streak-flame-v1.3.1/frame-$frame.png")!!.use { BitmapFactory.decodeStream(it) }
                    addView(ImageView(ctx).apply { setImageBitmap(bitmap) }, LinearLayout.LayoutParams(Ui.dp(ctx, 128), Ui.dp(ctx, 128)))
                }
            }
        }
    }

    @Test fun achievementStaysStill() {
        paparazzi.shootWith("a-achievement", listOf(Size.PHONE)) { ctx ->
            streak()
            val page = StatsPage(ctx, NoHost, StatsSection.ACHIEVEMENTS)
            val root = pageScreen(ctx, page)
            page.achievementsView().setTab("Study")
            call(page, "render")
            fun title(view: View): TextView? {
                if (view is TextView && view.text.toString() == "Best streak") return view
                if (view is ViewGroup) for (i in 0 until view.childCount) title(view.getChildAt(i))?.let { return it }
                return null
            }
            val tile = title(root)!!.parent as ViewGroup
            assertTrue(flames(tile).isEmpty())
            val icon = (0 until tile.childCount).map { tile.getChildAt(it) }.filterIsInstance<ImageView>().first()
            assertTrue(icon.drawable is android.graphics.drawable.VectorDrawable)
            assertNotNull(icon.colorFilter)
            root
        }
    }
}
