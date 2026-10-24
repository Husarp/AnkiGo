package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Answer
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.AppToast
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R18-B (PLAN 227): the Study bar around a switch. Real events of the real [com.husarp.ankigo.game.Game] (FakeGame's team and fight)
 * are played through the panel in order. PNGs r18b-*: the bar after a queued switch (normal segments, the toast on top), after it
 * came in on the first card of a round (mauve dot, "Switched to X") and on the card that ended the round (both dots, the last one
 * mauve), and after an item was used (dots as they were). Light + dark, split screen.
 */
class R18BSwitchShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    private fun atk(byPlayer: Boolean, move: String, type: Type, cat: Category, damage: Int) =
        GameEvent.Attack(byPlayer, if (byPlayer) "Pikachu" else "Gnarlpup", if (byPlayer) "Gnarlpup" else "Pikachu", move, type, cat, damage, 1, 1.0, false, false, false, 10, 30, 20, 30)

    /** The moves of the round before: both halves have something to show (no Encounter / RoundStart: they would reset the dots the save gives). */
    private val lastRound = listOf<GameEvent>(
        atk(true, "Thunderbolt", Type.ELECTRIC, Category.SPECIAL, 14),
        atk(false, "Bite", Type.DARK, Category.PHYSICAL, 9))

    private var toast: String? = null   // the message of the queue, shown over the panel like the app does

    private fun shot(name: String, setup: (Context, BattlePanelView) -> Unit) =
        paparazzi.shootWith("r18b-$name", listOf(Size.SPLIT)) { ctx: Context ->
            toast = null
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val p = BattlePanelView(ctx)
            p.setStudyLayout(false, false); p.renderAll()
            setup(ctx, p)
            FrameLayout(ctx).apply {
                addView(p as View, FrameLayout.LayoutParams(-1, -1))
                toast?.let { AppToast.show(this, it) }
            }
        }

    /** The events in order; with reduced motion the arena is then drawn from the saved game (the new main, its HP after the attack), as the app does. */
    private fun play(p: BattlePanelView, events: List<GameEvent>) { events.forEach { p.observeStudyEvent(it) }; p.renderAll() }

    /** One answer of a fight in the save, as the battle left it before the shown card: [answers] 1 = the first card of a round is answered. */
    private fun fightWith(answers: Int) {
        GameRepo.state!!.battle!!.let { b ->
            b.answers = answers; b.tally = if (answers == 1) 10 else 0; b.buttons.clear(); if (answers == 1) b.buttons += 3
            b.rounds = 1
        }
    }

    // ---------------------------------------------------------------- queued: the bar keeps its normal look

    @Test fun queuedFirstCard() = shot("queued-first") { ctx, p ->
        play(p, lastRound)
        fightWith(0)
        p.restoreStudyCharge()
        queue(ctx, p)
    }

    @Test fun queuedSecondCard() = shot("queued-second") { ctx, p ->
        play(p, lastRound)
        fightWith(1)
        p.restoreStudyCharge()
        queue(ctx, p)
    }

    private fun queue(ctx: Context, p: BattlePanelView) {
        val r = GameRepo.run(ctx) { it.requestMain("t2") }
        assertEquals("Switching to ${GameRepo.state!!.mon("t2")!!.name} on your next move.", r.message)
        toast = r.message
        play(p, r.events)
    }

    // ---------------------------------------------------------------- it came in with the answer

    @Test fun cameInOnTheFirstCard() = shot("came-in-first") { ctx, p ->
        play(p, lastRound)
        fightWith(0)
        p.restoreStudyCharge()
        GameRepo.run(ctx) { it.requestMain("t2") }
        val e = GameRepo.run(ctx) { it.onAnswer(Answer(91_001L, 3, System.currentTimeMillis())) }
        assertTrue(e.any { it is GameEvent.MainChanged && it.queued } && e.any { it is GameEvent.SwitchHit })
        play(p, e)
    }

    @Test fun cameInOnTheRoundEndingCard() = shot("came-in-round-end") { ctx, p ->
        play(p, lastRound)
        fightWith(1)
        p.restoreStudyCharge()
        GameRepo.run(ctx) { it.requestMain("t2") }
        val e = GameRepo.run(ctx) { it.onAnswer(Answer(91_002L, 1, System.currentTimeMillis())) }
        assertTrue(e.any { it is GameEvent.RoundStart } && e.any { it is GameEvent.MainChanged && it.queued })
        assertTrue("the wild one is still there", e.none { it is GameEvent.Encounter || it is GameEvent.Defeated || it is GameEvent.Caught })
        play(p, e)
    }

    // ---------------------------------------------------------------- an item: dots as they were

    @Test fun usedAnItem() = shot("used-item") { ctx, p ->
        play(p, lastRound)
        fightWith(1)
        p.restoreStudyCharge()
        GameRepo.state!!.main!!.let { it.hp = it.hp / 2 }
        val r = GameRepo.run(ctx) { it.useItem("potion", null) }
        assertTrue(r.message, r.ok)
        play(p, r.events)
    }
}
