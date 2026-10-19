package com.husarp.ankigo.ui

import com.husarp.ankigo.game.Encounters
import com.husarp.ankigo.game.Species
import com.husarp.ankigo.game.Tier
import com.husarp.ankigo.game.Type

/**
 * Round 8 battle backgrounds (pure, JVM-tested): the 35 pictures (round 9 dropped 17 of the 43, then added 3 + 5 + 1), their stand points and which types get them. 12 are Pokémon
 * Showdown's, 23 are Adam's own art (batch 1 and 2, sharpened in round 9; batch 3: the legendary shrine, ruins-courtyard and dark-town; batch 4: dark-forest, fire-savanna, ice-lake, fighting-training, poison-marsh; wave 5: psychic-lab). Stand points: the STAGE table of docs/design/backgrounds-stage.html (Showdown, Adam 3:2 and wide) and
 * backgrounds-stage-r8.html (batches 1 and 2, measured for round 8). A wild Pokémon gets one of its type's pictures at random per encounter,
 * the same for the whole fight ([pick] with a seed from the wild one's uid), also after a restart or a redraw from the save.
 */
internal object BackdropCatalog {
    private fun p(x: Float, y: Float, s: Float) = StandPoint(x, y, s)

    private fun showdown(id: String, w: Int, player: StandPoint, wild: StandPoint) =
        Backdrop(id, BackdropSource.SHOWDOWN, "png", w, 500, player, wild, crisp = true, dark = false)

    private fun own(id: String, ext: String, w: Int, h: Int, player: StandPoint, wild: StandPoint, crisp: Boolean, dark: Boolean = false, rev: Int = 1) =
        Backdrop(id, BackdropSource.OWN, ext, w, h, player, wild, crisp, dark, rev)

    val ALL: List<Backdrop> = listOf(
        // Pokémon Showdown (753 x 500, beach 700 x 500), pixel art
        showdown("beach", 700, p(.30f, .86f, .44f), p(.68f, .58f, .32f)),
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
        // Adam's batch 1 (1264 x 848 WebP, soft), sharpened in round 9 (rev 2; night-forest retouched + sharpened = rev 3)
        own("dragon-peak", "webp", 1264, 848, p(.30f, .85f, .44f), p(.79f, .61f, .32f), crisp = false, rev = 2),
        own("deep-sea-cave", "webp", 1264, 848, p(.30f, .815f, .44f), p(.80f, .615f, .32f), crisp = false, dark = true, rev = 2),
        own("night-forest", "webp", 1264, 848, p(.35f, .78f, .44f), p(.74f, .59f, .32f), crisp = false, dark = true, rev = 3),
        own("factory", "webp", 1264, 848, p(.31f, .82f, .44f), p(.72f, .66f, .32f), crisp = false, rev = 2),
        // Adam's batch 2 (WebP, soft; 1264 x 848 or 1376 x 768), sharpened in round 9 (rev 2)
        own("canyon", "webp", 1375, 768, p(.33f, .74f, .46f), p(.73f, .47f, .33f), crisp = false, rev = 2),
        own("cliff-clouds", "webp", 1376, 768, p(.30f, .76f, .46f), p(.80f, .53f, .33f), crisp = false, rev = 2),
        own("crystal-storm", "webp", 1264, 848, p(.33f, .70f, .44f), p(.77f, .48f, .32f), crisp = false, dark = true, rev = 2),
        own("flower-field", "webp", 1264, 848, p(.30f, .79f, .44f), p(.74f, .48f, .32f), crisp = false, rev = 2),
        own("haunted-mansion", "webp", 1264, 848, p(.30f, .79f, .44f), p(.695f, .605f, .30f), crisp = false, dark = true, rev = 2),
        own("jungle", "webp", 1264, 848, p(.30f, .83f, .44f), p(.85f, .625f, .32f), crisp = false, rev = 2),
        own("mine", "webp", 1264, 848, p(.32f, .72f, .44f), p(.76f, .50f, .32f), crisp = false, dark = true, rev = 2),
        own("snow-aurora", "webp", 1376, 768, p(.32f, .79f, .46f), p(.79f, .58f, .33f), crisp = false, dark = true, rev = 2),
        own("stormy-plains", "webp", 1264, 848, p(.30f, .76f, .44f), p(.74f, .49f, .32f), crisp = false, dark = true, rev = 2),
        own("sunset-beach", "webp", 1376, 768, p(.30f, .76f, .46f), p(.80f, .53f, .33f), crisp = false, rev = 2),
        // Adam's batch 3 (1024 x 687 WebP, de-dithered where needed and sharpened; stand points checked in every arena shape with the design page).
        // The shrine is in no type list: only a Legendary / Mythical encounter gets it (rev 2 = the new picture; the dropped round-8 one was rev 1).
        own(SHRINE, "webp", 1024, 687, p(.30f, .88f, .44f), p(.72f, .70f, .32f), crisp = false, rev = 2),
        own("ruins-courtyard", "webp", 1024, 687, p(.30f, .84f, .44f), p(.71f, .62f, .32f), crisp = false),
        own("dark-town", "webp", 1024, 687, p(.30f, .86f, .44f), p(.68f, .60f, .32f), crisp = false, dark = true),
        // Adam's batch 4 (round 9 wave 4; 1024 x 687 WebP, sharpened; the checker de-dithered in the fighting-training and poison-marsh skies). Poison-marsh: the two feet stand on its two dark ovals.
        own("dark-forest", "webp", 1024, 687, p(.28f, .84f, .44f), p(.70f, .58f, .32f), crisp = false, dark = true),
        own("fire-savanna", "webp", 1024, 687, p(.30f, .84f, .44f), p(.66f, .64f, .32f), crisp = false),
        own("ice-lake", "webp", 1024, 687, p(.30f, .86f, .44f), p(.68f, .62f, .32f), crisp = false),
        own("fighting-training", "webp", 1024, 687, p(.30f, .84f, .44f), p(.64f, .58f, .32f), crisp = false),
        own("poison-marsh", "webp", 1024, 687, p(.28f, .80f, .44f), p(.73f, .56f, .32f), crisp = false),
        // Round 9 wave 5: the psychic lab (1024 x 687 WebP, sharpened, clean sky/floor so not de-dithered). Dim purple interior = a night-type picture (dark: no extra dusk tint, mean luma 64 of 255).
        // Feet on the clear floor: the lower-left shelf/cart corner (x < .37, y > .75) and the cables/pedestal/tables stay free.
        own("psychic-lab", "webp", 1024, 687, p(.42f, .74f, .44f), p(.62f, .56f, .32f), crisp = false, dark = true),
    )

    private val BY_ID: Map<String, Backdrop> = ALL.associateBy { it.id }

    fun byId(id: String): Backdrop? = BY_ID[id]

    private val OPTIONS: Map<Type, List<String>> = mapOf(
        Type.NORMAL to listOf("meadow", "route", "flower-field", "sunset-beach"),
        Type.FIRE to listOf("volcanocave", "desert", "fire-savanna"),
        Type.WATER to listOf("beach", "river", "deep-sea-cave", "sunset-beach"),
        Type.GRASS to listOf("forest", "meadow", "flower-field", "jungle"),
        Type.ELECTRIC to listOf("city", "route", "crystal-storm", "stormy-plains"),
        Type.ICE to listOf("icecave", "snow-aurora", "ice-lake"),
        Type.FIGHTING to listOf("mountain", "route", "ruins-courtyard", "fighting-training"),
        Type.POISON to listOf("dampcave", "forest", "poison-marsh"),
        Type.GROUND to listOf("desert", "earthycave", "mine", "canyon", "fighting-training"),
        Type.FLYING to listOf("mountain", "meadow", "cliff-clouds"),
        Type.PSYCHIC to listOf("city", "forest", "ruins-courtyard", "psychic-lab"),
        Type.BUG to listOf("forest", "meadow", "night-forest", "jungle"),
        Type.ROCK to listOf("mountain", "earthycave", "crystal-storm", "mine", "ruins-courtyard"),
        Type.GHOST to listOf("dampcave", "night-forest", "haunted-mansion"),
        Type.DRAGON to listOf("mountain", "volcanocave", "dragon-peak", "canyon"),
        Type.DARK to listOf("night-forest", "dark-town", "dark-forest"),
        Type.STEEL to listOf("city", "earthycave", "factory", "mine"),
        Type.FAIRY to listOf("meadow", "forest", "flower-field"),
    )

    /** The pictures a type can get, in the order [pick] uses. */
    fun options(type: Type): List<Backdrop> = OPTIONS.getValue(type).map { BY_ID.getValue(it) }

    /** The type the picture follows: the first, or the second when the first is Normal and there is one; Normal when there is none. */
    fun typeFor(types: List<Type>): Type {
        val first = types.firstOrNull() ?: return Type.NORMAL
        return if (first == Type.NORMAL) types.getOrNull(1) ?: Type.NORMAL else first
    }

    /**
     * The special arena of a Legendary / Mythical encounter; in no type list. Round 9: the old shrine picture was dropped (D10), the new one
     * (rev 2, batch 3) is in [ALL] again; without a catalog entry [pick] would give a Legendary its type's pictures.
     */
    const val SHRINE = "legendary-shrine"

    /** True for a Legendary / Mythical encounter (by tier, so Ankimon's extra legendaries count; Ultra Beasts don't). */
    fun isLegendary(sp: Species?): Boolean = sp != null && Encounters.tierOf(sp).let { it == Tier.LEGENDARY || it == Tier.MYTHICAL }

    /** The picture for a wild Pokémon with [types]; [legendary] gives the shrine when the catalog has one (round 9: the new one, rev 2); [seed] is fixed for one encounter (the wild one's uid hash), so the fight keeps it. */
    fun pick(types: List<Type>, seed: Int, legendary: Boolean = false): Backdrop {
        if (legendary) BY_ID[SHRINE]?.let { return it }
        val options = options(typeFor(types))
        return options[Math.floorMod(seed, options.size)]
    }

    /** The seed of one encounter from the wild Pokémon's uid (empty uid: 0). */
    fun seed(uid: String): Int = uid.hashCode()
}
