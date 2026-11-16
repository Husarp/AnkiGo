package com.husarp.ankigo.game

import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Two real study answers per round, default move choice and every production rule enabled. */
internal object R40AFlow {
    val now = 1_791_892_800_000L
    private val statuses = listOf("brn", "psn", "tox", "par", "slp", "frz")
    private val extra = listOf("spikes", "toxicspikes", "stealthrock", "stickyweb", "meanlook", "embargo", "happyhour", "mudsport", "watersport", "iondeluge").associateWith {
        Move(it, it, Type.NORMAL, Category.STATUS, 0, null)
    }
    val data = TestData.data(TestData.world().species.values.toList() + listOf(
        TestData.species(300, "Fastie", base = Stats(250, 40, 100, 40, 100, 150)),
        TestData.species(301, "Slowie", base = Stats(250, 40, 100, 40, 100, 80)),
    ), TestData.MOVES + (R17C4Fixture.MOVES + R17C7.moves + R17C8.moves + R17C5.moves + R17C3.moves).associateBy { it.id } +
        extra + statuses.associate { key -> "apply$key" to Move("apply$key", key, Type.NORMAL, Category.STATUS, 0, null, status = key) } +
        statuses.associate { key -> "chance$key" to Move("chance$key", key, Type.NORMAL, Category.PHYSICAL, 1, null, secondary = Secondary(30, status = key)) } +
        ("chanceconfusion" to Move("chanceconfusion", "Confusing hit", Type.NORMAL, Category.PHYSICAL, 1, null, secondary = Secondary(30, volatileStatus = "confusion"))) +
        ("chanceflinch" to Move("chanceflinch", "Flinching hit", Type.NORMAL, Category.PHYSICAL, 1, null, secondary = Secondary(30, volatileStatus = "flinch", flinch = true))))

    fun game(seed: Int = 0, wildMove: String = "tackle"): Game {
        val s = TestData.state(data, 300, 50, listOf("tackle"))
        s.settings.dailyAverage = 100_000
        s.battle = Battle(TestData.mon(data, 301, 50, listOf(wildMove), "wild").apply { gender = 'F' }, Tier.NORMAL)
        return Game(data, s, Random(seed)) { now }.also { s.day = it.dayKey() }
    }

    fun round(g: Game, first: Int = 3, second: Int = first): List<GameEvent> {
        val b = g.state.battle!!
        val rounds = b.rounds
        val events = mutableListOf<GameEvent>()
        for ((i, button) in listOf(first, second).withIndex()) {
            val part = g.onAnswer(Answer(g.state.totalReviews + 1L, button, now + g.state.totalReviews, neutral = button == Tuning.BUTTON_OK))
            assertEquals(i == 1, part.any { it is GameEvent.RoundStart })
            if (i == 0) assertEquals(rounds, b.rounds)
            events += part
        }
        assertSame("audit must stay in the same battle", b, g.state.battle)
        assertEquals(rounds + 1, b.rounds)
        return events
    }

    fun apply(g: Game, move: String): List<GameEvent> {
        g.state.battle!!.wild.moves.apply { clear(); add(move) }
        val events = round(g, 2)
        g.state.battle!!.wild.moves.apply { clear(); add("tackle") }
        return events
    }

    fun effects(events: List<GameEvent>) = events.filterIsInstance<GameEvent.Effect>()
    fun floats(events: List<GameEvent>) = events.flatMap { FloatTexts.of(it) }
    fun log(events: List<GameEvent>): String = BattleLogBuilder(now = { now }).apply { feed(events) }
        .battles.flatMap { it.rounds }.flatMap { it.lines }.joinToString("\n") { it.text }
    fun board(g: Game) = ConditionBoard().apply { syncFrom(g.state.battle) }
}

class R40AStatusFlowTest {
    private val statuses = listOf("brn", "psn", "tox", "par", "slp", "frz")

    @Test fun everyMajorStatusIsAppliedAndVisibleFromAnAnswerDrivenMove() {
        for (key in statuses) {
            val g = R40AFlow.game(40)
            val events = R40AFlow.apply(g, "apply$key")
            assertEquals(key, g.state.battle!!.player.status)
            val effect = R40AFlow.effects(events).single { it.kind == EffectKind.STATUS_APPLIED }
            assertEquals(key, effect.status)
            assertTrue(effect.onPlayer)
            assertTrue(key, FloatTexts.of(effect).any { it.kind == FloatKind.STATUS && it.onPlayer })
            assertTrue(R40AFlow.log(events).contains(effect.text))
        }
    }

    @Test fun burnPoisonAndToxicTickOnIdleRoundsAndToxicEscalates() {
        for ((key, divisor) in listOf("brn" to 16, "psn" to 8, "tox" to 16)) {
            val g = R40AFlow.game(40)
            R40AFlow.apply(g, "apply$key")
            val max = StatCalc.maxHp(g.data, g.state.main!!)
            for (tick in 2..3) {
                val before = g.state.main!!.hp
                val events = R40AFlow.round(g, Tuning.BUTTON_OK)
                val damage = R40AFlow.effects(events).single { it.kind == EffectKind.STATUS_DAMAGE }
                assertEquals(max * (if (key == "tox") tick else 1) / divisor, damage.amount)
                assertEquals(before - damage.amount, g.state.main!!.hp)
                assertEquals(key, R40AFlow.floats(events).single { it.kind == FloatKind.STATUS_DAMAGE }.stat)
                assertTrue(R40AFlow.log(events).contains(damage.text))
            }
        }
    }

    @Test fun burnHalvesPhysicalDamageAndParalysisChangesTheRealTurnOrder() {
        fun damage(burned: Boolean): Int {
            val g = R40AFlow.game(7)
            if (burned) g.state.battle!!.player.status = "brn"
            return R40AFlow.round(g).filterIsInstance<GameEvent.Attack>().single().damage
        }
        assertEquals(damage(false) / 2, damage(true))
        val g = R40AFlow.game(7)
        val b = g.state.battle!!
        b.player.status = "par"
        val r = Round(g.data, b, g.state.main!!, 1.0, 1.0, Random(7))
        assertTrue(r.speed(true) < r.speed(false))
        assertFalse(R40AFlow.round(g, 3, 2).filterIsInstance<GameEvent.Attack>().first().byPlayer)
    }

    @Test fun confusionActuallySelfHitsOnBothSidesAndNamesTheDamage() {
        for (player in listOf(true, false)) {
            var hits = 0
            for (seed in 0 until 96) {
                val g = R40AFlow.game(seed)
                val b = g.state.battle!!
                val side = if (player) b.player else b.enemy
                side.volatiles["confusion"] = 3
                val before = if (player) g.state.main!!.hp else b.wild.hp
                val events = R40AFlow.round(g, if (player) 3 else 2)
                val stopped = R40AFlow.effects(events).firstOrNull { it.kind == EffectKind.CANT_MOVE && it.stat == "confusion" }
                if (stopped != null) {
                    hits++
                    val loss = R40AFlow.effects(events).single { it.kind == EffectKind.RESIDUAL }
                    assertEquals(player, loss.onPlayer)
                    assertTrue(loss.amount > 0)
                    assertEquals(before - loss.amount, if (player) g.state.main!!.hp else b.wild.hp)
                    assertTrue(events.filterIsInstance<GameEvent.Attack>().none { it.byPlayer == player })
                    assertEquals("Hurt itself", FloatTexts.of(stopped).single().text)
                    assertTrue("damage line must explain its cause", loss.text.contains("confusion"))
                    assertTrue(R40AFlow.log(events).contains(loss.text))
                }
                assertEquals(2, side.volatiles["confusion"])
            }
            assertTrue("seeded self-hits on $player: $hits", hits in 20..44)
        }
    }

    @Test fun confusionAppliedByAMoveSurvivesIdleAnswersUntilItsOwnAttempts() {
        val g = R40AFlow.game(40)
        val applied = R40AFlow.apply(g, "confuseray")
        assertTrue(R40AFlow.floats(applied).any { it.condition == "confusion" })
        val side = g.state.battle!!.player
        val turns = side.volatiles.getValue("confusion")
        assertTrue(turns in Tuning.CONFUSION_TURNS)
        repeat(2) { R40AFlow.round(g, 2); assertEquals(turns, side.volatiles["confusion"]) }
        repeat(turns - 1) { R40AFlow.round(g) }
        assertEquals(1, side.volatiles["confusion"])
        val ends = R40AFlow.round(g)
        assertFalse(side.volatiles.containsKey("confusion"))
        assertTrue(R40AFlow.effects(ends).any { it.text.contains("snapped out") })
        assertTrue(ends.any { it is GameEvent.Attack && it.byPlayer })
    }

    @Test fun chancesMatchTuningThroughStudyAnswersIncludingSecondaryRolls() {
        for ((key, chance) in listOf("confusion" to Tuning.CONFUSION_SELF_HIT, "attract" to Tuning.LOVE_STOPS,
            "par" to Tuning.PARA_SKIP, "slp" to 1.0 - Tuning.WAKE_CHANCE, "frz" to 1.0 - Tuning.FREEZE_THAW)) {
            var stopped = 0
            for (seed in 0 until 512) {
                val g = R40AFlow.game(seed)
                val s = g.state.battle!!.player
                if (key == "confusion" || key == "attract") s.volatiles[key] = 3 else s.status = key
                val events = R40AFlow.round(g)
                if (R40AFlow.effects(events).any { it.kind == EffectKind.CANT_MOVE && it.stat == key }) stopped++
            }
            println("R40-a $key: $stopped/512 stopped actions (expected $chance)")
            assertEquals(key, chance, stopped / 512.0, .06)
        }
        for (key in statuses + listOf("confusion", "flinch")) {
            var applied = 0
            for (seed in 0 until 512) {
                val g = R40AFlow.game(seed, "chance$key")
                if (R40AFlow.effects(R40AFlow.round(g, 2)).any {
                    it.kind == EffectKind.STATUS_APPLIED || (it.kind == EffectKind.VOLATILE && it.stat == key)
                }) applied++
                // Flinch starts quietly and is cleared at round end; exercise its roll with both sides acting below.
                if (key == "flinch") {
                    g.state.main!!.moves.apply { clear(); add("chanceflinch") }
                    if (R40AFlow.effects(R40AFlow.round(g, 3, 2)).any { it.kind == EffectKind.CANT_MOVE && it.stat == key }) applied++
                }
            }
            println("R40-a secondary $key: $applied/512 applied (expected 0.30)")
            assertEquals("secondary $key", .30, applied / 512.0, .06)
        }
    }

    @Test fun wakingAndThawingAreVisibleAndPermitTheSameAction() {
        for ((key, word) in listOf("slp" to "Woke up", "frz" to "Thawed")) {
            val results = (0 until 32).map { seed ->
                val g = R40AFlow.game(seed); g.state.battle!!.player.status = key
                R40AFlow.round(g)
            }
            assertTrue(results.any { events -> R40AFlow.effects(events).any { it.kind == EffectKind.CANT_MOVE } })
            val cured = results.first { events -> R40AFlow.effects(events).any { it.kind == EffectKind.STATUS_CURED } }
            assertTrue(cured.any { it is GameEvent.Attack && it.byPlayer })
            assertTrue(R40AFlow.floats(cured).any { it.text == word })
        }
    }

    @Test fun infatuationAndFlinchApplyFromMovesAndStopTheCorrectActions() {
        val g = R40AFlow.game(5)
        val start = R40AFlow.apply(g, "attract")
        assertTrue(R40AFlow.floats(start).any { it.condition == "attract" })
        assertTrue(R40AFlow.board(g).of(ConditionMarks.Slot.OURS).any { it.key == "attract" })
        val tries = (0 until 8).flatMap { R40AFlow.round(g) }
        assertTrue(R40AFlow.effects(tries).any { it.kind == EffectKind.CANT_MOVE && it.stat == "attract" })
        assertTrue(tries.any { it is GameEvent.Attack && it.byPlayer })
        for (fast in listOf(true, false)) {
            val f = R40AFlow.game(3)
            if (fast) f.state.main!!.moves.apply { clear(); add("bite") }
            else f.state.battle!!.wild.moves.apply { clear(); add("bite") }
            val events = R40AFlow.round(f, 3, 2)
            val stopped = R40AFlow.effects(events).filter { it.stat == "flinch" && it.kind == EffectKind.CANT_MOVE }
            assertEquals(if (fast) 1 else 0, stopped.size)
            if (fast) assertEquals("Flinched", FloatTexts.of(stopped.single()).single().text)
            assertTrue(f.state.battle!!.enemy.volatiles["flinch"] == null)
        }
    }
}
