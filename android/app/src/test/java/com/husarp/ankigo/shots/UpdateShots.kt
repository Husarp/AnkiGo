package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.update.UpdateOverlay
import com.husarp.ankigo.update.updatesScreen
import com.husarp.ankigo.update.Updates
import org.junit.Rule
import org.junit.Test

/** Updates screen (app bar + UpdateSection cards) and the floating UpdateBanner in every state. Updates' state is set by reflection. */
class UpdateShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val latest = Updates.Latest("0.4.3", "AnkiGo-0.4.3.apk", "https://example.invalid/AnkiGo-0.4.3.apk", 2_202_009L, "https://example.invalid/release")

    private fun setState(s: Updates.State) {
        val f = Updates::class.java.getDeclaredField("state")
        f.isAccessible = true
        f.set(Updates, s)
    }

    private fun section(ctx: Context): View {
        val ss = ShotCtx(ctx)
        // the real screen: app bar + scrolling cards on the window background
        return updatesScreen(ss)
    }

    private fun banner(ctx: Context): View {
        val ss = ShotCtx(ctx)
        val content = LinearLayout(ss).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.c(ss, R.color.ag_base))
        }
        return UpdateOverlay(ss, content).also { it.banner.animated = false }
    }

    private fun sectionShot(name: String, s: Updates.State) {
        setState(s)
        paparazzi.shoot(name, sizes = listOf(Size.PHONE)) { section(it) }
    }

    private fun bannerShot(name: String, s: Updates.State) {
        setState(s)
        paparazzi.shoot(name, sizes = listOf(Size.PHONE)) { banner(it) }
    }

    @Test fun updatesScreen() {
        val dl = Updates.Phase.Downloading(percent = 66, done = 1_468_006, total = 2_202_009, elapsedMs = 1700)
        sectionShot("updates-uptodate", Updates.State(latest = latest.copy(version = "0.4.2"), message = "AnkiGo is up to date."))
        sectionShot("updates-available", Updates.State(latest = latest))
        sectionShot("updates-downloading", Updates.State(latest = latest, phase = dl))
        sectionShot("updates-needpermission", Updates.State(latest = latest, phase = Updates.Phase.NeedsPermission))
        sectionShot("updates-failed", Updates.State(latest = latest, phase = Updates.Phase.Failed("No internet connection.")))
    }

    @Test fun updateBanner() {
        val dl = Updates.Phase.Downloading(percent = 66, done = 1_468_006, total = 2_202_009, elapsedMs = 1700)
        bannerShot("updatebanner-available", Updates.State(latest = latest))
        bannerShot("updatebanner-downloading", Updates.State(latest = latest, phase = dl))
        bannerShot("updatebanner-failed", Updates.State(latest = latest, phase = Updates.Phase.Failed("No internet connection.")))
    }
}
