package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.husarp.ankigo.R

/**
 * Round 10 W7 / W8 (ticket F): the one reusable preview chooser. A COLLAPSIBLE entry: the [title], the picked option's value in grey under
 * it and a chevron; collapsed at first, a tap opens it in place into a GRID of small live previews (each tile = the option's preview over
 * its name, the picked one ringed in the accent colour; 3 to a row, 2 when the space is narrow or the text big, see [LookChoosers.columns]). Picking a
 * tile applies at once ([onPick] writes the preference, every open battle follows live), updates the value line and leaves the grid open.
 * [above] (optional) is a view in the opened body over the grid: the info box layout choice.
 *
 * Used for "Info box style", "Battle frame" and "HP / XP bars", in Settings → Battle ([boxed] = false: the entry is a row of a card) and in
 * the battle's "View settings" sheet ([boxed] = true: the entry is its own rounded bar). Built by [LookChoosers].
 *
 * TalkBack: the entry is one button "<title>, <value>, collapsed|expanded"; every tile is one button "<name>" / "<name>, selected" (its
 * preview is hidden from TalkBack).
 */
internal class LookChooser<T : Any>(
    ctx: Context,
    val title: String,
    private val options: List<T>,
    initial: T,
    private val nameOf: (T) -> String,
    /** The live preview of one option; its own size is given by [previewHeightDp] (0 = it wraps to its content). */
    private val previewOf: (T) -> View,
    private val previewHeightDp: Int,
    private val valueOf: (T) -> String = nameOf,
    private val above: View? = null,
    private val boxed: Boolean = false,
    /**
     * Which entries are open, kept by the screen that owns them (Settings): this entry's [title] is in it while it is open, and it starts open
     * when it is already in it. A re-render of the page builds new entries, so without this they would all collapse again. Null = not kept.
     */
    private val keepOpen: MutableSet<String>? = null,
    private val onPick: (T) -> Unit,
) : LinearLayout(ctx) {
    /** True while the grid is shown. */
    var expanded = false
        private set

    /** The picked option. */
    var picked: T = initial
        private set

    /** Told after every open / close. */
    var onToggle: ((Boolean) -> Unit)? = null

    private val titleView = Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = title }
    private val valueView = Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private val chevron = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_chevron)
        setColorFilter(Ui.c(ctx, R.color.ag_sub0), PorterDuff.Mode.SRC_IN)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** The entry's tappable bar (title, value, chevron). */
    val header = LinearLayout(ctx)
    private val body = LinearLayout(ctx)
    private val grid = TileGrid(ctx, Ui.dp(ctx, 8))
    private val tiles = ArrayList<LinearLayout>()
    private val previews = ArrayList<View>()

    init {
        orientation = VERTICAL
        tag = TAG
        header.apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(ctx, 52)
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 8))
            isClickable = true
            isFocusable = true
            background = if (boxed) Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
            else RippleDrawable(ColorStateList.valueOf(0x22000000), null, ColorDrawable(0xFFFFFFFF.toInt()))
            addView(LinearLayout(ctx).apply {
                orientation = VERTICAL
                addView(titleView)
                addView(valueView, LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) })
            }, LayoutParams(0, -2, 1f))
            addView(chevron, LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24)).also { it.marginStart = Ui.dp(ctx, 8) })
            setOnClickListener { toggle() }
            asButton(this)
        }
        addView(header, LayoutParams(-1, -2))

        body.orientation = VERTICAL
        val side = Ui.dp(ctx, if (boxed) 2 else 14)
        body.setPadding(side, Ui.dp(ctx, if (boxed) 10 else 4), side, Ui.dp(ctx, if (boxed) 4 else 14))
        if (above != null) body.addView(above, LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 10) })
        for (o in options) {
            val preview = previewOf(o).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
            previews += preview
            val tile = LinearLayout(ctx).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6))
                isClickable = true
                isFocusable = true
                addView(preview, LayoutParams(-1, if (previewHeightDp > 0) Ui.dp(ctx, previewHeightDp) else -2))
                addView(Ui.text(ctx, 11f, 800, R.color.ag_sub1).apply {
                    text = nameOf(o)
                    gravity = Gravity.CENTER
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 5) })
                setOnClickListener { pick(o) }
                asButton(this)
            }
            tiles += tile
            grid.addView(tile)
        }
        body.addView(grid, LayoutParams(-1, -2))
        body.visibility = GONE
        addView(body, LayoutParams(-1, -2))
        expanded = keepOpen?.contains(title) == true
        paintTiles()
        refresh()
        sync()
    }

    /** The preview views in the order of the options (tests, and the info box chooser, which redraws them when its layout choice changes). */
    fun previewViews(): List<View> = previews

    /** The tile of [option] (tests). */
    fun tileFor(option: T): View = tiles[options.indexOf(option)]

    /** How many tiles sit in a row now (valid after a measure; tests). */
    val columnsNow: Int get() = grid.cols

    /** The value line shown under the title. */
    val valueText: String get() = valueView.text.toString()

    fun toggle() = if (expanded) collapse() else expand()

    fun expand() { if (!expanded) { expanded = true; sync(); scrollIntoView() } }

    fun collapse() { if (expanded) { expanded = false; sync() } }

    /** Applies [option] at once: writes the preference, moves the ring and the value line. The grid stays open. */
    fun pick(option: T) {
        picked = option
        onPick(option)
        paintTiles()
        refresh()
    }

    /** Re-reads the value line (the info box chooser calls it after its layout choice changed). */
    fun refresh() {
        val v = valueOf(picked)
        valueView.text = v
        header.contentDescription = "$title, $v, ${if (expanded) "expanded" else "collapsed"}"
    }

    private fun sync() {
        if (expanded) keepOpen?.add(title) else keepOpen?.remove(title)
        body.visibility = if (expanded) VISIBLE else GONE
        chevron.rotation = if (expanded) 90f else 0f
        refresh()
        onToggle?.invoke(expanded)
    }

    /** When it opens inside a scrolling page (Settings, the sheet), the whole entry is brought into view, as far as the page allows. */
    private fun scrollIntoView() {
        post { try { requestRectangleOnScreen(Rect(0, 0, width, height), false) } catch (_: Exception) {} }
    }

    private fun paintTiles() {
        val ctx = context
        options.forEachIndexed { i, o ->
            val on = o == picked
            val t = tiles[i]
            t.background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = Ui.dp(ctx, 10f)
                setStroke(Ui.dp(ctx, if (on) 2 else 1), Ui.c(ctx, if (on) R.color.ag_mauve else R.color.ag_s1))
            }
            t.isSelected = on
            t.contentDescription = if (on) "${nameOf(o)}, selected" else nameOf(o)
        }
    }

    /** TalkBack says "button" for [v] (a plain LinearLayout is announced as nothing in particular). */
    private fun asButton(v: View) {
        ViewCompat.setAccessibilityDelegate(v, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
                info.isSelected = host.isSelected
            }
        })
    }

    /**
     * The tiles in a grid: [columns] to a row by the width it is given, every tile as wide as the others, a row as tall as its tallest
     * tile. Not scrolling (the page / sheet around it does).
     */
    class TileGrid(ctx: Context, private val gapPx: Int) : ViewGroup(ctx) {
        /** The columns used by the last measure. */
        var cols = 3
            private set

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val d = resources.displayMetrics.density
            val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) (300 * d).toInt() else MeasureSpec.getSize(widthMeasureSpec)
            cols = minOf(LookChoosers.columns(w / d, resources.configuration.fontScale), maxOf(1, childCount))
            val tileW = (w - gapPx * (cols - 1)) / cols
            var y = 0
            var i = 0
            while (i < childCount) {
                var rowH = 0
                for (c in i until minOf(i + cols, childCount)) {
                    val child = getChildAt(c)
                    child.measure(MeasureSpec.makeMeasureSpec(tileW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                    rowH = maxOf(rowH, child.measuredHeight)
                }
                y += rowH + gapPx
                i += cols
            }
            setMeasuredDimension(w, if (childCount == 0) 0 else y - gapPx)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val tileW = (r - l - gapPx * (cols - 1)) / cols
            var y = 0
            var i = 0
            while (i < childCount) {
                var rowH = 0
                for (c in i until minOf(i + cols, childCount)) rowH = maxOf(rowH, getChildAt(c).measuredHeight)
                for (c in i until minOf(i + cols, childCount)) {
                    val x = (c - i) * (tileW + gapPx)
                    getChildAt(c).layout(x, y, x + tileW, y + rowH)   // a short tile still fills its row (the ring is as tall as its neighbours)
                }
                y += rowH + gapPx
                i += cols
            }
        }
    }

    companion object {
        /** The root view's tag, so a page can find its choosers. */
        const val TAG = "lookChooser"

        /** The chooser titled [title] somewhere inside [root], or null. */
        fun find(root: View, title: String): LookChooser<*>? {
            if (root is LookChooser<*> && root.title == title) return root
            if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), title)?.let { return it }
            return null
        }
    }
}

/** The three choosers, one builder each, shared by Settings and the battle's "View settings" sheet. */
internal object LookChoosers {
    const val INFO_BOX = "Info box style"
    const val FRAME = "Battle frame"
    const val BARS = "HP / XP bars"

    /** Three tiles in a row need about this much width (dp); less than that, or large text, gives two. */
    const val THREE_MIN_DP = 300f

    /** From this font scale on, two to a row (the names get two lines of big text). */
    const val TWO_FROM_FONT = 1.6f

    /** Tiles per row for a grid [widthDp] wide at [fontScale]: 3 on a phone, 2 when narrow (320 dp) or with big text (font 2.0). */
    fun columns(widthDp: Float, fontScale: Float): Int = if (widthDp < THREE_MIN_DP || fontScale >= TWO_FROM_FONT) 2 else 3

    /**
     * Which chooser a Settings catalog line lives in (search lands there, and opens it): the info box layout and look lines, the battle
     * frame lines, the bar look lines. Null for any other setting.
     */
    fun titleFor(settingId: String): String? = when {
        settingId.startsWith("infoBox") -> INFO_BOX
        settingId.startsWith("frame") -> FRAME
        settingId.startsWith("hpBars") -> BARS
        else -> null
    }

    /**
     * Equal-width segments in one rounded `ag_mantle` bar, the picked one raised on `ag_card` (the sort sheet's Ascending / Descending
     * look). The labels may take up to three lines, so a big system font does not cut them; the bar grows with its tallest label.
     */
    fun segmented(ctx: Context, labels: List<String>, selected: Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
            setPadding(Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3), Ui.dp(ctx, 3))
        }
        val cells = ArrayList<TextView>()
        fun paint(sel: Int) = cells.forEachIndexed { i, c ->
            c.setTextColor(Ui.c(ctx, if (i == sel) R.color.ag_text else R.color.ag_sub0))
            c.background = if (i == sel) Ui.rounded(Ui.c(ctx, R.color.ag_card), 10, ctx) else null
            c.isSelected = i == sel
        }
        labels.forEachIndexed { i, label ->
            val c = Ui.text(ctx, 13f, 900, R.color.ag_sub0).apply {
                text = label
                gravity = Gravity.CENTER
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 12))   // 42 dp segments in a 48 dp control
                setOnClickListener { paint(i); onPick(i) }
            }
            cells += c
            row.addView(c, LinearLayout.LayoutParams(0, -1, 1f))
        }
        paint(selected.coerceIn(0, labels.size - 1))
        return row
    }

    /** "Number above" / "Number inside": the layout half of the info box value line. */
    fun layoutWord(style: InfoBoxStyle): String = when (style) {
        InfoBoxStyle.ABOVE -> "number above"
        InfoBoxStyle.INSIDE -> "number inside"
    }

    /** The value line of the info box entry, e.g. "Glass · number above". */
    fun infoBoxValue(look: InfoBoxLook, style: InfoBoxStyle): String = "${look.label} · ${layoutWord(style)}"

    /**
     * "Info box style": first the layout choice (Number above / inside the bar), then the grid of the six looks as [BoxLookPreview]s in the
     * chosen layout (changing the layout redraws them).
     */
    fun infoBox(ctx: Context, boxed: Boolean, keepOpen: MutableSet<String>? = null): LookChooser<InfoBoxLook> {
        var style = AppPrefs.infoBoxStyle(ctx)
        val styles = InfoBoxStyle.entries
        lateinit var chooser: LookChooser<InfoBoxLook>
        val layoutRow = segmented(ctx, styles.map { it.label }, styles.indexOf(style)) { i ->
            style = styles[i]
            AppPrefs.setInfoBoxStyle(ctx, style)
            for (p in chooser.previewViews()) (p as BoxLookPreview).let { it.set(it.look, style) }
            chooser.refresh()
        }
        chooser = LookChooser(
            ctx, INFO_BOX, InfoBoxLook.entries, AppPrefs.infoBoxLook(ctx), { it.label },
            { look -> BoxLookPreview(ctx, look, style) }, 64,
            valueOf = { infoBoxValue(it, style) }, above = layoutRow, boxed = boxed, keepOpen = keepOpen,
        ) { AppPrefs.setInfoBoxLook(ctx, it) }
        return chooser
    }

    /** "Battle frame": the seven frames, each around a small plain picture. */
    fun frame(ctx: Context, boxed: Boolean, keepOpen: MutableSet<String>? = null): LookChooser<BattleFrameStyle> =
        LookChooser(ctx, FRAME, BattleFrameStyle.entries, AppPrefs.battleFrame(ctx), { it.label },
            { style ->
                // the plain stand-in picture is the arena's own gradient (as behind the info box previews), the frame sits on top of it
                BattleFramePreview(ctx, style).also {
                    it.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(Ui.c(ctx, R.color.ag_arena_top), Ui.c(ctx, R.color.ag_arena_bot)))
                }
            }, 56, boxed = boxed, keepOpen = keepOpen) { AppPrefs.setBattleFrame(ctx, it) }

    /** "HP / XP bars": Today / Pixel, each as the bars of an info box. */
    fun bars(ctx: Context, boxed: Boolean, keepOpen: MutableSet<String>? = null): LookChooser<HpBarLook> =
        LookChooser(ctx, BARS, HpBarLook.entries, AppPrefs.hpBarLook(ctx), { it.label },
            { BarLookPreview(ctx, it) }, 0, boxed = boxed, keepOpen = keepOpen) { AppPrefs.setHpBarLook(ctx, it) }
}
