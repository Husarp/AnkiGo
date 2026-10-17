package com.husarp.ankigo.ui.movefx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.husarp.ankigo.R
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The art of the move animations as bitmaps, by key, decoded once and kept (one small cache per app context):
 *  - a Kenney Particle Pack texture by name, e.g. `"circle_05"`, `"star_06"` (grey + alpha, 256 px, CC0): tint it with [tint];
 *  - a Showdown fx PNG as `"ps:iceball"`, `"ps:wisp"`, `"ps:purplewisp"`, `"ps:shadowball"` (100 px, CC0);
 *  - pictures drawn here in code like the mockup: `"drop"` (water droplet, 64 px) and `"shard"` (icicle pointing right, 192 x 72);
 *  - our pixel art ([FxPixelArt]) as `"flame_big_0"`..`"flame_big_5"`, `"flame_small_0"`..`"flame_small_5"` and `"leaf"`, with an
 *    optional flip suffix: `"-x"` mirrored left-right, `"-y"` upside down, `"-xy"` both (draw these with filtering off, [FxCanvas.crisp]).
 * A key with no art gives null and the renderer skips the particle.
 */
class FxArt private constructor(private val ctx: Context) {
    private val bitmaps = ArtCache<Bitmap> { load(it) }
    private val tints = HashMap<Int, ColorFilter>()

    /**
     * The bitmap for [key] (see the class), or null: no such art, or the background warm-up ([warmUp]) has not got to it yet (the
     * look skips it for this frame; nothing is ever decoded on the drawing thread while the warm-up runs).
     */
    fun bitmap(key: String): Bitmap? = bitmaps.get(key)

    /**
     * Decodes and builds all the art on a background thread, once per process (later calls do nothing), so that the first attack
     * animation finds it ready instead of decoding PNGs in the middle of a frame.
     */
    fun warmUp() {
        bitmaps.warm(KEYS) { work -> Thread(work, "fx-art-warm").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start() }
    }

    /** Test / screenshot use: waits up to [ms] for the warm-up thread, so a frozen still frame shows all the art. */
    fun awaitWarm(ms: Long): Boolean = bitmaps.awaitWarm(ms)

    private var lastTint = 0
    private var lastFilter: ColorFilter? = null

    /** A colour filter that multiplies the colour channels with [color]'s RGB and keeps the alpha (the mockup's `tinted()`), shared per colour. */
    fun tint(color: Int): ColorFilter {
        val c = color or OPAQUE
        lastFilter?.let { if (c == lastTint) return it }   // consecutive particles share a colour: no map lookup, no boxing
        val f = tints.getOrPut(c) { LightingColorFilter(c, 0) }
        lastTint = c; lastFilter = f
        return f
    }

    private fun load(key: String): Bitmap? {
        val dash = key.indexOf('-')
        val base = if (dash < 0) key else key.substring(0, dash)
        val flips = if (dash < 0) "" else key.substring(dash + 1)
        return when {
            base == "drop" -> drop()
            base == "shard" -> shard()
            base == "leaf" -> pixels(FxPixelArt.LEAF_ROWS, FxPixelArt.LEAF_PALETTE, 'x' in flips, 'y' in flips)
            base.startsWith("flame_big_") -> flame(FxPixelArt.FLAME_BIG, base.removePrefix("flame_big_"), flips)
            base.startsWith("flame_small_") -> flame(FxPixelArt.FLAME_SMALL, base.removePrefix("flame_small_"), flips)
            else -> {
                val id = resource(base) ?: return null
                val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                BitmapFactory.decodeResource(ctx.resources, id, o)?.also { it.setHasMipMap(true) }
            }
        }
    }

    private fun flame(set: List<String>, index: String, flips: String): Bitmap? {
        val i = index.toIntOrNull() ?: return null
        if (i !in set.indices) return null
        return pixels(FxPixelArt.rows(set[i]), FxPixelArt.FLAME_PALETTE, 'x' in flips, 'y' in flips)
    }

    private fun resource(name: String): Int? = when (name) {
        "circle_02" -> R.drawable.fxk_circle_02
        "circle_05" -> R.drawable.fxk_circle_05
        "dirt_01" -> R.drawable.fxk_dirt_01
        "dirt_02" -> R.drawable.fxk_dirt_02
        "fire_01" -> R.drawable.fxk_fire_01
        "flame_05" -> R.drawable.fxk_flame_05
        "light_02" -> R.drawable.fxk_light_02
        "magic_05" -> R.drawable.fxk_magic_05
        "slash_03" -> R.drawable.fxk_slash_03
        "smoke_01" -> R.drawable.fxk_smoke_01
        "smoke_05" -> R.drawable.fxk_smoke_05
        "smoke_08" -> R.drawable.fxk_smoke_08
        "spark_01" -> R.drawable.fxk_spark_01
        "spark_05" -> R.drawable.fxk_spark_05
        "star_01" -> R.drawable.fxk_star_01
        "star_04" -> R.drawable.fxk_star_04
        "star_06" -> R.drawable.fxk_star_06
        "star_08" -> R.drawable.fxk_star_08
        "twirl_01" -> R.drawable.fxk_twirl_01
        "ps:iceball" -> R.drawable.fxs_iceball
        "ps:wisp" -> R.drawable.fxs_wisp
        "ps:purplewisp" -> R.drawable.fxs_purplewisp
        "ps:shadowball" -> R.drawable.fxs_shadowball
        else -> null
    }

    // ---- pixel art (one bitmap pixel per art pixel; the renderer scales it without smoothing) ----
    private fun pixels(rows: List<String>, palette: Map<Char, Int>, flipX: Boolean, flipY: Boolean): Bitmap {
        val h = rows.size
        val w = rows[0].length
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            val row = rows[if (flipY) h - 1 - y else y]
            for (x in 0 until w) {
                val c = row[if (flipX) w - 1 - x else x]
                if (c != '.') b.setPixel(x, y, palette[c] ?: 0)
            }
        }
        return b
    }

    // ---- drawn in code, like the mockup (ours) ----
    /** Water droplet with a highlight: the mockup's radial gradient between two circles ((25, 24) r 2 and (32, 32) r 30), per pixel. */
    private fun drop(): Bitmap {
        val n = 64
        val px = IntArray(n * n)
        val stops = floatArrayOf(0f, .35f, .82f, .94f, 1f)
        val cols = intArrayOf(0xFFE2F5FF.toInt(), 0xFF72C3FF.toInt(), 0xFF2A86E0.toInt(), 0xFF1C66BD.toInt(), 0x001C66BD)
        val dx = 7f; val dy = 8f; val dr = 28f; val r0 = 2f
        val a = dx * dx + dy * dy - dr * dr
        for (y in 0 until n) for (x in 0 until n) {
            val wx = x + .5f - 25f; val wy = y + .5f - 24f
            val bq = -2f * (wx * dx + wy * dy + r0 * dr)
            val cq = wx * wx + wy * wy - r0 * r0
            val disc = bq * bq - 4f * a * cq
            val t = if (disc < 0f) 1f else {
                val sq = sqrt(disc)
                val t1 = (-bq + sq) / (2f * a); val t2 = (-bq - sq) / (2f * a)
                val hi = maxOf(t1, t2); val lo = minOf(t1, t2)
                if (r0 + hi * dr >= 0f) hi else lo
            }.coerceIn(0f, 1f)
            var s = 1
            while (s < stops.size - 1 && t > stops[s]) s++
            val f = (t - stops[s - 1]) / (stops[s] - stops[s - 1])
            val c0 = cols[s - 1]; val c1 = cols[s]
            fun ch(sh: Int) = (((c0 ushr sh) and 255) + (((c1 ushr sh) and 255) - ((c0 ushr sh) and 255)) * f).toInt().coerceIn(0, 255)
            val cover = (30f - hypot(x + .5f - 32f, y + .5f - 32f) + .5f).coerceIn(0f, 1f)
            val al = (ch(24) * cover).toInt()
            px[y * n + x] = (al shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        b.setPixels(px, 0, n, 0, 0, n, n)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); alpha = 230 }
        c.save(); c.translate(23f, 21f); c.rotate(Math.toDegrees(-.7).toFloat())
        c.drawOval(RectF(-7f, -4.5f, 7f, 4.5f), p)
        c.restore()
        return b
    }

    /** Icicle shard pointing right, 192 x 72: a faceted spear with a pale glow (the mockup's SHARD, ours, not Showdown's icicle). */
    private fun shard(): Bitmap {
        val b = Bitmap.createBitmap(192, 72, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 192f, 0f,
                intArrayOf(0x00 shl 24 or 0x96D7FF, (.75f * 255).toInt() shl 24 or 0xA0DEFF, (.95f * 255).toInt() shl 24 or 0xE1F7FF, 0xFFFFFFFF.toInt()),
                floatArrayOf(0f, .35f, .85f, 1f), Shader.TileMode.CLAMP,
            )
            setShadowLayer(14f, 0f, 0f, 0xFF8FDCFF.toInt())
        }
        val body = Path().apply { moveTo(8f, 36f); lineTo(50f, 20f); lineTo(150f, 24f); lineTo(186f, 36f); lineTo(150f, 48f); lineTo(50f, 52f); close() }
        c.drawPath(body, fill)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        line.color = 0xFFFFFFFF.toInt(); line.alpha = 230; line.strokeWidth = 3f
        c.drawLine(40f, 36f, 184f, 36f, line)
        line.alpha = 128; line.strokeWidth = 1.5f
        c.drawPath(Path().apply { moveTo(50f, 22f); lineTo(150f, 26f); lineTo(184f, 36f) }, line)
        return b
    }

    companion object {
        private const val OPAQUE = 0xFF000000.toInt()

        /** Every art key the looks use: the textures, the two drawn pictures, the leaf and the flame frames (as drawn, and mirrored for a target on the left). */
        private val KEYS: List<String> = buildList {
            addAll(listOf(
                "circle_02", "circle_05", "dirt_01", "dirt_02", "fire_01", "flame_05", "light_02", "magic_05", "slash_03", "smoke_01", "smoke_05",
                "smoke_08", "spark_01", "spark_05", "star_01", "star_04", "star_06", "star_08", "twirl_01",
                "ps:iceball", "ps:wisp", "ps:purplewisp", "ps:shadowball", "drop", "shard",
                "leaf", "leaf-x", "leaf-y", "leaf-xy",
            ))
            for (i in 0 until 6) for (flip in arrayOf("", "-x")) { add("flame_big_$i$flip"); add("flame_small_$i$flip") }
        }

        @Volatile private var cached: FxArt? = null
        @Volatile private var cachedFor: Context? = null

        /** The art cache of this app (one, kept while the app context stays the same). */
        fun of(ctx: Context): FxArt {
            val app = ctx.applicationContext ?: ctx
            val c = cached
            if (c != null && cachedFor === app) return c
            synchronized(this) {
                val again = cached
                if (again != null && cachedFor === app) return again
                return FxArt(app).also { cached = it; cachedFor = app }
            }
        }
    }
}
