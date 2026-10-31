package com.husarp.ankigo.game

/**
 * R9-B6: what a move does in AnkiGo's battle, in plain words. Built only from the fields the battle engine really uses (Showdown's
 * own texts are not in the data we download): no crits (they are off), no "secondary" on status moves, and so on.
 * Pure (no android.*). Every move gets at least one sentence, each ending in a full stop.
 * R17C-10 order of the sentences: what the move does (damage rule, hits, stats, drain / recoil, secondaries), then the charge sentence and
 * what the rule sets add (so "Charges ..., then hits ..." comes before "In harsh sunlight it hits at once"), then priority, then accuracy.
 */
object MoveText {
    /** The engine's own "this move targets the user" list (BattleEngine.SELF_TARGETS). */
    private val SELF_TARGETS = setOf("self", "adjacentAllyOrSelf", "allies", "allySide")
    private val BOOST_KEYS = listOf("atk", "def", "spa", "spd", "spe", "accuracy", "evasion")
    private val BOOST_LABELS = listOf("Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed", "accuracy", "evasiveness")

    /** Status verbs, {} = who gets it: "Burns {}" on its own, "burn {}" after "May". */
    private val STATUS_THIRD = mapOf(
        "par" to "Paralyzes {}", "brn" to "Burns {}", "psn" to "Poisons {}", "tox" to "Badly poisons {}",
        "slp" to "Puts {} to sleep", "frz" to "Freezes {}",
    )
    private val STATUS_BASE = mapOf(
        "par" to "paralyze {}", "brn" to "burn {}", "psn" to "poison {}", "tox" to "badly poison {}",
        "slp" to "put {} to sleep", "frz" to "freeze {}",
    )

    /** True when a "power" number means something: a damaging move with power and no fixed / level / half-HP / one-hit-KO rule. */
    fun showsPower(m: Move): Boolean =
        m.damaging && m.power > 0 && !m.levelDamage && m.fixedDamage == null && !m.halfHp && !m.ohko && m.id !in MovePower.VARIABLE_POWER

    fun describe(m: Move, knownMoves: List<Move>? = null): String {
        CoreRules.neverPickedText(m.id)?.let { return it } // R17C-3: a move that means nothing in a 1-vs-1 wild fight says why (it is never picked)
        RemainingMoves.texts[m.id]?.let { text ->
            return text + when {
                m.priority > 0 -> " Usually goes first."
                m.priority < 0 -> " Usually goes last."
                else -> ""
            }
        }
        val lines = ArrayList<String>()   // what the move does
        val extras = ArrayList<String>()  // when it goes, whether it can miss, charging
        val ruleLines = BattleRules.ALL.flatMap { it.describe(m) }.mapNotNull { contextualLine(m, it, knownMoves) }.map(StageText::explain) // R17C-1: what the rule sets add, after the engine's own lines

        if (m.damaging) {
            val rule = when {
                m.levelDamage -> "Deals damage equal to its level."
                m.fixedDamage != null -> "Always deals ${m.fixedDamage} damage."
                m.halfHp -> "Takes off half of the target's current HP."
                m.ohko -> "Knocks the target out in one hit, but fails against a higher-level target."
                else -> null
            }
            if (rule != null) lines += rule
            m.multihit?.let { r ->
                lines += if (r.first == r.last) "Hits ${r.first} times." else "Hits ${r.first}–${r.last} times."
            }
            // R17C-1: which stats the damage formula uses
            if (m.defensiveStat == "def") lines += "Hits the target's Defense instead of its Sp. Def."
            if (m.offensiveStat == "def") lines += "Uses its Defense instead of its Attack."
            if (m.offensiveFromTarget) lines += "Uses the target's Attack instead of its own."
            if (m.ignoreDefensive || m.ignoreEvasion) lines += "Ignores the target's stat changes."
            m.drain?.let { (a, b) -> lines += "Heals itself by ${part(a, b)} the damage dealt." }
            m.recoil?.let { (a, b) -> lines += "It takes ${part(a, b)} the damage dealt as recoil." }
            if (m.selfDestruct) lines += if (m.selfDestructIfHit) "It faints after using it if it hits." else "It faints after using it." // R17C FIX-A: Final Gambit needs a hit
            m.selfBoosts?.let { lines += boostSentences(it, "its", "Raises", "Lowers") }
            for (sec in m.allSecondaries) lines += secondary(sec) // R17C-3: a second secondary (the fangs) is rolled too
            chargeSentence(m)?.let { lines += it }
            lines += ruleLines
        } else {
            val self = m.target in SELF_TARGETS
            val who = if (self) "itself" else "the target"
            val whose = if (self) "its" else "the target's"
            m.status?.let { s -> STATUS_THIRD[s]?.let { lines += it.replace("{}", who) + "." } }
            m.boosts?.let { lines += boostSentences(it, whose, "Raises", "Lowers") }
            m.selfBoosts?.let { lines += boostSentences(it, "its", "Raises", "Lowers") }
            m.heal?.let { (a, b) -> lines += "Heals itself by ${part(a, b)} its max HP." }
            if (m.id in Tuning.POWDER_MOVES) lines += "Fails against Grass types."
            chargeSentence(m)?.let { lines += it }
            lines += ruleLines
            // the engine does nothing else with a status move: "But nothing happened."
            if (lines.isEmpty()) return "Has no effect in AnkiGo battles."
        }

        if (m.priority > 0) extras += "Usually goes first."
        else if (m.priority < 0) extras += "Usually goes last."
        // a one-hit KO ignores its accuracy (the engine uses a level rule). R17C-10: a status move says "Never misses." only when it changes the
        // target's status or stats (a field move, a swap or a copy aims at no Pokémon's luck: the sentence would only confuse)
        if (m.accuracy == null && !m.ohko && (m.damaging || (m.target !in SELF_TARGETS && (m.status != null || m.boosts != null)))) extras += "Never misses."

        // a damaging move with nothing else to say about it (when priority / never-misses / charging follow, that is enough)
        if (m.damaging && lines.isEmpty() && extras.isEmpty()) lines += "Deals damage with no extra effect."
        return (lines + extras).joinToString(" ")
    }

    /**
     * Only hide optional synergies of the user's own moves. Target-dependent interactions and prerequisites
     * (such as Swallow needing stockpile) remain visible. Null means a generic Dex/TM description.
     */
    private fun contextualLine(m: Move, line: String, known: List<Move>?): String? {
        if (known == null) return line
        val ids = known.map { it.id }.toSet()
        return when (m.id) {
            "defensecurl" -> if (ids.any { it == "rollout" || it == "iceball" })
                line.replace("Rollout and Ice Ball deal", linkedSubject(known.filter { LinkedMoves.empowers(m.id, it) }, "deal", "deals")) else null
            "charge" -> if (known.any { LinkedMoves.empowers(m.id, it) })
                "Its next Electric attack (${join(known.filter { LinkedMoves.empowers(m.id, it) }.map { it.name })}) deals double damage." else null
            "stockpile" -> if (ids.any { it == "swallow" || it == "spitup" })
                line.replace("Swallow and Spit Up use", linkedSubject(known.filter { LinkedMoves.empowers(m.id, it) }, "use", "uses")).let { if (known.count { LinkedMoves.empowers(m.id, it) } == 1) it.replace("and take the rises", "and takes the rises") else it }
                else line.substringBefore(" Swallow")
            "rollout", "iceball" -> if ("defensecurl" in ids) line
                else line.replace(", and double again after Defense Curl", "")
            else -> line
        }
    }

    private fun linkedSubject(moves: List<Move>, plural: String, singular: String): String =
        join(moves.map { it.name }) + " " + if (moves.size == 1) singular else plural

    /** R17C-3 (PLAN 267): the charge turn, then the user's very next move is the hit (or the effect) by itself; null for a move that doesn't charge. */
    private fun chargeSentence(m: Move): String? {
        if (!CoreRules.isCharge(m)) return null
        val rise = Tuning.CHARGE_TURN_RISE[m.id]?.let { boostSentences(it, "its", "raises", "lowers").firstOrNull() }?.removeSuffix(".")
        val then = if (m.damaging) "hits" else "takes effect"
        return "Charges on the first turn" + (rise?.let { " and $it" } ?: "") + ", then $then with its next move."
    }

    /** "half", "a third of", "a quarter of", "three quarters of" (fraction reduced); anything else "33 % of". Read it before "the damage". */
    internal fun part(a: Int, b: Int): String {
        if (a <= 0 || b <= 0) return "none of"
        val g = gcd(a, b)
        return when (a / g to b / g) {
            1 to 2 -> "half"
            1 to 3 -> "a third of"
            2 to 3 -> "two thirds of"
            1 to 4 -> "a quarter of"
            3 to 4 -> "three quarters of"
            1 to 8 -> "an eighth of"
            else -> "${Math.round(a * 100.0 / b)} % of"
        }
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    /** " (10 %)" for a chance below 100; "" for a sure effect. */
    private fun chanceTag(chance: Int) = if (chance < 100) " ($chance %)" else ""

    private fun secondary(s: Secondary): List<String> {
        if (s.chance <= 0) return emptyList()
        val may = s.chance < 100
        val tag = chanceTag(s.chance)
        val out = ArrayList<String>()
        s.status?.let { st ->
            val text = (if (may) STATUS_BASE else STATUS_THIRD)[st]?.replace("{}", "the target")
            if (text != null) out += (if (may) "May $text" else text) + "$tag."
        }
        s.boosts?.let { out += boostSentences(it, "the target's", if (may) "May raise" else "Raises", if (may) "May lower" else "Lowers", tag) }
        s.selfBoosts?.let { out += boostSentences(it, "its", if (may) "May raise" else "Raises", if (may) "May lower" else "Lowers", tag) }
        return out
    }

    /**
     * "Raises its Attack and Speed by 1 and Defense by 2." (stats with the same amount share a "by"); one sentence for the rises and
     * one for the drops. Keys the engine does not know are skipped, like the engine does.
     */
    private fun boostSentences(map: Map<String, Int>, whose: String, up: String, down: String, tag: String = ""): List<String> {
        fun one(verb: String, sign: Int): String? {
            val byAmount = LinkedHashMap<Pair<Int, Boolean>, MutableList<String>>()
            for ((i, key) in BOOST_KEYS.withIndex()) {
                val v = map[key] ?: continue
                if (v == 0 || (v > 0) != (sign > 0)) continue
                byAmount.getOrPut(Math.abs(v) to (key == "accuracy" || key == "evasion")) { ArrayList() } += BOOST_LABELS[i]
            }
            if (byAmount.isEmpty()) return null
            val groups = byAmount.entries.map { (n, labels) -> "${join(labels)} by ${StageText.amount(n.first * sign, n.second)}" }
            return "$verb $whose ${join(groups)}$tag."
        }
        return listOfNotNull(one(up, 1), one(down, -1))
    }

    /** "A", "A and B", "A, B and C". */
    private fun join(items: List<String>): String =
        if (items.size <= 1) items.joinToString("") else items.dropLast(1).joinToString(", ") + " and " + items.last()
}
