package com.husarp.ankigo.shots

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.data.Sprites
import com.husarp.ankigo.game.Battle
import com.husarp.ankigo.game.EvoKind
import com.husarp.ankigo.game.Evolution
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.TestData
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type
import com.husarp.ankigo.ui.DexDetail
import com.husarp.ankigo.ui.DexFilter
import com.husarp.ankigo.ui.DexPage
import com.husarp.ankigo.ui.MonDetail
import com.husarp.ankigo.ui.SettingsPage
import com.husarp.ankigo.ui.TeamPage
import com.husarp.ankigo.ui.Ui
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Regional forms (T37), made-up species with real-looking form ids: the Pokédex grid with a form next to its base species,
 * the number search, the detail dialogs, the battle panel with a long-named wild form, the team row, Settings "Always catch".
 * PNGs: forms-dex, forms-dex-number, forms-dex-long, forms-dexdetail, forms-dexdetail-long, forms-mondetail, forms-battle,
 * forms-team, forms-settings-catch (each -phone/-split, -light/-dark).
 */
class FormsShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val both = listOf(Size.PHONE, Size.SPLIT)
    private val bothVariants = listOf(Variants.PHONE, Variants.SPLIT)

    // ---------- the fake world with forms ----------

    private fun form(
        base: Species, id: Int, gen: Int, forme: String, name: String, prevo: Int? = null, evos: List<Evolution> = emptyList(),
        types: List<Type> = base.types, evoLevel: Int? = null,
    ) = base.copy(
        num = id, id = "${base.id}${forme.lowercase()}", name = name, gen = gen, form = forme, baseNum = base.num,
        spriteId = "${base.id}-${forme.lowercase()}", types = types, prevo = prevo, evolutions = evos, evoLevel = evoLevel,
    )

    private val data: GameData by lazy {
        val w = TestData.world()
        val sp = LinkedHashMap(w.species)
        val ratting = TestData.species(20, "Ratking", listOf(Type.NORMAL), prevo = 19, evoLevel = 20)
        sp[20] = ratting
        sp[10091] = form(sp.getValue(19), 10091, 7, "Alola", "Alolan Ratty", types = listOf(Type.DARK, Type.NORMAL),
            evos = listOf(Evolution(10092, EvoKind.LEVEL, level = 20, time = "night")))
        sp[10092] = form(ratting, 10092, 7, "Alola", "Alolan Ratking", prevo = 10091, types = listOf(Type.DARK, Type.NORMAL), evoLevel = 20)
        sp[10100] = form(sp.getValue(26), 10100, 7, "Alola", "Alolan Sparkrat", prevo = 25, types = listOf(Type.ELECTRIC, Type.PSYCHIC))
        sp[10168] = form(sp.getValue(130), 10168, 8, "Galar", "Galarian Flopdragon", prevo = 129, types = listOf(Type.ICE, Type.PSYCHIC))
        sp[10233] = form(sp.getValue(3), 10233, 8, "Hisui", "Hisuian Leafking", prevo = 2, types = listOf(Type.FIRE, Type.GHOST))
        sp[10253] = form(sp.getValue(54), 10253, 9, "Paldea", "Paldean Duckling", types = listOf(Type.POISON, Type.GROUND))
        GameData(sp, w.moves, w.growth, w.itemPrices, w.builtAt)
    }

    /** Puts the form world into GameRepo (after FakeGame.install) with a few forms met / caught, and makes sure every sprite file exists. */
    private fun install(ctx: Context, wild: Int? = null, teamForm: Boolean = false) {
        BattleShotKit.gameRepoSet("loadedData", data)
        val st = GameRepo.state!!
        val rebuild = GameRepo::class.java.getDeclaredMethod("rebuild").also { it.isAccessible = true }
        // caught: Alolan Ratty (the newest catch, so the Pokédex hero shows it) and Galarian Flopdragon; seen only: Alolan Sparkrat, Hisuian Leafking
        val ratty = TestData.mon(data, 10091, 12, listOf("tackle"), "f1").also { it.caughtAt = 1_760_500_000_000L }
        st.mons += ratty; st.seen += 10091; st.caught += 10091
        st.seen += setOf(10100, 10233, 10168); st.caught += 10168
        st.seen += 20
        if (teamForm) {
            val flop = TestData.mon(data, 10168, 22, listOf("tackle", "watergun"), "f2").also { it.hp = it.hp * 6 / 10; it.caughtAt = 1_760_000_000_000L }
            st.mons += flop
            st.team[5] = flop.uid
        }
        if (wild != null) {
            val w = TestData.mon(data, wild, 25, listOf("tackle", "watergun"), "wildform")
            w.hp = w.hp * 7 / 10
            st.battle = Battle(w, Tier.NORMAL, answers = 1, tally = 10, rounds = 2, startedAt = 1L)
            st.seen += wild
        }
        rebuild.invoke(GameRepo)
        sprites(ctx)
    }

    /** Every species of the form world gets its three sprite files (a form borrows its base species' picture when there is one). */
    private fun sprites(ctx: Context) {
        val dir = Sprites.dir(ctx)
        for (s in data.species.values) for (sub in listOf("front", "back", "static")) {
            val target = File(dir, "$sub/${s.num}.png")
            if (target.isFile) continue
            val borrowed = File(dir, "$sub/${s.baseNum}.png")
            if (s.baseNum != s.num && borrowed.isFile) { borrowed.copyTo(target); continue }
            val col = Ui.typeColor(s.types.first())
            FakeSprites.write(FakeSprites.blob(96, col, back = sub == "back"), target)
        }
    }

    // ---------- Pokédex ----------

    private fun dex(ctx: Context, f: DexFilter = DexFilter()): View {
        install(ctx)
        val p = DexPage(ctx, NoHost)
        setField(p, "filter", f)
        val v = pageScreen(ctx, p)
        if (f.query.isNotEmpty()) (getField(p, "search") as EditText).setText(f.query)
        return v
    }

    /** forms-dex: forms sit right after their base species (Alolan Ratty after Ratty ...), caught / seen-only / unseen cells. */
    @Test fun dexGrid() {
        paparazzi.shootWith("forms-dex", both) { dex(it) }
        paparazzi.shootWith("forms-dex-number", both) { dex(it, DexFilter(query = "19")) }   // #019 finds the base species and its form
        paparazzi.shootWith("forms-dex-long", both) { dex(it, DexFilter(query = "ian")) }    // the long names: Galarian ..., Hisuian ...
    }

    private fun dialog(name: String, open: (Context) -> Unit) {
        paparazzi.shootPage(name, bothVariants) { ctx ->
            install(ctx)
            val d: AlertDialog = DialogShots.capture { open(ctx) } ?: error("no dialog was shown")
            DialogShots.scene(ctx, d)
        }
    }

    @Test fun dexDetail() {
        dialog("forms-dexdetail") { ctx -> DexDetail.show(DexPage(ctx, NoHost), data.sp(10091)) }
        dialog("forms-dexdetail-long") { ctx -> DexDetail.show(DexPage(ctx, NoHost), data.sp(10168)) }
    }

    @Test fun monDetail() {
        dialog("forms-mondetail") { ctx -> MonDetail.show(ctx, data, GameRepo.state!!.mon("f1")!!) }
    }

    // ---------- battle, team ----------

    @Test fun battle() {
        paparazzi.shootWith("forms-battle", both) { ctx ->
            install(ctx, wild = 10168)
            BattleShotKit.bound(ctx).root
        }
    }

    @Test fun team() {
        paparazzi.shootPage("forms-team", bothVariants) { ctx ->
            install(ctx, teamForm = true)
            pageScreen(ctx, TeamPage(ctx, NoHost))
        }
    }

    // ---------- Settings ----------

    /** forms-settings-catch: the real Settings page scrolled to the "Always catch" card (Legendary ... Starter, Regional form). */
    @Test fun settingsCatchCard() {
        paparazzi.shootPage("forms-settings-catch", bothVariants) { base ->
            install(base)
            val ctx = ShotCtx(base)
            val page = SettingsPage(ctx, NoHost)
            val v = pageScreen(ctx, page)
            page.open(com.husarp.ankigo.ui.SettingsSection.GAME)
            val dm = ctx.resources.displayMetrics
            v.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, dm.widthPixels, dm.heightPixels)
            val title = findText(v, "ALWAYS CATCH") ?: error("no 'Always catch' section")
            val content = getField(page, "content") as View
            var y = 0
            var c: View? = title
            while (c != null && c !== content) { y += c.top; c = c.parent as? View }
            (getField(page, "scroll") as ScrollView).scrollTo(0, (y - Ui.dp(ctx, 6)).coerceAtLeast(0))
            v
        }
    }

    private fun findText(v: View, text: String): TextView? {
        if (v is TextView && v.text.toString() == text) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), text)?.let { return it }
        return null
    }
}
