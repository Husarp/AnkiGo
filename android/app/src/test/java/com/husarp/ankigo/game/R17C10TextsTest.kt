package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import com.husarp.ankigo.help.GameGuide
import com.husarp.ankigo.ui.StatusInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * R17C-10: the guide, the status badges and the move texts say what the new battle rules do. The guide sentences take their numbers from
 * the same constants as the rules; the move-text rules are checked on made-up moves and (with --real) on every real move.
 */
class R17C10TextsTest {
    private fun text(id: String) = GameGuide.chapter(id)!!.let { (listOf(it.intro) + it.points).joinToString(" ") }
    private fun dmg(id: String = "zap", acc: Int? = 100) = Move(id, id, Type.NORMAL, Category.SPECIAL, 80, acc)
    private fun status(id: String, target: String, acc: Int? = null) = Move(id, id, Type.NORMAL, Category.STATUS, 0, acc, target = target)

    private fun real(): GameData? {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) } ?: return null
        fun read(vararg names: String) = names.map { File(dir, it) }.first { it.isFile }.readText()
        return Parsers.build(Parsers.Sources(
            pokedexJson = read("pokedex.json"), movesJson = read("moves.json"), learnsetsJson = read("learnsets.json"),
            speciesCsv = read("pokemon_species.csv", "species.csv"), pokemonCsv = read("pokemon.csv"), statsCsv = read("pokemon_stats.csv"),
            evolutionCsv = read("pokemon_evolution.csv"), experienceCsv = read("experience.csv"), itemsCsv = read("items.csv"),
        ))
    }

    // ---------------------------------------------------------------- the guide

    @Test fun theMovesChapterSitsAfterBattlesAndUsesTheRuleNumbers() {
        val ids = GameGuide.chapters().map { it.id }
        assertEquals(ids.indexOf(GameGuide.BATTLES) + 1, ids.indexOf(GameGuide.MOVES))
        assertEquals(ids.size, ids.toSet().size)
        val t = text(GameGuide.MOVES)
        assertTrue(t, t.contains("a turn is one round (${Tuning.CARDS_PER_ROUND} answers)"))
        assertTrue(t, t.contains("${FieldRules.WEATHER_TURNS} rounds and terrain ${FieldRules.TERRAIN_TURNS} rounds"))
        assertTrue(t, t.contains("for ${SideMoves.SCREEN_TURNS} rounds"))
        assertTrue(t, t.contains("${Tuning.BIND_TURNS.first} to ${Tuning.BIND_TURNS.last} rounds"))
        assertTrue(t, t.contains("snaps out of it after ${Tuning.CONFUSION_TURNS.first} to ${Tuning.CONFUSION_TURNS.last} tries"))
        assertTrue(t, t.contains("hurts itself 1 time in 3") && t.contains("power ${Tuning.CONFUSION_POWER}"))
        assertTrue(t, t.contains("can't move 50% of the time"))
        assertTrue(t, t.contains("Substitute costs a quarter of your max HP"))
        assertTrue(t, t.contains("an eighth of its max HP"))
        // the rules the guide names
        for (word in listOf("Charge moves", "very next move is the hit", "can be hit", "recharge", "Flinch", "before the target in that round", "Confusion", "In love",
            "Protect", "only help when the other Pokémon attacks in that round", "Substitute", "Reflect, Light Screen and Aurora Veil", "Weather", "terrain", "Binding", "Abilities", "never used")) {
            assertTrue(word, t.contains(word))
        }
        assertEquals(Tuning.CONFUSION_SELF_HIT, 1.0 / 3.0, 0.0)
        assertEquals(0.5, Tuning.LOVE_STOPS, 0.0)
    }

    @Test fun theAbilityParagraphIsGeneralAndTrue() {
        val t = text(GameGuide.MOVES)
        // 9a built the common abilities; 9b adds more; the rest say "no effect" in their details
        assertTrue(t, t.contains("Abilities work in battle for your Pokémon and the wild ones"))
        assertTrue(t, t.contains("some abilities have no effect in AnkiGo, and the details say so"))
        // the abilities it names really exist
        for (a in listOf("intimidate", "static", "levitate", "sturdy")) assertTrue(a, AbilityText.of(a) != null)
    }

    @Test fun theBattleChapterNoLongerCallsOneAnswerATurn() {
        val b = text(GameGuide.BATTLES)
        assertFalse(b, b.contains("A turn is one dot"))
        assertFalse(b, b.contains("takes one turn"))
        assertTrue(b, b.contains("A switch is not a round") && b.contains("one free hit"))
        assertTrue(b, b.contains("gives the same free hit"))
        // Smart / Smartest never pick an attack the target is immune to; the Main then uses Struggle
        assertTrue(b, b.contains("never an attack the target is immune to") && b.contains("Struggle"))
        val healing = text(GameGuide.HEALING)
        assertFalse(healing, healing.contains("takes one turn"))
        assertTrue(healing, healing.contains("free hit"))
    }

    @Test fun noGuideSentenceIsEmptyOrUnfinished() {
        for (c in GameGuide.chapters()) for (p in c.points + c.intro) {
            assertTrue("${c.id}: $p", p.endsWith(".") && !p.contains("  ") && !p.contains("TODO"))
        }
    }

    // ---------------------------------------------------------------- the status badges

    @Test fun theStatusBadgesFollowTheNewRules() {
        val slp = StatusInfo.of("slp")!!.line
        assertTrue(slp, slp.contains("After Rest it sleeps through exactly ${SideMoves.REST_SLEEP} tries") && slp.contains("Uproar"))
        assertEquals(2, SideMoves.REST_SLEEP)
        assertTrue(StatusInfo.of("frz")!!.line.contains("each time it tries"))
        for (id in listOf("brn", "psn", "tox")) assertTrue(id, StatusInfo.of(id)!!.line.contains("Magic Guard"))
        for (i in StatusInfo.all) assertTrue(i.line, i.line.endsWith(".") && i.line.length < 200)
    }

    // ---------------------------------------------------------------- the move texts

    @Test fun theBoldWordsIncludeTheNewKeyVerbs() {
        val moves = mapOf(
            "headbutt" to dmg("headbutt").copy(secondary = Secondary(30, flinch = true, volatileStatus = "flinch")),
            "psybeam" to dmg("psybeam").copy(secondary = Secondary(10, volatileStatus = "confusion")),
            "confuseray" to status("confuseray", "normal", 100).copy(volatileStatus = "confusion"),
            "hyperbeam" to dmg("hyperbeam").copy(selfVolatile = "mustrecharge"),
            "protect" to status("protect", "self"),
            "wrap" to dmg("wrap").copy(volatileStatus = "partiallytrapped"),
            "healbell" to status("healbell", "allyTeam"),
        )
        val data = TestData.data(emptyList(), moves)
        assertEquals("May make the target **flinch** (30 %) if you move first.", ItemInfo.moveEffect(data, "headbutt"))
        assertEquals("May **confuse** the target (10 %).", ItemInfo.moveEffect(data, "psybeam"))
        assertEquals("**Confuses** the target.", ItemInfo.moveEffect(data, "confuseray"))
        assertTrue(ItemInfo.moveEffect(data, "hyperbeam"), ItemInfo.moveEffect(data, "hyperbeam").contains("rest turn to **recharge**"))
        assertTrue(ItemInfo.moveEffect(data, "protect"), ItemInfo.moveEffect(data, "protect").contains("**Blocks** every move"))
        assertTrue(ItemInfo.moveEffect(data, "wrap"), ItemInfo.moveEffect(data, "wrap").contains("**Holds the target**"))
        assertTrue(ItemInfo.moveEffect(data, "healbell"), ItemInfo.moveEffect(data, "healbell").contains("**Cures**"))
        for (id in moves.keys) assertTrue(id, ItemInfo.moveEffect(data, id).let { it.split("**").size % 2 == 1 })   // the bold marks come in pairs
    }

    @Test fun neverMissesIsSaidOnlyWhereItMeansSomething() {
        // damaging, accuracy null: yes (a one-hit KO ignores accuracy and says nothing)
        assertTrue(MoveText.describe(dmg(acc = null)).endsWith("Never misses."))
        assertFalse(MoveText.describe(dmg(acc = null).copy(ohko = true, power = 0)).contains("Never misses"))
        // a status move that changes the foe: yes
        assertTrue(MoveText.describe(status("leer2", "normal").copy(boosts = mapOf("def" to -1))).endsWith("Never misses."))
        // field moves, copies and swaps aim at nobody's luck
        for ((id, target) in listOf("raindance" to "all", "haze" to "all", "mirrormove" to "normal", "transform" to "normal", "psychup" to "normal", "healbell" to "allyTeam")) {
            assertFalse(id, MoveText.describe(status(id, target)).contains("Never misses"))
        }
    }

    @Test fun theChargeSentenceComesBeforeTheWeatherExceptions() {
        val t = MoveText.describe(dmg("solarbeam", 100).copy(flags = setOf("charge")))
        assertTrue(t, t.indexOf("Charges on the first turn") in 0 until t.indexOf("In harsh sunlight"))
        assertTrue(t, t.endsWith("its power is halved."))
    }

    // ---------------------------------------------------------------- every real move (--real)

    /** Moves that said "Has no effect" at the time of R17C-10: the ability changers (R17C-9b built them since); Parting Shot is built too (FIX-A). */
    private val PENDING = setOf("doodle", "entrainment", "gastroacid", "roleplay", "simplebeam", "skillswap", "worryseed")

    @Test fun everyRealMoveTextIsWellFormed() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        val bad = ArrayList<String>()
        for (m in d.moves.values.sortedBy { it.id }) {
            val t = MoveText.describe(m)
            val sentences = t.split(". ").map { it.trimEnd('.') }
            fun no(why: String) { bad += "${m.id}: $why | $t" }
            if (!t.endsWith(".")) no("no full stop")
            if (t.isBlank() || t.contains("  ")) no("empty or double space")
            if ("Has no effect" in t && m.id !in PENDING) no("still has no effect")
            if ("→" in t) no("arrow glyph")
            if (sentences.size != sentences.toSet().size) no("a sentence twice")
            if ("Usually goes first" in t && "Usually goes last" in t) no("first and last")
            // "Never misses." (the whole sentence) never sits with an accuracy rule, and a status move says it only with a status or stat change on the foe
            if (t.endsWith("Never misses.")) {
                if (t.contains("accurate") || t.contains("can't miss")) no("never misses with an accuracy sentence")
                if (!m.damaging && (m.target in setOf("self", "adjacentAllyOrSelf", "allies", "allySide") || (m.status == null && m.boosts == null))) no("never misses on a move that aims at no luck")
            }
            if (CoreRules.isCharge(m) && "Charges on the first turn" !in t) no("charge move without the charge sentence")
            if ("Charges" in t && "In harsh sunlight" in t && t.indexOf("Charges") > t.indexOf("In harsh sunlight")) no("charge sentence after the weather exception")
        }
        assertTrue("move texts with problems (${bad.size}):\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun everyRealStatusMoveWithAFlinchOrConfusionSaysSo() {
        val d = real(); assumeTrue("ANKIGO_REAL_DATA not set", d != null)
        d!!
        for (m in d.moves.values) {
            val sec = m.allSecondaries
            if (sec.any { it.flinch || it.volatileStatus == "flinch" }) assertTrue(m.id, MoveText.describe(m).contains("flinch"))
            if (sec.any { it.volatileStatus == "confusion" } || (!m.damaging && m.volatileStatus == "confusion")) assertTrue(m.id, MoveText.describe(m).contains("onfuse"))
        }
    }
}
