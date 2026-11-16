package com.husarp.ankigo.ui.movefx

import kotlin.math.*

/** The same glass frames and body fit as Ice Beam; retained for the whole Frozen status. */
internal object AiFrozenCube {
    fun draw(c: FxCanvas, bodyW: Float, bodyH: Float, unit: Float, ms: Float, clearing: Boolean, opacity: Float) {
        val u = unit.coerceAtLeast(.01f)
        val w = IceCubeFit.width(bodyW / u, bodyH / u)
        val h = IceCubeFit.height(bodyW / u, bodyH / u)
        c.save(); c.scale(u, u)
        if (!clearing || ms < 440f) {
            val pulse = if (clearing) 1f else 1f + .015f * sin(ms / 60f)
            c.soft(0f, 0f, w * .95f, h * .85f, 0xA0E6FF, .3f * opacity)
            c.ai(AiSheet.ICE_CUBE3, 0, 0f, 0f, pulse, .9f * opacity, w = w * 192f / 160f, h = h * 192f / 167f)
            if (clearing) {
                val crack = (ms / 150f).coerceIn(0f, 1f)
                c.ai(AiSheet.ICE_CUBE3, 1, sin(ms / 15f) * crack * 1.2f, 0f, pulse, crack * opacity, w = w * 192f / 160f, h = h * 192f / 167f)
            } else c.ai(AiSheet.ICE_CUBE3, 0, 0f, 0f, pulse, .22f * (.5f + .5f * sin(ms / 260f)) * opacity, true, w = w * 192f / 160f, h = h * 192f / 167f)
        } else {
            val sec = (ms - 440f) / 1000f
            val p = ((ms - 440f) / 560f).coerceIn(0f, 1f)
            val k = ((w + h) / 220f).coerceIn(.45f, 1.2f)
            for (i in 0 until 18) {
                val a = i * 2.399963f; val speed = 70f + (i * 73 % 200); val rad = sqrt((i + .5f) / 18f) * .45f
                c.ai(AiSheet.ICE_CUBE3, if (i < 12) 2 else 3,
                    cos(a) * (rad * w + speed * sec), sin(a) * (rad * h + speed * sec) - 30f * sec + if (i < 12) 190f * sec * sec else 0f,
                    (if (i < 12) .75f else .5f) * k, (1f - p) * opacity, i >= 12, a + (i % 5 - 2) * sec * 4f)
            }
            c.soft(0f, 0f, 40f + (w + h) * .275f, 40f + (w + h) * .275f, 0xAEEAFF, .5f * (1f - p) * opacity)
        }
        c.restore()
    }
}
