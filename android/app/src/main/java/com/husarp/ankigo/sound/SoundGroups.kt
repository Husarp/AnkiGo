package com.husarp.ankigo.sound

/** The three volume sliders of the sound settings (pick 84B). */
enum class SoundChannel { BUTTONS, BATTLE, CRIES }

/** The seven switchable groups of sounds; each one plays on one volume [channel]. */
enum class SoundGroup(val channel: SoundChannel) {
    ANSWER_BUTTONS(SoundChannel.BUTTONS), SHOW_ANSWER(SoundChannel.BUTTONS), MENUS(SoundChannel.BUTTONS), SHOP(SoundChannel.BUTTONS),
    BATTLE(SoundChannel.BATTLE), MOMENTS(SoundChannel.BATTLE), CRIES(SoundChannel.CRIES),
}

/**
 * Preference keys and defaults of the sound settings, pure so it is JVM-tested. [stored] reads one stored value (null = not stored).
 * Old saves keep working without a write: the first four groups and BUTTONS are new; BATTLE and CRIES keep their old keys
 * "battle" and "cries"; MOMENTS starts as the stored "battle" value; BATTLE and CRIES volumes start as the old single "volume".
 */
object SoundDefaults {
    const val BATTLE_KEY = "battle"
    const val OLD_VOLUME_KEY = "volume"
    const val BUTTONS_VOLUME = 40
    const val OTHER_VOLUME = 70

    fun groupKey(g: SoundGroup) = when (g) {
        SoundGroup.ANSWER_BUTTONS -> "g_answer"; SoundGroup.SHOW_ANSWER -> "g_show"; SoundGroup.MENUS -> "g_menus"
        SoundGroup.SHOP -> "g_shop"; SoundGroup.BATTLE -> BATTLE_KEY; SoundGroup.MOMENTS -> "g_moments"; SoundGroup.CRIES -> "cries"
    }

    fun volumeKey(ch: SoundChannel) = when (ch) {
        SoundChannel.BUTTONS -> "vol_buttons"; SoundChannel.BATTLE -> "vol_battle"; SoundChannel.CRIES -> "vol_cries"
    }

    fun groupOn(g: SoundGroup, stored: (String) -> Boolean?): Boolean =
        stored(groupKey(g)) ?: if (g == SoundGroup.MOMENTS) stored(BATTLE_KEY) ?: true else true

    /**
     * What to store alongside a write of the BATTLE switch: MOMENTS' effective value when its own key is still absent (it has been
     * following "battle"), so changing BATTLE no longer drags it along. Null = nothing to add. Reads never write.
     */
    fun momentsToPersist(stored: (String) -> Boolean?): Boolean? =
        if (stored(groupKey(SoundGroup.MOMENTS)) == null) groupOn(SoundGroup.MOMENTS, stored) else null

    fun volume(ch: SoundChannel, stored: (String) -> Int?): Int {
        val own = stored(volumeKey(ch))
        val v = own ?: if (ch == SoundChannel.BUTTONS) BUTTONS_VOLUME else stored(OLD_VOLUME_KEY) ?: OTHER_VOLUME
        return v.coerceIn(0, 100)
    }
}
