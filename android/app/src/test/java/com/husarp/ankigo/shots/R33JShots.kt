package com.husarp.ankigo.shots

import android.graphics.BitmapFactory
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R33JShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    @After fun reset() { AppLook.setAccent(Shots.files(paparazzi.context), Accent.MAUVE); AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    @Test fun everyEvolutionKindUsesLeftMarksAndAnAccurateCount() {
        val cases = linkedMapOf(
            "level" to Evolution(135, EvoKind.LEVEL, level = 26),
            "bond-day" to Evolution(135, EvoKind.FRIENDSHIP, minFriendship = 160, time = "day"),
            "item" to Evolution(135, EvoKind.ITEM, item = "metal-alloy"),
            "night" to Evolution(135, EvoKind.ITEM, item = "razor-claw", time = "night"),
            "trade" to Evolution(135, EvoKind.TRADE),
            "gender" to Evolution(135, EvoKind.ITEM, item = "dawn-stone", gender = 'F'),
            "move" to Evolution(135, EvoKind.LEVEL_MOVE, move = "tackle"),
            "move-type" to Evolution(135, EvoKind.FRIENDSHIP, moveType = Type.FAIRY),
            "condition" to Evolution(135, EvoKind.LEVEL, level = 20, condition = "with an Atk stat equal to its Def stat"),
            "other" to Evolution(135, EvoKind.OTHER, condition = "in a special place"),
        )
        for ((name, evo) in cases) paparazzi.shoot("j-587-$name", listOf(Size.PHONE)) { ctx ->
            AppLook.setAccent(ctx, Accent.MULTICOLOR)
            val data = TestData.data(listOf(TestData.species(133, "Eevee", evolutions = listOf(evo)), TestData.species(135, "Jolteon", prevo = 133)))
            val state = TestData.state(data, 133, 20).apply { bag.clear() }
            val mon = state.main!!.apply { bondPoints = 125; gender = 'M' }
            val requirements = EvolutionRowText.requirements(data, evo, data.sp(133), state, mon)
            val rows = EvolutionRows(ctx, data, EvoLine.of(data, data.sp(133)).root, data.sp(133), setOf(133), null, state, mon) {}
            val condition = all(rows).single { it.contentDescription?.startsWith("Explain ") == true }
            val dialog = DialogShots.capture { condition.performClick() }!!
            assertNotNull(dialog.button(AppDialog.BUTTON_POSITIVE))
            assertEquals("Evolve", dialog.button(AppDialog.BUTTON_POSITIVE)!!.text.toString())
            val scene = DialogShots.scene(ctx, dialog)
            val texts = all(scene).filterIsInstance<TextView>()
            assertTrue(texts.any { it.text.toString() == "${requirements.count { it.met }}/${requirements.size} met" })
            val header = texts.single { it.text.toString() == "REQUIREMENTS" }
            assertTrue(header.compoundDrawables.all { it == null })
            assertEquals(Multicolor.ink(ctx, Multicolor.Tone.GREEN, Ui.c(ctx, R.color.ag_card)), header.currentTextColor)
            for (requirement in requirements) {
                val label = texts.single { it.text.toString() == requirement.label }
                val row = label.parent as LinearLayout
                assertEquals(2, row.childCount)
                val mark = row.getChildAt(0) as TextView
                assertEquals(if (requirement.met) "✓" else "✗", mark.text.toString())
                assertEquals(if (requirement.met) "Met" else "Not met", mark.contentDescription)
                assertEquals(if (requirement.met) Multicolor.ink(ctx, Multicolor.Tone.GREEN, Ui.c(ctx, R.color.ag_card)) else Ui.c(ctx, R.color.ag_sub1), mark.currentTextColor)
            }
            assertFalse(texts.any { it.text.startsWith("•") })
            scene
        }
    }

    @Test fun allMetAndMissingRequirementsCountWithPlainAccent() = paparazzi.shoot("j-587-all-met", listOf(Size.PHONE)) { ctx ->
        AppLook.setAccent(ctx, Accent.MAUVE)
        val data = TestData.data(listOf(TestData.species(133, "Eevee"), TestData.species(135, "Jolteon")))
        val state = TestData.state(data, 133, 30).apply { bag["fire-stone"] = 1 }
        val mon = state.main!!.apply { bondPoints = 125 }
        val evo = Evolution(135, EvoKind.ITEM, item = "fire-stone")
        val body = EvolutionRows.explanationContent(ctx, data, evo, data.sp(133), emptySet(), state, mon) {}
        val texts = all(body).filterIsInstance<TextView>()
        assertTrue(texts.any { it.text.toString() == "2/2 met" })
        assertEquals(2, texts.count { it.text.toString() == "✓" })
        val missing = EvolutionRows.explanationContent(ctx, data, evo, data.sp(133), emptySet(), null, null) {}
        assertTrue(all(missing).filterIsInstance<TextView>().any { it.text.toString() == "0/2 met" })
        body
    }

    @Test fun fullStatNamesHaveSmallCenteredLucideIcons() {
        for (width in listOf(340, 412)) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p$width", width, 869).device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val data = GameRepo.data!!
            val mon = GameRepo.state!!.main!!
            val body = DetailSections.statsBody(ctx, StatCalc.stats(data, mon), mon.level, mon.nature, data.sp(mon.species), false)
            val names = listOf("HP", "Attack", "Defense", "Special Attack", "Special Defense", "Speed")
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 24), Ui.dp(ctx, 24), 0)
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(body)
            }
            root.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, width), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Ui.dp(ctx, 869), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            for ((index, name) in names.withIndex()) {
                val label = all(body).filterIsInstance<TextView>().single { it.text.toString() == name }
                val line = label.parent as LinearLayout
                val icon = line.getChildAt(0) as ImageView
                assertEquals(Ui.dp(ctx, 14), icon.width)
                assertEquals(Ui.dp(ctx, 14), icon.height)
                assertTrue("Icon is centred with $name", kotlin.math.abs((icon.top + icon.bottom) - (label.top + label.bottom)) <= 1)
                assertEquals(Ui.c(ctx, DetailMath.statColor(Stat.entries[index])), (icon.colorFilter as PorterDuffColorFilter).color)
                assertEquals(1, label.lineCount)
                assertTrue("Full label fits at $width: $name", label.layout.getEllipsisCount(0) == 0 && label.width >= label.paint.measureText(name))
                assertTrue((line.parent as View).isClickable)
            }
            paparazzi.snapshot(root, "j-595-stats-$width-${theme.name.lowercase()}", 1000)
        }
    }

    @Test fun reachedGoalUsesExactOptionCInEveryStyleAndAccent() = paparazzi.shoot("j-589-goal-and-579-flame", listOf(Size.PHONE)) { ctx ->
        for (accent in listOf(Accent.MAUVE, Accent.MULTICOLOR)) for (style in BarStyle.entries) {
            AppLook.setAccent(ctx, accent)
            AppLook.setBarStyle(ctx, style)
            for (fraction in listOf(1f, 1.89f)) assertEquals(BarFill(0xff5ebc75.toInt()), BarTones.fill(ctx, BarMeaning.GOAL, fraction))
            val before = if (accent == Accent.MULTICOLOR) BarFill(Multicolor.color(ctx, Multicolor.Tone.ORANGE))
                else BarTones.resolve(BarMeaning.GOAL, style, BarTones.palette(ctx), .99f)
            assertEquals(before, BarTones.fill(ctx, BarMeaning.GOAL, .99f))
        }
        val still = BitmapFactory.decodeResource(ctx.resources, R.drawable.streak_flame_still)!!
        assertEquals(512, still.width)
        assertEquals(512, still.height)
        assertNotNull(StreakFlame.stillBitmap(ctx, Ui.dp(ctx, 34)))
        AppLook.setAccent(ctx, Accent.MULTICOLOR)
        AnkiStatsStore.enabled = false
        AnkiStatsStore.snapshot = StatsSnapshot()
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        GameRepo.state!!.apply {
            day = GameRepo.game!!.dayKey()
            settings.dailyAverage = 100
            reviewsToday = 189
            streakDays = 23
            bestStreakDays = 23
            lastGoalDay = day
        }
        val page = StatsPage(ctx, NoHost)
        pageScreen(ctx, page).also { call(page, "render") }
    }
}
