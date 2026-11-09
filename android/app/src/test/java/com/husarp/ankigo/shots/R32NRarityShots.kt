package com.husarp.ankigo.shots

import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R32NRarityShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun texts(v: View): List<TextView> = (if (v is TextView) listOf(v) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()
    private fun fact(v: View, label: String): TextView = texts(v).single { it.text.toString() == "\uFFFC $label" }

    @Test fun ownedDetailsShowEverySpecialRarityWithMarkAndNoOrdinaryFact() {
        for ((tier, label) in listOf(Tier.BABY to "Baby", Tier.ULTRA to "Ultra Beast", Tier.LEGENDARY to "Legendary",
            Tier.MYTHICAL to "Mythical", Tier.STARTER to "Starter", Tier.NORMAL to "Strong", Tier.NORMAL to "")) {
            paparazzi.shoot("n-563-owned-${label.ifEmpty { "plain" }.replace(" ", "-").lowercase()}", listOf(Size.PHONE, Size.FLOAT)) { ctx ->
                val mon = GameRepo.state!!.mons.first().apply {
                    species = when (label) {
                        "Strong" -> 130
                        "Starter" -> 1
                        "Baby" -> 172
                        else -> 25
                    }
                    this.tier = tier
                }
                RarityMark.setEnabled(ctx, false)
                val dlg = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), GameRepo.data!!, mon) }!!
                val body = dlg.content()
                if (label.isNotEmpty()) {
                    val text = fact(body, label).text as Spanned
                    assertEquals(1, text.getSpans(0, text.length, RarityMark.Mark::class.java).size)
                } else {
                    assertFalse(texts(body).any { it.text is Spanned && (it.text as Spanned).getSpans(0, it.text.length, RarityMark.Mark::class.java).isNotEmpty() })
                }
                DialogShots.scene(ctx, dlg)
            }
        }
    }

    @Test fun dexAndWildRarityFollowKnowledgeForSeenAndUnseenSpecies() {
        for ((num, label) in listOf(1 to "Starter", 172 to "Baby", 130 to "Strong", 150 to "Legendary"))
        for (known in listOf(false, true)) for (seen in listOf(false, true)) {
            paparazzi.shoot("n-563-knowledge-$num-$known-$seen", listOf(Size.PHONE)) { ctx ->
                val st = GameRepo.state!!
                val data = GameRepo.data!!
                val sp = data.sp(num)
                st.mons.removeAll { it.species == sp.num }
                st.caught.remove(sp.num); st.defeatedSpecies.remove(sp.num); st.seen.remove(sp.num)
                if (seen) st.seen += sp.num
                if (known) st.defeatedSpecies += sp.num
                val dex = DialogShots.capture { DexDetail.show(DexPage(ctx, NoHost), sp) }!!
                assertEquals(known, texts(dex.content()).any { it.text.toString() == "\uFFFC $label" })
                paparazzi.snapshot(DialogShots.scene(ctx, dex), "n-563-dex-$num-$known-$seen-${ctx.resources.configuration.uiMode}")
                dex.dismiss()
                val battle = st.battle!!
                battle.wild.species = sp.num; battle.tier = Encounters.tierOf(sp) ?: Tier.NORMAL
                val wild = DialogShots.capture { assertTrue(BattleDetails.showWild(ctx)) }!!
                assertEquals(known, texts(wild.content()).any { it.text.toString() == "\uFFFC $label" })
                DialogShots.scene(ctx, wild)
            }
        }
    }

    @Test fun savedCommonBoxSelectionMigratesAndKeepsOtherSelections() {
        val ctx = Shots.files(paparazzi.context)
        val p = ctx.getSharedPreferences("ankigo_ui", 0)
        p.edit().putString("rarity.box", "COMMON").apply()
        assertEquals(setOf(PokemonRarity.GREAT), SortPrefs.loadBoxRarity(ctx).picked)
        p.edit().putString("rarity.box", "COMMON,LEGENDARY").apply()
        assertEquals(setOf(PokemonRarity.GREAT, PokemonRarity.LEGENDARY), SortPrefs.loadBoxRarity(ctx).picked)
    }
}
