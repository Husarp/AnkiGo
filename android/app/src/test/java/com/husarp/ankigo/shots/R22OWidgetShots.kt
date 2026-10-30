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
