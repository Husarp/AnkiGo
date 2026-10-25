package com.husarp.ankigo.game

/**
 * R10-X2: the one "When you beat a wild Pokémon" list. Each option is a pair of existing settings, so the save format
 * (and the Ankimon export, which reads autoMode 1-3) does not change. Pure, no Android.
 *
 * R19-P (PLAN 281): "Ask me" is a third setting next to the pair, [Settings.askMe]; it always sits on autoMode 3 with the swap off, so an
 * old app or the Ankimon side reads it as "Catch only new species" (the closest thing it knows).
 */
enum class CatchChoice(val title: String, val explain: String, val autoMode: Int, val swap: Boolean, val ask: Boolean = false) {
    ALWAYS("Always catch", "Every wild Pokémon you beat is caught", 1, false),
    NEW_ONLY("Catch only new species", "Others are defeated: the same XP and coins. Default", 3, false),
    NEW_AND_BETTER("Catch new species and better copies", "A higher-level copy replaces your old one", 3, true),
    DEFEAT("Defeat", "Catch only shinies and protected kinds", 2, false),
    ASK_ME("Ask me", "A better copy of one you own waits for your answer", 3, false, ask = true);

    companion object {
        /**
         * The option a save shows: the mode decides; the "better copies" switch only counts with mode 3 (see [normalize]); "Ask me"
         * only counts with mode 3 and wins over the switch.
         */
        fun of(s: Settings): CatchChoice = when {
            s.askMe && s.autoMode == 3 -> ASK_ME
            s.autoReleaseDuplicates && s.autoMode == 3 -> NEW_AND_BETTER
            s.autoMode == 1 -> ALWAYS
            s.autoMode == 2 -> DEFEAT
            else -> NEW_ONLY
        }

        /**
         * An old save with the "better copies" switch on and mode 1 or 2 is a pair no option shows: keep its mode (the list shows it)
         * and turn the switch off, so the game does exactly what the list says. "Ask me" on with mode 1 or 2 is the same kind of pair:
         * it goes off; "Ask me" with the switch also on shows as Ask me, so the switch goes off. Called once when a save is decoded.
         */
        fun normalize(s: Settings) {
            if (s.autoReleaseDuplicates && s.autoMode != 3) s.autoReleaseDuplicates = false
            if (s.askMe && s.autoMode != 3) s.askMe = false
            if (s.askMe) s.autoReleaseDuplicates = false
        }

        /** Writes all three fields of [choice]. */
        fun apply(s: Settings, choice: CatchChoice) {
            s.autoMode = choice.autoMode
            s.autoReleaseDuplicates = choice.swap
            s.askMe = choice.ask
        }
    }
}
