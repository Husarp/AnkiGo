package com.husarp.ankigo.game

import kotlin.math.max

// R4-03 / PLAN 79: the tiered achievements engine. Pure Kotlin. The list itself is AchievementTable.kt; no screen in here.
//
// Two kinds of numbers (Metric):
//  - DERIVED from what the save already has (catches, defeats, levels, Pokédex ...): nothing is stored for them.
//  - COUNTED in GameState.counters (damage, misses, balls thrown ...): counted from the GameEvents of one action in ONE
//    place, [count], called by Game next to StudyStats.record. Ignored answers produce no Counted/Attack/Cash events, so
//    they add nothing; an undo's redo is ignored the same way, so nothing is counted twice.
// Reached tiers are remembered with their date in GameState.achTiers and never go back down.

/** What an achievement counts. The names of the COUNTED ones are the keys in GameState.counters: never rename them. */
enum class Metric(val counted: Boolean, val external: Boolean = false) {
    // ---- derived from the save ----
    CATCHES(false),           // wild catches, or the Pokémon you own when that is more (imports)
    ANSWERS(false),           // GameState.totalReviews
    DEFEATS(false),           // wild Pokémon defeated or caught (a catch is a win)
    EVOLUTIONS(false),
    BEST_COMBO(false),        // best correct answers in a row
    BEST_STREAK_DAYS(false),  // longest daily-goal streak
    DEX_SEEN(false),
    DEX_CAUGHT(false),
    HIGHEST_LEVEL(false),     // highest level among the Pokémon you own
    BEST_DAY(false),          // most answers in one day: the day history, today, and the review badges (Ankimon 1-4, 12, 13) already earned
    LEGENDARY_MYTHICAL(false), // caught species flagged legendary or mythical
    TYPES_CAUGHT(false),      // different types among the caught species (0..18)
    BALL_CATCHES(false),      // wild catches with a thrown ball (not the auto rule's)

    // ---- counted ----
    DAMAGE_DEALT(true),       // damage of your attacks
    DAMAGE_TAKEN(true),       // damage of the wild one's attacks
    DAMAGE_BLOCKED(true),     // what the wild one's resisted hits did not do: damage / effectiveness - damage (an immune hit adds nothing: its full damage is unknown)
    MISSES(true),             // attacks that missed, yours or the wild one's (once per attack)
    SUPER_EFFECTIVE(true),    // your damaging hits that were super effective (once per attack)
    CRITS(true),              // your critical hits (once per attack; only happen while the engine has crits on)
    RESISTED(true),           // the wild one's damaging hits that were not very effective or had no effect (once per attack)
    BALLS_THROWN(true),
    SHINIES(true),            // shinies caught in a fight (the value is never below the shinies you own)
    TOTAL_XP(true),           // XP your Pokémon gained (XpGained events). R13 Part F: no floor from the Pokémon you own (a caught one's XP is not earned)
    TOTAL_LEVELS(true),       // R13 Part F: levels your Pokémon gained (LevelUp events), never the levels a Pokémon came with (was: the owned levels added up)
    ITEMS_FOUND(true),        // random items after answers
    MONEY_EARNED(true),
    MONEY_SPENT(true),        // Shop
    ITEMS_USED(true),         // healing / evolution items, fossils and escape items used up, held items given
    TMS_TAUGHT(true),
    DAYS_STUDIED(true),       // days with at least one counted answer
    GOALS_REACHED(true),      // times the daily goal was reached (the daily-goal bonus moments)
    EASY_PRESSES(true),
    GOOD_PRESSES(true),
    COMEBACKS(true),          // a wild one defeated or caught while your main is under 10 % HP

    // ---- external (R4-07): measured outside the game save, stored in GameState.counters as the latest known total; see Achievements.feed ----
    STUDY_MINUTES(true, external = true), // minutes AnkiGo timed you answering (reviews.csv + live-time.csv), all time
    NEW_CARDS(true, external = true),      // cards answered for the very first time (AnkiDroid's history), all time
}

/**
 * One row for the Achievements page: how far [id] is. [tier] = highest tier reached (-1 none); [dates] = epoch ms per reached tier;
 * [inferred] = the tiers (indexes) whose date is only the day AnkiGo first saw them, not the day they happened (see GameState.achInferred).
 */
class AchView(
    val id: String,
    val group: String,
    val title: String,
    val desc: String,
    val icon: String,
    val oneOff: Boolean,
    val ankimonBadge: Int?,
    val tier: Int,
    val tierName: String?,
    val dates: List<Long>,
    val value: Long,
    val next: Long?,            // threshold of the next tier; null at the top
    val progress: Float,        // value / next, 0..1 (1 at the top)
    val tiers: List<Long>,
    val inferred: Set<Int> = emptySet(),
)

/** The four battle numbers of one Attack event (see [Achievements.attackNumbers]). */
class AttackNumbers(val dealt: Long, val taken: Long, val blocked: Long, val misses: Int)

object Achievements {
    /** "Comeback": the main has less than this share of its HP (percent) when the wild one is defeated or caught. */
    const val COMEBACK_PERCENT = 10

    // ---- values ----

    /** The number [metric] stands at now. Derived ones read the save; counted ones read GameState.counters. */
    fun value(metric: Metric, state: GameState, data: GameData): Long = when (metric) {
        Metric.CATCHES -> max(state.catches, state.mons.size - ownedStarter(state)).toLong()
        Metric.ANSWERS -> state.totalReviews
        Metric.DEFEATS -> state.defeats.toLong()
        Metric.EVOLUTIONS -> state.evolutions.toLong()
        Metric.BEST_COMBO -> state.bestCardStreak.toLong()
        Metric.BEST_STREAK_DAYS -> max(state.bestStreakDays, state.streakDays).toLong()
        Metric.DEX_SEEN -> (state.seen.size - starterOnly(state, state.starterSeen, state.seen)).toLong()
        Metric.DEX_CAUGHT -> (state.caught.size - starterOnly(state, state.starterCaught, state.caught)).toLong()
        Metric.HIGHEST_LEVEL -> (state.mons.maxOfOrNull { if (isStarter(state, it) && it.level <= Tuning.STARTER_LEVEL) 0 else it.level } ?: 0).toLong()
        Metric.BEST_DAY -> bestDay(state)
        Metric.LEGENDARY_MYTHICAL -> caughtForReal(state).count { data.species[it]?.let { sp -> sp.isLegendary || sp.isMythical } == true }.toLong()
        Metric.TYPES_CAUGHT -> caughtForReal(state).flatMapTo(HashSet()) { data.species[it]?.types ?: emptyList() }.size.toLong()
        Metric.BALL_CATCHES -> state.ballCatches.filterKeys { it != "auto" }.values.sumOf { it.toLong() }
        Metric.SHINIES -> max(counter(state, metric), state.mons.count { it.shiny && !isStarter(state, it) }.toLong()) // an import brings owned shinies
        else -> counter(state, metric)
    }

    // ---- R9-W4-B: the starter counts for no achievement ----
    // It is a gift: it is not a catch, its species is not a Pokédex find, its types and (rare) shine are not collected, and the free
    // level / XP it starts with are not earned. What it earns later by studying (levels, XP, evolving) counts like anything else.
    // GameState.starterUid says which one it is ("" = unknown: an old save or an import, nothing is held back there).

    private fun isStarter(state: GameState, mon: Mon) = state.starterUid.isNotEmpty() && mon.uid == state.starterUid

    /** 1 while the starter is still owned, else 0. */
    private fun ownedStarter(state: GameState): Int = if (state.starterUid.isNotEmpty() && state.mons.any { it.uid == state.starterUid }) 1 else 0

    /** How many of [only] (species the starter alone brought) are in [all] and not owned by any other Pokémon (which makes them real finds). */
    private fun starterOnly(state: GameState, only: Set<Int>, all: Set<Int>): Int =
        only.count { sp -> sp in all && state.mons.none { it.species == sp && !isStarter(state, it) } }

    /** [GameState.caught] without the species only the starter brought. */
    private fun caughtForReal(state: GameState): Set<Int> {
        val skip = state.starterCaught.filterTo(HashSet()) { sp -> state.mons.none { it.species == sp && !isStarter(state, it) } }
        return if (skip.isEmpty()) state.caught else state.caught - skip
    }

    private fun counter(state: GameState, metric: Metric): Long = state.counters[metric.name] ?: 0L

    /**
     * The counted metrics whose [value] is max(counter, what the save holds): see [ratchet]. R13 Part F: only the shinies (an import
     * brings owned shinies). Total XP left the list: its floor added the XP a caught Pokémon came with, and the ratchet kept it.
     */
    private val RATCHETED = listOf(Metric.SHINIES)

    /**
     * R4 fix A1: stores the max(counter, derived) of the lifetime totals that [value] reads that way (the shinies) back into
     * GameState.counters, so the number a later release of a Pokémon would lower stays. Later event increments then add on top of
     * the stored baseline. Never lowers a counter. Called by [check] (so by every action, import and the first look at a save).
     */
    fun ratchet(state: GameState, data: GameData) {
        for (m in RATCHETED) {
            val v = value(m, state, data)
            if (v > counter(state, m)) state.counters[m.name] = v
        }
    }

    /** "<id>/<tier>": the key of one tier in GameState.achInferred. */
    fun inferredKey(id: String, tier: Int) = "$id/$tier"

    /** The most answers in one day: the best logged day, today, and the review count of the highest review badge already earned (the history keeps 400 days only). */
    private fun bestDay(state: GameState): Long {
        val logged = state.history.values.maxOfOrNull { it.reviews } ?: 0
        val badge = Tuning.REVIEW_BADGES.filter { (_, id) -> id in state.badges }.keys.maxOrNull() ?: 0
        return max(max(logged, state.reviewsToday), badge).toLong()
    }

    /** How many tiers of [row] are reached right now (0..row.tiers.size). A badge one-off is reached when its id is in GameState.badges. */
    fun reachedTiers(row: AchRow, state: GameState, data: GameData): Int {
        row.ankimonBadge?.let { return if (it in state.badges) 1 else 0 }
        val v = value(row.metric ?: return 0, state, data)
        return row.tiers.count { v >= it }
    }

    // ---- counting ----

    /** Adds [n] to a counted [metric] (shop money, items used, TMs: the places that have no event to count from). */
    fun bump(state: GameState, metric: Metric, n: Long = 1) {
        require(metric.counted) { "$metric is derived from the save, not counted" }
        if (n > 0) state.counters.merge(metric.name, n, Long::plus)
    }

    /**
     * Counts what the [events] of ONE action say (Game.onAnswer / throwBall). Counted -> Good / Easy presses and the first answer
     * of a day; Cash -> money earned (+ the daily-goal bonus = goal reached); XpGained -> total XP; ItemFound -> items found;
     * Attack -> damage dealt / taken / blocked, misses (either side), super-effective and critical hits, resisted hits;
     * BallThrow -> balls thrown; Caught -> shinies; Defeated / Caught while the main has less than [COMEBACK_PERCENT] % HP ->
     * comeback. Events other actions already counted must not be passed again.
     */
    fun count(state: GameState, events: List<GameEvent>) {
        var hp = -1; var maxHp = 0 // the main's HP as the events so far show it (-1 = not known yet)
        for ((i, e) in events.withIndex()) when (e) {
            is GameEvent.Counted -> {
                when (e.button) { 3 -> bump(state, Metric.GOOD_PRESSES); 4 -> bump(state, Metric.EASY_PRESSES) }
                if (e.reviewsToday == 1) bump(state, Metric.DAYS_STUDIED)
            }
            is GameEvent.Cash -> {
                bump(state, Metric.MONEY_EARNED, e.amount.toLong())
                if (e.kind == CashKind.DAILY_BONUS) bump(state, Metric.GOALS_REACHED)
            }
            is GameEvent.XpGained -> bump(state, Metric.TOTAL_XP, e.xp.toLong())
            is GameEvent.LevelUp -> bump(state, Metric.TOTAL_LEVELS)   // one event per level gained
            is GameEvent.ItemFound -> bump(state, Metric.ITEMS_FOUND)
            is GameEvent.Attack -> {
                if (e.byPlayer) { hp = e.userHp; maxHp = e.userMaxHp } else { hp = e.targetHp; maxHp = e.targetMaxHp }
                val hit = !e.missed && !e.failed && e.category != Category.STATUS
                val n = attackNumbers(e) // the four battle numbers: the same definitions StudyStats.record puts into the day log
                bump(state, Metric.MISSES, n.misses.toLong())
                bump(state, Metric.DAMAGE_DEALT, n.dealt)
                bump(state, Metric.DAMAGE_TAKEN, n.taken)
                bump(state, Metric.DAMAGE_BLOCKED, n.blocked)
                if (e.byPlayer) {
                    if (hit && e.damage > 0) {
                        if (e.effectiveness > 1.0) bump(state, Metric.SUPER_EFFECTIVE)
                        if (e.crit) bump(state, Metric.CRITS)
                    }
                } else if (hit) {
                    if (e.effectiveness < 1.0) bump(state, Metric.RESISTED)
                }
            }
            is GameEvent.Effect -> if (e.onPlayer) { hp = e.hp; maxHp = e.maxHp }
            is GameEvent.Healed -> { hp = e.hp; maxHp = e.maxHp }
            is GameEvent.MainChanged, is GameEvent.MainHealed -> hp = -1
            is GameEvent.BallThrow -> bump(state, Metric.BALLS_THROWN)
            is GameEvent.Defeated -> if (low(hp, maxHp)) bump(state, Metric.COMEBACKS)
            is GameEvent.Caught -> {
                if (e.shiny) bump(state, Metric.SHINIES)
                // A ball catch has no fight before it in this batch: the heal after the catch says what HP the main had.
                val before = if (hp >= 0) hp to maxHp else events.drop(i + 1).filterIsInstance<GameEvent.Healed>()
                    .firstOrNull { it.reason == HealReason.CATCH }?.let { (it.hp - it.amount) to it.maxHp }
                if (before != null && low(before.first, before.second)) bump(state, Metric.COMEBACKS)
            }
            else -> {}
        }
    }

    private fun low(hp: Int, maxHp: Int) = hp > 0 && maxHp > 0 && hp * 100 < maxHp * COMEBACK_PERCENT

    /**
     * What ONE Attack adds to the four battle numbers: [AttackNumbers.dealt] = damage of your damaging hits, [taken] = damage of the
     * wild one's hits, [blocked] = what its resisted hits did not do (damage / effectiveness - damage; an immune hit adds nothing: its
     * full damage is unknown), [misses] = 1 when the attack missed (either side). A status move or a failed move deals nothing.
     * The ONE definition: [count] (all-time counters) and StudyStats.record (the day log behind the Statistics charts) both use it.
     */
    fun attackNumbers(e: GameEvent.Attack): AttackNumbers {
        val hit = !e.missed && !e.failed && e.category != Category.STATUS
        val damaging = hit && e.damage > 0
        val blocked = !e.byPlayer && damaging && e.effectiveness > 0.0 && e.effectiveness < 1.0
        return AttackNumbers(
            dealt = if (e.byPlayer && damaging) e.damage.toLong() else 0L,
            taken = if (!e.byPlayer && damaging) e.damage.toLong() else 0L,
            blocked = if (blocked) Math.round(e.damage / e.effectiveness) - e.damage.toLong() else 0L,
            misses = if (e.missed) 1 else 0,
        )
    }

    // ---- tiers ----

    /**
     * Looks at every row of the table: each tier that is reached now and not yet remembered gets [now] (or the badge's own date)
     * appended to GameState.achTiers, in order, and (unless quiet) sends one AchievementReached per tier, lowest first. A row
     * that has never been looked at (an old save, a row added to the list later) is dealt with quietly, as is everything when
     * [silent] (after an Ankimon import): the dates are recorded, no events. A one-off that is an Ankimon badge never sends the
     * event (BadgeEarned has already said it), nor does a tier of a row whose tiers are badges (AchRow.tierBadges) once that badge is held. Tiers never go back down: a lower value later changes nothing.
     * A tier dated by a quiet look (silent, or a row never looked at) without a date of its own (the badge's) only says "AnkiGo saw it
     * by now": it is flagged in GameState.achInferred, so the page shows "Reached before <date>" and "Recently reached" skips it (R4 fix A3).
     * The call that migrates an older tier ladder ([migrate]) is quiet and flags its new dates as inferred too.
     * The first call on a state also sets GameState.achSince = [now]. Every call first [ratchet]s the lifetime totals (R4 fix A1).
     */
    fun check(state: GameState, data: GameData, now: Long, events: MutableList<GameEvent>, silent: Boolean = false, log: (String) -> Unit = {}) {
        val migrated = migrate(state, log) // R9-B2: a tier the player already had under the old ladder is added back quietly below
        if (state.achSince == 0L) state.achSince = now
        ratchet(state, data)
        for (row in AchievementTable.ROWS) {
            val known = state.achTiers[row.id]
            val dates = known ?: mutableListOf<Long>().also { state.achTiers[row.id] = it }
            val reached = reachedTiers(row, state, data)
            if (reached <= dates.size) continue
            val quiet = silent || migrated || known == null || row.ankimonBadge != null
            val guessed = silent || migrated || known == null // reached before AnkiGo looked: [now] is only when it was first seen
            for (t in dates.size until reached) {
                val badge = row.ankimonBadge ?: row.tierBadges.getOrNull(t) // a tier that IS a badge: the badge's date, and BadgeEarned has said it
                val badgeDate = badge?.let { state.badgeAt[it] }
                dates += badgeDate ?: now
                if (badgeDate == null && guessed) state.achInferred += inferredKey(row.id, t)
                if (!quiet && (badge == null || badge !in state.badges)) events += GameEvent.AchievementReached(row.id, t, row.title, AchievementTable.tierName(t))
            }
        }
    }

    /**
     * R9-B2: brings a save written for an older tier ladder (GameState.achLadder < AchievementTable.LADDER) to the current one, once.
     * Rows in [AchievementTable.REMAP]: new tier t takes the date (and the inferred flag) of old tier REMAP[t]; the old tiers that have no
     * new tier are dropped, and a new tier whose old one was never reached has no date (so [check] decides, quietly). Nothing is paid
     * for a tier, so nothing can be lost. Then (ladder 3) [fixLifetimeTotals]; [log] gets its line. Returns true when it changed the
     * version. No-op on a current state.
     */
    fun migrate(state: GameState, log: (String) -> Unit = {}): Boolean {
        if (state.achLadder >= AchievementTable.LADDER) return false
        if (state.achLadder < 2) for ((id, remap) in AchievementTable.REMAP) {
            val old = state.achTiers[id] ?: continue
            val oldInferred = old.indices.filterTo(HashSet()) { inferredKey(id, it) in state.achInferred }
            state.achInferred.removeAll { it.startsWith("$id/") }
            val kept = remap.filter { it < old.size }
            state.achTiers[id] = kept.map { old[it] }.toMutableList()
            kept.forEachIndexed { t, o -> if (o in oldInferred) state.achInferred += inferredKey(id, t) }
        }
        if (state.achLadder < 3) log(fixLifetimeTotals(state))
        state.achLadder = AchievementTable.LADDER
        return true
    }

    /**
     * R13 Part F, once (ladder 2 -> 3): "Total XP" and "Total levels" were inflated by what a caught (or imported) Pokémon came with:
     * Total XP had a floor of the owned Pokémon's whole growth-table XP and was ratcheted (kept), Total levels added up the owned
     * levels. Both are recomputed from what really happened: Total XP = the XP of the day history ([DayLog.xp], every XpGained;
     * 400 days kept), Total levels = the levels the starter gained after Lv [Tuning.STARTER_LEVEL] (no other Pokémon's level when it
     * was caught is known: those count from now on, level by level). The counters are set to these values (the ratchet reset), and a
     * tier of the two rows that the new value doesn't reach is locked again quietly (its date and inferred flag go; no event, no
     * message on screen). Returns the line for the debug log.
     */
    internal fun fixLifetimeTotals(state: GameState): String {
        val oldXp = counter(state, Metric.TOTAL_XP)
        val oldLevels = state.mons.sumOf { it.level.toLong() }
        val xp = state.history.values.sumOf { it.xp.toLong() }
        val starter = state.mons.firstOrNull { isStarter(state, it) }
        val levels = starter?.let { (it.level - Tuning.STARTER_LEVEL).coerceAtLeast(0).toLong() } ?: 0L
        fun set(m: Metric, v: Long) { if (v > 0) state.counters[m.name] = v else state.counters.remove(m.name) }
        set(Metric.TOTAL_XP, xp)
        set(Metric.TOTAL_LEVELS, levels)
        val relocked = ArrayList<String>()
        for (row in AchievementTable.ROWS) {
            val m = row.metric ?: continue
            if (m != Metric.TOTAL_XP && m != Metric.TOTAL_LEVELS) continue
            val dates = state.achTiers[row.id] ?: continue
            val keep = row.tiers.count { counter(state, m) >= it }
            if (dates.size <= keep) continue
            relocked += "${row.id} ${dates.size}->$keep"
            while (dates.size > keep) dates.removeAt(dates.size - 1)
            state.achInferred.removeAll { k -> k.startsWith("${row.id}/") && (k.substringAfter('/').toIntOrNull() ?: 0) >= keep }
        }
        return "achievements fix (R13 F): total XP $oldXp -> $xp, total levels (owned) $oldLevels -> $levels gained" +
            if (relocked.isEmpty()) "; no tier locked again" else "; locked again: " + relocked.joinToString(", ")
    }

    /**
     * R4-07: stores the latest known all-time total [value] of an external [metric] (STUDY_MINUTES, NEW_CARDS) in GameState.counters
     * and runs the normal tier check. The stored number is the max of what it was and [value]: a smaller reading (AnkiDroid could not
     * be read fully, a log was trimmed) never lowers it. The very first feed of a metric on a save is silent (dates only, no flood of
     * events for a long history); later tier crossings send one AchievementReached per tier. Returns the new events.
     */
    fun feed(state: GameState, data: GameData, metric: Metric, value: Long, now: Long): List<GameEvent> {
        require(metric.external) { "$metric is not fed from outside" }
        val first = metric.name !in state.counters
        state.counters[metric.name] = max(state.counters[metric.name] ?: 0L, value.coerceAtLeast(0L))
        return mutableListOf<GameEvent>().also { check(state, data, now, it, silent = first) }
    }

    /**
     * R4 fix A2: the first, quiet look at a save that has just become READY (GameRepo: load, restore, new game, import), before any screen
     * can show it or any action change it: it persists the tiers the save already earns (and the lifetime-total baselines), so a later
     * release of a Pokémon cannot take a tier the page has shown away again.
     */
    fun onReady(state: GameState, data: GameData, now: Long, log: (String) -> Unit = {}) = check(state, data, now, mutableListOf(), silent = true, log = log)

    /** One action done: count its events, then check the tiers (the new AchievementReached events are appended to [events]). */
    fun settle(data: GameData, state: GameState, now: Long, events: MutableList<GameEvent>) {
        count(state, events)
        check(state, data, now, events)
    }

    /** The rows for the Achievements page, in table order. Unknown ids in a save (a later list may bring them back) are not shown. */
    fun view(state: GameState, data: GameData): List<AchView> = AchievementTable.ROWS.map { row ->
        val dates = state.achTiers[row.id].orEmpty().take(row.tiers.size)
        val reached = max(dates.size, reachedTiers(row, state, data))
        val value = row.ankimonBadge?.let { if (it in state.badges) 1L else 0L } ?: value(row.metric!!, state, data)
        val next = row.tiers.getOrNull(reached)
        AchView(
            id = row.id, group = row.group, title = row.title, desc = row.desc, icon = row.icon, oneOff = row.oneOff, ankimonBadge = row.ankimonBadge,
            tier = reached - 1, tierName = if (reached > 0) AchievementTable.tierName(reached - 1) else null,
            dates = dates, value = value, next = next,
            progress = if (next == null) 1f else (value.toDouble() / next).coerceIn(0.0, 1.0).toFloat(),
            tiers = row.tiers,
            inferred = dates.indices.filterTo(HashSet()) { inferredKey(row.id, it) in state.achInferred },
        )
    }

    // ---- old saves ----

    /**
     * Fills the counters of a save made before they existed from the best data it has (SaveCodec calls this when the save has no
     * `counters` key): money earned / spent, XP, Easy / Good presses, items used (healing items) and shinies from the day history
     * (kept 400 days; the XP value is also never below what the owned Pokémon hold, see [value]); days studied = days with answers, goal reached = days at the daily goal (or the best streak if higher);
     * balls thrown = the catches made with a ball (a lower bound); the rest starts at 0. All are lower bounds, never more
     * than what happened. (Goal days are read against `settings.dailyAverage` on purpose: a save without `counters` predates the weekday
     * goal table, so `DailyGoal.forDay` always gives it that one goal.)
     */
    fun backfill(state: GameState) {
        val days = state.history.values
        fun set(m: Metric, v: Long) { if (v > 0) state.counters[m.name] = v }
        set(Metric.MONEY_EARNED, days.sumOf { it.cash.toLong() })
        set(Metric.MONEY_SPENT, days.sumOf { it.spent.toLong() })
        set(Metric.TOTAL_XP, days.sumOf { it.xp.toLong() })
        set(Metric.ITEMS_USED, days.sumOf { it.healItems.toLong() })
        set(Metric.EASY_PRESSES, days.sumOf { it.buttons[4].toLong() })
        set(Metric.GOOD_PRESSES, days.sumOf { it.buttons[3].toLong() })
        set(Metric.SHINIES, max(days.sumOf { it.shinies.toLong() }, state.mons.count { it.shiny }.toLong()))
        set(Metric.DAYS_STUDIED, days.count { it.reviews > 0 }.toLong())
        set(Metric.GOALS_REACHED, max(state.history.entries.count { it.value.reviews >= DailyGoal.forDay(state, it.key) }, max(state.bestStreakDays, state.streakDays)).toLong())
        set(Metric.BALLS_THROWN, state.ballCatches.filterKeys { it != "auto" }.values.sumOf { it.toLong() })
    }
}
