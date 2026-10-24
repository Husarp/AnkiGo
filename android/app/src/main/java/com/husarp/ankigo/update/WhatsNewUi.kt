package com.husarp.ankigo.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.Ui

/**
 * R18-K (PLAN 201a, Adam's pick C): after an update was installed, a card at the top in the [UpdateBanner]'s place and style:
 * "AnkiGo X.Y.Z installed", WHAT'S NEW (opens Settings → Updates) and ✕. Either one marks the version seen, which hides the card
 * on every open screen. No card on a first install. It does not slide or time out: it stays until tapped.
 */
class WhatsNewCard(ctx: Context) : LinearLayout(ctx) {
    private val title = Ui.text(ctx, 14f, 900, R.color.ag_text).apply { maxLines = 1 }
    private val listener: () -> Unit = { refresh() }
    /** Switched off from outside (picture-in-picture). */
    var suppressed = false
        set(v) { field = v; refresh() }
    /** The shots set the version by hand ([forced]) instead of asking the prefs. */
    private var forced: String? = null

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
        val card = LinearLayout(ctx).apply {
            orientation = VERTICAL
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 6), Ui.dp(ctx, 4), Ui.dp(ctx, 6))
            elevation = Ui.dp(ctx, 6f)
            background = GradientDrawable().apply {
                setColor(Ui.c(ctx, R.color.ag_card))
                cornerRadius = Ui.dp(ctx, 14f)
                setStroke(Ui.dp(ctx, 1), Ui.c(ctx, R.color.ag_mauve) and 0x00FFFFFF or 0x59000000)
            }
        }
        val row = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        // the icon is bare: no tile behind it
        row.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_down)
            setColorFilter(Ui.c(ctx, R.color.ag_mauve), PorterDuff.Mode.SRC_IN)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24)).also { it.marginEnd = Ui.dp(ctx, 12); it.marginStart = Ui.dp(ctx, 2) })
        val subtitle = Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = "See what changed"; maxLines = 1 }
        row.addView(LinearLayout(ctx).apply {
            orientation = VERTICAL
            addView(title)
            addView(subtitle, LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) })
        }, LayoutParams(0, -2, 1f))
        row.addView(pillButton(ctx, "WHAT'S NEW", true, 13f, 32, 14) { open() }, LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 8) })
        row.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Ui.c(ctx, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
            val pad = Ui.dp(ctx, 14)
            setPadding(pad, pad, pad, pad)
            contentDescription = "Close"
            isClickable = true
            setOnClickListener { WhatsNew.markSeen(context, forced ?: Updates.current(context)) }
        }, LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 48)))
        card.addView(row)
        addView(card, LayoutParams(-1, -2))
        visibility = GONE
    }

    private fun open() {
        val ctx = context
        WhatsNew.markSeen(ctx, forced ?: Updates.current(ctx))
        if (ctx is UpdatesActivity) return
        runCatching {
            ctx.startActivity(Intent(ctx, UpdatesActivity::class.java).also { if (ctx !is Activity) it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    /** Shots: the card for [version], shown without asking the prefs. */
    fun showFor(version: String) { forced = version; refresh() }

    fun refresh() {
        val v = forced
        val show = !suppressed && (v != null || WhatsNew.cardPending(context))
        if (show) title.text = "AnkiGo ${v ?: Updates.current(context)} installed"
        visibility = if (show) VISIBLE else GONE
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); WhatsNew.listen(listener); refresh() }
    override fun onDetachedFromWindow() { WhatsNew.unlisten(listener); super.onDetachedFromWindow() }
}

/**
 * R18-K (PLAN 201b, Adam's pick B): the What's new list on Settings → Updates: the newest version open, older ones folded (tap a
 * version to open it). [versions] = the bundled notes (any order); only versions up to [current] show. Hidden when there are none.
 */
class WhatsNewList(ctx: Context, versions: List<WhatsNew.Version> = WhatsNew.load(ctx), private val current: String = Updates.current(ctx)) : LinearLayout(ctx) {
    private val shown = WhatsNew.upTo(versions, current)

    init {
        orientation = VERTICAL
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 14, ctx)
        setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 6))
        addView(Ui.text(ctx, 12f, 900, R.color.ag_sub0).apply { text = "WHAT'S NEW"; letterSpacing = 0.06f })
        shown.forEachIndexed { i, v -> addView(versionBlock(ctx, v, open = i == 0, first = i == 0)) }
        visibility = if (shown.isEmpty()) GONE else VISIBLE
    }

    private fun versionBlock(ctx: Context, v: WhatsNew.Version, open: Boolean, first: Boolean): View {
        val block = LinearLayout(ctx).apply { orientation = VERTICAL }
        if (!first) block.addView(View(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_s0)) }, LayoutParams(-1, Ui.dp(ctx, 1)))
        val list = LinearLayout(ctx).apply {
            orientation = VERTICAL
            visibility = if (open) VISIBLE else GONE
            v.items.forEach { addView(item(ctx, it), LayoutParams(-1, -2).also { lp -> lp.bottomMargin = Ui.dp(ctx, 3) }) }
            if (v.fixes.isNotEmpty()) addView(fixesBlock(ctx, v.fixes))
        }
        val head = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = Ui.dp(ctx, 48) }
        head.addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = v.version })
        val small = Ui.text(ctx, 12f, 700, R.color.ag_sub0)
        head.addView(small, LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 6) })
        val chev = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_expand)
            setColorFilter(Ui.c(ctx, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        fun sync() {
            val isOpen = list.visibility == VISIBLE
            small.text = if (v.version == current) "installed" else if (isOpen) "" else WhatsNew.changes(v.items.size + v.fixes.size)
            chev.rotation = if (isOpen) 180f else 0f
            head.contentDescription = "${v.version}, ${if (isOpen) "open" else "folded"}"
        }
        head.addView(chev, LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24)))
        head.isClickable = true
        head.setOnClickListener { list.visibility = if (list.visibility == VISIBLE) GONE else VISIBLE; sync() }
        sync()
        block.addView(head)
        block.addView(list, LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 6) })
        return block
    }

    /** Adam 2026-10-24: a version's bug fixes sit folded under "Bugs & fixes (N)"; tap to open. */
    private fun fixesBlock(ctx: Context, fixes: List<WhatsNew.Item>): View {
        val block = LinearLayout(ctx).apply { orientation = VERTICAL }
        val list = LinearLayout(ctx).apply {
            orientation = VERTICAL
            visibility = GONE
            fixes.forEach { addView(item(ctx, it), LayoutParams(-1, -2).also { lp -> lp.bottomMargin = Ui.dp(ctx, 3) }) }
        }
        val head = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = Ui.dp(ctx, 40) }
        head.addView(Ui.text(ctx, 13f, 800, R.color.ag_sub0).apply { text = "Bugs & fixes (${fixes.size})" }, LayoutParams(0, -2, 1f))
        val chev = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_expand)
            setColorFilter(Ui.c(ctx, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        head.addView(chev, LayoutParams(Ui.dp(ctx, 20), Ui.dp(ctx, 20)))
        fun sync() {
            val isOpen = list.visibility == VISIBLE
            chev.rotation = if (isOpen) 180f else 0f
            head.contentDescription = "Bugs and fixes, ${fixes.size}, ${if (isOpen) "open" else "folded"}"
        }
        head.isClickable = true
        head.setOnClickListener { list.visibility = if (list.visibility == VISIBLE) GONE else VISIBLE; sync() }
        sync()
        block.addView(head)
        block.addView(list)
        return block
    }

    private fun item(ctx: Context, it: WhatsNew.Item): View {
        val row = LinearLayout(ctx).apply { orientation = HORIZONTAL }
        row.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub1).apply { text = "•"; gravity = Gravity.CENTER_HORIZONTAL }, LayoutParams(Ui.dp(ctx, 14), -2))
        val col = LinearLayout(ctx).apply { orientation = VERTICAL }
        col.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub1).apply { text = it.text; setLineSpacing(0f, 1.2f) })
        it.path?.let { p ->
            val line = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            line.addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_settings)
                setColorFilter(Ui.c(ctx, R.color.ag_mauve), PorterDuff.Mode.SRC_IN)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(Ui.dp(ctx, 12), Ui.dp(ctx, 12)).also { lp -> lp.marginEnd = Ui.dp(ctx, 4) })
            line.addView(Ui.text(ctx, 12f, 800, R.color.ag_mauve).apply { text = p })
            col.addView(line, LayoutParams(-2, -2).also { lp -> lp.topMargin = Ui.dp(ctx, 1) })
        }
        row.addView(col, LayoutParams(0, -2, 1f).also { lp -> lp.marginStart = Ui.dp(ctx, 4) })
        return row
    }
}
