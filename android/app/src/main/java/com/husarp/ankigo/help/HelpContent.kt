package com.husarp.ankigo.help

/** The four parts every troubleshooting page has, in this order (PLAN 44). */
enum class SectionKind(val title: String) {
    SEE("What you see"),
    WHY("Why it happens"),
    DO("What to do"),
    CHECK("How to check it worked"),
}

/** One part of a page: a [text] paragraph, or numbered [steps] (exact AnkiDroid/Anki/AnkiGo menu names), or both. */
data class HelpSection(val kind: SectionKind, val text: String = "", val steps: List<String> = emptyList())

data class HelpLink(val label: String, val url: String)

/** A help page. [summary] is the one line shown in the Help list. */
data class HelpPage(
    val id: String,
    val title: String,
    val summary: String,
    val sections: List<HelpSection>,
    val links: List<HelpLink> = emptyList(),
    val notes: List<GuideNote> = emptyList(),
) {
    fun section(kind: SectionKind): HelpSection? = sections.firstOrNull { it.kind == kind }
}

/**
 * Every help page, offline (PLAN 44). Pure Kotlin, no Android. The Help screen, the "?" buttons on problem banners and the
 * "Set up pictures" screen (via FixAction.HELP_PAGE) only show these. Page ids are stable: screens open a page by id.
 */
object HelpContent {
    const val PICTURES_DONT_SHOW = "pictures-dont-show"
    const val MOVE_COLLECTION = "move-collection-folder"
    const val PICTURES_MISSING = "pictures-missing-after-move"
    const val PICTURES_SYNC = "pictures-dont-sync"
    const val ANSWERS_NOT_DETECTED = "answers-not-detected"
    const val WIDGET_NOT_UPDATING = "widget-not-updating"
    const val BATTLE_POPS_UP = "battle-pops-up"
    const val ANKIMON_IMPORT_EXPORT = "ankimon-import-export"

    private const val WIKI = "https://github.com/ankidroid/Anki-Android/wiki"
    private const val MANUAL = "https://docs.ankidroid.org/"
    private const val RELEASES = "https://github.com/ankidroid/Anki-Android/releases"
    private const val ANKIWEB = "https://ankiweb.net/"
    private const val ANKIMON = "https://github.com/h0tp-ftw/ankimon"

    private fun see(text: String) = HelpSection(SectionKind.SEE, text)
    private fun why(text: String) = HelpSection(SectionKind.WHY, text)
    private fun doSteps(vararg steps: String) = HelpSection(SectionKind.DO, steps = steps.toList())
    private fun check(text: String) = HelpSection(SectionKind.CHECK, text)

    val pages: List<HelpPage> = listOf(
        HelpPage(
            PICTURES_DONT_SHOW, "Pictures don't show on my cards",
            "Cards show a gap or a broken picture where an image should be.",
            listOf(
                see("Your cards open, but images are missing. AnkiGo may show a pictures banner."),
                why(
                    "AnkiDroid only tells AnkiGo the file names. AnkiGo has to read the pictures from AnkiDroid's folder itself. " +
                        "The Play Store version of AnkiDroid keeps that folder in a place no other app can read. " +
                        "The GitHub \"full\" version can use the public AnkiDroid folder, and AnkiGo then needs \"All files access\".",
                ),
                doSteps(
                    "In AnkiGo open Set up pictures (tap the pictures banner, or Settings → Study). It shows which step is missing.",
                    "If it says your AnkiDroid is the Play Store version: in AnkiDroid sync everything, including media, to AnkiWeb first.",
                    "Install the \"full universal\" APK from the AnkiDroid GitHub releases page. If Android refuses, uninstall the Play Store AnkiDroid first (your cards are safe on AnkiWeb).",
                    "Open the new AnkiDroid, allow it \"All files access\" if it asks, and set its folder to /storage/emulated/0/AnkiDroid (see the page \"Moving AnkiDroid's folder\").",
                    "Sign in and download your collection from AnkiWeb.",
                    "In AnkiGo give it \"All files access\": open Set up pictures and tap the All files access button.",
                    "Don't want to move AnkiDroid? In AnkiDroid export the deck with media (Export, tick \"Include media\" and \"Support older Anki versions\"), then in AnkiGo import the .apkg file. AnkiGo keeps its own copy of the pictures.",
                ),
                check("Open a card that has a picture. It shows. In Set up pictures every step is green."),
            ),
            listOf(HelpLink("AnkiDroid releases on GitHub", RELEASES), HelpLink("AnkiDroid manual", MANUAL)),
        ),
        HelpPage(
            MOVE_COLLECTION, "Moving AnkiDroid's collection folder",
            "From Android/data to /storage/emulated/0/AnkiDroid, without losing anything.",
            listOf(
                see("AnkiDroid keeps your collection inside Android/data, and other apps can't open that folder."),
                why(
                    "On Android 11 and newer, apps can't read each other's Android/data folders. " +
                        "A collection in the public AnkiDroid folder can be read by AnkiGo once it has \"All files access\". An existing install does not move there by itself.",
                ),
                doSteps(
                    "In AnkiDroid sync with AnkiWeb and wait until media has finished too.",
                    "Make sure you use the GitHub \"full\" version of AnkiDroid (the Play Store one can't use the public folder).",
                    "Give AnkiDroid \"All files access\": Android Settings → Apps → AnkiDroid → Permissions → Files and media → Allow management of all files.",
                    "In AnkiDroid open Settings → Advanced → AnkiDroid directory (older versions call it Collection path) and set it to /storage/emulated/0/AnkiDroid.",
                    "AnkiDroid now has an empty collection there. Sync and, when it asks, choose Download from AnkiWeb. Never choose Upload now.",
                    "Keep AnkiDroid open until the media download is finished.",
                    "The old folder in Android/data stays as a backup. When everything works, you can remove it with a file manager.",
                ),
                check("AnkiDroid shows your decks and pictures. In AnkiGo, Set up pictures shows the collection in /storage/emulated/0/AnkiDroid."),
            ),
            listOf(HelpLink("AnkiDroid FAQ and wiki", WIKI), HelpLink("AnkiDroid manual", MANUAL)),
        ),
        HelpPage(
            PICTURES_MISSING, "Pictures missing after a move or a sync",
            "AnkiDroid hasn't downloaded all your pictures.",
            listOf(
                see("Some or most pictures are missing, but AnkiGo can read the folder. AnkiGo says \"AnkiDroid hasn't downloaded N pictures\"."),
                why(
                    "After a move, the new media list can wrongly think everything is in sync, so nothing gets downloaded. " +
                        "A big media download can also be cut short when the network timeout is too short.",
                ),
                doSteps(
                    "In AnkiDroid open Settings → Sync and raise Network timeout to 600 seconds.",
                    "Force a full sync: in Settings → Sync tap Force full sync (or sync, and when AnkiDroid asks which side to keep, choose Download from AnkiWeb). Never choose Upload.",
                    "Stay on Wi-Fi and keep AnkiDroid open and on screen until the media download is done.",
                    "In AnkiDroid use the menu → Check media to see how many files are missing now.",
                    "Never tap \"Delete unused\" while pictures are missing.",
                    "Last resort: copy the collection.media folder from the old AnkiDroid folder (in Android/data) into the new one, using a file manager with root or a PC.",
                ),
                check("Check media in AnkiDroid reports no missing files. In AnkiGo, Set up pictures shows 0 missing."),
            ),
            listOf(HelpLink("AnkiDroid manual: syncing", MANUAL), HelpLink("AnkiDroid wiki", WIKI)),
        ),
        HelpPage(
            PICTURES_SYNC, "Pictures don't reach my PC or phone",
            "A picture added on one device never shows up on the other.",
            listOf(
                see("You added a picture on the PC (or the phone), synced, and it's missing on the other device."),
                why("Media is synced separately from cards. It can be blocked by the network, by a blocker app, or by the battery saver."),
                doSteps(
                    "Make a test card with one picture on the PC. Sync it. Check it on the phone. Then do it the other way round.",
                    "Open ankiweb.net in a browser and look at the card there: this tells you which side didn't upload.",
                    "On the phone check that Settings → Sync → Fetch media on sync is on in AnkiDroid.",
                    "Turn off ad blockers (such as AdAway), and VPNs, while you sync. Set Android's Private DNS to Off or Automatic.",
                    "Allow AnkiDroid to run in the background: Android Settings → Apps → AnkiDroid → Battery → Unrestricted.",
                    "On the PC check that a firewall or antivirus isn't blocking Anki.",
                ),
                check("The test card shows its picture on both devices."),
            ),
            listOf(HelpLink("AnkiWeb", ANKIWEB), HelpLink("AnkiDroid manual: syncing", MANUAL)),
        ),
        HelpPage(
            ANSWERS_NOT_DETECTED, "My answers in AnkiDroid aren't counted",
            "You study in AnkiDroid, but AnkiGo's battle doesn't react.",
            listOf(
                see("You answer cards in AnkiDroid and nothing happens in the AnkiGo battle, or the count stays at zero."),
                why(
                    "AnkiGo has to ask AnkiDroid what you answered, which needs AnkiDroid's permission. " +
                        "It only checks while the battle is running (panel, floating window or picture-in-picture), and Android may stop AnkiGo in the background.",
                ),
                doSteps(
                    "Open AnkiGo → Settings → Diagnostics and tap Grant AnkiDroid permission. Allow it.",
                    "Check the top of Diagnostics: it should say \"API: OK\". If you have several AnkiDroid versions, use Switch AnkiDroid target to pick yours.",
                    "Keep the battle open: split screen, Pop out, or the floating battle window.",
                    "Allow AnkiGo to run in the background: Android Settings → Apps → AnkiGo → Battery → Unrestricted.",
                    "Make sure the battle is not paused (the Pause/Resume button).",
                    "Still stuck? In Diagnostics tap LIVE watch, answer one card in AnkiDroid, then Share report and send it.",
                ),
                check("Answer one card in AnkiDroid. The battle plays within a few seconds and the answer count goes up by one."),
            ),
            listOf(HelpLink("AnkiDroid manual", MANUAL)),
        ),
        HelpPage(
            WIDGET_NOT_UPDATING, "A widget isn't updating",
            "A home screen widget shows old numbers.",
            listOf(
                see("The motivation, study or buddy widget shows yesterday's numbers or never changes."),
                why("Widgets only update when AnkiGo is allowed to run. Battery savers and some launchers freeze them."),
                doSteps(
                    "Open AnkiGo once. Widgets refresh when the game opens and after answers.",
                    "Allow AnkiGo to run in the background: Android Settings → Apps → AnkiGo → Battery → Unrestricted.",
                    "Remove the widget and add it again: long-press the home screen → Widgets → AnkiGo.",
                    "Or in AnkiGo go to Settings → Diagnostics and use Pin motivation widget or Pin study widget.",
                    "On phones with an aggressive battery saver (some Xiaomi, Huawei, Samsung models), also turn off its \"optimise\" or \"sleeping apps\" setting for AnkiGo.",
                ),
                check("Answer a card. The widget changes within a minute, or when you next unlock the phone."),
            ),
        ),
        HelpPage(
            BATTLE_POPS_UP, "The battle window pops up when I leave AnkiGo",
            "Choose what happens when you leave the battle screen.",
            listOf(
                see("A small battle window appears over other apps when you press Home or switch app."),
                why(
                    "The battle does not pop out by itself any more. By default nothing happens when you leave. " +
                        "A small window only appears when you tap Pop out, or when you chose Picture-in-picture or Floating battle window for \"On leaving\".",
                ),
                doSteps(
                    "In AnkiGo open Settings → Battle panel & windows → On leaving.",
                    "Choose Nothing. The window then stays closed until you tap Pop out (the small-window button at the top of the battle).",
                    "Choose Picture-in-picture or Floating battle window only if you want it every time you leave.",
                    "Close a small window with its X, or open AnkiGo again.",
                ),
                check("Press Home from the battle. Nothing pops up (with Nothing chosen)."),
            ),
        ),
        HelpPage(
            ANKIMON_IMPORT_EXPORT, "Ankimon: import and export",
            "Bring your PC Ankimon save to the phone, and back.",
            listOf(
                see("You play Ankimon on the PC and want the same Pokémon in AnkiGo, or you can't find Export in Ankimon."),
                why(
                    "Ankimon 2.4-E and newer can export one .db file. Older versions only have Showdown and Pokepaste text exports, " +
                        "and an even older save is a few JSON files. AnkiGo can only import the .db file.",
                ),
                HelpSection(
                    SectionKind.DO,
                    text = "Follow the steps below. The same steps are in Settings → ${AnkimonGuide.SECTION_TITLE}.",
                    steps = AnkimonGuide.steps.map { it.title + ": " + it.detail } +
                        "Going back to the PC: AnkiGo → Settings → ${AnkimonGuide.SECTION_TITLE} → Export for Ankimon PC…, " +
                        "then in Anki use Ankimon → Game → Import Save File… and restart Anki.",
                ),
                check("The preview before importing shows how many Pokémon it adds. After importing, your Pokémon and Pokédex are in AnkiGo."),
            ),
            listOf(HelpLink("Ankimon on GitHub", ANKIMON)),
            AnkimonGuide.notes,
        ),
    )

    fun page(id: String): HelpPage? = pages.firstOrNull { it.id == id }
}
