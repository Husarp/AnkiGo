package com.husarp.ankigo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.data.Mirror
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.overlay.FloatBattleService
import com.husarp.ankigo.remind.GameFactsWriter
import com.husarp.ankigo.remind.ReminderScheduler
import com.husarp.ankigo.ui.AchievementPopup
import com.husarp.ankigo.ui.AppToast
import com.husarp.ankigo.ui.BattleScreen

/** Every Activity participates, including setup, diagnostics, updates and picker bridges. */
class AnkiGoApp : Application(), Application.ActivityLifecycleCallbacks {
    private val main = Handler(Looper.getMainLooper())
    private var backgroundRequested = false
    /** The last Activity stopped for a rotation/size change: the next start is a recreate, not the player entering. */
    private var recreating = false
    private val leave = Runnable { startWhenReady() }
    private fun startWhenReady() {
        if (!backgroundRequested || BattleScreen.visible || GameRepo.status != GameRepo.Status.READY) return
        backgroundRequested = false // one transition, one attempt; closing the overlay must not restart it
        FloatBattleService.startIfWanted(this)
    }
    /** R13 Part E: the app's own preference files whose changes go to the debug log (kept here: Android holds the listeners weakly). */
    private val prefFiles = listOf("study", "spike", "firstrun", "ankigo_ui", "ankigo_sound")
    private val prefLogger = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        val file = prefFiles.firstOrNull { getSharedPreferences(it, 0) === p } ?: "?"
        // never card text: the study widget keeps its card's question under "w<id>.q" (and the old "w_q")
        val value = if (key != null && (key == "w_q" || key.endsWith(".q"))) "(card text, not logged)" else p.all[key]?.toString()?.take(80)
        Report.log("SETTINGS", if (key == null) "$file: cleared" else "$file.$key -> $value")
    }

    override fun onCreate() {
        super.onCreate()
        Report.init(this)
        Report.log("LIFE", "app process started")
        GameLog.init(this)
        for (f in prefFiles) getSharedPreferences(f, 0).registerOnSharedPreferenceChangeListener(prefLogger)
        Mirror.init(this)
        registerActivityLifecycleCallbacks(this)
        GameRepo.addListener { if (backgroundRequested) main.post(leave) }
        GameRepo.addListener { GameFactsWriter.changed(this) } // the reminders' small snapshot of the game (PLAN 85)
        ReminderScheduler.rearm(this) // the reminder alarm: Android forgets it after a force stop or an update
    }
    override fun onActivityStarted(activity: Activity) {
        backgroundRequested = false
        main.removeCallbacks(leave)
        val entering = !BattleScreen.visible && !recreating
        recreating = false
        if (entering) Report.log("LIFE", "app in front (${activity.javaClass.simpleName})")   // R13 Part E
        BattleScreen.setVisible(activity, true)
        if (entering) LiveSession.recheck(this) // entering AnkiGo: read AnkiDroid again now
        FloatBattleService.stop(this)
    }
    override fun onActivityStopped(activity: Activity) {
        BattleScreen.setVisible(activity, false)
        if (!BattleScreen.visible && activity.isChangingConfigurations) {
            recreating = true
            main.postDelayed({ recreating = false }, 2_000) // a recreate follows at once; never leave the flag stuck
        }
        if (!BattleScreen.visible && !activity.isChangingConfigurations) {
            Report.log("LIFE", "app in background (${activity.javaClass.simpleName})")   // R13 Part E
            LiveSession.recheck(this) // leaving AnkiGo: PiP / floating battle may keep watching, make sure the last second counts
            backgroundRequested = true
            // A new Activity starts before the old one stops. Defer to settle task/picker transitions.
            main.removeCallbacks(leave)
            main.postDelayed(leave, 200)
        }
    }
    override fun onActivityDestroyed(activity: Activity) { BattleScreen.setVisible(activity, false); Report.log("LIFE", "${activity.javaClass.simpleName} closed") }
    override fun onActivityCreated(activity: Activity, state: Bundle?) { Report.log("LIFE", "${activity.javaClass.simpleName} opened" + if (state != null) " (restored)" else "") }
    override fun onActivityResumed(activity: Activity) { Report.log("LIFE", "${activity.javaClass.simpleName} resumed"); AppToast.resumed(activity); AchievementPopup.resumed(activity) } // the "achievement earned" popup shows on the screen in front
    override fun onActivityPaused(activity: Activity) { Report.log("LIFE", "${activity.javaClass.simpleName} paused"); AppToast.paused(activity); AchievementPopup.paused(activity) }
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
}
