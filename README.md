# AnkiGo

A personal Pokémon companion for [AnkiDroid](https://github.com/ankidroid/Anki-Android). Run it in split
screen next to AnkiDroid: while you study there, AnkiGo follows your answers and turns them into a game.

## Current state

0.3.0: the game, plus three additions:

- **Import from Ankimon (PC).** In Anki, use Ankimon's menu *Game → Export Save File…*, copy the `.db` file to
  the phone, then *Settings → Import Ankimon save…* in AnkiGo. You see what will change before anything does
  ("adds 37 Pokémon, upgrades 4"). The import only adds: Pokémon you don't have yet are added, ones imported earlier
  are upgraded if the file has them further along, the Pokédex is combined, and for items and coins the higher number
  is kept, so importing the same file twice changes nothing. Nicknames and EVs come along; gens 1–8 (gen 9 when it is
  switched on). Your current game is backed up first and can be restored under *Restore a backup*.
- **Picture-in-picture.** When you leave AnkiGo, the battle shrinks into a small picture-in-picture window and keeps
  following your answers (buttons: Open, Pause/Resume). Settings → *When you leave AnkiGo*.
- **Floating windows** (optional, needs Android's "Display over other apps"): in split screen, Team, Pokédex, Shop and
  Settings open in a small window you can move and resize over AnkiDroid, so the battle stays visible. Instead of
  picture-in-picture you can also choose a floating battle window; it keeps running with a notification while AnkiGo
  isn't in front.

The game itself: every answer you give in AnkiDroid becomes part of an Ankimon-style battle in AnkiGo's panel (two
answers make one round): catch and collect Pokémon, level them up, evolve them, fill the Pokédex, earn coins for the
daily shop. Nothing needs a tap while you study. The rules follow the [Ankimon](https://github.com/h0tp-ftw/ankimon)
Anki add-on, with a simpler battle engine.

No Pokémon pictures or data are part of this repository or the APK. On first start AnkiGo downloads the game
data (about 4 MB, from [PokeAPI](https://pokeapi.co) and [Pokémon Showdown](https://pokemonshowdown.com)) and
small pictures (about 1 MB); battle animations download as you meet new Pokémon. Pokémon and Pokémon names
are © Nintendo / Creatures / GAME FREAK.

The step-0 test screens (live watch, checks, report) are under Settings → Diagnostics.

## Install

1. Install AnkiDroid first.
2. Download the newest `AnkiGo-X.Y.Z.apk` from [Releases](https://github.com/Husarp/AnkiGo/releases) and
   open it on the phone (allow "Install unknown apps" for your browser or Files app when Android asks).
3. Open AnkiGo and allow access to AnkiDroid when it asks. AnkiGo needs AnkiDroid's API permission
   ("read and write to the AnkiDroid database") to see your answers. The game only reads; it never changes your cards.
4. Put AnkiDroid and AnkiGo in split screen (AnkiDroid on top) and study as usual.

After that, AnkiGo updates itself: it shows a banner when a new version is out, downloads it and hands it
to Android's installer (Android asks you to confirm each update).
