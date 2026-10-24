package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.CardKeys
import org.junit.Rule
import org.junit.Test

/*
 * R17C-FIX: the Chipped Pot is no longer sold or found, but a Bag that holds one keeps it; its card says it does nothing here.
 * PNG: r17cfixb-chipped-pot (light + dark), opened from the Bag next to a Cracked Pot (which still evolves a Pokémon).
 */
class R17CFixBShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val st get() = GameRepo.state!!

    @Test fun chippedPotCard() = paparazzi.shootPage("r17cfixb-chipped-pot", listOf(Variants.TALL)) { ctx ->
        ShopCardKit.install()
        st.bag["chipped-pot"] = 1; st.bag["cracked-pot"] = 1
        ShopCardKit.page(ctx, CardKeys.bagItem("chipped-pot"), bag = true, cash = 5000).second
    }
}
