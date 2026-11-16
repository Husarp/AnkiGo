package com.husarp.ankigo.ui

import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.*
import org.junit.Test

class BackdropCatalogTest {
    private val all get() = BackdropCatalog.ALL
    @Test fun approvedSetHasNoDroppedScenes() {
        assertEquals(36, BackdropCatalog.ALL.size)
        assertEquals(36, BackdropCatalog.ALL.map { it.id }.toSet().size)
        for (id in listOf("stormy-plains", "psychic-lab", "volcanocave", "fairy-hill", "bamboo-dojo-v2")) assertNull(id, BackdropCatalog.byId(id))
        assertEquals(0, BackdropCatalog.ALL.count { it.source == BackdropSource.SHOWDOWN })
        assertEquals(12, BackdropCatalog.GENERAL.size)
    }
    @Test fun bundledBatchHasExplicitTypeEligibility() {
        assertEquals(setOf(Type.FIRE, Type.ROCK), BackdropCatalog.typesFor("lava-field").toSet())
        assertEquals(listOf(Type.WATER), BackdropCatalog.typesFor("reef-bay"))
        for (id in BackdropCatalog.GENERAL) {
            val types = BackdropCatalog.typesFor(id)
            assertTrue(id, types.isNotEmpty() && types.size < Type.entries.size)
        }
        for (t in Type.entries) {
            val ids = BackdropCatalog.options(t).map { it.id }
            assertEquals(ids.size, ids.toSet().size)
            assertFalse(BackdropCatalog.SHRINE in ids)
        }
    }
    @Test fun wishiwashiNeverGetsLavaEvenWhenOnlyLavaIsEnabled() {
        val fish = TestData.species(746, name = "Wishiwashi", types = listOf(Type.WATER))
        val eligible = setOf("sandbar-isle", "reef-bay")
        assertEquals(eligible, BackdropCatalog.options(Type.WATER).map { it.id }.toSet())
        for (seed in (-100..100).toList() + listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertTrue(BackdropCatalog.pick(fish.types, seed, BackdropCatalog.isLegendary(fish)).id in eligible)
        }
        val onlyLava = BackdropCatalog.ALL.map { it.id }.toSet() - "lava-field"
        assertNull(BackdropCatalog.pickEnabled(fish.types, 0, false, onlyLava))
    }
    private fun assertBiomeVeto(type: Type, clashing: Set<String>) {
        val allIds = BackdropCatalog.ALL.map { it.id }.toSet()
        for (other in Type.entries) for (types in listOf(listOf(type, other), listOf(other, type))) {
            for (seed in (-50..50).toList() + listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
                val bg = BackdropCatalog.pickEnabled(types, seed, false, emptySet())
                if (types.toSet() == setOf(Type.FIRE, Type.ICE)) assertNull(bg)
                else assertNotNull("$types has a compatible scene", bg)
                if (bg != null) {
                    assertTrue("$types ${bg.id}", bg.id !in clashing)
                    assertTrue(types.any { it in BackdropCatalog.typesFor(bg.id) })
                }
            }
            for (id in clashing) {
                assertNull("$types must veto $id even if it is the only enabled scene",
                    BackdropCatalog.pickEnabled(types, 0, false, allIds - id))
            }
        }
    }
    @Test fun waterVetoesFireAndDesertInEitherTypeOrder() {
        assertBiomeVeto(Type.WATER, setOf("desert-oasis", "canyon", "fire-savanna", "lava-field", "volcano-b"))
    }
    @Test fun iceVetoesFireAndDesertInEitherTypeOrder() {
        assertBiomeVeto(Type.ICE, setOf("desert-oasis", "canyon", "fire-savanna", "lava-field", "volcano-b"))
    }
    @Test fun fireVetoesFrozenScenesInEitherTypeOrder() {
        assertBiomeVeto(Type.FIRE, setOf("snow-aurora", "snowy-pines", "snow-field"))
    }
    @Test fun grassVetoesBurningScenesInEitherTypeOrder() {
        assertBiomeVeto(Type.GRASS, setOf("fire-savanna", "lava-field", "volcano-b"))
    }
    @Test fun bugVetoesBurningScenesInEitherTypeOrder() {
        assertBiomeVeto(Type.BUG, setOf("fire-savanna", "lava-field", "volcano-b"))
    }
    @Test fun mutuallyClashingPoolsUseNeutralFallback() {
        for (types in listOf(listOf(Type.FIRE, Type.ICE), listOf(Type.ICE, Type.FIRE))) {
            for (seed in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
                assertNull(BackdropCatalog.pickEnabled(types, seed, false, emptySet()))
                assertEquals("countryside-route", BackdropCatalog.pick(types, seed).id)
            }
        }
    }
    @Test fun clashesTryCompatibleSecondaryScenesAndRespectDisabledChoices() {
        val types = listOf(Type.FIRE, Type.WATER)
        val expected = BackdropCatalog.options(Type.WATER).filter { it.id != "deep-sea-cave" }
        assertEquals(expected.toSet(), expected.indices.map {
            BackdropCatalog.pickEnabled(types, it, false, emptySet())
        }.toSet())
        assertNull(BackdropCatalog.pickEnabled(types, 0, false, expected.map { it.id }.toSet()))
        assertEquals("reef-bay", BackdropCatalog.pickEnabled(types, Int.MIN_VALUE, false,
            BackdropCatalog.ALL.map { it.id }.toSet() - "reef-bay")?.id)
    }
    @Test fun forestTechIsOnlyBugAndFairy() {
        assertEquals(setOf(Type.BUG, Type.FAIRY), Type.entries.filter { t -> BackdropCatalog.options(t).any { it.id == "forest-tech-b" } }.toSet())
    }
    @Test fun replacementsAndNewScenesHaveTheirAssignedTypes() {
        val expected = mapOf("crystal-storm" to setOf(Type.ELECTRIC, Type.ROCK),
            "normal-meadow" to setOf(Type.NORMAL), "grass-glade" to setOf(Type.GRASS, Type.BUG, Type.FAIRY),
            "poison-swamp" to setOf(Type.POISON), "flying-ridge" to setOf(Type.FLYING), "violet-plateau" to setOf(Type.PSYCHIC))
        for ((id, types) in expected) assertEquals(id, types, BackdropCatalog.typesFor(id).toSet())
        assertEquals(50, BackdropCatalog.byId("crystal-storm")!!.rev)
    }
    @Test fun keptOldScenesKeepTheirTypes() {
        assertEquals(listOf(Type.DARK), BackdropCatalog.typesFor("dark-town"))
        assertEquals(listOf(Type.BUG, Type.FAIRY), BackdropCatalog.typesFor("forest-tech-b"))
        assertEquals(listOf(Type.FIRE), BackdropCatalog.typesFor("fire-savanna"))
        assertEquals(listOf(Type.FIGHTING, Type.GROUND), BackdropCatalog.typesFor("fighting-training"))
    }
    @Test fun seedsAreStableAndCoverEachPool() {
        for (t in Type.entries) {
            val options = BackdropCatalog.options(t)
            assertEquals(options.toSet(), (options.indices).map { BackdropCatalog.pick(listOf(t), it) }.toSet())
            for (seed in listOf(Int.MIN_VALUE, -1, 0, Int.MAX_VALUE)) assertEquals(BackdropCatalog.pick(listOf(t), seed), BackdropCatalog.pick(listOf(t), seed))
        }
        assertEquals("uid".hashCode(), BackdropCatalog.seed("uid"))
    }
    @Test fun disabledScenesNeverAppearIncludingTheShrine() {
        val disabled = setOf(BackdropCatalog.SHRINE, "forest", "meadow") + BackdropCatalog.GENERAL
        for (t in Type.entries) for (seed in -100..100) for (legendary in listOf(false,true)) assertTrue(BackdropCatalog.pickEnabled(listOf(t), seed, legendary, disabled)?.id !in disabled)
    }
    @Test fun aTypeWithoutEligibleScenesUsesPlainWithoutBreakingTypeRestrictions() {
        val onlyForestTech = BackdropCatalog.ALL.map { it.id }.toSet() - "forest-tech-b"
        assertNull(BackdropCatalog.pickEnabled(listOf(Type.FIRE), 0, false, onlyForestTech))
        assertEquals("forest-tech-b", BackdropCatalog.pickEnabled(listOf(Type.BUG), 0, false, onlyForestTech)?.id)
        val onlyShrine = BackdropCatalog.ALL.map { it.id }.toSet() - BackdropCatalog.SHRINE
        assertNull(BackdropCatalog.pickEnabled(listOf(Type.BUG), 0, false, onlyShrine))
        assertEquals(BackdropCatalog.SHRINE, BackdropCatalog.pickEnabled(listOf(Type.BUG), 0, true, onlyShrine)?.id)
    }
    @Test fun aSingleBatchSceneOnlyWorksForItsAssignedTypes() {
        val disabled = BackdropCatalog.ALL.map { it.id }.toSet() - "autumn-forest"
        for (t in Type.entries) assertEquals(if (t in setOf(Type.GRASS, Type.BUG, Type.NORMAL)) "autumn-forest" else null,
            BackdropCatalog.pickEnabled(listOf(t), -1, false, disabled)?.id)
    }
    @Test fun legendaryAndTypeRulesRemain() {
        assertEquals(BackdropCatalog.SHRINE, BackdropCatalog.pick(listOf(Type.FIRE), 0, true).id)
        assertEquals(Type.FLYING, BackdropCatalog.typeFor(listOf(Type.NORMAL,Type.FLYING)))
        assertEquals(Type.NORMAL, BackdropCatalog.typeFor(emptyList()))
        assertEquals(Type.FIRE, BackdropCatalog.typeFor(listOf(Type.FIRE,Type.WATER)))
    }
    @Test fun standPointsAndFormatsAreValid() {
        for (b in BackdropCatalog.ALL) {
            assertTrue(b.w>0 && b.h>0)
            assertTrue(b.wild.y < b.player.y && b.wild.x > b.player.x && b.wild.s < b.player.s)
            assertTrue(b.ext in listOf("png","webp"))
            assertSame(b, BackdropCatalog.byId(b.id))
        }
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

    @Test fun everyBackgroundIsUsedBySomeType() {
        val used = Type.values().flatMap { BackdropCatalog.options(it) }.map { it.id }.toSet()
        // the shrine is in no type list: only Legendary / Mythical encounters get it
        assertEquals(all.map { it.id }.toSet() - BackdropCatalog.SHRINE, used)
    }

    @Test fun theNewBackgroundsAreInTheirTypeLists() {
        for (t in listOf(Type.ROCK, Type.GHOST, Type.PSYCHIC)) assertTrue("$t", "ruined-castle" in BackdropCatalog.options(t).map { it.id })
        assertTrue("dark-town" in BackdropCatalog.options(Type.DARK).map { it.id })
        assertEquals(setOf(Type.ROCK, Type.GHOST, Type.PSYCHIC), Type.values().filter { t -> BackdropCatalog.options(t).any { it.id == "ruined-castle" } }.toSet())
        assertEquals(setOf(Type.DARK), Type.values().filter { t -> BackdropCatalog.options(t).any { it.id == "dark-town" } }.toSet())
        for (t in Type.values()) assertTrue("$t", BackdropCatalog.options(t).none { it.id == BackdropCatalog.SHRINE })
    }

    @Test fun theNewShrineIsForLegendaryAndMythicalEncounters() {
        val shrine = BackdropCatalog.byId(BackdropCatalog.SHRINE)!!
        assertEquals("legendary-shrine", shrine.id)
        assertEquals(50, shrine.rev)
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
        assertEquals("legendary-shrine-50.webp", com.husarp.ankigo.data.Backdrops.fileName(BackdropCatalog.byId("legendary-shrine")!!))
        assertEquals("ruined-castle-50.webp", com.husarp.ankigo.data.Backdrops.fileName(BackdropCatalog.byId("ruined-castle")!!))
        assertEquals("dark-town-50.webp", com.husarp.ankigo.data.Backdrops.fileName(BackdropCatalog.byId("dark-town")!!))
        val town = BackdropCatalog.byId("dark-town")!!
        assertEquals(.68f, town.wild.x, 1e-6f) // a bit left of the rubble on the right
        assertTrue(town.dark)
        assertTrue(BackdropCatalog.byId("ruined-castle")!!.dark)
        for (id in listOf("legendary-shrine", "ruined-castle", "dark-town")) {
            val b = BackdropCatalog.byId(id)!!
            assertEquals(id, .44f, b.player.s, 1e-6f); assertEquals(id, .32f, b.wild.s, 1e-6f)
            assertTrue(id, b.crisp)
        }
    }

    @Test fun typeForRules() {
        assertEquals(Type.NORMAL, BackdropCatalog.typeFor(emptyList()))
        assertEquals(Type.NORMAL, BackdropCatalog.typeFor(listOf(Type.NORMAL)))
        assertEquals(Type.FLYING, BackdropCatalog.typeFor(listOf(Type.NORMAL, Type.FLYING)))
        assertEquals(Type.FIRE, BackdropCatalog.typeFor(listOf(Type.FIRE, Type.WATER)))
        assertEquals(Type.FLYING, BackdropCatalog.typeFor(listOf(Type.FLYING, Type.NORMAL)))
        assertEquals(Type.WATER, BackdropCatalog.typeFor(listOf(Type.WATER)))
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
