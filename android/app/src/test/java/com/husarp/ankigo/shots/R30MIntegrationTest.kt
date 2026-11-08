package com.husarp.ankigo.shots

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R30MIntegrationTest {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun battleBagCountsEachCopyOnceAndKeepsBothLanesActions() {
        val ctx = Shots.files(paparazzi.context)
        FakeGame.install(ctx)
        val state = GameRepo.state!!
        state.bag.clear()
        state.mons.forEach { it.heldItem = null }
        state.mons.take(2).forEach { it.heldItem = "soothe-bell" }
        state.bag["soothe-bell"] = 1
        state.bag["poke-ball"] = 3
        state.bag["water-stone"] = 1
        val received = mutableListOf<List<GameEvent>>()
        val page = ShopPage(ctx, NoHost, bagOnly = true, onBattleEvents = { received += it }, fightAvailable = { false })
        val bag = BagActions(page, MonActions(page), page, onBattleEvents = { received += it }, fightAvailable = { false })
        val body = LinearLayout(ctx)
        bag.fill(body)
        val heldHeader = all(body).filterIsInstance<TextView>().single { it.text.toString() == "HELD" }
        val headerWords = all(heldHeader.parent as View).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(headerWords.toString(), "3 items" in headerWords)
        val held = bag.spec(CardKeys.bagItem("soothe-bell"))!!
        assertNull("Shared held controls replace the old Give action", held.act)
        val footer = ItemCard.footer(page, held)
        assertEquals(1, all(footer).count { it.contentDescription == "Take off" })
        assertEquals(1, all(footer).count { it.contentDescription == "Give to another Pokémon" })
        assertNull("Evolution item removal survives the merge", bag.spec(CardKeys.bagItem("water-stone"))!!.act)
        assertFalse(bag.spec(CardKeys.bagItem("poke-ball"))!!.act!!.enabled)
        assertEquals("Tap next battle first", bag.spec(CardKeys.bagItem("poke-ball"))!!.act!!.offLine)
        val first = state.mons[0]
        val target = state.mons[2]
        target.heldItem = "everstone"
        assertTrue(page.act { ItemUse.giveHeld(it.data, it.state, "soothe-bell", first.uid, target.uid) }.ok)
        assertEquals(1, received.size)
        assertTrue(GameRepo.takePendingFightEvents().isEmpty())
        var restored = SaveCodec.decode(SaveCodec.encode(state))
        assertNull(restored.mon(first.uid)!!.heldItem)
        assertEquals("soothe-bell", restored.mon(target.uid)!!.heldItem)
        assertEquals(1, restored.count("everstone"))
        assertEquals(3, restored.ownedItemCount("soothe-bell"))
        assertTrue(page.act { ItemUse.takeHeld(it.state, target.uid) }.ok)
        restored = SaveCodec.decode(SaveCodec.encode(state))
        assertNull(restored.mon(target.uid)!!.heldItem)
        assertEquals(2, restored.count("soothe-bell"))
        assertEquals(3, restored.ownedItemCount("soothe-bell"))
        page.onGone()
    }
}
