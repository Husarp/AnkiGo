package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Stat
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Tier
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.IdentityHashMap
import java.util.Locale
import java.util.UUID
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt

// TICKET T52 owns this file. The public signatures are the API the UI (T53) codes against: keep them.

/** What an export wrote, for the result screen. */
data class ExportReport(
    val fresh: Boolean,     // true: there was no kept Ankimon import, so a brand-new Ankimon save was built
    val mons: Int,          // Pokémon rows in the written file
    val added: Int,         // AnkiGo Pokémon written as new rows (caught on the phone, or missing from the kept file)
    val updated: Int,       // rows of the kept file that AnkiGo changed
    val untouched: Int,     // rows of the kept file written back exactly as they were
    val released: Int,      // rows moved to Ankimon's released list (released on the phone)
    val team: Int,
    val cash: Int,
    val caught: Int,        // Pokédex: caught species in the written file
    val items: Int,         // item rows with quantity > 0 in the written file
)

/** Which rows [AnkimonExport.verify] must look at closely: the ones this export wrote ([keys]: the JSON members it set). */
internal class RowNote(val isNew: Boolean, val keys: Set<String>)

/** What reading the written file back with [AnkimonSaveReader] must give (the Pokémon as a count and a checksum). */
internal class ReadBack(val rest: AnkimonSave, val mons: Int, val monsHash: Int)

class ExportResult(val bytes: ByteArray, val report: ExportReport) {
    internal var rows: Map<String, RowNote>? = null
    internal var readBack: ReadBack? = null
}

/** The export can't produce a file it trusts; [message] is shown to the player as is. Nothing was written. */
class ExportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Reference data from AnkiGo's downloaded files that the export needs (Ankimon names and item numbers). */
class AnkimonRefs internal constructor(
    internal val items: Map<String, ItemRef>,   // PokeAPI item identifier -> its items.csv row
    internal val moveNums: Map<String, Int>,    // Showdown move id -> Showdown `num` (a TM item is numbered 20000 + num)
    internal val forms: Map<Int, Int>,          // PokeAPI form id (> 10000) -> base species number (pokemon.csv)
) {
    internal class ItemRef(val id: Int, val category: Int?, val cost: Int?, val flingPower: Int?, val flingEffect: Int?)

    companion object {
        /** Empty: works, but new item rows get Ankimon's "uncatalogued" numbers and names fall back to Showdown ids. */
        val NONE = AnkimonRefs(emptyMap(), emptyMap(), emptyMap())

        /**
         * Builds the refs from AnkiGo's downloaded raw files: [read] gets a file name of DataStore's raw folder
         * ("items.csv", "moves.json", "pokemon.csv") and returns its text, or null when it's missing. Junk is skipped.
         */
        fun fromFiles(read: (String) -> String?): AnkimonRefs {
            fun text(name: String): String? = try { read(name) } catch (e: Exception) { null }
            return AnkimonRefs(items(text("items.csv")), moveNums(text("moves.json")), forms(text("pokemon.csv")))
        }

        /** The rows of a PokeAPI csv by header name; PokeAPI cells here are never quoted, so a line with a quote or a wrong width is junk. */
        private fun csv(text: String?): List<Map<String, String>> {
            val lines = text?.lineSequence()?.map { it.trimEnd('\r') }?.filter { it.isNotBlank() }?.toList() ?: return emptyList()
            val head = lines.firstOrNull()?.split(',') ?: return emptyList()
            return lines.drop(1).filter { '"' !in it }.map { it.split(',') }.filter { it.size == head.size }.map { head.zip(it).toMap() }
        }

        private fun items(text: String?): Map<String, ItemRef> {
            val out = LinkedHashMap<String, ItemRef>()
            for (r in csv(text)) {
                val id = r["id"]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: continue
                val name = r["identifier"]?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                fun n(k: String) = r[k]?.trim()?.toIntOrNull()
                out.putIfAbsent(name, ItemRef(id, n("category_id"), n("cost"), n("fling_power"), n("fling_effect_id")))
            }
            return out
        }

        private fun moveNums(text: String?): Map<String, Int> {
            val out = HashMap<String, Int>()
            try {
                val o = JSONObject(text ?: return out)
                for (k in o.keys()) o.optJSONObject(k)?.optInt("num", 0)?.takeIf { it > 0 }?.let { out[k] = it }
            } catch (e: Exception) {
                out.clear()
            }
            return out
        }

        private fun forms(text: String?): Map<Int, Int> {
            val out = HashMap<Int, Int>()
            for (r in csv(text)) {
                val id = r["id"]?.trim()?.toIntOrNull()?.takeIf { it > 10000 } ?: continue
                r["species_id"]?.trim()?.toIntOrNull()?.let { out[id] = it }
            }
            return out
        }
    }
}

/** What [AnkimonExport.edit] did to the image: the report, the rows to check closely, and what a read-back must show. */
internal class Edited(val report: ExportReport, val rows: Map<String, RowNote>, val readBack: ReadBack)

object AnkimonExport {
    private const val KEPT_UNREADABLE = "Your kept Ankimon file can't be read. Import your Ankimon save again, then export."
    private const val NOT_SAFE = "Some data in your Ankimon save can't be written safely, so nothing was exported."
    private const val CHECK_FAILED = "The new Ankimon file didn't pass its checks, so nothing was exported."

    /**
     * Builds a complete Ankimon save (`ankimon.db` bytes). [kept] = the bytes of the last imported Ankimon file, or null:
     * then a fresh Ankimon save is built from Ankimon's schema. Pure; never changes [state] or [kept].
     * Throws [ExportException] (player-readable message) when it can't produce a file it trusts.
     *
     * The kept file is only read: every table and row goes into memory, AnkiGo's changes are applied there (a row AnkiGo
     * didn't change keeps its exact text), and a new file is written from it. The result is checked before it is returned.
     */
    fun build(data: GameData, state: GameState, kept: ByteArray?, refs: AnkimonRefs, now: Long): ExportResult {
        val image = try {
            if (kept == null) AnkimonSchema.fresh() else SqliteReader(kept).image()
        } catch (e: Exception) {
            throw ExportException(KEPT_UNREADABLE, e)
        }
        val edited = try {
            edit(data, state, image, refs, now, kept == null)
        } catch (e: ExportException) {
            throw e
        } catch (e: Exception) {
            throw ExportException(NOT_SAFE, e)
        }
        val bytes = try {
            SqliteWriter.write(image)
        } catch (e: Exception) {
            throw ExportException(NOT_SAFE, e)
        }
        val result = ExportResult(bytes, edited.report).also { it.rows = edited.rows; it.readBack = edited.readBack }
        val problems = verify(result)
        if (problems.isNotEmpty()) throw ExportException(CHECK_FAILED, IllegalStateException(problems.take(5).joinToString("; ")))
        return result
    }

    /** Applies the export to [image] (the kept file's tables, or the fresh schema) in memory. Throws [ExportException] when unsafe. */
    internal fun edit(data: GameData, state: GameState, image: SqliteImage, refs: AnkimonRefs, now: Long, fresh: Boolean): Edited =
        Exporter(data, state, refs, now, image, fresh).run()

    /** Re-checks written bytes (structure + Ankimon's own acceptance rules + content matches [report]); empty = OK. */
    fun verify(result: ExportResult): List<String> {
        val bytes = result.bytes
        if (bytes.size < 512) return listOf("The file is only ${bytes.size} bytes")
        return try {
            Verifier(result).run()
        } catch (e: Exception) {
            listOf("Checking the file failed (${e.javaClass.simpleName}: ${e.message})")
        }
    }

    // ---- formats shared by the writer and the verifier -------------------------------------------------------------

    private val GROWTH = listOf("slow", "medium", "fast", "medium-slow", "slow-then-very-fast", "fast-then-very-slow")
    private val STAT_KEYS = listOf("hp", "atk", "def", "spa", "spd", "spe")
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    /** Ankimon's growth_rate name for a PokeAPI growth rate id. */
    private fun growth(sp: Species) = GROWTH.getOrElse(sp.growthRate - 1) { "medium" }

    /** Ankimon gives a Pokémon without an ability "Run Away" (PokemonObject.__init__); a blank one would fail its own checks. */
    private fun abilityName(m: Mon) = m.ability.ifBlank { "Run Away" }

    private fun tierName(t: Tier) = when (t) { Tier.ULTRA -> "Ultra"; Tier.GMAX -> "Gmax"; else -> t.label }

    private fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DATE)

    private fun statMap(s: Stats): Map<String, Int> = STAT_KEYS.zip(s.toList()).toMap(LinkedHashMap())

    /** Ankimon's level-scaled stats (pokemon_obj.py calc_stat): the same formulas as the game's. */
    private fun statsOf(base: Stats, iv: Stats, ev: Stats, level: Int, nature: Nature) = Stats(
        StatCalc.hp(base.hp, iv.hp, ev.hp, level),
        StatCalc.other(base.atk, iv.atk, ev.atk, level, nature.mult(Stat.ATK)),
        StatCalc.other(base.def, iv.def, ev.def, level, nature.mult(Stat.DEF)),
        StatCalc.other(base.spa, iv.spa, ev.spa, level, nature.mult(Stat.SPA)),
        StatCalc.other(base.spd, iv.spd, ev.spd, level, nature.mult(Stat.SPD)),
        StatCalc.other(base.spe, iv.spe, ev.spe, level, nature.mult(Stat.SPE)),
    )

    /** Ankimon's Combat Power (business.py calculate_cp_from_dict / calculate_pokemon_go_cp), the same arithmetic in the same order. */
    internal fun cp(base: Stats, iv: Stats, ev: Stats, level: Int): Int {
        fun raw(b: Int, i: Int, e: Int) = maxOf(1, b + maxOf(0, i) + maxOf(0, e) / 4)
        val attack = (raw(base.atk, iv.atk, ev.atk) + raw(base.spa, iv.spa, ev.spa)) / 2.0
        val defense = (raw(base.def, iv.def, ev.def) + raw(base.spd, iv.spd, ev.spd)) / 2.0
        val stamina = raw(base.hp, iv.hp, ev.hp).toDouble()
        val cpm = 3.5 * (1 - exp(-maxOf(level, 1) / 85.0))
        return maxOf(10, floor(attack * sqrt(maxOf(defense, 1.0)) * sqrt(maxOf(stamina, 1.0)) * (cpm * cpm) / 10).toInt())
    }

    private fun text(v: Any?): String? = when (v) {
        is String -> v.trim().ifEmpty { null }
        is ByteArray -> String(v, Charsets.UTF_8).trim().ifEmpty { null }
        else -> null
    }

    /** The stored text exactly (no trimming), for editing. */
    private fun rawText(v: Any?): String? = when (v) { is String -> v; is ByteArray -> String(v, Charsets.UTF_8); else -> null }

    private fun SqlTable.maps(): List<Map<String, Any?>> =
        rows.map { r -> LinkedHashMap<String, Any?>().also { m -> columns.forEachIndexed { i, c -> m[c] = r.values[i] } } }

    private fun isMain(v: Any?) = (v as? Number)?.toDouble() == 1.0

    /** A TM row, decided as the import's reader does (AnkimonSaveReader.isTm): `is_tm` truthy, or type/category "tm". */
    private fun isTm(dataJson: String?): Boolean {
        val o = try { JSONObject(dataJson ?: return false) } catch (e: Exception) { return false }
        val flag = o.opt("is_tm")
        if (flag == true || (flag is Number && flag.toDouble() != 0.0) || (flag is String && flag.trim().lowercase() in setOf("true", "1", "yes", "y"))) return true
        return listOf("type", "category").any { (o.opt(it) as? String)?.trim().equals("tm", ignoreCase = true) }
    }

    /** Ankimon's own name normaliser for "is this nickname just the species name" (pyobj/evolution_window.py _normalize_nick). */
    private fun nickNorm(s: String) = s.lowercase().replace(" ", "").replace("-", "").replace("'", "").replace(".", "").replace(":", "")

    // ---- the work --------------------------------------------------------------------------------------------------

    private class Exporter(
        val data: GameData, val state: GameState, val refs: AnkimonRefs, val now: Long, val img: SqliteImage, val fresh: Boolean,
    ) {
        val formBase: (Int) -> Int? = { refs.forms[it] }
        val nameIndex = AnkimonMerge.nameIndex(data)
        val notes = LinkedHashMap<String, RowNote>()
        var added = 0
        var updated = 0
        var released = 0

        fun unsafe(why: String): Nothing = throw ExportException(NOT_SAFE, IllegalStateException(why))

        fun need(t: SqlTable, vararg cols: String): SqlTable {
            for (c in cols) if (t.col(c) < 0) unsafe("Table ${t.name} has no column $c")
            return t
        }

        /** A table of the image, added from Ankimon's schema when the kept file lacks it, with the columns the export uses. */
        fun table(name: String, vararg cols: String): SqlTable {
            AnkimonSchema.ensure(img, name)
            return need(img.table(name) ?: unsafe("No table $name"), *cols)
        }

        fun withRowidAlias(t: SqlTable, col: String): SqlTable {
            if (t.rowidAlias < 0 || t.rowidAlias != t.col(col)) unsafe("Table ${t.name}.$col isn't the row number")
            return t
        }

        fun keyValue(name: String): SqlTable =
            table(name, "key", "value").also { if (name != "user_data" && it.columns.size != 2) unsafe("Table $name must have exactly 2 columns") }

        val mons = need(img.table("captured_pokemon") ?: unsafe("No captured_pokemon table"), "individual_id", "is_main", "data")
        val team by lazy { withRowidAlias(table("team", "slot_position", "individual_id"), "slot_position") }
        val items by lazy { withRowidAlias(table("items", "id", "item_name", "quantity", "data", "category_id", "cost", "fling_power", "fling_effect_id"), "id") }
        val config by lazy { keyValue("config") }
        val userData by lazy { keyValue("user_data") }
        val history by lazy { withRowidAlias(table("pokemon_history", "id", "individual_id", "data"), "id") }

        /** Rows of the image as the import's reader sees them (what the import would make of this file). */
        fun view(): Map<String, List<Map<String, Any?>>> {
            val out = HashMap<String, List<Map<String, Any?>>>()
            for (n in listOf("captured_pokemon", "team", "items", "user_data", "config")) img.table(n)?.let { out[n] = it.maps() }
            return out
        }

        val rowsById = LinkedHashMap<String, SqlRow>().also { m ->
            for (r in mons.rows) text(mons[r, "individual_id"])?.let { m.putIfAbsent(it, r) }
        }
        val before = AnkimonSaveReader.parse(view())
        val monById = before.mons.associateBy { it.individualId }

        /** Every AnkiGo Pokémon's Ankimon id: its ankimonId, else its uid; a clash is settled with a derived, stable uuid. */
        val ids = IdentityHashMap<Mon, String>().also { out ->
            val used = HashSet<String>()
            for (mon in state.mons.sortedByDescending { it.ankimonId != null }) { // imported ones keep their id first
                var id = mon.ankimonId ?: mon.uid
                if (id.isBlank() || !used.add(id)) {
                    var n = 1
                    do { id = UUID.nameUUIDFromBytes("ankigo:${mon.uid}#${n++}".toByteArray(Charsets.UTF_8)).toString() } while (id in used || id in rowsById)
                    used += id
                }
                out[mon] = id
            }
        }

        fun run(): Edited {
            releaseRows()
            val baseRows = mons.rows.size
            var nextRowid = (mons.rows.maxOfOrNull { it.rowid } ?: 0L) + 1
            for (mon in state.mons) {
                val id = ids.getValue(mon)
                val sp = data.species[mon.species]
                val row = rowsById[id]
                if (row == null) {
                    if (sp == null) unsafe("Pokémon ${mon.name} has species #${mon.species}, which isn't in the game data")
                    val json = newJson(mon, sp, id)
                    val values = mons.columns.map { c ->
                        when (c.lowercase()) { "individual_id" -> id; "is_main" -> 0L; "data" -> json; else -> null }
                    }.toMutableList<Any?>()
                    rowsById[id] = SqlRow(nextRowid++, values).also { mons.rows += it }
                    notes[id] = RowNote(true, PyJson.members(json)!!.map { it.key }.toSet())
                    added++
                } else if (sp != null) {
                    refresh(row, id, mon, sp)
                }
            }
            teamAndMain()
            itemsAndTms()
            cash()
            xpShare()
            pokedex("pokedex_seen", before.seen, state.seen)
            pokedex("pokedex_caught", before.caught, state.caught)
            if (fresh) {
                keyValue("metadata").insert(mapOf("key" to "base_stats_normalized", "value" to "true"))
                trainerName()
            }
            tidyTeam()
            return finish(baseRows)
        }

        // ---- released on the phone -------------------------------------------------------------------------------

        /** Rows of Pokémon released on the phone leave the PC save too: history entry, no team slot, no Exp. Share. */
        fun releaseRows() {
            val ours = ids.values.toHashSet()
            for (id in state.releasedAnkimon) {
                val row = rowsById[id] ?: continue
                if (id in ours) continue // AnkiGo owns a Pokémon with this id: never delete it
                val am = monById[id]
                val raw = mons[row, "individual_id"] as? String ?: id
                if (history.rows.none { text(history[it, "individual_id"]) == id }) addHistory(raw, am)
                mons.rows.remove(row)
                rowsById.remove(id)
                img.table("team")?.let { t -> t.rows.removeAll { text(t[it, "individual_id"]) == id } }
                img.table("config")?.let { c ->
                    c.rows.removeAll { text(c[it, "key"]) == "trainer.xp_share" && text(c[it, "value"])?.trim('"') == id }
                }
                released++
            }
        }

        /** Ankimon's release record (gui_classes/pokemon_details.py PokemonFree); the id counts up like an AUTOINCREMENT would. */
        fun addHistory(raw: String, am: AnkimonMon?) {
            AnkimonSchema.ensure(img, "sqlite_sequence")
            val seq = img.table("sqlite_sequence")!!
            val last = seq.rows.firstOrNull { text(seq[it, "name"]) == "pokemon_history" }
            val next = maxOf(history.rows.maxOfOrNull { it.rowid } ?: 0L, (last?.let { seq[it, "seq"] } as? Long) ?: 0L) + 1
            history.insert(mapOf("id" to next, "individual_id" to raw, "data" to PyJson.dumps(linkedMapOf(
                "id" to am?.id, "name" to am?.name, "shiny" to (am?.shiny ?: false), "pokemon_defeated" to (am?.defeated ?: 0),
                "individual_id" to raw, "released_date" to date(now),
            ))))
            if (last != null) seq[last, "seq"] = next else seq.insert(mapOf("name" to "pokemon_history", "seq" to next))
        }

        // ---- one Pokémon -----------------------------------------------------------------------------------------

        /** A caught-on-the-phone Pokémon as Ankimon stores it (the order of PokemonObject.to_dict). */
        fun newJson(mon: Mon, sp: Species, id: String): String {
            val st = statsOf(sp.base, mon.ivs, mon.evs, mon.level, mon.nature)
            return PyJson.dumps(linkedMapOf(
                "name" to sp.id, "nickname" to (mon.nickname ?: ""), "level" to mon.level, "gender" to mon.gender.toString(), "id" to sp.num,
                "ability" to abilityName(mon), "type" to sp.types.map { it.label }, "base_stats" to statMap(sp.base), "stats" to statMap(st),
                "cp" to cp(sp.base, mon.ivs, mon.evs, mon.level), "nature" to mon.nature.label, "ev" to statMap(mon.evs), "iv" to statMap(mon.ivs),
                "attacks" to mon.moves.take(4), "base_experience" to sp.baseExp, "growth_rate" to growth(sp),
                "everstone" to (mon.heldItem == "everstone"), "evolution_rejected" to false, "shiny" to mon.shiny,
                "captured_date" to mon.caughtAt.takeIf { it > 0 }?.let { date(it) }, "individual_id" to id, "mega" to false, "special_form" to null,
                "xp" to mon.xp, "hp" to st.hp, "friendship" to mon.friendship, "pokemon_defeated" to mon.defeats, "tier" to tierName(mon.tier),
                "is_favorite" to false, "current_hp" to st.hp, "held_item" to mon.heldItem,
            ))
        }

        /**
         * A row AnkiGo owns: compare AnkiGo's Pokémon with what the import made of the row (same derivations) and write only
         * what differs. Nothing differs -> the row's text isn't touched at all.
         */
        fun refresh(row: SqlRow, id: String, mon: Mon, sp: Species) {
            val am = monById[id] ?: return
            val fileSp = AnkimonMerge.speciesFor(data, am, formBase, nameIndex).first ?: return
            val file = AnkimonMerge.buildMon(data, am, fileSp, "", now, AnkimonMerge.attackMoves(data, am))
            val old = rawText(mons[row, "data"]) ?: return
            val changes = changes(old, am, mon, sp, file, fileSp)
            if (changes.isEmpty()) return
            val edited = try {
                PyJson.edit(old, changes.mapValues { PyJson.dumps(it.value) })
            } catch (e: IllegalArgumentException) {
                unsafe("Pokémon $id: its stored text can't be edited safely")
            }
            mons[row, "data"] = edited
            notes[id] = RowNote(false, changes.keys)
            updated++
        }

        fun changes(old: String, am: AnkimonMon, mon: Mon, sp: Species, file: Mon, fileSp: Species): LinkedHashMap<String, Any?> {
            val c = LinkedHashMap<String, Any?>()
            val evolved = mon.species != file.species
            val leveled = mon.level != file.level
            if (evolved) {
                c["id"] = sp.num; c["name"] = sp.id; c["type"] = sp.types.map { it.label }; c["base_stats"] = statMap(sp.base)
                c["growth_rate"] = growth(sp); c["base_experience"] = sp.baseExp; c["evolution_rejected"] = false
            }
            if (leveled) c["level"] = mon.level
            if (evolved || leveled || mon.xp != file.xp) c["xp"] = mon.xp // another level or growth rate: never trust the old number
            if (mon.ivs != file.ivs) c["iv"] = statMap(mon.ivs)
            if (mon.evs != file.evs) c["ev"] = statMap(mon.evs)
            if (mon.nature != file.nature) c["nature"] = mon.nature.label
            if (mon.gender != file.gender) c["gender"] = mon.gender.toString()
            if (mon.shiny != file.shiny) c["shiny"] = mon.shiny
            if (mon.ability != file.ability) c["ability"] = abilityName(mon)
            if (mon.moves != file.moves) c["attacks"] = mon.moves.take(4)
            // The import cuts friendship at 255, Ankimon doesn't: add AnkiGo's change to what the file really had.
            if (mon.friendship != file.friendship) c["friendship"] = maxOf(0, (am.friendship ?: file.friendship) + mon.friendship - file.friendship)
            if (mon.heldItem != file.heldItem) { c["held_item"] = mon.heldItem; c["everstone"] = mon.heldItem == "everstone" }
            if (mon.defeats != file.defeats) c["pokemon_defeated"] = mon.defeats
            if (mon.tier != file.tier) c["tier"] = tierName(mon.tier)
            when {
                mon.nickname != file.nickname -> c["nickname"] = mon.nickname ?: ""
                // Evolved with a default nickname (the old species name): it follows the species, as in Ankimon.
                evolved && am.nickname != null &&
                    nickNorm(am.nickname) in listOf(nickNorm(fileSp.name), nickNorm(fileSp.id), nickNorm(am.name ?: "")) -> c["nickname"] = ""
            }
            if (evolved || leveled || mon.ivs != file.ivs || mon.evs != file.evs || mon.nature != file.nature) {
                val base = if (evolved) sp.base else storedBase(old) ?: sp.base.also { c["base_stats"] = statMap(it) }
                val level = if (leveled) mon.level else maxOf(1, am.level ?: 1) // a level above 100 stays as the file has it
                val st = statsOf(base, mon.ivs, mon.evs, level, mon.nature)
                c["stats"] = statMap(st); c["cp"] = cp(base, mon.ivs, mon.evs, level); c["hp"] = st.hp; c["current_hp"] = st.hp
            }
            return c
        }

        /** The row's own base_stats when it has all six as positive whole numbers. */
        fun storedBase(json: String): Stats? = try {
            val o = JSONObject(json).optJSONObject("base_stats")
            val v = STAT_KEYS.map { (o?.opt(it) as? Number)?.toInt() ?: 0 }
            if (v.all { it > 0 }) Stats.of(v) else null
        } catch (e: Exception) {
            null
        }

        // ---- team and main ---------------------------------------------------------------------------------------

        /** Same team as the import would take from the file -> leave team and main alone; else AnkiGo's team, its main first. */
        fun teamAndMain() {
            val ours = ids.values.toHashSet()
            val ankiTeam = state.team.mapNotNull { uid -> state.mon(uid)?.let { ids[it] } }.distinct().take(6)
            val main = before.mons.firstOrNull { it.isMain }?.individualId
            val fileTeam = (listOfNotNull(main) + before.team.filter { it != main }).distinct().filter { it in ours }.take(6)
            if (ankiTeam.isNotEmpty() && ankiTeam != fileTeam) {
                team.rows.clear()
                ankiTeam.forEachIndexed { i, id -> team.insert(mapOf("slot_position" to (i + 1).toLong(), "individual_id" to id)) }
                val main = rowsById[ankiTeam[0]]
                for (r in mons.rows) setMain(r, r === main)
            }
            // Exactly one main, always: when none is left (released, removed) AnkiGo's main takes over.
            val mains = mons.rows.filter { isMain(mons[it, "is_main"]) }
            if (mons.rows.isNotEmpty() && mains.size != 1) {
                val keep = mains.firstOrNull() ?: ankiTeam.firstOrNull()?.let { rowsById[it] } ?: mons.rows.first()
                for (r in mons.rows) setMain(r, r === keep)
            }
        }

        fun setMain(r: SqlRow, on: Boolean) {
            val want = if (on) 1L else 0L
            if (mons[r, "is_main"] != want) mons[r, "is_main"] = want
        }

        /** Team slots 1..n, each pointing at a Pokémon that is in the file (Ankimon survives a dangling slot, but we don't write one). */
        fun tidyTeam() {
            if (img.table("team") == null) return
            val t = team
            val sorted = t.rows.sortedBy { it.rowid }
            val keep = HashSet<String>()
            val raw = ArrayList<Any?>()
            for (r in sorted) { val id = text(t[r, "individual_id"]) ?: continue; if (id in rowsById && keep.add(id)) raw += t[r, "individual_id"] }
            if (sorted.size == raw.size && sorted.withIndex().all { (i, r) -> r.rowid == i + 1L && t[r, "individual_id"] == raw[i] }) return
            t.rows.clear()
            raw.forEachIndexed { i, v -> t.insert(mapOf("slot_position" to (i + 1).toLong(), "individual_id" to v)) }
        }

        // ---- items, TMs, money, Pokédex --------------------------------------------------------------------------

        fun qty(r: SqlRow): Long = (items[r, "quantity"] as? Number)?.toLong() ?: 0L

        /** The id for a row the file has no catalogue number for: one below the smallest id, or -1 (Ankimon's "uncatalogued"). */
        fun uncatalogued(): Long = items.rows.minOfOrNull { it.rowid }.let { if (it == null || it >= 0) -1L else it - 1 }

        fun insertItem(name: String, quantity: Long, wantId: Long?, category: Int?, cost: Int?, flingPower: Int?, flingEffect: Int?, tm: Boolean) {
            val id = wantId?.takeIf { n -> items.rows.none { it.rowid == n } } ?: uncatalogued()
            items.insert(mapOf(
                "id" to id, "item_name" to name, "quantity" to quantity, "data" to if (tm) "{\"type\": \"TM\"}" else null,
                "category_id" to category?.toLong(), "cost" to cost?.toLong(),
                "fling_power" to flingPower?.toLong(), "fling_effect_id" to flingEffect?.toLong(),
            ))
        }

        fun itemsAndTms() {
            if (img.table("items") == null && state.bag.values.none { it > 0 } && state.tms.isEmpty()) return
            val groups = LinkedHashMap<String, MutableList<SqlRow>>() // AnkiGo item id -> rows of the file that mean it
            val tmRows = HashMap<String, SqlRow>()                    // move id -> its TM row
            val names = HashSet<String>()
            for (r in items.rows) {
                val name = text(items[r, "item_name"]) ?: continue
                names += name
                if (isTm(text(items[r, "data"]))) AnkimonMerge.tmMove(data, name)?.let { tmRows.putIfAbsent(it, r) }
                else AnkimonMerge.itemKey(name)?.let { groups.getOrPut(it) { mutableListOf() } += r }
            }
            // Rows of an item AnkiGo knows: AnkiGo's count goes on the row the import took it from (the largest), so a
            // re-import changes nothing; other rows of the same item may not be larger (the import would raise AnkiGo's count).
            for ((key, rs) in groups) {
                val want = state.count(key).coerceAtLeast(0).toLong()
                val top = rs.maxByOrNull { qty(it) }!!
                for (r in rs) {
                    val target = if (r === top) want else minOf(qty(r), want)
                    if (want == 0L) items.rows.remove(r) else if (target != qty(r)) items[r, "quantity"] = target
                }
            }
            for ((key, n) in state.bag.toSortedMap()) {
                if (n <= 0 || key in groups || Items.def(key) == null || key in names) continue
                val ref = refs.items[key]
                insertItem(key, n.toLong(), ref?.id?.toLong(), ref?.category, ref?.cost, ref?.flingPower, ref?.flingEffect, false)
            }
            for (move in state.tms.sorted()) {
                val row = tmRows[move]
                if (row != null) { if (qty(row) <= 0) items[row, "quantity"] = 1L; continue }
                if (move !in data.moves || move in names) continue
                insertItem(move, 1L, refs.moveNums[move]?.let { 20000L + it }, 37, 1000, null, null, true)
            }
        }

        fun cash() {
            val want = state.cash.coerceAtLeast(0)
            val row = config.rows.firstOrNull { text(config[it, "key"]) == "trainer.cash" }
            if (row == null) config.insert(mapOf("key" to "trainer.cash", "value" to want.toString()))
            else if (before.cash != want) config[row, "value"] = want.toString()
        }

        /**
         * Ankimon's classic Exp. Share holder (config `trainer.xp_share`, the holder's individual_id as plain text) follows
         * the phone's. Equal to what the import read -> untouched. Turned off on the phone -> the row goes (as on release),
         * but only when the file's holder is a Pokémon AnkiGo has. Ankimon's "oras" mode (no single holder) is left alone.
         */
        fun xpShare() {
            val want = state.settings.xpShareUid?.let { state.mon(it) }?.let { ids[it] }
            if (want == before.xpShare) return
            val mode = config.rows.firstOrNull { text(config[it, "key"]) == "trainer.xp_share_mode" }
            if (mode != null && text(config[mode, "value"])?.trim('"')?.trim()?.lowercase() != "classic") return
            val row = config.rows.firstOrNull { text(config[it, "key"]) == "trainer.xp_share" }
            if (want == null) {
                if (row != null && before.xpShare in ids.values.toHashSet()) config.rows.remove(row) // toHashSet: an IdentityHashMap compares its values by identity
                return
            }
            val value = if (PyJson.isValue(want)) PyJson.quote(want) else want
            if (row == null) config.insert(mapOf("key" to "trainer.xp_share", "value" to value)) else config[row, "value"] = value
        }

        /** Species the file's list has no entry for (a form entry counts as its species) are appended, sorted; else the text stays. */
        fun pokedex(key: String, fileIds: Set<Int>, ours: Set<Int>) {
            val have = fileIds.mapNotNullTo(HashSet()) { AnkimonMerge.dexId(data, formBase, it) }
            val missing = ours.filter { it in data.species && it !in have }.sorted()
            if (missing.isEmpty()) return
            val row = userData.rows.firstOrNull { text(userData[it, "key"]) == key }
            if (row == null) {
                userData.insert(mapOf("key" to key, "value" to PyJson.dumps(missing)))
            } else {
                val old = PyJson.elements(text(userData[row, "value"]) ?: "") ?: emptyList()
                userData[row, "value"] = "[" + (old + missing.map { it.toString() }).joinToString(", ") + "]"
            }
        }

        /** A plain string, as Ankimon writes config strings - unless json.loads would read it as something else (e.g. "123"). */
        fun trainerName() {
            val name = state.settings.trainerName.trim()
            if (name.isEmpty()) return
            config.insert(mapOf("key" to "trainer.name", "value" to if (PyJson.isValue(name)) PyJson.quote(name) else name))
        }

        // ---- the result ------------------------------------------------------------------------------------------

        fun finish(baseRows: Int): Edited {
            val out = AnkimonSaveReader.parse(view()) // the file as the import will read it
            val report = ExportReport(
                fresh = fresh, mons = mons.rows.size, added = added, updated = updated, untouched = baseRows - updated, released = released,
                team = out.team.size, cash = out.cash ?: 0, caught = out.caught.size, items = out.items.size + out.tms.size,
            )
            return Edited(report, notes, ReadBack(out.copy(mons = emptyList(), problems = emptyList()), out.mons.size, out.mons.sumOf { it.hashCode() }))
        }
    }

    // ---- the check -------------------------------------------------------------------------------------------------

    private class Verifier(val result: ExportResult) {
        val problems = mutableListOf<String>()

        fun run(): List<String> {
            val bytes = result.bytes
            problems += SqliteCheck.check(bytes)
            if (problems.isNotEmpty()) return problems // nothing below makes sense on a damaged file
            val db = try { SqliteReader(bytes) } catch (e: SqliteFormatException) { return listOf("The file can't be read back: ${e.message}") }
            val names = db.tableNames().map { it.lowercase() }.toSet()
            if ("captured_pokemon" !in names) return listOf("The file has no captured_pokemon table")
            val cols = db.columns("captured_pokemon").map { it.lowercase() }
            for (c in listOf("individual_id", "is_main", "data", "name", "pokedex_id", "shiny", "level")) {
                if (c !in cols) problems += "captured_pokemon has no column $c"
            }
            for (t in listOf("config", "metadata")) if (t in names && db.columns(t).size != 2) problems += "$t must have exactly 2 columns"
            if (problems.isNotEmpty()) return problems

            val rows = db.rows("captured_pokemon", Int.MAX_VALUE)
            val ids = HashSet<String>()
            for (r in rows) text(r["individual_id"])?.let { ids += it }
            if (rows.isNotEmpty() && rows.count { isMain(it["is_main"]) } != 1) problems += "There must be exactly one main Pokémon"
            checkRows(rows)
            if ("team" in names) checkTeam(db.rows("team", 1_000), ids)
            checkReadBack(db)
            return problems
        }

        fun checkRows(rows: List<Map<String, Any?>>) {
            val notes = result.rows
            for (r in rows) {
                val id = text(r["individual_id"]) ?: continue
                val note = if (notes == null) RowNote(false, emptySet()) else notes[id] ?: continue
                val json = text(r["data"])?.let { try { JSONObject(it) } catch (e: Exception) { null } }
                val who = "Pokémon ${id.take(8)}"
                if (json == null) { problems += "$who: its data isn't a JSON object"; continue }
                if (note.isNew) problems += REQUIRED.filter { !json.has(it) }.map { "$who lacks $it" }
                for (k in if (note.isNew) json.keys().asSequence().toSet() else note.keys) {
                    val v = json.opt(k)
                    if (v == null || v == JSONObject.NULL) { if (k !in NULLABLE) problems += "$who: $k is empty" }
                    else if (!sane(k, v)) problems += "$who: $k has a wrong value"
                }
                if (note.isNew && json.optString("individual_id") != id) problems += "$who: individual_id differs from the row"
            }
        }

        fun checkTeam(rows: List<Map<String, Any?>>, ids: Set<String>) {
            val slots = rows.mapNotNull { (it["slot_position"] as? Number)?.toLong() }.sorted()
            if (slots != (1..rows.size).map { it.toLong() }) problems += "Team slots must be 1..${rows.size}"
            val teamIds = rows.mapNotNull { text(it["individual_id"]) }
            if (teamIds.size != rows.size || teamIds.any { it !in ids }) problems += "A team slot points at a Pokémon that isn't in the file"
            if (teamIds.toSet().size != teamIds.size) problems += "A Pokémon is in the team twice"
        }

        /** The file read back through the import's reader must hold what the export meant to write. */
        fun checkReadBack(db: SqliteReader) {
            val got = AnkimonSaveReader.read(db)
            val want = result.readBack
            val r = result.report
            if (want == null) {
                if (got.mons.size != r.mons) problems += "Pokémon read back: ${got.mons.size}, expected ${r.mons}"
                if (got.team.size != r.team) problems += "Team read back: ${got.team.size}, expected ${r.team}"
                if ((got.cash ?: 0) != r.cash) problems += "Cash read back: ${got.cash}, expected ${r.cash}"
                if (got.caught.size != r.caught) problems += "Pokédex read back: ${got.caught.size}, expected ${r.caught}"
                if (got.items.size + got.tms.size != r.items) problems += "Items read back: ${got.items.size + got.tms.size}, expected ${r.items}"
                return
            }
            if (got.mons.size != want.mons || got.mons.sumOf { it.hashCode() } != want.monsHash) problems += "The Pokémon don't read back as written"
            if (got.team != want.rest.team) problems += "The team doesn't read back as written"
            if (got.cash != want.rest.cash) problems += "The cash doesn't read back as written"
            if (got.xpShare != want.rest.xpShare) problems += "The Exp. Share holder doesn't read back as written"
            if (got.seen != want.rest.seen || got.caught != want.rest.caught) problems += "The Pokédex doesn't read back as written"
            if (got.items != want.rest.items || got.tms.sorted() != want.rest.tms.sorted()) problems += "The items don't read back as written"
        }
    }

    private val REQUIRED = listOf(
        "type", "name", "id", "shiny", "level", "ability", "gender", "growth_rate", "captured_date", "tier", "individual_id",
        "base_stats", "stats", "cp", "nature", "ev", "iv", "attacks", "xp", "hp", "current_hp",
    )
    private val NULLABLE = setOf("captured_date", "held_item", "special_form")

    private fun whole(v: Any?) = v is Int || v is Long || v is java.math.BigInteger
    private fun int(v: Any?, range: IntRange) = whole(v) && (v as Number).toLong() in range.first.toLong()..range.last.toLong()
    private fun str(v: Any?) = v is String && v.isNotBlank()
    private fun statObj(v: Any?) = v is JSONObject && STAT_KEYS.all { int(v.opt(it), 0..100_000) }

    /** Whether [v] (not null) is a sane value for member [k] of a Pokémon: the shapes Ankimon's PokemonObject needs. Unknown members pass. */
    private fun sane(k: String, v: Any?): Boolean = when (k) {
        "type" -> v is JSONArray && v.length() in 1..2 && (0 until v.length()).all { str(v.opt(it)) }
        "name", "ability", "growth_rate", "tier", "nature", "individual_id" -> str(v)
        "id" -> int(v, 1..Int.MAX_VALUE)
        "level" -> int(v, 1..100)
        "xp", "hp", "current_hp", "pokemon_defeated", "base_experience" -> int(v, 0..Int.MAX_VALUE)
        "cp" -> int(v, 10..Int.MAX_VALUE)
        "friendship" -> int(v, 0..Int.MAX_VALUE)
        "gender" -> v == "M" || v == "F" || v == "N"
        "shiny", "everstone", "evolution_rejected" -> v is Boolean
        "base_stats", "stats", "iv", "ev" -> statObj(v)
        "attacks" -> v is JSONArray && v.length() <= 4 && (0 until v.length()).all { v.opt(it) is String }
        "nickname", "held_item" -> v is String
        "captured_date" -> v is String && v.length == 19
        else -> true
    }
}
