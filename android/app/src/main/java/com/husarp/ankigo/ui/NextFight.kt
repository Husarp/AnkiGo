package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameEvent

/**
 * The order of the next fight (round 9 C4 + C5; pure, JVM-tested; [EventPlayer], [BattlePanelView] and [ArenaView] apply it).
 *
 * C5: the moment a fight is won or caught (its batch arrives) the Study screen's button already says Continue ([holdsEarly]); a tap
 * ends the rewards still playing ([splitAtNext]: what goes before the next fight's Encounter is already in the saved game).
 * C4: the next wild one comes in only after its background picture was applied ([EntranceGate]), so it never stands at the old
 * picture's stand points first.
 */
object NextFight {
    /** C4: the most the entrance waits for the next background picture to load (ms); after that the wild one comes in anyway (it may move once, as before). */
    const val PICTURE_WAIT_MS = 1200L

    /**
     * C5: true when the batch [events] ends a fight with a next fight behind it and the panel will hold that fight ([mode] =
     * [CatchTilesPlan.mode] for the panel as it is): Continue shows from now on, not only once the reward animations and the result
     * tiles are done. No: a fled wild one, no next Encounter (end of the deck, nothing to continue to), a small window (timed tiles,
     * no Continue) and an answer or a ball throw behind the result (fast play: the tiles would only flash by).
     */
    fun holdsEarly(events: List<GameEvent>, mode: CatchTilesPlan.Mode): Boolean {
        if (mode != CatchTilesPlan.Mode.HOLD) return false
        val end = events.indexOfLast { it is GameEvent.Caught || it is GameEvent.Defeated }
        if (end < 0) return false
        if ((end + 1 until events.size).none { events[it] is GameEvent.Encounter }) return false
        return (end + 1 until events.size).none { events[it] is GameEvent.Counted || events[it] is GameEvent.BallThrow }
    }

    /**
     * C5, screens whose Continue sits under the result tiles ([EventPlayer] with the panel's own Continue): where in [events] the tiles
     * are queued so they show, with their Continue, right after the fight's end instead of after the rewards. Right behind the
     * Defeated / Caught event (and the Duplicate event that belongs to a catch): [EventPlayer] looks ahead over those two.
     */
    fun earlyTilesIndex(events: List<GameEvent>): Int {
        val end = events.indexOfLast { it is GameEvent.Caught || it is GameEvent.Defeated }
        if (end < 0) return events.size
        val i = end + 1
        return if (i < events.size && events[i] is GameEvent.Duplicate && events[end] is GameEvent.Caught) i + 1 else i
    }

    /** What a Continue tap does with the waiting [items]: [before] = everything ahead of the next fight, [next] = its Encounter (null = none), [after] = what follows it. */
    class Split<T>(val before: List<T>, val next: T?, val after: List<T>)

    /** Cuts [items] at the first one [isNext] says is the next fight's Encounter. Without one everything is [Split.before]. */
    fun <T> splitAtNext(items: List<T>, isNext: (T) -> Boolean): Split<T> {
        val at = items.indexOfFirst(isNext)
        if (at < 0) return Split(items, null, emptyList())
        return Split(items.subList(0, at), items[at], items.subList(at + 1, items.size))
    }

    /**
     * C4: the one-time gate in front of the wild one's entrance. It is opened by the first of: the picture is there (applied, the
     * same one, plain, or the load failed) or the wait ran out. The entrance then runs once ([enter], possibly after the fade); a
     * late picture or a late timeout opens nothing.
     */
    class EntranceGate {
        private var opened = false
        private var entered = false

        /** True while neither the picture nor the timeout came. */
        val waiting: Boolean get() = !opened

        /** The picture came or the wait ran out. True = this call opened the gate (the entrance follows); false = it was open already. */
        fun open(): Boolean { if (opened) return false; opened = true; return true }

        /** The entrance itself is about to start. True once; false when it already did. */
        fun enter(): Boolean { if (entered) return false; entered = true; return true }
    }
}
