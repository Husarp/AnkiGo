package com.husarp.ankigo.study

import com.husarp.ankigo.CardHtml
import com.husarp.ankigo.study.TypeInRules.Keyboard
import org.junit.Assert.*
import org.junit.Test

/** PLAN 61: Enter = Show answer, and when the keyboard opens or hides on type-in cards. */
class TypeInRulesTest {
    private fun page(side: String, content: String = "Q [[type:Back]]", enter: Boolean = true, typed: String? = null) =
        CardHtml.build(content, "", 0, side, typed, enterShows = enter) { "back" }

    @Test fun typeInQuestionOpensTheKeyboardWhenTheScreenIsUp() {
        assertEquals(Keyboard.OPEN, TypeInRules.onPageReady(typeIn = true, answerSide = false, resumed = true))
        assertEquals(Keyboard.KEEP, TypeInRules.onPageReady(typeIn = true, answerSide = false, resumed = false))
    }

    @Test fun otherCardsAndTheAnswerSideCloseTheKeyboard() {
        assertEquals(Keyboard.HIDE, TypeInRules.onPageReady(typeIn = false, answerSide = false, resumed = true))
        assertEquals(Keyboard.HIDE, TypeInRules.onPageReady(typeIn = true, answerSide = true, resumed = true))
        assertEquals(Keyboard.HIDE, TypeInRules.onPageReady(typeIn = false, answerSide = true, resumed = false))
    }

    @Test fun delayedOpenIsDroppedWhenTheCardChangesOrTheScreenLeaves() {
        assertTrue(TypeInRules.stillWanted(5, 5, answerSide = false, resumed = true, destroyed = false))
        assertFalse(TypeInRules.stillWanted(5, 6, answerSide = false, resumed = true, destroyed = false))
        assertFalse(TypeInRules.stillWanted(5, 5, answerSide = true, resumed = true, destroyed = false))
        assertFalse(TypeInRules.stillWanted(5, 5, answerSide = false, resumed = false, destroyed = false))
        assertFalse(TypeInRules.stillWanted(5, 5, answerSide = false, resumed = true, destroyed = true))
        assertEquals(300L, TypeInRules.FOCUS_DELAY_MS)
    }

    @Test fun enterKeyOnlyBlockedInsideTypeInWithTheSettingOff() {
        assertTrue(TypeInRules.enterShowsAnswer(setting = true, typeInQuestion = true, pageFocused = true))
        assertFalse(TypeInRules.enterShowsAnswer(setting = false, typeInQuestion = true, pageFocused = true))
        assertTrue(TypeInRules.enterShowsAnswer(setting = false, typeInQuestion = false, pageFocused = true))
        assertTrue(TypeInRules.enterShowsAnswer(setting = false, typeInQuestion = true, pageFocused = false))
    }

    @Test fun enterListenerIsInThePageOnlyWhenOn() {
        val on = page("q", enter = true)
        val off = page("q", enter = false)
        assertTrue(on.typeIn); assertTrue(off.typeIn)
        assertTrue(on.html.contains("addEventListener('keydown'"))
        assertTrue(on.html.contains("onTypeAnswerKeyDown"))
        assertFalse(off.html.contains("onTypeAnswerKeyDown'") || off.html.contains("t.addEventListener('keydown'"))
        assertTrue(on.html.contains("id=\"typeans\""))
    }

    @Test fun noListenerWithoutATypeInField() {
        val p = page("q", content = "plain question")
        assertFalse(p.typeIn)
        assertFalse(p.html.contains("getElementById('typeans');if(!t)return"))
    }

    @Test fun answerSideHasNoFieldAndNoListener() {
        val p = page("a", typed = "abc")
        assertFalse(p.typeIn)
        assertFalse(p.html.contains("id=\"typeans\""))
        assertFalse(p.html.contains("getElementById('typeans');if(!t)return"))
    }

    @Test fun hookIsTheAnkiDroidOneAndShowsTheAnswer() {
        assertTrue(JsApiScript.JS.contains("A.onTypeAnswerKeyDown=function(ev){if(ev&&ev.key==='Enter')go('ankidroid://show-answer')}"))
        assertEquals(JsApi.Action.ShowAnswer, JsApi.signal("ankidroid://show-answer"))
        assertTrue(TypeInRules.enterScript(true).contains("window.ankidroid.onTypeAnswerKeyDown"))
        assertEquals("", TypeInRules.enterScript(false))
    }
}
