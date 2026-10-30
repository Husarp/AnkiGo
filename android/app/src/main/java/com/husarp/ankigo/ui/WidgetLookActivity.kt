package com.husarp.ankigo.ui

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.*
import com.husarp.ankigo.game.shownName
import com.husarp.ankigo.game.lookNum

/** Shared Look / Content page; the preview is outside the scrolling rows. */
class WidgetLookActivity : AgActivity() {
    override val showsUpdateBanner = false
    private var kind = "buddy"
    private var widgetId = 0
    private var tab = 0
    private var due: Int? = null
    private lateinit var rows: LinearLayout
    private lateinit var preview: ImageView
    private val prefs get() = getSharedPreferences("spike", 0)
    private fun look() = WidgetLooks.read(prefs, kind, widgetId)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        kind = intent.getStringExtra("kind") ?: "buddy"
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        if (!intent.hasExtra("kind") && widgetId > 0) {
            val className = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className.orEmpty()
            kind = when { className.endsWith("MotivationWidget") -> "wide"; className.endsWith("StudyNowWidget") -> "now"; className.endsWith("StudyWidget") -> "study"; else -> "buddy" }
        }
        if (widgetId < 0) { finish(); return }
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (kind !in names) { finish(); return }
        setContentView(buildContent())
        GameRepo.load(this) { if (!isDestroyed) render() }
        Thread { val read = WidgetDue.read(this); runOnUiThread { if (!isDestroyed) { due=read; renderPreview() } } }.start()
    }
    internal fun buildContent(): View {
        val root = StudyUi.column(this).apply { setBackgroundColor(Ui.c(context, R.color.ag_base)) }
        root.addView(Ui.appBar(this, names.getValue(kind), "", back=true))
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Ui.c(context, R.color.ag_mantle))
            contentDescription = "Live widget preview"
        }
        root.addView(preview, StudyUi.lp(-1, Ui.dp(this, 160)))
        root.addView(Form.segmented(this, listOf("Look", "Content"), tab) { tab=it; render() })
        rows = StudyUi.column(this).apply { setPadding(Ui.dp(context,12),0,Ui.dp(context,12),Ui.dp(context,16)) }
        root.addView(ScrollView(this).apply { addView(rows) }, StudyUi.lp(-1,0,1f))
        render()
        return root
    }
    override fun onResume() { super.onResume(); if (::rows.isInitialized) render() }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refresh = Runnable { WidgetLooks.refresh(this,kind,widgetId) }
    private fun save(value: WidgetLook) {
        WidgetLooks.write(prefs, kind, widgetId, value)
        if (kind=="buddy" && BuddyWidgetPrefs.ring(prefs,widgetId).mode==BuddyRing.Mode.PICK && value.accent!="goal")
            BuddyWidgetPrefs.setRing(prefs,widgetId,BuddyRing(BuddyRing.Mode.PICK,value.accent))
        handler.removeCallbacks(refresh); handler.postDelayed(refresh, 200)
        renderPreview()
    }
    override fun onDestroy() { handler.removeCallbacks(refresh); WidgetLooks.refresh(this,kind,widgetId); super.onDestroy() }
    private fun renderPreview() {
        val appearance = look()
        val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val saved = WidgetRender.snapshot(this, setOf(BuddyWidgetPrefs.choice(prefs,widgetId)))
            ?: if (kind=="now") WidgetSnapshot(null,false,0,0,0,false,0,0,emptyList()) else null
        val snap = saved?.forBuddy(BuddyWidgetPrefs.choice(prefs,widgetId))?.copy(due=due)
        if (kind == "study") {
            preview.setImageBitmap(studyPreview(this, widgetId, appearance, night))
        } else {
            val k = when(kind) { "wide" -> WidgetRender.Kind.WIDE; "now" -> WidgetRender.Kind.STUDY_NOW; else -> WidgetRender.Kind.BUDDY }
            preview.setImageBitmap(WidgetRender.render(this,k,snap,if(kind=="wide")286f else 140f,140f,night,
                look=BuddyWidgetPrefs.look(prefs,widgetId),appearance=appearance))
        }
    }
    private fun transparencyRow(): View {
        val value=StudyUi.label(this,"${look().transparency}%",13,R.color.ag_sub1)
        val slider=android.widget.SeekBar(this).apply {
            max=100; progress=look().transparency; minimumHeight=Ui.dp(context,40)
            contentDescription="Background see-through"
            progressTintList=android.content.res.ColorStateList.valueOf(Ui.c(context,R.color.ag_mauve))
            thumbTintList=progressTintList
            setOnSeekBarChangeListener(object: android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                    value.text="$progress%"
                    if(fromUser)save(look().copy(transparency=progress))
                }
                override fun onStartTrackingTouch(bar: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(bar: android.widget.SeekBar) {}
            })
        }
        return Form.stackRow(this,"Background see-through","0% is solid, 100% shows only the wallpaper",slider,value)
    }
    private fun render() {
        rows.removeAllViews(); renderPreview()
        if (tab == 0) {
            val options=StudyUi.column(this).apply { background=Ui.rounded(Ui.c(context,R.color.ag_card),16,context) }
            rows.addView(options,StudyUi.spaced(this,8))
            options.addView(transparencyRow())
            options.addView(Form.stackRow(this,"Colours","Phone follows the phone's light or dark mode",
                Form.segmented(this,listOf("Phone","Light","Dark"),listOf("phone","light","dark").indexOf(look().theme).coerceAtLeast(0)) { save(look().copy(theme=listOf("phone","light","dark")[it])) }))
            val accents = listOf("goal")+BuddyRing.PICKS
            options.addView(Form.stackRow(this,"Accent colour","The goal ring, bars and highlights",Form.swatches(this,
                accents.map { it to Ui.c(this,if(it=="goal")R.color.ag_sub0 else BuddyRing.pickRes(it)) },accents.indexOf(look().accent)) { save(look().copy(accent=accents[it])); render() }))
            options.addView(Form.stackRow(this,"Card tint","The colour behind everything",Form.segmented(this,listOf("Plain","Type colour","Preset"),when(look().tint){"plain"->0;"type"->1;else->2}) {
                if (it==2) AlertDialog.Builder(this).setTitle("Card tint").setItems(BuddyRing.PICKS.toTypedArray()) { _,i -> save(look().copy(tint=BuddyRing.PICKS[i])) }.show()
                else save(look().copy(tint=if(it==0)"plain" else "type"))
            }))
            if (kind=="buddy") {
                options.addView(Form.stackRow(this,"Buddy layout","A: ring · B: goal bar · C: open gauge",Form.segmented(this,listOf("A","B","C"),listOf("A","B","C").indexOf(BuddyWidgetPrefs.layout(prefs,widgetId))) {
                    BuddyWidgetPrefs.setLayout(prefs,widgetId,listOf("A","B","C")[it]); Widgets.refreshAll(this,study=false); renderPreview()
                }))
                options.addView(Form.stackRow(this,"Progress colour","Goal colours, the Pokémon's type colour, or the accent colour",Form.segmented(this,listOf("Goal","Type","One colour"),BuddyWidgetPrefs.ring(prefs,widgetId).mode.ordinal) {
                    BuddyWidgetPrefs.setRing(prefs,widgetId,when(it){0->BuddyRing.APP;1->BuddyRing(BuddyRing.Mode.TYPE);else->BuddyRing(BuddyRing.Mode.PICK,look().accent.takeIf { c -> c!="goal" } ?: "mauve")}); Widgets.refreshAll(this,study=false); renderPreview()
                }))
                options.addView(Form.switchRow(this,"Moving Pokémon","Plays its real animation while the home screen is on; still with reduced motion",BuddyWidgetPrefs.anim(prefs,widgetId)) {
                    BuddyWidgetPrefs.setAnim(prefs,widgetId,it); Widgets.refreshAll(this,study=false)
                })
            }
        } else {
            when(kind) {
                "buddy" -> {
                    val choice=BuddyWidgetPrefs.choice(prefs,widgetId)
                    val mon = GameRepo.state?.let { if(choice==BuddyWidgetPrefs.MAIN)it.main else it.mon(choice) ?: it.main }
                    val where=when { mon==null -> "Follows your team"; mon.uid==GameRepo.state?.main?.uid -> "Main"; mon.uid in (GameRepo.state?.team ?: emptyList()) -> "Team"; else -> "Box" }
                    val summary=Form.row(this,mon?.shownName ?: "Current main",mon?.let { "Lv ${it.level} · $where · tap to change" } ?: "Follows your team · tap to change",StudyUi.label(this,"›",22)) {
                        showPokemonSheet()
                    }
                    if(mon!=null) {
                        val image=ImageView(this)
                        Ui.staticSprite(this,mon.lookNum(GameRepo.data),mon.shiny,image)
                        summary.addView(image,0,StudyUi.lp(Ui.dp(this,40),Ui.dp(this,40)))
                    }
                    rows.addView(Form.cardOf(this,listOf(summary)))
                }
                "study" -> rows.addView(StudyUi.primary(this,"Choose the deck this widget studies") { startActivity(Intent(this,StudyWidgetSetupActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId).putExtra("pickerOnly",true)) })
                "now" -> rows.addView(StudyUi.label(this,"Shows the number of cards due. Tap opens AnkiGo Study."))
                else -> rows.addView(StudyUi.label(this,"Shows the next wild Pokémon, today's goal and your team. Tap opens Study or Battle."))
            }
        }
        rows.addView(StudyUi.primary(this,"Add to home screen") {
            val mgr=AppWidgetManager.getInstance(this)
            if (android.os.Build.VERSION.SDK_INT>=26 && mgr.isRequestPinAppWidgetSupported) {
                for (id in mgr.getAppWidgetIds(provider(this,kind))) {
                    WidgetLooks.write(prefs,kind,id,WidgetLooks.read(prefs,kind,id))
                    if (kind=="buddy") {
                        val old=BuddyWidgetPrefs.look(prefs,id)
                        val choice=BuddyWidgetPrefs.choice(prefs,id)
                        BuddyWidgetPrefs.setLayout(prefs,id,old.layout); BuddyWidgetPrefs.setRing(prefs,id,old.ring)
                        BuddyWidgetPrefs.setAnim(prefs,id,old.hop); BuddyWidgetPrefs.set(prefs,id,choice)
                    }
                }
                WidgetLooks.write(prefs,kind,0,look())
                if(kind=="buddy") {
                    BuddyWidgetPrefs.setLayout(prefs,0,BuddyWidgetPrefs.layout(prefs,widgetId))
                    BuddyWidgetPrefs.setRing(prefs,0,BuddyWidgetPrefs.ring(prefs,widgetId)); BuddyWidgetPrefs.setAnim(prefs,0,BuddyWidgetPrefs.anim(prefs,widgetId))
                    BuddyWidgetPrefs.set(prefs,0,BuddyWidgetPrefs.choice(prefs,widgetId))
                }
                mgr.requestPinAppWidget(provider(this,kind),null,WidgetPinReceiver.callback(this,kind,widgetId,look()))
            } else android.widget.Toast.makeText(this,"Long-press the home screen → Widgets → AnkiGo",android.widget.Toast.LENGTH_LONG).show()
        },StudyUi.spaced(this,16))
        rows.addView(StudyUi.primary(this,"Done") { finish() },StudyUi.spaced(this,8))
    }
    /** C keeps the page compact; the existing Main / Team / Box choices live in a separate sheet. No item-151 filters. */
    private fun showPokemonSheet() {
        val state=GameRepo.state
        val mons=state?.let { it.teamMons()+it.boxMons().sortedWith(compareByDescending<com.husarp.ankigo.game.Mon>{ m -> m.level }.thenBy { m -> m.shownName }) } ?: emptyList()
        val choices=listOf(BuddyWidgetPrefs.MAIN)+mons.map { it.uid }
        val labels=listOf("Current main — follows your team")+mons.map { mon ->
            val where=if(mon.uid in state!!.team) "Team" else "Box"
            "${if(mon.shiny) "★ " else ""}${mon.shownName} · Lv ${mon.level} · $where"
        }
        val current=choices.indexOf(BuddyWidgetPrefs.choice(prefs,widgetId)).coerceAtLeast(0)
        val dialog=AlertDialog.Builder(this).setTitle("Pokémon")
            .setSingleChoiceItems(labels.toTypedArray(),current) { sheet,index ->
                BuddyWidgetPrefs.set(prefs,widgetId,choices[index])
                Widgets.refreshAll(this,study=false)
                sheet.dismiss(); render()
            }.setNegativeButton("Cancel",null).create()
        dialog.show()
        dialog.window?.apply {
            setGravity(android.view.Gravity.BOTTOM)
            setLayout(-1,minOf((resources.displayMetrics.heightPixels*.8f).toInt(),Ui.dp(this@WidgetLookActivity,100+labels.size.coerceAtMost(12)*56)))
        }
    }
    companion object {
        val names = linkedMapOf("buddy" to "Buddy","wide" to "A wild one is waiting","study" to "Study","now" to "Study now")
        fun studyPreview(ctx: Context, id: Int, appearance: WidgetLook, night: Boolean): android.graphics.Bitmap {
            val themed = WidgetLooks.context(ctx,appearance,night)
            val rv = android.widget.RemoteViews(ctx.packageName, R.layout.widget_study)
            WidgetLooks.study(ctx,rv,id)
            WidgetLooks.studyDeck(rv, StudyWidgetPrefs.deckName(ctx.getSharedPreferences("spike",0),id) ?: "Study")
            val view=rv.apply(themed,null)
            val w=Ui.dp(ctx,286); val h=Ui.dp(ctx,180)
            view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY)); view.layout(0,0,w,h)
            val bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap)); return bitmap
        }
        fun provider(ctx: Context,kind: String) = ComponentName(ctx,when(kind){"wide"->MotivationWidget::class.java;"study"->StudyWidget::class.java;"now"->StudyNowWidget::class.java;else->BuddyWidget::class.java})
        fun open(ctx: Context,kind: String,id: Int=0) { ctx.startActivity(Intent(ctx,WidgetLookActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("kind",kind).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)) }
        fun tiles(ctx: Context): View = StudyUi.column(ctx).apply {
            addView(StudyUi.section(ctx,"Your widgets"))
            names.entries.chunked(2).forEach { entries ->
                val row=StudyUi.row(ctx).apply { gravity=android.view.Gravity.TOP }
                entries.forEach { (kind,name) ->
                    val tile=StudyUi.column(ctx).apply {
                        setPadding(Ui.dp(ctx,8),Ui.dp(ctx,8),Ui.dp(ctx,8),Ui.dp(ctx,8))
                        minimumHeight=Ui.dp(ctx,175)
                        background=Ui.rounded(Ui.c(ctx,R.color.ag_card),16,ctx)
                    }
                    val ids=runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetIds(provider(ctx,kind)) }.getOrDefault(intArrayOf())
                    val id=ids.firstOrNull()?:0
                    val p=ctx.getSharedPreferences("spike",0)
                    val snap=WidgetRender.snapshot(ctx,setOf(BuddyWidgetPrefs.choice(p,id)))?.forBuddy(BuddyWidgetPrefs.choice(p,id))
                    val night=ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    val img=ImageView(ctx).apply { scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription=name }
                    val k=when(kind){"wide"->WidgetRender.Kind.WIDE;"now"->WidgetRender.Kind.STUDY_NOW;else->WidgetRender.Kind.BUDDY}
                    if(kind=="study") img.setImageBitmap(studyPreview(ctx,ids.firstOrNull()?:0,WidgetLooks.read(ctx.getSharedPreferences("spike",0),kind,ids.firstOrNull()?:0),night)) else img.setImageBitmap(WidgetRender.render(ctx,k,snap,if(kind=="wide")286f else 140f,140f,night,look=BuddyWidgetPrefs.look(p,id),appearance=WidgetLooks.read(p,kind,id)))
                    tile.addView(img,StudyUi.lp(-1,Ui.dp(ctx,100))); tile.addView(StudyUi.label(ctx,name,14)); tile.addView(StudyUi.label(ctx,"${ids.size} on your home screen",11))
                    tile.isFocusable=true; tile.contentDescription="$name, ${ids.size} on your home screen, settings"
                    tile.setOnClickListener { if(ids.size<=1)open(ctx,kind,ids.firstOrNull()?:0) else AlertDialog.Builder(ctx).setTitle(name).setItems(ids.mapIndexed { i,_ -> "Widget ${i+1}" }.toTypedArray()) { _,i -> open(ctx,kind,ids[i]) }.show() }
                    row.addView(tile,StudyUi.lp(0,-2,1f).apply { setMargins(Ui.dp(ctx,4),Ui.dp(ctx,4),Ui.dp(ctx,4),Ui.dp(ctx,4)) })
                }
                addView(row)
            }
        }
    }
}
