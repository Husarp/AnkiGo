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
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.ArenaGeo
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.Ui
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * ROUND 10 B20, "Pokémon size": the battle panel at 80 / 100 / 130 % in the Battle tab (PHONE), the Study panel (SPLIT: the 220 dp Full
 * preset, the 180 dp strip and the 206 dp narrow pane), the floating battle window and picture-in-picture, with a generated stand-in picture and plain, light
 * and dark. Every shot checks the DRAWN pixels: each sprite's art box lies inside the arena; with a picture, when the plan is clear, no
 * info box pixel lies on an art box; without one, the cards cover no more of a sprite than at 100 %. Sizes never go the wrong way
 * (80 <= 100 <= 130 in drawn height) and the Battle tab, which has the room, really changes. 100 % is the layout of a panel that never
 * heard of the setting. PNGs: r10-size-<host>-<picture|plain>-<size>-<theme>; the table of every combination goes to build/shots/r10-size.txt.
 */
class R10PokemonSizeShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() {
        Backdrops.forget()
        BattleShotKit.reset()
    }

    private val noGet: HttpGet = { _, _ -> null }
    private val sizes = listOf(80, 100, 130)

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o: Any, name: String): T {
        var c: Class<*>? = o.javaClass
        while (c != null) {
            try { return c.getDeclaredField(name).also { it.isAccessible = true }.get(o) as T } catch (_: NoSuchFieldException) { c = c.superclass }
        }
        throw NoSuchFieldException(name)
    }

    private class Scene(val root: View, val arena: ArenaView)

    private fun picture(ctx: Context, arena: ArenaView) {
        val bg = BackdropCatalog.byId("forest")!!
        BackdropScenes.write(Sprites.dir(ctx), bg, 0.5f)
        val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false, noGet)!!
        Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
        field<ArenaBackdrop>(arena, "backdrop").request(bg, false)
    }

    // ---- the hosts ---------------------------------------------------------------------------------------------------------------

    private class Host(val name: String, val size: Size, val build: (Context, Boolean) -> Scene)

    private fun boxed(ctx: Context, withPicture: Boolean, wDp: Int, hDp: Int, setup: (BattlePanelView) -> Unit): Scene {
        val p = BattlePanelView(ctx)
        setup(p)
        p.renderAll()
        if (withPicture) picture(ctx, p.arena)
        val frame = FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            addView(p, FrameLayout.LayoutParams(Ui.dp(ctx, wDp), Ui.dp(ctx, hDp), Gravity.TOP or Gravity.LEFT))
        }
        return Scene(frame, p.arena)
    }

    private val hosts = listOf(
        Host("battle-tab", Size.PHONE) { ctx, pic ->
            val built = BattleShotKit.bound(ctx)
            if (pic) picture(ctx, built.arena!!)
            Scene(built.root, built.arena!!)
        },
        Host("study-full", Size.SPLIT) { ctx, pic -> boxed(ctx, pic, 412, 220) { it.setStudyLayout(false, false) } },
        Host("study-strip", Size.SPLIT) { ctx, pic -> boxed(ctx, pic, 412, 180) { it.setStudyLayout(true, false) } },
        Host("study-narrow", Size.SPLIT) { ctx, pic -> boxed(ctx, pic, 206, 170) { it.setStudyLayout(true, false); it.arena.setCompact(false); it.arena.setCompact(true) } },
        Host("float", Size.FLOATBATTLE) { ctx, pic ->
            val self = object : android.content.ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }
            val v = FloatBattleView(self, {}, {}, {})
            v.javaClass.getDeclaredMethod("bind").also { it.isAccessible = true }.invoke(v)
            val p: BattlePanelView = field(v, "panel")
            p.renderAll()
            if (pic) picture(ctx, p.arena)
            val window: Any = field(v, "window")
            Scene(field(window, "root"), p.arena)
        },
        Host("pip", Size.PIP_169) { ctx, pic ->
            val p = BattlePanelView(ctx).apply { setCompact(true) }
            p.renderAll()
            if (pic) picture(ctx, p.arena)
            Scene(p, p.arena)
        },
    )

    // ---- pixels ------------------------------------------------------------------------------------------------------------------

    private fun render(v: View): Bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }

    private class PxBox(val l: Int, val t: Int, val r: Int, val b: Int) {
        override fun toString() = "($l,$t)-($r,$b)"
    }

    private fun visibleBounds(v: View): PxBox? {
        val bmp = render(v)
        var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var r = -1; var b = -1
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) if ((bmp.getPixel(x, y) ushr 24) > 16) {
            if (x < l) l = x; if (x + 1 > r) r = x + 1
            if (y < t) t = y; if (y + 1 > b) b = y + 1
        }
        return if (r < 0) null else PxBox(l, t, r, b)
    }

    private fun measuredArt(holder: View, spr: SpriteView): PxBox {
        val v = visibleBounds(spr) ?: throw AssertionError("a sprite draws nothing")
        val ox = holder.left + spr.left
        val oy = holder.top + spr.top
        return PxBox(ox + v.l, oy + v.t, ox + v.r, oy + v.b)
    }

    private fun diffInside(with: Bitmap, without: Bitmap, box: PxBox): Int {
        var n = 0
        for (y in max(0, box.t) until min(with.height, box.b)) for (x in max(0, box.l) until min(with.width, box.r)) if (with.getPixel(x, y) != without.getPixel(x, y)) n++
        return n
    }

    private class Result(val wild: PxBox, val ours: PxBox, val coverWild: Int, val coverOurs: Int, val line: String) {
        val wildH get() = wild.b - wild.t
        val oursH get() = ours.b - ours.t
    }

    /** Measures the arena as it is drawn now and checks what must hold at every size. */
    private fun check(tag: String, sc: Scene, withPicture: Boolean): Result {
        val arena = sc.arena
        val d = arena.resources.displayMetrics.density
        val wildHolder: View = field(arena, "enemyHolder")
        val playerHolder: View = field(arena, "playerHolder")
        val wildCard: View = field(arena, "enemyBlock")
        val playerCard: View = field(arena, "playerCard")
        val newTag: View = field(arena, "newTag")
        assertTrue("$tag: arena has a size", arena.width > 0 && arena.height > 0)
        val wa = measuredArt(wildHolder, arena.enemySprite)
        val pa = measuredArt(playerHolder, arena.playerSprite)
        // the drawn art of both sprites is inside the arena (a pixel of slack for rounding)
        for ((name, a) in listOf("wild" to wa, "ours" to pa)) {
            assertTrue("$tag: $name art $a inside the ${arena.width} x ${arena.height} arena", a.l >= -1 && a.t >= -1 && a.r <= arena.width + 1 && a.b <= arena.height + 1)
        }
        val with = render(arena)
        val saved = listOf(wildCard.visibility, playerCard.visibility, newTag.visibility)
        wildCard.visibility = View.INVISIBLE; playerCard.visibility = View.INVISIBLE; newTag.visibility = View.INVISIBLE
        val without = render(arena)
        wildCard.visibility = saved[0]; playerCard.visibility = saved[1]; newTag.visibility = saved[2]
        val cw = diffInside(with, without, wa)
        val co = diffInside(with, without, pa)
        val plan = arena.stagePlanNow
        if (withPicture) {
            assertTrue("$tag: picture mode has a plan", plan != null)
            if (plan!!.clear) {
                assertEquals("$tag: box pixels on the wild Pokémon's art", 0, cw)
                assertEquals("$tag: box pixels on our Pokémon's art", 0, co)
            }
        }
        val line = "%-12s %-7s %5.0f x %-4.0f dp  wild art %3d x %-3d  ours art %3d x %-3d  box px on art %d / %d  %s".format(
            tag.substringBefore(" "), if (withPicture) "picture" else "plain", arena.width / d, arena.height / d,
            wa.r - wa.l, wa.b - wa.t, pa.r - pa.l, pa.b - pa.t, cw, co,
            if (plan != null) "frames x%.2f boxes x%.2f %s".format(plan.spriteScale, plan.k, if (plan.clear) "clear" else "FALLBACK")
            else field<ArenaGeo.Plan?>(arena, "plan").let { p -> if (p == null) "study layout" else "${p.layout} a=${"%.2f".format(p.a)}" })
        return Result(wa, pa, cw, co, line)
    }

    private fun shoot(host: Host, withPicture: Boolean): List<String> {
        val byTheme = HashMap<Theme, List<Result>>()
        val table = ArrayList<String>()
        for (theme in Theme.entries) {
            val results = ArrayList<Result>()
            for (pct in sizes) {
                paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(host.size, theme))
                val ctx = Shots.files(paparazzi.context)
                FakeGame.install(ctx)
                AppPrefs.setBackgroundPictures(ctx, withPicture)
                AppPrefs.setPokemonSize(ctx, pct)
                val sc = host.build(ctx, withPicture)
                val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
                val name = "r10-size-${host.name}-${if (withPicture) "picture" else "plain"}-$pct-${theme.name.lowercase()}"
                paparazzi.snapshot(frame, name, 1000)
                val r = check("${host.name} $pct", sc, withPicture)
                results += r
                if (theme == Theme.LIGHT) table += "$pct %: " + r.line
            }
            byTheme[theme] = results
            val (r80, r100, r130) = results
            val tag = "${host.name} ${if (withPicture) "picture" else "plain"} ${theme.name}"
            // never the wrong way round
            assertTrue("$tag: wild art 80 % (${r80.wildH}) <= 100 % (${r100.wildH}) <= 130 % (${r130.wildH})", r80.wildH <= r100.wildH && r100.wildH <= r130.wildH)
            assertTrue("$tag: our art 80 % (${r80.oursH}) <= 100 % (${r100.oursH}) <= 130 % (${r130.oursH})", r80.oursH <= r100.oursH && r100.oursH <= r130.oursH)
            // without a picture the cards cover no more of a sprite than at 100 % (the plain layouts' art box is a 85 % model of the drawn pixels: a little slack)
            if (!withPicture) for ((name, a, b) in listOf(Triple("wild", r130.coverWild, r100.coverWild), Triple("ours", r130.coverOurs, r100.coverOurs))) {
                assertTrue("$tag: $name card pixels on art at 130 % ($a) vs 100 % ($b)", a <= b + 40)
            }
            if (host.name == "battle-tab") {
                // the Battle tab has the room: both Pokémon really change
                assertTrue("$tag: wild art grows with the size (${r80.wildH} / ${r100.wildH} / ${r130.wildH})", r80.wildH < r130.wildH)
                assertTrue("$tag: our art grows with the size (${r80.oursH} / ${r100.oursH} / ${r130.oursH})", r80.oursH < r130.oursH)
            }
        }
        return table
    }

    private val all = ArrayList<String>()

    private fun host(name: String) {
        val h = hosts.first { it.name == name }
        all += "== ${h.name} (${h.size.name} ${h.size.wDp} x ${h.size.hDp} dp)"
        all += shoot(h, withPicture = true)
        all += shoot(h, withPicture = false)
        File(Shots.outDir, "r10-size-${h.name}.txt").writeText(all.joinToString("\n") + "\n")
    }

    @Test fun battleTab() = host("battle-tab")
    @Test fun studyPanelFull() = host("study-full")
    @Test fun studyPanelStrip() = host("study-strip")
    @Test fun studyPanelNarrow() = host("study-narrow")
    @Test fun floatingBattle() = host("float")
    @Test fun pictureInPicture() = host("pip")

    // ---- 100 % is the layout before the setting ------------------------------------------------------------------------------------

    private fun layoutOf(sc: Scene): List<Int> {
        val a = sc.arena
        val out = ArrayList<Int>()
        for (n in listOf("enemyHolder", "playerHolder", "enemyBlock", "playerCard", "platEnemy", "platPlayer")) {
            val v: View = field(a, n)
            out += listOf(v.left, v.top, v.width, v.height, (v.scaleX * 1000).toInt())
        }
        return out
    }

    @Test fun aHundredPercentPanelIsLaidOutLikeOneThatNeverHeardOfTheSetting() {
        for (host in hosts) for (withPicture in listOf(true, false)) {
            val seen = ArrayList<List<Int>>()
            for (stored in listOf<Int?>(null, 100, 131 /* out of range = 100 */)) {
                paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(host.size, Theme.LIGHT))
                val ctx = Shots.files(paparazzi.context)
                FakeGame.install(ctx)
                AppPrefs.setBackgroundPictures(ctx, withPicture)
                if (stored != null) ctx.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE).edit().putInt("pokemonSize", stored).apply()
                val sc = host.build(ctx, withPicture)
                val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
                paparazzi.snapshot(frame, "r10-size-parity", 1000)
                seen += layoutOf(sc)
            }
            val tag = "${host.name} ${if (withPicture) "picture" else "plain"}"
            assertEquals("$tag: an explicit 100 is the same layout as nothing stored", seen[0], seen[1])
            assertEquals("$tag: 131 (out of range) is the same layout as 100", seen[0], seen[2])
        }
    }
}
