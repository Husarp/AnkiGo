package com.husarp.ankigo.ui

import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.View
import android.widget.ImageView
import com.husarp.ankigo.R

/** Phosphor Fire, with the design's gentle 1.6-second lick; still when motion is reduced or hidden. */
class StreakFlame(ctx: Context) : ImageView(ctx) {
    private var motion: ObjectAnimator? = null
    private var windowVisible = false

    var alive: Boolean = true
        set(v) { if (field != v) { field = v; refresh() } }

    init {
        scaleType = ScaleType.FIT_CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setImageResource(R.drawable.ic_flame)
        refresh()
    }

    private fun picture() = StreakFlameRules.picture(Build.VERSION.SDK_INT,
        !ValueAnimator.areAnimatorsEnabled() || context.getSharedPreferences("study", 0).getBoolean("reduceMotion", false),
        isInEditMode || Build.DEVICE == "layoutlib" || Build.FINGERPRINT.contains("layoutlib"), alive)

    private fun refresh() {
        imageAlpha = StreakFlameRules.alpha(alive)
        setColorFilter(context.getColor(if (alive) R.color.ag_red else R.color.ag_sub0))
        sync()
    }

    private fun sync() {
        if (StreakFlameRules.shouldRun(picture(), isAttachedToWindow, windowVisible, isShown)) {
            if (motion == null) {
                fun track(name: String, a: Float, b: Float, c: Float) = PropertyValuesHolder.ofKeyframe(name,
                    Keyframe.ofFloat(0f, a), Keyframe.ofFloat(.35f, b), Keyframe.ofFloat(.65f, c), Keyframe.ofFloat(1f, a))
                motion = ObjectAnimator.ofPropertyValuesHolder(this,
                    track("scaleX", 1f, .97f, 1.025f), track("scaleY", 1f, 1.06f, .98f), track("rotation", -1f, 1.5f, -1f)).apply {
                    duration = 1600L
                    repeatCount = ValueAnimator.INFINITE
                }
            }
            if (motion?.isStarted != true) motion?.start()
        } else {
            motion?.cancel()
            scaleX = 1f; scaleY = 1f; rotation = 0f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pivotX = w * .5f; pivotY = h * .9f
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); windowVisible = windowVisibility == VISIBLE; refresh() }
    override fun onDetachedFromWindow() { windowVisible = false; motion?.cancel(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); windowVisible = visibility == VISIBLE; sync() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); sync() }

    companion object {
        /** Rasterise the same vector in the current theme for widgets, which cannot animate. */
        fun stillBitmap(ctx: Context, sizePx: Int): Bitmap? {
            val size = sizePx.coerceAtLeast(1)
            val icon = ctx.getDrawable(R.drawable.ic_flame)?.mutate() ?: return null
            icon.setTint(ctx.getColor(R.color.ag_red))
            return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                icon.setBounds(0, 0, size, size)
                icon.draw(Canvas(it))
            }
        }
    }
}
