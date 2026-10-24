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
        state.cashFracNum = 0; state.cashFracDen = 1
        state.buttonsToday.fill(0)
        // An undo whose redo never came must not swallow a real answer days later.
        state.redo.clear()
        Shop.ensureToday(data, state)
        refreshNext() // the daily goal count restarted: the predicted next one changes with it
        return listOf(GameEvent.DayChanged(today))
    }

    /** Answers per battle round: always [Tuning.CARDS_PER_ROUND]; tests lower it to shorten fights. */
    internal var roundAnswers: Int = Tuning.CARDS_PER_ROUND

    /**
     * One answer from AnkiDroid -> bookkeeping + (every cardsPerRound answers) one battle round. PLAN 206: a switch queued while this
     * card showed ([requestMain]) comes in right after the answer's own events, in the same list. It is applied AFTER the answer was
     * counted for the stats and achievements: the switch counts its own wild attack (switchHit), so nothing is counted twice. An
     * Ignored answer (the redo after an undo) has no Counted event and applies nothing; the queue is cleared before it is applied.
     */
    fun onAnswer(a: Answer): List<GameEvent> {
        val events = answer(a).toMutableList()
        StudyStats.record(state, events)
        Achievements.settle(data, state, clock(), events) // R4-03: counts these events once (an Ignored answer has none), then adds AchievementReached
        if (events.any { it is GameEvent.Counted }) events += applyQueuedMain()
        return events
    }

    /** PLAN 206: the queued switch comes in now (today's [setMain] path: MainChanged, and the wild one's attack when [switchCostsAHit]). A queue gone stale (left the team, already the main) is dropped. */
    private fun applyQueuedMain(): List<GameEvent> {
        val uid = state.pendingMain ?: return emptyList()
        val cycle = state.pendingCycle
        state.pendingMain = null; state.pendingCycle = false // first: whatever happens below, the switch is never applied twice
        if (state.mon(uid) == null || uid !in state.team || state.team.firstOrNull() == uid) return emptyList()
        // a queued [cycleMain] keeps its rotation of the first three ([A,B,C] -> [B,C,A]) while the one it queued is still next in line
        return if (cycle && state.team.getOrNull(1) == uid) rotateMain().events else setMain(uid).events
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
        if (!a.neutral) state.cardStreak = if (button == 1) 0 else state.cardStreak + 1   // PLAN 155: the OK of a new card neither breaks nor builds the combo
        if (state.cardStreak > state.bestCardStreak) state.bestCardStreak = state.cardStreak
        events += GameEvent.Counted(button, points, state.reviewsToday, state.cardStreak, a.catchUp, a.neutral)
        Healing.restBench(data, state) // every counted answer (catch-up ones too): the bench heals 0.5 % of max HP, exactly

        // Daily goal: once a day, the first time the count reaches it (also if the goal was lowered during the day).
        if (state.reviewsToday >= DailyGoal.today(state) && state.lastGoalDay != state.day) {
            val streakBefore = if (state.lastGoalDay == previousDay(state.day)) state.streakDays else 0 // goal days in a row before today
            state.streakDays = streakBefore + 1
            state.lastGoalDay = state.day
            state.bestStreakDays = maxOf(state.bestStreakDays, state.streakDays)
            events += GameEvent.Info("Daily goal reached!")
            DailyGift.give(data, state, events) // R14-B2-D: once a day, this moment never repeats on a day (lastGoalDay is saved): one gift into the Bag, no money
        }

        Economy.onReview(data, state, rng, events)
        Badges.onReviews(state, events, clock())

        events += ensureEncounter()
        val battle = state.battle ?: return events
        if (a.neutral) return events   // PLAN 155: counted above (reviews, money, goal, stats), but not part of a round: no Charge, nobody attacks
        // R10-B22: who attacks depends on each answer, not on the point sum. A battle saved before keeps only the sum: restore the buttons from it.
        if (battle.buttons.size != battle.answers) { battle.buttons.clear(); battle.buttons += RoundRule.restore(battle.answers, battle.tally) }
        battle.tally += points
        battle.buttons += button
        battle.answers++
        val n = roundAnswers
        if (battle.answers < n) {
            events += GameEvent.Charge(battle.tally, battle.answers, n)
            refreshNext()
            return events
        }

        val plan = RoundRule.plan(battle.buttons.takeLast(n))
        events += GameEvent.RoundStart(plan.multiplier, battle.tally, plan.enemyActs, plan.mainActs, plan.enemyPower)
        battle.tally = 0
        battle.answers = 0
        battle.buttons.clear()
        battle.team = state.team.mapNotNull { state.mon(it) } // R17C-4: Beat Up hits once for each team member with HP
        events += BattleEngine.playRound(data, battle, main, plan.multiplier, plan.enemyActs, rng, plan.enemyPower, plan.mainActs, state.settings.moveChoice)
        settleFight(battle, main, events)
        return events
    }

    /**
     * What follows an action of the fight (a round, or the free hit of a switch): a wild one that fainted is caught or defeated,
     * a Main that fainted is healed at once and the wild one is lost (Ankimon), then the next encounter starts if the fight is over.
     */
    private fun settleFight(battle: Battle, main: Mon, events: MutableList<GameEvent>) {
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
    }

    /**
     * The wild one fainted: catch or defeat it by the auto rule (decide() honours a one-off override). Both count as a win (defeat
     * counters, trainer XP) and give the same Pokémon XP and the same win money (R13 Part C, Economy.onWin, on the day's curve).
     */
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
                // A catch is a win like a defeat: defeat counters + trainer XP by tier.
                main.defeats++
                state.defeats++
                state.catchWins++
                Trainer.addDefeatXp(state, battle.tier, events)
                Economy.onWin(state, events)
            }
            Outcome.DEFEAT -> {
                Progression.rewardDefeat(data, state, main, battle.wild, now, rng, events)
                Healing.healMain(data, main, HealReason.DEFEAT, events) // after XP, level-ups and evolution
                Trainer.addDefeatXp(state, battle.tier, events)
                state.defeats++
                Economy.onWin(state, events)
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
                wild.hp, encounterHp, willCatch(), newSpecies, wild.uid,
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

    /**
     * Optional tap: throw a ball at the current wild Pokémon (AnkiGo fix: success really catches). PLAN 171: the ball is used up either
     * way, and a miss ends the fight: the wild one breaks free and runs away (no XP, no win counted) and the next one comes, as after
     * a defeat. PLAN 170: a ball catch gives the same Pokémon XP as a defeat or an auto catch, and (R13 Part C) the same win money.
     */
    fun throwBall(ball: String): List<GameEvent> {
        val battle = state.battle ?: return emptyList()
        if (battle.wild.hp <= 0 || Items.def(ball)?.kind != ItemKind.BALL || !Economy.take(state, ball)) return emptyList()
        val chance = Catching.ballChance(ball)
        val success = rng.nextInt(100) < chance
        val events = mutableListOf<GameEvent>(GameEvent.BallThrow(ball, chance, success, state.count(ball), battle.failedThrows + 1)) // the fight itself counts its throws (saved with it)
        if (success) {
            val now = clock()
            Catching.addToCollection(data, state, battle.wild, battle.tier, ball, now, events)
            state.ballCatches.merge(ball, 1, Int::plus)
            state.main?.let { main ->
                if (Tuning.CATCH_GIVES_XP) Progression.rewardCatch(data, state, main, battle.wild, now, rng, events)
                Healing.healMain(data, main, HealReason.CATCH, events) // after the rewards
            }
            // A ball catch is a win like the auto catch: defeat counters + trainer XP by tier.
            state.main?.let { it.defeats++ }
            state.defeats++
            state.catchWins++
            Trainer.addDefeatXp(state, battle.tier, events)
            Economy.onWin(state, events)
        } else {
            events += GameEvent.Info("${battle.wild.name} broke free!")   // the ball opens (its own animation and sound) ...
            events += GameEvent.Fled(battle.wild.name)                     // ... and the wild one runs away
        }
        state.battle = null
        events += ensureEncounter()
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
        Wishlist.onCaught(state, species)
        // R9-W4-B: the starter is a gift, not an achievement: it (and its species) counts for none (Achievements.value).
        state.starterUid = mon.uid
        state.starterSeen += species; state.starterCaught += species
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
     * Round 10 (W10): with a wild one in the fight the switch costs a hit, see [switchHit]. [oldMon] = the Pokémon that was the main
     * before (looked up by [oldMain] unless the caller removed it from the game, like [release]).
     */
    private fun changed(message: String, oldMain: String?, oldMon: Mon? = oldMain?.let { state.mon(it) }): ActionResult {
        val events = mutableListOf<GameEvent>()
        var text = message
        // PLAN 206: a queued switch whose Pokémon left the team (box, released, import) or is the main now has nothing to wait for
        state.pendingMain?.let { if (it !in state.team || it == state.team.firstOrNull()) state.pendingMain = null }
        val main = state.main
        if (main != null && main.uid != oldMain) {
            val battle = state.battle
            battle?.switchOutMain(data, oldMon) // R17C-1: the rule sets' onLeave, then a fresh side for the new main
            events += GameEvent.MainChanged(main.uid, main.name)
            refreshNext() // the wild level follows the main's level
            if (battle != null && oldMon != null && switchCostsAHit(battle, oldMon, main)) {
                val hit = switchHit(battle, main)
                events += hit
                text = SwitchHitText.withHit(message, hit, named = main.name in message)
            } else if (battle != null && main.hp <= 0 && battle.wild.hp > 0) {
                // A fainted Pokémon (an import, an old save) comes in during a fight: the normal faint settlement at once, no hit.
                val settle = mutableListOf<GameEvent>()
                settleFight(battle, main, settle)
                events += settle
                text = SwitchHitText.withFaintedIn(message)
            }
        }
        return ActionResult(true, text, events)
    }

    /**
     * Round 10 (W10): a switch costs the hit only when there is a wild one to hit with and a Pokémon to hit: FREE when the old main
     * had fainted (hp 0: replacing a fainted Pokémon is not a retreat), when the wild one has no HP left, and when the Pokémon that
     * comes in is itself at 0 HP (it can't be hit; [changed] settles it at once instead: healed, the wild one is lost).
     * Round 11 (PLAN 135): and FREE while nothing was answered in this battle yet (no round played, no answer waiting in the
     * round), also after "Next battle" was tapped: the hit starts with the first answer of the fight.
     */
    private fun switchCostsAHit(battle: Battle, oldMon: Mon, incoming: Mon): Boolean =
        oldMon.hp > 0 && battle.wild.hp > 0 && incoming.hp > 0 && (battle.rounds > 0 || battle.answers > 0)

    /**
     * The turn a switch (or an item, [item]) takes: the wild one attacks [incoming] once at [Tuning.SWITCH_HIT_POWER] with the current
     * move choice ([BattleEngine.switchHit]; no round is counted, no end-of-turn damage, the round's pending answers stay as they were).
     * Then what a round does: a fainted wild one is resolved, a fainted Main is healed at once and the wild one is lost ([settleFight]).
     * Counted for the stats and the achievements like any wild attack. Returns the events: the [GameEvent.SwitchHit] marker first.
     */
    private fun switchHit(battle: Battle, incoming: Mon, item: String? = null): List<GameEvent> {
        val hit = mutableListOf<GameEvent>(GameEvent.SwitchHit(battle.wild.name, incoming.name, incoming.hp, StatCalc.maxHp(data, incoming), item))
        hit += BattleEngine.switchHit(data, battle, incoming, rng, state.settings.moveChoice)
        settleFight(battle, incoming, hit)
        StudyStats.record(state, hit)
        Achievements.settle(data, state, clock(), hit)
        return hit
    }

    /**
     * R13 Part C: uses [item] from the Bag ([ItemUse.use]). A healing or evolution item used on the Main in a fight takes one turn (one
     * dot), like a switch: after it works the wild one attacks the Main once ([switchHit], marker with [item]); your next answer
     * attacks as normal. The same moments as a switch are free of it ([itemTakesATurn]); items given to hold, TMs, fossils and items
     * used on another Pokémon never take a turn, and balls / escape items have their own rules ([throwBall], [escape]).
     */
    fun useItem(item: String, targetUid: String?): ActionResult {
        val main = state.main
        val battle = state.battle
        val kind = Items.def(item)?.kind
        val onMain = main != null && (targetUid ?: if (kind == ItemKind.HEAL) main.uid else null) == main.uid
        val turn = onMain && battle != null && (kind == ItemKind.HEAL || kind == ItemKind.EVOLUTION) && itemTakesATurn(battle, main!!)
        val r = ItemUse.use(data, state, item, targetUid, rng, clock())
        if (!r.ok || !turn || battle == null || state.battle !== battle) return r
        val mon = state.main ?: return r
        if (mon.hp <= 0 || battle.wild.hp <= 0) return r
        val hit = switchHit(battle, mon, item)
        return ActionResult(true, SwitchHitText.withHit(r.message, hit, named = mon.name in r.message), r.events + hit)
    }

    /**
     * R13 Part C: an item on the Main takes its turn at the moments a switch takes one ([switchCostsAHit]): a wild one with HP left,
     * a Main with HP, and something answered in this battle (before the first answer, also after "Next battle", it is free).
     */
    private fun itemTakesATurn(battle: Battle, main: Mon): Boolean =
        main.hp > 0 && battle.wild.hp > 0 && (battle.rounds > 0 || battle.answers > 0)

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

    /**
     * PLAN 206 (decided): a switch made while a card shows takes effect right after that card is answered, so nobody picks the
     * fighter after seeing the question. The card you saw is fought by the Pokémon that is out; the switch's one wild attack stays
     * (it comes with the switch, see [switchCostsAHit]). The first card of a fight follows the same rule.
     *
     * With [cardShowing] and a fight on ([SwitchQueue.fightOn]) and [uid] a team member other than the main: [uid] is queued
     * ([GameState.pendingMain], a newer pick replaces an older one), the result has a [GameEvent.SwitchQueued] and the main stays.
     * Picking the main again cancels a queue ([GameEvent.SwitchQueued] with a null uid). Every other case is today's [setMain] at
     * once: no card in front of the player (a result holds / "Next battle"), no fight, a fainted main, a Pokémon that is not in the
     * team yet (a box Pokémon: the caller adds it with [toTeam] first). An immediate pick replaces the queue. The queue is applied by
     * [onAnswer] (the own study screen and the companion / PiP mode alike).
     */
    fun requestMain(uid: String, cardShowing: Boolean = true): ActionResult {
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        val main = state.main
        if (main != null && main.uid == uid) {
            if (state.pendingMain == null) return setMain(uid) // "already your main"
            state.pendingMain = null
            return ActionResult(true, SwitchQueue.cancelledText(main.name), listOf(GameEvent.SwitchQueued(null, main.name)))
        }
        if (cardShowing && uid in state.team && SwitchQueue.fightOn(state)) {
            state.pendingMain = uid; state.pendingCycle = false
            return ActionResult(true, SwitchQueue.queuedText(mon.name), listOf(GameEvent.SwitchQueued(uid, mon.name)))
        }
        state.pendingMain = null
        return setMain(uid)
    }

    /** Why [cycleMain] can't work right now (shown under the greyed menu item), or null when it can. */
    fun cycleMainBlocker(): String? = when {
        state.team.isEmpty() -> "Pick your first Pokémon first"
        state.team.size < 2 -> "Needs 2+ Pokémon in your team"
        else -> null
    }

    /**
     * Ankimon team cycle: the next team member becomes the main. PLAN 206: with a card showing in a fight ([cardShowing], like
     * [requestMain]) that member is only queued, to come in after the card; the answer then rotates the team as the cycle does
     * ([GameState.pendingCycle]).
     */
    fun cycleMain(cardShowing: Boolean = true): ActionResult {
        cycleMainBlocker()?.let { return ActionResult(false, it) }
        if (cardShowing && SwitchQueue.fightOn(state)) {
            val r = requestMain(state.team[1], cardShowing = true)
            if (state.pendingMain != null) state.pendingCycle = true
            return r
        }
        state.pendingMain = null
        return rotateMain()
    }

    /** Ankimon cycles the first 3 only: [A,B,C,D] -> [B,C,A,D]. */
    private fun rotateMain(): ActionResult {
        val old = state.team.first()
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

    /** R6-06: [inUid] (from the box) takes [outUid]'s place in the team; [outUid] goes to the box. A new main triggers the usual main-change events. */
    fun swapIntoTeam(inUid: String, outUid: String): ActionResult {
        val mon = state.mon(inUid) ?: return ActionResult(false, "Pokémon not found.")
        val out = state.mon(outUid) ?: return ActionResult(false, "Pokémon not found.")
        if (inUid in state.team) return ActionResult(false, "${mon.name} is already in your team.")
        val slot = state.team.indexOf(outUid)
        if (slot < 0) return ActionResult(false, "That Pokémon is not in your team.")
        val old = state.team.first()
        state.team[slot] = inUid
        return changed("${mon.name} joined your team; ${out.name} went to the box.", old)
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
     * Picks which look (sprite) [uid] shows: any species of its evolution line up to its current one that is caught ([Looks.choices]);
     * null or its own species = the current look again. Purely visual. A species outside the line is refused.
     */
    fun setLook(uid: String, species: Int?): ActionResult {
        val mon = state.mon(uid) ?: return ActionResult(false, "Pokémon not found.")
        if (species == null || species == mon.species) {
            mon.look = null
            return ActionResult(true, "${mon.shownName} shows its own look again.")
        }
        val pick = Looks.choices(data, mon, state.caught).firstOrNull { it.num == species }
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
        val oldMon = state.main // released with the main: the switch hit still needs to know whether it had fainted
        mon.heldItem?.let { Economy.give(state, it) }
        mon.heldItem = null
        state.team.remove(uid)
        state.mons.remove(mon)
        if (state.settings.xpShareUid == uid) state.settings.xpShareUid = null
        rememberReleased(mon.ankimonId ?: mon.uid)
        return changed("${mon.name} was released.", old, oldMon)
    }

    /** The Ankimon export removes released Pokémon from the PC save: remember the id (newest 5 000 kept). */
    private fun rememberReleased(id: String) {
        val set = state.releasedAnkimon
        set.remove(id)
        set.add(id)
        while (set.size > RELEASED_KEPT) set.remove(set.first())
    }

    /**
     * R10 B21 "Release duplicates": releases the lower-level copies [Duplicates.releasePlan] picks, in one action (one save), but ONLY
     * those the player [confirmed] (the uids the dialog showed) that are still in a fresh plan (still not the keeper, still unprotected):
     * a Pokémon that arrived or changed while the dialog was open is never released unasked. Each goes the way [release] does it (and is
     * remembered for the Ankimon export). The keeper, the main, the team and every protected copy stay, so no species loses its last
     * copy. Nothing left to release: not ok, nothing changes.
     */
    fun releaseDuplicates(confirmed: Set<String>): ActionResult {
        val plan = Duplicates.releasePlan(data, state)
        val go = plan.release.filter { it in confirmed }
        if (go.isEmpty()) return ActionResult(false, if (plan.isEmpty && confirmed.isEmpty()) "No duplicates to release" else Duplicates.CHANGED_NOTHING)
        val old = state.team.firstOrNull()
        val oldMon = state.main
        for (uid in go) {
            val mon = state.mon(uid) ?: continue
            mon.heldItem?.let { Economy.give(state, it) } // as in release(); a holder is protected, so this only guards a later change of the rule
            mon.heldItem = null
            state.team.remove(uid)
            state.mons.remove(mon)
            if (state.settings.xpShareUid == uid) state.settings.xpShareUid = null
            rememberReleased(mon.ankimonId ?: mon.uid)
        }
        return changed(if (go.size == confirmed.size) "Released ${go.size} Pokémon" else "Released ${go.size} of ${confirmed.size} — your Pokémon changed", old, oldMon)
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
