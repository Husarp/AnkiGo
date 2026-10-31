package com.husarp.ankigo.game

import kotlin.math.max

/** PLAN 270: entry hazards persist on the side; switch locks last a few battle rounds. */
internal object Hazards : BattleRules {
    const val TRAP = "switchtrap"
    const val TRAP_TURNS = 3
    val hazards = mapOf("spikes" to 3, "toxicspikes" to 2, "stealthrock" to 1, "stickyweb" to 1)
    private val traps = setOf("meanlook", "block", "spiderweb", "octolock", "fairylock")
    override val handles = hazards.keys + traps

    override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? {
        val cap = hazards[move.id]
        if (cap != null) {
            val c = r.conds(!p)
            val n = c[move.id] ?: 0
            if (n >= cap) return false
            c[move.id] = n + 1
            r.fx(!p, "${move.name} was scattered around ${r.mon(!p).name}'s team!", EffectKind.FIELD, move.id, n + 1)
            return true
        }
        if (move.id !in traps) return null
        val targets = if (move.id == "fairylock") listOf(true, false) else listOf(!p)
        var changed = false
        for (q in targets) {
            if ((Type.GHOST in r.types(q) && move.id != "octolock") || TRAP in r.side(q).volatiles) continue
            lock(r, q, move.id, move.name, if (move.id == "fairylock") 2 else TRAP_TURNS)
            changed = true
        }
        return changed
    }

    private fun lock(r: Round, q: Boolean, id: String, name: String, turns: Int) {
        r.side(q).volatiles[TRAP] = turns
        r.side(q).notes[TRAP] = id
        r.side(q).notes["$TRAP.name"] = name
        r.fx(q, "$name is in effect — ${r.mon(q).name} can't switch!", EffectKind.VOLATILE, TRAP, turns)
    }

    fun blocker(v: BattleView, p: Boolean = true): String? {
        if (v.mon(p).hp <= 0 || v.mon(!p).hp <= 0 || Type.GHOST in v.types(p)) return null
        if (v.ability(!p) == "magnetpull" && Type.STEEL in v.types(p)) return "Magnet Pull is in effect — you can’t switch."
        val s = v.side(p)
        val n = s.volatiles[TRAP] ?: run {
            if (!v.side(!p).entered) {
                val a = v.ability(!p)
                if (a == "arenatrap" && v.grounded(p) || a == "shadowtag" && v.ability(p) != a)
                    return "${if (a == "arenatrap") "Arena Trap" else "Shadow Tag"} is in effect — you can't switch ($TRAP_TURNS rounds left)."
            }
            return null
        }
        if (n <= 0) return null
        val source = s.notes[TRAP]
        if (source == "arenatrap" && (!v.grounded(p) || v.ability(!p) != source)) return null
        if (source == "shadowtag" && (v.ability(p) == source || v.ability(!p) != source)) return null
        val name = s.notes["$TRAP.name"] ?: "Trapping"
        return "$name is in effect — you can't switch ($n round${if (n == 1) "" else "s"} left)."
    }

    override fun onEnter(r: Round, p: Boolean) {
        // Only the picked abilities trap; other switching and hazard abilities keep their existing rules.
        val a = r.ability(p)
        val q = !p
        if (r.mon(q).hp > 0 && Type.GHOST !in r.types(q) &&
            (a == "arenatrap" && r.grounded(q) || a == "shadowtag" && r.ability(q) != "shadowtag")) {
            lock(r, q, a, if (a == "arenatrap") "Arena Trap" else "Shadow Tag", TRAP_TURNS)
        }
        if (TRAP !in r.side(p).volatiles) {
            val foe = r.ability(!p)
            if (Type.GHOST !in r.types(p) && (foe == "arenatrap" && r.grounded(p) || foe == "shadowtag" && r.ability(p) != foe))
                lock(r, p, foe, if (foe == "arenatrap") "Arena Trap" else "Shadow Tag", TRAP_TURNS)
        }
        if (r.side(p).volatiles.remove("hazards.entered") == null) applyEntry(r, p)
    }

    /** HP lost on entry, for the switch picker; the same type, grounding and indirect-damage rules as entry. */
    fun entryDamage(v: BattleView, p: Boolean): Int {
        if (v.ability(p) == "magicguard") return 0
        val c = v.conds(p)
        var total = 0
        fun add(n: Int) { total += n }
        if ((c["stealthrock"] ?: 0) > 0) add(max(1, (v.maxHp(p) * TypeChart.eff(Type.ROCK, v.types(p)) / 8).toInt()))
        val layers = c["spikes"] ?: 0
        if (layers > 0 && v.grounded(p)) add(max(1, v.maxHp(p) / when (layers) { 1 -> 8; 2 -> 6; else -> 4 }))
        return total.coerceIn(0, v.mon(p).hp.coerceAtLeast(0))
    }

    private fun applyEntry(r: Round, p: Boolean) {
        val c = r.conds(p)
        val m = r.mon(p)
        if (m.hp <= 0) return
        val rocks = c["stealthrock"] ?: 0
        if (rocks > 0) {
            val eff = r.types(p).fold(1.0) { x, t -> x * TypeChart.single(Type.ROCK, t) }
            r.hurt(p, max(1, (r.maxHp(p) * eff / 8).toInt()), "Pointed stones dug into ${m.name}!", EffectKind.RESIDUAL, source = "hazard")
        }
        if (m.hp <= 0 || !r.grounded(p)) return
        val spikes = c["spikes"] ?: 0
        if (spikes > 0) r.hurt(p, max(1, r.maxHp(p) / when (spikes) { 1 -> 8; 2 -> 6; else -> 4 }),
            "${m.name} was hurt by Spikes!", EffectKind.RESIDUAL, source = "hazard")
        if (m.hp <= 0) return
        val toxic = c["toxicspikes"] ?: 0
        if (toxic > 0) {
            if (Type.POISON in r.types(p)) {
                c.remove("toxicspikes")
                r.fx(p, "${m.name} absorbed the Toxic Spikes!", EffectKind.FIELD, "toxicspikes", -1)
            } else if (Type.STEEL !in r.types(p)) r.setStatus(p, if (toxic >= 2) "tox" else "psn", Type.POISON)
        }
        if ((c["stickyweb"] ?: 0) > 0) r.boost(p, mapOf("spe" to -1), byFoe = true)
    }

    override fun endOfRound(r: Round) {
        for (q in listOf(true, false)) {
            val s = r.side(q)
            val n = s.volatiles[TRAP] ?: continue
            if (s.notes[TRAP] == "octolock" && r.mon(q).hp > 0) r.boost(q, mapOf("def" to -1, "spd" to -1), byFoe = true)
            if (n > 1) s.volatiles[TRAP] = n - 1 else {
                s.volatiles.remove(TRAP); s.notes.remove(TRAP); s.notes.remove("$TRAP.name")
                r.fx(q, "${r.mon(q).name} can switch again!", EffectKind.VOLATILE, TRAP, -1)
            }
        }
    }

    override fun useful(v: BattleView, p: Boolean, move: Move): Boolean? {
        hazards[move.id]?.let { return (v.conds(!p)[move.id] ?: 0) < it }
        if (move.id in traps) return (Type.GHOST !in v.types(!p) || move.id == "octolock") && TRAP !in v.side(!p).volatiles
        return null
    }

    override fun describe(m: Move): List<String> = when (m.id) {
        "spikes" -> listOf("Scatters Spikes on the target's side. A grounded Pokémon switching in loses 1/8, 1/6 or 1/4 of its max HP with 1, 2 or 3 layers.")
        "stealthrock" -> listOf("Scatters pointed stones on the target's side. A Pokémon switching in loses 1/8 of its max HP, multiplied by its weakness or resistance to Rock.")
        "toxicspikes" -> listOf("Scatters up to 2 layers of Toxic Spikes. A grounded Pokémon switching in is poisoned (badly with 2 layers). Poison types absorb them; Steel types are immune.")
        "stickyweb" -> listOf("Spreads a Sticky Web on the target's side. A grounded Pokémon switching in loses 1 Speed stage (×2/3).")
        "meanlook", "block", "spiderweb" -> listOf("Stops the target from switching for $TRAP_TURNS rounds. Ghost types can still switch.")
        "octolock" -> listOf("Stops the target from switching for $TRAP_TURNS rounds and lowers its Defense and Sp. Def by 1 stage at each round's end. Ghost types can still switch.")
        "fairylock" -> listOf("Stops both Pokémon from switching through the next round. Ghost types can still switch.")
        else -> emptyList()
    }
}
