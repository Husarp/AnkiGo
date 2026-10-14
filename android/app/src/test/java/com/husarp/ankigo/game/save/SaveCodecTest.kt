package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.BattleSide
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Outcome
import com.husarp.ankigo.game.Settings
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Tuning
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveCodecTest {
    private fun mon(i: Int, name: String, gender: Char = 'F') = Mon(
        uid = "u$i", species = 100 + i, name = name, level = 10 + i, xp = 123 * i,
        ivs = Stats(31, 0, 15, 7, 20, 31), evs = Stats(252, 4, 0, 100, 0, 154),
        nature = Nature.entries[i % Nature.entries.size], gender = gender, shiny = i % 2 == 0, ability = "Ability $i",
        moves = mutableListOf("tackle", "growl", "thunderbolt", "quickattack").take(1 + i % 4).toMutableList(),
        hp = 17 + i, friendship = 70 + i, heldItem = if (i % 2 == 0) "everstone" else null, caughtAt = 1_700_000_000_000L + i,
        defeats = i * 3, tier = Tier.entries[i % Tier.entries.size],
        nickname = if (i % 2 == 0) "Nick \"$i\" ♥" else null, ankimonId = if (i % 3 == 0) "a1b2-$i" else null,
    )

    private fun fullState(): GameState {
        val s = GameState(schema = GameState.SCHEMA, seq = 4_000_000_000L, savedAt = 1_800_000_000_123L, createdAt = 1_700_000_000_000L)
        s.mons += mon(1, "Flabébé"); s.mons += mon(2, "Nidoran♀", 'F'); s.mons += mon(3, "Mr. \"Quote\" \\ Mime", 'M')
        s.mons += mon(4, "Magnemite", 'N'); s.mons += mon(5, "日本語 🐉")
        s.team += listOf("u3", "u1", "u5")
        s.bag["poke-ball"] = 12; s.bag["rare-candy"] = 3; s.bag["potion"] = 0
        s.tms += setOf("bodyslam", "thunderbolt")
        s.cash = 98765; s.trainerLevel = 12; s.trainerXp = 345
        s.seen += setOf(1, 2, 25, 133, 1025); s.caught += setOf(2, 25)
        val wild = mon(9, "Wildmon", 'M')
        s.battle = Battle(
            wild = wild, tier = Tier.LEGENDARY,
            player = BattleSide(intArrayOf(1, -2, 3, -4, 5, -6, 0), "par", 0, mutableSetOf("solarbeam", "fly")),
            enemy = BattleSide(intArrayOf(0, 0, 0, 0, 0, 2, -1), "slp", 3),
            tally = 25, answers = 3, override = Outcome.CATCH, rounds = 4, startedAt = 1_800_000_000_000L,
        )
        s.nextSpecies = 133; s.nextTier = Tier.BABY; s.nextShiny = true
        s.day = "2026-10-13"; s.reviewsToday = 321; s.cashToday = 4321; s.itemCountdown = 7
        intArrayOf(1, 2, 3, 4, 5).copyInto(s.buttonsToday)
        s.cardStreak = 17; s.bestCardStreak = 99; s.streakDays = 5; s.lastGoalDay = "2026-10-12"
        s.totalReviews = 5_000_000_000L
        s.badges += setOf(1, 4, 9)
        s.shopDay = "2026-10-13"; s.shopItems += listOf("potion", "great-ball"); s.shopTms += listOf("earthquake", "shadowball")
        s.redo[9_999_999_999_999L] = 2; s.redo[42L] = 1; s.redo[-7L] = 3
        s.liveDay = "2026-10-13"
        s.liveSnapshot = "{\"cards\":{\"1\":\"a \\\"q\\\"\"}}\nline2\r\n\ttab é ♀   end"
        s.starterChosen = true; s.catches = 11; s.defeats = 22; s.evolutions = 3
        val st = s.settings
        st.gens.clear(); st.gens += setOf(1, 3, 9)
        st.cardsPerRound = 5; st.autoMode = 1
        st.protectLegendary = false; st.protectMythical = false; st.protectUltra = false; st.protectStarter = false
        st.wishlist.clear(); st.wishlist += setOf(7, 8, 9)
        st.dailyAverage = 321; st.dayStartHour = 4; st.xpShareUid = "u2"; st.trainerName = "Zoë \"Ace\""
        return s
    }

    @Test fun fullStateRoundTripsToIdenticalJson() {
        val s = fullState()
        val json = SaveCodec.encode(s)
        val back = SaveCodec.decode(json)
        assertEquals(json, SaveCodec.encode(back))
    }

    @Test fun schemaComesFirstAndOutputIsNotPrettyPrinted() {
        val json = SaveCodec.encode(fullState())
        assertTrue(json.startsWith("{\"schema\":1,"))
        assertFalse(json.contains("\n"))
        assertFalse(json.contains("  "))
    }

    @Test fun spotChecksOfEveryGroupOfFields() {
        val b = SaveCodec.decode(SaveCodec.encode(fullState()))
        assertEquals(4_000_000_000L, b.seq)
        assertEquals(1_800_000_000_123L, b.savedAt)
        assertEquals(listOf("Flabébé", "Nidoran♀", "Mr. \"Quote\" \\ Mime", "Magnemite", "日本語 🐉"), b.mons.map { it.name })
        val m = b.mons[0]
        assertEquals("u1", m.uid); assertEquals(101, m.species); assertEquals(11, m.level); assertEquals(123, m.xp)
        assertEquals(Stats(31, 0, 15, 7, 20, 31), m.ivs); assertEquals(Stats(252, 4, 0, 100, 0, 154), m.evs)
        assertEquals(Nature.entries[1], m.nature); assertEquals('F', m.gender); assertEquals(false, m.shiny)
        assertEquals(listOf("tackle", "growl"), m.moves); assertNull(m.heldItem)
        assertEquals(Tier.entries[1], m.tier); assertEquals(1_700_000_000_001L, m.caughtAt)
        assertEquals("everstone", b.mons[1].heldItem); assertEquals(true, b.mons[1].shiny)
        assertNull(m.nickname); assertEquals("Nick \"2\" ♥", b.mons[1].nickname)
        assertNull(m.ankimonId); assertEquals("a1b2-3", b.mons[2].ankimonId)
        assertEquals('M', b.mons[2].gender); assertEquals('N', b.mons[3].gender)
        assertEquals(listOf("u3", "u1", "u5"), b.team)
        assertEquals(mapOf("poke-ball" to 12, "rare-candy" to 3, "potion" to 0), b.bag)
        assertEquals(setOf("bodyslam", "thunderbolt"), b.tms)
        assertEquals(setOf(1, 2, 25, 133, 1025), b.seen); assertEquals(setOf(2, 25), b.caught)
        val bt = b.battle!!
        assertEquals("Wildmon", bt.wild.name); assertEquals(Tier.LEGENDARY, bt.tier); assertEquals(Outcome.CATCH, bt.override)
        assertArrayEquals(intArrayOf(1, -2, 3, -4, 5, -6, 0), bt.player.boosts)
        assertEquals("par", bt.player.status); assertEquals("slp", bt.enemy.status); assertEquals(3, bt.enemy.statusTurns)
        assertEquals(setOf("solarbeam", "fly"), bt.player.charged); assertTrue(bt.enemy.charged.isEmpty())
        assertEquals(25, bt.tally); assertEquals(3, bt.answers); assertEquals(4, bt.rounds); assertEquals(1_800_000_000_000L, bt.startedAt)
        assertEquals(133, b.nextSpecies); assertEquals(Tier.BABY, b.nextTier); assertTrue(b.nextShiny)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5), b.buttonsToday)
        assertEquals("2026-10-12", b.lastGoalDay); assertEquals(5_000_000_000L, b.totalReviews)
        assertEquals(setOf(1, 4, 9), b.badges)
        assertEquals(listOf("potion", "great-ball"), b.shopItems); assertEquals(listOf("earthquake", "shadowball"), b.shopTms)
        assertEquals(mapOf(9_999_999_999_999L to 2, 42L to 1, -7L to 3), b.redo)
        assertEquals(fullState().liveSnapshot, b.liveSnapshot)
        assertTrue(b.starterChosen); assertEquals(11, b.catches); assertEquals(22, b.defeats); assertEquals(3, b.evolutions)
        val st = b.settings
        assertEquals(setOf(1, 3, 9), st.gens); assertEquals(5, st.cardsPerRound); assertEquals(1, st.autoMode)
        assertFalse(st.protectLegendary); assertFalse(st.protectMythical); assertFalse(st.protectUltra); assertFalse(st.protectStarter)
        assertEquals(setOf(7, 8, 9), st.wishlist); assertEquals(321, st.dailyAverage); assertEquals(4, st.dayStartHour)
        assertEquals("u2", st.xpShareUid); assertEquals("Zoë \"Ace\"", st.trainerName)
    }

    @Test fun statsAreSixNumberArrays() {
        val root = JSONObject(SaveCodec.encode(fullState()))
        val ivs = root.getJSONArray("mons").getJSONObject(0).getJSONArray("ivs")
        assertEquals("[31,0,15,7,20,31]", ivs.toString())
    }

    @Test fun defaultStateRoundTripsWithNullsAndEmpties() {
        val s = GameState()
        val b = SaveCodec.decode(SaveCodec.encode(s))
        assertNull(b.battle); assertNull(b.nextSpecies); assertNull(b.lastGoalDay); assertNull(b.liveSnapshot)
        assertNull(b.settings.xpShareUid); assertTrue(b.mons.isEmpty()); assertTrue(b.team.isEmpty())
        assertEquals(Tuning.DEFAULT_GENS, b.settings.gens)
        assertEquals(SaveCodec.encode(s), SaveCodec.encode(b))
    }

    @Test fun battleWithoutOverrideKeepsItNull() {
        val s = fullState()
        s.battle!!.override = null
        assertNull(SaveCodec.decode(SaveCodec.encode(s)).battle!!.override)
    }

    @Test fun unknownFieldsAreIgnored() {
        val o = JSONObject(SaveCodec.encode(fullState()))
        o.put("futureThing", JSONObject().put("a", 1))
        o.getJSONArray("mons").getJSONObject(0).put("newField", "x")
        o.getJSONObject("settings").put("alsoNew", true)
        val b = SaveCodec.decode(o.toString())
        assertEquals(5, b.mons.size)
        assertEquals(SaveCodec.encode(fullState()), SaveCodec.encode(b))
    }

    @Test fun missingOptionalFieldsGetModelDefaults() {
        val b = SaveCodec.decode("""{"schema":1,"mons":[{"uid":"a","species":25,"level":5}]}""")
        val d = GameState()
        assertEquals(1, b.mons.size)
        assertEquals(listOf("a"), b.team) // no team stored: the first Pokémon becomes the main
        assertEquals(d.trainerLevel, b.trainerLevel); assertEquals(d.settings.cardsPerRound, b.settings.cardsPerRound)
        assertEquals(d.settings.gens, b.settings.gens); assertEquals(d.settings.wishlist, b.settings.wishlist)
        assertEquals(d.settings.trainerName, b.settings.trainerName)
        assertEquals(Tier.NORMAL, b.nextTier); assertEquals("", b.day); assertEquals(5, b.buttonsToday.size)
        assertEquals(Tier.NORMAL, b.mons[0].tier); assertEquals(Nature.HARDY, b.mons[0].nature)
        assertEquals(Stats.ZERO, b.mons[0].evs); assertTrue(b.mons[0].moves.isEmpty())
    }

    @Test fun anOldBattleSideWithoutChargedDecodesToEmpty() {
        val b = SaveCodec.decode(
            """{"schema":1,"mons":[{"uid":"a","species":1,"level":1}],
               |"battle":{"wild":{"uid":"w","species":1,"level":1},"player":{"boosts":[1,2],"status":"par"}}}""".trimMargin(),
        )
        assertTrue(b.battle!!.player.charged.isEmpty()); assertTrue(b.battle!!.enemy.charged.isEmpty())
        assertEquals("par", b.battle!!.player.status)
    }

    @Test fun shortAndLongArraysAreFittedNotRejected() {
        val b = SaveCodec.decode(
            """{"schema":1,"mons":[{"uid":"a","species":1,"level":1,"ivs":[1,2]}],"buttonsToday":[9,8,7,6,5,4,3],
               |"battle":{"wild":{"uid":"w","species":1,"level":1},"player":{"boosts":[1,2]}}}""".trimMargin(),
        )
        assertEquals(Stats(1, 2, 0, 0, 0, 0), b.mons[0].ivs)
        assertArrayEquals(intArrayOf(9, 8, 7, 6, 5), b.buttonsToday)
        assertArrayEquals(intArrayOf(1, 2, 0, 0, 0, 0, 0), b.battle!!.player.boosts)
    }

    @Test fun unknownEnumNamesFallBack() {
        val b = SaveCodec.decode(
            """{"schema":1,"mons":[{"uid":"a","species":1,"level":1,"nature":"FUTURE","tier":"SUPER"}],"nextTier":"X",
               |"battle":{"wild":{"uid":"w","species":1,"level":1},"override":"MAYBE"}}""".trimMargin(),
        )
        assertEquals(Nature.HARDY, b.mons[0].nature); assertEquals(Tier.NORMAL, b.mons[0].tier)
        assertEquals(Tier.NORMAL, b.nextTier); assertNull(b.battle!!.override)
    }

    @Test fun newerSchemaThrows() {
        val e = assertThrows(SaveFormatException::class.java) { SaveCodec.decode("""{"schema":2,"mons":[]}""") }
        assertTrue(e.newerSchema)
        assertTrue(e.message!!.contains("save from a newer AnkiGo"))
    }

    @Test fun notJsonThrows() {
        for (text in listOf("", "   ", "hello", "[1,2]", "{", "\"x\"", "{\"schema\":1,\"mons\":[]} garbage", "{\"schema\":1,\"mon")) {
            assertThrows(text, SaveFormatException::class.java) { SaveCodec.decode(text) }
        }
    }

    @Test fun truncatedRealSaveThrowsAtEveryCut() {
        val json = SaveCodec.encode(fullState())
        for (cut in listOf(1, 10, json.length / 3, json.length / 2, json.length - 2, json.length - 1)) {
            assertThrows("cut $cut", SaveFormatException::class.java) { SaveCodec.decode(json.substring(0, cut)) }
        }
    }

    @Test fun missingSchemaOrMonsThrows() {
        assertThrows(SaveFormatException::class.java) { SaveCodec.decode("""{"mons":[]}""") }
        assertThrows(SaveFormatException::class.java) { SaveCodec.decode("""{"schema":1}""") }
        assertThrows(SaveFormatException::class.java) { SaveCodec.decode("""{"schema":1,"mons":"x"}""") }
        assertThrows(SaveFormatException::class.java) { SaveCodec.decode("""{"schema":"one","mons":[]}""") }
    }

    @Test fun aMalformedPokemonMakesTheSaveInvalidInsteadOfDroppingIt() {
        assertThrows(SaveFormatException::class.java) { SaveCodec.decode("""{"schema":1,"mons":[{"uid":"a","species":1,"level":1},{"uid":"b"}]}""") }
        assertThrows(SaveFormatException::class.java) { SaveCodec.decode("""{"schema":1,"mons":[{"uid":"a","species":1,"level":1},5]}""") }
    }

    @Test fun teamUidsMissingFromMonsAreDroppedFromTheTeamOnly() {
        val o = JSONObject(SaveCodec.encode(fullState()))
        o.put("team", JSONArray(listOf("u3", "ghost", "u1", "u5", "ghost2")))
        val b = SaveCodec.decode(o.toString())
        assertEquals(listOf("u3", "u1", "u5"), b.team)
        assertEquals(5, b.mons.size)
    }

    @Test fun aVanishedMainIsReplacedByTheNextTeamMemberOrTheFirstPokemon() {
        val o = JSONObject(SaveCodec.encode(fullState()))
        o.put("team", JSONArray(listOf("ghost", "u2")))
        assertEquals(listOf("u2"), SaveCodec.decode(o.toString()).team)
        o.put("team", JSONArray(listOf("ghost")))
        val b = SaveCodec.decode(o.toString())
        assertEquals(listOf("u1"), b.team) // first remaining Pokémon
        assertEquals(5, b.mons.size)
        assertEquals("u1", b.main!!.uid)
    }

    @Test fun badRedoKeysAreSkippedAndHugeLongKeysSurvive() {
        val o = JSONObject(SaveCodec.encode(fullState()))
        o.getJSONObject("redo").put("not-a-number", 1)
        val b = SaveCodec.decode(o.toString())
        assertEquals(3, b.redo.size)
        assertEquals(2, b.redo[9_999_999_999_999L])
    }

    @Test fun battleWithoutReadableWildIsDroppedButNothingElse() {
        val o = JSONObject(SaveCodec.encode(fullState()))
        o.getJSONObject("battle").remove("wild")
        val b = SaveCodec.decode(o.toString())
        assertNull(b.battle)
        assertEquals(5, b.mons.size)
    }

    @Test fun settingsObjectDefaultsFillIn() {
        val o = JSONObject(SaveCodec.encode(fullState()))
        o.remove("settings")
        val b = SaveCodec.decode(o.toString())
        assertEquals(Settings().cardsPerRound, b.settings.cardsPerRound)
        assertEquals(Tuning.DEFAULT_WISHLIST, b.settings.wishlist)
    }
}
