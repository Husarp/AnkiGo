package com.husarp.ankigo.shots

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R34EHeldItemsShots {
    @get:Rule val paparazzi = Shots.rule()
    @After fun reset() { ItemWindow.interceptShow = null; Pickers.interceptApp = null }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun setup(theme: Theme): TeamPage {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        GameRepo.state!!.mons.forEach { it.heldItem = null; it.heldItem2 = null }
        return TeamPage(ctx, NoHost).also { page ->
            for (section in DetailSection.entries) AppPrefs.setDetailOpen(page, section.key, section == DetailSection.HELD_ITEM)
        }
    }

    @Test fun detailsShowLockedSecondEmptySecondAndTwoTappableItemsInBothThemes() {
        for (theme in Theme.entries) for (kind in listOf("locked-one", "unlocked-one", "two")) {
            val page = setup(theme)
            val mon = GameRepo.state!!.main!!.apply {
                heldItem = "lucky-egg"; heldItem2 = if (kind == "two") "everstone" else null
                bondPoints = if (kind == "locked-one") 799 else 800
            }
            val dialog = DialogShots.capture { MonDetail.show(page, GameRepo.data!!, mon) }!!
            val section = all(dialog.content()).single { it.tag == DetailSection.HELD_ITEM }
            val labels = texts(section)
            assertEquals(kind == "locked-one", "Second item at Bond ♥5" in labels)
            assertEquals(kind != "locked-one", "Item 2" in labels)
            assertEquals(if (kind == "two") 2 else 1, all(section).count { it.contentDescription?.contains("Tap for what it does") == true })
            assertEquals(kind == "unlocked-one", "Give item" in labels)
            paparazzi.snapshot(DialogShots.scene(page, dialog), "e-details-$kind-${theme.name.lowercase()}")
            dialog.dismiss(); page.onGone()
        }
    }

    @Test fun givePickerShowsCapacityAndFillsSecondSlotWithoutReturningFirstItem() {
        for (theme in Theme.entries) for (unlocked in listOf(false, true)) {
            val page = setup(theme); val s = GameRepo.state!!
            val mon = s.main!!.apply { heldItem = "everstone"; bondPoints = if (unlocked) 800 else 799 }
            s.bag.clear(); s.bag["lucky-egg"] = 1; s.bag["everstone"] = 1
            val dialog = DialogShots.capture { MonActions(page).giveItem(mon) }!!
            val labels = texts(dialog.content())
            assertTrue(labels.any { if (unlocked) "1/2 items" in it else "Second item at Bond ♥5" in it })
            val list = all(dialog.content()).filterIsInstance<ListView>().single()
            assertEquals(1, list.adapter.count)
            paparazzi.snapshot(DialogShots.scene(page, dialog), "e-give-${if (unlocked) "second" else "locked"}-${theme.name.lowercase()}")
            list.performItemClick(list.adapter.getView(0, null, list), 0, 0)
            assertEquals(if (unlocked) listOf("everstone", "lucky-egg") else listOf("lucky-egg"), mon.heldItems)
            assertEquals(if (unlocked) 1 else 2, s.count("everstone")); assertEquals(0, s.count("lucky-egg"))
            page.onGone()
        }
    }

    @Test fun takingOffSecondItemRefreshesOpenDetailsAndKeepsTheFirst() {
        for (theme in Theme.entries) {
            val page = setup(theme); val s = GameRepo.state!!
            val mon = s.main!!.apply { heldItem = "lucky-egg"; heldItem2 = "everstone"; bondPoints = 800 }
            val dialog = DialogShots.capture { MonDetail.show(page, GameRepo.data!!, mon) }!!
            val scene = DialogShots.scene(page, dialog)
            var item: ItemWindow? = null
            ItemWindow.interceptShow = { item = it }
            all(scene).single { it.contentDescription?.contains("Everstone,") == true && it.contentDescription?.contains("Tap for what it does") == true }.performClick()
            val window = checkNotNull(item)
            val before = s.count("everstone")
            var removed = false
            val wrapper = object : FrameLayout(page) {
                override fun dispatchDraw(canvas: Canvas) {
                    if (!removed) {
                        removed = true
                        all(window.view).single { it.contentDescription == "Take off" }.performClick()
                        (GameRepo::class.java.getDeclaredField("notifyRunnable").apply { isAccessible = true }.get(GameRepo) as Runnable).run()
                        assertEquals(listOf("lucky-egg"), mon.heldItems)
                        assertEquals(before + 1, s.count("everstone"))
                        val section = all(scene).single { it.tag == DetailSection.HELD_ITEM }
                        assertTrue("Give item" in texts(section)); assertFalse("Everstone" in texts(section))
                        measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
                        layout(left, top, right, bottom)
                    }
                    super.dispatchDraw(canvas)
                }
            }.apply { addView(scene) }
            paparazzi.snapshot(wrapper, "e-details-second-taken-${theme.name.lowercase()}", 1000)
            assertTrue(removed)
            window.dismiss(); dialog.dismiss(); page.onGone()
        }
    }

    @Test fun wornBagIncludesBothItemsEvenWhenNeitherHasAFreeCopy() {
        for (theme in Theme.entries) {
            val teamPage = setup(theme); val state = GameRepo.state!!
            state.main!!.apply { heldItem = "lucky-egg"; heldItem2 = "everstone"; bondPoints = 800 }
            state.bag.clear(); state.bag["potion"] = 1
            val page = ShopPage(teamPage, NoHost)
            val bag = BagActions(page, MonActions(page), page)
            BagActions::class.java.getDeclaredField("heldOnly").apply { isAccessible = true }.setBoolean(bag, true)
            val body = android.widget.LinearLayout(page).apply { orientation = android.widget.LinearLayout.VERTICAL }
            bag.fill(body)
            val labels = texts(body)
            assertTrue("Lucky Egg" in labels); assertTrue("Everstone" in labels); assertFalse("Potion" in labels)
            paparazzi.snapshot(scrollOnBase(page, body), "e-bag-worn-${theme.name.lowercase()}")
            page.onGone(); teamPage.onGone()
        }
    }

}
