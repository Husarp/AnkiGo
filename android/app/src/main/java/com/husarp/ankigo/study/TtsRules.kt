package com.husarp.ankigo.study

/**
 * Text-to-speech for `{{tts ...}}` cards, as pure rules (no android.*).
 *
 * The AnkiDroid API only returns `[anki:play:q:N]` for speech: the text and language are not in the HTML. So the card's
 * template and note fields are rendered here the way Anki does it (filters, cloze, FrontSide, conditionals) into
 * `[anki:tts ...]text[/anki:tts]` tags, and the k-th play reference of the page is bound to the k-th tag.
 */
object TtsRules {
    /** One thing to say: [text] in [lang] (BCP-47, e.g. `en-US`) at speech [rate] (1.0 = normal). */
    data class Item(val text: String, val lang: String, val rate: Float = 1f, val pitch: Float = 1f) {
        val blank get() = text.isBlank()
    }

    /** One entry of a page's audio list, in document order: a sound file or something to say. */
    data class Av(val file: String? = null, val speech: Item? = null)

    data class Template(val ord: Int, val question: String, val answer: String)

    /** Speech of both sides of a card, every slot in text order (a slot with blank text still counts for the binding). */
    data class Card(val question: List<Item>, val answer: List<Item>) {
        val languages get() = (question + answer).filter { !it.blank }.map { it.lang }.distinct()
    }

    val NONE = Card(emptyList(), emptyList())

    private val FLAGS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    /** `[anki:tts options]content[/anki:tts]`: group 1 = options, group 2 = content. */
    val TAG = Regex("""\[anki:tts\b([^\]]*)](.*?)\[/anki:tts]""", FLAGS)
    private val OPTION = Regex("""([^\s=\]"]+)=(?:"([^"]*)"|([^\s"\]]*))""")
    private val SOUND = Regex("""\[sound:[^\]]*]""")
    private val LINEBREAK = Regex("""</?(?:br|address|article|aside|blockquote|canvas|dd|div|dl|dt|fieldset|figcaption|figure|footer|form|h[1-6]|header|hr|li|main|nav|noscript|ol|output|p|pre|section|table|tfoot|ul|video)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val HTML = Regex("""<!--.*?-->|<style.*?>.*?</style>|<script.*?>.*?</script>|<.*?>""", FLAGS)
    private val ENTITY = Regex("""&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);""")
    private val FURIGANA = Regex(""" ?([^ >]+?)\[(.+?)]""")
    private val EMPTY_FIELD = Regex("""^(?:\s|</?(?:br|div) ?/?>)*$""", RegexOption.IGNORE_CASE)
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "hellip" to "…", "mdash" to "—", "ndash" to "–", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
        "copy" to "©", "reg" to "®", "deg" to "°", "euro" to "€", "pound" to "£", "yen" to "¥", "middot" to "·", "times" to "×",
    )

    /** Anki's `en_US` is the Android tag `en-US`. */
    fun langTag(lang: String) = lang.trim().replace('_', '-')

    /** Anki's [TTS reading text]: block tags become spaces, tags go, entities are decoded; `[...]` is spoken as "blank". */
    fun speakable(html: String, blank: String = "blank"): String {
        val plain = decode(HTML.replace(LINEBREAK.replace(html, " "), ""))
        return SOUND.replace(plain.replace("[...]", blank), "").replace(Regex("""\s+"""), " ").trim()
    }

    private fun decode(s: String) = if ('&' !in s) s else ENTITY.replace(s) { m ->
        val n = m.groupValues[1]
        when {
            n[0] == '#' -> runCatching {
                val code = if (n[1] == 'x' || n[1] == 'X') n.substring(2).toInt(16) else n.substring(1).toInt()
                if (code == 0xA0) " " else String(Character.toChars(code))
            }.getOrDefault(m.value)
            else -> NAMED[n] ?: m.value
        }
    }

    /** The item of one tag, or null when it has no language (Anki shows an error there instead of a button). */
    fun item(options: String, content: String): Item? {
        val opts = OPTION.findAll(options).associate { it.groupValues[1] to (it.groups[2]?.value ?: it.groupValues[3]) }
        val lang = langTag(opts["lang"] ?: "")
        if (lang.isEmpty()) return null
        val rate = (opts["speed"]?.toFloatOrNull() ?: 1f).coerceIn(.3f, 4f)
        return Item(speakable(content, opts["cloze_blank"] ?: "blank"), lang, rate)
    }

    /** Every speech tag of [text], in order. */
    fun extract(text: String): List<Item> = TAG.findAll(text).mapNotNull { item(it.groupValues[1], it.groupValues[2]) }.toList()

    /** The template of card [ord]: note types with clozes have one template for all cards. */
    fun templateFor(templates: List<Template>, ord: Int, cloze: Boolean): Template? =
        if (cloze) templates.minByOrNull { it.ord } else templates.firstOrNull { it.ord == ord }

    /** Speech slots of both sides of card [ord]. [fields] = field name to value. */
    fun card(t: Template, fields: Map<String, String>, ord: Int): Card {
        val front = render(t.question, fields, ord, true, "")
        return Card(extract(front), extract(render(t.answer, fields, ord, false, front)))
    }

    /**
     * What each play reference of a page (in document order) says. [refs] = how many play references the page has.
     * Null = this reference can't be matched (it stays a small chip). A blank item is a reference that says nothing.
     */
    fun bind(items: List<Item>, refs: Int): List<Item?> {
        val usable = if (refs == items.size) items else items.filter { !it.blank }
        return List(refs) { usable.getOrNull(it) }
    }

    /** "No Japanese voice on this phone"; no names = no speech engine at all. */
    fun missingVoiceNote(names: List<String>): String = when (names.size) {
        0 -> "No text-to-speech voice on this phone"
        1 -> "No ${names[0]} voice on this phone"
        else -> "No ${names.dropLast(1).joinToString(", ")} and ${names.last()} voices on this phone"
    }

    /** TextToSpeech.isLanguageAvailable: LANG_MISSING_DATA (-1) and LANG_NOT_SUPPORTED (-2) mean no usable voice. */
    fun voiceMissing(availability: Int) = availability < 0

    // ---------------------------------------------------------------- template rendering

    /** Renders one side of a template: conditionals, fields with filters, FrontSide. Speech filters become literal tags. */
    fun render(template: String, fields: Map<String, String>, ord: Int, question: Boolean, front: String): String {
        val out = StringBuilder()
        val open = ArrayList<Boolean>()
        var last = 0
        fun on() = open.all { it }
        for (m in Regex("""\{\{(.*?)}}""", RegexOption.DOT_MATCHES_ALL).findAll(template)) {
            if (on()) out.append(template, last, m.range.first)
            last = m.range.last + 1
            val inner = m.groupValues[1].trim('{', '}', ' ', '\t', '\r', '\n')
            when {
                inner.startsWith("#") -> open.add(!isEmpty(fields[inner.drop(1).trim()]))
                inner.startsWith("^") -> open.add(isEmpty(fields[inner.drop(1).trim()]))
                inner.startsWith("/") -> if (open.isNotEmpty()) open.removeAt(open.lastIndex)
                on() -> out.append(replacement(inner, fields, ord, question, front))
            }
        }
        if (on()) out.append(template, last, template.length)
        return out.toString()
    }

    private fun isEmpty(v: String?) = v == null || EMPTY_FIELD.matches(v)

    private fun replacement(inner: String, fields: Map<String, String>, ord: Int, question: Boolean, front: String): String {
        val parts = inner.split(':').map { it.trim() }
        val name = parts.last()
        val filters = parts.dropLast(1)
        if (name.isEmpty()) return ""
        if (filters.lastOrNull() == "type") return ""
        var text = if (name == "FrontSide") front else fields[name] ?: ""
        for (f in filters.asReversed()) text = filter(f, text, ord, question)
        return text
    }

    private fun filter(name: String, text: String, ord: Int, question: Boolean): String = when {
        name == "text" -> decode(HTML.replace(text, ""))
        name == "kana" -> furigana(text) { it.groupValues[2] }
        name == "kanji" -> furigana(text) { it.groupValues[1] }
        name == "furigana" -> furigana(text) { it.groupValues[1] + it.groupValues[2] }
        name == "cloze" -> Cloze.reveal(text, ord + 1, question)
        name == "cloze-only" -> Cloze.only(text, ord + 1, question)
        name == "hint" || name.startsWith("type") -> ""
        name.startsWith("tts ") -> "[anki:tts lang=${name.drop(4).trim()}]$text[/anki:tts]"
        else -> text
    }

    private fun furigana(text: String, pick: (MatchResult) -> String) =
        FURIGANA.replace(text.replace("&nbsp;", " ")) { if (it.groupValues[2].startsWith("sound:")) it.value else pick(it) }

    /** Anki's cloze markup `{{c1::text::hint}}` (nesting, `c1,2`). Only the spoken text matters, so no HTML is produced. */
    internal object Cloze {
        private sealed class Node
        private class Text(val s: String) : Node()
        private class Cl(val ords: List<Int>) : Node() { val nodes = ArrayList<Node>(); var hint: String? = null }

        private fun parse(text: String): List<Node> {
            val out = ArrayList<Node>()
            val stack = ArrayList<Cl>()
            val buf = StringBuilder()
            fun flush() {
                if (buf.isEmpty()) return
                var s = buf.toString(); buf.setLength(0)
                val top = stack.lastOrNull()
                if (top != null) s.indexOf("::").let { if (it >= 0) { top.hint = s.substring(it + 2); s = s.substring(0, it) } }
                (top?.nodes ?: out).add(Text(s))
            }
            var i = 0
            while (i < text.length) {
                val ords = openAt(text, i)
                when {
                    ords != null -> {
                        flush()
                        if (stack.size < 10) stack.add(Cl(ords.first))
                        i = ords.second
                    }
                    stack.isNotEmpty() && text.startsWith("}}", i) -> {
                        flush()
                        val done = stack.removeAt(stack.lastIndex)
                        (stack.lastOrNull()?.nodes ?: out).add(done)
                        i += 2
                    }
                    else -> buf.append(text[i++])
                }
            }
            flush()
            return out
        }

        /** `{{c1,2::` at [i]: the numbers and the index after the `::`. */
        private fun openAt(text: String, i: Int): Pair<List<Int>, Int>? {
            if (!text.startsWith("{{c", i)) return null
            var j = i + 3
            while (j < text.length && (text[j].isDigit() || text[j] == ',')) j++
            if (j == i + 3 || !text.startsWith("::", j)) return null
            val ords = text.substring(i + 3, j).split(',').mapNotNull { it.toIntOrNull() }
            return if (ords.isEmpty()) null else ords to j + 2
        }

        private fun plain(nodes: List<Node>): String = nodes.joinToString("") { if (it is Text) it.s else plain((it as Cl).nodes) }
        private fun hint(c: Cl) = "[${c.hint ?: "..."}]"

        /** The `cloze:` filter: the active cloze is `[hint]` on the question, shown on the answer; empty when no cloze is active. */
        fun reveal(text: String, ord: Int, question: Boolean): String {
            var found = false
            fun go(nodes: List<Node>, sb: StringBuilder) {
                for (n in nodes) when (n) {
                    is Text -> sb.append(n.s)
                    is Cl -> {
                        val active = ord in n.ords
                        if (active) found = true
                        if (active && question) { go(n.nodes, StringBuilder()); sb.append(hint(n)) } else go(n.nodes, sb)
                    }
                }
            }
            val sb = StringBuilder()
            go(parse(text), sb)
            return if (found) sb.toString() else ""
        }

        /** The `cloze-only:` filter: only the active clozes, joined with ", ". */
        fun only(text: String, ord: Int, question: Boolean): String {
            val out = ArrayList<String>()
            fun go(nodes: List<Node>) {
                for (n in nodes) if (n is Cl) {
                    if (ord in n.ords) out.add(if (question) hint(n) else plain(n.nodes))
                    go(n.nodes)
                }
            }
            go(parse(text))
            return out.joinToString(", ")
        }
    }
}
