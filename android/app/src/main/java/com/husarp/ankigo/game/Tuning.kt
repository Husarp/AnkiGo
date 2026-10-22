package com.husarp.ankigo.game

/**
 * Every game number in one place. Values are Ankimon's (h0tp-ftw/ankimon @ da271b3, see docs/ankimon-rules.md)
 * unless the comment says "AnkiGo". Change them here only.
 */
object Tuning {
    /** Longest nickname (same as the Ankimon import). */
    const val NICKNAME_MAX = 20

    // ---- Checking AnkiDroid (AnkiGo; LiveDetector / LiveSession) ----
    /** Live watch: one light poll this often while the battle (panel, floating window, picture-in-picture) is running. */
    const val LIVE_POLL_MS = 1_000L
    /** Every this many polls one is a full read of today's rated cards. */
    const val LIVE_FULL_EVERY = 10
    /** A full re-read at least this often while the battle runs, whatever else happens (catches anything a light poll missed). */
    const val LIVE_RECHECK_MS = 30_000L
    /** Retry after "AnkiDroid not found" / permission missing. */
    const val LIVE_RETRY_MS = 5_000L
    /** Retry after an unexpected AnkiDroid error (old version, provider failure). */
    const val LIVE_RETRY_SLOW_MS = 30_000L

    // ---- Answers and rounds (Ankimon ankimon_tracker.py / battle_loop.py) ----
    /** Points per button: index = button (0 unknown counts as Good). */
    val POINTS = intArrayOf(10, 0, 5, 10, 20)
    /** Answers per battle round ("cards per round"). */
    const val CARDS_PER_ROUND = 2
    /**
     * AnkiGo (Round 10, B22; Ankimon sums [POINTS] instead): who attacks in a round is decided per answer, see [RoundRule].
     * The wild one attacks whenever one answer is Again or Hard; its damage is scaled by the sum of these (index = button).
     */
    val WILD_POWER = doubleArrayOf(0.0, 1.0, 0.5, 0.0, 0.0)
    /** Your Main attacks whenever one answer is Good or Easy; its damage multiplier is the sum of these (index = button, 0 unknown counts as Good). */
    val MAIN_POWER = doubleArrayOf(0.5, 0.0, 0.0, 0.5, 1.0)
    /**
     * AnkiGo (Round 10, W10): switching the Main in a fight takes one turn (one dot): the wild one attacks the incoming Pokémon once at
     * this power, the same as one Again. R13 (Part C): so does an item used on the Main in a fight (Game.useItem).
     */
    val SWITCH_HIT_POWER = WILD_POWER[1]

    // ---- Battle (poke-engine, simplified) ----
    const val STAB = 1.5
    const val CRIT_MULT = 1.5
    /** AnkiGo: critical hits switched off (Ankimon has none). false = no crit rolls at all, also for willCrit moves. */
    const val CRITS = false
    /** AnkiGo: crit chance by crit stage (1 = normal). Ankimon's engine has no crits; added on request. */
    val CRIT_CHANCE = doubleArrayOf(0.0, 1.0 / 24, 1.0 / 8, 1.0 / 2, 1.0)
    const val ROLL_MIN = 85 // damage roll 85..100 %
    /** Damaging moves whose power depends on things we don't model (Low Kick, Flail, Gyro Ball...). AnkiGo. */
    const val UNKNOWN_POWER = 60
    const val STRUGGLE = "struggle"
    const val SPLASH = "splash"
    const val FALLBACK_MOVE = "tackle"

    // ---- Move choice (AnkiGo, Round 10 W9; see MoveChooser) ----
    /** Smart / Smartest: how often a side that also has a useful status move still uses a damaging one. */
    const val SMART_DAMAGE_SHARE = 0.8
    /** Smart / Smartest: accuracy and evasion stages stop at ±this (Random keeps the games' ±6). */
    const val SMART_ACC_EVA_LIMIT = 2
    /** Smartest: with two or more damaging moves, the strongest one is picked this often, else the second strongest. */
    const val SMARTEST_BEST_SHARE = 0.7

    /** Paralysis: chance to be fully paralysed, speed multiplier. */
    const val PARA_SKIP = 0.25
    const val PARA_SPEED = 0.5
    const val FREEZE_THAW = 0.2
    /** Sleep (poke-engine): no turn counter, each move attempt wakes the sleeper with this chance and it acts at once. */
    const val WAKE_CHANCE = 0.33
    /** A frozen Pokémon that uses one of these moves thaws and acts (poke-engine THAW_IF_USES). */
    val THAW_IF_USES = setOf("scald", "flamewheel", "sacredfire", "flareblitz", "fusionflare", "steameruption", "scorchingsands")
    /** Besides every damaging Fire move, these also thaw a frozen target they hit (THAW_IF_HIT_BY). */
    val THAW_IF_HIT_BY = setOf("scald", "steameruption", "scorchingsands")
    /**
     * Charge moves (poke-engine `charge` flag, 17 of them): the first time one is picked in a fight the user only charges
     * (no damage, no accuracy roll), the next time it hits. The charge is kept on that side for the rest of the fight.
     */
    val CHARGE_MOVES = setOf(
        "bounce", "dig", "dive", "electroshot", "fly", "freezeshock", "geomancy", "iceburn", "meteorbeam", "phantomforce",
        "razorwind", "shadowforce", "skullbash", "skyattack", "skydrop", "solarbeam", "solarblade",
    )
    /**
     * These lose the charge after the hit (poke-engine after_move); every other charge move keeps it, so e.g. Solar Beam
     * charges once per fight and then hits whenever it is picked (copied from Ankimon). NOT copied: poke-engine also makes
     * a side that charged one of these untouchable until it uses the move again. A wild one only moves after weak answers
     * and could stay untouchable for dozens of cards, so AnkiGo does not model that.
     */
    val CHARGE_CLEARS_AFTER_USE = setOf("fly", "bounce", "dig", "dive", "phantomforce", "shadowforce")
    /** Powder moves fail against Grass types (poke-engine POWDER flag). */
    val POWDER_MOVES = setOf("sleeppowder", "stunspore", "poisonpowder", "spore", "powder", "ragepowder", "magicpowder")

    // ---- Catching ----
    /** Ankimon "automatic_battle" mode used because AnkiGo never asks during study: 3 = catch species you don't have. */
    const val DEFAULT_AUTO_MODE = 3
    val DEFAULT_WISHLIST = setOf(25, 133) // Pikachu, Eevee
    /**
     * AnkiGo (PLAN 171; Ankimon's were 8-13 %): the four ball tiers' catch chance in percent. A successful throw really catches;
     * a miss uses the ball up AND the wild Pokémon runs away (Game.throwBall), so a cheap ball is a lucky shot, not one to spam.
     */
    val BALL_CHANCE = mapOf("poke-ball" to 20, "great-ball" to 50, "ultra-ball" to 80, "master-ball" to 100)
    /**
     * PLAN 171: Ankimon's other ten balls. Their ids stay (the Ankimon import / export, balls already in a bag), but they are never sold
     * or found any more; one the player owns throws as an [OLD_BALL_AS] (its chance, no type bonus).
     */
    val OLD_BALLS = listOf(
        "premier-ball", "net-ball", "dive-ball", "dusk-ball", "heal-ball", "luxury-ball", "nest-ball", "quick-ball", "repeat-ball", "timer-ball",
    )
    const val OLD_BALL_AS = "great-ball"
    /** PLAN 171 (prices: Adam, R13 Part C): ball prices in ₽ (not PokeAPI's 200 / 600 / 800). 0 = never sold (the Master Ball, every [OLD_BALLS] one). */
    val BALL_PRICE = mapOf("poke-ball" to 200, "great-ball" to 700, "ultra-ball" to 1_500, "master-ball" to 0)
    /** PLAN 171: the Ultra Ball is in the Shop on about one game day in this many (date-seeded, see Shop.ultraBallDay); never in the daily draw. */
    const val ULTRA_BALL_EVERY_DAYS = 7

    // ---- XP, levels, EVs, friendship ----
    const val XP_DIVISOR = 7.0          // XP = ceil(base_exp * level / 7)
    /** AnkiGo: a wild Pokémon caught at 0 HP also gives its defeat XP (Ankimon: none; there you pick catch/defeat by hand). */
    const val CATCH_GIVES_XP = true
    const val MAX_LEVEL = 100
    const val MAX_LEVELS_PER_GAIN = 10
    const val XP_SHARE_PART = 0.5       // classic Exp. Share: main and holder each get half
    const val LUCKY_EGG = 1.5
    const val EV_STAT_CAP = 252
    const val EV_TOTAL_CAP = 510
    const val WILD_EV_PER_LEVEL = 5.1   // wild EV budget = level * 5.1
    val FRIENDSHIP_GAIN = 5..9
    const val SOOTHE_BELL = 1.5
    const val DEFAULT_MIN_FRIENDSHIP = 160
    const val DAY_FROM_HOUR = 6         // day 6:00-18:00, night otherwise
    const val NIGHT_FROM_HOUR = 18
    /** AnkiGo optional early pace: 40% wild HP until the lead reaches level 15. Other stats/rewards stay Ankimon's. */
    const val FASTER_START_BELOW_LEVEL = 15
    const val FASTER_START_HP_FACTOR = 0.40
    fun wildHp(fullHp: Int, leadLevel: Int, fasterStart: Boolean): Int =
        if (fasterStart && leadLevel < FASTER_START_BELOW_LEVEL)
            kotlin.math.ceil(fullHp * FASTER_START_HP_FACTOR).toInt().coerceAtLeast(1)
        else fullHp
    const val STARTER_LEVEL = 5
    const val FOSSIL_LEVEL = 5

    // ---- Encounters (legacy system, encounter_functions.py) ----
    const val WILD_LEVEL_SPREAD = 3     // main level -3..+3
    const val SHINY_ODDS = 4096
    val DEFAULT_GENS = (1..8).toSet()
    /** Base tier percentages before today's progress, trainer bonus and level gates. */
    val BASE_TIERS = linkedMapOf(
        Tier.NORMAL to 94.3, Tier.BABY to 2.0, Tier.ULTRA to 1.5, Tier.LEGENDARY to 0.5, Tier.MYTHICAL to 0.2,
        Tier.MEGA to 0.5, Tier.GMAX to 0.5, Tier.STARTER to 0.5,
    )
    /** Main level needed before a tier can appear. */
    val TIER_GATE = mapOf(
        Tier.ULTRA to 30, Tier.LEGENDARY to 50, Tier.MEGA to 60, Tier.GMAX to 65, Tier.MYTHICAL to 75, Tier.STARTER to 80,
    )
    /** Ankimon forces the starter tier to 0% ("Comment to activate starters"). */
    const val STARTER_TIER_ENABLED = false
    /** Wild minimum level for rare kinds. */
    val TIER_MIN_LEVEL = mapOf(Tier.MYTHICAL to 75, Tier.LEGENDARY to 50, Tier.ULTRA to 30, Tier.STARTER to 30)
    /** A wild form that evolves by anything but level only appears at this level (Ankimon). AnkiGo fix: not for baby-line basics. */
    const val NON_LEVEL_EVO_MIN_LEVEL = 100
    /** Regional forms: a picked base species turns into one of its eligible forms with this chance PER eligible form (Ankimon 7 %). */
    const val FORM_SWAP_CHANCE = 0.07
    /** Weight of a wishlisted species inside its rolled tier's pool (others 1). 1 = wishlisted species are met no more often (Ankimon). */
    const val WISHLIST_WEIGHT = 1
    const val DAILY_AVERAGE = 100
    const val DAY_START_HOUR = 4
    const val TRAINER_BONUS_FROM_LEVEL = 10 // trainer level above this adds the rare bonus once more

    // ---- Cash, items, shop ----
    // AnkiGo's money (not Ankimon's 40 per 10 reviews, cap 400): counted answers and won fights (a defeat or a catch, R13 Part C) pay.
    // PLAN 125/198: no hard daily cap; the day's money comes in tiers of CASH_TIER and each tier is harder to earn (linear): in tier N
    // every answer earns CASH_PER_ANSWER / N and every win CASH_PER_WIN / N, added up exactly and paid in whole coins (Economy).
    // R14-B2-D (PLAN 125 / R10 X3): reaching the daily goal gives a gift from a tiered pool (DailyGift) instead of money; money sits only in the tiers.
    const val CASH_PER_ANSWER = 5       // any button, Again too
    const val CASH_PER_WIN = 10         // a wild Pokémon defeated or caught (auto catch or ball); never a miss, an escape or a release
    const val CASH_TIER = 500           // counted BEFORE the Amulet Coin / Luck Incense doubling (so those keep doubling at every tier)
    // Daily gift (DailyGift.kt): the chance of each tier grows in a straight line with the goal streak, from day 1 to day GIFT_MAX_DAYS (then it stays).
    const val GIFT_MAX_DAYS = 30            // the streak (in days) at which the chances stop growing
    const val GIFT_UNCOMMON_FIRST = 22      // percent on streak day 1 ...
    const val GIFT_UNCOMMON_LAST = 40       // ... and on day GIFT_MAX_DAYS and later
    const val GIFT_RARE_FIRST = 3           // (common = the rest: 75 % on day 1, 40 % on day 30)
    const val GIFT_RARE_LAST = 20
    val GIFT_COMMON = listOf("poke-ball" to 3, "potion" to 2, "super-potion" to 1)    // item to count; each entry equally likely in its tier
    val GIFT_UNCOMMON = listOf("great-ball" to 2, "hyper-potion" to 1, "poke-ball" to 5)
    val GIFT_RARE_ITEMS = listOf("ultra-ball" to 1)  // the rare tier also holds one evolution stone and one TM (see DailyGift)
    val ITEM_COUNTDOWN = 3..385         // reviews until the next random item
    const val SHOP_BASE_SLOTS = 3       // + trainer level / 5
    const val SHOP_SLOTS_PER_LEVELS = 5
    const val TM_PRICE = 1000
    const val REROLL_PRICE = 100
    val SHOP_BASICS = listOf("poke-ball", "potion") // always on sale at their normal price; not part of the daily draw
    const val DEAL_PERCENT = 30             // the deal of the day: this much off (price = round(normal * 0.7), at least 1)
    const val WEEKEND_TM_MIN_POWER = 90     // the weekend special is an evolution stone or a TM of a move at least this strong

    // ---- Healing (AnkiGo's own rules, PLAN 80; Ankimon has none of these). Whole HP, never above max HP. ----
    const val LEVEL_UP_HEAL_PERCENT = 25 // of max HP per level gained, on top of the HP the new level adds (min 1)
    const val DEFEAT_HEAL_PERCENT = 10   // of max HP: the main after a defeat or a catch (min 1)
    const val BENCH_REST_DIVISOR = 200   // a bench team member heals maxHp / 200 HP (0.5 %) per counted answer, fraction kept (Mon.restFrac)

    // ---- Trainer ----
    val TRAINER_XP = mapOf(Tier.NORMAL to 10, Tier.BABY to 16, Tier.ULTRA to 30, Tier.LEGENDARY to 120, Tier.MYTHICAL to 160)
    const val TRAINER_XP_BASE = 50.0    // xp for next level = floor(50 * level^1.5)
    const val TRAINER_XP_EXP = 1.5

    // ---- Badges: reviews in one day ----
    val REVIEW_BADGES = linkedMapOf(100 to 1, 200 to 2, 300 to 3, 500 to 4, 1000 to 12, 2000 to 13)

    // ---- Start (AnkiGo: Ankimon starts with nothing but the starter) ----
    const val START_CASH = 0
}
