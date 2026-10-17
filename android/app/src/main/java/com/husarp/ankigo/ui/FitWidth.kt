package com.husarp.ankigo.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Holds one wide child (the details' evolution line) at its natural size, centred, and draws it smaller when it is wider than
 * the room it is given, so it fits without clipping or sideways scrolling (R4 fix N1: the 340 dp floating card). Touches still
 * land on the right view (the child is scaled with its own scale properties). Not under [MIN_SCALE]: nothing real is that wide.
 */
class FitWidth(ctx: Context) : ViewGroup(ctx) {
    private var scale = 1f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val child = getChildAt(0)
        if (child == null) { setMeasuredDimension(0, 0); return }
        child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val avail = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) child.measuredWidth else MeasureSpec.getSize(widthMeasureSpec)
        scale = scaleFor(child.measuredWidth, avail)
        child.pivotX = 0f; child.pivotY = 0f
        child.scaleX = scale; child.scaleY = scale
        setMeasuredDimension(avail, (child.measuredHeight * scale).roundToInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val child: View = getChildAt(0) ?: return
        val x = ((width - child.measuredWidth * scale) / 2f).roundToInt().coerceAtLeast(0)
        child.layout(x, 0, x + child.measuredWidth, child.measuredHeight)
    }

    /** The scale a child [natural] px wide gets in [avail] px: 1 when it fits, else just enough, never under [MIN_SCALE]. */
    companion object {
        const val MIN_SCALE = 0.5f

        fun scaleFor(natural: Int, avail: Int): Float =
            if (natural <= 0 || avail <= 0 || natural <= avail) 1f else max(MIN_SCALE, avail.toFloat() / natural)
    }
}
