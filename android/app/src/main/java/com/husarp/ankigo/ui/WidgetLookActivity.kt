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
        root.addView(MotionScrollView(this).apply { addView(rows) }, StudyUi.lp(-1,0,1f))
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
            max=20; progress=(look().transparency+2)/5; minimumHeight=Ui.dp(context,40)
            contentDescription="Background see-through"
            progressTintList=android.content.res.ColorStateList.valueOf(Ui.c(context,R.color.ag_mauve))
            thumbTintList=progressTintList
            setOnSeekBarChangeListener(object: android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                    value.text="${progress*5}%"
                    if(fromUser)save(look().copy(transparency=progress*5))
                }
                override fun onStartTrackingTouch(bar: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(bar: android.widget.SeekBar) {}
            })
        }
        return Form.stackRow(this,"Background see-through","Make the widget background see-through: 0% is solid and 100% shows your wallpaper",slider,value)
    }
    private fun render() {
        rows.removeAllViews(); renderPreview()
        if (tab == 0) {
            val options=StudyUi.column(this).apply { background=Ui.rounded(Ui.c(context,R.color.ag_card),16,context) }
            rows.addView(options,StudyUi.spaced(this,8))
            rows.addView(Form.sect(this,"Colours & background"), 0)
            options.addView(transparencyRow())
            options.addView(Form.stackRow(this,"Colours","Choose light or dark widget colours, or follow your phone’s theme",
                Form.segmented(this,listOf("Phone","Light","Dark"),listOf("phone","light","dark").indexOf(look().theme).coerceAtLeast(0)) { save(look().copy(theme=listOf("phone","light","dark")[it])) }))
            val accents = listOf("goal")+BuddyRing.PICKS
            options.addView(Form.stackRow(this,"Accent colour","Choose the colour of the widget’s goal ring, bars and highlights",Form.swatches(this,
                accents.map { it to Ui.c(this,if(it=="goal")R.color.ag_sub0 else BuddyRing.pickRes(it)) },accents.indexOf(look().accent)) { save(look().copy(accent=accents[it])); render() }))
            options.addView(Form.stackRow(this,"Card tint","Colour the widget background with a plain fill, your Pokémon’s type or a preset",Form.segmented(this,listOf("Plain","Type colour","Preset"),when(look().tint){"plain"->0;"type"->1;else->2}) {
                if (it==2) AlertDialog.Builder(this).setTitle("Card tint").setItems(BuddyRing.PICKS.toTypedArray()) { _,i -> save(look().copy(tint=BuddyRing.PICKS[i])) }.show()
                else save(look().copy(tint=if(it==0)"plain" else "type"))
            }))
            if (kind=="buddy") {
                options.addView(Form.sect(this,"Buddy layout & movement"))
                options.addView(sizeRow())
                options.addView(Form.stackRow(this,"Buddy layout","Show the Buddy’s progress as a ring (A), goal bar (B) or open gauge (C)",Form.segmented(this,listOf("A","B","C"),listOf("A","B","C").indexOf(BuddyWidgetPrefs.layout(prefs,widgetId))) {
                    BuddyWidgetPrefs.setLayout(prefs,widgetId,listOf("A","B","C")[it]); Widgets.refreshAll(this,study=false); renderPreview()
                }))
                options.addView(Form.stackRow(this,"Progress colour","Colour the Buddy’s progress with goal colours, its Pokémon’s type or your accent",Form.segmented(this,listOf("Goal","Type","One colour"),BuddyWidgetPrefs.ring(prefs,widgetId).mode.ordinal) {
                    BuddyWidgetPrefs.setRing(prefs,widgetId,when(it){0->BuddyRing.APP;1->BuddyRing(BuddyRing.Mode.TYPE);else->BuddyRing(BuddyRing.Mode.PICK,look().accent.takeIf { c -> c!="goal" } ?: "mauve")}); Widgets.refreshAll(this,study=false); renderPreview()
                }))
                options.addView(Form.switchRow(this,"Moving Pokémon","Animate the Buddy Pokémon while the home screen is visible; reduced motion keeps it still",BuddyWidgetPrefs.anim(prefs,widgetId)) {
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
                        BuddyWidgetPrefs.setSize(prefs,id,old.size); BuddyWidgetPrefs.setLayout(prefs,id,old.layout); BuddyWidgetPrefs.setRing(prefs,id,old.ring)
                        BuddyWidgetPrefs.setAnim(prefs,id,old.hop); BuddyWidgetPrefs.set(prefs,id,choice)
                    }
                }
                WidgetLooks.write(prefs,kind,0,look())
                if(kind=="buddy") {
                    BuddyWidgetPrefs.setSize(prefs,0,BuddyWidgetPrefs.size(prefs,widgetId))
                    BuddyWidgetPrefs.setLayout(prefs,0,BuddyWidgetPrefs.layout(prefs,widgetId))
                    BuddyWidgetPrefs.setRing(prefs,0,BuddyWidgetPrefs.ring(prefs,widgetId)); BuddyWidgetPrefs.setAnim(prefs,0,BuddyWidgetPrefs.anim(prefs,widgetId))
                    BuddyWidgetPrefs.set(prefs,0,BuddyWidgetPrefs.choice(prefs,widgetId))
                }
                mgr.requestPinAppWidget(provider(this,kind),null,WidgetPinReceiver.callback(this,kind,widgetId,look()))
            } else android.widget.Toast.makeText(this,"Long-press the home screen → Widgets → AnkiGo",android.widget.Toast.LENGTH_LONG).show()
        },StudyUi.spaced(this,16))
        rows.addView(StudyUi.primary(this,"Done") { finish() },StudyUi.spaced(this,8))
    }
    private fun sizeRow(): View {
        val value=StudyUi.label(this,"${BuddyWidgetPrefs.size(prefs,widgetId)}%",13,R.color.ag_sub1)
        val slider=android.widget.SeekBar(this).apply {
            max=50; progress=BuddyWidgetPrefs.size(prefs,widgetId)-50
            contentDescription="Pokémon size"
            progressTintList=android.content.res.ColorStateList.valueOf(Ui.c(context,R.color.ag_mauve))
            thumbTintList=progressTintList
            minimumHeight=Ui.dp(context,40)
            setOnSeekBarChangeListener(object: android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                    value.text="${progress+50}%"
                    if(fromUser) {
                        BuddyWidgetPrefs.setSize(prefs,widgetId,progress+50)
                        handler.removeCallbacks(refresh); handler.postDelayed(refresh,200)
                        renderPreview()
                    }
                }
                override fun onStartTrackingTouch(bar: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(bar: android.widget.SeekBar) {}
            })
        }
        return Form.stackRow(this,"Pokémon size","Resize the Buddy Pokémon within the widget",slider,value)
    }
    internal fun showPokemonSheet(show: Boolean = true): View {
        val state=GameRepo.state
        val mons=state?.let { it.teamMons()+it.boxMons() } ?: emptyList()
        val selected=BuddyWidgetPrefs.choice(prefs,widgetId)
        var sort=BuddyPicker.Sort.LEVEL
        var filters=BuddyPicker.Filters()
        val body=StudyUi.column(this)
        val search=android.widget.EditText(this).apply { hint="Search Pokémon name"; isSingleLine=true; contentDescription=hint }
        body.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,8))
        search.setTextColor(Ui.c(this,R.color.ag_text)); search.setHintTextColor(Ui.c(this,R.color.ag_sub0))
        Ui.tintEdit(search)
        val chips=StudyUi.row(this)
        val list=MotionListView(this).apply { divider=null; setBackgroundColor(Ui.c(context,R.color.ag_card)) }
        var choices=emptyList<String>()
        val dialog=AppDialog.Builder(this).setTitle("Pokémon").setView(body, scrollsItself=true).setNegativeButton("Cancel",null).create()
        fun update() {
            val found=BuddyPicker.mons(mons,selected,search.text.toString(),sort,filters,state?.team ?: emptyList()) {
                GameRepo.data?.species?.get(it.lookNum(GameRepo.data))?.types ?: emptyList()
            }
            chips.removeAllViews()
            chips.addView(BoxViews.chip(this,"Type${if(filters.types.active) " (${filters.types.types.size})" else ""}",filters.types.active) {
                val content=StudyUi.column(this).apply { setPadding(Ui.dp(context,16),0,Ui.dp(context,16),Ui.dp(context,12)) }
                val note=StudyUi.label(this,filters.types.sentence(),12,R.color.ag_sub1)
                content.addView(note)
                content.addView(ChipFlow(this,Ui.dp(this,6),-Ui.dp(this,8)).apply {
                    for(type in com.husarp.ankigo.game.Type.entries) addView(Ui.text(context,12f,800,R.color.ag_sub1).apply {
                        text=type.label
                        TypeChips.styleFilter(this,type,type in filters.types.types)
                        isSelected=type in filters.types.types
                        setOnClickListener {
                            filters=filters.copy(types=filters.types.toggle(type)!!)
                            isSelected=type in filters.types.types
                            TypeChips.styleFilter(this,type,isSelected)
                            note.text=filters.types.sentence()
                            update()
                        }
                    })
                })
                AlertDialog.Builder(this).setTitle("Type · any of").setView(MotionScrollView(this).apply { addView(content) })
                    .setPositiveButton("Done",null).setNeutralButton("Clear") { _,_ -> filters=filters.copy(types=TypeFilter()); update() }.show()
            })
            chips.addView(BoxViews.quickChip(this,"Favourites",filters.favourites,heart=true) { filters=filters.copy(favourites=!filters.favourites); update() })
            chips.addView(BoxViews.quickChip(this,"Shiny",filters.shiny) { filters=filters.copy(shiny=!filters.shiny); update() })
            val follow=!filters.active && (search.text.isNullOrBlank() || "Current main".contains(search.text.toString(),ignoreCase=true))
            choices=(if(follow)listOf(BuddyWidgetPrefs.MAIN) else emptyList())+found.map { it.uid }
            val labels=(if(follow)listOf("Current main — follows your team") else emptyList())+found.map {
                "${if(it.shiny) "★ " else ""}${it.shownName} · Lv ${it.level} · ${if(it.uid in state!!.team) "Team" else "Box"}"
            }
            // An explicitly picked Pokémon leads even the follow-main option.
            val index=choices.indexOf(selected)
            val order=choices.indices.toMutableList().apply { if(index>0) { remove(index); add(0,index) } }
            choices=order.map { choices[it] }
            val ordered=order.map { labels[it] }
            list.adapter=object: android.widget.BaseAdapter() {
                override fun getCount()=choices.size
                override fun getItem(position: Int)=choices[position]
                override fun getItemId(position: Int)=position.toLong()
                override fun getView(position: Int, recycled: View?, parent: android.view.ViewGroup): View {
                    val mon=mons.firstOrNull { it.uid==choices[position] }
                    val row=StudyUi.row(this@WidgetLookActivity).apply {
                        gravity=android.view.Gravity.CENTER_VERTICAL
                        minimumHeight=Ui.dp(context,64)
                        setPadding(Ui.dp(context,8),Ui.dp(context,4),Ui.dp(context,8),Ui.dp(context,4))
                        if(choices[position]==selected) background=Ui.rounded(Ui.c(context,R.color.ag_mantle),12,context)
                    }
                    if(mon!=null) row.addView(ImageView(this@WidgetLookActivity).apply { Ui.staticSprite(context,mon.lookNum(GameRepo.data),mon.shiny,this) },StudyUi.lp(Ui.dp(this@WidgetLookActivity,48),Ui.dp(this@WidgetLookActivity,48)))
                    row.addView(StudyUi.column(this@WidgetLookActivity).apply {
                        addView(StudyUi.label(context,mon?.shownName ?: "Current main",14))
                        addView(StudyUi.label(context,if(mon==null) "Follows your team" else "Lv ${mon.level} · ${if(mon.uid in state!!.team) "Team" else "Box"}${if(mon.favourite) " · Favourite" else ""}${if(mon.shiny) " · Shiny" else ""}",12,R.color.ag_sub1))
                        if(mon!=null) addView(StudyUi.row(context).apply {
                            for(type in GameRepo.data?.species?.get(mon.lookNum(GameRepo.data))?.types ?: emptyList())
                                addView(TypeChips.chip(context,type),StudyUi.lp(-2,-2).apply { marginEnd=Ui.dp(context,4) })
                        })
                    },StudyUi.lp(0,-2,1f))
                    if(choices[position]==selected) row.addView(StudyUi.label(this@WidgetLookActivity,"Selected",11,R.color.ag_mauve))
                    row.contentDescription=ordered[position]+if(choices[position]==selected) ", selected" else ""
                    return row
                }
            }
            list.setItemChecked(choices.indexOf(selected),true)
        }
        body.addView(search)
        body.addView(Form.segmented(this,listOf("Level","Name","Recently caught"),0) { sort=BuddyPicker.Sort.values()[it]; update() })
        body.addView(Form.segmented(this,listOf("All","Team","Box"),0) { filters=filters.copy(place=BuddyPicker.Place.values()[it]); update() })
        body.addView(MotionHorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(chips) })
        list.choiceMode=android.widget.ListView.CHOICE_MODE_SINGLE
        val empty=StudyUi.label(this,"No Pokémon match",14,R.color.ag_sub1)
        body.addView(empty)
        body.addView(list,StudyUi.lp(-1,0,1f))
        list.emptyView=empty
        search.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        list.setOnItemClickListener { _,_,index,_ ->
            BuddyWidgetPrefs.set(prefs,widgetId,choices[index]); Widgets.refreshAll(this,study=false)
            dialog.dismiss(); render()
        }
        update()
        if (show) {
            Pickers.show(dialog)
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        return dialog.content()
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
