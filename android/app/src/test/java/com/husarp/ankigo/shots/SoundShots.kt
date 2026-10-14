package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.sound.SoundPrefs
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test

/*
 * T39: Settings -> Sound. The real Settings page is laid out tall; the shot is the slice that starts at the SOUND title
 * (found by laying the page out once), so it stays right when sections above it change. Light + dark, PHONE + SPLIT.
 * "sound-settings-*" = defaults, "sound-settings-off-*" = everything off and the volume at 0 %.
 */
class SoundShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun findText(v: View, text: String): TextView? {
        if (v is TextView && v.text.toString() == text) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), text)?.let { return it }
        return null
    }

    private fun topIn(v: View, root: View): Int {
        var y = 0
        var cur: View = v
        while (cur !== root) { y += cur.top; cur = cur.parent as View }
        return y
    }

    private fun soundSlice(ctx: Context): View {
        val tallDp = 3600
        val page = pageScreen(ShotCtx(ctx), SettingsPage(ShotCtx(ctx), NoHost))
        val frame = FrameLayout(ctx)
        frame.addView(page, FrameLayout.LayoutParams(-1, Ui.dp(ctx, tallDp)))
        val dm = ctx.resources.displayMetrics
        frame.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, tallDp), View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, dm.widthPixels, Ui.dp(ctx, tallDp))
        val title = findText(page, "SOUND") ?: error("no SOUND section in Settings")
        page.translationY = -(topIn(title, frame) - Ui.dp(ctx, 6)).toFloat()
        return FrameLayout(ctx).apply { addView(frame) }
    }

    private fun reset(ctx: Context) {
        SoundPrefs.setBattleSounds(ctx, true); SoundPrefs.setCries(ctx, true); SoundPrefs.setVolume(ctx, 70); SoundPrefs.setQuietOnCardAudio(ctx, true)
    }

    @Test fun settingsSound() {
        paparazzi.shootPage("sound-settings", listOf(Variants.PHONE, Variants.SPLIT)) { ctx -> reset(ctx); soundSlice(ctx) }
        paparazzi.shootPage("sound-settings-off", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
            SoundPrefs.setBattleSounds(ctx, false); SoundPrefs.setCries(ctx, false); SoundPrefs.setVolume(ctx, 0); SoundPrefs.setQuietOnCardAudio(ctx, false)
            soundSlice(ctx)
        }
    }
}
