package com.husarp.ankigo

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Html
import android.view.Gravity
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.content.Intent
import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.overlay.FloatBattleService
import com.husarp.ankigo.sound.SoundPrefs
import com.husarp.ankigo.sound.SoundRules
import com.husarp.ankigo.study.CardSpeech
import com.husarp.ankigo.study.JsApi
import com.husarp.ankigo.study.PanelHeight
import com.husarp.ankigo.study.AutoAdvance
import com.husarp.ankigo.study.StudyLoop
import com.husarp.ankigo.study.StudyTiming
import com.husarp.ankigo.study.TtsRules
import com.husarp.ankigo.ui.*
import com.husarp.ankigo.update.Updates
import org.json.JSONObject
import java.util.concurrent.Executors

/** Study through AnkiDroid's scheduler, awarding game progress only after a verified answer. */
class StudyActivity : com.husarp.ankigo.ui.AgActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("study", 0) }
    private var deckId = 0L
    private var deckName = ""
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var cardColumn: LinearLayout
    private lateinit var side: LinearLayout
    private lateinit var web: WebView
    private lateinit var swipe: SwipeFrame
    private var swipeOn = false   // the "Swipe to answer" setting, read when the screen resumes
    private lateinit var notice: LinearLayout
    private lateinit var noticeScroll: ScrollView
    private lateinit var answerDivider: View
    private lateinit var buttons: LinearLayout
    private lateinit var header: TextView
    private lateinit var handle: TextView
    private lateinit var handleBar: FrameLayout
    private lateinit var gap: View
    private lateinit var topBar: LinearLayout
    private lateinit var subtitle: TextView
    private lateinit var subRow: LinearLayout   // the title's second line; the timer pill moves in here when the top bar is narrow
    private lateinit var queueCounts: LinearLayout
    private lateinit var cardSurface: LinearLayout
    private lateinit var summaryBar: AppBar
    private var actionSheet: Dialog? = null
    private var modelName = ""
    private var cardName = ""

    private lateinit var panel: BattlePanelView
    private var summaryView: View? = null
    private lateinit var events: EventPlayer
    private var card: Anki.Review? = null
    private var stats: Map<String, String?> = emptyMap()
    private var question = ""; private var answerHtml = ""; private var css = ""
    private var fields = emptyMap<String, String>()
    private var flipped = false
    private var locked = true
    private var mediaBlocked = false
    private var generation = 0
    private var shownAt = 0L
    private var timedCard: Pair<Long, Int>? = null
    private var elapsed = 0L
    private var background = true
    private var maxTime = 60_000L
    private var opts = StudyTiming.Options()   // the deck's time options (timer pill, auto-advance)
    private var sideNo = 0                     // counts up per card side shown: a countdown belongs to one side
    private var pageReady = false              // this side's page finished loading
    private var autoFired = -1                 // the side whose auto-advance already acted
    private var frozenAt = -1L                 // the timer pill's value once the answer is shown (deck: stop timer on answer)
    private var timerRed = false
    private lateinit var timerPill: TextView
    private lateinit var countdown: CountdownBar
    private val auto = AutoAdvance(main, { if (reduceMotion()) 1000L else 66L }, { countdown.fraction = it }, { fireAuto() })
    private val timerTick = Runnable { refreshTimer() }
    private var allowAt = Long.MAX_VALUE
    private var heightMode = 1   // the panel's look (0 full, 1 strip, 2 mini), derived from panelDp
    private var panelDp = -1      // the battle panel's height in dp (stacked layout); -1 = the preset of heightMode
    private var appliedBodyDp = -1
    private var audio: MediaPlayer? = null
    private var avAuto = emptyList<TtsRules.Av>()  // sounds and speech that play by themselves on this side
    private var avAll = emptyList<TtsRules.Av>()   // every sound/speech button of the page, by number
    private var speech: CardSpeech? = null         // created on the first card with speech
    private var ttsCard = TtsRules.NONE
    private var voiceNote: String? = null          // "No Japanese voice on this phone"
    private var mediaArgs = Triple(0, 0, false)
    private var audioBusy = false                  // the AV queue is playing
    private var audioNow = false   // the card on screen has sound (its question or answer)
    private var audioPrev = false  // the card just answered had sound; its battle events play while the next card loads
    private val soundQueue = ArrayDeque<TtsRules.Av>()
    private val counts = IntArray(4)
    private var totalTime = 0L
    private var coins = 0; private var xp = 0; private var met = 0; private var defeated = 0
    private var levels = 0; private var evolutions = 0
    private val caught = arrayListOf<String>()
    private val items = arrayListOf<String>()
    // Presentation-only session details: never used to calculate rewards or send provider writes.
    private val rewardMons = arrayListOf<JSONObject>()
    private val teamProgress = linkedMapOf<String, JSONObject>()
    private val initialLevels = linkedMapOf<String, Int>()
    private val foundItems = arrayListOf<String>()
    private var finished = false
    private var started = false
    private val sink = object : LiveSession.Sink {
        override fun onGameEvents(events: List<GameEvent>) { this@StudyActivity.events.enqueue(events); updateHandle() }
        override fun onLogLine(kind: LogKind, pill: String, text: String, redraw: Boolean) {
            events.enqueueAction { if (redraw) panel.renderAll(); panel.setLog(kind, pill, text) }
        }
        override fun onStatus(ok: Boolean, text: String, canGrant: Boolean) { panel.setStatus(ok, text, canGrant) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Anki.load(this)
        deckId = intent.getLongExtra("deckId", 0); deckName = intent.getStringExtra("deckName") ?: "Deck"
        prefs.edit().putLong("lastDeck", deckId).putString("lastName", deckName).apply()
        val tablet = resources.configuration.smallestScreenWidthDp >= com.husarp.ankigo.study.StudyLayout.TABLET_DP
        panelDp = PanelHeight.restore(prefs.getInt("panelDp", -1), prefs.getInt("height", -1), tablet)
        if (!prefs.contains("panelDp")) prefs.edit().putInt("panelDp", panelDp).apply()   // the old 0/1/2 pref, migrated once
        setContentView(buildContent())
        setupWeb(); layout()
        if (savedInstanceState != null) {
            savedInstanceState.getIntArray("counts")?.copyInto(counts)
            totalTime = savedInstanceState.getLong("totalTime"); coins = savedInstanceState.getInt("coins"); xp = savedInstanceState.getInt("xp")
            met = savedInstanceState.getInt("met"); defeated = savedInstanceState.getInt("defeated"); levels = savedInstanceState.getInt("levels"); evolutions = savedInstanceState.getInt("evolutions")
            caught += savedInstanceState.getStringArrayList("caught").orEmpty(); items += savedInstanceState.getStringArrayList("items").orEmpty()
            foundItems += savedInstanceState.getStringArrayList("foundItems").orEmpty()
            runCatching {
                val saved = JSONObject(savedInstanceState.getString("studyResults") ?: "{}")
                val mons = saved.optJSONArray("mons")
                if (mons != null) repeat(mons.length()) { rewardMons += mons.getJSONObject(it) }
                val progress = saved.optJSONObject("progress")
                progress?.keys()?.forEach { teamProgress[it] = progress.getJSONObject(it) }
                val starts = saved.optJSONObject("initialLevels")
                starts?.keys()?.forEach { initialLevels[it] = starts.getInt(it) }
            }
        }
        GameRepo.load(this) {
            if (!isDestroyed) {
                GameRepo.state?.teamMons()?.forEach { mon -> initialLevels.putIfAbsent(mon.uid, mon.level) }
                panel.renderAll(); panel.restoreStudyCharge()
                if (GameRepo.game != null) {
                    val initial = GameRepo.act(this) { g -> g.tick() + g.ensureEncounter() }
                    events.enqueue(initial); record(initial)
                }
                if (started) LiveSession.hold(this, this)
                loadNext()
            }
        }
    }
    /** The screen's views, without the Activity setup; split out of onCreate so the design screenshots can build them. */
    internal fun buildContent(): View {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(this@StudyActivity, R.color.ag_base)) }
        body = LinearLayout(this)
        cardColumn = StudyUi.column(this)
        topBar = StudyUi.column(this)
        val top = StudyUi.row(this).apply { setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 6), 0) }
        top.addView(StudyUi.iconButton(this, R.drawable.ic_back, "Back to decks") { finish() }, StudyUi.lp(Ui.dp(this, 34), Ui.dp(this, 34)))
        val title = StudyUi.column(this)
        header = StudyUi.ellipsis(StudyUi.label(this, deckName.substringAfterLast("::"), 15, R.color.ag_text, 900))
        subtitle = StudyUi.ellipsis(StudyUi.label(this, "Loading…", 10, R.color.ag_sub0, 800))
        subRow = StudyUi.row(this); subRow.addView(subtitle, StudyUi.lp(0, -2, 1f))
        title.addView(header); title.addView(subRow)
        top.addView(title, StudyUi.lp(0, -2, 1f).also { it.marginStart = Ui.dp(this, 6); it.marginEnd = Ui.dp(this, 6) })
        timerPill = StudyUi.label(this, "0:00", 11.5f, R.color.ag_sub1, 900).apply {
            gravity = Gravity.CENTER; fontFeatureSettings = "tnum"; visibility = View.GONE
            setPadding(Ui.dp(context, 7), Ui.dp(context, 3), Ui.dp(context, 7), Ui.dp(context, 3))
        }; styleTimer(false)
        top.addView(timerPill, StudyUi.lp(-2).also { it.marginEnd = Ui.dp(this, 8) })
        queueCounts = StudyUi.row(this); top.addView(queueCounts)
        top.addView(StudyUi.iconButton(this, R.drawable.ic_more_vert, "Card actions") { actions() }, StudyUi.lp(Ui.dp(this, 34), Ui.dp(this, 34)).also { it.marginStart = Ui.dp(this, 6) })
        topBar.addView(top, StudyUi.lp(-1, Ui.dp(this, 45)))
        topBar.addView(StudyUi.line(this))
        cardColumn.addView(topBar)
        summaryBar = Ui.appBar(this, deckName.substringAfterLast("::"), "done").apply { visibility = View.GONE }
        cardColumn.addView(summaryBar)
        cardSurface = StudyUi.column(this).apply { setBackgroundColor(Ui.c(context, R.color.ag_anki_bg)) }
        countdown = CountdownBar(this).apply { visibility = View.GONE }
        cardSurface.addView(countdown, StudyUi.lp(-1, Ui.dp(this, 3)))
        web = WebView(this).apply { setBackgroundColor(Ui.c(context, R.color.ag_anki_bg)) }
        swipe = SwipeFrame(this, web).also { it.onSwipe = { ease -> answer(ease) } }
        cardSurface.addView(swipe, StudyUi.lp(-1, 0, 1f))
        notice = StudyUi.column(this).apply { visibility = View.GONE }
        noticeScroll = object : ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) {
                if (notice.visibility == View.GONE) { super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY)); return }
                val available = View.MeasureSpec.getSize(h)
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec((available * .55f).toInt(), View.MeasureSpec.AT_MOST))
            }
        }.apply { visibility = View.GONE; isVerticalScrollBarEnabled = false; addView(notice) }
        cardSurface.addView(noticeScroll, StudyUi.lp().also { it.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 8)) })
        cardColumn.addView(cardSurface, StudyUi.lp(-1, 0, 1f))
        side = StudyUi.column(this)
        handleBar = FrameLayout(this).apply {
            setBackgroundColor(Ui.c(context, R.color.ag_mantle))
            contentDescription = "Battle panel height. Drag up or down to resize, tap to switch between Full, Strip and Mini"
            setOnClickListener { setPanelDp(PanelHeight.tap(currentPanelDp(), isTablet()), save = true) }
            setOnTouchListener(handleDrag())
        }
        handleBar.addView(View(this).apply { background = Ui.rounded(Ui.c(context, R.color.ag_ov), 2, context) }, FrameLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 4), Gravity.CENTER))
        handleBar.addView(StudyUi.line(this), FrameLayout.LayoutParams(-1, Ui.dp(this, 1), Gravity.TOP))
        handle = StudyUi.label(this, "STRIP", 9, R.color.ag_sub0, 900).apply { letterSpacing = .055f; gravity = Gravity.CENTER_VERTICAL }
        handleBar.addView(handle, FrameLayout.LayoutParams(-2, -1, Gravity.RIGHT).also { it.rightMargin = Ui.dp(this, 10) })
        side.addView(handleBar, StudyUi.lp(-1, Ui.dp(this, PanelHeight.BAR)))
        panel = BattlePanelView(this)
        events = EventPlayer(panel)
        events.muted = { SoundRules.quietNow(SoundPrefs.quietOnCardAudio(this), audioNow, audioPrev) }
        panel.onMenu = { anchor ->
            if (events.idle) AppMenu(anchor)
                .item(0, "Team", R.drawable.ic_team).item(1, "Bag / Shop", R.drawable.ic_shop)
                .item(2, "Pokédex", R.drawable.ic_book).item(3, "Settings", R.drawable.ic_settings)
                .show { i -> startActivity(Intent(this, listOf(TeamActivity::class.java, ShopActivity::class.java, DexActivity::class.java, SettingsActivity::class.java)[i])) }
        }
        panel.onBalls = { throwBall() }
        panel.arena.onCatchTagTap = {
            val game = GameRepo.game
            if (events.idle && game != null) {
                GameRepo.run(this) { it.setOverride(if (game.willCatch()) com.husarp.ankigo.game.Outcome.DEFEAT else com.husarp.ankigo.game.Outcome.CATCH) }
                panel.renderAll()
            }
        }
        panel.onGrant = { requestPermissions(arrayOf(Anki.permission), 1) }
        side.addView(panel)
        gap = View(this).apply { setBackgroundColor(Ui.c(context, R.color.ag_base)) }
        side.addView(gap, StudyUi.lp(-1, 0, 0f))
        buttons = StudyUi.column(this).apply { setPadding(Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8)) }
        buttons.setBackgroundColor(Ui.c(this, R.color.ag_base))
        answerDivider = StudyUi.line(this).apply { visibility = View.GONE }
        side.addView(answerDivider)
        side.addView(buttons)
        body.addView(cardColumn); body.addView(side)
        // The window's real height is known only after the first layout (and changes in split screen): keep the panel inside it.
        body.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val dp = ((bottom - top) / resources.displayMetrics.density).toInt()
            if (dp != appliedBodyDp && panelDp > 0 && studyPlan().handle && !finished) main.post { if (!isDestroyed) setPanelDp(panelDp, save = false) }
        }
        root.addView(body, StudyUi.lp(-1, 0, 1f))
        return root
    }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putIntArray("counts", counts); out.putLong("totalTime", totalTime); out.putInt("coins", coins); out.putInt("xp", xp)
        out.putInt("met", met); out.putInt("defeated", defeated); out.putInt("levels", levels); out.putInt("evolutions", evolutions)
        out.putStringArrayList("caught", caught); out.putStringArrayList("items", items)
        out.putStringArrayList("foundItems", foundItems)
        out.putString("studyResults", JSONObject().put("mons", org.json.JSONArray(rewardMons))
            .put("progress", JSONObject(teamProgress.toMap())).put("initialLevels", JSONObject(initialLevels.toMap())).toString())
    }
    override fun onConfigurationChanged(config: Configuration) { super.onConfigurationChanged(config); actionSheet?.dismiss(); layout() }
    private fun layout() {
        val plan = studyPlan()
        val wide = plan.columns
        body.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        // A split-screen window (e.g. 412x400 dp) counts as landscape but is too narrow for a 42% battle column
        // (~170 dp: info cards cover the sprites). There the columns are 50/50 and the arena keeps a fixed, width-sized
        // height (the sprites scale with the arena height) so both cards fit beside the sprites; the answers stay at the bottom.
        val narrow = plan.narrow
        cardColumn.layoutParams = if (wide) StudyUi.lp(0, -1, plan.cardShare) else StudyUi.lp(-1, 0, 1f)
        side.layoutParams = if (wide) StudyUi.lp(0, -1, plan.sideShare) else StudyUi.lp(-1, -2)
        val mode = plan.mode ?: PanelHeight.mode(panelHeightDp(plan), plan.panelDp(0))
        heightMode = mode
        panel.setStudyLayout(mode != 0, mode == 2)
        // Compact info cards (name, level, HP bar) on the study strip's sprite geometry: full cards cannot sit beside the sprites in ~200 dp.
        if (narrow) { panel.arena.setCompact(false); panel.arena.setCompact(true) }
        panel.layoutParams = when {
            narrow -> StudyUi.lp(-1, Ui.dp(this, 170))
            wide -> StudyUi.lp(-1, 0, 1f)
            else -> StudyUi.lp(-1, Ui.dp(this, panelHeightDp(plan)))
        }
        gap.layoutParams = StudyUi.lp(-1, 0, if (narrow) 1f else 0f)
        answerDivider.visibility = if (finished) View.VISIBLE else View.GONE
        panel.visibility = if (finished || GameRepo.game == null) View.GONE else View.VISIBLE
        handleBar.visibility = if (finished || !plan.handle || GameRepo.game == null) View.GONE else View.VISIBLE
        if (!finished) {   // the answer row has one fixed height whatever it holds, so nothing above it moves between cards
            buttons.layoutParams = StudyUi.lp(-1, Ui.dp(this, PanelHeight.buttonsDp(plan.answerRows)))
            buttons.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8)); buttons.gravity = Gravity.CENTER_VERTICAL
        }
        side.setPadding(if (wide) Ui.dp(this, 1) else 0, 0, 0, 0)
        side.setBackgroundColor(Ui.c(this, if (wide) R.color.ag_s0 else R.color.ag_base))
        updateHandle()
        placeTimer()
        if (card != null && !finished) showButtons()
    }
    private fun studyPlan(): com.husarp.ankigo.study.StudyLayout.Plan = resources.configuration.let {
        com.husarp.ankigo.study.StudyLayout.plan(it.screenWidthDp, it.smallestScreenWidthDp, it.orientation == Configuration.ORIENTATION_LANDSCAPE, finished)
    }
    private fun updateHandle() { handle.text = PanelHeight.label(currentPanelDp(), isTablet()) }
    private fun isTablet() = resources.configuration.smallestScreenWidthDp >= com.husarp.ankigo.study.StudyLayout.TABLET_DP
    /** The panel height (dp) before it is clamped to the window: the saved/dragged one, else the preset of [heightMode]. */
    private fun currentPanelDp() = if (panelDp > 0) panelDp else PanelHeight.preset(isTablet(), heightMode)
    /** The height the panel really gets: [currentPanelDp] kept inside the window (the card area keeps its minimum). */
    private fun panelHeightDp(plan: com.husarp.ankigo.study.StudyLayout.Plan): Int {
        val bodyPx = if (::body.isInitialized && body.height > 0) body.height else Ui.dp(this, resources.configuration.screenHeightDp - 24)
        appliedBodyDp = (bodyPx / resources.displayMetrics.density).toInt()
        return PanelHeight.clamp(currentPanelDp(), PanelHeight.maxDp(appliedBodyDp, PanelHeight.buttonsDp(plan.answerRows)))
    }
    /** Moves the battle panel to [dp] (clamped): resizes it and switches its look; [save] also remembers the height. */
    private fun setPanelDp(dp: Int, save: Boolean) {
        val plan = studyPlan()
        panelDp = dp
        val shown = panelHeightDp(plan)
        if (save) { panelDp = shown; prefs.edit().putInt("panelDp", shown).apply() }
        val mode = PanelHeight.mode(shown, plan.panelDp(0))
        if (mode != heightMode) { heightMode = mode; panel.setStudyLayout(mode != 0, mode == 2) }
        panel.layoutParams = StudyUi.lp(-1, Ui.dp(this, shown))
        updateHandle()
    }
    /** Drag the bar like a split-screen divider: the panel follows the finger, on release it snaps to a preset within reach. */
    private fun handleDrag() = object : View.OnTouchListener {
        private var downY = 0f; private var startDp = 0; private var dragging = false
        override fun onTouch(v: View, e: android.view.MotionEvent): Boolean {
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downY = e.rawY; startDp = panelHeightDp(studyPlan()); dragging = false }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - downY
                    if (!dragging && Math.abs(dy) > android.view.ViewConfiguration.get(this@StudyActivity).scaledTouchSlop) {
                        dragging = true; v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) setPanelDp(startDp - (dy / resources.displayMetrics.density).toInt(), save = false)
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (dragging) setPanelDp(PanelHeight.snap(panelHeightDp(studyPlan()), isTablet()), save = true) else v.performClick()
                    dragging = false
                }
                android.view.MotionEvent.ACTION_CANCEL -> { if (dragging) setPanelDp(panelHeightDp(studyPlan()), save = true); dragging = false }
            }
            return true
        }
    }
    override fun onStart() { super.onStart(); started = true; BattleScreen.setVisible(this, true); LiveSession.addSink(sink); LiveSession.hold(this, this) }
    override fun onStop() { started = false; LiveSession.removeSink(sink); LiveSession.release(this, this); BattleScreen.setVisible(this, false); super.onStop() }
    override fun onResume() {
        super.onResume(); background = false; Updates.onResume(this); FloatBattleService.startIfWanted(this)
        if (shownAt != 0L) shownAt = SystemClock.elapsedRealtime()
        swipeOn = prefs.getBoolean("swipe", false); if (::swipe.isInitialized) updateSwipe()
        if (::web.isInitialized && (finished || card != null && !locked)) loadNext()
        syncTiming()
    }
    override fun onPause() {
        pauseClock(); background = true; syncTiming(); stopAudio(); Updates.onPause(this); super.onPause()
    }
    private fun pauseClock() { if (shownAt != 0L) elapsed += SystemClock.elapsedRealtime() - shownAt; shownAt = 0 }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (::countdown.isInitialized) syncTiming() }

    // ---- visible answer timer and auto-advance (the rules are in study/StudyTiming.kt)
    private fun styleTimer(red: Boolean) {
        timerRed = red
        timerPill.setTextColor(Ui.c(this, if (red) R.color.ag_red else R.color.ag_sub1))
        timerPill.background = Ui.rounded(if (red) StudyUi.blend(Ui.c(this, R.color.ag_red), Ui.c(this, R.color.ag_s0), .16f) else Ui.c(this, R.color.ag_s0), 9, this)
    }
    /** The pill sits before the counts; in a narrow card column (split screen) it moves to the title's second line so the deck name keeps its room. */
    private fun placeTimer() {
        val narrow = resources.configuration.screenWidthDp * studyPlan().cardShare < 300
        val top = queueCounts.parent as ViewGroup
        val want: ViewGroup = if (narrow) subRow else top
        if (timerPill.parent === want) return
        (timerPill.parent as? ViewGroup)?.removeView(timerPill)
        if (narrow) want.addView(timerPill, StudyUi.lp(-2).also { it.marginStart = Ui.dp(this, 6) })
        else want.addView(timerPill, want.indexOfChild(queueCounts), StudyUi.lp(-2).also { it.marginEnd = Ui.dp(this, 8) })
    }
    private fun reduceMotion() = !android.animation.ValueAnimator.areAnimatorsEnabled() || prefs.getBoolean("reduceMotion", false)
    private fun autoOn() = prefs.getBoolean("autoAdvance", false)
    private fun autoPlan(): StudyTiming.Plan? {
        val c = card ?: return null
        return if (finished) null else StudyTiming.plan(opts, flipped, autoOn(), prefs.getInt("autoQuestionSecs", StudyTiming.OWN_QUESTION), prefs.getInt("autoAnswerSecs", StudyTiming.OWN_ANSWER), c.buttons)
    }
    private fun syncTiming() { if (::countdown.isInitialized) { refreshTimer(); syncAuto() } }
    internal fun refreshTimer() {
        main.removeCallbacks(timerTick)
        val shown = card != null && !finished && !(locked && notice.visibility == View.VISIBLE) &&
            StudyTiming.timerShown(prefs.getString("timer", StudyTiming.AS_DECK), opts.showTimer)
        timerPill.visibility = if (shown) View.VISIBLE else View.GONE
        subtitle.visibility = if (shown && timerPill.parent === subRow) View.GONE else View.VISIBLE   // narrow bar: the pill takes the subtitle's place
        if (!shown) return
        val ms = if (frozenAt >= 0) frozenAt else StudyTiming.taken(elapsed, shownAt, SystemClock.elapsedRealtime(), maxTime)
        val red = StudyTiming.atMax(ms, maxTime)
        if (red != timerRed) styleTimer(red)
        timerPill.text = StudyTiming.clock(ms); timerPill.contentDescription = StudyTiming.timerDescription(ms)
        if (!background && shownAt != 0L && frozenAt < 0 && !red) main.postDelayed(timerTick, 1000)
    }
    /** Colour and spoken text of the countdown bar for [plan] (the bar's fill is set by the countdown). */
    internal fun styleCountdown(plan: StudyTiming.Plan) {
        countdown.tone = Ui.c(this, when (plan.act) {
            StudyTiming.Act.SHOW_ANSWER -> R.color.ag_mauve; StudyTiming.Act.AGAIN -> R.color.ag_red; StudyTiming.Act.HARD -> R.color.ag_peach
            StudyTiming.Act.GOOD -> R.color.ag_green; StudyTiming.Act.BURY -> R.color.ag_sub0; StudyTiming.Act.REMINDER -> R.color.ag_yellow
        })
        countdown.contentDescription = StudyTiming.describe(plan)
    }
    private fun syncAuto() {
        countdown.visibility = if (autoOn()) View.VISIBLE else View.GONE
        val plan = autoPlan()
        val run = plan != null && autoFired != sideNo &&
            StudyTiming.mayRun(pageReady, !background, hasWindowFocus(), locked, mediaBlocked, actionSheet != null, audioBusy, opts.waitForAudio)
        if (plan == null || !run) { auto.stop(); return }
        if (auto.running && auto.side == sideNo) return
        styleCountdown(plan); auto.start(sideNo, plan.millis)
    }
    /** The countdown reached zero: act through the same functions a tap uses (they keep every safety check). */
    private fun fireAuto() {
        val plan = autoPlan() ?: return
        autoFired = sideNo
        when (plan.act) {
            StudyTiming.Act.SHOW_ANSWER -> flip()
            StudyTiming.Act.BURY -> writeAction("bury")
            StudyTiming.Act.AGAIN, StudyTiming.Act.HARD, StudyTiming.Act.GOOD -> answer(plan.ease)
            StudyTiming.Act.REMINDER -> {
                Ui.toast(this, StudyTiming.reminder(flipped))
                if (prefs.getBoolean("haptic", true)) web.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }
    override fun onDestroy() { generation++; main.removeCallbacksAndMessages(null); events.clear(); actionSheet?.dismiss(); stopAudio(); speech?.shutdown(); web.destroy(); io.shutdown(); super.onDestroy() }
    private fun setupWeb() {
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false; web.settings.allowContentAccess = false
        web.settings.textZoom = prefs.getInt("zoom", 100).coerceIn(80, 150)
        web.addJavascriptInterface(object {
            @JavascriptInterface fun play(name: String) { main.post { playAv(listOf(TtsRules.Av(file = name))) } }
            @JavascriptInterface fun speak(i: Int) { main.post { avAll.getOrNull(i)?.let { playAv(listOf(it)) } } }
            @JavascriptInterface fun flip() { main.post { this@StudyActivity.flip() } }
            @JavascriptInterface fun actions() { main.post { this@StudyActivity.actions() } }
            @JavascriptInterface fun mathjax(s: String) {}
            @JavascriptInterface fun jsError(s: String) {}
        }, "AnkiGo")
        web.addJavascriptInterface(object {
            @JavascriptInterface fun call(page: Int, endpoint: String, data: String): String = jsCall(page, endpoint, data)
        }, "AnkiGoJs")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (!flipped && card != null && !background && shownAt == 0L) shownAt = SystemClock.elapsedRealtime()
                pageReady = true; syncTiming()
            }
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                JsApi.signal(req.url.toString())?.let { a -> main.post { if (jsSnap != null) jsDo(a) }; return true }
                if (req.url.scheme in listOf("http", "https") && req.url.host != "ankigo.media") runCatching { startActivity(Intent(Intent.ACTION_VIEW, req.url)) }
                return true
            }
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                if (req.url.host != "ankigo.media") return WebResourceResponse("text/plain", "UTF-8", "".byteInputStream())
                val name = req.url.path?.trimStart('/') ?: return null
                val stream = if (name.startsWith("mathjax/") || name == "fonts/Nunito.ttf") runCatching { assets.open(name) }.getOrNull() else Media.open(this@StudyActivity, name)
                if (stream == null) return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), "".byteInputStream())
                return WebResourceResponse(android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.')) ?: "application/octet-stream", null, stream)
            }
        }
    }
    private fun loadNext() {
        stopAudio()
        audioPrev = audioNow; audioNow = false; voiceNote = null; ttsCard = TtsRules.NONE
        summaryView?.let { cardColumn.removeView(it) }; summaryView = null
        // The answer row keeps its place while the next card loads: the old buttons stay, disabled (the summary's buttons go).
        if (finished) buttons.removeAllViews() else disableButtons(buttons)
        finished = false; locked = true; card = null; pageReady = false; jsSnap = null
        topBar.visibility = View.VISIBLE; summaryBar.visibility = View.GONE; cardSurface.visibility = View.VISIBLE
        web.visibility = View.VISIBLE; showNotice(false); updateSwipe()
        layout(); syncTiming()
        val token = ++generation
        io.execute {
            val result = runCatching {
                check(Anki.studySupported(this)) { "Studying here needs AnkiDroid 2.24 or newer. Companion mode is still available." }
                Anki.selectDeck(this, deckId)
                var next: Anki.Review? = null
                var problem: Exception? = null
                repeat(3) {
                    if (next == null) try {
                        if (it > 0) { main.post { if(valid(token)) subtitle.text = "Waking AnkiDroid…" }; Thread.sleep(250) }
                        next = Anki.nextCards(this, deckId, 1).firstOrNull(); problem = null } catch (e: Exception) { problem = e }
                }
                problem?.let { throw it }
                val decks = Anki.decks(this)
                val n = next
                if (n == null) {
                    if ((decks.firstOrNull { it.id == deckId }?.total ?: 0) > 0) {
                        main.post { if(valid(token)) showError("AnkiDroid hasn't opened its study queue. Open AnkiDroid once, then retry.") }
                        return@runCatching
                    }
                    val queryDeck = "deck:\"${deckName.replace("\"", "") }\""
                    val learn = Anki.searchCards(this, "$queryDeck is:learn", arrayOf("_id")).size
                    val fresh = Anki.searchCards(this, "$queryDeck is:new", arrayOf("_id")).size
                    main.post { if (valid(token)) summary(decks, learn, fresh) }
                    return@runCatching
                }
                val html = Anki.card(this, n.nid, n.ord) ?: kotlin.error("Open AnkiDroid once, then retry.")
                val flagsOk = Anki.flagsSupported(this)
                val state = Anki.card(this, n.nid, n.ord, CardState.COLS + "ord" + "original_due" + (if (flagsOk) arrayOf("flags") else emptyArray())) ?: kotlin.error("Couldn't read this card's scheduling state.")
                check(CardState.from(state) != null) { "AnkiDroid didn't provide the card's review count." }
                val note = Anki.note(this, n.nid)
                val model = note?.let { Anki.model(this, it.mid) }
                val q = html["question"] ?: ""; val a = html["answer"] ?: ""
                val fieldMap = if (model != null) model.fieldNames.zip(note.fields).toMap() else emptyMap()
                val files = CardMedia.files(q + a + (model?.css ?: ""), n.media)
                val missing = files.filter { Media.open(this, it)?.use { false } ?: true }
                val blocked = missing.filterNot { CardMedia.isSound(it) }
                val unsupported = CardMedia.unsupported(q + a)
                // {{tts}} text and language are not in the API's HTML (only [anki:play:..]): rebuild them from the template
                val spoken = if (note != null && model != null && (q + a).contains("[anki:play:")) runCatching {
                    TtsRules.templateFor(Anki.templates(this, note.mid), n.ord, model.type == 1)?.let { TtsRules.card(it, fieldMap, n.ord) }
                }.getOrNull() ?: TtsRules.NONE else TtsRules.NONE
                val options = JSONObject(Anki.deckOptions(this, deckId))
                main.post {
                    if (!valid(token)) return@post
                    finished = false; card = n; stats = state; question = q; answerHtml = a; css = model?.css ?: ""; fields = fieldMap
                    audioNow = SoundRules.cardHasAudio(q, a); ttsCard = spoken
                    opts = StudyTiming.parse(options); maxTime = opts.maxMs
                    val d = decks.firstOrNull { it.id == deckId }
                    modelName = model?.name ?: ""
                    cardName = state["card_name"] ?: "Card ${n.ord + 1}"
                    header.text = deckName.substringAfterLast("::")
                    subtitle.text = "$cardName · card ${counts.sum() + 1}"
                    showQueueCounts(d, state["queue"]?.toIntOrNull())
                    jsBase = JsApi.snapshot(state, n.buttons, n.nextTimes, Triple(d?.new ?: 0, d?.learn ?: 0, d?.review ?: 0), note?.tags ?: "",
                        decks.firstOrNull { it.id == (state["deck_id"]?.toLongOrNull() ?: deckId) }?.name ?: deckName, flagsOk)
                    mediaBlocked = (blocked.isNotEmpty() || unsupported) && prefs.getString("missing", "Ask") != "Answer anyway"
                    layout(); showQuestion(); mediaNotice(blocked.size, missing.size - blocked.size, unsupported); checkVoices(token)
                    if (mediaBlocked && prefs.getString("missing", "Ask") == "Always open in AnkiDroid") openAnki()
                }
            }
            result.onFailure { main.post { if (valid(token)) showError(it.message ?: "Open AnkiDroid once, then retry.") } }
        }
    }
    internal fun showQueueCounts(d: Anki.Deck?, queue: Int?) {
        queueCounts.removeAllViews()
        val current = when (queue) { 0 -> 0; 1, 3 -> 1; else -> 2 }
        listOf((d?.new ?: 0) to R.color.ag_blue, (d?.learn ?: 0) to R.color.ag_red, (d?.review ?: 0) to R.color.ag_green).forEachIndexed { i, (number, color) ->
            queueCounts.addView(StudyUi.label(this, number.toString(), 14, color, 900).apply {
                if (i == current) paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                contentDescription = "${listOf("New", "Learn", "Review")[i]} $number${if (i == current) ", current" else ""}"
            }, StudyUi.lp(-2).also { if (i > 0) it.marginStart = Ui.dp(this, 7) })
        }
    }
    private fun valid(token: Int) = !isDestroyed && token == generation
    private fun page(content: String, side: String, typed: String?) = CardHtml.build(content, css, card!!.ord, side, typed,
        dark = isNight(), speech = if (side == "q") ttsCard.question else ttsCard.answer, jsPage = jsShow(side == "a")) { fields[it] }
    private fun showQuestion() {
        val key = card!!.nid to card!!.ord
        if (timedCard != key) elapsed = 0
        timedCard = key
        flipped = false; shownAt = 0; locked = false; sideNo++; pageReady = false; frozenAt = -1
        val page = page(question, "q", null); avAuto = page.autoplay; avAll = page.av
        web.loadDataWithBaseURL(CardHtml.BASE, page.html, "text/html", "UTF-8", null)
        showButtons(); playAv(avAuto); syncTiming()
    }
    private fun flip() {
        if (locked || flipped || mediaBlocked || card == null) return
        locked = true; syncTiming()
        web.evaluateJavascript("document.getElementById('typeans')?.value || ''") { raw ->
            if (isDestroyed || card == null) return@evaluateJavascript
            val typed = runCatching { org.json.JSONArray("[$raw]").getString(0) }.getOrDefault("")
            val page = page(answerHtml, "a", typed); avAuto = page.autoplay; avAll = page.av
            flipped = true; locked = false; sideNo++; pageReady = false; allowAt = SystemClock.elapsedRealtime() + 300
            if (opts.stopTimerOnAnswer) frozenAt = StudyTiming.taken(elapsed, shownAt, SystemClock.elapsedRealtime(), maxTime)
            web.loadDataWithBaseURL(CardHtml.BASE, page.html, "text/html", "UTF-8", null)
            web.announceForAccessibility("Answer shown"); showButtons(); playAv(avAuto); syncTiming()
        }
    }
    /** Swipe-to-answer is on for the answer side only; the hint stays while the card is up, a swipe works only while answering is allowed. */
    private fun updateSwipe() {
        val showing = swipeOn && flipped && card != null && !finished && !mediaBlocked
        swipe.setState(showing && !locked, card?.buttons ?: 4, showing)
    }
    private fun disableButtons(v: View) {
        if (v !== buttons) { v.isEnabled = false; if (v.isClickable) v.alpha = .55f }
        if (v is ViewGroup) for (i in 0 until v.childCount) disableButtons(v.getChildAt(i))
    }
    private fun showButtons() {
        buttons.removeAllViews(); updateSwipe()
        val enabled = !locked && !mediaBlocked
        if (!flipped) {
            buttons.addView(StudyUi.answer(this, "Show answer", null, R.color.ag_mauve, enabled) { flip() }, StudyUi.lp(-1, Ui.dp(this, 48)))
            return
        }
        val n = card ?: return
        val labels = when (n.buttons) { 2 -> listOf("Again", "Good"); 3 -> listOf("Again", "Good", "Easy"); else -> listOf("Again", "Hard", "Good", "Easy") }
        val wide = studyPlan().answerRows == 2
        var row = StudyUi.row(this)
        buttons.addView(row)
        labels.forEachIndexed { i, label ->
            if (wide && i == 2) { row = StudyUi.row(this); buttons.addView(row, StudyUi.spaced(this, 6)) }
            val ease = i + 1
            val color = when(label) { "Again" -> R.color.ag_red; "Hard" -> R.color.ag_peach; "Good" -> R.color.ag_green; else -> R.color.ag_blue }
            row.addView(StudyUi.answer(this, label, n.nextTimes.getOrElse(i) { "" }, color, enabled) { answer(ease) },
                StudyUi.lp(0, Ui.dp(this, if (wide) 40 else 48), 1f).also { if (row.childCount > 0) it.marginStart = Ui.dp(this, 6) })
        }
    }
    private fun answer(ease: Int) {
        if (locked || !flipped || mediaBlocked || ease !in 1..(card?.buttons ?: 4) || SystemClock.elapsedRealtime() < allowAt) return
        val c = card ?: return
        val before = stats
        val state = CardState.from(before) ?: return
        pauseClock(); val took = elapsed.coerceAtMost(maxTime)
        if (prefs.getBoolean("haptic", true)) web.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        locked = true; showButtons(); syncTiming()
        io.execute {
            val result = runCatching {
                StudyLoop(object : StudyLoop.Provider {
                    override fun select() { Anki.selectDeck(this@StudyActivity, deckId) }
                    override fun top() = Anki.nextCards(this@StudyActivity, deckId, 1).firstOrNull()?.let { StudyLoop.Key(it.nid, it.ord) }
                    override fun stats(key: StudyLoop.Key) = Anki.card(this@StudyActivity, key.nid, key.ord, CardState.COLS)
                    override fun answer(key: StudyLoop.Key, ease: Int, ms: Long) { Anki.answer(this@StudyActivity, key.nid, key.ord, ease, ms) }
                }).answer(StudyLoop.Key(c.nid, c.ord), before, ease, took) { LiveSession.expectOwn(state) }
            }
            main.post {
                result.onSuccess { r ->
                    when (r.status) {
                        StudyLoop.Status.SAVED -> {
                            val button = com.husarp.ankigo.study.StudyRules.gameButton(c.buttons, ease)
                            counts[button - 1]++; totalTime += took; timedCard = null
                            prefs.edit().putLong("lastStudied:$deckId", System.currentTimeMillis()).apply()
                            Local.recordReview(this, c.nid, c.ord, ease, took, "app")
                            val fresh = CardState.from(r.row!!)!!
                            LiveSession.ownAnswer(this, fresh, button) { record(it); if (!isDestroyed) loadNext() }
                        }
                        StudyLoop.Status.MOVED -> { LiveSession.cancelOwn(state); if (!isDestroyed) loadNext() }
                        StudyLoop.Status.FAILED -> { LiveSession.cancelOwn(state); if (!isDestroyed) showError("This answer wasn't saved. Reload before retrying.") }
                    }
                }.onFailure { LiveSession.cancelOwn(state); if (!isDestroyed) showError("Couldn't confirm that this answer was saved. Reload before retrying.") }
            }
        }
    }
    private fun record(list: List<GameEvent>) {
        for (e in list) when (e) {
            is GameEvent.Cash -> coins += e.amount
            is GameEvent.XpGained -> xp += e.xp
            is GameEvent.LevelUp -> {
                levels++
                val mon = GameRepo.state?.mon(e.uid)
                val progress = teamProgress.getOrPut(e.uid) { JSONObject().put("start", initialLevels[e.uid] ?: e.level - 1) }
                progress.put("name", mon?.name ?: e.name).put("level", mon?.level ?: e.level).put("species", mon?.species ?: 0).put("shiny", mon?.shiny ?: false)
            }
            is GameEvent.Evolved -> {
                evolutions++
                val mon = GameRepo.state?.mon(e.uid)
                val progress = teamProgress.getOrPut(e.uid) { JSONObject().put("start", initialLevels[e.uid] ?: mon?.level ?: 0) }
                progress.put("fromName", e.from).put("name", e.to).put("species", e.toSpecies).put("level", mon?.level ?: 0).put("shiny", mon?.shiny ?: false).put("evolved", true)
            }
            is GameEvent.Caught -> {
                caught += e.name
                rewardMons += JSONObject().put("species", e.species).put("name", e.name).put("shiny", e.shiny).put("caught", true)
            }
            is GameEvent.Defeated -> {
                defeated++
                rewardMons += JSONObject().put("species", e.species).put("name", e.name).put("caught", false)
            }
            is GameEvent.Encounter -> met++
            is GameEvent.ItemFound -> { items += "${e.count} ${e.item}"; foundItems += e.item }
            else -> {}
        }
    }
    private fun showNotice(show: Boolean) {
        notice.visibility = if (show) View.VISIBLE else View.GONE
        noticeScroll.visibility = notice.visibility
    }
    private fun styleNotice() {
        notice.setPadding(Ui.dp(this, 11), Ui.dp(this, 9), Ui.dp(this, 11), Ui.dp(this, 10))
        notice.background = Ui.rounded(StudyUi.blend(Ui.c(this, R.color.ag_peach), Ui.c(this, R.color.ag_card), .14f), 14, this).apply {
            setStroke(Ui.dp(this@StudyActivity, 1), (Ui.c(this@StudyActivity, R.color.ag_peach) and 0x00ffffff) or 0x73000000)
        }
    }
    /** The card column is only ~200 dp wide in a split-screen window: the notice's two main buttons stack there instead of squeezing. */
    private fun stackNoticeButtons(): Boolean {
        return resources.configuration.screenWidthDp * studyPlan().cardShare < 300
    }
    private fun noticeButtons() = if (stackNoticeButtons()) StudyUi.column(this) else StudyUi.row(this)
    private fun noticeGap() = StudyUi.lp(-2).also { if (stackNoticeButtons()) { it.width = -1; it.topMargin = Ui.dp(this, 6) } else it.marginStart = Ui.dp(this, 6) }
    private fun mediaNotice(pictures: Int, sound: Int, unsupported: Boolean) {
        mediaArgs = Triple(pictures, sound, unsupported)
        notice.removeAllViews(); styleNotice()
        showNotice(pictures > 0 || sound > 0 || unsupported || voiceNote != null)
        if (pictures > 0 || unsupported) {
            val heading = StudyUi.row(this)
            heading.addView(StudyUi.icon(this, R.drawable.ic_alert, R.color.ag_peach), StudyUi.lp(Ui.dp(this, 16), Ui.dp(this, 16)).also { it.marginEnd = Ui.dp(this, 6) })
            heading.addView(StudyUi.label(this, if (unsupported) "This card needs AnkiDroid" else "$pictures picture${if (pictures == 1) " can't" else "s can't"} be shown here", 13, R.color.ag_text, 900), StudyUi.lp(0, -2, 1f))
            notice.addView(heading)
            notice.addView(StudyUi.label(this, "The Play Store build keeps pictures private. This card is next in AnkiDroid too: answer there and AnkiGo counts it.", 11.5f, R.color.ag_sub0), StudyUi.spaced(this, 3))
            val row = noticeButtons()
            row.addView(StudyUi.smallButton(this, "Open in AnkiDroid", true) { openAnki() })
            row.addView(StudyUi.smallButton(this, "Answer anyway") { mediaBlocked = false; showNotice(false); showButtons(); syncTiming() }, noticeGap())
            notice.addView(row, StudyUi.spaced(this, 8))
            notice.addView(StudyUi.label(this, "For readable pictures, use F-Droid / GitHub and grant the media folder in Settings. Sync in AnkiDroid, install the other build, then sync back.", 11.5f, R.color.ag_sub0), StudyUi.spaced(this, 6))
            val links = StudyUi.row(this)
            links.addView(StudyUi.smallButton(this, "F-Droid") { openUrl("https://f-droid.org/packages/com.ichi2.anki/") })
            links.addView(StudyUi.smallButton(this, "GitHub") { openUrl("https://github.com/ankidroid/Anki-Android/releases") }, StudyUi.lp(-2).also { it.marginStart = Ui.dp(this, 6) })
            notice.addView(links, StudyUi.spaced(this, 4))
        } else {
            if (sound > 0) notice.addView(StudyUi.label(this, "$sound sound file${if (sound == 1) "" else "s"} unavailable here", 11.5f, R.color.ag_sub0))
            voiceNote?.let { note ->
                notice.addView(StudyUi.label(this, note, 11.5f, R.color.ag_sub0), if (sound > 0) StudyUi.spaced(this, 6) else StudyUi.lp(-2))
                val row = StudyUi.row(this)
                row.addView(StudyUi.smallButton(this, "Voice settings") { voiceSettings() })
                notice.addView(row, StudyUi.spaced(this, 6))
            }
        }
    }
    /** A card with speech needs a voice for its language: say so (never blocking) when the phone has none. The pictures banner wins. */
    private fun checkVoices(token: Int) {
        val langs = ttsCard.languages
        if (langs.isEmpty()) return
        val engine = speech ?: CardSpeech(this).also { speech = it }
        engine.missing(langs) { gone ->
            if (!valid(token) || card == null || gone != null && gone.isEmpty()) return@missing
            val names = gone?.map { java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.ENGLISH).ifEmpty { it } }?.distinct() ?: emptyList()
            voiceNote = TtsRules.missingVoiceNote(names)
            if (mediaArgs.first == 0 && !mediaArgs.third) mediaNotice(mediaArgs.first, mediaArgs.second, mediaArgs.third)
        }
    }
    private fun voiceSettings() {
        for (action in listOf("com.android.settings.TTS_SETTINGS", android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
            if (runCatching { startActivity(Intent(action)) }.isSuccess) return
    }
    private fun showError(message: String) {
        locked = true; notice.removeAllViews(); styleNotice(); showNotice(true)
        notice.addView(StudyUi.label(this, message, 13, R.color.ag_red, 900))
        val row = StudyUi.row(this)
        row.addView(StudyUi.smallButton(this, "Retry / reload", true) { loadNext() })
        row.addView(StudyUi.smallButton(this, "Open in AnkiDroid") { openAnki() }, StudyUi.lp(-2).also { it.marginStart = Ui.dp(this, 6) })
        notice.addView(row, StudyUi.spaced(this, 8)); showButtons(); syncTiming()
    }
    private fun throwBall() {
        if (!events.idle) return
        val st = GameRepo.state ?: return
        val balls = st.bag.filter { (id, count) -> count > 0 && com.husarp.ankigo.game.Items.def(id)?.kind == com.husarp.ankigo.game.ItemKind.BALL }.keys.toList()
        if (balls.isEmpty()) { Ui.toast(this, "No Poké Balls. Buy some in the shop."); return }
        AlertDialog.Builder(this).setTitle("Throw a ball").setItems(balls.map { "${com.husarp.ankigo.game.Items.name(it)} ×${st.count(it)}" }.toTypedArray()) { _, i ->
            val result = GameRepo.act(this) { it.throwBall(balls[i]) }
            record(result); events.enqueue(result); LiveSession.refreshWidgetsFor(this, result)
        }.setNegativeButton("Cancel", null).showCard()
    }
    private fun actions() {
        if (locked || card == null) return
        actionSheet?.dismiss()
        val dialog = Dialog(this).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        actionSheet = dialog
        val scroll = actionSheetContent { dialog.dismiss() }
        dialog.setContentView(scroll); dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.48f); setGravity(Gravity.BOTTOM)
        }
        dialog.show()
        dialog.window?.setLayout(minOf(resources.displayMetrics.widthPixels, Ui.dp(this, 600)), -2)
        dialog.setOnDismissListener { if (actionSheet === dialog) actionSheet = null; syncTiming() }
        syncTiming()
    }
    /** The card-actions sheet's content (the dialog around it is made by [actions]); [dismiss] closes that dialog. */
    internal fun actionSheetContent(dismiss: () -> Unit): View {
        val sheet = StudyUi.column(this).apply {
            background = GradientDrawable().apply {
                setColor(Ui.c(context, R.color.ag_base))
                val r = Ui.dp(context, 22f); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(Ui.dp(context, 8), Ui.dp(context, 6), Ui.dp(context, 8), Ui.dp(context, 10))
        }
        sheet.addView(View(this).apply { background = Ui.rounded(Ui.c(context, R.color.ag_ov), 2, context) },
            StudyUi.lp(Ui.dp(this, 40), Ui.dp(this, 4)).also { it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = Ui.dp(this, 4); it.bottomMargin = Ui.dp(this, 8) })
        val who = StudyUi.column(this).apply { setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 10), Ui.dp(context, 6)) }
        val preview = Html.fromHtml(question.replace(Regex("<script\\b[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), ""), 0).toString().trim().replace(Regex("\\s+"), " ")
        who.addView(StudyUi.ellipsis(StudyUi.label(this, preview.ifEmpty { cardName }.take(120), 14, R.color.ag_text, 900)))
        who.addView(StudyUi.ellipsis(StudyUi.label(this, "$modelName · Card ${(card?.ord ?: 0) + 1} · $deckName", 11, R.color.ag_sub0, 800)))
        sheet.addView(who)
        fun action(icon: Int, title: String, sub: String? = null, danger: Boolean = false, faded: Boolean = false, block: () -> Unit) {
            val row = StudyUi.row(this).apply {
                setPadding(Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 10), Ui.dp(context, 8))
                background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Ui.c(context, R.color.ag_mantle)), Ui.rounded(Color.TRANSPARENT, 12, context), null)
                if (faded) alpha = .6f
                setOnClickListener { dismiss(); block() }
            }
            row.addView(StudyUi.icon(this, icon, if (danger) R.color.ag_red else R.color.ag_sub1), StudyUi.lp(Ui.dp(this, 20), Ui.dp(this, 20)).also { it.marginEnd = Ui.dp(this, 12) })
            val text = StudyUi.column(this)
            text.addView(StudyUi.label(this, title, 14, if (danger) R.color.ag_red else R.color.ag_text, 800))
            if (sub != null) text.addView(StudyUi.label(this, sub, 10.5f, if (danger) R.color.ag_red else R.color.ag_sub0))
            row.addView(text, StudyUi.lp(0, -2, 1f)); sheet.addView(row)
        }
        action(R.drawable.ic_bury, "Bury card", "Back tomorrow") { writeAction("bury") }
        action(R.drawable.ic_pause, "Suspend card", "Unsuspend later in AnkiDroid's browser") { writeAction("suspend") }
        if (Anki.flagsSupported(this)) {
            val flags = StudyUi.row(this).apply { setPadding(Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 10), Ui.dp(context, 8)) }
            flags.addView(StudyUi.icon(this, R.drawable.ic_flag), StudyUi.lp(Ui.dp(this, 20), Ui.dp(this, 20)).also { it.marginEnd = Ui.dp(this, 12) })
            flags.addView(StudyUi.label(this, "Flag", 14, R.color.ag_text, 800), StudyUi.lp(0, -2, 1f))
            val names = listOf("None", "Red", "Orange", "Green", "Blue", "Pink", "Turquoise", "Purple")
            val colors = listOf(R.color.ag_s0, R.color.ag_red, R.color.ag_peach, R.color.ag_green, R.color.ag_blue, R.color.ag_pink, R.color.ag_teal, R.color.ag_mauve)
            colors.forEachIndexed { i, tone ->
                flags.addView(StudyUi.label(this, if (i == 0) "×" else "", 12, R.color.ag_sub0, 900).apply {
                    gravity = Gravity.CENTER; background = Ui.rounded(Ui.c(context, tone), 8, context)
                    contentDescription = "Flag ${names[i]}"; isFocusable = true
                    setOnClickListener { dismiss(); writeAction("flag", i) }
                }, StudyUi.lp(Ui.dp(this, 16), Ui.dp(this, 16)).also { it.marginStart = Ui.dp(this, 7) })
            }
            sheet.addView(flags)
        }
        action(R.drawable.ic_edit, "Edit note", "Opens AnkiDroid's browser on this note, tap it to edit") { edit() }
        action(R.drawable.ic_open, "Continue in AnkiDroid", "Same deck; the battle keeps counting") { openAnki() }
        action(R.drawable.ic_sound, "Replay sound") { playAv(avAuto) }
        sheet.addView(StudyUi.line(this), StudyUi.lp(-1, Ui.dp(this, 1)).also { it.setMargins(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4)) })
        action(R.drawable.ic_trash, "Delete note…", "Asks first. Deletes all cards of this note", danger = true) { confirmDelete() }
        action(R.drawable.ic_undo, "Undo…", "Not possible in AnkiGo. Use Undo in AnkiDroid", faded = true) {
            AlertDialog.Builder(this).setMessage("AnkiGo can't undo answers, bury, suspend or delete. Use Undo in AnkiDroid. The battle doesn't rewind; a redo won't count twice.").setPositiveButton("Open AnkiDroid") { _, _ -> openAnki() }.setNegativeButton("Back", null).showCard()
        }
        val scroll = object : ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels * .85f).toInt(), View.MeasureSpec.AT_MOST))
            }
        }.apply { isVerticalScrollBarEnabled = false; addView(sheet) }
        return scroll
    }
    private fun writeAction(action: String, flag: Int = 0) {
        val c = card ?: return
        locked = true; showButtons(); syncTiming()
        io.execute {
            val result = runCatching {
                Anki.selectDeck(this, deckId)
                when(action) {
                    "bury" -> { Anki.bury(this, c.nid, c.ord); com.husarp.ankigo.study.StudyRules.buried(Anki.cardStats(this, c.nid, c.ord)) }
                    "suspend" -> { Anki.suspend(this, c.nid, c.ord); com.husarp.ankigo.study.StudyRules.suspended(Anki.cardStats(this, c.nid, c.ord)) }
                    "flag" -> Anki.flag(this, c.nid, c.ord, flag)
                    else -> { Anki.deleteNote(this, c.nid); Anki.note(this, c.nid) == null }
                }
            }
            main.post { if (!isDestroyed) { if (result.getOrDefault(false)) loadNext() else showError("This change wasn't saved.") } }
        }
    }
    private fun confirmDelete() {
        val c = card ?: return
        locked = true
        io.execute {
            val count = runCatching { Anki.searchCards(this, "nid:${c.nid}", arrayOf("_id")).size }.getOrNull()
            main.post { if (!isDestroyed) {
                locked = false
                AlertDialog.Builder(this).setTitle("Delete note?").setMessage("Deletes the note and all its ${count ?: "associated"} cards. AnkiGo can't undo this.")
                    .setPositiveButton("Delete") { _, _ -> writeAction("delete") }.setNegativeButton("Cancel", null).showCard()
            } }
        }
    }
    private fun edit() {
        val c = card ?: return
        val did = stats["deck_id"]?.toLongOrNull() ?: deckId
        io.execute {
            val result = runCatching { Anki.selectDeck(this, did) }
            main.post { if (!isDestroyed) result.onSuccess { runCatching { startActivity(Anki.editIntent(c.nid)) }.onFailure { showError("Couldn't open AnkiDroid's browser") } } }
        }
    }
    private fun openAnki() {
        io.execute {
            val result = runCatching { Anki.selectDeck(this, deckId) }
            main.post { if (!isDestroyed) result.onSuccess {
                // Bring up the existing battle activity so its PiP / floating mode follows the reviewer.
                if (GameRepo.game == null) startActivity(Anki.reviewIntent(deckId))
                else startActivity(Intent(this, GameActivity::class.java).putExtra("reviewDeck", deckId).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }.onFailure { showError("Couldn't select this deck") } }
        }
    }
    private fun summary(decks: List<Anki.Deck>, learn: Int, fresh: Int) {
        finished = true; card = null; locked = true; buttons.removeAllViews(); showNotice(false); syncTiming()
        topBar.visibility = View.GONE; summaryBar.visibility = View.VISIBLE; cardSurface.visibility = View.GONE
        val n = counts.sum()
        val why = when { learn > 0 -> "$learn learning cards come back in a few minutes."; fresh > 0 -> "Daily new-card limit reached · $fresh new cards left."; else -> "All available cards studied." }
        val st = GameRepo.state
        val summary = StudyUi.column(this).apply { setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), Ui.dp(context, 12)) }
        val hero = StudyUi.row(this).apply {
            background = StudyUi.gradient(context, R.color.ag_green, .22f, 16)
            setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10))
        }
        st?.main?.let { mon -> hero.addView(ImageView(this).apply {
            com.husarp.ankigo.ui.MonPreview.show(context, mon.species, mon.shiny, this); contentDescription = mon.name
        }, StudyUi.lp(Ui.dp(this, 72), Ui.dp(this, 72)).also { it.marginStart = -Ui.dp(this, 4); it.marginEnd = Ui.dp(this, 4); it.topMargin = -Ui.dp(this, 6); it.bottomMargin = -Ui.dp(this, 6) }) }
        val heroText = StudyUi.column(this)
        heroText.addView(StudyUi.label(this, "Done for today!", 20, R.color.ag_text, 900))
        val evolvedLine = teamProgress.values.filter { it.optBoolean("evolved") }.joinToString("\n") { "${it.optString("fromName")} evolved into ${it.optString("name")}." }
        heroText.addView(StudyUi.label(this, listOf(evolvedLine, why).filter { it.isNotEmpty() }.joinToString("\n"), 12, R.color.ag_sub0), StudyUi.spaced(this, 2))
        hero.addView(heroText, StudyUi.lp(0, -2, 1f)); summary.addView(hero)
        val stats = StudyUi.row(this)
        listOf(n.toString() to "cards", "${totalTime / 60000} min" to "studied", "${if(n > 0) (n - counts[0]) * 100 / n else 0}%" to "correct").forEachIndexed { i, (value, name) ->
            stats.addView(StudyUi.column(this).apply {
                gravity = Gravity.CENTER; background = Ui.rounded(Ui.c(context, R.color.ag_card), 12, context)
                setPadding(Ui.dp(context, 8), Ui.dp(context, 7), Ui.dp(context, 8), Ui.dp(context, 7))
                addView(StudyUi.label(context, value, 19, R.color.ag_text, 900).apply { gravity = Gravity.CENTER })
                addView(StudyUi.label(context, name, 10.5f, R.color.ag_sub0, 800).apply { gravity = Gravity.CENTER })
            }, StudyUi.lp(0, -2, 1f).also { if (i > 0) it.marginStart = Ui.dp(this, 6) })
        }
        summary.addView(stats, StudyUi.spaced(this, 8))
        val tones = listOf(R.color.ag_red, R.color.ag_peach, R.color.ag_green, R.color.ag_blue)
        val stack = StudyUi.row(this).apply { background = Ui.rounded(Ui.c(context, R.color.ag_s0), 5, context); clipToOutline = true }
        counts.forEachIndexed { i, value -> if (value > 0) stack.addView(View(this).apply { setBackgroundColor(Ui.c(context, tones[i])) }, StudyUi.lp(0, -1, value.toFloat())) }
        summary.addView(stack, StudyUi.spaced(this, 8).also { it.height = Ui.dp(this, 10) })
        val legend = StudyUi.row(this)
        counts.forEachIndexed { i, value -> legend.addView(StudyUi.label(this, "${listOf("Again", "Hard", "Good", "Easy")[i]} $value", 10.5f, R.color.ag_sub0, 800).apply { gravity = if(i == 0) Gravity.START else if(i == 3) Gravity.END else Gravity.CENTER }, StudyUi.lp(0, -2, 1f)) }
        summary.addView(legend, StudyUi.spaced(this, 4))
        summary.addView(StudyUi.section(this, "Battle results", "$met met"))
        if (rewardMons.isNotEmpty()) {
            val sprites = StudyUi.row(this).apply {
                background = Ui.rounded(Ui.c(context, R.color.ag_card), 14, context)
                setPadding(Ui.dp(context, 6), Ui.dp(context, 6), Ui.dp(context, 6), Ui.dp(context, 4))
            }
            val spread = rewardMons.size <= 7
            rewardMons.forEach { mon ->
                sprites.addView(StudyUi.column(this).apply {
                    gravity = Gravity.CENTER
                    val caughtHere = mon.optBoolean("caught")
                    addView(ImageView(context).apply {
                        Ui.staticSprite(context, mon.getInt("species"), mon.optBoolean("shiny"), this)
                        if (!caughtHere) { alpha = .7f; colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(.3f) }) }
                        contentDescription = "${if (caughtHere) "Caught" else "Defeated"} ${mon.optString("name") }"
                    }, StudyUi.lp(Ui.dp(context, 42), Ui.dp(context, 42)).also { it.topMargin = -Ui.dp(context, 4); it.bottomMargin = -Ui.dp(context, 6) })
                    addView(StudyUi.label(context, if (caughtHere) "CAUGHT" else "XP", 8, if (caughtHere) R.color.ag_mauve else R.color.ag_sub0, 900).apply { gravity = Gravity.CENTER })
                }, if (spread) StudyUi.lp(0, -2, 1f) else StudyUi.lp(Ui.dp(this, 46), -2))
            }
            // Up to 7 sprites spread over the full card width (as in the mockup); more scroll sideways.
            if (spread) summary.addView(sprites)
            else summary.addView(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(sprites) })
        }
        val results = StudyUi.column(this).apply {
            background = Ui.rounded(Ui.c(context, R.color.ag_card), 14, context)
            setPadding(Ui.dp(context, 10), Ui.dp(context, 4), Ui.dp(context, 10), Ui.dp(context, 4))
        }
        fun result(icon: Int, label: String, value: String, tone: Int, species: Int = 0, item: String? = null, shiny: Boolean = false) {
            if (results.childCount > 0) results.addView(StudyUi.line(this))
            val row = StudyUi.row(this).apply { setPadding(0, Ui.dp(context, 5), 0, Ui.dp(context, 5)) }
            val image = if (species > 0 || item != null) ImageView(this).apply {
                if (species > 0) Ui.staticSprite(context, species, shiny, this) else Ui.itemIcon(context, item!!, this)
            } else StudyUi.icon(this, icon, tone)
            row.addView(image, StudyUi.lp(Ui.dp(this, 22), Ui.dp(this, 22)).also { it.marginEnd = Ui.dp(this, 8) })
            row.addView(StudyUi.label(this, label, 12.5f, R.color.ag_text, 800), StudyUi.lp(0, -2, 1f))
            row.addView(StudyUi.label(this, value, 12.5f, R.color.ag_text, 900).apply { gravity = Gravity.END }, StudyUi.lp(0, -2, 1f))
            results.addView(row)
        }
        result(R.drawable.ic_bolt, "XP for the team", "+$xp", R.color.ag_sky)
        teamProgress.values.forEach { mon ->
            val from = mon.optInt("start"); val to = mon.optInt("level")
            result(R.drawable.ic_star, "${mon.optString("name")} Lv $from${if (to != from) " → $to" else ""}", if (mon.optBoolean("evolved")) "evolved" else "level up", R.color.ag_mauve, mon.optInt("species"), shiny = mon.optBoolean("shiny"))
        }
        result(R.drawable.ic_battle, "Defeated / caught", "$defeated / ${caught.size}", R.color.ag_sub1)
        result(R.drawable.ic_coin, "Coins", "+$coins ₽", R.color.ag_yellow)
        if (foundItems.isEmpty()) result(R.drawable.ic_gift, "Found", "No items", R.color.ag_teal)
        else foundItems.distinct().forEach { item -> result(R.drawable.ic_gift, "Found", com.husarp.ankigo.game.Items.name(item), R.color.ag_teal, item = item) }
        summary.addView(results, StudyUi.spaced(this, 6))
        val today = st?.reviewsToday ?: 0; val goal = (st?.settings?.dailyAverage ?: 100).coerceAtLeast(1)
        val goalRow = StudyUi.row(this).apply {
            background = Ui.rounded(Ui.c(context, R.color.ag_card), 12, context)
            setPadding(Ui.dp(context, 10), Ui.dp(context, 7), Ui.dp(context, 10), Ui.dp(context, 7))
        }
        goalRow.addView(BarView(this).apply {
            setGradient(Ui.c(context, R.color.ag_mauve), Ui.c(context, R.color.ag_pink))
            set(today.toFloat() / goal, Ui.c(context, R.color.ag_mauve), false)
        }, StudyUi.lp(0, Ui.dp(this, 8), 1f).also { it.marginEnd = Ui.dp(this, 8) })
        goalRow.addView(StudyUi.label(this, if (today >= goal) "Goal done · $today" else "$today / $goal", 12, R.color.ag_text, 900))
        goalRow.addView(Ui.chip(this, R.drawable.ic_flame, R.color.ag_red).apply { setText("${st?.streakDays ?: 0} days") }, StudyUi.lp(-2).also { it.marginStart = Ui.dp(this, 6) })
        summary.addView(goalRow, StudyUi.spaced(this, 6))
        summaryView = ScrollView(this).apply { addView(summary, FrameLayout.LayoutParams(StudyUi.pageWidth(context), -2, Gravity.CENTER_HORIZONTAL)); isVerticalScrollBarEnabled = false }.also { cardColumn.addView(it, StudyUi.lp(-1, 0, 1f)) }
        val next = decks.firstOrNull { it.id != deckId && it.total > 0 }
        buttons.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 10))
        buttons.layoutParams = LinearLayout.LayoutParams(StudyUi.pageWidth(this), -2).apply { gravity = Gravity.CENTER_HORIZONTAL }
        if (next != null) buttons.addView(StudyUi.primary(this, "Next deck: ${next.name} · ${next.total} due") {
            startActivity(Intent(this, StudyActivity::class.java).putExtra("deckId", next.id).putExtra("deckName", next.name)); finish()
        })
        else buttons.addView(StudyUi.primary(this, "Custom study in AnkiDroid") { openAnki() })
        val navigation = StudyUi.row(this)
        navigation.addView(StudyUi.smallButton(this, "Back to decks") { finish() }, StudyUi.lp(0, -2, 1f))
        val sync = StudyUi.row(this).apply {
            gravity = Gravity.CENTER
            background = Ui.rounded(Ui.c(context, R.color.ag_s0), 10, context)
            setPadding(Ui.dp(context, 10), Ui.dp(context, 7), Ui.dp(context, 10), Ui.dp(context, 7))
            contentDescription = "Sync now"; isFocusable = true
            setOnClickListener { runCatching { startActivity(Anki.syncIntent()) }.onFailure { Ui.toast(this@StudyActivity, "Open AnkiDroid to sync") } }
        }
        sync.addView(StudyUi.icon(this, R.drawable.ic_sync, R.color.ag_text), StudyUi.lp(Ui.dp(this, 14), Ui.dp(this, 14)).also { it.marginEnd = Ui.dp(this, 5) })
        sync.addView(StudyUi.label(this, "Sync now", 12, R.color.ag_text, 900))
        navigation.addView(sync, StudyUi.lp(0, -2, 1f).also { it.marginStart = Ui.dp(this, 6) })
        buttons.addView(navigation, StudyUi.spaced(this, 6))
        layout()
        if (learn > 0) main.postDelayed({ recheckLearning() }, 30_000)
    }
    private fun recheckLearning() {
        if (!finished || background || isDestroyed) return
        val token = generation
        io.execute {
            val available = runCatching { Anki.selectDeck(this, deckId); Anki.nextCards(this, deckId, 1).isNotEmpty() }.getOrDefault(false)
            main.post {
                if (!valid(token) || !finished || background) return@post
                if (available) buttons.addView(StudyUi.primary(this, "Continue · learning cards are ready") { loadNext() })
                else main.postDelayed({ recheckLearning() }, 30_000)
            }
        }
    }
    private fun openUrl(url: String) { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    private fun stopAudio() {
        soundQueue.clear(); audio?.release(); audio = null; speech?.stop()
        if (audioBusy) { audioBusy = false; audioIdle() }
    }
    /** The AV queue (sound files and speech, in order) just emptied or was stopped. */
    private fun audioIdle() { main.post { syncTiming() } }
    private fun playAv(items: List<TtsRules.Av>) { stopAudio(); soundQueue.addAll(items); audioBusy = items.isNotEmpty(); playNextSound() }
    private fun playNextSound() {
        val next = soundQueue.removeFirstOrNull() ?: return run { if (audioBusy) { audioBusy = false; audioIdle() } }
        next.speech?.let { item ->
            (speech ?: CardSpeech(this).also { speech = it }).speak(item) { playNextSound() }
            return
        }
        val found = Media.find(this, next.file ?: return playNextSound()) ?: return playNextSound()
        try {
            val p = MediaPlayer(); audio = p
            if (found.file != null) p.setDataSource(found.file.absolutePath) else p.setDataSource(this, found.uri!!)
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { it.release(); audio = null; playNextSound() }
            p.setOnErrorListener { mp, _, _ -> mp.release(); audio = null; playNextSound(); true }
            p.prepareAsync()
        } catch (_: Exception) { audio?.release(); audio = null; playNextSound() }
    }
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == 0) { LiveSession.restart(this); loadNext() }
    }
    // ---- Card JavaScript API (study/JsApi.kt + JsApiScript.kt): scripts read a snapshot; every action goes through flip / answer / writeAction ----
    @Volatile private var jsSnap: JsApi.Snapshot? = null   // what card scripts may read, rebuilt on the main thread whenever a side is shown
    private var jsBase = JsApi.Snapshot()                   // the shown card's data (counts, scheduling, tags, deck name), set in loadNext
    private var jsPageSeq = 0
    private var jsLang = java.util.Locale.getDefault().toLanguageTag(); private var jsRate = 1f; private var jsPitch = 1f
    private fun isNight() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    /** A side is about to load: publish its snapshot and return the page token its scripts must present. */
    private fun jsShow(answer: Boolean): Int {
        val metered = runCatching { (getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered }.getOrDefault(false)
        jsSnap = jsBase.copy(page = ++jsPageSeq, answer = answer, night = isNight(), metered = metered)
        return jsPageSeq
    }
    /** Runs on the WebView's bridge thread: answers from the snapshot only, and hands any action to the main thread. */
    private fun jsCall(page: Int, endpoint: String, data: String): String {
        val reply = JsApi.call(page, endpoint, data, jsSnap, speech?.isSpeaking == true)
        reply.action?.let { a -> main.post { if (jsSnap?.page == page) jsDo(a) } }
        return reply.text
    }
    private fun jsDo(a: JsApi.Action) {
        when (a) {
            JsApi.Action.ShowAnswer -> flip()
            is JsApi.Action.Answer -> if (!flipped) flip() else JsApi.easeFor(card?.buttons ?: 4, a.rating)?.let { answer(it) }
            JsApi.Action.Bury -> jsWrite("bury", 0)
            JsApi.Action.Suspend -> jsWrite("suspend", 0)
            is JsApi.Action.Flag -> jsWrite("flag", a.value)
            is JsApi.Action.Toast -> android.widget.Toast.makeText(applicationContext, a.text, if (a.short) android.widget.Toast.LENGTH_SHORT else android.widget.Toast.LENGTH_LONG).show()
            is JsApi.Action.Speak -> {
                val say = TtsRules.Av(speech = TtsRules.Item(a.text, jsLang, jsRate, jsPitch))
                if (a.add && audioBusy) soundQueue.add(say) else playAv(listOf(say))
            }
            JsApi.Action.StopSpeech -> stopAudio()
            is JsApi.Action.Language -> jsLang = a.tag
            is JsApi.Action.Pitch -> jsPitch = a.value
            is JsApi.Action.SpeechRate -> jsRate = a.value
            is JsApi.Action.Scrollbar -> if (a.horizontal) web.isHorizontalScrollBarEnabled = a.on else web.isVerticalScrollBarEnabled = a.on
        }
    }
    /** Bury / suspend / flag from card JavaScript: same function as the action sheet, and only when the card is idle (it locks the screen itself). */
    private fun jsWrite(action: String, flag: Int) { if (!locked && !mediaBlocked && card != null && !finished) writeAction(action, flag) }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (web.hasFocus() && event.isPrintingKey && keyCode !in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_4) return super.onKeyUp(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER) { flip(); return true }
        if (keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_4 && flipped) { answer(keyCode - KeyEvent.KEYCODE_1 + 1); return true }
        return super.onKeyUp(keyCode, event)
    }
}
