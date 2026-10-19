package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.XpParticles
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * R9-W6 CX-A: still frames of the catch where the XP orbs burst out of the ball at the click: 300 ms after it (orbs leaving the glowing
 * ball, "Gotcha!" banner, stars, the number counting over the ball), mid-flight, and after the last landing (ball and banner gone, bar grown).
 * PNGs: r9cx-click300, r9cx-mid, r9cx-after (PHONE light + dark), r9cx-click300-small / r9cx-mid-small (FLOATBATTLE).
 */
class R9CatchXpShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val plan = XpParticles.plan(XpParticles.MOCK_SEED, burst = true)

    private fun layoutAgain(a: ArenaView) {
        a.measure(View.MeasureSpec.makeMeasureSpec(a.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(a.height, View.MeasureSpec.EXACTLY))
        a.layout(a.left, a.top, a.right, a.bottom)
    }

    private fun afterLayout(arena: ArenaView, fire: (ArenaView) -> Unit) {
        var done = false
        arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!done && v.width > 0 && v.height > 0) {
                done = true
                fire(arena)
                layoutAgain(arena)
            }
        }
    }

    /** Paparazzi does not advance View animators: put the ball and the banner at their resting state by hand. */
    private fun settle(a: ArenaView, bannerAlpha: Float) {
        fun field(n: String) = ArenaView::class.java.getDeclaredField(n).also { it.isAccessible = true }
        val ball = field("ball").get(a) as View
        val banner = field("banner").get(a) as View
        val fit = field("bannerFit").getFloat(a)
        ball.animate().cancel(); ball.translationY = 0f
        banner.animate().cancel(); banner.alpha = bannerAlpha; banner.scaleX = fit; banner.scaleY = fit
    }

    /** The scene [atMs] after the click: the ball on the field (until it has faded), the banner, the stars and the burst frozen at that moment. */
    private fun scene(atMs: Float, bar: Int) = { a: ArenaView ->
        a.setEnemyCardShown(false)
        a.enemySprite.scaleX = 0f; a.enemySprite.scaleY = 0f; a.enemySprite.alpha = 0f
        a.dropBall("poke-ball", 1)
        a.setPlayerXp(bar, 100, false)
        val over = atMs >= plan.bannerEnd
        if (!over) {
            a.showStars(true)
            a.showBanner("Gotcha!", "Sparkmouse was caught", "New in your Pokédex", numberAbove = true)
            settle(a, 1f - (atMs - plan.bannerFadeFrom).coerceIn(0f, 250f) / 250f)
        }
        layoutAgain(a)
        a.xpBurstStill(48, atMs)
        if (over) a.clearBall()
    }

    private fun full(ctx: Context, fire: (ArenaView) -> Unit): View {
        val b = BattleShotKit.bound(ctx)
        afterLayout(b.arena!!, fire)
        return b.root
    }

    private fun small(ctx: Context, fire: (ArenaView) -> Unit): View {
        val p = BattlePanelView(ctx)
        p.setCompact(true); p.renderAll()
        afterLayout(p.arena, fire)
        return p
    }

    @Test fun click300() = paparazzi.shootWith("r9cx-click300", listOf(Size.PHONE, Size.SPLIT), offsetMillis = 400) { ctx -> full(ctx, scene(300f, 20)) }
    @Test fun midFlight() = paparazzi.shootWith("r9cx-mid", listOf(Size.PHONE), offsetMillis = 400) { ctx -> full(ctx, scene(1000f, 45)) }
    @Test fun afterTheLastLanding() = paparazzi.shootWith("r9cx-after", listOf(Size.PHONE), offsetMillis = 400) { ctx -> full(ctx, scene(plan.bannerEnd + 100f, 70)) }
    @Test fun click300Small() = paparazzi.shootWith("r9cx-click300-small", listOf(Size.FLOATBATTLE), offsetMillis = 400) { ctx -> small(ctx, scene(300f, 20)) }
    @Test fun midFlightSmall() = paparazzi.shootWith("r9cx-mid-small", listOf(Size.FLOATBATTLE), offsetMillis = 400) { ctx -> small(ctx, scene(1000f, 45)) }
}
