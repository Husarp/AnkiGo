package com.husarp.ankigo.game

import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// TICKET T05 owns this file. Public signatures are fixed (other tickets call them); add private helpers freely.

object Progression {
    private const val EVERSTONE = "everstone"
    private const val LINKING_CORD = "linking-cord"

    /** Ankimon: ceil(base_experience * level / 7), at least 1. */
    fun xpForDefeat(sp: Species, level: Int): Int =
        max(1, ceil(sp.baseExp * level / Tuning.XP_DIVISOR).toInt())

    /** XP needed from the current level to the next (growth table); 0 at level 100. */
    fun xpToNext(data: GameData, mon: Mon): Int {
        if (mon.level >= Tuning.MAX_LEVEL) return 0
        val table = data.growth[data.sp(mon.species).growthRate]
        val n = mon.level
        return if (table != null) table[n + 1] - table[n] else (n + 1) * (n + 1) * (n + 1) - n * n * n
    }

    /** All moves learnable by level-up at or below [level] (incl. level 0 evolution moves), distinct, in learn order. */
    fun movesUpTo(sp: Species, level: Int): List<String> =
        sp.levelMoves.filter { it.level <= level }.map { it.move }.distinct()

    /**
     * A new Pokémon of [species] at [level]: random IVs 0..31, random nature, gender from maleRatio, random normal
     * ability, up to 4 random moves from movesUpTo (Ankimon random.sample; falls back to Tackle), EVs 0, full HP,
     * uid = random UUID, name = species name.
     */
    fun newMon(data: GameData, species: Int, level: Int, rng: Random, now: Long, shiny: Boolean = false): Mon {
        val sp = data.sp(species)
        val ivs = Stats(rng.nextInt(32), rng.nextInt(32), rng.nextInt(32), rng.nextInt(32), rng.nextInt(32), rng.nextInt(32))
        val nature = Nature.entries[rng.nextInt(Nature.entries.size)]
        val gender = when {
            sp.maleRatio == null -> 'N'
            rng.nextDouble() < sp.maleRatio -> 'M'
            else -> 'F'
        }
        val ability = if (sp.abilities.isEmpty()) "" else sp.abilities[rng.nextInt(sp.abilities.size)]
        // Moves the data doesn't know can't be used in a fight, so they are never handed out.
        val moves = movesUpTo(sp, level).filter { data.move(it) != null }.shuffled(rng).take(4)
            .ifEmpty { listOf(Tuning.FALLBACK_MOVE) }
        val mon = Mon(
            uid = java.util.UUID.randomUUID().toString(), species = species, name = sp.name, level = level, xp = 0,
            ivs = ivs, evs = Stats.ZERO, nature = nature, gender = gender, shiny = shiny, ability = ability,
            moves = moves.toMutableList(), hp = 1, caughtAt = 0,
        )
        mon.hp = StatCalc.maxHp(data, mon)
        return mon
    }

    /**
     * Gives [xp] to [mon] (Ankimon): XP is progress inside the level; at most 10 levels per call; no XP at 100.
     * Each new level: stats recalculated, current HP goes up by the HP the level adds to max HP plus 25 % of the new max
     * (AnkiGo, PLAN 80; see [Healing.levelUpHp]; never above max, a Pokémon at 0 HP stays there), moves learned at exactly
     * that level go through [learnMove]. Emits XpGained, LevelUp (with the HP after the heal), Healed (the main only),
     * MoveLearned/MoveSkipped. Returns levels gained.
     */
    fun gainXp(data: GameData, state: GameState, mon: Mon, xp: Int, events: MutableList<GameEvent>): Int {
        if (mon.level >= Tuning.MAX_LEVEL || xp <= 0) return 0
        val sp0 = data.sp(mon.species)
        val isMain = state.main?.uid == mon.uid // only the main's heals are shown (Healed)
        mon.xp += xp
        val later = mutableListOf<GameEvent>() // level events; XpGained goes first but needs the final numbers
        var gained = 0
        while (mon.level < Tuning.MAX_LEVEL && gained < Tuning.MAX_LEVELS_PER_GAIN) {
            val need = xpToNext(data, mon)
            if (need <= 0 || mon.xp < need) break
            mon.xp -= need
            val oldMax = StatCalc.maxHp(data, mon)
            mon.level++
            gained++
            val maxHp = StatCalc.maxHp(data, mon)
            val hpBefore = mon.hp
            mon.hp = Healing.levelUpHp(mon.hp, oldMax, maxHp)
            later += GameEvent.LevelUp(mon.uid, mon.name, mon.level, mon.hp, maxHp)
            if (isMain && mon.hp > hpBefore) later += GameEvent.Healed(mon.uid, mon.name, mon.hp - hpBefore, mon.hp, maxHp, HealReason.LEVEL_UP)
            Badges.award(state, 5, later)
            for (lm in sp0.levelMoves.filter { it.level == mon.level && it.level > 0 }.map { it.move }.distinct()) {
                learnMove(data, mon, lm, later)
            }
        }
        if (mon.level >= Tuning.MAX_LEVEL) mon.xp = 0
        else {
            val need = xpToNext(data, mon)
            if (need > 0) mon.xp = min(mon.xp, need - 1)
        }
        events += GameEvent.XpGained(mon.uid, mon.name, xp, mon.level, mon.xp, xpToNext(data, mon))
        events += later
        return gained
    }

    /**
     * Automatic move learning (AnkiGo: Ankimon asks with a dialog; no taps during study):
     * fewer than 4 moves -> add it (if not known); else replace the weakest move (score: status 0, power,
     * UNKNOWN_POWER for fixed/level damage, x3 for multi-hit) only if the new one scores higher; else skip.
     */
    fun learnMove(data: GameData, mon: Mon, move: String, events: MutableList<GameEvent>) {
        val m = data.move(move) ?: return
        if (move in mon.moves) return
        if (mon.moves.size < 4) {
            mon.moves += move
            events += GameEvent.MoveLearned(mon.uid, mon.name, m.name, null)
            return
        }
        val scores = mon.moves.map { score(data.move(it)) }
        val weakest = scores.indices.minByOrNull { scores[it] }!! // first index on ties
        if (score(m) > scores[weakest]) {
            val old = data.move(mon.moves[weakest])?.name ?: mon.moves[weakest]
            mon.moves[weakest] = move
            events += GameEvent.MoveLearned(mon.uid, mon.name, m.name, old)
        } else {
            events += GameEvent.MoveSkipped(mon.uid, mon.name, m.name)
        }
    }

    private fun score(m: Move?): Int {
        if (m == null || !m.damaging) return 0
        val base = if (m.levelDamage || m.fixedDamage != null || m.halfHp || m.ohko) Tuning.UNKNOWN_POWER else m.power
        return if (m.multihit != null) base * 3 else base
    }

    /** Adds an EV yield (Macho Brace x2, Power items +8 to their stat), caps 252 per stat / 510 total. */
    fun addEvs(mon: Mon, yield: Stats) {
        val gain = yield.toList().toMutableList()
        if (mon.heldItem == "macho-brace") {
            for (i in gain.indices) gain[i] *= 2
        } else {
            val idx = listOf("power-weight", "power-bracer", "power-belt", "power-lens", "power-band", "power-anklet")
                .indexOf(mon.heldItem)
            if (idx >= 0) gain[idx] += 8
        }
        val cur = mon.evs.toList().toMutableList()
        var total = cur.sum()
        for (i in cur.indices) {
            val add = min(gain[i], min(Tuning.EV_STAT_CAP - cur[i], Tuning.EV_TOTAL_CAP - total))
            if (add > 0) { cur[i] += add; total += add }
        }
        mon.evs = Stats.of(cur)
    }

    /**
     * Everything a Defeat gives (Ankimon): XP to the main = xpForDefeat; classic Exp. Share (settings.xpShareUid,
     * if set, owned and not the main): main and holder each get int(xp * 0.5); Lucky Egg x1.5 for whoever holds it;
     * EV yield and +5..9 friendship (Soothe Bell x1.5) to the main; main.defeats++; then the main's level evolution
     * if one is due (after the level-ups), otherwise its friendship evolution. The same evolution check runs for the
     * Exp. Share holder after it levels.
     */
    fun rewardDefeat(data: GameData, state: GameState, main: Mon, wild: Mon, now: Long, rng: Random, events: MutableList<GameEvent>) {
        val wildSp = data.sp(wild.species)
        val xp = xpForDefeat(wildSp, wild.level)
        val holder = state.settings.xpShareUid?.let { state.mon(it) }?.takeIf { it.uid != main.uid }
        val half = (xp * Tuning.XP_SHARE_PART).toInt()
        var mainXp = if (holder != null) half else xp
        var holderXp = half
        if (main.heldItem == "lucky-egg") mainXp = (mainXp * Tuning.LUCKY_EGG).toInt()
        if (holder?.heldItem == "lucky-egg") holderXp = (holderXp * Tuning.LUCKY_EGG).toInt()

        events += GameEvent.Defeated(wild.species, wild.name, xp)
        addEvs(main, wildSp.evYield)
        var friend = Tuning.FRIENDSHIP_GAIN.random(rng)
        if (main.heldItem == "soothe-bell") friend = (friend * Tuning.SOOTHE_BELL).toInt()
        main.friendship += friend
        main.defeats++

        gainXp(data, state, main, mainXp, events)
        if (holder != null) gainXp(data, state, holder, holderXp, events)

        (levelEvolution(data, state, main, now) ?: friendshipEvolution(data, state, main, now))
            ?.let { evolve(data, state, main, it, rng, events) }
        if (holder != null) levelEvolution(data, state, holder, now)?.let { evolve(data, state, holder, it, rng, events) }
    }

    /**
     * AnkiGo: XP for a wild Pokémon the auto rule CATCHES at 0 HP (Tuning.CATCH_GIVES_XP). Ankimon gives none, but there
     * you choose catch or defeat by hand; with the choice automatic, catching every new species would starve the team
     * of XP for weeks. The full defeat XP (Exp. Share, Lucky Egg apply) and the level-evolution check; no EVs or friendship
     * (defeat-only, as in Ankimon). The win itself (main.defeats, state.defeats, trainer XP) is counted by Game.resolveWild.
     */
    fun rewardCatch(data: GameData, state: GameState, main: Mon, wild: Mon, now: Long, rng: Random, events: MutableList<GameEvent>) {
        val xp = xpForDefeat(data.sp(wild.species), wild.level)
        val holder = state.settings.xpShareUid?.let { state.mon(it) }?.takeIf { it.uid != main.uid }
        val half = (xp * Tuning.XP_SHARE_PART).toInt()
        var mainXp = if (holder != null) half else xp
        var holderXp = half
        if (main.heldItem == "lucky-egg") mainXp = (mainXp * Tuning.LUCKY_EGG).toInt()
        if (holder?.heldItem == "lucky-egg") holderXp = (holderXp * Tuning.LUCKY_EGG).toInt()
        gainXp(data, state, main, mainXp, events)
        if (holder != null) gainXp(data, state, holder, holderXp, events)
        levelEvolution(data, state, main, now)?.let { evolve(data, state, main, it, rng, events) }
        if (holder != null) levelEvolution(data, state, holder, now)?.let { evolve(data, state, holder, it, rng, events) }
    }

    /** Level-based evolution that is due now (LEVEL with time/gender/condition, LEVEL_MOVE), or null. Everstone blocks. */
    fun levelEvolution(data: GameData, state: GameState, mon: Mon, now: Long): Evolution? {
        if (mon.heldItem == EVERSTONE) return null
        return data.sp(mon.species).evolutions.firstOrNull { e ->
            data.species.containsKey(e.to) && when (e.kind) {
                EvoKind.LEVEL -> e.level != null && mon.level >= e.level
                EvoKind.LEVEL_MOVE -> e.move != null && e.move in mon.moves && mon.level >= (e.level ?: 0)
                else -> false
            } && timeOk(e, now) && genderOk(e, mon) && conditionOk(data, state, e, mon)
        }
    }

    /** Friendship evolution that is due now (friendship >= minFriendship, time, Fairy move for Sylveon), or null. Everstone blocks. */
    fun friendshipEvolution(data: GameData, state: GameState, mon: Mon, now: Long): Evolution? {
        if (mon.heldItem == EVERSTONE) return null
        return data.sp(mon.species).evolutions.firstOrNull { e ->
            e.kind == EvoKind.FRIENDSHIP && data.species.containsKey(e.to) &&
                mon.friendship >= (e.minFriendship ?: Tuning.DEFAULT_MIN_FRIENDSHIP) && timeOk(e, now) &&
                (e.moveType == null || mon.moves.any { data.move(it)?.type == e.moveType })
        }
    }

    /**
     * The evolution [item] triggers on [mon] at [now], or null: ITEM with that item; TRADE with that held item; a TRADE
     * without an item, or OTHER, with "linking-cord"; a day/night-bound one (Razor Claw, Razor Fang, Oval Stone) only at
     * that time (Ankimon evolution_time_allows). Everstone blocks.
     */
    fun itemEvolution(data: GameData, mon: Mon, item: String, now: Long = System.currentTimeMillis()): Evolution? =
        itemEvolutions(data, mon, item).firstOrNull { timeOk(it, now) }

    /** "day" or "night" when the time of day is the ONLY thing keeping [item] from evolving [mon] now (Ankimon item_evolution_time_requirement), else null. */
    fun itemEvolutionTimeNeeded(data: GameData, mon: Mon, item: String, now: Long = System.currentTimeMillis()): String? =
        if (itemEvolution(data, mon, item, now) != null) null else itemEvolutionAnyTime(data, mon, item)?.time

    /** The evolution [item] triggers on [mon] whatever the time of day. */
    fun itemEvolutionAnyTime(data: GameData, mon: Mon, item: String): Evolution? = itemEvolutions(data, mon, item).firstOrNull()

    private fun itemEvolutions(data: GameData, mon: Mon, item: String): List<Evolution> {
        if (mon.heldItem == EVERSTONE) return emptyList()
        return data.sp(mon.species).evolutions.filter { e ->
            data.species.containsKey(e.to) && genderOk(e, mon) && when (e.kind) {
                EvoKind.ITEM -> e.item == item
                EvoKind.TRADE -> if (e.item != null) e.item == item else item == LINKING_CORD
                EvoKind.OTHER -> item == LINKING_CORD
                else -> false
            }
        }
    }

    /**
     * Evolves (Ankimon): species and name change, stats recalculated, HP refilled, ability re-rolled from the new
     * species, then the new species' level-0 (evolution) moves go through learnMove. Marks the species seen and caught,
     * state.evolutions++, emits Evolved (+ badge 16 "Evolved a Pokémon" via Badges).
     */
    fun evolve(data: GameData, state: GameState, mon: Mon, evo: Evolution, rng: Random, events: MutableList<GameEvent>) {
        val sp = data.sp(evo.to)
        val from = mon.name
        // Ankimon evolution_window.py: a nickname equal to the old species name follows the species.
        if (mon.nickname?.equals(from, ignoreCase = true) == true) mon.nickname = null
        mon.species = sp.num
        mon.name = sp.name
        mon.hp = StatCalc.maxHp(data, mon)
        if (sp.abilities.isNotEmpty()) mon.ability = sp.abilities[rng.nextInt(sp.abilities.size)]
        val wasSeen = sp.num in state.seen; val wasCaught = sp.num in state.caught
        state.seen += sp.num
        state.caught += sp.num
        // R9-W4-B: the starter's new form is not a find (the evolution itself counts); any other Pokémon evolving into it is.
        if (mon.uid == state.starterUid) {
            if (!wasSeen) state.starterSeen += sp.num
            if (!wasCaught) state.starterCaught += sp.num
        } else { state.starterSeen -= sp.num; state.starterCaught -= sp.num }
        Wishlist.onCaught(state, sp.num)
        state.evolutions++
        events += GameEvent.Evolved(mon.uid, from, sp.name, sp.num)
        for (m in sp.levelMoves.filter { it.level == 0 }.map { it.move }.distinct()) learnMove(data, mon, m, events)
        Badges.award(state, 16, events)
    }

    /** Day = 6:00-18:00 on the phone clock. */
    fun isDay(now: Long): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return hour >= Tuning.DAY_FROM_HOUR && hour < Tuning.NIGHT_FROM_HOUR
    }

    private fun timeOk(e: Evolution, now: Long) = when (e.time) {
        "day" -> isDay(now)
        "night" -> !isDay(now)
        else -> true
    }

    private fun genderOk(e: Evolution, mon: Mon) = e.gender == null || e.gender == mon.gender

    /** Stat comparisons and the Dark-type party rule; any other condition text (rain, upside-down...) is not modelled = true. */
    private fun conditionOk(data: GameData, state: GameState, e: Evolution, mon: Mon): Boolean {
        val c = e.condition ?: return true
        return when (c) {
            "with an Atk stat > its Def stat" -> StatCalc.stats(data, mon).let { it.atk > it.def }
            "with an Atk stat < its Def stat" -> StatCalc.stats(data, mon).let { it.atk < it.def }
            "with an Atk stat equal to its Def stat" -> StatCalc.stats(data, mon).let { it.atk == it.def }
            "with a Dark-type in the party" ->
                state.teamMons().any { it.uid != mon.uid && Type.DARK in data.sp(it.species).types }
            else -> true
        }
    }
}

object Catching {
    /** Ankimon's protected kinds (each a setting): legendary, mythical, ultra beast, starter, regional form (by the base number). */
    fun isProtected(settings: Settings, sp: Species, tier: Tier): Boolean =
        ((sp.isLegendary || tier == Tier.LEGENDARY || sp.baseNum in Encounters.LEGENDARY_EXTRA) && settings.protectLegendary) ||
            ((sp.isMythical || tier == Tier.MYTHICAL) && settings.protectMythical) ||
            ((tier == Tier.ULTRA || sp.baseNum in Encounters.ULTRA_BEASTS) && settings.protectUltra) ||
            (sp.baseNum in Encounters.STARTERS && settings.protectStarter) ||
            (sp.form != null && settings.protectRegional)

    /**
     * What happens when the wild one faints (Ankimon handle_enemy_faint, never a dialog): the one-off battle.override
     * first (CATCH -> CATCH; DEFEAT -> DEFEAT, except a protected kind is caught anyway); then the wishlist -> CATCH;
     * then the automatic mode: mode 1 CATCH; mode 2 CATCH if shiny or protected else DEFEAT;
     * mode 3 CATCH if the species was never caught, or shiny, or protected, else DEFEAT.
     * With Settings.autoReleaseDuplicates on, an owned species goes first by [Duplicates] (see [plan]).
     */
    fun decide(data: GameData, state: GameState, battle: Battle): Outcome = plan(data, state, battle).outcome

    /** [decide] plus the duplicate verdict when [Duplicates] acted on this fight (a swap releases a copy after the catch). */
    fun plan(data: GameData, state: GameState, battle: Battle): CatchPlan {
        val settings = state.settings
        val wild = battle.wild
        val protected = isProtected(settings, data.sp(wild.species), battle.tier)
        when (battle.override) {
            Outcome.CATCH -> return CatchPlan(Outcome.CATCH)
            Outcome.DEFEAT -> return CatchPlan(if (protected) Outcome.CATCH else Outcome.DEFEAT)
            null -> {}
        }
        val special = wild.shiny || protected
        // Auto-release duplicates: higher than the best copy = catch (and swap); not higher = defeat, unless it is special.
        val dup = Duplicates.verdict(data, state, wild)
        if (dup != null && dup.higher) return CatchPlan(Outcome.CATCH, dup)
        if (dup != null && !special && wild.species !in settings.wishlist) return CatchPlan(Outcome.DEFEAT, dup)
        if (wild.species in settings.wishlist) return CatchPlan(Outcome.CATCH)
        return CatchPlan(when (settings.autoMode) {
            1 -> Outcome.CATCH
            2 -> if (special) Outcome.CATCH else Outcome.DEFEAT
            else -> if (special || wild.species !in state.caught) Outcome.CATCH else Outcome.DEFEAT
        })
    }

    /** Ball chance in percent (Tuning.BALL_CHANCE, +10 Net Ball vs Water/Bug, +10 Dive Ball vs Water), max 100. */
    fun ballChance(ball: String, wildTypes: List<Type>): Int {
        var chance = Tuning.BALL_CHANCE[ball] ?: 0
        if (ball == "net-ball" && (Type.WATER in wildTypes || Type.BUG in wildTypes)) chance += Tuning.BALL_TYPE_BONUS
        if (ball == "dive-ball" && Type.WATER in wildTypes) chance += Tuning.BALL_TYPE_BONUS
        return min(100, chance)
    }

    /**
     * Adds the wild Pokémon to the collection (Ankimon catch: keeps level, IVs, nature, ability, gender, shiny, moves;
     * EVs 0, friendship 0, XP 0, no held item, full HP, new uid, caughtAt = now). Joins the team if it has fewer than
     * 6, else the box. Marks seen + caught, state.catches++, catch badges (first catch, first of each tier).
     * Emits Caught. No XP (Ankimon gives none for catching).
     */
    fun addToCollection(data: GameData, state: GameState, wild: Mon, tier: Tier, ball: String?, now: Long, events: MutableList<GameEvent>): Mon {
        val mon = Mon(
            uid = java.util.UUID.randomUUID().toString(), species = wild.species, name = wild.name, level = wild.level, xp = 0,
            ivs = wild.ivs, evs = Stats.ZERO, nature = wild.nature, gender = wild.gender, shiny = wild.shiny,
            ability = wild.ability, moves = wild.moves.toMutableList(), hp = 1, friendship = 0, heldItem = null,
            caughtAt = now, defeats = 0, tier = tier,
        )
        mon.hp = StatCalc.maxHp(data, mon)
        val newSpecies = wild.species !in state.caught
        val toTeam = false // R11-B (PLAN 134): a catch always goes to the Box; the player adds it to the team by hand
        state.mons += mon
        state.markSeen(mon.species, now)
        state.caught += mon.species
        state.starterCaught -= mon.species; state.starterSeen -= mon.species // R9-W4-B: a real catch of the starter's species
        Wishlist.onCaught(state, mon.species) // caught now: it leaves the wishlist (this fight's plan was made already)
        state.catches++
        events += GameEvent.Caught(mon.uid, mon.species, mon.name, mon.level, mon.shiny, ball, newSpecies, toTeam)
        Badges.award(state, 7, events, now)
        val tierBadge = when (tier) {
            Tier.NORMAL -> 17; Tier.BABY -> 18; Tier.ULTRA -> 8; Tier.LEGENDARY -> 9; Tier.MYTHICAL -> 10
            else -> null
        }
        if (tierBadge != null) Badges.award(state, tierBadge, events, now)
        return mon
    }
}
