package com.husarp.ankigo.shots

import android.app.Activity
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import com.husarp.ankigo.study.CardExplain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R26PShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun field(v: View): EditText? = if (v is EditText) v else if (v is ViewGroup) (0 until v.childCount).firstNotNullOfOrNull { field(v.getChildAt(it)) } else null

    @Test fun promptDraftResetCancelAndSave() {
        val ctx = ShotCtx(Shots.files(paparazzi.context))
        val prefs = ctx.getSharedPreferences("study", 0)
        prefs.edit().putString(CardExplain.PREF_TEMPLATE, "custom prompt").commit()
        val dialog = ExplainPromptDialog.create(ctx)
        val editor = field(dialog.content())!!
        assertEquals("custom prompt", editor.text.toString())
        dialog.button(AppDialog.BUTTON_NEUTRAL)!!.performClick()
        assertEquals(CardExplain.DEFAULT_TEMPLATE, editor.text.toString())
        assertEquals("custom prompt", prefs.getString(CardExplain.PREF_TEMPLATE, null))
        dialog.button(AppDialog.BUTTON_NEGATIVE)!!.performClick()
        assertEquals("custom prompt", field(ExplainPromptDialog.create(ctx).content())!!.text.toString())
        editor.setText("  new prompt  ")
        dialog.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
        assertEquals("new prompt", prefs.getString(CardExplain.PREF_TEMPLATE, null))
        editor.setText(" ")
        dialog.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
        assertEquals(CardExplain.DEFAULT_TEMPLATE, prefs.getString(CardExplain.PREF_TEMPLATE, null))
        editor.setText("x".repeat(1100)); assertEquals(1000, editor.length())
    }

    @Test fun stylesAnimateAndReducedMotionSettles() {
        val ctx = ShotCtx(Shots.files(paparazzi.context))
        for (style in TabBarStyle.entries) {
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
            TabBarStyle.put(ctx, style)
            val bar = BottomNav.create(attach(Activity(), ctx), BottomNav.Tab.SETTINGS) as LinearLayout
            val row = bar.getChildAt(1) as BottomNav.HighlightRow
            row.arriveFrom(BottomNav.Tab.STUDY)
            @Suppress("UNCHECKED_CAST")
            val jobs = getField(UiMotion, "jobs") as Map<View, ValueAnimator>
            val animator = jobs[row]!!
            assertEquals(when(style) { TabBarStyle.D3 -> 130L; TabBarStyle.A3, TabBarStyle.E1 -> 200L; else -> 140L }, animator.duration)
            val item = row.getChildAt(4) as LinearLayout
            assertTrue(item.isSelected)
            assertEquals(Ui.c(ctx, R.color.ag_teal), (item.getChildAt(1) as android.widget.TextView).currentTextColor)
            assertEquals(Ui.c(ctx, R.color.ag_sub0), ((row.getChildAt(0) as LinearLayout).getChildAt(1) as android.widget.TextView).currentTextColor)
            animator.currentPlayTime = animator.duration / 2
            when(style) {
                TabBarStyle.A3 -> assertTrue(item.getChildAt(1).scaleX < 1f)
                TabBarStyle.C1 -> assertTrue(row.position > 0f && row.position < 4f)
                TabBarStyle.E1 -> assertTrue(item.getChildAt(1).alpha < 1f)
                else -> assertEquals(4f, row.position, 0f)
            }
            UiMotion.finish(row)
            assertEquals(4f, row.position, 0f)
            assertEquals(1f, item.getChildAt(1).alpha, 0f)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            row.arriveFrom(BottomNav.Tab.STUDY)
            assertNull(jobs[row]); assertEquals(4f, row.position, 0f)
        }
    }

    @Test fun animationPictures() {
        for (style in TabBarStyle.entries) for (time in listOf(0L, 70L, 160L)) {
            paparazzi.shoot("r26p-${style.name}-$time", listOf(Size.SMALL), offsetMillis = time) { ctx ->
                TabBarStyle.put(ctx, style)
                ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
                val bar = BottomNav.create(attach(Activity(), ctx), BottomNav.Tab.SETTINGS) as LinearLayout
                (bar.getChildAt(1) as BottomNav.HighlightRow).arriveFrom(BottomNav.Tab.STUDY)
                android.widget.FrameLayout(ctx).apply {
                    setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                    addView(bar, android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
                }
            }
        }
    }

    @Test fun promptAndSecretPictures() {
        paparazzi.shootPage("r26p-prompt", listOf(Variant("w360",360,640), Variant("w320",320,640))) { ctx ->
            android.widget.FrameLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                val margin = Ui.dp(ctx, if (ctx.resources.configuration.screenWidthDp >= 360) 24 else 16)
                addView(ExplainPromptDialog.create(ctx).content(), android.widget.FrameLayout.LayoutParams(
                    ctx.resources.displayMetrics.widthPixels - margin * 2, -2, android.view.Gravity.CENTER))
            }
        }
        paparazzi.shootPage("r26p-secrets", listOf(Variant("w360",360,640))) { ctx ->
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                val icons = listOf(R.drawable.ic_ach_secret_one_shot, R.drawable.ic_ach_secret_last_breath,
                    R.drawable.ic_ach_secret_combo_20, R.drawable.ic_ach_secret_revolving_door,
                    R.drawable.ic_ach_secret_perfect_throw, R.drawable.ic_ach_secret_david_goliath,
                    R.drawable.ic_ach_secret_night_owl, R.drawable.ic_ach_secret_easy_street,
                    R.drawable.ic_ach_secret_deja_vu, R.drawable.ic_ach_secret_bully, R.drawable.ic_ach_secret_glow_up)
                for (chunk in icons.chunked(3)) addView(LinearLayout(ctx).apply {
                    for (icon in chunk) addView(ImageView(ctx).apply {
                        setImageResource(icon); setColorFilter(Ui.c(ctx, R.color.ag_teal))
                        setPadding(20,20,20,20)
                    }, LinearLayout.LayoutParams(100,100))
                })
            }
        }
    }
}
