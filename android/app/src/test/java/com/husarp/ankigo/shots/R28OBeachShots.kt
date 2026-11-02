package com.husarp.ankigo.shots

import android.graphics.BitmapFactory
import com.husarp.ankigo.data.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native battle views with real offline sprite PNGs and bundled artwork. */
class R28OBeachShots {
    @get:Rule val paparazzi = Shots.rule()
    @Test fun glowUpLightAndDark() {
        for (theme in Theme.entries) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.SMALL, theme))
            val ctx = Shots.files(paparazzi.context)
            val row = android.widget.LinearLayout(ctx).apply {
                setBackgroundColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_card))
                addView(AchMedal(ctx, 64).apply { set(com.husarp.ankigo.R.drawable.ic_ach_secret_glow_up, 0) })
                addView(android.widget.TextView(ctx).apply { text = "Glow-up"; setTextColor(Ui.c(ctx, com.husarp.ankigo.R.color.ag_text)) })
            }
            paparazzi.snapshot(row, "o-glow-up-${theme.name.lowercase()}")
        }
    }
    @Test fun beachesInBattle() {
        val ids = listOf("tropical-cove", "coral-lagoon", "sandbar-isle", "reef-bay", "coral-shallows")
        for (id in ids) for (theme in Theme.entries) for (species in listOf("eevee", "snorlax")) {
            paparazzi.unsafeUpdateConfig(deviceConfig = Shots.device(Size.PHONE, theme))
            val ctx = Shots.files(paparazzi.context)
            FakeGame.install(ctx)
            BackdropSharp.runner = BackdropSharp.InPlace
            val bg = BackdropCatalog.byId(id)!!
            Backdrops.installBundled(ctx, Sprites.dir(ctx), bg)
            val pic = Backdrops.fetchBlocking(Sprites.dir(ctx), bg, false) { _, _ -> null }!!
            Backdrops.finish(Backdrops.currentGeneration(), bg.id, pic)
            val battle = BattleShotKit.bound(ctx)
            val arena = battle.arena!!
            arena.setBackdrop(listOf(com.husarp.ankigo.game.Type.WATER), BackdropCatalog.options(com.husarp.ankigo.game.Type.WATER).indexOf(bg))
            assertEquals(id, arena.backdropScenery)
            fun image(name: String): SpriteImage {
                val path = if (name == "eevee-back") "/r28o/eevee-back.png" else "/r26e/$name.png"
                val bytes = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }
                return SpriteImage(listOf(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!), intArrayOf(100))
            }
            // Invalidate queued fixture loads before installing the real, padded sprite images.
            arena.playerSprite.setTag(com.husarp.ankigo.R.id.ag_tag_sprite, Any())
            arena.enemySprite.setTag(com.husarp.ankigo.R.id.ag_tag_sprite, Any())
            arena.playerSprite.setImage(image("eevee-back"))
            arena.enemySprite.setImage(image(species))
            arena.playerSprite.idleMotion = false
            arena.enemySprite.idleMotion = false
            arena.setPlayerInfo("o-player", "Eevee", 25)
            arena.setPlayerTypes(listOf(com.husarp.ankigo.game.Type.NORMAL))
            arena.setEnemyInfo(species.replaceFirstChar { it.titlecase() }, 25, false, listOf(com.husarp.ankigo.game.Type.NORMAL))
            paparazzi.snapshot(battle.root, "o-$id-$species-${theme.name.lowercase()}", 1000)
        }
    }
}
