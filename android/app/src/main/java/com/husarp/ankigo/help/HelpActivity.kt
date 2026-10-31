package com.husarp.ankigo.help

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.DiagnosticsActivity
import com.husarp.ankigo.Media
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.Bits
import com.husarp.ankigo.ui.BottomNav
import com.husarp.ankigo.ui.Form
import com.husarp.ankigo.ui.GamePage
import com.husarp.ankigo.ui.PageActivity
import com.husarp.ankigo.ui.PageHost
import com.husarp.ankigo.ui.Dest
import com.husarp.ankigo.ui.Nav
import com.husarp.ankigo.ui.Ui

// TICKET R3-V3a (PLAN 44, pick 3): the Help screen. Home = the game guide on top, problem pages below; a problem page
// (what you see / why / what to do / check); the game guide with one lesson selected from its top bar.
// Everything works offline: the texts are in HelpContent / GameGuide.

/** The Help screen as a page: no bottom bar, works without a loaded game. Back goes from a page to the home, then leaves. */
class HelpScreen(
    base: Context,
    host: PageHost,
    start: HelpRoute,
    private val outside: Boolean,
    /** Set by the screenshots: Set up pictures shows these facts and starts no check. */
    initialFacts: PictureFacts? = null,
) : GamePage(base, host) {
    override val tab: BottomNav.Tab? = null
    override val needsGame = false

    var route: HelpRoute = start
        private set

    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var guideNav: android.widget.FrameLayout
    private lateinit var barHolder: android.widget.FrameLayout
    private val openIds = LinkedHashSet<String>()
    internal val currentGuideChapter: String?
        get() = if (route is HelpRoute.Guide) openIds.firstOrNull() ?: GameGuide.BATTLES else null
    private val history = ArrayList<HelpRoute>()
    private val main = Handler(Looper.getMainLooper())
    private var scrollTo: String? = null
    private var facts: PictureFacts? = initialFacts
    private val pinned = initialFacts != null
    private var checking = false
    private var checkFailed = false // the last check threw: show an error, retry only through "Check again"
    private var checkToken = 0

    private fun dp(v: Int) = Ui.dp(this, v)

    init {
        (start as? HelpRoute.Guide)?.let { openIds += it.chapter ?: GameGuide.BATTLES; scrollTo = it.chapter }
    }

    override fun buildBar(): View {
        barHolder = android.widget.FrameLayout(this)
        rebuildBar()
        return barHolder
    }

    private fun rebuildBar() {
        barHolder.removeAllViews()
        val bar = Ui.appBar(this, HelpRoutes.title(route), back = true, onBack = { if (!goBack()) host.closePage() })
        bar.titleView.maxLines = 2 // the page titles are whole sentences
        barHolder.addView(bar, android.widget.FrameLayout.LayoutParams(-1, -2))
    }

    override fun buildBody(): View {
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(28))
        }
        scroll = ScrollView(this).apply { addView(content) }
        guideNav = android.widget.FrameLayout(this).apply { setPadding(dp(16), 0, dp(16), 0) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(guideNav, LinearLayout.LayoutParams(-1, -2))
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        render()
        return root
    }

    /** Back inside the screen: true when it moved (page or guide to the home). False = nothing left, the caller leaves. */
    fun goBack(): Boolean {
        if (history.isNotEmpty()) {
            showRoute(history.removeAt(history.size - 1))
            return true
        }
        val to = HelpRoutes.back(route, outside) ?: return false
        showRoute(to)
        return true
    }

    fun show(to: HelpRoute) {
        if (to == route) return
        history += route
        showRoute(to)
    }

    private fun showRoute(to: HelpRoute) {
        route = to
        openIds.clear()
        scrollTo = null
        (to as? HelpRoute.Guide)?.let { openIds += it.chapter ?: GameGuide.BATTLES; scrollTo = it.chapter }
        render()
        if (scrollTo == null && ::scroll.isInitialized) scroll.scrollTo(0, 0)
    }

    override fun render() {
        content.removeAllViews()
        guideNav.removeAllViews()
        guideNav.visibility = if (route is HelpRoute.Guide) View.VISIBLE else View.GONE
        rebuildBar()
        when (val r = route) {
            HelpRoute.Home -> home()
            is HelpRoute.Problem -> HelpContent.page(r.id)?.let { problem(it) } ?: home()
            is HelpRoute.Guide -> guide()
            HelpRoute.Setup -> setup()
        }
    }

    /** Back from AnkiDroid's or Android's settings: look again, so a fix shows at once. */
    override fun onShow() {
        super.onShow()
        if (route == HelpRoute.Setup && facts != null && !checking) checkSetup()
    }

    // ---------- home ----------

    private fun home() {
        val chapters = GameGuide.chapters()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = Ui.rounded(Bits.blend(Ui.c(this@HelpScreen, R.color.ag_mauve), Ui.c(this@HelpScreen, R.color.ag_card), 0.14f), 14, this@HelpScreen)
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(10))
            isClickable = true
            setOnClickListener { show(HelpRoute.Guide(null)) }
            val line = LinearLayout(this@HelpScreen).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            line.addView(android.widget.ImageView(this@HelpScreen).apply {
                setImageResource(R.drawable.ic_game)
                setColorFilter(Ui.c(this@HelpScreen, R.color.ag_mauve))
            }, LinearLayout.LayoutParams(dp(22), dp(22)).also { it.marginEnd = dp(8) })
            line.addView(Ui.text(this@HelpScreen, 16f, 900, R.color.ag_text).apply { text = "How the game works" }, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(android.widget.ImageView(this@HelpScreen).apply {
                setImageResource(R.drawable.ic_chevron)
                setColorFilter(Ui.c(this@HelpScreen, R.color.ag_mauve))
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(line)
            addView(Ui.text(this@HelpScreen, 12f, 700, R.color.ag_sub1).apply { text = "${chapters.size} short chapters, mostly pictures" }, HelpViews.lp(this@HelpScreen, 2))
        }
        box.addView(head)
        val perRow = if (resources.configuration.screenWidthDp >= 360) 4 else 2
        chapters.chunked(perRow).forEachIndexed { ri, row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEachIndexed { ci, ch ->
                line.addView(HelpViews.tocChip(this, ch.id, ri * perRow + ci + 1, tile = true) { show(HelpRoute.Guide(ch.id)) },
                    LinearLayout.LayoutParams(0, -2, 1f).also { if (ci > 0) it.marginStart = dp(6) })
            }
            // keep the last row's tiles the same width as the others
            repeat(perRow - row.size) { line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).also { it.marginStart = dp(6) }) }
            box.addView(line, HelpViews.lp(this, if (ri == 0) 0 else 6))
        }
        content.addView(box, HelpViews.lp(this))

        content.addView(Form.secondary(this, "Setup guide", onPage = true) {
            startScreen(Intent(this, com.husarp.ankigo.ui.FirstRunActivity::class.java).putExtra("setupOnly", true).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION))
        }, HelpViews.lp(this, 12))
        content.addView(Form.sect(this, "Fix a problem", "works offline"))
        val rows = HelpContent.pages.map { p -> HelpViews.problemRow(this, p) { show(HelpRoute.Problem(p.id)) } }
        content.addView(Form.cardOf(this, rows))
    }

    // ---------- problem page ----------

    private var checkHolder: LinearLayout? = null

    private fun problem(page: HelpPage) {
        for (kind in SectionKind.entries) {
            val sec = page.section(kind) ?: continue
            val card = HelpViews.card(this)
            card.addView(HelpViews.heading(this, HelpViews.sectionTone(kind), kind.title))
            if (sec.text.isNotBlank()) card.addView(HelpViews.rich(this, sec.text), HelpViews.lp(this, 7))
            sec.steps.forEachIndexed { i, s -> card.addView(HelpViews.step(this, i + 1, s), HelpViews.lp(this, if (i == 0) 6 else 0)) }
            if (kind == SectionKind.DO) {
                page.notes.forEach { card.addView(HelpViews.note(this, it), HelpViews.lp(this, 8)) }
                val buttons = HelpFixes.buttons(page.id).map { b -> HelpViews.button(this, b) { HelpActions.run(this, b) } }
                if (buttons.isNotEmpty()) card.addView(HelpViews.buttonRow(this, buttons), HelpViews.lp(this, 10))
            }
            if (kind == SectionKind.CHECK && HelpFixes.checksPictures(page.id)) {
                val holder = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                checkHolder = holder
                holder.addView(HelpViews.button(this, HelpButton(HelpAction.HELP_PAGE, "Check pictures now")) { checkPictures() })
                card.addView(holder, HelpViews.lp(this, 10))
            }
            content.addView(card, HelpViews.lp(this, if (content.childCount == 0) 4 else 8))
        }
    }

    /** Reads the picture facts off the main thread (it lists files and asks AnkiDroid) and shows the result next to the button. */
    private fun checkPictures() {
        val holder = checkHolder ?: return
        val app = applicationContext
        holder.removeViews(1, holder.childCount - 1)
        holder.addView(HelpViews.resultChip(this, PictureCheck(null, "Checking…")), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(8) })
        Thread {
            val result = try { HelpFixes.pictureCheck(PictureFacts.read(app)) } catch (_: Exception) { PictureCheck(null, "Couldn't check right now") }
            main.post {
                if (!alive || checkHolder !== holder) return@post
                holder.removeViews(1, holder.childCount - 1)
                holder.addView(HelpViews.resultChip(this, result), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = dp(8) })
            }
        }.start()
    }

    // ---------- set up pictures ----------

    private fun setup() {
        val f = facts
        val steps = f?.let { PictureSetup.evaluate(it) }
        content.addView(HelpViews.setupHeader(this, steps, checking, checkFailed && f == null) { checkSetup() }, HelpViews.lp(this, 4))
        steps?.forEach { st ->
            content.addView(HelpViews.setupStep(this, st) { b -> HelpActions.run(this, b) }, HelpViews.lp(this, 8))
        }
        if (f == null && !checking && !checkFailed) main.post { if (alive && route == HelpRoute.Setup && facts == null && !checking && !checkFailed) checkSetup() }
    }

    /** Reads the facts off the main thread (file listing, AnkiDroid queries), then draws the steps. A newer check wins. */
    private fun checkSetup() {
        if (pinned) return
        val app = applicationContext
        val token = ++checkToken
        checking = true
        checkFailed = false
        if (route == HelpRoute.Setup) render()
        Thread {
            val read = try { PictureFacts.read(app) } catch (_: Exception) { null }
            main.post {
                if (!alive || token != checkToken) return@post
                checking = false
                if (read != null) facts = read else checkFailed = true
                if (route == HelpRoute.Setup) render()
            }
        }.start()
    }

    // ---------- game guide ----------

    private fun guide() {
        val chapters = GameGuide.chapters()
        val current = chapters.indexOfFirst { it.id in openIds }.coerceAtLeast(0)
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val picker = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(48)
            gravity = android.view.Gravity.CENTER_VERTICAL
            contentDescription = "Choose chapter"
            addView(Ui.text(this@HelpScreen, 14f, 900, R.color.ag_text).apply { text = chapters[current].title + " ▾" })
            addView(Ui.text(this@HelpScreen, 11f, 700, R.color.ag_sub0).apply { text = "Lesson ${current + 1} of ${chapters.size}" })
            setOnClickListener {
                val rows = LinearLayout(this@HelpScreen).apply { orientation = LinearLayout.VERTICAL }
                val dialog = com.husarp.ankigo.ui.AppDialog.Builder(this@HelpScreen).setTitle("Chapters")
                    .setView(ScrollView(this@HelpScreen).apply { addView(rows) })
                    .setNegativeButton("Cancel", null).create()
                chapters.forEachIndexed { i, ch ->
                    rows.addView(HelpViews.chapterHeader(this@HelpScreen, ch, i + 1, false) {
                        dialog.dismiss(); openChapter(ch.id)
                    })
                }
                com.husarp.ankigo.ui.Pickers.show(dialog)
            }
        }
        nav.addView(picker, LinearLayout.LayoutParams(0, -2, 1f))
        fun arrow(previous: Boolean) = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(Ui.c(this@HelpScreen, R.color.ag_text))
            if (!previous) rotation = 180f
            setPadding(dp(13), dp(13), dp(13), dp(13))
            contentDescription = if (previous) "Previous lesson" else "Next lesson"
            isEnabled = if (previous) current > 0 else current < chapters.lastIndex
            alpha = if (isEnabled) 1f else 0.4f
            setOnClickListener { openChapter(chapters[(current + if (previous) -1 else 1).coerceIn(chapters.indices)].id) }
        }
        nav.addView(arrow(true), LinearLayout.LayoutParams(dp(48), dp(48)))
        nav.addView(arrow(false), LinearLayout.LayoutParams(dp(48), dp(48)))
        guideNav.addView(nav, android.widget.FrameLayout.LayoutParams(-1, -2))
        HelpViews.chapterBody(this, chapters[current]).forEach { content.addView(it, HelpViews.lp(this, 10)) }
        scrollTo = null
        scroll.scrollTo(0, 0)
    }

    /** Select one lesson and return its reading area to the top. */
    private fun openChapter(id: String) {
        openIds.clear()
        openIds += id
        scrollTo = id
        render()
    }
}

/** What the Help buttons do. Everything is wrapped: a missing app or settings screen shows a short message instead of crashing. */
internal object HelpActions {
    fun run(page: HelpScreen, b: HelpButton) {
        val ctx: Context = page
        try {
            when (b.action) {
                HelpAction.ALL_FILES_ACCESS -> allFiles(page)
                HelpAction.ANKIDROID_SETTINGS -> page.startScreen(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${Anki.pkg}")))
                HelpAction.OPEN_ANKIDROID -> {
                    val i = Anki.launchIntent(ctx)
                    if (i == null) Ui.toast(ctx, "AnkiDroid isn't installed") else page.startScreen(i)
                }
                HelpAction.IMPORT_APKG -> importApkg(page)
                HelpAction.HELP_PAGE -> b.arg?.let { page.show(HelpRoute.Problem(it)) }
                HelpAction.OPEN_DIAGNOSTICS -> page.startScreen(Intent(ctx, DiagnosticsActivity::class.java))
                HelpAction.OPEN_SETTINGS -> if (b.arg != null) HelpSettingsLinks.open(ctx, b.arg) else page.startScreen(Nav.intent(ctx, Dest.SETTINGS))
                HelpAction.OPEN_URL -> b.arg?.let { page.startScreen(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
            }
        } catch (e: Exception) {
            Ui.toast(ctx, "Couldn't open that")
        }
    }

    /** The same screen as Diagnostics → All files access; before Android 11 AnkiGo's own app settings (Permissions). */
    private fun allFiles(page: HelpScreen) {
        if (Build.VERSION.SDK_INT < 30) {
            page.startScreen(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${page.packageName}")))
            return
        }
        try {
            page.startScreen(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${page.packageName}")))
        } catch (e: Exception) {
            page.startScreen(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    /** Pick an .apkg exported from AnkiDroid with media; Media.importApkg copies its pictures (the Diagnostics flow). */
    private fun importApkg(page: HelpScreen) {
        val app = page.applicationContext // not the page: it can be recreated while the picker is open
        val main = Handler(Looper.getMainLooper())
        page.host.startForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")) { res, data ->
            val uri = data?.data
            if (res != Activity.RESULT_OK || uri == null) return@startForResult
            Ui.toast(app, "Importing pictures…")
            Thread {
                Media.importApkg(app, uri)
                main.post { Ui.toast(app, "Done. Use Check pictures now to see how it went.") }
            }.start()
        }
    }
}

/** The Help screen as its own Activity. */
class HelpActivity : PageActivity() {
    override val closesWhenLeft = true // PLAN 222
    private var restored: HelpRoute? = null
    private var outside = false
    private var screen: HelpScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        outside = intent.getBooleanExtra(EXTRA_OUTSIDE, false)
        restored = savedInstanceState?.let { HelpRoutes.parse(it.getString(EXTRA_PAGE), it.getString(EXTRA_CHAPTER)) }
        super.onCreate(savedInstanceState)
    }

    override fun createPage(): GamePage {
        val start = restored ?: HelpRoutes.parse(intent.getStringExtra(EXTRA_PAGE), intent.getStringExtra(EXTRA_CHAPTER))
        outside = outside || (restored == null && start != HelpRoute.Home)
        return HelpScreen(this, this, start, outside).also { screen = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(EXTRA_OUTSIDE, outside)
        when (val r = screen?.route) {
            is HelpRoute.Problem -> outState.putString(EXTRA_PAGE, r.id)
            HelpRoute.Setup -> outState.putString(EXTRA_PAGE, HelpRoutes.SETUP)
            is HelpRoute.Guide -> outState.putString(EXTRA_CHAPTER, screen?.currentGuideChapter ?: HelpRoutes.GUIDE)
            else -> {}
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (screen?.goBack() != true) super.onBackPressed()
    }

    companion object {
        private const val EXTRA_PAGE = "page"
        private const val EXTRA_CHAPTER = "chapter"
        private const val EXTRA_OUTSIDE = "outside"

        /**
         * Opens Help. No arguments = the home; [page] = a problem page (a [HelpContent] id); [chapter] = the game guide
         * with that [GameGuide] chapter open (or [HelpRoutes.GUIDE]: all chapters closed). Back from a page opened this
         * way returns to where it was opened from.
         */
        fun open(ctx: Context, page: String? = null, chapter: String? = null) {
            val i = Intent(ctx, HelpActivity::class.java)
            if (page != null) i.putExtra(EXTRA_PAGE, page)
            if (chapter != null) i.putExtra(EXTRA_CHAPTER, chapter)
            if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }

        /** Opens "Set up pictures" (PLAN 41). Back leaves Help. */
        fun openSetup(ctx: Context) = open(ctx, page = HelpRoutes.SETUP)
    }
}
