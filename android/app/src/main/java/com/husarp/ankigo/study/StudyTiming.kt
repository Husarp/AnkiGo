package com.husarp.ankigo.study

import org.json.JSONObject

/** The visible answer timer and auto-advance, from AnkiDroid's deck options (legacy deck-config JSON): pure rules, no Android. */
object StudyTiming {
    /** What a deck's options say about time. Missing or odd keys fall back to Anki's defaults. */
    data class Options(
        val maxTakenSecs: Long = 60, val showTimer: Boolean = false, val stopTimerOnAnswer: Boolean = false,
        val questionSecs: Double = 0.0, val answerSecs: Double = 0.0,
        val questionAction: Int = 0, val answerAction: Int = 0, val waitForAudio: Boolean = true,
    ) { val maxMs get() = maxTakenSecs.coerceAtLeast(1) * 1000 }

    private fun num(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is Boolean -> if (v) 1.0 else 0.0
        is String -> v.trim().toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() }
    private fun flag(v: Any?, default: Boolean): Boolean = num(v)?.let { it != 0.0 }
        ?: (v as? String)?.trim()?.lowercase()?.let { if (it == "true") true else if (it == "false") false else null } ?: default

    fun parse(o: JSONObject?): Options {
        if (o == null) return Options()
        val d = Options()
        return Options(
            maxTakenSecs = num(o.opt("maxTaken"))?.toLong()?.coerceAtLeast(1) ?: d.maxTakenSecs,
            showTimer = flag(o.opt("timer"), d.showTimer),
            stopTimerOnAnswer = flag(o.opt("stopTimerOnAnswer"), d.stopTimerOnAnswer),
            questionSecs = num(o.opt("secondsToShowQuestion"))?.coerceAtLeast(0.0) ?: 0.0,
            answerSecs = num(o.opt("secondsToShowAnswer"))?.coerceAtLeast(0.0) ?: 0.0,
            questionAction = num(o.opt("questionAction"))?.toInt() ?: 0,
            answerAction = num(o.opt("answerAction"))?.toInt() ?: 0,
            waitForAudio = flag(o.opt("waitForAudio"), d.waitForAudio),
        )
    }

    // ---------------------------------------------------------------- visible timer

    const val AS_DECK = "As the deck says"; const val ALWAYS = "Always"; const val OFF = "Off"
    val TIMER_MODES = listOf(AS_DECK, ALWAYS, OFF)

    /** Whether the pill shows: the setting [mode] (unknown = as the deck says) and the deck's own "show answer timer". */
    fun timerShown(mode: String?, deckTimer: Boolean) = when (mode) { ALWAYS -> true; OFF -> false; else -> deckTimer }

    /** The time sent with the answer: the banked [elapsed] plus the running stretch (since [shownAt], 0 = paused), capped at [maxMs]. */
    fun taken(elapsed: Long, shownAt: Long, now: Long, maxMs: Long) = (elapsed + if (shownAt != 0L) (now - shownAt).coerceAtLeast(0) else 0).coerceIn(0, maxMs)

    fun atMax(takenMs: Long, maxMs: Long) = takenMs >= maxMs

    /** "0:07", "1:05", "12:00": whole seconds, minutes unpadded. */
    fun clock(ms: Long): String { val s = (ms.coerceAtLeast(0) / 1000); return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }

    fun timerDescription(ms: Long) = "Answer time ${clock(ms)}"

    // ---------------------------------------------------------------- auto-advance

    enum class Act { SHOW_ANSWER, BURY, AGAIN, HARD, GOOD, REMINDER }
    /** [ease] is the answer button to press (1..4 as numbered on this card), 0 for the others. */
    data class Plan(val act: Act, val millis: Long, val ease: Int = 0)

    const val MIN_SECS = 3; const val MAX_SECS = 60; const val OWN_QUESTION = 10; const val OWN_ANSWER = 5

    /** Question side: 0 show the answer, 1 reminder (anything else: show the answer). */
    fun questionAct(action: Int) = if (action == 1) Act.REMINDER else Act.SHOW_ANSWER
    /** Answer side: 0 bury, 1 Again, 2 Good, 3 Hard, 4 reminder (anything else: bury). */
    fun answerAct(action: Int) = when (action) { 1 -> Act.AGAIN; 2 -> Act.GOOD; 3 -> Act.HARD; 4 -> Act.REMINDER; else -> Act.BURY }

    /** The ease for [act] on a card with [buttons] answer buttons. Hard needs 4 buttons; without it the action is a reminder. Good is the 2nd button of 2 and 3, the 3rd of 4. */
    fun easeFor(act: Act, buttons: Int): Int? = when (act) {
        Act.AGAIN -> 1
        Act.HARD -> if (buttons >= 4) 2 else null
        Act.GOOD -> if (buttons >= 4) 3 else 2
        else -> null
    }

    /**
     * What to do on this side, and when. null = no countdown (auto-advance off, or no time). Seconds are the deck's when it has some,
     * else AnkiGo's own ([ownQuestion], [ownAnswer]); actions always come from the deck.
     */
    fun plan(o: Options, answerSide: Boolean, enabled: Boolean, ownQuestion: Int, ownAnswer: Int, buttons: Int): Plan? {
        if (!enabled) return null
        val deck = if (answerSide) o.answerSecs else o.questionSecs
        val secs = if (deck > 0) deck else (if (answerSide) ownAnswer else ownQuestion).toDouble()
        val millis = Math.round(secs * 1000)
        if (millis <= 0) return null
        if (!answerSide) return Plan(questionAct(o.questionAction), millis)
        val act = answerAct(o.answerAction)
        if (act == Act.AGAIN || act == Act.HARD || act == Act.GOOD) {
            val ease = easeFor(act, buttons) ?: return Plan(Act.REMINDER, millis)
            return Plan(act, millis, ease)
        }
        return Plan(act, millis)
    }

    /** The part of the countdown still to run, 0..1. */
    fun left(totalMs: Long, startedAt: Long, now: Long) = if (totalMs <= 0) 0f else (1f - (now - startedAt).coerceAtLeast(0).toFloat() / totalMs).coerceIn(0f, 1f)

    private fun what(act: Act) = when (act) { Act.SHOW_ANSWER -> "show answer"; Act.BURY -> "bury card"; Act.AGAIN -> "Again"; Act.HARD -> "Hard"; Act.GOOD -> "Good"; Act.REMINDER -> "reminder" }
    fun describe(plan: Plan): String { val s = Math.round(plan.millis / 1000.0).coerceAtLeast(1); return "Auto-advance: ${what(plan.act)} in $s second${if (s == 1L) "" else "s"}" }

    fun reminder(answerSide: Boolean) = if (answerSide) "Time's up — pick an answer" else "Time's up — tap the card for the answer"

    /** May the countdown run now? Every pause reason in one place; the countdown restarts in full after any of them. */
    fun mayRun(pageReady: Boolean, inForeground: Boolean, windowFocus: Boolean, locked: Boolean, mediaBlocked: Boolean, dialogOpen: Boolean, audioBusy: Boolean, waitForAudio: Boolean) =
        pageReady && inForeground && windowFocus && !locked && !mediaBlocked && !dialogOpen && !(waitForAudio && audioBusy)
}
