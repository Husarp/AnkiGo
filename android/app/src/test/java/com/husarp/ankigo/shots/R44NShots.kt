package com.husarp.ankigo.shots

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.deck.NoteEditActivity
import com.husarp.ankigo.deck.NoteForEdit
import com.husarp.ankigo.study.TtsRules
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R44NShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun onlyASavedEditRequestsARefresh() {
        paparazzi.shootWith("n-697-result", listOf(Size.PHONE)) { ctx ->
            val study = attach(StudyActivity(), ShotCtx(ctx))
            call(study, "onActivityResult", 697, Activity.RESULT_CANCELED, null)
            assertEquals(false, getField(study, "noteEdited"))
            call(study, "onActivityResult", 999, Activity.RESULT_OK, null)
            assertEquals(false, getField(study, "noteEdited"))
            call(study, "onActivityResult", 697, Activity.RESULT_OK, null)
            assertEquals(true, getField(study, "noteEdited"))
            TextView(ctx).apply { text = "Saved note requests a fresh card on resume" }
        }
    }

    @Test fun previewUsesUnsavedFieldsOnBothSidesAndSurvivesRendererFailure() {
        paparazzi.shootWith("n-698-preview", listOf(Size.PHONE)) { ctx ->
            val editor = attach(NoteEditActivity(), ShotCtx(ctx))
            val root = editor.buildContent()
            editor.bind(NoteEditActivity.Loaded(NoteForEdit(7, "Basic", listOf("Front", "Back"), listOf("Old front", "Old back")),
                "Geography", 1, "", false, listOf(TtsRules.Template(0, "{{Front}}", "{{FrontSide}}<hr>{{Back}}"))))
            val edits = all(root).filterIsInstance<EditText>()
            edits[0].setText("Edited front"); edits[1].setText("Edited back")
            val pages = ArrayList<String>()
            editor.previewSink = { pages += it }
            all(root).filterIsInstance<TextView>().single { it.text.toString() == "Preview" }.performClick()
            assertTrue(pages.last().contains("Edited front"))
            assertFalse(pages.last().contains("Old front"))
            val chips = getField(editor, "sideChips") as ViewGroup
            chips.getChildAt(1).performClick()
            assertTrue(pages.last().contains("Edited front") && pages.last().contains("Edited back"))
            assertFalse(editor.isFinishing)
            editor.previewSink = null
            call(editor, "previewFailed")
            val card = getField(editor, "previewCard") as ViewGroup
            assertTrue(all(card).filterIsInstance<TextView>().any { it.text.contains("Edited back") })
            chips.getChildAt(0).performClick()
            assertTrue(all(card).filterIsInstance<TextView>().any { it.text.contains("Edited front") && !it.text.contains("Edited back") })
            assertFalse(editor.isFinishing)
            root
        }
    }

    @Test fun bookCanvasIgnoresLauncherOptionsAndAppConfiguration() {
        for (size in listOf(Size.PHONE, Size.LAND, Size.PIP)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT))
            val ctx = paparazzi.context
            val cases = listOf<Bundle?>(null, Bundle(), Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 45)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 300)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180)
            }, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, -1)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 10000)
            })
            for (options in cases) assertEquals(70f to 70f, WidgetRender.sizeDp(ctx, options, WidgetRender.Kind.STUDY_NOW))
            val options = cases[2]
            val other = WidgetRender.sizeDp(ctx, options, WidgetRender.Kind.WIDE)
            assertEquals(if (size == Size.PHONE) 45f to 180f else 300f to 130f, other)
        }
        for (density in listOf(1f, 2f, 3.5f)) assertEquals(density, WidgetLogic.pixelsPerDp(70f, 70f, density), 0f)
    }

    private fun opaque(v: View) {
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        v.background.setBounds(0, 0, 100, 100)
        v.background.draw(Canvas(bmp))
        for (y in 20..80) for (x in 20..80) assertEquals(255, Color.alpha(bmp.getPixel(x, y)))
        assertEquals(1f, v.alpha, 0f)
        bmp.recycle()
    }

    @Test fun everyBattleAlertHasAnOpaqueSurfaceInBothThemes() {
        paparazzi.shootWith("n-701-all-alerts", listOf(Size.PHONE)) { ctx ->
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), 0)
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                for (kind in ToastKind.entries) {
                    val card = ToastCard(ctx, ToastQueue.Item(kind, "${kind.name}: battle alert", "Details", pop = false), ToastLook.of(412f, 300f), 0f)
                    opaque(card)
                    addView(FrameLayout(ctx).apply {
                        addView(View(ctx).apply { setBackgroundColor(Color.GREEN) }, FrameLayout.LayoutParams(-1, Ui.dp(ctx, 7), android.view.Gravity.CENTER))
                        addView(card, FrameLayout.LayoutParams(-1, -2))
                    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(ctx, 4) })
                }
            }
        }
    }

    @Test fun bottomFeedbackAboveDailyProgressIsOpaque() {
        paparazzi.shootWith("n-701-bottom-feedback", listOf(Size.PHONE)) { ctx ->
            FrameLayout(ctx).apply {
                addView(BattleShotKit.bound(ctx).root, FrameLayout.LayoutParams(-1, -1))
                AppToast.show(this, "Daily goal reached — your gift is ready")
                opaque(getChildAt(childCount - 1))
            }
        }
        BattleShotKit.reset()
    }
}
