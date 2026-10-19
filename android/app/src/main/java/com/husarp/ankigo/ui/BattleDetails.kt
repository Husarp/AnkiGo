package com.husarp.ankigo.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.WildDex
import com.husarp.ankigo.game.WildInfo
import com.husarp.ankigo.game.lookNum

/**
 * Tap on your Pokémon in a battle (PLAN 86): the same Details window as in the Pokédex / Box, on top of the battle.
 * Close returns to the battle; nothing switches to the Team tab. The window is built on a bare [GamePage] so heart,
 * rename and "More options" work like in the Team tab.
 */
object BattleDetails {
    /** Shows the details of [uid] over whatever [ctx] belongs to (an Activity or an overlay window). False when there is nothing to show. */
    fun show(ctx: Context, uid: String): Boolean {
        val data = GameRepo.data ?: return false
        val mon = GameRepo.state?.mon(uid) ?: return false
        val host = object : PageHost {
            override val hostActivity: Activity? = Ui.findActivity(ctx)
            override fun closePage() {}
            override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) { ctx.startActivity(intent) }
        }
        MonDetail.show(BarePage(ctx, host), data, mon)
        return true
    }

    /**
     * Tap on the wild Pokémon in a battle (PLAN 93): a read-only info window on top of the battle, from [WildInfo] (so never IVs,
     * nature, EVs or moves it has not used). Close returns to the battle, which keeps running underneath. False when there is nothing to show.
     */
    fun showWild(ctx: Context): Boolean {
        val data = GameRepo.data ?: return false
        val st = GameRepo.state ?: return false
        val battle = st.battle ?: return false
        val info = WildInfo.of(data, st, battle, GameRepo.wildMoves.used(battle)) ?: return false
        val compact = DetailParts.compact(ctx)
        val pad = if (compact) 16 else 20
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, pad), Ui.dp(ctx, 4), Ui.dp(ctx, pad), Ui.dp(ctx, 8))
        }

        // header: sprite, name (♂/♀ in colour, ★ when shiny), #num / level, types
        val img = ImageView(ctx)
        MonPreview.show(ctx, battle.wild.lookNum(data), info.shiny, img)
        img.setOnClickListener { SpriteViewer.show(ctx, battle.wild.lookNum(data), info.shiny) } // R10-B1: full-screen viewer
        img.contentDescription = "Show the picture full screen"
        val name = Ui.text(ctx, if (compact) 18f else 20f, 900, R.color.ag_text).apply {
            text = nameLine(ctx, info); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            if (!compact) gravity = Gravity.CENTER
        }
        val num = Ui.text(ctx, 12f, 800, R.color.ag_sub0).apply {
            text = LevelTier.line(ctx, "#%03d · ".format(info.dexNum), info.level)
            if (!compact) gravity = Gravity.CENTER
        }
        val types = Bits.typeRow(ctx, info.types)
        if (compact) {
            col.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, 64), Ui.dp(ctx, 64)))
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(name, LinearLayout.LayoutParams(-2, -2))
                    addView(num, LinearLayout.LayoutParams(-2, -2))
                    addView(types, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(ctx, 4) })
                }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(ctx, 12) })
            }, LinearLayout.LayoutParams(-1, -2))
        } else {
            col.addView(img, LinearLayout.LayoutParams(Ui.dp(ctx, 96), Ui.dp(ctx, 96)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
            col.addView(name, LinearLayout.LayoutParams(-1, -2))
            col.addView(num, LinearLayout.LayoutParams(-1, -2))
            col.addView(types, LinearLayout.LayoutParams(-2, -2).also { it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(ctx, 4) })
        }

        // the same sections, in the same order and look as the details of your own Pokémon (MonDetail); what a wild one can not have
        // or must not show (HP/XP, Training, ability, caught date, defeats) is left out
        val sp = data.sp(battle.wild.species)
        val mem = DetailSections.memory(ctx)
        val seen = st.seen + st.caught
        col.addView(DetailSections.section(ctx, mem, DetailSection.STATS, "Total ${info.stats.total}") {
            DetailSections.statsBody(ctx, info.stats, info.level, null, sp, compact)
        })
        // moves it has used in this fight; the others stay "?"
        val known = info.moves.mapNotNull { it.id }
        val unknown = info.moves.count { it.id == null }
        col.addView(DetailSections.section(ctx, mem, DetailSection.MOVES, "${known.size} of ${info.moves.size} known") {
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(ctx, 11f, 700, R.color.ag_ov).apply { text = "A move shows once it has used it in this fight." })
                addView(DetailSections.moves(ctx, data, known, compact, unknown))
            }
        })
        col.addView(DetailSections.section(ctx, mem, DetailSection.MATCHUPS, DetailFormat.matchupSummary(info.matchups)) { DetailSections.matchups(ctx, info.types, info.name) })
        col.addView(DetailSections.infoSection(ctx, mem, data, sp, emptyList(), null, null, seen, compact,
            lines = listOf("Tier" to info.tier.label, "Pokédex" to if (info.dex == WildDex.CAUGHT) "Caught" else "Seen, not caught yet"), onTap = null))
        Pickers.info(ctx, "Wild Pokémon", DetailSections.scroll(ctx, col))
        return true
    }

    /** "♂ ★ Name" with the ♂/♀ in its colour; nothing for a genderless one. */
    private fun nameLine(ctx: Context, info: WildInfo): CharSequence {
        val plain = (if (info.gender == 'M' || info.gender == 'F') com.husarp.ankigo.ui.Bits.genderSign(info.gender) + " " else "") +
            (if (info.shiny) "★ " else "") + info.name
        if (info.gender != 'M' && info.gender != 'F') return plain
        return android.text.SpannableString(plain).apply {
            setSpan(android.text.style.ForegroundColorSpan(Ui.c(ctx, if (info.gender == 'M') R.color.ag_blue else R.color.ag_pink)), 0, 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** A page that is never shown: only the Context (and the game actions) the Details window needs. */
    private class BarePage(base: Context, host: PageHost) : GamePage(base, host) {
        override val tab: BottomNav.Tab? = null
        override val needsGame = false
        override fun buildBar(): View = View(this)
        override fun buildBody(): View = View(this)
        override fun render() {}
    }
}
