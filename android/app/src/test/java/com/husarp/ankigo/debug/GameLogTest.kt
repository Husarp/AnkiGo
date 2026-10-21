package com.husarp.ankigo.debug

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

/** R13 Part E: the game's lines in the debug log are readable (names, moves, items, the event's own text), short and one line each. */
class GameLogTest {
    @Test fun eventsAreReadableWithNames() {
        val events = listOf(
            GameEvent.Attack(true, "Sparky", "Ratty", "Thunderbolt", Type.ELECTRIC, Category.SPECIAL, 7, 1, 2.0, true, false, false, 13, 20, 18, 21),
            GameEvent.Caught("u1", 16, "Pidgey", 5, false, "great-ball", true, true),
            GameEvent.Evolved("u2", "Pichu", "Pikachu", 25), GameEvent.Info("Daily goal reached!"),
            GameEvent.Ignored("Answer repeated after an undo"), GameEvent.Effect(true, "Sparky is paralyzed!", 10, 20, "par", EffectKind.STATUS_APPLIED),
            GameEvent.BadgeEarned(1, "100 cards in one day"), GameEvent.ItemFound("super-potion", 2),
            GameEvent.SwitchHit("Ratty", "Sparky", 13, 20, "potion"), GameEvent.SwitchHit("Ratty", "Birdie", 13, 20),
        )
        val text = events.map(GameEventText::event)
        assertEquals("Attack mine Sparky -> Ratty: Thunderbolt ELECTRIC/SPECIAL dmg=7 eff=2.0 target=13/20 user=18/21 crit", text[0])
        assertEquals("Caught Pidgey #16 Lv 5 ball=Great Ball new to-team", text[1])
        assertEquals("Evolved Pichu -> Pikachu #25", text[2])
        assertEquals("Info: Daily goal reached!", text[3])
        assertEquals("Ignored: Answer repeated after an undo", text[4])
        assertTrue(text[5], text[5].contains("Sparky is paralyzed!") && text[5].contains("status=par"))
        assertEquals("BadgeEarned #1 100 cards in one day", text[6])
        assertEquals("ItemFound Super Potion (now 2)", text[7])
        assertEquals("TurnHit item Potion on Sparky hp=13/20; Ratty attacks once", text[8])
        assertEquals("TurnHit switch to Birdie hp=13/20; Ratty attacks once", text[9])
        for (source in listOf("live", "study", "catch-up"))
            assertEquals("Answer source=$source card=123 button=4", GameEventText.answer(source, 123, 4))
        assertEquals("Answer source=unknown card=123 button=3", GameEventText.answer("anything else", 123, 3))
    }

    @Test fun aLineIsOneShortLine() {
        val long = GameEventText.event(GameEvent.Info("a\nb" + "x".repeat(900)))
        assertFalse(long.contains('\n'))
        assertTrue(long.length <= 400)
    }
}
