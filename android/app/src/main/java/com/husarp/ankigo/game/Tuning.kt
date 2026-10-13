package com.husarp.ankigo.game

/**
 * Every game number in one place. Values are Ankimon's (h0tp-ftw/ankimon @ da271b3, see docs/ankimon-rules.md)
 * unless the comment says "AnkiGo". Change them here only.
 */
object Tuning {
    // ---- Answers and rounds (Ankimon ankimon_tracker.py / battle_loop.py) ----
    /** Points per button: index = button (0 unknown counts as Good). */
    val POINTS = intArrayOf(10, 0, 5, 10, 20)
    /** Answers per battle round ("cards per round"). */
    const val CARDS_PER_ROUND = 2
    /** multiplier = points / (POINTS_PER_CARD * answers). The enemy only attacks when the multiplier is below 1. */
    const val POINTS_PER_CARD = 10

    // ---- Battle (poke-engine, simplified) ----
    const val STAB = 1.5
    const val CRIT_MULT = 1.5
    /** AnkiGo: crit chance by crit stage (1 = normal). Ankimon's engine has no crits; added on request. */
    val CRIT_CHANCE = doubleArrayOf(0.0, 1.0 / 24, 1.0 / 8, 1.0 / 2, 1.0)
    const val ROLL_MIN = 85 // damage roll 85..100 %
    /** Damaging moves whose power depends on things we don't model (Low Kick, Flail, Gyro Ball...). AnkiGo. */
    const val UNKNOWN_POWER = 60
    const val STRUGGLE = "struggle"
    const val SPLASH = "splash"
    const val FALLBACK_MOVE = "tackle"
    /** Paralysis: chance to be fully paralysed, speed multiplier. */
    const val PARA_SKIP = 0.25
    const val PARA_SPEED = 0.5
    const val FREEZE_THAW = 0.2
    val SLEEP_TURNS = 1..3

    // ---- Catching ----
    /** Ankimon "automatic_battle" mode used because AnkiGo never asks during study: 3 = catch species you don't have. */
    const val DEFAULT_AUTO_MODE = 3
    val DEFAULT_WISHLIST = setOf(25, 133) // Pikachu, Eevee
    /** Ball throw chance in percent (Ankimon item_window.py). AnkiGo fix: a successful throw really catches. */
    val BALL_CHANCE = mapOf(
        "poke-ball" to 8, "great-ball" to 12, "ultra-ball" to 13, "master-ball" to 100, "premier-ball" to 8,
        "net-ball" to 12, "dive-ball" to 11, "dusk-ball" to 11, "heal-ball" to 12, "luxury-ball" to 12,
        "nest-ball" to 12, "quick-ball" to 13, "repeat-ball" to 12, "timer-ball" to 13,
    )
    const val BALL_TYPE_BONUS = 10 // net vs water/bug, dive vs water

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
    const val DAILY_AVERAGE = 100
    const val DAY_START_HOUR = 4
    const val TRAINER_BONUS_FROM_LEVEL = 10 // trainer level above this adds the rare bonus once more

    // ---- Cash, items, shop ----
    const val CASH_EVERY = 10           // reviews
    const val CASH_AMOUNT = 40
    const val CASH_DAILY_CAP = 400
    val ITEM_COUNTDOWN = 3..385         // reviews until the next random item
    const val SHOP_BASE_SLOTS = 3       // + trainer level / 5
    const val SHOP_SLOTS_PER_LEVELS = 5
    const val TM_PRICE = 1000
    const val REROLL_PRICE = 100

    // ---- Trainer ----
    val TRAINER_XP = mapOf(Tier.NORMAL to 10, Tier.BABY to 16, Tier.ULTRA to 30, Tier.LEGENDARY to 120, Tier.MYTHICAL to 160)
    const val TRAINER_XP_BASE = 50.0    // xp for next level = floor(50 * level^1.5)
    const val TRAINER_XP_EXP = 1.5

    // ---- Badges: reviews in one day ----
    val REVIEW_BADGES = linkedMapOf(100 to 1, 200 to 2, 300 to 3, 500 to 4, 1000 to 12, 2000 to 13)

    // ---- Start (AnkiGo: Ankimon starts with nothing but the starter) ----
    const val START_CASH = 0
}
