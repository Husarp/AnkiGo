package com.husarp.ankigo.game

import org.junit.Assert.assertEquals
import org.junit.Test

class StatRangeTest {
    // Hand-computed for a made-up base of 100 at Lv 100:
    //  HP    min 10+100+floor(200)            = 310   max 110+floor((200+31+63))            = 404
    //  other min floor((5+200)*0.9)=184       max floor((5+294)*1.1)=floor(328.9)=328
    @Test fun hpRangeAtLv100() {
        assertEquals(310..404, StatCalc.range(100, Stat.HP))
    }

    @Test fun otherStatRangeUsesNature() {
        assertEquals(184..328, StatCalc.range(100, Stat.ATK))
        assertEquals(184..328, StatCalc.range(100, Stat.SPE))
    }

    @Test fun smallBaseAndLowerLevel() {
        // base 2, Lv 100: HP 110+4=114 .. 110+(4+31+63)=208 ; other (base 1) floor(7*0.9)=6 .. floor((5+96)*1.1)=111
        // R17C-FIX1: base 1 is Shedinja's HP (always 1, see R17CFixBShedinjaTest), so the small HP base here is 2
        assertEquals(114..208, StatCalc.range(2, Stat.HP))
        assertEquals(1..1, StatCalc.range(1, Stat.HP))
        assertEquals(6..111, StatCalc.range(1, Stat.DEF))
        // Lv 50, base 100: HP 10+50+100=160 .. 60+floor(294/2)=207
        assertEquals(160..207, StatCalc.range(100, Stat.HP, 50))
    }

    @Test fun rangeBracketsEveryRealMon() {
        val data = TestData.world()
        val sp = data.sp(25)
        for (nat in Nature.entries) {
            val m = TestData.mon(data, 25, 100)
            val s = StatCalc.stats(sp, Mon(m.uid, m.species, m.name, 100, 0, m.ivs, m.evs, nat, 'M', false, "x", mutableListOf("tackle"), 1))
            Stat.entries.forEach { st ->
                val v = when (st) { Stat.HP -> s.hp; Stat.ATK -> s.atk; Stat.DEF -> s.def; Stat.SPA -> s.spa; Stat.SPD -> s.spd; Stat.SPE -> s.spe }
                val r = StatCalc.range(sp.base[st], st)
                assert(v in r) { "$nat $st $v not in $r" }
            }
        }
    }
}
