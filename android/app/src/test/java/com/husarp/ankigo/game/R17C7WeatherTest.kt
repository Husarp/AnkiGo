package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C7.attacks
import com.husarp.ankigo.game.R17C7.effects
import com.husarp.ankigo.game.R17C7.fields
import com.husarp.ankigo.game.R17C7.kinds
import com.husarp.ankigo.game.R17C7.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R17C-7: weather. The five weathers and the moves that start them (lines, floats, 5 rounds, replace / same again), sandstorm and hail damage,
 * the damage boosts, accuracy, Solar Beam, Weather Ball, Growth, "nobody is frozen in the sun", suppressed weather, the move choice and the texts.
 * Fixtures in [R17C7] (the Main is faster; level 100 so the damage numbers are big enough to compare factors).
 */
class R17C7WeatherTest {
    private val data = R17C7.data
    private fun main(vararg ids: String) = R17C7.main(*ids)
    private fun battle(wildSp: Int = 701, vararg ids: String) = R17C7.battle(R17C7.wild(wildSp, *(if (ids.isEmpty()) arrayOf("tackle") else ids)))
    private fun field(b: Battle, weather: String?, turns: Int = 5) { b.field.weather = weather; b.field.weatherTurns = turns }

    private val starts = listOf(
        Triple("raindance", "rain", "It started to rain!" to "Rain"),
        Triple("sunnyday", "sun", "The sunlight turned harsh!" to "Harsh sun"),
        Triple("sandstorm", "sand", "A sandstorm kicked up!" to "Sandstorm"),
        Triple("hail", "hail", "It started to hail!" to "Hail"),
        Triple("snowscape", "snow", "It started to snow!" to "Snow"),
        Triple("chillyreception", "snow", "It started to snow!" to "Snow"),
    )
    private val ends = mapOf(
        "rain" to "The rain stopped.", "sun" to "The sunlight faded.", "sand" to "The sandstorm subsided.", "hail" to "The hail stopped.", "snow" to "The snow stopped.",
    )

    // ---------------------------------------------------------------- the moves

    @Test fun eachWeatherMoveStartsItsWeatherWithItsLineAndFloatLabel() {
        for ((move, key, text) in starts) {
            val b = battle(); val e = R17C7.play(main(move), b, listOf(move)).single()
            val f = e.fields().single { it.stages == 1 }
            assertEquals(move, text.first, f.text)
            assertEquals(move, text.second, f.label)
            assertEquals(move, key, f.stat)
            assertTrue("$move shows on the user's side", f.onPlayer)
            assertFalse(move, e.attacks().single().failed)
            assertEquals(move, key, b.field.weather)
            assertEquals("$move: 5 rounds, the round it started in counts", 4, b.field.weatherTurns)
        }
    }

    @Test fun theWildOneStartsWeatherToo() {
        val b = battle(); val e = R17C7.play(main("tackle"), b, listOf(null), listOf("raindance")).single()
        assertEquals("rain", b.field.weather)
        val f = e.fields().single()
        assertFalse(f.onPlayer)
        assertEquals("Rain", f.label)
    }

    @Test fun theSameWeatherAgainFailsAndKeepsTheCountdown() {
        val b = battle(); val m = main("raindance")
        val e = R17C7.play(m, b, listOf("raindance", "raindance"))
        assertTrue(e[1].attacks().single().failed)
        assertEquals(listOf("But it failed!"), e[1].lines())
        assertEquals("rain", b.field.weather)
        assertEquals("it was not restarted", 3, b.field.weatherTurns)
    }

    @Test fun aNewWeatherReplacesTheOldOne() {
        val b = battle(); val m = main("raindance")
        val e = R17C7.play(m, b, listOf("raindance", "sunnyday"))
        assertEquals("sun", b.field.weather)
        assertEquals(4, b.field.weatherTurns)
        assertEquals(listOf("The sunlight turned harsh!"), e[1].fields().map { it.text })
    }

    @Test fun aWeatherLastsFiveRoundsAndEndsWithItsLine() {
        for ((key, end) in ends) {
            val move = starts.first { it.second == key }.first
            val b = battle(); val m = main(move)
            val e = R17C7.play(m, b, listOf(move, "tackle", "tackle", "tackle", "tackle", "tackle"))
            for (i in 0..3) assertTrue("$key round ${i + 1} is not the end", e[i].fields().none { it.stages < 0 })
            assertEquals("$key ends at the end of round 5", listOf(end), e[4].fields().filter { it.stages < 0 }.map { it.text })
            assertNull(key, b.field.weather)
            assertEquals(0, b.field.weatherTurns)
            assertTrue("$key: nothing after the end", e[5].fields().isEmpty())
        }
    }

    @Test fun chillyReceptionOnlyBringsTheSnow() {
        val b = battle(); val m = main("chillyreception")
        val e = R17C7.play(m, b, listOf("chillyreception")).single()
        assertEquals("snow", b.field.weather)
        assertEquals(listOf("It started to snow!"), e.lines())
    }

    // ---------------------------------------------------------------- the public API

    @Test fun theApiStartsAWeatherForTheGivenSide() {
        val b = battle(); val m = main("tackle"); val r = R17C7.round(m, b)
        assertTrue(FieldRules.setWeather(r, "sand", 5, byP = false))
        val f = r.events.filterIsInstance<GameEvent.Effect>().single()
        assertFalse(f.onPlayer); assertEquals(EffectKind.FIELD, f.kind); assertEquals("Sandstorm", f.label); assertEquals("sand", f.stat); assertEquals(1, f.stages)
        assertEquals(5, b.field.weatherTurns)
        assertFalse("the same again changes nothing", FieldRules.setWeather(r, "sand", 5, byP = true))
        assertEquals(1, r.events.size)
        assertTrue(FieldRules.setWeather(r, "rain", 3, byP = true))
        assertEquals("rain", b.field.weather); assertEquals(3, b.field.weatherTurns)
    }

    @Test fun aWeatherWithZeroTurnsLastsUntilItIsReplaced() {
        val b = battle(); val m = main("tackle"); val r = R17C7.round(m, b)
        assertTrue(FieldRules.setWeather(r, "hail", 0, byP = true))
        repeat(8) { FieldRules.endOfRound(r) }
        assertEquals("hail", b.field.weather)
    }

    @Test fun theApiStartsATerrain() {
        val b = battle(); val m = main("tackle"); val r = R17C7.round(m, b)
        assertTrue(FieldRules.setTerrain(r, "grassy", 5, byP = true))
        assertEquals("grassy", b.field.terrain); assertEquals(5, b.field.terrainTurns)
        assertFalse(FieldRules.setTerrain(r, "grassy", 5, byP = true))
        assertTrue(FieldRules.clearTerrain(r, byP = true))
        assertNull(b.field.terrain)
        assertFalse(FieldRules.clearTerrain(r, byP = true))
    }

    // ---------------------------------------------------------------- sandstorm and hail damage

    @Test fun aSandstormHurtsEveryoneButRockGroundAndSteelTypes() {
        val hurt = mapOf(701 to true, 702 to false, 703 to false, 704 to false, 706 to true)
        for ((sp, takes) in hurt) {
            val b = battle(sp); val m = main("sandstorm")
            val e = R17C7.play(m, b, listOf("sandstorm")).single()
            val mine = e.kinds(EffectKind.RESIDUAL).filter { it.onPlayer }
            val theirs = e.kinds(EffectKind.RESIDUAL).filter { !it.onPlayer }
            assertEquals("the Main (Normal) is hurt next to species $sp", listOf("Fastie is buffeted by the sandstorm!"), mine.map { it.text })
            assertEquals(R17C7.maxHp(m) / 16, mine.single().amount)
            if (takes) {
                assertEquals(sp.toString(), listOf("${b.wild.name} is buffeted by the sandstorm!"), theirs.map { it.text })
                assertEquals(R17C7.maxHp(b.wild) / 16, theirs.single().amount)
                assertEquals(R17C7.maxHp(b.wild) - R17C7.maxHp(b.wild) / 16, b.wild.hp)
            } else {
                assertTrue("species $sp takes none", theirs.isEmpty())
                assertEquals(R17C7.maxHp(b.wild), b.wild.hp)
            }
        }
    }

    @Test fun hailHurtsEveryoneButIceTypes() {
        for ((sp, takes) in mapOf(701 to true, 705 to false, 702 to true)) {
            val b = battle(sp); val m = main("hail")
            val e = R17C7.play(m, b, listOf("hail")).single()
            val theirs = e.kinds(EffectKind.RESIDUAL).filter { !it.onPlayer }
            assertEquals("Fastie is buffeted by the hail!", e.kinds(EffectKind.RESIDUAL).single { it.onPlayer }.text)
            if (takes) assertEquals(listOf("${b.wild.name} is buffeted by the hail!"), theirs.map { it.text }) else assertTrue(theirs.isEmpty())
        }
    }

    @Test fun snowRainAndSunDoNotHurtAnybody() {
        for (move in listOf("snowscape", "raindance", "sunnyday")) {
            val b = battle(); val e = R17C7.play(main(move), b, listOf(move)).single()
            assertTrue(move, e.kinds(EffectKind.RESIDUAL).isEmpty())
        }
    }

    @Test fun weatherDamageSkipsTheLastRoundOfTheWeather() {
        val b = battle(); val m = main("sandstorm")
        val e = R17C7.play(m, b, listOf("sandstorm", "tackle", "tackle", "tackle", "tackle"))
        assertEquals("rounds 1-4 hurt, round 5 (the weather ends) does not", listOf(1, 1, 1, 1, 0), e.map { r -> r.kinds(EffectKind.RESIDUAL).count { it.onPlayer } })
    }

    @Test fun weatherDamageIsAtLeastOneAndCanFaint() {
        val b = battle(); val m = main("tackle"); m.hp = 1
        field(b, "hail")
        val e = R17C7.play(m, b, listOf("tackle")).single()
        assertEquals(0, m.hp)
        assertEquals(1, e.kinds(EffectKind.RESIDUAL).single { it.onPlayer }.amount)
        assertTrue(e.any { it is GameEvent.Fainted && it.player })
    }

    @Test fun someAbilitiesStopTheWeatherDamage() {
        // Magic Guard and Overcoat stop both; Sand Veil / Sand Rush / Sand Force only the sandstorm; Ice Body and Snow Cloak only the hail
        val cases = listOf(
            Triple("Magic Guard", "sandstorm", false), Triple("Overcoat", "hail", false), Triple("Sand Veil", "sandstorm", false), Triple("Sand Veil", "hail", true),
            Triple("Ice Body", "hail", false), Triple("Ice Body", "sandstorm", true), Triple("Snow Cloak", "hail", false), Triple("Sand Rush", "sandstorm", false),
        )
        for ((ability, move, hurt) in cases) {
            val b = battle(); val m = main(move); b.wild.ability = ability
            val e = R17C7.play(m, b, listOf(move)).single()
            assertEquals("$ability in $move", hurt, e.kinds(EffectKind.RESIDUAL).any { !it.onPlayer })
        }
    }

    // ---------------------------------------------------------------- damage

    @Test fun rainBoostsWaterAndWeakensFire() {
        for (wildSp in listOf(701, 711, 712)) {
            val m = main()
            assertEquals(1.5, R17C7.ratio(m, wildSp, "hydropump") { it.weather = "rain" }, 0.03)
            assertEquals(0.5, R17C7.ratio(m, wildSp, "flamethrower") { it.weather = "rain" }, 0.03)
            assertEquals(1.0, R17C7.ratio(m, wildSp, "plainspecial") { it.weather = "rain" }, 0.0)
        }
        // the wild one gets the same
        assertEquals(1.5, R17C7.ratio(main(), 701, "hydropump", byMain = false) { it.weather = "rain" }, 0.03)
    }

    @Test fun sunBoostsFireAndWeakensWater() {
        val m = main()
        assertEquals(1.5, R17C7.ratio(m, 701, "flamethrower") { it.weather = "sun" }, 0.03)
        assertEquals(0.5, R17C7.ratio(m, 701, "hydropump") { it.weather = "sun" }, 0.03)
        assertEquals("Hydro Steam is boosted, not weakened", 1.5, R17C7.ratio(m, 701, "hydrosteam") { it.weather = "sun" }, 0.03)
        assertEquals(1.0, R17C7.ratio(m, 701, "plainspecial") { it.weather = "sun" }, 0.0)
        assertEquals(1.5, R17C7.ratio(main(), 701, "flamethrower", byMain = false) { it.weather = "sun" }, 0.03)
    }

    @Test fun sandAndHailDoNotChangeTheElements() {
        val m = main()
        for (w in listOf("sand", "hail", "snow")) {
            assertEquals(w, 1.0, R17C7.ratio(m, 701, "hydropump") { it.weather = w }, 0.0)
            assertEquals(w, 1.0, R17C7.ratio(m, 701, "flamethrower") { it.weather = w }, 0.0)
        }
    }

    @Test fun aSandstormRaisesRockSpDefAndSnowRaisesIceDefense() {
        val m = main()
        assertEquals(1 / 1.5, R17C7.ratio(m, 702, "plainspecial") { it.weather = "sand" }, 0.03)
        assertEquals("only Sp. Def", 1.0, R17C7.ratio(m, 702, "plainphysical") { it.weather = "sand" }, 0.0)
        assertEquals("a special move that hits Defense", 1.0, R17C7.ratio(m, 702, "psyshockish") { it.weather = "sand" }, 0.0)
        assertEquals("not for other types", 1.0, R17C7.ratio(m, 701, "plainspecial") { it.weather = "sand" }, 0.0)
        assertEquals(1 / 1.5, R17C7.ratio(m, 705, "plainphysical") { it.weather = "snow" }, 0.03)
        assertEquals("only Defense", 1.0, R17C7.ratio(m, 705, "plainspecial") { it.weather = "snow" }, 0.0)
        assertEquals("Psyshock-like hits the boosted Defense", 1 / 1.5, R17C7.ratio(m, 705, "psyshockish") { it.weather = "snow" }, 0.03)
        assertEquals("hail is not snow", 1.0, R17C7.ratio(m, 705, "plainphysical") { it.weather = "hail" }, 0.0)
    }

    // ---------------------------------------------------------------- accuracy

    @Test fun thunderAndHurricaneNeverMissInRainAndHalfMissInSun() {
        val m = main()
        for (id in listOf("thunder", "hurricane", "bleakwindstorm", "wildboltstorm", "sandsearstorm")) {
            val acc = data.move(id)!!.accuracy!!.toDouble()
            fun under(weather: String?): Double? { val b = battle(); field(b, weather); return FieldRules.accuracy(R17C7.hit(b, m, id), acc) }
            assertNull("$id in rain", under("rain"))
            assertEquals("$id in sun", 50.0, under("sun")!!, 0.0)
            assertEquals("$id in no weather", acc, under(null)!!, 0.0)
            assertEquals("$id in a sandstorm", acc, under("sand")!!, 0.0)
        }
        // other moves are not touched
        val b = battle(); field(b, "rain")
        assertEquals(80.0, FieldRules.accuracy(R17C7.hit(b, m, "springtidestorm"), 80.0)!!, 0.0)
        assertEquals(100.0, FieldRules.accuracy(R17C7.hit(b, m, "plainspecial"), 100.0)!!, 0.0)
    }

    @Test fun blizzardNeverMissesInHailAndSnow() {
        val m = main()
        for ((weather, never) in mapOf("hail" to true, "snow" to true, "rain" to false, "sand" to false, "sun" to false)) {
            val b = battle(); field(b, weather)
            val a = FieldRules.accuracy(R17C7.hit(b, m, "blizzard"), 70.0)
            if (never) assertNull(weather, a) else assertEquals(weather, 70.0, a!!, 0.0)
        }
    }

    @Test fun aMoveThatNeverMissesStaysSo() {
        val b = battle(); field(b, "sun")
        assertNull(FieldRules.accuracy(R17C7.hit(b, main(), "thunder"), null))
    }

    @Test fun theRoundUsesTheWeatherAccuracy() {
        // R17C7.high makes every hit chance below 100 miss
        val rain = battle(); field(rain, "rain")
        assertFalse("Thunder in rain hits", R17C7.play(main("thunder"), rain, listOf("thunder"), rng = R17C7.high).single().attacks().single().missed)
        val dry = battle()
        assertTrue("Thunder without rain misses", R17C7.play(main("thunder"), dry, listOf("thunder"), rng = R17C7.high).single().attacks().single().missed)
        val sun = battle(); field(sun, "sun")
        assertTrue("Thunder in the sun misses", R17C7.play(main("thunder"), sun, listOf("thunder"), rng = R17C7.high).single().attacks().single().missed)
        val hail = battle(); field(hail, "hail")
        assertFalse("Blizzard in hail hits", R17C7.play(main("blizzard"), hail, listOf("blizzard"), rng = R17C7.high).single().attacks().single().missed)
    }

    // ---------------------------------------------------------------- Solar Beam and friends

    @Test fun solarBeamHitsAtOnceInTheSun() {
        for (move in listOf("solarbeam", "solarblade")) {
            val b = battle(); field(b, "sun"); val m = main(move)
            val e = R17C7.play(m, b, listOf(move)).single()
            assertTrue(move, e.kinds(EffectKind.CHARGE).isEmpty())
            assertNull(move, b.player.charging)
            assertTrue(move, e.attacks().single().damage > 0)
        }
    }

    @Test fun solarBeamChargesWithoutTheSunAndHitsOnTheNextAction() {
        for (weather in listOf(null, "rain", "sand", "hail", "snow")) {
            val b = battle(); field(b, weather); val m = main("solarbeam")
            val e = R17C7.play(m, b, listOf("solarbeam", "solarbeam"))
            assertEquals("$weather charges", listOf("Fastie absorbed light!"), e[0].kinds(EffectKind.CHARGE).map { it.text })
            assertEquals("$weather: no damage on the charge turn", 0, e[0].attacks().sumOf { it.damage })
            assertTrue("$weather hits next", e[1].attacks().single().damage > 0)
        }
    }

    @Test fun solarBeamHasHalfPowerInRainSandHailAndSnow() {
        val m = main()
        for (move in listOf("solarbeam", "solarblade")) {
            for (w in listOf("rain", "sand", "hail", "snow")) assertEquals("$move in $w", 0.5, R17C7.ratio(m, 701, move) { it.weather = w }, 0.03)
            assertEquals("$move in the sun", 1.0, R17C7.ratio(m, 701, move) { it.weather = "sun" }, 0.0)
        }
    }

    @Test fun electroShotHitsAtOnceInRainAndStillRaisesSpAtk() {
        val b = battle(); field(b, "rain"); val m = main("electroshot")
        val e = R17C7.play(m, b, listOf("electroshot")).single()
        assertTrue(e.kinds(EffectKind.CHARGE).isEmpty())
        assertTrue(e.attacks().single().damage > 0)
        assertEquals(1, b.player.boosts[2])
        val dry = battle(); val e2 = R17C7.play(main("electroshot"), dry, listOf("electroshot")).single()
        assertEquals(listOf("Fastie absorbed electricity!"), e2.kinds(EffectKind.CHARGE).map { it.text })
    }

    @Test fun aSuppressedWeatherDoesNotSkipTheCharge() {
        val b = battle(); field(b, "sun")
        val suppress = object : BattleRules { override fun suppressWeather(v: BattleView) = true }
        val e = R17C7.play(main("solarbeam"), b, listOf("solarbeam"), extra = listOf(suppress)).single()
        assertEquals(1, e.kinds(EffectKind.CHARGE).size)
    }

    // ---------------------------------------------------------------- Weather Ball, Growth, freezing

    @Test fun weatherBallChangesTypeAndDoublesPowerInAnyWeather() {
        val mind = R17C7.mon(710, 100, "weatherball") // a Psychic user: no STAB with any of the types
        val expected = mapOf("rain" to (Type.WATER to 3.0), "sun" to (Type.FIRE to 3.0), "sand" to (Type.ROCK to 2.0), "hail" to (Type.ICE to 2.0), "snow" to (Type.ICE to 2.0))
        for ((w, want) in expected) {
            val b = battle(); field(b, w)
            assertEquals(w, want.first, R17C7.hit(b, mind, "weatherball").type)
            assertEquals("$w power x2 (and the weather's own boost)", want.second, R17C7.ratio(mind, 701, "weatherball") { it.weather = w }, 0.12)
        }
        val none = battle()
        assertEquals(Type.NORMAL, R17C7.hit(none, mind, "weatherball").type)
    }

    @Test fun weatherBallStaysNormalUnderASuppressedWeather() {
        val mind = R17C7.mon(710, 100, "weatherball")
        val b = battle(); field(b, "rain")
        val suppress = object : BattleRules { override fun suppressWeather(v: BattleView) = true }
        BattleRules.using(BattleRules.ALL + suppress) {
            assertEquals(Type.NORMAL, R17C7.hit(b, mind, "weatherball").type)
            assertEquals(1.0, R17C7.ratio(mind, 701, "weatherball") { it.weather = "rain" }, 0.0)
        }
    }

    @Test fun growthRaisesBothStatsByOneAndByTwoInTheSun() {
        val b = battle(); val m = main("growth")
        R17C7.play(m, b, listOf("growth"))
        assertEquals(1, b.player.boosts[0]); assertEquals(1, b.player.boosts[2])
        val sun = battle(); field(sun, "sun"); val m2 = main("growth")
        val e = R17C7.play(m2, sun, listOf("growth")).single()
        assertEquals(2, sun.player.boosts[0]); assertEquals(2, sun.player.boosts[2])
        assertTrue(e.lines().contains("Fastie's Attack rose sharply!"))
    }

    @Test fun nobodyIsFrozenInTheSun() {
        val dry = battle(); R17C7.play(main("icebeam"), dry, listOf("icebeam"))
        assertEquals("frz", dry.enemy.status)
        val sun = battle(); field(sun, "sun"); R17C7.play(main("icebeam"), sun, listOf("icebeam"))
        assertNull(sun.enemy.status)
        // the wild one gets the same protection from the Main's blocked freeze, and other statuses are not affected
        val par = battle(); field(par, "sun"); R17C7.play(main("thunderwave"), par, listOf("thunderwave"), rng = R17C7.low)
        assertEquals("par", par.enemy.status)
    }

    // ---------------------------------------------------------------- suppressed weather, the move choice, texts

    @Test fun aSuppressedWeatherHasNoEffectButStillCountsDown() {
        val suppress = object : BattleRules { override fun suppressWeather(v: BattleView) = true }
        BattleRules.using(BattleRules.ALL + suppress) {
            val m = main("sandstorm")
            val b = battle(); val e = R17C7.play(m, b, listOf("sandstorm")).single()
            assertTrue("no sandstorm damage", e.kinds(EffectKind.RESIDUAL).isEmpty())
            assertEquals("sand", b.field.weather); assertEquals(4, b.field.weatherTurns)
            assertEquals(1.0, R17C7.ratio(m, 701, "hydropump") { it.weather = "rain" }, 0.0)
        }
    }

    @Test fun theSmartChoiceKeepsWeatherMovesForWhenTheyChangeSomething() {
        val m = main("raindance"); val b = battle()
        val v = SimpleView.of(data, b, m)
        for ((move, key, _) in starts) {
            assertTrue("$move with clear skies", MoveChooser.statusUseful(MoveChoice.SMART, data.move(move)!!, v, true))
            field(b, key)
            assertFalse("$move while it is already so", MoveChooser.statusUseful(MoveChoice.SMART, data.move(move)!!, v, true))
            field(b, null)
        }
        field(b, "sun")
        assertTrue("Rain Dance replaces the sun", MoveChooser.statusUseful(MoveChoice.SMART, data.move("raindance")!!, v, true))
    }

    @Test fun theMoveTextsSayWhatHappens() {
        fun say(id: String) = MoveText.describe(data.move(id)!!)
        for ((move, _, _) in starts) assertFalse(move, say(move).contains("Has no effect"))
        assertTrue(say("raindance").startsWith("Makes it rain for 5 turns."))
        assertTrue(say("sunnyday").contains("Nobody can be frozen."))
        assertTrue(say("sandstorm").contains("a sixteenth of its max HP"))
        assertTrue(say("solarbeam").contains("In harsh sunlight it hits at once."))
        assertTrue(say("solarblade").contains("In harsh sunlight it hits at once."))
        assertTrue(say("electroshot").contains("In rain it hits at once."))
        assertTrue(say("thunder").contains("Never misses in rain"))
        assertTrue(say("blizzard").contains("Never misses in hail or snow."))
        assertTrue(say("growth").contains("In harsh sunlight it raises them by 2 instead."))
        assertTrue(say("weatherball").contains("double power in any weather"))
        assertTrue(say("hydrosteam").contains("1.5 times"))
        for (id in listOf("raindance", "sunnyday", "sandstorm", "hail", "snowscape", "chillyreception", "solarbeam", "weatherball", "growth")) {
            assertTrue("$id ends with a full stop", say(id).endsWith("."))
        }
    }

    @Test fun theSetHandlesTheWeatherMoves() {
        for ((move, _, _) in starts) assertTrue(move, move in FieldRules.handles)
        assertNotNull(data.move("growth"))
    }
}
