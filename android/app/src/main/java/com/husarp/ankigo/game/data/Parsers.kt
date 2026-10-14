package com.husarp.ankigo.game.data

import com.husarp.ankigo.game.Category
import com.husarp.ankigo.game.Encounters
import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.LevelMove
import com.husarp.ankigo.game.Move
import com.husarp.ankigo.game.Secondary
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.Type
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// Pure Kotlin + org.json (no android.*): unit-tested on the JVM with small hand-written samples
// (never commit real Pokémon data files to the repo).

object Parsers {
    /** The downloaded source files as text. */
    class Sources(
        val pokedexJson: String,   // play.pokemonshowdown.com/data/pokedex.json
        val movesJson: String,     // play.pokemonshowdown.com/data/moves.json
        val learnsetsJson: String, // play.pokemonshowdown.com/data/learnsets.json
        val speciesCsv: String,    // PokeAPI pokemon_species.csv
        val pokemonCsv: String,    // PokeAPI pokemon.csv (base_experience)
        val statsCsv: String,      // PokeAPI pokemon_stats.csv (effort = EV yield)
        val evolutionCsv: String,  // PokeAPI pokemon_evolution.csv (minimum_happiness)
        val experienceCsv: String, // PokeAPI experience.csv (growth tables)
        val itemsCsv: String,      // PokeAPI items.csv (cost)
    )

    private const val CACHE_FORMAT = 2 // 2: regional forms (form, baseNum, spriteId on every species)
    private const val MAX_NUM = 1025
    private val HALF_HP_MOVES = setOf("superfang", "naturesmadness", "ruination")
    private val SKIPPED_NONSTANDARD = setOf("CAP", "Future", "Gmax")

    private fun fail(msg: String): Nothing = throw IllegalStateException(msg)

    /** Builds GameData; throws IllegalStateException with a clear message when a source has an unexpected shape. */
    fun build(src: Sources, now: Long = System.currentTimeMillis()): GameData {
        val dex = json("pokedex.json", src.pokedexJson)
        val moves = buildMoves(json("moves.json", src.movesJson))
        val learn = json("learnsets.json", src.learnsetsJson)
        val speciesRows = csv(src.speciesCsv).associateBy { it["id"]?.toIntOrNull() }
        val pokemonRows = csv(src.pokemonCsv)
        val baseExp = pokemonRows.associate { (it["id"]?.toIntOrNull() ?: -1) to (it["base_experience"]?.toIntOrNull() ?: 1) }
        val effort = HashMap<Int, IntArray>()
        for (r in csv(src.statsCsv)) {
            val id = r["pokemon_id"]?.toIntOrNull() ?: continue
            val stat = r["stat_id"]?.toIntOrNull() ?: continue
            if (stat !in 1..6) continue
            effort.getOrPut(id) { IntArray(6) }[stat - 1] = r["effort"]?.toIntOrNull() ?: 0
        }
        val happiness = HashMap<Int, Int>()
        for (r in csv(src.evolutionCsv)) {
            val to = r["evolved_species_id"]?.toIntOrNull() ?: continue
            val h = r["minimum_happiness"]?.toIntOrNull() ?: continue
            happiness.putIfAbsent(to, h)
        }
        val growth = buildGrowth(src.experienceCsv)
        val prices = HashMap<String, Int>()
        for (r in csv(src.itemsCsv)) {
            val id = r["identifier"]?.takeIf { it.isNotEmpty() } ?: continue
            prices[id] = r["cost"]?.toIntOrNull() ?: 0
        }

        // Base forms: one Showdown entry per national number 1..1025 without a "forme".
        val base = LinkedHashMap<Int, Pair<String, JSONObject>>()
        for (key in dex.keys()) {
            val e = dex.optJSONObject(key) ?: continue
            if (!e.has("num") || e.has("forme")) continue
            val num = e.optInt("num", 0)
            if (num !in 1..MAX_NUM) continue
            base.putIfAbsent(num, key to e)
        }
        val numByName = HashMap<String, Int>()
        for ((num, p) in base) numByName[p.second.optString("name")] = num
        val forms = regionalFormEntries(dex, pokemonRows, base.keys)
        val formNumByName = HashMap<String, Int>()
        for ((id, f) in forms) formNumByName[f.entry.optString("name")] = id
        /** The Showdown entry of a base species or a regional form. */
        fun entryOf(num: Int): JSONObject = base[num]?.second ?: forms.getValue(num).entry

        val species = LinkedHashMap<Int, Species>()
        for (num in base.keys.sorted()) {
            val (id, e) = base.getValue(num)
            val srow = speciesRows[num]
            val types = e.optJSONArray("types")?.let { a ->
                (0 until a.length()).map { Type.parse(a.getString(it)) ?: fail("pokedex.json: unknown type '${a.getString(it)}' on $id") }
            } ?: fail("pokedex.json: no types on $id")
            if (types.isEmpty()) fail("pokedex.json: no types on $id")
            val bs = e.optJSONObject("baseStats") ?: fail("pokedex.json: no baseStats on $id")
            val abilities = e.optJSONObject("abilities")?.let { a -> listOf("0", "1").mapNotNull { k -> a.optString(k, "").takeIf { it.isNotEmpty() } } } ?: emptyList()
            val prevo = e.optString("prevo", "").takeIf { it.isNotEmpty() }?.let { numByName[it] ?: formNumByName[it] } // Perrserker <- Meowth-Galar
            val ev = effort[num] ?: IntArray(6)
            val evolutions = (e.optJSONArray("evos")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList())
                .mapNotNull { name -> numByName[name]?.let { to -> evolution(to, base.getValue(to).second, happiness[to]) } }
            species[num] = Species(
                num = num, id = id, name = e.optString("name", id),
                gen = srow?.get("generation_id")?.toIntOrNull() ?: genOf(num),
                types = types,
                base = Stats(bs.getInt("hp"), bs.getInt("atk"), bs.getInt("def"), bs.getInt("spa"), bs.getInt("spd"), bs.getInt("spe")),
                maleRatio = maleRatio(e),
                abilities = abilities,
                prevo = prevo,
                evolutions = evolutions,
                baseExp = baseExp[num] ?: 1,
                growthRate = srow?.get("growth_rate_id")?.toIntOrNull() ?: 2,
                evYield = Stats.of(ev.toList()),
                isBaby = srow?.get("is_baby") == "1",
                isLegendary = srow?.get("is_legendary") == "1",
                isMythical = srow?.get("is_mythical") == "1",
                levelMoves = emptyList(), tmMoves = emptySet(), // filled below
                evoLevel = if (e.has("evoLevel")) e.optInt("evoLevel") else null,
                evoType = e.optString("evoType", "").takeIf { it.isNotEmpty() },
            )
        }
        for ((num, s) in species.entries.toList()) {
            val ls = learn.optJSONObject(s.id)?.optJSONObject("learnset") ?: continue
            val (lm, tm) = learnset(ls, moves.keys)
            species[num] = s.copy(levelMoves = lm, tmMoves = tm)
        }
        if (species.size < 1000) fail("Game data looks wrong: only ${species.size} species found (need 1000+). Showdown's pokedex.json may have changed.")

        // Regional forms (never fatal: a form whose data is missing is simply left out).
        for ((id, f) in forms) {
            val b0 = species[f.baseNum] ?: continue
            val e = f.entry
            val srow = speciesRows[f.baseNum]
            val ev = effort[id] ?: IntArray(6)
            val bs = e.getJSONObject("baseStats")
            val ls = learn.optJSONObject(f.key)?.optJSONObject("learnset")
            val (lm, tm) = if (ls != null) learnset(ls, moves.keys) else b0.levelMoves to b0.tmMoves
            val formName = e.optString("forme")
            val baseSpecies = e.optString("baseSpecies", "").ifEmpty { b0.name }
            species[id] = Species(
                num = id, id = f.key, name = "${REGION_ADJECTIVE.getValue(f.region)} ${b0.name}",
                gen = Encounters.REGION_GEN.getValue(f.region),
                types = formTypes(e)!!, // checked when the form was found
                base = Stats(bs.getInt("hp"), bs.getInt("atk"), bs.getInt("def"), bs.getInt("spa"), bs.getInt("spd"), bs.getInt("spe")),
                maleRatio = if (e.has("genderRatio") || e.has("gender")) maleRatio(e) else maleRatio(entryOf(f.baseNum)),
                abilities = e.optJSONObject("abilities")?.let { a -> listOf("0", "1").mapNotNull { k -> a.optString(k, "").takeIf { it.isNotEmpty() } } } ?: emptyList(),
                prevo = e.optString("prevo", "").takeIf { it.isNotEmpty() }?.let { numByName[it] ?: formNumByName[it] },
                evolutions = (e.optJSONArray("evos")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList())
                    .mapNotNull { name -> (numByName[name] ?: formNumByName[name])?.let { to ->
                        evolution(to, entryOf(to), happiness[if (to > MAX_NUM) forms.getValue(to).baseNum else to])
                    } },
                baseExp = baseExp[id] ?: 1,
                growthRate = srow?.get("growth_rate_id")?.toIntOrNull() ?: b0.growthRate,
                evYield = Stats.of(ev.toList()),
                isBaby = srow?.get("is_baby") == "1",
                isLegendary = srow?.get("is_legendary") == "1",
                isMythical = srow?.get("is_mythical") == "1",
                levelMoves = lm, tmMoves = tm,
                evoLevel = if (e.has("evoLevel")) e.optInt("evoLevel") else null,
                evoType = e.optString("evoType", "").takeIf { it.isNotEmpty() },
                form = formName, baseNum = f.baseNum, spriteId = toId(baseSpecies) + "-" + toId(formName),
            )
        }

        if (moves.size < 700) fail("Game data looks wrong: only ${moves.size} moves found (need 700+). Showdown's moves.json may have changed.")
        if (growth.size != 6 || growth.values.any { it[100] == 0 }) fail("Game data looks wrong: experience.csv needs 6 growth rates of 100 levels.")
        if (prices.size < 1000) fail("Game data looks wrong: only ${prices.size} item prices found (need 1000+).")
        return GameData(species, moves, growth, prices, now)
    }

    // ---- Species helpers ----

    private fun genOf(num: Int) = when {
        num <= 151 -> 1; num <= 251 -> 2; num <= 386 -> 3; num <= 493 -> 4; num <= 649 -> 5
        num <= 721 -> 6; num <= 809 -> 7; num <= 905 -> 8; else -> 9
    }

    private val REGION_ADJECTIVE = mapOf("alola" to "Alolan", "galar" to "Galarian", "hisui" to "Hisuian", "paldea" to "Paldean")

    /** A regional form found in the downloaded files: its Showdown key and entry, the national number of its base species, its region. */
    private class FormEntry(val key: String, val entry: JSONObject, val baseNum: Int, val region: String)

    private fun formTypes(e: JSONObject): List<Type>? {
        val a = e.optJSONArray("types") ?: return null
        val out = (0 until a.length()).map { Type.parse(a.optString(it)) ?: return null }
        return out.takeIf { it.isNotEmpty() }
    }

    /**
     * The regional forms (Encounters.REGIONAL_FORMS, PokeAPI pokemon ids) that both source files describe: pokemon.csv names the
     * form ("tauros-paldea-combat-breed"), Showdown's key is that identifier without "-standard" / "-breed" and without
     * punctuation. A form without a pokemon.csv row, a Showdown entry with a "forme", valid types and stats, or whose base
     * species isn't in [baseNums] is left out; the build never fails because of one.
     */
    private fun regionalFormEntries(dex: JSONObject, rows: List<Map<String, String>>, baseNums: Set<Int>): Map<Int, FormEntry> {
        val byId = HashMap<Int, Map<String, String>>()
        for (r in rows) r["id"]?.toIntOrNull()?.let { byId[it] = r }
        val out = LinkedHashMap<Int, FormEntry>()
        for ((region, ids) in Encounters.REGIONAL_FORMS) for (id in ids) {
            val row = byId[id] ?: continue
            val key = toId(row["identifier"].orEmpty().removeSuffix("-standard").removeSuffix("-breed"))
            val e = dex.optJSONObject(key) ?: continue
            val bs = e.optJSONObject("baseStats")
            if (!e.has("forme") || formTypes(e) == null || bs == null || listOf("hp", "atk", "def", "spa", "spd", "spe").any { !bs.has(it) }) continue
            val baseNum = row["species_id"]?.toIntOrNull() ?: e.optInt("num", 0)
            if (baseNum !in baseNums) continue
            out[id] = FormEntry(key, e, baseNum, region)
        }
        return out
    }

    private fun maleRatio(e: JSONObject): Double? {
        e.optJSONObject("genderRatio")?.let { return it.optDouble("M", 0.5) }
        return when (e.optString("gender", "")) { "M" -> 1.0; "F" -> 0.0; "N" -> null; else -> 0.5 }
    }

    /** Showdown item name -> PokeAPI-style id: "King's Rock" -> "kings-rock". */
    private fun itemId(name: String) = name.lowercase().replace("'", "").replace("’", "").trim().replace(' ', '-')

    /** Showdown move name -> id: "Ancient Power" -> "ancientpower". */
    private fun moveId(name: String) = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    /** Showdown toID: lower case, letters and digits only ("Farfetch’d" -> "farfetchd", "Paldea-Combat" -> "paldeacombat"). */
    private fun toId(name: String) = moveId(name)

    /** The way the pre-evolution turns into [to], built from the evolved species' own Showdown fields. */
    private fun evolution(to: Int, e: JSONObject, happiness: Int?): Evolution {
        val type = e.optString("evoType", "").takeIf { it.isNotEmpty() }
        val level = if (e.has("evoLevel")) e.optInt("evoLevel") else null
        val cond = e.optString("evoCondition", "").takeIf { it.isNotEmpty() }
        val item = e.optString("evoItem", "").takeIf { it.isNotEmpty() }?.let { itemId(it) }
        val time = when {
            cond == null -> null
            cond.contains("during the day", true) -> "day"
            cond.contains("at night", true) -> "night"
            else -> null
        }
        val gender = e.optString("gender", "").takeIf { it == "M" || it == "F" }?.get(0)
        fun ev(kind: EvoKind, level: Int? = null, item: String? = null, move: String? = null, moveType: Type? = null, minFriendship: Int? = null) =
            Evolution(to, kind, level, item, move, moveType, time, gender, minFriendship, cond)
        return when (type) {
            // A few useItem entries (Kleavor) carry the item name in evoCondition instead of evoItem.
            "useItem" -> (item ?: cond?.let { itemId(it) })?.let { ev(EvoKind.ITEM, item = it) } ?: ev(EvoKind.OTHER)
            "levelHold" -> item?.let { ev(EvoKind.ITEM, item = it) } ?: ev(EvoKind.OTHER)
            "trade" -> ev(EvoKind.TRADE, item = item)
            "levelFriendship" -> ev(EvoKind.FRIENDSHIP, minFriendship = happiness)
            "levelMove" -> e.optString("evoMove", "").takeIf { it.isNotEmpty() }?.let { ev(EvoKind.LEVEL_MOVE, move = moveId(it)) } ?: ev(EvoKind.OTHER)
            "levelExtra" -> when {
                cond != null && cond.contains("Fairy-type move", true) -> ev(EvoKind.FRIENDSHIP, moveType = Type.FAIRY, minFriendship = happiness)
                level != null -> ev(EvoKind.LEVEL, level = level)
                else -> ev(EvoKind.OTHER)
            }
            "other" -> ev(EvoKind.OTHER)
            else -> if (level != null) ev(EvoKind.LEVEL, level = level) else ev(EvoKind.OTHER)
        }
    }

    // ---- Learnsets (Ankimon: newest generation that has level-up data) ----

    private val CODE = Regex("^(\\d+)([A-Z])(\\d*)$")

    private fun learnset(ls: JSONObject, known: Set<String>): Pair<List<LevelMove>, Set<String>> {
        val level = HashMap<Int, MutableSet<LevelMove>>() // generation -> level-up moves
        val tm = HashMap<Int, MutableSet<String>>()       // generation -> TM moves
        for (move in ls.keys()) {
            if (move !in known) continue
            val codes = ls.optJSONArray(move) ?: continue
            for (i in 0 until codes.length()) {
                val m = CODE.matchEntire(codes.optString(i)) ?: continue
                val gen = m.groupValues[1].toInt()
                when (m.groupValues[2]) {
                    "L" -> level.getOrPut(gen) { LinkedHashSet() }.add(LevelMove(m.groupValues[3].toIntOrNull() ?: 1, move))
                    "R" -> level.getOrPut(gen) { LinkedHashSet() }.add(LevelMove(1, move))
                    "M" -> tm.getOrPut(gen) { LinkedHashSet() }.add(move)
                }
            }
        }
        val g = (9 downTo 1).firstOrNull { level.containsKey(it) }
        // Sorted by level, then name, so the result doesn't depend on JSON key order.
        val lm = g?.let { level.getValue(it).sortedWith(compareBy({ x -> x.level }, { x -> x.move })) } ?: emptyList()
        val tg = if (g != null && tm.containsKey(g)) g else (9 downTo 1).firstOrNull { tm.containsKey(it) }
        return lm to (tg?.let { tm.getValue(it).toSet() } ?: emptySet())
    }

    // ---- Moves ----

    private fun flag(m: JSONObject, key: String) = m.has(key) && m.opt(key) != false

    private fun buildMoves(o: JSONObject): Map<String, Move> {
        val out = LinkedHashMap<String, Move>()
        for (id in o.keys()) {
            val m = o.optJSONObject(id) ?: continue
            if (flag(m, "isZ") || flag(m, "isMax")) continue
            if (m.optString("isNonstandard", "") in SKIPPED_NONSTANDARD) continue
            val cat = when (m.optString("category")) {
                "Physical" -> Category.PHYSICAL; "Special" -> Category.SPECIAL; "Status" -> Category.STATUS
                else -> fail("moves.json: unknown category on $id")
            }
            val type = Type.parse(m.optString("type")) ?: fail("moves.json: unknown type '${m.optString("type")}' on $id")
            val dmg = m.opt("damage")
            val ohko = m.has("ohko")
            val halfHp = id in HALF_HP_MOVES
            var power = m.optInt("basePower", 0)
            if (cat != Category.STATUS && power == 0 && dmg == null && !ohko && !halfHp) power = Tuning.UNKNOWN_POWER
            val sec = (m.optJSONObject("secondary") ?: m.optJSONArray("secondaries")?.optJSONObject(0))?.let { s ->
                Secondary(
                    chance = s.optInt("chance", 100),
                    status = s.optString("status", "").takeIf { it.isNotEmpty() },
                    boosts = boostMap(s.optJSONObject("boosts")),
                    selfBoosts = boostMap(s.optJSONObject("self")?.optJSONObject("boosts")),
                    flinch = s.optString("volatileStatus", "") == "flinch",
                )
            }
            val hit = m.opt("multihit")
            val multi = when (hit) {
                is Number -> hit.toInt()..hit.toInt()
                is JSONArray -> hit.getInt(0)..hit.getInt(1)
                else -> null
            }
            out[id] = Move(
                id = id, name = m.optString("name", id), type = type, category = cat, power = power,
                accuracy = if (m.opt("accuracy") is Number) m.getInt("accuracy") else null,
                priority = m.optInt("priority", 0), critRatio = m.optInt("critRatio", 1),
                target = m.optString("target", "normal"),
                boosts = boostMap(m.optJSONObject("boosts")),
                selfBoosts = boostMap(m.optJSONObject("self")?.optJSONObject("boosts")),
                status = m.optString("status", "").takeIf { it.isNotEmpty() },
                secondary = sec,
                drain = pair(m.optJSONArray("drain")), recoil = pair(m.optJSONArray("recoil")), heal = pair(m.optJSONArray("heal")),
                multihit = multi,
                fixedDamage = (dmg as? Number)?.toInt(),
                levelDamage = dmg == "level",
                halfHp = halfHp, ohko = ohko,
                selfDestruct = m.has("selfdestruct"),
                willCrit = flag(m, "willCrit"),
            )
        }
        return out
    }

    private fun boostMap(o: JSONObject?): Map<String, Int>? =
        o?.takeIf { it.length() > 0 }?.let { b -> b.keys().asSequence().associateWith { b.getInt(it) } }

    private fun pair(a: JSONArray?): Pair<Int, Int>? = a?.let { it.getInt(0) to it.getInt(1) }

    // ---- Growth tables ----

    private fun buildGrowth(text: String): Map<Int, IntArray> {
        val out = LinkedHashMap<Int, IntArray>()
        for (r in csv(text)) {
            val rate = r["growth_rate_id"]?.toIntOrNull() ?: continue
            val level = r["level"]?.toIntOrNull() ?: continue
            val xp = r["experience"]?.toIntOrNull() ?: continue
            if (level !in 1..100) continue
            out.getOrPut(rate) { IntArray(101) }[level] = xp
        }
        return out
    }

    // ---- Cache ----

    /** Compact own JSON cache of GameData (written once after download, read at every app start). */
    fun encodeCache(data: GameData): String {
        val root = JSONObject()
        root.put("format", CACHE_FORMAT)
        root.put("builtAt", data.builtAt)
        val sp = JSONArray()
        for (s in data.species.values.sortedBy { it.num }) sp.put(encodeSpecies(s))
        root.put("species", sp)
        val mv = JSONArray()
        for (m in data.moves.values) mv.put(encodeMove(m))
        root.put("moves", mv)
        val gr = JSONObject()
        for ((k, v) in data.growth) gr.put(k.toString(), JSONArray(v.toList()))
        root.put("growth", gr)
        val pr = JSONObject()
        for ((k, v) in data.itemPrices) pr.put(k, v)
        root.put("prices", pr)
        return root.toString()
    }

    fun decodeCache(json: String): GameData {
        val root = try { JSONObject(json) } catch (e: JSONException) { fail("Cache is not valid JSON: ${e.message}") }
        if (root.optInt("format", -1) != CACHE_FORMAT) fail("Cache format ${root.optInt("format", -1)} is not supported (need $CACHE_FORMAT)")
        try {
            val sp = root.getJSONArray("species")
            val species = LinkedHashMap<Int, Species>()
            for (i in 0 until sp.length()) decodeSpecies(sp.getJSONArray(i)).let { species[it.num] = it }
            val mv = root.getJSONArray("moves")
            val moves = LinkedHashMap<String, Move>()
            for (i in 0 until mv.length()) decodeMove(mv.getJSONArray(i)).let { moves[it.id] = it }
            val gr = root.getJSONObject("growth")
            val growth = LinkedHashMap<Int, IntArray>()
            for (k in gr.keys()) gr.getJSONArray(k).let { a -> growth[k.toInt()] = IntArray(a.length()) { a.getInt(it) } }
            val pr = root.getJSONObject("prices")
            val prices = HashMap<String, Int>()
            for (k in pr.keys()) prices[k] = pr.getInt(k)
            return GameData(species, moves, growth, prices, root.optLong("builtAt", 0))
        } catch (e: JSONException) {
            fail("Cache is damaged: ${e.message}")
        }
    }

    private fun nul(): Any = JSONObject.NULL

    private fun strs(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
    private fun intList(a: JSONArray) = (0 until a.length()).map { a.getInt(it) }
    private fun optI(a: JSONArray, i: Int): Int? = if (a.isNull(i)) null else a.getInt(i)
    private fun optS(a: JSONArray, i: Int): String? = if (a.isNull(i)) null else a.getString(i)

    private fun encodeSpecies(s: Species): JSONArray {
        val evos = JSONArray()
        for (e in s.evolutions) evos.put(JSONArray(listOf(
            e.to, e.kind.name, e.level ?: nul(), e.item ?: nul(), e.move ?: nul(), e.moveType?.name ?: nul(),
            e.time ?: nul(), e.gender?.toString() ?: nul(), e.minFriendship ?: nul(), e.condition ?: nul(),
        )))
        val lm = JSONArray()
        for (m in s.levelMoves) { lm.put(m.level); lm.put(m.move) }
        val flags = (if (s.isBaby) 1 else 0) or (if (s.isLegendary) 2 else 0) or (if (s.isMythical) 4 else 0)
        return JSONArray(listOf(
            s.num, s.id, s.name, s.gen, JSONArray(s.types.map { it.name }), JSONArray(s.base.toList()), s.maleRatio ?: nul(),
            JSONArray(s.abilities), s.prevo ?: nul(), evos, s.baseExp, s.growthRate, JSONArray(s.evYield.toList()), flags,
            lm, JSONArray(s.tmMoves.sorted()), s.evoLevel ?: nul(), s.evoType ?: nul(),
            s.form ?: nul(), s.baseNum, s.spriteId,
        ))
    }

    private fun decodeSpecies(a: JSONArray): Species {
        val evos = a.getJSONArray(9)
        val lm = a.getJSONArray(14)
        val flags = a.getInt(13)
        return Species(
            num = a.getInt(0), id = a.getString(1), name = a.getString(2), gen = a.getInt(3),
            types = strs(a.getJSONArray(4)).map { Type.parse(it) ?: fail("Cache: unknown type $it") },
            base = Stats.of(intList(a.getJSONArray(5))),
            maleRatio = if (a.isNull(6)) null else a.getDouble(6),
            abilities = strs(a.getJSONArray(7)),
            prevo = optI(a, 8),
            evolutions = (0 until evos.length()).map { i ->
                val e = evos.getJSONArray(i)
                Evolution(
                    to = e.getInt(0), kind = EvoKind.valueOf(e.getString(1)), level = optI(e, 2), item = optS(e, 3), move = optS(e, 4),
                    moveType = optS(e, 5)?.let { Type.parse(it) }, time = optS(e, 6), gender = optS(e, 7)?.get(0),
                    minFriendship = optI(e, 8), condition = optS(e, 9),
                )
            },
            baseExp = a.getInt(10), growthRate = a.getInt(11),
            evYield = Stats.of(intList(a.getJSONArray(12))),
            isBaby = flags and 1 != 0, isLegendary = flags and 2 != 0, isMythical = flags and 4 != 0,
            levelMoves = (0 until lm.length() / 2).map { LevelMove(lm.getInt(it * 2), lm.getString(it * 2 + 1)) },
            tmMoves = strs(a.getJSONArray(15)).toSet(),
            evoLevel = optI(a, 16), evoType = optS(a, 17),
            form = optS(a, 18), baseNum = a.getInt(19), spriteId = a.getString(20),
        )
    }

    private fun boostsJson(m: Map<String, Int>?): Any = m?.let { JSONObject(it) } ?: nul()
    private fun boostsOf(o: Any?): Map<String, Int>? = (o as? JSONObject)?.let { b -> b.keys().asSequence().associateWith { b.getInt(it) } }
    private fun pairJson(p: Pair<Int, Int>?): Any = p?.let { JSONArray(listOf(it.first, it.second)) } ?: nul()
    private fun pairOf(a: JSONArray, i: Int): Pair<Int, Int>? = if (a.isNull(i)) null else a.getJSONArray(i).let { it.getInt(0) to it.getInt(1) }

    private fun encodeMove(m: Move): JSONArray {
        val sec: Any = m.secondary?.let {
            JSONArray(listOf(it.chance, it.status ?: nul(), boostsJson(it.boosts), boostsJson(it.selfBoosts), if (it.flinch) 1 else 0))
        } ?: nul()
        val flags = (if (m.levelDamage) 1 else 0) or (if (m.halfHp) 2 else 0) or (if (m.ohko) 4 else 0) or
            (if (m.selfDestruct) 8 else 0) or (if (m.willCrit) 16 else 0)
        return JSONArray(listOf(
            m.id, m.name, m.type.name, m.category.name, m.power, m.accuracy ?: nul(), m.priority, m.critRatio, m.target,
            boostsJson(m.boosts), boostsJson(m.selfBoosts), m.status ?: nul(), sec,
            pairJson(m.drain), pairJson(m.recoil), pairJson(m.heal),
            m.multihit?.let { JSONArray(listOf(it.first, it.last)) } ?: nul(), m.fixedDamage ?: nul(), flags,
        ))
    }

    private fun decodeMove(a: JSONArray): Move {
        val flags = a.getInt(18)
        val sec = if (a.isNull(12)) null else a.getJSONArray(12).let { s ->
            Secondary(s.getInt(0), optS(s, 1), boostsOf(s.opt(2)), boostsOf(s.opt(3)), s.getInt(4) == 1)
        }
        return Move(
            id = a.getString(0), name = a.getString(1),
            type = Type.parse(a.getString(2)) ?: fail("Cache: unknown type ${a.getString(2)}"),
            category = Category.valueOf(a.getString(3)), power = a.getInt(4), accuracy = optI(a, 5),
            priority = a.getInt(6), critRatio = a.getInt(7), target = a.getString(8),
            boosts = boostsOf(a.opt(9)), selfBoosts = boostsOf(a.opt(10)), status = optS(a, 11), secondary = sec,
            drain = pairOf(a, 13), recoil = pairOf(a, 14), heal = pairOf(a, 15),
            multihit = if (a.isNull(16)) null else a.getJSONArray(16).let { it.getInt(0)..it.getInt(1) },
            fixedDamage = optI(a, 17),
            levelDamage = flags and 1 != 0, halfHp = flags and 2 != 0, ohko = flags and 4 != 0,
            selfDestruct = flags and 8 != 0, willCrit = flags and 16 != 0,
        )
    }

    // ---- CSV ----

    /** Minimal CSV reader (header row -> maps; handles quoted fields). */
    fun csv(text: String): List<Map<String, String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        val t = text.removePrefix("﻿")
        fun endField() { row.add(sb.toString()); sb.setLength(0) }
        fun endRow() { endField(); if (row.size > 1 || row[0].isNotEmpty()) rows.add(row); row = ArrayList() }
        while (i < t.length) {
            val c = t[i]
            when {
                quoted -> if (c == '"') {
                    if (i + 1 < t.length && t[i + 1] == '"') { sb.append('"'); i++ } else quoted = false
                } else sb.append(c)
                c == '"' -> quoted = true
                c == ',' -> endField()
                c == '\n' -> endRow()
                c == '\r' -> {}
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) endRow()
        if (rows.isEmpty()) return emptyList()
        val header = rows[0]
        return rows.drop(1).map { r -> LinkedHashMap<String, String>().also { m -> header.forEachIndexed { k, h -> m[h] = r.getOrElse(k) { "" } } } }
    }

    private fun json(name: String, text: String): JSONObject =
        try { JSONObject(text) } catch (e: JSONException) { fail("$name is not valid JSON: ${e.message}") }
}
