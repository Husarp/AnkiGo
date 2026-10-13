package com.husarp.ankigo.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.ActionResult
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.update.Updates
import java.util.Locale

// TICKET T14 owns this file: base class of the Team / Pokédex / Shop screens, dialog pickers and small shared widgets.

/**
 * Shared frame of the three between-battle screens: app bar, body, bottom nav (own tab selected), Updates hooks,
 * GameRepo listener (re-render after every change) and the "game not ready yet" message.
 * Subclasses build their views once ([buildBar], [buildBody]) and fill them in [render] (only called when the game is READY).
 */
abstract class GameScreen : Activity() {
    abstract val tab: BottomNav.Tab
    protected abstract fun buildBar(): View
    protected abstract fun buildBody(): View
    protected abstract fun render()

    val data: GameData get() = GameRepo.data!!
    val state: GameState get() = GameRepo.state!!

    private val handler = Handler(Looper.getMainLooper())
    private val renderNow = Runnable { doRender() }
    private val listener: () -> Unit = { requestRender() }
    private lateinit var content: View
    private lateinit var notReady: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = buildBar()
        content = buildBody()
        notReady = Ui.text(this, 14f, 700, R.color.ag_sub0).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@GameScreen, 24), Ui.dp(this@GameScreen, 24), Ui.dp(this@GameScreen, 24), Ui.dp(this@GameScreen, 24))
        }
        val body = FrameLayout(this).apply {
            addView(content, FrameLayout.LayoutParams(-1, -1))
            addView(notReady, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(this@GameScreen, R.color.ag_base))
            addView(bar, LinearLayout.LayoutParams(-1, -2))
            addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(BottomNav.create(this@GameScreen, tab), LinearLayout.LayoutParams(-1, -2))
        })
        GameRepo.load(this) { requestRender() }
    }

    override fun onResume() {
        super.onResume()
        Updates.onResume(this)
        GameRepo.addListener(listener)
        requestRender()
    }

    override fun onPause() {
        GameRepo.removeListener(listener)
        Updates.onPause(this)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(renderNow)
        super.onDestroy()
    }

    /** Re-renders once per message-loop turn, however many changes came in. */
    fun requestRender() {
        handler.removeCallbacks(renderNow)
        handler.post(renderNow)
    }

    private fun doRender() {
        if (isFinishing || isDestroyed) return
        val ok = GameRepo.status == GameRepo.Status.READY && GameRepo.data != null && GameRepo.state != null
        content.visibility = if (ok) View.VISIBLE else View.GONE
        notReady.visibility = if (ok) View.GONE else View.VISIBLE
        notReady.text = when (GameRepo.status) {
            GameRepo.Status.LOADING -> "Loading…"
            GameRepo.Status.NEED_DATA -> "Finish the first-run download on the Battle tab first."
            GameRepo.Status.NEED_STARTER -> "Pick your starter on the Battle tab first."
            GameRepo.Status.SAVE_ERROR -> "The save couldn't be read. Open the Battle tab for options."
            GameRepo.Status.READY -> "Something went wrong loading the game."
        }
        if (ok) render()
    }

    /** Runs an action through GameRepo (saves + notifies), shows its message, re-renders. */
    fun act(f: (Game) -> ActionResult): ActionResult {
        val r = GameRepo.run(this, f)
        report(r.message, r.events)
        requestRender()
        return r
    }

    /** Message toast (+ earned badges); an evolution gets a small dialog with the new sprite instead. */
    fun report(message: String, events: List<GameEvent> = emptyList()) {
        val evo = events.filterIsInstance<GameEvent.Evolved>().firstOrNull()
        val badges = events.filterIsInstance<GameEvent.BadgeEarned>().joinToString { it.title }
        if (evo != null) {
            Pickers.evolved(this, evo, state.mon(evo.uid)?.shiny == true)
            if (badges.isNotEmpty()) Ui.toast(this, "Badge: $badges")
        } else {
            Ui.toast(this, message + if (badges.isNotEmpty()) "\nBadge: $badges" else "")
        }
    }
}

/** Dialogs: choose from a list, confirm, pick a Pokémon / item / move. */
object Pickers {
    fun show(dialog: AlertDialog) {
        dialog.show()
        dialog.window?.setBackgroundDrawable(Ui.rounded(Ui.c(dialog.context, R.color.ag_card), 18, dialog.context))
    }

    /** A plain list of text choices. */
    fun choose(a: Activity, title: String, labels: List<String>, onPick: (Int) -> Unit) {
        show(AlertDialog.Builder(a).setTitle(title)
            .setItems(labels.toTypedArray()) { _, i -> onPick(i) }
            .setNegativeButton("Cancel", null).create())
    }

    fun confirm(a: Activity, title: String, message: String, yes: String = "OK", onYes: () -> Unit) {
        show(AlertDialog.Builder(a).setTitle(title).setMessage(message)
            .setPositiveButton(yes) { _, _ -> onYes() }
            .setNegativeButton("Cancel", null).create())
    }

    fun info(a: Activity, title: String, view: View) {
        show(AlertDialog.Builder(a).setTitle(title).setView(view).setPositiveButton("Close", null).create())
    }

    /** A list of [n] custom rows ([bind] builds row i); picking one closes the dialog and calls [onPick]. */
    fun rows(a: Activity, title: String, n: Int, bind: (Int) -> View, onPick: (Int) -> Unit) {
        lateinit var dlg: AlertDialog
        val list = ListView(a).apply {
            divider = null
            dividerHeight = 0
            adapter = object : BaseAdapter() {
                override fun getCount() = n
                override fun getItem(position: Int): Any = position
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = bind(position)
            }
            setOnItemClickListener { _, _, p, _ -> dlg.dismiss(); onPick(p) }
        }
        dlg = AlertDialog.Builder(a).setTitle(title).setView(list).setNegativeButton("Cancel", null).create()
        show(dlg)
    }

    /** Pick one of [mons]; [sub] is the second line of each row (HP, "can evolve"...). */
    fun pickMon(a: Activity, title: String, data: GameData, mons: List<Mon>, sub: (Mon) -> String, onPick: (Mon) -> Unit) {
        rows(a, title, mons.size, { Bits.monRow(a, data, mons[it], sub(mons[it])) }) { onPick(mons[it]) }
    }

    /** Pick one of the bag items [ids] (icon, name, note, ×owned). */
    fun pickItem(a: Activity, title: String, state: GameState, ids: List<String>, onPick: (String) -> Unit) {
        rows(a, title, ids.size, { Bits.itemRow(a, ids[it], state.count(ids[it]).let { n -> "×$n" }) }) { onPick(ids[it]) }
    }

    /** Pick one of the Pokémon's moves (returns its index 0..3). */
    fun pickMove(a: Activity, title: String, data: GameData, mon: Mon, onPick: (Int) -> Unit) {
        rows(a, title, mon.moves.size, { Bits.moveRow(a, data, mon.moves[it]) }, onPick)
    }

    /** Pick one of the move ids (TMs). */
    fun pickMoveId(a: Activity, title: String, data: GameData, moves: List<String>, onPick: (String) -> Unit) {
        rows(a, title, moves.size, { Bits.moveRow(a, data, moves[it]) }) { onPick(moves[it]) }
    }

    fun evolved(a: Activity, ev: GameEvent.Evolved, shiny: Boolean) {
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 4))
        }
        val img = ImageView(a)
        Ui.staticSprite(a, ev.toSpecies, shiny, img)
        col.addView(img, LinearLayout.LayoutParams(Ui.dp(a, 112), Ui.dp(a, 112)))
        col.addView(Ui.text(a, 15f, 800, R.color.ag_text).apply {
            text = "${ev.from} evolved into ${ev.to}!"
            gravity = Gravity.CENTER
        })
        show(AlertDialog.Builder(a).setTitle("Evolution").setView(col).setPositiveButton("Nice", null).create())
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
        Ui.staticSprite(ctx, mon.species, mon.shiny, img)
        addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)))
        addView(bodyText(ctx, (if (mon.shiny) "★ " else "") + mon.name, sub))
        addView(Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply { text = "Lv ${mon.level}" })
    }

    fun hpText(data: GameData, mon: Mon): String = "HP ${mon.hp}/${StatCalc.maxHp(data, mon)}"

    /** One move line: type chip, name, "Pow 90 · Acc 100 · Special". */
    fun moveRow(ctx: Context, data: GameData, id: String): View {
        val m = data.move(id)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 9), Ui.dp(ctx, 16), Ui.dp(ctx, 9))
            if (m == null) {
                addView(Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = id })
                return@apply
            }
            addView(Ui.typeChip(ctx, m.type), LinearLayout.LayoutParams(Ui.dp(ctx, 62), -2))
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

    /** Buy button (mockup `.buy`): coin + price on a yellow-tinted mantle, faded when [enabled] is false. */
    fun buyButton(ctx: Context, price: Int, enabled: Boolean, onClick: () -> Unit): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 7), Ui.dp(ctx, 9), Ui.dp(ctx, 7))
        background = Ui.rounded(blend(Ui.c(ctx, R.color.ag_yellow), Ui.c(ctx, R.color.ag_mantle), 0.22f), 10, ctx)
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
