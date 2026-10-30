package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test

class R22IPurchaseTest {
    private val data = TestData.world()
    @Test fun batchIsAllOrNothingAndUsesTheCurrentPrice() {
        for (quantity in listOf(1, 3, 5)) {
            val state = TestData.state(data)
            state.bag.clear()
            val total = Shop.priceOf(data, state, "poke-ball") * quantity
            state.cash = total - 1
            assertFalse(Shop.buy(data, state, "poke-ball", quantity = quantity).ok)
            assertEquals(total - 1, state.cash)
            assertEquals(0, state.count("poke-ball"))
            state.cash = total
            assertTrue(Shop.buy(data, state, "poke-ball", quantity = quantity).ok)
            assertEquals(0, state.cash)
            assertEquals(quantity, state.count("poke-ball"))
        }
    }
    @Test fun invalidBatchDoesNotSpendAnything() {
        val state = TestData.state(data)
        state.cash = 10000
        assertFalse(Shop.buy(data, state, "poke-ball", quantity = 2).ok)
        assertEquals(10000, state.cash)
    }
}
