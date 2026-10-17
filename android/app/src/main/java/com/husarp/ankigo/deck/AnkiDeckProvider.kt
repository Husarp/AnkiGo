package com.husarp.ankigo.deck

import android.content.Context
import com.husarp.ankigo.Anki
import com.husarp.ankigo.Report

/** The real [DeckOps.Provider]: thin calls into [Anki] (official FlashCardsContract only). Not unit-tested; the logic lives in [DeckOps]. */
class AnkiDeckProvider(private val ctx: Context) : DeckOps.Provider {
    override fun access() = when {
        !Anki.hasPermission(ctx) -> DeckOps.Access.NO_PERMISSION
        !Anki.studySupported(ctx) -> DeckOps.Access.UNSUPPORTED
        else -> DeckOps.Access.OK
    }

    override fun decks() = Anki.decks(ctx).map { DeckOps.DeckRef(it.id, it.name, it.dyn) }
    override fun createDeck(name: String, desc: String?) = Anki.createDeck(ctx, name, desc)
    override fun card(nid: Long, ord: Int) = Anki.card(ctx, nid, ord, DeckOps.CardRef.COLS)?.let { DeckOps.CardRef.from(it) }
    override fun cardsMatching(query: String) = Anki.searchCards(ctx, query, DeckOps.CardRef.COLS).mapNotNull { DeckOps.CardRef.from(it) }
    override fun cardCount(query: String) = Anki.countCards(ctx, query)
    override fun moveCard(nid: Long, ord: Int, deckId: Long) { Anki.moveCard(ctx, nid, ord, deckId) }
    override fun note(nid: Long) = Anki.note(ctx, nid)?.let { DeckOps.NoteRef(it.id, it.mid, it.fields) }
    override fun noteType(mid: Long) = Anki.model(ctx, mid)?.let { DeckOps.NoteType(it.name, it.fieldNames) }
    override fun setFields(nid: Long, fields: List<String>) { Anki.updateNoteFields(ctx, nid, fields) }
    override fun deleteNote(nid: Long) { Anki.deleteNote(ctx, nid) }
}

/** Ready to call from a background thread: deck and card management with the failures written to the in-app report. */
fun deckOps(ctx: Context) = DeckOps(AnkiDeckProvider(ctx)) { Report.log("DECK", it) }
