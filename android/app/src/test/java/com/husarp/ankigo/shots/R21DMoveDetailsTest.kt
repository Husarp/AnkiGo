package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.Anki
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R21DMoveDetailsTest {
    @get:Rule val paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun exactCardBrowserIntentKeepsPackageAndUsesCardNotNoteId() {
        val saved = Anki.pkg
        try {
            Anki.pkg = "com.ichi2.anki.A"
            val intent = Anki.resetCardIntent(123456789L)
            assertEquals("com.ichi2.anki.A", intent.`package`)
            assertEquals("android.intent.action.VIEW", intent.action)
            assertEquals("cid:123456789", intent.data!!.getQueryParameter("search"))
            assertEquals("/browser", intent.data!!.path)
        } finally { Anki.pkg = saved }
    }

    @Test fun statsShowTodayAndThirtyDayAveragesAndStudyChart() {
        val store = com.husarp.ankigo.stats.AnkiStatsStore
        val originalEnabled = store.enabled
        val originalSnapshot = store.snapshot
        store.enabled = false
        try {
            paparazzi.shootPage("r21d-seconds-per-card", listOf(Variants.PHONE, Variants.TALL)) { ctx ->
                val today = GameRepo.game!!.dayKey()
                store.snapshot = com.husarp.ankigo.stats.StatsSnapshot(
                    timeByDay = mapOf(today to 20000L), liveTimeByDay = mapOf(today to 9000L),
                    timedAnswersByDay = mapOf(today to 2L))
                val page = StatsPage(ctx, NoHost)
                page.preset(metric = com.husarp.ankigo.stats.ChartMetric.SECONDS_PER_CARD)
                val screen = pageScreen(ctx, page)
                call(page, "render") // GamePage normally posts this on its Handler.
                val text = views(screen).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue(text.contains("Today · Avg 14.5 s / card"))
                assertTrue(text.contains("Last 30 days · Avg 14.5 s / card"))
                assertTrue(text.any { it.contains("Seconds per card") })
                screen
            }
        } finally { store.enabled = originalEnabled; store.snapshot = originalSnapshot }
    }

    @Test fun resetCardSheetExplainsTheNativeHandoffAndCanCancel() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val act = attach(com.husarp.ankigo.StudyActivity(), ShotCtx(ctx))
        setField(act, "stats", mapOf("_id" to "123456789"))
        val sheet = act.actionSheetContent { }
        val label = views(sheet).filterIsInstance<TextView>().single { it.text.toString() == "Reset card" }
        val dialog = DialogShots.capture { (label.parent.parent as View).performClick() }!!
        val text = views(dialog.content()).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(text.any { it.contains("AnkiDroid must reset this card") })
        assertTrue(text.contains("Cancel"))
        assertTrue(text.contains("Open card in AnkiDroid"))
    }

    @Test fun targetedMoveOpensFoldAndDescriptionWithoutChangingSavedFold() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val original = DetailSections.storeOf
        val saved = mutableMapOf("moves" to false)
        DetailSections.storeOf = { object : DetailOpenStore {
            override fun get(key: String) = saved[key]
            override fun set(key: String, open: Boolean) { saved[key] = open }
        } }
        try {
            val mon = GameRepo.state!!.main!!
            mon.moves.clear(); mon.moves.addAll(listOf("tackle", "thunderbolt", "quickattack", "thunderwave"))
            val dialog = DialogShots.capture { MonDetail.show(ctx, GameRepo.data!!, mon, "thunderwave") }!!
            val scene = DialogShots.scene(ctx, dialog)
            paparazzi.snapshot(scene, "r21d-focused-move", 1000)
            val all = views(scene)
            val target = all.single { it.tag == "detail-move:thunderwave" }
            assertEquals(View.VISIBLE, (target.parent.parent.parent as View).visibility)
            val group = target.parent as ViewGroup
            assertEquals(View.VISIBLE, group.getChildAt(1).visibility)
            assertFalse(saved.getValue("moves"))
            val scroll = all.filterIsInstance<ScrollView>().first { views(it).contains(target) }
            assertTrue("scrolled down to move", scroll.scrollY > 0)
            val foldBar = all.first { it.contentDescription?.toString()?.equals("Moves, open", ignoreCase = true) == true }
            foldBar.performClick()
            assertEquals("first tap closes a temporarily opened fold", View.GONE, (target.parent.parent.parent as View).visibility)
            assertFalse(saved.getValue("moves"))
            foldBar.performClick()
            assertEquals(View.VISIBLE, (target.parent.parent.parent as View).visibility)

            assertTrue(all.filterIsInstance<TextView>().any { it.text.contains("Moves", ignoreCase = true) })
        } finally { DetailSections.storeOf = original }
    }
}
