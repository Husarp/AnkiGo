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

/** PLAN 472 / 479: native Android previews over real app views. All switches and replacements are test-only. */
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

    private class Arrows(c: Context, val stages: Int) : View(c) {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = StatPill.ink(stages > 0, StatPill.isDark(c)); style = Paint.Style.STROKE
            strokeWidth = Ui.dp(c, 1.4f); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val count = kotlin.math.abs(stages)
            val path = Path()
            for (i in 0 until count) {
                val y = (height / d - (4f + (count - 1) * 3f)) / 2f + i * 3f
                path.moveTo(1 * d, (y + if (stages > 0) 4 else 0) * d)
                path.lineTo(4 * d, (y + if (stages > 0) 0 else 4) * d)
                path.lineTo(7 * d, (y + if (stages > 0) 4 else 0) * d)
            }
            canvas.drawPath(path, stroke)
        }
    }

    private fun chip(c: Context, mark: ConditionMarks.Mark, style: Preview): View {
        val stat = mark.key.startsWith("stat.")
        val tone = if (stat) StatPill.ink(mark.value > 0, StatPill.isDark(c)) else Ui.c(c, if (mark.key == "confusion") R.color.ag_mauve else R.color.ag_green)
        val fill = if (stat) StatPill.background(c, mark.value > 0) else ColorUtils.blendARGB(Ui.c(c, R.color.ag_card), tone, 0.17f)
        return row(c).apply {
            tag = "r30-chip"
            background = Ui.rounded(fill, if (style == Preview.B) 6 else 10, c)
            setPadding(Ui.dp(c, 7), Ui.dp(c, 4), Ui.dp(c, 7), Ui.dp(c, 4))
            if (!stat) addView(icon(c, if (mark.key == "confusion") R.drawable.ic_field_psychic else R.drawable.ic_field_grassy, tone),
                LinearLayout.LayoutParams(Ui.dp(c, 14), Ui.dp(c, 14)).also { it.marginEnd = Ui.dp(c, 4) })
            val name = if (stat) StatPill.shortName(mark.key.removePrefix("stat."))!! else if (mark.key == "confusion") "Confused · 3" else "Leech Seed"
            addView(label(c, name, 11f).apply { setTextColor(tone) })
            if (stat) {
                addView(Arrows(c, mark.value).apply { tag = "r30-arrow" }, LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 12)).also { it.marginStart = Ui.dp(c, 3) })
                if (style == Preview.C) addView(label(c, "${if (mark.value > 0) "+" else "−"}${kotlin.math.abs(mark.value)}", 11f).apply {
                    setTextColor(tone)
                }, LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(c, 3) })
            }
            contentDescription = if (stat) StatPill.spoken(mark.key.removePrefix("stat."), mark.value) else mark.spoken
        }
    }

    /** Replace only the existing condition layer; the arena, cards, sprites, bars and tools are the real BattlePanelView. */
    private fun previewChips(arena: ArenaView, style: Preview) {
        if (style == Preview.TODAY) return
        field(arena, "marks").visibility = View.INVISIBLE
        val layer = field(arena, "cardLayer") as FrameLayout
        for ((slot, cardName) in listOf(ConditionMarks.Slot.WILD to "enemyBlock", ConditionMarks.Slot.OURS to "playerCard")) {
            val card = field(arena, cardName)
            val marks = arena.conditions.of(slot)
            val stats = marks.filter { it.key.startsWith("stat.") }
            val effects = marks.filterNot { it.key.startsWith("stat.") }
            val stack = col(arena.context)
            fun flow(list: List<ConditionMarks.Mark>) = FlowRow(arena.context, Ui.dp(arena.context, 4), centered = false, lineGapPx = Ui.dp(arena.context, 4)).apply {
                list.forEach { addView(chip(context, it, style)) }
            }
            when (style) {
                Preview.A -> { stack.addView(flow(stats)); stack.addView(flow(effects), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(arena.context, 4) }) }
                Preview.B -> { stack.addView(flow(effects)); stack.addView(flow(stats), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(arena.context, 4) }) }
                else -> stack.addView(flow(stats + effects))
            }
            layer.addView(stack, FrameLayout.LayoutParams(Ui.dp(arena.context, 220), -2))
            arena.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val d = arena.resources.displayMetrics.density
                val targetX = if (slot == ConditionMarks.Slot.WILD) card.left else card.left + card.width * card.scaleX - Ui.dp(arena.context, 220)
                stack.x = targetX.toFloat().coerceIn(4 * d, arena.width - Ui.dp(arena.context, 224).toFloat())
                stack.y = if (slot == ConditionMarks.Slot.WILD) card.top + card.height * card.scaleY + 5 * d
                    else card.top - stack.height - 5 * d
            }
        }
    }

    private fun battle(c: Context, preview: Preview): BattleShotKit.Built = BattleShotKit.bound(c).also {
        previewChips(it.arena!!, preview)
    }

    @Test fun battleChips() {
        for (theme in Theme.entries) for (preview in options) {
            val c = install(theme)
            val screen = battle(c, preview)
            paparazzi.snapshot(screen.root, "r30r-chips-${preview.name.lowercase()}-${theme.name.lowercase()}", 1000)
            if (preview != Preview.TODAY) {
                val chips = all(screen.root).filter { it.tag == "r30-chip" }
                assertEquals("Every condition and named stat is visible", 5, chips.size)
                assertTrue(chips.all { it.width > 0 && it.height > 0 })
                for (arrow in all(screen.root).filter { it.tag == "r30-arrow" }) assertTrue(arrow.height <= Ui.dp(c, 12))
                val boxes = chips.map { Rect().also { r -> it.getGlobalVisibleRect(r) } }
                for (i in boxes.indices) for (j in 0 until i) assertFalse("Chips never overlap", Rect.intersects(boxes[i], boxes[j]))
                val arena = screen.arena!!
                val blockers = listOf("enemyBlock", "playerCard").map { name ->
                    android.graphics.RectF(Rect().also { field(arena, name).getGlobalVisibleRect(it) })
                } + listOf(arena.enemySprite, arena.playerSprite).map { sprite ->
                    // Match the frame renderer's opaque-pixel bounds, including transparent PNG padding.
                    val side = sprite.height.toFloat()
                    val art = sprite.artShare(side)!!
                    val origin = IntArray(2).also { sprite.getLocationOnScreen(it) }
                    android.graphics.RectF(origin[0] + art.x * side, origin[1] + art.y * side,
                        origin[0] + (art.x + art.w) * side, origin[1] + (art.y + art.h) * side)
                }
                for (v in chips) {
                    val r = Rect(); assertTrue(v.getGlobalVisibleRect(r)); assertTrue(kotlin.math.abs(v.height - r.height()) <= 1); assertTrue(kotlin.math.abs(v.width - r.width()) <= 1)
                    assertFalse("Chip ${v.contentDescription} at $r clears cards and visible art $blockers",
                        blockers.any { android.graphics.RectF.intersects(android.graphics.RectF(r), it) })
                }
            }
        }
    }

    private fun info(c: Context, preview: Preview, stat: Boolean, seed: Boolean): View {
        val tone = Ui.c(c, if (stat || seed) R.color.ag_green else R.color.ag_mauve)
        val title = if (stat) "Attack +2" else if (seed) "Leech Seed" else "Confused"
        val effect = if (stat) "Physical moves deal ×2 damage." else if (seed) "Loses ⅛ max HP/round; heals the foe." else "May hit itself instead of moving."
        val owner = if (seed) "Your Sparkmouse" else "Wild Duckling" + if (stat) "" else " · 3 turns left"
        val drawable = if (stat) R.drawable.ic_swords else if (seed) R.drawable.ic_field_grassy else R.drawable.ic_field_psychic
        val box = col(c).apply {
            tag = "r30-info"
            background = Ui.rounded(Ui.c(c, R.color.ag_card), if (preview == Preview.C) 18 else 12, c)
            setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14))
            elevation = Ui.dp(c, 10f)
        }
        when (preview) {
            Preview.A -> {
                box.addView(row(c).apply {
                    addView(icon(c, drawable, tone), LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)).also { it.marginEnd = Ui.dp(c, 8) })
                    addView(label(c, title, 15f, 900))
                    if (stat) addView(label(c, "×2", 15f, 900, R.color.ag_green), LinearLayout.LayoutParams(-2, -2).also { it.marginStart = Ui.dp(c, 10) })
                })
                box.addView(label(c, effect, 12f, 700), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 8) })
                box.addView(label(c, owner, 10f, 700, R.color.ag_sub0), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 8) })
            }
            Preview.B -> {
                box.addView(row(c).apply {
                    addView(icon(c, drawable, tone), LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)).also { it.marginEnd = Ui.dp(c, 10) })
                    addView(col(c).apply {
                        addView(label(c, title, 17f, 900))
                        addView(label(c, owner, 10f, 700, R.color.ag_sub0), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 3) })
                    })
                })
                box.addView(View(c).apply { setBackgroundColor(Ui.c(c, R.color.ag_s0)) }, LinearLayout.LayoutParams(-1, Ui.dp(c, 1)).also { it.topMargin = Ui.dp(c, 12); it.bottomMargin = Ui.dp(c, 12) })
                box.addView(label(c, effect, 12f, 700))
            }
            else -> {
                box.addView(label(c, owner, 11f, 800, R.color.ag_sub0))
                box.addView(row(c).apply {
                    addView(icon(c, drawable, tone), LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)).also { it.marginEnd = Ui.dp(c, 8) })
                    addView(label(c, title, 20f, 900))
                }, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 10) })
                box.addView(label(c, effect, 13f, 700), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 10) })
                box.addView(label(c, "Tap to close", 10f, 700, R.color.ag_sub0), LinearLayout.LayoutParams(-1, -2).also { it.topMargin = Ui.dp(c, 12) })
            }
        }
        return box
    }

    @Test fun infoBoxes() {
        for (theme in Theme.entries) for (preview in options) for (focus in listOf("effect", "seed", "stat")) {
            val stat = focus == "stat"
            val seed = focus == "seed"
            val c = install(theme)
            val screen = battle(c, if (preview == Preview.TODAY) Preview.TODAY else Preview.A)
            val arena = screen.arena!!
            var shown = false
            val wrapper = object : FrameLayout(c) {
                override fun dispatchDraw(canvas: Canvas) {
                    if (!shown && arena.width > 0) {
                        shown = true
                        if (preview == Preview.TODAY) {
                            val method = ArenaView::class.java.getDeclaredMethod("showConditionList", ConditionMarks.Slot::class.java, Boolean::class.javaPrimitiveType, String::class.java)
                            method.isAccessible = true
                            method.invoke(arena, if (seed) ConditionMarks.Slot.OURS else ConditionMarks.Slot.WILD, false, if (stat) "summary" else null)
                        } else {
                            val box = info(c, preview, stat, seed)
                            val layer = field(arena, "popLayer") as FrameLayout
                            val w = Ui.dp(c, if (preview == Preview.C) 352 else 304)
                            box.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                            val card = field(arena, if (seed) "playerCard" else "enemyBlock")
                            val x = if (preview == Preview.C) (arena.width - w) / 2 else Ui.dp(c, 12)
                            val y = if (preview == Preview.C) arena.height - box.measuredHeight - Ui.dp(c, 16) else if (seed) (card.top - box.measuredHeight - Ui.dp(c, 76)) else (card.bottom + Ui.dp(c, 72))
                            layer.addView(box, FrameLayout.LayoutParams(w, box.measuredHeight).also { it.leftMargin = x; it.topMargin = y })
                        }
                        arena.measure(View.MeasureSpec.makeMeasureSpec(arena.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(arena.height, View.MeasureSpec.EXACTLY))
                        arena.layout(arena.left, arena.top, arena.right, arena.bottom)
                    }
                    super.dispatchDraw(canvas)
                }
            }
            wrapper.addView(screen.root, FrameLayout.LayoutParams(-1, -1))
            paparazzi.snapshot(wrapper, "r30r-info-$focus-${preview.name.lowercase()}-${theme.name.lowercase()}", 1000)
            assertTrue(shown)
            if (preview != Preview.TODAY) {
                val box = all(wrapper).single { it.tag == "r30-info" }
                val r = Rect(); assertTrue(box.getGlobalVisibleRect(r)); assertEquals(box.height, r.height())
                val texts = all(box).filterIsInstance<TextView>()
                assertFalse(texts.any { it.text.contains("Until switching") })
                assertTrue("Effect remains one line", texts.filter { it.text.contains("instead of") || it.text.contains("Physical moves") || it.text.contains("max HP/round") }.all { it.lineCount == 1 })
            }
        }
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
