package com.husarp.ankigo.help

import com.husarp.ankigo.game.Bond
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
            HelpContent.ANSWERS_NOT_DETECTED, HelpContent.WIDGET_NOT_UPDATING, HelpContent.BATTLE_POPS_UP, HelpContent.ANKIMON_IMPORT,
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
        val p = HelpContent.page(HelpContent.ANKIMON_IMPORT)!!
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
        fun factor(d: Double) = d.toBigDecimal().stripTrailingZeros().toPlainString()
        val battles = chapterText(GameGuide.BATTLES)
        assertTrue(battles.contains("${Tuning.CARDS_PER_ROUND} answers"))
        assertTrue(battles.contains("Good adds ×${factor(Tuning.MAIN_POWER[3])}"))
        assertTrue(battles.contains("Easy adds ×${factor(Tuning.MAIN_POWER[4])}"))
        assertTrue(battles.contains("Again adds ×${factor(Tuning.WILD_POWER[1])}"))
        assertTrue(battles.contains("Hard adds ×${factor(Tuning.WILD_POWER[2])}"))
        assertTrue(battles.contains("${Tuning.WILD_AGAIN_STREAK} Again answers"))
        assertTrue(battles.contains("Attack by ${Tuning.WILD_ATTACK_STAGES} stage"))
        assertTrue(battles.contains("After ${Tuning.WILD_STRONGEST_ROUNDS} rounds"))
        assertTrue(battles.contains("×${factor(Tuning.SWITCH_HIT_POWER)} damage"))
        assertTrue(battles.contains("Both can attack in the same round"))
        assertTrue(battles.contains("${(Tuning.XP_SHARE_PART * 100).toInt()}% of the battle XP"))
        assertTrue(battles.contains("×${factor(Tuning.LUCKY_EGG)} XP"))
        assertTrue(battles.contains("${(Tuning.PRACTICE_XP_PART * 100).toInt()}% Pokémon and trainer XP"))
        assertTrue(chapterText(GameGuide.LEVELS).contains("level ${Tuning.MAX_LEVEL}"))
        assertTrue(chapterText(GameGuide.LEVELS).contains("+${Bond.CARD_POINTS} point per answered card in battle and +${Bond.WIN_POINTS} per win"))
        assertTrue(chapterText(GameGuide.TYPES).contains("There are ${com.husarp.ankigo.game.Type.entries.size} types"))
        assertTrue(chapterText(GameGuide.TYPES).contains("×${factor(Tuning.STAB)}"))
        val stats = chapterText(GameGuide.STATS)
        assertTrue(stats.contains("${Tuning.EV_STAT_CAP}") && stats.contains("${Tuning.EV_TOTAL_CAP}"))
        assertTrue(stats.contains("${-Tuning.WILD_LEVEL_SPREAD.first} below to ${Tuning.WILD_LEVEL_SPREAD.last} above"))
        assertTrue(stats.contains("1–${Tuning.MAX_LEVEL}"))
        val catching = chapterText(GameGuide.CATCHING)
        for ((ball, chance) in Tuning.BALL_CHANCE) assertTrue(catching.contains("${Items.name(ball)} $chance%"))
        assertTrue(catching.contains("1 in ${Tuning.SHINY_ODDS}"))
        assertTrue(catching.contains("still has HP") && catching.contains("your catch rule decides"))
        assertTrue(catching.contains("Each throw spends a regular ball") && catching.contains("runs away"))
        assertTrue(catching.contains("Ultra Heal Ball") && catching.contains("same Pokémon XP, trainer XP, EVs, Bond points and coins as defeating"))
        assertTrue(catching.contains("spends no balls from your Bag"))
        val items = chapterText(GameGuide.ITEMS)
        assertTrue(items.contains("${Tuning.ITEM_COUNTDOWN.first} to ${Tuning.ITEM_COUNTDOWN.last}"))
        assertTrue(items.contains("level ${Tuning.FOSSIL_LEVEL}"))
        assertTrue(items.contains("${Tuning.POKE_FOR_GREAT} Poké Balls") && items.contains("${Tuning.GREAT_FOR_ULTRA} Great Balls"))
        val money = chapterText(GameGuide.MONEY)
        assertTrue(money.contains("${Tuning.SHOP_BASE_SLOTS} items") && money.contains("per ${Tuning.SHOP_SLOTS_PER_LEVELS} trainer levels"))
        assertTrue(money.contains("${Tuning.REROLL_PRICE} coins") && money.contains("costs ${Tuning.REROLL_PRICE_STEP} more"))
        assertTrue(money.contains("TMs cost ${Tuning.TM_PRICE}"))
        assertTrue(money.contains("${Tuning.SPECIAL_SHOP_PERCENT}% chance"))
        assertTrue(money.contains("${Tuning.SHOP_VARIATION_PERCENT}% each day"))
        assertTrue(money.contains("${Tuning.DEAL_PERCENTS.first()}–${Tuning.DEAL_PERCENTS.last()}%"))
    }

    @Test fun moneyAndHealingUseThePendingNumbersInOnePlace() {
        val n = GuideNumbers.pending()
        assertEquals(Tuning.CASH_PER_ANSWER, n.cashPerAnswer)
        assertEquals(Tuning.CASH_PER_WIN, n.cashPerWin)
        assertEquals(Tuning.CASH_TIER, n.cashTier)
        assertEquals(Tuning.LEVEL_UP_HEAL_PERCENT, n.levelUpHealPercent)
        assertEquals(Tuning.DEFEAT_HEAL_PERCENT, n.defeatHealPercent)
        assertEquals(100.0 / Tuning.BENCH_REST_DIVISOR, n.benchRestPercentPerAnswer, 0.0)
        val money = chapterText(GameGuide.MONEY)
        assertTrue(money.contains("${n.cashPerAnswer} coins per answer") && money.contains("${n.cashPerWin} coins per defeat or catch"))
        assertTrue(money.contains("After ${n.cashTier} coins, the rate halves") && money.contains("a third, a quarter"))
        assertTrue(money.contains("one random gift per game day"))
        assertTrue(money.contains("Day 1: common ${n.giftCommonFirst}%, uncommon ${n.giftUncommonFirst}%, rare ${n.giftRareFirst}%"))
        assertTrue(money.contains("After ${n.giftMaxStreakDays} goals in a streak: common ${n.giftCommonLast}%, uncommon ${n.giftUncommonLast}%, rare ${n.giftRareLast}%"))
        assertTrue(money.contains(com.husarp.ankigo.game.DailyGift.poolText(Tuning.GIFT_COMMON)))
        assertTrue(money.contains(com.husarp.ankigo.game.DailyGift.poolText(Tuning.GIFT_UNCOMMON)))
        assertTrue(money.contains(com.husarp.ankigo.game.DailyGift.poolText(Tuning.GIFT_RARE_ITEMS)))
        assertTrue(chapterText(GameGuide.CATCHING).contains("${n.masterBallStreakDays} daily goals in your streak"))
        val healing = chapterText(GameGuide.HEALING)
        assertTrue(healing.contains("restores ${n.levelUpHealPercent}% of max HP"))
        assertTrue(healing.contains("heals ${n.defeatHealPercent}% of max HP"))
        assertTrue(healing.contains("${n.benchRestPercentPerAnswer}% of max HP per answer"))
        val other = GameGuide.chapters(n.copy(cashPerAnswer = 7, cashPerWin = 19, cashTier = 300,
            levelUpHealPercent = 30, defeatHealPercent = 12, benchRestPercentPerAnswer = 0.8, masterBallStreakDays = 200,
            giftCommonFirst = 70, giftUncommonFirst = 25, giftRareFirst = 5,
            giftCommonLast = 35, giftUncommonLast = 40, giftRareLast = 25, giftMaxStreakDays = 20))
        fun changed(id: String) = other.first { it.id == id }.points.joinToString(" ")
        assertTrue(changed(GameGuide.MONEY).contains("7 coins per answer"))
        assertTrue(changed(GameGuide.MONEY).contains("19 coins per defeat or catch"))
        assertTrue(changed(GameGuide.MONEY).contains("After 300 coins"))
        assertTrue(changed(GameGuide.MONEY).contains("Day 1: common 70%, uncommon 25%, rare 5%"))
        assertTrue(changed(GameGuide.MONEY).contains("After 20 goals in a streak: common 35%, uncommon 40%, rare 25%"))
        assertTrue(changed(GameGuide.HEALING).contains("30%") && changed(GameGuide.HEALING).contains("12%") && changed(GameGuide.HEALING).contains("0.8%"))
        assertTrue(changed(GameGuide.CATCHING).contains("200 daily goals"))
    }
}
