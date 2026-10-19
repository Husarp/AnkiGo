package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.AppLook
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.MatchupBoxes
import com.husarp.ankigo.ui.MatchupChips
import com.husarp.ankigo.ui.MatchupGroup
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import com.husarp.ankigo.ui.SettingsCatalog
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R10-W2-B (W1): Details → Matchups chips. Soft (default, D1-A) = no ring; Type colour (D1-C) = a 1.5 dp ring in the type's own colour.
 * Shots: r10w2matchup-<soft|type>-<phone>-<light|dark>, r10w2matchup-settings-*.
 */
class R10W2MatchupShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun chips(v: View, out: MutableList<View> = ArrayList()): List<View> {
        if (v is android.widget.TextView && v.background is GradientDrawable && v.text.isNotEmpty() && Type.entries.any { it.label == v.text.toString() }) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) chips(v.getChildAt(i), out)
        return out
    }

    private fun matchups(ctx: Context, look: MatchupChips): View {
        AppLook.setMatchupChips(ctx, look)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_card))
            setPadding(Ui.dp(ctx, 16f).toInt(), Ui.dp(ctx, 16f).toInt(), Ui.dp(ctx, 16f).toInt(), Ui.dp(ctx, 16f).toInt())
        }
        val types = Type.entries
        for ((i, g) in MatchupGroup.entries.withIndex()) {
            val list = types.drop(i * 3).take(if (i == 0) 1 else 4)
            col.addView(MatchupBoxes.box(ctx, g, list, "Sparkmouse", compact = false),
                LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 8f).toInt() })
        }
        return col
    }

    @Test fun softHasNoRing() {
        try {
            paparazzi.shoot("r10w2matchup-soft", listOf(Size.PHONE)) { ctx ->
                val v = matchups(ctx, MatchupChips.SOFT)
                val cs = chips(v)
                assertTrue(cs.size >= 10)
                v
            }
        } finally { AppLook.setMatchupChips(Shots.files(paparazzi.context), MatchupChips.SOFT) }
    }

    @Test fun typeColourRing() {
        try {
            paparazzi.shoot("r10w2matchup-type", listOf(Size.PHONE)) { ctx -> matchups(ctx, MatchupChips.TYPE_COLOUR) }
        } finally { AppLook.setMatchupChips(Shots.files(paparazzi.context), MatchupChips.SOFT) }
    }

    @Test fun prefDefaultsToSoft() {
        val ctx = Shots.files(paparazzi.context)
        assertEquals(MatchupChips.SOFT, AppPrefs.matchupChips(ctx))
        AppLook.setMatchupChips(ctx, MatchupChips.TYPE_COLOUR)
        assertEquals(MatchupChips.TYPE_COLOUR, AppPrefs.matchupChips(ctx))
        AppLook.setMatchupChips(ctx, MatchupChips.SOFT)
        assertEquals(MatchupChips.SOFT, AppPrefs.matchupChips(ctx))
    }

    @Test fun catalogFindsIt() {
        val e = SettingsCatalog.all.firstOrNull { it.id == "matchupChips" }
        assertNotNull(e)
        assertTrue(e!!.explain.length <= 90 && !e.explain.endsWith("."))
    }

    @Test fun settingsLookCard() {
        paparazzi.shootPage("r10w2matchup-settings", listOf(Variants.PHONE)) { ctx ->
            val c = ShotCtx(ctx)
            val p = SettingsPage(c, NoHost)
            val v = pageScreen(c, p)
            p.open(SettingsSection.LOOK)
            v
        }
    }
}
