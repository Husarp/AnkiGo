package com.husarp.ankigo.help

import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpModelTest {
    // ---- routes

    @Test fun routesFallBackToTheHomeForUnknownIds() {
        assertEquals(HelpRoute.Home, HelpRoutes.parse(null, null))
        assertEquals(HelpRoute.Home, HelpRoutes.parse("nope", "nope"))
        assertEquals(HelpRoute.Problem(HelpContent.PICTURES_SYNC), HelpRoutes.parse(HelpContent.PICTURES_SYNC, null))
        assertEquals(HelpRoute.Guide(GameGuide.MONEY), HelpRoutes.parse(null, GameGuide.MONEY))
        assertEquals(HelpRoute.Guide(null), HelpRoutes.parse(null, HelpRoutes.GUIDE))
        // a page wins over a chapter
        assertEquals(HelpRoute.Problem(HelpContent.BATTLE_POPS_UP), HelpRoutes.parse(HelpContent.BATTLE_POPS_UP, GameGuide.ITEMS))
    }

    @Test fun everyChapterAndPageHasARoute() {
        for (c in GameGuide.chapters()) assertEquals(HelpRoute.Guide(c.id), HelpRoutes.parse(null, c.id))
        for (p in HelpContent.pages) {
            assertEquals(HelpRoute.Problem(p.id), HelpRoutes.parse(p.id, null))
            assertEquals(p.title, HelpRoutes.title(HelpRoute.Problem(p.id)))
        }
        assertEquals("Help", HelpRoutes.title(HelpRoute.Home))
    }

    @Test fun backGoesHomeThenLeavesAndLeavesAtOnceWhenOpenedOnAPage() {
        assertNull(HelpRoutes.back(HelpRoute.Home, false))
        assertEquals(HelpRoute.Home, HelpRoutes.back(HelpRoute.Problem(HelpContent.PICTURES_SYNC), false))
        assertEquals(HelpRoute.Home, HelpRoutes.back(HelpRoute.Guide(null), false))
        assertNull(HelpRoutes.back(HelpRoute.Problem(HelpContent.PICTURES_SYNC), true))
    }

    // ---- buttons

    @Test fun everyButtonIsWiredAndHelpPageButtonsPointAtRealPages() {
        for (p in HelpContent.pages) {
            for (b in HelpFixes.buttons(p.id)) {
                assertTrue(b.label.isNotBlank())
                when (b.action) {
                    HelpAction.HELP_PAGE -> assertTrue("${p.id} -> ${b.arg}", HelpContent.page(b.arg.orEmpty()) != null && b.arg != p.id)
                    HelpAction.OPEN_URL -> assertTrue(b.arg!!.startsWith("https://"))
                    else -> assertNull(b.arg)
                }
            }
            // every web link of the page is a button
            assertEquals(p.links.map { it.url }, HelpFixes.buttons(p.id).filter { it.action == HelpAction.OPEN_URL }.map { it.arg })
        }
    }

    @Test fun picturesPageHasTheFixesOfThePicture() {
        val a = HelpFixes.buttons(HelpContent.PICTURES_DONT_SHOW).map { it.action }
        assertTrue(HelpAction.ALL_FILES_ACCESS in a && HelpAction.IMPORT_APKG in a && HelpAction.OPEN_URL in a)
        assertTrue(HelpAction.OPEN_DIAGNOSTICS in HelpFixes.buttons(HelpContent.ANSWERS_NOT_DETECTED).map { it.action })
        assertTrue(HelpAction.OPEN_SETTINGS in HelpFixes.buttons(HelpContent.BATTLE_POPS_UP).map { it.action })
        assertTrue(HelpFixes.buttons("nope").isEmpty())
    }

    @Test fun everySetUpPicturesFixMapsToAButton() {
        for (a in FixAction.entries) {
            val b = HelpFixes.fromFix(Fix(a, if (a == FixAction.HELP_PAGE) HelpContent.MOVE_COLLECTION else null))
            val expected = when (a) {
                FixAction.OPEN_ALL_FILES_ACCESS -> HelpAction.ALL_FILES_ACCESS
                FixAction.OPEN_ANKIDROID_SETTINGS -> HelpAction.ANKIDROID_SETTINGS
                FixAction.OPEN_ANKIDROID -> HelpAction.OPEN_ANKIDROID
                FixAction.OPEN_GITHUB_RELEASES -> HelpAction.OPEN_URL
                FixAction.IMPORT_APKG -> HelpAction.IMPORT_APKG
                FixAction.HELP_PAGE -> HelpAction.HELP_PAGE
            }
            assertEquals(a.name, expected, b.action)
        }
        assertEquals(HelpContent.page(HelpContent.MOVE_COLLECTION)!!.title, HelpFixes.fromFix(Fix(FixAction.HELP_PAGE, HelpContent.MOVE_COLLECTION)).label)
        assertTrue(HelpFixes.fromFix(Fix(FixAction.OPEN_GITHUB_RELEASES)).arg == HelpFixes.RELEASES_URL)
        // the releases link of the pictures page is the same address
        assertTrue(HelpContent.page(HelpContent.PICTURES_DONT_SHOW)!!.links.any { it.url == HelpFixes.RELEASES_URL })
    }

    private val facts = PictureFacts(34, true, AnkiDroidFlavour.FULL, PublicFolder.READABLE, true, false, true, 3, 3)

    @Test fun pictureCheckSaysHowManyWereRead() {
        assertEquals(PictureCheck(true, "3 of 3 read"), HelpFixes.pictureCheck(facts))
        assertEquals(PictureCheck(false, "1 of 3 read"), HelpFixes.pictureCheck(facts.copy(sampleReadable = 1)))
        assertEquals(false, HelpFixes.pictureCheck(facts.copy(ankiDroidInstalled = false)).ok)
        assertNull(HelpFixes.pictureCheck(facts.copy(apiAllowed = false)).ok)
        assertNull(HelpFixes.pictureCheck(facts.copy(sampleTotal = 0, sampleReadable = 0)).ok)
        assertTrue(HelpFixes.checksPictures(HelpContent.PICTURES_MISSING) && !HelpFixes.checksPictures(HelpContent.WIDGET_NOT_UPDATING))
    }

    // ---- guide

    @Test fun everyChapterHasAShortTitle() {
        for (c in GameGuide.chapters()) {
            val s = GuideDemo.shortTitle(c.id)
            assertTrue(c.id, s.isNotBlank() && s.length <= 20)
        }
    }

    @Test fun guideVisualArgumentsAreReal() {
        val demoTags = setOf("MAIN", "FAINTED", "CAN_EVOLVE", "NEW", "EXP_SHARE", "HELD", "SHINY", "REGIONAL")
        for (v in GameGuide.chapters().flatMap { it.visuals }) {
            when (v.id) {
                GuideVisualId.TYPE_CHIPS -> if (v.arg != null) assertTrue(v.arg, Type.entries.any { it.name == v.arg })
                GuideVisualId.TAG -> assertTrue(GuideDemo.tagLabel(v.arg!!) != v.arg || v.arg in demoTags)
                else -> {}
            }
        }
        assertEquals(10, GuideDemo.levels.size)
        assertEquals(6, GuideDemo.stats.size)
        assertTrue(GuideDemo.stats.all { it in 1..GuideDemo.STAT_BAR_MAX })
        assertTrue(GuideDemo.statuses.all { com.husarp.ankigo.ui.StatusInfo.of(it) != null })
    }

    @Test fun guideMoneyNumbersAreTheTuningConstants() {
        val n = GuideNumbers.pending()
        assertEquals(Tuning.CASH_PER_ANSWER, n.cashPerAnswer)
        assertEquals(Tuning.CASH_TIER, n.cashTier)
        assertEquals(Tuning.CASH_PER_WIN, n.cashPerWin)
        // R14-B2-D: the daily gift's chances come from the table (DailyGift), the Master Ball day count from the Best streak row
        assertEquals(Tuning.GIFT_MAX_DAYS, n.giftMaxStreakDays)
        assertEquals(listOf(75, 22, 3), listOf(n.giftCommonFirst, n.giftUncommonFirst, n.giftRareFirst))
        assertEquals(listOf(40, 40, 20), listOf(n.giftCommonLast, n.giftUncommonLast, n.giftRareLast))
        assertEquals(365, n.masterBallStreakDays)
        val money = GameGuide.chapter(GameGuide.MONEY)!!.let { (listOf(it.intro) + it.points).joinToString(" ") }
        assertTrue(money.contains("+${Tuning.CASH_PER_ANSWER} for every answer"))
        assertTrue(money.contains("in the first ${Tuning.CASH_TIER} ₽ every answer pays ${Tuning.CASH_PER_ANSWER} ₽ and every win ${Tuning.CASH_PER_WIN} ₽") && money.contains("then a third, a quarter"))   // PLAN 125's curve
        assertTrue(money.contains("+${Tuning.CASH_PER_WIN} for every wild Pokémon you defeat or catch"))   // R13 Part C
        // Adam's decision: the coin / incense doubles the answer and win money (R14-B2-D: the daily goal gives a gift now, not money)
        assertTrue(money.contains("Amulet Coin") && money.contains("Luck Incense") && money.contains("doubles the money of your answers and wins"))
        assertFalse(money.contains("bonus of"))
        assertTrue(money.contains("Daily gift") && money.contains("it is never a Master Ball") && money.contains("doesn't touch your money"))
        assertTrue(Items.def("amulet-coin") != null && Items.def("luck-incense") != null)
    }

    // ---- grey boxes

    private fun boxes(s: String) = HelpText.pieces(s).filter { it.boxed }.map { it.text }

    @Test fun quotedNamesAreBoxedWithoutTheQuotes() {
        val p = HelpText.pieces("Give AnkiGo \"All files access\" now.")
        assertEquals(listOf(TextPiece("Give AnkiGo ", false), TextPiece("All files access", true), TextPiece(" now.", false)), p)
    }

    @Test fun arrowPathsAreBoxedAsOne() {
        assertEquals(listOf("Settings → Diagnostics"), boxes("In AnkiGo open Settings → Diagnostics and tap Grant AnkiDroid permission."))
        assertEquals(
            listOf("Android Settings → Apps → AnkiDroid → Battery → Unrestricted"),
            boxes("Allow it to run in the background: Android Settings → Apps → AnkiDroid → Battery → Unrestricted."),
        )
        assertEquals(listOf("Settings → Battle panel & windows → On leaving"), boxes("In AnkiGo open Settings → Battle panel & windows → On leaving."))
        assertEquals(listOf("Settings → Sync"), boxes("In AnkiDroid open Settings → Sync and raise the timeout."))
    }

    @Test fun aLowerCaseWordRightBeforeTheArrowJoinsTheName() {
        assertEquals(listOf("Ankimon menu → Game → Export Save File..."), boxes("In Anki open the Ankimon menu → Game → Export Save File... and pick where."))
    }

    @Test fun plainTextHasNoBoxesAndPiecesJoinBackToTheText() {
        assertTrue(boxes("Open a card that has a picture. It shows.").isEmpty())
        for (page in HelpContent.pages) for (s in page.sections) for (t in listOf(s.text) + s.steps) {
            val joined = HelpText.pieces(t).joinToString("") { it.text }
            assertEquals(t.replace("\"", ""), joined.replace("\"", ""))
            assertFalse(HelpText.pieces(t).any { it.text.isEmpty() })
        }
    }

    // ---- Set up pictures (R3-V3b)

    @Test fun setupPicturesHasItsOwnRoute() {
        assertEquals(HelpRoute.Setup, HelpRoutes.parse(HelpRoutes.SETUP, null))
        assertEquals("Set up pictures", HelpRoutes.title(HelpRoute.Setup))
        assertNull(HelpRoutes.back(HelpRoute.Setup, true)) // opened from a banner or Settings: back leaves Help
        assertEquals(HelpRoute.Home, HelpRoutes.back(HelpRoute.Setup, false))
        assertNull(HelpContent.page(HelpRoutes.SETUP)) // not a problem page: the id can't be taken by one
    }

    private fun facts(
        flavour: AnkiDroidFlavour = AnkiDroidFlavour.FULL, folder: PublicFolder = PublicFolder.READABLE, allFiles: Boolean = true,
        api: Boolean = true, total: Int = 20, readable: Int = 20,
    ) = PictureFacts(34, true, flavour, folder, allFiles, false, api, total, readable)

    @Test fun setupHeadlineCountsWhatNeedsFixing() {
        assertEquals("Pictures are set up", SetupText.headline(PictureSetup.evaluate(facts())))
        assertEquals("1 thing to fix", SetupText.headline(PictureSetup.evaluate(facts(total = 40, readable = 39))))
        val play = PictureSetup.evaluate(facts(flavour = AnkiDroidFlavour.PLAY, folder = PublicFolder.NOT_FOUND, allFiles = false, total = 12, readable = 0))
        assertTrue(SetupText.headline(play).endsWith("things to fix"))
        assertEquals("Nothing wrong so far", SetupText.headline(PictureSetup.evaluate(facts(total = 0, readable = 0))))
    }

    @Test fun everySetupFixBecomesAButtonThePageCanRun() {
        val worst = PictureSetup.evaluate(facts(flavour = AnkiDroidFlavour.PLAY, folder = PublicFolder.BLOCKED, allFiles = false, total = 12, readable = 0)) +
            PictureSetup.evaluate(facts(total = 20, readable = 5)) +
            PictureSetup.evaluate(facts(folder = PublicFolder.NOT_FOUND))
        val fixes = worst.flatMap { it.fixes }
        assertTrue(fixes.isNotEmpty())
        for (f in fixes) {
            val b = HelpFixes.fromFix(f)
            assertTrue(b.label.isNotBlank())
            if (b.action == HelpAction.HELP_PAGE) assertTrue(HelpContent.page(b.arg.orEmpty()) != null)
            if (b.action == HelpAction.OPEN_URL) assertTrue(b.arg!!.startsWith("https://"))
        }
        // All files access can be asked for from the Set up pictures screen itself
        assertTrue(fixes.any { HelpFixes.fromFix(it).action == HelpAction.ALL_FILES_ACCESS })
    }
}
