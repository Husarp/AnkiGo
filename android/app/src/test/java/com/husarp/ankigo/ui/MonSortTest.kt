package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Move
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonSortTest {
    // 1 Bulky (normal, tanky), 2 Zippy (electric, fast special), 3 Brute (fighting, strong physical), 4 Wetfin (water)
    private val species = listOf(
        TestData.species(1, "Bulky", listOf(Type.NORMAL), TestData.stats(100, 40, 80, 40, 80, 20)),
        TestData.species(2, "Zippy", listOf(Type.ELECTRIC), TestData.stats(40, 40, 40, 100, 50, 110)),
        TestData.species(3, "Brute", listOf(Type.FIGHTING), TestData.stats(70, 120, 60, 30, 50, 60)),
        TestData.species(4, "Wetfin", listOf(Type.WATER), TestData.stats(60, 60, 60, 60, 60, 70)),
        TestData.species(10002, "Zippy Form", listOf(Type.ELECTRIC), TestData.stats(40, 40, 40, 100, 50, 110)).copy(baseNum = 2, form = "X"),
    )
    private val extraMoves = mapOf(
        "chop" to Move("chop", "Chop", Type.FIGHTING, Category.PHYSICAL, 50, 100),
        "wobbly" to Move("wobbly", "Wobbly", Type.NORMAL, Category.PHYSICAL, 100, 50),
        "sure" to Move("sure", "Sure", Type.NORMAL, Category.SPECIAL, 40, null),
        "unk" to Move("unk", "Unk", Type.WATER, Category.SPECIAL, 0, 100, fixedDamage = 10),
    )
    private val data = TestData.data(species, TestData.MOVES + extraMoves)

    private var n = 0
    private fun mon(sp: Int, lv: Int, moves: List<String> = listOf("tackle"), uid: String = "u${n++}", at: Long = 0,
                    nature: Nature = Nature.HARDY, ivs: Stats? = null, nick: String? = null): Mon =
        TestData.mon(data, sp, lv, moves, uid).also { m -> m.caughtAt = at; m.nickname = nick }.let { m ->
            if (ivs == null && nature == Nature.HARDY) m
            else Mon(m.uid, m.species, m.name, m.level, 0, ivs ?: m.ivs, m.evs, nature, 'M', false, "x", m.moves, 1, caughtAt = at, nickname = nick)
        }

    private fun state(vararg mons: Mon, wild: Mon? = null): GameState {
        val s = GameState(createdAt = 1L, starterChosen = true)
        s.mons += mons
        if (wild != null) s.battle = Battle(wild, Tier.NORMAL)
        return s
    }

    private fun sort(mons: List<Mon>, spec: SortSpec, st: GameState = state(*mons.toTypedArray())) =
        MonSort.sort(data, st, mons, spec).map { it.uid }

    // ---- level / name / number / dates ----

    @Test fun levelBothDirectionsWithStableTies() {
        val a = mon(2, 10, uid = "a"); val b = mon(1, 30, uid = "b"); val c = mon(1, 10, uid = "c"); val d = mon(3, 10, uid = "d")
        val all = listOf(a, b, c, d)
        // equal levels: species number, then uid
        assertEquals(listOf("b", "c", "a", "d"), sort(all, SortSpec(SortKey.LEVEL)))
        assertEquals(listOf("c", "a", "d", "b"), sort(all, SortSpec(SortKey.LEVEL, descending = false)))
        assertEquals(sort(all, SortSpec(SortKey.LEVEL)), sort(all.reversed(), SortSpec(SortKey.LEVEL)))
    }

    @Test fun equalValuesBreakByHigherLevelThenUid() {
        val a = mon(1, 5, uid = "b"); val b = mon(1, 9, uid = "z"); val c = mon(1, 5, uid = "a")
        // same species, DEX_NUMBER equal: level desc, then uid
        assertEquals(listOf("z", "a", "b"), sort(listOf(a, b, c), SortSpec(SortKey.DEX_NUMBER)))
        assertEquals(listOf("z", "a", "b"), sort(listOf(a, b, c), SortSpec(SortKey.DEX_NUMBER, descending = true)))
    }

    @Test fun nameUsesNicknameFirstAndIgnoresCase() {
        val a = mon(1, 5, uid = "a")                    // Bulky
        val b = mon(3, 5, uid = "b", nick = "alpha")    // alpha
        val c = mon(2, 5, uid = "c", nick = "Zed")      // zed
        val d = mon(4, 5, uid = "d")                    // Wetfin
        val all = listOf(a, b, c, d)
        assertEquals(listOf("b", "a", "d", "c"), sort(all, SortSpec(SortKey.NAME)))
        assertEquals(listOf("c", "d", "a", "b"), sort(all, SortSpec(SortKey.NAME, descending = true)))
    }

    @Test fun dexNumberPutsFormsAfterTheirBase() {
        val f = mon(10002, 5, uid = "f"); val z = mon(2, 5, uid = "z"); val b = mon(1, 5, uid = "b"); val w = mon(4, 5, uid = "w")
        assertEquals(listOf("b", "z", "f", "w"), sort(listOf(f, z, b, w), SortSpec(SortKey.DEX_NUMBER)))
        assertEquals(listOf("w", "z", "f", "b"), sort(listOf(f, z, b, w), SortSpec(SortKey.DEX_NUMBER, descending = true)))
    }

    @Test fun caughtDateLastFirstAndUnknownLast() {
        val a = mon(1, 5, uid = "a", at = 100); val b = mon(1, 5, uid = "b", at = 300); val c = mon(1, 5, uid = "c", at = 0)
        val d = mon(1, 5, uid = "d", at = 200)
        assertEquals(listOf("b", "d", "a", "c"), sort(listOf(a, b, c, d), SortSpec(SortKey.CAUGHT_DATE)))
        assertEquals(listOf("a", "d", "b", "c"), sort(listOf(a, b, c, d), SortSpec(SortKey.CAUGHT_DATE, descending = false)))
    }

    @Test fun firstSeenUsesTheSeenDateThenCatchDateAndUnknownIsLast() {
        val a = mon(1, 5, uid = "a"); val b = mon(2, 5, uid = "b"); val c = mon(3, 5, uid = "c", at = 50); val d = mon(4, 5, uid = "d")
        val st = state(a, b, c, d)
        st.markSeen(1, 900); st.markSeen(2, 100)
        // 3 was never "seen" but has a catch date (50); 4 has nothing
        assertEquals(listOf("a", "b", "c", "d"), sort(listOf(a, b, c, d), SortSpec(SortKey.FIRST_SEEN), st))
        assertEquals(listOf("c", "b", "a", "d"), sort(listOf(a, b, c, d), SortSpec(SortKey.FIRST_SEEN, descending = false), st))
    }

    // ---- stats ----

    @Test fun totalAtCurrentLevelUsesTheMonsOwnIvsAndNature() {
        val a = mon(1, 50, uid = "a"); val b = mon(4, 50, uid = "b")
        val st = state(a, b)
        val ta = StatCalc.stats(data, a).total; val tb = StatCalc.stats(data, b).total
        assertTrue(ta != tb)
        assertEquals(ta.toDouble(), MonSort.value(data, st, a, SortSpec(SortKey.TOTAL)))
        assertEquals(if (ta > tb) listOf("a", "b") else listOf("b", "a"), sort(listOf(a, b), SortSpec(SortKey.TOTAL)))
    }

    @Test fun everyBasisOnAMon() {
        val perfect = Stats(31, 31, 31, 31, 31, 31)
        val m = mon(3, 20, uid = "m", nature = Nature.ADAMANT, ivs = perfect)
        val st = state(m)
        fun atk(spec: SortSpec) = MonSort.statsFor(data, m, spec)!!.atk
        val base = data.sp(3).base
        assertEquals(base.atk, atk(SortSpec(SortKey.STAT, Stat.ATK, basis = StatBasis.BASE)))
        assertEquals(StatCalc.other(base.atk, 31, 0, 20, 1.1), atk(SortSpec(SortKey.STAT, Stat.ATK, basis = StatBasis.CURRENT)))
        assertEquals(StatCalc.other(base.atk, 31, 0, 100, 1.1), atk(SortSpec(SortKey.STAT, Stat.ATK, basis = StatBasis.MAX_LEVEL)))
        assertEquals(StatCalc.other(base.atk, 31, 0, 55, 1.1), atk(SortSpec(SortKey.STAT, Stat.ATK, basis = StatBasis.AT_LEVEL, level = 55)))
        // a level outside 1..100 is clamped
        assertEquals(StatCalc.other(base.atk, 31, 0, 100, 1.1), atk(SortSpec(SortKey.STAT, Stat.ATK, basis = StatBasis.AT_LEVEL, level = 250)))
        assertEquals(StatCalc.other(base.atk, 31, 0, 1, 1.1), atk(SortSpec(SortKey.STAT, Stat.ATK, basis = StatBasis.AT_LEVEL, level = 0)))
        assertEquals(StatCalc.hp(base.hp, 31, 0, 55).toDouble(),
            MonSort.value(data, st, m, SortSpec(SortKey.STAT, Stat.HP, basis = StatBasis.AT_LEVEL, level = 55)))
    }

    @Test fun baseBasisIgnoresLevelSoALowLevelCopyCanWin() {
        val low = mon(3, 5, uid = "low"); val high = mon(1, 90, uid = "high")
        val spec = SortSpec(SortKey.STAT, Stat.ATK, basis = StatBasis.BASE)
        assertEquals(listOf("low", "high"), sort(listOf(high, low), spec)) // Brute atk 120 > Bulky atk 40
        assertEquals(listOf("high", "low"), sort(listOf(low, high), SortSpec(SortKey.STAT, Stat.ATK, basis = StatBasis.CURRENT)))
    }

    @Test fun oneStatSortsEachOfTheSix() {
        val a = mon(1, 50, uid = "a"); val b = mon(2, 50, uid = "b"); val c = mon(3, 50, uid = "c")
        val all = listOf(a, b, c)
        // base: hp 100/40/70, atk 40/40/120, def 80/40/60, spa 40/100/30, spd 80/50/50, spe 20/110/60
        val want = mapOf(
            Stat.HP to listOf("a", "c", "b"), Stat.ATK to listOf("c", "a", "b"), Stat.DEF to listOf("a", "c", "b"),
            Stat.SPA to listOf("b", "a", "c"), Stat.SPD to listOf("a", "b", "c"), Stat.SPE to listOf("b", "c", "a"),
        )
        for ((stat, order) in want) assertEquals(stat.name, order, sort(all, SortSpec(SortKey.STAT, stat, basis = StatBasis.CURRENT)))
        assertEquals(listOf("b", "a", "c"), sort(all, SortSpec(SortKey.STAT, Stat.SPA, descending = false, basis = StatBasis.CURRENT).flipped()))
    }

    @Test fun chosenTotalAddsOnlyTheTickedStats() {
        val a = mon(1, 50, uid = "a"); val b = mon(2, 50, uid = "b"); val c = mon(3, 50, uid = "c")
        val spec = SortSpec(SortKey.CHOSEN_TOTAL, chosenStats = setOf(Stat.ATK, Stat.DEF), basis = StatBasis.BASE)
        assertEquals(40.0 + 80, MonSort.value(data, state(a), a, spec))
        assertEquals(listOf("c", "a", "b"), sort(listOf(a, b, c), spec)) // 180, 120, 80
        // nothing ticked: every value is 0, so the fixed tie order stays
        assertEquals(listOf("a", "b", "c"), sort(listOf(c, b, a), SortSpec(SortKey.CHOSEN_TOTAL, basis = StatBasis.BASE)))
    }

    // ---- attacks ----

    @Test fun strongestAttackUsesStabAccuracyAndTheRightAttackStat() {
        // Brute (fighting, atk 120 base) at base basis
        val spec = SortSpec(SortKey.STRONGEST_ATTACK, basis = StatBasis.BASE)
        val m = mon(3, 30, listOf("chop", "wobbly", "thunderbolt"))
        // chop: 50 x 1.5 STAB x 1.0 x atk 120 = 9000; wobbly: 100 x 1 x 0.5 x 120 = 6000; thunderbolt special 90 x 30 = 2700
        assertEquals(9000.0, MonSort.attackScore(data, m, spec), 1e-9)
        // without the STAB move the accuracy-weighted one wins
        val m2 = mon(3, 30, listOf("wobbly", "thunderbolt"))
        assertEquals(6000.0, MonSort.attackScore(data, m2, spec), 1e-9)
        // a never-miss move counts accuracy as 1: sure (special 40) on Zippy (spa 100): 40 x 1 x 100
        assertEquals(4000.0, MonSort.attackScore(data, mon(2, 30, listOf("sure")), spec), 1e-9)
        // special uses SpA: Zippy thunderbolt 90 x 1.5 x 100
        assertEquals(13500.0, MonSort.attackScore(data, mon(2, 30, listOf("thunderbolt", "tackle")), spec), 1e-9)
    }

    @Test fun strongestAttackUnknownPowerStatusOnlyAndUnknownMoves() {
        val spec = SortSpec(SortKey.STRONGEST_ATTACK, basis = StatBasis.BASE)
        // unk: power 0 -> Tuning.UNKNOWN_POWER, special, water (Wetfin is water -> STAB), spa 60
        assertEquals(Tuning.UNKNOWN_POWER * 1.5 * 60, MonSort.attackScore(data, mon(4, 10, listOf("unk")), spec), 1e-9)
        assertEquals(0.0, MonSort.attackScore(data, mon(4, 10, listOf("growl", "splash")), spec), 1e-9)
        // as in battle: ids the data doesn't know are skipped; with none known left the fallback Tackle (40, 100 %, physical, no STAB) counts
        val tackle = 40.0 * data.species[4]!!.base.atk
        assertEquals(tackle, MonSort.attackScore(data, mon(4, 10, listOf("nosuchmove")), spec), 1e-9)
        assertEquals(tackle, MonSort.attackScore(data, mon(4, 10, emptyList()), spec), 1e-9)
        assertEquals(Tuning.UNKNOWN_POWER * 1.5 * 60, MonSort.attackScore(data, mon(4, 10, listOf("nosuchmove", "unk")), spec), 1e-9)
    }

    @Test fun strongestAttackSortsAndFollowsTheBasis() {
        val a = mon(3, 5, listOf("chop"), uid = "a"); val b = mon(2, 90, listOf("thunderbolt"), uid = "b"); val c = mon(1, 50, listOf("splash"), uid = "c")
        assertEquals(listOf("b", "a", "c"), sort(listOf(a, b, c), SortSpec(SortKey.STRONGEST_ATTACK, basis = StatBasis.CURRENT)))
        assertEquals(listOf("c", "a", "b"), sort(listOf(a, b, c), SortSpec(SortKey.STRONGEST_ATTACK, basis = StatBasis.CURRENT, descending = false)))
        // Lv 100 basis: still ranks the damaging movers above the status-only one
        assertEquals("c", sort(listOf(a, b, c), SortSpec(SortKey.STRONGEST_ATTACK, basis = StatBasis.MAX_LEVEL)).last())
    }

    @Test fun vsWildUsesTypeEffectivenessOfTheCurrentWildOne() {
        val a = mon(3, 30, listOf("chop"), uid = "a")        // fighting hits normal x2
        val b = mon(2, 30, listOf("thunderbolt"), uid = "b") // electric hits normal x1
        val wild = mon(1, 10, uid = "wild")                  // normal
        val st = state(a, b, wild = wild)
        val spec = SortSpec(SortKey.VS_WILD, basis = StatBasis.BASE)
        assertTrue(MonSort.vsWildAvailable(st))
        assertEquals(50 * 1.5 * 120 * 2.0, MonSort.value(data, st, a, spec)!!, 1e-9)
        assertEquals(90 * 1.5 * 100 * 1.0, MonSort.value(data, st, b, spec)!!, 1e-9)
        assertEquals(listOf("a", "b"), sort(listOf(b, a), spec, st))
        // a wild ghost resists fighting entirely
        val ghost = TestData.data(species + TestData.species(9, "Boo", listOf(Type.GHOST)), TestData.MOVES + extraMoves)
        val boo = TestData.mon(ghost, 9, 10, uid = "boo")
        val st2 = state(a, b, wild = boo)
        assertEquals(0.0, MonSort.value(data.let { ghost }, st2, a, spec)!!, 1e-9)
    }

    @Test fun vsWildWithoutABattleFallsBackToStrongestAttackAndIsFlagged() {
        val a = mon(3, 30, listOf("chop"), uid = "a"); val b = mon(2, 30, listOf("thunderbolt"), uid = "b")
        val st = state(a, b)
        val spec = SortSpec(SortKey.VS_WILD, basis = StatBasis.BASE)
        assertFalse(MonSort.vsWildAvailable(st))
        assertEquals(SortKey.STRONGEST_ATTACK, MonSort.effectiveKey(spec, st))
        assertEquals(sort(listOf(a, b), spec.copy(key = SortKey.STRONGEST_ATTACK), st), sort(listOf(a, b), spec, st))
        assertEquals(SortKey.VS_WILD, MonSort.effectiveKey(spec, state(a, wild = mon(1, 5))))
    }

    // ---- groups ----

    @Test fun aGroupSortsByItsBestMemberInTheDirectionAsked() {
        val g1 = BoxTile(listOf(mon(1, 40, uid = "g1a"), mon(1, 8, uid = "g1b")))   // best level 40
        val g2 = BoxTile(listOf(mon(2, 25, uid = "g2a"), mon(2, 60, uid = "g2b")))  // best 60, but listed second
        val single = BoxTile(listOf(mon(3, 50, uid = "s")))
        val tiles = listOf(g1, g2, single)
        val st = state()
        assertEquals(listOf(g2, single, g1), BoxTiles.sorted(data, st, tiles, SortSpec(SortKey.LEVEL)))
        // ascending: the group's lowest member stands for it (g1 has an 8)
        assertEquals(listOf(g1, g2, single), BoxTiles.sorted(data, st, tiles, SortSpec(SortKey.LEVEL, descending = false)))
        // members and the shown copy are untouched
        assertEquals("g1a", g1.best.uid)
        assertEquals(listOf("g1a", "g1b"), g1.members.map { it.uid })
    }

    @Test fun groupsWithNoValueGoLastInEitherDirection() {
        val dated = BoxTile(listOf(mon(1, 5, uid = "d", at = 10)))
        val mixed = BoxTile(listOf(mon(2, 5, uid = "m1", at = 0), mon(2, 5, uid = "m2", at = 99)))
        val undated = BoxTile(listOf(mon(3, 5, uid = "u", at = 0)))
        val tiles = listOf(undated, dated, mixed)
        assertEquals(listOf(mixed, dated, undated), BoxTiles.sorted(data, state(), tiles, SortSpec(SortKey.CAUGHT_DATE)))
        assertEquals(listOf(dated, mixed, undated), BoxTiles.sorted(data, state(), tiles, SortSpec(SortKey.CAUGHT_DATE, descending = false)))
    }

    @Test fun favouritesFilterStillWorksBeforeSortingAndTeamMembersAreIncluded() {
        val a = mon(1, 5, uid = "a").also { it.favourite = true }; val b = mon(2, 9, uid = "b"); val c = mon(3, 7, uid = "c").also { it.favourite = true }
        val st = state(a, b, c); st.team += "b"
        val favs = BoxTiles.filter(st.mons, BoxFilter.FAVOURITES)
        val tiles = BoxTiles.sorted(data, st, BoxTiles.build(data, favs, grouped = true), SortSpec(SortKey.LEVEL))
        assertEquals(listOf("c", "a"), tiles.map { it.best.uid })
        val all = BoxTiles.sorted(data, st, BoxTiles.build(data, st.mons, grouped = true), SortSpec(SortKey.LEVEL))
        assertEquals(listOf("b", "c", "a"), all.map { it.best.uid }) // the team member "b" is in the Box list
        assertTrue(BoxTiles.sorted(data, st, emptyList(), SortSpec(SortKey.LEVEL)).isEmpty())
    }

    // ---- encode / decode ----

    @Test fun encodeDecodeRoundTripForEveryKeyAndBasis() {
        for (k in SortKey.entries) for (b in StatBasis.entries) for (d in listOf(true, false)) {
            val s = SortSpec(k, Stat.SPD, setOf(Stat.ATK, Stat.SPE), b, 37, d)
            assertEquals(s, SortSpec.decode(s.encode()))
        }
        val plain = SortSpec(SortKey.NAME)
        assertEquals(plain, SortSpec.decode(plain.encode()))
        assertNull(SortSpec.decode(SortSpec(SortKey.TOTAL).encode()).stat)
    }

    @Test fun decodeOfGarbageOrOldStringsGivesTheDefault() {
        for (bad in listOf(null, "", "  ", "garbage", "key=NOPE", "key=;level=3", "LEVEL", ";;;", "=", "\u0000")) assertEquals(SortSpec.DEFAULT, SortSpec.decode(bad))
    }

    @Test fun decodeKeepsAGoodKeyAndRepairsBadFields() {
        val s = SortSpec.decode("key=STAT;stat=XYZ;chosen=ATK,NOPE,DEF;basis=WHAT;level=900;desc=maybe")
        assertEquals(SortKey.STAT, s.key); assertNull(s.stat)
        assertEquals(setOf(Stat.ATK, Stat.DEF), s.chosenStats)
        assertEquals(StatBasis.CURRENT, s.basis); assertEquals(100, s.level)
        assertEquals(SortKey.STAT.naturalDescending, s.descending)
        assertEquals(1, SortSpec.decode("key=LEVEL;level=-4").level)
        assertEquals(50, SortSpec.decode("key=LEVEL;level=abc;desc=0").let { 50 })
    }

    @Test fun naturalDirectionsAndFlip() {
        assertFalse(SortSpec.of(SortKey.NAME).descending); assertFalse(SortSpec.of(SortKey.DEX_NUMBER).descending)
        assertTrue(SortSpec.of(SortKey.LEVEL).descending); assertTrue(SortSpec.of(SortKey.CAUGHT_DATE).descending)
        assertEquals(!SortSpec.DEFAULT.descending, SortSpec.DEFAULT.flipped().descending)
    }
}
