package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.help.GameGuide
import com.husarp.ankigo.help.HelpViews
import com.husarp.ankigo.ui.BoxCopiesSheet
import com.husarp.ankigo.ui.BoxTiles
import com.husarp.ankigo.ui.DetailSections
import com.husarp.ankigo.ui.MonTags
import com.husarp.ankigo.ui.TagKind
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.TeamSection
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R16-2-H (PLAN 164C + T1, X1 row star, X3, X4): the "Can evolve" double chevron in the details header, the copies sheet, the guide and
 * the Team row; bare tinted icons instead of text pills. PNGs: r16h-<what>-<variant>-<theme>.
 */
class R16HEvolveMarkShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val gd get() = GameRepo.data!!
    private val st get() = GameRepo.state!!
    private val STRIP = Variant("strip", 360, 360)
    private val NARROW = Variant("narrow", 300, 420)

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out)
        return out
    }

    private fun described(v: View, text: String) = all(v).filterIsInstance<ImageView>().filter { it.contentDescription == text }

    @Test fun glyphSizes() {
        paparazzi.shootPage("r16h-glyph", listOf(STRIP)) { ctx ->
            LinearLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                gravity = Gravity.CENTER
                listOf(12, 14, 16, 24).forEach { s ->
                    addView(MonTags.icon(ctx, com.husarp.ankigo.ui.MonTag(TagKind.CAN_EVOLVE, "Can evolve"), s),
                        LinearLayout.LayoutParams(Ui.dp(ctx, s), Ui.dp(ctx, s)).also { it.marginEnd = Ui.dp(ctx, 14) })
                }
            }
        }
    }

    @Test fun copiesSheetRow() {
        for (v in listOf(Variants.PHONE)) paparazzi.shootPage("r16h-copies", listOf(v)) { ctx ->
            st.mons += TestData.mon(gd, 25, 12, listOf("tackle"), "x2").also { it.shiny = true; it.favourite = true; it.caughtAt = 1_760_200_000_000 }
            st.settings.xpShareUid = "x2"
            val p = TeamPage(ctx, NoHost); p.preset(TeamSection.BOX)
            val screen = pageScreen(ctx, p)
            val tile = BoxTiles.build(gd, st.mons.filter { it.species == 25 }, true).single()
            val view = BoxCopiesSheet(p, tile, {}, { _, _, _ -> }).build((ctx.resources.displayMetrics.heightPixels * BoxCopiesSheet.LIST_SHARE).toInt())
            // the copy with the star, the Exp. Share and (for the Pikachu with a thunder stone in the bag) the evolve mark
            assertTrue("shiny star", described(view, "Shiny").isNotEmpty())
            assertTrue("evolve mark", described(view, "Can evolve").isNotEmpty())
            assertTrue("Exp. Share", described(view, "Exp. Share").isNotEmpty())
            FrameLayout(ctx).apply {
                addView(screen, FrameLayout.LayoutParams(-1, -1))
                addView(View(ctx).apply { setBackgroundColor(Color.argb(122, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
                addView(view, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }
        }
    }

    @Test fun guideCellsNarrow() {
        paparazzi.shootPage("r16h-guide", listOf(NARROW)) { ctx ->
            val ch = GameGuide.chapter(GameGuide.LEVELS)!!
            assertFalse(ch.points.joinToString(" ").contains("tag"))
            assertTrue(ch.points.any { it.startsWith("A green mark shows when a Pokémon is ready to evolve.") })
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), 0)
                HelpViews.chapterBody(ctx, ch).forEach { addView(it, LinearLayout.LayoutParams(-1, -2)) }
                val catching = GameGuide.chapter(GameGuide.CATCHING)!!
                HelpViews.chapterBody(ctx, catching).takeLast(1).forEach { addView(it, LinearLayout.LayoutParams(-1, -2)) }
                assertTrue("guide evolve icon", described(this, "Can evolve").isNotEmpty())
            }
        }
    }

    @Test fun detailsHeader() {
        paparazzi.shootPage("r16h-details", listOf(STRIP)) { ctx ->
            val mon = st.mon("t1")!!.also { it.shiny = true }
            val tags = MonTags.detailTags(gd, st, mon)
            assertTrue(TagKind.CAN_EVOLVE in tags.map { it.kind })
            FrameLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, R.color.ag_base))
                setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 14), Ui.dp(ctx, 14), 0)
                addView(DetailSections.marks(ctx, tags), FrameLayout.LayoutParams(-1, -2))
            }
        }
    }

    @Test fun teamRow() {
        for (v in listOf(Variants.PHONE)) paparazzi.shootPage("r16h-team", listOf(v)) { ctx ->
            st.mon("t1")!!.also { it.shiny = true }   // t1: Pikachu with the thunder stone in the bag = can evolve
            val p = TeamPage(ctx, NoHost); p.preset(TeamSection.TEAM)
            val screen = pageScreen(ctx, p)
            // the page renders on the next message-loop turn; the checks need the rows now
            TeamPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(p)
            val ready = st.teamMons().count { MonTags.canEvolve(gd, st, it) }
            assertTrue("a team mon can evolve", ready >= 1)
            assertEquals("one evolve mark per team mon that can evolve", ready, described(screen, "Can evolve").size)
            assertEquals("one star on the Team rows", 1, described(screen, "Shiny").size)
            screen
        }
    }
}
