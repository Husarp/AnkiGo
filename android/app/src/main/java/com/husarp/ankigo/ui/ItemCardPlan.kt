package com.husarp.ankigo.ui

import com.husarp.ankigo.game.BallTrade
import com.husarp.ankigo.game.GameState
import com.husarp.ankigo.game.ItemInfo
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Items

/**
 * What the item windows of the Shop and the Bag (PLAN 168 / 181 / 200 / 199, PLAN 210) decide, worked out without Views so the rules are
 * tested on the JVM: which item is open, the keys of the cards, the Bag's action button of each kind, the words around the cards.
 */

/** Which item's window is open: ONE at a time (opening another replaces it). It survives a re-render but the page drops it when it is left. */
internal class OpenCard {
    /** The key of the open item (see [CardKeys]), or null. */
    var key: String? = null
        private set

    /** The trade confirm of the open ball window is showing. */
    var confirming: Boolean = false
        private set

    /**
     * R14-B2 fix Y5: the open window stays although its item count has dropped to 0 (a trade used the last ball): it then says "You don't
     * own any ... yet". Set by [keepWhenEmpty]; ends with the window (closed, another one opened, [reset]). Ordinary rows with 0 vanish.
     */
    var keepEmpty: Boolean = false
        private set

    /** Call when the open window's own action can use up its item (a trade): the window then stays with a 0 count. */
    fun keepWhenEmpty() { keepEmpty = key != null }

    /** A tap on a tile, row or card of [k]: its window is the open one (the one that was open is replaced). */
    fun open(k: String) {
        key = k
        confirming = false
        keepEmpty = false
    }

    fun confirmTrade(on: Boolean) { confirming = on && key != null }

    fun reset() { key = null; confirming = false; keepEmpty = false }
}

/** The keys of the items: where the tile is (a Shop section or the Bag) plus what it shows, so the page can find the item again after a re-render. */
internal object CardKeys {
    const val DEAL = "deal"
    const val WEEKEND = "weekend"
    fun basic(tile: ShopPlan.Tile) = "basic:${tile.key}"
    fun special(tile: ShopPlan.Tile) = "special:${tile.key}"
    fun bagItem(item: String) = "bag:$item"
    fun bagTm(move: String) = "bagtm:$move"

    /** The item id of a [bagItem] key, or null for any other key. */
    fun bagItemId(key: String): String? = key.removePrefix("bag:").takeIf { key.startsWith("bag:") }

    /** The move id of a [bagTm] key, or null for any other key. */
    fun bagTmMove(key: String): String? = key.removePrefix("bagtm:").takeIf { key.startsWith("bagtm:") }
}

/** The action button of a Bag card, by kind of item (PLAN 181); the TM's is [TEACH]. */
internal enum class BagAct(val label: String, val fightOnly: Boolean, val offFight: String?) {
    THROW("Throw", true, "Balls are thrown from a fight"),
    USE("Use", true, "Used in a fight"),
    USE_ON("Use on…", false, null),
    GIVE("Give to…", false, null),
    REVIVE("Revive", false, null),
    TEACH("Teach…", false, null);

    companion object {
        fun of(kind: ItemKind?): BagAct? = when (kind) {
            ItemKind.BALL -> THROW
            ItemKind.ESCAPE -> USE
            ItemKind.HEAL, ItemKind.EVOLUTION -> USE_ON
            ItemKind.HELD -> GIVE
            ItemKind.FOSSIL -> REVIVE
            null -> null
        }
    }

    /**
     * Can the button be used now? A ball needs a wild Pokémon with HP left (as Game.throwBall), an escape item any wild Pokémon
     * (Game.escape); the rest work any time. With none, the button is shown disabled with [offFight].
     */
    fun usableNow(state: GameState): Boolean = when (this) {
        THROW -> state.battle?.let { it.wild.hp > 0 } == true
        USE -> state.battle != null
        else -> true
    }
}

internal object ItemCardPlan {
    /** The right side of the TM card's "Who can learn it" button. */
    fun whoCount(n: Int): String = if (n <= 0) "None of your Pokémon" else "$n of your Pokémon"

    /** The title of the "Who can learn it" list. */
    const val WHO_TITLE = "Who can learn it"

    /** The mark of a Pokémon that already knows the move, in the "Who can learn it" list. */
    const val KNOWS_IT = "knows it"

    /** The Shop's Buy button when the money is short: the toast. */
    fun poor(price: Int): String = "Not enough money (need ${Bits.fmt(price)} coins)"

    // ---- trade-up (Bag) ----

    /** The Trade button of a ball card: "Trade 5 Poké Balls for 1 Great Ball" / "Trade 3 Great Balls for 1 Ultra Ball" (an old ball's card counts as Great). */
    fun tradeLabel(o: BallTrade.Offer): String {
        val goes = if (o.from == "poke-ball") "Poké Balls" else "Great Balls"
        return "Trade ${o.need} $goes for 1 ${Items.name(o.give)}"
    }

    /** The small line under a Trade button that cannot be used. */
    fun tradeShort(o: BallTrade.Offer): String = "Need ${o.need}, you have ${o.have}"

    /** The confirm's button: "Trade 5 for 1". */
    fun tradeGo(o: BallTrade.Offer): String = "Trade ${o.need} for 1"

    /**
     * The one line of the confirm (`**` marks bold). It says exactly what goes and what comes, the Shop worth of both sides when no old
     * ball is in it (old balls are not sold), that it is on purpose worse than buying, and what you keep.
     */
    fun tradeConfirm(state: GameState, o: BallTrade.Offer): String {
        val goes = BallTrade.list(o.take)
        val sells = BallTrade.worth(o.take)
        val buys = BallTrade.worth(listOf(o.give to 1))
        val head = if (sells != null && buys != null) "$goes (${Bits.fmt(sells)} coins in the Shop) for 1 ${Items.name(o.give)} (${Bits.fmt(buys)} coins)." else "$goes for 1 ${Items.name(o.give)}."
        val left = BallTrade.keep(state, o)
        val keep = if (left > 0) "You keep $left ${ItemInfo.plural(Items.name(o.from), left)}." else "You keep no ${ItemInfo.plural(Items.name(o.from), 2)}."
        return "$head **On purpose worse than buying.** $keep"
    }

    // ---- TalkBack ----

    /** A tile, row or card that opens the item's window: [base] ("Great Ball, 50 % to catch, 700") + "Opens details". */
    fun detailsLabel(base: String): String = "$base. Opens details"
}

/** Splits a text with `**bold**` marks into parts; the stars are never part of a text. An unclosed mark runs to the end. */
internal object Marks {
    data class Part(val text: String, val bold: Boolean)

    fun split(text: String): List<Part> {
        val out = ArrayList<Part>()
        var bold = false
        for (piece in text.split("**")) {
            if (piece.isNotEmpty()) out += Part(piece, bold)
            bold = !bold
        }
        return out
    }
}
