package com.husarp.ankigo.game

/**
 * Plain-words text for the Shop's item popup (R3-X2 item 3): what an item does and how it is used. Pure, no Android.
 * The short effect line is [ItemDef.note] (e.g. "Heals 20 HP"); [howToUse] says where in the app it is used.
 */
object ItemInfo {
    /** What the item does: its catalogue note, or a generic line for an item the catalogue does not know. */
    fun effect(item: String): String = Items.def(item)?.note?.takeIf { it.isNotBlank() } ?: "No description."

    /** Where and how the item is used (one sentence). */
    fun howToUse(item: String): String = when (Items.def(item)?.kind) {
        ItemKind.BALL -> "Throw it from the Bag at a wild Pokémon in battle. A good throw catches it."
        ItemKind.HEAL -> "Use it from the Bag on one of your Pokémon."
        ItemKind.EVOLUTION -> "Use it from the Bag on a Pokémon that evolves with it."
        ItemKind.HELD -> "Give it to a Pokémon from the Bag. A Pokémon holds one item at a time."
        ItemKind.ESCAPE -> "Use it from the Bag to leave the wild Pokémon in battle and meet a new one."
        ItemKind.FOSSIL -> "Use it from the Bag to revive an ancient Pokémon at Lv 5."
        null -> ""
    }

    /** What a TM does, for the popup: it teaches a move and is never used up. */
    fun tmHowToUse(): String = "Teaches this move to a Pokémon that can learn it. A TM is never used up."
}
