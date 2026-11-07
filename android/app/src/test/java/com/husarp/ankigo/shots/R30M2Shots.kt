package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

class R30M2Shots {
    @get:Rule val paparazzi = Shots.rule()
    private fun texts(v: View): List<String> = (if (v is TextView) listOf(v.text.toString()) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()

    @Test fun pickedNaturePerksAppearInTheMergedComparison() = paparazzi.shoot("m2-468-natures", listOf(Size.PHONE)) { ctx ->
        val state = GameRepo.state!!
        for ((i, nature) in listOf(Nature.HARDY, Nature.DOCILE).withIndex()) {
            val m = state.mons[i]
            state.mons[i] = Mon(m.uid, m.species, m.name, m.level, m.xp, m.ivs, m.evs, nature,
                m.gender, m.shiny, m.ability, m.moves, m.hp)
        }
        val picks = state.mons.take(2).map { ComparePick.Owned(it.uid) }
        val rows = CompareTable.build(GameRepo.data!!, state, picks, false).rows.map { it.key }
        CompareStore.saveRows(ctx, RowChoice(rows.filter { it != "nature" }.toSet()))
        val page = ComparePage(ctx, NoHost, picks)
        pageScreen(ctx, page).also { view ->
            assertTrue(texts(view).any { it.contains("+3% reward XP") })
            assertTrue(texts(view).any { it.contains("+4% Bond") })
        }
    }

    @Test fun typesDetailHasExactlyTheTwoPickedTiers() = paparazzi.shoot("m2-500-types", listOf(Size.PHONE)) { ctx ->
        val state = GameRepo.state!!
        val data = GameRepo.data!!
        state.achTiers["types"] = mutableListOf(1_794_000_000_000L)
        val view = Achievements.view(state, data).single { it.id == "types" }
        assertEquals(listOf(9L, 18L), view.tiers)
        AchievementDetail.layer(ctx, view, state.achSince, ZoneId.of("UTC")) {}.also { layer ->
            assertTrue(texts(layer).any { it.contains("Common") })
            assertTrue(texts(layer).any { it.contains("Rare") })
            assertFalse(texts(layer).any { it.contains("Epic") })
        }
    }

    @Test fun ownedSpeciesKeepsBondWithCurrentPerksAfterKnowledgeMerge() = paparazzi.shoot("m2-467-490-owned-bond", listOf(Size.PHONE)) { ctx ->
        val state = GameRepo.state!!
        val mon = state.main!!
        mon.bondPoints = Bond.thresholds[3]
        for (section in DetailSection.entries) AppPrefs.setDetailOpen(ctx, section.key, section == DetailSection.BOND)
        val dlg = DialogShots.capture { DexDetail.show(DexPage(ctx, NoHost), GameRepo.data!!.sp(mon.species)) }!!
        DialogShots.scene(ctx, dlg).also { scene ->
            assertTrue(texts(scene).any { it.equals("Bond", true) })
            assertTrue(texts(scene).any { it.equals("Current perks", true) })
            assertFalse(texts(scene).any { it.contains("Next ·") })
        }
    }
}
