package com.husarp.ankigo.shots

import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R22SEvolutionShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun bagNavigationTargetsShopAndSelectsBag() {
        val a = Shots.files(paparazzi.context)
        FakeGame.install(a)
        var launched: android.content.Intent? = null
        val capture = object : android.content.ContextWrapper(a) {
            override fun startActivity(intent: android.content.Intent) { launched = intent }
        }
        ShopActivity.openBag(capture)
        assertEquals(ShopActivity::class.java.name, launched?.component?.className)
        assertTrue(launched!!.getBooleanExtra(ShopActivity.EXTRA_BAG, false))
        val page = ShopPage(a, NoHost)
        page.openBag()
        val screen = pageScreen(a, page)
        ShopPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
        val labels = views(screen).filterIsInstance<TextView>().map { it.text.toString() }
        assertFalse(labels.contains("DEAL OF THE DAY"))
        assertTrue(labels.any { it.startsWith("Bag ·") })
        page.onGone()
    }

    @Test fun bagAndShopAreRealInlineLinks() {
        val a = Shots.files(paparazzi.context)
        FakeGame.install(a)
        val data = TestData.data(listOf(TestData.species(133, "Eevee")))
        var target: Boolean? = null
        val body = EvolutionRows.explanationContent(a, data, Evolution(135, EvoKind.ITEM, item = "thunder-stone"),
            data.sp(133), setOf(133), TestData.state(data, 133, 20).apply { bag.clear() }, null) { target = it }
        val text = views(body).filterIsInstance<TextView>().last().text as Spanned
        val links = text.getSpans(0, text.length, ClickableSpan::class.java)
        assertEquals(listOf("open your Bag", "Shop"), links.map { text.subSequence(text.getSpanStart(it), text.getSpanEnd(it)).toString() })
        links[0].onClick(body); assertEquals(true, target)
        links[1].onClick(body); assertEquals(false, target)
        paparazzi.snapshot(body, "r22s-condition-bubble")
    }

    @Test fun eightBranchesRemainSeparateReadableRowsWithHiddenNames() {
        for (theme in Theme.entries) for (scale in listOf(1f, 2f)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme).copy(fontScale = scale))
            val a = Shots.files(paparazzi.context)
            FakeGame.install(a)
            val root = TestData.species(133, "Eevee", evolutions = (134..141).map { Evolution(it, EvoKind.ITEM, item = "thunder-stone") })
            val data = TestData.data(listOf(root) + (134..141).map { TestData.species(it, "Branch$it", prevo = 133) })
            val rows = EvolutionRows(a, data, EvoLine.of(data, root).root, root, setOf(133), null)
            rows.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(a, 272), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            rows.layout(0, 0, rows.measuredWidth, rows.measuredHeight)
            assertEquals(9, rows.childCount)
            val labels = views(rows).filterIsInstance<TextView>()
            assertEquals(8, labels.count { it.text.toString() == "???" })
            assertEquals(8, labels.count { it.text.toString() == "Thunder Stone" })
            labels.forEach { assertTrue("text has space: ${it.text}", it.width > 0 && it.height >= it.layout.height) }
            paparazzi.snapshot(rows, "r22s-eight-branches-${theme.name}-$scale")
        }
    }

    @Test fun regionalLabelAndSpeciesTapsStayOnTheName() {
        val a = Shots.files(paparazzi.context)
        FakeGame.install(a)
        val root = TestData.species(25, "Pikachu", evolutions = listOf(Evolution(10100, EvoKind.ITEM, item = "thunder-stone")))
        val regional = TestData.species(10100, "Raichu-Alola", prevo = 25).copy(form = "Alola", baseNum = 26)
        val data = TestData.data(listOf(root, regional))
        var tapped: Int? = null
        val rows = EvolutionRows(a, data, EvoLine.of(data, root).root, root, setOf(25, 26), { tapped = it.num })
        val labels = views(rows).filterIsInstance<TextView>()
        assertTrue(labels.any { it.text.toString() == "Alola" })
        (labels.single { it.text.toString() == "Raichu" }.parent as View).performClick()
        assertEquals(10100, tapped)
    }
}
