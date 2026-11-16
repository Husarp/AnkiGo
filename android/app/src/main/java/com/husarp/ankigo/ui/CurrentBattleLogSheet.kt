package com.husarp.ankigo.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import com.husarp.ankigo.R

/** PLAN 652 A: the current fight, oldest first, over the battle that remains on screen. */
internal class CurrentBattleLogSheet(private val ctx: Context, private val load: () -> LogBattle? = { BattleLog.snapshot().firstOrNull() }, private val close: () -> Unit) : LinearLayout(ctx) {
    private val body = LinearLayout(ctx).apply { orientation = VERTICAL }
    internal val scroll = MotionScrollView(ctx).apply { addView(body); contentDescription = "Current fight events" }
    internal val newEvents = Ui.text(ctx, 12f, 800, R.color.ag_mauve).apply {
        text = "New events ↓"; gravity = Gravity.CENTER; minimumHeight = Ui.dp(ctx, 40); visibility = GONE
        setOnClickListener { follow = true; scroll.fullScroll(View.FOCUS_DOWN); visibility = GONE }
    }
    private var follow = true
    private var battleId: Long? = null
    private var shown = emptyList<BattleLogEntry>()
    private var reading = false
    private var active = false
    private val refresh = object : Runnable {
        override fun run() {
            if (!active || reading) return
            reading = true
            Thread {
                val current = load()
                post {
                    reading = false
                    if (active) { showBattle(current); postDelayed(this, 600) }
                }
            }.apply { isDaemon = true; name = "ankigo-current-battle-log" }.start()
        }
    }

    init {
        orientation = VERTICAL
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 22, ctx)
        setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 12))
        addView(View(ctx).apply { tag = SheetKit.HANDLE_TAG; background = Ui.rounded(Ui.c(ctx, R.color.ag_ov), 2, ctx) },
            LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 4)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        addView(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(ctx, 17f, 900, R.color.ag_text).apply { text = "Current battle log" }, LayoutParams(0, -2, 1f))
            addView(Ui.text(ctx, 22f, 700, R.color.ag_sub0).apply {
                text = "×"; gravity = Gravity.CENTER; contentDescription = "Close battle log"
                setOnClickListener { close() }
            }, LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48)))
        }, LayoutParams(-1, -2))
        addView(scroll, LayoutParams(-1, 0, 1f))
        addView(newEvents, LayoutParams(-1, -2))
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            follow = y + scroll.height >= body.height - Ui.dp(ctx, 12)
            if (follow) newEvents.visibility = GONE
        }
        showBattle(null)
    }

    internal fun showBattle(battle: LogBattle?) {
        val entries = entries(battle)
        if (battle?.startedAt == battleId && entries == shown && body.childCount > 0) return
        val changedBattle = battle?.startedAt != battleId
        if (changedBattle) follow = true
        val oldY = scroll.scrollY
        battleId = battle?.startedAt
        shown = entries
        body.removeAllViews()
        if (entries.isEmpty()) body.addView(Ui.text(ctx, 13f, 700, R.color.ag_sub0).apply { text = "No events in this fight yet." })
        var round = -1
        for (entry in entries) {
            if (entry.round != round) {
                round = entry.round
                body.addView(Ui.text(ctx, 10f, 900, R.color.ag_sub0).apply {
                    text = if (round == 0) "ENCOUNTER" else "ROUND $round"
                    setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 4))
                })
            }
            body.addView(Ui.text(ctx, 12f, 700, if (entry.text.startsWith("Battle alert")) R.color.ag_peach else R.color.ag_text).apply {
                text = entry.text
                setPadding(0, Ui.dp(ctx, 7), 0, Ui.dp(ctx, 7))
            }, LayoutParams(-1, -2))
        }
        if (follow) {
            newEvents.visibility = GONE
            scroll.post { if (follow) scroll.fullScroll(View.FOCUS_DOWN) }
        } else {
            newEvents.visibility = VISIBLE
            scroll.post { if (!follow) scroll.scrollTo(0, oldY) }
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); active = true; post(refresh) }
    override fun onDetachedFromWindow() { active = false; removeCallbacks(refresh); super.onDetachedFromWindow() }

    companion object {
        /** Old saved logs remain readable; new fights retain every entry in event order. */
        internal fun entries(battle: LogBattle?): List<BattleLogEntry> = battle?.timeline().orEmpty()

        fun open(ctx: Context) {
            val dialog = Dialog(ctx).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
            val sheet = CurrentBattleLogSheet(ctx) { dialog.dismiss() }
            val height = (ctx.resources.displayMetrics.heightPixels * .56f).toInt()
            val host = SheetKit.dialog(dialog, sheet)
            sheet.layoutParams = LayoutParams(-1, -1)
            dialog.setContentView(host, android.view.ViewGroup.LayoutParams(-1, height))
            dialog.setCanceledOnTouchOutside(true)
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(.24f); setGravity(Gravity.BOTTOM)
                if (Ui.findActivity(ctx) == null) setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
            dialog.show()
            AwayWindows.track(dialog) { it.dismiss() }
            dialog.window?.setLayout(minOf(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600)), height)
        }
    }
}
