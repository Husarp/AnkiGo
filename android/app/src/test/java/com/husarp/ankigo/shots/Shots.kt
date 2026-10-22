package com.husarp.ankigo.shots

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.RenderExtension
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import java.lang.reflect.Proxy
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import java.io.File

/*
 * Design screenshots (Paparazzi): renders the real Views off-device so their look can be checked against
 * the mockups in docs/design. Fake data only (TestData world, generated sprite shapes); images go to app/build/shots/,
 * never into the repo. Run: ./gradlew :app:testDebugUnitTest --tests 'com.husarp.ankigo.shots.*'
 */

/** One render size. Sizes in dp; rendered at 2x (xhdpi) so the PNGs stay small. */
enum class Size(val wDp: Int, val hDp: Int) {
    /** OnePlus 6T-class phone, full screen minus the system bars. */
    PHONE(412, 869),
    /** Bottom half of a split screen under AnkiDroid. */
    SPLIT(412, 400),
    /** Landscape phone (study frame G). */
    LAND(869, 412),
    /** Floating card default size (FloatScreens). */
    FLOAT(340, 480),
    /** Floating battle window default size. */
    FLOATBATTLE(320, 300),
    /** Picture-in-picture window (16:9-ish). */
    PIP(240, 136),
    /** Portrait tablet (study frame G, tablet variant; smallest width >= 600 dp). */
    TABLET(800, 1280),
    /** Landscape tablet. */
    TABLET_LAND(1280, 800),
    /** Short split-screen halves (battle must scale, not drop parts). */
    SPLIT_SHORT(412, 300),
    SPLIT_TINY(412, 250),
    /** Picture-in-picture 16:9 small / medium and 1:1. */
    PIP_169(240, 135),
    PIP_169M(320, 180),
    PIP_11(200, 200),
    /** Floating battle window default size (FloatBattleView 240 x 190). */
    FLOATB(240, 190),
    /** Tall modern phone (lane A). */
    TALL(412, 915),
    /** Small phone (lane A). */
    SMALL(360, 640),
}

enum class Theme(val night: NightMode) { LIGHT(NightMode.NOTNIGHT), DARK(NightMode.NIGHT) }

object Shots {
    /** Where the PNGs land: <module>/build/shots (the test task runs with the module as working dir). */
    val outDir: File = File(System.getProperty("user.dir"), "build/shots").apply { mkdirs() }

    fun device(size: Size, theme: Theme): DeviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = size.wDp * 2,
        screenHeight = size.hDp * 2,
        xdpi = 320,
        ydpi = 320,
        density = Density.XHIGH,
        nightMode = theme.night,
        orientation = if (size.wDp > size.hDp) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
    )

    /** Stand-in for the app's private storage: the renderer has none (its files dirs are null, which would put the
     *  save and sprite cache in the working directory, i.e. the module folder). Everything goes under build/. */
    val filesRoot: File = File(System.getProperty("user.dir"), "build/shots-files")

    /** Optional local sprites for nicer renders (never in the repo). Missing = generated shapes. */
    val spriteSource: File = File(System.getProperty("user.home"), ".cache/ankigo-shots/sprites")

    /** The render context with real (build/) storage dirs; app code that asks for files dirs gets these. */
    fun files(base: Context): Context = if (base is FilesCtx) base else FilesCtx(base)

    class FilesCtx(base: Context) : ContextWrapper(base) {
        private fun d(n: String) = File(filesRoot, n).apply { mkdirs() }
        override fun getFilesDir(): File = d("files")
        override fun getNoBackupFilesDir(): File = d("nobackup")
        override fun getCacheDir(): File = d("cache")
        override fun getCodeCacheDir(): File = d("code_cache")
        override fun getExternalFilesDir(type: String?): File = d("external")
        override fun getDir(name: String, mode: Int): File = d("app_$name")
        override fun getApplicationContext(): Context = this
        // The renderer's SharedPreferences store nothing (and its editor returns null when chained); keep them in memory.
        private val prefs = HashMap<String, MemPrefs>()
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences = prefs.getOrPut(name) { MemPrefs() }
    }

    /** Map-backed SharedPreferences (one per render). */
    class MemPrefs : android.content.SharedPreferences {
        private val m = HashMap<String, Any?>()
        override fun getAll(): MutableMap<String, *> = HashMap(m)
        override fun getString(k: String, d: String?): String? = m[k] as String? ?: d
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(k: String, d: MutableSet<String>?): MutableSet<String>? = (m[k] as Set<String>?)?.toMutableSet() ?: d
        override fun getInt(k: String, d: Int): Int = m[k] as Int? ?: d
        override fun getLong(k: String, d: Long): Long = m[k] as Long? ?: d
        override fun getFloat(k: String, d: Float): Float = m[k] as Float? ?: d
        override fun getBoolean(k: String, d: Boolean): Boolean = m[k] as Boolean? ?: d
        override fun contains(k: String): Boolean = k in m
        override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
            private val put = HashMap<String, Any?>(); private val del = HashSet<String>(); private var clear = false
            private fun p(k: String, v: Any?) = apply { put[k] = v }
            override fun putString(k: String, v: String?) = p(k, v)
            override fun putStringSet(k: String, v: MutableSet<String>?) = p(k, v?.toSet())
            override fun putInt(k: String, v: Int) = p(k, v)
            override fun putLong(k: String, v: Long) = p(k, v)
            override fun putFloat(k: String, v: Float) = p(k, v)
            override fun putBoolean(k: String, v: Boolean) = p(k, v)
            override fun remove(k: String) = apply { del += k }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean { if (clear) m.clear(); del.forEach { m.remove(it) }; m.putAll(put); return true }
            override fun apply() { commit() }
        }
    }

    /** Paparazzi rule that writes plain PNGs named after the snapshot into [outDir] (no goldens, never fails on diffs). */
    fun rule(): Paparazzi = Paparazzi(
        deviceConfig = device(Size.PHONE, Theme.LIGHT),
        theme = "GameTheme",
        renderingMode = SessionParams.RenderingMode.NORMAL,
        showSystemUi = false,
        maxPercentDifference = 100.0,
        snapshotHandler = fileHandler(outDir),
        renderExtensions = setOf(BalancedFirstFrame),
    )

    /**
     * R16 (RAM rule): before a shot's frame, layoutlib draws the screen once into one shared no-op canvas (RenderSessionImpl.NOP_CANVAS)
     * whose save / restore do nothing, so every translate, scale and concat stays on it, shot after shot. Once its scale is large, each
     * vector icon drawn into it gets a 2048 x 2048 cache (32 MB, freed only when the JVM collects): PagesShots grew past 3 GB with a
     * 768 MB heap. This wraps every shot's view so that pass draws it into a plain canvas without pixels, where save / restore work.
     */
    private object BalancedFirstFrame : RenderExtension {
        override fun renderView(contentView: View): View {
            val lp = contentView.layoutParams
            return object : FrameLayout(contentView.context) {
                override fun dispatchDraw(canvas: android.graphics.Canvas) =
                    super.dispatchDraw(if (canvas.javaClass.simpleName == "NopCanvas") android.graphics.Canvas() else canvas)
            }.apply {
                addView(contentView, FrameLayout.LayoutParams(-1, -1))
                if (lp != null) layoutParams = lp
            }
        }
    }

    // Android unit-test sources compile without java.awt, so the handler is a Proxy and the image is written through
    // reflection (ImageIO is there at run time).
    private fun fileHandler(dir: File): SnapshotHandler {
        val write = Class.forName("javax.imageio.ImageIO")
            .getMethod("write", Class.forName("java.awt.image.RenderedImage"), String::class.java, File::class.java)
        val loader = SnapshotHandler::class.java.classLoader
        return Proxy.newProxyInstance(loader, arrayOf(SnapshotHandler::class.java)) { _, m, args ->
            when (m.name) {
                "newFrameHandler" -> {
                    val snap = args!![0] as Snapshot
                    Proxy.newProxyInstance(loader, arrayOf(SnapshotHandler.FrameHandler::class.java)) { _, fm, fargs ->
                        if (fm.name == "handle") write.invoke(null, fargs!![0], "PNG", File(dir, "${snap.name ?: "shot"}.png"))
                        null
                    }
                }
                "hashCode" -> 0
                "equals" -> false
                "toString" -> "ShotsFileHandler"
                else -> null
            }
        } as SnapshotHandler
    }
}

/**
 * Renders [build] for every theme x size and saves `<name>-<size>-<theme>.png`. [build] gets a fresh context per
 * config (night resources follow the theme). The view is put in a FrameLayout of exactly that size.
 */
fun Paparazzi.shoot(
    name: String,
    sizes: List<Size> = listOf(Size.PHONE, Size.SPLIT),
    themes: List<Theme> = Theme.entries,
    offsetMillis: Long = 1000,
    build: (Context) -> View,
) {
    for (size in sizes) for (theme in themes) {
        unsafeUpdateConfig(deviceConfig = Shots.device(size, theme))
        val ctx = Shots.files(context)
        FakeGame.install(ctx)
        val v = build(ctx)
        val frame = FrameLayout(ctx).apply {
            addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        snapshot(frame, "$name-${size.name.lowercase()}-${theme.name.lowercase()}", offsetMillis)
    }
}

/**
 * Gives an Activity created with `Foo()` the render context, so code that builds its Views can run without onCreate.
 * The theme is taken from the render context as is (Activity.setTheme would call into the system).
 */
fun <A : android.app.Activity> attach(activity: A, ctx: Context): A {
    val m = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
    m.isAccessible = true
    m.invoke(activity, ctx)
    val theme = android.view.ContextThemeWrapper::class.java.getDeclaredField("mTheme")
    theme.isAccessible = true
    theme.set(activity, ctx.theme)
    return activity
}

/** Generated PNG shapes for the sprite caches (simple blobs with eyes; no Pokémon art). */
object FakeSprites {
    class Img(val w: Int, val h: Int) { val px = IntArray(w * h) }

    fun blob(size: Int, color: Int, back: Boolean = false): Img {
        val img = Img(size, size)
        val c = size / 2.0
        val r = size * 0.32
        for (y in 0 until size) for (x in 0 until size) {
            val dx = (x - c) / r
            val dy = (y - c * 1.1) / (r * 0.9)
            val d = dx * dx + dy * dy
            val ear = y < c * 0.8 && y > c * 0.35 && (Math.abs(x - c * 0.68) < size * 0.05 || Math.abs(x - c * 1.32) < size * 0.05)
            if (d <= 1.0 || ear) {
                var col = if (d > 0.82 && !ear) darker(color) else color
                if (!back) {
                    val eye = Math.abs(y - c * 0.98) < size * 0.035 &&
                        (Math.abs(x - c * 0.82) < size * 0.035 || Math.abs(x - c * 1.18) < size * 0.035)
                    if (eye) col = 0xFF1E1E2E.toInt()
                }
                img.px[y * size + x] = col
            }
        }
        return img
    }

    private fun darker(c: Int): Int {
        val r = (c shr 16 and 0xFF) * 6 / 10; val g = (c shr 8 and 0xFF) * 6 / 10; val b = (c and 0xFF) * 6 / 10
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Minimal RGBA PNG encoder. */
    fun write(img: Img, file: File) {
        file.parentFile.mkdirs()
        val raw = ByteArrayOutputStream()
        for (y in 0 until img.h) {
            raw.write(0)
            for (x in 0 until img.w) {
                val p = img.px[y * img.w + x]
                raw.write(p shr 16 and 0xFF); raw.write(p shr 8 and 0xFF); raw.write(p and 0xFF); raw.write(p ushr 24)
            }
        }
        val def = Deflater()
        def.setInput(raw.toByteArray()); def.finish()
        val z = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!def.finished()) z.write(buf, 0, def.deflate(buf))
        val out = DataOutputStream(file.outputStream())
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            out.writeInt(data.size)
            val t = type.toByteArray()
            out.write(t); out.write(data)
            val crc = CRC32(); crc.update(t); crc.update(data)
            out.writeInt(crc.value.toInt())
        }
        val hdr = ByteArrayOutputStream()
        DataOutputStream(hdr).apply { writeInt(img.w); writeInt(img.h); write(8); write(6); write(0); write(0); write(0) }
        chunk("IHDR", hdr.toByteArray())
        chunk("IDAT", z.toByteArray())
        chunk("IEND", ByteArray(0))
        out.close()
    }
}

/** A page host for screenshots: no Activity, nothing to close. */
object NoHost : com.husarp.ankigo.ui.PageHost {
    override val hostActivity: android.app.Activity? get() = null
    override fun closePage() = Unit
    override fun startForResult(intent: android.content.Intent, onResult: (Int, android.content.Intent?) -> Unit) = Unit
}

/** A page laid out like PageActivity does it: page + the bottom bar (when the page has a tab). Calls onShow. */
fun pageScreen(ctx: Context, page: com.husarp.ankigo.ui.GamePage): View {
    val col = android.widget.LinearLayout(ctx).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setBackgroundColor(com.husarp.ankigo.ui.Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
        addView(page.root, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        val tab = page.tab
        if (tab != null) addView(com.husarp.ankigo.ui.BottomNav.create(attach(android.app.Activity(), ctx), tab), android.widget.LinearLayout.LayoutParams(-1, -2))
    }
    page.onShow()
    return col
}
