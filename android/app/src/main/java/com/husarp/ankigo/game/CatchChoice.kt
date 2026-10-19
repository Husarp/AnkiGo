package com.husarp.ankigo.game

/**
 * R10-X2: the one "When you beat a wild Pokémon" list. Each option is a pair of existing settings, so the save format
 * (and the Ankimon export, which reads autoMode 1-3) does not change. Pure, no Android.
 */
enum class CatchChoice(val title: String, val explain: String, val autoMode: Int, val swap: Boolean) {
    ALWAYS("Always catch", "Every wild Pokémon you beat is caught", 1, false),
    NEW_ONLY("Catch only new species", "Others are defeated for money. Default", 3, false),
    NEW_AND_BETTER("Catch new species and better copies", "A higher-level copy replaces your old one", 3, true),
    DEFEAT("Defeat", "Catch only shinies and protected kinds", 2, false);

    companion object {
        /**
         * The option a save shows: the mode decides; the "better copies" switch only counts with mode 3 (see [normalize]).
         */
        fun of(s: Settings): CatchChoice = when {
            s.autoReleaseDuplicates && s.autoMode == 3 -> NEW_AND_BETTER
            s.autoMode == 1 -> ALWAYS
            s.autoMode == 2 -> DEFEAT
            else -> NEW_ONLY
        }

        /**
         * An old save with the "better copies" switch on and mode 1 or 2 is a pair no option shows: keep its mode (the list shows it)
         * and turn the switch off, so the game does exactly what the list says. Called once when a save is decoded.
         */
        fun normalize(s: Settings) {
            if (s.autoReleaseDuplicates && s.autoMode != 3) s.autoReleaseDuplicates = false
        }

        /** Writes both fields of [choice]. */
        fun apply(s: Settings, choice: CatchChoice) {
            s.autoMode = choice.autoMode
            s.autoReleaseDuplicates = choice.swap
        }
    }
}
