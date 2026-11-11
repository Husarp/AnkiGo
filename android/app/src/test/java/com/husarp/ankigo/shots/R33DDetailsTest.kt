package com.husarp.ankigo.shots

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R33DDetailsTest {
    @get:Rule val paparazzi = Shots.rule()
    @After fun reset() { ItemWindow.interceptShow = null; BoxMonPicker.interceptShow = null; Pickers.interceptApp = null }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun notifyChanges() {
        (GameRepo::class.java.getDeclaredField("notifyRunnable").apply { isAccessible = true }.get(GameRepo) as Runnable).run()
    }

    @Test fun neutralSentenceFollowsEveryMatchupGroup() = paparazzi.shoot("d-details-matchups", listOf(Size.PHONE)) { ctx ->
        val types = listOf(Type.GHOST, Type.POISON)
        val body = DetailSections.matchups(ctx, types, "Gastly", TypeMatchups.discovered(types, setOf(Type.NORMAL, Type.FIRE, Type.DARK)))
        assertEquals("All other types deal ×1 damage.", texts(body).last())
        assertTrue(texts(body).contains("Strong against"))
        scrollOnBase(ctx, body)
    }

    @Test fun compareEndsMatchupsWithNeutralSentence() = paparazzi.shoot("d-compare-matchups", listOf(Size.PHONE)) { ctx ->
        val state = GameRepo.state!!
        state.seenMatchups[state.main!!.species] = mutableSetOf(Type.GROUND)
        val picks = listOf(ComparePick.Owned(state.main!!.uid), ComparePick.Dex(54))
        val table = CompareTable.build(GameRepo.data!!, state, picks, false)
        val keys = table.rows.map { it.key }
        assertEquals(listOf("weak", "resist", "immune", "strong", "normal"), keys.subList(keys.indexOf("weak"), keys.indexOf("normal") + 1))
        assertEquals("evo", keys[keys.indexOf("normal") + 1])
        CompareStore.saveRows(ctx, RowChoice(keys.filter { it !in setOf("types", "weak", "resist", "immune", "strong", "normal") }.toSet()))
        val page = ComparePage(ctx, NoHost, picks)
        pageScreen(ctx, page).also { page.render() }
    }

    @Test fun unknownTilesShareTypeCellDimensionsAndGridForEveryStyle() {
        for (style in TypeStyle.entries) for (width in listOf(160, 340)) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            AppLook.setTypeStyle(ctx, style)
            val box = MatchupBoxes.box(ctx, MatchupGroup.WEAK, listOf(Type.FIRE, Type.ELECTRIC), "Test", compact = width == 160, roomPx = Ui.dp(ctx, width), unknownCount = 4)
            val frame = FrameLayout(ctx).apply { addView(box, FrameLayout.LayoutParams(Ui.dp(ctx, width), -2)) }
            paparazzi.snapshot(frame, "d-tiles-${style.name.lowercase()}-$width-${theme.name.lowercase()}")
            val flow = all(box).filterIsInstance<FlowRow>().single()
            val chips = (0 until flow.childCount).map { flow.getChildAt(it) }
            assertEquals(6, chips.size)
            assertEquals(1, chips.map { it.width to it.height }.distinct().size)
            assertTrue(chips.all { it.width > 0 && it.height > 0 })
            val columns = chips.groupBy { it.top }.values
            assertTrue(columns.all { row -> row.zipWithNext().all { (a, b) -> b.left == a.right + Ui.dp(ctx, 5) } })
            assertTrue(columns.all { it.first().left == 0 })
            assertTrue(chips.drop(2).all { it.contentDescription == "Matchup not yet seen in battle" && !it.isClickable })
        }
    }

    @Test fun takingOffAndGivingRefreshTheAlreadyOpenDetails() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val state = GameRepo.state!!
            state.mons.forEach { it.heldItem = null }
            val mon = state.main!!.apply { heldItem = "soothe-bell" }
            val page = TeamPage(ctx, NoHost)
            for (section in DetailSection.entries) AppPrefs.setDetailOpen(page, section.key, section == DetailSection.HELD_ITEM)
            val detail = DialogShots.capture { MonDetail.show(page, GameRepo.data!!, mon) }!!
            val scene = DialogShots.scene(ctx, detail)
            var window: ItemWindow? = null
            ItemWindow.interceptShow = { window = it }
            all(scene).single { it.contentDescription?.contains("Tap for what it does") == true && it.contentDescription?.contains("Soothe Bell") == true }.performClick()
            val item = checkNotNull(window)
            val before = state.count("soothe-bell")
            var took = false
            val wrapper = object : FrameLayout(ctx) {
                override fun dispatchDraw(canvas: Canvas) {
                    if (!took) {
                        took = true
                        all(item.view).single { it.contentDescription == "Take off" }.performClick()
                        notifyChanges()
                        assertNull(mon.heldItem)
                        assertEquals(before + 1, state.count("soothe-bell"))
                        assertFalse(all(scene).any { it.tag == DetailSection.HELD_ITEM })
                        measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
                        layout(left, top, right, bottom)
                    }
                    super.dispatchDraw(canvas)
                }
            }.apply { addView(scene) }
            paparazzi.snapshot(wrapper, "d-details-taken-off-${theme.name.lowercase()}", 1000)
            assertTrue(took)
            (scene.parent as ViewGroup).removeView(scene)
            // Give through that same item sheet, selecting this Pokémon in the real Box picker.
            BoxMonPicker.interceptShow = { dialog, picker ->
                picker.preset(TeamSection.BOX, group = false, sort = SortSpec.of(SortKey.LEVEL))
                TeamPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(picker)
                val w = ctx.resources.displayMetrics.widthPixels
                val h = ctx.resources.displayMetrics.heightPixels
                picker.root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                picker.root.layout(0, 0, w, h)
                all(picker.root).first { it.contentDescription?.startsWith("${mon.shownName}, level") == true }.performClick()
                dialog.dismiss(); picker.onGone()
            }
            var gave = false
            val given = object : FrameLayout(ctx) {
                override fun dispatchDraw(canvas: Canvas) {
                    if (!gave) {
                        gave = true
                        all(item.view).single { it.contentDescription == "Give to another Pokémon" }.performClick()
                        notifyChanges()
                        assertEquals("soothe-bell", mon.heldItem)
                        assertEquals(before, state.count("soothe-bell"))
                        val sections = all(scene).filter { it.tag is DetailSection }
                        assertEquals(DetailSection.HELD_ITEM, sections.first().tag)
                        assertEquals(1, sections.count { it.tag == DetailSection.HELD_ITEM })
                        measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
                        layout(left, top, right, bottom)
                    }
                    super.dispatchDraw(canvas)
                }
            }.apply { addView(scene) }
            paparazzi.snapshot(given, "d-details-given-${theme.name.lowercase()}", 1000)
            assertTrue(gave)
            item.dismiss(); detail.dismiss(); page.onGone()
        }
    }

    @Test fun longPressPreviewsTmAndTapStillSelectsIt() = paparazzi.shoot("d-teach-tm-preview", listOf(Size.PHONE)) { ctx ->
        val page = TeamPage(ctx, NoHost)
        var picked: String? = null
        var window: ItemWindow? = null
        ItemWindow.interceptShow = { window = it }
        val dialog = DialogShots.capture { Pickers.pickMoveId(page, "Teach Pikachu", GameRepo.data!!, listOf("thunderbolt", "tackle")) { picked = it } }!!
        val list = all(dialog.content()).filterIsInstance<ListView>().single()
        paparazzi.snapshot(DialogShots.scene(ctx, dialog), "d-teach-list-${if (TypeChips.isDark(ctx)) "dark" else "light"}")
        assertTrue(list.onItemLongClickListener!!.onItemLongClick(list, list.adapter.getView(0, null, list), 0, 0))
        assertNull(picked)
        val sheet = checkNotNull(window)
        assertTrue(texts(sheet.view).any { it.contains("Thunderbolt") })
        assertTrue(texts(sheet.view).contains("POWER"))
        assertTrue(texts(sheet.view).contains("90"))
        sheet.dismiss()
        list.performItemClick(list.adapter.getView(1, null, list), 1, 1)
        assertEquals("tackle", picked)
        page.onGone()
        sheet.view
    }

    @Test fun replacementMoveLongPressDoesNotForgetOrTeach() = paparazzi.shoot("d-teach-forget-preview", listOf(Size.PHONE)) { ctx ->
        val page = TeamPage(ctx, NoHost)
        val mon = GameRepo.state!!.main!!
        val before = mon.moves.toList()
        var selected = -1
        var window: ItemWindow? = null
        ItemWindow.interceptShow = { window = it }
        val dialog = DialogShots.capture { Pickers.pickMove(page, "Forget a move", GameRepo.data!!, mon) { selected = it } }!!
        val list = all(dialog.content()).filterIsInstance<ListView>().single()
        assertTrue(list.onItemLongClickListener!!.onItemLongClick(list, list.adapter.getView(0, null, list), 0, 0))
        assertEquals(-1, selected); assertEquals(before, mon.moves)
        val sheet = checkNotNull(window)
        list.performItemClick(list.adapter.getView(0, null, list), 0, 0)
        assertEquals(0, selected)
        sheet.dismiss(); page.onGone()
        sheet.view
    }
}
