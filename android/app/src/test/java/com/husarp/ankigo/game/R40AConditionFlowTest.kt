package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Test

class R40AConditionFlowTest {
    @Test fun leechSeedDrainsAndHealsOnAnswerDrivenRoundsAndHasAChip() {
        val g = R40AFlow.game(40)
        val b = g.state.battle!!
        b.wild.hp -= 100
        val before = g.state.main!!.hp
        val events = R40AFlow.apply(g, "leechseed")
        val loss = R40AFlow.effects(events).single { it.kind == EffectKind.RESIDUAL }
        val heal = R40AFlow.effects(events).single { it.kind == EffectKind.HEAL }
        assertEquals(before - loss.amount, g.state.main!!.hp)
        assertEquals(loss.amount, heal.amount)
        assertFalse(heal.onPlayer)
        assertTrue(R40AFlow.floats(events).any { it.kind == FloatKind.HEAL && !it.onPlayer })
        assertTrue(R40AFlow.board(g).of(ConditionMarks.Slot.OURS).any { it.key == "leechseed" })
        val next = R40AFlow.round(g, Tuning.BUTTON_OK)
        assertTrue(R40AFlow.effects(next).any { it.kind == EffectKind.RESIDUAL && it.amount == loss.amount })
    }

    @Test fun bindingDamagesCountsDownAndVisiblyExpiresWithoutAttacking() {
        val g = R40AFlow.game(40)
        val start = R40AFlow.apply(g, "wrap")
        val side = g.state.battle!!.player
        var left = side.volatiles.getValue("partiallytrapped")
        assertTrue(left in 3..4)
        assertTrue(R40AFlow.floats(start).any { it.condition == "partiallytrapped" })
        while (left > 0) {
            assertEquals(left, R40AFlow.board(g).of(ConditionMarks.Slot.OURS).single { it.key == "partiallytrapped" }.number)
            val events = R40AFlow.round(g, Tuning.BUTTON_OK)
            assertTrue(R40AFlow.effects(events).any { it.kind == EffectKind.RESIDUAL && it.amount > 0 })
            left--
            if (left == 0) assertTrue(R40AFlow.effects(events).any { it.stat == "partiallytrapped" && it.stages == -1 })
        }
        assertFalse(R40AFlow.board(g).all().any { it.key == "partiallytrapped" })
    }

    @Test fun saltCureSyrupBombNightmareAndCurseDoTheirEffects() {
        for (move in listOf("saltcure", "syrupbomb", "nightmare", "curse")) {
            val g = R40AFlow.game(40)
            if (move == "nightmare") {
                g.state.battle!!.player.status = "slp"
                g.state.battle!!.player.volatiles[CoreRules.SLEEP_TURNS] = 3
            }
            if (move == "curse") {
                g.state.battle = Battle(TestData.mon(g.data, 92, 50, listOf("curse"), "ghost"), Tier.NORMAL)
            }
            val events = R40AFlow.apply(g, move)
            assertTrue(move, R40AFlow.board(g).of(ConditionMarks.Slot.OURS).any { it.key == move })
            assertTrue(move, R40AFlow.floats(events).isNotEmpty())
            if (move == "syrupbomb") assertEquals(-1, g.state.battle!!.player.boosts[4])
            else assertTrue(move, R40AFlow.effects(events).any { it.kind == EffectKind.RESIDUAL && it.onPlayer && it.amount > 0 })
        }
    }

    @Test fun aquaRingIngrainWishAndYawnWaitAndResolveThroughAnswers() {
        for (move in listOf("aquaring", "ingrain", "wish")) {
            val g = R40AFlow.game(40)
            g.state.battle!!.wild.hp -= 150
            val start = R40AFlow.apply(g, move)
            assertTrue(move, R40AFlow.board(g).of(ConditionMarks.Slot.WILD).any { it.key == move })
            val next = R40AFlow.round(g, Tuning.BUTTON_OK)
            assertTrue(move, R40AFlow.effects(next).any { it.kind == EffectKind.HEAL && !it.onPlayer && it.amount > 0 })
            assertTrue(move, R40AFlow.floats(next).any { it.kind == FloatKind.HEAL })
            if (move == "wish") {
                assertFalse(R40AFlow.effects(start).any { it.kind == EffectKind.HEAL })
                assertFalse(R40AFlow.board(g).all().any { it.key == "wish" })
            }
        }
        val g = R40AFlow.game(40)
        R40AFlow.apply(g, "yawn")
        assertNull(g.state.battle!!.player.status)
        assertTrue(R40AFlow.board(g).all().any { it.key == "yawn" })
        val events = R40AFlow.round(g, Tuning.BUTTON_OK)
        assertEquals("slp", g.state.battle!!.player.status)
        assertFalse(R40AFlow.board(g).all().any { it.key == "yawn" })
        assertTrue(R40AFlow.floats(events).any { it.text == "Fell asleep" })
    }

    @Test fun berryJuiceAndSweetHeartWaitForCompletedStudyRoundsAndExpire() {
        for (item in listOf("berry-juice", "sweet-heart")) {
            val g = R40AFlow.game(40, "splash")
            val s = g.state; val mon = s.main!!; val max = StatCalc.maxHp(g.data, mon)
            mon.hp = 1; Economy.give(s, item)
            val use = g.useItem(item, mon.uid)
            assertTrue(use.ok)
            val key = if (item == "berry-juice") "heal.regen" else "heal.pending"
            assertTrue(R40AFlow.board(g).all().any { it.key == key })
            val before = mon.hp
            val first = g.onAnswer(Answer(1, 3, R40AFlow.now))
            assertFalse(first.any { it is GameEvent.RoundStart })
            assertEquals(before, mon.hp)
            val second = g.onAnswer(Answer(2, 3, R40AFlow.now))
            val heal = R40AFlow.effects(second).single { it.kind == EffectKind.HEAL }
            assertEquals(max * (if (item == "berry-juice") 10 else 70) / 100, heal.amount)
            assertTrue(R40AFlow.floats(second).any { it.text == Items.name(item) })
            assertTrue(R40AFlow.floats(second).any { it.kind == FloatKind.HEAL })
            if (item == "sweet-heart") assertTrue(second.indexOf(heal) < second.indexOfFirst { it is GameEvent.Attack })
            else repeat(3) { R40AFlow.round(g, Tuning.BUTTON_OK) }
            assertFalse(R40AFlow.board(g).all().any { it.key == key })
            val after = mon.hp
            R40AFlow.round(g, Tuning.BUTTON_OK)
            assertEquals(after, mon.hp)
        }
    }

    @Test fun itemStatConditionsAffectRealAnswerDrivenDamageAndTurnOrderAndSurviveSave() {
        for ((item, stat, factor) in listOf(Triple("soda-pop", Stat.SPE, 1.25), Triple("lemonade", Stat.ATK, 1.25),
            Triple("moomoo-milk", Stat.DEF, 1.25), Triple("energy-powder", Stat.SPE, .75), Triple("energy-root", Stat.ATK, .75))) {
            val g = R40AFlow.game(40)
            val s = g.state; val m = s.main!!; m.hp = 1
            val base = StatCalc.stats(g.data, m)[stat]
            Economy.give(s, item)
            assertTrue(item, g.useItem(item, m.uid).ok)
            assertEquals((base * factor).toInt(), SimpleView.of(g.data, s.battle!!, m).stats(true)[stat])
            val key = HealingItems.boosts.getValue(item)
            assertTrue(item, R40AFlow.board(g).all().any { it.key == key })
            val saved = SaveCodec.decode(SaveCodec.encode(s))
            val resumed = Game(g.data, saved, kotlin.random.Random(40)) { R40AFlow.now }
            R40AFlow.round(resumed)
            assertTrue(item, R40AFlow.board(resumed).all().any { it.key == key })
            assertEquals((base * factor).toInt(), SimpleView.of(g.data, saved.battle!!, saved.main!!).stats(true)[stat])
        }
    }

    @Test fun auraEntersFromAStudyAnswerAffectsAttacksAndHasAChip() {
        for (tier in listOf(Tier.LEGENDARY, Tier.MYTHICAL)) {
            val g = R40AFlow.game(40)
            val s = g.state
            s.battle = null; s.main!!.level = 100; s.main!!.hp = StatCalc.maxHp(g.data, s.main!!)
            s.reviewsToday = 100; s.settings.dailyAverage = 100; s.caught += 151
            s.nextSeed = (0L..100_000L).first { seed ->
                val r = kotlin.random.Random(seed)
                Encounters.roll(g.data, s, Encounters.wildLevel(100, r), r).tier == tier
            }
            val entry = g.onAnswer(Answer(1, 3, R40AFlow.now))
            val encounter = entry.filterIsInstance<GameEvent.Encounter>().single()
            assertNotNull(encounter.auraText)
            assertArrayEquals(intArrayOf(1, 1, 1, 1, 1, 0, 0), s.battle!!.enemy.boosts)
            assertTrue(R40AFlow.board(g).of(ConditionMarks.Slot.WILD).any { it.key == "legendaryaura" })
            s.battle!!.wild.moves.apply { clear(); add("tackle") }
            val events = g.onAnswer(Answer(2, 2, R40AFlow.now))
            assertEquals(2, events.filterIsInstance<GameEvent.Attack>().size)
            assertArrayEquals(intArrayOf(1, 1, 1, 1, 1, 0, 0), s.battle!!.enemy.boosts)
        }
    }
}
