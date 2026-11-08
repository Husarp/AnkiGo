package com.husarp.ankigo.game

/** PLAN 336: singles adaptations. All counters live in the fight, never on the saved Pokémon. */
internal object AbilityAdaptations : BattleRules {
    const val SWITCH_OFFER = "ab.switchOffer"
    private const val HIT_COUNT = "ab.successfulHits"
    private const val HALF = "ab.half"
    val berries = mapOf("oran-berry" to "", "sitrus-berry" to "", "figy-berry" to "",
        "liechi-berry" to "atk", "ganlon-berry" to "def", "petaya-berry" to "spa", "apicot-berry" to "spd", "salac-berry" to "spe")
    private fun team(v: BattleView, p: Boolean) = if (p) v.battle?.team.orEmpty().filter { it.uid != v.mon(p).uid } else emptyList()
    private fun once(v: BattleView, p: Boolean, key: String): Boolean = v.conds(p).putIfAbsent("ab.once.${v.mon(p).uid}.$key", 1) == null
    private fun used(v: BattleView, p: Boolean, key: String) = v.conds(p)["ab.once.${v.mon(p).uid}.$key"] == 1
    private fun alive(r: Round) = r.main.hp > 0 && r.wild.hp > 0
    private fun say(r: Round, p: Boolean, ab: String, tail: String = "!") = Abilities.say(r, p, ab, tail)

    internal fun enter(r: Round, p: Boolean, ab: String) {
        val s = r.side(p)
        if (ab == "soulheart" && r.mon(!p).hp <= r.maxHp(!p) / 2) once(r, p, HALF)
        when (ab) {
            "hospitality" -> if (!SideMoves.healBlocked(r, p) && once(r, p, ab)) r.heal(p, maxOf(1, r.maxHp(p) / 4), "${r.mon(p).name}'s Hospitality restored HP!", label = "Hospitality")
            "curiousmedicine" -> if (once(r, p, ab)) { s.boosts.indices.forEach { if (s.boosts[it] < 0) s.boosts[it] = 0 }; say(r, p, ab, " cleared its negative stat stages!") }
            "commander" -> if (once(r, p, ab)) { say(r, p, ab); r.boost(p, listOf("atk", "def", "spa", "spd", "spe").associateWith { 2 }, byFoe = false) }
            "terashift" -> if (once(r, p, ab)) { say(r, p, ab); r.boost(p, mapOf("def" to 1, "spd" to 1), byFoe = false) }
            "receiver" -> team(r, p).lastOrNull { it.hp <= 0 && BattleView.normalizeAbility(it.ability) !in setOf("receiver", "powerofalchemy", "trace", "imposter", "") }?.let {
                s.notes[Abilities.OVERRIDE] = BattleView.normalizeAbility(it.ability)
                say(r, p, ab, " received ${it.ability} from ${it.name}!")
                Abilities.enter(r, p, r.ability(p))
            }
            "supremeoverlord" -> s.volatiles[ab] = team(r, p).count { it.hp <= 0 }.coerceAtMost(5)
            "frisk" -> say(r, p, ab, ": ${r.mon(!p).heldItem?.let(Items::name) ?: "No held item"}.")
            "forewarn" -> { val moves = r.moves(!p); val power = moves.maxOfOrNull { it.power }; say(r, p, ab, ": ${moves.filter { it.power == power }.joinToString { it.name }.ifEmpty { "No moves" }}.") }
            "anticipation" -> { val moves = r.moves(!p).filter { it.ohko || (it.damaging && TypeChart.eff(it.type, r.types(p)) > 1.0) }; say(r, p, ab, ": ${moves.joinToString { it.name }.ifEmpty { "No threatening moves" }}.") }
        }
    }

    override fun onEnter(r: Round, p: Boolean) {
        r.conds(p).putIfAbsent("ab.startedHeld.${r.mon(p).uid}", if (r.mon(p).heldItem != null) 1 else 0)
        refreshIce(r, p)
        eat(r, p)
    }

    private fun counter(v: BattleView, p: Boolean) = "${HIT_COUNT}.${v.mon(p).uid}"
    private fun iceKey(v: BattleView, p: Boolean) = "ab.iceBroken.${v.mon(p).uid}"
    internal fun refreshIce(r: Round, p: Boolean) {
        if (r.ability(p) != "iceface") return
        val weather = r.weather()
        if (weather in setOf("hail", "snow") && r.side(p).notes["ab.iceWeather"] != weather && r.conds(p)[iceKey(r, p)] == 1 && once(r, p, "iceRestore")) {
            r.conds(p).remove(iceKey(r, p)); say(r, p, "iceface", " restored its ice face!")
        }
        r.side(p).notes["ab.iceWeather"] = weather ?: ""
    }

    override fun onLeave(battle: Battle, data: GameData, p: Boolean, mon: Mon) {
        if (!p || mon.hp <= 0) return
        val ab = battle.player.notes[Abilities.OVERRIDE] ?: BattleView.normalizeAbility(mon.ability)
        if (ab == "naturalcure") { battle.player.status = null; battle.player.statusTurns = 0; battle.player.volatiles.remove(CoreRules.SLEEP_TURNS) }
        if (ab == "zerotohero") battle.field.player["ab.hero.${mon.uid}"] = 1
    }

    override fun finalMod(h: Hit): Double {
        var factor = when (Abilities.aAb(h)) {
            "battery" -> if (!h.physical) 1.3 else 1.0
            "powerspot" -> 1.3
            "merciless" -> if (h.dSide.status in setOf("psn", "tox")) 1.5 else 1.0
            "supremeoverlord" -> 1.0 + 0.1 * (h.aSide.volatiles["supremeoverlord"] ?: 0)
            "sniper", "superluck" -> if (((h.view.conds(h.attackerIsMain)[counter(h.view, h.attackerIsMain)] ?: 0) + 1) % 4 == 0) { if (Abilities.aAb(h) == "sniper") 1.5 else 1.25 } else 1.0
            else -> 1.0
        }
        factor *= when (Abilities.dAb(h)) {
            "friendguard" -> 0.75
            "pressure", "battlearmor", "shellarmor", "telepathy" -> 0.9
            "illusion" -> if (!used(h.view, !h.attackerIsMain, "illusion")) 0.9 else 1.0
            else -> 1.0
        }
        return factor
    }
    override fun attackMod(h: Hit): Double = when (Abilities.aAb(h)) {
        "plus", "minus" -> if (!h.physical && team(h.view, h.attackerIsMain).any { it.hp > 0 && BattleView.normalizeAbility(it.ability) in setOf("plus", "minus") }) 1.5 else 1.0
        "flowergift" -> if (h.physical && h.view.weather() == "sun") 1.5 else 1.0
        "schooling" -> if (h.attacker.level >= 20 && h.attacker.hp > h.view.maxHp(h.attackerIsMain) / 4) 1.5 else 1.0
        "zerotohero" -> if (h.view.conds(h.attackerIsMain)["ab.hero.${h.attacker.uid}"] == 1) 1.5 else 1.0
        else -> 1.0
    }
    override fun defenseMod(h: Hit): Double = if (Abilities.dAb(h) == "flowergift" && !h.physical && h.view.weather() == "sun") 1.5 else 1.0
    override fun speedMod(v: BattleView, p: Boolean): Double = when (v.ability(p)) {
        "unburden" -> if (v.conds(p)["ab.unburden.${v.mon(p).uid}"] == 1) 2.0 else 1.0
        "shieldsdown" -> if (v.mon(p).hp <= v.maxHp(p) / 2) 1.5 else 1.0
        else -> 1.0
    }
    override fun types(v: BattleView, p: Boolean, t: List<Type>): List<Type> = when (v.ability(p)) {
        "forecast" -> listOf(when (v.weather()) { "sun" -> Type.FIRE; "rain" -> Type.WATER; "hail", "snow" -> Type.ICE; else -> Type.NORMAL })
        "multitype", "rkssystem" -> v.baseMoves(p).firstOrNull { it.damaging }?.let { listOf(it.type) } ?: t
        else -> t
    }
    override fun stats(v: BattleView, p: Boolean, s: Stats): Stats = if (v.ability(p) == "stancechange" && v.side(p).volatiles["ab.blade"] == 1) s.copy(atk = s.def, def = s.atk, spa = s.spd, spd = s.spa) else s
    override fun beforeMove(r: Round, p: Boolean, move: Move): Boolean {
        half(r, p, false)
        if (r.ability(p) == "stancechange") {
            if (move.damaging) r.side(p).volatiles["ab.blade"] = 1
            else if (move.id == "kingsshield") r.side(p).volatiles.remove("ab.blade")
        }
        return true
    }
    override fun moveType(h: Hit, type: Type): Type = if (Abilities.aAb(h) == "hungerswitch" && h.move.id == "aurawheel") {
        if ((h.aSide.volatiles["ab.hunger"] ?: 0) % 2 == 0) Type.ELECTRIC else Type.DARK
    } else type
    override fun blockStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean): String? =
        if (r.ability(tp) == "shieldsdown" && r.mon(tp).hp > r.maxHp(tp) / 2) "${r.mon(tp).name}'s Shields Down prevents status!" else null

    override fun takeDamage(r: Round, tp: Boolean, h: Hit, damage: Int): Int {
        if (damage <= 0 || !h.physical || Abilities.dAb(h) != "iceface") return damage
        refreshIce(r, tp)
        if (r.conds(tp)[iceKey(r, tp)] == 1) return damage
        r.conds(tp)[iceKey(r, tp)] = 1
        say(r, tp, "iceface", " blocked the physical hit!")
        return 0
    }
    override fun afterDamage(r: Round, tp: Boolean, h: Hit, damage: Int) {
        if (r.ability(tp) == "illusion") once(r, tp, "illusion")
        val attackerSide = r.conds(!tp)
        if (r.ability(!tp) in setOf("sniper", "superluck")) attackerSide[counter(r, !tp)] = (attackerSide[counter(r, !tp)] ?: 0) + 1
        val ab = r.ability(tp)
        if (alive(r)) when (ab) {
            "toxicdebris" -> if (h.physical) Abilities.inflict(r, tp, !tp, ab, "psn")
            "pickpocket" -> if (Abilities.contacts(r, !tp, h.move)) steal(r, tp)
        }
        offerSwitch(r, tp, damage)
        if (r.mon(!tp).hp > 0 && r.side(tp).volatiles.remove("ab.gulp") != null) {
            r.hurt(!tp, maxOf(1, r.maxHp(!tp) / 4), "Gulp Missile struck back!", EffectKind.RECOIL, label = "Gulp Missile", source = Indirect.ABILITY)
            if (r.mon(!tp).hp > 0) {
                if (r.side(tp).volatiles.remove("ab.gulpLow") == 1) Abilities.inflict(r, tp, !tp, "gulpmissile", "par")
                else r.boost(!tp, mapOf("def" to -1), byFoe = true)
            }
        }
        half(r, !tp, damage > 0)
        eat(r, tp)
    }
    private fun offerSwitch(r: Round, p: Boolean, damage: Int) {
        val ab = r.ability(p)
        if (alive(r) && ab in setOf("wimpout", "emergencyexit") && r.mon(p).hp <= r.maxHp(p) / 2 && r.mon(p).hp + damage > r.maxHp(p) / 2 && once(r, p, ab) && team(r, p).any { it.hp > 0 }) {
            r.conds(p)[SWITCH_OFFER] = 1
            say(r, p, ab, ": choose a teammate to switch in.")
        }
    }
    override fun afterIndirectDamage(r: Round, p: Boolean, damage: Int) {
        half(r, !p, false)
        offerSwitch(r, p, damage)
        if (alive(r)) eat(r, p)
    }

    private fun half(r: Round, p: Boolean, hit: Boolean) {
        if (!alive(r) || r.mon(!p).hp > r.maxHp(!p) / 2) return
        val ab = r.ability(p)
        if (ab !in setOf("beastboost", "moxie", "soulheart", "chillingneigh", "grimneigh") || (!hit && ab != "soulheart") || !once(r, p, HALF)) return
        val stat = when (ab) {
            "soulheart", "grimneigh" -> "spa"
            "beastboost" -> { val values = r.stats(p).toList().drop(1); listOf("atk", "def", "spa", "spd", "spe")[values.indexOf(values.max())] }
            else -> "atk"
        }
        say(r, p, ab); r.boost(p, mapOf(stat to 1), byFoe = false)
    }
    private fun steal(r: Round, p: Boolean) {
        if (r.mon(p).heldItem != null || Abilities.abVs(r, p) == "stickyhold") return
        val item = r.mon(!p).heldItem ?: return
        r.mon(p).heldItem = item; r.mon(!p).heldItem = null
        say(r, p, r.ability(p), " stole ${Items.name(item)}!")
        lostItem(r, !p)
    }
    override fun afterHit(r: Round, p: Boolean, move: Move, damage: Int) {
        if (damage <= 0) return
        if (alive(r) && r.ability(!p) == "angerpoint" && damage >= r.maxHp(!p) / 4 && once(r, !p, "angerpoint")) { say(r, !p, "angerpoint"); r.boost(!p, mapOf("atk" to 2), byFoe = false) }
        if (r.mon(p).hp > 0 && r.ability(p) == "magician") steal(r, p)
    }
    override fun afterMove(r: Round, p: Boolean, move: Move, failed: Boolean) {
        lostItem(r, p); lostItem(r, !p)
        completed(r, p, move)
    }
    override fun afterCalledMove(r: Round, p: Boolean, move: Move) { completed(r, p, move) }
    private fun completed(r: Round, p: Boolean, move: Move) {
        if (!alive(r)) return
        if (r.ability(p) == "gulpmissile" && move.id in setOf("surf", "dive")) {
            r.side(p).volatiles["ab.gulp"] = 1
            r.side(p).volatiles["ab.gulpLow"] = if (r.mon(p).hp <= r.maxHp(p) / 2) 1 else 0
            say(r, p, "gulpmissile", " loaded a retaliation!")
        }
        if ("dance" in move.flags && r.ability(!p) == "dancer" && r.field.player["ab.dancing"] != 1) {
            r.field.player["ab.dancing"] = 1
            try { say(r, !p, "dancer"); r.useMove(!p, move) } finally { r.field.player.remove("ab.dancing") }
        }
    }
    private fun lostItem(r: Round, p: Boolean) {
        if (r.ability(p) == "unburden" && r.conds(p)["ab.startedHeld.${r.mon(p).uid}"] == 1 && r.mon(p).heldItem == null) r.conds(p)["ab.unburden.${r.mon(p).uid}"] = 1
    }
    private fun berryEffect(r: Round, p: Boolean, item: String) {
        val boost = if (r.ability(p) == "ripen") 2 else 1
        val stat = berries[item] ?: return
        if (stat.isEmpty() && SideMoves.healBlocked(r, p)) return
        if (stat.isNotEmpty()) r.boost(p, mapOf(stat to boost), byFoe = false)
        else {
            val hp = when (item) { "oran-berry" -> 10; "sitrus-berry" -> maxOf(1, r.maxHp(p) / 4); else -> maxOf(1, r.maxHp(p) / 3) }
            r.heal(p, hp * boost, "${r.mon(p).name} ate ${Items.name(item)}!", label = Items.name(item))
        }
    }
    internal fun eat(r: Round, p: Boolean) {
        val item = r.mon(p).heldItem ?: return
        if (r.mon(p).hp <= 0 || item !in berries || r.ability(p) == "klutz" || r.ability(!p) == "unnerve" ||
            (r.side(p).volatiles["embargo"] ?: 0) > 0 || (r.field.rooms["magicroom"] ?: 0) > 0) return
        val pinch = item !in setOf("oran-berry", "sitrus-berry")
        val divisor = if (pinch && r.ability(p) != "gluttony") 4 else 2
        if (r.mon(p).hp > r.maxHp(p) / divisor) return
        r.mon(p).heldItem = null; r.side(p).notes["consumeditem"] = item
        berryEffect(r, p, item)
        if (p) r.events += GameEvent.BerryActivated(item)
        if (r.ability(p) == "cheekpouch" && !SideMoves.healBlocked(r, p)) r.heal(p, maxOf(1, r.maxHp(p) / 3), "Cheek Pouch restored HP!", label = "Cheek Pouch")
        if (r.ability(p) == "cudchew") {
            val sequence = (r.side(p).volatiles["ab.chewSequence"] ?: 0) + 1
            r.side(p).volatiles["ab.chewSequence"] = sequence
            val key = "ab.chew.$sequence"
            r.side(p).notes[key] = item; r.side(p).volatiles[key] = r.battle.rounds + if (r.freeHit) 0 else 1
        }
        lostItem(r, p)
    }
    override fun endOfRound(r: Round) {
        if (!alive(r)) return
        for (p in listOf(true, false)) {
            val s = r.side(p)
            when (val ab = r.ability(p)) {
                "healer" -> if (s.status != null && Abilities.roll(r, 30)) Abilities.cure(r, p, ab)
                "hungerswitch" -> s.volatiles["ab.hunger"] = (s.volatiles["ab.hunger"] ?: 0) + 1
                "iceface" -> refreshIce(r, p)
            }
            for ((key, due) in s.volatiles.filterKeys { it.startsWith("ab.chew.") }) {
                if (r.battle.rounds >= due) {
                    s.volatiles.remove(key)
                    val item = s.notes.remove(key)
                    if (item != null && r.ability(p) == "cudchew" && r.ability(!p) != "unnerve") berryEffect(r, p, item)
                }
            }
            half(r, p, false); eat(r, p); lostItem(r, p)
        }
    }

    fun onWin(state: GameState, rng: kotlin.random.Random, events: MutableList<GameEvent>) {
        val main = state.main ?: return
        val ab = state.battle?.player?.notes?.get(Abilities.OVERRIDE) ?: BattleView.normalizeAbility(main.ability)
        if (ab == "pickup" && rng.nextInt(100) < 10) { Economy.give(state, "potion"); events += GameEvent.Info("Pickup found a Potion!", main.uid) }
        if (ab == "honeygather" && rng.nextInt(100) < 10) { state.cash += 5; events += GameEvent.Info("Honey Gather found 5 coins!", main.uid) }
    }
}
