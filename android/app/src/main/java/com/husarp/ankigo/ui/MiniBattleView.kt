package com.husarp.ankigo.ui

import com.husarp.ankigo.game.lookNum
import android.content.Context
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.StatCalc

/** Two sprites and numeric HP in one 48 dp line. */
class MiniBattleView(ctx: Context) : LinearLayout(ctx) {
    init { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Ui.c(ctx, R.color.ag_mantle)) }
    fun render() {
        removeAllViews()
        val data = GameRepo.data ?: return
        val st = GameRepo.state ?: return
        val mons = listOfNotNull(st.main, st.battle?.wild)
        for (mon in mons) {
            val image = ImageView(context)
            Ui.staticSprite(context, if (mon === st.battle?.wild) mon.species else mon.lookNum(data), mon.shiny, image)
            addView(image, LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40)))
            val col = LinearLayout(context).apply { orientation = VERTICAL }
            val max = if (mon === st.battle?.wild) st.battle!!.maxWildHp(data) else StatCalc.maxHp(data, mon)
            col.addView(Ui.text(context, 10f, 800, R.color.ag_text).apply { text = DisplayNumbers.hp(mon.hp, max) })
            col.addView(BarView(context).apply { val f = if(max > 0) mon.hp.toFloat()/max else 0f; set(f, Ui.hpColor(context, f), false) }, LayoutParams(-1, Ui.dp(context, 5)))
            addView(col, LayoutParams(0, -2, 1f))
        }
    }
}
