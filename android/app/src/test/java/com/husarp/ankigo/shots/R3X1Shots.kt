package com.husarp.ankigo.shots

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BarView
import com.husarp.ankigo.ui.BattleDetails
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R3-X1 (Adam's feedback on 0.11.0): HP ghost + "61 / 61 HP" beside the bar, the Details window over the battle.
 * PNGs: r3x1-cards (battle panel), r3x1-ghost (bars), r3x1-details (tap on your Pokémon). The Box items are in BoxShots (box-*).
 */
class R3X1Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun arenaField(a: ArenaView, name: String): Any? = ArenaView::class.java.getDeclaredField(name).let { it.isAccessible = true; it.get(a) }

    /** The whole battle screen: HP bars with the number beside them, the status row under, XP under that. */
    @Test fun cards() = paparazzi.shootWith("r3x1-cards", listOf(Size.PHONE, Size.SPLIT, Size.FLOATBATTLE)) { ctx ->
        val b = BattleShotKit.bound(ctx)
        val main = GameRepo.state!!.main!!
        main.hp = (main.hp * 0.6f).toInt().coerceAtLeast(1)
        b.panel!!.renderAll()
        b.root
    }

    /** The HP ghost on the player's bar (a hit just took the top 30 %), and on bare bars. */
    @Test fun ghost() = paparazzi.shootWith("r3x1-ghost", listOf(Size.PHONE)) { ctx ->
        val b = BattleShotKit.bound(ctx)
        val arena = b.arena!!
        (arenaField(arena, "pHp") as BarView).apply { ghostOnDrop = true; set(0.6f, Ui.c(ctx, com.husarp.ankigo.R.color.ag_green), false); ghostStill(0.9f) }
        (arenaField(arena, "eHp") as BarView).apply { set(0.4f, Ui.c(ctx, com.husarp.ankigo.R.color.ag_yellow), false); ghostStill(0.75f) }
        val bars = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 12), Ui.dp(ctx, 24), Ui.dp(ctx, 12))
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            for ((now, was) in listOf(0.8f to 1f, 0.5f to 0.8f, 0.15f to 0.5f, 0.0f to 0.2f)) {
                addView(BarView(ctx).apply { set(now, Ui.hpColor(ctx, now), false); ghostStill(was) }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 8)).also { it.topMargin = Ui.dp(ctx, 10) })
            }
        }
        FrameLayout(ctx).apply {
            addView(b.root, FrameLayout.LayoutParams(-1, -1))
            addView(bars, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
    }

    /** Tap on your Pokémon in a battle: the Details window (heart, pencil, More options) on top, no Team tab. */
    @Test fun detailsOverBattle() = paparazzi.shootWith("r3x1-details", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
        val b = BattleShotKit.bound(ctx)
        val uid = GameRepo.state!!.main!!.uid
        val d = DialogShots.capture { assertTrue(BattleDetails.show(ctx, uid)) } ?: error("no dialog")
        assertEquals("Details", "Details")
        FrameLayout(ctx).apply {
            addView(b.root, FrameLayout.LayoutParams(-1, -1))
            addView(DialogShots.scene(ctx, d), FrameLayout.LayoutParams(-1, -1))
        }
    }

    @Test fun detailsHasTheFullActions() {
        paparazzi.shootWith("r3x1-details-check", listOf(Size.PHONE)) { ctx ->
            val uid = GameRepo.state!!.main!!.uid
            val d = DialogShots.capture { BattleDetails.show(ctx, uid) }
            assertNotNull(d)
            val scene = DialogShots.scene(ctx, d!!)
            assertTrue("More options", texts(scene).any { it == "More options" })
            assertTrue("Close", texts(scene).any { it.equals("Close", true) })
            assertTrue("Rename pencil", descs(scene).any { it == "Rename" })
            scene
        }
    }

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v is android.widget.TextView) out += v.text.toString()
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun descs(v: View, out: MutableList<String> = ArrayList()): List<String> {
        v.contentDescription?.let { out += it.toString() }
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) descs(v.getChildAt(i), out)
        return out
    }
}
