package com.husarp.ankigo.game

import org.junit.Assert.*
import org.junit.Test

class R22IPurchaseTest {
    private val data = TestData.world()
    @Test fun batchIsAllOrNothingAndUsesTheCurrentPrice() {
        for (quantity in listOf(1, 3, 5)) {
            val state = TestData.state(data)
            state.bag.clear()
            Shop.ensureToday(data, state)
            val ball = Shop.dailyBalls(state.day).first()
            val total = Shop.priceOf(data, state, ball) * quantity
            state.cash = total - 1
            assertFalse(Shop.buy(data, state, ball, quantity = quantity).ok)
            assertEquals(total - 1, state.cash)
            assertEquals(0, state.count(ball))
            state.cash = total
            assertTrue(Shop.buy(data, state, ball, quantity = quantity).ok)
            assertEquals(0, state.cash)
            assertEquals(quantity, state.count(ball))
        }
    }
    @Test fun invalidBatchDoesNotSpendAnything() {
        val state = TestData.state(data)
        Shop.ensureToday(data, state)
        val ball = Shop.dailyBalls(state.day).first()
        state.cash = 10000
        assertFalse(Shop.buy(data, state, ball, quantity = 2).ok)
        assertEquals(10000, state.cash)
    }
}
