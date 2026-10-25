package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.HttpGet
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.InfoBoxPrefs
import com.husarp.ankigo.ui.Ui
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * R19-A (PLAN 277): the wild info box stays on its NEW mark. The cause was a stale layout: [ArenaView] puts the two boxes by changing
 * their layout margins in onMeasure, but they live one level down (in the card layer), whose own measure / layout were skipped when only
 * the arena asked for a new layout (the picture arrived, a sprite's art changed, a setting): the boxes stayed where the old plan put them,
 * the NEW sticker (a direct child) moved to the new plan's place. Every arena size, picture mode (the picture arrives after the first layout)
 * and plain mode (the card layout changes after the first layout), light and dark; the wild one has a tall art box (a Shelmet's shell).
 * "Bigger info boxes" (PLAN 276) on in the tall Battle tab, and live. PNGs: r19a-<host>-<picture|plain>[-big]-<light|dark>.
 */
class R19AInfoBoxShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = Backdrops.forget()

    private val noGet: HttpGet = { _, _ -> null }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o: Any, name: String): T {
        var c: Class<*>? = o.javaClass
        while (c != null) {
            try { return c.getDeclaredField(name).also { it.isAccessible = true }.get(o) as T } catch (_: NoSuchFieldException) { c = c.superclass }
        }
        throw NoSuchFieldException(name)
    }

    /** A tall wild art box: a shell filling most of its frame (what the plan has to keep clear of). */
    private fun tallBlob(color: Int): FakeSprites.Img {
        val s = 96
        val img = FakeSprites.Img(s, s)
        val ry = 0.46 * s
        val rx = 0.36 * s
        val cy = s - ry - 1
        for (y in 0 until s) for (x in 0 until s) {
            val dx = (x - s / 2.0) / rx; val dy = (y - cy) / ry
            val d = dx * dx + dy * dy
            if (d <= 1.0) img.px[y * s + x] = if (d > 0.8) 0xFF6B5B2E.toInt() else color
        }
        return img
    }

    private fun device(w: Int, h: Int, theme: Theme): DeviceConfig = Shots.device(Size.PHONE, theme).copy(
        screenWidth = w * 2, screenHeight = h * 2, orientation = if (w > h) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
    )

    private fun install(ctx: Context) {
        FakeGame.install(ctx)
        val dir = Sprites.dir(ctx)
        for (sub in listOf("front", "static")) FakeSprites.write(tallBlob(0xFFD8C27A.toInt()), File(dir, "$sub/54.png"))
        val st = GameRepo.state!!
        st.caught -= st.battle!!.wild.species   // the wild one is new: the NEW sticker
    }

    private fun load(ctx: Context, bg: Backdrop) {
        BackdropScenes.write(Sprites.dir(ctx), bg, 0.5f)
        val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false, noGet)!!
        Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
    }

    private fun layoutRoot(v: View, wPx: Int, hPx: Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, wPx, hPx)
    }

    /** What a host builds: the view to lay out (panel or screen), the arena in it, and its size in px. */
    private class Scene(val root: View, val arena: ArenaView, val wPx: Int, val hPx: Int)

    private fun battle(ctx: Context): Scene {
        val b = BattleShotKit.bound(ctx)
        return Scene(b.root, b.arena!!, ctx.resources.configuration.screenWidthDp * 2, ctx.resources.configuration.screenHeightDp * 2)
    }

    private fun boxed(ctx: Context, wDp: Int, hDp: Int, setup: (BattlePanelView) -> Unit): Scene {
        val p = BattlePanelView(ctx)
        setup(p)
        p.renderAll()
        val frame = FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            addView(p, FrameLayout.LayoutParams(Ui.dp(ctx, wDp), Ui.dp(ctx, hDp), Gravity.TOP or Gravity.LEFT))
        }
        return Scene(frame, p.arena, ctx.resources.configuration.screenWidthDp * 2, ctx.resources.configuration.screenHeightDp * 2)
    }

    private fun pip(ctx: Context): Scene {
        val p = BattlePanelView(ctx).apply { setCompact(true) }
        p.renderAll()
        return Scene(p, p.arena, ctx.resources.configuration.screenWidthDp * 2, ctx.resources.configuration.screenHeightDp * 2)
    }

    private fun floating(ctx: Context): Scene {
        val self = object : android.content.ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        val bind = v.javaClass.getDeclaredMethod("bind").also { it.isAccessible = true }
        bind.invoke(v)
        val p: BattlePanelView = field(v, "panel")
        p.renderAll()
        val window: Any = field(v, "window")
        return Scene(field(window, "root"), p.arena, ctx.resources.configuration.screenWidthDp * 2, ctx.resources.configuration.screenHeightDp * 2)
    }

    /** What is wrong with the wild box (px -> dp): it must be where the plan put it, and the NEW sticker on it. */
    private fun problems(sc: Scene, picture: Boolean): List<String> {
        val a = sc.arena
        val d = a.resources.displayMetrics.density
        val card: View = field(a, "enemyBlock")
        val sticker: View = field(a, "newTag")
        val out = ArrayList<String>()
        val plan = a.stagePlanNow
        if (picture) {
            if (plan == null) { out += "no picture plan"; return out }
            if (Math.abs(card.left - plan.wildCard.x * d) > 1.5f || Math.abs(card.top - plan.wildCard.y * d) > 1.5f)
                out += "wild box at (${card.left / d}, ${card.top / d}) dp, the plan says (${plan.wildCard.x}, ${plan.wildCard.y})"
            val pc: View = field(a, "playerCard")
            if (Math.abs(pc.left - plan.playerCard.x * d) > 1.5f || Math.abs(pc.top - plan.playerCard.y * d) > 1.5f)
                out += "our box at (${pc.left / d}, ${pc.top / d}) dp, the plan says (${plan.playerCard.x}, ${plan.playerCard.y})"
        }
        else {   // plain mode: the layout's own plan (the study layouts have none)
            val plain: com.husarp.ankigo.ui.ArenaGeo.Plan? = field(a, "plan")
            if (plain != null && a.stagePlanNow == null && !isStudy(a) &&
                (Math.abs(card.left - plain.wildCard.x * d) > 1.5f || Math.abs(card.top - plain.wildCard.y * d) > 1.5f))
                out += "wild box at (${card.left / d}, ${card.top / d}) dp, the plain plan says (${plain.wildCard.x}, ${plain.wildCard.y})"
        }
        // the sticker (its pivot) is on the box: within a box width / height of its corner, never alone far away
        val sx = sticker.left + sticker.translationX
        val sy = sticker.top + sticker.translationY
        val w = card.width * card.scaleX; val h = card.height * card.scaleY
        if (sticker.visibility == View.VISIBLE && (sx < card.left - 12 * d || sx > card.left + w + 12 * d || sy < card.top - 12 * d || sy > card.top + h + 12 * d))
            out += "NEW at (${sx / d}, ${sy / d}) dp is off the box (${card.left / d}, ${card.top / d}) ${w / d} x ${h / d}"
        return out
    }

    private fun isStudy(a: ArenaView): Boolean = field<Boolean>(a, "compact") || field<Boolean>(a, "studyStrip")

    private fun scene(host: String, w: Int, h: Int, picture: Boolean, big: Boolean = false, build: (Context) -> Scene) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = device(w, h, theme))
            val ctx = Shots.files(paparazzi.context)
            install(ctx)
            InfoBoxPrefs.setBig(ctx, big)   // R19-A (PLAN 276): the setting, read when the arena is made
            val sc = build(ctx)
            val arena = sc.arena
            arena.setNewTag(true); arena.setEnemyCardShown(true)
            val bg = BackdropCatalog.byId("forest")!!
            if (picture) {
                layoutRoot(sc.root, sc.wPx, sc.hPx)      // the first layout, before the picture
                load(ctx, bg)
                field<ArenaBackdrop>(arena, "backdrop").request(bg, false)   // the picture arrives: the arena (only) asks for a new layout
            } else {
                arena.cardsUnderOwn = false
                layoutRoot(sc.root, sc.wPx, sc.hPx)      // the classic corners first
                arena.cardsUnderOwn = true               // then the cards under their own Pokémon
            }
            layoutRoot(sc.root, sc.wPx, sc.hPx)
            val name = "r19a-$host-${if (picture) "picture" else "plain"}${if (big) "-big" else ""}-${theme.name.lowercase()}"
            val bad = problems(sc, picture)
            paparazzi.snapshot(FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }, name, 1000)
            val bad2 = problems(sc, picture)   // and after the frame (the sprites came in)
            check(bad.isEmpty() && bad2.isEmpty()) { "$name: $bad $bad2" }
        }
    }

    @Test fun battleTabTallPicture() = scene("battle-412x869", 412, 869, true, build = ::battle)
    @Test fun battleTabTallPlain() = scene("battle-412x869", 412, 869, false, build = ::battle)
    @Test fun battleTab360Picture() = scene("battle-360x800", 360, 800, true, build = ::battle)
    @Test fun battleTab360Plain() = scene("battle-360x800", 360, 800, false, build = ::battle)
    @Test fun studyPanelPicture() = scene("study-412x400", 412, 400, true) { boxed(it, 412, 400) { p -> p.setStudyLayout(false, false) } }
    @Test fun studyPanelPlain() = scene("study-412x400", 412, 400, false) { boxed(it, 412, 400) { p -> p.setStudyLayout(false, false) } }
    @Test fun studyStripPicture() = scene("strip-412x180", 412, 180, true) { boxed(it, 412, 180) { p -> p.setStudyLayout(true, false) } }
    @Test fun studyStripPlain() = scene("strip-412x180", 412, 180, false) { boxed(it, 412, 180) { p -> p.setStudyLayout(true, false) } }
    @Test fun pipPicture() = scene("pip-240x160", 240, 160, true, build = ::pip)
    @Test fun pipPlain() = scene("pip-240x160", 240, 160, false, build = ::pip)
    @Test fun floatingPicture() = scene("float-320x300", 320, 300, true, build = ::floating)
    @Test fun floatingPlain() = scene("float-320x300", 320, 300, false, build = ::floating)

    // ---- "Bigger info boxes" (PLAN 276): on, in the tall Battle tab (two phone widths), picture and plain

    @Test fun bigBattleTabTallPicture() = scene("battle-412x869", 412, 869, true, big = true, build = ::battle)
    @Test fun bigBattleTabTallPlain() = scene("battle-412x869", 412, 869, false, big = true, build = ::battle)
    @Test fun bigBattleTab360Picture() = scene("battle-360x800", 360, 800, true, big = true, build = ::battle)
    @Test fun bigBattleTab360Plain() = scene("battle-360x800", 360, 800, false, big = true, build = ::battle)

    /** The arena asks for a new layout, as a setting or a new picture does, and the screen lays out. */
    private fun relayout(sc: Scene) { sc.arena.requestLayout(); layoutRoot(sc.root, sc.wPx, sc.hPx) }

    /** Where the two boxes are as drawn (dp): wild x, y, w, h, ours x, y, w, h. */
    private fun boxes(a: ArenaView): List<Float> {
        val d = a.resources.displayMetrics.density
        val out = ArrayList<Float>()
        for (n in listOf("enemyBlock", "playerCard")) {
            val v: View = field(a, n)
            out += listOf(v.left / d, v.top / d, v.width * v.scaleX / d, v.height * v.scaleY / d)
        }
        return out
    }

    /**
     * The setting follows live (the arena only asks for a new layout, as when Settings changes it) and only where the arena has room: the tall
     * Battle tab's boxes grow by about 1.3, the Study panel, the strip, picture-in-picture and the floating window do not change at all.
     */
    @Test fun theSettingMakesTheTallBattleTabsBoxesBiggerLiveAndLeavesSmallWindowsAlone() {
        val hosts = listOf<Triple<String, Pair<Int, Int>, (Context) -> Scene>>(
            Triple("battle-412x869", 412 to 869, ::battle), Triple("battle-360x800", 360 to 800, ::battle),
            Triple("study-412x400", 412 to 400) { boxed(it, 412, 400) { p -> p.setStudyLayout(false, false) } },
            Triple("strip-412x180", 412 to 180) { boxed(it, 412, 180) { p -> p.setStudyLayout(true, false) } },
            Triple("pip-240x160", 240 to 160, ::pip), Triple("float-320x300", 320 to 300, ::floating),
        )
        for ((host, size, build) in hosts) for (picture in listOf(true, false)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = device(size.first, size.second, Theme.LIGHT))
            val ctx = Shots.files(paparazzi.context)
            install(ctx)
            val sc = build(ctx)
            val a = sc.arena
            a.setNewTag(true); a.setEnemyCardShown(true)
            val bg = BackdropCatalog.byId("forest")!!
            if (picture) { load(ctx, bg); field<ArenaBackdrop>(a, "backdrop").request(bg, false) }
            relayout(sc)                            // a box's optional parts follow the plan one layout later: lay out until it is steady
            relayout(sc)
            val offScale = a.cardScale
            val off = boxes(a)
            a.bigBoxes = true                       // the setting turned on while the arena is open
            relayout(sc)
            relayout(sc)
            val onScale = a.cardScale
            val on = boxes(a)
            val roomy = host.startsWith("battle")
            if (roomy) {
                check(onScale > offScale * 1.1f) { "$host picture=$picture: boxes x$offScale -> x$onScale, not bigger" }
                check(problems(sc, picture).isEmpty()) { "$host picture=$picture: ${problems(sc, picture)}" }
            } else {
                check(Math.abs(onScale - offScale) < 0.0001f) { "$host picture=$picture: a small window changed: x$offScale -> x$onScale" }
                check(off.zip(on).all { (x, y) -> Math.abs(x - y) < 0.01f }) { "$host picture=$picture: a small window moved: $off -> $on" }
            }
            a.bigBoxes = false                      // and off again
            relayout(sc)
            relayout(sc)
            check(Math.abs(a.cardScale - offScale) < 0.0001f && boxes(a).zip(off).all { (x, y) -> Math.abs(x - y) < 0.01f }) { "$host picture=$picture: off again is not as before" }
        }
    }
}
