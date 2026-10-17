package com.husarp.ankigo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.overlay.FloatBattleService
import com.husarp.ankigo.remind.GameFactsWriter
import com.husarp.ankigo.remind.ReminderScheduler
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
    override fun onCreate() {
        super.onCreate()
        Report.init(this)
        GameLog.init(this)
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
            LiveSession.recheck(this) // leaving AnkiGo: PiP / floating battle may keep watching, make sure the last second counts
            backgroundRequested = true
            // A new Activity starts before the old one stops. Defer to settle task/picker transitions.
            main.removeCallbacks(leave)
            main.postDelayed(leave, 200)
        }
    }
    override fun onActivityDestroyed(activity: Activity) { BattleScreen.setVisible(activity, false) }
    override fun onActivityCreated(activity: Activity, state: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
}
