package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.StudyStats
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.ui.DetailSections
import com.husarp.ankigo.ui.IconLabel
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.StatsPage
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/*
 * TICKET R10-W2-D: the Stats streak card's "Best" (D5-A, trophy + "New best!" on a record) and the details header tags (D7-A: icon +
 * grey name, no background). PNGs: r10w2d-streak-<cur>of<best>-<font>-<theme>, r10w2d-tags-<what>-<size>-<theme>.
 */
class R10W2StreakTagsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val F340 = Variant("float", 340, 480)
    private val F320 = Variant("w320", 320, 480)
    private val gd get() = GameRepo.data!!
    private val st get() = GameRepo.state!!

    private class ActivityHost(val activity: Activity) : PageHost {
        override val hostActivity: Activity get() = activity
        override fun closePage() = Unit
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
    }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out)
        return out
    }

    private fun labels(v: View) = all(v).filterIsInstance<IconLabel>()

    /** The icon's centre is on the capitals' centre of its text, within 1 px (the check is in pixels of the render). */
    private fun assertCentred(l: IconLabel) {
        val (icon, text) = l.centres()
        assertTrue("icon centre $icon vs text centre $text (${l.label.text})", Math.abs(icon - text) <= 1f)
    }

    // ---------- the streak card ----------

    private fun streakCard(ctx: Context, cur: Int, best: Int): View {
        val today = LocalDate.parse(GameRepo.game!!.dayKey())
        st.day = today.toString()
        st.streakDays = cur; st.bestStreakDays = best
        st.lastGoalDay = if (cur > 0) today.minusDays(1).toString() else today.minusDays(9).toString()
        val page = StatsPage(ctx, ActivityHost(attach(Activity(), ctx)))
        val s = StudyStats.summary(st, today.toString(), 18)
        assertEquals(cur, s.streak.current)
        val m = StatsPage::class.java.getDeclaredMethod("streakCard", StudyStats.Summary::class.java).apply { isAccessible = true }
        val card = m.invoke(page, s) as View
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14))
            addView(card, LinearLayout.LayoutParams(-1, -2))
        }
    }

    @Test fun streak() {
        for (scale in listOf(1f, 2f)) for ((cur, best) in listOf(2 to 3, 3 to 3, 0 to 0)) {
            for (theme in Theme.entries) {
                paparazzi.unsafeUpdateConfig(deviceConfig = Variants.PHONE.device(theme).copy(fontScale = scale))
                val ctx = Shots.files(paparazzi.context)
                FakeGame.install(ctx)
                val root = streakCard(ctx, cur, best)
                val l = labels(root).single()
                val text = l.label.text.toString()
                if (cur > 0 && cur >= best) assertEquals("New best!", text) else assertEquals("Best $best", text)
                paparazzi.snapshot(FrameLayout(ctx).apply { addView(root, FrameLayout.LayoutParams(-1, -2)) },
                    "r10w2d-streak-${cur}of$best-x${scale.toString().replace('.', '_')}-${theme.name.lowercase()}")
                // measured after the snapshot's layout pass
                assertCentred(l)
            }
        }
    }

    // ---------- the details header tags ----------

    private fun shoot(name: String, variants: List<Variant>, scale: Float, prepare: () -> com.husarp.ankigo.game.Mon, expect: List<String>) {
        for (v in variants) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = v.device(theme).copy(fontScale = scale))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            DetailSections.storeOf = { object : com.husarp.ankigo.ui.DetailOpenStore {
                override fun get(key: String): Boolean? = false
                override fun set(key: String, open: Boolean) = Unit
            } }
            val mon = prepare()
            val d = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), gd, mon) } ?: error("no dialog was shown")
            val scene = DialogShots.scene(ctx, d)
            val frame = FrameLayout(ctx).apply { addView(scene, FrameLayout.LayoutParams(-1, -1)) }
            val label = if (scale == 1f) v.label else "${v.label}-x${scale.toString().replace('.', '_')}"
            paparazzi.snapshot(frame, "r10w2d-tags-$name-$label-${theme.name.lowercase()}", 1000)
            val tags = labels(scene)
            assertEquals(expect, tags.map { it.label.text.toString() })
            tags.forEach { assertCentred(it) }
            // no background on a tag; the TalkBack text lists them
            tags.forEach { assertTrue(it.background == null) }
            val flow = tags.first().parent as View
            assertEquals(tags.joinToString(", ") { it.contentDescription.toString() }, flow.contentDescription.toString())
        }
    }

    private fun mainTeamShare(): com.husarp.ankigo.game.Mon {
        val m = st.mon("t1")!!          // main, in the team, holds a Lucky Egg
        st.settings.xpShareUid = m.uid
        return m
    }

    private fun teamFavEvolveShiny(): com.husarp.ankigo.game.Mon {
        val m = TestData.mon(gd, 25, 30, listOf("tackle", "thunderbolt"), "x9")   // thunder stone in the bag: can evolve
        m.favourite = true; m.shiny = true
        st.mons += m; st.team += m.uid
        return m
    }

    @Test fun tagsMainTeamShareItem() {
        shoot("main", listOf(Variants.PHONE, F340, F320), 1f, ::mainTeamShare, listOf("Main", "Team", "Exp. Share", "Can evolve", "Lucky Egg"))
        shoot("main", listOf(F320), 2f, ::mainTeamShare, listOf("Main", "Team", "Exp. Share", "Can evolve", "Lucky Egg"))
    }

    @Test fun tagsTeamFavouriteEvolveShiny() {
        val want = listOf("Team", "Favourite", "Can evolve", "Shiny")
        shoot("fav", listOf(Variants.PHONE, F340, F320), 1f, ::teamFavEvolveShiny, want)
        shoot("fav", listOf(F320), 2f, ::teamFavEvolveShiny, want)
    }
}
