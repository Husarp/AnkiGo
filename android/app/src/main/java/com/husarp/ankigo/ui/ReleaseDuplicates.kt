package com.husarp.ankigo.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import com.husarp.ankigo.R
import com.husarp.ankigo.game.Duplicates
import com.husarp.ankigo.game.ReleasePlan

/**
 * R10 B21 "Release duplicates" (Settings -> Game -> Pokémon): one confirmation, then one game action. The rule is
 * [Duplicates.releasePlan] (keeps the best copy of each species and everything [Duplicates.keepReason] protects); the action
 * ([com.husarp.ankigo.game.Game.releaseDuplicates]) gets the uids the dialog showed and releases only those that a fresh plan still
 * lists: what is released is what was confirmed, never more.
 */
object ReleaseDuplicates {
    /** Nothing to release: a toast, no dialog. Else ONE dialog saying how many go; "Release N" runs the action. */
    fun start(page: GamePage) {
        val plan = Duplicates.releasePlan(page.data, page.state)
        if (plan.isEmpty) { Ui.toast(page, ReleaseDuplicatesText.NOTHING); return }
        val confirmed = plan.release.toSet() // the Pokémon the dialog names: only these can go
        Pickers.show(dialog(page, plan) {
            // the state may have changed while the dialog was open: none of the confirmed can still go -> no action (and so no save)
            if (Duplicates.releasePlan(page.data, page.state).release.none { it in confirmed }) Ui.toast(page, Duplicates.CHANGED_NOTHING)
            else page.act { it.releaseDuplicates(confirmed) }
        })
    }

    /** The confirmation for [plan]; [onRelease] runs on "Release N". */
    internal fun dialog(ctx: Context, plan: ReleasePlan, onRelease: () -> Unit): AppDialog {
        val d = AppDialog.Builder(ctx)
            .setTitle(ReleaseDuplicatesText.title(plan))
            .setMessage(ReleaseDuplicatesText.message(plan))
            .setPositiveButton(ReleaseDuplicatesText.button(plan)) { _, _ -> onRelease() }
            .setNegativeButton("Cancel", null).create()
        d.button(AppDialog.BUTTON_POSITIVE)?.let { b -> // a red button: this can't be undone
            val fill = Ui.c(ctx, R.color.ag_red)
            val ripple = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), Ui.rounded(fill, 22, ctx), Ui.rounded(Color.BLACK, 22, ctx))
            Ui.touchPill(b, ripple, 44, 22)
            b.setTextColor(AppDialogRules.onFill(fill, Ui.c(ctx, R.color.ag_crust), TypeInk::contrast))
        }
        return d
    }
}

/** The words of the confirmation (pure, so the JVM tests check them). */
object ReleaseDuplicatesText {
    const val NOTHING = "No duplicates to release"
    /** Species named in the list before "and N more". */
    const val MAX_SPECIES = 6

    fun title(plan: ReleasePlan) = "Release ${plan.count} Pokémon?"

    fun button(plan: ReleasePlan) = "Release ${plan.count}"

    /** "Pidgey ×3, Rattata ×2, and 4 more", the rule, the protected copies that stay, the reward, the warning. */
    fun message(plan: ReleasePlan): String {
        val lines = ArrayList<String>()
        lines += species(plan)
        lines += "\nKeeps the highest-level copy of each species."
        if (plan.keptCount > 0) {
            val n = plan.keptCount
            lines += "${if (n == 1) "1 duplicate stays because it is" else "$n duplicates stay because they are"} protected (${kept(plan)})."
        }
        lines += "\nReleasing gives no money or items. This can't be undone."
        return lines.joinToString("\n")
    }

    /** "Pidgey ×3, Rattata ×2, ..." for the biggest groups, then "and N more" for the rest. */
    fun species(plan: ReleasePlan): String {
        val shown = plan.groups.take(MAX_SPECIES).joinToString(", ") { "${it.name} ×${it.count}" }
        val more = plan.groups.size - MAX_SPECIES
        return if (more > 0) "$shown, and $more more" else shown
    }

    /** "2 favourites, 1 shiny, 1 holding an item": why the protected copies stay (in the order of [Duplicates.Keep]). */
    fun kept(plan: ReleasePlan): String = plan.keptProtected.entries.filter { it.value > 0 }.joinToString(", ") { (why, n) ->
        when (why) {
            Duplicates.Keep.FAVOURITE -> if (n == 1) "1 favourite" else "$n favourites"
            Duplicates.Keep.SHINY -> if (n == 1) "1 shiny" else "$n shinies"
            Duplicates.Keep.TEAM -> "$n in your team"
            Duplicates.Keep.HOLDS_ITEM -> "$n holding an item"
            Duplicates.Keep.PROTECTED_KIND -> "$n of a protected kind"
            Duplicates.Keep.EXP_SHARE -> "the Exp. Share holder"
            Duplicates.Keep.NICKNAME -> "$n nicknamed"
        }
    }
}
