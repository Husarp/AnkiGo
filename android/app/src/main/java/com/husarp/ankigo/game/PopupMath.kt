package com.husarp.ankigo.game

// R6-07 (items 19, 20): the pure parts of the "Achievement unlocked" / "Item found" popups: their title lines and which found items
// a batch of events brings. Pure, no Android.

/** An item found in a fight, for the popup. [total] is how many of it the bag holds now (the event's count). */
class ItemFind(val item: String, val total: Int)

/** R14-B2-D: a daily gift for the popup: [count] of [item], or the TM of the move [tm]; [streakDays] = the goal streak it came with. */
class GiftPop(val item: String?, val tm: String?, val count: Int, val streakDays: Int)

object PopupMath {
    /** The title line of the achievement popup: "Achievement unlocked", or "3 achievements unlocked". */
    fun achievementTitle(n: Int): String = if (n <= 1) "Achievement unlocked" else "$n achievements unlocked"

    /** The title line of the item popup: "Item found", or "2 items found". */
    fun itemTitle(n: Int): String = if (n <= 1) "Item found" else "$n items found"

    /** The title line of the daily gift popup. */
    const val GIFT_TITLE = "Daily gift"

    /** The daily gifts in [events], in order (one a day in practice). */
    fun gifts(events: List<GameEvent>): List<GiftPop> =
        events.filterIsInstance<GameEvent.DailyGift>().map { GiftPop(it.item, it.tm, it.count, it.streakDays) }

    /** The items found in [events], in order; the same item twice is one entry holding the last (highest) total. */
    fun finds(events: List<GameEvent>): List<ItemFind> = merge(emptyList(), events.filterIsInstance<GameEvent.ItemFound>().map { ItemFind(it.item, it.count) })

    /** [waiting] plus [fresh]: an item already waiting keeps its place and takes the newer total. */
    fun merge(waiting: List<ItemFind>, fresh: List<ItemFind>): List<ItemFind> {
        val out = ArrayList(waiting)
        for (f in fresh) {
            val at = out.indexOfFirst { it.item == f.item }
            if (at >= 0) out[at] = ItemFind(f.item, maxOf(out[at].total, f.total)) else out += f
        }
        return out
    }

    /** The line under the item's name: what it does. */
    fun description(item: String): String = ItemInfo.effect(item)
}
