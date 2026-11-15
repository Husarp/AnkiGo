package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R36QShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun ownedAndWildMovesCountEverySlotWithoutLeakingHiddenDetails() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
            val data = GameRepo.data!!; val state = GameRepo.state!!; val mon = state.main!!
            mon.moves.clear(); mon.moves += listOf("tackle", "growl", "vinewhip", "lick")
            mon.discoveredMoves.clear(); mon.discoveredMoves += "tackle"
            val content = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 24), Ui.dp(ctx, 16), 0)
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(Ui.text(ctx, 20f, 900, R.color.ag_text).apply { text = "${mon.shownName} · 4 moves" })
                addView(DetailSections.moves(ctx, data, mon.moves.filter { it in mon.discoveredMoves }, false, unknown = 3))
                addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = "Use a move in battle to discover it." })
            }
            val text = all(content).filterIsInstance<TextView>().map { it.text.toString() }
            assertEquals(3, text.count { it == "???" })
            assertTrue("Tackle" in text)
            assertFalse(text.any { it in listOf("Growl", "Vine Whip", "Lick") })
            assertTrue(all(content).filter { it.contentDescription == "Undiscovered move" }.all { !it.isClickable })
            paparazzi.snapshot(content, "q-moves-${theme.name.lowercase()}")
            val wild = state.battle!!.wild
            wild.discoveredMoves.clear()
            val info = WildInfo.of(data, state, state.battle!!, emptySet())!!
            assertEquals(wild.moves.size, info.moves.size)
            assertTrue(info.moves.all { it.id == null })
            val compare = CompareTable.build(data, state, listOf(ComparePick.Owned(mon.uid), ComparePick.Dex(wild.species)), false)
            val cells = compare.rows.single { it.key == "moves" }.cells
            assertEquals(mon.moves.size, (cells[0] as Cell.Moves).moves.size)
            assertEquals(3, (cells[0] as Cell.Moves).moves.count { it.name == "???" && it.id.isEmpty() })
        }
    }

    @Test fun actualOwnedAndWildDetailsKeepAllSlotsMasked() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val data = GameRepo.data!!; val state = GameRepo.state!!; val mon = state.main!!
        mon.discoveredMoves.clear()
        val owned = DialogShots.capture { MonDetail.show(ctx, data, mon, focusMove = mon.moves.first()) }!!
        assertEquals(mon.moves.size, all(owned.content()).count { it.contentDescription == "Undiscovered move" })
        assertTrue(all(owned.content()).filterIsInstance<TextView>().any { it.text.toString() == "${mon.moves.size} moves" })
        owned.dismiss()
        val wild = state.battle!!.wild
        wild.discoveredMoves.clear()
        val enemy = DialogShots.capture { assertTrue(BattleDetails.showWild(ctx, focusMove = wild.moves.first())) }!!
        assertEquals(wild.moves.size, all(enemy.content()).count { it.contentDescription == "Undiscovered move" })
        enemy.dismiss()
    }

    @Test fun tmPickerOnlyShowsDiscoveredMovesAndReturnsOriginalIndex() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val data = GameRepo.data!!; val mon = GameRepo.state!!.main!!
        mon.moves.clear(); mon.moves += listOf("tackle", "growl", "vinewhip", "lick")
        mon.discoveredMoves.clear(); mon.discoveredMoves += "lick"
        var picked = -1
        val dialog = DialogShots.capture { Pickers.pickMove(ctx, "Forget a discovered move", data, mon) { picked = it } }!!
        DialogShots.scene(ctx, dialog).measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 360), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 640), View.MeasureSpec.EXACTLY))
        val list = all(dialog.content()).filterIsInstance<android.widget.ListView>().single()
        assertEquals(1, list.adapter.count)
        val row = list.adapter.getView(0, null, list)
        val text = all(row).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(text.any { "Lick" in it })
        assertFalse(text.any { it in listOf("Tackle", "Growl", "Vine Whip") })
        list.performItemClick(row, 0, 0)
        assertEquals(3, picked)
        dialog.dismiss()
    }
}
