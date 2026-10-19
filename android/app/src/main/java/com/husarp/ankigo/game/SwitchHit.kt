package com.husarp.ankigo.game

/**
 * Round 10 (W10): the words for the free hit a switch costs, for the screens that do not show the battle (Team, Box, Compare:
 * the action's toast says it). Pure Kotlin; the hit itself is [BattleEngine.switchHit], played by [Game].
 */
object SwitchHitText {
    /**
     * The sentence for the toast, without a leading dash or a final full stop: "the wild Ratty hit it for 23 HP". [hit] = the events
     * of the free hit ([GameEvent.SwitchHit] first). [named] = the toast already names the Pokémon that came in, so it says "it".
     */
    fun tail(hit: List<GameEvent>, named: Boolean): String {
        val marker = hit.filterIsInstance<GameEvent.SwitchHit>().firstOrNull() ?: return ""
        val wild = "the wild " + marker.wildName.removePrefix("Wild ")
        val mon = if (named) "it" else marker.monName
        val attack = hit.filterIsInstance<GameEvent.Attack>().firstOrNull { !it.byPlayer }
        val hurt = hit.any { it is GameEvent.Fainted && it.player }
        val cant = hit.filterIsInstance<GameEvent.Effect>().firstOrNull { !it.onPlayer && (it.kind == EffectKind.CANT_MOVE || it.kind == EffectKind.CHARGE) }
        val what = when {
            attack == null -> if (cant?.kind == EffectKind.CHARGE) "$wild was charging up" else "$wild couldn't attack"
            attack.missed -> "$wild attacked but missed"
            attack.category == Category.STATUS -> "$wild used ${attack.move}" + if (attack.failed) ", but it failed" else ""
            attack.failed -> "$wild's ${attack.move} failed"
            attack.effectiveness == 0.0 -> "$wild's ${attack.move} had no effect on $mon"
            else -> "$wild hit $mon for ${attack.damage} HP"
        }
        val sb = StringBuilder(what)
        if (hurt) sb.append(" and ").append(if (named) "it" else marker.monName).append(" fainted. It is back at full HP, but the wild one is lost")
        else if (hit.any { it is GameEvent.Fainted && !it.player }) sb.append(". The wild one fainted too")
        return sb.toString()
    }

    /** [message] (the action's own, e.g. "Charizard is your main now.") with the hit added after a dash: "… — the wild Ratty hit it for 23 HP." */
    fun withHit(message: String, hit: List<GameEvent>, named: Boolean): String {
        val tail = tail(hit, named)
        return if (tail.isEmpty()) message else message.trimEnd().trimEnd('.') + " — " + tail + "."
    }

    /** [message] with the ending of a fainted Pokémon coming in during a fight: "… — it had fainted: it is back at full HP, but the wild one is lost." */
    fun withFaintedIn(message: String): String =
        message.trimEnd().trimEnd('.') + " — it had fainted: it is back at full HP, but the wild one is lost."
}

/**
 * "Make main" / "Set as main" stay silent when the change is visible on screen (no toast, as before); when the switch cost a hit the
 * toast says so ([SwitchHitText]). A failed result is returned as it is.
 */
fun ActionResult.quietUnlessHit(): ActionResult =
    if (ok && events.none { it is GameEvent.SwitchHit || it is GameEvent.MainHealed }) copy(message = "") else this
