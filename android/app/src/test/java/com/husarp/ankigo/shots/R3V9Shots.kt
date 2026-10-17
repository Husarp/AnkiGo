package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.data.SpriteKind
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.FloatKind
import com.husarp.ankigo.ui.FloatText
import com.husarp.ankigo.ui.FloatTextView
import com.husarp.ankigo.ui.LogKind
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.ui.XpCard
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * R3-V9 (pick 9): comic floating texts + team XP cards. PNGs: r3v9-kinds (every text kind and the stat badges, static),
 * r3v9-round / r3v9-round2 (several texts of one round on the arena, stacked), r3v9-xp (two XP cards, one with a level-up;
 * PHONE / SPLIT / FLOATBATTLE / FLOATB / PIP). Reduced motion (see [arenaOf]).
 */
class R3V9Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    // ---------------------------------------------------------------- the kinds, static

    private fun gallery(ctx: Context): View {
        fun tv(k: FloatKind, text: String, onPlayer: Boolean = false, stat: String? = null, stages: Int = 0) = FloatTextView(ctx, FloatText(onPlayer, text, k, stat, stages), 0f)
        val rows = listOf(
            listOf(tv(FloatKind.DAMAGE, "-31"), tv(FloatKind.BIG_DAMAGE, "-84"), tv(FloatKind.HEAL, "+12")),
            listOf(tv(FloatKind.MISS, "Miss"), tv(FloatKind.NO_EFFECT, "No effect"), tv(FloatKind.NOT_VERY, "Not very effective")),
            listOf(tv(FloatKind.SUPER, "Super effective!"), tv(FloatKind.CRIT, "Critical!")),
            listOf(tv(FloatKind.STATUS, "Burned"), tv(FloatKind.STATUS, "Fell asleep"), tv(FloatKind.STATUS, "Can't move")),
            listOf(tv(FloatKind.HESITATE, "Hesitated"), tv(FloatKind.INFO, "ATK max")),
            listOf(tv(FloatKind.STAT_UP, "ATK ↑", stat = "ATK", stages = 1), tv(FloatKind.STAT_UP, "SPE ↑↑", stat = "SPE", stages = 2), tv(FloatKind.STAT_UP, "SPA ↑↑↑", stat = "SPA", stages = 3)),
            listOf(tv(FloatKind.STAT_DOWN, "DEF ↓", stat = "DEF", stages = 1), tv(FloatKind.STAT_DOWN, "ACC ↓↓", stat = "ACC", stages = 2), tv(FloatKind.STAT_DOWN, "EVA ↓↓↓", stat = "EVA", stages = 3)),
        )
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Ui.c(ctx, R.color.ag_arena_top), Ui.c(ctx, R.color.ag_arena_bot)))
        }
        for (r in rows) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (v in r) row.addView(v, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(Ui.dp(ctx, 5), Ui.dp(ctx, 3), Ui.dp(ctx, 5), Ui.dp(ctx, 3)) })
            col.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        return col
    }

    @Test fun kinds() = paparazzi.shootWith("r3v9-kinds", listOf(Size.PHONE, Size.SPLIT, Size.PIP)) { ctx -> gallery(ctx) }

    // ---------------------------------------------------------------- in the arena

    /** Runs [fire] on the arena's first real layout (floatText / showXpCard place by the arena's size), then lays it out once more. */
    private fun afterLayout(arena: ArenaView, fire: (ArenaView) -> Unit) {
        var done = false
        arena.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!done && v.width > 0 && v.height > 0) {
                done = true
                fire(arena)
                arena.measure(View.MeasureSpec.makeMeasureSpec(arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(arena.height, View.MeasureSpec.EXACTLY))
                arena.layout(arena.left, arena.top, arena.right, arena.bottom)
            }
        }
    }

    private fun smallPanel(ctx: Context): BattlePanelView {
        val p = BattlePanelView(ctx)
        p.setCompact(true); p.renderAll()
        p.setLog(LogKind.GOOD, "Good", "Sparkmouse used {{Thunderbolt}}!", false)
        return p
    }

    /**
     * Paparazzi advances Handler time but not View animators, so these shots use reduced motion: texts and cards stand at their
     * final spot (stacked, no stagger delay) instead of sitting at the invisible start of their pop / slide.
     */
    /** The study screen's own panel ([BattlePanelView.setStudyLayout]): the normal study arena, or the small strip. */
    private fun studyPanel(ctx: Context, strip: Boolean, fire: (ArenaView) -> Unit): View {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val p = BattlePanelView(ctx)
        p.setStudyLayout(strip, false); p.renderAll()
        p.setLog(LogKind.GOOD, "Good", "Sparkmouse used {{Thunderbolt}}!", false)
        afterLayout(p.arena, fire)
        return p
    }

    private fun arenaOf(ctx: Context, small: Boolean, fire: (ArenaView) -> Unit): View {
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        if (small) { val p = smallPanel(ctx); afterLayout(p.arena, fire); return p }
        val b = BattleShotKit.bound(ctx)
        afterLayout(b.arena!!, fire)
        return b.root
    }

    private fun round(a: ArenaView) {
        a.floatText(FloatText(false, "-31", FloatKind.BIG_DAMAGE))
        a.floatText(FloatText(false, "Super effective!", FloatKind.SUPER))
        a.floatText(FloatText(false, "Critical!", FloatKind.CRIT))
        a.floatText(FloatText(true, "ATK ↑↑", FloatKind.STAT_UP, "ATK", 2))
        a.floatText(FloatText(true, "Hesitated", FloatKind.HESITATE))
        a.floatText(FloatText(true, "+12", FloatKind.HEAL))
    }

    @Test fun roundFull() = paparazzi.shootWith("r3v9-round", listOf(Size.PHONE, Size.SPLIT), offsetMillis = 850) { ctx -> arenaOf(ctx, false, ::round) }
    @Test fun roundSmall() = paparazzi.shootWith("r3v9-round", listOf(Size.FLOATBATTLE, Size.FLOATB, Size.PIP), offsetMillis = 850) { ctx -> arenaOf(ctx, true, ::round) }

    private fun round2(a: ArenaView) {
        a.floatText(FloatText(false, "Miss", FloatKind.MISS))
        a.floatText(FloatText(false, "DEF ↓↓↓", FloatKind.STAT_DOWN, "DEF", 3))
        a.floatText(FloatText(true, "Not very effective", FloatKind.NOT_VERY))
        a.floatText(FloatText(true, "Burned", FloatKind.STATUS))
        a.floatText(FloatText(true, "-9", FloatKind.DAMAGE))
    }

    @Test fun round2Full() = paparazzi.shootWith("r3v9-round2", listOf(Size.PHONE, Size.SPLIT), offsetMillis = 600) { ctx -> arenaOf(ctx, false, ::round2) }
    @Test fun round2Small() = paparazzi.shootWith("r3v9-round2", listOf(Size.FLOATB, Size.PIP), offsetMillis = 600) { ctx -> arenaOf(ctx, true, ::round2) }

    // ---------------------------------------------------------------- team XP cards

    private val cards = listOf(
        XpCard("u2", "Sprouty", 30, 9, 9, 0.30f, 0.80f, false),
        XpCard("u3", "Puddly", 40, 15, 16, 0f, 0.20f, true),
    )

    private fun loader(i: Int): (com.husarp.ankigo.gfx.SpriteView) -> Unit = { v ->
        val sp = FakeGame.data.species.values.toList().let { it[i % it.size] }
        // PanelSprites.show posts the picture to the main looper, which does not run inside a render pass: set it straight
        val file = com.husarp.ankigo.data.Sprites.cached(v.context, sp, SpriteKind.FRONT, false)
        if (file != null) v.setImage(com.husarp.ankigo.data.Sprites.decode(file))
        else v.setPlaceholder(sp.name.take(2), Ui.typeColor(sp.types.first()))
    }

    /** The card finds its place by the sprites' real drawn size, as in the app (there the pictures are in long before): load them first. */
    private fun xp(a: ArenaView) {
        loader(0)(a.playerSprite); loader(1)(a.enemySprite)
        cards.forEachIndexed { i, c -> a.showXpCard(c, loader(i)) }
    }

    @Test fun xpFull() = paparazzi.shootWith("r3v9-xp", listOf(Size.PHONE, Size.SPLIT), offsetMillis = 1100) { ctx -> arenaOf(ctx, false, ::xp) }
    @Test fun xpSmall() = paparazzi.shootWith("r3v9-xp", listOf(Size.FLOATBATTLE, Size.FLOATB, Size.PIP), offsetMillis = 1100) { ctx -> arenaOf(ctx, true, ::xp) }

    @Test fun studyXp() = paparazzi.shootWith("r3v9-xp-study", listOf(Size.SPLIT_SHORT), offsetMillis = 1100) { ctx -> studyPanel(ctx, false, ::xp) }
    @Test fun studyStripXp() = paparazzi.shootWith("r3v9-xp-strip", listOf(Size.SPLIT_TINY, Size.PIP_169M), offsetMillis = 1100) { ctx -> studyPanel(ctx, true, ::xp) }
    @Test fun studyRound() = paparazzi.shootWith("r3v9-round-study", listOf(Size.SPLIT_SHORT, Size.SPLIT_TINY), offsetMillis = 850) { ctx -> studyPanel(ctx, ctx.resources.configuration.screenHeightDp < 280, ::round) }

    /** The three card versions, static: full, slim, micro; a plain gain and a level-up each. */
    @Test fun cardsGallery() = paparazzi.shootWith("r3v9-xpcards", listOf(Size.PHONE, Size.PIP)) { ctx ->
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Ui.c(ctx, R.color.ag_arena_top), Ui.c(ctx, R.color.ag_arena_bot)))
        }
        for (kind in 0..2) for ((i, c) in cards.withIndex()) {   // full, slim, micro (no name, narrow gaps)
            val v = com.husarp.ankigo.ui.XpCardView(ctx, c, kind > 0, 0f, kind == 2)
            loader(i)(v.sprite)
            v.play(true)
            col.addView(v, LinearLayout.LayoutParams(Ui.dp(ctx, listOf(188, 160, 88)[kind]), -2).also { it.setMargins(0, Ui.dp(ctx, 5), 0, Ui.dp(ctx, 5)) })
        }
        col
    }
}
