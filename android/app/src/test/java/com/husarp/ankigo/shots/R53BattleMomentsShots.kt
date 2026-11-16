package com.husarp.ankigo.shots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.data.SpriteImage
import com.husarp.ankigo.gfx.SpriteView
import com.husarp.ankigo.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R53BattleMomentsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    @After fun cleanUp() = BattleShotKit.reset()

    private fun scene(ctx: Context, fire: (ArenaView) -> Unit): View {
        val built = BattleShotKit.bound(ctx)
        val a = built.arena!!
        var done = false
        a.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!done && a.width > 0 && a.height > 0) {
                done = true
                fire(a)
                for (i in 0 until a.childCount) (a.getChildAt(i) as? FloatTextView)?.let {
                    it.animate().cancel(); it.alpha = 1f; it.scaleX = 1f; it.scaleY = 1f
                }
                val ball = getField(a, "ball") as android.widget.ImageView
                ball.animate().cancel(); ball.translationY = 0f
                System.getenv("ANKIGO_MOMENT_BALLS")?.let { path ->
                    val item = (ball.getTag(com.husarp.ankigo.R.id.ag_tag_sprite) as? String)?.removePrefix("item:")
                    val file = java.io.File(path, "$item.png")
                    if (file.isFile) {
                        val bitmap = file.inputStream().use { requireNotNull(android.graphics.BitmapFactory.decodeStream(it)) }
                        ball.setTag(com.husarp.ankigo.R.id.ag_tag_sprite, "r53-still:$item")
                        ball.setImageDrawable(Ui.pixelBitmap(ctx, bitmap))
                    }
                }
                val banner = getField(a, "banner") as View
                val fit = getField(a, "bannerFit") as Float
                banner.animate().cancel(); banner.alpha = 1f; banner.scaleX = fit; banner.scaleY = fit
                a.measure(View.MeasureSpec.makeMeasureSpec(a.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(a.height, View.MeasureSpec.EXACTLY))
                a.layout(a.left, a.top, a.right, a.bottom)
            }
        }
        return built.root
    }
    private fun shot(name: String, fire: (ArenaView) -> Unit) = paparazzi.shootWith("b-$name", listOf(Size.PHONE), offsetMillis = 400) { scene(it, fire) }
    private fun moment(kind: BattleMomentFx.Kind, player: Boolean, atMs: Float) = { a: ArenaView -> a.momentStill(kind, player, atMs) }

    @Test fun throwPoke() = shot("catch-poke-arc") { it.throwStill("poke-ball", true, 440f) }
    @Test fun throwGreat() = shot("catch-great-arc") { it.throwStill("great-ball", true, 440f) }
    @Test fun throwUltra() = shot("catch-ultra-arc") { it.throwStill("ultra-ball", true, 440f) }
    @Test fun throwMaster() = shot("catch-master-arc") { it.throwStill("master-ball", true, 440f) }
    @Test fun caught() = shot("caught-burst") {
        it.dropBall("great-ball", 1); it.setEnemyCardShown(false)
        it.enemySprite.alpha = 0f; it.showBanner("Gotcha!", "Wildling was caught", null); it.showStars(true)
        it.catchBurstStill(true, 230f)
    }
    @Test fun breaksFree() = shot("breaks-free") { it.catchBurstStill(false, 160f) }
    @Test fun xpShapes() = shot("xp-shapes") { it.xpAbsorbStill(120, 1300f) }
    @Test fun levelUp() = shot("level-up-front", moment(BattleMomentFx.Kind.LEVEL, true, 500f))
    @Test fun shiny() = shot("shiny-a", moment(BattleMomentFx.Kind.SHINY, false, 340f))
    @Test fun statUp() = shot("stat-up-ribbons") {
        it.floatText(FloatText(true, "ATK ▲▲", FloatKind.STAT_UP), immediate = true)
        it.momentStill(BattleMomentFx.Kind.UP, true, 540f)
    }
    @Test fun statDown() = shot("stat-down-ribbons") {
        it.floatText(FloatText(false, "DEF ▼", FloatKind.STAT_DOWN), immediate = true)
        it.momentStill(BattleMomentFx.Kind.DOWN, false, 540f)
    }
    @Test fun genericHit() = shot("hit-b", moment(BattleMomentFx.Kind.HIT, false, 160f))
    @Test fun evolution() = shot("evolution-cover-both-forms") { a ->
        val next = SpriteView(a.context).apply {
            byFrame = a.playerSprite.byFrame; anchorFeet = a.playerSprite.anchorFeet; sizeFactor = a.playerSprite.sizeFactor
            setImage(shape(140, 85, 0xFF95B5DF.toInt()))
        }
        a.evolutionStill(next, 900f)
    }
    @Test fun compactRibbons() = paparazzi.shootWith("b-stat-up-small", listOf(Size.FLOATBATTLE), offsetMillis = 400) { ctx ->
        val p = BattlePanelView(ctx); p.setCompact(true); p.renderAll()
        var done = false
        p.arena.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!done && p.arena.width > 0) { done = true; p.arena.momentStill(BattleMomentFx.Kind.UP, true, 540f) }
        }
        p
    }

    private fun shape(w: Int, h: Int, color: Int): SpriteImage {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).drawRect(10f, 5f, w - 10f, h - 5f, Paint().apply { this.color = color })
        return SpriteImage(listOf(b), intArrayOf(100))
    }
    @Test fun visiblePixelBoundsIgnorePaddingAndMirrorCorrectly() {
        val ctx = Shots.files(paparazzi.context)
        val s = SpriteView(ctx).apply { anchorFeet = true; byFrame = true; setImage(shape(140, 85, Color.BLUE)) }
        val bounds = RectF(); s.momentBounds(bounds, 192, 192)
        assertTrue(bounds.width() < 280f)
        assertEquals(192f, bounds.bottom, 2f)
        val left = bounds.left; val right = bounds.right
        s.mirror = true; s.momentBounds(bounds, 192, 192)
        assertEquals(192f - right, bounds.left, .01f); assertEquals(192f - left, bounds.right, .01f)
    }
    @Test fun effectsEndAtTheirApprovedTimesAtBothPaces() {
        val ctx = Shots.files(paparazzi.context)
        val canvas = Canvas(Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888))
        for (factor in listOf(1f, .5f)) for ((kind, end) in listOf(
            BattleMomentFx.Kind.LEVEL to 1500f, BattleMomentFx.Kind.EVOLVE to 2200f,
            BattleMomentFx.Kind.SHINY to 1250f, BattleMomentFx.Kind.UP to 1700f,
            BattleMomentFx.Kind.DOWN to 1700f, BattleMomentFx.Kind.HIT to 800f,
            BattleMomentFx.Kind.CAUGHT to 1200f, BattleMomentFx.Kind.FREE to 800f)) {
            val fx = BattleMomentFx(ctx, kind, 140f, 140f, 1f, factor)
            assertTrue(fx.draw(canvas, end * factor - 1f)); assertFalse(fx.draw(canvas, end * factor))
        }
    }
    @Test fun reducedMotionAndCancellationLeaveNoMomentLayers() {
        val ctx = Shots.files(paparazzi.context)
        val a = ArenaView(ctx)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", true).commit()
        a.levelUpMoment(1500); a.statMoment(true, true, 1700); a.catchBurst(true, 1f); a.shinyBurst(false, 450)
        assertTrue((getField(a.fx, "live") as Collection<*>).isEmpty())
        assertTrue((getField(a.fx, "pending") as Collection<*>).isEmpty())
        a.fx.add(BattleMomentFx(ctx, BattleMomentFx.Kind.LEVEL, 20f, 20f, 1f))
        a.cancelAnimations()
        assertTrue((getField(a.fx, "live") as Collection<*>).isEmpty())
        val back = getField(a, "momentBack") as ArenaFxView
        assertTrue((getField(back, "live") as Collection<*>).isEmpty())
    }
    private fun panel(): BattlePanelView {
        paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, Theme.LIGHT))
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        ctx.getSharedPreferences("study", 0).edit().putBoolean("reduceMotion", false).commit()
        AppPrefs.setAttackAnimations(ctx, true)
        return BattlePanelView(ctx).also {
            it.renderAll()
            it.arena.setEnemyShown(true)
            it.arena.playerSprite.setPlaceholder("P", Color.BLUE)
            it.arena.enemySprite.setPlaceholder("W", Color.RED)
            it.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1738, View.MeasureSpec.EXACTLY))
            it.layout(0, 0, 824, 1738)
        }
    }
    private fun play(player: EventPlayer, event: com.husarp.ankigo.game.GameEvent): Long {
        val method = EventPlayer::class.java.getDeclaredMethod("play", com.husarp.ankigo.game.GameEvent::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        return method.invoke(player, event, false) as Long
    }
    @Test fun eventTimingsAndRibbonLayersFollowTheBattlePace() {
        val p = panel(); val player = EventPlayer(p, continueOnPanel = false)
        val factor = EventPlayer::class.java.getDeclaredField("factor").apply { isAccessible = true }
        for (pace in listOf(1.0, .5)) {
            factor.setDouble(player, pace)
            assertEquals((2350 * pace).toLong(), play(player, com.husarp.ankigo.game.GameEvent.BallThrow("ultra-ball", 50, true, 2)))
            p.arena.resetFx()
            assertEquals((1950 * pace).toLong(), play(player, com.husarp.ankigo.game.GameEvent.BallThrow("great-ball", 50, false, 2)))
            p.arena.resetFx()
            assertEquals((1500 * pace).toLong(), play(player, com.husarp.ankigo.game.GameEvent.LevelUp(p.arena.playerUid!!, "Main", 50, 100, 100)))
            p.arena.resetFx()
            assertEquals((1700 * pace).toLong(), play(player, com.husarp.ankigo.game.GameEvent.Effect(true, "Attack rose", 100, 100, kind = com.husarp.ankigo.game.EffectKind.STAT_CHANGE, stat = "atk", stages = 2)))
            assertEquals(1, (getField(p.arena.fx, "live") as Collection<*>).size)
            assertEquals(1, (getField(getField(p.arena, "momentBack")!!, "live") as Collection<*>).size)
            p.arena.resetFx()
        }
        player.clear()
    }
    @Test fun genericHitHonorsAttackAnimationSettingAndSpecificMovesKeepTheirLook() {
        val p = panel(); val a = p.arena; val player = EventPlayer(p, continueOnPanel = false)
        val hit = com.husarp.ankigo.game.GameEvent.Attack(true, "Main", "Wild", "Punch", com.husarp.ankigo.game.Type.FIGHTING,
            com.husarp.ankigo.game.Category.PHYSICAL, 10, 1, 1.0, false, false, false, 50, 100, 100, 100, "machpunch", 40)
        var called = false
        a.moveOnScreen = { true }
        a.moveMaker = { _, _, _, _ -> called = true; null }
        assertEquals(1100L, play(player, hit))
        assertFalse("category fallback uses the generic hit", called)
        play(player, hit.copy(moveId = "tackle", moveType = com.husarp.ankigo.game.Type.NORMAL))
        assertTrue("a move's own look is retained", called)
        a.resetFx()
        AppPrefs.setAttackAnimations(p.context, false)
        a.hit(false, 380)
        assertTrue((getField(a.fx, "live") as Collection<*>).isEmpty())
        AppPrefs.setAttackAnimations(p.context, true)
        a.hit(false, 380)
        assertEquals(1, (getField(a.fx, "live") as Collection<*>).size)
        player.clear(); a.resetFx()
    }

}
