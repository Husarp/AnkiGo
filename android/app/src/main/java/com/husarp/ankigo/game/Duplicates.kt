package com.husarp.ankigo.game

/**
 * "Auto-release duplicates" ([Settings.autoReleaseDuplicates], off by default; R3-X2 item 4). Pure rules, no Android.
 *
 * For a wild Pokémon of a species the player already owns, when the setting is on and the auto rule decides (no one-off
 * tap on this fight): if its level is NOT higher than the best owned copy it is defeated instead of caught; if it IS
 * higher it is caught and that best copy is released (a swap). A copy the player may care about is never released
 * ([keepReason]): then both are kept. A wild shiny, protected kind or wishlisted species is never turned into a defeat
 * (the normal catch rules keep it). Ball throws and the one-off Catch/Defeat tap are the player's own choice and skip this.
 */
object Duplicates {
    /** The toast when nothing the player confirmed can still go (the Pokémon changed while the dialog was open). */
    const val CHANGED_NOTHING = "Nothing released — your Pokémon changed"

    /** Why a copy must never be auto-released. */
    enum class Keep(val label: String) {
        FAVOURITE("a favourite"), SHINY("shiny"), TEAM("in your team"), HOLDS_ITEM("holding an item"),
        PROTECTED_KIND("a protected kind"), EXP_SHARE("the Exp. Share holder"), NICKNAME("nicknamed"),
    }

    /**
     * What the rule says about one wild Pokémon of an owned species: [higher] = it beats the best owned copy
     * ([bestLevel]); [release] = the copy to let go after the catch (null: none may go, so both are kept; [kept] says why).
     */
    class Verdict(val higher: Boolean, val bestLevel: Int, val release: Mon?, val kept: Keep?)

    /** Null when the rule does not apply (setting off, or no copy of the wild one's species is owned). */
    fun verdict(data: GameData, state: GameState, wild: Mon): Verdict? {
        if (!state.settings.autoReleaseDuplicates) return null
        val owned = state.mons.filter { it.species == wild.species }
        if (owned.isEmpty()) return null
        val best = owned.maxOf { it.level }
        if (wild.level <= best) return Verdict(false, best, null, null)
        // Only a best-level copy is the one being replaced; if none of them may go, nothing is released.
        val top = owned.filter { it.level == best }
        val target = top.firstOrNull { keepReason(data, state, it) == null }
        return Verdict(true, best, target, if (target == null) keepReason(data, state, top.first()) else null)
    }

    /** Why [mon] must be kept, or null when the rule may release it. The first matching reason wins. */
    fun keepReason(data: GameData, state: GameState, mon: Mon): Keep? = when {
        mon.favourite -> Keep.FAVOURITE
        mon.shiny -> Keep.SHINY
        mon.uid in state.team -> Keep.TEAM
        mon.heldItem != null -> Keep.HOLDS_ITEM
        Catching.isProtected(state.settings, data.sp(mon.species), mon.tier) -> Keep.PROTECTED_KIND
        state.settings.xpShareUid == mon.uid -> Keep.EXP_SHARE
        !mon.nickname.isNullOrBlank() -> Keep.NICKNAME
        else -> null
    }

    /**
     * R10 B21 "Release duplicates": for each species (a regional form has its own species number, so it is its own group) with 2+ copies,
     * the best copy stays (highest level; tie: higher IV total; tie: caught earliest, a copy without a date after the dated ones; tie: uid)
     * and every other copy is to be released EXCEPT a copy [keepReason] protects (favourite, shiny, team member, item holder, protected
     * kind, Exp. Share holder, nickname). The keeper is never in the plan, so no species loses its last copy; the main and the team are
     * protected, so they never change. A species with one copy is not looked at. Pure: never changes [state].
     */
    fun releasePlan(data: GameData, state: GameState): ReleasePlan {
        val release = ArrayList<String>()
        val groups = ArrayList<ReleasePlan.Group>()
        val kept = java.util.EnumMap<Keep, Int>(Keep::class.java)
        for ((num, copies) in state.mons.groupBy { it.species }.toSortedMap()) {
            if (copies.size < 2) continue
            var n = 0
            for (mon in copies.sortedWith(KEEPER_FIRST).drop(1)) { // the first is the keeper
                val why = keepReason(data, state, mon)
                if (why != null) kept[why] = (kept[why] ?: 0) + 1 else { release += mon.uid; n++ }
            }
            if (n > 0) groups += ReleasePlan.Group(num, data.sp(num).name, n)
        }
        groups.sortWith(compareByDescending<ReleasePlan.Group> { it.count }.thenBy { it.name }.thenBy { it.species })
        return ReleasePlan(release, groups, kept)
    }

    /** Best copy first: level, then IV total, then the earliest catch (no date = last), then uid, so the choice never depends on list order. */
    private val KEEPER_FIRST: Comparator<Mon> = compareByDescending<Mon> { it.level }
        .thenByDescending { it.ivs.total }
        .thenBy { if (it.caughtAt > 0) it.caughtAt else Long.MAX_VALUE }
        .thenBy { it.uid }
}

/**
 * What [Duplicates.releasePlan] decided: [release] = the uids to let go (species number order, best copy first inside a species),
 * [groups] = how many per species (most first), [keptProtected] = lower copies that stay because [Duplicates.keepReason] protects them.
 */
class ReleasePlan(val release: List<String>, val groups: List<Group>, val keptProtected: Map<Duplicates.Keep, Int>) {
    class Group(val species: Int, val name: String, val count: Int)

    val count: Int get() = release.size
    val isEmpty: Boolean get() = release.isEmpty()
    val keptCount: Int get() = keptProtected.values.sum()
}

/** [Catching.decide] with what goes with it: the duplicate verdict when [Duplicates] acted on this fight, else null. */
class CatchPlan(val outcome: Outcome, val duplicate: Duplicates.Verdict? = null)
