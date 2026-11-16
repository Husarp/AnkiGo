package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.game.*
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R48UShots {
    @get:Rule val paparazzi = Shots.rule()
    @After fun reset() { BattleShotKit.reset(); AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun text(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun ctx(theme: Theme): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
        return Shots.files(paparazzi.context).also { c ->
            FakeGame.install(c)
            c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val s = GameRepo.state!!
            s.day = GameRepo.game!!.dayKey(); s.reviewsToday = 128
            s.history[s.day] = DayLog(reviews = 128, won = 21, caught = 5, xp = 2480, cash = 1240,
                buttons = intArrayOf(0, 12, 16, 90, 10), foundItems = mutableMapOf("potion" to 3, "poke-ball" to 1))
            AnkiStatsStore.enabled = false
            AnkiStatsStore.snapshot = StatsSnapshot(timeByDay = mapOf(s.day to 1920000L))
        }
    }
    private fun finish(c: Context): Pair<StudyActivity, View> {
        val a = attach(StudyActivity(), ShotCtx(c))
        setField(a, "deckId", 2L); setField(a, "deckName", "Geography::Capitals")
        val root = a.buildContent()
        call(a, "summary", listOf(Anki.Deck(2L, "Geography::Capitals", 0, 0, 0, false)), 0, 0)
        return a to root
    }
    @Test fun finishDefaultsToDeckAndRemembersTodayAcrossVisits() {
        for (theme in Theme.entries) {
            val c = ctx(theme)
            val (_, root) = finish(c)
            assertTrue(text(root).contains("Done for today!"))
            paparazzi.snapshot(root, "u-finish-deck-${theme.name.lowercase()}", 0)
            all(root).first { it.contentDescription == "Today" }.performClick()
            assertTrue(text(root).contains("Your day so far"))
            assertTrue(text(root).contains("+1,240"))
            assertTrue(text(root).contains("Potion")); assertTrue(text(root).contains("×3"))
            paparazzi.snapshot(root, "u-finish-today-${theme.name.lowercase()}", 0)
            val (_, reopened) = finish(c)
            assertTrue(text(reopened).contains("Your day so far"))
            all(reopened).first { it.contentDescription == "This deck" }.performClick()
            assertTrue(text(reopened).contains("Done for today!"))
        }
    }
    private class Host(val a: Activity) : PageHost {
        override val hostActivity: Activity get() = a
        override fun closePage() = Unit
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
    }
    @Test fun statsRowOpensAndClosesDailySheetWithTheSameTotals() {
        for (theme in Theme.entries) {
            val c = ctx(theme)
            val page = StatsPage(c, Host(attach(Activity(), c))).apply { preset(deck = "Geography::Rivers") }
            val root = pageScreen(c, page)
            paparazzi.snapshot(root, "u-today-entry-${theme.name.lowercase()}", 0)
            val entry = all(root).first { it.contentDescription == "Today’s stats, all decks" }
            entry.performClick()
            assertTrue(text(root).contains("+1,240")); assertTrue(text(root).contains("16 / 5"))
            assertTrue(text(root).contains("Answers counted"))
            paparazzi.snapshot(root, "u-today-sheet-${theme.name.lowercase()}", 0)
            all(root).first { it.contentDescription == "Close Today" }.performClick()
            assertFalse(all(root).any { it.contentDescription == "Close Today" })
        }
    }
    @Test fun todaySelectionAndReturnedCardOfferSurviveFreshQueueCounts() {
        for (theme in Theme.entries) {
            val c = ctx(theme)
            val (a, root) = finish(c)
            all(root).first { it.contentDescription == "Today" }.performClick()
            assertTrue(text(root).contains("answers"))
            val deck = Anki.Deck(2L, "Geography::Capitals", 0, 1, 0, false)
            val review = Anki.Review(500, 0, 4, listOf("1m", "6m", "1d", "4d"), emptyList())
            val read = StudyActivity.DueRefresh(listOf(deck), listOf(review), learn = 1)
            call(a, "applyDueRefresh", read)
            assertTrue(text(root).contains("Your day so far"))
            assertEquals(true, getField(a, "summaryToday"))
            assertTrue(text(root).contains("1 card is back · Continue"))
            val summary = getField(a, "summaryView")
            call(a, "applyDueRefresh", read)
            assertSame(summary, getField(a, "summaryView"))
            assertEquals(1, text(root).count { it == "1 card is back · Continue" })
            all(root).first { it.contentDescription == "This deck" }.performClick()
            assertTrue(text(root).contains("Done for today!"))
            assertTrue(text(root).contains("1 card is back · Continue"))
            val tones = listOf(R.color.ag_red, R.color.ag_peach, R.color.ag_green, R.color.ag_blue)
            for ((i, label) in listOf("Again", "Hard", "Good", "Easy").withIndex()) {
                val answer = all(root).filterIsInstance<TextView>().single { it.text.startsWith("$label ") }
                assertEquals(Ui.c(c, tones[i]), answer.currentTextColor)
            }
        }
    }
    @Test fun stageWindowsShowPercentWithoutChangingChipLabels() {
        for (theme in Theme.entries) {
            val c = ctx(theme)
            val column = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(c, R.color.ag_base)) }
            for ((key, n, expected) in listOf(Triple("atk", 1, "Attack +50%"), Triple("accuracy", -1, "Accuracy −25%"), Triple("def", -1, "Defense −33%"))) {
                val mark = ConditionMarks.Mark("stat.$key", ConditionMarks.Slot.OURS, ConditionMarks.def("stat.$key")!!, n, 1)
                val card = ConditionList(c, 8f).apply { buildMark(mark, "Pikachu", Ui.dp(c, 360), Ui.dp(c, 200), Stats(200, 156, 101, 89, 117, 73)) {} }
                assertTrue(text(card).contains(expected))
                assertFalse(mark.word.contains("%"))
                column.addView(card)
            }
            paparazzi.snapshot(column, "u-stage-percent-${theme.name.lowercase()}", 0)
        }
    }
    @Test fun emptyTodayAndLongItemListsRemainScrollable() {
        val c = ctx(Theme.LIGHT)
        val s = GameRepo.state!!
        s.history.clear(); s.reviewsToday = 0
        val empty = TodayViews.body(c, TodaySummary(s, s.day, 0))
        assertTrue(text(empty).contains("No items")); assertTrue(text(empty).contains("—"))
        s.history[s.day] = DayLog(foundItems = Items.ALL.keys.take(30).associateWith { 3 }.toMutableMap())
        val sheet = TodayViews.sheet(c, TodaySummary(s, s.day, 0)) {}
        assertTrue(all(sheet).any { it is MotionScrollView })
        paparazzi.snapshot(sheet, "u-today-many-items-light", 0)
    }
}
