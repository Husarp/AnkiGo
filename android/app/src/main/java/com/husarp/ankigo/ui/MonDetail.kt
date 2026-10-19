package com.husarp.ankigo.ui

import com.husarp.ankigo.game.lookNum
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.EvoLine
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.shownName
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.TypeMatchups
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// TICKET T14 owns this file: the Details dialog of one Pokémon and the evolution texts shared with the Pokédex.

object MonDetail {
    /** "Lv 16", "Thunder Stone", "Linking Cord", "Friendship", "knows Mimic" (+ day/night, other conditions). */
    fun method(data: GameData, e: Evolution): String = EvoLine.method(data, e) // the text lives in game/EvoLine.kt (R4-04)

    /** "Raichu (Thunder Stone)" for each evolution of [sp] that exists in the data. */
    fun evolvesInto(data: GameData, sp: Species): List<String> =
        sp.evolutions.filter { data.species.containsKey(it.to) }.map { "${data.sp(it.to).name} (${method(data, it)})" }

    /** "Evolves with a Thunder Stone" style one-liner, or "Fully evolved". */
    fun nextEvoLine(data: GameData, sp: Species): String {
        val e = sp.evolutions.firstOrNull { data.species.containsKey(it.to) } ?: return "Fully evolved"
        val to = data.sp(e.to).name
        return "Evolves into $to · ${method(data, e)}"
    }

    /** "Pichu (Friendship)" for the species this one evolves from, or null. */
    fun evolvesFrom(data: GameData, sp: Species): String? {
        val prevo = sp.prevo?.let { data.species[it] } ?: return null
        val e = prevo.evolutions.firstOrNull { it.to == sp.num }
        return if (e == null) prevo.name else "${prevo.name} (${method(data, e)})"
    }

    fun date(ms: Long): String = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(ms))

    /** The name line of the details as plain text: "♂ ★ Name" (♂/♀ first, nothing for a genderless one; ★ for a shiny). */
    fun nameLine(mon: Mon): String =
        when (mon.gender) { 'M', 'F' -> Bits.genderSign(mon.gender) + " "; else -> "" } + (if (mon.shiny) "★ " else "") + mon.shownName

    /** [nameLine] with the ♂/♀ in its colour (blue / pink); it has the name's own size. */
    private fun styledName(a: Context, mon: Mon): CharSequence {
        val plain = nameLine(mon)
        if (mon.gender != 'M' && mon.gender != 'F') return plain
        val color = Ui.c(a, if (mon.gender == 'M') R.color.ag_blue else R.color.ag_pink)
        return android.text.SpannableString(plain).apply {
            setSpan(android.text.style.ForegroundColorSpan(color), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun show(a: Context, data: GameData, mon: Mon) {
        // opened = seen: the NEW mark of its Box tile / Team row goes away (R4-V5)
        GameRepo.state?.let { st -> if (TileMarks.isNew(st, mon)) GameRepo.act(a) { it.markViewed(mon.uid) } }
        val page = a as? GamePage
        val sp = data.sp(mon.species)
        val stats = StatCalc.stats(sp, mon)
        val compact = DetailParts.compact(a)
        val pad = if (compact) 16 else 20
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(a, pad), Ui.dp(a, 4), Ui.dp(a, pad), Ui.dp(a, 8))
        }

        // header: sprite, name + pencil, #num / Lv / nature / gender, types, all tags
        val img = ImageView(a)
        MonPreview.show(a, mon.lookNum(data), mon.shiny, img)
        val spriteDp = if (compact) 64 else 96
        val nameText = Ui.text(a, if (compact) 18f else 20f, 900, R.color.ag_text).apply {
            text = styledName(a, mon)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            // leave room for the pencil (and the sprite when it sits beside the text): the dialog is at most about 88 % of the window wide
            maxWidth = Ui.dp(a, ((a.resources.configuration.screenWidthDp * 0.88f).toInt() - 2 * pad - 48 - 8 - if (compact) spriteDp + 12 + (if (page != null) HEART_COMPACT_DP else 0) else 0).coerceAtLeast(100))
        }
        val nameRow = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (compact) Gravity.CENTER_VERTICAL else Gravity.CENTER
            addView(nameText, LinearLayout.LayoutParams(-2, -2))
        }
        if (page != null) {
            val pencil = ImageView(a).apply {
                setImageResource(R.drawable.ic_pencil)
                setColorFilter(Ui.c(a, R.color.ag_sub1), android.graphics.PorterDuff.Mode.SRC_IN)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(Ui.dp(a, 15), Ui.dp(a, 15), Ui.dp(a, 15), Ui.dp(a, 15))
                contentDescription = "Rename"
                setOnClickListener {
                    MonActions(page).rename(mon) {
                        GameRepo.state?.mon(mon.uid)?.let { m -> nameText.text = styledName(a, m) }
                    }
                }
            }
            nameRow.addView(pencil, LinearLayout.LayoutParams(Ui.dp(a, 48), Ui.dp(a, 48)))
        }
        val numText = Ui.text(a, 12f, 800, R.color.ag_sub0).apply {
            text = LevelTier.line(a, "#%03d · ".format(sp.baseNum), mon.level, " · %s".format(mon.nature.label))
            if (!compact) gravity = Gravity.CENTER
        }
        val typeRow = Bits.typeRow(a, sp.types)
        if (compact) {
            // sprite beside name / number / types: saves height in the floating card and split screen
            val side = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(nameRow, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = -Ui.dp(a, 4) })
                addView(numText, LinearLayout.LayoutParams(-2, -2))
                addView(typeRow, LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(a, 4) })
            }
            col.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(img, LinearLayout.LayoutParams(Ui.dp(a, spriteDp), Ui.dp(a, spriteDp)))
                addView(side, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(a, 12) })
                if (page != null) addView(heartButton(a, page, mon, HEART_COMPACT_DP), LinearLayout.LayoutParams(Ui.dp(a, HEART_COMPACT_DP), Ui.dp(a, HEART_COMPACT_DP)))
            }, LinearLayout.LayoutParams(-1, -2))
        } else {
            // the sprite centred, the heart button at the right of the header
            col.addView(FrameLayout(a).apply {
                addView(img, FrameLayout.LayoutParams(Ui.dp(a, spriteDp), Ui.dp(a, spriteDp), Gravity.CENTER_HORIZONTAL))
                if (page != null) addView(heartButton(a, page, mon, 48), FrameLayout.LayoutParams(Ui.dp(a, 48), Ui.dp(a, 48), Gravity.END or Gravity.TOP))
            }, LinearLayout.LayoutParams(-1, -2))
            col.addView(nameRow, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = -Ui.dp(a, 8) })
            col.addView(numText, LinearLayout.LayoutParams(-1, -2))
            col.addView(typeRow, LinearLayout.LayoutParams(-2, -2).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.topMargin = Ui.dp(a, 4)
            })
        }
        // the state marks that tiles no longer show (held item, can evolve, fainted, regional form) and the other tags
        val tags = GameRepo.state?.let { MonTags.of(data, it, mon) }.orEmpty()
        if (tags.isNotEmpty()) col.addView(DetailSections.marks(a, tags), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 8) })

        // HP and XP, numbers
        val maxHp = stats.hp
        val hpFrac = mon.hp.toFloat() / maxHp.coerceAtLeast(1)
        col.addView(barLine(a, "HP", "${mon.hp} / $maxHp", hpFrac, Ui.hpColor(a, hpFrac), 7),
            LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 10) })
        val need = Progression.xpToNext(data, mon)
        val maxed = mon.level >= Tuning.MAX_LEVEL || need <= 0
        col.addView(barLine(a, "XP", if (maxed) "Max level" else "XP ${mon.xp} / $need", if (maxed) 1f else mon.xp.toFloat() / need,
            Ui.c(a, R.color.ag_sky), 5, BarMeaning.XP), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(a, 6) })

        // sections that open on tap; the open ones are remembered (DetailOpen)
        val mem = DetailSections.memory(a)
        val seen = GameRepo.state?.let { it.seen + it.caught }.orEmpty()
        col.addView(DetailSections.section(a, mem, DetailSection.STATS, "Total ${stats.total}") {
            DetailSections.statsBody(a, stats, mon.level, mon.nature, sp, compact)
        })
        col.addView(DetailSections.section(a, mem, DetailSection.MOVES, "${mon.moves.size} moves") { DetailSections.moves(a, data, mon.moves, compact) })
        col.addView(DetailSections.section(a, mem, DetailSection.MATCHUPS, DetailFormat.matchupSummary(TypeMatchups.of(sp.types))) { DetailSections.matchups(a, sp.types, mon.shownName) })
        col.addView(DetailSections.section(a, mem, DetailSection.TRAINING, mon.nature.label) { DetailSections.training(a, mon, compact) })
        val others = DetailFormat.otherAbilities(sp, mon.ability)
        col.addView(DetailSections.infoSection(a, mem, data, sp,
            listOf(mon.defeats.toString() to "Defeats"),
            Triple("Ability", mon.ability.ifEmpty { "–" }.replaceFirstChar { it.uppercase() }, others?.let { "Others of its kind: $it" }),
            if (mon.shownName != mon.name) mon.name else null, seen, compact,
            lines = listOf("Caught" to if (mon.caughtAt > 0) date(mon.caughtAt) else "–"),
            onTap = if (page != null) { s -> DexDetail.show(page, s) } else null))

        if (page == null) { Pickers.info(a, "Details", DetailSections.scroll(a, col)); return }
        lateinit var dlg: AppDialog
        lateinit var more: View
        more = DetailParts.secondaryButton(a, "More options") {
            // the menu hangs on this button, so the details close only once a row was tapped
            MonActions(page).menu(mon, more) { dlg.dismiss() }
        }
        // "More options" is pinned under the scrolling details (AppDialog footer, just above Close), compact or not
        val pinned = FrameLayout(a).apply {
            addView(more, FrameLayout.LayoutParams(-1, -2).also { it.setMargins(Ui.dp(a, 20), Ui.dp(a, 4), Ui.dp(a, 20), Ui.dp(a, 4)) })
        }
        dlg = AppDialog.Builder(a).setTitle("Details").setView(DetailSections.scroll(a, col)).setFooter(pinned)
            .setPositiveButton("Close", null).create()
        Pickers.show(dlg)
    }

    private const val HEART_COMPACT_DP = 40

    /**
     * The favourite button of the header (PLAN 81): a rounded heart button, [sizeDp] touch size. Off = grey outline heart on the quiet
     * pill; on = pink heart on a pink-tinted pill. It flips the favourite through the page and repaints itself.
     */
    private fun heartButton(a: Context, page: GamePage, mon: Mon, sizeDp: Int): View = ImageView(a).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val iconPad = Ui.dp(a, (sizeDp - 18) / 2)
        fun paint(on: Boolean) {
            setImageResource(if (on) R.drawable.ic_heart else R.drawable.ic_heart_outline)
            setColorFilter(Ui.c(a, if (on) R.color.ag_pink else R.color.ag_sub1), android.graphics.PorterDuff.Mode.SRC_IN)
            val fill = if (on) Bits.blend(Ui.c(a, R.color.ag_pink), Ui.c(a, R.color.ag_card), 0.22f) else Ui.c(a, R.color.ag_mantle)
            background = android.graphics.drawable.InsetDrawable(Ui.rounded(fill, 12, a), Ui.dp(a, 6))
            setPadding(iconPad, iconPad, iconPad, iconPad) // after the background, which resets the padding
            contentDescription = if (on) "Remove from favourites" else "Add to favourites"
        }
        paint(page.state.mon(mon.uid)?.favourite ?: mon.favourite)
        setOnClickListener {
            val now = page.state.mon(mon.uid)?.favourite ?: mon.favourite
            page.act { it.setFavourite(mon.uid, !now) }
            paint(!now)
        }
    }

    /** "HP   47 / 58" with a coloured bar between label and number. */
    private fun barLine(a: Context, label: String, value: String, frac: Float, color: Int, barDp: Int, meaning: BarMeaning? = null): View = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(Ui.text(a, 12f, 800, R.color.ag_sub0).apply { text = label }, LinearLayout.LayoutParams(Ui.dp(a, 28), -2))
        val bar = BarView(a)
        if (meaning != null) bar.setMeaning(meaning, frac, false) else bar.set(frac, color, false)
        addView(bar, LinearLayout.LayoutParams(0, Ui.dp(a, barDp), 1f).also { it.marginStart = Ui.dp(a, 6); it.marginEnd = Ui.dp(a, 10) })
        addView(Ui.text(a, 12f, 900, R.color.ag_text).apply { text = value; gravity = Gravity.END }, LinearLayout.LayoutParams(-2, -2))
    }
}
