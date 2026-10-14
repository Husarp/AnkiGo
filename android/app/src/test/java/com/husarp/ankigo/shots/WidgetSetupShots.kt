package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.StudyWidgetSetupActivity
import org.junit.Rule
import org.junit.Test

/*
 * U-07: the study widget's deck picker (StudyWidgetSetupActivity, fake AnkiDroid decks) and the study widget layout in the
 * new states (change-deck button in the header, "Nothing due in <deck>" with a Change deck pill).
 */
class WidgetSetupShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun setup(ctx: Context, decks: List<Anki.Deck> = StudyDeckKit.decks, current: Long = 3, granted: Boolean = true): View {
        val ss = ShotCtx(ctx, granted = granted)
        val act = attach(StudyWidgetSetupActivity(), ss)
        setField(act, "decks", decks.sortedBy { it.name })
        setField(act, "currentDeck", current)
        val root = act.buildContent()
        if (!granted) act.showNoAccess() else act.render()
        return root
    }

    @Test fun setupScreen() {
        val both = listOf(Size.PHONE, Size.SPLIT)
        paparazzi.shootWith("widgetsetup-list", both) { setup(it) }
        paparazzi.shootWith("widgetsetup-nodecks", listOf(Size.PHONE)) { setup(it, decks = emptyList(), current = 0) }
        paparazzi.shootWith("widgetsetup-alldone", listOf(Size.PHONE)) { setup(it, decks = StudyDeckKit.decks.map { d -> Anki.Deck(d.id, d.name, 0, 0, 0, d.dyn) }, current = 0) }
        paparazzi.shootWith("widgetsetup-nopermission", both) { setup(it, granted = false) }
    }

    // ---- the widget itself (same wallpaper frame as WidgetShots) ----

    private fun study(ctx: Context, deck: String, text: String, showBtn: Boolean, answerBtns: Boolean, changeBtn: Boolean = false): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_study)
        rv.setTextViewText(R.id.s_deck, deck.substringAfterLast("::"))
        rv.setTextViewText(R.id.s_text, text)
        fun vis(on: Boolean) = if (on) View.VISIBLE else View.GONE
        rv.setViewVisibility(R.id.s_show, vis(showBtn))
        rv.setViewVisibility(R.id.s_change, vis(changeBtn))
        for (id in listOf(R.id.s_again, R.id.s_hard, R.id.s_good, R.id.s_easy)) rv.setViewVisibility(id, vis(answerBtns))
        return rv
    }

    private val widgetShots = WidgetShotsFrame()

    @Test fun widgetStates() {
        widgetShots.shoot(paparazzi, "widgetU07-question", 250, 180) { c -> study(c, "Geography::Capitals", "Capital of Australia?", true, false) }
        widgetShots.shoot(paparazzi, "widgetU07-answer", 250, 180) { c -> study(c, "Geography::Capitals", "Capital of Australia?\n―――\nCanberra", false, true) }
        widgetShots.shoot(paparazzi, "widgetU07-nothingdue", 250, 180) { c -> study(c, "Geography::Capitals", "Nothing due in Capitals", false, false, true) }
        widgetShots.shoot(paparazzi, "widgetU07-longname", 250, 180) { c -> study(c, "Medicine::Pharmacology::Cardiovascular drugs and their interactions", "Nothing due in Cardiovascular drugs and their interactions", false, false, true) }
        widgetShots.shoot(paparazzi, "widgetU07-nodeck", 250, 180) { c -> study(c, "Choose a deck", "Nothing due in AnkiDroid.", false, false, true) }
    }
}

/** The wallpaper frame of WidgetShots, kept here so that file is not edited. */
class WidgetShotsFrame {
    fun shoot(paparazzi: Paparazzi, name: String, wDp: Int, hDp: Int, build: (Context) -> RemoteViews) {
        val margin = 28
        val sw = wDp + 2 * margin
        val sh = hDp + 2 * margin
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme).copy(
                screenWidth = sw * 2, screenHeight = sh * 2,
                orientation = if (sw > sh) com.android.resources.ScreenOrientation.LANDSCAPE else com.android.resources.ScreenOrientation.PORTRAIT,
            ))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val view = build(ctx).apply(ctx, android.widget.FrameLayout(ctx))
            val d = ctx.resources.displayMetrics.density
            val colors = if (theme == Theme.LIGHT) intArrayOf(0xFF9EC5E8.toInt(), 0xFFE7B8D6.toInt(), 0xFFF3D9A4.toInt())
            else intArrayOf(0xFF1B2A4A.toInt(), 0xFF3A2754.toInt(), 0xFF152B33.toInt())
            val frame = android.widget.FrameLayout(ctx).apply {
                background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, colors)
                addView(view, android.widget.FrameLayout.LayoutParams((wDp * d).toInt(), (hDp * d).toInt(), android.view.Gravity.CENTER))
            }
            paparazzi.snapshot(frame, "$name-${wDp}x$hDp-${theme.name.lowercase()}", 1000)
        }
    }
}
