package com.husarp.ankigo.study

import com.husarp.ankigo.CardHtml
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JsApiTest {
    /** AnkiDroid js-api.js (v0.0.3) jsApiList, as method=endpoint. */
    private val ankiDroid = listOf(
        "ankiGetNewCardCount=newCardCount", "ankiGetLrnCardCount=lrnCardCount", "ankiGetRevCardCount=revCardCount", "ankiGetETA=eta", 
        "ankiGetCardMark=cardMark", "ankiGetCardFlag=cardFlag", "ankiGetNextTime1=nextTime1", "ankiGetNextTime2=nextTime2", 
        "ankiGetNextTime3=nextTime3", "ankiGetNextTime4=nextTime4", "ankiGetCardReps=cardReps", "ankiGetCardInterval=cardInterval", 
        "ankiGetCardFactor=cardFactor", "ankiGetCardMod=cardMod", "ankiGetCardId=cardId", "ankiGetCardNid=cardNid", "ankiGetCardType=cardType", 
        "ankiGetCardDid=cardDid", "ankiGetCardLeft=cardLeft", "ankiGetCardODid=cardODid", "ankiGetCardODue=cardODue", "ankiGetCardQueue=cardQueue", 
        "ankiGetCardLapses=cardLapses", "ankiGetCardDue=cardDue", "ankiIsInFullscreen=isInFullscreen", 
        "ankiSetFullscreenButtonsAndMenu=setFullscreenButtonsAndMenu", "ankiSetFullscreenButtonsOnly=setFullscreenButtonsOnly", 
        "ankiSetFullscreenAllGone=setFullscreenAllGone", "ankiIsTopbarShown=isTopbarShown", "ankiIsInNightMode=isInNightMode", 
        "ankiIsDisplayingAnswer=isDisplayingAnswer", "ankiGetDeckName=deckName", "ankiIsActiveNetworkMetered=isActiveNetworkMetered", 
        "ankiTtsFieldModifierIsAvailable=ttsFieldModifierIsAvailable", "ankiTtsIsSpeaking=ttsIsSpeaking", "ankiTtsStop=ttsStop", 
        "ankiBuryCard=buryCard", "ankiBuryNote=buryNote", "ankiSuspendCard=suspendCard", "ankiSuspendNote=suspendNote", 
        "ankiAddTagToCard=addTagToCard", "ankiResetProgress=resetProgress", "ankiMarkCard=markCard", "ankiToggleFlag=toggleFlag", 
        "ankiSearchCard=searchCard", "ankiSearchCardWithCallback=searchCardWithCallback", "ankiTtsSpeak=ttsSpeak", 
        "ankiTtsSetLanguage=ttsSetLanguage", "ankiTtsSetPitch=ttsSetPitch", "ankiTtsSetSpeechRate=ttsSetSpeechRate", 
        "ankiEnableHorizontalScrollbar=enableHorizontalScrollbar", "ankiEnableVerticalScrollbar=enableVerticalScrollbar", 
        "ankiSetCardDue=setCardDue", "ankiShowNavigationDrawer=showNavigationDrawer", "ankiShowOptionsMenu=showOptionsMenu", 
        "ankiShowToast=showToast", "ankiShowAnswer=showAnswer", "ankiAnswerEase1=answerEase1", "ankiAnswerEase2=answerEase2", 
        "ankiAnswerEase3=answerEase3", "ankiAnswerEase4=answerEase4", "ankiSttSetLanguage=sttSetLanguage", "ankiSttStart=sttStart", 
        "ankiSttStop=sttStop", "ankiAddTagToNote=addTagToNote", "ankiSetNoteTags=setNoteTags", "ankiGetNoteTags=getNoteTags", 
    )

    private val row = mapOf(
        "_id" to "1700000000123", "note_id" to "1600000000456", "deck_id" to "42", "original_deck_id" to "7", "reps" to "9", "lapses" to "2",
        "type" to "2", "queue" to "2", "due" to "19876", "original_due" to "19800", "left" to "1001", "interval" to "33", "sm2_factor" to "2500", "flags" to "3",
    )
    private fun snap(flags: Boolean = true, tags: String = " leech Marked ") = JsApi.snapshot(
        row, 4, listOf("<1m", "6m", "10m", "4d"), Triple(5, 3, 20), tags, "Parent::Child::Verbs", flags,
    ).copy(page = 7, answer = true, night = true, metered = true)
    private fun call(endpoint: String, data: String = "", s: JsApi.Snapshot? = snap(), page: Int = 7, speaking: Boolean = false) = JsApi.call(page, endpoint, data, s, speaking)
    private fun value(endpoint: String, data: String = ""): Any? {
        val j = JSONObject(call(endpoint, data).text)
        assertTrue(endpoint, j.getBoolean("success"))
        return j.get("value")
    }

    @Test fun methodTableMatchesAnkiDroid() {
        assertEquals(ankiDroid.size, JsApi.METHODS.size)
        assertEquals(ankiDroid.toSet(), JsApi.METHODS.map { "${it.key}=${it.value}" }.toSet())
    }

    @Test fun everyEndpointIsInExactlyOneGroup() {
        val all = JsApi.METHODS.values.toSet() + "init"
        val groups = listOf(JsApi.READ_ONLY, JsApi.ACTIONS, JsApi.UNSUPPORTED)
        assertEquals(all, groups.flatten().toSet())
        assertEquals(all.size, groups.sumOf { it.size })
    }

    @Test fun readOnlyEndpointsAnswerWithAnkiDroidTypes() {
        assertEquals(true, value("init"))
        assertEquals(5, value("newCardCount")); assertEquals(3, value("lrnCardCount")); assertEquals(20, value("revCardCount"))
        assertEquals(1700000000123L, value("cardId")); assertEquals(1600000000456L, value("cardNid"))
        assertEquals(42, value("cardDid")); assertEquals(7, value("cardODid"))
        assertEquals(2, value("cardType")); assertEquals(2, value("cardQueue")); assertEquals(9, value("cardReps")); assertEquals(2, value("cardLapses"))
        assertEquals(19876, value("cardDue")); assertEquals(19800, value("cardODue")); assertEquals(1001, value("cardLeft"))
        assertEquals(33, value("cardInterval")); assertEquals(2500, value("cardFactor")); assertEquals(3, value("cardFlag"))
        assertEquals(true, value("cardMark")); assertEquals(true, value("isInNightMode")); assertEquals(true, value("isDisplayingAnswer"))
        assertEquals(false, value("isInFullscreen")); assertEquals(true, value("isTopbarShown")); assertEquals(true, value("isActiveNetworkMetered"))
        assertEquals(true, value("ttsFieldModifierIsAvailable"))
        assertEquals(false, value("ttsIsSpeaking")); assertEquals(true, JSONObject(call("ttsIsSpeaking", speaking = true).text).getBoolean("value"))
        assertTrue(JsApi.READ_ONLY.all { call(it).action == null })
    }

    @Test fun exactResponseText() {
        assertEquals("{\"success\":true,\"value\":5}", call("newCardCount").text)
        assertEquals("{\"success\":true,\"value\":\"6m\"}", call("nextTime2").text)
        assertEquals("{\"success\":true,\"value\":\"Verbs\"}", call("deckName").text)
        assertEquals("{\"success\":false,\"value\":null}", JsApi.FAIL)
    }

    @Test fun nextTimesAndDeckNameAreRawTextEndpoints() {
        assertTrue(JsApi.rawText("nextTime1") && JsApi.rawText("nextTime4") && JsApi.rawText("deckName"))
        assertFalse(JsApi.rawText("cardId")); assertFalse(JsApi.rawText("getNoteTags"))
        assertEquals("<1m", value("nextTime1")); assertEquals("4d", value("nextTime4"))
        assertEquals("Verbs", value("deckName"))
    }

    @Test fun missingNextTimeIsEmptyAndTwoButtonCardsKeepTheirList() {
        val two = snap().copy(buttons = 2, nextTimes = listOf("<1m", "3d"))
        assertEquals("{\"success\":true,\"value\":\"\"}", call("nextTime3", s = two).text)
        assertEquals("{\"success\":true,\"value\":\"3d\"}", call("nextTime2", s = two).text)
    }

    @Test fun noteTagsAreAJsonArrayInsideAString() {
        val v = value("getNoteTags") as String
        assertEquals(listOf("leech", "Marked"), org.json.JSONArray(v).let { a -> (0 until a.length()).map { a.getString(it) } })
        assertEquals("[]", call("getNoteTags", s = snap(tags = "")).text.let { JSONObject(it).getString("value") })
        assertEquals(false, JSONObject(call("cardMark", s = snap(tags = "a b")).text).getBoolean("value"))
        assertEquals(true, JSONObject(call("cardMark", s = snap(tags = "x MARKED")).text).getBoolean("value"))
    }

    @Test fun flagIsZeroWithoutFlagSupportAndKeepsOnlyColourBits() {
        assertEquals(0, JSONObject(call("cardFlag", s = snap(flags = false)).text).getInt("value"))
        val s = JsApi.snapshot(row + ("flags" to "11"), 4, emptyList(), Triple(0, 0, 0), "", "d", true)
        assertEquals(3, s.flag)
    }

    @Test fun deckNameIsTheLastPartAndCountsNeverNegative() {
        assertEquals("Verbs", snap().deckName)
        val s = JsApi.snapshot(row, 4, emptyList(), Triple(-1, -1, -1), "", "Solo", false)
        assertEquals(Triple(0, 0, 0), Triple(s.newCount, s.lrnCount, s.revCount)); assertEquals("Solo", s.deckName)
    }

    @Test fun unsupportedEndpointsFailAndDoNothing() {
        val expected = setOf("markCard", "buryNote", "suspendNote", "setCardDue", "resetProgress", "searchCard", "searchCardWithCallback", "addTagToCard",
            "addTagToNote", "setNoteTags", "setFullscreenButtonsAndMenu", "setFullscreenButtonsOnly", "setFullscreenAllGone", "showNavigationDrawer",
            "showOptionsMenu", "sttSetLanguage", "sttStart", "sttStop", "cardMod", "eta")
        assertEquals(expected, JsApi.UNSUPPORTED)
        for (e in expected) { val r = call(e, "{\"noteId\":1,\"tag\":\"x\"}"); assertEquals(e, JsApi.FAIL, r.text); assertNull(e, r.action) }
        assertEquals(JsApi.FAIL, call("noSuchEndpoint").text); assertNull(call("noSuchEndpoint").action)
    }

    @Test fun callFromAnotherPageOrWithoutSnapshotDoesNothing() {
        for (e in JsApi.READ_ONLY + JsApi.ACTIONS) {
            assertEquals(e, JsApi.FAIL, call(e, "red", page = 6).text); assertNull(e, call(e, "red", page = 6).action)
            assertEquals(e, JsApi.FAIL, call(e, "red", s = null).text); assertNull(e, call(e, "red", s = null).action)
        }
        assertNotEquals(JsApi.FAIL, call("cardId", page = 7).text)
    }

    @Test fun answerEaseMapsRatingsToTheCardsButtons() {
        assertEquals(listOf(1, null, 2, null), (1..4).map { JsApi.easeFor(2, it) })
        assertEquals(listOf(1, null, 2, 3), (1..4).map { JsApi.easeFor(3, it) })
        assertEquals(listOf(1, 2, 3, 4), (1..4).map { JsApi.easeFor(4, it) })
        assertNull(JsApi.easeFor(4, 0)); assertNull(JsApi.easeFor(4, 5))
        // and the mapping is the exact inverse of the game's button mapping
        for (buttons in 2..4) for (ease in 1..buttons) assertEquals(ease, JsApi.easeFor(buttons, StudyRules.gameButton(buttons, ease)))
    }

    @Test fun answerAndShowAnswerAreActionsNotWrites() {
        assertEquals(JsApi.Action.ShowAnswer, call("showAnswer").action)
        for (n in 1..4) { val r = call("answerEase$n"); assertEquals(JsApi.Action.Answer(n), r.action); assertEquals("{\"success\":true,\"value\":true}", r.text) }
    }

    @Test fun toggleFlagSetsClearsAndRejects() {
        assertEquals(1, JsApi.flagTarget(0, "red")); assertEquals(0, JsApi.flagTarget(1, "red")); assertEquals(7, JsApi.flagTarget(3, "Purple"))
        assertEquals(0, JsApi.flagTarget(5, "none")); assertEquals(0, JsApi.flagTarget(0, "none"))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), listOf("red", "orange", "green", "blue", "pink", "turquoise", "purple").map { JsApi.flagTarget(0, it) })
        assertNull(JsApi.flagTarget(0, "yellow")); assertNull(JsApi.flagTarget(0, ""))
        assertEquals(JsApi.Action.Flag(0), call("toggleFlag", "green").action)       // snapshot flag is 3 (green): same colour clears
        assertEquals(JsApi.Action.Flag(1), call("toggleFlag", "red").action)
        val no = call("toggleFlag", "red", s = snap(flags = false)); assertEquals(JsApi.FAIL, no.text); assertNull(no.action)
        val bad = call("toggleFlag", "yellow"); assertEquals(JsApi.FAIL, bad.text); assertNull(bad.action)
    }

    @Test fun buryAndSuspendAreActions() {
        assertEquals(JsApi.Action.Bury, call("buryCard").action); assertEquals(JsApi.Action.Suspend, call("suspendCard").action)
    }

    @Test fun toastAndSpeechPayloads() {
        assertEquals(JsApi.Action.Toast("Hello world", true), call("showToast", "{\"text\":\"Hello%20world\",\"shortLength\":true}").action)
        assertEquals(JsApi.Action.Toast("100% sure+yes", false), call("showToast", "{\"text\":\"100% sure+yes\",\"shortLength\":false}").action)
        assertEquals(JsApi.FAIL, call("showToast", "not json").text); assertEquals(JsApi.FAIL, call("showToast", "{\"text\":\"\"}").text)
        assertEquals(JsApi.Action.Speak("bonjour", false), call("ttsSpeak", "{\"text\":\"bonjour\",\"queueMode\":0}").action)
        assertEquals(JsApi.Action.Speak("bonjour", true), call("ttsSpeak", "{\"text\":\"bonjour\",\"queueMode\":1}").action)
        assertEquals(JsApi.FAIL, call("ttsSpeak", "{\"text\":\"  \"}").text)
        assertEquals(JsApi.Action.StopSpeech, call("ttsStop").action)
        assertEquals(JsApi.Action.Language("fr-FR"), call("ttsSetLanguage", "fr_FR").action)
        assertEquals(JsApi.Action.Pitch(1.5f), call("ttsSetPitch", "1.5").action)
        assertEquals(JsApi.Action.SpeechRate(4f), call("ttsSetSpeechRate", "9").action)
        assertEquals(JsApi.FAIL, call("ttsSetPitch", "abc").text); assertEquals(JsApi.FAIL, call("ttsSetSpeechRate", "NaN").text); assertEquals(JsApi.FAIL, call("ttsSetLanguage", " ").text)
    }

    @Test fun scrollbarSwitches() {
        assertEquals(JsApi.Action.Scrollbar(true, true), call("enableHorizontalScrollbar", "true").action)
        assertEquals(JsApi.Action.Scrollbar(false, false), call("enableVerticalScrollbar", "false").action)
    }

    @Test fun signalLinksMapToTheSameActions() {
        assertEquals(JsApi.Action.ShowAnswer, JsApi.signal("signal:show_answer")); assertEquals(JsApi.Action.ShowAnswer, JsApi.signal("ankidroid://show-answer"))
        for (n in 1..4) assertEquals(JsApi.Action.Answer(n), JsApi.signal("signal:answer_ease$n"))
        assertNull(JsApi.signal("signal:typefocus")); assertNull(JsApi.signal("https://ankigo.media/x")); assertNull(JsApi.signal("signal:answer_ease5"))
    }

    @Test fun shimDefinesEveryAnkiDroidMethodAndTheRawTextRule() {
        val js = JsApiScript.JS
        for (pair in ankiDroid) { val (m, e) = pair.split('=');  assertTrue(m, js.contains("'$m':'$e'")) }
        assertTrue(js.contains("indexOf('nextTime')>=0") && js.contains("indexOf('deckName')>=0"))
        for (n in listOf("showAnswer", "buttonAnswerEase", "reloadPage", "onUpdateHook", "onShownHook", "showHint", "showAllHints", "ankidroid://show-answer", "signal:show_answer", "AnkiGoJs.call"))
            assertTrue(n, js.contains(n))
        for (e in JsApi.UNSUPPORTED) assertTrue(e, js.contains("'$e':'1'"))
        assertFalse("must not use fetch", js.contains("fetch("))
        assertTrue("assigned on window so a pasted copy of js-api.js still works", js.contains("window.AnkiDroidJS=class"))
    }

    @Test fun pageCarriesTokenShimAndAnkiDroidClasses() {
        val a = CardHtml.build("<b>x</b>", "", 0, "q", null, jsPage = 5) { null }.html
        val b = CardHtml.build("<b>x</b>", "", 0, "a", null, jsPage = 6) { null }.html
        assertTrue(a.contains("window.__agPage=5;")); assertTrue(b.contains("window.__agPage=6;"))
        assertTrue(a.indexOf("window.__agPage=5;") < a.indexOf("window.AnkiDroidJS=class"))
        assertTrue(a.indexOf("window.AnkiDroidJS=class") < a.indexOf("<body"))
        assertTrue(a.contains("<html class=\"mobile android linux js\">")); assertTrue(a.contains("class=\"card card1 chrome\""))
        assertEquals(1, Regex("window\\.AnkiDroidJS=class").findAll(a).count())
    }
}
