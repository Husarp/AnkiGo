package com.husarp.ankigo.game

// R4-03 / PLAN 79: THE list of achievements. Changing the list (titles, thresholds, new rows) is an edit of this file only.
// Rules for an edit: an id is stable and never reused (saves remember dates by id); a tiered row has exactly 5 thresholds,
// strictly ascending; a one-off has 1. A row that is new to a save is looked at silently the first time (no flood of
// celebrations), see Achievements.check. What a row counts is a Metric (Achievements.kt).
//
// LIST: the proposal in docs/design/round4.html section 3 with Adam's edits (.loki/picks/round4-achievements.txt, R4-V2b): "Best combo",
// "Good presses" and "Easy presses" are dropped (their counters stay in the engine; their ids are never reused; saved dates for them
// are kept as unknown ids), groups fixed: 32 tiered rows + the 11 one-offs (group "Badges"). Two of them are "external" (R4-07): "Study time" (AnkiGo's own timer files, in MINUTES) and
// "New cards learned" (AnkiDroid's history) are measured outside the game save and fed in by stats/AchievementFeed.kt
// through Achievements.feed. ROWS are listed in page order: group by group in [GROUPS] order.

/**
 * One achievement row. [metric] is null for a one-off that IS an Ankimon badge ([ankimonBadge]: reached when that id is in
 * GameState.badges). [tierBadges]: a tiered row whose tiers ARE Ankimon badges (badge id per tier, parallel to [tiers]): the
 * badge's own event/date is used and no second AchievementReached is sent for that tier.
 */
class AchRow(
    val id: String,
    val group: String,                // page section, in page order: Study, Battle, Collection, Economy, Special, Badges
    val title: String,
    val desc: String,                 // short: "Cards you answer while AnkiGo is running"
    val metric: Metric?,
    val tiers: List<Long>,            // thresholds, ascending; 5 for a tiered row, 1 for a one-off
    val icon: String,                 // key the page draws later
    val ankimonBadge: Int? = null,    // Ankimon badges.json id of a one-off that is today's badge (5-10, 16-20)
    val tierBadges: List<Int> = emptyList(),
) {
    val oneOff get() = tiers.size == 1
}

object AchievementTable {
    /** Tier names; the index is the tier id 0..4 (ids are stable, the names may change). */
    val TIER_NAMES = listOf("Common", "Rare", "Epic", "Legendary", "Mythical")

    /** The page's groups, in page order (every row's group is one of these, and ROWS follow this order). */
    val GROUPS = listOf("Study", "Battle", "Collection", "Economy", "Special", "Badges")

    fun tierName(tier: Int): String = TIER_NAMES.getOrElse(tier) { "Tier ${tier + 1}" }

    fun row(id: String): AchRow? = ROWS.firstOrNull { it.id == id }

    private fun tiered(
        id: String, group: String, title: String, desc: String, metric: Metric, icon: String,
        a: Long, b: Long, c: Long, d: Long, e: Long, tierBadges: List<Int> = emptyList(),
    ) = AchRow(id, group, title, desc, metric, listOf(a, b, c, d, e), icon, tierBadges = tierBadges)

    /** A one-off that is today's Ankimon badge [badge] (same meaning, same id for the import/export). */
    private fun badge(badge: Int, id: String, title: String, desc: String, icon: String) =
        AchRow(id, "Badges", title, desc, null, listOf(1L), icon, ankimonBadge = badge)

    val ROWS: List<AchRow> = listOf(
        // ---- Study ----
        tiered("answers", "Study", "Answers given", "Cards you answer while AnkiGo is running", Metric.ANSWERS, "cards", 500, 5_000, 25_000, 100_000, 250_000),
        tiered("streak-days", "Study", "Best streak", "Most days in a row with the daily goal reached", Metric.BEST_STREAK_DAYS, "flame", 3, 7, 30, 100, 365),
        // Keeps the Ankimon badges 1, 2, 4, 12, 13 as its tiers; badge 3 (300 cards) is still given, it just has no tier here.
        tiered("cards-day", "Study", "Cards in one day", "Most answers in a single day", Metric.BEST_DAY, "calendar", 100, 200, 500, 1_000, 2_000,
            tierBadges = listOf(1, 2, 4, 12, 13)),
        tiered("days-studied", "Study", "Days studied", "Days with at least one answer", Metric.DAYS_STUDIED, "days", 7, 30, 100, 365, 1_000),
        // External (fed by stats/AchievementFeed.kt): the thresholds of Study time are minutes: 1 h, 10 h, 50 h, 200 h, 500 h.
        tiered("study-time", "Study", "Study time", "Time spent answering, measured by AnkiGo", Metric.STUDY_MINUTES, "clock", 60, 600, 3_000, 12_000, 30_000),
        tiered("new-cards", "Study", "New cards learned", "Cards answered for the very first time", Metric.NEW_CARDS, "new-card", 50, 500, 2_000, 5_000, 10_000),
        tiered("goals", "Study", "Daily goal reached", "Days you reached the daily goal (not in a row)", Metric.GOALS_REACHED, "target", 5, 30, 100, 300, 1_000),

        // ---- Battle (battle and progress) ----
        tiered("defeats", "Battle", "Wild Pokémon defeated", "Wild Pokémon your main knocked out or caught", Metric.DEFEATS, "crossed-swords", 10, 100, 1_000, 5_000, 15_000),
        tiered("damage-dealt", "Battle", "Damage dealt", "HP your Pokémon took off wild ones", Metric.DAMAGE_DEALT, "sword", 1_000, 20_000, 200_000, 1_000_000, 3_000_000),
        tiered("damage-taken", "Battle", "Damage taken", "HP your Pokémon lost", Metric.DAMAGE_TAKEN, "heart-broken", 500, 10_000, 100_000, 500_000, 1_500_000),
        tiered("damage-blocked", "Battle", "Damage blocked", "Damage you didn't take thanks to resisting", Metric.DAMAGE_BLOCKED, "shield", 200, 5_000, 50_000, 200_000, 600_000),
        tiered("misses", "Battle", "Misses", "Attacks that missed, yours or the wild one's", Metric.MISSES, "miss", 10, 100, 1_000, 5_000, 15_000),
        tiered("super-effective", "Battle", "Super-effective hits", "Your hits that were super effective", Metric.SUPER_EFFECTIVE, "bolt", 25, 250, 2_500, 10_000, 30_000),
        tiered("crits", "Battle", "Critical hits", "Your critical hits (only while critical hits are on)", Metric.CRITS, "crit", 5, 50, 500, 2_000, 5_000),
        tiered("total-xp", "Battle", "Total XP", "XP all your Pokémon have earned", Metric.TOTAL_XP, "xp", 10_000, 100_000, 1_000_000, 5_000_000, 20_000_000),
        tiered("total-levels", "Battle", "Total levels", "All your Pokémon's levels added up", Metric.TOTAL_LEVELS, "level", 100, 1_000, 5_000, 15_000, 40_000),
        tiered("highest-level", "Battle", "Highest level", "Level of your best Pokémon", Metric.HIGHEST_LEVEL, "trophy", 10, 25, 50, 75, 100),
        tiered("evolutions", "Battle", "Evolutions", "Times one of your Pokémon evolved", Metric.EVOLUTIONS, "evolve", 1, 10, 50, 150, 400),

        // ---- Collection ----
        tiered("catches", "Collection", "Pokémon caught", "Every catch, duplicates too", Metric.CATCHES, "ball", 10, 100, 500, 2_000, 5_000),
        tiered("dex-caught", "Collection", "Pokédex caught", "Different species caught", Metric.DEX_CAUGHT, "dex-caught", 25, 100, 250, 500, 800),
        tiered("dex-seen", "Collection", "Pokédex seen", "Different species met", Metric.DEX_SEEN, "dex", 50, 150, 400, 700, 1_000),
        tiered("types", "Collection", "Types caught", "Different types among your Pokémon (18 in all)", Metric.TYPES_CAUGHT, "types", 3, 6, 10, 15, 18),
        tiered("balls-thrown", "Collection", "Balls thrown", "Balls you threw at wild Pokémon", Metric.BALLS_THROWN, "ball-throw", 5, 50, 250, 1_000, 3_000),
        tiered("ball-catches", "Collection", "Ball catches", "Catches with a thrown ball (not the auto-catch rule)", Metric.BALL_CATCHES, "ball-catch", 1, 10, 50, 200, 500),

        // ---- Economy ----
        tiered("money-earned", "Economy", "Money earned", "₽ earned from answers, defeats and goal bonuses", Metric.MONEY_EARNED, "coin", 2_000, 20_000, 100_000, 365_000, 1_000_000),
        tiered("money-spent", "Economy", "Money spent", "₽ spent in the Shop", Metric.MONEY_SPENT, "bag", 1_000, 10_000, 75_000, 250_000, 750_000),
        tiered("items-used", "Economy", "Items used", "Potions, stones, held items given", Metric.ITEMS_USED, "potion", 5, 50, 250, 1_000, 3_000),
        tiered("tms", "Economy", "TMs taught", "Moves taught with a TM", Metric.TMS_TAUGHT, "tm", 1, 5, 20, 50, 100),
        tiered("items-found", "Economy", "Items found", "Random items after answers", Metric.ITEMS_FOUND, "gift", 1, 25, 100, 500, 1_500),

        // ---- Special ----
        tiered("shinies", "Special", "Shinies caught", "Shiny Pokémon caught", Metric.SHINIES, "sparkle", 1, 3, 10, 25, 50),
        tiered("legendary-mythical", "Special", "Legendary & Mythical caught", "Legendary or Mythical species caught", Metric.LEGENDARY_MYTHICAL, "legendary", 1, 3, 10, 25, 50),
        tiered("comebacks", "Special", "Comebacks", "Wins with your main under 10% HP", Metric.COMEBACKS, "heart", 1, 10, 50, 200, 500),

        // ---- Badges: today's one-off Ankimon badges (5-10, 16-20; 11 leech and 14/15 eggs are not awarded) ----
        badge(7, "first-catch", "First Pokémon caught", "Your very first catch", "badge-ball"),
        badge(17, "first-normal", "First Normal Pokémon caught", "First catch of an ordinary (non-rare) Pokémon", "badge-normal"),
        badge(18, "first-baby", "First Baby Pokémon caught", "First catch of a Baby Pokémon", "badge-baby"),
        badge(8, "first-ultra", "First Ultra Beast caught", "First Ultra Beast", "badge-ultra"),
        badge(9, "first-legendary", "First Legendary caught", "First Legendary Pokémon", "badge-legendary"),
        badge(10, "first-mythical", "First Mythical caught", "First Mythical Pokémon", "badge-mythical"),
        badge(5, "first-level-up", "Levelled up a Pokémon", "Any Pokémon gains a level", "badge-level"),
        badge(6, "first-item", "Found the first item", "First random item after an answer", "badge-item"),
        badge(16, "first-evolution", "Evolved a Pokémon", "First evolution", "badge-evolve"),
        badge(19, "first-fossil", "Revived a fossil", "Turned a fossil into a Pokémon", "badge-fossil"),
        badge(20, "first-heal", "Healing does good", "Used a healing item", "badge-heal"),
    )
}
