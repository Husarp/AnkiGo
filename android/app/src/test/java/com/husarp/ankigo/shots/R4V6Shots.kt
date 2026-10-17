package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.ItemIconPlan
import com.husarp.ankigo.ui.ShopInfo
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/* R4-V6 (picks 7A + 7D): TM discs and kind badges. r4v6-shop, -bag, -tiles (every kind + every DRAWN id), -tm-info, -item-info. */
class R4V6Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private val sizes = listOf(Variants.PHONE, Variants.SPLIT)

    private fun state() {
        st.day = com.husarp.ankigo.game.Game(gd, st).dayKey(); st.shopDay = st.day // keep this stock: the page re-rolls when the day changes
        st.shopTms.clear(); st.shopTms += listOf("thunderbolt", "watergun", "earthquake", "shadowball", "bodyslam", "ember")
        st.tms.clear(); st.tms += listOf("ember", "vinewhip", "seismictoss", "dragonrage", "teleport", "thunderwave", "bodyslam")
        st.shopItems.clear(); st.shopItems += listOf("cracked-pot", "linking-cord", "sweet-apple")
        for (id in ItemIconPlan.DRAWN + listOf("potion", "everstone", "poke-doll", "helix-fossil", "poke-ball")) st.bag[id] = 2
    }

    @Test fun shop() = paparazzi.shootPage("r4v6-shop", sizes) { ctx -> state(); pageScreen(ctx, ShopPage(ctx, NoHost)) }

    @Test fun bag() = paparazzi.shootPage("r4v6-bag", sizes) { ctx ->
        state()
        val p = ShopPage(ctx, NoHost); setField(p, "showBag", true); pageScreen(ctx, p)
    }

    /** Every kind badge (no sprite on disk), the five drawn ids, then one TM disc per type in the data. */
    @Test fun tiles() = paparazzi.shootPage("r4v6-tiles", listOf(Variants.PHONE)) { ctx ->
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), 0, 0) }
        fun row(views: List<View>) = col.addView(LinearLayout(ctx).apply {
            for (v in views) addView(v, LinearLayout.LayoutParams(Ui.dp(ctx, 56), Ui.dp(ctx, 56)).also { it.marginEnd = Ui.dp(ctx, 8); it.bottomMargin = Ui.dp(ctx, 8) })
        })
        fun big(item: String) = ImageView(ctx).also { Ui.itemIcon(ctx, item, it) }
        // one id per kind that has no sprite in the fixture cache: the badge of every kind
        val noSprite = setOf("fluffy-tail", "dusk-ball", "full-restore", "dome-fossil", "scope-lens", "fire-stone")
        row(noSprite.map(::big))
        row(listOf("cracked-pot", "chipped-pot", "linking-cord", "sweet-apple", "tart-apple").map(::big))
        row(listOf("moon-stone", "everstone", "helix-fossil", "potion", "poke-doll", "poke-ball").map(::big))
        row(gd.moves.values.map { it.type }.distinct().take(6).map { t -> ImageView(ctx).also { Ui.tmIcon(ctx, t, it) } })
        row(gd.moves.values.map { it.type }.distinct().drop(6).take(6).map { t -> ImageView(ctx).also { Ui.tmIcon(ctx, t, it) } })
        row(listOf(ImageView(ctx).also { Ui.tmIcon(ctx, null, it) }))
        ScrollView(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_card)); addView(col) }
    }

    private fun dialog(name: String, open: (Context) -> Unit) = paparazzi.shootPage(name, listOf(Variants.PHONE)) { ctx ->
        state()
        DialogShots.scene(ctx, DialogShots.capture { open(ctx) } ?: error("no dialog was shown"))
    }

    @Test fun tmInfo() = dialog("r4v6-tm-info") { ctx -> ShopInfo.tm(ShopPage(ctx, NoHost), "thunderbolt") {} }
    @Test fun itemInfo() = dialog("r4v6-item-info") { ctx -> ShopInfo.item(ShopPage(ctx, NoHost), "cracked-pot") {} }
}
