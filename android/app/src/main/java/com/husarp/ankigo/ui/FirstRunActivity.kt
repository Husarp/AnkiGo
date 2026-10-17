package com.husarp.ankigo.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.DataStore
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.Encounters
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Tuning

/** Pokédex numbers of the species in [gens], for picture downloads. */
internal fun speciesNums(data: GameData, gens: Set<Int>): List<Int> =
    data.species.values.filter { it.gen in gens }.map { it.num }.sorted()

/** True when all but a few (5 %) of [total] static pictures are there; the rest load on demand. */
internal fun picturesMostlyThere(missing: Int, total: Int): Boolean = missing <= total / 20

/** True when all but a few (5 %) static pictures of [nums] are on disk; the rest load on demand. */
internal fun staticsMostlyPresent(ctx: Context, nums: List<Int>): Boolean =
    picturesMostlyThere(nums.count { !Sprites.staticFile(ctx, it).isFile }, nums.size)

/** The first-run download (data, then pictures). Lives here, not in the activity, so rotating the screen doesn't restart it. */
internal object FirstRunJob {
    val job = BgJob()

    fun start(ctx: Context, gens: Set<Int>) {
        if (job.running) return
        val app = ctx.applicationContext
        job.begin("Starting…")
        Thread {
            try {
                if (!DataStore.hasData(app)) {
                    val begun = android.os.SystemClock.elapsedRealtime()
                    val r = DataStore.download(app) { done, total, name ->
                        val f = if (total > 0) done.toFloat() / total else 0f
                        job.progress(f * 0.8f, if (name == "Building") "Building the Pokémon list…" else "Game data · ${DisplayNumbers.progress(done, total, android.os.SystemClock.elapsedRealtime() - begun)}")
                    }
                    if (r.isFailure) { job.fail(r.exceptionOrNull()?.message ?: "Couldn't download the game data"); return@Thread }
                }
                val data = DataStore.load(app)
                if (data == null) { job.fail("Couldn't read the game data. Try again."); return@Thread }
                if (!Sprites.enabled(app)) { job.done(); return@Thread }
                val nums = speciesNums(data, gens)
                job.progress(0.8f, "Downloading pictures…")
                val begun = android.os.SystemClock.elapsedRealtime()
                Sprites.downloadStatics(app, nums, bytesProgress = { d, t ->
                    job.progress(0.8f + 0.2f * (if (t > 0) d.toFloat() / t else 1f), "Pictures (total estimated) · ${DisplayNumbers.progress(d, t, android.os.SystemClock.elapsedRealtime() - begun)}")
                }, stop = { job.cancelled }) { _, _ -> }
                // Cancel (only Settings offers it): what was downloaded is kept
                if (job.cancelled) { job.done("Stopped. What was downloaded is kept."); return@Thread }
                // same rule as the step check (openStep): "done" must mean the step is really done, or the screen bounces back to step 2
                if (Sprites.enabled(app) && !staticsMostlyPresent(app, nums)) job.fail("Many pictures didn't download. Check the internet connection and try again.")
                else job.done()
            } catch (e: Throwable) {
                job.fail("Download stopped: ${e.message ?: e.javaClass.simpleName}")
            }
        }.apply { isDaemon = true; name = "firstrun-download" }.start()
    }
}

/**
 * First start, one activity whose steps swap in place: 1 AnkiDroid access, 2 data + pictures download, 3 starter,
 * 4 split-screen hint. Starts at the first step that isn't done and finishes with RESULT_OK after step 4.
 */
class FirstRunActivity : AgActivity() {
    override val showsUpdateBanner = false
    private val job get() = FirstRunJob.job
    private val prefs by lazy { getSharedPreferences("firstrun", MODE_PRIVATE) }

    private lateinit var content: LinearLayout
    private lateinit var actions: LinearLayout
    private var step = 0                 // 0 = still working out where to start, 5 = save problem
    private var selGen = 1
    private var selNum = 0
    private var denied = false           // permission asked and refused
    private var ankimonSteps = false     // the Ankimon steps are unfolded on the starter step
    private var picking = false          // starter being saved
    private var afterLoading = false     // GameRepo.load running after the download
    private var progress: ProgressBlock? = null

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        live = java.lang.ref.WeakReference(this)
        Anki.load(this)
        setContentView(buildContent())

        if (savedInstanceState != null) {
            step = savedInstanceState.getInt("step")
            selGen = savedInstanceState.getInt("gen", 1)
            selNum = savedInstanceState.getInt("num", 0)
            denied = savedInstanceState.getBoolean("denied")
        }
        render()
        if (step == 0) GameRepo.load(this) { goTo(openStep()) }
    }

    /** The screen's views (content + actions); onCreate sets them as the Activity's content. */
    internal fun buildContent(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(this@FirstRunActivity, R.color.ag_base)) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(12))
        }
        actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(16))
        }
        root.addView(ScrollView(this).apply { addView(content) }, Form.lp(h = 0, weight = 1f))
        root.addView(actions)
        return root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", step); outState.putInt("gen", selGen); outState.putInt("num", selNum)
        outState.putBoolean("denied", denied)
    }

    override fun onStart() {
        super.onStart()
        job.listener = { onJob() }
        if (step == 2) onJob()
    }

    override fun onStop() {
        job.listener = null
        super.onStop()
    }

    override fun onDestroy() {
        if (live?.get() === this) live = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_ANKIMON) return
        val cb = pendingPick ?: return
        pendingPick = null
        cb(resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        // back from the Play Store or the app settings with the permission now granted
        if (step == 1 && Anki.hasPermission(this)) goTo(openStep())
        else if (step == 1) render()
    }

    // ---------- flow ----------

    private fun permOk() = Anki.hasPermission(this) || prefs.getBoolean("skipPerm", false)

    private fun gens(): Set<Int> = GameRepo.state?.settings?.gens?.toSet() ?: Tuning.DEFAULT_GENS

    private fun staticsOk(): Boolean {
        val d = GameRepo.data ?: return false
        return !Sprites.enabled(this) || staticsMostlyPresent(this, speciesNums(d, gens()))
    }

    /** The first step that isn't done yet. */
    private fun openStep(): Int = when {
        GameRepo.status == GameRepo.Status.SAVE_ERROR -> 5
        !permOk() -> 1
        GameRepo.status == GameRepo.Status.NEED_DATA || GameRepo.data == null || !staticsOk() -> 2
        GameRepo.status == GameRepo.Status.NEED_STARTER -> 3
        else -> 4
    }

    private fun goTo(n: Int) {
        if (n == 2 && !job.running) job.reset() // don't show an old finished/failed result
        step = n
        render()
    }

    private fun onJob() {
        if (step != 2) return
        val p = progress
        if (job.state == BgJob.State.RUNNING && p != null) p.show(job.fraction, job.label) else render()
    }

    private fun render() {
        content.removeAllViews()
        actions.removeAllViews()
        progress = null
        when (step) {
            0 -> content.addView(Form.body(this, "Loading…"))
            1 -> stepAnki()
            2 -> stepDownload()
            3 -> stepStarter()
            4 -> stepSplit()
            5 -> stepSaveProblem()
        }
    }

    private fun header(title: String, n: Int) {
        content.addView(Ui.text(this, 12f, 900, R.color.ag_sub0).apply {
            text = "STEP $n OF 4"
            letterSpacing = 0.06f
        })
        content.addView(Ui.text(this, 28f, 900, R.color.ag_text).apply {
            text = title
            letterSpacing = -0.014f
        }, Form.lp(this, 6))
    }

    // ---------- step 1: AnkiDroid ----------

    private fun stepAnki() {
        header("AnkiGo", 1)
        content.addView(Form.body(this, "Study here or in AnkiDroid; every answer charges a Pokémon battle.", 15), Form.lp(this, 10))
        content.addView(Form.body(this, "When you study here, AnkiGo sends your answers to AnkiDroid’s scheduler. Companion mode reads your answers.", 13, R.color.ag_sub0), Form.lp(this, 8))
        if (Anki.installed(this).isEmpty()) {
            val card = Ui.card(this).apply { setPadding(dp(16), dp(14), dp(16), dp(14)) }
            card.addView(Ui.text(this, 16f, 800, R.color.ag_text).apply { text = "Install AnkiDroid first" })
            card.addView(Form.body(this, "AnkiGo needs AnkiDroid to see your answers. Come back here after installing it.", 13, R.color.ag_sub0), Form.lp(this, 4))
            content.addView(card, Form.lp(this, 18))
            actions.addView(Form.primary(this, "Open Play Store") { openPlayStore() })
        } else {
            if (denied) {
                content.addView(Form.body(this, "AnkiDroid access wasn't allowed. Tap the button to try again; if Android shows no question, open the app settings and allow it under Permissions.", 13, R.color.ag_red), Form.lp(this, 14))
            }
            actions.addView(Form.primary(this, "Allow access to AnkiDroid") {
                requestPermissions(arrayOf(Anki.permission), REQ_ANKI)
            })
            if (denied) actions.addView(Form.secondary(this, "Open app settings", onPage = true) {
                runCatching { startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            }, Form.lp(this, 8))
        }
        actions.addView(Form.secondary(this, "Skip for now", R.color.ag_sub0, onPage = true) {
            prefs.edit().putBoolean("skipPerm", true).apply()
            goTo(openStep())
        }, Form.lp(this, 8))
    }

    private fun openPlayStore() {
        val pkg = "com.ichi2.anki"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (_: Exception) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_ANKI) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) { denied = false; goTo(openStep()) }
        else { denied = true; if (step == 1) render() }
    }

    // ---------- step 2: download ----------

    private fun stepDownload() {
        header("Getting the game ready", 2)
        content.addView(Form.body(this, "Pokémon data (about 4.3 MB) and pictures (about 1 MB). Battle animations download as you meet new Pokémon.", 14), Form.lp(this, 10))
        actions.addView(Form.secondary(this, "Skip pictures for now", onPage = true) {
            Sprites.setEnabled(this, false)
            if (DataStore.hasData(this)) GameRepo.load(this) { goTo(openStep()) }
            else {
                content.addView(Form.body(this, "Game data is still needed for battles. You can study and use diagnostics without it."))
            }
        }, Form.lp(this, 8))
        actions.addView(Form.secondary(this, "Study without the game", onPage = true) {
            Sprites.setEnabled(this, false)
            prefs.edit().putBoolean("studyOnly", true).apply()
            startActivity(Intent(this, StudyDecksActivity::class.java)); finish()
        }, Form.lp(this, 8))
        val network = getSystemService(android.net.ConnectivityManager::class.java)
        val connection = when {
            network.activeNetwork == null -> "Offline. Skip pictures now; download them later in Settings."
            network.isActiveNetworkMetered -> "Mobile or metered data. You can skip pictures and download them later on Wi-Fi."
            else -> "Pictures are optional; download them later in Settings if you skip."
        }
        content.addView(Form.body(this, connection, 12), Form.lp(this, 8))
        when (job.state) {
            BgJob.State.IDLE -> { FirstRunJob.start(this, gens()) } // begin() pings, which renders again
            BgJob.State.RUNNING -> {
                val p = ProgressBlock(this)
                p.show(job.fraction, job.label)
                progress = p
                content.addView(p, Form.lp(this, 22))
            }
            BgJob.State.FAILED -> {
                content.addView(Form.body(this, job.error ?: "The download failed.", 14, R.color.ag_red), Form.lp(this, 18))
                actions.addView(Form.primary(this, "Try again") { job.reset() })
            }
            BgJob.State.DONE -> {
                content.addView(Form.body(this, "Done. Loading…", 14), Form.lp(this, 18))
                if (!afterLoading) {
                    afterLoading = true
                    GameRepo.load(this) {
                        afterLoading = false
                        job.reset()
                        val next = openStep()
                        // data still not seen by the game: say so instead of looping forever
                        if (next == 2) job.fail("The game data was downloaded but couldn't be loaded. Close AnkiGo and open it again.")
                        else goTo(next)
                    }
                }
            }
        }
    }

    // ---------- step 3: starter ----------

    private fun stepStarter() {
        val data = GameRepo.data
        if (data == null) { goTo(2); return }
        header("Choose your starter", 3)
        content.addView(Form.body(this, "Your first Pokémon (Lv 5). You'll catch more as you study.", 14), Form.lp(this, 8))

        val gens = (GameRepo.state?.settings?.gens ?: Tuning.DEFAULT_GENS).filter { Encounters.STARTER_CHOICES.containsKey(it) }.sorted()
            .ifEmpty { listOf(1) }
        if (selGen !in gens) selGen = gens.first()
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        gens.forEachIndexed { i, g ->
            val chip = Form.toggleChip(this, "Gen $g", g == selGen) {
                if (selGen != g) { selGen = g; selNum = 0; render() }
            }
            chips.addView(chip, Form.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { if (i > 0) it.marginStart = dp(5) })
        }
        content.addView(GenScroller(this, chips), Form.lp(this, 6)) // chips are 48 dp touch height around a 32 dp look

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Encounters.STARTER_CHOICES.getValue(selGen).forEachIndexed { i, num ->
            row.addView(starterCard(data, num), Form.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { if (i > 0) it.marginStart = dp(8) })
        }
        content.addView(row, Form.lp(this, 14))

        // Ankimon on the PC? The same steps as Settings → Ankimon PC sync, folded away until asked for (R3-V3b)
        content.addView(Form.secondary(this, if (ankimonSteps) "Hide the Ankimon steps" else "Play Ankimon on PC? Show the steps", onPage = true) {
            ankimonSteps = !ankimonSteps; render()
        }, Form.lp(this, 12))
        if (ankimonSteps) content.addView(com.husarp.ankigo.help.HelpViews.ankimonGuide(this, withWarnings = false), Form.lp(this, 10))

        val sp = data.species[selNum]
        val btn = Form.primary(this, if (sp != null) "Choose ${sp.name}" else "Tap a Pokémon") { choose(selNum) }
        Form.enable(btn, sp != null && !picking)
        actions.addView(btn)
        val ankimon = Form.secondary(this, "I play Ankimon on PC: import my save", onPage = true) { importAnkimon() }
        Form.enable(ankimon, !picking)
        actions.addView(ankimon, Form.lp(this, 8))
    }

    /** Ankimon import instead of a starter: the picker result may arrive after a rotation, so nothing here captures `this`. */
    private fun importAnkimon() {
        if (picking) return
        AnkimonImportFlow.start(
            applicationContext,
            launch = { intent, cb ->
                pendingPick = cb
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQ_ANKIMON)
                } catch (e: Exception) {
                    pendingPick = null
                    throw e
                }
            },
            ui = { live?.get() },
            onDone = { imported -> live?.get()?.afterImport(imported) },
        )
    }

    /** Imported a save that came with a team: the starter question is answered, go on as if a starter was picked. */
    private fun afterImport(imported: Boolean) {
        if (step != 3) return
        if (imported && GameRepo.state?.starterChosen == true) goTo(4) else render()
    }

    private fun starterCard(data: GameData, num: Int): View {
        val sp = data.species[num]
        val on = num == selNum
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(10), dp(6), dp(10))
            background = Ui.rounded(Ui.c(this@FirstRunActivity, R.color.ag_card), 14, this@FirstRunActivity).apply {
                setStroke(dp(2), Ui.c(this@FirstRunActivity, if (on) R.color.ag_mauve else R.color.ag_card))
            }
            isClickable = true
            setOnClickListener { if (!picking) { selNum = num; render() } }
        }
        val img = ImageView(this)
        card.addView(img, Form.lp(h = dp(96)))
        Ui.staticSprite(this, num, false, img)
        card.addView(Ui.text(this, 14f, 800, R.color.ag_text).apply {
            text = sp?.name ?: "#$num"
            gravity = Gravity.CENTER
            maxLines = 1
        }, Form.lp(this, 6))
        val types = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        sp?.types?.forEach { t -> types.addView(Ui.typeChip(this, t), Form.lp(this, 4, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        card.addView(types, Form.lp(this, 2))
        return card
    }

    private fun choose(num: Int) {
        val data = GameRepo.data ?: return
        val sp = data.species[num] ?: return
        if (picking) return
        picking = true
        render()
        GameRepo.load(this) {
            picking = false
            val st = GameRepo.state
            if (st != null && !st.starterChosen) GameRepo.act(this) { it.pickStarter(num) }
            runCatching { Sprites.prefetch(this, sp, false) }
            goTo(4)
        }
    }

    // ---------- step 4: split screen ----------

    private fun stepSplit() {
        header("Use it in split screen", 4)
        content.addView(SplitPicture(this), Form.lp(h = dp(170)).also { it.topMargin = dp(16) })
        val steps = listOf(
            "Open AnkiDroid.",
            "Recent apps → AnkiDroid's icon → Split screen.",
            "Pick AnkiGo for the bottom half. Study as usual.",
        )
        steps.forEachIndexed { i, s ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            line.addView(Ui.text(this, 12f, 900, R.color.ag_crust).apply {
                text = "${i + 1}"
                gravity = Gravity.CENTER
                background = Ui.rounded(Ui.c(this@FirstRunActivity, R.color.ag_mauve), 10, this@FirstRunActivity)
            }, Form.lp(dp(20), dp(20)))
            line.addView(Form.body(this, s, 14, R.color.ag_text), Form.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(10) })
            content.addView(line, Form.lp(this, 14))
        }
        actions.addView(Form.primary(this, "Start") {
            setResult(RESULT_OK)
            finish()
        })
    }

    // ---------- save problem (not a numbered step) ----------

    private fun stepSaveProblem() {
        content.addView(Ui.text(this, 28f, 900, R.color.ag_text).apply { text = "Your save can't be read" })
        content.addView(Form.body(this, (GameRepo.error ?: "The save file is damaged.") + "\n\nNothing was deleted. Restore a backup in Settings.", 14), Form.lp(this, 10))
        actions.addView(Form.primary(this, "Open Settings") { startActivity(Intent(this, SettingsActivity::class.java)) })
    }

    companion object {
        private const val REQ_ANKI = 1
        private const val REQ_ANKIMON = 2
        private var live: java.lang.ref.WeakReference<FirstRunActivity>? = null
        private var pendingPick: ((Int, Intent?) -> Unit)? = null
    }
}

/** The generation chips in a row that scrolls sideways when there are too many for a narrow panel. */
private class GenScroller(ctx: Context, chips: LinearLayout) : HorizontalScrollView(ctx) {
    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true
        // each chip keeps at least 56 dp so nine of them scroll instead of squashing
        for (i in 0 until chips.childCount) chips.getChildAt(i).minimumWidth = Ui.dp(ctx, 56)
        addView(chips, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}

/** Two rounded boxes, AnkiDroid on top and AnkiGo below, as a phone in split screen. */
private class SplitPicture(ctx: Context) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = Ui.dp(ctx, 2f) }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(ctx, 800); textAlign = Paint.Align.CENTER; textSize = Ui.dp(ctx, 15f) }
    private val r = RectF()

    private fun box(c: Canvas, l: Float, t: Float, rt: Float, b: Float, bg: Int, stroke: Int, text: String, textColor: Int) {
        r.set(l, t, rt, b)
        val rad = Ui.dp(context, 12f)
        fill.color = bg; c.drawRoundRect(r, rad, rad, fill)
        line.color = stroke; c.drawRoundRect(r, rad, rad, line)
        ink.color = textColor
        c.drawText(text, r.centerX(), r.centerY() - (ink.descent() + ink.ascent()) / 2f, ink)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val phoneW = minOf(w, h * 0.62f)
        val left = (w - phoneW) / 2f
        val right = left + phoneW
        val gap = Ui.dp(context, 8f)
        val pad = Ui.dp(context, 6f)
        // the phone frame
        r.set(left, 0f, right, h)
        val rad = Ui.dp(context, 18f)
        fill.color = Ui.c(context, R.color.ag_mantle); canvas.drawRoundRect(r, rad, rad, fill)
        line.color = Ui.c(context, R.color.ag_s2); canvas.drawRoundRect(r, rad, rad, line)
        val mid = h / 2f
        box(canvas, left + pad, pad, right - pad, mid - gap / 2, Ui.c(context, R.color.ag_card), Ui.c(context, R.color.ag_s1), "AnkiDroid", Ui.c(context, R.color.ag_text))
        box(canvas, left + pad, mid + gap / 2, right - pad, h - pad, Ui.c(context, R.color.ag_mauve), Ui.c(context, R.color.ag_mauve), "AnkiGo", Ui.c(context, R.color.ag_crust))
    }
}
