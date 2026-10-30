package com.husarp.ankigo.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.husarp.ankigo.Widgets

/** Pin completion supplies the allocated id; copy the exact settings the user previewed, including its content. */
class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
        val kind=intent.getStringExtra("kind") ?: return
        if(id<=0 || kind !in WidgetLookActivity.names) return
        if(AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)?.provider != WidgetLookActivity.provider(ctx,kind)) return
        val p=ctx.getSharedPreferences("spike",0)
        WidgetLooks.write(p,kind,id,WidgetLook(intent.getIntExtra("alpha",0),intent.getStringExtra("theme")?:"phone",
            intent.getStringExtra("accent")?:"goal",intent.getStringExtra("tint")?:"plain"))
        if(kind=="buddy") {
            BuddyWidgetPrefs.set(p,id,intent.getStringExtra("mon")?:BuddyWidgetPrefs.MAIN)
            BuddyWidgetPrefs.setLayout(p,id,intent.getStringExtra("layout")?:"A")
            BuddyWidgetPrefs.setRing(p,id,BuddyRing.parse(intent.getStringExtra("ring")))
            BuddyWidgetPrefs.setAnim(p,id,intent.getBooleanExtra("moving",false))
        }
        if(kind=="study") StudyWidgetPrefs.setDeck(p,id,intent.getLongExtra("deck",0L),intent.getStringExtra("deckName")?:"")
        if (kind=="study") com.husarp.ankigo.StudyWidget.requestUpdate(ctx,id) else Widgets.refreshAll(ctx,study=false)
    }
    companion object {
        fun callback(ctx: Context, kind: String, id: Int, look: WidgetLook): PendingIntent {
            val p=ctx.getSharedPreferences("spike",0)
            val intent=Intent(ctx,WidgetPinReceiver::class.java).putExtra("kind",kind)
                .putExtra("alpha",look.transparency).putExtra("theme",look.theme).putExtra("accent",look.accent).putExtra("tint",look.tint)
                .putExtra("mon",BuddyWidgetPrefs.choice(p,id)).putExtra("layout",BuddyWidgetPrefs.layout(p,id))
                .putExtra("ring",BuddyWidgetPrefs.ring(p,id).format()).putExtra("moving",BuddyWidgetPrefs.anim(p,id))
                .putExtra("deck",StudyWidgetPrefs.deckId(p,id)).putExtra("deckName",StudyWidgetPrefs.deckName(p,id))
            return PendingIntent.getBroadcast(ctx,System.nanoTime().toInt(),intent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        }
    }
}
