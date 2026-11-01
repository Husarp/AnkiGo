package com.husarp.ankigo.ui

import android.content.Context

/** Framework scrollers also wake their bars for taps, focus and layout. Wake only after
 * their actual content offset changes; framework fading and fling timing still apply.
 * Nullable trackers also tolerate callbacks from framework constructors before field initialization. */
private class ScrollbarMotion {
    private var x = 0
    private var y = 0
    fun changed(nextX: Int, nextY: Int): Boolean {
        if (x == nextX && y == nextY) return false
        x = nextX; y = nextY
        return true
    }
}

open class MotionScrollView(ctx: Context) : android.widget.ScrollView(ctx) {
    private val motion: ScrollbarMotion? = ScrollbarMotion()
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        awakenScrollBars()
    }
    override fun awakenScrollBars(startDelay: Int, invalidate: Boolean): Boolean =
        if (motion?.changed(scrollX, scrollY) == true) super.awakenScrollBars(startDelay, invalidate) else false
}

open class MotionHorizontalScrollView(ctx: Context) : android.widget.HorizontalScrollView(ctx) {
    private val motion: ScrollbarMotion? = ScrollbarMotion()
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        awakenScrollBars()
    }
    override fun awakenScrollBars(startDelay: Int, invalidate: Boolean): Boolean =
        if (motion?.changed(scrollX, scrollY) == true) super.awakenScrollBars(startDelay, invalidate) else false
}

open class MotionListView(ctx: Context) : android.widget.ListView(ctx) {
    private val motion: ScrollbarMotion? = ScrollbarMotion()
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        awakenScrollBars()
    }
    override fun awakenScrollBars(startDelay: Int, invalidate: Boolean): Boolean =
        if (motion?.changed(computeHorizontalScrollOffset(), computeVerticalScrollOffset()) == true) super.awakenScrollBars(startDelay, invalidate) else false
}

open class MotionWebView(ctx: Context) : android.webkit.WebView(ctx) {
    private val motion: ScrollbarMotion? = ScrollbarMotion()
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        awakenScrollBars()
    }
    override fun awakenScrollBars(startDelay: Int, invalidate: Boolean): Boolean =
        if (motion?.changed(scrollX, scrollY) == true) super.awakenScrollBars(startDelay, invalidate) else false
}
