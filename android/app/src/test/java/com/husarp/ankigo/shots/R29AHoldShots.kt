package com.husarp.ankigo.shots

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R29AHoldShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun shopAndBuy() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx); ShopCardKit.install()
            val (page, stage) = ShopCardKit.page(ctx, null)
            assertTrue(all(stage).any { it.contentDescription?.startsWith("Hold to refresh shop") == true })
            paparazzi.snapshot(stage, "a-shop-${theme.name.lowercase()}")
            page.openCard(CardKeys.basic(ShopPlan.Tile.Item("potion"))); stage.render()
            assertTrue(all(stage).filterIsInstance<TextView>().any { it.text.toString() == "Hold to buy" })
            paparazzi.snapshot(stage, "a-buy-${theme.name.lowercase()}")
            val buy = all(stage).single { it.contentDescription?.startsWith("Hold to buy.") == true }
            for (percent in listOf(0, 25, 50, 75, 100)) {
                buy.background.level = percent * 100
                paparazzi.snapshot(stage, "a-hold-buy-${theme.name.lowercase()}-$percent")
            }
            page.closeCard(); page.onGone()
        }
    }

    @Test fun readyRowAndEvolutionSheet() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val e = Evolution(135, EvoKind.ITEM, item = "thunder-stone")
            val root = TestData.species(133, "Eevee", evolutions = listOf(e))
            val data = TestData.data(listOf(root, TestData.species(135, "Jolteon", prevo = 133)))
            val state = TestData.state(data, 133, 20).apply { bag["thunder-stone"] = 1; main!!.bondPoints = 500 }
            var evolved = 0
            val rows = EvolutionRows(ctx, data, EvoLine.of(data, root).root, root, setOf(133, 135), null, state, state.main) { evolved++ }
            assertEquals(1, all(rows).count { it.contentDescription == "Ready to evolve" })
            paparazzi.snapshot(rows, "a-evolution-row-${theme.name.lowercase()}")
            var dialog: AppDialog? = null
            Pickers.interceptApp = { dialog = it }
            try {
                all(rows).single { it.contentDescription?.startsWith("Explain Thunder Stone") == true }.performClick()
                val sheet = dialog!!.content()
                assertEquals("This can't be undone.", all(sheet).filterIsInstance<TextView>().last { it.text.toString().contains("undone") }.text.toString())
                val go = dialog!!.button(AppDialog.BUTTON_POSITIVE)!!
                assertEquals("Hold to evolve", go.text.toString())
                paparazzi.snapshot(sheet, "a-evolve-sheet-${theme.name.lowercase()}")
                for (percent in listOf(0, 25, 50, 75, 100)) {
                    go.background.level = percent * 100
                    paparazzi.snapshot(sheet, "a-hold-evolve-${theme.name.lowercase()}-$percent")
                }
                assertTrue(go.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK, null)); assertEquals(1, evolved)
            } finally { dialog?.dismiss(); Pickers.interceptApp = null }
            state.bag.clear()
            val unavailable = EvolutionRows(ctx, data, EvoLine.of(data, root).root, root, setOf(133), null, state, state.main) {}
            assertFalse(all(unavailable).any { it.contentDescription == "Ready to evolve" })
            val missing = DialogShots.capture {
                all(unavailable).single { it.contentDescription?.startsWith("Explain Thunder Stone") == true }.performClick()
            }!!
            assertFalse(missing.button(AppDialog.BUTTON_POSITIVE)!!.isEnabled)
            assertTrue(all(missing.content()).filterIsInstance<TextView>().any { it.text.toString() == "This can't be undone." })
            paparazzi.snapshot(missing.content(), "a-evolve-unavailable-${theme.name.lowercase()}")
            missing.dismiss()
        }
    }

    @Test fun pokemonDetails() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val previous = DetailSections.storeOf
            DetailSections.storeOf = { object : DetailOpenStore {
                override fun get(key: String): Boolean? = key == "evolution"
                override fun set(key: String, open: Boolean) = Unit
            } }
            try {
                val data = com.husarp.ankigo.GameRepo.data!!
                val state = com.husarp.ankigo.GameRepo.state!!
                val mon = state.mons.first { it.species == 25 }.apply { bondPoints = 500 }
                state.bag["thunder-stone"] = 1
                val dialog = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), data, mon) }!!
                val scene = DialogShots.scene(ctx, dialog)
                assertTrue(all(scene).any { it.contentDescription == "Ready to evolve" })
                paparazzi.snapshot(scene, "a-pokemon-details-${theme.name.lowercase()}")
                dialog.dismiss()
            } finally { DetailSections.storeOf = previous }
        }
    }

    @Test fun holdSheetPreservesInformationAndRequiresConfirmation() {
        val ctx = Shots.files(paparazzi.context)
        for (action in listOf("Release", "Teach", "Use", "Revive", "Throw")) {
            var count = 0
            val message = "Action details for $action. This can't be undone."
            val dialog = DialogShots.capture { Pickers.hold(ctx, action, message, action) { count++ } }!!
            assertTrue(all(dialog.content()).filterIsInstance<TextView>().any { it.text.toString() == message })
            val button = dialog.button(AppDialog.BUTTON_POSITIVE)!!
            assertEquals("Hold to ${action.lowercase()}", button.text.toString())
            button.performClick(); assertEquals(0, count)
            button.performLongClick(); assertEquals(1, count)
        }
    }

    @Test fun releaseKeepsBothWarningsAndWaitsForHold() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val state = com.husarp.ankigo.GameRepo.state!!
            val mon = state.boxMons().first()
            val actions = MonActions(TeamPage(ctx, NoHost))
            val release = MonActions::class.java.getDeclaredMethod("release", Mon::class.java).apply { isAccessible = true }
            val dialog = DialogShots.capture { release.invoke(actions, mon) }!!
            val message = all(dialog.content()).filterIsInstance<TextView>().map { it.text.toString() }.joinToString("\n")
            assertTrue(message.contains("Lv ${mon.level}"))
            assertTrue(message.contains("gone for good"))
            assertTrue(message.contains("This can't be undone."))
            paparazzi.snapshot(dialog.content(), "a-release-${theme.name.lowercase()}")
            dialog.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
            assertTrue(mon in state.mons)
            dialog.button(AppDialog.BUTTON_POSITIVE)!!.performLongClick()
            assertFalse(mon in state.mons)
        }
    }

    @Test fun teachingWaitsForHoldAndCancelKeepsMoves() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx); ShopCardKit.install()
        val state = com.husarp.ankigo.GameRepo.state!!
        val mon = state.mons.first { it.species == 25 }
        mon.moves.remove("thunderbolt")
        state.tms.add("thunderbolt")
        val before = mon.moves.toList()
        val actions = MonActions(TeamPage(ctx, NoHost))
        var dialog = DialogShots.capture { actions.teach(mon, "thunderbolt") }!!
        dialog.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
        assertEquals(before, mon.moves)
        dialog.button(AppDialog.BUTTON_NEGATIVE)!!.performClick()
        assertEquals(before, mon.moves)
        dialog = DialogShots.capture { actions.teach(mon, "thunderbolt") }!!
        dialog.button(AppDialog.BUTTON_POSITIVE)!!.performLongClick()
        assertTrue("thunderbolt" in mon.moves)
    }

    @Test fun fullHoldConfirmsExactlyOnce() {
        val button = TextView(Shots.files(paparazzi.context))
        var count = 0
        var now = 0L
        val pending = linkedMapOf<Runnable, Long>()
        val timer = object : HoldToConfirm.Timer {
            override fun post(task: Runnable, delayMillis: Long) { pending[task] = now + delayMillis }
            override fun remove(task: Runnable) { pending.remove(task) }
        }
        fun advance(ms: Long) {
            now += ms
            pending.filterValues { it <= now }.keys.toList().forEach { pending.remove(it); it.run() }
        }
        fun touch(action: Int) {
            val event = MotionEvent.obtain(0, now, action, 10f, 10f, 0)
            button.dispatchTouchEvent(event); event.recycle()
        }
        HoldToConfirm.bind(button, "test", timer = timer) { count++ }
        button.layout(0, 0, 200, 60)
        touch(MotionEvent.ACTION_DOWN); advance(999); assertEquals(0, count)
        advance(1); assertEquals(1, count)
        advance(1000); touch(MotionEvent.ACTION_UP); assertEquals(1, count)
        touch(MotionEvent.ACTION_DOWN); advance(500); touch(MotionEvent.ACTION_UP)
        advance(1000); assertEquals(1, count)
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_CANCEL)
        advance(1000); assertEquals(1, count)
        touch(MotionEvent.ACTION_DOWN); advance(500)
        val focus = button.viewTreeObserver.javaClass.getDeclaredMethod("dispatchOnWindowFocusChange", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        focus.invoke(button.viewTreeObserver, false)
        assertTrue(pending.isEmpty())
        assertEquals(0, button.background.level)
        advance(1000); assertEquals(1, count)
        focus.invoke(button.viewTreeObserver, true)
        touch(MotionEvent.ACTION_DOWN); advance(1000); assertEquals(2, count)
    }

    @Test fun earlyReleaseCancelDisabledAndAccessibility() {
        val ctx = Shots.files(paparazzi.context)
        val button = TextView(ctx)
        var count = 0
        var allowed = true
        HoldToConfirm.bind(button, "test", { allowed }) { count++ }
        button.layout(0, 0, 200, 60)
        fun touch(action: Int) {
            val event = MotionEvent.obtain(0, 0, action, 20f, 20f, 0)
            button.dispatchTouchEvent(event); event.recycle()
        }
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_UP)
        assertEquals(0, count)
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_CANCEL)
        assertEquals(0, count)
        allowed = false; button.performLongClick(); assertEquals(0, count)
        allowed = true; button.performLongClick(); assertEquals(1, count)
        button.isEnabled = false; button.performLongClick(); assertEquals(1, count)
        assertEquals(1000L, HoldToConfirm.DURATION_MS)
    }
}
