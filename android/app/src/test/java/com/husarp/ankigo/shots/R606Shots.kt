package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.SwitchPopup
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * R6-06 (items 5, 12, 15): the Pokémon tab without the trainer card and the Compare button, the Stats tab with the trainer card at
 * the top, and the swap picker of a full team (header line, a fainted one can be swapped out). PNGs: r606-team, r606-stats, r606-swap.
 */
class R606Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Before fun storeOff() { AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot() }
    @After fun storeOn() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun descriptions(v: View, out: MutableList<String> = ArrayList()): List<String> {
        v.contentDescription?.let { out += it.toString() }
        if (v is ViewGroup) for (i in 0 until v.childCount) descriptions(v.getChildAt(i), out)
        return out
    }

    @Test fun team() = paparazzi.shootPage("r606-team", listOf(Variants.PHONE)) { ctx ->
        val p = TeamPage(ctx, NoHost)
        p.preset(TeamSection.TEAM)
        val screen = pageScreen(ctx, p)
        call(p, "render")
        assertTrue("no trainer card here", texts(p.root).none { it.startsWith("Trainer Lv") })
        assertTrue("no Compare button in the bar", "Compare" !in descriptions(p.root))
        screen
    }

    @Test fun stats() = paparazzi.shootPage("r606-stats", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
        val p = StatsPage(ctx, NoHost)
        val screen = pageScreen(ctx, p)
        call(p, "render")
        assertTrue(texts(p.root).any { it.startsWith("Trainer Lv") })
        screen
    }

    @Test fun swapPicker() = paparazzi.shootPage("r606-swap", listOf(Variants.PHONE)) { ctx ->
        SwitchPopup.content(ctx, GameRepo.data!!, GameRepo.state!!, {}, {}, header = "Pick a team Pokémon to swap out", swap = true)
    }

    /** In the swap picker every member can be picked (also the fainted t5 of the fake team), and the header line is there. */
    @Test fun swapPickerPicksEveryone() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val picked = ArrayList<String>()
        val root = SwitchPopup.content(ctx, GameRepo.data!!, GameRepo.state!!, { picked += it }, {}, header = "Pick a team Pokémon to swap out", swap = true)
        val t = texts(root)
        assertTrue("Pick a team Pokémon to swap out" in t)
        assertTrue("Swap out" in t)
        val cards = ArrayList<View>()
        fun walk(v: View) { if (v.hasOnClickListeners()) cards += v; (v as? ViewGroup)?.let { g -> for (i in 0 until g.childCount) walk(g.getChildAt(i)) } }
        walk(root)
        cards.forEach { it.performClick() }
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5", "t6"), picked)
    }
}
