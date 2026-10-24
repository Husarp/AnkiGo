package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C5.attacks
import com.husarp.ankigo.game.R17C5.effects
import com.husarp.ankigo.game.R17C5.kinds
import com.husarp.ankigo.game.R17C5.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** R17C-5: the Protect family (Protect ... Mat Block), the success chance of using one again, and the moves that break through. */
class R17C5ProtectTest {
    private val data = R17C5.data
    private fun main(vararg m: String) = R17C5.main(*m)
    private fun wild(vararg m: String, sp: Int = 301) = R17C5.wild(*m, sp = sp)
    private fun battle(w: Mon) = R17C5.battle(w)

    /** One round; the Main plays [mainIds] (one per round), the wild one [wildIds]. */
    private fun round(m: Mon, b: Battle, mainIds: List<String>, wildIds: List<String>, rng: Random = Random(1), wildPower: Double = 1.0) =
        R17C5.playing(mainIds, wildIds) { R17C5.round(m, b, rng, wildPower = wildPower) }

    private fun mainBlocked(e: List<GameEvent>) = e.kinds(EffectKind.BLOCKED).filter { !it.onPlayer }
    private fun mainAttack(e: List<GameEvent>) = e.attacks().first { it.byPlayer }

    // ---------------------------------------------------------------- Protect, Detect

    @Test fun protectBlocksTheAttackAndSaysSo() {
        val m = main("tackle"); val w = wild("protect"); val b = battle(w)
        val hp = w.hp
        val e = round(m, b, listOf("tackle"), listOf("protect"))
        assertEquals(hp, w.hp)
        val block = mainBlocked(e).single()
        assertEquals("Slowpoke protected itself!", block.text)
        assertEquals("Protected", block.label); assertEquals("protect", block.stat)
        assertTrue(mainAttack(e).failed)
        assertEquals(0, mainAttack(e).damage)
    }

    @Test fun detectWorksTheSameWay() {
        val m = main("tackle"); val w = wild("detect"); val b = battle(w)
        val hp = w.hp
        val e = round(m, b, listOf("tackle"), listOf("detect"))
        assertEquals(hp, w.hp)
        assertEquals(listOf("Slowpoke protected itself!"), mainBlocked(e).map { it.text })
    }

    @Test fun theProtectionGoesFirstBecauseOfItsPriority() {
        val m = main("tackle"); val w = wild("protect"); val b = battle(w)
        val e = round(m, b, listOf("tackle"), listOf("protect"))
        assertEquals("the wild one's Protect came before the Main's (faster) attack", false, e.attacks().first().byPlayer)
    }

    @Test fun aMoveWithoutTheProtectFlagGoesThrough() {
        val m = main("unblockable"); val w = wild("protect"); val b = battle(w)
        val hp = w.hp
        val e = round(m, b, listOf("unblockable"), listOf("protect"))
        assertTrue(w.hp < hp)
        assertTrue(mainBlocked(e).isEmpty())
    }

    @Test fun theProtectionLastsOneRoundOnly() {
        val m = main("tackle"); val w = wild("protect"); val b = battle(w)
        round(m, b, listOf("tackle"), listOf("protect", "splash"))
        assertNull(SideMoves.protecting(SimpleView.of(data, b, m), false))
        val hp = w.hp
        round(m, b, listOf("tackle"), listOf("protect", "splash"))
        assertTrue("the second round's hit lands", w.hp < hp)
    }

    @Test fun theMainProtectsTooAndTheWildOnesAttackIsBlocked() {
        val m = main("protect"); val w = wild("tackle"); val b = battle(w)
        val hp = m.hp
        val e = round(m, b, listOf("protect"), listOf("tackle"))
        assertEquals(hp, m.hp)
        val block = e.kinds(EffectKind.BLOCKED).single()
        assertEquals("Fastie protected itself!", block.text); assertTrue(block.onPlayer)
    }

    @Test fun aProtectedStatusMoveIsStoppedToo() {
        val m = main("thunderwave"); val w = wild("protect"); val b = battle(w)
        round(m, b, listOf("thunderwave"), listOf("protect"))
        assertNull(b.enemy.status)
    }

    // ---------------------------------------------------------------- King's Shield, Spiky Shield, Baneful Bunker, Burning Bulwark, Silk Trap, Obstruct

    @Test fun kingsShieldBlocksDamageAndLowersTheAttackOfAContactAttacker() {
        val m = main("tackle"); val w = wild("kingsshield"); val b = battle(w)
        val hp = w.hp
        val e = round(m, b, listOf("tackle"), listOf("kingsshield"))
        assertEquals(hp, w.hp)
        assertEquals(-1, b.player.boosts[0])
        assertTrue(e.lines().contains("Fastie's Attack fell!"))
    }

    @Test fun kingsShieldLetsStatusMovesThroughAndDoesNotPunishAFarAttack() {
        val m = main("thunderwave"); val w = wild("kingsshield"); val b = battle(w)
        round(m, b, listOf("thunderwave"), listOf("kingsshield"))
        assertEquals("par", b.enemy.status)
        val m2 = main("watergun"); val w2 = wild("kingsshield"); val b2 = battle(w2)
        val hp = w2.hp
        val e = round(m2, b2, listOf("watergun"), listOf("kingsshield"))
        assertEquals(hp, w2.hp); assertEquals(0, b2.player.boosts[0]); assertEquals(1, mainBlocked(e).size)
    }

    @Test fun spikyShieldHurtsAContactAttackerByAnEighth() {
        val m = main("tackle"); val w = wild("spikyshield"); val b = battle(w)
        val max = R17C5.maxHp(m)
        val e = round(m, b, listOf("tackle"), listOf("spikyshield"))
        assertEquals(max - max / 8, m.hp)
        assertTrue(e.lines().contains("Fastie was hurt!"))
        assertEquals(EffectKind.RECOIL, e.effects().first { it.text == "Fastie was hurt!" }.kind)
    }

    @Test fun spikyShieldBlocksStatusMovesAndSparesAFarAttacker() {
        val m = main("thunderwave"); val w = wild("spikyshield"); val b = battle(w)
        round(m, b, listOf("thunderwave"), listOf("spikyshield"))
        assertNull(b.enemy.status)
        val m2 = main("watergun"); val w2 = wild("spikyshield"); val b2 = battle(w2)
        round(m2, b2, listOf("watergun"), listOf("spikyshield"))
        assertEquals(R17C5.maxHp(m2), m2.hp)
    }

    @Test fun banefulBunkerPoisonsAndBurningBulwarkBurnsAContactAttacker() {
        val m = main("tackle"); val b = battle(wild("banefulbunker"))
        round(m, b, listOf("tackle"), listOf("banefulbunker"))
        assertEquals("psn", b.player.status)
        val m2 = main("tackle"); val b2 = battle(wild("burningbulwark"))
        round(m2, b2, listOf("tackle"), listOf("burningbulwark"))
        assertEquals("brn", b2.player.status)
        val m3 = main("watergun"); val b3 = battle(wild("banefulbunker"))
        round(m3, b3, listOf("watergun"), listOf("banefulbunker"))
        assertNull(b3.player.status)
    }

    @Test fun silkTrapAndObstructBlockDamageOnlyAndLowerAContactAttackersStats() {
        val m = main("tackle"); val b = battle(wild("silktrap"))
        round(m, b, listOf("tackle"), listOf("silktrap"))
        assertEquals(-1, b.player.boosts[4])
        val m2 = main("tackle"); val b2 = battle(wild("obstruct"))
        round(m2, b2, listOf("tackle"), listOf("obstruct"))
        assertEquals(-2, b2.player.boosts[1])
        val m3 = main("thunderwave"); val b3 = battle(wild("silktrap"))
        round(m3, b3, listOf("thunderwave"), listOf("silktrap"))
        assertEquals("par", b3.enemy.status)
    }

    // ---------------------------------------------------------------- Endure

    @Test fun endureLeavesOneHpAndSaysSo() {
        val m = main("tackle"); val w = wild("endure"); val b = battle(w)
        w.hp = 5
        val e = round(m, b, listOf("tackle"), listOf("endure"))
        assertEquals(1, w.hp)
        assertTrue(e.lines().contains("Slowpoke endured the hit!"))
        assertFalse(e.any { it is GameEvent.Fainted })
    }

    @Test fun endureIsOverAfterTheRound() {
        val m = main("tackle"); val w = wild("endure"); val b = battle(w)
        w.hp = 5
        round(m, b, listOf("tackle"), listOf("endure", "splash"))
        val e = round(m, b, listOf("tackle"), listOf("endure", "splash"))
        assertEquals(0, w.hp)
        assertTrue(e.any { it is GameEvent.Fainted })
    }

    // ---------------------------------------------------------------- Quick Guard, Wide Guard, Crafty Shield, Mat Block

    @Test fun quickGuardBlocksPriorityMovesOnly() {
        val m = main("quickattack"); val w = wild("quickguard"); val b = battle(w)
        val hp = w.hp
        val e = round(m, b, listOf("quickattack"), listOf("quickguard"))
        assertEquals(hp, w.hp)
        assertEquals("Quick Guard protected Slowpoke's team!", mainBlocked(e).single().text)
        val m2 = main("tackle"); val w2 = wild("quickguard"); val b2 = battle(w2)
        round(m2, b2, listOf("tackle"), listOf("quickguard"))
        assertTrue("a move with no priority gets through", w2.hp < R17C5.maxHp(w2))
    }

    @Test fun wideGuardBlocksMovesThatHitSeveralPokemon() {
        val m = main("earthquake"); val w = wild("wideguard"); val b = battle(w)
        val hp = w.hp
        val e = round(m, b, listOf("earthquake"), listOf("wideguard"))
        assertEquals(hp, w.hp)
        assertEquals("Wide Guard protected Slowpoke's team!", mainBlocked(e).single().text)
        val m2 = main("tackle"); val w2 = wild("wideguard"); val b2 = battle(w2)
        round(m2, b2, listOf("tackle"), listOf("wideguard"))
        assertTrue(w2.hp < R17C5.maxHp(w2))
    }

    @Test fun craftyShieldBlocksStatusMovesOnly() {
        val m = main("thunderwave"); val w = wild("craftyshield"); val b = battle(w)
        val e = round(m, b, listOf("thunderwave"), listOf("craftyshield"))
        assertNull(b.enemy.status)
        assertEquals("Crafty Shield protected Slowpoke's team!", mainBlocked(e).single().text)
        val m2 = main("tackle"); val w2 = wild("craftyshield"); val b2 = battle(w2)
        round(m2, b2, listOf("tackle"), listOf("craftyshield"))
        assertTrue(w2.hp < R17C5.maxHp(w2))
    }

    @Test fun matBlockBlocksDamageOnlyAndOnlyOnTheFirstAction() {
        val w = wild("matblock", sp = 305); val m = main("tackle"); val b = battle(w) // the wild one is faster: its Mat Block is up first
        val hp = w.hp
        val e = round(m, b, listOf("tackle"), listOf("matblock"))
        assertEquals(hp, w.hp)
        assertEquals(1, mainBlocked(e).size)
        val e2 = round(m, b, listOf("tackle"), listOf("matblock"))
        assertTrue("on its second action Mat Block fails", e2.lines().contains("But it failed!"))
        assertTrue(w.hp < hp)
    }

    @Test fun matBlockLetsStatusMovesThrough() {
        val w = wild("matblock", sp = 305); val m = main("thunderwave"); val b = battle(w)
        round(m, b, listOf("thunderwave"), listOf("matblock"))
        assertEquals("par", b.enemy.status)
    }

    // ---------------------------------------------------------------- using one again in a row

    @Test fun theSecondUseInARowCanFail() {
        val m = main("protect"); val w = wild("tackle"); val b = battle(w)
        val hp = m.hp
        round(m, b, listOf("protect"), listOf("tackle"), R17C5.stallFails)
        assertEquals("the first use always works", hp, m.hp)
        val e = round(m, b, listOf("protect"), listOf("tackle"), R17C5.stallFails)
        assertTrue(e.lines().contains("But it failed!"))
        assertTrue(m.hp < hp)
        assertTrue(mainBlocked(e).isEmpty() && e.kinds(EffectKind.BLOCKED).isEmpty())
    }

    @Test fun theChanceIsOneInThreeThenOneInNine() {
        val rolls = ArrayList<Int>()
        val spy = object : Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextInt(until: Int): Int { if (until in setOf(3, 9, 27)) rolls += until; return 0 }
        }
        val m = main("protect"); val b = battle(wild("tackle"))
        repeat(3) { round(m, b, listOf("protect"), listOf("tackle"), spy) }
        assertEquals(listOf(3, 9), rolls)
    }

    @Test fun aFailedUseStartsTheChainOver() {
        val m = main("protect"); val w = wild("tackle"); val b = battle(w)
        round(m, b, listOf("protect"), listOf("tackle"), R17C5.stallFails)
        round(m, b, listOf("protect"), listOf("tackle"), R17C5.stallFails) // fails
        val hp = m.hp
        round(m, b, listOf("protect"), listOf("tackle"), R17C5.stallFails) // the third works again without a roll
        assertEquals(hp, m.hp)
    }

    @Test fun aRoundWithAnotherMoveEndsTheChain() {
        val m = main("protect", "tackle"); val w = wild("tackle"); val b = battle(w)
        val ids = listOf("protect", "tackle", "protect")
        round(m, b, ids, listOf("tackle"), R17C5.stallFails)
        round(m, b, ids, listOf("tackle"), R17C5.stallFails)
        val hp = m.hp
        round(m, b, ids, listOf("tackle"), R17C5.stallFails)
        assertEquals("Protect works again after a round without one", hp, m.hp)
    }

    @Test fun endureAndProtectShareTheChain() {
        val m = main("protect"); val w = wild("tackle"); val b = battle(w)
        val ids = listOf("protect", "endure")
        round(m, b, ids, listOf("tackle"), R17C5.stallFails)
        val e = round(m, b, ids, listOf("tackle"), R17C5.stallFails)
        assertTrue(e.lines().contains("But it failed!"))
    }

    // ---------------------------------------------------------------- moves that break through

    @Test fun feintHitsThroughProtectAndLiftsIt() {
        val m = main("feint"); val w = wild("protect"); val b = battle(w)
        val hp = w.hp
        val e = round(m, b, listOf("feint"), listOf("protect"))
        assertTrue(w.hp < hp)
        assertTrue(e.lines().contains("Slowpoke fell for the feint!"))
        assertNull(SideMoves.protecting(SimpleView.of(data, b, m), false))
        assertTrue(e.kinds(EffectKind.BLOCKED).isEmpty())
    }

    @Test fun otherBreakersSayTheyBrokeThrough() {
        val m = main("hyperspacehole"); val w = wild("kingsshield"); val b = battle(w)
        val hp = w.hp
        val e = round(m, b, listOf("hyperspacehole"), listOf("kingsshield"))
        assertTrue(w.hp < hp)
        assertTrue(e.lines().contains("Fastie broke through Slowpoke's protection!"))
        assertEquals("no contact effect without a block", 0, b.player.boosts[0])
    }

    @Test fun feintBreaksAQuickGuardToo() {
        val m = main("feint"); val w = wild("quickguard"); val b = battle(w)
        val hp = w.hp
        round(m, b, listOf("feint"), listOf("quickguard"))
        assertTrue(w.hp < hp)
    }

    // ---------------------------------------------------------------- the move choice and the text

    @Test fun smartChoiceDoesNotProtectTwiceInARow() {
        val m = main("protect", "tackle"); val b = battle(wild("tackle"))
        val protect = data.move("protect")!!
        assertEquals(true, SideMoves.useful(SimpleView.of(data, b, m), true, protect))
        round(m, b, listOf("protect"), listOf("tackle"))
        assertEquals(false, SideMoves.useful(SimpleView.of(data, b, m), true, protect))
        round(m, b, listOf("tackle"), listOf("tackle"))
        assertEquals(true, SideMoves.useful(SimpleView.of(data, b, m), true, protect))
    }

    @Test fun theGuardsAreOnlyUsefulAgainstAFoeWithAMoveTheyStop() {
        val m = main("tackle")
        fun useful(id: String, vararg foeMoves: String) = SideMoves.useful(SimpleView.of(data, battle(wild(*foeMoves)), m), true, data.move(id)!!)
        assertEquals(false, useful("quickguard", "tackle")); assertEquals(true, useful("quickguard", "tackle", "quickattack"))
        assertEquals(false, useful("wideguard", "tackle")); assertEquals(true, useful("wideguard", "tackle", "earthquake"))
        assertEquals(false, useful("craftyshield", "tackle")); assertEquals(true, useful("craftyshield", "tackle", "thunderwave"))
        assertEquals(true, useful("protect", "tackle")); assertEquals(true, useful("endure", "tackle"))
    }

    @Test fun matBlockIsOnlyUsefulOnTheFirstAction() {
        val m = main("tackle"); val b = battle(wild("tackle"))
        val mat = data.move("matblock")!!
        assertEquals(true, SideMoves.useful(SimpleView.of(data, b, m), true, mat))
        b.player.actions = 1
        assertEquals(false, SideMoves.useful(SimpleView.of(data, b, m), true, mat))
    }

    @Test fun theTextsSayWhatTheCodeDoes() {
        fun say(id: String) = MoveText.describe(data.move(id)!!)
        val protect = say("protect")
        assertTrue(protect, protect.startsWith("Blocks every move aimed at you for the rest of the turn."))
        assertTrue(protect, "only helps in a turn in which the other Pokémon also attacks" in protect)
        assertTrue(protect, "1 time in 3" in protect && "then 1 in 9" in protect)
        assertTrue(say("kingsshield"), "Attack lowered by 1" in say("kingsshield"))
        assertTrue(say("spikyshield"), "an eighth of its max HP" in say("spikyshield"))
        assertTrue(say("obstruct"), "Defense lowered by 2" in say("obstruct"))
        assertTrue(say("feint"), "Goes through Protect and similar moves, and ends them." in say("feint"))
        for (id in SideMoves.handles) assertTrue("$id says something", say(id).let { it.endsWith(".") && it != "Has no effect in AnkiGo battles." })
    }

    @Test fun protectingNamesTheProtectionThatIsUp() {
        val m = main("tackle"); val w = wild("protect"); val b = battle(w)
        val view = SimpleView.of(data, b, m)
        assertNull(SideMoves.protecting(view, false))
        b.enemy.volatiles["kingsshield"] = 1
        assertEquals("kingsshield", SideMoves.protecting(view, false))
        assertNotNull(SideMoves.handles.firstOrNull { it == "matblock" })
    }
}
