package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.HttpGet
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BarView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.InfoBoxStyle
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.StatusRow
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * Round 9 wave 3 D12: the two info box designs ("number above the bar" and "number inside the bar") on real views, light and dark, with
 * 3-digit HP (wild 245 / 312, ours 198 / 301), a status on the wild one, ours with its XP line (6400 / 10000), and a long name. Hosts: the
 * Study panel dragged tall (arena 412 x 265), the Study strip (arena 412 x 146) and the Battle tab, each with a battle picture
 * (the automatic box rule) and without (the plain layouts). Every shot checks on the views: the HP bar runs the whole inner width of its
 * box and does not move with the digits or a status, nothing is cut off (only a long name may end in "..." and then the badge and "Lv"
 * stay), the badge sits in the name line and a tap on it opens the bubble anchored to it, the boxes are the short ones, and a box on a
 * picture covers no Pokemon (the plan is clear). PNGs: r9w3-<host>-<picture|plain>-<style>-<theme>.
 */
class R9W3BoxesShots {
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

    private fun picture(ctx: Context, arena: ArenaView) {
        val bg = BackdropCatalog.byId("forest")!!
        BackdropScenes.write(Sprites.dir(ctx), bg, 0.5f)
        val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false, noGet)!!
        Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
        field<ArenaBackdrop>(arena, "backdrop").request(bg, false)
    }

    /** The scenario: 3-digit HP on both sides, a burn on the wild one, our XP line; [long] = long names and two long type names. */
    private fun fill(arena: ArenaView, long: Boolean) {
        arena.setEnemyInfo(if (long) "Thundercrest Marshwalker" else "Duckling", 54, false, if (long) listOf(Type.ELECTRIC, Type.FIGHTING) else listOf(Type.WATER))
        arena.setEnemyHp(245, 312, false)
        arena.setEnemyStatus("brn")
        arena.setEnemyShown(true)
        arena.setPlayerInfo("u", if (long) "Stormcrest Longwhisker the Third" else "Sparkmouse", 56)
        arena.setPlayerHp(198, 301, false)
        arena.setPlayerXp(6400, 10000, false)
    }

    private class Scene(val root: View, val arena: ArenaView)

    private fun boxed(ctx: Context, wDp: Int, hDp: Int, pic: Boolean, setup: (BattlePanelView) -> Unit): Scene {
        val p = BattlePanelView(ctx)
        setup(p)
        p.renderAll()
        if (pic) picture(ctx, p.arena)
        val frame = FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            addView(p, FrameLayout.LayoutParams(Ui.dp(ctx, wDp), Ui.dp(ctx, hDp), Gravity.TOP or Gravity.LEFT))
        }
        return Scene(frame, p.arena)
    }

    private val hosts = listOf<Triple<String, Size, (Context, Boolean) -> Scene>>(
        Triple("study-tall", Size.SPLIT) { c, pic -> boxed(c, 412, 400, pic) { it.setStudyLayout(false, false) } },
        Triple("strip", Size.SPLIT) { c, pic -> boxed(c, 412, 180, pic) { it.setStudyLayout(true, false) } },
        Triple("battle", Size.PHONE) { c, pic ->
            val b = BattleShotKit.bound(c)
            if (pic) picture(c, b.arena!!)
            Scene(b.root, b.arena!!)
        },
    )

    // ---- checks on the views ----------------------------------------------------------------------------------------------------------

    private fun rectIn(card: ViewGroup, v: View): Rect = Rect(0, 0, v.width, v.height).also { card.offsetDescendantRectToMyCoords(v, it) }

    private fun visible(v: View): Boolean { var p: View? = v; while (p != null) { if (p.visibility != View.VISIBLE) return false; p = p.parent as? View }; return true }

    private fun textViews(v: View, out: MutableList<TextView>) {
        if (v.visibility != View.VISIBLE) return
        if (v is TextView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) textViews(v.getChildAt(i), out)
    }

    /** One card of the scenario: [style]'s promises. */
    private fun checkCard(tag: String, arena: ArenaView, wild: Boolean, style: InfoBoxStyle, long: Boolean) {
        val d = arena.resources.displayMetrics.density
        val card: ViewGroup = field(arena, if (wild) "enemyCard" else "playerCard")
        val name: TextView = field(arena, if (wild) "eName" else "pName")
        val level: TextView = field(arena, if (wild) "eLevel" else "pLevel")
        val bar: BarView = field(arena, if (wild) "eHp" else "pHp")
        val status: ViewGroup = field(arena, if (wild) "eStatus" else "pStatus")
        val hpText: TextView = field(arena, if (wild) "eHpText" else "pHpText")
        val who = if (wild) "wild" else "ours"
        assertTrue("$tag: $who card has a size", card.width > 0 && card.height > 0)
        val innerLeft = card.paddingLeft; val innerRight = card.width - card.paddingRight

        // the HP bar runs the whole inner width of the box (the number never takes width from it)
        assertEquals("$tag: $who HP bar full width", innerRight - innerLeft, bar.width)
        assertEquals("$tag: $who HP bar left", innerLeft, rectIn(card, bar).left)
        // the number: above the bar (its own line) or inside it
        val want = if (wild) "245 / 312" else "198 / 301"
        if (style == InfoBoxStyle.INSIDE) {
            assertEquals("$tag: $who number inside the bar", want, bar.labelText())
            assertTrue("$tag: $who has no separate number", hpText.parent == null || !visible(hpText))
            assertTrue("$tag: $who bar 14 dp thick or more (${bar.height / d})", bar.height / d >= 14f - 0.5f)
        } else {
            assertEquals("$tag: $who no label in the thin bar", null, bar.labelText())
            assertEquals("$tag: $who number above the bar", want, hpText.text.toString())
            assertTrue("$tag: $who number line is above the bar", rectIn(card, hpText).bottom <= rectIn(card, bar).top)
            assertTrue("$tag: $who bar 7 dp thick (${bar.height / d})", Math.abs(bar.height / d - 7f) < 0.6f)
        }

        // the badge is in the name line between the name and Lv; the status row is gone from under the bar
        assertEquals("$tag: $who badge shown", if (wild) View.VISIBLE else View.GONE, status.visibility)
        if (wild) {
            val nr = rectIn(card, name); val sr = rectIn(card, status); val lr = rectIn(card, level)
            assertTrue("$tag: badge between name and Lv ($nr $sr $lr)", nr.right <= sr.left + 1 && sr.right <= lr.left + 1)
            assertTrue("$tag: badge in the name line", sr.centerY() < rectIn(card, bar).top && Math.abs(sr.centerY() - nr.centerY()) <= 2 * d)
            assertTrue("$tag: the badge is a real square (${status.getChildAt(0).width})", status.getChildAt(0).width > 8 * d - 1)
        }
        assertTrue("$tag: $who Lv inside the box", visible(level) && rectIn(card, level).right <= innerRight + 1)
        assertTrue("$tag: $who name has room", name.width > 20 * d)
        if (long) {
            val ell = (name.layout?.getEllipsisCount(0) ?: 0) > 0 || name.textScaleX < 0.999f
            assertTrue("$tag: $who long name is narrowed or ends in ...", ell)
        }

        // nothing is cut off or outside the box; only the name may end in "..."
        val texts = ArrayList<TextView>(); textViews(card, texts)
        for (t in texts) {
            if (t.text.isNullOrEmpty()) continue
            val r = rectIn(card, t)
            assertTrue("$tag: $who '${t.text}' inside the box ($r of ${card.width} x ${card.height})", r.left >= 0 && r.top >= 0 && r.right <= card.width + 1 && r.bottom <= card.height + 1)
            if (t !== name) assertEquals("$tag: $who '${t.text}' is not cut off", 0, t.layout?.let { l -> (0 until l.lineCount).sumOf { l.getEllipsisCount(it) } } ?: 0)
        }
    }

    private fun checkScene(tag: String, sc: Scene, style: InfoBoxStyle, long: Boolean, pic: Boolean) {
        val arena = sc.arena
        val d = arena.resources.displayMetrics.density
        assertTrue("$tag: arena size", arena.width > 0)
        for (wild in listOf(true, false)) checkCard(tag, arena, wild, style, long)
        val wildCard: View = field(arena, "enemyBlock"); val playerCard: View = field(arena, "playerCard")
        // the short boxes
        val tops = if (style == InfoBoxStyle.ABOVE) 62f to 78f else 54f to 68f
        assertTrue("$tag: wild box ${wildCard.height / d} dp", wildCard.height / d <= tops.first)
        assertTrue("$tag: our box ${playerCard.height / d} dp", playerCard.height / d <= tops.second)
        if (pic) assertTrue("$tag: the boxes cover no Pokemon (the plan is clear)", arena.stagePlanNow!!.clear)
        // the status badge: a tap opens the bubble, anchored on it, inside the arena (a snapshot closes it, so it is read straight after the tap)
        val row: View = field(arena, "eStatus"); val bubble: View = field(arena, "bubble")
        row.performClick()
        assertEquals("$tag: bubble after a tap on the badge", View.VISIBLE, bubble.visibility)
        val lp = bubble.layoutParams as FrameLayout.LayoutParams
        val badge = Rect(0, 0, (row as ViewGroup).getChildAt(0).width, row.getChildAt(0).height).also { arena.offsetDescendantRectToMyCoords(row.getChildAt(0), it) }
        assertTrue("$tag: bubble in the arena", lp.leftMargin >= 0 && lp.topMargin >= 0 && lp.leftMargin + lp.width <= arena.width && lp.topMargin + lp.height <= arena.height)
        assertTrue("$tag: bubble over the badge (${badge.centerX()} in ${lp.leftMargin}..${lp.leftMargin + lp.width})", badge.centerX() >= lp.leftMargin && badge.centerX() <= lp.leftMargin + lp.width)
        row.performClick()
        assertEquals("$tag: second tap closes it", View.GONE, bubble.visibility)
    }

    private fun run(host: String, size: Size, build: (Context, Boolean) -> Scene, pic: Boolean, long: Boolean) {
        for (style in InfoBoxStyle.entries) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            AppPrefs.setInfoBoxStyle(ctx, style)
            val sc = build(ctx, pic)
            fill(sc.arena, long)
            val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
            val name = "r9w3-$host-${if (pic) "picture" else "plain"}${if (long) "-long" else ""}-${style.name.lowercase()}-${theme.name.lowercase()}"
            paparazzi.snapshot(frame, name, 1000)
            checkScene(name, sc, style, long, pic)
        }
    }

    @Test fun studyTallPicture() { val h = hosts[0]; run(h.first, h.second, h.third, true, false) }
    @Test fun studyTallPlain() { val h = hosts[0]; run(h.first, h.second, h.third, false, false) }
    @Test fun studyTallLongNames() { val h = hosts[0]; run(h.first, h.second, h.third, true, true) }
    @Test fun stripPicture() { val h = hosts[1]; run(h.first, h.second, h.third, true, false) }
    @Test fun stripPlain() { val h = hosts[1]; run(h.first, h.second, h.third, false, false) }
    @Test fun stripLongNames() { val h = hosts[1]; run(h.first, h.second, h.third, true, true) }
    @Test fun battleTabPicture() { val h = hosts[2]; run(h.first, h.second, h.third, true, false) }
    @Test fun battleTabPlain() { val h = hosts[2]; run(h.first, h.second, h.third, false, false) }
    @Test fun battleTabLongNamesPlain() { val h = hosts[2]; run(h.first, h.second, h.third, false, true) }

    // ---- the bar and the card do not move with the digits or a status -----------------------------------------------------------------

    @Test fun threeDigitHpAndAStatusNeverSqueezeTheBarOrMoveTheCard() {
        for (style in InfoBoxStyle.entries) for ((hostName, size, build) in hosts) for (pic in listOf(true, false)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            AppPrefs.setInfoBoxStyle(ctx, style)
            val sc = build(ctx, pic)
            val a = sc.arena
            val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
            a.setEnemyInfo("Duckling", 54, false, listOf(Type.WATER)); a.setEnemyShown(true)
            a.setPlayerInfo("u", "Sparkmouse", 56); a.setPlayerXp(6400, 10000, false)
            fun sizes(): List<Int> {
                val cards = listOf<ViewGroup>(field(a, "enemyCard"), field(a, "playerCard"))
                return cards.flatMap { listOf(it.width, it.height) } + listOf<BarView>(field(a, "eHp"), field(a, "pHp")).map { it.width }
            }
            a.setEnemyHp(5, 9, false); a.setPlayerHp(7, 9, false); a.setEnemyStatus(null)
            paparazzi.snapshot(frame, "r9w3-digits", 0)
            val few = sizes()
            a.setEnemyHp(245, 312, false); a.setPlayerHp(198, 301, false)
            paparazzi.snapshot(frame, "r9w3-digits", 0)
            val three = sizes()
            a.setEnemyStatus("tox"); a.setPlayerStatus("par")
            paparazzi.snapshot(frame, "r9w3-digits", 0)
            val status = sizes()
            val tag = "$hostName ${if (pic) "picture" else "plain"} ${style.name}"
            assertEquals("$tag: 3-digit HP changes no size", few, three)
            assertEquals("$tag: a status changes no size", three, status)
        }
    }

    // ---- a bigger system font: the boxes grow with the text (the model is made at the normal size) ------------------------------------------

    @Test fun aBiggerSystemFontKeepsEveryBoxWholeAndOffThePokemon() {
        for (style in InfoBoxStyle.entries) for ((hostName, size, build) in hosts) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT).copy(fontScale = 1.3f))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            AppPrefs.setInfoBoxStyle(ctx, style)
            val sc = build(ctx, true)
            fill(sc.arena, false)
            val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "r9w3-bigfont-$hostName-${style.name.lowercase()}", 1000)
            val d = ctx.resources.displayMetrics.density
            val name: TextView = field(sc.arena, "eName")
            val tag = "$hostName ${style.name}"
            assertTrue("$tag: the font is bigger (${name.textSize / d} sp)", name.textSize / d >= 14f * 1.2f)
            for (card in listOf<ViewGroup>(field(sc.arena, "enemyCard"), field(sc.arena, "playerCard"))) {
                val given = card.height
                card.measure(View.MeasureSpec.makeMeasureSpec(card.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                assertTrue("$tag: the box shows all of its content (content ${card.measuredHeight}, given $given)", card.measuredHeight <= given + 2 * d)
            }
            assertTrue("$tag: nothing covers a Pokemon", sc.arena.stagePlanNow!!.clear)
        }
    }

    // ---- the number inside the bar at a big system font (FIX Codex 1): the bar is as tall as its number, the number is drawn -------------

    @Test fun aBigFontKeepsTheNumbersInsideTheBarsWhole() {
        for (scale in listOf(1.3f, 2.0f)) for ((hostName, size, build) in hosts) for (pic in listOf(true, false)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT).copy(fontScale = scale))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            AppPrefs.setInfoBoxStyle(ctx, InfoBoxStyle.INSIDE)
            val sc = build(ctx, pic)
            fill(sc.arena, false)
            val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
            val tag = "$hostName ${if (pic) "picture" else "plain"} x$scale"
            paparazzi.snapshot(frame, "r9w3-inside-font${(scale * 10).toInt()}-$hostName-${if (pic) "picture" else "plain"}", 1000)
            val d = ctx.resources.displayMetrics.density
            val bars = listOf<Triple<String, BarView, String>>(
                Triple("wild HP", field(sc.arena, "eHp"), "245 / 312"),
                Triple("our HP", field(sc.arena, "pHp"), "198 / 301"),
                Triple("our XP", field(sc.arena, "pXp"), "XP 6400 / 10000"))
            // a reduced box (picture mode, small room) drops the XP number by design: its thin bar stays
            val xpDropped = pic && sc.arena.stagePlanNow!!.reduced
            for ((who, bar, want) in bars) {
                if (who == "our XP" && xpDropped) { assertEquals("$tag: reduced box has no XP number", null, bar.labelText()); continue }
                assertEquals("$tag: $who number is set", want, bar.labelText())
                val need = bar.labelNeedPx(bar.labelSp())
                assertTrue("$tag: $who bar ${bar.height} px is as tall as its number (${need} px)", bar.height >= need)
                assertTrue("$tag: $who number is drawn (bar ${bar.width} x ${bar.height} px)", bar.labelShown())
            }
            for (card in listOf<ViewGroup>(field(sc.arena, "enemyCard"), field(sc.arena, "playerCard"))) {
                val given = card.height
                val mh0 = card.measuredHeight
                card.measure(View.MeasureSpec.makeMeasureSpec(card.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val kids = "mh0=$mh0 top=${card.top} bottom=${card.bottom} pad=${card.paddingTop}/${card.paddingBottom} " + (0 until card.childCount).map { card.getChildAt(it) }.joinToString { "${(it.layoutParams as ViewGroup.MarginLayoutParams).topMargin}+${it.height}:${it.visibility}" } + " name=" + (field<TextView>(sc.arena, "pName").textSize / d)
                assertTrue("$tag: the box shows all of its content (content ${card.measuredHeight}, given $given, card ${card.javaClass.simpleName} w ${card.width} parent ${(card.parent as View).height}; $kids; arena ${sc.arena.width}x${sc.arena.height})", card.measuredHeight <= given + 2 * d)
            }
            if (pic) assertTrue("$tag: no box pixel on a Pokemon", sc.arena.stagePlanNow!!.clear)
        }
    }

    // ---- the status badge's touch area (FIX Codex 3): at least 48 x 48 dp on screen, a tap in it reaches the badge --------------------

    @Test fun theStatusBadgeHasA48DpTouchAreaAndATapInItOpensTheBubble() {
        for (style in InfoBoxStyle.entries) for ((hostName, size, build) in hosts) for (pic in listOf(true, false)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            AppPrefs.setInfoBoxStyle(ctx, style)
            val sc = build(ctx, pic)
            val a = sc.arena
            val d = ctx.resources.displayMetrics.density
            val frame = FrameLayout(ctx).apply { addView(sc.root, FrameLayout.LayoutParams(-1, -1)) }
            fill(a, false)
            a.setPlayerStatus("par")
            val tag = "$hostName ${if (pic) "picture" else "plain"} ${style.name}"
            paparazzi.snapshot(frame, "r9w3-status-touch", 0)
            // a real touch (down, up) at a point of the arena; true = the badge's row got it (pressed on the down, consumed by the arena both times).
            // The click itself is posted to the looper, which a Paparazzi test does not run: that a click opens the bubble is checked above.
            fun tap(row: View, x: Float, y: Float): Boolean {
                val t = android.os.SystemClock.uptimeMillis()
                val dn = a.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
                val pressed = row.isPressed
                val up = a.dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x, y, 0))
                return dn && pressed && up
            }
            for (wild in listOf(true, false)) {
                val who = if (wild) "wild" else "ours"
                val row: ViewGroup = field(a, if (wild) "eStatus" else "pStatus")
                val area = a.statusTouchArea(wild)
                assertNotNull("$tag: $who badge has a touch area", area)
                area!!
                assertTrue("$tag: $who area ${area.width() / d} x ${area.height() / d} dp is 48 dp both ways", area.width() >= 48 * d - 1 && area.height() >= 48 * d - 1)
                assertTrue("$tag: $who area inside the arena", area.left >= 0 && area.top >= 0 && area.right <= a.width && area.bottom <= a.height)
                val badge = row.getChildAt(0)
                val br = Rect(0, 0, badge.width, badge.height).also { a.offsetDescendantRectToMyCoords(badge, it) }
                assertTrue("$tag: $who area holds the badge ($area vs $br)", area.contains(br))
                // a tap in the area but off the badge: the corner of the area farthest from the badge
                val fx = if (br.centerX() - area.left > area.right - br.centerX()) area.left + 2f else area.right - 3f
                val fy = if (br.centerY() - area.top > area.bottom - br.centerY()) area.top + 2f else area.bottom - 3f
                assertTrue("$tag: $who tap in the area ($fx, $fy) reaches the badge", tap(row, fx, fy))
                assertTrue("$tag: $who tap on the badge itself reaches it", tap(row, br.centerX().toFloat(), br.centerY().toFloat()))
            }
            // no status: no area
            a.setEnemyStatus(null); a.setPlayerStatus(null)
            paparazzi.snapshot(frame, "r9w3-status-touch", 0)
            assertEquals("$tag: no wild area without a status", null, a.statusTouchArea(true))
            assertEquals("$tag: no area of ours without a status", null, a.statusTouchArea(false))
        }
    }

    // ---- the setting: stored by name, an unknown or missing name is the above style, every panel follows ---------------------------------

    @Test fun theSettingIsStoredByNameAndAnUnknownOneMeansTheAboveStyle() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        assertEquals("never chosen", InfoBoxStyle.ABOVE, AppPrefs.infoBoxStyle(c))
        AppPrefs.setInfoBoxStyle(c, InfoBoxStyle.INSIDE)
        assertEquals(InfoBoxStyle.INSIDE, AppPrefs.infoBoxStyle(c))
        assertEquals("INSIDE", c.applicationContext.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE).getString("infoBoxStyle", null))
        c.applicationContext.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE).edit().putString("infoBoxStyle", "UNDER").commit()
        assertEquals("an unknown name (another version)", InfoBoxStyle.ABOVE, AppPrefs.infoBoxStyle(c))
        AppPrefs.setInfoBoxStyle(c, InfoBoxStyle.ABOVE)
        assertEquals(InfoBoxStyle.ABOVE, AppPrefs.infoBoxStyle(c))
    }

    @Test fun anOpenPanelFollowsTheSettingWhenItRefreshesAndTheBoxesChangeShape() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        val d = c.resources.displayMetrics.density
        val p = BattlePanelView(c)
        p.renderAll()
        fun measure() {
            p.measure(View.MeasureSpec.makeMeasureSpec((412 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((400 * d).toInt(), View.MeasureSpec.EXACTLY))
            p.layout(0, 0, p.measuredWidth, p.measuredHeight)
        }
        measure()
        val bar: BarView = field(p.arena, "pHp")
        val above = (field<ViewGroup>(p.arena, "playerCard")).height
        assertEquals(InfoBoxStyle.ABOVE, p.arena.infoBoxStyle)
        assertEquals("no label in the above style", null, bar.labelText())
        AppPrefs.setInfoBoxStyle(c, InfoBoxStyle.INSIDE)
        p.refreshPrefs()
        measure()
        assertEquals(InfoBoxStyle.INSIDE, p.arena.infoBoxStyle)
        assertNotNull("the number is in the bar now", bar.labelText())
        val inside = (field<ViewGroup>(p.arena, "playerCard")).height
        assertTrue("the inside box is shorter ($inside < $above px)", inside < above)
        AppPrefs.setInfoBoxStyle(c, InfoBoxStyle.ABOVE)
        p.refreshPrefs()
        measure()
        assertEquals(InfoBoxStyle.ABOVE, p.arena.infoBoxStyle)
        assertEquals(above, (field<ViewGroup>(p.arena, "playerCard")).height)
        assertEquals(null, bar.labelText())
    }

    @Test fun theSettingsCardListsBothStylesAndATapPicksOne() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val page = SettingsPage(ShotCtx(ctx), NoHost)
            val m = SettingsPage::class.java.getDeclaredMethod("infoBoxCard").also { it.isAccessible = true }
            val card = m.invoke(page) as ViewGroup
            val holder = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), 0)
                addView(com.husarp.ankigo.ui.Form.sect(ctx, "Info box style"))
                addView(card, LinearLayout.LayoutParams(-1, -2))
            }
            paparazzi.snapshot(holder, "r9w3-settings-${theme.name.lowercase()}", 0)
            val texts = ArrayList<TextView>(); textViews(card, texts)
            val labels = texts.map { it.text.toString() }
            for (s in InfoBoxStyle.entries) assertTrue("${s.label} is listed: $labels", s.label in labels)
            assertEquals("Number above the bar", InfoBoxStyle.ABOVE.label)
            assertEquals(InfoBoxStyle.ABOVE, AppPrefs.infoBoxStyle(ctx))
            // the second row = "Number inside the bar"
            val rows = (0 until card.childCount).map { card.getChildAt(it) }.filter { it.hasOnClickListeners() }
            assertEquals(2, rows.size)
            rows[1].performClick()
            assertEquals(InfoBoxStyle.INSIDE, AppPrefs.infoBoxStyle(ctx))
        }
    }
}
