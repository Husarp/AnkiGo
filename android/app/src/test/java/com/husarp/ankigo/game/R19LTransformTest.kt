package com.husarp.ankigo.game

import com.husarp.ankigo.game.R17C8.effects
import com.husarp.ankigo.game.save.SaveCodec
import com.husarp.ankigo.ui.ShinyBurst
import com.husarp.ankigo.ui.TransformFlash
import com.husarp.ankigo.ui.TransformSprite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * R19-L (PLAN 271, pick A): a transformed Pokémon shows its target's picture (shiny or not as the target is), with a bright flash.
 * Here: the Transform event says which picture, the fight keeps it (saved), the sprite choice while transformed / after the transform
 * ends / after a reload, Imposter on entering, and the flash's timeline. "Blobby" (807) is the Ditto, "Torchy" (802) the Fire type.
 */
class R19LTransformTest {
    private val data = R17C8.data
    private fun blobby(lv: Int = 30) = R17C8.mon(807, lv, "transform")
    private fun torchy(shiny: Boolean = false) = R17C8.mon(802, 100, "ember", "flamethrower").also { it.shiny = shiny }
    private fun rd(main: Mon, b: Battle, wildActs: Boolean = false) = R17C8.round(main, b, Random(1), enemyActs = wildActs)
    private fun transformEvents(e: List<GameEvent>) = e.effects().filter { it.stat == "transform" }

    @Test fun theEventNamesTheTargetsPictureAndItsShininess() {
        val m = blobby(); val b = R17C8.battle(torchy(shiny = true))
        val ev = transformEvents(rd(m, b)).single()
        assertTrue(ev.onPlayer)
        assertEquals(EffectKind.VOLATILE, ev.kind)
        assertEquals(802, ev.into)
        assertTrue("a shiny target is copied shiny", ev.intoShiny)
        val plain = transformEvents(rd(blobby(), R17C8.battle(torchy()))).single()
        assertEquals(802, plain.into); assertFalse(plain.intoShiny)
    }

    @Test fun theWildOneCopiesOurMainsPicture() {
        val m = R17C8.main("ember").also { it.shiny = true }
        val w = R17C8.mon(807, 50, "transform"); val b = R17C8.battle(w)
        val ev = transformEvents(rd(m, b, wildActs = true)).single()
        assertFalse(ev.onPlayer)
        assertEquals(800, ev.into); assertTrue(ev.intoShiny)
        val (sp, shiny) = TransformSprite.wild(b, data)!!
        assertEquals(800, sp.num); assertTrue(shiny)
    }

    @Test fun otherEffectsCarryNoPicture() {
        val m = blobby(); val b = R17C8.battle(torchy())
        val all = rd(m, b, wildActs = true).effects()
        assertTrue(all.filter { it.stat != "transform" }.all { it.into == 0 && !it.intoShiny })
    }

    @Test fun theSpriteKeyFollowsTheFightWhileTransformedAndComesBackWhenItEnds() {
        val m = blobby(); val b = R17C8.battle(torchy(shiny = true))
        // before: its own picture
        var (sp, shiny) = TransformSprite.player(m, b, data)
        assertEquals(807, sp.num); assertFalse(shiny)
        assertNull(MoveControl.transformLook(b.player))
        rd(m, b)
        // while transformed: the target's picture, shiny as the target is
        TransformSprite.player(m, b, data).let { assertEquals(802, it.first.num); assertTrue(it.second) }
        assertEquals(802 to true, MoveControl.transformLook(b.player))
        // the wild one keeps its own picture
        TransformSprite.wild(b, data)!!.let { assertEquals(802, it.first.num); assertTrue(it.second) }
        // the user is another Pokémon's Main now / no fight / a new fight: its own picture again
        TransformSprite.player(m, null, data).let { assertEquals(807, it.first.num); assertFalse(it.second) }
        TransformSprite.player(m, R17C8.battle(torchy()), data).let { assertEquals(807, it.first.num) }
        // a switch replaces the Main's side: no notes, its own picture
        b.player = BattleSide()
        TransformSprite.player(m, b, data).let { assertEquals(807, it.first.num) }
    }

    @Test fun theSpriteKeyIsTheSameAfterAReload() {
        val m = blobby(); val b = R17C8.battle(torchy(shiny = true))
        rd(m, b)
        val s = GameState(schema = GameState.SCHEMA)
        s.mons += m; s.team += m.uid; s.battle = b
        val back = SaveCodec.decode(SaveCodec.encode(s)).battle!!
        assertEquals(802 to true, MoveControl.transformLook(back.player))
        TransformSprite.player(m, back, data).let { assertEquals(802, it.first.num); assertTrue(it.second) }
    }

    @Test fun anOldSaveWithoutTheLookNotesShowsTheCopiedSpeciesNotShiny() {
        val b = R17C8.battle(torchy())
        b.player.notes[MoveControl.TRANSFORMED] = "802"
        assertEquals(802 to false, MoveControl.transformLook(b.player))
        assertNull(MoveControl.transformLook(b.enemy))
    }

    @Test fun anUnknownSpeciesFallsBackToTheOwnPicture() {
        val m = blobby(); val b = R17C8.battle(torchy())
        b.player.notes[MoveControl.TRANSFORMED] = "99999"
        TransformSprite.player(m, b, data).let { assertEquals(807, it.first.num) }
    }

    @Test fun imposterTransformsOnEnteringAndTheEventSaysWhichPicture() {
        val m = R17C8.main("ember")
        val w = R17C8.mon(807, 50, "tackle"); w.ability = "Imposter"
        val b = R17C8.battle(w)
        val ev = transformEvents(rd(m, b, wildActs = true))
        assertEquals(1, ev.size)
        assertFalse(ev[0].onPlayer)
        assertEquals(800, ev[0].into)
        assertEquals(800 to false, MoveControl.transformLook(b.enemy))
        assertEquals(800, TransformSprite.wild(b, data)!!.first.num)
        // ... and ours is untouched
        assertNull(MoveControl.transformLook(b.player))
    }

    @Test fun imposterOnOurSideCopiesTheWildOne() {
        val m = blobby(); m.ability = "Imposter"
        val b = R17C8.battle(torchy(shiny = true))
        val ev = transformEvents(rd(m, b))
        assertEquals(1, ev.size)
        assertTrue(ev[0].onPlayer)
        assertEquals(802, ev[0].into); assertTrue(ev[0].intoShiny)
    }

    @Test fun theFlashIsWhiteAtTheSwapAndClearAtBothEnds() {
        assertEquals(0f, TransformFlash.white(0f), 0f)
        assertEquals(1f, TransformFlash.white(TransformFlash.SWAP_MS.toFloat()), 0f)
        assertEquals(0f, TransformFlash.white(TransformFlash.DURATION_MS.toFloat()), 0f)
        var last = 0f
        for (t in 0..160 step 10) { val w = TransformFlash.white(t.toFloat()); assertTrue(w >= last); last = w }   // rises
        for (t in 240..400 step 10) { val w = TransformFlash.white(t.toFloat()); assertTrue(w <= last); last = w }  // falls
        assertTrue("the copy is swapped in while the picture is all white", TransformFlash.SWAP_MS in 160..240)
        assertEquals("about 0.4 s", 400L, TransformFlash.DURATION_MS)
    }

    @Test fun theSparklesLiveOnlyDuringTheFlash() {
        fun count(t: Float): Int { var n = 0; TransformFlash.forEach(t, 100f, 100f, 2f, ShinyBurst.Sink { _, _, _, _, _, _ -> n++ }); return n }
        assertEquals(0, count(0f))
        assertEquals(0, count(TransformFlash.DURATION_MS.toFloat()))
        assertEquals(6, count(200f))   // a ring of six round the picture
        assertTrue(TransformFlash.finished(400f)); assertFalse(TransformFlash.finished(399f))
    }
}
