package com.husarp.ankigo.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.ActionResult
import com.husarp.ankigo.game.Game
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.update.Updates

// TICKET T21 owns this file: Team / Pokédex / Shop / Settings as "pages" that live in an Activity or a floating card (T24).

/** Where a page lives: an Activity (normal screen) or the floating card over other apps. */
interface PageHost {
    /** The Activity when the page fills one; null in a floating card. */
    val hostActivity: Activity?

    /** Closes the page (finishes the Activity / closes the floating card). */
    fun closePage()

    /** Starts [intent] for a result (file pickers); [onResult] (resultCode, data) is called on the main thread. */
    fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit)
}

/**
 * Content of one screen. It IS a Context (a ContextWrapper around [base]) so code that passes `this` to Ui.* keeps
 * working. [base] is the Activity, or a GameTheme-themed context in a floating card.
 *
 * Frame: [buildBar] on top, [buildBody] below, and (when [needsGame]) a "game not ready" cover over the body.
 * [render] is only called when the game is READY. The bottom bar is not part of the page: the host adds it.
 * The host calls [onShow] / [onHide] when the page becomes visible / hidden (Activity resume / pause) and [onGone] once
 * at the end; subclasses that override them must call super.
 */
abstract class GamePage(base: Context, val host: PageHost) : ContextWrapper(base) {
    /** The bottom-bar tab to highlight; null = no bottom bar (Settings). */
    abstract val tab: BottomNav.Tab?

    /** False for Settings: it works without a loaded game, so there is no "game not ready" cover and no re-render on changes. */
    open val needsGame: Boolean = true

    protected abstract fun buildBar(): View
    protected abstract fun buildBody(): View
    protected abstract fun render()

    val data: GameData get() = GameRepo.data!!
    val state: GameState get() = GameRepo.state!!

    /** False after [onGone]; replaces the old isFinishing / isDestroyed checks. */
    var alive: Boolean = true
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val renderNow = Runnable { doRender() }
    private val listener: () -> Unit = { requestRender() }
    private var content: View? = null
    private var notReady: TextView? = null

    /** Bar + body (+ the not-ready cover), built on first use. Add it to the host's layout. */
    val root: View by lazy(LazyThreadSafetyMode.NONE) { buildRoot() }

    private fun buildRoot(): View {
        val bar = buildBar()
        val body = buildBody()
        content = body
        val middle: View = if (needsGame) {
            val cover = Ui.text(this, 14f, 700, R.color.ag_sub0).apply {
                gravity = Gravity.CENTER
                setPadding(Ui.dp(this@GamePage, 24), Ui.dp(this@GamePage, 24), Ui.dp(this@GamePage, 24), Ui.dp(this@GamePage, 24))
            }
            notReady = cover
            FrameLayout(this).apply {
                addView(body, FrameLayout.LayoutParams(-1, -1))
                addView(cover, FrameLayout.LayoutParams(-1, -1))
            }
        } else {
            body
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(this@GamePage, R.color.ag_base))
            addView(bar, LinearLayout.LayoutParams(-1, -2))
            addView(middle, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        if (needsGame) GameRepo.load(this) { requestRender() }
        return column
    }

    /** The page is visible and in front: listen to GameRepo and draw. */
    open fun onShow() {
        if (needsGame) {
            GameRepo.addListener(listener)
            requestRender()
        }
    }

    /** The page is paused / hidden: stop listening. */
    open fun onHide() {
        GameRepo.removeListener(listener)
    }

    /** The page is discarded for good. */
    open fun onGone() {
        alive = false
        handler.removeCallbacks(renderNow)
        GameRepo.removeListener(listener)
    }

    /** Re-renders once per message-loop turn, however many changes came in. */
    fun requestRender() {
        handler.removeCallbacks(renderNow)
        handler.post(renderNow)
    }

    private fun doRender() {
        if (!alive) return
        val body = content ?: return
        val cover = notReady ?: return
        val ok = GameRepo.status == GameRepo.Status.READY && GameRepo.data != null && GameRepo.state != null
        body.visibility = if (ok) View.VISIBLE else View.GONE
        cover.visibility = if (ok) View.GONE else View.VISIBLE
        cover.text = when (GameRepo.status) {
            GameRepo.Status.LOADING -> "Loading…"
            GameRepo.Status.NEED_DATA -> "Finish the first-run download on the Battle tab first."
            GameRepo.Status.NEED_STARTER -> "Pick your starter on the Battle tab first."
            GameRepo.Status.SAVE_ERROR -> "The save couldn't be read. Open the Battle tab for options."
            GameRepo.Status.READY -> "Something went wrong loading the game."
        }
        if (ok) render()
    }

    /** Runs an action through GameRepo (saves + notifies), shows its message, re-renders. */
    fun act(f: (Game) -> ActionResult): ActionResult {
        val r = GameRepo.run(this, f)
        report(r.message, r.events)
        requestRender()
        return r
    }

    /** Message toast (+ earned badges); an evolution gets a small dialog with the new sprite instead. */
    fun report(message: String, events: List<GameEvent> = emptyList()) {
        val evo = events.filterIsInstance<GameEvent.Evolved>().firstOrNull()
        val badges = events.filterIsInstance<GameEvent.BadgeEarned>().joinToString { it.title }
        if (evo != null) {
            Pickers.evolved(this, evo, state.mon(evo.uid)?.shiny == true)
            if (badges.isNotEmpty()) Ui.toast(this, "Badge: $badges")
        } else {
            Ui.toast(this, message + if (badges.isNotEmpty()) "\nBadge: $badges" else "")
        }
    }

    /** Opens another Activity of the app; a floating card has no Activity, so the task flag is needed there. */
    fun startScreen(intent: Intent) {
        if (host.hostActivity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}

/**
 * Thin Activity around one [GamePage]: content = page + the bottom bar (when the page has a tab). Forwards the lifecycle
 * (resume -> onShow, pause -> onHide, destroy -> onGone), the Updates hooks, and activity results to [startForResult] callbacks.
 */
abstract class PageActivity : Activity(), PageHost {
    /** Builds the page; the Activity is the base context and the host: `MyPage(this, this)`. */
    abstract fun createPage(): GamePage

    private lateinit var page: GamePage

    override val hostActivity: Activity? get() = this

    override fun closePage() = finish()

    override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) {
        val code = nextCode++
        callbacks.put(code, onResult)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, code)
        } catch (e: Exception) {
            callbacks.remove(code)
            throw e
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val cb = callbacks.get(requestCode) ?: return
        callbacks.remove(requestCode)
        cb(resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = createPage()
        val tab = page.tab
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(this@PageActivity, R.color.ag_base))
            addView(page.root, LinearLayout.LayoutParams(-1, 0, 1f))
            if (tab != null) addView(BottomNav.create(this@PageActivity, tab), LinearLayout.LayoutParams(-1, -2))
        })
    }

    override fun onStart() {
        super.onStart()
        BattleScreen.setVisible(this, true) // the floating battle window stays hidden over AnkiGo's own screens
    }

    override fun onResume() {
        super.onResume()
        Updates.onResume(this)
        page.onShow()
    }

    override fun onStop() {
        BattleScreen.setVisible(this, false)
        super.onStop()
    }

    override fun onPause() {
        page.onHide()
        Updates.onPause(this)
        super.onPause()
    }

    override fun onDestroy() {
        BattleScreen.setVisible(this, false)
        page.onGone()
        super.onDestroy()
    }

    private companion object {
        // Process-wide so a result still arrives after the Activity was recreated (rotation / split-screen resize) while a
        // file picker was open. Callbacks must therefore not capture a page that can be recreated.
        val callbacks = SparseArray<(Int, Intent?) -> Unit>()
        var nextCode = 100
    }
}
