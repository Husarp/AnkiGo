package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.AbilityText
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.data.DexEntry
import com.husarp.ankigo.ui.DetailOpenStore
import com.husarp.ankigo.ui.DetailSections
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.TeamPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R17C-9a: the Info section of your Pokémon's details with the ability under its name: "Static", then what it does in AnkiGo, then the other abilities
 * of its kind on their own line. PNGs: r17c9-ability-<size>-<theme> (light + dark; phone and a float-sized window).
 */
class R17C9AbilityShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val FLOAT = Variant("float", 340, 480)
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!
    private val sample = DexEntry("Spark Pokémon", "It stores power in its cheeks and lets it out all at once when it is scared. (made-up sample text)")

    @After fun reset() {
        DetailSections.storeOf = { ctx -> object : DetailOpenStore {
            override fun get(key: String): Boolean? = com.husarp.ankigo.ui.AppPrefs.detailOpen(ctx, key)
            override fun set(key: String, open: Boolean) = com.husarp.ankigo.ui.AppPrefs.setDetailOpen(ctx, key, open)
        } }
        DetailSections.dexLoader = { c, n, done -> com.husarp.ankigo.data.DexText.load(c, n, done) }
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

    /** Your Pikachu-like Pokémon with the ability Static, on a species whose other ability is Spark Eye (made-up, like the sample text). */
    private fun staticMon(): Mon {
        val old = st.mon("t1")!!
        @Suppress("UNCHECKED_CAST") val map = gd.species as MutableMap<Int, com.husarp.ankigo.game.Species>
        map[25] = gd.sp(25).copy(heightM = 0.7, weightKg = 9.6, abilities = listOf("Static", "Spark Eye"), hiddenAbility = "Quick Wings")
        val m = Mon(old.uid, old.species, old.name, old.level, old.xp, Stats(28, 12, 25, 31, 20, 26), Stats(0, 0, 0, 52, 0, 44),
            Nature.TIMID, 'M', false, "Static", mutableListOf("tackle", "thunderbolt", "quickattack", "thunderwave"), old.hp,
            friendship = 182, heldItem = "lucky-egg", caughtAt = 1_760_000_000_000L, defeats = 214)
        st.mons[st.mons.indexOf(old)] = m
        return m
    }

    @Test fun abilityLine() {
        DetailSections.storeOf = { object : DetailOpenStore { override fun get(key: String) = key == "info"; override fun set(key: String, open: Boolean) {} } }
        DetailSections.dexLoader = { _, _, done -> done(sample) }
        var checked = false
        paparazzi.shootPage("r17c9-ability", listOf(Variants.PHONE, FLOAT)) { ctx: Context ->
            val d = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), gd, staticMon()) } ?: error("no dialog was shown")
            val scene = DialogShots.scene(ctx, d)
            // the line under the ability: its sentence, then the others of its kind on their own line
            val sub = texts(scene).firstOrNull { it.text.toString().startsWith(AbilityText.of("Static")!!) }
            assertNotNull("the ability's sentence is shown", sub)
            assertEquals(AbilityText.of("Static") + "\nOthers of its kind: Spark Eye · Quick Wings (hidden)", sub!!.text.toString())
            assertTrue("the ability's name is above it", texts(scene).any { it.text.toString() == "Static" })
            checked = true
            object : FrameLayout(ctx) {
                override fun dispatchDraw(canvas: android.graphics.Canvas) {
                    // the Info section is the last one: the end of the scroll shows it
                    scrolls(scene).maxByOrNull { it.getChildAt(0)?.height ?: 0 }?.let { sc -> sc.scrollTo(0, (sc.getChildAt(0).height - sc.height).coerceAtLeast(0)) }
                    super.dispatchDraw(canvas)
                }
            }.apply { addView(scene, FrameLayout.LayoutParams(-1, -1)) }
        }
        assertTrue(checked)
    }
}
