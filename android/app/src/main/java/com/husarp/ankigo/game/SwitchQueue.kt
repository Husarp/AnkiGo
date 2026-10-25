package com.husarp.ankigo.game

/**
 * PLAN 206: the words and the one rule of a queued switch. A switch made while a card shows is not done at once: it happens right
 * after that card is answered ([Game.requestMain], [GameState.pendingMain]), so the card you saw is fought by the Pokémon that was
 * out. Pure Kotlin; the screens only ask these functions.
 */
object SwitchQueue {
    /**
     * True when a switch made now waits for the next answer: a fight is on (a wild one with HP left) and the main can still fight.
     * Without a fight, or with a fainted main, a switch is done at once as before. The Team menu uses it to word "Make main".
     */
    fun fightOn(state: GameState): Boolean {
        val battle = state.battle ?: return false
        val main = state.main ?: return false
        return battle.wild.hp > 0 && main.hp > 0
    }

    /**
     * The toast / result text of a queued switch (R18-B: Adam's picked wording). The attack is in [MENU_SUB], and in the Help text:
     * the wild Pokémon attacks once. R19-T (PLAN 288): when the free hit really comes, the battle's own toast says it ([freeHitText]).
     */
    fun queuedText(name: String) = "Switching to $name on your next move."

    /**
     * R19-T (PLAN 288): the battle's card toast when a switch gave the wild Pokémon [wildName] (no "Wild" in front) its one extra attack. The
     * switch takes no dot and none of the round's answers; `{{...}}` turns the name mauve.
     */
    fun freeHitText(wildName: String) = "Switched! Wild {{$wildName}} gets a free hit."

    /** The result text when the queue was cancelled (the main was picked again). */
    fun cancelledText(name: String) = "$name stays in."

    /** The sub line of "Make main" in the Team / Compare menus while a fight is on (outside a fight there is none). */
    const val MENU_SUB = "On your next move · the wild Pokémon attacks once"

    /** The marker on the queued Pokémon's Team row. */
    const val ROW_MARK = "On your next move"
}
