package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.HttpGet
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.ArenaGeo
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BattleCard
import com.husarp.ankigo.ui.BarView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.InfoBoxStyle
import com.husarp.ankigo.ui.PanelFit
import com.husarp.ankigo.ui.StageGeo
import com.husarp.ankigo.ui.Ui
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Round 9 D1: the info boxes' automatic rule in every host that shows a battle picture: the Battle tab (upright + sideways), the
 * Study panel (dragged tall, the 220 dp Full preset, the 180 dp Strip), a narrow split-screen strip, the Pokémon tab's card, the
 * floating battle window and picture-in-picture, with generated stand-in pictures ([BackdropScenes]) and FakeGame's generated
 * sprites, light and dark. Every shot checks, on the DRAWN pixels and not on the plan's numbers: the pixels the two boxes (and the
 * NEW sticker) draw are found by drawing the arena with and without them; each sprite's art box is found from its own drawn
 * pixels (alpha above 16); then (when the plan says clear) no box pixel lies on an art box, every box keeps its gap from both
 * art boxes (the real, scaled card rectangles), both boxes lie inside the arena, and the plan's own art box is the measured one.
 * The table of every combination goes to build/shots/r9-boxes-<host>.txt. PNGs: r9-boxes-<host>-<background>-<size>-<theme>.
 */
class R9BoxesShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() {
        Backdrops.forget()
        BattleShotKit.reset()
    }

    private val ids = listOf("forest", "mine", "canyon", "night-forest")
    private val noGet: HttpGet = { _, _ -> null }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o: Any, name: String): T {
        var c: Class<*>? = o.javaClass
        while (c != null) {
            try { return c.getDeclaredField(name).also { it.isAccessible = true }.get(o) as T } catch (_: NoSuchFieldException) { c = c.superclass }
        }
        throw NoSuchFieldException(name)
    }

    private fun bg(id: String) = BackdropCatalog.byId(id)!!

    private fun show(ctx: Context, arena: ArenaView, bg: Backdrop) {
        BackdropScenes.write(Sprites.dir(ctx), bg, 0.5f)
        val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false, noGet)!!
        Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
        field<ArenaBackdrop>(arena, "backdrop").request(bg, false)
    }

    private class Scene(val root: View, val arena: ArenaView)

    // ---- the hosts ---------------------------------------------------------------------------------------------------------------

    private fun full(ctx: Context, b: Backdrop): Scene {
        val built = BattleShotKit.bound(ctx)
        show(ctx, built.arena!!, b)
        return Scene(built.root, built.arena!!)
    }

    /** A panel [wDp] x [hDp] dp at the top-left of the render window (the Study screen's panel presets, a narrow split-screen pane). */
    private fun boxed(ctx: Context, b: Backdrop, wDp: Int, hDp: Int, setup: (BattlePanelView) -> Unit): Scene {
        val p = BattlePanelView(ctx)
        setup(p)
        p.renderAll()
        show(ctx, p.arena, b)
        val frame = FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            addView(p, FrameLayout.LayoutParams(Ui.dp(ctx, wDp), Ui.dp(ctx, hDp), Gravity.TOP or Gravity.LEFT))
        }
        return Scene(frame, p.arena)
    }

    private fun studyTall(ctx: Context, b: Backdrop) = boxed(ctx, b, 412, 400) { it.setStudyLayout(false, false) }
    private fun studyShort(ctx: Context, b: Backdrop) = boxed(ctx, b, 412, 220) { it.setStudyLayout(false, false) }
    private fun strip(ctx: Context, b: Backdrop) = boxed(ctx, b, 412, 180) { it.setStudyLayout(true, false) }
    private fun narrow(ctx: Context, b: Backdrop) = boxed(ctx, b, 206, 170) { it.setStudyLayout(true, false) }

    private fun pip(ctx: Context, b: Backdrop): Scene {
        val p = BattlePanelView(ctx).apply { setCompact(true) }
        p.renderAll()
        show(ctx, p.arena, b)
        return Scene(p, p.arena)
    }

    private fun floating(ctx: Context, b: Backdrop): Scene {
        val self = object : android.content.ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        val bind = v.javaClass.getDeclaredMethod("bind").also { it.isAccessible = true }
        bind.invoke(v)
        val p: BattlePanelView = field(v, "panel")
        p.renderAll()
        show(ctx, p.arena, b)
        val window: Any = field(v, "window")
        return Scene(field(window, "root"), p.arena)
    }

    private fun card(ctx: Context, b: Backdrop): Scene {
        val p = BattlePanelView(ctx).apply { setCardLook(true) }
        p.renderAll()
        show(ctx, p.arena, b)
        val h = BattleCard.panelDp(ctx.resources.configuration.screenHeightDp.toFloat())
        val frame = FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            addView(p, FrameLayout.LayoutParams(Ui.dp(ctx, ctx.resources.configuration.screenWidthDp - 32), Ui.dp(ctx, h), Gravity.TOP or Gravity.CENTER_HORIZONTAL).also { it.topMargin = Ui.dp(ctx, 16) })
        }
        return Scene(frame, p.arena)
    }

    // ---- pixels ------------------------------------------------------------------------------------------------------------------

    private fun render(v: View): Bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }

    private class PxBox(val l: Int, val t: Int, val r: Int, val b: Int) {
        val empty get() = r <= l || b <= t
        override fun toString() = "($l,$t)-($r,$b)"
    }

    /** The bounds of the visible pixels (alpha above 16) of the view drawn alone, in its own px; null = nothing drawn. */
    private fun visibleBounds(v: View): PxBox? {
        val bmp = render(v)
        var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var r = -1; var b = -1
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) if ((bmp.getPixel(x, y) ushr 24) > 16) {
            if (x < l) l = x; if (x + 1 > r) r = x + 1
            if (y < t) t = y; if (y + 1 > b) b = y + 1
        }
        return if (r < 0) null else PxBox(l, t, r, b)
    }

    /** The art box of a sprite in the arena's px: its own drawn pixels moved to where its holder sits. */
    private fun measuredArt(holder: View, spr: SpriteView): PxBox {
        val v = visibleBounds(spr) ?: throw AssertionError("a sprite draws nothing")
        val ox = holder.left + spr.left
        val oy = holder.top + spr.top
        return PxBox(ox + v.l, oy + v.t, ox + v.r, oy + v.b)
    }

    private fun toBox(b: PxBox) = ArenaGeo.Box(b.l.toFloat(), b.t.toFloat(), (b.r - b.l).toFloat(), (b.b - b.t).toFloat())

    /** A view's rectangle as drawn: its box scaled from its top-left corner. */
    private fun drawnRect(v: View) = ArenaGeo.Box(v.left.toFloat(), v.top.toFloat(), v.width * v.scaleX, v.height * v.scaleY)

    /** Distance between two rectangles (0 when they touch or overlap). */
    private fun dist(a: ArenaGeo.Box, b: ArenaGeo.Box): Float {
        val dx = max(0f, max(a.x - b.right, b.x - a.right))
        val dy = max(0f, max(a.y - b.bottom, b.y - a.bottom))
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    /** How many pixels of [on] differ between the arena drawn with and without the boxes, inside [box] (px). */
    private fun diffInside(with: Bitmap, without: Bitmap, box: PxBox): Int {
        var n = 0
        for (y in max(0, box.t) until min(with.height, box.b)) for (x in max(0, box.l) until min(with.width, box.r)) if (with.getPixel(x, y) != without.getPixel(x, y)) n++
        return n
    }

    private class Row(val line: String, val clear: Boolean, val sp: Float)

    /** The smallest text a box draws, in sp on screen: every visible TextView of the cards and the bars' inside labels at (design size x the box's scale x the panel's scale). */
    private fun smallestSp(arena: ArenaView, vararg views: View): Float {
        val d = arena.resources.displayMetrics.density
        var lowest = Float.MAX_VALUE
        fun walk(v: View, k: Float) {
            if (v.visibility != View.VISIBLE) return
            if (v is TextView && v.text.isNotEmpty()) lowest = min(lowest, v.textSize / d * k * arena.panelScale)
            if (v is BarView) { val l = v.labelSp(); if (l > 0f) lowest = min(lowest, l * k * arena.panelScale) }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), k)
        }
        for (v in views) walk(v, v.scaleX)
        return lowest
    }


    private fun check(tag: String, sc: Scene, bg: Backdrop): Row {
        val arena = sc.arena
        val plan = arena.stagePlanNow ?: throw AssertionError("$tag: no plan (plain mode?)")
        val d = arena.resources.displayMetrics.density
        val wildHolder: View = field(arena, "enemyHolder")
        val playerHolder: View = field(arena, "playerHolder")
        val wildCard: View = field(arena, "enemyBlock")
        val playerCard: View = field(arena, "playerCard")
        val newTag: View = field(arena, "newTag")
        assertTrue("$tag: arena has a size", arena.width > 0 && arena.height > 0)

        // the sprites' REAL art boxes, from their own drawn pixels
        val wa = measuredArt(wildHolder, arena.enemySprite)
        val pa = measuredArt(playerHolder, arena.playerSprite)
        // the plan's art box (mapped from the bitmap's bounds) is the measured one, within a couple of px
        for ((name, plan0, meas) in listOf(Triple("wild", plan.wildArt, wa), Triple("ours", plan.playerArt, pa))) {
            assertEquals("$tag: $name art left", meas.l.toFloat(), plan0.x * d, 3f)
            assertEquals("$tag: $name art top", meas.t.toFloat(), plan0.y * d, 3f)
            assertEquals("$tag: $name art right", meas.r.toFloat(), plan0.right * d, 3f)
            assertEquals("$tag: $name art bottom", meas.b.toFloat(), plan0.bottom * d, 3f)
        }

        // the pixels the boxes (and the sticker) draw: the arena with them minus the arena without them
        val with = render(arena)
        val saved = listOf(wildCard.visibility, playerCard.visibility, newTag.visibility)
        wildCard.visibility = View.INVISIBLE; playerCard.visibility = View.INVISIBLE; newTag.visibility = View.INVISIBLE
        val without = render(arena)
        wildCard.visibility = saved[0]; playerCard.visibility = saved[1]; newTag.visibility = saved[2]
        val wcr = drawnRect(wildCard)
        val pcr = drawnRect(playerCard)
        var onWild = 0; var onOurs = 0
        for (art in listOf(wa, pa)) { val n = diffInside(with, without, art); if (art === wa) onWild = n else onOurs = n }
        val minGap = min(min(dist(wcr, toBox(wa)), dist(wcr, toBox(pa))), min(dist(pcr, toBox(wa)), dist(pcr, toBox(pa)))) / d
        val between = dist(wcr, pcr) / d
        if (plan.clear) {
            assertEquals("$tag: box pixels on the wild Pokémon's art box", 0, onWild)
            assertEquals("$tag: box pixels on our Pokémon's art box", 0, onOurs)
            // the gap from both art boxes (the real, drawn card rectangles; 2 px of slack for rounding to pixels) and between the two boxes
            assertTrue("$tag: gap to an art box ${minGap} dp, wanted ${plan.gap}", minGap >= plan.gap - 2f / d)
            assertTrue("$tag: gap between the boxes $between dp, wanted ${plan.gap}", between >= plan.gap - 2f / d)
            for ((name, r) in listOf("wild" to wcr, "ours" to pcr)) {
                assertTrue("$tag: $name box inside the arena ($r of ${arena.width} x ${arena.height})",
                    r.x >= -1f && r.y >= -1f && r.right <= arena.width + 1f && r.bottom <= arena.height + 1f)
            }
        }
        assertFalse("$tag: never an empty plan", plan.wildCard.w <= 0f)
        // a box is as high as its card really is: the model's height the plan used is the height of the laid out content (the card is given
        // it whole: under k = 1 a wrapped card used to be cut off at the panel's edge)
        val real = FloatArray(2)
        for ((i, card) in listOf(wildCard, playerCard).withIndex()) {
            val g = card as ViewGroup
            val given = g.height
            g.measure(View.MeasureSpec.makeMeasureSpec(g.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            real[i] = g.measuredHeight / d
            assertEquals("$tag: ${if (i == 0) "wild" else "our"} box height = its content's (model ${given / d} dp)", given / d, real[i], 2f)
            assertTrue("$tag: ${if (i == 0) "wild" else "our"} card shows all of its content (content ${g.measuredHeight}, given $given)", g.measuredHeight <= given + 2 * d)
        }
        assertEquals("$tag: wild box height = the plan's", plan.wildCard.h / plan.k, wildCard.height / d, 1.5f)
        assertEquals("$tag: our box height = the plan's", plan.playerCard.h / plan.k, playerCard.height / d, 1.5f)
        // no text of a box (nor the NEW sticker) under 8 sp on screen
        val sp = smallestSp(arena, wildCard, playerCard, newTag)
        assertTrue("$tag: smallest text ${sp} sp on screen", sp >= PanelFit.MIN_SP - 0.05f)
        val line = "%-11s %5.0f x %-4.0f dp  scale %.2f  sprites x%.1f  boxes x%.2f%s  %s  level %d  gap %.1f dp (min %.1f, between %.1f)  box px on art: %d / %d  heights real/given: wild %.1f/%.1f ours %.1f/%.1f  smallest text %.2f sp".format(
            bg.id, arena.width / d, arena.height / d, arena.panelScale, plan.spriteScale, plan.k, if (plan.reduced) " reduced" else "", if (plan.clear) "clear   " else "FALLBACK", plan.level,
            plan.gap, minGap, between, onWild, onOurs, real[0], wildCard.height / d, real[1], playerCard.height / d, sp)
        return Row(line, plan.clear, sp)
    }

    private fun host(host: String, size: Size, build: (Context, Backdrop) -> Scene): List<Row> {
        val rows = ArrayList<Row>()
        for (style in InfoBoxStyle.entries) {
            val table = ArrayList<String>()
            for (id in ids) for (theme in Theme.entries) {
                paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
                val ctx = Shots.files(paparazzi.context)
                FakeGame.install(ctx)
                AppPrefs.setInfoBoxStyle(ctx, style)
                val sc = build(ctx, bg(id))
                val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
                val name = "r9-boxes-$host-$id-${size.name.lowercase()}-${theme.name.lowercase()}-${style.name.lowercase()}"
                paparazzi.snapshot(frame, name, 1000)
                val row = check("$name", sc, bg(id))
                if (theme == Theme.LIGHT) { table += row.line; rows += row }
            }
            File(Shots.outDir, "r9-boxes-$host-${style.name.lowercase()}.txt").writeText("$host @ ${size.name} (${size.wDp} x ${size.hDp} dp), ${style.label}\n" + table.joinToString("\n") + "\n")
        }
        return rows
    }

    @Test fun battleTabPhone() { host("battle-phone", Size.PHONE, ::full) }
    @Test fun battleTabLand() { host("battle-land", Size.LAND, ::full) }
    @Test fun studyPanelTall() { host("study-tall", Size.SPLIT, ::studyTall) }
    @Test fun studyPanelFullPreset() { host("study-short", Size.SPLIT, ::studyShort) }
    @Test fun studyStrip() { host("strip", Size.SPLIT, ::strip) }
    @Test fun splitScreenNarrow() { host("split-narrow", Size.SPLIT, ::narrow) }
    @Test fun pokemonTabCard() { host("card", Size.PHONE, ::card) }
    @Test fun floatingBattle() { host("float", Size.FLOATBATTLE, ::floating) }
    @Test fun pictureInPicture() { host("pip", Size.PIP_169, ::pip) }

    // ---- the real art box from made-up bitmaps ---------------------------------------------------------------------------------------

    /** A 96 x 96 frame with a solid block on columns [x0]..[x1] and rows [y0]..[y1] (inclusive), the rest transparent. */
    private fun block(x0: Int, y0: Int, x1: Int, y1: Int): Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { b ->
        for (y in y0..y1) for (x in x0..x1) b.setPixel(x, y, 0xFF3366CC.toInt())
    }

    @Test fun theRealArtBoxComesFromTheBitmapsVisiblePixelsAndFollowsAMirrorAndAnAnimation() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        // an off-centre sprite: columns 10..39, rows 20..70 of a 96 px frame, drawn in a 288 px frame (3 px per sprite pixel)
        val img = com.husarp.ankigo.data.SpriteImage(listOf(block(10, 20, 39, 70)), intArrayOf(100))
        val v = SpriteView(ctx).apply { byFrame = true; anchorFeet = true; setImage(img) }
        val a = v.artShare(288f)!!
        // 25 empty rows under the feet hang below the frame: the feet (row 70's bottom) are on the frame's bottom
        assertEquals(30f / 288f, a.x, 0.0005f); assertEquals(90f / 288f, a.w, 0.0005f)
        assertEquals(135f / 288f, a.y, 0.0005f); assertEquals(153f / 288f, a.h, 0.0005f)
        assertEquals(1f, a.y + a.h, 0.0005f)
        // mirrored (our front sprite turned to face the wild one): the same box on the other side
        v.mirror = true
        val m = v.artShare(288f)!!
        assertEquals((96 - 40) * 3f / 288f, m.x, 0.0005f); assertEquals(a.w, m.w, 0.0005f); assertEquals(a.y, m.y, 0.0005f)
        v.mirror = false
        // an animation: the union of its frames (this one also reaches higher and further right)
        val anim = com.husarp.ankigo.data.SpriteImage(listOf(block(10, 20, 39, 70), block(50, 5, 59, 60)), intArrayOf(100, 100))
        v.setImage(anim)
        val u = v.artShare(288f)!!
        assertEquals(30f / 288f, u.x, 0.0005f); assertEquals((60 - 10) * 3f / 288f, u.w, 0.0005f)
        assertEquals(1f, u.y + u.h, 0.0005f); assertEquals((71 - 5) * 3f / 288f, u.h, 0.0005f)
        // the plan keeps clear of exactly that box: the measured drawn pixels agree (the sprite is drawn into a view that size)
        val drawn = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888)
        v.measure(View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 288, 288)
        v.setImage(img)
        v.draw(Canvas(drawn))
        val box = visibleBounds(v)!!
        val b1 = v.artShare(288f)!!
        assertEquals(box.l.toFloat(), b1.x * 288f, 1.5f); assertEquals(box.t.toFloat(), b1.y * 288f, 1.5f)
        assertEquals(box.r.toFloat(), (b1.x + b1.w) * 288f, 1.5f); assertEquals(box.b.toFloat(), (b1.y + b1.h) * 288f, 1.5f)
        // a placeholder circle is its own box, a blank image falls back to the whole bitmap
        val ph = SpriteView(ctx).apply { byFrame = true; setPlaceholder("A", 0xFF888888.toInt()) }.artShare(288f)!!
        assertEquals(1f, ph.y + ph.h, 0f); assertEquals((1f - ph.w) / 2f, ph.x, 0.0001f)
    }

    // ---- the NEW sticker with a box on the panel's edge, and a box under 1 ----

    @Test fun theNewStickerStaysInsideTheArenaWhenTheWildBoxTouchesTheEdgeAndScalesWithABoxUnderOne() {
        for ((host, build) in listOf<Pair<String, (Context, Backdrop) -> Scene>>("battle" to ::full, "strip" to ::strip)) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(if (host == "battle") Size.PHONE else Size.SPLIT, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val sc = build(ctx, bg("forest"))
            sc.arena.setNewTag(true); sc.arena.setEnemyCardShown(true)
            val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "r9-boxes-newtag-$host-${theme.name.lowercase()}", 1000)
            val card: View = field(sc.arena, "enemyBlock")
            val tag: View = field(sc.arena, "newTag")
            val plan = sc.arena.stagePlanNow!!
            assertEquals("$host: the sticker is shown", View.VISIBLE, tag.visibility)
            assertTrue("$host: the sticker's corner is inside the arena (${tag.left}, ${tag.top})", tag.left >= 0 && tag.top >= 0)
            // the sticker scales with the card, but its 8 sp text does not go under the readable size (R9 wave 3)
            assertTrue("$host: the sticker follows the card (${tag.scaleX} vs ${card.scaleX})", tag.scaleX >= card.scaleX - 0.0001f)
            assertTrue("$host: the sticker's text is readable (${8f * tag.scaleX * sc.arena.panelScale} sp)", 8f * tag.scaleX * sc.arena.panelScale >= PanelFit.MIN_SP - 0.05f)
            if (host == "strip") assertTrue("$host: the box is under its design size (${plan.k})", plan.k < 1f)
            // the wild box touches the arena's top edge in the tall Battle tab: the sticker is pushed in to the edge, over the card's corner
            if (plan.wildCard.y < 0.01f) assertEquals("$host: sticker on the top edge", 0, tag.top)
            if (plan.wildCard.x < 0.01f) assertEquals("$host: sticker on the left edge", 0, tag.left)
        }
    }

    // ---- the rule on the real arena ------------------------------------------------------------------------------------------------

    @Test fun theArenaFeedsTheRuleItsPanelScaleFloorAndFrameInset() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val sc = studyShort(ctx, bg("forest"))
        val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "r9-boxes-feed", 1000)
        val arena = sc.arena
        val plan = arena.stagePlanNow!!
        val d = arena.resources.displayMetrics.density
        // the 220 dp Full preset is drawn smaller than 1 (PanelFit), and the arena is told so
        assertTrue("panel scale ${arena.panelScale}", arena.panelScale < 1f && arena.panelScale > 0.3f)
        val onScreen = arena.height / d * arena.panelScale
        assertEquals("the gap is the rule's gap", StageGeo.gap(onScreen, arena.panelScale), plan.gap, 0.01f)
        assertTrue("small boxes: ${plan.k}", plan.k < 1f)
        // with a battle frame 6 dp deep, every box stays inside it
        arena.frameInsetDp = 6f
        val w = arena.width; val h = arena.height
        arena.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        arena.layout(0, 0, w, h)
        val p2 = arena.stagePlanNow!!
        assertNotNull(p2)
        for (b in listOf(p2.wildCard, p2.playerCard)) {
            assertTrue("inside the frame: ${b.x},${b.y} ${b.w}x${b.h}", b.x >= 6f - 0.01f && b.y >= 6f - 0.01f && b.right <= w / d - 6f + 0.01f && b.bottom <= h / d - 6f + 0.01f)
        }
    }
}
