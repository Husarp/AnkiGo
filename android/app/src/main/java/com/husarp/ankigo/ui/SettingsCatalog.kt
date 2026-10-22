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
        s("matchupChips", L, "", "Matchup chips", "Type chips in Matchups: soft fill, or a ring in the type colour"),
        s("scale", L, "", "Interface size", "Text, cards and Pokémon get bigger or smaller together"),

        // Sounds
        s("answerSounds", SO, "Study buttons", "Answer buttons", "A click for Again, Hard, Good and Easy"),
        s("showAnswerSound", SO, "Study buttons", "Show answer", "A click when you show the answer"),
        s("menuSounds", SO, "Study buttons", "Menus & tabs", "A click for the tabs, the menus and the Poké Ball button"),
        s("shopSounds", SO, "Study buttons", "Shop", "A click when you buy something"),
        s("volumeButtons", SO, "Study buttons", "Button volume", "How loud the button clicks are"),
        s("battleSounds", SO, "Battle", "Battle effects", "Hits, faint, catch, level up and misses during battles"),
        s("momentSounds", SO, "Battle", "Status & moments", "Burn, poison, shiny, evolution, XP, cash and goal sounds"),
        s("volumeBattle", SO, "Battle", "Battle volume", "How loud the battle sounds are"),
        s("cries", SO, "Pokémon cries", "Pokémon cries", "Plays a cry when a Pokémon appears; each one downloads once, ~7 KB"),
        s("volumeCries", SO, "Pokémon cries", "Cries volume", "How loud the Pokémon cries are"),
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
        s("pokemonSize", B, "Battle panel", "Pokémon size", "How big both Pokémon are in the battle (80–130 %); they stay clear of the info boxes"),
        s("cardsUnderOwn", B, "Battle panel", "Stat card under its own Pokémon", "Without a background picture. Off: the classic layout, each card on the opposite side"),
        s("xpAnimation", B, "Battle panel", "XP animation", "Sparks fly into your Pokémon when it gains XP"),
        s("attackAnimations", B, "Battle panel", "Attack animations", "Each move plays its own animation; off: a simple step and shake"),
        s("backgroundPictures", B, "Battle panel", "Background pictures", "A scene that fits the wild Pokémon's type, downloaded once (8–400 KB each); off: plain"),
        s("turnDots", B, "Battle panel", "Turn dots", "Where the charge dots sit in the bar under the battle picture"),
        s("infoBoxAbove", B, "Info box style", "Number above the bar", "Default; the HP number above a full-width bar, the type names beside it"),
        s("infoBoxInside", B, "Info box style", "Number inside the bar", "A thicker bar with the HP number written in it; the shortest boxes"),
        s("infoBoxLookToday", B, "Info box style", "Card", "Default; the card colour, as the info boxes always were"),
        s("infoBoxLookGlass", B, "Info box style", "Glass", "The picture behind the box, blurred, with a light tint and a thin bright edge"),
        s("infoBoxLookFrame", B, "Info box style", "Thin frame", "A faint dark veil, a thin white frame and white text, the same in light and dark"),
        s("infoBoxLookPlate", B, "Info box style", "Dark plate", "Deep navy with a gold trim and light text, the same in light and dark"),
        s("infoBoxLookHud", B, "Info box style", "HUD in the corners", "No box: outlined text and bars on the picture, yours and the wild one in opposite corners"),
        s("infoBoxLookType", B, "Info box style", "Type colour", "Washed with the Pokémon's first type colour, with a stripe on the left"),
        s("hpBarsToday", B, "HP / XP bars", "Today", "Default; flat rounded bars on a grey track"),
        s("hpBarsPixel", B, "HP / XP bars", "Pixel", "Hard pixel edges, a cut-corner outline, filled in 5 % steps"),
        s("frameNone", B, "Battle frame", "No frame", "Default; the battle picture reaches the edge"),
        s("frameSimple", B, "Battle frame", "Simple pixel", "A thin pixel frame around the battle picture, the quietest one"),
        s("frameWood", B, "Battle frame", "Wood", "A pixel frame of brown planks with brass corners around the battle picture"),
        s("frameStone", B, "Battle frame", "Stone", "A pixel frame of grey bricks around the battle picture"),
        s("frameGold", B, "Battle frame", "Gold", "A shiny gold pixel frame with rubies around the battle picture"),
        s("frameIce", B, "Battle frame", "Ice", "A pale blue pixel frame with icicles around the battle picture"),
        s("frameMetal", B, "Battle frame", "Dark metal", "A dark steel pixel frame with rivets around the battle picture"),
        s("statsBarSmall", B, "Small battle windows", "Show stats bar in small windows", "Off gives the battle more room in small windows"),
        s("compactCards", B, "Small battle windows", "Compact stat cards", "Only name and HP on the battle cards"),

        // Study
        s("inAnkiDroid", ST, "", "Tapping a deck opens AnkiDroid", "Off: you study inside AnkiGo"),
        s("reduceMotion", ST, "", "Remove battle animations", "Fewer moving effects; also follows Android's Remove animations"),
        s("tapShow", ST, "", "Tap the card to show the answer", "Off: use the Show answer button, a key or a swipe"),
        s("tapHide", ST, "", "Tap again to hide the answer", "Tap the answer to read the question again"),
        s("enterShows", ST, "", "Enter = Show answer", "Pressing Enter in a type-in answer shows the answer"),
        s("haptic", ST, "", "Vibrate on answer", "A short buzz when you answer a card"),
        s("newCardOk", ST, "", "New cards: one OK button", com.husarp.ankigo.study.NewCardOk.SETTING_NOTE),   // PLAN 155
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
        s("releaseDuplicates", G, "Pokémon", "Release duplicates", "Keeps the best copy of each species and releases the rest; favourites, shinies, team stay"),
        s("dailyAverage", G, "Difficulty", "Daily goal", "Reviews a day, the same or per weekday; a bigger goal makes rare Pokémon likelier"),
        s("dayStartHour", G, "Difficulty", "New day starts at", "Answers before this hour count for the day before"),
s("catchAlways", G, "When you beat a wild Pokémon", "Always catch", "Every wild Pokémon you beat is caught"),
s("catchNew", G, "When you beat a wild Pokémon", "Catch only new species", "Others are defeated: the same XP and ₽; default"),
s("catchBetter", G, "When you beat a wild Pokémon", "Catch new species and better copies", "A higher-level copy replaces your old one"),
s("catchDefeat", G, "When you beat a wild Pokémon", "Defeat", "Catch only shinies and protected kinds; the rest are defeated"),
        s("protectLegendary", G, "Always catch", "Legendary", "Always caught, even with Defeat"),
        s("protectMythical", G, "Always catch", "Mythical", "Always caught, even with Defeat"),
        s("protectUltra", G, "Always catch", "Ultra Beast", "Always caught, even with Defeat"),
        s("protectStarter", G, "Always catch", "Starter", "Always caught, even with Defeat"),
        s("protectRegional", G, "Always catch", "Regional form", "Always caught, even with Defeat"),
        s("moveChoiceRandom", G, "Battle", "Move choice: Random", "Each move by chance, as before"),
        s("moveChoiceSmart", G, "Battle", "Move choice: Smart", "Mostly attacks; status moves only when they still do something"),
        s("moveChoiceSmartest", G, "Battle", "Move choice: Smartest", "Smart, and picks the strongest attack more often"),

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
        s("ankidroidStatus", A, "", "AnkiDroid", "Which AnkiDroid AnkiGo uses, its version and whether it works"),
        s("about", A, "", "About", "Version, credits and licences"),
    )

    fun bySection(section: CatalogSection): List<SettingInfo> = all.filter { it.section == section }

    fun byId(id: String): SettingInfo? = all.firstOrNull { it.id == id }

    fun byTitle(title: String): SettingInfo? = all.firstOrNull { it.title == title }
}
