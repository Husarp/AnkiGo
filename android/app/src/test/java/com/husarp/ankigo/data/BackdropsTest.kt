package com.husarp.ankigo.data

import com.husarp.ankigo.ui.BackdropRules
import com.husarp.ankigo.game.Type
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** R7-07: the Showdown battle pictures' names, URL, cache file and the download gate (a fake downloader; no network, no Showdown file). */
class BackdropsTest {
    private lateinit var dir: File
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)

    @Before fun setUp() { dir = Files.createTempDirectory("ankigo-bg").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    @Test fun theFourteenNamesAreTheExpectedOnes() {
        assertEquals(
            listOf("beach", "beachshore", "city", "dampcave", "deepsea", "desert", "earthycave", "forest", "icecave", "meadow", "mountain", "river", "route", "volcanocave"),
            Backdrops.NAMES,
        )
        assertEquals(14, Backdrops.NAMES.toSet().size)
    }

    @Test fun theUrlIsShowdownsFxFolder() {
        assertEquals("https://play.pokemonshowdown.com/fx/bg-meadow.png", Backdrops.url("meadow"))
        for (n in Backdrops.NAMES) assertTrue(Backdrops.url(n).matches(Regex("https://play\\.pokemonshowdown\\.com/fx/bg-[a-z]+\\.png")))
    }

    @Test fun theFileIsUnderTheSpriteFolderSoSizeAndClearIncludeIt() {
        val f = Backdrops.file(dir, "forest")
        assertEquals(File(File(dir, "bg"), "forest.png"), f)
        assertTrue(f.path.startsWith(dir.path))
    }

    @Test fun everyPictureTheRulesPickIsADownloadableName() {
        for (t in Type.entries) for (c in BackdropRules.options(t)) assertTrue(c.name in Backdrops.NAMES)
    }

    @Test fun picturesNeedSpriteDownloadsAndTheSettingBothOn() {
        assertTrue(Backdrops.allowed(spritesOn = true, picturesOn = true))
        assertFalse(Backdrops.allowed(spritesOn = false, picturesOn = true))
        assertFalse(Backdrops.allowed(spritesOn = true, picturesOn = false))
        assertFalse(Backdrops.allowed(spritesOn = false, picturesOn = false))
    }

    @Test fun downloadSavesThePngWhereTheFileSays() {
        val asked = ArrayList<String>()
        val f = Backdrops.fetchFile(dir, "river") { url, _ -> asked += url; png }
        assertEquals(Backdrops.file(dir, "river"), f)
        assertTrue(f!!.isFile)
        assertEquals(listOf(Backdrops.url("river")), asked)
        assertFalse(File(dir, "bg/river.missing").exists())
    }

    @Test fun noDownloadWhenItIsOff() {
        var calls = 0
        val bmp = Backdrops.fetchBlocking(dir, "forest", download = false) { _, _ -> calls++; png }
        assertNull(bmp)
        assertEquals(0, calls)
        assertFalse(Backdrops.file(dir, "forest").exists())
        assertFalse(File(dir, "bg/forest.missing").exists())
    }

    @Test fun aNotFoundAnswerLeavesANoteAndIsNotAskedAgainRightAway() {
        var calls = 0
        assertNull(Backdrops.fetchFile(dir, "city") { _, _ -> calls++; null })
        assertTrue(File(dir, "bg/city.missing").isFile)
        assertNull(Backdrops.fetchFile(dir, "city") { _, _ -> calls++; png })
        assertEquals(1, calls)
    }

    @Test fun networkTroubleLeavesNoNoteSoALaterTryAsksAgain() {
        assertNull(Backdrops.fetchFile(dir, "beach") { _, _ -> throw IOException("down") })
        assertFalse(File(dir, "bg/beach.missing").exists())
        assertEquals(Backdrops.file(dir, "beach"), Backdrops.fetchFile(dir, "beach") { _, _ -> png })
    }

    @Test fun anErrorPageIsNeverSavedAsAPicture() {
        assertNull(Backdrops.fetchFile(dir, "desert") { _, _ -> "<html>404</html>".toByteArray() })
        assertFalse(Backdrops.file(dir, "desert").exists())
    }

    @Test fun aLoadFinishingAfterForgetIsDropped() {
        val old = Backdrops.currentGeneration()
        Backdrops.forget()
        assertNull(Backdrops.finish(old, "forest", null))
        assertTrue(Backdrops.finish(Backdrops.currentGeneration(), "forest", null)!!.isEmpty())
        assertNull(Backdrops.inMemory("forest"))
    }
}
