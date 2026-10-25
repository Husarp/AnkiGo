package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.SwitchQueue
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.ToastKind
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * R19-T (PLAN 284 / 288): the turn dots with the grey OK dot, and the switch's card toast. Real [GameEvent]s are played through the panel
 * in order. PNGs r19t-*: OK first (one grey dot), OK + Good (grey + green, the Good round), OK + OK (two grey dots, an empty round), a
 * switch in the middle of a round (the dots as they were, no mauve one, the toast on top). Light + dark, split screen; the toast also on the phone.
 */
class R19TTurnsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    private fun atk(byPlayer: Boolean, move: String, type: Type, cat: Category, damage: Int) =
        GameEvent.Attack(byPlayer, if (byPlayer) "Pikachu" else "Gnarlpup", if (byPlayer) "Gnarlpup" else "Pikachu", move, type, cat, damage, 1, 1.0, false, false, false, 10, 30, 20, 30)

    private val thunderbolt = atk(true, "Thunderbolt", Type.ELECTRIC, Category.SPECIAL, 14)
    private val bite = atk(false, "Bite", Type.DARK, Category.PHYSICAL, 9)

    private fun counted(button: Int, neutral: Boolean = false) = GameEvent.Counted(button, if (button == 1) 0 else 10, 1, 1, false, neutral)

    private fun shot(name: String, sizes: List<Size> = listOf(Size.SPLIT), setup: (Context, BattlePanelView) -> Unit) =
        paparazzi.shootWith("r19t-$name", sizes) { ctx: Context ->
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val p = BattlePanelView(ctx)
            p.setStudyLayout(false, false); p.renderAll()
            GameRepo.state!!.battle!!.let { it.answers = 0; it.tally = 0; it.buttons.clear() }
            p.restoreStudyCharge()
            setup(ctx, p)
            p as View
        }

    private fun play(p: BattlePanelView, vararg events: GameEvent) { events.forEach { p.observeStudyEvent(it) }; p.renderAll() }

    @Test fun okFirstIsOneGreyDot() = shot("ok-first") { _, p ->
        play(p, counted(1, neutral = true), GameEvent.Charge(0, 1, 2))
    }

    @Test fun okPlusGoodIsAGoodRound() = shot("ok-good") { _, p ->
        play(p, counted(1, neutral = true), GameEvent.Charge(0, 1, 2), counted(3), GameEvent.RoundStart(0.5, 10, false, true, 0.0), thunderbolt)
    }

    @Test fun okPlusOkIsAnEmptyRound() = shot("ok-ok") { _, p ->
        play(p, counted(1, neutral = true), GameEvent.Charge(0, 1, 2), counted(1, neutral = true), GameEvent.RoundStart(0.0, 0, false, false, 0.0))
    }

    @Test fun goodThenOkKeepsOrderGreenGrey() = shot("good-ok") { _, p ->
        play(p, counted(3), GameEvent.Charge(10, 1, 2), counted(1, neutral = true), GameEvent.RoundStart(0.5, 10, false, true, 0.0), thunderbolt)
    }

    @Test fun aSwitchMidRoundKeepsTheDotsAndShowsTheToast() = shot("switch", listOf(Size.SPLIT, Size.PHONE)) { _, p ->
        play(p, thunderbolt, bite, counted(3), GameEvent.Charge(10, 1, 2),
            GameEvent.MainChanged("t2", "Ivysaurus", queued = true), GameEvent.SwitchHit("Wild Gnarlpup", "Ivysaurus", 40, 60), bite)
        p.toast(ToastKind.SWITCH, SwitchQueue.freeHitText("Gnarlpup"))   // shown when the shot lays the panel out (the unit test checks the kind and text)
    }
}
