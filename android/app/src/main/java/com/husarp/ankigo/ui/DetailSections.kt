package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R
import com.husarp.ankigo.data.DexText
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.EvoLine
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.MoveText
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.TypeMatchups
import com.husarp.ankigo.game.data.DexEntry

// PLAN item 60 (pick 4B): the building blocks of the details v2: sections that open on tap (Stats, Moves, Matchups, Training,
// Info) shared by MonDetail (your Pokémon, also the battle's window) and DexDetail (a species). The texts are DetailOpen.kt.

object DetailSections {
    /** Loads the Pokédex text of a species (main-thread callback; null = not downloaded). The tests swap it. */
    internal var dexLoader: (Context, Int, (DexEntry?) -> Unit) -> Unit = { c, n, done -> DexText.load(c, n, done) }

    private class PrefsStore(private val ctx: Context) : DetailOpenStore {
        override fun get(key: String): Boolean? = try { AppPrefs.detailOpen(ctx, key) } catch (e: RuntimeException) { null }
        override fun set(key: String, open: Boolean) { try { AppPrefs.setDetailOpen(ctx, key, open) } catch (e: RuntimeException) { /* not kept */ } }
    }

    /** The tests swap the store (a map) to render all sections open or all closed. */
    internal var storeOf: (Context) -> DetailOpenStore = { PrefsStore(it) }

    fun memory(ctx: Context): DetailOpen = DetailOpen(storeOf(ctx))

    // ---------- the section bar ----------

    /**
     * One section: a card whose bar (title, the small [summary] while closed, chevron) opens and closes it; [body] is built the first
     * time it opens, [onFirstOpen] runs then (the Pokédex text download). The open answer is kept per section ([DetailOpen]).
     */
    fun section(a: Context, mem: DetailOpen, s: DetailSection, summary: CharSequence?, title: String = s.title, onFirstOpen: (() -> Unit)? = null, body: () -> View): View {
        val card = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.c(a, R.color.ag_mantle), 14, a)
        }
        val sum = Ui.text(a, 11f, 800, R.color.ag_sub0).apply {
            text = summary; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.END
        }
        val chevron = ImageView(a).apply {
            setImageResource(R.drawable.ic_chevron)
            setColorFilter(Ui.c(a, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
        }
        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(a, 48)
            setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 8), 0)
            addView(Ui.text(a, 14f, 900, R.color.ag_text).apply { text = title }, LinearLayout.LayoutParams(-2, -2))
            addView(sum, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(a, 10) })
            addView(chevron, LinearLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)).also { it.marginStart = Ui.dp(a, 4) })
        }
        val holder = FrameLayout(a)
        card.addView(bar, LinearLayout.LayoutParams(-1, -2))
        card.addView(holder, LinearLayout.LayoutParams(-1, -2))
        var built = false
        fun apply(open: Boolean) {
            if (open && !built) {
                built = true
                holder.addView(body().also { it.setPadding(Ui.dp(a, 14), Ui.dp(a, 0), Ui.dp(a, 14), Ui.dp(a, 12)) })
                onFirstOpen?.invoke()
            }
            holder.visibility = if (open) View.VISIBLE else View.GONE
            sum.visibility = if (open || summary.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
            chevron.rotation = if (open) 90f else 0f
            bar.contentDescription = "$title, ${if (open) "open" else "closed"}"
        }
        apply(mem.isOpen(s))
        bar.setOnClickListener { apply(mem.toggle(s)) }
        card.layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 8) }
        return card
    }

    // ---------- the marks of the header ----------

    /**
     * The tags under the types (R10-W2-D, D7-A): per tag the same icon the Box / Pokédex tiles use for it in its mark colour, then the
     * name in grey, sentence case, no background. A held item shows the item's own icon and name. Wraps to more lines when long;
     * TalkBack reads them as one list ("Main, Team, Exp. Share …").
     */
    fun marks(a: Context, tags: List<MonTag>): View {
        val flow = FlowRow(a, Ui.dp(a, 6), lineGapPx = Ui.dp(a, 2))
        tags.forEach { t ->
            val held = t.kind == TagKind.HELD && t.item != null
            val img = ImageView(a)
            if (held) Ui.itemIcon(a, t.item!!, img) else img.apply {
                setImageResource(markIcon(t.kind))
                setColorFilter(Ui.c(a, markTone(t.kind)), PorterDuff.Mode.SRC_IN)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            img.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val name = if (held) Items.name(t.item!!) else t.label
            val label = Ui.text(a, 12f, 800, R.color.ag_sub1).apply { text = name; maxLines = 1 }
            flow.addView(IconLabel(a, img, label, 14, 4).apply {
                setPadding(Ui.dp(a, 4), Ui.dp(a, 2), Ui.dp(a, 4), Ui.dp(a, 2))
                contentDescription = t.label
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        }
        flow.contentDescription = tags.joinToString(", ") { it.label }
        flow.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        return flow
    }

    /** The icon of a tag: the tiles' (swords, team, Exp. Share, heart) where the tiles have one. */
    @DrawableRes internal fun markIcon(kind: TagKind): Int = when (kind) {
        TagKind.MAIN -> R.drawable.ic_swords
        TagKind.TEAM -> BoxViews.TEAM_ICON
        TagKind.EXP_SHARE -> R.drawable.ic_exp_share
        TagKind.FAVOURITE -> R.drawable.ic_heart
        TagKind.FAINTED -> R.drawable.ic_alert
        TagKind.CAN_EVOLVE -> R.drawable.ic_evolve
        TagKind.NEW -> R.drawable.ic_ach_sparkle
        TagKind.SHINY -> R.drawable.ic_star
        TagKind.REGIONAL -> R.drawable.ic_flag
        TagKind.HELD -> R.drawable.ic_star // never drawn: a held item shows its own icon
    }

    /** The mark colour of a tag, the same as on the tiles (Main peach, Team mauve, Exp. Share teal, Favourite pink). */
    @ColorRes internal fun markTone(kind: TagKind): Int = when (kind) {
        TagKind.MAIN -> R.color.ag_peach
        TagKind.TEAM -> R.color.ag_mauve
        TagKind.EXP_SHARE -> R.color.ag_teal
        TagKind.FAVOURITE -> R.color.ag_pink
        TagKind.FAINTED -> R.color.ag_red
        TagKind.CAN_EVOLVE -> R.color.ag_green
        TagKind.NEW -> R.color.ag_sky
        TagKind.HELD -> R.color.ag_s0
        TagKind.SHINY -> R.color.ag_yellow
        TagKind.REGIONAL -> R.color.ag_lav
    }

    // ---------- Stats ----------

    /**
     * The Stats section body: hint, the six bars with [stats] (▲/▼ from [nature]; null = no arrows) and the grey base of [sp] at the
     * end, then the Total row. Your Pokémon's details and the wild Pokémon's window build the same block.
     */
    fun statsBody(a: Context, stats: Stats, level: Int, nature: Nature?, sp: Species, compact: Boolean): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        addView(DetailParts.statHint(a))
        val max = DetailMath.monStatMax(level)
        Stat.entries.forEach { s ->
            addView(DetailParts.statRow(a, s, stats[s], DetailMath.fraction(stats[s], max), if (nature == null) 0 else DetailMath.natureDir(nature, s), sp.base[s], compact))
        }
        addView(DetailParts.totalRow(a, stats.total, sp.base.total))
    }

    // ---------- Moves ----------

    @ColorRes private fun kindTone(c: Category): Int = when (c) { Category.PHYSICAL -> R.color.ag_peach; Category.SPECIAL -> R.color.ag_blue; Category.STATUS -> R.color.ag_sub0 }

    private fun kindIcon(c: Category): Int = when (c) {
        Category.PHYSICAL -> R.drawable.ic_kind_physical; Category.SPECIAL -> R.drawable.ic_kind_special; Category.STATUS -> R.drawable.ic_kind_status
    }

    fun kindName(c: Category): String = when (c) { Category.PHYSICAL -> "Physical"; Category.SPECIAL -> "Special"; Category.STATUS -> "Status" }

    /** The round-cornered tile with a move-kind icon: peach burst = Physical, blue rings = Special, grey half-circle = Status. */
    fun kindTile(a: Context, c: Category, sizeDp: Int): View = FrameLayout(a).apply {
        val tone = Ui.c(a, kindTone(c))
        background = Ui.rounded(Bits.blend(tone, Ui.c(a, R.color.ag_mantle), 0.2f), 9, a)
        addView(ImageView(a).apply {
            setImageResource(kindIcon(c))
            setColorFilter(tone, PorterDuff.Mode.SRC_IN)
        }, FrameLayout.LayoutParams(Ui.dp(a, sizeDp * 5 / 9), Ui.dp(a, sizeDp * 5 / 9), Gravity.CENTER))
        contentDescription = kindName(c)
    }

    /**
     * Each move: kind icon, name, type chip + kind, power and accuracy; the one-line legend of the icons under them. No PP (the battle has none).
     * [unknown] = moves that are not known yet (the wild Pokémon's unused ones): a grey "?" line each, after the known ones.
     * [levels] (R10-B4, the Pokédex entry's learnset) = one small label per id ("Lv 12") at the start of its row.
     */
    fun moves(a: Context, data: GameData, ids: List<String>, compact: Boolean, unknown: Int = 0, levels: List<String>? = null): View {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val tap = MoveTap()
        if (ids.isEmpty() && unknown == 0) col.addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = "No moves" })
        ids.forEachIndexed { i, id ->
            val m = data.move(id)
            if (i > 0) col.addView(View(a).apply { setBackgroundColor(Ui.c(a, R.color.ag_s0)); alpha = 0.6f }, LinearLayout.LayoutParams(-1, 1))
            if (m == null) { col.addView(Ui.text(a, 13f, 800, R.color.ag_text).apply { text = id; setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8)) }); return@forEachIndexed }
            val showPower = MoveText.showsPower(m)
            val lvl = levels?.getOrNull(i)
            fun num(v: String, label: String) = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(Ui.text(a, 13f, 900, R.color.ag_text).apply { text = v })
                addView(Ui.text(a, 9f, 700, R.color.ag_sub0).apply { text = label })
            }
            val row = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(a, 48)
                setPadding(0, Ui.dp(a, if (compact) 6 else 8), 0, Ui.dp(a, if (compact) 6 else 8))
                lvl?.let { addView(levelLabel(a, it), LinearLayout.LayoutParams(-2, -2)) }
                addView(kindTile(a, m.category, 28), LinearLayout.LayoutParams(Ui.dp(a, 28), Ui.dp(a, 28)))
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(a, 14f, 800, R.color.ag_text).apply { text = m.name; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                    addView(LinearLayout(a).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(Ui.typeChip(a, m.type), LinearLayout.LayoutParams(-2, -2))
                        addView(Ui.text(a, 10.5f, 800, R.color.ag_sub0).apply { text = kindName(m.category); maxLines = 1 },
                            LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(a, 6) })
                    }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(a, 3) })
                }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(a, 10) })
                // only the numbers the move has (R9-B6); power alone gets a blank where "acc." would be, so the columns line up
                if (showPower) addView(num(m.power.toString(), "power"), LinearLayout.LayoutParams(Ui.dp(a, 40), -2))
                if (m.accuracy != null) addView(num(m.accuracy.toString(), "acc."), LinearLayout.LayoutParams(Ui.dp(a, 40), -2))
                else if (showPower) addView(View(a), LinearLayout.LayoutParams(Ui.dp(a, 40), 1))
            }
            val desc = Ui.text(a, 12f, 700, R.color.ag_text).apply {
                text = MoveText.describe(m)
                visibility = View.GONE
                background = Ui.rounded(Bits.blend(Ui.c(a, R.color.ag_sub0), Ui.c(a, R.color.ag_mantle), 0.14f), 10, a)
                setPadding(Ui.dp(a, 10), Ui.dp(a, 7), Ui.dp(a, 10), Ui.dp(a, 8))
            }
            val said = listOfNotNull(m.name, m.type.label, kindName(m.category), if (showPower) "power ${m.power}" else null,
                m.accuracy?.let { "accuracy $it" }).joinToString(", ")
            row.contentDescription = "$said. Tap for what it does"
            row.setOnClickListener { tap.toggle(row, desc, said) }
            // a touch that starts on a row belongs to the row's click alone: the scroll's outside-tap must not also act on it
            row.setOnTouchListener { _, ev -> if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) tap.downOnRow(); false }
            col.addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(row, LinearLayout.LayoutParams(-1, -2))
                addView(desc, LinearLayout.LayoutParams(-1, -2).also { it.marginStart = Ui.dp(a, if (lvl != null) 38 + LEVEL_W else 38); it.bottomMargin = Ui.dp(a, 8) })
            })
        }
        repeat(unknown) { k ->
            if (ids.isNotEmpty() || k > 0) col.addView(divider(a), LinearLayout.LayoutParams(-1, 1))
            col.addView(Ui.text(a, 14f, 800, R.color.ag_sub0).apply {
                text = "?"; contentDescription = "Unknown move"
                setPadding(0, Ui.dp(a, if (compact) 8 else 10), 0, Ui.dp(a, if (compact) 8 else 10))
            })
        }
        // legend
        col.addView(FlowRow(a, Ui.dp(a, 10), centered = false).apply {
            Category.entries.forEach { c ->
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(kindTile(a, c, 20), LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)))
                    addView(Ui.text(a, 10.5f, 800, R.color.ag_sub0).apply { text = kindName(c) }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(a, 4) })
                })
            }
        }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 4) })
        return col
    }

    private const val LEVEL_W = 44

    /** The learn level at the start of a species' move row ("Lv 12", "Start"); at least [LEVEL_W] dp wide so the rows line up. */
    private fun levelLabel(a: Context, text: String): TextView = Ui.text(a, 11f, 900, R.color.ag_sub0).apply {
        this.text = text; minWidth = Ui.dp(a, LEVEL_W); maxLines = 1
    }

    /** The ScrollView of a details window: like [Bits.scroll], and it lets an open move text close on a tap anywhere. */
    internal fun scroll(ctx: Context, child: View): android.widget.ScrollView = TapWatchScroll(ctx).apply { addView(child) }

    /**
     * The one move whose "what it does" is open (at most one). Tapping its row again or another row, or any plain tap in the details
     * (a [TapWatchScroll] ancestor calls [down] / [tapped]), closes it. No animation: the text just appears under the row.
     */
    private class MoveTap : TapWatchScroll.Watcher {
        private var row: View? = null
        private var desc: View? = null
        private var said = ""
        private var host: TapWatchScroll? = null
        private var openAtDown: View? = null
        private var onRow = false

        /** Called by a row's touch listener on ACTION_DOWN (after [down] of this gesture). */
        fun downOnRow() { onRow = true }

        fun toggle(r: View, d: View, said: String) {
            val wasThis = row === r
            close()
            if (wasThis) return
            row = r; desc = d; this.said = said
            openAtDown = null; onRow = false   // a new open starts clean: the gesture that opened it is not an outside tap
            // listens for taps elsewhere only while a text is open
            var p = r.parent
            while (p != null && p !is TapWatchScroll) p = p.parent
            host = (p as? TapWatchScroll)?.also { it.watchers += this }
            d.visibility = View.VISIBLE
            r.contentDescription = "$said, open. Tap to close"
            r.announceForAccessibility((d as TextView).text)
        }

        fun close() {
            val r = row ?: return
            desc?.visibility = View.GONE
            r.contentDescription = "$said. Tap for what it does"
            host?.watchers?.remove(this); host = null
            row = null; desc = null
        }

        override fun down() { openAtDown = row; onRow = false }
        override fun tapped() { if (row != null && row === openAtDown && !onRow) close() }
        override fun end() { openAtDown = null; onRow = false }
    }

    // ---------- Matchups ----------

    /**
     * R9-W3-A (D8-V1): the matchups as boxes per multiplier (Very weak against ×4, Weak against ×2, Resists ×½, Strongly resists ×¼,
     * Immune to ×0), then "Strong against" (its own attacks ×2) last; empty groups are left out. A tap on a box says what it means for
     * [name]. The chips carry no ×N any more: the box does.
     */
    fun matchups(a: Context, types: List<com.husarp.ankigo.game.Type>, name: String): View {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        MatchupGroups.details(TypeMatchups.of(types)).forEach { (g, ts) ->
            col.addView(MatchupBoxes.box(a, g, ts, name, compact = false), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 6) })
        }
        return col
    }

    // ---------- Training ----------

    private fun fact(a: Context, label: String, tap: StatInfoText?, value: CharSequence, sub: CharSequence?, compact: Boolean): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, Ui.dp(a, if (compact) 6 else 8), 0, Ui.dp(a, if (compact) 6 else 8))
        val lab = Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = label }
        addView(if (tap != null) DetailParts.tapInfo(lab, tap).also { it.minimumHeight = 0 } else lab, LinearLayout.LayoutParams(Ui.dp(a, 70), -2))
        addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(a, 14f, 900, R.color.ag_text).apply { text = value })
            if (!sub.isNullOrEmpty()) addView(Ui.text(a, 11f, 700, R.color.ag_sub0).apply { text = sub }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(a, 2) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun divider(a: Context): View = View(a).apply { setBackgroundColor(Ui.c(a, R.color.ag_s0)); alpha = 0.6f }

    /** Nature (+ what it does), IVs, EVs, friendship and the held item of one of your Pokémon. */
    fun training(a: Context, mon: Mon, compact: Boolean): View {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        col.addView(fact(a, "Nature", StatInfo.NATURE, mon.nature.label, DetailMath.natureEffect(mon.nature), compact))
        col.addView(divider(a), LinearLayout.LayoutParams(-1, 1))
        col.addView(fact(a, "IVs", StatInfo.IV, DetailFormat.ivTotal(mon.ivs), DetailFormat.perStat(mon.ivs), compact))
        col.addView(divider(a), LinearLayout.LayoutParams(-1, 1))
        val evTotal = mon.evs.total
        col.addView(fact(a, "EVs", StatInfo.EV, "$evTotal / ${Tuning.EV_TOTAL_CAP}",
            if (evTotal == 0) "none yet" else DetailFormat.perStat(mon.evs, long = true, skipZero = true), compact))
        col.addView(divider(a), LinearLayout.LayoutParams(-1, 1))
        col.addView(fact(a, "Friendship", StatInfo.FRIENDSHIP, DetailFormat.friendship(mon.friendship), null, compact))
        col.addView(divider(a), LinearLayout.LayoutParams(-1, 1))
        val held = mon.heldItem
        col.addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(a, if (compact) 6 else 8), 0, Ui.dp(a, if (compact) 6 else 8))
            addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = "Held item" }, LinearLayout.LayoutParams(Ui.dp(a, 70), -2))
            if (held == null) addView(Ui.text(a, 14f, 900, R.color.ag_text).apply { text = "none" })
            else {
                addView(ImageView(a).also { Ui.itemIcon(a, held, it) }, LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22)))
                addView(Ui.text(a, 14f, 900, R.color.ag_text).apply { text = Items.name(held) }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(a, 6) })
            }
        })
        return col
    }

    // ---------- Info ----------

    /**
     * A drawn arrow of a stage change (never a "<" / ">" sign): a line with a head, [lengthDp] long (30 dp; shorter in small windows),
     * pointing right, or down when the stages are stacked ([set]).
     */
    class ArrowView(ctx: Context, private var lengthDp: Int = 30) : View(ctx) {
        private var down = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            strokeWidth = Ui.dp(ctx, 1.8f); color = Ui.c(ctx, R.color.ag_sub0)
        }
        fun set(lengthDp: Int, down: Boolean) {
            if (lengthDp == this.lengthDp && down == this.down) return
            this.lengthDp = lengthDp; this.down = down; requestLayout(); invalidate()
        }
        override fun onMeasure(w: Int, h: Int) {
            val long = Ui.dp(context, lengthDp); val short = Ui.dp(context, 14)
            if (down) setMeasuredDimension(short, long) else setMeasuredDimension(long, short)
        }
        override fun onDraw(c: Canvas) {
            val pad = paint.strokeWidth
            val head = Ui.dp(context, 4.5f)
            if (down) {
                val x = width / 2f
                c.drawLine(x, pad, x, height - pad, paint)
                c.drawLine(x - head, height - pad - head, x, height - pad, paint)
                c.drawLine(x + head, height - pad - head, x, height - pad, paint)
            } else {
                val y = height / 2f
                c.drawLine(pad, y, width - pad, y, paint)
                c.drawLine(width - pad - head, y - head, width - pad, y, paint)
                c.drawLine(width - pad - head, y + head, width - pad, y, paint)
            }
        }
    }

    /** The whole evolution line: every stage with its sprite, real arrows with the method, [current] highlighted, branches stacked. */
    fun evolution(a: Context, data: GameData, sp: Species, seen: Set<Int>, compact: Boolean, onTap: ((Species) -> Unit)?): View {
        val line = EvoLine.of(data, sp)
        if (line.standalone) return Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = "Does not evolve" }
        val tree = EvoTreeView(a, data, line.root, sp, seen, compact, onTap)
        // centred when it fits; when it does not (the 340 dp floating card) the tree first takes a narrower look (shorter arrows with
        // the method wrapped, smaller sprites, stacked downwards), then is drawn smaller: never clipped, never scrolling sideways
        return FitWidth(a).apply { addView(tree) }
    }

    private fun caption(a: Context, text: String): TextView = Ui.text(a, 10f, 900, R.color.ag_sub0).apply { this.text = text.uppercase(); letterSpacing = 0.08f }

    private fun cells(a: Context, items: List<Pair<String, String>>): View? {
        if (items.isEmpty()) return null
        return LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            items.forEach { (v, l) ->
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(Ui.text(a, 14f, 900, R.color.ag_text).apply { text = v; maxLines = 1 })
                    addView(Ui.text(a, 10f, 700, R.color.ag_sub0).apply { text = l })
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
    }

    /**
     * PLAN 183A: the small mauve "Read more on Bulbapedia" link (open-in-browser sign, 48 dp tall to touch) directly under the
     * Pokédex text; a tap opens the species' Bulbapedia page in the browser ([ReadMore]).
     */
    private fun readMore(a: Context, data: GameData, sp: Species): TextView {
        val name = data.species[sp.baseNum]?.name ?: sp.name
        return Ui.text(a, 12f, 800, R.color.ag_mauve).apply {
            text = "Read more on Bulbapedia"
            gravity = Gravity.CENTER_VERTICAL
            minHeight = Ui.dp(a, 48)
            contentDescription = "Read more about $name on Bulbapedia, opens the browser"
            a.getDrawable(R.drawable.ic_open)?.mutate()?.let { d ->
                d.setTint(Ui.c(a, R.color.ag_mauve))
                d.setBounds(0, 0, Ui.dp(a, 12), Ui.dp(a, 12))
                setCompoundDrawablesRelative(null, null, d, null)
                compoundDrawablePadding = Ui.dp(a, 4)
            }
            setOnClickListener { ReadMore.open(a, ReadMore.url(data, sp)) }
        }
    }

    /**
     * The Info section: Pokédex text and category (loaded the first time it opens; never blocks), height / weight, [extra] cells
     * (defeats; short values only), then each of [lines] (label, value) on its own full-width line (the long caught date, the
     * wild Pokémon's tier), the abilities, and the evolution line. [abilities] = (label, value, small line under it) or null.
     */
    private fun info(a: Context, data: GameData, sp: Species, extra: List<Pair<String, String>>, lines: List<Pair<String, String>>,
                     abilities: Triple<String, String, String?>?,
                     speciesLine: String?, seen: Set<Int>, compact: Boolean, onTap: ((Species) -> Unit)?): Pair<View, () -> Unit> {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val head = caption(a, "Pokédex")
        val text = Ui.text(a, 12f, 700, R.color.ag_text).apply { setLineSpacing(0f, 1.1f); text = "Loading the Pokédex text…" }
        col.addView(head)
        col.addView(text, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 4) })
        col.addView(readMore(a, data, sp), LinearLayout.LayoutParams(-2, Ui.dp(a, 48)))
        val facts = cells(a, listOfNotNull(DetailFormat.height(sp.heightM)?.let { it to "Height" }, DetailFormat.weight(sp.weightKg)?.let { it to "Weight" }) + extra)
        if (facts != null) {
            col.addView(divider(a), LinearLayout.LayoutParams(-1, 1).also { it.topMargin = Ui.dp(a, 10); it.bottomMargin = Ui.dp(a, 10) })
            col.addView(facts)
        }
        lines.forEachIndexed { i, (label, value) ->
            col.addView(divider(a), LinearLayout.LayoutParams(-1, 1).also { it.topMargin = Ui.dp(a, if (i == 0) 10 else 0) })
            col.addView(fact(a, label, null, value, null, compact))
        }
        if (speciesLine != null) {
            col.addView(divider(a), LinearLayout.LayoutParams(-1, 1).also { it.topMargin = Ui.dp(a, 10); it.bottomMargin = Ui.dp(a, 4) })
            col.addView(fact(a, "Species", null, speciesLine, null, compact))
        }
        if (abilities != null) {
            col.addView(divider(a), LinearLayout.LayoutParams(-1, 1).also { it.topMargin = Ui.dp(a, 6) })
            col.addView(fact(a, abilities.first, null, abilities.second, abilities.third, compact))
        }
        col.addView(divider(a), LinearLayout.LayoutParams(-1, 1).also { it.topMargin = Ui.dp(a, 4); it.bottomMargin = Ui.dp(a, 10) })
        // R10-B18: the generation at the bottom-right, opposite the evolution text (standalone) or under the tree
        val gen = Ui.text(a, 11f, 700, R.color.ag_sub0).apply { this.text = DetailFormat.generation(sp.gen); gravity = Gravity.END }
        if (EvoLine.of(data, sp).standalone) {
            col.addView(EvoGenRow(a, evolution(a, data, sp, seen, compact, onTap), gen), LinearLayout.LayoutParams(-1, -2))
        } else {
            col.addView(evolution(a, data, sp, seen, compact, onTap))
            col.addView(gen, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 6) })
        }
        val load = {
            dexLoader(a, sp.baseNum) { e ->
                if (e?.text != null) text.text = e.text else text.text = "Not downloaded yet — needs internet"
                e?.genus?.let { head.text = "POKÉDEX · ${it.uppercase()}" }
            }
        }
        return col to load
    }

    /** The Info section as a whole: the Pokédex text is asked for the first time it opens. */
    fun infoSection(a: Context, mem: DetailOpen, data: GameData, sp: Species, extra: List<Pair<String, String>>, abilities: Triple<String, String, String?>?,
                    speciesLine: String?, seen: Set<Int>, compact: Boolean, lines: List<Pair<String, String>> = emptyList(), onTap: ((Species) -> Unit)?): View {
        var load: () -> Unit = {}
        return section(a, mem, DetailSection.INFO, "Pokédex, evolution", onFirstOpen = { load() }) {
            val (v, l) = info(a, data, sp, extra, lines, abilities, speciesLine, seen, compact, onTap)
            load = l
            v
        }
    }
}

/**
 * The details window's ScrollView (MonDetail, BattleDetails): tells its [watchers] about every touch that goes down and every
 * plain tap (no scrolling) on it, without taking the touch, so the open move text can close on a tap anywhere in the details.
 */
internal class TapWatchScroll(ctx: Context) : android.widget.ScrollView(ctx) {
    interface Watcher { fun down(); fun tapped(); fun end() {} }

    val watchers = ArrayList<Watcher>()
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; moved = false; watchers.toList().forEach { it.down() } }
            android.view.MotionEvent.ACTION_MOVE -> if (Math.abs(ev.x - downX) > slop || Math.abs(ev.y - downY) > slop) moved = true
        }
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == android.view.MotionEvent.ACTION_UP && !moved) watchers.toList().forEach { it.tapped() }
        if (ev.actionMasked == android.view.MotionEvent.ACTION_UP || ev.actionMasked == android.view.MotionEvent.ACTION_CANCEL) watchers.toList().forEach { it.end() }
        return handled
    }
}

/**
 * R10-FIX (M1): the info row of a species that does not evolve: [evo] ("Does not evolve") on the left, [gen] at the right on the same
 * line while both fit side by side; when they do not (a big font on a narrow screen) the generation goes right-aligned on its own line
 * under the evolution text, as for an evolving species.
 */
internal class EvoGenRow(ctx: Context, private val evo: View, private val gen: View) : ViewGroup(ctx) {
    private val gap = Ui.dp(ctx, 8)
    private val under = Ui.dp(ctx, 6)

    /** True when the last measure put the generation under the evolution text. */
    internal var stacked = false
        private set

    init { addView(evo); addView(gen) }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val limit = if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE / 4 else MeasureSpec.getSize(widthSpec)
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        evo.measure(unspecified, unspecified)
        gen.measure(unspecified, unspecified)
        stacked = evo.measuredWidth + gap + gen.measuredWidth > limit
        val height: Int
        if (stacked) {
            evo.measure(MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST), unspecified)
            gen.measure(MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST), unspecified)
            height = evo.measuredHeight + under + gen.measuredHeight
        } else {
            height = maxOf(evo.measuredHeight, gen.measuredHeight)
        }
        val width = if (MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY) limit
            else if (stacked) minOf(limit, maxOf(evo.measuredWidth, gen.measuredWidth)) else evo.measuredWidth + gap + gen.measuredWidth
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        if (stacked) {
            evo.layout(0, 0, evo.measuredWidth, evo.measuredHeight)
            val top = evo.measuredHeight + under
            gen.layout(w - gen.measuredWidth, top, w, top + gen.measuredHeight)
        } else {
            val h = b - t
            evo.layout(0, h - evo.measuredHeight, evo.measuredWidth, h)
            gen.layout(w - gen.measuredWidth, h - gen.measuredHeight, w, h)
        }
    }
}
