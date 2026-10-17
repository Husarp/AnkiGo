package com.husarp.ankigo.ui

import com.husarp.ankigo.game.lookNum
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.InsetDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Type
import java.util.Locale

// TICKET T14 owns this file: dialog pickers and small shared widgets of the Team / Pokédex / Shop screens (the page frame is in Pages.kt).

/** Dialogs: choose from a list, confirm, pick a Pokémon / item / move. */
object Pickers {
    /** Screenshot tests set this to take a dialog instead of showing it (a renderer has no window for it). Null in the app. */
    internal var interceptApp: ((AppDialog) -> Unit)? = null

    /**
     * Shows an [AppDialog] (the app's own dialog card, see AppDialog.kt). Works in an Activity and over other apps (the
     * dialog becomes an overlay window when there is no Activity).
     */
    fun show(dialog: AppDialog) {
        interceptApp?.let { it(dialog); return }
        if (Ui.findActivity(dialog.context) == null) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
    }

    /** [show] for a platform dialog (the system time picker): over other apps (no Activity) its window becomes an overlay window, else `show()` throws BadTokenException. */
    fun showSystem(dialog: android.app.Dialog) {
        if (Ui.findActivity(dialog.context) == null) dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    /** A plain list of text choices. */
    fun choose(a: Context, title: String, labels: List<String>, onPick: (Int) -> Unit) {
        show(AppDialog.Builder(a).setTitle(title)
            .setItems(labels.toTypedArray()) { _, i -> onPick(i) }
            .setNegativeButton("Cancel", null).create())
    }

    fun confirm(a: Context, title: String, message: String, yes: String = "OK", onYes: () -> Unit) {
        show(AppDialog.Builder(a).setTitle(title).setMessage(message)
            .setPositiveButton(yes) { _, _ -> onYes() }
            .setNegativeButton("Cancel", null).create())
    }

    /**
     * One-line text input in the dialog look: Nunito field on `ag_mantle`, [hint], [initial] text cut to [max] characters,
     * IME Done = Save. [onSave] gets the raw text; [neutral] adds a third button (label + action) such as "Use species name".
     * Over other apps the dialog is its own overlay window with default (focusable) flags, so the keyboard works there
     * without touching the floating card's own focus toggle.
     */
    fun input(a: Context, title: String, hint: String, initial: String, max: Int, save: String = "Save",
              neutral: Pair<String, () -> Unit>? = null, onSave: (String) -> Unit) {
        val field = android.widget.EditText(a).apply {
            this.hint = hint
            typeface = Ui.font(a, 700)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Ui.c(a, R.color.ag_text))
            setHintTextColor(Ui.c(a, R.color.ag_ov))
            Ui.tintEdit(this)
            setSingleLine()
            filters = arrayOf(android.text.InputFilter.LengthFilter(max))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            background = Ui.rounded(Ui.c(a, R.color.ag_mantle), 12, a)
            backgroundTintList = null // tintEdit's underline tint would paint the whole rounded fill in the accent (white text on green)
            setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10))
            setText(initial)
            setSelection(text.length)
        }
        val box = FrameLayout(a).apply {
            setPadding(Ui.dp(a, 24), Ui.dp(a, 8), Ui.dp(a, 24), Ui.dp(a, 0))
            addView(field, FrameLayout.LayoutParams(-1, Ui.dp(a, 44)))
        }
        val b = AppDialog.Builder(a).setTitle(title).setView(box)
            .setPositiveButton(save) { _, _ -> onSave(field.text.toString()) }
            .setNegativeButton("Cancel", null)
        neutral?.let { (label, act) -> b.setNeutralButton(label) { _, _ -> act() } }
        val dlg = b.create()
        field.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { dlg.dismiss(); onSave(field.text.toString()); true } else false
        }
        show(dlg)
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        field.requestFocus()
    }

    fun info(a: Context, title: String, view: View) {
        show(AppDialog.Builder(a).setTitle(title).setView(view).setPositiveButton("Close", null).create())
    }

    /** A list of [n] custom rows ([bind] builds row i); picking one closes the dialog and calls [onPick]. */
    fun rows(a: Context, title: String, n: Int, bind: (Int) -> View, onPick: (Int) -> Unit) {
        lateinit var dlg: AppDialog
        val list = ListView(a).apply {
            divider = null
            dividerHeight = 0
            // rows carry 12 dp (start) and 16 dp (end) of their own: with this the content lines up with the dialog title (24 dp)
            setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 8), 0)
            clipToPadding = false
            adapter = object : BaseAdapter() {
                override fun getCount() = n
                override fun getItem(position: Int): Any = position
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = bind(position).also { it.minimumHeight = Ui.dp(a, 48) }
            }
            setOnItemClickListener { _, _, p, _ -> dlg.dismiss(); onPick(p) }
        }
        dlg = AppDialog.Builder(a).setTitle(title).setView(list).setNegativeButton("Cancel", null).create()
        show(dlg)
    }

    /** Pick one of [mons]; [sub] is the second line of each row (HP, "can evolve"...). */
    fun pickMon(a: Context, title: String, data: GameData, mons: List<Mon>, sub: (Mon) -> String, onPick: (Mon) -> Unit) {
        rows(a, title, mons.size, { Bits.monRow(a, data, mons[it], sub(mons[it])) }) { onPick(mons[it]) }
    }

    /** Pick one of the bag items [ids] (icon, name, note, ×owned). */
    fun pickItem(a: Context, title: String, state: GameState, ids: List<String>, onPick: (String) -> Unit) {
        rows(a, title, ids.size, { Bits.itemRow(a, ids[it], state.count(ids[it]).let { n -> "×$n" }) }) { onPick(ids[it]) }
    }

    /** Pick one of the Pokémon's moves (returns its index 0..3). */
    fun pickMove(a: Context, title: String, data: GameData, mon: Mon, onPick: (Int) -> Unit) {
        rows(a, title, mon.moves.size, { Bits.moveRow(a, data, mon.moves[it]) }, onPick)
    }

    /** Pick one of the move ids (TMs). */
    fun pickMoveId(a: Context, title: String, data: GameData, moves: List<String>, onPick: (String) -> Unit) {
        rows(a, title, moves.size, { Bits.moveRow(a, data, moves[it]) }) { onPick(moves[it]) }
    }

    fun evolved(a: Context, ev: GameEvent.Evolved, shiny: Boolean) {
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 4))
        }
        val img = ImageView(a)
        MonPreview.show(a, ev.toSpecies, shiny, img)
        col.addView(img, LinearLayout.LayoutParams(Ui.dp(a, 112), Ui.dp(a, 112)))
        col.addView(Ui.text(a, 15f, 800, R.color.ag_text).apply {
            text = "${ev.from} evolved into ${ev.to}!"
            gravity = Gravity.CENTER
        })
        show(AppDialog.Builder(a).setTitle("Evolution").setView(col).setPositiveButton("Nice", null).create())
    }
}

/** Small widgets shared by the three screens. */
object Bits {
    fun fmt(n: Int): String = String.format(Locale.US, "%,d", n)

    fun typeRow(ctx: Context, types: List<Type>): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        types.forEachIndexed { i, t ->
            addView(Ui.typeChip(ctx, t), LinearLayout.LayoutParams(-2, -2).also { if (i > 0) it.marginStart = Ui.dp(ctx, 3) })
        }
    }

    /** Section title (mockup `.sect`): 12 sp / 900 caps, optional right-hand text. */
    fun sect(ctx: Context, title: String, right: CharSequence? = null): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 14), Ui.dp(ctx, 4), Ui.dp(ctx, 8))
        addView(Ui.text(ctx, 12f, 900, R.color.ag_sub0).apply {
            text = title.uppercase()
            letterSpacing = 0.058f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (right != null) addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = right })
    }

    /** The 40 dp `ag_mantle` tile with an item icon (mockup `.item .ic`). */
    fun itemTile(ctx: Context, item: String, sizeDp: Int = 40): FrameLayout = FrameLayout(ctx).apply {
        background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 10, ctx)
        val img = ImageView(ctx)
        Ui.itemIcon(ctx, item, img)
        val inner = Ui.dp(ctx, sizeDp * 3 / 4)
        addView(img, FrameLayout.LayoutParams(inner, inner, Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, sizeDp), Ui.dp(ctx, sizeDp))
    }

    /** A tile with plain text instead of an icon (TM). */
    fun textTile(ctx: Context, label: String, color: Int, sizeDp: Int = 40): FrameLayout = FrameLayout(ctx).apply {
        background = Ui.rounded(blend(color, Ui.c(ctx, R.color.ag_mantle), 0.35f), 10, ctx)
        addView(Ui.text(ctx, 12f, 900, R.color.ag_text).apply { text = label }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, sizeDp), Ui.dp(ctx, sizeDp))
    }

    /** TM tile (pick 7A): a disc in the move's type colour with the type symbol in the middle; [type] null (unknown move) = the plain grey TM disc. */
    fun tmTile(ctx: Context, type: Type?, sizeDp: Int = 40): FrameLayout = FrameLayout(ctx).apply {
        val img = ImageView(ctx)
        Ui.tmIcon(ctx, type, img)
        val inner = Ui.dp(ctx, sizeDp * 9 / 10)
        addView(img, FrameLayout.LayoutParams(inner, inner, Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, sizeDp), Ui.dp(ctx, sizeDp))
    }

    fun blend(over: Int, under: Int, a: Float): Int {
        fun ch(shift: Int) = (((over shr shift) and 0xFF) * a + ((under shr shift) and 0xFF) * (1 - a)).toInt()
        return Color.rgb(ch(16), ch(8), ch(0))
    }

    /** Name over a small grey note: the middle part of an item row. */
    fun bodyText(ctx: Context, name: String, note: String?): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(Ui.text(ctx, 14f, 900, R.color.ag_text).apply { text = name; maxLines = 1 })
        if (!note.isNullOrEmpty()) addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = note })
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 10) }
    }

    /** A card row (mockup `.item`): [lead] tile, [middle], optional [right] view. */
    fun cardRow(ctx: Context, lead: View, middle: View, right: View?): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 14, ctx)
        setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 6))
        addView(lead)
        addView(middle)
        if (right != null) addView(right)
        layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 6) }
    }

    /** Dialog row for a bag item: icon, name, note and [right] text (usually "×3"). */
    fun itemRow(ctx: Context, item: String, right: String): View {
        val def = Items.def(item)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 5), Ui.dp(ctx, 16), Ui.dp(ctx, 5))
            addView(itemTile(ctx, item, 36))
            addView(bodyText(ctx, Items.name(item), def?.note))
            addView(Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { text = right })
        }
    }

    /** Dialog row for a Pokémon: sprite, "Name  Lv N" and a second line. */
    fun monRow(ctx: Context, data: GameData, mon: Mon, sub: String): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 2), Ui.dp(ctx, 16), Ui.dp(ctx, 2))
        alpha = if (mon.hp <= 0) 0.55f else 1f
        val img = ImageView(ctx)
        MonPreview.show(ctx, mon.lookNum(data), mon.shiny, img)
        addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)))
        addView(bodyText(ctx, (if (mon.shiny) "★ " else "") + mon.name, sub))
        addView(Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { LevelTier.style(this, mon.level) })
    }

    fun hpText(data: GameData, mon: Mon): String = "HP ${mon.hp}/${StatCalc.maxHp(data, mon)}"

    /** One move line: type chip, name, "Pow 90 · Acc 100 · Special". */
    fun moveRow(ctx: Context, data: GameData, id: String): View {
        val m = data.move(id)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 9), Ui.dp(ctx, 16), Ui.dp(ctx, 9))
            if (m == null) {
                addView(Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = id })
                return@apply
            }
            addView(Ui.typeChip(ctx, m.type).also { it.minWidth = Ui.dp(ctx, 62) }, LinearLayout.LayoutParams(-2, -2))
            addView(Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = m.name; maxLines = 1 },
                LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 8) })
            addView(Ui.text(ctx, 11f, 700, R.color.ag_sub0).apply { text = moveStats(data, id) })
        }
    }

    fun moveStats(data: GameData, id: String): String {
        val m = data.move(id) ?: return ""
        val cat = when (m.category) { Category.PHYSICAL -> "Physical"; Category.SPECIAL -> "Special"; Category.STATUS -> "Status" }
        val pow = if (m.damaging && m.power > 0) m.power.toString() else "—"
        return "Pow $pow · Acc ${m.accuracy ?: "—"} · $cat"
    }

    /**
     * Buy button (mockup `.buy`): coin + price on a yellow-tinted mantle, faded when [enabled] is false. The pill is 30 dp
     * tall inside a 48 dp touch height; [compact] drops the extra touch height (the shop tile is tappable as a whole).
     */
    fun buyButton(ctx: Context, price: Int, enabled: Boolean, compact: Boolean = false, onClick: () -> Unit): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        val fill = Ui.rounded(blend(Ui.c(ctx, R.color.ag_yellow), Ui.c(ctx, R.color.ag_mantle), 0.22f), 10, ctx)
        if (compact) {
            background = fill
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 7), Ui.dp(ctx, 12), Ui.dp(ctx, 7))
        } else {
            val inset = Ui.dp(ctx, 9)
            background = InsetDrawable(fill, 0, inset, 0, inset)
            minimumHeight = Ui.dp(ctx, 48)
            setPadding(Ui.dp(ctx, 9), 0, Ui.dp(ctx, 9), 0)
        }
        alpha = if (enabled) 1f else 0.45f
        addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_coin)
            setColorFilter(Ui.c(ctx, R.color.ag_yellow), PorterDuff.Mode.SRC_IN)
        }, LinearLayout.LayoutParams(Ui.dp(ctx, 12), Ui.dp(ctx, 12)).also { it.marginEnd = Ui.dp(ctx, 3) })
        addView(Ui.text(ctx, 12f, 900, R.color.ag_text).apply { text = fmt(price); maxLines = 1 })
        setOnClickListener { onClick() }
    }

    /** Small filled label for the Pokémon's gender. */
    fun genderSign(g: Char): String = when (g) { 'M' -> "♂"; 'F' -> "♀"; else -> "–" }

    fun scroll(ctx: Context, child: View): ScrollView = ScrollView(ctx).apply { addView(child) }
}

/** A cell that keeps a fixed height/width ratio ([ratio] 1 = square). */
class SquareFrame(ctx: Context, private val ratio: Float) : FrameLayout(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((w * ratio).toInt(), MeasureSpec.EXACTLY),
        )
    }
}

/**
 * One ListView row of [cols] equal cells with a [gapDp] gap (the "grid" of box and Pokédex: a ListView of rows recycles
 * views and takes a header, which GridView can't). [create] fills each cell once and stores its holder in `frame.tag`.
 */
class CellRow(ctx: Context, cols: Int, ratio: Float, gapDp: Int, sidePadDp: Int, create: (SquareFrame) -> Unit) : LinearLayout(ctx) {
    val cells: List<SquareFrame>

    init {
        orientation = HORIZONTAL
        val half = Ui.dp(ctx, gapDp) / 2
        setPadding(Ui.dp(ctx, sidePadDp) - half, 0, Ui.dp(ctx, sidePadDp) - half, 0)
        cells = List(cols) {
            SquareFrame(ctx, ratio).also { f ->
                create(f)
                addView(f, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { lp -> lp.setMargins(half, half, half, half) })
            }
        }
    }
}
