package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import com.husarp.ankigo.R
import com.husarp.ankigo.game.BallTrade
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.ItemInfo
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Move
import com.husarp.ankigo.game.MoveText

/**
 * PLAN 168 / 181 / 200 / 199 (R14 batch 2): the item card that opens in place, one design for the Shop and the Bag (mockup
 * docs/design/round14-shop.html `.xcard`). A full-width card with a thin mauve edge: header (icon, name, one line, ▴; a tap folds it),
 * a divider, then for a TM the move block, the text blocks from [ItemInfo], for a TM the "Who can learn it" button, the "You own" line
 * and the action row (Buy in the Shop; Throw / Use / Give / Revive / Teach in the Bag), in the Bag a ball also the Trade button.
 * The page decides which card is open ([OpenCard]) and what the buttons do; this object only builds the Views.
 */
internal object ItemCard {
    /** What one card shows and does. [item] is the catalogue id, or null for a TM ([move] is then its Showdown move id). */
    class Spec(
        val item: String?,
        val move: String? = null,
        val buy: Buy? = null,
        val act: Act? = null,
        val trade: Trade? = null,
        val onFold: () -> Unit,
    )

    /** The Shop's Buy button: [price] (a deal shows the old price struck through); [owned] (a TM) shows "✓ Owned" instead; [onBuy] runs after the money check. */
    class Buy(val price: ShopPlan.Price, val owned: Boolean, val onBuy: () -> Unit)

    /** The Bag's action button. [offLine] is the small line of a button that cannot be used now ([enabled] false); a tap on it says the same in a toast. */
    class Act(val label: String, val enabled: Boolean, val offLine: String?, val onTap: () -> Unit)

    /** The Trade part of a ball card in the Bag: the [offer], whether its confirm is showing, and what the buttons do. */
    class Trade(
        val offer: BallTrade.Offer, val confirming: Boolean, val confirmText: String,
        val onAsk: () -> Unit, val onCancel: () -> Unit, val onConfirm: () -> Unit,
    )

    private fun dp(ctx: Context, v: Float): Int = Math.round(Ui.dp(ctx, v))

    // ---- bold marks ----

    /** A part of a text in a heavier weight and the main text colour (a plain bold span would not change the app's variable font). */
    private class Heavy(private val face: Typeface, private val color: Int) : MetricAffectingSpan() {
        override fun updateDrawState(p: TextPaint) { p.typeface = face; p.color = color }
        override fun updateMeasureState(p: TextPaint) { p.typeface = face }
    }

    /** [text] with its `**bold**` parts in weight 900 and the text colour; the stars are never shown. */
    fun styled(ctx: Context, text: String, @ColorRes boldColor: Int = R.color.ag_text): CharSequence {
        val out = SpannableStringBuilder()
        val heavy = Ui.font(ctx, 900)
        val ink = Ui.c(ctx, boldColor)
        for (p in Marks.split(text)) {
            val at = out.length
            out.append(p.text)
            if (p.bold) out.setSpan(Heavy(heavy, ink), at, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    // ---- the card ----

    /** The card of [spec] as a View (to add to a vertical list with its own margins: 2 dp above, 8 dp below). */
    fun build(page: GamePage, spec: Spec): View {
        val ctx: Context = page
        val data = page.data
        val state = page.state
        val isTm = spec.item == null
        val move = spec.move?.let { data.move(it) }
        val name = if (isTm) Items.tmName(data, spec.move.orEmpty()) else Items.name(spec.item.orEmpty())
        val line = if (isTm) null else ItemInfo.line(data, spec.item.orEmpty())

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val card = Ui.c(ctx, R.color.ag_card)
            background = GradientDrawable().apply {
                setColor(card)
                cornerRadius = Ui.dp(ctx, 14f)
                setStroke(dp(ctx, 1.5f), Bits.blend(Ui.c(ctx, R.color.ag_mauve), card, 0.55f))
            }
            setPadding(dp(ctx, 12f), dp(ctx, 8f), dp(ctx, 12f), dp(ctx, 12f))
        }

        // header: icon tile, name, one line, the fold chevron; the whole header folds the card
        val lead: View = if (isTm) tmLead(ctx, move) else Bits.itemTile(ctx, spec.item.orEmpty(), 48)
        val titles = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 16f, 900, R.color.ag_text).apply { text = name })
            if (line != null) addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = line }, Form.lp(ctx, 1))
        }
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(lead)
            addView(titles, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = dp(ctx, 10f) })
            addView(chevron(ctx, "▴"))
            isClickable = true
            contentDescription = ItemCardPlan.openLabel(name, line)
            setOnClickListener { spec.onFold() }
        }, LinearLayout.LayoutParams(-1, -2))

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(ctx, 10f), 0, 0) }
        card.addView(View(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_s0)) }, LinearLayout.LayoutParams(-1, dp(ctx, 1f)).also { it.topMargin = dp(ctx, 8f) })
        card.addView(body, LinearLayout.LayoutParams(-1, -2))

        val below = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp(ctx, 10f) }
        if (move != null) body.addView(moveBlock(ctx, page, move), below)
        val facts = if (isTm) ItemInfo.tmFacts() else ItemInfo.facts(data, spec.item.orEmpty())
        for (f in facts) body.addView(factBlock(ctx, f.title, f.text), LinearLayout.LayoutParams(below))
        if (isTm) body.addView(whoButton(ctx, page, spec.move.orEmpty(), name), Form.lp(ctx, 0))

        val own = if (isTm) ItemInfo.tmOwnLine(spec.move in state.tms) else ItemInfo.ownLine(spec.item.orEmpty(), state.count(spec.item.orEmpty()))
        body.addView(Ui.text(ctx, 13.5f, 700, R.color.ag_sub0).apply { text = styled(ctx, own) }, Form.lp(ctx, 6))

        val trade = spec.trade
        if (trade == null || !trade.confirming) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            spec.buy?.let { row.addView(buyView(ctx, state.cash, it), LinearLayout.LayoutParams(0, -2, 1f)) }
            spec.act?.let { row.addView(actView(ctx, it), LinearLayout.LayoutParams(0, -2, 1f)) }
            if (row.childCount > 0) body.addView(row, Form.lp(ctx, 10))
        }
        if (trade != null) body.addView(if (trade.confirming) confirmView(ctx, trade) else tradeButton(ctx, trade), Form.lp(ctx, if (trade.confirming) 10 else 8))
        return card
    }

    /** The chevron at the right of a header or row: ▾ folded, ▴ open. */
    fun chevron(ctx: Context, glyph: String, sp: Float = 14f, padH: Float = 6f): TextView = Ui.text(ctx, sp, 900, R.color.ag_sub0).apply {
        text = glyph
        setPadding(dp(ctx, padH), dp(ctx, 4f), dp(ctx, padH), dp(ctx, 4f))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** The 48 dp tile of a TM in the card header: the move's type disc on the quiet tile colour. */
    private fun tmLead(ctx: Context, move: Move?): View = FrameLayout(ctx).apply {
        background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 10, ctx)
        addView(Bits.tmTile(ctx, move?.type, 44), FrameLayout.LayoutParams(dp(ctx, 44f), dp(ctx, 44f), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dp(ctx, 48f), dp(ctx, 48f))
    }

    // ---- the text blocks ----

    /** One block: its title (11 sp, caps, letter-spaced, grey) and its text (13.5 sp, bold parts in the text colour). */
    private fun factBlock(ctx: Context, title: String, text: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(Ui.text(ctx, 11f, 900, R.color.ag_sub0).apply { this.text = title.uppercase(); letterSpacing = 0.06f })
        addView(Ui.text(ctx, 13.5f, 700, R.color.ag_sub1).apply { this.text = styled(ctx, text); setLineSpacing(0f, 1.1f) }, Form.lp(ctx, 2))
    }

    // ---- the TM's move block ----

    private fun moveBlock(ctx: Context, page: GamePage, m: Move): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
            setPadding(dp(ctx, 10f), dp(ctx, 8f), dp(ctx, 10f), dp(ctx, 8f))
        }
        val tags = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.typeChip(ctx, m.type))
            addView(categoryTag(ctx, m.category), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(ctx, 6f) })
        }
        box.addView(tags)
        val stats = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        fun stat(label: String, value: String) {
            stats.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(ctx, 10f, 800, R.color.ag_sub0).apply { text = label.uppercase(); letterSpacing = 0.05f })
                addView(Ui.text(ctx, 18f, 900, R.color.ag_text).apply { text = value })
            }, LinearLayout.LayoutParams(-2, -2).also { if (stats.childCount > 0) it.marginStart = dp(ctx, 18f) })
        }
        if (MoveText.showsPower(m)) stat("Power", m.power.toString())
        m.accuracy?.let { stat("Accuracy", it.toString()) }
        if (stats.childCount > 0) box.addView(stats, Form.lp(ctx, 6))
        val effect = ItemInfo.moveEffect(page.data, m.id)
        if (effect.isNotBlank()) box.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub1).apply { text = styled(ctx, effect) }, Form.lp(ctx, 4))
        box.contentDescription = listOfNotNull(
            m.type.label, DetailSections.kindName(m.category),
            if (MoveText.showsPower(m)) "power ${m.power}" else null, m.accuracy?.let { "accuracy $it" }, ItemInfo.plain(effect).takeIf { it.isNotBlank() },
        ).joinToString(", ")
        return box
    }

    /** "SPECIAL" (mauve tint, as picked), "PHYSICAL" (peach), "STATUS" (grey): the move's kind as a small tag. */
    private fun categoryTag(ctx: Context, c: Category): TextView {
        val tone = Ui.c(ctx, when (c) { Category.SPECIAL -> R.color.ag_mauve; Category.PHYSICAL -> R.color.ag_peach; Category.STATUS -> R.color.ag_sub0 })
        val ink = if (c == Category.SPECIAL) tone else Bits.blend(tone, Ui.c(ctx, R.color.ag_text), 0.7f)
        return Ui.text(ctx, 10f, 900, R.color.ag_text).apply {
            text = DetailSections.kindName(c).uppercase()
            setTextColor(ink)
            letterSpacing = 0.03f
            maxLines = 1
            background = Ui.rounded(Bits.blend(tone, Ui.c(ctx, R.color.ag_card), 0.22f), 6, ctx)
            setPadding(dp(ctx, 7f), dp(ctx, 1f), dp(ctx, 7f), dp(ctx, 1f))
        }
    }

    /** "Who can learn it ........ 7 of your Pokémon ›" (a TM only); grey with "None of your Pokémon" when nobody can. */
    private fun whoButton(ctx: Context, page: GamePage, move: String, tmName: String): View {
        val n = ItemInfo.whoCanLearn(page.data, page.state, move).size
        val mauve = Ui.c(ctx, R.color.ag_mauve)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ctx, 44f)
            background = Ui.rounded(Bits.blend(mauve, Ui.c(ctx, R.color.ag_card), 0.16f), 14, ctx)
            setPadding(dp(ctx, 14f), dp(ctx, 6f), dp(ctx, 14f), dp(ctx, 6f))
            addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_team); setColorFilter(mauve); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                LinearLayout.LayoutParams(dp(ctx, 18f), dp(ctx, 18f)).also { it.marginEnd = dp(ctx, 8f) })
            // R14-B2 fix Y4: label and count share the width (weights) and wrap, so a large font scale on a narrow phone never clips them
            addView(Ui.text(ctx, 13.5f, 900, R.color.ag_mauve).apply { text = ItemCardPlan.WHO_TITLE; setTextColor(mauve) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Ui.text(ctx, 11.5f, 800, R.color.ag_sub0).apply { text = ItemCardPlan.whoCount(n); gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = dp(ctx, 8f) })
            if (n > 0) addView(Ui.text(ctx, 13.5f, 900, R.color.ag_mauve).apply { text = "›"; setTextColor(mauve) }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(ctx, 8f) })
            contentDescription = "${ItemCardPlan.WHO_TITLE}, ${ItemCardPlan.whoCount(n)}"
            if (n > 0) {
                isClickable = true
                setOnClickListener { showWho(page, move, tmName) }
            } else {
                alpha = 0.5f
            }
        }
    }

    /** The list behind "Who can learn it": your Pokémon that can learn [move] (name, level, "knows it" for those that already do); read only. */
    fun showWho(page: GamePage, move: String, tmName: String) {
        val mons = ItemInfo.whoCanLearn(page.data, page.state, move)
        val col = LinearLayout(page).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(page, 12f), 0, dp(page, 8f), 0)
            for (m in mons) addView(Bits.monRow(page, page.data, m, if (move in m.moves) ItemCardPlan.KNOWS_IT else ""))
        }
        Pickers.show(AppDialog.Builder(page).setTitle("${ItemCardPlan.WHO_TITLE}: $tmName").setView(Bits.scroll(page, col)).setPositiveButton("Close", null).create())
    }

    // ---- the buttons ----

    /** A button of the action row: [fill] with [ink] text, an optional small [sub] line under the label. */
    private fun button(ctx: Context, fill: Int, ink: Int, label: CharSequence, sub: String? = null): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = dp(ctx, 46f)
        background = Ui.rounded(fill, 13, ctx)
        setPadding(dp(ctx, 8f), dp(ctx, 4f), dp(ctx, 8f), dp(ctx, 4f))
        isClickable = true
        addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = label; setTextColor(ink); gravity = Gravity.CENTER })
        if (sub != null) addView(Ui.text(ctx, 11f, 800, R.color.ag_text).apply { text = sub; setTextColor(ink); gravity = Gravity.CENTER }, Form.lp(ctx, 1))
    }

    /** Buy: "Buy" + (the old price struck through for a deal) + coin + price on the yellow tint; faded, with a toast on tap, when the money is short. "✓ Owned" for a TM you own. */
    private fun buyView(ctx: Context, cash: Int, b: Buy): View {
        if (b.owned) return Ui.text(ctx, 14f, 900, R.color.ag_green).apply { text = "✓ Owned"; gravity = Gravity.CENTER; minimumHeight = dp(ctx, 44f) }
        val fill = Bits.blend(Ui.c(ctx, R.color.ag_yellow), Ui.c(ctx, R.color.ag_mantle), 0.22f)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(ctx, 46f)
            background = Ui.rounded(fill, 13, ctx)
            isClickable = true
            addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = "Buy" })
            b.price.was?.let { was ->
                addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = Bits.fmt(was); paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG },
                    LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(ctx, 6f) })
            }
            addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_money); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                LinearLayout.LayoutParams(dp(ctx, 14f), dp(ctx, 14f)).also { it.marginStart = dp(ctx, 6f); it.marginEnd = dp(ctx, 4f) })
            addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = Bits.fmt(b.price.now) })
            val enough = cash >= b.price.now
            alpha = if (enough) 1f else 0.45f
            contentDescription = (if (b.price.was != null) "Buy for ${Bits.fmt(b.price.now)}, instead of ${Bits.fmt(b.price.was)}" else "Buy for ${Bits.fmt(b.price.now)}") +
                if (enough) "" else ", not enough money"
            setOnClickListener {
                if (!enough) Ui.toast(ctx, ItemCardPlan.poor(b.price.now)) else b.onBuy()
            }
        }
        return row
    }

    /** The Bag's action: mauve with the label; a button that cannot be used now is faded with its reason as a small line (a tap says it as a toast). */
    private fun actView(ctx: Context, a: Act): View {
        val base = Ui.c(ctx, R.color.ag_base)
        return button(ctx, Ui.c(ctx, R.color.ag_mauve), base, a.label, if (a.enabled) null else a.offLine).apply {
            if (!a.enabled) alpha = 0.5f
            contentDescription = a.label + if (!a.enabled && a.offLine != null) ", ${a.offLine}" else ""
            setOnClickListener { if (a.enabled) a.onTap() else a.offLine?.let { Ui.toast(ctx, it) } }
        }
    }

    // ---- trade-up (Bag, balls) ----

    /** "Trade 5 Poké Balls for 1 Great Ball"; faded with "Need 5, you have 3" when there are too few. */
    private fun tradeButton(ctx: Context, t: Trade): View {
        val mauve = Ui.c(ctx, R.color.ag_mauve)
        val can = t.offer.canTrade
        return button(ctx, Bits.blend(mauve, Ui.c(ctx, R.color.ag_card), 0.16f), mauve, ItemCardPlan.tradeLabel(t.offer), if (can) null else ItemCardPlan.tradeShort(t.offer)).apply {
            minimumHeight = dp(ctx, if (can) 44f else 50f)
            if (!can) alpha = 0.5f
            contentDescription = ItemCardPlan.tradeLabel(t.offer) + if (can) "" else ", ${ItemCardPlan.tradeShort(t.offer)}"
            setOnClickListener { if (can) t.onAsk() else Ui.toast(ctx, ItemCardPlan.tradeShort(t.offer)) }
        }
    }

    /** The short confirm inside the card (the 199C look, in words): the balls that go ("5 ×"), "for", the ball that comes ("1 ×"), one line, [Cancel] [Trade 5 for 1]. */
    private fun confirmView(ctx: Context, t: Trade): View {
        val mauve = Ui.c(ctx, R.color.ag_mauve)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 14, ctx)
            setPadding(dp(ctx, 10f), dp(ctx, 10f), dp(ctx, 10f), dp(ctx, 10f))
        }
        fun side(id: String, n: Int): View = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(Bits.itemTile(ctx, id, 56))
            addView(Ui.text(ctx, 13f, 900, R.color.ag_text).apply { text = "$n ×" }, Form.lp(ctx, 2))
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        t.offer.take.forEachIndexed { i, (id, n) -> row.addView(side(id, n), LinearLayout.LayoutParams(-2, -2).also { if (i > 0) it.marginStart = dp(ctx, 8f) }) }
        row.addView(Ui.text(ctx, 15f, 900, R.color.ag_sub0).apply { text = "for" }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(ctx, 12f); it.marginEnd = dp(ctx, 12f) })
        row.addView(side(t.offer.give, 1))
        box.addView(row, Form.lp(ctx, 0))
        box.addView(Ui.text(ctx, 12.5f, 700, R.color.ag_sub1).apply { text = styled(ctx, t.confirmText); gravity = Gravity.CENTER; setLineSpacing(0f, 1.12f) }, Form.lp(ctx, 8))
        val btns = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btns.addView(button(ctx, Bits.blend(mauve, Ui.c(ctx, R.color.ag_card), 0.16f), mauve, "Cancel").apply { setOnClickListener { t.onCancel() } }, LinearLayout.LayoutParams(0, -2, 1f))
        btns.addView(button(ctx, mauve, Ui.c(ctx, R.color.ag_base), ItemCardPlan.tradeGo(t.offer)).apply { setOnClickListener { t.onConfirm() } },
            LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = dp(ctx, 8f) })
        box.addView(btns, Form.lp(ctx, 10))
        return box
    }
}
