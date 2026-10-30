package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class R22GMovesTest {
    private val extra = (RemainingMoves.handles + setOf("embargo", "magicroom", "decorate")).associateWith {
        Move(it, it, Type.NORMAL, if (it == "fling") Category.PHYSICAL else Category.STATUS,
            if (it == "fling") 1 else 0, null,
            priority = if (it in setOf("teleport", "roar", "whirlwind")) -6 else 0,
            flags = if (it == "roar") setOf("sound") else emptySet(),
            target = if (it == "teleport") "self" else if (it in setOf("magicroom", "happyhour")) "all" else "normal",
            boosts = if (it == "decorate") mapOf("atk" to 2, "spa" to 2) else null)
    }
    private val data = GameData(R17C3.data.species, R17C3.data.moves + extra, R17C3.data.growth, R17C3.data.itemPrices)
    private fun main() = R17C3.main("splash")
    private fun battle() = R17C3.battle(R17C3.wild("splash"))
    private fun play(m: Mon, b: Battle, id: String, player: Boolean = true): List<GameEvent> {
        val force = object : BattleRules {
            override fun forcedMove(v: BattleView, p: Boolean): Move? = if (p == player) data.move(id) else data.move("splash")
        }
        return R17C3.withRules(force) { BattleEngine.playRound(data, b, m, 1.0, true, Random(7)) }
    }
    private fun state(m: Mon, b: Battle) = GameState().also { it.mons += m; it.team += m.uid; it.battle = b }

    @Test fun embargoSuppressesForFiveRoundsWithoutRemovingTheItem() {
        val m = main().also { it.heldItem = "amulet-coin" }; val b = battle(); val s = state(m, b)
        play(m, b, "embargo", player = false)
        assertEquals(4, b.player.volatiles["embargo"])
        assertNull(s.activeHeldItem(m)); assertEquals("amulet-coin", m.heldItem)
        repeat(3) { play(m, b, "splash"); assertNull(s.activeHeldItem(m)) }
        play(m, b, "splash")
        assertEquals("amulet-coin", s.activeHeldItem(m)); assertFalse("embargo" in b.player.volatiles)
    }
    @Test fun embargoDoesNotRestartAndFreeHitsDoNotTickItsDuration() {
        val m = main(); val b = battle().also { it.wild.heldItem = "lucky-egg" }
        play(m, b, "embargo")
        val e = play(m, b, "embargo").filterIsInstance<GameEvent.Attack>().single { it.byPlayer }
        assertTrue(e.failed); assertEquals(3, b.enemy.volatiles["embargo"])
        BattleEngine.switchHit(data, b, m, Random(4))
        assertEquals(3, b.enemy.volatiles["embargo"])
    }
    @Test fun magicRoomSuppressesBothSidesAndTogglesOff() {
        val m = main().also { it.heldItem = "macho-brace" }; val b = battle().also { it.wild.heldItem = "lucky-egg" }; val s = state(m, b)
        play(m, b, "magicroom")
        assertEquals(4, b.field.rooms["magicroom"])
        assertNull(s.activeHeldItem(m)); assertNull(s.activeHeldItem(b.wild))
        play(m, b, "magicroom")
        assertFalse("magicroom" in b.field.rooms); assertEquals("macho-brace", s.activeHeldItem(m))
    }
    @Test fun smartMagicRoomRecognizesWhenSuppressionHelps() {
        val m = main(); val b = battle(); val view = SimpleView.of(data, b, m)
        val move = data.move("magicroom")!!
        assertFalse(MoveChooser.statusUseful(MoveChoice.SMART, move, view, true))
        b.wild.heldItem = "lucky-egg"
        assertTrue(MoveChooser.statusUseful(MoveChoice.SMART, move, view, true))
        b.field.rooms["magicroom"] = 3
        assertFalse(MoveChooser.statusUseful(MoveChoice.SMART, move, view, true))
        m.heldItem = "amulet-coin"
        assertTrue(MoveChooser.statusUseful(MoveChoice.SMART, move, view, true))
    }
    @Test fun magicRoomExpiresAndBenchedItemsKeepWorking() {
        val m = main(); val b = battle(); val s = state(m, b)
        val bench = main().also { it.uid = "bench"; it.heldItem = "lucky-egg" }
        play(m, b, "magicroom")
        assertEquals("lucky-egg", s.activeHeldItem(bench))
        repeat(4) { play(m, b, "splash") }
        assertFalse("magicroom" in b.field.rooms)
    }
    @Test fun embargoAndMagicRoomDisableActualMoneyAndEvBonuses() {
        fun money(blocked: Boolean, happy: Boolean): Int {
            val m = main().also { it.heldItem = "amulet-coin" }; val b = battle(); val s = state(m, b)
            if (blocked) b.player.volatiles["embargo"] = 5
            if (happy) b.field.player["happyhour"] = 1
            Economy.onWin(s, mutableListOf())
            return s.cash
        }
        assertEquals(money(true, false) * 2, money(false, false))
        assertEquals(money(false, false) * 2, money(false, true))
        val m = main().also { it.heldItem = "macho-brace" }; val b = battle(); val s = state(m, b)
        b.field.rooms["magicroom"] = 5
        Progression.addEvs(m, Stats(1, 0, 0, 0, 0, 0), s.activeHeldItem(m))
        assertEquals(1, m.evs.hp)
        b.field.rooms.clear()
        Progression.addEvs(m, Stats(1, 0, 0, 0, 0, 0), s.activeHeldItem(m))
        assertEquals(3, m.evs.hp)
    }
    @Test fun suppressionDisablesLuckyEggAndSootheBellWhenRewardsArePaid() {
        fun reward(item: String, blocked: Boolean, caught: Boolean): Pair<Int, Int> {
            val m = main().also { it.heldItem = item }; val b = battle(); val s = state(m, b)
            val before = m.xp
            if (blocked) b.player.volatiles["embargo"] = 2
            if (caught) Progression.rewardCatch(data, s, m, b.wild, 1L, Random(7), mutableListOf())
            else Progression.rewardDefeat(data, s, m, b.wild, 1L, Random(7), mutableListOf())
            return (m.xp - before) to m.friendship
        }
        for (caught in listOf(false, true)) {
            val ordinary = reward("lucky-egg", true, caught).first
            assertEquals((ordinary * Tuning.LUCKY_EGG).toInt(), reward("lucky-egg", false, caught).first)
            assertTrue(reward("soothe-bell", false, caught).second > reward("soothe-bell", true, caught).second)
        }
    }
    @Test fun trickAndSwitcherooSwapItemsAndStickyHoldStopsThem() {
        for (id in listOf("trick", "switcheroo")) {
            val m = main().also { it.heldItem = "lucky-egg" }; val b = battle().also { it.wild.heldItem = "amulet-coin" }
            play(m, b, id)
            assertEquals("amulet-coin", m.heldItem); assertEquals("lucky-egg", b.wild.heldItem)
            b.wild.ability = "Sticky Hold"
            assertTrue(play(m, b, id).filterIsInstance<GameEvent.Attack>().first { it.byPlayer }.failed)
            assertEquals("amulet-coin", m.heldItem)
            m.ability = "Mold Breaker"
            assertTrue(MoveChooser.statusUseful(MoveChoice.SMART, data.move(id)!!, SimpleView.of(data, b, m), true))
            play(m, b, id)
            assertEquals("lucky-egg", m.heldItem)
        }
    }
    @Test fun bestowRequiresAnEmptyTargetAndCorrosiveGasRemovesItsItem() {
        val m = main().also { it.heldItem = "lucky-egg" }; val b = battle()
        play(m, b, "bestow"); assertNull(m.heldItem); assertEquals("lucky-egg", b.wild.heldItem)
        b.wild.ability = "Sticky Hold"; play(m, b, "corrosivegas"); assertEquals("lucky-egg", b.wild.heldItem)
        b.wild.ability = ""; play(m, b, "corrosivegas"); assertNull(b.wild.heldItem)
        assertFalse("consumeditem" in b.enemy.notes)
    }
    @Test fun flingConsumesTheItemAndRecycleRestoresItExactlyOnce() {
        val m = main().also { it.heldItem = "macho-brace" }; val b = battle()
        val hp = b.wild.hp
        play(m, b, "fling"); assertNull(m.heldItem); assertTrue(b.wild.hp < hp)
        assertEquals("macho-brace", b.player.notes["consumeditem"])
        play(m, b, "recycle"); assertEquals("macho-brace", m.heldItem); assertFalse("consumeditem" in b.player.notes)
        m.heldItem = null
        assertTrue(play(m, b, "recycle").filterIsInstance<GameEvent.Attack>().first { it.byPlayer }.failed)
    }
    @Test fun embargoStopsFlingWithoutConsumingAnything() {
        val m = main().also { it.heldItem = "lucky-egg" }; val b = battle(); b.player.volatiles["embargo"] = 3
        val hp = b.wild.hp
        play(m, b, "fling"); assertEquals(hp, b.wild.hp); assertEquals("lucky-egg", m.heldItem)
    }
    @Test fun healingMovesHealTheTargetAndRespectHealBlock() {
        for (id in listOf("healpulse", "floralhealing")) {
            val m = main(); val b = battle(); val max = b.wild.hp; b.wild.hp = 1
            play(m, b, id); assertEquals(1 + max / 2, b.wild.hp)
            b.enemy.volatiles["healblock"] = 5; b.wild.hp = 1
            play(m, b, id); assertEquals(1, b.wild.hp)
        }
    }
    @Test fun purifyCuresTheTargetThenHealsTheUser() {
        val m = main().also { it.hp = 1 }; val b = battle(); b.enemy.status = "par"
        play(m, b, "purify"); assertNull(b.enemy.status); assertEquals(1 + StatCalc.maxHp(data, m) / 2, m.hp)
    }
    @Test fun decorateCanBoostTheOpponentInASinglesBattle() {
        val m = main(); val b = battle()
        play(m, b, "decorate")
        assertEquals(2, b.enemy.boosts[0]); assertEquals(2, b.enemy.boosts[2])
        assertEquals(0, b.player.boosts[0]); assertEquals(0, b.player.boosts[2])
    }
    @Test fun plusAndMinusSupportMovesBoostOnlyMatchingUsers() {
        for ((id, indices) in listOf("gearup" to listOf(0, 2), "magneticflux" to listOf(1, 3))) {
            val m = main(); val b = battle()
            play(m, b, id); assertTrue(b.player.boosts.all { it == 0 })
            m.ability = "Plus"; play(m, b, id)
            indices.forEach { assertEquals(1, b.player.boosts[it]) }
        }
    }
    @Test fun mementoLowersTheTargetAndFaintsTheUser() {
        val m = main(); val b = battle()
        play(m, b, "memento"); assertEquals(0, m.hp)
        assertEquals(-2, b.enemy.boosts[0]); assertEquals(-2, b.enemy.boosts[2])
    }
    @Test fun mementoStillFaintsTheUserWhenTheTargetsStatsCannotFall() {
        val m = main(); val b = battle(); b.wild.ability = "Clear Body"
        play(m, b, "memento")
        assertEquals(0, m.hp); assertEquals(0, b.enemy.boosts[0]); assertEquals(0, b.enemy.boosts[2])
    }
    @Test fun happyHourDoesNotStack() {
        val m = main(); val b = battle()
        play(m, b, "happyhour")
        assertTrue(play(m, b, "happyhour").filterIsInstance<GameEvent.Attack>().first { it.byPlayer }.failed)
        assertEquals(1, b.field.player["happyhour"])
    }
    @Test fun perishSongCountsThreeMoreRoundsAndSoundproofBlocksIt() {
        val m = main(); val b = battle(); b.wild.ability = "Soundproof"
        play(m, b, "perishsong")
        assertEquals(3, b.player.volatiles["perishsong"]); assertFalse("perishsong" in b.enemy.volatiles)
        repeat(2) { play(m, b, "splash"); assertTrue(m.hp > 0) }
        play(m, b, "splash"); assertEquals(0, m.hp); assertTrue(b.wild.hp > 0)
    }
    @Test fun assistCallsATeammateMoveAndInstructCannotRecurse() {
        val m = main(); val b = battle()
        val teammate = R17C3.main("tackle").also { it.uid = "teammate" }
        b.team = listOf(m, teammate)
        val hp = b.wild.hp
        play(m, b, "assist"); assertTrue(b.wild.hp < hp)
        b.enemy.lastMove = "instruct"
        assertTrue(play(m, b, "instruct").filterIsInstance<GameEvent.Attack>().first { it.byPlayer }.failed)
        b.enemy.lastMove = "tackle"
        val old = m.hp
        play(m, b, "instruct"); assertTrue(m.hp < old)
    }
    @Test fun revivalBlessingRevivesTheFirstFaintedTeammate() {
        val m = main(); val b = battle()
        val first = main().also { it.uid = "first"; it.hp = 0 }
        val second = main().also { it.uid = "second"; it.hp = 0 }
        b.team = listOf(m, first, second)
        play(m, b, "revivalblessing")
        assertEquals(StatCalc.maxHp(data, first) / 2, first.hp); assertEquals(0, second.hp)
    }
    @Test fun moveStateSurvivesASaveWithoutLosingOrDuplicatingItems() {
        val m = main().also { it.heldItem = "lucky-egg" }; val b = battle(); val s = state(m, b)
        b.player.volatiles["embargo"] = 3; b.player.volatiles["perishsong"] = 2
        b.enemy.notes["consumeditem"] = "soothe-bell"; b.field.rooms["magicroom"] = 4
        b.field.player["happyhour"] = 1
        val loaded = SaveCodec.decode(SaveCodec.encode(s))
        assertNull(loaded.activeHeldItem(loaded.main!!))
        assertEquals("lucky-egg", loaded.main!!.heldItem)
        assertEquals(3, loaded.battle!!.player.volatiles["embargo"])
        assertEquals(2, loaded.battle!!.player.volatiles["perishsong"])
        assertEquals("soothe-bell", loaded.battle!!.enemy.notes["consumeditem"])
        assertEquals(4, loaded.battle!!.field.rooms["magicroom"])
        assertEquals(1, loaded.battle!!.field.player["happyhour"])
    }
    @Test fun escapeMovesEndEitherSidesWildBattleWithoutResiduals() {
        for (id in listOf("teleport", "roar", "whirlwind")) for (player in listOf(true, false)) {
            val m = main(); val b = battle(); val hp = m.hp
            b.player.status = "psn"; b.field.player["tailwind"] = 3
            val events = play(m, b, id, player)
            assertEquals("$id $player", 1, events.filterIsInstance<GameEvent.Fled>().size)
            assertEquals(1, b.field.player[RemainingMoves.ESCAPED])
            assertEquals(hp, m.hp); assertEquals(3, b.field.player["tailwind"])
        }
    }
    @Test fun suctionCupsAndIngrainStopForcedEscapeAndMoldBreakerIgnoresSuctionCups() {
        for (id in listOf("roar", "whirlwind")) for (guard in listOf("suctioncups", "ingrain")) {
            val m = main(); val b = battle()
            if (guard == "suctioncups") b.wild.ability = "Suction Cups" else b.enemy.volatiles["ingrain"] = 1
            assertTrue(play(m, b, id).filterIsInstance<GameEvent.Fled>().isEmpty())
            assertNull(b.field.player[RemainingMoves.ESCAPED])
            if (guard == "suctioncups") {
                m.ability = "Mold Breaker"
                assertTrue(MoveChooser.statusUseful(MoveChoice.SMART, data.move(id)!!, SimpleView.of(data, b, m), true))
                assertEquals(1, play(m, b, id).filterIsInstance<GameEvent.Fled>().size)
            }
        }
    }
    @Test fun teleportUsesTheExistingEscapeOutcomeWhenSwitchingIsTrapped() {
        val m = main(); val b = battle(); b.player.volatiles[Hazards.TRAP] = 3
        assertNotNull(Hazards.blocker(SimpleView.of(data, b, m)))
        assertEquals(1, play(m, b, "teleport").filterIsInstance<GameEvent.Fled>().size)
    }
    @Test fun aCalledEscapeKeepsEventOrderAndStopsTheOtherAction() {
        val m = main(); val b = battle()
        val caller = Move("testcaller", "Caller", Type.NORMAL, Category.STATUS, 0, null, target = "self")
        val call = object : BattleRules {
            override fun forcedMove(v: BattleView, p: Boolean): Move? = if (p) caller else data.move("splash")
            override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? =
                if (move.id == caller.id) r.useMove(p, data.move("teleport")!!) else null
        }
        val events = R17C3.withRules(call) { BattleEngine.playRound(data, b, m, 1.0, true, Random(7)) }
        assertEquals(listOf("testcaller", "teleport"), events.filterIsInstance<GameEvent.Attack>().map { it.moveId })
        assertTrue(events.last() is GameEvent.Fled)
    }
    @Test fun gameSettlesEscapeAndStartsANewEncounterWithoutAWinReward() {
        for (id in listOf("teleport", "roar", "whirlwind")) for (player in listOf(true, false)) {
            val m = main(); val b = battle(); val s = state(m, b).also { it.starterChosen = true }
            val g = Game(data, s, Random(7)) { 1L }
            g.roundAnswers = 1; s.day = g.dayKey()
            val force = object : BattleRules {
                override fun forcedMove(v: BattleView, p: Boolean): Move? = if (p == player) data.move(id) else data.move("splash")
            }
            val events = R17C3.withRules(force) { g.onAnswer(Answer(1L, if (player) 3 else 1, 1L)) }
            assertEquals("$id $player", 1, events.filterIsInstance<GameEvent.Fled>().size)
            assertTrue(events.any { it is GameEvent.Encounter })
            assertTrue(events.none { it is GameEvent.Defeated || it is GameEvent.Caught })
            assertTrue(events.filterIsInstance<GameEvent.Cash>().none { it.kind == CashKind.DEFEAT })
            assertEquals(0, s.defeats); assertEquals(0, m.xp)
            assertNotSame(b, s.battle); assertNull(s.battle!!.field.player[RemainingMoves.ESCAPED])
        }
    }
    @Test fun wildTeleportCanEndTheFreeHitOfATeamSwitch() {
        val m = main(); val b = battle(); val s = state(m, b).also { it.starterChosen = true }
        val bench = main().also { it.uid = "bench" }
        s.mons += bench; s.team += bench.uid; b.rounds = 1
        b.wild.moves.clear(); b.wild.moves += "teleport"
        val g = Game(data, s, Random(7)) { 1L }; s.day = g.dayKey()
        val result = g.setMain(bench.uid)
        assertTrue(result.events.any { it is GameEvent.Fled })
        assertEquals(bench.uid, s.main!!.uid); assertNotSame(b, s.battle)
        assertTrue(b.wild.hp > 0)
    }
    @Test fun realMoveTextAudit() {
        val file = System.getenv("ANKIGO_R22_MOVES")?.let(::File)
        assumeTrue(file != null && file.isFile)
        val moves = Parsers.parseMoves(file!!.readText())
        val changed = RemainingMoves.handles + setOf("embargo", "magicroom", "perishsong", "decorate")
        for (id in changed) {
            assertNotNull(id, moves[id])
            val text = MoveText.describe(moves.getValue(id))
            assertFalse("$id: $text", "Never used" in text || "Has no effect" in text)
            assertFalse(id, id in CoreRules.NEVER_PICKED)
            if (moves.getValue(id).priority < 0) assertTrue("$id: $text", "Usually goes last." in text)
        }
        val unhandled = moves.values.filter { "Has no effect in AnkiGo battles" in MoveText.describe(it) }.map { it.id }.sorted()
        println("R22G_HAS_NO_EFFECT=$unhandled")
        println("R22G_NEVER_PICKED=${CoreRules.NEVER_PICKED.keys.sorted()}")
        assertTrue("Unaudited moves: $unhandled", unhandled.isEmpty())
    }
}
