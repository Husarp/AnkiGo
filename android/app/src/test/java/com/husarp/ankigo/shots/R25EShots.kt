package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R25EShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun closeAll(d: AppDialog) = views(d.content()).filterIsInstance<TextView>().single { it.text.toString() == "Close all" }

    @Test fun stackKeepsSingleCloseAndOffersCloseAll() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val a = Shots.files(paparazzi.context)
            val first = AppDialog.Builder(a).setTitle("Pokémon details").setCloseAtTop().create()
            val second = AppDialog.Builder(a).setTitle("Species details").setCloseAtTop().create()
            val third = AppDialog.Builder(a).setTitle("Move details").setBackAtTop().create()
            try {
                first.joinStack()
                assertEquals(View.GONE, closeAll(first).visibility)
                second.joinStack()
                assertEquals(View.VISIBLE, closeAll(second).visibility)
                paparazzi.snapshot(second.content(), "r25e-close-all-${theme.name}")
                second.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
                assertEquals(View.GONE, closeAll(first).visibility)
                second.joinStack(); third.joinStack()
                third.cancel() // Back/cancel removes one only
                assertEquals(View.VISIBLE, closeAll(second).visibility)
                closeAll(second).performClick()
                val fresh = AppDialog.Builder(a).setTitle("Fresh").setCloseAtTop().create()
                try { fresh.joinStack(); assertEquals(View.GONE, closeAll(fresh).visibility) } finally { fresh.dismiss() }
            } finally { first.dismiss(); second.dismiss(); third.dismiss() }
        }
    }

    @Test fun unrelatedPagesDoNotCountAsAStack() {
        val a = Shots.files(paparazzi.context)
        val b = android.content.ContextWrapper(a)
        val first = AppDialog.Builder(a).setTitle("First page").setCloseAtTop().create()
        val other = AppDialog.Builder(b).setTitle("Other page").setCloseAtTop().create()
        try {
            first.joinStack(); other.joinStack()
            assertEquals(View.GONE, closeAll(first).visibility)
            assertEquals(View.GONE, closeAll(other).visibility)
        } finally { first.dismiss(); other.dismiss() }
    }

    @Test fun itemSheetParticipatesAndCloseAllNotifiesOwnerOnce() {
        val a = Shots.files(paparazzi.context)
        FakeGame.install(a)
        val page = ShopPage(a, NoHost)
        var closed = 0
        val item = ItemWindow(page) { closed++ }
        val detail = AppDialog.Builder(page).setTitle("Species").setCloseAtTop().create()
        ItemWindow.interceptShow = { }
        try {
            item.update(ItemCard.Spec("potion")); item.show()
            detail.joinStack()
            assertEquals(View.VISIBLE, closeAll(detail).visibility)
            closeAll(detail).performClick()
            assertFalse(item.isShowing)
            assertEquals(1, closed)
            item.dismiss(); detail.dismiss()
            assertEquals(1, closed)
        } finally { ItemWindow.interceptShow = null; item.dismiss(); detail.dismiss(); page.onGone() }
    }

    @Test fun baseSpeciesCatchDoesNotColourSeenRegionalEvolution() {
        val a = Shots.files(paparazzi.context)
        FakeGame.install(a)
        val root = TestData.species(25, "Pikachu", evolutions = listOf(Evolution(10100, EvoKind.ITEM, item = "thunder-stone")))
        val regional = TestData.species(10100, "Raichu-Alola", prevo = 25).copy(form = "Alola", baseNum = 26)
        val data = TestData.data(listOf(root, regional))
        val state = TestData.state(data, 25, 5).apply { caught.clear(); caught.add(26) }
        val rows = EvolutionRows(a, data, EvoLine.of(data, root).root, root, setOf(25, 26), null, state)
        assertNotNull(views(rows).filterIsInstance<ImageView>()[1].colorFilter)
        assertTrue(views(rows).filterIsInstance<TextView>().any { it.text.toString() == "Alola" })
    }

    @Test fun evolutionUsesCaughtHistoryAndKeepsSeenLinks() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val a = Shots.files(paparazzi.context)
            FakeGame.install(a)
            val root = TestData.species(1, "Bulbasaur", evolutions = listOf(Evolution(2, EvoKind.LEVEL, level = 16)))
            val middle = TestData.species(2, "Ivysaur", prevo = 1, evolutions = listOf(Evolution(3, EvoKind.LEVEL, level = 32)))
            val data = TestData.data(listOf(root, middle, TestData.species(3, "Venusaur", prevo = 2)))
            val state = TestData.state(data, 1, 5).apply { caught.clear(); caught.add(1) }
            var tapped = 0
            val rows = EvolutionRows(a, data, EvoLine.of(data, root).root, root, setOf(1, 2), { tapped = it.num }, state)
            val images = views(rows).filterIsInstance<ImageView>().take(3)
            assertNull(images[0].colorFilter)
            assertNotNull(images[1].colorFilter)
            assertNotNull(images[2].colorFilter)
            assertEquals(0.55f, images[1].alpha, 0.001f)
            val labels = views(rows).filterIsInstance<TextView>()
            assertTrue(labels.any { it.text.toString() == "???" })
            (labels.single { it.text.toString() == "Ivysaur" }.parent as View).performClick()
            assertEquals(2, tapped)
            paparazzi.snapshot(rows, "r25e-evolution-${theme.name}")
            // Ever-caught colour survives release.
            state.caught.add(2)
            state.mons.clear()
            val released = EvolutionRows(a, data, EvoLine.of(data, root).root, root, setOf(1, 2), null, state)
            assertNull(views(released).filterIsInstance<ImageView>()[1].colorFilter)
        }
    }
}
