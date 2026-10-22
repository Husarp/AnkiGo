package com.husarp.ankigo.ui

import com.husarp.ankigo.game.lookSpecies
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.Report
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.game.CatchBall
import com.husarp.ankigo.game.DailyGift
import com.husarp.ankigo.game.DailyGoal
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.DuplicateResult
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.sound.GameSound
import com.husarp.ankigo.ui.movefx.MoveFxCatalog
import com.husarp.ankigo.ui.PanelText.em
import java.util.Locale
import kotlin.math.max

/**
 * Plays [GameEvent]s on the panel, one after the other: the next event starts when the previous animation is done.
 * The game state is already final when events arrive, so every visual comes from the numbers inside the events.
 * Pace: an answer's events take about a second; with 4 or more answers waiting the durations are halved, and with
 * 10 or more the animations are skipped and one summary line shows (then the panel is redrawn from the saved game).
 */
class EventPlayer(private val panel: BattlePanelView, private val continueOnPanel: Boolean = true) {
    private sealed class Item {
        class Ev(val e: GameEvent) : Item()
        class Act(val block: () -> Unit) : Item()
        /** The catch result tiles of a fight that ended (PLAN 40); dropped when the animations are skipped. */
        class Tiles(val summary: CatchSummary) : Item()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Item>()
    private val arena get() = panel.arena
    private var busy = false
    private var epoch = 0          // bumped by clear(); callbacks of an older epoch do nothing
    private var dirty = false      // something was played since the last redraw from the state
    private var factor = 1.0
    private var lastButton = 0
    private var mult = 1.0
    private var wildMult = 1.0   // R10-B22: the wild one's damage factor of this round (shown on its attack line like the Main's)
    private var logWhole = true           // R9 D3: the line of the event being played is about the whole battle (the bar shows it); else it is only spoken
    /** The next fight's Encounter, held back while the result tiles show (it plays with the next answer's events). */
    private var parked: GameEvent.Encounter? = null
    /** R9-W6 CX-A: the player's XpGained whose burst already played inside the Caught step; its own step only waits for what is left ([doneAt] = uptime ms). */
    private class XpRun(val ev: GameEvent.XpGained, val doneAt: Long)
    private var xpRun: XpRun? = null

    /** True when nothing is playing or waiting. */
    val idle: Boolean get() = !busy && queue.isEmpty()

    fun enqueue(events: List<GameEvent>) {
        if (events.isEmpty()) return
        releaseTiles()
        val summary = CatchSummary.of(events, data = GameRepo.data) // the throw number comes with the BallThrow event (counted by the fight in the game state)
        // R9 C5: a won / caught fight with a next one behind it holds that one from now on (Continue shows at once, the rewards keep playing);
        // screens whose Continue sits under the result tiles queue the tiles right behind the fight's end so they show early too
        val early = summary != null && NextFight.holdsEarly(events, panel.resultMode())
        val at = when {
            summary == null -> -1
            early && continueOnPanel -> NextFight.earlyTilesIndex(events)
            else -> CatchTilesPlan.resultIndex(events)
        }
        events.forEachIndexed { i, e ->
            if (i == at) queue.addLast(Item.Tiles(summary!!))
            queue.addLast(Item.Ev(e))
        }
        if (at == events.size) queue.addLast(Item.Tiles(summary!!))
        for (e in events) if (e is GameEvent.Encounter) panel.prefetchBackdrop(e.species, e.wildUid)   // R9 C4: its picture loads while the rewards play
        if (early) panel.holdEarly()
        pump()
    }

    /**
     * PLAN 191: the fight events made on a screen that is not a battle (an item on the main from the Bag, a team edit that changed
     * it) wait in [GameRepo]; a host that is on screen and idle takes them here ONCE and plays them like an in-battle switch: the
     * Pokémon comes in at its HP before the hit ([BattlePanelView] draws it so until now), then the wild one's attack. Returns true
     * when events were queued. Busy: they stay for the next time. Events of a fight that is over (another wild one, no battle) are
     * dropped; two hosts on screen: the first takes them, the other just shows the end state.
     */
    fun playPending(): Boolean {
        if (!idle) return false
        val events = GameRepo.takePendingFightEvents()
        if (events.isEmpty()) return false
        enqueue(events)
        return true
    }

    /** Runs [block] in order with the events (e.g. a log line for a catch-up batch). */
    fun enqueueAction(block: () -> Unit) {
        queue.addLast(Item.Act(block))
        pump()
    }

    /** Drops everything waiting and stops the animations. The caller redraws from the state afterwards. */
    fun clear() {
        parked = null
        xpRun = null
        panel.hideCatchTiles()
        epoch++
        queue.clear()
        busy = false
        dirty = false
        handler.removeCallbacksAndMessages(null)
        panel.cancelAnimations()
    }

    /**
     * Ends everything playing or waiting at once and draws the saved state (the numbers in the dropped events are
     * already in it), for a tap whose result must show right away. Events enqueued afterwards play as usual.
     */
    fun finishNow() {
        val events = ArrayList<GameEvent>()
        val acts = ArrayList<() -> Unit>()
        while (queue.isNotEmpty()) queue.removeFirst().let { if (it is Item.Ev) events += it.e else if (it is Item.Act) acts += it.block }
        clear()
        events.forEach { panel.observeStudyEvent(it) }
        panel.renderAll()
        acts.forEach { it() }
    }

    // ---------------------------------------------------------------- queue

    private fun pump() {
        if (busy) return
        while (true) {
            val item = queue.removeFirstOrNull()
            if (item == null) {
                if (dirty) { dirty = false; panel.renderAll() }
                return
            }
            val answers = queue.count { it is Item.Ev && it.e is GameEvent.Counted } + if (item is Item.Ev && item.e is GameEvent.Counted) 1 else 0
            if (answers >= SKIP_AT || !android.animation.ValueAnimator.areAnimatorsEnabled() || panel.context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)) { skipAll(item); continue }
            factor = if (answers >= SLOW_AT) 0.5 else 1.0
            val ms = when (item) {
                is Item.Act -> {
                    dirty = true
                    try { item.block() } catch (t: Throwable) { Report.fail("PANEL", "queued action", t) }
                    0L
                }
                is Item.Tiles -> {
                    dirty = true
                    try { panel.showCatchTiles(item.summary, answerWaiting()) } catch (t: Throwable) { Report.fail("PANEL", "catch tiles", t) }
                    0L
                }
                is Item.Ev -> try {
                    dirty = true
                    play(item.e)
                } catch (t: Throwable) {
                    Report.fail("PANEL", "event ${item.e.javaClass.simpleName}", t)
                    0L
                }
            }
            if (ms == PENDING) { busy = true; return }   // R9 C4: the event ends itself ([resume]) once its picture is there
            if (ms > 0) {
                busy = true
                resume(ms)
                return
            }
        }
    }

    /** The event being played is over in [ms]: the next one starts. */
    private fun resume(ms: Long) {
        val my = epoch
        handler.postDelayed({ if (my == epoch) { busy = false; arena.releaseMove(); pump() } }, ms)
    }

    /** An answer (or a ball throw) is queued behind what is playing: the result tiles would only flash by. */
    private fun answerWaiting() = queue.any { it is Item.Ev && (it.e is GameEvent.Counted || it.e is GameEvent.BallThrow) }

    /** The next answer is here: the tiles go and the held next fight comes first. */
    private fun releaseTiles() {
        panel.hideCatchTiles()
        parked?.let { parked = null; queue.addFirst(Item.Ev(it)) }
    }

    /** True while a fight result holds the next wild one back (a Continue is pending). */
    val holding: Boolean get() = panel.holdsNextFight

    /**
     * "Continue" after a fight result (R6-09): the tiles go and the held next wild one makes its entrance; the card in front of the
     * player is not touched. Returns false when no result holds (nothing happened). The next answer's events release the same way
     * ([enqueue]), so an answer that arrives anyway never waits for a tap.
     */
    fun continueBattle(): Boolean {
        if (!panel.holdsNextFight) return false
        if (!idle) { skipToNextFight(); return true }   // R9 C5: the rewards still play (Continue came at once): they end now
        releaseTiles()
        dirty = true   // nothing parked: the wild one is drawn from the saved game when the queue is done
        pump()
        return true
    }

    /**
     * R9 C5: Continue while the reward animations of the won / caught fight still play (or wait). The rewards are in the saved game
     * already, so: what plays stops (and its cues stay silent), every event up to the next fight's Encounter is only observed, the
     * panel is drawn from the saved game with the next wild one still held back (it is not drawn), queued actions run, the result
     * tiles go, and the Encounter plays as usual (new background first, C4). What follows the Encounter in the queue plays after it.
     */
    private fun skipToNextFight() {
        val items = ArrayList<Item>()
        while (queue.isNotEmpty()) items += queue.removeFirst()
        val held = parked
        parked = null
        xpRun = null
        val before: List<Item>
        val next: GameEvent.Encounter?
        val after: List<Item>
        if (held != null) {
            before = emptyList(); next = held; after = items   // the next fight was parked already: all that is queued follows it
        } else {
            val s = NextFight.splitAtNext(items) { it is Item.Ev && it.e is GameEvent.Encounter }
            before = s.before; next = ((s.next as? Item.Ev)?.e as? GameEvent.Encounter); after = s.after
        }
        epoch++
        busy = false
        mult = 1.0; wildMult = 1.0
        handler.removeCallbacksAndMessages(null)
        GameSound.cancelPending()
        panel.cancelAnimations()
        for (item in before) if (item is Item.Ev) panel.observeStudyEvent(item.e)
        panel.renderAll()   // still holding: the player side, the footer and the numbers come from the saved game, the next wild one waits
        for (item in before) if (item is Item.Act) try { item.block() } catch (t: Throwable) { Report.fail("PANEL", "queued action", t) }
        panel.hideCatchTiles()
        if (next != null) queue.addLast(Item.Ev(next))
        queue.addAll(after)
        dirty = true
        pump()
    }

    /**
     * The screen is left under a result: it goes without an entrance (nothing is played to an invisible panel) and the new wild one
     * is drawn from the saved game, so nobody comes back to a stuck Continue.
     */
    fun dropHold() {
        if (!panel.holdsNextFight) return
        if (!idle) { finishNow(); return }   // R9 C5: the hold started with the winning batch, its rewards may still play: they end, the saved game shows
        parked = null
        panel.hideCatchTiles()
        panel.renderAll()
    }

    /** Too many answers waiting: no animations, one summary line, then the saved state. */
    private fun skipAll(first: Item) {
        val events = ArrayList<GameEvent>()
        val acts = ArrayList<() -> Unit>()
        xpRun = null
        var result: CatchSummary? = null
        fun take(i: Item) { if (i is Item.Ev) events += i.e else if (i is Item.Act) acts += i.block else if (i is Item.Tiles) result = i.summary }
        take(first)
        while (queue.isNotEmpty()) take(queue.removeFirst())
        panel.releaseEarlyHold()   // R9 C5: the early hold of a batch that is skipped goes; the result below decides again
        panel.cancelAnimations()
        events.forEach { panel.observeStudyEvent(it) }
        panel.renderAll()
        acts.forEach { it() }
        val lone = events.singleOrNull() as? GameEvent.Encounter   // Continue with reduced motion: just the new wild one
        if (lone != null) {
            logWhole = false
            log(LogKind.SYS, "NEW", "A wild ${em(bare(lone.name))}" + (if (lone.shiny) " ${em("(shiny!)")}" else "") + " appeared!")
        }
        else if (events.isNotEmpty()) panel.setLog(LogKind.SYS, "FAST", summary(events))
        // One answer's fight result still shows its tiles (no animation needed); a pile of answers gets only the summary line.
        result?.let { r ->
            if (events.count { it is GameEvent.Counted } <= 1 && panel.showCatchTiles(r, false) == CatchTilesPlan.Mode.HOLD) {
                parked = events.lastOrNull { it is GameEvent.Encounter } as? GameEvent.Encounter
                panel.renderAll()
            }
        }
        dirty = false
    }

    private fun summary(events: List<GameEvent>): String {
        var answers = 0; var won = 0; var caught = 0; var levels = 0; var evolved = 0; var cash = 0
        for (e in events) when (e) {
            is GameEvent.Counted -> answers++
            is GameEvent.Defeated -> won++
            is GameEvent.Caught -> caught++
            is GameEvent.LevelUp -> levels++
            is GameEvent.Evolved -> evolved++
            is GameEvent.Cash -> cash += e.amount
            else -> {}
        }
        val parts = ArrayList<String>()
        parts += "$answers answer${if (answers == 1) "" else "s"}"
        if (won > 0) parts += "$won won"
        if (caught > 0) parts += "$caught caught"
        if (levels > 0) parts += "$levels level-up${if (levels == 1) "" else "s"}"
        if (evolved > 0) parts += "$evolved evolved"
        if (cash > 0) parts += "+$cash ₽"
        return "Played fast: " + parts.joinToString(", ")
    }

    // ---------------------------------------------------------------- helpers

    private fun d(ms: Long) = max(1L, (ms * factor).toLong())

    private fun after(ms: Long, f: () -> Unit) {
        val my = epoch
        handler.postDelayed({ if (my == epoch) f() }, d(ms))
    }

    /** Runs [f] in [ms] of real time (no pace scaling: it comes from an effect that is scaled already); at once when [ms] is 0. */
    private fun atMs(ms: Long, f: () -> Unit) {
        if (ms <= 0L) { f(); return }
        val my = epoch
        handler.postDelayed({ if (my == epoch) f() }, ms)
    }

    /** Looks ahead in the queue: after the wild one fainted, does a Caught (not a Defeated) come first? */
    private fun willBeCaught(): Boolean {
        for (item in queue) {
            when ((item as? Item.Ev)?.e) {
                is GameEvent.Caught -> return true
                is GameEvent.Defeated, is GameEvent.Counted, is GameEvent.Encounter, is GameEvent.MainHealed, null -> return false
                else -> {}
            }
        }
        return false
    }

    /** Looks ahead in the queue: does the Caught being played come with a swap (auto-release duplicates)? */
    private fun upcomingSwap(): GameEvent.Duplicate? {
        for (item in queue) {
            when (val ev = (item as? Item.Ev)?.e) {
                is GameEvent.Duplicate -> return ev.takeIf { it.result == DuplicateResult.SWAPPED }
                is GameEvent.Counted, is GameEvent.Encounter, null -> return null
                else -> {}
            }
        }
        return null
    }

    /** R9-W6 CX-A: looks ahead in the queue: the player's XpGained that belongs to the Caught being played (null = no XP for the player). */
    private fun upcomingPlayerXp(): GameEvent.XpGained? = CatchXp.find(queue.asSequence().map { (it as? Item.Ev)?.e }.asIterable(), arena.playerUid)
    private val ctx get() = panel.context

    /** True while game sounds must stay quiet (the study screen sets this when the card has audio). */
    var muted: () -> Boolean = { false }

    init {
        GameSound.warm(panel.context)
        panel.hostIdle = { idle }   // R9-W5-A: the panel follows changes of the saved game made elsewhere only while nothing plays
        panel.onPendingFight = { playPending() }   // PLAN 191: fight events made on another screen play here when the panel is on screen and idle
        if (continueOnPanel) panel.onContinue = { continueBattle() }   // the Continue tap under the held tiles (screens whose answers come from AnkiDroid)
        // a held next wild one let go by a window change (B7) plays its entrance now; nothing parked: drawn from the save
        panel.onHeldReleased = { val e = parked; if (e != null) { parked = null; queue.addFirst(Item.Ev(e)); pump() } else if (idle) panel.renderAll() }
    }

    /** Battle sound / cry of [e] (never throws, never waits). A wild one that is about to be caught does not "faint" first. */
    private fun sound(e: GameEvent, delayMs: Long? = null) {
        if (e is GameEvent.Fainted && !e.player && willBeCaught()) return
        GameSound.onEvent(panel.context, e, muted(), arena.ballShown, factor, delayMs)
    }
    /** A battle line: across the bar when it is about the whole battle; a move or a line about one side is on the bar / the arena already, so it is only spoken. */
    private fun log(kind: LogKind, pill: String, text: String) {
        if (logWhole) panel.setLog(kind, pill, text) else panel.sayBattle(text)
    }

    private fun label(b: Int) = when (b) { 1 -> "AGAIN"; 2 -> "HARD"; 3 -> "GOOD"; 4 -> "EASY"; else -> "OK" }
    private fun kind(b: Int) = when (b) { 1 -> LogKind.AGAIN; 2 -> LogKind.HARD; 3 -> LogKind.GOOD; 4 -> LogKind.EASY; else -> LogKind.GOOD }

    /** "Wild Psyduck" whether or not the game already put "Wild" in front. */
    private fun wild(name: String) = if (name.startsWith("Wild ")) name else "Wild $name"
    private fun bare(name: String) = name.removePrefix("Wild ")

    private fun moveName(id: String): String = GameRepo.data?.move(id)?.name ?: id.replaceFirstChar { it.uppercase() }

    private fun maxHpOf(uid: String): Int {
        val data = GameRepo.data ?: return 1
        val mon = GameRepo.state?.mon(uid) ?: return 1
        return if (data.species[mon.species] != null) StatCalc.maxHp(data, mon) else max(mon.hp, 1)
    }

    private fun aOrAn(s: String) = if (s.firstOrNull()?.lowercaseChar()?.let { it in "aeiou" } == true) "an $s" else "a $s"

    private fun times(m: Double): String {
        val whole = m == m.toInt().toDouble()
        return "×" + if (whole) m.toInt().toString() else String.format(Locale.US, "%.2f", m).trimEnd('0').trimEnd('.')
    }

    // ---------------------------------------------------------------- one event

    /** Starts the visuals of [e]; returns how long (ms) to wait before the next event. */
    private fun play(e: GameEvent): Long {
        if (e is GameEvent.Encounter && panel.holdsNextFight && !answerWaiting()) { parked = e; return 0 }   // under the result tiles: the next answer starts it
        val st = GameRepo.state
        val data = GameRepo.data
        panel.observeStudyEvent(e)
        logWhole = ActionBarModel.scope(e) == LineScope.WHOLE
        if (e !is GameEvent.Attack && e !is GameEvent.Encounter) sound(e)   // an attack sounds from [playAttack]: with its animation, when the hit lands; a new wild one's cry when it comes in
        when (e) {
            is GameEvent.Counted -> {
                if (e.neutral) {   // PLAN 155: the OK of a new card: counted, but no charge, no float, nobody attacks; the combo stays as it was
                    panel.setGoal(e.reviewsToday, st?.let { DailyGoal.today(it) } ?: 100, true)
                    panel.setCombo(e.cardStreak, true)
                    log(LogKind.NEUTRAL, "OK", com.husarp.ankigo.study.NewCardOk.LOG_LINE)
                    return d(450)
                }
                lastButton = e.button
                if (!e.catchUp) floats(e)   // an Again answer: "Hesitated"
                panel.setGoal(e.reviewsToday, st?.let { DailyGoal.today(it) } ?: 100, true)
                panel.setCombo(e.cardStreak, true)
                return 0
            }
            is GameEvent.Ignored -> {
                log(LogKind.NEUTRAL, "UNDO", "Redo after undo — not counted")
                return d(700)
            }
            is GameEvent.Charge -> {
                log(kind(lastButton), label(lastButton), "charging ${e.answers}/${e.of} — the next answer attacks")
                return d(450)
            }
            is GameEvent.RoundStart -> { mult = e.multiplier; wildMult = e.enemyPower; return 0 }

            is GameEvent.Attack -> return playAttack(e)
            is GameEvent.Splash -> {
                log(LogKind.NEUTRAL, "WILD", "${wild(e.name)} is watching.")
                return d(400)
            }
            is GameEvent.Effect -> {
                log(LogKind.SYS, "EFFECT", e.text)
                floats(e)
                if (e.onPlayer) arena.setPlayerStatus(e.status) else arena.setEnemyStatus(e.status)
                if (e.maxHp > 0) {
                    if (e.onPlayer) arena.setPlayerHp(e.hp, e.maxHp, true) else arena.setEnemyHp(e.hp, e.maxHp, true)
                }
                return d(550)
            }
            is GameEvent.Fainted -> {
                if (e.player) {
                    arena.faint(true, d(500))
                    log(LogKind.AGAIN, "FAINT", "${em(e.name)} fainted!")
                    return d(800)
                }
                // A caught one goes into the ball instead of sinking (the Caught event does that).
                if (willBeCaught()) return 0
                arena.faint(false, d(500))
                return d(550)
            }
            is GameEvent.BallThrow -> return playBallThrow(e)
            is GameEvent.Caught -> return playCaught(e)
            is GameEvent.Defeated -> {
                log(LogKind.GOOD, "WIN", "Wild ${em(bare(e.name))} fainted! +${e.xp} XP")
                return d(650)
            }
            is GameEvent.XpGained -> {
                xpRun?.let { run ->   // R9-W6 CX-A: its burst played inside the Caught step already; only what is left of it is waited for
                    if (run.ev === e) { xpRun = null; return CatchXp.remaining(run.doneAt, SystemClock.uptimeMillis()) }
                }
                if (e.uid != arena.playerUid) { teamXpCard(e); return 0 }
                return startXp(e, fromBall = false)
            }
            is GameEvent.LevelUp -> {
                if (e.uid == arena.playerUid) {
                    if (e.level > arena.playerLevel) arena.setPlayerLevel(e.level)   // PLAN 138: the XP bar's loop already changed it, level by level
                    arena.setPlayerHp(e.hp, e.maxHp, true)
                }
                log(LogKind.SYS, "LEVEL UP", "${em(e.name)} grew to Lv ${em(e.level.toString())}!")
                return d(1000)
            }
            is GameEvent.MoveLearned -> {
                val forgot = e.forgot?.let { " It forgot ${moveName(it)}." } ?: ""
                log(LogKind.SYS, "MOVE", "${em(e.name)} learned ${em(moveName(e.move))}!$forgot")
                return d(900)
            }
            is GameEvent.MoveSkipped -> {
                log(LogKind.SYS, "MOVE", "${em(e.name)} kept its moves and skipped ${em(moveName(e.move))}.")
                return d(800)
            }
            is GameEvent.Evolved -> return playEvolved(e)
            is GameEvent.MainHealed -> {
                log(LogKind.AGAIN, "FAINT", "${em(e.name)} fainted… it's back at full HP. The wild one ran off.")
                arena.setPlayerStatus(null)
                arena.setEnemyStatus(null)
                arena.enemyLeave(d(450))
                if (e.uid == arena.playerUid) {
                    arena.enter(true, d(450))
                    if (GameRepo.state?.mon(e.uid)?.shiny == true) arena.shinyBurst(true, d(ShinyBurst.DELAY_MS))
                    arena.setPlayerHp(e.hp, maxHpOf(e.uid), true)
                }
                return d(1100)
            }
            is GameEvent.Healed -> {
                if (e.uid != arena.playerUid) return 0
                floats(e)
                arena.setPlayerHp(e.hp, e.maxHp, true)
                log(LogKind.GOOD, "HEAL", "${em(e.name)} healed ${em("+${e.amount}")} HP")
                return d(500)
            }
            is GameEvent.Encounter -> return playEncounter(e)
            is GameEvent.Fled -> {
                log(LogKind.NEUTRAL, "FLED", "${em(bare(e.name))} ran away.")
                arena.enemyLeave(d(450))
                return d(700)
            }
            is GameEvent.MainChanged -> {
                log(LogKind.SYS, "SWITCH", "Go, ${em(e.name)}!")
                panel.refreshPlayer(e.uid)   // the one this event brings in, not the save's main: fight events made elsewhere can hold several switches (PLAN 191)
                arena.setPlayerStatus(null)   // a switch starts clean (the battle side is reset): a status comes with its own Effect event
                // R10 W10: a free hit follows: the saved game already has the HP after it, the bar shows the HP before until the hit lands
                val hit = (queue.firstOrNull() as? Item.Ev)?.e as? GameEvent.SwitchHit
                if (hit != null) arena.setPlayerHp(hit.hp, hit.maxHp, false)
                arena.enter(true, d(450))
                if (GameRepo.state?.mon(e.uid)?.shiny == true) arena.shinyBurst(true, d(ShinyBurst.DELAY_MS))
                return d(800)
            }
            is GameEvent.SwitchQueued -> return 0   // PLAN 206: nothing to animate; the action bar (observeStudyEvent above) shows "Switching after this card…" or drops it
            is GameEvent.SwitchHit -> {
                wildMult = Tuning.SWITCH_HIT_POWER   // the turn's attack is at normal power: no "×0" left over from a round where the wild one stayed quiet
                if (e.item != null) {
                    // R13 Part C: an item used on the main in a fight took the turn: the bar shows the HP after the item, then the attack lands
                    arena.setPlayerHp(e.hp, e.maxHp, true)
                    log(LogKind.AGAIN, "WILD", "Used ${em(Items.name(e.item))} on ${em(e.monName)} — ${wild(e.wildName)} attacks once!")
                } else {
                    log(LogKind.AGAIN, "WILD", "Switched to ${em(e.monName)} — ${wild(e.wildName)} attacks once!")
                }
                return d(500)
            }
            is GameEvent.Cash -> {
                panel.setCoins(e.total, true)
                panel.floatAtCoins("+${e.amount} ₽")
                return 0
            }
            is GameEvent.DailyGift -> { // R14-B2-D: the goal moment's gift (it replaced the money bonus): its own log line, the Bag count may have changed
                log(LogKind.SYS, "GOAL", DailyGift.logText(DailyGift.name(data, e.item, e.tm, e.count), e.streakDays))
                if (st != null) panel.setBalls(BattlePanelView.ballCount(st), true)
                return d(900)
            }
            is GameEvent.ItemFound -> {
                log(LogKind.SYS, "ITEM", "Found ${em(aOrAn(Items.name(e.item)))}!")
                if (st != null) panel.setBalls(BattlePanelView.ballCount(st), true)
                return d(900)
            }
            is GameEvent.BadgeEarned -> {
                log(LogKind.SYS, "BADGE", "Badge: ${em(e.title)}")
                return d(900)
            }
            is GameEvent.AchievementReached -> {
                log(LogKind.SYS, "ACHIEVE", "Achievement: ${em(e.title)} — ${e.tierName}")
                return d(900)
            }
            is GameEvent.TrainerLevelUp -> {
                log(LogKind.SYS, "TRAINER", "Trainer level ${em(e.level.toString())}!")
                return d(900)
            }
            is GameEvent.DayChanged -> {
                log(LogKind.SYS, "NEW DAY", "A new day begins: the daily goal starts again.")
                if (st != null) panel.renderFooter(st)
                return d(900)
            }
            is GameEvent.Duplicate -> return playDuplicate(e)
            is GameEvent.Info -> {
                if (arena.ballShown && e.text.endsWith("broke free!")) return breakFree(e.text)
                log(LogKind.SYS, if (e.text.startsWith("Daily goal")) "GOAL" else "INFO", e.text)
                return d(800)
            }
        }
    }

    /**
     * XP animation (PLAN 38) for the player's [e]: sparks fly into our Pokémon and the card's bar grows with them (0 = off, as before).
     * R9-C8: the bar is driven by the effect's own clock, one step per landing; R9-C3: a soft pling per picked landing. [fromBall]
     * (R9-W6 CX-A, from the Caught step): the orbs leave the catch ball. Returns how long until the next event may start.
     */
    private fun startXp(e: GameEvent.XpGained, fromBall: Boolean): Long {
        val frac = if (e.xpForLevel > 0) e.xpInLevel.toFloat() / e.xpForLevel else 1f
        val levelUp = e.crossedNeeds.isNotEmpty() || e.level > arena.playerLevel || frac < arena.xpShown - 0.001f
        // PLAN 138: levels this gain grew (the event knows; an old-style event: the gap to the level shown, at least one)
        val crossed = if (!levelUp) 0 else if (e.crossedNeeds.isNotEmpty()) e.crossedNeeds.size else max(1, e.level - arena.playerLevel)
        val needs = XpLoop.needs(crossed, e.crossedNeeds, e.xpForLevel)
        val animated = AppPrefs.xpAnimation(ctx)
        // a level-up bar first runs to the end of the OLD level (its own XP for the label)
        val fill = if (animated) XpFill(arena.xpShown, e.xpInLevel, needs[0], levelUp) else null
        // not animated: xp 0 starts no orbs but still clears the previous plan (catchWithXp reads arena.xpPlan as THIS call's plan)
        val lead = arena.xpAbsorb(if (animated) e.xp else 0, factor.toFloat(), fill, { rate, last -> GameSound.xpOrb(ctx, rate, last, muted()) }, fromBall)
        val lastLand = if (lead > 0L) arena.xpLastLandingMs else 0L
        if (levelUp) {
            if (!animated) {   // XP animation off: the end state at once (the LevelUp event changes the level number)
                arena.setPlayerXp(e.xpInLevel, e.xpForLevel, false)
                return d(300)
            }
            // fill to the end (the orbs do it; without orbs the bar slides there), then per level: hold, level number, empty, slide on
            val segs = XpLoop.segments(e.level - crossed, arena.xpShown, e.level, frac)
            var t = lastLand
            if (lead <= 0L) {
                val ms = d(XpLoop.runMs(segs[0].length))
                atMs(lead) { arena.setPlayerXp(needs[0], needs[0], true, ms) }
                t = lead + ms
            }
            for (i in 1 until segs.size) {
                val seg = segs[i]
                val need = needs[i]
                val cur = if (i == segs.lastIndex) e.xpInLevel else if (seg.toEnd) need else (seg.to * need).toInt()
                val ms = if (seg.length <= 0f) 0L else d(XpLoop.runMs(seg.length))   // (d is never 0)
                t += d(XpLoop.HOLD_MS)
                atMs(t) {
                    arena.setPlayerLevel(seg.level)
                    arena.setPlayerXp(0, need, false)
                    if (ms > 0L) arena.setPlayerXp(cur, need, true, ms) else arena.setPlayerXp(cur, need, false)
                }
                t += ms
            }
            return t + d(300)
        }
        // with orbs the bar is already growing; the wait covers the last landing when it comes later than the old 600 ms
        if (lead > 0L) return max(lead + d(600), lastLand + d(150))
        atMs(lead) { arena.setPlayerXp(e.xpInLevel, e.xpForLevel, true) }
        return lead + d(600)
    }

    /** The floating texts of [e] (damage, Miss, effectiveness, status words, stat badges, +HP, Hesitated), via the pure mapper. */
    private fun floats(e: GameEvent) = FloatTexts.of(e, FloatContext(Tuning.CRITS)).forEach { arena.floatText(it) }

    /**
     * Another team member's XP: a card slides in between the two Pokémon (pick 9A). The level-up / evolve events that belong to
     * this gain are still waiting in the queue, so the card is built from this event plus the ones after it up to the next answer.
     */
    private fun teamXpCard(e: GameEvent.XpGained) {
        val batch = ArrayList<GameEvent>()
        batch += e
        for (item in queue) {
            val ev = (item as? Item.Ev)?.e ?: break
            if (ev is GameEvent.Counted || ev is GameEvent.Encounter) break
            batch += ev
        }
        val card = TeamXpCards.of(batch, arena.playerUid).firstOrNull { it.uid == e.uid } ?: return
        arena.showXpCard(card) { view ->
            val data = GameRepo.data
            val mon = GameRepo.state?.mon(card.uid)
            val sp = if (mon != null && data != null) mon.lookSpecies(data) else null   // owned: its chosen look
            if (sp != null && mon != null) PanelSprites.show(ctx, view, sp, SpriteKind.FRONT, mon.shiny, false)
        }
    }

    /** Auto-release duplicates: one log line says what happened to the wild one (a swap also shows on the "Gotcha!" banner). */
    private fun playDuplicate(e: GameEvent.Duplicate): Long {
        val who = em(bare(e.name))
        when (e.result) {
            DuplicateResult.DEFEATED -> log(LogKind.SYS, "DUPE", "Not higher than your Lv ${e.ownedLevel} copy: $who was defeated, not caught.")
            DuplicateResult.SWAPPED -> log(LogKind.SYS, "SWAP", "Swapped for Lv ${e.level}: your Lv ${e.ownedLevel} ${em(e.releasedName ?: bare(e.name))} was released.")
            DuplicateResult.KEPT_BOTH -> log(LogKind.SYS, "DUPE", "Kept both $who: your Lv ${e.ownedLevel} copy is ${e.keptBecause ?: "kept"}.")
        }
        return d(700)
    }

    private fun playAttack(e: GameEvent.Attack): Long {
        val who = if (e.byPlayer) e.user else wild(e.user)
        val sb = StringBuilder("$who used ${em(e.move)}.")
        when {
            e.failed -> sb.append(" But it failed!")
            e.missed -> sb.append(" But it missed!")
            e.category != Category.STATUS && e.effectiveness == 0.0 -> sb.append(" It doesn't affect ${e.target}…")
            else -> {
                if (e.hits > 1) sb.append(" Hit ${e.hits} times!")
                if (e.category != Category.STATUS && e.damage > 0) {
                    if (e.effectiveness > 1.0) sb.append(" It's super effective!")
                    else if (e.effectiveness < 1.0) sb.append(" It's not very effective…")
                }
                if (e.crit) sb.append(" A critical hit!")
            }
        }
        if (e.byPlayer) {
            if (mult != 1.0 && !e.failed) sb.append("  ").append(times(mult))
            mult = 1.0
            log(kind(lastButton), label(lastButton), sb.toString())
        } else {
            if (wildMult != 1.0 && !e.failed) sb.append("  ").append(times(wildMult))
            wildMult = 1.0
            log(LogKind.AGAIN, "WILD", sb.toString())
        }
        // Round 7: the move's own animation (the setting is on, the move connects, the arena can show it); it has its own lunge and hit
        val run = if (AppPrefs.attackAnimations(ctx) && !e.missed && !e.failed) {
            val plan = MoveFxCatalog.plan(e.moveId, e.moveType, e.category, e.power)
            arena.playMove(plan, e.byPlayer, react = e.damage > 0, speed = (1.0 / factor).toFloat())
        } else null
        if (run == null) {
            sound(e)
            arena.lunge(e.byPlayer, d(350))
            after(300) { afterHit(e) }
            return d(800)
        }
        sound(e, run.impactMs)
        atMs(run.impactMs) { afterHit(e, animated = true) }
        return ArenaMoveStage.Geo.waitMs(run.doneMs, run.impactMs, factor)
    }

    /** The hit has landed: the target's shake and flash (plain path only), the floating numbers and the HP bars. */
    private fun afterHit(e: GameEvent.Attack, animated: Boolean = false) {
        val onPlayer = !e.byPlayer
        if (!animated && !e.missed && !e.failed && e.damage > 0) {
            arena.hit(onPlayer, d(380))
        }
        floats(e)
        // HP of the target, and of the user (drain, recoil, heal)
        if (e.byPlayer) {
            if (e.targetMaxHp > 0) arena.setEnemyHp(e.targetHp, e.targetMaxHp, true)
            if (e.userMaxHp > 0) arena.setPlayerHp(e.userHp, e.userMaxHp, true)   // drain / heal float from their Effect event
        } else {
            if (e.targetMaxHp > 0) arena.setPlayerHp(e.targetHp, e.targetMaxHp, true)
            if (e.userMaxHp > 0) arena.setEnemyHp(e.userHp, e.userMaxHp, true)
        }
    }

    /**
     * R9 C4: the order of a new fight is: the background changes first (its cross-fade), then the wild one comes in, already at its
     * right stand point. The wild side stays out while the background loads (its picture was asked for early, [enqueue], so it is
     * usually in memory), the wild one enters when the picture is applied and has cross-faded ([NextFight.EntranceGate]); a picture
     * that is not there after [NextFight.PICTURE_WAIT_MS] does not hold the fight up (the wild one comes in and may move once,
     * as it did before). Reduced motion never gets here ([skipAll] draws from the saved game, which waits the same way).
     */
    private fun playEncounter(e: GameEvent.Encounter): Long {
        val sp = GameRepo.data?.species?.get(e.species)
        val my = epoch
        val gate = NextFight.EntranceGate()
        var entered = false
        var inline = true
        panel.cancelEnemyReveal()
        arena.resetEnemySide()
        arena.setEnemyShown(false)
        arena.setEnemyStatus(null)
        arena.setPlayerStatus(null)
        arena.setBackdrop(sp?.types.orEmpty(), BackdropCatalog.seed(e.wildUid), BackdropCatalog.isLegendary(sp))   // R8-03: one of the pictures for this wild Pokémon's type, random per encounter
        arena.setEnemyInfo(bare(e.name), e.level, e.shiny, sp?.types.orEmpty())
        arena.setEnemyHp(e.hp, e.maxHp, false)
        arena.setNewTag(e.newSpecies)
        if (sp != null) {
            PanelSprites.show(ctx, arena.enemySprite, sp, SpriteKind.FRONT, e.shiny, false)
            panel.noteEnemySprite(e.species, e.shiny)
        }
        fun enter() {
            if (my != epoch || !gate.enter()) return
            entered = true
            arena.setEnemyShown(true)
            arena.setEnemyCardShown(true)
            arena.enter(false, d(450))
            if (e.shiny) arena.shinyBurst(false, d(ShinyBurst.DELAY_MS))
            sound(e)
            log(LogKind.SYS, "NEW", "A wild ${em(bare(e.name))}" + (if (e.shiny) " ${em("(shiny!)")}" else "") + " appeared!")
            GameRepo.state?.let { s -> GameRepo.data?.let { panel.prefetch(s, it) } }
            if (!inline) resume(d(950))
        }
        fun open() {   // the picture is there (or the wait ran out): in after its cross-fade
            if (my != epoch || !gate.open()) return
            val fade = arena.backdropFadeMs()
            if (fade > 0L) atMs(fade) { enter() } else enter()
        }
        arena.whenBackdropReady(NextFight.PICTURE_WAIT_MS) { open() }
        if (entered) return d(950)
        inline = false
        atMs(NextFight.PICTURE_WAIT_MS + 300L) { open() }   // the arena's own cap needs a window; this one never hangs the player
        return PENDING
    }

    /** Optional ball tap: the wild one goes into the ball, wobbles, then breaks free or stays (Caught follows). */
    private fun playBallThrow(e: GameEvent.BallThrow): Long {
        log(LogKind.SYS, "CATCH", "Throw: ${em(Items.name(e.ball))} · ${e.chance}%…")
        GameRepo.state?.let { panel.setBalls(BattlePanelView.ballCount(it), true) }
        arena.setEnemyCardShown(false)
        arena.enemyIntoBall(d(350))
        arena.dropBall(e.ball, d(450))
        after(450) { arena.wobbleBall(d(1800)) }
        // A failed throw is followed by an Info "<name> broke free!" (see [breakFree]); a success by Caught.
        return d(2250)
    }

    /** The ball broke: the wild one pops back out. */
    private fun breakFree(text: String): Long {
        log(LogKind.AGAIN, "MISS", "Oh no! $text")
        arena.clearBall()
        arena.enemyOutOfBall(d(350))
        arena.setEnemyCardShown(true)
        return d(900)
    }

    /** Mockup frame B: ball drops (450 ms) and wobbles (1.8 s), stars twinkle, "Gotcha!" banner. */
    private fun playCaught(e: GameEvent.Caught): Long {
        val hadBall = arena.ballShown
        val swap = upcomingSwap()
        val xpEv = upcomingPlayerXp()
        var t = 0L
        arena.setEnemyCardShown(false)
        if (!hadBall) {
            log(LogKind.SYS, "CATCH", "Catching ${em(bare(e.name))}…")
            arena.enemyIntoBall(d(350))
            arena.dropBall(e.ball ?: CatchBall.forAuto(GameRepo.data?.species?.get(e.species)), d(450)) // PLAN 205: an auto-catch shows a ball by rarity (visual only)
            after(450) { arena.wobbleBall(d(1800)) }
            t = 2250
        }
        if (xpEv != null) { catchWithXp(e, xpEv, swap, t); return PENDING }
        after(t) {
            showGotcha(e, swap, null)
        }
        after(t + 1600) { endCatch() }
        return d(t + 1650)
    }

    /** The click: "Gotcha!" + stars + the log line ([xp] = the player's XP in the line, CX-A). */
    private fun showGotcha(e: GameEvent.Caught, swap: GameEvent.Duplicate?, xp: Int?, numberAbove: Boolean = false) {
        val line2 = swap?.let { "Swapped for Lv ${it.level}" } ?: if (e.newSpecies) "New in your Pokédex" else null
        arena.showBanner("Gotcha!", "${bare(e.name)} was caught", line2, numberAbove)
        arena.showStars(true)
        val where = if (e.toTeam) "" else " It went to the box."
        log(LogKind.SYS, "GOTCHA", "${em(bare(e.name))} was caught!" + (if (xp != null) " +$xp XP" else "") + (if (e.newSpecies) " New in your Pokédex!" else "") + where)
        GameRepo.state?.let { s -> panel.setBalls(BattlePanelView.ballCount(s), true) }
    }

    private fun endCatch() {
        arena.hideBanner()
        arena.showStars(false)
        arena.clearBall()
        arena.setEnemyShown(false)
    }

    /**
     * R9-W6 CX-A (item 5): the catch gives the player's XP ([xpEv], found by looking ahead, whatever sits between Caught and it in the
     * queue: catch badges, a swap, a heal, team XP). At the click (after [t] ms) "Gotcha!" + stars and the XP burst start TOGETHER: the
     * orbs leave the ball (glowing while they go) and fly to our Pokémon, the ball fades after the last launch, banner and stars stay
     * until just after the last landing. The XpGained step later only waits for what is left ([xpRun]); no second burst, no second fill.
     * Without orbs (XP animation off, no window yet) the bar fills at the click and the banner stays as it always did.
     */
    private fun catchWithXp(e: GameEvent.Caught, xpEv: GameEvent.XpGained, swap: GameEvent.Duplicate?, t: Long) {
        after(t) {
            val wait = startXp(xpEv, fromBall = true)
            xpRun = XpRun(xpEv, SystemClock.uptimeMillis() + wait)
            val plan = arena.xpPlan
            showGotcha(e, swap, xpEv.xp, numberAbove = plan != null)
            if (plan == null) {   // no orbs flew: the old timing for the banner (the bar was filled at the click)
                after(1600) { endCatch() }
                resume(d(1650))
                return@after
            }
            atMs(plan.ballFadeFrom.toLong()) { arena.fadeBall(plan.ballFadeMs.toLong()) }
            atMs(plan.bannerFadeFrom.toLong()) { arena.fadeBanner(plan.bannerEnd.toLong() - plan.bannerFadeFrom.toLong()) }
            atMs(plan.bannerEnd.toLong()) { endCatch() }
            resume(plan.bannerEnd.toLong() + d(50))
        }
    }

    /** Sprite flashes white three times, swaps to the new back sprite at the middle flash. */
    private fun playEvolved(e: GameEvent.Evolved): Long {
        log(LogKind.SYS, "EVOLVE", "What? ${em(e.from)} is evolving…")
        if (e.uid != arena.playerUid) {
            after(500) { log(LogKind.SYS, "EVOLVE", "${em(e.from)} evolved into ${em(e.to)}!") }
            return d(1400)
        }
        arena.flashPlayer(d(900), 3)
        after(450) {
            val data = GameRepo.data
            val mon = GameRepo.state?.mon(e.uid)
            // the new form, unless the Pokémon keeps a chosen look (still one of its line)
            val sp = if (mon != null && data != null) mon.lookSpecies(data) else data?.species?.get(e.toSpecies)
            if (sp != null && mon != null) {
                PanelSprites.showMain(ctx, arena.playerSprite, sp, mon.shiny)
                panel.notePlayerSprite(sp.num, mon.shiny)
                arena.setPlayerName(e.to)
                arena.setPlayerTypes(sp.types)   // the type look follows the new form (Round 10 W7)
                arena.setPlayerHp(mon.hp, maxHpOf(e.uid), true)
            }
        }
        after(900) { log(LogKind.SYS, "EVOLVE", "${em(e.from)} evolved into ${em(e.to)}!") }
        return d(1900)
    }

    private companion object {
        const val PENDING = -1L // an event that is not over until it says so ([resume])
        const val SLOW_AT = 4   // answers waiting: halve the durations
        const val SKIP_AT = 10  // answers waiting: skip the animations
    }
}
