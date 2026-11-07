package com.husarp.ankigo.ui

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.Report
import com.husarp.ankigo.Widgets
import com.husarp.ankigo.data.DataStore
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.DailyGoal
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.lookNum
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.shownName
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.game.save.SaveStore
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

// TICKET T15 owns this file: draws the two game widgets (mockup frame F) into bitmaps.

/** One team member as the widgets show it. [hpFrac] is 0..1. */
data class WidgetMon(val species: Int, val name: String, val level: Int, val shiny: Boolean, val hpFrac: Float, val uid: String = "",
                     /** First type of the shown picture (the Buddy ring's "Pokémon type" colour); null = unknown. */
                     val type: Type? = null)

/** Everything the widgets show, read from a save (never written back). */
data class WidgetSnapshot(
    val nextSpecies: Int?,
    val nextShiny: Boolean,
    val today: Int,
    val goal: Int,
    val streak: Int,
    val atRisk: Boolean,
    val cash: Int,
    val balls: Int,
    val team: List<WidgetMon>,
    /** The Pokémon some Buddy widget was told to show (by uid); only the uids asked for in [WidgetLogic.summarize]. */
    val picked: Map<String, WidgetMon> = emptyMap(),
    /** The Pokémon the Buddy widget being drawn shows; null = the main. See [forBuddy]. */
    val buddy: WidgetMon? = null,
    /** Cards waiting in AnkiDroid (see [WidgetDue]); null = not read or not readable. Only set by [WidgetRender.snapshot] with `withDue`. */
    val due: Int? = null,
) {
    /** No main yet: the player hasn't picked a starter. */
    val hasStarter get() = team.isNotEmpty()
    val goalDone get() = today >= goal
    val left get() = max(0, goal - today)
    val progress get() = if (goal <= 0) 1f else (today.toFloat() / goal).coerceIn(0f, 1f)
    val main get() = team.firstOrNull()

    /** The Pokémon the Buddy widget draws: the chosen one, or the main. */
    val buddyMon get() = buddy ?: main

    /** This snapshot for one Buddy widget with [choice] (a uid or [BuddyWidgetPrefs.MAIN]); a Pokémon that is gone falls back to the main. */
    fun forBuddy(choice: String) = copy(buddy = if (choice == BuddyWidgetPrefs.MAIN) null else picked[choice])
}

/** Where the wide widget's parts go (dp); see [WidgetLogic.wideGeometry]. */
data class WideGeometry(val tile: Float, val sprite: Float, val chipsRow: Boolean, val chipsInColumn: Boolean)

/** Pure rules and numbers of the widgets (JVM-tested; no android.* here). */
object WidgetLogic {
    const val PAD_X = 14f
    const val PAD_Y = 12f
    const val CHIP_H = 22f
    const val RISK_HOUR = 20
    /** One bitmap may take this many bytes (a wide widget has a light and a dark one; RemoteViews' limit is far higher). */
    const val MAX_BITMAP_BYTES = 1_500_000

    /** Game day "yyyy-MM-dd": a day starts at [startHour], like Game.dayKey. */
    fun dayKey(now: Long, startHour: Int, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(now).atZone(zone).minusHours(startHour.toLong()).toLocalDate().toString()

    fun hpFrac(hp: Int, maxHp: Int?): Float = when {
        hp <= 0 -> 0f
        maxHp == null || maxHp <= 0 -> 1f
        else -> (hp.toFloat() / maxHp).coerceIn(0f, 1f)
    }

    /**
     * Builds the snapshot. [maxHp] gives a mon's max HP (null = unknown, the bar then shows full).
     * Daily numbers count as 0 when the save is from an earlier game day; a streak that already lapsed shows 0.
     */
    fun summarize(s: GameState, now: Long, zone: ZoneId = ZoneId.systemDefault(), extraUids: Set<String> = emptySet(),
                  typeOf: (Mon) -> Type? = { null }, lookNum: (Mon) -> Int = { it.species }, maxHp: (Mon) -> Int? = { null }): WidgetSnapshot {
        val dayKey = dayKey(now, s.settings.dayStartHour, zone)
        val today = if (s.day == dayKey) s.reviewsToday else 0
        val goal = DailyGoal.forDay(s, dayKey)
        val yesterday = runCatching { LocalDate.parse(dayKey).minusDays(1).toString() }.getOrDefault("")
        val last = s.streakThroughDay ?: s.lastGoalDay
        val streak = if (last != null && last != dayKey && last != yesterday) 0 else s.streakDays
        val hour = Instant.ofEpochMilli(now).atZone(zone).hour
        return WidgetSnapshot(
            nextSpecies = s.nextSpecies, nextShiny = s.nextShiny,
            today = today, goal = goal, streak = streak,
            atRisk = hour >= RISK_HOUR && today < goal && streak > 0,
            cash = s.cash, balls = s.bag.filterKeys { it.endsWith("-ball") }.values.sumOf { max(0, it) },
            team = s.teamMons().take(6).map { widgetMon(it, lookNum, maxHp, typeOf) },
            picked = extraUids.mapNotNull { s.mon(it) }.associate { it.uid to widgetMon(it, lookNum, maxHp, typeOf) },
        )
    }

    /**
     * R9-W5-A: who is in the team, in which order, and how each one shows (species, chosen look, shininess, name, level): everything
     * of the team the widgets draw that a Team / Box action, an item, a look or a rename can change. HP and XP are left out on
     * purpose (every answer changes them; those redraws come from the answer events). The Buddy widget follows the main, which is the first.
     * [picked] = the uids the widgets show by pick (a Buddy widget set to a Pokémon, often one in the box): they count the same way,
     * and a pick that is gone (released) is its own marker, so the widget that falls back to the main redraws too.
     */
    fun teamSignature(s: GameState, picked: Set<String> = emptySet()): String {
        fun shows(m: Mon) = "${m.uid}:${m.species}:${m.look}:${m.shiny}:${m.shownName}:${m.level}"
        val team = s.teamMons().take(6).joinToString("|") { shows(it) }
        if (picked.isEmpty()) return team
        return team + "#" + picked.sorted().joinToString("|") { u -> s.mon(u)?.let { shows(it) } ?: "$u:gone" }
    }

    private fun widgetMon(m: Mon, lookNum: (Mon) -> Int, maxHp: (Mon) -> Int?, typeOf: (Mon) -> Type?) =
        WidgetMon(lookNum(m), m.shownName, m.level, m.shiny, hpFrac(m.hp, maxHp(m)), m.uid, typeOf(m))

    /**
     * Wide widget layout for a [w] x [h] dp widget with [teamCount] team sprites: the silhouette tile takes the height
     * left over by the team row (and the chips row when there is room for it: 175 dp and up); at smaller heights the
     * chips move under the goal bar if the tile is tall enough, else they are left out.
     */
    fun wideGeometry(w: Float, h: Float, teamCount: Int): WideGeometry {
        val chipsRow = h >= 175f
        val sprite = if (chipsRow) 40f else 32f
        var bottom = h - PAD_Y
        if (chipsRow) bottom -= CHIP_H + 4f
        if (teamCount > 0) bottom -= teamRegionHeight(sprite)
        val tile = min((bottom - PAD_Y).coerceIn(40f, 96f), w * 0.4f)
        return WideGeometry(tile, sprite, chipsRow, chipsInColumn = !chipsRow && tile >= 76f)
    }

    /** Divider margin 6 + line 1 + padding 8 + the sprites with their HP bars. */
    fun teamRegionHeight(sprite: Float) = 6f + 1f + 8f + (sprite - 6f)

    /** Pixels per dp to draw at: the screen density, lowered so one bitmap stays within [MAX_BITMAP_BYTES]. */
    fun pixelsPerDp(wDp: Float, hDp: Float, density: Float, maxBytes: Int = MAX_BITMAP_BYTES): Float =
        min(density, sqrt(maxBytes / 4f / max(1f, wDp * hDp)))
}

/** Draws the widgets and pushes them to the home screen. */
object WidgetRender {
    enum class Kind(val layout: Int, val imgLight: Int, val imgDark: Int, val root: Int, val defW: Float, val defH: Float) {
        WIDE(R.layout.widget_motivation, R.id.m_img_l, R.id.m_img_d, R.id.m_root, 250f, 130f),
        BUDDY(R.layout.widget_buddy, R.id.b_img_l, R.id.b_img_d, R.id.b_root, 110f, 110f),
        STUDY_NOW(R.layout.widget_study_now, R.id.sn_img_l, R.id.sn_img_d, R.id.sn_root, 70f, 70f),
    }

    /** A slow provider or GIF decode must not keep an Android broadcast open indefinitely.
     * The worker may finish its in-flight operation later; finishing the broadcast does not cancel it. */
    fun async(pr: BroadcastReceiver.PendingResult?, work: () -> Unit) {
        com.husarp.ankigo.remind.TickDeadline.run(
            com.husarp.ankigo.remind.TickDeadline.LIMIT_MS,
            work = { work() },
            onError = { Report.fail("T15", "widget update", it) },
            onEnd = { pr?.finish() },
        )
    }

    private val moneyRequests = java.util.concurrent.ConcurrentHashMap<Int, Any>()
    private val shownMoney = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    /** Draws and pushes every id of one widget kind. Blocking: call from a background thread. */
    fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray, kind: Kind) {
        if (ids.isEmpty()) return
        if (kind == Kind.STUDY_NOW) { updateStudyNow(ctx, mgr, ids); return }
        val request = Any()
        synchronized(moneyRequests) { ids.forEach { moneyRequests[it] = request } }
        val prefs = ctx.getSharedPreferences("spike", 0)
        val choices = if (kind == Kind.BUDDY) ids.associateWith { BuddyWidgetPrefs.choice(prefs, it) } else emptyMap()
        val snap = snapshot(ctx, choices.values.filter { it != BuddyWidgetPrefs.MAIN }.toSet(), withDue = kind == Kind.BUDDY)
        val still = reduceMotion(ctx)
        for (id in ids) {
            val (w, h) = sizeDp(ctx, mgr.getAppWidgetOptions(id), kind)
            val appearance = WidgetLooks.read(prefs, if (kind == Kind.BUDDY) "buddy" else "wide", id)
            val shown = choices[id]?.let { snap?.forBuddy(it) } ?: snap
            val look = if (kind == Kind.BUDDY) BuddyWidgetPrefs.look(prefs, id) else BuddyLook()
            val rv = if (kind == Kind.BUDDY && look.hop && !still && shown?.buddyMon != null) buddyHopViews(ctx, shown, w, h, look, appearance) else {
                val rv = RemoteViews(ctx.packageName, kind.layout)
                rv.setImageViewBitmap(kind.imgLight, render(ctx, kind, shown, w, h, night = false, look = look, appearance = appearance))
                rv.setImageViewBitmap(kind.imgDark, render(ctx, kind, shown, w, h, night = true, look = look, appearance = appearance))
                rv
            }
            if (kind == Kind.BUDDY) {
                rv.setContentDescription(kind.root, buddyDescription(shown))
            }
            rv.setOnClickPendingIntent(kind.root, if (kind == Kind.WIDE) Widgets.openStudyOrBattle(ctx) else Widgets.openApp(ctx))
            val previous = shownMoney[id]
            if (kind == Kind.WIDE && !still && shown != null && previous != null && previous != shown.cash) {
                // RemoteViews are drawn by another process. Send a short, finite sequence of bitmap frames;
                // a newer update cancels this sequence before it can overwrite the new balance.
                val start = android.os.SystemClock.uptimeMillis()
                val duration = MoneyRollMath.duration(previous, shown.cash)
                while (moneyRequests[id] === request) {
                    val progress = ((android.os.SystemClock.uptimeMillis() - start).toFloat() / duration).coerceAtMost(1f)
                    val amount = MoneyRollMath.value(previous, shown.cash, 1f - (1f - progress) * (1f - progress))
                    val frameViews = RemoteViews(ctx.packageName, kind.layout)
                    frameViews.setImageViewBitmap(kind.imgLight, moneyFrame(ctx, shown, amount, w, h, false, look, appearance))
                    frameViews.setImageViewBitmap(kind.imgDark, moneyFrame(ctx, shown, amount, w, h, true, look, appearance))
                    frameViews.setOnClickPendingIntent(kind.root, Widgets.openStudyOrBattle(ctx))
                    synchronized(moneyRequests) {
                        if (moneyRequests[id] === request) { mgr.updateAppWidget(id, frameViews); shownMoney[id] = amount }
                    }
                    if (progress >= 1f) break
                    Thread.sleep(minOf(80L, maxOf(1L, duration - (android.os.SystemClock.uptimeMillis() - start))))
                }
            } else synchronized(moneyRequests) {
                if (moneyRequests[id] === request) {
                    try { mgr.updateAppWidget(id, rv) } catch (e: RuntimeException) {
                        if (kind != Kind.BUDDY) throw e
                        val reduced = if (look.hop && !still && shown != null) runCatching { BuddyFrames.views(ctx, shown, w, h, look, appearance, 2) }.getOrNull() else null
                        val fallback = reduced ?: buddyStillViews(ctx, shown, w, h, look, appearance)
                        fallback.setContentDescription(kind.root, buddyDescription(shown))
                        fallback.setOnClickPendingIntent(kind.root, Widgets.openApp(ctx))
                        try { mgr.updateAppWidget(id, fallback) } catch (_: RuntimeException) {
                            val finalStill = buddyStillViews(ctx, shown, w, h, look, appearance)
                            finalStill.setContentDescription(kind.root, buddyDescription(shown))
                            finalStill.setOnClickPendingIntent(kind.root, Widgets.openApp(ctx)); mgr.updateAppWidget(id, finalStill)
                        }
                    }
                    shown?.let { shownMoney[id] = it.cash }
                }
            }
        }
    }

    internal fun moneyFrame(ctx: Context, snap: WidgetSnapshot, amount: Int, w: Float, h: Float,
                            night: Boolean, look: BuddyLook, appearance: WidgetLook): Bitmap =
        render(ctx, Kind.WIDE, snap.copy(cash = amount), w, h, night, look, appearance = appearance)

    /**
     * R5-07: the 1x1 Study now widget needs the due count and (R6-08) today's goal progress for its colour, both read once
     * for all its ids. No save yet = no progress (the colour is red).
     */
    private fun updateStudyNow(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val prefs = ctx.getSharedPreferences("spike", 0)
        val snap = (snapshot(ctx) ?: WidgetSnapshot(null, false, 0, 0, 0, false, 0, 0, emptyList())).copy(due = WidgetDue.read(ctx))
        for (id in ids) {
            val (w, h) = sizeDp(ctx, mgr.getAppWidgetOptions(id), Kind.STUDY_NOW)
            val bg = StudyNowPrefs.bg(prefs, id)
            val appearance = WidgetLooks.read(prefs, "now", id)
            val rv = RemoteViews(ctx.packageName, Kind.STUDY_NOW.layout)
            rv.setImageViewBitmap(Kind.STUDY_NOW.imgLight, render(ctx, Kind.STUDY_NOW, snap, w, h, night = false, studyBg = bg, appearance = appearance))
            rv.setImageViewBitmap(Kind.STUDY_NOW.imgDark, render(ctx, Kind.STUDY_NOW, snap, w, h, night = true, studyBg = bg, appearance = appearance))
            rv.setContentDescription(Kind.STUDY_NOW.root, StudyNow.description(snap.due, snap.today, snap.goal))
            rv.setOnClickPendingIntent(Kind.STUDY_NOW.root, Widgets.openStudy(ctx))
            mgr.updateAppWidget(id, rv)
        }
    }

    /** Remove-animations on (the app's setting or Android's): the Buddy widget stays a still picture. */
    private fun reduceMotion(ctx: Context) =
        !android.animation.ValueAnimator.areAnimatorsEnabled() || ctx.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)

    private fun buddyDescription(s: WidgetSnapshot?): String {
        val mon=s?.buddyMon ?: return "Buddy"
        return BuddyText.description(mon.name,s.today,s.goal,s.due,s.streak)
    }

    /** Real sprite animation, or a still picture when frames cannot be decoded or served. */
    internal fun buddyHopViews(ctx: Context, snap: WidgetSnapshot, w: Float, h: Float, look: BuddyLook, appearance: WidgetLook = WidgetLook()): RemoteViews {
        return runCatching { BuddyFrames.views(ctx, snap, w, h, look, appearance) }.getOrNull() ?: buddyStillViews(ctx, snap, w, h, look, appearance)
    }

    private fun buddyStillViews(ctx: Context, snap: WidgetSnapshot?, w: Float, h: Float, look: BuddyLook, appearance: WidgetLook): RemoteViews =
        RemoteViews(ctx.packageName, R.layout.widget_buddy).apply {
            setImageViewBitmap(R.id.b_img_l, render(ctx, Kind.BUDDY, snap, w, h, false, look=look, appearance=appearance))
            setImageViewBitmap(R.id.b_img_d, render(ctx, Kind.BUDDY, snap, w, h, true, look=look, appearance=appearance))
        }

    /** The widget's current size in dp from its options (portrait: min width x max height; landscape: max width x min height). */
    fun sizeDp(ctx: Context, o: Bundle?, kind: Kind): Pair<Float, Float> {
        val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val w = o?.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val h = o?.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        return Pair(if (w > 40) w.toFloat() else kind.defW, if (h > 40) h.toFloat() else kind.defH)
    }

    /**
     * The game as the widgets show it: the loaded game if there is one, else a read-only look at the save files
     * (never SaveStore.load, never a write). Null when there is no readable save yet.
     */
    fun snapshot(ctx: Context, extraUids: Set<String> = emptySet(), withDue: Boolean = false): WidgetSnapshot? {
        val now = System.currentTimeMillis()
        var data: GameData? = null
        var dataTried = false
        fun loaded(): GameData? {
            if (!dataTried) {
                dataTried = true
                data = runCatching { GameRepo.data }.getOrNull() ?: runCatching { DataStore.load(ctx) }.getOrNull()
            }
            return data
        }
        val maxHp: (Mon) -> Int? = { m -> loaded()?.let { d -> runCatching { StatCalc.maxHp(d, m) }.getOrNull() } }
        // the picture of an owned Pokémon follows its chosen look (WidgetMon.species is only used to draw it)
        val lookNum: (Mon) -> Int = { m -> runCatching { m.lookNum(loaded()) }.getOrDefault(m.species) }
        // the first type of the picture shown (the Buddy ring's "Pokémon type" colour)
        val typeOf: (Mon) -> Type? = { m -> runCatching { loaded()?.species?.get(lookNum(m))?.types?.firstOrNull() }.getOrNull() }
        // The loaded game is changed on the main thread; if a list moves under us, fall back to the file.
        val live = runCatching { GameRepo.state }.getOrNull()
        val snap = run {
            if (live != null) {
                try {
                    return@run WidgetLogic.summarize(live, now, extraUids = extraUids, lookNum = lookNum, maxHp = maxHp, typeOf = typeOf)
                } catch (_: Exception) { /* read the file instead */ }
            }
            val dir = GameRepo.saveDir(ctx)
            val store = SaveStore(dir)
            val st = store.read(File(dir, "save.json")) ?: store.read(File(dir, "save.bak")) ?: return null
            WidgetLogic.summarize(st, now, extraUids = extraUids, lookNum = lookNum, maxHp = maxHp, typeOf = typeOf)
        }
        return if (withDue) snap.copy(due = WidgetDue.read(ctx)) else snap
    }

    /**
     * One widget bitmap, [wDp] x [hDp] of layout, in the light or dark colours. [snap] null = no save yet.
     * [look] is for the Buddy widget; [maxBytes] caps the bitmap size. The legacy [hop] argument is ignored; motion uses real frames.
     */
    fun render(ctx: Context, kind: Kind, snap: WidgetSnapshot?, wDp: Float, hDp: Float, night: Boolean,
               look: BuddyLook = BuddyLook(), hop: Boolean = false, maxBytes: Int = WidgetLogic.MAX_BITMAP_BYTES,
               studyBg: StudyNowBg = StudyNowBg.SYSTEM, appearance: WidgetLook = WidgetLook(), hideSprite: Boolean = false): Bitmap {
        // the 1x1 widget's White / Black background fixes its colours in both bitmaps
        val dark = appearance.dark(if (kind == Kind.STUDY_NOW) studyBg.dark(night) else night)
        val themed = themed(ctx, appearance.dark(dark))
        val k = WidgetLogic.pixelsPerDp(wDp, hDp, ctx.resources.displayMetrics.density, maxBytes)
        val bmp = Bitmap.createBitmap(max(1, (wDp * k).roundToInt()), max(1, (hDp * k).roundToInt()), Bitmap.Config.ARGB_8888)
        val d = Draw(themed, ctx, bmp, k, appearance, hideSprite)
        d.roundRect(0f, 0f, wDp, hDp, 24f, appearance.background(themed, snap?.buddyMon?.type))
        when (kind) {
            Kind.WIDE -> d.wide(snap, wDp, hDp)
            Kind.BUDDY -> d.buddy(snap, wDp, hDp, look)
            Kind.STUDY_NOW -> d.studyNow(snap, wDp, hDp, studyBg, dark)
        }
        return bmp
    }

    private fun themed(ctx: Context, night: Boolean): Context {
        val c = Configuration(ctx.resources.configuration)
        c.uiMode = (c.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        return ctx.createConfigurationContext(c)
    }

    /** Canvas helpers; all coordinates are dp (the canvas is scaled by [k]); text sizes are dp too, so the font scale doesn't change a widget. */
    private class Draw(val ctx: Context, val spriteCtx: Context, val bmp: Bitmap, val k: Float, val appearance: WidgetLook = WidgetLook(), val hideSprite: Boolean = false) {
        val canvas = Canvas(bmp).apply { scale(k, k) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val pixel = Paint().apply { isFilterBitmap = false; isAntiAlias = false }

        fun col(@ColorRes r: Int) = Ui.c(ctx, r)

        fun paint(size: Float, weight: Int, @ColorRes color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Ui.font(ctx, weight); textSize = size; this.color = col(color)
        }

        /** One line of [text] in a line box [lh] tall starting at [top]. */
        fun line(text: String, x: Float, top: Float, lh: Float, p: TextPaint, align: Paint.Align = Paint.Align.LEFT) {
            p.textAlign = align
            val fm = p.fontMetrics
            canvas.drawText(text, x, top + lh / 2f - (fm.ascent + fm.descent) / 2f, p)
        }

        /** Shrinks [text] from [maxSize] towards [minSize] until it fits [maxW]; at the minimum it is cut with an ellipsis. */
        fun fitted(text: String, maxW: Float, maxSize: Float, minSize: Float, weight: Int, @ColorRes color: Int): Pair<String, TextPaint> {
            val p = paint(maxSize, weight, color)
            while (p.measureText(text) > maxW && p.textSize > minSize) p.textSize = max(minSize, p.textSize - 0.5f)
            return Pair(TextUtils.ellipsize(text, p, maxW, TextUtils.TruncateAt.END).toString(), p)
        }

        fun roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
            fill.shader = null; fill.color = color
            canvas.drawRoundRect(l, t, r, b, rad, rad, fill)
        }

        fun icon(@DrawableRes res: Int, x: Float, y: Float, size: Float, color: Int) {
            val dr = ctx.getDrawable(res)?.mutate() ?: return
            if (res != R.drawable.ic_money) dr.setTint(color) // the gold money stack is never tinted
            // vectors rasterise at their bounds: draw them in real pixels, not through the dp scale
            canvas.save()
            canvas.scale(1f / k, 1f / k)
            dr.setBounds((x * k).roundToInt(), (y * k).roundToInt(), ((x + size) * k).roundToInt(), ((y + size) * k).roundToInt())
            dr.draw(canvas)
            canvas.restore()
        }

        /** The still streak flame (R7-06; widgets cannot animate) in a [size] square, drawn in real pixels; false when it cannot be decoded. */
        fun flame(x: Float, y: Float, size: Float): Boolean {
            val px = (size * k).roundToInt()
            val b = StreakFlame.stillBitmap(ctx, px) ?: return false
            canvas.save()
            canvas.scale(1f / k, 1f / k)
            canvas.drawBitmap(b, x * k, y * k, null)
            canvas.restore()
            return true
        }

        /** Static sprite in a [size] square, pixel-crisp; null bitmap -> a coloured disc with two letters of [name]. */
        fun sprite(num: Int, shiny: Boolean, name: String, x: Float, y: Float, size: Float, fit: Boolean = false) {
            if (hideSprite) return
            val b = Sprites.staticBitmap(spriteCtx, num, shiny)
            if (b == null) {
                val type = com.husarp.ankigo.GameRepo.data?.species?.get(num)?.types?.firstOrNull() ?: com.husarp.ankigo.game.Type.NORMAL
                val placeholder = SpritePlaceholder(Ui.typeColor(type))
                placeholder.setBounds((x*k).toInt(), (y*k).toInt(), ((x+size)*k).toInt(), ((y+size)*k).toInt())
                canvas.save(); canvas.scale(1f/k, 1f/k); placeholder.draw(canvas); canvas.restore()
                return
            }
            val scale = size / maxOf(b.width, b.height)
            val width = if (fit) b.width * scale else size
            val height = if (fit) b.height * scale else size
            val left = x + (size-width)/2
            val top = y + (size-height)/2
            canvas.drawBitmap(b, Rect(0, 0, b.width, b.height), RectF(left, top, left+width, top+height), pixel)
        }

        /** Same sprite as a flat silhouette: ag_text at the mockup's strength (R.integer.ag_sil of 255). */
        fun silhouette(num: Int, shiny: Boolean, x: Float, y: Float, size: Float) {
            val b = Sprites.staticBitmap(spriteCtx, num, shiny)
            if (b == null) { sprite(num, shiny, "?", x, y, size); return }
            val p = Paint().apply {
                isFilterBitmap = false; isAntiAlias = false
                colorFilter = PorterDuffColorFilter(col(R.color.ag_text), PorterDuff.Mode.SRC_IN)
                alpha = ctx.resources.getInteger(R.integer.ag_sil)
            }
            canvas.drawBitmap(b, Rect(0, 0, b.width, b.height), RectF(x, y, x + size, y + size), p)
        }

        fun chip(x: Float, y: Float, @DrawableRes iconRes: Int, @ColorRes tone: Int, text: String, streak: Boolean = false): Float {
            val p = paint(11f, 800, tone)
            val iconSize = if (iconRes == R.drawable.ic_money) 17f else 13f
            val w = 7f + iconSize + 4f + p.measureText(text) + 7f
            roundRect(x, y, x + w, y + WidgetLogic.CHIP_H, 9f, col(R.color.ag_mantle))
            if (!streak || !flame(x + 5.5f, y + (WidgetLogic.CHIP_H - 16f) / 2f, 16f)) icon(iconRes, x + 7f, y + (WidgetLogic.CHIP_H - iconSize) / 2f, iconSize, col(tone))
            line(text, x + 7f + iconSize + 4f, y, WidgetLogic.CHIP_H, p)
            return w
        }

        /** Streak, coins and balls in a row from [x]; stops when [maxX] would be passed. */
        fun chips(x: Float, y: Float, maxX: Float, s: WidgetSnapshot) {
            var cx = x
            val items = listOf(
                Triple(R.drawable.ic_flame, if (s.streak > 0) R.color.ag_red else R.color.ag_sub0, if (s.streak == 1) "1 day" else "${s.streak} days"),
                Triple(R.drawable.ic_money, R.color.ag_yellow, "%,d".format(s.cash)),
                Triple(R.drawable.ic_ball, R.color.ag_sub1, "${s.balls}"),
            )
            for ((ic, tone, text) in items) {
                val w = 7f + (if (ic == R.drawable.ic_money) 17f else 13f) + 4f + paint(11f, 800, tone).measureText(text) + 7f
                if (cx + w > maxX) break
                cx += chip(cx, y, ic, tone, text, streak = ic == R.drawable.ic_flame && s.streak > 0) + 6f
            }
        }

        fun goalBar(x: Float, y: Float, w: Float, progress: Float, solid: Int? = null) {
            roundRect(x, y, x + w, y + 7f, 3.5f, col(R.color.ag_s0))
            val fw = if (progress <= 0f) 0f else max(7f, w * progress)
            if (fw > 0f) {
                val f = BarTones.fill(ctx, BarMeaning.GOAL, progress)
                val to = if (solid==null && appearance.accent == "goal") f.gradientTo else null
                if (solid != null) fill.color=solid
                else if (appearance.accent != "goal") fill.color = col(BuddyRing.pickRes(appearance.accent))
                else if (to != null) fill.shader = LinearGradient(x, 0f, x + fw, 0f, f.color, to, Shader.TileMode.CLAMP) else fill.color = f.color
                canvas.drawRoundRect(x, y, x + fw, y + 7f, 3.5f, 3.5f, fill)
                fill.shader = null
            }
        }

        /** Wrapped text in a box; returns its height. */
        fun block(text: String, x: Float, y: Float, w: Float, p: TextPaint, maxLines: Int, center: Boolean = false): Float {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, max(1, w.toInt()))
                .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL).build()
            canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore()
            return layout.height.toFloat()
        }

        // ---------------- wide widget ----------------

        fun wide(s: WidgetSnapshot?, w: Float, h: Float) {
            val team = s?.team.orEmpty()
            val g = WidgetLogic.wideGeometry(w, h, team.size)
            val px = WidgetLogic.PAD_X
            val py = WidgetLogic.PAD_Y
            val tile = g.tile

            // silhouette tile
            roundRect(px, py, px + tile, py + tile, 18f * tile / 96f, col(R.color.ag_mantle))
            val next = s?.nextSpecies
            if (s != null && next != null) silhouette(next, s.nextShiny, px + 2f, py + 2f, tile - 4f)
            line("?", px + tile - 8f, py + 2f, 30f * tile / 96f + 6f, paint(max(16f, 26f * tile / 96f), 900, R.color.ag_mauve), Paint.Align.RIGHT)

            val tx = px + tile + 12f
            val tw = w - px - tx
            if (s == null || !s.hasStarter) {
                val p = paint(15f, 900, R.color.ag_text)
                val msg = "Open AnkiGo to choose your starter"
                val hgt = StaticLayout.Builder.obtain(msg, 0, msg.length, p, tw.toInt()).setMaxLines(3).build().height
                block(msg, tx, py + (tile - hgt) / 2f, tw, p, 3)
                return
            }

            // title + sub + goal bar (+ chips when the chips row has no room), centred beside the tile
            val colChips = g.chipsInColumn
            val blockH = 17.5f + 15f + 6f + 7f + if (colChips) 6f + WidgetLogic.CHIP_H else 0f
            var y = py + max(0f, (tile - blockH) / 2f)
            if (s.atRisk) {
                val (t, p) = fitted("Streak at risk", tw - 20f, 15f, 11f, 900, R.color.ag_red)
                icon(R.drawable.ic_alert, tx, y + 2f, 14f, col(R.color.ag_red))
                line(t, tx + 19f, y, 17.5f, p)
            } else {
                val (t, p) = fitted("A wild Pokémon is waiting", tw, 15f, 11f, 900, R.color.ag_text)
                line(t, tx, y, 17.5f, p)
            }
            y += 17.5f
            val sub = if (s.goalDone) "Daily goal done!" else if (s.left == 1) "1 answer left today" else "${s.left} answers left today"
            val (st, sp) = fitted(sub, tw, 12f, 10f, 700, R.color.ag_sub0)
            line(st, tx, y, 15f, sp)
            y += 15f + 6f
            goalBar(tx, y, tw, s.progress)
            if (colChips) chips(tx, y + 7f + 6f, w - px, s)

            // bottom: chips row, then the team under a divider
            var bottom = h - py
            if (g.chipsRow) {
                chips(px, bottom - WidgetLogic.CHIP_H, w - px, s)
                bottom -= WidgetLogic.CHIP_H + 4f
            }
            if (team.isNotEmpty()) {
                val innerW = w - 2 * px
                val size = min(g.sprite, innerW / team.size - 2f)
                val blockTop = bottom - (g.sprite - 6f)
                canvas.drawRect(px, blockTop - 8f - 1f, w - px, blockTop - 8f, fill.apply { shader = null; color = col(R.color.ag_s0) })
                val gap = if (team.size >= 6) (innerW - team.size * size) / (team.size - 1) else min(14f, (innerW - team.size * size) / max(1, team.size - 1))
                team.forEachIndexed { i, m ->
                    val x = px + i * (size + max(0f, gap))
                    val barTop = blockTop + (g.sprite - 10f)
                    sprite(m.species, m.shiny, m.name, x, barTop + 4f - size, size)
                    val bx = x + (size - 22f) / 2f
                    roundRect(bx, barTop, bx + 22f, barTop + 4f, 2f, col(R.color.ag_s0))
                    if (m.hpFrac > 0f) roundRect(bx, barTop, bx + max(2f, 22f * m.hpFrac), barTop + 4f, 2f, Ui.hpColor(ctx, m.hpFrac, com.husarp.ankigo.GameRepo.data?.species?.get(m.species)?.types?.firstOrNull()))
                }
            }
        }

        // ---------------- buddy widget ----------------

        /** Picked A (ring), B (bar) and C (open gauge), with separate number regions at each size. */
        fun buddy(s: WidgetSnapshot?, w: Float, h: Float, look: BuddyLook = BuddyLook()) {
            val space=BuddyGeometry.space(w,h)
            val rw=space.width; val rh=space.height
            val wide=rw>=220f; val short=rh<100f
            canvas.save(); canvas.translate(space.offsetX,space.offsetY); canvas.scale(space.scale,space.scale)
            val main = s?.buddyMon
            if (s == null || main == null) {
                block("Open AnkiGo to choose your starter", 8f, rh / 3, rw - 16, paint(12f, 800, R.color.ag_text), 3, true)
                canvas.restore(); return
            }
            val g = BuddyGeometry.of(rw, rh, look.layout).scaled(look.size)
            val cx = g.cx; val cy = g.top + g.size / 2
            val ring = Paint(3).apply { style = Paint.Style.STROKE; strokeWidth = g.size * .065f; strokeCap = Paint.Cap.ROUND }
            val oval = RectF(cx-g.size/2+3, g.top+3, cx+g.size/2-3, g.top+g.size-3)
            val arc = if (look.layout == "C") 270f else 360f
            val start = if (look.layout == "C") 135f else -90f
            if (look.layout != "B") {
                ring.color = col(R.color.ag_s0); canvas.drawArc(oval, start, arc, false, ring)
                val solid = look.ring.solid(main.type?.let { Ui.typeColor(it) }) { col(BuddyRing.pickRes(it)) }
                val tone = BarTones.fill(ctx, BarMeaning.GOAL, s.progress)
                ring.color = solid ?: if (appearance.accent != "goal") col(BuddyRing.pickRes(appearance.accent)) else tone.color
                if (solid==null && appearance.accent=="goal" && tone.gradientTo!=null) {
                    ring.shader=SweepGradient(cx,cy,tone.color,tone.gradientTo).apply {
                        setLocalMatrix(android.graphics.Matrix().apply { setRotate(start,cx,cy) })
                    }
                }
                canvas.drawArc(oval, start, arc * s.progress, false, ring)
            }
            val progressColour=look.ring.solid(main.type?.let { Ui.typeColor(it) }) { col(BuddyRing.pickRes(it)) }
            sprite(main.species, main.shiny, main.name, g.spriteLeft, g.spriteTop, g.spriteSize, fit=true)
            fun goal(x: Float, y: Float, width: Float, size: Float, centered: Boolean = false, unit: Boolean = true) {
                val text = BuddyText.goal(s.today, s.goal)
                val (t, p) = fitted(text, if (unit) width*.69f else width, size, 6f, 900, R.color.ag_text)
                val left = if (centered) x + width/2 - p.measureText(t)/2 else x
                line(t, left, y, size+3, p)
                if (unit) line("today", left+p.measureText(t)+3, y+size*.25f, size, paint(size*.58f, 700, R.color.ag_sub1))
            }
            fun streak(x: Float, y: Float, width: Float, size: Float) {
                if (s.streak <= 0) return
                val (t,p) = fitted("${s.streak}", (width-size-5).coerceAtLeast(8f), size*.85f, 6f, 900, R.color.ag_red)
                if (!flame(x, y, size)) icon(R.drawable.ic_flame, x, y, size, col(R.color.ag_red))
                line(t, x+size+4, y, size, p)
            }
            fun due(x: Float, y: Float, width: Float, size: Float, unit: Boolean = true) {
                if (s.due == null) return
                val (t,p) = fitted(if(unit) "${s.due} left" else "${s.due}", width, size, 6f, 800, R.color.ag_sub0)
                line(t, x, y, size+3, p)
            }
            if (short) {
                val x = if(look.layout=="C")76f else 70f
                goal(x, 5f, if (wide) 126f else 62f, if(look.layout=="C") { if(wide)16f else 12f } else if (wide) 20f else 15f, unit=wide)
                if (look.layout == "B") goalBar(x, 32f, rw-x-12, s.progress, progressColour)
                if (look.layout == "B" && !wide) {
                    // The mockup shares this row: two strictly separate boxes, shrinking both counts as needed.
                    streak(64f,44f,32f,15f)
                    due(102f,44f,30f,11f,unit=false)
                } else {
                    due(x, if (look.layout == "B") 44f else 28f, if (wide) 120f else 62f, 11f)
                    streak(if (wide) rw-75 else x, if (wide) 10f else 44f, 65f, if (wide) 26f else 16f)
                }
            } else if (wide) {
                if (look.layout == "C") {
                    goal(cx-g.size*.275f, 109f, g.size*.55f, 12f, centered=true, unit=false)
                    line("today", cx, 126f, 10f, paint(11f,700,R.color.ag_sub1), Paint.Align.CENTER)
                    streak(12f, 25f, 75f, 25f); due(12f, 85f, 75f, 18f, unit=false)
                    if (s.streak > 0) line("day streak", 12f, 54f, 13f, paint(10f,700,R.color.ag_sub1))
                    if (s.due != null) line("cards left", 12f, 109f, 13f, paint(10f,700,R.color.ag_sub1))
                } else {
                    goal(146f, 20f, 128f, 22f)
                    if (look.layout == "A") roundRect(146f,62f,274f,63f,0f,col(R.color.ag_s0))
                    if (look.layout == "B") goalBar(146f, 54f, 126f, s.progress, progressColour)
                    streak(146f, 79f, 62f, 26f); due(218f, 82f, 58f, 20f, unit=false)
                    if (s.streak > 0) line("day streak", 146f, 110f, 13f, paint(10f,700,R.color.ag_sub1))
                    if (s.due != null) line("cards left", 218f, 110f, 13f, paint(10f,700,R.color.ag_sub1))
                }
            } else {
                goal(if(look.layout=="C")38f else 0f, if (look.layout == "C") 95f else if (look.layout == "B") 82f else 96f, if(look.layout=="C")rw-76 else rw, if(look.layout=="C")12f else 17f, centered=true, unit=look.layout!="C")
                if (look.layout == "C") line("today", rw/2, 111f, 10f, paint(10f,700,R.color.ag_sub1), Paint.Align.CENTER)
                if (look.layout == "B") goalBar(16f, 106f, rw-32, s.progress, progressColour)
                streak(10f, 119f, 57f, 20f); due(80f, 121f, 50f, 13f)
            }
            canvas.restore()
        }

        /**
         * R5-07 (mockup 64I), changed by R6-08: a plain System / White / Black square; the Study icon (a check when nothing is
         * due) and "Study" ("All done") in today's goal colour (red, orange, green), the due number under them in the neutral
         * ink (white on a dark square, dark on a light one), no badge.
         */
        fun studyNow(s: WidgetSnapshot?, w: Float, h: Float, bg: StudyNowBg, dark: Boolean) {
            val due = s?.due
            val state = StudyNow.state(due)
            val sc = (min(w, h) / 70f).coerceIn(0.8f, 1.6f)
            val surface = when (bg) {
                StudyNowBg.SYSTEM -> col(R.color.ag_base)
                StudyNowBg.WHITE -> 0xFFFFFFFF.toInt()
                StudyNowBg.BLACK -> 0xFF000000.toInt()
            }
            // Shared appearance supplies the surface (including transparency).
            val tone = if (appearance.accent != "goal") col(BuddyRing.pickRes(appearance.accent)) else StudyNow.tone(state, s?.progress ?: 0f, GoalPalette(col(R.color.ag_red), col(R.color.ag_peach), col(R.color.ag_green)))
            val neutral = if (dark) 0xFFFFFFFF.toInt() else col(R.color.ag_text)
            val number = StudyNow.badge(due)
            val isz = 24f * sc
            val lh = 13f * sc
            val gap = 2f * sc
            val nh = 15f * sc
            val total = isz + gap + lh + if (number != null) gap + 1f * sc + nh else 0f
            val top = (h - total) / 2f
            icon(if (state == StudyNow.State.DONE) R.drawable.ic_check else R.drawable.ic_study, (w - isz) / 2f, top, isz, tone)
            val (txt, tp) = fitted(StudyNow.label(state), w - 12f * sc, 10.5f * sc, 7f, 900, R.color.ag_base)
            tp.color = tone
            line(txt, w / 2f, top + isz + gap, lh, tp, Paint.Align.CENTER)
            if (number == null) return
            val np = paint(12f * sc, 900, R.color.ag_base).apply { color = neutral }
            line(number, w / 2f, top + isz + gap + lh + gap + 1f * sc, nh, np, Paint.Align.CENTER)
        }
    }
}
