package com.husarp.ankigo.study

import kotlin.math.abs

/** Swipe-to-answer rules (pure): which swipe answers what, and when a drag counts. Directions are where the finger goes. */
object SwipeRules {
    enum class Dir(val arrow: String, val horizontal: Boolean, val towardsEnd: Boolean) {
        LEFT("←", true, false), DOWN("↓", false, true), RIGHT("→", true, true), UP("↑", false, false)
    }

    /** Distance the finger must travel, in dp. */
    const val THRESHOLD_DP = 80
    /** The longer axis must be at least this many times the shorter one. */
    const val DOMINANCE = 2f

    /** Directions a card with [buttons] answer buttons has, in hint order. */
    fun dirs(buttons: Int): List<Dir> = when (buttons) {
        2 -> listOf(Dir.LEFT, Dir.RIGHT)
        3 -> listOf(Dir.LEFT, Dir.RIGHT, Dir.UP)
        else -> listOf(Dir.LEFT, Dir.DOWN, Dir.RIGHT, Dir.UP)
    }

    /** The ease (1-based button number, as in StudyActivity.answer) a swipe gives, or null if this card has no such button. */
    fun ease(dir: Dir, buttons: Int): Int? = dirs(buttons).indexOf(dir).takeIf { it >= 0 }?.plus(1)

    fun label(dir: Dir): String = when (dir) { Dir.LEFT -> "Again"; Dir.DOWN -> "Hard"; Dir.RIGHT -> "Good"; Dir.UP -> "Easy" }

    fun hint(buttons: Int): String = dirs(buttons).joinToString(" · ") { "${it.arrow} ${label(it)}" }

    /** For TalkBack: "Swipe left for Again, down for Hard, ...". */
    fun description(buttons: Int): String = "Swipe " + dirs(buttons).joinToString(", ") { "${it.name.lowercase()} for ${label(it)}" }

    /** A second finger (pinch zoom) cancels the swipe. */
    fun cancelledBy(pointers: Int): Boolean = pointers > 1

    /** The label the live feedback pill shows: the arrow sits on the side the finger heads for. */
    fun pill(dir: Dir): String = if (dir == Dir.LEFT || dir == Dir.UP) "${dir.arrow} ${label(dir)}" else "${label(dir)} ${dir.arrow}"

    /** The direction a drag points to (longer axis wins; 0,0 is none). Screen y grows downwards. */
    fun axis(dx: Float, dy: Float): Dir? = when {
        dx == 0f && dy == 0f -> null
        abs(dx) >= abs(dy) -> if (dx < 0) Dir.LEFT else Dir.RIGHT
        else -> if (dy < 0) Dir.UP else Dir.DOWN
    }

    /** True when the longer axis is at least [DOMINANCE] times the shorter one. */
    fun dominant(dx: Float, dy: Float): Boolean = maxOf(abs(dx), abs(dy)) >= DOMINANCE * minOf(abs(dx), abs(dy))

    /**
     * The swipe a lifted finger commits: travelled at least [thresholdPx] along a dominant axis and the card has a button
     * for it; otherwise null (nothing happens).
     */
    fun commit(dx: Float, dy: Float, thresholdPx: Float, buttons: Int): Dir? {
        val dir = axis(dx, dy) ?: return null
        val travel = if (dir.horizontal) abs(dx) else abs(dy)
        return dir.takeIf { dominant(dx, dy) && travel >= thresholdPx && ease(it, buttons) != null }
    }

    /** How far along (0..1, 1 = full at the threshold) the drag is towards [dir]; 0 when it points elsewhere or isn't dominant. */
    fun progress(dir: Dir, dx: Float, dy: Float, thresholdPx: Float): Float {
        if (axis(dx, dy) != dir || !dominant(dx, dy)) return 0f
        return ((if (dir.horizontal) abs(dx) else abs(dy)) / thresholdPx).coerceIn(0f, 1f)
    }

    /**
     * Whether the frame may take over a drag that has just passed the touch slop: the card has a button for the direction
     * and the card content can't scroll that way (a finger moving up scrolls content down, i.e. direction +1).
     * [canScroll] is (horizontal, direction) like View.canScrollHorizontally / canScrollVertically.
     */
    fun claims(dx: Float, dy: Float, buttons: Int, canScroll: (Boolean, Int) -> Boolean): Boolean {
        val dir = axis(dx, dy) ?: return false
        if (ease(dir, buttons) == null) return false
        return !canScroll(dir.horizontal, if (dir.towardsEnd) -1 else 1)
    }
}
