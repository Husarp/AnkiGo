package com.husarp.ankigo.ui

import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.Report
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.ui.PanelText.em
import java.util.Locale
import kotlin.math.max

/**
 * Plays [GameEvent]s on the panel, one after the other: the next event starts when the previous animation is done.
 * The game state is already final when events arrive, so every visual comes from the numbers inside the events.
 * Pace: an answer's events take about a second; with 4 or more answers waiting the durations are halved, and with
 * 10 or more the animations are skipped and one summary line shows (then the panel is redrawn from the saved game).
 */
class EventPlayer(private val panel: BattlePanelView) {
    private sealed class Item {
        class Ev(val e: GameEvent) : Item()
        class Act(val block: () -> Unit) : Item()
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

    /** True when nothing is playing or waiting. */
    val idle: Boolean get() = !busy && queue.isEmpty()

    fun enqueue(events: List<GameEvent>) {
        if (events.isEmpty()) return
        events.forEach { queue.addLast(Item.Ev(it)) }
        pump()
    }

    /** Runs [block] in order with the events (e.g. a log line for a catch-up batch). */
    fun enqueueAction(block: () -> Unit) {
        queue.addLast(Item.Act(block))
        pump()
    }

    /** Drops everything waiting and stops the animations. The caller redraws from the state afterwards. */
    fun clear() {
        epoch++
        queue.clear()
        busy = false
        dirty = false
        handler.removeCallbacksAndMessages(null)
        panel.cancelAnimations()
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
            if (answers >= SKIP_AT) { skipAll(item); continue }
            factor = if (answers >= SLOW_AT) 0.5 else 1.0
            val ms = when (item) {
                is Item.Act -> {
                    dirty = true
                    try { item.block() } catch (t: Throwable) { Report.fail("PANEL", "queued action", t) }
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
            if (ms > 0) {
                busy = true
                val my = epoch
                handler.postDelayed({ if (my == epoch) { busy = false; pump() } }, ms)
                return
            }
        }
    }

    /** Too many answers waiting: no animations, one summary line, then the saved state. */
    private fun skipAll(first: Item) {
        val events = ArrayList<GameEvent>()
        val acts = ArrayList<() -> Unit>()
        fun take(i: Item) { if (i is Item.Ev) events += i.e else if (i is Item.Act) acts += i.block }
        take(first)
        while (queue.isNotEmpty()) take(queue.removeFirst())
        panel.cancelAnimations()
        panel.renderAll()
        acts.forEach { it() }
        if (events.isNotEmpty()) panel.setLog(LogKind.SYS, "FAST", summary(events))
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
    private val ctx get() = panel.context
    private fun log(kind: LogKind, pill: String, text: String) = panel.setLog(kind, pill, text)

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
        val st = GameRepo.state
        val data = GameRepo.data
        when (e) {
            is GameEvent.Counted -> {
                lastButton = e.button
                panel.setGoal(e.reviewsToday, st?.settings?.dailyAverage ?: 100, true)
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
            is GameEvent.RoundStart -> { mult = e.multiplier; return 0 }

            is GameEvent.Attack -> return playAttack(e)
            is GameEvent.Splash -> {
                log(LogKind.NEUTRAL, "WILD", "${wild(e.name)} is watching.")
                return d(400)
            }
            is GameEvent.Effect -> {
                log(LogKind.SYS, "EFFECT", e.text)
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
                if (e.uid != arena.playerUid) return 0
                val frac = if (e.xpForLevel > 0) e.xpInLevel.toFloat() / e.xpForLevel else 1f
                if (e.level > arena.playerLevel || frac < arena.xpShown - 0.001f) {
                    // grew a level: fill the bar, then restart at the new level's progress
                    arena.setPlayerXp(1f, true)
                    after(450) { arena.setPlayerXp(0f, false); arena.setPlayerXp(frac, true) }
                    return d(950)
                }
                arena.setPlayerXp(frac, true)
                return d(600)
            }
            is GameEvent.LevelUp -> {
                if (e.uid == arena.playerUid) {
                    arena.setPlayerLevel(e.level)
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
                arena.enemyLeave(d(450))
                if (e.uid == arena.playerUid) {
                    arena.enter(true, d(450))
                    arena.setPlayerHp(e.hp, maxHpOf(e.uid), true)
                }
                return d(1100)
            }
            is GameEvent.Encounter -> return playEncounter(e)
            is GameEvent.Fled -> {
                log(LogKind.NEUTRAL, "FLED", "${em(bare(e.name))} ran away.")
                arena.enemyLeave(d(450))
                return d(700)
            }
            is GameEvent.MainChanged -> {
                log(LogKind.SYS, "SWITCH", "Go, ${em(e.name)}!")
                panel.refreshPlayer()
                arena.enter(true, d(450))
                return d(800)
            }
            is GameEvent.Cash -> {
                panel.setCoins(e.total, true)
                panel.floatAtCoins("+${e.amount} ₽")
                return 0
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
            is GameEvent.TrainerLevelUp -> {
                log(LogKind.SYS, "TRAINER", "Trainer level ${em(e.level.toString())}!")
                return d(900)
            }
            is GameEvent.DayChanged -> {
                log(LogKind.SYS, "NEW DAY", "A new day begins: the daily goal starts again.")
                if (st != null) panel.renderFooter(st)
                return d(900)
            }
            is GameEvent.Info -> {
                if (arena.ballShown && e.text.endsWith("broke free!")) return breakFree(e.text)
                log(LogKind.SYS, if (e.text.startsWith("Daily goal")) "GOAL" else "INFO", e.text)
                return d(800)
            }
        }
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
            log(LogKind.AGAIN, "WILD", sb.toString())
        }
        arena.lunge(e.byPlayer, d(350))
        after(300) {
            val onPlayer = !e.byPlayer
            if (!e.missed && !e.failed && e.damage > 0) {
                arena.hit(onPlayer, d(380))
                val big = e.crit || e.effectiveness > 1.0
                arena.floatNumber(onPlayer, "-${e.damage}", if (big) FloatStyle.BIG else FloatStyle.DAMAGE)
            } else if (e.missed) {
                arena.floatNumber(onPlayer, "Miss", FloatStyle.MISS)
            }
            // HP of the target, and of the user (drain, recoil, heal)
            if (e.byPlayer) {
                if (e.targetMaxHp > 0) arena.setEnemyHp(e.targetHp, e.targetMaxHp, true)
                if (e.userMaxHp > 0) {
                    val gained = e.userHp - arena.playerHpShown
                    if (gained > 0 && arena.playerHpShown > 0) arena.floatNumber(true, "+$gained", FloatStyle.HEAL)
                    arena.setPlayerHp(e.userHp, e.userMaxHp, true)
                }
            } else {
                if (e.targetMaxHp > 0) arena.setPlayerHp(e.targetHp, e.targetMaxHp, true)
                if (e.userMaxHp > 0) arena.setEnemyHp(e.userHp, e.userMaxHp, true)
            }
        }
        return d(800)
    }

    private fun playEncounter(e: GameEvent.Encounter): Long {
        val sp = GameRepo.data?.species?.get(e.species)
        arena.resetEnemySide()
        arena.setEnemyShown(true)
        arena.setEnemyCardShown(true)
        arena.setEnemyInfo(bare(e.name), e.level, e.shiny, sp?.types.orEmpty())
        arena.setEnemyHp(e.hp, e.maxHp, false)
        arena.setCatchTag(e.willCatch, e.newSpecies)
        if (sp != null) {
            PanelSprites.show(ctx, arena.enemySprite, sp, SpriteKind.FRONT, e.shiny, false)
            panel.noteEnemySprite(e.species, e.shiny)
        }
        arena.enter(false, d(450))
        log(LogKind.SYS, "NEW", "A wild ${em(bare(e.name))}" + (if (e.shiny) " ${em("(shiny!)")}" else "") + " appeared!")
        GameRepo.state?.let { s -> GameRepo.data?.let { panel.prefetch(s, it) } }
        return d(950)
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
        var t = 0L
        arena.setEnemyCardShown(false)
        if (!hadBall) {
            log(LogKind.SYS, "CATCH", "Catching ${em(bare(e.name))}…")
            arena.enemyIntoBall(d(350))
            arena.dropBall(e.ball ?: "poke-ball", d(450))
            after(450) { arena.wobbleBall(d(1800)) }
            t = 2250
        }
        after(t) {
            val line2 = if (e.newSpecies) "New in your Pokédex" else null
            arena.showBanner("Gotcha!", "${bare(e.name)} was caught", line2)
            arena.showStars(true)
            val where = if (e.toTeam) "" else " It went to the box."
            log(LogKind.SYS, "GOTCHA", "${em(bare(e.name))} was caught!" + (if (e.newSpecies) " New in your Pokédex!" else "") + where)
            GameRepo.state?.let { s -> panel.setBalls(BattlePanelView.ballCount(s), true) }
        }
        after(t + 1600) {
            arena.hideBanner()
            arena.showStars(false)
            arena.clearBall()
            arena.setEnemyShown(false)
        }
        return d(t + 1650)
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
            val sp = data?.species?.get(e.toSpecies)
            if (sp != null && mon != null) {
                PanelSprites.show(ctx, arena.playerSprite, sp, SpriteKind.BACK, mon.shiny, true)
                panel.notePlayerSprite(sp.num, mon.shiny)
                arena.setPlayerName(e.to)
                arena.setPlayerHp(mon.hp, maxHpOf(e.uid), true)
            }
        }
        after(900) { log(LogKind.SYS, "EVOLVE", "${em(e.from)} evolved into ${em(e.to)}!") }
        return d(1900)
    }

    private companion object {
        const val SLOW_AT = 4   // answers waiting: halve the durations
        const val SKIP_AT = 10  // answers waiting: skip the animations
    }
}
