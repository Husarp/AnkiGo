package com.husarp.ankigo.game.save

import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.BattleSide
import com.husarp.ankigo.game.DayLog
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Outcome
import com.husarp.ankigo.game.Settings
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.UnsupportedMon
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Thrown by [SaveCodec.decode] for anything that isn't a readable save. [newerSchema]: the file is fine but from a newer app. */
class SaveFormatException(message: String, val newerSchema: Boolean = false, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/**
 * GameState <-> JSON. Every field of Model.kt's GameState/Mon/Battle/Settings round-trips exactly.
 * The output is deterministic (sets and maps are written sorted), so encode -> decode -> encode gives identical text.
 * Decoding is tolerant: unknown fields are ignored and missing optional fields get the Model defaults.
 */
object SaveCodec {
    fun encode(state: GameState): String {
        val w = W()
        w.obj {
            kv("schema", state.schema) // first, so a reader can check it before anything else
            kv("seq", state.seq); kv("savedAt", state.savedAt); kv("createdAt", state.createdAt)
            key("mons"); arr { state.mons.forEach { writeMon(this, it) } }
            key("unsupported"); arr { state.unsupported.forEach { writeUnsupported(this, it) } }
            list("team", state.team)
            key("bag"); obj { for ((k, v) in state.bag.toSortedMap()) kv(k, v) }
            list("tms", state.tms.sorted())
            kv("cash", state.cash); kv("trainerLevel", state.trainerLevel); kv("trainerXp", state.trainerXp)
            list("seen", state.seen.sorted()); list("caught", state.caught.sorted())
            key("battle"); state.battle.let { if (it == null) value(null) else writeBattle(this, it) }
            kv("nextSpecies", state.nextSpecies); kv("nextTier", state.nextTier.name); kv("nextShiny", state.nextShiny); kv("nextSeed", state.nextSeed)
            kv("day", state.day); kv("reviewsToday", state.reviewsToday); kv("cashToday", state.cashToday)
            kv("itemCountdown", state.itemCountdown)
            list("buttonsToday", state.buttonsToday.toList())
            kv("cardStreak", state.cardStreak); kv("bestCardStreak", state.bestCardStreak)
            kv("streakDays", state.streakDays); kv("lastGoalDay", state.lastGoalDay); kv("totalReviews", state.totalReviews)
            kv("bestStreakDays", state.bestStreakDays)
            key("history"); obj {
                for ((k, v) in state.history.toSortedMap()) { key(k); arr { value(v.reviews); value(v.won); value(v.caught); value(v.shinies) } }
            }
            list("badges", state.badges.sorted())
            kv("shopDay", state.shopDay)
            list("shopItems", state.shopItems); list("shopTms", state.shopTms)
            key("redo"); obj { for ((k, v) in state.redo.toSortedMap()) kv(k.toString(), v) }
            kv("liveDay", state.liveDay); kv("liveSnapshot", state.liveSnapshot)
            kv("starterChosen", state.starterChosen); kv("catches", state.catches); kv("defeats", state.defeats)
            kv("evolutions", state.evolutions)
            list("releasedAnkimon", state.releasedAnkimon) // insertion order (oldest first), not sorted
            key("settings"); writeSettings(this, state.settings)
        }
        return w.toString()
    }

    /**
     * Throws [SaveFormatException] when the text isn't JSON, `schema` or `mons` is missing, a Pokémon is malformed, or the
     * schema is newer than ours. It never drops a Pokémon to make a save load.
     */
    fun decode(json: String): GameState {
        try {
            val t = JSONTokener(json)
            val root = t.nextValue() as? JSONObject ?: throw SaveFormatException("not a JSON object")
            if (t.nextClean() != '\u0000') throw SaveFormatException("garbage after the JSON text")
            if (!root.has("schema") || root.isNull("schema")) throw SaveFormatException("missing schema")
            val schema = root.getInt("schema")
            if (schema > GameState.SCHEMA) throw SaveFormatException("save from a newer AnkiGo (schema $schema)", newerSchema = true)
            val monsJson = root.optJSONArray("mons") ?: throw SaveFormatException("missing mons")
            return readState(root, schema, monsJson)
        } catch (e: SaveFormatException) {
            throw e
        } catch (e: Exception) {
            throw SaveFormatException("unreadable save: ${e.message}", cause = e)
        }
    }

    // ---- write ----

    /**
     * A tiny compact JSON writer. Not org.json's JSONStringer: its chained methods return different types in Android's
     * and in the JVM test library, which breaks binary compatibility between the two.
     */
    private class W {
        private val sb = StringBuilder()
        private var needComma = false

        private fun sep() { if (needComma) sb.append(',') }
        fun obj(f: W.() -> Unit) { sep(); sb.append('{'); needComma = false; f(); sb.append('}'); needComma = true }
        fun arr(f: W.() -> Unit) { sep(); sb.append('['); needComma = false; f(); sb.append(']'); needComma = true }
        fun key(k: String) { sep(); sb.append(JSONObject.quote(k)).append(':'); needComma = false }
        fun value(v: Any?) {
            sep()
            when (v) {
                null -> sb.append("null")
                is Number, is Boolean -> sb.append(v.toString())
                else -> sb.append(JSONObject.quote(v.toString()))
            }
            needComma = true
        }
        fun kv(k: String, v: Any?) { key(k); value(v) }
        fun list(k: String, items: Collection<Any?>) { key(k); arr { items.forEach { value(it) } } }
        override fun toString() = sb.toString()
    }

    private fun W.stats(k: String, s: Stats) = list(k, s.toList())

    private fun writeMon(w: W, m: Mon) = w.obj {
        kv("uid", m.uid); kv("species", m.species); kv("name", m.name); kv("level", m.level); kv("xp", m.xp)
        stats("ivs", m.ivs); stats("evs", m.evs)
        kv("nature", m.nature.name); kv("gender", m.gender.toString()); kv("shiny", m.shiny); kv("ability", m.ability)
        list("moves", m.moves)
        kv("hp", m.hp); kv("friendship", m.friendship); kv("heldItem", m.heldItem); kv("caughtAt", m.caughtAt)
        kv("defeats", m.defeats); kv("tier", m.tier.name)
        kv("nickname", m.nickname); kv("ankimonId", m.ankimonId); kv("favourite", m.favourite)
    }

    private fun writeUnsupported(w: W, u: UnsupportedMon) = w.obj {
        kv("ankimonId", u.ankimonId); kv("speciesId", u.speciesId); kv("name", u.name); kv("nickname", u.nickname)
        kv("level", u.level); kv("shiny", u.shiny); kv("reason", u.reason); kv("raw", u.raw)
    }

    private fun writeSide(w: W, s: BattleSide) = w.obj {
        list("boosts", s.boosts.toList())
        kv("status", s.status); kv("statusTurns", s.statusTurns)
        list("charged", s.charged.sorted())
    }

    private fun writeBattle(w: W, b: Battle) = w.obj {
        key("wild"); writeMon(this, b.wild)
        kv("tier", b.tier.name)
        key("player"); writeSide(this, b.player)
        key("enemy"); writeSide(this, b.enemy)
        kv("tally", b.tally); kv("answers", b.answers); kv("override", b.override?.name); kv("rounds", b.rounds)
        kv("startedAt", b.startedAt); kv("wildMaxHp", b.wildMaxHp)
    }

    private fun writeSettings(w: W, s: Settings) = w.obj {
        list("gens", s.gens.sorted())
        kv("fasterStartV2", s.fasterStart)
        kv("cardsPerRound", s.cardsPerRound); kv("autoMode", s.autoMode)
        kv("protectLegendary", s.protectLegendary); kv("protectMythical", s.protectMythical)
        kv("protectUltra", s.protectUltra); kv("protectStarter", s.protectStarter)
        kv("protectRegional", s.protectRegional)
        list("wishlist", s.wishlist.sorted())
        kv("dailyAverage", s.dailyAverage); kv("dayStartHour", s.dayStartHour)
        kv("xpShareUid", s.xpShareUid); kv("trainerName", s.trainerName)
    }

    // ---- read ----

    private fun JSONObject.has2(k: String) = has(k) && !isNull(k)
    private fun JSONObject.str(k: String, d: String): String = if (has2(k)) getString(k) else d
    private fun JSONObject.strOrNull(k: String): String? = if (has2(k)) getString(k) else null
    private fun JSONObject.int(k: String, d: Int): Int = if (has2(k)) getInt(k) else d
    private fun JSONObject.intOrNull(k: String): Int? = if (has2(k)) getInt(k) else null
    private fun JSONObject.long(k: String, d: Long): Long = if (has2(k)) getLong(k) else d
    private fun JSONObject.bool(k: String, d: Boolean): Boolean = if (has2(k)) getBoolean(k) else d

    private fun JSONObject.intList(k: String): List<Int>? =
        if (has2(k)) getJSONArray(k).let { a -> List(a.length()) { a.getInt(it) } } else null

    private fun JSONObject.strList(k: String): List<String>? =
        if (has2(k)) getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } } else null

    /** An IntArray of exactly [size]: a shorter stored array is padded with zeros, a longer one cut. */
    private fun JSONObject.intArray(k: String, size: Int): IntArray {
        val l = intList(k) ?: emptyList()
        return IntArray(size) { l.getOrElse(it) { 0 } }
    }

    private fun JSONObject.stats(k: String): Stats = intArray(k, 6).let { Stats.of(it.toList()) }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, d: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: d

    private fun readMon(o: JSONObject): Mon {
        if (!o.has2("uid") || !o.has2("species") || !o.has2("level")) throw SaveFormatException("pokemon without uid/species/level")
        return Mon(
            uid = o.getString("uid"), species = o.getInt("species"), name = o.str("name", ""), level = o.getInt("level"),
            xp = o.int("xp", 0), ivs = o.stats("ivs"), evs = o.stats("evs"),
            nature = enumOr(o.strOrNull("nature"), Nature.HARDY),
            gender = o.strOrNull("gender")?.firstOrNull() ?: 'N',
            shiny = o.bool("shiny", false), ability = o.str("ability", ""),
            moves = (o.strList("moves") ?: emptyList()).toMutableList(),
            hp = o.int("hp", 1), friendship = o.int("friendship", 0), heldItem = o.strOrNull("heldItem"),
            caughtAt = o.long("caughtAt", 0), defeats = o.int("defeats", 0),
            tier = enumOr(o.strOrNull("tier"), Tier.NORMAL),
            nickname = o.strOrNull("nickname"), ankimonId = o.strOrNull("ankimonId"),
            favourite = o.bool("favourite", false),
        )
    }

    private fun readUnsupported(o: JSONObject): UnsupportedMon = UnsupportedMon(
        ankimonId = o.getString("ankimonId"), speciesId = o.intOrNull("speciesId"), name = o.str("name", ""),
        nickname = o.strOrNull("nickname"), level = o.int("level", 1), shiny = o.bool("shiny", false),
        reason = o.str("reason", ""), raw = o.strOrNull("raw"),
    )

    private fun readSide(o: JSONObject?): BattleSide =
        if (o == null) BattleSide() else BattleSide(
            o.intArray("boosts", 7), o.strOrNull("status"), o.int("statusTurns", 0),
            (o.strList("charged") ?: emptyList()).toMutableSet(),
        )

    /** A battle is transient; one without a readable wild Pokémon is dropped (the next answer starts a new one). */
    private fun readBattle(o: JSONObject): Battle? {
        val wild = try { o.optJSONObject("wild")?.let { readMon(it) } } catch (e: Exception) { null } ?: return null
        return Battle(
            wild = wild, tier = enumOr(o.strOrNull("tier"), Tier.NORMAL),
            player = readSide(o.optJSONObject("player")), enemy = readSide(o.optJSONObject("enemy")),
            tally = o.int("tally", 0), answers = o.int("answers", 0),
            override = o.strOrNull("override")?.let { n -> Outcome.entries.firstOrNull { it.name == n } },
            rounds = o.int("rounds", 0), startedAt = o.long("startedAt", 0),
            wildMaxHp = o.intOrNull("wildMaxHp")?.coerceAtLeast(1),
        )
    }

    private fun readSettings(o: JSONObject?, s: Settings) {
        s.fasterStart = o?.bool("fasterStartV2", false) ?: false // old "fasterStart" key ignored: existing saves switch it off once
        if (o == null) return
        o.intList("gens")?.let { s.gens.clear(); s.gens.addAll(it) }
        s.cardsPerRound = o.int("cardsPerRound", s.cardsPerRound)
        s.autoMode = o.int("autoMode", s.autoMode)
        s.protectLegendary = o.bool("protectLegendary", s.protectLegendary)
        s.protectMythical = o.bool("protectMythical", s.protectMythical)
        s.protectUltra = o.bool("protectUltra", s.protectUltra)
        s.protectStarter = o.bool("protectStarter", s.protectStarter)
        s.protectRegional = o.bool("protectRegional", s.protectRegional)
        o.intList("wishlist")?.let { s.wishlist.clear(); s.wishlist.addAll(it) }
        s.dailyAverage = o.int("dailyAverage", s.dailyAverage)
        s.dayStartHour = o.int("dayStartHour", s.dayStartHour)
        s.xpShareUid = o.strOrNull("xpShareUid")
        s.trainerName = o.str("trainerName", s.trainerName)
    }

    /** History is a day -> [reviews, won, caught, shinies] object. An old save has none: today's count seeds today. */
    private fun readHistory(h: JSONObject?, s: GameState) {
        if (h == null) {
            if (s.day.isNotEmpty() && s.reviewsToday > 0) s.history[s.day] = DayLog(reviews = s.reviewsToday)
            return
        }
        for (k in h.keys()) {
            val a = h.optJSONArray(k) ?: continue
            s.history[k] = DayLog(a.optInt(0), a.optInt(1), a.optInt(2), a.optInt(3))
        }
    }

    private fun readState(o: JSONObject, schema: Int, monsJson: JSONArray): GameState {
        val s = GameState()
        s.schema = schema
        s.seq = o.long("seq", 0); s.savedAt = o.long("savedAt", 0); s.createdAt = o.long("createdAt", 0)
        for (i in 0 until monsJson.length()) s.mons += readMon(monsJson.getJSONObject(i))
        // "Not supported yet" Pokémon (an old save has none). An unreadable entry is skipped: its row is still in the kept Ankimon file.
        o.optJSONArray("unsupported")?.let { a ->
            for (i in 0 until a.length()) {
                val u = try { readUnsupported(a.getJSONObject(i)) } catch (e: Exception) { null } ?: continue
                if (s.unsupported.none { it.ankimonId == u.ankimonId }) s.unsupported += u
            }
        }
        // A uid that isn't in mons leaves the team; the Pokémon themselves are never touched.
        val known = s.mons.map { it.uid }.toSet()
        s.team += (o.strList("team") ?: emptyList()).filter { it in known }
        if (s.team.isEmpty() && s.mons.isNotEmpty()) s.team += s.mons.first().uid
        o.optJSONObject("bag")?.let { b -> for (k in b.keys()) s.bag[k] = b.getInt(k) }
        o.strList("tms")?.let { s.tms += it }
        s.cash = o.int("cash", 0); s.trainerLevel = o.int("trainerLevel", 1); s.trainerXp = o.int("trainerXp", 0)
        o.intList("seen")?.let { s.seen += it }
        o.intList("caught")?.let { s.caught += it }
        s.battle = o.optJSONObject("battle")?.let { readBattle(it) }
        s.nextSpecies = o.intOrNull("nextSpecies")
        s.nextTier = enumOr(o.strOrNull("nextTier"), Tier.NORMAL)
        s.nextShiny = o.bool("nextShiny", false)
        s.nextSeed = if (o.has2("nextSeed")) o.getLong("nextSeed") else null
        s.day = o.str("day", ""); s.reviewsToday = o.int("reviewsToday", 0); s.cashToday = o.int("cashToday", 0)
        s.itemCountdown = o.int("itemCountdown", 0)
        o.intArray("buttonsToday", s.buttonsToday.size).copyInto(s.buttonsToday)
        s.cardStreak = o.int("cardStreak", 0); s.bestCardStreak = o.int("bestCardStreak", 0)
        s.streakDays = o.int("streakDays", 0); s.lastGoalDay = o.strOrNull("lastGoalDay")
        s.totalReviews = o.long("totalReviews", 0)
        s.bestStreakDays = o.int("bestStreakDays", s.streakDays)
        readHistory(o.optJSONObject("history"), s)
        o.intList("badges")?.let { s.badges += it }
        s.shopDay = o.strOrNull("shopDay")
        o.strList("shopItems")?.let { s.shopItems += it }
        o.strList("shopTms")?.let { s.shopTms += it }
        o.optJSONObject("redo")?.let { r -> for (k in r.keys()) k.toLongOrNull()?.let { id -> s.redo[id] = r.getInt(k) } }
        s.liveDay = o.strOrNull("liveDay"); s.liveSnapshot = o.strOrNull("liveSnapshot")
        s.starterChosen = o.bool("starterChosen", false)
        s.catches = o.int("catches", 0); s.defeats = o.int("defeats", 0); s.evolutions = o.int("evolutions", 0)
        o.strList("releasedAnkimon")?.let { s.releasedAnkimon += it }
        readSettings(o.optJSONObject("settings"), s.settings)
        // Decoded and filled with defaults, so it is a current-schema state now.
        s.schema = GameState.SCHEMA
        return s
    }
}
