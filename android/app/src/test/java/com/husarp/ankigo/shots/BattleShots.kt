package com.husarp.ankigo.shots

import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.update.Updates
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * The main game screen (GameActivity): battle panel states, first-run / save-error cards, the update banner, picture-in-picture.
 * PNGs: battle-mid, battle-catch, battle-defeat, battle-idle, nodata, starter, saveerror, loading, nosprites, banner,
 * banner-downloading, banner-failed, pip, pip-paused (see each test).
 */
class BattleShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    @After fun cleanUp() = BattleShotKit.reset()

    private val sizes = listOf(Size.PHONE, Size.SPLIT)

    /** battle-mid: a fight in progress. Full panel: arena, both info cards, log, goal bar, chips (bottom bar on the tall window). */
    @Test fun battleMid() = paparazzi.shootWith("battle-mid", sizes) { ctx -> BattleShotKit.bound(ctx).root }

    /**
     * battle-catch: the catch moment as EventPlayer plays a Caught event with the ball already on the field:
     * wild card hidden, ball + stars, "Gotcha!" banner, GOTCHA log line.
     */
    @Test fun battleCatch() = paparazzi.shootWith("battle-catch", sizes, offsetMillis = 800) { ctx ->
        val b = BattleShotKit.bound(ctx)
        val w = GameRepo.state!!.battle!!.wild
        b.arena!!.setEnemyCardShown(false)
        b.arena!!.enemyIntoBall(1)
        b.arena!!.dropBall("poke-ball", 1)
        b.player!!.enqueue(listOf(GameEvent.Caught(w.uid, w.species, w.name, w.level, false, "poke-ball", newSpecies = true, toTeam = false)))
        b.root
    }

    /** battle-defeat: the wild one fainted and was not caught: sprite sunk, WIN log line with XP. */
    @Test fun battleDefeat() = paparazzi.shootWith("battle-defeat", sizes, offsetMillis = 1000) { ctx ->
        val b = BattleShotKit.bound(ctx)
        val w = GameRepo.state!!.battle!!.wild
        b.player!!.enqueue(listOf(GameEvent.Fainted(false, w.name), GameEvent.Defeated(w.species, w.name, 38)))
        b.root
    }

    /** battle-idle: no wild one on the field (state.battle = null): the wild side is empty, log says waiting. */
    @Test fun battleIdle() = paparazzi.shootWith("battle-idle", sizes) { ctx ->
        val b = BattleShotKit.bound(ctx)
        GameRepo.state!!.battle = null
        b.panel!!.renderAll()
        b.root
    }

    /** nodata: first run without the game data: GameActivity would start FirstRunActivity; after cancelling it shows this card. */
    @Test fun nodata() = paparazzi.shootWith("nodata", sizes, scenario = FakeGame.Scenario.NO_DATA) { ctx ->
        val b = BattleShotKit.build(ctx)
        b.setField("firstRunCanceled", true)
        b.call("onStatus", GameRepo.status)
        b.root
    }

    /** starter: NEED_STARTER (data there, no first Pokémon chosen yet): same card as nodata, reached after cancelling first run. */
    @Test fun starter() = paparazzi.shootWith("starter", sizes, scenario = FakeGame.Scenario.NEED_STARTER) { ctx ->
        val b = BattleShotKit.build(ctx)
        b.setField("firstRunCanceled", true)
        b.call("onStatus", GameRepo.status)
        b.root
    }

    /** saveerror: the save couldn't be read card (restore a backup / start a new game). */
    @Test fun saveError() = paparazzi.shootWith("saveerror", sizes) { ctx ->
        BattleShotKit.gameRepoSet("held", null)
        BattleShotKit.gameRepoSet("error", "The save file is damaged (unexpected end of data at line 1).")
        GameRepo::class.java.getDeclaredMethod("rebuild").also { it.isAccessible = true }.invoke(GameRepo)
        val b = BattleShotKit.build(ctx)
        b.call("onStatus", GameRepo.status)
        b.root
    }

    /** loading: the "Loading…" message shown until the game is loaded. */
    @Test fun loading() = paparazzi.shootWith("loading", sizes) { ctx ->
        val b = BattleShotKit.build(ctx)
        b.call("showMessage", "Loading…")
        b.root
    }

    /** nosprites: battle-mid with no sprite files at all (sprite download skipped): placeholders instead of pictures. */
    @Test fun noSprites() = paparazzi.shootWith("nosprites", sizes, sprites = false) { ctx -> BattleShotKit.bound(ctx).root }

    /** banner: battle-mid with the floating update banner (a newer version found). */
    @Test fun banner() = paparazzi.shootWith("banner", sizes) { ctx ->
        BattleShotKit.setUpdates(BattleShotKit.newer())
        BattleShotKit.withBanner(ctx, BattleShotKit.bound(ctx).root)
    }

    /** banner-downloading: the banner while the update downloads (66 %). */
    @Test fun bannerDownloading() = paparazzi.shootWith("banner-downloading", sizes) { ctx ->
        BattleShotKit.setUpdates(BattleShotKit.newer(Updates.Phase.Downloading(66, 1_468_006, 2_202_009, 1700)))
        BattleShotKit.withBanner(ctx, BattleShotKit.bound(ctx).root)
    }

    /** banner-failed: the banner after a failed update (TRY AGAIN + GITHUB). */
    @Test fun bannerFailed() = paparazzi.shootWith("banner-failed", sizes) { ctx ->
        BattleShotKit.setUpdates(BattleShotKit.newer(Updates.Phase.Failed("No internet connection.")))
        BattleShotKit.withBanner(ctx, BattleShotKit.bound(ctx).root)
    }

    /** pip: the picture-in-picture window (compact battle: arena + one-line log, no banner, no bottom bar). */
    @Test fun pip() = paparazzi.shootWith("pip", listOf(Size.PIP)) { ctx ->
        BattleShotKit.setUpdates(BattleShotKit.newer())
        val b = BattleShotKit.bound(ctx)
        BattleShotKit.pip(b)
        b.root
    }

    /** pip-paused: the same while paused (a "Paused" pill over the arena). */
    @Test fun pipPaused() = paparazzi.shootWith("pip-paused", listOf(Size.PIP)) { ctx ->
        val b = BattleShotKit.bound(ctx)
        BattleShotKit.pip(b, paused = true)
        b.root
    }
}
