package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BarLookPreview
import com.husarp.ankigo.ui.BarView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.HpBarLook
import com.husarp.ankigo.ui.InfoBoxLook
import com.husarp.ankigo.ui.InfoBoxSkin
import com.husarp.ankigo.ui.InfoBoxStyle
import com.husarp.ankigo.ui.PixelBar
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * Round 10 W8 (D9-E): the info boxes' HP / XP bars in the Today and Pixel looks. Shots: the bars at 2x in both looks (and the
 * BarLookPreview tile), the Study panel and the Battle tab with the boxes in PIXEL, both number layouts, light + dark. Checks on the
 * views: the pixel bars are laid out 2 outline units taller than today's, the boxes still hold their content and clear the Pokemon, the
 * number inside the bar is still drawn, and the pixel bar really draws hard edges (cut corners, no half-covered pixels).
 * PNGs: r10w2-pixelbars-*, r10w2-pixelbox-<host>-<style>-<look>-<theme>.
 */
class R10W2PixelBarShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() { BattleShotKit.reset() }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o: Any, name: String): T {
        var c: Class<*>? = o.javaClass
        while (c != null) {
            try { return c.getDeclaredField(name).also { it.isAccessible = true }.get(o) as T } catch (_: NoSuchFieldException) { c = c.superclass }
        }
        throw NoSuchFieldException(name)
    }

    private fun fill(arena: ArenaView) {
        arena.setEnemyInfo("Duckling", 54, false, listOf(Type.WATER))
        arena.setEnemyHp(245, 312, false)
        arena.setEnemyStatus("brn")
        arena.setEnemyShown(true)
        arena.setPlayerInfo("u", "Sparkmouse", 56)
        arena.setPlayerHp(198, 301, false)
        arena.setPlayerXp(6400, 10000, false)
    }

    // ---- the bars at 2x, both looks -------------------------------------------------------------------------------------------------

    private fun barsAt2x(ctx: Context, look: HpBarLook): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            val p = Ui.dp(ctx, 12); setPadding(p, p, p, p)
        }
        val d = ctx.resources.displayMetrics.density
        fun bar(f: Float, color: Int, innerDp: Float): BarView = BarView(ctx).also {
            it.look = look
            it.set(f, color, false)
            val inner = Math.round(innerDp * d)
            col.addView(it, LinearLayout.LayoutParams(Ui.dp(ctx, 300), if (look == HpBarLook.PIXEL) PixelBar.outerPx(inner, d) else inner).also { lp -> lp.topMargin = Ui.dp(ctx, 8) })
        }
        for (f in listOf(0.86f, 0.42f, 0.14f)) bar(f, Ui.hpColor(ctx, f), 14f)
        bar(0.64f, Ui.c(ctx, R.color.ag_sky), 6f)
        col.addView(BarLookPreview(ctx, look), LinearLayout.LayoutParams(Ui.dp(ctx, 300), -2).also { it.topMargin = Ui.dp(ctx, 12) })
        return col
    }

    @Test fun barsAtTwoTimesInBothLooks() {
        for (look in HpBarLook.entries) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val v = barsAt2x(ctx, look)
            paparazzi.snapshot(FrameLayout(ctx).apply { addView(v, FrameLayout.LayoutParams(-1, -2, Gravity.TOP)) }, "r10w2-pixelbars-${look.name.lowercase()}-${theme.name.lowercase()}", 1000)
        }
    }

    @Test fun thePreviewShowsThreeHpBarsAndAnXpBarInTheGivenLook() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val p = BarLookPreview(ctx, HpBarLook.TODAY)
        assertEquals(4, p.bars().size)
        assertTrue(p.bars().all { it.look == HpBarLook.TODAY })
        val today = p.bars().map { (it.layoutParams as ViewGroup.LayoutParams).height }
        p.set(HpBarLook.PIXEL)
        assertEquals(HpBarLook.PIXEL, p.look)
        assertTrue(p.bars().all { it.look == HpBarLook.PIXEL })
        val pixel = p.bars().map { (it.layoutParams as ViewGroup.LayoutParams).height }
        for (i in today.indices) assertTrue("bar $i grows in the pixel look ($pixel vs $today)", pixel[i] > today[i])
        p.set(HpBarLook.TODAY)
        assertEquals(today, p.bars().map { (it.layoutParams as ViewGroup.LayoutParams).height })
    }

    // ---- the pixel bar draws hard edges ---------------------------------------------------------------------------------------------

    private fun draw(v: View, w: Int, h: Int): Bitmap {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
    }

    @Test fun thePixelBarHasCutCornersHardEdgesAndFiveStepFill() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val d = ctx.resources.displayMetrics.density
        val inner = Math.round(7 * d)
        val h = PixelBar.outerPx(inner, d)
        val u = PixelBar.unitPx(inner, d)
        val w = 200
        val bar = BarView(ctx).also { it.look = HpBarLook.PIXEL; it.set(0.86f, Ui.hpColor(ctx, 0.86f), false) }
        assertEquals(u, run { bar.layout(0, 0, w, h); bar.pixelUnit() })
        val bmp = draw(bar, w, h)
        for ((x, y) in listOf(0 to 0, w - 1 to 0, 0 to h - 1, w - 1 to h - 1, u - 1 to u - 1)) assertEquals("corner ($x,$y) is cut out", 0, Color.alpha(bmp.getPixel(x, y)))
        assertTrue("the top edge is outline", Color.alpha(bmp.getPixel(w / 2, 0)) > 200)
        assertTrue("the left edge is outline", Color.alpha(bmp.getPixel(0, h / 2)) > 200)
        // the fill ends on a 5 % step: 86 % -> 85 % of the track inside the outline
        val innerW = w - 2 * u
        val fillEnd = u + PixelBar.fillPx(innerW, 0.86f)
        val y = h / 2
        val last = bmp.getPixel(fillEnd - 1, y); val after = bmp.getPixel(fillEnd, y)
        assertTrue("the fill's last pixel is the HP green, not the track", last != after)
        assertEquals(255, Color.alpha(last)); assertEquals(255, Color.alpha(after))
        // hard pixels: every pixel on the middle row is either fully the fill, fully the track or the outline (no blended edge colours)
        val seen = HashSet<Int>()
        for (x in 0 until w) seen += bmp.getPixel(x, y)
        assertTrue("a middle row of a pixel bar has at most 4 colours (outline, fill, track, ghost-free): $seen", seen.size <= 4)
        // the highlight on top of the fill is lighter than its middle, the bottom band darker
        val topPx = bmp.getPixel(u + 2, u); val midPx = bmp.getPixel(u + 2, h / 2); val botPx = bmp.getPixel(u + 2, h - u - 1)
        fun lum(c: Int) = Color.red(c) + Color.green(c) + Color.blue(c)
        assertTrue("highlight lighter than the middle", lum(topPx) > lum(midPx))
        assertTrue("bottom band darker than the middle", lum(botPx) < lum(midPx))
        // today's look draws rounded ends: the corner pixel is not opaque and there is no outline
        bar.look = HpBarLook.TODAY
        val today = draw(bar, w, Math.round(7 * d))
        assertTrue("Today's corner is rounded away", Color.alpha(today.getPixel(0, 0)) < 255)
    }

    @Test fun theFillStepsAcrossTheAnimation() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val d = ctx.resources.displayMetrics.density
        val h = PixelBar.outerPx(Math.round(7 * d), d)
        val u = PixelBar.unitPx(Math.round(7 * d), d)
        val bar = BarView(ctx).also { it.look = HpBarLook.PIXEL }
        val ends = HashSet<Int>()
        for (i in 0..100) {
            bar.set(i / 100f, Ui.hpColor(ctx, i / 100f), false)
            val bmp = draw(bar, 200, h)
            var x = 200 - u - 1
            val track = bmp.getPixel(200 - u - 1, h / 2)
            while (x > u && bmp.getPixel(x, h / 2) == track) x--
            ends += x
        }
        assertTrue("101 fractions draw at most 21 distinct fill ends (${ends.size})", ends.size <= 21)
    }

    // ---- the boxes in the Study panel and the Battle tab ----------------------------------------------------------------------------

    private class Scene(val root: View, val arena: ArenaView)

    private val hosts = listOf<Triple<String, Size, (Context) -> Scene>>(
        Triple("study-tall", Size.SPLIT) { c ->
            val p = BattlePanelView(c)
            p.setStudyLayout(false, false)
            p.renderAll()
            Scene(FrameLayout(c).apply {
                setBackgroundColor(Ui.c(c, R.color.ag_base))
                addView(p, FrameLayout.LayoutParams(Ui.dp(c, 412), Ui.dp(c, 400), Gravity.TOP or Gravity.LEFT))
            }, p.arena)
        },
        Triple("battle", Size.PHONE) { c -> val b = BattleShotKit.bound(c); Scene(b.root, b.arena!!) },
    )

    @Test fun theBoxesInThePixelLookInBothLayouts() {
        for ((host, size, build) in hosts) for (style in InfoBoxStyle.entries) for (theme in Theme.entries) {
            val dims = HashMap<HpBarLook, List<Int>>()
            for (look in HpBarLook.entries) {
                paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
                val ctx = Shots.files(paparazzi.context)
                FakeGame.install(ctx)
                AppPrefs.setInfoBoxStyle(ctx, style)
                AppPrefs.setHpBarLook(ctx, look)
                val sc = build(ctx)
                fill(sc.arena)
                val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
                val name = "r10w2-pixelbox-$host-${style.name.lowercase()}-${look.name.lowercase()}-${theme.name.lowercase()}"
                paparazzi.snapshot(frame, name, 1000)
                val d = ctx.resources.displayMetrics.density
                val bars = listOf<BarView>(field(sc.arena, "eHp"), field(sc.arena, "pHp"), field(sc.arena, "pXp"))
                dims[look] = bars.map { it.height }
                for (b in bars) assertEquals("$name: bar look", look, b.look)
                for (card in listOf<ViewGroup>(field(sc.arena, "enemyCard"), field(sc.arena, "playerCard"))) {
                    val given = card.height
                    card.measure(View.MeasureSpec.makeMeasureSpec(card.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    assertTrue("$name: the box holds its content (content ${card.measuredHeight}, given $given)", card.measuredHeight <= given + 2 * d)
                }
                if (look == HpBarLook.PIXEL) {
                    for (b in bars) if (b.visibility == View.VISIBLE) assertTrue("$name: the pixel bar has an outline unit (${b.height} px)", b.pixelUnit() >= 1)
                    if (style == InfoBoxStyle.INSIDE) {
                        for (b in bars.take(2)) assertTrue("$name: the number inside the bar is drawn", b.labelShown())
                    }
                }
                assertTrue("$name: nothing covers a Pokemon", sc.arena.stagePlanNow?.clear ?: true)
            }
            val tag = "$host ${style.name} ${theme.name}"
            val t = dims[HpBarLook.TODAY]!!; val p = dims[HpBarLook.PIXEL]!!
            for (i in t.indices) if (t[i] > 0) assertTrue("$tag: bar $i is 2 units taller in pixel (${p[i]} vs ${t[i]})", p[i] > t[i])
        }
    }

    /**
     * Codex 6 / M3: the pixel bar's outline is the theme's text colour, so on the fixed-dark looks (thin frame, dark plate, HUD) it was dark
     * on a dark plate in the light theme. Those looks hand the bars the fixed dark palette's text colour: the outline is light in BOTH
     * themes; the other looks keep the theme's own (dark in light, light in dark).
     */
    @Test fun theOutlineIsLightOnTheFixedDarkLooksInBothThemes() {
        fun lum(c: Int) = Color.red(c) + Color.green(c) + Color.blue(c)
        val (host, size, build) = hosts[0]
        for (look in InfoBoxLook.entries) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            AppPrefs.setInfoBoxStyle(ctx, InfoBoxStyle.ABOVE)
            AppPrefs.setHpBarLook(ctx, HpBarLook.PIXEL)
            AppPrefs.setInfoBoxLook(ctx, look)
            val sc = build(ctx)
            fill(sc.arena)
            val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
            val fixed = look == InfoBoxLook.FRAME || look == InfoBoxLook.PLATE || look == InfoBoxLook.HUD
            paparazzi.snapshot(frame, "r10w2-pixelbar-outline-${look.name.lowercase()}-${theme.name.lowercase()}", 1000)
            for (n in listOf("eHp", "pHp", "pXp")) {
                val bar: BarView = field(sc.arena, n)
                if (bar.visibility != View.VISIBLE || bar.width <= 0) continue
                val tag = "$host ${look.name} ${theme.name} $n"
                if (fixed) assertEquals("$tag: the fixed palette's text colour", InfoBoxSkin.ink(look)!!.text, bar.outlineColor)
                else assertEquals("$tag: the theme's own", null, bar.outlineColor)
                val px = draw(bar, bar.width, bar.height).getPixel(bar.width / 2, 0)
                assertTrue("$tag: the outline is drawn", Color.alpha(px) > 200)
                if (fixed || theme == Theme.DARK) assertTrue("$tag: the outline is light (${Integer.toHexString(px)})", lum(px) > 600)
                else assertTrue("$tag: the outline is dark in the light theme (${Integer.toHexString(px)})", lum(px) < 300)
            }
        }
    }

    @Test fun aBigFontKeepsTheNumbersInsideThePixelBars() {
        for (scale in listOf(1.3f, 2.0f)) for ((host, size, build) in hosts) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT).copy(fontScale = scale))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            AppPrefs.setInfoBoxStyle(ctx, InfoBoxStyle.INSIDE)
            AppPrefs.setHpBarLook(ctx, HpBarLook.PIXEL)
            val sc = build(ctx)
            fill(sc.arena)
            val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "r10w2-pixelbox-font${(scale * 10).toInt()}-$host", 1000)
            val tag = "$host x$scale"
            for (n in listOf("eHp", "pHp")) {
                val bar: BarView = field(sc.arena, n)
                assertTrue("$tag: $n number is drawn (bar ${bar.width} x ${bar.height})", bar.labelShown())
            }
        }
    }
}
