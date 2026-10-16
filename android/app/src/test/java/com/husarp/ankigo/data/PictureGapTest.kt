package com.husarp.ankigo.data

import com.husarp.ankigo.game.TestData
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** R1-12 (PLAN 101): a Pokémon of the late DLC range showed the "no sprite" placeholder; a wrong "not found" note must not stick. */
class PictureGapTest {
    private lateinit var dir: File
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
    private val hour = 3600_000L

    @Before fun setUp() { dir = Files.createTempDirectory("ankigo-gap").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    private val api = "https://raw.githubusercontent.com/PokeAPI/sprites/${DataStore.SPRITES_COMMIT}/sprites/"
    private val sd = "https://play.pokemonshowdown.com/sprites/"

    @Test fun lateDlcSpeciesGetTheirOwnNumberAndName() {
        val sp = TestData.species(1018, "madeupmon")
        assertEquals(
            listOf("${sd}gen5ani/madeupmon.gif", "${sd}ani/madeupmon.gif", "${api}pokemon/other/showdown/1018.gif", "${sd}gen5/madeupmon.png", "${api}pokemon/1018.png"),
            SpriteUrls.battle(sp, SpriteKind.FRONT, false))
        assertEquals("${sd}ani-back-shiny/madeupmon.gif", SpriteUrls.battle(sp, SpriteKind.BACK, true)[1])
        assertEquals("${api}pokemon/shiny/1018.png", SpriteUrls.static(1018, true))
        for (n in 1009..1025) {
            val s = TestData.species(n, "mon$n")
            val front = SpriteUrls.battle(s, SpriteKind.FRONT, false)
            assertTrue("no BW gif for #$n", front.none { "black-white" in it })
            assertTrue(front.contains("${sd}ani/mon$n.gif"))
            assertEquals("${api}pokemon/$n.png", front.last())
            assertEquals("${api}pokemon/$n.png", SpriteUrls.staticChain(n, false)[0])
        }
    }

    @Test fun aNotFoundNoteForAPictureThatMustExistIsTriedAgainAfterAnHour() {
        val base = File(dir, "static/1018")
        base.parentFile.mkdirs()
        val note = File(dir, "static/1018.missing").apply { writeText("1"); setLastModified(System.currentTimeMillis() - 2 * hour) }
        var asked = 0
        val get: HttpGet = { _, _ -> asked++; png }
        // the old 7-day rule still blocks
        assertNull(Sprites.download(base, SpriteUrls.staticChain(1018, false), get = get))
        assertEquals(0, asked)
        // the picture rule (1 hour) lets it through and clears the note
        assertNotNull(Sprites.download(base, SpriteUrls.staticChain(1018, false), get = get, retryMissingMs = hour))
        assertFalse(note.exists())
        assertTrue(File(dir, "static/1018.png").isFile)
    }

    @Test fun aFreshNoteStillStopsTheRequests() {
        val base = File(dir, "static/1018")
        base.parentFile.mkdirs()
        File(dir, "static/1018.missing").writeText("1")
        var asked = 0
        assertNull(Sprites.download(base, SpriteUrls.staticChain(1018, false), get = { _, _ -> asked++; png }, retryMissingMs = hour))
        assertEquals(0, asked)
    }

    @Test fun aDiskProblemIsNotRememberedAsNotFound() {
        val front = File(dir, "front").apply { mkdirs() }
        File(front, "1018.png").apply { mkdirs() }.also { File(it, "x").writeText("blocks the rename") }
        assertNull(Sprites.download(File(front, "1018"), listOf("https://x/y.png"), get = { _, _ -> png }))
        assertFalse(File(front, "1018.missing").exists())
    }

    @Test fun upgradeDropsOldStaticNotesOnceAndKeepsThePictures() {
        val static = File(dir, "static").apply { mkdirs() }
        File(static, "1018.missing").writeText("1"); File(static, "25.png").writeBytes(png)
        Sprites.upgradeBattleCache(dir)
        assertFalse(File(static, "1018.missing").exists())
        assertTrue(File(static, "25.png").isFile)
        // a note written after the upgrade is a real one (the upgrade runs once)
        File(static, "1019.missing").writeText("1")
        Sprites.upgradeBattleCache(dir)
        assertTrue(File(static, "1019.missing").isFile)
    }

    @Test fun gapsListMissingBlockedAndBrokenPictures() {
        val static = File(dir, "static").apply { mkdirs() }
        File(static, "25.png").writeBytes(png)
        File(static, "1017.png").writeBytes(byteArrayOf(1, 2, 3))
        File(static, "1018.missing").writeText("1")
        File(static, "1018.missing").setLastModified(System.currentTimeMillis() - 3 * 86_400_000L)
        val keys = listOf(25 to false, 1018 to false, 1017 to false, 1018 to false, 1020 to true)
        val gaps = Sprites.pictureGaps(static, keys, decodes = { it.length() > 4 })
        assertEquals(listOf(1017, 1018, 1020), gaps.map { it.num })
        assertTrue(gaps[0].broken); assertNull(gaps[0].noteAgeMs)
        assertFalse(gaps[1].broken); assertEquals(3L, gaps[1].noteAgeMs!! / 86_400_000L)
        assertTrue(gaps[2].shiny)
        val lines = Sprites.describeGaps(gaps) { "Mon$it" }
        assertEquals("#1017 Mon1017: picture file can't be read", lines[0])
        assertEquals("#1018 Mon1018: no picture on the phone, a 'not found' note from 3 days ago blocks the download", lines[1])
        assertEquals("#1020 Mon1020 (shiny): no picture on the phone", lines[2])
    }

    @Test fun noGapsWhenEveryPictureIsThere() {
        val static = File(dir, "static").apply { mkdirs() }
        File(static, "1018.png").writeBytes(png); File(static, "1018s.png").writeBytes(png)
        assertTrue(Sprites.pictureGaps(static, listOf(1018 to false, 1018 to true)).isEmpty())
    }
}
