package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.help.AnkiDroidFlavour
import com.husarp.ankigo.help.HelpRoute
import com.husarp.ankigo.help.HelpScreen
import com.husarp.ankigo.help.HelpViews
import com.husarp.ankigo.help.PictureFacts
import com.husarp.ankigo.help.PublicFolder
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.AppMenu
import com.husarp.ankigo.ui.FirstRunActivity
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * TICKET R3-V3b: "Set up pictures" (all OK / Play build / many missing / no access / checking), the Shop bar "?", the
 * pictures banner with its Help + Set up pictures buttons, the battle and floating ⋯ menus with Help, Settings → Study
 * "Set up pictures" row, Settings → Ankimon PC sync steps, and the first-run Ankimon steps. PHONE + SPLIT, light + dark.
 */
class R3V3bShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private class ActivityHost(val activity: Activity) : PageHost {
        override val hostActivity: Activity get() = activity
        override fun closePage() = Unit
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
    }

    private val both = listOf(Variants.PHONE, Variants.SPLIT)
    private val long = Variant("long", 412, 1500)

    private fun facts(
        flavour: AnkiDroidFlavour = AnkiDroidFlavour.FULL, folder: PublicFolder = PublicFolder.READABLE, allFiles: Boolean = true,
        own: Boolean = false, api: Boolean = true, total: Int = 20, readable: Int = 20, installed: Boolean = true, sdk: Int = 34,
    ) = PictureFacts(sdk, installed, flavour, folder, allFiles, own, api, total, readable)

    private fun setup(ctx: Context, f: PictureFacts) =
        pageScreen(ctx, HelpScreen(ctx, ActivityHost(attach(Activity(), ctx)), HelpRoute.Setup, true, f))

    @Test fun setupAllOk() = paparazzi.shootPage("r3v3b-setup-ok", both) { setup(it, facts()) }

    @Test fun setupPlayBuild() = paparazzi.shootPage("r3v3b-setup-play", both + long) {
        setup(it, facts(flavour = AnkiDroidFlavour.PLAY, folder = PublicFolder.NOT_FOUND, allFiles = false, total = 12, readable = 0))
    }

    @Test fun setupManyMissing() = paparazzi.shootPage("r3v3b-setup-missing", both + long) { setup(it, facts(total = 20, readable = 7)) }

    @Test fun setupFewMissing() = paparazzi.shootPage("r3v3b-setup-fewmissing", listOf(Variants.PHONE)) { setup(it, facts(total = 40, readable = 39)) }

    @Test fun setupNoAccess() = paparazzi.shootPage("r3v3b-setup-noaccess", both + long) {
        setup(it, facts(folder = PublicFolder.BLOCKED, allFiles = false, api = false, total = 0, readable = 0))
    }

    @Test fun setupNoAnkiDroid() = paparazzi.shootPage("r3v3b-setup-noanki", listOf(Variants.PHONE)) {
        setup(it, facts(installed = false, flavour = AnkiDroidFlavour.UNKNOWN, folder = PublicFolder.NOT_FOUND, allFiles = false, api = false, total = 0, readable = 0))
    }

    @Test fun setupChecking() = paparazzi.shootPage("r3v3b-setup-checking", listOf(Variants.PHONE)) { ctx ->
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 16), Ui.dp(ctx, 16), 0)
            addView(HelpViews.setupHeader(ctx, null, true) {}, LinearLayout.LayoutParams(-1, -2))
        }
    }

    @Test fun shopBar() = paparazzi.shootPage("r3v3b-shop", both) { ctx -> pageScreen(ctx, ShopPage(ctx, NoHost)) }

    private fun studyScreen(ctx: Context): View {
        val ss = ShotCtx(ctx)
        val act = attach(StudyActivity(), ss)
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
        setField(act, "mediaBlocked", true)
        (getField(act, "subtitle") as TextView).text = "Card 1 · card 8"
        act.showQueueCounts(StudyDeckKit.decks[1], 2)
        call(act, "layout")
        val panel = getField(act, "panel") as com.husarp.ankigo.ui.BattlePanelView
        panel.renderAll(); panel.restoreStudyCharge()
        call(act, "showButtons")
        call(act, "mediaNotice", 3, 0, false)
        return root
    }

    @Test fun banner() {
        paparazzi.shootWith("r3v3b-banner", listOf(Size.PHONE, Size.SPLIT)) { studyScreen(it) }
    }

    @Test fun menus() {
        paparazzi.shootPage("r3v3b-menu-battle", both) { ctx ->
            val b = BattleShotKit.bound(ctx)
            MenuKit.scene(ctx, MenuKit.capture { b.call("showMenu", b.root) })
        }
        paparazzi.shootPage("r3v3b-menu-float", both) { ctx ->
            val m = AppMenu(View(ctx))
            val fv = FloatBattleView(object : android.content.ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }, {}, {}, {})
            fv.fillMenu(m)
            MenuKit.scene(ctx, m)
        }
        BattleShotKit.reset()
    }

    @Test fun settingsStudyRow() = paparazzi.shootPage("r3v3b-settings-study", listOf(long)) { ctx ->
        val sp = SettingsPage(ShotCtx(ctx), NoHost)
        val screen = pageScreen(ShotCtx(ctx), sp)
        sp.open(SettingsSection.STUDY)
        screen
    }

    @Test fun settingsAnkimon() = paparazzi.shootPage("r3v3b-settings-ankimon", both + long) { ctx ->
        val sp = SettingsPage(ShotCtx(ctx), NoHost)
        val screen = pageScreen(ShotCtx(ctx), sp)
        sp.open(SettingsSection.ANKIMON)
        screen
    }

    /** A tapped warning opens its paragraph. */
    @Test fun settingsAnkimonWarningOpen() = paparazzi.shootPage("r3v3b-settings-ankimon-open", listOf(Variants.PHONE)) { ctx ->
        val sp = SettingsPage(ShotCtx(ctx), NoHost)
        val screen = pageScreen(ShotCtx(ctx), sp)
        sp.open(SettingsSection.ANKIMON)
        val warn = findDesc(screen, "Old Ankimon: JSON files instead of a .db")!!
        warn.performClick()
        screen
    }

    private fun findDesc(v: View, d: String): View? {
        if (v.contentDescription?.toString() == d) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findDesc(v.getChildAt(i), d)?.let { return it }
        return null
    }

    private fun firstRun(ctx: Context, open: Boolean): View {
        val a = attach(FirstRunActivity(), ShotCtx(ctx))
        val v = a.buildContent()
        setField(a, "step", 3)
        setField(a, "ankimonSteps", open)
        call(a, "render")
        return v
    }

    @Test fun firstRun() {
        paparazzi.shootPage("r3v3b-firstrun-closed", both) { firstRun(it, false) }
        paparazzi.shootPage("r3v3b-firstrun-steps", both + long) { firstRun(it, true) }
    }
}
