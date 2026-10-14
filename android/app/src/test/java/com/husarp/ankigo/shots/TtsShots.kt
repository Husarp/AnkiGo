package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.CardHtml
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.study.TtsRules
import java.io.File
import org.junit.Rule
import org.junit.Test

/** T44: the "no voice" note on the study screen (Paparazzi) and the speech buttons in the card page (HTML written for headless Chrome). */
class TtsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun screen(ctx: Context, note: String?, sounds: Int = 0): View {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L); setField(act, "deckName", "Languages::Japanese"); setField(act, "heightMode", 1)
        val root = act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>猫</div>"); setField(act, "answerHtml", "<div>cat</div>")
        setField(act, "modelName", "Basic"); setField(act, "cardName", "Card 1")
        setField(act, "flipped", false); setField(act, "locked", false)
        (getField(act, "subtitle") as TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as com.husarp.ankigo.ui.BattlePanelView
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        setField(act, "voiceNote", note)
        call(act, "mediaNotice", 0, sounds, false)
        return root
    }

    @Test fun missingVoice() {
        val sizes = listOf(Size.PHONE, Size.SPLIT, Size.LAND, Size.TABLET)
        paparazzi.shootWith("tts-novoice", sizes) { screen(it, TtsRules.missingVoiceNote(listOf("Japanese"))) }
        paparazzi.shootWith("tts-novoice-sound", listOf(Size.PHONE, Size.SPLIT), ) { screen(it, TtsRules.missingVoiceNote(listOf("Japanese", "Polish")), sounds = 2) }
        paparazzi.shootWith("tts-noengine", listOf(Size.PHONE)) { screen(it, TtsRules.missingVoiceNote(emptyList())) }
    }

    // ---------------------------------------------------------------- card pages for headless Chrome

    private val out = File(System.getProperty("user.dir"), "build/cardhtml/t44").apply { mkdirs() }
    private val css = ".card{font-family:arial;font-size:20px;text-align:center}"
    private val items = listOf(TtsRules.Item("Hello", "en-US"), TtsRules.Item("Canberra", "en-AU"))

    @Test fun pages() {
        val q = "<div>Capital of Australia? [anki:play:q:1] <b>Say it</b> [sound:hello.mp3]</div><div>[anki:play:q:2] and a <i>second</i> line of text that wraps over several lines so the buttons sit inside text</div>"
        val a = "$q<hr id=answer><div>Canberra [anki:play:a:3] [sound:can.mp3]</div>"
        for (dark in listOf(false, true)) {
            val tag = if (dark) "dark" else "light"
            File(out, "q-$tag.html").writeText(CardHtml.build(q, css, 0, "q", null, dark, items) { null }.html)
            File(out, "a-$tag.html").writeText(CardHtml.build(a, css, 0, "a", null, dark, items + TtsRules.Item("Canberra", "en-AU")) { null }.html)
        }
    }
}
