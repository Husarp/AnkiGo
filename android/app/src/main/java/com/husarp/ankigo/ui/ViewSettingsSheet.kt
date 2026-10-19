package com.husarp.ankigo.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.husarp.ankigo.R

// TICKET R10-H (B19): the battle's "View settings" sheet. B20's slider is [PokemonSizeSlider], used here and in Settings.

/**
 * Round 10 B19: the battle-look settings in one bottom sheet, opened from the battle's ⋯ menu in every host (Battle tab, Study screen,
 * floating battle window): Pokémon size, which side of our Pokémon shows, the info box style, the HP / XP bars and the battle frame (three
 * collapsed [LookChooser] entries that open into a grid of small live previews), the stat card under its own Pokémon, compact stat cards and the background pictures; on the Study screen also the panel's height
 * (Full / Strip / Mini). They are the same preferences as in Settings ([AppPrefs]); every change is written at once and every open battle
 * view follows live ([BattlePanelView]'s preference watcher). The look is the sort sheet's ([SortSheet]): handle, title, sections, a
 * mauve button; over other apps (no Activity) the dialog is an overlay window.
 */
internal class ViewSettingsSheet(
    private val ctx: Context,
    /** The Study screen's panel height choice; null = the host has no panel handle (the row is left out). */
    private val panelHeight: PanelHeightPart? = null,
) {
    /** Full (0) / Strip (1) / Mini (2): [current] is the mode the panel shows now, [set] moves the panel to that preset (the Study screen's own `setPanelDp`). */
    class PanelHeightPart(val current: () -> Int, val set: (Int) -> Unit)

    /** The sheet's rows, top to bottom. */
    enum class Part { SIZE, FACING, INFO_BOX, BARS, FRAME, CARD_OWN, COMPACT, BACKGROUND, HEIGHT }

    private var dismiss: () -> Unit = {}

    /**
     * R10-FIX (M2): on the Study screen in the stacked (portrait) layout the battle panel is at the bottom, so a bottom sheet would cover
     * exactly what is being changed. That host passes the panel height part only there, so the sheet then opens at the TOP of the window
     * (rounded bottom corners, at most [TOP_SHARE] of the height, the same light dim) and the panel stays visible below it.
     */
    val atTop: Boolean get() = atTop(panelHeight)

    /** The sheet's content (card, handle, title, the scrolling rows, the Done button); [maxBodyPx] caps the rows' height. */
    fun build(maxBodyPx: Int): View {
        val top = atTop
        val padTop = Ui.dp(ctx, if (top) 12 else 6)
        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.c(ctx, R.color.ag_card))
                val r = Ui.dp(ctx, 22f)
                cornerRadii = if (top) floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r) else floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(Ui.dp(ctx, 16), padTop, Ui.dp(ctx, 16), Ui.dp(ctx, 12))
        }
        if (top) {
            // a window that draws under the status bar / camera cut-out: keep the title clear of it
            ViewCompat.setOnApplyWindowInsetsListener(sheet) { v, ins ->
                val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                v.setPadding(Ui.dp(ctx, 16), padTop + b.top, Ui.dp(ctx, 16), Ui.dp(ctx, 12))
                ins
            }
        } else sheet.addView(View(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_ov), 2, ctx) },
            LinearLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 4)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(ctx, 4); it.bottomMargin = Ui.dp(ctx, 6)
            })
        sheet.addView(Ui.text(ctx, 17f, 900, R.color.ag_text).apply { text = "View settings"; maxLines = 1 }, LinearLayout.LayoutParams(-1, -2))
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        for (part in parts(panelHeight != null)) addPart(body, part)
        sheet.addView(MaxHeightScroll(ctx, maxBodyPx).apply {
            isVerticalScrollBarEnabled = false
            addView(body, FrameLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, -2))
        sheet.addView(Ui.text(ctx, 14f, 900, R.color.ag_crust).apply {
            text = "Done"
            gravity = Gravity.CENTER
            minHeight = Ui.dp(ctx, 48)
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 14, ctx)
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 10) })
        return sheet
    }

    private fun section(title: String): View = Ui.text(ctx, 11f, 900, R.color.ag_sub0).apply {
        text = title.uppercase()
        letterSpacing = 0.06f
        setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 10), 0, Ui.dp(ctx, 2))
    }

    private fun hint(text: String): View = Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply {
        this.text = text
        setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 4), 0, 0)
    }

    private fun addPart(body: LinearLayout, part: Part) {
        val full = LinearLayout.LayoutParams(-1, -2)
        val choosers = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 8) }
        when (part) {
            Part.SIZE -> {
                body.addView(section("Pokémon size"))
                val (bar, label) = PokemonSizeSlider.make(ctx, AppPrefs.pokemonSize(ctx), "Pokémon size") { AppPrefs.setPokemonSize(ctx, it) }
                body.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(bar, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(label.apply { gravity = Gravity.END; minWidth = Ui.dp(ctx, 48) }, LinearLayout.LayoutParams(-2, -2))
                }, full)
                body.addView(hint("Both Pokémon, kept inside the picture and off the info boxes."))
            }
            Part.FACING -> {
                body.addView(section("Facing"))
                body.addView(segmented(listOf("Back", "Front"), if (AppPrefs.playerFront(ctx)) 1 else 0) { AppPrefs.setPlayerFront(ctx, it == 1) }, full)
                body.addView(hint("Which side of your Pokémon you see; swiping across it turns it."))
            }
            Part.INFO_BOX -> body.addView(LookChoosers.infoBox(ctx, boxed = true), choosers)
            Part.BARS -> body.addView(LookChoosers.bars(ctx, boxed = true), choosers)
            Part.FRAME -> body.addView(LookChoosers.frame(ctx, boxed = true), choosers)
            Part.CARD_OWN -> body.addView(switchRow("Stat card under its own Pokémon", "Without a background picture", AppPrefs.cardsUnderOwn(ctx)) { AppPrefs.setCardsUnderOwn(ctx, it) },
                LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 6) })
            Part.COMPACT -> body.addView(switchRow("Compact stat cards", "Only name and HP", AppPrefs.compactCards(ctx)) { AppPrefs.setCompactCards(ctx, it) }, full)
            Part.BACKGROUND -> body.addView(switchRow("Background pictures", "A scene that fits the wild Pokémon's type", AppPrefs.backgroundPictures(ctx)) { AppPrefs.setBackgroundPictures(ctx, it) }, full)
            Part.HEIGHT -> {
                val h = panelHeight ?: return
                body.addView(section("Panel height"))
                body.addView(segmented(listOf("Full", "Strip", "Mini"), h.current().coerceIn(0, 2)) { h.set(it) }, full)
                body.addView(hint("The battle panel here; dragging its handle works too."))
            }
        }
    }

    /** Equal-width segments in one rounded `ag_mantle` bar, the picked one raised on `ag_card` ([LookChoosers.segmented], shared with the info box entry). */
    private fun segmented(labels: List<String>, selected: Int, onPick: (Int) -> Unit): View = LookChoosers.segmented(ctx, labels, selected, onPick)

    /** A 48 dp row: the label (with a grey line under it) and a switch. */
    private fun switchRow(label: String, sub: String, checked: Boolean, onSet: (Boolean) -> Unit): View {
        val sw = Switch(ctx).apply {
            isChecked = checked
            Ui.tintSwitch(this)
            setOnCheckedChangeListener { _, on -> onSet(on) }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = Ui.dp(ctx, 52)
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(ctx, 13f, 800, R.color.ag_text).apply { text = label })
                addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = sub }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 1) })
            }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 2); it.marginEnd = Ui.dp(ctx, 8) })
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

    /**
     * Shows the sheet at the bottom of the screen, or at the top on the stacked Study screen ([atTop]; an overlay window when [ctx] has no
     * Activity). The window dims the battle only a little and the sheet stays short, so the change being made can still be seen behind it.
     */
    fun show() {
        val dialog = Dialog(ctx).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        dismiss = { dialog.dismiss() }
        val top = atTop
        val maxBody = (ctx.resources.displayMetrics.heightPixels * (if (top) TOP_SHARE else BODY_SHARE)).toInt() - Ui.dp(ctx, 120)
        val sheet = build(maxBody.coerceAtLeast(Ui.dp(ctx, 120)))
        // at the top it closes by Done, a tap outside or Back (no drag: the handle is a bottom sheet's)
        val content = if (top) sheet else SheetKit.host(ctx, sheet,
            { dialog.window?.setWindowAnimations(0); dialog.cancel() },
            { dim -> dialog.window?.setDimAmount(dim * DIM / SheetDrag.DIM) })
        dialog.setContentView(content)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(DIM); setGravity(if (top) Gravity.TOP else Gravity.BOTTOM)
            if (Ui.findActivity(ctx) == null) setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
        dialog.window?.setLayout(minOf(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600)), -2)
    }

    companion object {
        /** The sheet (handle, title, rows, button) takes at most this share of the screen height. */
        const val BODY_SHARE = 0.8f

        /** ... and at the top of the Study screen only this much, so the battle panel below it stays in view. */
        const val TOP_SHARE = 0.55f

        /** The sheet opens at the top when the host passes its panel height part: only the Study screen in its stacked layout does. */
        fun atTop(panelHeight: PanelHeightPart?): Boolean = panelHeight != null

        /** The window dim behind the sheet: light, the battle must stay readable. */
        private const val DIM = 0.2f

        /** The rows in order; the panel height only where the host has a panel handle (the Study screen). */
        fun parts(withPanelHeight: Boolean): List<Part> = Part.entries.filter { it != Part.HEIGHT || withPanelHeight }

        /** Opens the sheet from a battle menu. */
        fun open(ctx: Context, panelHeight: PanelHeightPart? = null) = ViewSettingsSheet(ctx, panelHeight).show()
    }
}

/**
 * Round 10 B20: the Pokémon size slider, one for Settings and the view settings sheet: a plain SeekBar in the app colours (the volume
 * sliders' look, [Form.sliderRow]) over [PokemonSize.VALUES] with a label that shows the value ("100%").
 */
internal object PokemonSizeSlider {
    /** The slider and its value label. [onChange] gets the new size (80..130) every time the thumb moves to another step. */
    fun make(ctx: Context, percent: Int, title: String, onChange: (Int) -> Unit): Pair<SeekBar, TextView> {
        val label = Ui.text(ctx, 13f, 800, R.color.ag_sub1).apply { text = PokemonSize.label(percent) }
        val bar = SeekBar(ctx).apply {
            max = PokemonSize.VALUES.size - 1
            progress = PokemonSize.index(percent)
            progressTintList = ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mauve))
            progressBackgroundTintList = ColorStateList.valueOf(Ui.c(ctx, R.color.ag_s1))
            thumbTintList = ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mauve))
            splitTrack = false
            minimumHeight = Ui.dp(ctx, 40)
            contentDescription = title
            ViewCompat.setStateDescription(this, PokemonSize.label(percent))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val v = PokemonSize.at(progress)
                    label.text = PokemonSize.label(v)
                    ViewCompat.setStateDescription(sb, PokemonSize.label(v))   // TalkBack says "110%", not the step number
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        return bar to label
    }
}
