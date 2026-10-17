package com.husarp.ankigo.help

enum class StepStatus { OK, WARN, FAIL, UNKNOWN }

enum class StepId { ANKIDROID, BUILD, FOLDER, ALL_FILES, SAMPLE, MISSING }

/** What a fix button does. The screen maps each to an Intent / dialog; [label] is the button text. */
enum class FixAction(val label: String) {
    OPEN_ALL_FILES_ACCESS("Allow All files access"),
    OPEN_ANKIDROID_SETTINGS("Open AnkiDroid settings"),
    OPEN_ANKIDROID("Open AnkiDroid"),
    OPEN_GITHUB_RELEASES("AnkiDroid downloads (GitHub)"),
    IMPORT_APKG("Import a deck file (.apkg)"),
    HELP_PAGE("Read the help page"),
}

/** One fix button. [helpPageId] is set for [FixAction.HELP_PAGE] (an id of [HelpContent]). */
data class Fix(val action: FixAction, val helpPageId: String? = null)

data class SetupStep(val id: StepId, val title: String, val status: StepStatus, val text: String, val fixes: List<Fix> = emptyList())

/** The checks behind "Set up pictures" and the Diagnostics rows. Pure Kotlin. */
object PictureSetup {
    /** How many of the next cards the missing-pictures check looks at. */
    const val SAMPLE_CARDS = 50
    /** At least this many missing pictures (and at least [MANY_PERCENT] % of the sample) is a FAIL; fewer is a WARN. */
    const val MANY_MISSING = 3
    const val MANY_PERCENT = 10

    private val PICTURE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "avif", "heic")

    /** True when [name] (a media file name from the API, maybe URL-encoded) is a picture. */
    fun isPicture(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in PICTURE_EXT

    fun manyMissing(missing: Int, total: Int): Boolean = missing >= MANY_MISSING && missing * 100 >= total * MANY_PERCENT

    private val GITHUB = Fix(FixAction.OPEN_GITHUB_RELEASES)
    private val ALL_FILES = Fix(FixAction.OPEN_ALL_FILES_ACCESS)
    private val APKG = Fix(FixAction.IMPORT_APKG)
    private fun help(id: String) = Fix(FixAction.HELP_PAGE, id)

    fun evaluate(f: PictureFacts): List<SetupStep> {
        val steps = ArrayList<SetupStep>()
        val folderOk = f.publicFolder == PublicFolder.READABLE
        // A verified readable alternative: AnkiGo has its own source (picked folder / imported copy) and really read sample pictures.
        val altOk = f.ownSource && f.apiAllowed && f.sampleReadable > 0
        val altText = if (f.pickedFolder) "the folder you picked" else "the pictures you imported"

        // 1. AnkiDroid there?
        steps += if (f.ankiDroidInstalled) {
            SetupStep(StepId.ANKIDROID, "AnkiDroid", StepStatus.OK, "AnkiDroid is installed.")
        } else {
            SetupStep(StepId.ANKIDROID, "AnkiDroid", StepStatus.FAIL, "AnkiGo can't find AnkiDroid. Install it first.", listOf(GITHUB))
        }

        // 2. Which build?
        steps += when {
            !f.ankiDroidInstalled -> SetupStep(StepId.BUILD, "AnkiDroid version", StepStatus.UNKNOWN, "Install AnkiDroid to check this.")
            f.flavour == AnkiDroidFlavour.FULL ->
                SetupStep(StepId.BUILD, "AnkiDroid version", StepStatus.OK, "Your AnkiDroid is the \"full\" version (GitHub or F-Droid). It can keep pictures in a folder AnkiGo can read.")
            f.flavour == AnkiDroidFlavour.PLAY && altOk -> SetupStep(
                StepId.BUILD, "AnkiDroid version", if (f.pickedFolder) StepStatus.OK else StepStatus.WARN,
                "Your AnkiDroid is the Play Store version. It keeps its files private, but AnkiGo reads pictures from $altText, and that works." +
                    if (f.pickedFolder) "" else " New pictures from AnkiDroid won't show unless you import them again.",
            )
            f.flavour == AnkiDroidFlavour.PLAY -> SetupStep(
                StepId.BUILD, "AnkiDroid version", StepStatus.FAIL,
                "Your AnkiDroid is the Play Store version. It keeps its files private, so no other app can read its pictures. " +
                    "Switch to the GitHub \"full\" version, or import a deck file with pictures into AnkiGo.",
                listOf(GITHUB, help(HelpContent.PICTURES_DONT_SHOW), APKG),
            )
            else -> SetupStep(StepId.BUILD, "AnkiDroid version", StepStatus.UNKNOWN, "AnkiGo couldn't tell which AnkiDroid version you have.", listOf(help(HelpContent.PICTURES_DONT_SHOW)))
        }

        // 3. Where is the collection?
        steps += when {
            folderOk -> SetupStep(StepId.FOLDER, "AnkiDroid's folder", StepStatus.OK, "AnkiDroid's pictures are in /storage/emulated/0/AnkiDroid and AnkiGo can open that folder.")
            altOk -> SetupStep(
                StepId.FOLDER, "AnkiDroid's folder", if (f.pickedFolder) StepStatus.OK else StepStatus.WARN,
                if (f.pickedFolder) "AnkiGo reads AnkiDroid's pictures from the folder you picked, so it doesn't need to open AnkiDroid's own folder."
                else "AnkiGo can't open AnkiDroid's own folder, but it reads the pictures you imported. New pictures from AnkiDroid won't show unless you import them again.",
            )
            f.publicFolder == PublicFolder.BLOCKED -> SetupStep(
                StepId.FOLDER, "AnkiDroid's folder", StepStatus.FAIL,
                "AnkiDroid's folder is in /storage/emulated/0/AnkiDroid, but AnkiGo is not allowed to open it yet.", listOf(ALL_FILES),
            )
            !f.ankiDroidInstalled -> SetupStep(StepId.FOLDER, "AnkiDroid's folder", StepStatus.UNKNOWN, "Install AnkiDroid to check this.")
            f.flavour == AnkiDroidFlavour.PLAY -> SetupStep(
                StepId.FOLDER, "AnkiDroid's folder", StepStatus.FAIL,
                "AnkiDroid keeps its collection in Android/data, which no other app can read.",
                listOf(help(HelpContent.PICTURES_DONT_SHOW), APKG),
            )
            f.flavour == AnkiDroidFlavour.FULL && f.allFilesAccess -> SetupStep(
                StepId.FOLDER, "AnkiDroid's folder", StepStatus.FAIL,
                "AnkiGo has All files access but finds no AnkiDroid folder. An existing AnkiDroid doesn't move to /storage/emulated/0/AnkiDroid by itself: " +
                    "its collection is probably still in Android/data.",
                listOf(Fix(FixAction.OPEN_ANKIDROID_SETTINGS), help(HelpContent.MOVE_COLLECTION), APKG),
            )
            f.flavour == AnkiDroidFlavour.FULL -> SetupStep(
                StepId.FOLDER, "AnkiDroid's folder", StepStatus.UNKNOWN,
                "AnkiGo can't look for AnkiDroid's folder until it has All files access.", listOf(ALL_FILES),
            )
            else -> SetupStep(StepId.FOLDER, "AnkiDroid's folder", StepStatus.UNKNOWN, "AnkiGo couldn't find AnkiDroid's folder.", listOf(help(HelpContent.MOVE_COLLECTION)))
        }

        // 4. All files access (Android 11+)
        steps += when {
            f.androidSdk < 30 -> SetupStep(StepId.ALL_FILES, "All files access", StepStatus.OK, "Not needed on this Android version. The normal storage permission is enough.")
            f.allFilesAccess -> SetupStep(StepId.ALL_FILES, "All files access", StepStatus.OK, "AnkiGo has All files access.")
            f.pickedFolder && altOk -> SetupStep(
                StepId.ALL_FILES, "All files access", StepStatus.OK,
                "AnkiGo has no All files access, but it reads the pictures from the folder you picked, so it doesn't need it.",
            )
            f.ownSource -> SetupStep(
                StepId.ALL_FILES, "All files access", StepStatus.WARN,
                "AnkiGo has no All files access, but it has its own copy of the pictures. New pictures from AnkiDroid won't show.", listOf(ALL_FILES),
            )
            else -> SetupStep(
                StepId.ALL_FILES, "All files access", StepStatus.FAIL,
                "AnkiGo needs \"All files access\" to read AnkiDroid's pictures.", listOf(ALL_FILES),
            )
        }

        // 5. Can a sample picture be opened?  6. Are many missing?
        val canOpen = f.sampleReadable > 0
        // AnkiDroid's folder can be listed: then "nothing readable" means the pictures aren't there (not downloaded), not a permission problem
        val sourceAccessible = folderOk
        steps += when {
            !f.apiAllowed -> SetupStep(StepId.SAMPLE, "A sample picture", StepStatus.UNKNOWN, "AnkiGo may not read AnkiDroid's cards yet, so it can't pick a picture to test.")
            f.sampleTotal == 0 -> SetupStep(StepId.SAMPLE, "A sample picture", StepStatus.UNKNOWN, "The next cards have no pictures, so there is nothing to test yet.")
            canOpen -> SetupStep(StepId.SAMPLE, "A sample picture", StepStatus.OK, "AnkiGo opened ${f.sampleReadable} of the ${f.sampleTotal} pictures on your next cards.")
            else -> SetupStep(
                StepId.SAMPLE, "A sample picture", StepStatus.FAIL,
                "AnkiGo could not open any of the ${f.sampleTotal} pictures on your next cards.",
                buildList {
                    if (f.androidSdk >= 30 && !f.allFilesAccess) add(ALL_FILES)
                    add(help(HelpContent.PICTURES_DONT_SHOW))
                    add(APKG)
                },
            )
        }
        steps += when {
            !f.apiAllowed || f.sampleTotal == 0 -> SetupStep(StepId.MISSING, "Missing pictures", StepStatus.UNKNOWN, "Needs cards with pictures to check.")
            !canOpen && !sourceAccessible -> SetupStep(StepId.MISSING, "Missing pictures", StepStatus.UNKNOWN, "Checked after AnkiGo can open AnkiDroid's pictures.")
            else -> {
                val missing = f.sampleTotal - f.sampleReadable
                when {
                    missing == 0 -> SetupStep(StepId.MISSING, "Missing pictures", StepStatus.OK, "All ${f.sampleTotal} pictures on your next cards are in the folder.")
                    else -> SetupStep(
                        StepId.MISSING, "Missing pictures",
                        if (manyMissing(missing, f.sampleTotal)) StepStatus.FAIL else StepStatus.WARN,
                        "AnkiDroid hasn't downloaded $missing ${if (missing == 1) "picture" else "pictures"} (of ${f.sampleTotal} on your next cards). " +
                            "In AnkiDroid raise the Network timeout to 600 seconds, force a full sync and choose Download, " +
                            "and keep AnkiDroid open until the media finishes.",
                        listOf(Fix(FixAction.OPEN_ANKIDROID), help(HelpContent.PICTURES_MISSING)),
                    )
                }
            }
        }
        return steps
    }

    /** The worst status of [steps] (FAIL > WARN > UNKNOWN > OK); the Set up pictures header colour. */
    fun overall(steps: List<SetupStep>): StepStatus = listOf(StepStatus.FAIL, StepStatus.WARN, StepStatus.UNKNOWN)
        .firstOrNull { s -> steps.any { it.status == s } } ?: StepStatus.OK

    /** Short lines for the Diagnostics screen and report: AnkiDroid build, collection folder, what AnkiGo can read. */
    fun diagnosticLines(f: PictureFacts): List<String> {
        val flavour = when {
            !f.ankiDroidInstalled -> "AnkiDroid build: not installed"
            f.flavour == AnkiDroidFlavour.FULL -> "AnkiDroid build: full (asks for All files access)"
            f.flavour == AnkiDroidFlavour.PLAY -> "AnkiDroid build: Play Store (files are private)"
            else -> "AnkiDroid build: unknown"
        }
        val folder = "Collection folder: " + when (f.publicFolder) {
            PublicFolder.READABLE -> "/storage/emulated/0/AnkiDroid (readable)"
            PublicFolder.BLOCKED -> "/storage/emulated/0/AnkiDroid (AnkiGo may not open it)"
            PublicFolder.NOT_FOUND -> if (f.flavour == AnkiDroidFlavour.PLAY) "Android/data (private)" else "not found (probably Android/data)"
        }
        val sample = when {
            !f.apiAllowed -> "not checked (no AnkiDroid permission)"
            f.sampleTotal == 0 -> "no pictures on the next cards"
            else -> "${f.sampleReadable}/${f.sampleTotal} readable, ${f.sampleTotal - f.sampleReadable} missing"
        }
        val read = "AnkiGo can read: All files access=${if (f.androidSdk < 30) "not needed" else if (f.allFilesAccess) "yes" else "no"}, " +
            "media folder=${if (f.publicFolder == PublicFolder.READABLE) "found" else "no"}, sample pictures=$sample"
        return listOf(flavour, folder, read)
    }
}
