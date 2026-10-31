package com.husarp.ankigo.game

/** Moves from the R22 audit that can use the existing single-battle model unchanged. */
internal object RemainingMoves : BattleRules {
    const val ESCAPED = "moveescape"
    val texts = mapOf(
        "teleport" to "Ends this wild battle by escaping.",
        "roar" to "Ends this wild battle by forcing the target out. Fails against Ingrain or Suction Cups.",
        "whirlwind" to "Ends this wild battle by forcing the target out. Fails against Ingrain or Suction Cups.",
        "assist" to "Uses a random callable move known by another member of its team.",
        "instruct" to "Makes the target repeat its last callable move immediately.",
        "revivalblessing" to "Revives the first fainted teammate with half its max HP.",
        "trick" to "Swaps the two Pokémon's held items. Sticky Hold protects the target's item.",
        "switcheroo" to "Swaps the two Pokémon's held items. Sticky Hold protects the target's item.",
        "bestow" to "Gives its held item to the target, if the target holds nothing.",
        "recycle" to "Restores the last item it used up in this fight, if it holds nothing.",
        "corrosivegas" to "Destroys the target's held item. Sticky Hold protects it.",
        "fling" to "Throws away its held item to deal damage. Power depends on the item. Fails without an active held item.",
        "healpulse" to "Heals the target by half its max HP, or three quarters with Mega Launcher.",
        "floralhealing" to "Heals the target by half its max HP, or two thirds in Grassy Terrain.",
        "purify" to "Cures the target's status, then heals itself by half its max HP.",
        "memento" to "Lowers the target's Attack and Sp. Atk by 2, then the user faints.",
        "gearup" to "Raises its Attack and Sp. Atk by 1 if its ability is Plus or Minus.",
        "magneticflux" to "Raises its Defense and Sp. Def by 1 if its ability is Plus or Minus.",
        "happyhour" to "Doubles the money earned for the rest of this fight. Using it again does not stack.",
    )
    override val handles = texts.keys

    private fun active(v: BattleView, p: Boolean): String? = v.mon(p).heldItem.takeUnless {
        v.ability(p) == "klutz" || (v.side(p).volatiles["embargo"] ?: 0) > 0 || (v.field.rooms["magicroom"] ?: 0) > 0
    }
    private fun sticky(v: BattleView, p: Boolean) = v.mon(p).heldItem != null && Abilities.abVs(v, !p) == "stickyhold"
    private fun flingPower(item: String): Int = when {
        item == "macho-brace" -> 60
        item.startsWith("power-") -> 70
        item == "soothe-bell" || item == "luck-incense" -> 10
        else -> 30 // all remaining held items in AnkiGo's catalogue
    }
    override fun basePower(h: Hit): Int? = if (h.move.id == "fling") active(h.view, h.attackerIsMain)?.let(::flingPower) ?: 0 else null
    private fun resistsForcedEscape(v: BattleView, p: Boolean): Boolean =
        (v.side(!p).volatiles["ingrain"] ?: 0) > 0 || Abilities.abVs(v, p) == "suctioncups"

    override fun failReason(r: Round, p: Boolean, move: Move): String? = when {
        move.id == "fling" && active(r, p) == null -> "But it has no usable held item!"
        move.id in setOf("roar", "whirlwind") && resistsForcedEscape(r, p) -> "${r.mon(!p).name} can't be forced out!"
        else -> null
    }

    override fun afterHit(r: Round, p: Boolean, move: Move, damage: Int) {
        if (move.id == "fling") {
            r.mon(p).heldItem?.let { r.side(p).notes["consumeditem"] = it }
            r.mon(p).heldItem = null
            r.fx(p, "${r.mon(p).name} threw away its held item!")
        }
    }

    override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? {
        val me = r.mon(p)
        val foe = r.mon(!p)
        when (move.id) {
            "teleport", "roar", "whirlwind" -> r.escapeWild()
            "assist" -> {
                val pool = assistPool(r, p)
                if (pool.isEmpty()) return false
                return r.useMove(p, pool[r.rng.nextInt(pool.size)])
            }
            "instruct" -> {
                val called = instructed(r, !p) ?: return false
                return r.useMove(!p, called)
            }
            "revivalblessing" -> {
                val teammate = faintedTeammate(r, p) ?: return false
                teammate.hp = maxOf(1, StatCalc.maxHp(r.data, teammate) / 2)
                r.fx(p, "${teammate.name} was revived with half its max HP!")
            }
            "trick", "switcheroo" -> {
                if (sticky(r, !p) || (me.heldItem == null && foe.heldItem == null)) return false
                val mine = me.heldItem
                me.heldItem = foe.heldItem
                foe.heldItem = mine
                r.fx(p, "${me.name} swapped held items with ${foe.name}!")
            }
            "bestow" -> {
                val item = me.heldItem ?: return false
                if (foe.heldItem != null) return false
                me.heldItem = null; foe.heldItem = item
                r.fx(!p, "${me.name} gave its ${Items.name(item)} to ${foe.name}!")
            }
            "recycle" -> {
                if (me.heldItem != null) return false
                me.heldItem = r.side(p).notes.remove("consumeditem") ?: return false
                r.fx(p, "${me.name} restored its ${Items.name(me.heldItem!!)}!")
            }
            "corrosivegas" -> {
                if (foe.heldItem == null || sticky(r, !p)) return false
                foe.heldItem = null
                r.fx(!p, "${foe.name}'s held item corroded away!")
            }
            "healpulse", "floralhealing" -> {
                if (foe.hp >= r.maxHp(!p) || MoveControl.healBlocked(r, !p)) return false
                val amount = when {
                    move.id == "healpulse" && r.ability(p) == "megalauncher" -> r.maxHp(!p) * 3 / 4
                    move.id == "floralhealing" && r.terrain() == "grassy" -> r.maxHp(!p) * 2 / 3
                    else -> r.maxHp(!p) / 2
                }
                r.heal(!p, maxOf(1, amount), "${foe.name} regained health!")
            }
            "purify" -> {
                val side = r.side(!p)
                val status = side.status ?: return false
                side.status = null; side.statusTurns = 0; side.volatiles.remove(CoreRules.SLEEP_TURNS)
                r.fx(!p, "${foe.name}'s status was cured!", EffectKind.STATUS_CURED, status)
                if (!MoveControl.healBlocked(r, p)) r.heal(p, maxOf(1, r.maxHp(p) / 2), "${me.name} regained health!")
            }
            "memento" -> {
                r.boost(!p, mapOf("atk" to -2, "spa" to -2), byFoe = true)
                r.hurt(p, me.hp, "${me.name} fainted after Memento!")
            }
            "gearup", "magneticflux" -> {
                if (r.ability(p) !in setOf("plus", "minus")) return false
                return r.boost(p, if (move.id == "gearup") mapOf("atk" to 1, "spa" to 1) else mapOf("def" to 1, "spd" to 1), byFoe = false)
            }
            "happyhour" -> {
                if (r.field.player["happyhour"] == 1) return false
                r.field.player["happyhour"] = 1
                r.fx(p, "Money earned for the rest of this fight is doubled!")
            }
            else -> return null
        }
        return true
    }

    private fun assistPool(v: BattleView, p: Boolean): List<Move> =
        if (!p) emptyList() else v.battle?.team.orEmpty().filter { it.uid != v.mon(p).uid }
            .flatMap { it.moves }.mapNotNull { v.data.move(it) }
            .filter { MoveControl.callable(it) && "noassist" !in it.flags }
    private fun instructed(v: BattleView, p: Boolean): Move? =
        v.side(p).lastMove?.let { v.data.move(it) }?.takeIf {
            MoveControl.callable(it) && "failinstruct" !in it.flags && v.side(p).charging == null &&
                CoreRules.MUSTRECHARGE !in v.side(p).volatiles
        }
    private fun faintedTeammate(v: BattleView, p: Boolean): Mon? =
        if (!p) null else v.battle?.team?.firstOrNull { it.uid != v.mon(p).uid && it.hp <= 0 }

    override fun useful(v: BattleView, p: Boolean, move: Move): Boolean? = when (move.id) {
        "teleport" -> true
        "roar", "whirlwind" -> !resistsForcedEscape(v, p)
        "assist" -> assistPool(v, p).isNotEmpty()
        "instruct" -> instructed(v, !p) != null
        "revivalblessing" -> faintedTeammate(v, p) != null
        "fling" -> active(v, p) != null
        "trick", "switcheroo" -> !sticky(v, !p) && (v.mon(p).heldItem != null || v.mon(!p).heldItem != null)
        "bestow" -> v.mon(p).heldItem != null && v.mon(!p).heldItem == null
        "recycle" -> v.mon(p).heldItem == null && "consumeditem" in v.side(p).notes
        "corrosivegas" -> v.mon(!p).heldItem != null && !sticky(v, !p)
        "healpulse", "floralhealing" -> v.mon(!p).hp < v.maxHp(!p) && !MoveControl.healBlocked(v, !p)
        "purify" -> v.side(!p).status != null
        "gearup", "magneticflux" -> v.ability(p) in setOf("plus", "minus") &&
            (if (move.id == "gearup") listOf(0, 2) else listOf(1, 3)).any { v.side(p).boosts[it] < 6 }
        "happyhour" -> v.field.player["happyhour"] != 1
        else -> null
    }
    override fun describe(m: Move): List<String> = texts[m.id]?.let(::listOf) ?: emptyList()
}
