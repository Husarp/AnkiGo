package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.AchCelebration
import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.GiftPop
import com.husarp.ankigo.ui.AchievementDetail
import com.husarp.ankigo.ui.AchievementPopup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/**
 * R14-B2-D: the "Daily gift" popup (an item, a single item on day one, a TM, a long streak) and the Master Ball reward (the Best streak
 * sheet's top tier row, the popup of that tier). PNGs: r14b2d-gift-item, r14b2d-gift-day1, r14b2d-gift-tm, r14b2d-gift-long,
 * r14b2d-sheet-open (streak 100: the reward is ahead), r14b2d-sheet-done (365 reached), r14b2d-ach-master (light + dark).
 */
class R14B2DShots {
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

    private fun gift(ctx: Context, g: GiftPop, onNice: () -> Unit = {}): View = dim(ctx, AchievementPopup.giftLayer(ctx, GameRepo.data, g, onNice))

    @Test fun giftOfSeveralBalls() {
        paparazzi.shootPage("r14b2d-gift-item", listOf(Variants.PHONE, SMALL)) { ctx ->
            var nice = 0
            val v = gift(ctx, GiftPop("poke-ball", null, 3, 6)) { nice++ }
            val t = texts(v)
            assertTrue(t.toString(), t.any { it.equals("Daily gift", ignoreCase = true) } && "3 Poké Balls" in t && "Nice" in t)
            assertTrue(t.toString(), "Goal streak: 6 days · rarer gifts as it grows, up to 30 days" in t)
            assertTrue(t.toString(), t.any { it.contains("catch", ignoreCase = true) }) // the item's own line
            fun tap(s: String) { var x: View? = findText(v, s); while (x != null && !x.isClickable) x = x.parent as? View; x!!.performClick() }
            tap("Nice"); assertEquals(1, nice)
            v
        }
    }

    @Test fun giftOfOneOnDayOne() {
        paparazzi.shootPage("r14b2d-gift-day1", listOf(Variants.PHONE)) { ctx ->
            val v = gift(ctx, GiftPop("great-ball", null, 1, 1))
            val t = texts(v)
            assertTrue(t.toString(), "Great Ball" in t && "Reach your goal tomorrow too for rarer gifts" in t)
            assertFalse("a single one is just its name", t.any { it.startsWith("1 ") })
            v
        }
    }

    @Test fun giftOfATm() {
        paparazzi.shootPage("r14b2d-gift-tm", listOf(Variants.PHONE, SMALL)) { ctx ->
            val v = gift(ctx, GiftPop(null, "thunderbolt", 1, 12))
            val t = texts(v)
            assertTrue(t.toString(), "TM Thunderbolt" in t && t.any { it.equals("Daily gift", ignoreCase = true) } && "Goal streak: 12 days · rarer gifts as it grows, up to 30 days" in t)
            v
        }
    }

    @Test fun giftOnALongStreak() {
        paparazzi.shootPage("r14b2d-gift-long", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val v = gift(ctx, GiftPop("ultra-ball", null, 1, 45))
            assertTrue(texts(v).toString(), texts(v).any { it.startsWith("Goal streak: 45 days") })
            v
        }
    }

    private fun sheet(ctx: Context, streak: Int): View {
        st.bestStreakDays = streak; st.streakDays = streak
        val v = Achievements.view(st, GameRepo.data!!).first { it.id == "streak-days" }
        return AchievementDetail.layer(ctx, v, 0L, ZoneId.of("UTC")) {}
    }

    @Test fun theSheetSaysWhatTheTopTierGives() {
        paparazzi.shootPage("r14b2d-sheet-open", listOf(Variants.PHONE, SMALL)) { ctx ->
            val v = sheet(ctx, 100)
            val t = texts(v)
            assertEquals(t.toString(), 1, t.count { it == "Reward: Master Ball" })
            assertTrue(t.toString(), t.any { it.contains("top tier: a Master Ball") })
            v
        }
        paparazzi.shootPage("r14b2d-sheet-done", listOf(Variants.PHONE)) { ctx ->
            val v = sheet(ctx, 400)
            assertEquals(texts(v).toString(), 1, texts(v).count { it == "Reward: Master Ball" })
            v
        }
    }

    @Test fun theTopTierPopupSaysTheMasterBallIsInTheBag() {
        val row = AchievementTable.row("streak-days")!!
        paparazzi.shootPage("r14b2d-ach-master", listOf(Variants.PHONE, SMALL)) { ctx ->
            val c = AchCelebration(row.id, row.title, 4, AchievementTable.tierName(4), row.tiers[4])
            val v = dim(ctx, AchievementPopup.layer(ctx, listOf(c), {}, {}))
            assertTrue(texts(v).toString(), "Reward: a Master Ball, in your Bag" in texts(v))
            v
        }
        // a tier without a reward adds nothing
        paparazzi.shootPage("r14b2d-ach-plain", listOf(Variants.PHONE), themes = listOf(Theme.LIGHT)) { ctx ->
            val c = AchCelebration(row.id, row.title, 3, AchievementTable.tierName(3), row.tiers[3])
            val v = dim(ctx, AchievementPopup.layer(ctx, listOf(c), {}, {}))
            assertFalse(texts(v).toString(), texts(v).any { it.startsWith("Reward") })
            v
        }
    }

    private fun findText(v: View, s: String): TextView? {
        if (v is TextView && v.text.toString() == s) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), s)?.let { return it }
        return null
    }
}
