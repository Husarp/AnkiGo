package com.husarp.ankigo.game

import com.husarp.ankigo.game.save.SaveCodec
import org.junit.Assert.*
import org.junit.Test

class R30FHeldItemsTest {
    private val data = TestData.world()
    private fun state() = TestData.state(data).apply {
        mons += TestData.mon(data, 16, 20, listOf("tackle"), "other")
        mons += TestData.mon(data, 19, 20, listOf("tackle"), "third")
        bag.clear()
        mons[0].heldItem = "soothe-bell"
    }
    @Test fun takingOffReturnsExactlyOneCopyAndSurvivesSaving() {
        val s = state()
        assertTrue(ItemUse.takeHeld(s, s.mons[0].uid).ok)
        assertFalse(ItemUse.takeHeld(s, s.mons[0].uid).ok)
        val restored = SaveCodec.decode(SaveCodec.encode(s))
        assertNull(restored.mons[0].heldItem)
        assertEquals(1, restored.count("soothe-bell"))
        assertEquals(1, restored.ownedItemCount("soothe-bell"))
    }
    @Test fun givingFromHolderIsAtomicAndReturnsRecipientsOldItem() {
        val s = state(); s.mons[1].heldItem = "everstone"
        assertTrue(ItemUse.giveHeld(data, s, "soothe-bell", s.mons[0].uid, "other").ok)
        val restored = SaveCodec.decode(SaveCodec.encode(s))
        assertNull(restored.mons[0].heldItem)
        assertEquals("soothe-bell", restored.mon("other")!!.heldItem)
        assertEquals(0, restored.count("soothe-bell"))
        assertEquals(1, restored.count("everstone"))
        assertEquals(1, restored.ownedItemCount("soothe-bell"))
    }
    @Test fun severalHoldersMoveOnlyTheSelectedCopyAndTotalsIncludeFreeCopies() {
        val s = state(); s.mons[1].heldItem = "soothe-bell"; s.bag["soothe-bell"] = 2
        assertEquals(4, s.ownedItemCount("soothe-bell"))
        assertTrue(ItemInfo.plain(ItemInfo.ownLine(s, "soothe-bell")).contains("2 held by"))
        assertTrue(ItemUse.giveHeld(data, s, "soothe-bell", "other", "third").ok)
        assertEquals("soothe-bell", s.mons[0].heldItem)
        assertNull(s.mon("other")!!.heldItem)
        assertEquals("soothe-bell", s.mon("third")!!.heldItem)
        assertEquals(4, s.ownedItemCount("soothe-bell"))
    }
    @Test fun manyHoldersKeepThePinnedCountLineShortWithoutLosingTheTotal() {
        val s = state()
        repeat(8) { n -> s.mons += TestData.mon(data, 16, 20, uid = "holder-$n").apply { heldItem = "soothe-bell" } }
        val line = ItemInfo.plain(ItemInfo.ownLine(s, "soothe-bell"))
        assertTrue(line.contains("9 Soothe Bells"))
        assertTrue(line.contains("9 held by"))
        assertTrue(line.endsWith("and 7 more"))
        assertEquals(9, s.itemHolders("soothe-bell").size)
    }
    @Test fun itemFoundPopupTotalsIncludeTheCopyAlreadyHeld() {
        val pool = Items.dropPool()
        val seed = (0..1000).first { Items.def(pool[kotlin.random.Random(it).nextInt(pool.size)])?.kind == ItemKind.HELD }
        val item = pool[kotlin.random.Random(seed).nextInt(pool.size)]
        val s = state().apply { mons[0].heldItem = item; itemCountdown = 1 }
        val events = mutableListOf<GameEvent>()
        Economy.onReview(data, s, kotlin.random.Random(seed), events)
        assertEquals(1, s.count(item))
        assertEquals(2, s.ownedItemCount(item))
        assertEquals(2, events.filterIsInstance<GameEvent.ItemFound>().single().count)
        assertEquals(2, PopupMath.finds(events).single().total)
    }
    @Test fun staleSourceMissingTargetAndSameHolderNeverMutateTheSave() {
        val s = state()
        for ((from, to) in listOf("other" to "third", s.mons[0].uid to "missing", s.mons[0].uid to s.mons[0].uid)) {
            val before = SaveCodec.encode(s)
            assertFalse(ItemUse.giveHeld(data, s, "soothe-bell", from, to).ok)
            assertEquals(before, SaveCodec.encode(s))
        }
    }
    @Test fun givingAFreeCopyRequiresInventoryAndPreservesTheTotal() {
        val s = state()
        assertFalse(ItemUse.giveHeld(data, s, "soothe-bell", null, "other").ok)
        s.bag["soothe-bell"] = 1
        assertTrue(ItemUse.giveHeld(data, s, "soothe-bell", null, "other").ok)
        assertEquals(0, s.count("soothe-bell")); assertEquals(2, s.ownedItemCount("soothe-bell"))
    }
}
