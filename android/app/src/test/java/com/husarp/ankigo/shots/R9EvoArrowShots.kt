package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.DetailSections
import com.husarp.ankigo.ui.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R9-B11: the evolution chain in the details is never clipped. Made-up lines (TestData species; sprite numbers are the ones the
 * FakeGame world has pictures for) with long method texts and many branches, at 320 / 340 / 412 / 869 dp, light + dark.
 * Every arrow label is whole (no ellipsis, no clipped line) and every view lies inside its parent and inside the section.
 * Shots: r9evo-<case>-<width>-<theme>.
 */
class R9EvoArrowShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun sp(num: Int, name: String, prevo: Int? = null, evos: List<Evolution> = emptyList()) =
        TestData.species(num, name, listOf(Type.NORMAL), prevo = prevo, evolutions = evos)

    private fun lv(to: Int, level: Int? = null, time: String? = null, condition: String? = null, item: String? = null,
                   kind: EvoKind = EvoKind.LEVEL, move: String? = null, moveType: Type? = null) =
        Evolution(to, kind, level = level, item = item, time = time, condition = condition, move = move, moveType = moveType)

    /** name -> (line data, the species shown). */
    private val cases: Map<String, Pair<GameData, Int>> by lazy {
        val short = TestData.data(listOf(sp(16, "Birdie", evos = listOf(lv(19, 7))), sp(19, "Ratty", 16)))
        val three = TestData.data(listOf(
            sp(1, "Leafling", evos = listOf(lv(2, 16))), sp(2, "Leafbud", 1, listOf(lv(3, 36, "night"))), sp(3, "Leafking", 2)))
        val long = TestData.data(listOf(
            sp(1, "Longnamedmon", evos = listOf(lv(2, null, "night", "holding Razor Fang at night"))),
            sp(2, "Extralongname", 1, listOf(lv(3, 40, "day", "while knowing Mimic with high friendship"))),
            sp(3, "Evenlongername", 2)))
        val moveKnown = TestData.data(listOf(
            sp(16, "Birdie", evos = listOf(lv(19, kind = EvoKind.LEVEL_MOVE, move = "mimic", time = "night", moveType = Type.FAIRY))),
            sp(19, "Ratty", 16)))
        val eevee = TestData.data(listOf(
            sp(133, "Fluffy", evos = listOf(
                lv(134, kind = EvoKind.ITEM, item = "water-stone"), lv(135, kind = EvoKind.ITEM, item = "thunder-stone"),
                lv(700, kind = EvoKind.FRIENDSHIP, moveType = Type.FAIRY), lv(54, kind = EvoKind.FRIENDSHIP, time = "day"),
                lv(92, kind = EvoKind.FRIENDSHIP, time = "night"), lv(129, 20, "night", "holding Razor Fang"), lv(130, kind = EvoKind.ITEM, item = "dusk-stone"))),
            sp(134, "Fluffwave", 133), sp(135, "Fluffbolt", 133), sp(700, "Fluffcharm", 133), sp(54, "Duckling", 133),
            sp(92, "Spooky", 133), sp(129, "Flopfish", 133), sp(130, "Flopdragon", 133)))
        val tree = TestData.data(listOf(
            sp(63, "Blinky", evos = listOf(lv(64, 16))),
            sp(64, "Blinkier", 63, listOf(lv(65, kind = EvoKind.ITEM, item = "kings-rock"), lv(150, 40, "night", "in a magnetic field"), lv(151, kind = EvoKind.TRADE))),
            sp(65, "Blinkiest", 64), sp(150, "Mindlord", 64), sp(151, "Tinymyth", 64)))
        linkedMapOf("short" to (short to 19), "three" to (three to 2), "long" to (long to 2), "move" to (moveKnown to 16),
            "eevee" to (eevee to 133), "tree" to (tree to 64))
    }

    private val widths = listOf(320, 340, 412, 869)

    private fun section(c: Context, name: String, compact: Boolean): View {
        val (data, num) = cases.getValue(name)
        val d = c.resources.displayMetrics.density
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * d).toInt(); setPadding(p, p, p, p)
            setBackgroundColor(Ui.c(c, R.color.ag_card))
            addView(DetailSections.evolution(c, data, data.species.getValue(num), setOf(1, 2, 3, 16, 19, 25, 26, 54, 63, 64, 65, 92, 129, 130, 133, 134, 135, 150, 151, 700), compact, null))
        }
    }

    private fun measure(v: View, wDp: Int, c: Context) {
        val d = c.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    private fun texts(v: View, out: MutableList<TextView> = ArrayList()): List<TextView> {
        if (v is TextView) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    /** Problems: a view bigger than the room its parent gave it (clipped), an ellipsized or cut-off text, a view outside the section. */
    private fun problems(v: View, where: String, out: MutableList<String> = ArrayList()): List<String> {
        if (v is TextView) {
            val l = v.layout
            if (l != null) {
                for (i in 0 until l.lineCount) if (l.getEllipsisCount(i) > 0) out += "$where: '${v.text}' ellipsized"
                if (l.height > v.height - v.compoundPaddingTop - v.compoundPaddingBottom + 1) out += "$where: '${v.text}' text ${l.height} px in ${v.height} px"
                if (l.width > v.width + 1) out += "$where: '${v.text}' layout ${l.width} px wide in ${v.width} px"
                val w = (0 until l.lineCount).maxOfOrNull { l.getLineMax(it) } ?: 0f
                if (w > v.width - v.compoundPaddingLeft - v.compoundPaddingRight + 1) out += "$where: '${v.text}' line $w px in ${v.width} px"
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) {
            val ch = v.getChildAt(i)
            if (v !is com.husarp.ankigo.ui.FitWidth && (ch.left < 0 || ch.top < 0 || ch.right > v.width || ch.bottom > v.height))
                out += "$where: ${ch.javaClass.simpleName} ${ch.left},${ch.top},${ch.right},${ch.bottom} outside ${v.javaClass.simpleName} ${v.width}x${v.height}"
            problems(ch, where, out)
        }
        return out
    }

    @Test fun nothingIsClipped() {
        val all = ArrayList<String>()
        for (theme in Theme.entries) for (w in widths) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme).copy(screenWidth = w * 2))
            val c = Shots.files(paparazzi.context)
            FakeGame.install(c)
            for (compact in listOf(false, true)) for (name in cases.keys) {
                val sec = section(c, name, compact)
                measure(sec, w, c)
                val tag = "$name/$w/${theme.name}/${if (compact) "compact" else "full"}"
                // the section itself: the chain must fit in its width and be drawn whole
                val fit = (sec as ViewGroup).getChildAt(0) as ViewGroup
                val tree = fit.getChildAt(0)
                val drawn = tree.measuredWidth * tree.scaleX
                if (drawn > fit.width + 1) all += "$tag: tree drawn $drawn px in ${fit.width}"
                if (tree.scaleX < 0.75f) all += "$tag: drawn at scale ${tree.scaleX}"
                all += problems(tree, tag)
                all += texts(tree).filter { it.text.isNotEmpty() && it.textSize * tree.scaleX < 7f * c.resources.displayMetrics.scaledDensity }.map { "$tag: '${it.text}' drawn too small (scale ${tree.scaleX})" }
            }
        }
        assertTrue(all.size.toString() + "\n" + all.joinToString("\n"), all.isEmpty())
    }

    @Test fun theNormalLookStaysWhenItFits() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        val d = c.resources.displayMetrics.density
        for ((name, compact, arrowDp) in listOf(Triple("short", false, 30), Triple("three", false, 30), Triple("short", true, 22))) {
            val sec = section(c, name, compact)
            measure(sec, 412, c)
            val tree = ((sec as ViewGroup).getChildAt(0) as ViewGroup).getChildAt(0)
            assertEquals("$name $compact: not scaled", 1f, tree.scaleX, 0f)
            val arrows = ArrayList<View>().also { fun walk(v: View) { if (v is DetailSections.ArrowView) it += v; if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }; walk(tree) }
            assertTrue(arrows.isNotEmpty())
            arrows.forEach { assertEquals("$name $compact: arrow length", Ui.dp(c, arrowDp), it.measuredWidth) }
        }
    }

    @Test fun shots() {
        for (w in widths) for (name in cases.keys) {
            val size = Size.PHONE
            for (theme in Theme.entries) {
                paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(size, theme).copy(screenWidth = w * 2, screenHeight = 700 * 2))
                val c = Shots.files(paparazzi.context)
                FakeGame.install(c)
                val frame = FrameLayout(c).apply {
                    setBackgroundColor(Ui.c(c, R.color.ag_base))
                    addView(section(c, name, false), FrameLayout.LayoutParams(-1, -2))
                }
                paparazzi.snapshot(frame, "r9evo-$name-$w-${theme.name.lowercase()}")
            }
        }
    }
}
