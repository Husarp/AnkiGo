package com.husarp.ankigo.shots

import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executor

class R40BBackgroundShots {
    @get:Rule val paparazzi = Shots.rule()

    @Test fun settingsBackgroundTypesInBothThemes() {
        val pool = BackdropPrefs.previewPool
        BackdropPrefs.previewPool = Executor { it.run() }
        try {
            for (theme in Theme.entries) {
                paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
                val ctx = ShotCtx(Shots.files(paparazzi.context))
                FakeGame.install(ctx)
                val settings = SettingsPage(ctx, NoHost)
                settings.root
                settings.open(SettingsSection.BACKGROUNDS)
                fun loadPreviews(v: View) {
                    if (v is BackdropPrefs.Preview) {
                        val id = v.contentDescription.toString().replace(' ', '-')
                        ctx.assets.open("backdrop-thumbs/$id.webp").use { v.setImageBitmap(BitmapFactory.decodeStream(it)) }
                    }
                    if (v is ViewGroup) for (i in 0 until v.childCount) loadPreviews(v.getChildAt(i))
                }
                loadPreviews(settings.root)
                paparazzi.snapshot(settings.root, "b-676-settings-${theme.name.lowercase()}", 1000)
                val page = BackdropPrefs.page(ctx)
                fun texts(v: View): List<String> = (if (v is TextView) listOf(v.text.toString()) else emptyList()) +
                    if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()
                val cards = (0 until page.childCount).map { page.getChildAt(it) }
                for (bg in BackdropCatalog.ALL) {
                    val title = bg.id.replace('-', ' ').replaceFirstChar { it.titlecase() }
                    val card = cards.single { title in texts(it) }
                    val labels = texts(card)
                    if (bg.id == BackdropCatalog.SHRINE) assertTrue("Legendary & Mythical Pokémon only" in labels)
                    else for (type in BackdropCatalog.typesFor(bg.id)) assertTrue("${bg.id}: $type", labels.any { it.contains(type.name.lowercase().replaceFirstChar(Char::titlecase)) })
                }
                val lava = cards.single { "Lava field" in texts(it) }
                loadPreviews(page)
                val scroll = object : ScrollView(ctx) {
                    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                        super.onLayout(changed, l, t, r, b)
                        scrollTo(0, lava.top)
                    }
                }.apply { addView(page); setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base)) }
                paparazzi.snapshot(scroll, "b-676-lava-types-${theme.name.lowercase()}", 1000)
                assertEquals(lava.top, scroll.scrollY)
                settings.onGone()
            }
        } finally { BackdropPrefs.previewPool = pool }
    }
}
