package com.husarp.ankigo.remind

import android.content.Context
import android.content.SharedPreferences

/** When the "cards due" reminder is checked: at one fixed time, at the time AnkiGo picked, or every few hours. */
enum class DueMode { FIXED, APP_PICKS, EVERY_HOURS }

/** What the own reminder goal counts: reviews done today, or new cards started today. */
enum class GoalKind { REVIEWS, NEW_CARDS }

/**
 * All reminder settings (PLAN 85). Times are minutes of the day (0..1439). Every reminder starts OFF, so an update never
 * brings surprise notifications or a permission prompt; the settings screen asks for the permission when one is switched on.
 */
data class ReminderPrefs(
    // Cards due
    val dueOn: Boolean = false,
    val dueMin: Int = 20,
    val dueMode: DueMode = DueMode.FIXED,
    val dueTime: Int = 18 * 60,
    val dueEveryHours: Int = 2,
    val minGapHours: Int = 3,
    val dailyCap: Int = 1,
    /** True: all decks count; false: only [deckIds]. The chosen set is kept while "all" is on. */
    val allDecks: Boolean = true,
    val deckIds: Set<Long> = emptySet(),
    // Own reminder goal
    val goalOn: Boolean = false,
    val goalKind: GoalKind = GoalKind.REVIEWS,
    val goalReviews: Int = 100,
    val goalNewCards: Int = 20,
    // Game
    val streakOn: Boolean = false,
    val streakTime: Int = 20 * 60,
    val dailyGoalOn: Boolean = false,
    val dailyGoalTime: Int = 19 * 60,
    val shopOn: Boolean = false,
    // General
    val quietOn: Boolean = true,
    val quietFrom: Int = 22 * 60,
    val quietTo: Int = 8 * 60,
) {
    /** The count the own goal waits for (100 reviews or 20 new cards unless changed). */
    val goalCount: Int get() = if (goalKind == GoalKind.REVIEWS) goalReviews else goalNewCards

    fun withGoalCount(n: Int): ReminderPrefs = if (goalKind == GoalKind.REVIEWS) copy(goalReviews = n) else copy(goalNewCards = n)

    /** True when at least one reminder is switched on (nothing on: no alarm, no work). */
    val anyOn: Boolean get() = dueOn || goalOn || streakOn || dailyGoalOn || shopOn

    /** Every value inside its allowed range (a hand-edited or half-written file can't break the rules). */
    fun sane(): ReminderPrefs = copy(
        dueMin = dueMin.coerceIn(1, 9999),
        dueTime = dueTime.coerceIn(0, 1439),
        dueEveryHours = dueEveryHours.coerceIn(1, 24),
        minGapHours = minGapHours.coerceIn(0, 24),
        dailyCap = dailyCap.coerceIn(1, 2),
        goalReviews = goalReviews.coerceIn(1, 99_999),
        goalNewCards = goalNewCards.coerceIn(1, 99_999),
        streakTime = streakTime.coerceIn(0, 1439),
        dailyGoalTime = dailyGoalTime.coerceIn(0, 1439),
        quietFrom = quietFrom.coerceIn(0, 1439),
        quietTo = quietTo.coerceIn(0, 1439),
    )
}

/** Loads and saves [ReminderPrefs] in their own SharedPreferences file "reminders" (not part of the game save). */
object ReminderStore {
    const val FILE = "reminders"

    fun prefs(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(ctx: Context): ReminderPrefs = load(prefs(ctx))

    /** Saves and re-arms the reminder alarm (on, off or a new time). */
    fun save(ctx: Context, p: ReminderPrefs) {
        save(prefs(ctx), p)
        ReminderScheduler.rearm(ctx)
    }

    /** Changes some settings: `ReminderStore.update(ctx) { it.copy(dueOn = true) }`. */
    fun update(ctx: Context, change: (ReminderPrefs) -> ReminderPrefs) = save(ctx, change(load(ctx)))

    fun load(p: SharedPreferences): ReminderPrefs {
        val d = ReminderPrefs()
        return ReminderPrefs(
            dueOn = p.getBoolean("dueOn", d.dueOn),
            dueMin = p.getInt("dueMin", d.dueMin),
            dueMode = enumOf(p.getString("dueMode", null), d.dueMode),
            dueTime = p.getInt("dueTime", d.dueTime),
            dueEveryHours = p.getInt("dueEveryHours", d.dueEveryHours),
            minGapHours = p.getInt("minGapHours", d.minGapHours),
            dailyCap = p.getInt("dailyCap", d.dailyCap),
            allDecks = p.getBoolean("allDecks", d.allDecks),
            deckIds = p.getStringSet("deckIds", null).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            goalOn = p.getBoolean("goalOn", d.goalOn),
            goalKind = enumOf(p.getString("goalKind", null), d.goalKind),
            goalReviews = p.getInt("goalReviews", d.goalReviews),
            goalNewCards = p.getInt("goalNewCards", d.goalNewCards),
            streakOn = p.getBoolean("streakOn", d.streakOn),
            streakTime = p.getInt("streakTime", d.streakTime),
            dailyGoalOn = p.getBoolean("dailyGoalOn", d.dailyGoalOn),
            dailyGoalTime = p.getInt("dailyGoalTime", d.dailyGoalTime),
            shopOn = p.getBoolean("shopOn", d.shopOn),
            quietOn = p.getBoolean("quietOn", d.quietOn),
            quietFrom = p.getInt("quietFrom", d.quietFrom),
            quietTo = p.getInt("quietTo", d.quietTo),
        ).sane()
    }

    fun save(p: SharedPreferences, v: ReminderPrefs) {
        val s = v.sane()
        p.edit()
            .putBoolean("dueOn", s.dueOn).putInt("dueMin", s.dueMin).putString("dueMode", s.dueMode.name)
            .putInt("dueTime", s.dueTime).putInt("dueEveryHours", s.dueEveryHours).putInt("minGapHours", s.minGapHours)
            .putInt("dailyCap", s.dailyCap).putBoolean("allDecks", s.allDecks)
            .putStringSet("deckIds", s.deckIds.mapTo(HashSet()) { it.toString() })
            .putBoolean("goalOn", s.goalOn).putString("goalKind", s.goalKind.name)
            .putInt("goalReviews", s.goalReviews).putInt("goalNewCards", s.goalNewCards)
            .putBoolean("streakOn", s.streakOn).putInt("streakTime", s.streakTime)
            .putBoolean("dailyGoalOn", s.dailyGoalOn).putInt("dailyGoalTime", s.dailyGoalTime)
            .putBoolean("shopOn", s.shopOn)
            .putBoolean("quietOn", s.quietOn).putInt("quietFrom", s.quietFrom).putInt("quietTo", s.quietTo)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default
}
