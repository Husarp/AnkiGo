package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Catching
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Shop
import com.husarp.ankigo.game.Tuning
import java.util.Calendar

/**
 * What the Shop tab shows, worked out without Views (R10 W11, so the rules are tested on the JVM): the price an item has today and the
 * price it would have without the deal, the tiles of "Today's specials", the number of tile columns, and the "ends at midnight" line.
 * The game rules themselves (deal, weekend special, always in stock) are [Shop].
 */
internal object ShopPlan {
    /** A price as the page shows it: [now] is what the buy costs; [was] is the normal price while a deal makes it cheaper (the old one is drawn struck through). */
    data class Price(val now: Int, val was: Int? = null) {
        val discounted: Boolean get() = was != null
    }

    /** The price of [item] on game day [day]: the deal price with the normal one as [Price.was] for that day's deal item, else just the normal price. */
    fun price(data: GameData, day: String, item: String): Price {
        val normal = data.price(item)
        val now = Shop.priceOf(data, day, item)
        return Price(now, if (now < normal) normal else null)
    }

    /** PLAN 171: the small line under a ball's tile, "50 % catch"; null for anything else. */
    fun chanceLine(item: String): String? = if (Items.def(item)?.kind == ItemKind.BALL) "${Catching.ballChance(item)} % catch" else null

    /** A TM costs [Tuning.TM_PRICE], never discounted. */
    fun tmPrice(): Price = Price(Tuning.TM_PRICE)

    /** One tile of "Today's specials": an item or a TM (the move id). */
    sealed class Tile {
        data class Item(val id: String) : Tile()
        data class Tm(val move: String) : Tile()
    }

    /** Today's items, then today's TMs, as one list of tiles in rows (the old separate TM list is gone). */
    fun specials(items: List<String>, tms: List<String>): List<Tile> = items.map { Tile.Item(it) } + tms.map { Tile.Tm(it) }

    /** The ribbon of the deal card: "−30 %". */
    fun ribbon(): String = "−${Tuning.DEAL_PERCENT} %"

    /**
     * Tiles per row: three, as in the mockup, unless the window is so narrow for its text size that a price like "4,800" would not fit in a third
     * of it (320 dp wide at font 2.0, 412 dp at 2.0 ...): then two. [widthDp] is the window width, [fontScale] the user's text scale.
     */
    fun columns(widthDp: Int, fontScale: Float): Int = if (widthDp / fontScale.coerceAtLeast(1f) >= 300f) 3 else 2

    /** Whole hours (at least 1) until the next game day starts ([dayStartHour], local time) from [now] (epoch ms). */
    fun hoursToNewDay(now: Long, dayStartHour: Int): Int {
        val next = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, dayStartHour)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return ((next - now + 3_599_999L) / 3_600_000L).toInt().coerceAtLeast(1)
    }

    /** "ends at midnight · 7 h" (the day starts at midnight) or "ends at 04:00 · 7 h" (another day-start hour). */
    fun endsText(dayStartHour: Int, hoursLeft: Int): String =
        "ends at " + (if (dayStartHour == 0) "midnight" else String.format(java.util.Locale.US, "%02d:00", dayStartHour)) + " · $hoursLeft h"

    /** What TalkBack reads for a shop tile or card: "Poké Ball, 200", "Super Potion, deal, 490 instead of 700", "TM Surf, owned". */
    fun describe(name: String, price: Price, owned: Boolean = false): String = when {
        owned -> "$name, owned"
        price.was != null -> "$name, deal, ${fmt(price.now)} instead of ${fmt(price.was)}"
        else -> "$name, ${fmt(price.now)}"
    }

    private fun fmt(n: Int): String = String.format(java.util.Locale.US, "%,d", n)

    /** The weekend card's second line. */
    const val WEEKEND_NOTE = "Rare in the daily stock · until Sunday night"
}
