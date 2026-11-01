package com.husarp.ankigo.shots

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.study.CardExplain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R22LExplainTest {
    @get:Rule val paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun questionNeverIncludesHiddenBackEvenWithASelection() {
        for (selection in listOf(null, "term")) {
            val prompt = CardExplain.prompt("<b>Front &amp; term</b>", "SECRET ANSWER", false, selection)
            assertTrue(prompt.contains("Front & term"))
            assertFalse(prompt.contains("SECRET"))
            assertFalse(prompt.contains("back:"))
            if (selection != null) assertTrue(prompt.startsWith("Explain «term» in the context of this flashcard"))
        }
    }

    @Test fun shownAnswerAndSelectedWordsUseTheCardAsContext() {
        val prompt = CardExplain.prompt("Question", "<p>Back &lt;answer&gt;</p>", true, "rare words")
        assertTrue(prompt.contains("«rare words»"))
        assertTrue(prompt.endsWith("Flashcard front: Question back: Back <answer>"))
        assertTrue(prompt.contains("2–4 sentences, no intro"))
    }

    @Test fun htmlMediaAndSoundsAreNotSent() {
        assertEquals("Before Keep this After", CardExplain.plain("Before<img src='private.jpg'>Keep this<audio>not text</audio>After"))
        assertEquals("Hello world End", CardExplain.plain("<style>hidden css</style><script>secret()</script><p>Hello&nbsp;world</p><img src='private.jpg'>[sound:private.mp3]<audio src='a'>hidden audio</audio><video>hidden video</video><p>End</p>"))
    }

    @Test fun longCardBudgetKeepsBothSidesAndCustomPromptIsBounded() {
        val p = CardExplain.prompt("f".repeat(2000), "b".repeat(2000), true, template = "Custom")
        assertEquals("Custom Flashcard front: " + "f".repeat(750) + " back: " + "b".repeat(750), p)
        assertEquals("Custom Flashcard front: " + "f".repeat(1500), CardExplain.prompt("f".repeat(2000), "secret", false, template = "Custom"))
        assertTrue(CardExplain.prompt("f", "b", true, template = "").startsWith(CardExplain.DEFAULT_TEMPLATE))
    }

    @Test fun googleBrowserUrlRoundTripsUnicodeAndReservedCharacters() {
        val prompt = "Explain «żółć» & #? + / 日本語"
        val intent = CardExplain.intent(prompt)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.categories.contains(Intent.CATEGORY_BROWSABLE))
        assertEquals("https", intent.data!!.scheme)
        assertEquals("www.google.com", intent.data!!.host)
        assertEquals("50", intent.data!!.getQueryParameter("udm"))
        assertEquals(prompt, intent.data!!.getQueryParameter("q"))
        assertNull(intent.`package`)
    }

    @Test fun selectionMenuKeepsNativeActionsAndAddsExplainOnlyForVisibleCards() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = paparazzi.context
        val menu = android.widget.PopupMenu(c, View(c)).menu
        val mode = object : android.view.ActionMode() {
            override fun setTitle(title: CharSequence?) {}
            override fun setTitle(resId: Int) {}
            override fun setSubtitle(subtitle: CharSequence?) {}
            override fun setSubtitle(resId: Int) {}
            override fun setCustomView(view: View?) {}
            override fun invalidate() {}
            override fun finish() {}
            override fun getMenu() = menu
            override fun getTitle(): CharSequence = ""
            override fun getSubtitle(): CharSequence = ""
            override fun getCustomView(): View? = null
            override fun getMenuInflater() = android.view.MenuInflater(c)
        }
        var visible = true
        var destroyed = false
        val original = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                menu.add(0, 42, 0, "Copy")
                return true
            }
            override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = true
            override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem) = item.itemId == 42
            override fun onDestroyActionMode(mode: android.view.ActionMode) { destroyed = true }
        }
        val web = com.husarp.ankigo.study.ExplainWebView(c, { visible }, {})
        val callback = web.javaClass.getDeclaredMethod("wrap", android.view.ActionMode.Callback::class.java).apply { isAccessible = true }
            .invoke(web, original) as android.view.ActionMode.Callback
        assertTrue(callback.onCreateActionMode(mode, menu))
        assertEquals(listOf("Copy", "Explain"), (0 until menu.size()).map { menu.getItem(it).title.toString() })
        assertTrue(menu.getItem(1).contentDescription.toString().contains("Opens Google in the browser"))
        assertTrue(callback.onActionItemClicked(mode, menu.findItem(42)))
        assertTrue(callback.onPrepareActionMode(mode, menu))
        callback.onDestroyActionMode(mode)
        assertTrue(destroyed)
        visible = false
        callback.onPrepareActionMode(mode, menu)
        assertFalse(menu.getItem(1).isVisible)
        menu.clear()
        assertTrue(callback.onCreateActionMode(mode, menu))
        assertEquals(1, menu.size())
        web.destroy()
    }

    @Test fun studySettingsExposeTheEditableGooglePrompt() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = ShotCtx(Shots.files(paparazzi.context))
        FakeGame.install(c)
        val page = com.husarp.ankigo.ui.SettingsPage(c, NoHost)
        val root = pageScreen(c, page)
        page.open(com.husarp.ankigo.ui.SettingsSection.STUDY)
        val text = views(root).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(text.contains("Google browser prompt"))
        assertTrue(text.contains("Edit the Google search prompt used by Explain this card, which opens your browser"))
        assertEquals(com.husarp.ankigo.ui.SettingsSection.STUDY,
            com.husarp.ankigo.ui.SettingsSearch.sectionOf(com.husarp.ankigo.ui.SettingsCatalog.byId("explainTemplate")!!))
        val prefs = c.getSharedPreferences("study", 0)
        prefs.edit().remove(CardExplain.PREF_TEMPLATE).commit()
        try {
            val dialog = DialogShots.capture { call(page, "editExplainPrompt") }!!
            val field = views(dialog.content()).filterIsInstance<android.widget.EditText>().single()
            assertEquals(CardExplain.DEFAULT_TEMPLATE, field.text.toString())
            field.setText("My short explanation")
            dialog.button(com.husarp.ankigo.ui.AppDialog.BUTTON_POSITIVE)!!.performClick()
            assertEquals("My short explanation", prefs.getString(CardExplain.PREF_TEMPLATE, null))
            val reopened = DialogShots.capture { call(page, "editExplainPrompt") }!!
            val second = views(reopened.content()).filterIsInstance<android.widget.EditText>().single()
            assertEquals("My short explanation", second.text.toString())
            second.setText("Discard this")
            reopened.button(com.husarp.ankigo.ui.AppDialog.BUTTON_NEGATIVE)!!.performClick()
            assertEquals("My short explanation", prefs.getString(CardExplain.PREF_TEMPLATE, null))
        } finally { prefs.edit().remove(CardExplain.PREF_TEMPLATE).commit() }
    }

    @Test fun explainIsTheFirstActionAndHiddenWhenThereIsNoVisibleCard() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        val act = attach(StudyActivity(), ShotCtx(c))
        act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, emptyList(), emptyList()))
        setField(act, "question", "Question")
        setField(act, "locked", false)
        fun texts() = views(act.actionSheetContent {}).filterIsInstance<TextView>().map { it.text.toString() }
        val text = texts()
        assertTrue(text.indexOf("Explain this card") < text.indexOf("Edit note"))
        assertTrue(text.contains("Opens Google in the browser"))
        setField(act, "calmOn", true)
        assertFalse(texts().contains("Explain this card"))
        setField(act, "calmOn", false)
        setField(act, "finished", true)
        assertFalse(texts().contains("Explain this card"))
        setField(act, "finished", false)
        setField(act, "card", null)
        assertFalse(texts().contains("Explain this card"))
    }
}
