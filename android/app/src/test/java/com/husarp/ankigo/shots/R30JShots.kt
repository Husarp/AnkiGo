package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.ui.*
import org.junit.*
import org.junit.Assert.*

class R30JShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun ctx(theme: Theme, width: Int = 360): android.content.Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Variant("j$width", width, 820).device(theme))
        return Shots.files(paparazzi.context).also { FakeGame.install(it); ShopCardKit.install() }
    }
    @After fun clean() { Pickers.interceptApp = null }

    @Test fun soundTargetsTrackPlaybackWithoutChangingTheSwitch() {
        for (theme in Theme.entries) {
            val c = ctx(theme)
            var playback: ((Boolean) -> Unit)? = null
            var toggles = 0
            val row = Form.soundRow(c, "Answer buttons", "Again, Hard, Good, Easy", true,
                { playing -> playback = playing }) { toggles++ }
            val stage = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(c, R.color.ag_base))
                addView(Form.cardOf(c, listOf(Form.cardTitle(c, R.drawable.ic_sound, "UI sounds"), row)))
            }
            paparazzi.snapshot(stage, "j-469-idle-${theme.name.lowercase()}")
            val play = all(row).single { it.contentDescription == "Hear Answer buttons" }
            assertEquals(Ui.dp(c, 60), play.width); assertEquals(Ui.dp(c, 64), play.height)
            play.performClick(); assertEquals(0, toggles)
            assertFalse("Waiting or a silent preview must not claim playback", play.isActivated)
            playback!!(true)
            assertTrue(play.isActivated)
            assertTrue(all(row).filterIsInstance<TextView>().any { it.text == "♪ Playing…" })
            paparazzi.snapshot(stage, "j-469-playing-${theme.name.lowercase()}")
            val previousPlayback = playback!!
            play.performClick()
            assertFalse("A new pending or failed preview must clear the previous playing state", play.isActivated)
            previousPlayback(true)
            assertFalse("An old start callback cannot reactivate the new request", play.isActivated)
            playback!!(true)
            previousPlayback(false)
            assertTrue("An old finish callback cannot clear the new playback", play.isActivated)
            playback!!(false)
            assertFalse(play.isActivated)
            assertFalse(all(row).filterIsInstance<TextView>().any { it.text.contains("Played") })
            assertTrue(all(row).filterIsInstance<TextView>().any { it.text == "Tap to play" })
            row.performClick(); assertEquals(1, toggles)
            com.husarp.ankigo.sound.SoundPrefs.setVolume(c, com.husarp.ankigo.sound.SoundChannel.BUTTONS, 0)
            assertEquals("Volume is 0", com.husarp.ankigo.sound.GameSound.preview(c, com.husarp.ankigo.sound.SoundId.GOOD) {
                fail("A muted preview reported playback")
            })
        }
    }

    @Test fun settingsSoundPage() {
        for (theme in Theme.entries) {
            val c = ctx(theme)
            val stage = MotionScrollView(c).apply {
                setBackgroundColor(Ui.c(c, R.color.ag_base))
                addView(soundCard(c))
            }
            paparazzi.snapshot(stage, "j-469-settings-${theme.name.lowercase()}")
            assertEquals(7, all(stage).count { it.contentDescription?.startsWith("Hear ") == true })
        }
    }

    @Test fun holdsHaveVisibleFillAndFixedReadableLabels() {
        for (theme in Theme.entries) {
            val c = ctx(theme)
            val idle = Ui.c(c, R.color.ag_hold_idle)
            val fill = Ui.c(c, R.color.ag_hold_fill)
            val ink = Ui.c(c, R.color.ag_hold_ink)
            assertTrue(ColorUtils.calculateContrast(fill, idle) >= 3)
            assertTrue(ColorUtils.calculateContrast(ink, fill) >= 3)
            assertTrue(ColorUtils.calculateContrast(ink, idle) >= 3)
            val dialog = DialogShots.capture { Pickers.hold(c, "Ready to evolve", "Pikachu can use your Thunder Stone.\nThis can't be undone.", "Evolve") {} }!!
            val go = dialog.button(AppDialog.BUTTON_POSITIVE)!!
            for (percent in listOf(0, 50, 100)) {
                go.background.level = percent * 100
                assertEquals("Hold to evolve", go.text.toString())
                paparazzi.snapshot(dialog.content(), "j-476-hold-$percent-${theme.name.lowercase()}")
            }
            dialog.dismiss()
        }
    }

    @Test fun bagSideListReclaimsSpaceAndCountsEachOwnedCopyOnce() {
        for (theme in Theme.entries) for (width in listOf(320, 360)) {
            val c = ctx(theme, width)
            val st = GameRepo.state!!
            st.bag.clear(); st.tms.clear(); st.mons.forEach { it.heldItem = null }
            st.bag["potion"] = 3; st.bag["poke-ball"] = 7
            st.mons.first().heldItem = "amulet-coin"; st.bag["amulet-coin"] = 1
            st.tms.add("thunderbolt")
            val page = ShopPage(c, NoHost, bagOnly = true)
            val bag = BagActions(page, MonActions(page), page)
            val body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.c(c, R.color.ag_base)) }
            fun render() { body.removeAllViews(); bag.fill(body) }
            fun snap(state: String) = paparazzi.snapshot(body, "j-477-bag-$width-$state-${theme.name.lowercase()}")
            render(); snap("collapsed")
            assertFalse(all(body).any { it.tag == "bag-categories" })
            assertEquals(all(body).single { it.tag == "bag-body" }.width, all(body).single { it.tag == "bag-results" }.width)
            all(body).single { it.contentDescription == "Expand categories" }.performClick(); render(); snap("open")
            val categories = all(body).single { it.tag == "bag-categories" } as LinearLayout
            assertEquals(9, categories.childCount)
            assertEquals(listOf("All items", "13"), all(categories.getChildAt(0)).filterIsInstance<TextView>().map { it.text.toString() }.takeLast(2))
            assertTrue(all(body).single { it.tag == "bag-results" }.width < body.width)
            all(body).single { it.contentDescription == "Healing category" }.performClick(); render()
            assertTrue(all(body).filterIsInstance<TextView>().any { it.text == "Potion" })
            assertFalse(all(body).filterIsInstance<TextView>().any { it.text == "Poké Ball" })
            all(body).single { it.contentDescription == "Collapse categories" }.performClick(); render(); snap("healing-collapsed")
            assertFalse(all(body).any { it.tag == "bag-categories" })
            assertEquals(all(body).single { it.tag == "bag-body" }.width, all(body).single { it.tag == "bag-results" }.width)
            page.onHide(); page.onGone()
        }
    }

    @Test fun battleBagUsesTheSameCollapsibleSideList() {
        for (theme in Theme.entries) {
            val c = ctx(theme)
            val panel = BattlePanelView(c).apply { renderAll() }
            var dialog: AppDialog? = null
            Pickers.interceptApp = { dialog = it }
            BattleBag.show(c) {}
            val content = dialog!!.content()
            val page = all(content).mapNotNull { it.context as? ShopPage }.first()
            fun render() = ShopPage::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(page)
            render()
            val stage = FrameLayout(c).apply {
                addView(panel, FrameLayout.LayoutParams(-1, -1))
                addView(View(c).apply { setBackgroundColor(0x77000000) }, FrameLayout.LayoutParams(-1, -1))
                addView(content, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER))
            }
            paparazzi.snapshot(stage, "j-477-battle-collapsed-${theme.name.lowercase()}")
            assertFalse(all(content).filterIsInstance<TextView>().any { it.text == "Shop" })
            all(content).single { it.contentDescription == "Expand categories" }.performClick(); render()
            paparazzi.snapshot(stage, "j-477-battle-open-${theme.name.lowercase()}")
            assertTrue(all(content).any { it.contentDescription == "Healing category" })
            dialog!!.dismiss(); page.onHide(); page.onGone()
        }
    }
}
