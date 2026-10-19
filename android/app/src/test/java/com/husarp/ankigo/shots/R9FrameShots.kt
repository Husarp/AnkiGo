package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattleCard
import com.husarp.ankigo.ui.BattleFrame
import com.husarp.ankigo.ui.BattleFrameDrawable
import com.husarp.ankigo.ui.BattleFrameStyle
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.HomeView
import com.husarp.ankigo.ui.LogKind
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R9 D2: the battle frame setting. Shots r9fr-*: each frame on the Battle tab (phone), Wood in every other host (Study
 * panel split / strip, landscape, floating window, picture-in-picture, the Pokémon tab's battle card), the Settings card with the
 * previews; light + dark. Plus pixel checks of the drawable (crisp squares, mirrored corners, None draws nothing).
 */
class R9FrameShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    private fun frame(ctx: Context, s: BattleFrameStyle) {
        AppPrefs.setBattleFrame(ctx, s)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
    }

    private fun shoot(name: String, size: Size, style: BattleFrameStyle, build: (Context) -> View) =
        paparazzi.shootWith(name, listOf(size)) { ctx -> frame(ctx, style); build(ctx) }

    // ---------------------------------------------------------------- hosts

    private fun battleTab(ctx: Context): View = BattleShotKit.bound(ctx).root

    private fun small(ctx: Context): View {
        val p = BattlePanelView(ctx)
        p.setCompact(true); p.renderAll()
        p.setLog(LogKind.GOOD, "Good", "Sparkmouse used {{Thunderbolt}}!", false)
        return p
    }

    private fun pip(ctx: Context): View {
        val b = BattleShotKit.bound(ctx)
        BattleShotKit.pip(b)
        return b.root
    }

    private fun studyPanel(ctx: Context, strip: Boolean): View {
        val p = BattlePanelView(ctx)
        p.setStudyLayout(strip, false); p.renderAll()
        p.setLog(LogKind.GOOD, "Good", "Sparkmouse used {{Thunderbolt}}!", false)
        return p
    }

    private fun study(ctx: Context, heightMode: Int): View {
        val act = attach(com.husarp.ankigo.StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        setField(act, "heightMode", heightMode)
        val root = act.buildContent()
        setField(act, "card", com.husarp.ankigo.Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>")
        setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic")
        setField(act, "cardName", "Card 1")
        setField(act, "flipped", false)
        setField(act, "locked", false)
        (getField(act, "subtitle") as android.widget.TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as BattlePanelView
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        return root
    }

    private class FakeLive : BattleCard.Live {
        override var paused = false
        override fun hold(ctx: Context, owner: Any) {}
        override fun release(ctx: Context, owner: Any) {}
        override fun addSink(s: LiveSession.Sink) {}
        override fun removeSink(s: LiveSession.Sink) {}
        override fun addPauseListener(l: (Boolean) -> Unit) {}
        override fun removePauseListener(l: (Boolean) -> Unit) {}
    }

    private class CardHost : BattleCard.Host {
        override fun expand() {}
        override fun popOut() {}
    }

    private fun card(ctx: Context): View {
        val c = BattleCard(ctx, CardHost(), FakeLive())
        c.start()
        return FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), 0)
            addView(c.view, FrameLayout.LayoutParams(-1, -2))
        }
    }

    // ---------------------------------------------------------------- the six frames on the Battle tab

    @Test fun simple() = shoot("r9fr-phone-simple", Size.PHONE, BattleFrameStyle.SIMPLE, ::battleTab)
    @Test fun wood() = shoot("r9fr-phone-wood", Size.PHONE, BattleFrameStyle.WOOD, ::battleTab)
    @Test fun stone() = shoot("r9fr-phone-stone", Size.PHONE, BattleFrameStyle.STONE, ::battleTab)
    @Test fun gold() = shoot("r9fr-phone-gold", Size.PHONE, BattleFrameStyle.GOLD, ::battleTab)
    @Test fun ice() = shoot("r9fr-phone-ice", Size.PHONE, BattleFrameStyle.ICE, ::battleTab)
    @Test fun metal() = shoot("r9fr-phone-metal", Size.PHONE, BattleFrameStyle.METAL, ::battleTab)
    @Test fun none() = shoot("r9fr-phone-none", Size.PHONE, BattleFrameStyle.NONE, ::battleTab)

    // ---------------------------------------------------------------- Wood in every other host

    private val w = BattleFrameStyle.WOOD
    @Test fun landBattle() = shoot("r9fr-land-battle", Size.LAND, w, ::battleTab)
    @Test fun studySplit() = shoot("r9fr-study-split", Size.SPLIT, w) { study(it, 0) }
    @Test fun studyStrip() = shoot("r9fr-study-strip", Size.SPLIT, w) { study(it, 1) }
    @Test fun studyPanelTall() = shoot("r9fr-study-panel", Size.SPLIT, w) { studyPanel(it, false) }
    @Test fun studyPanelStrip() = shoot("r9fr-study-panelstrip", Size.PIP_169M, w) { studyPanel(it, true) }
    @Test fun studyHomeView() = shoot("r9fr-study-homeview", Size.SPLIT, w) { BattleShotKit.bound(it, HomeView.STUDY).root }
    @Test fun floatBattle() = shoot("r9fr-floatbattle", Size.FLOATBATTLE, w, ::small)
    @Test fun pipWindow() = shoot("r9fr-pip", Size.PIP, w, ::pip)
    @Test fun teamCard() = shoot("r9fr-teamcard", Size.PHONE, w, ::card)
    @Test fun teamCardSplit() = shoot("r9fr-teamcard", Size.SPLIT, BattleFrameStyle.GOLD, ::card)

    // ---------------------------------------------------------------- the Settings card

    @Test fun settingsCard() {
        for (picked in listOf(BattleFrameStyle.NONE, BattleFrameStyle.ICE)) {
            paparazzi.shootWith("r9fr-settings-${picked.name.lowercase()}", listOf(Size.PHONE)) { ctx ->
                AppPrefs.setBattleFrame(ctx, picked)
                val c = ShotCtx(ctx)
                val page = SettingsPage(c, NoHost)
                val m = SettingsPage::class.java.getDeclaredMethod("battleFrameCard").also { it.isAccessible = true }
                val card = m.invoke(page) as View
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                    setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), 0)
                    addView(com.husarp.ankigo.ui.Form.sect(ctx, "Battle frame"))
                    addView(card, LinearLayout.LayoutParams(-1, -2))
                }
            }
        }
    }

    // ---------------------------------------------------------------- the panel follows the setting

    private fun panelWith(ctx: Context, s: BattleFrameStyle): BattlePanelView {
        AppPrefs.setBattleFrame(ctx, s)
        val p = BattlePanelView(ctx)
        val d = ctx.resources.displayMetrics.density
        p.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((600 * d).toInt(), View.MeasureSpec.EXACTLY))
        p.layout(0, 0, p.measuredWidth, p.measuredHeight)
        return p
    }

    private fun ctx(): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        return c
    }

    @Test fun theArenaGetsTheFrameAndItsInsetFromThePanel() {
        val c = ctx()
        val d = c.resources.displayMetrics.density
        val p = panelWith(c, BattleFrameStyle.NONE)
        assertNull(p.arena.foreground)
        assertEquals(0f, p.arena.frameInsetDp, 0f)

        AppPrefs.setBattleFrame(c, BattleFrameStyle.GOLD)
        p.refreshPrefs()
        p.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((600 * d).toInt(), View.MeasureSpec.EXACTLY))
        val fg = p.arena.foreground
        assertTrue("the frame is the arena's foreground", fg is BattleFrameDrawable)
        assertEquals(BattleFrameStyle.GOLD, (fg as BattleFrameDrawable).style)
        val scale = p.currentFit.scale
        val u = BattleFrame.unitPx(d, scale)
        assertEquals(u, fg.unit)
        assertEquals("inset = thickness in virtual dp", 3 * u / d, p.arena.frameInsetDp, 0.001f)
        assertTrue("a frame is 2 dp (+/- rounding) on screen", Math.abs(u * scale / d - 2f) < 0.5f)

        AppPrefs.setBattleFrame(c, BattleFrameStyle.NONE)
        p.refreshPrefs()
        p.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((600 * d).toInt(), View.MeasureSpec.EXACTLY))
        assertNull(p.arena.foreground)
        assertEquals(0f, p.arena.frameInsetDp, 0f)
    }

    @Test fun anUnknownSavedFrameMeansNone() {
        val c = ctx()
        c.applicationContext.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE).edit().putString("battleFrame", "LEAF").commit()
        assertEquals(BattleFrameStyle.NONE, AppPrefs.battleFrame(c))
    }

    // ---------------------------------------------------------------- the drawable

    private fun drawn(s: BattleFrameStyle, unit: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val dr = BattleFrameDrawable(s, unit)
        dr.setBounds(0, 0, w, h)
        dr.draw(Canvas(bmp))
        return bmp
    }

    @Test fun noneDrawsNothing() {
        val b = drawn(BattleFrameStyle.NONE, 4, 100, 60)
        for (y in 0 until 60) for (x in 0 until 100) assertEquals(0, b.getPixel(x, y))
    }

    @Test fun goldIsCrispSquaresWithMirroredCorners() {
        val b = drawn(BattleFrameStyle.GOLD, 4, 100, 60)
        val dark = 0xFF5A3D06.toInt()
        assertEquals("top-left corner", dark, b.getPixel(0, 0))
        assertEquals("bottom-right corner is the same pattern mirrored", dark, b.getPixel(99, 59))
        assertEquals("the white glint of the top-left ruby plate", 0xFFFFFFFF.toInt(), b.getPixel(9, 9))
        assertEquals("... mirrored into the bottom-right", 0xFFFFFFFF.toInt(), b.getPixel(89, 49))
        assertEquals("... and the top-right", 0xFFFFFFFF.toInt(), b.getPixel(90, 9))
        assertEquals("top edge, depth 1, cell 10 (no shine)", 0xFFF2C94C.toInt(), b.getPixel(41, 5))
        assertEquals("top edge, depth 1, cell 12 (shine)", 0xFFFFF3B8.toInt(), b.getPixel(49, 5))
        assertEquals("bottom edge outer line", dark, b.getPixel(41, 59))
        assertEquals("left edge outer line", dark, b.getPixel(0, 30))
        assertEquals("right edge outer line", dark, b.getPixel(99, 30))
        assertEquals("inside the frame nothing is drawn", 0, b.getPixel(50, 30))
        for (y in 0 until 60) for (x in 0 until 100) {
            val a = (b.getPixel(x, y) ushr 24)
            assertTrue("pixel $x,$y is opaque or empty, never blended ($a)", a == 0 || a == 255)
        }
    }

    @Test fun aSizeThatIsNoMultipleOfTheSquareStillReachesTheFarEdge() {
        val b = drawn(BattleFrameStyle.GOLD, 4, 103, 62)
        assertEquals(0xFF5A3D06.toInt(), b.getPixel(102, 30))
        assertEquals(0xFF5A3D06.toInt(), b.getPixel(40, 61))
    }

    @Test fun iceHangsIciclesFromTheTopOnly() {
        val b = drawn(BattleFrameStyle.ICE, 4, 200, 120)
        // column 2 holds a 3-long icicle: pixels in rows 8..19 (depth 2..4) around x = 8..11 + 4*? (outside the corner plate at x >= 28)
        // column 13 -> 13 % 11 = 2: the same icicle further right, clear of the corner plates
        val x = 13 * 4 + 1
        assertEquals("pale ice at depth 2", 0xFFDFF6FF.toInt(), b.getPixel(x, 2 * 4 + 1))
        assertEquals("white tip at depth 4", 0xFFFFFFFF.toInt(), b.getPixel(x, 4 * 4 + 1))
        assertEquals("nothing below the tip", 0, b.getPixel(x, 5 * 4 + 1))
        assertEquals("the bottom edge has no icicle", 0, b.getPixel(x, 120 - 3 * 4 + 1))
        assertEquals("nor the left edge", 0, b.getPixel(2 * 4 + 1, 13 * 4 + 1))
    }

    @Test fun redrawingOnlyChangesWhenTheStyleOrUnitDoes() {
        val dr = BattleFrameDrawable(BattleFrameStyle.WOOD, 4)
        var invalidated = 0
        dr.callback = object : android.graphics.drawable.Drawable.Callback {
            override fun invalidateDrawable(who: android.graphics.drawable.Drawable) { invalidated++ }
            override fun scheduleDrawable(who: android.graphics.drawable.Drawable, what: Runnable, `when`: Long) {}
            override fun unscheduleDrawable(who: android.graphics.drawable.Drawable, what: Runnable) {}
        }
        dr.set(BattleFrameStyle.WOOD, 4)
        assertEquals(0, invalidated)
        dr.set(BattleFrameStyle.STONE, 4)
        assertEquals(1, invalidated)
        dr.set(BattleFrameStyle.STONE, 6)
        assertEquals(2, invalidated)
        assertNotNull(dr)
    }
}
