package com.husarp.ankigo.study

/**
 * Type-in cards and the keyboard (PLAN 61, behaviour only): when Enter shows the answer, and when the keyboard
 * opens or hides. Pure; StudyActivity does the InputMethodManager calls and the timer.
 */
object TypeInRules {
    /** Study pref "Enter = Show answer": on by default. */
    const val PREF_ENTER = "enterShows"
    const val ENTER_DEFAULT = true
    /** The keyboard opens this long after a type-in question page finished loading. */
    const val FOCUS_DELAY_MS = 300L

    enum class Keyboard { OPEN, HIDE, KEEP }

    /** What to do with the keyboard when a card side's page has finished loading. */
    fun onPageReady(typeIn: Boolean, answerSide: Boolean, resumed: Boolean): Keyboard = when {
        answerSide || !typeIn -> Keyboard.HIDE   // other cards never open it and close it if it was open
        resumed -> Keyboard.OPEN
        else -> Keyboard.KEEP
    }

    /** The delayed open still applies only to the side it was planned for, while the screen is up. */
    fun stillWanted(plannedSide: Int, currentSide: Int, answerSide: Boolean, resumed: Boolean, destroyed: Boolean) =
        !destroyed && resumed && !answerSide && plannedSide == currentSide

    /** Whether the Enter key (hardware, or soft keyboard) handled by the activity shows the answer. With the setting off, Enter inside a type-in field does nothing special. */
    fun enterShowsAnswer(setting: Boolean, typeInQuestion: Boolean, pageFocused: Boolean) = !(pageFocused && typeInQuestion && !setting)

    /** The `keydown` listener of the type-in field, or "" when Enter should do nothing special. It calls the card's `ankidroid.onTypeAnswerKeyDown` (same hook as AnkiDroid, so a card that replaces it wins) and falls back to the bridge. */
    fun enterScript(enabled: Boolean): String =
        if (!enabled) "" else "(function(){var t=document.getElementById('typeans');if(!t)return;" +
            "t.addEventListener('keydown',function(e){if(e.key!=='Enter'&&e.keyCode!==13)return;e.preventDefault();" +
            "var h=window.ankidroid&&window.ankidroid.onTypeAnswerKeyDown;" +
            "if(typeof h==='function'){try{h(e)}catch(x){AnkiGo.flip()}}else AnkiGo.flip()})})();"

    /** JavaScript that focuses the field (the activity then asks the keyboard to open) / drops focus (so the keyboard can hide). */
    const val FOCUS_JS = "(function(){var t=document.getElementById('typeans');if(t){t.focus();t.select&&t.select()}})()"
    const val BLUR_JS = "(function(){var a=document.activeElement;if(a&&a.blur)a.blur()})()"
}
