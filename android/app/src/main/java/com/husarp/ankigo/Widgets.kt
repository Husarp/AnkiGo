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
import com.husarp.ankigo.ui.WidgetRender

object Widgets {
    fun refreshAll(ctx: Context) {
        for (cls in listOf(MotivationWidget::class.java, com.husarp.ankigo.ui.BuddyWidget::class.java, StudyWidget::class.java)) {
            val ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isNotEmpty()) ctx.sendBroadcast(Intent(ctx, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }

    fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, 0, Intent(ctx, com.husarp.ankigo.ui.GameActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

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

/** C8b: study widget — simple text card, Show answer, Again / Good. */
class StudyWidget : AppWidgetProvider() {
    companion object {
        const val SHOW = "com.husarp.ankigo.W_SHOW"
        const val ANSWER = "com.husarp.ankigo.W_ANSWER"
        const val NEXT = "com.husarp.ankigo.W_NEXT"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            SHOW, ANSWER, NEXT -> {
                Report.init(ctx); Anki.load(ctx)
                val pr = goAsync()
                Thread {
                    try {
                        handle(ctx, intent.action!!, intent.getIntExtra("ease", 0))
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
                handle(ctx, NEXT, 0)
            } catch (e: Exception) {
                Report.fail("C8", "study widget update", e)
            } finally {
                pr.finish()
            }
        }.start()
    }

    private fun handle(ctx: Context, action: String, ease: Int) {
        val p = ctx.getSharedPreferences("spike", 0)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_study)
        rv.setOnClickPendingIntent(R.id.s_show, Widgets.bcast(ctx, StudyWidget::class.java, SHOW, 1))
        rv.setOnClickPendingIntent(R.id.s_again, Widgets.bcast(ctx, StudyWidget::class.java, ANSWER, 2, 1))
        rv.setOnClickPendingIntent(R.id.s_hard, Widgets.bcast(ctx, StudyWidget::class.java, ANSWER, 6, 2))
        rv.setOnClickPendingIntent(R.id.s_good, Widgets.bcast(ctx, StudyWidget::class.java, ANSWER, 3, 3))
        rv.setOnClickPendingIntent(R.id.s_easy, Widgets.bcast(ctx, StudyWidget::class.java, ANSWER, 7, 4))
        rv.setOnClickPendingIntent(R.id.s_refresh, Widgets.bcast(ctx, StudyWidget::class.java, NEXT, 4))
        rv.setOnClickPendingIntent(R.id.s_open, PendingIntent.getActivity(ctx, 5,
            Intent(ctx, StudyActivity::class.java).putExtra("deckId", p.getLong("studyDeck", 0)).putExtra("deckName", p.getString("studyDeckName", "?"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

        if (!Anki.hasPermission(ctx)) {
            show(ctx, rv, "Open AnkiGo and grant the AnkiDroid permission first.", false, false); return
        }
        var deckId = p.getLong("studyDeck", 0)
        var deckName = p.getString("studyDeckName", null)
        if (deckId == 0L) {
            Anki.selectedDeck(ctx).firstOrNull()?.let { deckId = it["deck_id"]?.toLongOrNull() ?: 0; deckName = it["deck_name"] }
        }
        rv.setTextViewText(R.id.s_deck, deckName ?: "?")

        if (action == ANSWER) {
            val nid = p.getLong("w_nid", 0); val ord = p.getInt("w_ord", 0)
            val took = System.currentTimeMillis() - p.getLong("w_start", System.currentTimeMillis())
            if (nid == 0L) return
            val n = Anki.answer(ctx, nid, ord, ease, took, deckId)
            Local.recordReview(ctx, nid, ord, ease, took, "widget")
            Report.log("C8", "widget answer nid=$nid ord=$ord ease=$ease took=${took}ms rows=$n")
            Widgets.refreshAll(ctx) // motivation widget count
        }

        if (action == SHOW) {
            val nid = p.getLong("w_nid", 0); val ord = p.getInt("w_ord", 0)
            val c = Anki.card(ctx, nid, ord, arrayOf("question", "answer", "answer_pure"))
            val ans = Widgets.plain(c?.get("answer_pure") ?: c?.get("answer") ?: "?")
            show(ctx, rv, p.getString("w_q", "") + "\n―――\n" + ans, false, true)
            Report.log("C8", "widget show answer nid=$nid")
            return
        }

        // NEXT (or after answer): fetch next card
        val next = Anki.nextCards(ctx, deckId, 1).firstOrNull()
        if (next == null) {
            show(ctx, rv, "No cards due in this deck. 🎉", false, false); return
        }
        val c = Anki.card(ctx, next.nid, next.ord, arrayOf("question", "answer"))
        val qHtml = c?.get("question") ?: ""
        val complex = next.media.isNotEmpty() || qHtml.contains("[[type:") || qHtml.contains("<img", true) ||
            qHtml.contains("\\(") || qHtml.contains("image-occlusion", true) || qHtml.contains("[anki:play")
        val qText = Widgets.plain(qHtml)
        p.edit().putLong("w_nid", next.nid).putInt("w_ord", next.ord).putLong("w_start", System.currentTimeMillis()).putString("w_q", qText).apply()
        if (complex) {
            show(ctx, rv, "This card has media / math / type-in.\nTap 'Open' to study it in the app.", false, false)
            Report.log("C8", "widget: nid=${next.nid} is not a simple text card -> open app")
        } else {
            show(ctx, rv, qText, true, false)
            Report.log("C8", "widget showing question nid=${next.nid} ord=${next.ord}")
        }
    }

    private fun show(ctx: Context, rv: RemoteViews, text: String, showBtn: Boolean, answerBtns: Boolean) {
        rv.setTextViewText(R.id.s_text, text)
        rv.setViewVisibility(R.id.s_show, if (showBtn) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.s_again, if (answerBtns) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.s_hard, if (answerBtns) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.s_good, if (answerBtns) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.s_easy, if (answerBtns) View.VISIBLE else View.GONE)
        val mgr = AppWidgetManager.getInstance(ctx)
        mgr.getAppWidgetIds(ComponentName(ctx, StudyWidget::class.java)).forEach { mgr.updateAppWidget(it, rv) }
    }
}
