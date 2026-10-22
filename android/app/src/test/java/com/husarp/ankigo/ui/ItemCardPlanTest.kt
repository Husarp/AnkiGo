package com.husarp.ankigo.ui

import com.husarp.ankigo.game.BallTrade
import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN 168 / 181 / 199 (R14 B2-B), PLAN 210 (R15-A): which item's window is open, the Bag's buttons, the words around the cards. */
class ItemCardPlanTest {
    private val data = TestData.world()
    private fun state(vararg bag: Pair<String, Int>) = TestData.state(data).also { it.bag.clear(); it.bag.putAll(bag) }

    // ---- one window open at a time ----

    @Test fun openingAnotherItemReplacesTheOneThatWasOpen() {
        val o = OpenCard()
        assertNull(o.key)
        o.open("a"); assertEquals("a", o.key)
        o.open("b"); assertEquals("b", o.key)
        o.reset(); assertNull("closing the window ends it", o.key)
    }

    @Test fun theTradeConfirmBelongsToTheOpenWindowAndIsDroppedWithIt() {
        val o = OpenCard()
        o.confirmTrade(true); assertFalse("no window, no confirm", o.confirming)
        o.open("bag:poke-ball"); o.confirmTrade(true); assertTrue(o.confirming)
        o.open("bag:great-ball"); assertFalse("another item: the confirm is gone", o.confirming)
        o.confirmTrade(true); o.confirmTrade(false); assertFalse("cancel closes it", o.confirming)
        o.open("x"); o.confirmTrade(true); o.reset(); assertFalse(o.confirming); assertNull(o.key)
    }

    @Test fun aWindowKeptOpenAfterATradeEndsWithTheWindow() {
        val o = OpenCard()
        o.keepWhenEmpty(); assertFalse("no window, nothing kept", o.keepEmpty)
        o.open("bag:poke-ball"); o.keepWhenEmpty(); assertTrue(o.keepEmpty)
        o.open("bag:poke-ball"); assertFalse("opening it again ends it", o.keepEmpty)
        o.keepWhenEmpty(); o.open("bag:great-ball"); assertFalse("another item ends it", o.keepEmpty)
        o.keepWhenEmpty(); o.reset(); assertFalse("closing or leaving the page ends it", o.keepEmpty)
    }

    @Test fun theKeysSayWhereACardIs() {
        val keys = listOf(
            CardKeys.DEAL, CardKeys.WEEKEND, CardKeys.basic(ShopPlan.Tile.Item("potion")), CardKeys.special(ShopPlan.Tile.Item("potion")),
            CardKeys.special(ShopPlan.Tile.Tm("potion")), CardKeys.bagItem("potion"), CardKeys.bagTm("potion"),
        )
        assertEquals("the same thing in two places has two keys", keys.size, keys.toSet().size)
    }

    @Test fun aBagKeyGivesBackItsItemOrMoveAndNothingElse() {
        assertEquals("potion", CardKeys.bagItemId(CardKeys.bagItem("potion")))
        assertNull(CardKeys.bagItemId(CardKeys.bagTm("potion")))
        assertNull(CardKeys.bagItemId(CardKeys.DEAL))
        assertNull(CardKeys.bagItemId(CardKeys.special(ShopPlan.Tile.Item("potion"))))
        assertEquals("thunderbolt", CardKeys.bagTmMove(CardKeys.bagTm("thunderbolt")))
        assertNull(CardKeys.bagTmMove(CardKeys.bagItem("thunderbolt")))
    }

    // ---- the Bag's buttons ----

    @Test fun everyKindHasItsActionAndTheLabelsAreThePickedOnes() {
        assertEquals("Throw", BagAct.of(ItemKind.BALL)!!.label)
        assertEquals("Use", BagAct.of(ItemKind.ESCAPE)!!.label)
        assertEquals("Use on…", BagAct.of(ItemKind.HEAL)!!.label)
        assertEquals("Use on…", BagAct.of(ItemKind.EVOLUTION)!!.label)
        assertEquals("Give to…", BagAct.of(ItemKind.HELD)!!.label)
        assertEquals("Revive", BagAct.of(ItemKind.FOSSIL)!!.label)
        assertEquals("Teach…", BagAct.TEACH.label)
        assertNull("an item the catalogue does not know has no button", BagAct.of(null))
    }

    @Test fun ballsAndEscapeItemsNeedAFightTheRestWorkAnyTime() {
        val calm = state()
        val fight = state().also { it.battle = Battle(TestData.mon(data, 19, 5), Tier.NORMAL) }
        assertFalse(BagAct.THROW.usableNow(calm))
        assertFalse(BagAct.USE.usableNow(calm))
        assertTrue(BagAct.THROW.usableNow(fight))
        assertTrue(BagAct.USE.usableNow(fight))
        for (a in listOf(BagAct.USE_ON, BagAct.GIVE, BagAct.REVIVE, BagAct.TEACH)) assertTrue("$a", a.usableNow(calm))
        assertEquals("Balls are thrown from a fight", BagAct.THROW.offFight)
    }

    @Test fun aWildPokemonWithoutHpLeftIsNotAFightForABall() {
        val s = state().also { it.battle = Battle(TestData.mon(data, 19, 5).also { m -> m.hp = 0 }, Tier.NORMAL) }
        assertFalse(BagAct.THROW.usableNow(s))
        assertTrue("an escape item only needs a wild Pokémon (Game.escape)", BagAct.USE.usableNow(s))
    }

    // ---- the words ----

    @Test fun whoCanLearnItSaysHowManyOrNone() {
        assertEquals("7 of your Pokémon", ItemCardPlan.whoCount(7))
        assertEquals("1 of your Pokémon", ItemCardPlan.whoCount(1))
        assertEquals("None of your Pokémon", ItemCardPlan.whoCount(0))
    }

    @Test fun theTradeButtonAndItsConfirmAreInWordsNotArrows() {
        val s = state("poke-ball" to 7)
        val o = BallTrade.offer(s, "poke-ball")!!
        assertEquals("Trade 5 Poké Balls for 1 Great Ball", ItemCardPlan.tradeLabel(o))
        assertEquals("Trade 5 for 1", ItemCardPlan.tradeGo(o))
        assertEquals(
            "5 Poké Balls (1,000 ₽ in the Shop) for 1 Great Ball (700 ₽). **On purpose worse than buying.** You keep 2 Poké Balls.",
            ItemCardPlan.tradeConfirm(s, o),
        )
        val g = state("great-ball" to 3)
        val og = BallTrade.offer(g, "great-ball")!!
        assertEquals("Trade 3 Great Balls for 1 Ultra Ball", ItemCardPlan.tradeLabel(og))
        assertEquals(
            "3 Great Balls (2,100 ₽ in the Shop) for 1 Ultra Ball (1,500 ₽). **On purpose worse than buying.** You keep no Great Balls.",
            ItemCardPlan.tradeConfirm(g, og),
        )
    }

    @Test fun theConfirmNamesExactlyWhatGoesWhenOldBallsAreIn() {
        val s = state("great-ball" to 4, "net-ball" to 1)
        val o = BallTrade.offer(s, "great-ball")!!
        assertEquals(
            "1 Net Ball and 2 Great Balls for 1 Ultra Ball. **On purpose worse than buying.** You keep 2 Great Balls.",
            ItemCardPlan.tradeConfirm(s, o),
        )
        // an old ball's own card counts Great Balls too
        val n = BallTrade.offer(s, "net-ball")!!
        assertEquals("Trade 3 Great Balls for 1 Ultra Ball", ItemCardPlan.tradeLabel(n))
        assertTrue(ItemCardPlan.tradeConfirm(s, n).endsWith("You keep no Net Balls."))
    }

    @Test fun tooFewSaysHowManyYouNeed() {
        val o = BallTrade.offer(state("poke-ball" to 3), "poke-ball")!!
        assertEquals("Need 5, you have 3", ItemCardPlan.tradeShort(o))
    }

    @Test fun talkBackSaysTheTileOpensDetails() {
        assertEquals("Great Ball, 50 % to catch, 700. Opens details", ItemCardPlan.detailsLabel("Great Ball, 50 % to catch, 700"))
        assertTrue("no word of the old in-place card", !ItemCardPlan.detailsLabel("TM Thunderbolt").contains("fold") && !ItemCardPlan.detailsLabel("TM Thunderbolt").contains("in place"))
    }

    @Test fun theMoneyToastIsTheOldOne() {
        assertEquals("Not enough money (need 1,500 ₽)", ItemCardPlan.poor(1500))
    }

    // ---- bold marks ----

    @Test fun boldPartsAreSplitOutAndTheStarsNeverShow() {
        assertEquals(listOf(Marks.Part("Heals ", false), Marks.Part("20 HP", true), Marks.Part(", never above max.", false)), Marks.split("Heals **20 HP**, never above max."))
        assertEquals(listOf(Marks.Part("no marks", false)), Marks.split("no marks"))
        assertEquals(listOf(Marks.Part("all bold", true)), Marks.split("**all bold**"))
        assertEquals("an unclosed mark runs to the end", listOf(Marks.Part("a ", false), Marks.Part("b c", true)), Marks.split("a **b c"))
        assertTrue(Marks.split("x **y** z **w**").none { it.text.contains("*") })
        assertTrue(Marks.split("").isEmpty())
    }
}
