package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.update.Updates
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * The floating update banner (UpdateOverlay) over the battle screen, the Team page, the study screen and Settings, at
 * PHONE and SPLIT, Idle and Downloading; and the battle in picture-in-picture with no banner. The banner is drawn at its
 * final (slid-in) position. PNGs: banner-<screen>-<idle|dl>-<phone|split>-<light|dark>, banner-pip-*.
 */
class BannerShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val sizes = listOf(Size.PHONE, Size.SPLIT)
    private val dl = Updates.Phase.Downloading(66, 1_468_006, 2_202_009, 1700)

    private fun battle(ctx: Context) = BattleShotKit.withBanner(ctx, BattleShotKit.bound(ctx).root)
    private fun team(ctx: Context) = BattleShotKit.withBanner(ctx, pageScreen(ctx, TeamPage(ctx, NoHost)))
    private fun settings(ctx: Context) = ShotCtx(ctx).let { BattleShotKit.withBanner(ctx, pageScreen(it, SettingsPage(it, NoHost))) }

    private fun study(ctx: Context): View {
        val ss = ShotCtx(ctx)
        val act = attach(StudyActivity(), ss)
        setField(act, "deckId", 2L)
        setField(act, "deckName", "Geography::Capitals")
        setField(act, "heightMode", 1)
        val root = act.buildContent()
        setField(act, "card", com.husarp.ankigo.Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
        setField(act, "question", "<div>Capital of Australia?</div>")
        setField(act, "answerHtml", "<div>Canberra</div>")
        setField(act, "modelName", "Basic")
        setField(act, "cardName", "Card 1")
        setField(act, "flipped", false)
        setField(act, "locked", false)
        (getField(act, "subtitle") as TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as com.husarp.ankigo.ui.BattlePanelView
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        return BattleShotKit.withBanner(ctx, root)
    }

    @Test fun idle() {
        BattleShotKit.setUpdates(BattleShotKit.newer())
        paparazzi.shootWith("banner-battle-idle", sizes) { battle(it) }
        paparazzi.shootPage("banner-team-idle", listOf(Variants.PHONE, Variants.SPLIT)) { team(it) }
        paparazzi.shootWith("banner-study-idle", sizes) { study(it) }
        paparazzi.shootPage("banner-settings-idle", listOf(Variants.PHONE, Variants.SPLIT)) { settings(it) }
    }

    @Test fun downloading() {
        BattleShotKit.setUpdates(BattleShotKit.newer(dl))
        paparazzi.shootWith("banner-battle-dl", sizes) { battle(it) }
        paparazzi.shootPage("banner-team-dl", listOf(Variants.PHONE, Variants.SPLIT)) { team(it) }
        paparazzi.shootWith("banner-study-dl", sizes) { study(it) }
        paparazzi.shootPage("banner-settings-dl", listOf(Variants.PHONE, Variants.SPLIT)) { settings(it) }
    }

    /** The battle in picture-in-picture while a newer version is known: AgActivity suppresses the banner there. */
    @Test fun pipShowsNoBanner() = paparazzi.shootWith("banner-pip", listOf(Size.PIP)) { ctx ->
        BattleShotKit.setUpdates(BattleShotKit.newer())
        val b = BattleShotKit.bound(ctx)
        BattleShotKit.pip(b)
        val o = BattleShotKit.withBanner(ctx, b.root) as com.husarp.ankigo.update.UpdateOverlay
        o.banner.suppressed = true
        o
    }
}
