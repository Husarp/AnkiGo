package com.husarp.ankigo.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ROUND 10 W7, the info box looks as pure numbers: the choice ([InfoBoxLook]), what each look paints ([InfoBoxSkin], the numbers of the
 * design page's `.bx.sk-*` skins) in light and dark, the dark palette against the real `values-night/colors.xml`, and the glass copy of a
 * picture ([BackdropBlur.glass]).
 */
class InfoBoxLookTest {
    private val card = 0xFFFFFFFF.toInt()
    private val darkCard = 0xFF262637.toInt()
    private val blue = 0xFF6390F0.toInt()

    private fun a(c: Int) = c ushr 24
    private fun rgb(c: Int) = c and 0xFFFFFF

    // ---------------------------------------------------------------- the choice

    @Test fun theChoiceParsesByNameAndAnythingElseIsTheCardLook() {
        assertEquals(InfoBoxLook.TODAY, InfoBoxLook.DEFAULT)
        assertEquals("infoBoxLook", InfoBoxLook.KEY)
        assertEquals(InfoBoxLook.TODAY, InfoBoxLook.parse(null))
        assertEquals(InfoBoxLook.TODAY, InfoBoxLook.parse(""))
        assertEquals("a name from another version", InfoBoxLook.TODAY, InfoBoxLook.parse("RIBBON"))
        assertEquals("names are exact", InfoBoxLook.TODAY, InfoBoxLook.parse("glass"))
        for (l in InfoBoxLook.entries) assertEquals(l, InfoBoxLook.parse(l.name))
        // the six looks Adam picked, none of the two he did not (D8-D frames that match the background, D8-F two-piece)
        assertEquals(listOf("TODAY", "GLASS", "FRAME", "PLATE", "HUD", "TYPE"), InfoBoxLook.entries.map { it.name })
        assertEquals("Card", InfoBoxLook.TODAY.label)
        assertEquals("Thin frame", InfoBoxLook.FRAME.label)
        assertEquals("Dark plate", InfoBoxLook.PLATE.label)
        assertEquals("HUD in the corners", InfoBoxLook.HUD.label)
        assertEquals("Type colour", InfoBoxLook.TYPE.label)
        for (l in InfoBoxLook.entries) assertTrue("${l.name} has a line of help", l.sub.length > 10)
    }

    // ---------------------------------------------------------------- which looks do what

    @Test fun theFrameThePlateAndTheHudAreTheSameInLightAndDark() {
        assertEquals(setOf(InfoBoxLook.FRAME, InfoBoxLook.PLATE, InfoBoxLook.HUD), InfoBoxLook.entries.filter { InfoBoxSkin.fixedDark(it) }.toSet())
        for (l in InfoBoxLook.entries) {
            if (InfoBoxSkin.fixedDark(l)) assertNotNull("${l.name} paints its inside itself", InfoBoxSkin.ink(l))
            else assertNull("${l.name} keeps the theme's colours", InfoBoxSkin.ink(l))
        }
        // their background layers do not depend on the theme
        for (l in listOf(InfoBoxLook.FRAME, InfoBoxLook.PLATE, InfoBoxLook.HUD)) {
            assertEquals(describe(InfoBoxSkin.layers(l, card, false, blue)), describe(InfoBoxSkin.layers(l, darkCard, true, blue)))
        }
    }

    @Test fun onlyTheHudHasNoBoxAndOnlyTheHudGoesToTheCorners() {
        for (l in InfoBoxLook.entries) {
            assertEquals(l.name, l != InfoBoxLook.HUD, InfoBoxSkin.hasBox(l))
            assertEquals(l.name, l == InfoBoxLook.HUD, InfoBoxSkin.corners(l))
            assertEquals(l.name, l == InfoBoxLook.HUD, InfoBoxSkin.layers(l, card, false, blue).isEmpty())
            assertEquals(l.name, l == InfoBoxLook.GLASS, InfoBoxSkin.glass(l))
            assertEquals(l.name, l == InfoBoxLook.TYPE, InfoBoxSkin.needsType(l))
        }
    }

    @Test fun onlyTheGlassCastsADropShadowAndOnlyThePlateChangesTheRadius() {
        for (l in InfoBoxLook.entries) {
            assertEquals(l.name, if (l == InfoBoxLook.GLASS) 3f else 0f, InfoBoxSkin.elevationDp(l), 0f)
            assertEquals(l.name, if (l == InfoBoxLook.PLATE) 9f else 12f, InfoBoxSkin.radiusDp(l, 12f), 0f)
            assertEquals(l.name, if (l == InfoBoxLook.PLATE) 9f else 10f, InfoBoxSkin.radiusDp(l, 10f), 0f)
        }
    }

    // ---------------------------------------------------------------- the layers (docs/design/round10.html, CSS lines 43-52)

    private fun describe(ls: List<InfoBoxSkin.Layer>) = ls.joinToString(";") { "${it.kind}:${Integer.toHexString(it.color)}:${Integer.toHexString(it.color2)}:${it.widthDp}:${it.insetDp}" }

    @Test fun todayIsTheCardColourAt92Percent() {
        val l = InfoBoxSkin.layers(InfoBoxLook.TODAY, card, false, null)
        assertEquals(1, l.size)
        assertEquals(InfoBoxSkin.Kind.FILL, l[0].kind)
        assertEquals(0xEB, a(l[0].color))
        assertEquals(0xFFFFFF, rgb(l[0].color))
        val d = InfoBoxSkin.layers(InfoBoxLook.TODAY, darkCard, true, null)
        assertEquals(rgb(darkCard), rgb(d[0].color))
        assertEquals("92 %", 0xEB, (0.92f * 255f + 0.5f).toInt())
    }

    @Test fun glassIsTheBlurUnderTheCardAt40PercentAndAThinWhiteEdge() {
        for ((c, dark) in listOf(card to false, darkCard to true)) {
            val l = InfoBoxSkin.layers(InfoBoxLook.GLASS, c, dark, null)
            assertEquals(listOf(InfoBoxSkin.Kind.BLUR, InfoBoxSkin.Kind.FILL, InfoBoxSkin.Kind.EDGE), l.map { it.kind })
            assertEquals("ag_card at 40 %", (0.4f * 255f + 0.5f).toInt(), a(l[1].color))
            assertEquals(rgb(c), rgb(l[1].color))
            assertEquals("a 1 dp edge", 1f, l[2].widthDp, 0f)
            assertEquals("white", 0xFFFFFF, rgb(l[2].color))
            assertEquals("at 50 %", (0.5f * 255f + 0.5f).toInt(), a(l[2].color))
            assertEquals("just inside the edge", 0f, l[2].insetDp, 0f)
        }
        // the glass keeps the theme's text colours
        assertNull(InfoBoxSkin.ink(InfoBoxLook.GLASS))
    }

    @Test fun theFrameIsADarkVeilFrom42To22PercentAndA1Point5DpWhiteFrame() {
        val l = InfoBoxSkin.layers(InfoBoxLook.FRAME, card, false, null)
        assertEquals(listOf(InfoBoxSkin.Kind.GRADIENT, InfoBoxSkin.Kind.EDGE), l.map { it.kind })
        assertEquals(0x0A0C18, rgb(l[0].color)); assertEquals(0x0A0C18, rgb(l[0].color2))
        assertEquals((0.42f * 255f + 0.5f).toInt(), a(l[0].color))
        assertEquals((0.22f * 255f + 0.5f).toInt(), a(l[0].color2))
        assertTrue("fades down", a(l[0].color) > a(l[0].color2))
        assertEquals(1.5f, l[1].widthDp, 0f)
        assertEquals(0xFFFFFF, rgb(l[1].color))
        assertEquals((0.88f * 255f + 0.5f).toInt(), a(l[1].color))
        val ink = InfoBoxSkin.ink(InfoBoxLook.FRAME)!!
        assertEquals(0xFFFFFFFF.toInt(), ink.text)
        assertEquals(0xFFE8EAF6.toInt(), ink.sub)
        assertEquals("rgba(0,0,0,.45)", 0x000000, rgb(ink.track)); assertEquals((0.45f * 255f + 0.5f).toInt(), a(ink.track))
        val sh = InfoBoxSkin.textShadow(InfoBoxLook.FRAME)!!
        assertEquals("0 1px 2px", 1f, sh.dyDp, 0f); assertEquals(2f, sh.radiusDp, 0f); assertEquals(0f, sh.dxDp, 0f)
        assertEquals("rgba(0,0,0,.7)", (0.7f * 255f + 0.5f).toInt(), a(sh.color))
    }

    @Test fun thePlateIsNavyWithTheGoldTrimInThreeBands() {
        val l = InfoBoxSkin.layers(InfoBoxLook.PLATE, card, false, null)
        assertEquals(listOf(InfoBoxSkin.Kind.FILL, InfoBoxSkin.Kind.EDGE, InfoBoxSkin.Kind.EDGE, InfoBoxSkin.Kind.EDGE), l.map { it.kind })
        assertEquals(0xFF1F2537.toInt(), l[0].color)
        // 2 dp gold, then 1 dp navy, then 1 dp gold at 35 % (CSS: inset 0 0 0 2px / 3px / 4px)
        assertEquals(0xFFD6B052.toInt(), l[1].color); assertEquals(2f, l[1].widthDp, 0f); assertEquals(0f, l[1].insetDp, 0f)
        assertEquals(0xFF1F2537.toInt(), l[2].color); assertEquals(1f, l[2].widthDp, 0f); assertEquals(2f, l[2].insetDp, 0f)
        assertEquals(0xD6B052, rgb(l[3].color)); assertEquals((0.35f * 255f + 0.5f).toInt(), a(l[3].color)); assertEquals(1f, l[3].widthDp, 0f); assertEquals(3f, l[3].insetDp, 0f)
        val ink = InfoBoxSkin.ink(InfoBoxLook.PLATE)!!
        assertEquals(0xFFF3F3F7.toInt(), ink.text)
        assertEquals(0xFFC3CBE2.toInt(), ink.sub)
        assertEquals(0xFF3B4460.toInt(), ink.track)
        assertEquals("HP green", 0xFF5FD35A.toInt(), ink.hp(0.9f))
        assertEquals("HP yellow", 0xFFF2C94C.toInt(), ink.hp(0.4f))
        assertEquals("HP red is the dark palette's", InfoBoxSkin.DARK_RED, ink.hp(0.1f))
        assertEquals("XP sky", 0xFF5AB8FF.toInt(), ink.xp)
    }

    @Test fun theHudHasNoBoxButAStrongOutlineAndARingRoundTheBars() {
        assertTrue(InfoBoxSkin.layers(InfoBoxLook.HUD, card, false, null).isEmpty())
        val ink = InfoBoxSkin.ink(InfoBoxLook.HUD)!!
        assertEquals(0xFFFFFFFF.toInt(), ink.text)
        assertEquals(0xFFF0F0F8.toInt(), ink.sub)
        assertEquals("rgba(0,0,0,.5)", (0.5f * 255f + 0.5f).toInt(), a(ink.track))
        val sh = InfoBoxSkin.textShadow(InfoBoxLook.HUD)!!
        assertEquals("a black outline, fully opaque", 0xFF000000.toInt(), sh.color)
        assertTrue("stronger than the frame's", sh.radiusDp >= InfoBoxSkin.textShadow(InfoBoxLook.FRAME)!!.radiusDp)
        val ring = InfoBoxSkin.barRing(InfoBoxLook.HUD)!!
        assertEquals(1f, ring.widthDp, 0f)
        assertEquals("white 75 %", 0xFFFFFF, rgb(ring.color)); assertEquals((0.75f * 255f + 0.5f).toInt(), a(ring.color))
        for (l in InfoBoxLook.entries) if (l != InfoBoxLook.HUD) assertNull("${l.name} has no bar ring", InfoBoxSkin.barRing(l))
        for (l in listOf(InfoBoxLook.TODAY, InfoBoxLook.GLASS, InfoBoxLook.PLATE, InfoBoxLook.TYPE)) assertNull("${l.name} has no text shadow", InfoBoxSkin.textShadow(l))
    }

    @Test fun theTypeLookWashesTheCardWithTheTypeColourAndAStripe() {
        // light: 22 % of the type colour into ag_card, dark: 30 %; both at 93 %
        val light = InfoBoxSkin.layers(InfoBoxLook.TYPE, card, false, blue)
        val dark = InfoBoxSkin.layers(InfoBoxLook.TYPE, darkCard, true, blue)
        assertEquals(listOf(InfoBoxSkin.Kind.FILL, InfoBoxSkin.Kind.STRIPE), light.map { it.kind })
        assertEquals(rgb(TypeInk.mix(card, blue, 0.22f)), rgb(light[0].color))
        assertEquals(rgb(TypeInk.mix(darkCard, blue, 0.30f)), rgb(dark[0].color))
        assertEquals((0.93f * 255f + 0.5f).toInt(), a(light[0].color))
        assertEquals((0.93f * 255f + 0.5f).toInt(), a(dark[0].color))
        // the stripe: 4 dp of the type colour, opaque
        assertEquals(4f, light[1].widthDp, 0f)
        assertEquals(blue, light[1].color)
        assertEquals(blue, dark[1].color)
        // the washes differ with the colour: each box its own Pokémon's type
        val red = 0xFFEE8130.toInt()
        assertTrue(light[0].color != InfoBoxSkin.layers(InfoBoxLook.TYPE, card, false, red)[0].color)
        // a Pokémon without a type is the plain card
        assertEquals(describe(InfoBoxSkin.layers(InfoBoxLook.TODAY, card, false, null)), describe(InfoBoxSkin.layers(InfoBoxLook.TYPE, card, false, null)))
        assertNull("the type look keeps the theme's text colours", InfoBoxSkin.ink(InfoBoxLook.TYPE))
    }

    // ---------------------------------------------------------------- the dark palette

    @Test fun theFixedLooksUseTheRealDarkPalette() {
        val xml = File(System.getProperty("user.dir"), "src/main/res/values-night/colors.xml").readText()
        fun color(name: String): Int {
            val m = Regex("""<color name="$name">#([0-9a-fA-F]{6})</color>""").find(xml) ?: throw AssertionError("$name is not in values-night/colors.xml")
            return (0xFF000000L or m.groupValues[1].toLong(16)).toInt()
        }
        assertEquals(color("ag_text"), InfoBoxSkin.DARK_TEXT)
        assertEquals(color("ag_sub0"), InfoBoxSkin.DARK_SUB0)
        assertEquals(color("ag_red"), InfoBoxSkin.DARK_RED)
        assertEquals(color("ag_peach"), InfoBoxSkin.DARK_PEACH)
        assertEquals(color("ag_yellow"), InfoBoxSkin.DARK_YELLOW)
        assertEquals(color("ag_green"), InfoBoxSkin.DARK_GREEN)
        assertEquals(color("ag_sky"), InfoBoxSkin.DARK_SKY)
        assertEquals(color("ag_lav"), InfoBoxSkin.DARK_LAV)
        assertEquals(color("ag_mauve"), InfoBoxSkin.DARK_MAUVE)
        assertEquals(color("ag_card"), InfoBoxSkin.DARK_CARD)
        for (i in 0 until 10) assertEquals("tier $i", color("ag_tier_$i"), InfoBoxSkin.DARK_TIERS[i])
        // and the chips of those looks come from the dark side of TypeInk (the card they are washed onto is this card)
        assertEquals(InfoBoxSkin.DARK_CARD, 0xFF262637.toInt())
    }

    @Test fun theLevelTiersFollowTheGameTiersAndTheHpColoursTheHpRule() {
        for (level in listOf(1, 9, 10, 19, 50, 59, 89, 90, 100, 150, 0, -5)) assertEquals("level $level", LevelTier.index(level), InfoBoxSkin.tierIndex(level))
        val ink = InfoBoxSkin.ink(InfoBoxLook.FRAME)!!
        assertEquals(InfoBoxSkin.DARK_TIERS[0], ink.tier(5))
        assertEquals(InfoBoxSkin.DARK_TIERS[5], ink.tier(54))
        assertEquals(InfoBoxSkin.DARK_TIERS[9], ink.tier(100))
        // the same cut-offs as Ui.hpColor: above 50 % green, above 20 % yellow, else red
        assertEquals(InfoBoxSkin.DARK_GREEN, ink.hp(1f)); assertEquals(InfoBoxSkin.DARK_GREEN, ink.hp(0.51f))
        assertEquals(InfoBoxSkin.DARK_YELLOW, ink.hp(0.5f)); assertEquals(InfoBoxSkin.DARK_YELLOW, ink.hp(0.21f))
        assertEquals(InfoBoxSkin.DARK_RED, ink.hp(0.2f)); assertEquals(InfoBoxSkin.DARK_RED, ink.hp(0f))
        assertEquals(InfoBoxSkin.DARK_SKY, ink.xp)
    }

    @Test fun theStatusBadgesHaveTheDarkPalettesColours() {
        assertEquals(InfoBoxSkin.DARK_MAUVE, InfoBoxSkin.statusFill("psn"))
        assertEquals(InfoBoxSkin.DARK_MAUVE, InfoBoxSkin.statusFill("tox"))
        assertEquals(InfoBoxSkin.DARK_PEACH, InfoBoxSkin.statusFill("brn"))
        assertEquals(InfoBoxSkin.DARK_YELLOW, InfoBoxSkin.statusFill("par"))
        assertEquals(InfoBoxSkin.DARK_LAV, InfoBoxSkin.statusFill("slp"))
        assertEquals(InfoBoxSkin.DARK_SKY, InfoBoxSkin.statusFill("frz"))
    }

    // ---------------------------------------------------------------- the glass copy of a picture

    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun theGlassCopyIsNotDarkenedAndIsMoreColourfulThanThePicture() {
        // a flat grey stays exactly that grey (the panel's blur would be at 55 %)
        val grey = BackdropBlur.glass(IntArray(24 * 16) { argb(255, 200, 200, 200) }, 24, 16)
        for (p in grey) assertTrue("grey ${r(p)}", Math.abs(200 - r(p)) <= 1 && Math.abs(200 - g(p)) <= 1 && Math.abs(200 - b(p)) <= 1)
        // a colour is pushed away from its grey by the glass saturation 1.35, at the picture's own brightness
        val c = BackdropBlur.glass(IntArray(8 * 8) { argb(255, 200, 100, 50) }, 8, 8)[0]
        assertEquals(1.35f, BackdropBlur.GLASS_SATURATION, 0f)
        assertEquals((200 - 50) * 1.35f, (r(c) - b(c)).toFloat(), 2f)
        val panel = BackdropBlur.process(IntArray(8 * 8) { argb(255, 200, 100, 50) }, 8, 8)[0]
        assertTrue("brighter than the panel's darkened blur", r(c) > r(panel) + 40)
    }

    @Test fun theGlassBlursLessThanThePanelFillAndKeepsAlpha() {
        val w = 40; val h = 6
        val px = IntArray(w * h) { if (it % w < w / 2) argb(255, 0, 0, 0) else argb(255, 255, 255, 255) }
        val glass = BackdropBlur.glass(px, w, h)
        val panel = BackdropBlur.grade(BackdropBlur.blur(px, w, h), 1f, 1f)
        var gw = 0; var pw = 0   // how many columns of the row are neither black nor white after the blur
        for (x in 0 until w) {
            if (r(glass[x]) in 3..252) gw++
            if (r(panel[x]) in 3..252) pw++
        }
        assertTrue("the glass edge is narrower ($gw) than the fill's ($pw)", gw in 1 until pw)
        assertTrue(glass.all { it ushr 24 == 255 })
        assertEquals(0, r(glass[0])); assertEquals(255, r(glass[w - 1]))
        assertFalse("not the same as the panel's copy", glass.contentEquals(BackdropBlur.process(px, w, h)))
    }
}
