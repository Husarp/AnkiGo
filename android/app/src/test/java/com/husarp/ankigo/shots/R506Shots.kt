package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.ui.BuddyLook
import com.husarp.ankigo.ui.BuddyRing
import com.husarp.ankigo.ui.BuddyWidgetPrefs
import com.husarp.ankigo.ui.BuddyWidgetSetupActivity
import com.husarp.ankigo.ui.WidgetLogic
import com.husarp.ankigo.ui.WidgetRender
import com.husarp.ankigo.ui.WidgetSnapshot
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/*
 * R5-06: the Buddy widget v2 (64K) at 110x110, 160x180 and 250x250 dp, light and dark: goal in progress, goal reached,
 * streak 0 / 12, AnkiDroid unreadable, the three ring colours, the hop frame, and the setup screen with "Widget look".
 */
class R506Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val frame = WidgetShotsFrame()
    private val now = LocalDateTime.of(2026, 10, 14, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun snap(today: Int = 32, goal: Int = 50, streak: Int = 12, due: Int? = 48): WidgetSnapshot {
        val data = GameRepo.data!!
        val s = WidgetLogic.summarize(GameRepo.state!!, now, ZoneOffset.UTC, emptySet(), { data.species[it.species]?.types?.firstOrNull() }, { it.species }, { StatCalc.maxHp(data, it) })
        return s.copy(today = today, goal = goal, streak = streak, due = due)
    }

    private fun buddy(ctx: Context, s: WidgetSnapshot?, w: Int, h: Int, look: BuddyLook = BuddyLook()): RemoteViews {
        val k = WidgetRender.Kind.BUDDY
        val rv = RemoteViews(ctx.packageName, k.layout)
        rv.setImageViewBitmap(k.imgLight, WidgetRender.render(ctx, k, s, w.toFloat(), h.toFloat(), night = false, look = look))
        rv.setImageViewBitmap(k.imgDark, WidgetRender.render(ctx, k, s, w.toFloat(), h.toFloat(), night = true, look = look))
        return rv
    }

    private val sizes = listOf(110 to 110, 160 to 180, 250 to 250)
    private fun all(name: String, build: (Context, Int, Int) -> RemoteViews) {
        for ((w, h) in sizes) frame.shoot(paparazzi, name, w, h) { build(it, w, h) }
    }

    @Test fun progress() = all("r506-progress") { c, w, h -> buddy(c, snap(), w, h) }
    @Test fun goalReached() = all("r506-reached") { c, w, h -> buddy(c, snap(today = 50), w, h) }
    @Test fun goalExceeded() = all("r506-exceeded") { c, w, h -> buddy(c, snap(today = 74, streak = 123), w, h) }
    @Test fun noStreak() = all("r506-nostreak") { c, w, h -> buddy(c, snap(streak = 0), w, h) }
    @Test fun dueUnknown() = all("r506-dueunknown") { c, w, h -> buddy(c, snap(due = null), w, h) }
    @Test fun nothingLeft() = all("r506-noneleft") { c, w, h -> buddy(c, snap(today = 50, due = 0), w, h) }
    @Test fun ringType() = all("r506-ringtype") { c, w, h -> buddy(c, snap(), w, h, BuddyLook(BuddyRing(BuddyRing.Mode.TYPE))) }
    @Test fun ringPick() = all("r506-ringpick") { c, w, h -> buddy(c, snap(), w, h, BuddyLook(BuddyRing(BuddyRing.Mode.PICK, "pink"))) }
    @Test fun noSave() = frame.shoot(paparazzi, "r506-nosave", 110, 110) { buddy(it, null, 110, 110) }
    @Test fun noSprite() {
        FakeGame.withSprites = false
        frame.shoot(paparazzi, "r506-nosprite", 160, 180) { buddy(it, snap(), 160, 180) }
    }

    /** The two hop frames side by side (what the ViewFlipper alternates), drawn as the update does. */
    @Test fun hopFrames() {
        for (night in listOf(false, true)) paparazzi.shootWith("r506-hop-${if (night) "dark" else "light"}", listOf(Size.PHONE)) { c ->
            val k = WidgetRender.Kind.BUDDY
            val s = snap()
            fun img(hop: Boolean) = ImageView(c).apply {
                setImageBitmap(WidgetRender.render(c, k, s, 160f, 180f, night, BuddyLook(hop = true), hop, 750_000))
                setBackgroundColor(if (night) 0xFF1E1E2E.toInt() else 0xFFEFF1F5.toInt())
            }
            val d = c.resources.displayMetrics.density
            FrameLayout(c).apply {
                setBackgroundColor(0xFF808080.toInt())
                addView(img(false), FrameLayout.LayoutParams((160 * d).toInt(), (180 * d).toInt()).also { it.leftMargin = 10 })
                addView(img(true), FrameLayout.LayoutParams((160 * d).toInt(), (180 * d).toInt()).also { it.leftMargin = (180 * d).toInt() })
            }
        }
    }

    /** The real hop layout (ViewFlipper in a RemoteViews, as the launcher inflates it): shows its first frame. */
    @Test fun hopLayoutInflates() = frame.shoot(paparazzi, "r506-hoplayout", 160, 180) { c ->
        WidgetRender.buddyHopViews(c, snap(), 160f, 180f, BuddyLook(hop = true))
    }

    private fun picker(ctx: Context, look: BuddyLook): View {
        val prefs = ctx.getSharedPreferences("spike", 0)
        BuddyWidgetPrefs.setRing(prefs, 1, look.ring)
        BuddyWidgetPrefs.setAnim(prefs, 1, look.hop)
        val act = attach(BuddyWidgetSetupActivity(), ShotCtx(ctx))
        setField(act, "widgetId", 1)
        val root = act.buildContent()
        act.render()
        return root
    }

    @Test fun setupApp() = paparazzi.shootWith("r506-setup-app", listOf(Size.PHONE)) { picker(it, BuddyLook()) }
    @Test fun setupType() = paparazzi.shootWith("r506-setup-type", listOf(Size.PHONE)) { picker(it, BuddyLook(BuddyRing(BuddyRing.Mode.TYPE), hop = true)) }
    @Test fun setupPick() = paparazzi.shootWith("r506-setup-pick", listOf(Size.PHONE, Size.SPLIT)) { picker(it, BuddyLook(BuddyRing(BuddyRing.Mode.PICK, "pink"), hop = true)) }
}
