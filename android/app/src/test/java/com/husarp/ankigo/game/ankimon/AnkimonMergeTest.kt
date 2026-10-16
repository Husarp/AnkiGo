package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.BattleSide
import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.LevelMove
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class AnkimonMergeTest {
    @get:Rule val tmp = TemporaryFolder()

    private val NOW = 1_800_000_000_000L

    /** TestData.world() plus a few species for the odd cases (gender, abilities, gen 8/9, growth rates, many moves). */
    private val data: GameData = TestData.data(
        TestData.world().species.values.toList() + listOf(
            TestData.species(200, "Neutron", maleRatio = null),
            TestData.species(201, "Hen", maleRatio = 0.0),
            TestData.species(202, "Rooster", maleRatio = 1.0),
            TestData.species(203, "Twoabil").let { it.copy(abilities = listOf("Static", "Lightning Rod")) },
            TestData.species(204, "Noabil").let { it.copy(abilities = emptyList()) },
            TestData.species(205, "Quarter", maleRatio = 0.25),
            TestData.species(210, "Slowling", growthRate = 1, evolutions = listOf(Evolution(211, EvoKind.LEVEL, level = 30))),
            TestData.species(211, "Fastbud", growthRate = 3, prevo = 210),
            TestData.species(220, "Strange", levelMoves = listOf(LevelMove(1, "notamove"), LevelMove(2, "alsonot"))),
            TestData.species(221, "Manymoves", levelMoves = listOf("tackle", "growl", "ember", "watergun", "vinewhip", "absorb").map { LevelMove(5, it) }),
            TestData.species(230, "Mr. Test"),
            TestData.species(905, "Gen8mon"),
            TestData.species(906, "Futuremon"),
        ),
    )

    // ---- builders -----------------------------------------------------------------------------------------------

    private fun am(
        id: String, species: Int?, name: String? = null, main: Boolean = false, level: Int? = null, xp: Int? = null,
        iv: Map<String, Int> = emptyMap(), ev: Map<String, Int> = emptyMap(), nature: String? = null, gender: String? = null,
        ability: String? = null, attacks: List<String> = emptyList(), shiny: Boolean = false, held: String? = null,
        everstone: Boolean = false, friendship: Int? = null, date: String? = null, defeated: Int? = null, tier: String? = null,
        nickname: String? = null,
    ) = AnkimonMon(
        individualId = id, isMain = main, id = species, name = name, nickname = nickname, level = level, xp = xp, iv = iv, ev = ev,
        nature = nature, gender = gender, ability = ability, attacks = attacks, shiny = shiny, heldItem = held, everstone = everstone,
        friendship = friendship, capturedDate = date, defeated = defeated, tier = tier,
    )

    private fun sv(
        vararg mons: AnkimonMon, team: List<String> = emptyList(), items: Map<String, Int> = emptyMap(),
        tms: List<String> = emptyList(), cash: Int? = null, seen: Set<Int> = emptySet(), caught: Set<Int> = emptySet(),
    ) = AnkimonSave(mons.toList(), team, items, tms, cash, seen, caught, emptyList())

    private fun merge(
        save: AnkimonSave, state: GameState = GameState(), useTeam: Boolean = false, formBase: (Int) -> Int? = { null },
    ) = AnkimonMerge.merge(data, state, save, formBase, useTeam, NOW)

    private fun addOne(m: AnkimonMon): Mon {
        val state = GameState()
        merge(sv(m), state)
        return state.mons.single()
    }

    /** An AnkiGo Pokémon that came from Ankimon earlier. */
    private fun owned(state: GameState, ankimonId: String, species: Int, level: Int, xp: Int = 0, uid: String = "own-$ankimonId"): Mon {
        val m = TestData.mon(data, species, level, listOf("tackle"), uid)
        m.xp = xp
        m.ankimonId = ankimonId
        state.mons += m
        return m
    }

    // ---- adding -------------------------------------------------------------------------------------------------

    @Test fun addsAPokemonWithEveryFieldMapped() {
        val state = GameState()
        val r = merge(sv(am(
            "p-1", 203, name = "Twoabil", level = 30, xp = 100,
            iv = mapOf("hp" to 31, "atk" to 40, "def" to -3, "spa" to 10), // spd and spe missing
            ev = mapOf("hp" to 252, "atk" to 300, "def" to 100, "spa" to 50, "spd" to 0, "spe" to 252),
            nature = "Timid", gender = "female", ability = "lightning rod",
            attacks = listOf("Thunderbolt", "Fake Move", "Body Slam", "thunderbolt", "Quick Attack", "Double-Edge", "Slash"),
            shiny = true, held = "Lucky Egg", friendship = 300, date = "2026-01-02 03:04:05", defeated = 12, tier = "Legendary",
            nickname = "  Sparky  ",
        )), state)
        assertEquals(1, r.added)
        val m = state.mons.single()
        assertEquals(UUID.nameUUIDFromBytes("ankimon:p-1".toByteArray()).toString(), m.uid)
        assertEquals(203, m.species)
        assertEquals("Twoabil", m.name)
        assertEquals("Sparky", m.nickname)
        assertEquals(30, m.level)
        assertEquals(100, m.xp)
        assertEquals(Stats(31, 31, 0, 10, 15, 15), m.ivs) // clamped to 0..31, missing = 15
        assertEquals(Stats(252, 252, 6, 0, 0, 0), m.evs) // each at most 252, in total 510: the last stats are cut
        assertEquals(Nature.TIMID, m.nature)
        assertEquals('F', m.gender)
        assertEquals("Lightning Rod", m.ability)
        assertEquals(listOf("thunderbolt", "bodyslam", "quickattack", "doubleedge"), m.moves) // known, distinct, max 4
        assertTrue(m.shiny)
        assertEquals("lucky-egg", m.heldItem)
        assertEquals(255, m.friendship)
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4, 5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), m.caughtAt)
        assertEquals(12, m.defeats)
        assertEquals(Tier.LEGENDARY, m.tier)
        assertEquals(StatCalc.maxHp(data, m), m.hp)
        assertEquals("p-1", m.ankimonId)
        // Imported Pokémon go to the box.
        assertEquals(emptyList<String>(), state.team)
        assertEquals(listOf(m), state.boxMons())
    }

    @Test fun missingFieldsGetSensibleDefaults() {
        val m = addOne(am("d-1", 25))
        assertEquals("Sparkmouse", m.name)
        assertEquals(1, m.level)
        assertEquals(0, m.xp)
        assertEquals(Stats(15, 15, 15, 15, 15, 15), m.ivs)
        assertEquals(Stats.ZERO, m.evs)
        assertEquals(Nature.SERIOUS, m.nature)
        assertEquals('M', m.gender) // maleRatio 0.5
        assertEquals("Testability", m.ability)
        assertEquals(listOf("tackle", "thunderwave"), m.moves) // what a level 1 one knows
        assertFalse(m.shiny)
        assertNull(m.heldItem)
        assertEquals(0, m.friendship)
        assertEquals(NOW, m.caughtAt)
        assertEquals(0, m.defeats)
        assertEquals(Tier.NORMAL, m.tier)
        assertNull(m.nickname)
        assertEquals(StatCalc.maxHp(data, m), m.hp)
    }

    @Test fun movesFallBackToTheLastFourKnownLevelUpMovesThenTackle() {
        assertEquals(listOf("tackle", "growl", "vinewhip", "absorb"), addOne(am("a", 1, level = 20)).moves)
        assertEquals(listOf("bulletseed", "tackle", "doubleedge"), addOne(am("b", 2, level = 25, attacks = listOf("nope", "alsonope"))).moves)
        assertEquals(listOf("ember", "watergun", "vinewhip", "absorb"), addOne(am("c", 221, level = 10)).moves) // the last 4 of 6
        assertEquals(listOf("tackle"), addOne(am("d", 220, level = 10)).moves) // none of its moves is in the data
    }

    @Test fun nicknamesAreTrimmedShortAndOnlyKeptWhenTheyAreReal() {
        fun nick(nickname: String?, name: String? = "Sparkmouse") = addOne(am("n", 25, name = name, nickname = nickname)).nickname
        assertEquals("Zappy", nick("  Zappy "))
        assertNull(nick(null))
        assertNull(nick("   "))
        assertNull(nick("sparkmouse")) // same as the species name
        assertNull(nick("SPARK", name = "Spark")) // same as Ankimon's own name for it
        assertEquals("a".repeat(20), nick("a".repeat(30)))
        assertEquals("日本語 ♥", nick("日本語 ♥"))
    }

    @Test fun levelAndXpAreClamped() {
        assertEquals(1, addOne(am("a", 25, level = 0)).level)
        assertEquals(1, addOne(am("b", 25, level = -4)).level)
        assertEquals(100, addOne(am("c", 25, level = 150)).level)
        assertEquals(0, addOne(am("d", 25, level = 100, xp = 5000)).xp) // no XP at 100
        assertEquals(330, addOne(am("e", 25, level = 10, xp = 999_999)).xp) // growth 2: 11^3 - 10^3 - 1
        assertEquals(0, addOne(am("f", 25, level = 10, xp = -20)).xp)
    }

    @Test fun genderFitsTheSpecies() {
        fun gender(species: Int, g: String?) = addOne(am("g", species, gender = g)).gender
        assertEquals('N', gender(200, "M")) // genderless
        assertEquals('F', gender(201, "M")) // always female
        assertEquals('M', gender(202, "F")) // always male
        assertEquals('M', gender(25, "M"))
        assertEquals('F', gender(25, "F"))
        assertEquals('F', gender(25, "Female"))
        assertEquals('M', gender(25, "male"))
        assertEquals('M', gender(25, "N")) // doesn't fit: the likelier one (ratio 0.5 -> male)
        assertEquals('M', gender(25, null))
        assertEquals('F', gender(205, null)) // ratio 0.25 -> female
        assertEquals('F', gender(205, "x"))
    }

    @Test fun abilityIsMatchedToTheSpecies() {
        fun ability(species: Int, a: String?) = addOne(am("a", species, ability = a)).ability
        assertEquals("Lightning Rod", ability(203, "lightning-rod"))
        assertEquals("Lightning Rod", ability(203, "LIGHTNING ROD"))
        assertEquals("Static", ability(203, "Blaze")) // not one of its abilities: the first
        assertEquals("Static", ability(203, null))
        assertEquals("", ability(204, "Static")) // a species with none
    }

    @Test fun heldItemsAreOnlyKeptWhenAnkiGoKnowsThemAsHeldItems() {
        fun held(h: String?, everstone: Boolean = false) = addOne(am("h", 25, held = h, everstone = everstone)).heldItem
        assertEquals("everstone", held(null, everstone = true))
        assertEquals("everstone", held("Lucky Egg", everstone = true))
        assertEquals("lucky-egg", held("Lucky Egg"))
        assertEquals("macho-brace", held("Macho_Brace"))
        assertEquals("amulet-coin", held("amulet coin"))
        assertNull(held("Potion")) // a bag item, not a held one
        assertNull(held("Poké Ball"))
        assertNull(held("Mystery Thing"))
        assertNull(held(null))
    }

    @Test fun tiersFollowTheirNameOrLabel() {
        fun tier(t: String?) = addOne(am("t", 25, tier = t)).tier
        assertEquals(Tier.ULTRA, tier("Ultra"))
        assertEquals(Tier.ULTRA, tier("Ultra Beast"))
        assertEquals(Tier.ULTRA, tier("ULTRA"))
        assertEquals(Tier.GMAX, tier("GMax"))
        assertEquals(Tier.GMAX, tier("G-Max"))
        assertEquals(Tier.LEGENDARY, tier("legendary"))
        assertEquals(Tier.MYTHICAL, tier("Mythical"))
        assertEquals(Tier.BABY, tier("Baby"))
        assertEquals(Tier.NORMAL, tier("Fossil"))
        assertEquals(Tier.NORMAL, tier(null))
    }

    @Test fun capturedDateParsesOrFallsBackToNow() {
        val expected = LocalDateTime.of(2025, 12, 31, 23, 59, 58).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, addOne(am("a", 25, date = "2025-12-31 23:59:58")).caughtAt)
        assertEquals(expected, addOne(am("b", 25, date = "2025-12-31T23:59:58")).caughtAt)
        assertEquals(expected, addOne(am("c", 25, date = " 2025-12-31 23:59:58.123456 ")).caughtAt)
        assertEquals(NOW, addOne(am("d", 25, date = "yesterday")).caughtAt)
        assertEquals(NOW, addOne(am("e", 25, date = "2026-13-45 99:00:00")).caughtAt)
        assertEquals(NOW, addOne(am("f", 25, date = null)).caughtAt)
    }

    @Test fun natureIsMatchedByNameIgnoringCase() {
        assertEquals(Nature.ADAMANT, addOne(am("a", 25, nature = "adamant")).nature)
        assertEquals(Nature.ADAMANT, addOne(am("b", 25, nature = " ADAMANT ")).nature)
        assertEquals(Nature.SERIOUS, addOne(am("c", 25, nature = "grumpy")).nature)
        assertEquals(Nature.SERIOUS, addOne(am("d", 25, nature = null)).nature)
    }

    @Test fun evsAreCappedPerStatAndInTotal() {
        val evs = addOne(am("e", 25, ev = mapOf("hp" to 252, "atk" to 252, "def" to 252, "spa" to 252, "spd" to 252, "spe" to 252))).evs
        assertEquals(Stats(252, 252, 6, 0, 0, 0), evs)
        assertEquals(Stats(0, 0, 0, 0, 0, 252), addOne(am("f", 25, ev = mapOf("spe" to 999))).evs)
        assertEquals(Stats.ZERO, addOne(am("g", 25, ev = mapOf("hp" to -5))).evs)
    }

    // ---- re-importing -------------------------------------------------------------------------------------------

    private fun richSave() = sv(
        am("main", 203, name = "Twoabil", main = true, level = 30, xp = 100, attacks = listOf("Thunderbolt", "Body Slam"), shiny = true, nickname = "Boss",
            ev = mapOf("atk" to 100), friendship = 90, held = "lucky-egg"),
        am("leaf", 1, name = "Leafling", level = 12, xp = 5, nature = "bold"),
        am("bird", 16, name = "Birdie", level = 5),
        am("future", 906, name = "Futuremon", level = 50),
        am("form", 10100, name = "Sparkmouse-Mega", level = 40),
        am("unknown", 4, name = "Charmeleon", level = 9),
        team = listOf("leaf", "bird", "future"),
        items = mapOf("poke-ball" to 12, "Great Ball" to 3, "mystery" to 9, "kingsrock" to 2),
        tms = listOf("thunderbolt", "Earthquake", "nothingmove"),
        cash = 12_400, seen = setOf(1, 2, 4, 10100), caught = setOf(16, 133),
    )

    @Test fun importingTheSameSaveTwiceChangesNothing() {
        val formBase = { n: Int -> if (n == 10100) 25 else null }
        val state = TestData.state(data, species = 133, level = 20)
        state.mons[0].uid = "own-start"; state.team[0] = "own-start" // an Ankimon id now also matches a Pokémon's uid (T52): don't clash with "main"
        state.cash = 100
        state.bag["poke-ball"] = 5
        val first = merge(richSave(), state, useTeam = true, formBase = formBase)
        assertEquals(4, first.added) // main, leaf, bird, form (future gen 9 and unknown are kept as Not supported yet)
        assertEquals(2, first.unsupported.size)
        assertEquals(2, state.unsupported.size)
        assertEquals(0, first.skipped.size)
        val afterFirst = SaveCodec.encode(state)

        val second = merge(richSave(), state, useTeam = true, formBase = formBase)
        assertEquals(0, second.added)
        assertEquals(0, second.upgraded)
        assertEquals(4, second.unchanged)
        assertEquals(0, second.seenAdded)
        assertEquals(0, second.caughtAdded)
        assertEquals(0, second.tmsAdded)
        assertEquals(emptyMap<String, Pair<Int, Int>>(), second.itemsRaised)
        assertEquals(second.cashBefore, second.cashAfter)
        assertFalse(second.teamChanged)
        assertEquals("nothing new", second.summary())
        assertEquals(afterFirst, SaveCodec.encode(state))

        // Same after a trip through the save file, and again on top.
        val reloaded = SaveCodec.decode(afterFirst)
        val third = merge(richSave(), reloaded, useTeam = true, formBase = formBase)
        assertEquals(0, third.added + third.upgraded)
        assertEquals(afterFirst, SaveCodec.encode(reloaded))
    }

    @Test fun theSameSaveOnAFreshGameAlwaysGivesTheSameGame() {
        val a = GameState(); val b = GameState()
        merge(richSave(), a, useTeam = true); merge(richSave(), b, useTeam = true)
        assertEquals(SaveCodec.encode(a), SaveCodec.encode(b)) // uids come from the Ankimon ids
    }

    // ---- upgrading ----------------------------------------------------------------------------------------------

    @Test fun upgradesByLevel() {
        val state = GameState()
        val m = owned(state, "u-1", 1, level = 10, xp = 5)
        val oldIvs = m.ivs
        m.nickname = "Old"
        val r = merge(sv(am(
            "u-1", 1, level = 12, xp = 3, iv = mapOf("hp" to 31), nature = "adamant", attacks = listOf("Tackle", "Growl", "Vine Whip"),
            ev = mapOf("atk" to 40), friendship = 77, shiny = true, nickname = "New",
        )), state)
        assertEquals(0, r.added); assertEquals(1, r.upgraded); assertEquals(0, r.unchanged)
        assertEquals(1, state.mons.size)
        assertEquals(12, m.level)
        assertEquals(3, m.xp)
        assertEquals(listOf("tackle", "growl", "vinewhip"), m.moves)
        assertEquals(Stats(0, 40, 0, 0, 0, 0), m.evs)
        assertEquals(77, m.friendship)
        assertTrue(m.shiny)
        assertEquals("New", m.nickname)
        assertEquals(StatCalc.maxHp(data, m), m.hp)
        assertEquals(oldIvs, m.ivs) // IVs and nature never change
        assertEquals(Nature.HARDY, m.nature)
        assertEquals("own-u-1", m.uid)
    }

    @Test fun upgradesByXpWithinTheSameLevel() {
        val state = GameState()
        val m = owned(state, "u-1", 1, level = 10, xp = 5)
        val r = merge(sv(am("u-1", 1, level = 10, xp = 50)), state)
        assertEquals(1, r.upgraded)
        assertEquals(50, m.xp)
        assertEquals(listOf("tackle"), m.moves) // the file lists no attacks: moves stay
    }

    @Test fun upgradesByEvolution() {
        val state = GameState()
        val m = owned(state, "u-1", 1, level = 20, xp = 7)
        val r = merge(sv(am("u-1", 2, name = "Leafbud", level = 10, xp = 0)), state)
        assertEquals(1, r.upgraded)
        assertEquals(2, m.species)
        assertEquals("Leafbud", m.name)
        assertEquals(20, m.level) // the greater (level, xp) pair stays
        assertEquals(7, m.xp)
        assertEquals(StatCalc.maxHp(data, m), m.hp)

        // Two steps ahead counts too.
        val m3 = owned(state, "u-3", 1, level = 5)
        merge(sv(am("u-3", 3, level = 5)), state)
        assertEquals(3, m3.species)
        assertEquals("Leafking", m3.name)
    }

    @Test fun evolutionAndLevelBothFromTheFile() {
        val state = GameState()
        val m = owned(state, "u-1", 1, level = 10, xp = 5)
        merge(sv(am("u-1", 2, level = 18, xp = 40, attacks = listOf("Bullet Seed", "Tackle"))), state)
        assertEquals(2, m.species)
        assertEquals(18, m.level)
        assertEquals(40, m.xp)
        assertEquals(listOf("bulletseed", "tackle"), m.moves)
    }

    @Test fun xpIsKeptInsideTheLevelOfTheNewSpecies() {
        val state = GameState()
        val m = owned(state, "u-1", 210, level = 50, xp = 8000) // slow growth: 9563 to the next level
        merge(sv(am("u-1", 211, level = 40, xp = 10)), state) // evolved (fast growth: 6120 to the next level)
        assertEquals(211, m.species)
        assertEquals(50, m.level)
        assertEquals(6119, m.xp)
    }

    @Test fun upgradesByMoreMovesOnly() {
        val state = GameState()
        val m = owned(state, "u-1", 1, level = 10)
        val r = merge(sv(am("u-1", 1, level = 10, xp = 0, attacks = listOf("Tackle", "Growl", "Vine Whip"))), state)
        assertEquals(1, r.upgraded)
        assertEquals(listOf("tackle", "growl", "vinewhip"), m.moves)
        assertEquals(10, m.level)
        // The same file again, and a file with fewer moves, change nothing.
        assertEquals(0, merge(sv(am("u-1", 1, level = 10, attacks = listOf("Tackle", "Growl", "Vine Whip"))), state).upgraded)
        val r2 = merge(sv(am("u-1", 1, level = 10, attacks = listOf("Absorb"))), state)
        assertEquals(1, r2.unchanged)
        assertEquals(listOf("tackle", "growl", "vinewhip"), m.moves)
    }

    @Test fun neverDowngrades() {
        val state = GameState()
        val m = owned(state, "u-1", 2, level = 50, xp = 100)
        m.moves.addAll(listOf("growl", "bodyslam", "thunderbolt"))
        m.nickname = "Mine"
        m.friendship = 200
        m.evs = Stats(10, 0, 0, 0, 0, 0)
        state.seen += setOf(1, 2); state.caught += setOf(1, 2) // the Pokédex already knows both forms
        val before = SaveCodec.encode(state)
        val r = merge(sv(am(
            "u-1", 1, level = 10, xp = 4, attacks = listOf("Tackle"), friendship = 5, nickname = "Other", ev = mapOf("hp" to 3), shiny = true,
        )), state)
        assertEquals(1, r.unchanged)
        assertEquals(0, r.upgraded)
        assertEquals(before, SaveCodec.encode(state)) // not even the earlier form's shiny flag or nickname
    }

    @Test fun anEvolvedAnkiGoCopyKeepsItsSpeciesWhenTheFileIsOnlyHigherLevel() {
        val state = GameState()
        val m = owned(state, "u-1", 2, level = 20)
        merge(sv(am("u-1", 1, level = 30)), state)
        assertEquals(2, m.species) // the file's Pokémon is the earlier form: no devolving
        assertEquals("Leafbud", m.name)
        assertEquals(30, m.level)
    }

    @Test fun evsTakeTheHigherPerStatUnlessTheTotalGoesOver510() {
        val state = GameState()
        val a = owned(state, "a", 1, level = 10); a.evs = Stats(100, 0, 0, 0, 0, 0)
        val b = owned(state, "b", 1, level = 10); b.evs = Stats(252, 252, 0, 0, 0, 0)
        merge(sv(
            am("a", 1, level = 11, ev = mapOf("hp" to 50, "atk" to 200)),
            am("b", 1, level = 11, ev = mapOf("def" to 10)),
        ), state)
        assertEquals(Stats(100, 200, 0, 0, 0, 0), a.evs)
        assertEquals(Stats(252, 252, 0, 0, 0, 0), b.evs) // 514 would be over: the old ones stay
    }

    @Test fun anExistingNicknameStaysWhenTheFileHasNone() {
        val state = GameState()
        val m = owned(state, "u-1", 1, level = 10); m.nickname = "Keep"
        merge(sv(am("u-1", 1, level = 11)), state)
        assertEquals("Keep", m.nickname)
        assertEquals(11, m.level)
    }

    @Test fun neverRemovesAnything() {
        val state = TestData.state(data, species = 25, level = 20)
        val extra = TestData.mon(data, 16, 7, uid = "box-1"); state.mons += extra
        state.bag["potion"] = 4; state.tms += "earthquake"; state.seen += 99; state.caught += 99; state.cash = 777
        val before = state.mons.map { it.uid }
        merge(sv(am("x", 1, level = 5), team = listOf("x")), state, useTeam = false)
        assertTrue(state.mons.map { it.uid }.containsAll(before))
        assertEquals(before.size + 1, state.mons.size)
        assertEquals(4, state.count("potion"))
        assertTrue("earthquake" in state.tms)
        assertTrue(99 in state.seen && 99 in state.caught)
        assertEquals(777, state.cash)
        assertEquals(listOf("main"), state.team) // useTeam off and a team exists: untouched
    }

    @Test fun twoCopiesOfOneAnkimonIdAreNeverCreated() {
        val state = GameState()
        merge(sv(am("same", 1, level = 5)), state)
        merge(sv(am("same", 1, level = 5)), state)
        merge(sv(am("same", 1, level = 6)), state)
        assertEquals(1, state.mons.size)
    }

    @Test fun aTakenUidGetsAnotherOne() {
        val state = GameState()
        val clash = UUID.nameUUIDFromBytes("ankimon:p".toByteArray()).toString()
        state.mons += TestData.mon(data, 16, 3, uid = clash) // some unrelated Pokémon with that uid
        merge(sv(am("p", 1)), state)
        assertEquals(2, state.mons.size)
        assertEquals(2, state.mons.map { it.uid }.toSet().size)
    }

    // ---- species rules ------------------------------------------------------------------------------------------

    @Test fun formsGoToTheirBaseSpeciesByLookupThenByName() {
        val state = GameState()
        val r = merge(
            sv(
                am("f1", 10100, name = "Sparkmouse-Mega"),            // by formBase
                am("f2", 10101, name = "Leafling-Alola"),             // formBase unknown: by the name before the '-'
                am("f3", 10102, name = "Birdie Gigantamax"),          // ... or before the space
                am("f4", 10103, name = "Mr. Test-Galar"),             // a name with a dot and a space in it
                am("f5", 10104, name = "Hen-Special"),                // formBase gives a species that isn't in the data -> name
                am("f6", 10200, name = "Zzz-Form"),                   // nobody knows it
                am("f7", 10201, name = null),
            ),
            state, formBase = { n -> when (n) { 10100 -> 25; 10104 -> 99999; else -> null } },
        )
        assertEquals(listOf(25, 1, 16, 230, 201), state.mons.map { it.species })
        assertEquals(listOf("Sparkmouse", "Leafling", "Birdie", "Mr. Test", "Hen"), state.mons.map { it.name })
        assertEquals(5, r.added)
        assertEquals(listOf("Zzz-Form: form 10200 isn't known", "#10201: form 10201 isn't known"), r.unsupported)
        assertTrue(25 in state.caught && 230 in state.caught)
    }

    @Test fun gen9IsSkippedUnlessTurnedOnInSettings() {
        val state = GameState()
        val save = sv(am("g9", 906, name = "Futuremon"), am("g8", 905, name = "Gen8mon"))
        val r = merge(save, state)
        assertEquals(listOf("Gen8mon"), state.mons.map { it.name })
        assertEquals(listOf("Futuremon: gen 9: turn on generation 9 in Settings to import it"), r.unsupported)
        assertEquals(listOf("Futuremon"), state.unsupported.map { it.name })
        assertFalse(906 in state.caught)

        state.settings.gens += 9
        val r2 = merge(save, state)
        assertEquals(1, r2.added)
        assertEquals(1, r2.unchanged)
        assertEquals(emptyList<String>(), r2.skipped)
        assertTrue(state.unsupported.isEmpty()) // it became a normal Pokémon
        assertTrue(906 in state.caught)
    }

    @Test fun gensUpToEightAlwaysImportWhateverTheGenerationSetting() {
        val state = GameState()
        state.settings.gens.clear(); state.settings.gens += 1
        merge(sv(am("g8", 905), am("g5", 16)), state)
        assertEquals(2, state.mons.size)
    }

    @Test fun unknownSpeciesAreSkippedWithAReason() {
        val state = GameState()
        val r = merge(sv(
            am("a", 4, name = "Charmeleon"), am("b", 0, name = "Zero"), am("c", -3), am("d", null, name = "Nameless"),
            am("e", null), am("f", 4000), am("ok", 16, name = "Birdie"),
        ), state)
        assertEquals(listOf("Birdie"), state.mons.map { it.name })
        assertEquals(
            listOf(
                "Charmeleon: species #4 isn't in AnkiGo's data", "Zero: no species number", "#-3: no species number",
                "Nameless: no species number", "Pokémon e: no species number", "#4000: species #4000 isn't in AnkiGo's data",
            ),
            r.unsupported,
        )
        assertTrue(r.skipped.isEmpty())
        assertEquals(6, state.unsupported.size)
        assertEquals(1, r.added)
    }

    // ---- Pokédex, items, money, TMs -------------------------------------------------------------------------------

    @Test fun pokedexIsAUnionIncludingImportedSpecies() {
        val state = GameState()
        state.seen += 25; state.caught += 25
        val r = merge(
            sv(am("x", 3), seen = setOf(1, 2, 4, 10100), caught = setOf(16, 4, 10101)),
            state, formBase = { n -> if (n == 10100) 25 else null },
        )
        assertEquals(setOf(25, 1, 2, 16, 3), state.seen) // 4 is not in the data; the form 10100 is 25 (already seen)
        assertEquals(setOf(25, 16, 3), state.caught) // 10101 can't be resolved
        assertEquals(4, r.seenAdded)
        assertEquals(2, r.caughtAdded)
    }

    @Test fun gen9DexEntriesAreKeptEvenWhenGen9IsOff() {
        val state = GameState()
        merge(sv(am("x", 1), seen = setOf(906), caught = setOf(906)), state)
        assertTrue(906 in state.seen && 906 in state.caught)
    }

    @Test fun itemsAndCashTakeTheHigherValue() {
        val state = GameState()
        state.bag["poke-ball"] = 3; state.bag["potion"] = 10
        state.cash = 5000
        val save = sv(
            am("x", 1),
            items = mapOf("poke-ball" to 5, "Potion" to 4, "Great Ball" to 2, "mystery-item" to 9, "kingsrock" to 1, "Thunder_Stone" to 3, "Poké Ball" to 8, "rare-candy" to 5),
            cash = 3000,
        )
        val r = merge(save, state)
        assertEquals(8, state.count("poke-ball")) // "Poké Ball" and "poke-ball" are one item: the highest of the file's values
        assertEquals(10, state.count("potion"))
        assertEquals(2, state.count("great-ball"))
        assertEquals(1, state.count("kings-rock"))
        assertEquals(3, state.count("thunder-stone"))
        assertFalse(state.bag.containsKey("mystery-item"))
        assertFalse(state.bag.containsKey("rare-candy")) // not an item in AnkiGo
        assertEquals(mapOf("poke-ball" to (3 to 8), "great-ball" to (0 to 2), "kings-rock" to (0 to 1), "thunder-stone" to (0 to 3)), r.itemsRaised)
        assertEquals(5000, state.cash) // the file has less
        assertEquals(5000, r.cashBefore); assertEquals(5000, r.cashAfter)

        val r2 = merge(save, state)
        assertEquals(emptyMap<String, Pair<Int, Int>>(), r2.itemsRaised) // not doubled
        assertEquals(8, state.count("poke-ball"))

        val r3 = merge(sv(am("y", 1), cash = 9000), state)
        assertEquals(9000, state.cash)
        assertEquals(5000, r3.cashBefore); assertEquals(9000, r3.cashAfter)
        assertEquals(9000, merge(sv(am("y", 1), cash = 9000), state).cashAfter)
    }

    @Test fun tmsBecomeKnownMoves() {
        val state = GameState()
        state.tms += "thunderbolt"
        val save = sv(am("x", 1), tms = listOf("thunderbolt", "Thunder Wave", "unknownmove", "TM24 Earthquake", "Body-Slam"))
        val r = merge(save, state)
        assertEquals(setOf("thunderbolt", "thunderwave", "earthquake", "bodyslam"), state.tms)
        assertEquals(3, r.tmsAdded)
        assertEquals(0, merge(save, state).tmsAdded)
    }

    // ---- team ---------------------------------------------------------------------------------------------------

    private fun eight(): List<AnkimonMon> = (1..8).map { am("m$it", 1, level = it, main = it == 5) }

    @Test fun theAnkimonTeamStartsWithTheMainAndHasAtMostSix() {
        val state = GameState()
        val r = merge(sv(*eight().toTypedArray(), team = (1..8).map { "m$it" }), state, useTeam = true)
        val ids = state.team.map { state.mon(it)!!.ankimonId }
        assertEquals(listOf("m5", "m1", "m2", "m3", "m4", "m6"), ids)
        assertEquals(8, state.mons.size) // the other two stay in the box
        assertEquals(2, state.boxMons().size)
        assertTrue(r.teamChanged)
        assertTrue(state.starterChosen)
        assertSame(state.main, state.mon(state.team[0]))
    }

    @Test fun aMainThatIsNotInTheTeamTableIsPutFirst() {
        val state = GameState()
        merge(sv(*eight().toTypedArray(), team = listOf("m3", "m1")), state, useTeam = true)
        assertEquals(listOf("m5", "m3", "m1"), state.team.map { state.mon(it)!!.ankimonId })
    }

    @Test fun withoutAMainTheTeamTableDecides() {
        val state = GameState()
        merge(sv(am("a", 1), am("b", 1), team = listOf("b", "a", "ghost")), state, useTeam = true)
        assertEquals(listOf("b", "a"), state.team.map { state.mon(it)!!.ankimonId })
    }

    @Test fun onlyPokemonThatAreInAnkiGoJoinTheTeam() {
        val state = GameState()
        merge(sv(am("a", 906, main = true), am("b", 1), am("c", 4), am("d", 16), team = listOf("c", "b", "a", "d")), state, useTeam = true)
        assertEquals(listOf("b", "d"), state.team.map { state.mon(it)!!.ankimonId }) // gen 9 (off) and an unknown species left out
    }

    @Test fun anImportedTeamThatEndsUpEmptyChangesNothing() {
        val state = TestData.state(data)
        val r = merge(sv(am("a", 4, main = true), team = listOf("a")), state, useTeam = true)
        assertEquals(listOf("main"), state.team)
        assertFalse(r.teamChanged)
    }

    @Test fun oldTeamMembersStayAsBoxPokemon() {
        val state = TestData.state(data, species = 25, level = 20)
        merge(sv(am("a", 1, main = true), team = listOf("a")), state, useTeam = true)
        assertEquals(1, state.team.size)
        assertEquals("a", state.main!!.ankimonId)
        assertNotNull(state.mon("main"))
        assertEquals(listOf("main"), state.boxMons().map { it.uid })
    }

    @Test fun withoutUseTeamAnExistingTeamStaysButAnEmptyOneIsFilled() {
        val withTeam = TestData.state(data)
        val r = merge(sv(am("a", 1, main = true), team = listOf("a")), withTeam, useTeam = false)
        assertEquals(listOf("main"), withTeam.team)
        assertFalse(r.teamChanged)

        val empty = GameState()
        val r2 = merge(sv(am("a", 1, main = true), am("b", 16), team = listOf("b")), empty, useTeam = false)
        assertEquals(listOf("a", "b"), empty.team.map { empty.mon(it)!!.ankimonId })
        assertTrue(r2.teamChanged)
        assertTrue(empty.starterChosen)
    }

    @Test fun aNewMainStartsTheBattleFresh() {
        val state = TestData.state(data, species = 25, level = 20)
        val wild = TestData.mon(data, 16, 5, uid = "wild")
        val side = BattleSide(intArrayOf(2, 0, 0, 0, 0, 0, 0), "par", 0)
        state.battle = Battle(wild = wild, tier = Tier.NORMAL, player = side)
        merge(sv(am("a", 1, main = true), team = listOf("a")), state, useTeam = true)
        val b = state.battle!!
        assertNotSame(side, b.player)
        assertNull(b.player.status)
        assertArrayEquals(IntArray(7), b.player.boosts)
        assertSame(wild, b.wild)
    }

    @Test fun theSameMainKeepsItsBattleState() {
        val state = GameState()
        merge(sv(am("a", 1, main = true), team = listOf("a")), state, useTeam = true)
        val wild = TestData.mon(data, 16, 5, uid = "wild")
        val side = BattleSide(intArrayOf(2, 0, 0, 0, 0, 0, 0), "par", 0)
        state.battle = Battle(wild = wild, tier = Tier.NORMAL, player = side)
        merge(sv(am("a", 1, main = true, level = 9), team = listOf("a")), state, useTeam = true)
        assertSame(side, state.battle!!.player)
    }

    @Test fun starterChosenOnAnEmptyGame() {
        val state = GameState()
        assertFalse(state.starterChosen)
        merge(sv(am("a", 1, main = true), team = listOf("a")), state, useTeam = true)
        assertTrue(state.starterChosen)
        // No team in the file, nothing happens to the flag.
        val other = GameState()
        merge(sv(am("a", 1)), other, useTeam = true)
        assertFalse(other.starterChosen)
    }

    // ---- report -------------------------------------------------------------------------------------------------

    @Test fun reportCountsAddedUpgradedUnchangedAndSkipped() {
        val state = GameState()
        owned(state, "up", 1, level = 5)
        owned(state, "same", 1, level = 5)
        val r = merge(sv(
            am("up", 1, level = 9), am("same", 1, level = 5), am("new1", 16), am("new2", 16), am("bad", 4, name = "Bad"),
        ), state)
        assertEquals(2, r.added); assertEquals(1, r.upgraded); assertEquals(1, r.unchanged)
        assertEquals(listOf("Bad: species #4 isn't in AnkiGo's data"), r.unsupported)
        assertEquals(4, state.mons.size)
    }

    @Test fun summaryIsOneShortLine() {
        fun rep(
            added: Int = 0, upgraded: Int = 0, seen: Int = 0, caught: Int = 0, cashBefore: Int = 100, cashAfter: Int = 100,
            items: Map<String, Pair<Int, Int>> = emptyMap(), tms: Int = 0, team: Boolean = false,
        ) = ImportReport(added, upgraded, 0, emptyList(), seen, caught, cashBefore, cashAfter, items, tms, team)
        val five = (1..5).associate { "item-$it" to (0 to 1) }
        assertEquals(
            "adds 37 Pokémon, upgrades 4; Pokédex +12; ₽ 3,100 → 12,400; 5 items",
            rep(37, 4, 12, 9, 3100, 12400, five).summary(),
        )
        assertEquals("nothing new", rep().summary())
        assertEquals("adds 1 Pokémon", rep(added = 1).summary())
        assertEquals("upgrades 2 Pokémon", rep(upgraded = 2).summary())
        assertEquals("Pokédex +3", rep(seen = 3, caught = 1).summary())
        assertEquals("Pokédex +2", rep(seen = 0, caught = 2).summary())
        assertEquals("₽ 100 → 1,234,567", rep(cashAfter = 1_234_567).summary())
        assertEquals("1 item", rep(items = mapOf("potion" to (1 to 2))).summary())
        assertEquals("1 TM; new team", rep(tms = 1, team = true).summary())
        assertEquals("adds 1 Pokémon; 3 TMs", rep(added = 1, tms = 3).summary())
    }

    @Test fun theReportOfARealMergeHasASensibleSummary() {
        val state = GameState()
        state.cash = 100
        val r = merge(sv(am("a", 1), am("b", 16), seen = setOf(25), cash = 2500, items = mapOf("poke-ball" to 3)), state)
        assertEquals("adds 2 Pokémon; Pokédex +3; ₽ 100 → 2,500; 1 item", r.summary())
    }

    // ---- save file round trip ---------------------------------------------------------------------------------------

    @Test fun anImportedGameSurvivesTheSaveCodec() {
        val state = GameState()
        merge(richSave(), state, useTeam = true, formBase = { n -> if (n == 10100) 25 else null })
        val back = SaveCodec.decode(SaveCodec.encode(state))
        assertEquals(state.mons.map { it.uid }, back.mons.map { it.uid })
        assertEquals("Boss", back.mon(state.mons[0].uid)!!.nickname)
        assertEquals("main", back.mon(state.mons[0].uid)!!.ankimonId)
        assertEquals(SaveCodec.encode(state), SaveCodec.encode(back))
    }

    // ---- from a real database file ------------------------------------------------------------------------------

    @Test fun fromARealExportFileToAGame() {
        assumeTrue("sqlite3 is not installed", AnkimonFixture.sqliteAvailable())
        val file = AnkimonFixture.build(tmp.root, "export.db", AnkimonFixture.smallSave())
        val save = AnkimonSaveReader.read(SqliteReader.open(file))
        val state = GameState()
        state.cash = 50
        val r = merge(save, state, useTeam = true)
        assertEquals(3, r.added)
        assertEquals(emptyList<String>(), r.skipped)
        assertEquals(listOf("Sparkmouse", "Leafling"), state.team.map { state.mon(it)!!.name }) // the main first, then the team table order
        val main = state.main!!
        assertEquals("aaaa1111-0000-4000-8000-000000000001", main.ankimonId)
        assertEquals("Zappy", main.nickname)
        assertEquals(30, main.level)
        assertEquals(Stats(31, 20, 10, 0, 5, 31), main.ivs)
        assertEquals(Stats(4, 252, 0, 0, 0, 252), main.evs)
        assertEquals(Nature.TIMID, main.nature)
        assertEquals(listOf("thunderbolt", "quickattack"), main.moves)
        assertTrue(main.shiny)
        assertEquals(12_400, state.cash)
        assertEquals(12, state.count("poke-ball"))
        assertEquals(3, state.count("potion"))
        assertEquals(setOf("thunderbolt"), state.tms)
        assertTrue(setOf(1, 2, 25, 16).all { it in state.seen })
        assertTrue(25 in state.caught && 1 in state.caught && 16 in state.caught)
        // Again: nothing changes.
        val again = merge(save, state, useTeam = true)
        assertEquals(0, again.added + again.upgraded)
    }

    // ---- lane C: Exp. Share holder and Pokémon the file lacks -------------------------------------------------------

    @Test fun theFilesExpShareHolderBecomesTheGamesWhenThePhoneHasNoneOrTakesAnkimonsTeam() {
        val save = sv(am("p-1", 1, level = 5, main = true), am("p-2", 16, level = 5), team = listOf("p-1", "p-2")).copy(xpShare = "p-2")
        val st = GameState()
        val r = merge(save, st)
        assertTrue(r.xpShareChanged)
        assertTrue(r.summary(), r.summary().endsWith("Exp. Share holder"))
        assertEquals(st.mons.single { it.ankimonId == "p-2" }.uid, st.settings.xpShareUid)
        assertEquals("nothing new", merge(save, st).summary())               // again: nothing new

        val phone = GameState()
        val own = owned(phone, "p-9", 19, 5); phone.settings.xpShareUid = own.uid
        assertFalse(merge(save, phone).xpShareChanged)                         // the phone's own holder stays...
        assertEquals(own.uid, phone.settings.xpShareUid)
        assertTrue(merge(save, phone, useTeam = true).xpShareChanged)          // ...unless Ankimon's team is taken too
        assertEquals(phone.mons.single { it.ankimonId == "p-2" }.uid, phone.settings.xpShareUid)
    }

    @Test fun anExpShareHolderTheGameCantHaveChangesNothing() {
        val st = GameState()
        val r = merge(sv(am("p-1", 1, level = 5), am("p-gen9", 906, level = 5)).copy(xpShare = "p-gen9"), st)
        assertFalse(r.xpShareChanged)
        assertNull(st.settings.xpShareUid)
        assertFalse(merge(sv(am("p-1", 1, level = 5)).copy(xpShare = "not-there"), st).xpShareChanged)
    }

    @Test fun pokemonThePcFileLacksAreCountedAndKept() {
        val st = GameState()
        owned(st, "gone-on-pc", 1, 5)                                         // imported once, not in the file now
        owned(st, "still-there", 16, 5)
        val exported = TestData.mon(data, 19, 5, listOf("tackle"), "u-exported").also { it.caughtAt = 2_000L }; st.mons += exported
        val releasedOnPc = TestData.mon(data, 54, 5, listOf("tackle"), "u-old").also { it.caughtAt = 1_000L }; st.mons += releasedOnPc
        val newer = TestData.mon(data, 92, 5, listOf("tackle"), "u-newer").also { it.caughtAt = 3_000L }; st.mons += newer // never exported
        val save = sv(am("still-there", 16, level = 5), am("u-exported", 19, level = 5))
        val r = merge(save, st)
        assertEquals(2, r.notInFile)                                           // gone-on-pc and u-old
        assertEquals(5, st.mons.size)                                          // nothing removed
        assertEquals(0, r.added + r.upgraded)
        // A file without any of the phone's own catches can't tell which of them went to the PC: only imported ones count.
        assertEquals(1, AnkimonMerge.notInFile(st, sv(am("still-there", 16, level = 5))))
    }
}
