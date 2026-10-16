package com.husarp.ankigo.shots

import android.content.Context
import android.widget.RemoteViews
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.ui.BuddyWidgetPrefs
import com.husarp.ankigo.ui.BuddyWidgetSetupActivity
import com.husarp.ankigo.ui.WidgetLogic
import com.husarp.ankigo.ui.WidgetRender
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/*
 * R2-12: the Buddy widget showing a chosen non-main Pokémon (a box one and a team one), the main for comparison, a
 * stale choice (falls back to the main), and the Pokémon picker (BuddyWidgetSetupActivity) with and without a game.
 */
class BuddyWidgetShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val frame = WidgetShotsFrame()
    private val now = LocalDateTime.of(2026, 10, 14, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun buddy(ctx: Context, choice: String): RemoteViews {
        val data = GameRepo.data!!
        val extra = if (choice == BuddyWidgetPrefs.MAIN) emptySet() else setOf(choice)
        val snap = WidgetLogic.summarize(GameRepo.state!!, now, ZoneOffset.UTC, extra) { StatCalc.maxHp(data, it) }.forBuddy(choice)
        val rv = RemoteViews(ctx.packageName, WidgetRender.Kind.BUDDY.layout)
        val k = WidgetRender.Kind.BUDDY
        rv.setImageViewBitmap(k.imgLight, WidgetRender.render(ctx, k, snap, 170f, 170f, night = false))
        rv.setImageViewBitmap(k.imgDark, WidgetRender.render(ctx, k, snap, 170f, 170f, night = true))
        return rv
    }

    @Test fun main() = frame.shoot(paparazzi, "r2-buddy-main", 170, 170) { buddy(it, BuddyWidgetPrefs.MAIN) }
    @Test fun teamMember() = frame.shoot(paparazzi, "r2-buddy-team", 170, 170) { buddy(it, "t3") }
    @Test fun boxMember() = frame.shoot(paparazzi, "r2-buddy-box", 170, 170) { buddy(it, "b3") }
    @Test fun boxMemberSmall() = frame.shoot(paparazzi, "r2-buddy-box", 110, 110) { buddy(it, "b3") }
    @Test fun releasedFallsBack() = frame.shoot(paparazzi, "r2-buddy-released", 170, 170) { buddy(it, "gone") }

    // ---- the picker ----

    private fun picker(ctx: Context, current: String): android.view.View {
        val act = attach(BuddyWidgetSetupActivity(), ShotCtx(ctx))
        setField(act, "current", current)
        val root = act.buildContent()
        act.render()
        return root
    }

    @Test fun pickerList() = paparazzi.shootWith("r2-buddysetup-list", listOf(Size.PHONE, Size.SPLIT)) { picker(it, "b3") }
    @Test fun pickerDefault() = paparazzi.shootWith("r2-buddysetup-default", listOf(Size.PHONE)) { picker(it, BuddyWidgetPrefs.MAIN) }
    @Test fun pickerNoGame() = paparazzi.shootWith("r2-buddysetup-nogame", listOf(Size.PHONE), FakeGame.Scenario.NO_DATA) { picker(it, BuddyWidgetPrefs.MAIN) }
    @Test fun pickerNeedStarter() = paparazzi.shootWith("r2-buddysetup-nostarter", listOf(Size.PHONE), FakeGame.Scenario.NEED_STARTER) { picker(it, BuddyWidgetPrefs.MAIN) }
}
