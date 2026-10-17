package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.R
import com.husarp.ankigo.remind.DueMode
import com.husarp.ankigo.remind.GoalKind
import com.husarp.ankigo.remind.ReminderPrefs
import com.husarp.ankigo.remind.ReminderScreen
import com.husarp.ankigo.remind.ReminderStore
import com.husarp.ankigo.remind.ReminderTopic
import com.husarp.ankigo.ui.BottomNav
import com.husarp.ankigo.ui.GamePage
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/*
 * R3-V4: Settings → Reminders (pick 4E). The list (some on, some off; all off; notifications denied), every sheet and the deck
 * picker, PHONE + SPLIT + the floating card (340 x 480), light + dark. Names: remind-<what>-<phone|split|float>-<light|dark>.
 */
class RemindersShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val variants = listOf(Variants.PHONE, Variants.SPLIT, Variant("float", 340, 480))

    /** A page that only hosts the view (the Settings page that will show it comes with R3-V2). */
    private class HostPage(ctx: Context) : GamePage(ctx, NoHost) {
        override val tab: BottomNav.Tab? = null
        override val needsGame = false
        override fun buildBar(): View = View(this)
        override fun buildBody(): View = View(this)
        override fun render() = Unit
    }

    private fun decks() = listOf(
        Anki.Deck(1, "Default", 0, 0, 0, false),
        Anki.Deck(2, "Spanish", 5, 25, 10, false),
        Anki.Deck(3, "Spanish::Verbs", 0, 10, 2, false),
        Anki.Deck(4, "Spanish::Verbs::Irregular", 0, 4, 0, false),
        Anki.Deck(5, "Medicine", 2, 8, 0, false),
        Anki.Deck(6, "Medicine::Anatomy and physiology of the whole human body, part two", 1, 2, 0, false),
        Anki.Deck(7, "Capitals", 0, 0, 5, false),
    )

    private fun screen(ctx: Context, prefs: ReminderPrefs, canPost: Boolean = true): ReminderScreen {
        ReminderStore.save(ReminderStore.prefs(ctx), prefs)
        return ReminderScreen(HostPage(ctx), loadDecks = { it(decks()) }, canPost = { canPost })
    }

    /** What the list looks like on the Settings page: padded, scrolling, on the page colour. */
    private fun list(ctx: Context, s: ReminderScreen): View =
        scrollOnBase(ctx, LinearLayout(ctx).apply {
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
            addView(s.root, LinearLayout.LayoutParams(-1, -2))
        })

    private fun withSheet(ctx: Context, s: ReminderScreen, sheet: View): View = FrameLayout(ctx).apply {
        addView(list(ctx, s), FrameLayout.LayoutParams(-1, -1))
        addView(View(ctx).apply { setBackgroundColor(Color.argb(122, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
        addView(sheet, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private val mixed = ReminderPrefs(
        dueOn = true, dueMin = 20, dueMode = DueMode.APP_PICKS, minGapHours = 3, dailyCap = 2,
        goalOn = false, streakOn = true, dailyGoalOn = true, shopOn = false, quietOn = true,
    )

    @Test fun listMixed() {
        paparazzi.shootPage("remind-list", variants) { ctx -> list(ctx, screen(ctx, mixed)) }
    }

    @Test fun listAllOff() {
        paparazzi.shootPage("remind-list-off", listOf(Variants.PHONE)) { ctx -> list(ctx, screen(ctx, ReminderPrefs())) }
    }

    @Test fun listEverythingOnWithLongSummaries() {
        val all = mixed.copy(
            goalOn = true, goalKind = GoalKind.NEW_CARDS, goalNewCards = 1200, shopOn = true, dueMin = 1500,
            allDecks = false, deckIds = setOf(2, 5), quietFrom = 23 * 60 + 30, quietTo = 6 * 60 + 15,
        )
        paparazzi.shootPage("remind-list-on", listOf(Variants.PHONE, Variant("float", 340, 480))) { ctx -> list(ctx, screen(ctx, all)) }
    }

    @Test fun listNotificationsDenied() {
        paparazzi.shootPage("remind-list-denied", variants) { ctx -> list(ctx, screen(ctx, mixed, canPost = false)) }
    }

    @Test fun sheetCardsDueFixed() {
        paparazzi.shootPage("remind-sheet-due", variants) { ctx ->
            val s = screen(ctx, mixed.copy(dueMode = DueMode.FIXED, dueMin = 35, minGapHours = 3))
            withSheet(ctx, s, s.sheetView(ReminderTopic.DUE))
        }
    }

    @Test fun sheetCardsDueAppPicksAndDecks() {
        paparazzi.shootPage("remind-sheet-due-picks", listOf(Variants.PHONE)) { ctx ->
            val s = screen(ctx, mixed.copy(dueMode = DueMode.APP_PICKS, allDecks = false, deckIds = setOf(2, 5)))
            withSheet(ctx, s, s.sheetView(ReminderTopic.DUE))
        }
    }

    @Test fun sheetCardsDueEveryHours() {
        paparazzi.shootPage("remind-sheet-due-hours", listOf(Variants.PHONE)) { ctx ->
            val s = screen(ctx, mixed.copy(dueMode = DueMode.EVERY_HOURS, dueEveryHours = 4, dueMin = 10, minGapHours = 5, dailyCap = 1))
            withSheet(ctx, s, s.sheetView(ReminderTopic.DUE))
        }
    }

    @Test fun sheetCardsDueOff() {
        paparazzi.shootPage("remind-sheet-due-off", listOf(Variants.PHONE)) { ctx ->
            val s = screen(ctx, ReminderPrefs())
            withSheet(ctx, s, s.sheetView(ReminderTopic.DUE))
        }
    }

    @Test fun sheetOwnGoal() {
        paparazzi.shootPage("remind-sheet-goal", variants) { ctx ->
            val s = screen(ctx, mixed.copy(goalOn = true))
            withSheet(ctx, s, s.sheetView(ReminderTopic.GOAL))
        }
        paparazzi.shootPage("remind-sheet-goal-new", listOf(Variants.PHONE)) { ctx ->
            val s = screen(ctx, mixed.copy(goalOn = true, goalKind = GoalKind.NEW_CARDS, goalNewCards = 35, dueEveryHours = 3))
            withSheet(ctx, s, s.sheetView(ReminderTopic.GOAL))
        }
    }

    @Test fun sheetStreakDailyAndQuiet() {
        paparazzi.shootPage("remind-sheet-streak", variants) { ctx ->
            val s = screen(ctx, mixed)
            withSheet(ctx, s, s.sheetView(ReminderTopic.STREAK))
        }
        paparazzi.shootPage("remind-sheet-daily", listOf(Variants.PHONE)) { ctx ->
            val s = screen(ctx, mixed.copy(dailyGoalOn = false))
            withSheet(ctx, s, s.sheetView(ReminderTopic.DAILY))
        }
        paparazzi.shootPage("remind-sheet-quiet", variants) { ctx ->
            val s = screen(ctx, mixed)
            withSheet(ctx, s, s.sheetView(ReminderTopic.QUIET))
        }
    }

    /** B8: a Settings search result opens the sheet of its reminder (the sheet itself is what the shot shows). */
    @Test fun searchHitOpensItsSheet() {
        paparazzi.shootPage("remind-sheet-hit", listOf(Variants.PHONE)) { ctx ->
            val s = screen(ctx, mixed.copy(dueMode = DueMode.FIXED, minGapHours = 3))
            var opened: ReminderTopic? = null
            assertEquals(ReminderTopic.DUE, s.openSheetFor("minGapHours") { opened = it })
            assertEquals(ReminderTopic.DUE, opened)
            opened = null
            assertEquals(ReminderTopic.QUIET, s.openSheetFor("quietFrom") { opened = it })
            assertEquals(ReminderTopic.QUIET, opened)
            opened = null
            assertNull(s.openSheetFor("shopOn") { opened = it }) // no sheet: the row is the switch
            assertNull(s.openSheetFor("theme") { opened = it }) // not a reminder setting
            assertNull(opened)
            withSheet(ctx, s, s.sheetView(ReminderTopic.forSetting("minGapHours")!!))
        }
    }

    @Test fun deckPicker() {
        paparazzi.shootPage("remind-decks", variants) { ctx ->
            val s = screen(ctx, mixed.copy(allDecks = false, deckIds = setOf(3, 5)))
            withSheet(ctx, s, s.deckPickerView(decks()))
        }
        paparazzi.shootPage("remind-decks-loading", listOf(Variants.PHONE)) { ctx ->
            val s = screen(ctx, mixed)
            withSheet(ctx, s, s.deckPickerView(null, loading = true))
        }
        paparazzi.shootPage("remind-decks-failed", listOf(Variants.PHONE)) { ctx ->
            val s = screen(ctx, mixed)
            withSheet(ctx, s, s.deckPickerView(null))
        }
    }
}
