package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MoveOffersTest {
    private val data = TestData.world()
    private fun state() = TestData.state(data, 1, 5, listOf("growl", "tackle", "vinewhip", "lick"))
    private fun learn(mon: Mon, move: String) = Progression.learnMove(data, mon, move, mutableListOf())
    private fun accept(mon: Mon, move: String) = Progression.answerMoveOffer(data, mon, move, true,
        if (mon.moves.size >= 4) Progression.replacementIndex(data, mon)?.let { mon.moves[it] } else null)

    @Test fun replacementKeepsFixedDamageAndMultihitScoring() {
        val m = TestData.mon(data, 1, 20, listOf("bulletseed", "dragonrage", "shadowball", "bodyslam"))
        assertEquals(1, Progression.replacementIndex(data, m)) // 75, 60, 80, 85
        m.moves[1] = "earthquake"
        assertEquals(0, Progression.replacementIndex(data, m))
    }

    @Test fun skipIsPermanentEvenAfterSaveAndEvolution() {
        val s = state(); val m = s.main!!
        val before = m.moves.toList()
        learn(m, "bulletseed")
        assertTrue(Progression.answerMoveOffer(data, m, "bulletseed", false, "growl").ok)
        assertEquals(before, m.moves)
        val restored = SaveCodec.decode(SaveCodec.encode(s)); val loaded = restored.main!!
        Progression.evolve(data, restored, loaded, data.sp(1).evolutions.first(), Random(1), mutableListOf())
        learn(loaded, "bulletseed")
        assertTrue(loaded.moveOffers.isEmpty())
        assertFalse("bulletseed" in loaded.moves)
    }

    @Test fun crossingLevelsQueuesInOrderWithoutChangingMovesOrLearningEvents() {
        val sp = data.sp(1).copy(levelMoves = listOf(LevelMove(6, "bodyslam"), LevelMove(7, "bulletseed"), LevelMove(8, "dragonrage")))
        val d = GameData(data.species + (1 to sp), data.moves, data.growth, emptyMap())
        val s = state(); val m = s.main!!; val before = m.moves.toList(); val events = mutableListOf<GameEvent>()
        val xp = (5 until 8).sumOf { level -> (level + 1) * (level + 1) * (level + 1) - level * level * level }
        Progression.gainXp(d, s, m, xp, events)
        assertEquals(listOf(MoveOffer("bodyslam", 6), MoveOffer("bulletseed", 7), MoveOffer("dragonrage", 8)), m.moveOffers)
        assertEquals(before, m.moves)
        assertFalse(events.any { it is GameEvent.MoveLearned })
        assertFalse(Progression.answerMoveOffer(d, m, "bulletseed", false, "growl").ok)
        assertTrue(accept(m, "bodyslam").ok)
        assertEquals("lick", m.moves[Progression.replacementIndex(data, m)!!])
        assertTrue(accept(m, "bulletseed").ok)
        assertEquals(listOf("dragonrage"), m.moveOffers.map { it.move })
        assertEquals(4, m.moves.size)
    }

    @Test fun offerAndAnswersRoundTripAndSnapshotIsDetached() {
        val s = state(); val m = s.main!!
        learn(m, "bodyslam"); learn(m, "bulletseed")
        val snapshot = SaveCodec.snapshot(s)
        assertTrue(accept(m, "bodyslam").ok)
        val loaded = SaveCodec.decode(snapshot())
        assertEquals(listOf("bodyslam", "bulletseed"), loaded.main!!.moveOffers.map { it.move })
        val json = SaveCodec.encode(s)
        assertEquals(json, SaveCodec.encode(SaveCodec.decode(json)))
        assertEquals(listOf("bulletseed"), SaveCodec.decode(json).main!!.moveOffers.map { it.move })
    }

    @Test fun noDuplicatePendingKnownOrAcceptedMoves() {
        val s = state(); val m = s.main!!
        learn(m, "tackle"); learn(m, "bodyslam"); learn(m, "bodyslam")
        assertEquals(1, m.moveOffers.size)
        assertTrue(accept(m, "bodyslam").ok)
        learn(m, "bodyslam")
        assertTrue(m.moveOffers.isEmpty())
        assertEquals(m.moves.distinct(), m.moves)
        assertFalse(accept(m, "bodyslam").ok)
    }

    @Test fun tmCanTeachPendingMoveButAcceptCannotDuplicateIt() {
        val s = state(); val m = s.main!!
        val sp = data.sp(1).copy(tmMoves = setOf("bodyslam"))
        val d = GameData(data.species + (1 to sp), data.moves, data.growth, emptyMap())
        s.tms += "bodyslam"
        learn(m, "bodyslam")
        assertTrue(ItemUse.teachTm(d, s, "bodyslam", m.uid, 0).ok)
        assertFalse(ItemUse.teachTm(d, s, "bodyslam", m.uid, 1).ok)
        assertTrue(accept(m, "bodyslam").ok)
        assertEquals(1, m.moves.count { it == "bodyslam" })
        assertEquals(4, m.moves.size)
    }

    @Test fun stalePromptCannotReplaceAnotherMove() {
        val m = state().main!!
        learn(m, "bodyslam")
        m.moves[0] = "earthquake"
        val before = m.moves.toList()
        assertFalse(Progression.answerMoveOffer(data, m, "bodyslam", true, "growl").ok)
        assertEquals(before, m.moves)
        assertEquals(1, m.moveOffers.size)
    }

    @Test fun evolutionAlsoOffersWithoutSwappingAndDoesNotDuplicateKnownMoves() {
        val s = state(); val m = s.main!!; val before = m.moves.toList()
        Progression.evolve(data, s, m, data.sp(1).evolutions.first(), Random(1), mutableListOf())
        assertEquals(before, m.moves)
        assertEquals(listOf("bulletseed"), m.moveOffers.map { it.move })
        learn(m, "bulletseed")
        assertEquals(1, m.moveOffers.size)
    }

    @Test fun legacyMigrationDoesNotGuessPastSwapsAndRemovesDuplicateImportedMoves() {
        val s = state()
        val root = JSONObject(SaveCodec.encode(s)); val mon = root.getJSONArray("mons").getJSONObject(0)
        mon.remove("offeredMoves"); mon.remove("moveOffers")
        val loaded = SaveCodec.decode(root.toString()).main!!
        assertEquals(s.main!!.moves, loaded.moves)
        assertTrue(loaded.moveOffers.isEmpty())
        mon.put("moves", JSONArray(listOf("tackle", "tackle", "growl", "vinewhip", "lick", "bodyslam")))
        val imported = SaveCodec.decode(root.toString()).main!!
        assertEquals(listOf("tackle", "growl", "vinewhip", "lick"), imported.moves)
        assertTrue(imported.moveOffers.isEmpty())
    }

    @Test fun caughtMovesAreDistinctAndCappedAtFour() {
        val s = state()
        val wild = TestData.mon(data, 1, 10, listOf("tackle", "tackle", "growl", "vinewhip", "lick", "bodyslam"))
        val caught = Catching.addToCollection(data, s, wild, Tier.NORMAL, null, 1L, mutableListOf())
        assertEquals(listOf("tackle", "growl", "vinewhip", "lick"), caught.moves)
    }

    @Test fun acceptingUsesFreeSlotIfImportLeftFewerThanFourMoves() {
        val m = state().main!!; learn(m, "bodyslam")
        m.moves.removeAt(0)
        assertTrue(accept(m, "bodyslam").ok)
        assertEquals(4, m.moves.size)
        assertEquals(1, m.moves.count { it == "bodyslam" })
    }
}
