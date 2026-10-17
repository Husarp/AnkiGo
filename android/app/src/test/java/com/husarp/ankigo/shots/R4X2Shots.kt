package com.husarp.ankigo.shots

import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattleDetails
import com.husarp.ankigo.ui.DetailOpenStore
import com.husarp.ankigo.ui.DetailSections
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R4-X2 (PLAN 93): tap the wild Pokémon in a battle -> its read-only info window over the battle.
 * PNGs: r4x2-unknown (no move used yet), r4x2-used (two moves used, tier/Pokédex line), r4x2-tap (the window opened by the tap on the
 * battle screen).
 */
class R4X2Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() {
        BattleShotKit.reset()
        DetailSections.storeOf = { ctx -> object : DetailOpenStore {
            override fun get(key: String): Boolean? = com.husarp.ankigo.ui.AppPrefs.detailOpen(ctx, key)
            override fun set(key: String, open: Boolean) = com.husarp.ankigo.ui.AppPrefs.setDetailOpen(ctx, key, open)
        } }
    }

    /** The wild window has the same sections as the own details; Info (tier, Pokédex line) starts closed, so these shots open it. */
    private fun openInfo() {
        DetailSections.storeOf = { object : DetailOpenStore { override fun get(key: String): Boolean? = if (key == "info") true else null; override fun set(key: String, open: Boolean) {} } }
    }

    private fun attack(move: String) = GameEvent.Attack(
        false, "Duckling", "Me", move, Type.WATER, Category.SPECIAL, 5, 1, 1.0, false, false, false, 10, 20, 10, 20,
    )

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v is android.widget.TextView) out += v.text.toString()
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun window(ctx: android.content.Context): View {
        val d = DialogShots.capture { assertTrue(BattleDetails.showWild(ctx)) } ?: error("no dialog")
        return DialogShots.scene(ctx, d)
    }

    @Test fun unknownMoves() = paparazzi.shootWith("r4x2-unknown", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
        val b = BattleShotKit.bound(ctx)
        val scene = window(ctx)
        val t = texts(scene)
        assertEquals("every move is still unknown", 2, t.count { it == "?" })
        assertFalse(t.any { it == "IV" || it == "EV" || it.contains("Nature") })
        FrameLayout(ctx).apply { addView(b.root, FrameLayout.LayoutParams(-1, -1)); addView(scene, FrameLayout.LayoutParams(-1, -1)) }
    }

    @Test fun usedMovesAndTier() = paparazzi.shootWith("r4x2-used", listOf(Size.PHONE, Size.FLOATBATTLE)) { ctx ->
        openInfo()
        val st = GameRepo.state!!
        val battle = st.battle!!
        battle.tier = Tier.LEGENDARY
        battle.wild.shiny = true
        battle.wild.gender = 'F'
        GameRepo.wildMoves.observe(listOf(attack("Water Gun")), battle)
        val scene = window(ctx)
        val t = texts(scene)
        assertTrue(t.contains("Water Gun"))
        assertTrue(t.contains("Legendary"))
        assertEquals("the other move stays unknown", 1, t.count { it == "?" })
        assertFalse("an unused move is not named", t.contains("Tackle"))
        scene
    }

    /** The whole window on a tall screen, so the Moves section (scrolled out of a phone shot) is visible. */
    @Test fun tall() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme).copy(screenHeight = 3000))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val battle = GameRepo.state!!.battle!!
            GameRepo.wildMoves.observe(listOf(attack("Tackle")), battle)
            paparazzi.snapshot(window(ctx), "r4x2-tall-${theme.name.lowercase()}")
        }
    }

    @Test fun tapOnTheWildOneOpensIt() {
        val ctx = paparazzi.context
        FakeGame.install(ctx)
        val a = ArenaView(ctx)
        var taps = 0
        a.onWildTap = { taps++ }
        val holder = (0 until a.childCount).map { a.getChildAt(it) }.first { it.contentDescription?.startsWith("Wild Pokémon") == true }
        assertTrue(holder.isClickable)
        holder.performClick()
        assertEquals(1, taps)
        assertNotNull(GameRepo.state?.battle)
    }
}
