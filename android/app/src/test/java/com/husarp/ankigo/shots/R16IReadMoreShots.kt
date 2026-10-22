package com.husarp.ankigo.shots

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.data.DexEntry
import com.husarp.ankigo.ui.DetailOpenStore
import com.husarp.ankigo.ui.DetailSections
import com.husarp.ankigo.ui.DexDetail
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.ReadMore
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * TICKET R16-2-I (PLAN 183A + 183S1, X5): "Read more on Bulbapedia" under the Pokédex text in the Info section of both details
 * windows, and "Options" on the own Pokémon's window. PNGs: r16i-<own|dex>-<size>-<theme>.
 */
class R16IReadMoreShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val F340 = Variant("float", 340, 480)
    private val gd get() = GameRepo.data!!
    private val st get() = GameRepo.state!!
    private val link = "Read more on Bulbapedia"

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
        ReadMore.starter = { c, i -> c.startActivity(i) }
    }

    private fun infoState(open: Boolean) {
        DetailSections.storeOf = { MapStore(open) }
        DetailSections.dexLoader = { _, _, done -> done(sample) }
    }

    private fun texts(v: View, out: MutableList<TextView> = ArrayList()): List<TextView> {
        if (v is TextView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun scrolls(v: View, out: MutableList<ScrollView> = ArrayList()): List<ScrollView> {
        if (v is ScrollView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) scrolls(v.getChildAt(i), out)
        return out
    }

    private fun find(v: View, text: String): TextView? = texts(v).firstOrNull { it.text.toString() == text }

    /** Top of [v] inside [sc]'s content. */
    private fun topIn(sc: ScrollView, v: View): Int {
        var y = 0; var x: View = v
        while (x !== sc.getChildAt(0)) { y += x.top; x = x.parent as View }
        return y
    }

    private fun shoot(name: String, open: (Context) -> Unit, after: (Context, View) -> Unit) {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = F340.device(theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            var scene: View? = null
            val frame = object : FrameLayout(ctx) {
                override fun dispatchDraw(canvas: android.graphics.Canvas) {
                    scene?.let { s -> find(s, link)?.let { l ->
                        scrolls(s).firstOrNull()?.let { sc -> sc.scrollTo(0, (topIn(sc, l) - sc.height / 3).coerceAtLeast(0)) }
                    } }
                    super.dispatchDraw(canvas)
                }
            }
            val d = DialogShots.capture { open(ctx) } ?: error("no dialog was shown")
            scene = DialogShots.scene(ctx, d)
            frame.addView(scene, FrameLayout.LayoutParams(-1, -1))
            paparazzi.snapshot(frame, "r16i-$name-${F340.label}-${theme.name.lowercase()}", 1000)
            after(ctx, scene)
        }
    }

    private fun checkLink(ctx: Context, scene: View, speciesNum: Int, pageName: String) {
        val l = find(scene, link) ?: error("no link")
        val dens = ctx.resources.displayMetrics.density
        assertTrue("48 dp touch height (${l.height})", l.height >= (48 * dens).toInt())
        assertEquals("Read more about $pageName on Bulbapedia, opens the browser", l.contentDescription.toString())
        assertNotNull("open sign", l.compoundDrawablesRelative[2])
        // directly under the Pokédex text
        val p = l.parent as ViewGroup
        val before = p.getChildAt(p.indexOfChild(l) - 1) as TextView
        assertEquals(sample.text, before.text.toString())
        var got: Intent? = null
        ReadMore.starter = { _, i -> got = i }
        l.performClick()
        assertEquals(Intent.ACTION_VIEW, got!!.action)
        assertEquals(ReadMore.url(pageName), got!!.dataString)
        // no browser: a toast, no crash
        Ui.lastToast = null
        ReadMore.starter = { _, _ -> throw ActivityNotFoundException() }
        l.performClick()
        assertEquals("No browser to open the page", Ui.lastToast)
    }

    @Test fun ownPokemonDetails() {
        infoState(true)
        shoot("own", { ctx -> MonDetail.show(TeamPage(ctx, NoHost), gd, st.mon("t1")!!) }) { ctx, scene ->
            val mon = st.mon("t1")!! // after this shot's FakeGame.install
            checkLink(ctx, scene, mon.species, gd.sp(mon.species).name)
            assertNotNull(find(scene, "Options")); assertNull(find(scene, "More options"))
        }
    }

    @Test fun pokedexEntry() {
        infoState(true)
        shoot("dex", { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(25)) }) { ctx, scene ->
            checkLink(ctx, scene, 25, gd.sp(25).name)
            assertNotNull(find(scene, "Options"))
        }
    }

    @Test fun hiddenWhileInfoIsFolded() {
        infoState(false)
        DetailSections.dexLoader = { _, _, done -> done(sample) }
        shoot("folded", { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(25)) }) { _, scene -> assertNull(find(scene, link)) }
    }
}
