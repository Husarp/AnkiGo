package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Move
import com.husarp.ankigo.game.Type
import org.junit.Assert.*
import org.junit.Test

/** PLAN 266: a charge turn is a move on either side, with the engine's sentence only in its bubble. */
class R19JChargeTileTest {
    private fun charge(player: Boolean, text: String) = GameEvent.Effect(player, text, 30, 30, kind = EffectKind.CHARGE)
    private fun move(model: ActionBarModel, player: Boolean) = (model.half(if (player) BarSide.MINE else BarSide.THEIRS) as HalfState.Moved).move

    @Test fun wildDigNeedsNoCaughtPokemon() {
        val model = ActionBarModel()
        model.observe(charge(false, "Orthworm burrowed its way under the ground!"))
        val m = move(model, false)
        assertEquals("Wild Orthworm", m.who)
        assertEquals("Dig", m.move)
        assertEquals(Type.GROUND, m.type)
        assertEquals(Category.PHYSICAL, m.category)
        assertTrue(m.charging)
        assertEquals("Orthworm burrowed its way under the ground!", m.chargeText)
        val bubble = ActionBarModel.bubble(m)
        assertEquals("Charging", bubble.damage)
        assertTrue(bubble.chips.isEmpty())
        assertEquals(listOf(m.chargeText, "Charging this turn: no damage. Its next action uses this move automatically.",
            "If it cannot act, the charge is lost.", "It can still be hit while charging in AnkiGo."), bubble.lines)
        assertTrue(bubble.spoken().contains(m.chargeText!!))
    }

    @Test fun solarBeamOnBothSides() {
        for (player in listOf(true, false)) {
            val model = ActionBarModel()
            model.observe(charge(player, "Bulbasaur absorbed light!"))
            val m = move(model, player)
            assertEquals("Solar Beam", m.move)
            assertEquals(Type.GRASS, m.type)
            assertEquals(Category.SPECIAL, m.category)
            assertTrue(m.charging)
            assertEquals(3, ActionBarModel.bubble(m).lines.size)
            assertFalse(ActionBarModel.bubble(m).spoken().contains("−0 HP"))
        }
    }

    @Test fun liveMoveDisambiguatesSharedSentences() {
        for (name in listOf("Solar Blade", "Shadow Force")) {
            val model = ActionBarModel { Move(name.lowercase().replace(" ", ""), name,
                if (name == "Solar Blade") Type.GRASS else Type.GHOST, Category.PHYSICAL, 100, 100) }
            model.observe(charge(true, if (name == "Solar Blade") "Bulbasaur absorbed light!" else "Giratina vanished instantly!"))
            assertEquals(name, move(model, true).move)
            assertEquals(Category.PHYSICAL, move(model, true).category)
        }
    }

    @Test fun skullBashKeepsItsChargeTurnStatPill() {
        val model = ActionBarModel()
        model.observe(charge(true, "Squirtle tucked in its head!"))
        model.observe(GameEvent.Effect(true, "Defense rose!", 30, 30, kind = EffectKind.STAT_CHANGE, stat = "def", stages = 1))
        val c = ActionBarModel.bubble(move(model, true))
        assertEquals("Charging", c.damage)
        assertEquals(BubbleTone.STAT_UP, c.chips.single().tone)
        assertEquals("def", c.chips.single().stat)
    }

    @Test fun hitReplacesChargeAndRechargeRemainsANote() {
        val model = ActionBarModel()
        model.observe(charge(true, "Bulbasaur absorbed light!"))
        model.observe(GameEvent.Attack(true, "Bulbasaur", "Orthworm", "Solar Beam", Type.GRASS, Category.SPECIAL,
            12, 1, 1.0, false, false, false, 30, 30, 18, 30))
        assertFalse(move(model, true).charging)
        assertEquals("−12 HP", ActionBarModel.bubble(move(model, true)).damage)
        model.observe(GameEvent.Effect(true, "Bulbasaur must recharge!", 30, 30, kind = EffectKind.CANT_MOVE, stat = "recharge"))
        assertEquals(HalfState.Note("Can't move: recharging"), model.mine)
    }
}
