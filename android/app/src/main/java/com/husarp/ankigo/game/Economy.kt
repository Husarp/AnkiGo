package com.husarp.ankigo.game

import kotlin.random.Random

// TICKET T07 owns this file. Public signatures are fixed (other tickets call them); add private helpers freely.

enum class ItemKind { BALL, HEAL, EVOLUTION, HELD, ESCAPE, FOSSIL }

/** An item that does something in AnkiGo. Prices are NOT here: they come from PokeAPI items.csv (GameData.price). */
data class ItemDef(
    val id: String,              // PokeAPI item id, e.g. "thunder-stone"
    val kind: ItemKind,
    val heal: Int = 0,           // HEAL: HP restored (Ankimon fixed amounts); Int.MAX_VALUE = full
    val fossil: Int? = null,     // FOSSIL: species revived
    val note: String = "",       // one short line for the shop/bag ("Heals 20 HP", "Held: x1.5 XP", "Evolves some Pokémon")
)

object Items {
    private fun balls() = Tuning.BALL_CHANCE.map { (id, pct) -> ItemDef(id, ItemKind.BALL, note = "Catch chance $pct%") }

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
        "lucky-egg" to "Held: ×1.5 XP",
        "amulet-coin" to "Held: ×2 cash",
        "luck-incense" to "Held: ×2 cash",
        "everstone" to "Held: never evolves",
        "macho-brace" to "Held: ×2 EVs",
        "power-weight" to "Held: +8 HP EVs per defeat",
        "power-bracer" to "Held: +8 Atk EVs per defeat",
        "power-belt" to "Held: +8 Def EVs per defeat",
        "power-lens" to "Held: +8 Sp. Atk EVs per defeat",
        "power-band" to "Held: +8 Sp. Def EVs per defeat",
        "power-anklet" to "Held: +8 Speed EVs per defeat",
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
        EVOLUTION.forEach { add(ItemDef(it, ItemKind.EVOLUTION, note = "Evolves certain Pokémon")) }
        HELD.forEach { (id, note) -> add(ItemDef(id, ItemKind.HELD, note = note)) }
        listOf("poke-doll", "fluffy-tail").forEach { add(ItemDef(it, ItemKind.ESCAPE, note = "Escape: a new wild Pokémon appears")) }
        FOSSILS.forEach { (id, sp) -> add(ItemDef(id, ItemKind.FOSSIL, fossil = sp, note = "Revives a fossil Pokémon")) }
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

    /** Ankimon daily-shop pool: catalogue items with price > 0 (balls included; Master Ball has price 0). */
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
     * +5 cash for the answer (any button), then the random item countdown (3..385 answers, rolled when 0) -> one random
     * dropPool item (badge 6 first item). Money goes through [earn] (x2 with Amulet Coin / Luck Incense, daily cap).
     * Emits Cash, ItemFound, BadgeEarned.
     */
    fun onReview(data: GameData, state: GameState, rng: Random, events: MutableList<GameEvent>) {
        earn(state, Tuning.CASH_PER_ANSWER, CashKind.ANSWER, events)
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
     * The defeat money: +10 cash for a wild Pokémon that was defeated (call for Outcome.DEFEAT only: a catch pays nothing).
     * Same doubling and daily cap as the answer money. Emits Cash.
     */
    fun onDefeat(state: GameState, events: MutableList<GameEvent>) = earn(state, Tuning.CASH_PER_DEFEAT, CashKind.DEFEAT, events)

    /**
     * Pays [base] cash for [kind] (ANSWER or DEFEAT): x2 when the main holds Amulet Coin or Luck Incense, then at most what
     * is left of today's cap (Tuning.CASH_DAILY_CAP counts what is really paid, after the doubling). Nothing left: no
     * payment and no event. Adds to state.cash and state.cashToday; emits Cash.
     */
    private fun earn(state: GameState, base: Int, kind: CashKind, events: MutableList<GameEvent>) {
        val held = state.main?.heldItem
        val want = if (held == "amulet-coin" || held == "luck-incense") base * 2 else base
        val amount = minOf(want, Tuning.CASH_DAILY_CAP - state.cashToday)
        if (amount <= 0) return
        state.cash += amount
        state.cashToday += amount
        events += GameEvent.Cash(amount, state.cash, kind)
    }

    /** The daily-goal bonus for [streakBefore] goal-streak days before today: 50 + 5 per day, 10 days at most (so 50..100). */
    fun dailyBonus(streakBefore: Int): Int =
        Tuning.DAILY_BONUS_BASE + Tuning.DAILY_BONUS_PER_DAY * streakBefore.coerceIn(0, Tuning.DAILY_BONUS_MAX_DAYS)

    /**
     * Pays the daily-goal bonus once (the caller is the one-a-day "Daily goal reached" moment): never doubled, outside the
     * daily cap (state.cashToday stays as it is), paid even when the cap is reached. Emits Cash(DAILY_BONUS).
     */
    fun payDailyBonus(state: GameState, streakBefore: Int, events: MutableList<GameEvent>) {
        val amount = dailyBonus(streakBefore)
        state.cash += amount
        events += GameEvent.Cash(amount, state.cash, CashKind.DAILY_BONUS)
    }

    /** The log line of the daily bonus: "Daily goal bonus: +75 ₽ (6-day streak)"; a first day (streak 1 or less) has no streak part. */
    fun dailyBonusText(amount: Int, streakDays: Int): String =
        "Daily goal bonus: +$amount ₽" + if (streakDays > 1) " ($streakDays-day streak)" else ""

    private fun rollCountdown(rng: Random) = rng.nextInt(Tuning.ITEM_COUNTDOWN.first, Tuning.ITEM_COUNTDOWN.last + 1)
}

object Shop {
    /** 3 + trainerLevel / 5 (Ankimon). */
    fun slots(trainerLevel: Int): Int = Tuning.SHOP_BASE_SLOTS + trainerLevel / Tuning.SHOP_SLOTS_PER_LEVELS

    /** Every move some species can learn by TM (and that exists in data.moves), sorted. */
    fun tmPool(data: GameData): List<String> =
        data.species.values.flatMap { it.tmMoves }.filter { it in data.moves }.distinct().sorted()

    /** If state.shopDay != state.day: today's stock = date-seeded sample of slots() items and slots() TMs. */
    fun ensureToday(data: GameData, state: GameState) {
        if (state.shopDay == state.day) return
        val (items, tms) = stockFor(data, state.day, state.trainerLevel)
        setStock(state, items, tms)
    }

    /**
     * The date-seeded stock (items, TMs) of game day [day] for a trainer of [trainerLevel]: what [ensureToday] puts in
     * the shop that day. Pure, so the reminders can look at tomorrow's shop. After a reroll the real stock is state.shopItems.
     */
    fun stockFor(data: GameData, day: String, trainerLevel: Int): Pair<List<String>, List<String>> =
        draw(data, Random(day.hashCode()), trainerLevel)

    private fun draw(data: GameData, rng: Random, trainerLevel: Int): Pair<List<String>, List<String>> {
        val n = slots(trainerLevel)
        val items = Items.shopPool(data).shuffled(rng).take(n)
        val tms = tmPool(data).shuffled(rng).take(n)
        return items to tms
    }

    private fun setStock(state: GameState, items: List<String>, tms: List<String>) {
        state.shopDay = state.day
        state.shopItems.clear(); state.shopItems.addAll(items)
        state.shopTms.clear(); state.shopTms.addAll(tms)
    }

    private fun restock(data: GameData, state: GameState, rng: Random) {
        val (items, tms) = draw(data, rng, state.trainerLevel)
        setStock(state, items, tms)
    }

    fun buy(data: GameData, state: GameState, item: String): ActionResult {
        if (item !in state.shopItems) return ActionResult(false, "Not in today's shop")
        val price = data.price(item)
        if (state.cash < price) return ActionResult(false, "Not enough money (need ${money(price)})")
        state.cash -= price
        StudyStats.addSpent(state, price)
        Economy.give(state, item)
        return ActionResult(true, "Bought ${Items.name(item)} for ${money(price)}")
    }

    /** TMs cost 1000, can be owned once, never used up. */
    fun buyTm(data: GameData, state: GameState, move: String): ActionResult {
        if (move !in state.shopTms) return ActionResult(false, "Not in today's shop")
        val name = Items.tmName(data, move)
        if (move in state.tms) return ActionResult(false, "You already own $name")
        val price = Tuning.TM_PRICE
        if (state.cash < price) return ActionResult(false, "Not enough money (need ${money(price)})")
        state.cash -= price
        StudyStats.addSpent(state, price)
        state.tms += move
        return ActionResult(true, "Bought $name for ${money(price)}")
    }

    /** 100: a fresh random stock for today. */
    fun reroll(data: GameData, state: GameState, rng: Random): ActionResult {
        val price = Tuning.REROLL_PRICE
        if (state.cash < price) return ActionResult(false, "Not enough money (need ${money(price)})")
        state.cash -= price
        StudyStats.addSpent(state, price)
        restock(data, state, rng)
        return ActionResult(true, "New stock for ${money(price)}")
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

    /** Adds the badge once; emits BadgeEarned the first time. */
    fun award(state: GameState, id: Int, events: MutableList<GameEvent>) {
        val title = TITLES[id] ?: return
        if (state.badges.add(id)) events += GameEvent.BadgeEarned(id, title)
    }

    /** Reviews-in-a-day badges (Tuning.REVIEW_BADGES). */
    fun onReviews(state: GameState, events: MutableList<GameEvent>) {
        for ((reviews, id) in Tuning.REVIEW_BADGES) if (state.reviewsToday >= reviews) award(state, id, events)
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
                Badges.award(state, 20, events)
                return ActionResult(true, "$name healed ${mon.name} by $healed HP", events)
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
                return ActionResult(true, "$from evolved into ${mon.name}!", events)
            }
            ItemKind.FOSSIL -> {
                val species = def.fossil?.takeIf { data.species.containsKey(it) } ?: return ActionResult(false, "This fossil can't be revived.")
                val mon = Progression.newMon(data, species, Tuning.FOSSIL_LEVEL, rng, now)
                Economy.take(state, item)
                val added = Catching.addToCollection(data, state, mon, Tier.NORMAL, null, now, events)
                Badges.award(state, 19, events)
                return ActionResult(true, "Revived ${added.name}!", events)
            }
            ItemKind.HELD -> {
                val mon = target(state, targetUid, false) ?: return ActionResult(false, "Choose a Pokémon first.")
                if (mon.heldItem == item) return ActionResult(false, "${mon.name} already holds $name.")
                val old = mon.heldItem
                Economy.take(state, item)
                if (old != null) Economy.give(state, old)
                mon.heldItem = item
                val back = if (old != null) " (${Items.name(old)} went back to the bag)" else ""
                return ActionResult(true, "${mon.name} now holds $name$back")
            }
        }
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
    fun teachTm(data: GameData, state: GameState, move: String, uid: String, replaceIndex: Int?): ActionResult {
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
        return ActionResult(true, text, listOf(GameEvent.MoveLearned(mon.uid, mon.name, moveName, forgot)))
    }
}
