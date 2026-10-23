package com.husarp.ankigo.ui

import android.content.Intent

/** One Pokémon picked for Compare: an owned one (by uid) or a Pokédex entry (by species number). */
sealed class ComparePick {
    data class Owned(val uid: String) : ComparePick()
    data class Dex(val species: Int) : ComparePick()
}

/** Compare any number (at least [MIN]) of Pokémon side by side: its own screen ([CompareActivity] / [ComparePage]). */
object Compare {
    const val MIN = 2

    /** Opens the Compare screen for [picks] from [page] (an Activity page or the floating card). */
    fun open(page: GamePage, picks: List<ComparePick>) {
        if (CompareTable.build(page.data, page.state, picks, false).columns.size < MIN) {
            Ui.toast(page, "Pick at least $MIN Pokémon to compare")
            return
        }
        page.startScreen(Intent(page, CompareActivity::class.java).putStringArrayListExtra(CompareActivity.EXTRA_PICKS, ArrayList(picks.map(CompareArgs::encode))))
    }
}

/** The Compare screen: the picks come in the Intent (see [CompareArgs]). */
class CompareActivity : PageActivity() {
    override val closesWhenLeft = true // PLAN 222
    private var comparePage: ComparePage? = null
    private var restoredFit = CompareFit.atOpen()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        // a rotation keeps the columns chosen on this screen (R9 C1); a fresh open starts on automatic
        restoredFit = CompareFit.valid(savedInstanceState?.getInt(STATE_FIT, CompareFit.AUTO) ?: CompareFit.AUTO)
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        comparePage?.let { outState.putInt(STATE_FIT, it.fit) }
    }

    override fun createPage(): GamePage = ComparePage(this, this, CompareArgs.decodeAll(intent.getStringArrayListExtra(EXTRA_PICKS)))
        .also { it.fit = restoredFit; comparePage = it }

    companion object {
        const val EXTRA_PICKS = "picks"
        private const val STATE_FIT = "compare_fit_open"
    }
}
