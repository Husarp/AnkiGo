package com.husarp.ankigo.sound

import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent

/** What to play: one of the synthesized sounds, or the cry of a species (its dex number or form id). [delayMs] lines it up with the animation. */
sealed class Cue(val delayMs: Long) {
    class Effect(val id: SoundId, delayMs: Long = 0) : Cue(delayMs) {
        override fun toString() = "Effect($id)"
    }
    class Cry(val species: Int, delayMs: Long = 0) : Cue(delayMs) {
        override fun toString() = "Cry($species)"
    }
}

/** Everything [SoundRules.shouldPlay] needs to know about the player's settings and the phone. */
data class SoundContext(
    /** Groups the player switched off (all on by default). */
    val groupsOff: Set<SoundGroup> = emptySet(),
    /** 0..100 per channel; a missing channel counts as 70. */
    val volumes: Map<SoundChannel, Int> = emptyMap(),
    val ringerNormal: Boolean = true,
    /** Quiet-on-card-audio rule says the study screen is playing a card with sound. */
    val muted: Boolean = false,
    /** Catch-up batch or reduced motion: the panel skips the animations, so nothing sounds. */
    val skipping: Boolean = false,
) {
    fun on(g: SoundGroup) = g !in groupsOff
    fun volumePct(ch: SoundChannel) = volumes[ch] ?: SoundDefaults.OTHER_VOLUME
}

/** Pure rules: which event makes which sounds, and whether they may be heard now. */
object SoundRules {
    /** A cue delay shortened (or kept) by the event player's pace [factor] (0.5 = the visuals run twice as fast); applied once. */
    fun scaledDelay(delayMs: Long, factor: Double): Long =
        if (delayMs <= 0) 0 else maxOf(0L, (delayMs * factor.coerceIn(0.0, 1.0)).toLong())

    /** The same sound is not started again within this time (a fast burst of identical hits would only buzz). */
    const val MIN_GAP_MS = 80L

    /** Visual delays of [com.husarp.ankigo.ui.EventPlayer]: the hit lands 300 ms after the lunge starts, the wild one has entered after 450 ms. */
    private const val HIT_DELAY_MS = 300L
    private const val ENTER_DELAY_MS = 450L
    /** A ball drops for 450 ms, then wobbles for 1.8 s: three wobbles inside that, and the banner at 2250 ms. */
    private val WOBBLE_MS = longArrayOf(600, 1100, 1600)
    private const val BALL_DONE_MS = 2250L
    private const val EVO_DONE_MS = 900L

    private fun fx(id: SoundId, at: Long = 0) = Cue.Effect(id, at)

    /** The first cue of [e], or null when the event is silent. [ballShown] = the ball is already wobbling when the catch is announced. */
    fun cueFor(e: GameEvent, ballShown: Boolean = false): Cue? = cuesFor(e, ballShown).firstOrNull()

    /**
     * All the cues of [e] (empty = silent), with delays that line up with the event player. [ballShown] = the ball is on the field
     * (a throw's wobble already sounded; a "broke free!" line follows a ball). [shiny] = for a team switch: the Pokémon sent out is shiny.
     */
    fun cuesFor(e: GameEvent, ballShown: Boolean = false, shiny: Boolean = false): List<Cue> = when (e) {
        is GameEvent.Encounter -> listOfNotNull(Cue.Cry(e.species, ENTER_DELAY_MS), if (e.shiny) fx(SoundId.SHINY, ENTER_DELAY_MS) else null)
        is GameEvent.Attack -> when {
            e.missed || e.failed || (e.category != Category.STATUS && e.effectiveness == 0.0) -> listOf(fx(SoundId.MISS, HIT_DELAY_MS))
            e.category == Category.STATUS || e.damage <= 0 -> emptyList()
            e.effectiveness > 1.0 -> listOf(fx(SoundId.SUPER, HIT_DELAY_MS))
            e.effectiveness < 1.0 -> listOf(fx(SoundId.WEAK, HIT_DELAY_MS))
            else -> listOf(fx(SoundId.HIT, HIT_DELAY_MS))
        }
        is GameEvent.Effect -> listOfNotNull(effect(e))
        is GameEvent.Fainted -> listOf(fx(SoundId.FAINT))
        is GameEvent.BallThrow -> listOf(fx(SoundId.THROW)) + WOBBLE_MS.map { fx(SoundId.WOBBLE, it) }
        is GameEvent.Caught ->
            if (ballShown) listOf(fx(SoundId.CATCH))
            else WOBBLE_MS.map { fx(SoundId.WOBBLE, it) } + fx(SoundId.CATCH, BALL_DONE_MS)
        is GameEvent.LevelUp -> listOf(fx(SoundId.LEVEL_UP))
        is GameEvent.Fled -> listOf(fx(SoundId.FLED))
        is GameEvent.Evolved -> listOf(fx(SoundId.EVO_START), fx(SoundId.EVO_DONE, EVO_DONE_MS))
        is GameEvent.MoveLearned -> listOf(fx(SoundId.LEARNED))
        is GameEvent.ItemFound -> listOf(fx(SoundId.ITEM))
        is GameEvent.Cash -> if (e.kind == CashKind.DEFEAT) listOf(fx(SoundId.CASH)) else emptyList()
        is GameEvent.BadgeEarned, is GameEvent.AchievementReached, is GameEvent.TrainerLevelUp -> listOf(fx(SoundId.BADGE))
        is GameEvent.Healed, is GameEvent.MainHealed -> listOf(fx(SoundId.HEALED))
        is GameEvent.MainChanged -> listOfNotNull(fx(SoundId.SENT_OUT), if (shiny) fx(SoundId.SHINY, ENTER_DELAY_MS) else null)
        is GameEvent.Info -> when {
            ballShown && e.text.endsWith("broke free!") -> listOf(fx(SoundId.BREAK_FREE))
            e.text == "Daily goal reached!" -> listOf(fx(SoundId.GOAL))
            else -> emptyList()
        }
        else -> emptyList()
    }

    private fun effect(e: GameEvent.Effect): Cue.Effect? = when (e.kind) {
        EffectKind.STAT_CHANGE -> if (e.stages > 0) fx(SoundId.STAT_UP) else if (e.stages < 0) fx(SoundId.STAT_DOWN) else null
        EffectKind.STATUS_APPLIED -> when (e.status) {
            "brn" -> fx(SoundId.BURN); "psn", "tox" -> fx(SoundId.POISON); "par" -> fx(SoundId.PARALYSIS)
            "slp" -> fx(SoundId.SLEEP); "frz" -> fx(SoundId.FREEZE); else -> null
        }
        EffectKind.STATUS_DAMAGE -> when (e.status) { "brn" -> fx(SoundId.BURN_TICK); "psn", "tox" -> fx(SoundId.POISON_TICK); else -> null }
        EffectKind.CHARGE -> fx(SoundId.CHARGE)
        EffectKind.OTHER, EffectKind.STATUS_CURED, EffectKind.CANT_MOVE, EffectKind.STAT_LIMIT,
        EffectKind.DRAIN, EffectKind.RECOIL, EffectKind.HEAL -> null
    }

    /** The settings group that switches [cue]. */
    fun groupOf(cue: Cue): SoundGroup = when (cue) { is Cue.Effect -> cue.id.group; is Cue.Cry -> SoundGroup.CRIES }

    fun shouldPlay(cue: Cue?, c: SoundContext): Boolean {
        if (cue == null || c.skipping || c.muted || !c.ringerNormal) return false
        val g = groupOf(cue)
        return c.on(g) && c.volumePct(g.channel) > 0
    }

    /** True when a sound that last played at [lastAt] (null = never) may start at [now]. */
    fun gapOk(lastAt: Long?, now: Long) = lastAt == null || now - lastAt >= MIN_GAP_MS || now < lastAt

    /** Volume 0.0..1.0 from the in-app percentage. */
    fun volume(pct: Int) = pct.coerceIn(0, 100) / 100f

    private val CARD_SOUND = Regex("""\[sound:.+?]|\[anki:play:[qa]:\d+]|\[anki:tts\b|<audio\b""", RegexOption.IGNORE_CASE)

    /** A card "has audio" when its question or answer has a [sound:...] reference, speech ([anki:play:...] / [anki:tts]) or an audio tag. */
    fun cardHasAudio(question: String, answer: String) = CARD_SOUND.containsMatchIn(question) || CARD_SOUND.containsMatchIn(answer)

    /** "Quiet on cards with sound": the card being shown, or the one just answered (its events play while the next card loads), has audio. */
    fun quietNow(quietOnCardAudio: Boolean, currentHasAudio: Boolean, previousHasAudio: Boolean) =
        quietOnCardAudio && (currentHasAudio || previousHasAudio)
}
