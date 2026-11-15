package com.husarp.ankigo.game

import java.time.LocalDate
import kotlin.random.Random

object BerryFeeding {
    fun feed(data: GameData, state: GameState, item: String, uid: String, rng: Random, now: Long = System.currentTimeMillis()): ActionResult {
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        val day = LocalDate.parse(state.day).toEpochDay()
        val key = "berry-fed/$uid"
        val count = if (state.counters["$key/day"] == day) state.counters["$key/count"] ?: 0 else 0
        if (count >= 2) return ActionResult(false, "${mon.shownName} isn't hungry right now.")
        if (item !in AbilityAdaptations.berries || state.count(item) <= 0) return ActionResult(false, "No berry in the Bag.")
        Economy.take(state, item)
        state.counters["$key/day"] = day; state.counters["$key/count"] = count + 1
        val events = mutableListOf<GameEvent>()
        Bond.add(data, state, mon, 5, events)
        var message = "Fed ${Items.name(item)} to ${mon.shownName}. +5 Bond points."
        if (rng.nextInt(100) < 15) when (rng.nextInt(3)) {
            0 -> {
                if (mon.level < Tuning.MAX_LEVEL) {
                    Progression.gainXp(data, state, mon, 20, events); message += " A gift: 20 XP!"
                } else {
                    state.cash = (state.cash.toLong() + 5).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(); message += " A gift: 5 coins!"
                }
            }
            1 -> { val coins = minOf(5, Int.MAX_VALUE - state.cash); state.cash += coins; message += " A gift: $coins coins!" }
            else -> {
                val stat = Stat.entries[rng.nextInt(Stat.entries.size)]
                val n = mon.evs[stat]
                if (n < Tuning.EV_STAT_CAP && mon.evs.total < Tuning.EV_TOTAL_CAP) {
                    mon.evs = mon.evs.with(stat, n + 1)
                    message += " A gift: +1 ${stat.name} EV!"
                } else { state.cash = (state.cash.toLong() + 5).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(); message += " A gift: 5 coins!" }
            }
        }
        Achievements.bump(state, Metric.ITEMS_USED)
        Achievements.settle(data, state, now, events)
        return ActionResult(true, message, events)
    }
}

object BagSell {
    fun allowed(item: String): Boolean = Items.def(item) != null && item != PokeDoll.ITEM && item != StreakFreeze.ITEM
    fun price(data: GameData, item: String): Int = (data.price(item) / 10).coerceAtLeast(1)
    fun sell(data: GameData, state: GameState, item: String, quantity: Int): ActionResult {
        if (!allowed(item)) return ActionResult(false, "This item can't be sold.")
        if (quantity !in setOf(1, 3, 5) || state.count(item) < quantity) return ActionResult(false, "Not enough copies in the Bag.")
        val total = price(data, item).toLong() * quantity
        if (total + state.cash > Int.MAX_VALUE) return ActionResult(false, "Your coin pouch is full.")
        repeat(quantity) { Economy.take(state, item) }
        state.cash += total.toInt()
        return ActionResult(true, "Sold $quantity × ${Items.name(item)} for $total coins.")
    }
}
