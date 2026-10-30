package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R22HStudyShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun independentSettings() {
        var sent: android.content.res.Configuration? = null
        val capture = object : android.content.ContextWrapper(Shots.files(paparazzi.context)) {
            override fun createConfigurationContext(config: android.content.res.Configuration): android.content.Context {
                sent = android.content.res.Configuration(config)
                return this
            }
        }
        val ctx = ShotCtx(capture)
        assertEquals(DeckListLook.TODAY, DeckListLook.get(ctx))
        ctx.getSharedPreferences("study", 0).edit().putInt("zoom", 130).apply()
        assertEquals(130, TextSizes.get(ctx, "card"))
        TextSizes.put(ctx, "app", 120)
        TextSizes.put(ctx, "battle", 150)
        assertEquals(120, TextSizes.get(ctx, "app"))
        assertEquals(130, TextSizes.get(ctx, "card"))
        AppLook.wrap(ctx)
        assertEquals(ctx.resources.configuration.fontScale * 1.2f, sent!!.fontScale, .001f)
        TextSizes.battleContext(ctx).resources
        assertEquals(ctx.resources.configuration.fontScale * 1.5f / 1.2f, sent!!.fontScale, .001f)
        TextSizes.put(ctx, "battle", 999)
        assertEquals(150, TextSizes.get(ctx, "battle"))
        for (look in DeckListLook.entries) {
            DeckListLook.put(ctx, look)
            assertEquals(look, DeckListLook.get(ctx))
        }
    }

    @Test fun battleTextContextRetainsItsActivityForDialogs() {
        var requested: android.content.res.Configuration? = null
        val capture = object : android.content.ContextWrapper(Shots.files(paparazzi.context)) {
            override fun createConfigurationContext(config: android.content.res.Configuration): android.content.Context {
                requested = android.content.res.Configuration(config)
                return this
            }
        }
        val activity = attach(android.app.Activity(), ShotCtx(capture))
        TextSizes.put(activity, "app", 100)
        TextSizes.put(activity, "battle", 150)
        val battle = TextSizes.battleContext(activity)
        assertSame(activity, Ui.findActivity(battle))
        assertSame(activity.theme, battle.theme)
        battle.resources
        assertEquals(activity.resources.configuration.fontScale * 1.5f, requested!!.fontScale, .001f)
        assertSame(activity, Ui.findActivity(AppDialog.Builder(battle).setTitle("Battle").create().context))
    }

    @Test fun previews() {
        for (kind in listOf("app", "battle", "card")) for (percent in (if (kind == "app") listOf(100) else listOf(80, 150))) {
            paparazzi.shootWith("r22h-preview-$kind-$percent", listOf(Size.PHONE)) { base ->
                val ctx = ShotCtx(base)
                TextSizes.put(ctx, kind, percent)
                val page = SettingsPage(ctx, NoHost)
                call(page, "textPreviewContent", kind) as android.view.View
            }
        }
    }

    @Test fun lookControls() {
        paparazzi.shootPage("r22h-look-controls", listOf(Variant("tall", 360, 2300))) { base ->
            val ctx = ShotCtx(base)
            val page = SettingsPage(ctx, NoHost)
            val root = pageScreen(ctx, page)
            page.open(SettingsSection.LOOK)
            root
        }
    }

    @Test fun battlePanelDefault() {
        paparazzi.shootWith("r22h-battle-default", listOf(Size.SPLIT)) { ctx ->
            BattlePanelView(ctx).apply { renderAll() }
        }
    }

    @Test fun deckLooks() {
        for (look in DeckListLook.entries) paparazzi.shootWith("r22h-decks-${look.name}", listOf(Size.PHONE, Size.SPLIT)) {
            val ctx = ShotCtx(it)
            DeckListLook.put(ctx, look)
            val built = StudyDeckKit.compose(ctx)
            setField(built.home, "decks", (StudyDeckKit.decks + com.husarp.ankigo.Anki.Deck(10, "Geography::Capitals::Asia", 1, 6, 2, false)).sortedBy { d -> d.name })
            setField(built.home, "expanded", setOf(1L, 2L, 4L))
            call(built.home, "render")
            built.root
        }
    }
}
