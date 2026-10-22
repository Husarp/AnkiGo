package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.husarp.ankigo.R

// TICKET R10-H (B19): the battle's "View settings". B20's slider is [PokemonSizeSlider], used here and in Settings.
// TICKET R15-F (PLAN 141): it is a full screen with a live battle preview now ([ViewSettingsScreen]); this class keeps the rows and the entry.

/**
 * Round 10 B19 / Round 15 F (PLAN 141): the battle-look settings, opened from the battle's ⋯ menu in every host (Battle tab, Study screen,
 * floating battle window) as a FULL-SCREEN page ([ViewSettingsScreen]: a live battle preview on top, these rows under it). The rows: Pokémon
 * size, which side of our Pokémon shows, the info box style, the HP / XP bars and the battle frame (three collapsed [LookChooser] entries
 * that open into a grid of small live previews), the stat card under its own Pokémon, compact stat cards and the background pictures; on
 * the Study screen also the panel's height (Full / Strip / Mini). They are the same preferences as in Settings ([AppPrefs]); every change is
 * written at once and every open battle view follows live ([BattlePanelView]'s preference watcher), the page's preview included. Only the
 * panel height is held back: it moves the preview and goes to the host when the page closes.
 */
internal class ViewSettingsSheet(
    private val ctx: Context,
    /** The Study screen's panel height choice; null = the host has no panel handle (the row is left out). */
    private val panelHeight: PanelHeightPart? = null,
) {
    /** Full (0) / Strip (1) / Mini (2): [current] is the mode the panel shows now, [set] moves the panel to that preset (the Study screen's own `setPanelDp`). */
    class PanelHeightPart(val current: () -> Int, val set: (Int) -> Unit)

    /** The rows, top to bottom. */
    enum class Part { SIZE, FACING, INFO_BOX, BARS, FRAME, CARD_OWN, COMPACT, BACKGROUND, HEIGHT }

    /**
     * The rows in one vertical column (the page scrolls it). [onHeight] hears a pick in the Panel height row (0 Full, 1 Strip, 2 Mini); it is
     * not the host's setter: the page keeps the choice and hands it to the host once, when it closes.
     */
    fun rows(onHeight: (Int) -> Unit = {}): LinearLayout {
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        for (part in parts(panelHeight != null)) addPart(body, part, onHeight)
        return body
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

    private fun addPart(body: LinearLayout, part: Part, onHeight: (Int) -> Unit) {
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
                body.addView(segmented(listOf("Full", "Strip", "Mini"), h.current().coerceIn(0, 2)) { onHeight(it) }, full)
                body.addView(hint("Shown in the preview now; the battle panel takes it when you close this page. Mini shows only the action bar."))
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

    companion object {
        /** The rows in order; the panel height only where the host has a panel handle (the Study screen). */
        fun parts(withPanelHeight: Boolean): List<Part> = Part.entries.filter { it != Part.HEIGHT || withPanelHeight }

        /** Opens the page from a battle menu (a full-window dialog: over other apps it is an overlay window). */
        fun open(ctx: Context, panelHeight: PanelHeightPart? = null) = ViewSettingsScreen(ctx, panelHeight).show()
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
