package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.AskChoice
import com.husarp.ankigo.game.CatchChoice
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.PendingAsk
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.AppDialog
import com.husarp.ankigo.ui.CatchAskPlan
import com.husarp.ankigo.ui.CatchAskPopup
import com.husarp.ankigo.ui.CatchSummary
import com.husarp.ankigo.ui.CatchTilesView
import com.husarp.ankigo.ui.ReplacePlan
import com.husarp.ankigo.ui.ReplaceWindow
import com.husarp.ankigo.ui.SettingsCatalog
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R19-P (PLAN 281, picks 1C and 2B): the "Ask me" pop-up when a fight ends, the "Release the old one?" window, the settings row, and the
 * "Your call" result row. PNGs: r19p-<what>-<size>-<theme>. Fake Pokémon (Birdie, species 16) on the fake game; the dialog cards are the real
 * views on a scrim over a Study-like backdrop. The flow checks click the real buttons of the same views.
 */
class R19PAskMeShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val sizes = listOf(Size.PHONE, Size.SPLIT, Size.FLOAT)

    private val data get() = GameRepo.data!!
    private val st get() = GameRepo.state!!

    private fun mon(species: Int, level: Int, uid: String, iv: Int, shiny: Boolean = false): Mon {
        val b = TestData.mon(data, species, level, listOf("tackle"), uid)
        val a = IntArray(6); var left = iv
        for (i in a.indices) { a[i] = minOf(31, left); left -= a[i] }
        return Mon(uid = uid, species = species, name = b.name, level = level, xp = 0, ivs = Stats(a[0], a[1], a[2], a[3], a[4], a[5]), evs = Stats.ZERO,
            nature = Nature.HARDY, gender = 'M', shiny = shiny, ability = "Testability", moves = mutableListOf("tackle"), hp = 1,
            caughtAt = 1_760_000_000_000L).also { it.hp = StatCalc.maxHp(data, it) }
    }

    /** What the Study screen shows behind the pop-up: the title bar, the result row ("Your call"), the next card, Continue. */
    private fun backdrop(ctx: Context): View {
        val xp = listOf(CatchSummary.XpLine("m", "Mainy", 120, 7, false))
        val summary = CatchSummary(false, "Birdie", null, null, xp, 10, CatchSummary.NEXT_TEXT, ask = true)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12), Ui.dp(ctx, 12))
            addView(Ui.text(ctx, 16f, 900, R.color.ag_text).apply { text = "Spanish · Vocabulary" })
            addView(CatchTilesView(ctx).also { it.bindRow(summary, next = false, rowH = Ui.dp(ctx, 36), radiusDp = 12f) },
                LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(ctx, 10) })
            addView(Ui.text(ctx, 18f, 900, R.color.ag_text).apply {
                text = "Capital of Mongolia?"; gravity = Gravity.CENTER
                background = Ui.rounded(Ui.c(ctx, R.color.ag_card), 14, ctx)
            }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 200)).also { it.topMargin = Ui.dp(ctx, 10) })
            addView(Ui.text(ctx, 15f, 900, R.color.ag_crust).apply {
                text = "Continue"; gravity = Gravity.CENTER
                background = Ui.rounded(Ui.c(ctx, R.color.ag_mauve), 14, ctx)
            }, LinearLayout.LayoutParams(-1, Ui.dp(ctx, 52)).also { it.topMargin = Ui.dp(ctx, 10) })
        }
    }

    private fun over(ctx: Context, d: AppDialog): View = FrameLayout(ctx).apply {
        addView(backdrop(ctx), FrameLayout.LayoutParams(-1, -1))
        addView(AppDialogShots.scene(ctx, d), FrameLayout.LayoutParams(-1, -1))
    }

    private fun askDialog(ctx: Context, wild: Mon, tweak: () -> Unit = {}): AppDialog {
        st.mons += mon(16, 18, "old16", 142); st.caught += 16
        tweak()
        val m = CatchAskPlan.of(data, st, PendingAsk(PendingAsk.Kind.CATCH, wild.uid, wild, Tier.NORMAL))!!
        return AppDialog.Builder(ctx).setTitle(m.title).setView(CatchAskPopup.content(ctx, data, m) {}).create()
    }

    // ---- 1C: the pop-up

    @Test fun popUp() = paparazzi.shoot("r19p-ask", sizes) { ctx -> over(ctx, askDialog(ctx, mon(16, 34, "w", 160))) }

    @Test fun popUpBetterByIvsOnly() = paparazzi.shoot("r19p-ask-iv", sizes) { ctx -> over(ctx, askDialog(ctx, mon(16, 12, "w", 171))) }

    @Test fun popUpWhenTheOldOneIsShiny() = paparazzi.shoot("r19p-ask-shiny", sizes) { ctx ->
        over(ctx, askDialog(ctx, mon(16, 34, "w", 160)) { st.mon("old16")!!.shiny = true })
    }

    // ---- 2B: the release window

    private fun replaceDialog(ctx: Context, keep: Boolean, team: Boolean = true, fav: Boolean = true, share: Boolean = true): AppDialog {
        val old = mon(16, 18, "old16", 142); old.favourite = fav
        val new = mon(16, 34, "new16", 160)
        st.mons += old; st.mons += new; st.caught += 16
        if (team) st.team.let { if (it.size > 1) it[1] = "old16" else it += "old16" }
        if (share) st.settings.xpShareUid = "old16"
        val m = ReplacePlan.of(data, st, PendingAsk(PendingAsk.Kind.REPLACE, "new16", oldUid = "old16"))!!
        return AppDialog.Builder(ctx).setTitle("Release the old one?").setView(ReplaceWindow.content(ctx, data, m, keepStart = keep, onPick = {})).create()
    }

    @Test fun releaseTicked() = paparazzi.shoot("r19p-replace-ticked", sizes) { ctx -> over(ctx, replaceDialog(ctx, keep = true)) }

    @Test fun releaseUnticked() = paparazzi.shoot("r19p-replace-unticked", sizes) { ctx -> over(ctx, replaceDialog(ctx, keep = false)) }

    @Test fun releaseHeartOnly() = paparazzi.shoot("r19p-replace-heart", listOf(Size.PHONE)) { ctx ->
        over(ctx, replaceDialog(ctx, keep = true, team = false, fav = true, share = false))
    }

    // ---- the settings row and the result row

    @Test fun settingsRow() {
        paparazzi.shootPage("r19p-settings", listOf(Variants.PHONE)) { ctx ->
            CatchChoice.apply(GameRepo.state!!.settings, CatchChoice.ASK_ME)
            val c = ShotCtx(ctx)
            val p = SettingsPage(c, NoHost)
            val v = pageScreen(c, p)
            p.openSetting(SettingsCatalog.byId("catchAsk")!!)
            v
        }
    }

    @Test fun yourCallRow() {
        val xp = listOf(CatchSummary.XpLine("m", "Mainy", 120, 7, false))
        val ask = CatchSummary(false, "Birdie", null, null, xp, 10, CatchSummary.NEXT_TEXT, ask = true)
        val plain = CatchSummary(false, "Birdie", null, null, xp, 10, CatchSummary.NEXT_TEXT)
        paparazzi.shoot("r19p-row", listOf(Size.SMALL, Size.FLOATB, Size.PIP_169M)) { ctx ->
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8), Ui.dp(ctx, 8))
                for ((s, next) in listOf(ask to true, ask to false, plain to false)) {
                    addView(CatchTilesView(ctx).also { it.bindRow(s, next = next, rowH = Ui.dp(ctx, 26), radiusDp = 12f) },
                        LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = Ui.dp(ctx, 8) })
                }
            }
        }
    }

    // ---- the flows (real buttons of the real views)

    private fun View.all(): List<View> = listOf(this) + ((this as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { g.getChildAt(it).all() } } ?: emptyList())
    private fun View.byDesc(prefix: String): View = all().first { it.contentDescription?.startsWith(prefix) == true }

    @Test fun theThreeButtonsAnswerWithTheirChoice() {
        paparazzi.shoot("r19p-flow-ask", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            st.mons += mon(16, 18, "old16", 142)
            val m = CatchAskPlan.of(data, st, PendingAsk(PendingAsk.Kind.CATCH, "w", mon(16, 34, "w", 160), Tier.NORMAL))!!
            val got = ArrayList<AskChoice>()
            val v = CatchAskPopup.content(ctx, data, m) { got += it }
            v.byDesc("Catch, keep both").performClick()
            v.byDesc("Catch and replace").performClick()
            v.byDesc("Don't catch").performClick()
            assertEquals(listOf(AskChoice.CATCH, AskChoice.REPLACE, AskChoice.DONT), got)
            View(ctx)
        }
    }

    @Test fun aReplaceThatIsOffIsDead() {
        paparazzi.shoot("r19p-flow-off", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            st.mons += mon(16, 18, "old16", 142).also { it.shiny = true }
            val m = CatchAskPlan.of(data, st, PendingAsk(PendingAsk.Kind.CATCH, "w", mon(16, 34, "w", 160), Tier.NORMAL))!!
            val got = ArrayList<AskChoice>()
            val v = CatchAskPopup.content(ctx, data, m) { got += it }
            val b = v.byDesc("Catch and replace")
            assertTrue(!b.isEnabled && !b.isClickable); b.performClick()
            assertTrue(got.isEmpty())
            View(ctx)
        }
    }

    @Test fun theTickBoxStartsTickedAndTheButtonsReportIt() {
        paparazzi.shoot("r19p-flow-replace", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            val old = mon(16, 18, "old16", 142).also { it.favourite = true }
            st.mons += old; st.mons += mon(16, 34, "new16", 160)
            val m = ReplacePlan.of(data, st, PendingAsk(PendingAsk.Kind.REPLACE, "new16", oldUid = "old16"))!!
            val keeps = ArrayList<Boolean>(); val picks = ArrayList<Boolean>()
            val v = ReplaceWindow.content(ctx, data, m, onKeepChanged = { keeps += it }, onPick = { picks += it })
            assertEquals("the box starts ticked", listOf(true), keeps)
            v.byDesc("Keep its place").performClick()
            assertEquals(listOf(true, false), keeps)
            v.byDesc("Release old one").performClick()
            v.byDesc("Keep both").performClick()
            assertEquals(listOf(true, false), picks)
            View(ctx)
        }
    }

    @Test fun noPopUpWithoutAFullScreen() {
        paparazzi.shoot("r19p-flow-none", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            st.mons += mon(16, 18, "old16", 142)
            st.asks += PendingAsk(PendingAsk.Kind.CATCH, "w", mon(16, 34, "w", 160), Tier.NORMAL)
            // the context of a floating window or a widget (no Activity): the question waits, nothing opens, nothing is answered
            val dlg = AppDialogShots.capture { CatchAskPopup.offer(ctx) }
            assertNull(dlg)
            assertEquals(1, st.asks.size)
            View(ctx)
        }
    }
}
