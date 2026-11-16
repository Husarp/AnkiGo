package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R39BShots {
    @get:Rule val paparazzi = Shots.rule()
    @After fun reset() = BattleShotKit.reset()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun setup(theme: Theme): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
        return Shots.files(paparazzi.context).also { FakeGame.install(it) }
    }
    private fun naive(mon: Mon) = Mon(mon.uid, mon.species, mon.name, mon.level, mon.xp, mon.ivs, mon.evs,
        Nature.NAIVE, mon.gender, mon.shiny, mon.ability, mon.moves, mon.hp)
    private fun shot(ctx: Context, dialog: AppDialog, name: String, theme: Theme) {
        paparazzi.snapshot(DialogShots.scene(ctx, dialog), "b-$name-${theme.name.lowercase()}")
        dialog.dismiss()
    }

    @Test fun helpUsesFullStatNames() {
        for (theme in Theme.entries) {
            val ctx = setup(theme)
            val page = com.husarp.ankigo.help.HelpScreen(ctx, NoHost,
                com.husarp.ankigo.help.HelpRoute.Guide(com.husarp.ankigo.help.GameGuide.STATS), false)
            val scene = pageScreen(ctx, page)
            assertTrue(texts(scene).any { it.contains("Special Attack and Special Defense") })
            paparazzi.snapshot(scene, "b-help-stats-${theme.name.lowercase()}")
            page.onGone()
        }
    }

    @Test fun natureRowsOpenSpecificWindowsForEveryNature() {
        for (theme in Theme.entries) {
            val ctx = setup(theme)
            for (nature in Nature.entries) {
                val row = DetailSections.natureRow(ctx, nature, compact = true)
                val dialog = DialogShots.capture { row.performClick() }!!
                val text = texts(dialog.content())
                assertTrue(text.contains(nature.label))
                if (nature.plus == null) {
                    assertTrue(text.contains("This nature changes no stats."))
                    assertFalse(text.any { it.contains("10%") })
                } else {
                    val up = "${DetailMath.statName(nature.plus!!)} +10%"
                    val down = "${DetailMath.statName(nature.minus!!)} −10%"
                    assertTrue(text.contains(up)); assertTrue(text.contains(down))
                    val labels = all(dialog.content()).filterIsInstance<TextView>()
                    assertEquals(StatPill.ink(true, StatPill.isDark(ctx)), labels.single { it.text.toString() == up }.currentTextColor)
                    assertEquals(Ui.c(ctx, R.color.ag_red), labels.single { it.text.toString() == down }.currentTextColor)
                }
                if (nature in listOf(Nature.NAIVE, Nature.HARDY)) shot(ctx, dialog, "nature-${nature.name.lowercase()}", theme)
                else dialog.dismiss()
            }
        }
    }

    @Test fun wildNatureIsInTheOwnedHeaderPositionAndOpensItsWindow() {
        for (theme in Theme.entries) for (known in listOf(false, true)) {
            val ctx = setup(theme)
            val state = GameRepo.state!!
            val wild = naive(state.battle!!.wild)
            state.battle = Battle(wild, Tier.NORMAL)
            if (known) state.caught += wild.species else {
                state.caught.remove(wild.species); state.defeatedSpecies.remove(wild.species)
                state.mons.removeAll { it.species == wild.species }
            }
            val dialog = DialogShots.capture { assertTrue(BattleDetails.showWild(ctx)) }!!
            val header = all(dialog.content()).filterIsInstance<TextView>().single { it.text.toString() == DetailFormat.numLine(GameRepo.data!!.sp(wild.species).baseNum, Nature.NAIVE.label) }
            val natureDialog = DialogShots.capture { header.performClick() }!!
            assertTrue(texts(natureDialog.content()).contains("Special Defense −10%"))
            natureDialog.dismiss()
            shot(ctx, dialog, "wild-${if (known) "known" else "unknown"}", theme)
        }
    }

    @Test fun ownedDetailsAndMoreMenuUseNatureAndUnequip() {
        for (theme in Theme.entries) {
            val ctx = setup(theme); val page = TeamPage(ctx, NoHost)
            val state = GameRepo.state!!
            val old = state.main!!
            val mon = naive(old).apply { heldItem = "leftovers"; ability = "Lightning Rod" }
            state.mons[state.mons.indexOf(old)] = mon
            DetailSection.entries.forEach { AppPrefs.setDetailOpen(ctx, it.key, it == DetailSection.TRAINING) }
            val dialog = DialogShots.capture { MonDetail.show(page, GameRepo.data!!, mon) }!!
            assertTrue(texts(dialog.content()).contains("Special Defense −10%"))
            shot(ctx, dialog, "owned-details", theme)
            DetailSection.entries.forEach { AppPrefs.setDetailOpen(ctx, it.key, it == DetailSection.STATS) }
            val stats = DialogShots.capture { MonDetail.show(page, GameRepo.data!!, mon) }!!
            assertTrue(texts(stats.content()).any { it.contains("Special Attack") && it.contains("Electric") })
            shot(ctx, stats, "owned-stats-ability", theme)
            val menu = MenuKit.capture { MonActions(page).menu(mon, View(ctx)) }!!
            menu.expandMore()
            assertTrue(menu.describe().any { it.second == "Unequip Leftovers" })
            assertFalse(menu.describe().any { it.second.startsWith("Take ") })
            paparazzi.snapshot(MenuKit.scene(ctx, menu), "b-more-menu-${theme.name.lowercase()}")
            val footer = ItemCard.footer(page, ItemCard.Spec("leftovers"))
            assertTrue(texts(footer).contains("Unequip Leftovers"))
            val item = ItemWindow(page) {}
            item.update(ItemCard.Spec("leftovers"))
            paparazzi.snapshot(FrameLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(item.view, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }, "b-held-actions-${theme.name.lowercase()}")
            item.dismiss()
            page.onGone()
        }
    }

    @Test fun evolutionLevelIsOnlyInRequirements() {
        for (theme in Theme.entries) {
            val ctx = setup(theme); val data = GameRepo.data!!
            val e = Evolution(54, EvoKind.LEVEL, level = 55)
            val d = EvolutionRows.explanationDialog(ctx, data, e, data.sp(25), setOf(25, 54), GameRepo.state, GameRepo.state!!.main) {}
            assertEquals(1, texts(d.content()).count { it.contains("Reach level 55") })
            assertTrue(texts(d.content()).any { it.contains("When ready, choose Evolve") })
            shot(ctx, d, "evolution-level", theme)
        }
    }

    @Test fun singleArrowsHaveShaftsAndStacksKeepHalfArrows() {
        // Bounds include a 12-unit shaft for a lone arrow; stacks retain 4-unit heads at 7-unit pitch.
        for (up in listOf(false, true)) for (n in 1..6) {
            val path = Path(); StatPill.chevrons(path, n, up)
            val bounds = RectF(); path.computeBounds(bounds, true)
            assertEquals(if (n == 1) 12f else if (n == 2) 11f else 18f, bounds.height(), 0.01f)
            assertTrue(bounds.top >= 0f); assertTrue(bounds.bottom <= 25f)
        }
        for (theme in Theme.entries) {
            val ctx = setup(theme)
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 20), Ui.dp(ctx, 20), Ui.dp(ctx, 20))
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                for (battleStyle in listOf(false, true)) for (up in listOf(false, true)) {
                    addView(Ui.text(ctx, 14f, 800, R.color.ag_text).apply { text = "${if (battleStyle) "Arena" else "Move"} · ${if (up) "Up" else "Down"}" })
                    addView(LinearLayout(ctx).apply {
                        for (n in 1..6) addView(StatPillView(ctx, "spe", if (up) n else -n, battleStyle))
                    })
                }
            }
            paparazzi.snapshot(col, "b-stage-arrows-${theme.name.lowercase()}")
        }
    }

    @Test fun moveCategoryIconsAndFullStatExplanations() {
        for (theme in Theme.entries) {
            val ctx = setup(theme); val data = GameRepo.data!!
            val moves = DetailSections.moves(ctx, data, listOf("tackle", "watergun", "growl"), true)
            for (kind in Category.entries) {
                val tile = all(moves).first { it.contentDescription == DetailSections.kindName(kind) } as ViewGroup
                val icon = tile.getChildAt(0)
                assertEquals(Ui.dp(ctx, 28 * 2 / 3), icon.layoutParams.width)
                assertEquals(icon.layoutParams.width, icon.layoutParams.height)
            }
            shot(ctx, AppDialog.Builder(ctx).setTitle("Moves").setView(moves).create(), "moves", theme)
            val row = DetailParts.tapInfo(View(ctx), StatInfo.of(Stat.SPA))
            val info = DialogShots.capture { row.performClick() }!!
            assertTrue(texts(info.content()).any { it.contains("target's Special Defense") })
            shot(ctx, info, "special-attack-info", theme)
            val calm = Move("calmmind", "Calm Mind", Type.PSYCHIC, Category.STATUS, 0, null,
                target = "self", boosts = mapOf("spa" to 1, "spd" to 1))
            val moveData = TestData.data(data.species.values.toList(), TestData.MOVES + (calm.id to calm))
            val explained = DetailSections.moves(ctx, moveData, listOf(calm.id), true)
            val moveWindow = DialogShots.capture { all(explained).single { it.contentDescription?.toString()?.startsWith("Calm Mind,") == true }.performClick() }!!
            assertTrue(texts(moveWindow.content()).any { it.contains("Special Attack") && it.contains("Special Defense") })
            shot(ctx, moveWindow, "move-stat-prose", theme)
            val page = TeamPage(ctx, NoHost)
            val item = ItemWindow(page) {}
            item.update(ItemCard.Spec("pewter-crunchies"))
            assertTrue(texts(item.view).any { it.contains("Special Attack") })
            paparazzi.snapshot(FrameLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(item.view, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }, "b-item-stat-prose-${theme.name.lowercase()}")
            item.dismiss(); page.onGone()
        }
    }
}
