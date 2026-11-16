package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Encounters
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type

/** Approved R50 backgrounds, their stand points and type pools. */
internal object BackdropCatalog {
    private fun p(x: Float, y: Float, s: Float) = StandPoint(x, y, s)

    private fun own(id: String, ext: String, w: Int, h: Int, player: StandPoint, wild: StandPoint, crisp: Boolean, dark: Boolean = false, rev: Int = 1, ground: StandPoint = wild, groundZoom: Float = 1f, groundMaxHeight: Float = 1f) =
        Backdrop(id, BackdropSource.OWN, ext, w, h, player, wild, crisp, dark, rev, sharp = false, wildGround = ground, groundZoom = groundZoom, groundMaxHeight = groundMaxHeight)

    // R50: approved final art; native pixels are drawn without filtered or Lanczos scaling.
    val ALL: List<Backdrop> = listOf(
        own("sandbar-isle", "webp", 512, 342, p(.29f, .94f, .44f), p(.74f, .54f, .32f), crisp = true, ground = p(.50f, .80f, .32f), groundZoom = 3f, groundMaxHeight = .40f, rev = 50),
        own("reef-bay", "webp", 512, 341, p(.29f, .94f, .44f), p(.74f, .54f, .32f), crisp = true, ground = p(.50f, .80f, .32f), groundZoom = 3f, groundMaxHeight = .40f, rev = 50),
        own("canyon", "webp", 512, 286, p(.33f, .74f, .46f), p(.73f, .47f, .33f), crisp = true, rev = 50),
        own("crystal-storm", "webp", 1024, 687, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = true, rev = 50),
        own("haunted-mansion", "webp", 1264, 848, p(.30f, .79f, .44f), p(.695f, .605f, .30f), crisp = true, dark = true, rev = 50),
        own("mine", "webp", 1024, 687, p(.32f, .72f, .44f), p(.76f, .50f, .32f), crisp = true, dark = true, rev = 50),
        own("snow-aurora", "webp", 512, 344, p(.32f, .79f, .46f), p(.79f, .58f, .33f), crisp = true, dark = true, rev = 50),
        own(SHRINE, "webp", 1024, 687, p(.30f, .88f, .44f), p(.72f, .70f, .32f), crisp = true, rev = 50),
        own("dark-town", "webp", 1024, 687, p(.30f, .86f, .44f), p(.68f, .60f, .32f), crisp = true, dark = true, rev = 50),
        own("dark-forest", "webp", 1024, 687, p(.28f, .84f, .44f), p(.70f, .58f, .32f), crisp = true, dark = true, rev = 50),
        own("fire-savanna", "webp", 1024, 687, p(.30f, .84f, .44f), p(.66f, .64f, .32f), crisp = true, rev = 50),
        own("fighting-training", "webp", 1024, 687, p(.30f, .84f, .44f), p(.64f, .58f, .32f), crisp = true, rev = 50),
        own("forest-tech-b", "webp", 512, 343, p(.29f, .72f, .44f), p(.79f, .51f, .32f), crisp = true, dark = false, rev = 50),
        own("autumn-forest", "webp", 512, 344, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = false, rev = 50),
        own("countryside-route", "webp", 512, 344, p(.29f, .80f, .44f), p(.77f, .585f, .32f), crisp = true, dark = false, rev = 50),
        own("windy-hilltop", "webp", 512, 344, p(.29f, .79f, .44f), p(.78f, .66f, .32f), crisp = true, dark = false, rev = 50),
        own("bamboo-dojo", "webp", 512, 344, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = false, rev = 50),
        own("snowy-pines", "webp", 512, 344, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = false, rev = 50),
        own("power-plant", "webp", 1024, 687, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = false, rev = 50),
        own("desert-oasis", "webp", 1024, 687, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = false, rev = 50),
        own("starry-plateau", "webp", 512, 343, p(.29f, .79f, .44f), p(.76f, .61f, .32f), crisp = true, dark = true, rev = 50),
        own("lava-field", "webp", 1024, 687, p(.30f, .82f, .44f), p(.79f, .475f, .32f), crisp = true, dark = false, rev = 50),
        own("night-rooftops", "webp", 1024, 687, p(.29f, .80f, .44f), p(.79f, .49f, .32f), crisp = true, dark = true, rev = 50),
        own("old-graveyard", "webp", 512, 344, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = true, rev = 50),
        own("starry-plateau-galaxy", "webp", 512, 344, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = true, rev = 50),
        own("dojo", "webp", 1264, 848, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = false, rev = 50),
        own("graveyard", "webp", 1024, 687, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = true, rev = 50),
        own("ruined-castle", "webp", 1024, 687, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = true, rev = 50),
        own("snow-field", "webp", 1024, 687, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = false, rev = 50),
        own("volcano-b", "webp", 1024, 687, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = false, rev = 50),
        own("night-forest-2", "webp", 1024, 687, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = true, rev = 50),
        own("normal-meadow", "webp", 512, 343, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = false),
        own("grass-glade", "webp", 512, 343, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = false),
        own("poison-swamp", "webp", 1024, 687, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = true),
        own("flying-ridge", "webp", 1024, 687, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = false),
        own("violet-plateau", "webp", 1024, 687, p(.29f, .80f, .44f), p(.74f, .54f, .32f), crisp = true, dark = false),
    )

    private val BY_ID: Map<String, Backdrop> = ALL.associateBy { it.id }

    fun byId(id: String): Backdrop? = BY_ID[id]

    private val OPTIONS: Map<Type, List<String>> = mapOf(
        Type.NORMAL to listOf("normal-meadow", "countryside-route", "autumn-forest"),
        Type.FIRE to listOf("fire-savanna", "lava-field", "volcano-b"),
        Type.WATER to listOf("sandbar-isle", "reef-bay"),
        Type.GRASS to listOf("grass-glade", "autumn-forest", "countryside-route"),
        Type.ELECTRIC to listOf("crystal-storm", "power-plant"),
        Type.ICE to listOf("snow-aurora", "snowy-pines", "snow-field"),
        Type.FIGHTING to listOf("fighting-training", "bamboo-dojo", "dojo"),
        Type.POISON to listOf("poison-swamp"),
        Type.GROUND to listOf("mine", "canyon", "fighting-training", "desert-oasis"),
        Type.FLYING to listOf("flying-ridge", "windy-hilltop", "night-rooftops"),
        Type.PSYCHIC to listOf("violet-plateau", "starry-plateau", "starry-plateau-galaxy", "ruined-castle"),
        Type.BUG to listOf("forest-tech-b", "night-forest-2", "grass-glade", "autumn-forest"),
        Type.ROCK to listOf("crystal-storm", "mine", "canyon", "lava-field", "ruined-castle"),
        Type.GHOST to listOf("haunted-mansion", "night-forest-2", "old-graveyard", "graveyard", "ruined-castle"),
        Type.DRAGON to listOf("canyon", "starry-plateau-galaxy"),
        Type.DARK to listOf("dark-town", "dark-forest", "night-forest-2", "night-rooftops"),
        Type.STEEL to listOf("mine", "power-plant"),
        Type.FAIRY to listOf("forest-tech-b", "grass-glade", "starry-plateau"),
    )

    val GENERAL: Set<String> = setOf(
        "autumn-forest", "countryside-route", "windy-hilltop", "bamboo-dojo", "snowy-pines", "power-plant", "desert-oasis", "starry-plateau", "lava-field", "night-rooftops", "old-graveyard", "starry-plateau-galaxy"
    )

    /** The pictures a type can get, in the order [pick] uses. */
    fun options(type: Type): List<Backdrop> =
        OPTIONS.getValue(type).map { BY_ID.getValue(it) }

    /** Settings uses the selection pools themselves so its type chips cannot drift from battle eligibility. */
    fun typesFor(id: String): List<Type> = Type.entries.filter { type -> options(type).any { it.id == id } }

    /** The type the picture follows: the first, or the second when the first is Normal and there is one; Normal when there is none. */
    fun typeFor(types: List<Type>): Type {
        val first = types.firstOrNull() ?: return Type.NORMAL
        return if (first == Type.NORMAL) types.getOrNull(1) ?: Type.NORMAL else first
    }

    /**
     * The special arena of a Legendary / Mythical encounter; in no type list. Round 9: the old shrine picture was dropped (D10), the new one
     * (batch 3, compact revision 50) is in [ALL] again; without a catalog entry [pick] would give a Legendary its type's pictures.
     */
    const val SHRINE = "legendary-shrine"

    /** True for a Legendary / Mythical encounter (by tier, so Ankimon's extra legendaries count; Ultra Beasts don't). */
    fun isLegendary(sp: Species?): Boolean = sp != null && Encounters.tierOf(sp).let { it == Tier.LEGENDARY || it == Tier.MYTHICAL }

    /** The picture for a wild Pokémon with [types]; [legendary] gives the shrine when the catalog has one (the retained shrine artwork); [seed] is fixed for one encounter (the wild one's uid hash), so the fight keeps it. Uses a neutral countryside route if both type pools clash. */
    fun pick(types: List<Type>, seed: Int, legendary: Boolean = false): Backdrop =
        pickEnabled(types, seed, legendary, emptySet()) ?: BY_ID.getValue("countryside-route")

    private val FIRE_SCENES = setOf("fire-savanna", "lava-field", "volcano-b")
    private val DESERT_SCENES = setOf("desert-oasis", "canyon")
    private val ICE_SCENES = setOf("snow-aurora", "snowy-pines", "snow-field")

    /** Both types can veto a clashing biome, even when the other type would allow it. */
    private fun compatible(id: String, types: List<Type>): Boolean =
        !(Type.WATER in types && id in FIRE_SCENES + DESERT_SCENES) &&
        !(Type.ICE in types && id in FIRE_SCENES + DESERT_SCENES) &&
        !(Type.FIRE in types && id in ICE_SCENES) &&
        !(Type.GRASS in types && id in FIRE_SCENES) &&
        !(Type.BUG in types && id in FIRE_SCENES)

    /** Disabled scenes never appear. With no eligible enabled scene, the arena uses its existing plain background. */
    fun pickEnabled(types: List<Type>, seed: Int, legendary: Boolean, disabled: Set<String>): Backdrop? {
        if (legendary && SHRINE !in disabled) BY_ID[SHRINE]?.let { return it }
        val primary = options(typeFor(types)).filter { compatible(it.id, types) }
        // A biome clash can rule out the entire primary pool (Fire/Water); try the other type then.
        val eligible = primary.ifEmpty { types.flatMap { options(it) }.distinct().filter { compatible(it.id, types) } }
        val options = eligible.filter { it.id !in disabled }
        return if (options.isEmpty()) null else options[Math.floorMod(seed, options.size)]
    }

    /** The seed of one encounter from the wild Pokémon's uid (empty uid: 0). */
    fun seed(uid: String): Int = uid.hashCode()
}
