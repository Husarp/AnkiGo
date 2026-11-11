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
            "Answer cards to battle wild Pokémon. Your Main is the Pokémon fighting for you.",
            listOf(
                "A round takes ${Tuning.CARDS_PER_ROUND} answers. Your answers decide which Pokémon can move.",
                "Good and Easy let your Main attack. Each Good adds ×${x(Tuning.MAIN_POWER[3])} damage; each Easy adds ×${x(Tuning.MAIN_POWER[4])}. These add together within a round.",
                "Again and Hard let the wild Pokémon attack. Each Again adds ×${x(Tuning.WILD_POWER[1])} damage; each Hard adds ×${x(Tuning.WILD_POWER[2])}. These add together too.",
                "${Tuning.WILD_AGAIN_STREAK} Again answers in a row raise the wild Pokémon’s Attack by ${Tuning.WILD_ATTACK_STAGES} stage, once per streak. Good, Easy or Hard ends the streak; OK leaves it unchanged.",
                "After ${Tuning.WILD_STRONGEST_ROUNDS} rounds without Good or Easy, the wild Pokémon tries its strongest available attack. Good or Easy resets the count; rounds with only OK pause it.",
                "Both can attack in the same round. With only Good and Easy answers, the wild Pokémon gets no move. Poison, burn and other ongoing effects can still hurt you.",
                "The OK button on a new card counts toward the round but powers neither side and keeps your combo. OK + Good lets your Main move; OK + Again lets the wild Pokémon move. With only OK answers, neither moves, but ongoing effects still apply.",
                "Switching: before your first answer in a battle, changing your Main is free. Later, a switch waits for your answer if a card is showing. The wild Pokémon then gets one free move against the new Main at ×${x(Tuning.SWITCH_HIT_POWER)} damage. Switching uses no answer slot and keeps your round progress. At a battle result, it happens at once; the next battle waits for Continue.",
                "Using a healing or evolution item on your Main during a battle takes effect immediately, then gives the wild Pokémon one free move. Your round progress stays the same. Giving a held item is free; balls follow the Catching rules.",
                "Free switches: the wild Pokémon gets no free move before your first answer, when replacing a fainted Main, or when no wild Pokémon is left to fight.",
                "Move choice: Settings → Game → Battle applies to both Pokémon. Smart is the default: when both an attack and a useful status move are available, it attacks ${pct(Tuning.SMART_DAMAGE_SHARE)}% of the time. Smartest also favours stronger attacks. Random picks moves by chance.",
                "Smart choices: Smart and Smartest avoid ineffective status moves and attacks blocked by types or abilities, such as Wonder Guard. If your Main cannot damage the target, it uses Struggle; the wild Pokémon may fall back to another move. Accuracy and evasion changes stop at ±${Tuning.SMART_ACC_EVA_LIMIT}.",
                "Battle XP: defeat or catch the wild Pokémon to earn XP for your Main. Stronger opponents generally give more XP.",
                "Exp. Share: choose another Pokémon to receive ${pct(Tuning.XP_SHARE_PART)}% of the battle XP, leaving the same share for your Main. A Lucky Egg holder earns ×${n(Tuning.LUCKY_EGG)} XP. Cards not due in Free study give ${pct(Tuning.PRACTICE_XP_PART)}% Pokémon and trainer XP, with no coins.",
            ),
            listOf(
                GuideVisual(GuideVisualId.HP_BAR, caption = "HP: health left before fainting"), GuideVisual(GuideVisualId.XP_BAR, caption = "XP: progress to the next level"),
                GuideVisual(GuideVisualId.LEVEL_COLOURS, caption = "Level colours show progress"),
                GuideVisual(GuideVisualId.STATUS_BADGES, caption = "Status: tap a badge for what it does"),
                GuideVisual(GuideVisualId.TYPE_CHIPS, "FIRE", "Type: see Types"),
            ),
        ),
        GuideChapter(
            MOVES, "Moves and effects",
            "Moves can deal damage, change stats or leave lasting effects. Tap a move or status badge to learn what it does.",
            listOf(
                "Stat stages: stat boosts and drops add up and last until the Pokémon switches out, faints or the battle ends. For example, +1 Attack means ×${com.husarp.ankigo.game.StageText.factor(1)} Attack; −1 means ×${com.husarp.ankigo.game.StageText.factor(-1)}.",
                "Rounds: in move and ability descriptions, a turn is one round (${Tuning.CARDS_PER_ROUND} answers). Timed effects count down at the end of each round.",
                "Charge moves: Dig and Fly usually charge first, then attack on the Pokémon’s next move. Charging does not make it untouchable. Some moves can skip charging in the right weather. Recharge moves, such as Hyper Beam, make the Pokémon rest on its next move after a hit.",
                "Flinch: if a move makes its target flinch before the target moves that round, the target loses its move.",
                "Confusion: a Pokémon has a 1 in ${Math.round(1 / Tuning.CONFUSION_SELF_HIT)} chance to hurt itself instead of moving. It recovers after ${Tuning.CONFUSION_TURNS.first} to ${Tuning.CONFUSION_TURNS.last} attempts to move. In love: a Pokémon cannot move ${pct(Tuning.LOVE_STOPS)}% of the time; this only affects male–female pairs.",
                "Protect blocks most moves aimed at the user for the rest of the round. It helps when the opponent moves later that round; repeated use is less reliable. Substitute costs ${MoveText.part(1, SideMoves.SUBSTITUTE_DIVISOR)} your max HP to absorb hits and block most status moves.",
                "Screens: Reflect halves physical damage; Light Screen halves special damage. Aurora Veil halves both, but needs hail or snow to start. Each lasts ${SideMoves.SCREEN_TURNS} rounds.",
                "Weather lasts ${FieldRules.WEATHER_TURNS} rounds and terrain ${FieldRules.TERRAIN_TURNS} rounds. Rain favours Water moves; sun favours Fire. Sandstorm and hail can deal damage at the end of a round; snow does not. Terrain affects Pokémon on the ground. Tap the field badge for its current effects.",
                "Binding moves: Wrap and similar moves deal ${MoveText.part(1, Tuning.BIND_DIVISOR)} the target’s max HP per round for ${Tuning.BIND_TURNS.first} to ${Tuning.BIND_TURNS.last} rounds. They do not prevent switching.",
                "Abilities: both Pokémon can have battle abilities, such as Intimidate or Levitate. Read the ability in a Pokémon’s details to see its effect in AnkiGo, including when it has none.",
                "Unused moves: moves that do not apply to these battles are skipped; their descriptions explain why. If a Pokémon knows only these moves, your Main uses Struggle and a wild Pokémon’s move has no effect.",
            ),
            listOf(GuideVisual(GuideVisualId.STATUS_BADGES, caption = "Status: tap a badge for what it does")),
        ),
        GuideChapter(
            LEVELS, "Levels and evolutions",
            "Earn XP to raise your Pokémon up to level ${Tuning.MAX_LEVEL}. Some can also evolve.",
            listOf(
                "Level evolutions: reach the required Pokémon level and Bond level. Check the Evolution section in its details for any missing requirements.",
                "Bond: your Main earns +${com.husarp.ankigo.game.Bond.CARD_POINTS} point per answered card in battle and +${com.husarp.ankigo.game.Bond.WIN_POINTS} per win. Bond Level 2 unlocks normal evolutions; Bond evolutions need Level 3 or 4. Higher Bond also gives bonus XP and coins, and can help your Main survive a hit. Level 3 reveals the species’ matchups.",
                "Item evolutions: keep the required item in your Bag, then open the Pokémon’s Evolution section and choose Evolve. The item is consumed.",
                "Ready to evolve: the green mark means an evolution is available. Evolution never happens automatically: choose Evolve for the branch you want. Some branches need a time of day, gender or learned move. Trade evolutions use a Linking Cord unless another item is required.",
                "Evolution restores the Pokémon to full HP.",
            ),
            listOf(GuideVisual(GuideVisualId.TAG, "CAN_EVOLVE", "Ready to evolve"), GuideVisual(GuideVisualId.ITEM_ICON, "thunder-stone", "An evolution stone"), GuideVisual(GuideVisualId.ITEM_ICON, "everstone", "Boosts battle XP for species with no evolution branch")),
        ),
        GuideChapter(
            TYPES, "Types and matchups",
            "There are ${Type.entries.size} types. Each move has a type; a Pokémon can have one or two.",
            listOf(
                "Type advantage: a super-effective move deals ×2 damage; a resisted move deals ×0.5.",
                "Immunity: some types take no damage from certain moves. Normal moves, for example, cannot hit Ghost Pokémon.",
                "Same-type bonus (STAB): a damaging move that matches one of the user’s types deals ×${n(Tuning.STAB)} damage.",
                "Two types: combine both matchups. A move can be extra strong, resisted, neutral or completely blocked.",
            ),
            listOf(GuideVisual(GuideVisualId.MATCHUP_TABLE, caption = "Move types against Pokémon types")),
        ),
        GuideChapter(
            STATS, "Stats, IVs, EVs and natures",
            "Pokémon of the same species can have different strengths. Check their stats before choosing your Main.",
            listOf(
                "Six stats: HP is health. Attack and Defense affect physical moves; Sp. Atk and Sp. Def affect special moves. Speed helps decide who moves first.",
                "IVs: a Pokémon’s natural stat potential. Higher is better. Training does not change IVs, but Heal Ball and Luxury Ball can improve them when catching.",
                "Wild levels: usually ${-Tuning.WILD_LEVEL_SPREAD.first} below to ${Tuning.WILD_LEVEL_SPREAD.last} above your Main, within levels 1–${Tuning.MAX_LEVEL}. Wild Pokémon already have some EVs and keep them when caught.",
                "EVs: stat training earned by your Main when you defeat or catch a wild Pokémon. Each stat can gain up to ${Tuning.EV_STAT_CAP} EVs, with ${Tuning.EV_TOTAL_CAP} total. Macho Brace boosts EV gains; Power items add EVs to a particular stat.",
                "Natures: most raise one stat and lower another. Others leave stats unchanged and give a small perk instead. Read the nature in the Pokémon’s details.",
            ),
            listOf(GuideVisual(GuideVisualId.STAT_BARS, caption = "The six stats"), GuideVisual(GuideVisualId.ITEM_ICON, "macho-brace", "Doubles EV gain")),
        ),
        GuideChapter(
            CATCHING, "Catching and balls",
            "Throw a ball while a wild Pokémon still has HP. When you beat it, your catch rule decides whether to keep it.",
            listOf(
                "Catch chance: Poké Ball ${Tuning.BALL_CHANCE.getValue("poke-ball")}%, Great Ball ${Tuning.BALL_CHANCE.getValue("great-ball")}%, Ultra Ball ${Tuning.BALL_CHANCE.getValue("ultra-ball")}%, Master Ball ${Tuning.BALL_CHANCE.getValue("master-ball")}%. Special balls can add bonuses; lowering HP or applying a status does not help.",
                "Each throw spends a regular ball. If the catch fails, the wild Pokémon runs away and you earn no battle rewards. Throwing gives it no extra move.",
                "Master Ball: earn one from the top tier of Best streak by reaching ${nums.masterBallStreakDays} daily goals in your streak. The reward goes straight to your Bag, once. The Shop never sells it.",
                "Special balls: add one to a regular ball, such as an Ultra Heal Ball, for an extra effect. Read the special ball’s description before throwing. Both are spent, though Repeat Ball can return.",
                "Catch rewards: catching gives the same Pokémon XP, trainer XP, EVs, Bond points and coins as defeating. A successful throw counts as a win too.",
                "Catch rule: open Settings → Game → \"When you beat a wild Pokémon\". Choose Always catch, Catch only new species (the default), Catch new species and better copies, Ask me, or Defeat. Better copies means higher-level copies. Shinies and protected kinds are caught even with Defeat selected.",
                "Catch animation: the blue diamond marks the Great Ball group: Baby, Starters and Pokémon with base stat total 500+. Other ordinary Pokémon have no mark. Automatic catches use a ball that matches the Pokémon’s rarity. This spends no balls from your Bag. Throws show the ball you chose. Name marks can be hidden in Settings → Battle → Pokémon rarity marks.",
                "Ask me: new species are caught automatically. For a better copy of a species you own (higher level or higher total IVs), choose Catch, Catch and replace, or Don't catch. Dismissing the question means Don't catch. Rewards are paid immediately. In a small battle window or AnkiDroid, the question waits until you return to full-screen AnkiGo.",
                "Replacing copies: before releasing a team member, favourite or Exp. Share holder, AnkiGo asks \"Release the old one?\". Leave \"Keep its place\" checked to transfer its team slot, favourite heart and Exp. Share to the new copy. Shiny and protected copies are never replaced.",
                "Wishlist: open a Pokémon you have seen but not caught in the Pokédex to add it. Your catch rule always keeps wishlisted Pokémon.",
                "Discoveries: defeat or catch a species to unlock its stats and other details; any form counts. Wild Matchups appear after your first attack, or immediately with Bond Level 3. Each type starts as ??? and is remembered when your move hits that species, or one you own is hit by that type. A catch or defeat alone reveals no matchups. Bond Level 3 reveals all matchups for that species and its forms.",
                "Shiny Pokémon: each wild encounter has a 1 in ${Tuning.SHINY_ODDS} chance to be shiny.",
            ),
            listOf(
                GuideVisual(GuideVisualId.ITEM_ICON, "poke-ball"), GuideVisual(GuideVisualId.ITEM_ICON, "great-ball"),
                GuideVisual(GuideVisualId.ITEM_ICON, "ultra-ball"), GuideVisual(GuideVisualId.ITEM_ICON, "master-ball"),
                GuideVisual(GuideVisualId.TAG, "SHINY", "Shiny"),
            ),
        ),
        GuideChapter(
            MONEY, "Money and what's worth buying",
            "Earn coins by answering cards and winning battles. Spend them in the Shop.",
            listOf(
                "Answers: earn ${nums.cashPerAnswer} coins per answer before daily reductions and bonuses. Every answer button pays; wrong answers never cost coins. Cards not due in Free study pay none.",
                "Wins: earn ${nums.cashPerWin} coins per defeat or catch before daily reductions and bonuses. Failed throws give no win rewards; Free study battles from cards not due give no coins.",
                "Daily earnings: base rewards start at ${nums.cashPerAnswer} coins per answer and ${nums.cashPerWin} per win. After ${nums.cashTier} coins, the rate halves, then falls to a third, a quarter and so on with each further ${nums.cashTier}. Fractions add up to whole coins. The rate resets each game day.",
                "Money boosters: an active Amulet Coin or Luck Incense on your Main doubles base answer and win rewards. The bonus does not make daily reductions arrive sooner.",
                "Daily gift: reach your daily goal to receive one random gift per game day. Items go into your Bag; TMs go into your TM collection. Gifts can include several of one item, but never coins or a Master Ball.",
                "Goal streak: longer streaks improve your usual gift chances. Day 1: common ${nums.giftCommonFirst}%, uncommon ${nums.giftUncommonFirst}%, rare ${nums.giftRareFirst}%. After ${nums.giftMaxStreakDays} goals in a streak: common ${nums.giftCommonLast}%, uncommon ${nums.giftUncommonLast}%, rare ${nums.giftRareLast}%. Protected days keep your streak but do not add goals. A Streak Freeze can sometimes replace the usual gift.",
                "Gift contents: common gifts include ${DailyGift.poolText(Tuning.GIFT_COMMON)}. Uncommon gifts include ${DailyGift.poolText(Tuning.GIFT_UNCOMMON)}. Rare gifts include ${DailyGift.poolText(Tuning.GIFT_RARE_ITEMS)}, an evolution stone or a TM you do not own.",
                "Shop stock: starts with up to ${Tuning.SHOP_BASE_SLOTS} items and ${Tuning.SHOP_BASE_SLOTS} TMs, plus one of each per ${Tuning.SHOP_SLOTS_PER_LEVELS} trainer levels. Refresh starts at ${Tuning.REROLL_PRICE} coins, costs ${Tuning.REROLL_PRICE_STEP} more each time and resets the next game day. TMs cost ${Tuning.TM_PRICE} coins.",
                "Basics: Poké Balls and Potions are always on sale.",
                "Daily balls: one Great or Ultra Ball is on sale each game day, with a ${Tuning.SPECIAL_SHOP_PERCENT}% chance of a special ball alongside it. Refresh does not change these offers.",
                "Daily deals: a daytime and a nighttime offer each discount an item by ${Tuning.DEAL_PERCENTS.first()}–${Tuning.DEAL_PERCENTS.last()}%. Check the Shop for the current offer and when it changes.",
                "Prices: item prices vary by up to ${Tuning.SHOP_VARIATION_PERCENT}% each day. Refresh keeps prices. TMs stay at a fixed price.",
                "Weekend special: one rare held item is on sale on Saturday and Sunday. This is where to buy Lucky Egg, Amulet Coin, Luck Incense, Macho Brace, Soothe Bell and Power items. The offer stays the same all weekend.",
                "Streak Freeze: keep up to ${com.husarp.ankigo.game.StreakFreeze.CAP}. Each protects one missed daily goal automatically. You need enough to cover every missed day in a gap; otherwise your streak ends and none are spent. Protected days give no gifts or goal progress. Find freezes in daily gifts, rewarded battles or the Shop.",
                "Purchase limits: weekend items allow one purchase per weekend; Everstone allows one per game day across all offers. TMs stay yours forever. Buy Streak Freezes one at a time up to the limit; each costs more as you hold more. Refreshing never restores a sold-out item.",
                "Shop reminder: a dot on the Shop tab means you have not looked at the new day’s offers yet.",
                "Worth buying: save for evolution items your Pokémon need, better balls for Pokémon you want, or a held item that helps your team. Check Healing before stocking up on potions.",
            ),
            listOf(
                GuideVisual(GuideVisualId.ITEM_ICON, "amulet-coin", "Main: ×2 money"), GuideVisual(GuideVisualId.ITEM_ICON, "luck-incense", "Main: ×2 money"),
                GuideVisual(GuideVisualId.ITEM_ICON, "ultra-ball", "Balls"), GuideVisual(GuideVisualId.ITEM_ICON, "thunder-stone", "Evolution items"),
            ),
        ),
        GuideChapter(
            HEALING, "Healing",
            "Your Pokémon recover HP as you play. Healing items can help when you need HP right away.",
            listOf(
                "Level up: restores ${nums.levelUpHealPercent}% of max HP, plus the extra HP gained from the new level.",
                "Win: your Main heals ${nums.defeatHealPercent}% of max HP after a defeat or catch.",
                "Bench rest: team members other than your Main heal ${n(nums.benchRestPercentPerAnswer)}% of max HP per answer. Small fractions carry over until they make a whole HP. Pokémon in the Box do not get bench rest.",
                "Fainting: your Main returns to full HP, but you lose that wild Pokémon.",
                "Healing items and moves: restore HP up to its maximum. Full Restore also cures burn, poison, paralysis, sleep or freeze; Max Potion only restores HP. Using a healing item on your Main during a battle gives the wild Pokémon a free move afterward.",
            ),
            listOf(GuideVisual(GuideVisualId.HP_BAR, caption = "HP: current health"), GuideVisual(GuideVisualId.ITEM_ICON, "potion", "Healing items")),
        ),
        GuideChapter(
            ITEMS, "Items",
            "Find items as you study, earn a Daily gift by reaching your goal, or buy items in the Shop.",
            listOf(
                "Item finds: receive a random item every ${Tuning.ITEM_COUNTDOWN.first} to ${Tuning.ITEM_COUNTDOWN.last} answers.",
                "Item details: tap an item in the Shop or Bag to see its effect and how to use it.",
                "Ball trades: in the Bag, trade ${Tuning.POKE_FOR_GREAT} Poké Balls for one Great Ball, or ${Tuning.GREAT_FOR_ULTRA} Great Balls for one Ultra Ball. Special balls cannot be traded.",
                "Evolution items: use the item shown in a Pokémon’s Evolution section. Black Augurite, Peat Block and Metal Alloy come from random finds; the Shop does not sell them.",
                "Held items: a Pokémon can hold one item. Giving another returns the old one to the Bag. Lucky Egg boosts XP, Macho Brace boosts EVs and Soothe Bell boosts Bond points. Embargo and Magic Room can temporarily stop held items from working in battle.",
                "Fossils: use one to revive an ancient Pokémon at level ${Tuning.FOSSIL_LEVEL}.",
                "TMs: keep them forever and use them to teach their moves again. The Pokémon must be able to learn the move.",
            ),
            listOf(
                GuideVisual(GuideVisualId.ITEM_ICON, "potion", "Healing"), GuideVisual(GuideVisualId.TAG, "HELD", "Worn"),
                GuideVisual(GuideVisualId.ITEM_ICON, "helix-fossil", "Fossil"), GuideVisual(GuideVisualId.TAG, "HELD", "Holding an item"),
            ),
        ),
    )

    fun chapter(id: String): GuideChapter? = chapters().firstOrNull { it.id == id }
}
