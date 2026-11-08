package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.EffectKind
import com.husarp.ankigo.game.GameEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * R19-K (PLAN 269, pick A "Word chips on the cards"; PLAN 287 for the bubbles). Pure rules, no Android:
 * - [ConditionMarks]: which lasting conditions of a fight get a chip, their short word, the number on it (turns left, or none when the
 *   rule's number is not turns: a Substitute's HP, Stockpile's layers) and how important each is;
 * - [ConditionBoard]: what is shown right now, kept in step with the played events and settled from the saved fight;
 * - [ConditionRow.fit]: the one-row plan ("Confused 3, Seeded, +2");
 * - [ConditionSpot]: where a side's row and the field chip sit - always in free room, never over anything.
 * The chips are drawn by [ConditionMarksView]; the list that opens on a tap is [ConditionList].
 */

/** The words, numbers and order of the condition chips. A key that is not here (a bookkeeping key of a rule set) never gets a chip. */
object ConditionMarks {
    /** Where a mark shows: the wild card's row, our card's row, or the corner chip for the whole field. */
    enum class Slot { WILD, OURS, FIELD }

    /** The colour family of a chip (the view picks the hue). */
    enum class Group { HINDER, DRAIN, GUARD, BOOST, WEATHER, TERRAIN, ROOM }

    /** What the stored number means: turns left, nothing to show, an HP value, or layers (the chip says "Stockpile 2"). */
    enum class Num { TURNS, NONE, HP, LAYERS }

    /** Which map of the fight a key lives in. */
    enum class Kind { VOLATILE, SIDE, WEATHER, TERRAIN, ROOM }

    class Def(
        val key: String, val kind: Kind, val word: String, val group: Group, val num: Num, val rank: Int,
        /** What the list says after the word when there is no number to give (nothing for a condition without a turn limit; "until replaced" for a weather that stays). */
        val noTurns: String = "",
    )

    private fun v(key: String, word: String, group: Group, num: Num, rank: Int) = Def(key, Kind.VOLATILE, word, group, num, rank)
    private fun s(key: String, word: String, rank: Int) = Def(key, Kind.SIDE, word, Group.GUARD, Num.TURNS, rank)

    /** The lasting conditions of one Pokémon (`BattleSide.volatiles`), most important first. Round-long ones (flinch, Protect ...) have no chip. */
    private val VOLATILES: List<Def> = listOf(
        v("embargo", "Embargo", Group.HINDER, Num.TURNS, 8),
        v("perishsong", "Perish Song", Group.HINDER, Num.TURNS, 7),
        v("switchtrap", "Trapped", Group.HINDER, Num.TURNS, 9),
        v("confusion", "Confused", Group.HINDER, Num.TURNS, 10),
        v("attract", "In love", Group.HINDER, Num.NONE, 11),
        v("partiallytrapped", "Trapped", Group.HINDER, Num.TURNS, 12),
        v("taunt", "Taunted", Group.HINDER, Num.TURNS, 13),
        v("encore", "Encore", Group.HINDER, Num.TURNS, 14),
        v("disable", "Disabled", Group.HINDER, Num.TURNS, 15),
        v("torment", "Tormented", Group.HINDER, Num.NONE, 16),
        v("healblock", "Heal Block", Group.HINDER, Num.TURNS, 17),
        v("throatchop", "Throat Chop", Group.HINDER, Num.TURNS, 18),
        v("yawn", "Drowsy", Group.HINDER, Num.TURNS, 19),
        v("imprison", "Imprison", Group.HINDER, Num.NONE, 20),
        v("lockedmove", "Locked in", Group.HINDER, Num.TURNS, 21),
        v("uproar", "Uproar", Group.HINDER, Num.TURNS, 22),
        v("smackdown", "Grounded", Group.HINDER, Num.NONE, 23),
        v("tarshot", "Tar Shot", Group.HINDER, Num.NONE, 24),
        v("foresight", "Foresight", Group.HINDER, Num.NONE, 25),
        v("miracleeye", "Miracle Eye", Group.HINDER, Num.NONE, 26),
        v("leechseed", "Seeded", Group.DRAIN, Num.NONE, 30),
        v("curse", "Cursed", Group.DRAIN, Num.NONE, 31),
        v("nightmare", "Nightmare", Group.DRAIN, Num.NONE, 32),
        v("saltcure", "Salt Cure", Group.DRAIN, Num.NONE, 33),
        v("syrupbomb", "Syrup Bomb", Group.DRAIN, Num.TURNS, 34),
        v("substitute", "Substitute", Group.GUARD, Num.HP, 40),
        v("destinybond", "Destiny Bond", Group.GUARD, Num.NONE, 41),
        v("aquaring", "Aqua Ring", Group.GUARD, Num.NONE, 42),
        v("ingrain", "Ingrain", Group.GUARD, Num.NONE, 43),
        v("stockpile", "Stockpile", Group.GUARD, Num.LAYERS, 44),
        v("magnetrise", "Magnet Rise", Group.BOOST, Num.TURNS, 60),
        v("telekinesis", "Telekinesis", Group.BOOST, Num.TURNS, 61),
        v("charge", "Charged", Group.BOOST, Num.NONE, 63),
        v("defensecurl", "Curled up", Group.BOOST, Num.NONE, 64),
        v("minimize", "Minimized", Group.BOOST, Num.NONE, 65),
        v("powertrick", "Power Trick", Group.BOOST, Num.NONE, 67),
        v("powershift", "Power Shift", Group.BOOST, Num.NONE, 68),
        v("ab.flashfire", "Flash Fire", Group.BOOST, Num.NONE, 71),
        v("ab.gas", "Neutralizing Gas", Group.BOOST, Num.NONE, 72),
    )

    /** The conditions of one SIDE that survive a switch (`BattleField.player` / `enemy`: key -> turns left). */
    private val SIDES: List<Def> = listOf(
        Def("spikes", Kind.SIDE, "Spikes", Group.HINDER, Num.LAYERS, 35),
        Def("toxicspikes", Kind.SIDE, "Toxic Spikes", Group.DRAIN, Num.LAYERS, 36),
        Def("stealthrock", Kind.SIDE, "Stealth Rock", Group.HINDER, Num.NONE, 37),
        Def("stickyweb", Kind.SIDE, "Sticky Web", Group.HINDER, Num.NONE, 38),
        s("reflect", "Reflect", 50), s("lightscreen", "Light Screen", 51), s("auroraveil", "Aurora Veil", 52),
        s("safeguard", "Safeguard", 53), s("mist", "Mist", 54), s("tailwind", "Tailwind", 55), s("wish", "Wish", 56),
        s("mp.fs", "Future Sight", 57),
        Def("happyhour", Kind.SIDE, "Happy Hour", Group.BOOST, Num.NONE, 73),
    )

    private fun f(key: String, kind: Kind, word: String, group: Group, rank: Int, noTurns: String) = Def(key, kind, word, group, Num.TURNS, rank, noTurns)
    private const val UNTIL_WEATHER = "until replaced"
    private const val UNTIL_TERRAIN = "until replaced"

    /** The weather, the terrain and the rooms of the whole field (`BattleField`). */
    private val FIELDS: List<Def> = listOf(
        f("rain", Kind.WEATHER, "Rain", Group.WEATHER, 1, UNTIL_WEATHER), f("sun", Kind.WEATHER, "Sun", Group.WEATHER, 1, UNTIL_WEATHER),
        f("sand", Kind.WEATHER, "Sandstorm", Group.WEATHER, 1, UNTIL_WEATHER), f("hail", Kind.WEATHER, "Hail", Group.WEATHER, 1, UNTIL_WEATHER),
        f("snow", Kind.WEATHER, "Snow", Group.WEATHER, 1, UNTIL_WEATHER),
        f("electric", Kind.TERRAIN, "Electric Terrain", Group.TERRAIN, 2, UNTIL_TERRAIN), f("grassy", Kind.TERRAIN, "Grassy Terrain", Group.TERRAIN, 2, UNTIL_TERRAIN),
        f("misty", Kind.TERRAIN, "Misty Terrain", Group.TERRAIN, 2, UNTIL_TERRAIN), f("psychic", Kind.TERRAIN, "Psychic Terrain", Group.TERRAIN, 2, UNTIL_TERRAIN),
        f("trickroom", Kind.ROOM, "Trick Room", Group.ROOM, 3, ""), f("gravity", Kind.ROOM, "Gravity", Group.ROOM, 4, ""),
        f("magicroom", Kind.ROOM, "Magic Room", Group.ROOM, 5, ""),
        f("wonderroom", Kind.ROOM, "Wonder Room", Group.ROOM, 5, ""), f("mudsport", Kind.ROOM, "Mud Sport", Group.ROOM, 6, ""),
        f("watersport", Kind.ROOM, "Water Sport", Group.ROOM, 7, ""), f("iondeluge", Kind.ROOM, "Ion Deluge", Group.ROOM, 8, ""),
    )

    val STAT_KEYS = listOf("atk", "def", "spa", "spd", "spe", "accuracy", "evasion")
    private val STAT_DEFS = STAT_KEYS.mapIndexed { i, key -> v("stat.$key", StatPill.shortName(key)!!, Group.BOOST, Num.NONE, -10 + i) }

    private val BY_KEY: Map<String, Def> = (VOLATILES + SIDES + FIELDS + STAT_DEFS).associateBy { it.key }
    private val VOLATILE_KEYS: Set<String> = VOLATILES.map { it.key }.toSet()
    private val SIDE_KEYS: Set<String> = SIDES.map { it.key }.toSet()

    /** Every key that gets a chip (tests). */
    val allKeys: Set<String> get() = BY_KEY.keys

    fun def(key: String): Def? = BY_KEY[key]

    /** The chip's word; Stockpile carries its layers in the word ("Stockpile 2"). */
    fun word(d: Def, value: Int): String = if (d.key.startsWith("stat.")) "${d.word} ${if (value > 0) "+" else "−"}${abs(value)}" else if (d.num == Num.LAYERS) "${d.word}${if (d.key == "spikes" || d.key == "toxicspikes") " ×" else " "}${max(1, value)}" else d.word

    /**
     * The number on the chip: the turns left (never under 1 while it is there), or null when the rule's number is not turns (a Substitute's HP,
     * Stockpile's layers), when the condition has no turn limit (Leech Seed), or for a weather / terrain that lasts until replaced (0).
     */
    fun number(d: Def, value: Int): Int? = when (d.num) {
        Num.TURNS -> if (d.kind == Kind.VOLATILE || d.kind == Kind.SIDE) max(1, value) else value.takeIf { it > 0 }
        else -> null
    }

    /** "3 turns left" / "1 turn left". */
    fun turnsLeft(n: Int): String = "$n turn${if (n == 1) "" else "s"} left"

    /** The list line after the word: the turns left, or what the number is; blank when there is nothing to add (no turn limit). */
    fun detail(d: Def, value: Int): String = if (d.key.startsWith("stat.")) {
        val key = d.key.removePrefix("stat.")
        val multiplier = if (key == "accuracy" || key == "evasion") {
            if (value >= 0) (3.0 + value) / 3.0 else 3.0 / (3.0 - value)
        } else com.husarp.ankigo.game.BattleEngine.stageMult(value)
        "${StatPill.spoken(key, value)} (×${"%.2f".format(java.util.Locale.US, multiplier)}). Lasts this fight; resets on switching or fainting."
    } else when (d.num) {
        Num.TURNS -> number(d, value)?.let { turnsLeft(it) } ?: d.noTurns
        Num.HP -> "${max(0, value)} HP left"
        Num.LAYERS -> "${max(1, value)} layer${if (value == 1) "" else "s"}"
        Num.NONE -> d.noTurns
    }

    /** One chip / one line of the list. */
    class Mark(val id: String, val slot: Slot, val def: Def, val value: Int, val seq: Long) {
        val key: String get() = def.key
        val word: String get() = word(def, value)
        val number: Int? get() = number(def, value)
        val detail: String get() = detail(def, value)
        /** What the chip says: "Confused · 3" or "Seeded". */
        val chipText: String get() = word + (number?.let { " · $it" } ?: "")
        /** The list line: "Confused: 3 turns left", "Seeded" (nothing to add). */
        val line: String get() = if (detail.isBlank()) def.word else "${def.word}: $detail"
        /** What a screen reader says: "Confused, 3 turns left." */
        val spoken: String get() = if (detail.isBlank()) word else "$word, $detail"
    }

    /** The marks of [slot] in the order the row shows them: most important first, the newest first among equals. */
    fun order(marks: Collection<Mark>): List<Mark> = marks.sortedWith(compareBy<Mark> { it.def.rank }.thenByDescending { it.seq })

    /** The `BattleSide.volatiles` / side-condition / field values of [b] that have a chip: id -> (slot, def, value). */
    internal fun fromBattle(b: Battle): List<Triple<Slot, Def, Int>> {
        val out = ArrayList<Triple<Slot, Def, Int>>()
        fun volatiles(map: Map<String, Int>, slot: Slot) { for ((k, value) in map) if (k in VOLATILE_KEYS) out += Triple(slot, BY_KEY.getValue(k), value) }
        fun sides(map: Map<String, Int>, slot: Slot) { for ((k, value) in map) if (k in SIDE_KEYS && value > 0) out += Triple(slot, BY_KEY.getValue(k), value) }
        fun stats(side: com.husarp.ankigo.game.BattleSide, slot: Slot) {
            side.boosts.forEachIndexed { i, value -> if (value != 0) out += Triple(slot, STAT_DEFS[i], value) }
        }
        stats(b.enemy, Slot.WILD); stats(b.player, Slot.OURS)
        volatiles(b.enemy.volatiles, Slot.WILD); volatiles(b.player.volatiles, Slot.OURS)
        sides(b.field.enemy, Slot.WILD); sides(b.field.player, Slot.OURS)
        b.field.weather?.let { w -> BY_KEY[w]?.takeIf { it.kind == Kind.WEATHER }?.let { out += Triple(Slot.FIELD, it, b.field.weatherTurns) } }
        b.field.terrain?.let { t -> BY_KEY[t]?.takeIf { it.kind == Kind.TERRAIN }?.let { out += Triple(Slot.FIELD, it, b.field.terrainTurns) } }
        for ((k, value) in b.field.rooms) BY_KEY[k]?.takeIf { it.kind == Kind.ROOM && value > 0 }?.let { out += Triple(Slot.FIELD, it, value) }
        return out
    }
}

/**
 * What the arena shows, as a plain list of [ConditionMarks.Mark]s. It is moved by the played events ([applyEffect]: a condition starts or ends
 * in step with its animation) and settled from the saved fight whenever the panel is drawn from it ([syncFrom]: the turns tick down, anything the
 * events did not say is corrected). Not thread-safe (main thread).
 */
class ConditionBoard {
    private val marks = LinkedHashMap<String, ConditionMarks.Mark>()
    private var seq = 0L

    /** Bumps with every change, so a view can tell. */
    var version = 0
        private set

    val size: Int get() = marks.size

    /** The marks of [slot], most important first. */
    fun of(slot: ConditionMarks.Slot): List<ConditionMarks.Mark> = ConditionMarks.order(marks.values.filter { it.slot == slot })

    /** All marks, no order promised (tests). */
    fun all(): List<ConditionMarks.Mark> = marks.values.toList()

    private fun id(slot: ConditionMarks.Slot, key: String) = "${slot.name}|$key"

    private fun put(slot: ConditionMarks.Slot, d: ConditionMarks.Def, value: Int): Boolean {
        val id = id(slot, d.key)
        val old = marks[id]
        if (old != null && old.value == value) return false
        if (slot == ConditionMarks.Slot.FIELD && (d.kind == ConditionMarks.Kind.WEATHER || d.kind == ConditionMarks.Kind.TERRAIN)) {
            // one weather and one terrain at a time: the newer replaces the older
            marks.values.filter { it.slot == slot && it.def.kind == d.kind && it.key != d.key }.map { it.id }.forEach { marks.remove(it) }
        }
        marks[id] = ConditionMarks.Mark(id, slot, d, value, old?.seq ?: ++seq)
        return true
    }

    /** A condition started (or its number changed). */
    fun start(slot: ConditionMarks.Slot, key: String, value: Int): Boolean {
        val d = ConditionMarks.def(key) ?: return false
        val changed = put(slot, d, value)
        if (changed) version++
        return changed
    }

    /** A condition ended. */
    fun end(slot: ConditionMarks.Slot, key: String): Boolean {
        val changed = marks.remove(id(slot, key)) != null
        if (changed) version++
        return changed
    }

    /** Nothing is shown any more (the fight is over / a new one starts). */
    fun clear(): Boolean {
        if (marks.isEmpty()) return false
        marks.clear(); version++
        return true
    }

    /** Our Pokémon was switched: its own conditions go; what our side keeps (Reflect, Tailwind ...) and the field stay. */
    fun dropOurVolatiles(): Boolean {
        val gone = marks.values.filter { it.slot == ConditionMarks.Slot.OURS && it.def.kind == ConditionMarks.Kind.VOLATILE }.map { it.id }
        gone.forEach { marks.remove(it) }
        if (gone.isNotEmpty()) version++
        return gone.isNotEmpty()
    }

    /**
     * Settles the board to the saved fight [b] (null = no fight: nothing). A mark that stays keeps its age; a new one is the newest. True = changed.
     */
    fun syncFrom(b: Battle?): Boolean {
        if (b == null) return clear()
        val want = ConditionMarks.fromBattle(b)
        val wantIds = HashSet<String>()
        var changed = false
        for ((slot, d, value) in want) {
            wantIds += id(slot, d.key)
            if (put(slot, d, value)) changed = true
        }
        val stale = marks.keys.filter { it !in wantIds }
        if (stale.isNotEmpty()) { stale.forEach { marks.remove(it) }; changed = true }
        if (changed) version++
        return changed
    }

    /**
     * An [GameEvent.Effect] played: a VOLATILE / FIELD line that starts (stages over 0) or ends (under 0) a condition moves the board, with the
     * turns taken from the saved fight [b] (the events are played after the game is done: a condition the fight no longer holds is not shown).
     * A broken Substitute and a Destiny Bond that came true (stages 0) end. True = changed.
     */
    fun applyEffect(e: GameEvent.Effect, b: Battle?): Boolean {
        if (e.kind == EffectKind.STAT_CHANGE) {
            val key = "stat.${e.stat ?: return false}"
            val slot = if (e.onPlayer) ConditionMarks.Slot.OURS else ConditionMarks.Slot.WILD
            val held = b?.let { ConditionMarks.fromBattle(it).firstOrNull { mark -> mark.first == slot && mark.second.key == key } }
            return if (held == null) end(slot, key) else start(slot, key, held.third)
        }
        val key = e.stat ?: return false
        val d = ConditionMarks.def(key) ?: return false
        if (e.kind != EffectKind.VOLATILE && e.kind != EffectKind.FIELD) return false
        val slot = when (d.kind) {
            ConditionMarks.Kind.WEATHER, ConditionMarks.Kind.TERRAIN, ConditionMarks.Kind.ROOM -> ConditionMarks.Slot.FIELD
            else -> if (e.onPlayer) ConditionMarks.Slot.OURS else ConditionMarks.Slot.WILD
        }
        val ends = e.stages < 0 || (e.stages == 0 && (key == "destinybond" || (key == "substitute" && e.label?.contains("broke") == true)))
        if (ends) return end(slot, key)
        if (e.stages <= 0) return false
        val held = if (b == null) null else ConditionMarks.fromBattle(b).firstOrNull { it.first == slot && it.second.key == key }
        if (b != null && held == null) return false   // already over in the saved fight: it never shows
        return start(slot, key, held?.third ?: 0)
    }
}

/** The sections of the conditions list (pure): one per place that has conditions, the tapped one first; a title on each only when there are several. */
object ConditionSections {
    class Section(val title: String?, val marks: List<ConditionMarks.Mark>)

    fun stat(board: ConditionBoard, slot: ConditionMarks.Slot, key: String, wildName: String, ourName: String): List<Section> {
        if (!key.startsWith("stat.")) return emptyList()
        val marks = board.of(slot).filter { it.key == key }
        if (marks.isEmpty()) return emptyList()
        val title = if (slot == ConditionMarks.Slot.OURS) "Your $ourName" else "Wild ${wildName.removePrefix("Wild ")}".trim()
        return listOf(Section(title, marks))
    }

    fun of(board: ConditionBoard, first: ConditionMarks.Slot, wildName: String, ourName: String): List<Section> {
        val order = listOf(first) + ConditionMarks.Slot.entries.filter { it != first }
        val parts = order.mapNotNull { slot -> board.of(slot).takeIf { it.isNotEmpty() }?.let { slot to it } }
        return parts.map { (slot, marks) ->
            val title = if (parts.size < 2) null else when (slot) {
                ConditionMarks.Slot.WILD -> "Wild ${wildName.removePrefix("Wild ")}".trim()
                ConditionMarks.Slot.OURS -> if (ourName.isBlank()) "You" else "Your $ourName"
                ConditionMarks.Slot.FIELD -> "The field"
            }
            Section(title, marks)
        }
    }
}

/** The one-row plan of a side's chips. */
object ConditionRow {
    /** [shown] chips of the most important and, when some are left out, a "+[more]" chip; [width] = the row's whole width. */
    class Fit(val shown: Int, val more: Int, val width: Float)

    /**
     * How many of the chips (widths [widths], in the order they show) fit in one row of [avail] px with [gap] between chips: all of them, or
     * the most that fit before a "+N" chip ([moreWidth] N = its width). No chip fits but "+N" does: just "+N". Not even that: null.
     */
    fun fit(widths: FloatArray, gap: Float, avail: Float, moreWidth: (Int) -> Float): Fit? {
        val total = widths.size
        if (total == 0) return null
        for (n in total downTo 0) {
            var w = 0f
            for (i in 0 until n) w += widths[i] + if (i > 0) gap else 0f
            val more = total - n
            if (more > 0) w += (if (n > 0) gap else 0f) + moreWidth(more)
            if (w <= avail + EPS) return Fit(n, more, w)
        }
        return null
    }

    private const val EPS = 0.01f
}

/**
 * Where the chips sit (pure, arena px). Every spot is worked out from the room that is really free: the rows sit by their own info box (the
 * wild one's under it, ours above it; the other side of the box, then beside it, when that is blocked) and the field chip in a corner (top-right,
 * bottom-right, bottom-left - the top-left corner is the card toasts' place). A spot is never over an obstacle (the boxes, the sprites' pictures,
 * the NEW sticker, the frame's corner squares, another row) or outside the frame's safe area; with too little room the row shows fewer chips
 * and a "+N", with none at all nothing shows (the box's tap still lists everything).
 */
internal object ConditionSpot {
    class R(val l: Float, val t: Float, val r: Float, val b: Float) {
        val w: Float get() = r - l
        val h: Float get() = b - t
        fun hits(o: R) = l < o.r - EPS && o.l < r - EPS && t < o.b - EPS && o.t < b - EPS
        fun grow(m: Float) = R(l - m, t - m, r + m, b + m)
        fun inside(o: R) = l >= o.l - EPS && t >= o.t - EPS && r <= o.r + EPS && b <= o.b + EPS
    }

    /** A chosen spot: the row's rectangle (exactly as wide as its chips), how many chips it shows and how many are left for "+N". */
    class Placed(val rect: R, val fit: ConditionRow.Fit, val leftAligned: Boolean)

    /** The arena [w] x [h] px, its frame's safe area ([safe], px) and what every spot keeps clear of ([obstacles], already padded). */
    class Scene(val w: Float, val h: Float, val safe: FrameSafe?, val obstacles: List<R>, val inset: Float = 0f) {
        /** The room inside the frame's bands (and [inset] px in from the arena's edge, so a chip never touches it). */
        val area: R = (safe?.takeIf { !it.none }?.let { R(it.edge, it.top, w - it.edge, h - it.edge) } ?: R(0f, 0f, w, h)).let { R(it.l + inset, it.t + inset, it.r - inset, it.b - inset) }

        /** All obstacles incl. the frame's four corner squares. */
        val blocks: List<R> = obstacles + (safe?.takeIf { it.corner > 0f }?.let { c ->
            listOf(R(0f, 0f, c.corner, c.corner), R(w - c.corner, 0f, w, c.corner), R(0f, h - c.corner, c.corner, h), R(w - c.corner, h - c.corner, w, h))
        } ?: emptyList())
    }

    /**
     * Room for a row of height [rowH] whose first edge is at [x] and which grows toward [dir] (+1 right, -1 left): the width up to the next
     * obstacle (or the room's edge), at most [cap]; 0 when [x] itself is covered.
     */
    private fun span(scene: Scene, taken: List<R>, y: Float, rowH: Float, x: Float, dir: Int, cap: Float): Float {
        val band = R(0f, y, scene.w, y + rowH)
        var limit = if (dir > 0) scene.area.r else scene.area.l
        if (dir > 0 && x >= limit - EPS || dir < 0 && x <= limit + EPS) return 0f
        if (x < scene.area.l - EPS || x > scene.area.r + EPS) return 0f
        for (o in scene.blocks + taken) {
            if (!(o.t < band.b - EPS && band.t < o.b - EPS)) continue
            if (dir > 0) {
                if (o.l < x - EPS && o.r > x + EPS) return 0f
                if (o.l >= x - EPS) limit = min(limit, o.l)
            } else {
                if (o.l < x - EPS && o.r > x + EPS) return 0f
                if (o.r <= x + EPS) limit = max(limit, o.r)
            }
        }
        return min(cap, if (dir > 0) limit - x else x - limit).coerceAtLeast(0f)
    }

    private fun candidate(scene: Scene, taken: List<R>, widths: FloatArray, gap: Float, moreW: (Int) -> Float, y: Float, rowH: Float, x: Float, dir: Int, cap: Float): Placed? {
        if (y < scene.area.t - EPS || y + rowH > scene.area.b + EPS) return null
        val room = span(scene, taken, y, rowH, x, dir, cap)
        if (room <= 0f) return null
        val fit = ConditionRow.fit(widths, gap, room, moreW) ?: return null
        val rect = if (dir > 0) R(x, y, x + fit.width, y + rowH) else R(x - fit.width, y, x, y + rowH)
        return Placed(rect, fit, dir > 0)
    }

    /** The best of [cands] in priority order: the first that shows every chip, else the one that shows most (the earlier one wins ties). */
    private fun best(cands: Sequence<Placed?>, total: Int): Placed? {
        var best: Placed? = null
        for (c in cands) {
            if (c == null) continue
            if (c.fit.more == 0 && c.fit.shown == total) return c
            if (best == null || c.fit.shown > best.fit.shown) best = c
        }
        return best
    }

    /**
     * The row of a side's chips by its info box [card]: the wild one's [below] it first, ours above it first. Each side of the box is tried left-
     * aligned with the box (growing right) and right-aligned (growing left); then beside the box. [gap] = between chips, [lift] = between the
     * row and the box. [taken] = rows already placed.
     */
    fun side(scene: Scene, card: R, below: Boolean, widths: FloatArray, gap: Float, lift: Float, moreW: (Int) -> Float, rowH: Float, taken: List<R> = emptyList(), maxWidth: Float = Float.MAX_VALUE): Placed? {
        if (widths.isEmpty()) return null
        val cap = min(maxWidth, max(card.w * CAP_OF_CARD, rowH * CAP_ROW_HEIGHTS))
        val under = card.b + lift
        val over = card.t - lift - rowH
        val ys = if (below) listOf(under, over) else listOf(over, under)
        val left = max(card.l, scene.area.l)
        val right = min(card.r, scene.area.r)
        val total = widths.size
        return best(sequence {
            for (y in ys) { yield(candidate(scene, taken, widths, gap, moreW, y, rowH, left, +1, cap)); yield(candidate(scene, taken, widths, gap, moreW, y, rowH, right, -1, cap)) }
            // beside the box, level with its edge that is away from the Pokémon first (the wild one's bottom edge, ours' top edge), then the other edge
            for (bY in if (below) listOf(card.b - rowH, card.t) else listOf(card.t, card.b - rowH)) {
                yield(candidate(scene, taken, widths, gap, moreW, bY, rowH, card.r + lift, +1, cap))
                yield(candidate(scene, taken, widths, gap, moreW, bY, rowH, card.l - lift, -1, cap))
            }
        }, total)
    }

    /**
     * The field's chips: in a corner first (top-right, bottom-right, bottom-left; never the top-left one, which is the toasts'), else along the
     * top edge (never at its left end, the toasts' place) or the bottom edge, next to whatever is in the way, the spot nearest the right edge
     * first. [margin] px from the room's edge. The weather belongs to no box, so any free spot will do.
     */
    fun corner(scene: Scene, widths: FloatArray, gap: Float, moreW: (Int) -> Float, rowH: Float, margin: Float, taken: List<R> = emptyList(), maxWidth: Float = Float.MAX_VALUE): Placed? {
        if (widths.isEmpty()) return null
        val a = scene.area
        val cap = min(maxWidth, a.w * CAP_FIELD_OF_AREA)
        val top = a.t + margin
        val bottom = a.b - margin - rowH
        val toastEnd = a.l + a.w * TOASTS_SHARE
        return best(sequence {
            yield(candidate(scene, taken, widths, gap, moreW, top, rowH, a.r - margin, -1, cap))
            yield(candidate(scene, taken, widths, gap, moreW, bottom, rowH, a.r - margin, -1, cap))
            yield(candidate(scene, taken, widths, gap, moreW, bottom, rowH, a.l + margin, +1, cap))
            val anchors = (scene.blocks + taken).flatMap { o -> listOf((o.l - margin) to -1, (o.r + margin) to +1) }.sortedBy { abs(it.first - a.r) }
            for (y in listOf(top, bottom)) for ((x, dir) in anchors) {
                val p = candidate(scene, taken, widths, gap, moreW, y, rowH, x, dir, cap)
                if (p != null && (y != top || p.rect.l >= toastEnd)) yield(p)
            }
        }, widths.size)
    }

    /** Sizes a placement needs (px): [gap] between chips, [lift] between a row and its box, [rowH] a row's height, [moreW] the width of "+N". */
    class Sizes(val gap: Float, val lift: Float, val rowH: Float, val moreW: (Int) -> Float)

    /** The three rows of the arena. */
    class All(val wild: Placed?, val ours: Placed?, val field: Placed?)

    /**
     * All three rows, one after the other, each keeping clear of the rows placed before it: the wild one's under its box ([wildCard] null = the
     * wild side is not on stage), ours above its box, the field's in a corner. [wild] / [ours] / [field] = the widths of the chips of each, in
     * the order they show (empty = no row).
     */
    fun all(scene: Scene, wildCard: R?, ourCard: R?, wild: FloatArray, ours: FloatArray, field: FloatArray, z: Sizes): All {
        val taken = ArrayList<R>()
        fun keep(p: Placed?): Placed? { p?.let { taken += it.rect.grow(z.lift * 0.5f) }; return p }
        val w = if (wildCard == null) null else keep(side(scene, wildCard, true, wild, z.gap, z.lift, z.moreW, z.rowH, taken))
        val o = if (ourCard == null) null else keep(side(scene, ourCard, true, ours, z.gap, z.lift, z.moreW, z.rowH, taken))
        val f = keep(corner(scene, field, z.gap, z.moreW, z.rowH, z.lift * 2f, taken))
        return All(w, o, f)
    }

    /** A row may be as wide as its box (or this many row heights, for a narrow box) times this much. */
    private const val CAP_OF_CARD = 1.7f
    private const val CAP_ROW_HEIGHTS = 12f
    private const val CAP_FIELD_OF_AREA = 0.4f

    /** The part of the top edge, from the left, that the card toasts use: a field chip along the top edge starts after it. */
    private const val TOASTS_SHARE = 0.4f
    private const val EPS = 0.01f
}

/** Stats first, then effects; each mark has its own chip. */
internal object ConditionSummary {
    fun groups(marks: List<ConditionMarks.Mark>): List<List<ConditionMarks.Mark>> {
        val (stages, others) = marks.partition { it.key.startsWith("stat.") }
        return (stages + others).map { listOf(it) }
    }

    fun textSp(widthDp: Float, heightDp: Float, panelScale: Float, floorSp: Float): Float {
        val scale = min(widthDp * panelScale / 360f, heightDp * panelScale / 604f).coerceIn(0f, 1f)
        return max(floorSp, max(8f, 10.5f * scale) / panelScale.coerceAtLeast(0.01f))
    }
}
