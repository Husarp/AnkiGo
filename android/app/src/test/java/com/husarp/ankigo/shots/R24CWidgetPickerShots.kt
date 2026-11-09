package com.husarp.ankigo.shots

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R24CWidgetPickerShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun pokemonPickerUsesAppDialogWithPinnedControlsAndScrollableList() = paparazzi.shootWith(
        "r24c-widget-picker", listOf(Size.PHONE, Size.SMALL, Size.SPLIT)
    ) { ctx ->
        val activity = attach(WidgetLookActivity(), ShotCtx(ctx))
        activity.buildContent()
        val card = activity.showPokemonSheet(show=false)
        val background = card.background as GradientDrawable
        assertEquals(Ui.c(ctx, R.color.ag_card), background.color!!.defaultColor)
        assertEquals(Ui.dp(ctx, 20).toFloat(), background.cornerRadius)
        fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
            (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
        val views = descendants(card)
        assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == "Pokémon" })
        val cancel = views.filterIsInstance<TextView>().single { it.text.toString() == "Cancel" }
        val list = views.filterIsInstance<ListView>().single()
        assertTrue(list.adapter.count > 0)
        var parent = list.parent
        while (parent is View) {
            assertFalse("The Pokémon list must scroll independently", parent is ScrollView)
            parent = parent.parent
        }
        list.viewTreeObserver.addOnGlobalLayoutListener {
            assertTrue("List has at least one row below the filters: ${list.height}", list.height >= Ui.dp(ctx, 64))
            assertTrue("Cancel remains visible", cancel.bottom > 0)
        }
        android.widget.FrameLayout(ctx).apply {
            setBackgroundColor(android.graphics.Color.argb(122, 0, 0, 0))
            addView(card, android.widget.FrameLayout.LayoutParams(
                Ui.dp(ctx, AppDialogRules.widthDp(ctx.resources.configuration.screenWidthDp)),
                ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER
            ).also { it.topMargin=Ui.dp(ctx,24); it.bottomMargin=Ui.dp(ctx,24) })
        }
    }
}
