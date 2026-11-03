package com.husarp.ankigo.ui

import org.junit.Assert.*
import org.junit.Test

class R29DetailOrderTest {
    @Test fun defaultKeepsBondLowerAndHoldingWithEvolution() {
        assertEquals(listOf("evolution", "stats", "moves", "matchups", "training", "bond", "info"), DetailOrder.default.map { it.key })
        assertEquals("Holding & Evolution", DetailOrder.label(DetailSection.EVOLUTION))
    }

    @Test fun missingUnknownAndDuplicateKeysNeverLoseASection() {
        assertEquals(DetailOrder.default, DetailOrder.resolve(emptyList()))
        val order = DetailOrder.resolve(listOf("info", "removed", "stats", "info"))
        assertEquals(listOf(DetailSection.INFO, DetailSection.STATS), order.take(2))
        assertEquals(DetailSection.entries.toSet(), order.toSet())
        assertEquals(7, order.size)
        assertEquals(DetailSection.BOND, order.last())
    }

    @Test fun movingFromEitherEndKeepsEverySectionAndRelativeOrder() {
        val order = DetailOrder.move(DetailOrder.default, DetailSection.BOND, 0)
        assertEquals(DetailSection.BOND, order.first())
        assertEquals(DetailOrder.default.filter { it != DetailSection.BOND }, order.drop(1))
        val back = DetailOrder.move(order, DetailSection.BOND, 99)
        assertEquals(DetailSection.BOND, back.last())
        assertEquals(7, back.distinct().size)
    }
}
