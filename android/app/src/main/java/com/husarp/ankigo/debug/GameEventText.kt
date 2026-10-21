package com.husarp.ankigo.debug

import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Items

/**
 * R13 Part E: every game event as one short readable line for the debug log, with names (Pokémon, moves, items, the event's own
 * text), so a report says what happened. Never card text: no game event carries any (the Battle log's card fronts are not logged).
 * Kept short on purpose: the whole log is ~1 MB, and a day of study writes thousands of these.
 */
object GameEventText {
    fun answer(source: String, cardId: Long, button: Int) =
        "Answer source=${if (source in setOf("live", "study", "catch-up")) source else "unknown"} card=$cardId button=$button"

    fun event(e: GameEvent): String = when (e) {
        is GameEvent.Counted -> "Counted button=${e.button} points=${e.points} today=${e.reviewsToday} streak=${e.cardStreak}" + flag(e.catchUp, "catch-up")
        is GameEvent.Ignored -> "Ignored: ${e.reason}"
        is GameEvent.Charge -> "Charge points=${e.points} answers=${e.answers}/${e.of}"
        is GameEvent.Cash -> "Cash +${e.amount} total=${e.total} ${e.kind}"
        is GameEvent.ItemFound -> "ItemFound ${Items.name(e.item)} (now ${e.count})"
        is GameEvent.BadgeEarned -> "BadgeEarned #${e.id} ${e.title}"
        is GameEvent.AchievementReached -> "AchievementReached ${e.id.filter { it.isLetterOrDigit() || it == '-' }.take(48)} tier=${e.tier} ${e.title} ${e.tierName}"
        is GameEvent.TrainerLevelUp -> "TrainerLevelUp level=${e.level}"
        is GameEvent.DayChanged -> "DayChanged ${e.day}"
        is GameEvent.Encounter -> "Encounter ${e.name} #${e.species} Lv ${e.level} ${e.tier} hp=${e.hp}/${e.maxHp}" +
            flag(e.shiny, "shiny") + flag(e.newSpecies, "new") + (if (e.willCatch) " will-catch" else " will-defeat")
        is GameEvent.RoundStart -> "RoundStart x${e.multiplier} points=${e.points} main=${e.mainActs} wild=${e.enemyActs} wildPower=${e.enemyPower}"
        is GameEvent.Attack -> "Attack ${if (e.byPlayer) "mine" else "wild"} ${e.user} -> ${e.target}: ${e.move} ${e.moveType ?: "?"}/${e.category} dmg=${e.damage}" +
            (if (e.hits > 1) " hits=${e.hits}" else "") + " eff=${e.effectiveness} target=${e.targetHp}/${e.targetMaxHp} user=${e.userHp}/${e.userMaxHp}" +
            flag(e.crit, "crit") + flag(e.missed, "missed") + flag(e.failed, "failed")
        is GameEvent.Splash -> "Splash ${e.name}"
        is GameEvent.Effect -> "Effect on ${if (e.onPlayer) "mine" else "wild"} ${e.kind}: ${e.text} hp=${e.hp}/${e.maxHp}" + (e.status?.let { " status=$it" } ?: "")
        is GameEvent.Fainted -> "Fainted ${if (e.player) "mine" else "wild"} ${e.name}"
        is GameEvent.BallThrow -> "BallThrow ${Items.name(e.ball)} chance=${e.chance}% ${if (e.success) "caught" else "missed"} left=${e.left} throw=${e.number}"
        is GameEvent.Caught -> "Caught ${e.name} #${e.species} Lv ${e.level} ball=${e.ball?.let(Items::name) ?: "auto"}" +
            flag(e.shiny, "shiny") + flag(e.newSpecies, "new") + (if (e.toTeam) " to-team" else " to-box")
        is GameEvent.Defeated -> "Defeated ${e.name} #${e.species} xp=${e.xp}"
        is GameEvent.Duplicate -> "Duplicate ${e.name} Lv ${e.level} (own Lv ${e.ownedLevel}) ${e.result}" +
            (e.releasedName?.let { " released=$it" } ?: "") + (e.keptBecause?.let { " kept: $it" } ?: "")
        is GameEvent.XpGained -> "XpGained ${e.name} +${e.xp} Lv ${e.level} ${e.xpInLevel}/${e.xpForLevel}"
        is GameEvent.LevelUp -> "LevelUp ${e.name} -> Lv ${e.level} hp=${e.hp}/${e.maxHp}"
        is GameEvent.MoveLearned -> "MoveLearned ${e.name}: ${e.move}" + (e.forgot?.let { " (forgot $it)" } ?: "")
        is GameEvent.MoveSkipped -> "MoveSkipped ${e.name}: ${e.move}"
        is GameEvent.Evolved -> "Evolved ${e.from} -> ${e.to} #${e.toSpecies}"
        is GameEvent.MainHealed -> "MainHealed ${e.name} hp=${e.hp}"
        is GameEvent.Healed -> "Healed ${e.name} +${e.amount} hp=${e.hp}/${e.maxHp} ${e.reason}"
        is GameEvent.Fled -> "Fled ${e.name}"
        is GameEvent.MainChanged -> "MainChanged -> ${e.name}"
        is GameEvent.SwitchHit -> "TurnHit ${e.item?.let { "item ${Items.name(it)} on" } ?: "switch to"} ${e.monName} hp=${e.hp}/${e.maxHp}; ${e.wildName} attacks once"
        is GameEvent.Info -> "Info: ${e.text}"
    }.replace('\n', ' ').take(400)

    fun error(tag: String, type: String) = "Error tag=${safe(tag)} type=${safe(type)}"
    private fun safe(value: String) = value.filter { it.isLetterOrDigit() || it == '_' }.take(48)
    private fun flag(on: Boolean, word: String) = if (on) " $word" else ""
}
