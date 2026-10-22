package com.husarp.ankigo.ui

import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.DuplicateResult
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tuning
import java.util.Locale

// TICKET R2-07: the Battle log. Pure Kotlin: game events (+ the answers that caused them) -> battles -> rounds.
// Unlike the diagnostics log (debug/GameEventText: ids only, privacy-stripped) this keeps the readable details
// (names, move names, the card's front); it lives in its own small file (BattleLog) and is never shared.

enum class Tone { PLAIN, GOOD, BAD, SUB }

/** One line of a round or of a result. */
class LogLine(val tone: Tone, val text: String)

/** One counted answer. [card] is the short front text or "card #id"; [coins] are what this answer earned (the defeat money joins the last answer). */
class LogAnswer(val button: Int, val card: String, val catchUp: Boolean, var coins: Int = 0)

enum class RoundKind { ROUND, BALL }

/** [multiplier] is null while the round is still collecting answers. */
class LogRound(val kind: RoundKind, var multiplier: Double? = null, var points: Int = 0) {
    val answers = ArrayList<LogAnswer>()
    val lines = ArrayList<LogLine>()
}

enum class Outcome(val label: String) {
    OPEN("Now"), DEFEATED("Defeated"), CAUGHT("Caught"), FLED("Fled"), LOST("Lost"), ENDED("Ended")
}

class LogBattle(val startedAt: Long, var wild: String?, var level: Int, var shiny: Boolean, var outcome: Outcome = Outcome.OPEN) {
    val rounds = ArrayList<LogRound>()
    val result = ArrayList<LogLine>()

    /** Coins earned by the answers and the defeat of this battle (not the daily-goal bonus). */
    val coins: Int get() = rounds.sumOf { r -> r.answers.sumOf { it.coins } }

    fun copy(): LogBattle = LogBattle(startedAt, wild, level, shiny, outcome).also { b ->
        for (r in rounds) {
            val c = LogRound(r.kind, r.multiplier, r.points)
            r.answers.mapTo(c.answers) { LogAnswer(it.button, it.card, it.catchUp, it.coins) }
            r.lines.mapTo(c.lines) { LogLine(it.tone, it.text) }
            b.rounds += c
        }
        result.mapTo(b.result) { LogLine(it.tone, it.text) }
    }
}

/**
 * Feed it [answer] (when the answering code logs an answer) and [feed] (every game event, in order). [battles] are
 * oldest first; the newest may still be [Outcome.OPEN]. At most [keep] finished battles are kept.
 */
class BattleLogBuilder(
    private val keep: Int = KEEP,
    private val now: () -> Long = System::currentTimeMillis,
    val battles: MutableList<LogBattle> = ArrayList(),
) {
    private class Pending(val button: Int, val card: String, val catchUp: Boolean)

    private val pending = ArrayDeque<Pending>()
    private var lastAnswer: LogAnswer? = null
    private var defeatedWith: LogAnswer? = null // the answer whose round defeated the wild one: the defeat money comes after the result
    private var wildHp: Int? = null
    private var playerHp: Int? = null
    private var resultOpen = false
    private var wildPower = 1.0 // R10-B22: the wild one's damage factor of the round being played

    fun answer(source: String, cardId: Long, button: Int, front: String?) {
        pending.addLast(Pending(button, if (front.isNullOrBlank()) "card #$cardId" else front, source == "catch-up"))
        while (pending.size > MAX_PENDING) pending.removeFirst()
    }

    fun newestFirst(): List<LogBattle> = battles.asReversed().toList()

    fun feed(e: GameEvent) {
        when (e) {
            is GameEvent.Counted -> counted(e)
            is GameEvent.Ignored -> pending.removeFirstOrNull()
            is GameEvent.Cash -> when (e.kind) {
                CashKind.ANSWER -> lastAnswer?.let { it.coins += e.amount }
                CashKind.DEFEAT -> (lastAnswer ?: defeatedWith)?.let { it.coins += e.amount }
                CashKind.DAILY_BONUS -> {} // no fight money: the panel log has its own line
            }
            is GameEvent.Encounter -> encounter(e)
            is GameEvent.RoundStart -> { collecting().let { it.multiplier = e.multiplier; it.points = e.points }; wildPower = e.enemyPower }
            is GameEvent.Attack -> attack(e)
            is GameEvent.Splash -> moves().lines += LogLine(Tone.SUB, "${e.name} did nothing (Splash)")
            is GameEvent.SwitchHit -> {
                wildPower = Tuning.SWITCH_HIT_POWER // R10 W10: the turn a switch takes is at normal power, whatever the round before had
                val why = e.item?.let { "Used ${com.husarp.ankigo.game.Items.name(it)} on ${e.monName}" } ?: "Switched to ${e.monName}"   // R13 Part C: or an item
                moves().lines += LogLine(Tone.PLAIN, "$why — the wild ${e.wildName.removePrefix("Wild ")} attacks once!")
            }
            is GameEvent.Effect -> effect(e)
            is GameEvent.Fainted -> moves().lines += LogLine(if (e.player) Tone.BAD else Tone.GOOD, "${e.name} fainted")
            is GameEvent.BallThrow -> ball(e)
            is GameEvent.Defeated -> finish(Outcome.DEFEATED, LogLine(Tone.GOOD, "${e.name} was defeated · ${e.xp} XP"))
            is GameEvent.Caught -> finish(Outcome.CAUGHT, LogLine(Tone.GOOD, caughtText(e)))
            is GameEvent.Fled -> finish(Outcome.FLED, LogLine(Tone.SUB, "${e.name} left"))
            is GameEvent.Duplicate -> resultLine(Tone.PLAIN, duplicateText(e))
            is GameEvent.MainHealed -> finish(Outcome.LOST, LogLine(Tone.BAD, "${e.name} was healed to ${e.hp} HP · the wild one is lost"))
            is GameEvent.XpGained -> resultLine(Tone.PLAIN, "${e.name} +${e.xp} XP · Lv ${e.level}")
            is GameEvent.LevelUp -> resultLine(Tone.GOOD, "${e.name} grew to Lv ${e.level}")
            is GameEvent.Healed -> resultLine(Tone.PLAIN, "${e.name} +${e.amount} HP")
            is GameEvent.Evolved -> resultLine(Tone.GOOD, "${e.from} evolved into ${e.to}")
            is GameEvent.MoveLearned -> resultLine(Tone.PLAIN, "${e.name} learned ${e.move}" + (e.forgot?.let { " (forgot $it)" } ?: ""))
            else -> {}
        }
    }

    /** One action's events. Result lines (XP, level-ups) only join the fight that this same batch ended. */
    fun feed(events: List<GameEvent>) { resultOpen = false; events.forEach(::feed) }

    // ---- pieces ----

    private fun counted(e: GameEvent.Counted) {
        val p = takePending(e.button)
        val a = LogAnswer(if (e.neutral) OK else e.button, p?.card ?: "a card", e.catchUp || p?.catchUp == true)   // PLAN 155: the OK of a new card is its own row, not an "Again"
        lastAnswer = a
        defeatedWith = null
        collecting().answers += a
    }

    private fun takePending(button: Int): Pending? {
        val first = pending.removeFirstOrNull() ?: return null
        if (first.button == button) return first
        val at = pending.indexOfFirst { it.button == button }
        if (at < 0) return first // the button text differs (clamped): keep the order
        pending.addFirst(first)
        return pending.removeAt(at + 1) // `at` was counted without `first`
    }

    private fun encounter(e: GameEvent.Encounter) {
        val cur = open()
        if (cur != null && cur.wild == null) { // answers came before the first encounter
            cur.wild = e.name; cur.level = e.level; cur.shiny = e.shiny
        } else {
            if (cur != null) cur.outcome = Outcome.ENDED
            battles += LogBattle(now(), e.name, e.level, e.shiny)
        }
        wildHp = e.hp; playerHp = null
        while (battles.count { it.outcome != Outcome.OPEN } > keep) battles.removeAt(0)
    }

    private fun open(): LogBattle? = battles.lastOrNull()?.takeIf { it.outcome == Outcome.OPEN }

    private fun battle(): LogBattle = open() ?: LogBattle(now(), null, 0, false).also { battles += it }

    /** The round that is still collecting answers (no multiplier yet), or a new one. */
    private fun collecting(): LogRound {
        val b = battle()
        val last = b.rounds.lastOrNull()
        if (last != null && last.kind == RoundKind.ROUND && last.multiplier == null) return last
        return LogRound(RoundKind.ROUND).also { b.rounds += it; trim(b) }
    }

    /** The round the moves belong to: the one that just started (its multiplier is set). */
    private fun moves(): LogRound {
        val b = battle()
        val last = b.rounds.lastOrNull()
        if (last != null && last.kind == RoundKind.ROUND) return last
        return LogRound(RoundKind.ROUND).also { b.rounds += it; trim(b) }
    }

    private fun attack(e: GameEvent.Attack) {
        val parts = ArrayList<String>()
        parts += "${e.user} used ${e.move}"
        when {
            e.missed -> parts += "missed"
            e.failed -> parts += "failed"
            e.effectiveness == 0.0 -> parts += "no effect"
            else -> {
                if (e.damage > 0) parts += "${e.damage} damage" + if (e.hits > 1) " (${e.hits} hits)" else ""
                if (e.crit && e.damage > 0) parts += "critical hit"
                if (e.damage > 0 && e.effectiveness > 1.0) parts += "super effective"
                if (e.damage > 0 && e.effectiveness < 1.0) parts += "not very effective"
            }
        }
        if (e.damage > 0 && !e.byPlayer && wildPower != 1.0) parts += "×" + String.format(Locale.US, "%.2f", wildPower).trimEnd('0').trimEnd('.') + " power"
        if (e.damage > 0) parts += "${e.target} ${e.targetHp}/${e.targetMaxHp} HP"
        val hit = e.damage > 0
        val tone = when {
            e.missed || e.failed || e.effectiveness == 0.0 -> Tone.SUB
            hit -> if (e.byPlayer) Tone.GOOD else Tone.BAD
            else -> Tone.PLAIN
        }
        moves().lines += LogLine(tone, parts.joinToString(" · "))
        if (e.byPlayer) { wildHp = e.targetHp; playerHp = e.userHp } else { playerHp = e.targetHp; wildHp = e.userHp }
    }

    private fun effect(e: GameEvent.Effect) {
        val last = if (e.onPlayer) playerHp else wildHp
        if (e.onPlayer) playerHp = e.hp else wildHp = e.hp
        val tone = when {
            e.status != null -> if (e.onPlayer) Tone.BAD else Tone.GOOD
            else -> Tone.PLAIN
        }
        moves().lines += LogLine(tone, e.text + if (last != e.hp) " · HP ${e.hp}/${e.maxHp}" else "")
    }

    private fun ball(e: GameEvent.BallThrow) {
        val b = battle()
        val r = LogRound(RoundKind.BALL).also { b.rounds += it; trim(b) }
        r.lines += LogLine(
            if (e.success) Tone.GOOD else Tone.SUB,
            "Threw ${prettyItem(e.ball)} · ${e.chance}% · " + if (e.success) "caught" else "it broke free and ran away",
        )
    }

    private fun finish(outcome: Outcome, line: LogLine) {
        val b = open() ?: return
        b.outcome = outcome
        b.result += line
        resultOpen = true
        defeatedWith = if (outcome == Outcome.DEFEATED) lastAnswer else null
        lastAnswer = null
    }

    private fun resultLine(tone: Tone, text: String) {
        if (resultOpen) (battles.lastOrNull() ?: return).result += LogLine(tone, text)
    }

    private fun trim(b: LogBattle) { while (b.rounds.size > MAX_ROUNDS) b.rounds.removeAt(0) }

    private fun duplicateText(e: GameEvent.Duplicate): String = when (e.result) {
        DuplicateResult.DEFEATED -> "Not higher than your Lv ${e.ownedLevel} copy · defeated, not caught"
        DuplicateResult.SWAPPED -> "Swapped for Lv ${e.level} · your Lv ${e.ownedLevel} ${e.releasedName ?: e.name} was released"
        DuplicateResult.KEPT_BOTH -> "Kept both · your Lv ${e.ownedLevel} copy is ${e.keptBecause ?: "kept"}"
    }

    private fun caughtText(e: GameEvent.Caught): String =
        "Caught ${e.name} · Lv ${e.level}" + (if (e.shiny) " · shiny" else "") + (if (e.newSpecies) " · new Pokédex entry" else "") +
            (e.ball?.let { " · ${prettyItem(it)}" } ?: "") + (if (e.toTeam) " · joined the team" else " · sent to the box")

    companion object {
        const val KEEP = 5
        private const val MAX_ROUNDS = 150
        private const val MAX_PENDING = 200

        /** "great-ball" -> "Great ball". */
        fun prettyItem(id: String): String = id.replace('-', ' ').replaceFirstChar { it.titlecase(Locale.US) }

        /** PLAN 155: the log's own code for the OK of a new card (the game's buttons are 0..4; the log file keeps the number as it is). */
        const val OK = 5

        fun buttonLabel(button: Int): String = when (button) { 1 -> "Again"; 2 -> "Hard"; 3 -> "Good"; 4 -> "Easy"; OK -> "OK"; else -> "Answer" }
    }
}
