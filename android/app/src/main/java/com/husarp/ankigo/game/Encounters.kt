package com.husarp.ankigo.game

import kotlin.random.Random

// TICKET T06 owns this file. Public signatures are fixed (other tickets call them); add private helpers freely.

/** Ankimon's legacy encounter system (the one players get; the "overhaul" is switched off in Ankimon). */
object Encounters {
    data class Roll(val species: Int, val tier: Tier, val shiny: Boolean)

    /** Starter lines of gens 1-9 (Ankimon STARTERS, 81 numbers): never wild while the starter tier is 0%. */
    val STARTERS: Set<Int> = listOf(1..9, 152..160, 252..260, 387..395, 495..503, 650..658, 722..730, 810..818, 906..914)
        .flatMap { it.toList() }.toSet()
    /** The three base starters per generation, for the first-run picker: gen -> [grass, fire, water]. */
    val STARTER_CHOICES: Map<Int, List<Int>> = mapOf(
        1 to listOf(1, 4, 7), 2 to listOf(152, 155, 158), 3 to listOf(252, 255, 258), 4 to listOf(387, 390, 393),
        5 to listOf(495, 498, 501), 6 to listOf(650, 653, 656), 7 to listOf(722, 725, 728), 8 to listOf(810, 813, 816),
        9 to listOf(906, 909, 912),
    )
    /** Ultra Beasts (793-799, 803-806). */
    val ULTRA_BEASTS: Set<Int> = ((793..799) + (803..806)).toSet()
    /** Fossil Pokémon: never wild, only from fossil items (Ankimon UNAVAILABLE < 10000). */
    val FOSSILS: Set<Int> = listOf(138..142, 345..348, 408..411, 564..567, 696..699, 880..883)
        .flatMap { it.toList() }.toSet()
    /**
     * Ankimon's LEGENDARY list (encounter_data.py) differs from PokeAPI's flags: Type: Null (772), Walking Wake (1009),
     * Iron Leaves (1010), Gouging Fire (1020), Raging Bolt (1021), Iron Boulder (1022), Iron Crown (1023) are Legendary
     * there, whatever the data's is_legendary flag says.
     */
    val LEGENDARY_EXTRA: Set<Int> = setOf(772, 1009, 1010, 1020, 1021, 1022, 1023)
    /**
     * Never wild: Ankimon only lists the Zygarde-50% / 10% forms (ids 10119 / 10181) as Legendary, which AnkiGo doesn't
     * have, so the base Zygarde (718) never spawns.
     */
    val NEVER_WILD: Set<Int> = setOf(718)
    /** Capture requirements for base forms (Ankimon PREREQUISITES): species -> all of these caught. 1024 is in [ANY_PREREQUISITES]. */
    val PREREQUISITES: Map<Int, Set<Int>> = mapOf(
        150 to setOf(151), 249 to setOf(144, 145, 146), 250 to setOf(243, 244, 245), 384 to setOf(382, 383),
        483 to setOf(480, 481, 482), 484 to setOf(480, 481, 482), 487 to setOf(483, 484), 493 to setOf(487),
        490 to setOf(489), 486 to setOf(377, 378, 379, 894, 895), 647 to setOf(638, 639, 640), 645 to setOf(641, 642),
        646 to setOf(643, 644), 718 to setOf(716, 717), 800 to setOf(791, 792), 773 to setOf(772), 890 to setOf(888, 889),
        896 to setOf(898), 897 to setOf(898), 905 to setOf(645), 1025 to setOf(1014, 1015, 1016),
    )
    /** Capture requirements where ANY one of the species is enough (Terapagos-style special case: 1024). */
    val ANY_PREREQUISITES: Map<Int, Set<Int>> = mapOf(1024 to setOf(1007, 1008))

    /**
     * Regional forms by region, as PokeAPI pokemon ids (Ankimon REGIONAL_FORMS: totems and Zen Darmanitan left out).
     * They are never in a tier pool: a wild base species turns into one of its forms (see [roll]).
     */
    val REGIONAL_FORMS: Map<String, List<Int>> = mapOf(
        "alola" to listOf(10091, 10092) + (10100..10115),
        "galar" to (10161..10177).toList() + listOf(10179, 10180),
        "hisui" to (10229..10244).toList(),
        "paldea" to (10250..10253).toList(),
    )
    /** The generation each region came with: a form needs that generation switched on too. */
    val REGION_GEN: Map<String, Int> = mapOf("alola" to 7, "galar" to 8, "hisui" to 8, "paldea" to 9)
    /** Every form id, in a fixed order. */
    val FORM_IDS: List<Int> = REGIONAL_FORMS.values.flatten()

    private val RARE_BOOSTS = listOf(
        Tier.LEGENDARY to 0.5, Tier.ULTRA to 1.5, Tier.MEGA to 0.5, Tier.GMAX to 0.5, Tier.STARTER to 0.5, Tier.MYTHICAL to 0.2,
    )

    /** Ankimon _modify_percentages_legacy, normalised to 100 (includes MEGA/GMAX/STARTER keys). */
    fun tierPercentages(reviewsToday: Int, dailyAverage: Int, trainerLevel: Int, mainLevel: Int): Map<Tier, Double> {
        val p = HashMap<Tier, Double>(Tuning.BASE_TIERS)
        val ratio = if (dailyAverage > 0) reviewsToday.toDouble() / dailyAverage else 0.0
        val present = HashSet(p.keys)
        when {
            ratio < 0.4 -> {
                // Everything rare folds into Normal.
                for (t in Tier.entries) if (t != Tier.NORMAL) {
                    p[Tier.NORMAL] = p.getValue(Tier.NORMAL) + p.getValue(t)
                    p.remove(t); present.remove(t)
                }
            }
            ratio < 0.6 -> { add(p, Tier.BABY, 1.5); add(p, Tier.NORMAL, -1.5) }
            ratio < 0.8 -> { add(p, Tier.ULTRA, 1.5); add(p, Tier.NORMAL, -1.5) }
            else -> {
                for ((t, b) in RARE_BOOSTS) add(p, t, b)
                add(p, Tier.NORMAL, -3.7)
            }
        }
        if (mainLevel != 0) {
            if (trainerLevel > Tuning.TRAINER_BONUS_FROM_LEVEL) {
                var added = 0.0
                for ((t, b) in RARE_BOOSTS) if (t in present) { add(p, t, b); added += b }
                add(p, Tier.NORMAL, -added)
            }
            for ((t, gate) in Tuning.TIER_GATE) if (t in p && mainLevel < gate) p[t] = 0.0
        }
        if (!Tuning.STARTER_TIER_ENABLED) p[Tier.STARTER] = 0.0
        val total = p.values.sum()
        return Tier.entries.associateWith { if (total > 0) (p[it] ?: 0.0) / total * 100 else 0.0 }
    }

    private fun add(p: MutableMap<Tier, Double>, t: Tier, d: Double) { p[t] = (p[t] ?: 0.0) + d }

    /** The tier a species belongs to for pools (NORMAL/BABY/ULTRA/LEGENDARY/MYTHICAL/STARTER), or null if never wild (fossils, Zygarde). */
    fun tierOf(sp: Species): Tier? = when {
        sp.baseNum in STARTERS -> Tier.STARTER
        sp.baseNum in FOSSILS || sp.baseNum in NEVER_WILD -> null
        sp.baseNum in ULTRA_BEASTS -> Tier.ULTRA
        sp.baseNum in LEGENDARY_EXTRA -> Tier.LEGENDARY
        sp.isMythical -> Tier.MYTHICAL
        sp.isLegendary -> Tier.LEGENDARY
        sp.isBaby -> Tier.BABY
        else -> Tier.NORMAL
    }

    /** Ankimon check_min_generate_level (+ AnkiGo fix: forms whose prevo is a baby count as basic). */
    fun minLevel(data: GameData, sp: Species): Int {
        val evoLevel = sp.evoLevel
        var min = when {
            evoLevel != null && evoLevel > 0 -> evoLevel
            sp.evoType != null -> if (sp.prevo?.let { data.species[it] }?.isBaby == true) 1 else Tuning.NON_LEVEL_EVO_MIN_LEVEL
            else -> 1
        }
        val floor = tierOf(sp)?.let { Tuning.TIER_MIN_LEVEL[it] }
        if (floor != null) min = maxOf(min, floor)
        return min
    }

    /** main level -3..+3, at least 1, at most 100 (100 when main is 100). */
    fun wildLevel(mainLevel: Int, rng: Random): Int {
        if (mainLevel >= Tuning.MAX_LEVEL) return Tuning.MAX_LEVEL
        val lo = maxOf(1, mainLevel - Tuning.WILD_LEVEL_SPREAD)
        val hi = minOf(Tuning.MAX_LEVEL, maxOf(1, mainLevel + Tuning.WILD_LEVEL_SPREAD))
        return rng.nextInt(lo, hi + 1)
    }

    private fun prerequisitesMet(num: Int, caught: Set<Int>): Boolean =
        (PREREQUISITES[num]?.all { it in caught } ?: true) && (ANY_PREREQUISITES[num]?.any { it in caught } ?: true)

    /** Species allowed in [tier] at [wildLevel]: enabled generation, min level, capture requirements. */
    fun pool(data: GameData, tier: Tier, settings: Settings, wildLevel: Int, caught: Set<Int>): List<Int> {
        if (tier == Tier.MEGA || tier == Tier.GMAX) return emptyList()
        return data.species.values.asSequence()
            .filter { it.form == null && tierOf(it) == tier && it.gen in settings.gens && minLevel(data, it) <= wildLevel && prerequisitesMet(it.num, caught) }
            .map { it.num }.sorted().toList()
    }

    /**
     * Forms that may replace the picked base species [num] now (Ankimon _variant_eligible): the base species' generation
     * and the form's region generation both on, the form's own minimum level reached, the capture requirements met.
     */
    fun eligibleForms(data: GameData, settings: Settings, num: Int, wildLevel: Int, caught: Set<Int>): List<Int> {
        if (data.species[num]?.gen?.let { it in settings.gens } != true) return emptyList()
        return FORM_IDS.filter { id ->
            val f = data.species[id]
            f != null && f.baseNum == num && f.gen in settings.gens && minLevel(data, f) <= wildLevel && prerequisitesMet(num, caught)
        }
    }

    /**
     * Picks from the rolled tier's [pool]. AnkiGo's own small addition inside Ankimon's rules (Ankimon has no wishlist weighting):
     * a wishlisted species weighs [Tuning.WISHLIST_WEIGHT], every other 1; a wishlisted regional form counts for its base species.
     * Tier roll, level gates, generations and capture requirements are untouched. With no wishlisted species in the pool this is
     * exactly `pool[rng.nextInt(pool.size)]`. Example (Normal pool, gens 1-8, wild Lv 20: 398 species): a wishlisted
     * species goes from 1 in 398 (0.25 %) to 2 in 399 (0.50 %) per Normal encounter (Lv 5: 338, Lv 50: 587, Lv 100: 696;
     * with gen 9: 395 / 459 / 668 / 795).
     */
    internal fun pickFrom(data: GameData, pool: List<Int>, wishlist: Set<Int>, rng: Random): Int {
        if (wishlist.isEmpty()) return pool[rng.nextInt(pool.size)]
        val bases = wishlist.mapTo(HashSet()) { data.species[it]?.baseNum ?: it }
        val extra = pool.count { it in bases } * (Tuning.WISHLIST_WEIGHT - 1)
        if (extra == 0) return pool[rng.nextInt(pool.size)]
        var r = rng.nextInt(pool.size + extra)
        for (n in pool) {
            r -= if (n in bases) Tuning.WISHLIST_WEIGHT else 1
            if (r < 0) return n
        }
        return pool.last()
    }

    /**
     * Rolls tier (from state.reviewsToday, settings.dailyAverage, trainerLevel, main level), then a random species (uniform, wishlisted ones
     * weighted by [Tuning.WISHLIST_WEIGHT], see [pickFrom]) from that tier's pool at [wildLevel]; an empty rare pool falls back to NORMAL, an empty NORMAL to Rattata (19).
     * The pick may then turn into one of its regional forms ([eligibleForms]). MEGA/GMAX rolls resolve to NORMAL. Shiny 1/4096.
     */
    fun roll(data: GameData, state: GameState, wildLevel: Int, rng: Random): Roll {
        val pct = tierPercentages(state.reviewsToday, state.settings.dailyAverage, state.trainerLevel, state.main?.level ?: 1)
        var pick = rng.nextDouble() * 100
        var tier = Tier.NORMAL
        for ((t, w) in pct) {
            if (w <= 0) continue
            tier = t
            pick -= w
            if (pick < 0) break
        }
        if (tier == Tier.MEGA || tier == Tier.GMAX) tier = Tier.NORMAL
        var species = pool(data, tier, state.settings, wildLevel, state.caught)
        if (species.isEmpty() && tier != Tier.NORMAL) {
            tier = Tier.NORMAL
            species = pool(data, tier, state.settings, wildLevel, state.caught)
        }
        val num = if (species.isEmpty()) 19 else pickFrom(data, species, state.settings.wishlist, rng)
        // Regional form: FORM_SWAP_CHANCE per eligible form (Ankimon 7 %); no random number is used when there is none.
        val forms = eligibleForms(data, state.settings, num, wildLevel, state.caught)
        val picked = if (forms.isNotEmpty() && rng.nextDouble() < Tuning.FORM_SWAP_CHANCE * forms.size) forms[rng.nextInt(forms.size)] else num
        return Roll(picked, tier, rng.nextInt(Tuning.SHINY_ODDS) == 0)
    }

    /** Builds the wild Mon (Progression.newMon) with a random EV spread worth level*5.1 (max 252/stat, 510 total). */
    fun makeWild(data: GameData, roll: Roll, level: Int, rng: Random, now: Long): Mon {
        val mon = Progression.newMon(data, roll.species, level, rng, now, roll.shiny)
        mon.tier = roll.tier
        mon.evs = wildEvs(level, rng)
        mon.hp = StatCalc.maxHp(data, mon)
        return mon
    }

    /** Ankimon get_ev_spread (4 styles) scaled down to the level's budget, shape kept (scale_ev_spread_to_level). */
    private fun wildEvs(level: Int, rng: Random): Stats {
        val cap = Tuning.EV_STAT_CAP
        val shape = when (rng.nextInt(4)) {
            0 -> { // random split of 510 over six stats
                val cuts = (0..Tuning.EV_TOTAL_CAP).shuffled(rng).take(5).sorted()
                val edges = listOf(0) + cuts + listOf(Tuning.EV_TOTAL_CAP)
                List(6) { minOf(cap, edges[it + 1] - edges[it]) }
            }
            1 -> { // two random stats at 252, a third at 4
                val s = (0..5).shuffled(rng)
                MutableList(6) { 0 }.also { it[s[0]] = cap; it[s[1]] = cap; it[s[2]] = 4 }
            }
            2 -> listOf(4, 0, cap, 0, cap, 0) // hp + def + spd
            else -> List(6) { 84 }
        }
        val budget = minOf(Tuning.EV_TOTAL_CAP, (level * 51) / 10) // floor(level * 5.1), kept in integers
        val total = shape.sum()
        val out = if (total <= budget) shape else shape.map { minOf(cap, it * budget / total) }
        return Stats.of(out)
    }
}
