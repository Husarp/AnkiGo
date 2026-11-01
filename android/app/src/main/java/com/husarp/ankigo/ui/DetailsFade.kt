package com.husarp.ankigo.ui

import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import com.husarp.ankigo.R

/** Touch-transparent ending; no overlay remains when the last line is reachable. */
internal object DetailsFade {
    fun wrap(scroll: ScrollView): View = object : FrameLayout(scroll.context) {
        private val fade = View(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val surface = Ui.c(context, R.color.ag_card)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(surface and 0x00ffffff, surface))
        }

        init {
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            addView(fade, FrameLayout.LayoutParams(-1, Ui.dp(context, 28), Gravity.BOTTOM))
            scroll.setOnScrollChangeListener { _, _, _, _, _ -> update() }
        }

        private fun update() {
            val bottom = scroll.getChildAt(0)?.bottom ?: 0
            val visibleBottom = scroll.scrollY + scroll.height - scroll.paddingBottom
            // INVISIBLE keeps the gradient measured, including when content grows after a fold opens.
            fade.visibility = if (scroll.height > 0 && bottom > visibleBottom + 1) View.VISIBLE else View.INVISIBLE
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            update()
        }

        override fun dispatchDraw(canvas: Canvas) {
            update()
            super.dispatchDraw(canvas)
        }
    }
}
