package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.HttpGet
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.ArenaGeo
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BattleCard
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.Stage
import com.husarp.ankigo.ui.StageFit
import com.husarp.ankigo.ui.Ui
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.min

/**
 * R8-04 "Pokémon belong to the picture": the arena in picture mode in every host, with GENERATED stand-in pictures ([BackdropScenes]:
 * sky, ground, and a light oval centred on each stand point, so a shot shows whether the feet are on the spots) and FakeGame's
 * generated sprites. Four backgrounds of different shapes (forest 1.5:1, reef 3:2 crisp, rocky-path 2.3:1 wide, night-alley dark
 * with a low wild spot) in: the full battle (PHONE, LAND, SPLIT), the study panel and the study strips, picture-in-picture, the
 * floating battle window (zoom .72) and the Pokémon tab's battle card; light and dark. Every shot asserts: the sprite holders'
 * bottom-centre is the stage's feet (within 1 px), the backdrop got the same stage, the holders are the plan's frames, and (when the
 * plan says clear) no card covers a sprite's picture or the other card. The table of every combination (sprite scale, card scale,
 * clear or not) goes to build/shots/r8-04-<host>.txt.
 * PNGs: r8-04-<host>-<background>-<size>-<theme>.
 */
class R804Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() {
        Backdrops.forget()
        BattleShotKit.reset()
    }

    private val ids = listOf("forest", "reef", "rocky-path", "night-alley")
    private val noGet: HttpGet = { _, _ -> null }

    // ---- helpers -----------------------------------------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o: Any, name: String): T {
        var c: Class<*>? = o.javaClass
        while (c != null) {
            try { return c.getDeclaredField(name).also { it.isAccessible = true }.get(o) as T } catch (_: NoSuchFieldException) { c = c.superclass }
        }
        throw NoSuchFieldException(name)
    }

    private fun backdropOf(arena: ArenaView): ArenaBackdrop = field(arena, "backdrop")

    /** The generated picture of [bg] in the cache and in memory, requested on [arena] at once (a swap or a first picture alike). */
    private fun show(ctx: Context, arena: ArenaView, bg: Backdrop) {
        BackdropScenes.write(Sprites.dir(ctx), bg, 0.5f)
        val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false, noGet)!!
        Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
        backdropOf(arena).request(bg, false)
    }

    private class Scene(val root: View, val arena: ArenaView, val zoom: Float = 1f)

    private fun bg(id: String) = BackdropCatalog.byId(id)!!

    private fun full(ctx: Context, b: Backdrop): Scene {
        val built = BattleShotKit.bound(ctx)
        show(ctx, built.arena!!, b)
        return Scene(built.root, built.arena!!)
    }

    private fun panel(ctx: Context, b: Backdrop, setup: (BattlePanelView) -> Unit): Scene {
        val p = BattlePanelView(ctx)
        setup(p)
        p.renderAll()
        show(ctx, p.arena, b)
        return Scene(p, p.arena)
    }

    private fun study(ctx: Context, b: Backdrop, strip: Boolean) = panel(ctx, b) { it.setStudyLayout(strip, false) }

    private fun pip(ctx: Context, b: Backdrop) = panel(ctx, b) { it.setCompact(true) }

    /** The real floating battle window (its own panel, `stageZoom` set by FloatBattleView.bind). */
    private fun floating(ctx: Context, b: Backdrop): Scene {
        val self = object : android.content.ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        val bind = v.javaClass.getDeclaredMethod("bind").also { it.isAccessible = true }
        bind.invoke(v)
        val p: BattlePanelView = field(v, "panel")
        p.renderAll()
        show(ctx, p.arena, b)
        val window: Any = field(v, "window")
        return Scene(field(window, "root"), p.arena, zoom = 0.72f)
    }

    /** The Pokémon tab's battle card: the panel in its card look, [BattleCard.panelDp] high, 16 dp in from the screen's edges. */
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

    private fun box(v: View) = ArenaGeo.Box(v.left.toFloat(), v.top.toFloat(), v.width * v.scaleX, v.height * v.scaleY)

    private fun hostBox(v: View) = ArenaGeo.Box(v.left.toFloat(), v.top.toFloat(), v.width.toFloat(), v.height.toFloat())

    private class Row(val line: String, val clear: Boolean)

    /** The assertions of one shot; the table line. */
    private fun check(tag: String, sc: Scene, bg: Backdrop): Row {
        val arena = sc.arena
        val stage: Stage = arena.stageNow ?: throw AssertionError("$tag: no stage (plain mode?)")
        val plan = arena.stagePlanNow ?: throw AssertionError("$tag: no plan")
        assertSame("$tag: the backdrop got the same stage", stage, field<Stage?>(backdropOf(arena), "stage"))
        assertEquals("$tag: zoom", sc.zoom, arena.stageZoom, 0f)
        assertFalse("$tag: no middle layout in picture mode", arena.middleLayout)
        assertEquals("$tag: card scale", plan.k, arena.cardScale, 0f)
        assertTrue("$tag: feet anchored", arena.enemySprite.anchorFeet && arena.playerSprite.anchorFeet)
        val d = arena.resources.displayMetrics.density
        val wild: View = field(arena, "enemyHolder")
        val player: View = field(arena, "playerHolder")
        // the holders' bottom-centre is the stage's feet; their size is the frame (shrunk by the plan when the cards needed the room)
        for ((name, h, fx, fy, frame) in listOf(
            Foot("wild", wild, stage.wildFeetX, stage.wildFeetY, stage.wildFrame),
            Foot("player", player, stage.playerFeetX, stage.playerFeetY, stage.playerFrame),
        )) {
            assertEquals("$tag: $name feet x", fx * d, (h.left + h.right) / 2f, 1f)
            assertEquals("$tag: $name feet y", fy * d, h.bottom.toFloat(), 1f)
            assertEquals("$tag: $name holder width", frame * plan.spriteScale * d, h.width.toFloat(), 1.5f)
            assertEquals("$tag: $name holder height", frame * plan.spriteScale * d, h.height.toFloat(), 1.5f)
        }
        // the cards: where the plan put them (real sizes, scaled), and clear of both sprites' pictures when the plan says clear
        val wc = box(field(arena, "enemyBlock"))
        val pc = box(field(arena, "playerCard"))
        assertTrue("$tag: picture mode draws by frame", arena.enemySprite.byFrame && arena.playerSprite.byFrame)
        val wa = drawnArt(wild, arena.enemySprite)    // what the sprites REALLY draw (R8-FIX M2), not a flat 85 % of the frame
        val pa = drawnArt(player, arena.playerSprite)
        assertEquals("$tag: wild card x", plan.wildCard.x * d, wc.x, 1f)
        assertEquals("$tag: wild card y", plan.wildCard.y * d, wc.y, 1f)
        assertEquals("$tag: player card x", plan.playerCard.x * d, pc.x, 1f)
        assertEquals("$tag: player card y", plan.playerCard.y * d, pc.y, 1f)
        if (plan.clear) {
            // 1 px of slack for the rounding of the boxes to pixels
            fun hits(a: ArenaGeo.Box, b: ArenaGeo.Box) = a.x < b.right - 1f && b.x < a.right - 1f && a.y < b.bottom - 1f && b.y < a.bottom - 1f
            assertFalse("$tag: wild card on the wild sprite", hits(wc, wa))
            assertFalse("$tag: wild card on our sprite", hits(wc, pa))
            assertFalse("$tag: our card on the wild sprite", hits(pc, wa))
            assertFalse("$tag: our card on our sprite", hits(pc, pa))
            assertFalse("$tag: the cards on each other", hits(wc, pc))
            assertTrue("$tag: wild card inside (${wc.right} x ${wc.bottom} of ${arena.width} x ${arena.height})", wc.x >= -1f && wc.y >= -1f && wc.right <= arena.width + 1f && wc.bottom <= arena.height + 1f)
            assertTrue("$tag: our card inside (${pc.right} x ${pc.bottom} of ${arena.width} x ${arena.height})", pc.x >= -1f && pc.y >= -1f && pc.right <= arena.width + 1f && pc.bottom <= arena.height + 1f)
        }
        val line = "%-11s %5.0f x %-4.0f dp  %s  sprites x%.1f  cards x%.2f  %s  %s  frames %.0f/%.0f dp  drawn %.0f x %.0f / %.0f x %.0f dp".format(
            bg.id, arena.width / d, arena.height / d, if (stage.shrunk) "shrunk" else "crop  ", plan.spriteScale, plan.k,
            if (plan.clear) "clear   " else "FALLBACK", if (plan.bothTop) "both-top" else "        ", stage.wildFrame * plan.spriteScale, stage.playerFrame * plan.spriteScale,
            wa.w / d, wa.h / d, pa.w / d, pa.h / d,
        )
        return Row(line, plan.clear)
    }

    /**
     * The picture [spr] really draws, in the arena's px: bottom-centred in its [holder], [SpriteView.drawnWidth] wide and as high as
     * [SpriteView.drawnHeight] without the empty rows under its feet (the flat 85 % when nothing is shown yet).
     */
    private fun drawnArt(holder: View, spr: SpriteView): ArenaGeo.Box {
        val dw = min(spr.drawnWidth(), holder.width)
        val dh = (spr.drawnHeight() - spr.drawnBottomInset()).coerceIn(0, holder.height)
        if (dw <= 0 || dh <= 0) return hostBox(holder).art()
        return ArenaGeo.Box(holder.left + (holder.width - dw) / 2f, holder.bottom - dh.toFloat(), dw.toFloat(), dh.toFloat())
    }

    private data class Foot(val name: String, val holder: View, val x: Float, val y: Float, val frame: Float)

    /** One host: every background x both themes at [size]; the table goes to r8-04-[host].txt. */
    private fun host(host: String, size: Size, build: (Context, Backdrop) -> Scene): List<Row> {
        val table = ArrayList<String>()
        val rows = ArrayList<Row>()
        for (id in ids) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val sc = build(ctx, bg(id))
            val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
            val name = "r8-04-$host-$id-${size.name.lowercase()}-${theme.name.lowercase()}"
            paparazzi.snapshot(frame, name, 1000)
            val row = check(name, sc, bg(id))
            if (theme == Theme.LIGHT) { table += row.line; rows += row }
        }
        File(Shots.outDir, "r8-04-$host.txt").writeText("$host @ ${size.name} (${size.wDp} x ${size.hDp} dp)\n" + table.joinToString("\n") + "\n")
        return rows
    }

    // ---- the hosts -----------------------------------------------------------------------------------------------------------

    @Test fun fullBattlePhone() { host("full-phone", Size.PHONE, ::full) }
    @Test fun fullBattleLand() { host("full-land", Size.LAND, ::full) }
    @Test fun fullBattleSplit() { host("full-split", Size.SPLIT, ::full) }
    @Test fun studyPanel() { host("study", Size.SPLIT_SHORT) { c, b -> study(c, b, false) } }
    @Test fun studyStripShort() { host("strip-short", Size.SPLIT_SHORT) { c, b -> study(c, b, true) } }
    @Test fun studyStripTiny() { host("strip-tiny", Size.SPLIT_TINY) { c, b -> study(c, b, true) } }
    @Test fun pictureInPicture() { host("pip", Size.PIP_169, ::pip) }
    @Test fun floatingBattle() { host("float", Size.FLOATBATTLE, ::floating) }
    @Test fun pokemonTabCard() { host("card", Size.PHONE, ::card) }

    // ---- behaviour on a bare arena: picture mode switches on and off, swaps, zoom, the study widths ----------------------------

    private fun layOut(arena: ArenaView, wDp: Int, hDp: Int) {
        val w = Ui.dp(arena.context, wDp); val h = Ui.dp(arena.context, hDp)
        arena.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        arena.layout(0, 0, w, h)
    }

    private fun setUp(theme: Theme = Theme.LIGHT): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        return ctx
    }

    private fun rect(v: View) = listOf(v.left, v.top, v.right, v.bottom)

    @Test fun pictureModeSwitchesOnAndOffWithThePicture() {
        val ctx = setUp()
        val arena = ArenaView(ctx)
        arena.configure(0f, false, true)
        val platW: View = field(arena, "platEnemy")
        val wild: View = field(arena, "enemyHolder")
        val player: View = field(arena, "playerHolder")

        // plain: the old layout, ellipses showing, nothing anchored
        layOut(arena, 380, 300)
        assertNull(arena.stagePlanNow); assertNull(arena.stageNow)
        assertEquals(View.VISIBLE, platW.visibility)
        assertFalse(arena.enemySprite.anchorFeet); assertFalse(arena.playerSprite.anchorFeet)
        assertFalse(arena.enemySprite.byFrame); assertFalse(arena.playerSprite.byFrame)
        val plainWild = rect(wild); val plainPlayer = rect(player); val plainScale = arena.cardScale
        assertEquals("the big scale of the plain arena", 8f, arena.enemySprite.scaleDp, 0f)

        // a picture appears: a re-layout is asked for at once, and then the Pokémon stand on its spots
        show(ctx, arena, bg("forest"))
        assertTrue("layout asked for", arena.isLayoutRequested)
        assertEquals(View.GONE, platW.visibility)
        layOut(arena, 380, 300)
        val st1 = arena.stageNow!!; val p1 = arena.stagePlanNow!!
        assertSame(st1, field<Stage?>(backdropOf(arena), "stage"))
        assertTrue(arena.enemySprite.anchorFeet && arena.playerSprite.anchorFeet)
        assertTrue("picture mode draws by frame", arena.enemySprite.byFrame && arena.playerSprite.byFrame)
        assertEquals(st1.wildFeetX * 2f, (wild.left + wild.right) / 2f, 1f)
        assertEquals(st1.playerFeetY * 2f, player.bottom.toFloat(), 1f)
        assertNotEquals(plainWild, rect(wild))

        // the same size again: the very same stage (the backdrop is not re-laid-out for nothing)
        layOut(arena, 380, 300)
        assertSame(st1, arena.stageNow)

        // another picture replaces it: other stand points, so a re-layout
        show(ctx, arena, bg("reef"))
        assertTrue("layout asked for on a swap", arena.isLayoutRequested)
        layOut(arena, 380, 300)
        val st2 = arena.stageNow!!
        assertNotSame(st1, st2)
        assertEquals(st2.wildFeetX * 2f, (wild.left + wild.right) / 2f, 1f)
        assertEquals(st2.wildFeetY * 2f, wild.bottom.toFloat(), 1f)
        assertEquals(st2.playerFeetX * 2f, (player.left + player.right) / 2f, 1f)
        assertEquals(StageFit.fit(bg("reef"), 380f, 300f)!!.cropX, st2.cropX, 0.001f)

        // the floating window's zoom: a re-layout, and the very crop StageFit gives for that zoom
        arena.stageZoom = 0.72f
        assertTrue("layout asked for on a zoom change", arena.isLayoutRequested)
        layOut(arena, 380, 300)
        val z = StageFit.fit(bg("reef"), 380f, 300f, 0.72f)!!
        val st3 = arena.stageNow!!
        assertEquals(z.cropW, st3.cropW, 0.001f); assertEquals(z.cropX, st3.cropX, 0.001f); assertEquals(z.cropY, st3.cropY, 0.001f)
        assertEquals(z.wildFeetY, st3.wildFeetY, 0.001f)
        arena.stageZoom = 1f
        layOut(arena, 380, 300)

        // plain again: the old geometry back, ellipses showing, feet unanchored
        arena.setPlainBackdrop()
        assertTrue("layout asked for when the picture goes", arena.isLayoutRequested)
        assertEquals(View.VISIBLE, platW.visibility)
        layOut(arena, 380, 300)
        assertNull(arena.stagePlanNow); assertNull(arena.stageNow)
        assertEquals(plainWild, rect(wild)); assertEquals(plainPlayer, rect(player)); assertEquals(plainScale, arena.cardScale, 0f)
        assertFalse(arena.enemySprite.anchorFeet); assertFalse(arena.playerSprite.anchorFeet)
        assertFalse("plain mode is back to the old size rule", arena.enemySprite.byFrame || arena.playerSprite.byFrame)
        assertEquals(8f, arena.enemySprite.scaleDp, 0f)
        assertNotNull(p1)
    }

    private fun assertNotSame(a: Any?, b: Any?) = assertTrue("same object", a !== b)

    @Test fun theStudyLayoutsKeepTheirCardWidthsAndCardScaleOne() {
        val ctx = setUp()
        val arena = ArenaView(ctx)
        arena.configure(0f, true, false)
        arena.setCompact(true); arena.setStudyStrip(true)
        val wildCard: View = field(arena, "enemyBlock")
        val playerCard: View = field(arena, "playerCard")
        show(ctx, arena, bg("forest"))
        layOut(arena, 412, 146)
        val p = arena.stagePlanNow!!
        assertEquals(1f, p.k, 0f); assertEquals(1f, arena.cardScale, 0f)
        // the strip's cards: 138 / 142 dp (a 412 dp strip has room), laid out at that width and not scaled
        assertEquals(Ui.dp(ctx, 138), wildCard.layoutParams.width)
        assertEquals(Ui.dp(ctx, 142), playerCard.layoutParams.width)
        assertEquals(1f, wildCard.scaleX, 0f); assertEquals(1f, playerCard.scaleY, 0f)
        // a narrow pane: the cards shrink with it (to half the pane minus 15 dp, never under 96 dp)
        layOut(arena, 220, 146)
        assertEquals(Ui.dp(ctx, 96), wildCard.layoutParams.width)
        assertEquals(Ui.dp(ctx, 96), playerCard.layoutParams.width)

        // the compact study layout (not the strip): 130 dp
        val b = ArenaView(ctx)
        b.configure(0f, true, false)
        b.setCompact(true)
        show(ctx, b, bg("reef"))
        layOut(b, 412, 200)
        assertEquals(Ui.dp(ctx, 130), field<View>(b, "enemyBlock").layoutParams.width)
    }

    @Test fun theNormalLayoutDrawsTheCardsBiggerFromTheirCorner() {
        val ctx = setUp()
        val arena = ArenaView(ctx)
        arena.configure(0f, false, true)
        show(ctx, arena, bg("forest"))
        layOut(arena, 412, 700)
        val p = arena.stagePlanNow!!
        assertTrue("a tall arena draws the cards bigger", p.k > 1f)
        val wildCard: View = field(arena, "enemyBlock")
        assertEquals(p.k, wildCard.scaleX, 0f)
        assertEquals(0f, wildCard.pivotX, 0f); assertEquals(0f, wildCard.pivotY, 0f)
        // laid out at the card's normal width
        assertEquals(ArenaGeo.cards(0f, false, true).wildW * 2f, wildCard.layoutParams.width.toFloat(), 1f)
        assertEquals(p.k, arena.cardScale, 0f)
        assertFalse(arena.middleLayout)
    }

    // ---- SpriteView: the feet on the view's bottom -----------------------------------------------------------------------------

    /** A 96 x 96 frame with a solid body on rows [top]..[bottom]. */
    private fun body(top: Int, bottom: Int, size: Int = 96): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { b ->
        for (y in top..bottom) for (x in 30 until size - 30) b.setPixel(x, y, 0xFF3366CC.toInt())
    }

    private fun lowestVisibleRow(bmp: Bitmap): Int {
        for (y in bmp.height - 1 downTo 0) for (x in 0 until bmp.width) if ((bmp.getPixel(x, y) ushr 24) > 16) return y
        return -1
    }

    private fun drawn(view: SpriteView, side: Int): Bitmap {
        view.measure(View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, side, side)
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(out))
        return out
    }

    @Test fun anchoredFeetLandOnTheViewsBottomAndTheFrameBottomOtherwise() {
        val ctx = setUp()
        // the body ends on row 80 of 96: 15 empty rows under the feet; the view is 2 device px per sprite pixel
        val img = SpriteImage(listOf(body(20, 80)), intArrayOf(100))
        val v = SpriteView(ctx).apply { scaleDp = 1f; setImage(img) }
        val frameBottom = drawn(v, 192)
        assertEquals("unanchored: the frame's bottom is the view's bottom", 81 * 2 - 1, lowestVisibleRow(frameBottom))
        assertEquals(0, v.drawnBottomInset())
        v.anchorFeet = true
        val anchored = drawn(v, 192)
        assertEquals("anchored: the feet are on the view's last row", 191, lowestVisibleRow(anchored))
        assertEquals((95 - 80) * 2, v.drawnBottomInset())
        assertEquals("the drawn size is the same", 192, v.drawnHeight())
        v.anchorFeet = false
        assertEquals(0, v.drawnBottomInset())
        assertEquals(81 * 2 - 1, lowestVisibleRow(drawn(v, 192)))
    }

    @Test fun anAnimationAnchorsOnTheLowestFeetOfAllItsFrames() {
        val ctx = setUp()
        // a hop: the feet are on row 70, 85 and 80 in the three frames, so the lowest, 85, is the anchor
        val img = SpriteImage(listOf(body(10, 70), body(10, 85), body(10, 80)), intArrayOf(100, 100, 100))
        val v = SpriteView(ctx).apply { scaleDp = 1f; anchorFeet = true; setImage(img) }
        // the view shows the first frame (feet on 70): it stands 15 sprite rows higher than the anchor
        assertEquals(191 - (85 - 70) * 2, lowestVisibleRow(drawn(v, 192)))
        assertEquals((95 - 85) * 2, v.drawnBottomInset())
        // an animation that starts on its lowest frame (feet on 85) has them on the last row
        val low = SpriteView(ctx).apply { scaleDp = 1f; anchorFeet = true; setImage(SpriteImage(listOf(body(10, 85), body(10, 70)), intArrayOf(100, 100))) }
        assertEquals(191, lowestVisibleRow(drawn(low, 192)))
    }

    @Test fun aSpriteThatFillsItsFrameIsNotMoved() {
        val ctx = setUp()
        val img = SpriteImage(listOf(body(5, 95)), intArrayOf(100))
        val v = SpriteView(ctx).apply { scaleDp = 1f; anchorFeet = true; setImage(img) }
        assertEquals(0, v.drawnBottomInset())
        assertEquals(191, lowestVisibleRow(drawn(v, 192)))
        // no image: a placeholder is unchanged, and an empty image has no feet to anchor
        val ph = SpriteView(ctx).apply { anchorFeet = true; setPlaceholder("?", 0xFF888888.toInt()) }
        assertEquals(0, ph.drawnBottomInset())
        val blank = SpriteView(ctx).apply { anchorFeet = true; setImage(SpriteImage(listOf(Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)), intArrayOf(100))) }
        assertEquals(0, blank.drawnBottomInset())
    }

    // ---- R8-FIX M1: the size follows the species in picture mode ---------------------------------------------------------------

    private fun solid(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFF3366CC.toInt()) }

    private fun visibleColumns(bmp: Bitmap, row: Int): Int = (0 until bmp.width).count { (bmp.getPixel(it, row) ushr 24) > 16 }

    @Test fun aSmallSpeciesIsSmallInItsFrameAndABigOneBig() {
        val ctx = setUp()
        val small = SpriteView(ctx).apply { scaleDp = 8f; byFrame = true; anchorFeet = true; setImage(SpriteImage(listOf(solid(43, 49)), intArrayOf(100))) }
        val full = SpriteView(ctx).apply { scaleDp = 8f; byFrame = true; anchorFeet = true; setImage(SpriteImage(listOf(solid(96, 96)), intArrayOf(100))) }
        val old = SpriteView(ctx).apply { scaleDp = 8f; anchorFeet = true; setImage(SpriteImage(listOf(solid(43, 49)), intArrayOf(100))) }
        // a 300-px frame wants 300 / 96 = 3.125: pixel art at 3
        val s = drawn(small, 300); val f = drawn(full, 300); val o = drawn(old, 300)
        assertEquals(43 * 3, small.drawnWidth()); assertEquals(49 * 3, small.drawnHeight())
        assertEquals(96 * 3, full.drawnWidth()); assertEquals(96 * 3, full.drawnHeight())
        assertEquals("the visible feet stand on the frame's bottom", 299, lowestVisibleRow(s))
        assertEquals(43 * 3, visibleColumns(s, 299)); assertEquals(96 * 3, visibleColumns(f, 299))
        // before: the plain rule fills the holder whatever the species (whole 6 px per sprite pixel for 43 x 49)
        assertEquals(43 * 6, old.drawnWidth())
        assertEquals(43 * 6, visibleColumns(o, 299))
        // and what the arena plans the cards around is exactly that picture
        val a = small.artShare(300f)!!
        assertEquals(129f / 300f, a.w, 0.0005f); assertEquals(147f / 300f, a.h, 0.0005f)
        val b = full.artShare(300f)!!
        assertEquals(288f / 300f, b.w, 0.0005f)
        // a placeholder: the circle (80 % of the frame, at most 72 dp), both ways
        val ph = SpriteView(ctx).apply { byFrame = true; setPlaceholder("A", 0xFF888888.toInt()) }
        val c = ph.artShare(300f)!!
        assertEquals(144f / 300f, c.w, 0.0005f); assertEquals(c.w, c.h, 0f)
        // nothing shown: no share (the plan then uses its default)
        assertNull(SpriteView(ctx).apply { byFrame = true }.artShare(300f))
    }

    // ---- R8-FIX M2: the cards follow what the sprites really draw ---------------------------------------------------------------

    private fun assertClearOfDrawn(tag: String, arena: ArenaView) {
        val plan = arena.stagePlanNow!!
        val wild: View = field(arena, "enemyHolder"); val player: View = field(arena, "playerHolder")
        val wa = drawnArt(wild, arena.enemySprite); val pa = drawnArt(player, arena.playerSprite)
        val wc = box(field(arena, "enemyBlock")); val pc = box(field(arena, "playerCard"))
        if (!plan.clear) return
        fun hits(a: ArenaGeo.Box, b: ArenaGeo.Box) = a.x < b.right - 1f && b.x < a.right - 1f && a.y < b.bottom - 1f && b.y < a.bottom - 1f
        assertFalse("$tag: wild card on the wild picture", hits(wc, wa)); assertFalse("$tag: wild card on our picture", hits(wc, pa))
        assertFalse("$tag: our card on the wild picture", hits(pc, wa)); assertFalse("$tag: our card on our picture", hits(pc, pa))
    }

    @Test fun anImageOrPlaceholderChangeReplansTheCardsOnlyWhenItsSizeChanged() {
        val ctx = setUp()
        val arena = ArenaView(ctx)
        arena.configure(0f, false, true)
        show(ctx, arena, bg("forest"))
        layOut(arena, 380, 300)
        val p0 = arena.stagePlanNow!!
        assertFalse("nothing asked for after a layout", arena.isLayoutRequested)

        // the wild one's placeholder arrives (a circle, not the flat default): the cards are planned again
        arena.enemySprite.setPlaceholder("Aa", 0xFF2266AA.toInt())
        assertTrue("a new picture size asks for a layout", arena.isLayoutRequested)
        layOut(arena, 380, 300)
        val p1 = arena.stagePlanNow!!
        assertNotSame(p0, p1)
        assertClearOfDrawn("placeholder", arena)

        // another placeholder of the same size: nothing changes, nothing is asked for (no loop)
        arena.enemySprite.setPlaceholder("Bb", 0xFFAA6622.toInt())
        assertFalse("the same size asks for nothing", arena.isLayoutRequested)
        layOut(arena, 380, 300)
        assertSame(p1, arena.stagePlanNow)

        // the real image arrives: a tiny one, then a frame-filling one; each is planned for
        arena.enemySprite.setImage(SpriteImage(listOf(solid(20, 24)), intArrayOf(100)))
        arena.playerSprite.setImage(SpriteImage(listOf(solid(20, 24)), intArrayOf(100)))
        assertTrue(arena.isLayoutRequested)
        layOut(arena, 380, 300)
        val p2 = arena.stagePlanNow!!
        assertNotSame(p1, p2)
        assertClearOfDrawn("tiny", arena)
        layOut(arena, 380, 300)
        assertSame("a second layout of the same state changes nothing", p2, arena.stagePlanNow)
        arena.enemySprite.setImage(SpriteImage(listOf(solid(96, 96)), intArrayOf(100)))
        arena.playerSprite.setImage(SpriteImage(listOf(solid(96, 96)), intArrayOf(100)))
        layOut(arena, 380, 300)
        val p3 = arena.stagePlanNow!!
        assertNotSame(p2, p3)
        assertClearOfDrawn("full", arena)
        assertTrue("a bigger picture never gets more room than a tiny one (${p3.spriteScale} vs ${p2.spriteScale})", p3.spriteScale <= p2.spriteScale + 0.001f)

        // plain mode again: the sprites are told nothing and the callback is a no-op
        arena.setPlainBackdrop()
        layOut(arena, 380, 300)
        assertNull(arena.stagePlanNow)
        arena.enemySprite.setPlaceholder("Cc", 0xFF000000.toInt())
        layOut(arena, 380, 300)
        assertNull(arena.stagePlanNow)
    }
}
