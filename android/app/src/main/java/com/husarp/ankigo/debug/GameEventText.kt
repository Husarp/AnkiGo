package com.husarp.ankigo.debug

import com.husarp.ankigo.game.GameEvent

/** Only structured game facts. Never include card HTML, nicknames, trainer names, free-form messages or exceptions. */
object GameEventText {
    fun answer(source: String, cardId: Long, button: Int) =
        "Answer source=${if (source in setOf("live", "study", "catch-up")) source else "unknown"} card=$cardId button=$button"

    fun event(e: GameEvent): String = when (e) {
        is GameEvent.Counted -> "Counted button=${e.button} points=${e.points} today=${e.reviewsToday} catchUp=${e.catchUp}"
        is GameEvent.Attack -> "Attack player=${e.byPlayer} damage=${e.damage} hits=${e.hits} targetHp=${e.targetHp}/${e.targetMaxHp} userHp=${e.userHp}/${e.userMaxHp} critical=${e.crit} missed=${e.missed} failed=${e.failed} effectiveness=${e.effectiveness}"
        is GameEvent.Encounter -> "Encounter species=${e.species} level=${e.level} shiny=${e.shiny} hp=${e.hp}/${e.maxHp} catch=${e.willCatch} new=${e.newSpecies}"
        is GameEvent.BallThrow -> "BallThrow ball=${com.husarp.ankigo.game.Items.def(e.ball)?.id ?: "unknown"} chance=${e.chance} success=${e.success} left=${e.left}"
        is GameEvent.Caught -> "Caught species=${e.species} level=${e.level} shiny=${e.shiny} new=${e.newSpecies} team=${e.toTeam}"
        is GameEvent.Defeated -> "Defeated species=${e.species} xp=${e.xp}"
        is GameEvent.XpGained -> "XpGained uid=${id(e.uid)} xp=${e.xp} level=${e.level} progress=${e.xpInLevel}/${e.xpForLevel}"
        is GameEvent.LevelUp -> "LevelUp uid=${id(e.uid)} level=${e.level} hp=${e.hp}/${e.maxHp}"
        is GameEvent.Evolved -> "Evolved uid=${id(e.uid)} species=${e.toSpecies}"
        is GameEvent.Effect -> "Effect player=${e.onPlayer} hp=${e.hp}/${e.maxHp}"
        is GameEvent.Charge -> "Charge points=${e.points} answers=${e.answers}/${e.of}"
        is GameEvent.RoundStart -> "RoundStart multiplier=${e.multiplier} points=${e.points} enemyActs=${e.enemyActs}"
        is GameEvent.Cash -> "Cash amount=${e.amount} total=${e.total} kind=${e.kind}"
        is GameEvent.ItemFound -> "ItemFound item=${com.husarp.ankigo.game.Items.def(e.item)?.id ?: "unknown"} count=${e.count}"
        is GameEvent.BadgeEarned -> "BadgeEarned id=${e.id}"
        is GameEvent.TrainerLevelUp -> "TrainerLevelUp level=${e.level}"
        is GameEvent.Fainted -> "Fainted player=${e.player}"
        is GameEvent.MainHealed -> "MainHealed uid=${id(e.uid)} hp=${e.hp}"
        is GameEvent.Healed -> "Healed uid=${id(e.uid)} amount=${e.amount} hp=${e.hp}/${e.maxHp} reason=${e.reason}"
        is GameEvent.MainChanged -> "MainChanged uid=${id(e.uid)}"
        else -> e.javaClass.simpleName // Info, undo reasons, move names etc deliberately omit their text
    }

    fun error(tag: String, type: String) = "Error tag=${safe(tag)} type=${safe(type)}"
    private fun safe(value: String) = value.filter { it.isLetterOrDigit() || it == '_' }.take(48)
    private fun id(uid: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(uid.toByteArray(Charsets.UTF_8)).take(6).joinToString("") { "%02x".format(it.toInt() and 255) }
}
