package com.husarp.ankigo.ui.movefx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** R7-FIX 6: the art cache loads once, warms off the calling thread, and never decodes on the drawing thread while it warms. Pure JVM. */
class ArtCacheTest {
    private class Loader(val missing: Set<String> = emptySet(), val failing: Set<String> = emptySet()) : (String) -> String? {
        val calls = AtomicInteger()
        val threads = java.util.Collections.synchronizedList(ArrayList<String>())
        override fun invoke(key: String): String? {
            calls.incrementAndGet(); threads += Thread.currentThread().name
            if (key in failing) error("boom")
            return if (key in missing) null else "art:$key"
        }
    }

    @Test fun withoutAWarmUpItLoadsOnDemandAndKeepsIt() {
        val l = Loader(); val c = ArtCache(l)
        assertEquals("art:a", c.get("a")); assertEquals("art:a", c.get("a"))
        assertEquals(1, l.calls.get())
        assertTrue(c.isReady("a")); assertFalse(c.isReady("b"))
        assertFalse(c.warmStarted)
    }

    @Test fun aKeyWithNoArtIsAskedOnlyOnce() {
        val l = Loader(missing = setOf("x")); val c = ArtCache(l)
        assertNull(c.get("x")); assertNull(c.get("x"))
        assertEquals(1, l.calls.get())
    }

    @Test fun whileWarmingNothingIsLoadedOnTheCallingThreadAndTheReadyOnesDraw() {
        val l = Loader(); val c = ArtCache(l)
        var work: Runnable? = null
        assertTrue(c.warm(listOf("a", "b", "c")) { work = it })
        assertTrue(c.warming); assertTrue(c.warmStarted)
        assertNull("not ready yet: skipped, not decoded now", c.get("a"))
        assertNull(c.get("zz"))
        assertEquals("the drawing thread decoded nothing", 0, l.calls.get())
        work!!.run()
        assertFalse(c.warming); assertTrue(c.warmStarted)
        assertEquals(3, l.calls.get())
        assertEquals("art:b", c.get("b"))
        assertEquals("a key outside the list loads on demand after the warm-up", "art:zz", c.get("zz"))
        assertEquals(4, l.calls.get())
    }

    @Test fun halfwayThroughTheReadyKeysDrawAndTheOthersAreSkipped() {
        val gate = CountDownLatch(1); val atB = CountDownLatch(1)
        val seen = java.util.Collections.synchronizedList(ArrayList<String>())
        val c = ArtCache<String> { k -> seen += k; if (k == "b") { atB.countDown(); gate.await(5, TimeUnit.SECONDS) }; "art:$k" }
        lateinit var t: Thread
        c.warm(listOf("a", "b", "c")) { t = Thread(it, "warm-test").also { th -> th.start() } }
        assertTrue(atB.await(5, TimeUnit.SECONDS))                        // the warm-up sits inside "b"
        assertEquals("art:a", c.get("a"))                                 // ready: draws
        assertNull(c.get("b")); assertNull(c.get("c"))                    // not ready: skipped for this frame
        gate.countDown(); t.join(5000)
        assertEquals("art:b", c.get("b")); assertEquals("art:c", c.get("c"))
        assertEquals(listOf("a", "b", "c"), seen.toList())                // each decoded once, by the warm-up
    }

    @Test fun theWarmUpRunsOnItsOwnThread() {
        val l = Loader(); val c = ArtCache(l)
        lateinit var t: Thread
        c.warm(listOf("a", "b")) { t = Thread(it, "fx-test-warm").also { th -> th.start() } }
        t.join(5000)
        assertEquals(listOf("fx-test-warm", "fx-test-warm"), l.threads.toList())
        assertNotEquals(Thread.currentThread().name, l.threads[0])
    }

    @Test fun itWarmsOnlyOnce() {
        val l = Loader(); val c = ArtCache(l)
        var starts = 0
        assertTrue(c.warm(listOf("a")) { starts++; it.run() })
        assertFalse(c.warm(listOf("a", "b")) { starts++; it.run() })
        assertEquals(1, starts)
        assertEquals(1, l.calls.get())
        assertFalse("b was never part of a warm-up", c.isReady("b"))
    }

    @Test fun aFailingKeyDoesNotStopTheRestAndIsTriedAgainLater() {
        val l = Loader(failing = setOf("bad")); val c = ArtCache(l)
        c.warm(listOf("a", "bad", "c")) { it.run() }
        assertFalse(c.warming)
        assertTrue(c.isReady("a")); assertTrue(c.isReady("c")); assertFalse(c.isReady("bad"))
        var failed = false
        try { c.get("bad") } catch (_: IllegalStateException) { failed = true }
        assertTrue("the key is tried again, on the asking thread", failed)
    }

    @Test fun keysAlreadyLoadedAreNotLoadedAgainByTheWarmUp() {
        val l = Loader(); val c = ArtCache(l)
        c.get("a")
        c.warm(listOf("a", "b")) { it.run() }
        assertEquals(2, l.calls.get())
    }

    @Test fun awaitWarmWaitsForTheThreadAndGivesUpAtTheLimit() {
        val gate = CountDownLatch(1)
        val c = ArtCache<String> { k -> if (k == "slow") gate.await(5, TimeUnit.SECONDS); "art:$k" }
        assertTrue("nothing to wait for before a warm-up", c.awaitWarm(10))
        lateinit var t: Thread
        c.warm(listOf("slow")) { t = Thread(it).also { th -> th.start() } }
        assertFalse("still running after 30 ms", c.awaitWarm(30))
        gate.countDown()
        assertTrue(c.awaitWarm(5000))
        assertFalse(c.warming)
        t.join(5000)
    }
}
