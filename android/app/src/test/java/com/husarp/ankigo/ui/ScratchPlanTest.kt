package com.husarp.ankigo.ui

import org.junit.Test
import java.io.File

class ScratchPlanTest {
    private fun d(b: ArenaGeo.Box) = "(%.0f,%.0f %.0fx%.0f)".format(b.x, b.y, b.w, b.h)
    @Test fun dump() {
        val sb = StringBuilder()
        for (own in listOf(true, false)) for ((w, h) in listOf(412f to 869f, 412f to 678f, 412f to 600f, 412f to 500f, 412f to 430f, 412f to 340f, 412f to 267f, 412f to 230f, 412f to 213f, 412f to 200f, 360f to 530f, 800f to 1100f, 869f to 340f, 344f to 200f, 200f to 400f, 700f to 213f)) {
            val c = ArenaGeo.cards(8f, false, true)
            val p = ArenaGeo.plan(w, h, c, false, own)
            sb.append("own=$own ${w.toInt()}x${h.toInt()} ${p.layout} fits=${p.fits} a=%.2f k=%.2f wild=${d(p.wildCard)} player=${d(p.playerCard)} ws=${d(p.wildSprite)} ps=${d(p.playerSprite)}\n".format(p.a, p.k))
        }
        File("/tmp/a01-scratch/plans.txt").writeText(sb.toString())
    }
}
