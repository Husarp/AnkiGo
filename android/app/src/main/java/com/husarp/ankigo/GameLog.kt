package com.husarp.ankigo

import android.content.Context
import com.husarp.ankigo.debug.GameEventText
import com.husarp.ankigo.debug.LogLine
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.BattleLog

/**
 * The game's lines in the debug log (R13 Part E: one dated log, [Report], instead of the separate 500-event ring of ids): every
 * GameEvent of every action with its readable text ([GameEventText]), every answer, imports and game errors. The texts are built
 * on the log's own thread, so the answer path only queues them. The Battle log (the screen) is fed from here too.
 */
object GameLog {
    private const val TAG = "GAME"

    @Synchronized fun init(ctx: Context) {
        if (initialized) return
        initialized = true
        BattleLog.init(ctx)
    }
    private var initialized = false

    fun events(events: List<GameEvent>) {
        if (events.isNotEmpty()) { val copy = events.toList(); Report.logLater(TAG) { copy.map(GameEventText::event) } }
        BattleLog.events(events)
    }

    fun answer(source: String, cardId: Long, button: Int, neutral: Boolean = false) {
        Report.logLater(TAG) { listOf(GameEventText.answer(source, cardId, button) + if (neutral) " neutral" else "") }
        BattleLog.answer(source, cardId, button)
    }

    fun error(tag: String, error: Throwable? = null) =
        Report.log(TAG, GameEventText.error(tag, error?.javaClass?.simpleName ?: "Failure") + (error?.let { "\n" + LogLine.stack(it) } ?: ""))

    fun imported(source: String, count: Int, seq: Long) =
        Report.log(TAG, "Import source=${if (source == "ankimon") "ankimon" else "restore"} mons=$count seq=$seq")
}
