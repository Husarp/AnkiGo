package com.husarp.ankigo.ui.movefx

/** Visible, nontransparent body in effect-layer pixels. */
data class FxBody(val cx: Float, val cy: Float, val w: Float, val h: Float)

/** PLAN 705 round 5 approval: +41%, aspect 0.8–1.25, then clamp in design-stage units. */
internal object IceCubeFit {
    fun width(w: Float, h: Float): Float = maxOf(w * 1.41f, h * 1.41f * .8f).coerceIn(50f, 190f)
    fun height(w: Float, h: Float): Float = maxOf(h * 1.41f, w * 1.41f / 1.25f).coerceIn(50f, 190f)
}
