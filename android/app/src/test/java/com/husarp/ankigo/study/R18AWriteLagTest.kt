package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test
import java.util.TreeMap

/**
 * R18-A (278, "it lags, and every time it lags the orange dot shows"): AnkiDroid's ANSWER write is sometimes slow (0.6 to 3.8 s on Adam's
 * phone, kernel throttling of its storage). Nothing the user sees may wait for it. A fast user (100 ms to read a card side) on a virtual
 * clock, with every ANSWER taking [WRITE_MS]. The numbers are printed by each test (run with `-i`, or read the XML's system-out).
 *
 * The OLD rule (R10-X1) is rebuilt in the rig: `maxAhead = 0` (every tap on a shown-ahead card is held for the confirm, as the reducer still
 * does for 0) and `legacyFlip` ("Show answer" on a shown-ahead card is held for the confirm, which the screen used to do itself). The cause:
 * the confirming read sits on the one io thread BEHIND the answer's write, so a held flip or tap waits for the whole write. The next card
 * itself already showed at once.
 */
class R18AWriteLagTest {
    private val deck = 7L

    /** The study screen's user, the io thread and a pretend AnkiDroid on one virtual clock (ms). */
    private class LagRig(val fake: FakeAnki, deck: Long, maxAhead: Int, private val think: Long, private val legacyFlip: Boolean = false, tryNotTop: Boolean = false) {
        val s = StudySim(fake, deck, maxAhead = maxAhead, tryNotTop = tryNotTop)
        private val events = TreeMap<Long, ArrayList<() -> Unit>>()
        val flipTap = LinkedHashMap<String, Long>()
        val answerSide = LinkedHashMap<String, Long>()
        val shown = LinkedHashMap<String, Long>()
        val tapAt = LinkedHashMap<String, Long>()
        val taken = LinkedHashMap<String, Long>()
        private var seen = 0
        private var heldFlip: String? = null // legacyFlip: the card whose "Show answer" waits for the confirm

        fun at(t: Long, f: () -> Unit) { events.getOrPut(t) { ArrayList() } += f }

        /** Boots the screen (the first card shown, the cards after it prepared) and returns the time of the first tap, a second later. */
        fun start(): Long {
            s.boot(); s.pump(); seen = s.log.size
            return fake.clock.ms + 1000
        }

        private fun nameOf(k: StudyLoop.Key) = "c${k.nid}/${k.ord}"

        /** The user taps "Show answer" on the card on screen. */
        private fun flip() {
            val d = s.displayed ?: return
            val n = nameOf(d.key)
            if (flipTap.containsKey(n)) return
            flipTap[n] = fake.clock.ms
            if (legacyFlip && d.predicted) { heldFlip = n; return }
            open(n)
        }

        private fun open(n: String) {
            s.flip(); answerSide[n] = fake.clock.ms
            at(fake.clock.ms + think) { tap() }
        }

        /** The user taps "Good" on the card on screen (its answer side is up). */
        private fun tap() {
            val d = s.displayed ?: return
            val n = nameOf(d.key)
            if (tapAt.containsKey(n) || !answerSide.containsKey(n)) return
            tapAt[n] = fake.clock.ms
            s.tap(3)
        }

        fun firstTap(ease: Int) {
            val d = s.displayed ?: return
            tapAt[nameOf(d.key)] = fake.clock.ms
            s.tap(ease)
        }

        /** What the user does after each thing the screen showed: read the question, then (once the answer is up) read it and answer. */
        private fun react() {
            while (seen < s.log.size) {
                val (t, l) = s.log[seen++]
                val n = l.substringAfterLast(':').removeSuffix("*")
                when {
                    l.startsWith("show:") -> { shown.putIfAbsent(n, t); at(t + think) { flip() }; if (!l.startsWith("show:P:")) heldFlip = null }   // a swap drops a held flip
                    l.startsWith("confirm:") -> if (heldFlip == n) { heldFlip = null; open(n) }
                    l.startsWith("enqueue:") -> taken.putIfAbsent(n, t)
                }
            }
        }

        private fun fire(upTo: Long) {
            while (events.isNotEmpty() && events.firstKey() <= upTo) {
                val e = events.pollFirstEntry()
                if (fake.clock.ms < e.key) fake.clock.ms = e.key
                for (f in e.value) f()
                s.pumpMain(); react()
            }
        }

        /** Plays the io thread and the user until nothing is left to do. User actions that fall inside a provider call fire at their own time. */
        fun run() {
            fake.ticker = { from, to ->
                val keep = fake.clock.ms
                while (events.isNotEmpty() && events.firstKey() <= to) {
                    val e = events.pollFirstEntry()
                    fake.clock.ms = maxOf(e.key, from)
                    for (f in e.value) f()
                    s.pumpMain(); react()
                }
                fake.clock.ms = keep
            }
            var guard = 0
            while (guard++ < 20000) {
                fire(fake.clock.ms); s.pumpMain(); react()
                if (s.runOneIo()) continue
                if (s.p.main.isNotEmpty()) continue
                val next = events.firstEntry() ?: return
                fake.clock.ms = maxOf(fake.clock.ms, next.key)
            }
            error("did not settle")
        }
    }

    private fun rig(answerMs: Long, ahead: Int, notes: Int = 12, think: Long = 100, legacyFlip: Boolean = false, tryNotTop: Boolean = false, configure: (FakeAnki) -> Unit = {}): LagRig {
        val fake = StudySim.deck(deck, notes); fake.selected = 99
        fake.slowMs = answerMs
        fake.chaos = { op -> if (op == "answer") F.SLOW else F.NONE }
        configure(fake)
        return LagRig(fake, deck, ahead, think, legacyFlip, tryNotTop)
    }

    private fun card(n: Int) = "c${100 + n}/0"

    // ---- the cause, measured

    /** What the user felt on cards 2 and 3 (and the appearance of card 4): ms after the thing before it. */
    private inner class Felt(val r: LagRig, val t: Long) {
        fun shown(n: Int) = r.shown[card(n)]!! - r.taken[card(n - 1)]!!        // card n appears after the answer on card n-1 was taken
        fun flip(n: Int) = r.answerSide[card(n)]!! - r.flipTap[card(n)]!!     // "Show answer" tap -> answer side up
        fun took(n: Int) = r.taken[card(n)]!! - r.tapAt[card(n)]!!            // answer tap -> the answer is taken (card after it shows)
        fun pace(n: Int) = r.taken[card(n)]!! - r.taken[card(n - 1)]!!        // one whole card: 2 x 100 ms of the user's own time
        fun line() = (2..3).joinToString("; ") { n -> "card $n: shown +${shown(n)}, flip ${flip(n)}, answer taken ${took(n)}, whole card ${pace(n)}" } + "; card 4 shown +${shown(4)}"

        /** Every margin of the ticket that is broken: next card, flip and answer tap each within [LIMIT_MS], a whole card within the user's own time + [LIMIT_MS]. */
        fun broken(): List<String> = buildList {
            for (n in 2..3) {
                if (shown(n) > LIMIT_MS) add("card $n shown ${shown(n)} ms after the answer before it")
                if (flip(n) > LIMIT_MS) add("card $n: Show answer took ${flip(n)} ms")
                if (took(n) > LIMIT_MS) add("card $n: the answer tap took ${took(n)} ms to be taken")
                if (pace(n) > 2 * THINK_MS + LIMIT_MS) add("card $n took ${pace(n)} ms from the answer before it to its own")
            }
            if (shown(4) > LIMIT_MS) add("card 4 shown ${shown(4)} ms after the answer before it")
        }
    }

    private fun measure(ahead: Int, legacyFlip: Boolean): Felt {
        val r = rig(WRITE_MS, ahead = ahead, legacyFlip = legacyFlip); val t = r.start()
        r.at(t) { r.firstTap(3) }
        r.run()
        r.s.check()
        return Felt(r, t)
    }

    @Test fun theOldRuleMakesTheUserWaitForTheWholeWrite() {
        // the R10-X1 rule: the card shows at once, but "Show answer" (and any tap) on it waits for the confirming read behind the write
        val f = measure(ahead = 0, legacyFlip = true)
        println("OLD (ahead 0, flip held), $WRITE_MS ms writes: ${f.line()}")
        for (n in 2..3) {
            assertTrue("card $n is shown at once (this part already worked): ${f.shown(n)} ms", f.shown(n) <= 50)
            assertTrue("Show answer waited for the write: ${f.flip(n)} ms", f.flip(n) >= WRITE_MS - 400)
            assertTrue("a whole card took the write: ${f.pace(n)} ms", f.pace(n) >= WRITE_MS - 400)
        }
        assertTrue("the test sees it: margins broken ${f.broken()}", f.broken().size >= 4)
    }

    @Test fun withNoAnswersAheadEveryTapOnTheShownCardWaitsForTheWholeWrite() {
        // flip not held but the reducer held at 0: the answer tap itself waits (maxAhead 0 is the kill switch of the new rule)
        val r = rig(WRITE_MS, ahead = 0); val t = r.start()
        r.at(t) { r.firstTap(3) }
        r.run()
        val c2 = card(2); val c3 = card(3)
        val waited = r.taken[c2]!! - r.tapAt[c2]!!
        println("ahead 0: card 2 shown +${r.shown[c2]!! - t}, answer tapped +${r.tapAt[c2]!! - t}, taken +${r.taken[c2]!! - t} (held ${waited} ms); card 3 shown +${r.shown[c3]!! - t}")
        assertTrue("the next card is shown at once", r.shown[c2]!! - t <= 50)
        assertTrue("the answer tap waited for the write: $waited ms", waited >= WRITE_MS - 400)
        assertTrue("so did the card after it", r.shown[c3]!! - t >= WRITE_MS - 400)
        r.s.check()
    }

    // ---- the fix, measured

    @Test fun theNewRuleKeepsEveryMarginOfAFastUserThroughATwoSecondWrite() {
        val f = measure(ahead = NextCard.AHEAD, legacyFlip = false)
        println("NEW (ahead ${NextCard.AHEAD}), $WRITE_MS ms writes: ${f.line()}")
        assertEquals("no margin broken: ${f.line()}", emptyList<String>(), f.broken())
        for (n in 2..3) assertTrue("card $n answer side up while the first write still runs: +${f.r.answerSide[card(n)]!! - f.t}", f.r.answerSide[card(n)]!! - f.t < WRITE_MS / 2)
        assertTrue("three answers taken in well under one write: ${f.r.taken[card(3)]!! - f.t} ms", f.r.taken[card(3)]!! - f.t <= 600)
    }

    @Test fun firstThreeAnswersAreNeverHeldByATwoSecondWrite() {
        val r = rig(WRITE_MS, ahead = NextCard.AHEAD); val t = r.start()
        r.at(t) { r.firstTap(3) }
        r.run()
        val lines = (2..4).joinToString("; ") { n ->
            val c = card(n)
            "card $n: shown +${r.shown[c]!! - t}, answer side +${r.answerSide[c]!! - t}, answer tapped +${r.tapAt[c]!! - t}, taken +${r.taken[c]!! - t}"
        }
        println("ahead ${NextCard.AHEAD}: $lines")
        for (n in 2..4) {
            val c = card(n)
            val label = "card $n"
            assertTrue("$label is shown right after the answer before it: ${r.shown[c]!! - (r.taken[card(n - 1)]!!)} ms", r.shown[c]!! - r.taken[card(n - 1)]!! <= 50)
            assertTrue("$label can be flipped at once: ${r.answerSide[c]!! - r.flipTap[c]!!} ms", r.answerSide[c]!! - r.flipTap[c]!! <= 50)
            if (n <= 3) {
                assertTrue("$label answer tap taken at once: ${r.taken[c]!! - r.tapAt[c]!!} ms", r.taken[c]!! - r.tapAt[c]!! <= 300)
                assertTrue("$label answer side up while the first write still runs", r.answerSide[c]!! - t < WRITE_MS / 2)
            }
        }
        assertTrue("three answers taken in well under one write: ${r.taken[card(3)]!! - t} ms", r.taken[card(3)]!! - t <= 600)
        r.s.check()
        assertEquals("every card was written once, in the order it was answered", r.fake.cards.values.map { it.id }.take(r.fake.revlog.size), r.fake.revlog.map { it.cardId })
    }

    @Test fun theThirdTapAheadIsHeldUntilTheConfirmBecauseOnlyTwoGuessesAreAllowed() {
        val r = rig(WRITE_MS, ahead = NextCard.AHEAD); val t = r.start()
        r.at(t) { r.firstTap(3) }
        r.run()
        val c4 = card(4)
        val held = r.taken[c4]!! - r.tapAt[c4]!!
        println("cap: card 4 answer tapped +${r.tapAt[c4]!! - t}, taken +${r.taken[c4]!! - t} (held $held ms)")
        assertTrue("held for the writes before it: $held ms", held >= 2 * WRITE_MS)
        val firstHeld = r.s.log.indexOfFirst { it.second.startsWith("held:") }
        assertTrue("a held tap was shown", firstHeld >= 0)
        assertEquals("exactly the first three answers were made before the hold", 3, r.s.log.take(firstHeld).count { it.second.startsWith("enqueue:") })
        r.s.check()
    }

    @Test fun aLongFastRunInASlowPhaseKeepsEveryAnswerInOrderAndExactlyOnce() {
        val r = rig(WRITE_MS, ahead = NextCard.AHEAD, notes = 20); val t = r.start()
        r.at(t) { r.firstTap(3) }
        r.run()
        assertTrue(r.s.finished)
        assertEquals((1..20).map { it.toLong() }, r.fake.revlog.map { it.cardId })
        assertEquals(20, r.s.made.size)
        assertTrue("the user was ahead of AnkiDroid at least once: ${r.s.p.nc.tally()}", r.s.p.nc.answeredAhead >= 2)
        println("20 answers, ${WRITE_MS} ms writes: ${r.s.p.nc.tally()}; done at +${r.fake.clock.ms - t} ms")
        r.s.check()
    }

    // ---- the device load (R18 FIX-1, RV-9)

    @Test fun theLearningCardsAreReadOncePerLoadNotOncePerPreparedCard() {
        // every confirmed card gets AHEAD + 1 prepared cards after it; each used to do its own AnkiDroid learning search (3 per answered card, 2 thrown away at the next confirm)
        val r = rig(30, ahead = NextCard.AHEAD, notes = 12); val t = r.start()
        r.at(t) { r.firstTap(3) }
        r.run()
        assertTrue(r.s.finished)
        val answered = r.s.made.size
        println("learning reads: ${r.s.learnReads} for $answered answered cards (${r.s.prepares} cards prepared)")
        assertEquals(12, answered)
        assertTrue("the old rule read once per prepared card, at least 2 per answered card: ${r.s.prepares} prepared", r.s.prepares >= 2 * answered)
        assertTrue("one learning read per answered card at most: ${r.s.learnReads} for $answered", r.s.learnReads <= answered)
        assertTrue("the prepared cards shared them: ${r.s.learnReads} reads for ${r.s.prepares} prepared cards", r.s.learnReads * 2 <= r.s.prepares)
        r.s.check()
    }

    @Test fun inASlowPhaseTheLearningCardsAreStillReadAtMostOncePerAnsweredCard() {
        val r = rig(WRITE_MS, ahead = NextCard.AHEAD, notes = 20); val t = r.start()
        r.at(t) { r.firstTap(3) }
        r.run()
        assertTrue(r.s.finished)
        println("slow phase, learning reads: ${r.s.learnReads} for ${r.s.made.size} answered cards (${r.s.prepares} cards prepared)")
        assertTrue("${r.s.learnReads} reads for ${r.s.made.size} answers", r.s.learnReads <= r.s.made.size)
        r.s.check()
    }

    @Test fun theMarginsOfAFastUserStayTheSameWithTheSharedLearningRead() {
        val f = measure(ahead = NextCard.AHEAD, legacyFlip = false)
        assertEquals("no margin broken: ${f.line()}", emptyList<String>(), f.broken())
        assertTrue("one learning read per load: ${f.r.s.learnReads} for ${f.r.s.prepares} prepared cards", f.r.s.learnReads in 1..f.r.s.made.size)
    }

    // ---- a guess that turns out wrong

    @Test fun aWrongGuessIsNeverWrittenToTheWrongCardAndTheRealCardComesOnItsQuestion() {
        // card 1 is written; a learning card (card 7) comes due right after, so cards 2 and 3 (answered ahead) are not first any more
        var moved = false
        val r = rig(WRITE_MS, ahead = NextCard.AHEAD, notes = 8) { f ->
            f.afterApply = { _, _ -> if (!moved) { moved = true; f.moveToTop(f.cards.values.first { it.nid == 107L }) } }
        }
        val t = r.start()
        r.at(t) { r.firstTap(3) }
        r.run()
        val a2 = r.s.made[1]; val a3 = r.s.made[2]
        assertEquals(AnswerOutcome.Reason.NOT_TOP, (r.s.terminal(a2) as AnswerOutcome.NotWritten).reason)
        assertEquals(AnswerOutcome.Reason.NOT_TOP, (r.s.terminal(a3) as AnswerOutcome.NotWritten).reason)
        assertEquals("nothing was written to a card that was not first", 0, r.fake.notTopWrites)
        assertEquals(1, r.s.p.nc.misses)
        val swapTo = r.s.log.first { it.second.startsWith("show:C:") && it.first > t }
        assertEquals("the real first card is shown, on its question: ${swapTo.second}", "show:C:${card(7)}", swapTo.second)
        r.s.check()
    }

    @Test fun withThePlan195TryOnAGuessThatTurnsOutWrongEachAnswerGoesToTheCardTheUserAnsweredAndNeverToTheFirstOne() {
        // the app's default (Diagnostics: write a card that is not first): cards 2 and 3 were answered ahead, then a learning card (7) came due
        var moved = false
        val r = rig(WRITE_MS, ahead = NextCard.AHEAD, notes = 8, tryNotTop = true) { f ->
            f.answerAnyKey = true   // what the device test of PLAN 195 found: AnkiDroid applies the card it was asked for
            f.afterApply = { _, _ -> if (!moved) { moved = true; f.moveToTop(f.cards.values.first { it.nid == 107L }) } }
        }
        val t = r.start()
        r.at(t) { r.firstTap(3) }
        r.run()
        assertEquals("cards 1, 2 and 3 were written, each once and in order; card 7 (first) was not touched", listOf(1L, 2L, 3L), r.fake.revlog.take(3).map { it.cardId })
        assertTrue("card 7 was not written before it was shown", r.fake.revlog.indexOfFirst { it.cardId == 7L } >= 3)
        for (i in 0..2) assertTrue("answer ${i + 1} saved: ${r.s.terminal(r.s.made[i])}", r.s.terminal(r.s.made[i]) is AnswerOutcome.Saved)
        val swapTo = r.s.log.first { it.second.startsWith("show:C:") && it.first > t }
        assertEquals("the real first card comes on its question", "show:C:${card(7)}", swapTo.second)
        r.s.check(allowNotTopWrites = true)
    }

    private companion object {
        const val WRITE_MS = 2000L
        const val THINK_MS = 100L
        const val LIMIT_MS = 300L
    }
}
