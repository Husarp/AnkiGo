package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R36MergeTest {
    private val data = TestData.world()
    private fun state() = TestData.state(data, 25, 30, listOf("tackle")).apply {
        day = "2026-11-15"; cash = 1_000_000
        battle = Battle(TestData.mon(data, 19, 30, listOf("tackle"), "wild"), Tier.NORMAL)
    }
    private fun round(s: GameState) = BattleEngine.Round(data, s.battle!!, s.main!!, 1.0, 1.0, Random(4))
    private fun hit(r: Round, p: Boolean = true, move: String = "tackle") =
        Hit(r, p, r.mon(p), r.side(p), r.mon(!p), r.side(!p), data.moves.getValue(move))

    @Test fun newHeldEffectsWorkTogetherInEitherSlotAndConsumeOnlyTheSash() {
        for (reverse in listOf(false, true)) {
            val s = state(); val m = s.main!!; m.bondPoints = 1000; m.hp = 10
            m.heldItem = if (reverse) "focus-sash" else "leftovers"
            m.heldItem2 = if (reverse) "leftovers" else "focus-sash"
            val r = round(s)
            assertEquals(9, BattleItems.takeDamage(r, true, hit(r, false), 100))
            assertEquals(listOf("leftovers"), m.heldItems)
            BattleItems.endOfRound(r)
            assertEquals(10 + StatCalc.maxHp(data, m) / 16, m.hp)
            assertEquals(listOf("leftovers"), SaveCodec.decode(SaveCodec.encode(s)).main!!.heldItems)
        }
    }
    @Test fun damageBoostsCombineAndSecondSlotHealingAndSuppressionWork() {
        val s = state(); val m = s.main!!; m.heldItem = "life-orb"; m.heldItem2 = "expert-belt"
        val r = round(s)
        assertEquals(1.3 * 1.2, BattleItems.finalMod(hit(r, move = "seismictoss")), 0.0)
        m.heldItem2 = "shell-bell"; m.hp = 10
        BattleItems.afterHit(r, true, data.moves.getValue("tackle"), 40)
        assertEquals(15, m.hp)
        r.side(true).volatiles["embargo"] = 2
        BattleItems.afterHit(r, true, data.moves.getValue("tackle"), 40)
        assertEquals(15, m.hp)
        assertEquals(1.0, BattleItems.finalMod(hit(r)), 0.0)
    }
    @Test fun consumingTmDoesNotRefillOfferButRefreshAndNewDayDo() {
        var s = state(); Shop.ensureToday(data, s); s.shopTms += "thunderbolt"
        assertTrue(Shop.buyTm(data, s, "thunderbolt").ok)
        s.tms -= "thunderbolt"
        s = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(0, Shop.tmRemaining(s, "thunderbolt"))
        val cash = s.cash
        assertFalse(Shop.buyTm(data, s, "thunderbolt").ok)
        assertEquals(cash, s.cash)
        assertTrue(Shop.reroll(data, s, Random(6)).ok); s.shopTms += "thunderbolt"
        assertEquals(1, Shop.tmRemaining(s, "thunderbolt"))
        assertTrue(Shop.buyTm(data, s, "thunderbolt").ok); s.tms -= "thunderbolt"
        s.day = "2026-11-16"; Shop.ensureToday(data, s); s.shopTms += "thunderbolt"
        assertTrue(Shop.buyTm(data, s, "thunderbolt").ok)
    }
    @Test fun repairCannotIntroduceBerriesOrIneligibleRareSnacks() {
        repeat(120) { n ->
            val s = state(); s.day = "day$n"; Shop.ensureToday(data, s)
            val deal = Shop.dealFor(data, s.day)!!.item
            s.shopItems += listOf(deal, deal, "oran-berry", "old-gateau", "rage-candy-bar")
            Shop.ensureToday(data, s)
            assertFalse("oran-berry" in s.shopItems)
            assertEquals(s.shopItems.size, s.shopItems.distinct().size)
            assertFalse(deal in s.shopItems)
            for (item in listOf("old-gateau", "rage-candy-bar")) {
                val chance = if (item == "old-gateau") 2 else 10
                if (Random(("rare:${s.day}:$item").hashCode()).nextInt(100) >= chance)
                    assertFalse(item in s.shopItems)
            }
        }
    }
    @Test fun riskySnackCannotSettleADormantEncounter() {
        val s = state(); s.battle!!.awaitingContinue = true; s.main!!.hp = 1
        Economy.give(s, "lava-cookie")
        val b = s.battle!!
        val seed = (0..100).first { !Random(it).nextBoolean() }
        val result = Game(data, s, Random(seed)) { 0 }.useItem("lava-cookie", s.main!!.uid)
        assertTrue(result.ok)
        assertEquals(0, s.main!!.hp)
        assertSame(b, s.battle)
        assertTrue(b.awaitingContinue)
        assertTrue(result.events.none { it is GameEvent.Encounter || it is GameEvent.MainHealed || it is GameEvent.SwitchHit })
    }

}
