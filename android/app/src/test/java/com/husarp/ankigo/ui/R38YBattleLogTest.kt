package com.husarp.ankigo.ui

import com.husarp.ankigo.game.*
import org.junit.Assert.*
import org.junit.Test

class R38YBattleLogTest {
    private var now = 0L
    private fun encounter(name: String = "Duck") = GameEvent.Encounter(54, name, 25, false, Tier.NORMAL, 60, 60, false, false)
    private fun log() = BattleLogBuilder(now = { ++now }).apply { feed(encounter()) }

    @Test fun everyMoveEffectSwitchItemAndAlertStaysInChronologicalOrderThroughAResult() {
        val b = log()
        b.feed(listOf(GameEvent.RoundStart(1.5, 10, true),
            GameEvent.Attack(true, "Mouse", "Duck", "Thunder Shock", Type.ELECTRIC, Category.SPECIAL, 18, 1, 2.0, false, false, false, 42, 60, 38, 50),
            GameEvent.Effect(false, "Duck was paralysed!", 42, 60, "par"),
            GameEvent.SwitchQueued("next", "Bird"), GameEvent.MainChanged("next", "Bird"),
            GameEvent.BerryActivated("oran-berry"), GameEvent.Info("Used Potion on Bird")))
        b.alert("Bird is trapped for 2 more rounds.")
        b.feed(listOf(GameEvent.Fled("Duck")))
        val entries = b.battles.single().entries
        assertEquals(listOf("A wild Duck appeared · Lv 25 · 60/60 HP", "Mouse used Thunder Shock · 18 damage · super effective · Duck 42/60 HP",
            "Duck was paralysed!", SwitchQueue.queuedText("Bird"), "Switched to Bird", "Oran Berry activated", "Used Potion on Bird",
            "Battle alert · Bird is trapped for 2 more rounds.", "Duck left"), entries.map { it.text })
        assertEquals(0, entries.first().round)
        assertTrue(entries.drop(1).all { it.round == 1 })
        assertEquals(entries, BattleLogCodec.decode(BattleLogCodec.encode(b.battles)).single().entries)
        assertEquals(entries, b.battles.single().copy().entries)
    }

    @Test fun catchingBeforeTheFirstAttackKeepsTheResultAfterTheThrowInTheSameSection() {
        val b = log()
        b.feed(listOf(GameEvent.BallThrow("poke-ball", 100, true, 3),
            GameEvent.Caught("caught", 54, "Duck", 25, false, "poke-ball", false, true)))
        val entries = b.battles.single().entries
        assertEquals(listOf(0, 1, 1), entries.map { it.round })
        assertTrue(entries.last().text.startsWith("Caught Duck"))
    }

    @Test fun newFightHasOnlyItsOwnEventsAndOutOfBattleMessagesAreIgnored() {
        val b = log()
        b.alert("Blocked switch")
        b.feed(listOf(GameEvent.Fled("Duck")))
        b.alert("No fight")
        b.feed(listOf(GameEvent.Info("Shop purchase")))
        b.feed(encounter("Bird"))
        assertEquals(listOf("A wild Bird appeared · Lv 25 · 60/60 HP"), b.battles.last().entries.map { it.text })
        assertFalse(b.battles.flatMap { it.entries }.any { "Shop" in it.text || "No fight" in it.text })
    }

    @Test fun fullCurrentFightSurvivesTheLegacyRoundLimit() {
        val b = log()
        repeat(170) { round ->
            b.feed(listOf(GameEvent.RoundStart(1.0, 10, true), GameEvent.Effect(false, "Effect $round", 60, 60)))
        }
        assertEquals(171, b.battles.single().entries.size)
        assertEquals("Effect 0", b.battles.single().entries[1].text)
        assertEquals("Effect 169", b.battles.single().entries.last().text)
    }

    @Test fun oldLogsRemainReadableAfterANewEventOrAlert() {
        val old = LogBattle(1, "Duck", 25, false).apply {
            rounds += LogRound(RoundKind.ROUND, 1.0).apply { lines += LogLine(Tone.PLAIN, "Old move") }
        }
        val b = BattleLogBuilder(battles = mutableListOf(old))
        b.alert("New alert")
        b.feed(GameEvent.Effect(false, "New effect", 60, 60))
        assertEquals(listOf("A wild Duck appeared · Lv 25", "Old move", "Battle alert · New alert", "New effect · HP 60/60"), old.entries.map { it.text })
    }

    @Test fun successfulHealingItemsKeepTheirMessageAndLogTheFreeAttackSeparately() {
        val data = TestData.world()
        val state = TestData.state(data, 25, 20, listOf("tackle"))
        val g = Game(data, state)
        state.battle = Battle(TestData.mon(data, 54, 20, listOf("tackle"), "wild"), Tier.NORMAL)
        state.main!!.hp = 1
        Economy.give(state, "potion")
        val result = g.useItem("potion", state.main!!.uid)
        assertTrue(result.ok)
        assertTrue(result.message.startsWith(result.battleMessage!!))
        val b = log()
        b.message(result.battleMessage!!)
        b.feed(result.events)
        assertTrue(b.battles.first().entries.any { it.text == result.battleMessage })
        assertTrue(b.battles.first().entries.any { it.text.contains("one free move") })
        assertEquals(1, result.events.filterIsInstance<GameEvent.Attack>().count { !it.byPlayer })
        assertFalse(b.battles.first().entries.any { it.text.startsWith("Battle alert") })
    }

    @Test fun onlyBattleActionFailuresAreMarkedAsAlerts() {
        val data = TestData.world()
        val state = TestData.state(data, 25, 20, listOf("tackle"))
        val g = Game(data, state)
        state.battle = Battle(TestData.mon(data, 54, 20, listOf("tackle"), "wild"), Tier.NORMAL)
        state.battle!!.player.volatiles["switchtrap"] = 1
        val trapped = g.cycleMain()
        assertFalse(trapped.ok)
        assertTrue(trapped.battleAlert)
        assertFalse(g.rename("missing", "Name").battleAlert)
        val failedItem = g.useItem("potion", state.main!!.uid)
        assertFalse(failedItem.ok)
        assertTrue(failedItem.battleAlert)
        state.battle = null
        assertFalse(g.useItem("potion", state.main!!.uid).battleAlert)
    }
}
