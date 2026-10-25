package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.HttpGet
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.BubbleDismiss
import com.husarp.ankigo.ui.ConditionMarks
import com.husarp.ankigo.ui.EventPlayer
import com.husarp.ankigo.ui.PanelFit
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R19-K (PLAN 269 A, 287): the condition chips on the real arena at every host - the Battle tab, the Study panel, two Study strips,
 * picture-in-picture, the floating window (default and smallest) - with a battle picture and without, 1 / 3 / 8 conditions on each side and
 * the weather on. The checks: no chip is over another chip, a box, a Pokémon's picture, the NEW sticker, the frame's band or corner squares, or
 * outside the arena; every condition is a chip or counted in the "+N"; the text is never under the panel's 8 sp floor. The flow tests: the
 * list a tap opens, the bubbles closing on a tap elsewhere and on Back, the events moving the chips. PNGs: r19k-<host>-<n>-<light|dark>,
 * r19k-list-<host>-<light|dark>, r19k-picture-<host>-<n>-<light|dark>.
 */
class R19KConditionShots {
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

    // ---------------------------------------------------------------- the fight

    private enum class How { VOL, SIDE }

    private val wildPicks = listOf(How.VOL to ("confusion" to 3), How.VOL to ("leechseed" to 1), How.SIDE to ("tailwind" to 3), How.VOL to ("substitute" to 25),
        How.VOL to ("taunt" to 2), How.SIDE to ("safeguard" to 4), How.VOL to ("encore" to 2), How.VOL to ("healblock" to 4))
    private val ourPicks = listOf(How.VOL to ("attract" to 1), How.VOL to ("partiallytrapped" to 3), How.SIDE to ("reflect" to 4), How.VOL to ("stockpile" to 2),
        How.SIDE to ("lightscreen" to 5), How.VOL to ("aquaring" to 1), How.VOL to ("torment" to 1), How.SIDE to ("wish" to 2))

    /** [n] conditions on each side and the weather (n = 3 adds a terrain, n = 8 a room too), in the saved fight. */
    private fun fight(n: Int) {
        val b = GameRepo.state!!.battle!!
        b.enemy.volatiles.clear(); b.player.volatiles.clear(); b.field.enemy.clear(); b.field.player.clear(); b.field.rooms.clear()
        b.field.weather = "rain"; b.field.weatherTurns = 3
        b.field.terrain = if (n >= 3) "electric" else null; b.field.terrainTurns = 4
        if (n >= 8) b.field.rooms["trickroom"] = 3
        for ((how, kv) in wildPicks.take(n)) (if (how == How.VOL) b.enemy.volatiles else b.field.enemy)[kv.first] = kv.second
        for ((how, kv) in ourPicks.take(n)) (if (how == How.VOL) b.player.volatiles else b.field.player)[kv.first] = kv.second
    }

    // ---------------------------------------------------------------- hosts

    private fun device(w: Int, h: Int, theme: Theme): DeviceConfig = Shots.device(Size.PHONE, theme).copy(
        screenWidth = w * 2, screenHeight = h * 2, orientation = if (w > h) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
    )

    private fun install(ctx: Context) {
        FakeGame.install(ctx)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val st = GameRepo.state!!
        st.caught -= st.battle!!.wild.species   // the wild one is new: the NEW sticker is on its box
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

    private class Scene(val root: View, val arena: ArenaView, val panel: BattlePanelView?, val wPx: Int, val hPx: Int)

    private class Host(val name: String, val w: Int, val h: Int, val build: (Context) -> Scene)

    private fun px(ctx: Context) = ctx.resources.configuration.screenWidthDp * 2 to ctx.resources.configuration.screenHeightDp * 2

    private fun battle(ctx: Context): Scene {
        val b = BattleShotKit.bound(ctx)
        return Scene(b.root, b.arena!!, b.panel, px(ctx).first, px(ctx).second)
    }

    private fun boxed(ctx: Context, wDp: Int, hDp: Int, strip: Boolean): Scene {
        val p = BattlePanelView(ctx)
        p.setStudyLayout(strip, false)
        p.renderAll()
        val frame = FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            addView(p, FrameLayout.LayoutParams(Ui.dp(ctx, wDp), Ui.dp(ctx, hDp), Gravity.TOP or Gravity.LEFT))
        }
        return Scene(frame, p.arena, p, px(ctx).first, px(ctx).second)
    }

    private fun pip(ctx: Context): Scene {
        val p = BattlePanelView(ctx).apply { setCompact(true) }
        p.renderAll()
        return Scene(p, p.arena, p, px(ctx).first, px(ctx).second)
    }

    private fun floating(ctx: Context): Scene {
        val self = object : android.content.ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        v.javaClass.getDeclaredMethod("bind").also { it.isAccessible = true }.invoke(v)
        val p: BattlePanelView = field(v, "panel")
        p.renderAll()
        val window: Any = field(v, "window")
        return Scene(field(window, "root"), p.arena, p, px(ctx).first, px(ctx).second)
    }

    private val hosts = listOf(
        Host("battle-412x869", 412, 869, ::battle),
        Host("study-412x400", 412, 400) { boxed(it, 412, 400, strip = false) },
        Host("strip-412x230", 412, 230) { boxed(it, 412, 230, strip = true) },
        Host("strip-412x130", 412, 130) { boxed(it, 412, 130, strip = true) },
        Host("pip-240x160", 240, 160, ::pip),
        Host("float-240x190", 240, 190, ::floating),
        Host("float-170x130", 170, 130, ::floating),
    )

    private fun layoutScene(sc: Scene) { sc.arena.requestLayout(); layoutRoot(sc.root, sc.wPx, sc.hPx) }

    /** Builds [host], puts the fight with [n] conditions on it, with or without a battle picture, and lays it out until it is steady. */
    private fun setup(ctx: Context, host: Host, n: Int, picture: Boolean): Scene {
        install(ctx)
        fight(n)
        val sc = host.build(ctx)
        val arena = sc.arena
        arena.setNewTag(true); arena.setEnemyCardShown(true)
        val bg = BackdropCatalog.byId("forest")!!
        layoutRoot(sc.root, sc.wPx, sc.hPx)
        if (picture) { load(ctx, bg); field<ArenaBackdrop>(arena, "backdrop").request(bg, false) }
        arena.syncConditions(GameRepo.state!!.battle)
        layoutScene(sc); layoutScene(sc)
        return sc
    }

    // ---------------------------------------------------------------- the checks

    private fun hits(a: RectF, b: RectF) = a.left < b.right - 0.01f && b.left < a.right - 0.01f && a.top < b.bottom - 0.01f && b.top < a.bottom - 0.01f

    /** What is wrong with the chips of [sc] (px of the arena), and a one-line summary of what was placed. */
    private fun inspect(sc: Scene, tag: String): Pair<List<String>, String> {
        val a = sc.arena
        val out = ArrayList<String>()
        val scene = a.markScenePublic()
        val rows = a.markRows().toList()
        val sum = StringBuilder()
        for (slot in ConditionMarks.Slot.entries) {
            val want = a.conditions.of(slot).size
            val row = rows.firstOrNull { it.slot == slot }
            if (row == null) { sum.append("${slot.name.take(1)}:none "); continue }
            val shown = row.chips.count { it.mark != null }
            val more = row.chips.firstOrNull { it.mark == null }?.more ?: 0
            sum.append("${slot.name.take(1)}:$shown${if (more > 0) "+$more" else ""} ")
            if (shown + more != want) out += "$tag ${slot.name}: $shown chips + $more is not $want conditions"
            if (more > 0 && row.chips.last().mark != null) out += "$tag ${slot.name}: the +N is not last"
            if (slot != ConditionMarks.Slot.FIELD && row.chips.first().mark != null && row.chips.first().mark!!.def.rank != a.conditions.of(slot).first().def.rank) out += "$tag ${slot.name}: not the most important first"
        }
        val all = rows.flatMap { r -> r.chips.map { it.rect } }
        val area = RectF(scene.area.l, scene.area.t, scene.area.r, scene.area.b)
        for ((i, c) in all.withIndex()) {
            if (c.left < area.left - 0.5f || c.top < area.top - 0.5f || c.right > area.right + 0.5f || c.bottom > area.bottom + 0.5f) out += "$tag: chip $c is outside the room $area"
            if (c.left < 0f || c.top < 0f || c.right > sc.arena.width || c.bottom > sc.arena.height) out += "$tag: chip $c is outside the arena"
            for (o in scene.blocks) if (hits(c, RectF(o.l, o.t, o.r, o.b))) out += "$tag: chip $c is over an obstacle ${o.l},${o.t},${o.r},${o.b}"
            for (j in i + 1 until all.size) if (hits(c, all[j])) out += "$tag: two chips overlap: $c and ${all[j]}"
        }
        if (all.isNotEmpty()) {
            val onScreen = a.chipTextSp() * a.panelScale
            if (onScreen < PanelFit.MIN_SP - 0.05f) out += "$tag: the chip text is $onScreen sp on screen"
        }
        return out to sum.toString().trim()
    }

    private fun tag(host: Host, picture: Boolean, n: Int, extra: String = "") = "${host.name}${if (picture) "-picture" else "-plain"}-$n$extra"

    @Test fun noChipIsEverOverAnythingAtEveryHost() {
        val report = ArrayList<String>()
        val bad = ArrayList<String>()
        for (host in hosts) for (picture in listOf(false, true)) for (n in listOf(1, 3, 8)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = device(host.w, host.h, Theme.LIGHT))
            val ctx = Shots.files(paparazzi.context)
            val sc = setup(ctx, host, n, picture)
            val (problems, summary) = inspect(sc, tag(host, picture, n))
            bad += problems
            report += "${tag(host, picture, n)}: $summary"
        }
        println("R19-K placed rows (W = wild, O = ours, F = field; chips shown +N):\n" + report.joinToString("\n"))
        check(bad.isEmpty()) { bad.joinToString("\n") }
    }

    @Test fun noChipIsEverOverTheBattleFrame() {
        for (host in hosts.filter { it.name.startsWith("battle") || it.name.startsWith("study") }) for (n in listOf(3, 8)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = device(host.w, host.h, Theme.LIGHT))
            val ctx = Shots.files(paparazzi.context)
            val sc = setup(ctx, host, n, picture = false)
            sc.arena.frameInsetDp = 10f; sc.arena.frameTopDp = 16f; sc.arena.frameCornerDp = 26f
            layoutScene(sc)
            val (problems, _) = inspect(sc, tag(host, false, n, "-frame"))
            check(problems.isEmpty()) { problems.joinToString("\n") }
        }
    }

    @Test fun theBattleTabHasRoomForEveryChipOfOneAndThree() {
        for (n in listOf(1, 3)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, Theme.LIGHT))
            val sc = setup(Shots.files(paparazzi.context), hosts[0], n, picture = false)
            val rows = sc.arena.markRows().associateBy { it.slot }
            for (slot in listOf(ConditionMarks.Slot.WILD, ConditionMarks.Slot.OURS)) {
                val row = rows[slot]
                assertNotNull("n=$n $slot: a row", row)
                val shown = row!!.chips.count { it.mark != null }
                val more = row.chips.firstOrNull { it.mark == null }?.more ?: 0
                assertEquals("n=$n $slot: every condition is a chip or counted", n, shown + more)
                if (n == 1 || slot == ConditionMarks.Slot.WILD) assertEquals("n=$n $slot: every chip, no +N", n, shown)
                assertTrue("n=$n $slot: at least one chip", shown >= 1)
            }
        }
    }

    @Test fun eightConditionsShowTheMostImportantAndAPlusNOnTheBattleTab() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, Theme.LIGHT))
        val sc = setup(Shots.files(paparazzi.context), hosts[0], 8, picture = false)
        for (row in sc.arena.markRows().filter { it.slot != ConditionMarks.Slot.FIELD }) {
            val plus = row.chips.last()
            assertNull("${row.slot}: the +N is the last chip", plus.mark)
            assertTrue("${row.slot}: some chips and a +N", plus.more in 1..7 && row.chips.size - 1 + plus.more == 8)
        }
    }

    // ---------------------------------------------------------------- the list a tap opens

    private fun tapCard(sc: Scene, wild: Boolean) = field<View>(sc.arena, if (wild) "enemyBlock" else "playerCard").performClick()

    @Test fun aTapOnABoxOrOnPlusNListsEveryConditionWithItsTurns() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, Theme.LIGHT))
        val sc = setup(Shots.files(paparazzi.context), hosts[0], 8, picture = false)
        val a = sc.arena
        assertTrue("the boxes take the tap while there are conditions", field<View>(a, "enemyBlock").isClickable)
        assertTrue(tapCard(sc, wild = true))
        val list = a.conditionListShown()!!
        layoutScene(sc)
        val text = list.spoken
        for (m in a.conditions.all()) assertTrue("the list names ${m.word} with its turns: ${m.spoken}", text.contains(m.spoken))
        assertTrue(text.contains("3 turns left")); assertTrue(text.contains("25 HP left")); assertTrue(text.contains("Stockpile 2, 2 layers"))
        assertTrue("the list sits inside the arena", list.left >= 0 && list.top >= 0 && list.right <= a.width && list.bottom <= a.height)
        assertTrue(a.bubbleOpen())
    }

    @Test fun aTapOnAChipOpensTheListAndTheBoxesAreNotTappableWithoutConditions() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, Theme.LIGHT))
        val sc = setup(Shots.files(paparazzi.context), hosts[0], 3, picture = false)
        val a = sc.arena
        val row = a.markRows().first { it.slot == ConditionMarks.Slot.WILD }
        val c = row.chips.first().rect
        val t = android.os.SystemClock.uptimeMillis()
        assertTrue("a chip takes the touch", a.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, c.centerX(), c.centerY(), 0)))
        a.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, c.centerX(), c.centerY(), 0))
        assertNotNull("a tap on a chip opens the list", a.conditionListShown())
        a.syncConditions(null)
        assertFalse(field<View>(a, "enemyBlock").isClickable)
        assertNull("no conditions: no list", a.conditionListShown())
        assertEquals(0, a.markRects().size)
    }

    @Test fun aTouchWhereThereIsNoChipGoesToTheViewsUnderIt() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, Theme.LIGHT))
        val sc = setup(Shots.files(paparazzi.context), hosts[0], 3, picture = false)
        val a = sc.arena
        var tapped = 0
        a.onPlayerTap = { tapped++ }
        val holder: View = field(a, "playerHolder")
        val chips = a.markRects()
        // a point of our Pokémon that is no chip: the tap still opens its details
        val x = holder.left + holder.width / 2f; val y = holder.top + holder.height / 2f
        assertTrue("the point is no chip", chips.none { it.contains(x, y) })
        val t = android.os.SystemClock.uptimeMillis()
        a.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x, y, 0))
        holder.performClick()
        assertTrue("our Pokémon's tap still works", tapped > 0)
    }

    @Test fun theListIsAlwaysInsideTheArenaAndOnlyScrollsWhenItHasToInTheSmallestWindows() {
        val scrolled = ArrayList<String>()
        for (host in hosts) for (picture in listOf(false, true)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = device(host.w, host.h, Theme.LIGHT))
            val sc = setup(Shots.files(paparazzi.context), host, 8, picture)
            val a = sc.arena
            tapCard(sc, wild = true)
            val list = a.conditionListShown()
            assertNotNull("${host.name}: a list", list)
            layoutScene(sc)
            val l = list!!
            assertTrue("${host.name} picture=$picture: the list ${l.left},${l.top},${l.right},${l.bottom} is inside the arena ${a.width}x${a.height}",
                l.left >= 0 && l.top >= 0 && l.right <= a.width && l.bottom <= a.height)
            if (l.scrolls) scrolled += host.name + if (picture) "-picture" else ""
            for (m in a.conditions.all()) assertTrue("${host.name}: ${m.spoken} is in the list", l.spoken.contains(m.spoken))
        }
        println("R19-K lists that scroll: $scrolled")
        assertTrue("the Battle tab, the Study panel and the taller strip show the whole list without scrolling", scrolled.none { it.startsWith("battle") || it.startsWith("study") || it.startsWith("strip-412x230") })
    }

    @Test fun aTouchOnTheListDoesNotCloseItButOneOutsideDoes() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, Theme.LIGHT))
        val sc = setup(Shots.files(paparazzi.context), hosts[0], 8, picture = false)
        val a = sc.arena
        tapCard(sc, wild = true)
        val l = a.conditionListShown()!!
        down(a, (l.left + l.right) / 2f, (l.top + l.bottom) / 2f)
        assertNotNull("a touch on the list keeps it (it may scroll)", a.conditionListShown())
        down(a, a.width - 3f, a.height - 3f)
        assertNull("a touch outside closes it", a.conditionListShown())
    }

    // ---------------------------------------------------------------- 287: the bubbles close

    private fun down(a: ArenaView, x: Float, y: Float): Boolean {
        val t = android.os.SystemClock.uptimeMillis()
        return a.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
    }

    private fun openStatusBubble(a: ArenaView) {
        a.setEnemyStatus("slp")
        field<View>(a, "eStatus").performClick()
    }

    @Test fun theStatusBubbleClosesOnATapAnywhereElseInTheArena() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, Theme.LIGHT))
        val sc = setup(Shots.files(paparazzi.context), hosts[0], 3, picture = false)
        val a = sc.arena
        for ((label, x, y) in listOf(Triple("empty ground", a.width - 10f, a.height / 2f), Triple("a card", field<View>(a, "playerCard").let { it.left + 5f }, field<View>(a, "playerCard").let { it.top + 5f }))) {
            ArenaView::class.java.getDeclaredField("touchClosedAt").apply { isAccessible = true }.setLong(a, Long.MIN_VALUE / 2)   // the last closing touch is long past (the renderer's clock is not a real one)
            openStatusBubble(a)
            assertTrue("$label: the bubble is open", a.bubbleOpen())
            down(a, x, y)
            assertFalse("$label: a tap elsewhere closes it", a.bubbleOpen())
        }
    }

    @Test fun theConditionsListClosesOnATapElsewhereAndOnBack() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, Theme.LIGHT))
        val sc = setup(Shots.files(paparazzi.context), hosts[0], 3, picture = false)
        val a = sc.arena
        tapCard(sc, wild = true)
        assertNotNull(a.conditionListShown())
        down(a, a.width - 6f, a.height / 2f)
        assertNull("a tap elsewhere closes the list", a.conditionListShown())
        // Back: the same closing the window's key callback asks for (the proxy itself is device-only); the touch hub is the pure part
        tapCard(sc, wild = false)
        assertNotNull(a.conditionListShown())
        val hub = BubbleDismiss.Hub()
        var closed = false
        hub.add(BubbleDismiss.Armed(onTouch = { _, _ -> closed = true }, onBack = { closed = true }))
        assertTrue(hub.back()); assertTrue(closed)
    }

    @Test fun aTapOnTheSameTriggerAgainClosesInsteadOfOpeningAgain() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, Theme.LIGHT))
        val sc = setup(Shots.files(paparazzi.context), hosts[0], 3, picture = false)
        val a = sc.arena
        tapCard(sc, wild = true)
        assertNotNull(a.conditionListShown())
        // the touch of the second tap closes it first; the click that follows must not open it again
        val row = a.markRows().first { it.slot == ConditionMarks.Slot.WILD }.chips.first().rect
        down(a, row.centerX(), row.centerY())
        assertNull(a.conditionListShown())
        tapCard(sc, wild = true)
        assertNull("the click right after the closing touch does not reopen it", a.conditionListShown())
        // a tap on the other side's box (or later) does open its list
        tapCard(sc, wild = false)
        assertNotNull(a.conditionListShown())
    }

    @Test fun theStatusBubbleOfTheSameBadgeClosesInsteadOfOpeningAgain() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, Theme.LIGHT))
        val sc = setup(Shots.files(paparazzi.context), hosts[0], 1, picture = false)
        val a = sc.arena
        openStatusBubble(a)
        assertTrue(a.bubbleOpen())
        val badge = field<android.view.ViewGroup>(a, "eStatus").let { r -> android.graphics.Rect(0, 0, r.width, r.height).also { a.offsetDescendantRectToMyCoords(r, it) } }
        down(a, badge.centerX().toFloat(), badge.centerY().toFloat())   // the touch of the second tap on the badge
        assertFalse(a.bubbleOpen())
        field<View>(a, "eStatus").performClick()                          // and its click
        assertFalse("the same badge: closed, not opened again", a.bubbleOpen())
    }

    @Test fun enlargedStatusTargetInsideClickableBoxOpensStatusInsteadOfConditions() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, Theme.LIGHT))
        val sc = setup(Shots.files(paparazzi.context), hosts[0], 1, picture = false)
        val a = sc.arena
        openStatusBubble(a)
        field<View>(a, "eStatus").performClick() // close it before the tested gesture
        a.measure(View.MeasureSpec.makeMeasureSpec(a.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(a.height, View.MeasureSpec.EXACTLY))
        a.layout(a.left, a.top, a.right, a.bottom)
        val target = a.statusTouchArea(true)!!
        val box = field<View>(a, "enemyBlock")
        val bounds = android.graphics.Rect(0, 0, box.width, box.height)
        a.offsetDescendantRectToMyCoords(box, bounds)
        assertTrue(bounds.intersect(target))
        val row = field<View>(a, "eStatus")
        val drawn = android.graphics.Rect(0, 0, row.width, row.height).also { a.offsetDescendantRectToMyCoords(row, it) }
        val x = bounds.centerX().toFloat()
        val y = (bounds.top until bounds.bottom).first { !drawn.contains(x.toInt(), it) }.toFloat()
        assertFalse("the tested point is outside the drawn status row", drawn.contains(x.toInt(), y.toInt()))
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        a.dispatchTouchEvent(down); down.recycle()
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, x, y, 0)
        a.dispatchTouchEvent(up); up.recycle()
        assertTrue(a.bubbleOpen())
        assertNull(a.conditionListShown())
    }

    @Test fun pressedChipFeedbackShots() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, theme))
            val sc = setup(Shots.files(paparazzi.context), hosts[0], 1, picture = false)
            val r = sc.arena.markRects().first()
            down(sc.arena, r.centerX(), r.centerY())
            paparazzi.snapshot(sc.root, "r19fix2-condition-pressed-${theme.name.lowercase()}")
        }
    }

    // ---------------------------------------------------------------- the events

    private fun play(player: EventPlayer, e: GameEvent) {
        val m = EventPlayer::class.java.getDeclaredMethod("play", GameEvent::class.java).also { it.isAccessible = true }
        m.invoke(player, e)
    }

    @Test fun aConditionAppearsAndGoesInStepWithItsLine() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 869, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        install(ctx)
        val b = GameRepo.state!!.battle!!
        b.enemy.volatiles.clear(); b.player.volatiles.clear(); b.field.enemy.clear(); b.field.player.clear()
        val sc = battle(ctx)
        sc.arena.setEnemyShown(true); sc.arena.setEnemyCardShown(true)
        layoutScene(sc)
        val a = sc.arena
        val player = EventPlayer(sc.panel!!)
        assertEquals(0, a.conditions.size)
        b.enemy.volatiles["confusion"] = 3      // the saved fight is final; its line has not played yet
        assertEquals("not ahead of the animation", 0, a.conditions.size)
        play(player, GameEvent.Effect(false, "Duckling became confused!", 20, 20, null, EffectKind.VOLATILE, "confusion", 1, 0, "Confused"))
        assertEquals("Confused · 3", a.conditions.of(ConditionMarks.Slot.WILD).single().chipText)
        layoutScene(sc)
        assertEquals(1, a.markRows().size)
        b.enemy.volatiles.remove("confusion")
        play(player, GameEvent.Effect(false, "Duckling snapped out of its confusion!", 20, 20, null, EffectKind.VOLATILE, "confusion", -1, 0))
        assertEquals(0, a.conditions.size)
        // the fight ends: nothing stays
        a.conditions.start(ConditionMarks.Slot.FIELD, "rain", 3); a.conditions.start(ConditionMarks.Slot.OURS, "reflect", 3)
        play(player, GameEvent.Fainted(false, "Duckling"))
        assertEquals("no chips after the fight", 0, a.conditions.size)
        assertEquals(0, a.markRects().size)
    }

    @Test fun thePanelSettlesTheChipsFromTheSavedFightWhenItIsDrawn() {
        paparazzi.unsafeUpdateConfig(deviceConfig = device(412, 400, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        install(ctx)
        fight(3)
        val p = BattlePanelView(ctx)
        p.renderAll()
        assertEquals(3 + 3 + 2, p.arena.conditions.size)
        GameRepo.state!!.battle!!.enemy.volatiles.clear()
        p.renderAll()
        assertEquals(0, p.arena.conditions.of(ConditionMarks.Slot.WILD).count { it.def.kind == ConditionMarks.Kind.VOLATILE })
    }

    // ---------------------------------------------------------------- screenshots

    private fun shoot(host: Host, n: Int, picture: Boolean, name: String, after: (Scene) -> Unit = {}) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = device(host.w, host.h, theme))
            val ctx = Shots.files(paparazzi.context)
            val sc = setup(ctx, host, n, picture)
            after(sc)
            layoutScene(sc)
            paparazzi.snapshot(FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }, "$name-${theme.name.lowercase()}", 1000)
            // and what the renderer's own layout left: the same checks on the arena as it was drawn
            val (problems, summary) = inspect(sc, "$name-${theme.name.lowercase()}")
            val scn = sc.arena.markScenePublic()
            println("R19-K shot $name-${theme.name.lowercase()}: arena ${sc.arena.width}x${sc.arena.height} px, $summary; room ${scn.area.l},${scn.area.t},${scn.area.r},${scn.area.b}; chip sp ${sc.arena.chipTextSp()} x${sc.arena.panelScale}; blocks " +
                scn.blocks.joinToString(" ") { "[${it.l.toInt()},${it.t.toInt()},${it.r.toInt()},${it.b.toInt()}]" })
            check(problems.isEmpty()) { problems.joinToString("\n") }
        }
    }

    @Test fun shotsBattleTab() { for (n in listOf(1, 3, 8)) shoot(hosts[0], n, false, "r19k-battle-$n") }
    @Test fun shotsStudyPanel() { for (n in listOf(1, 3, 8)) shoot(hosts[1], n, false, "r19k-study-$n") }
    @Test fun shotsStripTall() { for (n in listOf(1, 3, 8)) shoot(hosts[2], n, false, "r19k-strip230-$n") }
    @Test fun shotsStripShort() { for (n in listOf(1, 3, 8)) shoot(hosts[3], n, false, "r19k-strip130-$n") }
    @Test fun shotsPip() { for (n in listOf(1, 3, 8)) shoot(hosts[4], n, false, "r19k-pip-$n") }
    @Test fun shotsFloatDefault() { for (n in listOf(1, 3, 8)) shoot(hosts[5], n, false, "r19k-float240-$n") }
    @Test fun shotsFloatSmallest() { for (n in listOf(1, 3, 8)) shoot(hosts[6], n, false, "r19k-float170-$n") }
    @Test fun shotsPictureBattleStudyPip() {
        shoot(hosts[0], 8, true, "r19k-picture-battle-8")
        shoot(hosts[1], 3, true, "r19k-picture-study-3")
        shoot(hosts[4], 3, true, "r19k-picture-pip-3")
    }

    @Test fun shotsTheList() {
        shoot(hosts[0], 8, false, "r19k-list-battle") { tapCard(it, wild = true) }
        shoot(hosts[2], 8, false, "r19k-list-strip230") { tapCard(it, wild = false) }
        shoot(hosts[1], 8, false, "r19k-list-study") { tapCard(it, wild = false) }
        shoot(hosts[5], 8, false, "r19k-list-float240") { tapCard(it, wild = true) }   // the smallest windows scroll the list
    }
}
