package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R32BShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun groupedUnknownAndDiscovered() = paparazzi.shoot("b-matchup-sections", listOf(Size.PHONE)) { ctx ->
        val types = listOf(Type.GHOST, Type.POISON)
        val body = DetailSections.matchups(ctx, types, "Gastly", TypeMatchups.discovered(types, setOf(Type.NORMAL, Type.FIRE)))
        val nodes = views(body)
        val text = nodes.filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(text.containsAll(listOf("Weak against", "Resists", "Strongly resists", "Immune to", "Strong against", "???")))
        assertFalse(text.contains("Ground"))
        assertFalse(nodes.any { it.contentDescription?.contains("Ground") == true })
        body
    }

    @Test fun statsAndTraining() = paparazzi.shoot("b-stats-training", listOf(Size.PHONE)) { ctx ->
        val data = TestData.world()
        val mon = TestData.state(data).main!!.let { Mon(it.uid, it.species, it.name, it.level, 0, it.ivs, it.evs, Nature.ADAMANT, it.gender, false, it.ability, it.moves, it.hp) }
        val stats = DetailSections.statsBody(ctx, StatCalc.stats(data, mon), mon.level, mon.nature, data.sp(mon.species), false)
        assertFalse(views(stats).filterIsInstance<TextView>().any { it.text.contains("Nature") || it.text.contains("10%") })
        val training = DetailSections.training(ctx, mon, false)
        val text = views(training).filterIsInstance<TextView>()
        assertEquals(Ui.c(ctx, R.color.ag_red), text.single { it.text == "SpA −10%" }.currentTextColor)
        assertEquals(StatPill.ink(true, StatPill.isDark(ctx)), text.single { it.text == "Atk +10%" }.currentTextColor)
        val effect = NatureChanges.effectText(ctx, "+Atk −SpA") as android.text.Spanned
        val spans = effect.getSpans(0, effect.length, android.text.style.ForegroundColorSpan::class.java)
        assertEquals(Ui.c(ctx, R.color.ag_red), spans.single { effect.getSpanStart(it) == 5 }.foregroundColor)
        assertEquals("neutral", NatureChanges.effectText(ctx, "neutral"))
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            addView(stats); addView(training)
        }
    }
}
