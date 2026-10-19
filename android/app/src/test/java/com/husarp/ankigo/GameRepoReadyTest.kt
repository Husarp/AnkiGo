package com.husarp.ankigo

import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Metric
import com.husarp.ankigo.game.TestData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R4 fix A2: the game that becomes READY (GameRepo.rebuild: a loaded save, a restored backup, an import, a new game) has had its
 * first quiet achievements look before anything can read it. The repo is driven by reflection (no Android needed), like the screenshots do.
 */
class GameRepoReadyTest {
    private val data = TestData.world()

    private fun set(field: String, v: Any?) {
        val f = GameRepo::class.java.getDeclaredField(field)
        f.isAccessible = true
        f.set(GameRepo, v)
    }

    private fun rebuild(d: GameData?, st: GameState?) {
        set("loadedData", d)
        set("held", st)
        val m = GameRepo::class.java.getDeclaredMethod("rebuild")
        m.isAccessible = true
        m.invoke(GameRepo)
    }

    @After fun forget() = rebuild(null, null)

    @Test fun aLoadedSaveHasItsTiersStoredWhenItBecomesReady() {
        val st = TestData.state(data, 25, 20, listOf("tackle"))
        st.mons += TestData.mon(data, 19, 80, uid = "big")
        assertTrue(st.achTiers.isEmpty())
        rebuild(data, st)
        assertEquals(GameRepo.Status.READY, GameRepo.status)
        assertTrue(st.achSince > 0)
        assertEquals(2, st.achTiers["highest-level"]!!.size) // Lv 80: Common (25) and Rare (50)
        assertEquals(AchievementTable.ROWS.map { it.id }.toSet(), st.achTiers.keys)
        assertEquals(8_000L + 512_000L, st.counters[Metric.TOTAL_XP.name]) // Lv 20 and Lv 80 (n^3): the baseline is stored
    }

    @Test fun aReleaseAfterTheLoadCannotTakeTheTierAway() {
        val st = TestData.state(data, 25, 20, listOf("tackle"))
        st.mons += TestData.mon(data, 19, 80, uid = "big")
        rebuild(data, st)
        st.mons.removeAll { it.uid == "big" }
        assertEquals(1, Achievements.view(st, data).single { it.id == "highest-level" }.tier)
    }

    @Test fun aGameWithoutAStarterIsNotLookedAtYet() {
        val st = TestData.state(data, 25, 20, listOf("tackle")).also { it.starterChosen = false }
        rebuild(data, st)
        assertEquals(GameRepo.Status.NEED_STARTER, GameRepo.status)
        assertTrue(st.achTiers.isEmpty()) // pickStarter does its own first look
    }

    @Test fun withoutDataNothingIsLookedAt() {
        val st = TestData.state(data, 25, 20, listOf("tackle"))
        rebuild(null, st)
        assertEquals(GameRepo.Status.NEED_DATA, GameRepo.status)
        assertTrue(st.achTiers.isEmpty())
        rebuild(data, st) // the data arrived
        assertEquals(GameRepo.Status.READY, GameRepo.status)
        assertTrue(st.achTiers.isNotEmpty())
    }

    @Test fun aSaveThatStillListsACaughtSpeciesOnTheWishlistIsCleanedOnce() {
        val st = TestData.state(data, 25, 20, listOf("tackle")) // 25 is caught
        st.settings.wishlist.clear(); st.settings.wishlist += listOf(25, 92)
        rebuild(data, st)
        assertEquals(setOf(92), st.settings.wishlist)
    }
}
