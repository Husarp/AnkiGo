package com.husarp.ankigo.shots

import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R32HShots {
    @get:Rule val paparazzi = Shots.rule()
    @After fun reset() { ItemWindow.interceptShow = null; BattleShotKit.reset() }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = all(v).filterIsInstance<TextView>()

    @Test fun markedOwnerInChipStatusAndFullList() {
        for (theme in Theme.entries) for (kind in listOf("chip", "status", "list")) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            GameRepo.state!!.battle!!.player.apply { volatiles["confusion"] = 3; status = "par" }
            GameRepo.state!!.battle!!.enemy.volatiles["ingrain"] = 1
            val screen = BattleShotKit.bound(ctx)
            val arena = screen.arena!!
            arena.setPlayerInfo(null, "Celesteela", 50, Tier.ULTRA, 570)
            var opened = false
            var captured: ConditionList? = null
            val wrapper = object : FrameLayout(ctx) {
                override fun dispatchDraw(canvas: Canvas) {
                    if (!opened && arena.markRows().isNotEmpty()) {
                        arena.setPlayerName("Celesteela")
                        if (kind == "status") {
                            ArenaView::class.java.getDeclaredField("pStatus").let { it.isAccessible = true; (it.get(arena) as View).performClick() }
                        } else {
                            val method = ArenaView::class.java.getDeclaredMethod("showConditionList", ConditionMarks.Slot::class.java, Boolean::class.javaPrimitiveType, String::class.java)
                            method.isAccessible = true
                            method.invoke(arena, ConditionMarks.Slot.OURS, false, if (kind == "chip") "confusion" else null)
                        }
                        arena.measure(MeasureSpec.makeMeasureSpec(arena.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(arena.height, MeasureSpec.EXACTLY))
                        arena.layout(arena.left, arena.top, arena.right, arena.bottom)
                        captured = arena.conditionListShown()
                        opened = true
                    }
                    super.dispatchDraw(canvas)
                }
            }
            wrapper.addView(screen.root)
            paparazzi.snapshot(wrapper, "h-owner-$kind-${theme.name.lowercase()}", 1000)
            assertTrue(opened)
            val card = checkNotNull(captured)
            val content = texts(card).joinToString("\n") { it.text }
            assertTrue(content, content.contains("Your Celesteela"))
            val owners = texts(card).filter { it.text.contains("Celesteela") }
            assertTrue(owners.isNotEmpty())
            assertTrue(owners.all { !it.text.contains('\uFFFC') })
        }
    }

    @Test fun heldItemHasOnlyTheTappableItemAndKeepsCustomOrder() = paparazzi.shoot("h-held-item", listOf(Size.PHONE)) { ctx ->
        assertEquals(DetailSection.HELD_ITEM, AppPrefs.detailOrder(ctx).first())
        val custom = DetailOrder.default.reversed()
        AppPrefs.setDetailOrder(ctx, custom)
        assertEquals(custom, AppPrefs.detailOrder(ctx))
        val editor = DetailOrderSettings.Editor(ctx, custom)
        texts(editor).single { it.text == "Reset to default" }.performClick()
        assertEquals(DetailSection.HELD_ITEM, editor.order.first())
        assertEquals(custom, AppPrefs.detailOrder(ctx))
        val mon = GameRepo.state!!.main!!.apply { heldItem = "soothe-bell" }
        val page = TeamPage(ctx, NoHost)
        val section = checkNotNull(DetailTopCard.heldItem(ctx, GameRepo.data!!, mon, page))
        assertFalse(texts(section).any { it.text.contains("Take off") || it.text.contains("Give") })
        val item = all(section).single { it.contentDescription?.contains("Tap for what it does") == true }
        var opened: ItemWindow? = null
        ItemWindow.interceptShow = { opened = it }
        item.performClick()
        val window = checkNotNull(opened)
        assertTrue(all(window.view).any { it.contentDescription == "Take off" })
        assertTrue(all(window.view).any { it.contentDescription == "Give to another Pokémon" })
        window.dismiss()
        FrameLayout(ctx).apply {
            addView(section, FrameLayout.LayoutParams(-1, -2))
            addView(window.view, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
    }

    @Test fun bondFitsOnlyPerksAtNarrowWidths() {
        for (width in listOf(320, 412)) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant("w$width", width, 869).device(theme).copy(fontScale = 1.3f))
            val ctx = Shots.files(paparazzi.context)
            val mon = TestData.state(TestData.world()).main!!.apply { bondPoints = 370 }
            val body = BondSection.body(ctx, mon)
            val frame = FrameLayout(ctx).apply {
                setPadding(Ui.dp(ctx, 30), Ui.dp(ctx, 20), Ui.dp(ctx, 30), 0)
                addView(body, FrameLayout.LayoutParams(-1, -2))
            }
            paparazzi.snapshot(frame, "h-bond-w$width-${theme.name.lowercase()}")
            val labels = texts(body)
            val titles = labels.filter { it.text.toString() in listOf("CURRENT PERKS", "NEXT · LEVEL 4") }
            assertEquals(2, titles.size)
            titles.forEach {
                assertEquals(Ui.text(ctx, ItemCard.TITLE_SP, 900, com.husarp.ankigo.R.color.ag_text).textSize, it.textSize, .1f)
                assertEquals(Gravity.CENTER_VERTICAL, it.gravity and Gravity.VERTICAL_GRAVITY_MASK)
            }
            assertEquals(Ui.text(ctx, 18f, 900, com.husarp.ankigo.R.color.ag_text).textSize, labels.single { it.text == "Growing close" }.textSize, .1f)
            assertEquals(Ui.text(ctx, 13f, 800, com.husarp.ankigo.R.color.ag_text).textSize, labels.single { it.text == "120 / 250 points to Level 4" }.textSize, .1f)
            val perks = labels.filter { (it.parent as? LinearLayout)?.parent is LinearLayout && (it.text.startsWith("+") || it.text == "All species matchups revealed" || it.text.contains("chance to survive")) }
            assertTrue(perks.isNotEmpty())
            assertEquals(1, perks.map { it.textSize }.distinct().size)
            assertTrue(perks.first().textSize < Ui.text(ctx, 13f, 800, com.husarp.ankigo.R.color.ag_text).textSize)
            perks.forEach { assertEquals(1, it.lineCount); assertTrue(it.layout.getLineWidth(0) <= it.width + 1) }
        }
    }

    @Test fun compareUsesTheNeutralSentenceInsteadOfTiles() = paparazzi.shoot("h-compare-matchups", listOf(Size.PHONE)) { ctx ->
        val state = GameRepo.state!!
        val picks = listOf(ComparePick.Owned(state.main!!.uid), ComparePick.Dex(54))
        val table = CompareTable.build(GameRepo.data!!, state, picks, false)
        assertTrue(table.row("normal").cells.filterIsInstance<Cell.Text>().any { it.text == "All other types deal ×1 damage." })
        CompareStore.saveRows(ctx, RowChoice(table.rows.map { it.key }.filter { it !in setOf("types", "weak", "resist", "immune", "normal", "strong") }.toSet()))
        val page = ComparePage(ctx, NoHost, picks)
        pageScreen(ctx, page).also { page.render() }
    }

    @Test fun neutralMatchupsAreOneLineEvenWithUnknownTypes() {
        for (unknown in listOf(false, true)) paparazzi.shoot("h-matchups-${if (unknown) "unknown" else "known"}", listOf(Size.PHONE)) { ctx ->
            val types = listOf(Type.GHOST, Type.POISON)
            val known = if (unknown) TypeMatchups.discovered(types, setOf(Type.NORMAL, Type.FIRE)) else TypeMatchups.of(types)
            DetailSections.matchups(ctx, types, "Gastly", known).also { body ->
                val labels = texts(body).map { it.text.toString() }
                assertEquals(1, labels.count { it == "All other types deal ×1 damage." })
                assertFalse(labels.any { it.contains("Normal damage") || it == "Fire" })
                assertEquals(unknown, labels.contains("???"))
            }
        }
    }
}
