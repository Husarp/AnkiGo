package com.husarp.ankigo.ui

import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MonTagsTest {
    private val data = TestData.world()
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val now = ms(2026, 10, 14, 12, 0)
    private fun kinds(st: com.husarp.ankigo.game.GameState, m: com.husarp.ankigo.game.Mon) = MonTags.of(data, st, m, now).map { it.kind }

    @Test fun mainOnlyForSlotZero() {
        val st = TestData.state(data, 19, 10)
        assertEquals(listOf(TagKind.MAIN), kinds(st, st.mons[0]))
        val other = TestData.mon(data, 16, 10, uid = "o"); st.mons += other
        assertTrue(kinds(st, other).isEmpty())
    }

    @Test fun fainted() {
        val st = TestData.state(data, 19, 10); st.mons[0].hp = 0
        assertEquals(listOf(TagKind.MAIN, TagKind.FAINTED), kinds(st, st.mons[0]))
    }

    @Test fun canEvolveByLevelFriendshipAndItem() {
        val st = TestData.state(data, 1, 16)
        assertTrue(TagKind.CAN_EVOLVE in kinds(st, st.mons[0]))
        val low = TestData.state(data, 1, 10)
        assertTrue(TagKind.CAN_EVOLVE !in kinds(low, low.mons[0]))
        val fr = TestData.state(data, 172, 10); fr.mons[0].friendship = 200
        assertTrue(TagKind.CAN_EVOLVE in kinds(fr, fr.mons[0]))
        val it = TestData.state(data, 25, 10)
        assertTrue(TagKind.CAN_EVOLVE !in kinds(it, it.mons[0]))
        it.bag["thunder-stone"] = 1
        assertTrue(TagKind.CAN_EVOLVE in kinds(it, it.mons[0]))
        it.bag["thunder-stone"] = 0
        assertTrue(TagKind.CAN_EVOLVE !in kinds(it, it.mons[0]))
    }

    @Test fun newUsesDayStartHour() {
        val st = TestData.state(data, 19, 10); st.settings.dayStartHour = 4
        val m = st.mons[0]
        m.caughtAt = ms(2026, 10, 14, 1, 30) // game day Oct 13
        assertTrue(TagKind.NEW !in kinds(st, m))
        m.caughtAt = ms(2026, 10, 14, 5, 0)
        assertTrue(TagKind.NEW in kinds(st, m))
        m.caughtAt = 0
        assertTrue(TagKind.NEW !in kinds(st, m))
    }

    @Test fun expShareHeldShinyRegionalAndOrder() {
        val world = data.species.values.toList() + TestData.species(
            9001, "Alolanthing", evolutions = listOf(Evolution(2, EvoKind.LEVEL, level = 5))
        ).copy(form = "Alola")
        val d = TestData.data(world)
        val st = TestData.state(d, 9001, 10)
        val m = st.mons[0]
        m.shiny = true; m.heldItem = "lucky-egg"; m.caughtAt = now; m.hp = 0
        st.settings.xpShareUid = m.uid
        val tags = MonTags.of(d, st, m, now)
        assertEquals(
            listOf(TagKind.MAIN, TagKind.FAINTED, TagKind.CAN_EVOLVE, TagKind.NEW, TagKind.EXP_SHARE, TagKind.HELD, TagKind.SHINY, TagKind.REGIONAL),
            tags.map { it.kind },
        )
        assertEquals("Holds Lucky Egg", tags[5].label); assertEquals("lucky-egg", tags[5].item)
        assertEquals("★ Shiny", tags[6].label)
        assertEquals("Alolan", tags[7].label)
    }

    @Test fun regionalLabels() {
        assertEquals("Galarian", MonTags.regionalLabel("Galar"))
        assertEquals("Hisuian", MonTags.regionalLabel("Hisui"))
        assertEquals("Paldean", MonTags.regionalLabel("Paldea-Combat"))
    }
}
