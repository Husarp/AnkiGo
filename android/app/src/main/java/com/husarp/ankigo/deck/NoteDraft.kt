package com.husarp.ankigo.deck

/**
 * What the note editor keeps when its activity is recreated (turn, split screen, font size): the texts typed so far AND the texts
 * the editor started from, so [DeckOps.editNote] can still tell that the note was changed in AnkiDroid meanwhile.
 */
internal object NoteDraft {
    /** What goes into the saved state: [before] = the texts the editor started from, [texts] = the boxes now. */
    class Saved(val before: List<String>, val texts: List<String>)

    /** What the page shows once the note is loaded: [before] is what conflict detection compares with, [texts] go into the boxes. */
    class Shown(val before: List<String>, val texts: List<String>, val restored: Boolean)

    /** A bundle holds about 1 MB for the whole app: a draft bigger than this is not kept (better than a crash when the state is saved). */
    const val MAX_CHARS = 100_000

    /** The draft worth keeping, or null when there is nothing to keep (not loaded, no change) or it is too big. */
    fun toSave(before: List<String>, texts: List<String>): Saved? {
        if (before.size != texts.size || before == texts) return null
        if (before.sumOf { it.length } + texts.sumOf { it.length } > MAX_CHARS) return null
        return Saved(before, texts)
    }

    /**
     * Decides what the boxes show after the note was read again ([loaded]). The saved draft is brought back when it still fits the
     * note's fields, holds a real edit and differs from what AnkiDroid holds now (the same text already saved = nothing left to
     * restore); its original texts are kept so a change made in AnkiDroid meanwhile is still caught when saving.
     */
    fun resolve(saved: Saved?, loaded: List<String>): Shown {
        if (saved == null || saved.texts.size != loaded.size || saved.before.size != loaded.size ||
            saved.texts == loaded || saved.texts == saved.before) {
            return Shown(loaded, loaded, false)
        }
        return Shown(saved.before, saved.texts, true)
    }
}
