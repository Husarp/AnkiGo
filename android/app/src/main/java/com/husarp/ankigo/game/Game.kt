package com.husarp.ankigo.game

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlin.random.Random

// TICKET T11 owns this file. Public signatures are fixed (the UI calls them); add private helpers freely.

private const val RELEASED_KEPT = 5_000 // how many released Ankimon ids the game remembers (GameState.releasedAnkimon)

/**
 * The game controller: turns AnkiDroid answers into Ankimon rounds and between-battle actions into state changes.
 * Single-threaded: call everything on the main thread; the caller saves after each call (GameRepo does).
 */
class Game(val data: GameData, val state: GameState, val rng: Random = Random.Default, val clock: () -> Long = { System.currentTimeMillis() }) {

    /** Game day key "yyyy-MM-dd" for [now]; a day starts at settings.dayStartHour (phone time zone). */
    fun dayKey(now: Long = clock()): String =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
            .minusHours(state.settings.dayStartHour.coerceIn(0, 23).toLong()).toLocalDate().toString()

    /** Day rollover if the day changed (daily counters, goal streak, shop). Safe to call often. */
    fun tick(): List<GameEvent> {
        val today = dayKey()
        if (state.day == today) return emptyList()
        val firstEver = state.day.isEmpty()
        state.day = today
        if (firstEver) return emptyList()
        state.reviewsToday = 0
        state.cashToday = 0
        state.buttonsToday.fill(0)
        // An undo whose redo never came must not swallow a real answer days later.
        state.redo.clear()
        Shop.ensureToday(data, state)
        return listOf(GameEvent.DayChanged(today))
    }

    /** One answer from AnkiDroid -> bookkeeping + (every cardsPerRound answers) one battle round. */
    fun onAnswer(a: Answer): List<GameEvent> = answer(a).also { StudyStats.record(state, it) }

    private fun answer(a: Answer): List<GameEvent> {
        val events = tick().toMutableList()

        val pending = state.redo[a.cardId] ?: 0
        if (pending > 0) {
            if (pending == 1) state.redo.remove(a.cardId) else state.redo[a.cardId] = pending - 1
            events += GameEvent.Ignored("Answer repeated after an undo")
            return events
        }

        val main = state.main ?: return events // no starter yet: nothing to count

        val button = a.button.coerceIn(0, 4)
        val points = Tuning.POINTS[button]
        state.reviewsToday++
        state.totalReviews++
        state.buttonsToday[button]++
        state.cardStreak = if (button == 1) 0 else state.cardStreak + 1
        if (state.cardStreak > state.bestCardStreak) state.bestCardStreak = state.cardStreak
        events += GameEvent.Counted(button, points, state.reviewsToday, state.cardStreak, a.catchUp)

        // Daily goal: once a day, the first time the count reaches it (also if the goal was lowered during the day).
        if (state.reviewsToday >= state.settings.dailyAverage && state.lastGoalDay != state.day) {
            state.streakDays = if (state.lastGoalDay == previousDay(state.day)) state.streakDays + 1 else 1
            state.lastGoalDay = state.day
            state.bestStreakDays = maxOf(state.bestStreakDays, state.streakDays)
            events += GameEvent.Info("Daily goal reached!")
        }

        Economy.onReview(data, state, rng, events)
        Badges.onReviews(state, events)

        events += ensureEncounter()
        val battle = state.battle ?: return events
        battle.tally += points
        battle.answers++
        val n = state.settings.cardsPerRound.coerceIn(1, 4)
        if (battle.answers < n) {
            events += GameEvent.Charge(battle.tally, battle.answers, n)
            return events
        }

        val multiplier = battle.tally.toDouble() / (Tuning.POINTS_PER_CARD * n)
        val enemyActs = multiplier < 1.0
        events += GameEvent.RoundStart(multiplier, battle.tally, enemyActs)
        battle.tally = 0
        battle.answers = 0
        events += BattleEngine.playRound(data, battle, main, multiplier, enemyActs, rng)

        if (battle.wild.hp <= 0) {
            resolveWild(battle, main, events)
            state.battle = null
        }
        if (main.hp <= 0) {
            // Ankimon: a fainted main is healed at once and the wild one is lost.
            main.hp = StatCalc.maxHp(data, main)
            events += GameEvent.MainHealed(main.uid, main.name, main.hp)
            state.battle = null
        }
        events += ensureEncounter() // no-op while a fight is still going
        return events
    }

    /** The wild one fainted: catch or defeat it by the auto rule (decide() honours a one-off override). */
    private fun resolveWild(battle: Battle, main: Mon, events: MutableList<GameEvent>) {
        val now = clock()
        when (Catching.decide(data, state, battle)) {
            Outcome.CATCH -> {
                Catching.addToCollection(data, state, battle.wild, battle.tier, null, now, events)
                if (Tuning.CATCH_GIVES_XP) Progression.rewardCatch(data, state, main, battle.wild, now, rng, events)
            }
            Outcome.DEFEAT -> {
                Progression.rewardDefeat(data, state, main, battle.wild, now, rng, events)
                Trainer.addDefeatXp(state, battle.tier, events)
                state.defeats++
            }
        }
    }

    /** AnkiDroid undo of [count] answers on [cardId]: the battle doesn't rewind; the redo isn't counted again. */
    fun onUndo(cardId: Long, count: Int) {
        if (count <= 0) return
        state.redo[cardId] = (state.redo[cardId] ?: 0) + count
    }

    /** Starts a wild encounter if there is none (and a main exists). */
    fun ensureEncounter(): List<GameEvent> {
        if (state.battle != null) return emptyList()
        val main = state.main ?: return emptyList()
        val now = clock()
        val level = Encounters.wildLevel(main.level, rng)
        val preRolled = state.nextSpecies?.takeIf { Encounters.isValid(data, state, it, level) }
            ?.let { Encounters.Roll(it, state.nextTier, state.nextShiny) }
        val roll = preRolled ?: Encounters.roll(data, state, level, rng)

        val wild = Encounters.makeWild(data, roll, level, rng, now)
        val newSpecies = roll.species !in state.caught
        state.seen += roll.species
        val fullHp = StatCalc.maxHp(data, wild)
        val encounterHp = Tuning.wildHp(fullHp, main.level, state.settings.fasterStart)
        wild.hp = encounterHp
        state.battle = Battle(wild, roll.tier, startedAt = now, wildMaxHp = encounterHp.takeIf { it != fullHp })

        // Pre-roll the one after this (widget silhouette, sprite prefetch).
        val upcoming = Encounters.roll(data, state, Encounters.wildLevel(main.level, rng), rng)
        state.nextSpecies = upcoming.species
        state.nextTier = upcoming.tier
        state.nextShiny = upcoming.shiny

        return listOf(
            GameEvent.Encounter(
                wild.species, wild.name, wild.level, wild.shiny, roll.tier,
                wild.hp, encounterHp, willCatch(), newSpecies,
            ),
        )
    }

    /** What the auto rule will do when the current wild one faints (for the "NEW · will catch" tag). */
    fun willCatch(): Boolean = state.battle?.let { Catching.decide(data, state, it) == Outcome.CATCH } ?: false

    /** Optional tap: one-off catch/defeat choice for this fight (null = back to the auto rule). */
    fun setOverride(o: Outcome?) { state.battle?.override = o }

    /** Optional tap: throw a ball at the current wild Pokémon (AnkiGo fix: success really catches). */
    fun throwBall(ball: String): List<GameEvent> {
        val battle = state.battle ?: return emptyList()
        if (battle.wild.hp <= 0 || Items.def(ball)?.kind != ItemKind.BALL || !Economy.take(state, ball)) return emptyList()
        val chance = Catching.ballChance(ball, data.sp(battle.wild.species).types)
        val success = rng.nextInt(100) < chance
        val events = mutableListOf<GameEvent>(GameEvent.BallThrow(ball, chance, success, state.count(ball)))
        if (success) {
            Catching.addToCollection(data, state, battle.wild, battle.tier, ball, clock(), events)
            state.battle = null
            events += ensureEncounter()
        } else {
            events += GameEvent.Info("${battle.wild.name} broke free!")
        }
        StudyStats.record(state, events)
        return events
    }

    /** Optional tap: Poké Doll / Fluffy Tail: the wild one leaves, a new one appears. */
    fun escape(item: String): List<GameEvent> {
        val battle = state.battle ?: return emptyList()
        if (Items.def(item)?.kind != ItemKind.ESCAPE || !Economy.take(state, item)) return emptyList()
        val events = mutableListOf<GameEvent>(GameEvent.Fled(battle.wild.name))
        state.battle = null
        events += ensureEncounter()
        return events
    }

    /** First run: the chosen starter joins at Lv 5 and becomes the main. */
    fun pickStarter(species: Int): List<GameEvent> {
        if (state.starterChosen || !data.species.containsKey(species)) return emptyList()
        val now = clock()
        val mon = Progression.newMon(data, species, Tuning.STARTER_LEVEL, rng, now)
        mon.caughtAt = now
        state.mons += mon
        state.team.clear()
        state.team += mon.uid
        state.seen += species
        state.caught += species
        state.starterChosen = true
        state.cash = Tuning.START_CASH
        state.day = dayKey()
        Shop.ensureToday(data, state)
        val events = mutableListOf<GameEvent>(GameEvent.Info("${mon.name} is your partner!"))
        events += ensureEncounter()
        return events
    }

    // ---- team (optional taps between answers) ----

    /**
     * Success result after a team change. When the main differs from [oldMain], the fight (if any) goes on with the new
     * one on fresh stat stages and status, and a MainChanged event says so. The new main keeps its own HP (Ankimon).
     */
    private fun changed(message: String, oldMain: String?): ActionResult {
        val events = mutableListOf<GameEvent>()
        val main = state.main
        if (main != null && main.uid != oldMain) {
            state.battle?.player = BattleSide()
            events += GameEvent.MainChanged(main.uid, main.name)
        }
        return ActionResult(true, message, events)
    }

    /** Puts [uid] first. A Pokémon from the box takes a free team place, or the last member's when the team is full. */
    fun setMain(uid: String): ActionResult {
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        val old = state.team.firstOrNull()
        if (old == uid) return ActionResult(true, "${mon.name} is already your main.")
        var message = "${mon.name} is your main now."
        if (uid in state.team) {
            state.team.remove(uid)
        } else if (state.team.size >= 6) {
            val last = state.mon(state.team.removeAt(state.team.size - 1))
            message = "${mon.name} is your main now; ${last?.name ?: "the last team member"} went to the box."
        }
        state.team.add(0, uid)
        return changed(message, old)
    }

    /** Ankimon team cycle: the next team member becomes the main. */
    fun cycleMain(): ActionResult {
        if (state.team.size < 2) return ActionResult(false, "You need at least two Pokémon in your team.")
        val old = state.team.first()
        // Ankimon cycles the first 3 only: [A,B,C,D] -> [B,C,A,D].
        state.team.add(minOf(3, state.team.size) - 1, state.team.removeAt(0))
        return changed("${state.main?.name ?: "?"} is your main now.", old)
    }

    fun moveInTeam(uid: String, delta: Int): ActionResult {
        val from = state.team.indexOf(uid)
        if (from < 0) return ActionResult(false, "That Pokémon is not in your team.")
        val to = (from + delta).coerceIn(0, state.team.size - 1)
        if (to == from) return ActionResult(false, "It can't move any further.")
        val old = state.team.first()
        state.team.removeAt(from)
        state.team.add(to, uid)
        return changed("Moved ${state.mon(uid)?.name ?: "it"} to place ${to + 1}.", old)
    }

    fun toBox(uid: String): ActionResult {
        if (uid !in state.team) return ActionResult(false, "That Pokémon is not in your team.")
        if (state.team.size <= 1) return ActionResult(false, "Your team needs at least one Pokémon.")
        val old = state.team.first()
        state.team.remove(uid)
        return changed("${state.mon(uid)?.name ?: "It"} went to the box.", old)
    }

    fun toTeam(uid: String): ActionResult {
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        if (uid in state.team) return ActionResult(false, "${mon.name} is already in your team.")
        if (state.team.size >= 6) return ActionResult(false, "Your team is full (6).")
        state.team.add(uid)
        return ActionResult(true, "${mon.name} joined your team.")
    }

    fun setXpShare(uid: String?): ActionResult {
        if (uid == null) {
            state.settings.xpShareUid = null
            return ActionResult(true, "Exp. Share is off.")
        }
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        state.settings.xpShareUid = uid
        return ActionResult(true, "${mon.name} holds the Exp. Share.")
    }

    fun release(uid: String): ActionResult {
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        if (state.mons.size <= 1) return ActionResult(false, "You can't release your only Pokémon.")
        if (state.team.size == 1 && state.team[0] == uid) {
            return ActionResult(false, "Put another Pokémon in your team before releasing ${mon.name}.")
        }
        val old = state.team.firstOrNull()
        mon.heldItem?.let { Economy.give(state, it) }
        mon.heldItem = null
        state.team.remove(uid)
        state.mons.remove(mon)
        if (state.settings.xpShareUid == uid) state.settings.xpShareUid = null
        rememberReleased(mon.ankimonId ?: mon.uid)
        return changed("${mon.name} was released.", old)
    }

    /** The Ankimon export removes released Pokémon from the PC save: remember the id (newest 5 000 kept). */
    private fun rememberReleased(id: String) {
        val set = state.releasedAnkimon
        set.remove(id)
        set.add(id)
        while (set.size > RELEASED_KEPT) set.remove(set.first())
    }

    /**
     * Gives [uid] a nickname (Ankimon: free text on the Pokémon). The text is trimmed, inner whitespace collapsed, control
     * characters dropped and cut to [Tuning.NICKNAME_MAX]; blank or equal to the species name (ignoring case) removes it.
     */
    fun rename(uid: String, text: String): ActionResult {
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        val clean = text.filterNot { it.isISOControl() && !it.isWhitespace() }
            .trim().replace(Regex("\\s+"), " ").take(Tuning.NICKNAME_MAX).trim()
        if (clean.isEmpty() || clean.equals(mon.name, ignoreCase = true)) {
            mon.nickname = null
            return ActionResult(true, "${mon.name}'s nickname was removed.")
        }
        mon.nickname = clean
        return ActionResult(true, "${mon.name} is now called $clean.")
    }

    /** The day before [day] ("yyyy-MM-dd"), or null if [day] isn't a date. */
    private fun previousDay(day: String): String? =
        try { LocalDate.parse(day).minusDays(1).toString() } catch (e: DateTimeParseException) { null }
}
