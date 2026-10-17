package com.husarp.ankigo.help

/** One numbered step of a guide: a short [title] and an optional [detail] line under it. */
data class GuideStep(val title: String, val detail: String = "")

/** A warning or fact shown under the steps. [severity] lets the screen pick a tone. */
data class GuideNote(val severity: Severity, val title: String, val text: String) {
    enum class Severity { INFO, WARNING }
}

/**
 * How to bring an Ankimon (PC) save to the phone (PLAN 43), as plain data: the Settings section and the first-run step
 * both show the same [steps] and [notes]. Pure Kotlin, no Android; texts are checked by HelpContentTest.
 */
object AnkimonGuide {
    /** Section name in Settings (PLAN 43). */
    const val SECTION_TITLE = "Ankimon PC sync"

    val steps: List<GuideStep> = listOf(
        GuideStep(
            "Update Ankimon on the PC",
            "In Anki open Tools → Add-ons, pick Ankimon, then Check for Updates. You need version 2.4-E or newer.",
        ),
        GuideStep(
            "Export your save",
            "In Anki open the Ankimon menu → Game → Export Save File… and pick where to put it. You get one .db file (for example ankimon-save-yourname.db).",
        ),
        GuideStep(
            "Move the .db file to your phone",
            "Use a cable, Google Drive, or e-mail it to yourself. Any folder on the phone is fine.",
        ),
        GuideStep(
            "Import it here",
            "In AnkiGo open Settings → $SECTION_TITLE → Import Ankimon save (.db)… and pick the file. You see a preview first, and nothing changes until you confirm.",
        ),
    )

    val notes: List<GuideNote> = listOf(
        GuideNote(
            GuideNote.Severity.WARNING, "Old Ankimon: JSON files instead of a .db",
            "If your save is a few files named mypokemon.json, mainpokemon.json, items.json and badges.json, your Ankimon is too old. " +
                "Update it first (step 1), start Anki once, and then export.",
        ),
        GuideNote(
            GuideNote.Severity.WARNING, "Update says \"Access is denied\"",
            "Switch Ankimon off in Tools → Add-ons (Toggle Enabled), restart Anki, update it, and switch it on again. " +
                "To be safe, first copy the add-on's user_files folder somewhere else (Tools → Add-ons → Ankimon → View Files).",
        ),
        GuideNote(
            GuideNote.Severity.WARNING, "Showdown and Pokepaste exports are not the right file",
            "Only Game → Export Save File… makes a file AnkiGo can import. The Pokémon Showdown and Pokepaste texts miss levels, natures and your team.",
        ),
        GuideNote(
            GuideNote.Severity.INFO, "Importing only adds",
            "Import merges: it adds Pokémon you don't have and upgrades ones that are further along in the file. It never removes anything. " +
                "Importing the same file twice changes nothing.",
        ),
        GuideNote(
            GuideNote.Severity.INFO, "Coins and items: the higher number wins",
            "For your money and for every item, AnkiGo keeps the higher of the phone's value and the file's value. Nothing is added up.",
        ),
    )
}
