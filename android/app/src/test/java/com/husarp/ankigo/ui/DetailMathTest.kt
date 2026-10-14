package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Stat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailMathTest {
    @Test fun barFractionIsClamped() {
        assertEquals(0.5f, DetailMath.fraction(50, 100), 0.0001f)
        assertEquals(1f, DetailMath.fraction(400, 100), 0f)
        assertEquals(0f, DetailMath.fraction(-3, 100), 0f)
        assertEquals(0f, DetailMath.fraction(10, 0), 0f)
    }

    @Test fun statMaxGrowsWithLevelAndStaysInRange() {
        assertTrue(DetailMath.monStatMax(50) > DetailMath.monStatMax(10))
        assertEquals(DetailMath.monStatMax(1), DetailMath.monStatMax(-5))
        assertEquals(DetailMath.monStatMax(100), DetailMath.monStatMax(250))
    }

    @Test fun natureArrows() {
        assertEquals(1, DetailMath.natureDir(Nature.JOLLY, Stat.SPE))
        assertEquals(-1, DetailMath.natureDir(Nature.JOLLY, Stat.SPA))
        assertEquals(0, DetailMath.natureDir(Nature.JOLLY, Stat.ATK))
        assertEquals(0, DetailMath.natureDir(Nature.JOLLY, Stat.HP))
        Stat.entries.forEach { assertEquals(0, DetailMath.natureDir(Nature.HARDY, it)) }
        assertEquals("▲", DetailMath.arrow(1)); assertEquals("▼", DetailMath.arrow(-1)); assertEquals("", DetailMath.arrow(0))
    }

    @Test fun everyNatureHasOneUpAndOneDownOrNone() {
        for (n in Nature.entries) {
            val dirs = Stat.entries.map { DetailMath.natureDir(n, it) }
            assertEquals(n.name, if (n.plus == null) 0 else 1, dirs.count { it > 0 })
            assertEquals(n.name, if (n.plus == null) 0 else 1, dirs.count { it < 0 })
        }
    }

    @Test fun natureText() {
        assertEquals("Raises Speed, lowers Sp. Atk", DetailMath.natureEffect(Nature.JOLLY))
        assertEquals("Neutral", DetailMath.natureEffect(Nature.SERIOUS))
    }

    @Test fun sixDistinctStatColours() {
        assertEquals(6, Stat.entries.map { DetailMath.statColor(it) }.toSet().size)
        assertEquals(6, DetailMath.LABELS.size)
    }
}
