package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** R3-02, PLAN 80: level-up heal, defeat/catch heal of the main, and the bench rest with its exact fraction. */
class HealingTest {
    private val data = TestData.world()
    private val now = 1_790_000_000_000L
    private var nextCard = 5000L

    private inline fun <reified T : GameEvent> List<GameEvent>.of() = filterIsInstance<T>()
    private fun maxHp(m: Mon) = StatCalc.maxHp(data, m)

    /** A Sparkmouse (species 25) whose max HP is exactly [target] (level and HP EVs picked to fit). */
    private fun monWithMax(target: Int, uid: String): Mon {
        for (level in 1..100) for (ev in 0..252) {
            val m = TestData.mon(data, 25, level, listOf("tackle"), uid)
            m.evs = Stats.ZERO.copy(hp = ev)
            if (StatCalc.maxHp(data, m) == target) { m.hp = target; return m }
        }
        error("no Pokémon with max HP $target")
    }

    private fun game(state: GameState, seed: Int = 1) = Game(data, state, Random(seed)) { now }.also { it.state.day = it.dayKey() }

    private fun Game.answer(button: Int = 3, card: Long = nextCard++, catchUp: Boolean = false) = onAnswer(Answer(card, button, now, catchUp))

    private fun Game.fight(species: Int, level: Int, hp: Int?): Mon {
        val w = TestData.mon(data, species, level, listOf("tackle"), uid = "wild")
        if (hp != null) w.hp = hp
        state.battle = Battle(w, Tier.NORMAL, startedAt = now)
        return w
    }

    /** The main (Sparkmouse Lv 20, max HP 47) hurt to [hp], with its XP so close to Lv 21 that any XP levels it up. */
    private fun hurtMain(hp: Int, almostLevelUp: Boolean = false): GameState {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        val main = s.main!!
        main.hp = hp
        if (almostLevelUp) main.xp = Progression.xpToNext(data, main) - 1
        return s
    }

    // ---- the numbers ----

    @Test fun percentsAreWholeHpAndAtLeastOne() {
        assertEquals(10, Healing.share(40, Tuning.LEVEL_UP_HEAL_PERCENT))
        assertEquals(4, Healing.share(40, Tuning.DEFEAT_HEAL_PERCENT))
        assertEquals(49, Healing.share(199, 25)); assertEquals(19, Healing.share(199, 10))
        assertEquals(1, Healing.share(3, 25)); assertEquals(1, Healing.share(5, 10))
    }

    @Test fun levelUpHpAddsTheLevelsHpThenAQuarterAndCapsAtMax() {
        assertEquals(10 + 1 + 12, Healing.levelUpHp(10, 47, 48)) // +1 max HP, 25 % of 48 = 12
        assertEquals(48, Healing.levelUpHp(40, 47, 48)) // 40 + 1 + 12 = 53 -> capped
        assertEquals(48, Healing.levelUpHp(47, 47, 48)) // full stays full (and follows the new max)
        assertEquals(10 + 12, Healing.levelUpHp(10, 48, 48)) // a level that adds no HP still heals the quarter
        assertEquals(0, Healing.levelUpHp(0, 47, 48)) // a fainted Pokémon is not revived by levelling
    }

    // ---- level-up ----

    @Test fun eachLevelHealsTheLevelsHpPlusAQuarterOfTheNewMax() {
        val s = hurtMain(5)
        val main = s.main!!
        val e = mutableListOf<GameEvent>()
        assertEquals(3, Progression.gainXp(data, s, main, 23 * 23 * 23 - 20 * 20 * 20, e)) // exactly Lv 20 -> 23
        assertEquals(listOf(48, 50, 52), listOf(21, 22, 23).map { l -> StatCalc.hp(35, 15, 0, l) })
        assertEquals(listOf(18, 32, 47), e.of<GameEvent.LevelUp>().map { it.hp }) // 5+1+12, 18+2+12, 32+2+13
        assertEquals(listOf(48, 50, 52), e.of<GameEvent.LevelUp>().map { it.maxHp })
        val healed = e.of<GameEvent.Healed>()
        assertEquals(listOf(13, 14, 15), healed.map { it.amount })
        assertEquals(listOf(18, 32, 47), healed.map { it.hp })
        assertTrue(healed.all { it.reason == HealReason.LEVEL_UP && it.uid == main.uid })
        assertEquals(47, main.hp)
        // each Healed follows its own LevelUp
        val order = e.filter { it is GameEvent.LevelUp || it is GameEvent.Healed }.map { it::class.simpleName }
        assertEquals(listOf("LevelUp", "Healed", "LevelUp", "Healed", "LevelUp", "Healed"), order)
    }

    @Test fun levelUpNeverHealsAboveMax() {
        val s = hurtMain(45)
        val main = s.main!!
        val e = mutableListOf<GameEvent>()
        Progression.gainXp(data, s, main, Progression.xpToNext(data, main), e)
        assertEquals(48, main.hp)
        assertEquals(3, e.of<GameEvent.Healed>().single().amount) // only the real change: 45 -> 48
        assertEquals(48, e.of<GameEvent.LevelUp>().single().hp)
    }

    @Test fun aFullMainStillGrowsIntoItsNewMaxWithAHealedEvent() {
        val s = hurtMain(47)
        val e = mutableListOf<GameEvent>()
        Progression.gainXp(data, s, s.main!!, Progression.xpToNext(data, s.main!!), e)
        assertEquals(48, s.main!!.hp)
        assertEquals(1, e.of<GameEvent.Healed>().single().amount)
    }

    @Test fun theExpShareHolderHealsOnLevelUpButSendsNoHealed() {
        val s = hurtMain(47)
        val holder = TestData.mon(data, 16, 10, listOf("tackle"), "holder")
        s.mons += holder; s.team += holder.uid; s.settings.xpShareUid = holder.uid
        holder.hp = 5
        val e = mutableListOf<GameEvent>()
        Progression.gainXp(data, s, holder, Progression.xpToNext(data, holder), e)
        assertEquals(11, holder.level)
        assertTrue(holder.hp > 5)
        assertEquals(holder.hp, e.of<GameEvent.LevelUp>().single().hp)
        assertTrue(e.of<GameEvent.Healed>().isEmpty())
    }

    @Test fun aFaintedPokemonIsNotRevivedByALevel() {
        val s = hurtMain(0)
        val e = mutableListOf<GameEvent>()
        Progression.gainXp(data, s, s.main!!, Progression.xpToNext(data, s.main!!), e)
        assertEquals(0, s.main!!.hp)
        assertTrue(e.of<GameEvent.Healed>().isEmpty())
    }

    // ---- defeat, catch ----

    @Test fun defeatHealsTenPercentAfterTheLevelUpHeal() {
        val s = hurtMain(10, almostLevelUp = true)
        s.caught += 16 // known species: the auto rule defeats it
        val g = game(s)
        g.fight(16, 15, hp = 1)
        g.answer(); val e = g.answer()
        assertEquals(1, e.of<GameEvent.Defeated>().size)
        val healed = e.of<GameEvent.Healed>()
        assertEquals(listOf(HealReason.LEVEL_UP, HealReason.DEFEAT), healed.map { it.reason })
        assertEquals(10 + 1 + 12, healed[0].hp); assertEquals(13, healed[0].amount)
        assertEquals(4, healed[1].amount) // 10 % of 48
        assertEquals(27, healed[1].hp); assertEquals(48, healed[1].maxHp)
        assertEquals(27, s.main!!.hp)
        assertTrue(e.indexOfFirst { it is GameEvent.Healed && it.reason == HealReason.DEFEAT } > e.indexOfLast { it is GameEvent.LevelUp })
    }

    @Test fun anAutoCatchHealsTenPercentToo() {
        val s = hurtMain(10, almostLevelUp = true) // species 54 is new: the auto rule catches it (and it still gives XP)
        val g = game(s)
        g.fight(54, 15, hp = 1)
        g.answer(); val e = g.answer()
        assertEquals(1, e.of<GameEvent.Caught>().size)
        assertEquals(listOf(HealReason.LEVEL_UP, HealReason.CATCH), e.of<GameEvent.Healed>().map { it.reason })
        assertEquals(27, s.main!!.hp)
    }

    @Test fun aBallCatchHealsTenPercent() {
        val s = hurtMain(10)
        s.bag["master-ball"] = 1
        val g = game(s)
        g.fight(54, 15, hp = 30)
        val e = g.throwBall("master-ball")
        assertEquals(1, e.of<GameEvent.Caught>().size)
        val h = e.of<GameEvent.Healed>().single()
        assertEquals(HealReason.CATCH, h.reason)
        assertEquals(4, h.amount); assertEquals(14, h.hp); assertEquals(47, h.maxHp)
        assertEquals(14, s.main!!.hp)
    }

    @Test fun aBallThatBreaksFreeHealsNothing() {
        val s = hurtMain(10)
        s.bag["poke-ball"] = 1
        val chance = Catching.ballChance("poke-ball", data.sp(54).types)
        val seed = (0..500).first { Random(it).nextInt(100) >= chance } // the first draw of the throw misses
        val g = game(s, seed)
        g.fight(54, 15, hp = 30)
        val e = g.throwBall("poke-ball")
        assertTrue(e.of<GameEvent.Caught>().isEmpty())
        assertTrue(e.of<GameEvent.Healed>().isEmpty())
        assertEquals(10, s.main!!.hp)
    }

    @Test fun healingStopsAtMaxHpAndSkipsTheEventWhenNothingIsGained() {
        val main = TestData.mon(data, 25, 20, listOf("tackle"), "m")
        val e = mutableListOf<GameEvent>()
        Healing.healMain(data, main, HealReason.DEFEAT, e)
        assertTrue(e.isEmpty()) // already full
        main.hp = 45
        Healing.healMain(data, main, HealReason.DEFEAT, e)
        assertEquals(47, main.hp)
        assertEquals(2, (e.single() as GameEvent.Healed).amount) // only the 2 that fit
        main.hp = 0
        e.clear()
        Healing.healMain(data, main, HealReason.DEFEAT, e)
        assertTrue(e.isEmpty()); assertEquals(0, main.hp) // fainted: no heal
    }

    @Test fun evolvingStillHealsFullyAndAddsNoDefeatHeal() {
        val s = TestData.state(data, 1, 15, listOf("tackle")) // Leafling 15 evolves at 16
        val main = s.main!!
        main.hp = 5
        main.xp = Progression.xpToNext(data, main) - 1
        s.caught += 19
        val g = game(s)
        g.fight(19, 40, hp = 1)
        g.answer(); val e = g.answer()
        assertEquals(1, e.of<GameEvent.Evolved>().size)
        assertEquals(maxHp(main), main.hp)
        assertEquals(listOf(HealReason.LEVEL_UP), e.of<GameEvent.Healed>().map { it.reason })
    }

    @Test fun aFaintedMainIsStillFullyHealedAndGetsNoHealedEvent() {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        val main = s.main!!
        main.hp = 1
        val g = game(s)
        g.fight(19, 15, hp = 36)
        g.answer(1); val e = g.answer(1)
        assertEquals(1, e.of<GameEvent.MainHealed>().size)
        assertEquals(maxHp(main), main.hp)
        assertTrue(e.of<GameEvent.Healed>().isEmpty())
    }

    @Test fun whenBothFaintTheMainIsFullyHealedAndNothingElseHeals() {
        val moves = TestData.MOVES + ("recoilburn" to Move("recoilburn", "Recoil Burn", Type.NORMAL, Category.PHYSICAL, 40, null, recoil = 100 to 1))
        val d2 = TestData.data(listOf(TestData.species(25), TestData.species(19)), moves)
        val s = GameState(createdAt = 1L, day = "2026-10-13", starterChosen = true)
        val main = TestData.mon(d2, 25, 20, listOf("recoilburn"), "main")
        main.hp = 1; main.xp = Progression.xpToNext(d2, main) - 1
        s.mons += main; s.team += main.uid; s.seen += 25; s.caught += listOf(25, 19)
        val g = Game(d2, s, Random(1)) { now }.also { it.state.day = it.dayKey() }
        val w = TestData.mon(d2, 19, 15, listOf("tackle"), "wild").also { it.hp = 1 }
        s.battle = Battle(w, Tier.NORMAL, startedAt = now)
        g.onAnswer(Answer(1, 3, now)); val e = g.onAnswer(Answer(2, 3, now))
        assertTrue(e.of<GameEvent.Defeated>().isNotEmpty())
        assertTrue(e.of<GameEvent.Fainted>().any { it.player })
        assertEquals(1, e.of<GameEvent.MainHealed>().size)
        assertTrue(e.of<GameEvent.Healed>().isEmpty())
        assertEquals(StatCalc.maxHp(d2, main), main.hp)
    }

    // ---- bench rest ----

    @Test fun maxHp40GainsOneHpEveryFiveAnswersAndNeverEarly() {
        val m = monWithMax(40, "b"); m.hp = 10
        repeat(4) { Healing.rest(data, m) }
        assertEquals(10, m.hp); assertEquals(160, m.restFrac)
        Healing.rest(data, m)
        assertEquals(11, m.hp); assertEquals(0, m.restFrac)
        repeat(50) { Healing.rest(data, m) }
        assertEquals(21, m.hp)
    }

    @Test fun max199IsExactOver200Answers() {
        val m = monWithMax(199, "b"); m.hp = 0
        for (n in 1..200) {
            Healing.rest(data, m)
            assertEquals("answer $n", 199 * n / 200, m.hp)
            assertTrue(m.restFrac in 0..199)
        }
        assertEquals(199, m.hp)
        assertEquals(0, m.restFrac) // full: nothing is kept
    }

    @Test fun aBigMaxHpMayGainMoreThanOnePerAnswerWithoutLosingTheFraction() {
        val m = monWithMax(250, "b"); m.hp = 1
        for (n in 1..40) { // 250 / 200 = 1.25 HP per answer
            Healing.rest(data, m)
            assertEquals("answer $n", 1 + 250 * n / 200, m.hp)
        }
    }

    @Test fun atMaxHpNothingAccumulatesAndReachingItDropsTheLeftover() {
        val m = monWithMax(40, "b")
        m.restFrac = 77
        Healing.rest(data, m)
        assertEquals(40, m.hp); assertEquals(0, m.restFrac)
        m.hp = 39; m.restFrac = 199 // 199 + 40 = 239 -> +1, 39 left over, but full
        Healing.rest(data, m)
        assertEquals(40, m.hp); assertEquals(0, m.restFrac)
    }

    @Test fun anOutOfRangeStoredFractionIsHandled() {
        val m = monWithMax(40, "b"); m.hp = 10; m.restFrac = 5000
        Healing.rest(data, m)
        assertEquals(10 + 1, m.hp) // read as 199: 199 + 40 = 239 -> +1
        assertEquals(39, m.restFrac)
    }

    private fun benchState(vararg benchMax: Int): GameState {
        val s = hurtMain(20)
        benchMax.forEachIndexed { i, mx ->
            val m = monWithMax(mx, "bench$i"); m.hp = 10
            s.mons += m; s.team += m.uid
        }
        val box = monWithMax(40, "box"); box.hp = 10
        s.mons += box
        return s
    }

    @Test fun onlyBenchTeamMembersRest() {
        val s = benchState(40)
        val before = s.main!!.hp
        Healing.restBench(data, s)
        assertEquals(10, s.mon("bench0")!!.hp); assertEquals(40, s.mon("bench0")!!.restFrac)
        assertEquals(before, s.main!!.hp); assertEquals(0, s.main!!.restFrac) // the main doesn't rest
        assertEquals(10, s.mon("box")!!.hp); assertEquals(0, s.mon("box")!!.restFrac) // box Pokémon don't
    }

    @Test fun everyCountedAnswerRestsTheBenchCatchUpToo() {
        val s = benchState(40)
        val g = game(s)
        g.answer(card = 1); g.answer(card = 2, catchUp = true); g.answer(card = 3); g.answer(card = 4, catchUp = true)
        assertEquals(10, s.mon("bench0")!!.hp); assertEquals(160, s.mon("bench0")!!.restFrac)
        g.answer(card = 5)
        assertEquals(11, s.mon("bench0")!!.hp)
        assertEquals(10, s.mon("box")!!.hp)
    }

    @Test fun anIgnoredRedoAnswerDoesNotRest() {
        val s = benchState(40)
        val g = game(s)
        g.answer(card = 1)
        g.onUndo(1, 1)
        val e = g.answer(card = 1)
        assertTrue(e.of<GameEvent.Ignored>().isNotEmpty())
        assertEquals(40, s.mon("bench0")!!.restFrac) // only the first one counted
    }

    @Test fun theFractionSurvivesASaveAndLoadMidWay() {
        val s = benchState(40)
        val g = game(s)
        repeat(4) { g.answer() }
        assertEquals(160, s.mon("bench0")!!.restFrac)
        val loaded = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(160, loaded.mon("bench0")!!.restFrac)
        val g2 = game(loaded)
        g2.answer()
        assertEquals(11, loaded.mon("bench0")!!.hp)
        assertEquals(0, loaded.mon("bench0")!!.restFrac)
    }

    @Test fun benchRestEmitsNoEvent() {
        val s = benchState(40)
        val e = game(s).answer()
        assertTrue(e.of<GameEvent.Healed>().isEmpty())
    }
}
