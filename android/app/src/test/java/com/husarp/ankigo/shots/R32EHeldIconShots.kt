package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R32EHeldIconShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    private fun assertIcon(ctx: Context, actual: Drawable?, resource: Int) {
        fun pixels(drawable: Drawable): Bitmap {
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            val copy = drawable.constantState!!.newDrawable(ctx.resources).mutate()
            copy.setTint(android.graphics.Color.WHITE)
            copy.setBounds(0, 0, 48, 48)
            copy.draw(Canvas(bitmap))
            return bitmap
        }
        assertNotNull(actual)
        assertTrue("Expected icon $resource", pixels(actual!!).sameAs(pixels(ctx.getDrawable(resource)!!)))
    }

    @Test fun heldAndBagIconsOnRealViews() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("e393", 393, 820).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            assertEquals(R.drawable.ic_held, ItemIcons.symbol(ItemSymbol.TAG))
            assertEquals(R.drawable.ic_held, DetailSections.markIcon(TagKind.HELD))
            val (page, stage) = ShopCardKit.page(ctx, null, bag = true)
            ShopCardKit.findDesc(stage, "Worn")!!.performClick(); stage.render()
            val held = all(stage).filterIsInstance<TextView>().single { it.text.toString() == "Worn" }
            assertTrue(held.isSelected)
            assertEquals(Ui.dp(ctx, 16), held.compoundDrawablesRelative[0]!!.bounds.width())
            assertIcon(ctx, held.compoundDrawablesRelative[0], R.drawable.ic_held)
            val scope = all(stage).single { it.tag == "bag-scope" }
            val allScope = all(scope).filterIsInstance<TextView>().single { it.text.toString() == "All" }
            assertIcon(ctx, allScope.compoundDrawablesRelative[0], R.drawable.ic_item_bag)
            paparazzi.snapshot(stage, "e-bag-held-${theme.name.lowercase()}")
            all(stage).single { it.contentDescription == "Expand categories" }.performClick(); stage.render()
            assertNotNull(ShopCardKit.findDesc(stage, "Worn category"))
            assertNull(ShopCardKit.findDesc(stage, "Held category"))
            paparazzi.snapshot(stage, "e-bag-categories-${theme.name.lowercase()}")
            page.onGone()
            val dialog = AppDialogShots.capture {
                MonDetail.show(TeamPage(ctx, NoHost), GameRepo.data!!, GameRepo.state!!.mon("t1")!!)
            }!!
            paparazzi.snapshot(AppDialogShots.scene(ctx, dialog), "e-detail-${theme.name.lowercase()}")
            dialog.dismiss()
            val itemPage = ShopPage(ctx, NoHost, bagOnly = true)
            val itemStage = ShopCardKit.Stage(ctx, itemPage, itemPage.root).render()
            itemPage.openCard(CardKeys.bagItem("lucky-egg")); itemStage.render()
            assertNotNull(itemPage.itemWindow)
            paparazzi.snapshot(itemStage, "e-item-${theme.name.lowercase()}")
            itemPage.closeCard(); itemPage.onGone()
            paparazzi.snapshot(ItemCard.factBlock(ctx, "How to use", "Give to a Pokémon", held = true), "e-use-${theme.name.lowercase()}")
        }
    }
}
