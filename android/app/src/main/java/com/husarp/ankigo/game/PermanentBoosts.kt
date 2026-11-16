package com.husarp.ankigo.game

/** PLAN 673: cumulative permanent bonuses; saved dates keep reached tiers after progress falls. */
object PermanentBoosts {
    private val tierBasisPoints = listOf(10, 25, 50, 75, 100)

    fun xpBasisPoints(state: GameState): Int = AchievementTable.ROWS.sumOf { row ->
        val reached = state.achTiers[row.id].orEmpty().size.coerceAtMost(row.tiers.size)
        when {
            row.ankimonBadge != null -> 0
            row.group == "Secret" -> if (reached > 0) 100 else 0
            else -> tierBasisPoints.take(reached).sum()
        }
    }

    fun moneyBasisPoints(state: GameState): Int = state.badges.size * 50

    data class Reward(val amount: Int, val carry: Int)

    /** Basis points and a saved remainder prevent tiny bonuses disappearing on small rewards. No bonus cap. */
    fun reward(base: Int, basisPoints: Int, carry: Int): Reward {
        if (base <= 0) return Reward(0, carry)
        val total = base.toLong() * (10_000L + basisPoints) + carry
        return Reward((total / 10_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), (total % 10_000).toInt())
    }

    fun percent(basisPoints: Int): String = java.math.BigDecimal.valueOf(basisPoints.toLong(), 2).stripTrailingZeros().toPlainString() + "%"
    fun xpLabel(state: GameState): String = "XP bonus +${percent(xpBasisPoints(state))}"
    fun moneyLabel(state: GameState): String = "Money bonus +${percent(moneyBasisPoints(state))}"
}
