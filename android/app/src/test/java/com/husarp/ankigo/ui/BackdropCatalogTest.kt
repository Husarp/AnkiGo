package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Type
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// ROUND 8 R8-02, ROUND 9 D10: the 35 backgrounds (17 of the 43 dropped, 3 added in wave 3, 5 in wave 4, 1 in wave 5), their stand points, and which types get them.
class BackdropCatalogTest {
    private val all = BackdropCatalog.ALL
    private val dropped = setOf(
        "beachshore", "reef", "volcano-b", "garden", "rocky-path", "volcano", "sky", "poison-swamp", "temple", "night-alley", "snow-field",
        "graveyard", "dojo", "power-plant", "crystal-cave", "ruined-castle",
    )
    private val batch1 = setOf("dragon-peak", "deep-sea-cave", "night-forest", "factory")
    private val batch3 = setOf("legendary-shrine", "ruins-courtyard", "dark-town")
    private val batch4 = setOf("dark-forest", "fire-savanna", "ice-lake", "fighting-training", "poison-marsh")
    private val batch5 = setOf("psychic-lab")
    private val batch2 = mapOf(
        "canyon" to (1375 to 768), "cliff-clouds" to (1376 to 768), "crystal-storm" to (1264 to 848), "flower-field" to (1264 to 848),
        "haunted-mansion" to (1264 to 848), "jungle" to (1264 to 848), "mine" to (1264 to 848),
        "snow-aurora" to (1376 to 768), "stormy-plains" to (1264 to 848), "sunset-beach" to (1376 to 768),
    )

    @Test fun thirtyFiveUniqueBackgrounds() {
        assertEquals(35, all.size)
        assertEquals(35, all.map { it.id }.toSet().size)
        assertTrue(all.none { it.id in dropped })
        for (id in dropped) assertNull(id, BackdropCatalog.byId(id))
        assertTrue(all.all { it.id.isNotBlank() && it.id == it.id.lowercase() && !it.id.contains(' ') })
    }

    @Test fun twelveShowdownAndTwentyThreeOwn() {
        assertEquals(12, all.count { it.source == BackdropSource.SHOWDOWN })
        assertEquals(23, all.count { it.source == BackdropSource.OWN })
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
        // all of Adam's kept pictures are WebP, soft (not crisp); only Showdown's are crisp PNGs
        val webp = all.filter { it.ext == "webp" }.map { it.id }.toSet()
        assertEquals(batch1 + batch2.keys + batch3 + batch4 + batch5, webp)
        for ((id, size) in batch2) { val b = BackdropCatalog.byId(id)!!; assertEquals(id, size, b.w to b.h) }
        for (id in batch3 + batch4 + batch5) { val b = BackdropCatalog.byId(id)!!; assertEquals(id, 1024 to 687, b.w to b.h) }
        assertTrue(all.filter { it.ext == "webp" }.all { it.source == BackdropSource.OWN && !it.crisp })
        assertTrue(all.filter { it.source == BackdropSource.SHOWDOWN }.all { it.crisp })
        // the dark ones are flagged
        assertEquals(setOf("deep-sea-cave", "night-forest", "crystal-storm", "haunted-mansion", "mine", "snow-aurora", "stormy-plains", "dark-town", "dark-forest", "psychic-lab"), all.filter { it.dark }.map { it.id }.toSet())
    }

    @Test fun deepSeaCaveHasItsRound9StandPoints() {
        val b = BackdropCatalog.byId("deep-sea-cave")!!
        assertEquals(.30f, b.player.x, 1e-6f); assertEquals(.815f, b.player.y, 1e-6f)
        assertEquals(.80f, b.wild.x, 1e-6f); assertEquals(.615f, b.wild.y, 1e-6f)
    }

    @Test fun keptOwnPicturesAreSharpenedRevisions() {
        for (b in all.filter { it.source == BackdropSource.OWN && it.id !in batch3 + batch4 + batch5 }) assertEquals(b.id, if (b.id == "night-forest") 3 else 2, b.rev)
        // batch 3: the new normal pictures start at revision 1, the new shrine is revision 2 (the old shrine file was revision 1)
        assertEquals(1, BackdropCatalog.byId("ruins-courtyard")!!.rev); assertEquals(1, BackdropCatalog.byId("dark-town")!!.rev)
        assertEquals(2, BackdropCatalog.byId("legendary-shrine")!!.rev)
        for (b in all.filter { it.source == BackdropSource.SHOWDOWN }) assertEquals(b.id, 1, b.rev)
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

    @Test fun everyTypeHasAtLeastOneOption() {
        for (t in Type.values()) {
            val o = BackdropCatalog.options(t)
            assertTrue("$t", o.isNotEmpty())
            assertEquals("$t has no repeats", o.size, o.map { it.id }.toSet().size)
        }
    }

    @Test fun everyBackgroundIsUsedBySomeType() {
        val used = Type.values().flatMap { BackdropCatalog.options(it) }.map { it.id }.toSet()
        // the shrine is in no type list: only Legendary / Mythical encounters get it
        assertEquals(all.map { it.id }.toSet() - BackdropCatalog.SHRINE, used)
    }

    @Test fun darkAndTheTypesKeepWhatTheyShouldAfterTheDrops() {
        assertEquals(listOf("night-forest", "dark-town", "dark-forest"), BackdropCatalog.options(Type.DARK).map { it.id })
        assertEquals(listOf("meadow", "route", "flower-field", "sunset-beach"), BackdropCatalog.options(Type.NORMAL).map { it.id })
        assertEquals(listOf("meadow", "forest", "flower-field"), BackdropCatalog.options(Type.FAIRY).map { it.id })
    }

    @Test fun theNewBackgroundsAreInTheirTypeLists() {
        for (t in listOf(Type.ROCK, Type.FIGHTING, Type.PSYCHIC)) assertTrue("$t", "ruins-courtyard" in BackdropCatalog.options(t).map { it.id })
        assertTrue("dark-town" in BackdropCatalog.options(Type.DARK).map { it.id })
        assertEquals(setOf(Type.ROCK, Type.FIGHTING, Type.PSYCHIC), Type.values().filter { t -> BackdropCatalog.options(t).any { it.id == "ruins-courtyard" } }.toSet())
        assertEquals(setOf(Type.DARK), Type.values().filter { t -> BackdropCatalog.options(t).any { it.id == "dark-town" } }.toSet())
        for (t in Type.values()) assertTrue("$t", BackdropCatalog.options(t).none { it.id == BackdropCatalog.SHRINE })
    }

    @Test fun theNewShrineIsForLegendaryAndMythicalEncounters() {
        val shrine = BackdropCatalog.byId(BackdropCatalog.SHRINE)!!
        assertEquals("legendary-shrine", shrine.id)
        assertEquals(2, shrine.rev)
        for (t in Type.values()) for (seed in listOf(0, 1, 7, -3, Int.MAX_VALUE)) {
            assertSame("$t $seed", shrine, BackdropCatalog.pick(listOf(t), seed, legendary = true))
            // not legendary: still one of the type's own pictures, never the shrine
            assertTrue("$t $seed", BackdropCatalog.pick(listOf(t), seed).id != BackdropCatalog.SHRINE)
        }
        assertSame(shrine, BackdropCatalog.pick(emptyList(), 5, true))
    }

    @Test fun legendaryAndMythicalSpeciesGetTheShrine() {
        // isLegendary is by tier (Encounters.tierOf): Legendary and Mythical species count, an ordinary one and an Ultra Beast do not
        assertFalse(BackdropCatalog.isLegendary(null))
        assertTrue(BackdropCatalog.isLegendary(TestData.species(150, isLegendary = true)))
        assertTrue(BackdropCatalog.isLegendary(TestData.species(151, isMythical = true)))
        assertFalse(BackdropCatalog.isLegendary(TestData.species(19)))
        assertFalse(BackdropCatalog.isLegendary(TestData.species(793, isLegendary = true)))
        val sp = TestData.species(150, isLegendary = true)
        assertSame(BackdropCatalog.byId(BackdropCatalog.SHRINE), BackdropCatalog.pick(sp.types, BackdropCatalog.seed("u1"), BackdropCatalog.isLegendary(sp)))
    }

    @Test fun newPicturesFileNamesAndStandPoints() {
        assertEquals("legendary-shrine-2.webp", com.husarp.ankigo.data.Backdrops.fileName(BackdropCatalog.byId("legendary-shrine")!!))
        assertEquals("ruins-courtyard.webp", com.husarp.ankigo.data.Backdrops.fileName(BackdropCatalog.byId("ruins-courtyard")!!))
        assertEquals("dark-town.webp", com.husarp.ankigo.data.Backdrops.fileName(BackdropCatalog.byId("dark-town")!!))
        val town = BackdropCatalog.byId("dark-town")!!
        assertEquals(.68f, town.wild.x, 1e-6f) // a bit left of the rubble on the right
        assertTrue(town.dark)
        assertFalse(BackdropCatalog.byId("ruins-courtyard")!!.dark)
        for (id in listOf("legendary-shrine", "ruins-courtyard", "dark-town")) {
            val b = BackdropCatalog.byId(id)!!
            assertEquals(id, .44f, b.player.s, 1e-6f); assertEquals(id, .32f, b.wild.s, 1e-6f)
            assertTrue(id, !b.crisp)
        }
    }

    @Test fun theWave4BackgroundsAreInTheirTypeLists() {
        val types = mapOf(
            "dark-forest" to setOf(Type.DARK), "fire-savanna" to setOf(Type.FIRE), "ice-lake" to setOf(Type.ICE),
            "fighting-training" to setOf(Type.FIGHTING, Type.GROUND), "poison-marsh" to setOf(Type.POISON),
        )
        assertEquals(batch4, types.keys)
        for ((id, ts) in types) assertEquals(id, ts, Type.values().filter { t -> BackdropCatalog.options(t).any { it.id == id } }.toSet())
        // the new ones are appended: the older pictures keep their place in each list (a fight's seed keeps its picture)
        assertEquals("fire-savanna", BackdropCatalog.options(Type.FIRE).last().id)
        assertEquals("dark-forest", BackdropCatalog.options(Type.DARK).last().id)
    }

    @Test fun theWave4FileNamesFlagsAndStandPoints() {
        for (id in batch4) {
            val b = BackdropCatalog.byId(id)!!
            assertEquals(id, "$id.webp", com.husarp.ankigo.data.Backdrops.fileName(b))
            assertEquals(id, 1, b.rev); assertEquals(id, BackdropSource.OWN, b.source)
            assertEquals(id, .44f, b.player.s, 1e-6f); assertEquals(id, .32f, b.wild.s, 1e-6f)
            assertFalse(id, b.crisp)
            assertEquals(id, id == "dark-forest", b.dark) // the only night picture
        }
        val marsh = BackdropCatalog.byId("poison-marsh")!! // the feet stand on the two dark ovals: ours lower left, the wild one mid right
        assertTrue(marsh.player.x < .4f && marsh.player.y > .75f); assertTrue(marsh.wild.x > .65f && marsh.wild.y < .6f)
    }

    @Test fun thePsychicLabIsTheLastPsychicPictureAndOnlyThat() {
        // appended: the older Psychic picks keep their place (a fight's seed keeps its picture)
        assertEquals(listOf("city", "forest", "ruins-courtyard", "psychic-lab"), BackdropCatalog.options(Type.PSYCHIC).map { it.id })
        assertEquals(setOf(Type.PSYCHIC), Type.values().filter { t -> BackdropCatalog.options(t).any { it.id == "psychic-lab" } }.toSet())
        val b = BackdropCatalog.byId("psychic-lab")!!
        assertEquals("psychic-lab.webp", com.husarp.ankigo.data.Backdrops.fileName(b))
        assertEquals(1, b.rev); assertEquals(BackdropSource.OWN, b.source); assertEquals(1024, b.w); assertEquals(687, b.h)
        assertEquals(.44f, b.player.s, 1e-6f); assertEquals(.32f, b.wild.s, 1e-6f)
        assertFalse(b.crisp); assertTrue(b.dark) // dim purple interior: no extra dusk tint in the dark theme
        // ours on the clear floor right of the shelf/cart corner (x < .37, y > .75); the wild one on the floor in the middle/back
        assertTrue(b.player.x in .40f..0.45f && b.player.y in .72f..0.76f)
        assertTrue(b.wild.x in .58f..0.68f && b.wild.y in .50f..0.60f)
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
