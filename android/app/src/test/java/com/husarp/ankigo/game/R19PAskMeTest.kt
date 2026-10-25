package com.husarp.ankigo.game

import com.husarp.ankigo.game.ankimon.AnkimonExport
import com.husarp.ankigo.game.ankimon.AnkimonRefs
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R19-P (PLAN 281): the catch option "Ask me" and the safe replacing of a copy. Pure rules + the game (no Android).
 * Birdie (16) is the species under test; the main is Sparkmouse (25, Lv 20). Mode 3 + askMe = "Ask me".
 */
class R19PAskMeTest {
    private val data = TestData.world()
    private var now = 1_000L
    private var card = 1L

    private fun ivs(total: Int): Stats { // spread [total] over six stats (max 31 each)
        val a = IntArray(6); var left = total
        for (i in a.indices) { a[i] = minOf(31, left); left -= a[i] }
        return Stats(a[0], a[1], a[2], a[3], a[4], a[5])
    }

    private fun mon(species: Int, level: Int, uid: String, ivTotal: Int = 90): Mon {
        val b = TestData.mon(data, species, level, listOf("tackle"), uid)
        return Mon(uid = uid, species = species, name = b.name, level = level, xp = 0, ivs = ivs(ivTotal), evs = Stats.ZERO, nature = Nature.HARDY,
            gender = 'M', shiny = false, ability = "Testability", moves = mutableListOf("tackle"), hp = b.hp).also { it.hp = StatCalc.maxHp(data, it) }
    }

    /** Team: main (Sparkmouse 25, Lv 20). Box: d = Birdie 16, Lv 20, IV 90. "Ask me" is on. */
    private fun state(ask: Boolean = true): GameState {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        s.mons += mon(16, 20, "d"); s.caught += 16
        CatchChoice.apply(s.settings, if (ask) CatchChoice.ASK_ME else CatchChoice.NEW_ONLY)
        return s
    }

    private fun game(s: GameState) = Game(data, s, Random(1)) { now }.also { s.day = it.dayKey() }

    private fun wild(species: Int, level: Int, ivTotal: Int = 90, uid: String = "wild", shiny: Boolean = false, tier: Tier = Tier.NORMAL): Battle =
        Battle(mon(species, level, uid, ivTotal).also { it.shiny = shiny }, tier)

    /** The fight with [w] at 1 HP; two Good answers end it. */
    private fun Game.faint(w: Mon): List<GameEvent> {
        w.hp = 1
        state.battle = Battle(w, Tier.NORMAL, startedAt = now)
        onAnswer(Answer(card++, 3, now))
        return onAnswer(Answer(card++, 3, now))
    }

    private inline fun <reified T : GameEvent> List<GameEvent>.of() = filterIsInstance<T>()

    // ---------------------------------------------------------------- "better", exactly

    @Test fun betterByLevelByIvsNeitherAndEqual() {
        val s = state()
        // level: higher than every copy
        assertTrue(AskMe.better(s, mon(16, 21, "w"))!!.byLevel)
        // equal level, equal IVs: not better
        assertNull(AskMe.better(s, mon(16, 20, "w")))
        assertNull(AskMe.better(s, mon(16, 5, "w", ivTotal = 90)))
        // lower level but a higher IV total: better by IVs only
        val byIv = AskMe.better(s, mon(16, 5, "w", ivTotal = 91))!!
        assertFalse(byIv.byLevel); assertTrue(byIv.byIvs); assertSame(s.mon("d"), byIv.yours)
        // lower level and lower IV total: not better
        assertNull(AskMe.better(s, mon(16, 5, "w", ivTotal = 89)))
        // both
        val both = AskMe.better(s, mon(16, 30, "w", ivTotal = 120))!!
        assertTrue(both.byLevel && both.byIvs)
    }

    @Test fun betterIsAgainstEveryCopyYouOwn() {
        val s = state()
        s.mons += mon(16, 8, "hi-iv", ivTotal = 150) // a low-level copy with great IVs
        assertNull(AskMe.better(s, mon(16, 10, "w", ivTotal = 120)))      // beats the best level? no (20). IVs 120 < 150: not better
        val b = AskMe.better(s, mon(16, 10, "w", ivTotal = 151))!!        // beats the best IV total (150), not the level
        assertTrue(b.byIvs && !b.byLevel); assertSame(s.mon("hi-iv"), b.yours) // compared with (and replacing) the copy it beats on IVs
        val c = AskMe.better(s, mon(16, 21, "w"))!!                       // beats the level: compared with the best-level copy
        assertTrue(c.byLevel); assertSame(s.mon("d"), c.yours)
    }

    @Test fun aSpeciesYouDoNotOwnIsNeverAsked() {
        val s = state()
        assertNull(AskMe.better(s, mon(54, 40, "w", ivTotal = 186)))
    }

    // ---------------------------------------------------------------- the decision

    @Test fun planAsksOnlyForABetterOwnedCopy() {
        val s = state()
        val better = Catching.plan(data, s, wild(16, 30))
        assertEquals(Outcome.DEFEAT, better.outcome); assertNotNull(better.ask); assertNull(better.duplicate)
        val same = Catching.plan(data, s, wild(16, 20))
        assertEquals(Outcome.DEFEAT, same.outcome); assertNull(same.ask)            // not better: defeated as today
        val fresh = Catching.plan(data, s, wild(54, 10))
        assertEquals(Outcome.CATCH, fresh.outcome); assertNull(fresh.ask)           // new species: caught as today
        s.caught += 54
        assertEquals(Outcome.DEFEAT, Catching.plan(data, s, wild(54, 10)).outcome)  // known but none owned: today's rule (defeated)
    }

    @Test fun shinyProtectedAndWishlistedAreCaughtAsAlways() {
        val s = state()
        assertEquals(Outcome.CATCH, Catching.plan(data, s, wild(16, 30, shiny = true)).outcome)
        assertNull(Catching.plan(data, s, wild(16, 30, shiny = true)).ask)
        assertEquals(Outcome.CATCH, Catching.plan(data, s, wild(16, 30, tier = Tier.ULTRA)).outcome)
        s.settings.wishlist += 16
        assertEquals(Outcome.CATCH, Catching.plan(data, s, wild(16, 30)).outcome)
    }

    @Test fun theOneOffTapIsTheirOwnChoiceAndAsksNothing() {
        val s = state()
        val c = wild(16, 30).also { it.override = Outcome.CATCH }
        assertEquals(Outcome.CATCH, Catching.plan(data, s, c).outcome); assertNull(Catching.plan(data, s, c).ask)
        val d = wild(16, 30).also { it.override = Outcome.DEFEAT }
        assertEquals(Outcome.DEFEAT, Catching.plan(data, s, d).outcome); assertNull(Catching.plan(data, s, d).ask)
    }

    @Test fun otherOptionsNeverAsk() {
        for (c in CatchChoice.entries.filter { it != CatchChoice.ASK_ME }) {
            val s = state(); CatchChoice.apply(s.settings, c)
            assertNull(c.name, Catching.plan(data, s, wild(16, 30)).ask)
        }
    }

    // ---------------------------------------------------------------- the settings pair

    @Test fun theOptionWritesAndReadsBackAndKeepsTheAnkimonMode() {
        val s = Settings()
        CatchChoice.apply(s, CatchChoice.ASK_ME)
        assertEquals(3, s.autoMode); assertFalse(s.autoReleaseDuplicates); assertTrue(s.askMe)
        assertEquals(CatchChoice.ASK_ME, CatchChoice.of(s))
        for (c in CatchChoice.entries) { val t = Settings(); CatchChoice.apply(t, c); assertEquals(c, CatchChoice.of(t)); assertEquals(c == CatchChoice.ASK_ME, t.askMe) }
        assertEquals(listOf("Always catch", "Catch only new species", "Catch new species and better copies", "Defeat", "Ask me"), CatchChoice.entries.map { it.title })
    }

    @Test fun normalizeKeepsWhatTheListShows() {
        val a = Settings().also { it.askMe = true; it.autoMode = 1 }; CatchChoice.normalize(a)
        assertFalse(a.askMe); assertEquals(CatchChoice.ALWAYS, CatchChoice.of(a))
        val b = Settings().also { it.askMe = true; it.autoMode = 3; it.autoReleaseDuplicates = true }; CatchChoice.normalize(b)
        assertTrue(b.askMe); assertFalse(b.autoReleaseDuplicates); assertEquals(CatchChoice.ASK_ME, CatchChoice.of(b))
    }

    // ---------------------------------------------------------------- the fight's end

    @Test fun theFightIsPaidLikeADefeatAndTheQuestionWaits() {
        val plain = state(ask = false) // mode 3: an owned species is defeated
        val gPlain = game(plain)
        val evPlain = gPlain.faint(mon(16, 30, "wild"))
        val s = state()
        val g = game(s)
        val ev = g.faint(mon(16, 30, "wild"))
        val def = ev.of<GameEvent.Defeated>().single()
        assertTrue(def.ask); assertEquals(evPlain.of<GameEvent.Defeated>().single().copy(ask = true), def)
        assertTrue(ev.of<GameEvent.Caught>().isEmpty())
        assertEquals("same XP", evPlain.of<GameEvent.XpGained>().sumOf { it.xp }, ev.of<GameEvent.XpGained>().sumOf { it.xp })
        assertEquals("same coins", evPlain.of<GameEvent.Cash>().sumOf { it.amount }, ev.of<GameEvent.Cash>().sumOf { it.amount })
        assertEquals(plain.cash, s.cash); assertEquals(plain.defeats, s.defeats); assertEquals(plain.trainerXp, s.trainerXp)
        assertEquals(1, s.defeats); assertEquals(0, s.catches)
        assertEquals(listOf("main", "d"), s.mons.map { it.uid })                 // nothing caught yet
        val q = s.asks.single()
        assertEquals(PendingAsk.Kind.CATCH, q.kind); assertEquals("wild", q.uid); assertEquals(30, q.wild!!.level)
        assertTrue("a plain defeat asks nothing", plain.asks.isEmpty() && !evPlain.of<GameEvent.Defeated>().single().ask)
    }

    @Test fun aNotBetterOneIsDefeatedAsToday() {
        val s = state(); val ev = game(s).faint(mon(16, 20, "wild"))
        assertFalse(ev.of<GameEvent.Defeated>().single().ask); assertTrue(s.asks.isEmpty())
    }

    @Test fun aNewSpeciesIsCaughtAtOnce() {
        val s = state(); val ev = game(s).faint(mon(54, 12, "wild"))
        assertEquals(1, ev.of<GameEvent.Caught>().size); assertTrue(s.asks.isEmpty()); assertEquals(1, s.catches)
    }

    // ---------------------------------------------------------------- the answers

    private fun asked(s: GameState = state(), level: Int = 30, iv: Int = 120): Pair<Game, GameState> =
        game(s).also { it.faint(mon(16, level, "wild", ivTotal = iv)) }.let { it to s }

    @Test fun catchKeepsBothWithTheWildOnesOwnNumbers() {
        val (g, s) = asked()
        val r = g.answerCatch("wild", AskChoice.CATCH)
        assertTrue(r.ok); assertTrue(s.asks.isEmpty())
        assertEquals(listOf(16, 16), s.mons.filter { it.species == 16 }.map { it.species })
        val c = s.mons.single { it.species == 16 && it.uid != "d" }
        assertEquals(30, c.level); assertEquals(120, c.ivs.total)
        assertTrue("d" in s.mons.map { it.uid }); assertFalse(c.uid in s.team)         // the Box, not the team
        assertEquals(1, s.catches); assertEquals(1, s.catchWins)
        assertEquals("battles won still counts the fight once", 1, s.defeats + s.catches - s.catchWins)
        assertEquals(1, s.history[s.day]!!.caught)
        assertTrue(r.events.none { it is GameEvent.Caught })
    }

    @Test fun dontCatchAddsNothing() {
        val (g, s) = asked()
        val r = g.answerCatch("wild", AskChoice.DONT)
        assertTrue(r.ok); assertTrue(s.asks.isEmpty())
        assertEquals(listOf("main", "d"), s.mons.map { it.uid }); assertEquals(0, s.catches); assertEquals(1, s.defeats)
    }

    @Test fun aQuestionIsAnsweredExactlyOnce() {
        val (g, s) = asked()
        assertTrue(g.answerCatch("wild", AskChoice.CATCH).ok)
        val n = s.mons.size
        assertFalse(g.answerCatch("wild", AskChoice.CATCH).ok)
        assertFalse(g.answerCatch("wild", AskChoice.REPLACE).ok)
        assertFalse(g.answerCatch("nobody", AskChoice.DONT).ok)
        assertEquals(n, s.mons.size); assertEquals(1, s.catches)
    }

    @Test fun replaceReleasesAPlainCopyAtOnce() {
        val s = state(); s.mon("d")!!.ankimonId = "ank-9"; s.mon("d")!!.heldItem = "everstone"
        val (g, _) = asked(s)
        val r = g.answerCatch("wild", AskChoice.REPLACE)
        assertTrue(r.ok); assertTrue(s.asks.isEmpty())
        assertNull(s.mon("d")); assertEquals(listOf(30), s.mons.filter { it.species == 16 }.map { it.level })
        assertTrue("ank-9" in s.releasedAnkimon)                    // the Ankimon export removes it
        assertEquals(1, s.count("everstone"))                       // its held item went to the Bag
    }

    @Test fun replaceIsOnlyACatchWhenTheWildOneIsNotBetterAnyMore() {
        val s = state()
        val (g, _) = asked(s)
        s.mons += mon(16, 40, "later", ivTotal = 130)                  // meanwhile you got a better one (level and IVs)
        assertTrue(g.answerCatch("wild", AskChoice.REPLACE).ok)
        assertEquals(3, s.mons.count { it.species == 16 }); assertTrue(s.asks.isEmpty())
    }

    @Test fun aShinyOrProtectedCopyIsNeverReleased() {
        val s = state(); s.mon("d")!!.shiny = true
        val (g, _) = asked(s)
        assertTrue(g.answerCatch("wild", AskChoice.REPLACE).ok)
        assertNotNull(s.mon("d")); assertEquals(2, s.mons.count { it.species == 16 }); assertTrue(s.asks.isEmpty())
        val p = state(); p.mons += mon(150, 40, "legend"); p.caught += 150
        assertFalse(AskMe.canReplace(data, p, p.mon("legend")!!))
        assertTrue(AskMe.canReplace(data, p, p.mon("d")!!))
    }

    // ---------------------------------------------------------------- replacing a copy that holds a place

    private fun withPlace(team: Boolean = false, fav: Boolean = false, share: Boolean = false): Pair<Game, GameState> {
        val s = state(); val d = s.mon("d")!!
        if (team) s.team += "d"
        d.favourite = fav
        if (share) s.settings.xpShareUid = "d"
        return asked(s)
    }

    @Test fun aCopyThatHoldsAPlaceOpensTheReleaseQuestionAfterTheCatch() {
        val (g, s) = withPlace(team = true, fav = true, share = true)
        val r = g.answerCatch("wild", AskChoice.REPLACE)
        assertTrue(r.ok)
        assertNotNull(s.mon("d"))                                    // nothing released yet
        val q = s.asks.single()
        assertEquals(PendingAsk.Kind.REPLACE, q.kind); assertEquals("d", q.oldUid)
        val fresh = s.mon(q.uid)!!; assertEquals(30, fresh.level)
        assertSame(q, g.nextAsk())
    }

    @Test fun keepItsPlaceMovesTeamHeartAndExpShare() {
        val (g, s) = withPlace(team = true, fav = true, share = true)
        g.answerCatch("wild", AskChoice.REPLACE)
        val q = s.asks.single()
        val r = g.answerReplace(q.uid, release = true, keepPlace = true)
        assertTrue(r.ok); assertTrue(s.asks.isEmpty()); assertNull(s.mon("d"))
        assertEquals(listOf("main", q.uid), s.team)                  // the same slot (2)
        assertTrue(s.mon(q.uid)!!.favourite); assertEquals(q.uid, s.settings.xpShareUid)
        assertTrue("d" in s.releasedAnkimon)
    }

    @Test fun withoutKeepingItsPlaceTheMarksGoWithTheOldOne() {
        val (g, s) = withPlace(team = true, fav = true, share = true)
        g.answerCatch("wild", AskChoice.REPLACE)
        val q = s.asks.single()
        assertTrue(g.answerReplace(q.uid, release = true, keepPlace = false).ok)
        assertNull(s.mon("d")); assertEquals(listOf("main"), s.team)
        assertFalse(s.mon(q.uid)!!.favourite); assertNull(s.settings.xpShareUid)
    }

    @Test fun aFavouriteAloneOrAnExpShareAloneIsPlaceEnough() {
        for (variant in listOf(withPlace(fav = true), withPlace(share = true))) {
            val (g, s) = variant
            g.answerCatch("wild", AskChoice.REPLACE)
            val q = s.asks.single(); assertEquals(PendingAsk.Kind.REPLACE, q.kind)
            g.answerReplace(q.uid, release = true, keepPlace = true)
            assertNull(s.mon("d")); assertEquals(listOf("main"), s.team)
            assertTrue(s.mon(q.uid)!!.favourite || s.settings.xpShareUid == q.uid)
        }
    }

    @Test fun noKeepsBoth() {
        val (g, s) = withPlace(team = true, fav = true)
        g.answerCatch("wild", AskChoice.REPLACE)
        val q = s.asks.single()
        val r = g.answerReplace(q.uid, release = false, keepPlace = true)
        assertTrue(r.ok); assertTrue(s.asks.isEmpty())
        assertNotNull(s.mon("d")); assertTrue(s.mon("d")!!.favourite); assertTrue("d" in s.team)
        assertFalse(s.mon(q.uid)!!.favourite); assertFalse(q.uid in s.team)
    }

    @Test fun theReleaseQuestionIsAnsweredOnce() {
        val (g, s) = withPlace(team = true)
        g.answerCatch("wild", AskChoice.REPLACE)
        val q = s.asks.single()
        assertTrue(g.answerReplace(q.uid, true, true).ok)
        assertFalse(g.answerReplace(q.uid, true, true).ok)
        assertEquals(1, s.mons.count { it.species == 16 })
    }

    @Test fun aReleaseQuestionWhosePokemonChangedReleasesNothing() {
        val (g, s) = withPlace(team = true)
        g.answerCatch("wild", AskChoice.REPLACE)
        val q = s.asks.single()
        s.mons.remove(s.mon("d")!!); s.team.remove("d")              // released by hand meanwhile
        val r = g.answerReplace(q.uid, true, true)
        assertFalse(r.ok); assertEquals(Duplicates.CHANGED_NOTHING, r.message)
        assertTrue(s.asks.isEmpty()); assertNotNull(s.mon(q.uid))
    }

    @Test fun theMainCanBeReplacedAndNoWildHitFollows() {
        val s = state()
        s.mons += mon(25, 5, "spare") // a second Sparkmouse is irrelevant; the wild one is a better Sparkmouse than the main
        val g = game(s)
        s.asks += PendingAsk(PendingAsk.Kind.CATCH, "wild", mon(25, 30, "wild", ivTotal = 100), Tier.NORMAL) // a fight that ended earlier
        s.battle = Battle(mon(16, 12, "next").also { it.hp = StatCalc.maxHp(data, it) }, Tier.NORMAL, startedAt = now)
        val b = s.battle!!; b.rounds = 3; b.answers = 1                // a fight in progress: a switch would cost a hit
        val before = b.wild.hp
        g.answerCatch("wild", AskChoice.REPLACE)
        val q = s.asks.single(); assertEquals("main", q.oldUid)
        val r = g.answerReplace(q.uid, release = true, keepPlace = true)
        assertTrue(r.ok); assertEquals(q.uid, s.team.first()); assertNull(s.mon("main"))
        assertTrue(r.events.any { it is GameEvent.MainChanged })
        assertTrue("a better copy came in, nothing retreated", r.events.none { it is GameEvent.SwitchHit })
        assertEquals(before, s.battle!!.wild.hp)
    }

    @Test fun aTeamNeverEndsEmpty() {
        val s = state()
        val m = s.mon("main")!!
        s.mons += mon(25, 5, "z"); s.caught += 25
        val g = game(s)
        s.asks += PendingAsk(PendingAsk.Kind.CATCH, "wild", mon(25, 30, "wild"), Tier.NORMAL)
        g.answerCatch("wild", AskChoice.REPLACE)
        val q = s.asks.single()
        assertTrue(g.answerReplace(q.uid, true, keepPlace = false).ok)  // the only team member goes; the new one takes the slot
        assertEquals(listOf(q.uid), s.team); assertNull(s.mon(m.uid))
    }

    @Test fun theHeldItemOfTheOldOneGoesToTheBag() {
        val (g, s) = withPlace(team = true)
        s.mon("d")!!.heldItem = "leftovers"
        g.answerCatch("wild", AskChoice.REPLACE)
        g.answerReplace(s.asks.single().uid, true, true)
        assertEquals(1, s.count("leftovers"))
    }

    // ---------------------------------------------------------------- the "better copies" option meets a place

    private fun swapState(): GameState = state(ask = false).also { CatchChoice.apply(it.settings, CatchChoice.NEW_AND_BETTER) }

    @Test fun betterCopiesAsksWhenTheBestCopyOnlyHoldsAPlace() {
        val s = swapState(); s.team += "d"; s.mon("d")!!.favourite = true
        val g = game(s)
        val ev = g.faint(mon(16, 30, "wild"))
        val dup = ev.of<GameEvent.Duplicate>().single()
        assertEquals(DuplicateResult.KEPT_BOTH, dup.result); assertTrue(dup.ask)     // still "kept both", plus the question
        assertEquals(2, s.mons.count { it.species == 16 })
        val q = s.asks.single(); assertEquals(PendingAsk.Kind.REPLACE, q.kind); assertEquals("d", q.oldUid)
        assertEquals(ev.of<GameEvent.Caught>().single().uid, q.uid)
        assertTrue(g.answerReplace(q.uid, true, true).ok)
        assertNull(s.mon("d")); assertTrue(s.mon(q.uid)!!.favourite); assertEquals(listOf("main", q.uid), s.team)
    }

    @Test fun betterCopiesNeverAsksAboutAShinyOrAProtectedOrAPlainCopy() {
        val shiny = swapState(); shiny.mon("d")!!.shiny = true; shiny.mon("d")!!.favourite = true
        val e1 = game(shiny).faint(mon(16, 30, "wild"))
        assertFalse(e1.of<GameEvent.Duplicate>().single().ask); assertTrue(shiny.asks.isEmpty())
        val nick = swapState(); nick.mon("d")!!.nickname = "Zappy"                    // no place: kept both as today, no question
        val e2 = game(nick).faint(mon(16, 30, "wild"))
        assertEquals(DuplicateResult.KEPT_BOTH, e2.of<GameEvent.Duplicate>().single().result); assertFalse(e2.of<GameEvent.Duplicate>().single().ask)
        assertTrue(nick.asks.isEmpty())
        val plain = swapState()
        val e3 = game(plain).faint(mon(16, 30, "wild"))
        assertEquals(DuplicateResult.SWAPPED, e3.of<GameEvent.Duplicate>().single().result); assertTrue(plain.asks.isEmpty())
    }

    // ---------------------------------------------------------------- the save

    @Test fun waitingQuestionsSurviveARestart() {
        val (g, s) = withPlace(team = true)
        g.answerCatch("wild", AskChoice.REPLACE)                    // a release question
        s.asks += PendingAsk(PendingAsk.Kind.CATCH, "w2", mon(16, 44, "w2", ivTotal = 130), Tier.BABY, at = 77L)
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(SaveCodec.encode(s), SaveCodec.encode(back))   // deterministic
        assertEquals(2, back.asks.size)
        val c = back.asks.first { it.kind == PendingAsk.Kind.CATCH }
        assertEquals("w2", c.uid); assertEquals(44, c.wild!!.level); assertEquals(130, c.wild!!.ivs.total); assertEquals(Tier.BABY, c.tier); assertEquals(77L, c.at)
        val r = back.asks.first { it.kind == PendingAsk.Kind.REPLACE }; assertEquals("d", r.oldUid)
        // answered once on the restored game, then gone
        val g2 = Game(data, back, Random(2)) { now }
        assertTrue(g2.answerCatch("w2", AskChoice.CATCH).ok)
        assertFalse(g2.answerCatch("w2", AskChoice.CATCH).ok)
        assertEquals(1, back.asks.size)
        assertSame(back.asks.single(), g2.nextAsk())
    }

    @Test fun anOldSaveHasNoQuestionAndNoAskMe() {
        val old = SaveCodec.decode("""{"schema":1,"settings":{"autoMode":3},"mons":[{"uid":"a","species":25,"level":5}]}""")
        assertTrue(old.asks.isEmpty()); assertFalse(old.settings.askMe)
        assertEquals(CatchChoice.NEW_ONLY, CatchChoice.of(old.settings))
        val plain = SaveCodec.encode(TestData.state(data))
        assertFalse(""""asks"""" in plain); assertFalse(""""askMe"""" in plain)       // a save without them is byte for byte what it was
    }

    @Test fun askMeSurvivesTheSaveAndAnOldAppReadsItAsCatchOnlyNew() {
        val s = state()
        val text = SaveCodec.encode(s)
        assertTrue(""""askMe":true""" in text)
        val back = SaveCodec.decode(text)
        assertEquals(CatchChoice.ASK_ME, CatchChoice.of(back.settings))
        assertEquals(3, back.settings.autoMode); assertFalse(back.settings.autoReleaseDuplicates)
        // the mode the Ankimon side and an app that does not know "Ask me" read is 3
    }

    @Test fun anUnreadableQuestionIsDroppedAndNothingElseIsTouched() {
        val s = state(); val text = SaveCodec.encode(s).replaceFirst(""""settings":""", """"asks":[{"kind":"CATCH","uid":"x"},{"kind":"WHAT","uid":"y"},{"uid":"z"}],"settings":""")
        val back = SaveCodec.decode(text)
        assertTrue(back.asks.isEmpty()); assertEquals(2, back.mons.size)
    }

    @Test fun theQueueIsCappedAndTheOldestGoesFirst() {
        val s = state(); val g = game(s)
        for (i in 1..AskMe.MAX_WAITING + 3) g.faint(mon(16, 30 + i, "wild$i"))
        assertEquals(AskMe.MAX_WAITING, s.asks.size)
        assertEquals("wild4", s.asks.first().uid)                    // wild1..wild3 were the oldest and were dropped (never caught)
        assertEquals(0, s.catches)
        // a release question is shown before a catch question
        s.asks += PendingAsk(PendingAsk.Kind.REPLACE, "n", oldUid = "d")
        assertEquals(PendingAsk.Kind.REPLACE, g.nextAsk()!!.kind)
    }

    // ---------------------------------------------------------------- the Ankimon export does not change

    @Test fun theAnkimonExportIsTheSameWithAndWithoutAskMe() {
        val refs = AnkimonRefs.fromFiles { name ->
            when (name) {
                "items.csv" -> "id,identifier,category_id,cost,fling_power,fling_effect_id\n" +
                    "4,poke-ball,34,200,,\n3,great-ball,34,600,,\n17,potion,27,200,,\n50,rare-candy,26,10000,30,\n208,lucky-egg,16,10000,30,7\n"
                "moves.json" -> """{"tackle":{"num":33},"thunderbolt":{"num":85},"slash":{"num":163},"doubleedge":{"num":38},"bodyslam":{"num":34}}"""
                "pokemon.csv" -> "id,identifier,species_id,height,weight,base_experience,order,is_default\n10033,venusaur-mega,3,24,1555,,1,0\n"
                else -> null
            }
        }
        val plain = state(ask = false)
        val asking = state(ask = true)
        game(asking).faint(mon(16, 30, "wild"))                       // a waiting question in the state ...
        game(plain).faint(mon(16, 30, "wild"))                        // ... against the same fight paid as a plain defeat
        assertEquals(1, asking.asks.size); assertTrue(plain.asks.isEmpty())
        val a = AnkimonExport.build(data, asking, null, refs, 1_800_000_000_000L)
        val b = AnkimonExport.build(data, plain, null, refs, 1_800_000_000_000L)
        assertTrue("the export is byte for byte the same", a.bytes.contentEquals(b.bytes))
    }
}
