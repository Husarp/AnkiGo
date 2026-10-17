package com.husarp.ankigo.ui

/**
 * The proposed new Settings grouping (PLAN 87). The visual ticket maps this to the picked layout; [SettingsSection]
 * (the current screen) is unchanged.
 */
enum class CatalogSection(val title: String) {
    LOOK("Look"),
    SOUNDS("Sounds"),
    BATTLE("Battle"),
    STUDY("Study"),
    REMINDERS("Reminders"),
    GAME("Game"),
    DATA("Data & import"),
    UPDATES("Updates"),
    DIAGNOSTICS("Diagnostics"),
    ABOUT("About"),
}

/**
 * One setting: [title] is the row's title as shown today, [explain] one short plain line (no final period) for
 * under it, [sub] the group inside the section ("" = none). Pure data, no Android.
 */
data class SettingInfo(val id: String, val section: CatalogSection, val sub: String, val title: String, val explain: String)

object SettingsCatalog {
    const val MAX_EXPLAIN = 90

    private fun s(id: String, section: CatalogSection, sub: String, title: String, explain: String) =
        SettingInfo(id, section, sub, title, explain)

    private val L = CatalogSection.LOOK
    private val SO = CatalogSection.SOUNDS
    private val B = CatalogSection.BATTLE
    private val ST = CatalogSection.STUDY
    private val R = CatalogSection.REMINDERS
    private val G = CatalogSection.GAME
    private val D = CatalogSection.DATA
    private val U = CatalogSection.UPDATES
    private val DI = CatalogSection.DIAGNOSTICS
    private val A = CatalogSection.ABOUT

    val all: List<SettingInfo> = listOf(
        // Look
        s("theme", L, "", "Theme", "Light, dark, or follow the phone"),
        s("accent", L, "", "Accent", "The colour used for buttons, ticks and highlights"),
        s("barStyle", L, "", "Progress bars", "XP blue, goals orange, Pokédex teal; or all accent, or a gradient"),
        s("typeStyle", L, "", "Show types as", "Type names, small type icons, or both"),
        s("scale", L, "", "Interface size", "Text, cards and Pokémon get bigger or smaller together"),

        // Sounds
        s("battleSounds", SO, "", "Battle sounds", "Hit, faint and catch sounds during battles"),
        s("cries", SO, "", "Pokémon cries", "Plays a cry when a Pokémon appears; each one downloads once, ~7 KB"),
        s("volume", SO, "", "Volume", "How loud the game sounds are"),
        s("quietOnCardAudio", SO, "", "Quiet on cards with sound", "Game sounds pause while a card plays its own audio"),
        s("testSound", SO, "", "Play a test sound", "Checks the volume; follows the phone's silent mode"),

        // Battle
        s("leaveMode", B, "When you leave AnkiGo", "Nothing", "Leaving AnkiGo shows no battle; Pop out still works any time"),
        s("leaveModePip", B, "When you leave AnkiGo", "Picture-in-picture", "A small battle window when you switch to another app"),
        s("leaveModeFloat", B, "When you leave AnkiGo", "Floating battle window", "A movable battle window over AnkiDroid; needs the overlay permission"),
        s("popOutFloat", B, "Pop out opens", "Floating window", "Stays on top and you can tap it; needs “Display over other apps”"),
        s("popOutPip", B, "Pop out opens", "Picture-in-picture window", "Android's own small window; view only"),
        s("floatScreens", B, "Floating screens", "Floating screens in split screen", "Team, Pokédex, Shop and Settings open small over AnkiDroid"),
        s("overlay", B, "Floating screens", "Display over other apps", "Android permission the floating windows need"),
        s("cardsUnderOwn", B, "Battle panel", "Stat card under its own Pokémon", "Off: the classic layout, each card on the opposite side"),
        s("playerSameSize", B, "Battle panel", "Same size as the wild one", "Your Pokémon is not drawn bigger than the wild one"),
        s("playerFront", B, "Battle panel", "Show my Pokémon from the front", "Front picture instead of the back picture"),
        s("statsBarSmall", B, "Small battle windows", "Show stats bar in small windows", "Off gives the battle more room in small windows"),
        s("compactCards", B, "Small battle windows", "Compact stat cards", "Only name and HP on the battle cards"),

        // Study
        s("inAnkiDroid", ST, "", "Tapping a deck opens AnkiDroid", "Off: you study inside AnkiGo"),
        s("reduceMotion", ST, "", "Remove battle animations", "Fewer moving effects; also follows Android's Remove animations"),
        s("tapShow", ST, "", "Tap the card to show the answer", "Off: use the Show answer button, a key or a swipe"),
        s("tapHide", ST, "", "Tap again to hide the answer", "Tap the answer to read the question again"),
        s("enterShows", ST, "", "Enter = Show answer", "Pressing Enter in a type-in answer shows the answer"),
        s("haptic", ST, "", "Vibrate on answer", "A short buzz when you answer a card"),
        s("swipe", ST, "", "Swipe to answer", "On the answer side: left Again, down Hard, right Good, up Easy"),
        s("speakBattle", ST, "", "Read battle log with TalkBack", "Off keeps the card reading uninterrupted"),
        s("zoom", ST, "", "Card text size", "Makes the card text bigger or smaller"),
        s("timer", ST, "", "Answer timer", "Shows a countdown on the card, or follows the deck"),
        s("autoAdvance", ST, "", "Auto-advance", "Moves on by itself using the deck's seconds, or the times below"),
        s("autoQuestionSecs", ST, "", "Show the answer after", "Seconds before the answer shows by itself"),
        s("autoAnswerSecs", ST, "", "Then act after", "Seconds before the deck's answer action runs"),
        s("missingMedia", ST, "", "Cards with missing media", "Ask, skip or show cards whose pictures or sounds are missing"),
        s("pictureSetup", ST, "", "Set up pictures", "Checks AnkiDroid's pictures and shows what to fix"),
        s("mediaApkg", ST, "", "Import a media copy (.apkg)", "Optional; use a legacy-format export with media"),
        s("mediaFolder", ST, "", "Pick AnkiDroid media folder", "Choose collection.media in the public AnkiDroid folder"),

        // Reminders (ids match ReminderPrefs fields, PLAN 85)
        s("dueOn", R, "Cards due", "Cards due reminder", "Remind me when enough cards are waiting"),
        s("dueMin", R, "Cards due", "Remind from this many cards", "Only remind when at least this many cards are due"),
        s("dueMode", R, "Cards due", "When to remind", "At a fixed time, or let AnkiGo pick a good moment"),
        s("dueTime", R, "Cards due", "Reminder time", "The time of day for the fixed reminder"),
        s("dueEveryHours", R, "Cards due", "Check every", "Check every few hours and remind if cards are still left"),
        s("minGapHours", R, "Cards due", "Minimum gap between reminders", "So it never nags every few minutes"),
        s("dailyCap", R, "Cards due", "Reminders per day", "At most this many reminders a day"),
        s("decks", R, "Cards due", "Which decks count", "All decks, or only the ones you choose"),
        s("goalOn", R, "Own goal", "Reminder goal", "Keep reminding until today's goal is done"),
        s("goalKind", R, "Own goal", "Goal counts", "Reviews done, or new cards started"),
        s("goalCount", R, "Own goal", "Goal amount", "How many reviews or new cards to reach today"),
        s("streakOn", R, "Game", "Streak about to end", "A reminder to study today so your streak keeps going"),
        s("streakTime", R, "Game", "Streak reminder time", "When to send the streak reminder"),
        s("dailyGoalOn", R, "Game", "Daily goal not reached", "An evening reminder if today's goal isn't done yet"),
        s("dailyGoalTime", R, "Game", "Daily goal reminder time", "When to send the daily goal reminder"),
        s("shopOn", R, "Game", "Shop news", "Tells you when something worth having shows up in the Shop"),
        s("quietOn", R, "Quiet hours", "Quiet hours", "No reminders during the hours you choose"),
        s("quietFrom", R, "Quiet hours", "Quiet from", "When quiet hours start"),
        s("quietTo", R, "Quiet hours", "Quiet until", "When quiet hours end"),

        // Game
        s("trainerName", G, "Pokémon", "Trainer name", "The name shown for you in the game"),
        s("fasterStart", G, "Difficulty", "Faster start", "Shorter wild battles while your lead is below Lv 15"),
        s("gens", G, "Pokémon", "Generations", "Which Pokémon generations can appear; gen 9 uses still pictures"),
        s("cardsPerRound", G, "Battle", "Answers per attack", "How many answers make one attack; Ankimon uses 2"),
        s("dailyAverage", G, "Difficulty", "Daily goal", "Reviews a day; a bigger goal also makes rare Pokémon more likely"),
        s("dayStartHour", G, "Difficulty", "New day starts at", "Answers before this hour count for the day before"),
        s("xpShare", G, "Pokémon", "Exp. Share holder", "Who gets shared XP; change it in Team"),
        s("wishlist", G, "Pokémon", "Wishlist", "Always caught by the auto rule; tap the star in the Pokédex to edit"),
        s("autoMode1", G, "Battle", "Always catch", "Every wild Pokémon that faints is caught"),
        s("autoMode2", G, "Battle", "Defeat (catch shinies and protected kinds)", "Others are defeated for money instead of caught"),
        s("autoMode3", G, "Battle", "Catch species you don't have yet", "Default; new species are caught, the rest are defeated"),
        s("protectLegendary", G, "Always catch", "Legendary", "Always caught, even with Defeat"),
        s("protectMythical", G, "Always catch", "Mythical", "Always caught, even with Defeat"),
        s("protectUltra", G, "Always catch", "Ultra Beast", "Always caught, even with Defeat"),
        s("protectStarter", G, "Always catch", "Starter", "Always caught, even with Defeat"),
        s("protectRegional", G, "Always catch", "Regional form", "Always caught, even with Defeat"),
        s("autoReleaseDuplicates", G, "Battle", "Auto-release duplicates", "Copies you own are defeated unless higher level; then the old one is swapped out"),

        // Data & import
        s("gameDataSize", D, "Downloads", "Game data", "How much space the downloaded game data takes"),
        s("picturesSize", D, "Downloads", "Pictures", "How much space the downloaded pictures take"),
        s("spritesEnabled", D, "Downloads", "Download pictures as needed", "Off: simple placeholders instead of pictures"),
        s("lastSaved", D, "Save & backups", "Last saved", "When your game was last written to the phone"),
        s("exportSave", D, "Save & backups", "Export save", "Writes your game to a file you can keep"),
        s("importSave", D, "Save & backups", "Import save…", "Loads a saved file; your current game is kept as a backup first"),
        s("restoreBackup", D, "Save & backups", "Restore a backup…", "Goes back to an earlier copy; the current game is kept too"),
        s("ankimonImport", D, "Ankimon PC sync", "Import Ankimon save (.db)…", "Adds your PC Ankimon Pokémon here; a backup is kept first"),
        s("ankimonExport", D, "Ankimon PC sync", "Export for Ankimon PC…", "Writes a save your PC Ankimon can read"),

        // Updates
        s("checkUpdates", U, "", "Check for updates", "When the app opens or you come back to it"),

        // Diagnostics
        s("diagnostics", DI, "", "Diagnostics", "What AnkiGo can see on this phone, and a log to send if something breaks"),

        // About
        s("about", A, "", "About", "Version, credits and licences"),
    )

    fun bySection(section: CatalogSection): List<SettingInfo> = all.filter { it.section == section }

    fun byId(id: String): SettingInfo? = all.firstOrNull { it.id == id }

    fun byTitle(title: String): SettingInfo? = all.firstOrNull { it.title == title }
}
