package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/** R3-V10: the study screen on a type-in card with the soft keyboard open (a fake keyboard block of the given height under the resized window). The card itself is a WebView: layoutlib draws it blank. */
class TypeInShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun screen(ctx: Context, imeDp: Int, typeIn: Boolean = true, flipped: Boolean = false): View {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L); setField(act, "deckName", "Geography::Capitals"); setField(act, "heightMode", 1)
        val root = act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Mongolia?</div>"); setField(act, "answerHtml", "<div>Ulaanbaatar</div>")
        setField(act, "modelName", "Basic"); setField(act, "cardName", "Card 1")
        setField(act, "flipped", flipped); setField(act, "locked", false)
        setField(act, "typeInQuestion", typeIn); setField(act, "imeDp", imeDp)
        (getField(act, "subtitle") as TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as com.husarp.ankigo.ui.BattlePanelView
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        if (imeDp == 0) return root
        // the window is resized above the keyboard: the screen takes what is left, the keyboard fills the bottom
        val kb = TextView(ctx).apply {
            text = "keyboard (${imeDp} dp)"; gravity = Gravity.CENTER; setTextColor(Color.GRAY)
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_s1))
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(root, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(kb, LinearLayout.LayoutParams(-1, Ui.dp(ctx, imeDp)))
        }
    }

    @Test fun keyboardOpen() {
        paparazzi.shootWith("typein-kb-phone", listOf(Size.PHONE)) { screen(it, 290) }
        paparazzi.shootWith("typein-kb-split", listOf(Size.SPLIT)) { screen(it, 200) }
        paparazzi.shootWith("typein-kb-land", listOf(Size.LAND)) { screen(it, 190) }
    }

    @Test fun keyboardClosedAndOtherCards() {
        paparazzi.shootWith("typein-closed", listOf(Size.PHONE, Size.SPLIT)) { screen(it, 0) }
        paparazzi.shootWith("typein-kb-othercard", listOf(Size.PHONE)) { screen(it, 290, typeIn = false) }
        paparazzi.shootWith("typein-after-show", listOf(Size.PHONE)) { screen(it, 0, flipped = true) }
    }
}
