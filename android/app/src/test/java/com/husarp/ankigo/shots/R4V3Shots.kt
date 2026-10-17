package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.data.DexEntry
import com.husarp.ankigo.ui.BattleDetails
import com.husarp.ankigo.ui.DetailOpenStore
import com.husarp.ankigo.ui.DetailSection
import com.husarp.ankigo.ui.DetailSections
import com.husarp.ankigo.ui.DexDetail
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.TeamPage
import org.junit.After
import org.junit.Rule
import org.junit.Test

/* R4-V3 (PLAN 60, pick 4B): the details as sections that open on tap. Owned Pokémon, species, battle window; all open / all closed. */
class R4V3Shots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val FLOAT = Variant("float", 340, 480)
    private val variants = listOf(Variants.PHONE, Variants.SPLIT, FLOAT)
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    private class MapStore(private val all: Boolean?) : DetailOpenStore {
        private val m = HashMap<String, Boolean>()
        override fun get(key: String): Boolean? = m[key] ?: all
        override fun set(key: String, open: Boolean) { m[key] = open }
    }

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

    private fun dialog(name: String, slices: List<Float> = listOf(0f, 0.5f, 1f), vs: List<Variant> = variants, open: (Context) -> Unit) {
        slices.forEachIndexed { k, frac ->
            paparazzi.shootPage("$name-s$k", vs) { ctx ->
                val d = DialogShots.capture { open(ctx) } ?: error("no dialog was shown")
                val scene = DialogShots.scene(ctx, d)
                object : FrameLayout(ctx) {
                    override fun dispatchDraw(canvas: android.graphics.Canvas) {
                        scrolls(scene).maxByOrNull { it.getChildAt(0)?.height ?: 0 }?.let { sc ->
                            val range = (sc.getChildAt(0).height - sc.height).coerceAtLeast(0)
                            sc.scrollTo(0, (range * frac).toInt())
                        }
                        super.dispatchDraw(canvas)
                    }
                }.apply { addView(scene, FrameLayout.LayoutParams(-1, -1)) }
            }
        }
    }

    private fun rich(): Mon {
        val old = st.mon("t1")!!
        val m = Mon(old.uid, old.species, old.name, old.level, old.xp, Stats(28, 12, 25, 31, 20, 26), Stats(0, 0, 0, 52, 0, 44),
            Nature.TIMID, 'M', false, "static", mutableListOf("tackle", "thunderbolt", "quickattack", "thunderwave"), old.hp,
            friendship = 182, heldItem = "lucky-egg", caughtAt = 1_760_000_000_000L, defeats = 214)
        st.mons[st.mons.indexOf(old)] = m
        return m
    }

    /** Made-up size / hidden ability data on the made-up species (restored by the next fake install: it builds its own data). */
    private fun withInfo(num: Int) {
        @Suppress("UNCHECKED_CAST") val map = gd.species as MutableMap<Int, com.husarp.ankigo.game.Species>
        map[num] = gd.sp(num).copy(heightM = 0.7, weightKg = 9.6, abilities = listOf("Static", "Spark Eye"), hiddenAbility = "Quick Wings")
    }

    private val sample = DexEntry("Spark Pokémon", "It stores power in its cheeks and lets it out all at once when it is scared. (made-up sample text)")

    @Test fun ownedAllOpen() {
        DetailSections.storeOf = { MapStore(true) }
        DetailSections.dexLoader = { _, _, done -> done(sample) }
        dialog("r4v3-owned-open") { ctx -> withInfo(25); MonDetail.show(TeamPage(ctx, NoHost), gd, rich()) }
    }

    @Test fun ownedAllClosed() {
        DetailSections.storeOf = { MapStore(false) }
        dialog("r4v3-owned-closed", listOf(0f)) { ctx -> MonDetail.show(TeamPage(ctx, NoHost), gd, rich()) }
    }

    @Test fun ownedDefaultsAndMarks() {
        DetailSections.storeOf = { MapStore(null) }
        dialog("r4v3-owned-default", listOf(0f, 1f)) { ctx ->
            val m = rich(); st.team.remove(m.uid); st.team.add(m.uid); st.settings.xpShareUid = m.uid
            MonDetail.show(TeamPage(ctx, NoHost), gd, m)
        }
    }

    @Test fun infoNotDownloadedAndLoading() {
        DetailSections.storeOf = { object : DetailOpenStore { override fun get(key: String) = key == "info"; override fun set(key: String, open: Boolean) {} } }
        DetailSections.dexLoader = { _, _, done -> done(null) }
        dialog("r4v3-info-offline", listOf(1f), listOf(Variants.PHONE, FLOAT)) { ctx -> withInfo(25); MonDetail.show(TeamPage(ctx, NoHost), gd, rich()) }
        DetailSections.dexLoader = { _, _, _ -> }
        dialog("r4v3-info-loading", listOf(1f), listOf(Variants.PHONE)) { ctx -> withInfo(25); MonDetail.show(TeamPage(ctx, NoHost), gd, rich()) }
    }

    @Test fun branchedLine() {
        DetailSections.storeOf = { object : DetailOpenStore { override fun get(key: String) = key == "info" || key == "matchups"; override fun set(key: String, open: Boolean) {} } }
        DetailSections.dexLoader = { _, _, done -> done(sample) }
        dialog("r4v3-info-branch", listOf(1f), listOf(Variants.PHONE, FLOAT)) { ctx ->
            DexDetail.show(DexPage(ctx, NoHost), gd.sp(133))
        }
    }

    @Test fun speciesAllOpen() {
        DetailSections.storeOf = { MapStore(true) }
        DetailSections.dexLoader = { _, _, done -> done(sample) }
        dialog("r4v3-dex-open", listOf(0f, 0.5f, 1f), listOf(Variants.PHONE, Variants.SPLIT)) { ctx -> withInfo(25); DexDetail.show(DexPage(ctx, NoHost), gd.sp(25)) }
        DetailSections.storeOf = { MapStore(false) }
        dialog("r4v3-dex-closed", listOf(0f), listOf(Variants.PHONE)) { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(25)) }
    }

    @Test fun battleWindow() {
        DetailSections.storeOf = { MapStore(true) }
        DetailSections.dexLoader = { _, _, done -> done(sample) }
        dialog("r4v3-battle", listOf(0f, 1f), listOf(Variants.PHONE, FLOAT)) { ctx -> rich(); BattleDetails.show(ctx, "t1") }
    }
}
