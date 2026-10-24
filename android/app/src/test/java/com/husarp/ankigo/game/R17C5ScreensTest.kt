package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C5.attacks
import com.husarp.ankigo.game.R17C5.effects
import com.husarp.ankigo.game.R17C5.kinds
import com.husarp.ankigo.game.R17C5.lines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** R17C-5: Reflect, Light Screen, Aurora Veil (and the moves that break them), Safeguard, Mist and Tailwind. */
class R17C5ScreensTest {
    private val data = R17C5.data
    private fun main(vararg m: String) = R17C5.main(*m)
    private fun wild(vararg m: String, sp: Int = 301) = R17C5.wild(*m, sp = sp)
    private fun battle(w: Mon) = R17C5.battle(w)
    private fun round(m: Mon, b: Battle, mainIds: List<String>, wildIds: List<String>, rng: Random = Random(1), wildPower: Double = 1.0) =
        R17C5.playing(mainIds, wildIds) { R17C5.round(m, b, rng, wildPower = wildPower) }

    private fun mainSide(b: Battle) = b.field.player
    private fun wildSide(b: Battle) = b.field.enemy

    // ---------------------------------------------------------------- the damage

    @Test fun reflectHalvesPhysicalHitsOnly() {
        val m = main("splash"); val b = battle(wild("tackle"))
        val physical = R17C5.hitOn(b, m, "tackle"); val special = R17C5.hitOn(b, m, "watergun")
        mainSide(b)[SideMoves.REFLECT] = 5
        assertEquals(physical / 2, R17C5.hitOn(b, m, "tackle"))
        assertEquals(special, R17C5.hitOn(b, m, "watergun"))
    }

    @Test fun lightScreenHalvesSpecialHitsOnly() {
        val m = main("splash"); val b = battle(wild("tackle"))
        val physical = R17C5.hitOn(b, m, "tackle"); val special = R17C5.hitOn(b, m, "watergun")
        mainSide(b)[SideMoves.LIGHT_SCREEN] = 5
        assertEquals(physical, R17C5.hitOn(b, m, "tackle"))
        assertEquals(special / 2, R17C5.hitOn(b, m, "watergun"))
    }

    @Test fun auroraVeilHalvesBothAndDoesNotStackWithReflect() {
        val m = main("splash"); val b = battle(wild("tackle"))
        val physical = R17C5.hitOn(b, m, "tackle"); val special = R17C5.hitOn(b, m, "watergun")
        mainSide(b)[SideMoves.AURORA_VEIL] = 5
        assertEquals(physical / 2, R17C5.hitOn(b, m, "tackle"))
        assertEquals(special / 2, R17C5.hitOn(b, m, "watergun"))
        mainSide(b)[SideMoves.REFLECT] = 5; mainSide(b)[SideMoves.LIGHT_SCREEN] = 5
        assertEquals(physical / 2, R17C5.hitOn(b, m, "tackle"))
    }

    @Test fun aScreenProtectsItsOwnSideOnly() {
        val m = main("tackle"); val b = battle(wild("splash"))
        val hit = R17C5.hitOn(b, m, "tackle", wildHits = false)
        wildSide(b)[SideMoves.REFLECT] = 5
        assertEquals("the wild one's Reflect halves the Main's hit", hit / 2, R17C5.hitOn(b, m, "tackle", wildHits = false))
        assertEquals("and does nothing for the Main", R17C5.hitOn(battle(wild("splash")), m, "tackle"), R17C5.hitOn(b, m, "tackle"))
    }

    // ---------------------------------------------------------------- the moves that set them

    @Test fun reflectLastsFiveRoundsWithItsLines() {
        val m = main("reflect"); val b = battle(wild("splash"))
        val e = round(m, b, listOf("reflect"), listOf("splash"))
        val start = e.effects().first { it.kind == EffectKind.FIELD }
        assertEquals("Reflect made Fastie's team stronger against physical moves!", start.text)
        assertEquals("Reflect", start.label); assertEquals(1, start.stages); assertEquals("reflect", start.stat)
        assertEquals(4, mainSide(b)[SideMoves.REFLECT])
        for (left in listOf(3, 2, 1)) { round(m, b, listOf("splash"), listOf("splash")); assertEquals(left, mainSide(b)[SideMoves.REFLECT]) }
        val last = round(m, b, listOf("splash"), listOf("splash"))
        assertNull(mainSide(b)[SideMoves.REFLECT])
        val end = last.effects().first { it.kind == EffectKind.FIELD }
        assertEquals("Fastie's Reflect wore off!", end.text); assertEquals(-1, end.stages)
    }

    @Test fun lightScreenAndAuroraVeilSayWhatTheyDo() {
        val m = main("lightscreen"); val b = battle(wild("splash"))
        val e = round(m, b, listOf("lightscreen"), listOf("splash"))
        assertEquals("Light Screen made Fastie's team stronger against special moves!", e.kinds(EffectKind.FIELD).single().text)
        val m2 = main("auroraveil"); val b2 = battle(wild("splash")); b2.field.weather = "hail"
        val e2 = round(m2, b2, listOf("auroraveil"), listOf("splash"))
        assertEquals("Aurora Veil made Fastie's team stronger against physical and special moves!", e2.kinds(EffectKind.FIELD).single().text)
        assertEquals("Aurora Veil", e2.kinds(EffectKind.FIELD).single().label)
    }

    @Test fun aScreenThatIsUpFailsAndKeepsItsTime() {
        val m = main("reflect"); val b = battle(wild("splash"))
        round(m, b, listOf("reflect"), listOf("splash"))
        val e = round(m, b, listOf("reflect"), listOf("splash"))
        assertTrue(e.attacks().first { it.byPlayer }.failed)
        assertTrue(e.lines().contains("But it failed!"))
        assertEquals(3, mainSide(b)[SideMoves.REFLECT])
    }

    @Test fun auroraVeilNeedsHailOrSnow() {
        for ((weather, works) in listOf(null to false, "rain" to false, "sun" to false, "hail" to true, "snow" to true)) {
            val m = main("auroraveil"); val b = battle(wild("splash")); b.field.weather = weather
            val e = round(m, b, listOf("auroraveil"), listOf("splash"))
            assertEquals("$weather", works, mainSide(b).containsKey(SideMoves.AURORA_VEIL))
            assertEquals("$weather", !works, e.attacks().first { it.byPlayer }.failed)
        }
    }

    @Test fun theWildOneSetsScreensForItsOwnSide() {
        val m = main("splash"); val b = battle(wild("reflect"))
        val e = round(m, b, listOf("splash"), listOf("reflect"))
        assertEquals(4, wildSide(b)[SideMoves.REFLECT]); assertNull(mainSide(b)[SideMoves.REFLECT])
        assertEquals("Reflect made Slowpoke's team stronger against physical moves!", e.kinds(EffectKind.FIELD).single().text)
    }

    @Test fun screensSurviveASwitchAndDoNotCountDownOnASwitchHit() {
        val m = main("splash"); val b = battle(wild("tackle"))
        mainSide(b)[SideMoves.REFLECT] = 3
        b.switchOutMain(data, m)
        assertEquals(3, mainSide(b)[SideMoves.REFLECT])
        val incoming = main("splash")
        R17C5.playing(wild = listOf("tackle")) { BattleEngine.switchHit(data, b, incoming, Random(1)) }
        assertEquals("a switch's free hit is not a round", 3, mainSide(b)[SideMoves.REFLECT])
    }

    // ---------------------------------------------------------------- Brick Break, Psychic Fangs, Raging Bull

    @Test fun screenBreakersIgnoreTheScreensAndEndThem() {
        for (id in listOf("brickbreak", "psychicfangs", "ragingbull")) {
            val m = main("splash"); val b = battle(wild(id)); val b2 = battle(wild(id)); val m2 = main("splash")
            for (key in listOf(SideMoves.REFLECT, SideMoves.LIGHT_SCREEN, SideMoves.AURORA_VEIL)) mainSide(b)[key] = 5
            val withScreens = round(m, b, listOf("splash"), listOf(id), Random(7))
            val without = round(m2, b2, listOf("splash"), listOf(id), Random(7))
            assertEquals("$id hits as if there were no screens", without.attacks().first { !it.byPlayer }.damage, withScreens.attacks().first { !it.byPlayer }.damage)
            assertTrue("$id ends them", mainSide(b).isEmpty())
            val gone = withScreens.kinds(EffectKind.FIELD).map { it.text }
            assertEquals(listOf("Fastie's Reflect was shattered!", "Fastie's Light Screen was shattered!", "Fastie's Aurora Veil was shattered!"), gone)
        }
    }

    @Test fun theMainsBrickBreakBreaksTheWildOnesScreensButNotBehindProtect() {
        val m = main("brickbreak"); val w = wild("splash"); val b = battle(w)
        wildSide(b)[SideMoves.REFLECT] = 5
        round(m, b, listOf("brickbreak"), listOf("splash"))
        assertTrue(wildSide(b).isEmpty())
        val m2 = main("brickbreak"); val w2 = wild("protect"); val b2 = battle(w2)
        wildSide(b2)[SideMoves.REFLECT] = 5
        round(m2, b2, listOf("brickbreak"), listOf("protect"))
        assertEquals("a blocked Brick Break breaks nothing", 4, wildSide(b2)[SideMoves.REFLECT])
    }

    // ---------------------------------------------------------------- Safeguard

    @Test fun safeguardStopsAStatusMoveFromTheFoe() {
        val m = main("thunderwave"); val b = battle(wild("splash"))
        wildSide(b)[SideMoves.SAFEGUARD] = 5
        val e = round(m, b, listOf("thunderwave"), listOf("splash"))
        assertNull(b.enemy.status)
        val block = e.kinds(EffectKind.BLOCKED).single()
        assertEquals("Slowpoke's team is protected by Safeguard!", block.text); assertFalse(block.onPlayer)
        assertFalse("no second line", e.lines().contains("But it failed!"))
    }

    @Test fun safeguardStopsASecondaryStatusQuietly() {
        val m = main("ember"); val b = battle(wild("splash"))
        wildSide(b)[SideMoves.SAFEGUARD] = 5
        val e = round(m, b, listOf("ember"), listOf("splash"), R17C5.low)
        assertNull(b.enemy.status)
        assertTrue(e.kinds(EffectKind.BLOCKED).isEmpty())
    }

    @Test fun safeguardStopsConfusionAndYawnFromTheFoe() {
        val m = main("confuseray"); val b = battle(wild("splash"))
        wildSide(b)[SideMoves.SAFEGUARD] = 5
        val e = round(m, b, listOf("confuseray"), listOf("splash"))
        assertFalse("confusion" in b.enemy.volatiles)
        assertEquals(1, e.kinds(EffectKind.BLOCKED).size)
        val m2 = main("yawn"); val b2 = battle(wild("splash"))
        wildSide(b2)[SideMoves.SAFEGUARD] = 5
        round(m2, b2, listOf("yawn"), listOf("splash"))
        assertFalse(SideMoves.YAWN in b2.enemy.volatiles)
    }

    @Test fun safeguardDoesNotStopTheSidesOwnRest() {
        val m = main("rest"); val b = battle(wild("splash"))
        mainSide(b)[SideMoves.SAFEGUARD] = 5
        m.hp = R17C5.maxHp(m) / 2
        round(m, b, listOf("rest"), listOf("splash"))
        assertEquals("slp", b.player.status)
    }

    @Test fun safeguardAndMistEndAfterFiveRounds() {
        val m = main("safeguard"); val b = battle(wild("splash"))
        round(m, b, listOf("safeguard"), listOf("splash"))
        assertEquals(4, mainSide(b)[SideMoves.SAFEGUARD])
        var last: List<GameEvent> = emptyList()
        repeat(4) { last = round(m, b, listOf("splash"), listOf("splash")) }
        assertNull(mainSide(b)[SideMoves.SAFEGUARD])
        assertEquals("Fastie's Safeguard wore off!", last.kinds(EffectKind.FIELD).single().text)
        val m2 = main("mist"); val b2 = battle(wild("splash"))
        round(m2, b2, listOf("mist"), listOf("splash"))
        repeat(4) { last = round(m2, b2, listOf("splash"), listOf("splash")) }
        assertEquals("Fastie's Mist faded!", last.kinds(EffectKind.FIELD).single().text)
    }

    // ---------------------------------------------------------------- Mist

    @Test fun mistStopsTheFoesStatDropButNotYourOwn() {
        val m = main("growl"); val w = wild("splash"); val b = battle(w)
        wildSide(b)[SideMoves.MIST] = 5
        val e = round(m, b, listOf("growl"), listOf("splash"))
        assertEquals(0, b.enemy.boosts[0])
        assertEquals("Slowpoke is protected by the mist!", e.kinds(EffectKind.BLOCKED).single().text)
        // its own drop (Curse's Speed) is not stopped, and a rise is not either
        val m2 = main("curse"); val b2 = battle(wild("splash"))
        mainSide(b2)[SideMoves.MIST] = 5
        round(m2, b2, listOf("curse"), listOf("splash"))
        assertEquals(-1, b2.player.boosts[4]); assertEquals(1, b2.player.boosts[0])
    }

    @Test fun mistAlsoStopsADropThatIsASecondary() {
        val m = main("psychic"); val b = battle(wild("splash"))
        wildSide(b)[SideMoves.MIST] = 5
        round(m, b, listOf("psychic"), listOf("splash"), R17C5.low)
        assertEquals(0, b.enemy.boosts[3])
    }

    // ---------------------------------------------------------------- Tailwind

    @Test fun tailwindDoublesTheSpeedOfTheTeam() {
        val faster = R17C5.mon(305, 20, "tackle", gender = 'F')
        val slowMain = R17C5.mon(300, 20, "tackle")
        val b = battle(faster)
        val e = R17C5.playing(listOf("tackle"), listOf("tackle")) { R17C5.round(slowMain, b, Random(1), wildPower = 0.01) }
        assertFalse("the faster wild one goes first", e.attacks().first().byPlayer)
        val faster2 = R17C5.mon(305, 20, "tackle", gender = 'F'); val slowMain2 = R17C5.mon(300, 20, "tackle")
        val b2 = battle(faster2); mainSide(b2)[SideMoves.TAILWIND] = 4
        val e2 = R17C5.playing(listOf("tackle"), listOf("tackle")) { R17C5.round(slowMain2, b2, Random(1), wildPower = 0.01) }
        assertTrue("with Tailwind the Main is faster", e2.attacks().first().byPlayer)
    }

    @Test fun tailwindLastsFourRounds() {
        val m = main("tailwind"); val b = battle(wild("splash"))
        val first = round(m, b, listOf("tailwind"), listOf("splash"))
        assertEquals("The tailwind blew from behind Fastie's team!", first.kinds(EffectKind.FIELD).single().text)
        assertEquals("Tailwind", first.kinds(EffectKind.FIELD).single().label)
        assertEquals(3, mainSide(b)[SideMoves.TAILWIND])
        repeat(2) { round(m, b, listOf("splash"), listOf("splash")) }
        assertEquals(1, mainSide(b)[SideMoves.TAILWIND])
        val last = round(m, b, listOf("splash"), listOf("splash"))
        assertNull(mainSide(b)[SideMoves.TAILWIND])
        assertEquals("Fastie's Tailwind petered out!", last.kinds(EffectKind.FIELD).single().text)
    }

    // ---------------------------------------------------------------- the move choice and the texts

    @Test fun smartChoiceUsesTheSideMovesOnlyWhenTheyCanStillWork() {
        val m = main("tackle"); val b = battle(wild("tackle"))
        val v = { SimpleView.of(data, b, m) }
        fun useful(id: String) = SideMoves.useful(v(), true, data.move(id)!!)
        for (id in listOf("reflect", "lightscreen", "safeguard", "mist", "tailwind")) {
            assertEquals(id, true, useful(id))
            mainSide(b)[id] = 3
            assertEquals(id, false, useful(id))
        }
        assertEquals("no Aurora Veil without hail or snow", false, useful("auroraveil"))
        b.field.weather = "snow"
        assertEquals(true, useful("auroraveil"))
        mainSide(b)[SideMoves.AURORA_VEIL] = 2
        assertEquals(false, useful("auroraveil"))
    }

    @Test fun theTextsGiveTheNumbers() {
        fun say(id: String) = MoveText.describe(data.move(id)!!)
        assertEquals("For 5 turns, physical moves hurt your team half as much.", say("reflect"))
        assertEquals("For 5 turns, special moves hurt your team half as much.", say("lightscreen"))
        assertEquals("For 5 turns, physical and special moves hurt your team half as much. It fails unless it is hailing or snowing.", say("auroraveil"))
        assertEquals("For 5 turns, the other Pokémon can't give your team a status or confusion.", say("safeguard"))
        assertEquals("For 5 turns, the other Pokémon can't lower your stats.", say("mist"))
        assertEquals("For 4 turns, your Speed is doubled.", say("tailwind"))
        assertTrue(say("brickbreak"), "Breaks the target's Reflect, Light Screen and Aurora Veil first." in say("brickbreak"))
    }
}
