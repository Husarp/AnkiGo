package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.EditText
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.DexFilter
import com.husarp.ankigo.ui.DexOwned
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.DexSort
import org.junit.Rule
import org.junit.Test

/** The Pokédex with its filters (T32): default, Owned + type, Not owned + gen, search, last-caught sort, nothing found. */
class DexShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val both = listOf(Size.PHONE, Size.SPLIT)

    private fun dex(ctx: Context, f: DexFilter = DexFilter()): View {
        val p = DexPage(ctx, NoHost)
        setField(p, "filter", f)
        val v = pageScreen(ctx, p)
        // the search box keeps its own text: type it like a user would (this also re-filters)
        if (f.query.isNotEmpty()) (getField(p, "search") as EditText).setText(f.query)
        return v
    }

    @Test fun dexFilters() {
        paparazzi.shootWith("dex-default", both) { dex(it) }
        paparazzi.shootWith("dex-owned-type", both) { dex(it, DexFilter(owned = DexOwned.OWNED, types = setOf(Type.ELECTRIC, Type.FIRE))) }
        paparazzi.shootWith("dex-notowned-gen", both) { dex(it, DexFilter(owned = DexOwned.NOT_OWNED, gens = setOf(1), wishlistOnly = false)) }
        paparazzi.shootWith("dex-search", both) { dex(it, DexFilter(query = "char")) }
        paparazzi.shootWith("dex-lastcaught", both) { dex(it, DexFilter(sort = DexSort.LAST_CAUGHT, owned = DexOwned.OWNED)) }
        paparazzi.shootWith("dex-empty-result", both) { dex(it, DexFilter(query = "zzzz")) }
    }
}
