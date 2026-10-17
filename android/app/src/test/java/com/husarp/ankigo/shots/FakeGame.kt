package com.husarp.ankigo.shots

import android.content.Context
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.Ui
import java.io.File

/**
 * Fake game for screenshots: TestData's made-up world, a mid-game save (team of 6, a box, money, items, a battle) and
 * generated sprite shapes in the sprite cache. [install] puts it into GameRepo (READY) — no real Pokémon data.
 */
object FakeGame {
    /** Set before [install] to change what gets installed (reset to default after each install). */
    var scenario: Scenario = Scenario.MID_GAME
    /** False = no sprite files at all (first run skipped the download): every Pokémon shows the placeholder. */
    var withSprites: Boolean = true

    enum class Scenario { MID_GAME, NO_DATA, NEED_STARTER, EMPTY }

    val data: GameData by lazy { TestData.world() }

    fun state(d: GameData = data): GameState {
        val s = GameState(createdAt = 1L, day = "2026-10-14", starterChosen = true)
        fun add(num: Int, level: Int, uid: String, team: Boolean, xpFrac: Double = 0.4, hpFrac: Double = 1.0, item: String? = null): Mon {
            val m = TestData.mon(d, num, level, listOf("tackle", "thunderbolt", "quickattack"), uid)
            m.hp = (StatCalc.maxHp(d, m) * hpFrac).toInt().coerceAtLeast(if (hpFrac > 0) 1 else 0)
            m.xp = (com.husarp.ankigo.game.Progression.xpToNext(d, m) * xpFrac).toInt()
            m.heldItem = item
            m.caughtAt = 1_760_000_000_000L
            s.mons += m
            if (team) s.team += uid
            s.seen += num; s.caught += num
            return m
        }
        add(25, 26, "t1", true, 0.62, 0.82, item = "lucky-egg")
        add(2, 24, "t2", true, 0.2)
        add(3, 33, "t3", true, 0.5, 0.45)
        add(92, 25, "t4", true, 0.1, 0.15)
        add(130, 22, "t5", true, 0.7, 0.0)
        add(133, 18, "t6", true, 0.05)
        var i = 0
        for (num in listOf(16, 19, 54, 129, 63, 64, 172, 1, 26, 134, 135, 700, 16, 19, 54)) add(num, 9 + (i++ % 8), "b$i", false)
        s.seen += listOf(150, 151, 65)
        s.cash = 1240
        s.bag.putAll(mapOf("poke-ball" to 14, "great-ball" to 4, "ultra-ball" to 2, "potion" to 3, "super-potion" to 1,
            "thunder-stone" to 1, "rare-candy" to 2))
        s.reviewsToday = 86
        s.cashToday = 120
        s.buttonsToday[1] = 9; s.buttonsToday[2] = 6; s.buttonsToday[3] = 60; s.buttonsToday[4] = 11
        s.cardStreak = 5
        s.bestCardStreak = 21
        s.streakDays = 12
        s.lastGoalDay = "2026-10-13"
        s.itemCountdown = 7
        s.trainerLevel = 7
        s.trainerXp = 340
        s.totalReviews = 4120
        s.catches = 21; s.defeats = 64; s.evolutions = 3
        val wild = TestData.mon(d, 54, 25, listOf("watergun", "tackle"), "wild")
        wild.hp = (StatCalc.maxHp(d, wild) * 0.7).toInt()
        s.battle = Battle(wild, Tier.NORMAL, answers = 1, tally = 10, rounds = 2, startedAt = 1L)
        s.nextSpecies = 63
        s.shopDay = "2026-10-14"
        s.shopItems += listOf("thunder-stone", "rare-candy", "lucky-egg")
        return s
    }

    fun install(ctx: Context) {
        // Sprite decoding normally runs on a background thread, which races the snapshot: decode in place instead.
        // (Set here, not in the rule: PanelSprites needs the renderer's main looper.)
        com.husarp.ankigo.ui.PanelSprites.pool = java.util.concurrent.Executor { it.run() }
        // Battle background pictures: load in place, and never from the network (R7-07; a shot puts its own pictures in the cache).
        com.husarp.ankigo.data.Backdrops.pool = java.util.concurrent.Executor { it.run() }
        com.husarp.ankigo.data.Backdrops.get = { _, _ -> null }
        com.husarp.ankigo.data.Backdrops.forget()
        val sc = scenario
        scenario = Scenario.MID_GAME
        val d: GameData? = if (sc == Scenario.NO_DATA) null else data
        val st: GameState? = when (sc) {
            Scenario.MID_GAME -> state()
            Scenario.NEED_STARTER -> GameState(createdAt = 1L, day = "2026-10-14", starterChosen = false)
            Scenario.EMPTY -> GameState(createdAt = 1L, day = "2026-10-14", starterChosen = true).also {
                val m = TestData.mon(data, 25, 5, listOf("tackle"), "t1"); it.mons += m; it.team += m.uid; it.seen += 25; it.caught += 25
            }
            Scenario.NO_DATA -> GameState(createdAt = 1L, day = "2026-10-14", starterChosen = true)
        }
        set("loadedData", d)
        set("held", st)
        val rebuild = GameRepo::class.java.getDeclaredMethod("rebuild")
        rebuild.isAccessible = true
        rebuild.invoke(GameRepo)
        sprites(ctx, withSprites)
        withSprites = true
    }

    private fun set(field: String, v: Any?) {
        val f = GameRepo::class.java.getDeclaredField(field)
        f.isAccessible = true
        f.set(GameRepo, v)
    }

    /**
     * Fills (or empties) the sprite cache: a copy of [Shots.spriteSource] when that local folder exists, otherwise
     * generated PNG shapes for every fake species and the bag items.
     */
    fun sprites(ctx: Context, on: Boolean) {
        val dir = Sprites.dir(ctx)
        dir.deleteRecursively()
        if (!on) return
        if (Shots.spriteSource.isDirectory) { Shots.spriteSource.copyRecursively(dir, overwrite = true); return }
        for (sp in data.species.values) {
            val col = Ui.typeColor(sp.types.firstOrNull() ?: Type.NORMAL)
            FakeSprites.write(FakeSprites.blob(96, col), File(dir, "front/${sp.num}.png"))
            FakeSprites.write(FakeSprites.blob(96, col, back = true), File(dir, "back/${sp.num}.png"))
            FakeSprites.write(FakeSprites.blob(96, col), File(dir, "static/${sp.num}.png"))
        }
        val items = listOf("poke-ball", "great-ball", "ultra-ball", "master-ball", "net-ball", "potion", "super-potion",
            "max-potion", "thunder-stone", "moon-stone", "linking-cord", "lucky-egg", "everstone", "amulet-coin",
            "helix-fossil", "poke-doll", "kings-rock", "rare-candy", "water-stone")
        for ((n, it) in items.withIndex()) {
            val col = listOf(0xFFD20F39, 0xFF1E66F5, 0xFFDF8E1D, 0xFF40A02B, 0xFF8839EF)[n % 5].toInt()
            FakeSprites.write(FakeSprites.blob(30, col, back = true), File(dir, "item/$it.png"))
        }
    }
}
