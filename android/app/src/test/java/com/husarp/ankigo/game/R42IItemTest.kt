package com.husarp.ankigo.game

import com.husarp.ankigo.ui.FloatTexts
import com.husarp.ankigo.ui.FloatKind
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class R42IItemTest {
    private val data = TestData.world()
    @Test fun allStatBerriesExplainTemporaryMultipliersOnEverySurface() {
        for ((id, stat) in AbilityAdaptations.berries.filterValues { it != "hp" }) {
            if (id in setOf("oran-berry", "sitrus-berry", "figy-berry")) continue
            val text = listOf(ItemInfo.short(data, id), ItemInfo.line(data, id), Items.def(id)!!.note)
            for (t in text) { assertTrue("$id/$stat: $t", "×1.5" in t); assertTrue(t, "fight" in t); assertFalse(t, "+1 " in t) }
        }
    }

    @Test fun allAbilityStageNumbersExplainFactorsAndDuration() {
        for (id in AbilityText.ids) {
            val t = AbilityText.of(id)!!
            assertFalse("$id: $t", Regex("""by [126](?![.\d]| stages?)|loses 1 Speed""").containsMatchIn(t))
            if (" stage" in t && id != "curiousmedicine") { assertTrue("$id: $t", "×" in t); assertTrue(t, "fight" in t) }
        }
        assertTrue(AbilityText.of("Intimidate")!!.contains("×2/3"))
        assertTrue(AbilityText.of("Supersweet Syrup")!!.contains("×3/4"))
        assertTrue(AbilityText.of("Steam Engine")!!.contains("×4"))
        assertTrue(AbilityText.of("Flower Gift")!!.contains("multiplied by 1.5"))
    }

    @Test fun healingItemsFloatOnlyHpActuallyRestored() {
        for (id in listOf("potion", "super-potion", "max-potion", "old-gateau", "casteliacone", "big-malasada")) {
            val s = TestData.state(data)
            val m = s.main!!; val max = StatCalc.maxHp(data, m); m.hp = max - 1
            s.bag[id] = 1
            val result = ItemUse.use(data, s, id, m.uid, Random(1), 100)
            assertTrue(id, result.ok); assertEquals(max, m.hp)
            assertEquals(id, listOf("+1 HP"), result.events.flatMap { FloatTexts.of(it) }.filter { it.kind == FloatKind.HEAL }.map { it.text })
        }
    }

    @Test fun fullHpBoostAndStatusCureProduceNoHealNumber() {
        for (id in listOf("soda-pop", "casteliacone", "full-restore")) {
            val s = TestData.state(data); val m = s.main!!; m.hp = StatCalc.maxHp(data, m)
            s.battle = Battle(TestData.mon(data, 1, 20, listOf("splash"), "wild"), Tier.NORMAL)
            if (id == "full-restore") s.battle!!.player.status = "par"
            s.bag[id] = 1
            val result = ItemUse.use(data, s, id, m.uid, Random(1), 100)
            assertTrue(id, result.ok)
            assertTrue(id, result.events.flatMap { FloatTexts.of(it) }.none { it.kind == FloatKind.HEAL })
            assertFalse(result.message, "0 HP" in result.message)
        }
    }

    @Test fun battleHealsSuppressFullHpAndCapOverhealBeforeFloating() {
        val s = TestData.state(data); val m = s.main!!; val max = StatCalc.maxHp(data, m)
        val b = Battle(TestData.mon(data, 1, 20, listOf("splash"), "wild"), Tier.NORMAL)
        val r = BattleEngine.Round(data, b, m, 1.0, 1.0, Random(1))
        m.hp = max; r.heal(true, 20, "Restored HP")
        assertTrue(r.events.isEmpty())
        m.hp = max - 1; r.heal(true, 20, "Restored HP")
        assertEquals(1, r.events.filterIsInstance<GameEvent.Effect>().single().amount)
        assertEquals(listOf("+1 HP"), r.events.flatMap { FloatTexts.of(it) }.map { it.text })
    }
}
