package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.FloatFrame
import com.husarp.ankigo.ui.FloatKind
import com.husarp.ankigo.ui.FloatText
import com.husarp.ankigo.ui.FloatTextView
import com.husarp.ankigo.ui.FloatTexts
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R19-I (PLAN 244B): hit texts above the target's head. PNGs r19i-<where>-<n>-<theme>: the Battle tab, the Study strip (normal and
 * short) with 1 and 3 stacked texts on each side; r19i-frames-<theme>: a close-up of every kind's frame (twice the size).
 * Also checks the real ink keeps >= 1.5 dp of room in its frame for every kind, at font scale 1 and 2.
 */
class R19IHitTextShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private fun texts(n: Int, mine: Boolean): List<FloatText> = if (n == 1) {
        listOf(if (mine) FloatText(true, "Super effective!", FloatKind.SUPER) else FloatText(false, "Not very effective", FloatKind.NOT_VERY))
    } else if (mine) {
        listOf(FloatText(true, FloatTexts.loss(12), FloatKind.BIG_DAMAGE), FloatText(true, "Super effective!", FloatKind.SUPER), FloatText(true, "Critical!", FloatKind.CRIT))
    } else {
        listOf(FloatText(false, FloatTexts.loss(4), FloatKind.DAMAGE), FloatText(false, "Not very effective", FloatKind.NOT_VERY),
            FloatText(false, "Attack down 2", FloatKind.STAT_DOWN, "ATK", 2))
    }

    private fun arenaShot(name: String, size: Size, n: Int, build: (Context) -> Pair<View, ArenaView>) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
            val (view, arena) = build(ctx)
            val frame = FrameLayout(ctx).apply { addView(view) }
            paparazzi.snapshot(frame, "zz-pre-$name", 0)          // lays it out, loads the pictures
            Thread.sleep(400)
            paparazzi.snapshot(frame, "zz-pre2-$name", 50)
            for (side in listOf(true, false)) for (ft in texts(n, side)) arena.floatText(ft)
            paparazzi.snapshot(frame, "$name-$n-${theme.name.lowercase()}", 100)
        }
    }

    private fun strip(ctx: Context, hDp: Int): Pair<View, ArenaView> {
        val p = BattlePanelView(ctx)
        p.arena.setStudyStrip(true)
        p.renderAll()
        p.layoutParams = FrameLayout.LayoutParams(-1, Ui.dp(ctx, hDp), Gravity.TOP)
        return p to p.arena
    }

    @Test fun battleTab1() = arenaShot("r19i-battle", Size.PHONE, 1) { ctx -> BattleShotKit.bound(ctx).let { it.root to it.arena!! } }
    @Test fun battleTab3() = arenaShot("r19i-battle", Size.PHONE, 3) { ctx -> BattleShotKit.bound(ctx).let { it.root to it.arena!! } }
    @Test fun studyNormal1() = arenaShot("r19i-study", Size.SPLIT, 1) { ctx -> strip(ctx, 230) }
    @Test fun studyNormal3() = arenaShot("r19i-study", Size.SPLIT, 3) { ctx -> strip(ctx, 230) }
    @Test fun studyShort1() = arenaShot("r19i-short", Size.SPLIT, 1) { ctx -> strip(ctx, 130) }
    @Test fun studyShort3() = arenaShot("r19i-short", Size.SPLIT, 3) { ctx -> strip(ctx, 130) }

    // ---- close-up of every kind's frame

    private fun samples(ctx: Context): List<FloatTextView> {
        fun tv(k: FloatKind, t: String, stat: String? = null, stages: Int = 0) = FloatTextView(ctx, FloatText(true, t, k, stat, stages), 0f)
        val out = ArrayList<FloatTextView>()
        out += tv(FloatKind.SUPER, "Super effective!")
        out += tv(FloatKind.NOT_VERY, "Not very effective")
        out += tv(FloatKind.NO_EFFECT, "No effect")
        out += tv(FloatKind.CRIT, "Critical!")
        out += tv(FloatKind.MISS, "Miss")
        out += tv(FloatKind.DAMAGE, FloatTexts.loss(12)); out += tv(FloatKind.BIG_DAMAGE, FloatTexts.loss(128)); out += tv(FloatKind.HEAL, FloatTexts.heal(128))
        out += tv(FloatKind.STATUS_DAMAGE, "${FloatTexts.loss(4)} Burning", "brn")
        out += tv(FloatKind.STATUS, "Badly poisoned"); out += tv(FloatKind.STATUS, "Fully paralyzed", "par"); out += tv(FloatKind.STATUS, "Fast asleep", "slp")
        out += tv(FloatKind.STATUS, "Frozen solid", "frz"); out += tv(FloatKind.HESITATE, "Hesitated"); out += tv(FloatKind.INFO, "Swapped for Lv 100")
        for (up in listOf(true, false)) for (st in listOf("ATK", "SPA", "ACC", "EVA")) for (n in 1..3)
            out += tv(if (up) FloatKind.STAT_UP else FloatKind.STAT_DOWN, "$st ${if (up) "up" else "down"} $n", st, n)
        return out
    }

    private fun gallery(ctx: Context, part: Int): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6)) }
        val all = samples(ctx).filter { (it.ft.kind == FloatKind.STAT_UP || it.ft.kind == FloatKind.STAT_DOWN) == (part == 2) }
        var row: LinearLayout? = null
        var w = 0
        val limit = Ui.dp(ctx, 190)
        for (v in all) {
            v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            if (row == null || w + v.measuredWidth > limit) { row = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }; col.addView(row); w = 0 }
            row.addView(v, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(Ui.dp(ctx, 2), Ui.dp(ctx, 1), Ui.dp(ctx, 2), Ui.dp(ctx, 1)) })
            w += v.measuredWidth + Ui.dp(ctx, 4)
        }
        col.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF6F86B8.toInt(), 0xFF7E9C74.toInt()))
        col.scaleX = 2f; col.scaleY = 2f; col.pivotX = 0f; col.pivotY = 0f
        return FrameLayout(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_base)); addView(col, FrameLayout.LayoutParams(Ui.dp(ctx, 200), -2)) }
    }

    @Test fun frames() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            for (part in 1..2) paparazzi.snapshot(gallery(ctx, part), "r19i-frames$part-${theme.name.lowercase()}", 0)
        }
    }

    @Test fun everyKindKeepsRoomForItsLetters() {
        for (scale in listOf(1.0f, 2.0f)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT).copy(fontScale = scale))
            val ctx = Shots.files(paparazzi.context)
            for (v in samples(ctx)) {
                val room = v.frameRoomDp()
                assertTrue("${v.ft.kind} '${v.ft.text}' at font scale $scale: ${room} dp", room >= FloatFrame.ROOM_DP - 0.05f)
            }
        }
    }
}
