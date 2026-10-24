package com.husarp.ankigo.shots

import android.view.Gravity
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.DetailSections
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/**
 * R17C-2: the details' Training block of a Main that has won a lot: EVs above 0, friendship at the cap ("255 / 255", an older save's
 * 300 shows the same), holding a Soothe Bell; the Friendship title is tappable now. PNGs: r17c2-training-<size>-<theme>.
 */
class R17C2TrainingShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun training() = paparazzi.shootWith("r17c2-training", listOf(Size.PHONE, Size.SMALL), sprites = false) { ctx ->
        val d = TestData.world()
        val mon = TestData.mon(d, 25, 36, listOf("tackle"), uid = "t1").apply {
            evs = Stats(0, 4, 0, 0, 0, 252)
            friendship = 300 // an older save: shows as 255
            heldItem = "soothe-bell"
        }
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
            addView(DetailSections.training(ctx, mon, compact = false), LinearLayout.LayoutParams(-1, -2))
        }
    }
}
