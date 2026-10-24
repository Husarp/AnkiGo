package com.husarp.ankigo.game.data

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Move
import com.husarp.ankigo.game.Secondary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** R17C-1: the rule data of Showdown's moves.json (flags, conditions, extra secondaries, other-stat moves ...) and its cache. */
class R17C1ParserTest {
    // Hand-copied entries of the real moves.json (functions are not in the file, flags only the ones that matter here)
    private val sample = """{
      "firefang":{"num":424,"accuracy":95,"basePower":65,"category":"Physical","name":"Fire Fang","priority":0,
        "flags":{"contact":1,"protect":1,"mirror":1,"bite":1},"secondaries":[{"chance":10,"status":"brn"},{"chance":10,"volatileStatus":"flinch"}],"target":"normal","type":"Fire"},
      "triattack":{"num":161,"accuracy":100,"basePower":80,"category":"Special","name":"Tri Attack","priority":0,
        "flags":{"protect":1,"mirror":1},"secondary":{"chance":20},"target":"normal","type":"Normal"},
      "psyshock":{"num":473,"accuracy":100,"basePower":80,"category":"Special","overrideDefensiveStat":"def","name":"Psyshock","priority":0,
        "flags":{"protect":1},"target":"normal","type":"Psychic"},
      "bodypress":{"num":776,"accuracy":100,"basePower":80,"category":"Physical","name":"Body Press","priority":0,
        "flags":{"contact":1,"protect":1},"overrideOffensiveStat":"def","target":"normal","type":"Fighting"},
      "foulplay":{"num":492,"accuracy":100,"basePower":95,"category":"Physical","name":"Foul Play","priority":0,
        "flags":{"contact":1},"overrideOffensivePokemon":"target","target":"normal","type":"Dark"},
      "sacredsword":{"num":533,"accuracy":100,"basePower":90,"category":"Physical","name":"Sacred Sword","priority":0,
        "flags":{"contact":1,"slicing":1},"ignoreEvasion":true,"ignoreDefensive":true,"target":"normal","type":"Fighting"},
      "highjumpkick":{"num":136,"accuracy":90,"basePower":130,"category":"Physical","name":"High Jump Kick","priority":0,
        "flags":{"contact":1},"hasCrashDamage":true,"target":"normal","type":"Fighting"},
      "mindblown":{"num":720,"accuracy":100,"basePower":150,"category":"Special","name":"Mind Blown","priority":0,
        "flags":{"protect":1},"mindBlownRecoil":true,"target":"allAdjacent","type":"Fire"},
      "hyperbeam":{"num":63,"accuracy":90,"basePower":150,"category":"Special","name":"Hyper Beam","priority":0,
        "flags":{"recharge":1,"protect":1},"self":{"volatileStatus":"mustrecharge"},"target":"normal","type":"Normal"},
      "outrage":{"num":200,"accuracy":100,"basePower":120,"category":"Physical","name":"Outrage","priority":0,
        "flags":{"contact":1},"self":{"volatileStatus":"lockedmove"},"target":"randomNormal","type":"Dragon"},
      "raindance":{"num":240,"accuracy":true,"basePower":0,"category":"Status","name":"Rain Dance","priority":0,
        "flags":{"metronome":1},"weather":"RainDance","target":"all","type":"Water"},
      "electricterrain":{"num":604,"accuracy":true,"basePower":0,"category":"Status","name":"Electric Terrain","priority":0,
        "flags":{},"terrain":"electricterrain","target":"all","type":"Electric"},
      "reflect":{"num":115,"accuracy":true,"basePower":0,"category":"Status","name":"Reflect","priority":0,
        "flags":{"snatch":1},"sideCondition":"reflect","target":"allySide","type":"Psychic"},
      "trickroom":{"num":433,"accuracy":true,"basePower":0,"category":"Status","name":"Trick Room","priority":-7,
        "flags":{},"pseudoWeather":"trickroom","target":"all","type":"Psychic"},
      "wish":{"num":273,"accuracy":true,"basePower":0,"category":"Status","name":"Wish","priority":0,
        "flags":{"heal":1},"slotCondition":"Wish","target":"self","type":"Normal"},
      "confuseray":{"num":109,"accuracy":100,"basePower":0,"category":"Status","name":"Confuse Ray","priority":0,
        "flags":{"protect":1},"volatileStatus":"confusion","target":"normal","type":"Ghost"},
      "uturn":{"num":369,"accuracy":100,"basePower":70,"category":"Physical","name":"U-turn","priority":0,
        "flags":{"contact":1},"selfSwitch":true,"target":"normal","type":"Bug"},
      "dragontail":{"num":525,"accuracy":90,"basePower":60,"category":"Physical","name":"Dragon Tail","priority":-6,
        "flags":{"contact":1},"forceSwitch":true,"target":"normal","type":"Dragon"},
      "tripleaxel":{"num":813,"accuracy":90,"basePower":20,"basePowerCallback":true,"category":"Physical","name":"Triple Axel","priority":0,
        "flags":{"contact":1},"multihit":3,"multiaccuracy":true,"target":"normal","type":"Ice"},
      "sleeptalk":{"num":214,"accuracy":true,"basePower":0,"category":"Status","name":"Sleep Talk","priority":0,
        "flags":{},"sleepUsable":true,"callsMove":true,"target":"self","type":"Normal"},
      "thunderwave":{"num":86,"accuracy":90,"basePower":0,"category":"Status","name":"Thunder Wave","priority":0,
        "flags":{"protect":1},"ignoreImmunity":false,"status":"par","target":"normal","type":"Electric"},
      "sonicboom":{"num":49,"accuracy":90,"basePower":0,"category":"Special","name":"Sonic Boom","priority":0,
        "flags":{"protect":1},"damage":20,"target":"normal","type":"Normal"},
      "bide":{"num":117,"accuracy":true,"basePower":0,"category":"Physical","name":"Bide","priority":1,
        "flags":{"contact":1},"ignoreImmunity":true,"volatileStatus":"bide","target":"self","type":"Normal"},
      "tackle":{"num":33,"accuracy":100,"basePower":40,"category":"Physical","name":"Tackle","priority":0,
        "flags":{"contact":1,"protect":1},"secondary":false,"target":"normal","type":"Normal"},
      "bite":{"num":44,"accuracy":100,"basePower":60,"category":"Physical","name":"Bite","priority":0,
        "flags":{"contact":1,"bite":1},"secondary":{"chance":30,"volatileStatus":"flinch"},"target":"normal","type":"Dark"}
    }"""

    private val moves: Map<String, Move> by lazy { Parsers.parseMoves(sample) }
    private fun mv(id: String) = moves.getValue(id)

    @Test fun flagsAreEveryTruthyKey() {
        assertEquals(setOf("contact", "protect", "mirror", "bite"), mv("firefang").flags)
        assertEquals(setOf("recharge", "protect"), mv("hyperbeam").flags)
        assertEquals(emptySet<String>(), mv("trickroom").flags)
        assertTrue("slicing" in mv("sacredsword").flags)
    }

    @Test fun volatileStatusAndSelfVolatile() {
        assertEquals("confusion", mv("confuseray").volatileStatus)
        assertNull(mv("confuseray").selfVolatile)
        assertEquals("mustrecharge", mv("hyperbeam").selfVolatile)
        assertEquals("lockedmove", mv("outrage").selfVolatile)
        assertNull(mv("hyperbeam").volatileStatus)
    }

    @Test fun fieldConditionsAreIds() {
        assertEquals("raindance", mv("raindance").weather)           // "RainDance" in the file
        assertEquals("electricterrain", mv("electricterrain").terrain)
        assertEquals("reflect", mv("reflect").sideCondition)
        assertEquals("trickroom", mv("trickroom").pseudoWeather)
        assertEquals("wish", mv("wish").slotCondition)               // "Wish" in the file
        assertNull(mv("tackle").weather); assertNull(mv("tackle").sideCondition)
    }

    @Test fun moreSecondariesKeepTheFirstOneAsSecondary() {
        val fang = mv("firefang")
        assertEquals(Secondary(10, status = "brn"), fang.secondary)
        assertEquals(listOf(Secondary(10, flinch = true, volatileStatus = "flinch")), fang.moreSecondaries)
        assertEquals(2, fang.allSecondaries.size)
        assertEquals(listOf(Secondary(30, flinch = true, volatileStatus = "flinch")), mv("bite").allSecondaries)
        assertEquals(emptyList<Secondary>(), mv("tackle").allSecondaries)
        assertTrue(mv("bite").secondary!!.flinch)
    }

    @Test fun aSecondaryWithOnlyAChanceIsACodePart() {
        assertTrue(mv("triattack").secondaryOnHit)
        assertEquals(20, mv("triattack").secondary!!.chance)
        assertFalse(mv("firefang").secondaryOnHit)
        assertFalse(mv("bite").secondaryOnHit)
        assertFalse(mv("tackle").secondaryOnHit)
    }

    @Test fun otherStatMoves() {
        assertEquals("def", mv("psyshock").defensiveStat)
        assertNull(mv("psyshock").offensiveStat)
        assertEquals("def", mv("bodypress").offensiveStat)
        assertTrue(mv("foulplay").offensiveFromTarget)
        assertFalse(mv("bodypress").offensiveFromTarget)
        assertTrue(mv("sacredsword").ignoreDefensive); assertTrue(mv("sacredsword").ignoreEvasion)
        assertFalse(mv("psyshock").ignoreDefensive)
    }

    @Test fun singleFlagsAndOddKeys() {
        assertTrue(mv("highjumpkick").crashDamage)
        assertTrue(mv("mindblown").mindBlownRecoil)
        assertTrue(mv("tripleaxel").multiAccuracy)
        assertEquals(3..3, mv("tripleaxel").multihit)
        assertTrue(mv("sleeptalk").sleepUsable)
        assertTrue(mv("uturn").selfSwitch)
        assertTrue(mv("dragontail").forceSwitch)
        assertTrue(mv("bide").ignoreImmunity)           // true
        assertFalse(mv("thunderwave").ignoreImmunity)   // false is "no"
        assertFalse(mv("tackle").breaksProtect)
        assertEquals(20, mv("sonicboom").fixedDamage)
    }

    @Test fun cacheKeepsEveryNewField() {
        val data = GameData(emptyMap(), moves, emptyMap(), emptyMap(), 77L)
        val json = Parsers.encodeCache(data)
        assertTrue(json.contains("\"format\":4"))
        val back = Parsers.decodeCache(json)
        assertEquals(moves.keys.toList(), back.moves.keys.toList())
        for ((id, m) in moves) assertEquals("move $id", m, back.moves.getValue(id))
        assertEquals(2, back.move("firefang")!!.allSecondaries.size)
        assertTrue(back.move("triattack")!!.secondaryOnHit)
        assertEquals("def", back.move("psyshock")!!.defensiveStat)
    }

    @Test fun aMoveWithNoRuleDataWritesAnEmptyObjectAtIndex19() {
        val data = GameData(emptyMap(), mapOf("plain" to Move("plain", "Plain", com.husarp.ankigo.game.Type.NORMAL, com.husarp.ankigo.game.Category.PHYSICAL, 40, 100)), emptyMap(), emptyMap())
        val arr = org.json.JSONObject(Parsers.encodeCache(data)).getJSONArray("moves").getJSONArray(0)
        assertEquals(20, arr.length())
        assertEquals(0, arr.getJSONObject(19).length())
    }

    @Test fun cacheFormat3IsRefusedSoDataStoreRebuildsFromTheRawFiles() {
        val data = GameData(emptyMap(), moves, emptyMap(), emptyMap())
        val old = Parsers.encodeCache(data).replace("\"format\":4", "\"format\":3")
        try { Parsers.decodeCache(old); fail("a format 3 cache must not be accepted") }
        catch (e: IllegalStateException) { assertTrue(e.message!!, "format 3" in e.message!! && "not supported" in e.message!!) }
    }

    @Test fun anOldSecondaryArrayWithoutIndex5StillDecodes() {
        // a hand-made format-4 move whose secondary array is the old 5-entry shape
        val data = GameData(emptyMap(), mapOf("bite" to mv("bite")), emptyMap(), emptyMap())
        val json = org.json.JSONObject(Parsers.encodeCache(data))
        val sec = json.getJSONArray("moves").getJSONArray(0).getJSONArray(12)
        val short = org.json.JSONArray(); for (i in 0 until 5) short.put(sec.get(i))
        json.getJSONArray("moves").getJSONArray(0).put(12, short)
        val back = Parsers.decodeCache(json.toString()).move("bite")!!
        assertEquals(Secondary(30, flinch = true), back.secondary)
    }

    // ---- the real files (skipped without ANKIGO_REAL_DATA) ----

    @Test fun realMovesHaveTheRuleData() {
        val dir = System.getenv("ANKIGO_REAL_DATA")?.takeIf { it.isNotBlank() }?.let { File(it) }
        assumeTrue("ANKIGO_REAL_DATA not set", dir != null && File(dir, "moves.json").isFile)
        val real = Parsers.parseMoves(File(dir!!, "moves.json").readText())
        // 271 of the 863 kept moves (the ticket guessed "more than 300"; the real file has 278 in all, 271 kept)
        assertTrue("contact moves: ${real.values.count { "contact" in it.flags }}", real.values.count { "contact" in it.flags } in 250..300)
        assertEquals("def", real.getValue("psyshock").defensiveStat)
        assertEquals("def", real.getValue("bodypress").offensiveStat)
        assertTrue(real.getValue("foulplay").offensiveFromTarget)
        val fang = real.getValue("firefang")
        assertEquals(2, fang.allSecondaries.size)
        assertTrue(fang.allSecondaries.any { it.flinch })
        assertEquals("mustrecharge", real.getValue("hyperbeam").selfVolatile)
        assertTrue(real.getValue("highjumpkick").crashDamage)
        assertTrue(real.values.count { it.weather != null } >= 5)
        assertTrue(real.values.count { it.sideCondition != null } >= 10)
        assertTrue(real.getValue("triattack").secondaryOnHit)
        // every real move still gets a sentence (MoveText) and it ends with a full stop
        for (m in real.values) assertTrue("${m.id}: ${com.husarp.ankigo.game.MoveText.describe(m)}", com.husarp.ankigo.game.MoveText.describe(m).let { it.isNotBlank() && it.endsWith(".") })
        // the whole cache round trip keeps every move exactly
        val data = GameData(emptyMap(), real, emptyMap(), emptyMap())
        val back = Parsers.decodeCache(Parsers.encodeCache(data))
        assertEquals(real, back.moves)
    }
}
