package com.husarp.ankigo.ui

import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.Type

/** Which picture an item gets (PLAN 90). Pure; the look ticket turns it into pixels. */
sealed class ItemIconSpec {
    /** The PokeAPI sprite AnkiGo already downloads: file `items/<id>.png` in the pinned sprite repo, cached as `item/<fileName>`. */
    data class Sprite(val fileName: String) : ItemIconSpec()

    /** A TM: the type icon of its move's type (item 33's icon set). */
    data class TypeIcon(val type: Type) : ItemIconSpec()

    /** No sprite exists: AnkiGo's own drawn icon for [key] (the look ticket draws one per key in [ItemIconPlan.DRAWN]). */
    data class Drawn(val key: String) : ItemIconSpec()
}

/** The own symbol an item without a picture gets, by what the item is for (PLAN 90, pick 7D). */
enum class ItemSymbol { GEM, TAG, FOSSIL, BOTTLE, BALL, ESCAPE, BAG }

object ItemIconPlan {
    /** The kind symbol of [itemId]: gem = evolution item, tag = held item, fossil, bottle = healing, ball, escape; anything else the bag. */
    fun symbolOf(itemId: String): ItemSymbol = when (Items.def(itemId)?.kind) {
        ItemKind.EVOLUTION -> ItemSymbol.GEM
        ItemKind.HELD -> ItemSymbol.TAG
        ItemKind.FOSSIL -> ItemSymbol.FOSSIL
        ItemKind.HEAL -> ItemSymbol.BOTTLE
        ItemKind.BALL -> ItemSymbol.BALL
        ItemKind.ESCAPE -> ItemSymbol.ESCAPE
        null -> ItemSymbol.BAG
    }

    /**
     * Item ids the game can hold or show that PokeAPI's `sprites/items/` folder has NO file for, checked against the folder
     * listing at the pinned commit (DataStore.SPRITES_COMMIT, 904 files, 2026-10-17) for every id in [com.husarp.ankigo.game.Items.ALL]:
     * the Sweet / Tart Apple and Cracked / Chipped Pot evolution items and the Linking Cord. Each needs a drawn icon.
     * They show their own 16x16 pixel icon ([ItemIcons.drawn], PLAN 166A); the kind symbol badge ([ItemIcons.badge]) is the fallback, and
     * what any other item shows while its sprite is missing or not downloaded yet.
     */
    val DRAWN: Set<String> = setOf("sweet-apple", "tart-apple", "cracked-pot", "chipped-pot", "linking-cord")

    /** The icon of a bag / shop item. [noSprite] = ids known to have no sprite file (default [DRAWN]; tests pass made-up ones). */
    fun of(itemId: String, noSprite: Set<String> = DRAWN): ItemIconSpec =
        if (itemId in noSprite) ItemIconSpec.Drawn(itemId) else ItemIconSpec.Sprite("$itemId.png")

    /** The icon of a TM for [move] (Showdown move id): its move's type; a move missing from the data gets the drawn plain TM disc. */
    fun tm(data: GameData, move: String): ItemIconSpec =
        data.move(move)?.let { ItemIconSpec.TypeIcon(it.type) } ?: ItemIconSpec.Drawn("tm")
}
