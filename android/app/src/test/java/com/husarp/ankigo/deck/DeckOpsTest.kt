package com.husarp.ankigo.deck

import com.husarp.ankigo.deck.DeckOps.CardRef
import com.husarp.ankigo.deck.DeckOps.DeckRef
import org.junit.Assert.*
import org.junit.Test

/** DeckOps against a fake AnkiDroid (made-up decks and notes only). */
class DeckOpsTest {
    private class Fake : DeckOps.Provider {
        var access = DeckOps.Access.OK
        val decks = mutableListOf(DeckRef(1, "Default", false), DeckRef(2, "Spanish", false), DeckRef(3, "Spanish::Verbs", false), DeckRef(9, "Review mix", true))
        val cards = mutableListOf<CardRef>()
        val notes = LinkedHashMap<Long, DeckOps.NoteRef>()
        val types = HashMap<Long, DeckOps.NoteType>()
        var nextDeck = 100L
        val writes = mutableListOf<String>()
        val queries = mutableListOf<String>()
        var search: List<CardRef>? = null

        var createThrows: Exception? = null
        var createSilently = false
        var moveThrows: Exception? = null
        var moveSilentFor = emptySet<Long>()     // note ids whose move is acknowledged but not saved
        var moveThrowsFor = emptySet<Long>()     // note ids whose move throws a plain failure
        var readThrows: Exception? = null
        var setFieldsThrows: Exception? = null
        var setFieldsSilently = false
        var setFieldsMangle: (String) -> String = { it }
        var deleteSilently = false
        var cardAfterMoveGone = false
        var readThrowsAfterMoveFor = emptySet<Long>()   // note ids whose re-read throws once their move was written
        var afterMove: (Long) -> Unit = {}              // runs after a move write (another app changing things meanwhile)

        override fun access() = access
        override fun decks() = decks.toList()
        override fun createDeck(name: String, desc: String?): Long {
            writes += "create:$name"
            createThrows?.let { throw it }
            val id = nextDeck++
            if (!createSilently) {
                val parts = name.split("::")
                for (i in 1 until parts.size) {
                    val p = parts.take(i).joinToString("::")
                    if (decks.none { it.name.equals(p, true) }) decks += DeckRef(nextDeck++, p, false)
                }
                decks += DeckRef(id, name, false)
            }
            return id
        }
        override fun card(nid: Long, ord: Int): CardRef? {
            readThrows?.let { throw it }
            if (cardAfterMoveGone && writes.any { it.startsWith("move:") }) return null
            if (nid in readThrowsAfterMoveFor && writes.any { it.startsWith("move:$nid/") }) throw RuntimeException("slow")
            return cards.firstOrNull { it.nid == nid && it.ord == ord }
        }
        override fun cardsMatching(query: String): List<CardRef> { queries += query; return search ?: cards.toList() }
        override fun cardCount(query: String): Int { queries += query; return cards.count { query == "nid:${it.nid}" } }
        override fun moveCard(nid: Long, ord: Int, deckId: Long) {
            writes += "move:$nid/$ord->$deckId"
            moveThrows?.let { throw it }
            if (nid in moveThrowsFor) throw RuntimeException("boom")
            if (decks.firstOrNull { it.id == deckId }?.dyn == true) throw IllegalArgumentException("Cards cannot be moved to a filtered deck")
            if (nid in moveSilentFor) return
            val i = cards.indexOfFirst { it.nid == nid && it.ord == ord }
            cards[i] = cards[i].copy(did = deckId)
            afterMove(nid)
        }
        override fun note(nid: Long): DeckOps.NoteRef? { readThrows?.let { throw it }; return notes[nid] }
        override fun noteType(mid: Long) = types[mid]
        override fun setFields(nid: Long, fields: List<String>) {
            writes += "fields:$nid"
            setFieldsThrows?.let { throw it }
            if (setFieldsSilently) return
            val n = notes.getValue(nid)
            notes[nid] = DeckOps.NoteRef(n.id, n.mid, fields.map(setFieldsMangle))
        }
        override fun deleteNote(nid: Long) {
            writes += "delete:$nid"
            if (deleteSilently) return
            notes.remove(nid); cards.removeAll { it.nid == nid }
        }
    }

    private fun ops(f: Fake, log: MutableList<String>? = null) = DeckOps(f) { log?.add(it) }
    private fun card(id: Long, nid: Long, ord: Int = 0, did: Long = 2, odid: Long = 0) = CardRef(id, nid, ord, did, odid)

    // ---- create deck ------------------------------------------------------------------------------------------

    @Test fun createOk() {
        val f = Fake()
        val r = ops(f).createDeck("  French  ")
        assertTrue(r.message, r.ok); assertTrue(r.changed)
        assertEquals("Created the deck \"French\".", r.message)
        assertEquals(listOf("create:French"), f.writes)
        assertEquals(f.decks.first { it.name == "French" }.id, r.deckId)
    }

    @Test fun createSubdeckTrimsEachPartAndMentionsNewParent() {
        val f = Fake()
        val r = ops(f).createDeck("Languages ::  French ")
        assertTrue(r.message, r.ok)
        assertEquals(listOf("create:Languages::French"), f.writes)
        assertTrue(r.message, r.message.contains("\"Languages\" was created too"))
        // an existing parent is not mentioned
        val g = Fake()
        val s = ops(g).createDeck("Spanish::Nouns")
        assertEquals("Created the deck \"Spanish::Nouns\".", s.message)
    }

    @Test fun createRefusesBadNamesWithoutCallingAnkiDroid() {
        val f = Fake()
        val o = ops(f)
        for (bad in listOf("", "   ", "::", "A::", "::A", "A::::B", "A:: ::B", "Tab\tName", "A\u001fB")) {
            val r = o.createDeck(bad)
            assertFalse("'$bad' should be refused", r.ok)
            assertFalse(r.changed)
        }
        assertTrue(f.writes.isEmpty())
        assertEquals("Type a name for the deck.", o.createDeck(" ").message)
        assertTrue(o.createDeck("A::").message.contains("start or end"))
        assertTrue(o.createDeck("A::  ::B").message.contains("empty part"))
    }

    @Test fun createRefusesDuplicateIgnoringCase() {
        val f = Fake()
        for (dup in listOf("Spanish", "spanish", "SPANISH :: verbs", "Spanish::Verbs")) {
            val r = ops(f).createDeck(dup)
            assertFalse(dup, r.ok)
            assertTrue(r.message, r.message.contains("already exists"))
        }
        assertTrue(f.writes.isEmpty())
    }

    @Test fun createRefusesFilteredParent() {
        val f = Fake()
        val r = ops(f).createDeck("review MIX::Sub")
        assertFalse(r.ok)
        assertTrue(r.message, r.message.contains("filtered deck"))
        assertTrue(f.writes.isEmpty())
    }

    @Test fun createMapsAnkiDroidExceptionsToSentences() {
        fun run(e: Exception): DeckResult {
            val f = Fake().apply { createThrows = e }
            return ops(f).createDeck("New")
        }
        assertTrue(run(IllegalArgumentException("Deck name already exists: New")).message.contains("already exists"))
        assertTrue(run(IllegalArgumentException("Invalid deck name 'New'")).message.contains("doesn't accept"))
        assertTrue(run(IllegalArgumentException("Cannot create a deck inside a filtered deck")).message.contains("filtered"))
        assertEquals("AnkiDroid didn't accept that deck name.", run(IllegalArgumentException("weird")).message)
        assertEquals(DeckOps.NO_PERMISSION, run(SecurityException("Permission not granted for: x")).message)
        assertTrue(run(IllegalStateException("AnkiDroid storage is not configured")).message.contains("isn't ready"))
        val other = run(RuntimeException("db is locked"))
        assertFalse(other.ok); assertFalse(other.message.contains("locked")); assertFalse(other.message.contains("Exception"))
        for (e in listOf(IllegalArgumentException("a"), SecurityException("b"), RuntimeException("c"))) assertFalse(run(e).changed)
    }

    @Test fun createVerifyFailedIsNotReportedAsDone() {
        val f = Fake().apply { createSilently = true }
        val log = mutableListOf<String>()
        val r = ops(f, log).createDeck("Ghost")
        assertFalse(r.ok); assertFalse(r.changed)
        assertTrue(r.message, r.message.contains("could not confirm"))
        assertTrue(log.any { it.contains("could not confirm") })
    }

    @Test fun createFindsDeckByNameWhenIdDiffers() {
        // the deck is in the list under another id than the one returned: still confirmed (by name)
        val inner = Fake()
        var created = false
        val f = object : DeckOps.Provider by inner {
            override fun decks() = inner.decks() + if (created) listOf(DeckRef(555, "Odd", false)) else emptyList()
            override fun createDeck(name: String, desc: String?): Long { created = true; return 7L }
        }
        val r = DeckOps(f).createDeck("Odd")
        assertTrue(r.message, r.ok); assertEquals(555L, r.deckId)
    }

    @Test fun nothingRunsWithoutPermissionOrSupport() {
        val f = Fake()
        f.access = DeckOps.Access.NO_PERMISSION
        val o = ops(f)
        assertEquals(DeckOps.NO_PERMISSION, o.createDeck("X").message)
        assertEquals(DeckOps.NO_PERMISSION, o.moveCard(1, 0, 2).message)
        assertEquals(DeckOps.NO_PERMISSION, o.moveAllCards(2, 1).message)
        assertEquals(DeckOps.NO_PERMISSION, o.deleteNote(1).message)
        assertEquals(DeckOps.NO_PERMISSION, o.editNote(1, listOf("a")).message)
        assertEquals(DeckOps.NO_PERMISSION, o.loadNote(1).message)
        f.access = DeckOps.Access.UNSUPPORTED
        assertEquals(DeckOps.UNSUPPORTED, o.createDeck("X").message)
        assertEquals(DeckOps.UNSUPPORTED, o.deleteNote(1).message)
        assertTrue(f.writes.isEmpty())
        assertTrue(f.queries.isEmpty())
    }

    // ---- move one card -------------------------------------------------------------------------------------------

    @Test fun moveOneOk() {
        val f = Fake().apply { cards += card(10, 1, did = 1) }
        val r = ops(f).moveCard(1, 0, 3)
        assertTrue(r.message, r.ok); assertTrue(r.changed)
        assertEquals("Moved the card to \"Spanish::Verbs\".", r.message)
        assertEquals(3L, f.cards[0].did)
        assertEquals(listOf("move:1/0->3"), f.writes)
    }

    @Test fun moveOneRefusesFilteredTarget() {
        val f = Fake().apply { cards += card(10, 1, did = 1) }
        val r = ops(f).moveCard(1, 0, 9)
        assertFalse(r.ok)
        assertEquals("Cards can't be moved into a filtered deck.", r.message)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun moveOneRefusesCardInFilteredDeck() {
        val f = Fake().apply { cards += card(10, 1, did = 9, odid = 2) }
        val r = ops(f).moveCard(1, 0, 3)
        assertFalse(r.ok)
        assertTrue(r.message, r.message.contains("Empty the filtered deck in AnkiDroid first"))
        assertTrue(f.writes.isEmpty())
    }

    @Test fun moveOneAlreadyThereIsANoOp() {
        val f = Fake().apply { cards += card(10, 1, did = 3) }
        val r = ops(f).moveCard(1, 0, 3)
        assertTrue(r.ok); assertFalse(r.changed)
        assertTrue(r.message, r.message.contains("already in"))
        assertTrue(f.writes.isEmpty())
    }

    @Test fun moveOneVerifyFailsIsHonest() {
        val f = Fake().apply { cards += card(10, 1, did = 1); moveSilentFor = setOf(1L) }
        val r = ops(f).moveCard(1, 0, 3)
        assertFalse(r.ok); assertFalse(r.changed)
        assertTrue(r.message, r.message.contains("did not move the card"))
        assertTrue(r.message, r.message.contains("\"Default\""))
    }

    @Test fun moveOneUnknownTargetMissingCardAndProviderErrors() {
        val f = Fake().apply { cards += card(10, 1, did = 1) }
        assertTrue(ops(f).moveCard(1, 0, 404).message.contains("doesn't exist"))
        assertTrue(ops(f).moveCard(77, 0, 3).message.contains("can't find that card"))
        f.moveThrows = IllegalArgumentException("Cards cannot be moved to a filtered deck")
        assertEquals(DeckOps.FILTERED_TARGET, ops(f).moveCard(1, 0, 3).message)
        f.moveThrows = IllegalArgumentException("whatever")
        assertTrue(ops(f).moveCard(1, 0, 3).message.contains("didn't accept"))
        f.moveThrows = null; f.readThrows = RuntimeException("x")
        assertFalse(ops(f).moveCard(1, 0, 3).ok)
    }

    @Test fun moveOneReReadFailureAfterWriteIsUnconfirmedNotDone() {
        val f = Fake().apply { cards += card(10, 1, did = 1); cardAfterMoveGone = true }
        val r = ops(f).moveCard(1, 0, 3)
        assertFalse(r.ok); assertFalse(r.changed)
        assertTrue(r.message, r.message.contains("could not confirm"))
    }

    // ---- move all cards of a deck --------------------------------------------------------------------------------

    @Test fun moveAllQueryExcludesSubdecks() {
        assertEquals("deck:\"Spanish\" -deck:\"Spanish::*\"", DeckOps.directCardsQuery("Spanish"))
        val f = Fake().apply { cards += card(10, 1, did = 2) }
        ops(f).moveAllCards(2, 1)
        assertEquals(listOf("deck:\"Spanish\" -deck:\"Spanish::*\""), f.queries)
    }

    @Test fun searchEscaping() {
        assertEquals("a\\\\b", DeckOps.escapeSearch("a\\b"))
        assertEquals("say \\\"hi\\\"", DeckOps.escapeSearch("say \"hi\""))
        assertEquals("100\\* \\_x", DeckOps.escapeSearch("100* _x"))
        assertEquals("plain::sub (1) - é", DeckOps.escapeSearch("plain::sub (1) - é"))
        assertEquals("deck:\"A\\_B\\*\" -deck:\"A\\_B\\*::*\"", DeckOps.directCardsQuery("A_B*"))
    }

    @Test fun moveAllMovesVerifiesAndReportsProgress() {
        val f = Fake().apply { for (i in 1..4) cards += card(i.toLong(), i.toLong(), did = 2) }
        val seen = mutableListOf<Pair<Int, Int>>()
        val r = ops(f).moveAllCards(2, 1, { d, t -> seen += d to t })
        assertTrue(r.message, r.ok); assertTrue(r.changed)
        assertEquals("Moved all 4 cards to \"Default\". The cards of its subdecks were not touched.", r.message)
        assertEquals(listOf(1 to 4, 2 to 4, 3 to 4, 4 to 4), seen)
        assertTrue(f.cards.all { it.did == 1L })
        assertEquals(4, r.done); assertEquals(4, r.total)
    }

    @Test fun moveAllNeverTouchesCardsOfSubdecksOrOtherDecks() {
        // even if the search returned them, only cards directly in the source deck move
        val f = Fake().apply {
            cards += card(1, 1, did = 2); cards += card(2, 2, did = 3); cards += card(3, 3, did = 1)
        }
        val r = ops(f).moveAllCards(2, 1)
        assertTrue(r.ok)
        assertEquals(listOf("move:1/0->1"), f.writes)
        assertEquals(3L, f.cards[1].did)
        assertTrue(r.message, r.message.contains("subdecks were not touched"))
    }

    @Test fun moveAllMixedResultsText() {
        val f = Fake().apply {
            cards += card(1, 1, did = 2); cards += card(2, 2, did = 2); cards += card(3, 3, did = 2)
            cards += card(4, 4, did = 9, odid = 2); cards += card(5, 5, did = 9, odid = 2)
            moveSilentFor = setOf(3L)
        }
        val r = ops(f).moveAllCards(2, 1)
        assertFalse(r.ok); assertTrue(r.changed)
        assertEquals("Moved 2 of 5 cards to \"Default\" (2 are in a filtered deck, 1 could not be moved). The cards of its subdecks were not touched.", r.message)
        assertEquals(2, r.done); assertEquals(5, r.total); assertEquals(2, r.skipped); assertEquals(1, r.failed)
        assertEquals(listOf("move:1/0->1", "move:2/0->1", "move:3/0->1"), f.writes)
    }

    @Test fun moveAllOnlyFilteredCardsSaysNothingMoved() {
        val f = Fake().apply { cards += card(4, 4, did = 9, odid = 2); decks.removeAll { it.id == 3L } }
        val r = ops(f).moveAllCards(2, 1)
        assertFalse(r.ok); assertFalse(r.changed)
        assertEquals("No card could be moved (1 is in a filtered deck). Nothing was changed.", r.message)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun moveAllCancelMidWay() {
        val f = Fake().apply { for (i in 1..5) cards += card(i.toLong(), i.toLong(), did = 2); decks.removeAll { it.id == 3L } }
        var handled = 0
        val r = ops(f).moveAllCards(2, 1, { d, _ -> handled = d }, { handled >= 2 })
        assertTrue(r.message, r.ok); assertTrue(r.cancelled); assertTrue(r.changed)
        assertEquals(2, r.done); assertEquals(5, r.total)
        assertEquals("Stopped. Moved 2 of 5 cards to \"Default\"; the rest stay in \"Spanish\".", r.message)
        assertEquals(3, f.cards.count { it.did == 2L })
    }

    @Test fun moveAllRefusals() {
        val f = Fake().apply { cards += card(1, 1, did = 2) }
        val o = ops(f)
        assertEquals(DeckOps.FILTERED_TARGET, o.moveAllCards(2, 9).message)
        assertTrue(o.moveAllCards(9, 2).message.contains("filtered deck"))
        assertTrue(o.moveAllCards(2, 2).ok)
        assertFalse(o.moveAllCards(2, 2).changed)
        assertTrue(o.moveAllCards(404, 1).message.contains("doesn't exist"))
        assertTrue(o.moveAllCards(2, 404).message.contains("doesn't exist"))
        assertTrue(f.writes.isEmpty())
    }

    @Test fun moveAllEmptyDeck() {
        val f = Fake()
        val r = ops(f).moveAllCards(1, 2)
        assertTrue(r.ok); assertFalse(r.changed)
        assertEquals("There are no cards directly in \"Default\".", r.message)
    }

    @Test fun moveAllStopsWhenPermissionDisappears() {
        val f = Fake().apply { for (i in 1..3) cards += card(i.toLong(), i.toLong(), did = 2); moveThrows = SecurityException("no") }
        val r = ops(f).moveAllCards(2, 1)
        assertFalse(r.ok); assertFalse(r.changed)
        assertTrue(r.message, r.message.startsWith(DeckOps.NO_PERMISSION))
        assertEquals(1, f.writes.size)
    }

    @Test fun moveAllCountsAPlainFailureAndKeepsGoing() {
        val f = Fake().apply { for (i in 1..3) cards += card(i.toLong(), i.toLong(), did = 2); moveThrowsFor = setOf(2L); decks.removeAll { it.id == 3L } }
        val r = ops(f).moveAllCards(2, 1)
        assertFalse(r.ok); assertTrue(r.changed)
        assertEquals("Moved 2 of 3 cards to \"Default\" (1 could not be moved).", r.message)
    }

    @Test fun moveAllRereadsEachCardAndSkipsOneThatEnteredAFilteredDeckMeanwhile() {
        // the search saw cards 1-3 in "Spanish"; while card 1 moves, card 2 is pulled into a filtered deck (original deck = Spanish)
        val f = Fake().apply { for (i in 1..3) cards += card(i.toLong(), i.toLong(), did = 2); decks.removeAll { it.id == 3L } }
        f.afterMove = { nid -> if (nid == 1L) f.cards[1] = card(2, 2, did = 9, odid = 2) }
        val r = ops(f).moveAllCards(2, 1)
        assertEquals(listOf("move:1/0->1", "move:3/0->1"), f.writes)          // card 2 was never written
        assertEquals(9L, f.cards[1].did); assertEquals(2L, f.cards[1].odid)    // and is exactly as AnkiDroid had it
        assertTrue(r.changed); assertEquals(2, r.done); assertEquals(3, r.total); assertEquals(1, r.skipped); assertEquals(0, r.failed)
        assertEquals("Moved 2 of 3 cards to \"Default\" (1 is in a filtered deck).", r.message)
    }

    @Test fun moveAllSkipsACardThatMovedElsewhereOrVanishedMeanwhile() {
        val f = Fake().apply { for (i in 1..4) cards += card(i.toLong(), i.toLong(), did = 2); decks.removeAll { it.id == 3L } }
        f.afterMove = { nid ->
            if (nid == 1L) { f.cards[1] = card(2, 2, did = 5); f.cards.removeAll { it.nid == 3L } }   // 2 moved to another deck, 3 deleted
        }
        val r = ops(f).moveAllCards(2, 1)
        assertEquals(listOf("move:1/0->1", "move:4/0->1"), f.writes)
        assertEquals(5L, f.cards.first { it.nid == 2L }.did)
        assertTrue(r.changed); assertEquals(2, r.done); assertEquals(4, r.total); assertEquals(2, r.skipped); assertEquals(0, r.failed)
        assertEquals("Moved 2 of 4 cards to \"Default\" (2 changed in AnkiDroid meanwhile).", r.message)
    }

    @Test fun moveAllOnlyChangedCardsSaysNothingWasChanged() {
        val f = Fake().apply { cards += card(1, 1, did = 2); decks.removeAll { it.id == 3L } }
        f.search = listOf(card(1, 1, did = 2))
        f.cards[0] = card(1, 1, did = 9, odid = 2)       // filtered after the search, before the first write
        val r = ops(f).moveAllCards(2, 1)
        assertFalse(r.ok); assertFalse(r.changed)
        assertEquals("No card could be moved (1 is in a filtered deck). Nothing was changed.", r.message)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun moveAllWriteThatCannotBeCheckedIsUnconfirmedNotNothingChanged() {
        val f = Fake().apply { cards += card(1, 1, did = 2); decks.removeAll { it.id == 3L }; readThrowsAfterMoveFor = setOf(1L) }
        val log = mutableListOf<String>()
        val r = ops(f, log).moveAllCards(2, 1)
        assertFalse(r.ok); assertTrue(r.changed)
        assertEquals(1, r.unconfirmed); assertEquals(0, r.failed); assertEquals(0, r.done)
        assertTrue(r.message, r.message.contains("could not be confirmed"))
        assertFalse(r.message, r.message.contains("Nothing was changed"))
        assertEquals(1, f.cards[0].did.toInt())            // the write really did go through
        assertTrue(log.any { it.contains("could not confirm") })
    }

    @Test fun moveAllCountsUnconfirmedMovesAmongGoodOnes() {
        val f = Fake().apply { for (i in 1..3) cards += card(i.toLong(), i.toLong(), did = 2); decks.removeAll { it.id == 3L }; readThrowsAfterMoveFor = setOf(2L) }
        val r = ops(f).moveAllCards(2, 1)
        assertFalse(r.ok); assertTrue(r.changed)
        assertEquals(2, r.done); assertEquals(3, r.total); assertEquals(1, r.unconfirmed); assertEquals(0, r.failed)
        assertTrue(r.message, r.message.startsWith("Moved 2 of 3 cards to \"Default\" (1 could not be confirmed)."))
        assertTrue(r.message, r.message.contains("Some moves could not be confirmed"))
    }

    @Test fun moveAllPermissionLostWhileCheckingStillReportsChanged() {
        val f = Fake().apply { cards += card(1, 1, did = 2); cards += card(2, 2, did = 2); decks.removeAll { it.id == 3L } }
        var armed = false
        f.afterMove = { armed = true }
        val o = DeckOps(object : DeckOps.Provider by f {
            override fun card(nid: Long, ord: Int): CardRef? { if (armed) throw SecurityException("revoked"); return f.card(nid, ord) }
        })
        val r = o.moveAllCards(2, 1)
        assertFalse(r.ok); assertTrue(r.changed)
        assertEquals(1, r.unconfirmed); assertEquals(1, f.writes.size)
        assertTrue(r.message, r.message.startsWith(DeckOps.NO_PERMISSION))
        assertTrue(r.message, r.message.contains("could not be confirmed"))
    }

    // ---- delete a note --------------------------------------------------------------------------------------------

    private fun noteFake(): Fake = Fake().apply {
        types[5] = DeckOps.NoteType("Basic (and reversed)", listOf("Front", "Back"))
        notes[1] = DeckOps.NoteRef(1, 5, listOf("hola", "hello"))
        notes[2] = DeckOps.NoteRef(2, 5, listOf("adios", "bye"))
        cards += card(10, 1, 0); cards += card(11, 1, 1); cards += card(12, 2, 0)
    }

    @Test fun deleteCountsCardsAndVerifies() {
        val f = noteFake()
        val r = ops(f).deleteNote(1)
        assertTrue(r.message, r.ok); assertTrue(r.changed)
        assertEquals("Deleted the whole note with all its 2 cards.", r.message)
        assertEquals(2, r.done)
        assertNull(f.notes[1]); assertNotNull(f.notes[2])
        assertEquals(1, f.cards.size)
        assertTrue(f.queries.contains("nid:1"))
    }

    @Test fun deleteSingleCardNote() {
        val f = noteFake()
        assertEquals("Deleted the whole note (it had 1 card).", ops(f).deleteNote(2).message)
    }

    @Test fun deleteVerifyFails() {
        val f = noteFake().apply { deleteSilently = true }
        val r = ops(f).deleteNote(1)
        assertFalse(r.ok); assertFalse(r.changed)
        assertTrue(r.message, r.message.contains("still there"))
    }

    @Test fun deleteMissingNoteIsAlreadyGone() {
        val f = noteFake()
        val r = ops(f).deleteNote(99)
        assertTrue(r.ok); assertFalse(r.changed)
        assertTrue(f.writes.isEmpty())
    }

    // ---- edit a note ---------------------------------------------------------------------------------------------

    @Test fun loadNoteGivesFieldNames() {
        val f = noteFake()
        val l = ops(f).loadNote(1)
        assertEquals(listOf("Front", "Back"), l.note!!.fieldNames)
        assertEquals(listOf("hola", "hello"), l.note!!.values)
        assertEquals("Basic (and reversed)", l.note!!.noteTypeName)
        assertNull(ops(f).loadNote(99).note)
        f.notes[1] = DeckOps.NoteRef(1, 5, listOf("only one"))
        assertNull(ops(f).loadNote(1).note)
        f.types.clear()
        assertNull(ops(f).loadNote(2).note)
    }

    @Test fun editRefusesWrongFieldCount() {
        val f = noteFake()
        val r = ops(f).editNote(1, listOf("only one"))
        assertFalse(r.ok)
        assertTrue(r.message, r.message.contains("2 fields (Front, Back) but 1 texts"))
        assertFalse(ops(f).editNote(1, listOf("a", "b", "c")).ok)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun editUnchangedWritesNothing() {
        val f = noteFake()
        val r = ops(f).editNote(1, listOf("hola", "hello"))
        assertTrue(r.ok); assertFalse(r.changed)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun editOkVerifiedByReReading() {
        val f = noteFake()
        val r = ops(f).editNote(1, listOf("hola!", "hello<br>there"), before = listOf("hola", "hello"))
        assertTrue(r.message, r.ok); assertTrue(r.changed)
        assertEquals(listOf("hola!", "hello<br>there"), f.notes[1]!!.fields)
        assertEquals(listOf("fields:1"), f.writes)
    }

    @Test fun editAcceptsUnicodeNormalisation() {
        val f = noteFake().apply { setFieldsMangle = { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFC) } }
        val decomposed = "café"   // e + combining accent, stored back as the single character
        val r = ops(f).editNote(1, listOf(decomposed, "x"))
        assertTrue(r.message, r.ok); assertTrue(r.changed)
    }

    @Test fun editVerifyFailsWhenNothingWasSaved() {
        val f = noteFake().apply { setFieldsSilently = true }
        val r = ops(f).editNote(1, listOf("new", "text"))
        assertFalse(r.ok); assertFalse(r.changed)
        assertTrue(r.message, r.message.contains("note is as it was"))
    }

    @Test fun editVerifyFailsWhenSomethingElseWasSaved() {
        val f = noteFake().apply { setFieldsMangle = { it.uppercase() } }
        val r = ops(f).editNote(1, listOf("new", "text"))
        assertFalse(r.ok); assertTrue(r.changed)
        assertTrue(r.message, r.message.contains("something different"))
    }

    @Test fun editRefusesStaleEditorFieldSeparatorAndEmptyFirstField() {
        val f = noteFake()
        assertTrue(ops(f).editNote(1, listOf("new", "text"), before = listOf("old", "stuff")).message.contains("changed in AnkiDroid meanwhile"))
        assertTrue(ops(f).editNote(1, listOf("new\u001fx", "text")).message.contains("character"))
        assertTrue(ops(f).editNote(1, listOf("  ", "text")).message.contains("first field (Front)"))
        // a note whose first field is already empty may be edited elsewhere
        f.notes[2] = DeckOps.NoteRef(2, 5, listOf("", "back"))
        assertTrue(ops(f).editNote(2, listOf("", "back changed")).ok)
        assertEquals(1, f.writes.size)
    }

    @Test fun editProviderErrorsAreSentences() {
        val f = noteFake().apply { setFieldsThrows = IllegalArgumentException("Incorrect flds argument : x") }
        val r = ops(f).editNote(1, listOf("a", "b"))
        assertFalse(r.ok); assertFalse(r.message.contains("flds"))
        f.setFieldsThrows = SecurityException("no")
        assertEquals(DeckOps.NO_PERMISSION, ops(f).editNote(1, listOf("a", "b")).message)
        assertTrue(ops(f).editNote(404, listOf("a", "b")).message.contains("doesn't exist"))
    }

    // ---- small things --------------------------------------------------------------------------------------------

    @Test fun cardRefFromRow() {
        val c = CardRef.from(mapOf("_id" to "5", "note_id" to "7", "ord" to "1", "deck_id" to "3", "original_deck_id" to "0"))
        assertEquals(CardRef(5, 7, 1, 3, 0), c)
        assertEquals(4L, CardRef.from(mapOf("_id" to "5", "note_id" to "7", "ord" to "1", "deck_id" to "9", "original_deck_id" to "4"))!!.odid)
        assertEquals(0L, CardRef.from(mapOf("_id" to "5", "note_id" to "7", "ord" to "1", "deck_id" to "9"))!!.odid)
        assertNull(CardRef.from(mapOf("_id" to "5")))   // card deleted after the search
    }

    @Test fun cleanNameTrimsParts() {
        assertEquals("A::B c::D", DeckOps.cleanName("  A ::  B c::D  "))
        assertNull(DeckOps.nameProblem("A:::B"))
        assertNull(DeckOps.nameProblem("Kanji (N5) - \"quotes\" _star*"))
    }

    @Test fun notPossibleTextsPointToAnkiDroid() {
        val n = DeckOps.notPossible
        for (t in listOf(n.renameDeck, n.deleteDeck, n.deckOptions)) assertTrue(t, t.contains("only work") && t.contains("Open AnkiDroid"))
        assertTrue(n.renameDeck.contains("Renaming"))
        assertTrue(n.deleteDeck.contains("Deleting"))
        assertTrue(n.deckOptions.contains("options"))
    }
}
