package com.husarp.ankigo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailsOnceTest {
    @Test fun nothingAsked() = assertNull(DetailsOnce().take { true })

    @Test fun handedOutOnce() {
        val d = DetailsOnce()
        d.ask("m1")
        assertEquals("m1", d.take { true })
        assertNull(d.take { true })
    }

    @Test fun unknownMonIsDroppedNotRetried() {
        val d = DetailsOnce()
        d.ask("gone")
        assertNull(d.take { false })
        assertNull(d.take { true })
    }

    @Test fun askAgainAfterTake() {
        val d = DetailsOnce()
        d.ask("a"); d.take { true }
        d.ask("b")
        assertEquals("b", d.take { it == "b" })
    }
}
