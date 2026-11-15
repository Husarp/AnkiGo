package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.ConditionBoard
import com.husarp.ankigo.ui.ConditionMarks
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R36RItemsTest {
    private val data = TestData.world()
    private fun state(): GameState = TestData.state(data, 25, 30, listOf("tackle")).apply {
        bag.clear()
        battle = Battle(TestData.mon(data, 19, 30, listOf("tackle"), "wild"), Tier.NORMAL)
        day = "2026-11-15"
    }
    private fun use(s: GameState, item: String, seed: Int = 0): ActionResult {
        Economy.give(s, item)
        return ItemUse.use(data, s, item, s.main!!.uid, Random(seed), 0)
    }
    private fun round(s: GameState) = BattleEngine.Round(data, s.battle!!, s.main!!, 1.0, 1.0, Random(4))
    private fun hit(r: Round, p: Boolean = true, move: String = "tackle") = Hit(r, p, r.mon(p), r.side(p), r.mon(!p), r.side(!p), data.moves.getValue(move))

    @Test fun snacksRestoreTheirExactPercentAndMissingHpAmounts() {
        for ((item, percent) in mapOf("rage-candy-bar" to 40, "lumiose-galette" to 25, "casteliacone" to 40, "pewter-crunchies" to 40, "old-gateau" to 50)) {
            val s = state(); val m = s.main!!; val max = StatCalc.maxHp(data, m); m.hp = 1
            assertTrue(item, use(s, item).ok)
            assertEquals(item, 1 + max * percent / 100, m.hp)
            assertEquals(0, s.count(item))
        }
        val s = state(); val m = s.main!!; val max = StatCalc.maxHp(data, m); m.hp = max - 10
        assertTrue(use(s, "big-malasada").ok); assertEquals(max - 4, m.hp)
        m.hp = max - 1; assertTrue(use(s, "big-malasada").ok); assertEquals(max, m.hp)
    }
    @Test fun snackBoostsUseExactMultipliersWithoutStackingAndBelongToThePokemon() {
        val s = state(); val m = s.main!!
        val base = StatCalc.stats(data, m)
        assertTrue(use(s, "rage-candy-bar").ok)
        assertTrue(use(s, "casteliacone").ok)
        assertTrue(use(s, "pewter-crunchies").ok)
        val boosted = SimpleView.of(data, s.battle!!, m).stats(true)
        for (stat in listOf(Stat.ATK, Stat.DEF, Stat.SPE)) assertEquals((base[stat] * 1.1).toInt(), boosted[stat])
        for (stat in listOf(Stat.SPA, Stat.SPD)) assertEquals((base[stat] * 1.1 * 1.25).toInt(), boosted[stat])
        m.hp = 1; assertTrue(use(s, "rage-candy-bar").ok)
        assertEquals(boosted, SimpleView.of(data, s.battle!!, m).stats(true))
        val restored = SaveCodec.decode(SaveCodec.encode(s))
        val b = restored.battle!!; b.switchOutMain(data, restored.main)
        val other = TestData.mon(data, 16, 30, uid = "other")
        BattleItems.sync(b, other)
        assertEquals(StatCalc.stats(data, other), SimpleView.of(data, b, other).stats(true))
        BattleItems.sync(b, restored.main!!)
        assertEquals(boosted, SimpleView.of(data, b, restored.main!!).stats(true))
        val board = ConditionBoard(); board.syncFrom(b)
        assertEquals(3, board.of(ConditionMarks.Slot.OURS).count { it.key.startsWith("snack.") })
        restored.battle = Battle(b.wild, Tier.NORMAL)
        assertEquals(base, SimpleView.of(data, restored.battle!!, restored.main!!).stats(true))
    }
    @Test fun shieldReducesOnlyTheNextDamagingHitAndNeverHeals() {
        val s = state(); val m = s.main!!; m.hp = 20
        assertTrue(use(s, "shalour-sable").ok); assertEquals(20, m.hp)
        val r = round(s); val h = hit(r, false)
        assertEquals(0, BattleItems.takeDamage(r, true, h, 0))
        assertEquals(70, BattleItems.takeDamage(r, true, h, 100))
        assertEquals(100, BattleItems.takeDamage(r, true, h, 100))
        assertFalse(s.battle!!.player.volatiles.containsKey("snack.shield"))
        s.battle = null; Economy.give(s, "shalour-sable")
        assertFalse(ItemUse.canUse(data, s, "shalour-sable", m))
    }
    @Test fun galetteCuresEveryStatusAndAwakeningOnlyCuresSleepEvenAtFullHp() {
        for (status in listOf("brn", "psn", "tox", "slp", "frz", "par")) {
            val s = state(); s.battle!!.player.apply { this.status = status; statusTurns = 3; volatiles[CoreRules.SLEEP_TURNS] = 3 }
            assertTrue(use(s, "lumiose-galette").ok)
            assertNull(s.battle!!.player.status); assertEquals(0, s.battle!!.player.statusTurns)
            assertFalse(s.battle!!.player.volatiles.containsKey(CoreRules.SLEEP_TURNS))
            val a = state(); a.battle!!.player.status = status; val hp = a.main!!.hp
            assertEquals(status == "slp", use(a, "awakening").ok)
            assertEquals(hp, a.main!!.hp)
            assertEquals(if (status == "slp") null else status, a.battle!!.player.status)
        }
        val s = state(); s.battle = null
        assertFalse(use(s, "awakening").ok); assertEquals(1, s.count("awakening"))
    }
    @Test fun lavaCookieHasBothOutcomesAndClampsHp() {
        val seen = mutableSetOf<Boolean>()
        for (seed in 0..40) {
            val s = state(); val m = s.main!!; val max = StatCalc.maxHp(data, m); m.hp = max / 2
            val before = m.hp; val heal = Random(seed).nextBoolean(); seen += heal
            assertTrue(use(s, "lava-cookie", seed).ok)
            assertEquals(if (heal) max else before - max / 4, m.hp)
        }
        assertEquals(setOf(true, false), seen)
        val lossSeed = (0..100).first { !Random(it).nextBoolean() }
        val s = state(); s.main!!.hp = 1; use(s, "lava-cookie", lossSeed); assertEquals(0, s.main!!.hp)
    }
    @Test fun oldGateauIsFreeButOtherSnacksAndAwakeningUseTheNormalItemTurn() {
        for (item in listOf("old-gateau", "lumiose-galette", "awakening")) {
            val s = state(); s.battle!!.answers = 1; s.main!!.hp /= 2; s.battle!!.player.status = "slp"; Economy.give(s, item)
            val g = Game(data, s, Random(10)) { 0 }
            val result = g.useItem(item, s.main!!.uid)
            assertTrue(result.ok)
            assertEquals(if (item == "old-gateau") 0 else 1, result.events.filterIsInstance<GameEvent.SwitchHit>().size)
        }
    }
    @Test fun leftoversHealsSixteenthOnlyLivingHolderAndRespectsSuppression() {
        val s = state(); val m = s.main!!; m.heldItem = "leftovers"; m.hp = 10
        val r = round(s); val amount = StatCalc.maxHp(data, m) / 16
        BattleItems.endOfRound(r); assertEquals(10 + amount, m.hp)
        r.field.rooms["magicroom"] = 2; BattleItems.endOfRound(r); assertEquals(10 + amount, m.hp)
        r.field.rooms.clear(); m.hp = 0; BattleItems.endOfRound(r); assertEquals(0, m.hp)
    }
    @Test fun shellBellHealsEighthOfActualDamageOnceAndNotOnNoDamage() {
        val s = state(); val m = s.main!!; m.heldItem = "shell-bell"; m.hp = 10; val r = round(s)
        BattleItems.afterHit(r, true, data.moves.getValue("tackle"), 40); assertEquals(15, m.hp)
        BattleItems.afterHit(r, true, data.moves.getValue("tackle"), 0); assertEquals(15, m.hp)
        r.side(true).volatiles["healblock"] = 2
        BattleItems.afterHit(r, true, data.moves.getValue("tackle"), 40); assertEquals(15, m.hp)
    }
    @Test fun helmetReducesHitByTwentyPercentAndReflectsTwentyPercentOfActualLossEvenOnKo() {
        val s = state(); s.main!!.heldItem = "rocky-helmet"; val r = round(s); val h = hit(r, false)
        assertEquals(80, BattleItems.takeDamage(r, true, h, 100))
        val before = r.mon(false).hp; r.mon(true).hp = 0
        BattleItems.afterDamage(r, true, h, 30); assertEquals(before - 6, r.mon(false).hp)
    }
    @Test fun orbBoostsDamageAndCostsTenPercentPerAttemptIncludingMissesButNotStatusMoves() {
        val s = state(); val m = s.main!!; m.heldItem = "life-orb"; val r = round(s)
        assertEquals(1.3, BattleItems.finalMod(hit(r)), 0.0)
        val before = m.hp; val cost = StatCalc.maxHp(data, m) / 10
        BattleItems.afterMove(r, true, data.moves.getValue("tackle"), true); assertEquals(before - cost, m.hp)
        BattleItems.afterMove(r, true, data.moves.getValue("growl"), false); assertEquals(before - cost, m.hp)
        BattleItems.afterMove(r, true, data.moves.getValue("tackle"), false); assertEquals(before - cost * 2, m.hp)
        r.useMove(true, data.moves.getValue("tackle")); assertEquals(before - cost * 3, m.hp)
    }
    @Test fun expertBeltOnlyBoostsSuperEffectiveDamage() {
        val s = state(); s.main!!.heldItem = "expert-belt"; val r = round(s)
        assertEquals(1.0, BattleItems.finalMod(hit(r)), 0.0)
        assertEquals(1.2, BattleItems.finalMod(hit(r, move = "seismictoss")), 0.0)
        r.side(true).volatiles["embargo"] = 2
        assertEquals(1.0, BattleItems.finalMod(hit(r, move = "seismictoss")), 0.0)
    }
    @Test fun heldDamageBonusesAlsoApplyToFixedDamageAndRespectImmunity() {
        val s = state(); val m = s.main!!; val r = round(s)
        fun damage(move: String) = BattleEngine.damage(data, m, r.side(true), r.mon(false), r.side(false), data.moves.getValue(move), Random(0), view = r).damage
        assertEquals(40, damage("dragonrage"))
        m.heldItem = "life-orb"; assertEquals(52, damage("dragonrage"))
        m.heldItem = "expert-belt"; assertEquals(36, damage("seismictoss")); assertEquals(40, damage("dragonrage"))
        r.side(true).volatiles["embargo"] = 2; assertEquals(30, damage("seismictoss"))
        r.side(true).volatiles.clear(); r.mon(false).species = 92
        assertEquals(0, damage("seismictoss"))
    }
    @Test fun focusSashSavesBelowFullHpAndIsConsumedExactlyOnce() {
        val s = state(); val m = s.main!!; m.hp = 10; m.heldItem = "focus-sash"; val r = round(s); val h = hit(r, false)
        assertEquals(9, BattleItems.takeDamage(r, true, h, 30)); assertNull(m.heldItem)
        m.hp = 1; assertEquals(30, BattleItems.takeDamage(r, true, h, 30))
        assertNull(SaveCodec.decode(SaveCodec.encode(s)).main!!.heldItem)
    }
    @Test fun heldEffectsAreWiredIntoTheRealEngine() {
        val s = state(); val m = s.main!!; m.heldItem = "focus-sash"; m.hp = 2
        val r = round(s); r.useMove(false, data.moves.getValue("tackle"))
        assertEquals(1, m.hp); assertNull(m.heldItem)
        m.hp = 10; m.heldItem = "shell-bell"
        val before = m.hp; r.useMove(true, data.moves.getValue("tackle"))
        assertTrue(m.hp > before)
    }
    @Test fun snackGivenOnBenchFollowsOnlyThatPokemonThroughRealSwitches() {
        val s = state(); val old = s.main!!
        val other = TestData.mon(data, 16, 30, uid = "other"); s.mons += other; s.team += other.uid
        Economy.give(s, "casteliacone")
        assertTrue(ItemUse.use(data, s, "casteliacone", other.uid, Random(1), 0).ok)
        val game = Game(data, s, Random(1)) { 0 }
        assertTrue(game.setMain(other.uid).ok)
        assertTrue(s.battle!!.player.volatiles.containsKey("snack.spd"))
        assertTrue(game.setMain(old.uid).ok)
        assertFalse(s.battle!!.player.volatiles.containsKey("snack.spd"))
        assertTrue(game.setMain(other.uid).ok)
        assertTrue(s.battle!!.player.volatiles.containsKey("snack.spd"))
    }
    @Test fun suppressedHeldEffectsNeverConsumeSashOrBoostHealReflectOrRecoil() {
        for (held in BattleItems.held.keys) for (suppression in listOf("klutz", "embargo", "magicroom")) {
            val s = state(); val m = s.main!!; m.heldItem = held; m.hp = 10; val r = round(s)
            when (suppression) {
                "klutz" -> m.ability = "Klutz"
                "embargo" -> r.side(true).volatiles["embargo"] = 2
                else -> r.field.rooms["magicroom"] = 2
            }
            assertEquals(1.0, BattleItems.finalMod(hit(r, move = "seismictoss")), 0.0)
            assertEquals(100, BattleItems.takeDamage(r, true, hit(r, false), 100))
            val enemyHp = r.mon(false).hp
            BattleItems.afterDamage(r, true, hit(r, false), 10)
            BattleItems.afterHit(r, true, data.moves.getValue("tackle"), 40)
            BattleItems.afterMove(r, true, data.moves.getValue("tackle"), false)
            BattleItems.endOfRound(r)
            assertEquals(10, m.hp); assertEquals(enemyHp, r.mon(false).hp); assertEquals(held, m.heldItem)
        }
    }
    @Test fun allNewItemsHaveExactTextsPriceAndSourcesWithoutChangingOldHeals() {
        for (item in BattleItems.snacks.keys + BattleItems.held.keys + "awakening") {
            assertNotNull(Items.def(item)); assertTrue(data.price(item) > 0)
            assertTrue(item in Items.shopPool(data)); assertTrue(item in Items.dropPool())
            assertFalse(ItemInfo.line(data, item).contains("No description"))
            assertTrue(ItemInfo.facts(data, item).isNotEmpty())
        }
        assertEquals("Pewter Crunchies", ItemInfo.plural("Pewter Crunchies", 3))
        assertEquals("Leftovers", ItemInfo.plural("Leftovers", 3))
        assertEquals(20, Items.def("potion")!!.heal)
        assertEquals(120, Items.def("hyper-potion")!!.heal)
    }
    @Test fun oldGateauStockIsVeryRareAndLimitedToOnePerOffer() {
        var eligible = 0
        val firstDay = java.time.LocalDate.parse("2026-01-01")
        val days = (0..4999).map { firstDay.plusDays(it.toLong()).toString() }
        fun offers(day: String): List<String> {
            val date = java.time.LocalDate.parse(day)
            val deals = listOf(12, 22).mapNotNull { hour ->
                val now = date.atTime(hour, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                Shop.dealFor(data, day, now)?.item
            }
            return Shop.stockFor(data, day, 100).first + deals
        }
        for (day in days) if ("old-gateau" in offers(day)) eligible++
        assertTrue("$eligible", eligible in 1..175)
        val s = state(); s.cash = 100000
        s.day = days.first { "old-gateau" in offers(it) }
        s.shopItems += "old-gateau"
        assertFalse(Shop.buy(data, s, "old-gateau", quantity = 3).ok)
        assertTrue(Shop.buy(data, s, "old-gateau").ok)
        assertFalse(Shop.buy(data, s, "old-gateau").ok)
    }
}
