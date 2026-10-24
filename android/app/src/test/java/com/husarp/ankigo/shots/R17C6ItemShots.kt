package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.CardKeys
import com.husarp.ankigo.ui.ShopPage
import org.junit.Rule
import org.junit.Test

/*
 * R17C-6: the item cards of Black Augurite (a new evolution item with no sprite at the pinned PokeAPI commit: the kind badge shows)
 * and Full Restore (heals all HP and cures a status), and a Bag with both. PNGs: r17c6-black-augurite / -full-restore / -bag (light + dark).
 */
class R17C6ItemShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val st get() = GameRepo.state!!

    /** The Shop kit's world plus Scyther -> Kleavor by Black Augurite and the price of Full Restore. */
    private fun install() {
        val base = ShopCardKit.world()
        val sp = mapOf(
            1100 to TestData.species(1100, "Scyther", evolutions = listOf(Evolution(1101, EvoKind.ITEM, item = "black-augurite"))),
            1101 to TestData.species(1101, "Kleavor", prevo = 1100, evoType = "useItem"),
        )
        val d = GameData(base.species + sp, base.moves, base.growth, base.itemPrices + ("full-restore" to 3000))
        val f = GameRepo::class.java.getDeclaredField("loadedData"); f.isAccessible = true; f.set(GameRepo, d)
        val rebuild = GameRepo::class.java.getDeclaredMethod("rebuild"); rebuild.isAccessible = true; rebuild.invoke(GameRepo)
    }

    private fun bag() {
        st.bag["black-augurite"] = 1; st.bag["full-restore"] = 2; st.bag["max-potion"] = 1; st.bag["peat-block"] = 1
        st.bag["galarica-cuff"] = 1; st.bag["syrupy-apple"] = 1
    }

    private fun card(name: String, key: String) = paparazzi.shootPage(name, listOf(Variants.TALL)) { ctx ->
        install(); bag()
        ShopCardKit.page(ctx, key, bag = true, cash = 5000).second
    }

    @Test fun blackAugurite() = card("r17c6-black-augurite", CardKeys.bagItem("black-augurite"))
    @Test fun fullRestore() = card("r17c6-full-restore", CardKeys.bagItem("full-restore"))

    @Test fun bagWithTheNewItems() = paparazzi.shootPage("r17c6-bag", listOf(Variants.PHONE)) { ctx ->
        install(); bag()
        val p = ShopPage(ctx, NoHost)
        setField(p, "showBag", true)
        pageScreen(ctx, p)
    }
}
