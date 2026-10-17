package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.CatchSummary
import com.husarp.ankigo.ui.LogKind
import org.junit.Rule
import org.junit.Test

/**
 * R4-V7 (PLAN 40, pick 8A): the catch result tiles under the arena, and the "Gotcha!" banner in the comic style.
 * PNGs: r4v7-newcatch (new species, 2 throws, XP with a level-up, coins), r4v7-recatch, r4v7-auto, r4v7-defeat at PHONE / SPLIT /
 * TABLET (big or one-line layout by the panel's height); r4v7-small-* at FLOATBATTLE / FLOATB / PIP (one line, timed);
 * r4v7-strip-* the study strip; r4v7-gotcha the banner above the ball (see also [R406Shots]).
 */
class R4V7Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun xp(uid: String, name: String, n: Int, level: Int) = GameEvent.XpGained(uid, name, n, level, 0, 100)
    private fun caught(newSpecies: Boolean, ball: String?) = GameEvent.Caught("w1", 147, "Sparkmouse", 12, false, ball, newSpecies, true)

    private val newCatch get() = CatchSummary.of(
        listOf(
            GameEvent.BallThrow("great-ball", 12, true, 3), caught(true, "great-ball"), xp("m", "Puddlet", 120, 8),
            GameEvent.LevelUp("m", "Puddlet", 8, 30, 30), GameEvent.Cash(10, 1240, CashKind.DEFEAT),
        ),
        earlierThrows = 1,
    )!!
    private val reCatch get() = CatchSummary.of(
        listOf(GameEvent.BallThrow("poke-ball", 40, true, 5), caught(false, "poke-ball"), xp("m", "Puddlet", 45, 7), GameEvent.Cash(10, 1240, CashKind.DEFEAT)),
    )!!
    private val autoCatch get() = CatchSummary.of(
        listOf(caught(true, null), xp("m", "Puddlet", 80, 7), xp("b", "Embercub", 40, 24), xp("c", "Leafy", 40, 9), GameEvent.Cash(10, 1240, CashKind.DEFEAT)),
    )!!
    private val defeat get() = CatchSummary.of(
        listOf(GameEvent.Defeated(147, "Sparkmouse", 84), xp("m", "Puddlet", 84, 7), GameEvent.Cash(10, 1240, CashKind.DEFEAT)),
    )!!

    private fun panel(ctx: Context, s: CatchSummary, mode: String): View {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val p = BattlePanelView(ctx)
        when (mode) {
            "small" -> p.setCompact(true)
            "strip" -> p.setStudyLayout(true, false)
            "study" -> p.setStudyLayout(false, false)
        }
        p.renderAll()
        p.setLog(LogKind.SYS, "GOTCHA", "Sparkmouse was caught!", false)
        p.showCatchTiles(s, false)
        p.renderAll()
        return p
    }

    private val big = listOf(Size.PHONE, Size.SPLIT, Size.TABLET)
    private val small = listOf(Size.FLOATBATTLE, Size.FLOATB, Size.PIP)

    @Test fun newCatch() = paparazzi.shootWith("r4v7-newcatch", big, offsetMillis = 400) { panel(it, newCatch, "full") }
    @Test fun reCatch() = paparazzi.shootWith("r4v7-recatch", big, offsetMillis = 400) { panel(it, reCatch, "full") }
    @Test fun autoCatch() = paparazzi.shootWith("r4v7-auto", big, offsetMillis = 400) { panel(it, autoCatch, "full") }
    @Test fun defeat() = paparazzi.shootWith("r4v7-defeat", big, offsetMillis = 400) { panel(it, defeat, "full") }
    @Test fun smallNew() = paparazzi.shootWith("r4v7-small-new", small, offsetMillis = 400) { panel(it, newCatch, "small") }
    @Test fun smallDefeat() = paparazzi.shootWith("r4v7-small-defeat", small, offsetMillis = 400) { panel(it, defeat, "small") }
    @Test fun strip() = paparazzi.shootWith("r4v7-strip-new", listOf(Size.PIP_169M, Size.PIP_169), offsetMillis = 400) { panel(it, newCatch, "strip") }
    @Test fun study() = paparazzi.shootWith("r4v7-study-new", listOf(Size.SPLIT_SHORT), offsetMillis = 400) { panel(it, newCatch, "study") }

    @Test fun gotcha() = paparazzi.shootWith("r4v7-gotcha", listOf(Size.PHONE, Size.SPLIT, Size.PIP), offsetMillis = 400) { ctx ->
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val p = BattlePanelView(ctx)
        if (ctx.resources.configuration.screenHeightDp < 200) p.setCompact(true)
        p.renderAll()
        var done = false
        p.arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!done && v.width > 0 && v.height > 0) {
                done = true
                val a = p.arena
                a.setEnemyCardShown(false)
                a.enemySprite.scaleX = 0f; a.enemySprite.scaleY = 0f; a.enemySprite.alpha = 0f
                a.dropBall("poke-ball", 1)
                a.showStars(true)
                a.showBanner("Gotcha!", "Sparkmouse was caught", "New in your Pokédex")
                fun field(n: String) = ArenaView::class.java.getDeclaredField(n).also { it.isAccessible = true }
                val ball = field("ball").get(a) as View
                val banner = field("banner").get(a) as View
                val fit = field("bannerFit").getFloat(a)
                ball.animate().cancel(); ball.translationY = 0f
                banner.animate().cancel(); banner.alpha = 1f; banner.scaleX = fit; banner.scaleY = fit
                a.measure(View.MeasureSpec.makeMeasureSpec(a.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(a.height, View.MeasureSpec.EXACTLY))
                a.layout(a.left, a.top, a.right, a.bottom)
            }
        }
        p
    }
}
