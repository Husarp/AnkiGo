package com.husarp.ankigo.data

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Lane D: 3D animations for gens 6-9 and regional forms (fallbacks, size limit, the one-time cache upgrade). */
class BattleCacheTest {
    private lateinit var dir: File
    private val gif = "GIF89a".toByteArray() + ByteArray(20)
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)

    @Before fun setUp() { dir = Files.createTempDirectory("ankigo-battle").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    private val sprigatito = com.husarp.ankigo.game.TestData.species(906, "sprigatito")

    @Test fun gen9WithoutAPixelAnimationGetsThe3dOne() {
        val asked = ArrayList<String>()
        val f = Sprites.download(File(dir, "front/906"), SpriteUrls.battle(sprigatito, SpriteKind.FRONT, false), get = { url, _ ->
            asked += url
            if ("/ani/" in url) gif else null
        })
        assertEquals("906.gif", f?.name)
        assertEquals(2, asked.size) // gen5ani (not found), then ani
    }

    @Test fun showdownDownFallsBackToPokeApisCopy() {
        val f = Sprites.download(File(dir, "front/906"), SpriteUrls.battle(sprigatito, SpriteKind.FRONT, false), get = { url, _ ->
            if (url.contains("pokemonshowdown")) throw java.io.IOException("down") else if ("other/showdown" in url) gif else null
        })
        assertEquals("906.gif", f?.name)
    }

    @Test fun noAnimationAnywhereEndsWithTheStillAndRemembersNothingBad() {
        val f = Sprites.download(File(dir, "front/1010"), SpriteUrls.battle(com.husarp.ankigo.game.TestData.species(1010, "ironleaves"), SpriteKind.FRONT, false),
            get = { url, _ -> if (url.endsWith("/gen5/ironleaves.png")) png else null })
        assertEquals("1010.png", f?.name)
        assertFalse(File(dir, "front/1010.missing").exists())
    }

    @Test fun aTooBigFileIsSkippedLikeAMissingOne() {
        val huge = "GIF89a".toByteArray() + ByteArray(Sprites.MAX_SPRITE_BYTES)
        val f = Sprites.download(File(dir, "front/906"), SpriteUrls.battle(sprigatito, SpriteKind.FRONT, false), get = { url, _ ->
            when {
                "/ani/" in url -> huge
                url.endsWith(".png") -> png
                else -> null
            }
        })
        assertEquals("906.png", f?.name)
        val none = Sprites.download(File(dir, "back/906"), listOf("https://x/ani-back/sprigatito.gif"), get = { _, _ -> huge })
        assertNull(none)
        assertTrue("too big counts as not there", File(dir, "back/906.missing").isFile)
    }

    @Test fun upgradeDropsOldStillsAndMissesOnceAndKeepsTheRest() {
        val front = File(dir, "front").apply { mkdirs() }
        val back = File(dir, "back").apply { mkdirs() }
        val static = File(dir, "static").apply { mkdirs() }
        File(front, "906.png").writeBytes(png); File(front, "25.gif").writeBytes(gif); File(back, "906.missing").writeText("1")
        File(back, "656s.png").writeBytes(png); File(static, "906.png").writeBytes(png)
        Sprites.upgradeBattleCache(dir)
        assertFalse(File(front, "906.png").exists()); assertFalse(File(back, "906.missing").exists()); assertFalse(File(back, "656s.png").exists())
        assertTrue(File(front, "25.gif").isFile); assertTrue(File(static, "906.png").isFile)
        // only once: a still saved after the upgrade (no animation anywhere) stays
        File(front, "1010.png").writeBytes(png)
        Sprites.upgradeBattleCache(dir)
        assertTrue(File(front, "1010.png").isFile)
    }
}
