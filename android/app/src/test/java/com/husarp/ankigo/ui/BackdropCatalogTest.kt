package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// ROUND 8 R8-02: the 43 backgrounds, their stand points, and which types get them.
class BackdropCatalogTest {
    private val all = BackdropCatalog.ALL
    private val batch2 = mapOf(
        "canyon" to (1375 to 768), "cliff-clouds" to (1376 to 768), "crystal-storm" to (1264 to 848), "flower-field" to (1264 to 848),
        "haunted-mansion" to (1264 to 848), "jungle" to (1264 to 848), "legendary-shrine" to (1376 to 768), "mine" to (1264 to 848),
        "ruined-castle" to (1264 to 848), "snow-aurora" to (1376 to 768), "stormy-plains" to (1264 to 848), "sunset-beach" to (1376 to 768),
    )

    @Test fun fortyThreeUniqueBackgrounds() {
        assertEquals(43, all.size)
        assertEquals(43, all.map { it.id }.toSet().size)
        assertTrue(all.all { it.id.isNotBlank() && it.id == it.id.lowercase() && !it.id.contains(' ') })
    }

    @Test fun thirteenShowdownAndThirtyOwn() {
        assertEquals(13, all.count { it.source == BackdropSource.SHOWDOWN })
        assertEquals(30, all.count { it.source == BackdropSource.OWN })
        assertTrue(all.none { it.id == "deepsea" })
        assertNull(BackdropCatalog.byId("deepsea"))
    }

    @Test fun extensionsAndSizes() {
        for (b in all) {
            assertTrue(b.id, b.ext == "png" || b.ext == "webp")
            assertTrue(b.id, b.w > 0 && b.h > 0)
            if (b.source == BackdropSource.SHOWDOWN) {
                assertEquals(b.id, "png", b.ext)
                assertEquals(b.id, 500, b.h)
            }
        }
        // Adam's batch 1 are WebP, the rest of his are PNG
        val webp = all.filter { it.ext == "webp" }.map { it.id }.toSet()
        assertEquals(
            setOf("night-alley", "dragon-peak", "deep-sea-cave", "night-forest", "factory", "snow-field", "graveyard", "dojo", "power-plant", "crystal-cave") + batch2.keys,
            webp,
        )
        for ((id, size) in batch2) { val b = BackdropCatalog.byId(id)!!; assertEquals(id, size, b.w to b.h); assertFalse(id, b.crisp) }
        assertEquals(12, all.count { it.ext == "webp" } - 10)
        assertTrue(all.filter { it.ext == "webp" }.all { it.source == BackdropSource.OWN })
        // the 3:2 pixel grid pictures are crisp, the dark ones are flagged
        for (id in listOf("reef", "volcano-b", "garden")) {
            val b = BackdropCatalog.byId(id)!!
            assertEquals(316, b.w); assertEquals(212, b.h); assertTrue(b.crisp)
        }
        assertEquals(setOf("night-alley", "deep-sea-cave", "night-forest", "graveyard", "crystal-storm", "haunted-mansion", "mine", "ruined-castle", "snow-aurora", "stormy-plains"), all.filter { it.dark }.map { it.id }.toSet())
    }

    @Test fun standPointsAreSane() {
        for (b in all) {
            for ((name, s) in listOf("player" to b.player, "wild" to b.wild)) {
                assertTrue("${b.id} $name x", s.x > 0f && s.x < 1f)
                assertTrue("${b.id} $name y", s.y > 0f && s.y < 1f)
                assertTrue("${b.id} $name s", s.s > 0f && s.s < 1f)
            }
            assertTrue("${b.id} wild is further back (higher)", b.wild.y < b.player.y)
            assertTrue("${b.id} wild is right of ours", b.wild.x > b.player.x)
            assertTrue("${b.id} ours is the bigger one", b.player.s > b.wild.s)
        }
    }

    @Test fun byIdFindsEveryBackground() {
        for (b in all) assertSame(b, BackdropCatalog.byId(b.id))
        assertNull(BackdropCatalog.byId("nothing"))
        assertNull(BackdropCatalog.byId(""))
    }

    @Test fun everyTypeHasAtLeastTwoOptions() {
        for (t in Type.values()) {
            val o = BackdropCatalog.options(t)
            assertTrue("$t", o.size >= 2)
            assertEquals("$t has no repeats", o.size, o.map { it.id }.toSet().size)
        }
    }

    @Test fun everyBackgroundIsUsedBySomeType() {
        val used = Type.values().flatMap { BackdropCatalog.options(it) }.map { it.id }.toSet()
        assertEquals(all.map { it.id }.toSet() - BackdropCatalog.SHRINE, used)
    }

    @Test fun shrineIsInNoTypeListAndOnlyForLegendary() {
        assertTrue(BackdropCatalog.byId(BackdropCatalog.SHRINE) != null)
        for (t in Type.values()) assertTrue("$t", BackdropCatalog.options(t).none { it.id == BackdropCatalog.SHRINE })
        for (t in Type.values()) for (seed in listOf(0, 1, 7, -3, Int.MAX_VALUE)) {
            assertEquals(BackdropCatalog.SHRINE, BackdropCatalog.pick(listOf(t), seed, legendary = true).id)
            assertTrue(BackdropCatalog.pick(listOf(t), seed).id != BackdropCatalog.SHRINE)
        }
        assertEquals(BackdropCatalog.SHRINE, BackdropCatalog.pick(emptyList(), 5, true).id)
    }

    @Test fun typeForRules() {
        assertEquals(Type.NORMAL, BackdropCatalog.typeFor(emptyList()))
        assertEquals(Type.NORMAL, BackdropCatalog.typeFor(listOf(Type.NORMAL)))
        assertEquals(Type.FLYING, BackdropCatalog.typeFor(listOf(Type.NORMAL, Type.FLYING)))
        assertEquals(Type.FIRE, BackdropCatalog.typeFor(listOf(Type.FIRE, Type.WATER)))
        assertEquals(Type.FLYING, BackdropCatalog.typeFor(listOf(Type.FLYING, Type.NORMAL)))
        assertEquals(Type.WATER, BackdropCatalog.typeFor(listOf(Type.WATER)))
    }

    @Test fun pickIsDeterministicPerSeedAndCoversAllOptions() {
        for (t in Type.values()) {
            val options = BackdropCatalog.options(t)
            val seen = HashSet<String>()
            for (seed in 0 until options.size * 3) {
                val a = BackdropCatalog.pick(listOf(t), seed)
                assertSame("$t $seed", a, BackdropCatalog.pick(listOf(t), seed))
                assertTrue("$t $seed", a in options)
                seen += a.id
            }
            assertEquals("$t", options.map { it.id }.toSet(), seen)
        }
    }

    @Test fun pickFollowsTheTypeRule() {
        for (seed in 0..20) {
            assertSame(BackdropCatalog.pick(listOf(Type.FLYING), seed), BackdropCatalog.pick(listOf(Type.NORMAL, Type.FLYING), seed))
            assertSame(BackdropCatalog.pick(listOf(Type.NORMAL), seed), BackdropCatalog.pick(emptyList(), seed))
        }
    }

    @Test fun negativeSeedsAreFine() {
        for (t in Type.values()) {
            val options = BackdropCatalog.options(t)
            for (seed in listOf(-1, -7, -1000003, Int.MIN_VALUE, Int.MAX_VALUE)) {
                val b = BackdropCatalog.pick(listOf(t), seed)
                assertNotNull(b)
                assertTrue("$t $seed", b in options)
            }
            // -1 is the last option (floorMod), not an out-of-range one
            assertSame(options.last(), BackdropCatalog.pick(listOf(t), -1))
        }
    }

    @Test fun seedOfAnEmptyUidIsZeroAndOthersAreStable() {
        assertEquals(0, BackdropCatalog.seed(""))
        assertEquals("abc".hashCode(), BackdropCatalog.seed("abc"))
        assertEquals(BackdropCatalog.seed("uid-1"), BackdropCatalog.seed("uid-1"))
        assertFalse(BackdropCatalog.seed("uid-1") == BackdropCatalog.seed("uid-2"))
    }
}
