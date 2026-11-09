package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.PokemonRarity
import com.husarp.ankigo.study.NextCard
import com.husarp.ankigo.ui.EventPlayer
import com.husarp.ankigo.ui.RarityFrame
import com.husarp.ankigo.ui.StreakFlame
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class R32StudyLagUiTest {
    @get:Rule val paparazzi = Shots.rule()

    private fun ctx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        return Shots.files(paparazzi.context).also {
            FakeGame.install(it)
            it.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        }
    }

    @Test fun slowFailedFlameDecodeLeavesTheStillAndUiUsableAndOnlyRunsOnce() {
        val c = ctx()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        val delivered = ConcurrentLinkedQueue<Runnable>()
        val main = Thread.currentThread()
        var decodedOn: Thread? = null
        var calls = 0
        val flame = StreakFlame(c, {
            decodedOn = Thread.currentThread()
            calls++; entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            error("test decode failure")
        }, worker) { delivered.add(it) }
        try {
            assertFalse("no decode while constructing an unseen chip", getField(flame, "triedDecode") as Boolean)
            val still = flame.drawable
            flame.decode()
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            assertNotSame(main, decodedOn)
            flame.decode()
            flame.alive = false
            assertSame(still, flame.drawable)
            assertEquals(115, flame.imageAlpha)
            assertFalse(getField(flame, "decodeFailed") as Boolean)
            release.countDown()
            worker.submit {}.get(3, TimeUnit.SECONDS)
            assertFalse("the worker must not update the image", getField(flame, "decodeFailed") as Boolean)
            assertEquals(1, delivered.size)
            delivered.remove().run()
            paparazzi.snapshot(FrameLayout(c).apply { addView(flame, FrameLayout.LayoutParams(24, 24)) }, "r32i-flame-result", 100)
            assertTrue(getField(flame, "decodeFailed") as Boolean)
            flame.decode()
            assertEquals(1, calls)
            assertSame(still, flame.drawable)
        } finally { release.countDown(); worker.shutdownNow(); worker.awaitTermination(3, TimeUnit.SECONDS) }
    }

    @Test fun appReducedMotionUnregistersSparklesAndCanResumeThem() {
        val c = ctx()
        val card = object : View(c) {
            var redraws = 0
            override fun invalidate() { redraws++; super.invalidate() }
        }.apply { RarityFrame.bind(this, PokemonRarity.MYTHICAL) }
        val frame = card.foreground as RarityFrame.Frame
        var checked = false
        val root = object : FrameLayout(c) {
            override fun dispatchDraw(canvas: Canvas) {
                if (!checked) {
                    frame.refresh(true)
                    assertTrue(frame in RarityFrame.Clock.active)
                    val before = RarityFrame.Clock.timeMs
                    try {
                        setField(RarityFrame.Clock, "timeMs", 7000L)
                        frame.tick()
                        card.redraws = 0
                        frame.tick()
                        assertEquals("quiet sparkles cause no redraw", 0, card.redraws)
                        setField(RarityFrame.Clock, "timeMs", 784L)
                        frame.tick()
                        assertEquals(1, card.redraws)
                        setField(RarityFrame.Clock, "timeMs", 7000L)
                        frame.tick(); frame.tick()
                        assertEquals("one redraw clears the last lit frame", 2, card.redraws)
                        setField(RarityFrame.Clock, "timeMs", 1600L)
                        frame.draw(canvas)
                        setField(RarityFrame.Clock, "timeMs", 7000L)
                        frame.tick()
                        assertEquals("a first draw near the burst end is also cleared", 3, card.redraws)
                    } finally { setField(RarityFrame.Clock, "timeMs", before) }
                    c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
                    frame.refresh(true)
                    assertFalse(frame in RarityFrame.Clock.active)
                    assertFalse(RarityFrame.Clock.posted)
                    c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
                    frame.refresh(true)
                    assertTrue(frame in RarityFrame.Clock.active)
                    checked = true
                }
                super.dispatchDraw(canvas)
            }
        }.apply { addView(card, FrameLayout.LayoutParams(180, 240)) }
        paparazzi.snapshot(root, "r32i-sparkles-motion", 0)
        assertTrue(checked)
        assertTrue(RarityFrame.Clock.active.isEmpty())
    }

    @Test fun busyBattleAllowsFlipAndAnAnswerTapWithoutChangingItsButtons() {
        val c = ctx()
        val act = attach(StudyActivity(), ShotCtx(c))
        setField(act, "deckId", 2L)
        act.buildContent()
        setField(act, "card", com.husarp.ankigo.Anki.Review(10, 0, 4, listOf("1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Question</div>")
        setField(act, "answerHtml", "<div>Answer</div>")
        setField(act, "locked", false)
        val events = getField(act, "events") as EventPlayer
        events.enqueue(listOf(GameEvent.Charge(10, 1, 2), GameEvent.Info("You know Mainy's matchups now", "t1")))
        assertFalse("a timeline is actually playing", events.idle)
        assertFalse(events.holding)
        // Layoutlib omits evaluateJavascript (including the keyboard blur). Reaching it proves that
        // the flip gate admitted the request; model its answer callback for the remaining input checks.
        try { call(act, "flip") } catch (e: NoSuchMethodError) {
            assertTrue(e.message.orEmpty().contains("WebView.evaluateJavascript"))
        }
        assertEquals(true, getField(act, "answerRevealed"))
        setField(act, "flipped", true); setField(act, "locked", false)
        call(act, "showButtons")
        assertEquals(true, getField(act, "flipped"))
        val row = getField(act, "buttons") as ViewGroup
        fun clickable(v: View): List<View> = if (v.isClickable) listOf(v) else if (v is ViewGroup)
            (0 until v.childCount).flatMap { clickable(v.getChildAt(it)) } else emptyList()
        val buttons = clickable(row)
        assertEquals(4, buttons.size)
        assertTrue(buttons.all { it.isEnabled })
        setField(act, "allowAt", Long.MIN_VALUE) // allow any layoutlib clock origin; the double-tap delay is outside this check

        // A real predicted reducer with no safe card after it holds this tap. This avoids provider IO while
        // proving the Activity accepts the tap through the same gate as buttons, keys, swipes and card JS.
        @Suppress("UNCHECKED_CAST")
        val nc = call(act, "getNc") as NextCard<com.husarp.ankigo.StudyCard>
        val key = com.husarp.ankigo.study.StudyLoop.Key(10, 0)
        val prepared = com.husarp.ankigo.study.Prepared(key,
            mapOf("_id" to "10", "reps" to "2", "queue" to "2", "deck_id" to "2", "due" to "5"),
            com.husarp.ankigo.StudyCard(
                getField(act, "card") as com.husarp.ankigo.Anki.Review, "Question", "Answer", "Card", "Basic", "", emptyMap(), "",
                emptyList(), emptyList(), false, com.husarp.ankigo.study.TtsRules.NONE, org.json.JSONObject(), "Deck", true, 0))
        nc.reload(); nc.loaded(1, prepared, listOf(key))
        // Mark the shown card predicted, as if its prior answer still writes.
        setField(nc, "slot", NextCard.Slot.Predicted(prepared, 1, 1))
        setField(act, "stats", prepared.before)
        call(act, "answer", 3)
        assertEquals("clock=${android.os.SystemClock.elapsedRealtime()}, allow=${getField(act, "allowAt")}, " +
            "flipped=${getField(act, "flipped")}, locked=${getField(act, "locked")}, " +
            "blocked=${getField(act, "mediaBlocked")}, slot=${nc.slot}", 3, getField(act, "heldEase"))
        assertEquals(3, (nc.slot as NextCard.Slot.Predicted).held!!.ease)
        assertFalse("the answer did not wait for playback", events.idle)
        val next = prepared.copy(key = com.husarp.ankigo.study.StudyLoop.Key(11, 0),
            before = prepared.before + ("_id" to "11"),
            view = prepared.view.copy(review = prepared.view.review.copy(nid = 11)))
        call(act, "perform", listOf(com.husarp.ankigo.study.Cmd.Show(next, predicted = true)))
        assertEquals(11L, (getField(act, "card") as com.husarp.ankigo.Anki.Review).nid)
        assertEquals(false, getField(act, "locked"))
        assertEquals("Show answer", clickable(row).single().contentDescription.toString())
        assertFalse("the next question is usable before playback ends", events.idle)
        events.clear()
    }
}
