package com.husarp.ankigo.game.data

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.LevelMove
import com.husarp.ankigo.game.Secondary
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Parsers on small hand-written, made-up JSON/CSV (padded with filler rows to pass the sanity limits). */
class ParsersTest {

    // ---- Fixture: hand-written Showdown + PokeAPI snippets --------------------------------------------------

    private fun dexEntry(key: String, num: Int?, name: String, extra: String = "", types: String = "\"Normal\"") =
        "\"$key\":{" + (if (num != null) "\"num\":$num," else "") +
            "\"name\":\"$name\",\"types\":[$types],\"baseStats\":{\"hp\":50,\"atk\":60,\"def\":70,\"spa\":80,\"spd\":90,\"spe\":100}" +
            (if (extra.isEmpty()) "" else ",$extra") + "}"

    private fun evolved(num: Int, name: String, prevo: String, extra: String) =
        dexEntry(name.lowercase(), num, name, "\"prevo\":\"$prevo\",$extra")

    /** Specials use nums 1..40; fillers take the other numbers up to [fillers] species in total. */
    private fun pokedex(totalSpecies: Int = 1025, badType: Boolean = false): String {
        val special = listOf(
            dexEntry("base", 10, "Base", "\"evos\":[\"Itemmon\",\"Itemmon-Alola\",\"Holdmon\",\"Tradeitem\",\"Trademon\",\"Friendday\",\"Friendnight\"," +
                "\"Fairymon\",\"Movemon\",\"Extralevel\",\"Extraother\",\"Othermon\",\"Plainlevel\",\"Gendermon\",\"Upgrademon\",\"Augurmon\"]," +
                "\"genderRatio\":{\"M\":0.875,\"F\":0.125},\"abilities\":{\"0\":\"Ab One\",\"1\":\"Ab Two\",\"H\":\"Hidden Ab\"},\"heightm\":1.7,\"weightkg\":90.5", types = "\"Fire\",\"Flying\""),
            evolved(11, "Itemmon", "Base", "\"evoType\":\"useItem\",\"evoItem\":\"Thunder Stone\""),
            dexEntry("itemmonalola", 11, "Itemmon-Alola", "\"forme\":\"Alola\",\"baseSpecies\":\"Itemmon\",\"prevo\":\"Base\""),
            evolved(12, "Holdmon", "Base", "\"evoType\":\"levelHold\",\"evoItem\":\"King's Rock\",\"evoCondition\":\"at night\",\"heightm\":0,\"weightkg\":-1"),
            evolved(13, "Tradeitem", "Base", "\"evoType\":\"trade\",\"evoItem\":\"Metal Coat\""),
            evolved(14, "Trademon", "Base", "\"evoType\":\"trade\""),
            evolved(15, "Friendday", "Base", "\"evoType\":\"levelFriendship\",\"evoCondition\":\"during the day\""),
            evolved(16, "Friendnight", "Base", "\"evoType\":\"levelFriendship\",\"evoCondition\":\"at night\""),
            evolved(17, "Fairymon", "Base", "\"evoType\":\"levelExtra\",\"evoCondition\":\"with a Fairy-type move and two levels of Affection\""),
            evolved(18, "Movemon", "Base", "\"evoType\":\"levelMove\",\"evoMove\":\"Ancient Power\""),
            evolved(19, "Extralevel", "Base", "\"evoType\":\"levelExtra\",\"evoLevel\":30,\"evoCondition\":\"in a magnetic field\""),
            evolved(20, "Extraother", "Base", "\"evoType\":\"levelExtra\",\"evoCondition\":\"near a rock\""),
            evolved(21, "Othermon", "Base", "\"evoType\":\"other\",\"evoCondition\":\"walk 1000 steps\""),
            evolved(22, "Plainlevel", "Base", "\"evoLevel\":16"),
            evolved(23, "Gendermon", "Base", "\"gender\":\"M\",\"evoType\":\"useItem\",\"evoItem\":\"Dawn Stone\""),
            evolved(24, "Upgrademon", "Base", "\"evoType\":\"trade\",\"evoItem\":\"Up-Grade\""),
            evolved(25, "Augurmon", "Base", "\"evoType\":\"useItem\",\"evoCondition\":\"Black Augurite\""),
            // regional prevo is dropped; the regional form itself is not a base form
            dexEntry("meowish", 30, "Meowish", "\"evos\":[\"Perrish\"]"),
            dexEntry("meowishgalar", 30, "Meowish-Galar", "\"forme\":\"Galar\",\"baseSpecies\":\"Meowish\",\"evos\":[\"Perrish\"]"),
            evolved(31, "Perrish", "Meowish-Galar", "\"evoLevel\":28"),
            dexEntry("maleonly", 32, "Maleonly", "\"gender\":\"M\""),
            dexEntry("femonly", 33, "Femonly", "\"gender\":\"F\""),
            dexEntry("nogender", 34, "Nogender", "\"gender\":\"N\""),
            dexEntry("nidoranish", 35, "Nidoran-X", "\"gender\":\"F\",\"evos\":[\"Mr. Nidoran\"]"),
            evolved(36, "Mr. Nidoran", "Nidoran-X", "\"evoLevel\":16,\"evoCondition\":\"during the day\""),
            // no num at all (cosmetic entry) and out of range numbers are skipped
            dexEntry("cosmetic", null, "Cosmetic"),
            dexEntry("future", 1026, "Futuremon"),
            dexEntry("zero", 0, "Zeromon"),
        )
        val specialNums = (10..25).toSet() + setOf(30, 31, 32, 33, 34, 35, 36)
        val fillers = (1..1025).filter { it !in specialNums }.take((totalSpecies - specialNums.size).coerceAtLeast(0))
            .map { dexEntry("filler$it", it, "Filler $it", types = if (badType && it == 1) "\"Cosmic\"" else "\"Water\"") }
        return "{" + (special + fillers).joinToString(",") + "}"
    }

    private fun move(id: String, name: String, body: String) = "\"$id\":{\"name\":\"$name\",$body}"

    private fun basic(type: String = "Normal", cat: String = "Physical", power: Int = 40, acc: String = "100", extra: String = "") =
        "\"type\":\"$type\",\"category\":\"$cat\",\"basePower\":$power,\"accuracy\":$acc,\"priority\":0,\"target\":\"normal\"" +
            (if (extra.isEmpty()) "" else ",$extra")

    private fun moves(totalMoves: Int = 800): String {
        val special = listOf(
            move("tackle", "Tackle", basic()),
            move("growl", "Growl", basic(cat = "Status", power = 0, extra = "\"boosts\":{\"atk\":-1},\"target\":\"allAdjacentFoes\"").replace("\"target\":\"normal\",", "")),
            move("swordsdance", "Swords Dance", basic(cat = "Status", power = 0, acc = "true").replace("\"target\":\"normal\"", "\"target\":\"self\"") + ",\"boosts\":{\"atk\":2}"),
            move("thunderwave", "Thunder Wave", basic("Electric", "Status", 0, "90", "\"status\":\"par\"")),
            move("closecombat", "Close Combat", basic("Fighting", power = 120, extra = "\"self\":{\"boosts\":{\"def\":-1,\"spd\":-1}}")),
            move("flamethrower", "Flamethrower", basic("Fire", "Special", 90, extra = "\"secondary\":{\"chance\":10,\"status\":\"brn\"}")),
            move("bite", "Bite", basic("Dark", power = 60, extra = "\"secondary\":{\"chance\":30,\"volatileStatus\":\"flinch\"}")),
            move("firefang", "Fire Fang", basic("Fire", power = 65, extra = "\"secondaries\":[{\"chance\":10,\"status\":\"brn\"},{\"chance\":10,\"volatileStatus\":\"flinch\"}]")),
            move("poweruppunch", "Power-Up Punch", basic("Fighting", extra = "\"secondary\":{\"chance\":100,\"self\":{\"boosts\":{\"atk\":1}}}")),
            move("acidspray", "Acid Spray", basic("Poison", "Special", 40, extra = "\"secondary\":{\"chance\":100,\"boosts\":{\"spd\":-2}}")),
            move("confuseray", "Confuse Ray", basic("Ghost", "Status", 0, extra = "\"volatileStatus\":\"confusion\"")),
            move("drainpunch", "Drain Punch", basic("Fighting", power = 75, extra = "\"drain\":[1,2]")),
            move("flareblitz", "Flare Blitz", basic("Fire", power = 120, extra = "\"recoil\":[33,100]")),
            move("recover", "Recover", basic(cat = "Status", power = 0, acc = "true", extra = "\"heal\":[1,2]")),
            move("doubleslap", "Double Slap", basic(power = 15, acc = "85", extra = "\"multihit\":[2,5],\"isNonstandard\":\"Past\"")),
            move("surgingstrikes", "Surging Strikes", basic("Water", power = 25, extra = "\"multihit\":3,\"willCrit\":true")),
            move("dragonrage", "Dragon Rage", basic("Dragon", "Special", 0, extra = "\"damage\":40")),
            move("seismictoss", "Seismic Toss", basic("Fighting", power = 0, extra = "\"damage\":\"level\"")),
            move("sheercold", "Sheer Cold", basic("Ice", "Special", 0, "30", "\"ohko\":\"Ice\"")),
            move("fissure", "Fissure", basic("Ground", power = 0, acc = "30", extra = "\"ohko\":true")),
            move("superfang", "Super Fang", basic(power = 0, acc = "90")),
            move("explosion", "Explosion", basic(power = 250, extra = "\"selfdestruct\":\"always\"")),
            move("lowkick", "Low Kick", basic("Fighting", power = 0, extra = "\"basePowerCallback\":true")),
            move("quickattack", "Quick Attack", basic().replace("\"priority\":0", "\"priority\":1")),
            move("slash", "Slash", basic(power = 70, extra = "\"critRatio\":2")),
            move("swift", "Swift", basic("Normal", "Special", 60, "true").replace("\"target\":\"normal\"", "\"target\":\"allAdjacentFoes\"")),
            move("splash", "Splash", basic(cat = "Status", power = 0, acc = "true").replace("\"target\":\"normal\"", "\"target\":\"self\"")),
            // kept
            move("pastmove", "Past Move", basic(extra = "\"isNonstandard\":\"Past\"")),
            move("unobtainablemove", "Unobtainable Move", basic(extra = "\"isNonstandard\":\"Unobtainable\"")),
            move("lgpemove", "Lgpe Move", basic(extra = "\"isNonstandard\":\"LGPE\"")),
            // skipped
            move("zmove", "Z Move", basic(power = 180, extra = "\"isZ\":\"normaliumz\"")),
            move("maxmove", "Max Move", basic(power = 130, extra = "\"isMax\":true")),
            move("gmaxmove", "G-Max Move", basic(power = 130, extra = "\"isMax\":\"Venusaur\",\"isNonstandard\":\"Gmax\"")),
            move("capmove", "Cap Move", basic(extra = "\"isNonstandard\":\"CAP\"")),
            move("futuremove", "Future Move", basic(extra = "\"isNonstandard\":\"Future\"")),
        )
        val fillers = (1..(totalMoves - special.size).coerceAtLeast(0)).map { move("fillermove$it", "Filler Move $it", basic()) }
        return "{" + (special + fillers).joinToString(",") + "}"
    }

    private fun ls(vararg moveCodes: Pair<String, String>) =
        "{\"learnset\":{" + moveCodes.joinToString(",") { (m, c) -> "\"$m\":[" + c.split(",").joinToString(",") { "\"$it\"" } + "]" } + "}}"

    private fun learnsets() = "{" + listOf(
        // gen 9 beats gen 8; R counts as level 1; L0 kept; duplicates removed; unknown moves dropped; TMs of gen 9
        "\"base\":" + ls(
            "tackle" to "9L1,8L1", "growl" to "9L5,9L5,8L7", "slash" to "8L10", "swift" to "9R,8M",
            "recover" to "9L0", "closecombat" to "9M,8M", "flamethrower" to "8M", "nosuchmove" to "9L3,9M",
        ),
        // only gen 8 data: levels from 8; TMs: no 8M there, newest gen with M codes is 7
        "\"itemmon\":" + ls("tackle" to "8L1,7L1,7M", "bite" to "8L12,7L20", "slash" to "6M"),
        // only an event/egg/tutor line: no level data, so empty level moves, TMs still read
        "\"holdmon\":" + ls("tackle" to "9E,9T,9S1", "slash" to "9M"),
    ).joinToString(",") + "}"

    private fun speciesCsv() = """
id,identifier,generation_id,evolves_from_species_id,evolution_chain_id,color_id,shape_id,habitat_id,gender_rate,capture_rate,base_happiness,is_baby,hatch_counter,has_gender_differences,growth_rate_id,forms_switchable,is_legendary,is_mythical,order,conquest_order
10,base,2,,1,5,8,3,1,45,70,1,20,0,4,0,0,0,1,
11,itemmon,5,10,1,5,8,3,1,45,70,0,20,0,3,0,1,0,2,
12,holdmon,3,10,1,5,8,3,1,45,70,0,20,0,5,0,0,1,3,
"""

    private fun pokemonCsv() = """
id,identifier,species_id,height,weight,base_experience,order,is_default
10,base,10,7,69,64,1,1
11,itemmon,11,10,130,,2,1
12,holdmon,12,10,130,240,3,1
10001,base-alt,10,10,130,999,4,0
"""

    private fun statsCsv() = """
pokemon_id,stat_id,base_stat,effort
10,1,45,0
10,2,49,1
10,3,49,0
10,4,65,2
10,5,65,0
10,6,45,3
11,1,45,3
10001,2,49,2
"""

    private fun evolutionCsv() = """
id,evolved_species_id,evolution_trigger_id,version_group_id,is_default,trigger_item_id,minimum_level,gender_id,location_id,held_item_id,time_of_day,known_move_id,known_move_type_id,minimum_happiness,minimum_beauty
1,15,1,3,1,,,,,,day,,,220,
2,16,1,3,1,,,,,,night,,,,
3,16,1,3,1,,,,,,night,,,160,
4,17,1,15,0,,,,,,,,18,,
5,17,1,20,1,,,,,,,,18,120,
"""

    private fun experienceCsv(rates: Int = 6): String {
        val sb = StringBuilder("growth_rate_id,level,experience\n")
        for (r in 1..rates) for (l in 1..100) sb.append("$r,$l,${(l - 1) * (l - 1) * r}\n")
        return sb.toString()
    }

    private fun itemsCsv(count: Int = 1100): String {
        val sb = StringBuilder("id,identifier,category_id,cost,fling_power,fling_effect_id\n")
        sb.append("1,master-ball,34,0,,\n2,ultra-ball,34,800,,\n3,thunder-stone,8,3000,30,\n")
        for (i in 4..count) sb.append("$i,filler-item-$i,1,${i % 7 * 100},,\n")
        return sb.toString()
    }

    private fun sources(
        species: Int = 1025, moveCount: Int = 800, rates: Int = 6, items: Int = 1100, badType: Boolean = false,
        pokedex: String = pokedex(species, badType), learnsets: String = learnsets(),
    ) = Parsers.Sources(pokedex, moves(moveCount), learnsets, speciesCsv(), pokemonCsv(), statsCsv(), evolutionCsv(), experienceCsv(rates), itemsCsv(items))

    private val data: GameData by lazy { Parsers.build(sources(), now = 1234L) }

    private fun fails(block: () -> Unit): String {
        try { block() } catch (e: IllegalStateException) { return e.message ?: "" }
        fail("expected IllegalStateException")
        return ""
    }

    // ---- Species -------------------------------------------------------------------------------------------

    @Test fun onlyBaseFormsInRangeAreSpecies() {
        assertEquals(1025, data.species.size)
        assertEquals((1..1025).toList(), data.species.keys.toList())
        assertEquals("base", data.sp(10).id)
        assertEquals("Base", data.sp(10).name)
        assertEquals("itemmon", data.sp(11).id) // the Alola form shares num 11 but has a forme -> skipped
        assertEquals("meowish", data.sp(30).id)
        assertEquals(1234L, data.builtAt)
    }

    @Test fun speciesFields() {
        val b = data.sp(10)
        assertEquals(listOf(Type.FIRE, Type.FLYING), b.types)
        assertEquals(Stats(50, 60, 70, 80, 90, 100), b.base)
        assertEquals(0.875, b.maleRatio!!, 0.0)
        assertEquals(listOf("Ab One", "Ab Two"), b.abilities) // no hidden ability
        assertEquals(2, b.gen)
        assertEquals(64, b.baseExp) // pokemon.csv row with id == num; the 10001 form row is ignored
        assertEquals(4, b.growthRate)
        assertTrue(b.isBaby); assertFalse(b.isLegendary); assertFalse(b.isMythical)
        assertEquals(Stats(0, 1, 0, 2, 0, 3), b.evYield) // only the num-10 rows, not 10001
        assertTrue(data.sp(11).isLegendary)
        assertTrue(data.sp(12).isMythical)
        assertEquals(1, data.sp(11).baseExp) // empty base_experience -> 1
        assertEquals(240, data.sp(12).baseExp)
        assertEquals(Stats(3, 0, 0, 0, 0, 0), data.sp(11).evYield)
    }

    @Test fun fallbacksWithoutSpeciesCsvRow() {
        val f = data.sp(500) // filler without csv rows
        assertEquals(5, f.gen) // by number range
        assertEquals(2, f.growthRate)
        assertEquals(1, f.baseExp)
        assertEquals(Stats.ZERO, f.evYield)
        assertFalse(f.isBaby)
        assertEquals(1, data.sp(1).gen)
        assertEquals(9, data.sp(1025).gen)
    }

    @Test fun genderRules() {
        assertEquals(0.5, data.sp(500).maleRatio!!, 0.0) // neither field
        assertEquals(1.0, data.sp(32).maleRatio!!, 0.0)
        assertEquals(0.0, data.sp(33).maleRatio!!, 0.0)
        assertNull(data.sp(34).maleRatio)
    }

    @Test fun prevoOnlyForBaseForms() {
        assertEquals(10, data.sp(11).prevo)
        assertNull(data.sp(10).prevo)
        assertNull(data.sp(31).prevo) // Meowish-Galar is a regional form
        assertEquals(36, data.sp(35).evolutions.single().to)
        assertEquals(35, data.sp(36).prevo)
    }

    @Test fun ownEvoFieldsKeptOnTheSpecies() {
        assertEquals("useItem", data.sp(11).evoType)
        assertEquals(16, data.sp(22).evoLevel)
        assertEquals(28, data.sp(31).evoLevel)
        assertNull(data.sp(10).evoType); assertNull(data.sp(10).evoLevel)
    }

    // ---- Evolutions ----------------------------------------------------------------------------------------

    private fun evo(to: Int) = data.sp(10).evolutions.single { it.to == to }

    @Test fun regionalEvolutionsAreSkipped() {
        // Base lists Itemmon-Alola too; only base-form targets are kept
        assertEquals(setOf(11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25), data.sp(10).evolutions.map { it.to }.toSet())
        assertEquals(15, data.sp(10).evolutions.size)
        assertEquals(11, data.sp(10).evolutions.first().to) // order of "evos" kept
    }

    @Test fun useItemAndItemIdConversion() {
        assertEquals(EvoKind.ITEM, evo(11).kind)
        assertEquals("thunder-stone", evo(11).item)
        assertEquals("up-grade", evo(24).item)
        assertEquals("dawn-stone", evo(23).item)
    }

    @Test fun levelHoldIsAnItemWithTime() {
        val e = evo(12)
        assertEquals(EvoKind.ITEM, e.kind)
        assertEquals("kings-rock", e.item) // apostrophe dropped
        assertEquals("night", e.time)
        assertEquals("at night", e.condition)
    }

    @Test fun tradeWithAndWithoutItem() {
        assertEquals(EvoKind.TRADE, evo(13).kind); assertEquals("metal-coat", evo(13).item)
        assertEquals(EvoKind.TRADE, evo(14).kind); assertNull(evo(14).item)
    }

    @Test fun friendshipDayNightAndMinimumHappiness() {
        val d = evo(15)
        assertEquals(EvoKind.FRIENDSHIP, d.kind); assertEquals("day", d.time); assertEquals(220, d.minFriendship)
        val n = evo(16)
        assertEquals("night", n.time); assertEquals(160, n.minFriendship) // first row had no value, the second has
    }

    @Test fun fairyMoveEvolutionIsFriendshipWithMoveType() {
        val e = evo(17)
        assertEquals(EvoKind.FRIENDSHIP, e.kind)
        assertEquals(Type.FAIRY, e.moveType)
        assertEquals(120, e.minFriendship) // only row with a value
        assertNull(e.time)
        assertTrue(e.condition!!.contains("Fairy-type move"))
    }

    @Test fun levelMoveEvolutionUsesShowdownMoveId() {
        assertEquals(EvoKind.LEVEL_MOVE, evo(18).kind)
        assertEquals("ancientpower", evo(18).move)
    }

    @Test fun levelExtraAndOther() {
        assertEquals(EvoKind.LEVEL, evo(19).kind); assertEquals(30, evo(19).level); assertEquals("in a magnetic field", evo(19).condition)
        assertEquals(EvoKind.OTHER, evo(20).kind)
        assertEquals(EvoKind.OTHER, evo(21).kind); assertEquals("walk 1000 steps", evo(21).condition)
    }

    @Test fun plainLevelEvolution() {
        val e = evo(22)
        assertEquals(EvoKind.LEVEL, e.kind); assertEquals(16, e.level)
        assertNull(e.item); assertNull(e.time); assertNull(e.gender)
    }

    @Test fun genderOfEvolvedSpeciesIsKept() {
        assertEquals('M', evo(23).gender)
        assertNull(evo(22).gender)
        val e = data.sp(35).evolutions.single() // Nidoran-X -> Mr. Nidoran at level 16, "during the day"
        assertEquals(EvoKind.LEVEL, e.kind); assertEquals("day", e.time); assertNull(e.gender)
    }

    @Test fun useItemWithItemNameInCondition() {
        val e = evo(25)
        assertEquals(EvoKind.ITEM, e.kind)
        assertEquals("black-augurite", e.item)
    }

    // ---- Learnsets -----------------------------------------------------------------------------------------

    @Test fun newestGenerationWinsAndRCountsAsLevelOne() {
        val b = data.sp(10)
        // gen 9: tackle L1, growl L5 (dup removed), swift R -> level 1, recover L0. Sorted by level, then move id.
        assertEquals(
            listOf(LevelMove(0, "recover"), LevelMove(1, "swift"), LevelMove(1, "tackle"), LevelMove(5, "growl")),
            b.levelMoves,
        )
    }

    @Test fun tmMovesOfTheSameGeneration() {
        assertEquals(setOf("closecombat"), data.sp(10).tmMoves) // gen 9 "9M"; "8M" moves and unknown moves left out
    }

    @Test fun genEightOnlySpeciesAndTmFallback() {
        val s = data.sp(11)
        assertEquals(listOf(LevelMove(1, "tackle"), LevelMove(12, "bite")), s.levelMoves)
        assertEquals(setOf("tackle"), s.tmMoves) // no 8M -> newest gen with M codes is 7 ("6M" is older)
    }

    @Test fun noLevelDataLeavesLevelMovesEmpty() {
        val s = data.sp(12)
        assertTrue(s.levelMoves.isEmpty())
        assertEquals(setOf("slash"), s.tmMoves)
    }

    @Test fun speciesWithoutLearnsetHaveEmptyLists() {
        assertTrue(data.sp(500).levelMoves.isEmpty())
        assertTrue(data.sp(500).tmMoves.isEmpty())
    }

    // ---- Moves ---------------------------------------------------------------------------------------------

    private fun mv(id: String) = data.move(id) ?: fail("missing move $id").let { error("unreachable") }

    @Test fun zMaxCapFutureGmaxMovesAreSkippedOthersKept() {
        for (id in listOf("zmove", "maxmove", "gmaxmove", "capmove", "futuremove")) assertNull(id, data.move(id))
        for (id in listOf("pastmove", "unobtainablemove", "lgpemove")) assertNotNull(id, data.move(id))
        assertEquals(795, data.moves.size) // 800 in the file minus the 5 skipped
    }

    @Test fun basicMoveFields() {
        val t = mv("tackle")
        assertEquals("Tackle", t.name); assertEquals(Type.NORMAL, t.type); assertEquals(Category.PHYSICAL, t.category)
        assertEquals(40, t.power); assertEquals(100, t.accuracy)
        assertEquals(0, t.priority); assertEquals(1, t.critRatio); assertEquals("normal", t.target)
        assertNull(t.boosts); assertNull(t.status); assertNull(t.secondary); assertNull(t.multihit)
        assertFalse(t.ohko); assertFalse(t.selfDestruct); assertFalse(t.willCrit); assertFalse(t.levelDamage); assertFalse(t.halfHp)
        assertEquals(1, mv("quickattack").priority)
        assertEquals(2, mv("slash").critRatio)
        assertEquals("allAdjacentFoes", mv("swift").target)
    }

    @Test fun accuracyTrueMeansNeverMisses() {
        assertNull(mv("swordsdance").accuracy)
        assertNull(mv("swift").accuracy)
        assertEquals(90, mv("thunderwave").accuracy)
    }

    @Test fun boostsStatusAndSelfBoosts() {
        val sd = mv("swordsdance")
        assertEquals(mapOf("atk" to 2), sd.boosts); assertEquals("self", sd.target); assertEquals(Category.STATUS, sd.category)
        assertEquals(mapOf("atk" to -1), mv("growl").boosts)
        assertEquals("par", mv("thunderwave").status)
        val cc = mv("closecombat")
        assertEquals(mapOf("def" to -1, "spd" to -1), cc.selfBoosts)
        assertNull(cc.boosts)
    }

    @Test fun secondaries() {
        assertEquals(Secondary(10, status = "brn"), mv("flamethrower").secondary)
        assertEquals(Secondary(30, flinch = true, volatileStatus = "flinch"), mv("bite").secondary) // R17C-1: volatileStatus too
        assertEquals(Secondary(10, status = "brn"), mv("firefang").secondary) // first of "secondaries"
        assertEquals(Secondary(100, selfBoosts = mapOf("atk" to 1)), mv("poweruppunch").secondary)
        assertEquals(Secondary(100, boosts = mapOf("spd" to -2)), mv("acidspray").secondary)
    }

    @Test fun drainRecoilHeal() {
        assertEquals(1 to 2, mv("drainpunch").drain)
        assertEquals(33 to 100, mv("flareblitz").recoil)
        assertEquals(1 to 2, mv("recover").heal)
    }

    @Test fun multihitIntAndRange() {
        assertEquals(2..5, mv("doubleslap").multihit)
        assertEquals(3..3, mv("surgingstrikes").multihit)
        assertTrue(mv("surgingstrikes").willCrit)
        assertEquals(85, mv("doubleslap").accuracy)
    }

    @Test fun fixedLevelOhkoHalfHpSelfDestruct() {
        assertEquals(40, mv("dragonrage").fixedDamage); assertFalse(mv("dragonrage").levelDamage)
        assertTrue(mv("seismictoss").levelDamage); assertNull(mv("seismictoss").fixedDamage)
        assertTrue(mv("sheercold").ohko); assertTrue(mv("fissure").ohko)
        assertTrue(mv("superfang").halfHp)
        assertTrue(mv("explosion").selfDestruct)
    }

    @Test fun powerZeroRules() {
        assertEquals(Tuning.UNKNOWN_POWER, mv("lowkick").power) // damaging, no special rule
        assertEquals(0, mv("dragonrage").power) // fixed damage keeps 0
        assertEquals(0, mv("sheercold").power)
        assertEquals(0, mv("superfang").power)
        assertEquals(0, mv("splash").power) // status moves stay 0
        assertEquals(0, mv("growl").power)
    }

    @Test fun moveWithoutUsefulSecondaryKeepsNoVolatiles() {
        assertNull(mv("confuseray").secondary)
        assertNull(mv("confuseray").status)
    }

    // ---- Growth + prices -----------------------------------------------------------------------------------

    @Test fun growthTables() {
        assertEquals(6, data.growth.size)
        val g = data.growth.getValue(3)
        assertEquals(101, g.size)
        assertEquals(0, g[0]); assertEquals(0, g[1])
        assertEquals(99 * 99 * 3, g[100])
    }

    @Test fun itemPrices() {
        assertEquals(3000, data.price("thunder-stone"))
        assertEquals(800, data.itemPrices["ultra-ball"])   // PokeAPI's own number is parsed ...
        assertEquals(1500, data.price("ultra-ball"))       // ... and the Shop uses AnkiGo's (PLAN 171)
        assertEquals(0, data.price("master-ball"))
        assertEquals(0, data.price("not-an-item"))
    }

    // ---- CSV -----------------------------------------------------------------------------------------------

    @Test fun csvQuotesEmptyFieldsAndLineEndings() {
        val rows = Parsers.csv("﻿id,name,note\r\n1,\"Hello, World\",\r\n2,\"say \"\"hi\"\"\",x\r\n\r\n3,\"two\nlines\",y")
        assertEquals(3, rows.size)
        assertEquals(mapOf("id" to "1", "name" to "Hello, World", "note" to ""), rows[0])
        assertEquals("say \"hi\"", rows[1]["name"]); assertEquals("x", rows[1]["note"])
        assertEquals("two\nlines", rows[2]["name"])
    }

    @Test fun csvEdgeCases() {
        assertTrue(Parsers.csv("").isEmpty())
        assertTrue(Parsers.csv("a,b\n").isEmpty())
        assertEquals("", Parsers.csv("a,b,c\n1,2\n")[0]["c"]) // short row padded with ""
    }

    // ---- Cache ---------------------------------------------------------------------------------------------

    @Test fun cacheRoundTripIsExact() {
        val json = Parsers.encodeCache(data)
        assertTrue(json.contains("\"format\":4"))
        val back = Parsers.decodeCache(json)
        assertEquals(data.builtAt, back.builtAt)
        assertEquals(data.species, back.species)
        assertEquals(data.moves, back.moves)
        assertEquals(data.itemPrices, back.itemPrices)
        assertEquals(data.growth.keys, back.growth.keys)
        for ((k, v) in data.growth) assertArrayEquals(v, back.growth.getValue(k))
        // a few spot checks of the trickiest shapes
        assertEquals(data.sp(10).evolutions, back.sp(10).evolutions)
        assertEquals('M', back.sp(10).evolutions.single { it.to == 23 }.gender)
        assertEquals(2..5, back.move("doubleslap")!!.multihit)
        assertEquals(Secondary(10, status = "brn"), back.move("firefang")!!.secondary)
        assertNull(back.sp(34).maleRatio)
        assertEquals(0.875, back.sp(10).maleRatio!!, 0.0)
    }

    @Test fun cacheRejectsOtherFormatsAndGarbage() {
        val json = Parsers.encodeCache(data)
        assertTrue(fails { Parsers.decodeCache(json.replace("\"format\":4", "\"format\":1")) }.contains("format"))
        assertTrue(fails { Parsers.decodeCache("not json") }.isNotEmpty())
        assertTrue(fails { Parsers.decodeCache("{\"format\":4}") }.contains("damaged"))
        assertTrue(fails { Parsers.decodeCache(json.take(json.length / 2)) }.isNotEmpty())
    }

    // ---- Info data: height, weight, hidden ability (R4-04) ----------------------------------------------------

    @Test fun heightWeightAndHiddenAbilityAreRead() {
        val b = data.sp(10)
        assertEquals(1.7, b.heightM!!, 0.0)
        assertEquals(90.5, b.weightKg!!, 0.0)
        assertEquals("Hidden Ab", b.hiddenAbility)
        assertEquals(listOf("Ab One", "Ab Two"), b.abilities) // battle data is unchanged
    }

    @Test fun missingOrZeroInfoIsNullNeverZero() {
        val none = data.sp(11) // no heightm / weightkg / abilities.H in the sample
        assertNull(none.heightM); assertNull(none.weightKg); assertNull(none.hiddenAbility)
        val bad = data.sp(12) // heightm 0, weightkg -1
        assertNull(bad.heightM); assertNull(bad.weightKg)
    }

    @Test fun infoDataSurvivesTheCache() {
        val back = Parsers.decodeCache(Parsers.encodeCache(data))
        assertEquals(1.7, back.sp(10).heightM!!, 0.0)
        assertEquals(90.5, back.sp(10).weightKg!!, 0.0)
        assertEquals("Hidden Ab", back.sp(10).hiddenAbility)
        assertNull(back.sp(11).heightM); assertNull(back.sp(11).hiddenAbility)
    }

    @Test fun aSpeciesEntryWrittenBeforeFormat3LoadsWithNullInfo() {
        // cut the three new values off every species array, as an old cache had them
        val root = org.json.JSONObject(Parsers.encodeCache(data))
        val sp = root.getJSONArray("species")
        for (i in 0 until sp.length()) { val a = sp.getJSONArray(i); for (k in 23 downTo 21) a.remove(k) }
        val back = Parsers.decodeCache(root.toString())
        assertEquals(data.species.size, back.species.size)
        val b = back.sp(10)
        assertNull(b.heightM); assertNull(b.weightKg); assertNull(b.hiddenAbility)
        assertEquals(data.sp(10).copy(heightM = null, weightKg = null, hiddenAbility = null), b)
    }

    // ---- Sanity errors -------------------------------------------------------------------------------------

    @Test fun tooFewSpeciesFailsClearly() {
        assertTrue(fails { Parsers.build(sources(species = 500)) }.contains("species"))
    }

    @Test fun tooFewMovesFailsClearly() {
        assertTrue(fails { Parsers.build(sources(moveCount = 300)) }.contains("moves"))
    }

    @Test fun growthTableShapeIsChecked() {
        assertTrue(fails { Parsers.build(sources(rates = 5)) }.contains("growth"))
    }

    @Test fun tooFewItemPricesFailsClearly() {
        assertTrue(fails { Parsers.build(sources(items = 500)) }.contains("item"))
    }

    @Test fun brokenJsonNamesTheFile() {
        assertTrue(fails { Parsers.build(sources(pokedex = "<html>nope</html>")) }.contains("pokedex.json"))
    }

    @Test fun unknownTypeFailsClearly() {
        assertTrue(fails { Parsers.build(sources(badType = true)) }.contains("Cosmic"))
    }
}
