package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.help.GuideTypeChart
import com.husarp.ankigo.ui.FirstRunActivity
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class R22MHelpShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @Test fun setupPages() {
        for (step in 6..8) paparazzi.shootPage("r22m-setup-$step", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
            val activity = attach(FirstRunActivity(), ctx)
            val view = activity.buildContent()
            setField(activity, "step", step)
            call(activity, "render")
            view
        }
    }
    private fun views(v: android.view.View): List<android.view.View> = listOf(v) +
        if (v is android.view.ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun setupNavigationAndSkipKeepExistingPreferences() = paparazzi.shootPage("r22m-setup-skip", listOf(Variants.PHONE)) { ctx ->
        val activity = attach(FirstRunActivity(), ctx).apply { intent = android.content.Intent() }
        val study = activity.getSharedPreferences("study", 0)
        study.edit().putBoolean("swipe", true).apply()
        val root = activity.buildContent()
        setField(activity, "step", 6); call(activity, "render")
        fun click(label: String) {
            views(root).filterIsInstance<android.widget.TextView>().first { it.text.toString() == label }.performClick()
        }
        click("Next")
        assertTrue(views(root).filterIsInstance<android.widget.TextView>().any { it.text.toString() == "Game" })
        click("Next")
        assertTrue(views(root).filterIsInstance<android.widget.TextView>().any { it.text.toString() == "Look" })
        click("Back"); click("Skip setup")
        assertTrue(activity.getSharedPreferences("firstrun", 0).getBoolean("optionsDone", false))
        assertTrue(study.getBoolean("swipe", false))
        assertTrue(views(root).filterIsInstance<android.widget.TextView>().any { it.text.toString() == "Use it in split screen" })
        study.edit().remove("swipe").apply()
        root
    }

    @Test fun lessonButtonsUpdateTheCurrentChapter() = paparazzi.shootPage("r22m-lesson-navigation", listOf(Variants.PHONE)) { ctx ->
        val page = com.husarp.ankigo.help.HelpScreen(ctx, NoHost, com.husarp.ankigo.help.HelpRoute.Guide(com.husarp.ankigo.help.GameGuide.BATTLES), false)
        val root = pageScreen(ctx, page)
        views(root).first { it.contentDescription == "Next lesson" }.performClick()
        assertEquals(com.husarp.ankigo.help.GameGuide.MOVES, page.currentGuideChapter)
        views(root).first { it.contentDescription == "Previous lesson" }.performClick()
        assertEquals(com.husarp.ankigo.help.GameGuide.BATTLES, page.currentGuideChapter)
        root
    }

    @Test fun settingLinkKeepsHelpAliveAndSupportsAWindowContext() = paparazzi.shootPage("r22m-link-intent", listOf(Variants.PHONE)) { ctx ->
        var opened: android.content.Intent? = null
        val window = object : android.content.ContextWrapper(ctx) {
            override fun startActivity(intent: android.content.Intent) { opened = intent }
        }
        com.husarp.ankigo.help.HelpSettingsLinks.open(window, "leaveMode")
        val intent = opened!!
        assertEquals("leaveMode", intent.getStringExtra("helpSetting"))
        assertTrue(intent.flags and android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION != 0)
        assertTrue(intent.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        android.view.View(ctx)
    }

    @Test fun inlineLinksKeepSpansWhenPathsAndQuotesWrap() = paparazzi.shootPage("r22m-inline-links", listOf(Variants.PHONE)) { ctx ->
        val view = com.husarp.ankigo.help.HelpViews.rich(ctx, "Open Settings → Game → \"When you beat a wild Pokémon\" and choose. Again Hard Good Easy")
        val text = view.text as android.text.Spanned
        assertEquals(1, text.getSpans(0, text.length, android.text.style.ClickableSpan::class.java).size)
        assertEquals(4, text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java).size)
        assertTrue(text.toString().contains("Settings › Game ›"))
        val result = com.husarp.ankigo.help.HelpViews.resultChip(ctx, com.husarp.ankigo.help.PictureCheck(null, "Allow access (Settings → Diagnostics)"))
        val resultText = result.text as android.text.Spanned
        assertEquals(1, resultText.getSpans(0, resultText.length, android.text.style.ClickableSpan::class.java).size)
        view
    }

    @Test fun defensivePickerShowsIncomingImmunities() = paparazzi.shootPage("r22m-defend", listOf(Variants.PHONE)) { ctx ->
        val picker = GuideTypeChart.picker(ctx)
        views(picker).first { it.contentDescription == "Show ghost matchups" }.performClick()
        views(picker).filterIsInstance<android.widget.TextView>().first { it.text.toString() == "Defend" }.performClick()
        val text = views(picker).filterIsInstance<android.widget.TextView>().map { it.text.toString() }
        assertTrue(text.any { it.contains("Immune to") })
        assertTrue(views(picker).any { it.contentDescription?.toString()?.equals("Normal", true) == true } || text.any { it.contains("Normal", true) })
        picker
    }

    @Test fun nextAndPreviousFollowTheSelectedChapter() = paparazzi.shootPage("r22m-chapter-navigation", listOf(Variants.PHONE, Variants.SPLIT)) { ctx ->
        val page = com.husarp.ankigo.help.HelpScreen(ctx, NoHost,
            com.husarp.ankigo.help.HelpRoute.Guide(com.husarp.ankigo.help.GameGuide.TYPES), false)
        val root = pageScreen(ctx, page)
        fun click(label: String) = views(root).first { it.contentDescription == label }.performClick()
        click("Next lesson")
        assertTrue(views(root).filterIsInstance<android.widget.TextView>().any { it.text.toString() == "Stats, IVs, EVs and natures ▾" })
        click("Previous lesson")
        assertTrue(views(root).filterIsInstance<android.widget.TextView>().any { it.text.toString() == "Types and matchups ▾" })
        root
    }

    @Test fun typeGrid() = paparazzi.shootPage("r22m-type-grid", listOf(Variants.PHONE)) { GuideTypeChart.grid(it) }
    @Test fun helpSettingBackLeavesInsteadOfOpeningParent() = paparazzi.shootPage("r22m-help-setting", listOf(Variants.PHONE)) { ctx ->
        val intent = com.husarp.ankigo.help.HelpSettingsLinks.intent(ctx, "leaveMode")
        assertEquals("leaveMode", intent.getStringExtra("helpSetting"))
        assertTrue(intent.flags and android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION != 0)
        val page = SettingsPage(ctx, NoHost)
        page.helpEntry = true
        page.restoreHelpSetting(SettingsCatalog.all.first { it.id == "leaveMode" })
        val view = pageScreen(ctx, page)
        assertFalse(page.goHome())
        assertNotNull(page.section)
        view
    }
}
