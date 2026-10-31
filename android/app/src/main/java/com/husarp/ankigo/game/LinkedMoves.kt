package com.husarp.ankigo.game

/** Optional own-move synergies inventoried by lane c; no battle effects change. */
object LinkedMoves {
    val empowering = setOf("defensecurl", "charge", "stockpile")

    fun empowers(source: String, target: Move): Boolean = when (source) {
        "defensecurl" -> target.id in setOf("rollout", "iceball")
        "charge" -> target.id != source && target.damaging && target.type == Type.ELECTRIC
        "stockpile" -> target.id in setOf("swallow", "spitup")
        else -> false
    }

    /** Only called for freshly generated movesets, never on save loading or level-up. */
    fun generated(data: GameData, sp: Species, initial: List<String>): List<String> {
        val moves = initial.toMutableList()
        val learnable = (sp.levelMoves.map { it.move } + sp.tmMoves.sorted()).distinct()
            .mapNotNull(data::move).filter { CoreRules.neverPickedText(it.id) == null }
        for (source in initial.filter { it in empowering }) {
            if (source !in moves || moves.any { data.move(it)?.let { m -> empowers(source, m) } == true }) continue
            val partner = learnable.firstOrNull { empowers(source, it) }
            if (partner != null) {
                if (moves.size < 4) moves += partner.id
                else {
                    // Preserve all empowering moves and the partners already secured for them.
                    val slot = moves.indexOfLast { id -> id !in empowering &&
                        moves.none { s -> s in empowering && data.move(id)?.let { empowers(s, it) } == true } }
                    if (slot >= 0) moves[slot] = partner.id
                    else moves[moves.indexOf(source)] = partner.id
                }
            } else {
                val replacement = learnable.firstOrNull { it.id !in moves && it.id !in empowering }
                val slot = moves.indexOf(source)
                if (replacement != null) moves[slot] = replacement.id else moves.removeAt(slot)
            }
        }
        return moves.ifEmpty { listOf(Tuning.FALLBACK_MOVE) }
    }

    /** Names the other moves losing their pairing; another remaining partner prevents a warning. */
    fun replacementWarning(data: GameData, ids: List<String>, index: Int, incoming: String): String? {
        val old = ids.mapNotNull(data::move)
        val after = ids.toMutableList().apply { this[index] = incoming }.mapNotNull(data::move)
        val removed = data.move(ids[index]) ?: return null
        val affected = old.filter { other -> other.id != removed.id && after.any { it.id == other.id } &&
            ((empowers(other.id, removed) && after.none { empowers(other.id, it) }) ||
                (empowers(removed.id, other) && after.none { empowers(it.id, other) })) }
        if (affected.isEmpty()) return null
        return "Forgetting ${removed.name} breaks its linked-move combination. ${affected.joinToString(" and ") { it.name }} becomes useless for that combination. Teach anyway?"
    }
}
