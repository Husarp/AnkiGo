package com.husarp.ankigo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import com.husarp.ankigo.shots.shoot
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WhiteboardTest {
    @get:Rule val paparazzi = com.husarp.ankigo.shots.Shots.rule()
    private fun board() = Whiteboard(paparazzi.context).apply { layout(0, 0, 200, 200); ink = Color.RED }
    private fun stroke(b: Whiteboard, x: Float = 50f) {
        for ((action, y) in listOf(MotionEvent.ACTION_DOWN to 20f, MotionEvent.ACTION_MOVE to 60f, MotionEvent.ACTION_UP to 100f)) {
            val e = MotionEvent.obtain(0, 0, action, x, y, 0)
            b.onTouchEvent(e); e.recycle()
        }
    }
    private fun pixel(b: Whiteboard) = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).let {
        b.draw(Canvas(it)); it.getPixel(50, 50)
    }
    @Test fun drawsErasesAndUndoRestoresInkWithoutErasingCard() {
        val b = board(); stroke(b)
        assertEquals(Color.RED, pixel(b)); assertEquals(1, b.strokeCount)
        b.erasing = true; stroke(b)
        assertEquals(Color.TRANSPARENT, pixel(b)); assertEquals(0, b.inkBottom)
        b.undo(); assertEquals(Color.RED, pixel(b)); assertTrue(b.inkBottom >= 100)
        b.clear(); assertEquals(0, b.strokeCount); assertEquals(Color.TRANSPARENT, pixel(b)); assertEquals(0, b.inkBottom)
        b.undo(); assertEquals(1, b.strokeCount)
    }
    @Test fun cancelledGestureIsDiscardedAndTapMakesDot() {
        val b = board()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL)) {
            val e = MotionEvent.obtain(0, 0, action, 50f, 50f, 0); b.onTouchEvent(e); e.recycle()
        }
        assertEquals(0, b.strokeCount)
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val e = MotionEvent.obtain(0, 0, action, 50f, 50f, 0); b.onTouchEvent(e); e.recycle()
        }
        assertEquals(Color.RED, pixel(b))
    }
    @Test fun newPreferenceDefaultsOnAndPaletteHasSixChoices() {
        val c = com.husarp.ankigo.shots.Shots.files(paparazzi.context)
        c.getSharedPreferences("study", 0).edit().remove(WhiteboardTools.PREF).commit()
        val b = Whiteboard(c)
        val tools = WhiteboardTools(c, b)
        assertTrue(tools.isOn)
        fun eraser(v: View): android.widget.TextView? {
            if (v is android.widget.TextView && v.text == "Eraser") return v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) eraser(v.getChildAt(i))?.let { return it }
            return null
        }
        b.ink = Color.RED
        val toggle = eraser(tools)!!
        toggle.performClick(); assertTrue(b.erasing)
        toggle.performClick(); assertFalse(b.erasing); assertEquals(Color.RED, b.ink)
        assertEquals(6, tools.colours().size)
        assertFalse(tools.colours().any { it.first == "Pen" || it.first == "Ink" })
        val night = c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        assertFalse(tools.colours().any { it.first == if (night == android.content.res.Configuration.UI_MODE_NIGHT_YES) "Black" else "White" })
    }
    @Test fun toggleIsRememberedAndHidingKeepsDrawing() {
        val c = com.husarp.ankigo.shots.Shots.files(paparazzi.context)
        val b = Whiteboard(c); val tools = WhiteboardTools(c, b)
        tools.setBoardOn(true); stroke(b)
        tools.setBoardOn(false); assertEquals(View.GONE, b.visibility); assertEquals(1, b.strokeCount)
        val next = Whiteboard(c); WhiteboardTools(c, next)
        assertEquals(View.GONE, next.visibility)
        tools.setBoardOn(true)
        val reopened = Whiteboard(c); WhiteboardTools(c, reopened)
        assertEquals(View.VISIBLE, reopened.visibility)
        tools.setBoardOn(false)
    }
    @Test fun paletteInBothThemes() {
        paparazzi.shoot("r26-whiteboard-palette", listOf(com.husarp.ankigo.shots.Size.PHONE)) { ctx ->
            val tools = WhiteboardTools(ctx, Whiteboard(ctx))
            fun all(v: View): List<View> = listOf(v) + if (v is android.view.ViewGroup)
                (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
            val colour = all(tools).first { it.contentDescription?.toString()?.startsWith("Whiteboard colour:") == true }
            val dialog = com.husarp.ankigo.shots.AppDialogShots.capture { colour.performClick() }!!
            val labels = all(dialog.content()).filterIsInstance<android.widget.TextView>().map { it.text.toString() }
            val dark = ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            assertTrue(labels.any { it.endsWith(if (dark) "White" else "Black") })
            assertFalse(labels.any { it.endsWith(if (dark) "Black" else "White") })
            com.husarp.ankigo.shots.AppDialogShots.scene(ctx, dialog)
        }
    }

    @Test fun studyOverlayStaysInsideCardAndBelowCalmCoverInBothThemes() {
        for (theme in com.husarp.ankigo.shots.Theme.values()) {
            paparazzi.unsafeUpdateConfig(deviceConfig = com.husarp.ankigo.shots.Shots.device(com.husarp.ankigo.shots.Size.PHONE, theme))
            val c = com.husarp.ankigo.shots.Shots.files(paparazzi.context)
            com.husarp.ankigo.shots.FakeGame.install(c)
            val activity = com.husarp.ankigo.shots.attach(com.husarp.ankigo.StudyActivity(), com.husarp.ankigo.shots.ShotCtx(c))
            activity.buildContent()
            fun field(name: String) = com.husarp.ankigo.shots.getField(activity, name) as View
            val board = field("whiteboard") as Whiteboard
            val parent = board.parent as android.view.ViewGroup
            assertSame(parent, field("swipe").parent)
            assertTrue(((parent.parent.parent.parent as View).parent as android.view.ViewGroup).indexOfChild(field("calmView")) > 0)
            assertNotSame(parent, field("buttons").parent)
            assertNotSame(parent, field("panel").parent)
            val review = com.husarp.ankigo.Anki.Review(10L, 0, 4, listOf("1m", "6m", "1d", "4d"), emptyList())
            val card = com.husarp.ankigo.StudyCard(review, "Question", "Answer", "Card", "Basic", "", emptyMap(), "",
                emptyList(), emptyList(), false, com.husarp.ankigo.study.TtsRules.NONE, org.json.JSONObject(), "Deck", true, 0L)
            val prepared = com.husarp.ankigo.study.Prepared(com.husarp.ankigo.study.StudyLoop.Key(10L, 0), emptyMap(), card)
            com.husarp.ankigo.shots.call(activity, "showCard", prepared, true)
            (com.husarp.ankigo.shots.getField(activity, "whiteboardTools") as WhiteboardTools).setBoardOn(true)
            paparazzi.snapshot(field("root"), name = "whiteboard-${theme.name}")
            fun findMore(v: View): View? {
                if (v is android.widget.TextView && v.text == "+ More room") return v
                if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findMore(v.getChildAt(i))?.let { return it }
                return null
            }
            val more = findMore(field("root"))!!
            assertTrue("More room is visible on the first layout", more.getGlobalVisibleRect(android.graphics.Rect()))
            more.performClick()
            paparazzi.snapshot(field("root"), name = "whiteboard-extended-${theme.name}")
            stroke(board); assertEquals(1, board.strokeCount)
            com.husarp.ankigo.shots.call(activity, "cardConfirmed", prepared)
            assertEquals("confirmation keeps handwriting", 1, board.strokeCount)
            // A repeated note is still a new scheduled presentation and must get a clean board.
            com.husarp.ankigo.shots.call(activity, "showCard", prepared, true)
            assertEquals(0, board.strokeCount)
        }
    }
}
