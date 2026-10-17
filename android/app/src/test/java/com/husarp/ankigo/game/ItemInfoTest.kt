package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R3-X2 item 3: the texts of the Shop's item popup. */
class ItemInfoTest {
    @Test fun everyCatalogueItemSaysWhatItDoesAndHowToUseIt() {
        for (id in Items.ALL.keys) {
            assertTrue("$id effect", ItemInfo.effect(id).isNotBlank() && ItemInfo.effect(id) != "No description.")
            assertTrue("$id how", ItemInfo.howToUse(id).isNotBlank())
        }
    }

    @Test fun theEffectIsTheCatalogueNote() {
        assertEquals("Heals 20 HP", ItemInfo.effect("potion"))
        assertEquals("Heals all HP", ItemInfo.effect("max-potion"))
        assertEquals("Held: ×1.5 XP", ItemInfo.effect("lucky-egg"))
    }

    @Test fun anUnknownItemHasAGenericLineAndNoHowTo() {
        assertEquals("No description.", ItemInfo.effect("not-an-item"))
        assertEquals("", ItemInfo.howToUse("not-an-item"))
    }

    @Test fun eachKindPointsToTheRightPlace() {
        assertTrue(ItemInfo.howToUse("poke-ball").contains("Throw"))
        assertTrue(ItemInfo.howToUse("potion").contains("Bag"))
        assertTrue(ItemInfo.howToUse("thunder-stone").contains("evolves"))
        assertTrue(ItemInfo.howToUse("lucky-egg").contains("holds one item"))
        assertTrue(ItemInfo.howToUse("poke-doll").contains("new") || ItemInfo.howToUse("poke-doll").contains("meet"))
        assertTrue(ItemInfo.howToUse("helix-fossil").contains("revive"))
        assertTrue(ItemInfo.tmHowToUse().contains("never used up"))
    }
}
