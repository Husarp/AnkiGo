package com.husarp.ankigo.game

import kotlin.math.max
import kotlin.math.min

/**
 * AnkiGo's own healing rules (PLAN 80; Ankimon has none of these). Whole HP everywhere, never above max HP:
 * - level up: the HP the level adds to max HP, plus 25 % of the new max HP ([levelUpHp]);
 * - defeat or catch: the main heals 10 % of max HP ([healMain]);
 * - bench rest: team members that aren't the main heal 0.5 % of max HP per counted answer, the fraction kept in
 *   [Mon.restFrac] so no whole point is lost ([restBench]).
 * Kept as before: the full heal of a fainted main and of an evolution, items, healing moves.
 */
object Healing {
    /** [percent] % of [maxHp] in whole HP, at least 1. */
    fun share(maxHp: Int, percent: Int): Int = max(1, maxHp * percent / 100)

    /**
     * Current HP after one level gained: [hp] + the max-HP increase ([oldMax] to [newMax]) + 25 % of [newMax], capped at
     * [newMax]. A Pokémon at 0 HP is not revived by levelling (a fainted main is healed by the game, see Game.answer).
     */
    fun levelUpHp(hp: Int, oldMax: Int, newMax: Int): Int {
        if (hp <= 0) return hp
        return min(newMax, hp + max(0, newMax - oldMax) + share(newMax, Tuning.LEVEL_UP_HEAL_PERCENT))
    }

    /**
     * The main heals 10 % of its max HP after a defeat or a catch ([reason]); emits [GameEvent.Healed] with the HP really
     * gained, none when it gained nothing (already full, or fainted: a fainted main is fully healed by Game.answer instead).
     */
    fun healMain(data: GameData, main: Mon, reason: HealReason, events: MutableList<GameEvent>) {
        if (main.hp <= 0) return
        val maxHp = StatCalc.maxHp(data, main)
        val gain = min(share(maxHp, Tuning.DEFEAT_HEAL_PERCENT), maxHp - main.hp)
        if (gain <= 0) return
        main.hp += gain
        events += GameEvent.Healed(main.uid, main.name, gain, main.hp, maxHp, reason)
    }

    /** One counted answer: every team member that isn't the main rests ([rest]). Box Pokémon never rest. */
    fun restBench(data: GameData, state: GameState) {
        val mainUid = state.team.firstOrNull()
        for (uid in state.team) {
            if (uid == mainUid) continue
            state.mon(uid)?.let { rest(data, it) }
        }
    }

    /**
     * [mon] gains max HP / 200 for this answer: `restFrac += maxHp; hp += restFrac / 200; restFrac %= 200`. At max HP nothing
     * builds up (restFrac 0); reaching max HP drops the leftover fraction.
     */
    fun rest(data: GameData, mon: Mon) {
        val maxHp = StatCalc.maxHp(data, mon)
        if (mon.hp >= maxHp) { mon.restFrac = 0; return }
        val total = mon.restFrac.coerceIn(0, Tuning.BENCH_REST_DIVISOR - 1) + maxHp
        mon.hp = min(maxHp, mon.hp + total / Tuning.BENCH_REST_DIVISOR)
        mon.restFrac = if (mon.hp >= maxHp) 0 else total % Tuning.BENCH_REST_DIVISOR
    }
}
