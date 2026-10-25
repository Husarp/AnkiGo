package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * R19-L (PLAN 271, pick A "a bright flash"): before, the flash (the old picture all white with sparkles, 200 ms), the copy fading out of the
 * white (300 ms) and after. "Your Pokémon turns into the wild one" (Eevee as the Ditto, into Psyduck) and "the wild one turns into yours"
 * (Eevee as the wild Ditto, into Pikachu). Light and dark, a full screen and the floating window.
 */
class R19LTransformShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private enum class Who { MINE, WILD }

    /** The state before ([transformed] false) or after the transform: Eevee (133, "t6") is the Ditto. */
    private fun setup(ctx: Context, who: Who, transformed: Boolean) {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val st = GameRepo.state!!
        val data = GameRepo.data!!
        val ditto = st.mon("t6")!!
        if (who == Who.MINE) {
            st.team.remove("t6"); st.team.add(0, "t6")
            val wild = st.battle!!.wild
            if (transformed) st.battle!!.player.notes.also { it["mc.transform"] = wild.species.toString(); it["mc.tlook"] = wild.species.toString() }
        } else {
            val w = com.husarp.ankigo.game.TestData.mon(data, 133, 25, listOf("transform"), "wild")
            w.hp = (StatCalc.maxHp(data, w) * 0.7).toInt()
            st.battle = Battle(w, Tier.NORMAL, answers = 1, tally = 10, rounds = 2, startedAt = 1L)
            if (transformed) st.battle!!.enemy.notes.also { it["mc.transform"] = st.main!!.species.toString(); it["mc.tlook"] = st.main!!.species.toString() }
        }
        check(ditto.species == 133)
    }

    private fun stillAfterLayout(arena: ArenaView, player: Boolean, atMs: Float) {
        if (atMs < 0f) return
        var done = false
        arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!done && v.width > 0 && v.height > 0) {
                done = true
                arena.transformStill(player, atMs)
                arena.measure(View.MeasureSpec.makeMeasureSpec(arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(arena.height, View.MeasureSpec.EXACTLY))
                arena.layout(arena.left, arena.top, arena.right, arena.bottom)
            }
        }
    }

    private fun full(ctx: Context, who: Who, transformed: Boolean, atMs: Float): View {
        setup(ctx, who, transformed)
        val b = BattleShotKit.bound(ctx)
        stillAfterLayout(b.arena!!, who == Who.MINE, atMs)
        return b.root
    }

    private fun small(ctx: Context, who: Who, transformed: Boolean, atMs: Float): View {
        setup(ctx, who, transformed)
        val p = BattlePanelView(ctx)
        p.setCompact(true); p.renderAll()
        stillAfterLayout(p.arena, who == Who.MINE, atMs)
        return p
    }

    private val sizes = listOf(Size.PHONE)
    private val floating = listOf(Size.FLOATB)

    @Test fun mineBefore() = paparazzi.shootWith("r19l-mine-1before", sizes) { full(it, Who.MINE, false, -1f) }
    @Test fun mineFlash() = paparazzi.shootWith("r19l-mine-2flash", sizes) { full(it, Who.MINE, false, 200f) }
    @Test fun mineFade() = paparazzi.shootWith("r19l-mine-3fade", sizes) { full(it, Who.MINE, true, 300f) }
    @Test fun mineAfter() = paparazzi.shootWith("r19l-mine-4after", sizes) { full(it, Who.MINE, true, -1f) }
    @Test fun wildBefore() = paparazzi.shootWith("r19l-wild-1before", sizes) { full(it, Who.WILD, false, -1f) }
    @Test fun wildFlash() = paparazzi.shootWith("r19l-wild-2flash", sizes) { full(it, Who.WILD, false, 200f) }
    @Test fun wildFade() = paparazzi.shootWith("r19l-wild-3fade", sizes) { full(it, Who.WILD, true, 300f) }
    @Test fun wildAfter() = paparazzi.shootWith("r19l-wild-4after", sizes) { full(it, Who.WILD, true, -1f) }

    @Test fun floatMineFlash() = paparazzi.shootWith("r19l-float-mine-2flash", floating) { small(it, Who.MINE, false, 200f) }
    @Test fun floatMineAfter() = paparazzi.shootWith("r19l-float-mine-4after", floating) { small(it, Who.MINE, true, -1f) }
    @Test fun floatWildFlash() = paparazzi.shootWith("r19l-float-wild-2flash", floating) { small(it, Who.WILD, false, 200f) }
    @Test fun floatWildAfter() = paparazzi.shootWith("r19l-float-wild-4after", floating) { small(it, Who.WILD, true, -1f) }
}
