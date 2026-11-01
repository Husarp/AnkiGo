package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.TestData
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class R26SaveSnapshotTest {
    @Test fun workerEncoderCannotObserveLaterChangesToNestedState() {
        val data = TestData.world()
        val state = TestData.state(data, 25, 20, listOf("tackle"))
        state.main!!.nickname = "Quoted \"name\"\n雪"
        state.achTiers["test"] = mutableListOf(123L)
        val expected = SaveCodec.encode(state)
        val encodeLater = SaveCodec.snapshot(state)
        state.cash += 100
        state.main!!.nickname = "changed"
        state.main!!.moves.clear()
        state.buttonsToday[0] += 1
        state.achTiers["test"]!!.add(456L)
        state.bag.clear()
        state.team.clear()
        state.mons.clear()
        val worker = Executors.newSingleThreadExecutor()
        try {
            val actual = worker.submit<String> { encodeLater() }.get(5, TimeUnit.SECONDS)
            assertEquals(expected, actual)
            assertEquals(expected, SaveCodec.encode(SaveCodec.decode(actual)))
        } finally { worker.shutdownNow() }
    }
}
