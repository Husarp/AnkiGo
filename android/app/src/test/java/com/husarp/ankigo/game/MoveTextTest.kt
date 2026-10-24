package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/* R9-B6: the plain-words sentence of a move (made-up moves only; the local-data check is skipped when the data isn't on this machine). */
class MoveTextTest {
    private fun dmg(id: String = "zap", power: Int = 80, acc: Int? = 100, cat: Category = Category.SPECIAL, vararg edit: (Move) -> Move): Move =
        edit.fold(Move(id, id, Type.NORMAL, cat, power, acc)) { m, f -> f(m) }

    private fun status(id: String = "buff", target: String = "self", acc: Int? = null) = Move(id, id, Type.NORMAL, Category.STATUS, 0, acc, target = target)

    private fun say(m: Move) = MoveText.describe(m)

    @Test fun plainDamageMove() {
        assertEquals("Deals damage with no extra effect.", say(dmg()))
    }

    @Test fun extraEffectLineOnlyWhenNothingElseIsSaid() {
        assertEquals("Usually goes first.", say(dmg().copy(priority = 1)))
        assertEquals("Usually goes first. Never misses.", say(dmg(acc = null).copy(priority = 1)))
        assertFalse(say(dmg(acc = null)).contains("no extra effect"))
    }

    @Test fun damageRules() {
        assertEquals("Always deals 40 damage.", say(dmg(power = 0).copy(fixedDamage = 40)))
        assertEquals("Deals damage equal to your level.", say(dmg(power = 0).copy(levelDamage = true)))
        assertEquals("Takes off half of the target's current HP.", say(dmg(power = 0).copy(halfHp = true)))
        assertEquals("Knocks the target out in one hit, but fails against a higher-level target.", say(dmg(power = 0, acc = 30).copy(ohko = true)))
        assertEquals("Hits 2–5 times.", say(dmg().copy(multihit = 2..5)))
        assertEquals("Hits 2 times.", say(dmg().copy(multihit = 2..2)))
    }

    @Test fun priorityDrainRecoilSelfDestruct() {
        assertEquals("Usually goes first.", say(dmg().copy(priority = 1)))
        assertEquals("Usually goes last.", say(dmg().copy(priority = -3)))
        assertEquals("Heals you by half the damage dealt.", say(dmg().copy(drain = 1 to 2)))
        assertEquals("Heals you by a quarter of the damage dealt.", say(dmg().copy(drain = 2 to 8)))
        assertEquals("You take a quarter of the damage dealt as recoil.", say(dmg().copy(recoil = 1 to 4)))
        assertEquals("You take a third of the damage dealt as recoil.", say(dmg().copy(recoil = 33 to 99)))
        assertEquals("You take 33 % of the damage dealt as recoil.", say(dmg().copy(recoil = 33 to 100)))
        assertEquals("You faint after using it.", say(dmg().copy(selfDestruct = true)))
        assertEquals("Heals you by three quarters of the damage dealt.", say(dmg().copy(drain = 3 to 4)))
    }

    @Test fun statusMoves() {
        assertEquals("Puts the target to sleep.", say(status(target = "normal", acc = 75).copy(status = "slp")))
        assertEquals("Paralyzes the target.", say(status(target = "normal", acc = 90).copy(status = "par")))
        assertEquals("Badly poisons the target.", say(status(target = "normal", acc = 90).copy(status = "tox")))
        assertEquals("Burns the target. Never misses.", say(status(target = "normal").copy(status = "brn")))
        assertEquals("Puts you to sleep. Heals you by half your max HP.", say(status().copy(status = "slp", heal = 1 to 2)))
        assertEquals("Heals you by a third of your max HP.", say(status().copy(heal = 1 to 3)))
        assertEquals("Fails against Grass types.".let { "Puts the target to sleep. $it" },
            say(status("spore", "normal", 100).copy(status = "slp")))
    }

    @Test fun boosts() {
        assertEquals("Raises your Attack by 2.", say(status().copy(boosts = mapOf("atk" to 2))))
        assertEquals("Raises your Attack and Speed by 1.", say(status().copy(boosts = mapOf("spe" to 1, "atk" to 1))))
        assertEquals("Raises your Attack and Speed by 2 and Defense by 1.", say(status().copy(boosts = mapOf("atk" to 2, "def" to 1, "spe" to 2))))
        assertEquals("Lowers the target's Defense by 1.", say(status(target = "normal", acc = 100).copy(boosts = mapOf("def" to -1))))
        assertEquals("Raises your Attack by 2. Lowers your Defense and Sp. Def by 1.", say(status().copy(boosts = mapOf("atk" to 2, "def" to -1, "spd" to -1))))
        assertEquals("Lowers the target's accuracy by 1.", say(status(target = "allAdjacentFoes", acc = 100).copy(boosts = mapOf("accuracy" to -1))))
        assertEquals("Raises your evasiveness by 1.", say(status().copy(boosts = mapOf("evasion" to 1))))
        assertEquals("Raises your Sp. Atk by 1. Usually goes first.", say(status().copy(selfBoosts = mapOf("spa" to 1), priority = 1)))
        // the user's own stat change after a hit
        assertEquals("Lowers your Defense by 1.", say(dmg().copy(selfBoosts = mapOf("def" to -1))))
    }

    @Test fun secondaryEffects() {
        assertEquals("May burn the target (10 %).", say(dmg().copy(secondary = Secondary(10, status = "brn"))))
        assertEquals("May put the target to sleep (30 %).", say(dmg().copy(secondary = Secondary(30, status = "slp"))))
        assertEquals("May lower the target's Sp. Def by 1 (10 %).", say(dmg().copy(secondary = Secondary(10, boosts = mapOf("spd" to -1)))))
        assertEquals("May raise your Attack by 1 (10 %).", say(dmg().copy(secondary = Secondary(10, selfBoosts = mapOf("atk" to 1)))))
        // a sure effect: no "May", no "(100 %)"
        assertEquals("Raises your Attack by 1.", say(dmg().copy(secondary = Secondary(100, selfBoosts = mapOf("atk" to 1)))))
        assertEquals("Burns the target.", say(dmg().copy(secondary = Secondary(100, status = "brn"))))
        assertEquals("Lowers the target's Speed by 1.", say(dmg().copy(secondary = Secondary(100, boosts = mapOf("spe" to -1)))))
        // R17C-3: flinch is in the engine (only when you move first)
        assertEquals("May make the target flinch (30 %) if you move first.", say(dmg().copy(secondary = Secondary(30, flinch = true))))
        assertEquals("Makes the target flinch if you move first.", say(dmg().copy(secondary = Secondary(100, flinch = true, volatileStatus = "flinch"))))
        assertEquals("May confuse the target (20 %).", say(dmg().copy(secondary = Secondary(20, volatileStatus = "confusion"))))
        // a second secondary is rolled too (the fangs)
        assertEquals("May burn the target (10 %). May make the target flinch (10 %) if you move first.",
            say(dmg().copy(secondary = Secondary(10, status = "brn"), moreSecondaries = listOf(Secondary(10, flinch = true, volatileStatus = "flinch")))))
    }

    @Test fun neverMisses() {
        assertEquals("Never misses.", say(dmg(acc = null)))
        assertFalse(say(status().copy(boosts = mapOf("atk" to 1))).contains("Never misses"))
        assertFalse(say(dmg(power = 0, acc = null).copy(ohko = true)).contains("Never misses"))
        // R17C-10: a status move says it only when it changes the target's status or stats (not for a field move, a swap or a copy)
        assertEquals("Lowers the target's Attack by 1. Never misses.", say(status(target = "normal").copy(boosts = mapOf("atk" to -1))))
        assertEquals("Burns the target. Never misses.", say(status(target = "normal").copy(status = "brn")))
        assertFalse(say(status("trickroom", target = "all").copy(pseudoWeather = "trickroom")).contains("Never misses"))
        assertFalse(say(status("transform", target = "normal")).contains("Never misses"))
        assertFalse(say(status("mirrormove", target = "normal")).contains("Never misses"))
    }

    @Test fun chargeMoves() {
        // R17C-3 (PLAN 267): the charge turn, then your very next move is the hit
        // R17C-7: Solar Beam says what the sun and the bad weather do to it; R17C-10: after its charge sentence, so "hits at once" reads as the exception
        assertEquals("Charges on the first turn, then hits with your next move. In harsh sunlight it hits at once. In rain, a sandstorm, hail or snow its power is halved.", say(dmg("solarbeam")))
        assertEquals("Charges on the first turn, then hits with your next move.", say(dmg("fly")))
        assertEquals("Charges on the first turn, then hits with your next move.", say(dmg("xcharge").copy(flags = setOf("charge"))))
        assertEquals("Charges on the first turn and raises your Defense by 1, then hits with your next move.", say(dmg("skullbash")))
        assertEquals("Charges on the first turn and raises your Sp. Atk by 1, then hits with your next move.", say(dmg("meteorbeam")))
        assertEquals("Raises your Sp. Atk, Sp. Def and Speed by 2. Charges on the first turn, then takes effect with your next move.",
            say(status("geomancy").copy(boosts = mapOf("spa" to 2, "spd" to 2, "spe" to 2))))
    }

    @Test fun rechargeAndCrashAndLocks() {
        assertEquals("After a hit, your next move is a rest turn to recharge.", say(dmg("hyperbeam").copy(selfVolatile = "mustrecharge")))
        assertEquals("After a hit, your next move is a rest turn to recharge.", say(dmg("x").copy(flags = setOf("recharge"))))
        assertEquals("If it misses or has no effect, you lose half of your max HP.", say(dmg("highjumpkick").copy(crashDamage = true)))
        assertEquals("You lose half of your max HP after using it.", say(dmg("steelbeam").copy(mindBlownRecoil = true)))
        assertEquals("You lose half of your max HP after using it.", say(dmg("chloroblast")))
        assertEquals("Locks you into using it for 2–3 moves in a row, then you become confused.", say(dmg("outrage").copy(selfVolatile = "lockedmove")))
        assertEquals("Holds the target for 4–5 turns: it loses an eighth of its max HP at the end of each turn.", say(dmg("wrap").copy(volatileStatus = "partiallytrapped")))
        assertEquals("Confuses the target.", say(status("confuseray", "normal", 100).copy(volatileStatus = "confusion")))
    }

    @Test fun neverPickedMovesSayWhy() {
        assertEquals("It does nothing, so it is never used.", say(status("splash")))
        assertEquals("Never used in AnkiGo battles: it needs a partner Pokémon.", say(status("helpinghand", "adjacentAlly")))
        assertEquals("Never used in AnkiGo battles: it only works when Pokémon switch in or out.", say(status("roar", "normal", 100).copy(priority = -6)))
        assertEquals("Never used in AnkiGo battles: it needs held items.", say(dmg("fling", power = 0)))
        assertEquals("Never used in AnkiGo battles: AnkiGo has no PP.", say(status("spite", "normal", 100)))
        assertEquals("Never used in AnkiGo battles: AnkiGo has no critical hits.", say(status("focusenergy")))
        assertEquals("Never used in AnkiGo battles: it would end every fight on its own.", say(status("perishsong", "all")))
    }

    @Test fun statusMoveWithNothingTheEngineUses() {
        // R17C-5: Protect is built now (SideMoves), so a move nothing handles stands for the old rule
        assertEquals("Has no effect in AnkiGo battles.", say(status("unhandledmove").copy(priority = 4)))
        assertTrue(say(status("protect").copy(priority = 4)).startsWith("Blocks every move aimed at you for the rest of the turn."))
        // a bad status code or empty boosts is not a use either
        assertEquals("Has no effect in AnkiGo battles.", say(status().copy(status = "xyz", boosts = mapOf("atk" to 0))))
    }

    @Test fun showsPowerRule() {
        assertTrue(MoveText.showsPower(dmg()))
        assertFalse(MoveText.showsPower(dmg(power = 0)))
        assertFalse(MoveText.showsPower(status()))
        assertFalse(MoveText.showsPower(dmg().copy(fixedDamage = 20)))
        assertFalse(MoveText.showsPower(dmg().copy(levelDamage = true)))
        assertFalse(MoveText.showsPower(dmg().copy(halfHp = true)))
        assertFalse(MoveText.showsPower(dmg().copy(ohko = true)))
    }

    @Test fun neverEmptyAndAlwaysEndsWithAFullStop() {
        val stats = listOf(null, mapOf("atk" to 1), mapOf("def" to -2, "spe" to 1), mapOf("spa" to 0))
        val secs = listOf(null, Secondary(0), Secondary(10, status = "frz"), Secondary(100, boosts = mapOf("def" to -1)), Secondary(20, flinch = true))
        val fracs = listOf(null, 1 to 2, 1 to 3, 7 to 9)
        var n = 0
        for (cat in Category.entries) for (target in listOf("normal", "self", "allAdjacentFoes", "foeSide")) for (acc in listOf(null, 100)) {
            for (b in stats) for (sb in stats) for (sec in secs) for (f in fracs) for (st in listOf(null, "par", "zzz")) for (pr in listOf(-1, 0, 2)) {
                val m = Move("x", "X", Type.NORMAL, cat, if (cat == Category.STATUS) 0 else 50, acc, priority = pr, target = target, boosts = b, selfBoosts = sb,
                    status = st, secondary = sec, drain = f, recoil = f, heal = f, selfDestruct = f != null && pr == 2)
                val text = say(m)
                assertTrue(text, text.isNotBlank() && text.endsWith("."))
                n++
            }
        }
        assertTrue(n > 1000)
    }

    @Test fun realDataEveryMoveGetsASentence() {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) }
        assumeTrue("ANKIGO_REAL_DATA not set", dir != null)
        fun text(vararg names: String) = names.map { File(dir!!, it) }.first { it.isFile }.readText()
        val d = Parsers.build(Parsers.Sources(
            pokedexJson = text("pokedex.json"), movesJson = text("moves.json"), learnsetsJson = text("learnsets.json"),
            speciesCsv = text("pokemon_species.csv", "species.csv"), pokemonCsv = text("pokemon.csv"), statsCsv = text("pokemon_stats.csv"),
            evolutionCsv = text("pokemon_evolution.csv"), experienceCsv = text("experience.csv"), itemsCsv = text("items.csv"),
        ))
        assertTrue(d.moves.size > 800)
        d.moves.values.forEach { m ->
            val t = say(m)
            assertTrue("${m.id}: '$t'", t.isNotBlank() && t.endsWith("."))
        }
    }
}
