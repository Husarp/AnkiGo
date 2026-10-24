package com.husarp.ankigo.game

import com.husarp.ankigo.game.GameEvent.Attack
import com.husarp.ankigo.game.GameEvent.Effect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor
import kotlin.random.Random

/**
 * R17C-1: the rule hooks are called at their places and combine as documented (BattleRules KDoc), and with every hook neutral the
 * engine plays exactly the old rounds. Test rule sets are injected with [BattleRules.using].
 */
class R17C1EngineTest {
    private val extraMoves = listOf(
        Move("custom1", "Custom One", Type.NORMAL, Category.STATUS, 0, null, target = "self"),
        Move("lowacc", "Low Acc", Type.NORMAL, Category.PHYSICAL, 40, 30),
        Move("twosec", "Two Sec", Type.NORMAL, Category.PHYSICAL, 40, 100,
            secondary = Secondary(100, status = "brn"), moreSecondaries = listOf(Secondary(100, flinch = true, volatileStatus = "flinch"))),
        Move("parhit", "Par Hit", Type.NORMAL, Category.PHYSICAL, 40, 100, secondary = Secondary(100, status = "par")),
        Move("snorish", "Snorish", Type.NORMAL, Category.SPECIAL, 40, 100, sleepUsable = true),
        // the "other stat" moves, typed Normal so a Water user has no STAB and a Normal target takes them neutrally
        Move("psyshockish", "Psyshockish", Type.NORMAL, Category.SPECIAL, 80, 100, defensiveStat = "def"),
        Move("plainspecial", "Plain Special", Type.NORMAL, Category.SPECIAL, 80, 100),
        Move("bodypressish", "Body Pressish", Type.NORMAL, Category.PHYSICAL, 80, 100, offensiveStat = "def"),
        Move("plainphysical", "Plain Physical", Type.NORMAL, Category.PHYSICAL, 80, 100),
        Move("foulplayish", "Foul Playish", Type.NORMAL, Category.PHYSICAL, 95, 100, offensiveFromTarget = true),
        Move("swordish", "Swordish", Type.NORMAL, Category.PHYSICAL, 80, 100, ignoreDefensive = true, ignoreEvasion = true),
    )
    private val world = TestData.world()
    private val attackerSp = TestData.species(200, "Attacker", listOf(Type.WATER), TestData.stats(50, 100, 40, 80, 60, 50))
    private val defenderSp = TestData.species(201, "Defender", listOf(Type.NORMAL), TestData.stats(50, 70, 120, 50, 30, 50))
    private val data = TestData.data(world.species.values.toList() + attackerSp + defenderSp, TestData.MOVES + extraMoves.associateBy { it.id })

    private fun mon(sp: Int, lv: Int, vararg moves: String) = TestData.mon(data, sp, lv, moves.toList())
    private fun battle(wild: Mon) = Battle(wild, Tier.NORMAL)
    private fun round(main: Mon, b: Battle, acts: Boolean = true, seed: Int = 1, choice: MoveChoice = MoveChoice.RANDOM, rng: Random = Random(seed)) =
        BattleEngine.playRound(data, b, main, 1.0, acts, rng, moveChoice = choice)

    private fun List<GameEvent>.attacks() = filterIsInstance<Attack>()
    private fun List<GameEvent>.effects() = filterIsInstance<Effect>()
    private fun List<GameEvent>.lines() = effects().map { it.text }

    private fun <T> under(vararg rules: BattleRules, block: () -> T): T = BattleRules.using(rules.toList(), block)

    private fun hit(move: String, a: Mon = mon(19, 20), d: Mon = mon(16, 20)): Hit =
        BattleEngine.newHit(SimpleView(data, a, d), true, a, BattleSide(), d, BattleSide(), data.move(move)!!)

    private fun dmg(a: Mon, d: Mon, move: String, aSide: BattleSide = BattleSide(), dSide: BattleSide = BattleSide(), roll: Int = 100) =
        BattleEngine.damage(data, a, aSide, d, dSide, data.move(move)!!, Random(1), false, roll)

    /** Always the biggest random numbers: a hit chance roll misses, a wake-up roll fails. */
    private val highRandom = object : Random() { override fun nextBits(bitCount: Int) = 0x7FFFFFFF shr (31 - bitCount) }
    private val midRandom = object : Random() { override fun nextBits(bitCount: Int) = if (bitCount == 0) 0 else 1 shl (bitCount - 1) }

    // ---------------------------------------------------------------- the sets and how they combine

    @Test fun theSevenSetsAreListedInTheDocumentedOrder() { // R17C-9b: AbilitiesMore comes after Abilities
        assertEquals(listOf("CoreRules", "MovePower", "SideMoves", "FieldRules", "MoveControl", "Abilities", "AbilitiesMore"), BattleRules.ALL.map { it::class.simpleName })
    }

    @Test fun usingPutsTheRealListBack() {
        val before = BattleRules.ALL
        BattleRules.using(emptyList()) { assertTrue(BattleRules.ALL.isEmpty()) }
        assertEquals(before, BattleRules.ALL)
        try { BattleRules.using(emptyList()) { error("boom") } } catch (e: IllegalStateException) { /* expected */ }
        assertEquals(before, BattleRules.ALL)
    }

    @Test fun firstNonNullWins() {
        val a = object : BattleRules { override fun basePower(h: Hit): Int? = null }
        val b = object : BattleRules { override fun basePower(h: Hit): Int? = 50 }
        val c = object : BattleRules { override fun basePower(h: Hit): Int? = 70 }
        under(a, b, c) { assertEquals(50, rulesFirst { it.basePower(hit("tackle")) }) }
        under(a) { assertNull(rulesFirst { it.basePower(hit("tackle")) }) }
    }

    @Test fun chainedHooksSeeTheAnswerSoFarInOrder() {
        val toFire = object : BattleRules { override fun moveType(h: Hit, type: Type) = if (type == Type.NORMAL) Type.FIRE else type }
        val fireToWater = object : BattleRules { override fun moveType(h: Hit, type: Type) = if (type == Type.FIRE) Type.WATER else type }
        under(toFire, fireToWater) { assertEquals(Type.WATER, hit("tackle").type) }
        under(fireToWater, toFire) { assertEquals(Type.FIRE, hit("tackle").type) } // the other order stops at Fire
    }

    @Test fun multipliedHooksMultiplyAndAddedOnesAdd() {
        val two = object : BattleRules { override fun powerMod(h: Hit) = 2.0; override fun priority(v: BattleView, p: Boolean, move: Move) = 1 }
        val oneAndHalf = object : BattleRules { override fun powerMod(h: Hit) = 1.5; override fun priority(v: BattleView, p: Boolean, move: Move) = 2 }
        under(two, oneAndHalf) {
            assertEquals(3.0, rulesProduct { it.powerMod(hit("tackle")) }, 0.0)
            assertEquals(3, rulesSum { it.priority(SimpleView(data, mon(19, 5), mon(16, 5)), true, data.move("tackle")!!) })
            // 40 x 3 = 120 power: the same damage as a 120 power move without any set
            val boosted = dmg(mon(19, 20), mon(16, 20), "tackle").damage
            val plain = BattleRules.using(emptyList()) { BattleEngine.damage(data, mon(19, 20), BattleSide(), mon(16, 20), BattleSide(), data.move("tackle")!!.copy(power = 120), Random(1), false, 100).damage }
            assertEquals(plain, boosted)
        }
    }

    @Test fun anyAndAllStopAtTheFirstDecision() {
        val calls = mutableListOf<String>()
        val no = object : BattleRules { override fun suppressWeather(v: BattleView): Boolean { calls += "no"; return false } }
        val yes = object : BattleRules { override fun suppressWeather(v: BattleView): Boolean { calls += "yes"; return true } }
        val later = object : BattleRules { override fun suppressWeather(v: BattleView): Boolean { calls += "later"; return false } }
        val view = SimpleView(data, mon(19, 5), mon(16, 5))
        under(no, yes, later) { assertTrue(rulesAny { it.suppressWeather(view) }) }
        assertEquals(listOf("no", "yes"), calls)
        calls.clear()
        under(yes, no) { assertFalse(rulesAll { !it.suppressWeather(view) }) }
        assertEquals(listOf("yes"), calls)
    }

    // ---------------------------------------------------------------- neutral = the old engine

    private fun fight(choice: MoveChoice): List<GameEvent> {
        val main = mon(25, 30, "tackle", "thunderbolt", "growl", "swordsdance")
        val wild = mon(150, 25, "tackle", "bodyslam", "hypnosis", "growl")
        val b = battle(wild)
        val out = ArrayList<GameEvent>()
        val rng = Random(11)
        repeat(8) { out += BattleEngine.playRound(data, b, main, 1.0, it % 3 != 2, rng, moveChoice = choice) }
        return out
    }

    @Test fun anEmptyListAndTheSixEmptySetsPlayTheSame() {
        for (choice in MoveChoice.entries) {
            val real = fight(choice)
            val bare = BattleRules.using(emptyList()) { fight(choice) }
            assertEquals(choice.name, bare, real)
            assertTrue("a real fight, not an empty one", real.attacks().size > 2)
        }
    }

    // ---------------------------------------------------------------- the turn: enter, before, blocked, forced, fail, blocks, immune

    @Test fun onEnterRunsOncePerSideAndAgainForAFreshSide() {
        val entered = mutableListOf<Boolean>()
        val rule = object : BattleRules { override fun onEnter(r: Round, p: Boolean) { entered += p } }
        val main = mon(19, 20, "tackle"); val wild = mon(150, 20, "tackle")
        val b = battle(wild)
        under(rule) {
            round(main, b); round(main, b)
            assertEquals(listOf(false, true), entered) // the faster Pokémon first, never again
            assertTrue(b.player.entered && b.enemy.entered)
            b.switchOutMain(data, main)
            assertFalse(b.player.entered)
            round(main, b)
            assertEquals(listOf(false, true, true), entered)
        }
    }

    @Test fun beforeMoveFalseStopsTheMoveButNotTheTurnCount() {
        val rule = object : BattleRules {
            override fun beforeMove(r: Round, p: Boolean, move: Move): Boolean {
                if (p) return true
                r.fx(p, "The wild one loafs.", EffectKind.CANT_MOVE, "truant"); return false
            }
        }
        val main = mon(19, 20, "tackle"); val wild = mon(150, 20, "tackle")
        val b = battle(wild)
        val hp = main.hp
        val ev = under(rule) { round(main, b) }
        assertEquals(listOf(true), ev.attacks().map { it.byPlayer })
        assertTrue("The wild one loafs." in ev.lines())
        assertEquals(hp, main.hp)
        assertEquals(1, b.enemy.actions); assertNull(b.enemy.lastMove)
        assertEquals(1, b.player.actions); assertEquals("tackle", b.player.lastMove)
    }

    @Test fun beforeMoveStopsAtTheFirstFalse() {
        val calls = mutableListOf<String>()
        val first = object : BattleRules { override fun beforeMove(r: Round, p: Boolean, move: Move): Boolean { calls += "first"; return false } }
        val second = object : BattleRules { override fun beforeMove(r: Round, p: Boolean, move: Move): Boolean { calls += "second"; return true } }
        val main = mon(19, 20, "tackle"); val wild = mon(150, 20, "tackle")
        under(first, second) { round(main, battle(wild), acts = false) }
        assertEquals(listOf("first"), calls)
    }

    @Test fun aBlockedMoveSaysSoAndFails() {
        val rule = object : BattleRules {
            override fun moveBlocked(v: BattleView, p: Boolean, move: Move) = if (!p && move.id == "tackle") "after the taunt" else null
        }
        val main = mon(19, 20, "tackle"); val wild = mon(150, 20, "tackle")
        val hp = main.hp
        val ev = under(rule) { round(main, battle(wild)) }
        val wildAttack = ev.attacks().single { !it.byPlayer }
        assertTrue(wildAttack.failed)
        assertTrue("Mindlord can't use Tackle after the taunt!" in ev.lines())
        assertEquals(hp, main.hp)
    }

    @Test fun theMoveChoiceNeverPicksABlockedMoveWhenItHasAnother() {
        val rule = object : BattleRules {
            override fun moveBlocked(v: BattleView, p: Boolean, move: Move) = if (!p && move.id == "tackle") "" else null
        }
        val main = mon(19, 20, "tackle"); val wild = mon(150, 20, "tackle", "growl")
        val view = SimpleView(data, main, wild)
        under(rule) {
            for (choice in MoveChoice.entries) for (s in 1..40) assertEquals("$choice $s", "growl", MoveChooser.pick(choice, view, false, Random(s)).id)
            // no phrase = "can't use Tackle!"
            val ev = round(main, battle(mon(150, 20, "tackle")))
            assertTrue("Mindlord can't use Tackle!" in ev.lines())
        }
    }

    @Test fun aForcedMoveReplacesThePickInEveryChoiceAndInTheSwitchHit() {
        val rule = object : BattleRules {
            override fun forcedMove(v: BattleView, p: Boolean): Move? = if (!p) v.data.move("growl") else null
        }
        val main = mon(19, 20, "tackle"); val wild = mon(150, 20, "tackle", "bodyslam")
        under(rule) {
            for (choice in MoveChoice.entries) {
                val ev = round(main, battle(wild), choice = choice)
                assertEquals(choice.name, listOf("Growl"), ev.attacks().filter { !it.byPlayer }.map { it.move })
                assertEquals("Growl", BattleEngine.switchHit(data, battle(wild), main, Random(3), choice).attacks().single().move)
            }
        }
    }

    @Test fun failReasonFailsTheMoveWithItsLine() {
        val rule = object : BattleRules {
            override fun failReason(r: Round, p: Boolean, move: Move): String? = if (p && move.id == "tackle") "But nothing could be done!" else null
        }
        val main = mon(19, 20, "tackle"); val wild = mon(150, 20, "splash")
        val hp = wild.hp
        val ev = under(rule) { round(main, battle(wild), acts = false) }
        assertTrue(ev.attacks().single().failed)
        assertEquals(hp, wild.hp)
        assertTrue("But nothing could be done!" in ev.lines())
    }

    @Test fun blocksStopsOnlyMovesAimedAtTheOtherSide() {
        val rule = object : BattleRules {
            override fun blocks(r: Round, p: Boolean, move: Move): Boolean { r.fx(!p, "Protected!", EffectKind.BLOCKED, "protect", label = "Protected"); return true }
            override fun forcedMove(v: BattleView, p: Boolean): Move? = if (p && v.battle?.rounds == 1) v.data.move("swordsdance") else null
        }
        val main = mon(19, 20, "tackle"); val wild = mon(150, 20, "splash")
        val b = battle(wild)
        val hp = wild.hp
        under(rule) {
            val ev1 = round(main, b, acts = false)
            assertTrue(ev1.attacks().single().failed); assertEquals(hp, wild.hp)
            assertEquals(EffectKind.BLOCKED, ev1.effects().single().kind)
            val ev2 = round(main, b, acts = false) // Swords Dance targets the user: not asked
            assertFalse(ev2.attacks().single().failed); assertEquals(2, b.player.boosts[0])
        }
    }

    @Test fun immuneStopsDamagingAndStatusMovesAimedAtTheOtherSide() {
        val rule = object : BattleRules { override fun immune(r: Round, h: Hit): Boolean { r.fx(!h.attackerIsMain, "It is immune."); return true } }
        val wild = mon(150, 20, "splash")
        val hp = wild.hp
        val ev = under(rule) { round(mon(19, 20, "tackle"), battle(wild), acts = false) }
        assertTrue(ev.attacks().single().failed); assertEquals(hp, wild.hp); assertTrue("It is immune." in ev.lines())
        val b2 = battle(mon(150, 20, "splash"))
        val ev2 = under(rule, forced("thunderwave")) { round(mon(19, 20, "tackle"), b2, acts = false, rng = midRandom) }
        assertNull(b2.enemy.status); assertTrue(ev2.attacks().single().failed)
    }

    @Test fun anAccuracyHookCanMakeAMoveNeverMissAndEvasionCanBeIgnored() {
        val main = mon(19, 20, "lowacc"); val wild = mon(150, 20, "splash")
        val b = battle(wild)
        assertTrue(round(main, b, acts = false, rng = highRandom).attacks().single().missed)
        val never = object : BattleRules { override fun accuracy(h: Hit, acc: Double?): Double? = null }
        assertFalse(under(never) { round(main, battle(wild), acts = false, rng = highRandom) }.attacks().single().missed)
        // +6 evasion: 100 % becomes 33 %; a mid roll (50) misses, Sacred-Sword-like ignoreEvasion hits
        val evasive = battle(mon(150, 20, "splash")).also { it.enemy.boosts[6] = 6 }
        assertTrue(round(mon(19, 20, "plainphysical"), evasive, acts = false, rng = midRandom).attacks().single().missed)
        val evasive2 = battle(mon(150, 20, "splash")).also { it.enemy.boosts[6] = 6 }
        assertFalse(round(mon(19, 20, "swordish"), evasive2, acts = false, rng = midRandom).attacks().single().missed)
    }

    // ---------------------------------------------------------------- the damage pipeline

    // Ratty L20 Tackle vs Birdie L20 at roll 100: base 12, STAB 18 (see BattleEngineTest.formulaHandComputed)
    @Test fun everyDamageHookActsAtItsPlaceOfThePipeline() {
        val a = mon(19, 20, "tackle"); val d = mon(16, 20)
        fun under1(rule: BattleRules) = under(rule) { dmg(a, d, "tackle").damage }
        assertEquals(18, dmg(a, d, "tackle").damage)
        assertEquals(36, under1(object : BattleRules { override fun finalMod(h: Hit) = 2.0 }))
        assertEquals(27, under1(object : BattleRules { override fun weatherMod(h: Hit) = 1.5 })) // 12 -> 18 -> STAB 27
        assertEquals(24, under1(object : BattleRules { override fun stab(h: Hit): Double? = 2.0 }))
        assertEquals(33, under1(object : BattleRules { override fun powerMod(h: Hit) = 2.0 })) // 22 -> 33
        assertEquals(33, under1(object : BattleRules { override fun attackMod(h: Hit) = 2.0 }))
        assertEquals(10, under1(object : BattleRules { override fun defenseMod(h: Hit) = 2.0 })) // 7 -> 10
        assertEquals(33, under1(object : BattleRules { override fun basePower(h: Hit): Int? = 80 }))
        assertEquals(12, under1(object : BattleRules { override fun moveType(h: Hit, type: Type) = Type.FIRE })) // no STAB any more
        assertEquals(7, under1(object : BattleRules { override fun fixedDamage(h: Hit): Int? = 7 }))
        val doubled = object : BattleRules { override fun effectiveness(h: Hit, eff: Double) = eff * 2 }
        under(doubled) { val r = dmg(a, d, "tackle"); assertEquals(36, r.damage); assertEquals(2.0, r.effectiveness, 0.0) }
        // a fixed damage is still nothing against an immune type
        under(object : BattleRules { override fun fixedDamage(h: Hit): Int? = 7 }) { assertEquals(0, dmg(a, mon(92, 20), "tackle").damage) }
    }

    @Test fun effectiveTypesAndStatsFeedTheFormula() {
        val a = mon(19, 20, "tackle"); val ghost = mon(92, 20)
        assertEquals(0, dmg(a, ghost, "tackle").damage)
        val normalized = object : BattleRules { override fun types(v: BattleView, p: Boolean, t: List<Type>) = if (!p) listOf(Type.NORMAL) else t }
        under(normalized) { assertTrue(dmg(a, ghost, "tackle").damage > 0) } // the Ghost counts as Normal now
        val strong = object : BattleRules { override fun stats(v: BattleView, p: Boolean, s: Stats) = if (p) s.copy(atk = s.atk * 2) else s }
        under(strong) { assertEquals(33, dmg(a, mon(16, 20), "tackle").damage) }
    }

    @Test fun takeDamageAndAfterDamageSeeEachHit() {
        val seen = mutableListOf<Triple<Boolean, Int, Int>>()
        val half = object : BattleRules {
            override fun takeDamage(r: Round, tp: Boolean, h: Hit, damage: Int) = damage / 2
            override fun afterDamage(r: Round, tp: Boolean, h: Hit, damage: Int) { seen += Triple(tp, h.hitIndex, damage) }
        }
        val main = mon(19, 20, "bulletseed"); val wild = mon(150, 60, "splash")
        val plain = BattleEngine.playRound(data, battle(mon(150, 60, "splash")), mon(19, 20, "bulletseed"), 1.0, false, Random(5)).attacks().single()
        val ev = under(half) { round(main, battle(wild), acts = false, seed = 5) }
        val atk = ev.attacks().single()
        assertEquals(plain.hits, atk.hits)
        assertTrue(atk.hits >= 2)
        assertEquals(atk.damage, seen.sumOf { it.third })
        assertEquals((0 until atk.hits).toList(), seen.map { it.second })
        assertTrue(seen.all { it.first == false })
        assertTrue("half of each hit", atk.damage < plain.damage)
    }

    @Test fun aHookThatTakesAllTheDamageLeavesTheHpAndSkipsAfterDamage() {
        var after = 0
        val sub = object : BattleRules {
            override fun takeDamage(r: Round, tp: Boolean, h: Hit, damage: Int) = 0
            override fun afterDamage(r: Round, tp: Boolean, h: Hit, damage: Int) { after++ }
        }
        val wild = mon(150, 20, "splash"); val hp = wild.hp
        val b = battle(wild)
        val atk = under(sub) { round(mon(19, 20, "tackle"), b, acts = false) }.attacks().single()
        assertEquals(hp, wild.hp); assertEquals(0, atk.damage); assertEquals(0, after); assertEquals(0, b.enemy.timesHit)
    }

    @Test fun theHitsHookReplacesTheMultiHitRange() {
        val three = object : BattleRules { override fun hits(r: Round, p: Boolean, move: Move): Int? = 3 }
        val atk = under(three) { round(mon(19, 20, "tackle"), battle(mon(150, 60, "splash")), acts = false) }.attacks().single()
        assertEquals(3, atk.hits)
    }

    @Test fun afterHitSecondariesAfterMoveAndTheSecondaryHookAllRun() {
        val log = mutableListOf<String>()
        val rule = object : BattleRules {
            override fun afterHit(r: Round, p: Boolean, move: Move, damage: Int) { log += "hit:${move.id}:$damage" }
            override fun secondary(r: Round, p: Boolean, move: Move, sec: Secondary) { log += "sec:${sec.volatileStatus ?: sec.status}" }
            override fun afterMove(r: Round, p: Boolean, move: Move, failed: Boolean) { log += "after:${move.id}:$failed" }
        }
        val wild = mon(150, 40, "splash"); val b = battle(wild)
        val atk = under(rule) { round(mon(19, 20, "twosec"), b, acts = false) }.attacks().single()
        assertEquals(listOf("hit:twosec:${atk.damage}", "sec:brn", "sec:flinch", "after:twosec:false"), log) // the first secondary rolled and applied, then the second
        assertEquals("brn", b.enemy.status)
    }

    @Test fun everySecondaryIsRolledEvenWithoutAnyRule() {
        // twosec has two chance-100 secondaries: the burn lands and no error; with an empty list the second does nothing visible
        val wild = mon(150, 40, "splash"); val b = battle(wild)
        round(mon(19, 20, "twosec"), b, acts = false)
        assertEquals("brn", b.enemy.status)
        // two draws of the generator for the two secondaries (plus the roll and the first draw of the pick)
        val counting = CountingRandom(Random(9))
        round(mon(19, 20, "tackle"), battle(mon(150, 40, "splash")), acts = false, rng = counting.also { it.reset() })
        val one = counting.draws
        round(mon(19, 20, "twosec"), battle(mon(150, 40, "splash")), acts = false, rng = counting.also { it.reset() })
        assertEquals(one + 2, counting.draws) // a plain move has no secondary roll; the two-secondary move has two
    }

    private class CountingRandom(val inner: Random) : Random() {
        var draws = 0
        fun reset() { draws = 0 }
        override fun nextBits(bitCount: Int): Int { draws++; return inner.nextBits(bitCount) }
    }

    // ---------------------------------------------------------------- status moves, statuses, stages, conditions

    private fun forced(id: String, side: Boolean = true) = object : BattleRules {
        override fun forcedMove(v: BattleView, p: Boolean): Move? = if (p == side) v.data.move(id) else null
    }

    @Test fun statusMoveTrueWorkedFalseFailedNullGenericPath() {
        val works = object : BattleRules { override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? = if (move.id == "custom1") true else null }
        val fails = object : BattleRules { override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? = if (move.id == "custom1") false else null }
        val speaks = object : BattleRules {
            override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? { if (move.id != "custom1") return null; r.fx(p, "It was no use."); return false }
        }
        fun play(vararg rules: BattleRules): List<GameEvent> = under(*rules) { round(mon(19, 20, "tackle"), battle(mon(150, 20, "splash")), acts = false) }
        val generic = play(forced("custom1"))
        assertTrue("But nothing happened." in generic.lines()); assertTrue(generic.attacks().single().failed) // as before: a move that does nothing counts as failed
        val ok = play(forced("custom1"), works)
        assertEquals(emptyList<String>(), ok.lines()); assertFalse(ok.attacks().single().failed)
        val no = play(forced("custom1"), fails)
        assertTrue("But it failed!" in no.lines()); assertTrue(no.attacks().single().failed)
        val own = play(forced("custom1"), speaks)
        assertEquals(listOf("It was no use."), own.lines()); assertTrue(own.attacks().single().failed)
    }

    @Test fun aBlockedStatusMoveShowsTheReasonAndASecondaryFailsQuietly() {
        val wild = mon(150, 20, "splash"); val b = battle(wild)
        val block = object : BattleRules { override fun blockStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean): String? = "The wild one shrugs it off." }
        val ev = under(forced("thunderwave"), block) { round(mon(19, 20, "tackle"), b, acts = false, rng = midRandom) }
        val shown = ev.effects().single { it.kind == EffectKind.BLOCKED }
        assertEquals("The wild one shrugs it off.", shown.text); assertEquals("par", shown.stat)
        assertNull(b.enemy.status)
        assertFalse("no second 'failed' line", "But it failed!" in ev.lines())
        assertTrue(ev.attacks().single().failed)
        // the same block on a secondary: no line at all
        val b2 = battle(mon(150, 20, "splash"))
        val ev2 = under(block) { round(mon(19, 20, "parhit"), b2, acts = false) }
        assertTrue(ev2.effects().none { it.kind == EffectKind.BLOCKED }); assertNull(b2.enemy.status)
    }

    @Test fun afterStatusHearsWhoGotWhatFromWhom() {
        val seen = mutableListOf<Triple<Boolean, String, Boolean>>()
        val rule = object : BattleRules { override fun afterStatus(r: Round, tp: Boolean, status: String, byFoe: Boolean) { seen += Triple(tp, status, byFoe) } }
        under(rule) { round(mon(19, 20, "parhit"), battle(mon(150, 20, "splash")), acts = false) }
        assertEquals(listOf(Triple(false, "par", true)), seen)
    }

    @Test fun stageChangeIsChainedZeroBlocksAndAfterStageChangeGetsTheAppliedAmount() {
        val seen = mutableListOf<Triple<String, Int, Boolean>>()
        val simple = object : BattleRules {
            override fun stageChange(r: Round, tp: Boolean, key: String, delta: Int, byFoe: Boolean) = delta * 2
            override fun afterStageChange(r: Round, tp: Boolean, key: String, delta: Int, byFoe: Boolean) { seen += Triple(key, delta, byFoe) }
        }
        val b = battle(mon(150, 20, "growl"))
        under(simple, forced("swordsdance")) { round(mon(19, 20, "tackle"), b, acts = true) }
        assertEquals(2, b.player.boosts[0])                                  // the faster wild Growl: -1 doubled to -2, then +2 doubled to +4
        assertTrue(Triple("atk", 4, false) in seen)                          // its own boost: not by a foe
        assertTrue(seen.any { it.first == "atk" && it.second == -2 && it.third })  // the wild Growl: -1 doubled, by a foe
        val clearBody = object : BattleRules { override fun stageChange(r: Round, tp: Boolean, key: String, delta: Int, byFoe: Boolean) = if (byFoe && delta < 0) 0 else delta }
        val b2 = battle(mon(150, 20, "growl")); val main = mon(19, 20, "tackle")
        val ev = under(clearBody, forced("swordsdance")) { round(main, b2, acts = true) }
        assertEquals(2, b2.player.boosts[0]); assertEquals(1, ev.effects().count { it.kind == EffectKind.STAT_CHANGE })
    }

    @Test fun volatilesStartEndAndCanBeBlocked() {
        var wasStarted: Boolean? = null
        var again: Boolean? = null
        var ended: Boolean? = null
        val rule = object : BattleRules {
            override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? {
                if (move.id != "custom1") return null
                wasStarted = r.addVolatile(!p, "confusion", 3, byFoe = true, text = "Mindlord became confused!", label = "Confused")
                again = r.addVolatile(!p, "confusion", 3, byFoe = true)
                ended = r.removeVolatile(!p, "confusion", "Mindlord snapped out of its confusion!")
                return true
            }
        }
        val b = battle(mon(150, 20, "splash"))
        val ev = under(forced("custom1"), rule) { round(mon(19, 20, "tackle"), b, acts = false) }
        assertEquals(true, wasStarted); assertEquals(false, again); assertEquals(true, ended)
        val kinds = ev.effects().filter { it.kind == EffectKind.VOLATILE }
        assertEquals(listOf("confusion" to 1, "confusion" to -1), kinds.map { it.stat to it.stages })
        assertEquals("Confused", kinds[0].label)
        assertTrue(b.enemy.volatiles.isEmpty())
        // blocked: the reason is shown, nothing starts
        val block = object : BattleRules { override fun blockVolatile(r: Round, tp: Boolean, key: String, byFoe: Boolean): String? = "It cannot be confused." }
        val keep = object : BattleRules {
            override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? { if (move.id == "custom1") wasStarted = r.addVolatile(!p, "confusion", 3, true); return true }
        }
        val b2 = battle(mon(150, 20, "splash"))
        val ev2 = under(forced("custom1"), block, keep) { round(mon(19, 20, "tackle"), b2, acts = false) }
        assertEquals(false, wasStarted); assertTrue(b2.enemy.volatiles.isEmpty())
        assertTrue("It cannot be confused." in ev2.lines())
    }

    // ---------------------------------------------------------------- the Pokémon as a view

    @Test fun effectiveValuesGoThroughTheHooksAndTheBaseValuesDoNot() {
        val main = mon(19, 20, "tackle"); val bird = mon(16, 20)
        val view = SimpleView(data, main, bird)
        assertEquals(listOf(Type.NORMAL, Type.FLYING), view.types(false))
        assertFalse(view.grounded(false)); assertTrue(view.grounded(true))
        assertEquals("testability", view.ability(true))
        assertEquals(StatCalc.stats(data, main), view.stats(true))
        assertEquals(listOf("tackle"), view.moves(true).map { it.id })
        val rule = object : BattleRules {
            override fun types(v: BattleView, p: Boolean, t: List<Type>) = if (!p) listOf(Type.WATER) else t
            override fun grounded(v: BattleView, p: Boolean, g: Boolean) = if (!p) true else g
            override fun ability(v: BattleView, p: Boolean, a: String) = if (p) "" else a
            override fun moves(v: BattleView, p: Boolean, m: List<Move>) = if (p) m + v.data.move("growl")!! else m
        }
        under(rule) {
            assertEquals(listOf(Type.WATER), view.types(false)); assertEquals(listOf(Type.NORMAL, Type.FLYING), view.baseTypes(false))
            assertTrue(view.grounded(false)); assertEquals("", view.ability(true)); assertEquals("testability", view.baseAbility(true))
            assertEquals(listOf("tackle", "growl"), view.moves(true).map { it.id }); assertEquals(listOf("tackle"), view.baseMoves(true).map { it.id })
        }
    }

    @Test fun weatherAndTerrainReadTheFieldAndASetCanSuppressTheWeather() {
        val b = battle(mon(150, 20, "splash")); b.field.weather = "rain"; b.field.terrain = "grassy"
        val view = SimpleView.of(data, b, mon(19, 20, "tackle"))
        assertEquals("rain", view.weather()); assertEquals("grassy", view.terrain())
        assertSame(b.field, view.field)
        under(object : BattleRules { override fun suppressWeather(v: BattleView) = true }) { assertNull(view.weather()); assertEquals("grassy", view.terrain()) }
        assertEquals(mutableMapOf<String, Int>(), view.conds(true)); b.field.player["reflect"] = 5
        assertEquals(5, view.conds(true)["reflect"]); assertTrue(view.conds(false).isEmpty())
    }

    private fun assertSame(a: Any, b: Any) = assertTrue(a === b)

    @Test fun theMoveChoiceUsesTheEffectiveTypes() {
        val main = mon(19, 20, "tackle", "growl"); val wild = mon(16, 20)
        val view = SimpleView(data, main, wild)
        val ghostly = object : BattleRules { override fun types(v: BattleView, p: Boolean, t: List<Type>) = if (!p) listOf(Type.GHOST) else t }
        assertTrue(MoveChooser.canHurt(view, true, data.move("tackle")!!))
        under(ghostly) {
            assertFalse(MoveChooser.canHurt(view, true, data.move("tackle")!!))
            for (s in 1..20) assertEquals("struggle", MoveChooser.pick(MoveChoice.SMART, view, true, Random(s)).id)
        }
    }

    @Test fun usefulAnswersTheSmartChoiceFirst() {
        val main = mon(19, 20, "custom1"); val wild = mon(16, 20)
        val view = SimpleView(data, main, wild)
        val custom = data.move("custom1")!!
        assertFalse(MoveChooser.statusUseful(MoveChoice.SMART, custom, view, true)) // the engine's rule: it changes nothing
        under(object : BattleRules { override fun useful(v: BattleView, p: Boolean, move: Move) = if (move.id == "custom1") true else null }) {
            assertTrue(MoveChooser.statusUseful(MoveChoice.SMART, custom, view, true))
        }
        // the old public signature still answers
        assertFalse(MoveChooser.statusUseful(MoveChoice.SMART, custom, data, main, BattleSide(), 30, wild, BattleSide()))
    }

    // ---------------------------------------------------------------- order of the turn

    private fun firstAttacker(ev: List<GameEvent>) = ev.attacks().first().byPlayer

    @Test fun speedModPriorityAndSlowerFirstChangeTheOrder() {
        fun fresh(main: String = "tackle") = round(mon(19, 20, main), battle(mon(150, 20, "tackle"))) // the wild one is faster
        assertFalse(firstAttacker(fresh()))
        under(object : BattleRules { override fun speedMod(v: BattleView, p: Boolean) = if (p) 4.0 else 1.0 }) { assertTrue(firstAttacker(fresh())) }
        under(object : BattleRules { override fun slowerFirst(v: BattleView) = true }) { assertTrue(firstAttacker(fresh())) }
        under(object : BattleRules { override fun priority(v: BattleView, p: Boolean, move: Move) = if (p) 1 else 0 }) { assertTrue(firstAttacker(fresh())) }
        // the move's own priority still counts, and the hooks add to it
        assertTrue(firstAttacker(fresh("quickattack")))
        under(object : BattleRules { override fun priority(v: BattleView, p: Boolean, move: Move) = if (!p) 1 else 0 }) { assertFalse(firstAttacker(fresh("quickattack"))) } // 1 against 1: speed again
    }

    @Test fun endOfRoundRunsOncePerRoundBeforeBurnDamageAndNotForASwitchHit() {
        var calls = 0
        val rule = object : BattleRules {
            override fun endOfRound(r: Round) {
                calls++
                r.hurt(true, 3, "The Main is hurt by the weather.", EffectKind.RESIDUAL)
            }
        }
        val main = mon(19, 40, "tackle"); val wild = mon(150, 20, "tackle")
        val b = battle(wild); b.player.status = "brn"
        val ev = under(rule) { round(main, b) }
        assertEquals(1, calls)
        val kinds = ev.effects().map { it.kind }
        assertEquals(listOf(EffectKind.RESIDUAL, EffectKind.STATUS_DAMAGE), kinds.takeLast(2))
        under(rule) { BattleEngine.switchHit(data, b, main, Random(2)) }
        assertEquals(1, calls)
    }

    @Test fun onLeaveRunsForTheOldMainThenTheSideIsFresh() {
        val seen = mutableListOf<String>()
        val rule = object : BattleRules { override fun onLeave(battle: Battle, data: GameData, p: Boolean, mon: Mon) { seen += "${p}:${mon.name}" } }
        val b = battle(mon(150, 20, "splash")); b.player.boosts[0] = 3; b.player.volatiles["confusion"] = 2; b.field.player["reflect"] = 4
        val old = mon(19, 20, "tackle")
        under(rule) { b.switchOutMain(data, old); b.switchOutMain(data, null) }
        assertEquals(listOf("true:Ratty"), seen)
        assertEquals(0, b.player.boosts[0]); assertTrue(b.player.volatiles.isEmpty())
        assertEquals(4, b.field.player["reflect"]) // side conditions survive the switch
    }

    @Test fun gameChangedRunsOnLeaveForTheOldMainAndStartsAFreshSide() {
        val state = TestData.state(data, 25, 20, listOf("tackle"))
        val second = TestData.mon(data, 19, 20, listOf("tackle"), uid = "second")
        state.mons += second; state.team += second.uid
        val g = Game(data, state, Random(1)) { 1_700_000_000_000L }
        val b = battle(mon(150, 20, "splash")); state.battle = b
        b.player.volatiles["confusion"] = 3; b.player.entered = true; b.field.player["reflect"] = 4
        val seen = mutableListOf<String>()
        val rule = object : BattleRules { override fun onLeave(battle: Battle, data: GameData, p: Boolean, mon: Mon) { seen += "${p}:${mon.uid}:${battle === b}" } }
        val result = under(rule) { g.setMain("second") }
        assertTrue(result.ok)
        assertEquals(listOf("true:main:true"), seen)
        assertTrue(b.player.volatiles.isEmpty()); assertFalse(b.player.entered)
        assertEquals(4, b.field.player["reflect"])
        // no fight, no call
        state.battle = null
        under(rule) { g.setMain("main") }
        assertEquals(1, seen.size)
    }

    // ---------------------------------------------------------------- tracking and the toolbox

    @Test fun theRoundTracksWhoMovedAndWhatHitWhom() {
        val seen = mutableListOf<String>()
        val rule = object : BattleRules {
            override fun endOfRound(r: Round) {
                seen += "moved:${r.movedThisRound(true)}/${r.movedThisRound(false)}"
                seen += "taken:${r.damageTakenThisRound(true) > 0}/${r.damageTakenThisRound(false) > 0}"
                seen += "hit:${r.hitThisRound(true)}/${r.hitThisRound(false)}"
                r.lastHitTaken(false)?.let { seen += "last:${it.category}:${it.byMain}:${it.moveId}:${it.damage == r.damageTakenThisRound(false)}" }
            }
        }
        val main = mon(19, 20, "tackle"); val wild = mon(150, 20, "splash")
        val b = battle(wild)
        under(rule) { round(main, b, acts = false) }
        assertEquals(listOf("moved:true/false", "taken:false/true", "hit:false/true", "last:PHYSICAL:true:tackle:true"), seen)
        assertEquals(1, b.enemy.timesHit); assertEquals(0, b.player.timesHit)
        assertEquals("tackle", b.player.lastMove); assertFalse(b.player.lastMoveFailed); assertEquals(1, b.player.actions)
    }

    @Test fun actionsCountEarlierActionsAndALostMoveIsRecorded() {
        val seenActions = mutableListOf<Int>()
        val rule = object : BattleRules { override fun failReason(r: Round, p: Boolean, move: Move): String? { if (p) seenActions += r.side(true).actions; return null } }
        val main = mon(19, 20, "lowacc"); val wild = mon(150, 20, "splash")
        val b = battle(wild)
        under(rule) { round(main, b, acts = false, rng = highRandom); round(main, b, acts = false, rng = highRandom) }
        assertEquals(listOf(0, 1), seenActions) // a hook sees the number of earlier actions
        assertTrue(b.player.lastMoveFailed) // missed
    }

    @Test fun useMoveRunsAnotherMoveAfterTheCallersOwnAttackEvent() {
        var worked: Boolean? = null
        val calls = object : BattleRules {
            override fun statusMove(r: Round, p: Boolean, move: Move): Boolean? {
                if (move.id != "custom1") return null
                worked = r.useMove(p, r.data.move("tackle")!!); return true
            }
        }
        val wild = mon(150, 20, "splash"); val hp = wild.hp
        val b = battle(wild)
        val ev = under(forced("custom1"), calls) { round(mon(19, 20, "tackle"), b, acts = false) }
        assertEquals(listOf("Custom One", "Tackle"), ev.attacks().map { it.move })
        assertEquals(true, worked); assertTrue(wild.hp < hp)
        assertEquals("custom1", b.player.lastMove) // the caller is what was picked
    }

    @Test fun aChargeMoveSkipsItsChargeWhenASetSays() {
        val main = mon(19, 20, "solarbeam"); val wild = mon(150, 20, "splash")
        val ev1 = round(main, battle(wild), acts = false)
        assertTrue(ev1.attacks().isEmpty()); assertEquals(EffectKind.CHARGE, ev1.effects().single().kind)
        val sun = object : BattleRules { override fun skipsCharge(r: Round, p: Boolean, move: Move) = move.id == "solarbeam" }
        val ev2 = under(sun) { round(mon(19, 20, "solarbeam"), battle(wild), acts = false) }
        assertEquals(1, ev2.attacks().size)
    }

    @Test fun aSleeperMayUseASleepUsableMove() {
        val wild = mon(150, 20, "splash")
        val b = battle(wild); b.player.status = "slp"
        val ev = round(mon(19, 20, "snorish"), b, acts = false, rng = highRandom) // never wakes
        assertEquals(1, ev.attacks().size); assertFalse("Ratty is fast asleep." in ev.lines()); assertEquals("slp", b.player.status)
        val b2 = battle(mon(150, 20, "splash")); b2.player.status = "slp"
        assertTrue("Ratty is fast asleep." in round(mon(19, 20, "tackle"), b2, acts = false, rng = highRandom).lines())
    }

    // ---------------------------------------------------------------- MoveText

    @Test fun ruleSetsAddSentencesToTheMoveText() {
        val custom = data.move("custom1")!!
        assertEquals("Has no effect in AnkiGo battles.", MoveText.describe(custom))
        val says = object : BattleRules { override fun describe(m: Move) = if (m.id == "custom1") listOf("Does a custom thing.") else emptyList() }
        under(says) {
            assertEquals("Does a custom thing.", MoveText.describe(custom))
            assertEquals("Deals damage with no extra effect.", MoveText.describe(data.move("tackle")!!))
        }
        val adds = object : BattleRules { override fun describe(m: Move) = if (m.id == "tackle") listOf("Also a rule sentence.") else emptyList() }
        under(adds) { assertEquals("Also a rule sentence.", MoveText.describe(data.move("tackle")!!)) }
    }

    // ---------------------------------------------------------------- "other stat" moves

    private fun formula(level: Int, power: Int, a: Int, d: Int) = floor(floor(2.0 * level / 5 + 2).toInt() * power * a / d.toDouble()).toInt() / 50 + 2

    private val attacker get() = mon(200, 30, "tackle")
    private val defender get() = mon(201, 30, "tackle")
    private val aStats get() = StatCalc.stats(data, attacker)
    private val dStats get() = StatCalc.stats(data, defender)

    @Test fun psyshockHitsDefenseNotSpecialDefense() {
        assertEquals(formula(30, 80, aStats.spa, dStats.def), dmg(attacker, defender, "psyshockish").damage)
        assertEquals(formula(30, 80, aStats.spa, dStats.spd), dmg(attacker, defender, "plainspecial").damage)
        assertTrue(dStats.def != dStats.spd)
        // the Defense STAGE counts, not the Sp. Def one
        val up = BattleSide().also { it.boosts[1] = 2; it.boosts[3] = -2 }
        assertEquals(formula(30, 80, aStats.spa, dStats.def * 2), dmg(attacker, defender, "psyshockish", dSide = up).damage)
    }

    @Test fun bodyPressUsesTheUsersDefenseAndItsStage() {
        assertEquals(formula(30, 80, aStats.def, dStats.def), dmg(attacker, defender, "bodypressish").damage)
        val up = BattleSide().also { it.boosts[1] = 2; it.boosts[0] = -6 }
        assertEquals(formula(30, 80, aStats.def * 2, dStats.def), dmg(attacker, defender, "bodypressish", aSide = up).damage)
        // still a physical move: a burn halves it
        val burned = BattleSide(status = "brn")
        assertEquals(formula(30, 80, aStats.def, dStats.def) / 2, dmg(attacker, defender, "bodypressish", aSide = burned).damage)
    }

    @Test fun foulPlayUsesTheTargetsAttackAndItsStage() {
        assertEquals(formula(30, 95, dStats.atk, dStats.def), dmg(attacker, defender, "foulplayish").damage)
        val aSide = BattleSide().also { it.boosts[0] = 6 } // the user's own stage does not count
        val dSide = BattleSide().also { it.boosts[0] = 1 }
        assertEquals(formula(30, 95, floor(dStats.atk * 1.5).toInt(), dStats.def), dmg(attacker, defender, "foulplayish", aSide = aSide, dSide = dSide).damage)
    }

    @Test fun sacredSwordIgnoresTheTargetsDefenseStage() {
        val up = BattleSide().also { it.boosts[1] = 2 }
        val down = BattleSide().also { it.boosts[1] = -2 }
        val plain = formula(30, 80, aStats.atk, dStats.def)
        assertEquals(plain, dmg(attacker, defender, "swordish", dSide = up).damage)
        assertEquals(plain, dmg(attacker, defender, "swordish", dSide = down).damage)
        assertTrue(dmg(attacker, defender, "plainphysical", dSide = up).damage < plain)
        assertTrue(dmg(attacker, defender, "plainphysical", dSide = down).damage > plain)
    }

    @Test fun theOtherStatSentencesAreInTheMoveText() {
        assertTrue("Hits the target's Defense instead of its Sp. Def." in MoveText.describe(data.move("psyshockish")!!))
        assertTrue("Uses your Defense instead of your Attack." in MoveText.describe(data.move("bodypressish")!!))
        assertTrue("Uses the target's Attack instead of yours." in MoveText.describe(data.move("foulplayish")!!))
        assertTrue("Ignores the target's stat changes." in MoveText.describe(data.move("swordish")!!))
        assertFalse("instead" in MoveText.describe(data.move("plainphysical")!!))
    }
}
