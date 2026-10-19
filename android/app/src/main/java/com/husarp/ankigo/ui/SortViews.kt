package com.husarp.ankigo.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Stat
import java.util.IdentityHashMap

// PLAN items 48 + 55, pick 6A (R3-V6): the sort chip with its small list, the one "Sort & filter" sheet, and the texts they show.
// Used by the Box (TeamActivity) and the Pokédex (DexActivity). The maths is MonSort / DexFilter; this file is only views + words.

/** The words of the sort UI. Pure Kotlin (no views), unit-tested. */
object SortText {
    private val STAT_LABELS = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")

    fun statLabel(s: Stat): String = STAT_LABELS[s.ordinal]

    /** Keys that read stats or attacks, so the stat basis (Base / Max level / Current / At level) applies and a tile shows a value. */
    fun usesBasis(k: SortKey): Boolean = when (k) {
        SortKey.TOTAL, SortKey.STAT, SortKey.CHOSEN_TOTAL, SortKey.STRONGEST_ATTACK, SortKey.VS_WILD -> true
        else -> false
    }

    /** The keys of the Box and the Team picker, in the order the list and the sheet show them. */
    val boxKeys: List<SortKey> = listOf(
        SortKey.LEVEL, SortKey.NAME, SortKey.DEX_NUMBER, SortKey.CAUGHT_DATE, SortKey.FIRST_SEEN,
        SortKey.TOTAL, SortKey.STAT, SortKey.CHOSEN_TOTAL, SortKey.STRONGEST_ATTACK, SortKey.VS_WILD,
    )

    /** The keys that make sense for a Pokédex species (no moves; "last caught" works from the owned copies). */
    val dexKeys: List<SortKey> = listOf(
        SortKey.DEX_NUMBER, SortKey.NAME, SortKey.CAUGHT_DATE, SortKey.FIRST_SEEN,
        SortKey.LEVEL, SortKey.TOTAL, SortKey.STAT, SortKey.CHOSEN_TOTAL,
    )

    /** A key's name in the small list and the sheet. */
    fun keyLabel(k: SortKey): String = when (k) {
        SortKey.LEVEL -> "Level"; SortKey.TOTAL -> "Total stats"; SortKey.STAT -> "One stat"; SortKey.CHOSEN_TOTAL -> "Chosen stats"
        SortKey.STRONGEST_ATTACK -> "Strongest attack"; SortKey.VS_WILD -> "Best vs the wild one"; SortKey.NAME -> "Name"
        SortKey.DEX_NUMBER -> "Pokédex no."; SortKey.CAUGHT_DATE -> "Last caught"; SortKey.FIRST_SEEN -> "First seen"
    }

    /** The chosen stats as the chip shows them: "Atk + Def", "4 stats", "All stats"; none ticked = "Chosen stats". */
    private fun chosenName(set: Set<Stat>): String {
        val s = set.sortedBy { it.ordinal }
        return when {
            s.isEmpty() -> "Chosen stats"
            s.size == 6 -> "All stats"
            s.size > 3 -> "${s.size} stats"
            else -> s.joinToString(" + ") { statLabel(it) }
        }
    }

    /** What the basis adds to the chip: "" for Current (the usual), " · Base", " · Lv 100", " · Lv 50". */
    private fun basisSuffix(spec: SortSpec): String = when (spec.basis) {
        StatBasis.CURRENT -> ""
        StatBasis.BASE -> " · Base"
        StatBasis.MAX_LEVEL -> " · Lv 100"
        StatBasis.AT_LEVEL -> " · Lv ${spec.level.coerceIn(1, 100)}"
    }

    /** The sort chip in three parts: the name ("Atk + Def"), the direction arrow, and the basis note (" · Lv 50", often empty). */
    fun chipParts(spec: SortSpec): Triple<String, String, String> {
        val name = when (spec.key) {
            SortKey.LEVEL -> "Level"; SortKey.TOTAL -> "Total"; SortKey.STAT -> statLabel(spec.stat ?: Stat.HP)
            SortKey.CHOSEN_TOTAL -> chosenName(spec.chosenStats); SortKey.STRONGEST_ATTACK -> "Strongest attack"
            SortKey.VS_WILD -> "Best vs wild"; SortKey.NAME -> "Name"; SortKey.DEX_NUMBER -> "No."
            SortKey.CAUGHT_DATE -> "Last caught"; SortKey.FIRST_SEEN -> "First seen"
        }
        return Triple(name, if (spec.descending) "↓" else "↑", if (usesBasis(spec.key)) basisSuffix(spec) else "")
    }

    /** The whole chip text: "Level ↓", "Atk + Def ↓ · Lv 50". */
    fun chipLabel(spec: SortSpec): String = chipParts(spec).let { (n, a, s) -> "$n $a$s" }

    /** The direction in words, for the sheet's direction chip and the list's note. */
    fun directionLabel(key: SortKey, descending: Boolean): String = when (key) {
        SortKey.NAME -> if (descending) "Z → A" else "A → Z"
        SortKey.CAUGHT_DATE, SortKey.FIRST_SEEN -> if (descending) "Newest first" else "Oldest first"
        else -> if (descending) "High → low" else "Low → high"
    }

    /** The two directions of the sort sheet's top switch. */
    fun directionName(descending: Boolean): String = if (descending) "Descending" else "Ascending"

    /** A stat basis in the sheet's switch ("At Lv 50" shows the chosen level). */
    fun basisLabel(b: StatBasis, level: Int): String = when (b) {
        StatBasis.BASE -> "Base"; StatBasis.MAX_LEVEL -> "Max level"; StatBasis.CURRENT -> "Current"
        StatBasis.AT_LEVEL -> "At Lv ${level.coerceIn(1, 100)}"
    }

    /** A sort value on a tile: whole numbers up to 9,999, then 12k / 1.2M (attack scores are big). Null stays null. */
    fun tileValue(v: Double?): String? {
        if (v == null) return null
        val n = Math.round(v)
        return when {
            n < 10_000 -> n.toString()
            n < 100_000 -> "%.1fk".format(java.util.Locale.US, n / 1000.0).replace(".0k", "k")
            n < 1_000_000 -> "${n / 1000}k"
            else -> "%.1fM".format(java.util.Locale.US, n / 1_000_000.0).replace(".0M", "M")
        }
    }

    /**
     * The value each Box tile shows when a stat-based sort is on (key -> text). A group shows the value of the copy that stands for
     * it in the order (its best under [spec]). Empty when the sort has no stat value.
     */
    fun tileValues(
        data: com.husarp.ankigo.game.GameData, state: com.husarp.ankigo.game.GameState, tiles: List<BoxTile>, spec: SortSpec,
    ): IdentityHashMap<BoxTile, String> {
        val out = IdentityHashMap<BoxTile, String>()
        if (!usesBasis(MonSort.effectiveKey(spec, state))) return out
        for (t in tiles) {
            val rep = MonSort.sort(data, state, t.members, spec).firstOrNull() ?: continue
            tileValue(MonSort.value(data, state, rep, spec))?.let { out[t] = it }
        }
        return out
    }
}

/** A left-aligned wrapping row of chips. Lines overlap by [lineGapPx] (negative) so 48 dp touch rows sit close together. */
internal class ChipFlow(ctx: Context, private val gapPx: Int, private val lineGapPx: Int) : ViewGroup(ctx) {
    private val lineStart = ArrayList<Int>()
    private val lineH = ArrayList<Int>()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxW = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
        lineStart.clear(); lineH.clear()
        var x = 0; var h = 0
        var start = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            measureChild(c, MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            if (x > 0 && x + gapPx + c.measuredWidth > maxW) {
                lineStart += start; lineH += h
                start = i; x = 0; h = 0
            }
            x += (if (x > 0) gapPx else 0) + c.measuredWidth
            h = maxOf(h, c.measuredHeight)
        }
        if (x > 0) { lineStart += start; lineH += h }
        val total = lineH.sum() + lineGapPx * maxOf(0, lineH.size - 1) + paddingTop + paddingBottom
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), total)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var y = paddingTop
        for (li in lineStart.indices) {
            var x = paddingLeft
            val end = if (li + 1 < lineStart.size) lineStart[li + 1] else childCount
            for (i in lineStart[li] until end) {
                val c = getChildAt(i)
                if (c.visibility == GONE) continue
                c.layout(x, y + (lineH[li] - c.measuredHeight) / 2, x + c.measuredWidth, y + (lineH[li] + c.measuredHeight) / 2)
                x += c.measuredWidth + gapPx
            }
            y += lineH[li] + lineGapPx
        }
    }
}

internal object SortViews {
    /**
     * The sort chip: a tap anywhere on it ([onOpen]) opens the sort sheet, which holds the direction and the sort options too
     * (R4-X1 item 15: no separate arrow target). Accent fill, so it reads as "this is on" like the mockup.
     */
    fun sortChip(ctx: Context, spec: SortSpec, onOpen: (View) -> Unit): LinearLayout {
        val (name, arrow, suffix) = SortText.chipParts(spec)
        val ink = Ui.c(ctx, R.color.ag_crust)
        fun label(t: String) = Ui.text(ctx, 12f, 800, R.color.ag_crust).apply { text = t; maxLines = 1; setTextColor(ink) }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(ctx, 48)
            background = InsetDrawable(Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 10, ctx), 0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10))
            contentDescription = "Sort: ${SortText.chipLabel(spec)}. Tap to change."
            setOnClickListener { onOpen(it) }
            addView(label(name).apply {
                maxWidth = Ui.dp(ctx, 150)
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(ctx, 10) })
            addView(label(arrow).apply {
                gravity = Gravity.CENTER
                textSize = 16f // a little bigger than the name (R4-X1 item 15)
                setPadding(Ui.dp(ctx, 5), 0, Ui.dp(ctx, 5), 0)
            }, LinearLayout.LayoutParams(-2, -2).also { if (suffix.isEmpty()) it.marginEnd = Ui.dp(ctx, 5) })
            if (suffix.isNotEmpty()) addView(label(suffix.removePrefix(" ")).apply { maxLines = 1 },
                LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = Ui.dp(ctx, 10) })
        }
    }

    /** A plain chip row that scrolls sideways when the chips don't fit (a long sort chip + the filter chips). */
    fun scrollRow(ctx: Context, views: List<View>): View {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        views.forEachIndexed { i, v -> row.addView(v, LinearLayout.LayoutParams(-2, -2).also { if (i > 0) it.marginStart = Ui.dp(ctx, 6) }) }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row, FrameLayout.LayoutParams(-2, -2))
        }
    }

    /** The Pokédex starts on species base stats (a species you don't own has no "current"). */
    val DEX_DEFAULT = SortSpec(basis = StatBasis.BASE)

    /** The remembered Pokédex sort; nothing saved yet = [DEX_DEFAULT]. */
    fun loadDex(ctx: Context): SortSpec {
        val p = ctx.applicationContext.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE)
        return if (p.contains(SortPrefs.Where.DEX.key)) SortPrefs.load(p, SortPrefs.Where.DEX) else DEX_DEFAULT
    }

    /**
     * What choosing [key] in the small list does to [cur]: the same key flips the direction; a new key starts in its natural
     * direction and keeps the stat / basis settings.
     */
    fun choose(cur: SortSpec, key: SortKey): SortSpec = if (key == cur.key) cur.flipped() else cur.copy(key = key, descending = key.naturalDescending)

    /** True when [key] still needs the player to pick a stat (so the sheet opens right after the list). */
    fun needsStats(spec: SortSpec): Boolean = when (spec.key) {
        SortKey.CHOSEN_TOTAL -> spec.chosenStats.isEmpty()
        SortKey.STAT -> spec.stat == null
        else -> false
    }
}

/**
 * The "Sort & filter" sheet (mockup 6A): sort key, direction, the stats to count, the stat basis (with a level stepper for
 * "At Lv N"), the show filters and a "Show N Pokémon" button that closes it. Every change is applied at once through [onChange]
 * (the page behind updates), so the button only closes.
 */
internal class SortSheet(
    private val page: GamePage,
    private val keys: List<SortKey>,
    private var spec: SortSpec,
    private val resetSpec: SortSpec,
    private val vsWildOk: Boolean,
    private val showLabels: List<String>?,
    private var showIndex: Int,
    private val group: GroupToggle?,
    private val count: () -> Int,
    private val onChange: (SortSpec, Int) -> Unit,
    /** R4-X1 item 15: only the sort part (the sort chip's sheet): direction on top, the sort options below; no show filters. */
    private val sortOnly: Boolean = false,
) {
    /** The "Group duplicates" switch row of the Box (hidden while the Box is picking for Compare). */
    class GroupToggle(var on: Boolean, val set: (Boolean) -> Unit)

    private val ctx: Context get() = page
    private var dismiss: () -> Unit = {}
    private lateinit var body: LinearLayout
    private lateinit var go: TextView

    fun build(maxBodyPx: Int): View {
        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.c(ctx, R.color.ag_card))
                val r = Ui.dp(ctx, 22f); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 6), Ui.dp(ctx, 16), Ui.dp(ctx, 12))
        }
        sheet.addView(View(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_ov), 2, ctx) },
            LinearLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 4)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(ctx, 4); it.bottomMargin = Ui.dp(ctx, 6)
            })
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.text(ctx, 17f, 900, R.color.ag_text).apply { text = if (sortOnly) "Sort by" else "Sort & filter"; maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Ui.text(ctx, 13f, 800, R.color.ag_sub1).apply {
            text = "Reset"
            contentDescription = if (sortOnly) "Reset sort" else "Reset sort and filters"
            minHeight = Ui.dp(ctx, 48)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(ctx, 8), 0, Ui.dp(ctx, 4), 0)
            setOnClickListener { spec = resetSpec; showIndex = 0; changed() }
        }, LinearLayout.LayoutParams(-2, -2))
        sheet.addView(head, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = -Ui.dp(ctx, 6) })

        body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        sheet.addView(MaxHeightScroll(ctx, maxBodyPx).apply {
            isVerticalScrollBarEnabled = false
            addView(body, FrameLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, -2))

        go = Ui.text(ctx, 14f, 900, R.color.ag_crust).apply {
            gravity = Gravity.CENTER
            minHeight = Ui.dp(ctx, 48)
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 14, ctx)
            setOnClickListener { dismiss() }
        }
        sheet.addView(go, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 10) })
        fill()
        return sheet
    }

    private fun changed() { onChange(spec, showIndex); fill() }

    private fun section(title: String): View = Ui.text(ctx, 11f, 900, R.color.ag_sub0).apply {
        text = title.uppercase()
        letterSpacing = 0.06f
        setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 10), 0, Ui.dp(ctx, 2))
    }

    private fun flow(): ChipFlow = ChipFlow(ctx, Ui.dp(ctx, 6), -Ui.dp(ctx, 8))

    private fun fill() {
        body.removeAllViews()
        if (sortOnly) {
            body.addView(section("Direction"))
            body.addView(directionSwitch(), LinearLayout.LayoutParams(-1, -2))
            body.addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply {
                text = SortText.directionLabel(spec.key, spec.descending)
                setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 4), 0, 0)
            })
        }
        body.addView(section("Sort by"))
        val keyFlow = flow()
        for (k in keys) {
            val ok = k != SortKey.VS_WILD || vsWildOk
            keyFlow.addView(BoxViews.chip(ctx, SortText.keyLabel(k).replace("Best vs the wild one", "Best vs wild"), k == spec.key) {
                if (!ok) Ui.toast(ctx, "Needs a wild Pokémon in battle")
                else if (!(sortOnly && k == spec.key)) { spec = spec.copy(key = k, descending = k.naturalDescending); changed() } // the sheet keeps a direction the player chose
            }.apply { if (!ok) alpha = 0.4f })
        }
        if (!sortOnly) keyFlow.addView(BoxViews.chip(ctx, (if (spec.descending) "↓ " else "↑ ") + SortText.directionLabel(spec.key, spec.descending), false) {
            spec = spec.flipped(); changed()
        })
        body.addView(keyFlow, LinearLayout.LayoutParams(-1, -2))

        if (spec.key == SortKey.STAT) {
            body.addView(section("Which stat"))
            val f = flow()
            for (s in Stat.entries) f.addView(BoxViews.chip(ctx, SortText.statLabel(s), s == (spec.stat ?: Stat.HP)) { spec = spec.copy(stat = s); changed() })
            body.addView(f, LinearLayout.LayoutParams(-1, -2))
        }
        if (spec.key == SortKey.CHOSEN_TOTAL) {
            body.addView(section("Stats that count"))
            val f = flow()
            for (s in Stat.entries) {
                val on = s in spec.chosenStats
                f.addView(BoxViews.chip(ctx, (if (on) "✓ " else "") + SortText.statLabel(s), on) {
                    spec = spec.copy(chosenStats = if (on) spec.chosenStats - s else spec.chosenStats + s); changed()
                })
            }
            body.addView(f, LinearLayout.LayoutParams(-1, -2))
        }
        if (SortText.usesBasis(spec.key)) {
            body.addView(section("Stat basis"))
            body.addView(basisSwitch(), LinearLayout.LayoutParams(-1, -2))
            if (spec.basis == StatBasis.AT_LEVEL) body.addView(levelStepper(), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 4) })
        }
        if (showLabels != null && !sortOnly) {
            body.addView(section("Show"))
            val f = flow()
            showLabels.forEachIndexed { i, label ->
                f.addView(BoxViews.chip(ctx, label, i == showIndex, heart = label == "Favourites") { showIndex = i; changed() })
            }
            body.addView(f, LinearLayout.LayoutParams(-1, -2))
        }
        if (group != null && !sortOnly) body.addView(groupRow(), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 4) })
        go.text = if (sortOnly) "Done" else "Show ${count()} Pokémon"
    }

    /** Ascending / Descending as one switch (the look of the stat basis switch). */
    private fun directionSwitch(): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
            setPadding(Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3))
        }
        for (desc in listOf(false, true)) {
            val on = desc == spec.descending
            row.addView(Ui.text(ctx, 13f, 900, if (on) R.color.ag_text else R.color.ag_sub0).apply {
                text = (if (desc) "↓ " else "↑ ") + SortText.directionName(desc)
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 12), Ui.dp(ctx, 2), Ui.dp(ctx, 12))
                background = if (on) Ui.rounded(Ui.c(ctx, R.color.ag_card), 10, ctx) else null
                setOnClickListener { if (spec.descending != desc) { spec = spec.copy(descending = desc); changed() } }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return row
    }

    /** Base / Max level / Current / At Lv N as one switch (the look of the Pokédex's All / Owned / Not owned switch). */
    private fun basisSwitch(): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
            setPadding(Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3))
        }
        for (b in StatBasis.entries) {
            val on = b == spec.basis
            row.addView(Ui.text(ctx, 12f, 900, if (on) R.color.ag_text else R.color.ag_sub0).apply {
                text = SortText.basisLabel(b, spec.level)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 12), Ui.dp(ctx, 2), Ui.dp(ctx, 12)) // 42 dp segments in a 48 dp control
                background = if (on) Ui.rounded(Ui.c(ctx, R.color.ag_card), 10, ctx) else null
                setOnClickListener { spec = spec.copy(basis = b); changed() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return row
    }

    /** "Level  −10 −1 [50] +1 +10": the number picker of "At Lv N". */
    private fun levelStepper(): View {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(Ui.text(ctx, 13f, 800, R.color.ag_sub1).apply { text = "Level" }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 2) })
        fun step(label: String, d: Int) = BoxViews.chip(ctx, label, false) { spec = spec.copy(level = (spec.level + d).coerceIn(1, 100)); changed() }
        val gap = Ui.dp(ctx, 4)
        for (v in listOf(step("−10", -10), step("−1", -1))) row.addView(v, LinearLayout.LayoutParams(-2, -2).also { it.marginEnd = gap })
        row.addView(Ui.text(ctx, 16f, 900, R.color.ag_text).apply {
            text = spec.level.toString()
            gravity = Gravity.CENTER
            minWidth = Ui.dp(ctx, 40)
        })
        for (v in listOf(step("+1", 1), step("+10", 10))) row.addView(v, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = gap })
        return row
    }

    private fun groupRow(): View {
        val g = group!!
        val sw = android.widget.Switch(ctx).apply {
            isChecked = g.on
            Ui.tintSwitch(this)
            setOnCheckedChangeListener { _, on -> g.on = on; g.set(on); fill() }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(ctx, 48)
            addView(Ui.text(ctx, 13f, 800, R.color.ag_text).apply { text = "Group duplicates" }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 2) })
            addView(sw, LinearLayout.LayoutParams(-2, -2))
            setOnClickListener { sw.toggle() }
        }
    }

    /** A ScrollView that never grows past [maxPx]: the sheet grows with its content, then scrolls. */
    private class MaxHeightScroll(ctx: Context, private val maxPx: Int) : ScrollView(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cap = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) maxPx else minOf(maxPx, MeasureSpec.getSize(heightMeasureSpec))
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        }
    }

    /** Shows the sheet at the bottom of the screen (an overlay window when the page lives in the floating card). */
    fun show() {
        val dialog = Dialog(ctx).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        dismiss = { dialog.dismiss() }
        val maxBody = (ctx.resources.displayMetrics.heightPixels * BODY_SHARE).toInt() - Ui.dp(ctx, 150) // the handle, title and button stay in view
        dialog.setContentView(SheetKit.dialog(dialog, build(maxBody.coerceAtLeast(Ui.dp(ctx, 120)))))
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.48f); setGravity(Gravity.BOTTOM)
            if (Ui.findActivity(ctx) == null) setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
        dialog.window?.setLayout(minOf(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600)), -2)
    }

    companion object {
        /** The sheet (handle, title, list, button) takes at most this share of the screen height. */
        const val BODY_SHARE = 0.9f
    }
}
