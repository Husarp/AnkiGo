package com.husarp.ankigo.ui

import android.content.Context
import com.husarp.ankigo.debug.DelayedFlush
import com.husarp.ankigo.game.GameEvent
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// TICKET R2-07: the persisted Battle log. A small text file in filesDir (not the game save, not the diagnostics log).

/** Text form of the battles: one tab-separated record per line, tabs / newlines / backslashes in texts escaped. */
object BattleLogCodec {
    private const val HEAD = "battlelog 1"

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")
    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                sb.append(when (val n = s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> n }); i += 2
            } else { sb.append(ch); i++ }
        }
        return sb.toString()
    }

    fun encode(battles: List<LogBattle>): String {
        val sb = StringBuilder(HEAD).append('\n')
        for (b in battles) {
            sb.append("B\t${b.startedAt}\t${b.level}\t${if (b.shiny) 1 else 0}\t${b.outcome.name}\t${esc(b.wild ?: "")}\n")
            for (r in b.rounds) {
                sb.append("R\t${r.kind.name}\t${r.multiplier ?: "-"}\t${r.points}\n")
                for (a in r.answers) sb.append("A\t${a.button}\t${if (a.catchUp) 1 else 0}\t${a.coins}\t${esc(a.card)}\n")
                for (l in r.lines) sb.append("L\t${l.tone.name}\t${esc(l.text)}\n")
            }
            for (e in b.entries) sb.append("E\t${e.round}\t${e.tone.name}\t${esc(e.text)}\n")
            for (l in b.result) sb.append("Z\t${l.tone.name}\t${esc(l.text)}\n")
        }
        return sb.toString()
    }

    /** Never throws: a damaged line is skipped, an unknown file gives no battles. */
    fun decode(text: String): MutableList<LogBattle> {
        val out = ArrayList<LogBattle>()
        val lines = text.lines()
        if (lines.firstOrNull() != HEAD) return out
        var round: LogRound? = null
        for (line in lines.drop(1)) {
            val f = line.split('\t')
            runCatching {
                when (f[0]) {
                    "B" -> {
                        out += LogBattle(f[1].toLong(), unesc(f[5]).ifEmpty { null }, f[2].toInt(), f[3] == "1", Outcome.valueOf(f[4]))
                        round = null
                    }
                    "R" -> round = LogRound(RoundKind.valueOf(f[1]), f[2].toDoubleOrNull(), f[3].toInt()).also { out.last().rounds += it }
                    "A" -> round!!.answers += LogAnswer(f[1].toInt(), unesc(f[4]), f[2] == "1", f[3].toInt())
                    "L" -> round!!.lines += LogLine(Tone.valueOf(f[1]), unesc(f[2]))
                    "E" -> out.last().entries += BattleLogEntry(f[1].toInt(), Tone.valueOf(f[2]), unesc(f[3]))
                    "Z" -> out.last().result += LogLine(Tone.valueOf(f[1]), unesc(f[2]))
                }
            }
        }
        return out
    }
}

/**
 * The Battle log store. Fed from the same points as [com.husarp.ankigo.GameLog] (answers and game events), keeps readable
 * details, last [BattleLogBuilder.KEEP] finished battles + the current one. All file work runs on one background thread
 * (never delay studying, saving or the battle); until [init] has run nothing is recorded (unit tests).
 */
object BattleLog {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ankigo-battle-log").apply { isDaemon = true } }
    private var builder = BattleLogBuilder() // worker-owned
    private var file: File? = null           // worker-owned
    @Volatile private var started = false
    /** At most one file rewrite + fsync per ~3 s; the delayed persist runs on [worker] (the builder is worker-owned). */
    private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "ankigo-battle-log-flush").apply { isDaemon = true } }
    private val flush = DelayedFlush(3000, { ms, task -> timer.schedule({ worker.execute(task) }, ms, TimeUnit.MILLISECONDS) }) { runCatching { persist() } }

    /** Worker-owned short fronts, ordered before the answer record. HTML stripping never runs on the tap. */
    private val fronts = LinkedHashMap<Long, String>()

    @Synchronized fun init(ctx: Context) {
        if (started) return
        started = true
        val f = File(ctx.applicationContext.filesDir, "battle-log.txt")
        worker.execute {
            file = f
            runCatching { if (f.isFile && f.length() < 2_000_000) builder = BattleLogBuilder(battles = BattleLogCodec.decode(f.readText())) }
        }
    }

    /** The study screen knows the card's front: remember a short plain text of it for the answer that follows. */
    internal var frontText: (String) -> String = ::plain

    fun front(cardId: Long, html: String) {
        if (!started) return
        worker.execute {
            val t = frontText(html)
            if (t.isNotEmpty()) {
                fronts[cardId] = t
                while (fronts.size > 8) fronts.remove(fronts.keys.first())
            }
        }
    }

    fun answer(source: String, cardId: Long, button: Int) {
        if (!started) return
        worker.execute { runCatching { builder.answer(source, cardId, button, fronts.remove(cardId)) } }
    }

    fun events(events: List<GameEvent>) {
        if (!started || events.isEmpty()) return
        val copy = events.toList()
        worker.execute { runCatching { builder.feed(copy); flush.request() } }
    }

    fun alert(text: String) {
        if (!started) return
        worker.execute { runCatching { builder.alert(text); flush.request() } }
    }

    fun message(text: String) {
        if (!started) return
        worker.execute { runCatching { builder.message(text); flush.request() } }
    }

    /** Newest battle first; empty when nothing is logged (or the worker is stuck). Call it off the main thread. */
    fun snapshot(): List<LogBattle> =
        try { worker.submit<List<LogBattle>> { builder.newestFirst().map { it.copy() } }.get(3, TimeUnit.SECONDS) } catch (_: Exception) { emptyList() }

    private fun persist() {
        val f = file ?: return
        f.parentFile?.mkdirs()
        val temp = File(f.path + ".tmp")
        FileOutputStream(temp).use { out -> out.write(BattleLogCodec.encode(builder.battles).toByteArray(Charsets.UTF_8)); out.fd.sync() }
        try { Files.move(temp.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }

    private val TAG = Regex("<[^>]*>")
    private val SPACE = Regex("\\s+")

    /** Card HTML -> one short line of plain text (about 40 characters). */
    internal fun plain(html: String): String {
        val noScript = html.replace(Regex("<(script|style)\\b.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        val t = TAG.replace(noScript.replace(Regex("<br\\s*/?>|</div>|</p>", RegexOption.IGNORE_CASE), " "), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        val one = SPACE.replace(t, " ").trim()
        return if (one.length > 40) one.take(39).trimEnd() + "…" else one
    }
}
