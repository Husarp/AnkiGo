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
        val picked = movesUpTo(sp, level).filter { data.move(it) != null }.shuffled(rng).take(4)
            .ifEmpty { listOf(Tuning.FALLBACK_MOVE) }
        val moves = LinkedMoves.generated(data, sp, picked)
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
        val before = mon.xp
        val crossed = mutableListOf<Int>() // PLAN 138: the XP each level grown needed
        mon.xp += xp
        val later = mutableListOf<GameEvent>() // level events; XpGained goes first but needs the final numbers
        var gained = 0
        while (mon.level < Tuning.MAX_LEVEL && gained < Tuning.MAX_LEVELS_PER_GAIN) {
            val need = xpToNext(data, mon)
            if (need <= 0 || mon.xp < need) break
            crossed += need
            mon.xp -= need
            val oldMax = StatCalc.maxHp(data, mon)
            mon.level++
            gained++
            val maxHp = StatCalc.maxHp(data, mon)
            val hpBefore = mon.hp
            mon.hp = Healing.levelUpHp(mon.hp, oldMax, maxHp)
            later += GameEvent.LevelUp(mon.uid, mon.name, mon.level, mon.hp, maxHp)
            if (isMain && mon.hp > hpBefore) later += GameEvent.Healed(mon.uid, mon.name, mon.hp - hpBefore, mon.hp, maxHp, HealReason.LEVEL_UP)
            Badges.award(state, 5, later, pokemon = mon, data = data)
            AchievementPokemon.record(state, "total-levels", mon, data)
            if (mon.level >= (state.mons.maxOfOrNull { it.level } ?: 0)) AchievementPokemon.record(state, "highest-level", mon, data)
            for (lm in sp0.levelMoves.filter { it.level == mon.level && it.level > 0 }.map { it.move }.distinct()) {
                learnMove(data, mon, lm, later)
            }
        }
        if (mon.level >= Tuning.MAX_LEVEL) mon.xp = 0
        else {
            val need = xpToNext(data, mon)
            if (need > 0) mon.xp = min(mon.xp, need - 1)
        }
        AchievementPokemon.record(state, "total-xp", mon, data)
        events += GameEvent.XpGained(mon.uid, mon.name, xp, mon.level, mon.xp, xpToNext(data, mon), before, crossed)
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

    /** Adds an EV yield of one win (Macho Brace x2, Power items +8 to their stat), caps 252 per stat / 510 total. */
    fun addEvs(mon: Mon, yield: Stats, heldItem: String? = mon.heldItem) {
        val gain = yield.toList().toMutableList()
        if (heldItem == "macho-brace") {
            for (i in gain.indices) gain[i] *= 2
        } else {
            val idx = listOf("power-weight", "power-bracer", "power-belt", "power-lens", "power-band", "power-anklet")
                .indexOf(heldItem)
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
     * R17C-2 (PLAN 240): what every WIN gives the Main besides XP: the wild species' EV yield ([addEvs]: Macho Brace x2, Power items +8)
     * and +5..9 friendship (Soothe Bell x1.5), capped at [Tuning.FRIENDSHIP_MAX] (a higher number from an older save is cut here).
     * A defeat, an auto catch and a ball catch all come through here, so they all give the same. The Exp. Share holder gets neither.
     */
    private fun winBonus(data: GameData, state: GameState, main: Mon, wild: Mon, rng: Random) {
        addEvs(main, data.sp(wild.species).evYield, state.activeHeldItem(main))
        var friend = Tuning.FRIENDSHIP_GAIN.random(rng)
        if (state.activeHeldItem(main) == "soothe-bell") friend = (friend * Tuning.SOOTHE_BELL).toInt()
        main.friendship = min(Tuning.FRIENDSHIP_MAX, main.friendship + friend)
    }

    /**
     * Everything a Defeat gives (Ankimon): XP to the main = xpForDefeat; classic Exp. Share (settings.xpShareUid,
     * if set, owned and not the main): main and holder each get int(xp * 0.5); Lucky Egg x1.5 for whoever holds it;
     * EV yield and +5..9 friendship (Soothe Bell x1.5, up to 255) to the main ([winBonus], the same as a catch win gives);
     * main.defeats++. Evolution is always an explicit choice in the details (including for the Exp. Share holder).
     */
    fun rewardDefeat(data: GameData, state: GameState, main: Mon, wild: Mon, now: Long, rng: Random, events: MutableList<GameEvent>) {
        val wildSp = data.sp(wild.species)
        val xp = xpForDefeat(wildSp, wild.level)
        val holder = state.settings.xpShareUid?.let { state.mon(it) }?.takeIf { it.uid != main.uid }
        val half = (xp * Tuning.XP_SHARE_PART).toInt()
        var mainXp = if (holder != null) half else xp
        var holderXp = half
        if (state.activeHeldItem(main) == "lucky-egg") mainXp = (mainXp * Tuning.LUCKY_EGG).toInt()
        if (holder != null && state.activeHeldItem(holder) == "lucky-egg") holderXp = (holderXp * Tuning.LUCKY_EGG).toInt()

        events += GameEvent.Defeated(wild.species, wild.name, Tuning.studyXp(xp, state.practiceRewards))
        winBonus(data, state, main, wild, rng)
        main.defeats++

        gainXp(data, state, main, Tuning.studyXp(mainXp, state.practiceRewards), events)
        if (holder != null) gainXp(data, state, holder, Tuning.studyXp(holderXp, state.practiceRewards), events)

    }

    /**
     * AnkiGo: what a wild Pokémon the auto rule or a ball CATCHES at 0 HP gives. Ankimon gives nothing for a catch, but there you
     * choose catch or defeat by hand; with the choice automatic, most wins are catches (R17C-2: they gave no EVs, so the Main's EVs
     * stayed 0). So a catch win gives exactly what a defeat gives: the full defeat XP (Exp. Share, Lucky Egg apply), the EVs and the
     * friendship ([winBonus]). Evolution waits for the player's choice. Counting the win itself (main.defeats,
     * state.defeats, trainer XP) stays with Game (resolveWild / throwBall).
     */
    fun rewardCatch(data: GameData, state: GameState, main: Mon, wild: Mon, now: Long, rng: Random, events: MutableList<GameEvent>) {
        val xp = xpForDefeat(data.sp(wild.species), wild.level)
        val holder = state.settings.xpShareUid?.let { state.mon(it) }?.takeIf { it.uid != main.uid }
        val half = (xp * Tuning.XP_SHARE_PART).toInt()
        var mainXp = if (holder != null) half else xp
        var holderXp = half
        if (state.activeHeldItem(main) == "lucky-egg") mainXp = (mainXp * Tuning.LUCKY_EGG).toInt()
        if (holder != null && state.activeHeldItem(holder) == "lucky-egg") holderXp = (holderXp * Tuning.LUCKY_EGG).toInt()
        winBonus(data, state, main, wild, rng)
        gainXp(data, state, main, Tuning.studyXp(mainXp, state.practiceRewards), events)
        if (holder != null) gainXp(data, state, holder, Tuning.studyXp(holderXp, state.practiceRewards), events)
    }

    /** Every currently available branch, without choosing one for the player. */
    fun readyEvolutions(data: GameData, state: GameState, mon: Mon, now: Long): List<Evolution> {
        if (state.activeHeldItem(mon) == EVERSTONE) return emptyList()
        return data.sp(mon.species).evolutions.filter { e ->
            data.species.containsKey(e.to) && timeOk(e, now) && genderOk(e, mon) && when (e.kind) {
                EvoKind.LEVEL -> e.level != null && mon.level >= e.level && conditionOk(data, state, e, mon)
                EvoKind.LEVEL_MOVE -> e.move != null && e.move in mon.moves && mon.level >= (e.level ?: 0)
                EvoKind.FRIENDSHIP -> mon.friendship >= (e.minFriendship ?: Tuning.DEFAULT_MIN_FRIENDSHIP) &&
                    (e.moveType == null || mon.moves.any { data.move(it)?.type == e.moveType })
                else -> state.count(evolutionItem(e)!!) > 0 && e in itemEvolutions(data, mon, evolutionItem(e)!!, state.activeHeldItem(mon))
            }
        }.distinctBy { it.to }
    }

    fun evolutionItem(e: Evolution): String? = when (e.kind) {
        EvoKind.ITEM, EvoKind.TRADE -> e.item ?: LINKING_CORD
        EvoKind.OTHER -> LINKING_CORD
        else -> null
    }

    /** Level-based evolution that is due now (LEVEL with time/gender/condition, LEVEL_MOVE), or null. Everstone blocks. */
    fun levelEvolution(data: GameData, state: GameState, mon: Mon, now: Long): Evolution? {
        if (state.activeHeldItem(mon) == EVERSTONE) return null
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
        if (state.activeHeldItem(mon) == EVERSTONE) return null
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
    fun itemEvolution(data: GameData, mon: Mon, item: String, now: Long = System.currentTimeMillis(), heldItem: String? = mon.heldItem): Evolution? =
        itemEvolutions(data, mon, item, heldItem).firstOrNull { timeOk(it, now) }

    /** "day" or "night" when the time of day is the ONLY thing keeping [item] from evolving [mon] now (Ankimon item_evolution_time_requirement), else null. */
    fun itemEvolutionTimeNeeded(data: GameData, mon: Mon, item: String, now: Long = System.currentTimeMillis(), heldItem: String? = mon.heldItem): String? =
        if (itemEvolution(data, mon, item, now, heldItem) != null) null else itemEvolutionAnyTime(data, mon, item, heldItem)?.time

    /** The evolution [item] triggers on [mon] whatever the time of day. */
    fun itemEvolutionAnyTime(data: GameData, mon: Mon, item: String, heldItem: String? = mon.heldItem): Evolution? = itemEvolutions(data, mon, item, heldItem).firstOrNull()

    private fun itemEvolutions(data: GameData, mon: Mon, item: String, heldItem: String?): List<Evolution> {
        if (heldItem == EVERSTONE) return emptyList()
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
        if (state.battle != null) Achievements.bump(state, Metric.GLOW_UP)
        events += GameEvent.Evolved(mon.uid, from, sp.name, sp.num)
        for (m in sp.levelMoves.filter { it.level == 0 }.map { it.move }.distinct()) learnMove(data, mon, m, events)
        Badges.award(state, 16, events, pokemon = mon, data = data)
        AchievementPokemon.record(state, "evolutions", mon, data)
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
     * With Settings.autoReleaseDuplicates on, an owned species goes first by [Duplicates] (see [plan]). With "Ask me" ([AskMe]) a better
     * copy of an owned species is a DEFEAT here whose [CatchPlan.ask] says the question is to be asked.
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
        val asking = settings.askMe && settings.autoMode == 3 // R19-P (PLAN 281): "Ask me" is mode 3 plus a question; it never runs together with the swap
        // Auto-release duplicates: higher than the best copy = catch (and swap); not higher = defeat, unless it is special.
        val dup = if (asking) null else Duplicates.verdict(data, state, wild)
        if (dup != null && dup.higher) return CatchPlan(Outcome.CATCH, dup)
        if (dup != null && !special && wild.species !in settings.wishlist) return CatchPlan(Outcome.DEFEAT, dup)
        if (wild.species in settings.wishlist) return CatchPlan(Outcome.CATCH)
        // R19-P: a better copy of a species you own is defeated for the rewards and waits for the answer; a shiny / protected one is caught as always
        if (asking && !special) AskMe.better(state, wild)?.let { return CatchPlan(Outcome.DEFEAT, ask = it) }
        return CatchPlan(when (settings.autoMode) {
            1 -> Outcome.CATCH
            2 -> if (special) Outcome.CATCH else Outcome.DEFEAT
            else -> if (special || wild.species !in state.caught) Outcome.CATCH else Outcome.DEFEAT
        })
    }

    /** Regular ball chance; a special cannot be thrown alone. */
    fun ballChance(ball: String): Int =
        Tuning.BALL_CHANCE[ball] ?: 0

    fun ballChance(ball: String, special: String?, data: GameData, state: GameState, battle: Battle, now: Long): Int {
        val base = ballChance(ball)
        if (base == 0 || ball == "master-ball") return base
        val wild = battle.wild
        val types = data.sp(wild.species).types
        val bonus = when (special) {
            "net-ball" -> if (Type.WATER in types || Type.BUG in types) Tuning.SPECIAL_BONUS else 0
            "dive-ball" -> if (Type.WATER in types) Tuning.SPECIAL_BONUS else 0
            "dusk-ball" -> if (!Progression.isDay(now)) Tuning.SPECIAL_BONUS else 0
            "quick-ball" -> if (battle.rounds == 0) Tuning.SPECIAL_BONUS else 0
            "timer-ball" -> (battle.rounds * Tuning.TIMER_BONUS_PER_ROUND).coerceAtMost(Tuning.SPECIAL_BONUS)
            "nest-ball" -> if (wild.level <= Tuning.NEST_MAX_LEVEL) Tuning.SPECIAL_BONUS else 0
            "repeat-ball" -> 0
            "premier-ball" -> if (wild.shiny || battle.tier in listOf(Tier.LEGENDARY, Tier.MYTHICAL, Tier.ULTRA)) Tuning.SPECIAL_BONUS else 0
            else -> 0
        }
        return (base + bonus).coerceAtMost(Tuning.BALL_CHANCE_CAP)
    }

    fun specialIvs(ivs: Stats, special: String?): Stats {
        if (special != "heal-ball" && special != "luxury-ball") return ivs
        fun add(v: Int, hp: Boolean = false) = (v + when (special) {
            "heal-ball" -> if (hp) Tuning.HEAL_IV_BONUS else 0
            "luxury-ball" -> Tuning.LUXURY_IV_BONUS
            else -> 0
        }).coerceAtMost(Tuning.BALL_IV_CAP)
        return Stats(add(ivs.hp, true), add(ivs.atk), add(ivs.def), add(ivs.spa), add(ivs.spd), add(ivs.spe))
    }

    fun throwName(ball: String, special: String?): String = if (special == null) Items.name(ball)
        else Items.name(ball).removeSuffix(" Ball") + " " + Items.name(special)

    /**
     * Adds the wild Pokémon to the collection (Ankimon catch: keeps level, IVs, nature, ability, gender, shiny, moves;
     * keeps EVs, friendship 0, XP 0, no held item, full HP, new uid, caughtAt = now). Joins the team if it has fewer than
     * 6, else the box. Marks seen + caught, state.catches++, catch badges (first catch, first of each tier).
     * Emits Caught. No XP (Ankimon gives none for catching).
     */
    fun addToCollection(data: GameData, state: GameState, wild: Mon, tier: Tier, ball: String?, now: Long, events: MutableList<GameEvent>, special: String? = null): Mon {
        val mon = Mon(
            uid = java.util.UUID.randomUUID().toString(), species = wild.species, name = wild.name, level = wild.level, xp = 0,
            ivs = specialIvs(wild.ivs, special), evs = wild.evs, nature = wild.nature, gender = wild.gender, shiny = wild.shiny,
            ability = wild.ability, moves = wild.moves.toMutableList(), hp = 1, friendship = 0, heldItem = null,
            caughtAt = now, defeats = 0, tier = tier,
        )
        mon.hp = StatCalc.maxHp(data, mon)
        val previousTypes = Achievements.value(Metric.TYPES_CAUGHT, state, data)
        val previousRare = Achievements.value(Metric.LEGENDARY_MYTHICAL, state, data)
        val newSpecies = wild.species !in state.caught
        val toTeam = false // R11-B (PLAN 134): a catch always goes to the Box; the player adds it to the team by hand
        state.mons += mon
        state.markSeen(mon.species, now)
        state.caught += mon.species
        state.starterCaught -= mon.species; state.starterSeen -= mon.species // R9-W4-B: a real catch of the starter's species
        Wishlist.onCaught(state, mon.species) // caught now: it leaves the wishlist (this fight's plan was made already)
        state.catches++
        events += GameEvent.Caught(mon.uid, mon.species, mon.name, mon.level, mon.shiny, ball, newSpecies, toTeam)
        Badges.award(state, 7, events, now, mon, data)
        AchievementPokemon.record(state, "catches", mon, data)
        if (Achievements.value(Metric.TYPES_CAUGHT, state, data) > previousTypes) AchievementPokemon.record(state, "types", mon, data)
        if (mon.level >= (state.mons.maxOfOrNull { it.level } ?: 0)) AchievementPokemon.record(state, "highest-level", mon, data)
        if (mon.shiny) AchievementPokemon.record(state, "shinies", mon, data)
        if (Achievements.value(Metric.LEGENDARY_MYTHICAL, state, data) > previousRare) AchievementPokemon.record(state, "legendary-mythical", mon, data)
        if (ball != null) AchievementPokemon.record(state, "ball-catches", mon, data)
        val tierBadge = when (tier) {
            Tier.NORMAL -> 17; Tier.BABY -> 18; Tier.ULTRA -> 8; Tier.LEGENDARY -> 9; Tier.MYTHICAL -> 10
            else -> null
        }
        if (tierBadge != null) Badges.award(state, tierBadge, events, now, mon, data)
        return mon
    }
}
