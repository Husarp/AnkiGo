package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

class R22IPlanTest {
    @Test fun quantityCyclesAndAlwaysRequiresFreshConfirmation() {
        val open = OpenCard()
        open.open("basic:potion")
        assertEquals(1, open.quantity)
        open.askBuy(); assertTrue(open.buying)
        open.cycleQuantity(); assertEquals(3, open.quantity); assertFalse(open.buying)
        open.cycleQuantity(); assertEquals(5, open.quantity)
        open.cycleQuantity(); assertEquals(1, open.quantity)
        open.askBuy(); open.bought(); assertFalse(open.buying)
        open.cycleQuantity(); open.open("other"); assertEquals(1, open.quantity)
        open.askBuy(); open.reset(); assertFalse(open.buying)
        open.askBuy(); assertFalse(open.buying)
    }
}
