package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Encounters
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type

/**
 * Round 8 battle backgrounds (pure, JVM-tested): the 43 pictures, their stand points and which types get them. 13 are Pokémon
 * Showdown's (deep sea dropped), 30 are Adam's own art (his reef, volcano with platforms and garden at their pixel grid, the wide
 * set, batch 1, batch 2 with the legendary shrine). Stand points: the STAGE table of docs/design/backgrounds-stage.html (Showdown, Adam 3:2 and wide) and
 * backgrounds-stage-r8.html (batches 1 and 2, measured for round 8). A wild Pokémon gets one of its type's pictures at random per encounter,
 * the same for the whole fight ([pick] with a seed from the wild one's uid), also after a restart or a redraw from the save.
 */
internal object BackdropCatalog {
    private fun p(x: Float, y: Float, s: Float) = StandPoint(x, y, s)

    private fun showdown(id: String, w: Int, player: StandPoint, wild: StandPoint) =
        Backdrop(id, BackdropSource.SHOWDOWN, "png", w, 500, player, wild, crisp = true, dark = false)

    private fun own(id: String, ext: String, w: Int, h: Int, player: StandPoint, wild: StandPoint, crisp: Boolean, dark: Boolean = false) =
        Backdrop(id, BackdropSource.OWN, ext, w, h, player, wild, crisp, dark)

    val ALL: List<Backdrop> = listOf(
        // Pokémon Showdown (753 x 500, beach 700 x 500), pixel art
        showdown("beach", 700, p(.30f, .86f, .44f), p(.68f, .58f, .32f)),
        showdown("beachshore", 753, p(.30f, .86f, .44f), p(.68f, .60f, .32f)),
        showdown("city", 753, p(.30f, .86f, .44f), p(.68f, .56f, .32f)),
        showdown("dampcave", 753, p(.30f, .79f, .44f), p(.70f, .48f, .32f)),
        showdown("desert", 753, p(.30f, .82f, .44f), p(.64f, .55f, .32f)),
        showdown("earthycave", 753, p(.30f, .79f, .44f), p(.70f, .48f, .32f)),
        showdown("forest", 753, p(.30f, .78f, .44f), p(.68f, .50f, .32f)),
        showdown("icecave", 753, p(.30f, .79f, .44f), p(.70f, .48f, .32f)),
        showdown("meadow", 753, p(.27f, .80f, .44f), p(.68f, .49f, .32f)),
        showdown("mountain", 753, p(.30f, .79f, .44f), p(.68f, .48f, .32f)),
        showdown("river", 753, p(.34f, .80f, .44f), p(.62f, .52f, .32f)),
        showdown("route", 753, p(.25f, .81f, .44f), p(.70f, .48f, .32f)),
        showdown("volcanocave", 753, p(.28f, .76f, .44f), p(.70f, .48f, .32f)),
        // Adam's 3:2 pictures at their pixel grid (316 x 212, 256 colours): crisp
        own("reef", "png", 316, 212, p(.31f, .86f, .44f), p(.775f, .54f, .32f), crisp = true),
        own("volcano-b", "png", 316, 212, p(.32f, .77f, .44f), p(.775f, .55f, .32f), crisp = true),
        own("garden", "png", 316, 212, p(.285f, .84f, .44f), p(.76f, .61f, .32f), crisp = true),
        // Adam's wide set (about 2.3 : 1, soft)
        own("rocky-path", "png", 753, 325, p(.36f, .88f, .50f), p(.60f, .66f, .36f), crisp = false),
        own("volcano", "png", 753, 331, p(.34f, .89f, .50f), p(.68f, .70f, .36f), crisp = false),
        own("sky", "png", 753, 334, p(.33f, .89f, .50f), p(.70f, .72f, .36f), crisp = false),
        own("poison-swamp", "png", 753, 325, p(.35f, .89f, .50f), p(.66f, .67f, .36f), crisp = false),
        own("temple", "png", 753, 367, p(.34f, .89f, .50f), p(.68f, .70f, .36f), crisp = false),
        // Adam's batch 1 (1264 x 848 WebP, soft)
        own("night-alley", "webp", 1264, 848, p(.38f, .80f, .44f), p(.685f, .69f, .30f), crisp = false, dark = true),
        own("dragon-peak", "webp", 1264, 848, p(.30f, .85f, .44f), p(.79f, .61f, .32f), crisp = false),
        own("deep-sea-cave", "webp", 1264, 848, p(.32f, .82f, .44f), p(.71f, .60f, .32f), crisp = false, dark = true),
        own("night-forest", "webp", 1264, 848, p(.35f, .78f, .44f), p(.74f, .59f, .32f), crisp = false, dark = true),
        own("factory", "webp", 1264, 848, p(.31f, .82f, .44f), p(.72f, .66f, .32f), crisp = false),
        own("snow-field", "webp", 1264, 848, p(.30f, .81f, .44f), p(.82f, .60f, .32f), crisp = false),
        own("graveyard", "webp", 1264, 848, p(.32f, .81f, .44f), p(.755f, .62f, .32f), crisp = false, dark = true),
        own("dojo", "webp", 1264, 848, p(.33f, .82f, .44f), p(.77f, .615f, .32f), crisp = false),
        own("power-plant", "webp", 1264, 848, p(.33f, .82f, .44f), p(.785f, .625f, .32f), crisp = false),
        own("crystal-cave", "webp", 1264, 848, p(.33f, .78f, .44f), p(.74f, .62f, .32f), crisp = false),
        // Adam's batch 2 (WebP, soft; 1264 x 848 or 1376 x 768). legendary-shrine is in no type list: only a Legendary / Mythical encounter gets it
        own("canyon", "webp", 1375, 768, p(.33f, .74f, .46f), p(.73f, .47f, .33f), crisp = false),
        own("cliff-clouds", "webp", 1376, 768, p(.30f, .76f, .46f), p(.80f, .53f, .33f), crisp = false),
        own("crystal-storm", "webp", 1264, 848, p(.33f, .70f, .44f), p(.77f, .48f, .32f), crisp = false, dark = true),
        own("flower-field", "webp", 1264, 848, p(.30f, .79f, .44f), p(.74f, .48f, .32f), crisp = false),
        own("haunted-mansion", "webp", 1264, 848, p(.30f, .79f, .44f), p(.695f, .605f, .30f), crisp = false, dark = true),
        own("jungle", "webp", 1264, 848, p(.30f, .83f, .44f), p(.85f, .625f, .32f), crisp = false),
        own("legendary-shrine", "webp", 1376, 768, p(.25f, .77f, .46f), p(.50f, .59f, .30f), crisp = false),
        own("mine", "webp", 1264, 848, p(.32f, .72f, .44f), p(.76f, .50f, .32f), crisp = false, dark = true),
        own("ruined-castle", "webp", 1264, 848, p(.30f, .80f, .44f), p(.75f, .62f, .32f), crisp = false, dark = true),
        own("snow-aurora", "webp", 1376, 768, p(.32f, .79f, .46f), p(.79f, .58f, .33f), crisp = false, dark = true),
        own("stormy-plains", "webp", 1264, 848, p(.30f, .76f, .44f), p(.74f, .49f, .32f), crisp = false, dark = true),
        own("sunset-beach", "webp", 1376, 768, p(.30f, .76f, .46f), p(.80f, .53f, .33f), crisp = false),
    )

    private val BY_ID: Map<String, Backdrop> = ALL.associateBy { it.id }

    fun byId(id: String): Backdrop? = BY_ID[id]

    private val OPTIONS: Map<Type, List<String>> = mapOf(
        Type.NORMAL to listOf("meadow", "route", "garden", "flower-field", "sunset-beach"),
        Type.FIRE to listOf("volcanocave", "desert", "volcano-b", "volcano"),
        Type.WATER to listOf("beach", "river", "beachshore", "reef", "deep-sea-cave", "sunset-beach"),
        Type.GRASS to listOf("forest", "meadow", "garden", "flower-field", "jungle"),
        Type.ELECTRIC to listOf("city", "route", "power-plant", "crystal-storm", "stormy-plains"),
        Type.ICE to listOf("icecave", "snow-field", "snow-aurora"),
        Type.FIGHTING to listOf("mountain", "route", "rocky-path", "dojo"),
        Type.POISON to listOf("dampcave", "forest", "poison-swamp"),
        Type.GROUND to listOf("desert", "earthycave", "rocky-path", "mine", "canyon"),
        Type.FLYING to listOf("mountain", "meadow", "sky", "cliff-clouds"),
        Type.PSYCHIC to listOf("city", "forest", "temple", "crystal-cave"),
        Type.BUG to listOf("forest", "meadow", "night-forest", "jungle"),
        Type.ROCK to listOf("mountain", "earthycave", "rocky-path", "crystal-cave", "crystal-storm", "mine"),
        Type.GHOST to listOf("dampcave", "temple", "night-forest", "graveyard", "haunted-mansion", "ruined-castle"),
        Type.DRAGON to listOf("mountain", "volcanocave", "temple", "dragon-peak", "canyon"),
        Type.DARK to listOf("night-alley", "night-forest", "ruined-castle"),
        Type.STEEL to listOf("city", "earthycave", "factory", "mine"),
        Type.FAIRY to listOf("meadow", "forest", "sky", "garden", "flower-field"),
    )

    /** The pictures a type can get, in the order [pick] uses. */
    fun options(type: Type): List<Backdrop> = OPTIONS.getValue(type).map { BY_ID.getValue(it) }

    /** The type the picture follows: the first, or the second when the first is Normal and there is one; Normal when there is none. */
    fun typeFor(types: List<Type>): Type {
        val first = types.firstOrNull() ?: return Type.NORMAL
        return if (first == Type.NORMAL) types.getOrNull(1) ?: Type.NORMAL else first
    }

    /** The special arena of a Legendary / Mythical encounter; in no type list. */
    const val SHRINE = "legendary-shrine"

    /** True for a Legendary / Mythical encounter (by tier, so Ankimon's extra legendaries count; Ultra Beasts don't). */
    fun isLegendary(sp: Species?): Boolean = sp != null && Encounters.tierOf(sp).let { it == Tier.LEGENDARY || it == Tier.MYTHICAL }

    /** The picture for a wild Pokémon with [types]; [legendary] always gives the shrine; [seed] is fixed for one encounter (the wild one's uid hash), so the fight keeps it. */
    fun pick(types: List<Type>, seed: Int, legendary: Boolean = false): Backdrop {
        if (legendary) return BY_ID.getValue(SHRINE)
        val options = options(typeFor(types))
        return options[Math.floorMod(seed, options.size)]
    }

    /** The seed of one encounter from the wild Pokémon's uid (empty uid: 0). */
    fun seed(uid: String): Int = uid.hashCode()
}
