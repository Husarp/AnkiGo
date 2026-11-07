package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test

class R30NItemTextTest {
    @Test fun everyCatalogueItemHasCurrentBalancedInfo() {
        for (id in Items.ALL.keys) {
            val text = ItemInfo.facts(null, id).joinToString("\n") { it.text } + ItemInfo.howToUse(id)
            assertFalse(id, text.contains("no decay", true))
            assertFalse(id, text.contains("friendship", true))
            assertFalse(id, text.contains("No description"))
            assertTrue(id, text.split("**").size % 2 == 1)
            if (Items.def(id)?.kind == ItemKind.EVOLUTION && id !in Items.RETIRED) {
                assertTrue(id, text.contains("Level 2 Bond"))
                assertTrue(id, text.contains("Evolution section"))
            }
        }
    }
    @Test fun itemSourcesFollowTheShopCatalogueWithoutPrices() {
        assertTrue(ItemInfo.whereToFind(null, "amulet-coin").contains("Weekend Shop"))
        assertTrue(ItemInfo.whereToFind(null, "metal-alloy").contains("Not sold"))
        assertFalse(ItemInfo.whereToFind(null, "master-ball").contains("Shop"))
        assertTrue(ItemInfo.whereToFind(null, "chipped-pot").contains("Not sold or found"))
    }
}
