package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.R
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** PLAN 479: retained Bond design previews; battle chips now use the real implementation. */
class R30RDesignShots {
    @get:Rule val paparazzi = Shots.rule(useDeviceResolution = true)
    private enum class Preview { TODAY, A, B, C, D }
    private val options = listOf(Preview.TODAY, Preview.A, Preview.B, Preview.C)
    private val savedStore = DetailSections.storeOf

    @After fun reset() { DetailSections.storeOf = savedStore; BattleShotKit.reset() }

    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun field(v: Any, name: String): View = v.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(v) as View }
    private fun label(c: Context, value: String, sp: Float = 12f, weight: Int = 800, tone: Int = R.color.ag_text) = Ui.text(c, sp, weight, tone).apply {
        text = value; includeFontPadding = false
        if (tone == R.color.ag_pink) setTextColor(bondInk(c))
    }
    private fun bondInk(c: Context): Int = if (StatPill.isDark(c)) 0xFFF5B2DC.toInt() else 0xFFA93479.toInt()
    private fun col(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    private fun row(c: Context) = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
    private fun icon(c: Context, res: Int, tone: Int? = null) = ImageView(c).apply {
        setImageResource(res); if (tone != null) setColorFilter(tone)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun install(theme: Theme): Context {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
        val c = Shots.files(paparazzi.context)
        FakeGame.install(c)
        // Existing local design assets, never a network request or repository art.
        val source = File("/home/coder/code/projects/AnkiGo/docs/design/plan-kit/sprites")
        for (side in listOf("front", "back", "static")) for (num in listOf(25, 54, 133, 700)) {
            val asset = File(source, "${if (side == "static") "front" else side}/$num.png")
            if (asset.exists()) File(Sprites.dir(c), "$side/$num.png").also { it.parentFile!!.mkdirs(); asset.copyTo(it, overwrite = true) }
        }
        for (id in listOf("lucky-egg", "poke-ball", "great-ball", "ultra-ball")) {
            val asset = File(source, "items/$id.png")
            if (asset.exists()) asset.copyTo(File(Sprites.dir(c), "item/$id.png"), overwrite = true)
        }
        c.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val battle = GameRepo.state!!.battle!!
        battle.enemy.volatiles["confusion"] = 3
        battle.enemy.boosts[0] = 2; battle.enemy.boosts[1] = -1
        battle.player.volatiles["leechseed"] = 1; battle.player.boosts[4] = -1
        return c
    }

    private fun bondBody(c: Context, mon: Mon, preview: Preview): View {
        val original = BondSection.body(c, mon) as LinearLayout
        val ball = all(original).filterIsInstance<ImageView>().single()
        (ball.parent as ViewGroup).removeView(ball)
        val bar = all(original).filterIsInstance<BarView>().single()
        (bar.parent as ViewGroup).removeView(bar)
        val level = Bond.level(mon.bondPoints)
        return col(c).apply {
            tag = "r30-bond-body"
            addView(row(c).apply {
                val ballColumn = col(c).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(ball, LinearLayout.LayoutParams(Ui.dp(c, 72), Ui.dp(c, 72)))
                    if (preview == Preview.A) addView(label(c, "Level $level", 14f, 900, R.color.ag_pink), LinearLayout.LayoutParams(-2, -2).also { it.topMargin = Ui.dp(c, 4) })
                }
                addView(ballColumn)
                addView(col(c).apply {
                    addView(label(c, "Growing close", 18f, 900))
                    addView(label(c, "${mon.bondPoints} total points", 12f, 700, R.color.ag_sub0), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 5) })
                }, LinearLayout.LayoutParams(0, -2, 1f).also { it.marginStart = Ui.dp(c, 14) })
            })
            addView(label(c, "34 / 250 points to Level 4", 12f, 800, R.color.ag_sub1), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 12) })
            addView(bar, LinearLayout.LayoutParams(-1, Ui.dp(c, 7)).also { it.topMargin = Ui.dp(c, 7) })
            addView(label(c, "CURRENT PERKS", 10f, 900, R.color.ag_sub0), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 18) })
            addView(label(c, "+3 coins per win  ·  +15% XP", 12f, 800), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 7) })
        }
    }

    private fun details(c: Context, mon: Mon, preview: Preview, folded: Boolean = false, requirements: Boolean = false): View {
        DetailSections.storeOf = { object : DetailOpenStore {
            override fun get(key: String): Boolean = (key == "bond" && !folded) || (key == "evolution" && requirements)
            override fun set(key: String, open: Boolean) = Unit
        } }
        val dialog = DialogShots.capture { MonDetail.show(c, GameRepo.data!!, mon) }!!
        val scene = DialogShots.scene(c, dialog)
        val card = all(scene).filterIsInstance<LinearLayout>().single { it.tag == DetailSection.BOND }
        if (preview != Preview.TODAY) {
            val header = card.getChildAt(0) as LinearLayout
            val titleFlow = header.getChildAt(1) as FlowRow
            val title = titleFlow.getChildAt(0) as TextView
            val summary = header.getChildAt(2) as TextView
            summary.text = "Level ${Bond.level(mon.bondPoints)}"
            summary.setTextColor(bondInk(c))
            when (preview) {
                Preview.B -> { title.text = "BOND · Level 3"; title.textSize = 13f }
                Preview.C -> titleFlow.addView(label(c, "Level 3", 12f, 900, R.color.ag_pink).apply {
                    background = Ui.rounded(ColorUtils.blendARGB(Ui.c(c, R.color.ag_mantle), Ui.c(c, R.color.ag_pink), 0.12f), 8, c)
                    setPadding(Ui.dp(c, 7), Ui.dp(c, 4), Ui.dp(c, 7), Ui.dp(c, 4))
                })
                Preview.D -> { summary.visibility = View.VISIBLE; summary.textSize = 14f; summary.typeface = Ui.font(c, 900) }
                else -> Unit
            }
            if (!folded) {
                val holder = card.getChildAt(1) as FrameLayout
                holder.removeAllViews()
                holder.addView(bondBody(c, mon, preview).apply { setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), Ui.dp(c, 16)) })
            }
        }
        return object : FrameLayout(c) {
            init { addView(scene, LayoutParams(-1, -1)) }
            override fun dispatchDraw(canvas: Canvas) {
                all(scene).filterIsInstance<ScrollView>().maxByOrNull { it.getChildAt(0)?.height ?: 0 }?.let { scroll ->
                    val rect = Rect(); card.getDrawingRect(rect); scroll.offsetDescendantRectToMyCoords(card, rect)
                    // The complete Bond section and following Info row, at an ordinary details scroll position.
                    scroll.scrollTo(0, (scroll.scrollY + rect.bottom - scroll.height + Ui.dp(c, 58)).coerceAtLeast(0))
                }
                super.dispatchDraw(canvas)
            }
        }
    }

    @Test fun bondPlacements() {
        for (theme in Theme.entries) for (preview in Preview.entries) {
            val c = install(theme)
            val mon = GameRepo.state!!.main!!; mon.bondPoints = 284
            val scene = details(c, mon, preview)
            paparazzi.snapshot(scene, "r30r-bond-${preview.name.lowercase()}-${theme.name.lowercase()}", 1000)
            if (preview != Preview.TODAY) {
                val body = all(scene).single { it.tag == "r30-bond-body" }
                val rect = Rect(); assertTrue(body.getGlobalVisibleRect(rect)); assertEquals("Whole Bond section fits", body.height, rect.height())
                assertEquals("Filling ball retained", 1, all(body).filterIsInstance<ImageView>().size)
                val text = all(scene).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }.map { it.text.toString() }
                assertTrue(text.any { it.contains("Level 3") })
                assertFalse(all(body).filterIsInstance<TextView>().any { it.text.contains("♥") || it.text.contains("Next ·") || it.text.contains("decay") })
            }
        }
    }

    @Test fun foldedSummary() {
        for (theme in Theme.entries) for (preview in listOf(Preview.TODAY, Preview.D)) {
            val c = install(theme)
            val mon = GameRepo.state!!.main!!; mon.bondPoints = 284
            val scene = details(c, mon, preview, folded = true)
            paparazzi.snapshot(scene, "r30r-summary-${preview.name.lowercase()}-${theme.name.lowercase()}", 1000)
            val bond = all(scene).single { it.tag == DetailSection.BOND }
            assertTrue(all(bond).filterIsInstance<TextView>().any { it.visibility == View.VISIBLE && it.text.toString() == if (preview == Preview.TODAY) "♥3" else "Level 3" })
        }
    }

    @Test fun requirement() {
        for (theme in Theme.entries) for (preview in listOf(Preview.TODAY, Preview.D)) {
            val c = install(theme)
            val data = GameRepo.data!!
            @Suppress("UNCHECKED_CAST") val species = data.species as MutableMap<Int, Species>
            val old = species.getValue(133)
            try {
                species[133] = old.copy(evolutions = listOf(Evolution(700, EvoKind.FRIENDSHIP, minFriendship = 160)))
                val mon = GameRepo.state!!.mon("t6")!!; mon.bondPoints = 125
                val scene = details(c, mon, preview, folded = true, requirements = true)
                val requirements = all(scene).filterIsInstance<TextView>().filter { it.text.contains("Bond") && it.text.contains("3") }
                assertTrue("Real evolution requirement found", requirements.isNotEmpty())
                if (preview != Preview.TODAY) requirements.forEach {
                    it.text = "Needs Level 3 Bond"
                    val condition = it.parent as LinearLayout
                    all(condition).filterIsInstance<TextView>().filter { v -> v.text.toString() == "♥" }.forEach { v -> condition.removeView(v) }
                    (condition.layoutParams as LinearLayout.LayoutParams).weight = 2f
                }
                paparazzi.snapshot(scene, "r30r-requirement-${preview.name.lowercase()}-${theme.name.lowercase()}", 1000)
                if (preview != Preview.TODAY) {
                    val text = all(scene).filterIsInstance<TextView>().single { it.text.toString() == "Needs Level 3 Bond" }
                    assertEquals("Requirement fits on one line", 1, text.lineCount)
                    val rect = Rect(); assertTrue(text.getGlobalVisibleRect(rect)); assertEquals(text.height, rect.height())
                }
            } finally { species[133] = old }
        }
    }
}
