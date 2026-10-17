package com.husarp.ankigo.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.*

/**
 * R6-08: the 1x1 Study now widget's setup (APPWIDGET_CONFIGURE and the launcher's Reconfigure): its background, System (follows
 * light / dark, the default), White or Black. A pick is saved and drawn at once; "Done" finishes.
 */
class StudyNowSetupActivity : AgActivity() {
    override val showsUpdateBanner = false
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { setResult(Activity.RESULT_CANCELED); finish(); return }
        // The setup is optional: Back and "Done" both keep the widget with whatever is picked (System by default), so the result is OK from the start
        // (the usual launcher pattern; RESULT_CANCELED would make a launcher discard the new widget).
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        setContentView(buildContent())
    }

    /** "Done": draws the widget (also when the default System background was kept), then finishes with RESULT_OK + the widget id. */
    private fun done() {
        Widgets.refreshAll(this, study = false)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    /** App bar plus the scrolling page; split out of onCreate so the design screenshots can build it. */
    internal fun buildContent(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(this@StudyNowSetupActivity, R.color.ag_base)) }
        root.addView(Ui.appBar(this, "Study now widget", "Pick a background", back = true))
        val content = StudyUi.column(this).apply { setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), Ui.dp(context, 16)) }
        val prefs = getSharedPreferences("spike", 0)
        val current = if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) StudyNowBg.SYSTEM else StudyNowPrefs.bg(prefs, widgetId)
        content.addView(StudyUi.section(this, "Background", "default = System"))
        val control = Form.segmented(this, StudyNowBg.values().map { it.label }, current.ordinal) { i ->
            StudyNowPrefs.setBg(prefs, widgetId, StudyNowBg.values()[i])
            Widgets.refreshAll(this, study = false)
        }
        content.addView(Form.cardOf(this, listOf(Form.stackRow(this, "Widget background", "System follows the phone's light or dark mode. The icon, \"Study\" and the due number keep readable colours on each.", control))),
            StudyUi.lp().also { it.bottomMargin = Ui.dp(this, 8) })
        content.addView(StudyUi.primary(this, "Done") { done() }, StudyUi.spaced(this, 8))
        val scroll = ScrollView(this).apply { addView(content, android.widget.FrameLayout.LayoutParams(StudyUi.pageWidth(context), -2, Gravity.CENTER_HORIZONTAL)) }
        root.addView(scroll, StudyUi.lp(-1, 0, 1f))
        return root
    }
}
