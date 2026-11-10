package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.RectF
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.data.Backdrops
import com.husarp.ankigo.data.HttpGet
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.overlay.FloatBattleView
import com.husarp.ankigo.ui.ArenaBackdrop
import com.husarp.ankigo.ui.ArenaView
import com.husarp.ankigo.ui.Backdrop
import com.husarp.ankigo.ui.BackdropCatalog
import com.husarp.ankigo.ui.BattlePanelView
import com.husarp.ankigo.ui.BubbleDismiss
import com.husarp.ankigo.ui.ConditionMarks
import com.husarp.ankigo.ui.EventPlayer
import com.husarp.ankigo.ui.PanelFit
import com.husarp.ankigo.ui.StatPill
import com.husarp.ankigo.ui.Ui
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** PLAN 510: real hosts keep every condition attached to its owner. */
class R31BConditionShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = Backdrops.forget()

    private val noGet: HttpGet = { _, _ -> null }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o: Any, name: String): T {
        var c: Class<*>? = o.javaClass
        while (c != null) {
            try { return c.getDeclaredField(name).also { it.isAccessible = true }.get(o) as T } catch (_: NoSuchFieldException) { c = c.superclass }
        }
        throw NoSuchFieldException(name)
    }

    // ---------------------------------------------------------------- the fight

    private enum class How { VOL, SIDE }

    private val wildPicks = listOf(How.VOL to ("confusion" to 3), How.VOL to ("leechseed" to 1), How.SIDE to ("tailwind" to 3), How.VOL to ("substitute" to 25),
        How.VOL to ("taunt" to 2), How.SIDE to ("safeguard" to 4), How.VOL to ("encore" to 2), How.VOL to ("healblock" to 4))
    private val ourPicks = listOf(How.VOL to ("attract" to 1), How.VOL to ("partiallytrapped" to 3), How.SIDE to ("reflect" to 4), How.VOL to ("stockpile" to 2),
        How.SIDE to ("lightscreen" to 5), How.VOL to ("aquaring" to 1), How.VOL to ("torment" to 1), How.SIDE to ("wish" to 2))

    /** [n] conditions on each side and the weather (n = 3 adds a terrain, n = 8 a room too), in the saved fight. */
    private fun fight(n: Int) {
        val b = GameRepo.state!!.battle!!
        if (n == 2) {
            b.enemy.boosts[1] = 2; b.player.boosts[1] = 2
            b.enemy.volatiles.clear(); b.player.volatiles.clear()
            b.enemy.volatiles["ingrain"] = 1; b.player.volatiles["ingrain"] = 1
            return
        }
        b.enemy.volatiles.clear(); b.player.volatiles.clear(); b.field.enemy.clear(); b.field.player.clear(); b.field.rooms.clear()
        b.field.weather = "rain"; b.field.weatherTurns = 3
        b.field.terrain = if (n >= 3) "electric" else null; b.field.terrainTurns = 4
        if (n >= 8) b.field.rooms["trickroom"] = 3
        for ((how, kv) in wildPicks.take(n)) (if (how == How.VOL) b.enemy.volatiles else b.field.enemy)[kv.first] = kv.second
        for ((how, kv) in ourPicks.take(n)) (if (how == How.VOL) b.player.volatiles else b.field.player)[kv.first] = kv.second
    }

    // ---------------------------------------------------------------- hosts

    private fun device(w: Int, h: Int, theme: Theme): DeviceConfig = Shots.device(Size.PHONE, theme).copy(
        screenWidth = w * 2, screenHeight = h * 2, orientation = if (w > h) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
    )

    private fun install(ctx: Context) {
        FakeGame.install(ctx)
        val source = File("/home/coder/code/projects/AnkiGo/docs/design/plan-kit/sprites")
        for (side in listOf("front", "back", "static")) for (n in listOf(25, 54)) {
            val f = File(source, "${if (side == "static") "front" else side}/$n.png")
            if (f.exists()) File(Sprites.dir(ctx), "$side/$n.png").also { it.parentFile!!.mkdirs(); f.copyTo(it, overwrite = true) }
        }
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        val st = GameRepo.state!!
        st.caught -= st.battle!!.wild.species   // the wild one is new: the NEW sticker is on its box
    }

    private fun load(ctx: Context, bg: Backdrop) {
        BackdropScenes.write(Sprites.dir(ctx), bg, 0.5f)
        val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false, noGet)!!
        Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
    }

    private fun layoutRoot(v: View, wPx: Int, hPx: Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, wPx, hPx)
    }

    private class Scene(val root: View, val arena: ArenaView, val panel: BattlePanelView?, val wPx: Int, val hPx: Int)

    private class Host(val name: String, val w: Int, val h: Int, val build: (Context) -> Scene)

    private fun px(ctx: Context) = ctx.resources.configuration.screenWidthDp * 2 to ctx.resources.configuration.screenHeightDp * 2

    private fun battle(ctx: Context): Scene {
        val b = BattleShotKit.bound(ctx)
        return Scene(b.root, b.arena!!, b.panel, px(ctx).first, px(ctx).second)
    }

    private fun boxed(ctx: Context, wDp: Int, hDp: Int, strip: Boolean): Scene {
        val p = BattlePanelView(ctx)
        p.setStudyLayout(strip, false)
        p.renderAll()
        val frame = FrameLayout(ctx).apply {
            setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_base))
            addView(p, FrameLayout.LayoutParams(Ui.dp(ctx, wDp), Ui.dp(ctx, hDp), Gravity.TOP or Gravity.LEFT))
        }
        return Scene(frame, p.arena, p, px(ctx).first, px(ctx).second)
    }

    private fun pip(ctx: Context): Scene {
        val p = BattlePanelView(ctx).apply { setCompact(true) }
        p.renderAll()
        return Scene(p, p.arena, p, px(ctx).first, px(ctx).second)
    }

    private fun floating(ctx: Context): Scene {
        val self = object : android.content.ContextWrapper(ctx) { override fun getApplicationContext(): Context = this }
        val v = FloatBattleView(self, {}, {}, {})
        v.javaClass.getDeclaredMethod("bind").also { it.isAccessible = true }.invoke(v)
        val p: BattlePanelView = field(v, "panel")
        p.renderAll()
        val window: Any = field(v, "window")
        return Scene(field(window, "root"), p.arena, p, px(ctx).first, px(ctx).second)
    }

    private val hosts = listOf(
        Host("battle-tall-412x915", 412, 915, ::battle),
        Host("battle-tablet-land-1280x800", 1280, 800, ::battle),
        Host("battle-320x640", 320, 640, ::battle),
        Host("battle-360x640", 360, 640, ::battle),
        Host("battle-land-869x412", 869, 412, ::battle),
        Host("battle-tablet-800x1280", 800, 1280, ::battle),
        Host("battle-412x869", 412, 869, ::battle),
        Host("study-412x400", 412, 400) { boxed(it, 412, 400, strip = false) },
        Host("study-412x300", 412, 300) { boxed(it, 412, 300, strip = false) },
        Host("study-412x250", 412, 250) { boxed(it, 412, 250, strip = false) },
        Host("strip-412x230", 412, 230) { boxed(it, 412, 230, strip = true) },
        Host("strip-412x130", 412, 130) { boxed(it, 412, 130, strip = true) },
        Host("pip-240x136", 240, 136, ::pip),
        Host("pip-240x135", 240, 135, ::pip),
        Host("pip-200x200", 200, 200, ::pip),
        Host("pip-320x180", 320, 180, ::pip),
        Host("pip-240x160", 240, 160, ::pip),
        Host("float-320x300", 320, 300, ::floating),
        Host("float-340x480", 340, 480, ::floating),
        Host("float-240x190", 240, 190, ::floating),
        Host("float-170x130", 170, 130, ::floating),
    )

    private fun layoutScene(sc: Scene) { sc.arena.requestLayout(); layoutRoot(sc.root, sc.wPx, sc.hPx) }

    /** Builds [host], puts the fight with [n] conditions on it, with or without a battle picture, and lays it out until it is steady. */
    private fun setup(ctx: Context, host: Host, n: Int, picture: Boolean): Scene {
        install(ctx)
        fight(n)
        val sc = host.build(ctx)
        val arena = sc.arena
        arena.setNewTag(true); arena.setEnemyCardShown(true)
        val bg = BackdropCatalog.byId("forest")!!
        layoutRoot(sc.root, sc.wPx, sc.hPx)
        if (picture) { load(ctx, bg); field<ArenaBackdrop>(arena, "backdrop").request(bg, false) }
        else arena.setPlainBackdrop()
        arena.syncConditions(GameRepo.state!!.battle)
        layoutScene(sc); layoutScene(sc)
        return sc
    }

    @Test fun anchoredChipsAtEveryHostInBothThemes() {
        val bad = ArrayList<String>()
        for (host in hosts) for (theme in Theme.entries) for (picture in listOf(false, true)) for (n in listOf(2, 8)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = device(host.w, host.h, theme))
            val sc = setup(Shots.files(paparazzi.context), host, n, picture)
            paparazzi.snapshot(sc.root, "b-chips-${host.name}-$n-${if (picture) "picture" else "plain"}-${theme.name.lowercase()}", 1000)
            for (slot in listOf(ConditionMarks.Slot.WILD, ConditionMarks.Slot.OURS)) {
                val card: View = field(sc.arena, if (slot == ConditionMarks.Slot.WILD) "enemyBlock" else "playerCard")
                val chips = sc.arena.markRows().singleOrNull { it.slot == slot }?.chips.orEmpty()
                val tag = "${host.name} $picture $theme $slot"
                if (chips.count { it.mark != null } + chips.sumOf { it.more } != n) bad += "$tag missing conditions: ${chips.size}"
                if (chips.isEmpty()) {
                    val scene = sc.arena.markScenePublic()
                    bad += "$tag card=${card.left},${card.top},${card.width * card.scaleX},${card.height * card.scaleY} area=${scene.area.l},${scene.area.t},${scene.area.r},${scene.area.b} sprites=" + scene.sprites.joinToString { "${it.l},${it.t},${it.r},${it.b}" }
                    continue
                }
                if (card.top < 0) bad += "$tag card pushed outside arena: ${card.top}"
                val distance = if (slot == ConditionMarks.Slot.WILD) card.top - chips.first().rect.bottom
                    else chips.first().rect.top - (card.top + card.height * card.scaleY)
                if (distance < 0 || distance > 20) bad += "$tag detached from card: $distance"
                for (line in chips.groupBy { it.rect.top }.values) {
                    if (kotlin.math.abs((line.first().rect.left + line.last().rect.right) / 2f - (card.left + card.width * card.scaleX / 2f)) > .5f) bad += "$tag row not centred"
                }
                for (chip in chips) if (chip.rect.left < 0 || chip.rect.right > sc.arena.width + .5f || chip.rect.top < 0 || chip.rect.bottom > sc.arena.height + .5f) bad += "$tag outside arena: ${chip.rect} arena=${sc.arena.width}x${sc.arena.height}"
            }
            sc.arena.markRows().firstOrNull { row -> row.chips.any { it.more > 0 } }?.let { row ->
                val chip = row.chips.first { it.more > 0 }
                val marks: com.husarp.ankigo.ui.ConditionMarksView = field(sc.arena, "marks")
                val pixels = Bitmap.createBitmap(marks.width, marks.height, Bitmap.Config.ARGB_8888)
                marks.draw(Canvas(pixels))
                val r = chip.rect
                val hasText = ((r.left + r.width() * .3f).toInt()..(r.right - r.width() * .3f).toInt()).any { x ->
                    ((r.top + r.height() * .25f).toInt()..(r.bottom - r.height() * .25f).toInt()).any { y -> pixels.getPixel(x, y) != marks.metrics!!.moreFill() }
                }
                if (!hasText) bad += "${host.name} overflow label is blank"
                pixels.recycle()
                val now = android.os.SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(now, now + action * 40L, action, chip.rect.centerX(), chip.rect.centerY(), 0)
                    marks.onTouchEvent(event); event.recycle()
                }
                if (sc.arena.conditionListShown() == null) bad += "${host.name} overflow cannot open full list"
                sc.arena.conditionListShown()?.performClick()
            }
            val chips = sc.arena.markRects()
            val scene = sc.arena.markScenePublic()
            for (chip in chips) for (block in scene.blocks.filter { it !in scene.sprites }) {
                if (block.hits(com.husarp.ankigo.ui.ConditionSpot.R(chip.left, chip.top, chip.right, chip.bottom))) bad += "${host.name} $n chip covers info or frame: $chip block=${block.l},${block.t},${block.r},${block.b}"
            }
            for (i in chips.indices) for (j in 0 until i) if (RectF.intersects(chips[i], chips[j])) bad += "${host.name} $n chips overlap: ${chips[i]} ${chips[j]}"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
