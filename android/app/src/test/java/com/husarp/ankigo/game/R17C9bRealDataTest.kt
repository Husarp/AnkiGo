package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * R17C-9b with the real game data (run with --real; skipped otherwise): every ability the game builds or says does nothing is a real Showdown ability
 * with the same name, every normal ability of the species has a sentence, the share of the species' ability slots that work is printed, the flags the
 * new abilities read exist on real moves, a real Pokémon of every built ability plays a few rounds without a crash, and the seven ability moves have texts.
 */
class R17C9bRealDataTest {
    private fun dir(): File? = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) }

    private fun real(): GameData? {
        val dir = dir() ?: return null
        fun text(vararg names: String) = names.map { File(dir, it) }.first { it.isFile }.readText()
        return Parsers.build(Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv", "species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        ))
    }

    private fun showdown(): Map<String, String>? {
        val f = dir()?.let { File(it, "abilities.js") }?.takeIf { it.isFile } ?: return null
        return Regex("""([a-z0-9]+):\{(?:[^{}]|\{(?:[^{}]|\{[^{}]*\})*\})*?name:"([^"]+)"""").findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test fun everyAbilityOfTheTextsIsARealShowdownAbilityWithTheSameName() {
        val names = showdown(); assumeTrue("ANKIGO_REAL_DATA (abilities.js) not set", names != null)
        names!!
        for (id in AbilityText.ids + AbilityText.noEffect) {
            assertTrue("$id is a Showdown ability", id in names)
            assertEquals("the name of $id", names[id], AbilityText.name(id))
        }
        for (id in AbilityMoves.CANT_SUPPRESS + AbilityMoves.FAIL_ROLEPLAY + AbilityMoves.FAIL_SKILLSWAP + AbilityMoves.NO_ENTRAIN) assertTrue("$id (a flag list entry) is a Showdown ability", id in names)
    }

    @Test fun everyNormalAbilityOfTheSpeciesHasASentenceAndTheCoverageIsAbout80Percent() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        var slots = 0; var built = 0; var noEffect = 0; var species = 0
        val perAbility = HashMap<String, Int>()
        val missing = HashSet<String>()
        for (sp in d.species.values) {
            if (sp.abilities.isEmpty()) continue
            species++
            for (a in sp.abilities) {
                slots++
                val id = BattleView.normalizeAbility(a)
                perAbility.merge(id, 1, Int::plus)
                when {
                    id in AbilityText.ids -> built++
                    id in AbilityText.noEffect -> noEffect++
                    else -> missing += a
                }
            }
        }
        val pct = 100.0 * built / slots
        println("R17C-9b coverage: $built of $slots ability slots on $species species/forms work in battle = ${"%.1f".format(pct)} % with ${AbilityText.ids.size} abilities (R17C-9a + 9b)")
        println("R17C-9b no effect: $noEffect slots on ${AbilityText.noEffect.size} abilities; by reason: " +
            AbilityText.noEffectByReason.entries.joinToString("; ") { (why, ids) -> "$why ${ids.size} abilities / ${ids.sumOf { perAbility[it] ?: 0 }} slots" })
        println("R17C-9b built but on no species slot: " + (AbilityText.ids - perAbility.keys).sorted())
        println("R17C-9b 9b only: ${AbilityText.ids.filterNot { it in ABILITIES_9A }.count()} abilities, ${AbilityText.ids.filterNot { it in ABILITIES_9A }.sumOf { perAbility[it] ?: 0 }} slots")
        assertTrue("every normal ability has a sentence: $missing", missing.isEmpty())
        assertEquals(slots, built + noEffect)
        assertTrue("about 79 % of the slots work: $pct", pct >= 77.0)
    }

    @Test fun theFlagsTheNewAbilitiesReadExistOnRealMoves() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        fun with(flag: String) = d.moves.values.filter { flag in it.flags }.map { it.id }
        for (flag in listOf("wind", "bullet", "slicing", "sound", "contact", "heal", "protect", "pulse", "punch", "bite")) assertTrue("$flag exists on real moves", with(flag).size >= 5)
        assertTrue("hurricane" in with("wind") && "tailwind" in with("wind") && "icywind" in with("wind"))
        assertTrue("shadowball" in with("bullet") && "sludgebomb" in with("bullet"))
        assertTrue("aerialace" in with("slicing") && "airslash" in with("slicing") || "aircutter" in with("slicing"))
        assertTrue("gigadrain" in with("heal") && "recover" in with("heal") && "drainpunch" in with("heal"))
        println("R17C-9b flags: wind ${with("wind").size}, bullet ${with("bullet").size}, slicing ${with("slicing").size}, heal ${with("heal").size}")
        for (id in AbilityMoves.IDS) assertTrue("$id exists", d.move(id) != null)
    }

    @Test fun theSevenAbilityMovesHaveTextsInTheRealData() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        for (id in AbilityMoves.IDS) {
            val t = MoveText.describe(d.move(id)!!)
            assertFalse("$id: $t", t.startsWith("Has no effect"))
            assertTrue("$id: $t", t.contains("ability") || t.contains("abilities"))
        }
    }

    @Test fun aRealPokemonOfEveryBuiltAbilityPlaysWithoutTrouble() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        fun moves(num: Int): List<String> = d.sp(num).levelMoves.filter { it.level <= 50 && d.move(it.move) != null }.map { it.move }.distinct().takeLast(4).ifEmpty { listOf("tackle") }
        val rng = Random(1709)
        val bySpecies = d.species.values.filter { it.num <= 1025 }
        var fights = 0
        for (id in AbilityText.ids) {
            val holder = bySpecies.firstOrNull { sp -> sp.abilities.any { BattleView.normalizeAbility(it) == id } } ?: bySpecies[rng.nextInt(bySpecies.size)]
            for (asMain in listOf(false, true)) for (choice in listOf(MoveChoice.RANDOM, MoveChoice.SMART, MoveChoice.SMARTEST)) {
                val opponent = bySpecies[rng.nextInt(bySpecies.size)]
                val holderMon = TestData.mon(d, holder.num, 50, moves(holder.num)).also { it.ability = AbilityText.name(id)!! }
                val otherMon = TestData.mon(d, opponent.num, 50, moves(opponent.num))
                val (main, wild) = if (asMain) holderMon to otherMon else otherMon to holderMon
                val b = Battle(wild, Tier.NORMAL)
                repeat(6) {
                    if (main.hp > 0 && wild.hp > 0) BattleEngine.playRound(d, b, main, 1.0, true, rng, moveChoice = choice)
                }
                fights++
                assertTrue("$id (${holder.name}) Main hp ${main.hp}", main.hp in 0..StatCalc.maxHp(d, main))
                assertTrue("$id (${holder.name}) wild hp ${wild.hp}", wild.hp in 0..b.maxWildHp(d))
                for (v in listOf(b.player, b.enemy)) assertTrue("$id: stages stay in range", v.boosts.all { it in -6..6 })
            }
        }
        println("R17C-9b played $fights fights with the ${AbilityText.ids.size} abilities")
        assertTrue(fights >= 6 * 200)
    }

    @Test fun aRealFightWithTheAbilityMovesAndTheTradedAbilitiesPlaysOn() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        val rng = Random(31)
        val bySpecies = d.species.values.filter { it.num <= 1025 && it.abilities.isNotEmpty() }
        var runs = 0
        repeat(120) {
            val a = bySpecies[rng.nextInt(bySpecies.size)]; val c = bySpecies[rng.nextInt(bySpecies.size)]
            val ids = AbilityMoves.IDS.toList()
            val main = TestData.mon(d, a.num, 50, listOf(ids[rng.nextInt(ids.size)], "tackle")).also { it.ability = a.abilities.first() }
            val wild = TestData.mon(d, c.num, 50, listOf(ids[rng.nextInt(ids.size)], "tackle")).also { it.ability = c.abilities.first() }
            val b = Battle(wild, Tier.NORMAL)
            repeat(6) { if (main.hp > 0 && wild.hp > 0) BattleEngine.playRound(d, b, main, 1.0, true, rng, moveChoice = MoveChoice.SMART) }
            runs++
            assertTrue(main.hp in 0..StatCalc.maxHp(d, main))
        }
        assertEquals(120, runs)
    }

    companion object {
        /** The ids of the first 102 rows (R17C-9a), i.e. the abilities that were built before R17C-9b. */
        private val ABILITIES_9A: Set<String> = setOf(
            "sturdy", "keeneye", "intimidate", "levitate", "swiftswim", "chlorophyll", "sandrush", "overgrow", "blaze", "torrent", "swarm", "innerfocus", "owntempo",
            "static", "poisonpoint", "flamebody", "flashfire", "oblivious", "shedskin", "guts", "waterabsorb", "voltabsorb", "rockhead", "thickfat", "hustle", "sandveil",
            "snowcloak", "rivalry", "earlybird", "insomnia", "vitalspirit", "cutecharm", "clearbody", "whitesmoke", "hydration", "leafguard", "technician", "limber",
            "soundproof", "lightningrod", "stormdrain", "motordrive", "sapsipper", "icebody", "shielddust", "compoundeyes", "hypercutter", "bigpecks", "sheerforce",
            "serenegrace", "strongjaw", "ironfist", "megalauncher", "toughclaws", "reckless", "adaptability", "hugepower", "purepower", "infiltrator", "competitive",
            "defiant", "effectspore", "damp", "prankster", "tangledfeet", "magicguard", "steadfast", "weakarmor", "synchronize", "dryskin", "stench", "poisontouch",
            "unaware", "regenerator", "noguard", "skilllink", "waterveil", "trace", "quickfeet", "overcoat", "speedboost", "solidrock", "filter", "tintedlens", "scrappy",
            "justified", "rattled", "liquidooze", "roughskin", "ironbarbs", "download", "drizzle", "drought", "sandstream", "snowwarning", "simple", "contrary",
            "heatproof", "cursedbody", "moldbreaker", "teravolt", "turboblaze",
        )
    }
}
