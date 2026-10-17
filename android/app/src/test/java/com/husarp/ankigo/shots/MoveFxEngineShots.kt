package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.movefx.FxArt
import com.husarp.ankigo.ui.movefx.FxColor
import com.husarp.ankigo.ui.movefx.FxCubic
import com.husarp.ankigo.ui.movefx.FxFilterAt
import com.husarp.ankigo.ui.movefx.FxMath
import com.husarp.ankigo.ui.movefx.FxPixelArt
import com.husarp.ankigo.ui.movefx.FxPt
import com.husarp.ankigo.ui.movefx.FxRenderer
import com.husarp.ankigo.ui.movefx.FxScript
import com.husarp.ankigo.ui.movefx.FxSprite
import com.husarp.ankigo.ui.movefx.FxTimes
import com.husarp.ankigo.ui.movefx.MoveFx
import com.husarp.ankigo.ui.movefx.MoveFxPlayer
import com.husarp.ankigo.ui.movefx.PsAfter
import com.husarp.ankigo.ui.movefx.PsEase
import com.husarp.ankigo.ui.movefx.PsEnd
import com.husarp.ankigo.ui.movefx.PsKey
import com.husarp.ankigo.ui.movefx.FxKit
import com.husarp.ankigo.ui.movefx.FxPalette
import com.husarp.ankigo.ui.movefx.MoveFxPlan
import com.husarp.ankigo.ui.movefx.MoveLook
import org.junit.Rule
import org.junit.Test

/**
 * R7-03: Tackle and the physical template's kits, frozen at three moments (the lunge, just after the hit, the burst growing), our
 * creature attacking and the wild one attacking, at the phone-wide and the picture-in-picture arena size, light and dark.
 * Placeholder creatures only (MoveFxShotKit). Files: `r703-<look>-<frame>-<theme>.png`.
 */
class MoveFxEngineShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val moments = listOf(150f, 330f, 520f)
    private val palette = FxPalette(0xFF3A1458.toInt(), 0xFFB06BFF.toInt(), 0xFF52207A.toInt(), 0xFFE1B8FF.toInt())

    private fun plan(look: MoveLook, kit: FxKit, k: Float = 1f) = MoveFxPlan(look, k, kit, palette)

    private fun shoot(name: String, build: (Context) -> View) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.TABLET_LAND, theme))
            val ctx = Shots.files(paparazzi.context)
            val frame = FrameLayout(ctx).apply { addView(build(ctx), FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "$name-${theme.name.lowercase()}", 1000)
        }
    }

    private fun sheets(prefix: String, look: MoveLook, kit: FxKit, frame: MoveFxShotKit.Frame, k: Float = 1f) =
        shoot("r703-$prefix-${frame.name.lowercase()}") { ctx -> MoveFxShotKit.sheet(ctx, prefix, frame, plan(look, kit, k), moments) }

    @Test fun tackleWide() = sheets("tackle", MoveLook.TACKLE, FxKit.NORMAL, MoveFxShotKit.Frame.WIDE)
    @Test fun tacklePip() = sheets("tackle", MoveLook.TACKLE, FxKit.NORMAL, MoveFxShotKit.Frame.PIP)

    @Test fun kitsWide() { for (kit in FxKit.entries) sheets("hit-${kit.name.lowercase()}", MoveLook.HIT_TEMPLATE, kit, MoveFxShotKit.Frame.WIDE, 1.2f) }
    @Test fun kitsPip() { for (kit in FxKit.entries) sheets("hit-${kit.name.lowercase()}", MoveLook.HIT_TEMPLATE, kit, MoveFxShotKit.Frame.PIP, 1.2f) }

    /**
     * The engine's own API in one made-up look (not a real move): a pixel flame along a Bezier path (proc + crisp), Showdown-style
     * wisps and a shard (psFx), a jagged glowing bolt (proc + stroke), droplets and stars by fixed-step ticks, the dark wash,
     * and the target's colour filter. Checks that what R7-04 / R7-08 build on draws, both ways round.
     */
    @Test fun engineDemo() {
        for (frame in MoveFxShotKit.Frame.entries) shoot("r703-demo-${frame.name.lowercase()}") { ctx ->
            MoveFxShotKit.sheetOf(ctx, "engine demo", frame, listOf(250f, 480f, 700f)) { arena, ms ->
                val stage = arena.stage
                val script = MoveFx.script(stage, plan(MoveLook.TACKLE, FxKit.NORMAL), true, 5L)!!
                val times = demoLook(script)
                val e = MoveFxPlayer(stage, script, times, 1f) { FxRenderer(ctx, script.space.unit) }
                arena.showEffect(e, ms)
            }
        }
    }

    private fun demoLook(s: FxScript): FxTimes {
        val sp = s.space
        val a = sp.fromAttacker(55f, -28f)
        val b = sp.fromTarget(-12f, 4f)
        val path = FxCubic(a, FxPt(FxMath.lerp(a.x, b.x, .33f), FxMath.lerp(a.y, b.y, .33f) - sp.len(40f)), FxPt(FxMath.lerp(a.x, b.x, .66f), FxMath.lerp(a.y, b.y, .66f) - sp.len(20f)), b)
        val dev = sp.artPixels(1f)
        s.tween(0f, 200f) { p, _ -> s.attacker.set(-10f * kotlin.math.sin(p * 3.14f)) }
        s.tween(0f, 120f) { p, _ -> s.dim = .4f * p }
        s.tween(500f, 200f) { p, _ -> s.dim = .4f * (1f - p) }
        s.proc(100f, 400f, 1) { c, p, ms ->
            val x = path.x(p); val y = path.y(p)
            c.additive = true; c.alpha = .3f
            c.tex("circle_05", 0xFFFFA030.toInt(), x, y, s.u * 40f, s.u * 40f)
            c.additive = false; c.alpha = 1f
            c.crisp("flame_big_${(ms / 70f).toInt() % 6}" + if (sp.dir < 0f) "-x" else "", x - (if (sp.dir < 0f) 22f - FxPixelArt.FLAME_HEAD_X else FxPixelArt.FLAME_HEAD_X) * dev, y - FxPixelArt.FLAME_HEAD_Y * dev, dev)
        }
        val bolt = s.jag(b.x + sp.len(6f), 0f, b.x, b.y - sp.len(4f), 9, sp.len(22f))
        s.proc(300f, 560f) { c, p, _ -> c.stroke(bolt, sp.len(5f), 0xFFFFE14D.toInt(), 1f - p, glow = sp.len(14f)); c.stroke(bolt, sp.len(1.6f), 0xFFFFFFFF.toInt(), 1f - p) }
        s.psFx(FxSprite("shard", 0, 80f, 30f), PsKey(z = 40f, scale = 1.5f, opacity = .8f, time = 0f), PsEnd(z = 200f, opacity = 0f, time = 600f), PsEase.LINEAR, PsAfter.NONE, add = true, rot = sp.pathAngle)
        s.psFx(FxSprite("ps:wisp", 0xFFFF6AD5.toInt()), PsKey(x = -30f, z = 200f, scale = 2f, opacity = .6f, time = 200f), PsEnd(scale = 4f, opacity = 0f, time = 600f), PsEase.LINEAR, PsAfter.FADE)
        s.ticks(150f, 300f) { _, _ ->
            if (s.chance(.5f)) s.particle("star_04", 0xFFFFFFFF.toInt(), s.space.betweenX(s.rnd(.2f, .9f), 0f), s.space.betweenY(.6f, s.rnd(-20f, 10f)), s0 = s.rnd(12f, 22f), s1 = 2f, life = 300f, add = true)
        }
        s.at(400f) { s.burst("star_04", 0xFFFFFFFF.toInt(), b, 6, 200f, 400f, 26f, 3f, true); s.flashOnce(.25f, 50f) }
        val f = FxColor.Chain()
        s.hit(400f, 300f, push = 8f, filter = FxFilterAt { p -> f.reset().sepia(.55f).saturate(2.2f).brightness(1.1f + .2f * kotlin.math.sin(p * 40f)).matrix })
        return FxTimes(1000f, 400f, 700f)
    }

    /** Every bitmap of the shared art on one sheet: the pixel flames (both ways), leaf, droplet, shard and some textures tinted. */
    @Test fun artSheet() = shoot("r703-art") { ctx -> ArtSheet(ctx) }

    private class ArtSheet(ctx: Context) : View(ctx) {
        private val art = FxArt.of(ctx)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val d = ctx.resources.displayMetrics.density

        override fun onDraw(c: Canvas) {
            c.drawColor(0xFF8A8F9C.toInt())
            var y = 12f * d
            fun row(keys: List<String>, scale: Float, crisp: Boolean, tint: Int = 0) {
                var x = 12f * d
                var h = 0f
                for (k in keys) {
                    val b = art.bitmap(k) ?: continue
                    paint.isFilterBitmap = !crisp
                    paint.colorFilter = if (tint != 0) art.tint(tint) else null
                    val w = b.width * scale; val hh = b.height * scale
                    c.drawBitmap(b, null, android.graphics.RectF(x, y, x + w, y + hh), paint)
                    x += w + 10f * d; h = maxOf(h, hh)
                }
                y += h + 10f * d
            }
            val px = 3f * d
            row((0..5).map { "flame_big_$it" }, px, true)
            row((0..5).map { "flame_big_$it-x" }, px, true)
            row((0..5).map { "flame_small_$it" }, px, true)
            row(listOf("leaf", "leaf-x", "leaf-y", "leaf-xy"), px, true)
            row(listOf("drop", "shard"), 1.6f * d, false)
            row(listOf("circle_02", "circle_05", "star_04", "star_06", "spark_01", "spark_05", "twirl_01", "smoke_08", "dirt_01", "magic_05"), .45f * d, false, 0xFFFFC040.toInt())
            row(listOf("ps:iceball", "ps:wisp", "ps:purplewisp", "ps:shadowball", "light_02", "fire_01", "flame_05", "slash_03"), .8f * d, false)
        }
    }
}
