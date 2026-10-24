package com.husarp.ankigo.game

/**
 * R17C-9b (PLAN 229): the moves that change abilities, and the one place that changes an ability in a fight (also used by Mummy, Lingering Aroma and
 * Wandering Spirit in [AbilitiesMore]). Mainline Gen 9 rules, which abilities may be changed follow Showdown's flags (the tables below are copied from
 * its `abilities.js`): Entrainment (the target gets the user's ability), Gastro Acid (the target's ability does nothing), Role Play (the user copies
 * the target's), Simple Beam (the target gets Simple), Skill Swap (swap), Worry Seed (the target gets Insomnia, and a sleeper wakes up) and Doodle (the
 * user copies the target's). A change lasts for the fight: it is the side's note [Abilities.OVERRIDE] ("" = no ability, Gastro Acid), and a Main that is
 * switched out gets its own ability back with its fresh side. The new ability starts like on entering ([Abilities.enter]: Intimidate acts, a weather
 * starts ...), and a status the new ability stops is cured ([settle]: Insomnia wakes a sleeper).
 *
 * "The ability a Pokémon really has" is [current]: the note, else its own (or the copy a Transform made); an ability that Neutralizing Gas or a Gastro
 * Acid puts out of action is still the one it has for these moves.
 */
internal object AbilityMoves {
    /** The ids of the seven moves. */
    val IDS: Set<String> = setOf("entrainment", "gastroacid", "roleplay", "simplebeam", "skillswap", "worryseed", "doodle")

    // ---- Showdown's flags of abilities.js ----
    /** `cantsuppress`: Gastro Acid, Entrainment, Simple Beam, Worry Seed fail on them, Neutralizing Gas doesn't stop them, Mummy can't replace them. */
    val CANT_SUPPRESS: Set<String> = setOf(
        "asoneglastrier", "asonespectrier", "battlebond", "comatose", "disguise", "gulpmissile", "iceface", "multitype", "powerconstruct",
        "rkssystem", "schooling", "shieldsdown", "stancechange", "terashift", "zenmode", "zerotohero",
    )
    /** `failroleplay`: Role Play and Doodle can't copy them. */
    val FAIL_ROLEPLAY: Set<String> = setOf(
        "asoneglastrier", "asonespectrier", "battlebond", "comatose", "commander", "disguise", "embodyaspectcornerstone",
        "embodyaspecthearthflame", "embodyaspectteal", "embodyaspectwellspring", "flowergift", "forecast", "hungerswitch", "iceface",
        "illusion", "imposter", "multitype", "neutralizinggas", "poisonpuppeteer", "powerconstruct", "powerofalchemy", "protosynthesis",
        "quarkdrive", "receiver", "rkssystem", "schooling", "shieldsdown", "stancechange", "teraformzero", "terashell", "terashift",
        "trace", "wonderguard", "zenmode", "zerotohero",
    )
    /** `noentrain`: Entrainment can't give them away. */
    val NO_ENTRAIN: Set<String> = FAIL_ROLEPLAY
    /** `failskillswap`: Skill Swap and Wandering Spirit fail when either side has one. */
    val FAIL_SKILLSWAP: Set<String> = setOf(
        "asoneglastrier", "asonespectrier", "battlebond", "comatose", "commander", "disguise", "embodyaspectcornerstone",
        "embodyaspecthearthflame", "embodyaspectteal", "embodyaspectwellspring", "hungerswitch", "iceface", "illusion", "multitype",
        "neutralizinggas", "poisonpuppeteer", "powerconstruct", "protosynthesis", "quarkdrive", "rkssystem", "schooling", "shieldsdown",
        "stancechange", "teraformzero", "terashell", "terashift", "wonderguard", "zenmode", "zerotohero",
    )

    // ---------------------------------------------------------------- reading

    /** The ability [q] really has: the note (Role Play, Mummy ...), else the copy of a Transform, else its own. NOT asked through the hooks, so Neutralizing Gas can read it. */
    fun current(v: BattleView, q: Boolean): String =
        v.side(q).notes[Abilities.OVERRIDE] ?: if (MoveControl.isTransformed(v, q)) v.ability(q) else v.baseAbility(q)

    /** Could the move [id] of [p] change anything now? (Also the Smart move choice's question.) */
    fun works(v: BattleView, p: Boolean, id: String): Boolean {
        val mine = current(v, p)
        val theirs = current(v, !p)
        if (v.mon(!p).hp <= 0) return false
        return when (id) {
            "entrainment" -> mine.isNotEmpty() && mine != theirs && mine !in NO_ENTRAIN && theirs !in CANT_SUPPRESS
            "gastroacid" -> theirs.isNotEmpty() && theirs !in CANT_SUPPRESS
            "roleplay", "doodle" -> theirs.isNotEmpty() && mine != theirs && mine !in CANT_SUPPRESS && theirs !in FAIL_ROLEPLAY
            "simplebeam" -> theirs !in CANT_SUPPRESS && theirs != "simple" && theirs != "truant"
            "skillswap" -> mine != theirs && mine !in FAIL_SKILLSWAP && theirs !in FAIL_SKILLSWAP
            "worryseed" -> theirs !in CANT_SUPPRESS && theirs != "insomnia"
            else -> false
        }
    }

    // ---------------------------------------------------------------- changing

    /**
     * [q]'s ability becomes [id] ("" = none) for the rest of the fight: the line [text] (the new ability's name is the float label, "Suppressed" for
     * ""), a status the new ability stops is cured, then the new ability starts. No [text] = say nothing (Skill Swap writes one line for both).
     */
    fun set(r: Round, q: Boolean, id: String, text: String?, start: Boolean = true) {
        r.side(q).notes[Abilities.OVERRIDE] = id
        if (text != null) r.fx(q, text, EffectKind.OTHER, label = if (id.isEmpty()) "Suppressed" else Abilities.name(id))
        if (id.isEmpty()) return
        settle(r, q)
        if (start && r.mon(q).hp > 0) Abilities.enter(r, q, id)
    }

    /** The status of [q] is cured when its (new) ability stops it (Insomnia and sleep, Limber and paralysis ...). */
    private fun settle(r: Round, q: Boolean) {
        val status = r.side(q).status ?: return
        val ab = r.ability(q)
        if (ab.isNotEmpty() && (Abilities.stopsStatus(r, ab, status) || AbilitiesMore.stopsStatus(ab, status))) Abilities.cure(r, q, ab)
    }

    /** Skill Swap and Wandering Spirit: the two sides trade their abilities (one line). */
    fun swap(r: Round, a: Boolean, b: Boolean, text: String) {
        val ma = current(r, a)
        val mb = current(r, b)
        r.fx(a, text, EffectKind.OTHER, label = "Swapped")
        set(r, a, mb, null)
        set(r, b, ma, null)
    }

    // ---------------------------------------------------------------- the moves

    /** The moves as status moves: true = it worked (a failure gives false and the engine writes "But it failed!"). */
    fun use(r: Round, p: Boolean, move: Move): Boolean {
        val id = move.id
        if (!works(r, p, id)) return false
        val tp = !p
        val mine = current(r, p)
        val theirs = current(r, tp)
        val n = r.mon(p).name
        val tn = r.mon(tp).name
        when (id) {
            "entrainment" -> set(r, tp, mine, "$tn acquired ${Abilities.name(mine)}!")
            "gastroacid" -> set(r, tp, "", "$tn's Ability was suppressed!")
            "roleplay", "doodle" -> set(r, p, theirs, "$n copied $tn's ${Abilities.name(theirs)}!")
            "simplebeam" -> set(r, tp, "simple", "$tn acquired Simple!")
            "skillswap" -> swap(r, p, tp, "$n swapped Abilities with its target!")
            "worryseed" -> set(r, tp, "insomnia", "$tn acquired Insomnia!")
        }
        return true
    }

    /** The sentences [MoveText] shows for the seven moves (through [AbilitiesMore.describe]). */
    val TEXT: Map<String, String> = mapOf(
        "entrainment" to "The target's ability becomes your ability for the rest of the fight. Fails if either ability can't be changed.",
        "gastroacid" to "The target's ability does nothing for the rest of the fight. Fails if it can't be suppressed.",
        "roleplay" to "Your ability becomes the target's for the rest of the fight. Fails if it can't be copied.",
        "simplebeam" to "The target's ability becomes Simple for the rest of the fight.",
        "skillswap" to "You and the target swap abilities for the rest of the fight. Fails if either ability can't be swapped.",
        "worryseed" to "The target's ability becomes Insomnia for the rest of the fight, and a sleeping target wakes up.",
        "doodle" to "Your ability becomes the target's for the rest of the fight. Fails if it can't be copied.",
    )
}
