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
    private fun all(v: View): List<View> = listOf(v) + if (v is android.view.ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun toolsToggleExclusivelyAndOffUsesExistingPreferenceAndCallback() {
        val c = com.husarp.ankigo.shots.Shots.files(paparazzi.context)
        val b = Whiteboard(c); val tools = WhiteboardTools(c, b)
        tools.setBoardOn(true)
        fun tool(label: String) = all(tools).filterIsInstance<android.widget.TextView>().single { it.text == label }
        val pen = tool("Pen"); val eraser = tool("Eraser")
        assertTrue(pen.isSelected); assertFalse(eraser.isSelected)
        pen.performClick(); assertFalse(b.drawingEnabled); assertFalse(pen.isSelected)
        stroke(b); assertEquals(0, b.strokeCount)
        eraser.performClick(); assertTrue(b.drawingEnabled); assertTrue(b.erasing); assertTrue(eraser.isSelected)
        pen.performClick(); assertTrue(b.drawingEnabled); assertFalse(b.erasing); assertFalse(eraser.isSelected)
        eraser.performClick(); eraser.performClick()
        assertFalse(b.drawingEnabled); assertFalse(eraser.isSelected); assertFalse(pen.isSelected)
        all(tools.paletteContent()).first { it.contentDescription == "Whiteboard colour: Blue" }.performClick()
        assertTrue(b.drawingEnabled); assertFalse(b.erasing); assertTrue(pen.isSelected)
        var notified: Boolean? = null
        tools.onChanged = { notified = it }
        all(tools).single { it.contentDescription == "Whiteboard Off" }.performClick()
        assertEquals(false, notified); assertFalse(tools.isOn)
        assertFalse(c.getSharedPreferences("study", 0).getBoolean(WhiteboardTools.PREF, true))
        assertFalse(WhiteboardTools(c, Whiteboard(c)).isOn)
    }

    @Test fun disabledToolPassesSingleFingerDragToPaperScrollAndCancelsPendingStroke() {
        val c = com.husarp.ankigo.shots.Shots.files(paparazzi.context)
        val b = Whiteboard(c)
        val paper = WhiteboardPaper(c, View(c).apply { isClickable = true }, b)
        all(paper).filterIsInstance<android.widget.TextView>().single { it.text == "+ More room" }.performClick()
        paper.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        paper.layout(0, 0, 720, 800)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 600f, 0)
        assertTrue(b.onTouchEvent(down)); down.recycle()
        b.drawingEnabled = false
        for ((index, action) in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).withIndex()) {
            val event = MotionEvent.obtain(0, index * 30L, action, 100f, 600f - index * 140f, 0)
            assertFalse(b.onTouchEvent(event))
            paper.dispatchTouchEvent(event); event.recycle()
        }
        assertEquals(0, b.strokeCount)
        val scroll = all(paper).filterIsInstance<MotionScrollView>().single()
        assertTrue("One finger scrolls the paper with both tools off", scroll.scrollY > 0)
        b.drawingEnabled = true
        stroke(b); assertEquals(1, b.strokeCount)
    }

    @Test fun compactToolbarAndExtendedPaperAtBothWidthsAndThemes() {
        for (width in listOf(360, 412)) for (theme in com.husarp.ankigo.shots.Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = com.husarp.ankigo.shots.Variant("phone", width, 800).device(theme))
            val c = com.husarp.ankigo.shots.Shots.files(paparazzi.context)
            val b = Whiteboard(c); val tools = WhiteboardTools(c, b)
            tools.setBoardOn(true)
            val paper = WhiteboardPaper(c, StudyUi.label(c, "What powers photosynthesis?", 20, com.husarp.ankigo.R.color.ag_text, 700).apply {
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(Ui.c(c, com.husarp.ankigo.R.color.ag_card))
            }, b)
            tools.onChanged = { paper.setBoardOn(it) }
            all(paper).filterIsInstance<android.widget.TextView>().single { it.text == "+ More room" }.performClick()
            val root = android.widget.LinearLayout(c).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(c, com.husarp.ankigo.R.color.ag_base))
                addView(tools, android.widget.LinearLayout.LayoutParams(-1, -2))
                addView(paper, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
            }
            paparazzi.snapshot(root, name = "k-whiteboard-${width}-${theme.name.lowercase()}")
            assertEquals(Ui.dp(c, 48), tools.height)
            val pen = all(tools).filterIsInstance<android.widget.TextView>().single { it.text == "Pen" }
            val dot = all(tools).single { it.contentDescription?.startsWith("Whiteboard colour:") == true }
            assertTrue(pen.width - Ui.dp(c, 6) >= Ui.dp(c, 40))
            assertTrue(dot.width >= Ui.dp(c, 40)); assertTrue(dot.height >= Ui.dp(c, 40))
            assertTrue(dot.x < pen.right)
            assertTrue(all(tools).filterIsInstance<android.widget.TextView>().filter { it != dot && it.text.isNotEmpty() }.all { it.text == "Pen" || it.text == "Eraser" })
            assertTrue(all(tools).filter { it.isClickable }.all { it.right <= tools.width })
            var colourTaps = 0
            dot.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) colourTaps++
                true
            }
            fun tap(x: Float) {
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(0, 0, action, x, tools.height / 2f, 0)
                    tools.dispatchTouchEvent(event); event.recycle()
                }
            }
            tap(dot.x + Ui.dp(c, 2).toFloat())
            assertEquals(1, colourTaps); assertTrue(b.drawingEnabled)
            var penTaps = 0
            pen.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) penTaps++
                true
            }
            tap(pen.x + Ui.dp(c, 20))
            assertEquals(1, penTaps); assertEquals(1, colourTaps)
            pen.performClick(); assertFalse(b.drawingEnabled)
            paparazzi.snapshot(root, name = "k-scroll-${width}-${theme.name.lowercase()}")
        }
    }

    @Test fun leavingAppDismissesColourDropdown() {
        AwayWindows.dismissAll()
        val c = com.husarp.ankigo.shots.Shots.files(paparazzi.context)
        val tools = WhiteboardTools(c, Whiteboard(c))
        var dismissed = 0
        val popup = object : android.widget.PopupWindow() {
            override fun dismiss() { dismissed++ }
        }
        assertSame(popup, tools.paletteWindow(popup))
        assertNotNull(popup.contentView)
        AwayWindows.dismissAll()
        assertEquals(1, dismissed)
        AwayWindows.dismissAll()
        assertEquals(1, dismissed)
    }

    @Test fun paletteInBothThemes() {
        paparazzi.shoot("r27d-whiteboard-palette", listOf(com.husarp.ankigo.shots.Size.SMALL)) { ctx ->
            val tools = WhiteboardTools(ctx, Whiteboard(ctx))
            fun all(v: View): List<View> = listOf(v) + if (v is android.view.ViewGroup)
                (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
            val colour = all(tools).first { it.contentDescription?.toString()?.startsWith("Whiteboard colour:") == true }
            val palette = tools.paletteContent()
            val swatches = all(palette).filter { it.contentDescription?.startsWith("Whiteboard colour:") == true }
            assertEquals(6, swatches.size)
            run {
                swatches[2].performClick()
                assertEquals(tools.colours()[2].second, tools.board.ink)
                assertFalse(tools.board.erasing)
            }
            android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                addView(tools); addView(palette)
            }
        }
    }

    @Test fun studyOverlayStaysInsideCardAndBelowCalmCoverInBothThemes() {
        for (width in listOf(360, 412)) for (theme in com.husarp.ankigo.shots.Theme.values()) {
            paparazzi.unsafeUpdateConfig(deviceConfig = com.husarp.ankigo.shots.Variant("phone", width, 800).device(theme))
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
            paparazzi.snapshot(field("root"), name = "k-study-${width}-${theme.name.lowercase()}")
            val menu = activity.actionSheetContent {}
            paparazzi.snapshot(menu, name = "k-menu-${width}-${theme.name.lowercase()}")
            fun all(v: View): List<View> = listOf(v) + if (v is android.view.ViewGroup)
                (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
            val title = all(menu).filterIsInstance<android.widget.TextView>().single { it.text == "Whiteboard" }
            val row = title.parent.parent as android.view.ViewGroup
            assertTrue(all(row).any { it is android.widget.ImageView && it.drawable != null })
            assertFalse(all(field("root")).filterIsInstance<android.widget.TextView>().any { it.text == "Whiteboard" })
            fun findMore(v: View): View? {
                if (v is android.widget.TextView && v.text == "+ More room") return v
                if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findMore(v.getChildAt(i))?.let { return it }
                return null
            }
            val more = findMore(field("root"))!!
            assertTrue("More room is visible on the first layout", more.getGlobalVisibleRect(android.graphics.Rect()))
            more.performClick()
            paparazzi.snapshot(field("root"), name = "k-study-extended-${width}-${theme.name.lowercase()}")
            stroke(board); assertEquals(1, board.strokeCount)
            com.husarp.ankigo.shots.call(activity, "cardConfirmed", prepared, true)
            assertEquals("confirmation keeps handwriting", 1, board.strokeCount)
            // A repeated note is still a new scheduled presentation and must get a clean board.
            com.husarp.ankigo.shots.call(activity, "showCard", prepared, true)
            assertEquals(0, board.strokeCount)
        }
    }
}
