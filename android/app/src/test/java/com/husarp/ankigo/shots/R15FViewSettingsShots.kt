package com.husarp.ankigo.shots

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.study.PanelHeight
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattleFrame
import com.husarp.ankigo.ui.BattleFrameStyle
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.HpBarLook
import com.husarp.ankigo.ui.InfoBoxLook
import com.husarp.ankigo.ui.InfoBoxStyle
import com.husarp.ankigo.ui.LookChooser
import com.husarp.ankigo.ui.LookChoosers
import com.husarp.ankigo.ui.PokemonSize
import com.husarp.ankigo.ui.ViewSettingsPlan
import com.husarp.ankigo.ui.ViewSettingsScreen
import com.husarp.ankigo.ui.ViewSettingsSheet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * ROUND 15 F, PLAN 141: "View settings" is a full-screen page with a live battle preview on top and the rows under it.
 * Shots (light + dark): r15f-battle-* (from the Battle tab), r15f-study-full / -strip / -mini-* (from the Study screen, the preview in the
 * picked panel look), r15f-land-* (landscape: preview left, rows right), r15f-nobattle-*, r15f-before-* / r15f-after-* (the preview after
 * Frame / Info box look / HP bars / Pokémon size / Facing were changed in the rows).
 * Checks: the preview is a real battle panel without a player, takes no touches; the Panel height pick moves the preview only, the host's
 * setter is called once when the page closes; the sizes of the page.
 */
class R15FViewSettingsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    /** The preferences are kept between tests in this JVM: every test starts from the defaults. */
    private fun reset(ctx: Context) {
        AppPrefs.setInfoBoxLook(ctx, InfoBoxLook.TODAY)
        AppPrefs.setInfoBoxStyle(ctx, InfoBoxStyle.ABOVE)
        AppPrefs.setHpBarLook(ctx, HpBarLook.TODAY)
        AppPrefs.setBattleFrame(ctx, BattleFrameStyle.NONE)
        AppPrefs.setPokemonSize(ctx, 100)
        AppPrefs.setPlayerFront(ctx, false)
        AppPrefs.setCompactCards(ctx, false)
        AppPrefs.setCardsUnderOwn(ctx, true)
        AppPrefs.setBackgroundPictures(ctx, true)
    }

    /** The Study screen's host side: [mode] is the panel's look now, every [set] is recorded. */
    private class Host(var mode: Int = 1) {
        val sets = ArrayList<Int>()
        val part get() = ViewSettingsSheet.PanelHeightPart({ mode }) { sets += it; mode = it }
    }

    private fun screen(ctx: Context, host: Host? = null): ViewSettingsScreen {
        reset(ctx)
        return ViewSettingsScreen(ctx, host?.part)
    }

    // ---- the pictures ----------------------------------------------------------------------------------------------------------------

    @Test fun pageFromTheBattleTab() {
        paparazzi.shootWith("r15f-battle", listOf(Size.PHONE, Size.SPLIT, Size.TABLET)) { ctx -> screen(ctx).root }
    }

    @Test fun pageFromTheStudyScreen() {
        paparazzi.shootWith("r15f-study-strip", listOf(Size.PHONE, Size.TABLET)) { ctx -> screen(ctx, Host(1)).root }
        paparazzi.shootWith("r15f-study-full", listOf(Size.PHONE)) { ctx -> screen(ctx, Host(0)).root }
        paparazzi.shootWith("r15f-study-mini", listOf(Size.PHONE)) { ctx -> screen(ctx, Host(2)).root }
    }

    @Test fun pageInLandscape() {
        paparazzi.shootWith("r15f-land", listOf(Size.LAND, Size.TABLET_LAND)) { ctx -> screen(ctx).root }
    }

    @Test fun noBattleYet() {
        paparazzi.shootWith("r15f-nobattle", listOf(Size.PHONE), scenario = FakeGame.Scenario.EMPTY) { ctx ->
            val s = screen(ctx)
            assertTrue("the calm state says so", "No battle yet" in texts(s.root))
            s.root
        }
    }

    @Test fun aBattleShowsNoSuchLine() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        assertFalse("No battle yet" in texts(screen(ctx).root))
    }

    // ---- the preview follows every change --------------------------------------------------------------------------------------------

    private fun find(v: View, test: (View) -> Boolean, out: MutableList<View> = ArrayList()): List<View> {
        if (test(v)) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), test, out)
        return out
    }

    private fun texts(v: View): List<String> = find(v, { it is TextView && it.text.isNotEmpty() }).map { (it as TextView).text.toString() }

    private fun tap(root: View, label: String) {
        var v: View? = find(root, { it is TextView && it.text.toString() == label }).firstOrNull() ?: throw AssertionError("no '$label' on the page")
        while (v != null && !v.isClickable) v = v.parent as? View
        requireNotNull(v) { "'$label' is not tappable" }.performClick()
    }

    private fun press(bar: SeekBar, key: Int, times: Int) = repeat(times) { bar.onKeyDown(key, KeyEvent(KeyEvent.ACTION_DOWN, key)) }

    private val keys = listOf(PokemonSize.KEY, "cardsUnderOwn", InfoBoxStyle.KEY, InfoBoxLook.KEY, HpBarLook.KEY, BattleFrame.KEY, "compactCards", "playerFront", "backgroundPictures")

    /**
     * [page] in a window, and the preference store's change calls for [keys] sent to the preview's watcher once it is on a window (the
     * renderer's store keeps no listeners, so they are called as the real store would); then the picture.
     */
    private fun snapshotFollowing(ctx: Context, page: ViewSettingsScreen, name: String) {
        val watch = getField(page.preview, "facingWatch") as SharedPreferences.OnSharedPreferenceChangeListener
        val prefs = ctx.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE)
        val ping = object : View(ctx) {
            override fun onAttachedToWindow() { super.onAttachedToWindow(); for (k in keys) watch.onSharedPreferenceChanged(prefs, k) }
        }
        val frame = FrameLayout(ctx).apply {
            addView(page.root, FrameLayout.LayoutParams(-1, -1))
            addView(ping, FrameLayout.LayoutParams(1, 1))
        }
        paparazzi.snapshot(frame, name, 1000)
    }

    @Test fun thePreviewFollowsFrameInfoBoxBarsSizeAndFacing() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            // before: the defaults
            val before = screen(ctx)
            val arena0 = before.preview.arena
            assertEquals(0f, arena0.frameInsetDp, 0f)
            assertEquals(InfoBoxLook.TODAY, arena0.infoBoxLook)
            assertEquals(1f, arena0.pokemonSize, 0f)
            snapshotFollowing(ctx, before, "r15f-before-${theme.name.lowercase()}")
            // the rows change five things (a fresh game: the first picture's background request is not left half done)
            FakeGame.install(ctx)
            val page = screen(ctx)
            val root = page.root
            val arena = page.preview.arena
            val info = LookChooser.find(root, LookChoosers.INFO_BOX) as LookChooser<InfoBoxLook>
            val bars = LookChooser.find(root, LookChoosers.BARS) as LookChooser<HpBarLook>
            val frame = LookChooser.find(root, LookChoosers.FRAME) as LookChooser<BattleFrameStyle>
            info.expand(); bars.expand(); frame.expand()
            info.tileFor(InfoBoxLook.GLASS).performClick()
            bars.tileFor(HpBarLook.PIXEL).performClick()
            frame.tileFor(BattleFrameStyle.GOLD).performClick()
            press(find(root, { it is SeekBar }).single() as SeekBar, KeyEvent.KEYCODE_DPAD_RIGHT, 4)
            tap(root, "Front")
            assertEquals(InfoBoxLook.GLASS, AppPrefs.infoBoxLook(ctx))
            assertEquals(120, AppPrefs.pokemonSize(ctx))
            assertTrue(AppPrefs.playerFront(ctx))
            snapshotFollowing(ctx, page, "r15f-after-${theme.name.lowercase()}")
            assertEquals("the wild one is there", View.VISIBLE, (getField(arena, "enemyHolder") as View).visibility)
            assertEquals("info box look", InfoBoxLook.GLASS, arena.infoBoxLook)
            assertEquals("bars look", HpBarLook.PIXEL, arena.hpBarLook)
            assertTrue("frame (${arena.frameInsetDp})", arena.frameInsetDp > 0f)
            assertEquals("size", 1.2f, arena.pokemonSize, 0.0001f)
            assertTrue("facing: the picture was asked for the front", (getField(page.preview, "playerKey") as String?)?.endsWith(":true") == true)
        }
    }

    // ---- the preview is calm ---------------------------------------------------------------------------------------------------------

    @Test fun thePreviewHasNoPlayerAndTakesNoPendingFightEvents() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        GameRepo.keepPendingFight(GameRepo.fightId(), listOf(GameEvent.MainChanged("t2", "Raichu")))
        assertEquals(1, GameRepo.pendingFightEvents().size)
        val page = screen(ctx)
        assertFalse("no EventPlayer: it never takes the waiting events", page.preview.onPendingFight())
        assertTrue(page.preview.hostIdle())
        paparazzi.snapshot(FrameLayout(ctx).apply { addView(page.root, FrameLayout.LayoutParams(-1, -1)) }, "r15f-pending", 1000)
        assertEquals("still waiting for a host that plays them", 1, GameRepo.pendingFightEvents().size)
        GameRepo.takePendingFightEvents()
    }

    /** A touch on the preview reaches nothing in the panel (no turn, no details, no bubbles); the same touch on a bare panel reaches the Pokémon. */
    @Test fun thePreviewTakesNoTouches() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val page = screen(ctx)
        val w = ctx.resources.displayMetrics.widthPixels
        val h = ctx.resources.displayMetrics.heightPixels
        page.root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        page.root.layout(0, 0, w, h)
        val holders = find(page.preview.arena, { it.contentDescription?.startsWith("Your Pokémon") == true || it.contentDescription?.startsWith("Wild") == true })
        assertTrue("the arena has its tap targets", holders.isNotEmpty())
        var seen = 0
        for (holder in holders) holder.setOnTouchListener { _, _ -> seen++; false }   // counts every touch that reaches the Pokémon (it replaces the swipe handling)
        val box = page.preview.parent as ViewGroup
        var proven = 0
        for (holder in holders) {
            val r = android.graphics.Rect(0, 0, holder.width, holder.height)
            page.preview.offsetDescendantRectToMyCoords(holder, r)
            val x = r.exactCenterX(); val y = r.exactCenterY()
            fun touch(to: View, action: Int, t: Long, down: Long) = to.dispatchTouchEvent(MotionEvent.obtain(down, t, action, x, y, 0))
            seen = 0
            // the same touch straight on the bare panel reaches the Pokémon (this is the check that the spot is right) ...
            touch(page.preview, MotionEvent.ACTION_DOWN, 100L, 100L)
            touch(page.preview, MotionEvent.ACTION_UP, 140L, 100L)
            if (seen == 0) continue   // this target is not under the finger here (e.g. the wild one waits for its picture)
            seen = 0
            // ... through the page's box, nothing does
            assertTrue("the box takes the touch itself", touch(box, MotionEvent.ACTION_DOWN, 300L, 300L))
            touch(box, MotionEvent.ACTION_UP, 340L, 300L)
            assertEquals("a touch on '${holder.contentDescription}' reached nothing", 0, seen)
            proven++
        }
        assertTrue("at least one target was proven", proven >= 1)
        assertEquals("Battle preview", box.contentDescription)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, page.preview.importantForAccessibility)
    }

    // ---- the Panel height pick: preview first, the host when the page closes ---------------------------------------------------------

    @Test fun theHostHearsThePanelHeightOnlyWhenThePageCloses() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val host = Host(1)
        val page = screen(ctx, host)
        assertEquals(1, page.heightMode)
        tap(page.root, "Mini")
        assertEquals(2, page.heightMode)
        tap(page.root, "Full")
        assertEquals(0, page.heightMode)
        assertTrue("the host has heard nothing while the page is open", host.sets.isEmpty())
        assertEquals("... and the live panel still has its own look", 1, host.mode)
        page.close()
        assertEquals("called once, with the last pick", listOf(0), host.sets)
        page.close(); page.closed()
        assertEquals("never twice", listOf(0), host.sets)
    }

    @Test fun aPickBackToTheOpeningLookCallsNothing() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val host = Host(1)
        val page = screen(ctx, host)
        tap(page.root, "Mini"); tap(page.root, "Strip")
        page.close()
        assertTrue(host.sets.isEmpty())
    }

    @Test fun theBackArrowAndTheDialogBothCloseOnce() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val host = Host(0)
        val page = screen(ctx, host)
        var closes = 0
        page.onClose = { closes++; page.closed() }   // what the dialog does: dismiss -> its dismiss listener
        val arrow = find(page.root, { it.contentDescription == "Back" }).single()
        tap(page.root, "Strip")
        arrow.performClick()
        assertEquals(1, closes)
        assertEquals(listOf(1), host.sets)
    }

    @Test fun theBattleTabHasNoPanelHeightRow() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        assertFalse("PANEL HEIGHT" in texts(screen(ctx).root))
        assertTrue("PANEL HEIGHT" in texts(screen(ctx, Host(1)).root))
    }

    /** What the panel shows: Full = the whole panel, Strip = the legacy strip, Mini = the action bar alone (the arena is gone). */
    @Test fun thePreviewPanelTakesTheLookOfThePick() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val page = screen(ctx, Host(1))
        val p = page.preview
        fun legacy() = getField(p, "legacy") as Boolean
        assertTrue("strip", legacy()); assertEquals(View.VISIBLE, p.arena.visibility)
        tap(page.root, "Mini")
        assertTrue(legacy()); assertEquals("mini: the action bar alone", View.GONE, p.arena.visibility)
        tap(page.root, "Full")
        assertFalse("full", legacy()); assertEquals(View.VISIBLE, p.arena.visibility)
        tap(page.root, "Strip")
        assertTrue(legacy()); assertEquals(View.VISIBLE, p.arena.visibility)
    }

    // ---- the sizes of the page -------------------------------------------------------------------------------------------------------

    private fun previewDp(ctx: Context, page: ViewSettingsScreen, wDp: Int, hDp: Int): Pair<Float, Float> {
        val d = ctx.resources.displayMetrics.density
        val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
        page.root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        page.root.layout(0, 0, w, h)
        val box = page.preview.parent as View
        return box.width / d to box.height / d
    }

    @Test fun thePreviewIsFixedAtTheTopAndAtMostFortyPercentInPortrait() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val battle = screen(ctx)
        val (w, h) = previewDp(ctx, battle, 412, 869)
        assertEquals(412f, w, 1f)
        assertEquals("40 % of the height", 869f * 0.4f, h, 1f)
        val pageView = (battle.root as ViewGroup).getChildAt(0) as ViewGroup
        assertTrue("fixed at the top, right under the bar", (battle.preview.parent as View).top > 0 && (battle.preview.parent as View).top == pageView.getChildAt(0).bottom)
        // the study screen: the panel's own height for the pick (Full / Strip / Mini), never above 40 %
        for ((mode, dp) in listOf(0 to PanelHeight.preset(false, 0), 1 to PanelHeight.preset(false, 1), 2 to PanelHeight.preset(false, 2))) {
            val page = screen(ctx, Host(mode))
            assertEquals("mode $mode", dp.toFloat(), previewDp(ctx, page, 412, 869).second, 1f)
        }
        // a short window: 40 % wins over the preset
        assertEquals(400 * 0.4f, previewDp(ctx, screen(ctx, Host(0)), 412, 400).second, 1f)
        // the pick moves the preview at once
        val page = screen(ctx, Host(1))
        assertEquals(PanelHeight.preset(false, 1).toFloat(), previewDp(ctx, page, 412, 869).second, 1f)
        tap(page.root, "Mini")
        assertEquals(PanelHeight.preset(false, 2).toFloat(), previewDp(ctx, page, 412, 869).second, 1f)
        tap(page.root, "Full")
        assertEquals(PanelHeight.preset(false, 0).toFloat(), previewDp(ctx, page, 412, 869).second, 1f)
    }

    @Test fun inLandscapeThePreviewIsLeftAndTheRowsScrollOnTheRight() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val page = screen(ctx)
        val d = ctx.resources.displayMetrics.density
        val (pw, ph) = previewDp(ctx, page, 869, 412)
        assertEquals(869f * ViewSettingsPlan.LAND_SHARE, pw, 1f)
        assertTrue("the preview takes the height under the bar ($ph)", ph > 412f * 0.7f)
        val box = page.preview.parent as View
        assertEquals("left", 0, box.left)
        assertTrue("the rows are to its right", page.scroll.left >= box.right)
        assertTrue("... and take the rest of the width", (page.scroll.right - page.scroll.left) / d > 869f * 0.5f)
        assertTrue("... and scroll", page.scroll.getChildAt(0).let { c -> c.measure(View.MeasureSpec.makeMeasureSpec(page.scroll.width, View.MeasureSpec.EXACTLY), 0); c.measuredHeight } > page.scroll.height)
    }

    @Test fun thePlanMaths() {
        assertTrue(ViewSettingsPlan.landscape(869, 412))
        assertFalse(ViewSettingsPlan.landscape(412, 869))
        assertFalse("a square window is stacked", ViewSettingsPlan.landscape(500, 500))
        assertFalse("a split-screen half (412 x 400) too", ViewSettingsPlan.landscape(412, 400))
        assertTrue("a short one (412 x 300) is not", ViewSettingsPlan.landscape(412, 300))
        assertTrue("a tablet turned (1280 x 800)", ViewSettingsPlan.landscape(1280, 800))
        assertEquals(347.6f, ViewSettingsPlan.portraitDp(869f, null, false), 0.01f)
        assertEquals(220f, ViewSettingsPlan.portraitDp(869f, 0, false), 0f)
        assertEquals(180f, ViewSettingsPlan.portraitDp(869f, 1, false), 0f)
        assertEquals(34f, ViewSettingsPlan.portraitDp(869f, 2, false), 0f)
        assertEquals("a tablet's Full panel is taller", 400f, ViewSettingsPlan.portraitDp(1280f, 0, true), 0f)
        assertEquals("never above 40 %", 120f, ViewSettingsPlan.portraitDp(300f, 0, false), 0f)
    }

    @Test fun theRowsKeepTheirNamesAndThePageHasABar() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val t = texts(screen(ctx, Host(1)).root)
        for (s in listOf("View settings", "POKÉMON SIZE", "FACING", "Info box style", "HP / XP bars", "Battle frame", "Stat card under its own Pokémon", "Compact stat cards", "Background pictures", "PANEL HEIGHT", "Full", "Strip", "Mini")) assertTrue("'$s' on the page", s in t)
        assertFalse("no Done button: the arrow and Back close it", "Done" in texts(screen(ctx).root))
        assertSame("a real battle panel", BattlePanelView::class.java, find(screen(ctx).root, { it is BattlePanelView }).single().javaClass)
    }
}
