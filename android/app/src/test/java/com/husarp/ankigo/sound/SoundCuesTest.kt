package com.husarp.ankigo.sound

import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.HealReason
import com.husarp.ankigo.game.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN 39: every formerly silent event has its sound(s) with delays that line up with the event player. */
class SoundCuesTest {
    private fun cues(e: GameEvent, ball: Boolean = false, shiny: Boolean = false) =
        SoundRules.cuesFor(e, ball, shiny).map { (it as? Cue.Effect)?.id?.name?.let { n -> n to it.delayMs } ?: ("CRY" to it.delayMs) }

    private fun attack(damage: Int = 10, eff: Double = 1.0, missed: Boolean = false, failed: Boolean = false, cat: Category = Category.PHYSICAL) =
        GameEvent.Attack(true, "A", "B", "Tackle", null, cat, damage, 1, eff, false, missed, failed, 5, 20, 20, 20)

    private fun fx(kind: EffectKind, status: String? = null, stages: Int = 0, onPlayer: Boolean = false) =
        GameEvent.Effect(onPlayer, "text", 10, 20, status, kind, null, stages, 3)

    @Test fun missesAndNoEffect() {
        assertEquals(listOf("MISS" to 300L), cues(attack(damage = 0, missed = true)))
        assertEquals(listOf("MISS" to 300L), cues(attack(damage = 0, failed = true)))
        assertEquals(listOf("MISS" to 300L), cues(attack(damage = 0, eff = 0.0)))
    }

    @Test fun statusMoveItselfIsSilent() {
        assertEquals(emptyList<Pair<String, Long>>(), cues(attack(damage = 0, cat = Category.STATUS)))
        assertEquals(listOf("HIT" to 300L), cues(attack()))
        assertEquals(listOf("SUPER" to 300L), cues(attack(eff = 2.0)))
        assertEquals(listOf("WEAK" to 300L), cues(attack(eff = 0.5)))
    }

    @Test fun statChanges() {
        assertEquals(listOf("STAT_UP" to 0L), cues(fx(EffectKind.STAT_CHANGE, stages = 1)))
        assertEquals(listOf("STAT_UP" to 0L), cues(fx(EffectKind.STAT_CHANGE, stages = 2)))
        assertEquals(listOf("STAT_DOWN" to 0L), cues(fx(EffectKind.STAT_CHANGE, stages = -1)))
        assertTrue(cues(fx(EffectKind.STAT_CHANGE, stages = 0)).isEmpty())
    }

    @Test fun statusApplied() {
        val want = mapOf("brn" to "BURN", "psn" to "POISON", "tox" to "POISON", "par" to "PARALYSIS", "slp" to "SLEEP", "frz" to "FREEZE")
        for ((st, name) in want) assertEquals(st, listOf(name to 0L), cues(fx(EffectKind.STATUS_APPLIED, st)))
        assertTrue(cues(fx(EffectKind.STATUS_APPLIED, null)).isEmpty())
    }

    @Test fun statusTicks() {
        assertEquals(listOf("BURN_TICK" to 0L), cues(fx(EffectKind.STATUS_DAMAGE, "brn")))
        assertEquals(listOf("POISON_TICK" to 0L), cues(fx(EffectKind.STATUS_DAMAGE, "psn")))
        assertEquals(listOf("POISON_TICK" to 0L), cues(fx(EffectKind.STATUS_DAMAGE, "tox")))
        assertTrue(cues(fx(EffectKind.STATUS_DAMAGE, null)).isEmpty())
    }

    @Test fun chargeTurn() = assertEquals(listOf("CHARGE" to 0L), cues(fx(EffectKind.CHARGE)))

    @Test fun otherEffectsStaySilent() {
        for (k in listOf(EffectKind.OTHER, EffectKind.STATUS_CURED, EffectKind.CANT_MOVE, EffectKind.STAT_LIMIT, EffectKind.DRAIN, EffectKind.RECOIL, EffectKind.HEAL))
            assertTrue("$k", cues(fx(k, "brn", stages = 1)).isEmpty())
    }

    @Test fun ballThrowWobblesThreeTimes() {
        assertEquals(listOf("THROW" to 0L, "WOBBLE" to 600L, "WOBBLE" to 1100L, "WOBBLE" to 1600L), cues(GameEvent.BallThrow("poke-ball", 40, false, 3)))
    }

    @Test fun breakFreeOnlyWithABall() {
        val info = GameEvent.Info("Wild X broke free!")
        assertEquals(listOf("BREAK_FREE" to 0L), cues(info, ball = true))
        assertTrue(cues(info, ball = false).isEmpty())
    }

    @Test fun caughtWithAndWithoutAThrownBall() {
        val caught = GameEvent.Caught("u", 1, "B", 5, false, null, false, true)
        assertEquals(listOf("CATCH" to 0L), cues(caught, ball = true))
        assertEquals(listOf("WOBBLE" to 600L, "WOBBLE" to 1100L, "WOBBLE" to 1600L, "CATCH" to 2250L), cues(caught, ball = false))
    }

    @Test fun encounterCryAndShiny() {
        fun enc(shiny: Boolean) = GameEvent.Encounter(10, "Wild X", 5, shiny, Tier.NORMAL, 20, 20, false, false)
        assertEquals(listOf("CRY" to 450L), cues(enc(false)))
        assertEquals(listOf("CRY" to 450L, "SHINY" to 450L), cues(enc(true)))
    }

    @Test fun switchSendsOutAndSparklesWhenShiny() {
        val sw = GameEvent.MainChanged("u", "A")
        assertEquals(listOf("SENT_OUT" to 0L), cues(sw))
        assertEquals(listOf("SENT_OUT" to 0L, "SHINY" to 450L), cues(sw, shiny = true))
    }

    @Test fun momentsAndMore() {
        assertEquals(listOf("FLED" to 0L), cues(GameEvent.Fled("X")))
        assertEquals(listOf("EVO_START" to 0L, "EVO_DONE" to 900L), cues(GameEvent.Evolved("u", "A", "B", 2)))
        assertEquals(listOf("LEARNED" to 0L), cues(GameEvent.MoveLearned("u", "A", "Tackle", null)))
        assertEquals(listOf("ITEM" to 0L), cues(GameEvent.ItemFound("potion", 1)))
        assertEquals(listOf("CASH" to 0L), cues(GameEvent.Cash(5, 10, CashKind.DEFEAT)))
        assertEquals(listOf("GOAL" to 0L), cues(GameEvent.Info("Daily goal reached!")))
        assertEquals(listOf("BADGE" to 0L), cues(GameEvent.BadgeEarned(1, "B")))
        assertEquals(listOf("BADGE" to 0L), cues(GameEvent.AchievementReached("x", 1, "T", "Bronze")))
        assertEquals(listOf("BADGE" to 0L), cues(GameEvent.TrainerLevelUp(5)))
        assertEquals(listOf("HEALED" to 0L), cues(GameEvent.Healed("u", "A", 5, 15, 20, HealReason.DEFEAT)))
        assertEquals(listOf("HEALED" to 0L), cues(GameEvent.MainHealed("u", "A", 20)))
    }

    @Test fun silentStaysSilent() {
        assertTrue(cues(GameEvent.Cash(5, 10, CashKind.ANSWER)).isEmpty())
        assertTrue(cues(GameEvent.Cash(50, 60, CashKind.DAILY_BONUS)).isEmpty())
        assertTrue(cues(GameEvent.Info("Something else")).isEmpty())
        assertTrue(cues(GameEvent.Defeated(1, "B", 5)).isEmpty())
        assertTrue(cues(GameEvent.Counted(3, 2, 1, 1, false)).isEmpty())
        assertTrue(cues(GameEvent.Splash("X")).isEmpty())
        assertTrue(cues(GameEvent.MoveSkipped("u", "A", "Tackle")).isEmpty())
    }

    @Test fun cueForIsTheFirstCue() {
        val e = GameEvent.Encounter(10, "Wild X", 5, true, Tier.NORMAL, 20, 20, false, false)
        assertTrue(SoundRules.cueFor(e) is Cue.Cry)
        assertEquals(null, SoundRules.cueFor(GameEvent.Info("x")))
    }

    // ---- groups and channels ----

    @Test fun groupsOfAllIds() {
        fun ids(vararg n: String) = n.map { SoundId.valueOf(it) }
        for (id in ids("AGAIN", "HARD", "GOOD", "EASY")) assertEquals("$id", SoundGroup.ANSWER_BUTTONS, id.group)
        assertEquals(SoundGroup.SHOW_ANSWER, SoundId.SHOW_ANSWER.group)
        for (id in ids("TAB", "MENU", "BALL_BTN")) assertEquals("$id", SoundGroup.MENUS, id.group)
        assertEquals(SoundGroup.SHOP, SoundId.BUY.group)
        for (id in ids("HIT", "SUPER", "WEAK", "FAINT", "CATCH", "LEVEL_UP", "MISS", "CHARGE", "THROW", "WOBBLE", "BREAK_FREE", "FLED", "SENT_OUT"))
            assertEquals("$id", SoundGroup.BATTLE, id.group)
        for (id in ids("STAT_UP", "STAT_DOWN", "BURN", "BURN_TICK", "POISON", "POISON_TICK", "PARALYSIS", "SLEEP", "FREEZE", "SHINY", "EVO_START",
            "EVO_DONE", "LEARNED", "ITEM", "CASH", "BADGE", "HEALED", "GOAL")) assertEquals("$id", SoundGroup.MOMENTS, id.group)
        assertEquals(40, SoundId.entries.size)
        assertTrue(SoundId.entries.none { it.group == SoundGroup.CRIES })
    }

    @Test fun channelsOfGroups() {
        for (g in listOf(SoundGroup.ANSWER_BUTTONS, SoundGroup.SHOW_ANSWER, SoundGroup.MENUS, SoundGroup.SHOP)) assertEquals(SoundChannel.BUTTONS, g.channel)
        assertEquals(SoundChannel.BATTLE, SoundGroup.BATTLE.channel)
        assertEquals(SoundChannel.BATTLE, SoundGroup.MOMENTS.channel)
        assertEquals(SoundChannel.CRIES, SoundGroup.CRIES.channel)
        assertEquals(7, SoundGroup.entries.size)
    }

    @Test fun everySoftSoundHasAnIdAndFile() {
        for (s in SoftSound.entries) assertEquals(s.name, SoundId.valueOf(s.name).soft?.name)
        assertEquals(34, SoftSound.entries.size)
        assertEquals("stat_up-v1.wav", SoundId.STAT_UP.file)
        assertTrue(SoundId.entries.filter { it.soft == null }.map { it.name }.toSet() == setOf("HIT", "SUPER", "WEAK", "FAINT", "CATCH", "LEVEL_UP"))
    }

    // ---- shouldPlay with groups ----

    @Test fun groupSwitchAndChannelVolume() {
        val ctx = SoundContext(groupsOff = setOf(SoundGroup.MOMENTS), volumes = mapOf(SoundChannel.BUTTONS to 0, SoundChannel.BATTLE to 50))
        assertFalse(SoundRules.shouldPlay(Cue.Effect(SoundId.STAT_UP), ctx))     // group off
        assertTrue(SoundRules.shouldPlay(Cue.Effect(SoundId.MISS), ctx))          // battle group on, volume 50
        assertFalse(SoundRules.shouldPlay(Cue.Effect(SoundId.GOOD), ctx))         // buttons volume 0
        assertTrue(SoundRules.shouldPlay(Cue.Cry(1), ctx))                        // cries default 70
        val noCries = SoundContext(volumes = mapOf(SoundChannel.CRIES to 0))
        assertFalse(SoundRules.shouldPlay(Cue.Cry(1), noCries))
        assertTrue(SoundRules.shouldPlay(Cue.Effect(SoundId.HIT), noCries))        // battle volume unaffected
        assertFalse(SoundRules.shouldPlay(Cue.Effect(SoundId.TAB), SoundContext(muted = true)))
        assertFalse(SoundRules.shouldPlay(Cue.Effect(SoundId.TAB), SoundContext(ringerNormal = false)))
    }

    // ---- prefs defaults / migration (pure) ----

    private fun bools(vararg kv: Pair<String, Boolean>): (String) -> Boolean? { val m = mapOf(*kv); return { m[it] } }
    private fun ints(vararg kv: Pair<String, Int>): (String) -> Int? { val m = mapOf(*kv); return { m[it] } }

    @Test fun freshInstallDefaults() {
        for (g in SoundGroup.entries) assertTrue("$g", SoundDefaults.groupOn(g, bools()))
        assertEquals(40, SoundDefaults.volume(SoundChannel.BUTTONS, ints()))
        assertEquals(70, SoundDefaults.volume(SoundChannel.BATTLE, ints()))
        assertEquals(70, SoundDefaults.volume(SoundChannel.CRIES, ints()))
    }

    @Test fun oldBattleOffSilencesMomentsToo() {
        val st = bools("battle" to false)
        assertFalse(SoundDefaults.groupOn(SoundGroup.BATTLE, st))
        assertFalse(SoundDefaults.groupOn(SoundGroup.MOMENTS, st))
        assertTrue(SoundDefaults.groupOn(SoundGroup.CRIES, st))
        assertTrue(SoundDefaults.groupOn(SoundGroup.MENUS, st))
        // an own choice for moments wins over the old battle switch
        assertTrue(SoundDefaults.groupOn(SoundGroup.MOMENTS, bools("battle" to false, "g_moments" to true)))
        assertFalse(SoundDefaults.groupOn(SoundGroup.MOMENTS, bools("g_moments" to false)))
        assertFalse(SoundDefaults.groupOn(SoundGroup.CRIES, bools("cries" to false)))
    }

    @Test fun oldVolumeCarriesToBattleAndCries() {
        val st = ints("volume" to 50)
        assertEquals(50, SoundDefaults.volume(SoundChannel.BATTLE, st))
        assertEquals(50, SoundDefaults.volume(SoundChannel.CRIES, st))
        assertEquals(40, SoundDefaults.volume(SoundChannel.BUTTONS, st))
        assertEquals(20, SoundDefaults.volume(SoundChannel.BATTLE, ints("volume" to 50, "vol_battle" to 20)))
        assertEquals(100, SoundDefaults.volume(SoundChannel.CRIES, ints("vol_cries" to 300)))
        assertEquals(0, SoundDefaults.volume(SoundChannel.BUTTONS, ints("vol_buttons" to -4)))
    }

    @Test fun keysAreDistinctAndOldOnesKept() {
        assertEquals(7, SoundGroup.entries.map { SoundDefaults.groupKey(it) }.toSet().size)
        assertEquals("battle", SoundDefaults.groupKey(SoundGroup.BATTLE))
        assertEquals("cries", SoundDefaults.groupKey(SoundGroup.CRIES))
        assertEquals(3, SoundChannel.entries.map { SoundDefaults.volumeKey(it) }.toSet().size)
    }

    // ---- R5-FIX 1: MOMENTS keeps its value when BATTLE is first written ----

    @Test fun freshPrefsKeepMomentsOnWhenBattleIsSwitchedOff() {
        // the settings write: whatever momentsToPersist says, then battle=false
        val stored = HashMap<String, Boolean>()
        SoundDefaults.momentsToPersist { stored[it] }?.let { stored["g_moments"] = it }
        stored["battle"] = false
        assertFalse(SoundDefaults.groupOn(SoundGroup.BATTLE) { stored[it] })
        assertTrue(SoundDefaults.groupOn(SoundGroup.MOMENTS) { stored[it] })
    }

    @Test fun oldBattleOffStillSilencesMomentsAfterTheWrite() {
        val stored = hashMapOf("battle" to false)
        assertEquals(false, SoundDefaults.momentsToPersist { stored[it] })
        SoundDefaults.momentsToPersist { stored[it] }?.let { stored["g_moments"] = it }
        stored["battle"] = true // later, battle is switched back on: moments stays as it was
        assertFalse(SoundDefaults.groupOn(SoundGroup.MOMENTS) { stored[it] })
    }

    @Test fun momentsOwnKeyIsNotTouched() {
        assertEquals(null, SoundDefaults.momentsToPersist(bools("g_moments" to false, "battle" to true)))
        assertEquals(null, SoundDefaults.momentsToPersist(bools("g_moments" to true)))
    }

    @Test fun readsNeverNeedAWrite() {
        // groupOn is a pure function of the stored values: reading twice gives the same answer
        val st = bools("battle" to false)
        assertEquals(SoundDefaults.groupOn(SoundGroup.MOMENTS, st), SoundDefaults.groupOn(SoundGroup.MOMENTS, st))
    }

    // ---- R5-FIX 2: cue delays follow the pace factor ----

    @Test fun cueDelayIsScaledByThePaceOnce() {
        assertEquals(2250L, SoundRules.scaledDelay(2250, 1.0))
        assertEquals(1125L, SoundRules.scaledDelay(2250, 0.5))
        assertEquals(300L, SoundRules.scaledDelay(600, 0.5))
        assertEquals(0L, SoundRules.scaledDelay(0, 0.5))
        assertEquals(150L, SoundRules.scaledDelay(300, 0.5))
    }

    @Test fun autoCatchCueLandsWithTheShortenedBallPhase() {
        val catchCue = SoundRules.cuesFor(GameEvent.Caught("u", 1, "A", 5, false, null, false, true), false).first { (it as? Cue.Effect)?.id == SoundId.CATCH }
        assertEquals(1125L, SoundRules.scaledDelay(catchCue.delayMs, 0.5))
    }

    @Test fun badPaceFactorsAreClamped() {
        assertEquals(0L, SoundRules.scaledDelay(500, -1.0))
        assertEquals(500L, SoundRules.scaledDelay(500, 3.0))
    }
}
