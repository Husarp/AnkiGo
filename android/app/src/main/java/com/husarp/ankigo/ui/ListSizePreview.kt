package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.view.ViewCompat
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.lookNum

/**
 * PLAN 215: Settings → Look → "Pokémon size in lists": a five-step slider (the volume sliders' look) with a live preview under it, one row
 * of real-size Box tiles (the width a Box cell has on this screen, the sprite room it gives its picture) that follows the thumb. Fewer tiles
 * than four are shown when the card is too narrow.
 */
internal class ListSizePreview(ctx: Context, mons: List<Pair<Int, Boolean>>, step: Int) : LinearLayout(ctx) {
    private val sprites = ArrayList<TileSprite>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = Ui.rounded(Ui.c(ctx, R.color.ag_mantle), 12, ctx)
        val pad = Ui.dp(ctx, 8)
        setPadding(pad, pad, pad, pad)
        val dp1 = Ui.dp(ctx, 1f)
        val cols = GridMath.columns(ctx.resources.configuration.screenWidthDp)
        val cellW = (((ctx.resources.configuration.screenWidthDp - 20f) / cols - 4f) * dp1).toInt() // CellRow: 12 dp sides, 4 dp gaps (Box)
        val cellH = (cellW * TeamPage.CELL_RATIO).toInt()
        val data = GameRepo.data
        mons.forEachIndexed { i, (num, shiny) ->
            val img = TileSprite(ctx).apply { sizeStep = step }
            val name = Ui.text(ctx, 10f, 800, R.color.ag_sub1).apply {
                text = data?.species?.get(num)?.name ?: "#$num"
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
            }
            val tile = FrameLayout(ctx).apply { background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 10, ctx) }
            tile.addView(img, FrameLayout.LayoutParams(-1, -1).also {
                it.topMargin = Ui.dp(ctx, TeamPage.TOP_DP); it.bottomMargin = Ui.dp(ctx, TeamPage.STRIP_DP)
                it.leftMargin = Ui.dp(ctx, 2); it.rightMargin = Ui.dp(ctx, 2)
            })
            tile.addView(name, FrameLayout.LayoutParams(-1, Ui.dp(ctx, TeamPage.STRIP_DP), Gravity.BOTTOM).also {
                it.leftMargin = Ui.dp(ctx, 2); it.rightMargin = Ui.dp(ctx, 2)
            })
            tile.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            Ui.staticSprite(ctx, num, shiny, img)
            sprites += img
            addView(tile, LayoutParams(cellW, cellH).also { if (i > 0) it.marginStart = Ui.dp(ctx, 4) })
        }
    }

    /** The tiles that fit the width this row got; the rest are hidden. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val avail = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var used = 0
        var full = false
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val lp = c.layoutParams as MarginLayoutParams
            val need = lp.width + lp.marginStart
            if (full || (avail > 0 && used + need > avail)) { c.visibility = GONE; full = true } else { c.visibility = VISIBLE; used += need }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun setStep(step: Int) { sprites.forEach { it.sizeStep = step } }

    companion object {
        private val DEFAULTS = listOf(25 to false, 1 to false, 4 to false, 6 to false)

        /** The Pokémon the preview shows: the first species the player caught (up to 4, a shiny stays shiny), topped up with #25, #1, #4, #6. Pure. */
        fun pick(caught: List<Pair<Int, Boolean>>): List<Pair<Int, Boolean>> {
            val out = ArrayList<Pair<Int, Boolean>>()
            for (m in caught) if (out.size < 4 && out.none { it.first == m.first }) out += m
            for (m in DEFAULTS) if (out.size < 4 && out.none { it.first == m.first }) out += m
            return out
        }

        /** The Settings row: title, sub, the step name at the right, the slider and the live preview under it. */
        fun row(ctx: Context, step: Int, onChange: (Int) -> Unit): LinearLayout {
            val st = GameRepo.state
            val data = GameRepo.data
            val mons = pick(if (st == null) emptyList() else st.mons.map { it.lookNum(data) to it.shiny })
            val preview = ListSizePreview(ctx, mons, step)
            val label = Ui.text(ctx, 13f, 800, R.color.ag_sub1).apply { text = ListSpriteSize.name(step) }
            val title = "Pokémon size in lists"
            val bar = SeekBar(ctx).apply {
                max = ListSpriteSize.LAST
                progress = step
                progressTintList = ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mauve))
                progressBackgroundTintList = ColorStateList.valueOf(Ui.c(ctx, R.color.ag_s1))
                thumbTintList = ColorStateList.valueOf(Ui.c(ctx, R.color.ag_mauve))
                splitTrack = false
                minimumHeight = Ui.dp(ctx, 40)
                contentDescription = title
                ViewCompat.setStateDescription(this, ListSpriteSize.name(step))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                        label.text = ListSpriteSize.name(progress)
                        ViewCompat.setStateDescription(sb, ListSpriteSize.name(progress)) // TalkBack says "Large", not the step number
                        preview.setStep(progress)
                        if (fromUser) onChange(progress)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            }
            val control = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(bar, LinearLayout.LayoutParams(-1, -2))
                addView(preview, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 4); it.bottomMargin = Ui.dp(ctx, 4) })
            }
            return Form.stackRow(ctx, title, "Pokédex and Box pictures", control, right = label)
        }
    }
}
