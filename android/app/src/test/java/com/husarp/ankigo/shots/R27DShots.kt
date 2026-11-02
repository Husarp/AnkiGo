package com.husarp.ankigo.shots

import android.view.Gravity
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** Real production views, 360 dp; all magnitudes and both semantic palettes. */
class R27DShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @Test fun floatingAbbreviationsKeepCanonicalStatNames() {
        for (key in listOf("atk","def","spa","spd","spe","accuracy","evasion")) {
            val float = FloatTextView(paparazzi.context, FloatText(true,"change",FloatKind.STAT_DOWN,StatPill.shortName(key)!!.uppercase(),4),0f)
            val pill = getField(float,"stagePill") as StatPillView
            assertEquals(StatPill.spoken(key,-4),pill.contentDescription)
        }
    }
    @Test fun stagesAndTools() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("phone", 360, 800).device(theme))
            val ctx = Shots.files(paparazzi.context)
            val page = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx,16), Ui.dp(ctx,16), Ui.dp(ctx,16), Ui.dp(ctx,16))
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            }
            page.addView(Ui.text(ctx, 18f, 800, R.color.ag_text).apply { text = "Stage changes" })
            for (sign in listOf(1, -1)) for (group in listOf(1..3, 4..6)) {
                page.addView(LinearLayout(ctx).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    for (n in group) addView(StatPillView(ctx, "atk", sign*n), LinearLayout.LayoutParams(-2,-2).apply { setMargins(0,8,12,8) })
                })
            }
            val model = ActionBarModel()
            model.observe(com.husarp.ankigo.game.GameEvent.Attack(true,"Pikachu","Eevee","Growl",com.husarp.ankigo.game.Type.NORMAL,com.husarp.ankigo.game.Category.STATUS,0,1,1.0,false,false,false,1,10,10,10))
            model.observe(com.husarp.ankigo.game.GameEvent.Effect(true,"x",5,10,null,com.husarp.ankigo.game.EffectKind.STAT_CHANGE,"atk",-1))
            page.addView(MoveBubble(ctx,MoveBubbleLook.create(ctx,MoveBubbleLook.Variant.E2B)).apply {
                bind(ActionBarModel.bubble((model.half(BarSide.MINE) as HalfState.Moved).move))
            }, LinearLayout.LayoutParams(-2,-2))
            page.addView(Ui.text(ctx, 16f, 800, R.color.ag_text).apply { text = "Floating battle changes" })
            for (sign in listOf(1,-1)) page.addView(LinearLayout(ctx).apply {
                for (n in listOf(1,4,6)) addView(FloatTextView(ctx, FloatText(true,"Attack",if(sign>0) FloatKind.STAT_UP else FloatKind.STAT_DOWN,"atk",n),0f))
            })
            for (sign in listOf(1,-1)) {
                val metrics = ChipMetrics(ctx, 12f)
                val marks = (4..6).map { n -> ConditionMarks.Mark("$n", ConditionMarks.Slot.OURS, ConditionMarks.def("stat.atk")!!, sign*n, n.toLong()) }
                val w = marks.sumOf { metrics.width(it).toDouble() }.toFloat() + 2*metrics.gap
                page.addView(ConditionMarksView(ctx).apply {
                    setRow(ConditionMarks.Slot.OURS, ConditionSpot.Placed(ConditionSpot.R(0f,0f,w,metrics.height), ConditionRow.Fit(3,0,w),true),marks,metrics)
                }, LinearLayout.LayoutParams(-1,(metrics.height+8).toInt()))
            }
            page.addView(DetailParts.statRow(ctx, com.husarp.ankigo.game.Stat.ATK,48,.5f,0,60,false,stage=-2))
            val board = Whiteboard(ctx)
            val tools = WhiteboardTools(ctx,board).apply { setBoardOn(true) }
            page.addView(Ui.text(ctx, 16f, 800, R.color.ag_text).apply { text = "Whiteboard tools and colour dropdown" })
            page.addView(tools)
            page.addView(tools.paletteContent(), LinearLayout.LayoutParams(-2,-2).apply { gravity = Gravity.END })
            paparazzi.snapshot(page,"d-stages-tools-${theme.name}")
            for (n in 1..6) {
                val path = android.graphics.Path(); StatPill.chevrons(path,n,true)
                val bounds = android.graphics.RectF(); path.computeBounds(bounds,true)
                assertEquals(12.5f,bounds.centerY(),0.01f)
                val measure = android.graphics.PathMeasure(path,false)
                val centres = mutableListOf<Float>()
                do {
                    val arrow = android.graphics.Path()
                    measure.getSegment(0f, measure.length, arrow,true)
                    arrow.computeBounds(bounds,true); centres += bounds.centerY()
                } while (measure.nextContour())
                assertEquals(n, centres.size)
                centres.chunked(3).forEach { assertEquals(12.5f,it.average().toFloat(),0.01f) }
                assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(
                    StatPill.ink(n % 2 == 0, StatPill.isDark(ctx)), StatPill.background(ctx,n % 2 == 0)) >= 4.5)

            }
        }
    }
}
