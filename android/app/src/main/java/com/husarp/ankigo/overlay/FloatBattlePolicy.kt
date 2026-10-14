package com.husarp.ankigo.overlay

/** A foreground notification is needed only for an allowed battle overlay outside AnkiGo's Activities. */
internal object FloatBattlePolicy {
    fun shouldRun(floating: Boolean, allowed: Boolean, appVisible: Boolean, ready: Boolean) =
        floating && allowed && !appVisible && ready
}
