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
        val goal = s.settings.dailyAverage
        val yesterday = runCatching { LocalDate.parse(dayKey).minusDays(1).toString() }.getOrDefault("")
        val last = s.lastGoalDay
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

    /** Runs [work] on a background thread; [pr] (from goAsync) is finished when it ends. */
    fun async(pr: BroadcastReceiver.PendingResult?, work: () -> Unit) {
        Thread {
            try {
                work()
            } catch (e: Throwable) {
                Report.fail("T15", "widget update", e)
            } finally {
                pr?.finish()
            }
        }.start()
    }

    /** Draws and pushes every id of one widget kind. Blocking: call from a background thread. */
    fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray, kind: Kind) {
        if (ids.isEmpty()) return
        if (kind == Kind.STUDY_NOW) { updateStudyNow(ctx, mgr, ids); return }
        val prefs = ctx.getSharedPreferences("spike", 0)
        val choices = if (kind == Kind.BUDDY) ids.associateWith { BuddyWidgetPrefs.choice(prefs, it) } else emptyMap()
        val snap = snapshot(ctx, choices.values.filter { it != BuddyWidgetPrefs.MAIN }.toSet(), withDue = kind == Kind.BUDDY)
        val still = reduceMotion(ctx)
        for (id in ids) {
            val (w, h) = sizeDp(ctx, mgr.getAppWidgetOptions(id), kind)
            val shown = choices[id]?.let { snap?.forBuddy(it) } ?: snap
            val look = if (kind == Kind.BUDDY) BuddyWidgetPrefs.look(prefs, id) else BuddyLook()
            val rv = if (kind == Kind.BUDDY && look.hop && !still && shown?.buddyMon != null) buddyHopViews(ctx, shown, w, h, look) else {
                val rv = RemoteViews(ctx.packageName, kind.layout)
                rv.setImageViewBitmap(kind.imgLight, render(ctx, kind, shown, w, h, night = false, look = look))
                rv.setImageViewBitmap(kind.imgDark, render(ctx, kind, shown, w, h, night = true, look = look))
                rv
            }
            if (kind == Kind.BUDDY) {
                val b = shown?.buddyMon
                rv.setContentDescription(kind.root, if (shown == null || b == null) "Buddy" else BuddyText.description(b.name, shown.today, shown.goal, shown.due, shown.streak))
            }
            rv.setOnClickPendingIntent(kind.root, if (kind == Kind.WIDE) Widgets.openStudyOrBattle(ctx) else Widgets.openApp(ctx))
            mgr.updateAppWidget(id, rv)
        }
    }

    /** R5-07: the 1x1 Study now widget needs only the due count (no save), read once for all its ids. */
    private fun updateStudyNow(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val snap = WidgetSnapshot(null, false, 0, 0, 0, false, 0, 0, emptyList(), due = WidgetDue.read(ctx))
        for (id in ids) {
            val (w, h) = sizeDp(ctx, mgr.getAppWidgetOptions(id), Kind.STUDY_NOW)
            val rv = RemoteViews(ctx.packageName, Kind.STUDY_NOW.layout)
            rv.setImageViewBitmap(Kind.STUDY_NOW.imgLight, render(ctx, Kind.STUDY_NOW, snap, w, h, night = false))
            rv.setImageViewBitmap(Kind.STUDY_NOW.imgDark, render(ctx, Kind.STUDY_NOW, snap, w, h, night = true))
            rv.setContentDescription(Kind.STUDY_NOW.root, StudyNow.description(snap.due))
            rv.setOnClickPendingIntent(Kind.STUDY_NOW.root, Widgets.openStudy(ctx))
            mgr.updateAppWidget(id, rv)
        }
    }

    /** Remove-animations on (the app's setting or Android's): the Buddy widget stays a still picture. */
    private fun reduceMotion(ctx: Context) =
        !android.animation.ValueAnimator.areAnimatorsEnabled() || ctx.getSharedPreferences("study", 0).getBoolean("reduceMotion", false)

    /** Bytes one hop frame may take: four of them (light and dark, two frames) stay within the two bitmaps of a still widget. */
    private const val HOP_FRAME_BYTES = WidgetLogic.MAX_BITMAP_BYTES / 2

    /**
     * The hopping Buddy: a ViewFlipper (layout widget_buddy_anim, flips by itself every ~550 ms and stops when the home
     * screen is not shown) over two pre-drawn frames per colour scheme: the normal picture, and the same with the Pokémon
     * lifted a little. The home screen cannot run a frame loop, so this is as light as an animation can be there.
     */
    internal fun buddyHopViews(ctx: Context, snap: WidgetSnapshot, w: Float, h: Float, look: BuddyLook): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_buddy_anim)
        fun frame(night: Boolean, hop: Boolean) = render(ctx, Kind.BUDDY, snap, w, h, night, look, hop, HOP_FRAME_BYTES)
        rv.setImageViewBitmap(R.id.b_img_l1, frame(night = false, hop = false))
        rv.setImageViewBitmap(R.id.b_img_l2, frame(night = false, hop = true))
        rv.setImageViewBitmap(R.id.b_img_d1, frame(night = true, hop = false))
        rv.setImageViewBitmap(R.id.b_img_d2, frame(night = true, hop = true))
        return rv
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
     * [look] and [hop] (the lifted frame of the hopping Pokémon) are for the Buddy widget; [maxBytes] caps the bitmap size.
     */
    fun render(ctx: Context, kind: Kind, snap: WidgetSnapshot?, wDp: Float, hDp: Float, night: Boolean,
               look: BuddyLook = BuddyLook(), hop: Boolean = false, maxBytes: Int = WidgetLogic.MAX_BITMAP_BYTES): Bitmap {
        val themed = themed(ctx, night)
        val k = WidgetLogic.pixelsPerDp(wDp, hDp, ctx.resources.displayMetrics.density, maxBytes)
        val bmp = Bitmap.createBitmap(max(1, (wDp * k).roundToInt()), max(1, (hDp * k).roundToInt()), Bitmap.Config.ARGB_8888)
        val d = Draw(themed, ctx, bmp, k)
        when (kind) {
            Kind.WIDE -> d.wide(snap, wDp, hDp)
            Kind.BUDDY -> d.buddy(snap, wDp, hDp, look, hop)
            Kind.STUDY_NOW -> d.studyNow(snap?.due, wDp, hDp)
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
    private class Draw(val ctx: Context, val spriteCtx: Context, val bmp: Bitmap, val k: Float) {
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
            dr.setTint(color)
            // vectors rasterise at their bounds: draw them in real pixels, not through the dp scale
            canvas.save()
            canvas.scale(1f / k, 1f / k)
            dr.setBounds((x * k).roundToInt(), (y * k).roundToInt(), ((x + size) * k).roundToInt(), ((y + size) * k).roundToInt())
            dr.draw(canvas)
            canvas.restore()
        }

        /** Static sprite in a [size] square, pixel-crisp; null bitmap -> a coloured disc with two letters of [name]. */
        fun sprite(num: Int, shiny: Boolean, name: String, x: Float, y: Float, size: Float) {
            val b = Sprites.staticBitmap(spriteCtx, num, shiny)
            if (b == null) {
                val type = com.husarp.ankigo.GameRepo.data?.species?.get(num)?.types?.firstOrNull() ?: com.husarp.ankigo.game.Type.NORMAL
                val placeholder = SpritePlaceholder(Ui.typeColor(type))
                placeholder.setBounds((x*k).toInt(), (y*k).toInt(), ((x+size)*k).toInt(), ((y+size)*k).toInt())
                canvas.save(); canvas.scale(1f/k, 1f/k); placeholder.draw(canvas); canvas.restore()
                return
            }
            canvas.drawBitmap(b, Rect(0, 0, b.width, b.height), RectF(x, y, x + size, y + size), pixel)
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

        fun chip(x: Float, y: Float, @DrawableRes iconRes: Int, @ColorRes tone: Int, text: String): Float {
            val p = paint(11f, 800, tone)
            val w = 7f + 13f + 4f + p.measureText(text) + 7f
            roundRect(x, y, x + w, y + WidgetLogic.CHIP_H, 9f, col(R.color.ag_mantle))
            icon(iconRes, x + 7f, y + (WidgetLogic.CHIP_H - 13f) / 2f, 13f, col(tone))
            line(text, x + 7f + 13f + 4f, y, WidgetLogic.CHIP_H, p)
            return w
        }

        /** Streak, coins and balls in a row from [x]; stops when [maxX] would be passed. */
        fun chips(x: Float, y: Float, maxX: Float, s: WidgetSnapshot) {
            var cx = x
            val items = listOf(
                Triple(R.drawable.ic_flame, if (s.streak > 0) R.color.ag_red else R.color.ag_sub0, if (s.streak == 1) "1 day" else "${s.streak} days"),
                Triple(R.drawable.ic_coin, R.color.ag_yellow, "%,d".format(s.cash)),
                Triple(R.drawable.ic_ball, R.color.ag_sub1, "${s.balls}"),
            )
            for ((ic, tone, text) in items) {
                val w = 7f + 13f + 4f + paint(11f, 800, tone).measureText(text) + 7f
                if (cx + w > maxX) break
                cx += chip(cx, y, ic, tone, text) + 6f
            }
        }

        fun goalBar(x: Float, y: Float, w: Float, progress: Float) {
            roundRect(x, y, x + w, y + 7f, 3.5f, col(R.color.ag_s0))
            val fw = if (progress <= 0f) 0f else max(7f, w * progress)
            if (fw > 0f) {
                val f = BarTones.fill(ctx, BarMeaning.GOAL, progress)
                val to = f.gradientTo
                if (to != null) fill.shader = LinearGradient(x, 0f, x + fw, 0f, f.color, to, Shader.TileMode.CLAMP) else fill.color = f.color
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
                    if (m.hpFrac > 0f) roundRect(bx, barTop, bx + max(2f, 22f * m.hpFrac), barTop + 4f, 2f, Ui.hpColor(ctx, m.hpFrac))
                }
            }
        }

        // ---------------- buddy widget ----------------

        /**
         * 64K: the Pokémon in a goal ring (a green check on it when the goal is reached, the streak as a small flame pill),
         * "32 / 50 today" and "32 done · 48 left" under it. [hop] = the lifted frame of the hopping Pokémon.
         */
        fun buddy(s: WidgetSnapshot?, w: Float, h: Float, look: BuddyLook = BuddyLook(), hop: Boolean = false) {
            val padX = 8f
            val padY = 6f
            val main = s?.buddyMon
            val ts = (min(w, h) / 150f).coerceIn(0.85f, 1.3f) // text grows a little with the widget
            val goalH = 18f * ts
            val subH = 14f * ts
            val textH = goalH + 1f + subH
            val gap = 6f
            val ring = min(min(w - 2 * padX, h - 2 * padY - textH - gap), 130f).coerceAtLeast(36f)
            val total = ring + gap + textH
            val cx = w / 2f
            val top = max(padY / 2f, (h - total) / 2f)
            val cy = top + ring / 2f
            val stroke = ring * 0.09f
            val r = ring / 2f - stroke / 2f
            val oval = RectF(cx - r, cy - r, cx + r, cy + r)

            fill.shader = null
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
            ringPaint.color = col(R.color.ag_s0)
            canvas.drawCircle(cx, cy, r, ringPaint)
            if (s == null || main == null) {
                line("?", cx, cy - 16f, 32f, paint(26f, 900, R.color.ag_mauve), Paint.Align.CENTER)
                val msg = if (s == null) "Open AnkiGo to choose your starter" else "Open AnkiGo to pick a main"
                block(msg, padX, top + ring + 4f, w - 2 * padX, paint(12f, 800, R.color.ag_text), 2, center = true)
                return
            }
            if (s.progress > 0f) {
                val solid = look.ring.solid(main.type?.let { Ui.typeColor(it) }) { col(BuddyRing.pickRes(it)) }
                if (solid != null) ringPaint.color = solid else {
                    val f = BarTones.fill(ctx, BarMeaning.GOAL, s.progress)
                    val to = f.gradientTo
                    if (to != null) {
                        val sweep = SweepGradient(cx, cy, f.color, to)
                        sweep.setLocalMatrix(android.graphics.Matrix().apply { setRotate(-90f, cx, cy) })
                        ringPaint.shader = sweep
                    } else ringPaint.color = f.color
                }
                canvas.drawArc(oval, -90f, 360f * s.progress, false, ringPaint)
            }
            val inner = ring * 0.8333f
            val spriteTop = cy - inner / 2f - inner * 0.02f
            canvas.save()
            if (hop) { // 2nd frame: up 7 % of its size, a touch taller, feet stay put
                canvas.translate(0f, -inner * 0.07f)
                canvas.scale(1f, 1.03f, cx, spriteTop + inner)
            }
            sprite(main.species, main.shiny, main.name, cx - inner / 2f, spriteTop, inner)
            canvas.restore()

            // badges sit on the ring at 45 degrees; a thin ring of the card colour separates them from it
            val edge = r * 0.7071f
            val halo = 2.5f
            if (s.goalDone) {
                val d = max(15f, ring * 0.27f)
                val bx = cx + edge
                val by = cy + edge
                fill.shader = null
                fill.color = col(R.color.ag_base); canvas.drawCircle(bx, by, d / 2f + halo, fill)
                fill.color = col(R.color.ag_green); canvas.drawCircle(bx, by, d / 2f, fill)
                icon(R.drawable.ic_check, bx - d * 0.39f, by - d * 0.39f, d * 0.78f, 0xFFFFFFFF.toInt())
            }
            if (s.streak > 0) {
                val ph = max(13f, ring * 0.17f)
                val fp = paint(max(8.5f, ph * 0.66f), 900, R.color.ag_red)
                val txt = "${s.streak}"
                val fl = ph * 0.72f
                val pw = 4f + fl + 2f + fp.measureText(txt) + 5f
                val px = min(cx + edge - pw * 0.3f, w - 2f - pw).coerceAtLeast(2f)
                val py = cy - edge - ph / 2f
                roundRect(px - halo, py - halo, px + pw + halo, py + ph + halo, ph / 2f + halo, col(R.color.ag_base))
                icon(R.drawable.ic_flame, px + 4f, py + (ph - fl) / 2f, fl, col(R.color.ag_red))
                line(txt, px + 4f + fl + 2f, py, ph, fp)
            }

            // "32 / 50 today": the numbers bold and bigger, "today" small
            val maxW = w - 2 * padX
            var y = top + ring + gap
            val numTxt = BuddyText.goal(s.today, s.goal)
            val np = paint(15f * ts, 900, R.color.ag_text)
            val up = paint(11f * ts, 700, R.color.ag_sub1)
            val sp = 3f * ts
            fun lineW() = np.measureText(numTxt) + sp + up.measureText(BuddyText.UNIT)
            while (lineW() > maxW && np.textSize > 8f) { np.textSize -= 0.5f; up.textSize = np.textSize * 11f / 15f }
            val x0 = cx - lineW() / 2f
            val fm = np.fontMetrics
            val base = y + goalH / 2f - (fm.ascent + fm.descent) / 2f
            np.textAlign = Paint.Align.LEFT; up.textAlign = Paint.Align.LEFT
            canvas.drawText(numTxt, x0, base, np)
            canvas.drawText(BuddyText.UNIT, x0 + np.measureText(numTxt) + sp, base, up)
            y += goalH + 1f
            val (cnt, cp) = fitted(BuddyText.counts(s.today, s.due), maxW, 10.5f * ts, 7.5f, 800, R.color.ag_sub0)
            line(cnt, cx, y, subH, cp, Paint.Align.CENTER)
        }

        /** R5-07 (mockup 64I): accent square + Study icon + "Study" + red due badge; green + check + "All done" when nothing is due. */
        fun studyNow(due: Int?, w: Float, h: Float) {
            val state = StudyNow.state(due)
            val sc = (min(w, h) / 70f).coerceIn(0.8f, 1.6f)
            val ink = col(R.color.ag_base) // the mockup's var(--base): white on light, dark on the pastel dark accent
            val white = 0xFFFFFFFF.toInt()
            roundRect(0f, 0f, w, h, 22f * sc, col(if (state == StudyNow.State.DONE) R.color.ag_green else R.color.ag_mauve))
            val isz = 26f * sc
            val lh = 14f * sc
            val gap = 3f * sc
            val top = (h - (isz + gap + lh)) / 2f + 3f * sc
            icon(if (state == StudyNow.State.DONE) R.drawable.ic_check else R.drawable.ic_study, (w - isz) / 2f, top, isz, ink)
            val (txt, tp) = fitted(StudyNow.label(state), w - 12f * sc, 10.5f * sc, 7f, 900, R.color.ag_base)
            line(txt, w / 2f, top + isz + gap, lh, tp, Paint.Align.CENTER)
            val badge = StudyNow.badge(due) ?: return
            val bp = paint(10.5f * sc, 900, R.color.ag_base).apply { color = white }
            val bh = 20f * sc
            val bw = max(bh, bp.measureText(badge) + 10f * sc)
            val right = w - 5f * sc
            val bt = 5f * sc
            val ring = 2f * sc
            roundRect(right - bw - ring, bt - ring, right + ring, bt + bh + ring, bh / 2f + ring, col(R.color.ag_base))
            roundRect(right - bw, bt, right, bt + bh, bh / 2f, col(R.color.ag_red))
            line(badge, right - bw / 2f, bt, bh, bp, Paint.Align.CENTER)
        }
    }
}
