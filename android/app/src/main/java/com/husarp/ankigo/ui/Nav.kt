package com.husarp.ankigo.ui

import android.app.Activity
import android.content.Context
import android.content.Intent

/** Everything the app can jump to. The bottom bar's tabs are groups of these ([BottomNav.tabOf]). */
enum class Dest { STUDY, BATTLE, TEAM, BOX, DEX, SHOP, STATS, ACHIEVEMENTS, SETTINGS }

/**
 * The ONE place that knows which Activity (and which section extra) shows a [Dest]. Every in-app jump to a tab or a
 * section and every deep link (widgets, reminders, PiP, the floating window, first run) asks it, so moving a screen
 * means changing the `when` in [target] and nothing else. [intent] has only the target's own flags: the caller adds what it needs
 * (new task from a service, no user action…); [open] is the common "go there from inside the app".
 */
object Nav {
    /**
     * The Activity of a destination, its one extra (name, value; null when it needs none) and the flags every start of it
     * carries (0 for none). Plain data, so it is tested on the JVM.
     */
    class Target(val screen: Class<out Activity>, val extra: Pair<String, String>? = null, val flags: Int = 0)

    fun target(dest: Dest): Target = when (dest) {
        Dest.STUDY -> home(HomeView.STUDY)
        Dest.BATTLE -> home(HomeView.BATTLE)
        Dest.TEAM -> pokemon(TeamSection.TEAM)
        Dest.BOX -> pokemon(TeamSection.BOX)
        Dest.DEX -> pokemon(TeamSection.DEX)
        Dest.SHOP -> Target(ShopActivity::class.java, null, TAB_FLAGS) // the Shop is a tab of its own (R10 W11); onResume shows today's stock
        Dest.STATS -> stats(StatsSection.STATISTICS)
        Dest.ACHIEVEMENTS -> stats(StatsSection.ACHIEVEMENTS)
        Dest.SETTINGS -> Target(SettingsActivity::class.java)
    }

    // Study and the full-screen battle are the two views of the home screen (GameActivity). An open one is brought to the front
    // and told which view to show (onNewIntent), also from a widget, reminder or service: never a second home screen.
    private fun home(v: HomeView) = Target(GameActivity::class.java, HomeView.EXTRA_VIEW to v.key,
        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    // The Pokémon tab (Team · Box · Pokédex) and the Stats tab (Statistics · Achievements) are one Activity each, and both
    // handle onNewIntent (section, pick, details): an open one is brought to the front and told, never stacked a second time.
    // The Shop tab has no sections: an open one is just brought to the front.
    private fun pokemon(s: TeamSection) = Target(TeamActivity::class.java, TeamActivity.EXTRA_SECTION to s.name, TAB_FLAGS)
    private fun stats(s: StatsSection) = Target(StatsActivity::class.java, StatsActivity.EXTRA_SECTION to s.name, TAB_FLAGS)

    /** The section name the Pokémon tab used to have for the Shop: an old intent still carrying it is sent to the Shop tab ([TeamActivity]). */
    const val OLD_SHOP_SECTION = "SHOP"

    /** The flags of the tab targets that reuse an open screen ([intent] carries them, so every start of a tab has them, not only [open]). */
    const val TAB_FLAGS = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP

    /**
     * The Intent that shows [dest] (the target's own flags only; the caller adds what it needs). [from]: for the full-screen
     * battle, the screen that opened it (the Pokémon tab's battle card): Back returns there ([HomeNav.back]).
     */
    fun intent(ctx: Context, dest: Dest, from: Dest? = null): Intent {
        val t = target(dest)
        return Intent(ctx, t.screen).also { i ->
            t.extra?.let { (k, v) -> i.putExtra(k, v) }
            if (t.flags != 0) i.addFlags(t.flags)
            if (dest == Dest.BATTLE && from != null) i.putExtra(HomeView.EXTRA_FROM, from.name)
        }
    }

    /** The Pokémon tab's battle card "Pop out": the home screen comes to the front (its view stays) and pops the battle out when it is ready. */
    fun popOutIntent(ctx: Context): Intent =
        Intent(ctx, GameActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(HomeView.EXTRA_POP_OUT, true)

    /** Goes to [dest]: an existing screen of it is brought to the front; from a service or a card (no Activity) in its own task. */
    fun open(ctx: Context, dest: Dest, from: Dest? = null) {
        val i = intent(ctx, dest, from).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (Ui.findActivity(ctx) == null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Extra of the Stats tab: the id of the achievement whose detail sheet opens at once (R4-V2, the popup's "See it"). */
    const val EXTRA_ACHIEVEMENT = "achievement"

    /** Stats > Achievements with the detail sheet of achievement [id] open (null = the list only). */
    fun achievementIntent(ctx: Context, id: String?): Intent = intent(ctx, Dest.ACHIEVEMENTS).also { i -> if (id != null) i.putExtra(EXTRA_ACHIEVEMENT, id) }

    /** Goes to [achievementIntent]: an existing Stats screen is brought to the front and told (onNewIntent). */
    fun openAchievement(ctx: Context, id: String?) {
        val i = achievementIntent(ctx, id).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (Ui.findActivity(ctx) == null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
