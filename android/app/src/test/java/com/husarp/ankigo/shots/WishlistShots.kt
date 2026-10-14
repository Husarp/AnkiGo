package com.husarp.ankigo.shots

import android.app.AlertDialog
import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.DexDetail
import com.husarp.ankigo.ui.DexFilter
import com.husarp.ankigo.ui.DexPage
import org.junit.Rule
import org.junit.Test

/** Wishlist (T42): PNGs wishlist-dexdetail, wishlist-dexdetail-off, wishlist-dex, wishlist-dex-filter (each -phone/-split, -light/-dark). */
class WishlistShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val bothVariants = listOf(Variants.PHONE, Variants.SPLIT)

    private fun dialog(name: String, num: Int, wish: Boolean) {
        paparazzi.shootPage(name, bothVariants) { ctx ->
            val w = GameRepo.state!!.settings.wishlist
            if (wish) w.add(num) else w.remove(num)
            val d: AlertDialog = DialogShots.capture { DexDetail.show(DexPage(ctx, NoHost), GameRepo.data!!.sp(num)) } ?: error("no dialog")
            DialogShots.scene(ctx, d)
        }
    }

    @Test fun detail() { dialog("wishlist-dexdetail", 25, true); dialog("wishlist-dexdetail-off", 26, false) }

    private fun dex(ctx: Context, f: DexFilter): View {
        val p = DexPage(ctx, NoHost)
        setField(p, "filter", f)
        return pageScreen(ctx, p)
    }

    @Test fun grid() {
        paparazzi.shootPage("wishlist-dex", bothVariants) { dex(it, DexFilter()) }
        paparazzi.shootPage("wishlist-dex-filter", bothVariants) { dex(it, DexFilter(wishlistOnly = true)) }
    }
}
