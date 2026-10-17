package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.AppLook
import com.husarp.ankigo.ui.ComparePage
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.DexFilter
import com.husarp.ankigo.ui.DexOwned
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TypeChips
import com.husarp.ankigo.ui.TypeStyle
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Rule
import org.junit.Test

/* R4-V4 (pick 5C): type chips = symbol + name in the type colour. r4v4-all-<style> (all 18), -battle, -dex, -compare, -detail. */
class TypeChipShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val FLOAT = Variant("float", 340, 480)

    @After fun reset() = AppLook.setTypeStyle(Shots.files(paparazzi.context), TypeStyle.ICON_TEXT)

    private fun style(ctx: Context, s: TypeStyle) = AppLook.setTypeStyle(ctx, s)

    /** All 18 chips, three to a row on an `ag_card` sheet, in one style. */
    private fun grid(ctx: Context): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16)) }
        Type.entries.chunked(3).forEach { row ->
            col.addView(LinearLayout(ctx).apply {
                for (t in row) addView(Ui.typeChip(ctx, t), LinearLayout.LayoutParams(0, -2, 1f).also { it.bottomMargin = Ui.dp(ctx, 8); it.marginEnd = Ui.dp(ctx, 8) })
            })
        }
        return ScrollView(ctx).apply {
            setBackgroundColor(Ui.c(ctx, R.color.ag_card))
            addView(col)
        }
    }

    @Test fun allChips() {
        for (s in TypeStyle.entries) paparazzi.shootPage("r4v4-all-${s.name.lowercase()}", listOf(Variants.PHONE, Variants.SPLIT)) { ctx -> style(ctx, s); grid(ctx) }
    }

    /** The 18 symbols at 56 dp in their ink colour (drawing check). */
    @Test fun bigSymbols() {
        paparazzi.shootPage("r4v4-symbols", listOf(Variants.PHONE), themes = Theme.entries) { ctx ->
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), 0, 0) }
            Type.entries.chunked(6).forEach { row ->
                col.addView(LinearLayout(ctx).apply {
                    for (t in row) addView(android.widget.ImageView(ctx).apply { setImageDrawable(TypeChips.tinted(ctx, t, TypeChips.ink(ctx, t))) },
                        LinearLayout.LayoutParams(Ui.dp(ctx, 56), Ui.dp(ctx, 56)).also { it.marginEnd = Ui.dp(ctx, 4); it.bottomMargin = Ui.dp(ctx, 8) })
                })
            }
            FrameLayout(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_card)); addView(col) }
        }
    }

    @Test fun battleCard() {
        for (s in TypeStyle.entries) paparazzi.shootWith("r4v4-battle-${s.name.lowercase()}") { ctx -> style(ctx, s); BattleShotKit.bound(ctx).root }
    }

    @Test fun dexFilterChips() {
        paparazzi.shootWith("r4v4-dex") { ctx ->
            style(ctx, TypeStyle.ICON_TEXT)
            val p = DexPage(ctx, NoHost)
            setField(p, "filter", DexFilter(owned = DexOwned.OWNED, types = setOf(Type.ELECTRIC, Type.FIRE)))
            pageScreen(ctx, p)
        }
    }

    @Test fun compare() {
        for (s in listOf(TypeStyle.ICON_TEXT, TypeStyle.ICON)) {
            paparazzi.shootPage("r4v4-compare-${s.name.lowercase()}", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
                style(ctx, s)
                pageScreen(ctx, ComparePage(ctx, NoHost, listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Dex(150))))
            }
        }
    }

    @Test fun details() {
        paparazzi.shootPage("r4v4-detail", listOf(Variants.PHONE, Variants.SPLIT, FLOAT)) { ctx ->
            style(ctx, TypeStyle.ICON_TEXT)
            val d = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), GameRepo.data!!, GameRepo.state!!.mon("t1")!!) } ?: error("no dialog")
            DialogShots.scene(ctx, d)
        }
    }
}
