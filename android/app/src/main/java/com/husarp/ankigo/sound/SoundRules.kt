package com.husarp.ankigo.sound

import com.husarp.ankigo.game.Category
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
    val battleSounds: Boolean = true,
    val cries: Boolean = true,
    val volumePct: Int = 70,
    val ringerNormal: Boolean = true,
    /** Quiet-on-card-audio rule says the study screen is playing a card with sound. */
    val muted: Boolean = false,
    /** Catch-up batch or reduced motion: the panel skips the animations, so nothing sounds. */
    val skipping: Boolean = false,
)

/** Pure rules: which event makes which sound, and whether it may be heard now. */
object SoundRules {
    /** The same sound is not started again within this time (a fast burst of identical hits would only buzz). */
    const val MIN_GAP_MS = 80L

    /** Visual delays of [com.husarp.ankigo.ui.EventPlayer]: the hit lands 300 ms after the lunge starts, the wild one has entered after 450 ms. */
    private const val HIT_DELAY_MS = 300L
    private const val ENTER_DELAY_MS = 450L

    /** The cue of [e], or null when the event is silent. [ballShown] = the ball is already wobbling when the catch is announced. */
    fun cueFor(e: GameEvent, ballShown: Boolean = false): Cue? = when (e) {
        is GameEvent.Encounter -> Cue.Cry(e.species, ENTER_DELAY_MS)
        is GameEvent.Attack -> when {
            e.missed || e.failed || e.category == Category.STATUS || e.damage <= 0 -> null
            e.effectiveness > 1.0 -> Cue.Effect(SoundId.SUPER, HIT_DELAY_MS)
            e.effectiveness < 1.0 -> Cue.Effect(SoundId.WEAK, HIT_DELAY_MS)
            else -> Cue.Effect(SoundId.HIT, HIT_DELAY_MS)
        }
        is GameEvent.Fainted -> Cue.Effect(SoundId.FAINT)
        is GameEvent.Caught -> Cue.Effect(SoundId.CATCH, if (ballShown) 0 else ENTER_DELAY_MS)
        is GameEvent.LevelUp -> Cue.Effect(SoundId.LEVEL_UP)
        else -> null
    }

    fun shouldPlay(cue: Cue?, c: SoundContext): Boolean {
        if (cue == null || c.skipping || c.muted || !c.ringerNormal || c.volumePct <= 0) return false
        return when (cue) {
            is Cue.Effect -> c.battleSounds
            is Cue.Cry -> c.cries
        }
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
