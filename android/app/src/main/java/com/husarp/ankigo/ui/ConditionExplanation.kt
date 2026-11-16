package com.husarp.ankigo.ui

import com.husarp.ankigo.game.StageText

/** Short descriptions of the implemented battle rules, available even without a loaded save. */
internal object ConditionExplanation {
    val effects = mapOf(
        "heal.regen" to "10% max HP per remaining turn end; 10% now + 4 ticks = 50% total. Pauses benched; ends with fight.",
        "heal.pending" to "0% now; 70% max HP before attacks next turn. Pauses on the bench; ends with the fight.",
        "heal.speed" to "Speed ×1.25 for this Pokémon this fight, including switches. Reuse does not stack.",
        "heal.attack" to "Attack ×1.25 for this Pokémon this fight, including switches. Reuse does not stack.",
        "heal.defense" to "Defense ×1.25 for this Pokémon this fight, including switches. Reuse does not stack.",
        "heal.slow" to "Speed ×0.75 for this Pokémon this fight, including switches. Reuse does not stack.",
        "heal.weak" to "Attack ×0.75 for this Pokémon this fight, including switches. Reuse does not stack.",
        "snack.rage" to "All five battle stats ×1.1 this fight.",
        "snack.spd" to "Sp. Def ×1.25 this fight.",
        "snack.spa" to "Sp. Atk ×1.25 this fight.",
        "snack.shield" to "Next damaging hit taken is 30% weaker.",
        "legendaryaura" to "Legendary aura: every stat ×1.5 for this fight",
        "happyhour" to "Doubles the coins earned from this fight.",
        "perishsong" to "Faints when the count ends; switch to escape.",
        "embargo" to "Its held item has no effect.",
        "switchtrap" to "Can't switch; Ghost types can escape.",
        "confusion" to "May hit itself instead of moving.",
        "attract" to "May skip its move because of infatuation.",
        "partiallytrapped" to "Loses 1/8 of max HP per round.",
        "taunt" to "Can't use status moves.",
        "encore" to "Must repeat its last move.",
        "disable" to "Can't use its last move.",
        "torment" to "Can't use the same move twice running.",
        "healblock" to "Can't restore HP.",
        "throatchop" to "Can't use sound moves.",
        "yawn" to "Falls asleep at the end of the next turn.",
        "imprison" to "Can't use moves the opponent also knows.",
        "lockedmove" to "Repeats its move, then becomes confused.",
        "uproar" to "Repeats Uproar and keeps both sides awake.",
        "smackdown" to "Ground moves can hit it.",
        "tarshot" to "Takes double Fire damage.",
        "foresight" to "Ignores Ghost immunity and evasion boosts.",
        "miracleeye" to "Ignores Dark immunity and evasion boosts.",
        "leechseed" to "Loses 1/8 of max HP per round; heals the foe.",
        "curse" to "Loses 1/4 of max HP per round.",
        "nightmare" to "Loses 1/4 of max HP per round while asleep.",
        "saltcure" to "Loses 1/8 of max HP per round; Water or Steel lose 1/4.",
        "syrupbomb" to "Speed drops one stage per round.",
        "substitute" to "A decoy takes hits; sound moves bypass it.",
        "destinybond" to "Its attacker faints too if its next hit KOs.",
        "aquaring" to "Heals 1/16 of max HP per round.",
        "ingrain" to "Heals 1/16 of max HP per round; resists forced escape.",
        "stockpile" to "Stores defenses for Swallow or Spit Up.",
        "magnetrise" to "Avoids Ground moves unless grounded.",
        "telekinesis" to "Floats; most non-Ground moves always hit.",
        "charge" to "Next Electric move deals double damage.",
        "defensecurl" to "Rollout and Ice Ball deal double damage.",
        "minimize" to "Stomping moves always hit for double damage.",
        "powertrick" to "Attack and Defense are swapped.",
        "powershift" to "Swaps offensive and defensive stats.",
        "ab.flashfire" to "Fire attacks deal ×1.5 damage.",
        "ab.gas" to "Suppresses most other abilities.",
        "spikes" to "Hurts grounded switch-ins; layers add damage.",
        "toxicspikes" to "Poisons switch-ins; 2 layers badly poison.",
        "stealthrock" to "Rock damage on entry; follows type matchups.",
        "stickyweb" to "Grounded switch-ins lose one Speed stage.",
        "reflect" to "Physical hits deal half damage.",
        "lightscreen" to "Special hits deal half damage.",
        "auroraveil" to "Physical and special hits deal half damage.",
        "safeguard" to "Blocks opposing status and confusion.",
        "mist" to "Blocks the opponent's stat drops.",
        "tailwind" to "Doubles Speed.",
        "wish" to "Heals half max HP at the end of the next turn.",
        "mp.fs" to "A delayed special hit lands after two turns.",
        "rain" to "Water moves ×1.5 damage; Fire moves ×0.5.",
        "sun" to "Fire damage ×1.5; Water ×0.5; blocks freeze.",
        "sand" to "Sand chips HP; Rock Sp. Def ×1.5.",
        "hail" to "Hurts non-Ice types per round except the last.",
        "snow" to "Ice types' Defense ×1.5; no weather damage.",
        "electric" to "Grounded: Electric damage ×1.3; can't fall asleep.",
        "grassy" to "Boosts Grass; heals grounded Pokémon.",
        "misty" to "Grounded: no status/confusion; Dragon ×½.",
        "psychic" to "Boosts Psychic; blocks priority on the ground.",
        "trickroom" to "Slower Pokémon acts first at equal priority.",
        "gravity" to "Grounds Pokémon and raises accuracy.",
        "magicroom" to "Held items have no effect.",
        "wonderroom" to "Swaps Defense and Sp. Def for both sides.",
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
        }
    }

    fun line(mark: ConditionMarks.Mark): String {
        val detail = if (mark.key.startsWith("stat.")) StatPill.longName(mark.key.removePrefix("stat.")) + " " + StageText.percent(mark.value, mark.key in listOf("stat.accuracy", "stat.evasion")) else mark.line
        return "$detail — ${effect(mark)}"
    }

    fun spoken(mark: ConditionMarks.Mark): String = if (mark.key.startsWith("stat.")) line(mark) else "${mark.spoken}. ${effect(mark)}"
}

/** The stat before and after this stage multiplier; accuracy/evasion have no numeric Pokémon stat. */
internal object ConditionStat {
    fun values(mark: ConditionMarks.Mark, stats: com.husarp.ankigo.game.Stats?): String? {
        val key = mark.key.removePrefix("stat.")
        val stat = when (key) {
            "atk" -> com.husarp.ankigo.game.Stat.ATK
            "def" -> com.husarp.ankigo.game.Stat.DEF
            "spa" -> com.husarp.ankigo.game.Stat.SPA
            "spd" -> com.husarp.ankigo.game.Stat.SPD
            "spe" -> com.husarp.ankigo.game.Stat.SPE
            else -> return null
        }
        val before = stats?.get(stat) ?: return null
        val after = (before * com.husarp.ankigo.game.BattleEngine.stageMult(mark.value)).toInt().coerceAtLeast(1)
        return "$before → $after"
    }
}
