package com.husarp.ankigo.help

import com.husarp.ankigo.game.AchievementTable
import com.husarp.ankigo.game.DailyGift
import com.husarp.ankigo.game.FieldRules
import com.husarp.ankigo.game.MoveText
import com.husarp.ankigo.game.SideMoves
import com.husarp.ankigo.game.Tuning
import com.husarp.ankigo.game.Type
import java.util.Locale
import kotlin.math.roundToInt

/** Which real in-app element a guide chapter shows (the visual ticket renders the real component for each id). */
enum class GuideVisualId {
    /** The type chips exactly as in the game ([GuideVisual.arg] = a game Type name such as "FIRE"; null = a row of a few types). */
    TYPE_CHIPS,
    HP_BAR,
    XP_BAR,
    /** An item's icon; [GuideVisual.arg] = the item id (always an id of game.Items). */
    ITEM_ICON,
    /** A Pokémon mark (drawn as its icon, like the details header); [GuideVisual.arg] = a ui TagKind name ("CAN_EVOLVE", "SHINY", "MAIN"...). */
    TAG,
    /** The compare/Pokédex type matchup table. */
    MATCHUP_TABLE,
    /** The six stat bars of the details screen. */
    STAT_BARS,
    /** "Lv N" in every level-colour tier (bronze to master). */
    LEVEL_COLOURS,
    /** The round status badges (poison, burn, paralysis, sleep, freeze). */
    STATUS_BADGES,
}

data class GuideVisual(val id: GuideVisualId, val arg: String? = null, val caption: String = "")

/** One chapter of "How the game works": a [title], one [intro] sentence, a few short [points], and the [visuals] to show. */
data class GuideChapter(val id: String, val title: String, val intro: String, val points: List<String>, val visuals: List<GuideVisual>)

/**
 * Numbers of the new money and healing rules (PLAN 72, 80). All of them are the Tuning constants (R3-01 money, R3-02 healing).
 */
data class GuideNumbers(
    val cashPerAnswer: Int,
    val cashPerWin: Int,
    val cashTier: Int,
    val giftMaxStreakDays: Int,        // R14-B2-D: the daily gift's chances (percent) on goal-streak day 1 and on day [giftMaxStreakDays] and later
    val giftCommonFirst: Int,
    val giftUncommonFirst: Int,
    val giftRareFirst: Int,
    val giftCommonLast: Int,
    val giftUncommonLast: Int,
    val giftRareLast: Int,
    val masterBallStreakDays: Int,     // the top tier of "Best streak": it gives the Master Ball
    val levelUpHealPercent: Int,
    val defeatHealPercent: Int,
    val benchRestPercentPerAnswer: Double,
) {
    companion object {
        /** Money (R3-01) and healing (R3-02) numbers, straight from Tuning. */
        fun pending() = GuideNumbers(
            cashPerAnswer = Tuning.CASH_PER_ANSWER, cashPerWin = Tuning.CASH_PER_WIN, cashTier = Tuning.CASH_TIER,
            giftMaxStreakDays = Tuning.GIFT_MAX_DAYS,
            giftCommonFirst = DailyGift.chances(1).common.roundToInt(), giftUncommonFirst = DailyGift.chances(1).uncommon.roundToInt(),
            giftRareFirst = DailyGift.chances(1).rare.roundToInt(),
            giftCommonLast = DailyGift.chances(Tuning.GIFT_MAX_DAYS).common.roundToInt(), giftUncommonLast = DailyGift.chances(Tuning.GIFT_MAX_DAYS).uncommon.roundToInt(),
            giftRareLast = DailyGift.chances(Tuning.GIFT_MAX_DAYS).rare.roundToInt(),
            masterBallStreakDays = AchievementTable.row("streak-days")?.tiers?.last()?.toInt() ?: 365,
            levelUpHealPercent = Tuning.LEVEL_UP_HEAL_PERCENT, defeatHealPercent = Tuning.DEFEAT_HEAL_PERCENT,
            benchRestPercentPerAnswer = 100.0 / Tuning.BENCH_REST_DIVISOR,
        )
    }
}

/**
 * "How the game works" (PLAN 44, R17C-10: nine with Moves and effects): short chapters the player can jump between. All numbers come from [Tuning], except
 * money and healing, which come from [GuideNumbers.pending]. Pure Kotlin, no Android.
 */
object GameGuide {
    const val BATTLES = "battles-xp"
    const val MOVES = "moves-effects"
    const val LEVELS = "levels-evolutions"
    const val TYPES = "types-matchups"
    const val STATS = "stats-ivs-evs-natures"
    const val CATCHING = "catching-balls"
    const val MONEY = "money"
    const val HEALING = "healing"
    const val ITEMS = "items"

    private fun n(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else String.format(Locale.US, "%.1f", d)
    private fun pct(d: Double): String = n(d * 100)
    /** A factor with up to two decimals, no trailing zeros: 0.35, 1.2, 2. */
    private fun x(d: Double): String = String.format(Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')

    fun chapters(nums: GuideNumbers = GuideNumbers.pending()): List<GuideChapter> = listOf(
        GuideChapter(
            BATTLES, "Battles and XP",
            "Every answer you give is a move in a battle against a wild Pokémon.",
            listOf(
                "A battle round is ${Tuning.CARDS_PER_ROUND} answers, and each answer decides who attacks.",
                "Your Main attacks when the round has a Good or an Easy answer. Good adds ×${x(Tuning.MAIN_POWER[3])} and Easy adds ×${x(Tuning.MAIN_POWER[4])} to its damage (Good + Good = ×${x(2 * Tuning.MAIN_POWER[3])}, Easy + Easy = ×${x(2 * Tuning.MAIN_POWER[4])}).",
                "The wild Pokémon attacks when the round has an Again or a Hard answer. Again gives it ×${x(Tuning.WILD_POWER[1])} and Hard ×${x(Tuning.WILD_POWER[2])} of its normal damage (Again + Again = ×${x(2 * Tuning.WILD_POWER[1])}, Hard + Easy = ×${x(Tuning.WILD_POWER[2])}).",
                "${Tuning.WILD_AGAIN_STREAK} Agains in a row give the wild Pokémon +${Tuning.WILD_ATTACK_STAGES} Attack stage, once per streak, until the battle ends. A non-Again answer starts a new streak; normal stage limits apply. OK neither adds to nor breaks this streak.",
                "After ${Tuning.WILD_STRONGEST_ROUNDS} rounds without Good or Easy, the wild Pokémon picks its strongest move once, announced in the log, then counts again. Strength uses expected damage, including accuracy and types. Good or Easy resets the count; OK-only rounds pause it. Existing forced moves still apply.",
                "Both can attack in the same round. A round of only Good and Easy answers is safe: the wild Pokémon does nothing.",
                "The OK button on a new card (a setting in Study) is a neutral move. It fills a grey dot and counts as one of the round's ${Tuning.CARDS_PER_ROUND} answers, but it has no power: the round's attacks come from the other answer alone (OK + Good is a Good round, OK + Again is an Again round). OK + OK is an empty round: nobody attacks, but burn, poison and the weather still tick at its end. An OK never breaks your combo.",
                "A switch is not a round and uses none of your answers. Before your first answer of a fight, a switch is instant and free. After that, while a card shows, a switch waits for your answer to it (the toast says \"Switching to X on your next move\"). That answer counts as normal; right after it the new Main comes in, and the wild Pokémon gets one free move on it, at the damage of one Again (×${x(Tuning.SWITCH_HIT_POWER)}); a toast says \"Switched! Wild X gets a free move.\" The switch fills no dot. The free move is picked like in a round (it can also use a status move or start a charge); the answers you already gave stay as they were, and your next answer attacks as normal. With no card in front of you (a result is on screen) the switch happens at once, and the next battle still waits for Continue. This goes for every way of changing the Main (the switch menu, Team, Box, Compare). Effects that count rounds don't count that free move.",
                "Using an item on your Main during a battle (a healing item or an evolution item) works at once, then the wild Pokémon uses one free move on your Main, the same way. No answer was made, so the dots of the round don't change. Balls have their own rule (see Catching), and items you give a Pokémon to hold are free.",
                "The wild Pokémon doesn't attack when you replace a Main that has fainted, when there is no wild Pokémon in the fight, or before you have answered a card in this battle.",
                "Move choice (Settings → Game → Battle) is how both Pokémon pick their move, yours and the wild one alike. Smart (the default) uses an attack ${pct(Tuning.SMART_DAMAGE_SHARE)}% of the time when it also has a status move that can still change something, and always when it has none. Smartest also prefers the stronger attack. Random picks any of its moves by chance, as before.",
                "Smart and Smartest never use a status move that would do nothing (a stat already at its limit, a status the target already has or cannot get, healing at full HP, a screen that is already up) and never an attack the target is immune to (by its type or by an ability, such as Wonder Guard). With nothing else to use, your Main uses Struggle and a wild Pokémon uses any move. Accuracy and evasion stop at ±${Tuning.SMART_ACC_EVA_LIMIT} instead of ±6.",
                "Beat it and your main Pokémon gets XP: the wild Pokémon's base experience × its level ÷ ${n(Tuning.XP_DIVISOR)}, rounded up to at least 1 XP, at most ${Tuning.MAX_LEVELS_PER_GAIN} levels at a time.",
                "If another Pokémon is the Exp. Share holder, it and your Main each get ${pct(Tuning.XP_SHARE_PART)}% of the XP, rounded down. A Lucky Egg holder gets ×${n(Tuning.LUCKY_EGG)} XP. Free study answers to cards that are not due give ${pct(Tuning.PRACTICE_XP_PART)}% Pokémon and trainer XP and no coins.",
            ),
            listOf(
                GuideVisual(GuideVisualId.HP_BAR, caption = "HP: when it is empty the Pokémon faints"), GuideVisual(GuideVisualId.XP_BAR, caption = "XP: full means a level up"),
                GuideVisual(GuideVisualId.LEVEL_COLOURS, caption = "Level colour = tier, every 10 levels"),
                GuideVisual(GuideVisualId.STATUS_BADGES, caption = "Status: tap a badge for what it does"),
                GuideVisual(GuideVisualId.TYPE_CHIPS, "FIRE", "Type: see Types"),
            ),
        ),
        GuideChapter(
            MOVES, "Moves and effects",
            "Your Pokémon and the wild ones use their moves by the same rules, which follow the games.",
            listOf(
                com.husarp.ankigo.game.StageText.table,
                "In the texts of moves and abilities a turn is one round (${Tuning.CARDS_PER_ROUND} answers). Effects that last count down at the end of each round.",
                "Charge moves (Solar Beam, Dig, Fly and a few more) take two moves: the first only charges, and your very next move is the hit, whatever you would have picked. Nobody hides underground or in the sky, so a charging Pokémon can be hit. After a recharge move (Hyper Beam, Giga Impact) hits, your next move is a rest turn.",
                "Flinch: a move that makes the target flinch only works if it moves before the target in that round. The target then loses its move.",
                "Confusion: a confused Pokémon hurts itself 1 time in ${Math.round(1 / Tuning.CONFUSION_SELF_HIT)} instead of moving (a typeless hit of power ${Tuning.CONFUSION_POWER}) and snaps out of it after ${Tuning.CONFUSION_TURNS.first} to ${Tuning.CONFUSION_TURNS.last} tries to move. In love: it can't move ${pct(Tuning.LOVE_STOPS)}% of the time, and it only works between a male and a female.",
                "Protect and the moves like it block everything for the rest of the round, so they only help when the other Pokémon attacks in that round. Used again right after, they often fail. Substitute costs ${MoveText.part(1, SideMoves.SUBSTITUTE_DIVISOR)} your max HP and takes the hits meant for you; it also stops status moves.",
                "Reflect, Light Screen and Aurora Veil halve the damage of physical or special moves for ${SideMoves.SCREEN_TURNS} rounds.",
                "Weather (rain, harsh sun, sandstorm, hail, snow) lasts ${FieldRules.WEATHER_TURNS} rounds and terrain ${FieldRules.TERRAIN_TURNS} rounds. Rain and sun make Water and Fire moves stronger or weaker, and a sandstorm or hail hurts a sixteenth of max HP each round. A terrain gives Pokémon on the ground a boost or a protection.",
                "Binding moves (Wrap, Fire Spin, Whirlpool) hurt the target ${MoveText.part(1, Tuning.BIND_DIVISOR)} its max HP at the end of each round for ${Tuning.BIND_TURNS.first} to ${Tuning.BIND_TURNS.last} rounds. They never stop anyone from switching.",
                "Abilities work in battle for your Pokémon and the wild ones (Intimidate, Static, Levitate, Sturdy and many more). The ability's line in a Pokémon's details says what it does here; some abilities have no effect in AnkiGo, and the details say so.",
                "Some moves are never used because they mean nothing in a one-on-one fight with a wild Pokémon (switching, partner, PP and critical-hit moves). The move's text says so. A Pokémon that knows only such moves uses Struggle (your Main) or does nothing (a wild one).",
            ),
            listOf(GuideVisual(GuideVisualId.STATUS_BADGES, caption = "Status: tap a badge for what it does")),
        ),
        GuideChapter(
            LEVELS, "Levels and evolutions",
            "Pokémon grow with XP up to level ${Tuning.MAX_LEVEL}, and some evolve.",
            listOf(
                "Level: some Pokémon become ready to evolve when they reach a level.",
                "Bond: earn +1 point per answered card while fighting and +3 per win. ♥1–♥5 start at 50 / 125 / 250 / 500 / 800 points. Normal evolutions need ♥2 plus their usual requirement; Bond evolutions need ♥3 or ♥4. Each level adds +1 coin per defeated Pokémon and +5% XP. At ♥4/♥5, a lethal attack has a 5%/10% chance to leave 1 HP. A Soothe Bell makes points ×1.5.",
                "Item: stones and similar items evolve the Pokémon that need them. Open the item in your bag and use it.",
                "A green mark shows when a Pokémon is ready to evolve. Evolution never happens automatically: open its details and choose Evolve for the branch you want. Required items are used up. Some branches also need a particular time of day, gender, learned move or other condition; the evolution options show what is missing. Trade evolutions use a Linking Cord unless they require another item. An active Everstone in its hold stops evolving.",
                "Evolving heals the Pokémon fully.",
            ),
            listOf(GuideVisual(GuideVisualId.TAG, "CAN_EVOLVE", "Ready to evolve"), GuideVisual(GuideVisualId.ITEM_ICON, "thunder-stone", "An evolution stone"), GuideVisual(GuideVisualId.ITEM_ICON, "everstone", "Holds a Pokémon back")),
        ),
        GuideChapter(
            TYPES, "Types and matchups",
            "There are ${Type.entries.size} types. Each move has one, and so does each Pokémon.",
            listOf(
                "A move that is strong against the target's type does ×2 damage. A weak match does ×0.5.",
                "Some matchups do nothing at all (×0), for example a Normal move against a Ghost.",
                "STAB: a move of the same type as the Pokémon using it does ×${n(Tuning.STAB)} damage.",
                "Pokémon with two types multiply both matchups.",
            ),
            listOf(GuideVisual(GuideVisualId.MATCHUP_TABLE, caption = "Who beats whom")),
        ),
        GuideChapter(
            STATS, "Stats, IVs, EVs and natures",
            "Two Pokémon of the same kind are never exactly the same.",
            listOf(
                "Six stats: HP, Attack, Defense, Sp. Atk, Sp. Def and Speed.",
                "IVs are the Pokémon's born-in talent. They never change.",
                "Wild Pokémon appear from 1 level below to 5 levels above your Main (levels 1–100). Their EVs grow with level in the stats their species yields, and they keep those EVs when caught.",
                "EVs grow for your Main with every wild Pokémon defeated or caught: up to ${Tuning.EV_STAT_CAP} in one stat and ${Tuning.EV_TOTAL_CAP} in total. Macho Brace doubles them. Power items add extra to one stat.",
                "A nature makes one stat 10% higher and another 10% lower (some natures change nothing).",
            ),
            listOf(GuideVisual(GuideVisualId.STAT_BARS, caption = "The six stats"), GuideVisual(GuideVisualId.ITEM_ICON, "macho-brace", "Doubles EV gain")),
        ),
        GuideChapter(
            CATCHING, "Catching and balls",
            "Throw a ball at a wild Pokémon while it still has HP. At 0 HP, your catch rule decides whether to catch or defeat it.",
            listOf(
                "Poké Ball ${Tuning.BALL_CHANCE.getValue("poke-ball")}%, Great Ball ${Tuning.BALL_CHANCE.getValue("great-ball")}%, Ultra Ball ${Tuning.BALL_CHANCE.getValue("ultra-ball")}%, Master Ball ${Tuning.BALL_CHANCE.getValue("master-ball")}% per throw before special-ball bonuses. HP and status do not change these chances.",
                "A ball is used up whether it works or not. If it fails, the wild Pokémon breaks free and runs away: no XP, and the next wild Pokémon comes. Throwing a ball is not a turn: the wild Pokémon doesn't attack.",
                "The Master Ball can't be bought. You get one for the top tier of the \"Best streak\" achievement (${nums.masterBallStreakDays} days in a row with your daily goal reached). The reward goes straight into your Bag once; using it does not earn another.",
                "Choose a regular ball and optionally one special: for example, Ultra Heal Ball. The regular ball is always used up; specials are too, except Repeat Ball can return. " + Tuning.OLD_BALLS.joinToString(" ") { com.husarp.ankigo.game.Items.specialNote(it) },
                "A catch counts as a win like a defeat: your trainer XP and defeat counts go up, and your Pokémon get the same XP and money as for a defeat (base ${nums.cashPerWin} coins before daily money tiers and held-item bonuses), with a ball or without.",
                "When you beat a wild Pokémon without a ball, Settings → Game → \"When you beat a wild Pokémon\" decides: always catch, catch only species you have never caught (the default), catch new species and better copies (a higher-level copy replaces your old one), ask me, or defeat (only shinies and protected kinds are caught). A defeat and a catch both give the XP, the EVs, the Bond points and the same money (see Money); a catch also gives you the Pokémon.",
                "The auto-catch animation follows the rarity marks: no mark → Poké Ball; round dot (Starters, or Normal / Mega / G-Max with base stat total 500+) and Baby diamond → Great Ball; Ultra Beast hexagon and Legendary sparkle → Ultra Ball; Mythical star → Master Ball. This is only the picture: it uses no balls from your Bag. A thrown ball shows the ball you used. Hide name marks in Settings → Battle → Pokémon rarity marks.",
                "Ask me works like \"catch only new species\", except for one thing: when the Pokémon you beat is a species you already own and it is better than your copies (a higher level than your best one, or a higher IV total, the six IVs added up), a pop-up asks you: Catch (keep both), Catch and replace, or Don't catch. Closing the pop-up (Back, or a tap outside) means Don't catch. The fight is paid at once, as a defeat; only the Pokémon waits. In the floating window, picture-in-picture or while you study in AnkiDroid there is no pop-up: the question waits, and opens the next time a full AnkiGo screen is in front. Nothing is caught or released without your answer.",
                "Replacing a copy that is in your team, a favourite or the Exp. Share holder asks first (\"Release the old one?\"), with \"Keep its place\" ticked: the new Pokémon takes the old one's team slot, heart and Exp. Share. The same question comes when the \"better copies\" option meets such a copy. A shiny or a protected kind is never replaced.",
                "Your wishlist: you can add a Pokémon you have seen but not caught yet (open it in the Pokédex). Wishlisted Pokémon are always caught.",
                "Each wild Pokémon is shiny with a 1 in ${Tuning.SHINY_ODDS} chance.",
            ),
            listOf(
                GuideVisual(GuideVisualId.ITEM_ICON, "poke-ball"), GuideVisual(GuideVisualId.ITEM_ICON, "great-ball"),
                GuideVisual(GuideVisualId.ITEM_ICON, "ultra-ball"), GuideVisual(GuideVisualId.ITEM_ICON, "master-ball"),
                GuideVisual(GuideVisualId.TAG, "SHINY", "Shiny"),
            ),
        ),
        GuideChapter(
            MONEY, "Money and what's worth buying",
            "You earn money by answering cards and winning battles. Spend it in the Shop.",
            listOf(
                "+${nums.cashPerAnswer} for every answer, with any button, before daily tiers and item bonuses. Wrong answers never cost coins; cards not due in Free study pay no coins.",
                "+${nums.cashPerWin} for every wild Pokémon you defeat or catch, before daily tiers and item bonuses. Failed throws and cards not due in Free study pay no coins.",
                "The more you earn in a day, the slower it comes: in the first ${nums.cashTier} coins every answer pays ${nums.cashPerAnswer} coins and every win ${nums.cashPerWin} coins; in the next ${nums.cashTier} coins half of that (an answer ${nums.cashPerAnswer / 2.0} coins, paid in whole coins as they add up), then a third, a quarter and so on. It starts again each day.",
                "A Main holding an active Amulet Coin or Luck Incense doubles the coins paid for answers and wins (×2). The daily money tiers use earnings before this bonus, so it does not make earnings slow down sooner.",
                "Daily gift: reach your daily goal to receive one randomly chosen gift per game day. Items go into your Bag; TMs go into your TM collection. A gift can contain several copies of one item. It gives no coins and is never a Master Ball.",
                "Your goal streak counts earned daily goals; automatically protected days preserve that count. A longer streak raises the chance of an uncommon or rare gift. On day 1 it is common ${nums.giftCommonFirst}%, uncommon ${nums.giftUncommonFirst}% or rare ${nums.giftRareFirst}%. The chances move up day by day, to common ${nums.giftCommonLast}%, uncommon ${nums.giftUncommonLast}% and rare ${nums.giftRareLast}% after ${nums.giftMaxStreakDays} days in a row, and stay there.",
                "Common: ${DailyGift.poolText(Tuning.GIFT_COMMON)}. Uncommon: ${DailyGift.poolText(Tuning.GIFT_UNCOMMON)}. Rare: ${DailyGift.poolText(Tuning.GIFT_RARE_ITEMS)}, an evolution stone or a TM you don't own yet. Within each tier, each listed option is equally likely. The stone option picks a random Shop evolution stone; the TM option picks a random unowned Shop TM, or a stone if you own them all.",
                "The Shop draws up to ${Tuning.SHOP_BASE_SLOTS} items and ${Tuning.SHOP_BASE_SLOTS} TMs, plus one of each for every ${Tuning.SHOP_SLOTS_PER_LEVELS} trainer levels, alongside potions, daily balls, the deal and any weekend special. Refresh starts at ${Tuning.REROLL_PRICE} coins, costs ${Tuning.REROLL_PRICE_STEP} more each time, and resets the next game day; a TM costs ${Tuning.TM_PRICE}.",
                "Potions are always on sale. Each day also draws one regular ball.",
                "Daily balls: one regular (Poké ${Tuning.REGULAR_SHOP_WEIGHTS.getValue("poke-ball")}%, Great ${Tuning.REGULAR_SHOP_WEIGHTS.getValue("great-ball")}%, Ultra ${Tuning.REGULAR_SHOP_WEIGHTS.getValue("ultra-ball") }%), plus a ${Tuning.SPECIAL_SHOP_PERCENT}% chance of a special (normal prices ${Tuning.SHOP_PRICE.getValue("net-ball")}–${String.format(Locale.US, "%,d", Tuning.SHOP_PRICE.getValue("heal-ball"))} coins). Base prices before daily price variation: Poké Ball ${Tuning.BALL_PRICE.getValue("poke-ball")} coins, Great Ball ${Tuning.BALL_PRICE.getValue("great-ball")} coins, Ultra Ball ${String.format(Locale.US, "%,d", Tuning.BALL_PRICE.getValue("ultra-ball"))} coins. The Master Ball is never sold: you get one for reaching the top tier of Best streak (${nums.masterBallStreakDays} days in a row).",
                "Deal of the day (06:00–21:00) and Deal of the night (21:00–06:00): a different item in each period costs 20, 25, 30, 35 or 40% less (fixed for that offer all day). Consumables remain unlimited at that price. Everstone shares its one-per-game-day allowance with regular stock; rerolls and price changes never refill it.",
                "Item prices vary ±${Tuning.SHOP_VARIATION_PERCENT}% each day, rounded to ${Tuning.SHOP_ROUND_SMALL} below ${Tuning.SHOP_ROUND_THRESHOLD} and ${Tuning.SHOP_ROUND_LARGE} from ${Tuning.SHOP_ROUND_THRESHOLD}. Refresh keeps prices. The daily deal takes 20–40% off the day’s price; TMs stay fixed.",
                "Weekend special: on Saturday and Sunday the Shop sells one rare held item at a fixed price. Lucky Egg, Amulet Coin, Luck Incense, Macho Brace, Soothe Bell and the six Power items are only sold here, never in normal stock, rerolls or the deal of the day. The offer and price are the same on both days.",
                "Streak Freeze: hold up to 3. One freeze automatically protects one missed daily goal at your configured day cutoff; a whole gap must be covered or the streak ends and none are spent. Protected days add no goal days, reviews, gifts or achievement progress. Daily gifts have a 2% chance to give one; rewarded battle wins have a 0.2% chance, at most one per game day. At 3 held it is unavailable from gifts, battles and the Shop.",
                "Limited Shop cards show 1 left or Sold out. Weekend items allow one purchase across Saturday and Sunday. Everstone allows one per game day across all offers; TMs are owned forever. Streak Freeze allows one purchase per game day, costing 2,000 / 3,000 / 4,000 coins with 0 / 1 / 2 held. Limited items have no quantity selector. New days or weekends refill the allowance; reopening, restarting and rerolling do not.",
                "A small dot on the Shop tab means a new day's offers and deal are waiting and you haven't looked yet.",
                "Worth buying: evolution items for Pokémon that need them, and better balls when you hunt for a rare Pokémon. You don't need many healing items, because your Pokémon heal on their own (see Healing).",
            ),
            listOf(
                GuideVisual(GuideVisualId.ITEM_ICON, "amulet-coin", "Main: ×2 money"), GuideVisual(GuideVisualId.ITEM_ICON, "luck-incense", "Main: ×2 money"),
                GuideVisual(GuideVisualId.ITEM_ICON, "ultra-ball", "Balls"), GuideVisual(GuideVisualId.ITEM_ICON, "thunder-stone", "Evolution items"),
            ),
        ),
        GuideChapter(
            HEALING, "Healing",
            "Your Pokémon heal as you play, so you rarely need items.",
            listOf(
                "Level up: heals ${nums.levelUpHealPercent}% of max HP, on top of the HP the new level gives.",
                "Defeat or catch: your main heals ${nums.defeatHealPercent}% of max HP.",
                "Resting on the bench: team members that aren't the main heal ${n(nums.benchRestPercentPerAnswer)}% of their max HP with each answer. Partial points add up, so a Pokémon with 40 max HP gains 1 HP every 5 answers. Pokémon in the Box do not heal this way.",
                "If your main faints, it is healed fully and that wild Pokémon is lost.",
                "Healing items still work, and a healing move (Recover, Rest, Moonlight ...) heals the Pokémon that uses it in a battle. HP never goes above the maximum. A Full Restore also cures your main's burn, poison, paralysis, sleep or freeze in a battle; a Max Potion only heals. A healing item on your Main during a battle works at once, then the wild Pokémon uses one free move.",
            ),
            listOf(GuideVisual(GuideVisualId.HP_BAR, caption = "HP goes up in small steps"), GuideVisual(GuideVisualId.ITEM_ICON, "potion", "Healing items")),
        ),
        GuideChapter(
            ITEMS, "Items",
            "Items come from the Shop, as random drops, and as the Daily gift when you reach your daily goal.",
            listOf(
                "A new random item drops after ${Tuning.ITEM_COUNTDOWN.first} to ${Tuning.ITEM_COUNTDOWN.last} answers.",
                "Open any item in the Shop or the Bag to read exactly what it does, how to use it and what it means in a fight.",
                "In the Bag trade ${Tuning.POKE_FOR_GREAT} Poké Balls for one Great Ball, or ${Tuning.GREAT_FOR_ULTRA} Great Balls for one Ultra Ball. Specials cannot be traded.",
                "Balls catch Pokémon. Healing items restore HP. Evolution items evolve the Pokémon that need them; a few of them (Black Augurite, Peat Block, Metal Alloy) are never sold, you find them as random drops.",
                "Held items work while a Pokémon holds them, unless Embargo or Magic Room suppresses their effects in battle: Lucky Egg (×${n(Tuning.LUCKY_EGG)} XP), Everstone (never evolves), Macho Brace (×2 EVs), Soothe Bell (×1.5 Bond points) and more. A Pokémon holds one item at a time; giving it a new one sends the old one back to the Bag.",
                "Fossils bring back an old Pokémon at level ${Tuning.FOSSIL_LEVEL}.",
                "TMs are never used up: you keep them and can teach their move again.",
            ),
            listOf(
                GuideVisual(GuideVisualId.ITEM_ICON, "potion", "Healing"), GuideVisual(GuideVisualId.ITEM_ICON, "lucky-egg", "Held"),
                GuideVisual(GuideVisualId.ITEM_ICON, "helix-fossil", "Fossil"), GuideVisual(GuideVisualId.TAG, "HELD", "Holding an item"),
            ),
        ),
    )

    fun chapter(id: String): GuideChapter? = chapters().firstOrNull { it.id == id }
}
