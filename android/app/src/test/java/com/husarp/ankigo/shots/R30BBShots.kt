package com.husarp.ankigo.shots

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30BBShots {
    @get:Rule val paparazzi = Shots.rule()
    private val state get() = GameRepo.state!!
    private var page: TeamPage? = null
    private var dialog: Dialog? = null
    private fun all(v: View): List<View> = if (v.visibility == View.GONE) emptyList() else listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun draw(p: TeamPage) = TeamPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(p)
    @After fun clean() {
        dialog?.dismiss(); page?.onGone()
        BoxMonPicker.interceptShow = null; BoxCopiesSheet.interceptShow = null
    }
    private fun scene(test: (Context, String) -> Unit) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p360", 360, 820).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            FakeGame.install(ctx)
            SortPrefs.saveShows(ctx, emptySet())
            SortPrefs.saveType(ctx, SortPrefs.TypeWhere.BOX, TypeFilter())
            state.mons.forEach { it.heldItem = null }
            test(ctx, theme.name.lowercase())
            clean()
        }
    }
    private fun open(ctx: Context, picked: (Mon) -> Unit = {}): TeamPage {
        BoxMonPicker.interceptShow = { d, p ->
            (p.root.parent as? ViewGroup)?.removeView(p.root)
            dialog = d; page = p
        }
        BoxMonPicker.show(TeamPage(ctx, NoHost), "Give Soothe Bell to…", { it.heldItem != "soothe-bell" }, picked)
        return checkNotNull(page).also { draw(it) }
    }
    private fun shot(p: TeamPage, name: String) = paparazzi.snapshot(p.root, "bb-485-$name", 300)
    private fun cell(p: TeamPage, mon: Mon): View = all(p.root).first {
        it.contentDescription?.startsWith("${mon.shownName}, level") == true
    }
    @Test fun onlyBoxIsReachableAndSearchAndFiltersWork() = scene { ctx, theme ->
        val p = open(ctx)
        p.preset(TeamSection.BOX, group = false)
        draw(p)
        p.showSection(TeamSection.TEAM); p.showSection(TeamSection.DEX)
        p.startPickWith(state.mons.first().uid); p.showDetailsOnce(state.mons.first().uid)
        draw(p)
        assertNull(p.tab)
        assertFalse(texts(p.root).any { it in listOf("Team", "Pokédex", "Compare (1)") })
        assertTrue("Give Soothe Bell to…" in texts(p.root))
        shot(p, "box-$theme")
        val field = all(p.root).filterIsInstance<EditText>().single()
        field.setText("no-such-pokemon"); draw(p)
        assertTrue("No Pokémon match your search." in texts(p.root))
        field.setText(""); draw(p)
        assertTrue(all(p.root).any { it.contentDescription?.contains(", level") == true })
        val sheet = p.sortSheet().build(Ui.dp(ctx, 600))
        all(sheet).filterIsInstance<TextView>().first { it.text.toString() == "In team" }.performClick()
        draw(p)
        shot(p, "filtered-$theme")
        val shown = TeamPage::class.java.getDeclaredField("shown").apply { isAccessible = true }.getInt(p)
        assertEquals(state.teamMons().size, shown)
        assertTrue(texts(sheet).any { it.contains("Shiny") })
        assertTrue(texts(sheet).any { it.contains("Favourites") })
    }
    @Test fun singletonSelectsOnceAndCurrentHolderIsDimmedAndBlocked() = scene { ctx, theme ->
        state.mons.retainAll(state.mons.take(6))
        val holder = state.mons.first().apply { heldItem = "soothe-bell" }
        var chosen: Mon? = null
        var count = 0
        val p = open(ctx) { chosen = it; count++ }
        p.preset(TeamSection.BOX, group = false); draw(p)
        shot(p, "disabled-holder-$theme")
        val disabled = cell(p, holder)
        assertFalse(disabled.isEnabled); assertEquals(0.35f, disabled.alpha)
        disabled.performClick(); assertNull(chosen)
        assertFalse(disabled.performLongClick())
        val target = state.mons[1]
        val enabled = cell(p, target)
        enabled.performClick(); enabled.performClick()
        assertSame(target, chosen); assertEquals(1, count)
    }
    @Test fun duplicateGroupUsesCopiesSheetAndOnlyEligibleCopySelects() = scene { ctx, theme ->
        val holder = state.mons[0].apply { heldItem = "soothe-bell"; level = 80 }
        val target = state.mons[1].apply { species = holder.species; name = holder.name; nickname = null; level = 20 }
        state.mons.retainAll(listOf(holder, target))
        var chosen: Mon? = null
        val p = open(ctx) { chosen = it }
        p.preset(TeamSection.BOX, group = true); draw(p)
        shot(p, "group-$theme")
        var copies: BoxCopiesSheet? = null
        BoxCopiesSheet.interceptShow = { copies = it }
        val group = cell(p, holder)
        assertTrue(group.isEnabled)
        assertFalse(group.performLongClick())
        group.performClick(); assertNull(chosen)
        val body = checkNotNull(copies).build(Ui.dp(ctx, 400))
        paparazzi.snapshot(body, "bb-485-copies-$theme", 300)
        val rows = all(body).filter { it.minimumHeight == Ui.dp(ctx, 56) }
        assertEquals(2, rows.size)
        rows[0].performClick(); assertNull(chosen)
        assertFalse(rows[0].isEnabled); assertEquals(0.35f, rows[0].alpha)
        rows[1].performClick(); assertSame(target, chosen)
    }
    @Test fun backAndSystemCancelPreserveTheSave() = scene { ctx, theme ->
        state.mons.first().heldItem = "soothe-bell"
        state.viewedMons += state.mons.map { it.uid }
        val before = SaveCodec.encode(state)
        var count = 0
        val p = open(ctx) { count++ }
        shot(p, "cancel-$theme")
        all(p.root).single { it.contentDescription == "Back" }.performClick()
        assertEquals(before, SaveCodec.encode(state)); assertEquals(0, count)
        clean()
        open(ctx) { count++ }
        dialog!!.cancel()
        assertEquals(before, SaveCodec.encode(state)); assertEquals(0, count)
    }
}
