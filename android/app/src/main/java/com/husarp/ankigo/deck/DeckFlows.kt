package com.husarp.ankigo.deck

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.husarp.ankigo.Anki
import com.husarp.ankigo.ui.AppDialog
import com.husarp.ankigo.ui.Ui
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** A running "Move all cards". Kept for the whole app: a screen that turns or closes does not stop it, the next screen shows its progress again. */
internal class MoveAllJob(val from: String, val to: String) {
    @Volatile var done = 0
    @Volatile var total = 0
    @Volatile var result: DeckResult? = null
    val cancel = AtomicBoolean(false)
    /** The screen showing the progress (main thread); null when none does. */
    @Volatile var onChange: (() -> Unit)? = null
}

/**
 * Deck and card management as screen flows: what to ask, what to read first, what to confirm, where the answer shows. The actual changes
 * are [DeckOps]' (official AnkiDroid API only, verified, plain messages); every call to AnkiDroid here runs OFF the main thread.
 *
 * Failures never crash and never say "done" when it wasn't: they show [DeckOps]' sentence in a dialog. A missing permission or an
 * AnkiDroid that is too old to take writes is the same kind of plain message (the screens hide the entries where that is already known).
 */
internal object DeckFlows {
    private val io = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    /** The move in progress, if any (main thread). */
    @Volatile var job: MoveAllJob? = null
        private set
    private var progress: AppDialog? = null

    private fun ui(act: Activity, block: () -> Unit) = act.runOnUiThread { if (!act.isDestroyed) block() }

    /** Why AnkiDroid can't be changed right now, or null when it can. */
    private fun blocked(ctx: Context): String? = when (AnkiDeckProvider(ctx).access()) {
        DeckOps.Access.OK -> null
        DeckOps.Access.NO_PERMISSION -> DeckOps.NO_PERMISSION
        DeckOps.Access.UNSUPPORTED -> DeckOps.UNSUPPORTED
    }

    /** A read failed: one plain sentence (the technical detail goes to the report). */
    private fun plain(e: Exception): String = when (e) {
        is SecurityException -> DeckOps.NO_PERMISSION
        is IllegalStateException -> "AnkiDroid isn't ready. Open AnkiDroid once, wait until it has loaded, then try again."
        else -> "AnkiDroid did not answer properly. Check in AnkiDroid before you try again."
    }

    /** Success = a short toast; anything else = the sentence in a dialog the user can read in peace. */
    private fun report(act: Activity, failTitle: String, r: DeckResult) {
        if (r.ok) Ui.toast(act, r.message) else DeckDialogs.say(act, failTitle, r.message)
    }

    // ---- new deck / subdeck ---------------------------------------------------------------------------------------

    /** "New deck" (a plain deck, "A::B" makes a subdeck) or, with [parent], "New subdeck" inside it. [onCreated] runs after a deck was made. */
    fun newDeck(act: Activity, parent: Anki.Deck?, onCreated: (DeckResult) -> Unit) {
        DeckDialogs.Name(act, if (parent == null) "New deck" else "New subdeck", parent?.name, if (parent == null) "Name, or Parent::Child" else "Subdeck name") { typed, dlg ->
            val full = if (parent == null) typed.trim() else DeckTexts.subdeckName(parent.name, typed)
            io.execute {
                val r = deckOps(act.applicationContext).createDeck(full)
                ui(act) { if (r.ok) { dlg.done(); Ui.toast(act, r.message); onCreated(r) } else dlg.fail(r.message) }
            }
        }.show()
    }

    // ---- move one card -----------------------------------------------------------------------------------------------

    /**
     * "Move to deck": reads the decks and the card, shows the tree, moves on the button. The screen has already locked the card
     * (see [CardChange]) before calling: [onCancel] runs when the flow ends without a move (nothing allowed, a read failed, the sheet was
     * closed), [onDone] with the result when the move is over (changed = the card is somewhere else now). Nothing happens for a card that
     * sits in a filtered deck: a message says to empty the filtered deck first.
     */
    fun moveCard(act: Activity, nid: Long, ord: Int, onCancel: () -> Unit, onDone: (DeckResult) -> Unit) {
        io.execute {
            val ctx = act.applicationContext
            fun stop(m: String) { ui(act) { DeckDialogs.say(act, "Can't move the card", m); onCancel() } }
            try {
                blocked(ctx)?.let { m -> stop(m); return@execute }
                val (decks, card) = Anki.decks(ctx) to AnkiDeckProvider(ctx).card(nid, ord)
                ui(act) {
                    when {
                        card == null -> { DeckDialogs.say(act, "Can't move the card", "AnkiDroid can't find that card any more."); onCancel() }
                        card.odid != 0L -> { DeckDialogs.say(act, "Can't move the card", DeckOps.FILTERED_SOURCE_CARD); onCancel() }
                        else -> pickDeck(act, "Move to deck", decks, card.did, DeckPick.Mode.CARD, "Move", onCancel) { target, close ->
                            close()
                            io.execute {
                                val r = deckOps(ctx).moveCard(nid, ord, target.id)
                                ui(act) { report(act, "Couldn't move the card", r); onDone(r) }
                            }
                        }
                    }
                }
            } catch (e: Exception) { stop(plain(e)) }
        }
    }

    /** The picker sheet with its "+" (a new deck is made, the list reloads and the new deck is picked). [onPick] gets the deck and a way to close the sheet. */
    private fun pickDeck(act: Activity, title: String, decks: List<Anki.Deck>, currentId: Long, mode: DeckPick.Mode, action: String, onCancel: () -> Unit = {}, onPick: (Anki.Deck, () -> Unit) -> Unit) {
        var sheet: Dialog? = null
        var picked = false
        lateinit var picker: DeckPickerSheet
        picker = DeckPickerSheet(act, title, decks, currentId, mode, action, { d -> picked = true; onPick(d) { sheet?.dismiss() } }, {
            newDeck(act, null) { r ->
                io.execute {
                    val fresh = runCatching { Anki.decks(act.applicationContext) }.getOrNull()
                    if (fresh != null) ui(act) { picker.reload(fresh, r.deckId) }
                }
            }
        })
        sheet = showSheet(act, picker.content()).also { it.setOnDismissListener { if (!picked) onCancel() } }
    }

    /** A bottom sheet in its own dialog window: dim behind, at the bottom, at most 600 dp wide. */
    fun showSheet(act: Activity, content: View): Dialog {
        val dialog = Dialog(act).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        dialog.setContentView(content)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.48f); setGravity(Gravity.BOTTOM)
        }
        dialog.show()
        dialog.window?.setLayout(minOf(act.resources.displayMetrics.widthPixels, Ui.dp(act, 600)), -2)
        return dialog
    }

    // ---- delete a note --------------------------------------------------------------------------------------------------

    /**
     * "Delete this note?": says it deletes the WHOLE note with all its [homeDeck] cards and can't be undone; Delete runs [DeckOps.deleteNote].
     * [onCancel] / [onDone] as for [moveCard]: the screen has locked the card before calling (see [CardChange]), so the card can't change
     * while the notes are read and the question is open. Deleting is not an answer: nothing here touches the game or the study counts.
     */
    fun deleteNote(act: Activity, nid: Long, homeDeckId: Long, onCancel: () -> Unit, onDone: (DeckResult) -> Unit) {
        io.execute {
            val ctx = act.applicationContext
            try {
                blocked(ctx)?.let { m -> ui(act) { DeckDialogs.say(act, "Can't delete the note", m); onCancel() }; return@execute }
                val cards = try { Anki.countCards(ctx, "nid:$nid") } catch (e: Exception) { null }
                val deck = runCatching { Anki.decks(ctx).firstOrNull { it.id == homeDeckId }?.name }.getOrNull() ?: "its deck"
                ui(act) {
                    DeckDialogs.confirm(act, "Delete this note?", DeckViews.withBold(act, DeckTexts.deleteBody(cards, deck)), "Delete note", danger = true, onCancel = onCancel) {
                        io.execute {
                            val r = deckOps(ctx).deleteNote(nid)
                            ui(act) { report(act, "Couldn't delete the note", r); onDone(r) }
                        }
                    }
                }
            } catch (e: Exception) { ui(act) { DeckDialogs.say(act, "Can't delete the note", plain(e)); onCancel() } }
        }
    }

    // ---- move all cards of a deck -------------------------------------------------------------------------------------------

    /** "Move all cards…" of [from] (only the cards directly in it): pick the deck, confirm, then a progress card with Cancel. [onFinished] when the card is closed. */
    fun moveAll(act: Activity, from: Anki.Deck, onFinished: () -> Unit) {
        if (job != null) { resume(act, onFinished); return }
        io.execute {
            val ctx = act.applicationContext
            blocked(ctx)?.let { m -> ui(act) { DeckDialogs.say(act, "Can't move the cards", m) }; return@execute }
            val read = try { Anki.decks(ctx) to Anki.countCards(ctx, DeckOps.directCardsQuery(from.name)) } catch (e: Exception) { ui(act) { DeckDialogs.say(act, "Can't move the cards", plain(e)) }; return@execute }
            val (decks, count) = read
            val hasSub = decks.any { it.name.startsWith(from.name + DeckOps.SEP, ignoreCase = true) }
            ui(act) {
                if (count == 0) DeckDialogs.say(act, "Nothing to move", DeckTexts.nothingToMove(from.name, hasSub))
                else pickDeck(act, "Move all cards to", decks, from.id, DeckPick.Mode.ALL, "Move") { to, close ->
                    close()
                    DeckDialogs.confirm(act, "Move all cards?", DeckViews.withBold(act, DeckTexts.moveAllBody(count, from.name, to.name, hasSub)), "Move cards", danger = false) {
                        start(act, ctx, from, to, onFinished)
                    }
                }
            }
        }
    }

    private fun start(act: Activity, ctx: Context, from: Anki.Deck, to: Anki.Deck, onFinished: () -> Unit) {
        val j = MoveAllJob(from.name, to.name)
        job = j
        Thread({
            val r = deckOps(ctx).moveAllCards(from.id, to.id, { d, t -> j.done = d; j.total = t; main.post { j.onChange?.invoke() } }, { j.cancel.get() })
            j.result = r
            main.post { j.onChange?.invoke() }
        }, "ankigo-move-all").start()
        showProgress(act, j, onFinished)
    }

    private fun showProgress(act: Activity, j: MoveAllJob, onFinished: () -> Unit) {
        progress?.dismiss()
        val p = DeckDialogs.MoveProgress(act, j) { job = null; progress = null; j.onChange = null; onFinished() }
        progress = p.show()
        j.onChange = { if (!act.isDestroyed) p.render() }
    }

    /** A screen came up while a move is running (or finished unseen): show its progress card again. No-op without a job. */
    fun resume(act: Activity, onFinished: () -> Unit) { job?.let { showProgress(act, it, onFinished) } }

    /** The screen goes away: take the progress card down (the move itself carries on). */
    fun detach() { progress?.dismiss(); progress = null; job?.onChange = null }
}
