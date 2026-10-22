package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.DiagnosticsActivity
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.ankimon.AnkimonMon
import com.husarp.ankigo.game.ankimon.AnkimonSave
import com.husarp.ankigo.ui.AnkimonImportFlow
import com.husarp.ankigo.ui.DexDetail
import com.husarp.ankigo.ui.DexFilter
import com.husarp.ankigo.ui.DexOwned
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.FirstRunActivity
import com.husarp.ankigo.ui.FirstRunJob
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.Pickers
import com.husarp.ankigo.ui.SettingsJobs
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.ShopPage
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.update.UpdateSection
import com.husarp.ankigo.update.Updates
import org.junit.Rule
import org.junit.Test

/*
 * Pages and dialogs: Team, Pokedex (+ detail), Shop (shop + bag), Settings, Diagnostics, First run, Updates and the popups.
 * Light + dark everywhere; scrolling pages also at TALL (412 x 2200 dp) so the whole content shows. Dialogs are taken
 * from Pickers (intercept hook) and put on a 50 % scrim with the same rounded card Pickers.show applies.
 */
class PagesShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val all = listOf(Variants.PHONE, Variants.SPLIT, Variants.TALL)
    private val phoneTall = listOf(Variants.PHONE, Variants.TALL)
    private val phoneSplit = listOf(Variants.PHONE, Variants.SPLIT)

    // ---------- 1. Team ----------

    @Test fun team() {
        paparazzi.shootPage("pages-team", all) { ctx -> pageScreen(ctx, TeamPage(ctx, NoHost)) }
        paparazzi.shootPage("pages-team-empty", all, scenario = FakeGame.Scenario.EMPTY) { ctx -> pageScreen(ctx, TeamPage(ctx, NoHost)) }
        paparazzi.shootPage("pages-team-nosprites", all, withSprites = false) { ctx -> pageScreen(ctx, TeamPage(ctx, NoHost)) }
    }

    // ---------- 2. Pokedex ----------

    /**
     * Sets the Pokedex filter by reflection, whatever the page keeps it as: a String key ("caught", "missing", "wish", "gen1")
     * in the original page, a DexFilter (owned / gens / types / wishlist / search) once the page has the filter bar.
     * Returns false when the page has no such filter (nothing set).
     */
    private fun setDexFilter(page: DexPage, kind: String): Boolean {
        val f = DexPage::class.java.getDeclaredField("filter").apply { isAccessible = true }
        if (f.type == String::class.java) {
            if (kind == "search" || kind == "types") return false
            f.set(page, kind); return true
        }
        val owned = if (kind == "caught") DexOwned.OWNED else if (kind == "missing") DexOwned.NOT_OWNED else DexOwned.ALL
        f.set(page, DexFilter(
            owned = owned,
            gens = if (kind == "gen1") setOf(1) else emptySet(),
            types = if (kind == "types") setOf(com.husarp.ankigo.game.Type.WATER, com.husarp.ankigo.game.Type.ELECTRIC) else emptySet(),
            wishlistOnly = kind == "wish",
            query = if (kind == "search") "spark" else "",
        ))
        return true
    }

    private fun dex(ctx: Context, filter: String?): View {
        val p = DexPage(ctx, NoHost)
        if (filter != null && !setDexFilter(p, filter)) error("no filter $filter")
        return pageScreen(ctx, p)
    }

    private fun hasFilterBar() = DexPage::class.java.getDeclaredField("filter").type != String::class.java

    @Test fun dex() {
        paparazzi.shootPage("pages-dex", all) { dex(it, null) }
        paparazzi.shootPage("pages-dex-caught", phoneTall) { dex(it, "caught") }
        paparazzi.shootPage("pages-dex-missing", phoneTall) { dex(it, "missing") }
        paparazzi.shootPage("pages-dex-wish", listOf(Variants.PHONE)) { dex(it, "wish") }
        paparazzi.shootPage("pages-dex-gen1", listOf(Variants.PHONE)) { dex(it, "gen1") }
        if (hasFilterBar()) { // search box + sort + type chips exist only in the redesigned page
            paparazzi.shootPage("pages-dex-search", phoneTall) { dex(it, "search") }
            paparazzi.shootPage("pages-dex-types", phoneTall) { dex(it, "types") }
        }
        paparazzi.shootPage("pages-dex-nosprites", all, withSprites = false) { dex(it, null) }
        paparazzi.shootPage("pages-dex-empty", phoneTall, scenario = FakeGame.Scenario.EMPTY) { dex(it, null) }
    }

    // ---------- 5. Shop ----------

    private fun shop(ctx: Context, bag: Boolean): View {
        val p = ShopPage(ctx, NoHost)
        if (bag) setField(p, "showBag", true)
        return pageScreen(ctx, p)
    }

    @Test fun shop() {
        paparazzi.shootPage("pages-shop", all) { shop(it, false) }
        paparazzi.shootPage("pages-shop-nosprites", phoneTall, withSprites = false) { shop(it, false) }
        paparazzi.shootPage("pages-bag", all) { shop(it, true) }
        paparazzi.shootPage("pages-bag-empty", listOf(Variants.PHONE), scenario = FakeGame.Scenario.EMPTY) { shop(it, true) }
    }

    // ---------- 6. Settings ----------

    private fun settings(ctx: Context): View = pageScreen(ShotCtx(ctx), SettingsPage(ShotCtx(ctx), NoHost))

    @Test fun settings() {
        paparazzi.shootPage("pages-settings", listOf(Variants.PHONE, Variants.TALL_SETTINGS)) { settings(it) }
        paparazzi.shootPage("pages-settings-nosprites", listOf(Variants.TALL_SETTINGS), withSprites = false) { settings(it) }
        // data card with both long downloads shown: everything running, game data failed
        SettingsJobs.everything.begin("Starting…"); SettingsJobs.everything.progress(0.42f, "Downloaded 168 / 400 · 7 MB")
        SettingsJobs.data.begin("Starting…"); SettingsJobs.data.fail("No internet connection.")
        try { paparazzi.shootPage("pages-settings-jobs", listOf(Variants.TALL_SETTINGS)) { settings(it) } }
        finally { SettingsJobs.everything.reset(); SettingsJobs.data.reset() }
    }

    // ---------- 7. Diagnostics ----------

    @Test fun diagnostics() {
        paparazzi.shootPage("pages-diagnostics", listOf(Variants.PHONE)) { ctx ->
            val a = attach(DiagnosticsActivity(), ShotCtx(ctx))
            val content = a.buildContent()
            (getField(a, "header") as android.widget.TextView).text =
                "▶ com.ichi2.anki  v2.24.0 (20240000)  provider=yes spec=2  permission=granted\n" +
                "Target: com.ichi2.anki  ·  API: OK (7 decks readable)\n" +
                "Media: all-files=false folder=false imported=0  ·  AnkiGo reviews today: 86"
            (getField(a, "logView") as android.widget.TextView).text =
                "03:51:02 C12b === AnkiGo started; Android 14 (SDK 34), ExampleCo Phone\n" +
                "03:51:02 C12b ▶ com.ichi2.anki v2.24.0 provider=yes | API: OK (7 decks readable)\n" +
                "03:51:09 C1 OK 7 decks (Default 12/4/30, Geography 3/0/21, ...)\n" +
                "03:51:20 C3 media probe: /storage/emulated/0/AnkiDroid/collection.media not readable"
            content
        }
    }

    // ---------- 8. First run ----------

    private fun firstRun(ctx: Context, step: Int, ankiVersion: String? = "2.24.0", granted: Boolean = true,
                         prepare: (FirstRunActivity) -> Unit = {}): View {
        val a = attach(FirstRunActivity(), ShotCtx(ctx, ankiVersion, granted = granted))
        val v = a.buildContent()
        setField(a, "step", step)
        prepare(a)
        call(a, "render")
        return v
    }

    private fun withJob(setup: () -> Unit, shoot: () -> Unit) {
        FirstRunJob.job.reset(); setup()
        try { shoot() } finally { FirstRunJob.job.reset() }
    }

    @Test fun firstRun() {
        val sizes = phoneSplit
        paparazzi.shootPage("pages-firstrun-noanki", sizes) { firstRun(it, 1, ankiVersion = null) }
        paparazzi.shootPage("pages-firstrun-welcome", sizes) { firstRun(it, 1, granted = false) }
        paparazzi.shootPage("pages-firstrun-denied", sizes) { firstRun(it, 1, granted = false) { a -> setField(a, "denied", true) } }
        withJob({ FirstRunJob.job.begin("Starting…"); FirstRunJob.job.progress(0.45f, "Game data · 1.9 of 4.3 MB · 1.1 MB/s · 2 s left") }) {
            paparazzi.shootPage("pages-firstrun-downloading", sizes) { firstRun(it, 2) }
        }
        withJob({ FirstRunJob.job.begin("Starting…"); FirstRunJob.job.progress(0.9f, "Pictures (total estimated) · 0.8 of 1.0 MB · 600 KB/s") }) {
            paparazzi.shootPage("pages-firstrun-pictures", sizes) { firstRun(it, 2) }
        }
        withJob({ FirstRunJob.job.begin("Starting…"); FirstRunJob.job.fail("Couldn't download the game data. Check the internet connection and try again.") }) {
            paparazzi.shootPage("pages-firstrun-failed", sizes) { firstRun(it, 2) }
        }
        withJob({ FirstRunJob.job.begin("Starting…"); FirstRunJob.job.done() }) {
            // afterLoading = true: the real code then starts GameRepo.load and moves on; keep the "Done" state on screen
            paparazzi.shootPage("pages-firstrun-done", sizes) { firstRun(it, 2) { a -> setField(a, "afterLoading", true) } }
        }
        paparazzi.shootPage("pages-firstrun-starter", sizes) { firstRun(it, 3) }
        paparazzi.shootPage("pages-firstrun-starter-picked", sizes) { firstRun(it, 3) { a -> setField(a, "selNum", 1) } }
        paparazzi.shootPage("pages-firstrun-split", sizes) { firstRun(it, 4) }
        paparazzi.shootPage("pages-firstrun-saveproblem", sizes) { firstRun(it, 5) }
    }

    // ---------- 9. Updates screen ----------

    private val latest = Updates.Latest("0.4.3", "AnkiGo-0.4.3.apk", "https://example.invalid/AnkiGo-0.4.3.apk", 2_202_009L, "https://example.invalid/release")

    private fun setUpdates(s: Updates.State) {
        val f = Updates::class.java.getDeclaredField("state"); f.isAccessible = true; f.set(Updates, s)
    }

    private fun updatesScreen(name: String, s: Updates.State) {
        setUpdates(s)
        try {
            paparazzi.shootPage(name, listOf(Variants.PHONE)) { ctx -> com.husarp.ankigo.update.updatesScreen(ShotCtx(ctx)) }
        } finally { setUpdates(Updates.State()) }
    }

    @Test fun updates() {
        val dl = Updates.Phase.Downloading(percent = 66, done = 1_468_006, total = 2_202_009, elapsedMs = 1700)
        updatesScreen("pages-updates-uptodate", Updates.State(latest = latest.copy(version = "0.4.2"), message = "AnkiGo is up to date."))
        updatesScreen("pages-updates-available", Updates.State(latest = latest))
        updatesScreen("pages-updates-downloading", Updates.State(latest = latest, phase = dl))
        updatesScreen("pages-updates-needpermission", Updates.State(latest = latest, phase = Updates.Phase.NeedsPermission))
        updatesScreen("pages-updates-failed", Updates.State(latest = latest, phase = Updates.Phase.Failed("No internet connection.")))
    }

    // ---------- readable slices of the long pages ----------
    // The TALL shots are downscaled to 1000 px high (unreadable); these show the same page in phone-sized slices (name-s0, -s1, ...).

    private fun slice(ctx: Context, page: View, tallDp: Int, offsetDp: Int): View = android.widget.FrameLayout(ctx).apply {
        addView(page, android.widget.FrameLayout.LayoutParams(-1, Ui.dp(ctx, tallDp)).also { })
        page.translationY = -Ui.dp(ctx, offsetDp).toFloat()
    }

    private fun slices(name: String, tallDp: Int, withSprites: Boolean = true, scenario: FakeGame.Scenario = FakeGame.Scenario.MID_GAME, build: (Context) -> View) {
        for (k in 0 until (tallDp + 799) / 800) {
            paparazzi.shootPage("$name-s$k", listOf(Variants.PHONE), scenario, withSprites) { ctx -> slice(ctx, build(ctx), tallDp, k * 800) }
        }
    }

    @Test fun slices() {
        slices("pages-team", 1800) { pageScreen(it, TeamPage(it, NoHost)) }
        slices("pages-dex", 1800) { dex(it, null) }
        slices("pages-dex-types", 1800) { dex(it, "types") }
        slices("pages-shop", 1600) { shop(it, false) }
        slices("pages-bag", 1600) { shop(it, true) }
        slices("pages-settings", 3600) { settings(it) }
    }

    // ---------- dialogs (3, 4, 6, 9) ----------

    /** Opens a dialog through [open] (which gets the render context) and shoots it on a scrim. */
    private fun dialog(name: String, variants: List<Variant> = phoneSplit, scenario: FakeGame.Scenario = FakeGame.Scenario.MID_GAME,
                       open: (Context) -> Unit) {
        paparazzi.shootPage(name, variants, scenario) { ctx ->
            val d = DialogShots.capture { open(ctx) } ?: error("no dialog was shown")
            DialogShots.scene(ctx, d)
        }
    }

    /** The same for dialogs that are an AppDialog (Pickers.confirm / choose since R6-01). */
    private fun appDialog(name: String, variants: List<Variant> = phoneSplit, open: (Context) -> Unit) {
        paparazzi.shootPage(name, variants, FakeGame.Scenario.MID_GAME) { ctx ->
            val d = AppDialogShots.capture { open(ctx) } ?: error("no dialog was shown")
            AppDialogShots.scene(ctx, d)
        }
    }

    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    @Test fun dexDetail() {
        dialog("pages-dlg-dexdetail-caught", all) { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(25)) }
        dialog("pages-dlg-dexdetail-seen", all) { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(150)) }
    }

    @Test fun monDetail() {
        dialog("pages-dlg-mondetail", all) { ctx -> MonDetail.show(ctx, gd, st.mon("t1")!!) }
    }

    @Test fun popups() {
        dialog("pages-dlg-evolved") { ctx ->
            Pickers.evolved(ctx, GameEvent.Evolved("t2", gd.sp(2).name, gd.sp(3).name, 3), false)
        }
        appDialog("pages-dlg-confirm") { ctx -> Pickers.confirm(ctx, "Refresh the shop?", "New items and TMs for 100 ₽.", "Refresh") {} }
        appDialog("pages-dlg-release") { ctx -> Pickers.confirm(ctx, "Release Sparkmouse?", "Sparkmouse (Lv 26) will be gone for good.", "Release…") {} }
        dialog("pages-dlg-pickitem") { ctx -> Pickers.pickItem(ctx, "Use which item?", st, listOf("potion", "super-potion", "thunder-stone", "rare-candy")) {} }
        dialog("pages-dlg-pickball") { ctx -> Pickers.pickItem(ctx, "Throw which ball?", st, listOf("poke-ball", "great-ball", "ultra-ball")) {} }
        dialog("pages-dlg-pickmon") { ctx ->
            Pickers.pickMon(ctx, "Give it to…", gd, st.mons.take(5), { com.husarp.ankigo.ui.Bits.hpText(gd, it) }) {}
        }
        dialog("pages-dlg-pickmove") { ctx -> Pickers.pickMove(ctx, "Forget which move?", gd, st.mon("t1")!!) {} }
        appDialog("pages-dlg-choose") { ctx -> Pickers.choose(ctx, "Pick one", listOf("Ask", "Always open in AnkiDroid", "Answer anyway")) {} }
        for ((name, uid) in listOf("pages-dlg-monmenu" to "t3", "pages-dlg-monmenu-main" to "t1")) {
            paparazzi.shootPage(name, phoneSplit) { ctx ->
                MenuKit.scene(ctx, MenuKit.capture { MonActions(TeamPage(ctx, NoHost)).menu(st.mon(uid)!!, View(ctx)) })
            }
        }
    }

    // ---------- Ankimon import dialogs ----------

    private fun ankimonSave(): AnkimonSave {
        fun m(id: String, species: Int?, name: String, lv: Int, main: Boolean = false) = AnkimonMon(
            individualId = id, isMain = main, id = species, name = name, nickname = null, level = lv, xp = 120,
            iv = mapOf("hp" to 20, "atk" to 15, "def" to 22, "spa" to 8, "spd" to 17, "spe" to 25), ev = emptyMap(),
            nature = "Hardy", gender = "M", ability = "static", attacks = listOf("tackle"), shiny = false, heldItem = null,
            everstone = false, friendship = 70, capturedDate = "2026-09-01 10:00:00", defeated = 12, tier = "Normal",
        )
        val mons = listOf(m("a1", 16, "Birdie", 14, main = true), m("a2", 19, "Ratty", 9), m("a3", 54, "Duckling", 21), m("a4", 129, "Flopfish", 12),
            m("a5", 999_999, "Unknownmon", 5))
        return AnkimonSave(mons, team = listOf("a1", "a2", "a3"), items = mapOf("Poke Ball" to 10), tms = emptyList(), cash = 3100,
            seen = setOf(16, 19, 54, 129, 63), caught = setOf(16, 19, 54, 129), problems = listOf("Unknownmon: unknown species #999999"))
    }

    private fun ankimonPreview(ctx: Context) {
        val okCls = Class.forName("com.husarp.ankigo.ui.AnkimonImportFlow\$Read\$Ok")
        val ok = okCls.declaredConstructors.first().apply { isAccessible = true }.newInstance(ankimonSave(), emptyMap<Int, Int>())
        call(AnkimonImportFlow, "preview", ctx.applicationContext, ctx, ok, { ctx }, { _: Boolean -> })
    }

    @Test fun ankimonImport() {
        dialog("pages-dlg-ankimon-preview", phoneTall) { ctx -> ankimonPreview(ctx) }
        dialog("pages-dlg-ankimon-preview-notinfile", phoneSplit) { ctx ->
            st.mons.take(3).forEachIndexed { i, m -> m.ankimonId = "pc-gone-$i" } // on the phone from an earlier import, not in this file
            ankimonPreview(ctx)
        }
        dialog("pages-dlg-ankimon-nothingnew-notinfile", phoneSplit) { ctx ->
            call(AnkimonImportFlow, "info", ctx, "Nothing new in this file",
                "Everything in it is already in your game.\n\n" + AnkimonImportFlow.notInFileLine(3))
        }
        dialog("pages-dlg-ankimon-failed") { ctx ->
            call(AnkimonImportFlow, "info", ctx, "Import failed",
                "This is not an Ankimon save file. In Anki use Ankimon's menu Game → Export Save File… and pick that file.")
        }
    }
}
