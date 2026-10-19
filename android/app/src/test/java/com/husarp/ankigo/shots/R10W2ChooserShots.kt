package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.AppPrefs
import com.husarp.ankigo.ui.BattleFrameStyle
import com.husarp.ankigo.ui.BoxLookPreview
import com.husarp.ankigo.ui.Form
import com.husarp.ankigo.ui.HpBarLook
import com.husarp.ankigo.ui.InfoBoxLook
import com.husarp.ankigo.ui.InfoBoxStyle
import com.husarp.ankigo.ui.LookChooser
import com.husarp.ankigo.ui.LookChoosers
import com.husarp.ankigo.ui.SettingsCatalog
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.SettingsSection
import com.husarp.ankigo.ui.SheetKit
import com.husarp.ankigo.ui.Ui
import com.husarp.ankigo.ui.ViewSettingsSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.min

/**
 * ROUND 10 W7 / W8 (ticket F): the three collapsible preview choosers, "Info box style", "HP / XP bars" and "Battle frame", in the Settings
 * Battle section and in the battle's "View settings" sheet. Collapsed by default, each opens in place into a grid of small live previews
 * (3 to a row on a phone, 2 when narrow / at font 2.0). PNGs: r10w2ch-set-* (Settings), r10w2ch-sheet-* (sheet), r10w2ch-page-* (the whole page
 * after a search hit); sizes phone (412), narrow (320), big (320 at font 2.0), float (340).
 */
class R10W2ChooserShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private class V(val label: String, val wDp: Int, val hDp: Int, val font: Float = 1f)

    private val phone = V("phone", 412, 900)
    private val narrow = V("narrow", 320, 900)
    private val big = V("big", 320, 1500, 2.0f)
    private val float = V("float", 340, 900)

    private fun shoot(name: String, variants: List<V>, build: (Context) -> View) {
        for (v in variants) for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Variant(v.label, v.wDp, v.hDp).device(theme).copy(fontScale = v.font))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            reset(ctx)
            val frame = FrameLayout(ctx).apply { addView(build(ctx), FrameLayout.LayoutParams(-1, -1)) }
            paparazzi.snapshot(frame, "$name-${v.label}-${theme.name.lowercase()}", 1000)
        }
    }

    private fun config(v: V, theme: Theme = Theme.LIGHT): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Variant(v.label, v.wDp, v.hDp).device(theme).copy(fontScale = v.font))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        reset(ctx)
        return ctx
    }

    /** The preferences are kept between tests in this JVM: every test starts from the defaults. */
    private fun reset(ctx: Context) {
        AppPrefs.setInfoBoxLook(ctx, InfoBoxLook.TODAY)
        AppPrefs.setInfoBoxStyle(ctx, InfoBoxStyle.ABOVE)
        AppPrefs.setHpBarLook(ctx, HpBarLook.TODAY)
        AppPrefs.setBattleFrame(ctx, BattleFrameStyle.NONE)
    }

    // ---- Settings --------------------------------------------------------------------------------------------------------------------

    private val titles = listOf(LookChoosers.INFO_BOX, LookChoosers.BARS, LookChoosers.FRAME)

    /** The Battle section's "Battle look" part as Settings builds it: the three cards one under the other (the page's own methods). */
    private fun settingsHolder(ctx: Context, open: Set<String> = emptySet(), prep: (Context) -> Unit = {}): View {
        prep(ctx)
        val page = SettingsPage(ShotCtx(ctx), NoHost)
        fun card(name: String) = SettingsPage::class.java.getDeclaredMethod(name).also { it.isAccessible = true }.invoke(page) as ViewGroup
        val cards = listOf(card("infoBoxCard"), card("hpBarsCard"), card("battleFrameCard"))
        for (t in open) LookChooser.find(cards.first { LookChooser.find(it, t) != null }, t)!!.expand()
        return android.widget.ScrollView(ctx).apply {
            setBackgroundColor(Ui.c(ctx, R.color.ag_base))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 16))
                addView(Form.sect(ctx, "Battle look"))
                cards.forEachIndexed { i, c -> addView(c, Form.lp(ctx, if (i == 0) 0 else 8)) }
            })
        }
    }

    /** A made-up non-default state so the ring and the value line show a picked tile, not only the defaults. */
    private fun picked(ctx: Context) {
        AppPrefs.setInfoBoxLook(ctx, InfoBoxLook.GLASS)
        AppPrefs.setInfoBoxStyle(ctx, InfoBoxStyle.INSIDE)
        AppPrefs.setHpBarLook(ctx, HpBarLook.PIXEL)
        AppPrefs.setBattleFrame(ctx, BattleFrameStyle.WOOD)
    }

    @Test fun settingsCollapsed() {
        shoot("r10w2ch-set-collapsed", listOf(V("phone", 412, 420), V("narrow", 320, 420), V("big", 320, 700, 2.0f))) { settingsHolder(it, prep = ::picked) }
    }

    @Test fun settingsEachEntryExpanded() {
        for (t in titles) {
            val key = t.lowercase().replace(Regex("[^a-z]+"), "-").trim('-')
            shoot("r10w2ch-set-open-$key", listOf(phone, narrow, big, float)) { settingsHolder(it, open = setOf(t), prep = ::picked) }
        }
    }

    // ---- the sheet -------------------------------------------------------------------------------------------------------------------

    private fun sheetView(ctx: Context, open: Set<String> = emptySet(), bodyDp: Int = 1300, prep: (Context) -> Unit = {}): View {
        prep(ctx)
        val s = ViewSettingsSheet(ctx, null)
        val body = s.build(Ui.dp(ctx, bodyDp))
        for (t in open) LookChooser.find(body, t)!!.expand()
        val sheet = SheetKit.host(ctx, body, {}, {})
        val w = min(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600))
        return FrameLayout(ctx).apply {
            setBackgroundColor(Color.argb(60, 0, 0, 0))
            addView(sheet, FrameLayout.LayoutParams(w, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }
    }

    @Test fun sheetCollapsed() {
        shoot("r10w2ch-sheet-collapsed", listOf(V("phone", 412, 869), V("narrow", 320, 800), V("big", 320, 1500, 2.0f))) { sheetView(it, prep = ::picked) }
    }

    @Test fun sheetEachEntryExpanded() {
        for (t in titles) {
            val key = t.lowercase().replace(Regex("[^a-z]+"), "-").trim('-')
            shoot("r10w2ch-sheet-open-$key", listOf(V("phone", 412, 1100), V("narrow", 320, 1100), V("big", 320, 1700, 2.0f), V("float", 340, 1100))) {
                sheetView(it, open = setOf(t), prep = ::picked)
            }
        }
    }

    /** The sheet with an entry open still fits: its body is capped at the usual share of the height and scrolls. */
    @Test fun theSheetStillFitsWithEveryEntryOpen() {
        for (v in listOf(V("phone", 412, 869), V("narrow", 320, 640), V("float", 340, 480), V("big", 320, 640, 2.0f))) {
            val ctx = config(v)
            val s = ViewSettingsSheet(ctx, null)
            val h = ctx.resources.displayMetrics.heightPixels
            val body = s.build((h * ViewSettingsSheet.BODY_SHARE).toInt() - Ui.dp(ctx, 120))
            for (t in titles) LookChooser.find(body, t)!!.expand()
            body.measure(View.MeasureSpec.makeMeasureSpec(min(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600)), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST))
            assertTrue("${v.label}: the sheet is ${body.measuredHeight} of $h px", body.measuredHeight <= h * 0.95f)
            val scroll = (body as ViewGroup).let { g -> (0 until g.childCount).map { g.getChildAt(it) }.first { it is android.widget.ScrollView } as android.widget.ScrollView }
            assertTrue("${v.label}: its rows scroll", scroll.getChildAt(0).let { c -> c.measure(View.MeasureSpec.makeMeasureSpec(scroll.measuredWidth, View.MeasureSpec.EXACTLY), 0); c.measuredHeight } > scroll.measuredHeight)
        }
    }

    // ---- behaviour -------------------------------------------------------------------------------------------------------------------

    private fun texts(v: View, out: MutableList<String> = ArrayList()): List<String> {
        if (v is TextView && v.text.isNotEmpty()) out += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
        return out
    }

    private fun tapText(root: View, label: String) {
        fun first(v: View): TextView? {
            if (v is TextView && v.text.toString() == label) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) first(v.getChildAt(i))?.let { return it }
            return null
        }
        var v: View? = first(root) ?: throw AssertionError("no '$label'")
        while (v != null && !v.isClickable) v = v.parent as? View
        requireNotNull(v) { "'$label' is not tappable" }.performClick()
    }

    /** True when [v] and every ancestor up to [root] is VISIBLE (a closed chooser's grid is GONE). */
    private fun shown(v: View, root: View): Boolean {
        var c: View? = v
        while (c != null) { if (c.visibility != View.VISIBLE) return false; if (c === root) return true; c = c.parent as? View }
        return true
    }

    private fun both(ctx: Context): List<Pair<String, View>> {
        val sheet = ViewSettingsSheet(ctx, null).build(Ui.dp(ctx, 900))
        return listOf("settings" to settingsHolder(ctx), "sheet" to sheet)
    }

    @Test fun collapsedByDefaultWithTheValueLineAndATapTogglesIt() {
        val ctx = config(phone)
        for ((where, root) in both(ctx)) {
            for (t in titles) {
                val c = LookChooser.find(root, t) as LookChooser<*>
                assertFalse("$where/$t: collapsed", c.expanded)
                assertFalse("$where/$t: grid hidden", shown(c.tileFor0(), c))
                assertTrue("$where/$t: TalkBack reads title, value, state: ${c.header.contentDescription}", c.header.contentDescription.toString().let { it.startsWith("$t, ") && it.endsWith(", collapsed") })
                c.header.performClick()
                assertTrue("$where/$t: opened", c.expanded)
                assertTrue("$where/$t: grid shown", shown(c.tileFor0(), c))
                assertTrue(c.header.contentDescription.toString().endsWith(", expanded"))
                c.header.performClick()
                assertFalse("$where/$t: closed again", c.expanded)
            }
            assertEquals("$where: default value lines", "Card · number above", (LookChooser.find(root, LookChoosers.INFO_BOX) as LookChooser<*>).valueText)
            assertEquals("Today", (LookChooser.find(root, LookChoosers.BARS) as LookChooser<*>).valueText)
            assertEquals("No frame", (LookChooser.find(root, LookChoosers.FRAME) as LookChooser<*>).valueText)
        }
    }

    private fun LookChooser<*>.tileFor0(): View = (this as LookChooser<Any>).let { c -> c.tileFor(c.picked) }

    @Test fun aPickWritesThePreferenceAndTheValueLineFollowsAndTheGridStaysOpen() {
        val ctx = config(phone)
        for ((where, root) in both(ctx)) {
            val info = LookChooser.find(root, LookChoosers.INFO_BOX) as LookChooser<InfoBoxLook>
            val bars = LookChooser.find(root, LookChoosers.BARS) as LookChooser<HpBarLook>
            val frame = LookChooser.find(root, LookChoosers.FRAME) as LookChooser<BattleFrameStyle>
            info.expand(); bars.expand(); frame.expand()
            for (look in InfoBoxLook.entries) {
                info.tileFor(look).performClick()
                assertEquals("$where: $look", look, AppPrefs.infoBoxLook(ctx))
                val word = if (AppPrefs.infoBoxStyle(ctx) == InfoBoxStyle.INSIDE) "inside" else "above"
                assertEquals("${look.label} · number $word", info.valueText)
                assertEquals("${look.label}, selected", info.tileFor(look).contentDescription)
                assertTrue(info.tileFor(look).isSelected)
                assertEquals(1, InfoBoxLook.entries.count { info.tileFor(it).isSelected })
                assertTrue("the grid stays open", info.expanded)
            }
            for (look in HpBarLook.entries) {
                bars.tileFor(look).performClick()
                assertEquals(look, AppPrefs.hpBarLook(ctx)); assertEquals(look.label, bars.valueText)
            }
            for (f in BattleFrameStyle.entries) {
                frame.tileFor(f).performClick()
                assertEquals(f, AppPrefs.battleFrame(ctx)); assertEquals(f.label, frame.valueText)
                assertEquals("${f.label}, selected", frame.tileFor(f).contentDescription)
            }
            assertTrue(bars.expanded && frame.expanded)
        }
    }

    @Test fun theLayoutChoiceRedrawsThePreviewsAndTheValueLine() {
        val ctx = config(phone)
        for ((where, root) in both(ctx)) {
            AppPrefs.setInfoBoxStyle(ctx, InfoBoxStyle.ABOVE)
            val info = LookChooser.find(root, LookChoosers.INFO_BOX) as LookChooser<InfoBoxLook>
            val box = { info.previewViews().map { it as BoxLookPreview } }
            assertTrue(box().all { it.style == InfoBoxStyle.ABOVE })
            tapText(info, InfoBoxStyle.INSIDE.label)
            assertEquals("$where: layout pref", InfoBoxStyle.INSIDE, AppPrefs.infoBoxStyle(ctx))
            assertTrue("$where: all six previews follow", box().size == 6 && box().all { it.style == InfoBoxStyle.INSIDE })
            assertEquals("Card · number inside", info.valueText)
            tapText(info, InfoBoxStyle.ABOVE.label)
            assertEquals(InfoBoxStyle.ABOVE, AppPrefs.infoBoxStyle(ctx))
            assertTrue(box().all { it.style == InfoBoxStyle.ABOVE })
            assertEquals("Card · number above", info.valueText)
            // one tile per look, named by the look; the picked look is kept when only the layout changes
            info.tileFor(InfoBoxLook.PLATE).performClick()
            tapText(info, InfoBoxStyle.INSIDE.label)
            assertEquals(InfoBoxLook.PLATE, AppPrefs.infoBoxLook(ctx))
            assertEquals("Dark plate · number inside", info.valueText)
            assertTrue(box().map { it.look } == InfoBoxLook.entries)
            AppPrefs.setInfoBoxStyle(ctx, InfoBoxStyle.ABOVE)
        }
    }

    @Test fun theTilesAreButtonsForTalkBack() {
        val ctx = config(phone)
        for ((_, root) in both(ctx)) for (t in titles) {
            val c = LookChooser.find(root, t) as LookChooser<Any>
            for (view in listOf(c.header, c.tileFor0())) {
                assertTrue(view.isClickable && view.isFocusable)
                val node = androidx.core.view.accessibility.AccessibilityNodeInfoCompat.obtain()
                view.onInitializeAccessibilityNodeInfo(node.unwrap())
                assertEquals("$t: a button", "android.widget.Button", node.className.toString())
            }
        }
    }

    @Test fun twoToARowWhenNarrowOrBigAndThreeOnAPhone() {
        for ((v, want) in listOf(phone to 3, narrow to 2, big to 2, float to 3)) {
            val ctx = config(v)
            val sheet = ViewSettingsSheet(ctx, null).build(Ui.dp(ctx, 900))
            val set = settingsHolder(ctx)
            val w = min(ctx.resources.displayMetrics.widthPixels, Ui.dp(ctx, 600))
            for ((where, root) in listOf("sheet" to sheet, "settings" to set)) {
                val c = LookChooser.find(root, LookChoosers.INFO_BOX) as LookChooser<*>
                c.expand()
                root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val expect = if (where == "settings" && v === float) 2 else want   // the Settings card is narrower than the sheet (page padding + row padding)
                assertEquals("${v.label}/$where: tiles per row", expect, c.columnsNow)
            }
        }
    }

    // ---- search ----------------------------------------------------------------------------------------------------------------------

    private fun battlePage(ctx: Context, hit: String?): Pair<SettingsPage, View> {
        val c = ShotCtx(ctx)
        val p = SettingsPage(c, NoHost)
        val v = pageScreen(c, p)
        if (hit != null) p.openSetting(SettingsCatalog.byId(hit)!!) else p.open(SettingsSection.BATTLE)
        return p to v
    }

    @Test fun aSearchHitOpensTheBattleSectionAndExpandsTheRightEntry() {
        val ctx = config(phone)
        val ids = SettingsCatalog.all.filter { LookChoosers.titleFor(it.id) != null }.map { it.id }
        assertEquals("2 layouts + 6 looks + 7 frames + 2 bars", 17, ids.size)
        for (id in ids) {
            val (_, v) = battlePage(ctx, id)
            val want = LookChoosers.titleFor(id)!!
            for (t in titles) assertEquals("$id: only $want is open", t == want, (LookChooser.find(v, t) as LookChooser<*>).expanded)
        }
        // Battle section without a hit: all three collapsed
        val (_, v) = battlePage(ctx, null)
        for (t in titles) assertFalse((LookChooser.find(v, t) as LookChooser<*>).expanded)
        assertNotNull(texts(v).firstOrNull { it == "BATTLE LOOK" })
    }

    @Test fun anOpenEntryStaysOpenWhenThePageRendersAgain() {
        val ctx = config(phone)
        val (p, v) = battlePage(ctx, null)
        fun open() = titles.map { (LookChooser.find(v, it) as LookChooser<*>).expanded }
        assertEquals(listOf(false, false, false), open())
        (LookChooser.find(v, LookChoosers.BARS) as LookChooser<*>).expand()
        (LookChooser.find(v, LookChoosers.FRAME) as LookChooser<*>).expand()
        p.open(SettingsSection.BATTLE)   // the page builds every row again (a theme change, the font scale, coming back to the section)
        assertEquals("the same entries are still open", listOf(false, true, true), open())
        (LookChooser.find(v, LookChoosers.FRAME) as LookChooser<*>).collapse()
        p.open(SettingsSection.BATTLE)
        assertEquals("a closed one stays closed", listOf(false, true, false), open())
        // the Activity being recreated (a theme change): the open ones are saved and put back
        val saved = p.openChoosersNow()
        assertEquals(listOf(LookChoosers.BARS), saved)
        val (p2, v2) = battlePage(ctx, null)
        assertEquals("a fresh open starts collapsed", listOf(false, false, false), titles.map { (LookChooser.find(v2, it) as LookChooser<*>).expanded })
        p2.restoreChoosers(saved)
        p2.open(SettingsSection.BATTLE)
        assertEquals(listOf(false, true, false), titles.map { (LookChooser.find(v2, it) as LookChooser<*>).expanded })
    }

    @Test fun pageAfterASearchHit() {
        for (id in listOf("hpBarsPixel", "infoBoxLookGlass", "frameGold")) {
            shoot("r10w2ch-page-${id.lowercase()}", listOf(V("tall", 412, 3300))) { battlePage(it, id).second }
        }
    }
}
