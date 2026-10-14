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
import com.husarp.ankigo.ui.DexDetail
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.TeamPage
import org.junit.Rule
import org.junit.Test

/* U-09: the redesigned Pokémon details and the Pokédex entry: phone, split, floating card, tall; light and dark. */
class DetailShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val FLOAT = Variant("float", 340, 480)
    private val variants = listOf(Variants.PHONE, Variants.SPLIT, FLOAT)
    private val st get() = GameRepo.state!!
    private val gd get() = GameRepo.data!!

    /** All ScrollViews of the tree (the dialog has its own, empty one for the message). */
    private fun scrolls(v: View, out: MutableList<ScrollView> = ArrayList()): List<ScrollView> {
        if (v is ScrollView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) scrolls(v.getChildAt(i), out)
        return out
    }

    /** One render per scroll position in [slices] (0 = top, 1 = bottom): the dialog scrolls, so a tall one needs several. */
    private fun dialog(name: String, slices: List<Float> = listOf(0f, 0.5f, 1f), open: (Context) -> Unit) {
        slices.forEachIndexed { k, frac ->
            paparazzi.shootPage("$name-s$k", variants) { ctx ->
                val d = DialogShots.capture { open(ctx) } ?: error("no dialog was shown")
                val scene = DialogShots.scene(ctx, d)
                object : FrameLayout(ctx) {
                    // scrolls right before drawing, when every size is final: the details' ScrollView is the one with most content
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

    /** t1 made rich: Jolly nature, 4 moves, EVs, shiny, new, Exp. Share, holding a Lucky Egg, can evolve (Thunder Stone in the bag). */
    private fun rich(): Mon {
        val old = st.mon("t1")!!
        val m = Mon(old.uid, old.species, old.name, old.level, old.xp, Stats(31, 12, 25, 3, 18, 30), Stats(0, 4, 0, 0, 0, 252),
            Nature.JOLLY, 'F', true, "static", mutableListOf("tackle", "thunderbolt", "quickattack", "bodyslam"), old.hp,
            friendship = 180, heldItem = "lucky-egg", caughtAt = System.currentTimeMillis(), defeats = 42)
        st.mons[st.mons.indexOf(old)] = m
        st.settings.xpShareUid = m.uid
        return m
    }

    @Test fun teamMon() {
        dialog("detail-team") { ctx -> MonDetail.show(TeamPage(ctx, NoHost), gd, rich()) }
    }

    @Test fun faintedMonNoPage() {
        dialog("detail-fainted") { ctx -> MonDetail.show(TeamPage(ctx, NoHost), gd, st.mon("t5")!!) }
        dialog("detail-nopage", listOf(0f)) { ctx -> MonDetail.show(ctx, gd, st.mon("t3")!!) }
    }

    @Test fun nickname() {
        dialog("detail-nick", listOf(0f)) { ctx ->
            val m = st.mon("t2")!!; m.nickname = "Mr. Longest Nick Ever 12345"
            MonDetail.show(TeamPage(ctx, NoHost), gd, m)
        }
    }

    @Test fun dex() {
        dialog("detail-dex-caught") { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(25)) }
        dialog("detail-dex-seen") { ctx -> DexDetail.show(DexPage(ctx, NoHost), gd.sp(150)) }
    }
}
