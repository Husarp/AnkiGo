package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.StatusBadge
import com.husarp.ankigo.ui.StatusInfo
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R2-V12 (item 95): the round status badges in their own row under the HP bar.
 * PNGs: r2v12-gallery (the five badges big and at card size), r2v12-none (no status: the reserved row is empty),
 * r2v12-psnbrn|parslp|toxfrz (full panel, PHONE + SPLIT), r2v12-cc1 (compact cards), r2v12-small-* (picture-in-picture and
 * floating battle), r2v12-bubble-* (the popup after a tap). [geometry] checks on the real views that the row never
 * overlaps a sprite or the other card, the bubble stays in the arena and nothing moves when a status comes.
 */
class R2V12Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val full = listOf(Size.PHONE, Size.SPLIT)
    private val small = listOf(Size.PIP_169M, Size.PIP, Size.FLOATB, Size.FLOATBATTLE)

    private fun set(enemy: String?, player: String?) {
        val b = GameRepo.state!!.battle!!
        b.enemy.status = enemy
        b.player.status = player
    }

    private fun bare(ctx: Context, enemy: String?, player: String?): BattlePanelView {
        val p = BattlePanelView(ctx)
        p.setCompact(true)
        set(enemy, player)
        p.renderAll()
        return p
    }

    // ---------------------------------------------------------------- gallery

    @Test fun gallery() = paparazzi.shoot("r2v12-gallery", listOf(Size.SPLIT)) { ctx ->
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
        }
        for (sizeDp in listOf(72, 14)) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            for (s in listOf("psn", "brn", "par", "slp", "frz")) {
                row.addView(StatusBadge(ctx, s), LinearLayout.LayoutParams(Ui.dp(ctx, sizeDp), Ui.dp(ctx, sizeDp)).also { it.marginEnd = Ui.dp(ctx, 10) })
            }
            col.addView(row, LinearLayout.LayoutParams(-2, -2).also { it.bottomMargin = Ui.dp(ctx, 12) })
        }
        // the 12 dp ones of the compact cards
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for (s in listOf("psn", "brn", "par", "slp", "frz")) row.addView(StatusBadge(ctx, s), LinearLayout.LayoutParams(Ui.dp(ctx, 10), Ui.dp(ctx, 10)).also { it.marginEnd = Ui.dp(ctx, 10) })
        col.addView(row)
        col
    }

    // ---------------------------------------------------------------- full panel

    @Test fun none() = paparazzi.shootWith("r2v12-none", full) { ctx -> val b = BattleShotKit.bound(ctx); set(null, null); b.panel!!.renderAll(); b.root }
    @Test fun psnBrn() = paparazzi.shootWith("r2v12-psnbrn", full) { ctx -> val b = BattleShotKit.bound(ctx); set("psn", "brn"); b.panel!!.renderAll(); b.root }
    @Test fun parSlp() = paparazzi.shootWith("r2v12-parslp", full) { ctx -> val b = BattleShotKit.bound(ctx); set("par", "slp"); b.panel!!.renderAll(); b.root }
    @Test fun toxFrz() = paparazzi.shootWith("r2v12-toxfrz", full) { ctx -> val b = BattleShotKit.bound(ctx); set("tox", "frz"); b.panel!!.renderAll(); b.root }

    @Test fun compactCards() = paparazzi.shootWith("r2v12-cc1", listOf(Size.SPLIT, Size.PIP_169M, Size.FLOATB)) { ctx ->
        AppPrefs.setCompactCards(ctx, true)
        val b = BattleShotKit.bound(ctx); set("brn", "par"); b.panel!!.renderAll(); b.root
    }

    @Test fun classicCorners() = paparazzi.shootWith("r2v12-corners", listOf(Size.SPLIT)) { ctx ->
        AppPrefs.setCardsUnderOwn(ctx, false)
        val b = BattleShotKit.bound(ctx); set("slp", "psn"); b.panel!!.renderAll(); b.root
    }

    // ---------------------------------------------------------------- small windows

    @Test fun smallNone() = paparazzi.shootWith("r2v12-small-none", small) { ctx -> bare(ctx, null, null) }
    @Test fun smallStatus() = paparazzi.shootWith("r2v12-small-psnbrn", small) { ctx -> bare(ctx, "psn", "brn") }
    @Test fun smallStatusCc1() = paparazzi.shootWith("r2v12-small-cc1-parfrz", small) { ctx -> AppPrefs.setCompactCards(ctx, true); bare(ctx, "par", "frz") }

    // ---------------------------------------------------------------- the bubble

    private fun field(o: Any, name: String): Any = o.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(o)!! }

    private fun shootBubble(name: String, size: Size, theme: Theme, enemy: String?, player: String?, tapEnemy: Boolean, smallWindow: Boolean = false, cc1: Boolean = false) {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        AppPrefs.setCompactCards(ctx, cc1)
        val p = if (smallWindow) bare(ctx, enemy, player) else BattlePanelView(ctx).also { set(enemy, player); it.renderAll() }
        val frame = FrameLayout(ctx).apply { addView(p, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(frame, "$name-before", 0)
        (field(p.arena, if (tapEnemy) "eStatus" else "pStatus") as View).performClick()
        paparazzi.snapshot(frame, "$name-${size.name.lowercase()}-${theme.name.lowercase()}", 100)
    }

    @Test fun bubbles() {
        for (theme in Theme.entries) {
            shootBubble("r2v12-bubble-player", Size.SPLIT, theme, "psn", "psn", tapEnemy = false)
            shootBubble("r2v12-bubble-enemy", Size.SPLIT, theme, "par", "brn", tapEnemy = true)
            shootBubble("r2v12-bubble-tox", Size.PHONE, theme, "slp", "tox", tapEnemy = false)
            shootBubble("r2v12-bubble-small", Size.PIP_169M, theme, "frz", "slp", tapEnemy = false, smallWindow = true)
            shootBubble("r2v12-bubble-float", Size.FLOATB, theme, "frz", "par", tapEnemy = true, smallWindow = true, cc1 = true)
        }
    }

    // ---------------------------------------------------------------- geometry on the real views

    private fun rectIn(arena: ArenaView, v: View): Rect = Rect(0, 0, v.width, v.height).also { arena.offsetDescendantRectToMyCoords(v, it) }

    /** The picture area of a sprite holder: bottom-centre 85 %, as the plan's overlap test uses. */
    private fun art(r: Rect): Rect {
        val w = r.width() * 0.85f; val h = r.height() * 0.85f
        val x = r.left + r.width() * 0.075f; val y = r.top + r.height() * 0.15f
        return Rect(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt())
    }

    private fun hits(a: Rect, b: Rect, slack: Int) = a.left < b.right - slack && b.left < a.right - slack && a.top < b.bottom - slack && b.top < a.bottom - slack

    /**
     * For each window size and card style: before and after a status comes on both cards, the cards' heights and the HP bars'
     * widths are unchanged (no jump), each status row lies inside its card, clear of both sprites' pictures and of the other
     * card, and the bubble opened by a tap stays inside the arena. [PanelFit] must say the window fits.
     */
    @Test fun geometry() {
        val sizes = listOf(Size.PHONE, Size.SPLIT, Size.SPLIT_SHORT, Size.SPLIT_TINY, Size.TABLET, Size.PIP_169, Size.PIP_169M, Size.PIP_11, Size.PIP, Size.FLOATB, Size.FLOATBATTLE)
        val problems = ArrayList<String>()
        val out = StringBuilder()
        for (size in sizes) for (cc in listOf(false, true)) for (own in listOf(true, false)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            AppPrefs.setCompactCards(ctx, cc)
            AppPrefs.setCardsUnderOwn(ctx, own)
            val isSmall = size.wDp < 400
            val p = if (isSmall) bare(ctx, null, null) else BattlePanelView(ctx).also { set(null, null); it.renderAll() }
            val frame = FrameLayout(ctx).apply { addView(p, FrameLayout.LayoutParams(-1, -1)) }
            val tag = "${size.name} cc=$cc own=$own"
            paparazzi.snapshot(frame, "r2v12-geo", 0)
            val arena = p.arena
            val eCard = field(arena, "enemyCard") as View
            val pCard = field(arena, "playerCard") as View
            val eHp = field(arena, "eHp") as View
            val pHp = field(arena, "pHp") as View
            val before = listOf(eCard.height, pCard.height, eHp.width, pHp.width, eHp.height, pHp.height)

            set("tox", "par"); p.renderAll()
            paparazzi.snapshot(frame, "r2v12-geo", 0)
            val after = listOf(eCard.height, pCard.height, eHp.width, pHp.width, eHp.height, pHp.height)
            if (before != after) problems += "$tag: card or bar changed with a status: $before -> $after"
            if (!p.currentFit.ok) { out.append("$tag: PanelFit says not ok (skipped)\n"); continue }

            val rows = listOf("eStatus", "pStatus").map { field(arena, it) as View }
            val eR = rectIn(arena, rows[0]); val pR = rectIn(arena, rows[1])
            val eCardR = rectIn(arena, eCard); val pCardR = rectIn(arena, pCard)
            val eArt = art(rectIn(arena, field(arena, "enemyHolder") as View)); val pArt = art(rectIn(arena, field(arena, "playerHolder") as View))
            val aw = arena.width; val ah = arena.height
            for ((name, r, card, other) in listOf(Quad("wild", eR, eCardR, pCardR), Quad("ours", pR, pCardR, eCardR))) {
                if (r.left < card.left - 1 || r.right > card.right + 1 || r.top < card.top - 1 || r.bottom > card.bottom + 1) problems += "$tag: $name row $r outside its card $card"
                if (r.left < 0 || r.top < 0 || r.right > aw + 1 || r.bottom > ah + 1) problems += "$tag: $name row $r outside the arena ${aw}x$ah"
                if (hits(r, eArt, 2) || hits(r, pArt, 2)) problems += "$tag: $name row $r covers a sprite (wild $eArt ours $pArt)"
                if (hits(r, other, 1)) problems += "$tag: $name row $r on the other card $other"
            }
            val badges = rows.map { (it as ViewGroup).getChildAt(0) }
            if (badges.any { it == null || it.width <= 0 }) problems += "$tag: a badge is missing or has no size"

            // (a snapshot detaches the view, which closes the bubble, so it is read straight after the tap)
            for (enemy in listOf(true, false)) {
                val row = field(arena, if (enemy) "eStatus" else "pStatus") as View
                val bubble = field(arena, "bubble") as View
                row.performClick()
                val lp = bubble.layoutParams as FrameLayout.LayoutParams
                val br = Rect(lp.leftMargin, lp.topMargin, lp.leftMargin + lp.width, lp.topMargin + lp.height)
                if (bubble.visibility != View.VISIBLE) problems += "$tag: no bubble after a tap on the ${if (enemy) "wild" else "our"} badge"
                else if (br.left < 0 || br.top < 0 || br.right > aw || br.bottom > ah) problems += "$tag: bubble $br outside the arena ${aw}x$ah"
                else if (lp.width < 40 || lp.height < 20) problems += "$tag: bubble $br too small"
                // a second tap on the same badge closes it
                row.performClick()
                if (bubble.visibility == View.VISIBLE) problems += "$tag: the second tap did not close the bubble"
            }
            out.append("$tag: ok  cards ${eCard.height}/${pCard.height} px  rows $eR $pR\n")
        }
        java.io.File(Shots.outDir, "r2v12-geometry.txt").writeText(out.toString() + problems.joinToString("\n"))
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private data class Quad(val name: String, val row: Rect, val card: Rect, val other: Rect)

    /** Five different badge colours per theme, and the symbol reads on each (3:1 is the usual bar for a graphic; 2.5 is the code's threshold for white). */
    @Test fun coloursAreDistinctAndReadable() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SPLIT, theme))
            val ctx = Shots.files(paparazzi.context)
            val fills = listOf("psn", "brn", "par", "slp", "frz").map { StatusBadge.fillColor(ctx, it) }
            assertEquals("$theme", 5, fills.toSet().size)
            assertEquals(StatusBadge.fillColor(ctx, "psn"), StatusBadge.fillColor(ctx, "tox"))
            for (f in fills) {
                val ink = StatusBadge.inkColor(f)
                val c = androidx.core.graphics.ColorUtils.calculateContrast(ink, f)
                assertTrue("$theme fill ${Integer.toHexString(f)} ink ${Integer.toHexString(ink)} contrast $c", c >= 2.5)
            }
        }
    }

    @Test fun textsMatchStatusInfo() {
        for (s in listOf("par", "brn", "psn", "tox", "slp", "frz")) {
            assertEquals(s, StatusInfo.of(s)!!.status)
            assertTrue(StatusInfo.of(s)!!.line.contains(':'))
        }
    }
}
