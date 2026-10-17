package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.AppLook
import com.husarp.ankigo.ui.BarMeaning
import com.husarp.ankigo.ui.BarStyle
import com.husarp.ankigo.ui.BarView
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.ui.setMeaning
import org.junit.Rule
import org.junit.Test

/* R2-V08: progress bars by meaning / accent / gradient (the mockup's bar scene), and the Settings → Look row. Light + dark, PHONE. */
class R2V08Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val phone = listOf(Variants.PHONE)

    private fun scene(ctx: Context): View {
        fun label(t: String) = Ui.text(ctx, 12f, 800, R.color.ag_sub1).apply { text = t }
        fun bar(m: BarMeaning?, f: Float, dp: Int, hp: Boolean = false) = BarView(ctx).apply {
            if (m != null) setMeaning(m, f, false) else set(f, Ui.hpColor(ctx, if (hp) f else 1f), false)
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 24), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
            fun add(v: View, h: Int, top: Int = 6) = addView(v, LinearLayout.LayoutParams(-1, Ui.dp(ctx, h)).also { it.topMargin = Ui.dp(ctx, top) })
            addView(label("Team card: HP (stays) and XP"))
            add(bar(null, 0.82f, 8, hp = true), 8)
            add(bar(BarMeaning.XP, 0.62f, 4), 4)
            addView(label("Battle footer: daily goal"), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 16) })
            add(bar(BarMeaning.GOAL, 0.64f, 8), 8)
            addView(label("Goal colour: 10 % / 55 % / 100 %"), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 16) })
            add(bar(BarMeaning.GOAL, 0.10f, 8), 8)
            add(bar(BarMeaning.GOAL, 0.55f, 8), 8)
            add(bar(BarMeaning.GOAL, 1.0f, 8), 8)
            addView(label("Stats: weekly goal"), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 16) })
            add(bar(BarMeaning.GOAL, 0.68f, 8), 8)
            addView(label("Pokédex caught"), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 16) })
            add(bar(BarMeaning.COLLECTION, 0.21f, 8), 8)
            addView(label("Level 24 to 25"), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 16) })
            add(bar(BarMeaning.XP, 0.62f, 8), 8)
            addView(label("Download (no special meaning)"), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 16) })
            add(bar(BarMeaning.PROGRESS, 0.4f, 8), 8)
        }
    }

    @Test fun bars() {
        try {
            for (style in BarStyle.entries) {
                paparazzi.shootPage("r2v08-bars-${style.name.lowercase()}", phone) { ctx -> AppLook.setBarStyle(ctx, style); scene(ctx) }
            }
        } finally { AppLook.setBarStyle(Shots.files(paparazzi.context), BarStyle.MEANING) }
    }

    @Test fun lookSetting() {
        paparazzi.shootPage("r2v08-look", phone) { ctx ->
            val c = ShotCtx(ctx)
            val p = SettingsPage(c, NoHost)
            pageScreen(c, p).also { p.open(SettingsSection.LOOK) }
        }
    }
}
