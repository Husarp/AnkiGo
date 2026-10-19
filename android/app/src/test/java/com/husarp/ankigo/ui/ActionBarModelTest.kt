package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionBarModelTest {
    private fun attack(
        byPlayer: Boolean, move: String = "Zap", type: Type? = Type.ELECTRIC, cat: Category = Category.SPECIAL, damage: Int = 12,
        hits: Int = 1, eff: Double = 1.0, crit: Boolean = false, missed: Boolean = false, failed: Boolean = false, user: String = "Sparkmouse",
    ) = GameEvent.Attack(byPlayer, user, "Target", move, type, cat, damage, hits, eff, crit, missed, failed, 1, 10, 10, 10)

    private fun effect(onPlayer: Boolean, kind: EffectKind, text: String = "x", status: String? = null, stat: String? = null, stages: Int = 0) =
        GameEvent.Effect(onPlayer, text, 5, 10, status, kind, stat, stages)

    private fun encounter() = GameEvent.Encounter(1, "Wildling", 5, false, Tier.NORMAL, 10, 10, false, false)

    private fun moved(m: ActionBarModel, side: BarSide) = (m.half(side) as HalfState.Moved).move
    private fun bubble(e: GameEvent.Attack): BubbleContent = ActionBarModel.bubble(moved(ActionBarModel().also { it.observe(e) }, if (e.byPlayer) BarSide.MINE else BarSide.THEIRS))
    private fun labels(c: BubbleContent) = c.chips.map { it.label }

    // ---------------------------------------------------------------- folding the events

    @Test fun startsEmptyOnBothSides() {
        val m = ActionBarModel()
        assertEquals(HalfState.Empty, m.mine); assertEquals(HalfState.Empty, m.theirs)
    }

    @Test fun anAttackFillsItsUsersHalfOnly() {
        val m = ActionBarModel()
        assertEquals(listOf(BarSide.MINE), m.observe(attack(true, "Flame Burst")))
        assertEquals("Flame Burst", moved(m, BarSide.MINE).move)
        assertEquals("Sparkmouse", moved(m, BarSide.MINE).who)
        assertEquals(HalfState.Empty, m.theirs)
        assertEquals(listOf(BarSide.THEIRS), m.observe(attack(false, "Bite", user = "Gnarlpup")))
        assertEquals("Wild Gnarlpup", moved(m, BarSide.THEIRS).who)   // "Wild" is added once
        m.observe(attack(false, "Bite", user = "Wild Gnarlpup"))
        assertEquals("Wild Gnarlpup", moved(m, BarSide.THEIRS).who)
    }

    @Test fun aNewMoveReplacesTheOldOneOfThatSide() {
        val m = ActionBarModel()
        m.observe(attack(true, "Zap")); m.observe(attack(true, "Spark"))
        assertEquals("Spark", moved(m, BarSide.MINE).move)
    }

    @Test fun anEncounterClearsBothHalves() {
        val m = ActionBarModel()
        m.observe(attack(true)); m.observe(attack(false))
        assertEquals(listOf(BarSide.MINE, BarSide.THEIRS), m.observe(encounter()))
        assertEquals(HalfState.Empty, m.mine); assertEquals(HalfState.Empty, m.theirs)
        // and the window is closed: a late effect attaches to nothing
        assertTrue(m.observe(effect(false, EffectKind.STATUS_APPLIED, status = "brn")).isEmpty())
    }

    @Test fun otherEventsChangeNothing() {
        val m = ActionBarModel()
        m.observe(attack(true))
        assertTrue(m.observe(GameEvent.Fainted(false, "X")).isEmpty())
        assertTrue(m.observe(GameEvent.Defeated(1, "X", 5)).isEmpty())
        assertTrue(m.observe(GameEvent.Info("hello")).isEmpty())
        assertEquals("Zap", moved(m, BarSide.MINE).move)
    }

    @Test fun aSwitchedMainHasNotMovedYet() {
        val m = ActionBarModel()
        m.observe(attack(true)); m.observe(attack(false))
        assertEquals(listOf(BarSide.MINE), m.observe(GameEvent.MainChanged("u", "Newmon")))
        assertEquals(HalfState.Empty, m.mine)
        assertEquals("Zap", moved(m, BarSide.THEIRS).move)
    }

    // ---------------------------------------------------------------- effects attach to the right move

    @Test fun aStatusEffectAttachesToTheMoveItFollows() {
        val m = ActionBarModel()
        m.observe(attack(true, "Ember", Type.FIRE))
        assertEquals(listOf(BarSide.MINE), m.observe(effect(false, EffectKind.STATUS_APPLIED, status = "brn")))
        assertEquals("brn", moved(m, BarSide.MINE).status)
        assertNull(m.half(BarSide.THEIRS).let { (it as? HalfState.Moved)?.move })
    }

    @Test fun effectsAttachToTheLatestAttackOfTheSameBatchOnly() {
        val m = ActionBarModel()
        m.observe(attack(true, "Ember", Type.FIRE))        // mine, no effect
        m.observe(attack(false, "Growl", Type.NORMAL, Category.STATUS, 0))
        m.observe(effect(true, EffectKind.STAT_CHANGE, stat = "atk", stages = -1))   // lowers OUR attack: belongs to theirs
        assertEquals(emptyList<StatShift>(), moved(m, BarSide.MINE).shifts)
        assertEquals(listOf(StatShift("atk", -1)), moved(m, BarSide.THEIRS).shifts)
    }

    @Test fun aNonEffectEventClosesTheWindow() {
        val m = ActionBarModel()
        m.observe(attack(true))
        m.observe(GameEvent.Counted(3, 1, 1, 1, false))   // the next answer
        assertTrue(m.observe(effect(false, EffectKind.STATUS_APPLIED, status = "par")).isEmpty())
        assertNull(moved(m, BarSide.MINE).status)
    }

    @Test fun burnDamageAtTheEndOfARoundAttachesToNothing() {
        val m = ActionBarModel()
        m.observe(attack(false, "Bite"))
        assertTrue(m.observe(effect(true, EffectKind.STATUS_DAMAGE, "Sparkmouse is hurt by its burn!")).isEmpty())
        assertTrue(m.observe(effect(false, EffectKind.RECOIL)).isEmpty())
        assertEquals(emptyList<StatShift>(), moved(m, BarSide.THEIRS).shifts)
        assertNull(moved(m, BarSide.THEIRS).status)
    }

    @Test fun severalStatChangesAllAttach() {
        val m = ActionBarModel()
        m.observe(attack(true, "Dragon Dance", Type.DRAGON, Category.STATUS, 0))
        m.observe(effect(true, EffectKind.STAT_CHANGE, stat = "atk", stages = 1))
        m.observe(effect(true, EffectKind.STAT_CHANGE, stat = "spe", stages = 1))
        m.observe(effect(true, EffectKind.STAT_LIMIT, stat = "def", stages = 1))   // "won't go higher": not a change
        m.observe(effect(true, EffectKind.STAT_CHANGE, stat = "def", stages = 0))
        assertEquals(listOf(StatShift("atk", 1), StatShift("spe", 1)), moved(m, BarSide.MINE).shifts)
    }

    // ---------------------------------------------------------------- notes instead of moves

    @Test fun cantMoveIsThatSidesActionThisTurn() {
        val m = ActionBarModel()
        m.observe(attack(true)); m.observe(attack(false))
        assertEquals(listOf(BarSide.THEIRS), m.observe(effect(false, EffectKind.CANT_MOVE, "Gnarlpup is fully paralyzed!")))
        assertEquals(HalfState.Note("Can't move: paralyzed"), m.theirs)
        assertEquals("Zap", moved(m, BarSide.MINE).move)
        m.observe(effect(true, EffectKind.CANT_MOVE, "Sparkmouse is fast asleep."))
        assertEquals(HalfState.Note("Can't move: asleep"), m.mine)
        m.observe(effect(true, EffectKind.CANT_MOVE, "Sparkmouse is frozen solid!"))
        assertEquals(HalfState.Note("Can't move: frozen"), m.mine)
    }

    @Test fun aNoteDoesNotTakeLaterEffects() {
        val m = ActionBarModel()
        m.observe(effect(true, EffectKind.CANT_MOVE, "Sparkmouse is fully paralyzed!"))
        assertTrue(m.observe(effect(false, EffectKind.STATUS_APPLIED, status = "brn")).isEmpty())
    }

    @Test fun aChargeTurnAndASplashAreNotes() {
        val m = ActionBarModel()
        m.observe(effect(true, EffectKind.CHARGE, "Sparkmouse absorbed light!"))
        assertEquals(HalfState.Note("Sparkmouse absorbed light!"), m.mine)
        assertEquals(listOf(BarSide.THEIRS), m.observe(GameEvent.Splash("Gnarlpup")))
        assertTrue(m.theirs is HalfState.Note)
    }

    // ---------------------------------------------------------------- which lines the bar shows

    @Test fun movesAndTheirEffectsAreSpokenOnly() {
        assertEquals(LineScope.MOVE, ActionBarModel.scope(attack(true)))
        assertEquals(LineScope.MOVE, ActionBarModel.scope(effect(true, EffectKind.OTHER)))
        assertEquals(LineScope.MOVE, ActionBarModel.scope(GameEvent.Splash("X")))
    }

    @Test fun linesAboutOneSideAreSpokenOnly() {
        for (e in listOf(encounter(), GameEvent.Defeated(1, "X", 5), GameEvent.Fled("X"), GameEvent.Fainted(true, "X"),
            GameEvent.LevelUp("u", "X", 5, 10, 10), GameEvent.Evolved("u", "A", "B", 2), GameEvent.MainChanged("u", "X"),
            GameEvent.Info("Wildling broke free!"), GameEvent.BallThrow("poke-ball", 50, true, 2), GameEvent.Charge(1, 1, 2)))
            assertEquals(e.toString(), LineScope.SIDE, ActionBarModel.scope(e))
    }

    @Test fun theAppsOwnNewsIsAboutTheWholeBattle() {
        for (e in listOf(GameEvent.Ignored("undo"), GameEvent.ItemFound("potion", 1),
            GameEvent.TrainerLevelUp(3), GameEvent.DayChanged("d"), GameEvent.Info("Daily goal reached")))
            assertEquals(e.toString(), LineScope.WHOLE, ActionBarModel.scope(e))
    }

    // ---------------------------------------------------------------- the bubble

    @Test fun plainDamageIsInHpLostNotPercent() {
        val c = bubble(attack(true, damage = 23))
        assertEquals("−23 HP", c.damage); assertEquals(DamageTone.PLAIN, c.damageTone)
        assertEquals("Sparkmouse", c.who); assertEquals("Zap", c.move)
        assertTrue(c.chips.isEmpty())
    }

    @Test fun damageToUsIsRed() {
        assertEquals(DamageTone.RED, bubble(attack(false, damage = 9)).damageTone)
        assertEquals(DamageTone.PLAIN, bubble(attack(true, damage = 9)).damageTone)
    }

    @Test fun superEffectiveIsGreenForMeAndRedForThem() {
        val mine = bubble(attack(true, eff = 2.0)).chips.single()
        assertEquals("Super effective ×2", mine.label); assertEquals(BubbleTone.GOOD, mine.tone); assertEquals(ChipIcon.UP, mine.icon)
        val theirs = bubble(attack(false, eff = 2.0)).chips.single()
        assertEquals(BubbleTone.BAD, theirs.tone)
    }

    @Test fun fourTimesAndAQuarter() {
        assertEquals("Super effective ×4", bubble(attack(true, eff = 4.0)).chips.single().label)
        assertEquals("Not very effective ×½", bubble(attack(true, eff = 0.5)).chips.single().label)
        val q = bubble(attack(false, eff = 0.25)).chips.single()
        assertEquals("Not very effective ×¼", q.label); assertEquals(BubbleTone.NEUTRAL, q.tone); assertEquals(ChipIcon.DOWN, q.icon)
    }

    @Test fun noEffectIsMutedWithAnImmuneChip() {
        val c = bubble(attack(false, eff = 0.0, damage = 0))
        assertEquals("No effect", c.damage); assertEquals(DamageTone.MUTED, c.damageTone)
        assertEquals(listOf("Immune ×0"), labels(c)); assertEquals(BubbleTone.IMMUNE, c.chips[0].tone)
    }

    @Test fun aStatusMoveWithNoEffectivenessIsNotImmune() {
        val c = bubble(attack(true, cat = Category.STATUS, eff = 0.0, damage = 0))
        assertEquals(listOf("Status move"), labels(c)); assertNull(c.damage)
    }

    @Test fun aCritHasItsChip() {
        val c = bubble(attack(true, eff = 2.0, crit = true))
        assertEquals(listOf("Super effective ×2", "CRIT!"), labels(c))
        assertEquals(BubbleTone.CRIT, c.chips[1].tone); assertEquals(ChipIcon.CRIT, c.chips[1].icon)
    }

    @Test fun missedHasNoNumber() {
        val c = bubble(attack(true, missed = true, damage = 0))
        assertEquals("Missed", c.damage); assertEquals(DamageTone.MUTED, c.damageTone)
        assertEquals(listOf("No hit"), labels(c)); assertEquals(ChipIcon.MISS, c.chips[0].icon)
    }

    @Test fun failedIsLikeMissed() {
        val c = bubble(attack(false, cat = Category.STATUS, failed = true, damage = 0))
        assertEquals("Failed", c.damage); assertEquals(DamageTone.MUTED, c.damageTone); assertEquals(1, c.chips.size)
    }

    @Test fun multiHitShowsTheTotalAndTheCount() {
        val c = bubble(attack(true, damage = 31, hits = 3))
        assertEquals("−31 HP", c.damage); assertEquals(listOf("×3 hits"), labels(c))
        assertEquals(emptyList<String>(), labels(bubble(attack(true, damage = 31, hits = 1))))
    }

    @Test fun anInflictedStatusIsAChipWithItsName() {
        val m = ActionBarModel()
        m.observe(attack(true, "Ember", Type.FIRE, damage = 9))
        m.observe(effect(false, EffectKind.STATUS_APPLIED, status = "tox"))
        val c = ActionBarModel.bubble(moved(m, BarSide.MINE))
        val st = c.chips.single()
        assertEquals("Badly poisoned", st.label); assertEquals(BubbleTone.STATUS, st.tone); assertEquals("tox", st.status)
        assertEquals("−9 HP", c.damage)
    }

    @Test fun aStatMoveShowsItsChange() {
        val m = ActionBarModel()
        m.observe(attack(false, "Withdraw", Type.WATER, Category.STATUS, 0))
        m.observe(effect(false, EffectKind.STAT_CHANGE, stat = "def", stages = 1))
        m.observe(effect(false, EffectKind.STAT_CHANGE, stat = "atk", stages = -2))
        val c = ActionBarModel.bubble(moved(m, BarSide.THEIRS))
        assertEquals(listOf("Defense ↑", "Attack ↓↓"), labels(c))
        assertTrue(c.chips.all { it.tone == BubbleTone.BOOST }); assertNull(c.damage)
    }

    @Test fun threeStagesStopAtThreeArrows() {
        val m = ActionBarModel()
        m.observe(attack(true, "Swords Dance", Type.NORMAL, Category.STATUS, 0))
        m.observe(effect(true, EffectKind.STAT_CHANGE, stat = "spa", stages = 6))
        assertEquals(listOf("Sp. Atk ↑↑↑"), labels(ActionBarModel.bubble(moved(m, BarSide.MINE))))
    }

    @Test fun aStatusMoveWithNoOutcomeSaysSo() {
        val c = bubble(attack(true, "Splash", Type.NORMAL, Category.STATUS, 0))
        assertEquals(listOf("Status move"), labels(c)); assertEquals(BubbleTone.MUTED, c.chips[0].tone)
    }

    @Test fun aDamagingMoveCanAlsoInflictAndBoost() {
        val m = ActionBarModel()
        m.observe(attack(true, "Power-Up Punch", Type.FIGHTING, Category.PHYSICAL, 14, crit = true, eff = 2.0))
        m.observe(effect(true, EffectKind.STAT_CHANGE, stat = "atk", stages = 1))
        m.observe(effect(false, EffectKind.STATUS_APPLIED, status = "par"))
        assertEquals(listOf("Super effective ×2", "CRIT!", "Paralysed", "Attack ↑"), labels(ActionBarModel.bubble(moved(m, BarSide.MINE))))
    }

    @Test fun theBubbleIsReadOutInOneSentence() {
        assertEquals("Wild Gnarlpup used Bite: −12 HP, Super effective ×2",
            ActionBarModel.bubble(moved(ActionBarModel().also { it.observe(attack(false, "Bite", damage = 12, eff = 2.0, user = "Gnarlpup")) }, BarSide.THEIRS)).spoken())
    }

    @Test fun theDotsSettingParsesAndFallsBack() {
        assertEquals(DotsPlace.RIGHT_END, DotsPlace.parse("RIGHT_END"))
        assertEquals(DotsPlace.MIDDLE, DotsPlace.parse(null)); assertEquals(DotsPlace.MIDDLE, DotsPlace.parse("nonsense"))
    }
}
