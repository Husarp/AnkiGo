package com.husarp.ankigo.ui

import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** R10-W2-D: the details header tags (Main, Team, Exp. Share, Favourite, ... in one fixed order) and the streak "Best" rule. */
class DetailTagsTest {
    private val data = TestData.world()
    private val now = LocalDateTime.of(2026, 10, 14, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun kinds(st: com.husarp.ankigo.game.GameState, m: com.husarp.ankigo.game.Mon) = MonTags.detailTags(data, st, m, now).map { it.kind }

    @Test fun mainIsAlsoInTheTeam() {
        val st = TestData.state(data, 19, 10)
        assertEquals(listOf(TagKind.MAIN, TagKind.TEAM), kinds(st, st.mons[0]))
    }

    @Test fun teamAndFavouriteForAMonThatIsNotMain() {
        val st = TestData.state(data, 19, 10)
        val other = TestData.mon(data, 16, 10, uid = "o"); st.mons += other; st.team += other.uid
        assertEquals(listOf(TagKind.TEAM), kinds(st, other))
        other.favourite = true
        assertEquals(listOf(TagKind.TEAM, TagKind.FAVOURITE), kinds(st, other))
        st.team.remove(other.uid)
        assertEquals(listOf(TagKind.FAVOURITE), kinds(st, other))
        other.favourite = false
        assertTrue(kinds(st, other).isEmpty())
    }

    @Test fun orderWithEverything() {
        val world = data.species.values.toList() + TestData.species(
            9001, "Alolanthing", evolutions = listOf(Evolution(2, EvoKind.LEVEL, level = 5))
        ).copy(form = "Alola")
        val d = TestData.data(world)
        val st = TestData.state(d, 9001, 10)
        val m = st.mons[0]
        m.shiny = true; m.heldItem = "lucky-egg"; m.caughtAt = now; m.hp = 0; m.favourite = true
        st.settings.xpShareUid = m.uid
        val tags = MonTags.detailTags(d, st, m, now)
        assertEquals(
            listOf(TagKind.MAIN, TagKind.TEAM, TagKind.EXP_SHARE, TagKind.FAVOURITE, TagKind.FAINTED, TagKind.CAN_EVOLVE,
                TagKind.NEW, TagKind.HELD, TagKind.SHINY, TagKind.REGIONAL),
            tags.map { it.kind },
        )
        assertEquals("Team", tags[1].label); assertEquals("Favourite", tags[3].label)
        assertEquals("lucky-egg", tags[7].item)
        assertEquals("Shiny", tags[8].label)
    }

    @Test fun theOldTagListIsUnchanged() {
        val st = TestData.state(data, 19, 10); st.mons[0].favourite = true
        assertEquals(listOf(TagKind.MAIN), MonTags.of(data, st, st.mons[0], now).map { it.kind })
    }

    @Test fun streakRecordNeedsARunningStreakAtTheBest() {
        assertTrue(!StreakBest.isRecord(0, 0))
        assertTrue(!StreakBest.isRecord(0, 5))
        assertTrue(!StreakBest.isRecord(2, 3))
        assertTrue(StreakBest.isRecord(3, 3))
        assertTrue(StreakBest.isRecord(4, 3))
        assertTrue(StreakBest.isRecord(1, 1))
        assertEquals("Best streak: 3 days", StreakBest.description(2, 3))
        assertEquals("New best streak: 1 day", StreakBest.description(1, 1))
    }
}
