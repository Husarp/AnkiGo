package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R36QMovesTest {
    private val data = TestData.world()
    private fun state() = TestData.state(data, 25, 20, listOf("tackle", "growl", "lick", "watergun"))
    private fun tmData() = GameData(data.species + (25 to data.sp(25).copy(tmMoves = setOf("thunderbolt"))), data.moves, data.growth, data.itemPrices)

    @Test fun discoveryRoundTripsOwnedWildAndPendingCatchAndSnapshotIsDetached() {
        val s = state(); val wild = TestData.mon(data, 92, 20, listOf("lick", "hypnosis"), "wild")
        s.main!!.discoveredMoves += "tackle"
        wild.discoveredMoves += "lick"
        s.battle = Battle(wild, Tier.NORMAL)
        s.asks += PendingAsk(PendingAsk.Kind.CATCH, wild.uid, wild, Tier.NORMAL)
        val snapshot = SaveCodec.snapshot(s)
        s.main!!.discoveredMoves += "growl"; wild.discoveredMoves.clear()
        val saved = snapshot(); val loaded = SaveCodec.decode(saved)
        assertEquals(setOf("tackle"), loaded.main!!.discoveredMoves)
        assertEquals(setOf("lick"), loaded.battle!!.wild.discoveredMoves)
        assertEquals(setOf("lick"), loaded.asks.single().wild!!.discoveredMoves)
        assertEquals(saved, SaveCodec.encode(loaded))
        assertEquals(listOf("lick", null), WildInfo.of(data, loaded, loaded.battle!!, emptySet())!!.moves.map { it.id })
    }

    @Test fun legacySaveRevealsOwnedCurrentMovesOnlyAndEachTmHasOneUse() {
        val s = state(); s.tms += "thunderbolt"
        s.main!!.bondPoints = Bond.thresholds[2]
        s.battle = Battle(TestData.mon(data, 92, 20, listOf("lick", "hypnosis"), "wild"), Tier.NORMAL)
        val root = JSONObject(SaveCodec.encode(s))
        root.getJSONArray("mons").getJSONObject(0).remove("discoveredMoves")
        root.getJSONObject("battle").getJSONObject("wild").remove("discoveredMoves")
        val loaded = SaveCodec.decode(root.toString())
        assertEquals(loaded.main!!.moves.toSet(), loaded.main!!.discoveredMoves)
        assertTrue(loaded.battle!!.wild.discoveredMoves.isEmpty())
        assertEquals(setOf("thunderbolt"), loaded.tms)
        assertTrue(ItemUse.teachTm(tmData(), loaded, "thunderbolt", loaded.main!!.uid, 0).ok)
        assertTrue(SaveCodec.decode(SaveCodec.encode(loaded)).tms.isEmpty())
    }

    @Test fun explicitEmptyDiscoveryStaysEmptyAcrossRepeatedLoads() {
        var s = state()
        repeat(3) { s = SaveCodec.decode(SaveCodec.encode(s)); assertTrue(s.main!!.discoveredMoves.isEmpty()) }
    }

    @Test fun actualMovesOnBothSidesAreDiscoveredAndCatchKeepsIndividualKnowledge() {
        val main = TestData.mon(data, 25, 50, listOf("tackle"))
        val wild = TestData.mon(data, 54, 50, listOf("watergun"), "wild")
        val battle = Battle(wild, Tier.NORMAL)
        val events = BattleEngine.playRound(data, battle, main, 0.0, true, Random(1), enemyPower = 0.0)
        assertEquals(2, events.filterIsInstance<GameEvent.Attack>().size)
        assertEquals(setOf("tackle"), main.discoveredMoves)
        assertEquals(setOf("watergun"), wild.discoveredMoves)
        wild.moves += "growl"
        val caught = Catching.addToCollection(data, state(), wild, Tier.NORMAL, null, 100, mutableListOf())
        assertEquals(setOf("watergun"), caught.discoveredMoves)
        wild.discoveredMoves += "growl"
        assertEquals(setOf("watergun"), caught.discoveredMoves)
    }

    @Test fun sleepingPokemonDoesNotDiscoverAnUnperformedMove() {
        val s = state(); val main = s.main!!
        main.moves.clear(); main.moves += "tackle"
        val battle = Battle(TestData.mon(data, 54, 20, listOf("watergun")), Tier.NORMAL)
        battle.player.status = "slp"; battle.player.volatiles[CoreRules.SLEEP_TURNS] = 3
        BattleEngine.playRound(data, battle, main, 0.0, false, Random(1))
        assertTrue(main.discoveredMoves.isEmpty())
    }

    @Test fun visibleChargeAndMissDiscoverMovesWithoutChangingTheirOutcome() {
        val charge = data.move("solarbeam")!!.copy(flags = setOf("charge"))
        val miss = data.move("tackle")!!.copy(accuracy = 0)
        val d = GameData(data.species, data.moves + mapOf(charge.id to charge, miss.id to miss), data.growth, data.itemPrices)
        val main = TestData.mon(d, 1, 50, listOf("solarbeam"))
        val wild = TestData.mon(d, 54, 50, listOf("tackle"), "wild")
        val battle = Battle(wild, Tier.NORMAL)
        val events = BattleEngine.playRound(d, battle, main, 0.0, true, Random(1), enemyPower = 0.0)
        assertEquals("solarbeam", battle.player.charging)
        assertEquals(setOf("solarbeam"), main.discoveredMoves)
        assertEquals(setOf("tackle"), wild.discoveredMoves)
        assertTrue(events.filterIsInstance<GameEvent.Attack>().single().missed)
    }

    @Test fun freeHitDiscoversWildMoveWithoutRevealingUnchosenSlots() {
        val s = state(); val wild = TestData.mon(data, 54, 20, listOf("watergun"), "wild")
        BattleEngine.switchHit(data, Battle(wild, Tier.NORMAL), s.main!!, Random(1), power = 0.0)
        assertEquals(setOf("watergun"), wild.discoveredMoves)
        assertTrue(s.main!!.discoveredMoves.isEmpty())
    }

    @Test fun tmRequiresBondTwoAndDiscoveredReplacementAndConsumesOnlyOnSuccess() {
        val s = state(); val mon = s.main!!; s.tms += "thunderbolt"
        val d = tmData(); mon.discoveredMoves += "lick"
        mon.bondPoints = Bond.thresholds[2] - 1
        assertFalse(ItemUse.teachTm(d, s, "thunderbolt", mon.uid, 2).ok)
        mon.bondPoints++
        assertFalse(ItemUse.teachTm(d, s, "thunderbolt", mon.uid, 0).ok)
        assertEquals(setOf("thunderbolt"), s.tms)
        assertEquals(listOf("tackle", "growl", "lick", "watergun"), mon.moves)
        assertTrue(ItemUse.teachTm(d, s, "thunderbolt", mon.uid, 2).ok)
        assertTrue(s.tms.isEmpty())
        assertFalse("thunderbolt" in mon.discoveredMoves)
        assertEquals(listOf("tackle", "growl", "thunderbolt", "watergun"), mon.moves)
        assertFalse(ItemUse.teachTm(d, s, "thunderbolt", mon.uid, 1).ok)
    }

    @Test fun consumedTmCanBeBoughtFromANewOffer() {
        val s = state(); s.main!!.bondPoints = Bond.thresholds[2]; s.main!!.discoveredMoves += "tackle"
        s.shopDay = s.day; s.shopTms += "thunderbolt"; s.cash = 10000
        val price = Shop.tmPrice(s.day, "thunderbolt")
        assertTrue(Shop.buyTm(data, s, "thunderbolt").ok)
        assertTrue(ItemUse.teachTm(tmData(), s, "thunderbolt", s.main!!.uid, 0).ok)
        assertFalse(Shop.buyTm(data, s, "thunderbolt").ok)
        val refresh = Shop.refreshPrice(s)
        assertTrue(Shop.reroll(data, s, Random(0)).ok)
        s.shopTms += "thunderbolt"
        assertTrue(Shop.buyTm(data, s, "thunderbolt").ok)
        assertEquals(setOf("thunderbolt"), s.tms)
        assertEquals(10000 - 2 * price - refresh, s.cash)
    }

    @Test fun fossilRevivalStartsWithAllMovesUndiscoveredAndSavePreservesThat() {
        val s = state(); Economy.give(s, "helix-fossil")
        val d = GameData(data.species + (138 to TestData.species(138, "Spiralmon")), data.moves, data.growth, data.itemPrices)
        assertTrue(ItemUse.use(d, s, "helix-fossil", null, Random(1), 100).ok)
        val fossil = s.mons.last()
        assertEquals(138, fossil.species); assertTrue(fossil.moves.isNotEmpty())
        assertTrue(fossil.discoveredMoves.isEmpty())
        assertTrue(SaveCodec.decode(SaveCodec.encode(s)).mons.last().discoveredMoves.isEmpty())
    }
}
