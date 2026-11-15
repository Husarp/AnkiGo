package com.husarp.ankigo.game

/** Percentage heals and recipient-owned effects; the fight field keeps them across switches and saves. */
internal object HealingItems : BattleRules {
    val percent = linkedMapOf(
        "potion" to 25, "super-potion" to 50, "hyper-potion" to 75, "max-potion" to 100,
        "full-restore" to 100, "berry-juice" to 50, "sweet-heart" to 70, "soda-pop" to 40,
        "lemonade" to 50, "moomoo-milk" to 60, "fresh-water" to 40, "energy-powder" to 70, "energy-root" to 100,
    )
    val text = mapOf(
        "potion" to "Heals 25% max HP.",
        "super-potion" to "Heals 50% max HP.",
        "hyper-potion" to "Heals 75% max HP.",
        "max-potion" to "Heals 100% max HP; does not cure status.",
        "full-restore" to "Heals 100% max HP and cures burn, poison, paralysis, sleep or freeze.",
        "berry-juice" to "Heals 10% max HP now + 10% at the end of each of the next 4 turns (50% total). Outside battle: heals 50% now.",
        "sweet-heart" to "Heals 0% now; 70% max HP at the start of the next turn. Outside battle: heals 70% now.",
        "soda-pop" to "Heals 40% max HP; Speed ×1.25 for this Pokémon until the fight ends.",
        "lemonade" to "Heals 50% max HP; Attack ×1.25 for this Pokémon until the fight ends.",
        "moomoo-milk" to "Heals 60% max HP; Defense ×1.25 for this Pokémon until the fight ends.",
        "fresh-water" to "Heals 40% max HP and clears this Pokémon's lowered stat stages in battle.",
        "energy-powder" to "Heals 70% max HP; Speed ×0.75 for this Pokémon until the fight ends.",
        "energy-root" to "Heals 100% max HP; Attack ×0.75 for this Pokémon until the fight ends.",
    )
    val summary = mapOf(
        "potion" to "Heals 25% max HP", "super-potion" to "Heals 50% max HP", "hyper-potion" to "Heals 75% max HP",
        "max-potion" to "Heals 100% max HP", "full-restore" to "100% max HP + status cure",
        "berry-juice" to "10% now + 10%/turn ×4 (50%)", "sweet-heart" to "0% now · 70% next turn",
        "soda-pop" to "40% HP · Speed ×1.25", "lemonade" to "50% HP · Attack ×1.25", "moomoo-milk" to "60% HP · Defense ×1.25",
        "fresh-water" to "40% HP + clears stat drops", "energy-powder" to "70% HP · Speed ×0.75", "energy-root" to "100% HP · Attack ×0.75",
    )
    val short = summary + mapOf(
        "full-restore" to "100% HP + status cure", "berry-juice" to "10% + 10%/turn ×4", "fresh-water" to "40% HP · clear drops",
    )
    val boosts = mapOf(
        "soda-pop" to "heal.speed", "lemonade" to "heal.attack", "moomoo-milk" to "heal.defense",
        "energy-powder" to "heal.slow", "energy-root" to "heal.weak",
    )
    val effects = boosts.values + listOf("heal.regen", "heal.pending")
    private fun key(mon: Mon, effect: String) = "$effect/${mon.uid}"
    private fun live(state: GameState) = state.battle?.takeIf { it.wild.hp > 0 }
    private fun side(state: GameState, mon: Mon) = live(state)?.player?.takeIf { state.main?.uid == mon.uid }
    private fun amount(max: Int, percent: Int) = (max * percent / 100).coerceAtLeast(1)

    fun sync(battle: Battle, mon: Mon) {
        for (effect in effects) {
            val n = battle.field.player[key(mon, effect)] ?: 0
            if (n > 0) battle.player.volatiles[effect] = n else battle.player.volatiles.remove(effect)
        }
    }
    fun canUse(data: GameData, state: GameState, item: String, mon: Mon?): Boolean {
        if (mon == null) return false
        val b = live(state)
        if (item == "sweet-heart" && b != null && mon.hp <= 0) return false
        val hurt = mon.hp < StatCalc.maxHp(data, mon)
        if (item == "sweet-heart" && b?.field?.player?.get(key(mon, "heal.pending")) == 1) return false
        return hurt || (item == Items.FULL_RESTORE && side(state, mon)?.status != null) ||
            (item == "fresh-water" && side(state, mon)?.boosts?.any { it < 0 } == true) ||
            (b != null && mon.hp > 0 && boosts[item]?.let { b.field.player[key(mon, it)] != 1 } == true && item != "energy-powder" && item != "energy-root")
    }
    fun use(data: GameData, state: GameState, item: String, mon: Mon, now: Long): ActionResult {
        if (!canUse(data, state, item, mon)) return ActionResult(false, "${mon.name} is already healthy.")
        val b = live(state)
        val max = StatCalc.maxHp(data, mon)
        val pct = if (b == null) percent.getValue(item) else when (item) { "berry-juice" -> 10; "sweet-heart" -> 0; else -> percent.getValue(item) }
        val healed = minOf(max - mon.hp, if (pct == 0) 0 else amount(max, pct))
        mon.hp += healed
        val events = mutableListOf<GameEvent>()
        var message = "${Items.name(item)} healed ${mon.name} by $healed HP"
        if (b != null) {
            boosts[item]?.let { b.field.player[key(mon, it)] = 1; message += "; ${text.getValue(item).substringAfter("; ")}" }
            when (item) {
                "berry-juice" -> { b.field.player[key(mon, "heal.regen")] = 4; message += "; heals 10% max HP for 4 more turns" }
                "sweet-heart" -> { b.field.player[key(mon, "heal.pending")] = 1; message = "Sweet Heart: 70% max HP healing at the start of this Pokémon's next turn" }
            }
            side(state, mon)?.let { side ->
                if (item == "fresh-water") {
                    side.boosts.indices.filter { side.boosts[it] < 0 }.forEach { i ->
                        val delta = -side.boosts[i]; side.boosts[i] = 0
                        events += GameEvent.Effect(true, "${mon.shownName}'s lowered stats were cleared!", mon.hp, max, side.status, EffectKind.STAT_CHANGE, listOf("atk", "def", "spa", "spd", "spe", "accuracy", "evasion")[i], delta)
                    }
                    message += "; lowered stat stages cleared"
                }
                if (item == Items.FULL_RESTORE && side.status != null) {
                    val status = side.status!!
                    side.status = null; side.statusTurns = 0; side.volatiles.remove(CoreRules.SLEEP_TURNS)
                    val cured = when (status) { "slp" -> "${mon.name} woke up."; "frz" -> "${mon.name} thawed out."; "par" -> "${mon.name}'s paralysis was healed."; "psn", "tox" -> "${mon.name}'s poison was healed."; else -> "${mon.name}'s burn was healed." }
                    events += GameEvent.Effect(true, cured, mon.hp, max, null, EffectKind.STATUS_CURED, status)
                    message = if (healed > 0) "$message and cured its status" else "${Items.name(item)} cured ${mon.name}"
                }
                sync(b, mon)
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
        fun active(effect: String) = v.conds(p)[key(v.mon(p), effect)] == 1
        var result = s
        for ((stat, up, down) in listOf(Triple(Stat.ATK, "heal.attack", "heal.weak"), Triple(Stat.DEF, "heal.defense", ""), Triple(Stat.SPE, "heal.speed", "heal.slow"))) {
            val factor = (if (active(up)) 1.25 else 1.0) * (if (active(down)) 0.75 else 1.0)
            result = result.with(stat, (s[stat] * factor).toInt().coerceAtLeast(1))
        }
        return result
    }
    fun startOfRound(r: Round) {
        val mon = r.mon(true)
        if (mon.hp <= 0 || r.mon(false).hp <= 0) return
        if (r.conds(true).remove(key(mon, "heal.pending")) != null) {
            if (!SideMoves.healBlocked(r, true)) r.heal(true, amount(r.maxHp(true), 70), "Sweet Heart restored 70% max HP!", label = "Sweet Heart")
            r.side(true).volatiles.remove("heal.pending")
            r.fx(true, "Sweet Heart's pending healing ended.", EffectKind.VOLATILE, "heal.pending", -1)
        }
    }
    override fun endOfRound(r: Round) {
        val mon = r.mon(true)
        if (mon.hp <= 0 || r.mon(false).hp <= 0) return
        val k = key(mon, "heal.regen")
        val turns = r.conds(true)[k] ?: return
        if (!SideMoves.healBlocked(r, true)) r.heal(true, amount(r.maxHp(true), 10), "Berry Juice restored 10% max HP!", label = "Berry Juice")
        if (turns <= 1) {
            r.conds(true).remove(k); r.side(true).volatiles.remove("heal.regen")
            r.fx(true, "Berry Juice's healing ended.", EffectKind.VOLATILE, "heal.regen", -1)
        } else {
            r.conds(true)[k] = turns - 1; r.side(true).volatiles["heal.regen"] = turns - 1
        }
    }
}
