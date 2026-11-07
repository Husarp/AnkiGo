package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Shop
import com.husarp.ankigo.game.Tuning
import java.util.Calendar

/**
 * What the Shop tab shows, worked out without Views (R10 W11, so the rules are tested on the JVM): the price an item has today and the
 * price it would have without the deal, the tiles of "Today's specials", and the countdown to the next game day.
 * The game rules themselves (deal, weekend special, always in stock) are [Shop].
 */
internal object ShopPlan {
    /** [now] is charged; [was] is the daily price before a deal, struck through. */
    data class Price(val now: Int, val was: Int? = null, val percent: Int? = null) {
        val discounted: Boolean get() = was != null
    }

    /** The price of [item] on game day [day]: the deal price with the normal one as [Price.was] for that day's deal item, else just the normal price. */
    fun price(data: GameData, day: String, item: String, time: Long = System.currentTimeMillis()): Price {
        val now = Shop.priceOf(data, day, item, time)
        val deal = Shop.dealFor(data, day, time)?.takeIf { it.item == item }
        return Price(now, deal?.oldPrice, deal?.percent)
    }

    fun price(data: GameData, state: com.husarp.ankigo.game.GameState, item: String): Price =
        if (item == com.husarp.ankigo.game.StreakFreeze.ITEM) Price(Shop.priceOf(data, state, item)) else price(data, state.day, item)

    /** A TM costs [Tuning.TM_PRICE], never discounted. */
    fun tmPrice(): Price = Price(Tuning.TM_PRICE)

    /** One tile of "Today's specials": an item or a TM (the move id). */
    sealed class Tile {
        /** What the tile shows, for the key of its card ([CardKeys]). */
        abstract val key: String
        data class Item(val id: String) : Tile() { override val key: String get() = "item:$id" }
        data class Tm(val move: String) : Tile() { override val key: String get() = "tm:$move" }
    }

    /** Today's items, then today's TMs, as one list of tiles in rows (the old separate TM list is gone). */
    fun specials(items: List<String>, tms: List<String>): List<Tile> = items.map { Tile.Item(it) } + tms.map { Tile.Tm(it) }

    /** The small pill beside the price; never part of the item effect text. */
    fun ribbon(percent: Int): String = "−$percent %"

    /** Whole minutes (at least 1) until the next game day starts ([dayStartHour], local time) from [now] (epoch ms). */
    fun minutesToNewDay(now: Long, dayStartHour: Int): Int {
        val next = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, dayStartHour)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return ((next - now + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }

    fun hoursToNewDay(now: Long, dayStartHour: Int): Int = (minutesToNewDay(now, dayStartHour) + 59) / 60

    fun countdown(minutesLeft: Int): String = "${minutesLeft / 60} h ${minutesLeft % 60} min"

    fun endsText(minutesLeft: Int): String = "Ends in ${countdown(minutesLeft)}"

    fun dealEndsText(now: Long): String {
        val minutes = ((Shop.dealEndsAt(now) - now + 59_999L) / 60_000L).coerceAtLeast(1)
        return "Ends in ${minutes / 60} h ${minutes % 60} min"
    }

    /** The offer lasts through Sunday’s game day, including a custom day-start hour and DST. */
    fun weekendEndsText(endsDay: String, dayStartHour: Int, now: Long = System.currentTimeMillis()): String {
        val end = java.time.LocalDate.parse(endsDay).plusDays(1).atTime(dayStartHour, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val minutes = ((end - now + 59_999L) / 60_000L).coerceAtLeast(1)
        return if (minutes >= 1440) "Ends in ${minutes / 1440} d ${minutes % 1440 / 60} h"
            else "Ends in ${minutes / 60} h ${minutes % 60} min"
    }

    /** What TalkBack reads for a shop tile or card: "Poké Ball, 200", "Super Potion, deal, 490 instead of 700", "TM Surf, owned". */
    fun describe(name: String, price: Price, owned: Boolean = false): String = when {
        owned -> "$name, owned"
        price.was != null -> "$name, deal, ${fmt(price.now)} instead of ${fmt(price.was)}"
        else -> "$name, ${fmt(price.now)}"
    }

    private fun fmt(n: Int): String = String.format(java.util.Locale.US, "%,d", n)

    /**
     * PLAN 237 (R18-H): the money pill's FIRST text, when the bar is built: the real amount when the game is loaded ([cash] not null),
     * else "" (the pill stays hidden until the amount is known, never a "0" that jumps to the amount and changes the pill's width).
     */
    fun pillText(cash: Int?): String = if (cash == null) "" else fmt(cash)

    /** [pillText] for a page that builds its bar: the amount only while the game is [ready] (the Shop and the Pokémon tab share it, R18 RV-6). */
    fun firstPill(ready: Boolean, cash: Int?): String = pillText(if (ready) cash else null)

}
