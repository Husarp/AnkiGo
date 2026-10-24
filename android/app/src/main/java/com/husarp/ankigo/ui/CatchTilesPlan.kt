package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameEvent

/**
 * What the catch result tiles say and when they show (PLAN 40, pick 8A; pure, the drawing is [CatchTilesView]).
 * The texts come only from a [CatchSummary], so every number is a real one.
 */
object CatchTilesPlan {
    enum class Kind { DEX, DEFEATED, THROW, AUTO, XP, COINS }

    /** [label] = the small line above, [value] = the big one, [extra] = the grey tail after it ("· 12 %"); [ball] = the item id for the ball picture. */
    data class Tile(val kind: Kind, val label: String, val value: String, val extra: String = "", val ball: String? = null)

    /** Most XP tiles drawn one by one; more Pokémon than this share one "N Pokémon" tile. */
    const val MAX_XP_TILES = 2

    /** How long the tiles stay in a small window (split strip, floating battle, picture-in-picture). */
    const val SMALL_MS = 4000L

    /** The tiles of the big layout, in order: Pokédex (or Defeated), the throw, XP, coins. [ballName] turns a ball id into its name. */
    fun tiles(s: CatchSummary, ballName: (String) -> String): List<Tile> {
        val out = ArrayList<Tile>()
        if (s.caught) {
            s.pokedex?.let { out += Tile(Kind.DEX, "Pokédex", it.text) }
            val t = s.throwInfo
            out += if (t != null) Tile(Kind.THROW, ballName(t.ball), "Throw ${t.number}", "· ${t.chance} %", t.ball)
            else Tile(Kind.AUTO, "Catch", "Caught automatically")
        } else {
            out += Tile(Kind.DEFEATED, "Defeated", s.name)
        }
        if (s.xp.size <= MAX_XP_TILES) {
            for (x in s.xp) out += Tile(Kind.XP, x.name, "+${x.xp} XP", if (x.leveledUp) "· Lv ${x.level}" else "")
        } else {
            out += Tile(Kind.XP, "${s.xp.size} Pokémon", "+${s.xp.sumOf { it.xp }} XP")
        }
        if (s.coins > 0) out += Tile(Kind.COINS, "Coins", "+${s.coins}")
        return out
    }

    /** The one-line layout's tiles (small windows, short panels): short values, all the XP in one. */
    fun rowTiles(s: CatchSummary): List<Tile> {
        val out = ArrayList<Tile>()
        if (s.caught) {
            s.pokedex?.let { out += Tile(Kind.DEX, "", if (it.isNew) "New #${it.number}" else "Caught before") }
            val t = s.throwInfo
            out += if (t != null) Tile(Kind.THROW, "", "Throw ${t.number} · ${t.chance} %", ball = t.ball) else Tile(Kind.AUTO, "", "Auto")
        } else {
            out += Tile(Kind.DEFEATED, "", "Defeated")
        }
        if (s.xp.isNotEmpty()) out += Tile(Kind.XP, "", "+${s.xp.sumOf { it.xp }} XP")
        if (s.coins > 0) out += Tile(Kind.COINS, "", "+${s.coins}")
        return out
    }

    /** What the panel does with a result. */
    enum class Mode {
        /** Nothing: no room (the log is stepped out) or an answer is already waiting (fast play). */
        NONE,
        /** Shown until the next answer starts the next fight; the next wild one stays off the stage meanwhile. */
        HOLD,
        /** Shown for [SMALL_MS], then the normal panel returns; the next wild one appears at once. */
        TIMED,
    }

    /** [full] = the normal panel (not picture-in-picture, a floating window or the study strip). */
    fun mode(full: Boolean, logShown: Boolean, answerWaiting: Boolean): Mode = when {
        !logShown || answerWaiting -> Mode.NONE
        full -> Mode.HOLD
        else -> Mode.TIMED
    }

    /** The big (two columns) layout needs a tall panel; shorter ones, and every small window, use the one-line layout. */
    fun grid(full: Boolean, virtualHeightDp: Float): Boolean = full && virtualHeightDp >= GRID_MIN_DP

    const val GRID_MIN_DP = 600f

    /**
     * Where in the batch [events] the result goes: right before its trailing Encounter (the next fight, which waits for the next
     * answer), else at the end. [events] must contain the end of a fight.
     */
    fun resultIndex(events: List<GameEvent>): Int {
        val end = events.indexOfLast { it is GameEvent.Caught || it is GameEvent.Defeated }
        val enc = (end + 1 until events.size).firstOrNull { events[it] is GameEvent.Encounter }
        return enc ?: events.size
    }
}
