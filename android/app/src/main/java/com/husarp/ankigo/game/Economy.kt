package com.husarp.ankigo.game

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.random.Random

// TICKET T07 owns this file. Public signatures are fixed (other tickets call them); add private helpers freely.

enum class ItemKind { BALL, HEAL, EVOLUTION, HELD, ESCAPE, FOSSIL }

/** An item that does something in AnkiGo. Prices are NOT here: they come from PokeAPI items.csv (GameData.price). */
data class ItemDef(
    val id: String,              // PokeAPI item id, e.g. "thunder-stone"
    val kind: ItemKind,
    val heal: Int = 0,           // HEAL: HP restored (Ankimon fixed amounts); Int.MAX_VALUE = full
    val fossil: Int? = null,     // FOSSIL: species revived
    val note: String = "",       // one short line for the shop/bag ("Heals 20 HP", "Holder gets x1.5 XP", "Evolves some Pokémon")
)

object Items {
    /**
     * PLAN 171: the four ball tiers, then Ankimon's other balls (kept so the Ankimon import and balls already in a bag still work; they
     * throw as a Great Ball and are never sold or found). Every ball's text says what a miss costs.
     */
    private fun balls() = Tuning.BALL_CHANCE.map { (id, pct) ->
        ItemDef(id, ItemKind.BALL, note = if (pct >= 100) "Catch chance $pct %: it always catches." else "Catch chance $pct %. $MISS_RULE")
    } + Tuning.OLD_BALLS.map { id ->
        ItemDef(id, ItemKind.BALL, note = "Throws as a ${name(Tuning.OLD_BALL_AS)}: catch chance ${Catching.ballChance(id)} %. $MISS_RULE")
    }

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
    )

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
    )

    private val FOSSILS = listOf(
        "helix-fossil" to 138, "dome-fossil" to 140, "old-amber" to 142, "root-fossil" to 345, "claw-fossil" to 347,
        "skull-fossil" to 408, "armor-fossil" to 410, "cover-fossil" to 564, "plume-fossil" to 566, "jaw-fossil" to 696,
        "sail-fossil" to 698,
    )

    /** The catalogue (Ankimon effects only; battle held items are left out with the simpler engine). */
    val ALL: Map<String, ItemDef> = buildList {
        addAll(balls())
        HEALS.forEach { (id, hp) ->
            add(ItemDef(id, ItemKind.HEAL, heal = hp, note = if (hp == Int.MAX_VALUE) "Heals all HP" else "Heals $hp HP"))
        }
        EVOLUTION.forEach { add(ItemDef(it, ItemKind.EVOLUTION, note = "Evolves Pokémon that evolve with it")) }
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

    /** Ankimon daily-shop pool: catalogue items with price > 0 (balls included; the Master Ball and the old Ankimon balls have price 0). */
    fun shopPool(data: GameData): List<String> = ALL.keys.filter { data.price(it) > 0 }.sorted()

    /** Ankimon random-item pool: catalogue items except balls. */
    fun dropPool(): List<String> = ALL.values.filter { it.kind != ItemKind.BALL }.map { it.id }.sorted()
}

object Economy {
    fun give(state: GameState, item: String, n: Int = 1) { state.bag[item] = state.count(item) + n }

    /** Removes one; false if none. */
    fun take(state: GameState, item: String): Boolean {
        val n = state.count(item)
        if (n <= 0) return false
        if (n == 1) state.bag.remove(item) else state.bag[item] = n - 1
        return true
    }

    /**
     * Bookkeeping for one counted answer (call after state.reviewsToday was increased):
     * the answer money ([payAnswer]: the day's curve, x2 with Amulet Coin / Luck Incense), then the random item countdown (3..385
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
            events += GameEvent.ItemFound(item, state.count(item))
            Badges.award(state, 6, events)
            state.itemCountdown = rollCountdown(rng)
        }
    }

    /**
     * PLAN 198 (it replaced PLAN 125's "the whole amount every Nth time"), the money curve (linear, reset each game day; no hard cap):
     * the day's money comes in tiers of Tuning.CASH_TIER and every counted answer earns Tuning.CASH_PER_ANSWER / tier, where
     * tier = state.cashToday / CASH_TIER + 1 at that moment (cashToday = the day's base money paid so far, before any x2). So an
     * answer is worth 5, 2.5, 1.67, 1.25, 1 ... ₽. The earnings are added to one exact running fraction of the day
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
     * win is worth 10, 5, 3.33, 2.5, 2 ... ₽. Its money counts toward the day's tiers like an answer's, and Amulet Coin / Luck
     * Incense doubles the paid coins the same way. Emits Cash(DEFEAT) when at least one whole coin was paid.
     */
    fun onWin(state: GameState, events: MutableList<GameEvent>) {
        earn(state, Tuning.CASH_PER_WIN, CashKind.DEFEAT, events)
    }

    /**
     * Adds [base] / tier ₽ to the day's exact fraction and pays out its whole coins: cashToday grows by the paid coins (base),
     * cash by the same coins, x2 with Amulet Coin / Luck Incense on the main. No event when this action paid 0 coins.
     * Overflow guard: if the denominator would pass [MAX_FRAC_DEN] (about 1e12; it cannot in a normal day, the denominators are
     * the least common multiples of the tiers so far), the unpaid rest (under one coin) is rounded DOWN to a denominator of
     * 1,000,000: the money already paid stays exact, at most a millionth of a coin is dropped.
     */
    private fun earn(state: GameState, base: Int, kind: CashKind, events: MutableList<GameEvent>) {
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
        val held = state.main?.heldItem
        val amount = if (held == "amulet-coin" || held == "luck-incense") coins * 2 else coins
        state.cashToday += coins
        state.cash += amount
        events += GameEvent.Cash(amount, state.cash, kind)
    }

    private const val MAX_FRAC_DEN = 1_000_000_000_000L

    private fun gcd(a: Long, b: Long): Long { var x = a; var y = b; while (y != 0L) { val t = x % y; x = y; y = t }; return if (x == 0L) 1 else x }

    private fun rollCountdown(rng: Random) = rng.nextInt(Tuning.ITEM_COUNTDOWN.first, Tuning.ITEM_COUNTDOWN.last + 1)
}

object Shop {
    /** 3 + trainerLevel / 5 (Ankimon). */
    fun slots(trainerLevel: Int): Int = Tuning.SHOP_BASE_SLOTS + trainerLevel / Tuning.SHOP_SLOTS_PER_LEVELS

    /** Every move some species can learn by TM (and that exists in data.moves), sorted. */
    fun tmPool(data: GameData): List<String> =
        data.species.values.flatMap { it.tmMoves }.filter { it in data.moves }.distinct().sorted()

    /**
     * If state.shopDay != state.day: today's stock = date-seeded sample of slots() items and slots() TMs (+ the Ultra Ball on its days).
     * PLAN 171: an item that is no longer sold (an old Ankimon ball drawn before the update) leaves today's stock.
     */
    fun ensureToday(data: GameData, state: GameState) {
        state.shopItems.removeAll { data.price(it) <= 0 }
        if (state.shopDay == state.day) return
        val (items, tms) = stockFor(data, state.day, state.trainerLevel)
        setStock(state, items, tms)
    }

    /**
     * The date-seeded stock (items, TMs) of game day [day] for a trainer of [trainerLevel]: what [ensureToday] puts in
     * the shop that day. Pure, so the reminders can look at tomorrow's shop. After a reroll the real stock is state.shopItems.
     */
    fun stockFor(data: GameData, day: String, trainerLevel: Int): Pair<List<String>, List<String>> =
        draw(data, Random(day.hashCode()), trainerLevel, day)

    /** slots() items and TMs drawn with [rng]; on an [ultraBallDay] the Ultra Ball comes first, on top of the drawn items (a reroll keeps it). */
    private fun draw(data: GameData, rng: Random, trainerLevel: Int, day: String): Pair<List<String>, List<String>> {
        val n = slots(trainerLevel)
        val items = dailyPool(data).shuffled(rng).take(n)
        val tms = tmPool(data).shuffled(rng).take(n)
        val ultra = if (ultraBallDay(day) && data.price(ULTRA_BALL) > 0) listOf(ULTRA_BALL) else emptyList()
        return ultra + items to tms
    }

    private const val ULTRA_BALL = "ultra-ball"

    /**
     * PLAN 171: is the Ultra Ball in the Shop on game day [day]? About one day in Tuning.ULTRA_BALL_EVERY_DAYS, seeded by the date like
     * the rest of the stock, so a reroll neither brings it nor takes it away.
     */
    fun ultraBallDay(day: String): Boolean = Random(("ultra:$day").hashCode()).nextInt(Tuning.ULTRA_BALL_EVERY_DAYS) == 0

    /**
     * The items the daily draw and the deal pick from: the shop's item pool minus the always-on-sale [basics] and the Ultra Ball
     * (PLAN 171: it has its own rare days, [ultraBallDay], and is never the deal).
     */
    private fun dailyPool(data: GameData): List<String> = Items.shopPool(data).filter { it !in Tuning.SHOP_BASICS && it != ULTRA_BALL }

    // ---- always in stock, deal of the day, weekend special (R10 T5; all derived from the game day, nothing is saved) ----

    /** Poké Ball and Potion: on sale every day at their normal price, whatever the daily stock is. */
    fun basics(): List<String> = Tuning.SHOP_BASICS

    /** The deal of the day: [item] at [price] instead of [oldPrice] (-Tuning.DEAL_PERCENT %, at least 1). */
    data class Deal(val item: String, val oldPrice: Int, val price: Int)

    /** The deal price for a [normal] price: round(normal * (100 - DEAL_PERCENT) / 100), half up, at least 1. */
    fun dealPrice(normal: Int): Int = ((normal * (100 - Tuning.DEAL_PERCENT) + 50) / 100).coerceAtLeast(1)

    /**
     * The deal of game day [day]: one day-seeded item of the shop's item pool without the basics (items only, no TMs); null
     * when that pool is empty. The same all day, whatever is bought or rerolled; any number of buys at [Deal.price].
     */
    fun dealFor(data: GameData, day: String): Deal? {
        val pool = dailyPool(data)
        if (pool.isEmpty()) return null
        val item = pool[Random(("deal:$day").hashCode()).nextInt(pool.size)]
        val normal = data.price(item)
        return Deal(item, normal, dealPrice(normal))
    }

    /** The weekend special: an evolution stone ([item]) or a strong TM ([tm]; exactly one is set) at its normal [price], on sale until [endsDay] (the Sunday). */
    data class Special(val item: String?, val tm: String?, val price: Int, val endsDay: String) {
        val isTm: Boolean get() = tm != null
        /** The item id, or the TM's move id. */
        val id: String get() = tm ?: item.orEmpty()
    }

    private fun parse(day: String): LocalDate? = try { LocalDate.parse(day) } catch (e: DateTimeParseException) { null }

    /** Is game day [day] a Saturday or a Sunday? (The game day already follows the day-start hour.) */
    fun isWeekend(day: String): Boolean = parse(day)?.dayOfWeek.let { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }

    /**
     * The weekend special of game day [day] (null on weekdays, for a day that isn't a date, or when the data has neither a priced
     * stone nor a strong TM). Seeded by that weekend's Saturday, so Saturday and Sunday show the same one. The kind (stone or
     * TM) is drawn first; an empty pool falls back to the other kind.
     */
    fun weekendFor(data: GameData, day: String): Special? {
        val date = parse(day) ?: return null
        val saturday = when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> date
            DayOfWeek.SUNDAY -> date.minusDays(1)
            else -> return null
        }
        val stones = Items.ALL.values.filter { it.kind == ItemKind.EVOLUTION && it.id.endsWith("-stone") && data.price(it.id) > 0 }.map { it.id }.sorted()
        val tms = tmPool(data).filter { (data.moves[it]?.power ?: 0) >= Tuning.WEEKEND_TM_MIN_POWER }
        val rng = Random(("weekend:$saturday").hashCode())
        val wantStone = rng.nextBoolean()
        val ends = saturday.plusDays(1).toString()
        return when {
            stones.isEmpty() && tms.isEmpty() -> null
            tms.isEmpty() || (wantStone && stones.isNotEmpty()) ->
                stones[rng.nextInt(stones.size)].let { Special(it, null, data.price(it), ends) }
            else -> tms[rng.nextInt(tms.size)].let { Special(null, it, Tuning.TM_PRICE, ends) }
        }
    }

    /** What an item costs on game day [day]: the deal price for that day's deal item, else its normal price. One price per item per day. */
    fun priceOf(data: GameData, day: String, item: String): Int {
        val deal = dealFor(data, day)
        return if (deal != null && deal.item == item) deal.price else data.price(item)
    }

    fun priceOf(data: GameData, state: GameState, item: String): Int = priceOf(data, state.day, item)

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

    /** Buys one [item] for [priceOf]: from today's stock, the always-on-sale [basics], today's deal or today's weekend stone. */
    fun buy(data: GameData, state: GameState, item: String, now: Long = System.currentTimeMillis()): ActionResult {
        val onSale = item in state.shopItems || item in Tuning.SHOP_BASICS || dealFor(data, state.day)?.item == item ||
            weekendFor(data, state.day)?.item == item
        if (!onSale || data.price(item) <= 0) return ActionResult(false, "Not in today's shop")
        val price = priceOf(data, state, item)
        if (state.cash < price) return ActionResult(false, "Not enough money (need ${money(price)})")
        state.cash -= price
        StudyStats.addSpent(state, price)
        Economy.give(state, item)
        return ActionResult(true, "Bought ${Items.name(item)} for ${money(price)}", spent(data, state, price, now))
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

    /** 100: a fresh random stock for today. */
    fun reroll(data: GameData, state: GameState, rng: Random, now: Long = System.currentTimeMillis()): ActionResult {
        val price = Tuning.REROLL_PRICE
        if (state.cash < price) return ActionResult(false, "Not enough money (need ${money(price)})")
        state.cash -= price
        StudyStats.addSpent(state, price)
        restock(data, state, rng)
        return ActionResult(true, "New stock for ${money(price)}", spent(data, state, price, now))
    }

    private fun money(n: Int) = "$n ₽"
}

object Trainer {
    /** floor(50 * level^1.5). */
    fun xpForNext(level: Int): Int = Math.floor(Tuning.TRAINER_XP_BASE * Math.pow(level.toDouble(), Tuning.TRAINER_XP_EXP)).toInt()

    /** Trainer XP for a Defeat by tier (Normal 10, Baby 16, Ultra 30, Legendary 120, Mythical 160, else 0). Emits TrainerLevelUp. */
    fun addDefeatXp(state: GameState, tier: Tier, events: MutableList<GameEvent>) {
        val gain = Tuning.TRAINER_XP[tier] ?: 0
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
    fun award(state: GameState, id: Int, events: MutableList<GameEvent>, now: Long = System.currentTimeMillis()) {
        val title = TITLES[id] ?: return
        if (state.badges.add(id)) {
            state.badgeAt[id] = now
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

    /** Can [item] be used on [mon] right now (heal: hurt; evolution item: Progression.itemEvolution != null; held: anyone)? */
    fun canUse(data: GameData, state: GameState, item: String, mon: Mon?, now: Long = System.currentTimeMillis()): Boolean {
        val def = Items.def(item) ?: return false
        if (state.count(item) <= 0) return false
        return when (def.kind) {
            ItemKind.HEAL -> (mon ?: state.main)?.let { it.hp < StatCalc.maxHp(data, it) } ?: false
            ItemKind.EVOLUTION -> mon != null && Progression.itemEvolution(data, mon, item, now) != null
            ItemKind.HELD -> mon != null && mon.heldItem != item
            ItemKind.FOSSIL -> data.species.containsKey(def.fossil)
            ItemKind.BALL, ItemKind.ESCAPE -> false
        }
    }

    /**
     * HEAL: heals the target (default the main) by the fixed amount (badge 20). EVOLUTION: evolves via
     * Progression.itemEvolution/evolve. FOSSIL: revives the species at Lv 5 into the collection (badge 19).
     * HELD: gives it to the target (the old held item goes back to the bag). Uses up the item except HELD moves it.
     */
    fun use(data: GameData, state: GameState, item: String, targetUid: String?, rng: Random, now: Long): ActionResult {
        val def = Items.def(item) ?: return ActionResult(false, "You can't use that.")
        val name = Items.name(item)
        if (state.count(item) <= 0) return ActionResult(false, "You have no $name.")
        val events = mutableListOf<GameEvent>()
        when (def.kind) {
            ItemKind.BALL, ItemKind.ESCAPE -> return ActionResult(false, BATTLE_ONLY)
            ItemKind.HEAL -> {
                val mon = target(state, targetUid, true) ?: return ActionResult(false, "No Pokémon to heal.")
                val missing = StatCalc.maxHp(data, mon) - mon.hp
                if (missing <= 0) return ActionResult(false, "${mon.name} is already healthy.")
                val healed = minOf(missing, def.heal)
                mon.hp += healed
                Economy.take(state, item)
                StudyStats.addHealItem(state)
                Badges.award(state, 20, events, now)
                return used(data, state, now, events, "$name healed ${mon.name} by $healed HP")
            }
            ItemKind.EVOLUTION -> {
                val mon = target(state, targetUid, false) ?: return ActionResult(false, "Choose a Pokémon first.")
                val evo = Progression.itemEvolution(data, mon, item, now) ?: return ActionResult(
                    false,
                    when (Progression.itemEvolutionTimeNeeded(data, mon, item, now)) {
                        "night" -> "${mon.name} evolves with $name only at night."
                        "day" -> "${mon.name} evolves with $name only during the day."
                        else -> "It won't have any effect."
                    },
                )
                val from = mon.name
                Economy.take(state, item)
                Progression.evolve(data, state, mon, evo, rng, events)
                return used(data, state, now, events, "$from evolved into ${mon.name}!")
            }
            ItemKind.FOSSIL -> {
                val species = def.fossil?.takeIf { data.species.containsKey(it) } ?: return ActionResult(false, "This fossil can't be revived.")
                val mon = Progression.newMon(data, species, Tuning.FOSSIL_LEVEL, rng, now)
                Economy.take(state, item)
                val added = Catching.addToCollection(data, state, mon, Tier.NORMAL, null, now, events)
                Badges.award(state, 19, events, now)
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
        Achievements.bump(state, Metric.TMS_TAUGHT)
        Achievements.settle(data, state, now, events)
        return ActionResult(true, text, events)
    }
}
