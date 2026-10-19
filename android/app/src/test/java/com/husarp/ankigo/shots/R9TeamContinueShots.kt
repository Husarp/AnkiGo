package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.husarp.ankigo.Anki
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.ui.BattleCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/*
 * R9-B9b: the green Continue button in the Pokémon tab's battle card header (next to Pop out). Shots: r9-teamcontinue-*
 * (with a deck / without, phone + split + a 320 dp wide window, light + dark).
 */
class R9TeamContinueShots {
    @get:Rule val paparazzi = Shots.rule()

    private val deck = Anki.Deck(7, "Spanish::Verbs", 3, 12, 5, false)

    private class FakeLive : BattleCard.Live {
        override var paused = false
        val holders = LinkedHashSet<Any>()
        override fun hold(ctx: Context, owner: Any) { holders += owner }
        override fun release(ctx: Context, owner: Any) { holders -= owner }
        override fun addSink(s: LiveSession.Sink) {}
        override fun removeSink(s: LiveSession.Sink) {}
        override fun addPauseListener(l: (Boolean) -> Unit) {}
        override fun removePauseListener(l: (Boolean) -> Unit) {}
    }

    private class CardHost : BattleCard.Host {
        val studied = ArrayList<Anki.Deck>()
        override fun expand() {}
        override fun popOut() {}
        val wanted = ArrayList<() -> Boolean>()
        override fun continueStudy(deck: Anki.Deck, stillWanted: () -> Boolean) { studied += deck; wanted += stillWanted }
    }

    private fun find(v: View, d: String): View? {
        if (v.contentDescription?.toString() == d) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), d)?.let { return it }
        return null
    }

    private fun scene(ctx: Context, withDeck: Boolean): View {
        val card = BattleCard(ctx, CardHost(), FakeLive()) { null }
        card.setContinue(if (withDeck) deck else null)
        return FrameLayout(ctx).apply {
            setBackgroundColor(com.husarp.ankigo.ui.Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            setPadding(com.husarp.ankigo.ui.Ui.dp(ctx, 12), com.husarp.ankigo.ui.Ui.dp(ctx, 12), com.husarp.ankigo.ui.Ui.dp(ctx, 12), 0)
            addView(card.view, FrameLayout.LayoutParams(-1, -2))
        }
    }

    private val vs = listOf(Variants.PHONE, Variants.SPLIT, Variant("narrow", 320, 600))

    @Test fun cardWithAContinueButton() = paparazzi.shootPage("r9-teamcontinue-deck", vs) { scene(it, true) }

    @Test fun cardWithoutOne() = paparazzi.shootPage("r9-teamcontinue-none", vs) { scene(it, false) }

    @Test fun theButtonShowsOnlyWithADeckAndStudiesIt() {
        paparazzi.shootPage("r9-teamcontinue-tap", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val host = CardHost()
            val card = BattleCard(ctx, host, FakeLive()) { null }
            assertFalse(card.continueShown)
            card.setContinue(deck)
            assertTrue(card.continueShown)
            val b = find(card.view, "Continue Verbs, 20 due")
            assertNotNull("button with its description", b)
            assertTrue(b!!.performClick())
            assertEquals(listOf(deck), host.studied)
            card.setContinue(null)
            assertFalse(card.continueShown)
            card.view
        }
    }

    /**
     * A lookup scene: a real parent holds the card, the loader blocks on [release], the "main thread" is a queue the test runs
     * itself once the worker has delivered ([delivered]), so nothing sleeps and the assertion runs after the result was posted.
     */
    private class Lookup(ctx: Context, val stopBeforeResult: Boolean, val deck: Anki.Deck) {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val delivered = CountDownLatch(1)
        val posted = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        var loads = 0
        val card = BattleCard(ctx, CardHost(), FakeLive(), { r -> posted.add(r); delivered.countDown() }) {
            loads++
            started.countDown(); release.await(5, TimeUnit.SECONDS); deck
        }
        val parent = FrameLayout(ctx).apply { addView(card.view, FrameLayout.LayoutParams(-1, -2)) }

        fun run() {
            card.start()
            assertTrue(card.isLive)
            assertTrue("the load runs off the main thread", started.await(5, TimeUnit.SECONDS))
            if (stopBeforeResult) card.stop()
            release.countDown()
            assertTrue("the worker delivered its result", delivered.await(5, TimeUnit.SECONDS))
            assertFalse("nothing shows before the main thread runs the callback", card.continueShown)
            while (true) posted.poll()?.run() ?: break
        }
    }

    private fun lookupCtx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        return c
    }

    @Test fun aLateResultAfterStopIsDropped() {
        val l = Lookup(lookupCtx(), stopBeforeResult = true, deck = deck)
        l.run()
        assertFalse("a late result is dropped", l.card.continueShown)
    }

    @Test fun aResultWhileLiveShowsTheButton() {
        val l = Lookup(lookupCtx(), stopBeforeResult = false, deck = deck)
        l.run()
        assertTrue("the same steps without stop show it", l.card.continueShown)
        assertNotNull(find(l.card.view, "Continue Verbs, 20 due"))
    }

    @Test fun aStartWhileALookupRunsReusesIt() {
        val l = Lookup(lookupCtx(), stopBeforeResult = false, deck = deck)
        l.card.start()
        assertTrue(l.started.await(5, TimeUnit.SECONDS))
        l.card.stop()
        l.card.start() // back on screen while the first lookup still waits: no second one
        l.release.countDown()
        assertTrue(l.delivered.await(5, TimeUnit.SECONDS))
        while (true) l.posted.poll()?.run() ?: break
        assertEquals("one lookup in flight per card", 1, l.loads)
        assertTrue("its result serves the new start", l.card.continueShown)
    }

    @Test fun aTapIsNoLongerWantedAfterStop() {
        val ctx = lookupCtx()
        val host = CardHost()
        val card = BattleCard(ctx, host, FakeLive(), poster = { }) { null }
        card.start()
        card.setContinue(deck)
        find(card.view, "Continue Verbs, 20 due")!!.performClick()
        assertTrue("wanted while the card is live", host.wanted.single()())
        card.stop()
        assertFalse("a late AnkiDroid start is dropped after stop", host.wanted.single()())
        card.start()
        assertFalse("and stays dropped when the card starts again", host.wanted.single()())
    }
}
