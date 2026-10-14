package com.husarp.ankigo.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

/**
 * The in-app updater (APP-STANDARDS sections 2 and 3), ported from Lockdown Mobile. Asks GitHub for the latest
 * release at launch and on every return to the app (at most every 5 minutes), finds the APK by its ".apk" ending,
 * downloads it over the app's own connection into the cache with the progress shown in the app, and hands it to
 * Android's package installer. The APK is deleted once the new version runs.
 *
 * Only an APK signed with the same release key (~/.keystores/ankigo.jks) installs over this app.
 */
object Updates {
    private const val API = "https://api.github.com/repos/Husarp/AnkiGo/releases/latest"
    const val RELEASES_PAGE = "https://github.com/Husarp/AnkiGo/releases"
    private const val TIMEOUT_MS = 10_000
    private const val PREFS = "updates"
    private const val KEY_CHECK = "checkUpdates"
    private const val KEY_HIDDEN_DAY = "bannerHiddenDay"

    /** The newest Android version on GitHub. [page] is its release page. */
    data class Latest(val version: String, val apkName: String, val apkUrl: String, val apkSize: Long, val page: String)

    sealed interface Phase {
        data object Idle : Phase
        data class Downloading(val percent: Int, val done: Long = 0, val total: Long = -1, val elapsedMs: Long = 1) : Phase
        data object NeedsPermission : Phase      // Android's "install unknown apps" screen is open
        data object Ready : Phase                // downloaded while the app was in the background; installs on return
        data object Installing : Phase           // handed to Android's installer
        data class Failed(val why: String) : Phase
    }

    data class State(
        val latest: Latest? = null,              // newest version found on GitHub (may equal the current one)
        val pageUrl: String = RELEASES_PAGE,     // the page of the release carrying the newest APK, for GITHUB
        val checking: Boolean = false,
        val message: String? = null,             // answer to a manual CHECK NOW
        val phase: Phase = Phase.Idle,
    )

    @Volatile var state = State()
        private set
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    @Volatile private var lastCheckMs = 0L
    @Volatile private var pendingApk: File? = null
    /** The app's screen while it is in front: Android's screens are opened from it (same task, so Back returns
     *  here), and never while the app is in the background (Android 10+ silently blocks that). */
    @Volatile private var front: WeakReference<Activity>? = null

    /** Called on the main thread after every change. */
    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }

    private fun change(f: (State) -> State) {
        synchronized(this) { state = f(state) }
        main.post { listeners.forEach { it() } }
    }

    fun current(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" }.getOrDefault("?")

    fun checkEnabled(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, 0).getBoolean(KEY_CHECK, true)

    fun setCheckEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_CHECK, on).apply()
        change { it }
        if (on) autoCheck(ctx)
    }

    /** The banner shows for a newer version unless ✕ was tapped today (the day is kept in the prefs, so a restart keeps it hidden). */
    fun bannerVisible(ctx: Context, s: State, current: String): Boolean =
        UpdateLogic.bannerVisible(s.latest?.version, current, hiddenDay(ctx), UpdateLogic.dayOf(System.currentTimeMillis()))

    private fun hiddenDay(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, 0).getLong(KEY_HIDDEN_DAY, UpdateLogic.NO_DAY)

    fun available(s: State, current: String): Boolean = s.latest != null && UpdateLogic.isNewer(s.latest.version, current)

    /** A fresh start of the app (not a return, not a rotation): old APKs go. (The banner's day rule is not reset.) */
    fun onAppStart(ctx: Context) {
        marker(ctx).delete()   // a fresh start is not a return from the "install unknown apps" screen
        // an old failure, or an installer that was left without installing, doesn't carry over to a fresh start
        change { if (it.phase is Phase.Failed || it.phase == Phase.Installing) it.copy(phase = Phase.Idle) else it }
        if (state.phase is Phase.Downloading) return   // the process outlived the screen mid-download
        val cur = current(ctx)
        val app = ctx.applicationContext
        io.execute {
            dir(app).listFiles()?.forEach { f ->
                val v = UpdateLogic.apkVersion(f.name)
                if (v == null || !UpdateLogic.isNewer(v, cur)) f.delete()   // also drops unfinished .part files
            }
        }
    }

    /** Every time one of the app's screens comes to the front: carry on an install waiting on the permission, then auto-check. */
    fun onResume(activity: Activity) {
        front = WeakReference(activity)
        val app = activity.applicationContext
        val phase = state.phase
        // Some phones restart the app when "install unknown apps" is switched on; the marker file survives that.
        val waiting = phase == Phase.NeedsPermission || (phase == Phase.Idle && marker(app).exists())
        when {
            waiting -> {
                val apk = pendingApk ?: runCatching { File(dir(app), marker(app).readText().trim()) }.getOrNull()
                marker(app).delete()
                if (canInstall(app) && apk != null && apk.isFile) install(app, apk)
                else fail("AnkiGo isn't allowed to install apps yet. Allow \"Install unknown apps\" for AnkiGo, then TRY AGAIN.")
            }
            phase == Phase.Ready -> pendingApk?.takeIf { it.isFile }?.let { install(app, it) } ?: fail("The downloaded update is gone.")
            phase == Phase.Installing -> fail("The update wasn't installed. If Android said it couldn't install it, the update " +
                "was signed with a different key than this app - get it from GitHub.")
        }
        autoCheck(app)
    }

    fun onPause(activity: Activity) { if (front?.get() === activity) front = null }

    /** ✕ on the banner: hidden until tomorrow (local date). `change {}` redraws every banner. */
    fun dismissBanner(ctx: Context) {
        ctx.getSharedPreferences(PREFS, 0).edit().putLong(KEY_HIDDEN_DAY, UpdateLogic.dayOf(System.currentTimeMillis())).apply()
        change { it }
    }

    /** Automatic check: only when switched on, at most every 5 minutes, silent when it fails. */
    fun autoCheck(ctx: Context) {
        if (!checkEnabled(ctx) || state.checking) return
        if (!UpdateLogic.mayCheck(System.currentTimeMillis(), lastCheckMs)) return
        check(ctx.applicationContext, manual = false)
    }

    /** CHECK NOW: gives up after ~10 seconds and says in words what is likely wrong. */
    fun checkNow(ctx: Context) {
        if (state.checking) return
        check(ctx.applicationContext, manual = true)
    }

    private fun check(app: Context, manual: Boolean) {
        lastCheckMs = System.currentTimeMillis()
        change { it.copy(checking = true, message = if (manual) "Checking…" else it.message) }
        val cur = current(app)
        io.execute {
            // A separate task, so the 10-second limit returns even if the socket hangs in DNS.
            val job = io.submit<Pair<Latest?, String>> { fetchLatest() }
            val res = try {
                Result.success(job.get(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))
            } catch (e: TimeoutException) {
                job.cancel(true); Result.failure(SocketTimeoutException())
            } catch (e: ExecutionException) {
                Result.failure(e.cause ?: e)
            } catch (e: Exception) {
                Result.failure(e)
            }
            res.onSuccess { (latest, page) ->
                change {
                    it.copy(checking = false, latest = latest, pageUrl = page,
                        message = if (!manual) it.message
                        else if (latest != null && UpdateLogic.isNewer(latest.version, cur)) "AnkiGo ${latest.version} is available."
                        else "You're up to date ($cur).")
                }
            }.onFailure { e ->
                change { it.copy(checking = false, message = if (manual) describe(e, download = false) else it.message) }
            }
        }
    }

    private class HttpStatus(val code: Int) : IOException("HTTP $code")

    /** The APK in the latest release (null when it has none) and its release page. Blocking; off the main thread. */
    private fun fetchLatest(): Pair<Latest?, String> {
        val c = open(API).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            if (c.responseCode != 200) throw HttpStatus(c.responseCode)
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val arr = j.optJSONArray("assets")
            val assets = (0 until (arr?.length() ?: 0)).map { k ->
                val a = arr!!.getJSONObject(k)
                UpdateLogic.Asset(a.getString("name"), a.getString("browser_download_url"), a.optLong("size", -1))
            }
            val release = UpdateLogic.Release(j.optString("html_url", RELEASES_PAGE), assets, j.optBoolean("draft"), j.optBoolean("prerelease"))
            val (rel, a, v) = UpdateLogic.newestApk(listOf(release)) ?: return null to RELEASES_PAGE
            return Latest(v, a.name, a.url, a.size, rel.page) to rel.page
        } finally { c.disconnect() }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "AnkiGo")
    }

    /** UPDATE / GET UPDATE / TRY AGAIN: download (unless already here), then install. Main thread. */
    fun update(ctx: Context) {
        val app = ctx.applicationContext
        if (state.phase is Phase.Downloading) return
        val latest = state.latest ?: run {
            // e.g. TRY AGAIN after Android restarted the app: use the file already here, else look again
            pendingApk?.takeIf { it.isFile }?.let { install(app, it) } ?: checkNow(app)
            return
        }
        val file = File(dir(app), latest.apkName.substringAfterLast('/'))
        if (file.exists() && (latest.apkSize <= 0 || file.length() == latest.apkSize)) { install(app, file); return }
        change { it.copy(phase = Phase.Downloading(0)) }
        io.execute {
            runCatching { download(latest, file) }
                .onSuccess { main.post { install(app, file) } }
                .onFailure { fail(describe(it, download = true)) }
        }
    }

    private fun download(latest: Latest, file: File) {
        val part = File(file.parentFile, file.name + ".part")
        val c = open(latest.apkUrl)
        try {
            if (c.responseCode != 200) throw HttpStatus(c.responseCode)
            val total = c.contentLengthLong.takeIf { it > 0 } ?: latest.apkSize
            val started = android.os.SystemClock.elapsedRealtime()
            var done = 0L; var shown = -1; var lastShown = 0L
            c.inputStream.use { inp ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = inp.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                        if (pct != shown || android.os.SystemClock.elapsedRealtime() - lastShown >= 250) { shown = pct; lastShown = android.os.SystemClock.elapsedRealtime(); change { it.copy(phase = Phase.Downloading(pct, done, total, android.os.SystemClock.elapsedRealtime() - started)) } }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("incomplete")
            if (!part.renameTo(file)) throw IOException("rename")
        } catch (e: Throwable) {
            part.delete(); throw e
        } finally { c.disconnect() }
    }

    private fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /**
     * Hands the APK to Android's installer; the first time, opens "install unknown apps" for AnkiGo instead.
     * Main thread only. With the app in the background it waits (Phase.Ready) and goes on in [onResume].
     */
    private fun install(app: Context, apk: File) {
        pendingApk = apk
        val act = front?.get()
        if (act == null) { change { it.copy(phase = Phase.Ready) }; return }
        if (!canInstall(app)) {
            change { it.copy(phase = Phase.NeedsPermission) }
            runCatching { marker(app).writeText(apk.name) }
            runCatching {
                act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")))
            }.onFailure {
                marker(app).delete()
                fail("Couldn't open Android's \"Install unknown apps\" setting. Allow it for AnkiGo in Settings, then TRY AGAIN.")
            }
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", apk)
            act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            change { it.copy(phase = Phase.Installing) }
        }.onFailure { fail("Couldn't open Android's installer.") }
    }

    private fun fail(why: String) = change { it.copy(phase = Phase.Failed(why)) }

    private fun dir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    /** Names the APK waiting on "install unknown apps" (no version in its name, so the start-up cleanup drops it). */
    private fun marker(ctx: Context) = File(dir(ctx), "pending-install")

    /** What went wrong, in words (never "Failed to fetch"). */
    private fun describe(e: Throwable, download: Boolean): String = when {
        e is UnknownHostException -> "Can't reach GitHub. Check that the phone is online - or a firewall (like AFWall) may be blocking AnkiGo."
        e is SocketTimeoutException -> "GitHub didn't answer within 10 seconds. The connection may be slow or blocked."
        e is SSLException -> "Couldn't open a secure connection to GitHub. The network may be intercepting it, or the phone's date is wrong."
        e is HttpStatus && (e.code == 403 || e.code == 429) -> "GitHub's limit of checks per hour was reached. Try again in a while."
        e is HttpStatus && e.code == 404 -> if (download) "The update file is no longer on GitHub." else "No release found on GitHub."
        download && e.message == "incomplete" -> "The download stopped before it finished."
        download && (e.message == "rename" || e.message?.contains("ENOSPC") == true) -> "Couldn't save the update - is the phone's storage full?"
        download -> "The download failed - the connection to GitHub dropped."
        else -> "Couldn't check for updates - the connection to GitHub failed."
    }
}
