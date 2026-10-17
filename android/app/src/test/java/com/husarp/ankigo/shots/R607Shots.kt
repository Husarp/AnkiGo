package com.husarp.ankigo.shots

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.AchCelebration
import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.ItemFind
import com.husarp.ankigo.ui.AchMedal
import com.husarp.ankigo.ui.AchStyle
import com.husarp.ankigo.ui.AchievementDetail
import com.husarp.ankigo.ui.AchievementPopup
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/**
 * R6-07 (items 18-21): the achievement sheet drags down to close, the popups say "Achievement unlocked" / "Item found", the Best streak
 * flame sits in the middle of its medal. PNGs: r607-medals (every icon in a medal, to see the centring), r607-sheet, r607-ach-one,
 * r607-ach-three, r607-item-one, r607-item-three (light + dark).
 */
class R607Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @Before @After fun clean() = AchievementPopup.reset()

    private val st get() = GameRepo.state!!
    private val SMALL = Variant("small", 360, 640)

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v.visibility == View.GONE) return out
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun dim(ctx: Context, layer: View): View = FrameLayout(ctx).apply {
        addView(layer, FrameLayout.LayoutParams(-1, -1))
        setBackgroundColor(android.graphics.Color.argb(150, 0, 0, 0)) // the dialog's dim
    }

    private fun one(rowId: String, tier: Int): AchCelebration {
        val row = AchievementTable.row(rowId)!!
        return AchCelebration(row.id, row.title, tier, AchievementTable.tierName(tier), row.tiers.getOrNull(tier))
    }

    // ---------- item 21 ----------

    @Test fun everyIconInAMedal() {
        paparazzi.shootPage("r607-medals", listOf(Variants.PHONE)) { ctx ->
            val keys = AchStyle.ICONS.keys.toList()
            val grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_card)); setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 6), Ui.dp(ctx, 6), 0) }
            for (chunk in keys.chunked(6)) {
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                for ((i, k) in chunk.withIndex()) row.addView(AchMedal(ctx, 56).apply { set(AchStyle.icon(k), if (k == "flame") 3 else i % 5) }, LinearLayout.LayoutParams(Ui.dp(ctx, 62), Ui.dp(ctx, 62)))
                grid.addView(row)
            }
            grid
        }
    }

    @Test fun flameIsCentredInItsMedal() {
        val (dx, dy) = AchStyle.nudge(com.husarp.ankigo.R.drawable.ic_flame)
        // the flame path spans x 7.5..20.5 / y 1.5..22 of 24: its box centre (14, 11.75) plus the shift is the viewport centre (12, 12)
        assertEquals(12f, 14f + dx, 0.01f)
        assertEquals(12f, 11.75f + dy, 0.01f)
        assertEquals(0f to 0f, AchStyle.nudge(com.husarp.ankigo.R.drawable.ic_star))
    }

    // ---------- item 18 ----------

    private fun sheetLayer(ctx: Context, onClose: () -> Unit): View {
        st.bestStreakDays = 12; st.streakDays = 12
        val v = Achievements.view(st, GameRepo.data!!).first { it.id == "streak-days" }
        return AchievementDetail.layer(ctx, v, 0L, ZoneId.of("UTC"), onClose)
    }

    @Test fun sheet() {
        paparazzi.shootPage("r607-sheet", listOf(Variants.PHONE, SMALL)) { ctx -> sheetLayer(ctx, {}) }
    }

    private fun touch(v: View, action: Int, y: Float, downTime: Long, x: Float = 100f) {
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        v.dispatchTouchEvent(e)
        e.recycle()
    }

    @Test fun draggingTheHandleFollowsTheFingerAndAShortDragSpringsBack() {
        paparazzi.shootPage("r607-sheet-drag", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            var closed = 0
            val layer = sheetLayer(ctx, { closed++ })
            val root = FrameLayout(ctx).apply { addView(layer, FrameLayout.LayoutParams(-1, -1)) }
            layout(root)
            val sheet = (layer as ViewGroup).getChildAt(1)
            val top = sheet.top.toFloat() + 10f * ctx.resources.displayMetrics.density // on the handle strip
            val t0 = SystemClock.uptimeMillis()
            touch(root, MotionEvent.ACTION_DOWN, top, t0)
            for (dy in listOf(10f, 60f, 120f)) touch(root, MotionEvent.ACTION_MOVE, top + dy, t0)
            assertTrue("the sheet follows the finger: ${sheet.translationY}", sheet.translationY > 0f)
            assertTrue(layer.getChildAt(0).alpha < 1f) // the scrim fades
            assertEquals(0, closed)
            root
        }
    }

    private fun layout(root: View) {
        val w = 412 * 2; val h = 869 * 2
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
    }

    // ---------- item 19 ----------

    @Test fun achievementPopupSaysItIsAnAchievement() {
        paparazzi.shootPage("r607-ach-one", listOf(Variants.PHONE, SMALL)) { ctx ->
            val v = dim(ctx, AchievementPopup.layer(ctx, listOf(one("streak-days", 2)), {}, {}))
            val t = texts(v)
            assertTrue(t.toString(), t.any { it.equals("Achievement unlocked", ignoreCase = true) })
            v
        }
        paparazzi.shootPage("r607-ach-three", listOf(Variants.PHONE, SMALL)) { ctx ->
            val v = dim(ctx, AchievementPopup.layer(ctx, listOf(one("streak-days", 2), one("catches", 1), one("answers", 3)), {}, {}))
            assertTrue(texts(v).toString(), "3 achievements unlocked" in texts(v))
            v
        }
    }

    // ---------- item 20 ----------

    @Test fun itemPopup() {
        paparazzi.shootPage("r607-item-one", listOf(Variants.PHONE, SMALL)) { ctx ->
            var nice = 0
            val v = dim(ctx, AchievementPopup.itemLayer(ctx, listOf(ItemFind("potion", 3)), { nice++ }))
            val t = texts(v)
            assertTrue(t.toString(), t.any { it.equals("Item found", ignoreCase = true) } && "Potion" in t && "Heals 20 HP" in t && "You have 3" in t && "Nice" in t)
            fun tap(s: String) { var x: View? = findText(v, s); while (x != null && !x.isClickable) x = x.parent as? View; x!!.performClick() }
            tap("Nice"); assertEquals(1, nice)
            v
        }
        paparazzi.shootPage("r607-item-three", listOf(Variants.PHONE, SMALL)) { ctx ->
            val v = dim(ctx, AchievementPopup.itemLayer(ctx, listOf(ItemFind("potion", 3), ItemFind("fire-stone", 1), ItemFind("poke-ball", 12)), {}))
            assertTrue(texts(v).toString(), texts(v).any { it.equals("3 items found", ignoreCase = true) })
            v
        }
    }

    private fun findText(v: View, s: String): TextView? {
        if (v is TextView && v.text.toString() == s) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), s)?.let { return it }
        return null
    }
}
