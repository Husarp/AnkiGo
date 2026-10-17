package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.game.Achievements
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4-03: an Ankimon import must not fire achievement celebrations; the export is not touched by achievements. */
class AchievementsAnkimonTest {
    private val data = TestData.world()
    private val NOW = 1_800_000_000_000L

    private fun am(id: String, species: Int, level: Int = 10) = AnkimonMon(
        individualId = id, isMain = false, id = species, name = null, nickname = null, level = level, xp = 0, iv = emptyMap(), ev = emptyMap(),
        nature = null, gender = null, ability = null, attacks = listOf("tackle"), shiny = false, heldItem = null, everstone = false,
        friendship = 0, capturedDate = null, defeated = 0, tier = null,
    )

    /** An Ankimon file with [n] Pokémon of species 19 (all different individuals). */
    private fun save(n: Int, level: Int = 10) = AnkimonSave(
        (1..n).map { am("imp-$it", 19, level) }, emptyList(), emptyMap(), emptyList(), null, emptySet(), emptySet(), emptyList(),
    )

    private fun merge(state: GameState, save: AnkimonSave) = AnkimonMerge.merge(data, state, save, { null }, false, NOW)

    private fun looked(): GameState {
        val s = TestData.state(data, 25, 20, listOf("tackle"))
        Achievements.check(s, data, NOW - 5000, mutableListOf(), silent = true)
        return s
    }

    @Test fun anImportReachesTiersWithDatesAndNoEvents() {
        val s = looked()
        assertEquals(emptyList<Long>(), s.achTiers["catches"])
        merge(s, save(12, level = 30)) // 13 Pokémon owned: "catches" Common (10); levels 20 + 12 * 30 = 380: "total-levels" Common (100)
        assertEquals(listOf(NOW), s.achTiers["catches"])
        assertEquals(listOf(NOW), s.achTiers["total-levels"])
        val later = mutableListOf<GameEvent>()
        Achievements.check(s, data, NOW + 1, later) // nothing is left over for the next answer to celebrate
        assertTrue(later.none { it is GameEvent.AchievementReached })
    }

    @Test fun aReImportThatBringsNothingChangesNothing() {
        val s = looked()
        merge(s, save(12))
        val before = SaveCodec.encode(s)
        val r = merge(s, save(12))
        assertEquals("nothing new", r.summary())
        assertEquals(before, SaveCodec.encode(s))
    }

    @Test fun theImportDoesNotTouchBadgesOrTheirDates() {
        val s = looked()
        s.badges += setOf(1, 7); s.badgeAt[7] = 123L
        merge(s, save(3))
        assertEquals(setOf(1, 7), s.badges)
        assertEquals(mapOf(7 to 123L), s.badgeAt)
    }

    @Test fun anImportIntoAnUnstampedStateStampsTheStart() {
        val s = TestData.state(data, 25, 20, listOf("tackle")) // never checked: an old save
        merge(s, save(2))
        assertEquals(NOW, s.achSince)
        assertTrue(AchievementTable.ROWS.all { it.id in s.achTiers })
    }

    @Test fun theExportWritesTheSameFileWhateverTheAchievementsAre() {
        val refs = AnkimonRefs.fromFiles { name ->
            when (name) {
                "items.csv" -> "id,identifier,category_id,cost,fling_power,fling_effect_id\n4,poke-ball,34,200,,\n17,potion,27,200,,\n"
                "moves.json" -> """{"tackle":{"num":33},"thunderbolt":{"num":85}}"""
                else -> null
            }
        }
        val img = AnkimonSchema.fresh()
        img.table("badges")!!.insert(mapOf("badge_id" to "b1", "achieved" to 1L))
        val kept = SqliteWriter.write(img)

        val plain = looked()
        val rich = looked()
        rich.badges += setOf(1, 2, 7, 16)
        rich.badgeAt[7] = 111L
        rich.counters["DAMAGE_DEALT"] = 99_999
        rich.achTiers["answers"] = mutableListOf(1L, 2L, 3L)
        rich.achSince = 5L

        val a = AnkimonExport.build(data, plain, kept, refs, NOW)
        val b = AnkimonExport.build(data, rich, kept, refs, NOW)
        assertEquals(emptyList<String>(), AnkimonExport.verify(b))
        assertTrue(a.bytes.contentEquals(b.bytes)) // badges, dates and counters are not in the Ankimon file
        val badges = SqliteReader(b.bytes).image().table("badges")!!
        assertEquals(1, badges.rows.size) // the table is exactly the one the PC save had
    }
}
