package com.husarp.ankigo.shots

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.study.PanelHeight
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattleFrameStyle
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.InfoBoxStyle
import com.husarp.ankigo.ui.PokemonSize
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import com.husarp.ankigo.ui.SheetKit
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.ui.ViewSettingsSheet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.min

/**
 * ROUND 10 B19, the battle's "View settings" sheet: its content in the Battle tab (no panel height row) and the Study screen (with
 * one), on PHONE, FLOAT (the floating window's width) and SPLIT, light and dark; the three battle menus list the row; every control
 * writes the same preference Settings writes, and an open battle panel follows each of them (its preference watcher).
 * PNGs: r10-view-battle-<size>-<theme>, r10-view-study-<size>-<theme>, r10-view-settings-size-<size>-<theme> (the Settings slider row).
 */
class R10ViewSettingsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val three = listOf(Size.PHONE, Size.FLOAT, Size.SPLIT)

    // ---- the sheet -------------------------------------------------------------------------------------------------------------------

    private fun sheet(ctx: Context, height: ViewSettingsSheet.PanelHeightPart?): View {
        val s = ViewSettingsSheet(ctx, height)
        val share = if (s.atTop) ViewSettingsSheet.TOP_SHARE else ViewSettingsSheet.BODY_SHARE
        val body = s.build((ctx.resources.displayMetrics.heightPixels * share).toInt() - Ui.dp(ctx, 120))
        return if (s.atTop) body else SheetKit.host(ctx, body, {}, {})
    }

    /** The sheet at the bottom of a dimmed screen (the top one on the stacked Study screen), the width a dialog gives it (at most 600 dp). */
    private fun scene(ctx: Context, height: ViewSettingsSheet.PanelHeightPart?): View {
        val w = min(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600))
        val top = ViewSettingsSheet.atTop(height)
        return FrameLayout(ctx).apply {
            setBackgroundColor(Color.argb(60, 0, 0, 0))
            addView(sheet(ctx, height), FrameLayout.LayoutParams(w, -2, (if (top) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL))
        }
    }

    private class TopSeen(val sheetTop: Int, val sheetBottom: Int, val panelTop: Int, val height: Int)
    private val topSeen = LinkedHashMap<String, TopSeen>()

    /** A made-up non-default state, so the shot shows picked values and not just the defaults. */
    private fun someSettings(ctx: Context) {
        AppPrefs.setPokemonSize(ctx, 115)
        AppPrefs.setPlayerFront(ctx, true)
        AppPrefs.setInfoBoxStyle(ctx, InfoBoxStyle.INSIDE)
        AppPrefs.setBattleFrame(ctx, BattleFrameStyle.WOOD)
        AppPrefs.setCompactCards(ctx, true)
        AppPrefs.setCardsUnderOwn(ctx, false)
    }

    @Test fun sheetInTheBattleTab() {
        paparazzi.shootWith("r10-view-battle", three) { ctx -> someSettings(ctx); scene(ctx, null) }
    }

    @Test fun sheetOnTheStudyScreen() {
        paparazzi.shootWith("r10-view-study", three) { ctx -> someSettings(ctx); scene(ctx, ViewSettingsSheet.PanelHeightPart({ 1 }) {}) } // top sheet
    }

    /** B19/M2: on the stacked Study screen the sheet is at the top and the battle panel (a stand-in block at the bottom) stays uncovered. */
    @Test fun sheetOnTheStackedStudyScreenIsAtTheTop() {
        assertTrue(ViewSettingsSheet.atTop(ViewSettingsSheet.PanelHeightPart({ 1 }) {}))
        assertFalse("the Battle tab and the floating window keep the bottom sheet", ViewSettingsSheet.atTop(null))
        for (size in three) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            someSettings(ctx)
            val panel = View(ctx).apply { setBackgroundColor(Color.argb(255, 60, 90, 70)); tag = "panel" }
            val sheetView = sheet(ctx, ViewSettingsSheet.PanelHeightPart({ 1 }) {})
            val w = min(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600))
            val key = "${size.name}-${theme.name}"
            val frame = object : FrameLayout(ctx) {
                override fun dispatchDraw(canvas: android.graphics.Canvas) {
                    topSeen[key] = TopSeen(sheetView.top, sheetView.bottom, panel.top, height)
                    super.dispatchDraw(canvas)
                }
            }.apply {
                setBackgroundColor(Color.argb(60, 0, 0, 0))
                // the stand-in for the battle panel: the bottom 42 % of the screen
                addView(panel, FrameLayout.LayoutParams(-1, (ctx.resources.displayMetrics.heightPixels * 0.42f).toInt(), Gravity.BOTTOM))
                addView(sheetView, FrameLayout.LayoutParams(w, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            }
            paparazzi.snapshot(frame, "r10-view-study-top-${size.name.lowercase()}-${theme.name.lowercase()}", 1000)
        }
        assertEquals(6, topSeen.size)
        for ((key, t) in topSeen) {
            assertEquals("$key: the sheet starts at the top", 0, t.sheetTop)
            assertTrue("$key: at most 55 % (${t.sheetBottom} of ${t.height})", t.sheetBottom <= t.height * ViewSettingsSheet.TOP_SHARE + 1)
            assertTrue("$key: the panel area is uncovered (sheet bottom ${t.sheetBottom} <= panel top ${t.panelTop})", t.sheetBottom <= t.panelTop)
        }
    }

    @Test fun sheetWithTheDefaults() {
        paparazzi.shootWith("r10-view-default", listOf(Size.PHONE)) { ctx -> scene(ctx, ViewSettingsSheet.PanelHeightPart({ 0 }) {}) }
    }

    @Test fun theRowsAndTheirOrder() {
        assertEquals(listOf("SIZE", "FACING", "INFO_BOX", "BARS", "FRAME", "CARD_OWN", "COMPACT", "BACKGROUND"), ViewSettingsSheet.parts(false).map { it.name })
        assertEquals(listOf("SIZE", "FACING", "INFO_BOX", "BARS", "FRAME", "CARD_OWN", "COMPACT", "BACKGROUND", "HEIGHT"), ViewSettingsSheet.parts(true).map { it.name })
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val titles = { h: ViewSettingsSheet.PanelHeightPart? -> texts(sheet(ctx, h)) }
        val battle = titles(null)
        val study = titles(ViewSettingsSheet.PanelHeightPart({ 1 }) {})
        for (t in listOf("View settings", "POKÉMON SIZE", "FACING", "Info box style", "HP / XP bars", "Battle frame", "Stat card under its own Pokémon", "Compact stat cards", "Background pictures", "Done")) {
            assertTrue("battle sheet has '$t'", t in battle)
            assertTrue("study sheet has '$t'", t in study)
        }
        for (s in BattleFrameStyle.entries) assertTrue("a tile for ${s.label}", s.label in battle)
        for (s in InfoBoxStyle.entries) assertTrue("an option for ${s.label}", s.label in battle)
        assertFalse("no panel height row in the Battle tab", "PANEL HEIGHT" in battle)
        assertTrue("panel height row on the Study screen", "PANEL HEIGHT" in study)
        assertTrue("Full / Strip / Mini", listOf("Full", "Strip", "Mini").all { it in study })
    }

    // ---- the menus -------------------------------------------------------------------------------------------------------------------

    private fun labels(m: AppMenu?) = requireNotNull(m) { "no menu was shown" }.describe().map { it.second }

    @Test fun theBattleTabMenuListsViewSettings() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val b = BattleShotKit.bound(ctx)
        val l = labels(MenuKit.capture { b.call("showMenu", b.root) })
        assertTrue(l.toString(), "View settings" in l)
        assertEquals("right after the Battle log", l.indexOf("Battle log") + 1, l.indexOf("View settings"))
    }

    @Test fun theFloatingBattleMenuListsViewSettings() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val m = AppMenu(View(ctx))
        FloatBattleView(object : android.content.ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }, {}, {}, {}).fillMenu(m)
        val l = labels(m)
        assertTrue(l.toString(), "View settings" in l)
        assertEquals(listOf("Open AnkiGo", "Pause watching AnkiDroid", "Pokémon", "Pokédex", "Shop", "Settings", "View settings", "Help"), l)
    }

    private fun studyScreen(ctx: Context): StudyActivity {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L); setField(act, "deckName", "Geography::Capitals"); setField(act, "heightMode", 1)
        act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "flipped", false); setField(act, "locked", false)
        call(act, "layout")
        (getField(act, "panel") as BattlePanelView).renderAll()
        return act
    }

    @Test fun theStudyPanelMenuListsViewSettingsAndItsHeightRowMovesThePanel() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val act = studyScreen(ctx)
        val panel = getField(act, "panel") as BattlePanelView
        val l = labels(MenuKit.capture { panel.onMenu(View(ctx)) })
        assertTrue(l.toString(), "View settings" in l)
        assertEquals("after Switch Pokémon", l.indexOf("Switch Pokémon") + 1, l.indexOf("View settings"))
        // the sheet's Panel height row goes through the Study screen's own setPanelDp: Full / Strip / Mini = the handle's presets
        val part = act.viewSettingsHeight()
        assertNotNull("the stacked layout has the handle", part)
        for (mode in listOf(2, 0, 1)) {
            part!!.set(mode)
            assertEquals("mode $mode: panel height", Ui.dp(ctx, PanelHeight.preset(false, mode)), panel.layoutParams.height)
            assertEquals("mode $mode: the sheet shows it", mode, part.current())
        }
    }

    // ---- the controls ----------------------------------------------------------------------------------------------------------------

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v is TextView && v.text.isNotEmpty()) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun find(v: View, test: (View) -> Boolean, out: MutableList<View> = ArrayList()): List<View> {
        if (test(v)) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), test, out)
        return out
    }

    /** The clickable ancestor (or the view itself) of the first text equal to [label]. */
    private fun tap(root: View, label: String) {
        var v: View? = find(root, { it is TextView && it.text.toString() == label }).firstOrNull() ?: throw AssertionError("no '$label' in the sheet")
        while (v != null && !v.isClickable) v = v.parent as? View
        requireNotNull(v) { "'$label' is not tappable" }.performClick()
    }

    private fun press(bar: SeekBar, key: Int, times: Int) {
        repeat(times) { bar.onKeyDown(key, KeyEvent(KeyEvent.ACTION_DOWN, key)) }
    }

    @Test fun everyControlWritesTheSamePreferenceSettingsWrites() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        var picked = -1
        val root = sheet(ctx, ViewSettingsSheet.PanelHeightPart({ 0 }) { picked = it })
        // size: the slider keys move one step (5 %) at a time and write the pref
        val bar = find(root, { it is SeekBar }).single() as SeekBar
        assertEquals(PokemonSize.index(100), bar.progress)
        assertTrue("100%" in texts(root))
        press(bar, KeyEvent.KEYCODE_DPAD_RIGHT, 6)
        assertEquals(130, AppPrefs.pokemonSize(ctx)); assertTrue("130%" in texts(root))
        press(bar, KeyEvent.KEYCODE_DPAD_RIGHT, 3)
        assertEquals("the slider stops at 130", 130, AppPrefs.pokemonSize(ctx))
        press(bar, KeyEvent.KEYCODE_DPAD_LEFT, 20)
        assertEquals("... and at 80", 80, AppPrefs.pokemonSize(ctx)); assertTrue("80%" in texts(root))
        press(bar, KeyEvent.KEYCODE_DPAD_RIGHT, 1)
        assertEquals(85, AppPrefs.pokemonSize(ctx))
        // facing, info box style, battle frame
        assertFalse(AppPrefs.playerFront(ctx))
        tap(root, "Front"); assertTrue(AppPrefs.playerFront(ctx))
        tap(root, "Back"); assertFalse(AppPrefs.playerFront(ctx))
        assertEquals(InfoBoxStyle.ABOVE, AppPrefs.infoBoxStyle(ctx))
        tap(root, InfoBoxStyle.INSIDE.label); assertEquals(InfoBoxStyle.INSIDE, AppPrefs.infoBoxStyle(ctx))
        assertEquals(BattleFrameStyle.NONE, AppPrefs.battleFrame(ctx))
        for (s in BattleFrameStyle.entries) { tap(root, s.label); assertEquals(s, AppPrefs.battleFrame(ctx)) }
        // the three switches
        val was = Triple(AppPrefs.cardsUnderOwn(ctx), AppPrefs.compactCards(ctx), AppPrefs.backgroundPictures(ctx))
        tap(root, "Stat card under its own Pokémon"); assertEquals(!was.first, AppPrefs.cardsUnderOwn(ctx))
        tap(root, "Compact stat cards"); assertEquals(!was.second, AppPrefs.compactCards(ctx))
        tap(root, "Background pictures"); assertEquals(!was.third, AppPrefs.backgroundPictures(ctx))
        assertEquals(3, find(root, { it is Switch }).size)
        // the panel height goes to the host
        tap(root, "Mini"); assertEquals(2, picked)
        tap(root, "Full"); assertEquals(0, picked)
        tap(root, "Strip"); assertEquals(1, picked)
    }

    @Test fun theSettingsSliderIsTheSameControlAndWritesTheSamePreference() {
        paparazzi.shootPage("r10-view-settings-size", listOf(Variants.PHONE, Variant("float", 340, 480))) { ctx ->
            AppPrefs.setPokemonSize(ctx, 120)
            val c = ShotCtx(ctx)
            val p = SettingsPage(c, NoHost)
            val v = pageScreen(c, p)
            p.open(SettingsSection.BATTLE)
            val bar = find(v, { it is SeekBar }).firstOrNull() as? SeekBar
            assertNotNull("the Battle section has the slider", bar)
            assertEquals(PokemonSize.index(120), bar!!.progress)
            assertTrue("shows 120%", "120%" in texts(v))
            assertTrue("a row named Pokémon size", "Pokémon size" in texts(v))
            press(bar, KeyEvent.KEYCODE_DPAD_RIGHT, 2)
            assertEquals(130, AppPrefs.pokemonSize(ctx))
            v
        }
    }

    // ---- an open panel follows --------------------------------------------------------------------------------------------------------

    private fun watcher(panel: BattlePanelView) = getField(panel, "facingWatch") as SharedPreferences.OnSharedPreferenceChangeListener

    @Test fun anOpenPanelFollowsEverySettingTheSheetChanges() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val panel = BattlePanelView(ctx)
        panel.renderAll()
        val arena = panel.arena
        assertEquals(1f, arena.pokemonSize, 0f)
        assertTrue(arena.cardsUnderOwn)
        assertEquals(InfoBoxStyle.ABOVE, arena.infoBoxStyle)
        assertEquals(0f, arena.frameInsetDp, 0f)
        // the sheet changes the settings ...
        val root = sheet(ctx, null)
        val bar = find(root, { it is SeekBar }).single() as SeekBar
        press(bar, KeyEvent.KEYCODE_DPAD_RIGHT, 6)
        tap(root, "Stat card under its own Pokémon")
        tap(root, InfoBoxStyle.INSIDE.label)
        tap(root, BattleFrameStyle.GOLD.label)
        tap(root, "Compact stat cards")
        // ... and the panel's watcher hears each one (the renderer's preference store keeps no listeners, so it is called as the store would)
        val w = watcher(panel)
        val prefs = ctx.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE)
        val keys = listOf(PokemonSize.KEY, "cardsUnderOwn", InfoBoxStyle.KEY, com.husarp.ankigo.ui.BattleFrame.KEY, "compactCards", "playerFront", "backgroundPictures")
        // the watcher posts its refresh to the panel: call it once the panel is on a window, as the store would
        val ping = object : View(ctx) {
            override fun onAttachedToWindow() { super.onAttachedToWindow(); for (key in keys) w.onSharedPreferenceChanged(prefs, key) }
        }
        val frame = FrameLayout(ctx).apply {
            addView(panel, FrameLayout.LayoutParams(-1, -1))
            addView(ping, FrameLayout.LayoutParams(1, 1))
        }
        paparazzi.snapshot(frame, "r10-view-follow", 1000)
        assertEquals("size", 1.3f, arena.pokemonSize, 0.0001f)
        assertFalse("card under its own", arena.cardsUnderOwn)
        assertEquals("info box style", InfoBoxStyle.INSIDE, arena.infoBoxStyle)
        assertTrue("battle frame (${arena.frameInsetDp})", arena.frameInsetDp > 0f)
        assertEquals("compact cards", true, getField(panel, "compactCards"))
    }
}
