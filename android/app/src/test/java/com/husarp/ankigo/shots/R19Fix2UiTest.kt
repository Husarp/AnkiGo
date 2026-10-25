package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.*
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

class R19Fix2UiTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun ctx() = Shots.files(paparazzi.context)
    private fun size(v: View, w: Int = 500, h: Int = 400) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
    }
    @Test fun oneWindowHookInEitherOrderAndRestoredAfterBothClose() {
        for (reverse in listOf(false, true)) {
            val c = ctx()
            val activity = attach(Activity(), c)
            val window = Class.forName("com.android.internal.policy.PhoneWindow").getConstructor(Context::class.java).newInstance(activity) as Window
            Activity::class.java.getDeclaredField("mWindow").apply { isAccessible = true }.set(activity, window)
            val original = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Window.Callback::class.java)) { _, method, _ ->
                if (method.returnType == Boolean::class.javaPrimitiveType) false else null
            } as Window.Callback
            window.callback = original
            val view = View(activity)
            var touches = 0
            var backs = 0
            lateinit var a: BubbleDismiss.Armed
            lateinit var b: BubbleDismiss.Armed
            a = BubbleDismiss.Armed({ _, _ -> touches++; BubbleDismiss.disarm(view, a) }, { backs++; BubbleDismiss.disarm(view, a) })
            b = BubbleDismiss.Armed({ _, _ -> touches++; BubbleDismiss.disarm(view, b) }, { backs++; BubbleDismiss.disarm(view, b) })
            val order = if (reverse) listOf(b, a) else listOf(a, b)
            BubbleDismiss.arm(view, order[0]); val hook = window.callback
            BubbleDismiss.arm(view, order[1]); assertSame(hook, window.callback)
            val e = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 2f, 2f, 0)
            window.callback.dispatchTouchEvent(e); e.recycle()
            assertEquals(2, touches); assertSame(original, window.callback)
            order.forEach { BubbleDismiss.arm(view, it) }
            window.callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            assertEquals(2, backs); assertSame(original, window.callback)
        }
    }
    @Test fun floatingRootWatchesOutsideTouchesAndRemovesItsWatcher() {
        val c = ctx()
        val root = FrameLayout(c).apply { setPadding(20, 20, 20, 20) }
        val view = View(c)
        root.addView(view, FrameLayout.LayoutParams(50, 50))
        var closed = 0
        lateinit var a: BubbleDismiss.Armed
        a = BubbleDismiss.Armed({ _, _ -> closed++; BubbleDismiss.disarm(view, a) }, {})
        BubbleDismiss.arm(view, a)
        assertEquals(2, root.childCount)
        size(root)
        val e = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 2f, 2f, 0)
        root.dispatchTouchEvent(e); e.recycle()
        assertEquals(1, closed); assertEquals(1, root.childCount)
    }
    @Test fun conditionTargetsAre48DpTallAndAdjacentRowsDoNotOverlap() {
        val c = ctx()
        val v = ConditionMarksView(c)
        val m = ChipMetrics(c, 10.5f)
        size(v)
        val x = Ui.dp(c, 10f)
        val y = Ui.dp(c, 70f)
        fun row(slot: ConditionMarks.Slot, top: Float) {
            v.setRow(slot, ConditionSpot.Placed(ConditionSpot.R(x, top, x + 50, top + m.height), ConditionRow.Fit(0, 1, 50f), true), emptyList(), m)
        }
        row(ConditionMarks.Slot.WILD, y)
        val mid = y + m.height / 2
        assertEquals(ConditionMarks.Slot.WILD, v.slotAt(x + 1, mid - Ui.dp(c, 23f)))
        assertEquals(ConditionMarks.Slot.WILD, v.slotAt(x + 1, mid + Ui.dp(c, 23f)))
        assertNull(v.slotAt(x + 1, mid + Ui.dp(c, 25f)))
        row(ConditionMarks.Slot.OURS, y + Ui.dp(c, 30f))
        assertEquals(ConditionMarks.Slot.WILD, v.slotAt(x + 1, mid + Ui.dp(c, 14f)))
        assertEquals(ConditionMarks.Slot.OURS, v.slotAt(x + 1, mid + Ui.dp(c, 16f)))
    }
    @Test fun staleToastWaitingForSizeIsDroppedButFreshToastShows() {
        val layer = ArenaToastLayer(ctx())
        var now = 100L
        layer.clock = { now }
        layer.show(ToastKind.SWITCH, "Switching after this card.", pop = false)
        now += 5001
        size(layer)
        assertNull(layer.shown)
        layer.show(ToastKind.NEWS, "Ready.", pop = false)
        assertEquals("Ready.", layer.shown?.text)
    }
    @Test fun toastRepositionsWithoutLayoutOrRepeatingItsAnnouncement() {
        val layer = ArenaToastLayer(ctx())
        size(layer)
        layer.arenaRect = { Rect(0, 0, 500, 400) }
        var announces = 0
        layer.announce = { announces++ }
        layer.show(ToastKind.NEWS, "Ready.", pop = false)
        val old = layer.shownCard!!
        layer.keepOff = { KeepOff(FloatSpot.Rect(0, 300, 200, 400), FloatSpot.Rect(300, 300, 500, 400),
            FloatSpot.Rect(0, 250, 100, 300), FloatSpot.Rect(400, 250, 500, 300), extra = listOf(FloatSpot.Rect(0, 0, 250, 100))) }
        layer.reposition()
        assertNotSame(old, layer.shownCard)
        assertTrue(layer.shownCard!!.left != old.left || layer.shownCard!!.top != old.top)
        assertEquals(1, announces)
    }
    @Test fun hpModeReadsOnlyItsStringAndToleratesAnOldWrongType() {
        val base = ctx()
        val stored = base.getSharedPreferences("ankigo_ui", 0)
        val wrapped = object : android.content.ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                object : android.content.SharedPreferences by stored {
                    override fun getAll(): MutableMap<String, *> = error("must not copy the preference map")
                }
        }
        stored.edit().putString(HpColorPrefs.KEY, HpColors.Mode.C.name).commit()
        assertEquals(HpColors.Mode.C, HpColorPrefs.mode(wrapped))
        stored.edit().putInt(HpColorPrefs.KEY, 0).commit()
        assertEquals(HpColors.Mode.B, HpColorPrefs.mode(wrapped))
    }

    @Test fun rowVisibilityIsSettledDuringMeasureAndCanGrowAgain() {
        val c = ctx()
        val summary = CatchSummary(true, "Pidgey", CatchSummary.PokedexLine(true, 16), CatchSummary.ThrowLine("poke-ball", 1, 63),
            listOf(CatchSummary.XpLine("a", "Bulbasaur", 30, 7, true)), 25, "Next: Rattata")
        val tiles = CatchTilesView(c).also { it.bindRow(summary, false, Ui.dp(c, 26), 12f) }
        val row = tiles.getChildAt(0) as ViewGroup
        fun measure(w: Int) = tiles.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(c, w), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        measure(100)
        val small = (0 until row.childCount).filter { row.getChildAt(it).visibility == View.VISIBLE }
        assertTrue(small.isNotEmpty())
        assertTrue(small.size < row.childCount)
        measure(800)
        val big = (0 until row.childCount).filter { row.getChildAt(it).visibility == View.VISIBLE }
        assertTrue(big.isNotEmpty())
        assertNotEquals(small, big)
    }
    @Test fun changingConditionsReplacesTheShowingToastImmediately() {
        val c = ctx()
        FakeGame.install(c)
        val battle = com.husarp.ankigo.GameRepo.state!!.battle!!
        battle.enemy.volatiles.clear()
        val panel = BattlePanelView(c).also { it.setStudyLayout(false, false); it.renderAll() }
        panel.arena.setEnemyCardShown(true)
        val frame = FrameLayout(c).apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }
        size(frame, Ui.dp(c, 412), Ui.dp(c, 400))
        panel.arena.requestLayout()
        size(frame, Ui.dp(c, 412), Ui.dp(c, 400))
        panel.toast(ToastKind.NEWS, "Ready.", pop = false)
        val before = panel.toastLayer.shownCard!!
        battle.enemy.volatiles["confusion"] = 3
        panel.arena.syncConditions(battle)
        assertTrue(panel.arena.markRects().isNotEmpty())
        assertNotSame(before, panel.toastLayer.shownCard)
    }
}
