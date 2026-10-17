package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.AppDialog
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
            val d: AppDialog = DialogShots.capture { DexDetail.show(DexPage(ctx, NoHost), GameRepo.data!!.sp(num)) } ?: error("no dialog")
            DialogShots.scene(ctx, d)
        }
    }

    /** R6-14: the entry of an unseen / owned species (no add button, a muted line) and a seen one (add button). */
    private fun rule(name: String, num: Int, seen: Boolean, owned: Boolean) {
        paparazzi.shootPage(name, listOf(Variants.PHONE)) { ctx ->
            val st = GameRepo.state!!
            st.settings.wishlist.remove(num)
            if (seen) st.seen += num else st.seen -= num
            if (owned) st.caught += num else st.caught -= num
            st.mons.removeAll { it.species == num && !owned }
            val d: AppDialog = DialogShots.capture { DexDetail.show(DexPage(ctx, NoHost), GameRepo.data!!.sp(num)) } ?: error("no dialog")
            DialogShots.scene(ctx, d)
        }
    }

    @Test fun rules() {
        rule("wishlist-dexdetail-seen", 26, seen = true, owned = false)
        rule("wishlist-dexdetail-unseen", 26, seen = false, owned = false)
        rule("wishlist-dexdetail-owned", 25, seen = true, owned = true)
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
