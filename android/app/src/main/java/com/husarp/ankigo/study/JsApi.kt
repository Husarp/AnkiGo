package com.husarp.ankigo.study

import org.json.JSONObject

/**
 * The part of AnkiDroid's card JavaScript API (v0.0.3) that AnkiGo can answer safely: endpoint + payload in, response
 * text and an optional action out. Pure (no android.*): the Activity builds a [Snapshot] on the main thread whenever a
 * side is shown, the bridge reads only that immutable snapshot, and every [Action] is executed on the main thread
 * through the study screen's own flip / answer / writeAction functions.
 */
object JsApi {
    /** `{"success":false,"value":null}`: what every unsupported, unknown or stale call gets. */
    const val FAIL = "{\"success\":false,\"value\":null}"

    /** Method name -> endpoint, exactly as AnkiDroid's js-api.js lists them (the shim builds `AnkiDroidJS` from this). */
    val METHODS: Map<String, String> = linkedMapOf(
        "ankiGetNewCardCount" to "newCardCount", "ankiGetLrnCardCount" to "lrnCardCount", "ankiGetRevCardCount" to "revCardCount",
        "ankiGetETA" to "eta", "ankiGetCardMark" to "cardMark", "ankiGetCardFlag" to "cardFlag",
        "ankiGetNextTime1" to "nextTime1", "ankiGetNextTime2" to "nextTime2", "ankiGetNextTime3" to "nextTime3", "ankiGetNextTime4" to "nextTime4",
        "ankiGetCardReps" to "cardReps", "ankiGetCardInterval" to "cardInterval", "ankiGetCardFactor" to "cardFactor", "ankiGetCardMod" to "cardMod",
        "ankiGetCardId" to "cardId", "ankiGetCardNid" to "cardNid", "ankiGetCardType" to "cardType", "ankiGetCardDid" to "cardDid",
        "ankiGetCardLeft" to "cardLeft", "ankiGetCardODid" to "cardODid", "ankiGetCardODue" to "cardODue", "ankiGetCardQueue" to "cardQueue",
        "ankiGetCardLapses" to "cardLapses", "ankiGetCardDue" to "cardDue",
        "ankiIsInFullscreen" to "isInFullscreen", "ankiSetFullscreenButtonsAndMenu" to "setFullscreenButtonsAndMenu",
        "ankiSetFullscreenButtonsOnly" to "setFullscreenButtonsOnly", "ankiSetFullscreenAllGone" to "setFullscreenAllGone",
        "ankiIsTopbarShown" to "isTopbarShown", "ankiIsInNightMode" to "isInNightMode", "ankiIsDisplayingAnswer" to "isDisplayingAnswer",
        "ankiGetDeckName" to "deckName", "ankiIsActiveNetworkMetered" to "isActiveNetworkMetered",
        "ankiTtsFieldModifierIsAvailable" to "ttsFieldModifierIsAvailable", "ankiTtsIsSpeaking" to "ttsIsSpeaking", "ankiTtsStop" to "ttsStop",
        "ankiBuryCard" to "buryCard", "ankiBuryNote" to "buryNote", "ankiSuspendCard" to "suspendCard", "ankiSuspendNote" to "suspendNote",
        "ankiAddTagToCard" to "addTagToCard", "ankiResetProgress" to "resetProgress", "ankiMarkCard" to "markCard", "ankiToggleFlag" to "toggleFlag",
        "ankiSearchCard" to "searchCard", "ankiSearchCardWithCallback" to "searchCardWithCallback",
        "ankiTtsSpeak" to "ttsSpeak", "ankiTtsSetLanguage" to "ttsSetLanguage", "ankiTtsSetPitch" to "ttsSetPitch", "ankiTtsSetSpeechRate" to "ttsSetSpeechRate",
        "ankiEnableHorizontalScrollbar" to "enableHorizontalScrollbar", "ankiEnableVerticalScrollbar" to "enableVerticalScrollbar",
        "ankiSetCardDue" to "setCardDue", "ankiShowNavigationDrawer" to "showNavigationDrawer", "ankiShowOptionsMenu" to "showOptionsMenu",
        "ankiShowToast" to "showToast", "ankiShowAnswer" to "showAnswer",
        "ankiAnswerEase1" to "answerEase1", "ankiAnswerEase2" to "answerEase2", "ankiAnswerEase3" to "answerEase3", "ankiAnswerEase4" to "answerEase4",
        "ankiSttSetLanguage" to "sttSetLanguage", "ankiSttStart" to "sttStart", "ankiSttStop" to "sttStop",
        "ankiAddTagToNote" to "addTagToNote", "ankiSetNoteTags" to "setNoteTags", "ankiGetNoteTags" to "getNoteTags",
    )

    /** Answered with data from the snapshot; changes nothing. */
    val READ_ONLY = setOf(
        "init", "newCardCount", "lrnCardCount", "revCardCount", "nextTime1", "nextTime2", "nextTime3", "nextTime4",
        "cardId", "cardNid", "cardDid", "cardODid", "cardType", "cardQueue", "cardReps", "cardLapses", "cardDue", "cardODue", "cardLeft",
        "cardInterval", "cardFactor", "cardFlag", "cardMark", "getNoteTags", "deckName", "isInNightMode", "isDisplayingAnswer",
        "isInFullscreen", "isTopbarShown", "isActiveNetworkMetered", "ttsFieldModifierIsAvailable", "ttsIsSpeaking",
    )

    /** Do something, always through the study screen's own safe functions. */
    val ACTIONS = setOf(
        "showAnswer", "answerEase1", "answerEase2", "answerEase3", "answerEase4", "buryCard", "suspendCard", "toggleFlag", "showToast",
        "ttsSpeak", "ttsStop", "ttsSetLanguage", "ttsSetPitch", "ttsSetSpeechRate", "enableHorizontalScrollbar", "enableVerticalScrollbar",
    )

    /** Answered with [FAIL] and nothing is done (they need AnkiDroid's own screens or the whole collection). */
    val UNSUPPORTED = setOf(
        "markCard", "buryNote", "suspendNote", "setCardDue", "resetProgress", "searchCard", "searchCardWithCallback", "addTagToCard",
        "addTagToNote", "setNoteTags", "setFullscreenButtonsAndMenu", "setFullscreenButtonsOnly", "setFullscreenAllGone",
        "showNavigationDrawer", "showOptionsMenu", "sttSetLanguage", "sttStart", "sttStop", "cardMod", "eta",
    )

    /** These two resolve to the response TEXT in the page; every other endpoint resolves to its parsed JSON. */
    fun rawText(endpoint: String) = endpoint.contains("nextTime") || endpoint.contains("deckName")

    /** Everything a card script may read, frozen when a side is shown. */
    data class Snapshot(
        val page: Int = 0, val answer: Boolean = false, val night: Boolean = false, val metered: Boolean = false,
        val newCount: Int = 0, val lrnCount: Int = 0, val revCount: Int = 0,
        val buttons: Int = 4, val nextTimes: List<String> = emptyList(),
        val cardId: Long = 0, val nid: Long = 0, val did: Long = 0, val odid: Long = 0,
        val type: Int = 0, val queue: Int = 0, val reps: Int = 0, val lapses: Int = 0,
        val due: Long = 0, val odue: Long = 0, val left: Int = 0, val ivl: Int = 0, val factor: Int = 0,
        val flag: Int = 0, val flagsSupported: Boolean = false, val tags: List<String> = emptyList(), val deckName: String = "",
    ) {
        val marked get() = tags.any { it.equals("marked", true) }
    }

    /** The card-row part of a snapshot (same column names as the content provider); the page-specific fields are set by [Snapshot.copy]. */
    fun snapshot(
        row: Map<String, String?>, buttons: Int, nextTimes: List<String>, counts: Triple<Int, Int, Int>,
        tags: String, deckName: String, flagsSupported: Boolean,
    ): Snapshot {
        fun l(k: String) = row[k]?.toLongOrNull() ?: 0L
        fun i(k: String) = row[k]?.toIntOrNull() ?: 0
        return Snapshot(
            newCount = counts.first.coerceAtLeast(0), lrnCount = counts.second.coerceAtLeast(0), revCount = counts.third.coerceAtLeast(0),
            buttons = buttons, nextTimes = nextTimes,
            cardId = l("_id"), nid = l("note_id"), did = l("deck_id"), odid = l("original_deck_id"),
            type = i("type"), queue = i("queue"), reps = i("reps"), lapses = i("lapses"),
            due = l("due"), odue = l("original_due"), left = i("left"), ivl = i("interval"), factor = i("sm2_factor"),
            flag = if (flagsSupported) i("flags") and 7 else 0, flagsSupported = flagsSupported,
            tags = tags.split(' ', '\t', '\n', '　').filter { it.isNotEmpty() }, deckName = deckName.substringAfterLast("::"),
        )
    }

    sealed class Action {
        object ShowAnswer : Action()
        /** 1 Again, 2 Hard, 3 Good, 4 Easy: the rating, not the button position. */
        data class Answer(val rating: Int) : Action()
        object Bury : Action()
        object Suspend : Action()
        /** The flag to set, 0 = none. */
        data class Flag(val value: Int) : Action()
        data class Toast(val text: String, val short: Boolean) : Action()
        data class Speak(val text: String, val add: Boolean) : Action()
        object StopSpeech : Action()
        data class Language(val tag: String) : Action()
        data class Pitch(val value: Float) : Action()
        data class SpeechRate(val value: Float) : Action()
        data class Scrollbar(val horizontal: Boolean, val on: Boolean) : Action()
    }

    class Reply(val text: String, val action: Action? = null)

    private fun ok(value: Any) = Reply(wrap(value))
    private fun wrap(value: Any) = "{\"success\":true,\"value\":" + when (value) {
        is String -> JSONObject.quote(value)
        else -> value.toString()
    } + "}"
    private fun act(action: Action?) = if (action == null) Reply(FAIL) else Reply(wrap(true), action)

    /** The AnkiDroid answer button (1..4) a rating means on a card with [buttons] buttons, or null when this card has no such button. */
    fun easeFor(buttons: Int, rating: Int): Int? = when (buttons) {
        2 -> listOf(1, 3).indexOf(rating)
        3 -> listOf(1, 3, 4).indexOf(rating)
        else -> if (rating in 1..4) rating - 1 else -1
    }.let { if (it < 0) null else it + 1 }

    private val FLAG_NAMES = listOf("none", "red", "orange", "green", "blue", "pink", "turquoise", "purple")

    /** The flag a `toggleFlag(name)` call leads to: the same colour again clears it; "none" clears; null for an unknown name. */
    fun flagTarget(current: Int, name: String): Int? {
        val i = FLAG_NAMES.indexOf(name.trim().lowercase())
        return if (i < 0) null else if (i == current) 0 else i
    }

    /** Toast text arrives URL-encoded from some templates; plain text (with %, +) stays as it is. */
    fun toastText(raw: String): String = runCatching { java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)

    /** The links `signal:show_answer`, `signal:answer_ease1..4` and `ankidroid://show-answer` that cards navigate to; null for any other URL. */
    fun signal(url: String): Action? = when (url) {
        "signal:show_answer", "ankidroid://show-answer" -> Action.ShowAnswer
        "signal:answer_ease1" -> Action.Answer(1)
        "signal:answer_ease2" -> Action.Answer(2)
        "signal:answer_ease3" -> Action.Answer(3)
        "signal:answer_ease4" -> Action.Answer(4)
        else -> null
    }

    /**
     * Answers one call. [data] is AnkiDroid's `data` field as text (the plain value, or JSON for toast / speak).
     * [page] must be the snapshot's own page; a call from another page gets [FAIL]. [speaking] = the TTS engine is talking now.
     */
    fun call(page: Int, endpoint: String, data: String, snap: Snapshot?, speaking: Boolean = false): Reply {
        if (snap == null || snap.page != page || endpoint in UNSUPPORTED) return Reply(FAIL)
        fun time(i: Int) = snap.nextTimes.getOrElse(i) { "" }
        return when (endpoint) {
            "init", "ttsFieldModifierIsAvailable", "isTopbarShown" -> ok(true)
            "newCardCount" -> ok(snap.newCount); "lrnCardCount" -> ok(snap.lrnCount); "revCardCount" -> ok(snap.revCount)
            "nextTime1" -> ok(time(0)); "nextTime2" -> ok(time(1)); "nextTime3" -> ok(time(2)); "nextTime4" -> ok(time(3))
            "cardId" -> ok(snap.cardId); "cardNid" -> ok(snap.nid); "cardDid" -> ok(snap.did); "cardODid" -> ok(snap.odid)
            "cardType" -> ok(snap.type); "cardQueue" -> ok(snap.queue); "cardReps" -> ok(snap.reps); "cardLapses" -> ok(snap.lapses)
            "cardDue" -> ok(snap.due); "cardODue" -> ok(snap.odue); "cardLeft" -> ok(snap.left)
            "cardInterval" -> ok(snap.ivl); "cardFactor" -> ok(snap.factor); "cardFlag" -> ok(snap.flag)
            "cardMark" -> ok(snap.marked)
            "getNoteTags" -> ok(org.json.JSONArray(snap.tags).toString())
            "deckName" -> ok(snap.deckName)
            "isInNightMode" -> ok(snap.night); "isDisplayingAnswer" -> ok(snap.answer)
            "isInFullscreen" -> ok(false); "isActiveNetworkMetered" -> ok(snap.metered)
            "ttsIsSpeaking" -> ok(speaking)
            "showAnswer" -> act(Action.ShowAnswer)
            "answerEase1", "answerEase2", "answerEase3", "answerEase4" -> act(Action.Answer(endpoint.last() - '0'))
            "buryCard" -> act(Action.Bury)
            "suspendCard" -> act(Action.Suspend)
            "toggleFlag" -> act(if (snap.flagsSupported) flagTarget(snap.flag, data)?.let { Action.Flag(it) } else null)
            "showToast" -> act(json(data)?.let { j ->
                j.optString("text", "").takeIf { it.isNotEmpty() }?.let { Action.Toast(toastText(it), j.optBoolean("shortLength", true)) }
            })
            "ttsSpeak" -> act(json(data)?.let { j ->
                j.optString("text", "").takeIf { it.isNotBlank() }?.let { Action.Speak(it, j.optInt("queueMode", 0) == 1) }
            })
            "ttsStop" -> Reply(wrap(0), Action.StopSpeech)
            "ttsSetLanguage" -> data.trim().takeIf { it.isNotEmpty() }?.let { Reply(wrap(0), Action.Language(it.replace('_', '-'))) } ?: Reply(FAIL)
            "ttsSetPitch" -> data.trim().toFloatOrNull()?.takeIf { it.isFinite() }?.let { Reply(wrap(0), Action.Pitch(it.coerceIn(.1f, 4f))) } ?: Reply(FAIL)
            "ttsSetSpeechRate" -> data.trim().toFloatOrNull()?.takeIf { it.isFinite() }?.let { Reply(wrap(0), Action.SpeechRate(it.coerceIn(.1f, 4f))) } ?: Reply(FAIL)
            "enableHorizontalScrollbar" -> act(Action.Scrollbar(true, data.trim().toBoolean()))
            "enableVerticalScrollbar" -> act(Action.Scrollbar(false, data.trim().toBoolean()))
            else -> Reply(FAIL)
        }
    }

    private fun json(s: String) = runCatching { JSONObject(s) }.getOrNull()
}
