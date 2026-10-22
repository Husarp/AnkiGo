package com.husarp.ankigo.ui

import com.husarp.ankigo.game.AchPageMath
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Type

/** The two halves of the battle action bar: your Pokémon's last move and the wild one's (Round 9, D3). */
enum class BarSide { MINE, THEIRS }

/** Where the charge dots sit in the action bar (Settings → Battle → Turn dots): between the two moves, or after them. */
enum class DotsPlace(val label: String) {
    MIDDLE("Middle"), RIGHT_END("Right end");

    companion object {
        const val KEY = "turnDots"

        /** The saved choice (the enum name); unknown or missing = [MIDDLE]. */
        fun parse(name: String?): DotsPlace = entries.firstOrNull { it.name == name } ?: MIDDLE
    }
}

/** What a chip in the move bubble means; the look ([MoveBubbleLook]) picks its colours from it. */
enum class BubbleTone { GOOD, BAD, NEUTRAL, IMMUNE, CRIT, STATUS, BOOST, MUTED }

/** The small symbol in front of a chip's label (a status chip shows the status badge instead). */
enum class ChipIcon { NONE, UP, DOWN, IMMUNE, CRIT, MISS }

/** One chip of the bubble. [status] = the engine status key of a [BubbleTone.STATUS] chip ("brn" ...). */
data class BubbleChip(val label: String, val tone: BubbleTone, val icon: ChipIcon = ChipIcon.NONE, val status: String? = null)

/** How the big number of the bubble is coloured: [RED] = damage to us, [MUTED] = no number (missed, no effect, failed). */
enum class DamageTone { PLAIN, RED, MUTED }

/** Everything the bubble shows, as data. [damage] null = the move deals none (a status move). */
data class BubbleContent(
    val who: String, val move: String, val type: Type?,
    val damage: String?, val damageTone: DamageTone, val chips: List<BubbleChip>,
) {
    /** The bubble read out for TalkBack: "Wild Gnarlpup used Bite: −12 HP, Super effective ×2". */
    fun spoken(): String = buildString {
        append(who).append(" used ").append(move)
        val parts = listOfNotNull(damage) + chips.map { it.label }
        if (parts.isNotEmpty()) append(": ").append(parts.joinToString(", "))
    }
}

/** A stat change a move caused ([stat] = the engine key "atk" ..., [stages] signed, as really applied). */
data class StatShift(val stat: String, val stages: Int)

/**
 * One move as the action bar remembers it: the [GameEvent.Attack] plus the effects that followed it (a status it inflicted,
 * stat changes). [who] is the display name of the user ("Wild Gnarlpup" for the wild one).
 */
data class BarMove(
    val side: BarSide, val who: String, val move: String, val type: Type?, val category: Category,
    val damage: Int, val hits: Int, val effectiveness: Double, val crit: Boolean, val missed: Boolean, val failed: Boolean,
    val status: String? = null, val shifts: List<StatShift> = emptyList(),
)

/** What one half of the bar holds: nothing yet in this fight, a note instead of a move (it could not move), or a move. */
sealed class HalfState {
    object Empty : HalfState()
    data class Note(val text: String) : HalfState()
    data class Moved(val move: BarMove) : HalfState()
}

/** What a battle line is about, so the panel knows where it goes (see [ActionBarModel.scope]). */
enum class LineScope {
    /** About the whole battle (the app's own news): shown across the full bar until the next move. */
    WHOLE,
    /** About a move or its effects: it is on the bar already, and in the bubble; the line is only spoken. */
    MOVE,
    /** About one side but not a move (encounter, caught, fainted ...): the arena shows it; the line is only spoken. */
    SIDE,
    /** An achievement (or a badge that is one): the "achievement earned" popup shows it, so the bar keeps its moves; the line is only spoken. */
    POPUP,
}

/**
 * Round 9 D3: the state behind the battle action bar, folded from the [GameEvent]s in the order they are played (pure, no
 * Android). An Attack fills its user's half and opens a window in which the Effects that follow in the same batch (a status it
 * inflicted, stat changes) attach to that move; anything else closes the window. An Encounter clears both halves.
 * Round 11 (PLAN 137): a round start refreshes BOTH halves to "No move", so a side that does not act (only the wild one attacks, or
 * only you) never keeps its move of the round before; the Attack / Splash / Effect events of the round then fill what happened.
 */
class ActionBarModel {
    var mine: HalfState = HalfState.Empty
        private set
    var theirs: HalfState = HalfState.Empty
        private set

    /** The side whose Attack was the latest event or only followed by Effects: its effects still attach. */
    private var open: BarSide? = null

    fun half(side: BarSide): HalfState = if (side == BarSide.MINE) mine else theirs

    private fun put(side: BarSide, s: HalfState) { if (side == BarSide.MINE) mine = s else theirs = s }

    /** Forgets both moves (a new fight). */
    fun reset() { mine = HalfState.Empty; theirs = HalfState.Empty; open = null }

    /** Takes [e] in; returns the sides whose half changed (empty = the bar looks the same). */
    fun observe(e: GameEvent): List<BarSide> {
        if (e !is GameEvent.Effect) open = null
        return when (e) {
            is GameEvent.Encounter -> { reset(); BOTH }
            is GameEvent.RoundStart -> { put(BarSide.MINE, HalfState.Note(NO_MOVE)); put(BarSide.THEIRS, HalfState.Note(NO_MOVE)); BOTH }
            // R11-133: the turn a switch (R13 Part C: or an item) took: your half says why only the wild one attacks
            is GameEvent.SwitchHit -> {
                put(BarSide.MINE, HalfState.Note(e.item?.let { "Used ${com.husarp.ankigo.game.Items.name(it)}" } ?: "Switched to ${e.monName}"))
                listOf(BarSide.MINE)
            }
            is GameEvent.MainChanged -> { put(BarSide.MINE, HalfState.Empty); listOf(BarSide.MINE) }   // a new Pokémon has not moved yet
            is GameEvent.Attack -> {
                val side = if (e.byPlayer) BarSide.MINE else BarSide.THEIRS
                put(side, HalfState.Moved(BarMove(
                    side, if (e.byPlayer) e.user else wild(e.user), e.move, e.moveType, e.category, e.damage, e.hits,
                    e.effectiveness, e.crit, e.missed, e.failed,
                )))
                open = side
                listOf(side)
            }
            is GameEvent.Splash -> { put(BarSide.THEIRS, HalfState.Note("Just watching")); listOf(BarSide.THEIRS) }
            is GameEvent.Effect -> effect(e)
            else -> emptyList()
        }
    }

    private fun effect(e: GameEvent.Effect): List<BarSide> {
        val own = if (e.onPlayer) BarSide.MINE else BarSide.THEIRS
        when (e.kind) {
            EffectKind.CANT_MOVE -> { open = null; put(own, HalfState.Note(cantMove(e.stat ?: e.status, e.text))); return listOf(own) }
            EffectKind.CHARGE -> { open = null; put(own, HalfState.Note(e.text)); return listOf(own) }
            EffectKind.STATUS_APPLIED, EffectKind.STAT_CHANGE -> {
                val side = open ?: return emptyList()
                val m = (half(side) as? HalfState.Moved)?.move ?: return emptyList()
                val next = if (e.kind == EffectKind.STATUS_APPLIED) {
                    m.copy(status = e.status ?: return emptyList())
                } else {
                    val stat = e.stat ?: return emptyList()
                    if (e.stages == 0) return emptyList()
                    m.copy(shifts = m.shifts + StatShift(stat, e.stages))
                }
                put(side, HalfState.Moved(next))
                return listOf(side)
            }
            else -> return emptyList()   // drain, recoil, heals, cures, burn damage ...: floats show them
        }
    }

    companion object {
        private val BOTH = listOf(BarSide.MINE, BarSide.THEIRS)

        /** What a half says in a round in which that side made no move. */
        const val NO_MOVE = "No move"

        /** PLAN 206: the bar's line while a switch waits for the next answer; with the incoming Pokémon's [name] when the bar has room for it ({{ }} = mauve). */
        fun queuedLine(name: String? = null) = if (name.isNullOrBlank()) "Switching after this card…" else "Switching to {{$name}} after this card…"

        /** "Wild Psyduck" whether or not the name already has "Wild" in front. */
        fun wild(name: String) = if (name.startsWith("Wild ")) name else "Wild $name"

        /** PLAN 172: by the status key the engine sends; the text is the fallback for old events. */
        private fun cantMove(key: String?, text: String) = when {
            key == "par" -> "Can't move: paralyzed"
            key == "slp" -> "Can't move: asleep"
            key == "frz" -> "Can't move: frozen"
            "paralyzed" in text -> "Can't move: paralyzed"
            "asleep" in text -> "Can't move: asleep"
            "frozen" in text -> "Can't move: frozen"
            else -> "Can't move"
        }

        /**
         * Where the battle line of [e] goes. The charge counter ([GameEvent.Charge], "charging 1/2") is about your side and not a move:
         * it stays off the bar (the dots show it on the Study screen), so the last moves stay on the bar between attacks.
         */
        fun scope(e: GameEvent): LineScope = when (e) {
            is GameEvent.Attack, is GameEvent.Effect, is GameEvent.Splash -> LineScope.MOVE
            is GameEvent.Encounter, is GameEvent.Defeated, is GameEvent.Fled, is GameEvent.Fainted, is GameEvent.Caught,
            is GameEvent.BallThrow, is GameEvent.LevelUp, is GameEvent.MoveLearned, is GameEvent.MoveSkipped, is GameEvent.Evolved,
            is GameEvent.MainHealed, is GameEvent.Healed, is GameEvent.MainChanged, is GameEvent.XpGained, is GameEvent.Charge,
            is GameEvent.Duplicate, is GameEvent.SwitchHit -> LineScope.SIDE   // PLAN 173: a switch draws no whole-bar line
            is GameEvent.SwitchQueued -> LineScope.SIDE   // PLAN 206: its line is [ActionBarView.setQueued] (also derived from the saved game), not a battle line
            is GameEvent.Info -> if (e.text.endsWith("broke free!")) LineScope.SIDE else LineScope.WHOLE
            is GameEvent.AchievementReached -> LineScope.POPUP
            is GameEvent.DailyGift -> LineScope.WHOLE   // R14-B2-D: like ItemFound, its line ("Daily gift: ...") goes across the bar although the popup shows it too
            is GameEvent.BadgeEarned -> if (AchPageMath.forBadge(e.id) != null) LineScope.POPUP else LineScope.WHOLE
            else -> LineScope.WHOLE
        }

        // ------------------------------------------------------------ the bubble

        private const val MINUS = "−"

        /** The bubble of [m]: header, the big number, and the chips (see the D3-E2 mockup's `parts()`). */
        fun bubble(m: BarMove): BubbleContent {
            val mine = m.side == BarSide.MINE
            val chips = ArrayList<BubbleChip>()
            fun done(damage: String?, tone: DamageTone) = BubbleContent(m.who, m.move, m.type, damage, tone, chips)
            if (m.missed) {
                chips += BubbleChip("No hit", BubbleTone.MUTED, ChipIcon.MISS)
                return done("Missed", DamageTone.MUTED)
            }
            if (m.failed) {
                chips += BubbleChip("Didn't work", BubbleTone.MUTED, ChipIcon.MISS)
                return done("Failed", DamageTone.MUTED)
            }
            val damaging = m.category != Category.STATUS
            if (damaging && m.effectiveness == 0.0) {
                chips += BubbleChip("Immune ×0", BubbleTone.IMMUNE, ChipIcon.IMMUNE)
                return done("No effect", DamageTone.MUTED)
            }
            if (damaging) {
                // super effective is good news when it is your move and bad news when it is theirs
                if (m.effectiveness > 1.0) chips += BubbleChip("Super effective ×${times(m.effectiveness)}", if (mine) BubbleTone.GOOD else BubbleTone.BAD, ChipIcon.UP)
                else if (m.effectiveness < 1.0) chips += BubbleChip("Not very effective ×${fraction(m.effectiveness)}", BubbleTone.NEUTRAL, ChipIcon.DOWN)
                if (m.crit) chips += BubbleChip("CRIT!", BubbleTone.CRIT, ChipIcon.CRIT)
                if (m.hits > 1) chips += BubbleChip("×${m.hits} hits", BubbleTone.NEUTRAL)
            }
            m.status?.let { s -> StatusInfo.of(s)?.let { chips += BubbleChip(it.name, BubbleTone.STATUS, status = it.status) } }
            for (s in m.shifts) chips += BubbleChip(shift(s), BubbleTone.BOOST)
            if (!damaging && chips.isEmpty()) chips += BubbleChip("Status move", BubbleTone.MUTED)
            return if (damaging) done("$MINUS${m.damage} HP", if (mine) DamageTone.PLAIN else DamageTone.RED) else done(null, DamageTone.PLAIN)
        }

        private fun times(x: Double) = if (x == Math.rint(x)) x.toInt().toString() else String.format(java.util.Locale.US, "%.2f", x).trimEnd('0').trimEnd('.')

        private fun fraction(x: Double) = when {
            Math.abs(x - 0.5) < 1e-9 -> "½"
            Math.abs(x - 0.25) < 1e-9 -> "¼"
            Math.abs(x - 0.125) < 1e-9 -> "⅛"
            else -> times(x)
        }

        private fun shift(s: StatShift): String {
            val name = when (s.stat) {
                "atk" -> "Attack"; "def" -> "Defense"; "spa" -> "Sp. Atk"; "spd" -> "Sp. Def"; "spe" -> "Speed"
                "accuracy" -> "Accuracy"; "evasion" -> "Evasion"; else -> s.stat
            }
            val n = minOf(3, Math.abs(s.stages))
            return "$name " + (if (s.stages > 0) "↑" else "↓").repeat(n)
        }
    }
}
