package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.drawable.RippleDrawable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.game.data.DexEntry
import com.husarp.ankigo.ui.ArrowCol
import com.husarp.ankigo.ui.DetailFormat
import com.husarp.ankigo.ui.DetailOpenStore
import com.husarp.ankigo.ui.DetailSections
import com.husarp.ankigo.ui.DexDetail
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.ItemWindow
import com.husarp.ankigo.ui.LevelTier
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.StatInfo
import com.husarp.ankigo.ui.StatInfoText
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * R18-G: the Pokémon details of round 18 (picks 243B + 239B + 239S1 + 242A + 254A + 260A, plus 241 / 273 / 226) on the real Views. A Pikachu-like
 * main (Lv 23, holds an Amulet Coin, Training and Info open), an Exp. Share holder with eight evolutions, one that holds nothing and does
 * not evolve, the Pokédex entry; 412 and 360 dp wide, light and dark. PNGs: r18g-<what>-<size>-<theme>[-s<slice>].
 */
class R18GDetailsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val PHONE = Variants.PHONE
    private val W360 = Variant("w360", 360, 740)
    private val sizes = listOf(PHONE, W360)
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!
    private val sample = DexEntry("Mouse Pokémon", "When several of these Pokémon gather, their electricity could build and cause lightning storms. (made-up sample text)")

    private class MapStore(private val open: Set<String>) : DetailOpenStore {
        private val m = HashMap<String, Boolean>()
        override fun get(key: String): Boolean? = m[key] ?: (key in open)
        override fun set(key: String, open: Boolean) { m[key] = open }
    }

    // ---------- a world with an Eevee that has eight evolutions (made-up names; FakeGame's data is shared, so it is put back) ----------

    private val saved = HashMap<Int, Species?>()
    private val extra = listOf(9101 to "Fluffflame", 9102 to "Fluffleaf", 9103 to "Fluffice", 9104 to "Fluffsun", 9105 to "Fluffmoon")

    @Before fun eightEvolutions() {
        @Suppress("UNCHECKED_CAST") val map = FakeGame.data.species as MutableMap<Int, Species>
        for (n in listOf(133) + extra.map { it.first }) saved[n] = map[n]
        extra.forEach { (n, name) -> map[n] = TestData.species(n, name, listOf(Type.NORMAL), prevo = 133) }
        map[133] = map.getValue(133).copy(evolutions = listOf(
            Evolution(134, EvoKind.ITEM, item = "water-stone"), Evolution(135, EvoKind.ITEM, item = "thunder-stone"),
            Evolution(9101, EvoKind.ITEM, item = "fire-stone"), Evolution(9102, EvoKind.ITEM, item = "leaf-stone"),
            Evolution(9103, EvoKind.ITEM, item = "ice-stone"), Evolution(9104, EvoKind.FRIENDSHIP, time = "day"),
            Evolution(9105, EvoKind.FRIENDSHIP, time = "night"), Evolution(700, EvoKind.FRIENDSHIP, moveType = Type.FAIRY, minFriendship = 160)))
    }

    @After fun putBack() {
        @Suppress("UNCHECKED_CAST") val map = FakeGame.data.species as MutableMap<Int, Species>
        saved.forEach { (n, sp) -> if (sp == null) map.remove(n) else map[n] = sp }
        saved.clear()
        DetailSections.storeOf = { ctx -> object : DetailOpenStore {
            override fun get(key: String): Boolean? = com.husarp.ankigo.ui.AppPrefs.detailOpen(ctx, key)
            override fun set(key: String, open: Boolean) = com.husarp.ankigo.ui.AppPrefs.setDetailOpen(ctx, key, open)
        } }
        DetailSections.dexLoader = { c, n, done -> com.husarp.ankigo.data.DexText.load(c, n, done) }
        ItemWindow.interceptShow = null
    }

    // ---------- the harness ----------

    private fun scrolls(v: View, out: MutableList<ScrollView> = ArrayList()): List<ScrollView> {
        if (v is ScrollView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) scrolls(v.getChildAt(i), out)
        return out
    }

    private fun views(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), out)
        return out
    }

    private fun texts(v: View) = views(v).filterIsInstance<TextView>()
    private fun strings(v: View) = texts(v).map { it.text.toString() }

    /** Opens [open]'s dialog, runs [check] on its scene (laid out at the window size) and shoots it at each scroll position of [slices]. */
    private fun dialog(name: String, slices: List<Float>, variants: List<Variant> = sizes, setup: () -> Unit = {}, check: (Context, View) -> Unit = { _, _ -> }, open: (Context) -> Unit) {
        slices.forEachIndexed { k, frac ->
            for (v in variants) for (theme in Theme.entries) {
                paparazzi.unsafeUpdateConfig(deviceConfig = v.device(theme))
                val ctx = Shots.files(paparazzi.context)
                FakeGame.install(ctx)
                setup() // the fake game was just made again: what a shot changes in it goes here
                var scene: View? = null
                val frame = object : FrameLayout(ctx) {
                    // scrolls right before drawing, when every size is final: the details' ScrollView is the one with most content
                    override fun dispatchDraw(canvas: android.graphics.Canvas) {
                        scene?.let { s -> scrolls(s).maxByOrNull { it.getChildAt(0)?.height ?: 0 }?.let { sc ->
                            sc.scrollTo(0, ((sc.getChildAt(0).height - sc.height).coerceAtLeast(0) * frac).toInt())
                        } }
                        super.dispatchDraw(canvas)
                    }
                }
                val d = DialogShots.capture { open(ctx) } ?: error("no dialog was shown")
                scene = DialogShots.scene(ctx, d)
                frame.addView(scene, FrameLayout.LayoutParams(-1, -1))
                paparazzi.snapshot(frame, "r18g-$name-${v.label}-${theme.name.lowercase()}-s$k", 1000)
                if (k == 0) check(ctx, scene)
            }
        }
    }

    /** Your Pikachu-like main (t1): Lv 23, Hasty, holds an Amulet Coin, 57 wins. */
    private fun pikachu(): Mon {
        val old = st.mon("t1")!!
        val m = Mon(old.uid, old.species, old.name, 23, 120, Stats(28, 12, 9, 20, 25, 18), Stats(0, 12, 0, 0, 0, 24),
            Nature.HASTY, 'M', false, "Static", mutableListOf("tackle", "thunderbolt", "quickattack", "thunderwave"), 1,
            friendship = 142, heldItem = "amulet-coin", caughtAt = 1_760_000_000_000L, defeats = 57)
        m.hp = (StatCalc.maxHp(gd, m) * 0.85).toInt()
        st.mons[st.mons.indexOf(old)] = m
        return m
    }

    // ---------- the whole window ----------

    @Test fun pikachuWholeWindow() {
        DetailSections.storeOf = { MapStore(setOf("stats", "moves", "training", "info")) }
        DetailSections.dexLoader = { _, _, done -> done(sample) }
        dialog("pikachu", listOf(0f, 0.34f, 0.67f, 1f), check = { ctx, scene ->
            val t = strings(scene)
            // 239B: the item, its Bag text, once (not a tag under the types, not a Training row any more)
            assertTrue("Holding" in t)
            assertEquals("the item shows once", 1, t.count { it == "Amulet Coin" })
            assertTrue("Main holds it: ×2 money" in t)
            assertFalse("no Held item row in Training", "Held item" in t)
            // 254A: the level is in the Stats title, in its band colour, and out of the line under the name
            val title = texts(scene).single { it.text.toString() == "Stats · Lv 23" }
            val sp = title.text as Spanned
            val lv = sp.getSpans(0, sp.length, ForegroundColorSpan::class.java).single { sp.getSpanStart(it) == "Stats · ".length }
            assertEquals(LevelTier.color(ctx, 23), lv.foregroundColor)
            assertTrue("#025 · Hasty" in t || t.any { it.endsWith("· Hasty") && !it.contains("Lv") })
            assertTrue(t.none { it.contains("Lv 23 ·") })
            // 273: Wins
            assertTrue("Wins" in t); assertFalse("Defeats" in t)
            // the heart is bare: a 48 dp ripple, no tile
            val heart = views(scene).single { it.contentDescription == "Add to favourites" || it.contentDescription == "Remove from favourites" }
            assertEquals(Ui.dp(ctx, 48), heart.width); assertEquals(Ui.dp(ctx, 48), heart.height)
            assertTrue("a ripple, no pill", heart.background is RippleDrawable)
        }) { ctx -> MonDetail.show(TeamPage(ctx, NoHost), gd, pikachu()) }
    }

    @Test fun pikachuDarkAndLightFolded() {
        // the top of the window with Stats folded: the level stays in the title, "Total" at the right
        DetailSections.storeOf = { MapStore(emptySet()) }
        dialog("pikachu-folded", listOf(0f), check = { _, scene ->
            val t = strings(scene)
            assertTrue("Stats · Lv 23" in t)
            assertTrue("Total ${StatCalc.stats(gd.sp(25), st.mon("t1")!!).total}" in t)
        }) { ctx -> MonDetail.show(TeamPage(ctx, NoHost), gd, pikachu()) }
    }

    @Test fun expShareHolderWithEightEvolutions() {
        DetailSections.storeOf = { MapStore(emptySet()) }
        dialog("eevee", listOf(0f), setup = { st.settings.xpShareUid = "t6" }, check = { ctx, scene ->
            val t = strings(scene)
            assertEquals("the Exp. Share shows once (no tag under the types)", 1, t.count { it == "Exp. Share" })
            assertTrue("It and your Main each get half the XP" in t)
            assertFalse("Nothing" in t)
            // the eight methods, under the names, in a grid of three per line
            val labels = listOf("Water Stone", "Thunder Stone", "Fire Stone", "Leaf Stone", "Ice Stone", "Friendship, day", "Friendship, night", "Friendship, Fairy-type move")
            val cells = labels.map { l -> texts(scene).single { it.text.toString() == l } }
            val tops = cells.map { top(it, scene) }.distinct().sorted()
            val lefts = cells.map { left(it, scene) + it.width / 2 }.sorted()
            assertTrue("three lines of targets, not eight: tops $tops", tops.size == 3)
            // each cell column is shared by up to three lines: at most three distinct centres (±3 px for the centring)
            assertTrue("three columns: $lefts", lefts.fold(ArrayList<Int>()) { acc, x -> if (acc.none { abs(it - x) <= Ui.dp(ctx, 12) }) acc.add(x); acc }.size == 3)
            // the arrow into the grid sits on the picture of Eevee
            assertArrowsOnThePictures(ctx, scene)
            // the whole evolution line is about 270 dp tall, not 560
            val evo = views(scene).filterIsInstance<ArrowCol>().first()
            assertTrue("grid height ${(evo.parent as View).height}", (evo.parent as View).height < Ui.dp(ctx, 320))
        }) { ctx -> MonDetail.show(TeamPage(ctx, NoHost), gd, st.mon("t6")!!) }
    }

    @Test fun holdsNothingAndDoesNotEvolve() {
        DetailSections.storeOf = { MapStore(emptySet()) }
        dialog("nothing", listOf(0f), setup = { st.bag["lucky-egg"] = 1 }, check = { ctx, scene ->
            val t = strings(scene)
            assertTrue("Nothing" in t && "Give item" in t)
            assertTrue("Evolution" in t && "Does not evolve" in t)
            val give = texts(scene).single { it.text.toString() == "Give item" }
            assertTrue(give.isClickable && give.height >= Ui.dp(ctx, 48))
            // "Give item" opens the picker of Options → "Give item…" and the details close
            val picker = DialogShots.capture { give.performClick() }
            assertNotNull("the picker opens", picker)
        }) { ctx -> MonDetail.show(TeamPage(ctx, NoHost), gd, st.mon("t4")!!) }
    }

    @Test fun nothingToGiveSaysSo() {
        // an empty bag of held items: the toast text of Options, and the details stay (no picker)
        paparazzi.unsafeUpdateConfig(deviceConfig = PHONE.device(Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        DetailSections.storeOf = { MapStore(emptySet()) }
        val d = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), gd, st.mon("t4")!!) } ?: error("no dialog")
        val scene = DialogShots.scene(ctx, d)
        val picker = DialogShots.capture { texts(scene).single { it.text.toString() == "Give item" }.performClick() }
        assertNull("no picker without a held item in the bag", picker)
        assertEquals("the toast of Options → Give item…", "No held items in the bag", Ui.lastToast)
    }

    @Test fun anItemLineOpensItsItemWindow() {
        DetailSections.storeOf = { MapStore(emptySet()) }
        paparazzi.unsafeUpdateConfig(deviceConfig = PHONE.device(Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        var shown: ItemWindow? = null
        ItemWindow.interceptShow = { shown = it }
        val d = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), gd, pikachu()) } ?: error("no dialog")
        val scene = DialogShots.scene(ctx, d)
        measureAndLayout(scene, ctx, 412, 869)
        val line = views(scene).single { it.isClickable && (it.contentDescription ?: "").startsWith("Amulet Coin") }
        assertTrue("the item line is ${line.height} px tall", line.height >= Ui.dp(ctx, 48))
        line.performClick()
        assertNotNull("its item window", shown)
    }

    @Test fun everyTappableTitleIsAtLeast48DpHigh() {
        DetailSections.storeOf = { MapStore(setOf("stats", "moves", "training", "info")) }
        DetailSections.dexLoader = { _, _, done -> done(sample) }
        paparazzi.unsafeUpdateConfig(deviceConfig = PHONE.device(Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val d = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), gd, pikachu()) } ?: error("no dialog")
        val scene = DialogShots.scene(ctx, d)
        measureAndLayout(scene, ctx, 412, 869)
        val rows = views(scene).filter { it.isClickable && it.tag is StatInfoText }
        // Atk, Def, SpA, SpD, Spe, HP, Total, HP and XP, Nature, IVs, EVs, Friendship, Holding, Evolution, Wins
        val said = rows.map { (it.tag as StatInfoText).title }
        listOf("HP (Hit points)", "Attack", "Defense", "Special Attack", "Special Defense", "Speed", "Total", "HP and XP", "Nature", "IV (Individual value)",
            "EV (Effort value)", "Friendship", "Holding", "Evolution", "Wins").forEach { assertTrue("$it opens an explanation; there are $said", it in said) }
        rows.forEach { assertTrue("${(it.tag as StatInfoText).title}: ${it.height} px tall", it.height >= Ui.dp(ctx, 48)) }
        assertTrue("whole rows, not the 32 dp label: ${rows.map { it.width }}", rows.all { it.width > Ui.dp(ctx, 90) })
    }

    // ---------- 242A: the titles sit on the middle of their rows ----------

    @Test fun titlesAreCentredInTheirRows() {
        DetailSections.storeOf = { MapStore(setOf("training", "info")) }
        DetailSections.dexLoader = { _, _, done -> done(sample) }
        paparazzi.unsafeUpdateConfig(deviceConfig = PHONE.device(Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val d = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), gd, pikachu()) } ?: error("no dialog")
        val scene = DialogShots.scene(ctx, d)
        measureAndLayout(scene, ctx, 412, 869)
        val off = ArrayList<String>()
        for (title in listOf("Nature", "IVs", "EVs", "Friendship", "Caught", "Ability")) {
            // the title of a fact row: a 70 dp column ("Friendship" is also an evolution method label)
            val t = texts(scene).first { it.text.toString() == title && it.layoutParams?.width == Ui.dp(ctx, 70) }
            val row = t.parent as ViewGroup
            val mid = t.top + t.height / 2
            val want = row.paddingTop + (row.height - row.paddingTop - row.paddingBottom) / 2
            if (abs(mid - want) > 2) off += "$title: label middle $mid px, row middle $want px (row ${row.height} px, pad ${row.paddingTop}/${row.paddingBottom}, min ${row.minimumHeight}, label ${t.top}..${t.bottom}, children ${(0 until row.childCount).map { row.getChildAt(it).let { c -> "${c.javaClass.simpleName} ${c.top}..${c.bottom}" } }}, density ${ctx.resources.displayMetrics.density})"
        }
        assertTrue("titles not centred:\n" + off.joinToString("\n"), off.isEmpty())
    }

    // ---------- 260A: the arrow always on the pictures' centre ----------

    private fun top(v: View, root: View): Int { var y = 0; var c: View = v; while (c !== root) { y += c.top; c = c.parent as View }; return y }
    private fun left(v: View, root: View): Int { var x = 0; var c: View = v; while (c !== root) { x += c.left; c = c.parent as View }; return x }

    private fun measureAndLayout(v: View, ctx: Context, wDp: Int, hDp: Int = 0) {
        val d = ctx.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY),
            if (hDp > 0) View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY) else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    /** Every arrow's centre line is within 1 px of the centre of a picture (the one it points at, or comes from for a grid). */
    private fun assertArrowsOnThePictures(ctx: Context, root: View) {
        val images = views(root).filterIsInstance<android.widget.ImageView>().filter { it.width in Ui.dp(ctx, 28)..Ui.dp(ctx, 48) && it.contentDescription == null }
        val arrows = views(root).filterIsInstance<DetailSections.ArrowView>().filter { it.width > it.height }
        assertTrue(arrows.isNotEmpty())
        arrows.forEach { a ->
            val y = top(a, root) + a.height / 2
            assertTrue("arrow at $y: picture centres ${images.map { top(it, root) + it.height / 2 }}", images.any { abs(top(it, root) + it.height / 2 - y) <= 1 })
        }
    }

    private fun lineData(vararg methods: Pair<Int, Evolution>, extra: List<Species> = emptyList()): GameData {
        val root = TestData.species(1, "Birdie", evolutions = methods.map { it.second })
        return TestData.data(listOf(root) + methods.map { (n, _) -> TestData.species(n, "Ratty$n", prevo = 1) } + extra)
    }

    private fun tree(c: Context, data: GameData, num: Int): View {
        FakeGame.install(c)
        return android.widget.LinearLayout(c).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val p = Ui.dp(c, 16); setPadding(p, p, p, p)
            setBackgroundColor(Ui.c(c, com.husarp.ankigo.R.color.ag_mantle))
            addView(DetailSections.evolution(c, data, data.species.getValue(num), setOf(1, 2, 3, 4, 5), false, null))
        }
    }

    @Test fun theArrowStaysOnThePicturesCentreWhateverItsLabel() {
        paparazzi.unsafeUpdateConfig(deviceConfig = PHONE.device(Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        // a one-line, a two-line and a three-line method; one arrow each, then three branches stacked with all three
        for (method in listOf(Evolution(2, EvoKind.FRIENDSHIP), Evolution(2, EvoKind.ITEM, item = "thunder-stone"),
            Evolution(2, EvoKind.LEVEL, level = 40, time = "night", condition = "holding Razor Fang at night"))) {
            val v = tree(c, lineData(2 to method), 1)
            measureAndLayout(v, c, 412)
            assertArrowsOnThePictures(c, v)
        }
        val stacked = tree(c, lineData(2 to Evolution(2, EvoKind.FRIENDSHIP), 3 to Evolution(3, EvoKind.ITEM, item = "thunder-stone"),
            4 to Evolution(4, EvoKind.LEVEL, level = 40, time = "night", condition = "holding Razor Fang at night")), 1)
        measureAndLayout(stacked, c, 412)
        assertArrowsOnThePictures(c, stacked)
    }

    @Test fun theArrowStaysOnTheCentreInAStackedBranchWithItsOwnBranches() {
        paparazzi.unsafeUpdateConfig(deviceConfig = PHONE.device(Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        // Birdie -> Ratty2 (-> Ratty4, Ratty5) and Birdie -> Ratty3: the arrow into Ratty2 stands on Ratty2's picture, which is centred among its own two branches
        val data = TestData.data(listOf(
            TestData.species(1, "Birdie", evolutions = listOf(Evolution(2, EvoKind.ITEM, item = "water-stone"), Evolution(3, EvoKind.LEVEL, level = 30))),
            TestData.species(2, "Ratty2", prevo = 1, evolutions = listOf(Evolution(4, EvoKind.LEVEL, level = 40), Evolution(5, EvoKind.ITEM, item = "thunder-stone"))),
            TestData.species(3, "Ratty3", prevo = 1), TestData.species(4, "Ratty4", prevo = 2), TestData.species(5, "Ratty5", prevo = 2)))
        val v = tree(c, data, 1)
        measureAndLayout(v, c, 412)
        assertArrowsOnThePictures(c, v)
    }

    @Test fun threeToAGridOfThreePerLineAndNoOverlap() {
        paparazzi.unsafeUpdateConfig(deviceConfig = PHONE.device(Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        val data = lineData(2 to Evolution(2, EvoKind.ITEM, item = "water-stone"), 3 to Evolution(3, EvoKind.ITEM, item = "thunder-stone"),
            4 to Evolution(4, EvoKind.FRIENDSHIP, time = "night"), 5 to Evolution(5, EvoKind.ITEM, item = "fire-stone"))
        val v = tree(c, data, 1)
        measureAndLayout(v, c, 412)
        val names = listOf("Ratty2", "Ratty3", "Ratty4", "Ratty5").map { n -> texts(v).single { it.text.toString() == n } }
        assertEquals("three on the first line, one on the second", listOf(3, 1), names.groupBy { top(it, v) }.toSortedMap().values.map { it.size })
        // every method text hangs under its own name, never over the next line's name
        val methods = listOf("Water Stone", "Thunder Stone", "Friendship, night", "Fire Stone").map { m -> texts(v).single { it.text.toString() == m } }
        names.zip(methods).forEach { (n, m) -> assertTrue("${n.text}: method under the name", top(m, v) >= top(n, v) + n.height) }
        assertArrowsOnThePictures(c, v)
    }

    // ---------- the Pokédex entry ----------

    @Test fun dexEntry() {
        DetailSections.storeOf = { MapStore(setOf("stats", "info")) }
        DetailSections.dexLoader = { _, _, done -> done(sample) }
        dialog("dex", listOf(0f, 1f), check = { _, scene ->
            val t = strings(scene)
            assertTrue("the evolution is in the card under the types" , "Evolution" in t)
            assertFalse("no Holding for a species", "Holding" in t)
            assertTrue("Abilities" in t)
        }) { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(25)) }
    }

    @Test fun dexEntryOfAnEeveeWithEightEvolutions() {
        DetailSections.storeOf = { MapStore(emptySet()) }
        dialog("dex-eevee", listOf(0f)) { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(133)) }
    }

    @Test fun dexEntryOfASpeciesThatDoesNotEvolve() {
        DetailSections.storeOf = { MapStore(emptySet()) }
        dialog("dex-single", listOf(0f), check = { _, scene -> assertTrue("Does not evolve" in strings(scene)) }) { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(150)) }
    }

    @Test fun theLevelTitleHasItsPlainText() {
        assertEquals("Stats · Lv 23", DetailFormat.levelTitle("Stats", 23))
    }
}
