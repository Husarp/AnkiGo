package com.husarp.ankigo.data

import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BackdropSource
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

/**
 * R8-03: the battle pictures' URLs (Showdown's and Adam's own), cache files, extensions, the download gate and the mirror route
 * (a fake downloader records the URLs; no network, no Showdown or Adam file; the ids are names of scenes, not Pokémon data).
 */
class BackdropsTest {
    private lateinit var dir: File
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
    private val gif = "GIF89a\u0001\u0002".toByteArray()
    private fun webp(): ByteArray {
        val b = ByteArray(20)
        "RIFF".toByteArray().copyInto(b, 0); b[4] = 12
        "WEBPVP8 ".toByteArray().copyInto(b, 8)
        return b
    }

    private val forest = BackdropCatalog.byId("forest")!!        // Showdown, png
    private val river = BackdropCatalog.byId("river")!!          // Showdown, png
    private val reef = BackdropCatalog.byId("reef")!!            // own, png
    private val alley = BackdropCatalog.byId("night-alley")!!    // own, webp

    @Before fun setUp() { dir = Files.createTempDirectory("ankigo-bg").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    @Test fun aShowdownUrlIsTheFxFolderAndAnOwnOneIsTheAssetsRelease() {
        assertEquals("https://play.pokemonshowdown.com/fx/bg-forest.png", Backdrops.url(forest))
        assertEquals(Mirror.BASE + "bg-night-alley.webp", Backdrops.url(alley))
        assertEquals(Mirror.ownUrl("bg-reef.png"), Backdrops.url(reef))
        for (bg in BackdropCatalog.ALL) {
            val url = Backdrops.url(bg)
            if (bg.source == BackdropSource.SHOWDOWN) assertTrue(url, url.matches(Regex("https://play\\.pokemonshowdown\\.com/fx/bg-[a-z]+\\.png")))
            else assertTrue(url, url.startsWith(Mirror.BASE) && url.endsWith("bg-${bg.id}.${bg.ext}"))
        }
    }

    @Test fun theFileIsUnderTheSpriteFolderSoSizeAndClearIncludeIt() {
        val f = Backdrops.file(dir, forest)
        assertEquals(File(File(dir, "bg"), "forest.png"), f) // round 7's cached Showdown files keep their names
        assertTrue(f.path.startsWith(dir.path))
        assertEquals(File(File(dir, "bg"), "night-alley.webp"), Backdrops.file(dir, alley))
        assertEquals(43, BackdropCatalog.ALL.map { Backdrops.file(dir, it) }.toSet().size)
    }

    @Test fun picturesNeedSpriteDownloadsAndTheSettingBothOn() {
        assertTrue(Backdrops.allowed(spritesOn = true, picturesOn = true))
        assertFalse(Backdrops.allowed(spritesOn = false, picturesOn = true))
        assertFalse(Backdrops.allowed(spritesOn = true, picturesOn = false))
        assertFalse(Backdrops.allowed(spritesOn = false, picturesOn = false))
    }

    @Test fun downloadSavesAShowdownPngWhereTheFileSays() {
        val asked = ArrayList<String>()
        val f = Backdrops.fetchFile(dir, river) { url, _ -> asked += url; png }
        assertEquals(Backdrops.file(dir, river), f)
        assertTrue(f!!.isFile)
        assertEquals(listOf(Backdrops.url(river)), asked)
        assertFalse(File(dir, "bg/river.missing").exists())
    }

    @Test fun downloadSavesAnOwnPngAndAnOwnWebpWithTheirExtensions() {
        val asked = ArrayList<String>()
        val a = Backdrops.fetchFile(dir, reef) { url, _ -> asked += url; png }
        val b = Backdrops.fetchFile(dir, alley) { url, _ -> asked += url; webp() }
        assertEquals(File(dir, "bg/reef.png"), a)
        assertEquals(File(dir, "bg/night-alley.webp"), b)
        assertEquals(Backdrops.file(dir, alley), b)
        assertEquals(listOf(Backdrops.url(reef), Backdrops.url(alley)), asked)
        assertEquals(12, b!!.readBytes()[4].toInt())
        assertTrue(dir.walkTopDown().none { it.name.endsWith(".tmp") })
    }

    @Test fun everyBackgroundSavesUnderTheNameTheCacheLooksFor() {
        for (bg in BackdropCatalog.ALL) {
            val body = when (bg.ext) { "webp" -> webp(); else -> png }
            assertEquals(bg.id, Backdrops.file(dir, bg), Backdrops.fetchFile(dir, bg) { _, _ -> body })
        }
    }

    @Test fun validityOfPngGifAndWebpFollowsTheUrl() {
        assertTrue(SpriteUrls.validFor("https://x/a.png", png))
        assertTrue(SpriteUrls.validFor("https://x/a.gif", gif))
        assertTrue(SpriteUrls.validFor("https://x/a.webp", webp()))
        assertFalse(SpriteUrls.validFor("https://x/a.webp", png))
        assertFalse(SpriteUrls.validFor("https://x/a.png", webp()))
        assertFalse(SpriteUrls.validFor("https://x/a.webp", "<html>404</html>".toByteArray()))
        assertFalse(SpriteUrls.validFor("https://x/a.webp", ByteArray(8))) // too short for a header
        val riffWave = webp().also { "WAVE".toByteArray().copyInto(it, 8) }
        assertFalse(SpriteUrls.isWebp(riffWave))
    }

    @Test fun anErrorPageIsNeverSavedAsAWebpOrPng() {
        assertNull(Backdrops.fetchFile(dir, alley) { _, _ -> "<html>404</html>".toByteArray() })
        assertFalse(Backdrops.file(dir, alley).exists())
        assertNull(Backdrops.fetchFile(dir, forest) { _, _ -> webp() }) // a WebP where a PNG is promised
        assertFalse(Backdrops.file(dir, forest).exists())
    }

    @Test fun noDownloadWhenItIsOff() {
        var calls = 0
        val pic = Backdrops.fetchBlocking(dir, forest, download = false) { _, _ -> calls++; png }
        assertNull(pic)
        assertEquals(0, calls)
        assertFalse(Backdrops.file(dir, forest).exists())
        assertFalse(File(dir, "bg/forest.missing").exists())
    }

    @Test fun aNotFoundAnswerLeavesANoteAndIsNotAskedAgainRightAway() {
        var calls = 0
        assertNull(Backdrops.fetchFile(dir, reef) { _, _ -> calls++; null })
        assertTrue(File(dir, "bg/reef.missing").isFile)
        assertNull(Backdrops.fetchFile(dir, reef) { _, _ -> calls++; png })
        assertEquals(1, calls)
    }

    @Test fun networkTroubleLeavesNoNoteSoALaterTryAsksAgain() {
        assertNull(Backdrops.fetchFile(dir, alley) { _, _ -> throw IOException("down") })
        assertFalse(File(dir, "bg/night-alley.missing").exists())
        assertEquals(Backdrops.file(dir, alley), Backdrops.fetchFile(dir, alley) { _, _ -> webp() })
    }

    @Test fun theDownloadGoesThroughTheGetItIsGiven() {
        // The default `get` is the mirror (Mirror.get: pack first, the URL second); a fake one records what is asked for.
        val seen = ArrayList<String>()
        val fake: HttpGet = { url, _ -> seen += url; png }
        Backdrops.fetchFile(dir, forest, fake)
        Backdrops.fetchFile(dir, reef, fake)
        assertEquals(listOf(Backdrops.url(forest), Backdrops.url(reef)), seen)
    }

    @Test fun aLoadFinishingAfterForgetIsDropped() {
        val old = Backdrops.currentGeneration()
        Backdrops.forget()
        assertNull(Backdrops.finish(old, "forest", null))
        assertTrue(Backdrops.finish(Backdrops.currentGeneration(), "forest", null)!!.isEmpty())
        assertNull(Backdrops.inMemory(forest))
    }

    @Test fun theGenerationBumpsOnEveryForget() {
        val a = Backdrops.currentGeneration()
        Backdrops.forget()
        val b = Backdrops.currentGeneration()
        Backdrops.forget()
        assertEquals(a + 1, b)
        assertEquals(b + 1, Backdrops.currentGeneration())
    }

    @Test fun theDefaultGetAsksTheMirrorAndAnOwnFileGoesStraightToTheRelease() {
        // the default `get` is Mirror.get; a fake host records what the mirror asks for (no index: the originals are asked)
        val asked = ArrayList<String>()
        Mirror.resetForTest()
        Mirror.logSink = { }
        Mirror.host = object : MirrorHost {
            override fun get(url: String, timeoutMs: Int, progress: ((Long, Long) -> Unit)?): ByteArray? { asked += url; return if (url.endsWith("index.json.gz")) null else png }
            override fun range(url: String, offset: Long, length: Int, timeoutMs: Int, progress: ((Long, Long) -> Unit)?): ByteArray? = null
        }
        try {
            assertEquals(png.toList(), Backdrops.get(Backdrops.url(forest), null)!!.toList())
            assertTrue("the mirror's index is looked at before the original", asked.first().endsWith("index.json.gz"))
            assertEquals(Backdrops.url(forest), asked.last())
            asked.clear()
            Backdrops.get(Backdrops.url(reef), null)
            assertEquals("an own file has no mirror key: straight to the release", listOf(Backdrops.url(reef)), asked)
        } finally {
            Mirror.resetForTest()
        }
    }

    @Test fun aCatalogEntryNeedsNothingElseForItsPath() {
        // any Backdrop (not only the catalog's) maps to bg/<id>.<ext>: the cache path has no other input
        val x = Backdrop(forest.id + "-x", BackdropSource.OWN, "webp", 10, 10, forest.player, forest.wild, crisp = false, dark = false)
        assertEquals(File(dir, "bg/forest-x.webp"), Backdrops.file(dir, x))
        assertEquals(Mirror.ownUrl("bg-forest-x.webp"), Backdrops.url(x))
    }
}
