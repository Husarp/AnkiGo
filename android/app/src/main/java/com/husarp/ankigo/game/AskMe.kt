package com.husarp.ankigo.game

/**
 * R19-P (PLAN 281): the catch option "Ask me" and the safe replacing of a copy. Pure rules, no Android.
 *
 * "Ask me" ([CatchChoice.ASK_ME]) is "Catch only new species" with one change: a beaten wild Pokémon of a species you OWN that is
 * [better] than your copies is not defeated at once; the fight counts as won (the same XP and coins, the Defeated event) and the question
 * waits in [GameState.asks] until the player answers: [AskChoice.CATCH] (keep both), [AskChoice.REPLACE] (catch and let the best copy
 * go) or [AskChoice.DONT] (stays defeated). A new species, a shiny, a protected kind and a wishlisted species are caught as always;
 * a wild one that is not better is defeated as always; the one-off Catch / Defeat tap and ball throws skip the question (they are the
 * player's own choice).
 *
 * "Better" (decided and documented here): the wild one has a HIGHER LEVEL than every copy you own, OR a HIGHER IV TOTAL (the six IVs
 * added up, [Stats.total], max 186) than every copy you own. Equal is not better. Nature, gender and moves do not count.
 */
object AskMe {
    /** Questions kept at most (each is a small Pokémon in the save). A longer queue drops the OLDEST: a catch question then counts as "Don't catch", a release question as "keep both". Nothing is ever caught or released unasked. */
    const val MAX_WAITING = 12

    /**
     * How a wild copy beats what is owned. [yours] = the copy it is compared with and the one "Catch and replace" lets go: the best copy
     * by level (ties: higher IV total) when the wild one is better by level, else the copy with the best IV total (ties: higher level).
     */
    class Better(val byLevel: Boolean, val byIvs: Boolean, val yours: Mon)

    /** Null when [wild] is not better than any owned copy of its species, or none is owned. */
    fun better(state: GameState, wild: Mon): Better? {
        val owned = state.mons.filter { it.species == wild.species }
        if (owned.isEmpty()) return null
        val byLevel = wild.level > owned.maxOf { it.level }
        val byIvs = wild.ivs.total > owned.maxOf { it.ivs.total }
        if (!byLevel && !byIvs) return null
        val yours = owned.sortedWith(if (byLevel) LEVEL_FIRST else IVS_FIRST).first()
        return Better(byLevel, byIvs, yours)
    }

    private val LEVEL_FIRST: Comparator<Mon> = compareByDescending<Mon> { it.level }.thenByDescending { it.ivs.total }.then(OLDEST_THEN_UID)
    private val IVS_FIRST: Comparator<Mon> = compareByDescending<Mon> { it.ivs.total }.thenByDescending { it.level }.then(OLDEST_THEN_UID)
    private val OLDEST_THEN_UID: Comparator<Mon> get() = compareBy<Mon> { if (it.caughtAt > 0) it.caughtAt else Long.MAX_VALUE }.thenBy { it.uid }

    /** What an old copy holds that the new one can take over: its Team slot (0 = the main, -1 = not in the team), the favourite heart, the Exp. Share. */
    class Place(val teamSlot: Int, val favourite: Boolean, val expShare: Boolean) {
        val inTeam: Boolean get() = teamSlot >= 0
        val any: Boolean get() = inTeam || favourite || expShare
    }

    fun placeOf(state: GameState, mon: Mon): Place =
        Place(state.team.indexOf(mon.uid), mon.favourite, state.settings.xpShareUid == mon.uid)

    /**
     * May [old] be released by a replace at all? Never a shiny or a protected kind (the same copies [Duplicates] never lets go), whatever
     * the player taps. Anything else may, a held item goes to the Bag like in any release.
     */
    fun canReplace(data: GameData, state: GameState, old: Mon): Boolean =
        !old.shiny && !Catching.isProtected(state.settings, data.sp(old.species), old.tier)

    /**
     * R19-FIX (CX-1): is [new] still the better copy [old] was asked about? A release question waits (companion / PiP play, a closed app),
     * so both Pokémon may have changed since: the same species still (an evolved one is another species), [new] still [betterThan] [old],
     * and [old] may still go ([canReplace]). Compared as a pair, so the new copy itself is never one of the "owned copies". Stale = both stay.
     */
    fun stillReplaces(data: GameData, state: GameState, new: Mon, old: Mon): Boolean =
        new.uid != old.uid && new.species == old.species && betterThan(new, old) && canReplace(data, state, old)

    /** [new] beats the single copy [old] by [better]'s rule: a higher level or a higher IV total (equal is not better). */
    fun betterThan(new: Mon, old: Mon): Boolean = new.level > old.level || new.ivs.total > old.ivs.total

    /** What a stale release question says (a toast / the answer): nothing was released. */
    const val STALE = "Kept both: your Pokémon changed since."

    /** True when releasing [old] needs the 2B window: it holds a place ([placeOf]) and may go ([canReplace]). */
    fun needsWindow(data: GameData, state: GameState, old: Mon): Boolean = canReplace(data, state, old) && placeOf(state, old).any

    /** The question to show first: a release question (the Pokémon is caught already) before a catch question; the oldest first. Null = nothing waits. */
    fun next(state: GameState): PendingAsk? =
        state.asks.firstOrNull { it.kind == PendingAsk.Kind.REPLACE } ?: state.asks.firstOrNull()
}

/** The three answers of a catch question ([PendingAsk.Kind.CATCH]). */
enum class AskChoice { CATCH, REPLACE, DONT }

/**
 * One question waiting for the player, saved with the game (an old save has none). CATCH: [wild] is the beaten wild Pokémon, not in the
 * collection yet (its fight is over and paid); [uid] = its uid. REPLACE: the better copy [uid] is caught; [oldUid] may go. [at] = when it
 * was asked (epoch ms). A question is answered exactly once: the answer removes it first.
 */
class PendingAsk(
    val kind: Kind,
    val uid: String,
    val wild: Mon? = null,
    val tier: Tier = Tier.NORMAL,
    val oldUid: String? = null,
    val at: Long = 0,
) {
    enum class Kind { CATCH, REPLACE }
}
