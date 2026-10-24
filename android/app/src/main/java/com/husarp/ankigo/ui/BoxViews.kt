package com.husarp.ankigo.ui

import android.content.Context
import android.graphics.PorterDuff
import android.widget.ImageView
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Mon

/** Small views shared by the Box grid, the copies sheet and the favourite buttons (items 54, 45, 81). */
internal object BoxViews {
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
            setOnClickListener { onClick() }
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
