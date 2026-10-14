package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.BattlePanelView
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * Status pills (BRN PAR SLP PSN FRZ) on both info cards in every panel mode.
 * PNGs: status-parbrn, status-slppsn, status-frz (full panel, PHONE + SPLIT), status-longnames (dual long types, long
 * name), status-study (study strip), status-float (floating battle), status-pip (picture-in-picture).
 */
class StatusShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val extraSpecies = ArrayList<Int>()

    /** The shared fake world must stay as other shots expect it. */
    @After fun cleanUp() {
        @Suppress("UNCHECKED_CAST") val m = FakeGame.data.species as MutableMap<Int, *>
        extraSpecies.forEach { m.remove(it) }
        extraSpecies.clear()
        BattleShotKit.reset()
    }

    private fun set(enemy: String?, player: String?) {
        val b = GameRepo.state!!.battle!!
        b.enemy.status = enemy
        b.player.status = player
    }

    /** A wild Electric/Fighting one (the two longest type labels) with a long name. */
    private fun longWild(name: String) {
        val sp = TestData.species(998, name, listOf(Type.ELECTRIC, Type.FIGHTING))
        @Suppress("UNCHECKED_CAST") (FakeGame.data.species as MutableMap<Int, com.husarp.ankigo.game.Species>)[998] = sp
        extraSpecies += 998
        val st = GameRepo.state!!
        val w = TestData.mon(FakeGame.data, 998, 25, listOf("tackle"), "wild")
        w.hp = w.hp * 7 / 10
        st.battle = com.husarp.ankigo.game.Battle(w, com.husarp.ankigo.game.Tier.NORMAL, answers = 1, tally = 10, rounds = 2, startedAt = 1L)
    }

    private val both = listOf(Size.PHONE, Size.SPLIT)

    @Test fun parBrn() = paparazzi.shootWith("status-parbrn", both) { ctx ->
        val b = BattleShotKit.bound(ctx); set("par", "brn"); b.panel!!.renderAll(); b.root
    }

    @Test fun slpPsn() = paparazzi.shootWith("status-slppsn", both) { ctx ->
        val b = BattleShotKit.bound(ctx); set("slp", "tox"); b.panel!!.renderAll(); b.root
    }

    @Test fun frz() = paparazzi.shootWith("status-frz", both) { ctx ->
        val b = BattleShotKit.bound(ctx); set("frz", "slp"); b.panel!!.renderAll(); b.root
    }

    @Test fun longNames() = paparazzi.shootWith("status-longnames", both) { ctx ->
        val b = BattleShotKit.bound(ctx); longWild("Electrofighter"); set("par", "psn"); b.panel!!.renderAll(); b.root
    }

    @Test fun pip() = paparazzi.shootWith("status-pip", listOf(Size.PIP)) { ctx ->
        val b = BattleShotKit.bound(ctx)
        BattleShotKit.pip(b)
        set("par", "brn"); b.panel!!.renderAll(); b.root
    }

    @Test fun pipPaused() = paparazzi.shootWith("status-pip-paused", listOf(Size.PIP)) { ctx ->
        val b = BattleShotKit.bound(ctx)
        BattleShotKit.pip(b, paused = true)
        set("slp", "psn"); b.panel!!.renderAll(); b.root
    }

    // ---------------------------------------------------------------- study strip

    private fun study(ctx: Context): View {
        val act = attach(StudyActivity(), ShotCtx(ctx))
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        setField(act, "heightMode", 1)
        val root = act.buildContent()
        setField(act, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
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
        set("par", "brn")
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        return root
    }

    @Test fun studyStrip() = paparazzi.shootWith("status-study", both) { study(it) }

    // ---------------------------------------------------------------- floating battle

    private fun field(o: Any, name: String): Any? = o.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(o) }

    private fun float(ctx: Context): View {
        val self = object : android.content.ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        v.javaClass.getDeclaredMethod("bind").also { it.isAccessible = true }.invoke(v)
        set("frz", "slp")
        (field(v, "panel") as BattlePanelView).renderAll()
        Thread.sleep(400)
        return field(field(v, "window")!!, "root") as View
    }

    @Test fun floatBattle() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.FLOATBATTLE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val frame = FrameLayout(ctx).apply { addView(float(ctx), FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "status-float-floatbattle-${theme.name.lowercase()}", 1000)
        }
    }
}
