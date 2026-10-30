package com.husarp.ankigo.shots

import android.view.View
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Answer
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.ToastKind
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R22FSwitchNoticeTest {
    @get:Rule val paparazzi = Shots.rule()

    private fun panel(): BattlePanelView {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        GameRepo.state!!.battle!!.rounds = 1
        GameRepo.run(ctx) { it.requestMain("t2") }
        assertEquals("t2", GameRepo.state!!.pendingMain)
        return BattlePanelView(ctx).also {
            it.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
            it.layout(0, 0, 800, 700)
            it.renderAll()
        }
    }

    @Test fun miniNoticeInBattleSizes() =
        paparazzi.shootWith("r22f-switch-mini", listOf(Size.PHONE, Size.SPLIT, Size.FLOATBATTLE)) { ctx ->
            GameRepo.state!!.battle!!.rounds = 1
            GameRepo.run(ctx) { it.requestMain("t2") }
            BattlePanelView(ctx).apply {
                renderAll()
                assertEquals(View.VISIBLE, switchNotice.visibility)
            }
        }

    @Test fun longMessageThenMiniAndCancelKeepsCurrentMain() {
        val p = panel()
        val main = GameRepo.state!!.main!!.uid
        assertEquals(View.VISIBLE, p.switchNotice.visibility)
        p.toast(ToastKind.SWITCH, "Switching to Puddlet on your next move.", pop = false)
        assertNotNull(p.toastLayer.shown)
        assertEquals(View.GONE, p.switchNotice.visibility)
        p.toastLayer.dismiss()
        assertEquals(View.VISIBLE, p.switchNotice.visibility)
        assertTrue(p.switchNotice.contentDescription.contains(GameRepo.state!!.mon("t2")!!.name))
        p.switchNotice.getChildAt(2).performClick()
        assertNull(GameRepo.state!!.pendingMain)
        assertEquals(main, GameRepo.state!!.main!!.uid)
        assertEquals(View.GONE, p.switchNotice.visibility)
    }

    @Test fun teamPreviewPlayerTapOpensDetailsWithoutExpandingBattle() {
        val p = panel()
        var expansions = 0
        val host = object : com.husarp.ankigo.ui.BattleCard.Host {
            override fun expand() { expansions++ }
            override fun popOut() {}
        }
        val live = object : com.husarp.ankigo.ui.BattleCard.Live {
            override val paused = false
            override fun hold(ctx: android.content.Context, owner: Any) {}
            override fun release(ctx: android.content.Context, owner: Any) {}
            override fun addSink(s: com.husarp.ankigo.live.LiveSession.Sink) {}
            override fun removeSink(s: com.husarp.ankigo.live.LiveSession.Sink) {}
            override fun addPauseListener(l: (Boolean) -> Unit) {}
            override fun removePauseListener(l: (Boolean) -> Unit) {}
        }
        val card = com.husarp.ankigo.ui.BattleCard(p.context, host, live) { null }
        val preview = com.husarp.ankigo.ui.BattleCard::class.java.getDeclaredField("panel").let {
            it.isAccessible = true
            it.get(card) as BattlePanelView
        }
        val dialog = DialogShots.capture { preview.arena.onPlayerTap() }
        assertNotNull(dialog)
        assertEquals(0, expansions)
        preview.arena.performClick()
        assertEquals(1, expansions)
    }

    @Test fun newPanelRestoresQueuedNoticeAndAnswerRemovesIt() {
        val p = panel()
        val restored = BattlePanelView(p.context)
        restored.renderAll()
        assertEquals(View.VISIBLE, restored.switchNotice.visibility)
        GameRepo.run(p.context) { it.onAnswer(Answer(922001L, 3, System.currentTimeMillis())) }
        restored.renderAll()
        assertNull(GameRepo.state!!.pendingMain)
        assertEquals("t2", GameRepo.state!!.main!!.uid)
        assertEquals(View.GONE, restored.switchNotice.visibility)
    }
}
