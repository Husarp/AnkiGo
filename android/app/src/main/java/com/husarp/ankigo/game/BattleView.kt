package com.husarp.ankigo.game

/**
 * R17C-1: a read-only view of one fight for the rule hooks ([BattleRules]) and the move choice. [BattleEngine]'s round is one; [SimpleView]
 * is one without a round (the move choice, the damage estimate, tests). `p = true` is the Main's side, `false` the wild one's.
 *
 * The BASE values ([baseTypes] [baseStats] [baseAbility] [baseMoves]) come straight from the Pokémon; the EFFECTIVE ones ([types] [stats]
 * [ability] [moves] [grounded] [weather] [terrain]) are the base values passed through every rule set's hook, so Transform, Soak, Levitate
 * or Cloud Nine are seen by everything that asks. Hooks that implement one of those read the base value, never the effective one.
 */
interface BattleView {
    val data: GameData

    /** The fight, null when there is none (a view made for the move choice outside a battle). */
    val battle: Battle?

    /** The fight's field effects (an empty field when there is no battle). */
    val field: BattleField

    fun mon(p: Boolean): Mon
    fun side(p: Boolean): BattleSide
    fun maxHp(p: Boolean): Int

    /** The conditions of [p]'s SIDE that survive a switch (Reflect, Tailwind, Wish ...); keys and numbers are the rule sets' business. */
    fun conds(p: Boolean): MutableMap<String, Int> = if (p) field.player else field.enemy

    // ---- base values (no hooks) ----
    fun baseTypes(p: Boolean): List<Type> = data.sp(mon(p).species).types
    fun baseStats(p: Boolean): Stats = StatCalc.stats(data, mon(p))
    fun baseAbility(p: Boolean): String = normalizeAbility(mon(p).ability)
    fun baseMoves(p: Boolean): List<Move> = BattleEngine.knownMoves(data, mon(p))

    // ---- effective values (through the hooks) ----
    fun types(p: Boolean): List<Type> = rulesChain(baseTypes(p)) { r, t -> r.types(this, p, t) }
    fun stats(p: Boolean): Stats = rulesChain(baseStats(p)) { r, s -> r.stats(this, p, s) }
    fun ability(p: Boolean): String = rulesChain(baseAbility(p)) { r, a -> r.ability(this, p, a) }
    fun moves(p: Boolean): List<Move> = rulesChain(baseMoves(p)) { r, m -> r.moves(this, p, m) }

    /** Not a Flying type, unless a hook says otherwise. */
    fun grounded(p: Boolean): Boolean = rulesChain(Type.FLYING !in types(p)) { r, g -> r.grounded(this, p, g) }

    /** The weather key ("rain" "sun" "sand" "hail" "snow"), null when there is none or a set suppresses it. */
    fun weather(): String? = if (rulesAny { it.suppressWeather(this) }) null else field.weather

    /** The terrain key ("electric" "grassy" "misty" "psychic"), or null. */
    fun terrain(): String? = field.terrain

    /**
     * R17C FIX-A: whether the side [p] takes an action in this round: false for the wild one in a round it idles, and for the Main in the free hit
     * of a switch. A move that only works against an action ([BattleRules.useful] of Counter, Protect, Snatch ...) asks it about the OTHER side, so the
     * move choice does not waste an answer on it. A view with no round says true.
     */
    fun acts(p: Boolean): Boolean = true

    companion object {
        /** An ability name as an id: lower case letters and digits only ("Lightning Rod" -> "lightningrod"). */
        fun normalizeAbility(name: String): String = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
    }
}

/**
 * A [BattleView] without a round: for the move choice, [BattleEngine.damage] called on its own and tests. [main] is the Main's Pokémon
 * (`p = true`), [wild] the wild one. Max HP is computed from the Pokémon unless given.
 */
class SimpleView(
    override val data: GameData,
    private val main: Mon,
    private val wild: Mon,
    private val player: BattleSide = BattleSide(),
    private val enemy: BattleSide = BattleSide(),
    private val mainMaxHp: Int? = null,
    private val wildMaxHp: Int? = null,
    override val battle: Battle? = null,
    /** R17C FIX-A: false for the free hit of a switch (the Main does not act): [acts] says so for the move choice. */
    private val mainActs: Boolean = true,
) : BattleView {
    override val field: BattleField = battle?.field ?: BattleField()
    override fun mon(p: Boolean) = if (p) main else wild
    override fun side(p: Boolean) = if (p) player else enemy
    override fun maxHp(p: Boolean): Int = (if (p) mainMaxHp else wildMaxHp) ?: StatCalc.maxHp(data, mon(p))
    override fun acts(p: Boolean): Boolean = !p || mainActs

    companion object {
        /** The view of [battle] with [main] on the field (the battle's own sides and field, the wild one's pace-adjusted max HP); [mainActs] false = a switch's free hit. */
        fun of(data: GameData, battle: Battle, main: Mon, mainActs: Boolean = true): SimpleView =
            SimpleView(data, main, battle.wild, battle.player, battle.enemy, StatCalc.maxHp(data, main), battle.maxWildHp(data), battle, mainActs)
    }
}
