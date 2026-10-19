package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.ui.DeckCounts
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R11-C (PLAN 132): the New / Lrn / Due header cells and the counts of every deck row share the same widths and right edges, and the
 * last (green) column keeps its right padding; 4- and 5-digit counts fit; 320 dp to tablet width, light + dark. Made-up decks only.
 * PNG: r11-deck-columns.
 */
class R11DeckColumnsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val decks = listOf(
        Anki.Deck(1, "Made-up big", 12, 1234, 1000, false),
        Anki.Deck(2, "Made-up huge", 3, 12345, 99999, false),
        Anki.Deck(3, "Made-up small", 0, 7, 4, false),
        Anki.Deck(4, "Made-up empty", 0, 0, 0, false),
    )

    private fun lay(ctx: Context, v: View, wDp: Int, hDp: Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, wDp), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, hDp), View.MeasureSpec.EXACTLY))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private fun texts(v: View, out: MutableList<TextView> = mutableListOf()): List<TextView> {
        if (v is TextView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun left(v: View, root: View): Int { var x = 0; var p: View? = v; while (p != null && p !== root) { x += p.left; p = p.parent as? View }; return x }

    @Test fun headerAndRowsLineUp() {
        paparazzi.shoot("r11-deck-columns", listOf(Size.PHONE), Theme.entries) { ctx ->
            var last: View? = null
            for (wDp in listOf(320, 360, 412, 800)) {
                val root = StudyDeckKit.build(ctx, decks, lastDeck = 0)
                lay(ctx, root, wDp, 869)
                val all = texts(root)
                val header = listOf("New", "Lrn", "Due").map { n -> all.single { it.text.toString() == n && it.textSize < Ui.dp(ctx, 11) } }
                for (d in decks) {
                    val row = all.single { it.text.toString() == d.name }.parent.parent as ViewGroup // name label -> name row -> deck row
                    val inRow = texts(row)
                    val cells = listOf("New" to d.new, "Learn" to d.learn, "Review" to d.review).map { (k, n) -> inRow.single { it.contentDescription?.toString() == "$k $n" && it.text.toString() == DeckCounts.format(n) } }
                    for (i in 0..2) {
                        val msg = "w=$wDp ${d.name} col $i"
                        assertEquals("$msg: same width", header[i].width, cells[i].width)
                        assertEquals("$msg: same right edge", left(header[i], root) + header[i].width, left(cells[i], root) + cells[i].width)
                        assertTrue("$msg: the whole number fits on one line", cells[i].layout.getLineWidth(0) <= cells[i].width - cells[i].paddingLeft - cells[i].paddingRight && cells[i].lineCount == 1)
                    }
                    // the green column keeps real padding from the right edge of the list row
                    val rowRight = (cells[2].parent as View).let { left(it, root) + it.width }
                    val gap = rowRight - (left(cells[2], root) + cells[2].width)
                    assertTrue("w=$wDp ${d.name}: right padding $gap px >= ${DeckCounts.END_PAD_DP} dp", gap >= Ui.dp(ctx, DeckCounts.END_PAD_DP))
                }
                // header cells are in the order New, Lrn, Due and do not overlap
                assertTrue(left(header[0], root) + header[0].width <= left(header[1], root) && left(header[1], root) + header[1].width <= left(header[2], root))
                last = root
            }
            val root = StudyDeckKit.build(ctx, decks, lastDeck = 0)
            lay(ctx, root, 412, 869)
            last ?: root
        }
    }
}
