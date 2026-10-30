package com.husarp.ankigo.ui

import com.husarp.ankigo.R

/** Picked round-3 paths and light/dark colours from plan-icons-2.html. */
internal object FieldIcons {
    fun of(key: String): Int? = when (key) {
        "rain" -> R.drawable.ic_field_rain
        "sun" -> R.drawable.ic_field_sun
        "sand" -> R.drawable.ic_field_sand
        "hail" -> R.drawable.ic_field_hail
        "snow" -> R.drawable.ic_field_snow
        "electric" -> R.drawable.ic_field_electric
        "grassy" -> R.drawable.ic_field_grassy
        "misty" -> R.drawable.ic_field_misty
        "psychic" -> R.drawable.ic_field_psychic
        "trickroom" -> R.drawable.ic_field_trickroom
        "gravity" -> R.drawable.ic_field_gravity
        "magicroom" -> R.drawable.ic_info
        "wonderroom" -> R.drawable.ic_field_wonderroom
        "mudsport" -> R.drawable.ic_field_mudsport
        "watersport" -> R.drawable.ic_field_watersport
        "iondeluge" -> R.drawable.ic_field_iondeluge
        "reflect" -> R.drawable.ic_field_reflect
        "lightscreen" -> R.drawable.ic_field_lightscreen
        "auroraveil" -> R.drawable.ic_field_auroraveil
        "safeguard" -> R.drawable.ic_field_safeguard
        "mist" -> R.drawable.ic_field_mist
        "tailwind" -> R.drawable.ic_field_tailwind
        "wish" -> R.drawable.ic_field_wish
        "mp.fs" -> R.drawable.ic_field_mp_fs
        else -> null
    }
}
