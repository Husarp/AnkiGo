package com.husarp.ankigo.shots

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.*
import com.husarp.ankigo.game.data.Parsers
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Captures the selected designs using production views and real sprites. */
class R31MShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    private val data by lazy {
        val dir = File(System.getenv("ANKIGO_REAL_DATA") ?: "/tmp/r17c-data2")
        fun text(name: String) = File(dir, name).readText()
        Parsers.build(Parsers.Sources(text("pokedex.json"), text("moves.json"), text("learnsets.json"),
            text("pokemon_species.csv"), text("pokemon.csv"), text("pokemon_stats.csv"),
            text("pokemon_evolution.csv"), text("experience.csv"), text("items.csv")))
    }
    private val scenes = mutableListOf<BattleShotKit.Built>()
    @After fun clean() {
        scenes.forEach { it.player?.clear() }
        scenes.clear(); BattleShotKit.reset(); Pickers.interceptApp = null
    }
    private fun context(theme: Theme, height: Int = 820): Context {
        // Keep a portrait canvas even for short section crops; layoutlib otherwise swaps width and height.
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme).copy(screenHeight = height.coerceAtLeast(400) * 2))
        val a = Shots.files(paparazzi.context)
        assertEquals("Every design capture is 360 dp wide", 720, a.resources.displayMetrics.widthPixels)
        FakeGame.withSprites = false; FakeGame.install(a)
        val source = File(System.getenv("ANKIGO_R31_SPRITES") ?: "/tmp/ankigo-r31c-sprites")
        for (name in listOf("static/1.png", "static/2.png", "static/3.png", "front/54.png", "back/25.png"))
            require(File(source, name).length() > 500) { "Supply real sprite $source/$name" }
        source.copyRecursively(Sprites.dir(a), overwrite = true)
        // Use the real species, moves and rules as well as the real app views and art.
        BattleShotKit.gameRepoSet("loadedData", data)
        BattleShotKit.gameRepoSet("held", FakeGame.state(data))
        GameRepo::class.java.getDeclaredMethod("rebuild").apply { isAccessible = true }.invoke(GameRepo)
        a.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        return a
    }
    private fun stage(a: Context, body: View): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(a, R.color.ag_base))
        setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8))
        addView(body, LinearLayout.LayoutParams(-1, -2))
    }
    private fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun mon() = Mon("r31-bold", 1, data.sp(1).name, 26, 0, Stats(20, 20, 20, 20, 20, 20), Stats.ZERO,
        Nature.BOLD, 'M', false, "overgrow", mutableListOf("tackle", "growl"), 70, bondPoints = 360)
    private fun shot(a: Context, body: View, name: String) {
        val scene = stage(a, body)
        paparazzi.snapshot(scene, name, 300)
        assertTrue("$name: full section fits in capture", body.bottom + scene.paddingBottom <= scene.height)
    }

    private fun all(v: View): List<View> = listOf(v) + if (v is android.view.ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun section(a: Context, kind: DetailSection, body: () -> View): View =
        DetailSections.section(a, DetailSections.memory(a), kind, null, forceOpen = true, body = body)

    @Test fun evolutionRequirementsAlwaysHaveGreenChevronsAndShortText() {
        for (theme in Theme.entries) {
            val a = context(theme, 420)
            val m = mon().apply { bondPoints = 0 }
            val state = GameRepo.state!!; state.mons += m; state.caught += listOf(1, 2, 3)
            val rows = EvolutionRows(a, data, EvoLine.of(data, data.sp(1)).root, data.sp(1), data.species.keys, null, state, m)
            val view = section(a, DetailSection.EVOLUTION) { rows }
            assertEquals(2, all(rows).count { it.contentDescription == "Evolution requirements" })
            assertFalse(texts(view).any { it.contains("Needs") })
            assertTrue(texts(view).any { it.contains("Level 16") && it.contains("Level 2 Bond") })
            shot(a, view, "m-506-${theme.name.lowercase()}")
        }
    }
    @Test fun natureStatsAndTrainingUseTheSameSemanticChanges() {
        for (theme in Theme.entries) {
            val a = context(theme, 760); val m = mon()
            val view = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(section(a, DetailSection.STATS) {
                    DetailSections.statsBody(a, StatCalc.stats(data, m), m.level, m.nature, data.sp(m.species), false)
                })
                addView(section(a, DetailSection.TRAINING) { DetailSections.training(a, m, false) })
            }
            assertEquals(2, all(view).count { it.contentDescription == "Def +10%" })
            assertEquals(2, all(view).count { it.contentDescription == "Atk −10%" })
            assertFalse(texts(view).any { it.contains("▲") || it.contains("▼") })
            shot(a, view, "m-509-${theme.name.lowercase()}")
            for (nature in Nature.entries) {
                val changes = NatureChanges.line(a, nature)
                val descriptions = all(changes).mapNotNull { it.contentDescription?.toString() }
                assertEquals(if (nature.plus == null) 0 else 2, descriptions.size)
                if (nature.plus == null) assertTrue(texts(changes).single().contains("Neutral"))
            }
        }
    }
    @Test fun battleChipsOnRealArenaWithAllStageCounts() {
        for (theme in Theme.entries) {
            val a = context(theme, 640); val b = GameRepo.state!!.battle!!
            b.player.boosts[1] = 1; b.player.volatiles["ingrain"] = 1
            b.enemy.boosts[1] = 2; b.enemy.boosts[0] = -2
            val built = BattleShotKit.bound(a); scenes += built
            paparazzi.snapshot(built.root, "m-511-${theme.name.lowercase()}", 300)
            val chips = built.arena!!.markRows().flatMap { it.chips }.mapNotNull { it.mark }
            assertTrue(chips.any { it.key == "ingrain" })
            assertTrue(chips.any { it.key == "stat.def" && it.value == 1 })
            assertTrue(chips.any { it.key == "stat.def" && it.value == 2 })
            assertTrue(chips.any { it.key == "stat.atk" && it.value == -2 })
            assertEquals(Ui.dp(a, 4).toFloat(), ChipMetrics(a, 12f).radius)
            val samples = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                for (n in 1..6) addView(LinearLayout(a).apply {
                    for (up in listOf(true, false)) addView(object : View(a) {
                        override fun onDraw(c: android.graphics.Canvas) = StageChevron.draw(c,
                            android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), n, up, StatPill.ink(up, StatPill.isDark(a)))
                    }, LinearLayout.LayoutParams(Ui.dp(a, if (n > 3) 32 else 18), Ui.dp(a, 24)))
                })
            }
            shot(a, samples, "m-511-stages-${theme.name.lowercase()}")
        }
    }
    @Test fun selectedSettingsAndItemHeadingIconsRender() {
        for (theme in Theme.entries) {
            val titles = listOf("Play", "Sound & data", "Help & app", "Advanced", "When you beat a wild Pokémon", "Always catch", "Difficulty", "Pokémon",
                "Theme & colours", "Types & notifications", "Text & size", "Box", "Pokémon details", "Study cards & decks", "Study & accessibility", "Timing", "Answer controls", "Card pictures & sound",
                "When you leave AnkiGo", "Pop out opens", "Battle panel", "Battle look", "Small battle windows", "Floating screens", "Storage used", "Downloads & cache", "Pokémon size", "Panel height", "Facing",
                "Info boxes & bars", "Arena & layout", "Bright scenes", "Dark scenes", "Colours & background", "Buddy layout & movement", "Study & game reminders", "Time", "Remind every", "Remind at", "From", "To",
                "Remind when at least", "Count", "At most", "Not more often than", "Decks", "Goal", "Quiet hours", "Fix a problem", "Playback & test")
            for ((batch, group) in titles.chunked(10).withIndex()) {
                val a = context(theme, 700)
                val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
                for (title in group) {
                    assertNotEquals(title, R.drawable.ic_star, Form.sectionIcon(title))
                    col.addView(Form.sect(a, title))
                }
                shot(a, col, "m-507-settings-$batch-${theme.name.lowercase()}")
            }
            val a = context(theme, 450)
            val col = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                for (title in listOf("Requirements", "Where to find", "What it does")) addView(ItemCard.factBlock(a, title, "Example details"))
            }
            shot(a, col, "m-507-item-${theme.name.lowercase()}")
        }
    }
    @Test fun selectedAchievementBadgeAndActionIconsRender() {
        for (theme in Theme.entries) {
            val a = context(theme, 600)
            val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            val choices = listOf(
                "Days studied" to AchStyle.icon("days"), "XP earned" to AchStyle.icon("xp"),
                "Ultra Beast" to AchStyle.icon("badge-ultra"), "Item badge" to AchStyle.icon("badge-item"),
                "Give / Take item" to R.drawable.ic_item_tag, "Keep this setup" to R.drawable.ic_bookmark,
                "Help Types" to com.husarp.ankigo.help.HelpViews.chapterIcon(com.husarp.ankigo.help.GameGuide.TYPES),
                "Regional form" to DetailSections.markIcon(TagKind.REGIONAL),
                "Battle Defense" to ConditionIcons.of("stat.def")!!)
            for ((label, icon) in choices) col.addView(LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(android.widget.ImageView(a).apply {
                    setImageResource(icon); setColorFilter(Ui.c(a, R.color.ag_mauve))
                }, LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)))
                addView(Ui.text(a, 13f, 800, R.color.ag_text).apply { text = label }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(a, 10) })
            }, LinearLayout.LayoutParams(-1, Ui.dp(a, 40)))
            shot(a, col, "m-507-icons-${theme.name.lowercase()}")
        }
    }

}
