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
    WIDGETS("Widgets"),
    REMINDERS("Reminders"),
    GAME("Game"),
    DATA("Data & import"),
    UPDATES("Updates"),
    DIAGNOSTICS("Diagnostics"),
    ABOUT("About"),
}

/**
 * One setting: [title] is the row's title as shown today, [explain] a short explanation that stands alone (no final period) for
 * under it, [sub] the group inside the section ("" = none). Pure data, no Android.
 */
data class SettingInfo(val id: String, val section: CatalogSection, val sub: String, val title: String, val explain: String)

object SettingsCatalog {
    const val MAX_EXPLAIN = 140

    private fun s(id: String, section: CatalogSection, sub: String, title: String, explain: String) =
        SettingInfo(id, section, sub, title, explain)

    private val L = CatalogSection.LOOK
    private val SO = CatalogSection.SOUNDS
    private val B = CatalogSection.BATTLE
    private val ST = CatalogSection.STUDY
    private val W = CatalogSection.WIDGETS
    private val R = CatalogSection.REMINDERS
    private val G = CatalogSection.GAME
    private val D = CatalogSection.DATA
    private val U = CatalogSection.UPDATES
    private val DI = CatalogSection.DIAGNOSTICS
    private val A = CatalogSection.ABOUT

    val all: List<SettingInfo> = listOf(
        s("tabBarStyle", L, "Theme & colours", "Bottom tab bar", "Choose a bottom tab bar with a bold name, sliding box, top line or selected name only"),
        // Look
        s("achievementPopup", L, "Types & notifications", "Achievement pop-ups", "Choose a pop-up, a small note or no message when you earn an achievement"),
        s("theme", L, "Theme & colours", "Theme", "Use a light or dark app, or follow your phone's theme"),
        s("accent", L, "Theme & colours", "Accent", "Choose the colour of app buttons, ticks and highlights"),
        s("barStyle", L, "Theme & colours", "Progress bars", "Colour progress bars by purpose, with your accent or with a gradient"),
        s("typeStyle", L, "Types & notifications", "Show types as", "Show Pokémon types as names, icons or both"),
        s("matchupChips", L, "Types & notifications", "Matchup chips", "Show matchup type chips with a soft fill or a coloured outline"),
        s("scale", L, "Text & size", "Interface size", "Resize app text, cards and Pokémon together"),
        s("listSpriteSize", L, "Box", "Pokémon size in lists", "Resize Pokémon pictures in the Pokédex and Box"),   // PLAN 215

        s("appText", L, "Text & size", "App text", "Resize menu and app text without changing card or battle text"),
        s("battleText", L, "Text & size", "Battle text", "Resize battle info boxes, move labels and arena messages"),
        s("deckListLook", L, "Study cards & decks", "Deck list", "Show decks as a plain list, connected branches or one card per deck family"),
        // PLAN 282 / R19
        s("boxStackedCards", L, "Box", "Stacked cards", "Show cards peeking behind groups of duplicate Pokémon in the Box"),
        s("boxTwoPokemon", L, "Box", "Show two Pokémon", "Show two Pokémon pictures for duplicate groups in the Box, keeping the copy count"),

        s("showAnswerColor", L, "Study cards & decks", "Show answer button colour", "Use the app accent or AnkiDroid dark blue for the button that reveals a card answer"),

        // Sounds
        s("answerSounds", SO, "UI sounds", "Answer buttons", "Play a click when you rate a card Again, Hard, Good or Easy"),
        s("showAnswerSound", SO, "UI sounds", "Show answer", "Play a click when you reveal a card's answer"),
        s("menuSounds", SO, "UI sounds", "Menus & tabs", "Play a click when you use tabs, menus or the Poké Ball button"),
        s("shopSounds", SO, "UI sounds", "Shop", "Play a click when you buy something in the Shop"),
        s("volumeButtons", SO, "UI sounds", "UI sounds volume", "Set the volume of button and menu clicks"),
        s("battleSounds", SO, "Battle", "Battle effects", "Play effects for hits, misses, catches, fainting and level-ups in battles"),
        s("typeSounds", SO, "Battle", "Move type sounds", "Play a sound for the attacking move's type alongside its hit sound"),
        s("momentSounds", SO, "Battle", "Status & moments", "Play effects for status conditions, shinies, evolution, XP, coins and goals"),
        s("volumeBattle", SO, "Battle", "Battle effects volume", "Set the volume of battle sound effects"),
        s("cries", SO, "Pokémon cries", "Pokémon cries", "Play a Pokémon's cry when it appears; each cry downloads once, about 7 KB"),
        s("volumeCries", SO, "Pokémon cries", "Cries volume", "Set the volume of Pokémon cries"),
        s("quietOnCardAudio", SO, "", "Quiet on cards with sound", "Pause game sounds while a study card plays its own audio"),
        s("testSound", SO, "", "Play a test sound", "Play a sample to check sound volume; respects your phone's silent mode"),

        // Battle
        s("leaveMode", B, "When you leave AnkiGo", "Nothing", "Show no battle when you leave AnkiGo; you can still use Pop out manually"),
        s("leaveModePip", B, "When you leave AnkiGo", "Picture-in-picture", "Show the battle in Android's small picture-in-picture window when you leave AnkiGo"),
        s("leaveModeFloat", B, "When you leave AnkiGo", "Floating battle window", "Show a movable battle above other apps when you leave AnkiGo; needs overlay permission"),
        s("popOutFloat", B, "Pop out opens", "Floating window", "Make Pop out open an interactive battle above other apps; needs overlay permission"),
        s("popOutPip", B, "Pop out opens", "Picture-in-picture window", "Make Pop out open Android's small battle window, which you can watch but not control"),
        s("floatScreens", B, "Floating screens", "Floating screens in split screen", "Open Team, Pokédex, Shop and Settings in small windows above AnkiDroid in split screen"),
        s("overlay", B, "Floating screens", "Display over other apps", "Allow AnkiGo to display floating windows above other apps"),
        s("pokemonSize", B, "Battle panel", "Pokémon size", "Resize both battling Pokémon from 80 to 130 percent without covering their info boxes"),
        s("pokemonRarityMark", B, "Battle panel", "Pokémon rarity marks", "Show achievement tier gems beside Pokémon names in battle info boxes and details; on by default"),
        s("cardsUnderOwn", B, "Battle panel", "Stat card under its own Pokémon", "Put each stat card below its own Pokémon when background pictures are off"),
        s("xpAnimation", B, "Battle panel", "XP animation", "Show sparks flying into your Pokémon when it gains experience points"),
        s("flyingTurnDots", B, "Battle panel", "Flying turn dots", "Animate the turn dots moving into the Pokémon about to attack"),
        s("attackAnimations", B, "Battle panel", "Attack animations", "Play an animation for each move; off uses a simple step and shake"),
        s("pokemonMovement", B, "Battle panel", "Pokémon movement", "Choose how often Pokémon move while idle in battle; reduced motion stops this"),   // R18-C, PLAN 247
        s("backgroundPictures", B, "Battle panel", "Background pictures", "Show a scene from your enabled backgrounds behind the battle; off uses a plain arena"),
        s("turnDots", B, "Battle panel", "Turn dots", "Choose where the battle charge dots appear below the arena"),
        s("infoBoxAbove", B, "Info box style", "Number above the bar", "Show HP numbers above full-width bars, with type names beside them"),
        s("infoBoxInside", B, "Info box style", "Number inside the bar", "Write HP numbers inside thicker bars to make battle info boxes shorter"),
        s("infoBoxLookToday", B, "Info box style", "Card", "Use the app's normal card colour for battle info boxes"),
        s("infoBoxLookGlass", B, "Info box style", "Glass", "Show the blurred battle background through tinted info boxes with bright edges"),
        s("infoBoxLookFrame", B, "Info box style", "Thin frame", "Show battle info on a faint dark fill with a thin white frame and white text"),
        s("infoBoxLookPlate", B, "Info box style", "Dark plate", "Show battle info on deep navy plates with gold trim and light text"),
        s("infoBoxLookHud", B, "Info box style", "HUD in the corners", "Show outlined battle text and bars in opposite corners without boxes"),
        s("infoBoxLookType", B, "Info box style", "Type colour", "Tint each battle info box with the Pokémon's first type colour"),
        s("wideInfoBoxes", B, "Info box style", "Wider info boxes", "Widen battle info boxes to give type names more room"),
        s("bigInfoBoxes", B, "Info box style", "Bigger info boxes", "Enlarge battle info boxes when there is enough space in the arena"),   // R19-A, PLAN 276
        s("hpColours", B, "HP / XP bars", "HP bar colours", "Colour HP bars with three steps, a smooth blend or the Pokémon's type colour"), // PLAN 163 / R19
        s("hpBarsToday", B, "HP / XP bars", "Today", "Show HP and XP as flat rounded bars on grey tracks"),
        s("hpBarsPixel", B, "HP / XP bars", "Pixel", "Show HP and XP bars with pixel edges and fill them in five-percent steps"),
        s("frameNone", B, "Battle frame", "No frame", "Let the battle background reach the edge without a frame"),
        s("frameSimple", B, "Battle frame", "Simple pixel", "Draw a thin pixel frame around the battle background"),
        s("frameAutomatic", B, "Battle frame", "Automatic: matches the background", "Draw a plain pixel rim coloured for the displayed scenery; dark mode uses a darker palette"),
        s("frameSea", B, "Battle frame", "Waves", "Draw a sea-and-beach pixel frame around the battle background"),
        s("frameWood", B, "Battle frame", "Wood", "Draw a brown plank frame with brass corners around the battle background"),
        s("frameStone", B, "Battle frame", "Stone", "Draw a grey brick frame around the battle background"),
        s("frameGold", B, "Battle frame", "Gold", "Draw a gold pixel frame with rubies around the battle background"),
        s("frameMetal", B, "Battle frame", "Dark metal", "Draw a dark steel pixel frame with rivets around the battle background"),
        s("statsBarSmall", B, "Small battle windows", "Show stats bar in small windows", "Show battle statistics in floating and picture-in-picture windows; off leaves more arena space"),
        s("compactCards", B, "Small battle windows", "Compact stat cards", "Show only the Pokémon's name and HP on battle stat cards"),

        // Study
        s("compactView", ST, "Study & accessibility", "Compact view", "Make more room for cards by hiding battle counters, daily goal and move labels"),
        s("explainTemplate", ST, "Study & accessibility", "Google browser prompt", "Edit the Google search prompt used by Explain this card, which opens your browser"),
        s("inAnkiDroid", ST, "Study & accessibility", "Tapping a deck opens AnkiDroid", "Open decks in AnkiDroid when tapped; off lets you study inside AnkiGo"),
        s("reduceMotion", ST, "Study & accessibility", "Remove battle animations", "Remove moving battle effects; Android's Remove animations setting also applies"),
        s("tapShow", ST, "Answer controls", "Tap the card to show the answer", "Reveal a card's answer by tapping the card; off requires a button, key or swipe"),
        s("tapHide", ST, "Answer controls", "Tap again to hide the answer", "Tap a revealed answer to return to the question; requires tap-to-show to be on"),
        s("enterShows", ST, "Answer controls", "Enter = Show answer", "Reveal the answer when you press Enter while typing a card answer"),
        s("haptic", ST, "Answer controls", "Vibrate on answer", "Vibrate briefly when you rate a study card"),
        s("newCardOk", ST, "Answer controls", "New cards: one OK button", "Use OK instead of Again and Hard on a new card’s first showing; OK brings the card back soon"),   // PLAN 155
        s("swipe", ST, "Answer controls", "Swipe to answer", "Rate a revealed answer by swiping left for Again, down for Hard, right for Good or up for Easy"),
        s("speakBattle", ST, "Study & accessibility", "Read battle log with TalkBack", "Read battle messages with TalkBack; off lets card reading continue uninterrupted"),
        s("zoom", L, "Text & size", "Anki card text", "Resize text inside Anki study cards without changing the app's text size"),
        s("timer", ST, "Timing", "Answer timer", "Show a countdown on study cards, hide it or follow each deck's timer setting"),
        s("autoAdvance", ST, "Timing", "Auto-advance", "Reveal answers and run the deck's answer action automatically using deck times or the times below"),
        s("autoQuestionSecs", ST, "Timing", "Show the answer after", "Set seconds before auto-advance reveals the answer when the deck has no timing"),
        s("autoAnswerSecs", ST, "Timing", "Then act after", "Set seconds before auto-advance runs the deck's answer action when the deck has no timing"),
        s("missingMedia", ST, "Card pictures & sound", "Cards with missing media", "Choose whether to ask, open AnkiDroid or answer anyway when card media is missing"),
        s("pictureSetup", ST, "Card pictures & sound", "Set up pictures", "Check access to AnkiDroid's card pictures and see how to fix missing media"),
        s("mediaApkg", ST, "Card pictures & sound", "Import a media copy (.apkg)", "Copy card pictures and sounds from a legacy-format Anki deck export that includes media"),
        s("mediaFolder", ST, "Card pictures & sound", "Pick AnkiDroid media folder", "Give AnkiGo access to card media by choosing AnkiDroid's collection.media folder"),
        s("saveNotFirst", ST, "Advanced", "Save answers for cards that aren't first (test)", "Allow saving answers when AnkiDroid lists another card first; disable if answers save incorrectly"),

        // Widgets
        s("openOn", W, "", "Open AnkiGo on", "Choose the screen opened by the app icon and Buddy widget"),

        // Reminders (ids match ReminderPrefs fields, PLAN 85)
        s("dueOn", R, "Cards due", "Cards due reminder", "Send a reminder when enough study cards are due"),
        s("dueMin", R, "Cards due", "Remind from this many cards", "Only send a cards-due reminder when at least this many cards are waiting"),
        s("dueMode", R, "Cards due", "When to remind", "Send cards-due reminders at a fixed time or let AnkiGo choose a suitable moment"),
        s("dueTime", R, "Cards due", "Reminder time", "Choose the time of day for fixed cards-due reminders"),
        s("dueEveryHours", R, "Cards due", "Check every", "Choose how often to check for cards still due and send another reminder"),
        s("minGapHours", R, "Cards due", "Minimum gap between reminders", "Wait at least this many hours between cards-due reminders"),
        s("dailyCap", R, "Cards due", "Reminders per day", "Limit how many cards-due reminders can be sent each day"),
        s("decks", R, "Cards due", "Which decks count", "Count due cards from all decks or only the decks you select"),
        s("goalOn", R, "Own goal", "Reminder goal", "Keep reminding you to study until your reminder goal is reached today"),
        s("goalKind", R, "Own goal", "Goal counts", "Count completed reviews or newly started cards toward your reminder goal"),
        s("goalCount", R, "Own goal", "Goal amount", "Set how many reviews or new cards complete today's reminder goal"),
        s("streakOn", R, "Game", "Streak about to end", "Remind you to study before your daily study streak ends"),
        s("streakTime", R, "Game", "Streak reminder time", "Choose when to send the reminder that your study streak is about to end"),
        s("dailyGoalOn", R, "Game", "Daily goal not reached", "Send an evening reminder if you have not reached your daily game goal"),
        s("dailyGoalTime", R, "Game", "Daily goal reminder time", "Choose when to remind you about an unfinished daily game goal"),
        s("shopOn", R, "Game", "Shop news", "Send a reminder when something worth having appears in the Shop"),
        s("quietOn", R, "Quiet hours", "Quiet hours", "Silence all reminders during the hours you choose"),
        s("quietFrom", R, "Quiet hours", "Quiet from", "Choose when the daily period without reminders begins"),
        s("quietTo", R, "Quiet hours", "Quiet until", "Choose when the daily period without reminders ends"),

        // Game
        s("trainerName", G, "Pokémon", "Trainer name", "Set the name used for you in the game"),
        s("fasterStart", G, "Difficulty", "Faster start", "Shorten wild battles while your lead Pokémon is below level 15; applies from the next encounter"),
        s("gens", G, "Pokémon", "Generations", "Choose which Pokémon generations can appear; generation 9 uses still pictures"),
        s("releaseDuplicates", G, "Pokémon", "Release duplicates", "Release extra copies, keeping the best of each species. Favourites, shinies, team, held items and nicknames protect copies; asks first"),
        s("dailyAverage", G, "Difficulty", "Daily goal", "Set daily review goals for all days or per weekday; progress toward today’s goal improves rare encounter chances"),
        s("dayStartHour", G, "Difficulty", "New day starts at", "Count answers before this hour toward the previous day's progress"),
s("catchAlways", G, "When you beat a wild Pokémon", "Always catch", "Catch every wild Pokémon you defeat"),
s("catchNew", G, "When you beat a wild Pokémon", "Catch only new species", "Catch species you have never caught; defeat others for the same XP and coins, except shinies, protected kinds and wishlist entries"),
s("catchBetter", G, "When you beat a wild Pokémon", "Catch new species and better copies", "Catch new species and replace owned copies when the wild Pokémon has a higher level"),
s("catchDefeat", G, "When you beat a wild Pokémon", "Defeat", "Defeat wild Pokémon instead of catching them, except shinies and protected kinds"),
s("catchAsk", G, "When you beat a wild Pokémon", "Ask me", "Catch new species; ask about better copies of owned species, comparing level or total IVs"),
        s("protectLegendary", G, "Always catch", "Legendary", "Always catch legendary Pokémon, even when your catch rule would defeat them"),
        s("protectMythical", G, "Always catch", "Mythical", "Always catch mythical Pokémon, even when your catch rule would defeat them"),
        s("protectUltra", G, "Always catch", "Ultra Beast", "Always catch Ultra Beasts, even when your catch rule would defeat them"),
        s("protectStarter", G, "Always catch", "Starter", "Always catch starter Pokémon, even when your catch rule would defeat them"),
        s("protectRegional", G, "Always catch", "Regional form", "Always catch regional forms, even when your catch rule would defeat them"),
        s("moveChoiceRandom", G, "Battle", "Move choice: Random", "Choose each battle move at random"),
        s("moveChoiceSmart", G, "Battle", "Move choice: Smart", "Prefer attacks and only use status moves that can still have an effect"),
        s("moveChoiceSmartest", G, "Battle", "Move choice: Smartest", "Use smart move choices and favour the strongest attack more often"),

        // Data & import
        s("gameDataSize", D, "Downloads", "Game data", "See how much phone storage the downloaded game data uses"),
        s("picturesSize", D, "Downloads", "Pictures", "See how much phone storage downloaded Pokémon pictures use"),
        s("spritesEnabled", D, "Downloads", "Download pictures as needed", "Download missing Pokémon pictures as needed; off keeps using pictures already saved"),
        s("lastSaved", D, "Save & backups", "Last saved", "See when your game progress was last saved on this phone"),
        s("exportSave", D, "Save & backups", "Export save", "Write your game progress to a file you can keep as a backup"),
        s("importSave", D, "Save & backups", "Import save…", "Load game progress from a save file, backing up your current game first"),
        s("restoreBackup", D, "Save & backups", "Restore a backup…", "Restore an earlier game save while keeping a backup of your current game"),
        s("ankimonImport", D, "Ankimon PC sync", "Import Ankimon save (.db)…", "Add Pokémon from your PC Ankimon save, backing up your current game first"),
        s("ankimonExport", D, "Ankimon PC sync", "Export for Ankimon PC…", "Write a game save that Ankimon on your PC can read"),

        // Updates
        s("checkUpdates", U, "", "Check for updates", "Check for app updates when AnkiGo opens or you return to it"),

        // Diagnostics
        s("diagnostics", DI, "", "Diagnostics", "Inspect phone access and collect a diagnostic log to help investigate problems"),

        // About
        s("ankidroidStatus", A, "", "AnkiDroid", "See the connected AnkiDroid app, its version and whether AnkiGo can access it"),
        s("about", A, "", "About", "See the AnkiGo version, credits and software licences"),
    )

    fun bySection(section: CatalogSection): List<SettingInfo> = all.filter { it.section == section }

    fun byId(id: String): SettingInfo? = all.firstOrNull { it.id == id }

    fun byTitle(title: String): SettingInfo? = all.firstOrNull { it.title == title }
}
