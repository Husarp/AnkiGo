package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

class ProgressionTest {
    private val data = TestData.world()
    private val noon: Long = at(12)
    private val midnight: Long = at(0)

    private fun at(hour: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 13, hour, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun ev() = mutableListOf<GameEvent>()
    private fun st(species: Int = 1, level: Int = 5, moves: List<String> = listOf("tackle")): Pair<GameState, Mon> {
        val s = TestData.state(data, species, level, moves)
        return s to s.main!!
    }
    private inline fun <reified T : GameEvent> List<GameEvent>.of() = filterIsInstance<T>()

    // ---- XP ----

    @Test fun xpForDefeatFormula() {
        assertEquals(72, Progression.xpForDefeat(data.sp(16), 10)) // ceil(50*10/7)
        assertEquals(1, Progression.xpForDefeat(TestData.species(900, baseExp = 1), 1))
        assertEquals(10, Progression.xpForDefeat(TestData.species(901, baseExp = 70), 1))
    }

    @Test fun xpToNextUsesGrowthTable() {
        val (_, m) = st(1, 10) // rate 2: 11^3 - 10^3
        assertEquals(331, Progression.xpToNext(data, m))
        m.level = 100
        assertEquals(0, Progression.xpToNext(data, m))
    }

    @Test fun xpToNextMissingTableFallsBackToMedium() {
        val d = GameData(mapOf(1 to TestData.species(1, growthRate = 6)), TestData.MOVES, emptyMap(), emptyMap())
        val m = TestData.mon(TestData.data(listOf(TestData.species(1, growthRate = 6))), 1, 10)
        assertEquals(331, Progression.xpToNext(d, m))
    }

    @Test fun movesUpToIsDistinctAndInOrder() {
        val sp = TestData.species(5, levelMoves = listOf(LevelMove(0, "a"), LevelMove(1, "b"), LevelMove(5, "a"), LevelMove(9, "c")))
        assertEquals(listOf("a", "b"), Progression.movesUpTo(sp, 5))
        assertEquals(listOf("a", "b", "c"), Progression.movesUpTo(sp, 50))
        assertEquals(listOf("a"), Progression.movesUpTo(sp, 0))
    }

    // ---- newMon ----

    @Test fun newMonBasics() {
        val m = Progression.newMon(data, 1, 20, Random(3), 100L, shiny = true)
        assertEquals("Leafling", m.name)
        assertEquals(20, m.level); assertEquals(0, m.xp)
        assertTrue(m.shiny)
        assertEquals(Stats.ZERO, m.evs)
        assertTrue(m.ivs.toList().all { it in 0..31 })
        assertEquals(StatCalc.maxHp(data, m), m.hp)
        assertEquals("Testability", m.ability)
        assertEquals(4, m.moves.size)
        assertEquals(4, m.moves.toSet().size)
        assertTrue(m.moves.all { it in Progression.movesUpTo(data.sp(1), 20) })
        assertEquals(36, m.uid.length)
        assertNotEquals(m.uid, Progression.newMon(data, 1, 20, Random(3), 100L).uid)
    }

    @Test fun newMonGenderAndFallbackMove() {
        val genderless = TestData.data(listOf(TestData.species(7, maleRatio = null, levelMoves = emptyList())))
        val m = Progression.newMon(genderless, 7, 5, Random(1), 0)
        assertEquals('N', m.gender)
        assertEquals(listOf("tackle"), m.moves)
        val allMale = TestData.data(listOf(TestData.species(8, maleRatio = 1.0)))
        assertEquals('M', Progression.newMon(allMale, 8, 5, Random(1), 0).gender)
        val allFemale = TestData.data(listOf(TestData.species(9, maleRatio = 0.0)))
        assertEquals('F', Progression.newMon(allFemale, 9, 5, Random(1), 0).gender)
    }

    @Test fun newMonFewMovesKeepsAll() {
        val m = Progression.newMon(data, 1, 5, Random(2), 0) // tackle, growl only
        assertEquals(setOf("tackle", "growl"), m.moves.toSet())
    }

    // ---- gainXp ----

    @Test fun gainXpWithinLevel() {
        val (s, m) = st(1, 5)
        val e = ev()
        assertEquals(0, Progression.gainXp(data, s, m, 50, e))
        assertEquals(5, m.level); assertEquals(50, m.xp)
        val x = e.of<GameEvent.XpGained>().single()
        assertEquals(50, x.xp); assertEquals(5, x.level); assertEquals(50, x.xpInLevel); assertEquals(91, x.xpForLevel) // 216-125
    }

    @Test fun gainXpMultipleLevelsOrderAndMoves() {
        val (s, m) = st(1, 5, listOf("tackle", "growl"))
        val e = ev()
        val hpBefore = m.hp
        // levels 5->6 (91) ->7 (127) ->8 needs 169 (512-343); give 91+127+10
        assertEquals(2, Progression.gainXp(data, s, m, 91 + 127 + 10, e))
        assertEquals(7, m.level); assertEquals(10, m.xp)
        assertTrue(e.first() is GameEvent.XpGained)
        assertEquals(listOf(6, 7), e.of<GameEvent.LevelUp>().map { it.level })
        assertEquals("Vine Whip", e.of<GameEvent.MoveLearned>().single().move) // learned at 7
        assertTrue(m.moves.contains("vinewhip"))
        assertEquals(7, e.of<GameEvent.XpGained>().single().level)
        assertEquals(10, e.of<GameEvent.XpGained>().single().xpInLevel)
        assertTrue(e.indexOfFirst { it is GameEvent.MoveLearned } > e.indexOfLast { it is GameEvent.LevelUp })
        // HP did not heal
        assertEquals(hpBefore, m.hp)
        assertTrue(StatCalc.maxHp(data, m) > hpBefore)
        assertTrue(5 in s.badges)
    }

    @Test fun gainXpCappedAtTenLevels() {
        val (s, m) = st(1, 5)
        val e = ev()
        assertEquals(10, Progression.gainXp(data, s, m, 1_000_000, e))
        assertEquals(15, m.level)
        assertEquals(Progression.xpToNext(data, m) - 1, m.xp)
        assertEquals(10, e.of<GameEvent.LevelUp>().size)
    }

    @Test fun gainXpStopsAtLevel100() {
        val (s, m) = st(1, 99)
        val e = ev()
        assertEquals(1, Progression.gainXp(data, s, m, 1_000_000, e))
        assertEquals(100, m.level); assertEquals(0, m.xp)
        e.clear()
        assertEquals(0, Progression.gainXp(data, s, m, 500, e))
        assertTrue(e.isEmpty())
        assertEquals(0, m.xp)
    }

    @Test fun gainXpDoesNotHeal() {
        val (s, m) = st(1, 5)
        m.hp = 3
        Progression.gainXp(data, s, m, 91, ev())
        assertEquals(3, m.hp)
    }

    @Test fun firstLevelUpBadgeOnce() {
        val (s, m) = st(1, 5)
        val e = ev()
        Progression.gainXp(data, s, m, 91, e)
        assertEquals(1, e.of<GameEvent.BadgeEarned>().count { it.id == 5 })
        e.clear()
        Progression.gainXp(data, s, m, 127, e)
        assertEquals(0, e.of<GameEvent.BadgeEarned>().size)
    }

    // ---- learnMove ----

    @Test fun learnMoveAddsWhenRoom() {
        val (_, m) = st(1, 5, listOf("tackle"))
        val e = ev()
        Progression.learnMove(data, m, "vinewhip", e)
        assertEquals(listOf("tackle", "vinewhip"), m.moves)
        assertEquals(GameEvent.MoveLearned(m.uid, m.name, "Vine Whip", null), e.single())
    }

    @Test fun learnMoveAlreadyKnownOrUnknownDoesNothing() {
        val (_, m) = st(1, 5, listOf("tackle"))
        val e = ev()
        Progression.learnMove(data, m, "tackle", e)
        Progression.learnMove(data, m, "nosuchmove", e)
        assertEquals(listOf("tackle"), m.moves)
        assertTrue(e.isEmpty())
    }

    @Test fun learnMoveReplacesWeakest() {
        val (_, m) = st(1, 5, listOf("tackle", "growl", "vinewhip", "lick"))
        val e = ev()
        Progression.learnMove(data, m, "bodyslam", e) // growl (status 0) is the weakest
        assertEquals(listOf("tackle", "bodyslam", "vinewhip", "lick"), m.moves)
        val l = e.single() as GameEvent.MoveLearned
        assertEquals("Growl", l.forgot); assertEquals("Body Slam", l.move)
    }

    @Test fun learnMoveTieReplacesEarliestSlot() {
        val (_, m) = st(1, 5, listOf("growl", "swordsdance", "tackle", "lick"))
        Progression.learnMove(data, m, "vinewhip", ev())
        assertEquals(listOf("vinewhip", "swordsdance", "tackle", "lick"), m.moves)
    }

    @Test fun learnMoveSkipsWeaker() {
        val (_, m) = st(1, 5, listOf("bodyslam", "thunderbolt", "earthquake", "shadowball"))
        val e = ev()
        Progression.learnMove(data, m, "tackle", e)
        assertEquals(listOf("bodyslam", "thunderbolt", "earthquake", "shadowball"), m.moves)
        assertEquals(GameEvent.MoveSkipped(m.uid, m.name, "Tackle"), e.single())
        // a status move never beats an attack either
        e.clear()
        Progression.learnMove(data, m, "growl", e)
        assertTrue(e.single() is GameEvent.MoveSkipped)
    }

    @Test fun learnMoveScoresSpecialAndMultihit() {
        // fixed damage counts as 60, multi-hit x3 (25 -> 75)
        val (_, m) = st(1, 5, listOf("tackle", "bodyslam", "thunderbolt", "earthquake"))
        Progression.learnMove(data, m, "dragonrage", ev()) // 60 > tackle 40
        assertEquals("dragonrage", m.moves[0])
        val (_, m2) = st(1, 5, listOf("tackle", "bodyslam", "thunderbolt", "earthquake"))
        Progression.learnMove(data, m2, "bulletseed", ev()) // 75 > 40
        assertEquals("bulletseed", m2.moves[0])
        val (_, m3) = st(1, 5, listOf("bodyslam", "thunderbolt", "earthquake", "shadowball"))
        val e = ev()
        Progression.learnMove(data, m3, "seismictoss", e) // 60 < 80
        assertTrue(e.single() is GameEvent.MoveSkipped)
    }

    // ---- EVs ----

    @Test fun evCaps() {
        val (_, m) = st()
        Progression.addEvs(m, Stats(0, 200, 0, 0, 0, 0))
        Progression.addEvs(m, Stats(0, 100, 0, 0, 0, 0))
        assertEquals(252, m.evs.atk)
        m.evs = Stats(252, 252, 0, 0, 0, 0) // 504
        Progression.addEvs(m, Stats(0, 0, 10, 10, 0, 0))
        assertEquals(6, m.evs.def); assertEquals(0, m.evs.spa)
        assertEquals(510, m.evs.total)
    }

    @Test fun machoBraceDoublesAndPowerItemAdds() {
        val (_, m) = st()
        m.heldItem = "macho-brace"
        Progression.addEvs(m, Stats(1, 0, 2, 0, 0, 0))
        assertEquals(Stats(2, 0, 4, 0, 0, 0), m.evs)
        val (_, p) = st()
        p.heldItem = "power-lens"
        Progression.addEvs(p, Stats(0, 1, 0, 0, 0, 0))
        assertEquals(Stats(0, 1, 0, 8, 0, 0), p.evs)
        val (_, q) = st()
        q.heldItem = "power-anklet"
        Progression.addEvs(q, Stats.ZERO)
        assertEquals(Stats(0, 0, 0, 0, 0, 8), q.evs)
    }

    // ---- rewardDefeat ----

    private fun wild16(level: Int = 10) = TestData.mon(data, 16, level, uid = "wild")

    @Test fun rewardDefeatPlain() {
        val (s, m) = st(1, 5)
        val e = ev()
        Progression.rewardDefeat(data, s, m, wild16(10), noon, Random(1), e)
        val d = e.first() as GameEvent.Defeated
        assertEquals(72, d.xp); assertEquals(16, d.species)
        val x = e.of<GameEvent.XpGained>().single()
        assertEquals(72, x.xp)
        assertEquals(1, m.defeats)
        assertEquals(1, m.evs.hp) // Birdie fixture yields 1 HP EV
        assertTrue(m.friendship in 5..9)
    }

    @Test fun friendshipGainSeededAndSootheBell() {
        val (s, m) = st(1, 5)
        val expected = Tuning.FRIENDSHIP_GAIN.random(Random(7))
        Progression.rewardDefeat(data, s, m, wild16(), noon, Random(7), ev())
        assertEquals(expected, m.friendship)
        val (s2, m2) = st(1, 5)
        m2.heldItem = "soothe-bell"
        Progression.rewardDefeat(data, s2, m2, wild16(), noon, Random(7), ev())
        assertEquals((expected * 1.5).toInt(), m2.friendship)
    }

    @Test fun expShareSplit() {
        val (s, m) = st(1, 5)
        val h = TestData.mon(data, 16, 5, uid = "holder")
        s.mons += h; s.team += h.uid
        s.settings.xpShareUid = "holder"
        val e = ev()
        Progression.rewardDefeat(data, s, m, wild16(10), noon, Random(1), e)
        val xs = e.of<GameEvent.XpGained>()
        assertEquals(2, xs.size)
        assertEquals("main", xs[0].uid); assertEquals(36, xs[0].xp)
        assertEquals("holder", xs[1].uid); assertEquals(36, xs[1].xp)
        assertEquals(72, (e.first() as GameEvent.Defeated).xp)
        assertEquals(0, h.defeats); assertEquals(Stats.ZERO, h.evs); assertEquals(0, h.friendship)
    }

    @Test fun expShareHolderIsMainOrUnknownGivesMainAll() {
        val (s, m) = st(1, 5)
        s.settings.xpShareUid = "main"
        val e = ev()
        Progression.rewardDefeat(data, s, m, wild16(10), noon, Random(1), e)
        assertEquals(72, e.of<GameEvent.XpGained>().single().xp)
        val (s2, m2) = st(1, 5)
        s2.settings.xpShareUid = "ghost"
        val e2 = ev()
        Progression.rewardDefeat(data, s2, m2, wild16(10), noon, Random(1), e2)
        assertEquals(72, e2.of<GameEvent.XpGained>().single().xp)
    }

    @Test fun luckyEggOnMainAndHolder() {
        val (s, m) = st(1, 5)
        m.heldItem = "lucky-egg"
        val e = ev()
        Progression.rewardDefeat(data, s, m, wild16(10), noon, Random(1), e)
        assertEquals(108, e.of<GameEvent.XpGained>().single().xp) // 72 * 1.5
        assertEquals(72, (e.first() as GameEvent.Defeated).xp)

        val (s2, m2) = st(1, 5)
        val h = TestData.mon(data, 16, 5, uid = "holder")
        h.heldItem = "lucky-egg"
        s2.mons += h
        s2.settings.xpShareUid = "holder"
        val e2 = ev()
        Progression.rewardDefeat(data, s2, m2, wild16(10), noon, Random(1), e2)
        val xs = e2.of<GameEvent.XpGained>()
        assertEquals(36, xs[0].xp); assertEquals(54, xs[1].xp)
    }

    @Test fun rewardDefeatLevelEvolves() {
        val (s, m) = st(1, 15)
        m.moves.clear(); m.moves += "tackle"
        val e = ev()
        // wild lv 50 Mindlord (baseExp 340): xp = 2429, enough for 15 -> 16 (721)
        Progression.rewardDefeat(data, s, m, TestData.mon(data, 150, 50, uid = "wild"), noon, Random(1), e)
        assertEquals(2, m.species)
        assertEquals("Leafbud", m.name)
        val ei = e.indexOfFirst { it is GameEvent.Evolved }
        assertTrue(ei > e.indexOfLast { it is GameEvent.LevelUp })
        assertTrue(16 in s.badges)
        assertEquals(1, s.evolutions)
    }

    @Test fun rewardDefeatFriendshipEvolution() {
        val (s, m) = st(172, 5, listOf("tackle"))
        m.friendship = 159
        val e = ev()
        Progression.rewardDefeat(data, s, m, wild16(1), noon, Random(1), e) // +5..9 -> >= 164
        assertEquals(25, m.species)
        assertTrue(e.any { it is GameEvent.Evolved })
    }

    @Test fun rewardDefeatHolderEvolves() {
        val (s, m) = st(16, 5)
        val h = TestData.mon(data, 1, 15, uid = "holder")
        s.mons += h
        s.settings.xpShareUid = "holder"
        val e = ev()
        Progression.rewardDefeat(data, s, m, TestData.mon(data, 150, 50, uid = "wild"), noon, Random(1), e)
        assertEquals(2, h.species)
        assertEquals("holder", e.of<GameEvent.Evolved>().single().uid)
    }

    // ---- level evolution ----

    @Test fun levelEvolutionDueAndNot() {
        val (s, m) = st(1, 15)
        assertNull(Progression.levelEvolution(data, s, m, noon))
        m.level = 16
        assertEquals(2, Progression.levelEvolution(data, s, m, noon)?.to)
        val (_, noEvo) = st(3, 50)
        assertNull(Progression.levelEvolution(data, s, noEvo, noon))
    }

    @Test fun everstoneBlocksAll() {
        val (s, m) = st(1, 16)
        m.heldItem = "everstone"
        assertNull(Progression.levelEvolution(data, s, m, noon))
        val (_, f) = st(172, 5)
        f.heldItem = "everstone"; f.friendship = 300
        assertNull(Progression.friendshipEvolution(data, s, f, noon))
        val (_, sp) = st(25, 5)
        sp.heldItem = "everstone"
        assertNull(Progression.itemEvolution(data, sp, "thunder-stone"))
    }

    private fun custom(vararg evo: Evolution): Triple<GameData, GameState, Mon> {
        val d = TestData.data(listOf(
            TestData.species(10, "Base", evolutions = evo.toList()),
            TestData.species(11, "Next", prevo = 10),
            TestData.species(12, "Dark", types = listOf(Type.DARK)),
        ))
        val s = TestData.state(d, 10, 30, listOf("tackle", "mimic"))
        return Triple(d, s, s.main!!)
    }

    @Test fun genderRule() {
        val (d, s, m) = custom(Evolution(11, EvoKind.LEVEL, level = 20, gender = 'M'))
        m.gender = 'F'
        assertNull(Progression.levelEvolution(d, s, m, noon))
        m.gender = 'M'
        assertEquals(11, Progression.levelEvolution(d, s, m, noon)?.to)
    }

    @Test fun timeRule() {
        val (d, s, m) = custom(Evolution(11, EvoKind.LEVEL, level = 20, time = "night"))
        assertNull(Progression.levelEvolution(d, s, m, noon))
        assertEquals(11, Progression.levelEvolution(d, s, m, midnight)?.to)
        val (d2, s2, m2) = custom(Evolution(11, EvoKind.LEVEL, level = 20, time = "day"))
        assertEquals(11, Progression.levelEvolution(d2, s2, m2, noon)?.to)
        assertNull(Progression.levelEvolution(d2, s2, m2, midnight))
    }

    @Test fun isDayBoundaries() {
        assertFalse(Progression.isDay(at(5)))
        assertTrue(Progression.isDay(at(6)))
        assertTrue(Progression.isDay(at(17)))
        assertFalse(Progression.isDay(at(18)))
    }

    @Test fun statConditions() {
        val (d, s, m) = custom(Evolution(11, EvoKind.LEVEL, level = 20, condition = "with an Atk stat > its Def stat"))
        // species 10 has equal base atk/def, so make the IVs decide
        val high = Mon(m.uid, 10, m.name, 30, 0, Stats(15, 31, 0, 15, 15, 15), Stats.ZERO, Nature.HARDY, 'M', false, "x", m.moves, 1)
        s.mons[0] = high
        assertNotNull(Progression.levelEvolution(d, s, high, noon))
        val (d2, s2, _) = custom(Evolution(11, EvoKind.LEVEL, level = 20, condition = "with an Atk stat < its Def stat"))
        val low = Mon("main", 10, "Base", 30, 0, Stats(15, 31, 0, 15, 15, 15), Stats.ZERO, Nature.HARDY, 'M', false, "x", mutableListOf("tackle"), 1)
        assertNull(Progression.levelEvolution(d2, s2, low, noon))
        val (d3, s3, _) = custom(Evolution(11, EvoKind.LEVEL, level = 20, condition = "with an Atk stat equal to its Def stat"))
        val eq = Mon("main", 10, "Base", 30, 0, Stats(15, 15, 15, 15, 15, 15), Stats.ZERO, Nature.HARDY, 'M', false, "x", mutableListOf("tackle"), 1)
        assertNotNull(Progression.levelEvolution(d3, s3, eq, noon))
        assertNull(Progression.levelEvolution(d3, s3, high, noon))
    }

    @Test fun darkTypeInPartyAndUnknownCondition() {
        val (d, s, m) = custom(Evolution(11, EvoKind.LEVEL, level = 20, condition = "with a Dark-type in the party"))
        assertNull(Progression.levelEvolution(d, s, m, noon))
        val dark = TestData.mon(d, 12, 5, uid = "dark")
        s.mons += dark; s.team += dark.uid
        assertNotNull(Progression.levelEvolution(d, s, m, noon))
        val (d2, s2, m2) = custom(Evolution(11, EvoKind.LEVEL, level = 20, condition = "during rain"))
        assertNotNull(Progression.levelEvolution(d2, s2, m2, noon))
    }

    @Test fun levelMoveEvolution() {
        val (d, s, m) = custom(Evolution(11, EvoKind.LEVEL_MOVE, move = "mimic"))
        assertEquals(11, Progression.levelEvolution(d, s, m, noon)?.to)
        m.moves.remove("mimic")
        assertNull(Progression.levelEvolution(d, s, m, noon))
    }

    // ---- friendship evolution ----

    @Test fun friendshipEvolutionThresholdAndTime() {
        val (s, m) = st(172, 5)
        m.friendship = 159
        assertNull(Progression.friendshipEvolution(data, s, m, noon))
        m.friendship = 160
        assertEquals(25, Progression.friendshipEvolution(data, s, m, noon)?.to)
        val (d, s2, m2) = custom(Evolution(11, EvoKind.FRIENDSHIP, time = "day", minFriendship = 220))
        m2.friendship = 200
        assertNull(Progression.friendshipEvolution(d, s2, m2, noon))
        m2.friendship = 220
        assertEquals(11, Progression.friendshipEvolution(d, s2, m2, noon)?.to)
        assertNull(Progression.friendshipEvolution(d, s2, m2, midnight))
    }

    @Test fun friendshipEvolutionNeedsFairyMove() {
        val (s, m) = st(133, 20, listOf("tackle"))
        m.friendship = 200
        assertNull(Progression.friendshipEvolution(data, s, m, noon))
        m.moves += "moonblast"
        assertEquals(700, Progression.friendshipEvolution(data, s, m, noon)?.to)
    }

    // ---- item evolution ----

    @Test fun itemEvolutions() {
        val (_, pika) = st(25, 20)
        assertEquals(26, Progression.itemEvolution(data, pika, "thunder-stone")?.to)
        assertNull(Progression.itemEvolution(data, pika, "moon-stone"))
        assertNull(Progression.itemEvolution(data, pika, "linking-cord"))
        val (_, blink) = st(64, 20)
        assertEquals(65, Progression.itemEvolution(data, blink, "linking-cord")?.to)
        assertNull(Progression.itemEvolution(data, blink, "thunder-stone"))
        val (_, eevee) = st(133, 20)
        assertEquals(134, Progression.itemEvolution(data, eevee, "water-stone")?.to)
        assertEquals(135, Progression.itemEvolution(data, eevee, "thunder-stone")?.to)
    }

    @Test fun tradeWithItemAndOther() {
        val (d, _, m) = custom(Evolution(11, EvoKind.TRADE, item = "kings-rock"))
        assertEquals(11, Progression.itemEvolution(d, m, "kings-rock")?.to)
        assertNull(Progression.itemEvolution(d, m, "linking-cord"))
        val (d2, _, m2) = custom(Evolution(11, EvoKind.OTHER))
        assertEquals(11, Progression.itemEvolution(d2, m2, "linking-cord")?.to)
        assertNull(Progression.itemEvolution(d2, m2, "kings-rock"))
    }

    // ---- evolve ----

    @Test fun evolveRefillsHpRenamesAndLearnsL0() {
        val (s, m) = st(1, 16, listOf("tackle", "growl"))
        m.hp = 1
        val e = ev()
        Progression.evolve(data, s, m, data.sp(1).evolutions[0], Random(1), e)
        assertEquals(2, m.species); assertEquals("Leafbud", m.name)
        assertEquals(StatCalc.maxHp(data, m), m.hp)
        assertEquals(16, m.level)
        val ei = e.indexOfFirst { it is GameEvent.Evolved }
        val mi = e.indexOfFirst { it is GameEvent.MoveLearned }
        assertTrue(ei in 0 until mi)
        assertTrue("bulletseed" in m.moves)
        assertTrue(2 in s.seen && 2 in s.caught)
        assertEquals(1, s.evolutions)
        assertTrue(16 in s.badges)
        val ev0 = e.of<GameEvent.Evolved>().single()
        assertEquals("Leafling", ev0.from); assertEquals("Leafbud", ev0.to); assertEquals(2, ev0.toSpecies)
    }

    // ---- Catching ----

    private fun battleOf(species: Int, tier: Tier = Tier.NORMAL, shiny: Boolean = false): Battle {
        val w = TestData.mon(data, species, 10, uid = "w$species")
        w.shiny = shiny
        return Battle(w, tier)
    }

    @Test fun isProtectedKinds() {
        val s = Settings()
        assertTrue(Catching.isProtected(s, data.sp(150), Tier.NORMAL))
        assertTrue(Catching.isProtected(s, data.sp(151), Tier.NORMAL))
        assertTrue(Catching.isProtected(s, data.sp(16), Tier.LEGENDARY))
        assertTrue(Catching.isProtected(s, data.sp(16), Tier.MYTHICAL))
        assertTrue(Catching.isProtected(s, data.sp(16), Tier.ULTRA))
        assertFalse(Catching.isProtected(s, data.sp(16), Tier.NORMAL))
        s.protectLegendary = false; s.protectMythical = false; s.protectUltra = false
        assertFalse(Catching.isProtected(s, data.sp(150), Tier.LEGENDARY))
        assertFalse(Catching.isProtected(s, data.sp(151), Tier.MYTHICAL))
        assertFalse(Catching.isProtected(s, data.sp(16), Tier.ULTRA))
    }

    @Test fun decideModes() {
        val s = TestData.state(data, 25, 20)
        s.caught += 16
        val owned = battleOf(16)
        val fresh = battleOf(19)
        s.settings.autoMode = 1
        assertEquals(Outcome.CATCH, Catching.decide(data, s, owned))
        s.settings.autoMode = 2
        assertEquals(Outcome.DEFEAT, Catching.decide(data, s, owned))
        assertEquals(Outcome.DEFEAT, Catching.decide(data, s, fresh))
        s.settings.autoMode = 3
        assertEquals(Outcome.DEFEAT, Catching.decide(data, s, owned))
        assertEquals(Outcome.CATCH, Catching.decide(data, s, fresh))
    }

    @Test fun decideShinyProtectedWishlistOverride() {
        val s = TestData.state(data, 25, 20)
        s.caught += 16
        s.settings.autoMode = 2
        assertEquals(Outcome.CATCH, Catching.decide(data, s, battleOf(16, shiny = true)))
        assertEquals(Outcome.CATCH, Catching.decide(data, s, battleOf(150)))
        assertEquals(Outcome.CATCH, Catching.decide(data, s, battleOf(16, Tier.ULTRA)))
        // wishlist (Pikachu 25 in default list) beats everything, including a DEFEAT override
        s.settings.autoMode = 2
        val pika = battleOf(25)
        pika.override = Outcome.DEFEAT
        assertEquals(Outcome.CATCH, Catching.decide(data, s, pika))
        // override beats the mode
        val b = battleOf(16)
        b.override = Outcome.CATCH
        assertEquals(Outcome.CATCH, Catching.decide(data, s, b))
        s.settings.autoMode = 1
        val c = battleOf(16)
        c.override = Outcome.DEFEAT
        assertEquals(Outcome.DEFEAT, Catching.decide(data, s, c))
    }

    @Test fun ballChances() {
        assertEquals(8, Catching.ballChance("poke-ball", listOf(Type.NORMAL)))
        assertEquals(12, Catching.ballChance("great-ball", listOf(Type.WATER)))
        assertEquals(100, Catching.ballChance("master-ball", listOf(Type.WATER)))
        assertEquals(22, Catching.ballChance("net-ball", listOf(Type.WATER)))
        assertEquals(22, Catching.ballChance("net-ball", listOf(Type.GRASS, Type.BUG)))
        assertEquals(12, Catching.ballChance("net-ball", listOf(Type.NORMAL)))
        assertEquals(21, Catching.ballChance("dive-ball", listOf(Type.FLYING, Type.WATER)))
        assertEquals(11, Catching.ballChance("dive-ball", listOf(Type.BUG)))
        assertEquals(0, Catching.ballChance("mystery-ball", listOf(Type.WATER)))
    }

    @Test fun addToCollectionResets() {
        val s = TestData.state(data, 25, 20)
        val w = TestData.mon(data, 16, 12, uid = "wild")
        w.xp = 99; w.friendship = 50; w.heldItem = "lucky-egg"; w.evs = Stats(4, 4, 4, 4, 4, 4); w.hp = 2; w.shiny = true; w.defeats = 3
        val e = ev()
        val c = Catching.addToCollection(data, s, w, Tier.NORMAL, "poke-ball", 555L, e)
        assertNotEquals("wild", c.uid)
        assertEquals(Stats.ZERO, c.evs); assertEquals(0, c.xp); assertEquals(0, c.friendship); assertNull(c.heldItem)
        assertEquals(StatCalc.maxHp(data, c), c.hp)
        assertEquals(555L, c.caughtAt); assertEquals(12, c.level); assertTrue(c.shiny)
        assertEquals(0, c.defeats)
        assertEquals(w.ivs, c.ivs); assertEquals(w.nature, c.nature); assertEquals(w.moves, c.moves)
        assertTrue(c.uid in s.team)
        assertTrue(16 in s.seen && 16 in s.caught)
        assertEquals(1, s.catches)
        val ce = e.of<GameEvent.Caught>().single()
        assertTrue(ce.newSpecies); assertTrue(ce.toTeam); assertEquals("poke-ball", ce.ball)
        assertEquals(c.uid, ce.uid)
        assertTrue(7 in s.badges && 17 in s.badges)
        // same species again is not new
        val e2 = ev()
        Catching.addToCollection(data, s, TestData.mon(data, 16, 5, uid = "w2"), Tier.NORMAL, null, 1L, e2)
        assertFalse(e2.of<GameEvent.Caught>().single().newSpecies)
        assertEquals(0, e2.of<GameEvent.BadgeEarned>().size)
    }

    @Test fun addToCollectionBoxWhenTeamFull() {
        val s = TestData.state(data, 25, 20)
        for (i in 1..5) { val m = TestData.mon(data, 16, 5, uid = "t$i"); s.mons += m; s.team += m.uid }
        assertEquals(6, s.team.size)
        val e = ev()
        val c = Catching.addToCollection(data, s, TestData.mon(data, 19, 5, uid = "w"), Tier.NORMAL, null, 1L, e)
        assertFalse(c.uid in s.team)
        assertTrue(s.mon(c.uid) != null)
        assertFalse(e.of<GameEvent.Caught>().single().toTeam)
        assertEquals(6, s.team.size)
    }

    @Test fun tierBadges() {
        val cases = mapOf(Tier.BABY to 18, Tier.ULTRA to 8, Tier.LEGENDARY to 9, Tier.MYTHICAL to 10, Tier.NORMAL to 17)
        for ((tier, badge) in cases) {
            val s = TestData.state(data, 25, 20)
            Catching.addToCollection(data, s, TestData.mon(data, 16, 5, uid = "w"), tier, null, 1L, ev())
            assertTrue("tier $tier", badge in s.badges && 7 in s.badges)
        }
    }
}
