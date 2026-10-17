package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.AppDialog
import com.husarp.ankigo.ui.AppDialogRules
import com.husarp.ankigo.ui.AppToast
import com.husarp.ankigo.ui.Pickers
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The scene of an [AppDialog] without a window: 48 % black scrim and the card centred, as the window shows it. */
object AppDialogShots {
    fun scene(ctx: Context, d: AppDialog): View {
        val dm = ctx.resources.displayMetrics
        val w = Ui.dp(ctx, AppDialogRules.widthDp((dm.widthPixels / dm.density).toInt()))
        (d.content().parent as? android.view.ViewGroup)?.removeView(d.content()) // a dialog shot in several sizes / themes keeps one card
        return FrameLayout(ctx).apply {
            setBackgroundColor(Color.argb(122, 0, 0, 0))
            addView(d.content(), FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).also {
                it.topMargin = Ui.dp(ctx, 24); it.bottomMargin = Ui.dp(ctx, 24)
            })
        }
    }

    /** The AppDialog that [open] tried to show (null when none). */
    fun capture(open: () -> Unit): AppDialog? {
        var got: AppDialog? = null
        Pickers.interceptApp = { got = it }
        try { open() } finally { Pickers.interceptApp = null }
        return got
    }
}

/* R6-01: the app's own dialog card and message popup, light + dark, PHONE and FLOATBATTLE (320 dp wide). */
class R6AppDialogShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val sizes = listOf(Size.PHONE, Size.FLOATBATTLE)

    private fun shootDialog(name: String, build: (Context) -> AppDialog) =
        paparazzi.shoot(name, sizes) { ctx -> AppDialogShots.scene(ctx, build(ctx)) }

    @Test fun titleMessageTwoButtons() = shootDialog("r6-dlg-confirm") { ctx ->
        AppDialog.Builder(ctx).setTitle("Release Sparkmouse?")
            .setMessage("Sparkmouse (Lv 26) will be gone for good. This can't be undone.")
            .setPositiveButton("Release", null).setNegativeButton("Cancel", null).create()
    }

    @Test fun items() = shootDialog("r6-dlg-items") { ctx ->
        AppDialog.Builder(ctx).setTitle("Pick one")
            .setItems(arrayOf("Ask", "Always open in AnkiDroid", "Answer anyway")) { _, _ -> }
            .setNegativeButton("Cancel", null).create()
    }

    @Test fun threeButtons() = shootDialog("r6-dlg-three") { ctx ->
        AppDialog.Builder(ctx).setTitle("Nickname")
            .setMessage("Give your Pokémon a name, or use the species name.")
            .setNeutralButton("Use species name", null).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
    }

    @Test fun longMessageScrolls() = shootDialog("r6-dlg-long") { ctx ->
        AppDialog.Builder(ctx).setTitle("A very long message")
            .setMessage((1..60).joinToString(" ") { "Line $it of the text that keeps going and going." })
            .setPositiveButton("Got it", null).create()
    }

    @Test fun disabledButton() = shootDialog("r6-dlg-disabled") { ctx ->
        AppDialog.Builder(ctx).setTitle("Name").setMessage("Type at least one letter.")
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create().also { it.button(AppDialog.BUTTON_POSITIVE)!!.isEnabled = false }
    }

    @Test fun messageOnAScreen() {
        paparazzi.shoot("r6-toast", sizes, offsetMillis = 0) { ctx ->
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).apply() // still: shown at once
            FrameLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(pageScreen(ctx, TeamPage(ctx, NoHost)), FrameLayout.LayoutParams(-1, -1))
                AppToast.show(this, "Sparkmouse joined your team")
            }
        }
        paparazzi.shoot("r6-toast-long", sizes, offsetMillis = 0) { ctx ->
            ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).apply()
            FrameLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                addView(pageScreen(ctx, TeamPage(ctx, NoHost)), FrameLayout.LayoutParams(-1, -1))
                AppToast.show(this, "Your team is full, so Sparkmouse went to the box. You can swap it in from the Team page whenever you like.")
            }
        }
    }

    // ---------- behaviour (no window needed) ----------

    @Test fun buttonTapRunsTheListenerWithTheRightButton() {
        paparazzi.shoot("r6-dlg-behaviour", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            val got = ArrayList<Int>()
            val d = AppDialog.Builder(ctx).setTitle("T")
                .setPositiveButton("Yes") { _, w -> got += w }
                .setNegativeButton("No") { _, w -> got += w }
                .setNeutralButton("Maybe") { _, w -> got += w }.create()
            d.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
            d.button(AppDialog.BUTTON_NEGATIVE)!!.performClick()
            d.button(AppDialog.BUTTON_NEUTRAL)!!.performClick()
            assertEquals(listOf(AppDialog.BUTTON_POSITIVE, AppDialog.BUTTON_NEGATIVE, AppDialog.BUTTON_NEUTRAL), got)
            assertFalse(d.isShowing)
            AppDialogShots.scene(ctx, d)
        }
    }

    @Test fun missingButtonsAreNull() {
        paparazzi.shoot("r6-dlg-behaviour2", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            val d = AppDialog.Builder(ctx).setMessage("m").setPositiveButton("OK", null).create()
            assertNotNull(d.button(AppDialog.BUTTON_POSITIVE))
            assertNull(d.button(AppDialog.BUTTON_NEGATIVE))
            assertTrue(d.button(AppDialog.BUTTON_POSITIVE)!!.isEnabled)
            d.button(AppDialog.BUTTON_POSITIVE)!!.isEnabled = false
            assertEquals(0.4f, d.button(AppDialog.BUTTON_POSITIVE)!!.alpha, 0.001f)
            AppDialogShots.scene(ctx, d)
        }
    }

    @Test fun pickersChooseAndConfirmUseTheAppDialog() {
        paparazzi.shoot("r6-dlg-pickers", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            var picked = -1
            val choose = AppDialogShots.capture { Pickers.choose(ctx, "Pick", listOf("a", "b", "c")) { picked = it } }!!
            assertNotNull(choose.button(AppDialog.BUTTON_NEGATIVE))
            var yes = false
            val confirm = AppDialogShots.capture { Pickers.confirm(ctx, "Sure?", "Really?", "Do it") { yes = true } }!!
            confirm.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
            assertTrue(yes)
            assertEquals(-1, picked)
            AppDialogShots.scene(ctx, choose)
        }
    }

    @Test fun uiToastKeepsLastToastWithoutAWindow() {
        paparazzi.shoot("r6-toast-nowin", listOf(Size.PHONE), listOf(Theme.LIGHT)) { ctx ->
            Ui.lastToast = null
            Ui.toast(ctx, "No window here")
            assertEquals("No window here", Ui.lastToast)
            View(ctx)
        }
    }
}
