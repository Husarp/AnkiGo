package com.husarp.ankigo.study

import org.junit.Assert.*
import org.junit.Test

class TemplateBridgeTest {
    @Test fun bridgeValidatesAndDispatchesReadOnlyActions() {
        val terms = mutableListOf<Pair<String, String>>()
        val notes = mutableListOf<Long>()
        val bridge = TemplateBridge({ key, subject -> terms.add(key to subject) }, notes::add)
        bridge.showTerm("całka", "matematyka_v2")
        bridge.showTerm("", "math")
        bridge.showTerm("key", "../math")
        bridge.openNote("1234567890123")
        listOf("0", "-1", "x", "9223372036854775808").forEach(bridge::openNote)
        assertEquals(listOf("całka" to "matematyka_v2"), terms)
        assertEquals(listOf(1234567890123L), notes)
    }
    @Test fun dictionaryReadsSharedJsonAndAssignmentWithoutExecution() {
        val json = """{"sigma":"<b>σ</b> \\(x\\)","integral":{"html":"Definition"}}"""
        for (source in listOf(json, "window.slownik = $json;", "const slownik = $json;")) {
            assertEquals("<b>σ</b> \\(x\\)", TemplateBridge.definition(source, "sigma"))
            assertEquals("Definition", TemplateBridge.definition(source, "integral"))
            assertNull(TemplateBridge.definition(source, "missing"))
        }
        assertNull(TemplateBridge.dictionary("alert('oops');"))
        assertNull(TemplateBridge.dictionary("window.slownik = {}; alert('oops')"))
        assertNull(TemplateBridge.dictionary("window.slownik = {bad}"))
    }
    @Test fun dictionaryFileAllowsVersionedSubjectsButNotPaths() {
        assertEquals("_slownik_fizyka_v2.js", TemplateBridge.dictionaryFile("fizyka_v2"))
        listOf("", "../math", "a/b", "a\\b", "a.js?x", "a".repeat(101)).forEach { assertNull(TemplateBridge.dictionaryFile(it)) }
    }
    @Test fun interactiveTemplatesKeepScriptsAndEnableOfflineMathClasses() {
        val body = """<script src="_slownik_math.js"></script><details><summary>Step</summary>\(\class{term}{x}\)</details>"""
        val html = com.husarp.ankigo.CardHtml.build(body, "", 0, "q", null) { null }.html
        assertTrue(html.contains(body))
        assertTrue(html.contains("load:['[tex]/html']"))
        assertTrue(html.contains("mathjax/tex-mml-chtml.js"))
        val tap = com.husarp.ankigo.CardHtml.tapScript("q", true, false)
        assertTrue(tap.contains("e.defaultPrevented"))
        assertTrue(tap.contains("details,summary,.term,[data-term],[onclick]"))
    }
}
