package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** R18-H (PLAN 237): the Shop's money pill starts with the real amount, or with no text (hidden), never with "0". */
class R18HShopPillTest {
    @Test fun aLoadedGameStartsWithTheAmount() {
        assertEquals("1,250", ShopPlan.pillText(1250))
        assertEquals("0", ShopPlan.pillText(0))   // a real zero is shown: the player has no money
        assertEquals("20", ShopPlan.pillText(20))
    }

    @Test fun anUnloadedGameHasNoPillText() {
        assertEquals("", ShopPlan.pillText(null))
    }

    // R18 RV-6: the Pokémon tab shares the Shop's start
    @Test fun firstPillIsTheAmountOnlyWhileTheGameIsReady() {
        assertEquals("1,250", ShopPlan.firstPill(true, 1250))
        assertEquals("0", ShopPlan.firstPill(true, 0))
        assertEquals("", ShopPlan.firstPill(true, null))
        assertEquals("", ShopPlan.firstPill(false, 1250))
    }
}
