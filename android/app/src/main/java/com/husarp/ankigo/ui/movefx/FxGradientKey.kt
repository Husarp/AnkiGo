package com.husarp.ankigo.ui.movefx

/**
 * Remembers the parameters of the last linear gradient [FxCanvas.gradientPoly] built, so a gradient that does not change from
 * frame to frame (the Ice Beam cube's front face) is built once and reused (pure, no android.*). Copies what it is given, so the
 * caller may reuse its arrays.
 */
internal class GradientKey {
    private val xy = FloatArray(4)
    private var colors = IntArray(0)
    private var stops = FloatArray(0)
    private var known = false

    /** True when these are the parameters of the gradient built last. */
    fun matches(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, stops: FloatArray): Boolean =
        known && xy[0] == x0 && xy[1] == y0 && xy[2] == x1 && xy[3] == y1 && this.colors.contentEquals(colors) && this.stops.contentEquals(stops)

    /** Notes the parameters of a gradient that was just built. */
    fun remember(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, stops: FloatArray) {
        xy[0] = x0; xy[1] = y0; xy[2] = x1; xy[3] = y1
        this.colors = colors.copyOf()
        this.stops = stops.copyOf()
        known = true
    }
}
