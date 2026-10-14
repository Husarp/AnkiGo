package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.study.StudyTiming
import com.husarp.ankigo.study.StudyTiming.Act
import com.husarp.ankigo.ui.CountdownBar
import com.husarp.ankigo.ui.SettingsPage
import org.junit.Rule
import org.junit.Test

/** The visible answer timer (top bar pill) and the auto-advance bar (top of the card), plus the Settings rows. WebView card = blank in the renderer. */
class TimingShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val sizes = listOf(Size.PHONE, Size.SPLIT, Size.LAND, Size.TABLET)

    private fun screen(
        ctx: Context, flipped: Boolean = false, buttons: Int = 4, takenMs: Long = 7_000, frozen: Long = -1, maxSecs: Long = 60,
        timer: String? = "Always", auto: StudyTiming.Plan? = null, left: Float = .6f, name: String = "Geography::Capitals",
    ): Pair<StudyActivity, View> {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L)
        setField(act, "deckName", name)
        setField(act, "heightMode", if (ctx.resources.configuration.smallestScreenWidthDp >= 600) 0 else 1)
        val root = act.buildContent()
        val times = if (buttons == 2) listOf("<1m", "1d") else if (buttons == 3) listOf("<1m", "1d", "4d") else listOf("<1m", "6m", "1d", "4d")
        setField(act, "card", Anki.Review(10L, 0, buttons, times, emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>")
        setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic")
        setField(act, "cardName", "Card 1")
        setField(act, "flipped", flipped)
        setField(act, "locked", false)
        setField(act, "elapsed", takenMs)
        setField(act, "maxTime", maxSecs * 1000)
        setField(act, "frozenAt", frozen)
        if (timer != null) act.getSharedPreferences("study", 0).edit().putString("timer", timer).apply()
        (getField(act, "subtitle") as TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as com.husarp.ankigo.ui.BattlePanelView
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        act.refreshTimer()
        if (auto != null) {
            act.getSharedPreferences("study", 0).edit().putBoolean("autoAdvance", true).apply()
            val bar = getField(act, "countdown") as CountdownBar
            bar.visibility = View.VISIBLE; act.styleCountdown(auto); bar.fraction = left
        }
        return act to root
    }

    private fun plan(act: Act, secs: Long = 5) = StudyTiming.Plan(act, secs * 1000, 0)

    @Test fun timer() {
        paparazzi.shootWith("timing-timer", sizes) { screen(it).second }
        paparazzi.shootWith("timing-timer-long", sizes) { screen(it, takenMs = 65_000, maxSecs = 120, name = "Languages::Japanese::JLPT N3 vocabulary and kanji").second }
        paparazzi.shootWith("timing-timer-max", sizes) { screen(it, takenMs = 75_000, flipped = true).second }
        paparazzi.shootWith("timing-timer-frozen", sizes) { screen(it, flipped = true, takenMs = 19_000, frozen = 12_000).second }
        paparazzi.shootWith("timing-timer-off", listOf(Size.PHONE)) { screen(it, timer = "Off").second }
    }

    @Test fun autoBar() {
        paparazzi.shootWith("timing-auto-question", sizes) { screen(it, auto = plan(Act.SHOW_ANSWER, 10)).second }
        paparazzi.shootWith("timing-auto-answer-good", sizes) { screen(it, flipped = true, auto = plan(Act.GOOD), left = .35f).second }
        paparazzi.shootWith("timing-auto-with-timer", sizes) { screen(it, flipped = true, takenMs = 14_000, auto = plan(Act.BURY), left = .8f).second }
    }

    /** Every colour: the question-side reminder and each answer-side action, at a different fill. */
    @Test fun autoColours() {
        val all = listOf("q-show" to (Act.SHOW_ANSWER to false), "q-reminder" to (Act.REMINDER to false), "a-bury" to (Act.BURY to true), "a-again" to (Act.AGAIN to true),
            "a-hard" to (Act.HARD to true), "a-good" to (Act.GOOD to true), "a-reminder" to (Act.REMINDER to true))
        for ((n, p) in all) paparazzi.shootWith("timing-colour-$n", listOf(Size.PHONE)) { screen(it, flipped = p.second, auto = plan(p.first), left = .5f).second }
    }

    /** The Settings page scrolled so the Study card is at the top. */
    private class ToStudy(ctx: Context, page: View) : FrameLayout(ctx) {
        init { addView(page, LayoutParams(-1, -1)) }
        private fun find(v: View, pred: (View) -> Boolean): View? {
            if (pred(v)) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
            return null
        }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            val scroll = find(this) { it is ScrollView } as? ScrollView ?: return
            val title = find(scroll) { it is TextView && it.text.toString() == "STUDY" } ?: return
            var y = 0; var v: View = title
            while (v !== scroll) { y += v.top; v = v.parent as View }
            scroll.scrollTo(0, y - 4)
        }
    }

    private fun studyPage(ctx: Context): View {
        val sp = SettingsPage(ShotCtx(ctx), NoHost)
        val v = pageScreen(ShotCtx(ctx), sp)
        sp.open(com.husarp.ankigo.ui.SettingsSection.STUDY) // the Study section of the Settings tab
        return v
    }

    @Test fun settingsRows() {
        val phone = Variant("phone", 412, 1100); val tablet = Variant("tablet", 800, 1100)
        paparazzi.shootPage("timing-settings", listOf(phone, tablet)) { ctx -> ToStudy(ctx, studyPage(ctx)) }
        paparazzi.shootPage("timing-settings-auto", listOf(phone)) { ctx ->
            ctx.getSharedPreferences("study", 0).edit().putBoolean("autoAdvance", true).putString("timer", "Always").apply()
            ToStudy(ctx, studyPage(ctx))
        }
    }
}
