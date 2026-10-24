package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.data.DexEntry
import com.husarp.ankigo.ui.DetailFormat
import com.husarp.ankigo.ui.DetailOpenStore
import com.husarp.ankigo.ui.DetailSections
import com.husarp.ankigo.ui.DexDetail
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.TeamPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * TICKET R10-C: the Pokédex entry's Moves (the species' own learnset with learn levels), "My Pokémon" instead of "Details",
 * and the generation at the bottom-right of the Info section. PNGs: r10details-<what>-<size>-<theme>.
 */
class R10DetailsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val F340 = Variant("float", 340, 480)
    private val F320 = Variant("w320", 320, 480)
    private val gd get() = GameRepo.data!!
    private val st get() = GameRepo.state!!

    private class MapStore(private val all: Boolean) : DetailOpenStore {
        private val m = HashMap<String, Boolean>()
        override fun get(key: String): Boolean? = m[key] ?: all
        override fun set(key: String, open: Boolean) { m[key] = open }
    }

    private val sample = DexEntry("Spark Pokémon", "It stores power in its cheeks and lets it out all at once when it is scared. (made-up sample text)")

    @After fun reset() {
        DetailSections.storeOf = { ctx -> object : DetailOpenStore {
            override fun get(key: String): Boolean? = com.husarp.ankigo.ui.AppPrefs.detailOpen(ctx, key)
            override fun set(key: String, open: Boolean) = com.husarp.ankigo.ui.AppPrefs.setDetailOpen(ctx, key, open)
        } }
        DetailSections.dexLoader = { c, n, done -> com.husarp.ankigo.data.DexText.load(c, n, done) }
        DexDetail.openMon = { a, data, m -> MonDetail.show(a, data, m) }
    }

    private fun allOpen() {
        DetailSections.storeOf = { MapStore(true) }
        DetailSections.dexLoader = { _, _, done -> done(sample) }
    }

    private fun scrolls(v: View, out: MutableList<ScrollView> = ArrayList()): List<ScrollView> {
        if (v is ScrollView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) scrolls(v.getChildAt(i), out)
        return out
    }

    private fun texts(v: View, out: MutableList<TextView> = ArrayList()): List<TextView> {
        if (v is TextView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun find(v: View, text: String): TextView? = texts(v).firstOrNull { it.text.toString() == text }

    /**
     * The Info section's generation text must sit inside its parent, not be cut, and right-aligned at the bottom of the section
     * (R18-G, PLAN 243: the evolution line moved out of Info into the card under the bars, so the generation line stands alone).
     */
    private fun checkGeneration(root: View, sp: Species) {
        val want = DetailFormat.generation(sp.gen)
        val g = texts(root).single { it.text.toString() == want }
        val p = g.parent as ViewGroup
        assertTrue("gen inside parent: ${g.left},${g.top},${g.right},${g.bottom} in ${p.width}x${p.height}",
            g.left >= 0 && g.top >= 0 && g.right <= p.width && g.bottom <= p.height)
        assertTrue("gen has a size", g.width > 0 && g.height > 0)
        val l = g.layout
        assertTrue("gen text not cut", l != null && l.getEllipsisCount(l.lineCount - 1) == 0)
        assertEquals("gen right-aligned", p.width - p.paddingRight, g.right)
        assertEquals("gen is the last line of Info", p.childCount - 1, p.indexOfChild(g))
    }

    private fun shoot(name: String, variants: List<Variant>, scale: Float, frac: Float, after: (Context, View) -> Unit = { _, _ -> }, open: (Context) -> Unit) {
        for (v in variants) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = v.device(theme).copy(fontScale = scale))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            var scene: View? = null
            val frame = object : FrameLayout(ctx) {
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
            val label = if (scale == 1f) v.label else "${v.label}-x${scale.toString().replace('.', '_')}"
            paparazzi.snapshot(frame, "r10details-$name-$label-${theme.name.lowercase()}", 1000)
            after(ctx, scene)
        }
    }

    private fun addBestCopy() {
        st.mons += TestData.mon(gd, 25, 40, uid = "x1")   // t1 is level 26: x1 is the highest
        st.mons += TestData.mon(gd, 25, 12, uid = "x2")
    }

    // ---------- B4: moves + My Pokémon ----------

    @Test fun dexOwnedMoves() {
        allOpen()
        for (f in listOf(0f, 0.45f, 1f)) {
            shoot("dex-owned-s${(f * 100).toInt()}", listOf(Variants.PHONE, F340), 1f, f, after = { _, scene ->
                // level labels: Start (level 1 twice) and Lv 26; the made-up move rows are the same rows as the details
                val t = texts(scene).map { it.text.toString() }
                assertEquals(2, t.count { it == "Start" }); assertTrue("Lv 26" in t)
                assertTrue("3 moves" in t)
                assertNotNull(find(scene, "My Pokémon")); assertNotNull(find(scene, "Options")); assertNull(find(scene, "Details"))
            }) { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(25)) }
        }
        shoot("dex-owned-big", listOf(F320), 2.0f, 0.45f) { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(25)) }
    }

    @Test fun dexSeenOnlyHasNoMyPokemon() {
        allOpen()
        shoot("dex-seen", listOf(Variants.PHONE, F340), 1f, 0f, after = { _, scene ->
            assertNull(find(scene, "My Pokémon")); assertNull(find(scene, "Options")); assertNull(find(scene, "Details"))
        }) { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(150)) }
    }

    @Test fun learnLevelsEvolutionAndStart() {
        // level 0 = "Evo" row, level 1 = "Start", others "Lv N": Leafbud (2) has all three
        allOpen()
        shoot("dex-levels", listOf(F340), 1f, 0f, after = { _, scene ->
            val t = texts(scene).map { it.text.toString() }
            assertTrue("Evo" in t); assertTrue("Start" in t); assertTrue("Lv 20" in t)
        }) { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(2)) }
    }

    @Test fun myPokemonOpensTheHighestLevelCopy() {
        for (theme in Theme.entries.take(1)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variants.PHONE.device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            allOpen()
            addBestCopy()
            var got: String? = null
            DexDetail.openMon = { _, _, m: Mon -> got = m.uid }
            val d = DialogShots.capture { DexDetail.show(DexPage(ctx, NoHost), gd.sp(25)) } ?: error("no dialog")
            val scene = DialogShots.scene(ctx, d)
            // no picker: the click opens the copy at once
            val picker = DialogShots.capture { find(scene, "My Pokémon")!!.performClick() }
            assertNull("no copy picker", picker)
            assertEquals("x1", got)
            // Options with several copies still asks which one
            val opt = DialogShots.capture { find(scene, "Options")!!.performClick() }
            assertNotNull("Options keeps the picker", opt)
        }
    }

    // ---------- B18: generation ----------

    @Test fun infoGenerationStandaloneAndEvolving() {
        allOpen()
        // made-up generations so the region name shows: 3 on the standalone one, 8 on the evolving one
        for ((variants, scale) in listOf(listOf(Variants.PHONE, F340) to 1f, listOf(F320) to 2.0f)) {
            val sx = if (scale == 1f) "" else "-big"
            shoot("info-standalone$sx", variants, scale, 1f, after = { _, scene -> checkGeneration(scene, gd.sp(92)) }) { ctx ->
                setGen(92, 3); MonDetail.show(TeamPage(ctx, NoHost), gd, st.mon("t4")!!)
            }
            shoot("info-evolving$sx", variants, scale, 1f, after = { _, scene -> checkGeneration(scene, gd.sp(25)) }) { ctx ->
                setGen(25, 8); MonDetail.show(TeamPage(ctx, NoHost), gd, st.mon("t1")!!)
            }
            shoot("info-dex-standalone$sx", variants, scale, 1f, after = { _, scene -> checkGeneration(scene, gd.sp(150)) }) { ctx ->
                setGen(150, 9); DexDetail.show(DexPage(ctx, NoHost), gd.sp(150))
            }
            shoot("info-dex-branch$sx", variants, scale, 1f, after = { _, scene -> checkGeneration(scene, gd.sp(133)) }) { ctx ->
                DexDetail.show(DexPage(ctx, NoHost), gd.sp(133))
            }
        }
    }

    private fun setGen(num: Int, gen: Int) {
        @Suppress("UNCHECKED_CAST") val map = gd.species as MutableMap<Int, Species>
        map[num] = gd.sp(num).copy(gen = gen)
    }
}
