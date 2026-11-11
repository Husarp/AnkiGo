package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PokeDollTest {
    private val data = TestData.world()
    private val minute = 60_000L
    private var now = 1_800_000_000_000L
    private fun game(found: Int = 1): Game {
        val s = TestData.state(data, 25, 25)
        Economy.give(s, PokeDoll.ITEM, found)
        return Game(data, s, Random(7)) { now }.also { it.ensureEncounter() }
    }
    private fun reload(g: Game): Game = Game(data, SaveCodec.decode(SaveCodec.encode(g.state)), Random(7)) { now }

    @Test fun exactLevelBoundariesAndCooldowns() {
        for ((found, level) in listOf(0 to 0, 1 to 1, 4 to 1, 5 to 2, 14 to 2, 15 to 3, 29 to 3, 30 to 4, 49 to 4, 50 to 5, 999 to 5))
            assertEquals("$found dolls", level, PokeDoll.level(found))
        assertEquals(listOf(60, 50, 40, 35, 30), listOf(1, 5, 15, 30, 50).map(PokeDoll::cooldownMinutes))
    }

    @Test fun oldBagCopiesConvertOnceAndNewFindsAccumulate() {
        val legacy = JSONObject(SaveCodec.encode(GameState())).apply {
            remove("dollsFound"); remove("dollLockedAt"); remove("dollReadyAt")
            put("bag", JSONObject().put(PokeDoll.ITEM, 17))
        }
        val s = SaveCodec.decode(legacy.toString())
        assertEquals(17, s.dollsFound); assertEquals(1, s.count(PokeDoll.ITEM))
        repeat(3) { PokeDoll.migrate(s) }
        Economy.give(s, PokeDoll.ITEM, 13)
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(30, back.dollsFound); assertEquals(1, back.count(PokeDoll.ITEM))
        assertFalse(Economy.take(back, PokeDoll.ITEM))
    }

    @Test fun skipLockSurvivesRestartAndRepeatedSkipsDoNotLiftOrResetIt() {
        val g = game(50)
        assertTrue(g.escape(PokeDoll.ITEM).any { it is GameEvent.Fled })
        val back = reload(g)
        val battle = back.state.battle
        val at = back.state.dollLockedAt
        now += 10 * minute
        assertEquals(PokeDoll.LOCKED, back.escape(PokeDoll.ITEM).filterIsInstance<GameEvent.Info>().single().text)
        assertSame(battle, back.state.battle); assertEquals(at, back.state.dollLockedAt)
        assertEquals(1, back.state.count(PokeDoll.ITEM))
    }

    @Test fun missedBallAndOtherEscapeDoNotUnlock() {
        val g = game()
        g.escape(PokeDoll.ITEM)
        Economy.give(g.state, "fluffy-tail")
        g.escape("fluffy-tail")
        assertEquals(PokeDoll.LOCKED, PokeDoll.blocker(g.state, now))
        Economy.give(g.state, "poke-ball")
        val misses = Game(data, g.state, object : Random() { override fun nextBits(bitCount: Int) = 0; override fun nextInt(until: Int) = until - 1 }) { now }
        assertFalse(misses.throwBall("poke-ball").filterIsInstance<GameEvent.BallThrow>().single().success)
        assertEquals(PokeDoll.LOCKED, PokeDoll.blocker(g.state, now))
    }

    @Test fun ballCatchStartsEveryLevelsCooldownAndCooldownSurvivesRestart() {
        for (found in listOf(1, 5, 15, 30, 50)) {
            val g = game(found)
            g.escape(PokeDoll.ITEM)
            now += 2 * minute
            Economy.give(g.state, "master-ball")
            assertTrue(g.throwBall("master-ball").any { it is GameEvent.Caught })
            val back = reload(g)
            val minutes = PokeDoll.cooldownMinutes(found)
            assertNull(back.state.dollLockedAt)
            assertEquals("Poké Doll: $minutes min left", PokeDoll.blocker(back.state, now))
            now += minutes * minute - 1
            assertEquals("Poké Doll: 1 min left", PokeDoll.blocker(back.state, now))
            now++
            assertNull(PokeDoll.blocker(back.state, now))
            assertTrue(back.escape(PokeDoll.ITEM).any { it is GameEvent.Fled })
        }
    }

    @Test fun normalDefeatAndAutoCatchUnlock() {
        for (outcome in Outcome.entries) {
            val g = game(5)
            g.escape(PokeDoll.ITEM)
            g.state.battle!!.wild.hp = 0
            g.state.battle!!.override = outcome
            val method = Game::class.java.getDeclaredMethod("resolveWild", Battle::class.java, Mon::class.java, MutableList::class.java)
            method.isAccessible = true
            method.invoke(g, g.state.battle!!, g.state.main!!, mutableListOf<GameEvent>())
            assertNull(g.state.dollLockedAt)
            assertEquals(now + 50 * minute, g.state.dollReadyAt)
        }
    }

    @Test fun oneHourFallbackStartsCooldownAtExpiryEvenAcrossRestart() {
        val g = game(50)
        g.escape(PokeDoll.ITEM)
        now += 60 * minute - 1
        assertEquals(PokeDoll.LOCKED, PokeDoll.blocker(g.state, now))
        now++
        val back = reload(g)
        assertEquals("Poké Doll: 30 min left", PokeDoll.blocker(back.state, now))
        now += 30 * minute
        assertNull(PokeDoll.blocker(back.state, now))
        val later = game(5)
        later.escape(PokeDoll.ITEM)
        now += 111 * minute
        assertNull(PokeDoll.blocker(reload(later).state, now))
    }

    @Test fun winAfterFallbackDoesNotRestartCooldown() {
        val g = game(50)
        g.escape(PokeDoll.ITEM)
        now += 75 * minute
        PokeDoll.won(g.state, now)
        assertEquals("Poké Doll: 15 min left", PokeDoll.blocker(g.state, now))
    }

    @Test fun skipPaysFlooredShareOfActualDefeatRewardsWithoutWinBonuses() {
        for (found in listOf(1, 5, 15, 30, 50)) for (share in listOf(false, true)) {
            val g = game(found)
            val s = g.state
            s.main!!.heldItem = "lucky-egg"
            s.main!!.bondPoints = 400
            if (share) {
                val holder = TestData.mon(data, 19, 20, listOf("tackle"), "holder")
                holder.heldItem = "everstone"
                s.mons += holder; s.settings.xpShareUid = holder.uid
            }
            s.cashToday = 105; s.cashFracNum = 2; s.cashFracDen = 3
            val win = SaveCodec.decode(SaveCodec.encode(s))
            val winEvents = mutableListOf<GameEvent>()
            val bond = Bond.level(win.main!!.bondPoints)
            Progression.rewardDefeat(data, win, win.main!!, win.battle!!.wild, now, Random(4), winEvents)
            Economy.onWin(win, winEvents, bond, Random(4))
            val evs = s.main!!.evs; val bp = s.main!!.bondPoints; val defeats = s.defeats
            val skip = g.escape(PokeDoll.ITEM)
            val pct = PokeDoll.rewardPercent(found)
            for (uid in s.mons.map { it.uid }) {
                val full = winEvents.filterIsInstance<GameEvent.XpGained>().filter { it.uid == uid }.sumOf { it.xp }
                val partial = skip.filterIsInstance<GameEvent.XpGained>().filter { it.uid == uid }.sumOf { it.xp }
                assertEquals("$found / $uid", full * pct / 100, partial)
            }
            assertEquals(winEvents.filterIsInstance<GameEvent.Cash>().sumOf { it.amount } * pct / 100,
                skip.filterIsInstance<GameEvent.Cash>().sumOf { it.amount })
            assertEquals(evs, s.main!!.evs); assertEquals(bp, s.main!!.bondPoints); assertEquals(defeats, s.defeats)
            assertFalse(skip.any { it is GameEvent.Defeated || it is GameEvent.Caught })
        }
    }

    @Test fun moneyBonusesAndDailyCurveAreSharedAndRoundedDown() {
        for (held in listOf(null, "amulet-coin", "luck-incense")) for (today in listOf(0, 99, 100, 350)) {
            val g = game(30)
            g.state.main!!.heldItem = held
            g.state.battle!!.field.player["happyhour"] = 1
            g.state.cashToday = today
            val copy = SaveCodec.decode(SaveCodec.encode(g.state))
            val win = mutableListOf<GameEvent>()
            Economy.onWin(copy, win)
            val skip = mutableListOf<GameEvent>()
            Economy.onSkip(g.state, 20, skip)
            assertEquals(win.filterIsInstance<GameEvent.Cash>().sumOf { it.amount } / 5,
                skip.filterIsInstance<GameEvent.Cash>().sumOf { it.amount })
        }
    }

    @Test fun dollIsNotSoldEvenInOldStockButCanBeFoundAndGifted() {
        assertFalse(PokeDoll.ITEM in Items.shopPool(data))
        assertTrue(PokeDoll.ITEM in Items.dropPool())
        assertTrue(PokeDoll.ITEM to 1 in Tuning.GIFT_UNCOMMON)
        val s = game().state
        s.shopItems += PokeDoll.ITEM; s.shopDay = s.day; s.cash = 100000
        assertFalse(Shop.buy(data, s, PokeDoll.ITEM).ok)
        Shop.ensureToday(data, s)
        assertFalse(PokeDoll.ITEM in s.shopItems)
        val gifts = (0..9999).map { DailyGift.draw(data, "day-$it", 30)!! }
        assertTrue(gifts.count { it.item == PokeDoll.ITEM } in 800..1200)
    }

    @Test fun runawayCannotBypassDollLockOrCooldown() {
        val g = game(50)
        g.state.main!!.ability = "Run Away"
        g.escape(PokeDoll.ITEM)
        assertEquals(PokeDoll.LOCKED, g.escape(PokeDoll.ITEM).filterIsInstance<GameEvent.Info>().single().text)
        PokeDoll.won(g.state, now)
        assertEquals("Poké Doll: 30 min left", g.escape(PokeDoll.ITEM).filterIsInstance<GameEvent.Info>().single().text)
    }

    @Test fun importedDollsUseTheFoundCounterAndRepeatedImportIsIdempotent() {
        val s = GameState()
        val save = com.husarp.ankigo.game.ankimon.AnkimonSave(emptyList(), emptyList(),
            mapOf(PokeDoll.ITEM to 15), emptyList(), null, emptySet(), emptySet(), emptyList())
        repeat(3) {
            com.husarp.ankigo.game.ankimon.AnkimonMerge.merge(data, s, save, { null }, false, now)
            assertEquals(15, s.dollsFound); assertEquals(1, s.count(PokeDoll.ITEM))
        }
        Economy.give(s, PokeDoll.ITEM)
        com.husarp.ankigo.game.ankimon.AnkimonMerge.merge(data, s, save, { null }, false, now)
        assertEquals(16, s.dollsFound)
    }

    @Test fun wishlistWeightsAreTwoAndFourAndTierRollIsUnchanged() {
        assertEquals(2, PokeDoll.wishlistWeight(49)); assertEquals(4, PokeDoll.wishlistWeight(50))
        val pool = listOf(16, 19, 54)
        for (weight in listOf(2, 4)) {
            val picks = (0 until weight + 2).map { roll ->
                Encounters.pickFrom(data, pool, setOf(19), object : Random() {
                    override fun nextBits(bitCount: Int) = 0
                    override fun nextInt(until: Int): Int { assertEquals(weight + 2, until); return roll }
                }, weight)
            }
            assertEquals(weight, picks.count { it == 19 }); assertEquals(1, picks.count { it == 16 })
        }
        val a = game(49).state; val b = game(50).state
        repeat(200) { seed -> assertEquals(Encounters.roll(data, a, 25, Random(seed)).tier, Encounters.roll(data, b, 25, Random(seed)).tier) }
    }

    @Test fun infoShowsCurrentAndNextPerksAndNeverHidesTheToolAfterUse() {
        val g = game(30)
        val text = PokeDoll.facts(g.state, now).joinToString { it.title + " " + it.text }
        for (part in listOf("Level 4", "30 / 50", "35 min", "20%", "30 min", "wishlist weight ×2")) assertTrue(part, text.contains(part))
        g.escape(PokeDoll.ITEM)
        assertEquals(1, g.state.count(PokeDoll.ITEM))
        assertTrue(ItemInfo.whereToFind(data, PokeDoll.ITEM).contains("Daily gifts"))
    }
}
