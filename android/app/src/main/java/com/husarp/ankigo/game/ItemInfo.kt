package com.husarp.ankigo.game

/** One block of an item's full text (PLAN 168): a heading ("What it does", "How to use", "In a fight") and its text, in which **double stars** mark the bold key words. */
data class ItemFact(val title: String, val text: String)

/**
 * Plain-words texts of the items (R3-X2 item 3; R14 batch 2 = PLAN 168 / 181 / 200): the tile line, the row line, the full text of an
 * opened item card and the "You own" line. Pure, no Android. Bold key words are marked with `**`; [plain] removes the marks.
 *
 * R14-B2: the API below is shared by two tickets: R14-B2-A writes the texts, R14-B2-B shows them (ui/ItemCard). Signatures are fixed.
 */
object ItemInfo {
    // ---- PLAN 168 API (R14 batch 2) ----

    private const val FIGHT_RULE = "On your **main** it takes **one turn**: the wild Pokémon attacks once. That happens only after your " +
        "**first answer** of a fight; before it, also after \"Next battle\", it is free."
    private const val MAIN_ONLY = "On a Pokémon on the bench it never takes a turn."
    private const val HOLD_RULE = "Give it to a Pokémon from the Bag. A Pokémon holds **one item**; the old one goes back to the Bag."
    private const val THROW_RULE = "Throw it from a fight (the ball button) or from the Bag while a wild Pokémon is out. **Throwing is not a turn**: you are not attacked."
    private const val NO_DESCRIPTION = "No description."
    private const val MAX_PAIRS = 6
    /** The "Best streak" achievement's top tier (AchievementTable "streak-days", the tier whose reward is the Master Ball): the days of a daily-goal streak that earn the one Master Ball. */
    private val MASTER_STREAK_DAYS get() = AchievementTable.rewardThreshold("streak-days", "master-ball") ?: 0L
    private val POWER_STATS = listOf(
        "power-weight" to "HP", "power-bracer" to "Attack", "power-belt" to "Defense",
        "power-lens" to "Sp. Atk", "power-band" to "Sp. Def", "power-anklet" to "Speed",
    ).toMap()
    /** Names that stay as they are for any number: "2 Fresh Water", "3 Luck Incense". */
    private val MASS = setOf("Fresh Water", "Moomoo Milk", "Energy Powder", "Berry Juice", "Lemonade", "Luck Incense", "Old Amber")

    private fun isMaster(id: String) = Tuning.BALL_CHANCE[id].let { it != null && it >= 100 }
    private fun isOldBall(id: String) = id in Tuning.OLD_BALLS

    /** The tile line (Shop tiles; about 18 characters, no bold marks): "20 % catch", "Heals 20 HP", "Evolves Applin", "Main: ×2 money". [data] null = no species names. */
    fun short(data: GameData?, item: String): String {
        val def = Items.def(item) ?: return NO_DESCRIPTION
        return when (def.kind) {
            ItemKind.BALL -> when {
                isMaster(item) -> "Always catches"
                isOldBall(item) -> "Like a ${Items.name(Tuning.OLD_BALL_AS)}"
                else -> "${Catching.ballChance(item)} % catch"
            }
            ItemKind.HEAL -> if (def.cures) "All HP + status" else healLine(def)
            ItemKind.EVOLUTION -> {
                if (item in Items.RETIRED) "Does nothing here"
                else if (item == LINKING_CORD) "Trade evolvers"
                else {
                    val from = evoPairs(data, item).map { it.from }.distinct()
                    when {
                        from.isEmpty() -> "Evolution item"
                        from.size == 1 -> "Evolves ${from[0]}"
                        else -> "Evolves ${from[0]} +${from.size - 1}"
                    }
                }
            }
            ItemKind.HELD -> when (item) {
                "lucky-egg" -> "×${nice(Tuning.LUCKY_EGG)} XP"
                "amulet-coin", "luck-incense" -> "Main: ×2 money"
                "everstone" -> "Never evolves"
                "macho-brace" -> "Main: ×2 EVs"
                "soothe-bell" -> "Main: ×${nice(Tuning.SOOTHE_BELL)} friendship"
                else -> POWER_STATS[item]?.let { "Main: +8 $it EVs" } ?: NO_DESCRIPTION
            }
            ItemKind.ESCAPE -> "Skip this wild one"
            ItemKind.FOSSIL -> fossilName(data, def)?.let { "Revives $it" } ?: "Revives a Pokémon"
        }
    }

    /** The row line (Bag rows, under the name of an opened card, the deal card; about 40 characters, no bold marks). */
    fun line(data: GameData?, item: String): String {
        val def = Items.def(item) ?: return NO_DESCRIPTION
        return when (def.kind) {
            ItemKind.BALL -> when {
                isMaster(item) -> "Always catches · can't be bought"
                isOldBall(item) -> "Like a ${Items.name(Tuning.OLD_BALL_AS)}: ${Catching.ballChance(item)} % to catch"
                else -> "${Catching.ballChance(item)} % to catch · a miss: it runs away"
            }
            ItemKind.HEAL -> if (def.cures) "Heals all HP, cures a status" else healLine(def)
            ItemKind.EVOLUTION -> evolutionLine(data, item)
            ItemKind.HELD -> when (item) {
                "lucky-egg" -> "Holder gets ×${nice(Tuning.LUCKY_EGG)} XP"
                "amulet-coin", "luck-incense" -> "Main holds it: ×2 money"
                "everstone" -> "Holder never evolves"
                "macho-brace" -> "Main holds it: ×2 EVs"
                "soothe-bell" -> "Main holds it: ×${nice(Tuning.SOOTHE_BELL)} friendship"
                else -> POWER_STATS[item]?.let { "Main holds it: +8 $it EVs" } ?: NO_DESCRIPTION
            }
            ItemKind.ESCAPE -> "Skip this wild Pokémon"
            ItemKind.FOSSIL -> "Revives ${fossilName(data, def) ?: "a Pokémon"} at Lv ${Tuning.FOSSIL_LEVEL}"
        }
    }

    /** The full text of an opened card, in blocks. Never empty for a catalogue item; an unknown item gets one generic block. */
    fun facts(data: GameData?, item: String): List<ItemFact> {
        val def = Items.def(item) ?: return listOf(ItemFact(WHAT, NO_DESCRIPTION))
        return when (def.kind) {
            ItemKind.BALL -> ballFacts(item)
            ItemKind.HEAL -> {
                val all = def.heal == Int.MAX_VALUE
                // R17C-6: a status (burn, poison, paralysis, sleep, freeze) exists only on the main in a fight; Full Restore cures it, Max Potion does not.
                listOf(
                    ItemFact(WHAT, when {
                        def.cures -> "Heals **all HP** of one of your Pokémon. In a fight it also **cures** your main's burn, poison, paralysis, sleep or freeze. A status ends with the fight anyway."
                        all -> "Heals **all HP** of one of your Pokémon. It **doesn't** cure a status: only a Full Restore does that."
                        else -> "Heals one of your Pokémon by **${def.heal} HP**, never above its maximum."
                    }),
                    ItemFact(HOW, if (def.cures) "Use it from the Bag. It **can't** be used on a Pokémon at full HP, except on your main when it has a status in a fight."
                    else "Use it from the Bag. It **can't** be used on a Pokémon at full HP."),
                    ItemFact(FIGHT, "$FIGHT_RULE $MAIN_ONLY"),
                )
            }
            ItemKind.EVOLUTION -> if (item in Items.RETIRED) listOf(
                ItemFact(WHAT, RETIRED_WHAT),
                ItemFact(HOW, "You can't use it. If you already own one, it stays in the Bag."),
            ) else listOf(
                ItemFact(WHAT, evolutionWhat(data, item)),
                ItemFact(HOW, "Use it from the Bag on a Pokémon that evolves with it. A Pokémon holding an **Everstone** won't evolve."),
                ItemFact(FIGHT, "$FIGHT_RULE $MAIN_ONLY"),
            )
            ItemKind.HELD -> listOf(
                ItemFact(WHAT, heldWhat(item)),
                ItemFact(HOW, HOLD_RULE),
                ItemFact(FIGHT, "Giving an item to hold is **not a turn**."),
            )
            ItemKind.ESCAPE -> listOf(
                ItemFact(WHAT, "Makes the wild Pokémon go away, and a new one appears. You get **no XP** and **no money** for it."),
                ItemFact(HOW, "Use it from the Bag while a wild Pokémon is out."),
                ItemFact(FIGHT, "The wild Pokémon **doesn't attack** you when you use it."),
            )
            ItemKind.FOSSIL -> listOf(
                ItemFact(WHAT, "Brings ${fossilName(data, def)?.let { "**$it**" } ?: "an ancient Pokémon"} back at **Lv ${Tuning.FOSSIL_LEVEL}**. " +
                    "It joins your team or your Box, and the fossil is used up."),
                ItemFact(HOW, "Use it from the Bag."),
                ItemFact(FIGHT, "Reviving a fossil is **not a turn**."),
            )
        }
    }

    /** The full text of an opened TM card: what a TM is, and how it is taught. */
    fun tmFacts(): List<ItemFact> = listOf(
        ItemFact(WHAT, "A **TM** (Technical Machine) teaches one move. Buy it **once**; it is **never used up**, so you can teach the move to every Pokémon of yours that can learn it."),
        ItemFact(HOW, "Teach it from the Bag with **Teach…**. **Who can learn it** lists your Pokémon that can learn the move. If a Pokémon already knows 4 moves, you choose which one to forget."),
    )

    /** What the TM's move does, in one or two short sentences (bold marks allowed), e.g. "10 % chance to **paralyze** the target."; "" when there is nothing to say. */
    fun moveEffect(data: GameData, move: String): String {
        val m = data.move(move) ?: return ""
        val text = MoveText.describe(m)
        if (text == "Deals damage with no extra effect.") return ""
        return KEY_WORDS.replace(text) { "**${it.value}**" }
    }

    /** Your Pokémon that can learn [move] by TM (team first, then the box), whether or not they already know it. */
    fun whoCanLearn(data: GameData, state: GameState, move: String): List<Mon> =
        (state.teamMons() + state.boxMons()).filter { move in data.sp(it.species).tmMoves }

    /** "You own **2 Great Balls**" / "You own **1 Great Ball**" / "You don't own any **Great Balls** yet" (natural plurals). */
    fun ownLine(item: String, count: Int): String =
        if (count > 0) "You own **$count ${plural(Items.name(item), count)}**" else "You don't own any **${plural(Items.name(item), 2)}** yet"

    /** "You **own** this TM" / "You don't own this TM yet". */
    fun tmOwnLine(owned: Boolean): String = if (owned) "You **own** this TM" else "You don't own this TM yet"

    /** [name] for [n] of it: "Great Ball" (1) / "Great Balls" (2); "Power Lens" -> "Power Lenses"; "Deep Sea Tooth" -> "Deep Sea Teeth"; mass nouns stay as they are. */
    fun plural(name: String, n: Int): String = when {
        n == 1 || name in MASS -> name
        name.endsWith("Tooth") -> name.removeSuffix("Tooth") + "Teeth"
        name.endsWith("sh") || name.endsWith("ch") || name.endsWith("x") || name.endsWith("s") || name.endsWith("z") -> name + "es"
        else -> name + "s"
    }

    /** [text] without the bold marks (TalkBack, tests). */
    fun plain(text: String): String = text.replace("**", "")

    private const val WHAT = "What it does"
    private const val HOW = "How to use"
    private const val FIGHT = "In a fight"
    private const val LINKING_CORD = "linking-cord"
    /** R17C-FIX: the text of an [Items.RETIRED] item (the Chipped Pot): kept in the catalogue for the bags that hold one. */
    private const val RETIRED_WHAT = "Does **nothing** in AnkiGo: the only Pokémon that evolves with it is a form the game doesn't have. It is not sold or found any more."

    /** The words of a move text that get bold marks (longest first, so "Badly poisons" wins over "poisons"). */
    private val KEY_WORDS = Regex(
        "Puts (?:you|the target) to sleep|put (?:you|the target) to sleep|Badly poisons|badly poison|Paralyzes|paralyze|Burns|burn|Poisons|poison|" +
            "Freezes|freeze|Knocks the target out in one hit|Usually goes first|Usually goes last|Never misses|Raises|raise|Lowers|lower|" +
            "Heals you|recoil|Hits \\d+(?:–\\d+)? times|Charges on the first turn|" +   // R17C-3: the charge sentence of MoveText
            // R17C-10: the key verbs of the new rules (whole words, so "confused" or "flinched" inside a longer sentence stay plain)
            "Confuses|\\bconfuse\\b|\\bflinch\\b|Blocks|Holds the target|\\brecharge\\b|Cures|\\bfall in love\\b|Seeds the target|Swaps|Copies",
    )

    private fun nice(d: Double): String = if (d == Math.floor(d)) d.toInt().toString() else d.toString()

    private fun healLine(def: ItemDef) = if (def.heal == Int.MAX_VALUE) "Heals all HP" else "Heals ${def.heal} HP"

    private fun fossilName(data: GameData?, def: ItemDef): String? = def.fossil?.let { data?.species?.get(it)?.name }

    private fun ballFacts(item: String): List<ItemFact> {
        val miss = "If it fails, the wild Pokémon **runs away** (no XP, no money)."
        return when {
            isMaster(item) -> listOf(
                ItemFact(WHAT, "**Always catches** (100 %). The ball is used up. It **can't be bought**: you get one for the top tier of the \"Best streak\" achievement (a $MASTER_STREAK_DAYS-day streak of reaching your daily goal)."),
                ItemFact(HOW, THROW_RULE),
            )
            isOldBall(item) -> listOf(
                ItemFact(WHAT, "An old ball from Ankimon. It throws like a **${Items.name(Tuning.OLD_BALL_AS)}** (${Catching.ballChance(item)} %) and is used up either way. $miss It is not sold or found any more."),
                ItemFact(HOW, "$THROW_RULE In the Bag it counts as a ${Items.name(Tuning.OLD_BALL_AS)} when you trade 3 for 1 Ultra Ball."),
            )
            else -> {
                val pct = Catching.ballChance(item)
                val extra = if (item == "ultra-ball") " Sold only about **one day a week**." else ""
                val trade = when (item) {
                    "poke-ball" -> " In the Bag you can trade 5 Poké Balls for 1 Great Ball."
                    "great-ball" -> " In the Bag you can trade 3 Great Balls for 1 Ultra Ball."
                    else -> ""
                }
                val article = if (pct == 80) "an" else "a"
                listOf(
                    ItemFact(WHAT, "Catches a wild Pokémon with $article **$pct %** chance. The ball is used up either way. $miss$extra"),
                    ItemFact(HOW, if (item == "ultra-ball") THROW_RULE else THROW_RULE + trade),
                )
            }
        }
    }

    /** One "from into to" pair an evolution item makes, with its time of day / gender rule. */
    private data class EvoPair(val from: String, val to: String, val time: String?, val gender: Char?)

    /**
     * Every pair [item] evolves, from the species data (Progression.itemEvolutions rules): ITEM with that item, TRADE with that held item;
     * Linking Cord = TRADE without an item + OTHER. Base forms first, then regional ones; the same pair is listed once.
     */
    private fun evoPairs(data: GameData?, item: String): List<EvoPair> {
        if (data == null) return emptyList()
        return data.species.values.sortedBy { it.num }.flatMap { sp ->
            sp.evolutions.mapNotNull { e ->
                val into = data.species[e.to] ?: return@mapNotNull null
                val hit = when (e.kind) {
                    EvoKind.ITEM -> e.item == item
                    EvoKind.TRADE -> if (e.item != null) e.item == item else item == LINKING_CORD
                    EvoKind.OTHER -> item == LINKING_CORD
                    else -> false
                }
                if (hit) EvoPair(sp.name, into.name, e.time, e.gender) else null
            }
        }.distinct()
    }

    private fun evolutionLine(data: GameData?, item: String): String {
        if (item in Items.RETIRED) return "Does nothing here: no Pokémon uses it"
        if (item == LINKING_CORD) return "Evolves Pokémon that evolve by trade"
        val pairs = evoPairs(data, item)
        val from = pairs.map { it.from }.distinct()
        val one = if (pairs.size == 1) "Evolves ${pairs[0].from} into ${pairs[0].to}" else null
        return when {
            from.isEmpty() -> "Evolves Pokémon that evolve with it"
            one != null && one.length <= 40 -> one
            from.size == 1 -> "Evolves ${from[0]}"
            from.size == 2 && "Evolves ${from[0]} and ${from[1]}".length <= 40 -> "Evolves ${from[0]} and ${from[1]}"
            else -> "Evolves ${from[0]} and ${from.size - 1} more"
        }
    }

    private fun evolutionWhat(data: GameData?, item: String): String {
        val pairs = evoPairs(data, item)
        val cord = item == LINKING_CORD
        if (pairs.isEmpty()) return if (cord) "Evolves Pokémon that normally evolve **by trade**." else "Evolves the Pokémon that need it."
        val times = pairs.map { it.time }.distinct()
        val common = times.singleOrNull()
        fun label(p: EvoPair): String {
            val tags = listOfNotNull(
                if (common == null) p.time?.let { "$it only" } else null,
                when (p.gender) { 'F' -> "female only"; 'M' -> "male only"; else -> null },
            )
            return "**${p.from}** into **${p.to}**" + if (tags.isEmpty()) "" else " (${tags.joinToString(", ")})"
        }
        val shown = pairs.take(MAX_PAIRS).joinToString(", ") { label(it) }
        val more = if (pairs.size > MAX_PAIRS) ", and ${pairs.size - MAX_PAIRS} more" else ""
        val head = if (cord) "Evolves Pokémon that normally evolve **by trade**, and a few with special rules: " else "Evolves "
        val time = if (common != null) " Works only at **$common**." else ""
        return "$head$shown$more.$time"
    }

    private fun heldWhat(item: String): String = when (item) {
        "lucky-egg" -> "The Pokémon holding it gets **×${nice(Tuning.LUCKY_EGG)} XP** from wins and catches. It works for your **main** and for the **Exp. Share** holder."
        "amulet-coin", "luck-incense" -> "While your **main** holds it, the money you earn for answers and wins is **doubled**, all day. " +
            "The slower pay after each ${Tuning.CASH_TIER} ₽ still applies, so you keep getting double at every step. It does nothing on a Pokémon that is not your main."
        "everstone" -> "A Pokémon holding it **never evolves**: not by level, friendship, stone or trade. Take it back to let the Pokémon evolve."
        "macho-brace" -> "Your **main** gets **double EVs** from each win, defeat or catch (as always, up to ${Tuning.EV_STAT_CAP} per stat and ${Tuning.EV_TOTAL_CAP} in total). " +
            "It does nothing on a Pokémon that is not your main."
        "soothe-bell" -> "Your **main** gets **×${nice(Tuning.SOOTHE_BELL)} friendship** from each win, defeat or catch (+${Tuning.FRIENDSHIP_GAIN.first} to ${Tuning.FRIENDSHIP_GAIN.last} becomes " +
            "+${(Tuning.FRIENDSHIP_GAIN.first * Tuning.SOOTHE_BELL).toInt()} to ${(Tuning.FRIENDSHIP_GAIN.last * Tuning.SOOTHE_BELL).toInt()}), up to ${Tuning.FRIENDSHIP_MAX}. " +
            "Friendship evolves some Pokémon and powers Return. It does nothing on a Pokémon that is not your main."
        else -> POWER_STATS[item]?.let {
            "Your **main** gets **8 extra $it EVs** from each win, defeat or catch, on top of the usual EVs (up to ${Tuning.EV_STAT_CAP} per stat and ${Tuning.EV_TOTAL_CAP} in total). " +
                "It does nothing on a Pokémon that is not your main."
        } ?: NO_DESCRIPTION
    }

    // ---- before R14 (kept while callers move to the API above) ----

    /** What the item does: its catalogue note, or a generic line for an item the catalogue does not know. */
    fun effect(item: String): String = line(null, item)

    /** Where and how the item is used (one sentence). */
    fun howToUse(item: String): String = when (Items.def(item)?.kind) {
        ItemKind.BALL -> "Throw it from the Bag at a wild Pokémon in battle. The ball is used up either way."
        ItemKind.HEAL -> "Use it from the Bag on one of your Pokémon. $FIGHT_TURN"
        ItemKind.EVOLUTION -> if (item in Items.RETIRED) "No Pokémon in AnkiGo evolves with it, so it can't be used."
            else "Use it from the Bag on a Pokémon that evolves with it. $FIGHT_TURN"
        ItemKind.HELD -> "Give it to a Pokémon from the Bag. A Pokémon holds one item at a time."
        ItemKind.ESCAPE -> "Use it from the Bag to leave the wild Pokémon in battle and meet a new one."
        ItemKind.FOSSIL -> "Use it from the Bag to revive an ancient Pokémon at Lv ${Tuning.FOSSIL_LEVEL}."
        null -> ""
    }

    /** R13 Part C: an item used on the main during a fight takes one turn (Game.useItem). */
    const val FIGHT_TURN = "On your main during a fight it takes one turn: the wild Pokémon attacks once."

    /** What a TM does, for the popup: it teaches a move and is never used up. */
    fun tmHowToUse(): String = "Teaches this move to a Pokémon that can learn it. A TM is never used up."
}
