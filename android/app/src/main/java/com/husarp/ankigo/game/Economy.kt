package com.husarp.ankigo.game

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.random.Random

// TICKET T07 owns this file. Public signatures are fixed (other tickets call them); add private helpers freely.

enum class ItemKind { BALL, HEAL, EVOLUTION, HELD, ESCAPE, FOSSIL, AUTO }

/** An item that does something in AnkiGo. Normal prices come from GameData.price (Tuning overrides, then PokeAPI). */
data class ItemDef(
    val id: String,              // PokeAPI item id, e.g. "thunder-stone"
    val kind: ItemKind,
    val heal: Int = 0,           // HEAL: HP restored (Ankimon fixed amounts); Int.MAX_VALUE = full
    val fossil: Int? = null,     // FOSSIL: species revived
    val cures: Boolean = false,  // R17C-6, HEAL: also cures the Main's battle status (Full Restore)
    val note: String = "",       // one short line for the shop/bag ("Heals 20 HP", "Holder gets x1.5 XP", "Evolves some Pokémon")
)

object Items {
    /** Regular balls and optional specials; IDs and inventory counts remain compatible with old saves. */
    private fun balls() = Tuning.BALL_CHANCE.map { (id, pct) ->
        ItemDef(id, ItemKind.BALL, note = if (pct >= 100) "Catch chance $pct %: it always catches." else "Catch chance $pct %. $MISS_RULE")
    } + Tuning.OLD_BALLS.map { id ->
        ItemDef(id, ItemKind.BALL, note = specialNote(id))
    }

    fun specialSummary(id: String): String = when (id) {
        "repeat-ball" -> "Returns ${Tuning.REPEAT_RETURN_MISS_PERCENT}% miss / ${Tuning.REPEAT_RETURN_CATCH_PERCENT}% catch"
        "heal-ball" -> "HP IV +${Tuning.HEAL_IV_BONUS} (max ${Tuning.BALL_IV_CAP})"
        "luxury-ball" -> "All IVs +${Tuning.LUXURY_IV_BONUS} (max ${Tuning.BALL_IV_CAP})"
        "timer-ball" -> "+${Tuning.TIMER_BONUS_PER_ROUND}%/round, max +${Tuning.SPECIAL_BONUS}%"
        else -> "+${Tuning.SPECIAL_BONUS}% " + when (id) {
            "net-ball" -> "on Water/Bug"
            "dive-ball" -> "on Water"
            "dusk-ball" -> "at night"
            "quick-ball" -> "first turn"
            "nest-ball" -> "at Lv ≤${Tuning.NEST_MAX_LEVEL}"
            "premier-ball" -> "on shiny/rare"
            else -> ""
        }
    }

    fun specialNote(id: String): String = if (id == "repeat-ball")
        "Combine with a regular ball: no catch bonus. The Repeat Ball returns with ${Tuning.REPEAT_RETURN_MISS_PERCENT}% chance on a miss, ${Tuning.REPEAT_RETURN_CATCH_PERCENT}% on a catch. The regular ball is always used up."
    else when (id) {
        "heal-ball" -> "Combine with a regular ball: HP IV +${Tuning.HEAL_IV_BONUS}, capped at ${Tuning.BALL_IV_CAP}."
        "luxury-ball" -> "Combine with a regular ball: every IV +${Tuning.LUXURY_IV_BONUS}, capped at ${Tuning.BALL_IV_CAP}."
        else -> "Combine with a regular ball: up to +${Tuning.SPECIAL_BONUS} percentage points (max ${Tuning.BALL_CHANCE_CAP}%). " + when (id) {
            "net-ball" -> "Against Water or Bug Pokémon."
            "dive-ball" -> "Against Water Pokémon."
            "dusk-ball" -> "At night."
            "quick-ball" -> "Before the first battle round."
            "timer-ball" -> "+${Tuning.TIMER_BONUS_PER_ROUND} per completed round."
            "nest-ball" -> "At level ${Tuning.NEST_MAX_LEVEL} or below."
            "premier-ball" -> "Against shiny, Legendary, Mythical or Ultra Beast Pokémon."
            else -> ""
        } + " Chance bonuses do nothing with a Master Ball."
    } + " Both balls are used up, even on a miss."

    /** PLAN 171: what a failed throw costs, said wherever a ball is chosen or explained. */
    const val MISS_RULE = "If it fails, the wild Pokémon runs away."

    // Ankimon's fixed heal amounts (item_window.py hp_heal_items); max-potion / full-restore heal everything.
    private val HEALS = listOf(
        "potion" to 20, "sweet-heart" to 20, "berry-juice" to 20, "fresh-water" to 30, "soda-pop" to 50, "super-potion" to 60,
        "energy-powder" to 60, "lemonade" to 70, "moomoo-milk" to 100, "hyper-potion" to 120, "energy-root" to 120,
        "full-restore" to Int.MAX_VALUE, "max-potion" to Int.MAX_VALUE,
    )

    private val EVOLUTION = listOf(
        "fire-stone", "water-stone", "thunder-stone", "leaf-stone", "moon-stone", "sun-stone", "shiny-stone", "dusk-stone",
        "dawn-stone", "ice-stone", "oval-stone", "razor-claw", "razor-fang", "kings-rock", "metal-coat", "dragon-scale",
        "up-grade", "prism-scale", "deep-sea-tooth", "deep-sea-scale", "protector", "electirizer", "magmarizer", "dubious-disc",
        "reaper-cloth", "sachet", "whipped-dream", "tart-apple", "sweet-apple", "cracked-pot", "chipped-pot", "linking-cord",
        // R17C-6: the nine items whose Pokémon could not evolve before. PokeAPI sells the first six (Shop pool, random finds); Black
        // Augurite, Peat Block and Metal Alloy have price 0 there, so they are only found (random finds, never sold). The Masterpiece Teacup
        // is left out: it evolves Poltchageist-Artisan, a form AnkiGo has no species for, so no Pokémon here could use it.
        "auspicious-armor", "malicious-armor", "galarica-cuff", "galarica-wreath", "syrupy-apple", "unremarkable-teacup",
        "black-augurite", "peat-block", "metal-alloy",
    )

    /**
     * R17C-FIX: items that stay in the catalogue only so a bag (or an Ankimon import) that already holds one keeps it: never sold, never
     * found. The Chipped Pot evolves Polteageist-Antique, a form AnkiGo has no species for, so no Pokémon here can use it.
     */
    val RETIRED: Set<String> = setOf("chipped-pot")

    /** R17C-6: the one healing item that also cures the Main's burn / poison / paralysis / sleep / freeze in a fight (Max Potion heals HP only). */
    const val FULL_RESTORE = "full-restore"

    private val HELD = listOf(
        "lucky-egg" to "Holder gets ×1.5 XP",
        "amulet-coin" to "Main holds it: ×2 money",
        "luck-incense" to "Main holds it: ×2 money",
        "everstone" to "Holder never evolves",
        "macho-brace" to "Main holds it: ×2 EVs",
        "power-weight" to "Main holds it: +8 HP EVs",
        "power-bracer" to "Main holds it: +8 Attack EVs",
        "power-belt" to "Main holds it: +8 Defense EVs",
        "power-lens" to "Main holds it: +8 Sp. Atk EVs",
        "power-band" to "Main holds it: +8 Sp. Def EVs",
        "power-anklet" to "Main holds it: +8 Speed EVs",
        "soothe-bell" to "Main holds it: ×1.5 Bond points",
    )

    private val FOSSILS = listOf(
        "helix-fossil" to 138, "dome-fossil" to 140, "old-amber" to 142, "root-fossil" to 345, "claw-fossil" to 347,
        "skull-fossil" to 408, "armor-fossil" to 410, "cover-fossil" to 564, "plume-fossil" to 566, "jaw-fossil" to 696,
        "sail-fossil" to 698,
    )

    /** The catalogue (Ankimon effects only; battle held items are left out with the simpler engine). */
    val ALL: Map<String, ItemDef> = buildList {
        addAll(balls())
        add(ItemDef(StreakFreeze.ITEM, ItemKind.AUTO, note = "Protects one missed daily goal automatically"))
        HEALS.forEach { (id, hp) ->
            val cures = id == FULL_RESTORE
            add(ItemDef(id, ItemKind.HEAL, heal = hp, cures = cures,
                note = if (cures) "Heals all HP and cures a status" else if (hp == Int.MAX_VALUE) "Heals all HP" else "Heals $hp HP"))
        }
        EVOLUTION.forEach { add(ItemDef(it, ItemKind.EVOLUTION, note = if (it in RETIRED) "Does nothing here" else "Evolves Pokémon that evolve with it")) }
        AbilityAdaptations.berries.keys.forEach { add(ItemDef(it, ItemKind.HELD, note = when (it) {
            "oran-berry" -> "At half HP: heals 10 HP; consumed"
            "sitrus-berry" -> "At half HP: heals ¼ max HP; consumed"
            "figy-berry" -> "At ¼ HP: heals ⅓ max HP; consumed"
            else -> "At ¼ HP: +1 ${when (AbilityAdaptations.berries[it]) { "atk" -> "Attack"; "def" -> "Defense"; "spa" -> "Sp. Atk"; "spd" -> "Sp. Def"; else -> "Speed" }}; consumed"
        })) }
        HELD.forEach { (id, note) -> add(ItemDef(id, ItemKind.HELD, note = note)) }
        listOf("poke-doll", "fluffy-tail").forEach { add(ItemDef(it, ItemKind.ESCAPE, note = "Skip this wild Pokémon")) }
        FOSSILS.forEach { (id, sp) -> add(ItemDef(id, ItemKind.FOSSIL, fossil = sp, note = "Revives an ancient Pokémon at Lv ${Tuning.FOSSIL_LEVEL}")) }
    }.associateBy { it.id }

    fun def(id: String): ItemDef? = ALL[id]

    /** "thunder-stone" -> "Thunder Stone", "kings-rock" -> "King's Rock", "poke-ball" -> "Poké Ball", "up-grade" -> "Up-Grade". */
    fun name(id: String): String = when (id) {
        "kings-rock" -> "King's Rock"
        "up-grade" -> "Up-Grade"
        else -> id.split('-').filter { it.isNotEmpty() }.joinToString(" ") { w ->
            if (w == "poke") "Poké" else w.replaceFirstChar { it.uppercase() }
        }
    }

    /** "TM Thunderbolt" style name for a TM move id. */
    fun tmName(data: GameData, move: String): String = "TM " + (data.move(move)?.name ?: move)

    /** Ankimon daily-shop pool: catalogue items with price > 0 (balls included; the Master Ball and [RETIRED] items are not sold). */
    fun shopPool(data: GameData): List<String> = ALL.keys.filter { it !in RETIRED && it != StreakFreeze.ITEM && data.price(it) > 0 }.sorted()

    /** Ankimon random-item pool: catalogue items except balls and the [RETIRED] items. */
    fun dropPool(): List<String> = ALL.values.filter { it.kind != ItemKind.BALL && it.kind != ItemKind.AUTO && it.id !in RETIRED }.map { it.id }.sorted()
}

object Economy {
    fun give(state: GameState, item: String, n: Int = 1) {
        state.bag[item] = (state.count(item) + n).let { if (item == StreakFreeze.ITEM) it.coerceAtMost(StreakFreeze.CAP) else it }
    }

    /** Removes one; false if none. */
    fun take(state: GameState, item: String): Boolean {
        val n = state.count(item)
        if (n <= 0) return false
        if (n == 1) state.bag.remove(item) else state.bag[item] = n - 1
        return true
    }

    /**
     * Bookkeeping for one counted answer (call after state.reviewsToday was increased):
     * the answer money ([payAnswer]: the day's curve, x2 with Amulet Coin / Luck Incense), then the random item countdown (60..140
     * answers, rolled when 0) -> one random dropPool item (badge 6 first item). Emits Cash, ItemFound, BadgeEarned.
     */
    fun onReview(data: GameData, state: GameState, rng: Random, events: MutableList<GameEvent>) {
        payAnswer(state, events)
        if (state.itemCountdown <= 0) state.itemCountdown = rollCountdown(rng)
        state.itemCountdown--
        if (state.itemCountdown <= 0) {
            val pool = Items.dropPool()
            val item = pool[rng.nextInt(pool.size)]
            give(state, item)
            events += GameEvent.ItemFound(item, state.ownedItemCount(item))
            Badges.award(state, 6, events)
            state.itemCountdown = rollCountdown(rng)
        }
    }

    /**
     * PLAN 198 (it replaced PLAN 125's "the whole amount every Nth time"), the money curve (linear, reset each game day; no hard cap):
     * the day's money comes in tiers of Tuning.CASH_TIER and every counted answer earns Tuning.CASH_PER_ANSWER / tier, where
     * tier = state.cashToday / CASH_TIER + 1 at that moment (cashToday = the day's base money paid so far, before any x2). So an
     * answer is worth 5, 2.5, 1.67, 1.25, 1 ... coins. The earnings are added to one exact running fraction of the day
     * (state.cashFracNum / cashFracDen, shared with the wins, never a Double) and the whole coins in it are paid out at once ([earn]).
     * Amulet Coin / Luck Incense on the main: the paid coins are doubled, and the tiers follow the coins BEFORE the doubling
     * (state.cashToday), so the item doubles the day's money at every tier and never makes the curve steeper sooner.
     * Adds to state.cash and state.cashToday; emits Cash(ANSWER) when at least one whole coin was paid.
     */
    private fun payAnswer(state: GameState, events: MutableList<GameEvent>) {
        earn(state, Tuning.CASH_PER_ANSWER, CashKind.ANSWER, events)
    }

    /**
     * R13 Part C, PLAN 198: a won fight (a wild Pokémon defeated, or caught by the auto rule or a ball; never a miss, an escape or a
     * release) earns Tuning.CASH_PER_WIN / tier on the same curve and the same running fraction as the answers ([payAnswer]), so a
     * win is worth 10, 5, 3.33, 2.5, 2 ... coins. Its money counts toward the day's tiers like an answer's, and Amulet Coin / Luck
     * Incense doubles the paid coins the same way. Emits Cash(DEFEAT) when at least one whole coin was paid.
     */
    fun onWin(state: GameState, events: MutableList<GameEvent>, bondLevel: Int = Bond.level(state.main?.bondPoints ?: 0), rng: Random = Random.Default) {
        StreakFreeze.battleDrop(state, rng, events)
        earn(state, Tuning.CASH_PER_WIN, CashKind.DEFEAT, events)
        // Flat earned bonus, after the daily curve and money items; practice still pays no coins.
        val bonus = Bond.coins(bondLevel)
        if (bonus > 0 && (!state.practiceRewards || Tuning.PRACTICE_PAYS_MONEY)) {
            state.cash += bonus
            state.cashToday += bonus
            events += GameEvent.Cash(bonus, state.cash, CashKind.DEFEAT)
        }
    }

    /**
     * Adds [base] / tier coins to the day's exact fraction and pays out its whole coins: cashToday grows by the paid coins (base),
     * cash by the same coins, x2 with Amulet Coin / Luck Incense on the main. No event when this action paid 0 coins.
     * Overflow guard: if the denominator would pass [MAX_FRAC_DEN] (about 1e12; it cannot in a normal day, the denominators are
     * the least common multiples of the tiers so far), the unpaid rest (under one coin) is rounded DOWN to a denominator of
     * 1,000,000: the money already paid stays exact, at most a millionth of a coin is dropped.
     */
    private fun earn(state: GameState, base: Int, kind: CashKind, events: MutableList<GameEvent>) {
        if (state.practiceRewards && !Tuning.PRACTICE_PAYS_MONEY) return
        val tier = state.cashToday.coerceAtLeast(0).toLong() / Tuning.CASH_TIER + 1
        val oldDen = state.cashFracDen.coerceAtLeast(1)
        var num = state.cashFracNum.coerceAtLeast(0) * tier + base.toLong() * oldDen    // num/oldDen + base/tier = num / (oldDen * tier)
        var den = oldDen * tier
        val g = gcd(num, den)
        num /= g; den /= g
        val paid = num / den
        num %= den
        if (den > MAX_FRAC_DEN) {
            num = java.math.BigInteger.valueOf(num).multiply(java.math.BigInteger.valueOf(1_000_000L)).divide(java.math.BigInteger.valueOf(den)).toLong()
            den = 1_000_000L
            val g2 = gcd(num, den); num /= g2; den /= g2
        }
        state.cashFracNum = num; state.cashFracDen = den
        if (paid <= 0) return
        val coins = paid.toInt()
        val held = state.main?.let { state.activeHeldItem(it) }
        val amount = if (held == "amulet-coin" || held == "luck-incense") coins * 2 else coins
        val bonusAmount = if (state.battle?.field?.player?.get("happyhour") == 1) amount * 2 else amount
        state.cashToday += coins
        state.cash += bonusAmount
        events += GameEvent.Cash(bonusAmount, state.cash, kind)
    }

    private const val MAX_FRAC_DEN = 1_000_000_000_000L

    private fun gcd(a: Long, b: Long): Long { var x = a; var y = b; while (y != 0L) { val t = x % y; x = y; y = t }; return if (x == 0L) 1 else x }

    private fun rollCountdown(rng: Random) = rng.nextInt(Tuning.ITEM_COUNTDOWN.first, Tuning.ITEM_COUNTDOWN.last + 1)
}

object Shop {
    /** PLAN 387d, approved option B: reserved for the weekend, including both money boosters. */
    val weekendPrices: Map<String, Int> = mapOf(
        "lucky-egg" to 6000, "amulet-coin" to 6000, "luck-incense" to 6000,
        "macho-brace" to 2500, "soothe-bell" to 2500,
        "power-weight" to 2000, "power-bracer" to 2000, "power-belt" to 2000,
        "power-lens" to 2000, "power-band" to 2000, "power-anklet" to 2000,
    )
    fun limited(item: String): Boolean = item in weekendPrices || item == "everstone" || item == StreakFreeze.ITEM

    private fun offerKey(state: GameState, item: String): String {
        val date = parse(state.day)
        val offer = if (item in weekendPrices && date != null && date.dayOfWeek == DayOfWeek.SUNDAY) date.minusDays(1).toString() else state.day
        return "$offer:$item"
    }

    fun soldOut(state: GameState, item: String): Boolean = limited(item) && offerKey(state, item) in state.shopPurchases
    fun freezeOnSale(state: GameState): Boolean = state.count(StreakFreeze.ITEM) < StreakFreeze.CAP

    /** 3 + trainerLevel / 5 (Ankimon). */
    fun slots(trainerLevel: Int): Int = Tuning.SHOP_BASE_SLOTS + trainerLevel / Tuning.SHOP_SLOTS_PER_LEVELS

    /** Every move some species can learn by TM (and that exists in data.moves), sorted. */
    fun tmPool(data: GameData): List<String> =
        data.species.values.flatMap { it.tmMoves }.filter { it in data.moves }.distinct().sorted()

    /**
     * A new day draws regular items/TMs plus one regular ball and an optional special.
     * Retired, unsold or weekend-only items are removed from existing stock.
     */
    fun ensureToday(data: GameData, state: GameState) {
        state.shopItems.removeAll { data.price(it) <= 0 || it in Items.RETIRED || it in weekendPrices }
        if (state.shopDay == state.day) {
            // Upgrade an already-open day's old ball offers without changing owned inventory or rerolled items/TMs.
            val balls = dailyBalls(state.day)
            if (state.shopItems.filter { Items.def(it)?.kind == ItemKind.BALL } != balls) {
                state.shopItems.removeAll { Items.def(it)?.kind == ItemKind.BALL }
                state.shopItems.addAll(0, balls)
            }
            return
        }
        // Keep Saturday's allowance through Sunday; rerolls never touch these keys.
        state.shopPurchases.retainAll((weekendPrices.keys + listOf("everstone", StreakFreeze.ITEM)).map { offerKey(state, it) }.toSet())
        state.shopRefreshes = 0
        val (items, tms) = stockFor(data, state.day, state.trainerLevel)
        setStock(state, items, tms)
    }

    /**
     * The date-seeded stock (items, TMs) of game day [day] for a trainer of [trainerLevel]: what [ensureToday] puts in
     * the shop that day. Pure, so the reminders can look at tomorrow's shop. After a reroll the real stock is state.shopItems.
     */
    fun stockFor(data: GameData, day: String, trainerLevel: Int): Pair<List<String>, List<String>> =
        draw(data, Random(day.hashCode()), trainerLevel, day)

    /** Item/TM rerolls preserve the date-seeded daily balls. */
    private fun draw(data: GameData, rng: Random, trainerLevel: Int, day: String): Pair<List<String>, List<String>> {
        val n = slots(trainerLevel)
        val items = dailyPool(data).shuffled(rng).take(n)
        val tms = tmPool(data).shuffled(rng).take(n)
        return dailyBalls(day) + items to tms
    }

    fun dailyBalls(day: String): List<String> {
        val rng = Random(("balls:$day").hashCode())
        fun pick(weights: Map<String, Int>): String {
            var roll = rng.nextInt(weights.values.sum())
            for ((id, weight) in weights) { roll -= weight; if (roll < 0) return id }
            error("Empty weights")
        }
        val regular = pick(Tuning.REGULAR_SHOP_WEIGHTS)
        return listOf(regular) + if (rng.nextInt(100) < Tuning.SPECIAL_SHOP_PERCENT)
            listOf(pick(Tuning.SPECIAL_SHOP_WEIGHTS)) else emptyList()
    }

    fun ultraBallDay(day: String): Boolean = "ultra-ball" in dailyBalls(day)

    private fun dailyPool(data: GameData): List<String> = Items.shopPool(data).filter {
        it !in Tuning.SHOP_BASICS && it !in weekendPrices && Items.def(it)?.kind != ItemKind.BALL
    }

    // ---- always in stock, deal of the day, weekend special (R10 T5; all derived from the game day, nothing is saved) ----

    /** Potions remain on sale every day; balls come from the daily draw. */
    fun basics(): List<String> = Tuning.SHOP_BASICS

    /** A date-seeded offer; the exact percentage survives price rounding. Nothing is persisted. */
    data class Deal(val item: String, val oldPrice: Int, val price: Int, val percent: Int)

    /** Round half up, at least one coin. Use Long so large normal prices cannot overflow. */
    fun dealPrice(normal: Int, percent: Int): Int =
        ((normal.toLong() * (100 - percent) + 50) / 100).coerceAtLeast(1).toInt()

    private fun localHour(now: Long): Int = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)

    /** Local clock periods are independent of the configured game-day start. */
    fun isNight(now: Long): Boolean {
        val hour = localHour(now)
        return hour < 6 || hour >= 21
    }

    fun dealEndsAt(now: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = now
        val hour = get(java.util.Calendar.HOUR_OF_DAY)
        set(java.util.Calendar.HOUR_OF_DAY, if (hour < 6 || hour >= 21) 6 else 21)
        set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        if (timeInMillis <= now) add(java.util.Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    /**
     * The deal of game day [day]: one day-seeded item of the shop's item pool without the basics (items only, no TMs); null
     * when that pool is empty. Fixed within each day/night period, whatever is bought or rerolled; any number of buys at [Deal.price].
     */
    fun dealFor(data: GameData, day: String, now: Long = System.currentTimeMillis()): Deal? {
        val pool = dailyPool(data)
        if (pool.isEmpty()) return null
        val rng = Random(("deal:$day").hashCode())
        val dayIndex = rng.nextInt(pool.size)
        // Continue the existing seed after item selection; refreshes and purchases never reroll it.
        val dayPercent = Tuning.DEAL_PERCENTS[rng.nextInt(Tuning.DEAL_PERCENTS.size)]
        val nightPercent = Tuning.DEAL_PERCENTS[rng.nextInt(Tuning.DEAL_PERCENTS.size)]
        val percent = if (isNight(now)) nightPercent else dayPercent
        val item = pool[(dayIndex + if (isNight(now) && pool.size > 1) 1 else 0) % pool.size]
        val normal = dailyPrice(data, day, item)
        return Deal(item, normal, dealPrice(normal, percent), percent)
    }

    /** The weekend special: a fixed-price exclusive held item, on sale until Sunday. */
    data class Special(val item: String?, val tm: String?, val price: Int, val endsDay: String) {
        val isTm: Boolean get() = tm != null
        /** The item id, or the TM's move id. */
        val id: String get() = tm ?: item.orEmpty()
    }

    private fun parse(day: String): LocalDate? = try { LocalDate.parse(day) } catch (e: DateTimeParseException) { null }

    /** Is game day [day] a Saturday or a Sunday? (The game day already follows the day-start hour.) */
    fun isWeekend(day: String): Boolean = parse(day)?.dayOfWeek.let { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }

    /** One fixed-price, weekend-only held item, seeded by Saturday and unchanged on Sunday. */
    fun weekendFor(data: GameData, day: String): Special? {
        val date = parse(day) ?: return null
        val saturday = when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> date
            DayOfWeek.SUNDAY -> date.minusDays(1)
            else -> return null
        }
        val pool = weekendPrices.keys.filter { data.price(it) > 0 }.sorted()
        if (pool.isEmpty()) return null
        val item = pool[Random(("weekend:$saturday").hashCode()).nextInt(pool.size)]
        return Special(item, null, weekendPrices.getValue(item), saturday.plusDays(1).toString())
    }

    /** Date + item seeded, independent of stock and rerolls. Round half up using the varied price's band. */
    fun dailyPrice(data: GameData, day: String, item: String): Int {
        val normal = data.price(item)
        if (normal <= 0) return normal
        val pct = Random(("price:$day:$item").hashCode()).nextInt(-Tuning.SHOP_VARIATION_PERCENT, Tuning.SHOP_VARIATION_PERCENT + 1)
        val varied = normal.toLong() * (100 + pct)
        val step = if (varied < Tuning.SHOP_ROUND_THRESHOLD * 100L) Tuning.SHOP_ROUND_SMALL else Tuning.SHOP_ROUND_LARGE
        return (((varied + step * 50L) / (step * 100L)) * step).toInt().coerceAtLeast(1)
    }

    /** One price per item per offer: the seeded discount off the daily price, otherwise the daily price. */
    fun priceOf(data: GameData, day: String, item: String, now: Long = System.currentTimeMillis()): Int {
        weekendPrices[item]?.let { return it }
        val deal = dealFor(data, day, now)
        return if (deal != null && deal.item == item) deal.price else dailyPrice(data, day, item)
    }

    fun priceOf(data: GameData, state: GameState, item: String, now: Long = System.currentTimeMillis()): Int =
        if (item == StreakFreeze.ITEM) StreakFreeze.price(state) else priceOf(data, state.day, item, now)

    /**
     * The dot on the Shop tab: true when the tab hasn't been opened on game day [today] ([lastSeenDay] = the game day it was last
     * opened, null = never), so that day's stock and deal are waiting. False while there is no game day yet.
     */
    fun unseen(lastSeenDay: String?, today: String): Boolean = today.isNotEmpty() && lastSeenDay != today

    private fun setStock(state: GameState, items: List<String>, tms: List<String>) {
        state.shopDay = state.day
        state.shopItems.clear(); state.shopItems.addAll(items)
        state.shopTms.clear(); state.shopTms.addAll(tms)
    }

    private fun restock(data: GameData, state: GameState, rng: Random) {
        val (items, tms) = draw(data, rng, state.trainerLevel, state.day)
        setStock(state, items, tms)
    }

    /** What the achievements say after a Shop action (money spent counts; a tier reached comes back as AchievementReached). */
    private fun spent(data: GameData, state: GameState, price: Int, now: Long): List<GameEvent> {
        Achievements.bump(state, Metric.MONEY_SPENT, price.toLong())
        return mutableListOf<GameEvent>().also { Achievements.check(state, data, now, it) }
    }

    /** Buys [quantity] copies of [item] atomically at [priceOf]: from today's stock, the always-on-sale [basics], today's deal or today's weekend item. */
    fun buy(data: GameData, state: GameState, item: String, now: Long = System.currentTimeMillis(), quantity: Int = 1): ActionResult {
        if (quantity !in setOf(1, 3, 5)) return ActionResult(false, "Invalid quantity")
        if (limited(item) && quantity != 1) return ActionResult(false, "Only one per offer")
        if (soldOut(state, item)) return ActionResult(false, "Sold out")
        if (item == StreakFreeze.ITEM && !freezeOnSale(state)) return ActionResult(false, "You already hold 3 freezes")
        val onSale = (item == StreakFreeze.ITEM && freezeOnSale(state)) || (item in state.shopItems && item !in weekendPrices) || item in Tuning.SHOP_BASICS || dealFor(data, state.day, now)?.item == item ||
            weekendFor(data, state.day)?.item == item
        if (!onSale || (item != StreakFreeze.ITEM && data.price(item) <= 0) || item in Items.RETIRED) return ActionResult(false, "Not in today's shop")
        val total = priceOf(data, state, item, now).toLong() * quantity
        if (total > Int.MAX_VALUE) return ActionResult(false, "Not enough money")
        val price = total.toInt()
        if (state.cash < price) return ActionResult(false, "Not enough money (need ${money(price)})")
        state.cash -= price
        StudyStats.addSpent(state, price)
        Economy.give(state, item, quantity)
        if (limited(item)) state.shopPurchases += offerKey(state, item)
        val label = if (quantity == 1) Items.name(item) else "$quantity × ${Items.name(item)}"
        return ActionResult(true, "Bought $label for ${money(price)}", spent(data, state, price, now))
    }

    /** TMs cost 1000, can be owned once, never used up. Today's TMs or today's weekend TM. */
    fun buyTm(data: GameData, state: GameState, move: String, now: Long = System.currentTimeMillis()): ActionResult {
        if (move !in state.shopTms && weekendFor(data, state.day)?.tm != move) return ActionResult(false, "Not in today's shop")
        val name = Items.tmName(data, move)
        if (move in state.tms) return ActionResult(false, "You already own $name")
        val price = Tuning.TM_PRICE
        if (state.cash < price) return ActionResult(false, "Not enough money (need ${money(price)})")
        state.cash -= price
        StudyStats.addSpent(state, price)
        state.tms += move
        return ActionResult(true, "Bought $name for ${money(price)}", spent(data, state, price, now))
    }

    fun refreshPrice(state: GameState): Int = (Tuning.REROLL_PRICE.toLong() +
        (if (state.shopDay == state.day) state.shopRefreshes.toLong() else 0L) * Tuning.REROLL_PRICE_STEP).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Each successful refresh costs one more step, reset at the next game day. */
    fun reroll(data: GameData, state: GameState, rng: Random, now: Long = System.currentTimeMillis()): ActionResult {
        ensureToday(data, state)
        val price = refreshPrice(state)
        if (state.cash < price) return ActionResult(false, "Not enough money (need ${money(price)})")
        state.cash -= price
        StudyStats.addSpent(state, price)
        restock(data, state, rng)
        state.shopRefreshes++
        return ActionResult(true, "New stock for ${money(price)}", spent(data, state, price, now))
    }

    private fun money(n: Int) = if (n == 1) "1 coin" else "$n coins"
}

object Trainer {
    /** floor(50 * level^1.5). */
    fun xpForNext(level: Int): Int = Math.floor(Tuning.TRAINER_XP_BASE * Math.pow(level.toDouble(), Tuning.TRAINER_XP_EXP)).toInt()

    /** Trainer XP for a Defeat by tier (Normal 10, Baby 16, Ultra 30, Legendary 120, Mythical 160, else 0). Emits TrainerLevelUp. */
    fun addDefeatXp(state: GameState, tier: Tier, events: MutableList<GameEvent>) {
        val gain = Tuning.studyXp(Tuning.TRAINER_XP[tier] ?: 0, state.practiceRewards)
        if (gain <= 0) return
        state.trainerXp += gain
        while (state.trainerXp >= xpForNext(state.trainerLevel)) {
            state.trainerXp -= xpForNext(state.trainerLevel)
            state.trainerLevel++
            events += GameEvent.TrainerLevelUp(state.trainerLevel)
        }
    }

    /** Ankimon rank title from caught species, highest level, trainer level, shinies, badges (trainer_functions.py). */
    fun rank(state: GameState): String {
        val caught = state.caught.size
        val top = state.mons.maxOfOrNull { it.level } ?: 0
        val lvl = state.trainerLevel
        val shiny = state.mons.count { it.shiny }
        val badges = state.badges.size
        return when {
            caught >= 900 && top >= 99 && lvl >= 100 && shiny >= 50 -> "Legendary Trainer"
            caught >= 800 && top >= 95 && lvl >= 80 && shiny >= 25 -> "Grand Champion"
            caught >= 700 && top >= 90 && lvl >= 70 && shiny >= 20 -> "Champion"
            caught >= 600 && top >= 80 && lvl >= 60 && shiny >= 10 && badges >= 8 -> "Master Trainer"
            caught >= 500 && top >= 75 && lvl >= 50 && shiny >= 5 && badges > 6 -> "Elite"
            caught >= 400 && top >= 70 && lvl >= 45 && shiny >= 3 && badges > 5 -> "Elite Trainer"
            caught >= 350 && top >= 60 && lvl >= 40 && shiny >= 2 && badges > 4 -> "Advanced Trainer"
            caught >= 300 && top >= 50 && lvl >= 30 && shiny > 0 && badges > 3 -> "Veteran"
            caught >= 250 && top >= 40 && lvl >= 20 && shiny > 0 -> "Skilled Trainer"
            caught >= 150 && top >= 30 && lvl >= 10 -> "Rookie"
            else -> "Novice Trainer"
        }
    }
}

object Badges {
    /** Ankimon badges.json ids 1..20 that AnkiGo can award (no 11 leech, 14/15 eggs). */
    val TITLES: Map<Int, String> = linkedMapOf(
        1 to "100 cards in one day", 2 to "200 cards in one day", 3 to "300 cards in one day", 4 to "500 cards in one day",
        5 to "Levelled up a Pokémon", 6 to "Found the first item", 7 to "First Pokémon caught", 8 to "First Ultra Beast caught",
        9 to "First Legendary caught", 10 to "First Mythical caught", 12 to "1000 cards in one day", 13 to "2000 cards in one day",
        16 to "Evolved a Pokémon", 17 to "First Normal Pokémon caught", 18 to "First Baby Pokémon caught",
        19 to "Revived a fossil", 20 to "Healing does good",
    )

    /** Adds the badge once; emits BadgeEarned the first time and remembers [now] in GameState.badgeAt (old badges have no date). */
    fun award(state: GameState, id: Int, events: MutableList<GameEvent>, now: Long = System.currentTimeMillis(), pokemon: Mon? = null, data: GameData? = null) {
        val title = TITLES[id] ?: return
        if (state.badges.add(id)) {
            state.badgeAt[id] = now
            if (pokemon != null) AchievementPokemon.record(state, "badge/$id", pokemon, data)
            events += GameEvent.BadgeEarned(id, title)
        }
    }

    /** Reviews-in-a-day badges (Tuning.REVIEW_BADGES). */
    fun onReviews(state: GameState, events: MutableList<GameEvent>, now: Long = System.currentTimeMillis()) {
        for ((reviews, id) in Tuning.REVIEW_BADGES) if (state.reviewsToday >= reviews) award(state, id, events, now)
    }
}

/** Between-battle item actions (optional taps in the Bag / Team screens). Balls and escape items go through Game. */
object ItemUse {
    private const val BATTLE_ONLY = "Use it from the battle screen."

    private fun target(state: GameState, uid: String?, defaultMain: Boolean): Mon? =
        if (uid != null) state.mon(uid) else if (defaultMain) state.main else null

    /** R17C-6: the battle status ("brn" "psn" "tox" "par" "slp" "frz") of [mon] when it is the Main in a fight, else null (only the Main has a status, and only in a fight). */
    private fun battleStatus(state: GameState, mon: Mon): String? =
        if (mon.uid == state.main?.uid) state.battle?.player?.status else null

    /** R17C-6: the status word of a cure line ("X's burn was healed."); sleep and freeze use the battle's own lines ("woke up", "thawed out"). */
    private fun cureText(name: String, status: String): String = when (status) {
        "slp" -> "$name woke up."
        "frz" -> "$name thawed out."
        "par" -> "$name's paralysis was healed."
        "psn", "tox" -> "$name's poison was healed."
        else -> "$name's burn was healed."
    }

    /** Can [item] be used on [mon] right now (heal: hurt, or Full Restore with a status in a fight; evolution item: Progression.itemEvolution != null; held: anyone)? */
    fun canUse(data: GameData, state: GameState, item: String, mon: Mon?, now: Long = System.currentTimeMillis()): Boolean {
        val def = Items.def(item) ?: return false
        if (state.count(item) <= 0) return false
        return when (def.kind) {
            ItemKind.HEAL -> (mon ?: state.main)?.let { it.hp < StatCalc.maxHp(data, it) || (def.cures && battleStatus(state, it) != null) } ?: false
            ItemKind.EVOLUTION -> mon != null && Progression.itemEvolution(data, mon, item, now, state.activeHeldItem(mon)) != null
            ItemKind.HELD -> mon != null && mon.heldItem != item
            ItemKind.FOSSIL -> data.species.containsKey(def.fossil)
            ItemKind.BALL, ItemKind.ESCAPE, ItemKind.AUTO -> false
        }
    }

    /**
     * HEAL: heals the target (default the main) by the fixed amount (badge 20). EVOLUTION: evolves via
     * Progression.itemEvolution/evolve. FOSSIL: revives the species at Lv 5 into the collection (badge 19).
     * HELD: gives it to the target (the old held item goes back to the bag). Uses up the item except HELD moves it.
     */
    fun use(data: GameData, state: GameState, item: String, targetUid: String?, rng: Random, now: Long, evolutionTo: Int? = null): ActionResult {
        val def = Items.def(item) ?: return ActionResult(false, "You can't use that.")
        val name = Items.name(item)
        if (state.count(item) <= 0) return ActionResult(false, "You have no $name.")
        val events = mutableListOf<GameEvent>()
        when (def.kind) {
            ItemKind.AUTO -> return ActionResult(false, "Used automatically when a daily goal is missed.")
            ItemKind.BALL, ItemKind.ESCAPE -> return ActionResult(false, BATTLE_ONLY)
            ItemKind.HEAL -> {
                val mon = target(state, targetUid, true) ?: return ActionResult(false, "No Pokémon to heal.")
                val missing = StatCalc.maxHp(data, mon) - mon.hp
                // R17C-6: Full Restore also cures the Main's battle status; at full HP it still works when there is a status to cure.
                val status = if (def.cures) battleStatus(state, mon) else null
                if (missing <= 0 && status == null) return ActionResult(false, "${mon.name} is already healthy.")
                val healed = minOf(missing, def.heal)
                mon.hp += healed
                var message = if (healed > 0) "$name healed ${mon.name} by $healed HP" else "$name cured ${mon.name}"
                if (status != null) {
                    val side = state.battle!!.player
                    side.status = null; side.statusTurns = 0
                    side.volatiles.remove(CoreRules.SLEEP_TURNS) // R17C-FIX6: Rest's fixed sleep length goes with the sleep, like every other cure
                    events += GameEvent.Effect(true, cureText(mon.name, status), mon.hp, StatCalc.maxHp(data, mon), null, EffectKind.STATUS_CURED, status)
                    if (healed > 0) message += " and cured its status"
                }
                Economy.take(state, item)
                StudyStats.addHealItem(state)
                Badges.award(state, 20, events, now, mon, data)
                return used(data, state, now, events, message)
            }
            ItemKind.EVOLUTION -> {
                val mon = target(state, targetUid, false) ?: return ActionResult(false, "Choose a Pokémon first.")
                if (data.sp(mon.species).evolutions.any { Progression.evolutionItem(it) == item && !Bond.allows(mon, it) })
                    return ActionResult(false, "${mon.name} needs Bond ♥2 before evolving.")
                val evo = (if (evolutionTo == null) Progression.itemEvolution(data, mon, item, now, state.activeHeldItem(mon)) else
                    Progression.readyEvolutions(data, state, mon, now).firstOrNull { it.to == evolutionTo && Progression.evolutionItem(it) == item }) ?: return ActionResult(
                    false,
                    when (Progression.itemEvolutionTimeNeeded(data, mon, item, now, state.activeHeldItem(mon))) {
                        "night" -> "${mon.name} evolves with $name only at night."
                        "day" -> "${mon.name} evolves with $name only during the day."
                        else -> "It won't have any effect."
                    },
                )
                val from = mon.name
                Economy.take(state, item)
                Progression.evolve(data, state, mon, evo, rng, events, now)
                return used(data, state, now, events, "$from evolved into ${mon.name}!")
            }
            ItemKind.FOSSIL -> {
                val species = def.fossil?.takeIf { data.species.containsKey(it) } ?: return ActionResult(false, "This fossil can't be revived.")
                val mon = Progression.newMon(data, species, Tuning.FOSSIL_LEVEL, rng, now)
                Economy.take(state, item)
                val added = Catching.addToCollection(data, state, mon, Tier.NORMAL, null, now, events)
                Badges.award(state, 19, events, now, added, data)
                return used(data, state, now, events, "Revived ${added.name}!")
            }
            ItemKind.HELD -> {
                val mon = target(state, targetUid, false) ?: return ActionResult(false, "Choose a Pokémon first.")
                if (mon.heldItem == item) return ActionResult(false, "${mon.name} already holds $name.")
                val old = mon.heldItem
                Economy.take(state, item)
                if (old != null) Economy.give(state, old)
                mon.heldItem = item
                val back = if (old != null) " (${Items.name(old)} went back to the bag)" else ""
                return used(data, state, now, events, "${mon.name} now holds $name$back")
            }
        }
    }

    /** An item was used up (heal, evolution, fossil): counts for the achievements, then checks the tiers (events get the AchievementReached ones). */
    private fun used(data: GameData, state: GameState, now: Long, events: MutableList<GameEvent>, message: String): ActionResult {
        Achievements.bump(state, Metric.ITEMS_USED)
        Achievements.settle(data, state, now, events)
        return ActionResult(true, message, events)
    }

    /** Takes the held item back into the bag. */
    fun takeHeld(state: GameState, uid: String): ActionResult {
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        val held = mon.heldItem ?: return ActionResult(false, "${mon.name} holds nothing.")
        mon.heldItem = null
        Economy.give(state, held)
        return ActionResult(true, "Took ${Items.name(held)} back from ${mon.name}")
    }

    /** Moves one copy atomically; a stale picker cannot take a different item or lose a copy. */
    fun giveHeld(state: GameState, item: String, fromUid: String?, toUid: String): ActionResult {
        if (Items.def(item)?.kind != ItemKind.HELD) return ActionResult(false, "This item can't be held.")
        val target = state.mon(toUid) ?: return ActionResult(false, "Pokémon not found.")
        val source = fromUid?.let { state.mon(it) ?: return ActionResult(false, "Pokémon not found.") }
        if (source != null && source.heldItem != item) return ActionResult(false, "That Pokémon no longer holds this item.")
        if (target.heldItem == item) return ActionResult(false, "${target.name} already holds ${Items.name(item)}.")
        if (source == null && !Economy.take(state, item)) return ActionResult(false, "No free copies in the Bag.")
        val old = target.heldItem
        source?.heldItem = null
        target.heldItem = item
        if (old != null) Economy.give(state, old)
        return ActionResult(true, "${target.name} now holds ${Items.name(item)}" + if (old == null) "" else " (${Items.name(old)} went back to the Bag)")
    }

    /** Teaches an owned TM (not used up) to a Pokémon whose species can learn it; [replaceIndex] when it knows 4 moves. */
    fun teachTm(data: GameData, state: GameState, move: String, uid: String, replaceIndex: Int?, now: Long = System.currentTimeMillis()): ActionResult {
        val tm = Items.tmName(data, move)
        if (move !in state.tms) return ActionResult(false, "You don't own $tm.")
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        val moveName = data.move(move)?.name ?: move
        if (move !in data.sp(mon.species).tmMoves) return ActionResult(false, "${mon.name} can't learn $moveName.")
        if (move in mon.moves) return ActionResult(false, "${mon.name} already knows $moveName.")
        var forgot: String? = null
        if (mon.moves.size < 4) {
            mon.moves += move
        } else {
            if (replaceIndex == null || replaceIndex !in 0..3) return ActionResult(false, "Choose a move to forget.")
            val old = mon.moves[replaceIndex]
            forgot = data.move(old)?.name ?: old
            mon.moves[replaceIndex] = move
        }
        val text = if (forgot == null) "${mon.name} learned $moveName" else "${mon.name} forgot $forgot and learned $moveName"
        val events = mutableListOf<GameEvent>(GameEvent.MoveLearned(mon.uid, mon.name, moveName, forgot))
        AchievementPokemon.record(state, "tms", mon, data)
        Achievements.bump(state, Metric.TMS_TAUGHT)
        Achievements.settle(data, state, now, events)
        return ActionResult(true, text, events)
    }
}
