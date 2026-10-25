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
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BackdropDraw
import com.husarp.ankigo.ui.BackdropSource
import com.husarp.ankigo.ui.BackdropSharp
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.StageFit
import com.husarp.ankigo.ui.Ui
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * R19-F (PLAN 274) preview: every battle background drawn by the REAL [ArenaBackdrop] (with the real [StageFit] crop) at the size the
 * real arena has in three hosts (Battle tab on a 412 x 869 dp phone, Study panel 412 x 220 dp, picture-in-picture 240 x 135 dp), as a
 * phone with Adam's density (2.625, OnePlus 6 class) would draw it: the arena is laid out at its "virtual" size and the panel scales
 * it by the same factor the real [BattlePanelView] uses. Needs the 35 pictures in /tmp/r19-bg (the public files; never in the repo):
 * skipped without them. The mode ("today" or "sharp") is read from /tmp/r19-bg-out/mode.txt and names the output folder
 * /tmp/r19-bg-out/<mode>/<host>/<id>.png (full-size PNG of the arena as it lands on the screen); /tmp/r19-bg-out/hosts.txt has the sizes.
 * The page (docs/design/r19-backgrounds.html) is built from these by a script.
 */
class R19FBackdropPreviewShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val src = File("/tmp/r19-bg")
    private val out = File("/tmp/r19-bg-out")
    private val density = 2.625f
    private val noGet: HttpGet = { _, _ -> null }

    /** One host: the arena's size at layout (virtual dp) and the factor the panel draws it at on the screen. */
    private class Host(val name: String, val label: String, val wDp: Float, val hDp: Float, val scale: Float)

    @Suppress("UNCHECKED_CAST")
    private fun findArena(v: View): ArenaView? {
        if (v is ArenaView) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findArena(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun scaleOf(v: View): Float {
        var s = 1f
        var p: View? = v
        while (p != null) { s *= p.scaleX; p = p.parent as? View }
        return s
    }

    /** Lays the real view out in Paparazzi (density 2) and reads the arena's size from it. */
    private fun measure(name: String, label: String, size: Size, build: (Context) -> View): Host {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val v = build(ctx)
        val frame = FrameLayout(ctx).apply { addView(v, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "r19f-host-$name", 1000)
        val a = findArena(v) ?: throw IllegalStateException("no arena in $name")
        val d = ctx.resources.displayMetrics.density
        return Host(name, label, a.width / d, a.height / d, scaleOf(a))
    }

    private fun studyPanel(c: Context, heightDp: Int): View {
        val p = BattlePanelView(c).apply { setStudyLayout(false, false); renderAll() }
        return FrameLayout(c).apply { addView(p, FrameLayout.LayoutParams(Ui.dp(c, 412), Ui.dp(c, heightDp), Gravity.TOP)) }
    }

    @Test fun render() {
        assumeTrue("pictures not in ${src.path}", src.isDirectory && BackdropCatalog.ALL.all { File(src, Backdrops.fileName(it)).isFile })
        val mode = File(out, "mode.txt").takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() } ?: "today"
        val hosts = listOf(
            measure("battle", "Battle tab (412 x 869 dp phone)", Size.PHONE) { c -> BattleShotKit.bound(c).root },
            measure("study", "Study panel (412 x 220 dp)", Size.PHONE) { c -> studyPanel(c, 220) },
            measure("pip", "Picture-in-picture (240 x 135 dp)", Size.PIP_169) { c -> BattlePanelView(c).apply { setCompact(true); renderAll() } },
        )
        File(out, mode).mkdirs()
        val table = StringBuilder()
        for (h in hosts) {
            val px = (h.wDp * density).toInt().toString() + " x " + (h.hDp * density).toInt()
            table.append("${h.name}\t${h.label}\tarena ${h.wDp} x ${h.hDp} dp\tvirtual $px px\tscale ${h.scale}\n")
        }
        File(out, "hosts.txt").writeText(table.toString())

        // step 1 of the ticket: the real scale (screen px per file px) of every picture in every host at three densities, and what is applied
        val scales = StringBuilder("id\tsource\tfile\thost\tdensity\tcrop\tscale\ttoday\tsharp\n")
        for (bg in BackdropCatalog.ALL) for (h in hosts) for (d in listOf(2.625f, 3f, 3.5f)) {
            val st = StageFit.fit(bg, h.wDp, h.hDp)!!
            val l = BackdropDraw.layout(bg.w, bg.h, BackdropDraw.Crop(st.cropX, st.cropY, st.cropW, st.cropH), 96, 64, h.wDp * d, h.hDp * d)
            val s = l.kx * h.scale
            val today = if (bg.source == BackdropSource.SHOWDOWN) "nearest" else "bilinear"
            val sharp = if (BackdropSharp.hard(s)) "nearest" else "lanczos copy"
            scales.append("${bg.id}\t${bg.source}\t${bg.w}x${bg.h}\t${h.name}\t$d\t${"%.0f".format(st.cropW)}x${"%.0f".format(st.cropH)}${if (st.shrunk) " shrunk" else ""}\t${"%.3f".format(s)}\t$today\t$sharp\n")
        }
        File(out, "scales.tsv").writeText(scales.toString())

        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        Backdrops.get = noGet
        Backdrops.forget()
        val dir = Sprites.dir(ctx)
        for (bg in BackdropCatalog.ALL) {
            val file = Backdrops.file(dir, bg)
            file.parentFile.mkdirs()
            File(src, Backdrops.fileName(bg)).copyTo(file, overwrite = true)
            val pic = Backdrops.fetchBlocking(dir, bg, false, noGet) ?: throw IllegalStateException("cannot decode ${bg.id}")
            Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
            for (h in hosts) {
                val vw = Math.round(h.wDp * density)
                val vh = Math.round(h.hDp * density)
                val back = ArenaBackdrop(ctx)
                back.reduceMotion = { true }
                configure(back, h.scale)
                back.request(bg, false)
                back.setStage(StageFit.fit(bg, vw / density, vh / density))
                back.setBounds(0, 0, vw, vh)
                val ow = Math.round(vw * h.scale)
                val oh = Math.round(vh * h.scale)
                val bmp = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.scale(ow / vw.toFloat(), oh / vh.toFloat())
                back.draw(canvas)
                val px = IntArray(ow * oh)
                bmp.getPixels(px, 0, ow, 0, 0, ow, oh)
                val img = FakeSprites.Img(ow, oh)
                System.arraycopy(px, 0, img.px, 0, px.size)
                FakeSprites.write(img, File(out, "$mode/${h.name}/${bg.id}.png"))
                bmp.recycle()
            }
            Backdrops.forget()
        }
    }

    /** The drawing's seams: the panel's on-screen scale (the real one comes from the views above it) and the copy made in place. */
    private fun configure(back: ArenaBackdrop, scale: Float) {
        back.screenScale = { scale }
        BackdropSharp.runner = BackdropSharp.InPlace
    }
}
