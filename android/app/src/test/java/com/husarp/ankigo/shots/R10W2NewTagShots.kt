package com.husarp.ankigo.shots

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
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
import com.husarp.ankigo.ui.InfoBoxLook
import com.husarp.ankigo.ui.InfoBoxStyle
import com.husarp.ankigo.ui.NewTagRule
import com.husarp.ankigo.ui.Ui
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * ROUND 10 wave 2 (G): the NEW sticker on the wild info box in the nine panel shapes of the F2 page (battle tab, study panel tall /
 * short / strip, landscape study, landscape battle, split screen, floating battle window, picture-in-picture), light + dark, a NEW wild
 * species, on a generated stand-in picture. Each shot checks the DRAWN sticker (its turned bounds from the view's own left / top /
 * translation / scale / rotation): whole inside the arena, clear of the HP numbers, today's corner kept exactly when the rule says it
 * fits, otherwise another corner of the wild box. The cut / fits case of every shape goes to build/shots/r10w2-newtag.txt.
 * PNGs: r10w2-newtag-<shape>-<theme>.
 */
class R10W2NewTagShots {
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

    private fun showPicture(ctx: Context, arena: ArenaView) {
        val bg: Backdrop = BackdropCatalog.byId("forest")!!
        BackdropScenes.write(Sprites.dir(ctx), bg, 0.5f)
        val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false, noGet)!!
        Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
        field<ArenaBackdrop>(arena, "backdrop").request(bg, false)
    }

    private fun markNew() { val st = GameRepo.state!!; st.caught -= st.battle!!.wild.species }

    private class Scene(val root: View, val arena: ArenaView)
    private class Host(val name: String, val size: Size, val build: (Context) -> Scene)

    private fun boxed(ctx: Context, wDp: Int, hDp: Int, setup: (BattlePanelView) -> Unit): Scene {
        val p = BattlePanelView(ctx)
        setup(p)
        markNew()
        p.renderAll()
        showPicture(ctx, p.arena)
        val frame = FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            addView(p, FrameLayout.LayoutParams(Ui.dp(ctx, wDp), Ui.dp(ctx, hDp), Gravity.TOP or Gravity.LEFT))
        }
        return Scene(frame, p.arena)
    }

    private fun bound(ctx: Context): Scene {
        val b = BattleShotKit.bound(ctx)
        markNew()
        b.panel!!.renderAll()
        showPicture(ctx, b.arena!!)
        return Scene(b.root, b.arena!!)
    }

    private val hosts = listOf(
        Host("battle-tab", Size.PHONE) { ctx -> bound(ctx) },
        Host("study-tall", Size.SPLIT) { ctx -> boxed(ctx, 412, 265) { it.setStudyLayout(false, false) } },
        Host("study-short", Size.SPLIT) { ctx -> boxed(ctx, 412, 220) { it.setStudyLayout(false, false) } },
        Host("strip", Size.SPLIT) { ctx -> boxed(ctx, 412, 146) { it.setStudyLayout(true, false) } },
        Host("land-study", Size.LAND) { ctx -> boxed(ctx, 440, 412) { it.setStudyLayout(false, false) } },
        Host("land-battle", Size.LAND) { ctx -> bound(ctx) },
        Host("split", Size.SPLIT) { ctx -> bound(ctx) },
        Host("float", Size.FLOATBATTLE) { ctx ->
            val self = object : android.content.ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }
            val v = FloatBattleView(self, {}, {}, {})
            v.javaClass.getDeclaredMethod("bind").also { it.isAccessible = true }.invoke(v)
            val p: BattlePanelView = field(v, "panel")
            markNew()
            p.renderAll()
            showPicture(ctx, p.arena)
            val window: Any = field(v, "window")
            Scene(field(window, "root"), p.arena)
        },
        Host("pip", Size.PIP_169) { ctx ->
            val p = BattlePanelView(ctx).apply { setCompact(true) }
            markNew()
            p.renderAll()
            showPicture(ctx, p.arena)
            Scene(p, p.arena)
        },
    )

    // ---- what the sticker really is on screen ----

    private fun tagBounds(arena: ArenaView): NewTagRule.Box {
        val t: View = field(arena, "newTag")
        return NewTagRule.turned(t.left + t.translationX, t.top + t.translationY, t.width.toFloat(), t.height.toFloat(), t.scaleX, t.rotation)
    }

    private fun boxOf(arena: ArenaView): NewTagRule.Box {
        val b: View = field(arena, "enemyBlock")
        return NewTagRule.Box(b.left.toFloat(), b.top.toFloat(), b.left + b.width * b.scaleX, b.top + b.height * b.scaleY)
    }

    /** The wild card's HP numbers (or its bar when the number is inside it) in the arena's px. */
    private fun hpRect(arena: ArenaView): NewTagRule.Box {
        val block: View = field(arena, "enemyBlock")
        val text: View = field(arena, "eHpText")
        val bar: View = field(arena, "eHp")
        val v = if (text.parent != null && text.visibility != View.GONE) text else bar
        var x = 0f; var y = 0f
        var p: View = v
        while (p !== block) { x += p.left; y += p.top; p = p.parent as View }
        val k = block.scaleX
        return NewTagRule.Box(block.left + x * k, block.top + y * k, block.left + (x + v.width) * k, block.top + (y + v.height) * k)
    }

    private fun cornerOf(arena: ArenaView): NewTagRule.Corner {
        val t: View = field(arena, "newTag")
        if (t.translationX == 0f && t.translationY == 0f && t.rotation == -NewTagRule.TILT) return NewTagRule.Corner.TOP_LEFT
        val b = tagBounds(arena)
        val box = boxOf(arena)
        val cx = (b.l + b.r) / 2f; val cy = (b.t + b.b) / 2f
        val right = cx > (box.l + box.r) / 2f
        val bottom = cy > (box.t + box.b) / 2f
        return when { !right && !bottom -> NewTagRule.Corner.TOP_LEFT; right && !bottom -> NewTagRule.Corner.TOP_RIGHT; right -> NewTagRule.Corner.BOTTOM_RIGHT; else -> NewTagRule.Corner.BOTTOM_LEFT }
    }

    private val table = ArrayList<String>()

    private fun shoot(host: Host, theme: Theme, style: InfoBoxStyle = InfoBoxStyle.ABOVE, look: InfoBoxLook = InfoBoxLook.TODAY, suffix: String = ""): Scene {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(host.size, theme))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        AppPrefs.setBackgroundPictures(ctx, true)
        AppPrefs.setInfoBoxStyle(ctx, style)
        AppPrefs.setInfoBoxLook(ctx, look)
        val sc = host.build(ctx)
        val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "r10w2-newtag-${host.name}${suffix}-${theme.name.lowercase()}", 1000)
        check(host, theme, style, look, sc.arena)
        return sc
    }

    private fun check(host: Host, theme: Theme, style: InfoBoxStyle, look: InfoBoxLook, arena: ArenaView) {
        val tag = "${host.name} ${theme.name} ${style.name} ${look.name}"
        val t: View = field(arena, "newTag")
        assertEquals("$tag: the sticker is shown", View.VISIBLE, t.visibility)
        assertTrue("$tag: laid out", arena.width > 0 && t.width > 0)
        assertFalse("$tag: the sticker takes no touches", t.isClickable || t.isLongClickable)
        val b = tagBounds(arena)
        val hp = hpRect(arena)
        val fits = b.outside(arena.width.toFloat(), arena.height.toFloat()) < 0.5f && !b.hits(hp)
        val d = arena.resources.displayMetrics.density
        // today's spot as the rule computes it: kept when it fits, else another corner
        val k = (field<View>(arena, "enemyBlock")).scaleX
        val box = boxOf(arena)
        val edge = if (arena.stagePlanNow != null) arena.frameInsetDp * d else 0f
        val todayPivotX = maxOf(edge, box.l - 4f * d * k); val todayPivotY = maxOf(edge, box.t - 3f * d * k)
        val today = NewTagRule.turned(todayPivotX, todayPivotY, t.width.toFloat(), t.height.toFloat(), t.scaleX, -NewTagRule.TILT)
        val todayFits = today.outside(arena.width.toFloat(), arena.height.toFloat()) < 0.5f && !today.hits(hpRect(arena))
        val corner = cornerOf(arena)
        if (todayFits) assertEquals("$tag: today's corner is kept ($b)", NewTagRule.Corner.TOP_LEFT, corner)
        else assertTrue("$tag: today's corner is cut ($today of ${arena.width} x ${arena.height}), the sticker moved (now $corner)", corner != NewTagRule.Corner.TOP_LEFT || !fits)
        assertTrue("$tag: the sticker $b lies inside the ${arena.width} x ${arena.height} arena", b.outside(arena.width.toFloat(), arena.height.toFloat()) < 0.5f)
        assertFalse("$tag: the sticker $b is off the HP numbers $hp", b.hits(hp))
        // it still reads as the box's: near its drawn rectangle
        assertTrue("$tag: the sticker touches the box ($b vs $box)", b.grow(2f * d).hits(box))
        // it scales with the box and keeps the text floor it had before (the text scale of [ArenaView.placeNewTag] is untouched)
        assertTrue("$tag: the sticker follows the box (${t.scaleX} vs $k)", t.scaleX >= k - 0.0001f)
        if (theme == Theme.LIGHT && style == InfoBoxStyle.ABOVE && look == InfoBoxLook.TODAY)
            table += "%-12s %4d x %-4d dp  box %s dp  case: %s  corner %s".format(host.name, (arena.width / d).toInt(), (arena.height / d).toInt(),
                NewTagRule.Box(box.l / d, box.t / d, box.r / d, box.b / d), if (todayFits) "fits -> today's corner" else "would be cut -> moved", corner)
    }

    private fun both(name: String, style: InfoBoxStyle = InfoBoxStyle.ABOVE, look: InfoBoxLook = InfoBoxLook.TODAY, suffix: String = "") {
        val h = hosts.first { it.name == name }
        for (theme in Theme.entries) shoot(h, theme, style, look, suffix)
    }

    @Test fun battleTab() = both("battle-tab")
    @Test fun studyPanelTall() = both("study-tall")
    @Test fun studyPanelShort() = both("study-short")
    @Test fun studyStrip() = both("strip")
    @Test fun landscapeStudy() = both("land-study")
    @Test fun landscapeBattleTab() = both("land-battle")
    @Test fun splitScreen() = both("split")
    @Test fun floatingBattleWindow() = both("float")
    @Test fun pictureInPicture() = both("pip")

    @Test fun numberInsideTheBarKeepsTheStickerOffTheBar() {
        for (n in listOf("battle-tab", "strip", "pip")) both(n, style = InfoBoxStyle.INSIDE, suffix = "-inside")
    }

    @Test fun everyInfoBoxLookKeepsTheRule() {
        for (look in InfoBoxLook.entries) both("battle-tab", look = look, suffix = "-${look.name.lowercase()}")
        for (look in listOf(InfoBoxLook.HUD, InfoBoxLook.GLASS)) both("strip", look = look, suffix = "-${look.name.lowercase()}")
    }

    @Test fun theTableOfTheNineCases() {
        for (h in hosts) shoot(h, Theme.LIGHT)
        File(Shots.outDir, "r10w2-newtag.txt").writeText("NEW sticker, rule: today's corner when it fits, else the first free corner of the wild box\n" + table.joinToString("\n") + "\n")
        assertEquals(hosts.size, table.size)
    }

    // ---- hidden when caught or the card hides; touches pass through; the sticker follows a moved box ----

    @Test fun hiddenWhenCaughtOrTheCardHidesAndNoTouches() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val sc = hosts.first { it.name == "strip" }.build(ctx)
        val arena = sc.arena
        val t: View = field(arena, "newTag")
        val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "r10w2-newtag-hide-check", 1000)
        assertEquals(View.VISIBLE, t.visibility)
        arena.setNewTag(false)
        assertEquals("caught: hidden", View.INVISIBLE, t.visibility)
        arena.setNewTag(true)
        assertEquals(View.VISIBLE, t.visibility)
        arena.setEnemyCardShown(false)
        assertEquals("card hidden: hidden", View.INVISIBLE, t.visibility)
        arena.setEnemyCardShown(true)
        assertEquals(View.VISIBLE, t.visibility)
        // a touch on the sticker is not taken by it: it falls through to what is under it
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        assertFalse("the sticker does not consume touches", t.dispatchTouchEvent(ev))
        ev.recycle()
    }

    @Test fun theStickerFollowsTheBoxWhenThePanelIsResized() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val sc = hosts.first { it.name == "study-tall" }.build(ctx)
        val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "r10w2-newtag-resize-check", 1000)
        val arena = sc.arena
        val d = arena.resources.displayMetrics.density
        for ((wDp, hDp) in listOf(300 to 146, 412 to 265, 412 to 220, 206 to 170)) {
            val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
            arena.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            arena.layout(0, 0, w, h)
            val b = tagBounds(arena)
            assertTrue("$wDp x $hDp: inside ($b)", b.outside(w.toFloat(), h.toFloat()) < 0.5f)
            assertTrue("$wDp x $hDp: on the box", b.grow(2f * d).hits(boxOf(arena)))
            assertFalse("$wDp x $hDp: off the HP numbers", b.hits(hpRect(arena)))
        }
    }

}
