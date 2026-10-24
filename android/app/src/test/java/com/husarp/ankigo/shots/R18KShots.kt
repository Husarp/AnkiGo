package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.MatchupBoxes
import com.husarp.ankigo.ui.MatchupGroup
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.update.UpdateOverlay
import com.husarp.ankigo.update.WhatsNew
import com.husarp.ankigo.update.updatesScreen
import com.husarp.ankigo.update.Updates
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * R18-K: the matchup boxes with icons (176), the "installed" card at the top (201a) and What's new on Settings → Updates (201b).
 * PNGs: r18k-matchups-*, r18k-card-*, r18k-updates-*.
 */
class R18KShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val notes get() = WhatsNew.parse(File("src/main/res/raw/whats_new.txt").readText())

    @Test fun matchups() = paparazzi.shoot("r18k-matchups", listOf(Size.PHONE)) { ctx ->
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, R.color.ag_card))
            val p = Ui.dp(ctx, 16)
            setPadding(p, p, p, p)
        }
        // Charizard (Fire / Flying) fills all six groups
        val chz = mapOf(
            MatchupGroup.VERY_WEAK to listOf(Type.ROCK),
            MatchupGroup.WEAK to listOf(Type.WATER, Type.ELECTRIC),
            MatchupGroup.RESISTS to listOf(Type.FIRE, Type.FIGHTING, Type.STEEL, Type.FAIRY),
            MatchupGroup.STRONGLY_RESISTS to listOf(Type.GRASS, Type.BUG),
            MatchupGroup.IMMUNE to listOf(Type.GROUND),
            MatchupGroup.STRONG_AGAINST to listOf(Type.GRASS, Type.ICE, Type.FIGHTING, Type.BUG, Type.STEEL),
        )
        for ((g, ts) in chz) col.addView(MatchupBoxes.box(ctx, g, ts, "Charizard", compact = false),
            LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 8) })
        col
    }

    private fun overlay(ctx: Context): UpdateOverlay {
        val ss = ShotCtx(ctx)
        val content = LinearLayout(ss).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(ss, R.color.ag_base)) }
        return UpdateOverlay(ss, content).also { it.banner.animated = false; it.whatsNew.showFor("0.24.0") }
    }

    @Test fun card() = paparazzi.shoot("r18k-card", listOf(Size.SPLIT)) { overlay(it) }

    /** The card above a waiting update banner. */
    @Test fun cardAndBanner() {
        BattleShotKit.setUpdates(BattleShotKit.newer())
        paparazzi.shoot("r18k-card-banner", listOf(Size.SPLIT)) { overlay(it) }
    }

    private fun updates(name: String, s: Updates.State) {
        BattleShotKit.setUpdates(s)
        paparazzi.shoot(name, listOf(Size.PHONE)) { ctx -> updatesScreen(ShotCtx(ctx), "0.23.0", notes) }
    }

    @Test fun updatesScreen() {
        updates("r18k-updates", Updates.State(message = "AnkiGo is up to date."))
        updates("r18k-updates-available", Updates.State(latest = BattleShotKit.newer().latest))
    }
}
