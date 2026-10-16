package com.husarp.ankigo.ui

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.game.StatCalc

// TICKET T14 owns this file: the dialog of one seen/caught Pokédex entry.

object DexDetail {
    /** Base stat that fills a bar (the best bases go above it and stay full). */
    private const val BASE_MAX = 160

    /** Your copies of species [num], highest level first (shiny first on a tie). */
    fun copies(mons: List<Mon>, num: Int): List<Mon> =
        mons.filter { it.species == num }.sortedWith(compareByDescending<Mon> { it.level }.thenByDescending { it.shiny })

    /** Second line of a copy in the picker: nickname (if any) and where it is. */
    fun copyNote(mon: Mon, inTeam: Boolean): String =
        listOfNotNull(mon.nickname?.takeIf { it.isNotBlank() }?.let { "\u201C$it\u201D" }, if (inTeam) "In team" else "In box").joinToString(" · ")

    fun show(a: GamePage, sp: Species) {
        val data = a.data
        val state = a.state
        val caught = sp.num in state.caught
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), Ui.dp(a, 6))
        }
        val img = ImageView(a)
        MonPreview.show(a, sp.num, false, img)
        col.addView(img, LinearLayout.LayoutParams(Ui.dp(a, if (DetailParts.compact(a)) 72 else 96), Ui.dp(a, if (DetailParts.compact(a)) 72 else 96)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(Ui.text(a, 20f, 900, R.color.ag_text).apply { text = sp.name; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2))
        col.addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply {
            text = "#%03d · Gen %d · %s".format(sp.baseNum, sp.gen, if (caught) "caught" else "seen")
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        col.addView(Bits.typeRow(a, sp.types), LinearLayout.LayoutParams(-2, -2).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(a, 6)
        })

        val compact = DetailParts.compact(a)
        col.addView(DetailParts.header(a, R.drawable.ic_stats, "Base stats", compact))
        col.addView(DetailParts.statHint(a))
        col.addView(Ui.text(a, 11f, 700, R.color.ag_ov).apply {
            text = "Grey, right: lowest to highest at Lv 100."
            setPadding(0, 0, 0, Ui.dp(a, 2))
        })
        Stat.entries.forEach { s ->
            val row = DetailParts.statRow(a, s, sp.base[s], DetailMath.fraction(sp.base[s], BASE_MAX), 0, null, compact) as LinearLayout
            val r = StatCalc.range(sp.base[s], s)
            row.addView(Ui.text(a, 11f, 700, R.color.ag_ov).apply { text = "${r.first}\u2013${r.last}"; gravity = Gravity.END; maxLines = 1 },
                LinearLayout.LayoutParams(Ui.dp(a, 58), -2))
            col.addView(row)
        }
        col.addView(DetailParts.totalRow(a, sp.base.total, null))

        col.addView(DetailParts.header(a, R.drawable.ic_update, "Evolution", compact))
        MonDetail.evolvesFrom(data, sp)?.let { col.addView(line(a, "From", it)) }
        val into = MonDetail.evolvesInto(data, sp)
        if (into.isEmpty()) col.addView(line(a, "Into", "fully evolved")) else into.forEach { col.addView(line(a, "Into", it)) }

        col.addView(DetailParts.header(a, R.drawable.ic_dex, "In your collection", compact))
        val mine = copies(state.mons, sp.num)
        col.addView(line(a, "Owned", mine.size.toString()))
        lateinit var dlg: AlertDialog
        if (mine.isNotEmpty()) {
            // Options / Details work on one copy; with several the player picks which first
            fun withCopy(f: (Mon) -> Unit) {
                if (mine.size == 1) f(mine[0])
                else Pickers.pickMon(a, "Which ${sp.name}?", data, mine, { copyNote(it, it.uid in a.state.team) }, f)
            }
            val options = DetailParts.secondaryButton(a, "Options") {}
            options.setOnClickListener { withCopy { MonActions(a).menu(it, options) { dlg.dismiss() } } }
            val details = DetailParts.secondaryButton(a, "Details") { withCopy { MonDetail.show(a, data, it) } }
            col.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(options, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginEnd = Ui.dp(a, 4) })
                addView(details, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(a, 4) })
            }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 6) })
        }

        // wishlist star toggle: wishlisted species are always caught by the auto rule
        val star = Ui.text(a, 14f, 900, R.color.ag_text).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(a, 12), Ui.dp(a, 9), Ui.dp(a, 12), Ui.dp(a, 9))
            background = Ui.rounded(Ui.c(a, R.color.ag_mantle), 10, a)
        }
        val hint = Ui.text(a, 11f, 700, R.color.ag_sub0).apply {
            text = "Always caught by the auto rule."
        }
        fun label() {
            val on = sp.num in a.state.settings.wishlist
            star.text = if (on) "★  On your wishlist" else "☆  Add to wishlist"
            hint.visibility = if (on) View.VISIBLE else View.GONE
        }
        label()
        star.setOnClickListener {
            GameRepo.run(a) { g ->
                val w = g.state.settings.wishlist
                if (!w.remove(sp.num)) w.add(sp.num)
                Unit
            }
            label()
            a.requestRender()
        }
        col.addView(star, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 10) })
        col.addView(hint, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 6) })

        dlg = AlertDialog.Builder(a).setTitle(sp.name).setView(Bits.scroll(a, col)).setPositiveButton("Close", null).create()
        Pickers.show(dlg)
    }

    private fun line(a: GamePage, label: String, value: String): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2))
        addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = label }, LinearLayout.LayoutParams(Ui.dp(a, 60), -2))
        addView(Ui.text(a, 12f, 800, R.color.ag_text).apply { text = value }, LinearLayout.LayoutParams(0, -2, 1f))
    }
}
