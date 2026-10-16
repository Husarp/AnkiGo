package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.BattleLogBuilder
import com.husarp.ankigo.ui.BattleLogPage
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/** TICKET R2-07: the Battle log screen with made-up fights (light + dark, PHONE + SPLIT + TALL), and its empty state. */
class BattleLogShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private class ActivityHost(val activity: Activity) : PageHost {
        override val hostActivity: Activity get() = activity
        override fun closePage() = Unit
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
    }

    private var clock = 1_760_000_000_000L
    private fun counted(b: Int) = GameEvent.Counted(b, 5, 40, 3, false)
    private fun enc(name: String, lvl: Int) = GameEvent.Encounter(1, name, lvl, false, Tier.NORMAL, 36, 36, true, false)
    private fun atk(byPlayer: Boolean, user: String, target: String, move: String, cat: Category, dmg: Int, tHp: Int, tMax: Int, uHp: Int, uMax: Int,
                    eff: Double = 1.0, crit: Boolean = false, missed: Boolean = false) =
        GameEvent.Attack(byPlayer, user, target, move, Type.ELECTRIC, cat, dmg, 1, eff, crit, missed, false, tHp, tMax, uHp, uMax)

    /** Newest first: the current fight (a round collecting answers), a won one, a caught one with balls, a lost one. */
    private fun fights() = BattleLogBuilder(5, { clock += 40 * 60_000L; clock }).also { b ->
        // lost (oldest)
        b.feed(enc("Rocklet", 11))
        b.answer("study", 31, 1, "Capital of Australia?"); b.answer("study", 32, 2, "Boiling point of water in °F")
        b.feed(listOf(counted(1), GameEvent.Cash(3, 90), GameEvent.Charge(2, 1, 2), counted(2), GameEvent.RoundStart(0.45, 7, true),
            atk(true, "Sparkmouse", "Rocklet", "Zap", Category.SPECIAL, 0, 36, 36, 40, 40, eff = 0.0),
            atk(false, "Rocklet", "Sparkmouse", "Rock Throw", Category.PHYSICAL, 38, 2, 40, 36, 36, crit = true),
            GameEvent.Effect(true, "Sparkmouse is paralyzed!", 2, 40, "par")))
        b.answer("live", 33, 1, null)
        b.feed(listOf(counted(1), GameEvent.Charge(1, 1, 2)))
        b.answer("live", 34, 1, "Largest planet in the Solar System")
        b.feed(listOf(counted(1), GameEvent.RoundStart(0.2, 2, true),
            atk(false, "Rocklet", "Sparkmouse", "Rock Throw", Category.PHYSICAL, 9, 0, 40, 36, 36),
            GameEvent.Fainted(true, "Sparkmouse"), GameEvent.MainHealed("u1", "Sparkmouse", 40), enc("Zapbug", 12)))
        // caught, with two balls
        b.answer("study", 41, 3, "Author of Hamlet")
        b.feed(listOf(counted(3), GameEvent.Cash(5, 95), GameEvent.Charge(5, 1, 2)))
        b.answer("catch-up", 42, 4, null)
        b.feed(listOf(counted(4), GameEvent.RoundStart(1.25, 10, false), GameEvent.Splash("Zapbug")))
        b.feed(listOf(GameEvent.BallThrow("poke-ball", 40, false, 9)))
        b.feed(listOf(GameEvent.BallThrow("great-ball", 62, true, 3),
            GameEvent.Caught("c1", 3, "Zapbug", 12, false, "great-ball", true, true), enc("Wavelet", 13)))
        // won
        b.answer("study", 51, 3, "2 + 2 × 5"); b.answer("study", 52, 4, "Chemical symbol for gold")
        b.feed(listOf(counted(3), GameEvent.Cash(6, 101), GameEvent.Charge(5, 1, 2), counted(4), GameEvent.Cash(8, 109), GameEvent.RoundStart(1.1, 11, false),
            atk(true, "Sparkmouse", "Wavelet", "Zap", Category.SPECIAL, 36, 0, 36, 40, 40, eff = 2.0, crit = true),
            GameEvent.Fainted(false, "Wavelet"),
            GameEvent.Defeated(7, "Wavelet", 54), GameEvent.XpGained("u1", "Sparkmouse", 54, 9, 20, 120),
            GameEvent.LevelUp("u1", "Sparkmouse", 9, 44, 44), GameEvent.Evolved("u1", "Sparkmouse", "Sparkhound", 9), enc("Pebbly", 10)))
        // current: one round fought, a second collecting
        b.answer("study", 61, 3, "Square root of 144"); b.answer("study", 62, 2, null)
        b.feed(listOf(counted(3), GameEvent.Cash(4, 113), GameEvent.Charge(5, 1, 2), counted(2), GameEvent.RoundStart(0.8, 8, true),
            atk(true, "Sparkhound", "Pebbly", "Zap", Category.SPECIAL, 12, 24, 36, 44, 44),
            atk(false, "Pebbly", "Sparkhound", "Tackle", Category.PHYSICAL, 0, 44, 44, 24, 36, missed = true),
            GameEvent.Effect(false, "Pebbly is burned!", 21, 36, "brn")))
        b.answer("study", 63, 4, "Opposite of 'ancient'")
        b.feed(listOf(counted(4), GameEvent.Cash(5, 118), GameEvent.Charge(5, 1, 2)))
    }.newestFirst()

    private fun screen(ctx: Context, show: (BattleLogPage) -> Unit): View {
        val page = BattleLogPage(ctx, ActivityHost(attach(Activity(), ctx)))
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            addView(page.root, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        show(page) // no onShow(): that one reads the real store on a thread
        return col
    }

    @Test fun logWithFights() {
        paparazzi.shootPage("battlelog", listOf(Variants.PHONE, Variants.SPLIT, Variants.TALL)) { ctx -> screen(ctx) { it.show(fights()) } }
    }

    @Test fun emptyLog() {
        paparazzi.shootPage("battlelog-empty", listOf(Variants.PHONE, Variants.SPLIT)) { ctx -> screen(ctx) { it.show(emptyList()) } }
    }
}
