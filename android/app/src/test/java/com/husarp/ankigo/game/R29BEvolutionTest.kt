package com.husarp.ankigo.game

import com.husarp.ankigo.game.data.Parsers
import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Calendar
import kotlin.random.Random

class R29BEvolutionTest {
    private fun real(staleCrabrawler: Boolean = false): GameData {
        val path=System.getenv("ANKIGO_REAL_DATA")
        org.junit.Assume.assumeTrue("ANKIGO_REAL_DATA not set",!path.isNullOrBlank())
        val dir = File(path!!)
        fun text(n: String) = File(dir, n).readText()
        val dex=org.json.JSONObject(text("pokedex.json"))
        if (staleCrabrawler) dex.getJSONObject("crabominable").apply {
            put("evoType","levelExtra"); remove("evoItem"); put("evoCondition","near Mount Lanakila")
        }
        return Parsers.build(Parsers.Sources(dex.toString(), text("moves.json"), text("learnsets.json"), text("pokemon_species.csv"), text("pokemon.csv"), text("pokemon_stats.csv"), text("pokemon_evolution.csv"), text("experience.csv"), text("items.csv")))
    }
    @Test fun everyDownloadedItemEvolutionChecksOwnershipAndConsumesExactlyOne() {
        val data = real()
        var branches = 0
        val checkedItems=linkedSetOf<String>()
        for (sp in data.species.values) for (e in sp.evolutions.filter { it.kind in setOf(EvoKind.ITEM, EvoKind.TRADE, EvoKind.OTHER) }) {
            val item = Progression.evolutionItem(e)!!
            checkedItems+=item
            assertNotNull("${sp.name} / $item catalogued", Items.def(item))
            val mon = TestData.mon(data, sp.num, 40, listOf("tackle"), "test").apply { gender = e.gender ?: 'M'; bondPoints = 500 }
            val st = GameState().apply { mons += mon }
            val now = Calendar.getInstance().apply { set(2026, 10, 2, if (e.time == "night") 23 else 12, 0, 0) }.timeInMillis
            assertFalse("${sp.name} needs $item", e in Progression.readyEvolutions(data, st, mon, now))
            assertFalse(ItemUse.canUse(data,st,item,mon,now))
            assertFalse(ItemUse.use(data, st, item, mon.uid, Random(1), now, e.to).ok)
            st.bag[item] = 2
            assertTrue("${sp.name} ready with $item", e in Progression.readyEvolutions(data, st, mon, now))
            assertTrue(ItemUse.canUse(data,st,item,mon,now))
            mon.heldItem = "everstone"
            assertTrue(ItemUse.canUse(data,st,item,mon,now))
            assertTrue(e in Progression.readyEvolutions(data, st, mon, now))
            e.gender?.let { required ->
                mon.gender = if (required == 'M') 'F' else 'M'
                assertFalse(e in Progression.readyEvolutions(data, st, mon, now))
                mon.gender = required
            }
            if (e.time != null) {
                val wrongTime = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, if (e.time == "night") 12 else 23) }.timeInMillis
                assertFalse(ItemUse.use(data, st, item, mon.uid, Random(1), wrongTime, e.to).ok)
                assertEquals(2, st.count(item))
            }
            val result = ItemUse.use(data, st, item, mon.uid, Random(1), now, evolutionTo = e.to)
            assertTrue("${sp.name}: ${result.message}", result.ok)
            assertEquals(e.to, mon.species); assertEquals(1, st.count(item)); assertEquals(now, mon.evolvedAt)
            branches++
        }
        assertTrue(branches > 50)
        val unused=Items.ALL.values.filter { it.kind==ItemKind.EVOLUTION }.map { it.id }.toSet()-checkedItems
        // Chipped Pot targets Antique Polteageist, an already unsupported non-regional form.
        assertEquals(setOf("chipped-pot"),unused)
        println("R29B audited $branches item/trade/other branches across ${checkedItems.size} items; unsupported: $unused")
    }
    @Test fun staleRawCrabrawlerDataUsesIceStoneThroughTheRealGameChoice() {
        val d=real(staleCrabrawler=true)
        val st=TestData.state(d,739,20).apply { bag["ice-stone"]=1; main!!.bondPoints=500 }
        val mon=st.mons.first()
        assertTrue(Game(d,st,Random(1)) { 987654321L }.chooseEvolution(mon.uid,740).ok)
        assertEquals(740,mon.species)
        assertEquals(0,st.count("ice-stone"))
        assertEquals(987654321L,mon.evolvedAt)
    }
    @Test fun staleCrabrawlerCacheIsRepairedWithoutRedownload() {
        val d = TestData.data(listOf(TestData.species(739, "Crabrawler", evolutions = listOf(Evolution(740, EvoKind.OTHER, condition = "near Mount Lanakila"))), TestData.species(740, "Crabominable")))
        val back = Parsers.decodeCache(Parsers.encodeCache(d))
        val st = TestData.state(back, 739, 20).apply { bag["ice-stone"] = 1; main!!.bondPoints = 500 }
        assertEquals("ice-stone", back.sp(739).evolutions.single().item)
        assertEquals(740, Progression.readyEvolutions(back, st, st.mons.first(), 1).single().to)
    }
    @Test fun levelAndFriendshipEvolutionRecordTheInjectedClock() {
        for (e in listOf(Evolution(2,EvoKind.LEVEL,level=16), Evolution(2,EvoKind.FRIENDSHIP,minFriendship=100))) {
            val d=TestData.data(listOf(TestData.species(1,evolutions=listOf(e)),TestData.species(2)))
            val st=TestData.state(d,1,20)
            st.mons.first().friendship=200; st.mons.first().bondPoints=500
            val g=Game(d,st,Random(1)) { 123456789L }
            assertTrue(g.chooseEvolution(st.mons.first().uid,2).ok)
            assertEquals(123456789L,st.mons.first().evolvedAt)
            assertFalse(g.chooseEvolution(st.mons.first().uid,2).ok)
            assertEquals(123456789L,st.mons.first().evolvedAt)
        }
    }
    @Test fun evolutionDatesPersistAndSortAcrossOwnedCopiesAndDex() {
        val d = TestData.data(listOf(TestData.species(1), TestData.species(2), TestData.species(3)))
        val st = GameState().apply { mons += listOf(TestData.mon(d, 1, 10, uid = "a"), TestData.mon(d, 2, 10, uid = "b"), TestData.mon(d, 3, 10, uid = "c")); mons[0].evolvedAt = 100; mons[1].evolvedAt = 200 }
        val back = SaveCodec.decode(SaveCodec.encode(st))
        val spec = SortSpec.of(SortKey.RECENTLY_EVOLVED)
        assertEquals(listOf(2,1,3), MonSort.sort(d, back, back.mons, spec).map { it.species })
        assertEquals(listOf(1,2,3), MonSort.sort(d, back, back.mons, spec.flipped()).map { it.species })
        assertEquals(listOf(2,1,3), MonSort.sortSpecies(d, back, d.species.values.toList(), spec).map { it.num })
        assertEquals(spec, SortSpec.decode(spec.encode()))
        assertEquals(DexSort.RECENTLY_EVOLVED, DexSort.fromKey(spec.key))
        assertEquals(listOf(2,1,3), BoxTiles.order(back.mons,CopySort.EVOLVED).map { it.species })
        assertEquals(listOf(2,1,3), BuddyPicker.mons(back.mons,"","",BuddyPicker.Sort.EVOLVED).map { it.species })
        assertEquals(listOf(2,1,3), DexFilter(sort=DexSort.RECENTLY_EVOLVED).apply(d.species.values.toList(),DexFilter.lastCaught(back.mons),emptySet(),setOf(1),DexSortInfo(d,back)).map { it.num })
        val old = SaveCodec.encode(st).replace(Regex(",?\\\"evolvedAt\\\":\\d+"), "")
        assertTrue(SaveCodec.decode(old).mons.all { it.evolvedAt == 0L })
    }
}
