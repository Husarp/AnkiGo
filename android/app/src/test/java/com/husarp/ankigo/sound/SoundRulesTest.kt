package com.husarp.ankigo.sound

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundRulesTest {
    private fun attack(damage: Int = 10, eff: Double = 1.0, missed: Boolean = false, failed: Boolean = false,
                       cat: Category = Category.PHYSICAL, byPlayer: Boolean = true) =
        GameEvent.Attack(byPlayer, "A", "B", "Tackle", null, cat, damage, 1, eff, false, missed, failed, 5, 20, 20, 20)

    private fun effect(e: GameEvent, ball: Boolean = false) = (SoundRules.cueFor(e, ball) as? Cue.Effect)?.id

    // ---- which event -> which sound ----

    @Test fun attacksByEffectiveness() {
        assertEquals(SoundId.HIT, effect(attack(eff = 1.0)))
        assertEquals(SoundId.SUPER, effect(attack(eff = 2.0)))
        assertEquals(SoundId.SUPER, effect(attack(eff = 4.0)))
        assertEquals(SoundId.WEAK, effect(attack(eff = 0.5)))
        assertEquals(SoundId.WEAK, effect(attack(eff = 0.25)))
        assertEquals(SoundId.HIT, effect(attack(byPlayer = false)))
    }

    @Test fun attacksThatDidNotHitAreSilent() {
        assertNull(SoundRules.cueFor(attack(damage = 0)))
        assertEquals(SoundId.MISS, effect(attack(missed = true)))
        assertEquals(SoundId.MISS, effect(attack(failed = true)))
        assertEquals(SoundId.MISS, effect(attack(damage = 0, eff = 0.0))) // no effect: a "miss" sound now
        assertNull(SoundRules.cueFor(attack(cat = Category.STATUS, damage = 0)))
    }

    @Test fun otherEvents() {
        assertEquals(SoundId.FAINT, effect(GameEvent.Fainted(true, "A")))
        assertEquals(SoundId.FAINT, effect(GameEvent.Fainted(false, "B")))
        assertEquals(SoundId.CATCH, effect(GameEvent.Caught("u", 1, "B", 5, false, null, false, true), ball = true))
        assertEquals(SoundId.LEVEL_UP, effect(GameEvent.LevelUp("u", "A", 6, 20, 20)))
        assertNull(SoundRules.cueFor(GameEvent.Cash(5, 10)))
        assertNull(SoundRules.cueFor(GameEvent.Defeated(1, "B", 5)))
        assertNull(SoundRules.cueFor(GameEvent.Counted(3, 2, 1, 1, false)))
        assertNull(SoundRules.cueFor(GameEvent.Info("hi")))
    }

    @Test fun encounterIsACry() {
        val cue = SoundRules.cueFor(GameEvent.Encounter(10091, "Wild X", 5, false, Tier.NORMAL, 20, 20, false, false))
        assertTrue(cue is Cue.Cry)
        assertEquals(10091, (cue as Cue.Cry).species)
    }

    @Test fun soundsLineUpWithTheAnimation() {
        assertEquals(300, SoundRules.cueFor(attack())!!.delayMs)
        assertEquals(0, SoundRules.cueFor(GameEvent.LevelUp("u", "A", 6, 20, 20))!!.delayMs)
        val caught = GameEvent.Caught("u", 1, "B", 5, false, null, false, true)
        assertEquals(0, SoundRules.cueFor(caught, ballShown = true)!!.delayMs)
        assertEquals(2250, SoundRules.cuesFor(caught, ballShown = false).last().delayMs) // the CATCH, after the wobbles
    }

    // ---- may it be heard? ----

    private val hit = Cue.Effect(SoundId.HIT)
    private val cry = Cue.Cry(25)
    private val ok = SoundContext()

    @Test fun defaultsPlayEverything() {
        assertTrue(SoundRules.shouldPlay(hit, ok))
        assertTrue(SoundRules.shouldPlay(cry, ok))
    }

    @Test fun nothingToPlay() = assertFalse(SoundRules.shouldPlay(null, ok))

    @Test fun battleSoundsOffKeepsCries() {
        val c = ok.copy(groupsOff = setOf(SoundGroup.BATTLE))
        assertFalse(SoundRules.shouldPlay(hit, c))
        assertTrue(SoundRules.shouldPlay(cry, c))
    }

    @Test fun criesOffKeepsBattleSounds() {
        val c = ok.copy(groupsOff = setOf(SoundGroup.CRIES))
        assertTrue(SoundRules.shouldPlay(hit, c))
        assertFalse(SoundRules.shouldPlay(cry, c))
    }

    @Test fun volumeZeroIsSilent() {
        val zero = SoundChannel.entries.associateWith { 0 }
        assertFalse(SoundRules.shouldPlay(hit, ok.copy(volumes = zero)))
        assertFalse(SoundRules.shouldPlay(cry, ok.copy(volumes = zero)))
        assertTrue(SoundRules.shouldPlay(hit, ok.copy(volumes = mapOf(SoundChannel.BATTLE to 10))))
    }

    @Test fun silentOrVibrateIsSilent() {
        assertFalse(SoundRules.shouldPlay(hit, ok.copy(ringerNormal = false)))
        assertFalse(SoundRules.shouldPlay(cry, ok.copy(ringerNormal = false)))
    }

    @Test fun mutedAndSkippingAreSilent() {
        assertFalse(SoundRules.shouldPlay(hit, ok.copy(muted = true)))
        assertFalse(SoundRules.shouldPlay(cry, ok.copy(muted = true)))
        assertFalse(SoundRules.shouldPlay(hit, ok.copy(skipping = true)))
        assertFalse(SoundRules.shouldPlay(cry, ok.copy(skipping = true)))
    }

    // ---- R9-C3: the XP orb pling follows the same rules as the other battle sounds ----

    private val orb = Cue.Effect(SoundId.XP_ABSORB)

    @Test fun theOrbPlingFollowsGroupVolumeRingerMuteAndSkip() {
        assertEquals(SoundGroup.MOMENTS, SoundId.XP_ABSORB.group)
        assertEquals("xp_absorb-v2.wav", SoundId.XP_ABSORB.file)
        assertTrue(SoundRules.shouldPlay(orb, ok))
        assertFalse(SoundRules.shouldPlay(orb, ok.copy(groupsOff = setOf(SoundGroup.MOMENTS))))
        assertTrue(SoundRules.shouldPlay(orb, ok.copy(groupsOff = setOf(SoundGroup.BATTLE))))
        assertFalse(SoundRules.shouldPlay(orb, ok.copy(volumes = mapOf(SoundChannel.BATTLE to 0))))
        assertTrue(SoundRules.shouldPlay(orb, ok.copy(volumes = mapOf(SoundChannel.BATTLE to 5))))
        assertFalse(SoundRules.shouldPlay(orb, ok.copy(ringerNormal = false)))
        assertFalse(SoundRules.shouldPlay(orb, ok.copy(muted = true)))
        assertFalse(SoundRules.shouldPlay(orb, ok.copy(skipping = true)))
    }

    @Test fun theLastOrbIsFullerAndRatesStayLegal() {
        assertTrue(SoundRules.orbGain(true) > SoundRules.orbGain(false))
        assertEquals(1f, SoundRules.orbGain(true), 0f)
        assertEquals(1.1f, SoundRules.orbRate(1.1f, false), 0f)
        assertTrue(SoundRules.orbRate(1.25f, true) < 1.25f)
        assertEquals(2f, SoundRules.orbRate(9f, false), 0f)
        assertEquals(0.5f, SoundRules.orbRate(0.1f, false), 0f)
    }

    @Test fun gapBetweenSameSounds() {
        assertTrue(SoundRules.gapOk(null, 1000))
        assertFalse(SoundRules.gapOk(1000, 1079))
        assertTrue(SoundRules.gapOk(1000, 1080))
        assertTrue(SoundRules.gapOk(5000, 100)) // clock went back: don't stay silent forever
    }

    @Test fun theOrbPlingHasItsOwnSmallerRepeatGuard() {
        // R9-W4-FIX: plings come 50 ms or more apart; the 80 ms guard of the other sounds would swallow them
        assertTrue(SoundRules.ORB_MIN_GAP_MS < SoundRules.MIN_GAP_MS)
        assertTrue(SoundRules.ORB_MIN_GAP_MS < com.husarp.ankigo.ui.XpParticles.SOUND_GAP_MS)   // always below the pick gap
        assertFalse(SoundRules.gapOk(1000, 1039, SoundRules.ORB_MIN_GAP_MS))
        assertTrue(SoundRules.gapOk(1000, 1050, SoundRules.ORB_MIN_GAP_MS))
        assertFalse(SoundRules.gapOk(1000, 1075)) // the default guard is unchanged
    }

    @Test fun volumeScale() {
        assertEquals(0f, SoundRules.volume(0), 0f)
        assertEquals(0.7f, SoundRules.volume(70), 0.001f)
        assertEquals(1f, SoundRules.volume(100), 0f)
        assertEquals(1f, SoundRules.volume(250), 0f)
        assertEquals(0f, SoundRules.volume(-5), 0f)
    }

    // ---- quiet on cards with sound ----

    @Test fun cardAudioDetection() {
        assertTrue(SoundRules.cardHasAudio("What is this? [sound:cat.mp3]", ""))
        assertTrue(SoundRules.cardHasAudio("Q", "A <audio src=\"x.mp3\"></audio>"))
        assertTrue(SoundRules.cardHasAudio("Q", "A <AUDIO controls>"))
        assertFalse(SoundRules.cardHasAudio("Capital of Australia?", "Canberra <img src=\"a.png\"> [sound:]"))
        assertFalse(SoundRules.cardHasAudio("", ""))
    }

    @Test fun quietRule() {
        assertTrue(SoundRules.quietNow(true, currentHasAudio = true, previousHasAudio = false))
        assertTrue(SoundRules.quietNow(true, currentHasAudio = false, previousHasAudio = true))
        assertFalse(SoundRules.quietNow(true, currentHasAudio = false, previousHasAudio = false))
        assertFalse(SoundRules.quietNow(false, currentHasAudio = true, previousHasAudio = true))
    }

    @Test fun cueExists() = assertNotNull(SoundRules.cueFor(attack()))
}
