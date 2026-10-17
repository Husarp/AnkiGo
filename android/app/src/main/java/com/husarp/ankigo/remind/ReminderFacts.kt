package com.husarp.ankigo.remind

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.Report
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.Shop
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.lookSpecies
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * What the reminders need to know about the game, as a small snapshot: the background tick must not read the save file.
 * [GameFactsWriter] keeps it up to date whenever the game changes; the rules compare [day] with the real game day, so a
 * snapshot that is a day or more old is handled (the counts count for [day] only).
 */
data class ReminderFacts(
    /** Game day ("yyyy-MM-dd") the snapshot is about; empty = no game yet. */
    val day: String = "",
    val dayStartHour: Int = Tuning.DAY_START_HOUR,
    val reviewsToday: Int = 0,
    val dailyGoal: Int = 0,
    val streakDays: Int = 0,
    val lastGoalDay: String? = null,
    /** Shop items worth having (any ball, an evolution item that evolves a team member) in [day]'s stock... */
    val shopToday: List<String> = emptyList(),
    /** ...and in the stock of the next game day (what the shop will hold when the new day starts). */
    val shopTomorrow: List<String> = emptyList(),
    /** Full path of the cached picture of the current main Pokémon (the notification's large icon); null = none cached (no icon). */
    val mainSprite: String? = null,
) {
    val valid: Boolean get() = day.isNotEmpty()

    companion object {
        val NONE = ReminderFacts()

        /** The snapshot of a loaded game; [NONE] before the first game day or the starter. [mainSprite] = [ReminderFacts.mainSprite]. */
        fun from(data: GameData, state: GameState, mainSprite: String? = null): ReminderFacts {
            if (state.day.isEmpty() || !state.starterChosen) return NONE
            val today = if (state.shopDay == state.day) state.shopItems.toList() else Shop.stockFor(data, state.day, state.trainerLevel).first
            val tomorrow = nextDay(state.day)?.let { Shop.stockFor(data, it, state.trainerLevel).first }.orEmpty()
            return ReminderFacts(
                day = state.day,
                dayStartHour = state.settings.dayStartHour.coerceIn(0, 23),
                reviewsToday = state.reviewsToday,
                dailyGoal = state.settings.dailyAverage,
                streakDays = state.streakDays,
                lastGoalDay = state.lastGoalDay,
                shopToday = worthHaving(data, state, today),
                shopTomorrow = worthHaving(data, state, tomorrow),
                mainSprite = mainSprite,
            )
        }

        /** The items worth a notification: any ball, or an evolution item that evolves a Pokémon of the team (at any time of day). */
        fun worthHaving(data: GameData, state: GameState, items: List<String>): List<String> = items.filter { id ->
            when (Items.def(id)?.kind) {
                ItemKind.BALL -> true
                ItemKind.EVOLUTION -> state.teamMons().any { Progression.itemEvolutionAnyTime(data, it, id) != null }
                else -> false
            }
        }

        fun nextDay(day: String): String? = try {
            LocalDate.parse(day).plusDays(1).toString()
        } catch (_: DateTimeParseException) {
            null
        }

        const val FILE = "reminders_facts"

        fun prefs(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        fun load(ctx: Context): ReminderFacts = load(prefs(ctx))

        fun load(p: SharedPreferences): ReminderFacts = ReminderFacts(
            day = p.getString("day", null).orEmpty(),
            dayStartHour = p.getInt("dayStartHour", Tuning.DAY_START_HOUR).coerceIn(0, 23),
            reviewsToday = p.getInt("reviewsToday", 0),
            dailyGoal = p.getInt("dailyGoal", 0),
            streakDays = p.getInt("streakDays", 0),
            lastGoalDay = p.getString("lastGoalDay", null),
            shopToday = split(p.getString("shopToday", null)),
            shopTomorrow = split(p.getString("shopTomorrow", null)),
            mainSprite = p.getString("mainSprite", null)?.takeIf { it.isNotEmpty() },
        )

        fun save(p: SharedPreferences, f: ReminderFacts) {
            p.edit()
                .putString("day", f.day).putInt("dayStartHour", f.dayStartHour).putInt("reviewsToday", f.reviewsToday)
                .putInt("dailyGoal", f.dailyGoal).putInt("streakDays", f.streakDays).putString("lastGoalDay", f.lastGoalDay)
                .putString("shopToday", f.shopToday.joinToString(",")).putString("shopTomorrow", f.shopTomorrow.joinToString(","))
                .putString("mainSprite", f.mainSprite)
                .apply()
        }

        private fun split(s: String?): List<String> = s.orEmpty().split(',').filter { it.isNotEmpty() }
    }
}

/**
 * Writes the [ReminderFacts] snapshot on the main thread when the game changed. Throttled: the first change starts a
 * [DELAY_MS] timer, changes inside it ride along (the write reads the game as it is then), and nothing is written when
 * the snapshot did not change.
 */
object GameFactsWriter {
    private const val DELAY_MS = 5_000L
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var pending = false
    private var last: ReminderFacts? = null

    /** The one `GameRepo` listener (AnkiGoApp.onCreate). Main thread. */
    fun changed(ctx: Context) {
        if (pending) return
        pending = true
        val app = ctx.applicationContext
        main.postDelayed({ pending = false; writeNow(app) }, DELAY_MS)
    }

    /** The cached static picture of the main Pokémon (as it looks: its chosen look, shiny or not), or null when it is not on disk yet. */
    private fun mainSpritePath(ctx: Context, data: GameData, state: GameState): String? {
        val main = state.main ?: return null
        return Sprites.staticFile(ctx, main.lookSpecies(data).num, main.shiny).takeIf { it.isFile }?.path
    }

    /** Writes the snapshot now if the game is loaded and it changed. Main thread. */
    fun writeNow(ctx: Context) {
        val data = GameRepo.data ?: return
        val state = GameRepo.state ?: return
        if (GameRepo.status != GameRepo.Status.READY) return
        val facts = try {
            ReminderFacts.from(data, state, mainSpritePath(ctx, data, state))
        } catch (e: Exception) {
            Report.fail("REMIND", "reading the game for the reminders", e)
            return
        }
        if (facts == last) return
        ReminderFacts.save(ReminderFacts.prefs(ctx), facts)
        last = facts
    }
}
