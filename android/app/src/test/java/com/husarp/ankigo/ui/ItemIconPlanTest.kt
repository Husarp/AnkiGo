package com.husarp.ankigo.ui

import com.husarp.ankigo.R
import com.husarp.ankigo.game.ItemKind
import com.husarp.ankigo.game.Items
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemIconPlanTest {
    private val data = TestData.world()

    @Test fun tmGetsItsMovesType() {
        assertEquals(ItemIconSpec.TypeIcon(Type.ELECTRIC), ItemIconPlan.tm(data, "thunderbolt"))
        assertEquals(ItemIconSpec.TypeIcon(Type.GHOST), ItemIconPlan.tm(data, "shadowball"))
    }

    @Test fun tmWithAnUnknownMoveIsDrawn() {
        assertEquals(ItemIconSpec.Drawn("tm"), ItemIconPlan.tm(data, "no-such-move"))
    }

    @Test fun ballAndOtherItemsUseTheirSprite() {
        assertEquals(ItemIconSpec.Sprite("poke-ball.png"), ItemIconPlan.of("poke-ball"))
        assertEquals(ItemIconSpec.Sprite("thunder-stone.png"), ItemIconPlan.of("thunder-stone"))
    }

    @Test fun anItemWithoutASpriteIsDrawn() {
        assertEquals(ItemIconSpec.Drawn("made-up-thing"), ItemIconPlan.of("made-up-thing", noSprite = setOf("made-up-thing")))
        assertEquals(ItemIconSpec.Sprite("made-up-thing.png"), ItemIconPlan.of("made-up-thing"))
    }

    @Test fun everyCatalogueItemGetsSpriteOrDrawnAndDrawnOnesAreRealItems() {
        for (id in Items.ALL.keys) {
            val spec = ItemIconPlan.of(id)
            assertTrue(id, spec is ItemIconSpec.Sprite || spec == ItemIconSpec.Drawn(id))
        }
        assertTrue(ItemIconPlan.DRAWN.all { it in Items.ALL })
        assertEquals(ItemIconSpec.Drawn("cracked-pot"), ItemIconPlan.of("cracked-pot"))
    }

    @Test fun everyDrawnItemMapsToItsOwnPixelIcon() {
        val expect = mapOf(
            "sweet-apple" to R.drawable.item_sweet_apple, "tart-apple" to R.drawable.item_tart_apple,
            "cracked-pot" to R.drawable.item_cracked_pot, "chipped-pot" to R.drawable.item_chipped_pot,
            "linking-cord" to R.drawable.item_linking_cord,
        )
        assertEquals(ItemIconPlan.DRAWN, expect.keys)
        for ((id, res) in expect) assertEquals(id, res, ItemIcons.drawnRes(id))
        assertEquals(5, expect.values.toSet().size)
    }

    @Test fun otherItemsAndUnknownIdsGetNoPixelIconSoTheyKeepTheBadge() {
        assertEquals(null, ItemIcons.drawnRes("poke-ball"))
        assertEquals(null, ItemIcons.drawnRes("no-such-item"))
        assertEquals(ItemSymbol.BAG, ItemIconPlan.symbolOf("no-such-item"))
    }

    @Test fun everyCatalogueItemHasAKindSymbolMatchingItsKind() {
        val expect = mapOf(
            ItemKind.EVOLUTION to ItemSymbol.GEM, ItemKind.HELD to ItemSymbol.TAG, ItemKind.FOSSIL to ItemSymbol.FOSSIL,
            ItemKind.HEAL to ItemSymbol.BOTTLE, ItemKind.BALL to ItemSymbol.BALL, ItemKind.ESCAPE to ItemSymbol.ESCAPE,
        )
        assertEquals(ItemKind.entries.toSet(), expect.keys)
        for ((id, def) in Items.ALL) assertEquals(id, expect[def.kind], ItemIconPlan.symbolOf(id))
        for (id in ItemIconPlan.DRAWN) assertTrue(id, ItemIconPlan.symbolOf(id) == ItemSymbol.GEM)
        assertEquals(ItemSymbol.BAG, ItemIconPlan.symbolOf("no-such-item"))
    }

    @Test fun everySymbolHasADrawableAndAColour() {
        for (k in ItemSymbol.entries) {
            assertTrue(k.name, ItemIcons.symbol(k) != 0)
            assertTrue(k.name, ItemIcons.colorRes(k) != 0)
        }
    }

    @Test fun aTmOfEveryMoveCategoryResolvesToItsType() {
        for (m in data.moves.values) assertEquals(m.id, ItemIconSpec.TypeIcon(m.type), ItemIconPlan.tm(data, m.id))
    }

    @Test fun discSymbolIsDarkOnPaleAndWhiteOnDeepDiscs() {
        for (t in Type.entries) {
            val disc = Ui.typeColor(t)
            assertTrue(t.name, TypeInk.contrast(ItemIcons.discInk(disc), disc) >= 3.0)
        }
    }
}
