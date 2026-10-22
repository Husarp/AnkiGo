package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameEvent

/**
 * XP card of a team member other than the main (Exp. Share holder, team share): [barFrom]/[barTo] are 0..1 fractions
 * of the level bar. [barFrom] is where the bar stood BEFORE the gain, in the OLD level ([levelFrom]); after a level-up the UI fills
 * it to the top, changes the level, and goes on from empty to [barTo] in [levelTo] (PLAN 182, the same loop as the main's bar:
 * [XpLoop]). A gain that did not record its starting XP (old-style event) falls back to [barFrom] = 0.
 */
data class XpCard(
    val uid: String, val name: String, val xp: Int, val levelFrom: Int, val levelTo: Int,
    val barFrom: Float, val barTo: Float, val levelUp: Boolean, val evolvedTo: String? = null,
)

/** Pure: the cards for one batch of events (a defeat or catch), in event order. The main's own XP is not a card. */
object TeamXpCards {
    fun of(events: List<GameEvent>, mainUid: String?): List<XpCard> {
        val cards = ArrayList<XpCard>()
        for ((i, e) in events.withIndex()) {
            if (e !is GameEvent.XpGained || e.uid == mainUid || e.xp <= 0) continue
            // the level / evolve events of this gain follow it, up to the next XpGained of the same Pokémon
            var ups = 0
            var evolved: String? = null
            for (j in i + 1 until events.size) {
                val f = events[j]
                if (f is GameEvent.XpGained && f.uid == e.uid) break
                if (f is GameEvent.LevelUp && f.uid == e.uid) ups++
                if (f is GameEvent.Evolved && f.uid == e.uid) evolved = f.to
            }
            val up = ups > 0
            val barTo = if (e.xpForLevel > 0) (e.xpInLevel.toFloat() / e.xpForLevel).coerceIn(0f, 1f) else 1f
            // PLAN 182: where the bar stood before the gain; with a level gained that is inside the OLD level's XP
            val oldNeed = if (up && e.crossedNeeds.isNotEmpty()) e.crossedNeeds[0] else e.xpForLevel
            val barFrom = when {
                e.beforeInLevel >= 0 && oldNeed > 0 -> (e.beforeInLevel.toFloat() / oldNeed).coerceIn(0f, 1f)
                up || e.xpForLevel <= 0 -> 0f
                else -> (maxOf(0, e.xpInLevel - e.xp).toFloat() / e.xpForLevel).coerceIn(0f, 1f)
            }
            cards += XpCard(e.uid, e.name, e.xp, e.level - ups, e.level, barFrom, barTo, up, evolved)
        }
        return cards
    }
}
