package com.husarp.ankigo.shots

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R28MRarityTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private fun texts(view: android.view.View): List<android.widget.TextView> =
        (if (view is android.widget.TextView) listOf(view) else emptyList()) +
            if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()
    private fun markCount(view: android.view.View) = texts(view).sumOf {
        val text = it.text
        if (text is Spanned) text.getSpans(0, text.length, RarityMark.Mark::class.java).size else 0
    }

    @Test fun mappingAndPreferencePreserveNameStyling() {
        val ctx = Shots.files(paparazzi.context)
        assertTrue(RarityMark.enabled(ctx))
        assertEquals(listOf(null, 1, 2, 3, 4, 1, null, null), Tier.entries.map { RarityMark.tier(it, 499) })
        for (tier in listOf(Tier.NORMAL, Tier.MEGA, Tier.GMAX, null)) {
            assertNull(RarityMark.tier(tier, 499))
            assertEquals(1, RarityMark.tier(tier, 500))
        }
        val original = android.text.SpannableString("♀ ★ Nickname").apply {
            setSpan(ForegroundColorSpan(123), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val marked = RarityMark.name(ctx, original, Tier.MYTHICAL, 499) as Spanned
        assertEquals(1, marked.getSpans(0, marked.length, RarityMark.Mark::class.java).size)
        val gender = marked.getSpans(0, marked.length, ForegroundColorSpan::class.java).single()
        assertEquals(2, marked.getSpanStart(gender))
        assertEquals(123, gender.foregroundColor)
        assertSame(original, RarityMark.name(ctx, original, Tier.NORMAL, 499))
        RarityMark.setEnabled(ctx, false)
        assertFalse(RarityMark.enabled(ctx))
        assertSame(original, RarityMark.name(ctx, original, Tier.MYTHICAL, 499))
    }

    @Test fun detailsRenameKeepsRarityMark() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.scenario = FakeGame.Scenario.MID_GAME
        FakeGame.install(ctx)
        RarityMark.setEnabled(ctx, true)
        val mon = GameRepo.state!!.mons.first()
        mon.tier = Tier.ULTRA
        val dialog = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), GameRepo.data!!, mon) }!!
        fun views(v: android.view.View): List<android.view.View> = listOf(v) +
            if (v is android.view.ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
        val rename = DialogShots.capture {
            views(dialog.content()).single { it.contentDescription == "Rename" }.performClick()
        }!!
        texts(rename.content()).filterIsInstance<android.widget.EditText>().single().setText("New nickname")
        texts(rename.content()).single { it.text.toString() == "Save" }.performClick()
        assertEquals("New nickname", GameRepo.state!!.mon(mon.uid)!!.nickname)
        assertTrue(texts(dialog.content()).any { it.text.toString().contains("New nickname") })
        assertEquals(2, markCount(dialog.content()))
    }

    @Test fun settingIsVisibleAndSearchable() {
        paparazzi.shootPage("m-settings", listOf(Variant("tall", 360, 1600))) { base ->
            val ctx = ShotCtx(base)
            val page = SettingsPage(ctx, NoHost)
            val scene = pageScreen(ctx, page)
            page.open(SettingsSection.BATTLE)
            assertTrue(texts(scene).any { it.text.toString() == "Pokémon rarity marks" })
            assertEquals("Pokémon rarity marks", SettingsCatalog.all.single { it.id == RarityMark.KEY }.title)
            scene
        }
    }

    @Test fun battleAndDetails() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.scenario = FakeGame.Scenario.MID_GAME
            FakeGame.install(ctx)
            for (on in listOf(true, false)) {
                RarityMark.setEnabled(ctx, on)
                val suffix = "${if (on) "on" else "off"}-${theme.name.lowercase()}"
                val panel = BattlePanelView(ctx).apply { renderAll() }
                val arena = panel.arena.apply {
                    setEnemyShown(true)
                    setEnemyInfo("Mewtwo", 50, true, emptyList(), Tier.LEGENDARY)
                    setPlayerInfo("test", "Mew", 45, Tier.MYTHICAL)
                }
                arena.setPlayerName("Mew") // renames keep the current tier
                assertEquals(if (on) 2 else 0, markCount(arena))
                paparazzi.snapshot(panel, "m-battle-$suffix")
                val gallery = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
                    setPadding(24, 24, 24, 24)
                }
                listOf(Triple(Tier.NORMAL, "Pikachu", 320), Triple(Tier.NORMAL, "Snorlax", 540), Triple(Tier.STARTER, "Bulbasaur", 318), Triple(Tier.BABY, "Pichu", 205), Triple(Tier.ULTRA, "Nihilego", 570), Triple(Tier.LEGENDARY, "Mewtwo", 680), Triple(Tier.MYTHICAL, "Mew", 600)).forEach { (tier, name, total) ->
                    gallery.addView(Ui.text(ctx, 20f, 900, com.husarp.ankigo.R.color.ag_text).apply {
                        text = RarityMark.name(ctx, name, tier, total)
                        setPadding(0, 12, 0, 12)
                    })
                }
                paparazzi.snapshot(gallery, "m-tier-mapping-$suffix")
                val mon = GameRepo.state!!.mons.first()
                val data = GameRepo.data!!
                val tierBefore = mon.tier
                try {
                    mon.tier = Tier.ULTRA
                    val dialog = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), data, mon) }!!
                    assertEquals(if (on) 2 else 1, markCount(dialog.content()))
                    paparazzi.snapshot(DialogShots.scene(ctx, dialog), "m-details-$suffix")
                } finally { mon.tier = tierBefore }
                val battle = GameRepo.state!!.battle!!
                val wildTierBefore = battle.tier
                try {
                    battle.tier = Tier.BABY
                    val dialog = DialogShots.capture { assertTrue(BattleDetails.showWild(ctx)) }!!
                    assertEquals(if (on) 2 else 1, markCount(dialog.content()))
                    paparazzi.snapshot(DialogShots.scene(ctx, dialog), "m-wild-details-$suffix")
                } finally { battle.tier = wildTierBefore }
            }
        }
    }
    @Test fun greatPokemonHaveDiamondsInBoxDexAndDetails() {
        paparazzi.shootPage("r30c-dot-box-details", listOf(Variants.PHONE)) { ctx ->
            val mon = GameRepo.state!!.mons.single { it.uid == "b8" } // Starter in the Box
            mon.tier = Tier.STARTER
            val dialog = DialogShots.capture { MonDetail.show(TeamPage(ctx, NoHost), GameRepo.data!!, mon) }!!
            assertEquals(2, markCount(dialog.content()))
            DialogShots.scene(ctx, dialog)
        }
        paparazzi.shootPage("r30c-dot-box-copies", listOf(Variants.PHONE)) { ctx ->
            val data = GameRepo.data!!
            val mons = GameRepo.state!!.mons.filter { it.species == 1 }
            val tile = BoxTiles.build(data, mons, true).single()
            val sheet = BoxCopiesSheet(TeamPage(ctx, NoHost), tile, {}, { _, _, _ -> }).build(600)
            sheet.also { assertEquals(1, markCount(it)) }
        }
        paparazzi.shootPage("r30c-dot-team", listOf(Variants.PHONE)) { ctx ->
            GameRepo.state!!.mons.single { it.uid == "t2" }.tier = Tier.STARTER
            val page = TeamPage(ctx, NoHost).apply { preset(TeamSection.TEAM) }
            pageScreen(ctx, page).also {
                GamePage::class.java.getDeclaredMethod("doRender").apply { isAccessible = true }.invoke(page)
                it.measure(android.view.View.MeasureSpec.makeMeasureSpec(ctx.resources.displayMetrics.widthPixels, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(ctx.resources.displayMetrics.heightPixels, android.view.View.MeasureSpec.EXACTLY))
                it.layout(0, 0, it.measuredWidth, it.measuredHeight)
                assertEquals(3, markCount(it))
            }
        }
        paparazzi.shootPage("r30c-dot-dex", listOf(Variants.PHONE)) { ctx ->
            val page = DexPage(ctx, NoHost)
            setField(page, "filter", DexFilter(query = "Leafling"))
            pageScreen(ctx, page).also {
                GamePage::class.java.getDeclaredMethod("doRender").apply { isAccessible = true }.invoke(page)
                it.measure(android.view.View.MeasureSpec.makeMeasureSpec(ctx.resources.displayMetrics.widthPixels, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(ctx.resources.displayMetrics.heightPixels, android.view.View.MeasureSpec.EXACTLY))
                it.layout(0, 0, it.measuredWidth, it.measuredHeight)
                assertEquals(1, markCount(it))
            }
        }
    }

    @Test fun strongBattleMarkSurvivesRefreshRenameAndSetting() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val arena = BattlePanelView(ctx).arena
        arena.setEnemyInfo("Strong", 50, false, emptyList(), Tier.NORMAL, 500)
        arena.setPlayerInfo("test", "Strong", 50, Tier.GMAX, 500)
        assertEquals(2, markCount(arena))
        arena.setPlayerName("Renamed")
        val refresh = ArenaView::class.java.getDeclaredMethod("refreshRarity").apply { isAccessible = true }
        refresh.invoke(arena)
        assertEquals(2, markCount(arena))
        RarityMark.setEnabled(ctx, false)
        refresh.invoke(arena)
        assertEquals(0, markCount(arena))
        RarityMark.setEnabled(ctx, true)
        refresh.invoke(arena)
        assertEquals(2, markCount(arena))
    }

}
