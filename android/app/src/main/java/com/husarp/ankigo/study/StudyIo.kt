package com.husarp.ankigo.study

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.husarp.ankigo.Anki
import com.husarp.ankigo.CardState
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.Local
import com.husarp.ankigo.Report
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.live.OwnAhead
import com.husarp.ankigo.ui.BattleLog
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The process-wide study io (R9 wave 6, item 1): the one `ankigo-study-io` thread for EVERY study provider call, the answer write
 * queue with its journal (`filesDir/study-answers.json`), and the game step of each answer (at the tap). It lives as long as the process: a
 * study screen being closed never stops an answer from being written. Created by the first screen (main thread); that restores the
 * journal and replays the answers it holds before anything else runs. The logic is in [AnswerHost].
 */
object StudyIo {
    private const val FILE = "study-answers.json"

    @Volatile private var host: AnswerHost? = null

    @Synchronized
    fun host(ctx: Context): AnswerHost {
        host?.let { return it }
        val app = ctx.applicationContext
        Anki.load(app)
        val io = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { r -> Thread(r, "ankigo-study-io").apply { isDaemon = true } }
        return AnswerHost(AnkiProvider(app), FileJournalStore(File(app.filesDir, FILE)), AndroidEnv(app), io).also {
            host = it
            it.start()
        }
    }

    /** The provider calls of the write queue, straight on AnkiDroid's official provider (all on the study io thread). */
    private class AnkiProvider(private val app: Context) : AnswerProvider {
        override fun select(deckId: Long) { Anki.selectDeck(app, deckId) }
        override fun top(deckId: Long, limit: Int) = Anki.nextCards(app, deckId, limit).map { StudyLoop.Key(it.nid, it.ord) }
        override fun stats(key: StudyLoop.Key) = Anki.card(app, key.nid, key.ord, CardState.COLS)
        override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) { Anki.answer(app, key.nid, key.ord, ease, ms) }
    }

    private class AndroidEnv(private val app: Context) : AnswerHost.Env {
        private val main = Handler(Looper.getMainLooper())

        override fun post(r: Runnable) { main.post(r) }
        override fun elapsed() = SystemClock.elapsedRealtime()
        override fun expect(before: Map<String, String?>, pinned: Boolean) { CardState.from(before)?.let { LiveSession.expectOwn(it, pinned) } }
        override fun cancel(before: Map<String, String?>) { CardState.from(before)?.let { LiveSession.cancelOwn(it) } }
        override fun log(text: String) { Report.log("STUDY", text) }

        /** At the tap (R10-B23): the button and the card as shown are all the game needs. The game is loaded before a card can be answered. */
        override fun gameStep(answer: QueuedAnswer, front: String?, done: (List<GameEvent>, Long) -> Unit) {
            val before = CardState.from(answer.before) ?: return done(emptyList(), -1)
            val step = {
                val t0 = SystemClock.elapsedRealtime()
                if (front != null) BattleLog.front(before.id, front) // the Battle log shows the card's front
                val events = LiveSession.ownAnswer(app, before, StudyRules.gameButton(answer.buttons, answer.ease))
                done(events, SystemClock.elapsedRealtime() - t0)
            }
            if (GameRepo.game == null) GameRepo.load(app) { step() } else step()
        }

        /** The game must be loaded to tell which replayed answers its save already counted (the replay can start before it has). */
        override fun restored(answers: List<QueuedAnswer>, done: () -> Unit) {
            GameRepo.load(app) {
                try {
                    LiveSession.ownRestored(app, answers.mapNotNull { a -> CardState.from(a.before)?.let { it to StudyRules.gameButton(a.buttons, a.ease) } })
                } finally {
                    done() // the replay must never wait for ever
                }
            }
        }

        override fun settled(answer: QueuedAnswer, after: Map<String, String?>, saved: Boolean) {
            Local.recordReview(app, answer.nid, answer.ord, answer.ease, answer.tookMs, "app")
            val before = CardState.from(answer.before) ?: return
            LiveSession.ownSettled(app, before, CardState.from(after) ?: OwnAhead.predicted(before))
        }

        override fun dropped(answer: QueuedAnswer) { CardState.from(answer.before)?.let { LiveSession.ownDropped(app, it) } }
    }
}
