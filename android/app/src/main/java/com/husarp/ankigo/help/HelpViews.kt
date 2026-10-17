package com.husarp.ankigo.help

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.Bits
import com.husarp.ankigo.ui.BarMeaning
import com.husarp.ankigo.ui.BarView
import com.husarp.ankigo.ui.CompareTable
import com.husarp.ankigo.ui.DetailMath
import com.husarp.ankigo.ui.DetailParts
import com.husarp.ankigo.ui.LevelTier
import com.husarp.ankigo.ui.MonTag
import com.husarp.ankigo.ui.MonTags
import com.husarp.ankigo.ui.StatusBadge
import com.husarp.ankigo.ui.StatusInfo
import com.husarp.ankigo.ui.TagKind
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.ui.setMeaning

// R3-V3a: the Views of the Help screen (home, problem page, game guide). All content is in HelpContent / GameGuide; the
// pictures of the guide are the app's own components, with made-up demo values (GuideDemo).

/** Wrapping row, lines start at the left. */
internal class LeftFlow(ctx: Context, private val gapPx: Int) : ViewGroup(ctx) {
    private val lines = ArrayList<IntArray>() // per line: first child, end child, height

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxW = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
        lines.clear()
        var start = 0
        var w = 0
        var h = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            measureChild(c, MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            val add = c.measuredWidth + if (w > 0) gapPx else 0
            if (w > 0 && w + add > maxW) {
                lines += intArrayOf(start, i, h)
                start = i; w = 0; h = 0
            }
            w += c.measuredWidth + if (w > 0) gapPx else 0
            h = maxOf(h, c.measuredHeight)
        }
        if (w > 0) lines += intArrayOf(start, childCount, h)
        val total = lines.sumOf { it[2] } + gapPx * maxOf(0, lines.size - 1) + paddingTop + paddingBottom
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), total)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var y = paddingTop
        for (line in lines) {
            var x = paddingLeft
            for (i in line[0] until line[1]) {
                val c = getChildAt(i)
                if (c.visibility == GONE) continue
                c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
                x += c.measuredWidth + gapPx
            }
            y += line[2] + gapPx
        }
    }
}

internal object HelpViews {
    private fun dp(ctx: Context, v: Int) = Ui.dp(ctx, v)

    fun lp(ctx: Context, top: Int = 0, w: Int = -1) = LinearLayout.LayoutParams(w, -2).also { it.topMargin = dp(ctx, top) }

    /** A help text with its menu and button names in grey boxes ([HelpText]). */
    fun rich(ctx: Context, text: String, sizeSp: Float = 13f, weight: Int = 600, @ColorRes tone: Int = R.color.ag_text): TextView {
        val sb = SpannableStringBuilder()
        val box = Ui.c(ctx, R.color.ag_s0)
        for (p in HelpText.pieces(text)) {
            if (!p.boxed) { sb.append(p.text); continue }
            val st = sb.length
            sb.append(' ').append(p.text).append(' ')
            sb.setSpan(BackgroundColorSpan(box), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return Ui.text(ctx, sizeSp, weight, tone).apply {
            setText(sb)
            setLineSpacing(0f, 1.18f)
        }
    }

    fun card(ctx: Context): LinearLayout = Ui.card(ctx).apply { setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 14)) }

    private fun tintedIcon(ctx: Context, @DrawableRes icon: Int, @ColorRes tone: Int, sizeDp: Int): ImageView = ImageView(ctx).apply {
        setImageResource(icon)
        setColorFilter(Ui.c(ctx, tone))
        layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    // ---------- problem page ----------

    /** Small colour dot + capital title of one part of a problem page. */
    fun heading(ctx: Context, @ColorRes dot: Int, title: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(View(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.c(ctx, dot)) }
        }, LinearLayout.LayoutParams(dp(ctx, 8), dp(ctx, 8)).also { it.marginEnd = dp(ctx, 7) })
        addView(Ui.text(ctx, 11f, 900, R.color.ag_text).apply { text = title.uppercase(); letterSpacing = 0.06f })
    }

    fun sectionTone(kind: SectionKind): Int = when (kind) {
        SectionKind.SEE -> R.color.ag_peach
        SectionKind.WHY -> R.color.ag_sky
        SectionKind.DO -> R.color.ag_mauve
        SectionKind.CHECK -> R.color.ag_green
    }

    fun step(ctx: Context, n: Int, text: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(ctx, 5), 0, dp(ctx, 5))
        addView(Ui.text(ctx, 11f, 900, R.color.ag_crust).apply {
            this.text = n.toString()
            gravity = Gravity.CENTER
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 11, ctx)
        }, LinearLayout.LayoutParams(dp(ctx, 22), dp(ctx, 22)).also { it.marginEnd = dp(ctx, 10) })
        addView(rich(ctx, text), LinearLayout.LayoutParams(0, -2, 1f))
    }

    /** A note under the steps (Ankimon warnings and facts). */
    fun note(ctx: Context, n: GuideNote): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(ctx, 10), dp(ctx, 9), dp(ctx, 10), dp(ctx, 10))
        val warn = n.severity == GuideNote.Severity.WARNING
        val tone = if (warn) R.color.ag_peach else R.color.ag_sky
        background = Ui.rounded(Bits.blend(Ui.c(ctx, tone), Ui.c(ctx, R.color.ag_card), 0.16f), 10, ctx)
        addView(tintedIcon(ctx, if (warn) R.drawable.ic_alert else R.drawable.ic_book, tone, 18),
            LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)).also { it.marginEnd = dp(ctx, 8); it.topMargin = dp(ctx, 1) })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 13f, 800, R.color.ag_text).apply { text = n.title })
            addView(rich(ctx, n.text, 12f, 600, R.color.ag_sub1), lp(ctx, 2))
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    /** A button under "What to do": a filled one for a fix, a quiet one (with the open-in-browser sign for a web link) for a link. */
    fun button(ctx: Context, b: HelpButton, onClick: () -> Unit): TextView =
        Ui.text(ctx, 12f, 900, if (b.link) R.color.ag_text else R.color.ag_crust).apply {
            text = b.label
            Ui.touchPill(this, Ui.rounded(Ui.c(ctx, if (b.link) R.color.ag_s0 else R.color.ag_mauve), 10, ctx), 34, 14)
            if (b.action == HelpAction.OPEN_URL) {
                ctx.getDrawable(R.drawable.ic_open)?.mutate()?.let { d ->
                    d.setTint(Ui.c(ctx, R.color.ag_text))
                    d.setBounds(0, 0, dp(ctx, 15), dp(ctx, 15))
                    setCompoundDrawablesRelative(d, null, null, null)
                    compoundDrawablePadding = dp(ctx, 6)
                }
            }
            setOnClickListener { onClick() }
        }

    fun buttonRow(ctx: Context, buttons: List<View>): View = LeftFlow(ctx, dp(ctx, 8)).apply { buttons.forEach { addView(it) } }

    /** The result next to "Check pictures now": green tick, red cross, or grey (nothing to test). */
    fun resultChip(ctx: Context, check: PictureCheck): TextView {
        val tone = when (check.ok) { true -> R.color.ag_green; false -> R.color.ag_red; null -> R.color.ag_sub1 }
        return Ui.text(ctx, 12f, 900, tone).apply {
            text = (when (check.ok) { true -> "✓ "; false -> "✗ "; null -> "" }) + check.text
            setPadding(dp(ctx, 10), dp(ctx, 7), dp(ctx, 10), dp(ctx, 7))
            background = Ui.rounded(
                if (check.ok == null) Ui.c(ctx, R.color.ag_s0) else Bits.blend(Ui.c(ctx, tone), Ui.c(ctx, R.color.ag_card), 0.2f), 10, ctx,
            )
        }
    }

    // ---------- Ankimon guide (Settings → Ankimon PC sync, first run) ----------

    /** One numbered step with its detail line under the title (small, for Settings and the first run). */
    private fun guideStep(ctx: Context, n: Int, step: GuideStep): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(ctx, 4), 0, dp(ctx, 4))
        addView(Ui.text(ctx, 10f, 900, R.color.ag_crust).apply {
            text = n.toString()
            gravity = Gravity.CENTER
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 10, ctx)
        }, LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)).also { it.marginEnd = dp(ctx, 9); it.topMargin = dp(ctx, 1) })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 13f, 800, R.color.ag_text).apply { text = step.title })
            if (step.detail.isNotBlank()) addView(rich(ctx, step.detail, 12f, 600, R.color.ag_sub1), lp(ctx, 1))
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    /** A warning, compact: icon and bold title on one line; a tap opens its paragraph (and closes it again). */
    private fun guideWarning(ctx: Context, n: GuideNote, open: Boolean = false): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(ctx, 9), dp(ctx, 7), dp(ctx, 9), dp(ctx, 8))
        background = Ui.rounded(Bits.blend(Ui.c(ctx, R.color.ag_peach), Ui.c(ctx, R.color.ag_card), 0.16f), 10, ctx)
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(tintedIcon(ctx, R.drawable.ic_alert, R.color.ag_peach, 16), LinearLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16)).also { it.marginEnd = dp(ctx, 7) })
        head.addView(Ui.text(ctx, 12f, 800, R.color.ag_text).apply { text = n.title }, LinearLayout.LayoutParams(0, -2, 1f))
        val arrow = tintedIcon(ctx, if (open) R.drawable.ic_expand else R.drawable.ic_chevron, R.color.ag_sub0, 18)
        head.addView(arrow, LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)).also { it.marginStart = dp(ctx, 6) })
        addView(head)
        val body = rich(ctx, n.text, 11.5f, 600, R.color.ag_sub1).apply { visibility = if (open) View.VISIBLE else View.GONE }
        addView(body, lp(ctx, 4))
        isClickable = true
        contentDescription = n.title
        setOnClickListener {
            val show = body.visibility != View.VISIBLE
            body.visibility = if (show) View.VISIBLE else View.GONE
            arrow.setImageResource(if (show) R.drawable.ic_expand else R.drawable.ic_chevron)
        }
    }

    /**
     * [AnkimonGuide] as short numbered steps and (when [withWarnings]) its warnings, compact. Shown under the buttons of
     * Settings → Ankimon PC sync and in the first-run Ankimon hint; the same words as the Help page.
     */
    fun ankimonGuide(ctx: Context, withWarnings: Boolean = true, openWarnings: Boolean = false): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(Ui.text(ctx, 11f, 900, R.color.ag_sub0).apply { text = "BRING YOUR PC SAVE OVER"; letterSpacing = 0.058f })
        AnkimonGuide.steps.forEachIndexed { i, st -> addView(guideStep(ctx, i + 1, st), lp(ctx, if (i == 0) 4 else 0)) }
        if (withWarnings) {
            val warnings = AnkimonGuide.notes.filter { it.severity == GuideNote.Severity.WARNING }
            warnings.forEachIndexed { i, n -> addView(guideWarning(ctx, n, open = openWarnings), lp(ctx, if (i == 0) 8 else 6)) }
        }
    }

    // ---------- Set up pictures ----------

    @DrawableRes private fun statusIcon(s: StepStatus): Int = when (s) {
        StepStatus.OK -> R.drawable.ic_check
        StepStatus.WARN -> R.drawable.ic_alert
        StepStatus.FAIL -> R.drawable.ic_close
        StepStatus.UNKNOWN -> R.drawable.ic_info
    }

    @ColorRes private fun statusTone(s: StepStatus): Int = when (s) {
        StepStatus.OK -> R.color.ag_green
        StepStatus.WARN -> R.color.ag_peach
        StepStatus.FAIL -> R.color.ag_red
        StepStatus.UNKNOWN -> R.color.ag_sub0
    }

    /** A round tinted badge with the status sign. */
    private fun statusBadge(ctx: Context, s: StepStatus, sizeDp: Int): View {
        val tone = statusTone(s)
        return android.widget.FrameLayout(ctx).apply {
            background = Ui.rounded(Bits.blend(Ui.c(ctx, tone), Ui.c(ctx, R.color.ag_card), 0.2f), sizeDp / 2, ctx)
            contentDescription = SetupText.statusName(s)
            val icon = sizeDp * 5 / 9
            addView(tintedIcon(ctx, statusIcon(s), tone, icon), android.widget.FrameLayout.LayoutParams(dp(ctx, icon), dp(ctx, icon), Gravity.CENTER))
        }
    }

    /**
     * The top card of Set up pictures: how it came out, and "Check again". [steps] null = the first check is still running.
     * [checking] = a check is running (the old result stays under it until the new one lands).
     */
    fun setupHeader(ctx: Context, steps: List<SetupStep>?, checking: Boolean, failed: Boolean = false, onCheck: () -> Unit): View {
        val card = card(ctx)
        val line = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val overall = if (failed && steps == null && !checking) StepStatus.FAIL else if (steps == null) StepStatus.UNKNOWN else PictureSetup.overall(steps)
        line.addView(statusBadge(ctx, overall, 40), LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 40)).also { it.marginEnd = dp(ctx, 12) })
        line.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 16f, 900, R.color.ag_text).apply {
                text = when {
                    failed && steps == null && !checking -> "Couldn't check the pictures"
                    steps == null || checking -> "Checking…"
                    else -> SetupText.headline(steps)
                }
            })
            addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply {
                text = when {
                    failed && steps == null && !checking -> "Something went wrong while reading AnkiDroid. Tap Check again to try once more."
                    steps == null || checking -> "Looking at AnkiDroid and your next cards"
                    else -> SetupText.sub(steps)
                }
            }, lp(ctx, 1))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val again = button(ctx, HelpButton(HelpAction.HELP_PAGE, "Check again", link = true)) { if (!checking) onCheck() }
        again.alpha = if (checking) 0.45f else 1f
        line.addView(again, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(ctx, 10) })
        card.addView(line)
        return card
    }

    /** One check: status badge, its title and sentence, and one button per fix. */
    fun setupStep(ctx: Context, step: SetupStep, onFix: (HelpButton) -> Unit): View {
        val card = card(ctx)
        val line = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        line.addView(statusBadge(ctx, step.status, 28), LinearLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28)).also { it.marginEnd = dp(ctx, 10) })
        line.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = step.title })
            addView(rich(ctx, step.text, 13f, 600, R.color.ag_sub1), lp(ctx, 3))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(line)
        if (step.fixes.isNotEmpty()) {
            val buttons = step.fixes.map { f -> HelpFixes.fromFix(f).let { b -> button(ctx, b) { onFix(b) } } }
            card.addView(buttonRow(ctx, buttons), lp(ctx, 10))
        }
        return card
    }

    // ---------- Help home ----------

    @DrawableRes fun problemIcon(id: String): Int = when (id) {
        HelpContent.PICTURES_DONT_SHOW -> R.drawable.ic_alert
        HelpContent.MOVE_COLLECTION -> R.drawable.ic_open
        HelpContent.PICTURES_MISSING -> R.drawable.ic_download
        HelpContent.PICTURES_SYNC -> R.drawable.ic_sync
        HelpContent.ANSWERS_NOT_DETECTED -> R.drawable.ic_bug
        HelpContent.WIDGET_NOT_UPDATING -> R.drawable.ic_update
        HelpContent.BATTLE_POPS_UP -> R.drawable.ic_pip
        else -> R.drawable.ic_swap
    }

    @ColorRes fun problemTone(id: String): Int = when (id) {
        HelpContent.PICTURES_DONT_SHOW -> R.color.ag_yellow
        HelpContent.MOVE_COLLECTION -> R.color.ag_blue
        HelpContent.PICTURES_MISSING -> R.color.ag_peach
        HelpContent.PICTURES_SYNC -> R.color.ag_teal
        HelpContent.ANSWERS_NOT_DETECTED -> R.color.ag_red
        HelpContent.WIDGET_NOT_UPDATING -> R.color.ag_sky
        HelpContent.BATTLE_POPS_UP -> R.color.ag_lav
        else -> R.color.ag_yellow
    }

    /** One row of the "Fix a problem" list: coloured icon tile, title, one grey line, chevron. */
    fun problemRow(ctx: Context, page: HelpPage, onClick: () -> Unit): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        minimumHeight = dp(ctx, 56)
        val tone = problemTone(page.id)
        addView(FrameLayoutTile(ctx, Bits.blend(Ui.c(ctx, tone), Ui.c(ctx, R.color.ag_card), 0.22f), problemIcon(page.id), tone),
            LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34)).also { it.marginEnd = dp(ctx, 12) })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = page.title })
            addView(Ui.text(ctx, 12f, 600, R.color.ag_sub0).apply { text = page.summary }, lp(ctx, 1))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(tintedIcon(ctx, R.drawable.ic_chevron, R.color.ag_sub0, 20), LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)).also { it.marginStart = dp(ctx, 6) })
        isClickable = true
        setOnClickListener { onClick() }
    }

    @Suppress("FunctionName")
    private fun FrameLayoutTile(ctx: Context, bg: Int, @DrawableRes icon: Int, @ColorRes tone: Int): View = android.widget.FrameLayout(ctx).apply {
        background = Ui.rounded(bg, 10, ctx)
        addView(tintedIcon(ctx, icon, tone, 20), android.widget.FrameLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20), Gravity.CENTER))
    }

    // ---------- game guide ----------

    @DrawableRes fun chapterIcon(id: String): Int = when (id) {
        GameGuide.BATTLES -> R.drawable.ic_swords
        GameGuide.LEVELS -> R.drawable.ic_up
        GameGuide.TYPES -> R.drawable.ic_flame
        GameGuide.STATS -> R.drawable.ic_stats
        GameGuide.CATCHING -> R.drawable.ic_ball
        GameGuide.MONEY -> R.drawable.ic_coin
        GameGuide.HEALING -> R.drawable.ic_heart
        else -> R.drawable.ic_shop
    }

    /** A contents entry: icon + "N. Short name" (the Guide page, two per row) or icon over name (the Help home tile). */
    fun tocChip(ctx: Context, id: String, number: Int, tile: Boolean, onClick: () -> Unit): View = LinearLayout(ctx).apply {
        orientation = if (tile) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = dp(ctx, if (tile) 64 else 48)
        setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6))
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 12, ctx)
        addView(tintedIcon(ctx, chapterIcon(id), R.color.ag_mauve, if (tile) 20 else 18), LinearLayout.LayoutParams(dp(ctx, if (tile) 20 else 18), dp(ctx, if (tile) 20 else 18)))
        val name = GuideDemo.shortTitle(id)
        addView(Ui.text(ctx, if (tile) 10f else 12f, 800, R.color.ag_text).apply {
            text = if (tile) name else "$number. $name"
            gravity = Gravity.CENTER
            if (tile) maxLines = 2 else maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(if (tile) -1 else -2, -2).also { if (tile) it.topMargin = dp(ctx, 4) else it.marginStart = dp(ctx, 7) })
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** The closed/open header of a chapter inside its card. */
    fun chapterHeader(ctx: Context, ch: GuideChapter, number: Int, open: Boolean, onClick: () -> Unit): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(ctx, 52)
        setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
        addView(Ui.text(ctx, 13f, 900, R.color.ag_sub0).apply { text = number.toString() }, LinearLayout.LayoutParams(dp(ctx, 18), -2))
        addView(tintedIcon(ctx, chapterIcon(ch.id), R.color.ag_mauve, 20), LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)).also { it.marginStart = dp(ctx, 4); it.marginEnd = dp(ctx, 10) })
        addView(Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = ch.title }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(tintedIcon(ctx, if (open) R.drawable.ic_expand else R.drawable.ic_chevron, R.color.ag_mauve, 22), LinearLayout.LayoutParams(dp(ctx, 22), dp(ctx, 22)))
        isClickable = true
        contentDescription = ch.title + if (open) ", open" else ", closed"
        setOnClickListener { onClick() }
    }

    private fun cell(ctx: Context, comp: View, caption: String?): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(comp)
        if (!caption.isNullOrBlank()) addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply {
            text = caption
            gravity = Gravity.CENTER
            maxWidth = dp(ctx, 92)
        }, lp(ctx, 3, -2))
    }

    private fun caption(ctx: Context, text: String): View = Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { this.text = text }

    private fun barRow(ctx: Context, fill: (BarView) -> Unit, text: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val bar = BarView(ctx).also(fill)
        addView(bar, LinearLayout.LayoutParams(dp(ctx, 96), dp(ctx, 8)).also { it.marginEnd = dp(ctx, 12) })
        addView(Ui.text(ctx, 12f, 700, R.color.ag_sub1).apply { this.text = text }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun matchupBlock(ctx: Context, text: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val defender = GuideDemo.matchupDefender
        val (weak, resist, immune) = CompareTable.matchups(listOf(defender))
        addView(Ui.text(ctx, 12f, 700, R.color.ag_sub1).apply {
            this.text = text.ifBlank { "A ${defender.name.lowercase().replaceFirstChar { it.uppercase() }}-type Pokémon takes" }
        })
        fun line(label: String, list: List<com.husarp.ankigo.ui.Matchup>) {
            if (list.isEmpty()) return
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(Ui.text(ctx, 11f, 800, R.color.ag_sub0).apply { this.text = label }, LinearLayout.LayoutParams(dp(ctx, 56), -2).also { it.topMargin = dp(ctx, 2) })
            val flow = LeftFlow(ctx, dp(ctx, 6))
            list.forEach { m ->
                flow.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(Ui.typeChip(ctx, m.type))
                    val tone = if (m.mult > 1.0) R.color.ag_red else if (m.mult == 0.0) R.color.ag_sub0 else R.color.ag_green
                    addView(Ui.text(ctx, 11f, 900, tone).apply { this.text = m.label }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(ctx, 3) })
                })
            }
            row.addView(flow, LinearLayout.LayoutParams(0, -2, 1f))
            addView(row, lp(ctx, 6))
        }
        line("Hit hard", weak)
        line("Resists", resist)
        line("No effect", immune)
    }

    private fun statBars(ctx: Context, text: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        Stat.entries.forEachIndexed { i, s ->
            val v = GuideDemo.stats[i]
            addView(DetailParts.statRow(ctx, s, v, DetailMath.fraction(v, GuideDemo.STAT_BAR_MAX), 0, null, true))
        }
        if (text.isNotBlank()) addView(caption(ctx, text), lp(ctx, 2))
    }

    /** One picture of a chapter: the app's own component with a made-up value. Single small things (items, tags, one type) come back as a cell; [inline] says so. */
    private fun isInline(v: GuideVisual) = v.id == GuideVisualId.ITEM_ICON || v.id == GuideVisualId.TAG || (v.id == GuideVisualId.TYPE_CHIPS && v.arg != null)

    private fun inlineCell(ctx: Context, v: GuideVisual): View = when (v.id) {
        GuideVisualId.ITEM_ICON -> {
            val id = v.arg.orEmpty()
            val def = Items.def(id)
            val cap = v.caption.ifBlank {
                Items.name(id) + if (def?.kind == ItemKind.BALL) "\n${Tuning.BALL_CHANCE[id]} %" else ""
            }
            cell(ctx, Bits.itemTile(ctx, id, 44), cap)
        }
        GuideVisualId.TAG -> {
            val kind = TagKind.entries.firstOrNull { it.name == v.arg } ?: TagKind.MAIN
            val item = if (kind == TagKind.HELD) "lucky-egg" else null
            cell(ctx, MonTags.pill(ctx, MonTag(kind, GuideDemo.tagLabel(kind.name), item)), v.caption)
        }
        else -> cell(ctx, Ui.typeChip(ctx, Type.entries.firstOrNull { it.name == v.arg } ?: Type.NORMAL), v.caption)
    }

    private fun blockVisual(ctx: Context, v: GuideVisual): View = when (v.id) {
        GuideVisualId.HP_BAR -> barRow(ctx, { it.set(GuideDemo.hpFraction, Ui.hpColor(ctx, GuideDemo.hpFraction), false) }, v.caption)
        GuideVisualId.XP_BAR -> barRow(ctx, { it.setMeaning(BarMeaning.XP, GuideDemo.xpFraction, false) }, v.caption)
        GuideVisualId.STAT_BARS -> statBars(ctx, v.caption)
        GuideVisualId.MATCHUP_TABLE -> matchupBlock(ctx, v.caption)
        GuideVisualId.LEVEL_COLOURS -> LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val flow = LeftFlow(ctx, dp(ctx, 8))
            GuideDemo.levels.forEach { lv ->
                flow.addView(cell(ctx, Ui.text(ctx, 13f, 900, R.color.ag_text).also { LevelTier.style(it, lv) }, LevelTier.name(lv)))
            }
            addView(flow)
            if (v.caption.isNotBlank()) addView(caption(ctx, v.caption), lp(ctx, 4))
        }
        GuideVisualId.STATUS_BADGES -> LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val flow = LeftFlow(ctx, dp(ctx, 10))
            GuideDemo.statuses.forEach { st ->
                val badge = StatusBadge(ctx, st).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 26), dp(ctx, 26))
                    setOnClickListener { StatusInfo.of(st)?.let { Ui.toast(ctx, it.line) } }
                }
                flow.addView(cell(ctx, badge, StatusInfo.of(st)?.name))
            }
            addView(flow)
            if (v.caption.isNotBlank()) addView(caption(ctx, v.caption), lp(ctx, 4))
        }
        else -> LinearLayout(ctx).apply { // TYPE_CHIPS without a type: a row of a few
            orientation = LinearLayout.VERTICAL
            val flow = LeftFlow(ctx, dp(ctx, 4))
            GuideDemo.typeRow.forEach { flow.addView(Ui.typeChip(ctx, it)) }
            addView(flow)
            if (v.caption.isNotBlank()) addView(caption(ctx, v.caption), lp(ctx, 4))
        }
    }

    /** What an open chapter shows: its sentence, its pictures, then its points. */
    fun chapterBody(ctx: Context, ch: GuideChapter): List<View> {
        val out = ArrayList<View>()
        out += Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = ch.intro; setLineSpacing(0f, 1.15f) }
        var group: LeftFlow? = null
        fun flush() { group?.let { out += it }; group = null }
        for (v in ch.visuals) {
            if (isInline(v)) {
                val g = group ?: LeftFlow(ctx, dp(ctx, 8)).also { group = it }
                g.addView(inlineCell(ctx, v))
            } else {
                flush()
                out += blockVisual(ctx, v)
            }
        }
        flush()
        for (p in ch.points) out += LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Ui.text(ctx, 13f, 900, R.color.ag_mauve).apply { text = "•" }, LinearLayout.LayoutParams(dp(ctx, 14), -2))
            addView(rich(ctx, p, 13f, 600, R.color.ag_sub1), LinearLayout.LayoutParams(0, -2, 1f))
        }
        return out
    }
}
