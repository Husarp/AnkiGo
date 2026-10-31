package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test

class R23CMoveTextTest {
    private fun move(id: String, type: Type = Type.NORMAL, damage: Boolean = false) =
        Move(id, id, type, if (damage) Category.PHYSICAL else Category.STATUS, if (damage) 30 else 0, 100,
            target = "self", boosts = if (id == "defensecurl") mapOf("def" to 1) else null)
    private fun say(id: String, vararg linked: Move) = MoveText.describe(move(id), listOf(move(id)) + linked)

    @Test fun curlKeepsDefenseAndOnlyShowsKnownSynergy() {
        assertFalse(say("defensecurl").contains("Rollout"))
        assertTrue(say("defensecurl").contains("Defense"))
        for (id in listOf("rollout", "iceball")) assertTrue(say("defensecurl", move(id)).contains("double damage"))
        assertTrue(MoveText.describe(move("defensecurl")).contains("Rollout"))
    }

    @Test fun rolloutAndIceBallKeepTheirLockAndMissRules() {
        for (id in listOf("rollout", "iceball")) {
            val m = move(id, damage = true)
            val alone = MoveText.describe(m, listOf(m))
            assertFalse(alone.contains("Defense Curl"))
            assertTrue(alone.contains("a miss ends it"))
            assertTrue(alone.contains("double the power after each hit"))
            assertTrue(MoveText.describe(m, listOf(m, move("defensecurl"))).contains("Defense Curl"))
        }
    }

    @Test fun stockpileKeepsBoostsAndOnlyShowsKnownConsumers() {
        assertTrue(say("stockpile").contains("Defense"))
        assertFalse(say("stockpile").contains("Swallow"))
        for (id in listOf("swallow", "spitup")) assertTrue(say("stockpile", move(id)).contains("$id uses the stockpile"))
    }

    @Test fun chargeNeedsADamagingElectricMove() {
        assertFalse(say("charge").contains("double damage"))
        assertFalse(say("charge", move("thunderwave", Type.ELECTRIC)).contains("double damage"))
        assertTrue(say("charge", move("thundershock", Type.ELECTRIC, true)).contains("double damage"))
    }

    @Test fun prerequisitesAndOpponentInteractionsStayVisible() {
        for (id in listOf("swallow", "spitup", "minimize", "round", "brickbreak", "lockon", "mindreader", "sleeptalk")) {
            assertEquals(MoveText.describe(move(id)), say(id))
        }
    }
}
