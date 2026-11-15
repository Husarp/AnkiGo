package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R34EHeldItemsTest {
    private val data = TestData.world()
    private fun state() = TestData.state(data, 54, 50)
    private fun give(s: GameState, item: String, uid: String = s.main!!.uid): ActionResult {
        Economy.give(s, item)
        return ItemUse.giveHeld(data, s, item, null, uid, 1L)
    }
    private fun battle(s: GameState): Battle = Battle(TestData.mon(data, 19, 20, listOf("splash")), Tier.NORMAL).also { s.battle = it }
    private fun round(s: GameState): Round = BattleEngine.Round(data, s.battle ?: battle(s), s.main!!, 1.0, 1.0, Random(7))
    private fun move(id: String) = Move(id, id, Type.NORMAL, Category.STATUS, 0, null)

    @Test fun bondBoundaryFillsSecondSlotOnlyAtLevelFiveAndRejectsDuplicates() {
        for (points in listOf(0, 799, 800)) {
            val s = state(); val m = s.main!!.apply { bondPoints = points }
            assertTrue(give(s, "everstone").ok)
            assertTrue(give(s, "lucky-egg").ok)
            assertEquals(if (points == 800) listOf("everstone", "lucky-egg") else listOf("lucky-egg"), m.heldItems)
            assertEquals(if (points == 800) 0 else 1, s.count("everstone"))
            assertFalse(give(s, "lucky-egg").ok)
            assertEquals(1, s.count("lucky-egg"))
        }
    }

    @Test fun ordinaryUseFillsTheSecondSlotAndFullSetsReplaceOnlyTheFirst() {
        val s = state(); val m = s.main!!.apply { bondPoints = 800 }
        give(s, "everstone")
        Economy.give(s, "soothe-bell")
        assertTrue(ItemUse.use(data, s, "soothe-bell", m.uid, Random(1), 1L).ok)
        assertEquals(listOf("everstone", "soothe-bell"), m.heldItems)
        assertTrue(give(s, "lucky-egg").ok)
        assertEquals(listOf("lucky-egg", "soothe-bell"), m.heldItems)
        assertEquals(1, s.count("everstone"))
    }

    @Test fun takeEitherItemReturnsExactlyThatCopyAndPromotesTheRemainingItem() {
        for (item in listOf("everstone", "lucky-egg")) {
            val s = state(); val m = s.main!!.apply { bondPoints = 800 }
            give(s, "everstone"); give(s, "lucky-egg")
            assertTrue(ItemUse.takeHeld(s, m.uid, item).ok)
            assertEquals(1, s.count(item)); assertEquals(1, m.heldItems.size)
            assertEquals(m.heldItems.single(), m.heldItem); assertNull(m.heldItem2)
            assertFalse(ItemUse.takeHeld(s, m.uid, item).ok)
            assertEquals(1, s.count(item))
        }
    }

    @Test fun transferFromSecondSlotIsAtomicAndDoesNotDisplaceTheTargetsFirstItem() {
        val s = state(); val source = s.main!!.apply { bondPoints = 800 }
        val target = TestData.mon(data, 25, 20, uid = "target").apply { bondPoints = 800; heldItem = "soothe-bell" }
        s.mons += target
        give(s, "everstone"); give(s, "lucky-egg")
        assertTrue(ItemUse.giveHeld(data, s, "lucky-egg", source.uid, target.uid).ok)
        assertEquals(listOf("everstone"), source.heldItems)
        assertEquals(listOf("soothe-bell", "lucky-egg"), target.heldItems)
        assertFalse(ItemUse.giveHeld(data, s, "lucky-egg", source.uid, target.uid).ok)
        assertFalse(ItemUse.giveHeld(data, s, "macho-brace", null, target.uid).ok)
        assertEquals(0, s.count("lucky-egg")); assertEquals(1, s.ownedItemCount("lucky-egg"))
    }

    @Test fun bondDropKeepsBothUntilTakenAndDoesNotAllowAnotherSecondItem() {
        val s = state(); val m = s.main!!.apply { bondPoints = 800 }
        give(s, "everstone"); give(s, "lucky-egg"); m.bondPoints = 799
        val restored = SaveCodec.decode(SaveCodec.encode(s)); val r = restored.main!!
        assertEquals(m.heldItems, r.heldItems)
        assertTrue(ItemUse.takeHeld(restored, r.uid, "everstone").ok)
        assertTrue(give(restored, "soothe-bell").ok)
        assertEquals(listOf("soothe-bell"), r.heldItems)
        assertEquals(1, restored.count("lucky-egg"))
    }

    @Test fun oldSaveHasOnlyItsOriginalItemAndNewSaveRoundTripsBenchAndWildSlots() {
        val s = state(); s.main!!.heldItem = "everstone"
        val old = SaveCodec.encode(s).replace(",\"heldItem2\":null", "")
        assertFalse(old.contains("\"heldItem2\""))
        val legacy = SaveCodec.decode(old).main!!
        assertEquals("everstone", legacy.heldItem); assertNull(legacy.heldItem2)
        s.main!!.apply { heldItem2 = "lucky-egg"; bondPoints = 800 }
        s.mons += TestData.mon(data, 25, 20, uid = "bench").apply { heldItem = "soothe-bell"; heldItem2 = "power-lens"; bondPoints = 800 }
        battle(s).wild.apply { heldItem = "oran-berry"; heldItem2 = "sitrus-berry"; bondPoints = 800 }
        val loaded = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(s.main!!.heldItems, loaded.main!!.heldItems)
        assertEquals(s.mons[1].heldItems, loaded.mons[1].heldItems)
        assertEquals(s.battle!!.wild.heldItems, loaded.battle!!.wild.heldItems)
        assertEquals(800, loaded.main!!.bondPoints)
    }

    @Test fun malformedDuplicateSecondSlotCannotCreateTwoCopies() {
        val s = state(); s.main!!.apply { heldItem = "everstone"; heldItem2 = "everstone" }
        val loaded = SaveCodec.decode(SaveCodec.encode(s))
        assertNull(loaded.main!!.heldItem2); assertEquals(1, loaded.ownedItemCount("everstone"))
    }

    @Test fun secondSlotCountsAsWornAndProtectsAgainstDuplicateRelease() {
        val s = state(); val m = s.main!!.apply { heldItem2 = "everstone" }
        assertEquals(listOf(m), s.itemHolders("everstone")); assertEquals(1, s.ownedItemCount("everstone"))
        s.team.clear()
        assertEquals(Duplicates.Keep.HOLDS_ITEM, Duplicates.keepReason(data, s, m))
    }

    @Test fun machoBraceDoublesPowerItemGainAndTwoPowerItemsBothApplyWithinCaps() {
        val m = state().main!!.apply { heldItem = "macho-brace"; heldItem2 = "power-lens" }
        Progression.addEvs(m, Stats(1, 0, 0, 0, 0, 0))
        assertEquals(Stats(2, 0, 0, 16, 0, 0), m.evs)
        m.evs = Stats.ZERO; m.heldItem = "power-weight"
        Progression.addEvs(m, Stats(1, 0, 0, 0, 0, 0))
        assertEquals(Stats(9, 0, 0, 8, 0, 0), m.evs)
        m.evs = Stats(252, 0, 0, 250, 0, 0)
        Progression.addEvs(m, Stats(10, 10, 10, 10, 10, 10))
        assertEquals(510, m.evs.toList().sum()); assertTrue(m.evs.toList().all { it <= 252 })
    }

    @Test fun bothSlotsAreSuppressedForFightersAndRemainActiveOnBench() {
        val s = state(); val m = s.main!!.apply { heldItem = "lucky-egg"; heldItem2 = "everstone" }
        val b = battle(s)
        assertEquals(m.heldItems, s.activeHeldItems(m))
        b.player.volatiles["embargo"] = 2
        assertTrue(s.activeHeldItems(m).isEmpty()); assertEquals(2, m.heldItems.size)
        b.player.volatiles.clear(); b.field.rooms["magicroom"] = 2
        assertTrue(s.activeHeldItems(m).isEmpty())
        val bench = TestData.mon(data, 25, 20).apply { heldItem = "lucky-egg"; heldItem2 = "everstone" }
        assertEquals(bench.heldItems, s.activeHeldItems(bench))
    }

    @Test fun moneyItemInEitherSlotDoublesButTwoMoneyItemsDoNotQuadruple() {
        fun money(first: String?, second: String?): Int {
            val s = state(); s.main!!.apply { heldItem = first; heldItem2 = second }
            Economy.onWin(s, mutableListOf(), bondLevel = 0, rng = Random(7))
            return s.cash
        }
        val base = money(null, null)
        assertEquals(base * 2, money("everstone", "amulet-coin"))
        assertEquals(base * 2, money("luck-incense", "amulet-coin"))
    }

    @Test fun luckyEggAndEverstoneMultiplyForMainAndExpShareHolderInEitherOrder() {
        for (share in listOf(false, true)) for (reverse in listOf(false, true)) {
            val s = state(); val main = s.main!!
            val recipient = if (share) TestData.mon(data, 54, 50, uid = "bench").also { s.mons += it; s.settings.xpShareUid = it.uid } else main
            recipient.apply { bondPoints = 800; heldItem = if (reverse) "everstone" else "lucky-egg"; heldItem2 = if (reverse) "lucky-egg" else "everstone" }
            val wild = TestData.mon(data, 19, 20)
            val base = Progression.xpForDefeat(data.sp(19), 20)
            val split = if (share) (base * Tuning.XP_SHARE_PART).toInt() else base
            val expected = Bond.bonusXp(((split * Tuning.LUCKY_EGG).toInt().toLong() * 115 / 100).toInt(), 800)
            Progression.rewardCatch(data, s, main, wild, 1L, Random(7), mutableListOf())
            assertEquals(expected, recipient.xp)
        }
    }

    @Test fun sootheBellInSecondSlotStillGrowsBondAfterALowerBondIsRestored() {
        val s = state(); val m = s.main!!.apply { bondPoints = 100; heldItem = "lucky-egg"; heldItem2 = "soothe-bell" }
        Progression.rewardDefeat(data, s, m, TestData.mon(data, 19, 20), 1L, Random(7), mutableListOf())
        assertEquals(104, m.bondPoints); assertEquals(1, m.bondHalf)
    }

    @Test fun evolutionItemsAndEverstoneRemainCompatibleWithTwoHeldItems() {
        val s = TestData.state(data, 25, 50); val m = s.main!!.apply { bondPoints = 800; heldItem = "lucky-egg"; heldItem2 = "everstone" }
        s.bag["thunder-stone"] = 1
        assertTrue(ItemUse.canUse(data, s, "thunder-stone", m))
        assertTrue(ItemUse.use(data, s, "thunder-stone", m.uid, Random(7), 1L).ok)
        assertEquals(26, m.species); assertEquals(listOf("lucky-egg", "everstone"), m.heldItems)
    }

    @Test fun berriesInBothSlotsActivateInOrderAndOnlyConsumedCopiesAreRemoved() {
        val s = state(); val m = s.main!!.apply { heldItem = "liechi-berry"; heldItem2 = "ganlon-berry"; hp = 1 }
        val r = round(s); AbilityAdaptations.eat(r, true)
        assertTrue(m.heldItems.isEmpty()); assertEquals(1, r.side(true).boosts[0]); assertEquals(1, r.side(true).boosts[1])
        assertEquals(listOf("liechi-berry", "ganlon-berry"), r.events.filterIsInstance<GameEvent.BerryActivated>().map { it.item })
        assertEquals("ganlon-berry", r.side(true).notes["consumeditem"])
    }

    @Test fun secondBerryRechecksThresholdAfterFirstBerryHeals() {
        val s = state(); val m = s.main!!.apply { heldItem = "oran-berry"; heldItem2 = "sitrus-berry" }
        m.hp = StatCalc.maxHp(data, m) / 2
        AbilityAdaptations.eat(round(s), true)
        assertEquals(listOf("sitrus-berry"), m.heldItems)
    }

    @Test fun berryInSecondSlotHonorsAllSuppressionRules() {
        for (block in listOf("klutz", "unnerve", "embargo", "magicroom")) {
            val s = state(); val m = s.main!!.apply { heldItem = "everstone"; heldItem2 = "sitrus-berry"; hp = 1 }
            val r = round(s)
            when (block) {
                "klutz" -> m.ability = "Klutz"
                "unnerve" -> r.wild.ability = "Unnerve"
                "embargo" -> r.side(true).volatiles[block] = 2
                else -> r.field.rooms[block] = 2
            }
            AbilityAdaptations.eat(r, true)
            assertEquals(2, m.heldItems.size)
        }
    }

    @Test fun bestowFindsTheFirstCompatibleItemAndRecycleUsesAFreeUnlockedSlot() {
        val s = state(); val m = s.main!!.apply { heldItem = "everstone"; heldItem2 = "lucky-egg"; bondPoints = 800 }
        val r = round(s); r.wild.apply { heldItem = "everstone"; bondPoints = 800 }
        assertEquals(true, RemainingMoves.statusMove(r, true, move("bestow")))
        assertEquals(listOf("everstone"), m.heldItems)
        assertEquals(listOf("everstone", "lucky-egg"), r.wild.heldItems)
        r.side(true).notes["consumeditem"] = "sitrus-berry"
        assertEquals(true, RemainingMoves.statusMove(r, true, move("recycle")))
        assertEquals(listOf("everstone", "sitrus-berry"), m.heldItems)
        assertEquals(false, RemainingMoves.statusMove(r, true, move("recycle")))
    }

    @Test fun trickSwapsFirstItemsAndRejectsDuplicatesWithoutChangingEitherSide() {
        val s = state(); val m = s.main!!.apply { heldItem = "everstone"; heldItem2 = "lucky-egg"; bondPoints = 800 }
        val r = round(s); r.wild.heldItem = "soothe-bell"
        assertEquals(true, RemainingMoves.statusMove(r, true, move("trick")))
        assertEquals(listOf("soothe-bell", "lucky-egg"), m.heldItems)
        assertEquals(listOf("everstone"), r.wild.heldItems)
        r.wild.heldItem = "lucky-egg"
        assertEquals(false, RemainingMoves.useful(r, true, move("trick")))
        assertEquals(false, RemainingMoves.statusMove(r, true, move("trick")))
        assertEquals(listOf("soothe-bell", "lucky-egg"), m.heldItems)
        assertEquals(listOf("lucky-egg"), r.wild.heldItems)
    }

    @Test fun corrosiveGasAndFlingRemoveOneItemAndTheOtherRemainsUsable() {
        val s = state(); val m = s.main!!.apply { heldItem = "macho-brace"; heldItem2 = "lucky-egg"; bondPoints = 800 }
        val r = round(s)
        RemainingMoves.beforeHit(r, true, move("fling")); RemainingMoves.afterHit(r, true, move("fling"), 10)
        assertEquals(listOf("lucky-egg"), m.heldItems); assertEquals("macho-brace", r.side(true).notes["consumeditem"])
        assertEquals(true, RemainingMoves.statusMove(r, false, move("corrosivegas")))
        assertTrue(m.heldItems.isEmpty())
    }

    @Test fun magicianCanStealIntoSecondSlotAndStickyHoldProtectsEitherSlot() {
        val s = state(); val m = s.main!!.apply { heldItem = "everstone"; bondPoints = 800; ability = "Magician" }
        val r = round(s); r.wild.apply { heldItem = "everstone"; heldItem2 = "lucky-egg"; bondPoints = 800 }
        r.wild.ability = "Sticky Hold"
        AbilityAdaptations.afterHit(r, true, data.move("tackle")!!, 1)
        assertEquals(listOf("everstone"), m.heldItems)
        r.wild.ability = ""
        AbilityAdaptations.afterHit(r, true, data.move("tackle")!!, 1)
        assertEquals(listOf("everstone", "lucky-egg"), m.heldItems)
        assertEquals(listOf("everstone"), r.wild.heldItems)
    }

    @Test fun unburdenWaitsUntilBothItemsAreGoneAndFriskRevealsBoth() {
        val s = state(); val m = s.main!!.apply { heldItem = "everstone"; heldItem2 = "lucky-egg"; ability = "Unburden" }
        val r = round(s); AbilityAdaptations.onEnter(r, true)
        m.removeHeld("everstone"); AbilityAdaptations.afterMove(r, true, move("splash"), false)
        assertNull(r.conds(true)["ab.unburden.${m.uid}"])
        m.removeHeld("lucky-egg"); AbilityAdaptations.afterMove(r, true, move("splash"), false)
        assertEquals(1, r.conds(true)["ab.unburden.${m.uid}"])
        m.ability = "Frisk"; r.wild.apply { heldItem = "everstone"; heldItem2 = "lucky-egg" }
        AbilityAdaptations.enter(r, true, "frisk")
        assertTrue(r.events.filterIsInstance<GameEvent.Effect>().any { it.text.contains("Everstone") && it.text.contains("Lucky Egg") })
    }

    @Test fun saveExportSnapshotRetainsBothItemsEvenIfLiveStateChanges() {
        val s = state(); s.main!!.apply { heldItem = "lucky-egg"; heldItem2 = "everstone"; bondPoints = 800 }
        val export = SaveCodec.snapshot(s)
        s.main!!.removeHeld("everstone")
        assertEquals(listOf("lucky-egg", "everstone"), SaveCodec.decode(export()).main!!.heldItems)
    }

    @Test fun releasingABoxedPokemonReturnsBothItems() {
        val s = state()
        val boxed = TestData.mon(data, 25, 20, uid = "boxed").apply { heldItem = "lucky-egg"; heldItem2 = "everstone"; bondPoints = 800 }
        s.mons += boxed
        assertTrue(Game(data, s, Random(7)) { 1L }.release(boxed.uid).ok)
        assertEquals(1, s.count("lucky-egg")); assertEquals(1, s.count("everstone")); assertNull(s.mon(boxed.uid))
    }

    @Test fun holdingLinesFiltersAndTagsIncludeBothAndExpShareUsesNoSlot() {
        val s = state(); val m = s.main!!.apply { heldItem = "lucky-egg"; heldItem2 = "everstone"; bondPoints = 800 }
        s.settings.xpShareUid = m.uid
        val lines = com.husarp.ankigo.ui.HoldingPlan.lines(data, m, m.uid, m.uid)
        assertEquals(listOf("Lucky Egg", "Everstone", "Exp. Share"), lines.map { it.name })
        assertEquals(m.heldItems, com.husarp.ankigo.ui.MonTags.of(data, s, m).filter { it.kind == com.husarp.ankigo.ui.TagKind.HELD }.map { it.item })
        s.bag["lucky-egg"] = 1; s.bag["everstone"] = 1; s.bag["soothe-bell"] = 1
        assertEquals(listOf("soothe-bell"), com.husarp.ankigo.ui.HoldingPlan.giveable(s, m))
        m.heldItem = null
        assertEquals(listOf(m), com.husarp.ankigo.ui.BoxTiles.filter(s.mons, com.husarp.ankigo.ui.BoxFilter.HOLDS_ITEM))
    }

    @Test fun knockOffPoltergeistAndAcrobaticsRecognizeAnItemInSecondSlot() {
        val s = state(); val r = round(s); val m = s.main!!
        r.wild.heldItem2 = "everstone"
        fun hit(id: String): Hit = Hit(r, true, m, r.side(true), r.wild, r.side(false), Move(id, id, Type.NORMAL, Category.PHYSICAL, 40, null))
        assertEquals(1.5, MovePower.powerMod(hit("knockoff")), 0.0)
        assertNull(MovePower.useful(r, true, hit("poltergeist").move))
        r.wild.heldItem2 = null
        assertEquals(false, MovePower.useful(r, true, hit("poltergeist").move))
        assertEquals(2.0, MovePower.powerMod(hit("acrobatics")), 0.0)
        m.heldItem2 = "lucky-egg"
        assertEquals(1.0, MovePower.powerMod(hit("acrobatics")), 0.0)
    }


    @Test fun recycleAndTheftCannotRefillALockedSecondSlotAfterBondDrops() {
        val s = state(); val m = s.main!!.apply { heldItem = "everstone"; bondPoints = 799; ability = "Magician" }
        val r = round(s); r.wild.heldItem = "lucky-egg"
        r.side(true).notes["consumeditem"] = "sitrus-berry"
        assertEquals(false, RemainingMoves.statusMove(r, true, move("recycle")))
        assertEquals("sitrus-berry", r.side(true).notes["consumeditem"])
        AbilityAdaptations.afterHit(r, true, data.move("tackle")!!, 1)
        assertEquals(listOf("everstone"), m.heldItems); assertEquals(listOf("lucky-egg"), r.wild.heldItems)
    }

}
