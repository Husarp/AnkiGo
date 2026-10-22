package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.data.DownloadAll
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.ui.AppDialog
import com.husarp.ankigo.ui.DownloadsDone
import com.husarp.ankigo.ui.SettingsJobs
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import org.junit.Rule
import org.junit.Test

/*
 * R15-E (PLAN 167): Settings -> Downloads with the one "Download everything" button: idle, the confirm dialog (with the size, also for
 * Resume), running (progress + Cancel), stopped and partly failed (Resume with the size left), and the green "Everything downloaded".
 * PHONE, light + dark. Names: r15e-<state>-phone-<light|dark>.
 */
class R15EDownloadShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun page(ctx: Context): View {
        val c = ShotCtx(ctx)
        val p = SettingsPage(c, NoHost)
        val v = pageScreen(c, p)
        p.open(SettingsSection.DOWNLOADS)
        return v
    }

    private fun gens(): Set<Int> = GameRepo.state?.settings?.gens?.toSet() ?: Tuning.DEFAULT_GENS

    private val job get() = SettingsJobs.everything

    private fun reset() { job.reset(); SettingsJobs.left = null }

    @Test fun idle() {
        paparazzi.shootPage("r15e-idle", listOf(Variants.PHONE)) { page(it) }
    }

    private fun confirmOver(ctx: Context, scan: DownloadAll.Scan, resume: Boolean): View {
        val d = AppDialog.Builder(ctx).setTitle(DownloadAll.confirmTitle(resume)).setMessage(DownloadAll.confirmMessage(scan))
            .setPositiveButton(if (resume) "Resume" else "Download", null).setNegativeButton("Cancel", null).create()
        return FrameLayout(ctx).apply {
            addView(page(ctx), FrameLayout.LayoutParams(-1, -1))
            addView(AppDialogShots.scene(ctx, d), FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun scan(todoBytes: Long, haveBytes: Long) =
        DownloadAll.Scan(6900, listOf(DownloadAll.Job(DownloadAll.Kind.FRONT, false, 1, "", todoBytes)), haveBytes)

    @Test fun confirm() {
        paparazzi.shootPage("r15e-confirm", listOf(Variants.PHONE)) { confirmOver(it, scan(141_000_000, 30_000_000), resume = false) }
        paparazzi.shootPage("r15e-confirm-fresh", listOf(Variants.PHONE)) { confirmOver(it, scan(168_000_000, 0), resume = false) }
        paparazzi.shootPage("r15e-confirm-resume", listOf(Variants.PHONE)) { confirmOver(it, scan(62_000_000, 106_000_000), resume = true) }
    }

    @Test fun running() {
        job.begin("Starting…"); job.progress(0.42f, DownloadAll.progressLabel(2870, 6840, 45_300_000))
        try { paparazzi.shootPage("r15e-running", listOf(Variants.PHONE)) { page(it) } } finally { reset() }
    }

    @Test fun stopped() {
        job.begin("Starting…"); job.done(DownloadAll.stoppedLabel(2870, 6840))
        try {
            paparazzi.shootPage("r15e-stopped", listOf(Variants.PHONE)) { ctx ->
                SettingsJobs.left = SettingsJobs.Left(gens(), 94_000_000)   // for the generations the fake game has switched on
                page(ctx)
            }
        } finally { reset() }
    }

    @Test fun partlyFailed() {
        job.begin("Starting…"); job.done(DownloadAll.failedLabel(12))
        try {
            paparazzi.shootPage("r15e-failed", listOf(Variants.PHONE)) { ctx ->
                SettingsJobs.left = SettingsJobs.Left(gens(), 560_000)
                page(ctx)
            }
        } finally { reset() }
    }

    @Test fun done() {
        job.begin("Starting…"); job.done()
        try {
            paparazzi.shootPage("r15e-done", listOf(Variants.PHONE)) { ctx ->
                val c = ShotCtx(ctx)
                DownloadsDone.mark(c, DownloadsDone.PICTURES, gens())
                DownloadsDone.mark(c, DownloadsDone.EVERYTHING, gens())
                try { page(ctx) } finally { DownloadsDone.clear(c) }
            }
        } finally { reset() }
    }
}
