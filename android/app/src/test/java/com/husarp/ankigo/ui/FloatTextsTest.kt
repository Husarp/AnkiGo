package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatTextsTest {
    private val on = FloatContext(crits = true)
    private val off = FloatContext(crits = false)

    private fun attack(
        byPlayer: Boolean = true, damage: Int = 12, hits: Int = 1, eff: Double = 1.0, crit: Boolean = false,
        missed: Boolean = false, failed: Boolean = false, category: Category = Category.PHYSICAL,
    ) = GameEvent.Attack(byPlayer, "A", "B", "Move", Type.NORMAL, category, damage, hits, eff, crit, missed, failed, 5, 20, 10, 20)

    private fun fx(kind: EffectKind, onPlayer: Boolean = true, status: String? = null, stat: String? = null, stages: Int = 0, amount: Int = 0, text: String = "t") =
        GameEvent.Effect(onPlayer, text, 5, 20, status, kind, stat, stages, amount)

    private fun List<FloatText>.pairs() = map { it.text to it.kind }

    @Test fun plainHitIsJustTheNumberAtTheTarget() {
        val r = FloatTexts.of(attack(byPlayer = true), on)
        assertEquals(listOf(FloatText(false, "-12", FloatKind.DAMAGE)), r) // our hit lands on the wild one
        assertEquals(listOf(FloatText(true, "-12", FloatKind.DAMAGE)), FloatTexts.of(attack(byPlayer = false), on))
    }

    @Test fun missIsAtTheTarget() {
        assertEquals(listOf(FloatText(false, "Miss", FloatKind.MISS)), FloatTexts.of(attack(byPlayer = true, damage = 0, missed = true), on))
        assertEquals(listOf(FloatText(true, "Miss", FloatKind.MISS)), FloatTexts.of(attack(byPlayer = false, damage = 0, missed = true), on))
    }

    @Test fun failedShowsNothing() {
        assertEquals(emptyList<FloatText>(), FloatTexts.of(attack(damage = 0, failed = true, category = Category.STATUS), on))
        assertEquals(emptyList<FloatText>(), FloatTexts.of(attack(damage = 0, failed = true), on))
    }

    @Test fun immuneIsNoEffectButAStatusMoveIsNot() {
        assertEquals(listOf("No effect" to FloatKind.NO_EFFECT), FloatTexts.of(attack(damage = 0, eff = 0.0), on).pairs())
        assertEquals(emptyList<FloatText>(), FloatTexts.of(attack(damage = 0, eff = 0.0, category = Category.STATUS), on))
    }

    @Test fun superAndResisted() {
        assertEquals(listOf("-30" to FloatKind.BIG_DAMAGE, "Super effective!" to FloatKind.SUPER), FloatTexts.of(attack(damage = 30, eff = 2.0), on).pairs())
        assertEquals(listOf("-3" to FloatKind.DAMAGE, "Not very effective" to FloatKind.NOT_VERY), FloatTexts.of(attack(damage = 3, eff = 0.5), on).pairs())
        assertEquals(listOf("-60" to FloatKind.BIG_DAMAGE, "Super effective!" to FloatKind.SUPER), FloatTexts.of(attack(damage = 60, eff = 4.0), on).pairs())
        assertEquals(listOf("-1" to FloatKind.DAMAGE, "Not very effective" to FloatKind.NOT_VERY), FloatTexts.of(attack(damage = 1, eff = 0.25), on).pairs())
    }

    @Test fun critOnlyWhenCritsAreOn() {
        assertEquals(listOf("-18" to FloatKind.BIG_DAMAGE, "Critical!" to FloatKind.CRIT), FloatTexts.of(attack(damage = 18, crit = true), on).pairs())
        assertEquals(listOf("-18" to FloatKind.BIG_DAMAGE), FloatTexts.of(attack(damage = 18, crit = true), off).pairs())
        assertEquals(listOf("-40" to FloatKind.BIG_DAMAGE, "Super effective!" to FloatKind.SUPER, "Critical!" to FloatKind.CRIT),
            FloatTexts.of(attack(damage = 40, eff = 2.0, crit = true), on).pairs())
    }

    @Test fun defaultContextFollowsTheTuning() {
        // crits are off in the shipped tuning: no "Critical!" with the default context
        assertEquals(1, FloatTexts.of(attack(crit = true)).size)
    }

    @Test fun multiHitIsOneTotalNumber() {
        assertEquals(listOf("-27" to FloatKind.DAMAGE), FloatTexts.of(attack(damage = 27, hits = 3), on).pairs())
    }

    @Test fun zeroDamageHitShowsNothing() {
        assertEquals(emptyList<FloatText>(), FloatTexts.of(attack(damage = 0, eff = 2.0), on))
    }

    @Test fun statusApplied() {
        val names = mapOf("brn" to "Burned", "par" to "Paralyzed", "psn" to "Poisoned", "tox" to "Badly poisoned", "slp" to "Fell asleep", "frz" to "Frozen")
        for ((k, n) in names) {
            assertEquals(listOf(FloatText(false, n, FloatKind.STATUS)), FloatTexts.of(fx(EffectKind.STATUS_APPLIED, onPlayer = false, status = k), on))
        }
        assertEquals(emptyList<FloatText>(), FloatTexts.of(fx(EffectKind.STATUS_APPLIED, status = null), on))
    }

    @Test fun curedAndCantMove() {
        assertEquals(listOf("Woke up" to FloatKind.STATUS), FloatTexts.of(fx(EffectKind.STATUS_CURED, stat = "slp"), on).pairs())
        assertEquals(listOf("Thawed" to FloatKind.STATUS), FloatTexts.of(fx(EffectKind.STATUS_CURED, stat = "frz"), on).pairs())
        assertEquals(listOf("Woke up" to FloatKind.STATUS), FloatTexts.of(fx(EffectKind.STATUS_CURED, text = "X woke up!"), on).pairs())
        assertEquals(listOf("Thawed" to FloatKind.STATUS), FloatTexts.of(fx(EffectKind.STATUS_CURED, text = "X thawed out!"), on).pairs())
        for (st in listOf("par", "slp", "frz")) {
            assertEquals(listOf(FloatText(true, "Can't move", FloatKind.STATUS)), FloatTexts.of(fx(EffectKind.CANT_MOVE, status = st), on))
        }
    }

    @Test fun statChangeLabelsAndArrows() {
        fun stat(k: String, n: Int, onPlayer: Boolean = true) = FloatTexts.of(fx(EffectKind.STAT_CHANGE, onPlayer = onPlayer, stat = k, stages = n), on)
        assertEquals(listOf("ATK ↑" to FloatKind.STAT_UP), stat("atk", 1).pairs())
        assertEquals(listOf("DEF ↓" to FloatKind.STAT_DOWN), stat("def", -1).pairs())
        assertEquals(listOf("SPE ↑↑" to FloatKind.STAT_UP), stat("spe", 2).pairs())
        assertEquals(listOf("ACC ↓" to FloatKind.STAT_DOWN), stat("accuracy", -1).pairs())
        assertEquals(listOf("EVA ↑" to FloatKind.STAT_UP), stat("evasion", 1).pairs())
        assertEquals(listOf("SPA ↑↑↑" to FloatKind.STAT_UP), stat("spa", 3).pairs())
        assertEquals(listOf("SPD ↓↓↓" to FloatKind.STAT_DOWN), stat("spd", -3).pairs())
        assertEquals(listOf("ATK ↑↑↑" to FloatKind.STAT_UP), stat("atk", 6).pairs()) // never more than three arrows
        assertEquals(false, stat("atk", 1, onPlayer = false).single().onPlayer)
        assertEquals(emptyList<FloatText>(), stat("atk", 0))
        assertEquals(emptyList<FloatText>(), stat("zzz", 1))
    }

    @Test fun statLimit() {
        assertEquals(listOf("ATK max" to FloatKind.INFO), FloatTexts.of(fx(EffectKind.STAT_LIMIT, stat = "atk", stages = 1), on).pairs())
        assertEquals(listOf("DEF min" to FloatKind.INFO), FloatTexts.of(fx(EffectKind.STAT_LIMIT, stat = "def", stages = -1), on).pairs())
    }

    @Test fun drainHealRecoilAndStatusDamage() {
        assertEquals(listOf(FloatText(true, "+7", FloatKind.HEAL)), FloatTexts.of(fx(EffectKind.DRAIN, amount = 7), on))
        assertEquals(listOf(FloatText(false, "+9", FloatKind.HEAL)), FloatTexts.of(fx(EffectKind.HEAL, onPlayer = false, amount = 9), on))
        assertEquals(emptyList<FloatText>(), FloatTexts.of(fx(EffectKind.HEAL, amount = 0), on)) // already full
        assertEquals(listOf("-4" to FloatKind.DAMAGE), FloatTexts.of(fx(EffectKind.RECOIL, amount = 4), on).pairs())
        assertEquals(listOf("-2" to FloatKind.DAMAGE), FloatTexts.of(fx(EffectKind.STATUS_DAMAGE, amount = 2), on).pairs())
    }

    @Test fun otherEffectShowsNothing() {
        assertEquals(emptyList<FloatText>(), FloatTexts.of(fx(EffectKind.OTHER), on))
    }

    @Test fun againAnswerHesitatesTheOtherButtonsDoNot() {
        fun counted(b: Int) = GameEvent.Counted(b, 0, 1, 0, false)
        assertEquals(listOf(FloatText(true, "Hesitated", FloatKind.HESITATE)), FloatTexts.of(counted(1), on))
        for (b in listOf(0, 2, 3, 4)) assertEquals(emptyList<FloatText>(), FloatTexts.of(counted(b), on))
    }

    @Test fun otherEventsShowNothing() {
        assertEquals(emptyList<FloatText>(), FloatTexts.of(GameEvent.Splash("W"), on))
        assertEquals(emptyList<FloatText>(), FloatTexts.of(GameEvent.Info("hi"), on))
    }

    @Test fun healedIsOnePlusNAtTheMainAndNothingForZero() {
        fun healed(a: Int) = GameEvent.Healed("u1", "Sparkmouse", a, 20, 30, com.husarp.ankigo.game.HealReason.LEVEL_UP)
        assertEquals(listOf(FloatText(true, "+6", FloatKind.HEAL)), FloatTexts.of(healed(6), on))
        assertEquals(emptyList<FloatText>(), FloatTexts.of(healed(0), on))
    }

    @Test fun statChangeCarriesTheBadgeData() {
        val up = FloatTexts.of(fx(EffectKind.STAT_CHANGE, stat = "spe", stages = 2), on).single()
        assertEquals("SPE" to 2, up.stat to up.stages)
        val down = FloatTexts.of(fx(EffectKind.STAT_CHANGE, stat = "accuracy", stages = -6), on).single()
        assertEquals("ACC" to 3, down.stat to down.stages) // never more than three chevrons
        assertEquals(FloatKind.STAT_DOWN, down.kind)
    }
}
