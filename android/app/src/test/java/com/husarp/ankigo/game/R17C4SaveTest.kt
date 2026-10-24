package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R17C-4: the state of its rules is in the maps the save already keeps (volatiles, notes, side conditions): a fight saved in the middle goes on as before. */
class R17C4SaveTest {
    private val data = R17C4Fixture.data()

    private fun play(battle: Battle, main: Mon, script: R17C4Fixture.Script): List<GameEvent> =
        BattleRules.using(listOf(script, MovePower)) { BattleEngine.playRound(data, battle, main, 1.0, false, R17C4Fixture.ScriptedRandom(), 1.0, true) }

    private fun saveAndLoad(battle: Battle, main: Mon): Pair<Battle, Mon> {
        val state = GameState(schema = GameState.SCHEMA)
        state.mons += main; state.team += main.uid; state.battle = battle
        val back = SaveCodec.decode(SaveCodec.encode(state))
        return back.battle!! to back.mons.first()
    }

    @Test fun aWaitingFutureSightLandsAfterAReload() {
        val main = TestData.mon(data, 400, 50, listOf("futuresight", "tackle"))
        val wild = TestData.mon(data, 401, 50, listOf("tackle"))
        val battle = Battle(wild, Tier.NORMAL)
        play(battle, main, R17C4Fixture.Script(main = ArrayDeque(listOf("futuresight"))))
        val planned = battle.field.enemy["mp.fs.dmg"]!!
        val (b2, m2) = saveAndLoad(battle, main)
        assertEquals(battle.field.enemy, b2.field.enemy)
        val hp = b2.wild.hp
        val script = R17C4Fixture.Script(main = ArrayDeque(listOf("tackle", "tackle")))
        play(b2, m2, script)
        val ev = play(b2, m2, script)
        assertTrue(ev.filterIsInstance<GameEvent.Effect>().any { it.text == "Tank took the Future Sight attack!" })
        assertTrue(b2.wild.hp <= hp - planned)
    }

    @Test fun aRolloutRunAndABideStoreSurviveAReload() {
        val main = TestData.mon(data, 400, 50, listOf("rollout", "bide", "tackle"))
        val wild = TestData.mon(data, 401, 50, listOf("tackle")).also { it.hp = 90_000 }
        val battle = Battle(wild, Tier.NORMAL).also { it.wildMaxHp = 90_000 }
        play(battle, main, R17C4Fixture.Script(main = ArrayDeque(listOf("rollout"))))
        val (b2, m2) = saveAndLoad(battle, main)
        assertEquals(battle.player.volatiles, b2.player.volatiles)
        assertEquals(battle.player.notes, b2.player.notes)
        // the run goes on after the reload: no pick, Rollout again
        val ev = play(b2, m2, R17C4Fixture.Script())
        assertEquals("rollout", ev.filterIsInstance<GameEvent.Attack>().single().move)

        val main3 = TestData.mon(data, 400, 50, listOf("bide", "tackle"))
        val b3 = Battle(TestData.mon(data, 401, 50, listOf("tackle")), Tier.NORMAL)
        play(b3, main3, R17C4Fixture.Script(main = ArrayDeque(listOf("bide"))))
        val (b4, m4) = saveAndLoad(b3, main3)
        assertEquals(b3.player.volatiles, b4.player.volatiles)
        val ev2 = play(b4, m4, R17C4Fixture.Script())
        assertTrue(ev2.filterIsInstance<GameEvent.Effect>().any { it.text.endsWith("is storing energy!") })
    }

    @Test fun anOldSaveWithoutTheseKeysPlaysAsNormal() {
        val main = TestData.mon(data, 400, 50, listOf("rollout", "tackle"))
        val battle = Battle(TestData.mon(data, 401, 50, listOf("tackle")), Tier.NORMAL)
        val (b2, m2) = saveAndLoad(battle, main)
        assertTrue(b2.player.volatiles.isEmpty() && b2.field.enemy.isEmpty())
        val ev = play(b2, m2, R17C4Fixture.Script(main = ArrayDeque(listOf("tackle"))))
        assertEquals("tackle", ev.filterIsInstance<GameEvent.Attack>().single().moveId)
        assertTrue(b2.team.isEmpty()) // the team for Beat Up is never saved
    }
}
