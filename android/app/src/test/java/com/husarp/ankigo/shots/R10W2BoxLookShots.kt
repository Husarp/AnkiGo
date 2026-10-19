package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.HttpGet
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.BoxLookPreview
import com.husarp.ankigo.ui.InfoBoxLook
import com.husarp.ankigo.ui.InfoBoxStyle
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ROUND 10 W7, "info box looks" in the real views: the battle tab (PHONE), the Study panel (tall and the 146 dp strip), the floating
 * battle window and picture-in-picture, every look (card, glass, thin frame, dark plate, HUD in the corners, type colour), light and
 * dark, on a generated stand-in picture (sky, sun, hills, trees; no downloaded art) and, for a few, on the plain gradient. Both layouts
 * (number above / inside) for the card, the glass and the HUD. Also the previews grid ([BoxLookPreview]: every look x both layouts x both
 * themes, with a picture and without) and pixel checks of what each look paints: the plate's gold edge, the frame's white edge, the type
 * stripe, the HUD having no box and standing in the corners, and the glass lining up with the picture behind it.
 * PNGs: r10w2-box-<host>-<look>-<style>-<theme>, r10w2-box-previews-<picture|plain>-<theme>.
 */
class R10W2BoxLookShots {
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

    // ---- a stand-in picture made in code: a sky with a sun, two ranges of hills, a lawn with stripes and a path, a few trees ----

    private fun scene(bg: Backdrop, scale: Float): FakeSprites.Img {
        val w = (bg.w * scale).toInt().coerceAtLeast(8)
        val h = (bg.h * scale).toInt().coerceAtLeast(8)
        val img = FakeSprites.Img(w, h)
        fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        val trees = listOf(0.08f to 0.62f, 0.2f to 0.58f, 0.9f to 0.6f, 0.62f to 0.55f, 0.48f to 0.57f)
        for (y in 0 until h) for (x in 0 until w) {
            val fx = x / w.toFloat()
            val fy = y / h.toFloat()
            var r: Int; var g: Int; var b: Int
            val far = 0.42f + 0.05f * Math.sin(fx * 9.0).toFloat()
            val mid = 0.50f + 0.06f * Math.sin(fx * 5.0 + 1.0).toFloat()
            if (fy < far) {   // sky
                val t = fy / far
                r = (70 + 130 * t).toInt(); g = (130 + 95 * t).toInt(); b = (220 + 30 * t).toInt()
                val dx = fx - 0.78f; val dy = (fy - 0.18f) * h / w
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (dist < 0.045f) { r = 255; g = 238; b = 160 } else if (dist < 0.11f) { val k = (0.11f - dist) / 0.065f; r += (60 * k).toInt(); g += (50 * k).toInt(); b += (10 * k).toInt() }
            } else if (fy < mid) {   // far hills
                val t = (fy - far) / (mid - far)
                r = (70 - 20 * t).toInt(); g = (140 - 20 * t).toInt(); b = (150 - 40 * t).toInt()
            } else {   // lawn
                val t = (fy - mid) / (1f - mid)
                r = (80 + 20 * t).toInt(); g = (170 - 25 * t).toInt(); b = (60 + 10 * t).toInt()
                if (((x / 14) % 2) == 0) { r -= 10; g -= 12; b -= 6 }
                val px = (fx - 0.5f); val py = (fy - 0.88f) * h / w
                if (px * px / 0.09f + py * py / 0.004f < 1f) { r = 196; g = 168; b = 112 }   // a path
            }
            for ((tx, ty) in trees) {
                val dx = fx - tx; val dy = (fy - ty) * h / w
                if (dx * dx + dy * dy < 0.0016f && fy >= far) { r = 28; g = 90; b = 40 }
                if (abs(dx) < 0.005f && fy > ty && fy < ty + 0.06f) { r = 90; g = 60; b = 40 }
            }
            img.px[y * w + x] = rgb(r, g, b)
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
        Host("pip", Size.PIP_169) { ctx, pic ->
            val p = BattlePanelView(ctx).apply { setCompact(true) }
            p.renderAll()
            if (pic) showPicture(ctx, p.arena)
            Scene(p, p.arena)
        },
    )

    private fun host(name: String) = hosts.first { it.name == name }

    // ---- pixels and boxes ----

    private fun render(v: View): Bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }

    /** A box on the arena's pixels: left, top, right, bottom (the card's layout box scaled from its corner, as it is drawn). */
    private class Px(val l: Int, val t: Int, val r: Int, val b: Int) {
        val w get() = r - l
        val h get() = b - t
        override fun toString() = "($l,$t)-($r,$b)"
    }

    private fun boxOf(v: View) = Px(v.left, v.top, v.left + (v.width * v.scaleX).toInt(), v.top + (v.height * v.scaleY).toInt())

    private fun shoot(host: Host, look: InfoBoxLook, style: InfoBoxStyle, theme: Theme, withPicture: Boolean = true): Scene {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(host.size, theme))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        AppPrefs.setBackgroundPictures(ctx, withPicture)
        AppPrefs.setInfoBoxLook(ctx, look)
        AppPrefs.setInfoBoxStyle(ctx, style)
        val sc = host.build(ctx, withPicture)
        val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "r10w2-box-${host.name}-${look.name.lowercase()}-${style.name.lowercase()}-${theme.name.lowercase()}${if (withPicture) "" else "-plain"}", 1000)
        check("${host.name} ${look.name} ${style.name} ${theme.name}", sc, look, withPicture)
        return sc
    }

    /** What must hold for every look: both boxes inside the arena; the HUD in two opposite corners (picture mode). */
    private fun check(tag: String, sc: Scene, look: InfoBoxLook, withPicture: Boolean) {
        val arena = sc.arena
        val d = arena.resources.displayMetrics.density
        assertTrue("$tag: the arena has a size", arena.width > 0 && arena.height > 0)
        assertEquals("$tag: the panel got the look", look, arena.infoBoxLook)
        val wild = boxOf(field(arena, "enemyBlock"))
        val ours = boxOf(field(arena, "playerCard"))
        for ((name, b) in listOf("wild" to wild, "ours" to ours)) {
            assertTrue("$tag: $name box $b inside the ${arena.width} x ${arena.height} arena", b.l >= -1 && b.t >= -1 && b.r <= arena.width + 1 && b.b <= arena.height + 1)
        }
        if (look == InfoBoxLook.HUD && withPicture) {
            assertNotNull("$tag: picture mode has a plan", arena.stagePlanNow)
            val inset = arena.frameInsetDp * d
            assertEquals("$tag: ours on the left edge", inset, ours.l.toFloat(), 1.5f)
            assertEquals("$tag: the wild one on the right edge", arena.width - inset, wild.r.toFloat(), 1.5f)
            if (arena.height > arena.width * 1.1f) {
                assertEquals("$tag: ours at the bottom", arena.height - inset, ours.b.toFloat(), 1.5f)
                assertEquals("$tag: the wild one at the top", inset, wild.t.toFloat(), 1.5f)
            } else {
                assertEquals("$tag: ours at the top", inset, ours.t.toFloat(), 1.5f)
                assertEquals("$tag: the wild one at the bottom", arena.height - inset, wild.b.toFloat(), 1.5f)
            }
        }
    }

    // ---- the shots ----

    private fun everyLook(hostName: String) {
        val h = host(hostName)
        for (theme in Theme.entries) for (look in InfoBoxLook.entries) shoot(h, look, InfoBoxStyle.ABOVE, theme)
        for (theme in Theme.entries) for (look in listOf(InfoBoxLook.TODAY, InfoBoxLook.GLASS, InfoBoxLook.HUD)) shoot(h, look, InfoBoxStyle.INSIDE, theme)
    }

    @Test fun battleTab() = everyLook("battle-tab")
    @Test fun studyPanelTall() = everyLook("study-tall")
    @Test fun studyPanelStrip() = everyLook("study-strip")
    @Test fun floatingBattle() = everyLook("float")
    @Test fun pictureInPicture() = everyLook("pip")

    @Test fun noPictureTheLooksStillWork() {
        val h = host("battle-tab")
        for (theme in Theme.entries) for (look in InfoBoxLook.entries) shoot(h, look, InfoBoxStyle.ABOVE, theme, withPicture = false)
        val s = host("study-strip")
        for (look in listOf(InfoBoxLook.GLASS, InfoBoxLook.HUD)) shoot(s, look, InfoBoxStyle.ABOVE, Theme.LIGHT, withPicture = false)
    }

    // ---- status badges, levels and the wild type chips in the looks that are the same in light and dark ----

    @Test fun aStatusAndTheInsideOfTheFixedLooksOnALightTheme() {
        val h = host("battle-tab")
        for (look in listOf(InfoBoxLook.TODAY, InfoBoxLook.FRAME, InfoBoxLook.PLATE, InfoBoxLook.HUD, InfoBoxLook.TYPE)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(h.size, Theme.LIGHT))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            AppPrefs.setBackgroundPictures(ctx, true)
            AppPrefs.setInfoBoxLook(ctx, look)
            val sc = h.build(ctx, true)
            sc.arena.setEnemyStatus("brn")
            sc.arena.setPlayerStatus("psn")
            sc.arena.setEnemyHp(40, 100, false)
            sc.arena.setPlayerHp(15, 100, false)
            val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "r10w2-box-status-${look.name.lowercase()}-light", 1000)
        }
    }

    // ---- what each look paints, in pixels ----

    private fun px(bmp: Bitmap, x: Int, y: Int) = bmp.getPixel(x.coerceIn(0, bmp.width - 1), y.coerceIn(0, bmp.height - 1))
    private fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
    private fun near(a: Int, b: Int, tol: Int) = abs(ch(a, 16) - ch(b, 16)) <= tol && abs(ch(a, 8) - ch(b, 8)) <= tol && abs(ch(a, 0) - ch(b, 0)) <= tol

    private fun meanOf(bmp: Bitmap, l: Int, t: Int, r: Int, b: Int): IntArray {
        val sum = IntArray(3)
        var n = 0
        for (y in max(0, t) until min(bmp.height, b)) for (x in max(0, l) until min(bmp.width, r)) {
            val c = bmp.getPixel(x, y)
            sum[0] += ch(c, 16); sum[1] += ch(c, 8); sum[2] += ch(c, 0); n++
        }
        return IntArray(3) { if (n == 0) 0 else sum[it] / n }
    }

    /** The arena drawn with and without the two boxes (their views invisible). */
    private class TwoRenders(val with: Bitmap, val without: Bitmap, val wild: Px, val ours: Px)

    private fun renders(sc: Scene): TwoRenders {
        val arena = sc.arena
        val wildView: View = field(arena, "enemyBlock")
        val oursView: View = field(arena, "playerCard")
        val newTag: View = field(arena, "newTag")
        val with = render(arena)
        val saved = listOf(wildView.visibility, oursView.visibility, newTag.visibility)
        wildView.visibility = View.INVISIBLE; oursView.visibility = View.INVISIBLE; newTag.visibility = View.INVISIBLE
        val without = render(arena)
        wildView.visibility = saved[0]; oursView.visibility = saved[1]; newTag.visibility = saved[2]
        return TwoRenders(with, without, boxOf(wildView), boxOf(oursView))
    }

    private fun build(theme: Theme, look: InfoBoxLook, withPicture: Boolean = true, style: InfoBoxStyle = InfoBoxStyle.ABOVE): Scene {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        AppPrefs.setBackgroundPictures(ctx, withPicture)
        AppPrefs.setInfoBoxLook(ctx, look)
        AppPrefs.setInfoBoxStyle(ctx, style)
        val sc = host("battle-tab").build(ctx, withPicture)
        val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "r10w2-box-pixels-${look.name.lowercase()}-${theme.name.lowercase()}", 1000)
        return sc
    }

    @Test fun thePlateHasGoldTrimAndTheFrameAWhiteOne() {
        for (theme in Theme.entries) {
            val d = 2f
            // plate: the top edge's first 2 dp are gold #d6b052 in both themes
            run {
                val sc = build(theme, InfoBoxLook.PLATE)
                val r = renders(sc)
                val k = sc.arena.cardScale
                val x = (r.wild.l + r.wild.r) / 2
                val gold = px(r.with, x, r.wild.t + (1f * d * k).toInt())
                assertTrue("${theme.name}: plate edge $gold is gold", near(gold, 0xFFD6B052.toInt(), 24))
                // the inside is navy
                val navy = px(r.with, r.wild.l + (4.5f * d * k).toInt(), (r.wild.t + r.wild.b) / 2)
                assertTrue("${theme.name}: plate inside ${Integer.toHexString(navy)} is navy", near(navy, 0xFF1F2537.toInt(), 28))
            }
            // frame: a white ring just inside the box, the same in both themes
            run {
                val sc = build(theme, InfoBoxLook.FRAME)
                val r = renders(sc)
                val k = sc.arena.cardScale
                val x = (r.wild.l + r.wild.r) / 2
                val edge = px(r.with, x, r.wild.t + (0.7f * d * k).toInt())
                assertTrue("${theme.name}: frame edge ${Integer.toHexString(edge)} is near white", ch(edge, 16) > 190 && ch(edge, 8) > 190 && ch(edge, 0) > 190)
                // the veil darkens the picture under it
                val under = px(r.with, r.wild.l + (4.5f * d * k).toInt(), (r.wild.t + r.wild.b) / 2)
                val over = px(r.without, r.wild.l + (4.5f * d * k).toInt(), (r.wild.t + r.wild.b) / 2)
                assertTrue("${theme.name}: the veil is darker than the picture", ch(under, 8) < ch(over, 8))
            }
        }
    }

    @Test fun theTypeLookHasAStripeOfTheTypeColourOnTheLeftOfEachBox() {
        for (theme in Theme.entries) {
            val sc = build(theme, InfoBoxLook.TYPE)
            val r = renders(sc)
            val k = sc.arena.cardScale
            val eType: Int = field(sc.arena, "eTypeColor")
            val pType: Int = field(sc.arena, "pTypeColor")
            assertTrue("${theme.name}: the wild one has a type colour", eType != 0)
            assertTrue("${theme.name}: ours has a type colour", pType != 0)
            for ((name, box, c) in listOf(Triple("wild", r.wild, eType), Triple("ours", r.ours, pType))) {
                val s = px(r.with, box.l + (2f * 2f * k).toInt(), (box.t + box.b) / 2)
                assertTrue("${theme.name} $name: stripe ${Integer.toHexString(s)} is ${Integer.toHexString(c)}", near(s, c, 30))
                // right of the stripe the box is the wash, not the stripe
                val wash = px(r.with, box.l + (8f * 2f * k).toInt(), box.t + (3f * 2f * k).toInt())
                assertTrue("${theme.name} $name: not the stripe colour any more", !near(wash, c, 6))
            }
        }
    }

    @Test fun theHudHasNoBoxAtAll() {
        for (theme in Theme.entries) {
            val sc = build(theme, InfoBoxLook.HUD)
            val r = renders(sc)
            for ((name, box) in listOf("wild" to r.wild, "ours" to r.ours)) {
                // the corner pixels of the box's area are the picture's own
                for ((x, y) in listOf(box.l + 1 to box.t + 1, box.r - 2 to box.t + 1, box.l + 1 to box.b - 2, box.r - 2 to box.b - 2)) {
                    assertEquals("${theme.name} $name: pixel ($x,$y) is the picture's", px(r.without, x, y), px(r.with, x, y))
                }
            }
            // but its text is there: some pixel inside the wild box differs
            var changed = 0
            for (y in r.wild.t until r.wild.b) for (x in r.wild.l until r.wild.r) if (px(r.with, x, y) != px(r.without, x, y)) changed++
            assertTrue("${theme.name}: the HUD's text and bars are drawn ($changed px)", changed > 200)
        }
    }

    @Test fun theCardLookIsStillTodaysCardAt92Percent() {
        for (theme in Theme.entries) {
            val sc = build(theme, InfoBoxLook.TODAY)
            val r = renders(sc)
            val k = sc.arena.cardScale
            val ctx = sc.arena.context
            val card = Ui.c(ctx, R.color.ag_card)
            // a pixel in the padding strip left of the text: 92 % of ag_card over the picture
            val x = r.wild.l + (4f * 2f * k).toInt()
            val y = (r.wild.t + r.wild.b) / 2
            val over = px(r.without, x, y)
            val a = 0xEB / 255f
            val want = ((ch(card, 16) * a + ch(over, 16) * (1 - a)).toInt() shl 16) or ((ch(card, 8) * a + ch(over, 8) * (1 - a)).toInt() shl 8) or (ch(card, 0) * a + ch(over, 0) * (1 - a)).toInt()
            assertTrue("${theme.name}: card pixel ${Integer.toHexString(px(r.with, x, y))} vs ${Integer.toHexString(want)}", near(px(r.with, x, y), want, 6))
        }
    }

    @Test fun theGlassShowsTheBlurredPictureLinedUpWithIt() {
        for (theme in Theme.entries) for (withPicture in listOf(true, false)) {
            val sc = build(theme, InfoBoxLook.GLASS, withPicture)
            val r = renders(sc)
            val k = sc.arena.cardScale
            val card = Ui.c(sc.arena.context, R.color.ag_card)
            for ((name, box) in listOf("wild" to r.wild, "ours" to r.ours)) {
                // the strip of padding left of the text, between the edge ring and the text: no text, no bars
                val l = box.l + (3f * 2f * k).toInt()
                val rr = box.l + (7f * 2f * k).toInt()
                val t = box.t + (8f * 2f * k).toInt()
                val b = box.b - (8f * 2f * k).toInt()
                val glass = meanOf(r.with, l, t, rr, b)
                val behind = meanOf(r.without, l, t, rr, b)
                // 40 % of the card colour over the (blurred, slightly more colourful) picture
                for (c in 0 until 3) {
                    val cardC = ch(card, 16 - 8 * c)
                    val want = (0.4f * cardC + 0.6f * behind[c]).toInt()
                    assertTrue("${theme.name} ${if (withPicture) "picture" else "plain"} $name channel $c: glass ${glass[c]} vs ${want} (card $cardC, behind ${behind[c]})", abs(glass[c] - want) <= 22)
                }
            }
        }
    }

    // ---- the previews ----

    private fun previewGrid(ctx: Context, caption: String, sink: MutableList<BoxLookPreview>? = null): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 12))
        }
        col.addView(TextView(ctx).apply { text = caption; setTextColor(Ui.c(ctx, R.color.ag_text)); textSize = 12f })
        for (look in InfoBoxLook.entries) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(ctx).apply { text = look.label; setTextColor(Ui.c(ctx, R.color.ag_text)); textSize = 12f }, LinearLayout.LayoutParams(Ui.dp(ctx, 120), -2))
            for (style in InfoBoxStyle.entries) {
                row.addView(BoxLookPreview(ctx, look, style).also { sink?.add(it) }, LinearLayout.LayoutParams(Ui.dp(ctx, 100), Ui.dp(ctx, 64)).also { it.setMargins(0, Ui.dp(ctx, 4), Ui.dp(ctx, 10), Ui.dp(ctx, 4)) })
            }
            col.addView(row, ViewGroup.LayoutParams(-1, -2))
        }
        return col
    }

    @Test fun thePreviewsGridOnAPictureAndOnTheGradient() {
        for (theme in Theme.entries) {
            for (withPicture in listOf(true, false)) {
                paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, theme))
                val ctx = Shots.files(paparazzi.context)
                FakeGame.install(ctx)
                Backdrops.forget()
                if (withPicture) {
                    val bg = BackdropCatalog.byId("forest")!!
                    FakeSprites.write(scene(bg, 0.5f), Backdrops.file(Sprites.dir(ctx), bg))
                    val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false, noGet)!!
                    Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
                }
                val grid = previewGrid(ctx, if (withPicture) "Previews on a picture" else "Previews on the plain arena")
                paparazzi.snapshot(grid, "r10w2-box-previews-${if (withPicture) "picture" else "plain"}-${theme.name.lowercase()}", 1000)
            }
        }
    }

    /**
     * Codex 4: the preview's `s` already holds the display density, and the text sizes, shadows and the ring used `* d * s` again (text 2 to 3 times
     * too big on a real phone). At density 2 (the harness) and density 3 (a real phone) every text line of every preview must fit its row, and
     * the overflow measured in the tile's own units must not depend on the density.
     */
    @Test fun thePreviewTextFitsItsRowAtDensity2And3() {
        val overflowDp = HashMap<String, MutableList<Float>>()
        for (density in listOf(Density.XHIGH, Density.XXHIGH)) {
            val d = density.dpiValue / 160f
            for (theme in Theme.entries) {
                val size = Size.SPLIT
                paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme).copy(
                    screenWidth = (size.wDp * d).toInt(), screenHeight = (size.hDp * d).toInt(), xdpi = density.dpiValue, ydpi = density.dpiValue, density = density))
                val ctx = Shots.files(paparazzi.context)
                FakeGame.install(ctx)
                Backdrops.forget()
                assertEquals(d, ctx.resources.displayMetrics.density, 0.01f)
                val previews = ArrayList<BoxLookPreview>()
                val grid = previewGrid(ctx, "Previews at density ${d.toInt()}", previews)
                paparazzi.snapshot(grid, "r10w2-box-previews-density${d.toInt()}-${theme.name.lowercase()}", 1000)
                assertEquals(InfoBoxLook.entries.size * InfoBoxStyle.entries.size, previews.size)
                for (p in previews) {
                    val over = p.worstOverflow
                    assertTrue("${p.look}/${p.style}: drawn", over > -1e6f)
                    assertTrue("density ${d.toInt()} ${theme.name} ${p.look}/${p.style}: a text line is $over px taller than its row", over <= 1f)
                    overflowDp.getOrPut("${p.look}/${p.style}") { ArrayList() }.add(over / d)
                }
            }
        }
        for ((k, v) in overflowDp) {
            val lo = v.min(); val hi = v.max()
            assertTrue("$k: the overflow in dp is the same at both densities ($v)", hi - lo <= 0.5f)
        }
    }

    @Test fun aPreviewFollowsSet() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        val p = BoxLookPreview(ctx, InfoBoxLook.TODAY, InfoBoxStyle.ABOVE)
        assertEquals(InfoBoxLook.TODAY, p.look)
        p.set(InfoBoxLook.PLATE, InfoBoxStyle.INSIDE)
        assertEquals(InfoBoxLook.PLATE, p.look)
        assertEquals(InfoBoxStyle.INSIDE, p.style)
        p.set(InfoBoxLook.PLATE, InfoBoxStyle.INSIDE)   // the same again: nothing happens
        assertEquals(InfoBoxLook.PLATE, p.look)
    }
}
