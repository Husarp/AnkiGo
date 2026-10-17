package com.husarp.ankigo.shots

import android.app.Activity
import android.content.Context
import android.content.Intent
import app.cash.paparazzi.Paparazzi
import com.husarp.ankigo.GameRepo
import com.husarp.ankigo.help.GameGuide
import com.husarp.ankigo.help.HelpContent
import com.husarp.ankigo.help.HelpRoute
import com.husarp.ankigo.help.HelpScreen
import com.husarp.ankigo.ui.PageHost
import org.junit.Rule
import org.junit.Test

/*
 * TICKET R3-V3a: the Help screen: home (guide tiles + "Fix a problem" list), problem pages, and the game guide with one
 * chapter open. Light + dark; PHONE + SPLIT.
 */
class HelpShots {
    @get:Rule val paparazzi: Paparazzi = Shots.rule()

    private class ActivityHost(val activity: Activity) : PageHost {
        override val hostActivity: Activity get() = activity
        override fun closePage() = Unit
        override fun startForResult(intent: Intent, onResult: (Int, Intent?) -> Unit) = Unit
    }

    private fun screen(ctx: Context, route: HelpRoute) =
        pageScreen(ctx, HelpScreen(ctx, ActivityHost(attach(Activity(), ctx)), route, false))

    private val long = Variant("long", 412, 1300)

    private val all = listOf(Variants.PHONE, Variants.SPLIT)

    @Test fun home() = paparazzi.shootPage("help-home", all) { screen(it, HelpRoute.Home) }

    @Test fun problemPictures() = paparazzi.shootPage("help-page-pictures", all + long) { screen(it, HelpRoute.Problem(HelpContent.PICTURES_DONT_SHOW)) }

    @Test fun problemMove() = paparazzi.shootPage("help-page-move", listOf(Variants.PHONE)) { screen(it, HelpRoute.Problem(HelpContent.MOVE_COLLECTION)) }

    @Test fun problemAnkimon() = paparazzi.shootPage("help-page-ankimon", listOf(Variants.PHONE, long)) { screen(it, HelpRoute.Problem(HelpContent.ANKIMON_IMPORT_EXPORT)) }

    @Test fun problemAnswers() = paparazzi.shootPage("help-page-answers", listOf(Variants.PHONE)) { screen(it, HelpRoute.Problem(HelpContent.ANSWERS_NOT_DETECTED)) }

    @Test fun guideClosed() = paparazzi.shootPage("help-guide-closed", listOf(Variants.PHONE)) { screen(it, HelpRoute.Guide(null)) }

    @Test fun guideBattles() = paparazzi.shootPage("help-guide-battles", all) { screen(it, HelpRoute.Guide(GameGuide.BATTLES)) }

    @Test fun guideOthers() {
        for (id in listOf(GameGuide.LEVELS, GameGuide.TYPES, GameGuide.STATS, GameGuide.CATCHING, GameGuide.MONEY, GameGuide.HEALING, GameGuide.ITEMS)) {
            paparazzi.shootPage("help-guide-$id", listOf(Variants.PHONE)) { screen(it, HelpRoute.Guide(id)) }
        }
    }
}
