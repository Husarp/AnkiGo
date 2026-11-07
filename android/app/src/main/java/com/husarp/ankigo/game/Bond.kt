package com.husarp.ankigo.game

/** Earned with the active fighter. No clock or background decay; thresholds are total points. */
object Bond {
    val thresholds: List<Int> = listOf(0, 50, 125, 250, 500, 800)
    const val MAX_POINTS = 800
    const val CARD_POINTS = 1
    const val WIN_POINTS = 3

    fun level(points: Int): Int = thresholds.indexOfLast { points >= it }.coerceAtLeast(0)
    fun requiredLevel(e: Evolution): Int = if (e.kind != EvoKind.FRIENDSHIP) 2
        else if ((e.minFriendship ?: Tuning.DEFAULT_MIN_FRIENDSHIP) <= 160) 3 else 4
    fun allows(mon: Mon, e: Evolution): Boolean = level(mon.bondPoints) >= requiredLevel(e)
    fun xpPercent(level: Int): Int = level.coerceIn(0, 5) * 5
    fun bonusXp(xp: Int, points: Int): Int = (xp.toLong() * (100 + xpPercent(level(points))) / 100).toInt()
    fun coins(level: Int): Int = level.coerceIn(0, 5)
    fun toughChance(points: Int): Int = when (level(points)) { 4 -> 5; 5 -> 10; else -> 0 }
    fun toughRoll(points: Int, roll: Int): Boolean = roll in 0 until toughChance(points)

    /** Keep the legacy value itself in Mon for Ankimon/Return. Old max (including oversized saves) is exactly ♥4. */
    fun fromLegacy(friendship: Int): Int = friendship.coerceIn(0, 250) * 2

    /** Soothe Bell retains its ×1.5 effect; half points carry between answers/wins and through saves. */
    fun add(mon: Mon, points: Int, events: MutableList<GameEvent>, sootheBell: Boolean = false, studyAnswer: Boolean = false) {
        if (points <= 0 || mon.bondPoints >= MAX_POINTS) return
        val before = level(mon.bondPoints)
        val halves = points * (if (sootheBell) 3 else 2) + mon.bondHalf
        val bonus = if (studyAnswer && mon.nature == Nature.DOCILE) {
            val hundredths = points * 4 + mon.natureBondCarry
            mon.natureBondCarry = hundredths % 100
            hundredths / 100
        } else 0
        mon.bondPoints = (mon.bondPoints + halves / 2 + bonus).coerceAtMost(MAX_POINTS)
        mon.bondHalf = if (mon.bondPoints == MAX_POINTS) 0 else halves % 2
        if (mon.bondPoints == MAX_POINTS) mon.natureBondCarry = 0
        val after = level(mon.bondPoints)
        if (after > before) events += GameEvent.Info("Bond grew! ${mon.shownName} reached ♥$after. ${unlock(after)}")
    }

    fun unlock(level: Int): String = when (level) {
        1 -> "+1 coin per win · +5% XP"
        2 -> "Normal evolutions, with their usual requirement"
        3 -> "First Bond evolutions"
        4 -> "Higher Bond evolutions · 5% chance to survive a KO with 1 HP"
        5 -> "10% chance to survive a KO with 1 HP"
        else -> "Start"
    }
}

/** After Sturdy/Endure/Substitute: only the owned fighter can tough out a lethal attack, including a switch hit. */
internal object BondRules : BattleRules {
    override fun takeDamage(r: Round, tp: Boolean, h: Hit, damage: Int): Int {
        val mon = r.mon(tp)
        if (tp && mon.hp > 0 && damage >= mon.hp && Bond.toughChance(mon.bondPoints) > 0 &&
            Bond.toughRoll(mon.bondPoints, r.rng.nextInt(100))) {
            r.fx(tp, "${mon.shownName} toughed it out thanks to your Bond!", EffectKind.BLOCKED, label = "Bond · 1 HP", hp = 1)
            return mon.hp - 1
        }
        return damage
    }
}
