package com.husarp.ankigo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.RemoteViews
import com.husarp.ankigo.ui.StudyWidgetPrefs
import com.husarp.ankigo.ui.StudyWidgetSetupActivity
import com.husarp.ankigo.ui.WidgetRender

object Widgets {
    /** [study] = false skips the study widgets (the answer a study widget just gave must not reset the others). */
    fun refreshAll(ctx: Context, study: Boolean = true) {
        for (cls in listOf(MotivationWidget::class.java, com.husarp.ankigo.ui.BuddyWidget::class.java, com.husarp.ankigo.ui.StudyNowWidget::class.java, StudyWidget::class.java)) {
            if (!study && cls == StudyWidget::class.java) continue
            val ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isNotEmpty()) ctx.sendBroadcast(Intent(ctx, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }

    /** The Buddy widget's tap (and the other plain widget taps): a trampoline reads Settings -> Widgets -> "Open AnkiGo on" at TAP time (R18 263). */
    fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, REQ_OPEN_APP, openAppIntent(ctx), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** The Intent behind [openApp]: [com.husarp.ankigo.ui.WidgetTapActivity] with its own action. */
    fun openAppIntent(ctx: Context): Intent =
        Intent(ctx, com.husarp.ankigo.ui.WidgetTapActivity::class.java).setAction(com.husarp.ankigo.ui.WidgetTapActivity.ACTION_OPEN)

    /** The 1x1 Study now widget's tap: AnkiGo's Study tab. */
    fun openStudy(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, REQ_OPEN_STUDY, com.husarp.ankigo.ui.Nav.intent(ctx, com.husarp.ankigo.ui.Dest.STUDY), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** The motivation widget's tap: a trampoline decides at tap time between the Study tab (cards due) and the battle. */
    fun openStudyOrBattle(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, REQ_STUDY_OR_BATTLE, Intent(ctx, com.husarp.ankigo.ui.WidgetTapActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    /** Own request codes: two PendingIntents with the same component and request code are the same to Android (extras don't count). */
    const val REQ_STUDY_OR_BATTLE = 0
    const val REQ_OPEN_APP = 1
    const val REQ_OPEN_STUDY = 2

    fun bcast(ctx: Context, cls: Class<*>, action: String, code: Int, ease: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, cls).setAction(action).putExtra("ease", ease),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun plain(html: String): String {
        val noStyle = html.replace(Regex("(?is)<style.*?</style>"), "").replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("\\[sound:.+?]"), "")
        return Html.fromHtml(noStyle, Html.FROM_HTML_MODE_COMPACT).toString().trim().replace(Regex("\n{3,}"), "\n\n")
    }
}

/** Wide game widget (4x2): the next wild Pokémon as a silhouette, today's goal, the team. Drawn by ui/WidgetRender. */
class MotivationWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pr = goAsync()
        WidgetRender.async(pr) { WidgetRender.update(ctx, mgr, ids, WidgetRender.Kind.WIDE) }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle?) {
        val pr = goAsync()
        WidgetRender.async(pr) { WidgetRender.update(ctx, mgr, intArrayOf(id), WidgetRender.Kind.WIDE) }
    }
}

/** C8b: study widget — simple text card, Show answer, Again / Good. Deck and card state are per widget id (ui/StudyWidgetPrefs). */
class StudyWidget : AppWidgetProvider() {
    companion object {
        const val SHOW = "com.husarp.ankigo.W_SHOW"
        const val ANSWER = "com.husarp.ankigo.W_ANSWER"
        const val NEXT = "com.husarp.ankigo.W_NEXT"

        /** Intent that opens the deck picker for one widget. */
        fun setupIntent(ctx: Context, id: Int) = Intent(ctx, StudyWidgetSetupActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Asks one widget to redraw (used after the setup screen saved a deck). */
        fun requestUpdate(ctx: Context, id: Int) {
            ctx.sendBroadcast(Intent(ctx, StudyWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id)))
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            SHOW, ANSWER, NEXT -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                Report.init(ctx); Anki.load(ctx)
                Report.log("WIDGET", "study widget $id: ${intent.action?.substringAfterLast('.')}${intent.getIntExtra("ease", 0).takeIf { it > 0 }?.let { " ease $it" } ?: ""}")   // R13 Part E
                val pr = goAsync()
                Thread {
                    try {
                        handle(ctx, intent.action!!, intent.getIntExtra("ease", 0), id)
                    } catch (e: Exception) {
                        Report.fail("C8", "study widget ${intent.action}", e)
                    } finally {
                        pr.finish()
                    }
                }.start()
            }
            else -> super.onReceive(ctx, intent)
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Report.init(ctx); Anki.load(ctx)
        val pr = goAsync()
        Thread {
            try {
                for (id in ids) {
                    try {
                        handle(ctx, NEXT, 0, id)
                    } catch (e: Exception) {
                        Report.fail("C8", "study widget update $id", e)
                    }
                }
            } finally {
                pr.finish()
            }
        }.start()
    }

    override fun onDeleted(ctx: Context, appWidgetIds: IntArray) {
        StudyWidgetPrefs.forget(ctx.getSharedPreferences("spike", 0), appWidgetIds)
    }

    private fun handle(ctx: Context, action: String, ease: Int, id: Int) {
        val p = ctx.getSharedPreferences("spike", 0)
        val mgr = AppWidgetManager.getInstance(ctx)
        StudyWidgetPrefs.prepare(p, mgr.getAppWidgetIds(ComponentName(ctx, StudyWidget::class.java)))
        val rv = RemoteViews(ctx.packageName, R.layout.widget_study)
        fun key(f: String) = StudyWidgetPrefs.key(id, f)
        fun b(a: String, n: Int, e: Int = 0): PendingIntent = PendingIntent.getBroadcast(ctx, id * 10 + n,
            Intent(ctx, StudyWidget::class.java).setAction(a).putExtra("ease", e).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val setup = PendingIntent.getActivity(ctx, id * 10 + 6, setupIntent(ctx, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        rv.setOnClickPendingIntent(R.id.s_show, b(SHOW, 1))
        rv.setOnClickPendingIntent(R.id.s_again, b(ANSWER, 2, 1))
        rv.setOnClickPendingIntent(R.id.s_hard, b(ANSWER, 3, 2))
        rv.setOnClickPendingIntent(R.id.s_good, b(ANSWER, 4, 3))
        rv.setOnClickPendingIntent(R.id.s_easy, b(ANSWER, 5, 4))
        rv.setOnClickPendingIntent(R.id.s_refresh, b(NEXT, 7))
        rv.setOnClickPendingIntent(R.id.s_deck, setup)
        rv.setOnClickPendingIntent(R.id.s_change_deck, setup)
        rv.setOnClickPendingIntent(R.id.s_change, setup)

        if (!Anki.hasPermission(ctx)) {
            show(ctx, rv, id, "Open AnkiGo and grant the AnkiDroid permission first.", showBtn = false, answerBtns = false); return
        }
        var deckId = StudyWidgetPrefs.deckId(p, id)
        var deckName = StudyWidgetPrefs.deckName(p, id)
        if (deckId == 0L) {
            // Added without setup (or nothing migrated): the deck with the most cards due, never an empty one.
            val pick = StudyWidgetPrefs.mostDue(Anki.decks(ctx))
            if (pick != null) {
                deckId = pick.id; deckName = pick.name
                StudyWidgetPrefs.setDeck(p, id, deckId, pick.name)
            }
        }
        if (deckId == 0L) {
            rv.setTextViewText(R.id.s_deck, "Choose a deck")
            show(ctx, rv, id, "Nothing due in AnkiDroid.", showBtn = false, answerBtns = false, changeBtn = true); return
        }
        val leaf = (deckName ?: "?").substringAfterLast("::")
        rv.setTextViewText(R.id.s_deck, leaf)
        rv.setOnClickPendingIntent(R.id.s_open, PendingIntent.getActivity(ctx, id * 10 + 8,
            Intent(ctx, StudyActivity::class.java).putExtra("deckId", deckId).putExtra("deckName", deckName ?: "?")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

        if (action == ANSWER) {
            val nid = p.getLong(key(StudyWidgetPrefs.NID), 0); val ord = p.getInt(key(StudyWidgetPrefs.ORD), 0)
            val took = System.currentTimeMillis() - p.getLong(key(StudyWidgetPrefs.START), System.currentTimeMillis())
            if (nid == 0L) return
            // Anki.answer only answers the top card of the deck's queue (returns 0 if another widget/app moved on): never count it then.
            val n = Anki.answer(ctx, nid, ord, ease, took, deckId)
            if (n > 0) Local.recordReview(ctx, nid, ord, ease, took, "widget")
            Report.log("C8", "widget $id answer nid=$nid ord=$ord ease=$ease took=${took}ms rows=$n")
            Widgets.refreshAll(ctx, study = false) // motivation widget count; the other study widgets keep their card
        }

        if (action == SHOW) {
            val nid = p.getLong(key(StudyWidgetPrefs.NID), 0); val ord = p.getInt(key(StudyWidgetPrefs.ORD), 0)
            val c = Anki.card(ctx, nid, ord, arrayOf("question", "answer", "answer_pure"))
            val ans = Widgets.plain(c?.get("answer_pure") ?: c?.get("answer") ?: "?")
            show(ctx, rv, id, p.getString(key(StudyWidgetPrefs.Q), "") + "\n―――\n" + ans, showBtn = false, answerBtns = true)
            Report.log("C8", "widget $id show answer nid=$nid")
            return
        }

        // NEXT (or after answer): fetch next card
        val next = Anki.nextCards(ctx, deckId, 1).firstOrNull()
        if (next == null) {
            p.edit().apply { StudyWidgetPrefs.cardKeysOf(id).forEach { remove(it) } }.apply()
            show(ctx, rv, id, "Nothing due in $leaf", showBtn = false, answerBtns = false, changeBtn = true); return
        }
        val c = Anki.card(ctx, next.nid, next.ord, arrayOf("question", "answer"))
        val qHtml = c?.get("question") ?: ""
        val complex = next.media.isNotEmpty() || qHtml.contains("[[type:") || qHtml.contains("<img", true) ||
            qHtml.contains("\\(") || qHtml.contains("image-occlusion", true) || qHtml.contains("[anki:play")
        val qText = Widgets.plain(qHtml)
        p.edit().putLong(key(StudyWidgetPrefs.NID), next.nid).putInt(key(StudyWidgetPrefs.ORD), next.ord)
            .putLong(key(StudyWidgetPrefs.START), System.currentTimeMillis()).putString(key(StudyWidgetPrefs.Q), qText).apply()
        if (complex) {
            show(ctx, rv, id, "This card has media / math / type-in.\nTap 'Open' to study it in the app.", showBtn = false, answerBtns = false)
            Report.log("C8", "widget $id: nid=${next.nid} is not a simple text card -> open app")
        } else {
            show(ctx, rv, id, qText, showBtn = true, answerBtns = false)
            Report.log("C8", "widget $id showing question nid=${next.nid} ord=${next.ord}")
        }
    }

    private fun show(ctx: Context, rv: RemoteViews, id: Int, text: String, showBtn: Boolean, answerBtns: Boolean, changeBtn: Boolean = false) {
        rv.setTextViewText(R.id.s_text, text)
        rv.setViewVisibility(R.id.s_show, if (showBtn) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.s_change, if (changeBtn) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.s_again, if (answerBtns) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.s_hard, if (answerBtns) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.s_good, if (answerBtns) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.s_easy, if (answerBtns) View.VISIBLE else View.GONE)
        AppWidgetManager.getInstance(ctx).updateAppWidget(id, rv)
    }
}
