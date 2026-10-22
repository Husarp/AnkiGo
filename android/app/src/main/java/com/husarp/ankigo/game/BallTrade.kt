package com.husarp.ankigo.game

/**
 * PLAN 199 (R14 batch 2): trading balls up, from a ball's card in the Bag (never in the Shop). 5 Poké Balls make 1 Great Ball, 3 Great
 * Balls make 1 Ultra Ball; on purpose worse than buying (5 Poké = 1,000 ₽ in the Shop for a 700 ₽ Great Ball). Ankimon's old balls
 * ([Tuning.OLD_BALLS], not sold or found any more) count as Great Balls and are used up first (the one you have fewest of first, then by
 * name), then the Great Balls. The Ultra and the Master Ball cannot be traded. One call = one batch. Pure (no android.*).
 */
object BallTrade {
    /** Poké Balls for one Great Ball. */
    const val POKE_FOR_GREAT = 5
    /** Great Balls (old Ankimon balls count as Great) for one Ultra Ball. */
    const val GREAT_FOR_ULTRA = 3

    private const val POKE = "poke-ball"
    private const val GREAT = "great-ball"
    private const val ULTRA = "ultra-ball"

    /**
     * What a trade from the card of [from] would give and take. [need] balls go for one [give]; [have] is how many the player has of the
     * kind that counts; [take] is exactly what would go (empty while [have] is under [need]).
     */
    data class Offer(val from: String, val give: String, val need: Int, val have: Int, val take: List<Pair<String, Int>>) {
        val canTrade: Boolean get() = have >= need
        /** True when an old Ankimon ball is in [take]. */
        val usesOldBalls: Boolean get() = take.any { it.first in Tuning.OLD_BALLS }
    }

    /** The ball kind a trade from the card of [ball] gives, or null (Ultra, Master, anything that is not a ball). */
    fun target(ball: String): String? = when {
        ball == POKE -> GREAT
        ball == GREAT || ball in Tuning.OLD_BALLS -> ULTRA
        else -> null
    }

    /** The offer from the card of [ball], or null when that card has none (Ultra, Master, not a ball). Too few balls = an offer with [Offer.canTrade] false. */
    fun offer(state: GameState, ball: String): Offer? {
        val give = target(ball) ?: return null
        if (ball == POKE) {
            val have = state.count(POKE)
            return Offer(ball, give, POKE_FOR_GREAT, have, if (have >= POKE_FOR_GREAT) listOf(POKE to POKE_FOR_GREAT) else emptyList())
        }
        val old = Tuning.OLD_BALLS.filter { state.count(it) > 0 }.sortedWith(compareBy({ state.count(it) }, { it }))
        val have = state.count(GREAT) + old.sumOf { state.count(it) }
        if (have < GREAT_FOR_ULTRA) return Offer(ball, give, GREAT_FOR_ULTRA, have, emptyList())
        val take = ArrayList<Pair<String, Int>>()
        var left = GREAT_FOR_ULTRA
        for (id in old + GREAT) {
            if (left == 0) break
            val n = minOf(left, state.count(id))
            if (n > 0) { take += id to n; left -= n }
        }
        return Offer(ball, give, GREAT_FOR_ULTRA, have, take)
    }

    /**
     * One batch: takes the balls of [offer] and gives the better one. Atomic: when the offer does not exist or the counts are too low
     * nothing changes; a count never goes under zero (a count that reaches zero leaves the bag).
     */
    fun trade(state: GameState, ball: String): ActionResult {
        val offer = offer(state, ball) ?: return ActionResult(false, "${Items.name(ball)} cannot be traded up")
        if (!offer.canTrade) return ActionResult(false, "Need ${offer.need}, you have ${offer.have}")
        if (offer.take.any { (id, n) -> state.count(id) < n }) return ActionResult(false, "Not enough balls")
        for ((id, n) in offer.take) {
            val left = state.count(id) - n
            if (left > 0) state.bag[id] = left else state.bag.remove(id)
        }
        Economy.give(state, offer.give)
        return ActionResult(true, "Traded ${list(offer.take)} for 1 ${Items.name(offer.give)}")
    }

    /** "5 Poké Balls", "1 Net Ball and 2 Great Balls", "1 Dive Ball, 1 Net Ball and 1 Great Ball" (natural plurals). */
    fun list(parts: List<Pair<String, Int>>): String {
        val named = parts.map { (id, n) -> "$n ${ItemInfo.plural(Items.name(id), n)}" }
        return if (named.size <= 1) named.joinToString("") else named.dropLast(1).joinToString(", ") + " and " + named.last()
    }

    /** What the player has left of [ball] after [offer] went (the "You keep ..." number). */
    fun keep(state: GameState, offer: Offer): Int = state.count(offer.from) - offer.take.filter { it.first == offer.from }.sumOf { it.second }

    /** The ₽ the shop would ask for [parts] (Poké / Great / Ultra at [Tuning.BALL_PRICE]); null when an old ball is in it (not sold). */
    fun worth(parts: List<Pair<String, Int>>): Int? {
        var sum = 0
        for ((id, n) in parts) sum += (Tuning.BALL_PRICE[id]?.takeIf { it > 0 } ?: return null) * n
        return sum
    }
}
