package com.husarp.ankigo.shots
import android.content.Context
import android.view.*
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.game.*
import com.husarp.ankigo.ui.*
import org.junit.*
import java.io.File
class R28KStacksShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule().apply { setField(this,"useDeviceResolution",true) }
    private fun dp(c: Context,n:Int)=Ui.dp(c,n)
    private fun render(name:String,h:Int,build:(Context)->View)=paparazzi.shootPage("r28k-$name",listOf(Variant("360",360,h))) { c -> installRealArt(c);build(c) }
    private fun installRealArt(c: Context) {
        val kit=File("/home/coder/code/projects/AnkiGo/docs/design/plan-kit/sprites")
        val dir=com.husarp.ankigo.data.Sprites.dir(c)
        for(kind in listOf("front","back")) for(src in File(kit,kind).listFiles().orEmpty().filter{it.extension=="png"}) {
            File(dir,kind).mkdirs();src.copyTo(File(dir,"$kind/${src.name}"),overwrite=true)
            if(kind=="front") { File(dir,"static").mkdirs();src.copyTo(File(dir,"static/${src.name}"),overwrite=true) }
        }
        // Production reads only warmed memory on the UI thread. Replace any fake cached bitmaps
        // on a worker after installing the real PNGs, before building the native Box tiles.
        val warm = Thread {
            for (num in listOf(1, 2, 3, 16, 19, 25, 26, 54, 92, 129, 133, 134)) {
                checkNotNull(com.husarp.ankigo.data.Sprites.staticBitmap(c, num))
            }
        }
        warm.start(); warm.join()
        val names=mapOf(1 to "Bulbasaur",2 to "Ivysaur",3 to "Venusaur",16 to "Pidgey",19 to "Rattata",25 to "Pikachu",26 to "Raichu",54 to "Psyduck",63 to "Abra",64 to "Kadabra",65 to "Alakazam",92 to "Gastly",129 to "Magikarp",130 to "Gyarados",133 to "Eevee",134 to "Vaporeon",135 to "Jolteon",150 to "Mewtwo",151 to "Mew",172 to "Pichu",700 to "Sylveon")
        val d=GameRepo.data!!
        setField(GameRepo,"loadedData",GameData(d.species.mapValues { (id,sp)-> names[id]?.let { sp.copy(name=it,id=it.lowercase(),spriteId=it.lowercase()) } ?: sp },d.moves,d.growth,d.itemPrices))
        GameRepo.state!!.mons.forEach { m -> names[m.species]?.let {m.name=it} }
    }
    private fun freeze(p: GamePage) { call(p,"doRender"); p.onGone() }
    private fun stacks()=render("stacks",820) { c ->
        val data=GameRepo.data!!;val st=GameRepo.state!!; st.mons.clear();st.team.clear();st.viewedMons.clear()
        listOf(25 to 1,133 to 3,1 to 1,2 to 2,3 to 1,19 to 1,134 to 5,54 to 1,16 to 2,26 to 1,92 to 12,129 to 1).forEachIndexed { n,(sp,count)-> repeat(count) { j ->
            val m=TestData.mon(data,sp,28+n-j,listOf("tackle"),"later-$n-$j").also {it.caughtAt=1L;it.name=mapOf(25 to "Pikachu",133 to "Eevee",1 to "Bulbasaur",2 to "Ivysaur",3 to "Venusaur",19 to "Rattata",134 to "Vaporeon",54 to "Psyduck",16 to "Pidgey",26 to "Raichu",92 to "Gastly",129 to "Magikarp")[sp]!!;it.favourite=n==1&&j==0}
            st.mons+=m;st.viewedMons+=m.uid
        } }
        st.team+=st.mons.first().uid;st.team+=st.mons.first{it.species==133}.uid
        BoxDupePrefs.setStacked(c,true);BoxDupePrefs.setTwo(c,false)
        val page=TeamPage(c,NoHost).apply{preset(TeamSection.BOX,group=true)};val screen=pageScreen(c,page);freeze(page)
        screen.measure(View.MeasureSpec.makeMeasureSpec(dp(c,360),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(c,820),View.MeasureSpec.EXACTLY));screen.layout(0,0,dp(c,360),dp(c,820))
        screen
    }
    @Test fun approvedFan()=stacks()
}
