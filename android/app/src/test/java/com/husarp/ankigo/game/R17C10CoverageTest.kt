package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * R17C-10 (real game data, run with --real): "every move does something". Every Status move of the data is handled by the generic engine
 * (status / stat changes / heal), by a rule set (`handles`), or never picked ([CoreRules.NEVER_PICKED]); every damaging move whose Showdown
 * data carries something the engine reads only through a rule (volatileStatus, selfVolatile, sideCondition, a power callback, weather ...)
 * is spoken for by a rule set (its `describe`), is a variable-power move, is never picked, or is on the list of known gaps below. The failure
 * message lists whatever is not.
 */
class R17C10CoverageTest {
    private fun dir(): File? = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) }

    private fun real(dir: File): GameData {
        fun read(vararg names: String) = names.map { File(dir, it) }.first { it.isFile }.readText()
        return Parsers.build(Parsers.Sources(
            pokedexJson = read("pokedex.json"), movesJson = read("moves.json"), learnsetsJson = read("learnsets.json"),
            speciesCsv = read("pokemon_species.csv", "species.csv"), pokemonCsv = read("pokemon.csv"), statsCsv = read("pokemon_stats.csv"),
            evolutionCsv = read("pokemon_evolution.csv"), experienceCsv = read("experience.csv"), itemsCsv = read("items.csv"),
        ))
    }

    private val handled: Set<String> get() = BattleRules.ALL.flatMap { it.handles }.toSet()

    /** The Showdown fields a Status move needs a rule for (the generic path reads only `status`, `boosts`, `self.boosts` and `heal`). */
    private fun needsRule(m: Move): List<String> = listOfNotNull(
        m.volatileStatus?.takeIf { it !in MEANINGLESS_VOLATILES }?.let { "volatileStatus=$it" }, m.selfVolatile?.let { "selfVolatile=$it" }, m.sideCondition?.let { "sideCondition=$it" },
        m.slotCondition?.let { "slotCondition=$it" }, m.weather?.let { "weather=$it" }, m.terrain?.let { "terrain=$it" },
        m.pseudoWeather?.let { "pseudoWeather=$it" }, if (m.forceSwitch) "forceSwitch" else null, if (m.selfSwitch) "selfSwitch" else null,
    )

    /** Conditions that only trap the user (No Retreat: "can't switch out"): switching is the player's own choice here, so there is nothing to build. */
    private val MEANINGLESS_VOLATILES = setOf("noretreat")

    private fun generic(m: Move) = m.status != null || m.boosts != null || m.selfBoosts != null || m.heal != null

    /** Status moves that are NOT built, with the reason (R17C-10 reports them to the manager; an entry that R17C-9b has built since is simply never hit). */
    private val STATUS_GAPS = mapOf(
        "doodle" to "ability changer (R17C-9b)", "entrainment" to "ability changer (R17C-9b)", "gastroacid" to "ability changer (R17C-9b)",
        "roleplay" to "ability changer (R17C-9b)", "simplebeam" to "ability changer (R17C-9b)", "skillswap" to "ability changer (R17C-9b)",
        "worryseed" to "ability changer (R17C-9b)",
    )

    @Test fun everyStatusMoveIsBuiltOrNeverPicked() {
        val d = dir(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        val data = real(d!!)
        val status = data.moves.values.filter { !it.damaging }
        assertTrue("the data has them: ${status.size}", status.size > 200)
        val left = ArrayList<String>()
        val silent = ArrayList<String>()
        for (m in status.sortedBy { it.id }) {
            val spoken = m.id in handled || m.id in CoreRules.NEVER_PICKED
            val ok = spoken || (needsRule(m).isEmpty() && generic(m))
            if (!ok && m.id !in STATUS_GAPS) left += "${m.id} (${needsRule(m).ifEmpty { listOf("no status / boosts / heal / rule") }.joinToString()})"
            if (ok && MoveText.describe(m).contains("Has no effect")) silent += m.id
        }
        assertTrue("Status moves that do nothing and are not never-picked (${left.size}): $left", left.isEmpty())
        assertTrue("Status moves that are handled but whose text says 'Has no effect': $silent", silent.isEmpty())
    }

    /** Showdown keys of a damaging move whose effect only a rule set can give. */
    private val RULE_KEYS = listOf("basePowerCallback", "volatileStatus", "sideCondition", "slotCondition", "weather", "terrain", "pseudoWeather", "selfSwitch",
        "forceSwitch", "stealsBoosts", "callsMove", "damageCallback")

    /** Damaging moves with such a field that no rule set speaks about, with the reason (reported). */
    private val DAMAGE_GAPS = mapOf(
        "uturn" to "switching: damage only, the text says nothing about the switch", "voltswitch" to "switching: damage only", "flipturn" to "switching: damage only",
        "circlethrow" to "switching: damage only", "dragontail" to "switching: damage only",
        "firepledge" to "double battle combo (power only changes with a partner): plain damage", "grasspledge" to "double battle combo: plain damage",
        "waterpledge" to "double battle combo: plain damage",
        "pursuit" to "its power only doubles when the target switches out: plain damage", "watershuriken" to "its fixed-power form is Ash-Greninja only: plain 2-5 hits",
        "terablast" to "no Terastallization here: plain Normal-type damage",
    )

    @Test fun everyDamagingMoveWithARuleFieldIsSpokenFor() {
        val d = dir(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        val data = real(d!!)
        val raw = JSONObject(File(d, "moves.json").readText())
        val left = ArrayList<String>()
        var checked = 0
        for (m in data.moves.values.filter { it.damaging }.sortedBy { it.id }) {
            val j = raw.optJSONObject(m.id) ?: continue
            val keys = RULE_KEYS.filter { j.has(it) }.toMutableList()
            j.optJSONObject("self")?.let { if (it.has("volatileStatus")) keys += "self.volatileStatus" }
            if (j.optInt("basePower", 1) == 0 && !j.has("damage") && !j.has("ohko") && !m.halfHp) keys += "basePower=0"
            if (keys.isEmpty()) continue
            checked++
            val spoken = m.id in CoreRules.NEVER_PICKED || m.id in MovePower.VARIABLE_POWER || BattleRules.ALL.any { it.describe(m).isNotEmpty() }
            if (!spoken && m.id !in DAMAGE_GAPS) left += "${m.id} ($keys)"
        }
        assertTrue("checked $checked damaging moves with a rule field", checked > 80)
        assertTrue("Damaging moves with a Showdown field no rule set speaks about (${left.size}): $left", left.isEmpty())
    }
}
