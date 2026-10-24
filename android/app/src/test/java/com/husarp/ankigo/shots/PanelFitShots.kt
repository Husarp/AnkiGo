package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.ArenaGeo
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.PanelFit
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The battle panel scaled to every window size (PanelFit): full panel at PHONE / SPLIT / SPLIT_SHORT / SPLIT_TINY, small-window
 * mode (picture-in-picture, floating battle) at PIP_169 / PIP_169M / PIP_11 / PIP / FLOATB / FLOAT, each with compact stat cards
 * off and on, small sizes also without the stats bar; plus a status pill with long names. PNGs: pf-full-cc0|cc1, pf-small-cc0|cc1-sb1|sb0,
 * pf-status-cc0|cc1, pf-tools, pf-strip. [calibration] checks the pure model against the real views.
 */
class PanelFitShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val extraSpecies = ArrayList<Int>()

    @After fun cleanUp() {
        @Suppress("UNCHECKED_CAST") val m = FakeGame.data.species as MutableMap<Int, *>
        extraSpecies.forEach { m.remove(it) }
        extraSpecies.clear()
        BattleShotKit.reset()
    }

    private val full = listOf(Size.PHONE, Size.SPLIT, Size.SPLIT_SHORT, Size.SPLIT_TINY)
    private val small = listOf(Size.PIP_169, Size.PIP_169M, Size.PIP_11, Size.PIP, Size.FLOATB, Size.FLOAT)

    private fun prefs(ctx: Context, compactCards: Boolean, statsBar: Boolean = true) {
        AppPrefs.setCompactCards(ctx, compactCards)
        AppPrefs.setStatsBarSmall(ctx, statsBar)
    }

    /** A bare panel in small-window mode, as FloatBattleView and picture-in-picture set it up. */
    private fun bare(ctx: Context, grip: Boolean = false, status: Pair<String?, String?>? = null, paused: Boolean = false): BattlePanelView {
        val p = BattlePanelView(ctx)
        p.setCompact(true)
        if (paused) p.setPaused(true)
        if (grip) p.setCornerClearance(Ui.dp(ctx, 26))
        if (status != null) { val b = GameRepo.state!!.battle!!; b.enemy.status = status.first; b.player.status = status.second }
        p.renderAll()
        p.setLog(com.husarp.ankigo.ui.LogKind.GOOD, "Good", "Sparkmouse used {{Thunderbolt}}! It's super effective.", false)
        return p
    }

    private fun longWild(name: String, vararg types: Type) {
        val num = 990 + extraSpecies.size
        @Suppress("UNCHECKED_CAST") (FakeGame.data.species as MutableMap<Int, Species>)[num] = TestData.species(num, name, types.toList())
        extraSpecies += num
        val st = GameRepo.state!!
        val w = TestData.mon(FakeGame.data, num, 25, listOf("tackle"), "wild")
        w.hp = w.hp * 7 / 10
        st.battle = Battle(w, Tier.NORMAL, answers = 1, tally = 10, rounds = 2, startedAt = 1L)
    }

    // ---------------------------------------------------------------- full panel

    @Test fun fullOff() = paparazzi.shootWith("pf-full-cc0", full) { ctx -> prefs(ctx, false); BattleShotKit.bound(ctx).root }
    @Test fun fullOn() = paparazzi.shootWith("pf-full-cc1", full) { ctx -> prefs(ctx, true); BattleShotKit.bound(ctx).root }

    // ---------------------------------------------------------------- small windows

    @Test fun smallOff() = paparazzi.shootWith("pf-small-cc0-sb1", small) { ctx -> prefs(ctx, false); bare(ctx, grip = true) }
    @Test fun smallOn() = paparazzi.shootWith("pf-small-cc1-sb1", small) { ctx -> prefs(ctx, true); bare(ctx, grip = true) }
    @Test fun smallOffNoStats() = paparazzi.shootWith("pf-small-cc0-sb0", small) { ctx -> prefs(ctx, false, statsBar = false); bare(ctx, grip = true) }
    @Test fun smallOnNoStats() = paparazzi.shootWith("pf-small-cc1-sb0", small) { ctx -> prefs(ctx, true, statsBar = false); bare(ctx, grip = true) }

    // ---------------------------------------------------------------- status pills, long names

    private val statusSizes = listOf(Size.SPLIT, Size.SPLIT_TINY, Size.PIP_169M, Size.PIP_11, Size.FLOATB)

    @Test fun statusOff() = paparazzi.shootWith("pf-status-cc0", statusSizes) { ctx ->
        prefs(ctx, false); longWild("Crabominable", Type.ELECTRIC, Type.FIGHTING); bare(ctx, status = "brn" to "par")
    }
    @Test fun statusOn() = paparazzi.shootWith("pf-status-cc1", statusSizes) { ctx ->
        prefs(ctx, true); longWild("Fletchinder", Type.FIRE, Type.FLYING); bare(ctx, status = "brn" to "par")
    }

    /** Paused picture-in-picture: the pill sits in the log line, or over the arena's corner when the log has stepped out. */
    @Test fun paused() {
        paparazzi.shootWith("pf-paused-cc0", listOf(Size.PIP_169, Size.PIP_169M, Size.FLOATB)) { ctx -> prefs(ctx, false); bare(ctx, paused = true) }
        paparazzi.shootWith("pf-paused-cc1", listOf(Size.PIP_169, Size.PIP_169M, Size.FLOATB)) { ctx -> prefs(ctx, true); bare(ctx, paused = true) }
    }

    /** refreshPrefs(): a pref changed after the panel was built shows up after the call. */
    @Test fun refresh() = paparazzi.shootWith("pf-refresh", listOf(Size.SPLIT, Size.PIP_169M)) { ctx ->
        prefs(ctx, false)
        val p = bare(ctx)
        AppPrefs.setCompactCards(ctx, true)
        AppPrefs.setStatsBarSmall(ctx, false)
        p.refreshPrefs()
        p
    }

    // ---------------------------------------------------------------- footer tools, strip

    /** The goal row with two tool buttons and the menu button (full panel), and with the tools hidden. */
    @Test fun tools() {
        paparazzi.shootWith("pf-tools", listOf(Size.SPLIT)) { ctx ->
            prefs(ctx, false)
            val p = BattlePanelView(ctx)
            p.addToolButton(com.husarp.ankigo.R.drawable.ic_stats, "Stats") {}
            p.addToolButton(com.husarp.ankigo.R.drawable.ic_pip, "Pop out") {}
            p.renderAll(); p
        }
        paparazzi.shootWith("pf-tools-off", listOf(Size.SPLIT)) { ctx ->
            prefs(ctx, false)
            val p = BattlePanelView(ctx)
            p.addToolButton(com.husarp.ankigo.R.drawable.ic_stats, "Stats") {}
            p.setFooterTools(false)
            p.renderAll(); p
        }
    }

    /** The status-problem strip on a normal and a small window. */
    @Test fun strip() {
        paparazzi.shootWith("pf-strip", listOf(Size.SPLIT_SHORT, Size.PIP_169M)) { ctx ->
            prefs(ctx, false)
            val p = BattlePanelView(ctx)
            if (ctx.resources.configuration.screenHeightDp < 200) p.setCompact(true)
            p.setStatus(false, "AnkiGo can't read AnkiDroid yet.", true)
            p.renderAll(); p
        }
    }

    // ---------------------------------------------------------------- the study screen's panel

    /** The Study screen with the panel in [heightMode] (0 full panel, 1 strip), as StatusShots builds it. */
    private fun study(ctx: Context, heightMode: Int): View {
        val act = attach(com.husarp.ankigo.StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        setField(act, "heightMode", heightMode)
        val root = act.buildContent()
        setField(act, "card", com.husarp.ankigo.Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>")
        setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic")
        setField(act, "cardName", "Card 1")
        setField(act, "flipped", false)
        setField(act, "locked", false)
        (getField(act, "subtitle") as android.widget.TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as BattlePanelView
        val b = GameRepo.state!!.battle!!
        b.enemy.status = "par"; b.player.status = "brn"
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        return root
    }

    /** Full panel on the phone's 220 dp study panel (scaled, no scrolling), and the strip with compact cards on and off. */
    @Test fun studyPanel() {
        paparazzi.shootWith("pf-study-full", listOf(Size.PHONE)) { ctx -> prefs(ctx, false); study(ctx, 0) }
        paparazzi.shootWith("pf-study-full-cc1", listOf(Size.PHONE)) { ctx -> prefs(ctx, true); study(ctx, 0) }
        paparazzi.shootWith("pf-study-strip", listOf(Size.PHONE, Size.SPLIT)) { ctx -> prefs(ctx, false); study(ctx, 1) }
        paparazzi.shootWith("pf-study-strip-cc1", listOf(Size.PHONE, Size.SPLIT)) { ctx -> prefs(ctx, true); study(ctx, 1) }
    }

    // ---------------------------------------------------------------- animations under scaling

    /**
     * The "+40" that floats up from the coin chip must start on the chip even though the panel is drawn scaled (it is
     * added to the overlay inside the scaled container). The panel is laid out by a first snapshot, then the float is
     * added and the second snapshot catches it 250 ms into its rise.
     */
    @Test fun coins() {
        for (size in listOf(Size.SPLIT_TINY, Size.PIP_169M)) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            prefs(ctx, true)
            val p = if (size == Size.PIP_169M) bare(ctx) else BattlePanelView(ctx).also { it.renderAll() }
            val frame = android.widget.FrameLayout(ctx).apply { addView(p, android.widget.FrameLayout.LayoutParams(-1, -1)) }
            val name = "${size.name.lowercase()}-${theme.name.lowercase()}"
            paparazzi.snapshot(frame, "pf-coins-before-$name", 0)
            p.floatAtCoins("+40")
            paparazzi.snapshot(frame, "pf-coins-$name", 250)
        }
    }

    // ---------------------------------------------------------------- calibration of the pure model

    private fun field(o: Any, name: String): Any = o.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(o)!! }

    /**
     * The numbers in PanelFit (heights of the log card, footer, chips row and the two cards at a given text floor) are
     * measured on the real views here, in virtual dp, and written to build/shots/panelfit-calibration.txt; the test
     * fails when the model is off by more than 6 dp (the renderer rounds text sizes to whole pixels, which costs the chips row a few dp).
     */
    @Test fun calibration() {
        val out = StringBuilder()
        val bad = ArrayList<String>()
        for ((name, size) in listOf("split" to Size.SPLIT, "split_tiny" to Size.SPLIT_TINY, "pip_169" to Size.PIP_169, "pip_169m" to Size.PIP_169M, "pip_11" to Size.PIP_11)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, Theme.LIGHT))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            prefs(ctx, false)
            // The streak chip shows "12 days" only while the goal day is today/yesterday; pin it so the row width doesn't depend on the date.
            com.husarp.ankigo.GameRepo.game?.let { g -> com.husarp.ankigo.GameRepo.state?.lastGoalDay = g.dayKey() }
            val small = size.wDp < 400
            val p = if (small) bare(ctx) else BattlePanelView(ctx).also { it.renderAll() }
            val frame = android.widget.FrameLayout(ctx).apply { addView(p, android.widget.FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "pf-calibration-$name", 100)
            val d = ctx.resources.displayMetrics.density
            val f = p.currentFit
            val floor = f.floorSp
            fun dp(px: Int) = px / d
            val logBox = field(p, "logBox") as View
            val footer = field(p, "footer") as View
            val chips = field(p, "chips") as ViewGroup
            val arena = p.arena
            val enemyCard = field(arena, "enemyCard") as View
            val playerCard = field(arena, "playerCard") as View
            var chipsW = 0f
            for (i in 0 until chips.childCount) chipsW += dp(chips.getChildAt(i).measuredWidth)
            val cards = ArenaGeo.cards(floor, f.compactCards, true)
            val rows = listOf(
                Triple("log box", dp(logBox.height), if (f.log) PanelFit.Heights.log(f.logLines, floor) - 8f else 0f),
                Triple("footer", dp(footer.height), if (f.stats) PanelFit.Heights.footer(floor, f.tools) else 0f),
                Triple("chips width", if (f.stats) chipsW + 20f else 0f, if (f.stats) PanelFit.Heights.chips(floor) else 0f),
                Triple("wild card", dp(enemyCard.height), cards.wildH),
                Triple("player card", dp(playerCard.height), cards.playerH),
            )
            out.append("$name  scale=${f.scale} floor=$floor virtual=${f.virtualW}x${f.virtualH} middle=${f.middle} ok=${f.ok}  arena=${dp(arena.height)}\n")
            for ((what, real, model) in rows) {
                out.append("   %-12s real %.1f  model %.1f\n".format(what, real, model))
                if (kotlin.math.abs(real - model) > 6f) bad += "$name $what real $real model $model"
            }
        }
        File(Shots.outDir, "panelfit-calibration.txt").writeText(out.toString())
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
