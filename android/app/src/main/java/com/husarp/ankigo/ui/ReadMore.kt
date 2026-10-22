package com.husarp.ankigo.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.husarp.ankigo.game.GameData
import com.husarp.ankigo.game.Species

/**
 * PLAN 183 (183A + 183S1): the "Read more on Bulbapedia" link under the Pokédex text. The page is built here (nothing is loaded
 * by the app) and opens in the browser when tapped. Pure part: [url] / [pageName]; the Android part: [open].
 */
object ReadMore {
    private const val BASE = "https://bulbapedia.bulbagarden.net/wiki/"

    /**
     * Bulbapedia's page title of a species as the app's data (Showdown names) spells it: spaces become "_", the curly apostrophe of
     * "Farfetch’d" / "Sirfetch’d" becomes a straight one, and the Nidorans take their gender signs. Everything else
     * (Mr. Mime, Type: Null, Flabébé, Ho-Oh, Porygon-Z, Tapu Koko, Great Tusk ...) is already spelled the way Bulbapedia does.
     */
    fun pageName(name: String): String {
        val n = name.trim().replace('’', '\'').replace('‘', '\'')
        val spelled = when (n.lowercase().replace(' ', '-')) {
            "nidoran-f" -> "Nidoran♀"
            "nidoran-m" -> "Nidoran♂"
            else -> n
        }
        return spelled.replace(' ', '_')
    }

    /** The page of the species called [name]: `…/wiki/<Name>_(Pokémon)`, with what a URI needs percent-encoded (é, ♀, ♂). */
    fun url(name: String): String = BASE + encode(pageName(name) + "_(Pokémon)")

    /** The page of [sp]: a regional form opens its base species' page (the species of the national number), never a nickname. */
    fun url(data: GameData, sp: Species): String = url(data.species[sp.baseNum]?.name ?: sp.name)

    /** What a path may keep as it is: letters, digits and these marks; the rest becomes UTF-8 %XX. */
    private fun encode(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-._~:'()!*,") sb.append(c)
            else sb.append('%').append("%02X".format(b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    /** Opens the page in the browser (new task when [ctx] is not an Activity: the floating window); no browser = a short toast. */
    fun open(ctx: Context, url: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (activity(ctx) == null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { starter(ctx, i) } catch (e: ActivityNotFoundException) { Ui.toast(ctx, "No browser to open the page") }
    }

    /** The launcher; the tests swap it. */
    internal var starter: (Context, Intent) -> Unit = { c, i -> c.startActivity(i) }

    private fun activity(c: Context): Activity? {
        var x: Context? = c
        while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }
        return null
    }
}
