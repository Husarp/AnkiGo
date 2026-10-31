package com.husarp.ankigo.shots

import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class R22OWidgetShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun layouts(ctx: Context): android.view.View = LinearLayout(ctx).apply {
        orientation=LinearLayout.VERTICAL
        val snap=WidgetSnapshot(null,false,28,50,999999,false,0,0,listOf(WidgetMon(25,"Pikachu",33,false,1f)),due=1234567)
        val night=ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        for(layout in listOf("A","B","C")) {
            val row=LinearLayout(ctx)
            for((w,h) in listOf(140f to 67f,140f to 140f,286f to 67f,286f to 140f)) {
                val image=ImageView(ctx).apply { setImageBitmap(WidgetRender.render(ctx,WidgetRender.Kind.BUDDY,snap,w,h,night,BuddyLook(layout=layout))) }
                row.addView(image,LinearLayout.LayoutParams(Ui.dp(ctx,w.toInt()),Ui.dp(ctx,h.toInt())))
            }
            addView(row)
        }
    }
    @Test fun buddySizeChangesRenderedSpriteWithoutChangingOtherInstances() = paparazzi.shootWith("r23d-size",listOf(Size.PHONE)) { ctx ->
        val p=ctx.getSharedPreferences("spike",0)
        BuddyWidgetPrefs.setSize(p,71,50)
        BuddyWidgetPrefs.setSize(p,72,100)
        val snap=WidgetSnapshot(null,false,28,50,12,false,0,0,listOf(WidgetMon(25,"Pikachu",33,false,1f)),due=87)
        LinearLayout(ctx).apply {
            orientation=LinearLayout.VERTICAL
            for(layout in listOf("A","B","C")) {
                val small=WidgetRender.render(ctx,WidgetRender.Kind.BUDDY,snap,140f,140f,false,BuddyWidgetPrefs.look(p,71).copy(layout=layout))
                val full=WidgetRender.render(ctx,WidgetRender.Kind.BUDDY,snap,140f,140f,false,BuddyWidgetPrefs.look(p,72).copy(layout=layout))
                assertFalse(small.sameAs(full))
                addView(LinearLayout(ctx).apply {
                    addView(ImageView(ctx).apply { setImageBitmap(small) },LinearLayout.LayoutParams(Ui.dp(ctx,140),Ui.dp(ctx,140)))
                    addView(ImageView(ctx).apply { setImageBitmap(full) },LinearLayout.LayoutParams(Ui.dp(ctx,140),Ui.dp(ctx,140)))
                })
            }
        }
    }
    @Test fun r23uGaugeLongCountersAndSharedTint() = paparazzi.shootWith("r23u-gauge-tints",listOf(Size.PHONE)) { ctx ->
        LinearLayout(ctx).apply {
            orientation=LinearLayout.VERTICAL
            val snap=WidgetSnapshot(null,false,1234,2000,12,false,0,0,listOf(WidgetMon(25,"Pikachu",33,false,1f,type=com.husarp.ankigo.game.Type.ELECTRIC)),due=87)
            for((w,h) in listOf(140f to 67f,140f to 140f,286f to 67f,286f to 140f)) {
                addView(ImageView(ctx).apply { setImageBitmap(WidgetRender.render(ctx,WidgetRender.Kind.BUDDY,snap,w,h,false,BuddyLook(layout="C"))) },LinearLayout.LayoutParams(Ui.dp(ctx,w.toInt()),Ui.dp(ctx,h.toInt())))
            }
            for(kind in WidgetRender.Kind.values()) {
                val plain=WidgetRender.render(ctx,kind,snap,140f,67f,false)
                val tinted=WidgetRender.render(ctx,kind,snap,140f,67f,false,appearance=WidgetLook(tint="peach",accent="blue"))
                assertFalse("Tint must affect $kind",plain.sameAs(tinted))
            }
            val prefs=ctx.getSharedPreferences("spike",0)
            WidgetLooks.write(prefs,"study",0,WidgetLook())
            val plainStudy=WidgetLookActivity.studyPreview(ctx,0,WidgetLook(),false)
            val appearance=WidgetLook(tint="peach",accent="blue")
            WidgetLooks.write(prefs,"study",0,appearance)
            val study=WidgetLookActivity.studyPreview(ctx,0,appearance,false)
            assertFalse("Tint must affect Study",plainStudy.sameAs(study))
            addView(ImageView(ctx).apply { setImageBitmap(study) },LinearLayout.LayoutParams(Ui.dp(ctx,286),Ui.dp(ctx,100)))
        }
    }
    @Test fun r23uPokemonSheet() = paparazzi.shootWith("r23u-picker",listOf(Size.PHONE,Size.SPLIT)) { ctx ->
        val activity=attach(WidgetLookActivity(),ShotCtx(ctx))
        activity.buildContent()
        activity.showPokemonSheet(show=false)
    }
    @Test fun r23uEveryWidgetOffersTheSameLookControlsAndFivePercentSteps() = paparazzi.shootWith("r23u-all-look",listOf(Size.PHONE)) { ctx ->
        var last: android.view.View?=null
        fun all(v: android.view.View): List<android.view.View> = listOf(v)+(if(v is android.view.ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList())
        for(kind in WidgetLookActivity.names.keys) {
            val activity=attach(WidgetLookActivity(),ShotCtx(ctx))
            setField(activity,"kind",kind)
            val view=activity.buildContent()
            val children=all(view)
            val text=children.filterIsInstance<android.widget.TextView>().map { it.text.toString() }
            for(label in listOf("Colours","Accent colour","Card tint","Plain","Type colour","Preset")) assertTrue("$kind offers $label",label in text)
            val slider=children.filterIsInstance<android.widget.SeekBar>().first { it.contentDescription=="Background see-through" }
            assertEquals(20,slider.max)
            last=view
        }
        last!!
    }
    @Test fun r23uOpaqueSpriteCornersAndAspectRatios() = paparazzi.shootWith("r23u-safe-sprites",listOf(Size.TABLET_LAND)) { ctx ->
        LinearLayout(ctx).apply {
            orientation=LinearLayout.VERTICAL
            for ((index,shape) in listOf(96 to 96,32 to 160,160 to 32).withIndex()) {
                val num=9900+index
                val file=com.husarp.ankigo.data.Sprites.staticFile(ctx,num)
                file.parentFile!!.mkdirs()
                val sprite=android.graphics.Bitmap.createBitmap(shape.first,shape.second,android.graphics.Bitmap.Config.ARGB_8888)
                sprite.eraseColor(android.graphics.Color.MAGENTA)
                file.outputStream().use { sprite.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                try {
                    val snap=WidgetSnapshot(null,false,28,50,12,false,0,0,listOf(WidgetMon(num,"Bounds",33,false,1f)),due=87)
                    for (layout in listOf("A","B","C")) {
                        addView(LinearLayout(ctx).apply {
                            for ((w,h) in listOf(140f to 67f,140f to 140f,286f to 67f,286f to 140f)) {
                                val look=BuddyLook(layout=layout,size=100)
                                val bitmap=WidgetRender.render(ctx,WidgetRender.Kind.BUDDY,snap,w,h,false,look)
                                val background=WidgetRender.render(ctx,WidgetRender.Kind.BUDDY,snap,w,h,false,look,hideSprite=true)
                                val space=BuddyGeometry.space(w,h)
                                val g=BuddyGeometry.of(space.width,space.height,layout).scaled(100)
                                val k=bitmap.width/w
                                val left=(space.offsetX+g.spriteLeft*space.scale)*k
                                val top=(space.offsetY+g.spriteTop*space.scale)*k
                                val side=g.spriteSize*space.scale*k
                                var changed=0
                                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                                    if (bitmap.getPixel(x,y) != background.getPixel(x,y)) {
                                        changed++
                                        assertTrue("Sprite escaped $layout $w x $h $shape",x >= left-1 && x <= left+side+1 && y >= top-1 && y <= top+side+1)
                                    }
                                }
                                assertTrue("Synthetic sprite must be drawn",changed>0)
                                addView(ImageView(ctx).apply { setImageBitmap(bitmap) },LinearLayout.LayoutParams(Ui.dp(ctx,(w/2).toInt()),Ui.dp(ctx,(h/2).toInt())))
                            }
                        })
                    }
                } finally { file.delete(); sprite.recycle() }
            }
        }
    }
    @Test fun bigNumbersInEveryLook() = paparazzi.shootWith("r22o-layouts",listOf(Size.TABLET_LAND)) { layouts(it) }
    @Test fun pinnedPreviewAndLook() = paparazzi.shootWith("r22o-settings",listOf(Size.PHONE,Size.SPLIT)) { ctx ->
        attach(WidgetLookActivity(),ShotCtx(ctx)).buildContent()
    }
    @Test fun explicitThemeAndTransparentSurface() = paparazzi.shootWith("r22o-theme-alpha",listOf(Size.PHONE)) { ctx ->
        val look=WidgetLook(100,"light")
        val surface=WidgetLooks.surface(ctx,look)
        assertEquals(0,android.graphics.Color.alpha(surface.getPixel(80,80)))
        val a=WidgetRender.render(ctx,WidgetRender.Kind.BUDDY,null,140f,140f,false,appearance=look.copy(transparency=0))
        val b=WidgetRender.render(ctx,WidgetRender.Kind.BUDDY,null,140f,140f,true,appearance=look.copy(transparency=0))
        assertTrue(a.sameAs(b))
        ImageView(ctx).apply { setImageBitmap(a) }
    }
    @Test fun rollingMoneyKeepsTheWidgetAppearance() = paparazzi.shootWith("r22m-money-look",listOf(Size.PHONE)) { ctx ->
        val snap=WidgetSnapshot(null,false,28,50,12,false,100,4,listOf(WidgetMon(25,"Buddy",33,false,1f)))
        val appearance=WidgetLook(45,"light","blue","peach")
        val frame=WidgetRender.moneyFrame(ctx,snap,75,286f,180f,true,BuddyLook(),appearance)
        val expected=WidgetRender.render(ctx,WidgetRender.Kind.WIDE,snap.copy(cash=75),286f,180f,true,appearance=appearance)
        val default=WidgetRender.render(ctx,WidgetRender.Kind.WIDE,snap.copy(cash=75),286f,180f,true)
        assertTrue(frame.sameAs(expected))
        assertFalse(frame.sameAs(default))
        val before=WidgetRender.render(ctx,WidgetRender.Kind.WIDE,snap,286f,180f,true,appearance=appearance)
        assertFalse("the frame shows the intermediate balance", frame.sameAs(before))
        ImageView(ctx).apply { setImageBitmap(frame) }
    }
    @Test fun widgetsSection() = paparazzi.shootWith("r22o-widgets-section",listOf(Size.PHONE)) { ctx ->
        val c=ShotCtx(ctx)
        val page=SettingsPage(c,NoHost)
        val view=pageScreen(c,page)
        page.open(SettingsSection.WIDGETS)
        view
    }
    @Test fun contentTab() = paparazzi.shootWith("r22o-content",listOf(Size.PHONE)) { ctx ->
        val activity=attach(WidgetLookActivity(),ShotCtx(ctx))
        setField(activity,"tab",1)
        activity.buildContent()
    }
    @Test fun typeProgressColourAlsoAppliesToBar() = paparazzi.shootWith("r22o-type-bar",listOf(Size.PHONE)) { ctx ->
        val type=com.husarp.ankigo.game.Type.GRASS
        val snap=WidgetSnapshot(null,false,28,50,12,false,0,0,listOf(WidgetMon(25,"Buddy",33,false,1f,type=type)),due=87)
        val bitmap=WidgetRender.render(ctx,WidgetRender.Kind.BUDDY,snap,140f,140f,false,
            BuddyLook(ring=BuddyRing(BuddyRing.Mode.TYPE),layout="B"),appearance=WidgetLook(accent="blue"))
        assertEquals(Ui.typeColor(type),bitmap.getPixel(25*bitmap.width/140,109*bitmap.height/140))
        ImageView(ctx).apply { setImageBitmap(bitmap) }
    }
    @Test fun tiles() = paparazzi.shootWith("r22o-tiles",listOf(Size.PHONE)) { WidgetLookActivity.tiles(it) }
}
