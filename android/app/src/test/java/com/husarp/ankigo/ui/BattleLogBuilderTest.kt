package com.husarp.ankigo.ui

import com.husarp.ankigo.game.CashKind
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.HealReason
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TICKET R2-07: events -> battles -> rounds (made-up species and moves only). */
class BattleLogBuilderTest {
    private var t = 1000L
    private fun builder(keep: Int = 5) = BattleLogBuilder(keep, { t++ })

    private fun encounter(name: String = "Wildling") = GameEvent.Encounter(1, name, 7, false, Tier.NORMAL, 30, 30, true, false)
    private fun counted(button: Int, catchUp: Boolean = false) = GameEvent.Counted(button, 5, 1, 1, catchUp)
    private fun hit(byPlayer: Boolean, user: String, target: String, move: String, dmg: Int, tHp: Int, uHp: Int = 40, eff: Double = 1.0, crit: Boolean = false) =
        GameEvent.Attack(byPlayer, user, target, move, Type.ELECTRIC, Category.SPECIAL, dmg, 1, eff, crit, false, false, tHp, 30, uHp, 40)

    /** Answers (logged first, like the app does), their events, and a round that kills the wild one. */
    private fun fight(b: BattleLogBuilder, name: String = "Wildling") {
        b.feed(encounter(name))
        b.answer("study", 11, 3, "Capital of Australia?")
        b.feed(listOf(counted(3), GameEvent.Cash(4, 104), GameEvent.Charge(5, 1, 2)))
        b.answer("live", 12, 1, null)
        b.feed(listOf(counted(1), GameEvent.RoundStart(0.8, 8, true),
            hit(true, "Sparkmouse", name, "Zap", 30, 0, eff = 2.0),
            GameEvent.Fainted(false, name),
            GameEvent.Defeated(1, name, 40), GameEvent.XpGained("u1", "Sparkmouse", 40, 8, 10, 100),
            GameEvent.LevelUp("u1", "Sparkmouse", 8, 30, 30), encounter("Next")))
    }

    @Test fun fightMoneyCountsAnswersAndDefeatButNotTheDailyBonus() {
        val b = builder()
        b.feed(encounter())
        b.answer("study", 11, 3, "Card")
        b.feed(listOf(counted(3), GameEvent.Cash(50, 50, CashKind.DAILY_BONUS), GameEvent.Cash(5, 55, CashKind.ANSWER),
            GameEvent.RoundStart(1.0, 10, false), hit(true, "Sparkmouse", "Wildling", "Zap", 30, 0),
            GameEvent.Defeated(1, "Wildling", 40), GameEvent.Cash(10, 65, CashKind.DEFEAT)))
        assertEquals(Outcome.DEFEATED, b.battles[0].outcome)
        assertEquals(15, b.battles[0].coins)
    }

    @Test fun theWildOnesScaledHitShowsItsPowerAndOnlyThatRound() {
        val b = builder()
        b.feed(encounter())
        b.feed(listOf(counted(2), GameEvent.RoundStart(1.0, 25, true, true, 0.35),
            hit(false, "Wildling", "Sparkmouse", "Bite", 3, 37, 30), hit(true, "Sparkmouse", "Wildling", "Zap", 10, 20)))
        b.feed(listOf(counted(1), GameEvent.RoundStart(0.0, 0, true, false, 1.0), hit(false, "Wildling", "Sparkmouse", "Bite", 9, 28, 30)))
        val rounds = b.battles[0].rounds
        assertTrue(rounds[0].lines[0].text, "×0.35 power" in rounds[0].lines[0].text)
        assertFalse("power" in rounds[0].lines[1].text) // the Main's line has no such part
        assertFalse("power" in rounds[1].lines[0].text) // full power: nothing to add
        assertEquals(0.0, rounds[1].multiplier!!, 0.0)
    }

    @Test fun answersAndMovesLandInTheirRound() {
        val b = builder()
        fight(b)
        val first = b.battles[0]
        assertEquals(Outcome.DEFEATED, first.outcome)
        assertEquals(1, first.rounds.size)
        val r = first.rounds[0]
        assertEquals(0.8, r.multiplier!!, 0.0001)
        assertEquals(listOf(3, 1), r.answers.map { it.button })
        assertEquals("Capital of Australia?", r.answers[0].card)
        assertEquals("card #12", r.answers[1].card)
        assertEquals(4, r.answers[0].coins)
        assertEquals(4, first.coins)
        assertEquals("Sparkmouse used Zap · 30 damage · super effective · Wildling 0/30 HP", r.lines[0].text)
        assertEquals(Tone.GOOD, r.lines[0].tone)
        assertEquals("Wildling fainted", r.lines[1].text)
        assertEquals(listOf("Wildling was defeated · 40 XP", "Sparkmouse +40 XP · Lv 8", "Sparkmouse grew to Lv 8"), first.result.map { it.text })
    }

    @Test fun aHealedEventIsAShortResultLine() {
        val b = builder()
        b.feed(encounter())
        b.feed(listOf(counted(3), GameEvent.RoundStart(1.0, 10, false), hit(true, "Sparkmouse", "Wildling", "Zap", 30, 0),
            GameEvent.Defeated(1, "Wildling", 40), GameEvent.Healed("u1", "Sparkmouse", 12, 30, 40, HealReason.DEFEAT)))
        assertEquals(listOf("Wildling was defeated · 40 XP", "Sparkmouse +12 HP"), b.battles[0].result.map { it.text })
    }

    @Test fun theNextEncounterOpensTheCurrentBattleAndNewestIsFirst() {
        val b = builder()
        fight(b, "One")
        fight(b, "Two") // starts with an Encounter("Two") although "Next" is open: that one ends silently
        val list = b.newestFirst()
        assertEquals(listOf("Next", "Two", "Next", "One"), list.map { it.wild })
        assertEquals(listOf(Outcome.OPEN, Outcome.DEFEATED, Outcome.ENDED, Outcome.DEFEATED), list.map { it.outcome })
    }

    @Test fun anIncompleteRoundIsShownAsCollecting() {
        val b = builder()
        b.feed(encounter())
        b.answer("live", 5, 4, null)
        b.feed(listOf(counted(4), GameEvent.Charge(5, 1, 2)))
        val round = b.battles.single().rounds.single()
        assertNull(round.multiplier)
        assertEquals(1, round.answers.size)
        assertEquals(Outcome.OPEN, b.battles.single().outcome)
        // the second answer completes the same round
        b.answer("live", 6, 3, null)
        b.feed(listOf(counted(3), GameEvent.RoundStart(1.0, 10, false), GameEvent.Splash("Wildling")))
        assertEquals(1, b.battles.single().rounds.size)
        assertEquals(2, b.battles.single().rounds[0].answers.size)
        assertEquals("Wildling did nothing (Splash)", b.battles.single().rounds[0].lines.single().text)
    }

    @Test fun nextAnswersStartANewRound() {
        val b = builder()
        b.feed(encounter())
        b.answer("live", 5, 3, null)
        b.feed(listOf(counted(3), GameEvent.RoundStart(1.0, 10, false)))
        b.answer("live", 6, 2, null)
        b.feed(listOf(counted(2), GameEvent.Charge(5, 1, 2)))
        assertEquals(2, b.battles.single().rounds.size)
        assertEquals(1, b.battles.single().rounds[0].answers.size)
        assertEquals(listOf(2), b.battles.single().rounds[1].answers.map { it.button })
    }

    @Test fun catchUpAnswersAreMarked() {
        val b = builder()
        b.feed(encounter())
        b.answer("catch-up", 9, 3, null)
        b.answer("catch-up", 10, 4, null)
        b.feed(listOf(counted(3, catchUp = true), GameEvent.Charge(5, 1, 2), counted(4, catchUp = true), GameEvent.RoundStart(1.1, 11, false)))
        val answers = b.battles.single().rounds.single().answers
        assertTrue(answers.all { it.catchUp })
        assertEquals(listOf("card #9", "card #10"), answers.map { it.card })
    }

    @Test fun anIgnoredRedoDoesNotShowUpAndDoesNotStealTheNextCard() {
        val b = builder()
        b.feed(encounter())
        b.answer("live", 5, 3, null)
        b.feed(GameEvent.Ignored("Answer repeated after an undo"))
        b.answer("live", 6, 3, null)
        b.feed(listOf(counted(3), GameEvent.Charge(5, 1, 2)))
        val answers = b.battles.single().rounds.single().answers
        assertEquals(listOf("card #6"), answers.map { it.card })
    }

    @Test fun aCountedAnswerTakesTheMatchingPendingButtonNotTheWrongCard() {
        val b = builder()
        b.feed(encounter())
        b.answer("live", 1, 3, "A")
        b.answer("live", 2, 1, "B")
        b.answer("live", 3, 3, "C")
        b.feed(listOf(counted(1)))
        b.feed(listOf(counted(3)))
        b.feed(listOf(counted(3), GameEvent.Charge(5, 1, 2)))
        assertEquals(listOf("B", "A", "C"), b.battles.single().rounds.single().answers.map { it.card })
    }

    @Test fun anAnswerWithoutALoggedCardStillShows() {
        val b = builder()
        b.feed(encounter())
        b.feed(listOf(counted(2), GameEvent.Charge(5, 1, 2)))
        assertEquals("a card", b.battles.single().rounds.single().answers.single().card)
    }

    @Test fun answersBeforeTheFirstEncounterJoinIt() {
        val b = builder()
        b.answer("study", 1, 3, null)
        b.feed(listOf(counted(3), GameEvent.Charge(5, 1, 2), encounter()))
        assertEquals(1, b.battles.size)
        assertEquals("Wildling", b.battles[0].wild)
        assertEquals(1, b.battles[0].rounds.single().answers.size)
    }

    @Test fun keepsTheCurrentBattleAndFiveFinishedOnes() {
        val b = builder()
        for (i in 1..9) fightAndEnd(b, "M$i")
        b.feed(listOf(encounter("Open")))
        val list = b.newestFirst()
        assertEquals(1, list.count { it.outcome == Outcome.OPEN })
        assertEquals(5, list.count { it.outcome != Outcome.OPEN })
        assertEquals("M9", list[1].wild) // the last finished one
        // XP from the bag (its own batch) does not land in a finished fight
        b.feed(listOf(GameEvent.XpGained("u1", "Sparkmouse", 99, 9, 1, 100)))
        assertTrue(b.battles.none { fb -> fb.result.any { it.text.contains("+99 XP") } })
    }

    private fun fightAndEnd(b: BattleLogBuilder, name: String) {
        b.feed(listOf(encounter(name)))
        b.feed(listOf(GameEvent.Defeated(1, name, 10)))
    }

    @Test fun missesStatusAndNoEffectAreReadable() {
        val b = builder()
        b.feed(encounter())
        b.answer("live", 1, 1, null)
        b.feed(listOf(counted(1), GameEvent.RoundStart(0.4, 5, true),
            GameEvent.Attack(true, "Sparkmouse", "Wildling", "Zap", Type.ELECTRIC, Category.SPECIAL, 0, 1, 1.0, false, true, false, 30, 30, 40, 40),
            GameEvent.Attack(false, "Wildling", "Sparkmouse", "Glare", Type.NORMAL, Category.STATUS, 0, 1, 1.0, false, false, false, 40, 40, 30, 30),
            GameEvent.Effect(true, "Sparkmouse is paralyzed!", 40, 40, "par"),
            GameEvent.Attack(false, "Wildling", "Sparkmouse", "Bite", Type.NORMAL, Category.PHYSICAL, 0, 1, 0.0, false, false, false, 40, 40, 30, 30),
            GameEvent.Attack(false, "Wildling", "Sparkmouse", "Slam", Type.NORMAL, Category.PHYSICAL, 12, 2, 0.5, true, false, false, 28, 40, 30, 30),
            GameEvent.Effect(true, "Sparkmouse is hurt by poison!", 25, 40, "psn")))
        val lines = b.battles.single().rounds.single().lines
        assertEquals("Sparkmouse used Zap · missed", lines[0].text)
        assertEquals(Tone.SUB, lines[0].tone)
        assertEquals("Wildling used Glare", lines[1].text)
        assertEquals("Sparkmouse is paralyzed!", lines[2].text)
        assertEquals(Tone.BAD, lines[2].tone)
        assertEquals("Wildling used Bite · no effect", lines[3].text)
        assertEquals("Wildling used Slam · 12 damage (2 hits) · critical hit · not very effective · Sparkmouse 28/40 HP", lines[4].text)
        assertEquals(Tone.BAD, lines[4].tone)
        assertEquals("Sparkmouse is hurt by poison! · HP 25/40", lines[5].text)
    }

    @Test fun ballsCatchesFleeingAndHealing() {
        val b = builder()
        b.feed(encounter("Ghosty"))
        b.feed(listOf(GameEvent.BallThrow("poke-ball", 30, false, 4)))
        b.feed(listOf(GameEvent.BallThrow("great-ball", 55, true, 3),
            GameEvent.Caught("c1", 1, "Ghosty", 7, true, "great-ball", true, false), encounter("B")))
        val first = b.battles[0]
        assertEquals(Outcome.CAUGHT, first.outcome)
        assertEquals(RoundKind.BALL, first.rounds[0].kind)
        assertEquals("Threw Poke ball · 30% · it broke free and ran away", first.rounds[0].lines[0].text)
        assertEquals("Caught Ghosty · Lv 7 · shiny · new Pokédex entry · Great ball · sent to the box", first.result[0].text)

        b.feed(listOf(GameEvent.MainHealed("u1", "Sparkmouse", 40), encounter("C")))
        assertEquals(Outcome.LOST, b.battles[1].outcome)
        b.feed(listOf(GameEvent.Fled("C"), encounter("D")))
        assertEquals(Outcome.FLED, b.battles[2].outcome)
    }

    @Test fun codecRoundTripsAndSurvivesGarbage() {
        val b = builder()
        fight(b)
        b.battles[0].rounds[0].answers[0].let { assertEquals("Capital of Australia?", it.card) }
        b.answer("study", 3, 2, "tab\there\nnewline \\ slash")
        b.feed(listOf(counted(2), GameEvent.Charge(5, 1, 2)))
        val text = BattleLogCodec.encode(b.battles)
        val back = BattleLogCodec.decode(text)
        assertEquals(text, BattleLogCodec.encode(back))
        assertEquals("tab\there\nnewline \\ slash", back.last().rounds.last().answers.last().card)
        assertNull(back.last().rounds.last().multiplier)

        assertTrue(BattleLogCodec.decode("").isEmpty())
        assertTrue(BattleLogCodec.decode("not a log").isEmpty())
        val damaged = BattleLogCodec.decode(text + "R\tNOPE\n" + "A\tx\n" + "Z\tGOOD\tafter damage\n")
        assertEquals(back.size, damaged.size)
        assertEquals("after damage", damaged.last().result.last().text)
    }

    @Test fun loadedLogKeepsFeedingTheSameOpenBattle() {
        val b = builder()
        b.feed(encounter())
        b.answer("live", 5, 3, null)
        b.feed(listOf(counted(3), GameEvent.Charge(5, 1, 2)))
        val again = BattleLogBuilder(5, { t++ }, BattleLogCodec.decode(BattleLogCodec.encode(b.battles)))
        again.answer("live", 6, 3, null)
        again.feed(listOf(counted(3), GameEvent.RoundStart(1.0, 10, false)))
        assertEquals(1, again.battles.size)
        assertEquals(2, again.battles[0].rounds.single().answers.size)
        assertFalse(again.battles[0].rounds.single().multiplier == null)
    }

    @Test fun frontTextIsShortPlainOneLine() {
        assertEquals("Capital of Australia?", BattleLog.plain("<div class=\"x\">Capital of<br>Australia?</div><script>var a=1</script>"))
        assertEquals("a & b", BattleLog.plain("a&nbsp;&amp; b"))
        val long = BattleLog.plain("x".repeat(100))
        assertEquals(40, long.length)
        assertTrue(long.endsWith("…"))
        assertEquals("", BattleLog.plain("<img src=a.png>"))
    }

    // ---- R10 W10: the free hit of a switch ----

    @Test fun theSwitchHitIsAMarkerLineAndTheAttackLineInTheRoundItHappenedIn() {
        val b = builder()
        b.feed(encounter())
        b.answer("live", 5, 1, null)
        b.feed(listOf(counted(1), GameEvent.Charge(0, 1, 2)))   // a round is collecting: one answer in
        b.feed(listOf(GameEvent.MainChanged("u2", "Birdie"), GameEvent.SwitchHit("Wildling", "Birdie", 30, 30),
            hit(false, "Wildling", "Birdie", "Bite", 9, 21, 30)))
        // the pending answer's round keeps its answer and gets the lines; the next answer completes THAT round
        b.answer("live", 6, 3, null)
        b.feed(listOf(counted(3), GameEvent.RoundStart(1.0, 10, true, true, 1.0), hit(true, "Birdie", "Wildling", "Zap", 4, 26)))
        val round = b.battles.single().rounds.single()
        assertEquals(2, round.answers.size)
        assertEquals("Switched to Birdie — the wild Wildling attacks once!", round.lines[0].text)
        assertEquals("Wildling used Bite · 9 damage · Birdie 21/30 HP", round.lines[1].text)
        assertEquals(Tone.BAD, round.lines[1].tone)
        assertTrue(round.lines[2].text.startsWith("Birdie used Zap"))
    }

    @Test fun theSwitchHitIsAtNormalPowerWhateverTheRoundBeforeHad() {
        val b = builder()
        b.feed(encounter())
        b.feed(listOf(counted(2), GameEvent.RoundStart(1.0, 25, true, true, 0.35), hit(false, "Wildling", "Sparkmouse", "Bite", 3, 37, 30)))
        b.feed(listOf(GameEvent.MainChanged("u2", "Birdie"), GameEvent.SwitchHit("Wild Wildling", "Birdie", 30, 30),   // (a name with "Wild " in front is shown once)
            hit(false, "Wildling", "Birdie", "Bite", 9, 21, 30)))
        val lines = b.battles.single().rounds.single().lines.map { it.text }
        assertTrue(lines[0], "×0.35 power" in lines[0])
        assertEquals("Switched to Birdie — the wild Wildling attacks once!", lines[1])
        assertFalse(lines[2], "power" in lines[2])
    }

    @Test fun anItemTurnIsAMarkerLineWithTheItem() {   // R13 Part C
        val b = builder()
        b.feed(encounter())
        b.feed(listOf(counted(2), GameEvent.RoundStart(1.0, 25, true, true, 0.35), hit(false, "Wildling", "Sparkmouse", "Bite", 3, 37, 30)))
        b.feed(listOf(GameEvent.SwitchHit("Wildling", "Sparkmouse", 30, 40, "super-potion"), hit(false, "Wildling", "Sparkmouse", "Bite", 9, 21, 40)))
        val lines = b.battles.single().rounds.single().lines.map { it.text }
        assertEquals("Used Super Potion on Sparkmouse — the wild Wildling attacks once!", lines[1])
        assertFalse(lines[2], "power" in lines[2])
    }

    @Test fun aSwitchHitThatMakesTheNewMainFaintEndsTheFightLikeARound() {
        val b = builder()
        b.feed(encounter())
        b.feed(listOf(GameEvent.MainChanged("u2", "Birdie"), GameEvent.SwitchHit("Wildling", "Birdie", 4, 30),
            hit(false, "Wildling", "Birdie", "Bite", 4, 0, 30), GameEvent.Fainted(true, "Birdie"), GameEvent.MainHealed("u2", "Birdie", 30), encounter("Next")))
        val first = b.battles[0]
        assertEquals(Outcome.LOST, first.outcome)
        assertEquals("Birdie fainted", first.rounds.single().lines.last().text)
        assertEquals("Birdie was healed to 30 HP · the wild one is lost", first.result.single().text)
        assertEquals("Next", b.battles[1].wild)
    }
}
