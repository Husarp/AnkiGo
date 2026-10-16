package com.husarp.ankigo.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.Report
import com.husarp.ankigo.live.LiveSession
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattleScreen
import com.husarp.ankigo.ui.GameActivity
import com.husarp.ankigo.ui.LeaveMode
import com.husarp.ankigo.ui.LeaveModeMigration

/**
 * Foreground service behind the floating battle window (LeaveMode.FLOAT): keeps live detection running while AnkiGo
 * isn't in front and shows the compact battle in a movable, resizable window over other apps while none of AnkiGo's Activities
 * is visible. Type specialUse (declared in the manifest) because no other type fits.
 */
class FloatBattleService : Service() {
    private var view: FloatBattleView? = null
    private var stopping = false     // stopSelf() asked for; nothing else should start
    private var destroyed = false
    private var wasReady = false
    private var seenHidden = false   // a Pop out waits for AnkiGo to leave the screen once before it may end
    private var lastNote: String? = null
    private val main = Handler(Looper.getMainLooper())
    private val healthCheck = object : Runnable {
        override fun run() {
            if (stopping || destroyed) return
            sync() // permission can be revoked while idle, without a game event
            updateNotification()
            if (!stopping) main.postDelayed(this, 1000)
        }
    }

    private val battleListener: (Boolean) -> Unit = { sync() }
    private val gameListener: () -> Unit = { sync(); updateNotification() }
    private val pauseListener: (Boolean) -> Unit = { view?.setPaused(it); updateNotification() }
    /** Held strongly: SharedPreferences keeps its listeners weakly. */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == PREF_LEAVE_MODE) sync()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Report.init(this)
        ensureChannel()
        goForeground() // first thing: startForegroundService() gives only a few seconds to do this
        if (stopping) return
        val v = FloatBattleView(
            this,
            onClose = { finish() },
            onTogglePause = { LiveSession.setPaused(this, !LiveSession.paused) },
            onOpenGame = { openGame() },
        )
        view = v
        v.setPaused(LiveSession.paused)
        LiveSession.hold(this, this)
        LiveSession.addPauseListener(pauseListener)
        BattleScreen.addListener(battleListener)
        GameRepo.addListener(gameListener)
        prefs().registerOnSharedPreferenceChangeListener(prefsListener)
        GameRepo.load(this) {
            if (destroyed) return@load
            LiveSession.hold(this, this) // starts the detector now that the game is READY (idempotent)
            sync()
            updateNotification()
        }
        sync()
        if (!stopping) main.postDelayed(healthCheck, 1000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground() // every start request has to be answered with startForeground
        if (stopping) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PAUSE -> LiveSession.setPaused(this, !LiveSession.paused)
            ACTION_STOP -> finish()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        main.removeCallbacks(healthCheck)
        stopping = true
        if (instance === this) instance = null
        BattleScreen.removeListener(battleListener)
        GameRepo.removeListener(gameListener)
        LiveSession.removePauseListener(pauseListener)
        prefs().unregisterOnSharedPreferenceChangeListener(prefsListener)
        view?.destroy() // removes the sink and the window
        view = null
        LiveSession.release(this, this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTE_ID)
        GameRepo.saveNow(this)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- window follows the battle screen

    /** Brings window and session in line with the setting and the battle screen. Cheap; called often. */
    private fun sync() {
        val v = view ?: return
        if (stopping) return
        if (!BattleScreen.visible) seenHidden = true
        else if (popOut && seenHidden) popOut = false // the player came back: a Pop out is over
        if (AppPrefs.leaveMode(this) != LeaveMode.FLOAT && !popOut) { finish(); return }
        val ready = GameRepo.status == GameRepo.Status.READY
        if (ready && !wasReady) LiveSession.hold(this, this)
        wasReady = ready
        if (popOut && BattleScreen.visible && !seenHidden) { v.hide(); return } // Pop out just asked: wait until the app is gone
        if (!FloatBattlePolicy.shouldRun(true, Overlay.canDraw(this), BattleScreen.visible, ready)) {
            finish()
        } else if (!v.show()) {
            Report.fail("FLOAT", "window could not be shown (overlay permission?), stopping")
            finish()
        }
    }

    private fun finish() {
        popOut = false
        if (stopping) return
        stopping = true
        view?.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTE_ID)
        stopSelf()
    }

    private fun openGame() {
        try {
            startActivity(openIntent(this))
        } catch (e: Exception) {
            Report.fail("FLOAT", "open AnkiGo", e)
        }
    }

    private fun prefs(): SharedPreferences = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- notification

    private fun ensureChannel() {
        val ch = NotificationChannel(CHANNEL, "Live battle", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while AnkiGo follows your answers in the background."
            setShowBadge(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Foreground with type specialUse (Android 14+ wants the type in the call; 8-13 take it from the manifest). */
    private fun goForeground() {
        try {
            val n = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTE_ID, n)
            }
            lastNote = noteKey()
        } catch (e: Exception) {
            // e.g. started from the background on Android 12+: not allowed to become a foreground service
            Report.fail("FLOAT", "startForeground", e)
            finish()
        }
    }

    /** Re-posts the notification when its text or the pause state changed (answers change it often). */
    private fun updateNotification() {
        if (stopping || view?.isShowing != true) return
        val key = noteKey()
        if (key == lastNote) return
        lastNote = key
        getSystemService(NotificationManager::class.java).notify(NOTE_ID, buildNotification())
    }

    private fun noteKey() = "${LiveSession.paused}|${noteText()}"

    /** "Wild Pikachu Lv 12 · 45/100 today". */
    private fun noteText(): String {
        val st = GameRepo.state
        if (GameRepo.status != GameRepo.Status.READY || st == null) {
            return if (GameRepo.status == GameRepo.Status.LOADING) "Loading…" else "Open AnkiGo to finish setting up"
        }
        val today = "${st.reviewsToday}/${st.settings.dailyAverage} today"
        val wild = st.battle?.wild ?: return today
        return "Wild ${wild.name} Lv ${wild.level} · $today"
    }

    private fun buildNotification(): Notification {
        val paused = LiveSession.paused
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_swords)
            .setContentTitle(if (paused) "AnkiGo is paused" else "AnkiGo is following your answers")
            .setContentText(noteText())
            .setContentIntent(openPending())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                if (paused) action(R.drawable.ic_play, "Resume", servicePending(ACTION_PAUSE, 1))
                else action(R.drawable.ic_pause, "Pause", servicePending(ACTION_PAUSE, 1))
            )
            .addAction(action(R.drawable.ic_open, "Open", openPending()))
            .addAction(action(R.drawable.ic_close, "Stop", servicePending(ACTION_STOP, 2)))
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_DEFERRED)
        return b.build()
    }

    private fun action(icon: Int, title: String, pi: PendingIntent) =
        Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi).build()

    private fun servicePending(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, FloatBattleService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openPending(): PendingIntent =
        PendingIntent.getActivity(this, 0, openIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        private const val CHANNEL = "live"
        private const val NOTE_ID = 26
        private const val ACTION_PAUSE = "com.husarp.ankigo.float.PAUSE"
        private const val ACTION_STOP = "com.husarp.ankigo.float.STOP"
        /** AppPrefs' SharedPreferences file and key: watched here so a setting change away from FLOAT ends the service. */
        private const val PREFS_FILE = "ankigo_ui"
        private const val PREF_LEAVE_MODE = LeaveModeMigration.KEY

        @Volatile private var instance: FloatBattleService? = null

        /** On-demand floating window ("Pop out" with leave mode not FLOAT): keeps the service alive until the player is back in AnkiGo. */
        @Volatile private var popOut = false

        /**
         * Pop out: shows the floating window once, whatever the leave mode, as soon as AnkiGo's screens are gone
         * (the caller moves the task to the back). Needs the overlay permission and a loaded game; otherwise does nothing.
         */
        fun startPopOut(ctx: Context) {
            val app = ctx.applicationContext
            if (!Overlay.canDraw(app) || GameRepo.status != GameRepo.Status.READY) return
            popOut = true
            try {
                app.startForegroundService(Intent(app, FloatBattleService::class.java))
            } catch (e: Exception) {
                popOut = false
                Report.fail("FLOAT", "start service (pop out)", e)
            }
        }

        private fun openIntent(ctx: Context) =
            Intent(ctx, GameActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /**
         * Starts the service if LeaveMode is FLOAT and the overlay permission is granted; otherwise does nothing.
         * Called at the Activity-to-background transition, within Android's recent-visible-Activity exemption.
         */
        fun startIfWanted(ctx: Context) {
            val app = ctx.applicationContext
            if (!FloatBattlePolicy.shouldRun(AppPrefs.leaveMode(app) == LeaveMode.FLOAT,
                    Overlay.canDraw(app), BattleScreen.visible, GameRepo.status == GameRepo.Status.READY)) return
            try {
                app.startForegroundService(Intent(app, FloatBattleService::class.java))
            } catch (e: Exception) {
                Report.fail("FLOAT", "start service", e)
            }
        }

        /**
         * Stops the service and removes its window (setting turned off, or the user closed the floating battle).
         * Does nothing when it isn't running: a start that is still on its way checks the setting itself and ends.
         */
        @Suppress("UNUSED_PARAMETER")
        fun stop(ctx: Context) {
            // Not stopService(): stopping before startForeground() ran can crash the app on Android 8+.
            instance?.finish()
        }

        /** True while the service runs. */
        val running: Boolean get() = instance != null
    }
}
