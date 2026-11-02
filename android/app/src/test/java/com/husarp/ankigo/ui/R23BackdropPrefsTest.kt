package com.husarp.ankigo.ui

import android.content.Context
import android.widget.Switch
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.shots.Shots
import com.husarp.ankigo.shots.ShotCtx
import org.junit.Rule
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.Sprites
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class R23BackdropPrefsTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private lateinit var ctx: Context
    @Before fun reset() { ctx = ShotCtx(Shots.files(paparazzi.context)); ctx.getSharedPreferences("ankigo_ui", 0).edit().clear().commit() }
    @Test fun defaultOnAndChoicesPersist() {
        assertTrue(BackdropPrefs.disabled(ctx).isEmpty())
        assertTrue(BackdropPrefs.setEnabled(ctx, "beach", false))
        assertTrue("beach" in BackdropPrefs.disabled(ctx))
        assertTrue(BackdropPrefs.setEnabled(ctx, "beach", true))
        assertTrue(BackdropPrefs.disabled(ctx).isEmpty())
    }
    @Test fun staleIdsDoNotBypassLastEnabledGuard() {
        ctx.getSharedPreferences("ankigo_ui",0).edit().putStringSet("disabledBackdrops", setOf("obsolete")).commit()
        BackdropCatalog.ALL.dropLast(1).forEach { assertTrue(BackdropPrefs.setEnabled(ctx,it.id,false)) }
        assertFalse(BackdropPrefs.setEnabled(ctx,BackdropCatalog.ALL.last().id,false))
        assertFalse(BackdropPrefs.setEnabled(ctx,"missing",true))
        assertEquals(1,BackdropCatalog.ALL.count { it.id !in BackdropPrefs.disabled(ctx) })
    }
    @Test fun pageHasEveryThumbnailAndSwitchAndRestoresLastSwitch() {
        val page = BackdropPrefs.page(ctx)
        val switches = mutableListOf<Switch>()
        fun walk(v: android.view.View) {
            if (v is Switch) switches.add(v)
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(page)
        assertEquals(BackdropCatalog.ALL.size,switches.size)
        assertTrue(switches.all { it.isChecked })
        paparazzi.snapshot(android.widget.ScrollView(ctx).apply { addView(page) }, "r23-backgrounds")
        switches.forEach { it.isChecked=false }
        assertTrue(switches.last().isChecked)
        assertEquals(BackdropCatalog.ALL.size - 1,BackdropPrefs.disabled(ctx).size)
    }
    @Test fun allBundledArtDecodesAndInstallsUnderItsRevision() {
        // Layoutlib supports opening assets, but does not implement AssetManager.list.
        val names = java.io.File("src/main/assets/backdrops").list()!!.toSet()
        val bundledIds = BackdropCatalog.GENERAL + setOf("forest-tech-b", "crystal-storm", "flower-field", "poison-marsh", "tropical-cove", "coral-lagoon", "sandbar-isle", "reef-bay")
        assertEquals(bundledIds.map { Backdrops.fileName(BackdropCatalog.byId(it)!!) }.toSet(), names)
        for (bg in BackdropCatalog.ALL) {
            ctx.assets.open("backdrop-thumbs/${bg.id}.webp").use { assertNotNull(android.graphics.BitmapFactory.decodeStream(it)) }
            if (Backdrops.fileName(bg) !in names) continue
            val file=Backdrops.file(Sprites.dir(ctx),bg)
            file.delete()
            Backdrops.installBundled(ctx,Sprites.dir(ctx),bg)
            assertTrue("Installed ${bg.id}", file.isFile)
            val bytes = file.readBytes()
            ctx.assets.open("backdrops/${Backdrops.fileName(bg)}").use { assertArrayEquals(it.readBytes(), bytes) }
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!
            assertEquals(bg.w,bmp.width);assertEquals(bg.h,bmp.height)
        }
    }
}
