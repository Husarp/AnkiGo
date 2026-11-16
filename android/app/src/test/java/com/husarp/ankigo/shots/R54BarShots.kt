package com.husarp.ankigo.shots

import android.content.ContextWrapper
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R54BarShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun defaultsPersistenceAndMapping() {
        val ctx = Shots.files(paparazzi.context)
        val prefs = ctx.getSharedPreferences("ankigo_ui", 0)
        prefs.edit().clear().commit()
        assertEquals(GoalBarFill.CLASSIC, BarTextures.goal(ctx))
        assertEquals(XpBarFill.CLASSIC, BarTextures.xp(ctx))
        for (xp in XpBarFill.entries) {
            BarTextures.putXp(ctx, xp)
            val reopened = ContextWrapper(ctx)
            assertEquals(xp, BarTextures.xp(reopened))
            assertEquals(xp.art, BarTextures.art(reopened, BarMeaning.XP, .6f))
            assertEquals(0, BarTextures.art(reopened, BarMeaning.COLLECTION, .6f))
            assertEquals(0, BarTextures.art(reopened, null, .6f))
        }
        BarTextures.putGoal(ctx, GoalBarFill.RIBBON)
        assertEquals(GoalBarFill.RIBBON, BarTextures.goal(ContextWrapper(ctx)))
        assertEquals(R.drawable.bar_goalfill_b_1, BarTextures.art(ctx, BarMeaning.GOAL, .25f))
        assertEquals(R.drawable.bar_goalfill_b_2, BarTextures.art(ctx, BarMeaning.GOAL, .6f))
        assertEquals(R.drawable.bar_goalfill_b_3, BarTextures.art(ctx, BarMeaning.GOAL, 1f))
        prefs.edit().putString(BarTextures.GOAL_KEY, "future").putString(BarTextures.XP_KEY, "future").commit()
        assertEquals(GoalBarFill.CLASSIC, BarTextures.goal(ctx))
        assertEquals(XpBarFill.CLASSIC, BarTextures.xp(ctx))
    }

    @Test fun textureSurvivesSkinColoursAndLeavesHpAlone() {
        val ctx = Shots.files(paparazzi.context)
        BarTextures.putXp(ctx, XpBarFill.SHIMMER)
        val xp = BarView(ctx).apply { setTextureMeaning(BarMeaning.XP, 0f) }
        xp.clearGradient()
        xp.set(.6f, 0xff75dbed.toInt(), false)
        val field = BarView::class.java.getDeclaredField("textureArt").apply { isAccessible = true }
        assertEquals(XpBarFill.SHIMMER.art, field.getInt(xp))
        val hp = BarView(ctx).apply { set(.6f, 0xff00ff00.toInt(), false) }
        assertEquals(0, field.getInt(hp))
        BarTextures.putXp(ctx, XpBarFill.CLASSIC)
        xp.setTextureMeaning(BarMeaning.XP, .6f)
        assertEquals(0, field.getInt(xp))
    }

    @Test fun backupRulesIncludeLookPreferences() {
        for (file in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(java.io.File("src/main/res/xml/$file"))
            val includes = doc.getElementsByTagName("include")
            val matches = (0 until includes.length).map { includes.item(it) as org.w3c.dom.Element }
                .count { it.getAttribute("domain") == "sharedpref" && it.getAttribute("path") == "ankigo_ui.xml" }
            assertEquals(if (file == "backup_rules.xml") 1 else 2, matches)
        }
    }

    @Test fun xpOptions() {
        for (choice in XpBarFill.entries) paparazzi.shootWith("b-xp-${choice.name.lowercase()}", listOf(Size.SPLIT)) { ctx ->
            BarTextures.putXp(ctx, choice)
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 20), Ui.dp(ctx, 20), 0)
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(Ui.text(ctx, 18f, 900, R.color.ag_text).apply { text = "XP bar · ${choice.label}" })
                for ((label, h) in listOf("Trainer · XP 340 / 500" to 7, "Pokémon details · XP 340 / 500" to 5,
                    "Battle · XP 340 / 500" to 18, "Team · XP 340 / 500" to 5)) {
                    addView(Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = label }, Form.lp(ctx, 18))
                    addView(BarView(ctx).apply {
                        setMeaning(BarMeaning.XP, .68f, false)
                        if (h == 18) setLabel("XP 340 / 500", 10f)
                    }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, h)))
                }
                addView(BarView(ctx).apply { look = HpBarLook.PIXEL; setMeaning(BarMeaning.XP, .68f, false) },
                    LinearLayout.LayoutParams(-1, Ui.dp(ctx, 12)).also { it.topMargin = Ui.dp(ctx, 18) })
            }
        }
    }

    @Test fun goalOptions() {
        for (choice in GoalBarFill.entries) paparazzi.shootWith("b-goal-${choice.name.lowercase()}", listOf(Size.SPLIT)) { ctx ->
            BarTextures.putGoal(ctx, choice)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            LinearLayout(ctx).apply goalHost@{
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 40), Ui.dp(ctx, 24), 0)
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(Ui.text(ctx, 18f, 900, R.color.ag_text).apply { text = "Daily goal · ${choice.label}" })
                for (fraction in listOf(.25f, .6f, .85f, 1f)) {
                    addView(Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = "${(fraction * 100).toInt()}%" }, Form.lp(ctx, 32))
                    addView(BarView(ctx).apply {
                        setMeaning(BarMeaning.GOAL, fraction, false)
                        setGoalSparks(if (fraction >= 1f) 1 else 0, this@goalHost)
                    }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 14)))
                }
            }
        }
    }

    @Test fun pickerPreviewAndSelection() = paparazzi.shootWith("b-xp-picker", listOf(Size.PHONE)) { ctx ->
        BarTextures.putXp(ctx, XpBarFill.CLASSIC)
        var changed = false
        val picker = BarTextures.xpPicker(ctx) { changed = true }
        Form.rowsIn(picker).last().performClick()
        assertTrue(changed)
        assertEquals(XpBarFill.TIDE, BarTextures.xp(ctx))
        BarTextures.xpPicker(ctx) {}.apply { setBackgroundColor(Ui.c(ctx, R.color.ag_base)) }
    }
}
