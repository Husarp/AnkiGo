package com.husarp.ankigo.data

import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.ItemIconPlan
import com.husarp.ankigo.ui.ItemIconSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R19-E (PLAN 272): item icons use their own PokeAPI pin; Pokémon sprites keep theirs. */
class R19EItemIconsTest {
    private val nine = listOf("auspicious-armor", "malicious-armor", "galarica-cuff", "galarica-wreath", "syrupy-apple",
        "unremarkable-teacup", "black-augurite", "peat-block", "metal-alloy")

    @Test fun itemUrlUsesTheItemPin() {
        assertEquals("https://raw.githubusercontent.com/PokeAPI/sprites/${DataStore.ITEM_SPRITES_COMMIT}/sprites/items/peat-block.png",
            SpriteUrls.item("peat-block"))
        assertEquals(40, DataStore.ITEM_SPRITES_COMMIT.length)
        assertNotEquals(DataStore.SPRITES_COMMIT, DataStore.ITEM_SPRITES_COMMIT)
    }

    @Test fun pokemonUrlsKeepTheOldPin() {
        val api = "https://raw.githubusercontent.com/PokeAPI/sprites/${DataStore.SPRITES_COMMIT}/sprites/"
        assertEquals("${api}pokemon/25.png", SpriteUrls.static(25, false))
        assertTrue(SpriteUrls.staticChain(25, true).all { DataStore.SPRITES_COMMIT in it && DataStore.ITEM_SPRITES_COMMIT !in it })
        assertTrue(SpriteUrls.battle(TestData.species(25, "pikachu"), SpriteKind.FRONT, false)
            .none { DataStore.ITEM_SPRITES_COMMIT in it })
    }

    @Test fun nineNewItemsAreSpritesInTheCatalogue() {
        for (id in nine) {
            assertTrue("$id in catalogue", Items.def(id) != null)
            assertEquals(ItemIconSpec.Sprite("$id.png"), ItemIconPlan.of(id))
        }
    }

    @Test fun mirrorKeyOfAnItemUrlComesFromTheItemPin() {
        assertEquals("gh/PokeAPI/sprites@${DataStore.ITEM_SPRITES_COMMIT}/sprites/items/metal-alloy.png",
            Mirror.keyFor(SpriteUrls.item("metal-alloy")))
        assertEquals("gh/PokeAPI/sprites@${DataStore.SPRITES_COMMIT}/sprites/pokemon/25.png", Mirror.keyFor(SpriteUrls.static(25, false)))
    }
}
