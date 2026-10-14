package com.husarp.ankigo.game.ankimon

import com.husarp.ankigo.game.BattleSide
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Mon
import com.husarp.ankigo.game.Nature
import com.husarp.ankigo.game.Progression
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.StatCalc
import com.husarp.ankigo.game.Stats
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Tuning
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// TICKET T19 owns this file.

/** What a merge did (or, run on a copy for the preview, would do). */
data class ImportReport(
    val added: Int,
    val upgraded: Int,
    val unchanged: Int,
    val skipped: List<String>,                       // "<name or #id>: <reason>"
    val seenAdded: Int,
    val caughtAdded: Int,
    val cashBefore: Int,
    val cashAfter: Int,
    val itemsRaised: Map<String, Pair<Int, Int>>,    // item id -> (before, after), only where it went up
    val tmsAdded: Int,
    val teamChanged: Boolean,
    val xpShareChanged: Boolean = false,             // the file's Exp. Share holder became the game's
    val notInFile: Int = 0,                          // Pokémon on the phone that were on the PC once but aren't in this file (they stay)
) {
    /** One short line, e.g. "adds 37 Pokémon, upgrades 4; Pokédex +12; ₽ 3,100 → 12,400; 5 items" ("nothing new" when empty). */
    fun summary(): String {
        val parts = mutableListOf<String>()
        when {
            added > 0 && upgraded > 0 -> parts += "adds $added Pokémon, upgrades $upgraded"
            added > 0 -> parts += "adds $added Pokémon"
            upgraded > 0 -> parts += "upgrades $upgraded Pokémon"
        }
        val dex = maxOf(seenAdded, caughtAdded)
        if (dex > 0) parts += "Pokédex +$dex"
        if (cashAfter != cashBefore) parts += "₽ ${money(cashBefore)} → ${money(cashAfter)}"
        if (itemsRaised.isNotEmpty()) parts += if (itemsRaised.size == 1) "1 item" else "${itemsRaised.size} items"
        if (tmsAdded > 0) parts += if (tmsAdded == 1) "1 TM" else "$tmsAdded TMs"
        if (teamChanged) parts += "new team"
        if (xpShareChanged) parts += "Exp. Share holder"
        return if (parts.isEmpty()) "nothing new" else parts.joinToString("; ")
    }

    private fun money(n: Int) = String.format(Locale.US, "%,d", n)
}

/** Merges an [AnkimonSave] into a game. Pure Kotlin; the UI runs it on a copy for the preview, then on a fresh copy to apply. */
object AnkimonMerge {
    private val STAT_KEYS = listOf("hp", "atk", "def", "spa", "spd", "spe")
    private const val NICKNAME_MAX = 20
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    /**
     * Merges [save] into [state] IN PLACE (the UI runs it on a copy for the preview, then again on a fresh copy to apply).
     * Never removes anything. Deterministic: same inputs -> same state (no randomness; uids derived from individual_id).
     * [formBase]: PokeAPI form id (> 10000) -> base species number, null if unknown. A form id that is a regional form in
     * [data] (Alolan, Galarian, Hisuian, Paldean) stays that form; every other form id (Mega, Deoxys...) maps to its base species.
     * [useTeam]: Ankimon's team becomes the team (old members stay in the box).
     */
    fun merge(data: GameData, state: GameState, save: AnkimonSave, formBase: (Int) -> Int?, useTeam: Boolean, now: Long): ImportReport {
        val nameIndex = nameIndex(data)
        val notInFile = notInFile(state, save)

        // A phone-caught Pokémon that was exported to the PC comes back under its uid (see AnkimonExport): match both.
        val byAnkimonId = HashMap<String, Mon>()
        for (m in state.mons) byAnkimonId.putIfAbsent(m.ankimonId ?: m.uid, m)
        val takenUids = state.mons.mapTo(HashSet()) { it.uid }

        var added = 0
        var upgraded = 0
        var unchanged = 0
        val skipped = mutableListOf<String>()
        val importedSpecies = LinkedHashSet<Int>()

        for (m in save.mons) {
            val label = m.name ?: m.id?.let { "#$it" } ?: "Pokémon ${m.individualId.take(8)}"
            val existing = byAnkimonId[m.individualId]
            if (existing == null && m.individualId in state.releasedAnkimon) { skipped += "$label: released on this phone"; continue }
            val (sp, reason) = resolveSpecies(data, state, m, formBase, nameIndex)
            if (sp == null) { skipped += "$label: $reason"; continue }
            importedSpecies += sp.num
            val attackMoves = attackMoves(data, m)
            if (existing == null) {
                val mon = buildMon(data, m, sp, newUid(m.individualId, takenUids), now, attackMoves)
                state.mons += mon
                byAnkimonId[m.individualId] = mon
                takenUids += mon.uid
                added++
            } else {
                val fileMon = buildMon(data, m, sp, "", now, attackMoves)
                if (upgrade(data, existing, fileMon, attackMoves)) upgraded++ else unchanged++
            }
        }

        val teamChanged = mergeTeam(state, save, byAnkimonId, useTeam)
        // The file's Exp. Share holder, when it is in the game: with Ankimon's team, or when the phone has none.
        val holder = save.xpShare?.let { byAnkimonId[it] }
        val phoneHolder = state.settings.xpShareUid?.let { state.mon(it) }
        val xpShareChanged = holder != null && holder !== phoneHolder && (useTeam || phoneHolder == null)
        if (xpShareChanged) state.settings.xpShareUid = holder!!.uid
        if (state.team.isNotEmpty()) state.starterChosen = true

        // Pokédex: union; regional forms stay themselves, other forms count as their base species; only species this game knows.
        fun dex(ids: Collection<Int>) = ids.mapNotNull { dexId(data, formBase, it) }
        val newCaught = dex(save.caught) + importedSpecies
        val newSeen = dex(save.seen) + newCaught
        val seenBefore = state.seen.size
        val caughtBefore = state.caught.size
        state.seen += newSeen
        state.caught += newCaught

        // Items and money: the higher of the two values, so importing the same file again changes nothing.
        val raised = LinkedHashMap<String, Pair<Int, Int>>()
        for ((name, qty) in save.items) {
            val id = itemKey(name) ?: continue
            val old = state.count(id) // already includes an earlier name that mapped to the same item
            if (qty > old) { raised[id] = (raised[id]?.first ?: old) to qty; state.bag[id] = qty }
        }
        val cashBefore = state.cash
        save.cash?.let { state.cash = maxOf(state.cash, it) }

        var tmsAdded = 0
        for (name in save.tms) {
            val move = tmMove(data, name) ?: continue
            if (state.tms.add(move)) tmsAdded++
        }

        return ImportReport(
            added = added, upgraded = upgraded, unchanged = unchanged, skipped = skipped,
            seenAdded = state.seen.size - seenBefore, caughtAdded = state.caught.size - caughtBefore,
            cashBefore = cashBefore, cashAfter = state.cash, itemsRaised = raised, tmsAdded = tmsAdded, teamChanged = teamChanged,
            xpShareChanged = xpShareChanged, notInFile = notInFile,
        )
    }

    /**
     * Pokémon on the phone that were on the PC once but that [save] doesn't have (released or traded on the PC, or an
     * older file). The import never removes them; the preview says so. "Once on the PC": imported from Ankimon, or caught
     * on the phone no later than the newest phone catch the file holds (the file came after an export that had it).
     */
    internal fun notInFile(state: GameState, save: AnkimonSave): Int {
        val inFile = save.mons.mapTo(HashSet()) { it.individualId }
        val lastExported = state.mons.filter { it.ankimonId == null && it.uid in inFile }.maxOfOrNull { it.caughtAt }
        return state.mons.count { m ->
            (m.ankimonId ?: m.uid) !in inFile && (m.ankimonId != null || (lastExported != null && m.caughtAt <= lastExported))
        }
    }

    // ---- species ------------------------------------------------------------------------------------------------

    /** Lower-case letters-only species name -> species number (the lowest number wins), for finding a form by its Ankimon name. */
    internal fun nameIndex(data: GameData): Map<String, Int> {
        val index = HashMap<String, Int>()
        for (sp in data.species.values.sortedBy { it.num }) index.putIfAbsent(letters(sp.name), sp.num)
        return index
    }

    private fun resolveSpecies(
        data: GameData, state: GameState, m: AnkimonMon, formBase: (Int) -> Int?, nameIndex: Map<String, Int>,
    ): Pair<Species?, String> {
        val (sp, reason) = speciesFor(data, m, formBase, nameIndex)
        if (sp != null && sp.gen >= 9 && 9 !in state.settings.gens) return null to "gen 9: turn on generation 9 in Settings to import it"
        return sp to reason
    }

    /** The species the import would give [m] (before the generation setting is applied), or null and the reason. */
    internal fun speciesFor(data: GameData, m: AnkimonMon, formBase: (Int) -> Int?, nameIndex: Map<String, Int>): Pair<Species?, String> {
        val id = m.id
        if (id == null || id <= 0) return null to "no species number"
        var num: Int? = id
        if (id > 10000 && !isRegional(data, id)) {
            num = formBase(id)?.takeIf { it in data.species } ?: speciesByName(m.name, nameIndex)
            if (num == null) return null to "form $id isn't known"
        }
        val sp = data.species[num] ?: return null to "species #$num isn't in AnkiGo's data"
        return sp to ""
    }

    /** The species number a Pokédex entry [n] of an Ankimon save counts as here (regional forms stay, other forms -> base species), or null. */
    internal fun dexId(data: GameData, formBase: (Int) -> Int?, n: Int): Int? =
        (if (n > 10000 && !isRegional(data, n)) formBase(n) else n)?.takeIf { it in data.species }

    /** True when [id] is a regional form this game has (its species entry carries a form name). */
    private fun isRegional(data: GameData, id: Int) = data.species[id]?.form != null

    /** "Charizard-Mega-X" -> Charizard: the longest leading run of words (split at '-' and spaces) that is a species name. */
    private fun speciesByName(name: String?, index: Map<String, Int>): Int? {
        val words = name?.split(Regex("[-\\s]+"))?.filter { it.isNotEmpty() } ?: return null
        for (k in words.size downTo 1) index[letters(words.take(k).joinToString(""))]?.let { return it }
        return null
    }

    // ---- building and upgrading Pokémon -------------------------------------------------------------------------

    private fun newUid(individualId: String, taken: Set<String>): String {
        var n = 0
        while (true) {
            val seed = if (n == 0) "ankimon:$individualId" else "ankimon:$individualId#$n"
            val uid = UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
            if (uid !in taken) return uid
            n++
        }
    }

    /** The file's attacks that AnkiGo knows (normalised to Showdown ids), distinct, at most 4. May be empty. */
    internal fun attackMoves(data: GameData, m: AnkimonMon): List<String> =
        m.attacks.map { letters(it) }.filter { it in data.moves }.distinct().take(4)

    internal fun buildMon(data: GameData, m: AnkimonMon, sp: Species, uid: String, now: Long, attackMoves: List<String>): Mon {
        val level = (m.level ?: 1).coerceIn(1, Tuning.MAX_LEVEL)
        val ivs = Stats.of(STAT_KEYS.map { (m.iv[it] ?: 15).coerceIn(0, 31) })
        val evs = Stats.of(capEvs(STAT_KEYS.map { (m.ev[it] ?: 0).coerceIn(0, Tuning.EV_STAT_CAP) }))
        val moves = attackMoves.ifEmpty {
            Progression.movesUpTo(sp, level).filter { it in data.moves }.takeLast(4).ifEmpty { listOf(Tuning.FALLBACK_MOVE) }
        }
        val heldItem = if (m.everstone) "everstone" else m.heldItem?.let { itemKey(it) }?.takeIf { Items.def(it)?.kind == ItemKind.HELD }
        val mon = Mon(
            uid = uid, species = sp.num, name = sp.name, level = level, xp = 0, ivs = ivs, evs = evs,
            nature = Nature.entries.firstOrNull { it.name.equals(m.nature?.trim(), ignoreCase = true) } ?: Nature.SERIOUS,
            gender = gender(m.gender, sp), shiny = m.shiny, ability = ability(m.ability, sp), moves = moves.toMutableList(),
            hp = 1, friendship = (m.friendship ?: 0).coerceIn(0, 255), heldItem = heldItem,
            caughtAt = parseDate(m.capturedDate) ?: now, defeats = (m.defeated ?: 0).coerceAtLeast(0), tier = tier(m.tier),
            nickname = nickname(m, sp), ankimonId = m.individualId,
        )
        mon.xp = clampXp(data, mon, m.xp ?: 0)
        mon.hp = StatCalc.maxHp(data, mon)
        return mon
    }

    /** Each stat at most 252 (done by the caller), the total at most 510: the last stats are cut. */
    private fun capEvs(evs: List<Int>): List<Int> {
        var left = Tuning.EV_TOTAL_CAP
        return evs.map { v -> v.coerceAtMost(left).also { left -= it } }
    }

    /** XP is progress inside the level: 0 .. xpToNext-1 (0 at level 100). */
    private fun clampXp(data: GameData, mon: Mon, xp: Int): Int {
        val need = Progression.xpToNext(data, mon)
        return if (need <= 0) 0 else xp.coerceIn(0, need - 1)
    }

    private fun gender(raw: String?, sp: Species): Char {
        val ratio = sp.maleRatio ?: return 'N'
        if (ratio <= 0.0) return 'F'
        if (ratio >= 1.0) return 'M'
        return when (raw?.trim()?.firstOrNull()?.uppercaseChar()) {
            'M' -> 'M'
            'F' -> 'F'
            else -> if (ratio >= 0.5) 'M' else 'F'
        }
    }

    private fun ability(raw: String?, sp: Species): String {
        val want = raw?.let { letters(it) }.orEmpty()
        return sp.abilities.firstOrNull { want.isNotEmpty() && letters(it) == want } ?: sp.abilities.firstOrNull().orEmpty()
    }

    private fun tier(raw: String?): Tier {
        val t = raw?.trim()?.lowercase().orEmpty()
        return Tier.entries.firstOrNull { it.name.lowercase() == t || it.label.lowercase() == t }
            ?: if (t.startsWith("ultra")) Tier.ULTRA else Tier.NORMAL
    }

    private fun nickname(m: AnkimonMon, sp: Species): String? {
        val n = m.nickname?.trim()?.take(NICKNAME_MAX)?.trim().orEmpty()
        if (n.isEmpty() || n.equals(sp.name, ignoreCase = true) || n.equals(m.name, ignoreCase = true)) return null
        return n
    }

    /** "yyyy-MM-dd HH:mm:ss" in the phone's time zone; null when it doesn't parse. */
    private fun parseDate(raw: String?): Long? {
        val s = raw?.trim()?.take(19)?.replace('T', ' ') ?: return null
        return try {
            LocalDateTime.parse(s, DATE).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    /** True when a (transitive) evolution of [from] is [to]. */
    private fun evolvesInto(data: GameData, from: Int, to: Int): Boolean {
        val seen = HashSet<Int>()
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (!seen.add(cur)) continue
            for (e in data.species[cur]?.evolutions.orEmpty()) {
                if (e.to == to) return true
                queue += e.to
            }
        }
        return false
    }

    /**
     * Lets [ex] catch up with the file's version [file] when that is further along; never lowers anything.
     * IVs, nature, gender, ability and held item are never touched. Returns true when something changed.
     */
    private fun upgrade(data: GameData, ex: Mon, file: Mon, fileMoves: List<String>): Boolean {
        if (ex.species !in data.species) return false
        val further = file.species != ex.species && evolvesInto(data, ex.species, file.species)
        val higher = file.level > ex.level || (file.level == ex.level && file.xp > ex.xp)
        if (further || higher) {
            if (further) { ex.species = file.species; ex.name = file.name }
            if (higher) { ex.level = file.level; ex.xp = file.xp }
            ex.xp = clampXp(data, ex, ex.xp) // the new species may grow at another rate
            if (fileMoves.isNotEmpty()) { ex.moves.clear(); ex.moves.addAll(fileMoves) }
            val merged = Stats.of(ex.evs.toList().zip(file.evs.toList()) { a, b -> maxOf(a, b) })
            if (merged.total <= Tuning.EV_TOTAL_CAP) ex.evs = merged
            ex.friendship = maxOf(ex.friendship, file.friendship)
            ex.shiny = ex.shiny || file.shiny
            file.nickname?.let { ex.nickname = it }
            ex.hp = StatCalc.maxHp(data, ex)
            return true
        }
        if (fileMoves.size > ex.moves.size) {
            ex.moves.clear(); ex.moves.addAll(fileMoves)
            return true
        }
        return false
    }

    // ---- team ---------------------------------------------------------------------------------------------------

    /** Ankimon's team (main first, at most 6, only Pokémon that are in AnkiGo) replaces the team. Returns true when the team changed. */
    private fun mergeTeam(state: GameState, save: AnkimonSave, byAnkimonId: Map<String, Mon>, useTeam: Boolean): Boolean {
        if (!useTeam && state.team.isNotEmpty()) return false
        val main = save.mons.firstOrNull { it.isMain }?.individualId
        val order = (listOfNotNull(main) + save.team.filter { it != main }).distinct()
        val uids = order.mapNotNull { byAnkimonId[it]?.uid }.distinct().take(6)
        if (uids.isEmpty()) return false
        val oldMain = state.team.firstOrNull()
        val changed = uids != state.team.toList()
        state.team.clear()
        state.team.addAll(uids)
        // A new main starts the fight fresh (no boosts or status carried over from the old one).
        if (uids.first() != oldMain) state.battle?.player = BattleSide()
        return changed
    }

    // ---- names --------------------------------------------------------------------------------------------------

    /** Lower-case, letters and digits only ("Mr. Mime" -> "mrmime", "Double-Edge" -> "doubleedge"): Showdown ids. */
    private fun letters(s: String): String = s.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    /** The id AnkiGo uses for an Ankimon item name ("Poké Ball", "poke_ball" -> "poke-ball"), or null if AnkiGo has no such item. */
    internal fun itemKey(name: String): String? {
        val plain = Normalizer.normalize(name, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        val id = plain.trim().lowercase().replace(' ', '-').replace('_', '-').filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }
        if (Items.def(id) != null) return id
        val bare = id.replace("-", "")
        return Items.ALL.keys.firstOrNull { it.replace("-", "") == bare }
    }

    /** A TM item is named after its move ("thunderbolt"); older exports may write "TM24 Thunderbolt". */
    internal fun tmMove(data: GameData, name: String): String? {
        val id = letters(name)
        if (id in data.moves) return id
        return id.replace(Regex("^tm\\d*"), "").takeIf { it in data.moves }
    }
}
