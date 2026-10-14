package com.husarp.ankigo.ui
import org.junit.Test
class TmpDbgTest {
    @Test fun p() { for (f in listOf(8f,9f,10f,12f)) { val c = ArenaGeo.cards(f, false, true); println("DBGC floor=$f wildH=${c.wildH} tagH=${c.tagH} sum=${c.wildH+c.tagH} playerH=${c.playerH}") } }
}
