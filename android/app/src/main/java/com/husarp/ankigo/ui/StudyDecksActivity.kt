package com.husarp.ankigo.ui

import android.os.Bundle

/**
 * No-UI trampoline. The deck list moved into the home screen's Study view ([StudyHome] inside [GameActivity], PLAN 88);
 * this class stays in the manifest so old PendingIntents and pinned shortcuts that name it still work: it forwards to
 * the Study destination and finishes. Translucent, out of recents, finishes itself.
 */
class StudyDecksActivity : AgActivity() {
    override val showsUpdateBanner = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Nav.intent(this, Dest.STUDY))
        finish()
    }
}
