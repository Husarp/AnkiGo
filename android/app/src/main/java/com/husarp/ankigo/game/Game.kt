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
        refreshNext() // the daily goal count restarted: the predicted next one changes with it
        return listOf(GameEvent.DayChanged(today))
    }

    /** One answer from AnkiDroid -> bookkeeping + (every cardsPerRound answers) one battle round. */
    fun onAnswer(a: Answer): List<GameEvent> {
        val events = answer(a).toMutableList()
        StudyStats.record(state, events)
        Achievements.settle(data, state, clock(), events) // R4-03: counts these events once (an Ignored answer has none), then adds AchievementReached
        return events
    }

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
        Healing.restBench(data, state) // every counted answer (catch-up ones too): the bench heals 0.5 % of max HP, exactly

        // Daily goal: once a day, the first time the count reaches it (also if the goal was lowered during the day).
        if (state.reviewsToday >= state.settings.dailyAverage && state.lastGoalDay != state.day) {
            val streakBefore = if (state.lastGoalDay == previousDay(state.day)) state.streakDays else 0 // goal days in a row before today
            state.streakDays = streakBefore + 1
            state.lastGoalDay = state.day
            state.bestStreakDays = maxOf(state.bestStreakDays, state.streakDays)
            events += GameEvent.Info("Daily goal reached!")
            Economy.payDailyBonus(state, streakBefore, events) // once a day: this moment never repeats on a day
        }

        Economy.onReview(data, state, rng, events)
        Badges.onReviews(state, events, clock())

        events += ensureEncounter()
        val battle = state.battle ?: return events
        battle.tally += points
        battle.answers++
        val n = state.settings.cardsPerRound.coerceIn(1, 4)
        if (battle.answers < n) {
            events += GameEvent.Charge(battle.tally, battle.answers, n)
            refreshNext()
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
        refreshNext()
        return events
    }

    /** The wild one fainted: catch or defeat it by the auto rule (decide() honours a one-off override). */
    private fun resolveWild(battle: Battle, main: Mon, events: MutableList<GameEvent>) {
        val now = clock()
        val plan = Catching.plan(data, state, battle)
        when (plan.outcome) {
            Outcome.CATCH -> {
                Catching.addToCollection(data, state, battle.wild, battle.tier, null, now, events)
                state.ballCatches.merge("auto", 1, Int::plus)
                plan.duplicate?.let { swapDuplicate(it, battle.wild, events) }
                if (Tuning.CATCH_GIVES_XP) Progression.rewardCatch(data, state, main, battle.wild, now, rng, events)
                Healing.healMain(data, main, HealReason.CATCH, events) // after the rewards
            }
            Outcome.DEFEAT -> {
                Progression.rewardDefeat(data, state, main, battle.wild, now, rng, events)
                Healing.healMain(data, main, HealReason.DEFEAT, events) // after XP, level-ups and evolution
                Trainer.addDefeatXp(state, battle.tier, events)
                state.defeats++
                Economy.onDefeat(state, events)
                plan.duplicate?.let { events += GameEvent.Duplicate(battle.wild.name, battle.wild.level, it.bestLevel, DuplicateResult.DEFEATED) }
            }
        }
    }

    /**
     * Auto-release duplicates, after the wild one was caught: lets the replaced copy go like a manual release (the Ankimon
     * export removes it), or keeps both when it may not go. The copy is checked again here; it is never in the team.
     */
    private fun swapDuplicate(dup: Duplicates.Verdict, wild: Mon, events: MutableList<GameEvent>) {
        val old = dup.release?.takeIf { it in state.mons && Duplicates.keepReason(data, state, it) == null }
        if (old == null) {
            events += GameEvent.Duplicate(wild.name, wild.level, dup.bestLevel, DuplicateResult.KEPT_BOTH,
                keptBecause = (dup.kept ?: dup.release?.let { Duplicates.keepReason(data, state, it) })?.label)
            return
        }
        state.mons.remove(old)
        rememberReleased(old.ankimonId ?: old.uid)
        events += GameEvent.Duplicate(wild.name, wild.level, old.level, DuplicateResult.SWAPPED, releasedName = old.shownName)
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
        // Ankimon rolls the wild one when it appears, with that moment's state; the seed only fixes the random draws.
        val r = state.nextSeed?.let { Random(it) } ?: rng
        val level = Encounters.wildLevel(main.level, r)
        val roll = Encounters.roll(data, state, level, r)

        val wild = Encounters.makeWild(data, roll, level, rng, now)
        val newSpecies = roll.species !in state.caught
        state.markSeen(roll.species, now)
        val fullHp = StatCalc.maxHp(data, wild)
        val encounterHp = Tuning.wildHp(fullHp, main.level, state.settings.fasterStart)
        wild.hp = encounterHp
        state.battle = Battle(wild, roll.tier, startedAt = now, wildMaxHp = encounterHp.takeIf { it != fullHp })

        // Seed of the one after this; the prediction (widget silhouette, sprite prefetch) follows the current state.
        state.nextSeed = rng.nextLong()
        refreshNext()

        return listOf(
            GameEvent.Encounter(
                wild.species, wild.name, wild.level, wild.shiny, roll.tier,
                wild.hp, encounterHp, willCatch(), newSpecies,
            ),
        )
    }

    /**
     * Prediction of the next encounter (nextSpecies/Tier/Shiny): the same level and roll a fight starting now would get from
     * [GameState.nextSeed]. Only a preview; the real roll happens when the encounter appears (ensureEncounter).
     */
    private fun refreshNext() {
        val seed = state.nextSeed ?: return
        val main = state.main ?: return
        val r = Random(seed)
        val upcoming = Encounters.roll(data, state, Encounters.wildLevel(main.level, r), r)
        state.nextSpecies = upcoming.species
        state.nextTier = upcoming.tier
        state.nextShiny = upcoming.shiny
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
        val events = mutableListOf<GameEvent>(GameEvent.BallThrow(ball, chance, success, state.count(ball), battle.failedThrows + 1)) // the fight itself counts its throws (saved with it)
        if (success) {
            Catching.addToCollection(data, state, battle.wild, battle.tier, ball, clock(), events)
            state.ballCatches.merge(ball, 1, Int::plus)
            state.main?.let { Healing.healMain(data, it, HealReason.CATCH, events) }
            state.battle = null
            events += ensureEncounter()
        } else {
            battle.failedThrows++
            events += GameEvent.Info("${battle.wild.name} broke free!")
        }
        StudyStats.record(state, events)
        Achievements.settle(data, state, clock(), events)
        return events
    }

    /** Optional tap: Poké Doll / Fluffy Tail: the wild one leaves, a new one appears. */
    fun escape(item: String): List<GameEvent> {
        val battle = state.battle ?: return emptyList()
        if (Items.def(item)?.kind != ItemKind.ESCAPE || !Economy.take(state, item)) return emptyList()
        val events = mutableListOf<GameEvent>(GameEvent.Fled(battle.wild.name))
        state.battle = null
        events += ensureEncounter()
        Achievements.bump(state, Metric.ITEMS_USED)
        Achievements.settle(data, state, clock(), events)
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
        state.markSeen(species, now)
        state.caught += species
        state.starterChosen = true
        state.cash = Tuning.START_CASH
        state.day = dayKey()
        Shop.ensureToday(data, state)
        val events = mutableListOf<GameEvent>(GameEvent.Info("${mon.name} is your partner!"))
        events += ensureEncounter()
        Achievements.check(state, data, now, events, silent = true) // every row is looked at once now: later tiers come with their celebration
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
            refreshNext() // the wild level follows the main's level
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

    /** Why [cycleMain] can't work right now (shown under the greyed menu item), or null when it can. */
    fun cycleMainBlocker(): String? = when {
        state.team.isEmpty() -> "Pick your first Pokémon first"
        state.team.size < 2 -> "Needs 2+ Pokémon in your team"
        else -> null
    }

    /** Ankimon team cycle: the next team member becomes the main. */
    fun cycleMain(): ActionResult {
        cycleMainBlocker()?.let { return ActionResult(false, it) }
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

    /** Marks / unmarks an owned Pokémon (not an unsupported one) as a favourite. */
    fun setFavourite(uid: String, on: Boolean): ActionResult {
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        mon.favourite = on
        return ActionResult(true, if (on) "${mon.shownName} is a favourite now." else "${mon.shownName} is no longer a favourite.")
    }

    /**
     * The player opened [uid]'s details: its NEW mark (Box tile / Team row) is gone from now on. Only a Pokémon that is NEW today is
     * remembered, so [GameState.viewedMons] stays small (it is saved without uids of released Pokémon). No events.
     */
    fun markViewed(uid: String): List<GameEvent> {
        val mon = state.mon(uid) ?: return emptyList()
        if (mon.caughtAt != 0L && dayKey(mon.caughtAt) == dayKey()) state.viewedMons += uid
        return emptyList()
    }

    /**
     * Picks which look (sprite) [uid] shows: any species of its evolution line up to its current one ([Looks.choices]);
     * null or its own species = the current look again. Purely visual. A species outside the line is refused.
     */
    fun setLook(uid: String, species: Int?): ActionResult {
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        if (species == null || species == mon.species) {
            mon.look = null
            return ActionResult(true, "${mon.shownName} shows its own look again.")
        }
        val pick = Looks.choices(data, mon).firstOrNull { it.num == species }
            ?: return ActionResult(false, "${mon.shownName} can't look like that.")
        mon.look = pick.num
        return ActionResult(true, "${mon.shownName} now looks like ${pick.name}.")
    }

    fun release(uid: String): ActionResult {
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        if (mon.favourite) return ActionResult(false, "Remove ${mon.shownName} from favourites first.")
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
