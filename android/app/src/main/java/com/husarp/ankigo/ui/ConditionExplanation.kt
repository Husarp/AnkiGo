package com.husarp.ankigo.ui

import com.husarp.ankigo.game.StageText

/** Short descriptions of the implemented battle rules, available even without a loaded save. */
internal object ConditionExplanation {
    val effects = mapOf(
        "switchtrap" to "Can't switch; Ghost types can escape.",
        "confusion" to "May hit itself instead of moving, 1–4 turns.",
        "attract" to "50% chance to miss its move.",
        "partiallytrapped" to "Loses 1/8 max HP each round.",
        "taunt" to "Can't use status moves.",
        "encore" to "Must repeat its last move.",
        "disable" to "Can't use its last move.",
        "torment" to "Can't use the same move twice running.",
        "healblock" to "Can't restore HP.",
        "throatchop" to "Can't use sound moves.",
        "yawn" to "Falls asleep at the end of the next turn.",
        "imprison" to "Can't use moves the opponent also knows.",
        "lockedmove" to "Repeats its move; then becomes confused.",
        "uproar" to "Repeats Uproar; keeps both Pokémon awake.",
        "smackdown" to "Ground moves can hit it.",
        "tarshot" to "Takes double Fire damage.",
        "foresight" to "Ghost immunity to Normal/Fighting and evasion boosts are ignored.",
        "miracleeye" to "Dark immunity to Psychic and evasion boosts are ignored.",
        "leechseed" to "Loses 1/8 max HP each round to heal the opponent.",
        "curse" to "Loses 1/4 max HP each round.",
        "nightmare" to "Loses 1/4 max HP each round while asleep.",
        "saltcure" to "Loses 1/8 max HP each round; Water/Steel lose 1/4.",
        "syrupbomb" to "Speed drops one stage each round.",
        "substitute" to "Decoy takes hits and blocks status moves; sound bypasses it.",
        "destinybond" to "If a move knocks it out before its next move, its attacker faints too.",
        "aquaring" to "Heals 1/16 max HP each round.",
        "ingrain" to "Heals 1/16 max HP each round; stays grounded.",
        "stockpile" to "Stores up to 3 Defense/Sp. Def rises for Swallow or Spit Up.",
        "magnetrise" to "Ground moves miss it unless grounded.",
        "telekinesis" to "Ground moves miss; other moves hit except one-hit KOs.",
        "charge" to "Next Electric move deals double damage.",
        "defensecurl" to "Rollout and Ice Ball deal double damage.",
        "minimize" to "Stomp, Body Slam and similar moves always hit for double damage.",
        "powertrick" to "Attack and Defense are swapped.",
        "powershift" to "Attack/Defense and Sp. Atk/Sp. Def are swapped.",
        "ab.flashfire" to "Fire moves deal 1.5× damage after absorbing Fire.",
        "ab.gas" to "Suppresses most other abilities.",
        "spikes" to "Hurts grounded Pokémon when they switch in; more layers hurt more.",
        "toxicspikes" to "Poisons grounded switch-ins; 2 layers badly poison.",
        "stealthrock" to "Rock damage on switching in; type matchups affect damage.",
        "stickyweb" to "Grounded switch-ins lose one Speed stage.",
        "reflect" to "Physical hits deal half damage.",
        "lightscreen" to "Special hits deal half damage.",
        "auroraveil" to "Physical and special hits deal half damage.",
        "safeguard" to "Blocks the opponent's status and confusion effects.",
        "mist" to "Blocks the opponent's stat drops.",
        "tailwind" to "Doubles Speed.",
        "wish" to "Heals half max HP at the end of the next turn.",
        "mp.fs" to "A delayed special hit lands after two turns.",
        "rain" to "Water moves ×1.5 damage; Fire moves ×0.5.",
        "sun" to "Fire moves ×1.5 damage; Water moves ×0.5 except Hydro Steam; prevents freeze.",
        "sand" to "Hurts unprotected types each round except the last; Rock Sp. Def ×1.5.",
        "hail" to "Hurts non-Ice types each round except the last.",
        "snow" to "Ice types' Defense ×1.5; no weather damage.",
        "electric" to "Grounded: Electric damage ×1.3; can't fall asleep.",
        "grassy" to "Grounded: Grass damage ×1.3; heals 1/16 HP; Earthquake-like damage halved.",
        "misty" to "Grounded: blocks status/confusion; Dragon damage halved.",
        "psychic" to "Grounded: Psychic damage ×1.3; blocks opposing priority moves.",
        "trickroom" to "Slower Pokémon moves first within the same priority.",
        "gravity" to "Grounds both Pokémon; accuracy ×5/3; blocks airborne moves.",
        "magicroom" to "Held items have no effect.",
        "wonderroom" to "Defense and Sp. Def are swapped for both Pokémon.",
        "mudsport" to "Electric damage is one third for both sides.",
        "watersport" to "Fire damage is one third for both sides.",
        "iondeluge" to "Normal moves become Electric this turn.",
    )

    fun effect(mark: ConditionMarks.Mark): String {
        if (!mark.key.startsWith("stat.")) return FieldExplanation.current(mark).ifBlank { effects.getValue(mark.key) }
        val key = mark.key.removePrefix("stat.")
        val factor = StageText.factor(mark.value, key == "accuracy" || key == "evasion")
        return when (key) {
            "accuracy" -> "Move accuracy ×$factor."
            "evasion" -> "Evasion ×$factor; higher means harder to hit."
            else -> "${StatPill.longName(key)} ×$factor."
        } + " Until switching, fainting or a new opponent."
    }

    fun line(mark: ConditionMarks.Mark): String {
        val detail = if (mark.key.startsWith("stat.")) mark.word else mark.line
        return "$detail — ${effect(mark)}"
    }

    fun spoken(mark: ConditionMarks.Mark): String = if (mark.key.startsWith("stat.")) line(mark) else "${mark.spoken}. ${effect(mark)}"
}
