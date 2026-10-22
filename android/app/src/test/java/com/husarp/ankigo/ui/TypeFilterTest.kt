package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Type
import com.husarp.ankigo.shots.Shots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** R16-2-E (PLAN 192, X7, X8): the type filter's three modes, its picks rule, the words under the switch and the saved form. */
class TypeFilterTest {
    // the design's nine demo Pokémon
    private val mons = linkedMapOf(
        "Bulbasaur" to listOf(Type.GRASS, Type.POISON), "Charmander" to listOf(Type.FIRE), "Squirtle" to listOf(Type.WATER),
        "Blastoise" to listOf(Type.WATER), "Pikachu" to listOf(Type.ELECTRIC), "Gengar" to listOf(Type.GHOST, Type.POISON),
        "Lapras" to listOf(Type.WATER, Type.ICE), "Eevee" to listOf(Type.NORMAL), "Snorlax" to listOf(Type.NORMAL),
    )

    private fun shown(match: TypeMatch, vararg picks: Type) = mons.filter { (_, t) -> TypeFilter(picks.toSet(), match).matches(t) }.keys.toList()

    @Test fun noPicksShowEverythingInEveryMode() {
        for (m in TypeMatch.entries) {
            assertEquals("$m", mons.keys.toList(), shown(m))
            assertFalse(TypeFilter(emptySet(), m).active)
        }
    }

    @Test fun oneType() {
        assertEquals(listOf("Squirtle", "Blastoise", "Lapras"), shown(TypeMatch.ANY, Type.WATER))
        assertEquals(listOf("Squirtle", "Blastoise", "Lapras"), shown(TypeMatch.ALL, Type.WATER)) // one pick: Any of and All of agree
        assertEquals(listOf("Squirtle", "Blastoise"), shown(TypeMatch.ONLY, Type.WATER)) // pure Water; Lapras goes
    }

    @Test fun waterAndIce() {
        assertEquals(listOf("Squirtle", "Blastoise", "Lapras"), shown(TypeMatch.ANY, Type.WATER, Type.ICE))
        assertEquals(listOf("Lapras"), shown(TypeMatch.ALL, Type.WATER, Type.ICE))
        assertEquals(listOf("Squirtle", "Blastoise", "Lapras"), shown(TypeMatch.ONLY, Type.WATER, Type.ICE)) // pure Water, pure Ice (none) and Water/Ice
    }

    @Test fun grassAndPoison() {
        assertEquals(listOf("Bulbasaur", "Gengar"), shown(TypeMatch.ANY, Type.GRASS, Type.POISON))
        assertEquals(listOf("Bulbasaur"), shown(TypeMatch.ALL, Type.GRASS, Type.POISON))
        assertEquals(listOf("Bulbasaur"), shown(TypeMatch.ONLY, Type.GRASS, Type.POISON)) // Gengar has Ghost
    }

    @Test fun purePoisonIsEmptyForOnly() {
        assertEquals(listOf("Bulbasaur", "Gengar"), shown(TypeMatch.ANY, Type.POISON))
        assertEquals(listOf("Bulbasaur", "Gengar"), shown(TypeMatch.ALL, Type.POISON))
        assertEquals(emptyList<String>(), shown(TypeMatch.ONLY, Type.POISON))
    }

    @Test fun threePicks() {
        val three = arrayOf(Type.FIRE, Type.WATER, Type.ICE)
        assertEquals(listOf("Charmander", "Squirtle", "Blastoise", "Lapras"), shown(TypeMatch.ANY, *three))
        assertEquals(listOf("Charmander", "Squirtle", "Blastoise", "Lapras"), shown(TypeMatch.ONLY, *three))
        assertEquals(emptyList<String>(), shown(TypeMatch.ALL, *three)) // no species has three types
        assertEquals(listOf("Squirtle", "Blastoise", "Lapras", "Eevee", "Snorlax"), shown(TypeMatch.ONLY, Type.WATER, Type.ICE, Type.NORMAL))
    }

    @Test fun onlyNeverShowsSomethingWithoutTypes() {
        assertFalse(TypeFilter(setOf(Type.FIRE), TypeMatch.ONLY).matches(emptyList()))
        assertFalse(TypeFilter(setOf(Type.FIRE), TypeMatch.ANY).matches(emptyList()))
    }

    @Test fun allOfTakesTwoPicksAndRefusesAThird() {
        var f = TypeFilter(match = TypeMatch.ALL)
        f = f.toggle(Type.WATER)!!
        f = f.toggle(Type.ICE)!!
        assertNull(f.toggle(Type.FIRE))
        assertEquals(listOf(Type.WATER, Type.ICE), f.types.toList()) // nothing changed
        assertEquals(listOf(Type.ICE), f.toggle(Type.WATER)!!.types.toList()) // un-picking always works
        assertEquals(listOf(Type.ICE, Type.FIRE), f.toggle(Type.WATER)!!.toggle(Type.FIRE)!!.types.toList())
        assertEquals("All of: pick up to 2 types", TypeFilter.FULL)
    }

    @Test fun anyAndOnlyTakeAnyNumberOfPicks() {
        for (m in listOf(TypeMatch.ANY, TypeMatch.ONLY)) {
            var f = TypeFilter(match = m)
            for (t in listOf(Type.WATER, Type.FIRE, Type.ICE, Type.ROCK)) f = f.toggle(t)!!
            assertEquals(4, f.types.size)
        }
    }

    @Test fun switchingToAllKeepsTheLastTwoAndBackKeepsThem() {
        var f = TypeFilter()
        for (t in listOf(Type.WATER, Type.FIRE, Type.ICE, Type.ROCK)) f = f.toggle(t)!!
        val all = f.withMatch(TypeMatch.ALL)
        assertEquals(listOf(Type.ICE, Type.ROCK), all.types.toList())
        assertEquals(TypeMatch.ONLY, all.withMatch(TypeMatch.ONLY).match)
        assertEquals(listOf(Type.ICE, Type.ROCK), all.withMatch(TypeMatch.ONLY).types.toList())
        // Only keeps all four, and Only to All of cuts to two again
        assertEquals(4, f.withMatch(TypeMatch.ONLY).types.size)
        assertEquals(listOf(Type.ICE, Type.ROCK), f.withMatch(TypeMatch.ONLY).withMatch(TypeMatch.ALL).types.toList())
    }

    @Test fun theWordsUnderTheSwitch() {
        val none = "No type picked: every type shows."
        for (m in TypeMatch.entries) assertEquals(none, TypeFilter(emptySet(), m).sentence())
        fun s(m: TypeMatch, vararg t: Type) = TypeFilter(t.toCollection(LinkedHashSet()), m).sentence()
        assertEquals("Has at least one of: Water.", s(TypeMatch.ANY, Type.WATER))
        assertEquals("Has at least one of: Water, Ice.", s(TypeMatch.ANY, Type.WATER, Type.ICE))
        assertEquals("Has at least one of: Water, Ice, Fire.", s(TypeMatch.ANY, Type.WATER, Type.ICE, Type.FIRE))
        assertEquals("Has Water.", s(TypeMatch.ALL, Type.WATER))
        assertEquals("Has both Water and Ice.", s(TypeMatch.ALL, Type.WATER, Type.ICE))
        assertEquals("Pure Water only.", s(TypeMatch.ONLY, Type.WATER))
        assertEquals("No type outside Water + Ice: pure Water, pure Ice and Water/Ice stay.", s(TypeMatch.ONLY, Type.WATER, Type.ICE))
        assertEquals("No type outside Water + Ice + Fire.", s(TypeMatch.ONLY, Type.WATER, Type.ICE, Type.FIRE))
        // the words follow the picks in the order they were picked
        assertEquals("Has both Ice and Water.", s(TypeMatch.ALL, Type.ICE, Type.WATER))
    }

    @Test fun saveAndLoadRoundTrip() {
        val cases = listOf(
            TypeFilter(),
            TypeFilter(emptySet(), TypeMatch.ONLY), // a mode alone is remembered too
            TypeFilter(linkedSetOf(Type.WATER), TypeMatch.ANY),
            TypeFilter(linkedSetOf(Type.ICE, Type.WATER), TypeMatch.ALL),
            TypeFilter(linkedSetOf(Type.FIRE, Type.FLYING, Type.DRAGON), TypeMatch.ONLY),
            TypeFilter(Type.entries.toCollection(LinkedHashSet()), TypeMatch.ANY),
        )
        for (c in cases) {
            val back = TypeFilter.decode(c.encode())
            assertEquals(c, back)
            assertEquals("pick order", c.types.toList(), back.types.toList())
        }
        assertEquals("ONLY:FIRE,FLYING", TypeFilter(linkedSetOf(Type.FIRE, Type.FLYING), TypeMatch.ONLY).encode())
        assertEquals("ANY:", TypeFilter().encode())
    }

    @Test fun unknownOrGarbledIsEmpty() {
        for (bad in listOf(null, "", "garbage", "ANY", "ANY:FIRE:WATER", "SOME:FIRE", "ANY:FIRE,PLASMA", "ANY:FIRE,", "any:fire", ":", "ONLY:,")) {
            assertEquals("'$bad'", TypeFilter(), TypeFilter.decode(bad))
        }
        // a saved All of with three types (cannot happen through the UI) loads as the last two
        assertEquals(listOf(Type.ICE, Type.ROCK), TypeFilter.decode("ALL:WATER,ICE,ROCK").types.toList())
    }

    @Test fun eachScreenRemembersItsOwnFilter() {
        val p = Shots.MemPrefs()
        assertEquals(TypeFilter(), SortPrefs.loadType(p, SortPrefs.TypeWhere.BOX))
        val box = TypeFilter(linkedSetOf(Type.WATER, Type.ICE), TypeMatch.ALL)
        val dex = TypeFilter(linkedSetOf(Type.FIRE), TypeMatch.ONLY)
        SortPrefs.saveType(p, SortPrefs.TypeWhere.BOX, box)
        SortPrefs.saveType(p, SortPrefs.TypeWhere.DEX, dex)
        assertEquals(box, SortPrefs.loadType(p, SortPrefs.TypeWhere.BOX))
        assertEquals(dex, SortPrefs.loadType(p, SortPrefs.TypeWhere.DEX))
        assertEquals("typeFilter.box", SortPrefs.TypeWhere.BOX.key)
        assertEquals("typeFilter.dex", SortPrefs.TypeWhere.DEX.key)
        SortPrefs.saveType(p, SortPrefs.TypeWhere.BOX, TypeFilter())
        assertEquals(TypeFilter(), SortPrefs.loadType(p, SortPrefs.TypeWhere.BOX))
        assertEquals(dex, SortPrefs.loadType(p, SortPrefs.TypeWhere.DEX))
    }
}
