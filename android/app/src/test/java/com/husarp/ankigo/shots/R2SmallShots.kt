package com.husarp.ankigo.shots

import android.widget.FrameLayout
import android.widget.ImageView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import org.junit.Rule
import org.junit.Test

/** R2-V02: the launcher icon (ball with thin outline) on white, black and a colour. */
class R2SmallShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun launcherIcon() = paparazzi.shootPage("r2-launcher", listOf(Variants.SPLIT), themes = listOf(Theme.LIGHT)) { ctx ->
        val row = android.widget.LinearLayout(ctx)
        for (c in intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF6A5ACD.toInt())) {
            val f = FrameLayout(ctx).apply { setBackgroundColor(c) }
            f.addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_launcher) }, FrameLayout.LayoutParams(300, 300))
            row.addView(f, android.widget.LinearLayout.LayoutParams(320, 320))
        }
        row
    }
}
