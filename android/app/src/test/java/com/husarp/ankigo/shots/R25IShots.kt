package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ScrollView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R25IShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun text(v: View, label: String) = views(v).filterIsInstance<TextView>().single { it.text.toString() == label }

    @Test fun speciesOptionsAndCloseStayPinnedAndCloseTheStack() {
        for (theme in Theme.entries) for (width in listOf(320, 360)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("w$width", width, 640).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val page = DexPage(ctx, NoHost)
            val parent = AppDialog.Builder(page).setTitle("Previous details").setCloseAtTop().create()
            val d = DialogShots.capture { DexDetail.show(page, GameRepo.data!!.sp(25)) }!!
            try {
                parent.joinStack(); d.joinStack()
                val root = d.content()
                val action = text(root, "Options")
                val myPokemon = text(root, "My Pokémon")
                val close = text(root, "Close")
                assertFalse(views(root).filterIsInstance<TextView>().any { it.text.toString() == "Close all" })
                assertSame(close.parent, action.parent)
                assertNotSame(close.parent, myPokemon.parent)
                paparazzi.snapshot(DialogShots.scene(ctx, d), "r25i-species-$width-${theme.name}")
                assertTrue(action.right <= close.left)
                val row = action.parent as ViewGroup
                assertTrue(action.left >= row.paddingLeft)
                assertTrue(close.right <= row.width - row.paddingRight)
                assertTrue(action.width > 0)
                assertEquals("action text fits", 1, action.layout.lineCount)
                val before = IntArray(2).also { action.getLocationInWindow(it) }
                views(root).filterIsInstance<ScrollView>().forEach { it.scrollTo(0, 10000) }
                val after = IntArray(2).also { action.getLocationInWindow(it) }
                assertArrayEquals("action stays pinned while body scrolls", before, after)
                close.performClick()
                val fresh = AppDialog.Builder(page).setCloseAtTop().create()
                try {
                    fresh.joinStack()
                    assertFalse(views(fresh.content()).filterIsInstance<TextView>().any { it.text.toString() == "Close all" })
                    assertFalse(WindowStack.stacked(page))
                } finally { fresh.dismiss() }
            } finally { d.dismiss(); parent.dismiss() }
        }
    }
}
