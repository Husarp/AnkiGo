package com.husarp.ankigo.ui

import com.husarp.ankigo.game.lookNum
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.*
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.shownName

/**
 * Pokémon picker for one Buddy widget: shown when the widget is added (APPWIDGET_CONFIGURE) and from the launcher's
 * Reconfigure. The first row is "Current main" (the default: the widget follows your team), then every owned Pokémon.
 * Tapping a row saves the choice for this widget id, redraws the widgets and finishes. Without a loaded game only
 * "Current main" is offered.
 */
class BuddyWidgetSetupActivity : AgActivity() {
    override val showsUpdateBanner = false
    private lateinit var content: LinearLayout
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var current = BuddyWidgetPrefs.MAIN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        // Backing out must cancel the add (the launcher then drops the widget when it was required).
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        current = BuddyWidgetPrefs.choice(getSharedPreferences("spike", 0), widgetId)
        setContentView(buildContent())
        render()
        GameRepo.load(this) { if (!isDestroyed) render() }
    }

    /** App bar plus the scrolling list; split out of onCreate so the design screenshots can build it. */
    internal fun buildContent(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(this@BuddyWidgetSetupActivity, R.color.ag_base)) }
        root.addView(Ui.appBar(this, "Buddy widget", "Pick a Pokémon", back = true))
        content = StudyUi.column(this).apply { setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), Ui.dp(context, 16)) }
        val scroll = ScrollView(this).apply { addView(content, android.widget.FrameLayout.LayoutParams(StudyUi.pageWidth(context), -2, Gravity.CENTER_HORIZONTAL)) }
        root.addView(scroll, StudyUi.lp(-1, 0, 1f))
        return root
    }

    /** The Pokémon in the list: the team in order, then the box by level (highest first), then name. */
    internal fun owned(): List<Mon> {
        val st = GameRepo.state ?: return emptyList()
        return st.teamMons() + st.boxMons().sortedWith(compareByDescending<Mon> { it.level }.thenBy { it.shownName })
    }

    internal fun render() {
        content.removeAllViews()
        content.addView(StudyUi.label(this, "Which Pokémon should this widget show? You can change it later from a Pokémon's menu (Show on Buddy widget).", 13)
            .apply { setPadding(Ui.dp(context, 4), Ui.dp(context, 4), Ui.dp(context, 4), 0) })
        content.addView(StudyUi.section(this, "Pokémon", "default = current main"))
        val st = GameRepo.state
        val main = st?.main
        addRow(BuddyWidgetPrefs.MAIN, null, "Current main", if (main != null) "Follows your team: now ${main.shownName}" else "Follows your team", tag = "DEFAULT")
        val mons = owned()
        if (st != null && mons.isEmpty() && main == null) {
            content.addView(StudyUi.label(this, "Open AnkiGo and choose your starter first, then you can pick any Pokémon here.", 12)
                .apply { setPadding(Ui.dp(context, 4), Ui.dp(context, 8), Ui.dp(context, 4), 0) })
        }
        for (m in mons) {
            val where = if (m.uid in st!!.team) (if (m.uid == main?.uid) "Main" else "Team") else "Box"
            addRow(m.uid, m, m.shownName, "Lv ${m.level} · $where")
        }
    }

    private fun addRow(choice: String, mon: Mon?, title: String, sub: String, tag: String? = null) {
        val selected = choice == current
        val row = StudyUi.row(this).apply {
            minimumHeight = Ui.dp(context, 52)
            background = if (selected) StudyUi.gradient(context, R.color.ag_mauve, .24f, 12) else Ui.rounded(Ui.c(context, R.color.ag_card), 12, context)
            setPadding(Ui.dp(context, 8), Ui.dp(context, 6), Ui.dp(context, 12), Ui.dp(context, 6))
            isClickable = true; isFocusable = true
            contentDescription = "$title, $sub${if (selected) ", selected" else ""}"
            setOnClickListener { choose(choice) }
        }
        val size = Ui.dp(this, 40)
        if (mon != null) {
            val img = ImageView(this)
            Ui.staticSprite(this, mon.lookNum(com.husarp.ankigo.GameRepo.data), mon.shiny, img)
            row.addView(img, StudyUi.lp(size, size))
        } else {
            row.addView(StudyUi.icon(this, R.drawable.ic_star, R.color.ag_mauve).apply { setPadding(Ui.dp(context, 9), Ui.dp(context, 9), Ui.dp(context, 9), Ui.dp(context, 9)) }, StudyUi.lp(size, size))
        }
        val text = StudyUi.column(this)
        val titleRow = StudyUi.row(this)
        titleRow.addView(StudyUi.ellipsis(StudyUi.label(this, (if (mon?.shiny == true) "★ " else "") + title, 14, R.color.ag_text, 800)), StudyUi.lp(-2, -2, 0f))
        if (tag != null) titleRow.addView(tag(tag, R.color.ag_mauve), StudyUi.lp(-2).also { it.marginStart = Ui.dp(this, 6) })
        if (selected) titleRow.addView(tag("SELECTED", R.color.ag_green), StudyUi.lp(-2).also { it.marginStart = Ui.dp(this, 6) })
        text.addView(titleRow)
        text.addView(StudyUi.ellipsis(StudyUi.label(this, sub, 11, R.color.ag_sub0, 700).also { tv ->
            if (mon != null) tv.text = LevelTier.line(this, "", mon.level, sub.removePrefix("Lv ${mon.level}")) // level in its tier colour
        }), StudyUi.spaced(this, 1))
        row.addView(text, StudyUi.lp(0, -2, 1f).also { it.marginStart = Ui.dp(this, 8) })
        content.addView(row, StudyUi.lp().also { it.bottomMargin = Ui.dp(this, 5) })
    }

    private fun tag(text: String, tone: Int) = StudyUi.label(this, text, 9, tone, 900).apply {
        background = Ui.rounded((Ui.c(context, tone) and 0x00ffffff) or 0x29000000, 5, context)
        setPadding(Ui.dp(context, 5), Ui.dp(context, 1), Ui.dp(context, 5), Ui.dp(context, 1))
    }

    private fun choose(choice: String) {
        BuddyWidgetPrefs.set(getSharedPreferences("spike", 0), widgetId, choice)
        Widgets.refreshAll(this, study = false)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
