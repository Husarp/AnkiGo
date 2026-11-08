package com.husarp.ankigo.shots

import android.content.Context
import android.view.*
import android.widget.*
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class R29BShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val phone = listOf(Variant("360",360,820))
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun labels(v: View) = all(v).filterIsInstance<TextView>().map { it.text.toString() }
    private fun dates() { GameRepo.state!!.mons.forEachIndexed { i,m -> m.evolvedAt = if (i < 3) 1000L+i else 0 } }
    private fun shoot(name: String, build: (Context) -> View) = paparazzi.shootPage("b-$name", phone, build = build)
    @Test fun unknownItem() = shoot("unknown-item") { c ->
        val p = ShopPage(c,NoHost)
        GameRepo.state!!.caught.clear(); GameRepo.state!!.seen.clear()
        val w = ItemWindow(p) {}
        w.update(ItemCard.Spec("thunder-stone"))
        val text = labels(w.view)
        assertEquals(text.toString(), 2, text.count { it == "???" })
        assertFalse(all(w.view).filter { it.contentDescription == "???" }.any { it.isClickable })
        assertFalse(text.toString(), text.any { "4 unknown Pokémon" in it })
        assertFalse(text.any { it.startsWith("…and") })
        w.view
    }
    @Test fun evolutionItemHasNoUseOnAction() = shoot("evolution-item") { c ->
        ShopCardKit.install()
        val (_,stage)=ShopCardKit.page(c,CardKeys.bagItem("thunder-stone"),bag=true,setup={ GameRepo.state!!.apply { bag["thunder-stone"]=2; mons.forEach { it.bondPoints=500 } } })
        assertFalse("Use on…" in labels(stage))
        assertFalse(labels(stage).any { "→" in it || "⟶" in it })
        stage
    }
    @Test fun boxAndSort() {
        shoot("box-recently-evolved") { c ->
            dates(); val p = TeamPage(c,NoHost).apply { preset(TeamSection.BOX,sort=SortSpec.of(SortKey.RECENTLY_EVOLVED)) }
            pageScreen(c,p)
        }
        shoot("box-sort") { c ->
            val p = TeamPage(c,NoHost)
            SortSheet(p, SortText.boxKeys,SortSpec.of(SortKey.RECENTLY_EVOLVED),false,null,0,null,{ 12 },{ _,_ -> }).build(Ui.dp(c,580))
        }
    }
    @Test fun dexAndSort() {
        shoot("dex-recently-evolved") { c ->
            dates(); val p = DexPage(c,NoHost)
            setField(p,"filter",DexFilter(sort=DexSort.RECENTLY_EVOLVED))
            pageScreen(c,p)
        }
        shoot("dex-sort") { c ->
            val p = DexPage(c,NoHost)
            SortSheet(p,SortText.dexKeys,SortSpec.of(SortKey.RECENTLY_EVOLVED),false,null,0,null,{ 12 },{ _,_ -> }).build(Ui.dp(c,580))
        }
    }
    @Test fun copies() = shoot("copies-sort") { c ->
        val p = TeamPage(c,NoHost); val d=GameRepo.data!!
        BoxCopiesSheet(p,BoxTile(listOf(TestData.mon(d,25,25,uid="a"),TestData.mon(d,25,20,uid="b"))),{}, { _,_,_ -> }).build(Ui.dp(c,400))
    }
    @Test fun buddyPicker() = shoot("buddy-sort") { c ->
        val a=attach(WidgetLookActivity(),ShotCtx(c));a.buildContent()
        val card=a.showPokemonSheet(show=false)
        FrameLayout(c).apply { addView(card,FrameLayout.LayoutParams(Ui.dp(c,328),-2,Gravity.CENTER)) }
    }
    @Test fun battlePicker() {
        shoot("battle-use-on") { c ->
            val st = GameRepo.state!!.apply { mons.forEach { it.bondPoints=500 } }
            val dialog=DialogShots.capture { BattleBag.pick(c,GameRepo.data!!,st,"thunder-stone") {} }!!
            assertFalse(labels(dialog.content()).any { "→" in it })
            DialogShots.scene(c,dialog)
        }
        shoot("battle-sort") { c ->
            val st=GameRepo.state!!.apply { mons.forEach { it.bondPoints=500 } }
            val first=DialogShots.capture { BattleBag.pick(c,GameRepo.data!!,st,"thunder-stone") {} }!!
            val more=all(first.content()).filterIsInstance<TextView>().single { it.text.toString()=="More Pokémon" }
            val expanded=DialogShots.capture { more.performClick() }!!
            assertTrue("Recently evolved" in labels(expanded.content()))
            DialogShots.scene(c,expanded)
        }
    }
    @Test fun evolutionAboveRefreshedDetails() = shoot("evolved-over-details") { c ->
        val original=GameRepo.data!!
        val species=original.species.toMutableMap()
        species[25]=species.getValue(25).copy(evolutions=listOf(Evolution(26,EvoKind.ITEM,item="thunder-stone")))
        species[26]=species.getValue(26).copy(name="Raichu",prevo=25)
        val d=GameData(species,original.moves,original.growth,original.itemPrices)
        setField(GameRepo,"loadedData",d)
        call(GameRepo,"rebuild")
        val st=GameRepo.state!!;st.battle=null;st.bag["thunder-stone"]=1
        val mon=st.mons.first { it.species==25 };mon.heldItem=null;mon.bondPoints=500
        val p=TeamPage(c,NoHost)
        AppPrefs.setDetailOpen(p,"evolution",true)
        val details=DialogShots.capture { MonDetail.show(p,d,mon) }!!
        val condition=all(details.content()).single { it.contentDescription?.toString()=="Explain Use Thunder Stone" }
        val requirement=DialogShots.capture { condition.performClick() }!!
        assertTrue(requirement.button(AppDialog.BUTTON_POSITIVE)!!.isEnabled)
        paparazzi.snapshot(DialogShots.scene(c,requirement), "b-evolution-ready-${c.resources.configuration.uiMode}")
        // A tap cannot evolve; the accessible hold action performs the real page action.
        requirement.button(AppDialog.BUTTON_POSITIVE)!!.performClick()
        assertEquals(25,mon.species)
        val opened=mutableListOf<AppDialog>()
        Pickers.interceptApp={ it.joinStack(); opened+=it }
        try { assertTrue(requirement.button(AppDialog.BUTTON_POSITIVE)!!.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK,null)) } finally { Pickers.interceptApp=null }
        assertEquals(26,mon.species)
        assertTrue("Details refresh precedes the celebration",opened.size>=2)
        val popup=opened.last()
        assertTrue(labels(popup.content()).any { "evolved into Raichu!" in it })
        val refreshed=opened[opened.lastIndex-1]
        FrameLayout(c).apply {
            addView(DialogShots.scene(c,refreshed),FrameLayout.LayoutParams(-1,-1))
            addView(DialogShots.scene(c,popup),FrameLayout.LayoutParams(-1,-1))
        }
    }
}
