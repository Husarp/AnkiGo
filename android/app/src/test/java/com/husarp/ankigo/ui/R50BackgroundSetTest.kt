package com.husarp.ankigo.ui

import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.game.Type
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class R50BackgroundSetTest {
    private val removed = setOf("tropical-cove", "coral-lagoon", "coral-shallows", "toxic-cave", "beach", "beachshore",
        "city", "dampcave", "desert", "earthycave", "forest", "icecave", "meadow", "mountain", "river", "route",
        "dragon-peak", "deep-sea-cave", "night-forest", "factory", "cliff-clouds", "flower-field", "jungle",
        "sunset-beach", "ruins-courtyard", "ice-lake", "poison-marsh", "crystal-cave", "garden", "night-alley",
        "reef", "rocky-path", "stormy-plains", "temple", "volcanocave")

    @Test fun removedScenesAreAbsentFromCatalogPoolsAndThumbnails() {
        val ids = BackdropCatalog.ALL.map { it.id }.toSet()
        assertEquals(36, ids.size)
        assertTrue(ids.intersect(removed).isEmpty())
        assertEquals(ids, SceneryRims.groups.keys)
        for (id in removed) assertNull(id, BackdropCatalog.byId(id))
        for (type in Type.entries) assertTrue(BackdropCatalog.options(type).all { it.id in ids && it.id !in removed })
        assertEquals(ids, File("src/main/assets/backdrop-thumbs").list()!!.map { it.removeSuffix(".webp") }.toSet())
    }

    @Test fun everyTypeHasArtAndEveryOrdinarySceneHasTypeChips() {
        for (type in Type.entries) {
            val scenes = BackdropCatalog.options(type)
            assertTrue("$type has art", scenes.isNotEmpty())
            assertEquals(scenes.size, scenes.toSet().size)
            assertTrue(scenes.all { type in BackdropCatalog.typesFor(it.id) })
        }
        for (bg in BackdropCatalog.ALL) assertEquals(bg.id, bg.id == BackdropCatalog.SHRINE, BackdropCatalog.typesFor(bg.id).isEmpty())
    }

    @Test fun bundledFilesAreWebpBelowSixtyThousandBytesWithNoOrphans() {
        val bundled = File("src/main/assets/backdrops").listFiles()!!.toList()
        val names = BackdropCatalog.ALL.map { Backdrops.fileName(it) }.toSet()
        assertEquals(15, bundled.size)
        for (f in bundled) {
            assertTrue(f.name, f.name in names)
            assertTrue(f.name, f.length() in 1..60_000)
            val bytes = f.readBytes()
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WEBP", String(bytes, 8, 4, Charsets.US_ASCII))
        }
    }

    @Test fun staleDisabledIdsDoNotRemoveNewChoicesOrChangeFallback() {
        for (type in Type.entries) for (seed in listOf(Int.MIN_VALUE, -1, 0, Int.MAX_VALUE)) {
            assertSame(BackdropCatalog.pickEnabled(listOf(type), seed, false, emptySet()),
                BackdropCatalog.pickEnabled(listOf(type), seed, false, removed))
        }
        val all = BackdropCatalog.ALL.map { it.id }.toSet()
        assertNull(BackdropCatalog.pickEnabled(listOf(Type.WATER), 0, false, all + removed))
        assertEquals("countryside-route", BackdropCatalog.pick(listOf(Type.FIRE, Type.ICE), 0).id)
    }

    @Test fun changedPicturesCannotReuseOlderInstallsCachedFiles() {
        for (bg in BackdropCatalog.ALL.filter { it.id !in setOf("normal-meadow", "grass-glade", "poison-swamp", "flying-ridge", "violet-plateau") }) {
            assertEquals(bg.id, 50, bg.rev)
            assertEquals("${bg.id}-50.webp", Backdrops.fileName(bg))
        }
    }
}
