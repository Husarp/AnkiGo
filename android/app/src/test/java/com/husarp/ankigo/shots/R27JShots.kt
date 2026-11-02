package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** PLAN 435: production stats card and battle HUD at 360 dp in both themes. */
class R27JShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val boosts = intArrayOf(1, -1, 0, 0, 0, 1, 0)
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun details() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("phone", 360, 800).device(theme))
            val ctx = Shots.files(paparazzi.context)
            val data = TestData.world()
            val mon = TestData.state(data, level = 21).main!!
            val section = DetailSections.section(ctx, DetailOpen(object : DetailOpenStore {
                override fun get(key: String): Boolean? = true
                override fun set(key: String, open: Boolean) {}
            }), DetailSection.STATS, null, titleLevel = 21, boosts = boosts) {
                DetailSections.statsBody(ctx, Stats(70, 50, 60, 45, 50, 55), 21, mon.nature,
                    data.sp(mon.species), true, boosts)
            }
            val views = all(section)
            assertEquals(3, views.filterIsInstance<StatPillView>().size)
            val texts = views.filterIsInstance<TextView>()
            assertTrue(texts.any { it.text.toString() == "50 → 75" })
            assertTrue(texts.any { it.text.toString() == "60 → 40" })
            assertTrue(texts.map { it.text.toString() }.containsAll(listOf("Atk", "Def", "Level 21")))
            val base = texts.single { it.text.toString() == "Base" }
            assertTrue(base.hasOnClickListeners())
            assertTrue(base.contentDescription.contains("species"))
            val explanation = AppDialogShots.capture { base.performClick() }!!
            assertTrue(all(explanation.content()).filterIsInstance<TextView>().any {
                it.text.contains("shared by all Pokémon of this species")
            })
            paparazzi.snapshot(AppDialogShots.scene(ctx, AppDialog.Builder(ctx).setTitle("Details")
                .setView(section, true).setDetailsFooter().create()), "j-details-${theme.name.lowercase()}")
        }
    }

    @Test fun battle() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("phone", 360, 800).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.install(ctx)
            boosts.copyInto(GameRepo.state!!.battle!!.player.boosts)
            val scene = BattleShotKit.bound(ctx)
            scene.arena!!.syncConditions(GameRepo.state!!.battle)
            paparazzi.snapshot(scene.root, "j-battle-${theme.name.lowercase()}", 1000)
            val metrics = ChipMetrics(ctx, 12f)
            val fm = metrics.word.fontMetrics
            assertEquals(metrics.height / 2f,
                metrics.baseline(0f) + (fm.ascent + fm.descent) / 2f, 0.01f)
            assertTrue(metrics.height / ctx.resources.displayMetrics.density < 28f)
        }
    }
}
