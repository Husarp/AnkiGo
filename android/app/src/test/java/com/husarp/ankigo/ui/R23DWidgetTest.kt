package com.husarp.ankigo.ui

import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.shots.Shots
import org.junit.Assert.*
import org.junit.Test

class R23DWidgetTest {
    private val data=TestData.world()
    private fun mon(uid: String, name: String, level: Int, caught: Long) = TestData.mon(data,16,level,uid=uid).also { it.name=name; it.caughtAt=caught }
    @Test fun pickerOrdersByLevelNameAndCaptureDateWithSelectedFirst() {
        val mons=listOf(mon("a","Zulu",5,30),mon("b","Alpha",20,10),mon("c","Beta",10,20))
        assertEquals(listOf("b","c","a"),BuddyPicker.mons(mons,"", "",BuddyPicker.Sort.LEVEL).map { it.uid })
        assertEquals(listOf("b","c","a"),BuddyPicker.mons(mons,"", "",BuddyPicker.Sort.NAME).map { it.uid })
        assertEquals(listOf("a","c","b"),BuddyPicker.mons(mons,"", "",BuddyPicker.Sort.RECENT).map { it.uid })
        for(sort in BuddyPicker.Sort.values()) assertEquals("c",BuddyPicker.mons(mons,"c","",sort).first().uid)
    }
    @Test fun searchMatchesSpeciesAndNicknameWithoutRetainingUnmatchedSelection() {
        val a=mon("a","Pidgey",5,0).also { it.nickname="Sky" }
        val b=mon("b","Rattata",10,1)
        assertEquals(listOf(a),BuddyPicker.mons(listOf(a,b),"b"," PIDG ",BuddyPicker.Sort.NAME))
        assertEquals(listOf(a),BuddyPicker.mons(listOf(a,b),"b","sKy",BuddyPicker.Sort.NAME))
        assertTrue(BuddyPicker.mons(listOf(a,b),"a","absent",BuddyPicker.Sort.NAME).isEmpty())
    }
    @Test fun sizeIsClampedIsolatedInheritedAndDeleted() {
        val p=Shots.MemPrefs()
        assertEquals(100,BuddyWidgetPrefs.look(p,7).size)
        BuddyWidgetPrefs.setSize(p,7,70); BuddyWidgetPrefs.setSize(p,0,80)
        assertEquals(70,BuddyWidgetPrefs.look(p,7).size)
        assertEquals(80,BuddyWidgetPrefs.look(p,8).size)
        BuddyWidgetPrefs.setSize(p,9,0); assertEquals(50,BuddyWidgetPrefs.size(p,9))
        BuddyWidgetPrefs.setSize(p,9,500); assertEquals(100,BuddyWidgetPrefs.size(p,9))
        BuddyWidgetPrefs.forget(p,intArrayOf(7)); assertFalse(p.contains("b7.size"))
        assertEquals(80,BuddyWidgetPrefs.size(p,7))
    }
    @Test fun smallerSpriteKeepsItsCenterAndOriginalBoundsInEveryLayoutAndSize() {
        for(layout in listOf("A","B","C")) for((w,h) in listOf(140f to 67f,140f to 140f,286f to 67f,286f to 140f)) {
            val full=BuddyGeometry.of(w,h,layout)
            for(percent in listOf(50,75,100)) {
                val scaled=full.scaled(percent)
                assertEquals(full.spriteSize*percent/100f,scaled.spriteSize,.001f)
                assertEquals(full.spriteLeft+full.spriteSize/2,scaled.spriteLeft+scaled.spriteSize/2,.001f)
                assertEquals(full.spriteTop+full.spriteSize/2,scaled.spriteTop+scaled.spriteSize/2,.001f)
                assertTrue(scaled.spriteLeft>=full.spriteLeft); assertTrue(scaled.spriteTop>=full.spriteTop)
                assertTrue(scaled.spriteLeft+scaled.spriteSize<=w); assertTrue(scaled.spriteTop+scaled.spriteSize<=h)
            }
        }
    }
}
