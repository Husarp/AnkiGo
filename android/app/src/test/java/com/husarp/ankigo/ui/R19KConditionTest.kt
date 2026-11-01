package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.CoreRules
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import com.husarp.ankigo.game.MoveControl
import com.husarp.ankigo.game.SideMoves
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.ui.ConditionMarks.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R19-K (PLAN 269 A + 287), the pure half: the word and number of every condition key, the order, the board that follows the events and the
 * saved fight, the one-row plan, and the rule that no row or corner chip ever overlaps anything (1, 3 and 8 conditions on both sides, at
 * every arena size of the plain layout, with and without a battle frame). The real views at the five hosts are in `shots.R19KConditionShots`.
 */
class R19KConditionTest {
    private val data = TestData.world()
    private fun battle() = Battle(TestData.mon(data, 54, 25, listOf("tackle"), "wild"), Tier.NORMAL)

    @Test fun statPopupContainsOnlyTheSelectedStatAndSide() {
        val b = battle()
        b.player.boosts[0] = 1
        b.player.boosts[1] = -2
        b.enemy.boosts[0] = 3
        val board = ConditionBoard()
        board.syncFrom(b)
        for ((slot, expected) in listOf(Slot.OURS to 1, Slot.WILD to 3)) {
            val sections = ConditionSections.stat(board, slot, "stat.atk", "Psyduck", "Pikachu")
            assertEquals(1, sections.size)
            assertEquals(1, sections.single().marks.size)
            val mark = sections.single().marks.single()
            assertEquals(slot, mark.slot)
            assertEquals("stat.atk", mark.key)
            assertEquals(expected, mark.value)
            assertTrue(sections.single().title!!.contains(if (slot == Slot.OURS) "Pikachu" else "Psyduck"))
        }
        assertTrue(ConditionSections.stat(board, Slot.OURS, "stat.spe", "Psyduck", "Pikachu").isEmpty())
        b.player.boosts[0] = 0
        board.syncFrom(b)
        assertTrue(ConditionSections.stat(board, Slot.OURS, "stat.atk", "Psyduck", "Pikachu").isEmpty())
    }

    private fun effect(onPlayer: Boolean, kind: EffectKind, key: String, stages: Int, label: String? = null) =
        GameEvent.Effect(onPlayer, "text", 10, 20, null, kind, key, stages, 0, label)

    // ---------------------------------------------------------------- words and numbers

    @Test fun theWordAndTheNumberOfEachKey() {
        val rows = listOf(
            Triple("switchtrap", 2, "Trapped · 2" to "2 turns left"),
            Triple("spikes", 2, "Spikes ×2" to "2 layers"),
            Triple("toxicspikes", 1, "Toxic Spikes ×1" to "1 layer"),
            Triple("stealthrock", 1, "Stealth Rock" to ""),
            Triple("stickyweb", 1, "Sticky Web" to ""),
            Triple("confusion", 3, "Confused · 3" to "3 turns left"),
            Triple("confusion", 1, "Confused · 1" to "1 turn left"),
            Triple("attract", 1, "In love" to ""),
            Triple("leechseed", 1, "Seeded" to ""),
            Triple("substitute", 25, "Substitute" to "25 HP left"),
            Triple("stockpile", 2, "Stockpile 2" to "2 layers"),
            Triple("stockpile", 1, "Stockpile 1" to "1 layer"),
            Triple("taunt", 3, "Taunted · 3" to "3 turns left"),
            Triple("encore", 2, "Encore · 2" to "2 turns left"),
            Triple("torment", 1, "Tormented" to ""),
            Triple("healblock", 5, "Heal Block · 5" to "5 turns left"),
            Triple("partiallytrapped", 4, "Trapped · 4" to "4 turns left"),
            Triple("uproar", 0, "Uproar · 1" to "1 turn left"),
            Triple("reflect", 4, "Reflect · 4" to "4 turns left"),
            Triple("lightscreen", 5, "Light Screen · 5" to "5 turns left"),
            Triple("tailwind", 3, "Tailwind · 3" to "3 turns left"),
            Triple("mp.fs", 2, "Future Sight · 2" to "2 turns left"),
            Triple("rain", 3, "Rain · 3" to "3 turns left"),
            Triple("rain", 0, "Rain" to "until replaced"),
            Triple("sand", 0, "Sandstorm" to "until replaced"),
            Triple("electric", 5, "Electric Terrain · 5" to "5 turns left"),
            Triple("grassy", 0, "Grassy Terrain" to "until replaced"),
            Triple("trickroom", 4, "Trick Room · 4" to "4 turns left"),
        )
        for ((key, value, want) in rows) {
            val d = ConditionMarks.def(key)
            assertNotNull("a chip for $key", d)
            val m = ConditionMarks.Mark("x", Slot.WILD, d!!, value, 1)
            assertEquals("$key $value: the chip", want.first, m.chipText)
            assertEquals("$key $value: the list line", want.second, m.detail)
            assertEquals("$key $value: the list line reads", if (want.second.isBlank()) d.word else "${d.word}: ${want.second}", m.line)
        }
    }

    @Test fun aNumberIsOnlyShownWhenItIsTurns() {
        for (key in ConditionMarks.allKeys) {
            val d = ConditionMarks.def(key)!!
            val n = ConditionMarks.number(d, 7)
            if (d.num == ConditionMarks.Num.TURNS) assertEquals("$key shows its turns", 7, n) else assertNull("$key has no turns to show", n)
        }
    }

    @Test fun everyKeyHasAWordAGroupAndAPlaceInTheOrder() {
        val words = HashSet<String>()
        for (key in ConditionMarks.allKeys) {
            val d = ConditionMarks.def(key)!!
            assertTrue("$key has a word", d.word.isNotBlank())
            assertTrue("$key: a word that is on one chip only (${d.word})", (words.add(d.word) || key == "partiallytrapped"))
            assertTrue("$key has a rank", if (key.startsWith("stat.")) d.rank < 0 else d.rank > 0)
            assertFalse("$key: no arrow glyphs in the texts", d.word.contains('→') || d.noTurns.contains('→'))
        }
    }

    /** The keys the games write are the ones the chips are made for (a renamed key would silently lose its chip). */
    @Test fun theKeysAreTheGamesOwn() {
        val game = listOf(
            CoreRules.CONFUSION, CoreRules.ATTRACT, CoreRules.BOUND, CoreRules.SALT_CURE, CoreRules.SYRUP_BOMB, CoreRules.HEAL_BLOCK, CoreRules.LOCKED, CoreRules.UPROAR,
            SideMoves.SUBSTITUTE, SideMoves.LEECH_SEED, SideMoves.CURSE, SideMoves.NIGHTMARE, SideMoves.YAWN, SideMoves.DESTINY_BOND, SideMoves.AQUA_RING,
            SideMoves.INGRAIN, SideMoves.STOCKPILE, SideMoves.REFLECT, SideMoves.LIGHT_SCREEN, SideMoves.AURORA_VEIL, SideMoves.SAFEGUARD, SideMoves.MIST,
            SideMoves.TAILWIND, SideMoves.WISH,
            MoveControl.TAUNT, MoveControl.ENCORE, MoveControl.DISABLE, MoveControl.TORMENT, MoveControl.IMPRISON, MoveControl.THROAT_CHOP, MoveControl.MAGNET_RISE,
            MoveControl.TELEKINESIS, MoveControl.SMACK_DOWN, MoveControl.FORESIGHT, MoveControl.MIRACLE_EYE, MoveControl.CHARGE, MoveControl.DEFENSE_CURL,
            MoveControl.MINIMIZE, MoveControl.TAR_SHOT, MoveControl.POWER_TRICK, MoveControl.POWER_SHIFT,
        )
        for (key in game) assertNotNull("the game's key $key has a chip", ConditionMarks.def(key))
    }

    @Test fun roundLongAndBookkeepingKeysNeverGetAChip() {
        val none = listOf(
            CoreRules.FLINCH, CoreRules.MUSTRECHARGE, CoreRules.GLAIVE_RUSH, CoreRules.SLEEP_TURNS, SideMoves.ENDURE, "protect", "kingsshield", "matblock",
            MoveControl.ELECTRIFY, MoveControl.ROOST, MoveControl.MAGIC_COAT, MoveControl.SNATCH, MoveControl.POWDER, MoveControl.LOCK_ON,
            "ab.turns", "ab.truant", "ab.busted", "ab.bounce", "ab.copy", "ab.rounds", "mp.echo", "mp.fury", "mp.rollout", "mp.bide", "mp.rand", "sm.stall",
            "sm.wishhp", "mp.fs.dmg", "mp.fs.doom", "courtchange", "haze",
        )
        for (key in none) assertNull("$key has no chip", ConditionMarks.def(key))
    }

    @Test fun whatHindersComesFirstAndTheNewestComesFirstAmongEquals() {
        val b = ConditionBoard()
        b.start(Slot.WILD, "reflect", 4)
        b.start(Slot.WILD, "leechseed", 1)
        b.start(Slot.WILD, "taunt", 3)
        b.start(Slot.WILD, "confusion", 3)
        b.start(Slot.WILD, "encore", 2)
        assertEquals(listOf("confusion", "taunt", "encore", "leechseed", "reflect"), b.of(Slot.WILD).map { it.key })
    }

    // ---------------------------------------------------------------- the board

    @Test fun theBoardIsSettledFromTheSavedFight() {
        val b = battle()
        b.enemy.volatiles["confusion"] = 3
        b.enemy.volatiles["leechseed"] = 1
        b.enemy.volatiles["mp.echo"] = 2            // bookkeeping: no chip
        b.player.volatiles["substitute"] = 25
        b.field.player["reflect"] = 4
        b.field.enemy["tailwind"] = 2
        b.field.enemy["sm.wishhp"] = 30             // bookkeeping: no chip
        b.field.weather = "rain"; b.field.weatherTurns = 3
        b.field.terrain = "grassy"; b.field.terrainTurns = 0
        b.field.rooms["trickroom"] = 4
        b.field.rooms["iondeluge"] = 0              // over
        val board = ConditionBoard()
        assertTrue(board.syncFrom(b))
        assertEquals(listOf("confusion", "leechseed", "tailwind"), board.of(Slot.WILD).map { it.key })
        assertEquals(listOf("reflect", "substitute"), board.of(Slot.OURS).map { it.key }.sorted())
        assertEquals(listOf("rain", "grassy", "trickroom"), board.of(Slot.FIELD).map { it.key })
        assertEquals("Rain · 3", board.of(Slot.FIELD).first().chipText)
        assertFalse("the same fight again changes nothing", board.syncFrom(b))
        b.enemy.volatiles["confusion"] = 2
        assertTrue(board.syncFrom(b))
        assertEquals("Confused · 2", board.of(Slot.WILD).first { it.key == "confusion" }.chipText)
        b.enemy.volatiles.remove("confusion")
        assertTrue(board.syncFrom(b))
        assertEquals(listOf("leechseed", "tailwind"), board.of(Slot.WILD).map { it.key })
        assertTrue("no fight: nothing", board.syncFrom(null))
        assertEquals(0, board.size)
    }

    @Test fun aMarkKeepsItsAgeWhileItsTurnsTickDown() {
        val b = battle()
        b.enemy.volatiles["taunt"] = 3
        val board = ConditionBoard()
        board.syncFrom(b)
        b.enemy.volatiles["encore"] = 3
        board.syncFrom(b)
        b.enemy.volatiles["taunt"] = 2
        board.syncFrom(b)
        val all = board.all().associateBy { it.key }
        assertTrue("taunt is the older mark", all.getValue("taunt").seq < all.getValue("encore").seq)
        assertEquals(2, all.getValue("taunt").value)
    }

    @Test fun eventsMoveTheBoardInStepWithTheirLines() {
        val b = battle()
        val board = ConditionBoard()
        // the saved fight is final: it already holds the confusion (3 turns left after the round)
        b.enemy.volatiles["confusion"] = 3
        assertFalse(board.applyEffect(effect(false, EffectKind.OTHER, "confusion", 1), b))
        assertTrue(board.applyEffect(effect(false, EffectKind.VOLATILE, "confusion", 1, "Confused"), b))
        assertEquals("Confused · 3", board.of(Slot.WILD).single().chipText)
        // a start the saved fight no longer holds (it ended in the same round) never shows
        assertFalse(board.applyEffect(effect(true, EffectKind.VOLATILE, "taunt", 1, "Taunt"), b))
        assertEquals(0, board.of(Slot.OURS).size)
        // the end line takes it away
        b.enemy.volatiles.remove("confusion")
        assertTrue(board.applyEffect(effect(false, EffectKind.VOLATILE, "confusion", -1), b))
        assertEquals(0, board.size)
    }

    @Test fun aSideConditionGoesToTheSideThatOwnsItAndTheWeatherToTheField() {
        val b = battle()
        b.field.player["reflect"] = 5
        b.field.weather = "sun"; b.field.weatherTurns = 5
        val board = ConditionBoard()
        assertTrue(board.applyEffect(effect(true, EffectKind.FIELD, "reflect", 1, "Reflect"), b))
        assertTrue(board.applyEffect(effect(true, EffectKind.FIELD, "sun", 1, "Sun"), b))
        assertEquals(listOf("reflect"), board.of(Slot.OURS).map { it.key })
        assertEquals(listOf("sun"), board.of(Slot.FIELD).map { it.key })
        // a new weather replaces the old one
        b.field.weather = "rain"; b.field.weatherTurns = 5
        assertTrue(board.applyEffect(effect(false, EffectKind.FIELD, "rain", 1, "Rain"), b))
        assertEquals(listOf("rain"), board.of(Slot.FIELD).map { it.key })
        // Defog ends a screen on the target's side
        assertTrue(board.applyEffect(effect(true, EffectKind.FIELD, "reflect", -1), null))
        assertEquals(0, board.of(Slot.OURS).size)
        // lines that are no condition change nothing
        assertFalse(board.applyEffect(effect(true, EffectKind.FIELD, "haze", 0, "Haze"), null))
        assertFalse(board.applyEffect(effect(true, EffectKind.FIELD, "courtchange", 1, "Court Change"), null))
    }

    @Test fun aBrokenSubstituteAndADestinyBondThatCameTrueEnd() {
        val board = ConditionBoard()
        board.start(Slot.OURS, "substitute", 25)
        board.start(Slot.WILD, "destinybond", 1)
        assertFalse("a hit that the substitute took leaves it", board.applyEffect(effect(true, EffectKind.VOLATILE, "substitute", 0), null))
        assertTrue(board.applyEffect(effect(true, EffectKind.VOLATILE, "substitute", 0, "Substitute broke"), null))
        assertTrue(board.applyEffect(effect(false, EffectKind.VOLATILE, "destinybond", 0, "Destiny Bond"), null))
        assertEquals(0, board.size)
    }

    @Test fun aSwitchTakesOurOwnConditionsButNotOurSideOrTheField() {
        val board = ConditionBoard()
        board.start(Slot.OURS, "confusion", 2)
        board.start(Slot.OURS, "reflect", 4)
        board.start(Slot.WILD, "taunt", 3)
        board.start(Slot.FIELD, "rain", 3)
        assertTrue(board.dropOurVolatiles())
        assertEquals(listOf("reflect"), board.of(Slot.OURS).map { it.key })
        assertEquals(1, board.of(Slot.WILD).size)
        assertEquals(1, board.of(Slot.FIELD).size)
        assertFalse(board.dropOurVolatiles())
        assertTrue(board.clear())
        assertFalse(board.clear())
    }

    @Test fun theListHasASectionPerPlaceAndTitlesOnlyWhenThereAreSeveral() {
        val board = ConditionBoard()
        board.start(Slot.WILD, "confusion", 3)
        val one = ConditionSections.of(board, Slot.OURS, "Duckling", "Sparkmouse")
        assertEquals(1, one.size); assertNull("a lone section has no title", one[0].title)
        board.start(Slot.OURS, "reflect", 4)
        board.start(Slot.FIELD, "rain", 3)
        val all = ConditionSections.of(board, Slot.OURS, "Duckling", "Sparkmouse")
        assertEquals(listOf("Your Sparkmouse", "Wild Duckling", "The field"), all.map { it.title })
        assertEquals("the tapped side comes first", Slot.WILD, ConditionSections.of(board, Slot.WILD, "Duckling", "Sparkmouse")[0].marks[0].slot)
        assertEquals(3, all.sumOf { it.marks.size })
    }

    // ---------------------------------------------------------------- the one-row plan

    private fun fit(n: Int, w: Float, avail: Float, more: Float = 20f) = ConditionRow.fit(FloatArray(n) { w }, 4f, avail) { more }

    @Test fun everyChipFitsOrTheRestBecomePlusN() {
        assertEquals(3, fit(3, 50f, 158f)!!.shown)
        assertEquals(0, fit(3, 50f, 158f)!!.more)
        val f = fit(8, 50f, 158f)!!
        assertEquals("room for 2 chips and the +6", 2, f.shown); assertEquals(6, f.more)
        assertEquals(50f + 4f + 50f + 4f + 20f, f.width, 0.01f)
        val none = fit(8, 50f, 30f)!!
        assertEquals("no chip fits, the +N does", 0, none.shown); assertEquals(8, none.more)
        assertNull("not even +N", fit(8, 50f, 10f))
        assertNull("nothing to show", ConditionRow.fit(FloatArray(0), 4f, 100f) { 20f })
        assertEquals("one chip that fits has no +N", 0, fit(1, 50f, 50f)!!.more)
    }

    // ---------------------------------------------------------------- never overlap

    /** The sizes of the arena (virtual dp): the Battle tab on three phones, the Study panel, strips, picture-in-picture, floating windows, tablets. */
    private val sizes = listOf(412f to 692f, 360f to 623f, 412f to 760f, 412f to 230f, 412f to 146f, 330f to 180f, 436f to 247f, 320f to 200f,
        240f to 150f, 200f to 200f, 436f to 300f, 800f to 1100f, 869f to 412f)

    private fun chipWidth(m: ConditionMarks.Mark) = 11f + 5.8f * m.chipText.length
    private val moreWidth = { n: Int -> 11f + 5.8f * "+$n".length }
    private val z = ConditionSpot.Sizes(gap = 4f, lift = 2.6f, rowH = 17f, moreW = moreWidth)

    private val wildKeys = listOf("confusion" to 3, "leechseed" to 1, "substitute" to 25, "taunt" to 2, "encore" to 2, "healblock" to 4, "tailwind" to 3, "saltcure" to 1)
    private val ourKeys = listOf("attract" to 1, "partiallytrapped" to 3, "reflect" to 4, "stockpile" to 2, "lightscreen" to 5, "aquaring" to 1, "safeguard" to 4, "wish" to 2)
    private val fieldKeys = listOf("rain" to 3, "electric" to 4, "trickroom" to 3)

    private fun marks(slot: Slot, keys: List<Pair<String, Int>>, n: Int): List<ConditionMarks.Mark> {
        val board = ConditionBoard()
        for ((k, v) in keys.take(n)) board.start(slot, k, v)
        return board.of(slot)
    }

    private fun r(b: ArenaGeo.Box) = ConditionSpot.R(b.x, b.y, b.right, b.bottom)

    @Test fun rowsNeverOverlapAnythingAtAnyArenaSize() {
        var rows = 0; var missing = 0; var plusN = 0
        val perSize = LinkedHashMap<String, IntArray>()   // size -> (rows, sides without room)
        for ((w, h) in sizes) for (compact in listOf(false, true)) for (frame in listOf(false, true)) for (own in listOf(true, false)) {
            val cards = ArenaGeo.cards(0f, compact, true)
            val safe = if (frame) FrameSafe(6f, 10f, 20f) else null
            val plan = ArenaGeo.plan(w, h, cards, compact, own, safe)
            val art = listOf(r(plan.wildSprite.art()).grow(3f), r(plan.playerSprite.art()).grow(3f))
            val scene = ConditionSpot.Scene(w, h, safe, listOf(r(plan.wildCard), r(plan.playerCard)) + art)
            for (n in listOf(1, 3, 8)) {
                val wild = marks(Slot.WILD, wildKeys, n); val ours = marks(Slot.OURS, ourKeys, n); val field = marks(Slot.FIELD, fieldKeys, minOf(n, 3))
                fun ws(ms: List<ConditionMarks.Mark>) = FloatArray(ms.size) { chipWidth(ms[it]) }
                val all = ConditionSpot.all(scene, r(plan.wildCard), r(plan.playerCard), ws(wild), ws(ours), ws(field), z)
                val tag = "${w.toInt()}x${h.toInt()} compact=$compact frame=$frame own=$own n=$n"
                val placed = listOf("wild" to (all.wild to wild.size), "ours" to (all.ours to ours.size), "field" to (all.field to field.size))
                for ((name, pair) in placed) {
                    val (p, total) = pair
                    val tally = perSize.getOrPut("${w.toInt()}x${h.toInt()}") { IntArray(2) }
                    if (p == null) { missing++; tally[1]++; continue }
                    rows++; tally[0]++
                    assertTrue("$tag $name: inside the room ${p.rect.l},${p.rect.t},${p.rect.r},${p.rect.b} of ${scene.area.l},${scene.area.t},${scene.area.r},${scene.area.b}", p.rect.inside(scene.area))
                    for (o in scene.blocks) assertFalse("$tag $name: the row is over an obstacle", p.rect.hits(o))
                    assertEquals("$tag $name: every condition is a chip or in the +N", total, p.fit.shown + p.fit.more)
                    if (p.fit.more > 0) plusN++
                    assertTrue("$tag $name: the row is no wider than its chips", p.rect.w <= p.fit.width + 0.01f)
                }
                val rects = placed.mapNotNull { it.second.first?.rect }
                for (i in rects.indices) for (j in i + 1 until rects.size) assertFalse("$tag: two rows overlap", rects[i].hits(rects[j]))
            }
        }
        println("R19-K plain layouts: $rows rows placed, $missing sides without room, $plusN rows with a +N")
        for ((k, v) in perSize) println("R19-K   $k: ${v[0]} rows, ${v[1]} without room")
        assertTrue("rows are placed in most scenes", rows > missing)
        assertTrue("+N is used", plusN > 0)
    }

    @Test fun aRoomyArenaShowsEveryChipOfOneAndThreeOnTheWildSideAndOneOnOurs() {
        for ((w, h) in listOf(412f to 692f, 412f to 760f, 800f to 1100f)) {
            val cards = ArenaGeo.cards(0f, false, true)
            val plan = ArenaGeo.plan(w, h, cards, false, true, null)
            val scene = ConditionSpot.Scene(w, h, null, listOf(r(plan.wildCard), r(plan.playerCard), r(plan.wildSprite.art()).grow(3f), r(plan.playerSprite.art()).grow(3f)))
            for (n in listOf(1, 3)) {
                val wild = marks(Slot.WILD, wildKeys, n); val ours = marks(Slot.OURS, ourKeys, n)
                val all = ConditionSpot.all(scene, r(plan.wildCard), r(plan.playerCard), FloatArray(n) { chipWidth(wild[it]) }, FloatArray(n) { chipWidth(ours[it]) }, FloatArray(1) { 90f }, z)
                assertEquals("${w}x$h n=$n wild", n, all.wild!!.fit.shown)
                assertEquals(0, all.wild!!.fit.more)
                // ours stands in the bottom corner with its Pokémon right above it: its row sits beside the box, where less is free
                assertEquals("${w}x$h n=$n ours: every condition counted", n, all.ours!!.fit.shown + all.ours!!.fit.more)
                assertTrue("${w}x$h n=$n ours: at least one chip", all.ours!!.fit.shown >= 1)
                if (n == 1) assertEquals(0, all.ours!!.fit.more)
            }
        }
    }

    @Test fun eightConditionsOnARoomyArenaShowTheMostImportantAndAPlusN() {
        val (w, h) = 412f to 692f
        val cards = ArenaGeo.cards(0f, false, true)
        val plan = ArenaGeo.plan(w, h, cards, false, true, null)
        val scene = ConditionSpot.Scene(w, h, null, listOf(r(plan.wildCard), r(plan.playerCard), r(plan.wildSprite.art()).grow(3f), r(plan.playerSprite.art()).grow(3f)))
        val wild = marks(Slot.WILD, wildKeys, 8); val ours = marks(Slot.OURS, ourKeys, 8)
        val all = ConditionSpot.all(scene, r(plan.wildCard), r(plan.playerCard), FloatArray(8) { chipWidth(wild[it]) }, FloatArray(8) { chipWidth(ours[it]) }, FloatArray(0), z)
        for (p in listOf(all.wild!!, all.ours!!)) {
            assertTrue("some chips and a +N", p.fit.shown in 1..7 && p.fit.more == 8 - p.fit.shown)
        }
        assertEquals("the wild row starts with the most important one", "confusion", wild[0].key)
    }

    @Test fun theTopLeftCornerIsNeverTakenByTheFieldChip() {
        for ((w, h) in sizes) {
            val scene = ConditionSpot.Scene(w, h, null, emptyList())
            val p = ConditionSpot.corner(scene, floatArrayOf(90f), 4f, moreWidth, 17f, 5f)!!
            assertTrue("${w}x$h: on the right, or at the bottom", p.rect.r >= w - 6f || p.rect.t > h / 2f)
            assertFalse("${w}x$h: not in the top-left", p.rect.l < 20f && p.rect.t < 20f)
        }
    }

    @Test fun aCornerChipGoesToTheNextCornerWhenTheFirstIsTaken() {
        val scene = ConditionSpot.Scene(300f, 200f, null, listOf(ConditionSpot.R(150f, 0f, 300f, 60f)))   // the wild Pokémon in the top right
        val p = ConditionSpot.corner(scene, floatArrayOf(90f), 4f, moreWidth, 17f, 5f)!!
        assertTrue("bottom right", p.rect.t > 100f && p.rect.r > 290f)
    }

    @Test fun aFieldChipFindsRoomAlongTheTopWhenEveryCornerIsTaken() {
        val taken = listOf(ConditionSpot.R(250f, 0f, 300f, 60f), ConditionSpot.R(200f, 140f, 300f, 200f), ConditionSpot.R(0f, 140f, 100f, 200f))
        val scene = ConditionSpot.Scene(300f, 200f, null, taken)
        val p = ConditionSpot.corner(scene, floatArrayOf(90f), 4f, moreWidth, 17f, 5f)!!
        assertTrue("along the top, clear of the block in the corner", p.rect.t < 10f && p.rect.r <= 250f)
        assertTrue("and not at the toasts' end of the top edge", p.rect.l >= 120f)
        for (o in scene.blocks) assertFalse(p.rect.hits(o))
        // nothing free at all: no chip
        val full = ConditionSpot.Scene(300f, 200f, null, listOf(ConditionSpot.R(0f, 0f, 300f, 200f)))
        assertNull(ConditionSpot.corner(full, floatArrayOf(90f), 4f, moreWidth, 17f, 5f))
    }

    @Test fun theRoomInsetKeepsEveryRowOffTheArenasEdge() {
        val scene = ConditionSpot.Scene(300f, 200f, null, emptyList(), inset = 4f)
        val p = ConditionSpot.corner(scene, floatArrayOf(90f), 4f, moreWidth, 17f, 0f)!!
        assertTrue(p.rect.r <= 296f + 0.01f && p.rect.t >= 4f - 0.01f)
    }

    // ---------------------------------------------------------------- 287

    @Test fun backAndATapCloseEveryArmedBubbleAndNothingElse() {
        val hub = BubbleDismiss.Hub()
        assertFalse("nothing open: Back is not taken", hub.back())
        var touched = 0; var closedByBack = 0
        val a = BubbleDismiss.Armed(onTouch = { _, _ -> touched++ }, onBack = { closedByBack++ })
        hub.add(a); hub.add(a)
        assertTrue(hub.isArmed)
        hub.touch(10f, 20f)
        assertEquals(1, touched)
        assertTrue("Back is taken by the bubble", hub.back())
        assertEquals(1, closedByBack)
        hub.remove(a)
        assertFalse(hub.isArmed)
        assertFalse("closed: Back goes to the screen again", hub.back())
        hub.touch()
        assertEquals("nobody listens any more", 1, touched)
    }
}
