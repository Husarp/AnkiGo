package com.husarp.ankigo.shots

import android.widget.ScrollView
import android.widget.ImageView
import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executor

class R26JSettingsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @Test fun backgroundsLoadOnlyInViewport() {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        val pool = BackdropPrefs.previewPool
        var requests = 0
        BackdropPrefs.previewPool = Executor { requests++; it.run() }
        try {
            val page = BackdropPrefs.page(ctx)
            val images = mutableListOf<ImageView>()
            fun walk(v: View) {
                if (v is BackdropPrefs.Preview) images.add(v)
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(page)
            assertEquals(51, images.size)
            assertEquals(0, requests)
            var firstLoaded = false
            var lastLoaded = false
            val scroll = object : ScrollView(ctx) {
                override fun dispatchDraw(canvas: android.graphics.Canvas) {
                    super.dispatchDraw(canvas)
                    firstLoaded = images.first().drawable != null
                    lastLoaded = images.last().drawable != null
                }
            }.apply { addView(page) }
            paparazzi.gif(scroll, "r26j-backgrounds-top", 0, 1000, 5)
            assertTrue("Only visible previews decode: $requests", requests in 1..4)
            assertTrue(firstLoaded)
            assertFalse(lastLoaded)
            scroll.scrollTo(0, page.height)
            paparazzi.gif(scroll, "r26j-backgrounds-bottom", 0, 1000, 5)
            assertTrue(lastLoaded)
            assertFalse(firstLoaded)
            assertTrue(requests < 10)
        } finally { BackdropPrefs.previewPool = pool }
    }

    @Test fun appReturnAndSectionMemory() {
        val ctx = ShotCtx(Shots.files(paparazzi.context))
        FakeGame.install(ctx)
        ctx.getSharedPreferences("settings_return", 0).edit().clear().commit()
        val clock = SettingsReturn.now
        var time = 100_000L
        SettingsReturn.now = { time }
        try {
            val page = SettingsPage(ctx, NoHost)
            page.root
            page.open(SettingsSection.BACKGROUNDS)
            page.onHide()
            assertEquals(SettingsSection.BACKGROUNDS, SettingsPage(ctx, NoHost).section)
            time += 120_000L // An external-app absence can exceed the one-minute reopen timeout.
            page.onAppReturned()
            page.onShow()
            assertEquals(SettingsSection.BACKGROUNDS, page.section)
            page.leavingSettings()
            page.onHide()
            time += 60_000L
            assertNull(SettingsPage(ctx, NoHost).section)
            page.onAppReturned() // Another screen leaving the app must not extend Settings' timeout.
            page.onShow()
            assertNull(page.section)
            page.onHide()
            assertNull(SettingsPage(ctx, NoHost).section)
            page.onGone()
        } finally { SettingsReturn.now = clock }
    }


    @Test fun talkBackDefaultAndExplicitChoices() {
        val ctx = ShotCtx(Shots.files(paparazzi.context))
        FakeGame.install(ctx)
        val prefs = ctx.getSharedPreferences("study", 0)
        for (choice in listOf<Boolean?>(null, true, false)) {
            prefs.edit().remove("speakBattle").commit()
            if (choice != null) prefs.edit().putBoolean("speakBattle", choice).commit()
            val page = SettingsPage(ctx, NoHost)
            page.root
            page.open(SettingsSection.STUDY)
            fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
                (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
            val title = descendants(page.root).filterIsInstance<android.widget.TextView>()
                .first { it.text.toString() == "Read battle log with TalkBack" }
            var row = title.parent as ViewGroup
            while (descendants(row).none { it is android.widget.Switch }) row = row.parent as ViewGroup
            val toggle = descendants(row).filterIsInstance<android.widget.Switch>().first()
            assertEquals(choice ?: false, toggle.isChecked)
            assertEquals(choice != null, prefs.contains("speakBattle"))
            page.onGone()
        }
    }

    @Test fun sharpBundledPreviews() {
        for (bg in BackdropCatalog.ALL) {
            paparazzi.context.assets.open("backdrop-thumbs/${bg.id}.webp").use {
                val bitmap = android.graphics.BitmapFactory.decodeStream(it)!!
                assertTrue("${bg.id} is sharp", bitmap.width >= 600)
            }
        }
    }
}
