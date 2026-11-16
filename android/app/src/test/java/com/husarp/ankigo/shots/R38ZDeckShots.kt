package com.husarp.ankigo.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.text.Spanned
import android.text.style.ImageSpan
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R38ZDeckShots {
    @get:Rule val paparazzi = Shots.rule()

    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    private fun alpha(drawable: Drawable, size: Int): List<Int> {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val bounds = android.graphics.Rect(drawable.bounds)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        drawable.bounds = bounds
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        bitmap.recycle()
        return pixels.map { it ushr 24 }
    }

    @Test fun favouriteDeckMarksAndHeadingUseOptionCInEveryListLook() {
        for (look in DeckListLook.entries) paparazzi.shoot("z-favourite-decks-${look.name.lowercase()}", listOf(Size.SMALL)) { base ->
            val ctx = ShotCtx(base)
            DeckListLook.put(ctx, look)
            ctx.getSharedPreferences("study", 0).edit().putString("favouriteDecks", "2,5").apply()
            val built = StudyDeckKit.compose(ctx)
            setField(built.home, "decks", StudyDeckKit.decks)
            setField(built.home, "expanded", setOf(1L, 4L))
            built.home.render()
            val expected = alpha(ctx.getDrawable(R.drawable.ic_deck_favourite)!!, 32)
            val old = alpha(ctx.getDrawable(R.drawable.ic_star)!!, 32)
            assertNotEquals(old, expected)
            val icons = all(built.root).filterIsInstance<ImageView>().mapNotNull { it.drawable }.map { alpha(it, 32) }
            assertTrue("favourite heading and both deck marks use C", icons.count { it == expected } >= 3)
            assertFalse("old star is absent from favourite decks", old in icons)
            val inlineMarks = all(built.root).filterIsInstance<TextView>().mapNotNull { it.text as? Spanned }
                .flatMap { it.getSpans(0, it.length, ImageSpan::class.java).toList() }
            assertEquals("both favourite rows use vector marks", 2, inlineMarks.size)
            inlineMarks.forEach { assertEquals(expected, alpha(it.drawable, 32)) }
            for (dp in listOf(16, 24, 32)) {
                val mask = alpha(ctx.getDrawable(R.drawable.ic_deck_favourite)!!, Ui.dp(ctx, dp))
                assertTrue(mask.any { it == 255 })
                assertEquals(0, mask.first())
            }
            built.root
        }
    }
}
