package com.husarp.ankigo.game.ankimon

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

// TICKET T19 owns this file.

/** One Pokémon of an Ankimon save, as plain data. Every field but [individualId] may be missing in the file. */
data class AnkimonMon(
    val individualId: String,
    val isMain: Boolean,
    val id: Int?,                    // species number (forms are 10001+)
    val name: String?,
    val nickname: String?,
    val level: Int?,
    val xp: Int?,
    val iv: Map<String, Int>,        // keys hp atk def spa spd spe (only those present)
    val ev: Map<String, Int>,
    val nature: String?,
    val gender: String?,
    val ability: String?,
    val attacks: List<String>,
    val shiny: Boolean,
    val heldItem: String?,
    val everstone: Boolean,
    val friendship: Int?,
    val capturedDate: String?,       // "yyyy-MM-dd HH:mm:ss"
    val defeated: Int?,
    val tier: String?,
)

/** What AnkiGo takes from an Ankimon save. Nothing else of the file is kept (live saves can hold tokens in `config`). */
data class AnkimonSave(
    val mons: List<AnkimonMon>,
    val team: List<String>,          // individual ids, in slot order
    val items: Map<String, Int>,     // item name as Ankimon wrote it -> quantity (> 0)
    val tms: List<String>,           // TM names (the move), quantity > 0
    val cash: Int?,
    val seen: Set<Int>,
    val caught: Set<Int>,
    val problems: List<String>,      // rows that were skipped while reading, one short line each
    val xpShare: String? = null,     // classic Exp. Share holder (config trainer.xp_share); null when off or Ankimon's "oras" mode
)

/** The file is a valid database but not an Ankimon save. */
class NotAnkimonException(message: String) : Exception(message)

/** Turns the rows of an Ankimon `.db` into an [AnkimonSave]. Loose on purpose: Ankimon's save has no version number. */
object AnkimonSaveReader {
    const val MAX_MONS = 20_000
    const val MAX_ITEMS = 2_000
    private const val MAX_ID_LENGTH = 200
    private const val MAX_DEX_ENTRIES = 5_000
    private const val NOT_ANKIMON = "This is not an Ankimon save file"
    private val STAT_KEYS = listOf("hp", "atk", "def", "spa", "spd", "spe")
    private val CONFIG_KEYS = setOf("trainer.cash", "trainer.xp_share", "trainer.xp_share_mode")

    /**
     * From plain rows per table (the mapping layer; tests feed maps directly). Needs table `captured_pokemon`.
     * Missing other tables are fine and read as empty.
     */
    fun parse(tables: Map<String, List<Map<String, Any?>>>): AnkimonSave {
        val monRows = tables["captured_pokemon"] ?: throw NotAnkimonException(NOT_ANKIMON)
        val problems = mutableListOf<String>()

        val mons = LinkedHashMap<String, AnkimonMon>()
        for (row in monRows) {
            if (mons.size >= MAX_MONS) { problems += "More than $MAX_MONS Pokémon: only the first $MAX_MONS were read"; break }
            val mon = parseMon(row, problems) ?: continue
            mons.putIfAbsent(mon.individualId, mon) // duplicate ids: the first wins
        }

        val team = tables["team"].orEmpty()
            .mapNotNull { r -> text(r["individual_id"])?.let { (int(r["slot_position"]) ?: Int.MAX_VALUE) to it } }
            .sortedBy { it.first } // stable: equal slots keep file order
            .map { it.second }.distinct()

        val items = LinkedHashMap<String, Int>()
        val tms = mutableListOf<String>()
        for (r in tables["items"].orEmpty().take(MAX_ITEMS)) {
            val name = text(r["item_name"]) ?: continue
            val qty = int(r["quantity"]) ?: continue
            if (qty <= 0) continue
            if (isTm(text(r["data"]))) { if (name !in tms) tms += name } else items.putIfAbsent(name, qty)
        }

        var seen = emptySet<Int>()
        var caught = emptySet<Int>()
        for (r in tables["user_data"].orEmpty()) {
            when (text(r["key"])) {
                "pokedex_seen" -> seen = speciesSet(text(r["value"]))
                "pokedex_caught" -> caught = speciesSet(text(r["value"]))
            }
        }

        // Only the trainer's cash and the Exp. Share holder; every other config value (tokens, names, settings) is never looked at.
        val config = tables["config"].orEmpty()
        fun cfg(key: String) = config.firstOrNull { text(it["key"]) == key }?.get("value")
        val cash = cfg("trainer.cash")?.let { cashValue(it) }
        val xpShare = xpShareValue(cfg("trainer.xp_share"), cfg("trainer.xp_share_mode"))

        return AnkimonSave(mons.values.toList(), team, items, tms, cash, seen, caught, problems, xpShare)
    }

    /**
     * Reads only captured_pokemon, team, items, user_data and config from [db] (caps: 20 000 Pokémon, 2 000 items).
     * Throws [NotAnkimonException] when the file has no `captured_pokemon` table with a `data` column,
     * [SqliteFormatException] when the file is damaged.
     */
    fun read(db: SqliteReader): AnkimonSave {
        val names = db.tableNames().map { it.lowercase() }.toSet()
        if ("captured_pokemon" !in names || "data" !in db.columns("captured_pokemon").map { it.lowercase() }) {
            throw NotAnkimonException(NOT_ANKIMON)
        }
        val tables = HashMap<String, List<Map<String, Any?>>>()
        tables["captured_pokemon"] = db.rows("captured_pokemon", MAX_MONS + 1)
        if ("team" in names) tables["team"] = db.rows("team", 1_000)
        if ("items" in names) tables["items"] = db.rows("items", MAX_ITEMS)
        // Other keys are dropped right here so they are never passed around.
        if ("user_data" in names) {
            tables["user_data"] = db.rows("user_data", 10_000).filter { text(it["key"]) in setOf("pokedex_seen", "pokedex_caught") }
        }
        if ("config" in names) {
            tables["config"] = db.rows("config", 10_000).filter { text(it["key"]) in CONFIG_KEYS }
        }
        return parse(tables)
    }

    // ---- one Pokémon --------------------------------------------------------------------------------------------

    private fun parseMon(row: Map<String, Any?>, problems: MutableList<String>): AnkimonMon? {
        val rowId = text(row["individual_id"])
        val json = text(row["data"])?.let { obj(it) }
        if (json == null) {
            problems += if (rowId != null) "Pokémon ${rowId.take(8)}: unreadable data, skipped" else "A Pokémon with unreadable data was skipped"
            return null
        }
        val id = rowId ?: text(json.opt("individual_id"))
        if (id == null) { problems += "A Pokémon without an id was skipped"; return null }
        if (id.length > MAX_ID_LENGTH) { problems += "A Pokémon with an oversized id was skipped"; return null }
        return AnkimonMon(
            individualId = id,
            isMain = bool(row["is_main"]),
            id = int(json.opt("id")),
            name = text(json.opt("name")),
            nickname = text(json.opt("nickname")),
            level = int(json.opt("level")),
            xp = int(json.opt("xp")),
            iv = stats(json.opt("iv")),
            ev = stats(json.opt("ev")),
            nature = text(json.opt("nature")),
            gender = text(json.opt("gender")),
            ability = text(json.opt("ability")),
            attacks = (json.opt("attacks") as? JSONArray)?.let { a -> (0 until minOf(a.length(), 20)).mapNotNull { text(a.opt(it)) } }.orEmpty(),
            shiny = bool(json.opt("shiny")),
            heldItem = text(json.opt("held_item")),
            everstone = bool(json.opt("everstone")),
            friendship = int(json.opt("friendship")),
            capturedDate = text(json.opt("captured_date")),
            defeated = int(json.opt("pokemon_defeated")),
            tier = text(json.opt("tier")),
        )
    }

    private fun stats(v: Any?): Map<String, Int> {
        val o = v as? JSONObject ?: return emptyMap()
        val out = LinkedHashMap<String, Int>()
        for (k in STAT_KEYS) int(o.opt(k))?.let { out[k] = it }
        return out
    }

    // ---- other tables -------------------------------------------------------------------------------------------

    private fun isTm(dataJson: String?): Boolean {
        val o = dataJson?.let { obj(it) } ?: return false
        if (bool(o.opt("is_tm"))) return true
        return listOf("type", "category").any { text(o.opt(it))?.equals("tm", ignoreCase = true) == true }
    }

    private fun speciesSet(valueJson: String?): Set<Int> {
        val a = valueJson?.let { try { JSONArray(it) } catch (e: Exception) { null } } ?: return emptySet()
        val out = LinkedHashSet<Int>()
        for (i in 0 until minOf(a.length(), MAX_DEX_ENTRIES)) {
            val n = int(a.opt(i)) ?: continue
            if (n > 0) out += n
        }
        return out
    }

    private fun cashValue(v: Any?): Int? {
        // Stored as JSON: a number ("12400") or a quoted string ("\"12400\"").
        val n = when (v) {
            is String -> v.trim().trim('"').trim().let { int(it) }
            else -> int(v)
        } ?: return null
        return n.coerceAtLeast(0)
    }

    /**
     * Ankimon stores the holder's individual_id as plain text (Settings.set -> str(value)); "turned off" is the text
     * "None" (str(None)) or no row. Only Ankimon's "classic" mode (the default) has one holder, as AnkiGo does.
     */
    internal fun xpShareValue(value: Any?, mode: Any?): String? {
        val m = text(mode)?.trim('"')?.trim()?.lowercase()
        if (m != null && m != "classic") return null
        val id = text(value)?.trim('"')?.trim() ?: return null
        if (id.equals("none", ignoreCase = true) || id == "null" || id.length > MAX_ID_LENGTH) return null
        return id
    }

    // ---- loose value coercion -----------------------------------------------------------------------------------

    private fun obj(s: String): JSONObject? = try { JSONObject(s) } catch (e: Exception) { null }

    /** A trimmed, non-blank string; nothing else (JSON null, numbers, objects -> null). */
    private fun text(v: Any?): String? = when (v) {
        is String -> v.trim().ifEmpty { null }
        is ByteArray -> String(v, Charsets.UTF_8).trim().ifEmpty { null }
        else -> null
    }

    /** Numbers, floats and numeric strings -> Int (rounded, clamped to the Int range); booleans and the rest -> null. */
    private fun int(v: Any?): Int? {
        val d: Double = when (v) {
            is Int -> return v
            is Long -> return v.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull() ?: return null
            else -> return null
        }
        if (!d.isFinite()) return null
        return d.roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    private fun bool(v: Any?): Boolean = when (v) {
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        is String -> v.trim().lowercase() in setOf("true", "1", "yes", "y")
        else -> false
    }
}
