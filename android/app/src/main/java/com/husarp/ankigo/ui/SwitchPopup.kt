package com.husarp.ankigo.ui

import com.husarp.ankigo.game.lookNum
import android.app.AlertDialog
import android.content.Context
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.shownName

/**
 * "Switch Pokémon" (PLAN 99, pick 13C): a centred popup with the team as cards in a 3-wide grid (sprite, name, level, small
 * HP bar). The main is outlined, a fainted one is greyed and can't be picked. A tap picks, a long press opens the details
 * (without the options button). Used from the ⋯ menu of the battle screen and of the Study panel; not in the floating battle.
 */
object SwitchPopup {
    private const val COLUMNS = 3

    /** A fainted Pokémon can't be picked. (Tapping the main just closes the popup.) */
    fun pickable(hp: Int): Boolean = hp > 0

    /** Shows the popup; [onPick] gets the uid of the chosen team member. */
    fun show(a: Context, data: GameData, state: GameState, onPick: (String) -> Unit) {
        lateinit var dlg: AlertDialog
        val body = content(a, data, state,
            onPick = { dlg.dismiss(); onPick(it) },
            onDetails = { MonDetail.show(a, data, it) })
        dlg = AlertDialog.Builder(a).setView(body).create()
        Pickers.show(dlg)
    }

    /** The popup's inside (header + cards); also what the screenshot tests render. */
    internal fun content(a: Context, data: GameData, state: GameState, onPick: (String) -> Unit, onDetails: (Mon) -> Unit): View {
        val compact = DetailParts.compact(a)
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14))
        }
        val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.text(a, 17f, 900, R.color.ag_text).apply { text = "Switch Pokémon"; maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Ui.text(a, 11f, 800, R.color.ag_sub0).apply { text = "hold for details"; maxLines = 1 },
            LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(a, 8) })
        col.addView(head, LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(a, 12) })

        val mons = state.team.mapNotNull { state.mon(it) }
        val mainUid = state.team.firstOrNull()
        val grid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        mons.chunked(COLUMNS).forEachIndexed { r, line ->
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until COLUMNS) {
                val mon = line.getOrNull(i)
                val lp = LinearLayout.LayoutParams(0, -2, 1f).also {
                    if (i > 0) it.marginStart = Ui.dp(a, 8)
                }
                row.addView(if (mon != null) card(a, data, mon, mon.uid == mainUid, compact, onPick, onDetails) else View(a), lp)
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2).also { if (r > 0) it.topMargin = Ui.dp(a, 8) })
        }
        col.addView(grid, LinearLayout.LayoutParams(-1, -2))
        return Bits.scroll(a, col)
    }

    private fun card(a: Context, data: GameData, mon: Mon, isMain: Boolean, compact: Boolean, onPick: (String) -> Unit, onDetails: (Mon) -> Unit): View {
        val fainted = mon.hp <= 0
        val maxHp = StatCalc.maxHp(data, mon).coerceAtLeast(1)
        val frac = (mon.hp.toFloat() / maxHp).coerceIn(0f, 1f)
        val spriteDp = if (compact) 40 else 56
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 8))
            background = Ui.rounded(Ui.c(a, R.color.ag_mantle), 12, a).apply {
                if (isMain) setStroke(Ui.dp(a, 2), Ui.c(a, R.color.ag_mauve))
            }
            alpha = if (fainted) 0.5f else 1f
            contentDescription = "${mon.shownName}, level ${mon.level}" + if (fainted) ", fainted" else if (isMain) ", main" else ""
            setOnClickListener { if (pickable(mon.hp)) onPick(mon.uid) }
            setOnLongClickListener { onDetails(mon); true }
            val img = ImageView(a)
            MonPreview.show(a, mon.lookNum(data), mon.shiny, img)
            addView(img, LinearLayout.LayoutParams(Ui.dp(a, spriteDp), Ui.dp(a, spriteDp)))
            addView(Ui.text(a, 12f, 900, R.color.ag_text).apply {
                text = mon.shownName; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 4) })
            addView(Ui.text(a, 10f, 800, R.color.ag_sub0).apply {
                LevelTier.style(this, mon.level)
                if (fainted) { val lv = text.length; text = SpannableString("$text · fainted").apply { setSpan(ForegroundColorSpan(Ui.c(a, R.color.ag_sub0)), lv, length, 0) } }
                maxLines = 1; gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 2) })
            addView(BarView(a).apply { set(frac, Ui.hpColor(a, frac), false) },
                LinearLayout.LayoutParams(-1, Ui.dp(a, 5)).also { it.topMargin = Ui.dp(a, 6) })
        }
    }
}
