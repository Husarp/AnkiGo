package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C9b.attacks
import com.husarp.ankigo.game.R17C9b.effects
import com.husarp.ankigo.game.R17C9b.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17C-9b: the rarer abilities, part 1 - what a target takes in or is immune to, and the damage formula (type boosts, cuts, auras, Ruin abilities,
 * Normalize and the -ate abilities, weather and terrain boosts, accuracy). Fixtures in [R17C9b]; `low` makes every chance happen.
 */
class R17C9bAbilitiesTest {
    private fun near(what: String, expected: Double, actual: Double) = assertEquals(what, expected, actual, 0.05)

    // ---------------------------------------------------------------- immunities of the target

    @Test fun bulletproofStopsBulletMovesAndOnlyThose() {
        val w = R17C9b.wild("Bulletproof", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "shadowball", "sludgebomb", "tackle")
        val e = R17C9b.round(m, b, "shadowball")
        assertEquals(R17C9b.maxHp(w), w.hp)
        assertTrue(e.attacks().single().failed)
        assertTrue(e.lines().any { "Bulletproof: bullet moves don't affect it." in it })
        R17C9b.round(m, b, "sludgebomb")
        assertEquals("Sludge Bomb is a bomb move too", R17C9b.maxHp(w), w.hp)
        R17C9b.round(m, b, "tackle")
        assertTrue("other moves hit", w.hp < R17C9b.maxHp(w))
        // Mold Breaker
        val w2 = R17C9b.wild("Bulletproof", 801, "tackle"); val b2 = R17C9b.battle(w2)
        R17C9b.round(R17C9b.main("Mold Breaker", "sludgebomb"), b2, "sludgebomb")
        assertTrue("Mold Breaker", w2.hp < R17C9b.maxHp(w2))
    }

    @Test fun windRiderTakesInWindMovesAndRaisesAttack() {
        val w = R17C9b.wild("Wind Rider", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "gust", "icywind", "tackle")
        val e = R17C9b.round(m, b, "gust")
        assertEquals(R17C9b.maxHp(w), w.hp)
        assertEquals(1, b.enemy.boosts[0])
        assertTrue(e.attacks().single().failed)
        assertTrue(e.lines().any { "Wind Rider: it absorbed the move!" in it })
        R17C9b.round(m, b, "icywind")
        assertEquals("another wind move", 2, b.enemy.boosts[0])
        R17C9b.round(m, b, "tackle")
        assertTrue(w.hp < R17C9b.maxHp(w))
    }

    @Test fun windRiderRisesWhenItsOwnTailwindStartsOrWhenItEntersUnderOne() {
        val w = R17C9b.wild("Wind Rider", 801, "tailwind", "tackle"); val b = R17C9b.battle(w)
        val e = R17C9b.round(R17C9b.main("", "tackle"), b, "tackle", "tailwind")
        assertEquals("its Tailwind", 1, b.enemy.boosts[0])
        assertTrue(e.lines().any { "Wind Rider!" in it })
        // the Main enters with a Tailwind up (it was used before a switch)
        val m = R17C9b.main("Wind Rider", "tackle"); val b2 = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        b2.field.player["tailwind"] = 3
        R17C9b.round(m, b2, "tackle")
        assertEquals("entering under Tailwind", 1, b2.player.boosts[0])
    }

    @Test fun earthEaterTakesInGroundMovesAndHeals() {
        val w = R17C9b.wild("Earth Eater", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "earthquake", "sandattack", "tackle")
        val max = R17C9b.maxHp(w)
        w.hp = max - 300
        val e = R17C9b.round(m, b, "earthquake")
        assertEquals(max - 300 + max / 4, w.hp)
        assertTrue(e.attacks().single().failed)
        assertEquals("the float label is the ability", "Earth Eater", e.effects().first { it.kind == EffectKind.HEAL }.label)
        R17C9b.round(m, b, "sandattack")
        assertEquals("a Ground status move is taken in too", 0, b.enemy.boosts[5])
        w.hp = max
        val e2 = R17C9b.round(m, b, "earthquake")
        assertTrue(e2.lines().any { "Earth Eater: it isn't affected." in it })
        R17C9b.round(m, b, "tackle")
        assertTrue(w.hp < max)
    }

    @Test fun wellBakedBodyTakesInFireMovesAndRaisesDefense() {
        val w = R17C9b.wild("Well-Baked Body", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "ember", "tackle")
        val e = R17C9b.round(m, b, "ember")
        assertEquals(R17C9b.maxHp(w), w.hp)
        assertEquals(2, b.enemy.boosts[1])
        assertTrue(e.lines().any { "Well-Baked Body: it absorbed the move!" in it })
    }

    @Test fun goodAsGoldStopsStatusMovesAimedAtItButNotItsOwn() {
        val w = R17C9b.wild("Good as Gold", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "thunderwave", "growl", "tackle")
        val e = R17C9b.round(m, b, "thunderwave")
        assertNull(b.enemy.status)
        assertTrue(e.lines().any { "Good as Gold: status moves don't affect it." in it })
        R17C9b.round(m, b, "growl")
        assertEquals(0, b.enemy.boosts[0])
        R17C9b.round(m, b, "tackle")
        assertTrue("attacks hit", w.hp < R17C9b.maxHp(w))
        // its own status move works
        val main = R17C9b.main("Good as Gold", "swordsdance"); val b2 = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        R17C9b.round(main, b2, "swordsdance")
        assertEquals(2, b2.player.boosts[0])
    }

    @Test fun wonderGuardOnlyLetsSuperEffectiveMovesThrough() {
        val w = R17C9b.wild("Wonder Guard", 820, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "watergun", "ember", "crunch")
        val e = R17C9b.round(m, b, "watergun") // Water against Bug / Ghost: neutral
        assertEquals(R17C9b.maxHp(w), w.hp)
        assertTrue(e.attacks().single().failed)
        assertTrue(e.lines().any { "Wonder Guard: only super effective moves hit it." in it })
        R17C9b.round(m, b, "ember") // Fire against Bug / Ghost: super effective
        assertTrue(w.hp < R17C9b.maxHp(w))
        // the Smart move choice leaves out the move that surely fails
        val v = SimpleView.of(R17C9b.data, b, m)
        assertEquals(false, rulesFirst { it.useful(v, true, R17C9b.data.move("watergun")!!) })
        assertNull(rulesFirst { it.useful(v, true, R17C9b.data.move("ember")!!) })
        // Mold Breaker
        val w2 = R17C9b.wild("Wonder Guard", 820, "tackle"); val b2 = R17C9b.battle(w2)
        R17C9b.round(R17C9b.main("Mold Breaker", "watergun"), b2, "watergun")
        assertTrue(w2.hp < R17C9b.maxHp(w2))
    }

    @Test fun dazzlingQueenlyMajestyAndArmorTailStopMovesThatGoFirst() {
        for (ab in listOf("Dazzling", "Queenly Majesty", "Armor Tail")) {
            val w = R17C9b.wild(ab, 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "quickattack", "tackle")
            val e = R17C9b.round(m, b, "quickattack")
            assertEquals(ab, R17C9b.maxHp(w), w.hp)
            assertTrue(ab, e.attacks().single().failed)
            assertTrue(ab, e.lines().any { "$ab: moves that go first can't be used against it." in it })
            R17C9b.round(m, b, "tackle")
            assertTrue("$ab: a move that does not go first hits", w.hp < R17C9b.maxHp(w))
        }
        // Prankster's status move goes first, so it is stopped too
        val w = R17C9b.wild("Dazzling", 801, "tackle"); val b = R17C9b.battle(w)
        R17C9b.round(R17C9b.main("Prankster", "growl"), b, "growl")
        assertEquals(0, b.enemy.boosts[0])
    }

    // ---------------------------------------------------------------- the damage formula: boosts of the user

    @Test fun sandForceBoostsRockGroundAndSteelMovesInASandstorm() {
        val sand = { b: Battle, _: Mon -> b.field.weather = "sand"; b.field.weatherTurns = 5 }
        near("Ground in sand", 1.3, R17C9b.ratio("Sand Force", "", "earthquake", setup = sand))
        near("Rock in sand", 1.3, R17C9b.ratio("Sand Force", "", "rockslide", mainMoves = listOf("rockslide"), setup = sand))
        near("Steel in sand", 1.3, R17C9b.ratio("Sand Force", "", "ironhead", setup = sand))
        near("Normal in sand", 1.0, R17C9b.ratio("Sand Force", "", "doubleedge", setup = sand))
        near("no sand", 1.0, R17C9b.ratio("Sand Force", "", "earthquake"))
    }

    @Test fun sharpnessBoostsSlicingMoves() {
        near("slicing", 1.5, R17C9b.ratio("Sharpness", "", "aerialace"))
        near("not slicing", 1.0, R17C9b.ratio("Sharpness", "", "doubleedge"))
    }

    @Test fun toxicBoostAndFlareBoostNeedTheirStatusAndTheirKindOfMove() {
        val poisoned = { b: Battle, _: Mon -> b.player.status = "psn" }
        near("Toxic Boost physical", 1.5, R17C9b.ratio("Toxic Boost", "", "doubleedge", setup = poisoned))
        near("Toxic Boost special", 1.0, R17C9b.ratio("Toxic Boost", "", "hyperbeam", setup = poisoned))
        near("Toxic Boost healthy", 1.0, R17C9b.ratio("Toxic Boost", "", "doubleedge"))
        val burned = { b: Battle, _: Mon -> b.player.status = "brn" }
        near("Flare Boost special", 1.5, R17C9b.ratio("Flare Boost", "", "hyperbeam", setup = burned))
        near("Flare Boost physical", 1.0, R17C9b.ratio("Flare Boost", "", "doubleedge", setup = burned))
    }

    @Test fun punkRockBoostsItsSoundMovesAndCutsSoundMovesAgainstIt() {
        near("its sound move", 1.3, R17C9b.ratio("Punk Rock", "", "hypervoice"))
        near("its other move", 1.0, R17C9b.ratio("Punk Rock", "", "doubleedge"))
        near("sound against it", 0.5, R17C9b.ratio("", "Punk Rock", "hypervoice"))
        near("other moves against it", 1.0, R17C9b.ratio("", "Punk Rock", "doubleedge"))
    }

    @Test fun theTypeBoostAbilitiesBoostTheirType() {
        near("Transistor", 1.3, R17C9b.ratio("Transistor", "", "thundershock"))
        near("Dragon's Maw", 1.5, R17C9b.ratio("Dragon's Maw", "", "dragonclaw"))
        near("Rocky Payload", 1.5, R17C9b.ratio("Rocky Payload", "", "rockslide", mainMoves = listOf("rockslide")))
        near("Steelworker", 1.5, R17C9b.ratio("Steelworker", "", "ironhead"))
        near("Steely Spirit", 1.5, R17C9b.ratio("Steely Spirit", "", "ironhead"))
        near("another type", 1.0, R17C9b.ratio("Steelworker", "", "doubleedge"))
        near("Transistor, a Fire move", 1.0, R17C9b.ratio("Transistor", "", "flamethrower"))
    }

    /** The type [move] has when a Main with [ability] uses it on the wild one (the type the damage formula and the immunities see). */
    private fun typeOf(ability: String, move: String): Type {
        val m = R17C9b.main(ability, move); val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        return BattleEngine.newHit(SimpleView.of(R17C9b.data, b, m), true, m, b.player, b.wild, b.enemy, R17C9b.data.move(move)!!).type
    }

    @Test fun normalizeMakesEveryMoveNormalAndTheAteAbilitiesChangeNormalMoves() {
        // Normalize: Water Gun becomes Normal (the Main is Normal: STAB) and x1.2
        assertEquals(Type.NORMAL, typeOf("Normalize", "watergun"))
        assertEquals("a status move keeps its type", Type.ELECTRIC, typeOf("Normalize", "thunderwave"))
        near("Normalize x1.2 and STAB", 1.2 * 1.5, R17C9b.ratio("Normalize", "", "hydropump"))
        near("a Normal move", 1.2, R17C9b.ratio("Normalize", "", "doubleedge"))
        // the -ate abilities: Normal moves change type (STAB is lost for a Normal Main), x1.2
        for ((ab, type) in listOf("Refrigerate" to Type.ICE, "Pixilate" to Type.FAIRY, "Aerilate" to Type.FLYING, "Galvanize" to Type.ELECTRIC)) {
            assertEquals(ab, type, typeOf(ab, "tackle"))
            near("$ab", 1.2 / 1.5, R17C9b.ratio(ab, "", "doubleedge"))
            assertEquals("$ab leaves other types alone", Type.WATER, typeOf(ab, "watergun"))
            assertEquals("$ab leaves a plain Main alone", Type.NORMAL, typeOf("", "tackle"))
        }
    }

    @Test fun analyticBoostsAMoveThatComesLast() {
        fun taken(ab: String, wildMove: String?): Int {
            val w = R17C9b.wild(ab, 801, "doubleedge"); val b = R17C9b.battle(w); val m = R17C9b.main("", "tackle")
            R17C9b.round(m, b, "tackle", wildMove)
            return R17C9b.maxHp(m) - m.hp
        }
        assertEquals("the wild one is slower, so it comes last", taken("", "doubleedge") * 1.3, taken("Analytic", "doubleedge").toDouble(), 3.0)
        // the Main is faster and moves first: no boost; when the wild one does not move at all, the Main comes last
        fun dealt(ab: String, wildActs: Boolean): Int {
            val w = R17C9b.wild("", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main(ab, "doubleedge")
            R17C9b.round(m, b, "doubleedge", if (wildActs) "tackle" else null)
            return R17C9b.maxHp(w) - w.hp
        }
        assertEquals("first mover", dealt("", true), dealt("Analytic", true))
        assertEquals("nobody left to move after it", dealt("", false) * 1.3, dealt("Analytic", false).toDouble(), 3.0)
    }

    @Test fun fairyAuraDarkAuraAndAuraBreak() {
        near("Fairy Aura", 1.33, R17C9b.ratio("", "Fairy Aura", "moonblast"))
        near("it boosts the user's moves too", 1.33, R17C9b.ratio("Fairy Aura", "", "moonblast"))
        near("another type", 1.0, R17C9b.ratio("", "Fairy Aura", "crunch"))
        near("Dark Aura", 1.33, R17C9b.ratio("", "Dark Aura", "crunch"))
        near("Aura Break reverses it", 0.75, R17C9b.ratio("Aura Break", "Fairy Aura", "moonblast"))
        near("Aura Break alone", 1.0, R17C9b.ratio("Aura Break", "", "moonblast"))
        near("one boost only", 1.33, R17C9b.ratio("Fairy Aura", "Fairy Aura", "moonblast"))
    }

    @Test fun theRuinAbilitiesWeakenTheOtherSidesStat() {
        near("Tablets of Ruin: the foe's Attack", 0.75, R17C9b.ratio("", "Tablets of Ruin", "doubleedge"))
        near("Tablets of Ruin: not the Sp. Atk", 1.0, R17C9b.ratio("", "Tablets of Ruin", "hyperbeam"))
        near("Vessel of Ruin: the foe's Sp. Atk", 0.75, R17C9b.ratio("", "Vessel of Ruin", "hyperbeam"))
        near("Sword of Ruin: the target's Defense", 1.0 / 0.75, R17C9b.ratio("Sword of Ruin", "", "doubleedge"))
        near("Sword of Ruin: not its Sp. Def", 1.0, R17C9b.ratio("Sword of Ruin", "", "hyperbeam"))
        near("Beads of Ruin: the target's Sp. Def", 1.0 / 0.75, R17C9b.ratio("Beads of Ruin", "", "hyperbeam"))
        near("it weakens the wild one's attacks on the Main too", 0.75, R17C9b.ratioFromWild("", "Tablets of Ruin", "doubleedge"))
    }

    @Test fun solarPowerRaisesSpAtkInTheSunAndCostsHp() {
        val sun = { b: Battle, _: Mon -> b.field.weather = "sun"; b.field.weatherTurns = 5 }
        near("special in sun", 1.5, R17C9b.ratio("Solar Power", "", "hyperbeam", setup = sun))
        near("physical in sun", 1.0, R17C9b.ratio("Solar Power", "", "doubleedge", setup = sun))
        near("no sun", 1.0, R17C9b.ratio("Solar Power", "", "hyperbeam"))
        val m = R17C9b.main("Solar Power", "tackle"); val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        b.field.weather = "sun"; b.field.weatherTurns = 5
        val e = R17C9b.round(m, b, "tackle")
        assertEquals(R17C9b.maxHp(m) - R17C9b.maxHp(m) / 8, m.hp)
        assertTrue(e.lines().any { "was hurt by its Solar Power!" in it })
        val m2 = R17C9b.main("Solar Power", "tackle"); val b2 = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        R17C9b.round(m2, b2, "tackle")
        assertEquals("no sun, no loss", R17C9b.maxHp(m2), m2.hp)
        // Magic Guard keeps the loss
        val m3 = R17C9b.main("Magic Guard", "tackle"); val w3 = R17C9b.wild("Solar Power", 801, "tackle"); val b3 = R17C9b.battle(w3)
        b3.field.weather = "sun"; b3.field.weatherTurns = 5
        R17C9b.round(m3, b3, "tackle")
        assertTrue(w3.hp < R17C9b.maxHp(w3))
    }

    @Test fun defeatistHalvesAttackAtHalfHpOrLess() {
        near("at half", 0.5, R17C9b.ratio("Defeatist", "", "doubleedge", setup = { _, m -> m.hp = R17C9b.maxHp(m) / 2 }))
        near("above half", 1.0, R17C9b.ratio("Defeatist", "", "doubleedge", setup = { _, m -> m.hp = R17C9b.maxHp(m) / 2 + 5 }))
        near("special too", 0.5, R17C9b.ratio("Defeatist", "", "hyperbeam", setup = { _, m -> m.hp = 3 }))
    }

    @Test fun orichalcumPulseAndHadronEngineBoostInTheirField() {
        val sun = { b: Battle, _: Mon -> b.field.weather = "sun"; b.field.weatherTurns = 5 }
        near("Attack in the sun", 1.33, R17C9b.ratio("Orichalcum Pulse", "", "doubleedge", setup = sun))
        near("not the Sp. Atk", 1.0, R17C9b.ratio("Orichalcum Pulse", "", "hyperbeam", setup = sun))
        near("no sun", 1.0, R17C9b.ratio("Orichalcum Pulse", "", "doubleedge"))
        val terrain = { b: Battle, _: Mon -> b.field.terrain = "electric"; b.field.terrainTurns = 5 }
        near("Sp. Atk in Electric Terrain", 1.33, R17C9b.ratio("Hadron Engine", "", "hyperbeam", setup = terrain))
        near("not the Attack", 1.0, R17C9b.ratio("Hadron Engine", "", "doubleedge", setup = terrain))
    }

    @Test fun gorillaTacticsRaisesAttack() {
        near("Attack", 1.5, R17C9b.ratio("Gorilla Tactics", "", "doubleedge"))
        near("not Sp. Atk", 1.0, R17C9b.ratio("Gorilla Tactics", "", "hyperbeam"))
    }

    @Test fun protosynthesisAndQuarkDriveRaiseTheHighestStat() {
        // Fastie: Speed is the highest base stat
        val sun = { b: Battle, _: Mon -> b.field.weather = "sun"; b.field.weatherTurns = 5 }
        val m = R17C9b.main("Protosynthesis", "tackle"); val b = R17C9b.battle(R17C9b.wild("", 801, "tackle")); sun(b, m)
        val v = SimpleView.of(R17C9b.data, b, m)
        val plain = StatCalc.stats(R17C9b.data, m)
        assertEquals((plain.spe * 1.5).toInt(), v.stats(true).spe)
        assertEquals(plain.atk, v.stats(true).atk)
        b.field.weather = null
        assertEquals("no sun", plain.spe, v.stats(true).spe)
        // a Pokémon whose Attack is the highest: Attack x1.3
        val atkMon = R17C9b.mon(819, 100, "Quark Drive", "tackle"); val b2 = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        b2.field.terrain = "electric"; b2.field.terrainTurns = 5
        val v2 = SimpleView.of(R17C9b.data, b2, atkMon)
        val plain2 = StatCalc.stats(R17C9b.data, atkMon)
        val best = listOf("atk" to plain2.atk, "def" to plain2.def, "spa" to plain2.spa, "spd" to plain2.spd, "spe" to plain2.spe).maxByOrNull { it.second }!!
        val after = v2.stats(true)
        val got = mapOf("atk" to after.atk, "def" to after.def, "spa" to after.spa, "spd" to after.spd, "spe" to after.spe)
        assertEquals((best.second * (if (best.first == "spe") 1.5 else 1.3)).toInt(), got[best.first])
        // the sun does not wake Quark Drive
        b2.field.terrain = null; b2.field.weather = "sun"
        assertEquals(plain2.atk, v2.stats(true).atk)
    }

    // ---------------------------------------------------------------- the damage formula: cuts of the target

    @Test fun fluffyHalvesContactAndDoublesFire() {
        near("contact", 0.5, R17C9b.ratio("", "Fluffy", "doubleedge"))
        near("Fire", 2.0, R17C9b.ratio("", "Fluffy", "flamethrower"))
        near("Fire with contact", 1.0, R17C9b.ratio("", "Fluffy", "flamecharge"))
        near("another move", 1.0, R17C9b.ratio("", "Fluffy", "hyperbeam"))
    }

    @Test fun furCoatMarvelScaleIceScalesAndPurifyingSaltCutDamage() {
        near("Fur Coat, physical", 0.5, R17C9b.ratio("", "Fur Coat", "doubleedge"))
        near("Fur Coat, special", 1.0, R17C9b.ratio("", "Fur Coat", "hyperbeam"))
        near("Marvel Scale with a status", 1.0 / 1.5, R17C9b.ratio("", "Marvel Scale", "doubleedge") { b, _ -> b.enemy.status = "par" })
        near("Marvel Scale without", 1.0, R17C9b.ratio("", "Marvel Scale", "doubleedge"))
        near("Ice Scales, special", 0.5, R17C9b.ratio("", "Ice Scales", "hyperbeam"))
        near("Ice Scales, physical", 1.0, R17C9b.ratio("", "Ice Scales", "doubleedge"))
        near("Purifying Salt, Ghost", 0.5, R17C9b.ratio("", "Purifying Salt", "shadowball", wildSp = 802))
        near("Purifying Salt, another type", 1.0, R17C9b.ratio("", "Purifying Salt", "doubleedge"))
    }

    @Test fun multiscaleAndShadowShieldHalveTheHitAtFullHp() {
        for (ab in listOf("Multiscale", "Shadow Shield")) {
            near("$ab at full HP", 0.5, R17C9b.ratio("", ab, "doubleedge"))
            near("$ab hurt", 1.0, R17C9b.ratio("", ab, "doubleedge") { b, _ -> b.wild.hp -= 10 })
        }
    }

    @Test fun prismArmorCutsSuperEffectiveHits() {
        near("super effective", 0.75, R17C9b.ratio("", "Prism Armor", "hydropump", wildSp = 806))
        near("neutral", 1.0, R17C9b.ratio("", "Prism Armor", "doubleedge"))
        near("not very effective", 1.0, R17C9b.ratio("", "Prism Armor", "doubleedge", wildSp = 806))
    }

    @Test fun waterBubbleHalvesFireAndDoublesItsWater() {
        near("Fire against it", 0.5, R17C9b.ratio("", "Water Bubble", "flamethrower"))
        near("its Water", 2.0, R17C9b.ratio("Water Bubble", "", "hydropump"))
        near("its other moves", 1.0, R17C9b.ratio("Water Bubble", "", "doubleedge"))
    }

    // ---------------------------------------------------------------- accuracy and stages

    @Test fun victoryStarAndWonderSkinChangeAccuracy() {
        val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        assertEquals(110.0, R17C9b.accuracy(b, R17C9b.main("Victory Star", "tackle"), "tackle")!!, 0.001)
        assertEquals(100.0, R17C9b.accuracy(b, R17C9b.main("", "tackle"), "tackle")!!, 0.001)
        val b2 = R17C9b.battle(R17C9b.wild("Wonder Skin", 801, "tackle"))
        assertEquals("a status move", 50.0, R17C9b.accuracy(b2, R17C9b.main("", "thunderwave"), "thunderwave")!!, 0.001)
        assertEquals("an attack", 100.0, R17C9b.accuracy(b2, R17C9b.main("", "tackle"), "tackle")!!, 0.001)
        assertNull("a move that never misses stays so", R17C9b.accuracy(b2, R17C9b.main("", "swordsdance"), "swordsdance"))
    }

    @Test fun illuminateIgnoresEvasionAndKeepsAccuracy() {
        val w = R17C9b.wild("", 801, "tackle"); val b = R17C9b.battle(w); b.enemy.boosts[6] = 6
        val plainMain = R17C9b.main("", "tackle")
        val plain = BattleEngine.accuracyStage(BattleEngine.newHit(SimpleView.of(R17C9b.data, b, plainMain), true, plainMain, b.player, w, b.enemy, R17C9b.data.move("tackle")!!))
        assertEquals(-6, plain)
        val m = R17C9b.main("Illuminate", "tackle")
        val h = BattleEngine.newHit(SimpleView.of(R17C9b.data, b, m), true, m, b.player, w, b.enemy, R17C9b.data.move("tackle")!!)
        assertEquals("evasion ignored", 0, BattleEngine.accuracyStage(h))
        // the other side can't lower its accuracy
        val w2 = R17C9b.wild("Illuminate", 801, "tackle"); val b2 = R17C9b.battle(w2)
        val e = R17C9b.round(R17C9b.main("", "sandattack"), b2, "sandattack")
        assertEquals(0, b2.enemy.boosts[5])
        assertTrue(e.lines().any { "Illuminate prevents accuracy loss!" in it })
    }

    @Test fun fullMetalBodyAndFlowerVeilAndMirrorArmorHandleStatDrops() {
        val w = R17C9b.wild("Full Metal Body", 801, "tackle"); val b = R17C9b.battle(w)
        val e = R17C9b.round(R17C9b.main("", "growl"), b, "growl")
        assertEquals(0, b.enemy.boosts[0])
        assertTrue(e.lines().any { "Full Metal Body prevents stat loss!" in it })
        // Mold Breaker does not get past it (not a breakable ability)
        val w2 = R17C9b.wild("Full Metal Body", 801, "tackle"); val b2 = R17C9b.battle(w2)
        R17C9b.round(R17C9b.main("Mold Breaker", "growl"), b2, "growl")
        assertEquals(0, b2.enemy.boosts[0])
        // Flower Veil: a Grass type only
        val grass = R17C9b.wild("Flower Veil", 802, "tackle"); val b3 = R17C9b.battle(grass)
        R17C9b.round(R17C9b.main("", "growl"), b3, "growl")
        assertEquals(0, b3.enemy.boosts[0])
        val notGrass = R17C9b.wild("Flower Veil", 801, "tackle"); val b4 = R17C9b.battle(notGrass)
        R17C9b.round(R17C9b.main("", "growl"), b4, "growl")
        assertEquals(-1, b4.enemy.boosts[0])
    }

    @Test fun mirrorArmorBouncesDropsBackAndItsOwnDropsStay() {
        val w = R17C9b.wild("Mirror Armor", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "growl", "swordsdance")
        val e = R17C9b.round(m, b, "growl")
        assertEquals("it keeps its Attack", 0, b.enemy.boosts[0])
        assertEquals("the user gets the drop", -1, b.player.boosts[0])
        assertTrue(e.lines().any { "Mirror Armor!" in it })
        // a stage that is at the limit is not bounced
        b.enemy.boosts[0] = -6
        R17C9b.round(m, b, "growl")
        assertEquals(-1, b.player.boosts[0])
        // two Mirror Armors: one bounce
        val w2 = R17C9b.wild("Mirror Armor", 801, "tackle"); val b2 = R17C9b.battle(w2)
        R17C9b.round(R17C9b.main("Mirror Armor", "growl"), b2, "growl")
        assertEquals(0, b2.enemy.boosts[0])
        assertEquals(-1, b2.player.boosts[0])
    }

    @Test fun opportunistCopiesTheOtherSidesRise() {
        val w = R17C9b.wild("Opportunist", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "swordsdance")
        val e = R17C9b.round(m, b, "swordsdance")
        assertEquals(2, b.player.boosts[0])
        assertEquals("copied", 2, b.enemy.boosts[0])
        assertTrue(e.lines().any { "Opportunist!" in it })
        // a drop of the other side is not copied; two Opportunists don't copy each other's copy
        val w2 = R17C9b.wild("Opportunist", 801, "tackle"); val b2 = R17C9b.battle(w2)
        R17C9b.round(R17C9b.main("Opportunist", "swordsdance"), b2, "swordsdance")
        assertEquals(2, b2.player.boosts[0])
        assertEquals(2, b2.enemy.boosts[0])
    }

    @Test fun guardDogLikesIntimidate() {
        val w = R17C9b.wild("Guard Dog", 801, "tackle"); val b = R17C9b.battle(w)
        val e = R17C9b.round(R17C9b.main("Intimidate", "tackle"), b, "tackle")
        assertEquals(1, b.enemy.boosts[0])
        assertTrue(e.lines().any { "Guard Dog!" in it })
        // other drops still work
        val w2 = R17C9b.wild("Guard Dog", 801, "tackle"); val b2 = R17C9b.battle(w2)
        R17C9b.round(R17C9b.main("", "growl"), b2, "growl")
        assertEquals(-1, b2.enemy.boosts[0])
    }

    @Test fun lightMetalAndHeavyMetalChangeTheWeightForWeightMoves() {
        // Low Kick: the power steps with the target's weight. The test species weigh the default weight; halving or doubling it moves the step
        fun power(ab: String): Int? {
            val w = R17C9b.wild(ab, 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "lowkick")
            val h = BattleEngine.newHit(SimpleView.of(R17C9b.data, b, m), true, m, b.player, w, b.enemy, R17C9b.data.move("lowkick")!!)
            return rulesFirst { it.basePower(h) }
        }
        val plain = power("")!!
        val light = power("Light Metal")!!
        val heavy = power("Heavy Metal")!!
        assertTrue("light $light <= plain $plain <= heavy $heavy", light <= plain && plain <= heavy)
        assertTrue("the weight made a difference", light < heavy)
        assertEquals(0.5, AbilitiesMore.LIGHT_METAL, 0.0)
        assertEquals(2.0, AbilitiesMore.HEAVY_METAL, 0.0)
    }

    @Test fun mimicryAndColorChangeChangeTheType() {
        val w = R17C9b.wild("Mimicry", 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "tackle")
        val v = SimpleView.of(R17C9b.data, b, m)
        assertEquals(listOf(Type.NORMAL), v.types(false))
        b.field.terrain = "grassy"
        assertEquals(listOf(Type.GRASS), v.types(false))
        b.field.terrain = "misty"
        assertEquals(listOf(Type.FAIRY), v.types(false))
        b.field.terrain = null
        assertEquals("back to its own", listOf(Type.NORMAL), v.types(false))
        // Color Change
        val w2 = R17C9b.wild("Color Change", 801, "tackle"); val b2 = R17C9b.battle(w2)
        val e = R17C9b.round(R17C9b.main("", "watergun", "tackle"), b2, "watergun")
        assertEquals(listOf(Type.WATER), SimpleView.of(R17C9b.data, b2, m).types(false))
        assertTrue(e.lines().any { "Color Change made it the Water type!" in it })
        // it does not change to a type it has
        val w3 = R17C9b.wild("Color Change", 801, "tackle"); val b3 = R17C9b.battle(w3)
        val e3 = R17C9b.round(R17C9b.main("", "tackle"), b3, "tackle")
        assertFalse(e3.lines().any { "Color Change" in it })
    }

    @Test fun cloudNineAndAirLockSuppressTheWeatherForEveryone() {
        for (ab in listOf("Cloud Nine", "Air Lock")) {
            val w = R17C9b.wild(ab, 801, "tackle"); val b = R17C9b.battle(w); val m = R17C9b.main("", "tackle")
            b.field.weather = "rain"; b.field.weatherTurns = 5
            val v = SimpleView.of(R17C9b.data, b, m)
            assertNull(ab, v.weather())
            near("$ab: rain does not boost Water (the plain damage gets the boost)", 1.0 / 1.5, R17C9b.ratio(ab, "", "hydropump") { bb, _ -> bb.field.weather = "rain"; bb.field.weatherTurns = 5 })
        }
        val b = R17C9b.battle(R17C9b.wild("", 801, "tackle")); b.field.weather = "rain"
        assertEquals("rain", SimpleView.of(R17C9b.data, b, R17C9b.main("", "tackle")).weather())
    }

    @Test fun slushRushAndSurgeSurferDoubleSpeed() {
        val b = R17C9b.battle(R17C9b.wild("", 801, "tackle"))
        val m = R17C9b.main("Slush Rush", "tackle")
        val v = SimpleView.of(R17C9b.data, b, m)
        assertEquals(1.0, rulesProduct { it.speedMod(v, true) }, 0.0)
        b.field.weather = "hail"
        assertEquals(2.0, rulesProduct { it.speedMod(v, true) }, 0.0)
        b.field.weather = "snow"
        assertEquals(2.0, rulesProduct { it.speedMod(v, true) }, 0.0)
        b.field.weather = "sun"
        assertEquals(1.0, rulesProduct { it.speedMod(v, true) }, 0.0)
        val m2 = R17C9b.main("Surge Surfer", "tackle")
        val v2 = SimpleView.of(R17C9b.data, b, m2)
        assertEquals(1.0, rulesProduct { it.speedMod(v2, true) }, 0.0)
        b.field.terrain = "electric"
        assertEquals(2.0, rulesProduct { it.speedMod(v2, true) }, 0.0)
    }
}
