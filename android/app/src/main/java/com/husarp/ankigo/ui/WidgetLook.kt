package com.husarp.ankigo.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import com.husarp.ankigo.R

/** Shared, per-instance widget appearance. Id 0 stores the next widget's defaults. */
data class WidgetLook(val transparency: Int = 0, val theme: String = "phone", val accent: String = "goal", val tint: String = "plain") {
    fun dark(phone: Boolean) = when (theme) { "light" -> false; "dark" -> true; else -> phone }
    fun background(ctx: Context, type: com.husarp.ankigo.game.Type? = null): Int {
        val base = when (tint) {
            "type" -> type?.let { Ui.typeColor(it) } ?: Ui.c(ctx, R.color.ag_base)
            "plain" -> Ui.c(ctx, R.color.ag_base)
            else -> Ui.c(ctx, BuddyRing.pickRes(tint))
        }
        return (base and 0xffffff) or (((255 * (100 - transparency)) / 100) shl 24)
    }
}
object WidgetLooks {
    fun key(kind: String, id: Int) = "widget.$kind.$id."
    fun read(p: SharedPreferences, kind: String, id: Int): WidgetLook {
        val k = key(kind, if (p.contains(key(kind, id) + "theme")) id else 0)
        return WidgetLook(p.getInt(k + "alpha", 0).coerceIn(0, 100), p.getString(k + "theme", "phone") ?: "phone",
            p.getString(k + "accent", "goal") ?: "goal", p.getString(k + "tint", "plain") ?: "plain")
    }
    fun write(p: SharedPreferences, kind: String, id: Int, look: WidgetLook) {
        val k = key(kind, id)
        p.edit().putInt(k + "alpha", look.transparency).putString(k + "theme", look.theme)
            .putString(k + "accent", look.accent).putString(k + "tint", look.tint).apply()
    }
    fun forget(p: SharedPreferences, kind: String, ids: IntArray) {
        val ed=p.edit()
        for (id in ids) for (field in listOf("alpha","theme","accent","tint")) ed.remove(key(kind,id)+field)
        ed.apply()
    }
    /** A look change must retain the Study widget's current question/answer and timer. */
    fun refresh(ctx: Context, kind: String, id: Int) {
        if (kind == "study" && id > 0) {
            val rv=RemoteViews(ctx.packageName,R.layout.widget_study)
            study(ctx,rv,id)
            android.appwidget.AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(id,rv)
        } else if (kind != "study") com.husarp.ankigo.Widgets.refreshAll(ctx,study=false)
    }
    fun context(ctx: Context, look: WidgetLook, phone: Boolean): Context {
        val cfg = Configuration(ctx.resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (look.dark(phone)) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return ctx.createConfigurationContext(cfg)
    }
    fun surface(ctx: Context, look: WidgetLook, w: Int = 160, h: Int = 160, type: com.husarp.ankigo.game.Type? = null): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 24f, 24f, Paint(3).apply { color = look.background(ctx, type) })
        return b
    }
    fun studyText(rv: RemoteViews, text: CharSequence) {
        rv.setTextViewText(R.id.s_text,text); rv.setTextViewText(R.id.s_text_d,text)
    }
    fun studyDeck(rv: RemoteViews, text: CharSequence) {
        rv.setTextViewText(R.id.s_deck,text); rv.setTextViewText(R.id.s_deck_d,text)
    }
    fun study(ctx: Context, rv: RemoteViews, id: Int) {
        val look = read(ctx.getSharedPreferences("spike", 0), "study", id)

        val type=if(look.tint=="type") WidgetRender.snapshot(ctx)?.buddyMon?.type else null
        val options=runCatching { android.appwidget.AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id) }.getOrNull()
        val w=(options?.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,286)?:286).coerceIn(40,600)
        val h=(options?.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,180)?:180).coerceIn(40,600)
        for (night in listOf(false,true)) {
            val themed=context(ctx,look,night)
            rv.setImageViewBitmap(if(night)R.id.s_surface_d else R.id.s_surface,surface(themed,look,w,h,type))
            rv.setTextColor(if(night)R.id.s_text_d else R.id.s_text,Ui.c(themed,R.color.ag_text))
            rv.setTextColor(if(night)R.id.s_deck_d else R.id.s_deck,Ui.c(themed,if(look.accent=="goal")R.color.ag_sub0 else BuddyRing.pickRes(look.accent)))
        }
    }
}
