package com.husarp.ankigo.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.AchCelebration
import com.husarp.ankigo.game.AchPageMath
import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.game.DailyGift
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.GiftPop
import com.husarp.ankigo.game.ItemFind
import com.husarp.ankigo.game.ItemInfo
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.PopupMath
import android.widget.ImageView
import java.lang.ref.WeakReference

// R4-V2 (PLAN 79, pick 2F): the popup when an achievement tier (or a badge one-off) is earned: a big medal on a burst, "Epic!", the title,
// buttons "Nice" and "See it" (opens Stats > Achievements with THAT achievement's sheet open). Several at once: ONE popup lists them.
//
// Where it hooks in: GameRepo.act / GameRepo.run call [offer] with the events of every action (answers, balls, the live answers from
// AnkiDroid, the stats feed ...), and AnkiGoApp's lifecycle callbacks call [resumed] / [paused]. So it shows on whichever AnkiGo screen is in
// front; when none is (floating window over AnkiDroid, app in the background, picture-in-picture) the batch waits and shows at the next
// resume. The queue lives in memory only: the earned tiers themselves are in the save, only the popup would be missed after a kill.
//
// R6-07 (items 19, 20): the popup says "Achievement unlocked" on top; an item found in a fight ([GameEvent.ItemFound]) gets the same kind of
// popup ("Item found": picture, name, what it does) through the same queue: one popup at a time, the achievements of a batch first, the items
// after them (all the items waiting form one popup). Like the achievement popup it is a dialog of the screen in front: a tap anywhere closes it.
//
// R14-B2-D: the "Daily gift" popup (the gift of the once-a-day goal moment, [GameEvent.DailyGift]) goes through the same queue: after the
// achievements of a batch, before the found items; one gift per popup. It shows the item's picture (or the TM disc), its name with the count
// in words, what the item does and the goal-streak line. An achievement tier with a reward (the Master Ball) says so on its popup.

object AchievementPopup {
    /** Wait a moment after the action so the answer's own animation is not covered at once. */
    internal const val DELAY_MS = 700L

    private val pending = ArrayList<AchCelebration>()
    private var pendingItems: List<ItemFind> = emptyList()
    private var shownItems: List<ItemFind> = emptyList()
    private var pendingGifts: List<GiftPop> = emptyList()
    private var shownGift: GiftPop? = null
    private var front: WeakReference<Activity>? = null
    private var dialog: Dialog? = null
    private var shown: List<AchCelebration> = emptyList()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val showNow = Runnable { tryShow() }

    /** Celebrations waiting for a screen (tests). */
    internal fun waiting(): List<AchCelebration> = pending.toList()

    /** Found items waiting for a screen (tests). */
    internal fun waitingItems(): List<ItemFind> = pendingItems

    /** Daily gifts waiting for a screen (tests). */
    internal fun waitingGifts(): List<GiftPop> = pendingGifts

    internal fun reset() { pending.clear(); shown = emptyList(); pendingItems = emptyList(); shownItems = emptyList(); pendingGifts = emptyList(); shownGift = null; front = null; dialog = null }

    /** The events of one action: what is worth a celebration is queued and shown (see the file comment). */
    fun offer(events: List<GameEvent>) {
        val items = AchPageMath.celebrations(events)
        val finds = PopupMath.finds(events)
        val gifts = PopupMath.gifts(events)
        if (items.isEmpty() && finds.isEmpty() && gifts.isEmpty()) return
        pending += items.filter { n -> pending.none { it.rowId == n.rowId && it.tier == n.tier } }
        pendingItems = PopupMath.merge(pendingItems, finds)
        pendingGifts = pendingGifts + gifts
        schedule()
    }

    /** An AnkiGo screen came to the front. */
    fun resumed(a: Activity) { front = WeakReference(a); schedule() }

    /** An AnkiGo screen left the front: its open popup closes and its batch goes back to the queue. */
    fun paused(a: Activity) {
        if (front?.get() !== a) return
        front = null
        dialog?.let { d ->
            if (d.isShowing) {
                dialog = null; d.dismiss()
                pending.addAll(0, shown); shown = emptyList()
                pendingItems = PopupMath.merge(shownItems, pendingItems); shownItems = emptyList()
                shownGift?.let { pendingGifts = listOf(it) + pendingGifts }; shownGift = null
            }
        }
    }

    private fun schedule() {
        if ((pending.isEmpty() && pendingItems.isEmpty() && pendingGifts.isEmpty()) || front?.get() == null) return
        handler.removeCallbacks(showNow)
        handler.postDelayed(showNow, DELAY_MS)
    }

    private fun tryShow() {
        val a = front?.get() ?: return
        if ((pending.isEmpty() && pendingItems.isEmpty() && pendingGifts.isEmpty()) || dialog?.isShowing == true || a.isFinishing || a.isDestroyed || a.isInPictureInPictureMode) return
        val d = Dialog(a, android.R.style.Theme_Translucent_NoTitleBar)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.45f)
        }
        fun finish() { if (dialog === d) { dialog = null; shown = emptyList(); shownItems = emptyList(); shownGift = null }; d.dismiss(); schedule() } // a tap closes it; more waiting? the next batch follows
        if (pending.isNotEmpty()) {
            val batch = pending.toList()
            pending.clear()
            shown = batch
            d.setContentView(layer(a, batch, onNice = { finish() }, onSee = { id ->
                finish()
                Nav.openAchievement(a, id)
            }))
        } else if (pendingGifts.isNotEmpty()) { // no achievement waiting: the daily gift (one per popup), before the found items
            val gift = pendingGifts.first()
            pendingGifts = pendingGifts.drop(1)
            shownGift = gift
            d.setContentView(giftLayer(a, GameRepo.data, gift, onNice = { finish() }))
        } else { // the found items
            val batch = pendingItems
            pendingItems = emptyList()
            shownItems = batch
            d.setContentView(itemLayer(a, batch, onNice = { finish() }))
        }
        d.setOnCancelListener { finish() }
        dialog = d
        d.show()
    }

    // ---- the popup's look (shots render this view) ----

    /**
     * The full-window layer: the card in the middle (scrolls when the window is short), a tap outside it = [onNice]. [onSee] gets the id of the
     * achievement whose sheet opens (the one shown; with several, the first listed, or the row that was tapped). One item: big medal on a
     * burst, the tier word, the title; several: a list.
     */
    fun layer(ctx: Context, items: List<AchCelebration>, onNice: () -> Unit, onSee: (String?) -> Unit): View {
        val frame = FrameLayout(ctx).apply { isClickable = true; setOnClickListener { onNice() } }
        val card = if (items.size == 1) single(ctx, items[0], onNice, onSee) else many(ctx, items, onNice, onSee)
        card.isClickable = true // a tap on the card itself is not a tap outside
        frame.addView(cardScroll(ctx, card), FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).also { it.setMargins(Ui.dp(ctx, 28), Ui.dp(ctx, 12), Ui.dp(ctx, 28), Ui.dp(ctx, 12)) })
        return frame
    }

    /** The card in a ScrollView that stops short of the window's edges and keeps a phone-like width in wide windows. */
    private fun cardScroll(ctx: Context, card: View): ScrollView = object : ScrollView(ctx) {
        override fun onMeasure(w: Int, h: Int) {
            val cap = MeasureSpec.getSize(h) - Ui.dp(ctx, 24)
            val wide = Ui.dp(ctx, 400) // wide windows (landscape, tablet): a phone-like card in the middle
            super.onMeasure(if (MeasureSpec.getSize(w) > wide) MeasureSpec.makeMeasureSpec(wide, MeasureSpec.EXACTLY) else w,
                if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED || cap <= 0) h else MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        }
    }.apply { isVerticalScrollBarEnabled = false; isFillViewport = false; addView(card, ViewGroup.LayoutParams(-1, -2)) }

    private fun cardBox(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 22, ctx)
        setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 8), Ui.dp(ctx, 14), Ui.dp(ctx, 14))
        elevation = Ui.dp(ctx, 8f)
    }

    private fun colorOf(ctx: Context, c: AchCelebration): Int = AchStyle.medalColor(ctx, c.tier, special = c.tier < 0)

    private fun single(ctx: Context, c: AchCelebration, onNice: () -> Unit, onSee: (String?) -> Unit): View {
        val box = cardBox(ctx)
        val color = colorOf(ctx, c)
        box.addView(title(ctx, PopupMath.achievementTitle(1)), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 4) })
        val h = ctx.resources.configuration.screenHeightDp // a small window: a smaller burst, so the buttons stay in view
        val (stageDp, medalDp) = if (h in 1..299) 84 to 54 else if (h in 1..459) 104 to 62 else 170 to 92
        val stage = FrameLayout(ctx)
        stage.addView(Burst(ctx, color), FrameLayout.LayoutParams(-1, Ui.dp(ctx, stageDp)))
        stage.addView(AchMedal(ctx, medalDp).apply { set(AchStyle.icon(AchievementTable.row(c.rowId)?.icon ?: ""), c.tier, special = c.tier < 0) },
            FrameLayout.LayoutParams(Ui.dp(ctx, medalDp), Ui.dp(ctx, medalDp), Gravity.CENTER))
        box.addView(stage, LinearLayout.LayoutParams(-1, -2))
        box.addView(Ui.text(ctx, 24f, 900, R.color.ag_text).apply { text = if (c.tier < 0) "Badge!" else c.word; setTextColor(color) }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 2) })
        val sub = if (c.threshold != null) "${c.title} · ${AchPageMath.fmt(AchievementTable.row(c.rowId)?.metric, c.threshold)}" else c.title
        box.addView(Ui.text(ctx, 14f, 800, R.color.ag_sub1).apply { text = sub; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 4) })
        AchPageMath.rewardOf(c.rowId, c.tier)?.let { reward -> // R14-B2-D: "Reward: a Master Ball, in your Bag"
            box.addView(Ui.text(ctx, 13f, 900, R.color.ag_text).apply { text = AchPageMath.rewardPopupText(reward); gravity = Gravity.CENTER; setTextColor(color) },
                LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 8) })
        }
        box.addView(buttons(ctx, "See it", onNice) { onSee(c.rowId) }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 14) })
        return box
    }

    private fun many(ctx: Context, items: List<AchCelebration>, onNice: () -> Unit, onSee: (String?) -> Unit): View {
        val box = cardBox(ctx)
        box.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 16), Ui.dp(ctx, 14), Ui.dp(ctx, 14))
        box.addView(Ui.text(ctx, 22f, 900, R.color.ag_text).apply { text = PopupMath.achievementTitle(items.size); setTextColor(AchStyle.tierColor(ctx, items.maxOf { it.tier }.coerceAtLeast(2))) })
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        for (c in items) {
            val color = colorOf(ctx, c)
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 14, ctx)
                setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
                isClickable = true
                setOnClickListener { onSee(c.rowId) }
            }
            row.addView(AchMedal(ctx, 38).apply { set(AchStyle.icon(AchievementTable.row(c.rowId)?.icon ?: ""), c.tier, special = c.tier < 0, ground = Ui.c(ctx, R.color.ag_mantle)) }, LinearLayout.LayoutParams(Ui.dp(ctx, 38), Ui.dp(ctx, 38)))
            val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            body.addView(Ui.text(ctx, 13.5f, 800, R.color.ag_text).apply { text = c.title; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            val tag = if (c.threshold != null) "${c.tierName} · ${AchPageMath.fmt(AchievementTable.row(c.rowId)?.metric, c.threshold)}" else "Badge"
            body.addView(Ui.text(ctx, 11.5f, 900, R.color.ag_text).apply { text = tag; setTextColor(color) }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 1) })
            AchPageMath.rewardOf(c.rowId, c.tier)?.let { reward -> // R14-B2-D
                body.addView(Ui.text(ctx, 11.5f, 800, R.color.ag_sub1).apply { text = AchPageMath.rewardPopupText(reward) }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 1) })
            }
            row.addView(body, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 10) })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 7) })
        }
        box.addView(list, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 6) })
        // "See it" opens the sheet of the first one listed (every row opens its own); there is no "See all" without a sheet (Adam's pick 2F)
        box.addView(buttons(ctx, "See it", onNice) { onSee(items.first().rowId) }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 14) })
        return box
    }

    /** The small line on top of a popup that says what it is ("Achievement unlocked", "Item found"). */
    private fun title(ctx: Context, text: String): TextView = Ui.text(ctx, 12.5f, 900, R.color.ag_sub1).apply {
        this.text = text.uppercase(); letterSpacing = 0.08f; gravity = Gravity.CENTER
    }

    /**
     * The full-window layer of the "Item found" popup (shots render this view): one item = a big picture, its name, what it does and how many
     * the bag holds now; several = a list. A tap outside the card or on "Nice" calls [onNice].
     */
    fun itemLayer(ctx: Context, finds: List<ItemFind>, onNice: () -> Unit): View {
        val frame = FrameLayout(ctx).apply { isClickable = true; setOnClickListener { onNice() } }
        val box = cardBox(ctx)
        box.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14))
        box.isClickable = true // a tap on the card itself is not a tap outside
        box.addView(title(ctx, PopupMath.itemTitle(finds.size)), LinearLayout.LayoutParams(-2, -2))
        val small = ctx.resources.configuration.screenHeightDp in 1..459
        if (finds.size == 1) {
            val f = finds[0]
            val pic = ImageView(ctx).also { Ui.itemIcon(ctx, f.item, it) }
            val side = if (small) 56 else 84
            box.addView(pic, LinearLayout.LayoutParams(Ui.dp(ctx, side), Ui.dp(ctx, side)).also { it.topMargin = Ui.dp(ctx, if (small) 8 else 14) })
            box.addView(Ui.text(ctx, 22f, 900, R.color.ag_text).apply { text = Items.name(f.item); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 8) })
            box.addView(Ui.text(ctx, 14f, 800, R.color.ag_sub1).apply { text = PopupMath.description(f.item); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 4) })
            val how = ItemInfo.howToUse(f.item)
            if (how.isNotEmpty()) box.addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = how; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 6) })
            box.addView(Ui.text(ctx, 12f, 900, R.color.ag_sub0).apply { text = "You have ${f.total}"; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 6) })
        } else {
            val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            for (f in finds) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 14, ctx)
                    setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
                }
                row.addView(ImageView(ctx).also { Ui.itemIcon(ctx, f.item, it) }, LinearLayout.LayoutParams(Ui.dp(ctx, 38), Ui.dp(ctx, 38)))
                val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                body.addView(Ui.text(ctx, 13.5f, 800, R.color.ag_text).apply { text = Items.name(f.item); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                body.addView(Ui.text(ctx, 11.5f, 700, R.color.ag_sub0).apply { text = "${PopupMath.description(f.item)} · you have ${f.total}"; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END },
                    LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 1) })
                row.addView(body, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 10) })
                list.addView(row, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 7) })
            }
            box.addView(list, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 6) })
        }
        box.addView(Ui.text(ctx, 14f, 900, R.color.ag_crust).apply {
            text = "Nice"; gravity = Gravity.CENTER; minHeight = Ui.dp(ctx, 48)
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 14, ctx); isClickable = true; setOnClickListener { onNice() }
        }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 14) })
        frame.addView(cardScroll(ctx, box), FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).also { it.setMargins(Ui.dp(ctx, 28), Ui.dp(ctx, 12), Ui.dp(ctx, 28), Ui.dp(ctx, 12)) })
        return frame
    }

    /**
     * R14-B2-D: the full-window layer of the "Daily gift" popup (shots render this view): the item's picture (or the TM disc), the name with its
     * count in words ("3 Poké Balls", "Great Ball", "TM Thunderbolt"), what it does, and the goal-streak line. A tap outside the card or on
     * "Nice" calls [onNice]. [data] null = no game data (the TM shows its move id and no type colour).
     */
    fun giftLayer(ctx: Context, data: GameData?, gift: GiftPop, onNice: () -> Unit): View {
        val frame = FrameLayout(ctx).apply { isClickable = true; setOnClickListener { onNice() } }
        val box = cardBox(ctx)
        box.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14))
        box.isClickable = true // a tap on the card itself is not a tap outside
        box.addView(title(ctx, PopupMath.GIFT_TITLE), LinearLayout.LayoutParams(-2, -2))
        val small = ctx.resources.configuration.screenHeightDp in 1..459
        val pic = ImageView(ctx)
        if (gift.tm != null) Ui.tmIcon(ctx, data?.move(gift.tm)?.type, pic) else if (gift.item != null) Ui.itemIcon(ctx, gift.item, pic)
        val side = if (small) 56 else 84
        box.addView(pic, LinearLayout.LayoutParams(Ui.dp(ctx, side), Ui.dp(ctx, side)).also { it.topMargin = Ui.dp(ctx, if (small) 8 else 14) })
        box.addView(Ui.text(ctx, 22f, 900, R.color.ag_text).apply { text = DailyGift.name(data, gift.item, gift.tm, gift.count); gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 8) })
        val what = when {
            gift.item != null -> ItemInfo.line(data, gift.item)
            else -> data?.let { ItemInfo.plain(ItemInfo.moveEffect(it, gift.tm.orEmpty())) }?.takeIf { it.isNotBlank() } ?: ItemInfo.tmHowToUse()
        }
        box.addView(Ui.text(ctx, 14f, 800, R.color.ag_sub1).apply { text = what; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 4) })
        box.addView(Ui.text(ctx, 12f, 700, R.color.ag_sub0).apply { text = DailyGift.streakLine(gift.streakDays); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 10) })
        box.addView(Ui.text(ctx, 14f, 900, R.color.ag_crust).apply {
            text = "Nice"; gravity = Gravity.CENTER; minHeight = Ui.dp(ctx, 48)
            background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 14, ctx); isClickable = true; setOnClickListener { onNice() }
        }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 14) })
        frame.addView(cardScroll(ctx, box), FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).also { it.setMargins(Ui.dp(ctx, 28), Ui.dp(ctx, 12), Ui.dp(ctx, 28), Ui.dp(ctx, 12)) })
        return frame
    }

    /** "[see]" (soft) and "Nice" (accent), side by side. */
    private fun buttons(ctx: Context, see: String, onNice: () -> Unit, onSee: () -> Unit): View {
        fun btn(label: String, primary: Boolean, tap: () -> Unit): TextView = Ui.text(ctx, 14f, 900, if (primary) R.color.ag_crust else R.color.ag_text).apply {
            text = label
            gravity = Gravity.CENTER
            minHeight = Ui.dp(ctx, 48)
            background = Ui.rounded(Ui.c(ctx, if (primary) R.color.ag_mauve else R.color.ag_s0), 14, ctx)
            isClickable = true
            setOnClickListener { tap() }
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btn(see, false, onSee), LinearLayout.LayoutParams(0, -2, 1f))
            addView(btn("Nice", true, onNice), LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 8) })
        }
    }

    /** Soft rays behind the medal: wedges in [color] that fade out towards the edge. */
    private class Burst(ctx: Context, private val color: Int) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val box = RectF()
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val r = minOf(width, height) / 2f * 1.05f
            paint.shader = RadialGradient(cx, cy, r, intArrayOf(AchStyle.alpha(color, 0.45f), AchStyle.alpha(color, 0f)), floatArrayOf(0.25f, 1f), Shader.TileMode.CLAMP)
            box.set(cx - r, cy - r, cx + r, cy + r)
            for (i in 0 until 18) canvas.drawArc(box, i * 20f, 10f, true, paint)
        }
    }
}
