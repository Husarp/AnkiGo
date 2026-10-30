package com.husarp.ankigo.sound

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.Type
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class R21TypeSoundTest {
    private fun attack(missed: Boolean = false, failed: Boolean = false) = GameEvent.Attack(true, "A", "B", "Water Gun", Type.WATER,
        Category.SPECIAL, 12, 1, 2.0, false, missed, failed, 30, 40, 50, 50)

    @Test fun allEighteenTypesAreDistinctSafeAndDeterministic() {
        val hashes = HashSet<Int>()
        for (t in Type.entries) {
            val samples = TypeSfx.samples(t)
            assertTrue(t.name, samples.size.toDouble() / Sfx.RATE in .1.. .7)
            assertTrue(t.name, samples.maxOf { abs(it.toInt()) } <= 32767 * .301)
            assertEquals(0, samples.first().toInt()); assertEquals(0, samples.last().toInt())
            assertArrayEquals(samples, Sfx.samples(TypeSfx.id(t)))
            hashes += samples.contentHashCode()
            assertEquals(SoundGroup.BATTLE, TypeSfx.id(t).group)
        }
        assertEquals(18, hashes.size)
        assertTrue(TypeSfx.samples(Type.STEEL).maxOf { abs(it.toInt()) } < TypeSfx.samples(Type.FIRE).maxOf { abs(it.toInt()) })
    }

    @Test fun typeCueSharesHitTimingAndLeavesEffectivenessIntact() {
        val attack = attack()
        val hit = SoundRules.cuesFor(attack).single() as Cue.Effect
        assertEquals(SoundId.SUPER, hit.id)
        assertEquals(hit.delayMs, TypeSfx.cue(attack, true)!!.delayMs)
        assertEquals(SoundId.TYPE_WATER, TypeSfx.cue(attack, true)!!.id)
        assertNull(TypeSfx.cue(attack, false))
        assertNull(TypeSfx.cue(attack(missed = true), true))
        assertNull(TypeSfx.cue(attack(failed = true), true))
        assertFalse(SoundRules.shouldPlay(TypeSfx.cue(attack, true)!!, SoundContext(groupsOff = setOf(SoundGroup.BATTLE))))
        assertFalse(SoundRules.shouldPlay(TypeSfx.cue(attack, true)!!, SoundContext(volumes = mapOf(SoundChannel.BATTLE to 0))))
        assertFalse(SoundRules.shouldPlay(TypeSfx.cue(attack, true)!!, SoundContext(muted = true)))
    }
}
