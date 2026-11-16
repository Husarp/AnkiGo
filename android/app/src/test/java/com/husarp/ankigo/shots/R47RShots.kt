package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Paint
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R47RShots {
    @get:Rule val paparazzi = Shots.rule()
    private val leaf = Move("leafstorm", "Leaf Storm", Type.GRASS, Category.SPECIAL, 130, 90, selfBoosts = mapOf("spa" to -2))
    private val dance = Move("swordsdance", "Swords Dance", Type.NORMAL, Category.STATUS, 0, null, target = "self", boosts = mapOf("atk" to 2))
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun text(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun setup(theme: Theme): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
        return Shots.files(paparazzi.context).also { FakeGame.install(it) }
    }
    private fun shot(ctx: Context, name: String, theme: Theme, view: View) {
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), Ui.dp(ctx, 12))
            addView(view, LinearLayout.LayoutParams(-1, -2))
        }
        val dialog = AppDialog.Builder(ctx).setTitle(if (name.startsWith("668")) "Stat names" else "Moves")
            .setView(body).setPositiveButton("Done", null).create()
        paparazzi.snapshot(DialogShots.scene(ctx, dialog), "r-$name-${theme.name.lowercase()}")
        dialog.dismiss()
    }
    private fun data(): GameData {
        val data = GameRepo.data!!
        return GameData(data.species, data.moves + listOf(leaf, dance).associateBy { it.id }, data.growth, data.itemPrices)
    }

    @Test fun maskedRowsRevealOnlyCategoryAndLabels() {
        for (theme in Theme.entries) {
            val ctx = setup(theme); val data = data()
            val moves = listOf(leaf, dance, data.move("growl")!!, data.move("tackle")!!)
            val rows = DetailSections.moves(ctx, data, emptyList(), true, unknown = moves.size, outlines = moves.map(MoveOutline::of))
            val hidden = all(rows).filter { it.contentDescription == "Undiscovered move" }
            assertEquals(4, hidden.size)
            for ((row, move) in hidden.zip(moves)) {
                assertTrue(text(row).contains(DetailSections.kindName(move.category)))
                assertTrue(text(row).contains("?")); assertTrue(text(row).contains("???"))
                assertEquals(move.category != Category.STATUS, "Power" in text(row))
                assertTrue("Accuracy" in text(row))
                assertFalse(text(row).any { it == move.name || it == move.type.label || it == move.power.toString() || it == move.accuracy.toString() })
                assertFalse(row.isClickable)
            }
            shot(ctx, "620-hidden-moves", theme, rows)
            val mon = GameRepo.state!!.main!!
            mon.moves.clear(); mon.moves += listOf("growl", "tackle"); mon.discoveredMoves.clear()
            val owned = DialogShots.capture { MonDetail.show(ctx, GameRepo.data!!, mon, focusMove = "tackle") }!!
            assertTrue(text(all(owned.content()).first { it.contentDescription == "Undiscovered move" }).contains("Status"))
            owned.dismiss()
            val state = GameRepo.state!!; val wild = state.battle!!.wild
            wild.discoveredMoves.clear()
            val info = WildInfo.of(GameRepo.data!!, state, state.battle!!, emptySet())!!
            assertTrue(info.moves.all { it.id == null && it.name == null && it.outline != null })
        }
    }

    @Test fun floatingWindowsCollapseExplanationsAndStyleOnlyEffectParts() {
        for (theme in Theme.entries) {
            val ctx = setup(theme); val data = data()
            for (move in listOf(leaf, dance)) {
                val rows = DetailSections.moves(ctx, data, listOf(move.id), true)
                val dialog = DialogShots.capture { rows.findViewWithTag<View>("detail-move:${move.id}").performClick() }!!
                assertFalse(text(rows).any { it.contains("stage") })
                val content = dialog.content()
                val power = content.findViewWithTag<View>("move-help:Power")
                val accuracy = content.findViewWithTag<View>("move-help:Accuracy")
                assertEquals(View.GONE, power.visibility); assertEquals(View.GONE, accuracy.visibility)
                val sentence = all(content).filterIsInstance<TextView>().single { it.text.startsWith(if (move == leaf) "Lowers" else "Raises") }.text as Spanned
                val stat = sentence.getSpans(0, sentence.length, StatProse.NameSpan::class.java).single()
                assertEquals(Ui.c(ctx, DetailMath.statColor(if (move == leaf) Stat.SPA else Stat.ATK)), stat.color)
                val colors = sentence.getSpans(0, sentence.length, ForegroundColorSpan::class.java)
                assertEquals(2, colors.size)
                assertEquals(if (move == leaf) "Lowers" else "Raises", sentence.subSequence(sentence.getSpanStart(colors[0]), sentence.getSpanEnd(colors[0])).toString())
                assertTrue(sentence.subSequence(sentence.getSpanStart(colors[1]), sentence.getSpanEnd(colors[1])).startsWith("2 stages"))
                paparazzi.snapshot(DialogShots.scene(ctx, dialog), "r-658-${move.id}-${theme.name.lowercase()}")
                content.findViewWithTag<View>("move-metric:Accuracy").performClick()
                assertEquals(View.VISIBLE, accuracy.visibility)
                if (move == leaf) {
                    content.findViewWithTag<View>("move-metric:Power").performClick()
                    assertEquals(View.VISIBLE, power.visibility)
                    assertTrue((power as TextView).text.contains("80 Power hits about twice"))
                    paparazzi.snapshot(DialogShots.scene(ctx, dialog), "r-658-explanations-${theme.name.lowercase()}")
                    content.findViewWithTag<View>("move-metric:Power").performClick()
                    assertEquals(View.GONE, power.visibility)
                }
                dialog.dismiss()
            }
        }
    }

    @Test fun accuracyUsesBattleStagesOnBothSidesAndInWindow() {
        for (theme in Theme.entries) {
            val ctx = setup(theme); val data = data(); val state = GameRepo.state!!; val battle = state.battle!!
            val view = SimpleView.of(data, battle, state.main!!)
            for (stage in listOf(1, -1)) {
                battle.player.boosts[5] = stage
                val effective = MoveAccuracy.effective(leaf, view, true)!!
                assertEquals(if (stage > 0) 100.0 else 67.5, effective, 0.001)
                val rows = DetailSections.moves(ctx, data, listOf(leaf.id), true, battle = view)
                val labels = all(rows).filterIsInstance<TextView>()
                assertTrue(labels.single { it.text.toString() == "90%" }.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG != 0)
                assertEquals(StatPill.ink(stage > 0, StatPill.isDark(ctx)), labels.single { it.text.toString() == MoveAccuracy.percent(effective) }.currentTextColor)
                shot(ctx, "660-list-${if (stage > 0) "up" else "down"}", theme, rows)
                val adjusted = labels.single { it.text.toString() == MoveAccuracy.percent(effective) }
                assertTrue("adjusted accuracy fits above its original", adjusted.top >= 0 && adjusted.bottom <= (adjusted.parent as View).height)
                assertTrue(adjusted.height > 0)
                val dialog = DialogShots.capture { rows.findViewWithTag<View>("detail-move:${leaf.id}").performClick() }!!
                assertTrue(text(dialog.content()).contains(MoveAccuracy.percent(effective)))
                paparazzi.snapshot(DialogShots.scene(ctx, dialog), "r-660-window-${if (stage > 0) "up" else "down"}-${theme.name.lowercase()}")
                val inWindow = all(dialog.content()).filterIsInstance<TextView>().single { it.text.toString() == MoveAccuracy.percent(effective) }
                assertTrue("window accuracy is not clipped", inWindow.top >= 0 && inWindow.bottom <= (inWindow.parent as View).height)
                dialog.dismiss()
            }
            battle.player.boosts[5] = 0; battle.enemy.boosts[6] = 1
            assertEquals(67.5, MoveAccuracy.effective(leaf, view, true)!!, 0.001)
            battle.player.boosts[5] = 1
            assertNull(MoveAccuracy.effective(leaf, view, true))
            battle.enemy.boosts[5] = -1; battle.player.boosts[6] = 1
            assertEquals(54.0, MoveAccuracy.effective(leaf, view, false)!!, 0.001)
            assertNull(MoveAccuracy.effective(leaf.copy(accuracy = 100), view, true))
            assertNull(MoveAccuracy.effective(leaf.copy(accuracy = null), view, true))
            assertNull(MoveAccuracy.effective(leaf.copy(ohko = true), view, true))
            assertNull(MoveAccuracy.effective(dance, view, true))
            battle.player.boosts[5] = 0
            state.main!!.ability = "Keen Eye"
            assertNull(MoveAccuracy.effective(leaf, view, true))
            assertNull(MoveAccuracy.effective(leaf.copy(ignoreEvasion = true), view, true))
        }
    }

    @Test fun actualOwnedAndWildWindowsReceiveTheirOwnAccuracyStages() {
        val ctx = setup(Theme.LIGHT)
        val data = GameRepo.data!!; val state = GameRepo.state!!; val battle = state.battle!!
        val main = state.main!!; val wild = battle.wild
        main.moves.clear(); main.moves += "hypnosis"; main.discoveredMoves += "hypnosis"
        wild.moves.clear(); wild.moves += "hypnosis"; wild.discoveredMoves += "hypnosis"
        AppPrefs.setDetailOpen(ctx, DetailSection.MOVES.key, true)
        battle.player.boosts[5] = 1; battle.enemy.boosts[5] = -1
        val owned = DialogShots.capture { MonDetail.show(ctx, data, main) }!!
        assertTrue(text(owned.content()).contains("80%"))
        owned.dismiss()
        val enemy = DialogShots.capture { BattleDetails.showWild(ctx) }!!
        assertTrue(text(enemy.content()).contains("45%"))
        enemy.dismiss()
        val page = TeamPage(ctx, NoHost)
        val item = ItemWindow(page) {}
        item.update(ItemCard.Spec("pewter-crunchies"))
        val itemText = all(item.view).filterIsInstance<TextView>().map { it.text }.filterIsInstance<Spanned>()
        assertTrue(itemText.any { it.toString().contains("Special Attack") && it.getSpans(0, it.length, StatProse.NameSpan::class.java).isNotEmpty() })
        item.dismiss(); page.onGone()
        val info = DialogShots.capture { DetailParts.showInfo(ctx, StatInfo.of(Stat.SPA)) }!!
        val infoText = all(info.content()).filterIsInstance<TextView>().single { it.text.contains("target's Special Defense") }.text as Spanned
        assertEquals(Ui.c(ctx, R.color.ag_teal), infoText.getSpans(0, infoText.length, StatProse.NameSpan::class.java).single().color)
        info.dismiss()
    }

    @Test fun statNamesKeepTextAndSpansAcrossItemsAbilitiesNatureAndInfo() {
        for (theme in Theme.entries) {
            val ctx = setup(theme)
            val prose = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            for ((title, value) in listOf(
                "Choice Specs" to ItemCard.styled(ctx, "Boosts **Special Attack** by 50%, but locks the holder into the first move it uses."),
                "Intimidate" to StatProse.format(ctx, AbilityText.of("Intimidate")!!),
                "Special Attack" to StatProse.format(ctx, StatInfo.of(Stat.SPA).text),
                "Move" to MoveDetails.effect(ctx, "Raises its Special Attack and Special Defense by 1 stage (×1.5)."))) {
                prose.addView(Ui.text(ctx, 15f, 900, R.color.ag_text).apply { text = title; setPadding(0, Ui.dp(ctx, 16), 0, Ui.dp(ctx, 8)) })
                val formatted = StatProse.format(ctx, value) as Spanned
                assertEquals(value.toString(), formatted.toString())
                assertTrue(formatted.getSpans(0, formatted.length, StatProse.NameSpan::class.java).isNotEmpty())
                val twice = StatProse.format(ctx, formatted) as Spanned
                assertEquals(formatted.getSpans(0, formatted.length, StatProse.NameSpan::class.java).size, twice.getSpans(0, twice.length, StatProse.NameSpan::class.java).size)
                prose.addView(Ui.text(ctx, 14f, 700, R.color.ag_text).apply { text = formatted; setLineSpacing(0f, 1.3f) })
            }
            prose.addView(NatureChanges.line(ctx, Nature.NAIVE, true))
            val nature = all(prose).filterIsInstance<TextView>().single { it.text.toString() == "Speed +10%" }.text as Spanned
            assertEquals(Ui.c(ctx, R.color.ag_pink), nature.getSpans(0, nature.length, StatProse.NameSpan::class.java).single().color)
            val comparison = NatureChanges.effectText(ctx, "+Speed −Special Defense") as Spanned
            assertEquals(2, comparison.getSpans(0, comparison.length, StatProse.NameSpan::class.java).size)
            assertEquals("Defense Curl", StatProse.format(ctx, "Defense Curl"))
            shot(ctx, "668-stat-prose", theme, prose)
        }
    }
}
