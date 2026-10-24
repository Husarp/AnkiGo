package com.husarp.ankigo.ui

import android.content.Context
import android.content.SharedPreferences

/** Remembers the sort choice (PLAN 48/55) separately for the Box, the Team picker and the Pokédex. Thin wrapper; the string is [SortSpec.encode]. */
object SortPrefs {
    enum class Where(val key: String) { BOX("sort.box"), TEAM_PICKER("sort.teamPicker"), DEX("sort.dex") }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("ankigo_ui", Context.MODE_PRIVATE)

    fun load(p: SharedPreferences, where: Where): SortSpec = SortSpec.decode(p.getString(where.key, null))
    fun save(p: SharedPreferences, where: Where, spec: SortSpec) { p.edit().putString(where.key, spec.encode()).apply() }

    fun load(ctx: Context, where: Where): SortSpec = load(prefs(ctx), where)
    fun save(ctx: Context, where: Where, spec: SortSpec) = save(prefs(ctx), where, spec)

    /** PLAN 192 (X8): the type filter (types + mode) is remembered per screen; the string is [TypeFilter.encode]. The rest of the filters: see [encodeDex] / [saveShow]. */
    enum class TypeWhere(val key: String) { BOX("typeFilter.box"), DEX("typeFilter.dex") }

    fun loadType(p: SharedPreferences, where: TypeWhere): TypeFilter = TypeFilter.decode(p.getString(where.key, null))
    fun saveType(p: SharedPreferences, where: TypeWhere, f: TypeFilter) { p.edit().putString(where.key, f.encode()).apply() }

    fun loadType(ctx: Context, where: TypeWhere): TypeFilter = loadType(prefs(ctx), where)
    fun saveType(ctx: Context, where: TypeWhere, f: TypeFilter) = saveType(prefs(ctx), where, f)

    // ---- R18 249: the rest of the filters is kept too (not the search text: it always starts empty) ----

    private const val DEX_FILTER_KEY = "dexFilter"
    private const val BOX_SHOW_KEY = "show.box"

    /** The Pokédex filters kept besides the type filter: "OWNED;1,2,3;1;0" = owner, generations, wishlist only, favourites only. */
    fun encodeDex(f: DexFilter): String =
        "${f.owned.name};${f.gens.sorted().joinToString(",")};${if (f.wishlistOnly) 1 else 0};${if (f.favouritesOnly) 1 else 0}"

    /** [base] with the kept Pokédex filters from [s]; null, unknown or garbled parts keep their default (All, no gens, off, off). */
    fun decodeDex(s: String?, base: DexFilter = DexFilter()): DexFilter {
        val parts = s?.split(';').orEmpty()
        val owned = DexOwned.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: DexOwned.ALL
        val gens = parts.getOrNull(1).orEmpty().let { g ->
            if (g.isEmpty()) emptySet() else g.split(',').map { it.toIntOrNull()?.takeIf { n -> n > 0 } ?: return@let emptySet<Int>() }.toSet()
        }
        return base.copy(owned = owned, gens = gens, wishlistOnly = parts.getOrNull(2) == "1", favouritesOnly = parts.getOrNull(3) == "1")
    }

    fun loadDexFilter(p: SharedPreferences, base: DexFilter): DexFilter = decodeDex(p.getString(DEX_FILTER_KEY, null), base)
    fun saveDexFilter(p: SharedPreferences, f: DexFilter) { p.edit().putString(DEX_FILTER_KEY, encodeDex(f)).apply() }
    fun loadDexFilter(ctx: Context, base: DexFilter): DexFilter = loadDexFilter(prefs(ctx), base)
    fun saveDexFilter(ctx: Context, f: DexFilter) = saveDexFilter(prefs(ctx), f)

    /** The Box "Show" choice; unknown or missing = All. */
    fun decodeShow(s: String?): BoxFilter = BoxFilter.entries.firstOrNull { it.name == s } ?: BoxFilter.ALL
    fun loadShow(p: SharedPreferences): BoxFilter = decodeShow(p.getString(BOX_SHOW_KEY, null))
    fun saveShow(p: SharedPreferences, f: BoxFilter) { p.edit().putString(BOX_SHOW_KEY, f.name).apply() }
    fun loadShow(ctx: Context): BoxFilter = loadShow(prefs(ctx))
    fun saveShow(ctx: Context, f: BoxFilter) = saveShow(prefs(ctx), f)
}
