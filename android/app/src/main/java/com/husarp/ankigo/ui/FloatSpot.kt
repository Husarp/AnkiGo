package com.husarp.ankigo.ui

/** Where a floating damage number starts and how far it rises so it stays fully inside the arena (pure pixel maths). */
object FloatSpot {
    /** [left]/[top] = the view's start margins, [rise] = how far it travels up (px, never negative). */
    data class Spot(val left: Int, val top: Int, val rise: Int)

    /**
     * Wanted start ([left], [top]) of a number [tvW] x [tvH] in an arena [w] x [h]; [maxRise] is the usual 34 dp.
     * The rise shrinks to the room the arena has (h - tvH), left stays in [0, w - tvW], top in [rise, h - tvH]:
     * with no room above the sprite the number starts lower (over it) and still ends inside.
     */
    fun place(left: Int, top: Int, w: Int, h: Int, tvW: Int, tvH: Int, maxRise: Int): Spot {
        val rise = minOf(maxRise, maxOf(0, h - tvH))
        val l = left.coerceIn(0, maxOf(0, w - tvW))
        val t = top.coerceIn(rise, maxOf(rise, h - tvH))
        return Spot(l, t, rise)
    }
}
