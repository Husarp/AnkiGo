package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * R17C-7 with the real game data (run with --real; skipped otherwise): every move the field rules name exists, every real move that starts a
 * weather, a terrain or a room is built (or never picked), each of them says its sentence, Gravity stops every move with the `gravity` flag,
 * and a real fight with Rain Dance and Sandstorm plays through.
 */
class R17C7RealDataTest {
    private fun real(): GameData? {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) } ?: return null
        fun text(vararg names: String) = names.map { File(dir, it) }.first { it.isFile }.readText()
        return Parsers.build(Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv", "species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        ))
    }

    private val named = listOf(
        "solarbeam", "solarblade", "electroshot", "thunder", "hurricane", "blizzard", "bleakwindstorm", "wildboltstorm", "sandsearstorm", "springtidestorm",
        "hydrosteam", "expandingforce", "risingvoltage", "mistyexplosion", "grassyglide", "steelroller", "icespinner", "earthquake", "bulldoze", "magnitude",
        "weatherball", "terrainpulse", "plasmafists", "growth", "defog", "courtchange", "tidyup",
    )

    @Test fun everyMoveTheFieldRulesNameExistsAndIsHandled() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        for (id in FieldRules.handles + named) assertNotNull("$id is not in the real data", d.move(id))
        for (id in FieldRules.handles) {
            val m = d.move(id)!!
            assertFalse("$id is a status move", m.damaging)
            assertTrue("$id says something", FieldRules.describe(m).isNotEmpty())
            assertFalse("$id still has no effect", MoveText.describe(m).contains("Has no effect"))
            assertFalse("$id is never picked", m.id in CoreRules.NEVER_PICKED)
        }
        for (id in named) assertTrue("$id says its sentence", FieldRules.describe(d.move(id)!!).isNotEmpty() || id == "springtidestorm")
    }

    @Test fun everyRealMoveThatStartsAFieldEffectIsBuiltOrNeverPicked() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        val field = d.moves.values.filter { it.weather != null || it.terrain != null || it.pseudoWeather != null }
        assertTrue("the data has them: ${field.size}", field.size >= 15)
        for (m in field) assertTrue("${m.id} (weather ${m.weather} terrain ${m.terrain} pseudo ${m.pseudoWeather}) is not built", m.id in FieldRules.handles || m.id in CoreRules.NEVER_PICKED || m.id == "plasmafists")
        // the data carries what the rules read
        assertEquals("raindance", d.move("raindance")!!.weather)
        assertEquals("snowscape", d.move("chillyreception")!!.weather)
        assertEquals("grassyterrain", d.move("grassyterrain")!!.terrain)
        assertEquals("trickroom", d.move("trickroom")!!.pseudoWeather)
        assertEquals("iondeluge", d.move("plasmafists")!!.pseudoWeather)
        assertEquals("all", d.move("courtchange")!!.target)
        assertTrue("charge" in d.move("solarbeam")!!.flags && "charge" in d.move("solarblade")!!.flags && "charge" in d.move("electroshot")!!.flags)
    }

    @Test fun gravityStopsEveryMoveWithTheGravityFlag() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        val a = TestData.mon(d, 25, 50); val t = TestData.mon(d, 143, 50)
        val b = Battle(t, Tier.NORMAL); b.field.rooms["gravity"] = 5
        val v = SimpleView.of(d, b, a)
        val flagged = d.moves.values.filter { "gravity" in it.flags }
        assertTrue("the data has them: ${flagged.map { it.id }}", flagged.map { it.id }.containsAll(listOf("fly", "bounce", "highjumpkick", "magnetrise", "splash", "flyingpress")))
        for (m in flagged) assertEquals("${m.id} under Gravity", "because of gravity", FieldRules.moveBlocked(v, true, m))
        for (id in listOf("tackle", "earthquake", "thunderbolt", "psychic")) d.move(id)?.let { assertNull(id, FieldRules.moveBlocked(v, true, it)) }
        // the ids the rule also names are all gravity moves in the data (or not in this data)
        for (id in listOf("fly", "bounce", "skydrop", "highjumpkick", "jumpkick", "magnetrise", "telekinesis", "splash", "flyingpress")) {
            d.move(id)?.let { assertTrue("$id has the gravity flag", "gravity" in it.flags) }
        }
    }

    @Test fun aRealFightWithRainAndSandstormPlaysThrough() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        val main = TestData.mon(d, 25, 50, listOf("raindance", "thunder")) // Pikachu: the faster one
        val wild = TestData.mon(d, 143, 50, listOf("sandstorm", "bodyslam")) // Snorlax
        val b = Battle(wild, Tier.NORMAL)
        val script = R17C7.Script(ArrayDeque(listOf("raindance", "thunder", "thunder")), ArrayDeque(listOf("sandstorm", "bodyslam", "bodyslam")))
        val rounds = BattleRules.using(listOf<BattleRules>(script) + BattleRules.ALL) {
            (0 until 3).map { BattleEngine.playRound(d, b, main, 1.0, true, Random(7), moveChoice = MoveChoice.RANDOM) }
        }
        val fields = rounds.flatten().filterIsInstance<GameEvent.Effect>().filter { it.kind == EffectKind.FIELD }
        assertEquals(listOf("It started to rain!", "A sandstorm kicked up!"), fields.map { it.text })
        assertEquals(listOf("Rain", "Sandstorm"), fields.map { it.label })
        assertEquals("the later weather replaced the first", "sand", b.field.weather)
        // the sandstorm hurt Pikachu (Electric) and Snorlax (Normal): both lost 1/16 of their max HP at the end of round 1 and 2
        val hurt = rounds.flatten().filterIsInstance<GameEvent.Effect>().filter { it.kind == EffectKind.RESIDUAL }
        assertTrue("the sandstorm hurt both: ${hurt.map { it.text }}", hurt.any { it.onPlayer } && hurt.any { !it.onPlayer })
    }
}
