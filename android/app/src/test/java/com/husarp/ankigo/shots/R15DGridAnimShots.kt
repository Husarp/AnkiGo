package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.CellRow
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.GridAnimDrawable
import com.husarp.ankigo.ui.GridClock
import com.husarp.ankigo.ui.GridShape
import com.husarp.ankigo.ui.GridSprites
import com.husarp.ankigo.ui.PanelSprites
import com.husarp.ankigo.ui.SettingsCatalog
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSearch
import com.husarp.ankigo.ui.SettingsSection
import com.husarp.ankigo.ui.ScrollGate
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.TileSprite
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * PLAN 156 (R15-D): animated caught Pokémon in the Pokédex grid and the Box grid. Real cells with GIFs in the sprite cache (generated
 * blobs, 64 x 64, 4 frames, 100 ms each): a caught Pokémon gets the animation drawable and shows its first frame, seen-only and unseen
 * ones, the setting off, reduced motion and "nothing cached" stay as they were; the clock runs only while a cell is visible; a fling
 * keeps everything still. PNGs: r15d-*. The Paparazzi host attaches a view for the snapshot and detaches it afterwards.
 */
class R15DGridAnimShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    // ---- a clock driven by hand ----

    private class ManualFrames : GridClock.Frames {
        var pending: ((Long) -> Unit)? = null
        var posts = 0
        override fun post(callback: (Long) -> Unit) { pending = callback; posts++ }
        /** One vsync at [ns]; the clock posts the next one itself while a cell is visible. */
        fun fire(ns: Long) { val c = pending; pending = null; c?.invoke(ns) }
        val waiting get() = pending != null
    }

    private val frames = ManualFrames()
    private lateinit var realFrames: GridClock.Frames
    private lateinit var realPost: (Runnable) -> Unit
    private lateinit var realPool: Executor

    @Before fun setUp() { // inside the Paparazzi rule: PanelSprites needs the renderer's main looper
        realFrames = GridClock.frames
        realPost = GridSprites.uiPost
        realPool = PanelSprites.pool
        GridClock.reset()
        GridClock.frames = frames
        GridSprites.uiPost = { it.run() }
    }

    @After fun tearDown() {
        GridClock.reset()
        GridClock.frames = realFrames
        GridSprites.uiPost = realPost
        PanelSprites.pool = realPool
    }

    // ---- GIFs made here ----

    /** [count] frames of a bouncing blob of [color] on a [size] x [size] canvas. */
    private fun blobFrames(size: Int, color: Int, count: Int = 4): List<IntArray> = List(count) { i ->
        val blob = FakeSprites.blob(size, color).px
        val dy = intArrayOf(0, 2, 5, 2)[i % 4]
        IntArray(size * size).also { out -> for (y in 0 until size - dy) System.arraycopy(blob, y * size, out, (y + dy) * size, size) }
    }

    private fun gifBytes(w: Int, h: Int, frames: List<IntArray>, delayCs: Int = 10): ByteArray {
        val palette = LinkedHashMap<Int, Int>().apply { put(0, 0) }
        fun argb(p: Int) = if (p ushr 24 == 0) 0 else p or (0xFF shl 24)
        for (f in frames) for (p in f) { val c = argb(p); if (c !in palette) palette[c] = palette.size }
        require(palette.size <= 256)
        val o = ByteArrayOutputStream()
        fun u16(v: Int) { o.write(v and 0xFF); o.write(v shr 8 and 0xFF) }
        o.write("GIF89a".toByteArray()); u16(w); u16(h); o.write(0xF7); o.write(0); o.write(0)
        val colours = palette.keys.toList()
        for (i in 0 until 256) { val c = colours.getOrElse(i) { 0 }; o.write(c shr 16 and 0xFF); o.write(c shr 8 and 0xFF); o.write(c and 0xFF) }
        for (f in frames) {
            o.write(0x21); o.write(0xF9); o.write(4); o.write((2 shl 2) or 1); u16(delayCs); o.write(0); o.write(0) // clear to transparent after the frame
            o.write(0x2C); u16(0); u16(0); u16(w); u16(h); o.write(0)
            o.write(8)
            val bits = ByteArrayOutputStream()
            var cur = 0; var n = 0
            fun put(code: Int) { cur = cur or (code shl n); n += 9; while (n >= 8) { bits.write(cur and 0xFF); cur = cur ushr 8; n -= 8 } }
            for (p in f) { put(256); put(palette.getValue(argb(p))) } // CLEAR before every index: valid and trivial
            put(257)
            if (n > 0) bits.write(cur and 0xFF)
            val data = bits.toByteArray()
            var p = 0
            while (p < data.size) { val k = minOf(255, data.size - p); o.write(k); o.write(data, p, k); p += k }
            o.write(0)
        }
        o.write(0x3B)
        return o.toByteArray()
    }

    private fun cacheGif(ctx: Context, num: Int, shiny: Boolean = false, size: Int = 64, color: Int = 0xFFE5A000.toInt()) {
        val dir = File(Sprites.dir(ctx), "front").apply { mkdirs() }
        File(dir, "$num${if (shiny) "s" else ""}.gif").writeBytes(gifBytes(size, size, blobFrames(size, color)))
    }

    // ---- the fixture ----

    private val st get() = GameRepo.state!!

    private fun setup(theme: Theme = Theme.LIGHT, reduce: Boolean = false, setting: Boolean = true, gifs: Boolean = true, tall: Boolean = false): Context {
        // tall: the whole grid is laid out (a ListView binds only the rows that fit)
        paparazzi.unsafeUpdateConfig(deviceConfig = if (tall) Variants.TALL.device(theme) else Shots.device(Size.PHONE, theme))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        PanelSprites.pool = Executor { it.run() } // decode in place
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", reduce).commit()
        AppPrefs.setAnimateLists(c, setting)
        if (gifs) {
            // every species of the fake world has an animation on the phone, the ones that are only seen or never met included
            for (sp in FakeGame.data.species.values) cacheGif(c, sp.num)
        }
        return c
    }

    private fun shoot(v: View, name: String) {
        val f = FrameLayout(v.context).apply { addView(v, FrameLayout.LayoutParams(-1, -1)) }
        paparazzi.snapshot(f, name, 1000)
    }

    private fun lazyList(page: Any, field: String) = getField(page, field) as ListView

    /** The cells of the rows a list has drawn: (species or uid holder, picture view). */
    private fun cells(list: ListView): List<Any> =
        (0 until list.childCount).flatMap { i -> (list.getChildAt(i) as? CellRow)?.cells.orEmpty().map { it.tag!! } }

    private fun isMoving(d: Drawable?) = d is GridAnimDrawable

    // ---- the Pokédex ----

    private fun dexRun(theme: Theme, setting: Boolean = true, reduce: Boolean = false, gifs: Boolean = true): List<Triple<Int, Boolean, ImageView>> {
        val c = setup(theme, reduce, setting, gifs, tall = true)
        st.seen.remove(151)   // never met: a silhouette
        st.caught.remove(151)
        val page = DexPage(c, NoHost)
        val screen = pageScreen(c, page)
        shoot(screen, "r15d-dex-${if (!gifs) "nogif" else if (!setting) "off" else if (reduce) "reduced" else "on"}-${theme.name.lowercase()}")
        return cells(lazyList(page, "list")).mapNotNull { cell ->
            val sp = getField(cell, "sp") as? com.husarp.ankigo.game.Species ?: return@mapNotNull null
            Triple(sp.num, sp.num in st.caught, getField(cell, "img") as ImageView)
        }
    }

    @Test fun theDexMovesOnlyCaughtPokemonLightAndDark() {
        for (theme in Theme.entries) {
            val cells = dexRun(theme)
            assertTrue("the grid drew its cells", cells.size >= 20)
            for ((num, caught, img) in cells) {
                if (caught) {
                    assertTrue("#$num is caught: it moves", isMoving(img.drawable))
                    val d = img.drawable as GridAnimDrawable
                    assertEquals("the animation's own size, so the tile scale follows the sprite's pixels", 64, d.intrinsicWidth)
                    assertEquals("the first frame shows", 0, d.shown)
                } else {
                    assertFalse("#$num is seen only / unseen: it stays a still picture", isMoving(img.drawable))
                }
            }
            val unseen = cells.single { it.first == 151 }
            assertFalse(unseen.second)
            assertTrue("a silhouette is a plain picture", unseen.third.drawable !is GridAnimDrawable)
            val seenOnly = cells.filter { it.first in listOf(65, 150) }
            assertEquals(2, seenOnly.size)
            assertTrue("seen only keeps the grey look", seenOnly.all { it.third.colorFilter != null })
        }
    }

    @Test fun theSettingIsOnByDefaultAndOffGivesTodaysStaticGrid() {
        assertTrue("on by default", AppPrefs.animateLists(Shots.files(paparazzi.context)))
        val cells = dexRun(Theme.LIGHT, setting = false)
        assertTrue(cells.none { isMoving(it.third.drawable) })
        assertTrue("the static pictures are there", cells.all { it.third.drawable is BitmapDrawable })
        assertEquals(0, GridClock.activeCount)
        val c = Shots.files(paparazzi.context)
        AppPrefs.setAnimateLists(c, true)
        assertTrue(AppPrefs.animateLists(c))
        AppPrefs.setAnimateLists(c, false)
        assertFalse(AppPrefs.animateLists(c))
    }

    @Test fun removeBattleAnimationsKeepsTheGridStill() {
        val cells = dexRun(Theme.LIGHT, reduce = true)
        assertTrue(cells.none { isMoving(it.third.drawable) })
    }

    @Test fun nothingCachedMeansTodaysStaticPictureAndNoDownload() {
        val cells = dexRun(Theme.LIGHT, gifs = false)
        assertTrue(cells.none { isMoving(it.third.drawable) })
        assertTrue(cells.all { it.third.drawable is BitmapDrawable })
        val c = Shots.files(paparazzi.context)
        val fronts = File(Sprites.dir(c), "front").listFiles().orEmpty().map { it.name }
        assertTrue("no animation was fetched: $fronts", fronts.none { it.endsWith(".gif") })
    }

    // ---- the Box ----

    private fun boxRun(theme: Theme, group: Boolean = true, before: (Context) -> Unit = {}): List<Pair<String, ImageView>> {
        val c = setup(theme, tall = true)
        before(c)
        val page = TeamPage(c, NoHost)
        page.preset(TeamSection.BOX, group = group)
        shoot(pageScreen(c, page), "r15d-box-${if (group) "grouped" else "single"}-${theme.name.lowercase()}")
        return cells(lazyList(page, "boxList")).mapNotNull { cell ->
            val uid = getField(cell, "uid") as? String ?: return@mapNotNull null
            uid to (getField(cell, "img") as ImageView)
        }
    }

    @Test fun everyBoxTileMovesLightAndDark() {
        for (theme in Theme.entries) {
            val tiles = boxRun(theme)
            assertTrue("the Box drew its tiles", tiles.size >= 10)
            for ((uid, img) in tiles) assertTrue("tile $uid moves", isMoving(img.drawable))
        }
    }

    @Test fun aBoxTilePlaysTheAnimationOfTheCopyItShows() {
        // the shiny copy of #54 has its own, smaller animation; ungrouped, every copy is a tile of its own
        val tiles = boxRun(Theme.LIGHT, group = false) { c ->
            st.mon("b3")!!.shiny = true
            cacheGif(c, 54, shiny = true, size = 48, color = 0xFFFFD700.toInt())
        }
        val shinyTile = tiles.single { it.first == "b3" }.second.drawable
        assertTrue(isMoving(shinyTile))
        assertEquals("the shiny animation", 48, shinyTile.intrinsicWidth)
        val plainCopy = tiles.single { it.first == "b15" }.second.drawable
        assertEquals("the plain copy of the same species keeps the normal one", 64, plainCopy.intrinsicWidth)
    }

    // ---- one view: bind, recycle, newest request, fling ----

    private fun tile(c: Context) = TileSprite(c)

    private fun sp(num: Int) = FakeGame.data.sp(num)

    @Test fun aRecycledCellStopsItsOldAnimation() {
        val c = setup()
        var activeBefore = -1
        var activeAfterTick = -1
        val probe = Probe(c) { v ->
            activeBefore = GridClock.activeCount
            // the cell is reused for a seen-only Pokemon: a still picture takes the animation's place. On a phone ImageView hides the old
            // drawable at once; the renderer treats the app as an old one and does not, so the clock's next tick drops it (no view any more).
            GridSprites.bind(c, v, 65, false, animate = false, gate = null)
            frames.fire(System.nanoTime())
            activeAfterTick = GridClock.activeCount
        }
        GridSprites.bind(c, probe, 25, false, animate = true, gate = null)
        assertTrue(probe.drawable is GridAnimDrawable)
        shoot(probe, "r15d-recycle")
        assertEquals("one animation was running while the cell was drawn", 1, activeBefore)
        assertEquals("the old animation is gone from the clock", 0, activeAfterTick)
        assertTrue(probe.drawable !is GridAnimDrawable)
    }

    @Test fun theNewestRequestPerViewWins() {
        val c = setup()
        val queue = ArrayDeque<Runnable>()
        PanelSprites.pool = Executor { queue.addLast(it) } // decodes wait until the test lets them run
        cacheGif(c, 54, size = 40)
        val img = tile(c)
        GridSprites.bind(c, img, 25, false, animate = true, gate = null)   // asks for #25 (64 px)
        GridSprites.bind(c, img, 54, false, animate = true, gate = null)   // the view is reused for #54 (40 px) before #25 is decoded
        assertEquals("both are queued; the first one finds itself stale and skips its work", 2, queue.size)
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertEquals("the picture is the newest request's", 40, img.drawable.intrinsicWidth)
        assertTrue(img.drawable is GridAnimDrawable)
    }

    @Test fun aFlingKeepsEverythingStillAndTheSettleStartsTheVisibleOnes() {
        val c = setup()
        val a = tile(c)
        val b = tile(c)
        val gate = ScrollGate { listOf(a, b) }
        gate.setFlinging(true)
        GridSprites.bind(c, a, 25, false, animate = true, gate = gate)
        GridSprites.bind(c, b, 26, false, animate = true, gate = gate)
        assertTrue("new cells bind the static picture during a fling", a.drawable !is GridAnimDrawable && b.drawable !is GridAnimDrawable)
        assertTrue(a.drawable != null && b.drawable != null)
        gate.setFlinging(false)
        assertTrue("the list settled: they start", a.drawable is GridAnimDrawable && b.drawable is GridAnimDrawable)
        // a settle with nothing waiting changes nothing, and a view that already moves is not restarted
        val keep = a.drawable
        gate.setFlinging(true); gate.setFlinging(false)
        assertTrue(a.drawable === keep)
    }

    @Test fun anAnimationThatMovesFreezesOnItsFrameWhileTheListFlings() {
        val c = setup()
        val gate = ScrollGate { emptyList() }
        val img = tile(c)
        GridSprites.bind(c, img, 25, false, animate = true, gate = gate)
        val d = img.drawable as GridAnimDrawable
        d.setVisible(true, true)
        val t = System.nanoTime()
        gate.setFlinging(true)
        d.advance(t + 250_000_000L)
        assertEquals("a fling: no new frame", 0, d.shown)
        gate.setFlinging(false)
        d.advance(t + 250_000_000L)
        assertEquals("settled: the frame of the real time", 2, d.shown)
        GridClock.reset()
    }

    @Test fun aFlingThatBeginsWhileTheDecodeIsRunningLeavesTheStaticPictureUntilTheSettle() {
        val c = setup()
        val queue = ArrayDeque<Runnable>()
        PanelSprites.pool = Executor { queue.addLast(it) }
        val img = tile(c)
        val gate = ScrollGate { listOf(img) }
        GridSprites.bind(c, img, 25, false, animate = true, gate = gate)
        gate.setFlinging(true)
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertTrue("decoded during the fling: not shown yet", img.drawable !is GridAnimDrawable)
        gate.setFlinging(false)
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertTrue("shown when the list settled", img.drawable is GridAnimDrawable)
    }

    @Test fun aPictureBiggerThanItsTileIsShrunkToTheTileButKeepsItsOwnSizeAndAnimates() {
        val c = setup()
        cacheGif(c, 25, size = 230)
        val img = tile(c)
        val gate = ScrollGate(GridShape(36, 100, 90)) { emptyList() } // a tile with a 100 x 90 px room
        GridSprites.bind(c, img, 25, false, animate = true, gate = gate)
        val d = img.drawable as? GridAnimDrawable ?: error("a big GIF still moves: ${img.drawable}")
        assertEquals("the GIF's own size, so the tile scale does not change", 230, d.intrinsicWidth)
        assertEquals(230, d.intrinsicHeight)
        assertTrue("the frames are kept at the room: ${d.keptWidth} x ${d.keptHeight}", d.keptWidth in 89..90 && d.keptHeight in 89..90)
        // drawn at its own size the shrunk frame is blown up again without smoothing: only the colours of the source appear
        val full = android.graphics.Bitmap.createBitmap(230, 230, android.graphics.Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 230, 230)
        d.draw(Canvas(full))
        val px = IntArray(230 * 230).also { full.getPixels(it, 0, 230, 0, 0, 230, 230) }
        val source = blobFrames(230, 0xFFE5A000.toInt())[0].map { if (it ushr 24 == 0) 0 else it or (0xFF shl 24) }.toSet()
        assertTrue("sharp: ${px.toSet().size} colours drawn, ${source.size} in the file", px.toSet().all { it in source })
        assertTrue("and something is drawn", px.any { it ushr 24 != 0 })
        // the same GIF in a tile with a big room is not shrunk
        val roomy = tile(c)
        GridSprites.bind(c, roomy, 25, false, animate = true, gate = ScrollGate(GridShape(36, 400, 400)) { emptyList() })
        assertEquals(230, (roomy.drawable as GridAnimDrawable).keptWidth)
    }

    // ---- the clock ----

    /** An ImageView that runs [onDrawn] once, in the first draw pass of the snapshot (attached and shown). */
    private class Probe(ctx: Context, private val onDrawn: (ImageView) -> Unit) : ImageView(ctx) {
        private var done = false
        var invalidations = 0
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!done) { done = true; onDrawn(this) }
        }
        override fun invalidateDrawable(dr: Drawable) { invalidations++; super.invalidateDrawable(dr) }
    }

    @Test fun theClockRunsOnlyWhileACellIsVisibleAndMovesTheFrameByRealTime() {
        val c = setup()
        val log = mutableListOf<String>()
        var probe: Probe? = null
        probe = Probe(c) { v ->
            val d = v.drawable as GridAnimDrawable
            log += "visible: active=${GridClock.activeCount} running=${GridClock.running}"
            d.setVisible(true, true) // restart: the loop starts now
            val t = System.nanoTime()
            val before = probe!!.invalidations
            frames.fire(t + 20_000_000L)
            log += "20 ms: frame=${d.shown} invalidated=${probe!!.invalidations - before}"
            frames.fire(t + 150_000_000L)
            log += "150 ms: frame=${d.shown}"
            val inv = probe!!.invalidations
            frames.fire(t + 160_000_000L)
            log += "160 ms: frame=${d.shown} invalidated=${probe!!.invalidations - inv}"
            frames.fire(t + 260_000_000L)
            log += "260 ms: frame=${d.shown}"
            frames.fire(t + 410_000_000L)
            log += "410 ms: frame=${d.shown} (the loop wrapped)"
            v.visibility = View.INVISIBLE
            log += "hidden: active=${GridClock.activeCount}"
            frames.fire(t + 520_000_000L)
            log += "after a hidden frame: waiting=${frames.waiting}"
            v.visibility = View.VISIBLE
            log += "visible again: active=${GridClock.activeCount} waiting=${frames.waiting}"
        }
        GridSprites.bind(c, probe, 25, false, animate = true, gate = null)
        shoot(probe, "r15d-clock")
        assertEquals(listOf(
            "visible: active=1 running=true",
            "20 ms: frame=0 invalidated=0",
            "150 ms: frame=1",
            "160 ms: frame=1 invalidated=0",
            "260 ms: frame=2",
            "410 ms: frame=0 (the loop wrapped)",
            "hidden: active=0",
            "after a hidden frame: waiting=false",
            "visible again: active=1 waiting=true",
        ), log)
        // the snapshot host detached the view: nothing is left in the clock, and its next frame stops the loop
        assertEquals(0, GridClock.activeCount)
        frames.fire(System.nanoTime())
        assertFalse("no callback left behind", frames.waiting)
        assertFalse(GridClock.running)
    }

    @Test fun theClockSlowsDownWhenFramesAreDroppedAndKeepsMoving() {
        val c = setup()
        var stepAfter = 0
        var shownAfter = -1
        val probe = Probe(c) { v ->
            val d = v.drawable as GridAnimDrawable
            d.setVisible(true, true)
            var t = System.nanoTime()
            repeat(40) { t += 45_000_000L; frames.fire(t) } // every vsync arrives 45 ms after the one before: far over budget
            stepAfter = GridClock.step
            shownAfter = d.shown
        }
        GridSprites.bind(c, probe, 25, false, animate = true, gate = null)
        shoot(probe, "r15d-clock-slow")
        assertTrue("the interval went up to every 2nd vsync or more: $stepAfter", stepAfter >= 2)
        assertTrue("and the animation kept going", shownAfter in 0..3)
    }

    @Test fun aFlingTakesItsAnimationsOffTheClockAndNoFrameCallbackIsPostedUntilTheSettle() {
        val c = setup()
        val log = mutableListOf<String>()
        var gate: ScrollGate? = null
        var probe: Probe? = null
        probe = Probe(c) { v ->
            val d = v.drawable as GridAnimDrawable
            d.setVisible(true, true)
            log += "moving: active=${GridClock.activeCount} waiting=${frames.waiting}"
            gate!!.setFlinging(true)
            log += "fling: active=${GridClock.activeCount}"
            val posts = frames.posts
            var t = System.nanoTime()
            repeat(30) { t += 16_000_000L; frames.fire(t) } // vsyncs go by during the fling
            log += "30 vsyncs of flying: new posts=${frames.posts - posts} waiting=${frames.waiting} running=${GridClock.running}"
            gate!!.setFlinging(false)
            log += "settled: active=${GridClock.activeCount} waiting=${frames.waiting}"
            t += 400_000_000L
            frames.fire(t)
            log += "next vsync: waiting=${frames.waiting}"
        }
        gate = ScrollGate { listOf(probe!!) }
        GridSprites.bind(c, probe, 25, false, animate = true, gate = gate)
        shoot(probe, "r15d-fling-clock")
        assertEquals(listOf(
            "moving: active=1 waiting=true",
            "fling: active=0",
            "30 vsyncs of flying: new posts=0 waiting=false running=false",
            "settled: active=1 waiting=true",
            "next vsync: waiting=true",
        ), log)
    }

    @Test fun aFlingOfOneListLeavesTheClockToTheOtherListAndAHiddenCellDoesNotComeBack() {
        val c = setup()
        val log = mutableListOf<String>()
        var a: Probe? = null
        var b: Probe? = null
        val gateA = ScrollGate { listOf(a!!) }
        val gateB = ScrollGate { listOf(b!!) }
        a = Probe(c) {}
        b = Probe(c) {}
        val c2 = Probe(c) { _ ->
            for (v in listOf(a, b)) (v!!.drawable as GridAnimDrawable).setVisible(true, true)
            log += "both on the clock: active=${GridClock.activeCount}"
            gateA.setFlinging(true)
            val posts = frames.posts
            frames.fire(System.nanoTime())
            log += "list A flies: active=${GridClock.activeCount} reposted=${frames.posts - posts} waiting=${frames.waiting}"
            gateB.setFlinging(true)
            frames.fire(System.nanoTime())
            log += "list B flies too: active=${GridClock.activeCount} waiting=${frames.waiting}"
            a!!.visibility = View.INVISIBLE // scrolled away / hidden while it flew
            gateA.setFlinging(false)
            log += "A settles, its cell is hidden: active=${GridClock.activeCount}"
            gateB.setFlinging(false)
            log += "B settles: active=${GridClock.activeCount} waiting=${frames.waiting}"
        }
        GridSprites.bind(c, a, 25, false, animate = true, gate = gateA)
        GridSprites.bind(c, b, 26, false, animate = true, gate = gateB)
        GridSprites.bind(c, c2, 54, false, animate = false, gate = null)
        shoot(LinearLayout(c).apply { addView(a, 100, 100); addView(b, 100, 100); addView(c2, 100, 100) }, "r15d-fling-two-lists")
        assertEquals(listOf(
            "both on the clock: active=2",
            "list A flies: active=1 reposted=1 waiting=true",
            "list B flies too: active=0 waiting=false",
            "A settles, its cell is hidden: active=0",
            "B settles: active=1 waiting=true",
        ), log)
    }

    // ---- the setting ----

    @Test fun theSettingsLookCardHasTheSwitchAndSearchFindsIt() {
        paparazzi.shootPage("r15d-settings-look", listOf(Variants.PHONE)) { ctx ->
            val c = ShotCtx(ctx)
            val p = SettingsPage(c, NoHost)
            val v = pageScreen(c, p)
            p.open(SettingsSection.LOOK)
            v
        }
        val e = SettingsCatalog.all.single { it.id == "animateLists" }
        assertEquals("Animate Pokémon in lists", e.title)
        assertEquals("Look", SettingsSearch.match("animate").first().where)
        assertTrue("found by its words too", SettingsSearch.match("pokemon lists").any { it.info.id == "animateLists" })
    }

    // ---- looks ----

    @Test fun theFramesOfAnAnimationAreSharpAtTileScale() {
        for (theme in Theme.entries) {
            val c = setup(theme)
            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(com.husarp.ankigo.ui.Ui.c(c, R.color.ag_card)) }
            val t = System.nanoTime()
            for (k in 0 until 4) {
                val img = TileSprite(c)
                GridSprites.bind(c, img, 25, false, animate = true, gate = null)
                (img.drawable as GridAnimDrawable).advance(t + k * 100_000_000L + 10_000_000L)
                row.addView(img, LinearLayout.LayoutParams(0, 180, 1f))
            }
            shoot(row, "r15d-frames-${theme.name.lowercase()}")
            val shown = (0 until 4).map { (row.getChildAt(it) as ImageView).drawable as GridAnimDrawable }.map { it.shown }
            assertEquals("each tile shows the frame of its time", listOf(0, 1, 2, 3), shown)
        }
    }
}
