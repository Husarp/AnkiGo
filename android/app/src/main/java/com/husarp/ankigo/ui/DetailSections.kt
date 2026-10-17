package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import com.husarp.ankigo.R
import com.husarp.ankigo.data.DexText
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.EvoLine
import com.husarp.ankigo.game.EvoNode
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Mon
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

    /** The state marks under the types (held item, can evolve, fainted, regional form and the other tags), small icon + label. */
    fun marks(a: Context, tags: List<MonTag>): View {
        val flow = FlowRow(a, Ui.dp(a, 4))
        tags.take(8).forEach { t ->
            val held = t.kind == TagKind.HELD && t.item != null
            val ink = if (held) R.color.ag_text else R.color.ag_crust
            val box = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(a, 6), Ui.dp(a, 3), Ui.dp(a, 7), Ui.dp(a, 3))
                background = Ui.rounded(Ui.c(a, if (held) R.color.ag_s0 else markTone(t.kind)), 7, a)
            }
            val img = ImageView(a)
            if (held) Ui.itemIcon(a, t.item!!, img) else {
                val icon = markIcon(t.kind)
                if (icon != 0) img.apply { setImageResource(icon); setColorFilter(Ui.c(a, ink), PorterDuff.Mode.SRC_IN) }
            }
            if (held || markIcon(t.kind) != 0) box.addView(img, LinearLayout.LayoutParams(Ui.dp(a, 12), Ui.dp(a, 12)).also { it.marginEnd = Ui.dp(a, 4) })
            box.addView(Ui.text(a, 10f, 900, ink).apply { text = t.label.uppercase(); letterSpacing = 0.06f; maxLines = 1 })
            flow.addView(box)
        }
        return flow
    }

    private fun markIcon(kind: TagKind): Int = when (kind) {
        TagKind.FAINTED -> R.drawable.ic_alert
        TagKind.CAN_EVOLVE -> R.drawable.ic_up
        TagKind.REGIONAL -> R.drawable.ic_flag
        else -> 0
    }

    @ColorRes private fun markTone(kind: TagKind): Int = when (kind) {
        TagKind.MAIN -> R.color.ag_mauve
        TagKind.FAINTED -> R.color.ag_red
        TagKind.CAN_EVOLVE -> R.color.ag_green
        TagKind.NEW -> R.color.ag_sky
        TagKind.EXP_SHARE -> R.color.ag_teal
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
     */
    fun moves(a: Context, data: GameData, ids: List<String>, compact: Boolean, unknown: Int = 0): View {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        if (ids.isEmpty() && unknown == 0) col.addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = "No moves" })
        ids.forEachIndexed { i, id ->
            val m = data.move(id)
            if (i > 0) col.addView(View(a).apply { setBackgroundColor(Ui.c(a, R.color.ag_s0)); alpha = 0.6f }, LinearLayout.LayoutParams(-1, 1))
            if (m == null) { col.addView(Ui.text(a, 13f, 800, R.color.ag_text).apply { text = id; setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8)) }); return@forEachIndexed }
            val power = if (m.damaging && m.power > 0) m.power.toString() else "—"
            fun num(v: String, label: String) = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(Ui.text(a, 13f, 900, R.color.ag_text).apply { text = v })
                addView(Ui.text(a, 9f, 700, R.color.ag_sub0).apply { text = label })
            }
            col.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(a, if (compact) 6 else 8), 0, Ui.dp(a, if (compact) 6 else 8))
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
                addView(num(power, "power"), LinearLayout.LayoutParams(Ui.dp(a, 40), -2))
                addView(num(m.accuracy?.toString() ?: "—", "acc."), LinearLayout.LayoutParams(Ui.dp(a, 40), -2))
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

    // ---------- Matchups ----------

    /** Strong against / Weak to / Resists / Immune: groups of type chips with the multiplier. */
    fun matchups(a: Context, types: List<com.husarp.ankigo.game.Type>): View {
        val m = TypeMatchups.of(types)
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        fun group(title: String, desc: String, @ColorRes tone: Int, list: List<com.husarp.ankigo.game.Matchup>) {
            if (list.isEmpty()) return
            if (col.childCount > 0) col.addView(View(a).apply { setBackgroundColor(Ui.c(a, R.color.ag_s0)); alpha = 0.6f },
                LinearLayout.LayoutParams(-1, 1).also { it.topMargin = Ui.dp(a, 8); it.bottomMargin = Ui.dp(a, 8) })
            col.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(Ui.text(a, 11f, 900, tone).apply { text = title.uppercase(); letterSpacing = 0.06f })
                addView(Ui.text(a, 11f, 700, R.color.ag_sub0).apply { text = desc }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(a, 8) })
            })
            col.addView(FlowRow(a, Ui.dp(a, 6), centered = false).apply {
                list.forEach { e ->
                    addView(LinearLayout(a).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(Ui.typeChip(a, e.type))
                        addView(Ui.text(a, 11f, 900, R.color.ag_text).apply { text = e.label }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(a, 3) })
                    })
                }
            }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 6) })
        }
        group("Strong against", "its attacks do ×2", R.color.ag_peach, m.strongAgainst)
        group("Weak to", "takes more damage", R.color.ag_red, m.weakTo)
        group("Resists", "takes less", R.color.ag_green, m.resists)
        group("Immune", "takes nothing", R.color.ag_blue, m.immune)
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
        col.addView(fact(a, "Friendship", null, "${mon.friendship} / ${DetailFormat.FRIENDSHIP_MAX}", null, compact))
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

    /** A drawn arrow of a stage change (never a "<" / ">" sign): a line with a head, [lengthDp] long (30 dp; a little shorter in small windows). */
    class ArrowView(ctx: Context, private val lengthDp: Int = 30) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            strokeWidth = Ui.dp(ctx, 1.8f); color = Ui.c(ctx, R.color.ag_sub0)
        }
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(Ui.dp(context, lengthDp), Ui.dp(context, 14))
        override fun onDraw(c: Canvas) {
            val y = height / 2f
            val pad = paint.strokeWidth
            val head = Ui.dp(context, 4.5f)
            c.drawLine(pad, y, width - pad, y, paint)
            c.drawLine(width - pad - head, y - head, width - pad, y, paint)
            c.drawLine(width - pad - head, y + head, width - pad, y, paint)
        }
    }

    private fun evoNode(a: Context, node: EvoNode, current: Species, seen: Set<Int>, spriteDp: Int, onTap: ((Species) -> Unit)?): View {
        val here = node.species.num == current.num
        val img = ImageView(a)
        MonPreview.show(a, node.species.num, false, img, silhouette = node.species.num !in seen && node.species.baseNum !in seen)
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val px = Ui.dp(a, if (spriteDp < 44) 2 else 4)
            setPadding(px, Ui.dp(a, 4), px, Ui.dp(a, 4))
            if (here) background = Ui.rounded(Bits.blend(Ui.c(a, R.color.ag_mauve), Ui.c(a, R.color.ag_mantle), 0.22f), 12, a)
            addView(img, LinearLayout.LayoutParams(Ui.dp(a, spriteDp), Ui.dp(a, spriteDp)))
            addView(Ui.text(a, if (spriteDp < 44) 9.5f else 10.5f, 800, if (here) R.color.ag_text else R.color.ag_sub1).apply {
                text = node.species.name; maxLines = 1; gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-2, -2))
            contentDescription = node.species.name + if (here) " (this one)" else ""
            if (!here && onTap != null && (node.species.num in seen || node.species.baseNum in seen)) setOnClickListener { onTap(node.species) }
        }
    }

    private fun evoTree(a: Context, data: GameData, node: EvoNode, current: Species, seen: Set<Int>, spriteDp: Int, onTap: ((Species) -> Unit)?): View {
        val me = evoNode(a, node, current, seen, spriteDp, onTap)
        if (node.children.isEmpty()) return me
        val kids = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            node.children.forEach { c ->
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(LinearLayout(a).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        addView(ArrowView(a, if (spriteDp < 44) 22 else 30))
                        c.via?.let { v ->
                            addView(Ui.text(a, if (spriteDp < 44) 9f else 9.5f, 800, R.color.ag_sub0).apply {
                                text = EvoLine.method(data, v); gravity = Gravity.CENTER; maxWidth = Ui.dp(a, if (spriteDp < 44) 52 else 66)
                            }, LinearLayout.LayoutParams(-2, -2))
                        }
                        setPadding(Ui.dp(a, if (spriteDp < 44) 0 else 2), 0, Ui.dp(a, if (spriteDp < 44) 0 else 2), 0)
                    })
                    addView(evoTree(a, data, c, current, seen, spriteDp, onTap))
                })
            }
        }
        return LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(me)
            addView(kids)
        }
    }

    /** The whole evolution line: every stage with its sprite, real arrows with the method, [current] highlighted, branches stacked. */
    fun evolution(a: Context, data: GameData, sp: Species, seen: Set<Int>, compact: Boolean, onTap: ((Species) -> Unit)?): View {
        val line = EvoLine.of(data, sp)
        if (line.standalone) return Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = "Does not evolve" }
        val tree = evoTree(a, data, line.root, sp, seen, if (compact) 36 else 48, onTap)
        // centred when it fits, drawn smaller when it does not (the 340 dp floating card): never clipped, never scrolling sideways
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
        col.addView(evolution(a, data, sp, seen, compact, onTap))
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
