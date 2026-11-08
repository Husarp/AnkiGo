package com.husarp.ankigo.shots

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.Anki
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.StudyActivity
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.*
import com.husarp.ankigo.help.*
import com.husarp.ankigo.stats.AnkiStatsStore
import com.husarp.ankigo.stats.StatsSnapshot
import com.husarp.ankigo.ui.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** R31-o production controls in both themes, with local real-art cache. */
class R31OShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule(useDeviceResolution = true)
    private val spriteRoot = File(System.getenv("R31O_SPRITES") ?: "/tmp/ankigo-r31d-sprites")
    private fun dp(ctx: Context, n: Int) = Ui.dp(ctx, n)
    private fun tree(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { tree(v.getChildAt(it)) } else emptyList()
    private fun detach(v: View): View = v.also { (it.parent as? ViewGroup)?.removeView(it) }
    private fun text(ctx: Context, s: String, sp: Float = 14f, weight: Int = 800, ink: Int = R.color.ag_text) =
        Ui.text(ctx, sp, weight, ink).apply { text = s; setLineSpacing(0f, 1.12f) }
    private fun col(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private fun lp(ctx: Context, top: Int = 0) = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(ctx, top) }
    @Before fun quietStats() {
        org.junit.Assume.assumeTrue("R31O_SPRITES must point to the local real-art cache", spriteRoot.isDirectory)
        AnkiStatsStore.enabled = false; AnkiStatsStore.snapshot = StatsSnapshot()
    }
    @After fun restoreStats() { AnkiStatsStore.enabled = true; AnkiStatsStore.snapshot = StatsSnapshot() }

    private fun context(theme: Theme, height: Int = 869, width: Int = 412): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme).copy(screenHeight = height * 2, screenWidth = width * 2))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.withSprites = false
        FakeGame.install(ctx)
        val base = GameRepo.data!!
        val named = GameData(base.species.mapValues { (n, sp) -> when (n) {
            25 -> sp.copy(name = "Pikachu", id = "pikachu")
            54 -> sp.copy(name = "Psyduck", id = "psyduck")
            else -> sp
        } }, base.moves, base.growth, base.itemPrices)
        setField(GameRepo, "loadedData", named); call(GameRepo, "rebuild")
        GameRepo.state!!.mons.forEach { if (it.species in listOf(25, 54)) it.name = named.sp(it.species).name }
        GameRepo.state!!.battle!!.wild.name = "Psyduck"
        require(spriteRoot.isDirectory) { "Real sprites required: set R31O_SPRITES; generated art is forbidden" }
        spriteRoot.copyRecursively(Sprites.dir(ctx), overwrite = true)
        for (n in listOf(25, 54)) for (kind in listOf("front", "back", "static"))
            require(File(Sprites.dir(ctx), "$kind/$n.png").isFile) { "Missing real sprite $kind/$n" }
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        return ctx
    }

    private fun shot(view: View, name: String) {
        paparazzi.snapshot(view, name)
        assertTrue("Empty render $name", view.width > 0 && view.height > 0)
        val out = JSONObject().put("width", view.width).put("height", view.height)
        if (view is LinearLayout && view.childCount > 0) out.put("contentBottom", view.getChildAt(view.childCount - 1).bottom)
        out.put("texts", org.json.JSONArray(tree(view).filterIsInstance<TextView>().map { it.text.toString() }))
        File(Shots.outDir, "$name.json").writeText(out.toString(2))
    }

    @Test fun goalTickerFollowsViewportAndMotionPreference() {
        val ctx = context(Theme.LIGHT)
        val bar = BarView(ctx).apply {
            setMeaning(BarMeaning.GOAL, 1f, false)
            setGoalSparks(1)
        }
        val column = col(ctx).apply {
            addView(bar, LinearLayout.LayoutParams(-1, dp(ctx, 14)))
            addView(View(ctx), LinearLayout.LayoutParams(-1, dp(ctx, 1800)))
        }
        val scroll = ScrollView(ctx).apply { addView(column) }
        var checked = false
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                scroll.viewTreeObserver.removeOnPreDrawListener(this)
                ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
                call(bar, "refreshSparks")
                assertEquals(android.animation.ValueAnimator.areAnimatorsEnabled(), getField(bar, "sparkRunning"))
                scroll.scrollTo(0, dp(ctx, 900))
                scroll.viewTreeObserver.dispatchOnScrollChanged()
                assertEquals(false, getField(bar, "sparkRunning"))
                assertNull(getField(bar, "sparkDrawable"))
                scroll.scrollTo(0, 0)
                scroll.viewTreeObserver.dispatchOnScrollChanged()
                assertNotNull(getField(bar, "sparkDrawable"))
                ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
                call(bar, "refreshSparks")
                assertEquals(false, getField(bar, "sparkRunning"))
                checked = true
                return true
            }
        }
        scroll.viewTreeObserver.addOnPreDrawListener(listener)
        shot(scroll, "o-513-motion-check")
        assertTrue("Viewport checks must run while attached", checked)
        assertEquals(false, getField(bar, "sparkRunning"))
    }

    @Test fun studyBackdrop513() {
        for (theme in Theme.entries) {
            val ctx = context(theme)
            val state = GameRepo.state!!
            state.reviewsToday = 100; state.settings.dailyAverage = 100
            val activity = attach(StudyActivity(), ShotCtx(ctx))
            setField(activity, "heightMode", 0); setField(activity, "panelDp", 360)
            setField(activity, "deckId", 2L); setField(activity, "deckName", "Geography::Capitals")
            val root = activity.buildContent()
            setField(activity, "card", Anki.Review(10L, 0, 4, listOf("<1m", "6m", "1d", "4d"), emptyList()))
            setField(activity, "question", "<div>Capital of Australia?</div>")
            setField(activity, "answerHtml", "<div>Canberra</div>")
            setField(activity, "modelName", "Basic"); setField(activity, "cardName", "Card 1")
            setField(activity, "flipped", false); setField(activity, "locked", false)
            (getField(activity, "subtitle") as TextView).text = "Card 1 · card 8"
            activity.showQueueCounts(StudyDeckKit.decks[1], 2)
            call(activity, "layout")
            val panel = getField(activity, "panel") as BattlePanelView
            panel.renderAll(); panel.restoreStudyCharge()
            call(activity, "showButtons")
            val name = "o-513-reached-${theme.name.lowercase()}"
            shot(root, name)
            val bar = getField(panel, "goalBar") as View
            val origin = IntArray(2).also { root.getLocationOnScreen(it) }
            val rect = android.graphics.Rect()
            assertTrue("Goal bar must be visible", bar.getGlobalVisibleRect(rect))
            assertTrue(rect.width() > 100 && rect.height() > 0)
            File(Shots.outDir, "$name-goal.json").writeText(JSONObject()
                .put("x", rect.left - origin[0]).put("y", rect.top - origin[1])
                .put("width", rect.width()).put("height", rect.height())
                .put("screenWidth", root.width).put("screenHeight", root.height).toString(2))

        }
    }

    private fun nativeSegments(ctx: Context, names: List<String>, ids: List<Int>): View {
        val row = Form.segmented(ctx, names, 0) {}
        tree(row).filterIsInstance<TextView>().forEachIndexed { i, t ->
            SubtabIcons.apply(t, ids[i], names.size > 2)
            t.maxLines = 2; t.gravity = Gravity.CENTER
        }
        return row
    }

    @Test fun subtabInventory516() {
        for (theme in Theme.entries) {
            val ctx = context(theme, 2800)
            val sheet = col(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_base)); setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12)) }
            fun title(s: String) { sheet.addView(text(ctx, s, 13f, 900, R.color.ag_sub0), lp(ctx, 17)) }
            title("Stats")
            val stats = LinearLayout(ctx)
            SectionSwitch.draw(ctx, stats, StatsSection.entries.map { SectionSwitch.Seg(it.label, it.icon) }, 0) {}
            assertTrue(tree(stats).filterIsInstance<TextView>().all { it.compoundDrawablesRelative[0] != null })
            sheet.addView(stats, lp(ctx, 5))
            title("Achievements · full strip, unrolled")
            val page = StatsPage(ShotCtx(ctx), NoHost, StatsSection.ACHIEVEMENTS)
            pageScreen(ctx, page)
            val av = page.achievementsView()
            val names = listOf("All") + AchievementTable.GROUPS
            names.chunked(2).forEach { labels ->
                val method = av.javaClass.getDeclaredMethod("tabs", List::class.java).apply { isAccessible = true }
                val strip = method.invoke(av, labels) as View
                assertTrue(tree(strip).filterIsInstance<TextView>().all { it.compoundDrawablesRelative[0] != null })
                // Detach the real strip's row so the next source render cannot remove it.
                val row = getField(av, "tabRow") as LinearLayout
                sheet.addView(detach(row), lp(ctx, 2))
                val next = LinearLayout(ctx)
                setField(av, "tabRow", next)
                (getField(av, "tabScroll") as ViewGroup).addView(next)
            }
            page.onGone()
            title("Shop")
            val shop = ShopPage(ShotCtx(ctx), NoHost)
            pageScreen(ctx, shop)
            call(shop, "render")
            val shopLabels = listOf(getField(shop, "tabShop") as TextView, getField(shop, "tabBag") as TextView)
            val shopTabs = shopLabels.first().parent as View
            assertTrue(shopLabels.all { it.compoundDrawablesRelative[0] != null })
            assertTrue("Both Shop tabs must survive decoration", shopLabels.all { it in tree(shopTabs) })
            sheet.addView(detach(shopTabs), lp(ctx, 5))
            shop.onGone()
            title("Bag · scope")
            val bag = ShopPage(ShotCtx(ctx), NoHost)
            bag.openBag()
            val bagRoot = pageScreen(ctx, bag)
            call(bag, "render")
            val scope = tree(bagRoot).first { it.tag == "bag-scope" }
            assertTrue(tree(scope).filterIsInstance<TextView>().all { it.compoundDrawablesRelative[0] != null })
            sheet.addView(detach(scope), lp(ctx, 5)); bag.onGone()
            title("Use item · More Pokémon")
            val firstPicker = DialogShots.capture { BattleBag.pick(ShotCtx(ctx), GameRepo.data!!, GameRepo.state!!, "potion") {} }!!
            val more = tree(firstPicker.content()).filterIsInstance<TextView>().single { it.text.toString() == "More Pokémon" }
            val expanded = DialogShots.capture { more.performClick() }!!
            val team = tree(expanded.content()).filterIsInstance<TextView>().first { it.text.toString() == "Team" }
            val pickerTabs = team.parent as LinearLayout
            tree(pickerTabs).filterIsInstance<TextView>().forEachIndexed { i, t ->
                assertNotNull(t.compoundDrawablesRelative[0])
            }
            sheet.addView(detach(pickerTabs), lp(ctx, 5))
            expanded.dismiss()
            title("Widget Pokémon picker · scope")
            sheet.addView(nativeSegments(ctx, listOf("All", "Team", "Box"), listOf(R.drawable.ic_grid, R.drawable.ic_team, R.drawable.ic_item_bag)), lp(ctx, 5))
            title("Pokémon · already has icons")
            val pokemon = LinearLayout(ctx)
            SectionSwitch.draw(ctx, pokemon, TeamSection.entries.map { SectionSwitch.Seg(it.label, it.icon) }, 0, SectionSwitch.Style.TABS) {}
            sheet.addView(pokemon, lp(ctx, 5))
            title("Pokédex · ownership filter")
            sheet.addView(nativeSegments(ctx, listOf("All", "Owned", "Seen", "Not owned"), listOf(R.drawable.ic_grid, R.drawable.ic_ball, R.drawable.ic_ach_eye, R.drawable.ic_ach_lock)), lp(ctx, 5))
            title("Compare · stat groups")
            sheet.addView(nativeSegments(ctx, listOf("Stats", "Base stats"), listOf(R.drawable.ic_stats, R.drawable.ic_dex)), lp(ctx, 5))
            title("Compare · level basis")
            sheet.addView(nativeSegments(ctx, listOf("Current levels", "Same level"), listOf(R.drawable.ic_ach_levels, R.drawable.ic_compare)), lp(ctx, 5))
            title("Note editor")
            sheet.addView(nativeSegments(ctx, listOf("Edit", "Preview"), listOf(R.drawable.ic_edit, R.drawable.ic_ach_eye)), lp(ctx, 5))
            title("Widget settings")
            sheet.addView(nativeSegments(ctx, listOf("Look", "Content"), listOf(R.drawable.ic_palette, R.drawable.ic_study)), lp(ctx, 5))
            title("Help · type chart")
            sheet.addView(nativeSegments(ctx, listOf("Attack", "Defend"), listOf(R.drawable.ic_swords, R.drawable.ic_ach_shield)), lp(ctx, 5))
            title("Settings · section list, already has icons")
            SettingsSection.entries.forEach { s ->
                val row = SettingsHome.row(ctx, s, "") {}
                val image = tree(row).filterIsInstance<ImageView>().first()
                row.minimumHeight = dp(ctx, 48)
                sheet.addView(row, lp(ctx))
            }
            shot(sheet, "o-516-${theme.name.lowercase()}")
            assertEquals("All Settings sections", 15, SettingsSection.entries.size)
            val last = sheet.getChildAt(sheet.childCount - 1)
            assertTrue("Inventory cropped: ${last.bottom} / ${sheet.height}", last.bottom <= sheet.height)
            assertTrue(tree(sheet).filterIsInstance<TextView>().all { t ->
                t.layout == null || (0 until t.layout.lineCount).all { t.layout.getEllipsisCount(it) == 0 }
            })
        }
    }

    @Test fun compareNavigationAtPhoneSmallAndSplitSizes() {
        for (theme in Theme.entries) for (size in listOf(Size.PHONE, Size.SMALL, Size.SPLIT)) {
            val ctx = context(theme, size.hDp, size.wDp)
            val page = ComparePage(ShotCtx(ctx), NoHost, listOf(ComparePick.Owned("t1"), ComparePick.Owned("b3")))
            val root = pageScreen(ctx, page)
            page.render()
            fun tabs() = tree(root).filterIsInstance<TextView>().filter {
                it.text.toString() in listOf("Stats", "Base stats", "Current levels", "Same level")
            }
            assertEquals(4, tabs().size)
            assertTrue(tabs().all { it.compoundDrawablesRelative[0] != null })
            tabs().single { it.text.toString() == "Same level" }.performClick()
            page.render()
            assertTrue(tabs().all { it.compoundDrawablesRelative[0] != null })
            shot(root, "o-516-compare-${size.name.lowercase()}-${theme.name.lowercase()}")
            page.onGone()
        }
    }

    private fun freezeImages(root: View): Int {
        val images = tree(root).filterIsInstance<ImageView>().filter { it.getTag(R.id.ag_tag_sprite) == "item:${StreakFreeze.ITEM}" }
        assertTrue(images.all { it.drawable != null })
        return images.size
    }

    @Test fun freezeContexts520() {
        for (theme in Theme.entries) {
            val ctx = context(theme, 1700)
            ShopCardKit.install()
            val state = GameRepo.state!!
            state.bag.clear(); state.bag[StreakFreeze.ITEM] = 1
            state.mons.forEach { it.heldItem = null }
            val (page, stage) = ShopCardKit.page(ctx, null, bag = true, cash = 12000) {
                state.bag.clear(); state.bag[StreakFreeze.ITEM] = 1
            }
            val sheet = col(ctx).apply { setBackgroundColor(Ui.c(ctx, R.color.ag_base)); setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 16)) }
            sheet.addView(text(ctx, "Bag", 16f, 900), lp(ctx))
            assertEquals(1, freezeImages(stage))
            sheet.addView(detach(stage), LinearLayout.LayoutParams(-1, dp(ctx, 560)))
            val (shopPage, shopStage) = ShopCardKit.page(ctx, null, bag = false, cash = 12000)
            assertEquals(1, freezeImages(shopStage))
            shot(shopStage, "o-520-shop-${theme.name.lowercase()}")
            shopPage.openCard("streak-freeze"); shopStage.render()
            val window = shopPage.itemWindow!!.view
            assertEquals(1, freezeImages(window))
            sheet.addView(text(ctx, "Shop · item card", 16f, 900), lp(ctx, 14))
            sheet.addView(detach(window), LinearLayout.LayoutParams(-1, dp(ctx, 560)).also { it.topMargin = dp(ctx, 6) })
            val saved = GameEvent.StreakSaved(17, 2, 1, "2026-11-03", "2026-11-04")
            val message = AchievementPopup.streakLayer(ctx, saved) {}
            assertEquals(1, freezeImages(message))
            sheet.addView(text(ctx, "Streak saved", 16f, 900), lp(ctx, 16))
            sheet.addView(message, LinearLayout.LayoutParams(-1, dp(ctx, 270)))
            page.onGone(); shopPage.onGone()
            shot(sheet, "o-520-${theme.name.lowercase()}")
            assertTrue("Saved message cropped", sheet.getChildAt(sheet.childCount - 1).bottom <= sheet.height)
        }
    }

    @Test fun guideCardsAndRuleFolds530() {
        for (theme in Theme.entries) for ((index, chapter) in GameGuide.chapters().withIndex()) {
            val ctx = context(theme)
            val page = HelpScreen(ShotCtx(ctx), NoHost, HelpRoute.Guide(chapter.id), false)
            val root = pageScreen(ctx, page)
            val content = getField(page, "content") as LinearLayout
            assertTrue((0 until content.childCount).all { content.getChildAt(it).background != null })
            assertEquals(chapter.id, page.currentGuideChapter)
            val texts = tree(content).filterIsInstance<TextView>()
            if (chapter.visuals.isNotEmpty()) {
                val heading = if (index < 2) "Read the battle panel" else "At a glance"
                assertTrue(texts.any { it.text.toString() == heading })
            }
            GuideLessons.details(chapter).forEach { (label, _) ->
                val head = texts.single { it.text.toString() == "$label  ›" }
                val fold = head.parent as LinearLayout
                assertEquals(View.GONE, fold.getChildAt(1).visibility)
                head.performClick()
                assertEquals(View.VISIBLE, fold.getChildAt(1).visibility)
                head.performClick()
                assertEquals(View.GONE, fold.getChildAt(1).visibility)
            }
            shot(root, "o-530-${index + 1}-${theme.name.lowercase()}")
            page.onGone()
        }
    }
}
