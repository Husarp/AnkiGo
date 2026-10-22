package com.husarp.ankigo.game

/**
 * PLAN 191: the fight events that were made on a screen that is not a battle (Team, Box, Compare, the Bag): an item healed the main
 * and the wild one hit back, a team edit changed the main. Nobody saw it; the saved game already has the result. [GameRepo] keeps
 * them here, in memory, with the id of the fight they belong to (the wild Pokémon's uid), and the first battle host that is on
 * screen and idle takes them ONCE and plays them like an in-battle switch: the Pokémon comes in at its HP before the hit, then the
 * attack lands. Pure Kotlin, tested on the JVM. Main thread only (like [GameRepo]).
 */
class PendingFight(private val cap: Int = CAP) {
    private var fightId: String? = null
    private val list = ArrayList<GameEvent>()

    /** Nothing waits. */
    val isEmpty: Boolean get() = list.isEmpty()

    /** The waiting events when they belong to [fightId] (a copy; nothing is taken), else none. */
    fun peek(fightId: String?): List<GameEvent> =
        if (fightId != null && fightId == this.fightId) list.toList() else emptyList()

    /**
     * Appends [events] of an action made in fight [fightId]. Events of another fight than the waiting ones start the list over (the
     * old ones no longer lead to the saved game). The list keeps the last [cap] events.
     */
    fun add(fightId: String, events: List<GameEvent>) {
        if (events.isEmpty()) return
        if (this.fightId != fightId) { list.clear(); this.fightId = fightId }
        list += events
        while (list.size > cap) list.removeAt(0)
    }

    /**
     * Gives the waiting events back and forgets them: once, never twice. Only when they belong to [fightId] (the fight on now);
     * for another fight (the wild one was replaced meanwhile) or none they are dropped and nothing comes back.
     */
    fun take(fightId: String?): List<GameEvent> {
        val out = peek(fightId)
        clear()
        return out
    }

    /** Forgets everything (an answer arrived, a new game, an import). */
    fun clear() { list.clear(); fightId = null }

    companion object {
        /** Most events kept (the last ones). */
        const val CAP = 40

        /**
         * What the battle plays of an action's [events], or null when there is nothing to play. Only a fight that goes on: the
         * switch / item (MainChanged, SwitchHit) and the wild one's one attack (Attack, Effect). An action that ended the fight
         * (the wild one was defeated or caught, the main fainted and was healed, a ball, a new wild one) is not kept: its result
         * can't be drawn from the saved game before it plays (the new wild one is already in it), so the toast says it as before.
         * Dialog-like events (an evolution, badges, found items) stay with the screen that showed them.
         */
        fun fightPart(events: List<GameEvent>): List<GameEvent>? {
            if (events.any { it is GameEvent.Encounter || it is GameEvent.Defeated || it is GameEvent.Caught || it is GameEvent.Fled ||
                    it is GameEvent.MainHealed || it is GameEvent.BallThrow }) return null
            if (events.none { it is GameEvent.SwitchHit || it is GameEvent.MainChanged }) return null
            return events.filter { it is GameEvent.SwitchHit || it is GameEvent.MainChanged || it is GameEvent.Attack || it is GameEvent.Effect }
        }

        /**
         * True for the events an action made on a battle host (or by an answer from AnkiDroid) can carry that move the fight on by
         * themselves: whatever was waiting for the old state no longer leads to the saved game.
         */
        fun movesFightOn(events: List<GameEvent>): Boolean = events.any {
            it is GameEvent.Counted || it is GameEvent.Encounter || it is GameEvent.BallThrow || it is GameEvent.Fled ||
                it is GameEvent.MainChanged || it is GameEvent.SwitchHit || it is GameEvent.MainHealed
        }

        /**
         * The HP [monName] had BEFORE the wild one's hit, from the waiting [events] (the first [GameEvent.SwitchHit] naming it), or
         * null: a panel draws the Pokémon at that value until the hit plays, so it never shows the result and then drops from it.
         */
        fun hpBeforeHit(events: List<GameEvent>, monName: String): GameEvent.SwitchHit? =
            events.firstOrNull { it is GameEvent.SwitchHit && it.monName == monName } as GameEvent.SwitchHit?
    }
}
