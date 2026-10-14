package com.husarp.ankigo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.overlay.FloatBattleService
import com.husarp.ankigo.ui.BattleScreen

/** Every Activity participates, including setup, diagnostics, updates and picker bridges. */
class AnkiGoApp : Application(), Application.ActivityLifecycleCallbacks {
    private val main = Handler(Looper.getMainLooper())
    private var backgroundRequested = false
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
    }
    override fun onActivityStarted(activity: Activity) {
        backgroundRequested = false
        main.removeCallbacks(leave)
        BattleScreen.setVisible(activity, true)
        FloatBattleService.stop(this)
    }
    override fun onActivityStopped(activity: Activity) {
        BattleScreen.setVisible(activity, false)
        if (!BattleScreen.visible && !activity.isChangingConfigurations) {
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
