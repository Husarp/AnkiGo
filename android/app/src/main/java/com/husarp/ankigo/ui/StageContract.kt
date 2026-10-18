package com.husarp.ankigo.ui

/*
 * Round 8 "Pokémon belong to the picture" (R8 contract, written by the manager; R8-02/03/04 build on it, don't change the
 * declarations). Every battle background has two stand points measured on the picture (docs/design/backgrounds-stage.html and
 * backgrounds-stage-r8.html). The panel crops the picture so both spots and the room above them stay in view, cutting sky and
 * edges first; when the panel's shape makes that impossible the picture shrinks and a blurred, darkened copy fills the rest.
 * The two Pokémon stand with their feet on the spots and take their size from the picture ([StageFit]); the stat cards go where
 * they cover neither Pokémon ([StageGeo]).
 */

/**
 * Where a Pokémon stands in a battle picture: its feet at ([x], [y]) as fractions of the picture's width and height, and [s] = the
 * height of its 96-px sprite frame as a share of the picture's height (the picture's own scale at that spot).
 */
class StandPoint(val x: Float, val y: Float, val s: Float)

/** Where a background's file comes from. */
enum class BackdropSource {
    /** Pokémon Showdown's `fx/bg-<id>.png` (through the AnkiGo mirror first, [com.husarp.ankigo.data.Mirror]). */
    SHOWDOWN,

    /** Adam's own art, a plain file `bg-<id>.<ext>` of AnkiGo's assets release ([com.husarp.ankigo.data.Mirror.ownUrl]); nothing else has it. */
    OWN,
}

/** One battle background ([BackdropCatalog]). */
class Backdrop(
    /** "forest", "night-alley": the picture's name; cached as `bg/<id>.<ext>` in the sprite folder. */
    val id: String,
    val source: BackdropSource,
    /** "png" or "webp". */
    val ext: String,
    /** The picture's size in pixels; [Stage] crops are in these pixels (a file of another size is scaled to them). */
    val w: Int,
    val h: Int,
    /** Our Pokémon (back sprite, near). */
    val player: StandPoint,
    /** The wild Pokémon (front sprite, far). */
    val wild: StandPoint,
    /** Pixel art on its own grid: drawn with nearest-neighbour scaling; false = smooth (filtered) scaling. */
    val crisp: Boolean,
    /** Already a dark (night) scene: the dark theme adds no dusk tint. */
    val dark: Boolean,
)

/**
 * A background laid into one panel ([StageFit.fit]). The crop is in picture pixels ([Backdrop.w] x [Backdrop.h]): the part of the
 * picture the panel shows, stretched over the whole panel. When [shrunk] the crop is wider and/or taller than the picture (negative
 * [cropX] / [cropY]): the picture is drawn smaller than the panel and a blurred, darkened copy fills the rest. Feet and frames are in
 * panel pixels: a Pokémon's feet touch ([playerFeetX], [playerFeetY]), its 96-px sprite frame is [playerFrame] high (the same for the wild one).
 */
class Stage(
    val cropX: Float, val cropY: Float, val cropW: Float, val cropH: Float,
    val shrunk: Boolean,
    val playerFeetX: Float, val playerFeetY: Float, val playerFrame: Float,
    val wildFeetX: Float, val wildFeetY: Float, val wildFrame: Float,
)

/**
 * The arena's layout in picture mode ([StageGeo.plan], virtual dp like [ArenaGeo.Plan]): the two sprite holders (square frames,
 * bottom = the feet, already shrunk by [spriteScale] when the cards needed the room), the two card boxes (on screen, [k] times
 * their normal size) and whether nothing overlaps ([clear]; false = the least-bad choice, a card may cover part of a Pokémon).
 */
internal class StagePlan(
    val wildSprite: ArenaGeo.Box,
    val playerSprite: ArenaGeo.Box,
    val wildCard: ArenaGeo.Box,
    val playerCard: ArenaGeo.Box,
    val k: Float,
    val spriteScale: Float,
    val clear: Boolean,
    /** True when both cards sit in the top row (ours top-right / top-middle), the arrangement tried when the usual rows found no room. */
    val bothTop: Boolean = false,
)

/**
 * A Pokémon's visible picture as shares of its square frame: [w] of the frame's width, [h] of its height, bottom-centred. What the
 * cards must stay clear of ([StageGeo.plan]); [DEFAULT] is the 85 % square [ArenaGeo.Box.art] always used.
 */
internal class ArtShare(val w: Float, val h: Float) {
    companion object {
        val DEFAULT = ArtShare(0.85f, 0.85f)
    }
}
