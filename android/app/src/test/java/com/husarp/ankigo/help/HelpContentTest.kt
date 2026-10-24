package com.husarp.ankigo.help

import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpContentTest {
    private fun allText(p: HelpPage): List<String> =
        listOf(p.id, p.title, p.summary) + p.sections.flatMap { listOf(it.text) + it.steps } + p.links.flatMap { listOf(it.label, it.url) } +
            p.notes.flatMap { listOf(it.title, it.text) }

    @Test fun pageIdsAreUniqueAndAllEightPagesExist() {
        val ids = HelpContent.pages.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(8, ids.size)
        listOf(
            HelpContent.PICTURES_DONT_SHOW, HelpContent.MOVE_COLLECTION, HelpContent.PICTURES_MISSING, HelpContent.PICTURES_SYNC,
            HelpContent.ANSWERS_NOT_DETECTED, HelpContent.WIDGET_NOT_UPDATING, HelpContent.BATTLE_POPS_UP, HelpContent.ANKIMON_IMPORT_EXPORT,
        ).forEach { assertNotNull(it, HelpContent.page(it)) }
    }

    @Test fun everyPageHasTheFourPartsInOrderAndAtLeastOneStep() {
        for (p in HelpContent.pages) {
            assertEquals(p.id, SectionKind.entries, p.sections.map { it.kind })
            for (k in SectionKind.entries) {
                val s = p.section(k)!!
                assertTrue("${p.id} ${k.name} empty", s.text.isNotBlank() || s.steps.isNotEmpty())
            }
            assertTrue("${p.id} needs a see text", p.section(SectionKind.SEE)!!.text.isNotBlank())
            assertTrue("${p.id} needs a why text", p.section(SectionKind.WHY)!!.text.isNotBlank())
            assertTrue("${p.id} needs a check text", p.section(SectionKind.CHECK)!!.text.isNotBlank())
            assertTrue("${p.id} needs steps", p.section(SectionKind.DO)!!.steps.isNotEmpty())
        }
    }

    @Test fun noEmptyStringsNoTodoAndLinksAreUrls() {
        for (p in HelpContent.pages) {
            for (t in allText(p)) {
                assertTrue("${p.id}: blank string", t.isNotBlank() || t.isEmpty()) // empty optional texts are allowed
                assertFalse("${p.id}: TODO in '$t'", t.contains("TODO", ignoreCase = true))
            }
            for (s in p.sections) {
                assertTrue(s.steps.all { it.isNotBlank() })
            }
            assertTrue(p.title.isNotBlank() && p.summary.isNotBlank())
            for (l in p.links) {
                assertTrue(l.label.isNotBlank())
                assertTrue(l.url, l.url.startsWith("https://"))
            }
        }
    }

    @Test fun ankimonPageUsesTheGuideStepsAndNotes() {
        val p = HelpContent.page(HelpContent.ANKIMON_IMPORT_EXPORT)!!
        val steps = p.section(SectionKind.DO)!!.steps.joinToString("\n")
        for (s in AnkimonGuide.steps) assertTrue(steps.contains(s.title))
        assertEquals(AnkimonGuide.notes, p.notes)
    }

    @Test fun battlePageDescribesTheCurrentLeaveBehaviour() {
        val text = allText(HelpContent.page(HelpContent.BATTLE_POPS_UP)!!).joinToString(" ")
        assertTrue(text.contains("Pop out"))
        assertTrue(text.contains("On leaving"))
        assertTrue(text.contains("Nothing"))
        assertTrue(text.contains("Picture-in-picture"))
    }

    // ---- Ankimon guide

    @Test fun ankimonGuideHasTheFourStepsWithExactMenuNames() {
        assertEquals(4, AnkimonGuide.steps.size)
        val all = AnkimonGuide.steps.joinToString(" ") { it.title + " " + it.detail }
        listOf("Tools → Add-ons", "Check for Updates", "2.4-E", "Game → Export Save File…", "Import Ankimon save (.db)…").forEach {
            assertTrue(it, all.contains(it))
        }
        assertEquals("Ankimon PC sync", AnkimonGuide.SECTION_TITLE)
        assertTrue(AnkimonGuide.steps.all { it.title.isNotBlank() && it.detail.isNotBlank() })
    }

    @Test fun ankimonGuideWarnsAboutOldSavesShowdownMergeAndCoins() {
        val all = AnkimonGuide.notes.joinToString(" ") { it.title + " " + it.text }
        listOf("mypokemon.json", "Access is denied", "Showdown", "Pokepaste", "never removes", "higher").forEach { assertTrue(it, all.contains(it)) }
        assertTrue(AnkimonGuide.notes.count { it.severity == GuideNote.Severity.WARNING } >= 3)
    }

    // ---- Game guide

    @Test fun guideCoversTheNineTopicsWithUniqueIds() {
        val ids = GameGuide.chapters().map { it.id }
        assertEquals(
            listOf(GameGuide.BATTLES, GameGuide.MOVES, GameGuide.LEVELS, GameGuide.TYPES, GameGuide.STATS, GameGuide.CATCHING, GameGuide.MONEY, GameGuide.HEALING, GameGuide.ITEMS),
            ids,
        )
        assertEquals(9, ids.toSet().size)   // R17C-10: Moves and effects joined the eight
        for (c in GameGuide.chapters()) {
            assertTrue(c.title.isNotBlank() && c.intro.isNotBlank())
            assertTrue(c.points.isNotEmpty() && c.points.all { it.isNotBlank() })
            assertTrue(c.visuals.isNotEmpty())
            assertFalse((listOf(c.title, c.intro) + c.points).any { it.contains("TODO", ignoreCase = true) })
        }
    }

    @Test fun guideVisualsPointAtRealItemsAndKnownTags() {
        val tagKinds = setOf("MAIN", "FAINTED", "CAN_EVOLVE", "NEW", "EXP_SHARE", "HELD", "SHINY", "REGIONAL")
        for (v in GameGuide.chapters().flatMap { it.visuals }) {
            when (v.id) {
                GuideVisualId.ITEM_ICON -> assertTrue("item ${v.arg}", Items.def(v.arg!!) != null)
                GuideVisualId.TAG -> assertTrue("tag ${v.arg}", v.arg in tagKinds)
                else -> {}
            }
        }
    }

    private fun chapterText(id: String) = GameGuide.chapter(id)!!.let { (listOf(it.intro) + it.points).joinToString(" ") }

    @Test fun numbersOfTheOtherChaptersAreTheTuningConstants() {
        val battles = chapterText(GameGuide.BATTLES)
        assertTrue(battles.contains("${Tuning.CARDS_PER_ROUND} answers"))
        // R10-B22: who attacks by answer (the numbers are the Tuning constants)
        assertTrue(battles.contains("Your Main attacks when the round has a Good or an Easy answer"))
        assertTrue(battles.contains("Good adds ×0.5 and Easy adds ×1 to its damage (Good + Good = ×1, Easy + Easy = ×2)"))
        assertTrue(battles.contains("The wild Pokémon attacks when the round has an Again or a Hard answer"))
        assertTrue(battles.contains("Again gives it ×1 and Hard ×0.5 of its normal damage (Again + Again = ×2, Hard + Easy = ×0.5)"))
        assertTrue(battles.contains("Both can attack in the same round"))
        assertTrue(battles.contains("÷ 7"))
        assertTrue(battles.contains("${Tuning.MAX_LEVELS_PER_GAIN} levels"))
        assertTrue(battles.contains("×1.5 XP"))
        assertEquals(1.5, Tuning.LUCKY_EGG, 0.0)
        assertTrue(chapterText(GameGuide.LEVELS).contains("level ${Tuning.MAX_LEVEL}"))
        assertTrue(chapterText(GameGuide.LEVELS).contains("${Tuning.DEFAULT_MIN_FRIENDSHIP}"))
        assertTrue(chapterText(GameGuide.LEVELS).contains("${Tuning.FRIENDSHIP_GAIN.first} to ${Tuning.FRIENDSHIP_GAIN.last}"))
        assertTrue(chapterText(GameGuide.TYPES).contains("There are 18 types"))
        assertTrue(chapterText(GameGuide.TYPES).contains("×1.5"))
        val stats = chapterText(GameGuide.STATS)
        assertTrue(stats.contains("${Tuning.EV_STAT_CAP}") && stats.contains("${Tuning.EV_TOTAL_CAP}"))
        val catching = chapterText(GameGuide.CATCHING)
        assertTrue(catching.contains("Poké Ball ${Tuning.BALL_CHANCE["poke-ball"]}%"))
        assertTrue(catching.contains("Great Ball ${Tuning.BALL_CHANCE["great-ball"]}%"))
        assertTrue(catching.contains("Ultra Ball ${Tuning.BALL_CHANCE["ultra-ball"]}%"))
        assertTrue(catching.contains("Master Ball ${Tuning.BALL_CHANCE["master-ball"]}%"))
        assertTrue(catching.contains("1 in ${Tuning.SHINY_ODDS}"))
        assertTrue(catching.contains("while it still has HP") && catching.contains("caught automatically"))
        assertTrue(catching.contains("used up whether it works or not") && catching.contains("runs away"))   // PLAN 171
        assertTrue(catching.contains("throws like a Great Ball") && catching.contains("the same XP and you the same ${Tuning.CASH_PER_WIN} ₽ as for a defeat"))   // PLAN 171, 170, R13 Part C
        val items = chapterText(GameGuide.ITEMS)
        assertTrue(items.contains("${Tuning.ITEM_COUNTDOWN.first} to ${Tuning.ITEM_COUNTDOWN.last}"))
        assertTrue(items.contains("level ${Tuning.FOSSIL_LEVEL}"))
        val money = chapterText(GameGuide.MONEY)
        assertTrue(money.contains("${Tuning.SHOP_BASE_SLOTS} items"))
        assertTrue(money.contains("${Tuning.REROLL_PRICE}") && money.contains("${Tuning.TM_PRICE}"))
        assertTrue(money.contains("Poké Ball costs 200 ₽") && money.contains("Great Ball 700 ₽") && money.contains("Ultra Ball 1,500 ₽") && money.contains("Master Ball is never sold"))   // R13 Part C
    }

    @Test fun moneyAndHealingUseThePendingNumbersInOnePlace() {
        val n = GuideNumbers.pending()
        assertEquals(listOf(5, 10, 500, 30, 75, 22, 3, 40, 40, 20, 365), listOf(n.cashPerAnswer, n.cashPerWin, n.cashTier, n.giftMaxStreakDays, n.giftCommonFirst, n.giftUncommonFirst, n.giftRareFirst,
            n.giftCommonLast, n.giftUncommonLast, n.giftRareLast, n.masterBallStreakDays))
        assertEquals(listOf(25, 10), listOf(n.levelUpHealPercent, n.defeatHealPercent))
        assertEquals(0.5, n.benchRestPercentPerAnswer, 0.0)
        val money = chapterText(GameGuide.MONEY)
        assertTrue(money.contains("+5 for every answer") && money.contains("+10 for every wild Pokémon you defeat or catch") && !money.contains("At most 500"))
        assertTrue(money.contains("in the first 500 ₽ every answer pays 5 ₽ and every win 10 ₽") && money.contains("in the next 500 ₽ half of that (an answer 2.5 ₽, paid in whole coins as they add up), then a third, a quarter"))
        // R14-B2-D: the daily gift replaced the money bonus; the whole table is in the guide
        assertTrue(money.contains("Daily gift: the first time you reach your daily goal each day, you get one gift in your Bag"))
        assertTrue(money.contains("On day 1 it is common 75%, uncommon 22% or rare 3%") && money.contains("common 40%, uncommon 40% and rare 20% after 30 days in a row"))
        assertTrue(money.contains("Common: 3 Poké Balls, 2 Potions or a Super Potion. Uncommon: 2 Great Balls, a Hyper Potion or 5 Poké Balls. Rare: an Ultra Ball, an evolution stone or a TM you don't own yet."))
        assertTrue(money.contains("The Master Ball is never sold: you get one for reaching the top tier of Best streak (365 days in a row)."))
        assertFalse(money.contains("bonus of 50"))
        val healing = chapterText(GameGuide.HEALING)
        assertTrue(healing.contains("heals 25% of max HP") && healing.contains("heals 10% of max HP") && healing.contains("0.5% of their max HP"))
        // the chapter text follows whatever numbers it is given
        val other = GameGuide.chapters(n.copy(cashPerAnswer = 7, levelUpHealPercent = 30)).first { it.id == GameGuide.MONEY }
        assertTrue(other.points.any { it.startsWith("+7 for every answer") })
        assertTrue(GameGuide.chapters(n.copy(levelUpHealPercent = 30)).first { it.id == GameGuide.HEALING }.points.any { it.contains("30%") })
    }
}
