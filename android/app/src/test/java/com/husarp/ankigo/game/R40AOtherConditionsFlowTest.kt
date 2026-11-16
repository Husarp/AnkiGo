package com.husarp.ankigo.game

import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Test

class R40AOtherConditionsFlowTest {
    @Test fun moveControlConditionsApplyExpireAndReallyRestrictTheNextMove() {
        for (move in listOf("taunt", "encore", "disable", "torment", "healblock", "throatchop")) {
            val g = R40AFlow.game(40)
            R40AFlow.round(g)
            val b = g.state.battle!!
            val start = R40AFlow.apply(g, move)
            assertTrue(move, R40AFlow.board(g).of(ConditionMarks.Slot.OURS).any { it.key == move })
            assertTrue(move, R40AFlow.log(start).isNotBlank())
            g.state.main!!.moves.apply { clear(); add(if (move == "throatchop") "hypervoice" else if (move == "taunt" || move == "healblock") "recover" else "tackle") }
            g.state.main!!.hp -= 10
            val events = R40AFlow.round(g)
            val attack = events.filterIsInstance<GameEvent.Attack>().single { it.byPlayer }
            assertEquals(move, if (move == "encore") "tackle" else "struggle", attack.moveId)
            if (move != "torment") {
                repeat(5) { R40AFlow.round(g, Tuning.BUTTON_OK) }
                assertFalse(move, b.player.volatiles.containsKey(move))
                assertFalse(move, R40AFlow.board(g).all().any { it.key == move })
            }
        }
    }

    @Test fun healBlockSuppressesBothMoveAndTimedItemHealing() {
        for (item in listOf("berry-juice", "sweet-heart")) {
            val g = R40AFlow.game(40); val m = g.state.main!!
            R40AFlow.apply(g, "healblock")
            g.state.battle!!.wild.moves.apply { clear(); add("splash") }
            m.hp = 1; Economy.give(g.state, item)
            assertTrue(g.useItem(item, m.uid).ok)
            val events = R40AFlow.round(g, Tuning.BUTTON_OK)
            assertFalse(R40AFlow.effects(events).any { it.kind == EffectKind.HEAL })
            assertTrue(R40AFlow.board(g).all().any { it.key == "healblock" })
            val before = m.hp
            R40AFlow.round(g, Tuning.BUTTON_OK)
            assertEquals(before, m.hp)
        }
    }

    @Test fun substituteCostsHpAndTakesHitsWithoutLosingItsOwnersHp() {
        val g = R40AFlow.game(40)
        val events = R40AFlow.apply(g, "substitute")
        assertTrue(R40AFlow.floats(events).any { it.condition == "substitute" })
        val hp = g.state.battle!!.wild.hp
        val decoy = g.state.battle!!.enemy.volatiles.getValue("substitute")
        val hit = R40AFlow.round(g)
        assertEquals(hp, g.state.battle!!.wild.hp)
        assertTrue(g.state.battle!!.enemy.volatiles.getValue("substitute") < decoy)
        assertTrue(R40AFlow.effects(hit).any { it.stat == "substitute" && it.text.contains("took damage") })
    }

    @Test fun screensTailwindSafeguardAndMistHaveEffectsAndChipsDuringStudy() {
        for (move in listOf("reflect", "lightscreen", "safeguard", "mist", "tailwind")) {
            val g = R40AFlow.game(40)
            val start = R40AFlow.apply(g, move)
            assertTrue(move, R40AFlow.board(g).of(ConditionMarks.Slot.WILD).any { it.key == move })
            assertTrue(move, R40AFlow.floats(start).any { it.condition == move })
            val base = R40AFlow.game(40)
            if (move == "reflect" || move == "lightscreen") {
                if (move == "lightscreen") for (game in listOf(g, base)) game.state.main!!.moves.apply { clear(); add("watergun") }
                val reduced = R40AFlow.round(g).filterIsInstance<GameEvent.Attack>().single().damage
                val full = R40AFlow.round(base).filterIsInstance<GameEvent.Attack>().single().damage
                assertTrue(move, reduced < full)
            } else if (move == "tailwind") {
                val b = g.state.battle!!
                val r = Round(g.data, b, g.state.main!!, 1.0, 1.0, kotlin.random.Random(40))
                assertTrue(r.speed(false) > r.speed(true))
            } else {
                g.state.main!!.moves.apply { clear(); add(if (move == "mist") "growl" else "applypsn"); add("tackle") }
                // Stay within the screen's lifetime while the production chooser picks moves.
                val attempts = (0 until 2).flatMap { R40AFlow.round(g) }
                if (move == "mist") assertEquals(0, g.state.battle!!.enemy.boosts[0]) else assertNull(g.state.battle!!.enemy.status)
                assertTrue(attempts.any { it is GameEvent.Attack && it.byPlayer })
            }
            repeat(6) { R40AFlow.round(g, Tuning.BUTTON_OK) }
            assertFalse(move, R40AFlow.board(g).all().any { it.key == move })
        }
    }

    @Test fun groundingAimAndStatConditionsBecomeVisibleAndAffectTheBattleView() {
        for (move in listOf("magnetrise", "telekinesis", "smackdown", "foresight", "miracleeye", "charge", "defensecurl", "minimize", "tarshot", "powertrick", "powershift", "stockpile", "embargo")) {
            val g = R40AFlow.game(40)
            if (move == "smackdown") g.state.main!!.ability = "Levitate"
            val events = R40AFlow.apply(g, move)
            val self = move in listOf("magnetrise", "charge", "defensecurl", "minimize", "powertrick", "powershift", "stockpile")
            val slot = if (self) ConditionMarks.Slot.WILD else ConditionMarks.Slot.OURS
            assertTrue(move, R40AFlow.board(g).of(slot).any { it.key == move })
            assertTrue(move, R40AFlow.log(events).isNotBlank())
            val b = g.state.battle!!; val v = SimpleView.of(g.data, b, g.state.main!!)
            when (move) {
                "magnetrise" -> assertFalse(v.grounded(false))
                "telekinesis" -> assertFalse(v.grounded(true))
                "smackdown" -> assertTrue(v.grounded(true))
                "charge" -> assertEquals(1, b.enemy.boosts[3])
                "defensecurl" -> assertEquals(1, b.enemy.boosts[1])
                "minimize" -> assertEquals(2, b.enemy.boosts[6])
                "tarshot" -> assertEquals(-1, b.player.boosts[4])
                "powertrick", "powershift" -> assertEquals(v.baseStats(false).def, v.stats(false).atk)
                "stockpile" -> assertEquals(1, b.enemy.boosts[1])
            }
        }
    }

    @Test fun weatherTerrainAndRoomsStartAndExpireOnAnswerDrivenRounds() {
        for ((move, key) in listOf("raindance" to "rain", "sunnyday" to "sun", "sandstorm" to "sand", "hail" to "hail", "snowscape" to "snow",
            "electricterrain" to "electric", "grassyterrain" to "grassy", "mistyterrain" to "misty", "psychicterrain" to "psychic",
            "trickroom" to "trickroom", "gravity" to "gravity", "magicroom" to "magicroom", "wonderroom" to "wonderroom",
            "mudsport" to "mudsport", "watersport" to "watersport")) {
            val g = R40AFlow.game(40)
            val events = R40AFlow.apply(g, move)
            assertTrue(move, events.any { it is GameEvent.Attack && it.moveId == move && !it.failed })
            assertTrue(move, R40AFlow.board(g).of(ConditionMarks.Slot.FIELD).any { it.key == key })
            assertTrue(move, R40AFlow.log(events).isNotBlank())
            repeat(6) { R40AFlow.round(g, Tuning.BUTTON_OK) }
            assertFalse(move, R40AFlow.board(g).all().any { it.key == key })
        }
    }

    @Test fun lockedMovesUproarAndRechargeFollowTheNextEligibleAction() {
        for (move in listOf("outrage", "uproar", "hyperbeam")) {
            val g = R40AFlow.game(40)
            R40AFlow.apply(g, move)
            val key = if (move == "outrage") "lockedmove" else if (move == "hyperbeam") "mustrecharge" else "uproar"
            assertTrue(move, g.state.battle!!.enemy.volatiles.containsKey(key))
            val before = g.state.battle!!.enemy.volatiles[key]
            R40AFlow.round(g, Tuning.BUTTON_OK)
            assertEquals(before, g.state.battle!!.enemy.volatiles[key])
            val events = R40AFlow.round(g, 2)
            if (move == "hyperbeam") assertTrue(R40AFlow.floats(events).any { it.text == "Recharging" })
            else assertEquals(move, events.filterIsInstance<GameEvent.Attack>().single().moveId)
        }
    }

    @Test fun hazardsAndTrapsApplyInStudyAndAffectRealSwitches() {
        for (move in listOf("spikes", "toxicspikes", "stealthrock", "stickyweb", "meanlook")) {
            val g = R40AFlow.game(40)
            val start = R40AFlow.apply(g, move)
            val key = if (move == "meanlook") "switchtrap" else move
            assertTrue(move, R40AFlow.board(g).of(ConditionMarks.Slot.OURS).any { it.key == key })
            assertTrue(move, R40AFlow.log(start).isNotBlank())
            val incoming = TestData.mon(g.data, 300, 50, listOf("tackle"), "incoming")
            g.state.mons += incoming; g.state.team += incoming.uid
            g.state.battle!!.wild.moves.apply { clear(); add("splash") }
            val hp = incoming.hp
            val result = g.setMain(incoming.uid)
            assertEquals(move, move != "meanlook", result.ok)
            when (move) {
                "spikes", "stealthrock" -> assertTrue(incoming.hp < hp)
                "toxicspikes" -> assertEquals("psn", g.state.battle!!.player.status)
                "stickyweb" -> assertEquals(-1, g.state.battle!!.player.boosts[4])
                "meanlook" -> {
                    repeat(3) { R40AFlow.round(g, Tuning.BUTTON_OK) }
                    assertTrue(g.setMain(incoming.uid).ok)
                }
            }
        }
    }

    @Test fun snackConditionsModifyStatsAndTheNextHitThenShieldDisappears() {
        for ((item, key) in BattleItems.boosts) {
            val g = R40AFlow.game(40)
            Economy.give(g.state, item)
            assertTrue(g.useItem(item, g.state.main!!.uid).ok)
            assertTrue(item, R40AFlow.board(g).all().any { it.key == key })
            val m = g.state.main!!; val base = StatCalc.stats(g.data, m)
            val view = SimpleView.of(g.data, g.state.battle!!, m)
            when (item) {
                "rage-candy-bar" -> assertEquals((base.atk * 1.1).toInt(), view.stats(true).atk)
                "casteliacone" -> assertEquals((base.spd * 1.25).toInt(), view.stats(true).spd)
                "pewter-crunchies" -> assertEquals((base.spa * 1.25).toInt(), view.stats(true).spa)
            }
            val events = R40AFlow.round(g, 2)
            if (item == "shalour-sable") {
                assertFalse(R40AFlow.board(g).all().any { it.key == key })
                assertTrue(R40AFlow.effects(events).any { it.text.contains("weakened the hit") })
            } else assertTrue(item, R40AFlow.board(g).all().any { it.key == key })
        }
    }

    @Test fun futureSightWaitsTwoMoreStudyRoundsBeforeItsVisibleDamage() {
        val g = R40AFlow.game(40)
        val before = g.state.main!!.hp
        R40AFlow.apply(g, "futuresight")
        assertEquals(before, g.state.main!!.hp)
        assertTrue(R40AFlow.board(g).all().any { it.key == "mp.fs" })
        R40AFlow.round(g, Tuning.BUTTON_OK)
        assertEquals(before, g.state.main!!.hp)
        val landed = R40AFlow.round(g, Tuning.BUTTON_OK)
        assertTrue(g.state.main!!.hp < before)
        assertTrue(R40AFlow.floats(landed).any { it.kind == FloatKind.DAMAGE })
        assertFalse(R40AFlow.board(g).all().any { it.key == "mp.fs" })
    }

    @Test fun perishSongCountsStudyRoundsAndSettlesTheFaint() {
        val g = R40AFlow.game(40)
        R40AFlow.apply(g, "perishsong")
        assertTrue(R40AFlow.board(g).all().any { it.key == "perishsong" && it.number == 3 })
        repeat(2) { R40AFlow.round(g, Tuning.BUTTON_OK) }
        val events = (0..1).flatMap { g.onAnswer(Answer(100L + it, 0, R40AFlow.now, neutral = true)) }
        assertTrue(events.any { it is GameEvent.Fainted })
        assertTrue(R40AFlow.effects(events).any { it.text.contains("perish count fell to zero") })
        assertTrue(g.state.battle?.player?.volatiles?.get("perishsong") == null)
    }

    @Test fun imprisonSealsSharedMovesAndDestinyBondTakesTheAttackerWithIt() {
        val g = R40AFlow.game(40)
        g.state.main!!.moves += "imprison"
        R40AFlow.apply(g, "imprison")
        g.state.battle!!.wild.moves += "imprison"
        assertTrue(R40AFlow.board(g).all().any { it.key == "imprison" })
        val sealed = R40AFlow.round(g)
        assertEquals("struggle", sealed.filterIsInstance<GameEvent.Attack>().single().moveId)
        val d = R40AFlow.game(40, "destinybond")
        val start = R40AFlow.round(d, 3, 2)
        assertTrue(R40AFlow.floats(start).any { it.condition == "destinybond" })
        assertTrue(R40AFlow.board(d).all().any { it.key == "destinybond" })
        d.state.battle!!.wild.hp = 1
        val ended = (0..1).flatMap { d.onAnswer(Answer(100L + it, 3, R40AFlow.now)) }
        assertEquals(2, ended.filterIsInstance<GameEvent.Fainted>().size)
    }

    @Test fun protectBlocksTheRealHitAndAuroraVeilNeedsSnow() {
        val g = R40AFlow.game(40, "protect")
        val hp = g.state.battle!!.wild.hp
        val events = R40AFlow.round(g, 3, 2)
        assertEquals(hp, g.state.battle!!.wild.hp)
        assertTrue(R40AFlow.effects(events).any { it.kind == EffectKind.BLOCKED })
        assertFalse(g.state.battle!!.enemy.volatiles.containsKey("protect"))
        val a = R40AFlow.game(40)
        a.state.battle!!.field.weather = "snow"; a.state.battle!!.field.weatherTurns = 5
        R40AFlow.apply(a, "auroraveil")
        assertTrue(R40AFlow.board(a).all().any { it.key == "auroraveil" })
        val damage = R40AFlow.round(a).filterIsInstance<GameEvent.Attack>().single().damage
        val normal = R40AFlow.round(R40AFlow.game(40)).filterIsInstance<GameEvent.Attack>().single().damage
        assertTrue(damage < normal)
    }

    @Test fun happyHourDoublesSubsequentStudyIncomeAndHasAChip() {
        val g = R40AFlow.game(40)
        R40AFlow.apply(g, "happyhour")
        assertTrue(R40AFlow.board(g).all().any { it.key == "happyhour" })
        val before = g.state.cash
        R40AFlow.round(g)
        val gained = g.state.cash - before
        val base = R40AFlow.game(40)
        val normal = base.state.cash
        R40AFlow.round(base)
        assertEquals((base.state.cash - normal) * 2, gained)
    }

    @Test fun ionDelugeChangesTheLaterAttackAndEndsInTheSameRound() {
        val base = R40AFlow.game(40)
        val moves = base.data.moves + ("iondeluge" to base.data.move("iondeluge")!!.copy(priority = 1))
        val data = GameData(base.data.species, moves, base.data.growth, base.data.itemPrices)
        base.state.battle!!.wild.moves.apply { clear(); add("iondeluge") }
        val g = Game(data, base.state, kotlin.random.Random(40)) { R40AFlow.now }
        val events = R40AFlow.round(g, 3, 2)
        assertEquals(Type.ELECTRIC, events.filterIsInstance<GameEvent.Attack>().single { it.byPlayer }.moveType)
        assertTrue(R40AFlow.floats(events).any { it.condition == "iondeluge" })
        assertFalse(g.state.battle!!.field.rooms.containsKey("iondeluge"))
    }

    @Test fun flashFireAndNeutralizingGasConditionsWorkThroughStudyAnswers() {
        val g = R40AFlow.game(40)
        g.state.main!!.ability = "Flash Fire"
        val before = g.state.main!!.hp
        val absorbed = R40AFlow.apply(g, "ember")
        assertEquals(before, g.state.main!!.hp)
        assertTrue(R40AFlow.board(g).all().any { it.key == "ab.flashfire" })
        assertTrue(R40AFlow.floats(absorbed).any { it.text == "Flash Fire" })
        g.state.main!!.moves.apply { clear(); add("ember") }
        val boosted = R40AFlow.round(g).filterIsInstance<GameEvent.Attack>().single().damage
        val base = R40AFlow.game(40)
        base.state.main!!.moves.apply { clear(); add("ember") }
        val plain = R40AFlow.round(base).filterIsInstance<GameEvent.Attack>().single().damage
        assertTrue(boosted > plain)
        val gas = R40AFlow.game(40)
        gas.state.main!!.ability = "Neutralizing Gas"
        gas.state.battle!!.wild.ability = "Intimidate"
        val entry = R40AFlow.round(gas)
        assertTrue(R40AFlow.board(gas).all().any { it.key == "ab.gas" })
        assertEquals(0, gas.state.battle!!.player.boosts[0])
        assertTrue(R40AFlow.floats(entry).any { it.text == "Neutralizing Gas" })
    }

}
