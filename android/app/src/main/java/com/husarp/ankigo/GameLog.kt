package com.husarp.ankigo

import android.content.Context
import com.husarp.ankigo.debug.DelayedFlush
import com.husarp.ankigo.debug.GameEventText
import com.husarp.ankigo.debug.RollingGameLog
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.BattleLog
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Background IO: never delay studying, saving or the battle to write diagnostics. */
object GameLog {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ankigo-event-log").apply { isDaemon = true } }
    private var journal: RollingGameLog? = null // worker-owned
    /** At most one file rewrite + fsync per ~2 s (a diagnostics log: the last 2 s may be lost on a process kill). */
    private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "ankigo-event-log-flush").apply { isDaemon = true } }
    private val flush = DelayedFlush(2000, { ms, task -> timer.schedule({ worker.execute(task) }, ms, TimeUnit.MILLISECONDS) }) { runCatching { journal?.persist() } }
    @Synchronized fun init(ctx: Context) {
        if (initialized) return
        initialized = true
        BattleLog.init(ctx)
        val file = File(ctx.applicationContext.filesDir, "game-events.log")
        worker.execute { runCatching { journal = RollingGameLog(file, flush = flush) } }
    }
    private var initialized = false
    private fun append(lines: List<String>) { worker.execute { runCatching { journal?.append(lines) } } }
    fun events(events: List<GameEvent>) { runCatching { if (events.isNotEmpty()) append(events.takeLast(500).map(GameEventText::event)) }; BattleLog.events(events) }
    fun answer(source: String, cardId: Long, button: Int) { append(listOf(GameEventText.answer(source, cardId, button))); BattleLog.answer(source, cardId, button) }
    fun error(tag: String, error: Throwable? = null) = append(listOf(GameEventText.error(tag, error?.javaClass?.simpleName ?: "Failure")))
    fun imported(source: String, count: Int, seq: Long) = append(listOf("Import source=${if (source == "ankimon") "ankimon" else "restore"} mons=$count seq=$seq"))
    fun all(): String = try { worker.submit<String> { journal?.all() ?: "Game log unavailable" }.get(3, TimeUnit.SECONDS) } catch (_: Exception) { "Game log unavailable" }
    fun clear() { worker.execute { runCatching { journal?.clear() } } }
}
