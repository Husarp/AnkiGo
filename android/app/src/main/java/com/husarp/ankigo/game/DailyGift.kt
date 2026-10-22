package com.husarp.ankigo.game

import kotlin.random.Random

// R14-B2-D (PLAN 125 / R10 X3): the daily gift. Reaching the daily goal no longer pays money: the first time on a game day it gives ONE gift
// from a tiered pool, straight into the Bag (a TM into the TMs). Pure Kotlin, no Android.
//
// THE TABLE (numbers are Tuning.GIFT_*). Streak s = the goal streak today (after today's goal), clamped to 1..30:
//   rare     = 3 + 17 * (s - 1) / 29 %
//   uncommon = 22 + 18 * (s - 1) / 29 %
//   common   = the rest
//   day 1: 75 / 22 / 3 %   day 15: ~58 / ~31 / ~11 %   day 30 and later: 40 / 40 / 20 %   (common / uncommon / rare)
// Each entry of a tier is equally likely:
//   common:   3 Poké Balls, 2 Potions, 1 Super Potion
//   uncommon: 2 Great Balls, 1 Hyper Potion, 5 Poké Balls
//   rare:     1 Ultra Ball, 1 evolution stone (a "-stone" evolution item the Shop sells, as the weekend special picks them), 1 TM the player
//             does not own yet (a move of Shop.tmPool; if every TM is owned, or there are none: a stone instead)
// Never a Master Ball, never an item the catalogue (Items.ALL) does not hold. A tier with nothing to give falls back to the tier below.
// The draw is seeded by the game day (Random("gift:<day>")), so a restart, the floating window or the live detector can never change it.

/** The chances in percent of the three tiers on one goal streak; they add up to 100. */
data class GiftChances(val common: Double, val uncommon: Double, val rare: Double)

/** What a draw gave: [count] of the item [item], or the TM of the move [tm] (then [count] is 1). Exactly one of the two is set. */
data class Gift(val item: String?, val tm: String?, val count: Int)

object DailyGift {
    private enum class Tier { COMMON, UNCOMMON, RARE }

    /** The tier chances for the goal streak [streakDays] (clamped to 1..[Tuning.GIFT_MAX_DAYS]): a straight line from day 1 to day 30. */
    fun chances(streakDays: Int): GiftChances {
        val s = streakDays.coerceIn(1, Tuning.GIFT_MAX_DAYS)
        val t = (s - 1).toDouble() / (Tuning.GIFT_MAX_DAYS - 1)
        val rare = Tuning.GIFT_RARE_FIRST + (Tuning.GIFT_RARE_LAST - Tuning.GIFT_RARE_FIRST) * t
        val uncommon = Tuning.GIFT_UNCOMMON_FIRST + (Tuning.GIFT_UNCOMMON_LAST - Tuning.GIFT_UNCOMMON_FIRST) * t
        return GiftChances(common = 100.0 - rare - uncommon, uncommon = uncommon, rare = rare)
    }

    /** The evolution stones a gift can hold: the "-stone" evolution items that have a price in [data] (the weekend special's list), sorted. */
    fun stones(data: GameData): List<String> =
        Items.ALL.values.filter { it.kind == ItemKind.EVOLUTION && it.id.endsWith("-stone") && data.price(it.id) > 0 }.map { it.id }.sorted()

    /** The TMs a gift can hold: the Shop's TM pool without the TMs in [owned] (sorted). */
    fun unownedTms(data: GameData, owned: Set<String>): List<String> = Shop.tmPool(data).filter { it !in owned }

    /**
     * The gift of game day [day] for the goal streak [streakDays], or null when the data gives nothing at all (never with the real pools).
     * Pure and seeded by [day] only (plus what the player owns, for the TM): the same day, streak and TMs always draw the same gift.
     * The pools are parameters so a test can empty one: [common] / [uncommon] / [rareItems] are item to count; an entry whose item is not in
     * the catalogue is skipped.
     */
    fun draw(
        data: GameData, day: String, streakDays: Int, ownedTms: Set<String> = emptySet(),
        common: List<Pair<String, Int>> = Tuning.GIFT_COMMON, uncommon: List<Pair<String, Int>> = Tuning.GIFT_UNCOMMON,
        rareItems: List<Pair<String, Int>> = Tuning.GIFT_RARE_ITEMS,
    ): Gift? {
        val rng = Random(("gift:$day").hashCode())
        val c = chances(streakDays)
        val roll = rng.nextDouble() * 100
        val first = when { roll < c.rare -> Tier.RARE; roll < c.rare + c.uncommon -> Tier.UNCOMMON; else -> Tier.COMMON }
        for (tier in Tier.entries.filter { it.ordinal <= first.ordinal }.reversed()) { // the drawn tier, then the ones below it
            val gift = when (tier) {
                Tier.COMMON -> pick(common, rng)
                Tier.UNCOMMON -> pick(uncommon, rng)
                Tier.RARE -> pickRare(data, ownedTms, rareItems, rng)
            }
            if (gift != null) return gift
        }
        return null
    }

    private fun pick(pool: List<Pair<String, Int>>, rng: Random): Gift? {
        val entries = pool.filter { Items.def(it.first) != null && it.second > 0 }
        if (entries.isEmpty()) return null
        return entries[rng.nextInt(entries.size)].let { Gift(it.first, null, it.second) }
    }

    /** The rare tier: the fixed items, one evolution stone and one TM are equally likely; a TM that cannot be given turns into a stone. */
    private fun pickRare(data: GameData, ownedTms: Set<String>, fixed: List<Pair<String, Int>>, rng: Random): Gift? {
        val items = fixed.filter { Items.def(it.first) != null && it.second > 0 }
        val slot = rng.nextInt(items.size + 2) // 0 until items.size = a fixed item, then the stone, then the TM
        if (slot < items.size) return Gift(items[slot].first, null, items[slot].second)
        if (slot == items.size + 1) {
            val tms = unownedTms(data, ownedTms)
            if (tms.isNotEmpty()) return Gift(null, tms[rng.nextInt(tms.size)], 1)
        }
        val stones = stones(data)
        return if (stones.isEmpty()) null else Gift(stones[rng.nextInt(stones.size)], null, 1)
    }

    /**
     * The once-a-day moment (Game.answer calls it when the daily goal is reached; its own gate, GameState.lastGoalDay, makes it once a
     * day): draws today's gift for the streak the player now has ([GameState.streakDays]), puts it in the Bag (a TM in [GameState.tms])
     * and adds [GameEvent.DailyGift]. Never touches the money. Returns the gift (null = nothing to give, no event).
     */
    fun give(data: GameData, state: GameState, events: MutableList<GameEvent>): Gift? {
        val gift = draw(data, state.day, state.streakDays, state.tms) ?: return null
        if (gift.tm != null) state.tms += gift.tm else Economy.give(state, gift.item!!, gift.count)
        events += GameEvent.DailyGift(gift.item, gift.tm, gift.count, state.streakDays)
        return gift
    }

    // ---- texts ----

    /** "3 Poké Balls", "Great Ball" (one is just its name), "TM Thunderbolt". [data] null = the TM shows its move id. */
    fun name(data: GameData?, item: String?, tm: String?, count: Int): String = when {
        tm != null -> if (data != null) Items.tmName(data, tm) else "TM $tm"
        item != null -> if (count == 1) Items.name(item) else "$count ${ItemInfo.plural(Items.name(item), count)}"
        else -> ""
    }

    /** The battle-log line: "Daily gift: 3 Poké Balls (6-day streak)"; a first day (streak 1 or less) has no streak part. */
    fun logText(name: String, streakDays: Int): String = "Daily gift: $name" + if (streakDays > 1) " ($streakDays-day streak)" else ""

    /** The streak line of the pop-up. */
    fun streakLine(streakDays: Int): String = when {
        streakDays <= 1 -> "Reach your goal tomorrow too for rarer gifts"
        streakDays >= Tuning.GIFT_MAX_DAYS -> "Goal streak: $streakDays days · rarer gifts are at their best now"
        else -> "Goal streak: $streakDays days · rarer gifts as it grows, up to ${Tuning.GIFT_MAX_DAYS} days"
    }

    /** A pool in words for the guide: "3 Poké Balls, 2 Potions or a Super Potion". */
    fun poolText(pool: List<Pair<String, Int>>): String {
        val parts = pool.map { (item, n) -> if (n == 1) withArticle(Items.name(item)) else name(null, item, null, n) }
        return if (parts.size < 2) parts.joinToString("") else parts.dropLast(1).joinToString(", ") + " or " + parts.last()
    }

    /** "a Master Ball", "an Ultra Ball". */
    fun withArticle(name: String): String = if (name.firstOrNull()?.lowercaseChar()?.let { it in "aeiou" } == true) "an $name" else "a $name"
}
