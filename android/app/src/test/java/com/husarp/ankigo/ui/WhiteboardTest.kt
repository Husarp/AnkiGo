package com.husarp.ankigo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
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
        assertEquals(Color.TRANSPARENT, pixel(b))
        b.undo(); assertEquals(Color.RED, pixel(b))
        b.clear(); assertEquals(0, b.strokeCount); assertEquals(Color.TRANSPARENT, pixel(b))
        b.undo(); assertEquals(0, b.strokeCount)
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
            assertTrue(parent.indexOfChild(board) < parent.indexOfChild(field("calmView")))
            assertNotSame(parent, field("buttons").parent)
            assertNotSame(parent, field("panel").parent)
            val review = com.husarp.ankigo.Anki.Review(10L, 0, 4, listOf("1m", "6m", "1d", "4d"), emptyList())
            val card = com.husarp.ankigo.StudyCard(review, "Question", "Answer", "Card", "Basic", "", emptyMap(), "",
                emptyList(), emptyList(), false, com.husarp.ankigo.study.TtsRules.NONE, org.json.JSONObject(), "Deck", true, 0L)
            val prepared = com.husarp.ankigo.study.Prepared(com.husarp.ankigo.study.StudyLoop.Key(10L, 0), emptyMap(), card)
            com.husarp.ankigo.shots.call(activity, "showCard", prepared, true)
            stroke(board); assertEquals(1, board.strokeCount)
            com.husarp.ankigo.shots.call(activity, "cardConfirmed", prepared)
            assertEquals("confirmation keeps handwriting", 1, board.strokeCount)
            // A repeated note is still a new scheduled presentation and must get a clean board.
            com.husarp.ankigo.shots.call(activity, "showCard", prepared, true)
            assertEquals(0, board.strokeCount)
        }
    }
}
