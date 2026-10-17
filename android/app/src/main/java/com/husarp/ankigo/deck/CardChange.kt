package com.husarp.ankigo.deck

/**
 * One deck or note change started from the study screen (move the card, delete the note), tied to the card it began on.
 * The screen calls [begin] BEFORE anything asynchronous starts, so the card can't be answered or auto-advanced while the
 * flow reads from AnkiDroid or waits for a confirmation. Every later callback ([cancelled], [finished]) acts only while the
 * screen still shows that same card ([generation] unchanged) and only once: a newer card is never locked, unlocked or reloaded
 * by an old flow.
 *
 * [lock] / [unlock] / [next] are the screen's own: lock the card, make it usable again, load the next card.
 */
internal class CardChange(
    private val generation: () -> Int,
    private val lock: () -> Unit,
    private val unlock: () -> Unit,
    private val next: () -> Unit,
) {
    private val token = generation()
    private var over = false

    /** The change starts: lock the card now. */
    fun begin() = lock()

    /** The flow ended without a change (blocked, failed to read, the dialog was cancelled): the card is usable again. */
    fun cancelled() = end { unlock() }

    /** The change ran. [gone] = the card is gone or elsewhere now (load the next one), else it is usable again. */
    fun finished(gone: Boolean) = end { if (gone) next() else unlock() }

    private fun end(act: () -> Unit) {
        if (over) return
        over = true
        if (generation() == token) act()
    }
}
