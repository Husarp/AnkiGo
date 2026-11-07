package com.husarp.ankigo.study

import com.husarp.ankigo.study.PredictRule.Why
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * R19-R (278b): when a learning card came due while the card was on screen, PredictRule says LEARNING_DUE and the answer used to wait
 * for AnkiDroid's real next card, behind the write. The learning read already tells WHICH card jumps to the front, so that card (prepared
 * by id) is predicted instead, when it is certain; else the answer waits as before.
 */
class R19RPredictLearningTest {
    private companion object {
        const val NOW = 1_700_000_000_000L // epoch ms, a whole second
        const val NOW_S = NOW / 1000

        fun key(n: Int) = StudyLoop.Key(100L + n, 0)
        fun card(n: Int, queue: Int = 2, due: Long = 5, learning: PredictRule.LearnRead? = null) = Prepared(
            key(n), mapOf("_id" to "$n", "note_id" to "${100 + n}", "deck_id" to "1", "reps" to "3", "due" to "$due", "queue" to "$queue", "last_review_time_secs" to "9"), "c$n", learning,
        )

        /** A learning card (queue 1) prepared by id, due at [due] (epoch seconds). */
        fun learn(n: Int, due: Long) = card(n, queue = 1, due = due)

        /** The learning cards ([id] to [due] in epoch seconds) read [ago] ms before the tap; their note ids follow [key]. */
        fun read(vararg cards: Pair<Int, Long>, ago: Long = 30_000) =
            PredictRule.LearnRead(NOW - ago, cards.map { PredictRule.Due(it.first.toLong(), it.second, key(it.first)) })
    }

    private val due = NOW_S - 100 // came due 100 s ago, while card 1 was on screen

    private class Rig(val ahead: Int = NextCard.AHEAD, var clock: Long = NOW) {
        var seq = 10L
        val nc = NextCard<String>(1, { ++seq }, { clock }, maxAhead = ahead, check = PredictRule::check)

        /** Card 1 confirmed (queue 1, 2, 3, ...), card 2 prepared behind it with [read], and the learning card [l] prepared by id. */
        fun ready(read: PredictRule.LearnRead?, l: Prepared<String>? = null, vararg more: Int): Rig {
            nc.reload()
            nc.loaded(1, card(1), listOf(key(1), key(2)) + more.map { key(it) })
            nc.prepared(key(1), 1, card(2, learning = read))
            more.firstOrNull()?.let { nc.prepared(key(2), 1, card(it, learning = read)) }
            if (l != null) nc.preparedLearning(key(1), 1, l)
            return this
        }
    }

    // ---- the log's case: a main-queue prediction, one learning card due

    @Test fun theDueLearningCardIsShownAtOnceInsteadOfTheWait() {
        val r = Rig().ready(read(9 to due), learn(9, due))
        val cmds = r.nc.tapAnswer(3, 700)
        assertEquals(listOf("Enqueue", "Show", "Load"), cmds.map { it.javaClass.simpleName })
        assertEquals("shown as a predicted card, no wait", Cmd.Show(learn(9, due), true), cmds[1])
        assertEquals("the confirm asks for it by key", Cmd.Load(2, key(9)), cmds[2])
        assertEquals(NextCard.Slot.Predicted(learn(9, due), 2, 11L), r.nc.slot)
        assertEquals("", r.nc.lastWait)
        assertEquals(1, r.nc.predicted); assertEquals(1, r.nc.predictedLearning)
        assertTrue(r.nc.withheld.isEmpty()); assertEquals(0, r.nc.noPrediction)
    }

    @Test fun withoutThePreparedLearningCardTheAnswerWaitsAsBefore() {
        val r = Rig().ready(read(9 to due), l = null)
        val cmds = r.nc.tapAnswer(3, 700)
        assertEquals(Cmd.ShowWaiting, cmds[1]); assertEquals(Cmd.Load(2, null), cmds[2])
        assertEquals("learning card due", r.nc.lastWait)
        assertEquals(0, r.nc.predictedLearning); assertEquals(1, r.nc.withheld[Why.LEARNING_DUE])
    }

    @Test fun theConfirmBehindTheWriteMakesItTheRealCard() {
        val r = Rig().ready(read(9 to due), learn(9, due))
        r.nc.tapAnswer(3, 700)
        val done = r.nc.confirmed(2, key(9), listOf(key(9), key(2), key(3)), refresh = { "$it*" })
        assertEquals(Cmd.Confirm(learn(9, due).copy(view = "c9*")), done[0])
        assertEquals("the cards after it are prepared from the fresh read", listOf<Cmd<String>>(Cmd.Prepare(key(2), key(9), 2), Cmd.Prepare(key(3), key(2), 2)), done.drop(1))
        assertEquals(1, r.nc.hits); assertEquals(0, r.nc.misses)
        assertNotNull(r.nc.actionCard())
    }

    @Test fun aWrongGuessKeepsTheLearningCardUntilAnswered() {
        val r = Rig().ready(read(9 to due), learn(9, due))
        r.nc.tapAnswer(3, 700)
        // AnkiDroid's real first card is another learning card (it was changed in AnkiDroid meanwhile)
        val cmds = r.nc.swapped(2, card(7, queue = 1, due = due - 5), listOf(key(7), key(9), key(2)))
        assertEquals(Cmd.Confirm(learn(9, due), onTop = false), cmds[0])
        assertTrue(cmds.none { it is Cmd.Show })
        assertEquals(1, r.nc.misses); assertEquals(0, r.nc.hits)
        assertEquals("a learning card came due", r.nc.lastSwap)
        assertNull(r.nc.actionCard())
        // and the main-queue card it was expected to jump over comes first after all
        val r2 = Rig().ready(read(9 to due), learn(9, due)); r2.nc.tapAnswer(3, 700)
        r2.nc.swapped(2, card(2), listOf(key(2), key(3)))
        assertEquals("the predicted card left the queue", r2.nc.lastSwap)
    }

    @Test fun theVisitSummaryCountsPredictedLearning() {
        val r = Rig().ready(read(9 to due), learn(9, due))
        r.nc.tapAnswer(3, 1)
        assertEquals("predicted 1 (hit 0, swap 0), waited 0, predicted learning 1", r.nc.tally())
        r.nc.confirmed(2, key(9), listOf(key(9), key(2)))
        assertEquals("predicted 1 (hit 1, swap 0), waited 0, predicted learning 1", r.nc.tally())
        assertFalse("nothing to report without one", Rig().ready(read()).nc.tally().contains("learning"))
        val waited = Rig().ready(read(9 to due), null); waited.nc.tapAnswer(3, 1)
        assertEquals("a wait stays a wait", "predicted 0 (hit 0, swap 0), waited 1 (learning card due 1)", waited.nc.tally())
    }

    // ---- which card comes first

    @Test fun ofTwoDueLearningCardsTheEarliestIsPredicted() {
        // 8 came due before 9; both are due. The earliest was prepared.
        val r = Rig().ready(read(9 to due, 8 to due - 40), learn(8, due - 40))
        assertEquals(Cmd.Show(learn(8, due - 40), true), r.nc.tapAnswer(3, 1)[1])
        // the later one prepared instead is not what comes first: the answer waits
        val wrong = Rig().ready(read(9 to due, 8 to due - 40), learn(9, due))
        assertEquals(Cmd.ShowWaiting, wrong.nc.tapAnswer(3, 1)[1])
        assertEquals(0, wrong.nc.predictedLearning)
    }

    @Test fun twoDueLearningCardsAtTheSameTimeAreNotCertainAndTheAnswerWaits() {
        for (l in listOf(8, 9)) {
            val r = Rig().ready(read(9 to due, 8 to due), learn(l, due))
            assertEquals("tie: $l", Cmd.ShowWaiting, r.nc.tapAnswer(3, 1)[1])
            assertEquals("learning card due", r.nc.lastWait)
        }
        // a tie later in the order does not matter
        val r = Rig().ready(read(9 to due - 50, 8 to due, 7 to due), learn(9, due - 50))
        assertEquals(Cmd.Show(learn(9, due - 50), true), r.nc.tapAnswer(3, 1)[1])
    }

    @Test fun aCardDueOnlyWithinTheSlackOfTheWriteIsPredictedToo() {
        // R19-FIX RV-1 (Adam's 278b addition): a card due seconds after the tap counts as next; the confirm swaps a wrong guess
        val soon = NOW_S + 2 // due 2 s after the tap: AnkiDroid applies the answer a moment later and it is most likely in
        val r = Rig().ready(read(9 to soon), learn(9, soon))
        assertEquals(Why.LEARNING_DUE, PredictRule.check(card(1), card(2, learning = read(9 to soon)), NOW))
        assertTrue(PredictRule.firstLearning(card(1), card(2, learning = read(9 to soon)), learn(9, soon), NOW))
        assertEquals(Cmd.Show(learn(9, soon), true), r.nc.tapAnswer(3, 1)[1])
        assertEquals(1, r.nc.predictedLearning)
        // due by the tap itself: as before
        val exact = Rig().ready(read(9 to NOW_S), learn(9, NOW_S))
        assertEquals(Cmd.Show(learn(9, NOW_S), true), exact.nc.tapAnswer(3, 1)[1])
        // the edge of the slack is in, one second past it is not even LEARNING_DUE (the answer shows the next card as usual)
        val edge = NOW_S + PredictRule.SLACK_MS / 1000
        assertTrue(PredictRule.firstLearning(card(1), card(2, learning = read(9 to edge)), learn(9, edge), NOW))
        val late = NOW_S + PredictRule.SLACK_MS / 1000 + 1
        assertEquals(Why.SAFE, PredictRule.check(card(1), card(2, learning = read(9 to late)), NOW))
        assertFalse(PredictRule.firstLearning(card(1), card(2, learning = read(9 to late)), learn(9, late), NOW))
        // an earlier card that is due makes the later slack card irrelevant
        val both = Rig().ready(read(9 to soon, 8 to due), learn(8, due))
        assertEquals(Cmd.Show(learn(8, due), true), both.nc.tapAnswer(3, 1)[1])
        // two slack cards at the same time are a tie: the answer still waits
        val tie = Rig().ready(read(9 to soon, 8 to soon), learn(8, soon))
        assertEquals(Cmd.ShowWaiting, tie.nc.tapAnswer(3, 1)[1])
        // the prepared card has to be the earliest one, slack or not
        val wrong = Rig().ready(read(9 to soon, 8 to soon - 1), learn(9, soon))
        assertEquals(Cmd.ShowWaiting, wrong.nc.tapAnswer(3, 1)[1])
    }

    @Test fun theAnsweredCardAndAnIntradayNextCardAreNotTheCardThatJumps() {
        // the answered card itself (Again on a learning card) is never the guess; the card read second keeps its place before later ones
        val answered = card(1, queue = 1, due = NOW_S - 300)
        val r = Rig(); r.nc.reload(); r.nc.loaded(1, answered, listOf(key(1), key(2)))
        r.nc.prepared(key(1), 1, card(2, learning = read(1 to NOW_S - 300, 9 to due)))
        r.nc.preparedLearning(key(1), 1, learn(1, NOW_S - 300))   // kept for no card: it is the confirmed card itself
        assertEquals("the answered card is no guess: the answer waits", Cmd.ShowWaiting, r.nc.tapAnswer(3, 1)[1])
        // an intraday learning card read second: only a card due BEFORE it sorts first
        val second = card(2, queue = 1, due = due + 30, learning = read(9 to due, 8 to due + 60))
        assertEquals(Why.LEARNING_DUE, PredictRule.check(card(1), second, NOW))
        assertTrue(PredictRule.firstLearning(card(1), second, learn(9, due), NOW))
        assertFalse("8 is due after the card read second: it does not jump", PredictRule.firstLearning(card(1), second, learn(8, due + 60), NOW))
    }

    @Test fun thePreparedCardMustMatchTheReadAndStillBeALearningCard() {
        val next = card(2, learning = read(9 to due))
        assertTrue(PredictRule.firstLearning(card(1), next, learn(9, due), NOW))
        assertFalse("another card", PredictRule.firstLearning(card(1), next, learn(8, due), NOW))
        assertFalse("its due time changed", PredictRule.firstLearning(card(1), next, learn(9, due + 600), NOW))
        assertFalse("no longer a learning card", PredictRule.firstLearning(card(1), next, card(9, queue = 2, due = due), NOW))
        assertTrue("a filtered deck's preview repeat sorts the same", PredictRule.firstLearning(card(1), next, card(9, queue = 4, due = due), NOW))
    }

    @Test fun nothingIsPredictedWhenTheRuleDoesNotSayLearningDue() {
        val ok = Rig().ready(read(9 to NOW_S + 600), learn(9, NOW_S + 600)) // not due: SAFE, card 2 shows as usual
        assertEquals(Cmd.Show(card(2, learning = read(9 to NOW_S + 600)), true), ok.nc.tapAnswer(3, 1)[1])
        assertEquals(0, ok.nc.predictedLearning)
        val old = Rig().ready(read(9 to due, ago = PredictRule.MAX_AGE_MS + 1), learn(9, due))
        assertEquals("an old read cannot be trusted", Cmd.ShowWaiting, old.nc.tapAnswer(3, 1)[1])
        assertEquals("old read", old.nc.lastWait)
        val unread = Rig().ready(PredictRule.LearnRead(NOW - 1000, null), learn(9, due))
        assertEquals(Cmd.ShowWaiting, unread.nc.tapAnswer(3, 1)[1])
        assertFalse(PredictRule.firstLearning(card(1), card(2, learning = null), learn(9, due), NOW))
    }

    // ---- R18's ahead rules

    @Test fun aTapOnThePredictedLearningCardIsHeldUntilItIsConfirmed() {
        // card 1 confirmed, cards 2 and 3 prepared behind it; card 9 is due, so after the answer on card 1 the learning card 9 is shown
        val r = Rig().ready(read(9 to due), learn(9, due), 3)
        val first = r.nc.tapAnswer(3, 1)
        assertEquals(Cmd.Show(learn(9, due), true), first[1])
        // the second tap is on the predicted learning card itself: held (a learning card is no card to answer ahead on, R18 aheadOk)
        assertEquals(listOf<Cmd<String>>(Cmd.ShowHeld(3)), r.nc.tapAnswer(3, 1))
        assertEquals(0, r.nc.answeredAhead)
        val done = r.nc.confirmed(2, key(9), listOf(key(9), key(2), key(3)), refresh = { "$it*" })
        assertEquals(Cmd.Confirm(learn(9, due).copy(view = "c9*")), done[0])
        val enq = done[1] as Cmd.Enqueue
        assertEquals(9L, enq.answer.cardId); assertEquals(3, enq.answer.ease)
    }

    @Test fun anAnswerMadeAheadWaitsWhenALearningCardWouldJumpTheNextOne() {
        // card 1 answered while nothing is due (card 2 shows ahead); a learning card 9 comes due before the tap on card 2
        var clock = NOW
        val r = Rig(clock = clock)
        val read = read(9 to NOW_S + 20) // not due at the first tap, due at the second
        r.ready(read, learn(9, NOW_S + 20), 3)
        val a = r.nc.tapAnswer(3, 1)
        assertEquals(Cmd.Show(card(2, learning = read), true), a[1])
        r.clock = NOW + 30_000
        // on card 2 (predicted, ahead): card 3 is behind the due learning card; the learning card may not be shown ahead -> held
        assertEquals(listOf<Cmd<String>>(Cmd.ShowHeld(3)), r.nc.tapAnswer(3, 1))
        assertEquals(0, r.nc.predictedLearning); assertEquals(0, r.nc.answeredAhead)
        assertFalse("aheadOk is what keeps a learning card out", PredictRule.aheadOk(learn(9, NOW_S)))
    }

    @Test fun thePreparedLearningCardIsOnlyKeptForTheConfirmedCardOfItsLoad() {
        val r = Rig(); r.nc.reload(); r.nc.loaded(1, card(1), listOf(key(1), key(2)))
        r.nc.prepared(key(1), 1, card(2, learning = read(9 to due)))
        r.nc.preparedLearning(key(1), 9, learn(9, due))   // another load
        r.nc.preparedLearning(key(2), 1, learn(9, due))   // another card
        r.nc.preparedLearning(key(1), 1, card(1))         // the confirmed card itself
        assertEquals("none of those was kept", Cmd.ShowWaiting, r.nc.tapAnswer(3, 1)[1])
        // not before a confirmed card either
        val loading = Rig(); loading.nc.reload()
        loading.nc.preparedLearning(key(1), 1, learn(9, due))
        loading.nc.loaded(1, card(1), listOf(key(1), key(2))); loading.nc.prepared(key(1), 1, card(2, learning = read(9 to due)))
        assertEquals(Cmd.ShowWaiting, loading.nc.tapAnswer(3, 1)[1])
    }

    @Test fun aSwapOrReloadForgetsThePreparedLearningCard() {
        val r = Rig().ready(read(9 to due), learn(9, due))
        r.nc.reload(); r.nc.loaded(2, card(1), listOf(key(1), key(2)))
        r.nc.prepared(key(1), 2, card(2, learning = read(9 to due)))
        assertEquals("a new load has its own", Cmd.ShowWaiting, r.nc.tapAnswer(3, 1)[1])
    }

    // ---- the rules and the read

    @Test fun theLearningReadKeepsTheNoteAndCardNumberToPrepareItBy() {
        assertTrue(PredictRule.LEARN_COLS.toList().containsAll(listOf("note_id", "ord")))
        val rows = listOf(
            mapOf("_id" to "5", "queue" to "1", "due" to "1700000100", "deck_id" to "7", "original_deck_id" to "0", "note_id" to "55", "ord" to "2"),
            mapOf("_id" to "6", "queue" to "1", "due" to "1700000200", "deck_id" to "7", "original_deck_id" to "0"), // an older AnkiDroid without them
        )
        val r = PredictRule.learnRead(rows, 7L, 1L).due!!
        assertEquals(StudyLoop.Key(55, 2), r[0].key); assertNull(r[1].key)
        assertEquals("equal as before when no key", PredictRule.Due(6L, 1700000200L), r[1])
    }

    @Test fun learningToPrepareIsTheEarliestCardThatCanBeDueWhileTheReadIsValid() {
        val x = key(1)
        fun pick(l: PredictRule.LearnRead?, next: Prepared<String> = card(2)) = PredictRule.learningToPrepare(x, next.copy(learning = l))?.cardId
        assertEquals(8L, pick(read(9 to due, 8 to due - 40)))
        assertEquals("not yet due is fine: it may come due before the tap", 9L, pick(read(9 to NOW_S + 300)))
        assertNull("due too late to be used while the read is valid", pick(read(9 to NOW_S - 30 + PredictRule.MAX_AGE_MS / 1000 + 1)))
        assertNull("a tie", pick(read(9 to due, 8 to due)))
        assertNull("the answered card itself", pick(read(1 to due)))
        assertEquals("the next card itself is skipped", 9L, pick(read(2 to due - 99, 9 to due)))
        assertNull("no read", pick(null)); assertNull(pick(PredictRule.LearnRead(NOW, null))); assertNull(pick(read()))
        assertNull("no key to prepare it by", PredictRule.learningToPrepare(x, card(2, learning = PredictRule.LearnRead(NOW - 1000, listOf(PredictRule.Due(9, due))))))
        assertEquals("before an intraday next card only", 9L, pick(read(9 to due, 8 to due + 60), card(2, queue = 1, due = due + 30)))
        assertNull(pick(read(8 to due + 60), card(2, queue = 1, due = due + 30)))
    }

    @Test fun theChoiceOfWhatToPrepareIsTheChoiceOfWhatToShow() {
        // whatever learningToPrepare picks is what firstLearning accepts at a tap that finds it due, for many reads
        val rng = Random(7)
        var accepted = 0
        repeat(2000) {
            val n = rng.nextInt(0, 5)
            val cards = (0 until n).map { (10 + it) to (NOW_S - 200 + rng.nextInt(0, 400)) }
            val l = PredictRule.LearnRead(NOW - 30_000, cards.map { PredictRule.Due(it.first.toLong(), it.second, key(it.first)) })
            val next = card(2, learning = l)
            val pick = PredictRule.learningToPrepare(key(1), next)
            val tapAt = NOW + rng.nextInt(0, 60_000)
            for (c in cards) {
                val ok = PredictRule.firstLearning(card(1), next, learn(c.first, c.second), tapAt)
                if (ok) { accepted++; assertEquals("the accepted card is the one prepared: $cards at $tapAt", pick?.cardId, c.first.toLong()) }
            }
        }
        assertTrue("some were accepted: $accepted", accepted > 100)
    }

    // ---- a model of AnkiDroid's v3 queue: the prediction is right, the swaps are gone, and many waits are gone

    private class Model(var nowMs: Long) {
        class C(val id: Long, var queue: Int, var due: Long = 0, var reps: Int = 0, var step: Int = 0)
        val main = ArrayList<C>()
        val learning = ArrayList<C>()
        var cutoff = nowMs / 1000
        val ahead = 1200L
        val nowSecs get() = nowMs / 1000

        fun order(): List<C> = learning.filter { it.due <= cutoff }.sortedBy { it.due } + main +
            learning.filter { it.due > cutoff && it.due <= cutoff + ahead }.sortedBy { it.due }

        fun byKey(k: StudyLoop.Key) = (main + learning).first { it.id == k.nid }

        fun answer(c: C, ease: Int) {
            assertSame("only the top card is answered", order().first(), c)
            main.remove(c); learning.remove(c)
            c.reps++
            val steps = listOf(60L, 600L)
            val secs: Long? = when (c.queue) {
                0, 1 -> when (ease) { 1 -> { c.step = 0; steps[0] }; 2 -> steps[c.step]; 3 -> if (c.step + 1 < steps.size) steps[++c.step] else null; else -> null }
                else -> if (ease == 1) { c.step = 0; 600L } else null
            }
            if (secs != null) {
                c.queue = 1; c.due = nowSecs + secs
                val aheadCutoff = cutoff + ahead
                val next = learning.minByOrNull { it.due }
                if (c.due <= aheadCutoff && main.isEmpty() && next != null && next.due >= c.due && next.due + 1 < aheadCutoff) c.due = next.due + 1
                learning += c
            } else c.queue = 2
            cutoff = nowSecs
        }

        fun prepared(c: C) = Prepared(
            StudyLoop.Key(c.id, 0),
            mapOf("_id" to "${c.id}", "note_id" to "${c.id}", "deck_id" to "1", "reps" to "${c.reps}", "queue" to "${c.queue}", "due" to "${c.due}"),
            "c${c.id}",
            PredictRule.LearnRead(nowMs, learning.map { PredictRule.Due(it.id, it.due, StudyLoop.Key(it.id, 0)) }),
        )
    }

    private class Run(val answers: Int, val predicted: Int, val swaps: Int, val waits: Int, val learning: Int, val notTopTries: Int, val learnWaits: Int)

    /** Like PredictSimTest: random sessions, think times of 1.5-40 s (learning cards come due meanwhile), writes up to 4.5 s late. [prepareLearning] = the R19-R feature on. */
    private fun session(seed: Int, prepareLearning: Boolean, ahead: Int): Run {
        val rng = Random(seed)
        val m = Model(1_700_000_000_000L)
        var id = 0L
        repeat(10 + rng.nextInt(20)) { m.main += Model.C(++id, if (rng.nextInt(3) == 0) 2 else 0) }
        val nc = NextCard<String>(1, { ++id }, { m.nowMs }, maxAhead = ahead, check = PredictRule::check)
        class W(val key: StudyLoop.Key, val ease: Int, val tapAt: Long, val reps: Int)
        val writes = ArrayDeque<W>()
        val waiting = ArrayList<W>()
        var ioFreeAt = 0L
        val io = ArrayDeque<() -> Unit>()
        var answers = 0
        var written = 0
        var changed = 0
        var notTopTries = 0
        fun cmds(list: List<Cmd<String>>) {
            for (c in list) when (c) {
                is Cmd.Enqueue -> { writes += W(c.answer.key, c.answer.ease, m.nowMs, c.answer.before.getValue("reps")!!.toInt()); answers++ }
                is Cmd.Load -> io += {
                    if (ahead == 0) m.nowMs += rng.nextLong(0, 4_500)
                    while (writes.isNotEmpty()) {
                        val w = writes.removeFirst()
                        val card = (m.main + m.learning).firstOrNull { it.id == w.key.nid }
                        // AnswerQueue rechecks the shown state: a stale learning prediction ends CHANGED, never a second write.
                        if (card == null || card.reps != w.reps) { changed++; continue }
                        if (ahead == 0) {
                            if (m.order().first() === card) { m.answer(card, w.ease); written++ } else { notTopTries++; waiting += w }
                            continue
                        }
                        ioFreeAt = maxOf(ioFreeAt, w.tapAt) + rng.nextLong(0, 4_500)
                        val now = m.nowMs
                        m.nowMs = ioFreeAt
                        if (m.order().first() !== card) { notTopTries++; waiting += w } else { m.answer(card, w.ease); written++ }
                        m.nowMs = maxOf(now, ioFreeAt)
                    }
                    while (true) {
                        val top = m.order().firstOrNull() ?: break
                        val held = waiting.firstOrNull { it.key.nid == top.id } ?: break
                        waiting.remove(held)
                        if (top.reps != held.reps) changed++ else { m.answer(top, held.ease); written++ }
                    }
                    val q = m.order().filterNot { c -> waiting.any { it.key.nid == c.id } }.take(maxOf(3, ahead + 2))
                    val keys = q.map { StudyLoop.Key(it.id, 0) }
                    cmds(when (val step = LoadPlan.decide(keys, c.predicted)) {
                        LoadPlan.Step.Finished -> nc.finished(c.token)
                        is LoadPlan.Step.Confirm -> nc.confirmed(c.token, step.key, keys)
                        is LoadPlan.Step.Fetch -> if (c.predicted != null) nc.swapped(c.token, m.prepared(q[0]), keys) else nc.loaded(c.token, m.prepared(q[0]), keys)
                    })
                }
                is Cmd.Prepare -> io += prepare@ {
                    val p = runCatching { m.prepared(m.byKey(c.key)) }.getOrNull() ?: return@prepare
                    nc.prepared(c.forKey, c.forToken, p)
                    // the screen does this for the Prepare of the first card after the confirmed one (StudyActivity.prepareLearning)
                    val top = m.order().firstOrNull()
                    if (prepareLearning && top != null && c.forKey == StudyLoop.Key(top.id, 0)) {
                        PredictRule.learningToPrepare(c.forKey, p)?.key?.let { k -> nc.preparedLearning(c.forKey, c.forToken, m.prepared(m.byKey(k))) }
                    }
                }
                else -> {}
            }
        }
        cmds(nc.reload())
        var guard = 0
        while (guard++ < 4000) {
            val fast = ahead > 0 && nc.slot is NextCard.Slot.Predicted<*> && rng.nextInt(100) < 70
            if (!fast) while (io.isNotEmpty()) io.removeFirst()()
            if (nc.slot is NextCard.Slot.Finished) break
            m.nowMs += if (fast) rng.nextLong(100, 1_500) else rng.nextLong(1_500, 40_000)
            cmds(nc.tapAnswer(listOf(1, 1, 2, 3, 3, 3, 4)[rng.nextInt(7)], 1000))
        }
        assertTrue("seed $seed: the deck is finished", nc.slot is NextCard.Slot.Finished)
        assertTrue("seed $seed: every refused answer was resolved when its card was first", waiting.isEmpty())
        assertEquals("seed $seed: every tap is written once or explicitly refused as CHANGED", answers, written + changed)
        return Run(answers, nc.predicted, nc.misses, nc.withheld.values.sum() + nc.noPrediction, nc.predictedLearning, notTopTries, nc.withheld[Why.LEARNING_DUE] ?: 0)
    }

    @Test fun inTheV3ModelThePredictedLearningCardIsRarelySwappedAndMostLearningWaitsAreGone() {
        var offWaits = 0; var onWaits = 0; var offLearnWaits = 0; var onLearnWaits = 0; var swaps = 0; var learning = 0; var predictedOn = 0; var predictedOff = 0; var answers = 0
        for (seed in 1..200) {
            val off = session(seed, prepareLearning = false, ahead = 0)
            val on = session(seed, prepareLearning = true, ahead = 0)
            offWaits += off.waits; onWaits += on.waits; offLearnWaits += off.learnWaits; onLearnWaits += on.learnWaits
            swaps += on.swaps; learning += on.learning; predictedOn += on.predicted; predictedOff += off.predicted; answers += on.answers
        }
        println("v3 model, 200 sessions: waits without $offWaits (learning card due $offLearnWaits), with $onWaits (learning card due $onLearnWaits); predicted learning $learning; swaps $swaps")
        // R19-FIX RV-1 (Adam's 278b addition): a card due only within the slack counts as next too, so the confirm now swaps a guess in the
        // few cases where the write came before the card was due (this model: writes up to 4.5 s late); it is rare, and no write goes astray
        assertTrue("a predicted learning card is rarely swapped away in the model: $swaps of $learning", swaps * 5 < learning)
        assertTrue("the model really has learning cards come due: $offLearnWaits", offLearnWaits > 100)
        assertTrue("predicted learning cards shown: $learning", learning > 50)
        assertTrue("fewer 'learning card due' waits: $onLearnWaits < $offLearnWaits", onLearnWaits < offLearnWaits)
        assertTrue("learning predictions replace waits", learning > 0 && offLearnWaits > onLearnWaits)
        assertTrue("answers: $answers predicted $predictedOn (before $predictedOff)", predictedOn > predictedOff)
    }

    @Test fun inTheV3ModelAnswersAheadRecoverNotTopAttemptsAndDisagreementsAreRare() {
        var refused = 0; var swaps = 0; var learning = 0; var heldOrWaits = 0
        for (seed in 1..200) {
            val on = session(seed, prepareLearning = true, ahead = NextCard.AHEAD)
            refused += on.notTopTries; swaps += on.swaps; learning += on.learning; heldOrWaits += on.waits
        }
        println("v3 model, answers ahead: not-top attempts $refused, swaps $swaps, predicted learning $learning, waits $heldOrWaits")
        assertTrue("not-top attempts remain rare: $refused of $learning learning predictions", refused * 5 < learning)
        assertTrue("swaps are rare (only a card due within the slack that was not due yet): $swaps of $learning", swaps * 5 < learning)   // R19-FIX RV-1
        assertTrue("the feature is used with answers ahead too: $learning", learning > 20)
    }
}
