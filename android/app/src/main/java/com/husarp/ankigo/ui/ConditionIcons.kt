package com.husarp.ankigo.ui

import com.husarp.ankigo.R

/** Battle conditions, including action-only effects; Tabler outlines match the app icons. */
internal object ConditionIcons {
    fun of(key: String): Int? = when (key) {
        "switchtrap" -> R.drawable.ic_condition_switchtrap
        "confusion" -> R.drawable.ic_condition_confusion
        "attract" -> R.drawable.ic_condition_attract
        "partiallytrapped" -> R.drawable.ic_condition_partiallytrapped
        "taunt" -> R.drawable.ic_condition_taunt
        "encore" -> R.drawable.ic_condition_encore
        "disable" -> R.drawable.ic_condition_disable
        "torment" -> R.drawable.ic_condition_torment
        "healblock" -> R.drawable.ic_condition_healblock
        "throatchop" -> R.drawable.ic_condition_throatchop
        "yawn" -> R.drawable.ic_condition_yawn
        "imprison" -> R.drawable.ic_condition_imprison
        "lockedmove" -> R.drawable.ic_condition_lockedmove
        "uproar" -> R.drawable.ic_condition_uproar
        "smackdown" -> R.drawable.ic_condition_smackdown
        "tarshot" -> R.drawable.ic_condition_tarshot
        "foresight" -> R.drawable.ic_condition_foresight
        "miracleeye" -> R.drawable.ic_condition_miracleeye
        "leechseed" -> R.drawable.ic_condition_leechseed
        "curse" -> R.drawable.ic_condition_curse
        "nightmare" -> R.drawable.ic_condition_nightmare
        "saltcure" -> R.drawable.ic_condition_saltcure
        "syrupbomb" -> R.drawable.ic_condition_syrupbomb
        "substitute" -> R.drawable.ic_condition_substitute
        "destinybond" -> R.drawable.ic_condition_destinybond
        "aquaring" -> R.drawable.ic_condition_aquaring
        "ingrain" -> R.drawable.ic_condition_ingrain
        "stockpile" -> R.drawable.ic_condition_stockpile
        "magnetrise" -> R.drawable.ic_condition_magnetrise
        "telekinesis" -> R.drawable.ic_condition_telekinesis
        "charge" -> R.drawable.ic_condition_charge
        "defensecurl" -> R.drawable.ic_condition_defensecurl
        "minimize" -> R.drawable.ic_condition_minimize
        "powertrick" -> R.drawable.ic_condition_powertrick
        "powershift" -> R.drawable.ic_condition_powershift
        "ab.flashfire" -> R.drawable.ic_condition_ab_flashfire
        "ab.gas" -> R.drawable.ic_condition_ab_gas
        "spikes" -> R.drawable.ic_condition_spikes
        "toxicspikes" -> R.drawable.ic_condition_toxicspikes
        "stealthrock" -> R.drawable.ic_condition_stealthrock
        "stickyweb" -> R.drawable.ic_condition_stickyweb
        "reflect" -> R.drawable.ic_field_reflect
        "lightscreen" -> R.drawable.ic_field_lightscreen
        "auroraveil" -> R.drawable.ic_field_auroraveil
        "safeguard" -> R.drawable.ic_field_safeguard
        "mist" -> R.drawable.ic_field_mist
        "tailwind" -> R.drawable.ic_field_tailwind
        "wish" -> R.drawable.ic_field_wish
        "mp.fs" -> R.drawable.ic_field_mp_fs
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
        "magicroom" -> R.drawable.ic_condition_magicroom
        "wonderroom" -> R.drawable.ic_field_wonderroom
        "mudsport" -> R.drawable.ic_field_mudsport
        "watersport" -> R.drawable.ic_field_watersport
        "iondeluge" -> R.drawable.ic_field_iondeluge
        "brn" -> R.drawable.ic_condition_brn
        "psn" -> R.drawable.ic_condition_psn
        "tox" -> R.drawable.ic_condition_tox
        "par" -> R.drawable.ic_condition_par
        "slp" -> R.drawable.ic_condition_slp
        "frz" -> R.drawable.ic_condition_frz
        "embargo" -> R.drawable.ic_condition_embargo
        "perishsong" -> R.drawable.ic_condition_perishsong
        "happyhour" -> R.drawable.ic_condition_happyhour
        "flinch" -> R.drawable.ic_condition_flinch
        "recharge", "mustrecharge" -> R.drawable.ic_condition_mustrecharge
        "charging" -> R.drawable.ic_condition_charging
        "glaiverush" -> R.drawable.ic_condition_glaiverush
        "protect" -> R.drawable.ic_condition_protect
        "kingsshield" -> R.drawable.ic_condition_kingsshield
        "spikyshield" -> R.drawable.ic_condition_spikyshield
        "banefulbunker" -> R.drawable.ic_condition_banefulbunker
        "burningbulwark" -> R.drawable.ic_condition_burningbulwark
        "silktrap" -> R.drawable.ic_condition_silktrap
        "obstruct" -> R.drawable.ic_condition_obstruct
        "quickguard" -> R.drawable.ic_condition_quickguard
        "wideguard" -> R.drawable.ic_condition_wideguard
        "craftyshield" -> R.drawable.ic_condition_craftyshield
        "matblock" -> R.drawable.ic_condition_matblock
        "endure" -> R.drawable.ic_condition_endure
        "electrify" -> R.drawable.ic_condition_electrify
        "roost" -> R.drawable.ic_condition_roost
        "magiccoat" -> R.drawable.ic_condition_magiccoat
        "snatch" -> R.drawable.ic_condition_snatch
        "powder" -> R.drawable.ic_condition_powder
        "lockon" -> R.drawable.ic_condition_lockon
        "transform", "mc.transform" -> R.drawable.ic_condition_mc_transform
        "typechange", "mc.types" -> R.drawable.ic_condition_mc_types
        "bide", "mp.bide" -> R.drawable.ic_condition_mp_bide
        "mp.rollout" -> R.drawable.ic_condition_mp_rollout
        "mp.rage" -> R.drawable.ic_condition_mp_rage
        else -> if (key.startsWith("stat.")) R.drawable.ic_swords else null
    }
}
