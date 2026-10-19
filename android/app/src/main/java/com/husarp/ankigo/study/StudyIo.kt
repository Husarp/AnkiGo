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
import com.husarp.ankigo.ui.BattleLog
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The process-wide study io (R9 wave 6, item 1): the one `ankigo-study-io` thread for EVERY study provider call, the answer write
 * queue with its journal (`filesDir/study-answers.json`), and the game step of a saved answer. It lives as long as the process: a
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

        /**
         * Runs even when the study screen is gone (the answer is saved in AnkiDroid, so the game must count it once). The game
         * must be loaded for it: after a restart, the replay of a journal can be saved before the game has loaded.
         */
        override fun gameStep(saved: AnswerOutcome.Saved, front: String?, onTiming: (Long, Long) -> Unit, done: (List<GameEvent>) -> Unit) {
            val a = saved.answer
            val step = {
                Local.recordReview(app, a.nid, a.ord, a.ease, a.tookMs, "app")
                val fresh = CardState.from(saved.after)
                if (fresh == null) done(emptyList())
                else {
                    if (front != null) BattleLog.front(fresh.id, front) // the Battle log shows the card's front
                    LiveSession.ownAnswer(app, fresh, StudyRules.gameButton(a.buttons, a.ease), onTiming) { done(it) }
                }
            }
            if (GameRepo.game == null) GameRepo.load(app) { step() } else step()
        }
    }
}
