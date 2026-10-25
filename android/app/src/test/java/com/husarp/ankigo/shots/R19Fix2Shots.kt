package com.husarp.ankigo.shots

import android.widget.LinearLayout
import android.view.ViewGroup
import android.widget.ImageView
import org.junit.Assert.*
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.Rule
import org.junit.Test

class R19Fix2Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @Test fun bigGrid() {
        paparazzi.shootWith("r19fix2-grid", listOf(Size.PHONE)) { ctx ->
            // PokeAPI's real item picture at DataStore.ITEM_SPRITES_COMMIT; independent of the local fake sprite cache.
            java.io.File(com.husarp.ankigo.data.Sprites.dir(ctx), "item/poke-ball.png").apply {
                parentFile!!.mkdirs()
                writeBytes(java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAB4AAAAeBAMAAADJHrORAAAAA3NCSVQFBQUYJt5DAAAAIVBMVEX///8xMTH/lDqcSjr/1jr////eWjpKUlKlpcV7e4TezvdcMgHRAAAAAXRSTlMAQObYZgAAAH5JREFUGNNjYKALEBQUROEqKSkhCTACuUpqAnC+kAtIIBEurRLqBOSnCeDgCymB1aclwvlg83DyhcH8tDRDKF/cSL0cyE0uhPHLKyLK0szLYXzG6aWh5eXllXD7l86IAHJXwB0sFTlz1apVjQj/dAG5HQJI/u3o6BDEEx60AwCJgyJdBXcpkgAAAABJRU5ErkJggg=="))
            }
            val s = CatchSummary(true, "Pidgey", CatchSummary.PokedexLine(true, 16), CatchSummary.ThrowLine("poke-ball", 1, 63),
                listOf(CatchSummary.XpLine("a", "Bulbasaur", 30, 7, true)), 25, "Next: Rattata")
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
                val grid = CatchTilesView(ctx).also { it.bindGrid(s, next = true) }
                var icons = 0
                for (i in 0 until grid.childCount - 1) {
                    val row = grid.getChildAt(i) as ViewGroup
                    for (j in 0 until row.childCount) {
                        val tile = row.getChildAt(j) as ViewGroup
                        val holder = tile.getChildAt(0) as ViewGroup
                        assertNull("big icons have no tinted backing", holder.background)
                        val image = holder.getChildAt(0) as ImageView
                        assertNotNull(image.drawable)
                        if (i == 0 && j == 1) assertNull("the real ball is not tinted", image.colorFilter)
                        icons++
                    }
                }
                assertEquals(4, icons)
                addView(grid, LinearLayout.LayoutParams(-1, -2))
            }
        }
    }
}
