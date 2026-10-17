package com.husarp.ankigo.ui.movefx

/*
 * Pixel art for the move animations, drawn by us (docs/design/attack-anims-v2/v2-fx.js, v4): the rows are ported character for
 * character, so this is our own art and not taken from any game. One character = one art pixel, "." = clear, "/" = next row.
 * Pure data (no android.*); [FxArt] turns it into bitmaps.
 */
object FxPixelArt {
    /** The fireball palette: O orange, R red (underneath), M amber, Y yellow, W pale core. ARGB. */
    val FLAME_PALETTE: Map<Char, Int> = mapOf(
        'O' to 0xFFF1701C.toInt(), 'R' to 0xFFD64818.toInt(), 'M' to 0xFFFAA41A.toInt(), 'Y' to 0xFFFFD628.toInt(), 'W' to 0xFFFFF4AA.toInt(),
    )

    /** Ember embers, brightest first (pale, yellow, amber, orange). */
    val EMBER_COLORS: IntArray = intArrayOf(0xFFFFF4AA.toInt(), 0xFFFFD628.toInt(), 0xFFFAA41A.toInt(), 0xFFF1701C.toInt())

    /** Flame head centre inside a frame, in art pixels (x, y): the head is on the right, the tongues trail up-left. */
    const val FLAME_HEAD_X = 16f
    const val FLAME_HEAD_Y = 9.5f

    /** Flying fireball frames (22 x 16 art pixels each), head on the right (the direction of travel). Six big, six small. */
    val FLAME_BIG: List<String> = listOf(
        "....OOO.............../.....OMO............../.....OMMO............./......OYMO............/......OMYMOOOOOOO...../.......OYYYYYYYYMO..../.....M.OMYYYWWWWYMOO../.....OOOOOMMYYMWYYMO../.....OOMYYYMMYYMWYYMO./.......OOMYYYYYWMWYMO./.........OOMYYWWWMYMR./...........OMMYYYYYO../...........OMMYYYMMR../..........OMYYWWMRO.../.........OOOORROR...../......................",
        "...OOO................/....OMO.............../....OMMOO............./.....OMYMOOOO........./......OYYYMMMOOOO...../....O..OYYYYYYYYMOO.../.......OOMYYYWWWYMMO../.........OOMMYYWYYYMO./......OOOMMMMYYWWWYMO./......OOOMMYYYWWWWYMO./.........OOMYYWWWMYMO./...........OMMYYYYYMO./............OMYYYYMR../.........OOOMYWWMOR.../..........OOYRORR...../............O.........",
        "..OOO................./...OMOO.............../....OMMOOO............/.....OMYMMOOOO......../...O..OMYYYYYMOOO...../.......OOYYWWYYYMOO.../.........OMYYWWWYMMO../..........OMMYYWYYYMO./.......OOOOOMYYWWWYMO./.......OOYYYYYMWWWYMO./.........OMYYWWWWWYMO./..........OOMMYYYYYMR./........OOO.OMYYYYMO../.........OMOYWWWMRR.../..........OORORRO...../......................",
        "....................../....OOO.............../.....OMOOO............/......OMYMOOOO......../..O....OMYYYYMOOO...../........OMYYWYYYMO..../.........OOYYWWWYMOO../.......O...OMYYWYYMO../.......OOO..OMYWWYYMO./........OYOOMYMWWWYMO./.........OYYYWWMWWYMR./.........OMMYYYYYYYO../........OMMMYYYYYMMR../.........OOYYYWWMRO.../...........OORROR...../......................",
        "....................../.....OOO............../......OMO............./.......OMOO.........../.......OMYMOOOOOO...../........OMYYYYYYMO..../......O..OMYYWWWYMO.../......OOO.OOMYYWYYMO../.......OMO..OMYWWMYO../.......OMYOOMYYWMWYMO./........OMYYWWWMWWYMO./.........OMMYYYYYYMO../.........OYYYYYYMMRR../........OOOORYYWMO..../.............RORR...../......................",
        ".....OO.............../......OO............../......OMO............./.......OMO............/.......OMMOOOOOOO...../........OYYYYYYYMO..../....OO..OMYYWWWWYMO.../.....OOOOOMMYYYWYYMO../......OMMMMMMMYWWYYO../.......OMYYYYYYWWWYMO./........OOYYYWWWWWYMR./..........OMMYYYYYMO../..........OYYYYYMMRO../.........OYORYYWMR..../........OOO..ORRO...../......................",
    )
    /** Six smaller flame frames. */
    val FLAME_SMALL: List<String> = listOf(
        "....................../....................../......O.............../.........O............/.........OO.........../..........OO........../.....M....OMO........./...........OMOOOOO..../...........OYYWMWYO.../.........OOMMMMYMYYO../...........OOYYYWMYO../.............RYYWWYO../..............OMYYOR../.............RROYR..../................R...../......................",
        "....................../....................../....................../........O............./........OO............/....O....OO.........../..........OOOOO......./..........OYMYYOOO..../...........OMYYWWYOO../...........OOMMYYYYO../..........OORYYYWMYO../.............RMYWWYO../..............OMYYMR../............RRYWROR.../..............OR....../......................",
        "....................../....................../....................../....................../...O....OO............/.........OOO........../..........OYOOOO....../..........OOMYYYOO..../............ROYWWYOO../..............OYYYYO../...........OOOYYWWYO../............OOYYWWYO../............R.OMYYMO../.............OYYORR.../..............RR....../......................",
        "....................../....................../....................../....................../..O......M............/.........OOO........../..........OYOOOO....../...........OMYYYOO..../............ORYWWYO.../...........O..OYYYYO../...........OOOMMWWYO../............OYYWWWYO../...........OOMMYYYOR../.............RROYR..../................R...../......................",
        "....................../....................../....................../........O............./....................../..........OO........../..........OMOO......../...........OYYOOOO..../..........O.RYYWWMO.../..........OO.OMYMYYO../...........OOMMMWWYO../............OYYWWWYR../............ORYYYYR.../...........OR.ORYO..../................R...../......................",
        "....................../....................../.......O............../....................../..........O.........../..........OO........../...........OO........./...........OYOOOOO..../.........O..OYWWWYO.../..........OOOMMYYYYO../...........OYYYYWWYO../............RMYYWWYO../.............OYYYYR.../............RORRYR..../................O...../......................",
    )

    /** One frame string as rows. */
    fun rows(frame: String): List<String> = frame.split('/')

    /** A vine leaf pointing up-right (9 x 6), the Vine Whip look stamps it; the grass template flings it. */
    val LEAF_ROWS: List<String> = listOf("......ooo", "....oollo", "..oollmmo", ".olmmmdo.", "oldddoo..", ".ooo.....")

    /** Leaf palette: o outline, d dark, m mid, l light. ARGB. */
    val LEAF_PALETTE: Map<Char, Int> = mapOf('o' to 0xFF173D1A.toInt(), 'd' to 0xFF2B6A27.toInt(), 'm' to 0xFF3F9136.toInt(), 'l' to 0xFF69BD48.toInt())

    /** Pixel dirt bits (Vine Whip, grass / ground templates). */
    val DIRT_COLORS: IntArray = intArrayOf(0xFF7A5632.toInt(), 0xFF9A7448.toInt(), 0xFF5A3D22.toInt())

    /** Size of one art pixel in stage units at k = 1 (close to the Showdown sprites' own pixel). */
    const val ART_PX = 1.5f
}
