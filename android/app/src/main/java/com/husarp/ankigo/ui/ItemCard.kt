package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Typeface
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
 * PLAN 168 / 181 / 200 / 199 (R14 batch 2), PLAN 210 (R15-A): the item card, one design for the Shop and the Bag (mockup docs/design/round14-shop.html
 * `.xcard`). Since R15 it is not drawn in place but inside the item WINDOW ([ItemWindow]), in three parts: the [header] (icon, name, one line, the
 * close button), the [body] that scrolls (for a TM the move block, the text blocks from [ItemInfo], the "Who can learn it" button) and the
 * [footer] that stays in view (the "You own" line, the action row: Buy in the Shop; Throw / Use / Give / Revive / Teach in the Bag, and in the
 * Bag a ball also the Trade button or its confirm). The page decides which item is open ([OpenCard]) and what the buttons do; this object
 * only builds the Views.
 */
internal object ItemCard {
    /** What one card shows and does. [item] is the catalogue id, or null for a TM ([move] is then its Showdown move id). */
    class Spec(
        val item: String?,
        val move: String? = null,
        val buy: Buy? = null,
        val act: Act? = null,
        val trade: Trade? = null,
        val holders: List<com.husarp.ankigo.game.Mon> = emptyList(),
    )

    /** The Shop purchase: [price] checks affordability; amounts stay on the Shop list. Owned TMs cannot be bought twice. */
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

    // ---- the card, in three parts ----

    /** The name the card shows: "TM Thunderbolt" or the item's name. */
    private fun nameOf(page: GamePage, spec: Spec): String =
        if (spec.item == null) Items.tmName(page.data, spec.move.orEmpty()) else Items.name(spec.item)

    /** The header: bare icon, name, the one line (an item only) and the close button ([onClose]; 48 dp to touch, the glyph is smaller). */
    fun header(page: GamePage, spec: Spec, onClose: () -> Unit): View {
        val ctx: Context = page
        val isTm = spec.item == null
        val move = spec.move?.let { page.data.move(it) }
        val line = if (isTm) null else ItemInfo.line(page.data, spec.item.orEmpty(), page.state.caught)
        val lead: View = if (isTm) tmLead(ctx, move) else Bits.itemTile(ctx, spec.item.orEmpty(), 48)
        val titles = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 16f, 900, R.color.ag_text).apply { text = nameOf(page, spec) })
            if (line != null) addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = line }, Form.lp(ctx, 1))
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(lead)
            addView(titles, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = dp(ctx, 10f) })
            if (spec.holders.isNotEmpty()) addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(ctx, 11f, 800, R.color.ag_mauve).apply { text = "Held by"; gravity = Gravity.END })
                val holders = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    for (mon in spec.holders) addView(LinearLayout(ctx).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(TileSprite(ctx).apply { Ui.staticSprite(ctx, mon.species, mon.shiny, this) }, LinearLayout.LayoutParams(dp(ctx, 28f), dp(ctx, 28f)))
                        addView(Ui.text(ctx, 11f, 800, R.color.ag_text).apply { text = mon.nickname?.takeIf { it.isNotBlank() } ?: mon.name }, LinearLayout.LayoutParams(0, -2, 1f))
                    })
                }
                addView(android.widget.ScrollView(ctx).apply { addView(holders) }, LinearLayout.LayoutParams(-1, dp(ctx, minOf(spec.holders.size, 3) * 28f)))
            }, LinearLayout.LayoutParams(0, -2, 0.85f))
            addView(closeButton(ctx, onClose), LinearLayout.LayoutParams(dp(ctx, 48f), dp(ctx, 48f)).also { it.marginStart = dp(ctx, 4f); it.marginEnd = -dp(ctx, 8f) })
        }
    }

    /** The ✕ of the window: a quiet X on a 48 dp touch area. */
    private fun closeButton(ctx: Context, onClose: () -> Unit): View = FrameLayout(ctx).apply {
        isClickable = true
        contentDescription = "Close"
        setOnClickListener { onClose() }
        addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Ui.c(ctx, R.color.ag_sub0), android.graphics.PorterDuff.Mode.SRC_IN)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dp(ctx, 22f), dp(ctx, 22f), Gravity.CENTER))
    }

    /** The body that scrolls: for a TM the move block, then the text blocks, for a TM "Who can learn it". */
    fun body(page: GamePage, spec: Spec): View {
        val ctx: Context = page
        val data = page.data
        val isTm = spec.item == null
        val move = spec.move?.let { data.move(it) }
        val below = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = dp(ctx, 12f) }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            if (move != null) addView(moveBlock(ctx, page, move), below)
            val facts = if (isTm) ItemInfo.tmFacts() else ItemInfo.facts(data, spec.item.orEmpty(), page.state.caught)
            if (!isTm) {
                val pairs = ItemInfo.evoPairs(data, spec.item.orEmpty())
                if (pairs.isNotEmpty()) addView(factBlock(ctx, "Evolves", ""), below)
                val known = page.state.caught
                fun species(num: Int): View = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    val sp = data.sp(num)
                    addView(TileSprite(ctx).apply { Ui.staticSprite(ctx, num, false, this) }, LinearLayout.LayoutParams(dp(ctx, 48f), dp(ctx, 48f)))
                    addView(Ui.text(ctx, 12f, 800, R.color.ag_text).apply { text = sp.name; gravity = Gravity.CENTER })
                    contentDescription = "${sp.name}. Opens details"
                    setOnClickListener { DexDetail.show(page, sp) }
                }
                for (pair in pairs.filter { it.fromNum in known || it.toNum in known }) addView(LinearLayout(ctx).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    if (pair.fromNum in known) addView(species(pair.fromNum), LinearLayout.LayoutParams(0, -2, 1f))
                    if (pair.fromNum in known && pair.toNum in known) addView(Ui.text(ctx, 22f, 900, R.color.ag_mauve).apply { text = "⟶" })
                    if (pair.toNum in known) addView(species(pair.toNum), LinearLayout.LayoutParams(0, -2, 1f))
                }, below)
                val unknown = pairs.flatMap { listOf(it.fromNum, it.toNum) }.distinct().count { it !in known }
                if (unknown > 0) addView(Ui.text(ctx, 13.5f, 700, R.color.ag_sub0).apply { text = "…and $unknown unknown" }, below)
            }
            for (f in facts) addView(factBlock(ctx, f.title, f.text), LinearLayout.LayoutParams(below))
            if (isTm) addView(whoButton(ctx, page, spec.move.orEmpty(), nameOf(page, spec)), Form.lp(ctx, 0))
        }
    }

    /** The footer that stays in view: the "You own" line, the action row, and in the Bag a ball's Trade button (or its confirm). */
    fun footer(page: GamePage, spec: Spec): View {
        val ctx: Context = page
        val state = page.state
        val isTm = spec.item == null
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val own = if (isTm) ItemInfo.tmOwnLine(spec.move in state.tms) else ItemInfo.ownLine(spec.item.orEmpty(), state.count(spec.item.orEmpty()) + spec.holders.size)
            addView(Ui.text(ctx, 13.5f, 700, R.color.ag_sub0).apply { text = styled(ctx, own) }, Form.lp(ctx, 0))

            val trade = spec.trade
            if (trade == null || !trade.confirming) {
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                spec.buy?.let { row.addView(buyView(page, spec, it), LinearLayout.LayoutParams(0, -2, 1f)) }
                spec.act?.let { row.addView(actView(ctx, it), LinearLayout.LayoutParams(0, -2, 1f)) }
                if (row.childCount > 0) addView(row, Form.lp(ctx, 10))
            }
            if (trade != null) addView(if (trade.confirming) confirmView(ctx, trade) else tradeButton(ctx, trade), Form.lp(ctx, if (trade.confirming) 10 else 8))
        }
    }

    /** The bare TM disc in its unchanged 48 dp header slot. */
    private fun tmLead(ctx: Context, move: Move?): View = FrameLayout(ctx).apply {
        addView(Bits.tmTile(ctx, move?.type, 44), FrameLayout.LayoutParams(dp(ctx, 44f), dp(ctx, 44f), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dp(ctx, 48f), dp(ctx, 48f))
    }

    // ---- the text blocks ----

    /** One block: its title (PLAN 236: 12.5 sp, caps, letter-spaced, in the accent colour; [Ui.c] turns ag_mauve into the chosen accent) and its text (13.5 sp, bold parts in the text colour). */
    private fun factBlock(ctx: Context, title: String, text: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(Ui.text(ctx, TITLE_SP, 900, R.color.ag_mauve).apply {
            Ui.decorateHeading(this, title)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, TITLE_SP)
            letterSpacing = 0.06f
            val icon = when (title) { "Evolves" -> R.drawable.ic_evolve; "In a fight" -> R.drawable.ic_swords; "How to use" -> R.drawable.ic_item_bag; else -> R.drawable.ic_info }
            androidx.core.content.ContextCompat.getDrawable(ctx, icon)?.mutate()?.let {
                it.setTint(Ui.c(ctx, R.color.ag_mauve)); it.setBounds(0, 0, dp(ctx, 16f), dp(ctx, 16f))
                setCompoundDrawablesRelative(it, null, null, null)
            }
        })
        if (text.isNotEmpty()) addView(Ui.text(ctx, 13.5f, 700, R.color.ag_sub1).apply { this.text = styled(ctx, text); setLineSpacing(0f, 1.1f) }, Form.lp(ctx, 3))
    }

    /** PLAN 236: the size of a text block's title (it was 11 sp grey). */
    internal const val TITLE_SP = 12.5f

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

    /** Buy becomes Sure? for the second tap. Quantity changes cancel that confirmation; TMs remain single purchases. */
    private fun buyView(page: GamePage, spec: Spec, b: Buy): View {
        val ctx: Context = page
        if (b.owned) return Ui.text(ctx, 14f, 900, R.color.ag_green).apply { text = "✓ Owned"; gravity = Gravity.CENTER; minimumHeight = dp(ctx, 44f) }
        val open = (page as CardHost).open
        val quantity = if (spec.item == null) 1 else open.quantity
        val total = b.price.now.toLong() * quantity
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val go = button(ctx, Bits.blend(Ui.c(ctx, R.color.ag_yellow), Ui.c(ctx, R.color.ag_mantle), 0.22f), Ui.c(ctx, R.color.ag_text), if (open.buying) "Sure?" else "Buy")
        go.alpha = if (page.state.cash >= total) 1f else 0.45f
        go.setOnClickListener {
            if (page.state.cash < total) Ui.toast(ctx, "Not enough money")
            else if (!open.buying) { open.askBuy(); page.requestRender() }
            else b.onBuy()
        }
        go.orientation = LinearLayout.HORIZONTAL
        go.getChildAt(0).layoutParams = LinearLayout.LayoutParams(-2, -2)
        go.addView(ShopViews.price(ctx, b.price.copy(was = null)), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(ctx, 8f) })
        row.addView(go, LinearLayout.LayoutParams(0, -2, 0.85f))
        if (spec.item != null) row.addView(button(ctx, Ui.c(ctx, R.color.ag_mantle), Ui.c(ctx, R.color.ag_text), "×$quantity").apply {
            contentDescription = "Quantity $quantity. Cycle 1, 3, 5"
            setOnClickListener { open.cycleQuantity(); page.requestRender() }
        }, LinearLayout.LayoutParams(0, -2, 0.15f).also { it.marginStart = dp(ctx, 6f) })
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
