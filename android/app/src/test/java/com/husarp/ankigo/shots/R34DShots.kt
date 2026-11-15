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

class R34DShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun offerMarksInTeamAndBoxAndYesSkipPrompt() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val data = GameRepo.data!!; val state = GameRepo.state!!; val mon = state.main!!
            mon.moves.clear(); mon.moves += listOf("growl", "tackle", "vinewhip", "lick")
            mon.discoveredMoves += mon.moves
            val before = mon.moves.toList()
            assertNull(DialogShots.capture {
                Progression.learnMove(data, mon, "bodyslam", mutableListOf())
                Progression.learnMove(data, mon, "bulletseed", mutableListOf())
            })
            assertEquals(before, mon.moves)
            for (section in listOf(TeamSection.TEAM, TeamSection.BOX)) {
                val page = TeamPage(ctx, NoHost, section)
                val scene = pageScreen(ctx, page)
                paparazzi.snapshot(scene, "d-offer-mark-${section.name.lowercase()}-${theme.name.lowercase()}")
                assertTrue(all(scene).any { it.contentDescription == "Move offer" })
                page.onHide(); page.onGone()
            }
            fun prompt() = DialogShots.capture {
                MoveOfferPrompt.show(ctx, data, mon) { move, accept, old -> Progression.answerMoveOffer(data, mon, move, accept, old) }
            }!!
            val closed = prompt(); closed.dismiss()
            assertEquals(2, mon.moveOffers.size)
            val dialog = prompt()
            val labels = all(dialog.content()).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue("Learn Body Slam?" in labels)
            assertTrue(labels.any { "It replaces Growl." in it && "1 more waiting" in it })
            assertEquals("Yes", dialog.button(AppDialog.BUTTON_POSITIVE)!!.text.toString())
            assertEquals("Skip", dialog.button(AppDialog.BUTTON_NEGATIVE)!!.text.toString())
            paparazzi.snapshot(DialogShots.scene(ctx, dialog), "d-offer-prompt-${theme.name.lowercase()}")
            dialog.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
            assertEquals(listOf("bodyslam", "tackle", "vinewhip", "lick"), mon.moves)
            val second = prompt()
            assertTrue(all(second.content()).filterIsInstance<TextView>().any { "It replaces Lick." in it.text.toString() })
            second.button(AppDialog.BUTTON_NEGATIVE)!!.performClick()
            assertTrue(mon.moveOffers.isEmpty())
            assertFalse(TileMark.MOVE_OFFER in TileMarks.of(state, listOf(mon)))
        }
    }

    @Test fun answeringFromDetailsRefreshesItsMovesAndMark() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val data = GameRepo.data!!; val mon = GameRepo.state!!.main!!
        mon.moves.clear(); mon.moves += listOf("growl", "tackle", "vinewhip", "lick")
        Progression.learnMove(data, mon, "bodyslam", mutableListOf())
        Progression.learnMove(data, mon, "bulletseed", mutableListOf())
        val details = DialogShots.capture { MonDetail.show(ctx, data, mon) }!!
        val offer = DialogShots.capture {
            all(details.content()).single { it.contentDescription == "Move offer: 2 waiting" }.performClick()
        }!!
        // Pokémon details reuse their window; answering refreshes that card rather than opening another one.
        assertNull(DialogShots.capture { offer.button(AppDialog.BUTTON_POSITIVE)!!.performClick() })
        assertEquals("bodyslam", mon.moves[0])
        val next = DialogShots.capture {
            all(details.content()).single { it.contentDescription == "Move offer: 1 waiting" }.performClick()
        }!!
        assertNull(DialogShots.capture { next.button(AppDialog.BUTTON_NEGATIVE)!!.performClick() })
        assertTrue(mon.moveOffers.isEmpty())
        assertFalse(all(details.content()).any { it.contentDescription?.startsWith("Move offer:") == true })
        details.dismiss()
    }

    @Test fun detailsContainsAnExplicitOfferButton() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context); FakeGame.install(ctx)
        val mon = GameRepo.state!!.main!!
        mon.moveOffers += MoveOffer("bodyslam", mon.level)
        val details = DialogShots.capture { MonDetail.show(ctx, GameRepo.data!!, mon) }!!
        val mark = all(details.content()).single { it.contentDescription == "Move offer: 1 waiting" }
        val offer = DialogShots.capture { mark.performClick() }!!
        assertTrue(all(offer.content()).filterIsInstance<TextView>().any { it.text.toString() == "Learn Body Slam?" })
        offer.dismiss(); details.dismiss()
        assertEquals(1, mon.moveOffers.size)
    }
}
