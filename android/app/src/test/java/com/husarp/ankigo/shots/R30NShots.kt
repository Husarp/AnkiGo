package com.husarp.ankigo.shots

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30NShots {
    @get:Rule val paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun scene(name: String, build: (Context) -> View) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("p360", 360, 820).device(theme))
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.withSprites = true
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            ShopCardKit.install()
            val view = build(ctx)
            val alert = (view as? BattlePanelView)?.toastLayer?.shownCard
            paparazzi.snapshot(view, "n-$name-${theme.name.lowercase()}", 300)
            if (view is BattlePanelView) {
                val card = checkNotNull(alert)
                assertEquals("Reached Level 5 Bond", card.main.text.toString())
                assertFalse(card.truncated)
                assertTrue(card.main.lineCount in 1..2)
            }
        }
    }
    @Test fun evolutionAndHeldItemWindows() {
        for (item in listOf("thunder-stone", "amulet-coin", "metal-alloy")) scene("487-$item") { ctx ->
            val page = ShopPage(ctx, NoHost)
            val window = ItemWindow(page) {}
            window.update(ItemCard.Spec(item))
            val text = texts(window.view).joinToString("\n")
            assertFalse(text.contains("Close all")); assertFalse(text.contains("Everstone not held"))
            assertTrue(text.contains("WHERE TO FIND", true))
            if (item != "amulet-coin") assertTrue(text.contains("Level 2 Bond"))
            FrameLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(window.view, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }
        }
    }
    @Test fun evolutionReadinessWithAndWithoutEverstone() = scene("487-readiness") { ctx ->
        val data = GameRepo.data!!
        val state = GameRepo.state!!
        val mon = state.main!!
        val e = Evolution(mon.species, EvoKind.ITEM, item = "metal-alloy")
        mon.bondPoints = Bond.thresholds[3]
        val ready = EvolutionRowText.explanation(data, e, data.sp(mon.species), state.seen, state, mon)
        assertTrue(ready.contains("Level 2 Bond ✓")); assertFalse(ready.contains("Bond now"))
        assertFalse(ready.contains("Everstone"))
        mon.heldItem = "everstone"
        val content = EvolutionRows.explanationContent(ctx, data, e, data.sp(mon.species), state.seen, state, mon) {}
        assertFalse(texts(content).any { it.contains("Everstone") })
        assertTrue("✓ Level 2 Bond" in texts(content))
        mon.bondPoints = 0
        assertFalse(EvolutionRowText.explanation(data, e, data.sp(mon.species), state.seen, state, mon).contains("Level 2 Bond ✓"))
        FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, R.color.ag_card))
            addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        }
    }
    @Test fun bondShowsCurrentAndNextPerksAtEveryLevel() = scene("490-current-perks") { ctx ->
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, R.color.ag_card))
            setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 12), Ui.dp(ctx, 20), 0)
            val mon = GameRepo.state!!.main!!
            for (level in 0..5) {
                mon.bondPoints = Bond.thresholds[level]
                val body = BondSection.body(ctx, mon)
                val t = texts(body).joinToString("\n")
                assertEquals(level < 5, t.contains("NEXT ·"))
                assertFalse(t.contains("slot")); assertFalse(t.contains("Level 6"))
                assertTrue(t.contains("CURRENT · $level"))
                if (level == 0) assertFalse(t.contains("+0")) else assertTrue(t.contains("+${level * 5}% XP"))
                if (level in listOf(0, 3, 5)) addView(body, Form.lp(ctx, 16))
            }
        }
    }
    @Test fun compactBondAlertUsesSpriteAndTwoLines() = scene("497-alert") { ctx ->
        val panel = BattlePanelView(ctx)
        panel.renderAll()
        val dm = ctx.resources.displayMetrics
        panel.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        panel.layout(0, 0, dm.widthPixels, dm.heightPixels)
        panel.setLog(LogKind.SYS, "BOND", "Bond grew! Eevee reached Level 5. 10% chance to survive a KO with 1 HP", pop = false)
        assertEquals(ToastKind.BOND, ToastKind.of(LogKind.SYS, "BOND"))
        assertEquals("Reached Level 5 Bond" to null, ToastText.split(ToastKind.BOND, "Bond grew! Eevee reached Level 5. bonus"))
        panel
    }
}
