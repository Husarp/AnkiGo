package com.husarp.ankigo.ui

import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.shots.Shots
import org.junit.Assert.*
import org.junit.Test

/** PLAN 280 / R19: visit snapshots survive restarts and never accumulate released IDs. */
class R19GBoxNewTest {
    private val data = TestData.world()
    private val now = System.currentTimeMillis()
    private fun fresh() = TestData.state(data, 19, 10).also { it.mons[0].caughtAt = now }

    @Test fun firstVisitKeepsNewAndSecondClearsItEverywhere() {
        val prefs = Shots.MemPrefs()
        val st = fresh(); val m = st.mons[0]
        BoxSeenPrefs.open(prefs, st)
        assertTrue(TileMarks.isNew(st, m, now))
        assertTrue(MonMarks.of(data, st, m, now).isNew)
        BoxSeenPrefs.open(prefs, st)
        assertFalse(TileMarks.isNew(st, m, now))
        assertFalse(MonMarks.of(data, st, m, now).isNew)
        assertFalse(MonTags.detailTags(data, st, m, now).any { it.kind == TagKind.NEW })
    }

    @Test fun caughtBetweenVisitsIsNewOnceEvenWithTheSameCatchTime() {
        val prefs = Shots.MemPrefs(); val st = fresh()
        BoxSeenPrefs.open(prefs, st)
        val later = TestData.mon(data, 16, 10, uid = "later").also { it.caughtAt = now }
        st.mons += later
        BoxSeenPrefs.open(prefs, st)
        assertFalse(TileMarks.isNew(st, st.mons[0], now))
        assertTrue(TileMarks.isNew(st, later, now))
        BoxSeenPrefs.open(prefs, st)
        assertFalse(TileMarks.isNew(st, later, now))
    }

    @Test fun restartBeforeAnotherGameSaveStillKeepsTheVisit() {
        val prefs = Shots.MemPrefs(); val st = fresh()
        val savedBeforeVisit = SaveCodec.encode(st)
        BoxSeenPrefs.open(prefs, st)
        val restored = SaveCodec.decode(savedBeforeVisit)
        BoxSeenPrefs.open(prefs, restored)
        assertFalse(TileMarks.isNew(restored, restored.mons[0], now))
    }

    @Test fun missingPreferencePreservesOldSaveFirstVisitAndDetailSeen() {
        val st = fresh(); val prefs = Shots.MemPrefs()
        assertFalse(prefs.contains(BoxSeenPrefs.KEY))
        BoxSeenPrefs.open(prefs, st)
        assertTrue(TileMarks.isNew(st, st.mons[0], now))
        val seen = fresh(); seen.viewedMons += seen.mons[0].uid
        BoxSeenPrefs.open(Shots.MemPrefs(), seen)
        assertFalse(TileMarks.isNew(seen, seen.mons[0], now))
    }

    @Test fun snapshotsAndSeenSetDiscardReleasedIds() {
        val st = fresh(); val prefs = Shots.MemPrefs()
        BoxSeenPrefs.open(prefs, st)
        st.mons.clear(); st.viewedMons += "released"
        BoxSeenPrefs.open(prefs, st)
        assertTrue(prefs.getStringSet(BoxSeenPrefs.KEY, null)!!.isEmpty())
        assertTrue(st.viewedMons.isEmpty())
    }
}
