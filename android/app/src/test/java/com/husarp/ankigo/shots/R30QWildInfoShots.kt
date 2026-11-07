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

class R30QWildInfoShots {
    @get:Rule val paparazzi = Shots.rule()
    @After fun cleanup() = BattleShotKit.reset()
    private fun texts(v: View): List<String> = (if (v is TextView) listOf(v.text.toString()) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()
    private fun unknown() {
        val st = GameRepo.state!!
        st.caught.remove(54)
        st.mons.removeAll { it.species == 54 }
        st.defeatedSpecies.remove(54)
    }
    private fun used() {
        GameRepo.wildMoves.observe(listOf(GameEvent.Attack(false, "Duckling", "Me", "Water Gun", Type.WATER, Category.SPECIAL,
            5, 1, 1.0, false, false, false, 10, 20, 10, 20)), GameRepo.state!!.battle)
    }
    private fun wild(ctx: Context): View {
        val dlg = DialogShots.capture { assertTrue(BattleDetails.showWild(ctx)) }!!
        return DialogShots.scene(ctx, dlg)
    }
    private fun locked(scene: View) {
        val text = texts(scene)
        for (label in listOf("Stats", "Base stats", "Matchups", "Bond", "Info", "Evolution", "?"))
            assertFalse("Hidden $label in $text", text.any { it.equals(label, true) || it.startsWith("$label ·", true) })
    }

    @Test fun unknownHasNoEmptySections() = paparazzi.shoot("q-wild-unknown", listOf(Size.PHONE)) { ctx ->
        unknown()
        wild(ctx).also { scene -> locked(scene); assertFalse(texts(scene).any { it.equals("Moves", true) }) }
    }

    @Test fun unknownShowsOnlyObservedMoves() = paparazzi.shoot("q-wild-used", listOf(Size.PHONE, Size.SPLIT)) { ctx ->
        unknown(); used()
        wild(ctx).also { scene ->
            locked(scene)
            assertTrue(texts(scene).contains("Water Gun"))
            assertFalse(texts(scene).contains("Tackle"))
        }
    }

    @Test fun defeatAndCatchRevealDetailsWithoutBond() {
        for (caught in listOf(false, true)) paparazzi.shoot("q-wild-${if (caught) "caught" else "defeated"}", listOf(Size.PHONE)) { ctx ->
            unknown()
            if (caught) GameRepo.state!!.caught += 54 else GameRepo.state!!.defeatedSpecies += 54
            wild(ctx).also { scene ->
                val text = texts(scene)
                assertTrue(text.any { it.startsWith("Stats", true) })
                assertTrue(text.any { it.equals("Matchups", true) })
                assertTrue(text.any { it.equals("Info", true) })
                assertTrue(text.contains("Water Gun"))
                assertTrue(text.contains("Tackle"))
                assertFalse(text.any { it.equals("Bond", true) })
            }
        }
    }

    @Test fun pokedexUnknownSeenAndUnseenAreLocked() {
        for (seen in listOf(false, true)) paparazzi.shoot("q-dex-${if (seen) "seen" else "unseen"}", listOf(Size.PHONE)) { ctx ->
            unknown()
            val st = GameRepo.state!!
            st.seen.remove(54)
            if (seen) { st.seen += 54; used() } else st.battle = null
            val dlg = DialogShots.capture { DexDetail.show(DexPage(ctx, NoHost), GameRepo.data!!.sp(54)) }!!
            DialogShots.scene(ctx, dlg).also { scene ->
                val text = texts(scene)
                for (label in listOf("Base stats", "Matchups", "Info", "Bond", "Evolution")) assertFalse(text.any { it.equals(label, true) })
                assertEquals(seen, text.contains("Water Gun"))
                assertFalse(text.contains("Tackle"))
                assertEquals(seen, text.contains("Duckling"))
            }
        }
    }

    @Test fun knownPokedexAndCompare() = paparazzi.shoot("q-dex-known", listOf(Size.PHONE)) { ctx ->
        unknown()
        GameRepo.state!!.defeatedSpecies += 54
        val dlg = DialogShots.capture { DexDetail.show(DexPage(ctx, NoHost), GameRepo.data!!.sp(54)) }!!
        DialogShots.scene(ctx, dlg).also { scene ->
            assertTrue(texts(scene).any { it.equals("Base stats", true) })
            assertTrue(texts(scene).any { it.equals("Matchups", true) })
            assertTrue(texts(scene).any { it.equals("Info", true) })
        }
    }

    @Test fun compareHidesUnknownSpeciesStats() = paparazzi.shoot("q-compare-mixed", listOf(Size.PHONE)) { ctx ->
        unknown(); used()
        val page = ComparePage(ctx, NoHost, listOf(ComparePick.Owned("t1"), ComparePick.Dex(54)))
        pageScreen(ctx, page)
    }

    @Test fun catchCardFollowsDefeatUnlock() = paparazzi.shoot("q-catch-card-known", listOf(Size.PHONE)) { ctx ->
        val st = GameRepo.state!!
        val wild = st.battle!!.wild
        val ask = PendingAsk(PendingAsk.Kind.CATCH, wild.uid, wild, st.battle!!.tier)
        val model = CatchAskPlan.of(GameRepo.data!!, st, ask)!!
        assertTrue(SpeciesKnowledge.known(GameRepo.data!!, st, wild.species))
        CatchAskPopup.content(ctx, GameRepo.data!!, model) {}
    }
}
