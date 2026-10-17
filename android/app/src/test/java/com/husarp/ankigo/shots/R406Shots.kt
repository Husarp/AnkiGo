package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.LogKind
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * R4-06 (PLAN 35): the catch moment with the "Gotcha!" banner above the ball / centred, off both Pokémon and both cards.
 * PNGs: r406-catch, r406-catch-swap (longest text) at PHONE / SPLIT / FLOATBATTLE / FLOATB / PIP, TABLET / TABLET_LAND, and the
 * study screen's arena + strip (r406-catch-study / r406-catch-strip). Reduced motion (see [R3V9Shots]).
 */
class R406Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun afterLayout(arena: ArenaView, fire: (ArenaView) -> Unit) {
        var done = false
        arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!done && v.width > 0 && v.height > 0) {
                done = true
                fire(arena)
                arena.measure(View.MeasureSpec.makeMeasureSpec(arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(arena.height, View.MeasureSpec.EXACTLY))
                arena.layout(arena.left, arena.top, arena.right, arena.bottom)
            }
        }
    }

    private fun catchMoment(line2: String?) = { a: ArenaView ->
        a.setEnemyCardShown(false)
        a.enemySprite.scaleX = 0f; a.enemySprite.scaleY = 0f; a.enemySprite.alpha = 0f
        a.dropBall("poke-ball", 1)
        a.showStars(true)
        a.showBanner("Gotcha!", "Sparkmouse was caught", line2)
        settle(a)
    }

    /** Paparazzi does not advance View animators: put the ball and the banner at their resting state by hand. */
    private fun settle(a: ArenaView) {
        fun field(n: String) = ArenaView::class.java.getDeclaredField(n).also { it.isAccessible = true }
        val ball = field("ball").get(a) as View
        val banner = field("banner").get(a) as View
        val fit = field("bannerFit").getFloat(a)
        ball.animate().cancel(); ball.translationY = 0f
        banner.animate().cancel(); banner.alpha = 1f; banner.scaleX = fit; banner.scaleY = fit
    }

    private fun small(ctx: Context, fire: (ArenaView) -> Unit): View {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val p = BattlePanelView(ctx)
        p.setCompact(true); p.renderAll()
        p.setLog(LogKind.SYS, "GOTCHA", "Sparkmouse was caught!", false)
        afterLayout(p.arena, fire)
        return p
    }

    private fun full(ctx: Context, fire: (ArenaView) -> Unit): View {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val b = BattleShotKit.bound(ctx)
        afterLayout(b.arena!!, fire)
        return b.root
    }

    private fun study(ctx: Context, strip: Boolean, fire: (ArenaView) -> Unit): View {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val p = BattlePanelView(ctx)
        p.setStudyLayout(strip, false); p.renderAll()
        p.setLog(LogKind.SYS, "GOTCHA", "Sparkmouse was caught!", false)
        afterLayout(p.arena, fire)
        return p
    }

    @Test fun catchFull() = paparazzi.shootWith("r406-catch", listOf(Size.PHONE, Size.SPLIT, Size.TABLET, Size.TABLET_LAND), offsetMillis = 400) { ctx -> full(ctx, catchMoment("New in your Pokédex")) }
    @Test fun catchSmall() = paparazzi.shootWith("r406-catch", listOf(Size.FLOATBATTLE, Size.FLOATB, Size.PIP, Size.PIP_11), offsetMillis = 400) { ctx -> small(ctx, catchMoment("New in your Pokédex")) }
    @Test fun catchSwap() = paparazzi.shootWith("r406-catch-swap", listOf(Size.PHONE, Size.SPLIT, Size.PIP), offsetMillis = 400) { ctx ->
        if (ctx.resources.configuration.screenHeightDp < 200) small(ctx, catchMoment("Swapped for Lv 12")) else full(ctx, catchMoment("Swapped for Lv 12"))
    }
    @Test fun catchStudy() = paparazzi.shootWith("r406-catch-study", listOf(Size.SPLIT_SHORT, Size.SPLIT_TINY), offsetMillis = 400) { ctx -> study(ctx, ctx.resources.configuration.screenHeightDp < 280, catchMoment(null)) }
    @Test fun catchStrip() = paparazzi.shootWith("r406-catch-strip", listOf(Size.PIP_169M, Size.PIP_169), offsetMillis = 400) { ctx -> study(ctx, true, catchMoment("New in your Pokédex")) }
}
