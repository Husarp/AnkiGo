package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.DiagnosticsActivity
import com.husarp.ankigo.ui.FoldGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * R16-3-L (PLAN 220): the Diagnostics page. The whole page scrolls (status card, log, option groups), the groups start folded and a tap on
 * a bar folds / unfolds one, the log box is 35 % of the window since R18 256 (was 70 %, at least 320 dp; now at least 200 dp) and "Full screen" shows it in a dialog.
 * Shots at 360 and 412 dp wide, light + dark: folded with a long log, one group open, the full-screen log.
 */
class R16LDiagnosticsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val widths = listOf(Variant("w360", 360, 780), Variant("w412", 412, 869))

    private val status = "▶ com.ichi2.anki  v2.24.0 (20240000)  provider=yes spec=2  permission=granted\n" +
        "Target: com.ichi2.anki  ·  API: OK (7 decks readable)\n" +
        "Media: all-files=false folder=false imported=0  ·  AnkiGo reviews today: 86"

    private val longLog = (1..300).joinToString("\n") { i ->
        "03:%02d:%02d C%d %s".format(i / 60, i % 60, 1 + i % 8,
            if (i % 7 == 0) "FAIL download 'sounds/cry_$i.ogg': SocketTimeoutException: timeout" else "OK $i decks readable, media probe /storage/emulated/0/AnkiDroid/collection.media")
    }

    private fun page(ctx: Context): Pair<DiagnosticsActivity, View> {
        val a = attach(DiagnosticsActivity(), ShotCtx(ctx))
        val content = a.buildContent()
        (getField(a, "header") as TextView).text = status
        (getField(a, "logView") as TextView).text = longLog
        return a to content
    }

    private fun setup(): Context = Shots.files(paparazzi.context).also { FakeGame.install(it) }

    private fun headers(v: View): List<View> {
        val out = ArrayList<View>()
        fun walk(x: View) { if (x.tag == FoldGroup.HEADER_TAG) out += x; if (x is ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i)) }
        walk(v)
        return out
    }

    private fun group(header: View): View = header.parent as View

    private fun title(header: View): String = ((header as ViewGroup).getChildAt(0) as TextView).text.toString()

    @Test fun foldedWithLongLog() {
        paparazzi.shootPage("r16l-folded", widths) { ctx -> page(ctx).second }
    }

    @Test fun oneGroupOpen() {
        paparazzi.shootPage("r16l-open", widths) { ctx ->
            val content = page(ctx).second
            headers(content).first { title(it) == "Checks" }.performClick()
            content
        }
    }

    @Test fun fullScreenLog() {
        paparazzi.shootPage("r16l-fullscreen", widths) { ctx ->
            val a = page(ctx).first
            val d = AppDialogShots.capture { call(a, "showFullLog") } ?: error("no dialog was shown")
            AppDialogShots.scene(ctx, d)
        }
    }

    @Test fun aFoldBarTogglesItsGroup() {
        val content = page(setup()).second
        val bars = headers(content)
        assertEquals(listOf("AnkiDroid", "Checks", "Media", "Widgets"), bars.map { title(it) })
        bars.forEach { assertFalse("${title(it)} starts folded", FoldGroup.isOpen(group(it))) }
        val checks = bars.first { title(it) == "Checks" }
        checks.performClick()
        assertTrue(FoldGroup.isOpen(group(checks)))
        assertFalse("other groups stay folded", FoldGroup.isOpen(group(bars.first { title(it) == "Media" })))
        assertTrue(checks.contentDescription.toString().endsWith("open"))
        checks.performClick()
        assertFalse(FoldGroup.isOpen(group(checks)))
        assertTrue(checks.contentDescription.toString().endsWith("closed"))
    }

    @Test fun theWholePageScrollsAndTheLogBoxIsTall() {
        val ctx = setup()
        val (a, content) = page(ctx)
        val d = ctx.resources.displayMetrics.density
        val w = (412 * d).toInt(); val h = (869 * d).toInt()
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        content.layout(0, 0, w, h)
        val log = getField(a, "logScroll") as ScrollView
        assertTrue("log box about 35 % of the window (R18 256), got ${log.height / d}", log.height >= 200 * d && log.height <= 320 * d)
        val page = (content as ViewGroup).getChildAt(1) as ScrollView
        // R18 256: with the shorter log box the folded page may fit the window; it is still one ScrollView
        assertTrue("log box lies inside the page's scrolling content", log.parent.parent === page || log.parent === page.getChildAt(0))
        // the log sits right under the status card (its buttons, then the box), above the option groups
        val body = page.getChildAt(0) as ViewGroup
        assertEquals(2, body.indexOfChild(log))
        assertTrue("log above the groups", body.indexOfChild(log) < body.indexOfChild(group(headers(content).first())))
        // the log opens at its end
        val inner = (getField(a, "logView") as TextView)
        assertEquals(inner.height - log.height, log.scrollY)
    }
}
