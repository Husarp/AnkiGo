package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.ui.WidgetLogic
import com.husarp.ankigo.ui.WidgetRender
import com.husarp.ankigo.ui.WidgetSnapshot
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/*
 * Home-screen widgets, built the way the code does it: WidgetRender.render() draws the light and dark bitmaps, they go
 * into a RemoteViews of the widget layout (setImageViewBitmap, like WidgetRender.update) and RemoteViews.apply() inflates
 * it, as the launcher does. Shown on a soft gradient "wallpaper", widget at its size in dp, centred. No main-code changes.
 */
class WidgetShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private val margin = 28 // dp of wallpaper round the widget

    /** A fixed "now" (2026-10-14 12:00, game day = today, streak alive, not "at risk" time). */
    private val now = LocalDateTime.of(2026, 10, 14, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun snap(): WidgetSnapshot {
        val data = GameRepo.data!!
        return WidgetLogic.summarize(GameRepo.state!!, now, ZoneOffset.UTC) { StatCalc.maxHp(data, it) }
    }

    /** Wallpaper-like backdrop with the widget view centred at wDp x hDp. */
    private fun onWallpaper(ctx: Context, theme: Theme, wDp: Int, hDp: Int, widget: View): View {
        val d = ctx.resources.displayMetrics.density
        val colors = if (theme == Theme.LIGHT) intArrayOf(0xFF9EC5E8.toInt(), 0xFFE7B8D6.toInt(), 0xFFF3D9A4.toInt())
        else intArrayOf(0xFF1B2A4A.toInt(), 0xFF3A2754.toInt(), 0xFF152B33.toInt())
        return FrameLayout(ctx).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
            addView(widget, FrameLayout.LayoutParams((wDp * d).toInt(), (hDp * d).toInt(), Gravity.CENTER))
        }
    }

    private fun shoot(name: String, wDp: Int, hDp: Int, build: (Context, Theme) -> RemoteViews, setup: () -> Unit = {}) {
        val sw = wDp + 2 * margin
        val sh = hDp + 2 * margin
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme).copy(
                screenWidth = sw * 2, screenHeight = sh * 2,
                // the device config must be oriented like its shape, or Paparazzi swaps width and height and crops the wide shots
                orientation = if (sw > sh) com.android.resources.ScreenOrientation.LANDSCAPE else com.android.resources.ScreenOrientation.PORTRAIT,
            ))
            setup()
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            val rv = build(ctx, theme)
            val parent = FrameLayout(ctx)
            val view = rv.apply(ctx, parent)
            val frame = onWallpaper(ctx, theme, wDp, hDp, view)
            paparazzi.snapshot(frame, "$name-${wDp}x$hDp-${theme.name.lowercase()}", 1000)
        }
    }

    /** RemoteViews for the wide/buddy widget like WidgetRender.update builds it. */
    private fun game(ctx: Context, kind: WidgetRender.Kind, s: WidgetSnapshot?, w: Int, h: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, kind.layout)
        rv.setImageViewBitmap(kind.imgLight, WidgetRender.render(ctx, kind, s, w.toFloat(), h.toFloat(), night = false))
        rv.setImageViewBitmap(kind.imgDark, WidgetRender.render(ctx, kind, s, w.toFloat(), h.toFloat(), night = true))
        return rv
    }

    private fun WidgetSnapshot.goalDoneState() = copy(today = goal)
    private fun WidgetSnapshot.noStreak() = copy(streak = 0)

    // ---------------- wide ("motivation") widget ----------------

    @Test fun wideNormal() = shoot("widget-wide", 360, 170, { c, _ -> game(c, WidgetRender.Kind.WIDE, snap(), 360, 170) })
    @Test fun wideDefaultSize() = shoot("widget-wide", 250, 130, { c, _ -> game(c, WidgetRender.Kind.WIDE, snap(), 250, 130) })
    @Test fun wideTall() = shoot("widget-wide", 360, 200, { c, _ -> game(c, WidgetRender.Kind.WIDE, snap(), 360, 200) })
    @Test fun wideAtRisk() = shoot("widget-wide-atrisk", 360, 170, { c, _ -> game(c, WidgetRender.Kind.WIDE, snap().copy(atRisk = true), 360, 170) })
    @Test fun wideGoalDone() = shoot("widget-wide-goaldone", 360, 170, { c, _ -> game(c, WidgetRender.Kind.WIDE, snap().goalDoneState(), 360, 170) })
    @Test fun wideNoStreak() = shoot("widget-wide-nostreak", 360, 170, { c, _ -> game(c, WidgetRender.Kind.WIDE, snap().noStreak(), 360, 170) })
    @Test fun wideNoSave() = shoot("widget-wide-nosave", 360, 170, { c, _ -> game(c, WidgetRender.Kind.WIDE, null, 360, 170) })
    @Test fun wideNoStarter() = shoot("widget-wide-nostarter", 360, 170, { c, _ -> game(c, WidgetRender.Kind.WIDE, snap().copy(team = emptyList()), 360, 170) })
    @Test fun wideNoSprites() = shoot("widget-wide-nosprites", 360, 170, { c, _ -> game(c, WidgetRender.Kind.WIDE, snap(), 360, 170) },
        setup = { FakeGame.withSprites = false })

    // ---------------- buddy widget ----------------

    @Test fun buddyNormal() = shoot("widget-buddy", 170, 170, { c, _ -> game(c, WidgetRender.Kind.BUDDY, snap(), 170, 170) })
    @Test fun buddyDefaultSize() = shoot("widget-buddy", 110, 110, { c, _ -> game(c, WidgetRender.Kind.BUDDY, snap(), 110, 110) })
    @Test fun buddyGoalDone() = shoot("widget-buddy-goaldone", 170, 170, { c, _ -> game(c, WidgetRender.Kind.BUDDY, snap().goalDoneState(), 170, 170) })
    @Test fun buddyNoStreak() = shoot("widget-buddy-nostreak", 170, 170, { c, _ -> game(c, WidgetRender.Kind.BUDDY, snap().noStreak(), 170, 170) })
    @Test fun buddyNoSave() = shoot("widget-buddy-nosave", 170, 170, { c, _ -> game(c, WidgetRender.Kind.BUDDY, null, 170, 170) })
    @Test fun buddyNoStarter() = shoot("widget-buddy-nostarter", 170, 170, { c, _ -> game(c, WidgetRender.Kind.BUDDY, snap().copy(team = emptyList()), 170, 170) })
    @Test fun buddyNoSprites() = shoot("widget-buddy-nosprites", 170, 170, { c, _ -> game(c, WidgetRender.Kind.BUDDY, snap(), 170, 170) },
        setup = { FakeGame.withSprites = false })

    // ---------------- study widget (plain RemoteViews layout, texts and buttons set as StudyWidget.show does) ----------------

    private fun study(ctx: Context, deck: String, text: String, showBtn: Boolean, answerBtns: Boolean): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_study)
        rv.setTextViewText(R.id.s_deck, deck.substringAfterLast("::"))
        rv.setTextViewText(R.id.s_text, text)
        fun vis(on: Boolean) = if (on) View.VISIBLE else View.GONE
        rv.setViewVisibility(R.id.s_show, vis(showBtn))
        for (id in listOf(R.id.s_again, R.id.s_hard, R.id.s_good, R.id.s_easy)) rv.setViewVisibility(id, vis(answerBtns))
        return rv
    }

    @Test fun studyQuestion() = shoot("widget-study-question", 250, 180, { c, _ -> study(c, "Geography::Capitals", "Capital of Australia?", true, false) })
    @Test fun studyAnswer() = shoot("widget-study-answer", 250, 180, { c, _ ->
        study(c, "Geography::Capitals", "Capital of Australia?\n―――\nCanberra", false, true)
    })
    @Test fun studyNoCards() = shoot("widget-study-nocards", 250, 180, { c, _ -> study(c, "Geography::Capitals", "No cards due in this deck. 🎉", false, false) })
    @Test fun studyNoPermission() = shoot("widget-study-nopermission", 250, 180, { c, _ ->
        // permission case in the code never sets the deck: the layout's own "AnkiGo study" label stays
        RemoteViews(c.packageName, R.layout.widget_study).also {
            it.setTextViewText(R.id.s_text, "Open AnkiGo and grant the AnkiDroid permission first.")
            for (id in listOf(R.id.s_show, R.id.s_again, R.id.s_hard, R.id.s_good, R.id.s_easy)) it.setViewVisibility(id, View.GONE)
        }
    })
    @Test fun studyMediaCard() = shoot("widget-study-media", 250, 180, { c, _ ->
        study(c, "Geography::Capitals", "This card has media / math / type-in.\nTap 'Open' to study it in the app.", false, false)
    })
    /** The layout as inflated before the first update (its own placeholder texts, "Show answer" visible, answer buttons hidden). */
    @Test fun studyInitial() = shoot("widget-study-initial", 250, 180, { c, _ -> RemoteViews(c.packageName, R.layout.widget_study) })
}
