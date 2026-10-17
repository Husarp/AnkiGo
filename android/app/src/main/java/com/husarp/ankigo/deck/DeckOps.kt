package com.husarp.ankigo.deck

import java.text.Normalizer
import java.util.Locale

/**
 * What an operation did, in plain words for the screen (never a stack trace).
 * [ok] false = something went wrong or was refused; [changed] true = AnkiDroid's collection really was changed
 * (a partial move is `ok=false, changed=true`). [done]/[total]/[skipped]/[failed] count cards for the bulk move
 * and the delete; [unconfirmed] counts bulk moves whose write went out but could not be checked afterwards (then [changed] is
 * true: the collection may have changed, so refresh it); [deckId] is the new deck of a create.
 */
data class DeckResult(
    val ok: Boolean,
    val message: String,
    val changed: Boolean = false,
    val deckId: Long? = null,
    val done: Int = 0,
    val total: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val cancelled: Boolean = false,
    val unconfirmed: Int = 0,
)

/** A note ready for the field editor: [fieldNames] come from the note type, [values] are the raw field texts (HTML). */
class NoteForEdit(val nid: Long, val noteTypeName: String, val fieldNames: List<String>, val values: List<String>)

/** [note] null = it could not be loaded; [message] says why. */
class NoteLoad(val note: NoteForEdit?, val message: String)

/**
 * Deck and card management through AnkiDroid's OFFICIAL provider API only (never the unofficial notes_v2/revlog back door).
 * Every call checks permission + supported version first, never trusts a write's return count (AnkiDroid often reports
 * success on a failed write) and re-reads to verify before it says "done".
 *
 * Threading: every function blocks on content-provider calls; the move-all can take minutes. Call from a background
 * thread, never the main thread. [moveAllCards] reports progress and honours a cancel flag.
 *
 * NO UNDO exists for any of this (AnkiDroid's API has no undo), so the screens must ask the user to confirm every
 * destructive step: moving cards, deleting a note (the whole note with all its cards), overwriting field texts.
 * Creating a deck is harmless but cannot be deleted from AnkiGo either (see [notPossible]).
 *
 * The provider is injected so this is tested on the JVM; [AnkiDeckProvider] is the real one (see also `deckOps(ctx)`).
 */
class DeckOps(private val provider: Provider, private val log: (String) -> Unit = {}) {

    enum class Access { OK, NO_PERMISSION, UNSUPPORTED }
    data class DeckRef(val id: Long, val name: String, val dyn: Boolean)
    /** [odid] is the home deck while the card sits in a filtered deck, else 0. */
    data class CardRef(val id: Long, val nid: Long, val ord: Int, val did: Long, val odid: Long) {
        companion object {
            val COLS = arrayOf("_id", "note_id", "ord", "deck_id", "original_deck_id")
            /** One row of the cards URI; null when the card vanished after the search (only `_id` filled) or has no usable ids. */
            fun from(row: Map<String, String?>): CardRef? {
                val nid = row["note_id"]?.toLongOrNull() ?: return null
                val ord = row["ord"]?.toIntOrNull() ?: return null
                val did = row["deck_id"]?.toLongOrNull() ?: return null
                return CardRef(row["_id"]?.toLongOrNull() ?: 0L, nid, ord, did, row["original_deck_id"]?.toLongOrNull() ?: 0L)
            }
        }
    }
    class NoteRef(val id: Long, val mid: Long, val fields: List<String>)
    class NoteType(val name: String, val fieldNames: List<String>)

    /** Thin calls to AnkiDroid. Reads may throw; writes throw what AnkiDroid throws (IllegalArgumentException, SecurityException, ...). */
    interface Provider {
        fun access(): Access
        fun decks(): List<DeckRef>
        /** Returns the new deck's id. */
        fun createDeck(name: String, desc: String?): Long
        fun card(nid: Long, ord: Int): CardRef?
        /** Cards matching an Anki search. */
        fun cardsMatching(query: String): List<CardRef>
        fun cardCount(query: String): Int
        fun moveCard(nid: Long, ord: Int, deckId: Long)
        /** Null when there is no such note. */
        fun note(nid: Long): NoteRef?
        fun noteType(mid: Long): NoteType?
        fun setFields(nid: Long, fields: List<String>)
        fun deleteNote(nid: Long)
    }

    // ---- create -------------------------------------------------------------------------------------------------

    /**
     * Creates a deck; "A::B" makes a subdeck (missing parents are created by AnkiDroid). Parts are trimmed. Refuses an empty name or
     * part, a leading/trailing "::", a name that exists already (capital letters ignored, like Anki) and a filtered parent.
     */
    fun createDeck(rawName: String, desc: String? = null): DeckResult {
        blocked()?.let { return it }
        nameProblem(rawName)?.let { return refuse(it) }
        val name = cleanName(rawName)
        val parts = name.split(SEP)
        val before = try { provider.decks() } catch (e: Exception) { return failed("create deck", e) }
        before.firstOrNull { key(it.name) == key(name) }?.let {
            return refuse("A deck called \"${it.name}\" already exists (capital letters don't make a new name).")
        }
        for (i in 1 until parts.size) {
            val parent = parts.take(i).joinToString(SEP)
            before.firstOrNull { key(it.name) == key(parent) && it.dyn }?.let {
                return refuse("\"${it.name}\" is a filtered deck, and filtered decks can't have subdecks.")
            }
        }
        val id = try {
            provider.createDeck(name, desc?.takeIf { it.isNotBlank() })
        } catch (e: IllegalArgumentException) {
            log("FAIL create deck \"$name\": ${e.javaClass.simpleName}: ${e.message}")
            val m = (e.message ?: "").lowercase(Locale.ROOT)
            return refuse(
                when {
                    "already exists" in m -> "A deck called \"$name\" already exists."
                    "invalid deck name" in m -> "AnkiDroid doesn't accept \"$name\" as a deck name."
                    "filtered" in m -> "That name would put the deck inside a filtered deck, and filtered decks can't have subdecks."
                    else -> "AnkiDroid didn't accept that deck name."
                },
            )
        } catch (e: Exception) { return failed("create deck", e) }
        val found = try {
            provider.decks().let { now -> now.firstOrNull { it.id == id } ?: now.firstOrNull { key(it.name) == key(name) } }
        } catch (e: Exception) { return unconfirmed("create deck", e) }
            ?: return unconfirmed("create deck", null)
        val newParents = (1 until parts.size).map { parts.take(it).joinToString(SEP) }.filter { p -> before.none { key(it.name) == key(p) } }
        val extra = if (newParents.isEmpty()) "" else " The missing parent deck${if (newParents.size > 1) "s" else ""} ${newParents.joinToString(", ") { "\"$it\"" }} ${if (newParents.size > 1) "were" else "was"} created too."
        return DeckResult(true, "Created the deck \"${found.name}\".$extra", changed = true, deckId = found.id)
    }

    // ---- move one card -------------------------------------------------------------------------------------------

    /** Moves one card to [deckId]. Refuses a filtered target and a card that sits in a filtered deck; no-op when it is already there. */
    fun moveCard(nid: Long, ord: Int, deckId: Long): DeckResult {
        blocked()?.let { return it }
        val decks = try { provider.decks() } catch (e: Exception) { return failed("move card", e) }
        val target = decks.firstOrNull { it.id == deckId } ?: return refuse("That deck doesn't exist any more. Reload the deck list.")
        if (target.dyn) return refuse(FILTERED_TARGET)
        val card = try { provider.card(nid, ord) } catch (e: Exception) { return failed("move card", e) }
            ?: return refuse("AnkiDroid can't find that card any more.")
        if (card.odid != 0L) return refuse(FILTERED_SOURCE_CARD)
        if (card.did == target.id) return DeckResult(true, "The card is already in \"${target.name}\".")
        try {
            provider.moveCard(nid, ord, target.id)
        } catch (e: IllegalArgumentException) {
            log("FAIL move card $nid/$ord: ${e.javaClass.simpleName}: ${e.message}")
            return refuse(if ("filtered" in (e.message ?: "").lowercase(Locale.ROOT)) FILTERED_TARGET else "AnkiDroid didn't accept the move. The card stays where it is.")
        } catch (e: Exception) { return failed("move card", e) }
        val after = try { provider.card(nid, ord) } catch (e: Exception) { return unconfirmed("move card", e) }
            ?: return unconfirmed("move card", null)
        if (after.did != target.id) {
            val where = decks.firstOrNull { it.id == after.did }?.name?.let { "\"$it\"" } ?: "its old deck"
            log("FAIL move card $nid/$ord: still in deck ${after.did} after the write")
            return DeckResult(false, "AnkiDroid did not move the card; it is still in $where. Nothing was changed.")
        }
        return DeckResult(true, "Moved the card to \"${target.name}\".", changed = true, done = 1, total = 1)
    }

    // ---- move all cards of a deck ---------------------------------------------------------------------------------

    /**
     * Moves every card that sits directly in [fromDeckId] (not the cards of its subdecks) to [toDeckId], one card at a time,
     * verifying each. The run can take minutes, so each card is read again right before it is written: a card that went into a
     * filtered deck or to another deck meanwhile is skipped and counted, never moved from stale information. Cards that sit in a
     * filtered deck at the start are skipped and counted too. A write that went out but could not be checked afterwards is counted as
     * unconfirmed (the result says so and has `changed = true`), never as "nothing was changed". [progress] gets (cardsHandled,
     * cardsToMove) after each card; [cancelled] is polled before each card, a cancel leaves the rest where it was.
     */
    fun moveAllCards(
        fromDeckId: Long, toDeckId: Long,
        progress: (done: Int, total: Int) -> Unit = { _, _ -> },
        cancelled: () -> Boolean = { false },
    ): DeckResult {
        blocked()?.let { return it }
        val decks = try { provider.decks() } catch (e: Exception) { return failed("move cards", e) }
        val from = decks.firstOrNull { it.id == fromDeckId } ?: return refuse("The deck to move from doesn't exist any more. Reload the deck list.")
        val to = decks.firstOrNull { it.id == toDeckId } ?: return refuse("The deck to move to doesn't exist any more. Reload the deck list.")
        if (to.dyn) return refuse(FILTERED_TARGET)
        if (from.dyn) return refuse("\"${from.name}\" is a filtered deck; its cards belong to other decks. Empty it in AnkiDroid first.")
        if (from.id == to.id) return DeckResult(true, "The cards are already in \"${to.name}\".")
        val found = try { provider.cardsMatching(directCardsQuery(from.name)) } catch (e: Exception) { return failed("move cards", e) }
        // Only cards that really sit in `from` right now are ever moved, whatever the search returned.
        val inFilter = found.filter { it.odid != 0L }
        val todo = found.filter { it.odid == 0L && it.did == from.id }
        val total = todo.size + inFilter.size
        val subNote = if (decks.any { it.name.startsWith(from.name + SEP, ignoreCase = true) }) " The cards of its subdecks were not touched." else ""
        if (total == 0) return DeckResult(true, "There are no cards directly in \"${from.name}\".$subNote")
        var moved = 0
        var bad = 0                 // the write failed (refused, threw, or was acknowledged but not saved)
        var unsure = 0              // the write went out, but the re-read could not confirm it
        var nowFiltered = 0         // went into a filtered deck since the search
        var changedSince = 0        // moved elsewhere or gone since the search
        var stopped = false
        var aborted: DeckResult? = null
        for ((i, c) in todo.withIndex()) {
            if (cancelled()) { stopped = true; break }
            var good = false
            var skip = false
            var wrote = false
            try {
                // Read it again right before the write: the search is minutes old on a big deck.
                val now = provider.card(c.nid, c.ord)
                when {
                    now == null -> { changedSince++; skip = true }
                    now.odid != 0L -> { nowFiltered++; skip = true }              // in a filtered deck now (its `did` is the filtered deck)
                    now.did != from.id -> { changedSince++; skip = true }
                    else -> { provider.moveCard(c.nid, c.ord, to.id); wrote = true }
                }
            } catch (e: IllegalArgumentException) {
                log("FAIL move card ${c.nid}/${c.ord}: ${e.javaClass.simpleName}: ${e.message}")
                if ("filtered" in (e.message ?: "").lowercase(Locale.ROOT)) { aborted = refuse(FILTERED_TARGET); break }
            } catch (e: Exception) {
                log("FAIL move card ${c.nid}/${c.ord}: ${e.javaClass.simpleName}: ${e.message}")
                if (e is SecurityException || e is IllegalStateException) { aborted = failed("move cards", e); break }
            }
            if (wrote) {
                // The write went out. From here on a failed check means "unconfirmed", never "not moved".
                val after = try { provider.card(c.nid, c.ord) } catch (e: Exception) {
                    log("FAIL move card ${c.nid}/${c.ord}: could not confirm (${e.javaClass.simpleName}: ${e.message})")
                    unsure++
                    if (e is SecurityException || e is IllegalStateException) { aborted = failed("move cards", e); break }
                    progress(i + 1, todo.size)
                    continue
                }
                if (after == null) {
                    log("FAIL move card ${c.nid}/${c.ord}: could not confirm (the card is gone after the write)")
                    unsure++
                } else {
                    good = after.did == to.id && after.odid == 0L
                    if (!good) { log("FAIL move card ${c.nid}/${c.ord}: still in deck ${after.did} after the write"); bad++ }
                }
            }
            if (good) moved++ else if (!skip && !wrote) bad++
            progress(i + 1, todo.size)
        }
        val filtered = inFilter.size + nowFiltered
        val skipped = filtered + changedSince
        val unsureLine = if (unsure == 0) "" else " Some moves could not be confirmed: look in AnkiDroid (and reload the deck list) before you try again."
        aborted?.let { a ->
            val tail = (if (moved > 0) " ($moved of $total cards were moved before that.)" else "") +
                (if (unsure > 0) " ($unsure ${if (unsure == 1) "move" else "moves"} could not be confirmed.)" else "")
            return a.copy(message = a.message + tail, changed = moved > 0 || unsure > 0, done = moved, total = total, skipped = skipped, failed = bad, unconfirmed = unsure)
        }
        val skipText = if (filtered == 0) "" else "$filtered ${if (filtered == 1) "is" else "are"} in a filtered deck"
        val changedText = if (changedSince == 0) "" else "$changedSince changed in AnkiDroid meanwhile"
        val failText = if (bad == 0) "" else "$bad could not be moved"
        val unsureText = if (unsure == 0) "" else "$unsure could not be confirmed"
        val notes = listOf(skipText, changedText, failText, unsureText).filter { it.isNotEmpty() }.joinToString(", ")
        val tail = if (notes.isEmpty()) "" else " ($notes)"
        val message = when {
            stopped -> "Stopped. Moved $moved of $total cards to \"${to.name}\"$tail; the rest stay in \"${from.name}\".$unsureLine$subNote"
            moved == total -> "Moved all $moved cards to \"${to.name}\".$subNote"
            moved == 0 && unsure == 0 -> "No card could be moved$tail. Nothing was changed.$subNote"
            moved == 0 -> "I could not confirm that any card was moved to \"${to.name}\"$tail.$unsureLine$subNote"
            else -> "Moved $moved of $total cards to \"${to.name}\"$tail.$unsureLine$subNote"
        }
        val ok = bad == 0 && unsure == 0 && (moved > 0 || stopped)
        return DeckResult(ok, message, changed = moved > 0 || unsure > 0, done = moved, total = total, skipped = skipped, failed = bad, cancelled = stopped, unconfirmed = unsure)
    }

    // ---- delete a note ---------------------------------------------------------------------------------------------

    /** Deletes the WHOLE note with all its cards (the API has no single-card delete) and checks that it is gone. Confirm first: no undo. */
    fun deleteNote(nid: Long): DeckResult {
        blocked()?.let { return it }
        val note = try { provider.note(nid) } catch (e: Exception) { return failed("delete note", e) }
            ?: return DeckResult(true, "That note is already gone.")
        val cards = try { provider.cardCount("nid:${note.id}") } catch (e: Exception) { return failed("delete note", e) }
        try {
            provider.deleteNote(nid)
        } catch (e: Exception) { return failed("delete note", e) }
        val still = try { provider.note(nid) } catch (e: Exception) { return unconfirmed("delete note", e) }
        if (still != null) {
            log("FAIL delete note $nid: still there after the delete")
            return DeckResult(false, "AnkiDroid did not delete the note; it is still there.")
        }
        val what = when (cards) {
            0 -> "Deleted the whole note."
            1 -> "Deleted the whole note (it had 1 card)."
            else -> "Deleted the whole note with all its $cards cards."
        }
        return DeckResult(true, what, changed = true, done = cards, total = cards)
    }

    // ---- edit a note's fields --------------------------------------------------------------------------------------

    /** Loads a note with its field names (from the note type) for the editor. */
    fun loadNote(nid: Long): NoteLoad {
        blockedText()?.let { return NoteLoad(null, it) }
        return try {
            val note = provider.note(nid) ?: return NoteLoad(null, "That note doesn't exist any more.")
            val type = provider.noteType(note.mid) ?: return NoteLoad(null, "AnkiDroid didn't tell me the fields of this note type.")
            if (type.fieldNames.size != note.fields.size) {
                NoteLoad(null, "The fields of this note don't match its note type any more. Open AnkiDroid to look at it.")
            } else {
                NoteLoad(NoteForEdit(nid, type.name, type.fieldNames, note.fields), "")
            }
        } catch (e: Exception) {
            NoteLoad(null, failed("load note", e).message)
        }
    }

    /**
     * Replaces the field texts of a note (tags stay). [fields] must have exactly one text per field of the note type, else it
     * is refused. Pass the texts the editor started from as [before] to refuse when the note was changed meanwhile. Saves nothing
     * when nothing changed; verifies by re-reading (Unicode forms ignored: AnkiDroid stores text normalised).
     */
    fun editNote(nid: Long, fields: List<String>, before: List<String>? = null): DeckResult {
        blocked()?.let { return it }
        val note = try { provider.note(nid) } catch (e: Exception) { return failed("edit note", e) }
            ?: return refuse("That note doesn't exist any more.")
        val type = try { provider.noteType(note.mid) } catch (e: Exception) { return failed("edit note", e) }
            ?: return refuse("AnkiDroid didn't tell me the fields of this note type. Nothing was changed.")
        if (fields.size != type.fieldNames.size) {
            return refuse("This note type has ${type.fieldNames.size} fields (${type.fieldNames.joinToString(", ")}) but ${fields.size} texts were given. Nothing was changed.")
        }
        if (note.fields.size != type.fieldNames.size) return refuse("The fields of this note don't match its note type. Open AnkiDroid to look at it. Nothing was changed.")
        if (before != null && before != note.fields) return refuse("The note was changed in AnkiDroid meanwhile. Open it again to edit the latest text. Nothing was changed.")
        if (fields.any { SEPARATOR in it }) return refuse("A field contains a character that notes can't hold. Nothing was changed.")
        if (fields == note.fields) return DeckResult(true, "Nothing changed, so nothing was saved.")
        if (note.fields[0].isNotBlank() && fields[0].isBlank()) {
            return refuse("The first field (${type.fieldNames[0]}) can't be left empty; Anki uses it to tell notes apart. Nothing was changed.")
        }
        try {
            provider.setFields(nid, fields)
        } catch (e: IllegalArgumentException) {
            log("FAIL edit note $nid: ${e.javaClass.simpleName}: ${e.message}")
            return refuse("AnkiDroid didn't accept the change. Nothing was changed.")
        } catch (e: Exception) { return failed("edit note", e) }
        val after = try { provider.note(nid) } catch (e: Exception) { return unconfirmed("edit note", e) }
            ?: return unconfirmed("edit note", null)
        if (after.fields.map(::nfc) == fields.map(::nfc)) return DeckResult(true, "Saved the changes to the note.", changed = true)
        log("FAIL edit note $nid: re-read differs from what was written")
        return if (after.fields == note.fields) {
            DeckResult(false, "AnkiDroid did not save the change; the note is as it was.")
        } else {
            DeckResult(false, "AnkiDroid saved something different from what was written. Check the note in AnkiDroid.", changed = true)
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private fun blockedText(): String? = try {
        when (provider.access()) {
            Access.OK -> null
            Access.NO_PERMISSION -> NO_PERMISSION
            Access.UNSUPPORTED -> UNSUPPORTED
        }
    } catch (e: Exception) { failed("check access", e).message }

    private fun blocked(): DeckResult? = blockedText()?.let { DeckResult(false, it) }

    private fun refuse(text: String) = DeckResult(false, text)

    /** A call threw: say it plainly, keep the technical detail in the log only. */
    private fun failed(what: String, e: Exception): DeckResult {
        log("FAIL $what: ${e.javaClass.simpleName}: ${e.message}")
        return DeckResult(
            false,
            when (e) {
                is SecurityException -> NO_PERMISSION
                is IllegalStateException -> "AnkiDroid isn't ready. Open AnkiDroid once, wait until it has loaded, then try again."
                else -> "AnkiDroid did not answer properly. Check in AnkiDroid before you try again."
            },
        )
    }

    /** The write went out but the re-read could not confirm it. Honest, never "done". */
    private fun unconfirmed(what: String, e: Exception?): DeckResult {
        log("FAIL $what: could not confirm${if (e != null) " (${e.javaClass.simpleName}: ${e.message})" else ""}")
        return DeckResult(false, "I could not confirm that it worked. Look in AnkiDroid before you try again.")
    }

    companion object {
        const val SEP = "::"
        private const val SEPARATOR = '\u001f'
        const val NO_PERMISSION = "AnkiGo has no permission to change AnkiDroid yet. Allow it first."
        const val UNSUPPORTED = "This needs AnkiDroid 2.24 or newer with its API switched on."
        const val FILTERED_TARGET = "Cards can't be moved into a filtered deck."
        const val FILTERED_SOURCE_CARD = "This card is in a filtered deck right now. Empty the filtered deck in AnkiDroid first."

        /** What AnkiDroid's API cannot do; the screens say so and offer to open AnkiDroid. */
        data class NotPossible(val renameDeck: String, val deleteDeck: String, val deckOptions: String)
        val notPossible = NotPossible(
            renameDeck = "Renaming a deck only works in AnkiDroid. Open AnkiDroid to do it.",
            deleteDeck = "Deleting a deck only works in AnkiDroid. Open AnkiDroid to do it.",
            deckOptions = "Deck options only work in AnkiDroid. Open AnkiDroid to change them.",
        )

        /** Why [raw] can't be a deck name, or null when it can. Parts are separated by "::" and trimmed. */
        fun nameProblem(raw: String): String? {
            val t = raw.trim()
            if (t.isEmpty()) return "Type a name for the deck."
            if (t.startsWith(SEP) || t.endsWith(SEP)) return "A deck name can't start or end with \"::\". Use \"::\" only between a deck and its subdeck, like Languages::French."
            if (t.any { it.isISOControl() }) return "A deck name can't contain control characters."
            if (t.split(SEP).any { it.isBlank() }) return "Every part of the name needs some text; there is an empty part between \"::\"."
            return null
        }

        /** The name as it will be created: whole name and each "::" part trimmed. Call only when [nameProblem] is null. */
        fun cleanName(raw: String): String = raw.trim().split(SEP).joinToString(SEP) { it.trim() }

        /** Anki search escaping for text inside quotes: backslash, quote, * and _ get a backslash. */
        fun escapeSearch(s: String): String = buildString {
            for (ch in s) {
                if (ch == '\\' || ch == '"' || ch == '*' || ch == '_') append('\\')
                append(ch)
            }
        }

        /** Search for the cards that sit directly in the deck [fullName]: the deck without its subdecks. */
        fun directCardsQuery(fullName: String): String {
            val e = escapeSearch(fullName)
            return "deck:\"$e\" -deck:\"$e::*\""
        }

        private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)
        private fun key(name: String) = nfc(name).lowercase(Locale.ROOT)
    }
}
