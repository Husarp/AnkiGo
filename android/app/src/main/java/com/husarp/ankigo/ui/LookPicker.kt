package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.PorterDuff
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Looks
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.lookSpecies
import com.husarp.ankigo.game.shownName

/**
 * "Change look" (PLAN 102, pick 8B): a small popup with the Pokémon's evolution line (picture + name, the shown look
 * outlined and ticked). Tap a picture, then Use; "Use its own look" resets. Purely visual. Opened from the options menu
 * ([MonActions.menu]) only when the Pokémon has more than one look ([Looks.hasChoice]).
 */
object LookPicker {
    private const val COLUMNS = 3

    /** What the cards show under a name: "Current" for its own species, else "Stage n" (1 = base form). */
    fun caption(index: Int, num: Int, ownNum: Int): String = if (num == ownNum) "Current" else "Stage ${index + 1}"

    /** What a pick means for [Game.setLook]: its own species = null (no look chosen). */
    fun pickOf(num: Int, ownNum: Int): Int? = if (num == ownNum) null else num

    /** Shows the popup; [onUse] gets the species number to show, or null for the Pokémon's own look. */
    fun show(a: Context, data: GameData, mon: Mon, caught: Set<Int>, onUse: (Int?) -> Unit) {
        lateinit var dlg: AppDialog
        val body = Body(a, data, mon, caught) { dlg.dismiss(); onUse(null) }
        dlg = AppDialog.Builder(a).setView(body.view)
            .setPositiveButton("Use") { _, _ -> if (body.selected != mon.lookSpecies(data).num) onUse(pickOf(body.selected, mon.species)) }
            .setNegativeButton("Cancel", null).create()
        Pickers.show(dlg)
    }

    /** The popup's inside (also what the screenshot tests render); [selected] is the picked species number. */
    internal class Body(private val a: Context, private val data: GameData, private val mon: Mon, private val caught: Set<Int>, onReset: () -> Unit) {
        var selected: Int = mon.lookSpecies(data).num
            private set
        private val cards = ArrayList<Pair<Int, LinearLayout>>()
        private val checks = HashMap<Int, ImageView>()
        val view: View

        init {
            val compact = DetailParts.compact(a)
            val col = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 6))
            }
            val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(Ui.text(a, 17f, 900, R.color.ag_text).apply { text = "Change look"; maxLines = 1 },
                LinearLayout.LayoutParams(-2, -2))
            head.addView(Ui.text(a, 11f, 800, R.color.ag_sub0).apply {
                text = mon.shownName; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.END
            }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(a, 8) })
            col.addView(head, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(a, 12) })

            // captions count the FULL line (Stage 1 = base form), so a hidden stage keeps its number
            val full = Looks.choices(data, mon, data.species.keys)
            val shown = Looks.choices(data, mon, caught)
            val line = full.withIndex().filter { v -> shown.any { it.num == v.value.num } }
            val grid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            line.chunked(COLUMNS).forEachIndexed { r, chunk ->
                val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                for (i in 0 until COLUMNS) {
                    val item = chunk.getOrNull(i)
                    val lp = LinearLayout.LayoutParams(0, -2, 1f).also { if (i > 0) it.marginStart = Ui.dp(a, 8) }
                    row.addView(if (item != null) card(item.index, item.value.num, item.value.name, compact) else View(a), lp)
                }
                grid.addView(row, LinearLayout.LayoutParams(-1, -2).also { if (r > 0) it.topMargin = Ui.dp(a, 8) })
            }
            col.addView(grid, LinearLayout.LayoutParams(-1, -2))
            if (mon.look != null) {
                col.addView(Ui.text(a, 13f, 900, R.color.ag_mauve).apply {
                    text = "Use its own look"; gravity = Gravity.CENTER
                    minimumHeight = Ui.dp(a, 44)
                    setOnClickListener { onReset() }
                }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 4) })
            }
            refresh()
            view = Bits.scroll(a, col)
        }

        private fun card(index: Int, num: Int, name: String, compact: Boolean): View {
            val spriteDp = if (compact) 44 else 60
            val card = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8))
                val cap = caption(index, num, mon.species)
                contentDescription = "$name, $cap"
                setOnClickListener { selected = num; refresh() }
                val img = ImageView(a)
                MonPreview.show(a, num, mon.shiny, img)
                addView(img, LinearLayout.LayoutParams(Ui.dp(a, spriteDp), Ui.dp(a, spriteDp)))
                addView(Ui.text(a, 12f, 900, R.color.ag_text).apply {
                    text = name; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 4) })
                addView(Ui.text(a, 10f, 800, R.color.ag_sub0).apply { text = cap; maxLines = 1; gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 2) })
            }
            cards += num to card
            val tick = ImageView(a).apply {
                setImageResource(R.drawable.ic_check)
                setColorFilter(Ui.c(a, R.color.ag_base), PorterDuff.Mode.SRC_IN)
                background = Ui.rounded(Ui.c(a, R.color.ag_mauve), 9, a)
                setPadding(Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3))
            }
            checks[num] = tick
            return FrameLayout(a).apply {
                addView(card, FrameLayout.LayoutParams(-1, -2))
                addView(tick, FrameLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18), Gravity.TOP or Gravity.END).also {
                    it.topMargin = Ui.dp(a, 5); it.marginEnd = Ui.dp(a, 5)
                })
            }
        }

        private fun refresh() {
            for ((num, card) in cards) {
                val on = num == selected
                card.background = Ui.rounded(if (on) Bits.blend(Ui.c(a, R.color.ag_mauve), Ui.c(a, R.color.ag_mantle), 0.18f) else Ui.c(a, R.color.ag_mantle), 12, a).apply {
                    if (on) setStroke(Ui.dp(a, 2), Ui.c(a, R.color.ag_mauve))
                }
                checks[num]?.visibility = if (on) View.VISIBLE else View.GONE
            }
        }

        /** Test hook: tap the card of species [num]. */
        internal fun pick(num: Int) { selected = num; refresh() }
    }
}
