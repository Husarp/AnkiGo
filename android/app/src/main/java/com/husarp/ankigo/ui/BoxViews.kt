package com.husarp.ankigo.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.lookNum
import com.husarp.ankigo.game.Mon

/** Small views shared by the Box grid, the copies sheet and the favourite buttons (items 54, 45, 81). */
internal object BoxViews {
    /** PLAN 282 / R19: the original tile content stays inside the pile's front card. */
    private class TileContent(ctx: Context, val front: TileSprite) : FrameLayout(ctx) {
        val rear = TileSprite(ctx).apply { alpha = 0.75f; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        var plan = BoxDupePlan.of(1, false, false)
        var refresh: (() -> Unit)? = null
        var frontBackground: Drawable? = null
        var picked = false
        private val prefs = ctx.getSharedPreferences("ankigo_ui", 0)
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == BoxDupePrefs.STACKED || key == BoxDupePrefs.TWO) refresh?.invoke()
        }
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            prefs.registerOnSharedPreferenceChangeListener(listener)
            refresh?.invoke()
        }
        override fun onDetachedFromWindow() {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            super.onDetachedFromWindow()
        }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            val two = plan.sprites == 2
            // Mockup: 60 + 52 - 24 = 88 units; the pair fits exactly inside the sprite lane
            front.scaleX = if (two) 60f / 88f else 1f
            front.scaleY = front.scaleX
            front.translationX = if (two) -front.width * (14f / 88f) else 0f
            rear.scaleX = 52f / 88f; rear.scaleY = rear.scaleX
            rear.translationX = rear.width * (18f / 88f)
            rear.translationY = -rear.height * 0.10f
        }
    }

    fun wrapTile(frame: FrameLayout, sprite: TileSprite) {
        val content = TileContent(frame.context, sprite)
        while (frame.childCount > 0) {
            val child = frame.getChildAt(0)
            val lp = child.layoutParams
            frame.removeViewAt(0); content.addView(child, lp)
        }
        content.addView(content.rear, content.indexOfChild(sprite), FrameLayout.LayoutParams(sprite.layoutParams as FrameLayout.LayoutParams))
        frame.addView(content, FrameLayout.LayoutParams(-1, -1))
    }

    fun bindDupes(frame: FrameLayout, sprite: TileSprite, tile: BoxTile, data: GameData) {
        val content = sprite.parent as TileContent
        content.refresh = { bindDupes(frame, sprite, tile, data) }
        val look = BoxDupePrefs.get(frame.context)
        content.plan = BoxDupePlan.of(tile.count, look.stacked, look.two)
        content.rear.visibility = if (content.plan.sprites == 2) View.VISIBLE else View.GONE
        if (content.plan.sprites == 2) {
            val other = tile.members[1]
            content.rear.sizeStep = sprite.sizeStep
            Ui.staticSprite(frame.context, other.lookNum(data), other.shiny, content.rear)
        }
        val inset = Ui.dp(frame.context, content.plan.insetDp)
        (content.layoutParams as FrameLayout.LayoutParams).apply {
            rightMargin = inset; bottomMargin = inset
            leftMargin = if (inset > 0) Ui.dp(frame.context, 3) else 0
            topMargin = if (inset > 0) Ui.dp(frame.context, 2) else 0
        }
        content.requestLayout()
        tileBackground(frame, content.frontBackground ?: content.background ?: frame.background, content.picked)
    }

    /** Keeps Compare's frame on the front card, including when only its tick changes. */
    fun tileBackground(frame: FrameLayout, front: Drawable?, picked: Boolean = false) {
        val content = frame.getChildAt(0) as? TileContent ?: return
        content.frontBackground = front
        content.picked = picked
        content.background = if (content.plan.cards > 0 && !picked) {
            (front?.constantState?.newDrawable()?.mutate() as? GradientDrawable)?.apply {
                setStroke(Ui.dp(frame.context, 1), Ui.c(frame.context, R.color.ag_sub0))
            } ?: front
        } else front
        frame.background = if (content.plan.cards > 0) FannedCards(frame.context, content.plan.cards) else null
        frame.setPadding(0, 0, 0, 0)
    }

    /** PLAN 306 option C: exact lower fan geometry from the approved native design fixture. */
    private class FannedCards(private val ctx: Context, private val cards: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private fun card(canvas: Canvas, rect: RectF, fill: Int) {
            val radius = Ui.dp(ctx, 9f)
            paint.style = Paint.Style.FILL; paint.color = fill
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.STROKE; paint.color = Ui.c(ctx, R.color.ag_sub0)
            paint.strokeWidth = Ui.dp(ctx, 1f)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        override fun draw(canvas: Canvas) {
            val d = ctx.resources.displayMetrics.density
            val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
            val save = canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            if (cards == 2) {
                val rear = canvas.save()
                canvas.rotate(-6f, w * .5f, h * .7f)
                card(canvas, RectF(8*d, 10*d, w-8*d, h-4*d), Ui.c(ctx, R.color.ag_s1))
                canvas.restoreToCount(rear)
            }
            canvas.rotate(5f, w * .5f, h * .7f)
            card(canvas, RectF(6*d, 8*d, w-8*d, h-6*d), Ui.c(ctx, R.color.ag_s0))
            canvas.restoreToCount(save)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** Two people (outline, accent colour, no text, no background): marks a team member. Not the bottom bar's Team tab icon. */
    val TEAM_ICON: Int get() = R.drawable.ic_team

    /** A tinted small icon view. */
    fun icon(ctx: Context, res: Int, colorRes: Int): ImageView = ImageView(ctx).apply {
        setImageResource(res)
        setColorFilter(Ui.c(ctx, colorRes), PorterDuff.Mode.SRC_IN)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    /** The team mark: [TEAM_ICON] in the accent colour. */
    fun teamMark(ctx: Context): ImageView = icon(ctx, TEAM_ICON, R.color.ag_mauve)

    /** The favourite mark: a small pink heart. */
    fun heartMark(ctx: Context): ImageView = icon(ctx, R.drawable.ic_heart, R.color.ag_pink)

    /**
     * A filter chip (28 dp look, 48 dp touch height): selected = accent fill, else the quiet `ag_mantle` pill. [heart] puts a heart
     * before the label (pink when off, the chip's ink colour when on).
     */
    fun chip(ctx: Context, label: String, on: Boolean, heart: Boolean = false, onClick: () -> Unit): TextView {
        val ink = Ui.c(ctx, if (on) R.color.ag_crust else R.color.ag_sub1)
        return Ui.text(ctx, 12f, 800, R.color.ag_sub1).apply {
            text = label
            maxLines = 1
            setTextColor(ink)
            Ui.touchPill(this, Ui.rounded(if (on) Ui.c(ctx, R.color.ag_mauve) else Ui.c(ctx, R.color.ag_mantle), 10, ctx), 28, 10)
            if (heart) {
                val d = ctx.getDrawable(R.drawable.ic_heart)!!.mutate()
                d.setTint(if (on) ink else Ui.c(ctx, R.color.ag_pink))
                val s = Ui.dp(ctx, 12)
                d.setBounds(0, 0, s, s)
                setCompoundDrawablesRelative(d, null, null, null)
                compoundDrawablePadding = Ui.dp(ctx, 5)
            }
            setOnClickListener { UiMotion.pop(this); onClick() }
        }
    }

    /**
     * R18 253: a quick chip of the filter row, a toggle in place: lit ([on], the [chip] accent look) while its filter is on, a tap flips it.
     * TalkBack reads "<label>, on" / "<label>, off" (the label without the star).
     */
    fun quickChip(ctx: Context, label: String, on: Boolean, heart: Boolean = false, onClick: () -> Unit): TextView =
        chip(ctx, label, on, heart, onClick).apply {
            isSelected = on
            contentDescription = "${label.removePrefix("★ ")}, ${if (on) "on" else "off"}"
        }

    /** "Brave · IV 142" (nature, then the six IVs added up): the second line of a copy in the sheet. */
    fun copyNote(mon: Mon): String = "${mon.nature.label} · IV ${mon.ivs.total}"
}
