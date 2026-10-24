package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C5.attacks
import com.husarp.ankigo.game.R17C5.effects
import com.husarp.ankigo.game.R17C5.kinds
import com.husarp.ankigo.game.R17C5.lines
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** R17C-5: Leech Seed, Curse, Nightmare, Yawn, Destiny Bond, Aqua Ring and Ingrain, and Rapid Spin freeing the user. */
class R17C5LastingTest {
    private val data = R17C5.data
    private fun main(vararg m: String, sp: Int = 300) = R17C5.main(*m, sp = sp)
    private fun wild(vararg m: String, sp: Int = 301) = R17C5.wild(*m, sp = sp)
    private fun battle(w: Mon) = R17C5.battle(w)
    private fun round(m: Mon, b: Battle, mainIds: List<String>, wildIds: List<String>, rng: Random = Random(1), wildPower: Double = 1.0) =
        R17C5.playing(mainIds, wildIds) { R17C5.round(m, b, rng, wildPower = wildPower) }

    // ---------------------------------------------------------------- Leech Seed

    @Test fun leechSeedSapsAnEighthEachRoundAndTheOtherSideHealsThat() {
        val m = main("leechseed"); val w = wild("splash"); val b = battle(w)
        val wMax = R17C5.maxHp(w)
        m.hp = R17C5.maxHp(m) - 100
        val before = m.hp
        val e = round(m, b, listOf("leechseed"), listOf("splash"))
        val seeded = e.kinds(EffectKind.VOLATILE).first { it.stat == "leechseed" }
        assertEquals("Slowpoke was seeded!", seeded.text); assertEquals("Seeded", seeded.label)
        assertEquals(wMax - wMax / 8, w.hp)
        val sap = e.kinds(EffectKind.RESIDUAL).single()
        assertEquals("Slowpoke's health is sapped by Leech Seed!", sap.text); assertEquals(wMax / 8, sap.amount)
        assertEquals(before + wMax / 8, m.hp)
        assertEquals("Fastie regained health!", e.kinds(EffectKind.HEAL).single().text)
        round(m, b, listOf("splash"), listOf("splash"))
        assertEquals(wMax - 2 * (wMax / 8), w.hp)
    }

    @Test fun leechSeedFailsOnGrassTypesAndOnSeededTargets() {
        val m = main("leechseed"); val b = battle(wild("splash", sp = 302))
        val e = round(m, b, listOf("leechseed"), listOf("splash"))
        assertTrue(e.lines().contains("It doesn't affect Gloom…"))
        assertTrue(e.attacks().first { it.byPlayer }.failed)
        assertFalse("leechseed" in b.enemy.volatiles)

        val m2 = main("leechseed"); val b2 = battle(wild("splash"))
        round(m2, b2, listOf("leechseed"), listOf("splash"))
        val e2 = round(m2, b2, listOf("leechseed"), listOf("splash"))
        assertTrue(e2.lines().contains("But it failed!"))
    }

    @Test fun theSeededOneSwitchingOutClearsIt() {
        val m = main("splash"); val b = battle(wild("leechseed"))
        round(m, b, listOf("splash"), listOf("leechseed"))
        assertTrue("leechseed" in b.player.volatiles)
        b.switchOutMain(data, m)
        assertTrue(b.player.volatiles.isEmpty())
    }

    @Test fun noHealWhenTheOtherSideIsFullOrHealBlocked() {
        val m = main("leechseed"); val b = battle(wild("splash"))
        val e = round(m, b, listOf("leechseed"), listOf("splash"))
        assertTrue("full HP: nothing to heal", e.kinds(EffectKind.HEAL).isEmpty())
        val m2 = main("leechseed"); val b2 = battle(wild("splash"))
        m2.hp = R17C5.maxHp(m2) - 100; b2.player.volatiles["healblock"] = 2
        val before = m2.hp
        round(m2, b2, listOf("leechseed"), listOf("splash"))
        assertEquals(before, m2.hp)
    }

    @Test fun rapidSpinFreesTheUserFromLeechSeedAndBinding() {
        val m = main("rapidspin"); val b = battle(wild("splash"))
        b.player.volatiles["leechseed"] = 1
        b.player.volatiles[CoreRules.BOUND] = 3; b.player.notes[CoreRules.BOUND] = "wrap"
        val hp = m.hp
        val e = round(m, b, listOf("rapidspin"), listOf("splash"))
        assertTrue(e.lines().contains("Fastie shed Leech Seed!"))
        assertTrue(e.lines().contains("Fastie got free of Wrap!"))
        assertTrue(b.player.volatiles.isEmpty() || b.player.volatiles.keys.none { it == "leechseed" || it == CoreRules.BOUND })
        assertEquals("nothing sapped or squeezed at the end of the round", hp, m.hp)
        assertEquals(1, b.player.boosts[4])
    }

    @Test fun mortalSpinFreesTheUserToo() {
        val m = main("mortalspin"); val b = battle(wild("splash"))
        b.player.volatiles["leechseed"] = 1
        round(m, b, listOf("mortalspin"), listOf("splash"))
        assertFalse("leechseed" in b.player.volatiles)
        assertEquals("psn", b.enemy.status)
    }

    @Test fun rapidSpinThatDoesNotHitFreesNobody() {
        val m = main("rapidspin"); val b = battle(wild("splash", sp = 303)) // a Ghost: Normal moves have no effect
        b.player.volatiles["leechseed"] = 1
        round(m, b, listOf("rapidspin"), listOf("splash"))
        assertTrue("leechseed" in b.player.volatiles)
    }

    // ---------------------------------------------------------------- Curse

    @Test fun cursedByANonGhostRaisesAttackAndDefenseAndLowersSpeed() {
        val m = main("curse"); val b = battle(wild("splash"))
        val e = round(m, b, listOf("curse"), listOf("splash"))
        assertEquals(1, b.player.boosts[0]); assertEquals(1, b.player.boosts[1]); assertEquals(-1, b.player.boosts[4])
        assertEquals(0, b.enemy.boosts[0])
        assertFalse(e.attacks().first { it.byPlayer }.failed)
        assertFalse("curse" in b.enemy.volatiles)
    }

    @Test fun aGhostPaysHalfItsHpAndTheTargetLosesAQuarterEachRound() {
        val m = main("curse", sp = 303); val w = wild("splash"); val b = battle(w)
        val max = R17C5.maxHp(m); val wMax = R17C5.maxHp(w)
        val e = round(m, b, listOf("curse"), listOf("splash"))
        assertEquals(max - max / 2, m.hp)
        assertEquals(0, b.player.boosts[0])
        val cursed = e.kinds(EffectKind.VOLATILE).first { it.stat == "curse" }
        assertEquals("Slowpoke was cursed!", cursed.text); assertEquals("Cursed", cursed.label)
        assertEquals(wMax - wMax / 4, w.hp)
        assertEquals("Slowpoke is afflicted by the curse!", e.kinds(EffectKind.RESIDUAL).single().text)
        round(m, b, listOf("splash"), listOf("splash"))
        assertEquals(wMax - 2 * (wMax / 4), w.hp)
    }

    @Test fun aSecondGhostCurseFailsWithoutCostingHp() {
        val m = main("curse", sp = 303); val b = battle(wild("splash"))
        round(m, b, listOf("curse"), listOf("splash"))
        val hp = m.hp
        val e = round(m, b, listOf("curse"), listOf("splash"))
        assertEquals(hp, m.hp)
        assertTrue(e.lines().contains("But it failed!"))
    }

    @Test fun theUsersEffectiveTypesDecideWhichCurseItGets() {
        val ghostly = object : BattleRules { override fun types(v: BattleView, p: Boolean, t: List<Type>): List<Type> = if (p) listOf(Type.GHOST) else t }
        val m = main("curse"); val w = wild("splash"); val b = battle(w)
        R17C5.withExtra(ghostly) { round(m, b, listOf("curse"), listOf("splash")) }
        assertTrue("curse" in b.enemy.volatiles)
        assertEquals(0, b.player.boosts[0])
    }

    @Test fun theGhostCurseGoesThroughASubstitute() {
        val m = main("curse", sp = 303); val b = battle(wild("splash"))
        b.enemy.volatiles[SideMoves.SUBSTITUTE] = 100
        round(m, b, listOf("curse"), listOf("splash"))
        assertTrue("curse" in b.enemy.volatiles)
    }

    // ---------------------------------------------------------------- Nightmare

    @Test fun nightmareNeedsASleepingTarget() {
        val m = main("nightmare"); val b = battle(wild("splash"))
        val e = round(m, b, listOf("nightmare"), listOf("splash"))
        assertTrue(e.lines().contains("But it failed!"))
        assertFalse("nightmare" in b.enemy.volatiles)
    }

    @Test fun nightmareHurtsAQuarterEachRoundWhileItSleeps() {
        val m = main("nightmare"); val w = wild("splash"); val b = battle(w)
        val wMax = R17C5.maxHp(w)
        b.enemy.status = "slp"
        val e = round(m, b, listOf("nightmare"), listOf("splash"), R17C5.high)
        assertEquals("Slowpoke began having a nightmare!", e.kinds(EffectKind.VOLATILE).first { it.stat == "nightmare" }.text)
        assertEquals("Nightmare", e.kinds(EffectKind.VOLATILE).first { it.stat == "nightmare" }.label)
        assertEquals(wMax - wMax / 4, w.hp)
        assertEquals("Slowpoke is locked in a nightmare!", e.kinds(EffectKind.RESIDUAL).single().text)
        round(m, b, listOf("splash"), listOf("splash"), R17C5.high)
        assertEquals(wMax - 2 * (wMax / 4), w.hp)
    }

    @Test fun nightmareEndsWhenTheSleeperWakes() {
        val m = main("nightmare"); val w = wild("splash"); val b = battle(w)
        b.enemy.status = "slp"
        round(m, b, listOf("nightmare"), listOf("splash"), R17C5.high)
        b.enemy.status = null
        val hp = w.hp
        val e = round(m, b, listOf("splash"), listOf("splash"), R17C5.high)
        assertEquals(hp, w.hp)
        assertFalse("nightmare" in b.enemy.volatiles)
        assertTrue(e.kinds(EffectKind.RESIDUAL).isEmpty())
    }

    // ---------------------------------------------------------------- Yawn

    @Test fun yawnPutsTheTargetToSleepAtTheEndOfTheNextRound() {
        val m = main("yawn"); val b = battle(wild("splash"))
        val e1 = round(m, b, listOf("yawn"), listOf("splash"))
        val drowsy = e1.kinds(EffectKind.VOLATILE).first { it.stat == "yawn" }
        assertEquals("Slowpoke grew drowsy!", drowsy.text); assertEquals("Drowsy", drowsy.label)
        assertNull("still awake after the round it was used in", b.enemy.status)
        val e2 = round(m, b, listOf("splash"), listOf("splash"))
        assertEquals("slp", b.enemy.status)
        assertTrue(e2.lines().contains("Slowpoke fell asleep!"))
        assertFalse("yawn" in b.enemy.volatiles)
    }

    @Test fun yawnFailsOnATargetWithAStatusOrAlreadyYawning() {
        val m = main("yawn"); val b = battle(wild("splash"))
        b.enemy.status = "par"
        val e = round(m, b, listOf("yawn"), listOf("splash"))
        assertTrue(e.lines().contains("But it failed!"))
        val m2 = main("yawn"); val b2 = battle(wild("splash"))
        round(m2, b2, listOf("yawn"), listOf("splash"))
        val e2 = round(m2, b2, listOf("yawn"), listOf("splash"))
        assertTrue("falls asleep in this round already, so the second use found a yawner or a sleeper", e2.lines().contains("But it failed!") || b2.enemy.status == "slp")
    }

    @Test fun aStatusInTheMeantimeCancelsTheSleep() {
        val m = main("yawn"); val b = battle(wild("splash"))
        round(m, b, listOf("yawn"), listOf("splash"))
        b.enemy.status = "brn"
        round(m, b, listOf("splash"), listOf("splash"))
        assertEquals("brn", b.enemy.status)
    }

    @Test fun anUproarKeepsTheYawnerAwake() {
        val m = main("yawn"); val b = battle(wild("splash"))
        b.enemy.volatiles["uproar"] = 2
        val e = round(m, b, listOf("yawn"), listOf("splash"))
        assertTrue(e.lines().any { it.startsWith("But the uproar kept") })
        assertFalse("yawn" in b.enemy.volatiles)
    }

    // ---------------------------------------------------------------- Destiny Bond

    @Test fun theAttackerFaintsWithTheBondedOne() {
        val w = wild("destinybond", sp = 305); val m = main("tackle"); val b = battle(w)
        w.hp = 1
        val e = round(m, b, listOf("tackle"), listOf("destinybond"))
        assertEquals(0, w.hp); assertEquals(0, m.hp)
        val bond = e.effects().first { it.text == "Zippy took its attacker down with it!" }
        assertEquals("Destiny Bond", bond.label)
        assertEquals(2, e.count { it is GameEvent.Fainted })
    }

    @Test fun noBondEffectWhenTheHitDoesNotFaintIt() {
        val w = wild("destinybond", sp = 305); val m = main("tackle"); val b = battle(w)
        round(m, b, listOf("tackle"), listOf("destinybond"))
        assertTrue(m.hp > 0)
    }

    @Test fun theBondEndsWhenItsUserActsAgain() {
        val w = wild("destinybond", sp = 305); val m = main("splash"); val b = battle(w)
        round(m, b, listOf("splash"), listOf("destinybond"))
        assertTrue("destinybond" in b.enemy.volatiles)
        w.hp = 1
        val e = R17C5.playing(listOf("tackle"), listOf("tackle")) { R17C5.round(m, b, Random(1), wildPower = 0.01) }
        assertEquals(0, w.hp)
        assertTrue("the Main lives: the bond ended before the wild one's second action", m.hp > 0)
        assertFalse(e.lines().contains("Zippy took its attacker down with it!"))
    }

    @Test fun usedTwiceInARowItFailsThenWorksAgain() {
        val w = wild("destinybond", sp = 305); val m = main("splash"); val b = battle(w)
        round(m, b, listOf("splash"), listOf("destinybond"))
        val e2 = round(m, b, listOf("splash"), listOf("destinybond"))
        assertTrue(e2.attacks().first { !it.byPlayer }.failed)
        assertTrue(e2.lines().contains("But it failed!"))
        assertFalse("destinybond" in b.enemy.volatiles)
        round(m, b, listOf("splash"), listOf("destinybond"))
        assertTrue("destinybond" in b.enemy.volatiles)
    }

    @Test fun theMainsBondTakesTheWildOneDownToo() {
        val m = main("destinybond"); val w = wild("tackle"); val b = battle(w)
        m.hp = 1
        val e = round(m, b, listOf("destinybond"), listOf("tackle"))
        assertEquals(0, m.hp); assertEquals(0, w.hp)
        assertTrue(e.lines().contains("Fastie took its attacker down with it!"))
    }

    // ---------------------------------------------------------------- Aqua Ring, Ingrain

    @Test fun aquaRingHealsASixteenthEachRound() {
        val m = main("aquaring"); val b = battle(wild("splash"))
        val max = R17C5.maxHp(m)
        m.hp = max - 100
        val e = round(m, b, listOf("aquaring"), listOf("splash"))
        assertEquals("Fastie surrounded itself with a veil of water!", e.kinds(EffectKind.VOLATILE).first { it.stat == "aquaring" }.text)
        assertEquals("Aqua Ring", e.kinds(EffectKind.VOLATILE).first { it.stat == "aquaring" }.label)
        assertEquals(max - 100 + max / 16, m.hp)
        assertEquals("Fastie's Aqua Ring restored its HP.", e.kinds(EffectKind.HEAL).single().text)
        round(m, b, listOf("splash"), listOf("splash"))
        assertEquals(max - 100 + 2 * (max / 16), m.hp)
    }

    @Test fun anAquaRingAtFullHpOrUnderHealBlockHealsNothing() {
        val m = main("aquaring"); val b = battle(wild("splash"))
        val e = round(m, b, listOf("aquaring"), listOf("splash"))
        assertTrue(e.kinds(EffectKind.HEAL).isEmpty())
        m.hp = 10; b.player.volatiles["healblock"] = 2
        round(m, b, listOf("splash"), listOf("splash"))
        assertEquals(10, m.hp)
    }

    @Test fun aSecondAquaRingFails() {
        val m = main("aquaring"); val b = battle(wild("splash"))
        round(m, b, listOf("aquaring"), listOf("splash"))
        assertTrue(round(m, b, listOf("aquaring"), listOf("splash")).lines().contains("But it failed!"))
    }

    @Test fun ingrainHealsAndKeepsTheUserOnTheGround() {
        val m = main("ingrain", sp = 304); val b = battle(wild("splash"))
        val max = R17C5.maxHp(m)
        assertFalse("a Flying type is in the air", SimpleView.of(data, b, m).grounded(true))
        m.hp = max - 100
        val e = round(m, b, listOf("ingrain"), listOf("splash"))
        assertEquals("Glider planted its roots!", e.kinds(EffectKind.VOLATILE).first { it.stat == "ingrain" }.text)
        assertEquals("Glider absorbed nutrients with its roots!", e.kinds(EffectKind.HEAL).single().text)
        assertEquals(max - 100 + max / 16, m.hp)
        assertTrue(SimpleView.of(data, b, m).grounded(true))
    }

    // ---------------------------------------------------------------- the save

    @Test fun theStateOfTheseMovesSurvivesASaveAndPlaysOn() {
        val m = main("splash"); val w = wild("splash"); val b = battle(w)
        m.hp = 50
        b.player.volatiles.putAll(mapOf("substitute" to 25, "leechseed" to 1, "stockpile" to 2, "sm.stockdef" to 2, "sm.stockspd" to 2, "destinybond" to 1, "sm.stall" to 1))
        b.enemy.volatiles.putAll(mapOf("curse" to 1, "yawn" to 1, "nightmare" to 1))
        b.field.player.putAll(mapOf("reflect" to 3, "tailwind" to 2, "wish" to 1, "sm.wishhp" to 40)); b.field.enemy["safeguard"] = 4
        val s = GameState(schema = GameState.SCHEMA)
        s.mons += m; s.team += m.uid; s.battle = b
        val back = SaveCodec.decode(SaveCodec.encode(s))
        val bb = back.battle!!
        assertEquals(b.player.volatiles, bb.player.volatiles); assertEquals(b.enemy.volatiles, bb.enemy.volatiles)
        assertEquals(b.field.player, bb.field.player); assertEquals(b.field.enemy, bb.field.enemy)
        // and the fight goes on from the loaded state: the wish comes true at the end of this round, the seed saps, the yawn puts the wild one to sleep
        val e = round(back.mons[0], bb, listOf("splash"), listOf("splash"))
        assertTrue(e.lines().contains("Fastie's wish came true!"))
        assertEquals("slp", bb.enemy.status)
        assertEquals(2, bb.field.player["reflect"]); assertEquals(3, bb.field.enemy["safeguard"])
    }

    // ---------------------------------------------------------------- the move choice and the texts

    @Test fun smartChoiceSkipsTheLastingMovesThatCannotWork() {
        val m = main("tackle"); val w = wild("tackle", sp = 302); val b = battle(w) // a Grass wild one
        val v = { SimpleView.of(data, b, m) }
        fun useful(id: String) = SideMoves.useful(v(), true, data.move(id)!!)
        assertEquals("no Leech Seed on a Grass type", false, useful("leechseed"))
        assertEquals("no Nightmare on an awake target", false, useful("nightmare"))
        assertEquals(true, useful("yawn"))
        b.enemy.status = "slp"
        assertEquals("no Yawn on a target with a status", false, useful("yawn"))
        assertEquals(true, useful("nightmare"))
        assertEquals(true, useful("destinybond"))
        b.player.volatiles["destinybond"] = 1
        assertEquals(false, useful("destinybond"))
        assertEquals(true, useful("aquaring")); b.player.volatiles["aquaring"] = 1; assertEquals(false, useful("aquaring"))
        val b2 = battle(wild("tackle"))
        assertEquals(true, SideMoves.useful(SimpleView.of(data, b2, m), true, data.move("leechseed")!!))
        b2.enemy.volatiles["leechseed"] = 1
        assertEquals(false, SideMoves.useful(SimpleView.of(data, b2, m), true, data.move("leechseed")!!))
    }

    @Test fun theTextsGiveTheNumbers() {
        fun say(id: String) = MoveText.describe(data.move(id)!!)
        assertTrue(say("leechseed"), "an eighth of its max HP" in say("leechseed") && "Grass" in say("leechseed"))
        assertTrue(say("curse"), "half its max HP" in say("curse") && "a quarter of its max HP" in say("curse"))
        assertTrue(say("nightmare"), "a quarter of its max HP" in say("nightmare"))
        assertTrue(say("yawn"), "end of the next turn" in say("yawn"))
        assertTrue(say("destinybond"), "twice in a row" in say("destinybond"))
        assertTrue(say("aquaring"), "a sixteenth" in say("aquaring"))
        assertTrue(say("rapidspin"), "Frees you from Leech Seed and from binding moves." in say("rapidspin"))
    }
}
