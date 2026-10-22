package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.HttpGet
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BattleCard
import com.husarp.ankigo.ui.BattleFrameStyle
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.InfoBoxLook
import com.husarp.ankigo.ui.InfoBoxStyle
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.ui.XpCard
import com.husarp.ankigo.ui.XpCardSpot
import com.husarp.ankigo.ui.XpCardView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * R14-A, PLAN 140 + 207: the two info boxes are never cut at their edges and never covered by the battle frame, in every battle host,
 * for every look x layout x frame; the small side pop-ups (the team XP cards) are drawn above the frame.
 *
 * WHAT WAS WRONG (found with the pixel checks below): (1) the arena clips each of its children at the child's own bounds (`clipChildren`,
 * the sprites' faint / entrance animations rely on that), so a box lost whatever it draws outside its own rectangle, and every group
 * INSIDE the boxes (the name row, the number row, the HUD's bar rows) cut its texts' shadows / outlines at each text's own bounds;
 * (2) the frame was only counted as its band: the corner squares (6 x 6 art pixels, gold 7 x 7) and the ice's icicles reach deeper, so
 * a box on the edge had the frame drawn over its corner. Shots: r14a-<host>-<look>-<layout>-<frame>-<theme>[-plain] (a stand-in picture
 * under the boxes; "-plain" = the plain arena), r14a-xp-*, r14a-zoom-*.
 */
class R14AFrameClipShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() {
        Backdrops.forget()
        BattleShotKit.reset()
    }

    private val noGet: HttpGet = { _, _ -> null }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o: Any, name: String): T {
        var c: Class<*>? = o.javaClass
        while (c != null) {
            try { return c.getDeclaredField(name).also { it.isAccessible = true }.get(o) as T } catch (_: NoSuchFieldException) { c = c.superclass }
        }
        throw NoSuchFieldException(name)
    }

    // ---- a stand-in picture made in code (sky, hills, a striped lawn) ----

    private fun scene(bg: Backdrop, scale: Float): FakeSprites.Img {
        val w = (bg.w * scale).toInt().coerceAtLeast(8)
        val h = (bg.h * scale).toInt().coerceAtLeast(8)
        val img = FakeSprites.Img(w, h)
        fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        for (y in 0 until h) for (x in 0 until w) {
            val fx = x / w.toFloat()
            val fy = y / h.toFloat()
            val far = 0.42f + 0.05f * Math.sin(fx * 9.0).toFloat()
            img.px[y * w + x] = if (fy < far) rgb((70 + 130 * fy / far).toInt(), (130 + 95 * fy / far).toInt(), 235)
            else rgb(80 + (x / 14 % 2) * 14, 160 - (y / 10 % 2) * 12, 70)
        }
        return img
    }

    private fun showPicture(ctx: Context, arena: ArenaView) {
        val bg = BackdropCatalog.byId("forest")!!
        FakeSprites.write(scene(bg, 0.5f), Backdrops.file(Sprites.dir(ctx), bg))
        val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false, noGet)!!
        Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
        field<ArenaBackdrop>(arena, "backdrop").request(bg, false)
    }

    // ---- the hosts ----

    private class Scene(val root: View, val arena: ArenaView)
    private class Host(val name: String, val size: Size, val build: (Context, Boolean) -> Scene)

    private fun boxed(ctx: Context, withPicture: Boolean, wDp: Int, hDp: Int, setup: (BattlePanelView) -> Unit): Scene {
        val p = BattlePanelView(ctx)
        setup(p)
        p.renderAll()
        if (withPicture) showPicture(ctx, p.arena)
        val frame = FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            addView(p, FrameLayout.LayoutParams(Ui.dp(ctx, wDp), Ui.dp(ctx, hDp), Gravity.TOP or Gravity.LEFT))
        }
        return Scene(frame, p.arena)
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

    private val hosts = listOf(
        Host("battle-tab", Size.PHONE) { ctx, pic ->
            val built = BattleShotKit.bound(ctx)
            if (pic) showPicture(ctx, built.arena!!)
            Scene(built.root, built.arena!!)
        },
        Host("study-tall", Size.SPLIT) { ctx, pic -> boxed(ctx, pic, 412, 265) { it.setStudyLayout(false, false) } },
        Host("study-strip", Size.SPLIT) { ctx, pic -> boxed(ctx, pic, 412, 146) { it.setStudyLayout(true, false) } },
        Host("float", Size.FLOATBATTLE) { ctx, pic ->
            val self = object : android.content.ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }
            val v = FloatBattleView(self, {}, {}, {})
            v.javaClass.getDeclaredMethod("bind").also { it.isAccessible = true }.invoke(v)
            val p: BattlePanelView = field(v, "panel")
            p.renderAll()
            if (pic) showPicture(ctx, p.arena)
            val window: Any = field(v, "window")
            Scene(field(window, "root"), p.arena)
        },
        Host("pip", Size.PIP_169M) { ctx, pic ->
            val p = BattlePanelView(ctx).apply { setCompact(true) }
            p.renderAll()
            if (pic) showPicture(ctx, p.arena)
            Scene(p, p.arena)
        },
        Host("pokemon-card", Size.PHONE) { ctx, pic ->
            val c = BattleCard(ctx, CardHost(), FakeLive())
            c.start()
            val panel: BattlePanelView = field(c, "panel")
            if (pic) showPicture(ctx, panel.arena)
            val root = FrameLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), 0)
                addView(c.view, FrameLayout.LayoutParams(-1, -2))
            }
            Scene(root, panel.arena)
        },
    )

    private fun host(name: String) = hosts.first { it.name == name }

    // ---- pixels and boxes ----

    private fun render(v: View): Bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }

    /** A box on the arena's pixels: left, top, right, bottom (the card's layout box scaled from its corner, as it is drawn). */
    private class Px(val l: Int, val t: Int, val r: Int, val b: Int) {
        override fun toString() = "($l,$t)-($r,$b)"
    }

    private fun boxOf(v: View): Px {
        return Px(v.left, v.top, v.left + (v.width * v.scaleX).toInt(), v.top + (v.height * v.scaleY).toInt())
    }

    /** Pixels that differ between [a] and [b] inside the rectangle (clamped to the bitmaps). */
    private fun diffs(a: Bitmap, b: Bitmap, l: Int, t: Int, r: Int, bt: Int): Int {
        var n = 0
        for (y in max(0, t) until min(a.height, bt)) for (x in max(0, l) until min(a.width, r)) if (a.getPixel(x, y) != b.getPixel(x, y)) n++
        return n
    }

    private fun groups(v: View, out: MutableList<ViewGroup> = ArrayList()): MutableList<ViewGroup> {
        if (v is ViewGroup) { out += v; for (i in 0 until v.childCount) groups(v.getChildAt(i), out) }
        return out
    }

    /** Draws [arena] with the sprites, platforms and the NEW sticker hidden (only the boxes and the picture are left), [unclipped] = no group in it cuts a child. */
    private fun boxesOnly(arena: ArenaView, unclipped: Boolean): Bitmap {
        val hide = listOf("enemyHolder", "playerHolder", "platEnemy", "platPlayer", "newTag").map { field<View>(arena, it) }.filter { it.visibility == View.VISIBLE }
        hide.forEach { it.visibility = View.INVISIBLE }
        val all = groups(arena)
        val was = all.map { it.clipChildren }
        if (unclipped) all.forEach { it.clipChildren = false }
        try { return render(arena) } finally {
            all.forEachIndexed { i, g -> g.clipChildren = was[i] }
            hide.forEach { it.visibility = View.VISIBLE }
        }
    }

    /** Writes [bmp]'s rectangle, blown up [zoom] times (nearest), as a shot (the snapshot PNGs are scaled down; a cut edge is a pixel or two). */
    private fun zoom(name: String, bmp: Bitmap, l: Int, t: Int, r: Int, b: Int, zoom: Int = 4) {
        val l2 = max(0, l); val t2 = max(0, t); val r2 = min(bmp.width, r); val b2 = min(bmp.height, b)
        val w = (r2 - l2) * zoom
        val h = (b2 - t2) * zoom
        if (w <= 0 || h <= 0) return
        val img = FakeSprites.Img(w, h)
        for (y in 0 until h) for (x in 0 until w) img.px[y * w + x] = bmp.getPixel(l2 + x / zoom, t2 + y / zoom)
        FakeSprites.write(img, File(Shots.outDir, "$name.png"))
    }

    private fun shoot(host: Host, look: InfoBoxLook, style: InfoBoxStyle, frame: BattleFrameStyle, theme: Theme, withPicture: Boolean = true, checks: Boolean = true): Scene {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(host.size, theme))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        AppPrefs.setBackgroundPictures(ctx, withPicture)
        AppPrefs.setInfoBoxLook(ctx, look)
        AppPrefs.setInfoBoxStyle(ctx, style)
        AppPrefs.setBattleFrame(ctx, frame)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val sc = host.build(ctx, withPicture)
        val root = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
        val name = "r14a-${host.name}-${look.name.lowercase()}-${style.name.lowercase()}-${frame.name.lowercase()}-${theme.name.lowercase()}${if (withPicture) "" else "-plain"}"
        paparazzi.snapshot(root, name, 1000)
        if (checks) check("$name", sc)
        return sc
    }

    /** What must hold for every combination (see the class comment). */
    private fun check(tag: String, sc: Scene) {
        val arena = sc.arena
        assertTrue("$tag: the arena has a size", arena.width > 0 && arena.height > 0)
        val wildV: View = field(arena, "enemyBlock")
        val oursV: View = field(arena, "playerCard")
        val wild = boxOf(wildV)
        val ours = boxOf(oursV)
        val d = arena.resources.displayMetrics.density
        for ((name, b) in listOf("wild" to wild, "ours" to ours)) {
            assertTrue("$tag: $name box $b inside the ${arena.width} x ${arena.height} arena", b.l >= -1 && b.t >= -1 && b.r <= arena.width + 1 && b.b <= arena.height + 1)
        }
        // 1. nothing of the frame is drawn over a box: with and without the frame the pixels inside each box are the same
        val withFrame = render(arena)
        val fg = arena.foreground
        arena.foreground = null
        val without = render(arena)
        arena.foreground = fg
        if (fg != null) assertTrue("$tag: the frame draws something (the comparison below is not empty)", diffs(withFrame, without, 0, 0, withFrame.width, withFrame.height) > 0)
        for ((name, b) in listOf("wild" to wild, "ours" to ours)) {
            val n = diffs(withFrame, without, b.l, b.t, b.r - 1, b.b - 1)
            assertEquals("$tag: $n pixels of the frame are drawn over the $name box $b (frame inset ${arena.frameInsetDp} dp, top ${arena.frameTopDp}, corner ${arena.frameCornerDp})", 0, n)
        }
        // 2. nothing cuts a box: with every group in the arena un-clipped, the boxes and what they draw round themselves are the same
        val g = (16f * d).toInt()
        val kept = boxesOnly(arena, unclipped = false)
        val free = boxesOnly(arena, unclipped = true)
        for ((name, b) in listOf("wild" to wild, "ours" to ours)) {
            val n = diffs(kept, free, b.l - g, b.t - g, b.r + g, b.b + g)
            assertEquals("$tag: $n pixels round / inside the $name box $b are cut by a clipping group", 0, n)
        }
    }

    // ---- the matrices ----

    private val frames = BattleFrameStyle.entries
    private val looks = InfoBoxLook.entries

    @Test fun battleTabEveryLookEveryFrame() {
        for (theme in Theme.entries) for (look in looks) for (frame in frames) shoot(host("battle-tab"), look, InfoBoxStyle.ABOVE, frame, theme)
    }

    @Test fun battleTabTheNumberInsideTheBar() {
        for (look in listOf(InfoBoxLook.TODAY, InfoBoxLook.GLASS, InfoBoxLook.HUD)) for (frame in listOf(BattleFrameStyle.NONE, BattleFrameStyle.WOOD, BattleFrameStyle.GOLD, BattleFrameStyle.ICE))
            shoot(host("battle-tab"), look, InfoBoxStyle.INSIDE, frame, Theme.LIGHT)
    }

    @Test fun battleTabOnThePlainArena() {
        for (theme in Theme.entries) for (look in looks) for (frame in listOf(BattleFrameStyle.NONE, BattleFrameStyle.WOOD, BattleFrameStyle.STONE, BattleFrameStyle.GOLD, BattleFrameStyle.ICE, BattleFrameStyle.METAL))
            shoot(host("battle-tab"), look, InfoBoxStyle.ABOVE, frame, theme, withPicture = false)
    }

    @Test fun studyPanelTall() {
        for (theme in Theme.entries) for (look in looks) for (frame in listOf(BattleFrameStyle.NONE, BattleFrameStyle.WOOD, BattleFrameStyle.GOLD, BattleFrameStyle.ICE))
            shoot(host("study-tall"), look, InfoBoxStyle.ABOVE, frame, theme)
        for (look in looks) for (frame in listOf(BattleFrameStyle.NONE, BattleFrameStyle.WOOD, BattleFrameStyle.ICE))
            shoot(host("study-tall"), look, InfoBoxStyle.ABOVE, frame, Theme.LIGHT, withPicture = false)
    }

    @Test fun studyPanelStrip() {
        for (look in looks) for (frame in listOf(BattleFrameStyle.NONE, BattleFrameStyle.STONE, BattleFrameStyle.ICE)) {
            shoot(host("study-strip"), look, InfoBoxStyle.ABOVE, frame, Theme.LIGHT)
            shoot(host("study-strip"), look, InfoBoxStyle.ABOVE, frame, Theme.LIGHT, withPicture = false)
        }
    }

    @Test fun floatingWindowPipAndThePokemonTabCard() {
        for (look in listOf(InfoBoxLook.TODAY, InfoBoxLook.GLASS, InfoBoxLook.HUD, InfoBoxLook.PLATE)) for (frame in listOf(BattleFrameStyle.NONE, BattleFrameStyle.WOOD, BattleFrameStyle.ICE)) {
            for (name in listOf("float", "pip", "pokemon-card")) shoot(host(name), look, InfoBoxStyle.ABOVE, frame, Theme.LIGHT)
        }
    }

    // ---- the cause, in pixels ----

    /**
     * The old tree (every group clipping its children at their own bounds) cuts the HUD's text outlines; the new one does not. Only the
     * HUD: it has no box, so what differs just outside a text's own view is that text's outline (the other looks have a plate there).
     */
    @Test fun theTextOutlinesOfTheHudAreNotCutAtTheirOwnViews() {
        for (look in listOf(InfoBoxLook.HUD)) {
            val sc = shoot(host("battle-tab"), look, InfoBoxStyle.ABOVE, BattleFrameStyle.NONE, Theme.LIGHT, checks = false)
            val arena = sc.arena
            val d = arena.resources.displayMetrics.density
            val block: View = field(arena, "enemyBlock")
            fun inArena(v: View): Px {
                var x = 0f; var y = 0f
                var p: View = v
                while (p !== block) { x += p.left; y += p.top; p = p.parent as View }
                val k = block.scaleX
                return Px((block.left + x * k).toInt(), (block.top + y * k).toInt(), (block.left + (x + v.width) * k).toInt(), (block.top + (y + v.height) * k).toInt())
            }
            val name: View = field(arena, "eName")
            val level: View = field(arena, "eLevel")
            val nameBox = inArena(name)
            val levelBox = inArena(level)
            // the cards hidden vs shown, sprites hidden: any pixel outside the text views' own boxes that the shadow / outline touches
            val cardLayer: View = field(arena, "cardLayer")
            val shown = boxesOnly(arena, unclipped = false)
            cardLayer.visibility = View.INVISIBLE
            val bare = boxesOnly(arena, unclipped = false)
            cardLayer.visibility = View.VISIBLE
            val w = (3f * d).toInt()
            val left = diffs(shown, bare, nameBox.l - w, nameBox.t, nameBox.l, nameBox.b)
            val right = diffs(shown, bare, levelBox.r, levelBox.t, levelBox.r + w, levelBox.b)
            println("R14A ${look.name}: shadow pixels just left of the name $left, just right of the level $right")
            zoom("r14a-zoom-${look.name.lowercase()}-wild", shown, nameBox.l - (12 * d).toInt(), nameBox.t - (6 * d).toInt(), levelBox.r + (12 * d).toInt(), nameBox.b + (40 * d).toInt(), 3)
            assertTrue("${look.name}: the name's shadow reaches left of its own view ($left)", left > 0)
            assertTrue("${look.name}: the level's shadow reaches right of its own view ($right)", right > 0)
            // ... and the old tree (every group clipping) really did cut exactly that
            val all = groups(arena)
            val was = all.map { it.clipChildren }
            all.forEach { it.clipChildren = true }
            val old = try { boxesOnly(arena, unclipped = false) } finally { all.forEachIndexed { i, g -> g.clipChildren = was[i] } }
            val cutLeft = diffs(old, bare, nameBox.l - w, nameBox.t, nameBox.l, nameBox.b)
            val cutRight = diffs(old, bare, levelBox.r, levelBox.t, levelBox.r + w, levelBox.b)
            println("R14A ${look.name}: with the old clipping the same strips have $cutLeft / $cutRight")
            assertTrue("${look.name}: the old clipping cut the name's shadow ($cutLeft < $left)", cutLeft < left)
            assertTrue("${look.name}: the old clipping cut the level's shadow ($cutRight < $right)", cutRight < right)
        }
    }

    @Test fun theBoxesLiveInALayerThatClipsNothingAndTheArenaStillClipsTheSprites() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val arena = BattlePanelView(ctx).arena
        val cardLayer: ViewGroup = field(arena, "cardLayer")
        val popLayer: ViewGroup = field(arena, "popLayer")
        val wild: View = field(arena, "enemyBlock")
        val ours: View = field(arena, "playerCard")
        assertSame(cardLayer, wild.parent)
        assertSame(cardLayer, ours.parent)
        assertSame("the layer of the side pop-ups is on top", popLayer, arena.getChildAt(arena.childCount - 1))
        assertTrue("the arena itself still clips (the sprites' faint / entrance animations rely on it)", arena.clipChildren)
        for (g in groups(cardLayer) + popLayer) assertFalse("${g.javaClass.simpleName} clips its children", g.clipChildren)
        for (n in listOf("eRow", "pRow", "eNumRow", "pNumRow", "pXpRow", "eHpBox", "pHpBox", "eTypes", "eIcons", "eStatus", "pStatus"))
            assertFalse("$n clips its children", field<ViewGroup>(arena, n).clipChildren)
        assertNotNull(popLayer)
    }

    // ---- PLAN 207: the side pop-ups over the frame ----

    private fun afterLayout(arena: ArenaView, fire: (ArenaView) -> Unit) {
        var done = false
        arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!done && v.width > 0 && v.height > 0) {
                done = true
                fire(arena)
                arena.measure(View.MeasureSpec.makeMeasureSpec(arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(arena.height, View.MeasureSpec.EXACTLY))
                arena.layout(arena.left, arena.top, arena.right, arena.bottom)
            }
        }
    }

    private val cards = listOf(
        XpCard("u2", "Sprouty", 30, 9, 9, 0.30f, 0.80f, false),
        XpCard("u3", "Puddly", 40, 15, 16, 0f, 0.20f, true),
    )

    private fun loader(ctx: Context, i: Int): (SpriteView) -> Unit = { v ->
        val sp = FakeGame.data.species.values.toList().let { it[i % it.size] }
        val file = Sprites.cached(v.context, sp, SpriteKind.FRONT, false)
        if (file != null) v.setImage(Sprites.decode(file)) else v.setPlaceholder(sp.name.take(2), Ui.typeColor(sp.types.first()))
    }

    private fun battleTabWithXp(frame: BattleFrameStyle, theme: Theme, name: String, fire: (Context, ArenaView) -> Unit): Scene {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        AppPrefs.setBattleFrame(ctx, frame)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val b = BattleShotKit.bound(ctx)
        showPicture(ctx, b.arena!!)
        afterLayout(b.arena!!) { a -> fire(ctx, a) }
        val root = FrameLayout(ctx).apply { addView(b.root, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(root, name, 1100)
        return Scene(b.root, b.arena!!)
    }

    @Test fun theTeamXpCardsStandOverTheFrameInTheShots() {
        for (theme in Theme.entries) for (frame in listOf(BattleFrameStyle.WOOD, BattleFrameStyle.ICE, BattleFrameStyle.GOLD)) {
            battleTabWithXp(frame, theme, "r14a-xp-${frame.name.lowercase()}-${theme.name.lowercase()}") { ctx, a ->
                loader(ctx, 0)(a.playerSprite); loader(ctx, 1)(a.enemySprite)
                cards.forEachIndexed { i, c -> a.showXpCard(c, loader(ctx, i)) }
            }
        }
    }

    /** A card put on the arena's top-left corner (over the band AND the corner square): its pixels there are the card's, not the frame's. */
    @Test fun aSidePopUpIsDrawnOverTheFrameNotUnderIt() {
        val slide = ArenaView::class.java.getDeclaredMethod("slideXp", XpCardView::class.java, XpCardSpot.Pos::class.java, Function1::class.java).also { it.isAccessible = true }
        for (frame in listOf(BattleFrameStyle.WOOD, BattleFrameStyle.STONE, BattleFrameStyle.GOLD, BattleFrameStyle.ICE, BattleFrameStyle.METAL)) {
            // the card leaves by itself a moment later (and the render session runs that out), so the arena is drawn right here, with it on
            var card: XpCardView? = null
            var drawn: Bitmap? = null
            var parentOk = false
            val sc = battleTabWithXp(frame, Theme.LIGHT, "r14a-xp-corner-${frame.name.lowercase()}-light") { ctx, a ->
                val v = XpCardView(ctx, cards[0], false, 0f)
                v.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 188), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val load: (SpriteView) -> Unit = loader(ctx, 0)
                slide.invoke(a, v, XpCardSpot.Pos(0, 0), load)
                a.measure(View.MeasureSpec.makeMeasureSpec(a.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(a.height, View.MeasureSpec.EXACTLY))
                a.layout(a.left, a.top, a.right, a.bottom)
                card = v
                parentOk = v.parent === field<ViewGroup>(a, "popLayer")
                drawn = render(a)
            }
            val arena = sc.arena
            val v = card!!
            assertTrue("${frame.name}: the card was laid out (${v.width} x ${v.height})", v.width > 0 && v.height > 0)
            assertTrue("${frame.name}: the card is in the pop-up layer", parentOk)
            val bmp = drawn!!
            val cardColour = Ui.c(arena.context, R.color.ag_card)
            fun near(a: Int, b: Int, tol: Int) = abs((a shr 16 and 255) - (b shr 16 and 255)) <= tol && abs((a shr 8 and 255) - (b shr 8 and 255)) <= tol && abs((a and 255) - (b and 255)) <= tol
            val d = arena.resources.displayMetrics.density
            // along the card's top edge (inside its rounded corners): the frame's top band lies under it
            val y = v.top + 1
            val xs = listOf(20, 40, 60, 80, 100, 120).map { v.left + (it * d).toInt() }
            var over = 0
            for (x in xs) if (near(bmp.getPixel(x, y), cardColour, 24)) over++
            println("R14A xp over ${frame.name}: $over of ${xs.size} sample pixels on the card's top edge are the card's colour")
            zoom("r14a-zoom-xp-corner-${frame.name.lowercase()}", bmp, 0, 0, (200 * d).toInt(), (70 * d).toInt(), 3)
            assertEquals("${frame.name}: the pop-up is drawn over the frame's top band", xs.size, over)
            // and the frame is still there next to it (its band right of the card)
            val bandX = v.right + (6 * d).toInt()
            assertFalse("${frame.name}: the frame itself is still drawn right of the card", near(bmp.getPixel(bandX, 1), cardColour, 24))
        }
    }
}
