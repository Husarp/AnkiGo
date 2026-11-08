package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R31JMatchupShots {
    @get:Rule val paparazzi = Shots.rule()
    private val originalStore = DetailSections.storeOf
    @After fun cleanup() { DetailSections.storeOf = originalStore; BattleShotKit.reset() }
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = views(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun openMatchups() {
        DetailSections.storeOf = { object : DetailOpenStore {
            override fun get(key: String) = key == "matchups"
            override fun set(key: String, open: Boolean) {}
        } }
    }
    private fun unknownWild() {
        val state = GameRepo.state!!
        state.mons.removeAll { it.species == 54 }; state.caught.remove(54); state.defeatedSpecies.remove(54)
    }
    private fun wild(ctx: Context): View = DialogShots.scene(ctx, DialogShots.capture { assertTrue(BattleDetails.showWild(ctx)) }!!)
    private fun reveal(player: Boolean, type: Type, eff: Double = 1.0) {
        val state = GameRepo.state!!
        MatchupKnowledge.observe(GameRepo.data!!, state, state.battle!!, state.main!!, listOf(GameEvent.Attack(
            player, "User", "Target", "Move", type, Category.PHYSICAL, if (eff == 0.0) 0 else 5, 1, eff,
            false, false, false, 10, 20, 10, 20,
        )))
    }
    private fun checkTiles(v: View, count: Int) {
        assertEquals(count, texts(v).count { it == "???" })
        val tiles = views(v).filter { it.contentDescription == "Matchup not yet seen in battle" }
        assertEquals(count, tiles.size)
        assertTrue(tiles.all { !it.isClickable })
    }

    @Test fun knownWildStillHidesMatchupsBeforeAnAttack() = paparazzi.shoot("j-wild-before-attack", listOf(Size.PHONE)) { ctx ->
        openMatchups()
        GameRepo.state!!.seenMatchups[54] = mutableSetOf(Type.GRASS)
        wild(ctx).also { assertFalse(texts(it).any { s -> s.equals("Matchups", true) }); assertFalse(texts(it).any { s -> s.contains("weak to") }) }
    }

    @Test fun firstMissShowsEighteenDarkTilesWithoutStatsOrWeaknessSummary() = paparazzi.shoot("j-wild-first-miss", listOf(Size.PHONE)) { ctx ->
        openMatchups(); unknownWild()
        GameRepo.state!!.battle!!.playerAttacked = true
        wild(ctx).also {
            checkTiles(it, 18)
            assertTrue(texts(it).contains("0/18 types seen"))
            assertFalse(texts(it).any { s -> s.startsWith("Stats", true) || s.contains("weak to") || s == "Strong against" })
        }
    }

    @Test fun wildShowsOnlySeenWeaknessAndNormalHit() = paparazzi.shoot("j-wild-discovered", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
        openMatchups(); unknownWild(); reveal(true, Type.GRASS, 2.0); reveal(true, Type.NORMAL)
        wild(ctx).also {
            checkTiles(it, 16)
            assertTrue(texts(it).contains("weak to Grass ×2"))
            assertTrue(texts(it).contains("Normal ×1"))
            assertFalse(texts(it).contains("Electric"))
            assertFalse(texts(it).contains("Strong against"))
        }
    }

    @Test fun collapsedSummaryDoesNotNameAnUnseenWeakness() = paparazzi.shoot("j-wild-summary", listOf(Size.PHONE)) { ctx ->
        DetailSections.storeOf = { object : DetailOpenStore {
            override fun get(key: String) = false
            override fun set(key: String, open: Boolean) {}
        } }
        unknownWild(); reveal(true, Type.NORMAL)
        wild(ctx).also {
            assertTrue(texts(it).contains("1/18 types seen"))
            assertFalse(texts(it).any { s -> s.contains("weak to") || s.contains("Electric") })
            checkTiles(it, 0) // the lazy body has not opened
        }
    }

    @Test fun ownedDetailsRememberIncomingHitWithoutOtherWeaknesses() = paparazzi.shoot("j-owned-discovered", listOf(Size.PHONE)) { ctx ->
        openMatchups(); reveal(false, Type.WATER)
        val mon = GameRepo.state!!.main!!
        GameRepo.state!!.viewedMons += mon.uid
        DialogShots.scene(ctx, DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), GameRepo.data!!, mon) }!!).also {
            checkTiles(it, 17)
            assertTrue(texts(it).contains("Water ×1"))
            assertFalse(texts(it).any { s -> s.contains("weak to Ground") })
        }
    }

    @Test fun catchingKeepsEveryUnseenPokedexEntryHidden() = paparazzi.shoot("j-dex-caught-empty", listOf(Size.PHONE)) { ctx ->
        openMatchups(); GameRepo.state!!.battle = null
        DialogShots.scene(ctx, DialogShots.capture { DexDetail.show(DexPage(ctx, NoHost), GameRepo.data!!.sp(54)) }!!).also { checkTiles(it, 18) }
    }

    @Test fun pokedexRemembersRevealedImmunityAndNormalType() = paparazzi.shoot("j-dex-discovered", listOf(Size.PHONE)) { ctx ->
        openMatchups()
        GameRepo.state!!.seenMatchups[92] = mutableSetOf(Type.NORMAL, Type.FIRE)
        DialogShots.scene(ctx, DialogShots.capture { DexDetail.show(DexPage(ctx, NoHost), GameRepo.data!!.sp(92)) }!!).also {
            checkTiles(it, 16)
            assertTrue(texts(it).contains("Normal"))
            assertTrue(texts(it).contains("Fire ×1"))
            assertTrue(texts(it).contains("Immune to"))
            assertFalse(texts(it).any { s -> s.contains("weak to Ground") })
        }
    }

    @Test fun compareShowsOnlyObservedEntriesAndDarkTiles() = paparazzi.shoot("j-compare-discovered", listOf(Size.PHONE)) { ctx ->
        reveal(true, Type.GRASS, 2.0); reveal(true, Type.NORMAL); reveal(false, Type.WATER)
        val state = GameRepo.state!!
        val picks = listOf(ComparePick.Owned("t1"), ComparePick.Dex(54))
        val table = CompareTable.build(GameRepo.data!!, state, picks, false)
        CompareStore.saveRows(ctx, RowChoice(table.rows.map { it.key }.filter { it !in setOf("types", "weak", "resist", "immune", "normal", "unseen_matchups") }.toSet()))
        val page = ComparePage(ctx, NoHost, picks)
        pageScreen(ctx, page).also {
            page.render()
            checkTiles(it, 33)
            assertFalse(texts(it).contains("Ground"))
        }
    }
}
