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
    @Test fun freezeSourcesMatchTheCapAndEscalatingShopPrices() {
        for (data in listOf(null, TestData.world())) {
            val source = ItemInfo.whereToFind(data, StreakFreeze.ITEM)
            assertTrue(source.contains("Daily gift"))
            assertTrue(source.contains("Rewarded battle wins"))
            assertTrue(source.contains("Shop, until you hold 3"))
            assertTrue(source.contains("Every next freeze costs more"))
            assertFalse(source.contains("Not sold"))
            val facts = ItemInfo.facts(data, StreakFreeze.ITEM).joinToString("\n") { it.text }
            assertFalse(facts.contains("coins"))
            assertTrue(facts.contains("Hold up to **3**"))
        }
    }
}
