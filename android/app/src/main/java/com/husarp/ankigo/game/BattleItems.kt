package com.husarp.ankigo.game

import kotlin.random.Random

/** Item effects use the fight's saved field, keyed by holder, so switching never transfers a snack boost. */
internal object BattleItems : BattleRules {
    val snacks = linkedMapOf(
        "rage-candy-bar" to "Heals 40% max HP; Attack, Defense, Special Attack, Special Defense and Speed ×1.1 this fight.",
        "big-malasada" to "Heals 60% of missing HP.",
        "shalour-sable" to "No healing; the next damaging hit taken is 30% weaker this fight.",
        "lumiose-galette" to "Heals 25% max HP and cures burn, poison, paralysis, sleep or freeze.",
        "casteliacone" to "Heals 40% max HP; Special Defense ×1.25 this fight.",
        "pewter-crunchies" to "Heals 40% max HP; Special Attack ×1.25 this fight.",
        "old-gateau" to "Heals 50% max HP without a wild attack; your round and own attack are unchanged. Very rare in the Shop; one per offer.",
        "lava-cookie" to "50% chance to heal 75% max HP; 50% chance to lose 25% max HP (can faint).",
    )
    val held = linkedMapOf(
        "leftovers" to "Heals 1/16 max HP at the end of each battle turn.",
        "shell-bell" to "Heals 1/8 of the HP damage dealt by each attack.",
        "rocky-helmet" to "Takes 20% less hit damage; the attacker loses 20% of the HP damage taken.",
        "life-orb" to "Deals 30% more damage; loses 10% max HP per attempted damaging attack, including misses.",
        "expert-belt" to "Deals 20% more damage with super-effective attacks.",
        "focus-sash" to "Survives one knockout hit with 1 HP, then the held sash is used up. Works below full HP too.",
    )
    val boosts = mapOf("rage-candy-bar" to "snack.rage", "casteliacone" to "snack.spd", "pewter-crunchies" to "snack.spa", "shalour-sable" to "snack.shield")
    private fun key(mon: Mon, effect: String) = "$effect/${mon.uid}"
    fun sync(battle: Battle, mon: Mon) {
        for (effect in boosts.values) {
            if (battle.field.player[key(mon, effect)] == 1) battle.player.volatiles[effect] = 1
            else battle.player.volatiles.remove(effect)
        }
    }
    private fun active(v: BattleView, p: Boolean, effect: String) = v.conds(p)[key(v.mon(p), effect)] == 1
    private fun items(v: BattleView, p: Boolean): List<String> = if (
        v.ability(p) == "klutz" || (v.side(p).volatiles["embargo"] ?: 0) > 0 || (v.field.rooms["magicroom"] ?: 0) > 0
    ) emptyList() else v.mon(p).heldItems
    fun canUse(data: GameData, state: GameState, item: String, mon: Mon?): Boolean {
        if (mon == null) return false
        val b = state.battle
        if (item == "awakening") return b != null && b.wild.hp > 0 && state.main?.uid == mon.uid && mon.hp > 0 && b.player.status == "slp"
        if (item == "shalour-sable") return b != null && b.wild.hp > 0 && mon.hp > 0 && b.field.player[key(mon, "snack.shield")] != 1
        val boost = boosts[item]
        return mon.hp < StatCalc.maxHp(data, mon) || (b != null && b.wild.hp > 0 && mon.hp > 0 && boost != null && b.field.player[key(mon, boost)] != 1) ||
            (item == "lumiose-galette" && state.main?.uid == mon.uid && b?.player?.status != null)
    }
    fun use(data: GameData, state: GameState, item: String, mon: Mon, rng: Random, now: Long): ActionResult {
        if (!canUse(data, state, item, mon)) return ActionResult(false, if (item == "awakening") "${mon.shownName} isn't asleep in battle." else "It won't have any effect.")
        val b = state.battle
        val max = StatCalc.maxHp(data, mon)
        val missing = max - mon.hp
        val events = mutableListOf<GameEvent>()
        val amount = when (item) {
            "big-malasada" -> (missing * 60 / 100).coerceAtLeast(1)
            "shalour-sable", "awakening" -> 0
            "lumiose-galette" -> (max * 25 / 100).coerceAtLeast(1)
            "old-gateau" -> (max * 50 / 100).coerceAtLeast(1)
            "lava-cookie" -> if (rng.nextBoolean()) (max * 75 / 100).coerceAtLeast(1) else -(max * 25 / 100).coerceAtLeast(1)
            else -> (max * 40 / 100).coerceAtLeast(1)
        }
        val before = mon.hp
        mon.hp = (mon.hp + amount).coerceIn(0, max)
        var message = "${Items.name(item)}: ${mon.shownName} " + when {
            mon.hp > before -> "healed ${mon.hp - before} HP."
            mon.hp < before -> "lost ${before - mon.hp} HP."
            else -> "used the item."
        }
        if (mon.hp > before && state.main?.uid == mon.uid) events += GameEvent.Effect(true, message, mon.hp, max, b?.player?.status, EffectKind.HEAL, amount = mon.hp - before)
        if (b != null && b.wild.hp > 0) {
            boosts[item]?.let { effect ->
                b.field.player[key(mon, effect)] = 1
                message += if (item == "shalour-sable") " Its next hit is 30% weaker." else " Its boost lasts this fight."
            }
            if (state.main?.uid == mon.uid) {
                sync(b, mon)
                if ((item == "awakening" || item == "lumiose-galette") && b.player.status != null) {
                    val status = b.player.status
                    b.player.status = null; b.player.statusTurns = 0
                    b.player.volatiles.remove(CoreRules.SLEEP_TURNS)
                    message += if (status == "slp") " ${mon.shownName} woke up." else " Its status was cured."
                    events += GameEvent.Effect(true, message, mon.hp, max, null, EffectKind.STATUS_CURED, status)
                }
            }
        }
        Economy.take(state, item)
        StudyStats.addHealItem(state)
        Badges.award(state, 20, events, now, mon, data)
        Achievements.bump(state, Metric.ITEMS_USED)
        Achievements.settle(data, state, now, events)
        return ActionResult(true, message, events)
    }
    override fun onEnter(r: Round, p: Boolean) { if (p) r.battle?.let { sync(it, r.mon(p)) } }
    override fun stats(v: BattleView, p: Boolean, s: Stats): Stats {
        var result = s
        for (stat in listOf(Stat.ATK, Stat.DEF, Stat.SPA, Stat.SPD, Stat.SPE)) {
            val factor = (if (active(v, p, "snack.rage")) 1.1 else 1.0) *
                (if ((stat == Stat.SPA && active(v, p, "snack.spa")) || (stat == Stat.SPD && active(v, p, "snack.spd"))) 1.25 else 1.0)
            result = result.with(stat, (s[stat] * factor).toInt().coerceAtLeast(1))
        }
        return result
    }
    override fun finalMod(h: Hit): Double {
        val held = items(h.view, h.attackerIsMain)
        return (if ("life-orb" in held) 1.3 else 1.0) *
            (if ("expert-belt" in held && rulesChain(TypeChart.eff(h.type, h.dTypes)) { rule, eff -> rule.effectiveness(h, eff) } > 1.0) 1.2 else 1.0)
    }
    override fun fixedFinalMod(h: Hit): Double = finalMod(h)
    override fun takeDamage(r: Round, tp: Boolean, h: Hit, damage: Int): Int {
        if (damage <= 0) return damage
        var n = damage
        if (active(r, tp, "snack.shield")) {
            r.conds(tp).remove(key(r.mon(tp), "snack.shield")); r.side(tp).volatiles.remove("snack.shield")
            n = (n * 70 / 100).coerceAtLeast(1)
            r.fx(tp, "Shalour Sable weakened the hit by 30%!", EffectKind.BLOCKED)
        }
        if ("rocky-helmet" in items(r, tp)) n = (n * 80 / 100).coerceAtLeast(1)
        if ("focus-sash" in items(r, tp) && r.mon(tp).hp > 0 && n >= r.mon(tp).hp) {
            r.mon(tp).removeHeld("focus-sash"); r.side(tp).notes["consumeditem"] = "focus-sash"
            r.fx(tp, "${r.mon(tp).shownName} held on with its Focus Sash!", EffectKind.BLOCKED, label = "Focus Sash", hp = 1)
            n = r.mon(tp).hp - 1
        }
        return n
    }
    override fun afterDamage(r: Round, tp: Boolean, h: Hit, damage: Int) {
        if ("rocky-helmet" in items(r, tp) && r.mon(!tp).hp > 0)
            r.hurt(!tp, (damage * 20 / 100).coerceAtLeast(1), "Rocky Helmet hurt the attacker!", label = "Rocky Helmet")
    }
    override fun afterHit(r: Round, p: Boolean, move: Move, damage: Int) {
        if ("shell-bell" in items(r, p) && damage > 0 && r.mon(p).hp > 0 && !SideMoves.healBlocked(r, p))
            r.heal(p, (damage / 8).coerceAtLeast(1), "Shell Bell restored HP!", label = "Shell Bell")
    }
    override fun afterMove(r: Round, p: Boolean, move: Move, failed: Boolean) {
        if (move.category != Category.STATUS && "life-orb" in items(r, p) && r.mon(p).hp > 0)
            r.hurt(p, (r.maxHp(p) / 10).coerceAtLeast(1), "Life Orb cost HP!", label = "Life Orb")
    }
    override fun afterCalledMove(r: Round, p: Boolean, move: Move) = afterMove(r, p, move, false)
    override fun endOfRound(r: Round) {
        for (p in listOf(true, false)) if ("leftovers" in items(r, p) && r.mon(p).hp > 0 && !SideMoves.healBlocked(r, p))
            r.heal(p, (r.maxHp(p) / 16).coerceAtLeast(1), "Leftovers restored HP!", label = "Leftovers")
    }
}
