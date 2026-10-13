package com.husarp.ankigo.data

import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteUrlsTest {
    private val api = "https://raw.githubusercontent.com/PokeAPI/sprites/${DataStore.SPRITES_COMMIT}/sprites/"
    private val bw = "${api}pokemon/versions/generation-v/black-white/animated/"
    private val sd = "https://play.pokemonshowdown.com/sprites/"
    private val pika = TestData.species(25, "pikachu")
    private val froakie = TestData.species(656, "froakie")

    @Test fun frontChainForGen5AndOlder() {
        assertEquals(listOf("${bw}25.gif", "${sd}gen5ani/pikachu.gif", "${sd}gen5/pikachu.png", "${api}pokemon/25.png"),
            SpriteUrls.battle(pika, SpriteKind.FRONT, false))
    }

    @Test fun frontChainSkipsPokeApiGifAfter649() {
        assertEquals(listOf("${sd}gen5ani/froakie.gif", "${sd}gen5/froakie.png", "${api}pokemon/656.png"),
            SpriteUrls.battle(froakie, SpriteKind.FRONT, false))
        assertEquals("${bw}649.gif", SpriteUrls.battle(TestData.species(649, "genesect"), SpriteKind.FRONT, false)[0])
    }

    @Test fun backChains() {
        assertEquals(listOf("${bw}back/25.gif", "${sd}gen5ani-back/pikachu.gif", "${sd}gen5-back/pikachu.png"),
            SpriteUrls.battle(pika, SpriteKind.BACK, false))
        assertEquals(listOf("${sd}gen5ani-back/froakie.gif", "${sd}gen5-back/froakie.png"),
            SpriteUrls.battle(froakie, SpriteKind.BACK, false))
    }

    @Test fun shinyChains() {
        assertEquals(listOf("${bw}shiny/25.gif", "${sd}gen5ani-shiny/pikachu.gif", "${sd}gen5-shiny/pikachu.png", "${api}pokemon/shiny/25.png"),
            SpriteUrls.battle(pika, SpriteKind.FRONT, true))
        assertEquals(listOf("${bw}back/shiny/25.gif", "${sd}gen5ani-back-shiny/pikachu.gif", "${sd}gen5-back-shiny/pikachu.png"),
            SpriteUrls.battle(pika, SpriteKind.BACK, true))
    }

    @Test fun staticAndItemUrls() {
        assertEquals("${api}pokemon/25.png", SpriteUrls.static(25, false))
        assertEquals("${api}pokemon/shiny/25.png", SpriteUrls.static(25, true))
        assertEquals("${api}items/potion.png", SpriteUrls.item("potion"))
    }

    @Test fun magicBytesAreChecked() {
        val gif = "GIF89a....".toByteArray()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)
        val html = "<html><body>404</body></html>".toByteArray()
        assertTrue(SpriteUrls.validFor("x/1.gif", gif)); assertFalse(SpriteUrls.validFor("x/1.gif", png))
        assertTrue(SpriteUrls.validFor("x/1.png", png)); assertFalse(SpriteUrls.validFor("x/1.png", gif))
        assertFalse(SpriteUrls.validFor("x/1.gif", html)); assertFalse(SpriteUrls.validFor("x/1.png", html))
        assertFalse(SpriteUrls.validFor("x/1.png", ByteArray(0)))
    }

    @Test fun lruEvictsLeastRecentlyUsedByBytes() {
        val lru = ByteLru<String>(10) { it.length.toLong() }
        lru.put("a", "aaaa"); lru.put("b", "bbbb")
        assertNotNull(lru.get("a")) // a is now the most recent
        lru.put("c", "cccc") // 12 bytes > 10: b (least recent) goes
        assertNull(lru.get("b"))
        assertNotNull(lru.get("a")); assertNotNull(lru.get("c"))
        lru.put("big", "x".repeat(11)) // bigger than the whole cache: not stored, nothing evicted
        assertNull(lru.get("big")); assertNotNull(lru.get("a"))
        lru.put("a", "aa") // replacing frees the old size
        lru.put("d", "dddddd")
        assertNull(lru.get("c")) // 2 + 4 + 6 = 12 > 10: the least recent (c) goes
        assertNotNull(lru.get("a")); assertNotNull(lru.get("d"))
    }
}
