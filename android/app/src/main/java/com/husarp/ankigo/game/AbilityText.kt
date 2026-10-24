package com.husarp.ankigo.game

/**
 * R17C-9a / 9b: what each ability does in AnkiGo, in one plain sentence (shown under the ability in the details of your Pokémon), and the
 * ability's display name. One row per ability that [Abilities] (the first 102 rows) and [AbilitiesMore] (the rest) build; every other ability of the
 * species data is in [NO_EFFECT] with the reason it can do nothing in AnkiGo ("No effect in AnkiGo battles: there are no held items in fights."). A
 * sentence is true to the code (the numbers come from the same constants), starts with the effect and ends with a full stop. "Turn" is one battle round.
 *
 * [of] takes a name as the game stores it ("Keen Eye", "keeneye", "KEEN EYE"); a name that is no ability of the game gives null. [ids] are the abilities
 * that work in battle, [noEffect] the ones that don't.
 */
internal object AbilityText {
    private class Row(val id: String, val name: String, val text: String)

    private const val C = Abilities.CONTACT_CHANCE

    /** A multiplier as a sentence says it: 1.5 -> "1.5", 2.0 -> "2". */
    private fun x(d: Double): String = if (d == d.toInt().toDouble()) d.toInt().toString() else d.toString()

    private val ROWS: List<Row> = listOf(
        Row("sturdy", "Sturdy", "It survives a hit that would knock it out from full HP with 1 HP left, and one-hit KO moves don't affect it."),
        Row("keeneye", "Keen Eye", "The other Pokémon can't lower its accuracy, and it ignores the target's evasiveness."),
        Row("intimidate", "Intimidate", "When it enters the fight, it lowers the other Pokémon's Attack by 1."),
        Row("levitate", "Levitate", "It floats above the ground, so Ground moves don't hit it."),
        Row("swiftswim", "Swift Swim", "Doubles its Speed in rain."),
        Row("chlorophyll", "Chlorophyll", "Doubles its Speed in harsh sunlight."),
        Row("sandrush", "Sand Rush", "Doubles its Speed in a sandstorm, which doesn't hurt it."),
        Row("overgrow", "Overgrow", "Its Grass moves deal 1.5 times the damage when its HP is a third of its max HP or less."),
        Row("blaze", "Blaze", "Its Fire moves deal 1.5 times the damage when its HP is a third of its max HP or less."),
        Row("torrent", "Torrent", "Its Water moves deal 1.5 times the damage when its HP is a third of its max HP or less."),
        Row("swarm", "Swarm", "Its Bug moves deal 1.5 times the damage when its HP is a third of its max HP or less."),
        Row("innerfocus", "Inner Focus", "It can't flinch, and Intimidate doesn't work on it."),
        Row("owntempo", "Own Tempo", "It can't be confused, and Intimidate doesn't work on it."),
        Row("static", "Static", "A Pokémon that hits it with a move that makes contact is paralyzed $C % of the time."),
        Row("poisonpoint", "Poison Point", "A Pokémon that hits it with a move that makes contact is poisoned $C % of the time."),
        Row("flamebody", "Flame Body", "A Pokémon that hits it with a move that makes contact is burned $C % of the time."),
        Row("flashfire", "Flash Fire", "Fire moves don't hurt it. Instead its own Fire moves deal 1.5 times the damage for the rest of the fight."),
        Row("oblivious", "Oblivious", "It can't fall in love or be taunted, and Intimidate doesn't work on it."),
        Row("shedskin", "Shed Skin", "At the end of each turn it cures its status ${Abilities.SHED_SKIN_CHANCE} % of the time."),
        Row("guts", "Guts", "Its Attack is 1.5 times higher while it has a status, and a burn doesn't halve its damage."),
        Row("waterabsorb", "Water Absorb", "Water moves don't hurt it. They heal a quarter of its max HP instead."),
        Row("voltabsorb", "Volt Absorb", "Electric moves don't hurt it. They heal a quarter of its max HP instead."),
        Row("rockhead", "Rock Head", "It takes no recoil damage from its moves and no crash damage when a move like High Jump Kick misses."),
        Row("thickfat", "Thick Fat", "Fire and Ice moves deal half damage to it."),
        Row("hustle", "Hustle", "Its Attack is 1.5 times higher, but its physical moves are only 80 % as accurate."),
        Row("sandveil", "Sand Veil", "In a sandstorm moves are 20 % less accurate against it, and the sandstorm doesn't hurt it."),
        Row("snowcloak", "Snow Cloak", "In hail or snow moves are 20 % less accurate against it, and hail doesn't hurt it."),
        Row("rivalry", "Rivalry", "Its moves deal 1.25 times the damage to a Pokémon of the same gender and 0.75 times to one of the other gender. Nothing changes if either has no gender."),
        Row("earlybird", "Early Bird", "Its sleep wears off twice as fast."),
        Row("insomnia", "Insomnia", "It can't fall asleep."),
        Row("vitalspirit", "Vital Spirit", "It can't fall asleep."),
        Row("cutecharm", "Cute Charm", "A Pokémon that hits it with a move that makes contact falls in love $C % of the time, if one of them is male and the other female."),
        Row("clearbody", "Clear Body", "The other Pokémon can't lower its stats."),
        Row("whitesmoke", "White Smoke", "The other Pokémon can't lower its stats."),
        Row("hydration", "Hydration", "At the end of each turn in rain it cures its status."),
        Row("leafguard", "Leaf Guard", "It can't get a status in harsh sunlight."),
        Row("technician", "Technician", "Its moves with ${Abilities.TECHNICIAN_MAX_POWER} power or less deal 1.5 times the damage."),
        Row("limber", "Limber", "It can't be paralyzed."),
        Row("soundproof", "Soundproof", "Sound moves don't affect it."),
        Row("lightningrod", "Lightning Rod", "Electric moves don't hurt it. They raise its Sp. Atk by 1 instead."),
        Row("stormdrain", "Storm Drain", "Water moves don't hurt it. They raise its Sp. Atk by 1 instead."),
        Row("motordrive", "Motor Drive", "Electric moves don't hurt it. They raise its Speed by 1 instead."),
        Row("sapsipper", "Sap Sipper", "Grass moves don't hurt it. They raise its Attack by 1 instead."),
        Row("icebody", "Ice Body", "In hail or snow it heals a sixteenth of its max HP at the end of each turn, and hail doesn't hurt it."),
        Row("shielddust", "Shield Dust", "The extra effects of the other Pokémon's moves don't affect it."),
        Row("compoundeyes", "Compound Eyes", "Its moves are 1.3 times as accurate."),
        Row("hypercutter", "Hyper Cutter", "The other Pokémon can't lower its Attack."),
        Row("bigpecks", "Big Pecks", "The other Pokémon can't lower its Defense."),
        Row("sheerforce", "Sheer Force", "Its moves with an extra effect deal 1.3 times the damage but lose that effect."),
        Row("serenegrace", "Serene Grace", "The extra effects of its moves are twice as likely."),
        Row("strongjaw", "Strong Jaw", "Its biting moves deal 1.5 times the damage."),
        Row("ironfist", "Iron Fist", "Its punching moves deal 1.2 times the damage."),
        Row("megalauncher", "Mega Launcher", "Its pulse moves deal 1.5 times the damage."),
        Row("toughclaws", "Tough Claws", "Its moves that make contact deal 1.3 times the damage."),
        Row("reckless", "Reckless", "Its moves that can hurt it with recoil or crash damage deal 1.2 times the damage."),
        Row("adaptability", "Adaptability", "Its same-type attack bonus is 2 instead of 1.5."),
        Row("hugepower", "Huge Power", "Doubles its Attack."),
        Row("purepower", "Pure Power", "Doubles its Attack."),
        Row("infiltrator", "Infiltrator", "Its moves go through the other Pokémon's Reflect, Light Screen, Aurora Veil, Substitute, Safeguard and Mist."),
        Row("competitive", "Competitive", "When the other Pokémon lowers one of its stats, its Sp. Atk rises by 2."),
        Row("defiant", "Defiant", "When the other Pokémon lowers one of its stats, its Attack rises by 2."),
        Row("effectspore", "Effect Spore", "A Pokémon that hits it with a move that makes contact is poisoned, paralyzed or put to sleep ${Abilities.CONTACT_CHANCE} % of the time. Grass types and Pokémon with Overcoat are immune."),
        Row("damp", "Damp", "Nobody can use Explosion, Self-Destruct, Mind Blown or Misty Explosion."),
        Row("prankster", "Prankster", "Its status moves go first. They fail against Dark types."),
        Row("tangledfeet", "Tangled Feet", "While it is confused, moves are only half as accurate against it."),
        Row("magicguard", "Magic Guard", "It only loses HP to attacks, not to recoil, burns, poison, weather, Leech Seed, binding moves or curses."),
        Row("steadfast", "Steadfast", "When it flinches, its Speed rises by 1."),
        Row("weakarmor", "Weak Armor", "When a physical move hits it, its Defense falls by 1 and its Speed rises by 2."),
        Row("synchronize", "Synchronize", "When the other Pokémon burns, poisons or paralyzes it, that Pokémon gets the same status."),
        Row("dryskin", "Dry Skin", "Water moves don't hurt it. They heal a quarter of its max HP instead. Fire moves deal 1.25 times the damage to it. At the end of each turn it heals an eighth of its max HP in rain and loses an eighth in harsh sunlight."),
        Row("stench", "Stench", "Its damaging moves make the target flinch ${Abilities.STENCH_CHANCE} % of the time, if it moves first."),
        Row("poisontouch", "Poison Touch", "Its moves that make contact poison the target ${Abilities.CONTACT_CHANCE} % of the time."),
        Row("unaware", "Unaware", "It ignores the other Pokémon's stat changes, both when it attacks and when it is hit."),
        Row("regenerator", "Regenerator", "When you switch it out, it heals a third of its max HP."),
        Row("noguard", "No Guard", "Its moves and the moves used against it never miss."),
        Row("skilllink", "Skill Link", "Its moves that hit 2 to 5 times always hit 5 times, and every hit of Triple Axel and moves like it connects."),
        Row("waterveil", "Water Veil", "It can't be burned."),
        Row("trace", "Trace", "When it enters the fight, it copies the other Pokémon's ability."),
        Row("quickfeet", "Quick Feet", "Its Speed is 1.5 times higher while it has a status, and paralysis doesn't slow it."),
        Row("overcoat", "Overcoat", "Powder moves and sandstorm or hail damage don't affect it."),
        Row("speedboost", "Speed Boost", "Its Speed rises by 1 at the end of each turn, from its second turn in the fight."),
        Row("solidrock", "Solid Rock", "Super effective moves deal 0.75 times the damage to it."),
        Row("filter", "Filter", "Super effective moves deal 0.75 times the damage to it."),
        Row("tintedlens", "Tinted Lens", "Its not very effective moves deal double damage."),
        Row("scrappy", "Scrappy", "Its Normal and Fighting moves hit Ghost types, and Intimidate doesn't work on it."),
        Row("justified", "Justified", "When a Dark move hits it, its Attack rises by 1."),
        Row("rattled", "Rattled", "When a Bug, Ghost or Dark move hits it, or Intimidate lowers its Attack, its Speed rises by 1."),
        Row("liquidooze", "Liquid Ooze", "A Pokémon that drains HP from it loses that HP instead."),
        Row("roughskin", "Rough Skin", "A Pokémon that hits it with a move that makes contact loses an eighth of its max HP."),
        Row("ironbarbs", "Iron Barbs", "A Pokémon that hits it with a move that makes contact loses an eighth of its max HP."),
        Row("download", "Download", "When it enters the fight, its Sp. Atk rises by 1 if the other Pokémon's Sp. Def is lower than its Defense, else its Attack rises by 1."),
        Row("drizzle", "Drizzle", "When it enters the fight, it makes it rain for ${FieldRules.WEATHER_TURNS} turns."),
        Row("drought", "Drought", "When it enters the fight, it makes the sun shine harshly for ${FieldRules.WEATHER_TURNS} turns."),
        Row("sandstream", "Sand Stream", "When it enters the fight, it whips up a sandstorm for ${FieldRules.WEATHER_TURNS} turns."),
        Row("snowwarning", "Snow Warning", "When it enters the fight, it makes it snow for ${FieldRules.WEATHER_TURNS} turns."),
        Row("simple", "Simple", "Its stat changes are doubled."),
        Row("contrary", "Contrary", "Its stat changes are reversed."),
        Row("heatproof", "Heatproof", "Fire moves deal half damage to it, and its burn hurts half as much."),
        Row("cursedbody", "Cursed Body", "When a move hits it, that move is disabled for ${Abilities.CURSED_BODY_TURNS} turns ${Abilities.CURSED_BODY_CHANCE} % of the time."),
        Row("moldbreaker", "Mold Breaker", "Its moves ignore the target's ability when it would protect the target, like Sturdy, Levitate or Clear Body."),
        Row("teravolt", "Teravolt", "Its moves ignore the target's ability when it would protect the target, like Sturdy, Levitate or Clear Body."),
        Row("turboblaze", "Turboblaze", "Its moves ignore the target's ability when it would protect the target, like Sturdy, Levitate or Clear Body."),
    )


    /** R17C-9b: the rows of [AbilitiesMore]. */
    private val MORE: List<Row> = listOf(
        Row("aftermath", "Aftermath", "When a move that makes contact knocks it out, the attacker loses a quarter of its max HP, unless the attacker has Damp."),
        Row("sweetveil", "Sweet Veil", "It can't fall asleep, and Yawn doesn't work on it."),
        Row("fluffy", "Fluffy", "Moves that make contact deal ${x(AbilitiesMore.FLUFFY_CONTACT)} times the damage to it, but Fire moves deal ${x(AbilitiesMore.FLUFFY_FIRE)} times the damage."),
        Row("bulletproof", "Bulletproof", "Ball and bomb moves, like Shadow Ball and Sludge Bomb, don't affect it."),
        Row("solarpower", "Solar Power", "In harsh sunlight its Sp. Atk is ${x(AbilitiesMore.SOLAR_POWER)} times higher, but it loses an eighth of its max HP at the end of each turn."),
        Row("magmaarmor", "Magma Armor", "It can't be frozen."),
        Row("raindish", "Rain Dish", "In rain it heals a sixteenth of its max HP at the end of each turn."),
        Row("sandforce", "Sand Force", "In a sandstorm its Rock, Ground and Steel moves deal ${x(AbilitiesMore.SAND_FORCE)} times the damage, and the sandstorm doesn't hurt it."),
        Row("slushrush", "Slush Rush", "Doubles its Speed in hail or snow."),
        Row("stamina", "Stamina", "When a move hits it, its Defense rises by 1."),
        Row("steamengine", "Steam Engine", "When a Fire or Water move hits it, its Speed rises by ${AbilitiesMore.STEAM_ENGINE_STAGES}."),
        Row("purifyingsalt", "Purifying Salt", "It can't get a status, and Yawn doesn't work on it. Ghost moves deal half damage to it."),
        Row("thermalexchange", "Thermal Exchange", "When a Fire move hits it, its Attack rises by 1. It can't be burned."),
        Row("toxicchain", "Toxic Chain", "Its damaging moves badly poison the target ${AbilitiesMore.TOXIC_CHAIN_CHANCE} % of the time."),
        Row("windrider", "Wind Rider", "Wind moves don't hurt it. They raise its Attack by 1 instead, and so does its own Tailwind."),
        Row("windpower", "Wind Power", "When a wind move hits it, its next Electric move deals double damage."),
        Row("electromorphosis", "Electromorphosis", "When a move hits it, its next Electric move deals double damage."),
        Row("furcoat", "Fur Coat", "Doubles its Defense."),
        Row("cloudnine", "Cloud Nine", "While it is in the fight, the weather has no effect on anybody."),
        Row("airlock", "Air Lock", "While it is in the fight, the weather has no effect on anybody."),
        Row("pastelveil", "Pastel Veil", "It can't be poisoned."),
        Row("immunity", "Immunity", "It can't be poisoned."),
        Row("screencleaner", "Screen Cleaner", "When it enters the fight, Reflect, Light Screen and Aurora Veil end on both sides."),
        Row("berserk", "Berserk", "When a hit leaves it at half its max HP or less, its Sp. Atk rises by 1."),
        Row("angershell", "Anger Shell", "When a hit leaves it at half its max HP or less, its Attack, Sp. Atk and Speed rise by 1 and its Defense and Sp. Def fall by 1."),
        Row("defeatist", "Defeatist", "At half its max HP or less, its Attack and Sp. Atk are halved."),
        Row("poisonheal", "Poison Heal", "While it is poisoned, it heals an eighth of its max HP at the end of each turn instead of losing HP."),
        Row("truant", "Truant", "It can only use a move every other turn."),
        Row("normalize", "Normalize", "Its attacks become Normal moves and deal ${x(AbilitiesMore.CONVERTED)} times the damage, except Weather Ball and a few moves like it."),
        Row("refrigerate", "Refrigerate", "Its Normal moves become Ice moves and deal ${x(AbilitiesMore.CONVERTED)} times the damage."),
        Row("pixilate", "Pixilate", "Its Normal moves become Fairy moves and deal ${x(AbilitiesMore.CONVERTED)} times the damage."),
        Row("aerilate", "Aerilate", "Its Normal moves become Flying moves and deal ${x(AbilitiesMore.CONVERTED)} times the damage."),
        Row("galvanize", "Galvanize", "Its Normal moves become Electric moves and deal ${x(AbilitiesMore.CONVERTED)} times the damage."),
        Row("mummy", "Mummy", "When a move that makes contact hits it, the attacker's ability becomes Mummy."),
        Row("lingeringaroma", "Lingering Aroma", "When a move that makes contact hits it, the attacker's ability becomes Lingering Aroma."),
        Row("wanderingspirit", "Wandering Spirit", "When a move that makes contact hits it, it swaps abilities with the attacker."),
        Row("waterbubble", "Water Bubble", "Fire moves deal half damage to it, its own Water moves deal double damage, and it can't be burned."),
        Row("gooey", "Gooey", "A Pokémon that hits it with a move that makes contact loses 1 Speed."),
        Row("tanglinghair", "Tangling Hair", "A Pokémon that hits it with a move that makes contact loses 1 Speed."),
        Row("cottondown", "Cotton Down", "When a move hits it, the attacker loses 1 Speed."),
        Row("sandspit", "Sand Spit", "When a move hits it, a sandstorm starts for ${FieldRules.WEATHER_TURNS} turns."),
        Row("seedsower", "Seed Sower", "When a move hits it, Grassy Terrain starts for ${FieldRules.TERRAIN_TURNS} turns."),
        Row("supersweetsyrup", "Supersweet Syrup", "When it enters the fight, the other Pokémon's evasiveness falls by 1."),
        Row("wonderguard", "Wonder Guard", "Only super-effective moves and Struggle hurt it."),
        Row("marvelscale", "Marvel Scale", "Its Defense is ${x(AbilitiesMore.MARVEL_SCALE)} times higher while it has a status."),
        Row("sharpness", "Sharpness", "Its slicing moves deal ${x(AbilitiesMore.SHARPNESS)} times the damage."),
        Row("slowstart", "Slow Start", "For its first ${AbilitiesMore.SLOW_START_TURNS} turns in the fight, its Attack and Speed are halved."),
        Row("electricsurge", "Electric Surge", "When it enters the fight, it makes Electric Terrain for ${FieldRules.TERRAIN_TURNS} turns."),
        Row("psychicsurge", "Psychic Surge", "When it enters the fight, it makes Psychic Terrain for ${FieldRules.TERRAIN_TURNS} turns."),
        Row("grassysurge", "Grassy Surge", "When it enters the fight, it makes Grassy Terrain for ${FieldRules.TERRAIN_TURNS} turns."),
        Row("mistysurge", "Misty Surge", "When it enters the fight, it makes Misty Terrain for ${FieldRules.TERRAIN_TURNS} turns."),
        Row("fullmetalbody", "Full Metal Body", "The other Pokémon can't lower its stats."),
        Row("shadowshield", "Shadow Shield", "At full HP it takes half damage from moves."),
        Row("multiscale", "Multiscale", "At full HP it takes half damage from moves."),
        Row("prismarmor", "Prism Armor", "Super effective moves deal ${x(AbilitiesMore.PRISM_ARMOR)} times the damage to it."),
        Row("punkrock", "Punk Rock", "Its sound moves deal ${x(AbilitiesMore.PUNK_ROCK)} times the damage, and sound moves deal half damage to it."),
        Row("transistor", "Transistor", "Its Electric moves deal ${x(AbilitiesMore.TRANSISTOR)} times the damage."),
        Row("dragonsmaw", "Dragon's Maw", "Its Dragon moves deal ${x(AbilitiesMore.TYPE_BOOST)} times the damage."),
        Row("rockypayload", "Rocky Payload", "Its Rock moves deal ${x(AbilitiesMore.TYPE_BOOST)} times the damage."),
        Row("steelworker", "Steelworker", "Its Steel moves deal ${x(AbilitiesMore.TYPE_BOOST)} times the damage."),
        Row("steelyspirit", "Steely Spirit", "Its Steel moves deal ${x(AbilitiesMore.TYPE_BOOST)} times the damage."),
        Row("intrepidsword", "Intrepid Sword", "When it enters the fight, its Attack rises by 1."),
        Row("dauntlessshield", "Dauntless Shield", "When it enters the fight, its Defense rises by 1."),
        Row("eartheater", "Earth Eater", "Ground moves don't hurt it. They heal a quarter of its max HP instead."),
        Row("wellbakedbody", "Well-Baked Body", "Fire moves don't hurt it. They raise its Defense by ${AbilitiesMore.WELL_BAKED_STAGES} instead."),
        Row("armortail", "Armor Tail", "Moves that go first can't be used against it."),
        Row("queenlymajesty", "Queenly Majesty", "Moves that go first can't be used against it."),
        Row("dazzling", "Dazzling", "Moves that go first can't be used against it."),
        Row("comatose", "Comatose", "It can't get a status, but it counts as asleep for Dream Eater, Wake-Up Slap and Snore and as having a status for Hex."),
        Row("tabletsofruin", "Tablets of Ruin", "The other Pokémon's Attack is ${x(AbilitiesMore.RUIN)} times as high while it is in the fight."),
        Row("swordofruin", "Sword of Ruin", "The other Pokémon's Defense is ${x(AbilitiesMore.RUIN)} times as high while it is in the fight."),
        Row("vesselofruin", "Vessel of Ruin", "The other Pokémon's Sp. Atk is ${x(AbilitiesMore.RUIN)} times as high while it is in the fight."),
        Row("beadsofruin", "Beads of Ruin", "The other Pokémon's Sp. Def is ${x(AbilitiesMore.RUIN)} times as high while it is in the fight."),
        Row("colorchange", "Color Change", "When a damaging move hits it, it becomes the type of that move."),
        Row("wonderskin", "Wonder Skin", "Status moves used on it are only ${AbilitiesMore.WONDER_SKIN_ACCURACY.toInt()} % accurate."),
        Row("guarddog", "Guard Dog", "Intimidate raises its Attack by 1 instead of lowering it."),
        Row("opportunist", "Opportunist", "When a stat of the other Pokémon rises, the same stat of its own rises too."),
        Row("orichalcumpulse", "Orichalcum Pulse", "When it enters the fight, it makes the sun shine harshly for ${FieldRules.WEATHER_TURNS} turns. In harsh sunlight its Attack is ${x(AbilitiesMore.ENERGY)} times higher."),
        Row("hadronengine", "Hadron Engine", "When it enters the fight, it makes Electric Terrain for ${FieldRules.TERRAIN_TURNS} turns. In Electric Terrain its Sp. Atk is ${x(AbilitiesMore.ENERGY)} times higher."),
        Row("protosynthesis", "Protosynthesis", "In harsh sunlight its highest stat is ${x(AbilitiesMore.ENERGY_STAT)} times higher, or ${x(AbilitiesMore.ENERGY_SPEED)} times if that is its Speed."),
        Row("quarkdrive", "Quark Drive", "In Electric Terrain its highest stat is ${x(AbilitiesMore.ENERGY_STAT)} times higher, or ${x(AbilitiesMore.ENERGY_SPEED)} times if that is its Speed."),
        Row("unseenfist", "Unseen Fist", "Its moves that make contact go through Protect and similar moves."),
        Row("disguise", "Disguise", "The first move that hits it does no damage, then it loses an eighth of its max HP. This works once per fight."),
        Row("stall", "Stall", "It moves last among moves of the same priority."),
        Row("quickdraw", "Quick Draw", "Its damaging moves go first among moves of the same priority ${AbilitiesMore.QUICK_DRAW_CHANCE} % of the time."),
        Row("triage", "Triage", "Its healing moves, and draining moves like Giga Drain, go first (priority +${AbilitiesMore.TRIAGE_PRIORITY})."),
        Row("fairyaura", "Fairy Aura", "Fairy moves deal ${x(AbilitiesMore.AURA)} times the damage, whoever uses them."),
        Row("darkaura", "Dark Aura", "Dark moves deal ${x(AbilitiesMore.AURA)} times the damage, whoever uses them."),
        Row("aurabreak", "Aura Break", "Fairy Aura and Dark Aura make Fairy and Dark moves deal ${x(AbilitiesMore.AURA_BREAK)} times the damage instead."),
        Row("gorillatactics", "Gorilla Tactics", "Its Attack is ${x(AbilitiesMore.GORILLA_TACTICS)} times higher, but it keeps using the first move it used."),
        Row("neutralizinggas", "Neutralizing Gas", "While it is in the fight, no other ability does anything."),
        Row("stakeout", "Stakeout", "Its attacks deal double damage to a Pokémon that has just come into the fight, like the wild one's attack when you switch."),
        Row("corrosion", "Corrosion", "It can poison Steel and Poison types."),
        Row("watercompaction", "Water Compaction", "When a Water move hits it, its Defense rises by ${AbilitiesMore.WATER_COMPACTION_STAGES}."),
        Row("goodasgold", "Good as Gold", "Status moves used on it fail."),
        Row("lightmetal", "Light Metal", "Its weight is halved for moves that depend on weight."),
        Row("heavymetal", "Heavy Metal", "Its weight is doubled for moves that depend on weight."),
        Row("aromaveil", "Aroma Veil", "Taunt, Encore, Disable, Torment, Heal Block and infatuation don't work on it."),
        Row("flowerveil", "Flower Veil", "If it is a Grass type, the other Pokémon can't lower its stats or give it a status, and Yawn doesn't work on it."),
        Row("baddreams", "Bad Dreams", "A sleeping Pokémon opposite it loses an eighth of its max HP at the end of each turn."),
        Row("victorystar", "Victory Star", "Its moves are ${x(AbilitiesMore.VICTORY_STAR)} times as accurate."),
        Row("poisonpuppeteer", "Poison Puppeteer", "A Pokémon that its move poisons also becomes confused."),
        Row("innardsout", "Innards Out", "When a move knocks it out, the attacker loses as much HP as the hit took off it."),
        Row("myceliummight", "Mycelium Might", "Its status moves go last among moves of the same priority and ignore the target's ability."),
        Row("moody", "Moody", "At the end of each turn one of its stats rises by 2 and another falls by 1."),
        Row("toxicboost", "Toxic Boost", "While it is poisoned, its physical moves deal ${x(AbilitiesMore.STATUS_BOOST)} times the damage."),
        Row("flareboost", "Flare Boost", "While it is burned, its special moves deal ${x(AbilitiesMore.STATUS_BOOST)} times the damage."),
        Row("mirrorarmor", "Mirror Armor", "Stat drops that the other Pokémon causes bounce back to it."),
        Row("icescales", "Ice Scales", "Special moves deal half damage to it."),
        Row("analytic", "Analytic", "Its moves deal ${x(AbilitiesMore.ANALYTIC)} times the damage when it moves last."),
        Row("surgesurfer", "Surge Surfer", "Doubles its Speed in Electric Terrain."),
        Row("mimicry", "Mimicry", "Its type changes to match the terrain, and goes back when the terrain ends."),
        Row("longreach", "Long Reach", "Its moves never make contact."),
        Row("galewings", "Gale Wings", "While it has full HP, its Flying moves go first."),
        Row("illuminate", "Illuminate", "The other Pokémon can't lower its accuracy, and it ignores the target's evasiveness."),
    )

    // ---------------------------------------------------------------- the abilities that do nothing in AnkiGo

    private const val NO_EFFECT = "No effect in AnkiGo battles: "
    private const val ITEMS = "there are no held items in fights."
    private const val PARTNER = "it helps a partner Pokémon."
    private const val SWITCH = "it works when Pokémon switch or run."
    private const val KNOCK_OUT = "a fight ends when a Pokémon faints."
    private const val PP = "AnkiGo has no PP."
    private const val INFO = "it only shows information."
    private const val LOOKS = "it only changes how the Pokémon looks."
    private const val FORMS = "AnkiGo has no form changes."
    private const val CRITS = "AnkiGo has no critical hits."
    private const val HAZARDS = "there are no entry hazards in fights."

    /** Why an ability can do nothing here -> its abilities as "id=Name" pairs. */
    private val NO_EFFECT_GROUPS: Map<String, String> = linkedMapOf(
        ITEMS to "gluttony=Gluttony pickup=Pickup unburden=Unburden cheekpouch=Cheek_Pouch ripen=Ripen klutz=Klutz pickpocket=Pickpocket magician=Magician " +
            "ballfetch=Ball_Fetch cudchew=Cud_Chew honeygather=Honey_Gather unnerve=Unnerve stickyhold=Sticky_Hold",
        PARTNER to "healer=Healer plus=Plus minus=Minus telepathy=Telepathy friendguard=Friend_Guard battery=Battery powerspot=Power_Spot receiver=Receiver " +
            "commander=Commander dancer=Dancer hospitality=Hospitality curiousmedicine=Curious_Medicine",
        SWITCH to "runaway=Run_Away naturalcure=Natural_Cure magnetpull=Magnet_Pull arenatrap=Arena_Trap shadowtag=Shadow_Tag wimpout=Wimp_Out " +
            "emergencyexit=Emergency_Exit zerotohero=Zero_to_Hero suctioncups=Suction_Cups",
        KNOCK_OUT to "beastboost=Beast_Boost moxie=Moxie soulheart=Soul-Heart chillingneigh=Chilling_Neigh grimneigh=Grim_Neigh supremeoverlord=Supreme_Overlord",
        PP to "pressure=Pressure",
        INFO to "frisk=Frisk anticipation=Anticipation forewarn=Forewarn",
        LOOKS to "illusion=Illusion",
        FORMS to "forecast=Forecast flowergift=Flower_Gift stancechange=Stance_Change schooling=Schooling shieldsdown=Shields_Down iceface=Ice_Face " +
            "hungerswitch=Hunger_Switch gulpmissile=Gulp_Missile multitype=Multitype rkssystem=RKS_System terashift=Tera_Shift",
        CRITS to "sniper=Sniper superluck=Super_Luck battlearmor=Battle_Armor shellarmor=Shell_Armor angerpoint=Anger_Point merciless=Merciless",
        HAZARDS to "toxicdebris=Toxic_Debris",
    )

    private val SKIPPED: List<Pair<String, Row>> = NO_EFFECT_GROUPS.flatMap { (reason, pairs) ->
        pairs.split(' ').map { pair ->
            val (id, name) = pair.split('=')
            reason to Row(id, name.replace('_', ' '), NO_EFFECT + reason)
        }
    }

    private val BUILT_BY_ID: Map<String, Row> = (ROWS + MORE).associateBy { it.id }
    private val BY_ID: Map<String, Row> = BUILT_BY_ID + SKIPPED.associate { it.second.id to it.second }

    /** The ids of every ability that works in battle (= the abilities [Abilities] and [AbilitiesMore] build). */
    val ids: Set<String> get() = BUILT_BY_ID.keys

    /** The ids of the abilities that do nothing in AnkiGo (their sentence says why). */
    val noEffect: Set<String> get() = SKIPPED.map { it.second.id }.toSet()

    /** Why the abilities that do nothing can't do anything here: the reason sentence (without "No effect in AnkiGo battles: ") -> their ids. */
    val noEffectByReason: Map<String, Set<String>> get() = SKIPPED.groupBy({ it.first }, { it.second.id }).mapValues { it.value.toSet() }

    /** The sentence for the ability [name] ("Static", "keeneye" ...), or null when it is no ability of the game. */
    fun of(name: String?): String? = name?.let { BY_ID[BattleView.normalizeAbility(it)]?.text }

    /** The display name of the ability with the id [id] ("keeneye" -> "Keen Eye"), or null when it has no row. */
    fun name(id: String): String? = BY_ID[id]?.name
}
