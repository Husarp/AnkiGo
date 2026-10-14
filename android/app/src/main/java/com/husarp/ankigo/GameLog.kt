package com.husarp.ankigo

import android.content.Context
import com.husarp.ankigo.debug.GameEventText
import com.husarp.ankigo.debug.RollingGameLog
import com.husarp.ankigo.game.GameEvent
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Background IO: never delay studying, saving or the battle to write diagnostics. */
object GameLog {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ankigo-event-log").apply { isDaemon = true } }
    private var journal: RollingGameLog? = null // worker-owned
    @Synchronized fun init(ctx: Context) {
        if (initialized) return
        initialized = true
        val file = File(ctx.applicationContext.filesDir, "game-events.log")
        worker.execute { runCatching { journal = RollingGameLog(file) } }
    }
    private var initialized = false
    private fun append(lines: List<String>) { worker.execute { runCatching { journal?.append(lines) } } }
    fun events(events: List<GameEvent>) { runCatching { if (events.isNotEmpty()) append(events.takeLast(500).map(GameEventText::event)) } }
    fun answer(source: String, cardId: Long, button: Int) = append(listOf(GameEventText.answer(source, cardId, button)))
    fun error(tag: String, error: Throwable? = null) = append(listOf(GameEventText.error(tag, error?.javaClass?.simpleName ?: "Failure")))
    fun imported(source: String, count: Int, seq: Long) = append(listOf("Import source=${if (source == "ankimon") "ankimon" else "restore"} mons=$count seq=$seq"))
    fun all(): String = try { worker.submit<String> { journal?.all() ?: "Game log unavailable" }.get(3, TimeUnit.SECONDS) } catch (_: Exception) { "Game log unavailable" }
    fun clear() { worker.execute { runCatching { journal?.clear() } } }
}
