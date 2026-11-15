package com.husarp.ankigo.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R36SShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)

    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun actualGroupedBoxAndDexInBothThemes() {
        for (section in listOf(TeamSection.BOX, TeamSection.DEX)) for (time in listOf(784L, 7000L)) {
            paparazzi.shoot("s-${section.name.lowercase()}-${if (time == 784L) "glints" else "still"}", listOf(Size.SMALL)) { ctx ->
                val data = TestData.data(listOf(
                    TestData.species(793, "Nihilego"), TestData.species(150, "Mewtwo", isLegendary = true),
                    TestData.species(151, "Mew", isMythical = true), TestData.species(133, "Eevee"),
                ))
                val state = GameState(createdAt = 1L, day = "2026-11-15", starterChosen = true)
                listOf(793 to 3, 150 to 2, 151 to 1, 133 to 1).forEach { (num, count) ->
                    repeat(count) { i -> state.mons += TestData.mon(data, num, 24, uid = "s-$num-$i").also {
                        it.tier = Encounters.tierOf(data.sp(num)) ?: Tier.NORMAL
                        it.favourite = true; state.viewedMons += it.uid
                    } }
                    state.caught += num; state.seen += num
                }
                setField(GameRepo, "loadedData", data); setField(GameRepo, "held", state)
                call(GameRepo, "rebuild")
                BoxDupePrefs.setStacked(ctx, true); BoxDupePrefs.setTwo(ctx, true)
                val page = TeamPage(ctx, NoHost).apply { preset(section, group = true) }
                val root = pageScreen(ctx, page)
                object : FrameLayout(ctx) {
                    override fun dispatchDraw(canvas: Canvas) {
                        val frames = all(root).mapNotNull { it.foreground as? RarityFrame.Frame }
                        frames.forEach { it.schedule = RarityFrame.starts; it.frozenTimeMs = time }
                        super.dispatchDraw(canvas)
                    }
                }.apply { addView(root, FrameLayout.LayoutParams(-1, -1)) }
            }
        }
    }

    @Test fun stackEdgesMatchRarityWithoutOuterOutlineAndRecyclingClearsColour() {
        paparazzi.shoot("s-stack-check", listOf(Size.SMALL)) { ctx ->
            val data = GameRepo.data!!
            LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                for (rarity in listOf(PokemonRarity.ULTRA, PokemonRarity.LEGENDARY, PokemonRarity.MYTHICAL)) {
                    val frame = FrameLayout(ctx)
                    val sprite = TileSprite(ctx)
                    frame.addView(sprite, FrameLayout.LayoutParams(-1, -1))
                    BoxViews.wrapTile(frame, sprite)
                    BoxDupePrefs.setStacked(ctx, true)
                    RarityFrame.bind(frame, rarity, 10)
                    BoxViews.tileBackground(frame, PickKit.cardBg(ctx, 10, false))
                    BoxViews.bindDupes(frame, sprite, BoxTile(List(3) { TestData.mon(data, 25, 24) }), data)
                    val size = Ui.dp(ctx, 80)
                    val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
                    frame.measure(spec, spec); frame.layout(0, 0, size, size)
                    val effect = frame.foreground as RarityFrame.Frame
                    effect.frozenTimeMs = 7000L; effect.schedule = RarityFrame.starts
                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    frame.draw(Canvas(bitmap))
                    val color = effect.effect.color
                    // Two straight exposed rear edges, at 74 and 76 dp; no outer edge at 79 dp.
                    assertEquals(color, bitmap.getPixel(Ui.dp(ctx, 40), Ui.dp(ctx, 74)))
                    assertEquals(color, bitmap.getPixel(Ui.dp(ctx, 40), Ui.dp(ctx, 76)))
                    assertEquals(0, bitmap.getPixel(Ui.dp(ctx, 79), Ui.dp(ctx, 40)))
                    val front = BoxViews.rarityBounds(frame)!!
                    assertEquals(Ui.dp(ctx, 3), front.left)
                    assertEquals(Ui.dp(ctx, 2), front.top)
                    bitmap.recycle()
                    addView(frame, LinearLayout.LayoutParams(size, size))
                    RarityFrame.bind(frame, PokemonRarity.PLAIN)
                    BoxViews.bindDupes(frame, sprite, BoxTile(listOf(TestData.mon(data, 25, 24))), data)
                    assertNull(frame.foreground); assertNull(frame.background)
                }
            }
        }
    }

    @Test fun scrollingKeepsVisibleGlintsOnTheSharedClock() {
        var checked = false
        paparazzi.shoot("s-scroll", listOf(Size.SMALL), listOf(Theme.LIGHT), offsetMillis = 0) { ctx ->
            val first = View(ctx).apply { RarityFrame.bind(this, PokemonRarity.ULTRA) }
            val second = View(ctx).apply { RarityFrame.bind(this, PokemonRarity.MYTHICAL) }
            val scroll = android.widget.ScrollView(ctx).apply {
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(first, LinearLayout.LayoutParams(160, 160))
                    addView(View(ctx), LinearLayout.LayoutParams(160, 1600))
                    addView(second, LinearLayout.LayoutParams(160, 160))
                })
            }
            object : FrameLayout(ctx) {
                override fun dispatchDraw(canvas: Canvas) {
                    if (!checked) {
                        val a = first.foreground as RarityFrame.Frame
                        val b = second.foreground as RarityFrame.Frame
                        a.refresh(true); b.refresh(true)
                        assertTrue(a in RarityFrame.Clock.active)
                        assertFalse(b in RarityFrame.Clock.active)
                        scroll.scrollTo(0, 40)
                        a.refresh(true)
                        assertTrue("partly visible tile keeps animating during scroll", a in RarityFrame.Clock.active)
                        scroll.fullScroll(View.FOCUS_DOWN)
                        scroll.scrollTo(0, 1760)
                        a.refresh(true); b.refresh(true)
                        assertFalse(a in RarityFrame.Clock.active)
                        assertTrue(b in RarityFrame.Clock.active)
                        checked = true
                    }
                    super.dispatchDraw(canvas)
                }
            }.apply { addView(scroll, FrameLayout.LayoutParams(-1, 400)) }
        }
        assertTrue(checked)
        assertTrue(RarityFrame.Clock.active.isEmpty())
        assertFalse(RarityFrame.Clock.posted)
    }

    @Test fun heartPixelsStayCrispAtSupportedSizes() {
        paparazzi.shoot("s-heart", listOf(Size.SMALL)) { ctx ->
            LinearLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                for (dp in listOf(12, 16, 24, 32)) {
                    val side = Ui.dp(ctx, dp)
                    val heart = ctx.getDrawable(R.drawable.ic_heart)!!.mutate()
                    heart.setTint(Ui.c(ctx, R.color.ag_pink)); heart.setBounds(0, 0, side, side)
                    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
                    heart.draw(Canvas(bitmap))
                    for (y in 0 until side) for (x in 0 until side)
                        assertTrue("crisp alpha at $dp dp", (bitmap.getPixel(x, y) ushr 24) in listOf(0, 255))
                    assertEquals(0, bitmap.getPixel(0, 0))
                    assertEquals(255, bitmap.getPixel(side / 2, side / 4) ushr 24)
                    bitmap.recycle()
                    addView(BoxViews.heartMark(ctx), LinearLayout.LayoutParams(side, side))
                }
            }
        }
    }
}
