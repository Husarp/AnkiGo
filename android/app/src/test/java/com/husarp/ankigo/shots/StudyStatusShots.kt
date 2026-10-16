package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The status row in the study layouts (strip and narrow split) is reserved like everywhere else: a status coming or going
 * changes no card height. PNGs: studystatus-none|on-<size>-<theme>; [geometry] writes studystatus-geometry.txt.
 */
class StudyStatusShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun set(enemy: String?, player: String?) {
        val b = GameRepo.state!!.battle!!
        b.enemy.status = enemy
        b.player.status = player
    }

    private fun study(ctx: Context, enemy: String?, player: String?): Pair<View, ArenaView> {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        setField(act, "heightMode", 1)
        val root = act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>")
        setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic")
        setField(act, "cardName", "Card 1")
        setField(act, "flipped", false)
        setField(act, "locked", false)
        (getField(act, "subtitle") as android.widget.TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as BattlePanelView
        set(enemy, player)
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        return root to panel.arena
    }

    private val sizes = listOf(Size.PHONE, Size.SPLIT)

    @Test fun none() = paparazzi.shootWith("studystatus-none", sizes) { study(it, null, null).first }
    @Test fun on() = paparazzi.shootWith("studystatus-on", sizes) { study(it, "par", "brn").first }

    private fun field(o: Any, name: String): Any = o.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(o)!! }
    private fun rectIn(arena: ArenaView, v: View): Rect = Rect(0, 0, v.width, v.height).also { arena.offsetDescendantRectToMyCoords(v, it) }
    private fun art(r: Rect): Rect {
        val w = r.width() * 0.85f; val h = r.height() * 0.85f
        val x = r.left + r.width() * 0.075f; val y = r.top + r.height() * 0.15f
        return Rect(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt())
    }
    private fun hits(a: Rect, b: Rect, slack: Int) = a.left < b.right - slack && b.left < a.right - slack && a.top < b.bottom - slack && b.top < a.bottom - slack

    private fun overlap(a: Rect, b: Rect): Int {
        val w = minOf(a.right, b.right) - maxOf(a.left, b.left); val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return if (w > 0 && h > 0) w * h else 0
    }

    @Test fun geometry() {
        val problems = ArrayList<String>()
        val out = StringBuilder()
        for (size in listOf(Size.PHONE, Size.SPLIT, Size.SPLIT_SHORT, Size.SPLIT_TINY, Size.LAND, Size.TABLET)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val (root, arena) = study(ctx, null, null)
            val frame = android.widget.FrameLayout(ctx).apply { addView(root, android.widget.FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "studystatus-geo", 0)
            val eCard = field(arena, "enemyCard") as View
            val pCard = field(arena, "playerCard") as View
            val before = listOf(eCard.height, pCard.height, eCard.top, pCard.top)
            set("tox", "par"); (getFieldPanel(arena)).renderAll()
            paparazzi.snapshot(frame, "studystatus-geo", 0)
            val after = listOf(eCard.height, pCard.height, eCard.top, pCard.top)
            val tag = size.name
            if (before != after) problems += "$tag: card moved with a status: $before -> $after"
            val eArt = art(rectIn(arena, field(arena, "enemyHolder") as View)); val pArt = art(rectIn(arena, field(arena, "playerHolder") as View))
            for ((name, rowName, card) in listOf(Triple("wild", "eStatus", eCard), Triple("ours", "pStatus", pCard))) {
                val row = field(arena, rowName) as View
                val r = rectIn(arena, row); val c = rectIn(arena, card)
                if (r.left < c.left - 1 || r.right > c.right + 1 || r.top < c.top - 1 || r.bottom > c.bottom + 1) problems += "$tag: $name row $r outside card $c"
                // the card must not cover more of a sprite than it did before the row was added (the card without its row); 600 px2 lets a card edge grow along the transparent side of a sprite box
                val before = if (name == "wild") Rect(c.left, c.top, c.right, c.bottom - r.height()) else Rect(c.left, c.top + r.height(), c.right, c.bottom)
                for ((what, art) in listOf("wild sprite" to eArt, "our sprite" to pArt)) {
                    // before the row was added the sprite boxes were that much taller (the enemy's downwards, ours upwards)
                    val old = if (what == "wild sprite") Rect(art.left, art.top, art.right, art.bottom + r.height()) else Rect(art.left, art.top - r.height(), art.right, art.bottom)
                    val now = overlap(c, art); val was = overlap(before, old)
                    if (now > was + 600) problems += "$tag: $name card covers $what by $now px2 (was $was without the row)"
                }
                if (r.height() <= 0) problems += "$tag: $name row has no height"
            }
            val ec = rectIn(arena, eCard); val pc = rectIn(arena, pCard)
            out.append("$tag: arena ${arena.width}x${arena.height} eCard $ec pCard $pc eArt $eArt pArt $pArt cardOnSprite=${hits(ec, eArt, 2) || hits(ec, pArt, 2) || hits(pc, eArt, 2) || hits(pc, pArt, 2)} cardsTouch=${hits(ec, pc, 0)}\n")
        }
        java.io.File(Shots.outDir, "studystatus-geometry.txt").writeText(out.toString() + problems.joinToString("\n"))
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun getFieldPanel(arena: ArenaView): BattlePanelView {
        var p: android.view.ViewParent? = arena.parent
        while (p != null && p !is BattlePanelView) p = p.parent
        return p as BattlePanelView
    }
}
