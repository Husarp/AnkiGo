package com.husarp.ankigo.ui

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.husarp.ankigo.R

/**
 * The battle panel's text floor ([PanelFit.MIN_SP]): the panel is drawn scaled down, so a text that would end up under
 * 8 sp on screen is drawn at `max(design size, floor)` instead. Every TextView keeps its design size in the view tag
 * `R.id.ag_tag_base_sp`; [set] changes a design size, [apply] re-raises a whole tree when the floor changes.
 */
internal object TextFloor {
    /** The design size of [tv] in sp: its tag, or (first time) the size it has now. */
    fun base(tv: TextView): Float =
        (tv.getTag(R.id.ag_tag_base_sp) as? Float) ?: (tv.textSize / (tv.resources.displayMetrics.density * tv.resources.configuration.fontScale))

    /** Gives [tv] the design size [sp] (raised to [floor] when that is bigger). */
    fun set(tv: TextView, sp: Float, floor: Float) {
        tv.setTag(R.id.ag_tag_base_sp, sp)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TextMath.at(sp, floor))
    }

    /** Raises every TextView under [root] to [floor] (or lowers it back to its design size). Chip icons follow their label. */
    fun apply(root: View, floor: Float) {
        if (root is TextView) set(root, base(root), floor)
        if (root is Chip) {
            val icon = root.getChildAt(0) as? ImageView
            val k = TextMath.at(11f, floor) / 11f
            icon?.layoutParams?.let { lp ->
                val d = Ui.dp(root.context, 13f * k).toInt()
                if (lp.width != d) { lp.width = d; lp.height = d; icon.layoutParams = lp }
            }
        }
        if (root is ViewGroup) for (i in 0 until root.childCount) apply(root.getChildAt(i), floor)
    }
}
