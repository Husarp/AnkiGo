package com.husarp.ankigo.ui

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Stat

// TICKET T14 owns this file: the dialog of one seen/caught Pokédex entry.

object DexDetail {
    private val LABELS = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")

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
        col.addView(img, LinearLayout.LayoutParams(Ui.dp(a, 96), Ui.dp(a, 96)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(Ui.text(a, 20f, 900, R.color.ag_text).apply { text = sp.name; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2))
        col.addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply {
            text = "#%03d · Gen %d · %s".format(sp.baseNum, sp.gen, if (caught) "caught" else "seen")
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        col.addView(Bits.typeRow(a, sp.types), LinearLayout.LayoutParams(-2, -2).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(a, 6)
        })

        col.addView(heading(a, "Base stats"))
        Stat.entries.forEachIndexed { i, s ->
            val v = sp.base[s]
            col.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2))
                addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = LABELS[i] }, LinearLayout.LayoutParams(Ui.dp(a, 34), -2))
                addView(Ui.text(a, 12f, 900, R.color.ag_text).apply { text = v.toString(); gravity = Gravity.END }, LinearLayout.LayoutParams(Ui.dp(a, 30), -2))
                val bar = BarView(a)
                bar.set(v / 160f, Ui.c(a, R.color.ag_mauve), false)
                addView(bar, LinearLayout.LayoutParams(0, Ui.dp(a, 5), 1f).also { it.marginStart = Ui.dp(a, 8) })
            })
        }
        col.addView(Ui.text(a, 11f, 800, R.color.ag_sub0).apply { text = "Total ${sp.base.total}" },
            LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(a, 2) })

        col.addView(heading(a, "Evolution"))
        MonDetail.evolvesFrom(data, sp)?.let { col.addView(line(a, "From", it)) }
        val into = MonDetail.evolvesInto(data, sp)
        if (into.isEmpty()) col.addView(line(a, "Into", "fully evolved")) else into.forEach { col.addView(line(a, "Into", it)) }

        col.addView(heading(a, "In your collection"))
        col.addView(line(a, "Owned", state.mons.count { it.species == sp.num }.toString()))

        // wishlist star toggle: wishlisted species are always caught by the auto rule
        val star = Ui.text(a, 14f, 900, R.color.ag_text).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(a, 12), Ui.dp(a, 9), Ui.dp(a, 12), Ui.dp(a, 9))
            background = Ui.rounded(Ui.c(a, R.color.ag_mantle), 10, a)
        }
        val hint = Ui.text(a, 11f, 700, R.color.ag_sub0).apply {
            text = "Always caught, and met twice as often within its rarity."
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

        Pickers.info(a, sp.name, Bits.scroll(a, col))
    }

    private fun heading(a: GamePage, text: String): TextView = Ui.text(a, 11f, 900, R.color.ag_sub0).apply {
        this.text = text.uppercase()
        letterSpacing = 0.06f
        setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 4))
    }

    private fun line(a: GamePage, label: String, value: String): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2))
        addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = label }, LinearLayout.LayoutParams(Ui.dp(a, 60), -2))
        addView(Ui.text(a, 12f, 800, R.color.ag_text).apply { text = value }, LinearLayout.LayoutParams(0, -2, 1f))
    }
}
