package com.husarp.ankigo.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.StudyActivity

/**
 * "Continue studying" slim bar under the battle screen's top bar (PLAN 92): ▶, the last studied deck and how many cards are
 * due; a tap studies it like the Study tab's CONTINUE card, × hides it until the next game day. Pure rule in [show].
 */
object ContinueBar {
    private const val PREFS = "study"
    private const val KEY_HIDDEN = "continueBarHiddenDay"

    /** Show the bar only for a known last deck that has cards due and was not hidden today (game day key). */
    fun show(lastDeck: Long, due: Int, hiddenDay: String?, today: String): Boolean =
        lastDeck != 0L && due > 0 && hiddenDay != today

    fun title(deckName: String): String = "Continue · " + deckName.substringAfterLast("::")
    fun dueText(due: Int): String = "$due due"

    /** The game day key now (a day starts at the player's day-start hour); the phone's date before the game loads. */
    fun today(): String = GameRepo.game?.dayKey() ?: java.time.LocalDate.now().toString()

    fun hiddenDay(ctx: Context): String? = ctx.getSharedPreferences(PREFS, 0).getString(KEY_HIDDEN, null)
    fun hideToday(ctx: Context) { ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY_HIDDEN, today()).apply() }

    /** Same as the Study tab's CONTINUE card: the saved last deck, if it is in [decks] and the rule says show it. */
    fun pick(ctx: Context, decks: List<Anki.Deck>): Anki.Deck? {
        val lastId = ctx.getSharedPreferences(PREFS, 0).getLong("lastDeck", 0)
        val deck = decks.firstOrNull { it.id == lastId } ?: return null
        return deck.takeIf { show(lastId, it.total, hiddenDay(ctx), today()) }
    }

    /**
     * The Pokémon tab battle card's Continue button (R9-B9b): the saved last deck if it is in [decks] and has cards due. Unlike
     * [pick] the bar's "hidden today" × does not apply (it hides the bar, not this button). Pure.
     */
    fun forCard(lastDeck: Long, decks: List<Anki.Deck>): Anki.Deck? =
        decks.firstOrNull { it.id == lastDeck && lastDeck != 0L }?.takeIf { it.total > 0 }

    /** [forCard] with the saved last deck read from the prefs. */
    fun forCard(ctx: Context, decks: List<Anki.Deck>): Anki.Deck? = forCard(ctx.getSharedPreferences(PREFS, 0).getLong("lastDeck", 0), decks)

    /** "Continue <deck>, N due" for the card's Continue button. */
    fun description(deck: Anki.Deck): String = "Continue ${deck.name.substringAfterLast("::")}, ${deck.total} due"

    /**
     * The small green Continue button (accent square with a play icon; 32 dp look in a 48 dp touch area), shared by the Pokémon tab's
     * battle card and the full-screen Battle view's top bar (PLAN 204) so they look the same. Hidden until [bindButton] gets a deck.
     * Its layout params are for a horizontal LinearLayout.
     */
    fun button(ctx: Context): View = FrameLayout(ctx).apply {
        visibility = View.GONE
        addView(StudyUi.icon(ctx, R.drawable.ic_play, R.color.ag_base).apply {
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 10, ctx)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(Ui.dp(ctx, 32), Ui.dp(ctx, 32), Gravity.CENTER))
    }.also { it.layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)) }

    /** Shows [b] for [deck] (a tap calls [onTap]) or hides it (null). */
    fun bindButton(b: View, deck: Anki.Deck?, onTap: (Anki.Deck) -> Unit) {
        if (deck == null) { b.visibility = View.GONE; b.setOnClickListener(null); b.contentDescription = null; return }
        b.contentDescription = description(deck)
        b.setOnClickListener { onTap(deck) }
        b.visibility = View.VISIBLE
    }

    /** The top bar's button shows only for a qualifying deck, while the top bar is on screen (tall window) and never in PiP. Pure. */
    fun topButtonShown(deck: Anki.Deck?, topBarShown: Boolean, inPip: Boolean): Boolean = deck != null && topBarShown && !inPip

    /** A delayed AnkiDroid start still runs only for a request that is wanted and an Activity that is not leaving. Pure. */
    fun mayStart(finishing: Boolean, destroyed: Boolean, wanted: Boolean): Boolean = !finishing && !destroyed && wanted

    /**
     * Starts studying [deck] like the Study tab's CONTINUE card: with "Study in AnkiDroid" on, AnkiDroid selects it and opens its
     * reviewer (selecting runs off the main thread); else AnkiGo's own study screen (immediate). [stillWanted] says whether the
     * request is current (the card's live flag): the delayed AnkiDroid start and its toasts are dropped when it is not, or when
     * the Activity is finishing / destroyed.
     */
    fun study(a: Activity, deck: Anki.Deck, stillWanted: () -> Boolean = { true }) {
        if (a.getSharedPreferences(PREFS, 0).getBoolean("inAnkiDroid", false)) {
            Thread {
                val ok = runCatching { Anki.selectDeck(a, deck.id) }.isSuccess
                a.runOnUiThread {
                    if (!mayStart(a.isFinishing, a.isDestroyed, stillWanted())) return@runOnUiThread
                    if (ok) runCatching { a.startActivity(Anki.reviewIntent(deck.id)) }.onFailure { Ui.toast(a, "Open AnkiDroid once first") }
                    else Ui.toast(a, "Couldn't select this deck")
                }
            }.start()
            return
        }
        a.startActivity(Intent(a, StudyActivity::class.java).putExtra("deckId", deck.id).putExtra("deckName", deck.name))
    }

    /** The bar's view: card with a rounded-square play button, "Continue · Deck" + "42 due", and a × at the end. */
    fun build(ctx: Context, deck: Anki.Deck, onOpen: () -> Unit, onHide: () -> Unit): View {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = StudyUi.ripple(ctx, Ui.rounded(Ui.c(ctx, R.color.ag_card), 16, ctx))
            setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 2), Ui.dp(ctx, 6))
            contentDescription = "${title(deck.name)}, ${dueText(deck.total)}"
            setOnClickListener { onOpen() }
        }
        bar.addView(StudyUi.icon(ctx, R.drawable.ic_play, R.color.ag_base).apply {
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 10, ctx)
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 32), Ui.dp(ctx, 32)).also { it.marginEnd = Ui.dp(ctx, 10) })
        val text = StudyUi.column(ctx)
        text.addView(StudyUi.ellipsis(StudyUi.label(ctx, title(deck.name), 14, R.color.ag_text, 900)))
        text.addView(StudyUi.label(ctx, dueText(deck.total), 12, R.color.ag_sub0))
        bar.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(StudyUi.icon(ctx, R.drawable.ic_close, R.color.ag_sub1).apply {
            setPadding(Ui.dp(ctx, 13), Ui.dp(ctx, 13), Ui.dp(ctx, 13), Ui.dp(ctx, 13))
            background = StudyUi.ripple(ctx, Ui.rounded(Color.TRANSPARENT, 16, ctx))
            contentDescription = "Hide until tomorrow"
            setOnClickListener { onHide() }
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 44)))
        return FrameLayout(ctx).apply {
            setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), Ui.dp(ctx, 6))
            addView(bar, FrameLayout.LayoutParams(-1, -2))
        }
    }
}
