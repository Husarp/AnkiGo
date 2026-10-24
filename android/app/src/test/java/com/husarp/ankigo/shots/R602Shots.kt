package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.data.DexEntry
import com.husarp.ankigo.ui.BattleDetails
import com.husarp.ankigo.ui.DetailOpenStore
import com.husarp.ankigo.ui.DetailSections
import com.husarp.ankigo.ui.DexDetail
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.TeamPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * R6-02 (items 1, 2, 4): the Total row lines up with the stat numbers, the Info row keeps "Caught" on its own line, and the wild
 * Pokémon's window has the sections of your own Pokémon's details. PNGs: r602-own, r602-dex, r602-wild (light + dark; PHONE tall,
 * a 360 dp compact tall window and the short SPLIT window).
 */
class R602Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val C360 = Variant("c360", 360, 700)
    private val variants = listOf(Variants.PHONE, C360, Variants.SPLIT)
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private class MapStore : DetailOpenStore {
        override fun get(key: String): Boolean = true
        override fun set(key: String, open: Boolean) {}
    }

    @Before fun open() {
        DetailSections.storeOf = { MapStore() }
        DetailSections.dexLoader = { _, _, done -> done(DexEntry("Spark Pokémon", "Made-up sample text for the Pokédex part of the Info section.")) }
    }

    @After fun reset() {
        DetailSections.storeOf = { ctx -> object : DetailOpenStore {
            override fun get(key: String): Boolean? = com.husarp.ankigo.ui.AppPrefs.detailOpen(ctx, key)
            override fun set(key: String, open: Boolean) = com.husarp.ankigo.ui.AppPrefs.setDetailOpen(ctx, key, open)
        } }
        DetailSections.dexLoader = { c, n, done -> com.husarp.ankigo.data.DexText.load(c, n, done) }
        BattleShotKit.reset()
    }

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v is TextView) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun scrolls(v: View, out: MutableList<ScrollView> = ArrayList()): List<ScrollView> {
        if (v is ScrollView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) scrolls(v.getChildAt(i), out)
        return out
    }

    /** One shot per scroll position in [slices] (0 = top, 1 = bottom of the details), named "$name-s$k". */
    private fun dialog(name: String, vs: List<Variant> = variants, slices: List<Float> = listOf(0f, 0.5f, 1f), check: (List<String>) -> Unit = {}, open: (Context) -> Unit) =
        slices.forEachIndexed { k, frac -> dialog1("$name-s$k", vs, frac, check, open) }

    private fun dialog1(name: String, vs: List<Variant>, frac: Float, check: (List<String>) -> Unit, open: (Context) -> Unit) {
        paparazzi.shootPage(name, vs) { ctx ->
            val d = DialogShots.capture { open(ctx) } ?: error("no dialog was shown")
            val scene = DialogShots.scene(ctx, d)
            check(texts(scene))
            object : FrameLayout(ctx) {
                override fun dispatchDraw(canvas: android.graphics.Canvas) {
                    scrolls(scene).maxByOrNull { it.getChildAt(0)?.height ?: 0 }?.let { sc ->
                        sc.scrollTo(0, ((sc.getChildAt(0).height - sc.height).coerceAtLeast(0) * frac).toInt())
                    }
                    super.dispatchDraw(canvas)
                }
            }.apply { addView(scene, FrameLayout.LayoutParams(-1, -1)) }
        }
    }

    private fun rich(): Mon {
        val old = st.mon("t1")!!
        val m = Mon(old.uid, old.species, old.name, 100, old.xp, Stats(28, 12, 25, 31, 20, 26), Stats(0, 0, 0, 52, 0, 44),
            Nature.TIMID, 'M', false, "static", mutableListOf("tackle", "thunderbolt", "quickattack", "thunderwave"), old.hp,
            friendship = 182, heldItem = "lucky-egg", caughtAt = 1_760_000_000_000L, defeats = 21_414)
        st.mons[st.mons.indexOf(old)] = m
        return m
    }

    private fun withInfo(num: Int) {
        @Suppress("UNCHECKED_CAST") val map = gd.species as MutableMap<Int, com.husarp.ankigo.game.Species>
        map[num] = gd.sp(num).copy(heightM = 0.7, weightKg = 9.6, abilities = listOf("Static", "Spark Eye"), hiddenAbility = "Quick Wings")
    }

    /** Titles of the sections in screen order (the bar texts). */
    private fun order(t: List<String>) = listOf("Stats", "Moves", "Matchups", "Training", "Info").filter { title -> t.any { it == title || it.startsWith("$title · ") } }

    @Test fun own() = dialog("r602-own", check = { t ->
        assertEquals(listOf("Stats", "Moves", "Matchups", "Training", "Info"), order(t))
        assertTrue("Caught has its own line", t.contains("Caught") && t.contains("Wins") && t.contains("Height") && t.contains("Weight"))
    }) { ctx -> withInfo(25); MonDetail.show(TeamPage(ctx, NoHost), gd, rich()) }

    @Test fun dex() = dialog("r602-dex", listOf(Variants.PHONE, Variants.SPLIT)) { ctx -> withInfo(25); DexDetail.show(DexPage(ctx, NoHost), gd.sp(25)) }

    @Test fun wild() = dialog("r602-wild", check = { t ->
        assertEquals("same order as your own details, without Training", listOf("Stats", "Moves", "Matchups", "Info"), order(t))
        assertFalse(t.any { it == "Nature" || it == "IVs" || it == "EVs" || it == "Friendship" || it == "Held item" || it == "Ability" || it == "Defeats" || it == "Wins" })
        assertTrue(t.contains("Tier") && t.contains("Pokédex") && t.contains("Total"))
        // R18-G (PLAN 254 / 243): the level is in the Stats title, and its evolution is in the card under the types (no Holding for a wild one)
        assertTrue("Stats title carries the level", t.any { it.startsWith("Stats · Lv ") })
        assertTrue(t.none { it.contains("· Lv ") && it.startsWith("#") })
        assertTrue(t.contains("Evolution") && !t.contains("Holding"))
        assertEquals("every move is still unknown", 2, t.count { it == "?" })
    }) { ctx -> BattleShotKit.bound(ctx); withInfo(GameRepo.state!!.battle!!.wild.species); assertTrue(BattleDetails.showWild(ctx)) }

    /** The Total number ends where the stat numbers end, and the label never touches it (also for a 4-digit total). */
    @Test fun totalRowAlignsWithTheStatNumbers() {
        FakeGame.install(paparazzi.context)
        val ctx = paparazzi.context
        val w = com.husarp.ankigo.ui.Ui.dp(ctx, 330)
        fun right(v: View, root: View): Int { var x = v.right; var p = v.parent; while (p is View && p !== root) { x += p.left; p = p.parent }; return x }
        fun find(v: View, t: String): TextView? =
            if (v is TextView && v.text.toString() == t) v else (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it), t) } }
        for (total in listOf(95, 950, 1234)) {
            val col = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL }
            col.addView(com.husarp.ankigo.ui.DetailParts.statRow(ctx, com.husarp.ankigo.game.Stat.HP, 208, 0.5f, 0, 35, false))
            col.addView(com.husarp.ankigo.ui.DetailParts.totalRow(ctx, total, 320))
            col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            col.layout(0, 0, col.measuredWidth, col.measuredHeight)
            val stat = find(col, "208")!!
            val num = find(col, total.toString())!!
            val label = find(col, "Total")!!
            assertEquals("number of $total ends with the stat numbers", right(stat, col), right(num, col))
            assertTrue("label and $total do not touch", right(label, col) + 2 <= right(num, col) - num.paint.measureText(total.toString()).toInt())
        }
    }
}
