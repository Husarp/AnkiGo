package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameEvent

/** Whose line the battle text bar shows: your Pokémon's (or the app's own news) or the wild one's. */
enum class LogSide {
    YOURS, WILD;

    val other: LogSide get() = if (this == YOURS) WILD else YOURS
}

/** Which way the small hint chevron points at the bar's end: [RIGHT] while your line shows, [LEFT] while the wild one's does. */
enum class LogArrow { NONE, LEFT, RIGHT }

/**
 * R6-12: the battle text bar keeps one latest line per side and shows one of them; a tap or a swipe flips to the other
 * side's line. A new line shows at once (and makes its side the shown one); the flip is forgotten by the next line.
 * Pure (JVM tests): [T] is whatever the bar draws (kind, pill, text).
 */
class LogSlots<T> {
    private val lines = HashMap<LogSide, T>()

    /** The side being shown. */
    var shown: LogSide = LogSide.YOURS
        private set

    /** A new line from [side]: it shows now. [fresh] (a new fight, a fast-forward summary) forgets the other side's line. */
    fun post(side: LogSide, line: T, fresh: Boolean = false) {
        if (fresh) lines.clear()
        lines[side] = line
        shown = side
    }

    /** Forgets both lines. */
    fun reset() { lines.clear(); shown = LogSide.YOURS }

    /** The line being shown (null before the first line). */
    fun current(): T? = lines[shown]

    /** True when the other side has a line to flip to. */
    val canFlip: Boolean get() = lines.containsKey(shown.other)

    /** Flips to the other side; returns its line, or null (nothing changes) when it has none yet. */
    fun flip(): T? {
        val o = lines[shown.other] ?: return null
        shown = shown.other
        return o
    }

    /** The hint for the current state: nothing when there is no other line to flip to. */
    fun arrow(): LogArrow = when {
        !canFlip -> LogArrow.NONE
        shown == LogSide.YOURS -> LogArrow.RIGHT
        else -> LogArrow.LEFT
    }
}

/** Which side a battle event's log line belongs to. System lines (level-ups, items, goal, catch results, notes) count as yours. */
object LogSides {
    fun of(e: GameEvent): LogSide = when (e) {
        is GameEvent.Encounter, is GameEvent.Splash, is GameEvent.Defeated, is GameEvent.Fled -> LogSide.WILD
        is GameEvent.Attack -> if (e.byPlayer) LogSide.YOURS else LogSide.WILD
        is GameEvent.Effect -> if (e.onPlayer) LogSide.YOURS else LogSide.WILD
        is GameEvent.Fainted -> if (e.player) LogSide.YOURS else LogSide.WILD
        is GameEvent.Info -> if (e.text.endsWith("broke free!")) LogSide.WILD else LogSide.YOURS
        else -> LogSide.YOURS
    }

    /** A new wild one starts a new fight: both slots start over. */
    fun startsFight(e: GameEvent): Boolean = e is GameEvent.Encounter
}
