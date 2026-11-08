package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R30XCollectionsTest {
    private val data = TestData.world()
    private val now = 1_794_052_800_000L
    private fun state() = TestData.state(data, 25, 20, listOf("splash"))
    private fun value(s: GameState, m: Metric) = Achievements.value(m, s, data)

    @Test fun fixedCataloguesAwardOnceAndSurviveSaveWithoutBackfill() {
        val s = state()
        for (items in ItemCollections.kinds.values) for (item in items) Economy.give(s, item)
        s.main!!.heldItem = "everstone"
        s.ballCatches["poke-ball"] = 500
        Achievements.onReady(s, data, now)
        for ((metric, items) in ItemCollections.kinds) {
            assertEquals(0L, value(s, metric))
            val row = AchievementTable.ROWS.single { it.metric == metric }
            assertEquals("Item collections", row.group)
            assertEquals(listOf(items.size.toLong()), row.tiers)
            assertTrue(row.oneOff); assertTrue(row.rewards.isEmpty())
            assertEquals(AchSource.SINCE_UPDATE, AchPageMath.source(row))
            for (item in items.toList().dropLast(1)) repeat(3) { ItemCollections.record(s, metric, item) }
            assertEquals(items.size - 1L, value(s, metric))
            assertEquals(0, Achievements.reachedTiers(row, s, data))
            ItemCollections.record(s, metric, "future-item")
            ItemCollections.record(s, metric, items.last())
            val events = mutableListOf<GameEvent>()
            Achievements.check(s, data, now, events)
            assertEquals(1, events.filterIsInstance<GameEvent.AchievementReached>().count { it.id == row.id })
            events.clear(); Achievements.check(s, data, now + 1, events)
            assertTrue(events.isEmpty())
        }
        val restored = SaveCodec.decode(SaveCodec.encode(s))
        for ((m, items) in ItemCollections.kinds) assertEquals(items.size.toLong(), value(restored, m))
        assertEquals(s.achTiers, restored.achTiers)
    }

    @Test fun purchasesCountSuccessfulDistinctFoodsOnly() {
        val prices = TestData.PRICES + ItemCollections.kinds.getValue(Metric.PANTRY_KINDS).associateWith { 100 }
        val d = GameData(data.species, data.moves, data.growth, prices)
        val s = state(); s.cash = 0; s.shopItems.addAll(prices.keys)
        assertFalse(Shop.buy(d, s, "sweet-heart", now).ok)
        assertEquals(0L, value(s, Metric.PANTRY_KINDS))
        s.cash = 100_000
        for (item in ItemCollections.kinds.getValue(Metric.PANTRY_KINDS)) assertTrue(Shop.buy(d, s, item, now, 3).ok)
        assertTrue(Shop.buy(d, s, "sweet-heart", now).ok)
        assertEquals(8L, value(s, Metric.PANTRY_KINDS))
    }

    @Test fun throwsCountBothKindsOnMissAndExcludeMasterBall() {
        val s = state()
        val g = Game(data, s, Random(2)) { now }
        assertTrue(g.throwBall("poke-ball").isEmpty())
        assertEquals(0L, value(s, Metric.REGULAR_BALL_KINDS))
        Achievements.count(s, listOf(GameEvent.BallThrow("poke-ball", 20, false, 0, special = "net-ball")))
        Achievements.count(s, listOf(GameEvent.BallThrow("poke-ball", 20, true, 0), GameEvent.BallThrow("master-ball", 100, true, 0)))
        assertEquals(1L, value(s, Metric.REGULAR_BALL_KINDS))
        assertEquals(1L, value(s, Metric.SPECIAL_BALL_KINDS))
        for (item in ItemCollections.kinds.getValue(Metric.SPECIAL_BALL_KINDS)) {
            s.battle = Battle(TestData.mon(data, 54, 20, listOf("splash")), Tier.NORMAL)
            Economy.give(s, "poke-ball"); Economy.give(s, item)
            assertTrue(g.throwBall("poke-ball", item).any { it is GameEvent.BallThrow })
        }
        for (item in listOf("great-ball", "ultra-ball")) {
            s.battle = Battle(TestData.mon(data, 54, 20, listOf("splash")), Tier.NORMAL)
            Economy.give(s, item)
            assertTrue(g.throwBall(item).any { it is GameEvent.BallThrow })
        }
        assertEquals(3L, value(s, Metric.REGULAR_BALL_KINDS))
        assertEquals(10L, value(s, Metric.SPECIAL_BALL_KINDS))
    }

    @Test fun equipAndEvolutionRequireSuccessfulActions() {
        val s = state(); val uid = s.main!!.uid
        Economy.give(s, "everstone", 2); Economy.give(s, "oran-berry")
        assertTrue(ItemUse.use(data, s, "everstone", uid, Random(1), now).ok)
        assertFalse(ItemUse.use(data, s, "everstone", uid, Random(1), now).ok)
        assertEquals(1L, value(s, Metric.EQUIPMENT_KINDS))
        assertTrue(ItemUse.use(data, s, "oran-berry", uid, Random(1), now).ok)
        assertEquals(0L, value(s, Metric.BERRY_KINDS))
        Economy.give(s, "thunder-stone", 2)
        s.main!!.bondPoints = 0
        assertFalse(ItemUse.use(data, s, "thunder-stone", uid, Random(1), now).ok)
        assertEquals(0L, value(s, Metric.STONE_KINDS))
        s.main!!.bondPoints = 125
        assertTrue(ItemUse.use(data, s, "thunder-stone", uid, Random(1), now).ok)
        assertEquals(1L, value(s, Metric.STONE_KINDS))
    }

    @Test fun boxGivingRecordsOnlySuccessfulDistinctEquipmentKinds() {
        val s = state()
        val source = s.main!!.apply { heldItem = "everstone" }
        val target = TestData.mon(data, 54, 20, uid = "target").apply { heldItem = "soothe-bell" }
        s.mons += target
        assertFalse(ItemUse.giveHeld(s, "everstone", source.uid, "missing").ok)
        assertEquals(0L, value(s, Metric.EQUIPMENT_KINDS))
        assertTrue(ItemUse.giveHeld(s, "everstone", source.uid, target.uid).ok)
        assertNull(source.heldItem)
        assertEquals("everstone", target.heldItem)
        assertEquals(1, s.count("soothe-bell"))
        assertEquals(1L, value(s, Metric.EQUIPMENT_KINDS))
        assertFalse(ItemUse.giveHeld(s, "everstone", null, target.uid).ok)
        assertTrue(ItemUse.giveHeld(s, "soothe-bell", null, source.uid).ok)
        assertTrue(ItemUse.giveHeld(s, "everstone", target.uid, source.uid).ok)
        assertEquals(2L, value(s, Metric.EQUIPMENT_KINDS))
        assertEquals(1, s.ownedItemCount("everstone"))
        assertEquals(1, s.ownedItemCount("soothe-bell"))
        val restored = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(2L, value(restored, Metric.EQUIPMENT_KINDS))
    }

    @Test fun berriesCountPlayerActivationNotWildOrSuppressedHeldItems() {
        for (item in ItemCollections.kinds.getValue(Metric.BERRY_KINDS)) {
            val s = state(); val main = s.main!!
            main.heldItem = item; main.hp = 1
            val wild = TestData.mon(data, 54, 20, listOf("splash")); wild.heldItem = item; wild.hp = 1
            val r = BattleEngine.Round(data, Battle(wild, Tier.NORMAL), main, 1.0, 1.0, Random(1))
            AbilityAdaptations.eat(r, false)
            assertTrue(r.events.none { it is GameEvent.BerryActivated })
            main.ability = "klutz"
            AbilityAdaptations.eat(r, true)
            assertEquals(item, main.heldItem)
            main.ability = "testability"
            AbilityAdaptations.eat(r, true)
            assertNull(main.heldItem)
            Achievements.count(s, r.events)
            assertEquals(1L, value(s, Metric.BERRY_KINDS))
        }
    }
}
