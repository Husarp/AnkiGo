package com.husarp.ankigo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** C11: a small animated battle panel (two bobbing sprites, animated HP bars) with frame-time stats. */
class BattleView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val enemies = listOf("🐉", "🦎", "🐢", "🦇", "🐝", "🦉")
    private var enemyIdx = 0
    private val player = "🦊"

    private var playerHp = 1f; private var playerShown = 1f
    private var enemyHp = 1f; private var enemyShown = 1f
    private var shakePlayer = 0f; private var shakeEnemy = 0f
    private var flash = ""; private var flashT = 0f
    var caught = 0; private set

    private val t0 = System.nanoTime()
    private var lastFrame = 0L
    var frames = 0L; private set
    var jank = 0L; private set // frames > 34 ms
    var worstMs = 0f; private set
    var running = true

    private val sprite = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f; isFakeBoldText = true }
    private val barBg = Paint().apply { color = Color.argb(140, 0, 0, 0) }
    private val bar = Paint()
    private val bg = Paint()
    private val rect = RectF()

    fun hit(ease: Int) {
        when (ease) {
            1 -> { playerHp = max(0f, playerHp - 0.25f); shakePlayer = 1f; flash = "Ouch!" }
            2 -> { enemyHp = max(0f, enemyHp - 0.10f); shakeEnemy = 0.6f; flash = "Hit" }
            3 -> { enemyHp = max(0f, enemyHp - 0.25f); shakeEnemy = 1f; flash = "Good hit!" }
            else -> { enemyHp = max(0f, enemyHp - 0.40f); shakeEnemy = 1.3f; flash = "Critical!" }
        }
        flashT = 1f
        if (enemyHp <= 0f) {
            caught++; flash = "Caught! (#$caught)"
            Report.log("C11", "battle: enemy fainted -> caught #$caught")
            enemyIdx = (enemyIdx + 1) % enemies.size; enemyHp = 1f
        }
        if (playerHp <= 0f) { playerHp = 1f; flash = "Fainted… revived" }
    }

    fun statsLine(): String {
        val secs = (System.nanoTime() - t0) / 1e9
        return "frames=$frames in ${"%.0f".format(secs)}s (avg ${"%.1f".format(frames / max(secs, 0.001))} fps), janky(>34ms)=$jank, worst=${"%.0f".format(worstMs)}ms"
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        bg.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(120, 190, 255), Color.rgb(110, 200, 120), Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        if (lastFrame != 0L) {
            val ms = (now - lastFrame) / 1e6f
            if (ms > 34f) jank++
            if (frames > 5) worstMs = max(worstMs, ms)
        }
        lastFrame = now; frames++
        val t = (now - t0) / 1e9f
        val w = width.toFloat(); val h = height.toFloat()

        c.drawRect(0f, 0f, w, h, bg)

        playerShown += (playerHp - playerShown) * 0.12f
        enemyShown += (enemyHp - enemyShown) * 0.12f
        shakePlayer = max(0f, shakePlayer - 0.04f); shakeEnemy = max(0f, shakeEnemy - 0.04f)
        flashT = max(0f, flashT - 0.015f)

        val size = min(h * 0.5f, w * 0.22f)
        sprite.textSize = size
        val bobP = sin(t * 3f) * h * 0.04f
        val bobE = sin(t * 2.4f + 1f) * h * 0.05f
        val shP = sin(t * 60f) * 14f * shakePlayer
        val shE = sin(t * 60f) * 14f * shakeEnemy
        c.drawText(player, w * 0.22f + shP, h * 0.85f + bobP, sprite)
        c.drawText(enemies[enemyIdx], w * 0.75f + shE, h * 0.62f + bobE, sprite)

        drawBar(c, 16f, 14f, w * 0.42f, playerShown, "You")
        drawBar(c, w * 0.55f, h - 44f, w * 0.42f, enemyShown, "Wild")
        if (flashT > 0f) {
            text.alpha = (255 * flashT).toInt()
            c.drawText(flash, w * 0.38f, h * 0.5f, text)
            text.alpha = 255
        }
        if (running) postInvalidateOnAnimation()
    }

    private fun drawBar(c: Canvas, x: Float, y: Float, bw: Float, v: Float, label: String) {
        rect.set(x, y, x + bw, y + 26f)
        c.drawRoundRect(rect, 10f, 10f, barBg)
        bar.color = when {
            v > 0.5f -> Color.rgb(60, 200, 80)
            v > 0.2f -> Color.rgb(240, 190, 40)
            else -> Color.rgb(230, 60, 50)
        }
        rect.set(x + 3, y + 3, x + 3 + (bw - 6) * v.coerceIn(0f, 1f), y + 23f)
        c.drawRoundRect(rect, 8f, 8f, bar)
        text.textSize = 22f
        c.drawText(label, x + 8, y + 20f, text)
        text.textSize = 34f
    }
}
