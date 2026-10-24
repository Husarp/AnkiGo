package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.ColorStateList
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.husarp.ankigo.R

/** The 5-step slider of "Pokémon movement" (R18-C / PLAN 247): Always, Often, Now and then, Rarely, Never. Same look as the Pokémon size slider. */
internal object MotionSlider {
    /** The slider and its value label. [onChange] gets the new step ([MotionPrefs.ALWAYS] .. [MotionPrefs.NEVER]) every time the thumb moves to another step. */
    fun make(ctx: Context, step: Int, title: String, onChange: (Int) -> Unit): Pair<SeekBar, TextView> {
        val label = Ui.text(ctx, 13f, 800, R.color.ag_sub1).apply { text = MotionPrefs.label(step) }
        val bar = SeekBar(ctx).apply {
            max = MotionPrefs.LAST
            progress = MotionPrefs.clamp(step)
            progressTintList = ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mauve))
            progressBackgroundTintList = ColorStateList.valueOf(Ui.c(ctx, R.color.ag_s1))
            thumbTintList = ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mauve))
            splitTrack = false
            minimumHeight = Ui.dp(ctx, 40)
            contentDescription = title
            ViewCompat.setStateDescription(this, MotionPrefs.label(step))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val v = MotionPrefs.clamp(progress)
                    label.text = MotionPrefs.label(v)
                    ViewCompat.setStateDescription(sb, MotionPrefs.label(v))   // TalkBack says "Rarely", not the step number
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        return bar to label
    }
}
