package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BackdropSource
import com.husarp.ankigo.ui.Stage
import com.husarp.ankigo.ui.StandPoint
import com.husarp.ankigo.ui.Ui
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Generated stand-in battle backgrounds (no Showdown or Adam file anywhere): a sky, a ground with a fine checker, a magenta frame
 * on the picture's edge, a light grid, and two light ovals at the background's own stand points. [scale] makes the file smaller
 * than the catalog says ([Backdrop.w] x [Backdrop.h]); the arena maps the crop to the real size.
 */
internal object BackdropScenes {
    fun scene(bg: Backdrop, scale: Float = 1f): FakeSprites.Img {
        val w = (bg.w * scale).toInt().coerceAtLeast(8)
        val h = (bg.h * scale).toInt().coerceAtLeast(8)
        val img = FakeSprites.Img(w, h)
        val hash = bg.id.hashCode()
        val base = intArrayOf(100 + (hash and 0x3f), 120 + ((hash shr 6) and 0x3f), 80 + ((hash shr 12) and 0x3f))
        val night = bg.dark
        fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        fun inOval(x: Int, y: Int, p: StandPoint): Boolean {
            val rx = p.s * 0.55f * h; val ry = p.s * 0.12f * h
            val dx = (x - p.x * w) / rx; val dy = (y - p.y * h) / ry
            return dx * dx + dy * dy <= 1f
        }
        val cell = maxOf(2, w / 110)
        val frame = maxOf(2, minOf(w, h) / 70)
        for (y in 0 until h) for (x in 0 until w) {
            val fy = y / h.toFloat()
            var r: Int; var g: Int; var b: Int
            if (fy < 0.38f) { // sky
                r = if (night) 18 + (fy * 50).toInt() else 150 + (fy * 70).toInt()
                g = if (night) 24 + (fy * 50).toInt() else 195 + (fy * 40).toInt()
                b = if (night) 60 + (fy * 80).toInt() else 235
            } else { // ground
                r = base[0]; g = base[1]; b = base[2]
                if (night) { r /= 4; g /= 4; b = b / 3 + 12 }
                if (((x / cell) + (y / cell)) % 2 == 0) { r -= 9; g -= 9; b -= 9 }
                if (inOval(x, y, bg.player) || inOval(x, y, bg.wild)) { r += 46; g += 46; b += 34 }
            }
            // a light grid every tenth, so a crop or shrink is easy to read
            if (x % (w / 10).coerceAtLeast(1) == 0 || y % (h / 10).coerceAtLeast(1) == 0) { r += 22; g += 22; b += 22 }
            if (x < frame || y < frame || x >= w - frame || y >= h - frame) { r = 230; g = 40; b = 200 } // the picture's edge
            img.px[y * w + x] = rgb(r, g, b)
        }
        return img
    }

    fun write(dir: File, bg: Backdrop, scale: Float = 1f) = FakeSprites.write(scene(bg, scale), Backdrops.file(dir, bg))

    /** A one-colour picture (opaque), for the pixel checks. */
    fun flat(bg: Backdrop, color: Int, dir: File) {
        val img = FakeSprites.Img(bg.w, bg.h)
        img.px.fill(color)
        FakeSprites.write(img, Backdrops.file(dir, bg))
    }
}

/**
 * R8-03 battle backgrounds: the crop + blur drawing of [ArenaBackdrop] with hand-made [Stage]s from the real catalog geometry
 * (a crop that fits, a picture that shrank wide / tall / both, crisp vs smooth scaling, day vs night pictures in the dark theme,
 * Earthquake's jolt), light and dark; plus pixel checks of the same drawing. Feet dots: red = ours, blue = the wild one.
 * PNGs: r8-03-crop|shrunk|scaling|tint|jolt|plain-<size>-<theme>.
 */
class R803Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = Backdrops.forget()

    // ---- hand-made stages -------------------------------------------------------------------------------------------------

    /**
     * The window of a [w] x [h] panel over [bg]: the panel's shape at [k] x the largest one that fits the picture (k > 1: bigger
     * than the picture, so it shrinks), centred between the two stand points across, bottom first up and down; an axis larger
     * than the picture is centred. Feet and frames follow from the stand points.
     */
    fun stageFor(bg: Backdrop, w: Float, h: Float, k: Float): Stage {
        val a = w / h
        var cw: Float; var ch: Float
        if (bg.w.toFloat() / bg.h > a) { ch = bg.h.toFloat(); cw = ch * a } else { cw = bg.w.toFloat(); ch = cw / a }
        cw *= k; ch *= k
        val mid = (bg.player.x + bg.wild.x) / 2f * bg.w
        val cx = if (cw >= bg.w) (bg.w - cw) / 2f else (mid - cw / 2f).coerceIn(0f, bg.w - cw)
        val cy = if (ch >= bg.h) (bg.h - ch) / 2f else bg.h - ch
        fun px(x: Float) = (x * bg.w - cx) / cw * w
        fun py(y: Float) = (y * bg.h - cy) / ch * h
        val frame = { p: StandPoint -> p.s * bg.h / ch * h }
        return Stage(
            cx, cy, cw, ch, shrunk = cw > bg.w + 0.01f || ch > bg.h + 0.01f,
            playerFeetX = px(bg.player.x), playerFeetY = py(bg.player.y), playerFrame = frame(bg.player),
            wildFeetX = px(bg.wild.x), wildFeetY = py(bg.wild.y), wildFrame = frame(bg.wild),
        )
    }

    /** One panel: the backdrop as its background, a stage worked out when it is laid out, and two dots on the stand points. */
    private inner class Panel(ctx: Context, val bg: Backdrop, val k: Float, val jolt: Float = 0f, val withStage: Boolean = true) : FrameLayout(ctx) {
        val backdrop = ArenaBackdrop(ctx)
        var stage: Stage? = null
        private val dot = Paint().apply { isAntiAlias = true }
        init {
            background = backdrop
            setWillNotDraw(false)
            backdrop.request(bg, false)
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            stage = if (withStage) stageFor(bg, w.toFloat(), h.toFloat(), k) else null
            backdrop.setStage(stage)
            backdrop.jolt = jolt * h
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            val s = stage ?: return
            val r = resources.displayMetrics.density * 3.5f
            dot.color = 0xFFE02020.toInt(); canvas.drawCircle(s.playerFeetX, s.playerFeetY + backdrop.jolt, r, dot)
            dot.color = 0xFF2060F0.toInt(); canvas.drawCircle(s.wildFeetX, s.wildFeetY + backdrop.jolt, r, dot)
        }
    }

    private fun label(ctx: Context, text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 10f
        setTextColor(Ui.c(ctx, R.color.ag_text))
        setPadding(0, Ui.dp(ctx, 3), 0, Ui.dp(ctx, 1))
    }

    private fun cell(ctx: Context, caption: String, wDp: Int, hDp: Int, panel: Panel): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(ctx, caption), LinearLayout.LayoutParams(-2, -2))
        addView(panel, LinearLayout.LayoutParams(Ui.dp(ctx, wDp), Ui.dp(ctx, hDp)))
        setPadding(0, 0, Ui.dp(ctx, 8), 0)
    }

    private fun row(ctx: Context, vararg cells: View): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        cells.forEach { addView(it, LinearLayout.LayoutParams(-2, -2)) }
    }

    private fun sheet(ctx: Context, vararg rows: View): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
        setBackgroundColor(Ui.c(ctx, R.color.ag_base))
        rows.forEach { addView(it, LinearLayout.LayoutParams(-2, -2)) }
        gravity = Gravity.TOP
    }

    private fun bg(id: String) = BackdropCatalog.byId(id)!!

    /** Writes the generated picture for [id] (after FakeGame.install, which empties the cache) and returns the Backdrop. */
    private fun scene(ctx: Context, id: String, scale: Float = 1f): Backdrop = bg(id).also { BackdropScenes.write(Sprites.dir(ctx), it, scale) }

    private fun shoot(name: String, sizes: List<Size>, build: (Context) -> View) {
        for (size in sizes) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val v = build(ctx)
            val frame = FrameLayout(ctx).apply { addView(v, FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "$name-${size.name.lowercase()}-${theme.name.lowercase()}", 1000)
        }
    }

    // ---- shots ---------------------------------------------------------------------------------------------------------------

    @Test fun crop() = shoot("r8-03-crop", listOf(Size.PHONE)) { ctx ->
        val forest = scene(ctx, "forest"); val peak = scene(ctx, "dragon-peak", 0.5f); val path = scene(ctx, "canyon")
        sheet(ctx,
            row(ctx, cell(ctx, "forest (pixel art, 753 x 500) crop 380 x 210", 380, 210, Panel(ctx, forest, 1f))),
            row(ctx, cell(ctx, "dragon-peak (soft, file at half size) crop 380 x 250", 380, 250, Panel(ctx, peak, 1f))),
            row(ctx, cell(ctx, "canyon (wide, soft) crop 380 x 160", 380, 160, Panel(ctx, path, 1f))),
            row(ctx, cell(ctx, "no stage yet: cover crop 380 x 150", 380, 150, Panel(ctx, forest, 1f, withStage = false))),
        )
    }

    @Test fun shrunk() = shoot("r8-03-shrunk", listOf(Size.PHONE)) { ctx ->
        val reef = scene(ctx, "beach"); val forest = scene(ctx, "forest"); val garden = scene(ctx, "jungle"); val temple = scene(ctx, "stormy-plains")
        sheet(ctx,
            row(ctx, cell(ctx, "wide: beach 380 x 110, blur left + right, edges fade", 380, 110, Panel(ctx, reef, 1.35f))),
            row(
                ctx,
                cell(ctx, "tall: forest 120 x 300, blur top + bottom", 120, 300, Panel(ctx, forest, 2.2f)),
                cell(ctx, "both: jungle 150 x 150", 150, 150, Panel(ctx, garden, 1.5f)),
                cell(ctx, "stormy-plains 90 x 220", 90, 220, Panel(ctx, temple, 2.8f)),
            ),
        )
    }

    @Test fun scaling() = shoot("r8-03-scaling", listOf(Size.SPLIT)) { ctx ->
        val reef = scene(ctx, "beach", 0.2f); val path = scene(ctx, "canyon", 0.15f)
        sheet(ctx,
            row(ctx, cell(ctx, "crisp (beach): nearest-neighbour", 190, 120, Panel(ctx, reef, 0.35f)), cell(ctx, "smooth (canyon): filtered", 190, 120, Panel(ctx, path, 0.35f))),
        )
    }

    @Test fun tint() = shoot("r8-03-tint", listOf(Size.PHONE)) { ctx ->
        val day = scene(ctx, "meadow"); val night = scene(ctx, "night-forest", 0.5f); val dayWebp = scene(ctx, "factory", 0.5f); val dark2 = scene(ctx, "haunted-mansion", 0.5f)
        sheet(ctx,
            row(ctx, cell(ctx, "meadow (day): dusk in the dark theme, blur too", 380, 120, Panel(ctx, day, 1.35f))),
            row(ctx, cell(ctx, "night-forest (dark picture): never tinted", 380, 120, Panel(ctx, night, 1.4f))),
            row(ctx, cell(ctx, "factory (day)", 185, 150, Panel(ctx, dayWebp, 1.6f)), cell(ctx, "haunted-mansion (dark)", 185, 150, Panel(ctx, dark2, 1.6f))),
        )
    }

    @Test fun jolt() = shoot("r8-03-jolt", listOf(Size.PHONE)) { ctx ->
        val forest = scene(ctx, "forest"); val reef = scene(ctx, "beach")
        sheet(ctx,
            row(ctx, cell(ctx, "crop, jolt +6 % (down): blur shows at the top", 185, 200, Panel(ctx, forest, 1f, jolt = 0.06f)), cell(ctx, "crop, jolt -6 % (up)", 185, 200, Panel(ctx, forest, 1f, jolt = -0.06f))),
            row(ctx, cell(ctx, "shrunk, jolt +6 %", 380, 110, Panel(ctx, reef, 1.35f, jolt = 0.06f))),
        )
    }

    @Test fun plain() = shoot("r8-03-plain", listOf(Size.SPLIT)) { ctx ->
        val missing = bg("river") // no file: the load finds nothing and the gradient stays
        sheet(ctx, row(ctx, cell(ctx, "no picture: the plain gradient", 380, 150, Panel(ctx, missing, 1f))))
    }

    // ---- pixel checks (the same drawing, into a bitmap) ------------------------------------------------------------------------

    private val noGet: com.husarp.ankigo.data.HttpGet = { _, _ -> null }

    /** Puts the picture of [b] (already in the cache) into memory, so a request applies at once (no posted callback to wait for). */
    private fun preload(ctx: Context, b: Backdrop) {
        val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), b, false, noGet)!!
        assertNotNull(pic.bitmap); assertTrue("the blur is small", maxOf(pic.blur.width, pic.blur.height) <= 96)
        Backdrops.finish(Backdrops.currentGeneration(), b.id, pic)
    }

    private fun assertNotNull(x: Any?) = org.junit.Assert.assertNotNull(x)

    private fun drawn(back: ArenaBackdrop, w: Int, h: Int): Bitmap {
        back.setBounds(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        back.draw(Canvas(bmp))
        return bmp
    }

    private fun flatBackdrop(id: String, dark: Boolean = false, crisp: Boolean = true) = Backdrop(
        id, BackdropSource.OWN, "png", 300, 200, StandPoint(.3f, .8f, .4f), StandPoint(.7f, .6f, .3f), crisp = crisp, dark = dark,
    )

    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF

    private fun setUp(theme: Theme): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, theme))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        return ctx
    }

    private val sand = 0xFFC86432.toInt() // (200, 100, 50)

    @Test fun aCropThatFitsIsOnlyThePictureAndAShrunkOneHasBlurAtTheSides() {
        val ctx = setUp(Theme.LIGHT)
        val bgx = flatBackdrop("flat-a")
        BackdropScenes.flat(bgx, sand, Sprites.dir(ctx))
        preload(ctx, bgx)
        val back = ArenaBackdrop(ctx)
        var told: Boolean? = null
        back.onPictureChange = { told = it }
        back.request(bgx, false)
        assertEquals(true, told); assertSame(bgx, back.shown); assertTrue(back.showsPicture)

        // no stage: the cover crop, nothing but the picture everywhere
        back.setStage(null)
        val plain = drawn(back, 300, 100)
        for ((x, y) in listOf(0 to 0, 299 to 0, 0 to 99, 299 to 99, 150 to 50)) assertEquals("($x,$y)", sand, plain.getPixel(x, y))

        // a stage that fits
        back.setStage(stageFor(bgx, 300f, 100f, 1f))
        val fit = drawn(back, 300, 100)
        for ((x, y) in listOf(0 to 0, 299 to 99, 150 to 50)) assertEquals(sand, fit.getPixel(x, y))

        // a shrunk one (window 1.5 x wider than the picture): blur at both sides, the picture in the middle, a fade between
        val st = stageFor(bgx, 300f, 100f, 1.5f)
        assertTrue(st.shrunk)
        back.setStage(st)
        val shrunk = drawn(back, 300, 100)
        assertEquals("the picture is in the middle", sand, shrunk.getPixel(150, 50))
        val left = shrunk.getPixel(2, 50); val right = shrunk.getPixel(297, 50)
        assertTrue("blur is darker than the picture", r(left) < r(sand) - 40 && r(right) < r(sand) - 40)
        assertTrue("same on both sides", Math.abs(r(left) - r(right)) <= 2)
        // a flat picture: the blur is the picture x .55 brightness, then more saturation
        assertEquals((200 * 0.55f), r(left).toFloat(), 14f)
        assertTrue("saturation: red-blue spread grew", (r(left) - b(left)) > (200 - 50) * 0.55f)
        // the fade: walking in from the left, the colour goes from blur to picture without a jump over 7 % of the picture width
        var prev = r(left); var maxStep = 0
        for (x in 3 until 150) { val c = r(shrunk.getPixel(x, 50)); maxStep = maxOf(maxStep, Math.abs(c - prev)); prev = c }
        assertTrue("no hard bar: biggest step $maxStep", maxStep < 45)
        // the vertical axis fits: top and bottom rows in the middle are the picture itself
        assertEquals(sand, shrunk.getPixel(150, 0)); assertEquals(sand, shrunk.getPixel(150, 99))
    }

    @Test fun aTallShrinkHasBlurAtTopAndBottom() {
        val ctx = setUp(Theme.LIGHT)
        val bgx = flatBackdrop("flat-b")
        BackdropScenes.flat(bgx, sand, Sprites.dir(ctx))
        preload(ctx, bgx)
        val back = ArenaBackdrop(ctx)
        back.request(bgx, false)
        val st = stageFor(bgx, 100f, 300f, 2f)
        assertTrue(st.shrunk)
        back.setStage(st)
        val bmp = drawn(back, 100, 300)
        assertEquals(sand, bmp.getPixel(50, 150))
        assertTrue(r(bmp.getPixel(50, 2)) < 160 && r(bmp.getPixel(50, 297)) < 160)
    }

    @Test fun jolt_moves_the_picture_and_the_blur_fills_the_gap() {
        val ctx = setUp(Theme.LIGHT)
        val bgx = flatBackdrop("flat-c")
        BackdropScenes.flat(bgx, sand, Sprites.dir(ctx))
        preload(ctx, bgx)
        val back = ArenaBackdrop(ctx)
        back.request(bgx, false)
        back.setStage(stageFor(bgx, 300f, 200f, 1f))
        assertEquals(sand, drawn(back, 300, 200).getPixel(150, 2))
        back.jolt = 12f // 6 % of 200: the picture moves down, the top 12 px show the blur
        val down = drawn(back, 300, 200)
        assertTrue("blur on top", r(down.getPixel(150, 3)) < 160)
        assertEquals(sand, down.getPixel(150, 40))
        assertTrue("never transparent", (0 until 200 step 20).all { y -> (down.getPixel(150, y) ushr 24) == 255 })
        back.jolt = -12f
        val up = drawn(back, 300, 200)
        assertTrue("blur at the bottom", r(up.getPixel(150, 196)) < 160)
        assertEquals(sand, up.getPixel(150, 150))
        assertTrue((0 until 300 step 30).all { x -> (up.getPixel(x, 199) ushr 24) == 255 })
    }

    @Test fun joltOfAFadedLandscapeCropKeepsTheSharpPictureThatSticksOutAboveThePanel() {
        val ctx = setUp(Theme.LIGHT)
        val bgx = flatBackdrop("flat-e")
        BackdropScenes.flat(bgx, sand, Sprites.dir(ctx))
        preload(ctx, bgx)
        val back = ArenaBackdrop(ctx)
        back.request(bgx, false)
        // a landscape panel, window 1.5 x wider than the picture: the picture takes x 50..250 (blur + a fade at both sides) and
        // sticks out above the panel (it starts at y -33), ending at the panel's bottom
        val st = stageFor(bgx, 300f, 100f, 1.5f)
        assertTrue(st.shrunk)
        back.setStage(st)
        assertEquals(sand, drawn(back, 300, 100).getPixel(150, 0))
        for (jolt in listOf(6f, 3f, -6f)) { // 6 % of the panel's height, down and up
            back.jolt = jolt
            val bmp = drawn(back, 300, 100)
            // the sharp picture spans screen rows (-33 + jolt) .. (100 + jolt), clipped to the panel
            val bottom = 100 + jolt.toInt()
            for (y in 0 until 100) {
                val c = bmp.getPixel(150, y)
                if (y < bottom - 1) assertEquals("jolt $jolt: sharp picture at row $y", sand, c)
                else if (y > bottom) assertTrue("jolt $jolt: blur under the picture at row $y (r=${r(c)})", r(c) < 160)
            }
            // never transparent
            assertTrue((0 until 100 step 5).all { y -> (bmp.getPixel(150, y) ushr 24) == 255 })
        }
    }

    @Test fun duskTintIsForDayPicturesInTheDarkThemeAndTheBlurGetsIt() {
        for (theme in Theme.entries) {
            val ctx = setUp(theme)
            val day = flatBackdrop("flat-day"); val night = flatBackdrop("flat-night", dark = true)
            BackdropScenes.flat(day, sand, Sprites.dir(ctx)); BackdropScenes.flat(night, sand, Sprites.dir(ctx))
            preload(ctx, day); preload(ctx, night)
            val dayBack = ArenaBackdrop(ctx); dayBack.request(day, false); dayBack.setStage(stageFor(day, 300f, 100f, 1.5f))
            val nightBack = ArenaBackdrop(ctx); nightBack.request(night, false); nightBack.setStage(stageFor(night, 300f, 100f, 1.5f))
            val d = drawn(dayBack, 300, 100); val n = drawn(nightBack, 300, 100)
            // the night picture never changes with the theme
            assertEquals("night picture, $theme", sand, n.getPixel(150, 50))
            if (theme == Theme.DARK) {
                // multiply rgba(34,38,96,.42): (200, 100, 50) -> about (127, 64, 37)
                val c = d.getPixel(150, 50)
                assertEquals(127f, r(c).toFloat(), 3f); assertEquals(64f, g(c).toFloat(), 3f); assertEquals(37f, b(c).toFloat(), 3f)
                // the blurred copy of the day picture gets the same multiply: darker than the night picture's untouched blur
                assertEquals(r(n.getPixel(3, 50)) * 0.636f, r(d.getPixel(3, 50)).toFloat(), 3f)
                assertEquals(b(n.getPixel(3, 50)) * 0.738f, b(d.getPixel(3, 50)).toFloat(), 3f)
            } else {
                assertEquals("light theme: no tint", sand, d.getPixel(150, 50))
                assertEquals("same blur in the light theme", d.getPixel(3, 50), n.getPixel(3, 50))
            }
        }
    }

    @Test fun aPictureFileOfAnotherSizeIsScaledToTheCatalogsPixels() {
        val ctx = setUp(Theme.LIGHT)
        val bgx = flatBackdrop("flat-d")
        // a file at 1/3 of the catalog size, with a magenta left strip of 1/10 of its width
        val img = FakeSprites.Img(100, 66)
        for (y in 0 until 66) for (x in 0 until 100) img.px[y * 100 + x] = if (x < 10) 0xFFE628C8.toInt() else sand
        FakeSprites.write(img, Backdrops.file(Sprites.dir(ctx), bgx))
        preload(ctx, bgx)
        val back = ArenaBackdrop(ctx)
        back.request(bgx, false)
        // the crop window is the left 60 x 40 catalog pixels (20 x 13 file pixels): the strip is the left half of it
        back.setStage(Stage(0f, 0f, 60f, 40f, false, 0f, 0f, 1f, 0f, 0f, 1f))
        val bmp = drawn(back, 120, 80)
        assertEquals(0xFFE628C8.toInt(), bmp.getPixel(5, 40))      // inside the strip (30 catalog px = 10 file px wide; 60 panel px)
        assertEquals(sand, bmp.getPixel(100, 40))
    }

    @Test fun requestSwapPlainAndTheCallbacks() {
        val ctx = setUp(Theme.LIGHT)
        val a = flatBackdrop("flat-e"); val c = flatBackdrop("flat-f")
        BackdropScenes.flat(a, sand, Sprites.dir(ctx)); BackdropScenes.flat(c, 0xFF3264C8.toInt(), Sprites.dir(ctx))
        preload(ctx, a); preload(ctx, c)
        val back = ArenaBackdrop(ctx)
        val told = ArrayList<Boolean>()
        back.onPictureChange = { told += it }
        assertNull(back.shown); assertFalse(back.showsPicture)
        back.request(a, false)
        back.request(a, false) // the same picture again: nothing new
        assertEquals(listOf(true), told)
        back.request(c, false) // another picture replaces it: the arena is told (stand points change)
        assertEquals(listOf(true, true), told)
        assertSame(c, back.shown)
        back.plain()
        assertEquals(listOf(true, true, false), told)
        assertNull(back.shown); assertFalse(back.showsPicture)
        back.plain() // already plain: silent
        assertEquals(3, told.size)
        // plain draws the gradient: opaque and not a picture colour
        val bmp = drawn(back, 50, 50)
        assertEquals(255, bmp.getPixel(25, 25) ushr 24)
        assertNotEquals(sand, bmp.getPixel(25, 25))
    }

    // ---- cross-fade (R8-X1b) ---------------------------------------------------------------------------------------------------

    private val blue = 0xFF3264C8.toInt() // (50, 100, 200)

    /** Counts the frames a drawable asks for. */
    private class Frames : Drawable.Callback {
        var asked = 0
        override fun invalidateDrawable(who: Drawable) { asked++ }
        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}
        override fun unscheduleDrawable(who: Drawable, what: Runnable) {}
    }

    private class Fade(val back: ArenaBackdrop, val frames: Frames, val now: () -> Long)

    /** A backdrop with a clock we move ([t]), a frame counter, and reduced motion as asked; [a] is already showing, settled. */
    private fun fadeRig(ctx: Context, a: Backdrop?, b: Backdrop, reduced: Boolean, t: LongArray): Fade {
        val back = ArenaBackdrop(ctx)
        val frames = Frames()
        back.callback = frames
        back.clock = { t[0] }
        back.reduceMotion = { reduced }
        if (a != null) {
            back.reduceMotion = { true }
            back.request(a, false) // settled at once
            back.reduceMotion = { reduced }
        }
        back.request(b, false)
        return Fade(back, frames) { t[0] }
    }

    private fun mix(c0: Int, c1: Int, f: Float) = Triple(
        r(c0) + (r(c1) - r(c0)) * f, g(c0) + (g(c1) - g(c0)) * f, b(c0) + (b(c1) - b(c0)) * f,
    )

    @Test fun aNewPictureFadesInOver400MsAndOnlyItRemainsAtTheEnd() {
        val ctx = setUp(Theme.LIGHT)
        val a = flatBackdrop("flat-fa"); val bb = flatBackdrop("flat-fb")
        BackdropScenes.flat(a, sand, Sprites.dir(ctx)); BackdropScenes.flat(bb, blue, Sprites.dir(ctx))
        preload(ctx, a); preload(ctx, bb)
        val t = longArrayOf(1000)
        val rig = fadeRig(ctx, a, bb, false, t)
        val back = rig.back
        assertSame("the arena lays its Pokemon on the new picture at the start", bb, back.shown)
        assertTrue(back.showsPicture)
        // the first frame starts the clock: still the old picture
        assertEquals(sand, drawn(back, 300, 100).getPixel(150, 50))
        t[0] = 1200
        val mid = drawn(back, 300, 100).getPixel(150, 50)
        val want = mix(sand, blue, 0.5f)
        assertEquals(want.first, r(mid).toFloat(), 3f); assertEquals(want.second, g(mid).toFloat(), 3f); assertEquals(want.third, b(mid).toFloat(), 3f)
        assertNotEquals(sand, mid); assertNotEquals(blue, mid)
        t[0] = 1100
        val early = drawn(back, 300, 100).getPixel(150, 50)
        assertTrue("a quarter in: nearer the old one", Math.abs(r(early) - r(sand)) < Math.abs(r(early) - r(blue)))
        t[0] = 1399
        assertNotEquals(blue, drawn(back, 300, 100).getPixel(150, 50))
        t[0] = 1400
        assertEquals("the end: only the new picture", blue, drawn(back, 300, 100).getPixel(150, 50))
        assertEquals(blue, drawn(back, 300, 100).getPixel(3, 3))
        // nothing is scheduled any more
        val asked = rig.frames.asked
        t[0] = 5000
        drawn(back, 300, 100); drawn(back, 300, 100)
        assertEquals("no frame asked after the end", asked, rig.frames.asked)
    }

    @Test fun everyFrameOfAFadeAsksForTheNextAndTheLastDoesNot() {
        val ctx = setUp(Theme.LIGHT)
        val a = flatBackdrop("flat-fc"); val bb = flatBackdrop("flat-fd")
        BackdropScenes.flat(a, sand, Sprites.dir(ctx)); BackdropScenes.flat(bb, blue, Sprites.dir(ctx))
        preload(ctx, a); preload(ctx, bb)
        val t = longArrayOf(0)
        val rig = fadeRig(ctx, a, bb, false, t)
        rig.frames.asked = 0
        drawn(rig.back, 100, 50)
        assertEquals(1, rig.frames.asked)
        t[0] = 399; drawn(rig.back, 100, 50)
        assertEquals(2, rig.frames.asked)
        t[0] = 400; drawn(rig.back, 100, 50)
        assertEquals("the last frame asks for nothing", 2, rig.frames.asked)
    }

    @Test fun reducedMotionSwapsAtOnceAndAsksForNoFrames() {
        val ctx = setUp(Theme.LIGHT)
        val a = flatBackdrop("flat-fe"); val bb = flatBackdrop("flat-ff")
        BackdropScenes.flat(a, sand, Sprites.dir(ctx)); BackdropScenes.flat(bb, blue, Sprites.dir(ctx))
        preload(ctx, a); preload(ctx, bb)
        val t = longArrayOf(0)
        val rig = fadeRig(ctx, a, bb, true, t)
        rig.frames.asked = 0
        assertEquals(blue, drawn(rig.back, 300, 100).getPixel(150, 50))
        assertEquals(0, rig.frames.asked)
        // gradient -> first picture is instant too
        val rig2 = fadeRig(ctx, null, a, true, t)
        assertEquals(sand, drawn(rig2.back, 300, 100).getPixel(150, 50))
    }

    @Test fun gradientToFirstPictureFadesToo() {
        val ctx = setUp(Theme.LIGHT)
        val bb = flatBackdrop("flat-fg")
        BackdropScenes.flat(bb, blue, Sprites.dir(ctx))
        preload(ctx, bb)
        val plainBack = ArenaBackdrop(ctx)
        val grad = drawn(plainBack, 300, 100).getPixel(150, 50)
        val t = longArrayOf(50)
        val rig = fadeRig(ctx, null, bb, false, t)
        assertEquals("starts as the gradient", grad, drawn(rig.back, 300, 100).getPixel(150, 50))
        t[0] = 250
        val mid = drawn(rig.back, 300, 100).getPixel(150, 50)
        val want = mix(grad, blue, 0.5f)
        assertEquals(want.first, r(mid).toFloat(), 3f); assertEquals(want.second, g(mid).toFloat(), 3f); assertEquals(want.third, b(mid).toFloat(), 3f)
        t[0] = 450
        assertEquals(blue, drawn(rig.back, 300, 100).getPixel(150, 50))
    }

    @Test fun theOutgoingPictureKeepsItsOwnCropAndTheNewOneItsStage() {
        val ctx = setUp(Theme.LIGHT)
        val a = flatBackdrop("flat-fh"); val bb = flatBackdrop("flat-fi")
        BackdropScenes.flat(a, sand, Sprites.dir(ctx)); BackdropScenes.flat(bb, blue, Sprites.dir(ctx))
        preload(ctx, a); preload(ctx, bb)
        val t = longArrayOf(0)
        val back = ArenaBackdrop(ctx)
        back.clock = { t[0] }
        back.reduceMotion = { true }
        back.request(a, false)
        back.setStage(stageFor(a, 300f, 100f, 1.5f)) // A is shrunk: blur at both sides
        back.reduceMotion = { false }
        back.request(bb, false)
        back.setStage(stageFor(bb, 300f, 100f, 1f)) // B fits: picture everywhere
        drawn(back, 300, 100) // start
        t[0] = 40 // B at about 10 %: A's blurred side is still dark underneath, not B's picture
        val early = drawn(back, 300, 100)
        val side = early.getPixel(2, 50)
        // under B's 10 %: A's blur (red about 110) mixed with B's blue picture: red below 120, far from sand's 200
        assertTrue("A's blur still under the side (r=${r(side)})", r(side) < 125)
        t[0] = 500
        assertEquals(blue, drawn(back, 300, 100).getPixel(2, 50))
    }

    @Test fun aResizeInTheMiddleOfAFadeEndsIt() {
        val ctx = setUp(Theme.LIGHT)
        val a = flatBackdrop("flat-fj"); val bb = flatBackdrop("flat-fk")
        BackdropScenes.flat(a, sand, Sprites.dir(ctx)); BackdropScenes.flat(bb, blue, Sprites.dir(ctx))
        preload(ctx, a); preload(ctx, bb)
        val t = longArrayOf(0)
        val rig = fadeRig(ctx, a, bb, false, t)
        drawn(rig.back, 300, 100)
        t[0] = 100
        drawn(rig.back, 300, 100)
        rig.back.setBounds(0, 0, 200, 120) // the resize ends the fade
        val asked = rig.frames.asked
        assertEquals(blue, drawn(rig.back, 200, 120).getPixel(100, 60))
        assertEquals("no frame asked after the resize", asked, rig.frames.asked)
    }

    @Test fun plainDropsAFadeAtOnceAndANewerPictureStartsAgain() {
        val ctx = setUp(Theme.LIGHT)
        val a = flatBackdrop("flat-fl"); val bb = flatBackdrop("flat-fm")
        BackdropScenes.flat(a, sand, Sprites.dir(ctx)); BackdropScenes.flat(bb, blue, Sprites.dir(ctx))
        preload(ctx, a); preload(ctx, bb) // the memory keeps two pictures only
        val t = longArrayOf(0)
        val rig = fadeRig(ctx, a, bb, false, t)
        drawn(rig.back, 100, 50); t[0] = 100
        rig.back.plain()
        val g0 = drawn(ArenaBackdrop(ctx), 100, 50).getPixel(50, 25)
        assertEquals(g0, drawn(rig.back, 100, 50).getPixel(50, 25))
        val asked = rig.frames.asked
        t[0] = 600; drawn(rig.back, 100, 50)
        assertEquals(asked, rig.frames.asked)
        // plain -> the picture again fades from the gradient, starting at its own first frame
        rig.back.request(bb, false)
        assertEquals(g0, drawn(rig.back, 100, 50).getPixel(50, 25))
        t[0] = 1100
        assertEquals(blue, drawn(rig.back, 100, 50).getPixel(50, 25))
    }

    private val green = 0xFF3CB43C.toInt() // (60, 180, 60)

    @Test fun aPictureThatComesMidFadeStartsFromTheBlendOnScreenAndNeverJumps() {
        val ctx = setUp(Theme.LIGHT)
        val a = flatBackdrop("flat-fn"); val bb = flatBackdrop("flat-fo"); val cc = flatBackdrop("flat-fp")
        BackdropScenes.flat(a, sand, Sprites.dir(ctx)); BackdropScenes.flat(bb, blue, Sprites.dir(ctx)); BackdropScenes.flat(cc, green, Sprites.dir(ctx))
        preload(ctx, a); preload(ctx, bb)
        val t = longArrayOf(1000)
        val rig = fadeRig(ctx, a, bb, false, t)
        val back = rig.back
        drawn(back, 300, 100) // A -> B starts at 1000
        t[0] = 1100 // 25 %
        val atInterrupt = drawn(back, 300, 100).getPixel(150, 50)
        val blend = mix(sand, blue, 0.25f)
        assertEquals(blend.first, r(atInterrupt).toFloat(), 3f)
        preload(ctx, cc)
        back.request(cc, false)
        assertSame(cc, back.shown)
        // the first frame of the new fade (its clock starts here) is the blend as it was, not B whole
        val first = drawn(back, 300, 100)
        for ((x, y) in listOf(150 to 50, 3 to 3, 290 to 95)) {
            val px = first.getPixel(x, y)
            assertEquals("r at $x,$y", r(atInterrupt).toFloat(), r(px).toFloat(), 2f)
            assertEquals("g at $x,$y", g(atInterrupt).toFloat(), g(px).toFloat(), 2f)
            assertEquals("b at $x,$y", b(atInterrupt).toFloat(), b(px).toFloat(), 2f)
        }
        // later frames move toward C
        t[0] = 1300 // 50 % into B -> C
        val mid = drawn(back, 300, 100).getPixel(150, 50)
        val want = Triple((r(atInterrupt) + 60) / 2f, (g(atInterrupt) + 180) / 2f, (b(atInterrupt) + 60) / 2f)
        assertEquals(want.first, r(mid).toFloat(), 3f); assertEquals(want.second, g(mid).toFloat(), 3f); assertEquals(want.third, b(mid).toFloat(), 3f)
        t[0] = 1499
        assertNotEquals(green, drawn(back, 300, 100).getPixel(150, 50))
        t[0] = 1500
        assertEquals("the end: only C", green, drawn(back, 300, 100).getPixel(150, 50))
        assertEquals(green, drawn(back, 300, 100).getPixel(3, 3))
        val asked = rig.frames.asked
        t[0] = 9000; drawn(back, 300, 100)
        assertEquals("no frame asked after the end", asked, rig.frames.asked)
    }

    @Test fun aPictureThatComesBeforeTheFadeDrewItsFirstFrameKeepsTheOldOutgoing() {
        val ctx = setUp(Theme.LIGHT)
        val a = flatBackdrop("flat-fq"); val bb = flatBackdrop("flat-fr"); val cc = flatBackdrop("flat-fs")
        BackdropScenes.flat(a, sand, Sprites.dir(ctx)); BackdropScenes.flat(bb, blue, Sprites.dir(ctx)); BackdropScenes.flat(cc, green, Sprites.dir(ctx))
        preload(ctx, a); preload(ctx, bb)
        val t = longArrayOf(0)
        val rig = fadeRig(ctx, a, bb, false, t)
        preload(ctx, cc)
        rig.back.request(cc, false) // B never drew
        assertEquals("A is still the outgoing one", sand, drawn(rig.back, 300, 100).getPixel(150, 50))
        t[0] = 400
        assertEquals(green, drawn(rig.back, 300, 100).getPixel(150, 50))
    }

    @Test fun reducedMotionSwitchedOnMidFadeFinishesAtOnceAndAsksForNothing() {
        val ctx = setUp(Theme.LIGHT)
        val a = flatBackdrop("flat-ft"); val bb = flatBackdrop("flat-fu")
        BackdropScenes.flat(a, sand, Sprites.dir(ctx)); BackdropScenes.flat(bb, blue, Sprites.dir(ctx))
        preload(ctx, a); preload(ctx, bb)
        val t = longArrayOf(0)
        val rig = fadeRig(ctx, a, bb, false, t)
        drawn(rig.back, 300, 100); t[0] = 100
        assertNotEquals(blue, drawn(rig.back, 300, 100).getPixel(150, 50))
        rig.back.reduceMotion = { true } // switched on while the fade runs
        val asked = rig.frames.asked
        assertEquals(blue, drawn(rig.back, 300, 100).getPixel(150, 50))
        assertEquals("no further frame", asked, rig.frames.asked)
        // also while the fade is only pending (the picture loaded while hidden)
        val rig2 = fadeRig(ctx, a, bb, false, t)
        rig2.back.reduceMotion = { true }
        rig2.frames.asked = 0
        assertEquals(blue, drawn(rig2.back, 300, 100).getPixel(150, 50))
        assertEquals(0, rig2.frames.asked)
    }

    @Test fun aSnapshotIsLetGoOfWithAResizeAndWithPlain() {
        val ctx = setUp(Theme.LIGHT)
        val a = flatBackdrop("flat-fv"); val bb = flatBackdrop("flat-fw"); val cc = flatBackdrop("flat-fx")
        BackdropScenes.flat(a, sand, Sprites.dir(ctx)); BackdropScenes.flat(bb, blue, Sprites.dir(ctx)); BackdropScenes.flat(cc, green, Sprites.dir(ctx))
        preload(ctx, a); preload(ctx, bb)
        val t = longArrayOf(0)
        val rig = fadeRig(ctx, a, bb, false, t)
        drawn(rig.back, 300, 100); t[0] = 100; drawn(rig.back, 300, 100)
        preload(ctx, cc)
        rig.back.request(cc, false) // interrupted: a snapshot is the outgoing side
        drawn(rig.back, 300, 100) // C's fade starts
        rig.back.setBounds(0, 0, 200, 120) // a resize ends it: only C, no stale 300 x 100 bitmap
        val asked = rig.frames.asked
        assertEquals(green, drawn(rig.back, 200, 120).getPixel(100, 60))
        assertEquals(green, drawn(rig.back, 200, 120).getPixel(3, 3))
        assertEquals(asked, rig.frames.asked)
    }

    @Test fun midFadeShot() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val a = scene(ctx, "beach"); val bb = scene(ctx, "jungle")
            preload(ctx, a); preload(ctx, bb)
            val wPx = Ui.dp(ctx, 380); val hPx = Ui.dp(ctx, 200)
            var calls = 0
            val back = ArenaBackdrop(ctx)
            back.clock = { if (calls++ == 0) 0L else 200L } // the first frame starts the fade, the shot is its middle
            back.reduceMotion = { true }
            back.request(a, false); back.setStage(stageFor(a, wPx.toFloat(), hPx.toFloat(), 1.2f))
            back.reduceMotion = { false }
            back.request(bb, false); back.setStage(stageFor(bb, wPx.toFloat(), hPx.toFloat(), 1f))
            drawn(back, wPx, hPx) // starts the clock at 0 (same bounds as the view below, so no resize)
            val pane = View(ctx).apply { background = back }
            val view = sheet(ctx, label(ctx, "beach -> jungle, 200 ms into the 400 ms cross-fade"), FrameLayout(ctx).apply { addView(pane, FrameLayout.LayoutParams(wPx, hPx)) })
            paparazzi.snapshot(FrameLayout(ctx).apply { addView(view, FrameLayout.LayoutParams(-1, -1)) }, "r8-x1b-midfade-phone-${theme.name.lowercase()}", 1000)
        }
    }

    @Test fun aMissingPictureTurnsThePanelPlainAndAsksOnlyWhenAllowed() {
        val ctx = setUp(Theme.LIGHT)
        var asked = 0
        Backdrops.get = { _, _ -> asked++; null }
        val back = ArenaBackdrop(ctx)
        val v = View(ctx).apply { background = back }
        val frame = FrameLayout(ctx).apply { addView(v, FrameLayout.LayoutParams(300, 200)) }
        back.request(bg("river"), false)
        paparazzi.snapshot(frame, "r8-03-check-nodownload", 100)
        assertEquals("downloads off: no request", 0, asked)
        assertFalse(back.showsPicture)
        back.request(bg("canyon"), true)
        paparazzi.snapshot(frame, "r8-03-check-notfound", 100)
        assertEquals("one request for the one picture", 1, asked)
        assertFalse("not found: plain", back.showsPicture)
    }
}
