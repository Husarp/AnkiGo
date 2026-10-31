package com.husarp.ankigo.game

import java.time.Instant
import java.time.ZoneId

/** Secret progress uses the existing saved counters; no battle or reward rules change. */
internal object SecretAchievements {
    fun win(state: GameState, battle: Battle, main: Mon) {
        if (main.level >= battle.wild.level + 20) Achievements.bump(state, Metric.BULLY)
        if (main.hp == 1) Achievements.bump(state, Metric.LAST_BREATH)
        if (battle.wild.level >= main.level + 20) Achievements.bump(state, Metric.DAVID_GOLIATH)
    }

    fun switch(state: GameState, battle: Battle) {
        // Keep only this encounter's switch count; its uid survives a save/load.
        val key = "secret-switch/${battle.wild.uid}"
        state.counters.keys.removeAll { it.startsWith("secret-switch/") && it != key }
        val n = (state.counters[key] ?: 0L) + 1
        state.counters[key] = n
        if (n >= 6) Achievements.bump(state, Metric.REVOLVING_DOOR)
    }

    fun count(state: GameState, now: Long, events: List<GameEvent>) {
        for (e in events) when (e) {
            is GameEvent.Counted -> {
                if (e.cardStreak >= 20) Achievements.bump(state, Metric.COMBO_20)
                if (Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour == 3) Achievements.bump(state, Metric.NIGHT_OWL)
                val n = if (e.button == 4) (state.counters["secret-easy-run"] ?: 0L) + 1 else 0L
                state.counters["secret-easy-run"] = n
                if (n >= 7) Achievements.bump(state, Metric.EASY_STREET)
            }
            is GameEvent.Attack -> if (e.byPlayer && !e.missed && !e.failed && e.category != Category.STATUS &&
                e.targetHp == 0 && e.damage >= e.targetMaxHp) Achievements.bump(state, Metric.ONE_SHOT)
            is GameEvent.Caught -> {
                val key = "secret-caught/${e.species}"
                val n = (state.counters[key] ?: 0L) + 1
                state.counters[key] = n
                if (n >= 5) Achievements.bump(state, Metric.DEJA_VU)
            }
            else -> Unit
        }
    }
}
