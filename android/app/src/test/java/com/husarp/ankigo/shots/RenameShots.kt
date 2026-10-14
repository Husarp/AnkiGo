package com.husarp.ankigo.shots

import android.content.Context
import android.view.View
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.ui.MonActions
import com.husarp.ankigo.ui.TeamPage
import org.junit.Rule
import org.junit.Test

/* T41: the rename dialog (empty field and with a nickname), the Pokémon menu with "Rename…", a team row with a nickname. */
class RenameShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()
    private val st get() = GameRepo.state!!

    private fun dialog(name: String, open: (Context) -> Unit) {
        paparazzi.shootPage(name) { ctx ->
            val d = DialogShots.capture { open(ctx) } ?: error("no dialog was shown")
            DialogShots.scene(ctx, d)
        }
    }

    @Test fun rename() {
        dialog("rename-dlg-empty") { ctx -> call(MonActions(TeamPage(ctx, NoHost)), "rename", st.mon("t1")!!) }
        dialog("rename-dlg-nick") { ctx ->
            st.mon("t1")!!.nickname = "Zappy the Great"
            call(MonActions(TeamPage(ctx, NoHost)), "rename", st.mon("t1")!!)
        }
        dialog("rename-menu") { ctx -> MonActions(TeamPage(ctx, NoHost)).menu(st.mon("t3")!!) }
        paparazzi.shootPage("rename-team") { ctx ->
            st.mon("t1")!!.nickname = "Zappy"; st.mon("t3")!!.nickname = "Mr. Longest Nick Ever 1"
            pageScreen(ctx, TeamPage(ctx, NoHost)) as View
        }
    }
}
