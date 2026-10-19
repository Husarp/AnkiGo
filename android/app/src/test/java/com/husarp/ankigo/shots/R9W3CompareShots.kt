package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.AppLook
import com.husarp.ankigo.ui.CompareFit
import com.husarp.ankigo.ui.CompareMode
import com.husarp.ankigo.ui.ComparePage
import com.husarp.ankigo.ui.ComparePick
import com.husarp.ankigo.ui.CompareStore
import com.husarp.ankigo.ui.DetailOpenStore
import com.husarp.ankigo.ui.DetailSections
import com.husarp.ankigo.ui.RowChoice
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.ui.TypeStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R9-W3-A: Compare "Best of all" with the D7-A two-sided scale, and the D8-V1 matchup boxes (Compare cells, wide and narrow, and the
 * details section). PNGs: r9w3-<what>-<size>-<theme>. The boxes are real buttons: a tap says what the group means.
 */
class R9W3CompareShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun reset() {
        val ctx = Shots.files(paparazzi.context)
        AppLook.setTypeStyle(ctx, TypeStyle.ICON_TEXT)
        CompareStore.saveFit(ctx, CompareFit.AUTO)
    }

    private val three = listOf(ComparePick.Owned("t1"), ComparePick.Owned("t2"), ComparePick.Dex(150))
    private val phone = listOf(Variants.PHONE)
    private val onlyMatchups = RowChoice(setOf("level", "nature", "ivs", "moves", "evo", "becomes", "hp", "atk", "def", "spa", "spd", "spe", "total",
        "base_hp", "base_atk", "base_def", "base_spa", "base_spd", "base_spe", "base_total"))
    private val onlyStats = RowChoice(setOf("level", "nature", "ivs", "moves", "evo", "becomes", "types", "weak", "resist", "immune",
        "base_hp", "base_atk", "base_def", "base_spa", "base_spd", "base_spe", "base_total"))

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out)
        return out
    }

    /** The matchup boxes: clickable vertical boxes whose description is a tap message. */
    private fun boxes(root: View) = all(root).filter { it is LinearLayout && it.isClickable && (it.contentDescription?.contains(" moves ") == true || it.contentDescription?.contains("own-type") == true) }

    private fun shoot(name: String, picks: List<ComparePick>, mode: CompareMode, rows: RowChoice, fit: Int, style: TypeStyle = TypeStyle.ICON_TEXT, check: (View) -> Unit = {}) {
        paparazzi.shootPage(name, phone) { ctx ->
            CompareStore.saveMode(ctx, mode); CompareStore.saveRows(ctx, rows)
            AppLook.setTypeStyle(ctx, style)
            val p = ComparePage(ctx, NoHost, picks)
            p.fit = fit
            val screen = pageScreen(ctx, p)
            p.render()
            check(p.root)
            screen
        }
    }

    @Test fun matchupsThree() = shoot("r9w3-cmp-matchups-3", three, CompareMode.VS_REFERENCE, onlyMatchups, CompareFit.AUTO) { root ->
        assertTrue("matchup boxes were built: ${boxes(root).size}", boxes(root).size >= 6)
    }

    @Test fun matchupsNarrow() = shoot("r9w3-cmp-matchups-narrow", three + ComparePick.Owned("t3"), CompareMode.BEST_OF_ALL, onlyMatchups, 6) { root ->
        assertTrue(boxes(root).size >= 8)
        // Compare: every box shows the pill only (one child in the header), whatever the width
        val heads = boxes(root).map { (it as LinearLayout).getChildAt(0) as LinearLayout }
        assertTrue("every header shows the pill only", heads.all { it.childCount == 1 })
    }

    @Test fun matchupsThreeAllPillOnly() = shoot("r9w3-cmp-matchups-3b", three, CompareMode.VS_REFERENCE, onlyMatchups, CompareFit.AUTO) { root ->
        assertTrue(boxes(root).all { ((it as LinearLayout).getChildAt(0) as LinearLayout).childCount == 1 })
    }

    @Test fun matchupsIcons() = shoot("r9w3-cmp-matchups-icon", three, CompareMode.VS_REFERENCE, onlyMatchups, CompareFit.AUTO, TypeStyle.ICON)

    @Test fun bestOfAllScale() = shoot("r9w3-cmp-scale", three + ComparePick.Owned("t3") + ComparePick.Dex(3), CompareMode.BEST_OF_ALL, onlyStats, CompareFit.AUTO)

    /** A tap on a group (or on an icon-only chip in it) shows what it means for the Pokémon. */
    @Test fun tapShowsTheMeaning() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        CompareStore.saveMode(ctx, CompareMode.VS_REFERENCE); CompareStore.saveRows(ctx, onlyMatchups)
        val p = ComparePage(ctx, NoHost, three)
        pageScreen(ctx, p); p.render()
        val dm = ctx.resources.displayMetrics
        p.root.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        p.root.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val b = boxes(p.root).first()
        Ui.lastToast = null
        b.performClick()
        assertEquals(b.contentDescription.toString(), Ui.lastToast)
        assertTrue(Ui.lastToast!!, Ui.lastToast!!.contains("hit ") || Ui.lastToast!!.contains("damage") || Ui.lastToast!!.contains("hurt"))
    }

    private fun details(name: String, types: List<Type>, style: TypeStyle) {
        paparazzi.shootPage(name, phone) { ctx ->
            AppLook.setTypeStyle(ctx, style)
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
                setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_card))
                addView(Ui.text(ctx, 14f, 900, com.husarp.ankigo.R.color.ag_text).apply { text = "Matchups" })
                addView(DetailSections.matchups(ctx, types, "Zorblax"))
            }
            assertTrue(boxes(col).isNotEmpty())
            col
        }
    }

    @Test fun detailsFireFlying() = details("r9w3-details-firefly", listOf(Type.FIRE, Type.FLYING), TypeStyle.ICON_TEXT)
    @Test fun detailsWater() = details("r9w3-details-water", listOf(Type.WATER), TypeStyle.TEXT)
    @Test fun detailsIcons() = details("r9w3-details-icon", listOf(Type.GHOST, Type.DARK), TypeStyle.ICON)

    /** 320 dp window, font scale 2.0: the label ellipsizes, the pill is never clipped (its right edge stays inside the box). */
    @Test fun detailsNarrowBigFontKeepThePill() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, Theme.LIGHT).copy(screenWidth = 640, fontScale = 2.0f))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val v = DetailSections.matchups(ctx, listOf(Type.FIRE, Type.FLYING), "Zorblax")
        val w = 320 * 2 - 2 * Ui.dp(ctx, 16)
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, w, v.measuredHeight)
        for (b in boxes(v)) {
            val head = (b as LinearLayout).getChildAt(0) as LinearLayout
            val pill = head.getChildAt(head.childCount - 1)
            assertTrue("pill fully inside: ${pill.right} <= ${head.width}", pill.right <= head.width && pill.width >= pill.measuredWidth)
            assertTrue("pill shown in full", pill.measuredWidth > 0 && pill.left >= 0)
        }
    }

    @Test fun detailsOrderAndStrongLast() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.DARK))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val v = DetailSections.matchups(ctx, listOf(Type.FIRE, Type.FLYING), "Zorblax")
        val msgs = boxes(v).map { it.contentDescription.toString() }
        assertEquals(6, msgs.size)
        assertTrue(msgs[0].startsWith("Rock moves hit Zorblax 4×"))
        assertTrue(msgs.last().startsWith("Zorblax's own-type moves hit"))
        val heads = boxes(v).map { ((it as LinearLayout).getChildAt(0) as LinearLayout).let { h -> (0 until h.childCount).map { i -> (h.getChildAt(i) as TextView).text.toString() } } }
        assertEquals(listOf("Very weak against", "×4"), heads[0])
        assertEquals(listOf("Strong against", "×2"), heads.last())
    }
}
