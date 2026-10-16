package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.SettingsPage
import org.junit.Rule
import org.junit.Test

/** R2-03: Settings -> Study with the two tap rows (second one greyed out while the first is off). */
class TapShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private class ToStudy(ctx: Context, page: View) : FrameLayout(ctx) {
        init { addView(page, LayoutParams(-1, -1)) }
        private fun find(v: View, pred: (View) -> Boolean): View? {
            if (pred(v)) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), pred)?.let { return it }
            return null
        }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            val scroll = find(this) { it is ScrollView } as? ScrollView ?: return
            val title = find(scroll) { it is TextView && it.text.toString() == "STUDY" } ?: return
            var y = 0; var v: View = title
            while (v !== scroll) { y += v.top; v = v.parent as View }
            scroll.scrollTo(0, y - 4)
        }
    }

    private fun studyPage(ctx: Context, tapShow: Boolean, tapHide: Boolean): View {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("tapShow", tapShow).putBoolean("tapHide", tapHide).apply()
        val sp = SettingsPage(ShotCtx(ctx), NoHost)
        val v = pageScreen(ShotCtx(ctx), sp)
        sp.open(com.husarp.ankigo.ui.SettingsSection.STUDY)
        return ToStudy(ctx, v)
    }

    @Test fun settingsRows() {
        val phone = Variant("phone", 412, 1100)
        paparazzi.shootPage("tap-settings", listOf(phone)) { studyPage(it, tapShow = true, tapHide = true) }
        paparazzi.shootPage("tap-settings-off", listOf(phone)) { studyPage(it, tapShow = false, tapHide = false) }
    }
}
