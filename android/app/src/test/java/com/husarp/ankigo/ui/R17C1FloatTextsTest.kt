package com.husarp.ankigo.ui

import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.sound.Cue
import com.husarp.ankigo.sound.SoundRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R17C-1: the new Effect kinds (VOLATILE, FIELD, RESIDUAL, BLOCKED), the new CANT_MOVE keys, their floats, bar notes and sounds. */
class R17C1FloatTextsTest {
    private fun fx(
        kind: EffectKind, onPlayer: Boolean = true, status: String? = null, stat: String? = null, stages: Int = 0, amount: Int = 0,
        label: String? = null, text: String = "t",
    ) = GameEvent.Effect(onPlayer, text, 5, 20, status, kind, stat, stages, amount, label)

    private fun floats(e: GameEvent) = FloatTexts.of(e, FloatContext(crits = false))

    @Test fun aStartingConditionShowsItsLabelOnce() {
        for (kind in listOf(EffectKind.VOLATILE, EffectKind.FIELD, EffectKind.BLOCKED)) {
            assertEquals(listOf(FloatText(false, "Confused", FloatKind.INFO)), floats(fx(kind, onPlayer = false, stat = "confusion", stages = 1, label = "Confused")))
            assertEquals(listOf(FloatText(true, "Rain", FloatKind.INFO)), floats(fx(kind, stat = "rain", stages = 0, label = "Rain"))) // 0 counts as shown
        }
    }

    @Test fun anEndingConditionOrNoLabelShowsNothing() {
        for (kind in listOf(EffectKind.VOLATILE, EffectKind.FIELD, EffectKind.BLOCKED)) {
            assertEquals(emptyList<FloatText>(), floats(fx(kind, stat = "confusion", stages = -1, label = "Confused")))
            assertEquals(emptyList<FloatText>(), floats(fx(kind, stat = "confusion", stages = 1, label = null)))
        }
    }

    @Test fun residualDamageIsAPlainRedNumber() {
        assertEquals(listOf(FloatText(true, "−6 HP", FloatKind.DAMAGE)), floats(fx(EffectKind.RESIDUAL, amount = 6)))
        assertEquals(listOf(FloatText(false, "−13 HP", FloatKind.DAMAGE)), floats(fx(EffectKind.RESIDUAL, onPlayer = false, amount = 13)))
        assertEquals(emptyList<FloatText>(), floats(fx(EffectKind.RESIDUAL, amount = 0)))
    }

    @Test fun cantMoveReasonsThatAreNotStatuses() {
        val want = mapOf("flinch" to "Flinched", "attract" to "In love", "recharge" to "Recharging", "confusion" to "Confused", "truant" to "Loafing")
        for ((key, text) in want) {
            assertEquals(listOf(FloatText(true, text, FloatKind.STATUS)), floats(fx(EffectKind.CANT_MOVE, stat = key)))
            assertEquals(listOf(FloatText(false, text, FloatKind.STATUS)), floats(fx(EffectKind.CANT_MOVE, onPlayer = false, stat = key)))
        }
        // the status ones are as before
        assertEquals(listOf(FloatText(true, "Fast asleep", FloatKind.STATUS, "slp")), floats(fx(EffectKind.CANT_MOVE, stat = "slp")))
        assertEquals(listOf(FloatText(true, "Can't move", FloatKind.STATUS)), floats(fx(EffectKind.CANT_MOVE, stat = "unknown")))
    }

    @Test fun theBarNotesNameTheReason() {
        val want = mapOf(
            "flinch" to "Can't move: flinched", "attract" to "Can't move: in love", "recharge" to "Can't move: recharging",
            "confusion" to "Hurt itself in confusion", "truant" to "Can't move: loafing around",
            "par" to "Can't move: paralyzed", "slp" to "Can't move: asleep", "frz" to "Can't move: frozen",
        )
        for ((key, note) in want) {
            val m = ActionBarModel()
            m.observe(fx(EffectKind.CANT_MOVE, stat = key, text = "some line"))
            assertEquals(key, HalfState.Note(note), m.mine)
        }
    }

    @Test fun theNewKindsLeaveTheBarAlone() {
        val m = ActionBarModel()
        m.observe(GameEvent.Attack(true, "A", "B", "Tackle", null, com.husarp.ankigo.game.Category.PHYSICAL, 5, 1, 1.0, false, false, false, 5, 20, 20, 20))
        val before = m.mine
        for (kind in listOf(EffectKind.VOLATILE, EffectKind.FIELD, EffectKind.RESIDUAL, EffectKind.BLOCKED)) {
            assertEquals(emptyList<BarSide>(), m.observe(fx(kind, stat = "x", stages = 1, amount = 3, label = "L")))
            assertEquals(before, m.mine)
        }
    }

    @Test fun soundsResidualTicksTheOthersAreSilent() {
        fun names(e: GameEvent) = SoundRules.cuesFor(e, false, false).map { (it as Cue.Effect).id.name }
        assertEquals(listOf("BURN_TICK"), names(fx(EffectKind.RESIDUAL, amount = 6)))
        for (kind in listOf(EffectKind.VOLATILE, EffectKind.FIELD, EffectKind.BLOCKED)) assertTrue(names(fx(kind, stat = "x", stages = 1, label = "L")).isEmpty())
    }

    @Test fun theLabelIsTheLastParameterWithADefault() {
        val e = GameEvent.Effect(true, "x", 1, 2)
        assertEquals(null, e.label)
        // the positional constructor of Adam's local shots file: onPlayer, text, hp, maxHp, status, kind, stat, stages (amount)
        val old = GameEvent.Effect(true, "x", 10, 30, null, EffectKind.CANT_MOVE, "slp", 0)
        assertEquals(null, old.label); assertEquals(0, old.amount)
    }
}
