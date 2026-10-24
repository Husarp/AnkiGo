package com.husarp.ankigo.ui

import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** R17C-9a: an ability at work floats its name (an Effect with a label), before the HP number when the effect has one. */
class R17C9aFloatTextsTest {
    private fun fx(kind: EffectKind, label: String? = null, amount: Int = 0, onPlayer: Boolean = false) =
        GameEvent.Effect(onPlayer, "text", 20, 30, null, kind, null, 0, amount, label)

    @Test fun aPlainLineWithALabelFloatsTheLabel() {
        assertEquals(listOf(FloatText(false, "Static", FloatKind.INFO)), FloatTexts.of(fx(EffectKind.OTHER, "Static")))
        assertEquals(listOf(FloatText(true, "Sturdy", FloatKind.INFO)), FloatTexts.of(fx(EffectKind.OTHER, "Sturdy", onPlayer = true)))
    }

    @Test fun aPlainLineWithoutALabelStillFloatsNothing() {
        assertEquals(emptyList<FloatText>(), FloatTexts.of(fx(EffectKind.OTHER)))
        assertEquals(emptyList<FloatText>(), FloatTexts.of(fx(EffectKind.CHARGE, "Anything")))
    }

    @Test fun aHealOrALossWithALabelFloatsTheNameThenTheNumber() {
        assertEquals(listOf(FloatText(false, "Volt Absorb", FloatKind.INFO), FloatText(false, "+12 HP", FloatKind.HEAL)), FloatTexts.of(fx(EffectKind.HEAL, "Volt Absorb", 12)))
        assertEquals(listOf(FloatText(false, "Rough Skin", FloatKind.INFO), FloatText(false, "−3 HP", FloatKind.DAMAGE)), FloatTexts.of(fx(EffectKind.RECOIL, "Rough Skin", 3)))
        assertEquals(listOf(FloatText(false, "Dry Skin", FloatKind.INFO), FloatText(false, "−8 HP", FloatKind.DAMAGE)), FloatTexts.of(fx(EffectKind.RESIDUAL, "Dry Skin", 8)))
        // no HP moved: no name either (the line says it)
        assertEquals(emptyList<FloatText>(), FloatTexts.of(fx(EffectKind.HEAL, "Volt Absorb", 0)))
    }

    @Test fun theOtherKindsAreUnchanged() {
        assertEquals(listOf(FloatText(false, "+5 HP", FloatKind.HEAL)), FloatTexts.of(fx(EffectKind.HEAL, null, 5)))
        assertEquals(listOf(FloatText(false, "Protected", FloatKind.INFO)), FloatTexts.of(fx(EffectKind.BLOCKED, "Protected")))
    }
}
