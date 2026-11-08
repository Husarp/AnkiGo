package com.husarp.ankigo.shots

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.ZoneId

class R31NShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    private fun nodes(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { nodes(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = nodes(v).filterIsInstance<TextView>()
    private fun measure(v: View, width: Int, height: Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }
    private fun fits(v: View) {
        val labels = texts(v)
        assertEquals("One size throughout Bond body", 1, labels.map { it.textSize }.distinct().size)
        labels.forEach {
            assertEquals("${it.text}: one line", 1, it.layout.lineCount)
            assertEquals("${it.text}: no ellipsis", 0, it.layout.getEllipsisCount(0))
            assertTrue("${it.text}: width", it.layout.getLineWidth(0) <= it.width - it.compoundPaddingLeft - it.compoundPaddingRight + 1)
            assertTrue("${it.text}: height", it.layout.height <= it.height - it.compoundPaddingTop - it.compoundPaddingBottom)
        }
    }
    private fun memory() = DetailOpen(object : DetailOpenStore {
        override fun get(key: String) = true
        override fun set(key: String, open: Boolean) {}
    })
    private fun view(row: AchRow, earned: Boolean = true, pokemon: List<AchPokemon> = listOf(AchPokemon("p", "Pikachu", 25, false))) = AchView(row.id, row.group, row.title, row.desc, row.icon,
        row.oneOff, row.ankimonBadge, if (earned) 0 else -1, "Common", if (earned) listOf(1_793_577_600_000L) else emptyList(),
        if (earned) row.tiers.first() else 0, row.tiers.getOrNull(1), .25f, row.tiers,
        pokemon = pokemon)

    @Test fun bondAndAchievementScreensAtBothWidthsThemesAndLargestFont() {
        for (width in listOf(360, 412)) for (theme in Theme.entries) for (font in listOf(1f, 2f)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("w$width", width, 1100).device(theme).copy(fontScale = font))
            val ctx = Shots.files(paparazzi.context)
            val mon = TestData.state(TestData.world()).main!!.apply { bondPoints = 370 }
            val body = BondSection.body(ctx, mon)
            val card = DetailSections.section(ctx, memory(), DetailSection.BOND, BondSection.summary(mon)) { body }
            val scene = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 12), Ui.dp(ctx, 20), Ui.dp(ctx, 12))
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(card)
            }
            val suffix = "w$width-font${font.toInt()}-${theme.name.lowercase()}"
            paparazzi.snapshot(scene, "n-508-$suffix")
            fits(body)
            val words = texts(body).map { it.text.toString() }
            for (word in listOf("Level 3", "All species matchups revealed", "+3 coins", "+15% XP", "+4 coins", "+20% XP", "5% chance to survive a KO with 1 HP", "Higher Bond evolutions")) assertTrue(word, word in words)
            val bar = nodes(body).filterIsInstance<BarView>().single()
            val column = bar.parent as LinearLayout
            assertEquals(Ui.dp(ctx, 28), (column.getChildAt(3).layoutParams as LinearLayout.LayoutParams).topMargin)
            // Re-measuring the same section must grow text again when space becomes available.
            measure(body, Ui.dp(ctx, 220), Ui.dp(ctx, 1000)); fits(body)
            val small = texts(body).first().textSize
            measure(body, Ui.dp(ctx, 500), Ui.dp(ctx, 1000)); fits(body)
            assertTrue(texts(body).first().textSize > small)

            FakeGame.withSprites = false; FakeGame.install(ctx)
            File("/tmp/r31k-sprites/25.png").copyTo(File(Sprites.dir(ctx), "static/25.png").also { it.parentFile!!.mkdirs() }, overwrite = true)
            Thread { Sprites.staticBitmap(ctx, 25, false) }.apply { start(); join() }
            assertNotNull(Sprites.staticBitmap(ctx, 25, false))
            for (id in listOf("first-catch", "defeats")) {
                val sheet = AchievementDetail.sheet(ctx, view(AchievementTable.row(id)!!), 0, ZoneId.of("UTC"))
                val frame = FrameLayout(ctx).apply {
                    setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                    addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
                }
                paparazzi.snapshot(frame, "n-539-$id-$suffix")
                assertEquals(id == "first-catch", texts(sheet).any { it.text == "Earned with Pikachu" })
                if (id == "first-catch") {
                    val content = nodes(sheet).filterIsInstance<ScrollView>().single().getChildAt(0) as LinearLayout
                    assertTrue(texts(content.getChildAt(content.childCount - 1)).any { it.text == "Earned with Pikachu" })
                }
            }
        }
    }

    @Test fun everyBondLevelFitsAndOnlyRealNextLevelsAppear() {
        val ctx = Shots.files(paparazzi.context)
        for (width in listOf(220, 280, 360)) for (level in 0..5) {
            val mon = TestData.state(TestData.world()).main!!.apply { bondPoints = Bond.thresholds[level] }
            val body = BondSection.body(ctx, mon)
            measure(body, Ui.dp(ctx, width), Ui.dp(ctx, 1000)); fits(body)
            val words = texts(body).map { it.text.toString() }
            assertEquals(level < 5, words.any { it.startsWith("NEXT") })
            assertFalse(words.any { it.contains("Level 6") || it.contains("NEXT · 6") })
            assertEquals(if (level >= 3) if (level < 5) 2 else 1 else if (level == 2) 1 else 0, words.count { it == "All species matchups revealed" })
            if (level == 0) assertTrue("No perks yet" in words)
            if (level == 5) assertTrue("10% chance to survive a KO with 1 HP" in words)
        }
    }

    @Test fun onlyProposedOneTimeAchievementsHaveAFooter() {
        val ctx = Shots.files(paparazzi.context)
        val eligible = setOf("first-catch", "first-normal", "first-baby", "first-ultra", "first-legendary", "first-mythical",
            "first-level-up", "first-evolution", "first-fossil", "first-heal", "one-shot", "last-breath", "perfect-throw",
            "david-goliath", "deja-vu", "bully", "glow-up")
        for (row in AchievementTable.ROWS) {
            val v = view(row)
            assertEquals(row.id, row.id in eligible, AchievementPokemon.applies(v))
            for (earned in listOf(false, true)) {
                val sheet = AchievementDetail.sheet(ctx, view(row, earned), 0, ZoneId.of("UTC"))
                assertEquals("${row.id}, earned=$earned", earned && row.id in eligible,
                    texts(sheet).any { it.text.startsWith("Earned with") })
            }
        }
        val old = view(AchievementTable.row("first-catch")!!, pokemon = emptyList())
        assertTrue(texts(AchievementDetail.sheet(ctx, old, 0, ZoneId.of("UTC"))).any { it.text.contains("Pokémon not recorded") })
    }
}
