package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.ConditionBoard
import com.husarp.ankigo.ui.ConditionExplanation
import com.husarp.ankigo.ui.ConditionIcons
import com.husarp.ankigo.ui.ConditionMarks
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R37UHealingTest {
    private val data = TestData.data(TestData.world().species.values.toList() + TestData.species(292, base = Stats(1, 90, 45, 30, 30, 40)))
    private fun state(level: Int = 30) = TestData.state(data, 25, level, listOf("splash")).apply {
        bag.clear()
        battle = Battle(TestData.mon(data, 19, level, listOf("splash"), "wild"), Tier.NORMAL)
    }
    private fun use(s: GameState, item: String, mon: Mon = s.main!!): ActionResult {
        Economy.give(s, item)
        assertTrue(item, ItemUse.canUse(data, s, item, mon))
        return ItemUse.use(data, s, item, mon.uid, Random(0), 0)
    }
    private fun round(s: GameState) = BattleEngine.Round(data, s.battle!!, s.main!!, 1.0, 1.0, Random(0))
    private fun play(s: GameState) = BattleEngine.playRound(data, s.battle!!, s.main!!, 1.0, false, Random(0), mainActs = false)
    private fun checkHeal(item: String, percent: Int) {
        for (level in listOf(1, 30, 100)) {
            val s = state(level); val m = s.main!!; val max = StatCalc.maxHp(data, m); m.hp = 1
            assertTrue(use(s, item).ok)
            assertEquals(item, minOf(max, 1 + (max * percent / 100).coerceAtLeast(1)), m.hp)
            assertEquals(0, s.count(item))
        }
    }
    @Test fun potionHeals25PercentAtEveryLevel() = checkHeal("potion", 25)
    @Test fun superPotionHeals50PercentAtEveryLevel() = checkHeal("super-potion", 50)
    @Test fun hyperPotionHeals75PercentAtEveryLevel() = checkHeal("hyper-potion", 75)
    @Test fun maxPotionHeals100PercentAtEveryLevel() = checkHeal("max-potion", 100)
    @Test fun fullRestoreHeals100PercentAndCuresEveryStatus() {
        checkHeal("full-restore", 100)
        for (status in listOf("brn", "psn", "tox", "par", "slp", "frz")) {
            val s = state(); val side = s.battle!!.player
            side.status = status; side.statusTurns = 3; side.volatiles[CoreRules.SLEEP_TURNS] = 2
            val r = use(s, "full-restore")
            assertTrue(r.ok); assertNull(side.status); assertEquals(0, side.statusTurns)
            assertFalse(side.volatiles.containsKey(CoreRules.SLEEP_TURNS))
            assertEquals(status, r.events.filterIsInstance<GameEvent.Effect>().single { it.kind == EffectKind.STATUS_CURED }.stat)
        }
    }
    @Test fun berryJuiceHeals10NowAndExactlyFourFurtherTurns() {
        val s = state(); val m = s.main!!; val max = StatCalc.maxHp(data, m); m.hp = 1
        use(s, "berry-juice"); assertEquals(1 + max / 10, m.hp)
        for (n in 1..4) {
            assertEquals(5 - n, s.battle!!.player.volatiles["heal.regen"])
            val events = play(s)
            assertEquals(1, events.filterIsInstance<GameEvent.Effect>().count { it.label == "Berry Juice" })
            assertEquals(1 + max / 10 * (n + 1), m.hp)
        }
        assertFalse(s.battle!!.player.volatiles.containsKey("heal.regen"))
        play(s); assertEquals(1 + max / 10 * 5, m.hp)
    }
    @Test fun sweetHeartHealsNothingNowAnd70PercentBeforeNextAttackOnlyOnce() {
        val s = state(); val m = s.main!!; val max = StatCalc.maxHp(data, m); m.hp = 1
        s.battle!!.wild.moves.clear(); s.battle!!.wild.moves += "tackle"
        use(s, "sweet-heart"); assertEquals(1, m.hp); assertEquals(1, s.battle!!.player.volatiles["heal.pending"])
        Economy.give(s, "sweet-heart")
        assertFalse(ItemUse.canUse(data, s, "sweet-heart", m))
        assertFalse(ItemUse.use(data, s, "sweet-heart", m.uid, Random(0), 0).ok)
        assertEquals(1, s.count("sweet-heart"))
        val events = BattleEngine.playRound(data, s.battle!!, m, 1.0, true, Random(0), mainActs = false)
        val heal = events.filterIsInstance<GameEvent.Effect>().single { it.label == "Sweet Heart" }
        assertEquals(max * 70 / 100, heal.amount)
        assertTrue(events.indexOf(heal) < events.indexOfFirst { it is GameEvent.Attack })
        assertFalse(s.battle!!.player.volatiles.containsKey("heal.pending"))
        assertTrue(play(s).filterIsInstance<GameEvent.Effect>().none { it.label == "Sweet Heart" })
    }
    @Test fun sodaPopHeals40AndRaisesSpeed() { checkHeal("soda-pop", 40); checkStat("soda-pop", Stat.SPE, 1.25) }
    @Test fun lemonadeHeals50AndRaisesAttack() { checkHeal("lemonade", 50); checkStat("lemonade", Stat.ATK, 1.25) }
    @Test fun moomooMilkHeals60AndRaisesDefense() { checkHeal("moomoo-milk", 60); checkStat("moomoo-milk", Stat.DEF, 1.25) }
    @Test fun freshWaterHeals40AndClearsOnlyNegativeStages() {
        checkHeal("fresh-water", 40)
        val s = state(); val side = s.battle!!.player
        val values = intArrayOf(-1, 2, -6, 3, 0, -2, -3)
        values.copyInto(side.boosts)
        val result = use(s, "fresh-water")
        assertArrayEquals(intArrayOf(0, 2, 0, 3, 0, 0, 0), side.boosts)
        val board = ConditionBoard(); board.syncFrom(s.battle)
        for (event in result.events.filterIsInstance<GameEvent.Effect>()) board.applyEffect(event, s.battle)
        assertEquals(setOf("stat.def", "stat.spd"), board.all().map { it.key }.toSet())
    }
    @Test fun energyPowderHeals70AndLowersSpeed() { checkHeal("energy-powder", 70); checkStat("energy-powder", Stat.SPE, 0.75) }
    @Test fun energyRootHeals100AndLowersAttack() { checkHeal("energy-root", 100); checkStat("energy-root", Stat.ATK, 0.75) }
    private fun checkStat(item: String, stat: Stat, factor: Double) {
        val s = state(); val m = s.main!!; val base = StatCalc.stats(data, m); m.hp = 1
        use(s, item)
        val expected = base.with(stat, (base[stat] * factor).toInt())
        assertEquals(expected, SimpleView.of(data, s.battle!!, m).stats(true))
        m.hp = 1; use(s, item)
        assertEquals("reusing must not stack", expected, SimpleView.of(data, s.battle!!, m).stats(true))
        s.battle = Battle(TestData.mon(data, 19, 30, listOf("splash"), "next"), Tier.NORMAL)
        assertEquals("next fight resets", base, SimpleView.of(data, s.battle!!, m).stats(true))
    }
    @Test fun allThirteenApplyOnlyHealingOutsideBattleAndClampToMax() {
        for ((item, pct) in HealingItems.percent) {
            val s = state(); s.battle = null; val m = s.main!!; val max = StatCalc.maxHp(data, m); m.hp = 1
            use(s, item)
            assertEquals(item, minOf(max, 1 + (max * pct / 100).coerceAtLeast(1)), m.hp)
            m.hp = max - 1; use(s, item); assertEquals(item, max, m.hp)
            Economy.give(s, item)
            assertFalse(item, ItemUse.canUse(data, s, item, m))
            assertFalse(item, ItemUse.use(data, s, item, m.uid, Random(0), 0).ok)
            assertEquals(1, s.count(item)); assertNull(s.battle)
        }
    }
    @Test fun oneHpPokemonGetsMinimumOneHpPerNonzeroHealAndNoInstantSweetHeart() {
        val s = state(); val m = TestData.mon(data, 292, 30, listOf("splash"), "shedinja")
        s.mons.clear(); s.mons += m; s.team.clear(); s.team += m.uid
        assertEquals(1, StatCalc.maxHp(data, m))
        for (item in HealingItems.percent.keys - "sweet-heart") { m.hp = 0; use(s, item); assertEquals(item, 1, m.hp) }
        m.hp = 0; Economy.give(s, "sweet-heart"); assertFalse(ItemUse.canUse(data, s, "sweet-heart", m))
    }
    @Test fun allRecipientEffectsSurviveSaveAndRealSwitchesWithoutTransferring() {
        for (item in HealingItems.boosts.keys + listOf("berry-juice", "sweet-heart")) {
            var s = state(); val owner = TestData.mon(data, 16, 30, listOf("splash"), "owner").apply { hp = 1 }
            s.mons += owner; s.team += owner.uid
            use(s, item, owner)
            assertTrue(s.battle!!.player.volatiles.keys.none { it.startsWith("heal.") })
            play(s)
            s = SaveCodec.decode(SaveCodec.encode(s))
            val key = HealingItems.boosts[item] ?: if (item == "berry-juice") "heal.regen" else "heal.pending"
            val before = s.mon(owner.uid)!!.hp; val old = s.main!!.uid
            val g = Game(data, s, Random(0)) { 0 }
            assertTrue(g.setMain(owner.uid).ok)
            assertEquals(if (item == "berry-juice") 4 else 1, s.battle!!.player.volatiles[key])
            assertEquals("switch free hit must not tick healing", before, s.main!!.hp)
            assertTrue(g.setMain(old).ok)
            assertFalse(s.battle!!.player.volatiles.containsKey(key))
            assertTrue(g.setMain(owner.uid).ok)
            assertTrue(s.battle!!.player.volatiles.containsKey(key))
            if (item == "sweet-heart") { play(s); assertEquals(before + StatCalc.maxHp(data, s.main!!) * 70 / 100, s.main!!.hp) }
        }
    }
    @Test fun timedEffectsRespectHealBlockExpireAndNeverReviveFaintedOwners() {
        for (item in listOf("berry-juice", "sweet-heart")) {
            val s = state(); val m = s.main!!; m.hp = 1; use(s, item)
            val before = m.hp; s.battle!!.player.volatiles["healblock"] = 10
            repeat(4) { play(s) }
            assertEquals(before, m.hp)
            assertTrue(s.battle!!.player.volatiles.keys.none { it == "heal.regen" || it == "heal.pending" })
            m.hp = 1; use(s, item); m.hp = 0
            HealingItems.startOfRound(round(s)); HealingItems.endOfRound(round(s)); assertEquals(0, m.hp)
        }
    }
    @Test fun berryJuiceRefreshesFourTicksWithoutStacking() {
        val s = state(); s.main!!.hp = 1; use(s, "berry-juice"); play(s)
        assertEquals(3, s.battle!!.player.volatiles["heal.regen"])
        use(s, "berry-juice"); assertEquals(4, s.battle!!.player.volatiles["heal.regen"])
        val before = s.main!!.hp; play(s)
        assertEquals(before + StatCalc.maxHp(data, s.main!!) / 10, s.main!!.hp)
    }
    @Test fun fullHealthAllowsUsefulBoostsAndStatCureButNotDrawbacks() {
        for (item in listOf("soda-pop", "lemonade", "moomoo-milk")) {
            val s = state(); use(s, item); Economy.give(s, item)
            assertFalse(ItemUse.canUse(data, s, item, s.main))
        }
        for (item in listOf("energy-powder", "energy-root", "fresh-water", "potion", "berry-juice", "sweet-heart")) {
            val s = state(); Economy.give(s, item); assertFalse(item, ItemUse.canUse(data, s, item, s.main))
        }
    }
    @Test fun everyItemUsesTheExistingItemTurnAndNeverTicksTimedHealsOnThatFreeHit() {
        for (item in HealingItems.percent.keys) {
            val s = state(); s.main!!.hp = 1; s.battle!!.answers = 1; Economy.give(s, item)
            val result = Game(data, s, Random(0)) { 0 }.useItem(item, s.main!!.uid)
            assertTrue(item, result.ok)
            assertEquals(item, 1, result.events.filterIsInstance<GameEvent.SwitchHit>().size)
            assertEquals(0, s.battle!!.rounds)
            if (item == "sweet-heart") assertEquals(1, s.main!!.hp)
            if (item == "berry-juice") assertEquals(4, s.battle!!.player.volatiles["heal.regen"])
        }
    }
    @Test fun textsPricesAndConditionIconsDescribeAllEffects() {
        assertEquals(13, HealingItems.percent.size)
        for ((item, pct) in HealingItems.percent) {
            assertTrue(item, ItemInfo.short(data, item).contains("%"))
            assertTrue(item, Items.def(item)!!.note.contains("%"))
            val text = ItemInfo.facts(data, item).joinToString { it.text }
            assertTrue(item, text.contains("Outside battle")); assertTrue(item, text.contains("$pct%"))
            assertTrue(item, item in Items.shopPool(data)); assertTrue(item, item in Items.dropPool())
        }
        assertTrue(data.price("super-potion") < data.price("lemonade"))
        assertTrue(data.price("hyper-potion") > data.price("energy-powder"))
        assertTrue(data.price("max-potion") > data.price("energy-root"))
        for (key in HealingItems.effects) {
            assertNotNull(key, ConditionIcons.of(key))
            val board = ConditionBoard(); assertTrue(board.start(ConditionMarks.Slot.OURS, key, 1))
            assertTrue(ConditionExplanation.effect(board.all().single()).isNotBlank())
        }
    }
}
